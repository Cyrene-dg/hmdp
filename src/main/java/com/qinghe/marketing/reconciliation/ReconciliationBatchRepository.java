package com.qinghe.marketing.reconciliation;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ReconciliationBatchRepository {
    boolean insertBatchIfAbsent(String reconBatchNo, ReconciliationManifest manifest,
                                LocalDateTime now);
    Optional<ReconciliationBatch> findByProviderBatch(String provider, String providerBatchNo);
    Optional<ReconciliationBatch> findByReconBatchNo(String reconBatchNo);
    Optional<ReconciliationBatch> findByProviderBatchForUpdate(String provider,
                                                               String providerBatchNo);
    Optional<ReconciliationBatch> findByIdForUpdate(long batchId);
    void recordAttempt(String provider, String providerBatchNo, String checksum, String result,
                       Long batchId, LocalDateTime now);
    boolean correctionSourceExists(String provider, String providerBatchNo);
    void createChunksIfAbsent(long batchId, int totalRows, int chunkSize, LocalDateTime now);
    List<ReconciliationImportChunk> findIncompleteChunks(long batchId);
    Optional<ReconciliationImportChunk> findChunkForUpdate(long batchId, int chunkNo);
    void markImporting(long batchId, long expectedVersion, LocalDateTime now);
    void insertRecord(long batchId, int chunkNo, ReconciliationCsvRow row,
                      String rightCodeHash, LocalDateTime occurredAt, LocalDateTime now);
    void insertIssue(long batchId, int chunkNo, ReconciliationRowIssue issue,
                     LocalDateTime now);
    void completeChunk(long chunkId, int successRows, int errorRows, LocalDateTime now);
    void recordChunkFailure(long batchId, int chunkNo, String errorCode, LocalDateTime now);
    ReconciliationImportSummary summarizeImport(long batchId);
    void completeImport(long batchId, ReconciliationBatchStatus status,
                        ReconciliationImportSummary summary, long expectedVersion,
                        LocalDateTime now);
}
