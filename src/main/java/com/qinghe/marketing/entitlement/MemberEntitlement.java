package com.qinghe.marketing.entitlement;

import java.time.LocalDateTime;

public final class MemberEntitlement {

    private final long id;
    private final String entitlementNo;
    private final String rightCodeHash;
    private final byte[] encryptedRightCode;
    private final long sourceClaimId;
    private final long campaignId;
    private final long memberId;
    private final EntitlementStatus status;
    private final LocalDateTime validFrom;
    private final LocalDateTime validUntil;
    private final long version;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    public MemberEntitlement(long id, String entitlementNo, String rightCodeHash,
                             byte[] encryptedRightCode, long sourceClaimId, long campaignId,
                             long memberId, EntitlementStatus status, LocalDateTime validFrom,
                             LocalDateTime validUntil, long version, LocalDateTime createdAt,
                             LocalDateTime updatedAt) {
        this.id = id;
        this.entitlementNo = entitlementNo;
        this.rightCodeHash = rightCodeHash;
        this.encryptedRightCode = encryptedRightCode == null ? null : encryptedRightCode.clone();
        this.sourceClaimId = sourceClaimId;
        this.campaignId = campaignId;
        this.memberId = memberId;
        this.status = status;
        this.validFrom = validFrom;
        this.validUntil = validUntil;
        this.version = version;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public long id() { return id; }
    public String entitlementNo() { return entitlementNo; }
    public String rightCodeHash() { return rightCodeHash; }
    public byte[] encryptedRightCode() {
        return encryptedRightCode == null ? null : encryptedRightCode.clone();
    }
    public long sourceClaimId() { return sourceClaimId; }
    public long campaignId() { return campaignId; }
    public long memberId() { return memberId; }
    public EntitlementStatus status() { return status; }
    public LocalDateTime validFrom() { return validFrom; }
    public LocalDateTime validUntil() { return validUntil; }
    public long version() { return version; }
    public LocalDateTime createdAt() { return createdAt; }
    public LocalDateTime updatedAt() { return updatedAt; }
}

