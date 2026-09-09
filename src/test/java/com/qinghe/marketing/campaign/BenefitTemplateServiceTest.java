package com.qinghe.marketing.campaign;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qinghe.marketing.shared.clock.BusinessClock;
import com.qinghe.marketing.shared.error.QingheBusinessException;
import com.qinghe.marketing.shared.error.QingheErrorCode;
import com.qinghe.marketing.shared.id.BusinessIdGenerator;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BenefitTemplateServiceTest {

    private final BusinessClock clock = () -> Instant.parse("2026-09-09T08:00:00Z");

    @Test
    void shouldValidateBenefitAndValidityCombinations() {
        BenefitTemplateService service = service(mock(BenefitTemplateRepository.class));
        BenefitTemplateDraft missingProduct = new BenefitTemplateDraft("券", BenefitType.FREE_PRODUCT,
                "免费饮品", null, null, null, ValidityType.RELATIVE_DAYS,
                7, null, null, "{}");
        BenefitTemplateDraft invalidCash = new BenefitTemplateDraft("券", BenefitType.CASH_DISCOUNT,
                "立减", null, null, 0L, ValidityType.RELATIVE_DAYS,
                7, null, null, "{}");

        assertEquals(QingheErrorCode.INVALID_ARGUMENT, assertThrows(QingheBusinessException.class,
                () -> service.create(missingProduct)).errorCode());
        assertEquals(QingheErrorCode.INVALID_ARGUMENT, assertThrows(QingheBusinessException.class,
                () -> service.create(invalidCash)).errorCode());
    }

    @Test
    void shouldRefuseToOverwriteTemplateUsedByPublishedCampaign() {
        BenefitTemplateRepository repository = mock(BenefitTemplateRepository.class);
        BenefitTemplate existing = new BenefitTemplate(1L, "TPL-1", validDraft(),
                BenefitTemplateStatus.ACTIVE, 3L);
        when(repository.findByTemplateNo("TPL-1")).thenReturn(Optional.of(existing));
        when(repository.isLockedByPublishedCampaign(1L)).thenReturn(true);
        BenefitTemplateService service = service(repository);

        QingheBusinessException exception = assertThrows(QingheBusinessException.class,
                () -> service.update("TPL-1", validDraft(), 3L));

        assertEquals(QingheErrorCode.TEMPLATE_VERSION_LOCKED, exception.errorCode());
        verify(repository, never()).update(anyString(), any(BenefitTemplateDraft.class),
                anyString(), anyLong(), any());
    }

    private BenefitTemplateService service(BenefitTemplateRepository repository) {
        return new BenefitTemplateService(repository,
                new BenefitTemplateSnapshotCodec(new ObjectMapper()),
                new BusinessIdGenerator(clock), clock);
    }

    private static BenefitTemplateDraft validDraft() {
        return new BenefitTemplateDraft("饮品券", BenefitType.FREE_PRODUCT, "免费饮品",
                null, "DRINK-001", null, ValidityType.RELATIVE_DAYS,
                7, null, null, "{\"allowStacking\":false}");
    }
}
