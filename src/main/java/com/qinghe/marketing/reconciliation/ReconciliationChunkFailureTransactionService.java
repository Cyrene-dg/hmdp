package com.qinghe.marketing.reconciliation;

import com.qinghe.marketing.shared.clock.BusinessClock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReconciliationChunkFailureTransactionService {
    private final ReconciliationBatchRepository repository;
    private final BusinessClock clock;

    public ReconciliationChunkFailureTransactionService(ReconciliationBatchRepository repository,
                                                        BusinessClock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional(rollbackFor = Exception.class)
    public void record(long batchId, int chunkNo, String errorCode) {
        repository.recordChunkFailure(batchId, chunkNo, errorCode, clock.dateTime());
    }
}
