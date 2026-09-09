package com.qinghe.marketing.campaign;

import java.time.LocalDateTime;

public final class CampaignPublicationSnapshot {

    private final long campaignId;
    private final long ruleVersion;
    private final String templateSnapshotJson;
    private final LocalDateTime claimBeginAt;
    private final LocalDateTime claimEndAt;
    private final int memberClaimLimit;
    private final long initialStock;
    private final Long franchiseSubsidyFen;
    private final String publishedBy;
    private final LocalDateTime publishedAt;

    public CampaignPublicationSnapshot(long campaignId, long ruleVersion, String templateSnapshotJson,
                                       LocalDateTime claimBeginAt, LocalDateTime claimEndAt,
                                       int memberClaimLimit, long initialStock, Long franchiseSubsidyFen,
                                       String publishedBy, LocalDateTime publishedAt) {
        this.campaignId = campaignId;
        this.ruleVersion = ruleVersion;
        this.templateSnapshotJson = templateSnapshotJson;
        this.claimBeginAt = claimBeginAt;
        this.claimEndAt = claimEndAt;
        this.memberClaimLimit = memberClaimLimit;
        this.initialStock = initialStock;
        this.franchiseSubsidyFen = franchiseSubsidyFen;
        this.publishedBy = publishedBy;
        this.publishedAt = publishedAt;
    }

    public long campaignId() { return campaignId; }
    public long ruleVersion() { return ruleVersion; }
    public String templateSnapshotJson() { return templateSnapshotJson; }
    public LocalDateTime claimBeginAt() { return claimBeginAt; }
    public LocalDateTime claimEndAt() { return claimEndAt; }
    public int memberClaimLimit() { return memberClaimLimit; }
    public long initialStock() { return initialStock; }
    public Long franchiseSubsidyFen() { return franchiseSubsidyFen; }
    public String publishedBy() { return publishedBy; }
    public LocalDateTime publishedAt() { return publishedAt; }
}
