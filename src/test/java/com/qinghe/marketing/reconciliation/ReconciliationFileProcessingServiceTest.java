package com.qinghe.marketing.reconciliation;

import com.qinghe.marketing.shared.error.QingheBusinessException;
import com.qinghe.marketing.shared.error.QingheErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReconciliationFileProcessingServiceTest {
    @TempDir
    Path temporary;

    @Test
    void shouldLeaveMissingPairAndProcessCompletedPairIntoReadOnlyArchive() throws Exception {
        LocalReconciliationFileGateway files = new LocalReconciliationFileGateway(
                temporary.toString());
        ReconciliationImportService imports = mock(ReconciliationImportService.class);
        ReconciliationMatchingService matching = mock(ReconciliationMatchingService.class);
        ReconciliationBatchRepository batches = mock(ReconciliationBatchRepository.class);
        ReconciliationFileProcessingService service = new ReconciliationFileProcessingService(
                files, imports, matching, batches, 2, 3);

        Path inbound = temporary.resolve("inbound");
        Files.createDirectories(inbound);
        Files.write(inbound.resolve("POS_20260908_MISSING.manifest.json"),
                "{}".getBytes(StandardCharsets.UTF_8));
        pair(inbound, "POS_20260908_POSB20260908088", "manifest", "csv");
        when(imports.importFile(any(byte[].class), anyString(), any(byte[].class), anyInt()))
                .thenReturn(new ReconciliationImportResult("REC202609090088",
                        ReconciliationBatchStatus.MATCHING, 1, 1, 0, false));
        ReconciliationBatch batch = new ReconciliationBatch(88, "REC202609090088", "MOCK_POS",
                "POSB20260908088", LocalDate.of(2026, 9, 8), repeat('a', 64),
                ReconciliationBatchStatus.MATCHING, 1, 1, 1, 0, 2);
        when(batches.findByReconBatchNo("REC202609090088")).thenReturn(Optional.of(batch));
        when(matching.match(88, 3)).thenReturn(new ReconciliationMatchSummary(0, 1, 0, 0, 1));

        List<ReconciliationFileProcessingResult> results = service.processAvailable();

        assertEquals(2, results.size());
        assertEquals(ReconciliationFileOutcome.MISSING, results.get(0).outcome());
        assertEquals(ReconciliationFileOutcome.COMPLETED, results.get(1).outcome());
        assertTrue(Files.exists(inbound.resolve("POS_20260908_MISSING.manifest.json")));
        assertFalse(Files.exists(inbound.resolve("POS_20260908_POSB20260908088.csv")));
        assertEquals(2, regularFileCount(temporary.resolve("archive/success")));
        verify(matching).match(88, 3);
    }

    @Test
    void shouldIsolateConflictAndAllowRecoverableFailureToRetry() throws Exception {
        LocalReconciliationFileGateway files = new LocalReconciliationFileGateway(
                temporary.toString());
        ReconciliationImportService imports = mock(ReconciliationImportService.class);
        ReconciliationMatchingService matching = mock(ReconciliationMatchingService.class);
        ReconciliationBatchRepository batches = mock(ReconciliationBatchRepository.class);
        ReconciliationFileProcessingService service = new ReconciliationFileProcessingService(
                files, imports, matching, batches, 2, 3);
        Path inbound = temporary.resolve("inbound");
        Files.createDirectories(inbound);

        pair(inbound, "POS_20260908_POSB20260908089", "manifest", "csv");
        when(imports.importFile(any(byte[].class), anyString(), any(byte[].class), anyInt()))
                .thenThrow(new QingheBusinessException(QingheErrorCode.RECON_BATCH_CONFLICT,
                        "conflict"));
        assertEquals(ReconciliationFileOutcome.CONFLICT,
                service.processAvailable().get(0).outcome());
        assertEquals(2, regularFileCount(temporary.resolve("archive/conflict")));

        pair(inbound, "POS_20260908_POSB20260908090", "manifest", "csv");
        when(imports.importFile(any(byte[].class), anyString(), any(byte[].class), anyInt()))
                .thenThrow(new IllegalStateException("temporary database failure"));
        assertEquals(ReconciliationFileOutcome.FAILED,
                service.processAvailable().get(0).outcome());
        assertEquals(2, regularFileCount(temporary.resolve("archive/error")));

        when(imports.importFile(any(byte[].class), anyString(), any(byte[].class), anyInt()))
                .thenReturn(new ReconciliationImportResult("REC202609090090",
                        ReconciliationBatchStatus.COMPLETED, 1, 1, 0, true));
        assertEquals(ReconciliationFileOutcome.DUPLICATE,
                service.retry("POS_20260908_POSB20260908090.csv").outcome());
        assertEquals(0, regularFileCount(temporary.resolve("archive/error")));
        assertEquals(2, regularFileCount(temporary.resolve("archive/success")));
    }

    private static void pair(Path inbound, String stem, String manifest, String csv)
            throws Exception {
        Files.write(inbound.resolve(stem + ".manifest.json"),
                manifest.getBytes(StandardCharsets.UTF_8));
        Files.write(inbound.resolve(stem + ".csv"), csv.getBytes(StandardCharsets.UTF_8));
    }

    private static long regularFileCount(Path directory) throws Exception {
        if (!Files.isDirectory(directory)) return 0;
        try (java.util.stream.Stream<Path> paths = Files.walk(directory)) {
            return paths.filter(Files::isRegularFile).count();
        }
    }

    private static String repeat(char value, int count) {
        StringBuilder result = new StringBuilder(count);
        for (int index = 0; index < count; index++) result.append(value);
        return result.toString();
    }
}
