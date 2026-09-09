package com.qinghe.marketing.entitlement;

import com.qinghe.marketing.campaign.BenefitTemplateDraft;
import com.qinghe.marketing.campaign.BenefitTemplateSnapshotCodec;
import com.qinghe.marketing.campaign.BenefitType;
import com.qinghe.marketing.campaign.CampaignPublicationSnapshot;
import com.qinghe.marketing.campaign.CampaignRepository;
import com.qinghe.marketing.campaign.ValidityType;
import com.qinghe.marketing.claim.ClaimRequest;
import com.qinghe.marketing.claim.ClaimRequestRepository;
import com.qinghe.marketing.claim.ClaimStatus;
import com.qinghe.marketing.shared.clock.BusinessClock;
import com.qinghe.marketing.shared.id.BusinessIdGenerator;
import com.qinghe.marketing.shared.id.BusinessIdType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ClaimIssueTransactionServiceTest {

    @Test
    void shouldCreateEntitlementBeforeCrossingTheSuccessGate() {
        Fixture fixture = new Fixture(ClaimStatus.PROCESSING);

        ClaimIssueResult result = fixture.service.issue(fixture.command);

        assertEquals(ClaimIssueOutcome.ISSUED, result.outcome());
        assertEquals("ENT-1", result.entitlement().entitlementNo());
        verify(fixture.claims).markSuccess(30L, 0L, fixture.now);
        verify(fixture.deliveries).record("EVT-1", 30L, "ISSUED", null, fixture.now);
    }

    @Test
    void shouldReturnTheExistingEntitlementForDuplicateDelivery() {
        Fixture fixture = new Fixture(ClaimStatus.SUCCESS);
        MemberEntitlement existing = fixture.entitlement(40L);
        when(fixture.entitlements.findBySourceClaimId(30L)).thenReturn(Optional.of(existing));

        ClaimIssueResult result = fixture.service.issue(fixture.command);

        assertEquals(ClaimIssueOutcome.ALREADY_ISSUED, result.outcome());
        verify(fixture.entitlements, never()).insert(any(MemberEntitlement.class));
        verify(fixture.claims, never()).markSuccess(eq(30L), eq(0L), any(LocalDateTime.class));
    }

    @Test
    void shouldIgnoreLateDeliveryAfterFailureAndRejectConflictingPayload() {
        Fixture failed = new Fixture(ClaimStatus.FAILED);
        assertEquals(ClaimIssueOutcome.IGNORED_TERMINAL,
                failed.service.issue(failed.command).outcome());
        verify(failed.entitlements, never()).insert(any(MemberEntitlement.class));

        Fixture conflict = new Fixture(ClaimStatus.PROCESSING);
        BenefitIssueCommand wrongMember = new BenefitIssueCommand(
                "EVT-1", "CLM-1", "RSV-1", 10L, 21L, "request-001");
        assertThrows(IllegalArgumentException.class, () -> conflict.service.issue(wrongMember));
    }

    private static final class Fixture {
        private final LocalDateTime now = LocalDateTime.of(2026, 9, 9, 16, 0);
        private final ClaimRequestRepository claims = mock(ClaimRequestRepository.class);
        private final MemberEntitlementRepository entitlements = mock(MemberEntitlementRepository.class);
        private final ClaimIssueDeliveryRepository deliveries = mock(ClaimIssueDeliveryRepository.class);
        private final CampaignRepository campaigns = mock(CampaignRepository.class);
        private final BenefitTemplateSnapshotCodec codec = mock(BenefitTemplateSnapshotCodec.class);
        private final RightCodeProtector protector = mock(RightCodeProtector.class);
        private final BusinessIdGenerator ids = mock(BusinessIdGenerator.class);
        private final BusinessClock clock = () -> Instant.parse("2026-09-09T08:00:00Z");
        private final BenefitIssueCommand command = new BenefitIssueCommand(
                "EVT-1", "CLM-1", "RSV-1", 10L, 20L, "request-001");
        private final ClaimIssueTransactionService service;

        private Fixture(ClaimStatus status) {
            ClaimRequest claim = new ClaimRequest(30L, "CLM-1", "request-001", repeat('a', 64),
                    10L, 20L, "CAMPAIGN:10", "RSV-1", status,
                    status == ClaimStatus.FAILED ? "OUTBOX_DEAD" : null, 0L, now, now);
            when(claims.findByClaimNoForUpdate("CLM-1")).thenReturn(Optional.of(claim));
            when(deliveries.sourceEventMatches("EVT-1", 30L)).thenReturn(true);
            when(entitlements.findBySourceClaimId(30L)).thenReturn(Optional.empty());
            when(campaigns.findPublication(10L)).thenReturn(Optional.of(new CampaignPublicationSnapshot(
                    10L, 1L, "{}", now.minusDays(1), now.plusDays(2), 1, 10L, 300L,
                    "admin", now.minusDays(1))));
            when(codec.decode("{}")).thenReturn(new BenefitTemplateDraft(
                    "template", BenefitType.FREE_PRODUCT, "免费饮品", null, "DRINK", null,
                    ValidityType.RELATIVE_DAYS, 7, null, null, "{}"));
            when(protector.protect(any(String.class))).thenReturn(
                    new ProtectedRightCode(repeat('b', 64), new byte[]{1, 2, 3}));
            when(ids.next(BusinessIdType.ENTITLEMENT)).thenReturn("ENT-1");
            when(claims.markSuccess(30L, 0L, now)).thenReturn(true);
            when(entitlements.insert(any(MemberEntitlement.class))).thenAnswer(invocation -> {
                MemberEntitlement value = invocation.getArgument(0);
                return entitlement(40L, value);
            });
            service = new ClaimIssueTransactionService(claims, entitlements, deliveries, campaigns,
                    codec, protector, ids, clock);
        }

        private MemberEntitlement entitlement(long id) {
            return new MemberEntitlement(id, "ENT-1", repeat('b', 64), new byte[]{1, 2, 3},
                    30L, 10L, 20L, EntitlementStatus.AVAILABLE, now, now.plusDays(7),
                    0L, now, now);
        }

        private MemberEntitlement entitlement(long id, MemberEntitlement source) {
            return new MemberEntitlement(id, source.entitlementNo(), source.rightCodeHash(),
                    source.encryptedRightCode(), source.sourceClaimId(), source.campaignId(),
                    source.memberId(), source.status(), source.validFrom(), source.validUntil(),
                    source.version(), source.createdAt(), source.updatedAt());
        }
    }

    private static String repeat(char value, int count) {
        StringBuilder result = new StringBuilder(count);
        for (int index = 0; index < count; index++) result.append(value);
        return result.toString();
    }
}
