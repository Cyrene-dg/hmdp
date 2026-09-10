package com.qinghe.marketing.reconciliation;

public enum ReconciliationBatchStatus {
    RECEIVED,
    IMPORTING,
    MATCHING,
    COMPLETED,
    PARTIAL_FAILED,
    CONFLICT,
    MISSING
}
