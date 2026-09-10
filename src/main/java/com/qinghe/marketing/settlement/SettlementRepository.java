package com.qinghe.marketing.settlement;

import com.qinghe.marketing.reconciliation.ReconciliationBatch;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface SettlementRepository {
    Optional<ReconciliationBatch> findReconBatchForUpdate(String reconBatchNo);
    Optional<SettlementBatch> findByReconBatchIdForUpdate(long reconBatchId);
    Optional<SettlementBatch> findByBatchNoForUpdate(String settlementBatchNo);
    Optional<SettlementBatch> findByBatchNo(String settlementBatchNo);
    SettlementTotals lockEligibleDetails(long reconBatchId, Long existingSettlementBatchId);
    SettlementBatch insertBatch(String batchNo, ReconciliationBatch recon,
                                SettlementTotals totals, LocalDateTime now);
    void attachDetails(long settlementBatchId, List<Long> detailIds, LocalDateTime now);
    SettlementBatch rebuildBatch(SettlementBatch batch, SettlementTotals totals,
                                 LocalDateTime now);
    SettlementTotals lockAttachedEligibleDetails(long settlementBatchId);
    void confirmDetails(long settlementBatchId, int expectedCount, LocalDateTime now);
    void confirmBatch(long settlementBatchId, long expectedVersion, String operatorId,
                      LocalDateTime now);
    List<SettlementExportRow> exportRows(long settlementBatchId);
}
