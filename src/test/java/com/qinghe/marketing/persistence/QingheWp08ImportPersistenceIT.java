package com.qinghe.marketing.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qinghe.marketing.entitlement.AesGcmRightCodeProtector;
import com.qinghe.marketing.entitlement.ProtectedRightCode;
import com.qinghe.marketing.entitlement.RightCodeProtector;
import com.qinghe.marketing.reconciliation.JdbcReconciliationBatchRepository;
import com.qinghe.marketing.reconciliation.ReconciliationBatchRepository;
import com.qinghe.marketing.reconciliation.ReconciliationBatchStatus;
import com.qinghe.marketing.reconciliation.ReconciliationChunkFailureTransactionService;
import com.qinghe.marketing.reconciliation.ReconciliationChunkTransactionService;
import com.qinghe.marketing.reconciliation.ReconciliationCompletionTransactionService;
import com.qinghe.marketing.reconciliation.ReconciliationFileParser;
import com.qinghe.marketing.reconciliation.ReconciliationImportResult;
import com.qinghe.marketing.reconciliation.ReconciliationImportService;
import com.qinghe.marketing.reconciliation.ReconciliationRegistrationTransactionService;
import com.qinghe.marketing.shared.clock.BusinessClock;
import com.qinghe.marketing.shared.error.QingheBusinessException;
import com.qinghe.marketing.shared.error.QingheErrorCode;
import com.qinghe.marketing.shared.id.BusinessIdGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Real MySQL probe for resumable, idempotent reconciliation file import. */
class QingheWp08ImportPersistenceIT {
    private static final String HOST_URL = System.getProperty("qinghe.it.mysql.url",
            "jdbc:mysql://127.0.0.1:3306/?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true");
    private static final String USER = System.getProperty("qinghe.it.mysql.user", "root");
    private static final String PASSWORD = credential(
            "qinghe.it.mysql.password", "QINGHE_IT_MYSQL_PASSWORD");

    @Test
    void shouldImportReplayConflictAuditBadRowsAndResumeFailedChunk() throws Exception {
        assertFalse(PASSWORD.isEmpty(),
                "Set QINGHE_IT_MYSQL_PASSWORD for the disposable database probe");
        String database = "qh_wp08_" + UUID.randomUUID().toString().replace("-", "")
                .substring(0, 12).toLowerCase(Locale.ROOT);
        if (!database.matches("qh_wp08_[a-f0-9]{12}")) {
            throw new IllegalStateException("unsafe database name");
        }
        createDatabase(database);
        try {
            String url = HOST_URL.replace("/?", "/" + database + "?");
            try (Connection connection = DriverManager.getConnection(url, USER, PASSWORD)) {
                for (int version = 1; version <= 7; version++) {
                    executeScript(connection, migration(version));
                }
                try (Statement statement = connection.createStatement()) {
                    statement.execute("INSERT INTO qh_recon_batch "
                            + "(provider,batch_no,business_date,checksum,status,total_rows,error_rows,created_at,updated_at) "
                            + "VALUES ('LEGACY_POS','LEGACY-BATCH-1','2026-09-01',REPEAT('a',64),"
                            + "'RECEIVED',0,0,NOW(3),NOW(3))");
                }
                executeScript(connection, migration(8));
                assertEquals(32, tableCount(connection));
                try (Statement statement = connection.createStatement();
                     ResultSet rows = statement.executeQuery("SELECT recon_batch_no, file_name, "
                             + "schema_version FROM qh_recon_batch WHERE batch_no='LEGACY-BATCH-1'")) {
                    assertTrue(rows.next());
                    assertTrue(rows.getString(1).startsWith("RCB-LEGACY-"));
                    assertEquals("LEGACY-LEGACY-BATCH-1.csv", rows.getString(2));
                    assertEquals("LEGACY", rows.getString(3));
                }
            }

            DriverManagerDataSource dataSource = new DriverManagerDataSource(url, USER, PASSWORD);
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            PlatformTransactionManager transactions = new DataSourceTransactionManager(dataSource);
            BusinessClock clock = () -> Instant.parse("2026-09-09T02:30:00Z");
            ReconciliationBatchRepository repository =
                    new JdbcReconciliationBatchRepository(jdbc);
            ReconciliationImportService service = service(repository, transactions, clock,
                    new AesGcmRightCodeProtector(""));

            Fixture valid = fixture("valid", "20260908", "POSB20260908001");
            ReconciliationImportResult imported = service.importFile(
                    valid.manifest, valid.fileName, valid.csv, 1);
            assertEquals(ReconciliationBatchStatus.MATCHING, imported.status());
            assertEquals(2, imported.totalRows());
            assertEquals(2, imported.successRows());
            assertEquals(0, imported.errorRows());
            assertFalse(imported.replayed());
            assertEquals(2, count(jdbc, "qh_recon_record"));
            assertEquals(2, count(jdbc, "qh_recon_import_chunk"));
            assertEquals(0, count(jdbc, "qh_recon_import_issue"));
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM qh_recon_record "
                    + "WHERE right_code_hash IN ('RGT-F4B8N2K9Q6')", Integer.class));

