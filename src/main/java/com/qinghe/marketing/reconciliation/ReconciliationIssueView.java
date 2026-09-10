package com.qinghe.marketing.reconciliation;

public final class ReconciliationIssueView {
    private final int lineNo;
    private final String errorCode;
    private final String rawDigest;

    public ReconciliationIssueView(int lineNo, String errorCode, String rawDigest) {
        this.lineNo = lineNo; this.errorCode = errorCode; this.rawDigest = rawDigest;
    }
    public int getLineNo() { return lineNo; }
    public String getErrorCode() { return errorCode; }
    public String getRawDigest() { return rawDigest; }
}
