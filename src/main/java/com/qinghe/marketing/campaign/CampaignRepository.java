package com.qinghe.marketing.campaign;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface CampaignRepository {

    Campaign createDraft(String campaignNo, long templateId, CampaignDraftCommand command,
                         String createdBy, List<CampaignStoreSnapshot> stores, LocalDateTime now);

    Campaign updateDraft(long campaignId, long templateId, CampaignDraftCommand command,
                         String operatorId, List<CampaignStoreSnapshot> stores,
                         long expectedVersion, LocalDateTime now);

    Optional<Campaign> findByCampaignNo(String campaignNo);

    Optional<Campaign> findById(long campaignId);

    Optional<Campaign> findByIdForUpdate(long campaignId);

    List<Campaign> list(int offset, int limit);

    CampaignInventory requireInventory(long campaignId);

    List<CampaignStoreSnapshot> findStores(long campaignId);

    Campaign transition(long campaignId, CampaignStatus expectedStatus, CampaignStatus nextStatus,
                        long expectedVersion, String approvedBy, LocalDateTime now);

    void saveReview(CampaignReview review);

    void savePublication(CampaignPublicationSnapshot snapshot);

    Optional<CampaignPublicationSnapshot> findPublication(long campaignId);

    Campaign terminate(long campaignId, long expectedVersion, String operatorId,
                       String reason, LocalDateTime now);

    int activateScheduled(LocalDateTime now);

    int endActive(LocalDateTime now);
}
