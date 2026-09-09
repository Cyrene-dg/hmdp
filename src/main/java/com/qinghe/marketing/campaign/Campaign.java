package com.qinghe.marketing.campaign;

import java.time.LocalDateTime;

public final class Campaign {

    private final long id;
    private final String campaignNo;
    private final long templateId;
    private final String name;
    private final String description;
    private final CampaignStatus status;
    private final LocalDateTime beginAt;
    private final LocalDateTime endAt;
    private final int memberClaimLimit;
    private final Long franchiseSubsidyFen;
    private final long ruleVersion;
    private final long version;
    private final String createdBy;

    public Campaign(long id, String campaignNo, long templateId, String name, String description,
                    CampaignStatus status, LocalDateTime beginAt, LocalDateTime endAt,
                    int memberClaimLimit, Long franchiseSubsidyFen, long ruleVersion,
                    long version, String createdBy) {
        this.id = id;
        this.campaignNo = campaignNo;
        this.templateId = templateId;
        this.name = name;
        this.description = description;
        this.status = status;
        this.beginAt = beginAt;
        this.endAt = endAt;
        this.memberClaimLimit = memberClaimLimit;
        this.franchiseSubsidyFen = franchiseSubsidyFen;
        this.ruleVersion = ruleVersion;
        this.version = version;
        this.createdBy = createdBy;
    }

    public long id() { return id; }
    public String campaignNo() { return campaignNo; }
    public long templateId() { return templateId; }
    public String name() { return name; }
    public String description() { return description; }
    public CampaignStatus status() { return status; }
    public LocalDateTime beginAt() { return beginAt; }
    public LocalDateTime endAt() { return endAt; }
    public int memberClaimLimit() { return memberClaimLimit; }
    public Long franchiseSubsidyFen() { return franchiseSubsidyFen; }
    public long ruleVersion() { return ruleVersion; }
    public long version() { return version; }
    public String createdBy() { return createdBy; }
}
