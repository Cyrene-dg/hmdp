package com.qinghe.marketing.campaign;

import java.time.LocalDateTime;

public final class BenefitTemplateDraft {

    private final String templateName;
    private final BenefitType benefitType;
    private final String title;
    private final String description;
    private final String productCode;
    private final Long benefitValueFen;
    private final ValidityType validityType;
    private final Integer validityValue;
    private final LocalDateTime validFrom;
    private final LocalDateTime validUntil;
    private final String usageRulesJson;

    public BenefitTemplateDraft(String templateName, BenefitType benefitType, String title,
                                String description, String productCode, Long benefitValueFen,
                                ValidityType validityType, Integer validityValue,
                                LocalDateTime validFrom, LocalDateTime validUntil,
                                String usageRulesJson) {
        this.templateName = templateName;
        this.benefitType = benefitType;
        this.title = title;
        this.description = description;
        this.productCode = productCode;
        this.benefitValueFen = benefitValueFen;
        this.validityType = validityType;
        this.validityValue = validityValue;
        this.validFrom = validFrom;
        this.validUntil = validUntil;
        this.usageRulesJson = usageRulesJson;
    }

    public String templateName() { return templateName; }
    public BenefitType benefitType() { return benefitType; }
    public String title() { return title; }
    public String description() { return description; }
    public String productCode() { return productCode; }
    public Long benefitValueFen() { return benefitValueFen; }
    public ValidityType validityType() { return validityType; }
    public Integer validityValue() { return validityValue; }
    public LocalDateTime validFrom() { return validFrom; }
    public LocalDateTime validUntil() { return validUntil; }
    public String usageRulesJson() { return usageRulesJson; }
}
