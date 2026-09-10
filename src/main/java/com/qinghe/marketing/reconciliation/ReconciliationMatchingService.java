package com.qinghe.marketing.reconciliation;

import com.qinghe.marketing.shared.error.QingheBusinessException;
import com.qinghe.marketing.shared.error.QingheErrorCode;
import org.springframework.stereotype.Service;

@Service
public class ReconciliationMatchingService {
    private final ReconciliationMatchingTransactionService transactions;
    private final ReconciliationMatchingCompletionService completion;

    public ReconciliationMatchingService(ReconciliationMatchingTransactionService transactions,
                                         ReconciliationMatchingCompletionService completion) {
        this.transactions = transactions;
        this.completion = completion;
    }

    public ReconciliationMatchSummary match(long batchId, int chunkSize) {
        if (chunkSize <= 0 || chunkSize > 1000) {
            throw new QingheBusinessException(QingheErrorCode.INVALID_ARGUMENT,
                    "reconciliation match chunk size must be between 1 and 1000");
        }
        while (transactions.matchNext(batchId, chunkSize) > 0) {
            // Each call commits one bounded chunk; UNMATCHED rows are the resume cursor.
        }
        return completion.complete(batchId);
    }
}
