package com.qinghe.marketing.settlement;

public final class SettlementConfirmCommand {
    private final long expectedVersion;
    private final int expectedDetailCount;
    private final long expectedTotalFen;
    private final String comment;
    private final String operatorId;
    private final String requestId;

    public SettlementConfirmCommand(long expectedVersion, int expectedDetailCount,
                                    long expectedTotalFen, String comment,
                                    String operatorId, String requestId) {
        this.expectedVersion = expectedVersion; this.expectedDetailCount = expectedDetailCount;
        this.expectedTotalFen = expectedTotalFen; this.comment = comment;
        this.operatorId = operatorId; this.requestId = requestId;
    }
    public long expectedVersion() { return expectedVersion; }
    public int expectedDetailCount() { return expectedDetailCount; }
    public long expectedTotalFen() { return expectedTotalFen; }
    public String comment() { return comment; }
    public String operatorId() { return operatorId; }
    public String requestId() { return requestId; }
}
