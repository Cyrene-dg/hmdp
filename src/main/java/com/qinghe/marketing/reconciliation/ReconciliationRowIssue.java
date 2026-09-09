package com.qinghe.marketing.reconciliation;

public final class ReconciliationRowIssue {
    private final int lineNo;
    private final String code;
    private final String rawDigest;

    public ReconciliationRowIssue(int lineNo, String code, String rawDigest) {
        this.lineNo = lineNo; this.code = code; this.rawDigest = rawDigest;
    }
    public int lineNo() { return lineNo; }
    public String code() { return code; }
    public String rawDigest() { return rawDigest; }
}
