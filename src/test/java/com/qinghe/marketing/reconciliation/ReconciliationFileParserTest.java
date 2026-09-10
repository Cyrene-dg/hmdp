package com.qinghe.marketing.reconciliation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qinghe.marketing.shared.error.QingheBusinessException;
import com.qinghe.marketing.shared.error.QingheErrorCode;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReconciliationFileParserTest {
    private final ReconciliationFileParser parser = new ReconciliationFileParser(new ObjectMapper());

    @Test
    void shouldParseFrozenValidAndEmptyFixtures() throws Exception {
        ParsedReconciliationFile valid = parse("valid", "20260908", "POSB20260908001");
        assertEquals(2, valid.rows().size());
        assertEquals(0, valid.issues().size());
        assertEquals("RDM20260908000001", valid.rows().get(0).redemptionNo());

        ParsedReconciliationFile empty = parse("empty", "20260907", "POSB20260907001");
        assertEquals(0, empty.rows().size());
        assertEquals(0, empty.issues().size());
    }

    @Test
    void shouldKeepBadRowLocationInsteadOfSilentlyDroppingIt() throws Exception {
        ParsedReconciliationFile invalid = parse("invalid-row", "20260906", "POSB20260906001");
        assertEquals(0, invalid.rows().size());
        assertEquals(1, invalid.issues().size());
        assertEquals(2, invalid.issues().get(0).lineNo());
        assertEquals("ROW_FORMAT_INVALID", invalid.issues().get(0).code());
    }

    @Test
    void shouldRejectRawByteChecksumMismatchBeforeParsingRows() throws Exception {
        byte[] manifest = bytes("contracts/reconciliation/valid/"
                + "POS_20260908_POSB20260908001.manifest.json");
        byte[] csv = bytes("contracts/reconciliation/valid/"
                + "POS_20260908_POSB20260908001.csv");
        csv[csv.length - 1] ^= 1;
        QingheBusinessException failure = assertThrows(QingheBusinessException.class,
                () -> parser.parse(manifest, "POS_20260908_POSB20260908001.csv", csv));
        assertEquals(QingheErrorCode.RECON_FILE_INVALID, failure.errorCode());
    }

    @Test
    void shouldRejectFileNameThatDoesNotFollowFrozenConvention() throws Exception {
        byte[] manifest = bytes("contracts/reconciliation/valid/"
                + "POS_20260908_POSB20260908001.manifest.json");
        byte[] csv = bytes("contracts/reconciliation/valid/"
                + "POS_20260908_POSB20260908001.csv");
        String changedManifest = new String(manifest, java.nio.charset.StandardCharsets.UTF_8)
                .replace("POS_20260908_POSB20260908001.csv", "renamed.csv");
        QingheBusinessException failure = assertThrows(QingheBusinessException.class,
                () -> parser.parse(changedManifest.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                        "renamed.csv", csv));
        assertEquals(QingheErrorCode.RECON_FILE_INVALID, failure.errorCode());
    }

    @Test
    void shouldDigestTheOriginalCsvRecordInsteadOfNormalizedFields() throws Exception {
        String batch = "POSB20260908077";
        String fileName = "POS_20260908_" + batch + ".csv";
        String rawRow = batch + ",invalid-date,\"QH,006\",T03,ORDER-1,REDEEM-1,"
                + "RDM-1,RIGHT-1,REDEEM,SUCCESS,2026-09-08T14:52:10+08:00";
        byte[] csv = ("batch_no,business_date,store_code,terminal_no,pos_order_no,"
                + "pos_request_no,platform_redemption_no,right_code,operation_type,"
                + "operation_status,occurred_at\r\n" + rawRow + "\r\n")
                .getBytes(StandardCharsets.UTF_8);
        String manifest = "{\"provider\":\"MOCK_POS_VENDOR\",\"batchNo\":\"" + batch
                + "\",\"businessDate\":\"2026-09-08\",\"schemaVersion\":\"1.0\","
                + "\"fileName\":\"" + fileName + "\",\"rowCount\":1,"
                + "\"checksumAlgorithm\":\"SHA-256\",\"checksum\":\"" + sha256(csv)
                + "\",\"generatedAt\":\"2026-09-09T02:00:05+08:00\","
                + "\"correctionOfBatchNo\":null}";

        ParsedReconciliationFile parsed = parser.parse(
                manifest.getBytes(StandardCharsets.UTF_8), fileName, csv);

        assertEquals(1, parsed.issues().size());
        assertEquals(sha256(rawRow.getBytes(StandardCharsets.UTF_8)),
                parsed.issues().get(0).rawDigest());
    }

    private ParsedReconciliationFile parse(String folder, String date, String batch) throws Exception {
        String base = "contracts/reconciliation/" + folder + "/POS_" + date + "_" + batch;
        return parser.parse(bytes(base + ".manifest.json"), "POS_" + date + "_" + batch + ".csv",
                bytes(base + ".csv"));
    }

    private static byte[] bytes(String name) throws IOException {
        try (InputStream input = ReconciliationFileParserTest.class.getClassLoader()
                .getResourceAsStream(name)) {
            if (input == null) throw new IOException("resource not found: " + name);
            byte[] buffer = new byte[4096]; int count;
            java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
            while ((count = input.read(buffer)) >= 0) output.write(buffer, 0, count);
            return output.toByteArray();
        }
    }

    private static String sha256(byte[] source) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(source);
        StringBuilder value = new StringBuilder(64);
        for (byte item : digest) {
            value.append(String.format(Locale.ROOT, "%02x", item & 0xff));
        }
        return value.toString();
    }
}
