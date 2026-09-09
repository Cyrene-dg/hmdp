package com.qinghe.marketing.shared.id;

public enum BusinessIdType {
    MEMBER_SESSION("SES"),
    STORE_IMPORT("IMP"),
    CAMPAIGN("CAM"),
    INVENTORY_ADJUSTMENT("IAD"),
    CLAIM("CLM"),
    ENTITLEMENT("ENT"),
    REDEMPTION("RDM"),
    REVERSAL("REV"),
    RECONCILIATION_BATCH("RCB"),
    SETTLEMENT_BATCH("STB"),
    OUTBOX_EVENT("EVT");

    private final String prefix;

    BusinessIdType(String prefix) {
        this.prefix = prefix;
    }

    public String prefix() {
        return prefix;
    }
}
