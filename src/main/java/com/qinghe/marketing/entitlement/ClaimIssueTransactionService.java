package com.qinghe.marketing.entitlement;

import com.qinghe.marketing.campaign.BenefitTemplateDraft;
import com.qinghe.marketing.campaign.BenefitTemplateSnapshotCodec;
import com.qinghe.marketing.campaign.CampaignPublicationSnapshot;
import com.qinghe.marketing.campaign.CampaignRepository;
import com.qinghe.marketing.campaign.ValidityType;
import com.qinghe.marketing.claim.ClaimRequest;
import com.qinghe.marketing.claim.ClaimRequestRepository;
import com.qinghe.marketing.claim.ClaimStatus;
import com.qinghe.marketing.shared.clock.BusinessClock;
import com.qinghe.marketing.shared.id.BusinessIdGenerator;
import com.qinghe.marketing.shared.id.BusinessIdType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
public class ClaimIssueTransactionService {

    private final ClaimRequestRepository claimRepository;
    private final MemberEntitlementRepository entitlementRepository;
    private final ClaimIssueDeliveryRepository deliveryRepository;
    private final CampaignRepository campaignRepository;
    private final BenefitTemplateSnapshotCodec snapshotCodec;
    private final RightCodeProtector rightCodeProtector;
    private final BusinessIdGenerator idGenerator;
    private final BusinessClock clock;

    public ClaimIssueTransactionService(ClaimRequestRepository claimRepository,
                                        MemberEntitlementRepository entitlementRepository,
                                        ClaimIssueDeliveryRepository deliveryRepository,
                                        CampaignRepository campaignRepository,
                                        BenefitTemplateSnapshotCodec snapshotCodec,
                                        RightCodeProtector rightCodeProtector,
                                        BusinessIdGenerator idGenerator,
                                        BusinessClock clock) {
        this.claimRepository = claimRepository;
        this.entitlementRepository = entitlementRepository;
        this.deliveryRepository = deliveryRepository;
        this.campaignRepository = campaignRepository;
        this.snapshotCodec = snapshotCodec;
        this.rightCodeProtector = rightCodeProtector;
        this.idGenerator = idGenerator;
        this.clock = clock;
    }

    @Transactional(rollbackFor = Exception.class)
    public ClaimIssueResult issue(BenefitIssueCommand command) {
        ClaimRequest claim = claimRepository.findByClaimNoForUpdate(command.claimNo())
                .orElseThrow(() -> new IllegalArgumentException("benefit issue claim does not exist"));
        requireMatches(claim, command);
        if (!deliveryRepository.sourceEventMatches(command.eventId(), claim.id())) {
            throw new IllegalArgumentException("benefit issue event does not belong to its claim");
        }

        Optional<MemberEntitlement> existing = entitlementRepository.findBySourceClaimId(claim.id());
        if (claim.status() == ClaimStatus.SUCCESS) {
            MemberEntitlement entitlement = existing.orElseThrow(() -> new IllegalStateException(
                    "successful claim has no entitlement"));
            deliveryRepository.record(command.eventId(), claim.id(), "DUPLICATE", null, clock.dateTime());
            return new ClaimIssueResult(ClaimIssueOutcome.ALREADY_ISSUED, entitlement);
        }
        if (claim.status() != ClaimStatus.PROCESSING) {
            deliveryRepository.record(command.eventId(), claim.id(), "IGNORED_TERMINAL",
                    claim.failureCode(), clock.dateTime());
            return new ClaimIssueResult(ClaimIssueOutcome.IGNORED_TERMINAL, existing.orElse(null));
        }
        if (existing.isPresent()) {
            throw new IllegalStateException("processing claim already has an entitlement");
        }

        CampaignPublicationSnapshot publication = campaignRepository.findPublication(claim.campaignId())
                .orElseThrow(() -> new IllegalStateException("campaign publication snapshot does not exist"));
        BenefitTemplateDraft template = snapshotCodec.decode(publication.templateSnapshotJson());
        LocalDateTime now = clock.dateTime();
        LocalDateTime validFrom = template.validityType() == ValidityType.FIXED_RANGE
                ? template.validFrom() : now;
        if (template.validityType() == ValidityType.RELATIVE_DAYS
                && (template.validityValue() == null || template.validityValue() <= 0)) {
            throw new IllegalStateException("published relative validity is invalid");
        }
        LocalDateTime validUntil = template.validityType() == ValidityType.FIXED_RANGE
                ? template.validUntil() : now.plusDays(template.validityValue());
        if (validFrom == null || validUntil == null || !validUntil.isAfter(validFrom)
                || !validUntil.isAfter(now)) {
            throw new IllegalStateException("published entitlement validity is invalid");
        }

        String plaintextRightCode = "QH-" + UUID.randomUUID().toString().replace("-", "").toUpperCase();
        ProtectedRightCode protectedCode = rightCodeProtector.protect(plaintextRightCode);
        MemberEntitlement inserted = entitlementRepository.insert(new MemberEntitlement(
                0L, idGenerator.next(BusinessIdType.ENTITLEMENT), protectedCode.hash(),
                protectedCode.encrypted(), claim.id(), claim.campaignId(), claim.memberId(),
                EntitlementStatus.AVAILABLE, validFrom, validUntil, 0L, now, now));
        if (!claimRepository.markSuccess(claim.id(), claim.version(), now)) {
            throw new IllegalStateException("claim success transition lost its state gate");
        }
        deliveryRepository.record(command.eventId(), claim.id(), "ISSUED", null, now);
        return new ClaimIssueResult(ClaimIssueOutcome.ISSUED, inserted);
    }

    private static void requireMatches(ClaimRequest claim, BenefitIssueCommand command) {
        if (claim.campaignId() != command.campaignId()
                || claim.memberId() != command.memberId()
                || !claim.reservationId().equals(command.reservationId())
                || !claim.requestId().equals(command.requestId())) {
            throw new IllegalArgumentException("benefit issue message conflicts with its claim");
        }
    }
}