            ReconciliationImportResult replay = service.importFile(
                    valid.manifest, valid.fileName, valid.csv, 1);
            assertTrue(replay.replayed());
            assertEquals(imported.reconBatchNo(), replay.reconBatchNo());
            assertEquals(2, count(jdbc, "qh_recon_record"));
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM qh_recon_file_attempt "
                    + "WHERE result='DUPLICATE'", Integer.class));

            byte[] changedCsv = append(valid.csv, "\r\n");
            byte[] changedManifest = replace(valid.manifest,
                    jsonValue(valid.manifest, "checksum"), sha256(changedCsv));
            QingheBusinessException conflict = assertThrows(QingheBusinessException.class,
                    () -> service.importFile(changedManifest, valid.fileName, changedCsv, 1));
            assertEquals(QingheErrorCode.RECON_BATCH_CONFLICT, conflict.errorCode());
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM qh_recon_file_attempt "
                    + "WHERE result='CONFLICT'", Integer.class));

            Fixture invalid = fixture("invalid-row", "20260906", "POSB20260906001");
            ReconciliationImportResult partial = service.importFile(
                    invalid.manifest, invalid.fileName, invalid.csv, 100);
            assertEquals(ReconciliationBatchStatus.PARTIAL_FAILED, partial.status());
            assertEquals(0, partial.successRows());
            assertEquals(1, partial.errorRows());
            assertEquals(1, count(jdbc, "qh_recon_import_issue"));

            Fixture resumable = renamed(valid, "POSB20260908002");
            ReconciliationImportService failing = service(repository, transactions, clock,
                    new FailsOnceRightCodeProtector());
            assertThrows(IllegalStateException.class, () -> failing.importFile(
                    resumable.manifest, resumable.fileName, resumable.csv, 1));
            assertEquals("IMPORTING", jdbc.queryForObject("SELECT status FROM qh_recon_batch "
                    + "WHERE batch_no='POSB20260908002'", String.class));
            assertEquals(1, jdbc.queryForObject("SELECT attempt_count FROM qh_recon_import_chunk c "
                    + "JOIN qh_recon_batch b ON b.id=c.batch_id "
                    + "WHERE b.batch_no='POSB20260908002' AND c.chunk_no=0", Integer.class));
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM qh_recon_record r "
                    + "JOIN qh_recon_batch b ON b.id=r.batch_id "
                    + "WHERE b.batch_no='POSB20260908002'", Integer.class));

            ReconciliationImportResult resumed = service.importFile(resumable.manifest,
                    resumable.fileName, resumable.csv, 1);
            assertEquals(ReconciliationBatchStatus.MATCHING, resumed.status());
            assertEquals(2, resumed.successRows());
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM qh_recon_file_attempt a "
                    + "WHERE a.provider_batch_no='POSB20260908002' AND a.result='RESUMED'",
                    Integer.class));

            Fixture empty = fixture("empty", "20260907", "POSB20260907001");
            ReconciliationImportResult zero = service.importFile(
                    empty.manifest, empty.fileName, empty.csv, 100);
            assertEquals(ReconciliationBatchStatus.MATCHING, zero.status());
            assertEquals(0, zero.totalRows());

            try (Connection connection = DriverManager.getConnection(url, USER, PASSWORD)) {
                executeScript(connection,
                        "db/qinghe/rollback/R008__drop_reconciliation_import_support.sql");
                assertEquals(29, tableCount(connection));
            }
        } finally {
            dropDatabase(database);
        }
    }

    private static ReconciliationImportService service(ReconciliationBatchRepository repository,
                                                        PlatformTransactionManager transactions,
                                                        BusinessClock clock,
                                                        RightCodeProtector protector) {
        ReconciliationRegistrationTransactionService registrations = transactional(
                new ReconciliationRegistrationTransactionService(repository,
                        new BusinessIdGenerator(clock), clock), transactions);
        ReconciliationChunkTransactionService chunks = transactional(
                new ReconciliationChunkTransactionService(repository, protector, clock), transactions);
        ReconciliationChunkFailureTransactionService failures = transactional(
                new ReconciliationChunkFailureTransactionService(repository, clock), transactions);
        ReconciliationCompletionTransactionService completions = transactional(
                new ReconciliationCompletionTransactionService(repository, clock), transactions);
        return new ReconciliationImportService(new ReconciliationFileParser(new ObjectMapper()),
                registrations, chunks, failures, completions, repository);
    }

    @SuppressWarnings("unchecked")
    private static <T> T transactional(T target, PlatformTransactionManager manager) {
        ProxyFactory proxy = new ProxyFactory(target);
        proxy.setProxyTargetClass(true);
        TransactionInterceptor advice = new TransactionInterceptor();
        advice.setTransactionManager(manager);
        advice.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
        proxy.addAdvice(advice);
        return (T) proxy.getProxy();
    }

    private static Fixture renamed(Fixture source, String batchNo) throws Exception {
        String oldBatch = jsonValue(source.manifest, "batchNo");
        String oldFile = source.fileName;
        String newFile = oldFile.replace(oldBatch, batchNo);
        byte[] csv = replace(source.csv, oldBatch, batchNo);
        byte[] manifest = replace(source.manifest, oldBatch, batchNo);
        manifest = replace(manifest, oldFile, newFile);
        manifest = replace(manifest, jsonValue(manifest, "checksum"), sha256(csv));
        return new Fixture(manifest, newFile, csv);
    }

    private static Fixture fixture(String folder, String date, String batch) throws Exception {
        String stem = "POS_" + date + "_" + batch;
        return new Fixture(resourceBytes("contracts/reconciliation/" + folder + "/"
                + stem + ".manifest.json"), stem + ".csv",
                resourceBytes("contracts/reconciliation/" + folder + "/" + stem + ".csv"));
    }

    private static int count(JdbcTemplate jdbc, String table) {
        if (!table.matches("qh_[a-z_]+")) throw new IllegalArgumentException("unsafe table");
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }

    private static String jsonValue(byte[] json, String field) throws Exception {
        return new ObjectMapper().readTree(json).path(field).asText();
    }

    private static byte[] replace(byte[] source, String from, String to) {
        return new String(source, StandardCharsets.UTF_8).replace(from, to)
                .getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] append(byte[] source, String suffix) {
        return (new String(source, StandardCharsets.UTF_8) + suffix)
                .getBytes(StandardCharsets.UTF_8);
    }

    private static String sha256(byte[] source) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(source);
        StringBuilder value = new StringBuilder(64);
        for (byte item : digest) value.append(String.format(Locale.ROOT, "%02x", item & 0xff));
        return value.toString();
    }

    private static String migration(int version) {
        switch (version) {
            case 1: return "db/qinghe/migration/V001__create_qinghe_core.sql";
            case 2: return "db/qinghe/migration/V002__create_identity_and_store_support.sql";
            case 3: return "db/qinghe/migration/V003__add_campaign_publication_and_reviews.sql";
            case 4: return "db/qinghe/migration/V004__add_claim_outbox_delivery_support.sql";
            case 5: return "db/qinghe/migration/V005__add_entitlement_issue_support.sql";
            case 6: return "db/qinghe/migration/V006__add_pos_redemption_support.sql";
            case 7: return "db/qinghe/migration/V007__add_reversal_idempotency_and_audit.sql";
            case 8: return "db/qinghe/migration/V008__add_reconciliation_import_support.sql";
            default: throw new IllegalArgumentException("unsupported migration");
        }
    }

    private static void createDatabase(String database) throws SQLException {
        executeHost("CREATE DATABASE " + database
                + " CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
    }

    private static void dropDatabase(String database) throws SQLException {
        executeHost("DROP DATABASE " + database);
    }

    private static void executeHost(String sql) throws SQLException {
        try (Connection connection = DriverManager.getConnection(HOST_URL, USER, PASSWORD);
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static void executeScript(Connection connection, String name) throws Exception {
        for (String part : resource(name).split(";")) {
            if (!part.trim().isEmpty()) {
                try (Statement statement = connection.createStatement()) {
                    statement.execute(part.trim());
                }
            }
        }
    }

    private static int tableCount(Connection connection) throws SQLException {
        int count = 0;
        try (ResultSet tables = connection.getMetaData().getTables(
                connection.getCatalog(), null, "qh\\_%", new String[]{"TABLE"})) {
            while (tables.next()) count++;
        }
        return count;
    }

    private static byte[] resourceBytes(String name) throws IOException {
        try (InputStream input = QingheWp08ImportPersistenceIT.class.getClassLoader()
                .getResourceAsStream(name)) {
            if (input == null) throw new IOException("resource not found: " + name);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[16384]; int count;
            while ((count = input.read(buffer)) >= 0) output.write(buffer, 0, count);
            return output.toByteArray();
        }
    }

    private static String resource(String name) throws IOException {
        return new String(resourceBytes(name), StandardCharsets.UTF_8);
    }

    private static String credential(String property, String environment) {
        String value = System.getProperty(property);
        if (value != null && !value.trim().isEmpty()) return value;
        value = System.getenv(environment);
        return value == null ? "" : value;
    }

    private static final class Fixture {
        private final byte[] manifest;
        private final String fileName;
        private final byte[] csv;
        private Fixture(byte[] manifest, String fileName, byte[] csv) {
            this.manifest = manifest; this.fileName = fileName; this.csv = csv;
        }
    }

    private static final class FailsOnceRightCodeProtector implements RightCodeProtector {
        private boolean failed;
        @Override public ProtectedRightCode protect(String plaintext) {
            throw new UnsupportedOperationException();
        }
        @Override public String hash(String plaintext) {
            if (!failed) {
                failed = true;
                throw new IllegalStateException("synthetic chunk failure");
            }
            return new AesGcmRightCodeProtector("").hash(plaintext);
        }
        @Override public String reveal(byte[] encrypted) {
            throw new UnsupportedOperationException();
        }
    }
}
