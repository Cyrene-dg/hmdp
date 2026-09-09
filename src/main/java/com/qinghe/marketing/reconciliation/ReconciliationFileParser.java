package com.qinghe.marketing.reconciliation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qinghe.marketing.shared.error.QingheBusinessException;
import com.qinghe.marketing.shared.error.QingheErrorCode;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
public class ReconciliationFileParser {
    private static final List<String> HEADER = Arrays.asList("batch_no", "business_date",
            "store_code", "terminal_no", "pos_order_no", "pos_request_no",
            "platform_redemption_no", "right_code", "operation_type", "operation_status",
            "occurred_at");
    private static final Set<String> MANIFEST_FIELDS = new HashSet<String>(Arrays.asList(
            "provider", "batchNo", "businessDate", "schemaVersion", "fileName", "rowCount",
            "checksumAlgorithm", "checksum", "generatedAt", "correctionOfBatchNo"));
    private final ObjectMapper mapper;

    public ReconciliationFileParser(ObjectMapper mapper) { this.mapper = mapper; }

    public ParsedReconciliationFile parse(byte[] manifestBytes, String actualFileName,
                                          byte[] csvBytes) {
        ReconciliationManifest manifest = manifest(manifestBytes);
        require(manifest.fileName().equals(actualFileName), "manifest fileName does not match");
        require(manifest.checksum().equals(sha256(csvBytes)), "CSV checksum does not match");
        String text = utf8(csvBytes);
        require(!text.startsWith("\uFEFF"), "CSV must not contain BOM");
        List<List<String>> records = records(text);
        require(!records.isEmpty() && HEADER.equals(records.get(0)), "CSV header is invalid");
        int dataRows = records.size() - 1;
        require(dataRows == manifest.rowCount(), "CSV rowCount does not match manifest");
        List<ReconciliationCsvRow> valid = new ArrayList<ReconciliationCsvRow>();
        List<ReconciliationRowIssue> issues = new ArrayList<ReconciliationRowIssue>();
        for (int index = 1; index < records.size(); index++) {
            List<String> values = records.get(index);
            String digest = sha256(String.join("\u001f", values).getBytes(StandardCharsets.UTF_8));
            try {
                valid.add(row(index + 1, values, manifest, digest));
            } catch (RuntimeException invalid) {
                issues.add(new ReconciliationRowIssue(index + 1, "ROW_FORMAT_INVALID", digest));
            }
        }
        return new ParsedReconciliationFile(manifest, valid, issues);
    }

    private ReconciliationManifest manifest(byte[] bytes) {
        try {
            JsonNode root = mapper.readTree(bytes);
            require(root != null && root.isObject(), "manifest must be an object");
            root.fieldNames().forEachRemaining(name -> require(MANIFEST_FIELDS.contains(name),
                    "manifest contains an unknown field"));
            String provider = required(root, "provider");
            String batchNo = required(root, "batchNo");
            require("1.0".equals(required(root, "schemaVersion")), "schemaVersion is unsupported");
            require("SHA-256".equals(required(root, "checksumAlgorithm")), "checksum algorithm is unsupported");
            int rowCount = root.path("rowCount").asInt(-1);
            require(rowCount >= 0, "rowCount is invalid");
            String checksum = required(root, "checksum");
            require(checksum.matches("[a-f0-9]{64}"), "checksum is invalid");
            String correction = root.path("correctionOfBatchNo").isNull()
                    ? null : root.path("correctionOfBatchNo").asText(null);
            require(provider.matches("[A-Za-z0-9._:-]{1,64}"), "provider is invalid");
            require(batchNo.matches("[A-Za-z0-9._:-]{8,64}"), "batchNo is invalid");
            if (correction != null) require(correction.matches("[A-Za-z0-9._:-]{8,64}"),
                    "correctionOfBatchNo is invalid");
            return new ReconciliationManifest(provider, batchNo,
                    LocalDate.parse(required(root, "businessDate")), required(root, "fileName"),
                    rowCount, checksum, OffsetDateTime.parse(required(root, "generatedAt")), correction);
        } catch (QingheBusinessException expected) {
            throw expected;
        } catch (RuntimeException invalid) {
            throw invalid("manifest is invalid");
        } catch (Exception invalid) {
            throw invalid("manifest cannot be read");
        }
    }

    private ReconciliationCsvRow row(int lineNo, List<String> v,
                                     ReconciliationManifest manifest, String digest) {
        if (v.size() != HEADER.size()) throw new IllegalArgumentException();
        LocalDate businessDate = LocalDate.parse(v.get(1));
        OffsetDateTime occurredAt = OffsetDateTime.parse(v.get(10));
        if (!v.get(0).equals(manifest.batchNo()) || !businessDate.equals(manifest.businessDate())
                || !text(v.get(2), 32) || !text(v.get(3), 32) || !text(v.get(4), 64)
                || !text(v.get(5), 64) || !text(v.get(7), 128)
                || !("REDEEM".equals(v.get(8)) || "REVERSE".equals(v.get(8)))
                || !("SUCCESS".equals(v.get(9)) || "FAILED".equals(v.get(9)))) {
            throw new IllegalArgumentException();
        }
        return new ReconciliationCsvRow(lineNo, v.get(0), businessDate, v.get(2), v.get(3),
                v.get(4), v.get(5), emptyToNull(v.get(6)), v.get(7), v.get(8), v.get(9),
                occurredAt, digest);
    }

    private static List<List<String>> records(String text) {
        List<List<String>> rows = new ArrayList<List<String>>();
        List<String> row = new ArrayList<String>(); StringBuilder field = new StringBuilder();
        boolean quoted = false;
        for (int index = 0; index < text.length(); index++) {
            char ch = text.charAt(index);
            if (ch == '"') {
                if (quoted && index + 1 < text.length() && text.charAt(index + 1) == '"') {
                    field.append('"'); index++;
                } else quoted = !quoted;
            } else if (ch == ',' && !quoted) {
                row.add(field.toString()); field.setLength(0);
            } else if ((ch == '\n' || ch == '\r') && !quoted) {
                if (ch == '\r' && index + 1 < text.length() && text.charAt(index + 1) == '\n') index++;
                row.add(field.toString()); field.setLength(0);
                if (!(row.size() == 1 && row.get(0).isEmpty())) rows.add(row);
                row = new ArrayList<String>();
            } else field.append(ch);
        }
        require(!quoted, "CSV contains an unclosed quote");
        if (field.length() > 0 || !row.isEmpty()) { row.add(field.toString()); rows.add(row); }
        return rows;
    }

    private static String utf8(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException invalid) { throw invalid("CSV is not valid UTF-8"); }
    }
    private static String required(JsonNode root, String field) {
        JsonNode value = root.get(field);
        require(value != null && value.isTextual() && !value.asText().isEmpty(), field + " is required");
        return value.asText();
    }
    private static boolean text(String value, int max) { return value != null && !value.isEmpty() && value.length() <= max; }
    private static String emptyToNull(String value) { return value == null || value.isEmpty() ? null : value; }
    private static String sha256(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder result = new StringBuilder(64);
            for (byte item : digest) result.append(String.format(Locale.ROOT, "%02x", item & 0xff));
            return result.toString();
        } catch (Exception unavailable) { throw new IllegalStateException(unavailable); }
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw invalid(message);
    }
    private static QingheBusinessException invalid(String message) {
        return new QingheBusinessException(QingheErrorCode.RECON_FILE_INVALID, message);
    }
}
