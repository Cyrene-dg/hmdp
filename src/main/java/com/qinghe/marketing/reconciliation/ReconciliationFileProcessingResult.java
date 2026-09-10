package com.qinghe.marketing.reconciliation;

public final class ReconciliationFileProcessingResult {
    private final String sourceName;
    private final String reconBatchNo;
    private final ReconciliationFileOutcome outcome;
    private final String errorCode;

    public ReconciliationFileProcessingResult(String sourceName, String reconBatchNo,
                                              ReconciliationFileOutcome outcome,
                                              String errorCode) {
        this.sourceName = sourceName;
        this.reconBatchNo = reconBatchNo;
        this.outcome = outcome;
        this.errorCode = errorCode;
    }

    public String sourceName() { return sourceName; }
    public String reconBatchNo() { return reconBatchNo; }
    public ReconciliationFileOutcome outcome() { return outcome; }
    public String errorCode() { return errorCode; }
}
