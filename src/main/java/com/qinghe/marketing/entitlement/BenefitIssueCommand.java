package com.qinghe.marketing.entitlement;

public final class BenefitIssueCommand {

    private final String eventId;
    private final String claimNo;
    private final String reservationId;
    private final long campaignId;
    private final long memberId;
    private final String requestId;

    public BenefitIssueCommand(String eventId, String claimNo, String reservationId,
                               long campaignId, long memberId, String requestId) {
        this.eventId = eventId;
        this.claimNo = claimNo;
        this.reservationId = reservationId;
        this.campaignId = campaignId;
        this.memberId = memberId;
        this.requestId = requestId;
    }

    public String eventId() { return eventId; }
    public String claimNo() { return claimNo; }
    public String reservationId() { return reservationId; }
    public long campaignId() { return campaignId; }
    public long memberId() { return memberId; }
    public String requestId() { return requestId; }
}

