package com.qinghe.marketing.claim;

import com.qinghe.marketing.campaign.Campaign;
import com.qinghe.marketing.campaign.CampaignRepository;
import com.qinghe.marketing.campaign.CampaignStatus;
import com.qinghe.marketing.shared.clock.BusinessClock;
import com.qinghe.marketing.shared.error.QingheBusinessException;
import com.qinghe.marketing.shared.error.QingheErrorCode;
import com.qinghe.marketing.shared.id.BusinessIdGenerator;
import com.qinghe.marketing.shared.id.BusinessIdType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
public class ClaimService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClaimService.class);

    private final CampaignRepository campaignRepository;
    private final ClaimRequestRepository claimRepository;
    private final ClaimReservationStore reservationStore;
    private final ClaimAcceptanceTransactionService transactionService;
    private final BusinessIdGenerator idGenerator;
    private final BusinessClock clock;

    public ClaimService(CampaignRepository campaignRepository,
                        ClaimRequestRepository claimRepository,
                        ClaimReservationStore reservationStore,
                        ClaimAcceptanceTransactionService transactionService,
                        BusinessIdGenerator idGenerator,
                        BusinessClock clock) {
        this.campaignRepository = campaignRepository;
        this.claimRepository = claimRepository;
        this.reservationStore = reservationStore;
        this.transactionService = transactionService;
        this.idGenerator = idGenerator;
        this.clock = clock;
    }

    public ClaimSubmissionResult submit(String campaignNo, long memberId, String requestId,
                                        OffsetDateTime clientRequestedAt) {
        validateRequestId(requestId);
        String digest = ClaimRequestDigest.calculate(campaignNo, clientRequestedAt);
        Optional<ClaimRequest> priorRequest = claimRepository.findByMemberAndRequestId(memberId, requestId);
        if (priorRequest.isPresent()) {
            requireSameDigest(priorRequest.get(), digest);
            return new ClaimSubmissionResult(priorRequest.get(), true);
        }

        Campaign campaign = campaignRepository.findByCampaignNo(campaignNo)
                .orElseThrow(() -> business(QingheErrorCode.RESOURCE_NOT_FOUND,
                        "campaign does not exist"));
        LocalDateTime now = clock.dateTime();
        requireActive(campaign, now);

        String cycle = ClaimAcceptanceTransactionService.claimCycle(campaign.id());
        Optional<ClaimRequest> priorCampaignClaim = claimRepository.findByMemberCampaignAndCycle(
                memberId, campaign.id(), cycle);
        if (priorCampaignClaim.isPresent()) {
            throw business(QingheErrorCode.ALREADY_CLAIMED,
                    "member already has a claim for this campaign");
        }

        ClaimReservationCommand command = new ClaimReservationCommand(
                campaign.id(), memberId, requestId, digest, UUID.randomUUID().toString(),
                idGenerator.next(BusinessIdType.CLAIM), idGenerator.next(BusinessIdType.OUTBOX_EVENT),
                now, campaign.beginAt(), campaign.endAt());
        ClaimReservationResult reservation = reservationStore.reserve(command);
        handleRejectedReservation(reservation);

        Optional<ClaimRequest> recovered = claimRepository.findByReservationId(reservation.reservationId());
        if (recovered.isPresent()) {
            requireSameDigest(recovered.get(), digest);
            markPersistedBestEffort(campaign.id(), reservation.reservationId());
            return new ClaimSubmissionResult(recovered.get(), true);
        }

        try {
            ClaimRequest accepted = transactionService.accept(campaign.id(), memberId, requestId, digest,
                    reservation.reservationId(), reservation.claimNo(), reservation.eventId(), now);
            markPersistedBestEffort(campaign.id(), reservation.reservationId());
            return new ClaimSubmissionResult(accepted,
                    reservation.outcome() == ClaimReservationResult.Outcome.IDEMPOTENT_REPLAY);
        } catch (DataIntegrityViolationException concurrentInsert) {
            Optional<ClaimRequest> concurrent = claimRepository.findByReservationId(reservation.reservationId());
            if (concurrent.isPresent()) {
                requireSameDigest(concurrent.get(), digest);
                markPersistedBestEffort(campaign.id(), reservation.reservationId());
                return new ClaimSubmissionResult(concurrent.get(), true);
            }
            compensateAfterFailure(command, concurrentInsert);
            throw business(QingheErrorCode.TEMPORARY_UNAVAILABLE,
                    "claim acceptance is temporarily unavailable");
        } catch (RuntimeException persistenceFailure) {
            compensateAfterFailure(command, persistenceFailure);
            throw business(QingheErrorCode.TEMPORARY_UNAVAILABLE,
                    "claim acceptance is temporarily unavailable");
        }
    }

    public ClaimRequest requireOwnedClaim(String claimNo, long memberId) {
        return claimRepository.findByClaimNoAndMemberId(claimNo, memberId)
                .orElseThrow(() -> business(QingheErrorCode.RESOURCE_NOT_FOUND,
                        "claim request does not exist"));
    }

    public String entitlementNoFor(ClaimRequest claim) {
        if (claim.status() != ClaimStatus.SUCCESS) {
            return null;
        }
        return claimRepository.findEntitlementNo(claim.id()).orElseThrow(
                () -> new IllegalStateException("successful claim has no entitlement"));
    }

    private void handleRejectedReservation(ClaimReservationResult result) {
        switch (result.outcome()) {
            case RESERVED:
            case IDEMPOTENT_REPLAY:
                return;
            case REQUEST_CONFLICT:
                throw business(QingheErrorCode.REQUEST_CONFLICT,
                        "request id was reused with different claim content");
            case MEMBER_ALREADY_RESERVED:
                throw business(QingheErrorCode.ALREADY_CLAIMED,
                        "member already has a claim reservation for this campaign");
            case SOLD_OUT:
                throw business(QingheErrorCode.SOLD_OUT, "campaign stock is sold out");
            case CAMPAIGN_NOT_ACTIVE:
                throw business(QingheErrorCode.CAMPAIGN_NOT_ACTIVE,
                        "campaign is outside its claim window");
            case STOCK_NOT_INITIALIZED:
            default:
                throw business(QingheErrorCode.TEMPORARY_UNAVAILABLE,
                        "campaign stock is not initialized");
        }
    }

    private void compensateAfterFailure(ClaimReservationCommand command, RuntimeException failure) {
        try {
            reservationStore.compensate(command.campaignId(), command.memberId(), command.requestId(),
                    command.reservationId(), "CLAIM_PERSISTENCE_FAILED");
        } catch (RuntimeException compensationFailure) {
            failure.addSuppressed(compensationFailure);
            LOGGER.error("Claim persistence and immediate reservation compensation both failed, reservationId={}",
                    command.reservationId(), failure);
        }
    }

    private void markPersistedBestEffort(long campaignId, String reservationId) {
        try {
            reservationStore.markPersisted(campaignId, reservationId);
        } catch (RuntimeException redisFailure) {
            LOGGER.warn("Claim persisted but reservation marker was not updated, reservationId={}",
                    reservationId, redisFailure);
        }
    }

    private static void requireSameDigest(ClaimRequest existing, String digest) {
        if (!existing.requestDigest().equals(digest)) {
            throw business(QingheErrorCode.REQUEST_CONFLICT,
                    "request id was reused with different claim content");
        }
    }

    private static void requireActive(Campaign campaign, LocalDateTime now) {
        if (campaign.status() != CampaignStatus.ACTIVE
                || now.isBefore(campaign.beginAt()) || !now.isBefore(campaign.endAt())) {
            throw business(QingheErrorCode.CAMPAIGN_NOT_ACTIVE,
                    "campaign is not active in the claim window");
        }
    }

    private static void validateRequestId(String requestId) {
        if (requestId == null || !requestId.matches("[A-Za-z0-9._:-]{8,64}")) {
            throw business(QingheErrorCode.INVALID_ARGUMENT,
                    "X-Request-Id must match [A-Za-z0-9._:-]{8,64}");
        }
    }

    private static QingheBusinessException business(QingheErrorCode code, String message) {
        return new QingheBusinessException(code, message);
    }
}
