package com.qinghe.marketing.settlement;

public final class SettlementGenerateCommand {
    private final long expectedReconVersion;
    private final String comment;
    private final String operatorId;
    private final String requestId;

    public SettlementGenerateCommand(long expectedReconVersion, String comment,
                                     String operatorId, String requestId) {
        this.expectedReconVersion = expectedReconVersion; this.comment = comment;
        this.operatorId = operatorId; this.requestId = requestId;
    }
    public long expectedReconVersion() { return expectedReconVersion; }
    public String comment() { return comment; }
    public String operatorId() { return operatorId; }
    public String requestId() { return requestId; }
}
