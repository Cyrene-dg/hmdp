package com.qinghe.marketing.reconciliation;

import com.qinghe.marketing.shared.clock.BusinessClock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReconciliationCompletionTransactionService {
    private final ReconciliationBatchRepository repository;
    private final BusinessClock clock;

    public ReconciliationCompletionTransactionService(ReconciliationBatchRepository repository,
                                                      BusinessClock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional(rollbackFor = Exception.class)
    public ReconciliationBatch complete(long batchId) {
        ReconciliationBatch batch = repository.findByIdForUpdate(batchId)
                .orElseThrow(() -> new IllegalStateException(
                        "reconciliation batch does not exist"));
        if (batch.status() != ReconciliationBatchStatus.IMPORTING) return batch;
        ReconciliationImportSummary summary = repository.summarizeImport(batchId);
        if (summary.completedChunks() != summary.totalChunks()
                || summary.importedRows() != batch.totalRows()) {
            throw new IllegalStateException("reconciliation import still has incomplete chunks");
        }
        ReconciliationBatchStatus target = summary.errorRows() == 0
                ? ReconciliationBatchStatus.MATCHING
                : ReconciliationBatchStatus.PARTIAL_FAILED;
        repository.completeImport(batchId, target, summary, batch.version(), clock.dateTime());
        return repository.findByIdForUpdate(batchId)
                .orElseThrow(() -> new IllegalStateException(
                        "completed reconciliation batch cannot be reloaded"));
    }
}
