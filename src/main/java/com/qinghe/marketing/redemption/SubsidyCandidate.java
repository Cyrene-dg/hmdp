package com.qinghe.marketing.redemption;

import java.time.LocalDateTime;

public final class SubsidyCandidate {
    private final long redemptionId;
    private final long campaignId;
    private final long storeId;
    private final long subsidyFen;
    private final SubsidyCandidateStatus status;
    private final long snapshotVersion;
    private final LocalDateTime createdAt;

    public SubsidyCandidate(long redemptionId, long campaignId, long storeId, long subsidyFen,
                            SubsidyCandidateStatus status, long snapshotVersion,
                            LocalDateTime createdAt) {
        this.redemptionId = redemptionId; this.campaignId = campaignId; this.storeId = storeId;
        this.subsidyFen = subsidyFen; this.status = status;
        this.snapshotVersion = snapshotVersion; this.createdAt = createdAt;
    }
    public long redemptionId() { return redemptionId; }
    public long campaignId() { return campaignId; }
    public long storeId() { return storeId; }
    public long subsidyFen() { return subsidyFen; }
    public SubsidyCandidateStatus status() { return status; }
    public long snapshotVersion() { return snapshotVersion; }
    public LocalDateTime createdAt() { return createdAt; }
}
