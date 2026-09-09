package com.qinghe.marketing.redemption;

import java.time.LocalDateTime;

public final class Redemption {
    private final long id;
    private final String redemptionNo;
    private final String posClientId;
    private final String posRequestNo;
    private final String requestDigest;
    private final String posOrderNo;
    private final String terminalNo;
    private final String operatorNo;
    private final long entitlementId;
    private final long storeId;
    private final RedemptionStatus status;
    private final LocalDateTime occurredAt;
    private final LocalDateTime firstProcessedAt;

    public Redemption(long id, String redemptionNo, String posClientId, String posRequestNo,
                      String requestDigest, String posOrderNo, String terminalNo,
                      String operatorNo, long entitlementId, long storeId,
                      RedemptionStatus status, LocalDateTime occurredAt,
                      LocalDateTime firstProcessedAt) {
        this.id = id; this.redemptionNo = redemptionNo; this.posClientId = posClientId;
        this.posRequestNo = posRequestNo; this.requestDigest = requestDigest;
        this.posOrderNo = posOrderNo; this.terminalNo = terminalNo; this.operatorNo = operatorNo;
        this.entitlementId = entitlementId; this.storeId = storeId; this.status = status;
        this.occurredAt = occurredAt; this.firstProcessedAt = firstProcessedAt;
    }
    public long id() { return id; }
    public String redemptionNo() { return redemptionNo; }
    public String posClientId() { return posClientId; }
    public String posRequestNo() { return posRequestNo; }
    public String requestDigest() { return requestDigest; }
    public String posOrderNo() { return posOrderNo; }
    public String terminalNo() { return terminalNo; }
    public String operatorNo() { return operatorNo; }
    public long entitlementId() { return entitlementId; }
    public long storeId() { return storeId; }
    public RedemptionStatus status() { return status; }
    public LocalDateTime occurredAt() { return occurredAt; }
    public LocalDateTime firstProcessedAt() { return firstProcessedAt; }
}
