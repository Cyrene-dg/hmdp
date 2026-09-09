package com.qinghe.marketing.campaign;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.List;

public interface BenefitTemplateRepository {

    BenefitTemplate create(String templateNo, BenefitTemplateDraft draft, String rulesSnapshotJson,
                           LocalDateTime now);

    Optional<BenefitTemplate> findByTemplateNo(String templateNo);

    Optional<BenefitTemplate> findById(long id);

    List<BenefitTemplate> list(int offset, int limit);

    BenefitTemplate update(String templateNo, BenefitTemplateDraft draft, String rulesSnapshotJson,
                           long expectedVersion, LocalDateTime now);

    boolean isLockedByPublishedCampaign(long templateId);
}
