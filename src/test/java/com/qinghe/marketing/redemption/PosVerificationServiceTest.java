package com.qinghe.marketing.redemption;

import com.qinghe.marketing.campaign.BenefitType;
import com.qinghe.marketing.campaign.CampaignStoreSnapshot;
import com.qinghe.marketing.entitlement.EntitlementStatus;
import com.qinghe.marketing.entitlement.RightCodeProtector;
import com.qinghe.marketing.identity.PosAuthenticatedStore;
import com.qinghe.marketing.shared.clock.BusinessClock;
import com.qinghe.marketing.shared.error.QingheBusinessException;
import com.qinghe.marketing.shared.error.QingheErrorCode;
import com.qinghe.marketing.store.StoreOwnershipType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class PosVerificationServiceTest {

    private RedemptionRepository repository;
    private RightCodeProtector protector;
    private CampaignStorePolicy policy;
    private PosVerificationService service;
    private PosAuthenticatedStore store;

    @BeforeEach
    void setUp() {
        repository = mock(RedemptionRepository.class);
        protector = mock(RightCodeProtector.class);
        policy = mock(CampaignStorePolicy.class);
        BusinessClock clock = () -> Instant.parse("2026-09-09T08:00:00Z");
        service = new PosVerificationService(repository, protector, policy, clock);
        store = new PosAuthenticatedStore(1L, "S001", StoreOwnershipType.DIRECT, "POS-1");
        when(protector.hash("RIGHT-0001")).thenReturn("hash");
    }

    @Test
    void shouldReturnAvailableRightWithoutWritingConsumptionState() {
        PosEntitlementSnapshot entitlement = entitlement(EntitlementStatus.AVAILABLE,
                LocalDateTime.of(2026, 9, 10, 0, 0));
        when(repository.findEntitlementByRightCodeHash("hash"))
                .thenReturn(Optional.of(entitlement));
        when(policy.requireApplicable(10L, store)).thenReturn(new CampaignStoreSnapshot(
                1L, "S001", StoreOwnershipType.DIRECT, 0L, "ACTIVE", 1L));

        PosVerificationResult result = service.verify(store, command());

        assertEquals("ENT-1", result.rightNo());
        assertEquals(EntitlementStatus.AVAILABLE, result.status());
        verify(repository).findEntitlementByRightCodeHash("hash");
        verifyNoMoreInteractions(repository);
    }

    @Test
    void shouldRejectExpiredRight() {
        when(repository.findEntitlementByRightCodeHash("hash"))
                .thenReturn(Optional.of(entitlement(EntitlementStatus.AVAILABLE,
                        LocalDateTime.of(2026, 9, 9, 16, 0))));

        QingheBusinessException failure = assertThrows(QingheBusinessException.class,
                () -> service.verify(store, command()));

        assertEquals(QingheErrorCode.RIGHT_EXPIRED, failure.errorCode());
    }

    @Test
    void shouldRejectCredentialForAnotherStore() {
        PosVerificationCommand mismatched = new PosVerificationCommand(
                "REQ-0001", "S002", "T-1", "RIGHT-0001",
                LocalDateTime.of(2026, 9, 9, 16, 0));

        QingheBusinessException failure = assertThrows(QingheBusinessException.class,
                () -> service.verify(store, mismatched));

        assertEquals(QingheErrorCode.STORE_NOT_APPLICABLE, failure.errorCode());
        verifyNoMoreInteractions(repository);
    }

    private static PosVerificationCommand command() {
        return new PosVerificationCommand("REQ-0001", "S001", "T-1", "RIGHT-0001",
                LocalDateTime.of(2026, 9, 9, 16, 0));
    }

    private static PosEntitlementSnapshot entitlement(EntitlementStatus status,
                                                       LocalDateTime validUntil) {
        return new PosEntitlementSnapshot(1L, "ENT-1", 10L, status,
                LocalDateTime.of(2026, 9, 8, 0, 0), validUntil, 0L,
                "免费饮品", BenefitType.FREE_PRODUCT, "DRINK", null);
    }
}
