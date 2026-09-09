package com.qinghe.marketing.redemption;

import com.qinghe.marketing.campaign.BenefitType;
import com.qinghe.marketing.entitlement.EntitlementStatus;

import java.time.LocalDateTime;

public final class PosVerificationResult {
    private final String rightNo;
    private final String title;
    private final EntitlementStatus status;
    private final LocalDateTime validUntil;
    private final BenefitType benefitType;
    private final String productCode;
    private final Long benefitValueFen;

    public PosVerificationResult(PosEntitlementSnapshot entitlement) {
        this.rightNo = entitlement.entitlementNo();
        this.title = entitlement.title();
        this.status = entitlement.status();
        this.validUntil = entitlement.validUntil();
        this.benefitType = entitlement.benefitType();
        this.productCode = entitlement.productCode();
        this.benefitValueFen = entitlement.benefitValueFen();
    }
    public String rightNo() { return rightNo; }
    public String title() { return title; }
    public EntitlementStatus status() { return status; }
    public LocalDateTime validUntil() { return validUntil; }
    public BenefitType benefitType() { return benefitType; }
    public String productCode() { return productCode; }
    public Long benefitValueFen() { return benefitValueFen; }
}
