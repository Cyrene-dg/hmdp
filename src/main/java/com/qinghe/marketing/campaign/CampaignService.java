package com.qinghe.marketing.campaign;

import com.qinghe.marketing.shared.clock.BusinessClock;
import com.qinghe.marketing.shared.error.QingheBusinessException;
import com.qinghe.marketing.shared.error.QingheErrorCode;
import com.qinghe.marketing.shared.id.BusinessIdGenerator;
import com.qinghe.marketing.shared.id.BusinessIdType;
import com.qinghe.marketing.store.StoreOwnershipType;
import com.qinghe.marketing.store.StoreRecord;
import com.qinghe.marketing.store.StoreRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class CampaignService {

    private final CampaignRepository campaignRepository;
    private final BenefitTemplateRepository templateRepository;
    private final BenefitTemplateSnapshotCodec templateCodec;
    private final StoreRepository storeRepository;
    private final CampaignApprovalTransactionService approvalTransactionService;
    private final CampaignStockCache stockCache;
    private final BusinessIdGenerator idGenerator;
    private final BusinessClock clock;

    public CampaignService(CampaignRepository campaignRepository,
                           BenefitTemplateRepository templateRepository,
                           BenefitTemplateSnapshotCodec templateCodec,
                           StoreRepository storeRepository,
                           CampaignApprovalTransactionService approvalTransactionService,
                           CampaignStockCache stockCache,
                           BusinessIdGenerator idGenerator,
                           BusinessClock clock) {
        this.campaignRepository = campaignRepository;
        this.templateRepository = templateRepository;
        this.templateCodec = templateCodec;
        this.storeRepository = storeRepository;
        this.approvalTransactionService = approvalTransactionService;
        this.stockCache = stockCache;
        this.idGenerator = idGenerator;
        this.clock = clock;
    }

    public Campaign createDraft(CampaignDraftCommand command, String operatorId) {
        validateDraft(command, operatorId);
        BenefitTemplate template = templateRepository.findByTemplateNo(command.templateNo())
                .orElseThrow(() -> notFound("benefit template does not exist"));
        if (template.status() != BenefitTemplateStatus.ACTIVE) {
            throw incomplete("benefit template is disabled");
        }
        List<CampaignStoreSnapshot> stores = resolveStores(command.storeCodes(), command.franchiseSubsidyFen());
        return campaignRepository.createDraft(idGenerator.next(BusinessIdType.CAMPAIGN), template.id(),
                command, operatorId, stores, clock.dateTime());
    }

    public Campaign updateDraft(String campaignNo, CampaignDraftCommand command,
                                long expectedVersion, String operatorId) {
        validateDraft(command, operatorId);
        Campaign campaign = require(campaignNo);
        if (!campaign.createdBy().equals(operatorId)) {
            throw new QingheBusinessException(QingheErrorCode.FORBIDDEN,
                    "only the campaign owner can update this draft");
        }
        if (campaign.status() != CampaignStatus.DRAFT && campaign.status() != CampaignStatus.REJECTED) {
            throw state("published or pending campaign rules cannot be overwritten");
        }
        BenefitTemplate template = templateRepository.findByTemplateNo(command.templateNo())
                .orElseThrow(() -> notFound("benefit template does not exist"));
        if (template.status() != BenefitTemplateStatus.ACTIVE) {
            throw incomplete("benefit template is disabled");
        }
        List<CampaignStoreSnapshot> stores = resolveStores(
                command.storeCodes(), command.franchiseSubsidyFen());
        return campaignRepository.updateDraft(campaign.id(), template.id(), command, operatorId,
                stores, expectedVersion, clock.dateTime());
    }

    public Campaign submit(String campaignNo, long expectedVersion, String operatorId) {
        Campaign campaign = require(campaignNo);
        if (!campaign.createdBy().equals(operatorId)) {
            throw new QingheBusinessException(QingheErrorCode.FORBIDDEN,
                    "only the campaign owner can submit this draft");
        }
        if (campaign.status() != CampaignStatus.DRAFT) {
            throw state("only a draft campaign can be submitted");
        }
        validateStoredCampaign(campaign);
        return campaignRepository.transition(campaign.id(), CampaignStatus.DRAFT,
                CampaignStatus.PENDING_APPROVAL, expectedVersion, null, clock.dateTime());
    }

    public Campaign review(String campaignNo, ReviewDecision decision, long expectedVersion,
                           String reviewerId, String comment) {
        Campaign campaign = require(campaignNo);
        if (decision == null || blank(reviewerId)) {
            throw invalid("review decision and reviewer are required");
        }
        if (decision == ReviewDecision.REJECT) {
            if (blank(comment)) {
                throw invalid("rejection comment is required");
            }
            return approvalTransactionService.reject(campaign, expectedVersion, reviewerId, comment);
        }
        if (campaign.status() == CampaignStatus.SCHEDULED || campaign.status() == CampaignStatus.ACTIVE) {
            CampaignPublicationSnapshot published = campaignRepository.findPublication(campaign.id())
                    .orElseThrow(() -> incomplete("published campaign snapshot does not exist"));
            if (!stockCache.currentAvailableStock(campaign.id()).isPresent()) {
                stockCache.initialize(campaign.id(), published.initialStock());
            }
            return campaign;
        }
        validateStoredCampaign(campaign);
        BenefitTemplate template = templateRepository.findById(campaign.templateId())
                .orElseThrow(() -> incomplete("benefit template does not exist"));
        if (template.status() != BenefitTemplateStatus.ACTIVE) {
            throw incomplete("benefit template is disabled");
        }
        CampaignInventory inventory = campaignRepository.requireInventory(campaign.id());
        CampaignPublicationSnapshot snapshot = new CampaignPublicationSnapshot(
                campaign.id(), campaign.ruleVersion(), templateCodec.encode(template.draft()),
                campaign.beginAt(), campaign.endAt(), campaign.memberClaimLimit(),
                inventory.totalStock(), campaign.franchiseSubsidyFen(), reviewerId, clock.dateTime());
        Campaign approved = approvalTransactionService.approve(
                campaign, expectedVersion, reviewerId, comment, snapshot);
        stockCache.initialize(campaign.id(), inventory.totalStock());
        return approved;
    }

    public Campaign terminate(String campaignNo, long expectedVersion,
                              String operatorId, String reason) {
        if (blank(reason)) {
            throw invalid("termination reason is required");
        }
        Campaign campaign = require(campaignNo);
        if (campaign.status() != CampaignStatus.ACTIVE) {
            throw state("only an active campaign can be terminated");
        }
        return campaignRepository.terminate(campaign.id(), expectedVersion,
                operatorId, reason, clock.dateTime());
    }

    public int advanceTimeBasedStates() {
        LocalDateTime now = clock.dateTime();
        int activated = campaignRepository.activateScheduled(now);
        return activated + campaignRepository.endActive(now);
    }

    public Campaign require(String campaignNo) {
        return campaignRepository.findByCampaignNo(campaignNo)
                .orElseThrow(() -> notFound("campaign does not exist"));
    }

    public List<Campaign> list(int pageNo, int pageSize) {
        if (pageNo < 1 || pageSize < 1 || pageSize > 100) {
            throw invalid("pageNo must be positive and pageSize must be between 1 and 100");
        }
        return campaignRepository.list((pageNo - 1) * pageSize, pageSize);
    }

    private void validateStoredCampaign(Campaign campaign) {
        if (campaign.memberClaimLimit() != 1 || campaign.beginAt() == null || campaign.endAt() == null
                || !campaign.endAt().isAfter(campaign.beginAt())
                || !campaign.endAt().isAfter(clock.dateTime())) {
            throw incomplete("campaign timing or claim limit is invalid");
        }
        CampaignInventory inventory = campaignRepository.requireInventory(campaign.id());
        if (inventory.totalStock() <= 0) {
            throw incomplete("campaign stock must be positive");
        }
        List<CampaignStoreSnapshot> snapshots = campaignRepository.findStores(campaign.id());
        if (snapshots.isEmpty()) {
            throw incomplete("campaign requires at least one store");
        }
        Long franchiseSubsidy = campaign.franchiseSubsidyFen();
        for (CampaignStoreSnapshot snapshot : snapshots) {
            StoreRecord current = storeRepository.findById(snapshot.storeId())
                    .orElseThrow(() -> new QingheBusinessException(
                            QingheErrorCode.STORE_NOT_ELIGIBLE, "campaign store does not exist"));
            if (!current.active()) {
                throw new QingheBusinessException(QingheErrorCode.STORE_NOT_ELIGIBLE,
                        "campaign store is disabled");
            }
            if (current.ownershipType() != snapshot.ownershipType()) {
                throw new QingheBusinessException(QingheErrorCode.STORE_NOT_ELIGIBLE,
                        "campaign store ownership changed before publication");
            }
            long expectedSubsidy = snapshot.ownershipType() == StoreOwnershipType.DIRECT
                    ? 0L : requiredSubsidy(franchiseSubsidy);
            if (snapshot.subsidyFen() != expectedSubsidy) {
                throw incomplete("campaign store subsidy snapshot is inconsistent");
            }
        }
    }

    private List<CampaignStoreSnapshot> resolveStores(List<String> storeCodes, Long franchiseSubsidyFen) {
        if (storeCodes == null || storeCodes.isEmpty()) {
            throw incomplete("campaign requires at least one store");
        }
        Set<String> unique = new HashSet<String>();
        List<StoreRecord> resolved = new ArrayList<StoreRecord>();
        boolean hasFranchise = false;
        for (String storeCode : storeCodes) {
            if (blank(storeCode) || !unique.add(storeCode)) {
                throw invalid("store codes must be non-empty and unique");
            }
            StoreRecord store = storeRepository.findByExternalStoreCode(storeCode)
                    .orElseThrow(() -> new QingheBusinessException(
                            QingheErrorCode.STORE_NOT_ELIGIBLE, "campaign store does not exist: " + storeCode));
            if (!store.active()) {
                throw new QingheBusinessException(QingheErrorCode.STORE_NOT_ELIGIBLE,
                        "campaign store is disabled: " + storeCode);
            }
            hasFranchise |= store.franchise();
            resolved.add(store);
        }
        if (hasFranchise && (franchiseSubsidyFen == null || franchiseSubsidyFen < 0)) {
            throw incomplete("franchiseSubsidyFen is required for franchise stores");
        }
        if (!hasFranchise && franchiseSubsidyFen != null && franchiseSubsidyFen < 0) {
            throw invalid("franchiseSubsidyFen cannot be negative");
        }
        List<CampaignStoreSnapshot> snapshots = new ArrayList<CampaignStoreSnapshot>();
        for (StoreRecord store : resolved) {
            snapshots.add(new CampaignStoreSnapshot(store.id(), store.externalStoreCode(),
                    store.ownershipType(), store.franchise() ? franchiseSubsidyFen : 0L,
                    "PARTICIPATING", 1L));
        }
        return snapshots;
    }

    private static void validateDraft(CampaignDraftCommand command, String operatorId) {
        if (command == null || blank(operatorId) || blank(command.name()) || blank(command.templateNo())
                || command.claimBeginAt() == null || command.claimEndAt() == null
                || !command.claimEndAt().isAfter(command.claimBeginAt())
                || command.initialStock() <= 0 || command.memberClaimLimit() != 1) {
            throw incomplete("campaign draft is incomplete");
        }
    }

    private static long requiredSubsidy(Long value) {
        if (value == null || value < 0) {
            throw incomplete("franchise subsidy is missing");
        }
        return value;
    }

    private static QingheBusinessException invalid(String message) {
        return new QingheBusinessException(QingheErrorCode.INVALID_ARGUMENT, message);
    }

    private static QingheBusinessException incomplete(String message) {
        return new QingheBusinessException(QingheErrorCode.CAMPAIGN_INCOMPLETE, message);
    }

    private static QingheBusinessException state(String message) {
        return new QingheBusinessException(QingheErrorCode.CAMPAIGN_STATE_CONFLICT, message);
    }

    private static QingheBusinessException notFound(String message) {
        return new QingheBusinessException(QingheErrorCode.RESOURCE_NOT_FOUND, message);
    }

    private static boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
