package com.qinghe.marketing.reconciliation;

import com.qinghe.marketing.shared.clock.BusinessClock;
import com.qinghe.marketing.shared.error.QingheBusinessException;
import com.qinghe.marketing.shared.error.QingheErrorCode;
import com.qinghe.marketing.shared.id.BusinessIdGenerator;
import com.qinghe.marketing.shared.id.BusinessIdType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class ReconciliationRegistrationTransactionService {
    private final ReconciliationBatchRepository repository;
    private final BusinessIdGenerator ids;
    private final BusinessClock clock;

    public ReconciliationRegistrationTransactionService(ReconciliationBatchRepository repository,
                                                        BusinessIdGenerator ids,
                                                        BusinessClock clock) {
        this.repository = repository;
        this.ids = ids;
        this.clock = clock;
    }

    @Transactional(rollbackFor = Exception.class)
    public ReconciliationRegistrationResult register(ReconciliationManifest manifest,
                                                     int chunkSize) {
        validateCorrection(manifest);
        LocalDateTime now = clock.dateTime();
        boolean inserted = repository.insertBatchIfAbsent(
                ids.next(BusinessIdType.RECONCILIATION_BATCH), manifest, now);
        ReconciliationBatch batch = repository.findByProviderBatchForUpdate(
                        manifest.provider(), manifest.batchNo())
                .orElseThrow(() -> new IllegalStateException(
                        "reconciliation batch cannot be reloaded"));
        if (!batch.checksum().equals(manifest.checksum())) {
            repository.recordAttempt(manifest.provider(), manifest.batchNo(), manifest.checksum(),
                    ReconciliationRegistrationOutcome.CONFLICT.name(), batch.id(), now);
            return new ReconciliationRegistrationResult(
                    ReconciliationRegistrationOutcome.CONFLICT, batch);
        }

        if (inserted || batch.status() == ReconciliationBatchStatus.RECEIVED
                || batch.status() == ReconciliationBatchStatus.IMPORTING) {
            repository.createChunksIfAbsent(batch.id(), batch.totalRows(), chunkSize, now);
            repository.markImporting(batch.id(), batch.version(), now);
            ReconciliationBatch importing = repository.findByIdForUpdate(batch.id())
                    .orElseThrow(() -> new IllegalStateException(
                            "reconciliation importing batch cannot be reloaded"));
            ReconciliationRegistrationOutcome outcome = inserted
                    ? ReconciliationRegistrationOutcome.ACCEPTED
                    : ReconciliationRegistrationOutcome.RESUMED;
            repository.recordAttempt(manifest.provider(), manifest.batchNo(), manifest.checksum(),
                    outcome.name(), batch.id(), now);
            return new ReconciliationRegistrationResult(outcome, importing);
        }

        repository.recordAttempt(manifest.provider(), manifest.batchNo(), manifest.checksum(),
                ReconciliationRegistrationOutcome.DUPLICATE.name(), batch.id(), now);
        return new ReconciliationRegistrationResult(
                ReconciliationRegistrationOutcome.DUPLICATE, batch);
    }

    private void validateCorrection(ReconciliationManifest manifest) {
        String source = manifest.correctionOfBatchNo();
        if (source == null) return;
        if (source.equals(manifest.batchNo())
                || !repository.correctionSourceExists(manifest.provider(), source)) {
            throw new QingheBusinessException(QingheErrorCode.RECON_FILE_INVALID,
                    "correction batch must reference an existing earlier provider batch");
        }
    }
}
