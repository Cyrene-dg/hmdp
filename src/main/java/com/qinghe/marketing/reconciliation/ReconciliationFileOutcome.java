package com.qinghe.marketing.reconciliation;

public enum ReconciliationFileOutcome {
    COMPLETED,
    DUPLICATE,
    PARTIAL_FAILED,
    CONFLICT,
    WAITING_FOR_PAIR,
    MISSING,
    FAILED
}
