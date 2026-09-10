package com.qinghe.marketing.reconciliation;

public final class ReconciliationImportSummary {
    private final int totalChunks;
    private final int completedChunks;
    private final int importedRows;
    private final int successRows;
    private final int errorRows;
    private final int nextLineNo;

    public ReconciliationImportSummary(int totalChunks, int completedChunks, int importedRows,
                                       int successRows, int errorRows, int nextLineNo) {
        this.totalChunks = totalChunks;
        this.completedChunks = completedChunks;
        this.importedRows = importedRows;
        this.successRows = successRows;
        this.errorRows = errorRows;
        this.nextLineNo = nextLineNo;
    }

    public int totalChunks() { return totalChunks; }
    public int completedChunks() { return completedChunks; }
    public int importedRows() { return importedRows; }
    public int successRows() { return successRows; }
    public int errorRows() { return errorRows; }
    public int nextLineNo() { return nextLineNo; }
}
