package com.qinghe.marketing.reconciliation;

public final class ReconciliationMatchSummary {
    private final int unmatchedRows;
    private final int matchedRows;
    private final int differenceRows;
    private final int directMatchedRows;
    private final int franchiseEligibleRows;

    public ReconciliationMatchSummary(int unmatchedRows, int matchedRows, int differenceRows,
                                      int directMatchedRows, int franchiseEligibleRows) {
        this.unmatchedRows = unmatchedRows; this.matchedRows = matchedRows;
        this.differenceRows = differenceRows; this.directMatchedRows = directMatchedRows;
        this.franchiseEligibleRows = franchiseEligibleRows;
    }

    public int unmatchedRows() { return unmatchedRows; }
    public int matchedRows() { return matchedRows; }
    public int differenceRows() { return differenceRows; }
    public int directMatchedRows() { return directMatchedRows; }
    public int franchiseEligibleRows() { return franchiseEligibleRows; }
}
