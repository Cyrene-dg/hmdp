package com.qinghe.marketing.reconciliation;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ReconciliationAdminRepository {
    List<ReconciliationBatchView> list(LocalDate businessDate,
                                       ReconciliationBatchStatus status, int offset, int limit);
    long count(LocalDate businessDate, ReconciliationBatchStatus status);
    Optional<ReconciliationBatchView> findDetail(String reconBatchNo);
}
