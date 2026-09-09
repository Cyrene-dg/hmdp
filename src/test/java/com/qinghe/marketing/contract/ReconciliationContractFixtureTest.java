package com.qinghe.marketing.contract;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReconciliationContractFixtureTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Path FIXTURE_ROOT = Paths.get(
            "src", "test", "resources", "contracts", "reconciliation");
    private static final List<String> EXPECTED_COLUMNS = Arrays.asList(
            "batch_no", "business_date", "store_code", "terminal_no", "pos_order_no",
            "pos_request_no", "platform_redemption_no", "right_code", "operation_type",
            "operation_status", "occurred_at"
    );

    @Test
    void manifestJsonSchemaIsParseableAndRequiresSafetyFields() throws IOException {
        Path schemaPath = Paths.get("docs", "contracts", "reconciliation-manifest-v1.0.schema.json");
        Map<String, Object> schema = readJson(schemaPath);
        assertEquals("object", schema.get("type"));
        List<?> required = (List<?>) schema.get("required");
        assertTrue(required.contains("batchNo"));
        assertTrue(required.contains("rowCount"));
        assertTrue(required.contains("checksum"));
        assertTrue(required.contains("correctionOfBatchNo"));
    }

    @Test
    void normalFixtureMatchesManifestAndCsvContract() throws Exception {
        Fixture fixture = loadFixture("valid", "POS_20260908_POSB20260908001");
        assertEquals(2, fixture.dataRows.size());
        assertEquals("POSB20260908001", fixture.manifest.get("batchNo"));
    }

    @Test
    void headerOnlyFileIsAValidZeroTransactionDay() throws Exception {
        Fixture fixture = loadFixture("empty", "POS_20260907_POSB20260907001");
        assertEquals(0, fixture.dataRows.size());
        assertEquals(0, ((Number) fixture.manifest.get("rowCount")).intValue());
    }

    @Test
    void sameProviderAndBatchWithDifferentChecksumIsDetectableAsConflict() throws Exception {
        Fixture original = loadFixture("valid", "POS_20260908_POSB20260908001");
        Fixture changed = loadFixture("conflict", "POS_20260908_POSB20260908001");

        assertEquals(original.manifest.get("provider"), changed.manifest.get("provider"));
        assertEquals(original.manifest.get("batchNo"), changed.manifest.get("batchNo"));
        assertNotEquals(original.manifest.get("checksum"), changed.manifest.get("checksum"));
    }

    @Test
    void malformedBusinessRowIsRejectedWithoutIgnoringIt() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> loadFixture("invalid-row", "POS_20260906_POSB20260906001"));
        assertTrue(error.getMessage().contains("business_date")
                || error.getMessage().contains("operation_type")
                || error.getMessage().contains("occurred_at"));
    }

    private Fixture loadFixture(String directory, String baseName) throws Exception {
        Path folder = FIXTURE_ROOT.resolve(directory);
        Path csv = folder.resolve(baseName + ".csv");
        Path manifestPath = folder.resolve(baseName + ".manifest.json");
        Map<String, Object> manifest = readJson(manifestPath);

        require(manifest, "provider");
        require(manifest, "batchNo");
        require(manifest, "businessDate");
        require(manifest, "schemaVersion");
        require(manifest, "fileName");
        require(manifest, "rowCount");
        require(manifest, "checksumAlgorithm");
        require(manifest, "checksum");
        require(manifest, "generatedAt");
        assertEquals("1.0", manifest.get("schemaVersion"));
        assertEquals("SHA-256", manifest.get("checksumAlgorithm"));
        assertEquals(csv.getFileName().toString(), manifest.get("fileName"));
        LocalDate.parse(String.valueOf(manifest.get("businessDate")));
        OffsetDateTime.parse(String.valueOf(manifest.get("generatedAt")));

        byte[] rawCsv = Files.readAllBytes(csv);
        assertEquals(manifest.get("checksum"), sha256(rawCsv));
        List<String> lines = Files.readAllLines(csv, StandardCharsets.UTF_8);
        assertTrue(!lines.isEmpty(), "CSV must include a header");
        assertEquals(EXPECTED_COLUMNS, Arrays.asList(lines.get(0).split(",", -1)));
        List<String> dataRows = lines.subList(1, lines.size());
        assertEquals(((Number) manifest.get("rowCount")).intValue(), dataRows.size());

        for (int index = 0; index < dataRows.size(); index++) {
            validateRow(dataRows.get(index), manifest, index + 2);
        }
        return new Fixture(manifest, dataRows);
    }

    private void validateRow(String line, Map<String, Object> manifest, int lineNo) {
        String[] values = line.split(",", -1);
        if (values.length != EXPECTED_COLUMNS.size()) {
            throw new IllegalArgumentException("line " + lineNo + " column count");
        }
        assertRequired(values, lineNo);
        if (!String.valueOf(manifest.get("batchNo")).equals(values[0])) {
            throw new IllegalArgumentException("line " + lineNo + " batch_no");
        }
        try {
            LocalDate rowDate = LocalDate.parse(values[1]);
            if (!String.valueOf(manifest.get("businessDate")).equals(rowDate.toString())) {
                throw new IllegalArgumentException("line " + lineNo + " business_date mismatch");
            }
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("line " + lineNo + " business_date", exception);
        }
        if (!"REDEEM".equals(values[8]) && !"REVERSE".equals(values[8])) {
            throw new IllegalArgumentException("line " + lineNo + " operation_type");
        }
        if (!"SUCCESS".equals(values[9]) && !"FAILED".equals(values[9])) {
            throw new IllegalArgumentException("line " + lineNo + " operation_status");
        }
        try {
            OffsetDateTime.parse(values[10]);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("line " + lineNo + " occurred_at", exception);
        }
    }

    private static void assertRequired(String[] values, int lineNo) {
        for (int i = 0; i < values.length; i++) {
            if (i == 6) {
                continue;
            }
            if (values[i] == null || values[i].trim().isEmpty()) {
                throw new IllegalArgumentException("line " + lineNo + " missing " + EXPECTED_COLUMNS.get(i));
            }
        }
    }

    private static void require(Map<String, Object> manifest, String field) {
        if (!manifest.containsKey(field) || manifest.get(field) == null) {
            throw new IllegalArgumentException("manifest missing " + field);
        }
    }

    private static Map<String, Object> readJson(Path path) throws IOException {
        try (java.io.InputStream input = Files.newInputStream(path)) {
            return JSON.readValue(input, new TypeReference<Map<String, Object>>() { });
        }
    }

    private static String sha256(byte[] bytes) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
        StringBuilder builder = new StringBuilder(digest.length * 2);
        for (byte value : digest) {
            builder.append(String.format("%02x", value & 0xff));
        }
        return builder.toString();
    }

    private static final class Fixture {
        private final Map<String, Object> manifest;
        private final List<String> dataRows;

        private Fixture(Map<String, Object> manifest, List<String> dataRows) {
            this.manifest = manifest;
            this.dataRows = dataRows;
        }
    }
}
