package com.qinghe.marketing.redemption;

import com.qinghe.marketing.campaign.BenefitType;
import com.qinghe.marketing.entitlement.EntitlementStatus;
import java.time.LocalDateTime;

public final class PosEntitlementSnapshot {
    private final long id;
    private final String entitlementNo;
    private final long campaignId;
    private final EntitlementStatus status;
    private final LocalDateTime validFrom;
    private final LocalDateTime validUntil;
    private final long version;
    private final String title;
    private final BenefitType benefitType;
    private final String productCode;
    private final Long benefitValueFen;

    public PosEntitlementSnapshot(long id, String entitlementNo, long campaignId,
                                  EntitlementStatus status, LocalDateTime validFrom,
                                  LocalDateTime validUntil, long version, String title,
                                  BenefitType benefitType, String productCode, Long benefitValueFen) {
        this.id = id; this.entitlementNo = entitlementNo; this.campaignId = campaignId;
        this.status = status; this.validFrom = validFrom; this.validUntil = validUntil;
        this.version = version; this.title = title; this.benefitType = benefitType;
        this.productCode = productCode; this.benefitValueFen = benefitValueFen;
    }
    public long id() { return id; }
    public String entitlementNo() { return entitlementNo; }
    public long campaignId() { return campaignId; }
    public EntitlementStatus status() { return status; }
    public LocalDateTime validFrom() { return validFrom; }
    public LocalDateTime validUntil() { return validUntil; }
    public long version() { return version; }
    public String title() { return title; }
    public BenefitType benefitType() { return benefitType; }
    public String productCode() { return productCode; }
    public Long benefitValueFen() { return benefitValueFen; }
}
