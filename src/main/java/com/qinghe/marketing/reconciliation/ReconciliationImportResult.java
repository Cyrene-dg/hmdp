package com.qinghe.marketing.reconciliation;

public final class ReconciliationImportResult {
    private final String reconBatchNo;
    private final ReconciliationBatchStatus status;
    private final int totalRows;
    private final int successRows;
    private final int errorRows;
    private final boolean replayed;

    public ReconciliationImportResult(String reconBatchNo, ReconciliationBatchStatus status,
                                      int totalRows, int successRows, int errorRows,
                                      boolean replayed) {
        this.reconBatchNo = reconBatchNo;
        this.status = status;
        this.totalRows = totalRows;
        this.successRows = successRows;
        this.errorRows = errorRows;
        this.replayed = replayed;
    }

    public String reconBatchNo() { return reconBatchNo; }
    public ReconciliationBatchStatus status() { return status; }
    public int totalRows() { return totalRows; }
    public int successRows() { return successRows; }
    public int errorRows() { return errorRows; }
    public boolean replayed() { return replayed; }
}
