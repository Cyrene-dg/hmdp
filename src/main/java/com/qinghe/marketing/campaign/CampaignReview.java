package com.qinghe.marketing.campaign;

import java.time.LocalDateTime;

public final class CampaignReview {

    private final String reviewNo;
    private final long campaignId;
    private final ReviewDecision decision;
    private final String applicantId;
    private final String reviewerId;
    private final CampaignStatus beforeStatus;
    private final CampaignStatus afterStatus;
    private final String comment;
    private final LocalDateTime reviewedAt;

    public CampaignReview(String reviewNo, long campaignId, ReviewDecision decision,
                          String applicantId, String reviewerId, CampaignStatus beforeStatus,
                          CampaignStatus afterStatus, String comment, LocalDateTime reviewedAt) {
        this.reviewNo = reviewNo;
        this.campaignId = campaignId;
        this.decision = decision;
        this.applicantId = applicantId;
        this.reviewerId = reviewerId;
        this.beforeStatus = beforeStatus;
        this.afterStatus = afterStatus;
        this.comment = comment;
        this.reviewedAt = reviewedAt;
    }

    public String reviewNo() { return reviewNo; }
    public long campaignId() { return campaignId; }
    public ReviewDecision decision() { return decision; }
    public String applicantId() { return applicantId; }
    public String reviewerId() { return reviewerId; }
    public CampaignStatus beforeStatus() { return beforeStatus; }
    public CampaignStatus afterStatus() { return afterStatus; }
    public String comment() { return comment; }
    public LocalDateTime reviewedAt() { return reviewedAt; }
}
