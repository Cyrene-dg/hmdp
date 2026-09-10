package com.qinghe.marketing.reconciliation;

import com.qinghe.marketing.shared.error.QingheBusinessException;
import com.qinghe.marketing.shared.error.QingheErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Service
public class ReconciliationFileProcessingService {
    private final LocalReconciliationFileGateway files;
    private final ReconciliationImportService imports;
    private final ReconciliationMatchingService matching;
    private final ReconciliationBatchRepository batches;
    private final ReconciliationMissingFileService missingFiles;
    private final ReconciliationExecutionMetrics metrics;
    private final int importChunkSize;
    private final int matchChunkSize;

    public ReconciliationFileProcessingService(LocalReconciliationFileGateway files,
            ReconciliationImportService imports, ReconciliationMatchingService matching,
            ReconciliationBatchRepository batches,ReconciliationMissingFileService missingFiles,
            ReconciliationExecutionMetrics metrics,
            @Value("${qinghe.reconciliation.import-chunk-size:500}") int importChunkSize,
            @Value("${qinghe.reconciliation.match-chunk-size:200}") int matchChunkSize) {
        this.files = files;
        this.imports = imports;
        this.matching = matching;
        this.batches = batches; this.missingFiles=missingFiles; this.metrics=metrics;
        this.importChunkSize = importChunkSize;
        this.matchChunkSize = matchChunkSize;
    }

    public List<ReconciliationFileProcessingResult> processAvailable() {
        if (!files.isConfigured()) return new ArrayList<ReconciliationFileProcessingResult>();
        try {
            List<ReconciliationFileProcessingResult> results =
                    new ArrayList<ReconciliationFileProcessingResult>();
            for (Path manifest : files.discoverManifests()) {
                Path csv = files.pairedCsv(manifest);
                if (!Files.isRegularFile(csv)) {
                    ReconciliationFileOutcome outcome=missingFiles.observeMissingCsv(
                            files.read(manifest));
                    ReconciliationFileProcessingResult missing = new ReconciliationFileProcessingResult(
                            manifest.getFileName().toString(), null,
                            outcome,outcome==ReconciliationFileOutcome.MISSING
                                    ? "CSV_PAIR_MISSING" : null);
                    metrics.record(outcome, 0);
                    results.add(missing);
                    continue;
                }
                results.add(process(files.stage(manifest)));
            }
            return results;
        } catch (IOException failure) {
            throw new IllegalStateException("reconciliation directory scan failed", failure);
        }
    }

    public ReconciliationFileProcessingResult retry(String fileName) {
        try {
            ReconciliationStagedFile failed = files.findFailed(fileName);
            if (failed == null) {
                throw new QingheBusinessException(QingheErrorCode.RESOURCE_NOT_FOUND,
                        "recoverable reconciliation source file does not exist");
            }
            return process(failed);
        } catch (IOException failure) {
            throw new IllegalStateException("reconciliation retry source cannot be read", failure);
        }
    }

    private ReconciliationFileProcessingResult process(ReconciliationStagedFile staged) {
        long started = System.nanoTime();
        ReconciliationFileProcessingResult result = doProcess(staged);
        metrics.record(result.outcome(), System.nanoTime() - started);
        return result;
    }

    private ReconciliationFileProcessingResult doProcess(ReconciliationStagedFile staged) {
        String reconBatchNo = null;
        try {
            ReconciliationImportResult imported = imports.importFile(files.read(staged.manifest()),
                    staged.csv().getFileName().toString(), files.read(staged.csv()), importChunkSize);
            reconBatchNo = imported.reconBatchNo();
            if (imported.status() == ReconciliationBatchStatus.PARTIAL_FAILED) {
                files.archive(staged, "error");
                return result(staged, reconBatchNo, ReconciliationFileOutcome.PARTIAL_FAILED,
                        "ROW_FORMAT_INVALID");
            }
            if (imported.status() == ReconciliationBatchStatus.MATCHING) {
                ReconciliationBatch batch = batches.findByReconBatchNo(reconBatchNo)
                        .orElseThrow(() -> new IllegalStateException(
                                "registered reconciliation batch cannot be found"));
                matching.match(batch.id(), matchChunkSize);
            }
            files.archive(staged, "success");
            return result(staged, reconBatchNo, imported.replayed()
                    ? ReconciliationFileOutcome.DUPLICATE
                    : ReconciliationFileOutcome.COMPLETED, null);
        } catch (QingheBusinessException failure) {
            archive(staged, failure.errorCode() == QingheErrorCode.RECON_BATCH_CONFLICT
                    ? "conflict" : "error");
            return result(staged, reconBatchNo,
                    failure.errorCode() == QingheErrorCode.RECON_BATCH_CONFLICT
                            ? ReconciliationFileOutcome.CONFLICT
                            : ReconciliationFileOutcome.FAILED,
                    failure.errorCode().name());
        } catch (IOException failure) {
            throw new IllegalStateException("reconciliation source cannot be archived", failure);
        } catch (RuntimeException failure) {
            archive(staged, "error");
            return result(staged, reconBatchNo, ReconciliationFileOutcome.FAILED,
                    "PROCESSING_FAILED");
        }
    }

    private void archive(ReconciliationStagedFile staged, String bucket) {
        try {
            files.archive(staged, bucket);
        } catch (IOException archiveFailure) {
            throw new IllegalStateException("reconciliation failure source cannot be archived",
                    archiveFailure);
        }
    }

    private static ReconciliationFileProcessingResult result(ReconciliationStagedFile staged,
            String reconBatchNo, ReconciliationFileOutcome outcome, String errorCode) {
        return new ReconciliationFileProcessingResult(staged.csv().getFileName().toString(),
                reconBatchNo, outcome, errorCode);
    }
}
