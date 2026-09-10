package com.qinghe.marketing.reconciliation;

import com.qinghe.marketing.entitlement.RightCodeProtector;
import com.qinghe.marketing.shared.clock.BusinessClock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ReconciliationChunkTransactionService {
    private final ReconciliationBatchRepository repository;
    private final RightCodeProtector rightCodeProtector;
    private final BusinessClock clock;

    public ReconciliationChunkTransactionService(ReconciliationBatchRepository repository,
                                                 RightCodeProtector rightCodeProtector,
                                                 BusinessClock clock) {
        this.repository = repository;
        this.rightCodeProtector = rightCodeProtector;
        this.clock = clock;
    }

    @Transactional(rollbackFor = Exception.class)
    public void importChunk(ReconciliationImportChunk selected,
                            List<ReconciliationCsvRow> rows,
                            List<ReconciliationRowIssue> issues) {
        ReconciliationImportChunk chunk = repository.findChunkForUpdate(
                        selected.batchId(), selected.chunkNo())
                .orElseThrow(() -> new IllegalStateException(
                        "reconciliation import chunk does not exist"));
        if (chunk.status() == ReconciliationChunkStatus.COMPLETED) return;
        if (rows.size() + issues.size() != chunk.rowCount()) {
            throw new IllegalStateException("reconciliation chunk input is incomplete");
        }
        LocalDateTime now = clock.dateTime();
        for (ReconciliationCsvRow row : rows) {
            repository.insertRecord(chunk.batchId(), chunk.chunkNo(), row,
                    rightCodeProtector.hash(row.rightCode()),
                    row.occurredAt().atZoneSameInstant(BusinessClock.BUSINESS_ZONE)
                            .toLocalDateTime(), now);
        }
        for (ReconciliationRowIssue issue : issues) {
            repository.insertIssue(chunk.batchId(), chunk.chunkNo(), issue, now);
        }
        repository.completeChunk(chunk.id(), rows.size(), issues.size(), now);
    }
}
