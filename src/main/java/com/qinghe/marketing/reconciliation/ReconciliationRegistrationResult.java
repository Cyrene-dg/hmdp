package com.qinghe.marketing.reconciliation;

public final class ReconciliationRegistrationResult {
    private final ReconciliationRegistrationOutcome outcome;
    private final ReconciliationBatch batch;

    public ReconciliationRegistrationResult(ReconciliationRegistrationOutcome outcome,
                                            ReconciliationBatch batch) {
        this.outcome = outcome;
        this.batch = batch;
    }

    public ReconciliationRegistrationOutcome outcome() { return outcome; }
    public ReconciliationBatch batch() { return batch; }
    public boolean shouldImport() {
        return outcome == ReconciliationRegistrationOutcome.ACCEPTED
                || outcome == ReconciliationRegistrationOutcome.RESUMED;
    }
}
