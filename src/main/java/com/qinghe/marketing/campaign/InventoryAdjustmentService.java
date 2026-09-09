package com.qinghe.marketing.campaign;

import com.qinghe.marketing.shared.clock.BusinessClock;
import com.qinghe.marketing.shared.error.QingheBusinessException;
import com.qinghe.marketing.shared.error.QingheErrorCode;
import com.qinghe.marketing.shared.id.BusinessIdGenerator;
import com.qinghe.marketing.shared.id.BusinessIdType;
import org.springframework.stereotype.Service;

@Service
public class InventoryAdjustmentService {

    private final InventoryAdjustmentRepository adjustmentRepository;
    private final CampaignRepository campaignRepository;
    private final CampaignStockCache stockCache;
    private final BusinessIdGenerator idGenerator;
    private final BusinessClock clock;

    public InventoryAdjustmentService(InventoryAdjustmentRepository adjustmentRepository,
                                      CampaignRepository campaignRepository,
                                      CampaignStockCache stockCache,
                                      BusinessIdGenerator idGenerator,
                                      BusinessClock clock) {
        this.adjustmentRepository = adjustmentRepository;
        this.campaignRepository = campaignRepository;
        this.stockCache = stockCache;
        this.idGenerator = idGenerator;
        this.clock = clock;
    }

    public InventoryAdjustment create(String campaignNo, long incrementStock, String reason,
                                      long expectedCampaignVersion, String applicantId) {
        if (incrementStock <= 0 || blank(reason) || blank(applicantId)) {
            throw new QingheBusinessException(QingheErrorCode.INVALID_ARGUMENT,
                    "positive inventory increment, reason and applicant are required");
        }
        Campaign campaign = requireCampaign(campaignNo);
        requireAdjustable(campaign);
        if (campaign.version() != expectedCampaignVersion) {
            throw new QingheBusinessException(QingheErrorCode.VERSION_CONFLICT,
                    "campaign version changed");
        }
        return adjustmentRepository.create(idGenerator.next(BusinessIdType.INVENTORY_ADJUSTMENT),
                campaign.id(), incrementStock, reason, applicantId, clock.dateTime());
    }

    public InventoryAdjustment submit(String adjustmentNo, long expectedVersion, String applicantId) {
        InventoryAdjustment adjustment = require(adjustmentNo);
        if (!adjustment.applicantId().equals(applicantId)) {
            throw new QingheBusinessException(QingheErrorCode.FORBIDDEN,
                    "only the applicant can submit this inventory adjustment");
        }
        Campaign campaign = campaignRepository.findById(adjustment.campaignId())
                .orElseThrow(() -> notFound("campaign does not exist"));
        requireAdjustable(campaign);
        return adjustmentRepository.submit(adjustment.id(), expectedVersion, clock.dateTime());
    }

    public InventoryAdjustmentResult review(String adjustmentNo, ReviewDecision decision,
                                            long expectedVersion, String reviewerId, String comment) {
        InventoryAdjustment adjustment = require(adjustmentNo);
        if (decision == null || blank(reviewerId)) {
            throw new QingheBusinessException(QingheErrorCode.INVALID_ARGUMENT,
                    "review decision and reviewer are required");
        }
        if (adjustment.applicantId().equals(reviewerId)) {
            throw new QingheBusinessException(QingheErrorCode.SELF_APPROVAL_NOT_ALLOWED,
                    "inventory applicant cannot review the adjustment");
        }
        if (decision == ReviewDecision.REJECT) {
            if (blank(comment)) {
                throw new QingheBusinessException(QingheErrorCode.INVALID_ARGUMENT,
                        "rejection comment is required");
            }
            InventoryAdjustment rejected = adjustmentRepository.reject(
                    adjustment.id(), expectedVersion, reviewerId, comment, clock.dateTime());
            CampaignInventory inventory = campaignRepository.requireInventory(adjustment.campaignId());
            return new InventoryAdjustmentResult(rejected, inventory.totalStock(), inventory.totalStock());
        }
        InventoryAdjustmentResult result = adjustmentRepository.approveAndApply(
                adjustment.id(), expectedVersion, reviewerId, comment, clock.dateTime());
        stockCache.applyApprovedIncrease(adjustment.campaignId(), adjustment.adjustmentNo(),
                adjustment.incrementStock());
        return result;
    }

    public InventoryAdjustment require(String adjustmentNo) {
        return adjustmentRepository.findByAdjustmentNo(adjustmentNo)
                .orElseThrow(() -> notFound("inventory adjustment does not exist"));
    }

    private Campaign requireCampaign(String campaignNo) {
        return campaignRepository.findByCampaignNo(campaignNo)
                .orElseThrow(() -> notFound("campaign does not exist"));
    }

    private static void requireAdjustable(Campaign campaign) {
        if (campaign.status() != CampaignStatus.SCHEDULED && campaign.status() != CampaignStatus.ACTIVE) {
            throw new QingheBusinessException(QingheErrorCode.CAMPAIGN_STATE_CONFLICT,
                    "campaign does not allow inventory adjustment");
        }
    }

    private static QingheBusinessException notFound(String message) {
        return new QingheBusinessException(QingheErrorCode.RESOURCE_NOT_FOUND, message);
    }

    private static boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
