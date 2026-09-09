package com.qinghe.marketing.store;

import com.qinghe.marketing.shared.clock.BusinessClock;
import com.qinghe.marketing.shared.error.QingheBusinessException;
import com.qinghe.marketing.shared.error.QingheErrorCode;
import com.qinghe.marketing.shared.id.BusinessIdGenerator;
import com.qinghe.marketing.shared.id.BusinessIdType;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class StoreImportService {

    private static final List<String> HEADER = Arrays.asList(
            "source_version", "store_code", "store_name", "ownership_type", "status", "pos_version");
    private static final Pattern SOURCE_VERSION = Pattern.compile("[A-Za-z0-9._-]{1,64}");
    private static final Pattern STORE_CODE = Pattern.compile("[A-Za-z0-9_-]{1,32}");

    private final StoreImportRepository importRepository;
    private final StoreRepository storeRepository;
    private final BusinessIdGenerator idGenerator;
    private final BusinessClock clock;

    public StoreImportService(StoreImportRepository importRepository,
                              StoreRepository storeRepository,
                              BusinessIdGenerator idGenerator,
                              BusinessClock clock) {
        this.importRepository = importRepository;
        this.storeRepository = storeRepository;
        this.idGenerator = idGenerator;
        this.clock = clock;
    }

    @Transactional(rollbackFor = Exception.class)
    public StoreImportPreview preview(byte[] file, String operatorId) {
        if (file == null || file.length == 0) {
            throw invalid("store import file is empty");
        }
        if (operatorId == null || operatorId.trim().isEmpty()) {
            throw invalid("operator id is required");
        }
        ParsedRows parsed = parse(file);
        String digest = sha256(file);
        Optional<StoreImportBatch> existing = importRepository.findBySourceVersion(parsed.sourceVersion);
        if (existing.isPresent()) {
            if (existing.get().fileSha256().equals(digest)) {
                return previewOf(existing.get());
            }
            throw new QingheBusinessException(QingheErrorCode.STORE_IMPORT_CONFLICT,
                    "source version already exists with different file content");
        }

        LocalDateTime now = clock.dateTime();
        StoreImportStatus status = parsed.rows.stream().allMatch(StoreImportRow::valid)
                ? StoreImportStatus.PREVIEWED : StoreImportStatus.REJECTED;
        StoreImportBatch candidate = new StoreImportBatch(0L,
                idGenerator.next(BusinessIdType.STORE_IMPORT), parsed.sourceVersion, digest,
                status, operatorId.trim(), now, null, parsed.rows);
        try {
            return previewOf(importRepository.save(candidate));
        } catch (DuplicateKeyException duplicate) {
            StoreImportBatch raced = importRepository.findBySourceVersion(parsed.sourceVersion)
                    .orElseThrow(() -> duplicate);
            if (!raced.fileSha256().equals(digest)) {
                throw new QingheBusinessException(QingheErrorCode.STORE_IMPORT_CONFLICT,
                        "source version was concurrently imported with different content");
            }
            return previewOf(raced);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public StoreImportPreview commit(String importNo) {
        StoreImportBatch batch = importRepository.findByImportNo(importNo)
                .orElseThrow(() -> new QingheBusinessException(
                        QingheErrorCode.RESOURCE_NOT_FOUND, "store import was not found"));
        if (batch.status() == StoreImportStatus.COMMITTED) {
            return previewOf(batch);
        }
        if (batch.status() != StoreImportStatus.PREVIEWED || batch.errorRows() > 0) {
            throw new QingheBusinessException(QingheErrorCode.BUSINESS_STATE_CONFLICT,
                    "only an error-free preview can be committed");
        }
        LocalDateTime now = clock.dateTime();
        for (StoreImportRow row : batch.rows()) {
            storeRepository.upsert(row, now);
        }
        importRepository.markCommitted(batch.id(), now);
        return new StoreImportPreview(batch.importNo(), StoreImportStatus.COMMITTED,
                batch.sourceVersion(), batch.totalRows(), batch.validRows(), new ArrayList<StoreImportRow>());
    }

    @Transactional(readOnly = true)
    public StoreImportPreview get(String importNo) {
        if (importNo == null || importNo.trim().isEmpty()) {
            throw invalid("import number is required");
        }
        return importRepository.findByImportNo(importNo.trim())
                .map(StoreImportService::previewOf)
                .orElseThrow(() -> new QingheBusinessException(
                        QingheErrorCode.RESOURCE_NOT_FOUND, "store import was not found"));
    }

    private ParsedRows parse(byte[] file) {
        String content = new String(file, StandardCharsets.UTF_8);
        if (!content.isEmpty() && content.charAt(0) == '\ufeff') {
            content = content.substring(1);
        }
        String[] lines = content.split("\\r?\\n", -1);
        if (lines.length < 2 || !CsvLineParser.parse(lines[0]).equals(HEADER)) {
            throw invalid("store import header does not match the contract");
        }

        List<StoreImportRow> rows = new ArrayList<StoreImportRow>();
        Set<String> storeCodes = new HashSet<String>();
        String sourceVersion = null;
        for (int index = 1; index < lines.length; index++) {
            if (lines[index].trim().isEmpty()) {
                continue;
            }
            int rowNumber = index + 1;
            List<String> columns;
            try {
                columns = CsvLineParser.parse(lines[index]);
            } catch (IllegalArgumentException malformedCsv) {
                throw invalid("malformed CSV at row " + rowNumber);
            }
            while (columns.size() < HEADER.size()) {
                columns.add("");
            }
            String rowSourceVersion = value(columns, 0);
            if (sourceVersion == null && !rowSourceVersion.isEmpty()) {
                sourceVersion = rowSourceVersion;
            }
            List<String> errors = new ArrayList<String>();
            if (columns.size() != HEADER.size()) {
                errors.add("expected 6 columns");
            }
            if (!SOURCE_VERSION.matcher(rowSourceVersion).matches()) {
                errors.add("invalid source_version");
            } else if (sourceVersion != null && !sourceVersion.equals(rowSourceVersion)) {
                errors.add("source_version differs from the file version");
            }
            String storeCode = value(columns, 1).toUpperCase(Locale.ROOT);
            if (!STORE_CODE.matcher(storeCode).matches()) {
                errors.add("invalid store_code");
            } else if (!storeCodes.add(storeCode)) {
                errors.add("duplicate store_code in file");
            }
            String storeName = value(columns, 2);
            if (storeName.isEmpty() || storeName.length() > 128) {
                errors.add("invalid store_name");
            }
            StoreOwnershipType ownership = enumValue(StoreOwnershipType.class, value(columns, 3),
                    "invalid ownership_type", errors);
            StoreStatus status = enumValue(StoreStatus.class, value(columns, 4),
                    "invalid status", errors);
            String posVersion = value(columns, 5);
            if (posVersion.isEmpty() || posVersion.length() > 32) {
                errors.add("invalid pos_version");
            }
            rows.add(new StoreImportRow(rowNumber, rowSourceVersion, storeCode, storeName,
                    ownership, status, posVersion, errors.isEmpty() ? null : String.join("; ", errors)));
        }
        if (rows.isEmpty()) {
            throw invalid("store import has no data rows");
        }
        if (sourceVersion == null || !SOURCE_VERSION.matcher(sourceVersion).matches()) {
            throw invalid("store import has no valid source_version");
        }
        return new ParsedRows(sourceVersion, rows);
    }

    private static StoreImportPreview previewOf(StoreImportBatch batch) {
        List<StoreImportRow> errors = batch.rows().stream()
                .filter(row -> !row.valid()).collect(Collectors.toList());
        return new StoreImportPreview(batch.importNo(), batch.status(), batch.sourceVersion(),
                batch.totalRows(), batch.validRows(), errors);
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, String value,
                                                    String message, List<String> errors) {
        try {
            return Enum.valueOf(type, value.toUpperCase(Locale.ROOT));
        } catch (RuntimeException invalid) {
            errors.add(message);
            return null;
        }
    }

    private static String value(List<String> values, int index) {
        return index >= values.size() ? "" : values.get(index).trim();
    }

    private static String sha256(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                result.append(String.format("%02x", value & 0xff));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static QingheBusinessException invalid(String message) {
        return new QingheBusinessException(QingheErrorCode.INVALID_ARGUMENT, message);
    }

    private static final class ParsedRows {
        private final String sourceVersion;
        private final List<StoreImportRow> rows;

        private ParsedRows(String sourceVersion, List<StoreImportRow> rows) {
            this.sourceVersion = sourceVersion;
            this.rows = rows;
        }
    }

    static final class CsvLineParser {
        private CsvLineParser() {
        }

        static List<String> parse(String line) {
            List<String> fields = new ArrayList<String>();
            StringBuilder field = new StringBuilder();
            boolean quoted = false;
            for (int index = 0; index < line.length(); index++) {
                char current = line.charAt(index);
                if (current == '"') {
                    if (quoted && index + 1 < line.length() && line.charAt(index + 1) == '"') {
                        field.append('"');
                        index++;
                    } else {
                        quoted = !quoted;
                    }
                } else if (current == ',' && !quoted) {
                    fields.add(field.toString());
                    field.setLength(0);
                } else {
                    field.append(current);
                }
            }
            if (quoted) {
                throw new IllegalArgumentException("unclosed quoted field");
            }
            fields.add(field.toString());
            return fields;
        }
    }
}
