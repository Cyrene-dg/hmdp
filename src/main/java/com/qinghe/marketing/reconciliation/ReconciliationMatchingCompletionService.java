package com.qinghe.marketing.reconciliation;

import com.qinghe.marketing.shared.clock.BusinessClock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReconciliationMatchingCompletionService {
    private final ReconciliationMatchingRepository repository;
    private final BusinessClock clock;

    public ReconciliationMatchingCompletionService(ReconciliationMatchingRepository repository,
                                                   BusinessClock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional(rollbackFor = Exception.class)
    public ReconciliationMatchSummary complete(long batchId) {
        ReconciliationBatch batch = repository.findBatchForUpdate(batchId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "reconciliation batch does not exist"));
        if (batch.status() == ReconciliationBatchStatus.COMPLETED) {
            return repository.summarize(batchId);
        }
        if (batch.status() != ReconciliationBatchStatus.MATCHING) {
            throw new IllegalStateException("only a fully imported batch can be matched");
        }
        ReconciliationMatchSummary beforePlatformOnly = repository.summarize(batchId);
        if (beforePlatformOnly.unmatchedRows() != 0) {
            throw new IllegalStateException("reconciliation records are still unmatched");
        }
        repository.insertPlatformOnlyDifferences(batchId, batch.businessDate(), clock.dateTime());
        ReconciliationMatchSummary summary = repository.summarize(batchId);
        repository.completeBatch(batchId, summary, batch.version(), clock.dateTime());
        return summary;
    }
}
