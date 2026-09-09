package com.qinghe.marketing.campaign;

import com.qinghe.marketing.shared.clock.BusinessClock;
import com.qinghe.marketing.shared.error.QingheBusinessException;
import com.qinghe.marketing.shared.error.QingheErrorCode;
import com.qinghe.marketing.shared.id.BusinessIdGenerator;
import com.qinghe.marketing.shared.id.BusinessIdType;
import org.springframework.stereotype.Service;

@Service
public class BenefitTemplateService {

    private final BenefitTemplateRepository repository;
    private final BenefitTemplateSnapshotCodec codec;
    private final BusinessIdGenerator idGenerator;
    private final BusinessClock clock;

    public BenefitTemplateService(BenefitTemplateRepository repository,
                                  BenefitTemplateSnapshotCodec codec,
                                  BusinessIdGenerator idGenerator,
                                  BusinessClock clock) {
        this.repository = repository;
        this.codec = codec;
        this.idGenerator = idGenerator;
        this.clock = clock;
    }

    public BenefitTemplate create(BenefitTemplateDraft draft) {
        validate(draft);
        return repository.create(idGenerator.next(BusinessIdType.BENEFIT_TEMPLATE), draft,
                codec.encode(draft), clock.dateTime());
    }

    public BenefitTemplate update(String templateNo, BenefitTemplateDraft draft, long expectedVersion) {
        validate(draft);
        BenefitTemplate existing = require(templateNo);
        if (repository.isLockedByPublishedCampaign(existing.id())) {
            throw new QingheBusinessException(QingheErrorCode.TEMPLATE_VERSION_LOCKED,
                    "published campaign template snapshot cannot be overwritten");
        }
        return repository.update(templateNo, draft, codec.encode(draft), expectedVersion, clock.dateTime());
    }

    public BenefitTemplate require(String templateNo) {
        return repository.findByTemplateNo(templateNo)
                .orElseThrow(() -> new QingheBusinessException(
                        QingheErrorCode.RESOURCE_NOT_FOUND, "benefit template does not exist"));
    }

    public java.util.List<BenefitTemplate> list(int pageNo, int pageSize) {
        validatePage(pageNo, pageSize);
        return repository.list((pageNo - 1) * pageSize, pageSize);
    }

    private static void validatePage(int pageNo, int pageSize) {
        if (pageNo < 1 || pageSize < 1 || pageSize > 100) {
            throw invalid("pageNo must be positive and pageSize must be between 1 and 100");
        }
    }

    private static void validate(BenefitTemplateDraft draft) {
        if (draft == null || blank(draft.templateName()) || blank(draft.title())
                || draft.benefitType() == null || draft.validityType() == null
                || blank(draft.usageRulesJson())) {
            throw invalid("benefit template is incomplete");
        }
        if (draft.benefitType() == BenefitType.FREE_PRODUCT && blank(draft.productCode())) {
            throw invalid("FREE_PRODUCT requires productCode");
        }
        if (draft.benefitType() == BenefitType.CASH_DISCOUNT
                && (draft.benefitValueFen() == null || draft.benefitValueFen() <= 0)) {
            throw invalid("CASH_DISCOUNT requires positive benefitValueFen");
        }
        if (draft.validityType() == ValidityType.RELATIVE_DAYS
                && (draft.validityValue() == null || draft.validityValue() <= 0)) {
            throw invalid("RELATIVE_DAYS requires positive validityValue");
        }
        if (draft.validityType() == ValidityType.FIXED_RANGE
                && (draft.validFrom() == null || draft.validUntil() == null
                || !draft.validUntil().isAfter(draft.validFrom()))) {
            throw invalid("FIXED_RANGE requires a valid time range");
        }
    }

    private static QingheBusinessException invalid(String message) {
        return new QingheBusinessException(QingheErrorCode.INVALID_ARGUMENT, message);
    }

    private static boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
