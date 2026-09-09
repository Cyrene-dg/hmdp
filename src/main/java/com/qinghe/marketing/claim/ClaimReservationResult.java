package com.qinghe.marketing.claim;

public final class ClaimReservationResult {

    public enum Outcome {
        RESERVED,
        IDEMPOTENT_REPLAY,
        REQUEST_CONFLICT,
        MEMBER_ALREADY_RESERVED,
        SOLD_OUT,
        STOCK_NOT_INITIALIZED,
        CAMPAIGN_NOT_ACTIVE
    }

    private final Outcome outcome;
    private final String reservationId;
    private final String claimNo;
    private final String eventId;

    public ClaimReservationResult(Outcome outcome, String reservationId,
                                  String claimNo, String eventId) {
        this.outcome = outcome;
        this.reservationId = reservationId;
        this.claimNo = claimNo;
        this.eventId = eventId;
    }

    public Outcome outcome() { return outcome; }
    public String reservationId() { return reservationId; }
    public String claimNo() { return claimNo; }
    public String eventId() { return eventId; }
}
