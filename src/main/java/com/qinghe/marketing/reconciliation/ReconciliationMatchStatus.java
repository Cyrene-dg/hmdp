package com.qinghe.marketing.reconciliation;

public enum ReconciliationMatchStatus {
    UNMATCHED,
    MATCHED,
    MATCHED_REVERSAL,
    DIFFERENCE_PLATFORM_ONLY,
    DIFFERENCE_POS_ONLY,
    DIFFERENCE_STATUS,
    DIFFERENCE_DATA
}
