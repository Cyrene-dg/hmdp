package com.qinghe.marketing.reconciliation;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ReconciliationMatchingRepository {
    Optional<ReconciliationBatch> findBatchForUpdate(long batchId);
    List<ReconciliationRecord> findUnmatchedForUpdate(long batchId, int limit);
    Optional<PlatformOperationFact> findPlatformFact(ReconciliationRecord record);
    SettlementPreparationOutcome prepareSettlementDetail(long batchId,
                                                          PlatformOperationFact fact,
                                                          LocalDateTime now);
    void markCandidateDifference(Long candidateId, LocalDateTime now);
    void markRecord(long recordId, ReconciliationMatchStatus status, String reason,
                    PlatformOperationFact fact, boolean settlementEligible, LocalDateTime now);
    void insertDifference(long batchId, Long recordId, Long redemptionId, Long reversalId,
                          ReconciliationMatchStatus type, String businessKey, String detail,
                          LocalDateTime now);
    void insertPlatformOnlyDifferences(long batchId, LocalDate businessDate, LocalDateTime now);
    ReconciliationMatchSummary summarize(long batchId);
    void completeBatch(long batchId, ReconciliationMatchSummary summary,
                       long expectedVersion, LocalDateTime now);
}
