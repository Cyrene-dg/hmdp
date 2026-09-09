package com.qinghe.marketing.campaign;

import com.qinghe.marketing.shared.clock.BusinessClock;
import com.qinghe.marketing.shared.error.QingheBusinessException;
import com.qinghe.marketing.shared.error.QingheErrorCode;
import com.qinghe.marketing.shared.id.BusinessIdGenerator;
import com.qinghe.marketing.shared.id.BusinessIdType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CampaignApprovalTransactionService {

    private final CampaignRepository campaignRepository;
    private final BusinessIdGenerator idGenerator;
    private final BusinessClock clock;

    public CampaignApprovalTransactionService(CampaignRepository campaignRepository,
                                              BusinessIdGenerator idGenerator,
                                              BusinessClock clock) {
        this.campaignRepository = campaignRepository;
        this.idGenerator = idGenerator;
        this.clock = clock;
    }

    @Transactional(rollbackFor = Exception.class)
    public Campaign approve(Campaign campaign, long expectedVersion, String reviewerId,
                            String comment, CampaignPublicationSnapshot snapshot) {
        Campaign current = campaignRepository.findByIdForUpdate(campaign.id())
                .orElseThrow(() -> notFound("campaign does not exist"));
        if ((current.status() == CampaignStatus.SCHEDULED || current.status() == CampaignStatus.ACTIVE)
                && campaignRepository.findPublication(current.id()).isPresent()) {
            return current;
        }
        requirePending(current, expectedVersion);
        if (current.createdBy().equals(reviewerId)) {
            throw new QingheBusinessException(QingheErrorCode.SELF_APPROVAL_NOT_ALLOWED,
                    "campaign creator cannot approve the campaign");
        }
        campaignRepository.savePublication(snapshot);
        Campaign updated = campaignRepository.transition(current.id(), CampaignStatus.PENDING_APPROVAL,
                CampaignStatus.SCHEDULED, expectedVersion, reviewerId, clock.dateTime());
        campaignRepository.saveReview(new CampaignReview(
                idGenerator.next(BusinessIdType.CAMPAIGN_REVIEW), current.id(), ReviewDecision.APPROVE,
                current.createdBy(), reviewerId, CampaignStatus.PENDING_APPROVAL,
                CampaignStatus.SCHEDULED, comment, clock.dateTime()));
        return updated;
    }

    @Transactional(rollbackFor = Exception.class)
    public Campaign reject(Campaign campaign, long expectedVersion, String reviewerId, String comment) {
        Campaign current = campaignRepository.findByIdForUpdate(campaign.id())
                .orElseThrow(() -> notFound("campaign does not exist"));
        requirePending(current, expectedVersion);
        if (current.createdBy().equals(reviewerId)) {
            throw new QingheBusinessException(QingheErrorCode.SELF_APPROVAL_NOT_ALLOWED,
                    "campaign creator cannot review the campaign");
        }
        Campaign updated = campaignRepository.transition(current.id(), CampaignStatus.PENDING_APPROVAL,
                CampaignStatus.REJECTED, expectedVersion, reviewerId, clock.dateTime());
        campaignRepository.saveReview(new CampaignReview(
                idGenerator.next(BusinessIdType.CAMPAIGN_REVIEW), current.id(), ReviewDecision.REJECT,
                current.createdBy(), reviewerId, CampaignStatus.PENDING_APPROVAL,
                CampaignStatus.REJECTED, comment, clock.dateTime()));
        return updated;
    }

    private static void requirePending(Campaign campaign, long expectedVersion) {
        if (campaign.version() != expectedVersion) {
            throw new QingheBusinessException(QingheErrorCode.VERSION_CONFLICT,
                    "campaign version changed");
        }
        if (campaign.status() != CampaignStatus.PENDING_APPROVAL) {
            throw new QingheBusinessException(QingheErrorCode.CAMPAIGN_STATE_CONFLICT,
                    "campaign is not pending approval");
        }
    }

    private static QingheBusinessException notFound(String message) {
        return new QingheBusinessException(QingheErrorCode.RESOURCE_NOT_FOUND, message);
    }
}
