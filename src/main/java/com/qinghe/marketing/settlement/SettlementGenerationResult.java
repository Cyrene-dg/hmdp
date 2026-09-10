package com.qinghe.marketing.settlement;

public final class SettlementGenerationResult {
    private final SettlementGenerationOutcome outcome;
    private final String reconBatchNo;
    private final SettlementBatch batch;

    public SettlementGenerationResult(SettlementGenerationOutcome outcome, String reconBatchNo,
                                      SettlementBatch batch) {
        this.outcome = outcome; this.reconBatchNo = reconBatchNo; this.batch = batch;
    }

    public SettlementGenerationOutcome outcome() { return outcome; }
    public String reconBatchNo() { return reconBatchNo; }
    public SettlementBatch batch() { return batch; }
}
