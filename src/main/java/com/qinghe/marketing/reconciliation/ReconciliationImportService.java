package com.qinghe.marketing.reconciliation;

import com.qinghe.marketing.shared.error.QingheBusinessException;
import com.qinghe.marketing.shared.error.QingheErrorCode;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ReconciliationImportService {
    private final ReconciliationFileParser parser;
    private final ReconciliationRegistrationTransactionService registrations;
    private final ReconciliationChunkTransactionService chunks;
    private final ReconciliationChunkFailureTransactionService failures;
    private final ReconciliationCompletionTransactionService completions;
    private final ReconciliationBatchRepository repository;

    public ReconciliationImportService(ReconciliationFileParser parser,
                                       ReconciliationRegistrationTransactionService registrations,
                                       ReconciliationChunkTransactionService chunks,
                                       ReconciliationChunkFailureTransactionService failures,
                                       ReconciliationCompletionTransactionService completions,
                                       ReconciliationBatchRepository repository) {
        this.parser = parser;
        this.registrations = registrations;
        this.chunks = chunks;
        this.failures = failures;
        this.completions = completions;
        this.repository = repository;
    }

    public ReconciliationImportResult importFile(byte[] manifestBytes, String actualFileName,
                                                 byte[] csvBytes, int chunkSize) {
        if (chunkSize <= 0 || chunkSize > 10000) {
            throw new QingheBusinessException(QingheErrorCode.INVALID_ARGUMENT,
                    "reconciliation chunk size must be between 1 and 10000");
        }
        ParsedReconciliationFile parsed = parser.parse(manifestBytes, actualFileName, csvBytes);
        ReconciliationRegistrationResult registration = registrations.register(
                parsed.manifest(), chunkSize);
        if (registration.outcome() == ReconciliationRegistrationOutcome.CONFLICT) {
            throw new QingheBusinessException(QingheErrorCode.RECON_BATCH_CONFLICT,
                    "provider batch number was reused with different content",
                    registration.batch().reconBatchNo());
        }
        if (!registration.shouldImport()) {
            return result(registration.batch(), true);
        }

        Map<Integer, ReconciliationCsvRow> rows = indexRows(parsed.rows());
        Map<Integer, ReconciliationRowIssue> issues = indexIssues(parsed.issues());
        for (ReconciliationImportChunk chunk : repository.findIncompleteChunks(
                registration.batch().id())) {
            List<ReconciliationCsvRow> chunkRows = new ArrayList<ReconciliationCsvRow>();
            List<ReconciliationRowIssue> chunkIssues = new ArrayList<ReconciliationRowIssue>();
            for (int line = chunk.firstLineNo(); line <= chunk.lastLineNo(); line++) {
                ReconciliationCsvRow row = rows.get(line);
                ReconciliationRowIssue issue = issues.get(line);
                if ((row == null) == (issue == null)) {
                    throw new IllegalStateException(
                            "each reconciliation input line must be valid or audited invalid");
                }
                if (row != null) chunkRows.add(row); else chunkIssues.add(issue);
            }
            try {
                chunks.importChunk(chunk, chunkRows, chunkIssues);
            } catch (RuntimeException failure) {
                failures.record(chunk.batchId(), chunk.chunkNo(), "CHUNK_IMPORT_FAILED");
                throw failure;
            }
        }
        return result(completions.complete(registration.batch().id()), false);
    }

    private static Map<Integer, ReconciliationCsvRow> indexRows(
            List<ReconciliationCsvRow> rows) {
        Map<Integer, ReconciliationCsvRow> indexed = new HashMap<Integer, ReconciliationCsvRow>();
        for (ReconciliationCsvRow row : rows) {
            if (indexed.put(row.lineNo(), row) != null) {
                throw new IllegalStateException("duplicate reconciliation row line number");
            }
        }
        return indexed;
    }

    private static Map<Integer, ReconciliationRowIssue> indexIssues(
            List<ReconciliationRowIssue> issues) {
        Map<Integer, ReconciliationRowIssue> indexed =
                new HashMap<Integer, ReconciliationRowIssue>();
        for (ReconciliationRowIssue issue : issues) {
            if (indexed.put(issue.lineNo(), issue) != null) {
                throw new IllegalStateException("duplicate reconciliation issue line number");
            }
        }
        return indexed;
    }

    private static ReconciliationImportResult result(ReconciliationBatch batch,
                                                     boolean replayed) {
        return new ReconciliationImportResult(batch.reconBatchNo(), batch.status(),
                batch.totalRows(), batch.successRows(), batch.errorRows(), replayed);
    }
}
