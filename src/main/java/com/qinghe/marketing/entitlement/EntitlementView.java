package com.qinghe.marketing.entitlement;

import com.qinghe.marketing.campaign.BenefitType;

import java.time.LocalDateTime;

public final class EntitlementView {

    private final MemberEntitlement entitlement;
    private final String title;
    private final BenefitType benefitType;
    private final String usageRulesJson;
    private final int applicableStoreCount;

    public EntitlementView(MemberEntitlement entitlement, String title, BenefitType benefitType,
                           String usageRulesJson, int applicableStoreCount) {
        this.entitlement = entitlement;
        this.title = title;
        this.benefitType = benefitType;
        this.usageRulesJson = usageRulesJson;
        this.applicableStoreCount = applicableStoreCount;
    }

    public MemberEntitlement entitlement() { return entitlement; }
    public String entitlementNo() { return entitlement.entitlementNo(); }
    public String title() { return title; }
    public BenefitType benefitType() { return benefitType; }
    public EntitlementStatus status() { return entitlement.status(); }
    public LocalDateTime validFrom() { return entitlement.validFrom(); }
    public LocalDateTime validUntil() { return entitlement.validUntil(); }
    public String usageRulesJson() { return usageRulesJson; }
    public int applicableStoreCount() { return applicableStoreCount; }
}

