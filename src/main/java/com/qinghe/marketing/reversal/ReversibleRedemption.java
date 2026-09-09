package com.qinghe.marketing.reversal;

import com.qinghe.marketing.redemption.RedemptionStatus;
import java.time.LocalDateTime;

public final class ReversibleRedemption {
    private final long id;
    private final String redemptionNo;
    private final String posOrderNo;
    private final long entitlementId;
    private final long storeId;
    private final RedemptionStatus status;
    private final LocalDateTime occurredAt;
    private final long version;

    public ReversibleRedemption(long id, String redemptionNo, String posOrderNo,
                                long entitlementId, long storeId, RedemptionStatus status,
                                LocalDateTime occurredAt, long version) {
        this.id = id; this.redemptionNo = redemptionNo; this.posOrderNo = posOrderNo;
        this.entitlementId = entitlementId; this.storeId = storeId; this.status = status;
        this.occurredAt = occurredAt; this.version = version;
    }
    public long id() { return id; }
    public String redemptionNo() { return redemptionNo; }
    public String posOrderNo() { return posOrderNo; }
    public long entitlementId() { return entitlementId; }
    public long storeId() { return storeId; }
    public RedemptionStatus status() { return status; }
    public LocalDateTime occurredAt() { return occurredAt; }
    public long version() { return version; }
}
