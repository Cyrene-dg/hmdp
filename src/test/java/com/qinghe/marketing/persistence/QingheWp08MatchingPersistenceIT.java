package com.qinghe.marketing.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qinghe.marketing.entitlement.AesGcmRightCodeProtector;
import com.qinghe.marketing.entitlement.RightCodeProtector;
import com.qinghe.marketing.reconciliation.JdbcReconciliationBatchRepository;
import com.qinghe.marketing.reconciliation.JdbcReconciliationMatchingRepository;
import com.qinghe.marketing.reconciliation.ReconciliationBatchRepository;
import com.qinghe.marketing.reconciliation.ReconciliationBatchStatus;
import com.qinghe.marketing.reconciliation.ReconciliationChunkFailureTransactionService;
import com.qinghe.marketing.reconciliation.ReconciliationChunkTransactionService;
import com.qinghe.marketing.reconciliation.ReconciliationCompletionTransactionService;
import com.qinghe.marketing.reconciliation.ReconciliationFileParser;
import com.qinghe.marketing.reconciliation.ReconciliationImportResult;
import com.qinghe.marketing.reconciliation.ReconciliationImportService;
import com.qinghe.marketing.reconciliation.ReconciliationMatchSummary;
import com.qinghe.marketing.reconciliation.ReconciliationMatchingCompletionService;
import com.qinghe.marketing.reconciliation.ReconciliationMatchingRepository;
import com.qinghe.marketing.reconciliation.ReconciliationMatchingService;
import com.qinghe.marketing.reconciliation.ReconciliationMatchingTransactionService;
import com.qinghe.marketing.reconciliation.ReconciliationRegistrationTransactionService;
import com.qinghe.marketing.shared.clock.BusinessClock;
import com.qinghe.marketing.shared.id.BusinessIdGenerator;
import com.qinghe.marketing.settlement.JdbcSettlementRepository;
import com.qinghe.marketing.settlement.SettlementBatch;
import com.qinghe.marketing.settlement.SettlementBatchStatus;
import com.qinghe.marketing.settlement.SettlementConfirmCommand;
import com.qinghe.marketing.settlement.SettlementConfirmationService;
import com.qinghe.marketing.settlement.SettlementExport;
import com.qinghe.marketing.settlement.SettlementExportService;
import com.qinghe.marketing.settlement.SettlementGenerateCommand;
import com.qinghe.marketing.settlement.SettlementGenerationOutcome;
import com.qinghe.marketing.settlement.SettlementGenerationResult;
import com.qinghe.marketing.settlement.SettlementGenerationService;
import com.qinghe.marketing.settlement.SettlementRepository;
import com.qinghe.marketing.shared.error.QingheBusinessException;
import com.qinghe.marketing.shared.error.QingheErrorCode;
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
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Real MySQL probe for reconciliation matching and subsidy-detail eligibility. */
class QingheWp08MatchingPersistenceIT {
    private static final String HOST_URL = System.getProperty("qinghe.it.mysql.url",
            "jdbc:mysql://127.0.0.1:3306/?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true");
    private static final String USER = System.getProperty("qinghe.it.mysql.user", "root");
    private static final String PASSWORD = credential(
            "qinghe.it.mysql.password", "QINGHE_IT_MYSQL_PASSWORD");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 9, 10, 0);

    @Test
    void shouldMatchFactsClassifyDifferencesAndOnlyPrepareFranchiseSettlement() throws Exception {
        assertFalse(PASSWORD.isEmpty(),
                "Set QINGHE_IT_MYSQL_PASSWORD for the disposable database probe");
        String database = "qh_wp08_match_" + UUID.randomUUID().toString().replace("-", "")
                .substring(0, 12).toLowerCase(Locale.ROOT);
        if (!database.matches("qh_wp08_match_[a-f0-9]{12}")) {
            throw new IllegalStateException("unsafe database name");
        }
        createDatabase(database);
        try {
            String url = HOST_URL.replace("/?", "/" + database + "?");
            try (Connection connection = DriverManager.getConnection(url, USER, PASSWORD)) {
                for (int version = 1; version <= 8; version++) {
                    executeScript(connection, migration(version));
                }
                assertEquals(33, tableCount(connection));
            }
            DriverManagerDataSource dataSource = new DriverManagerDataSource(url, USER, PASSWORD);
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            PlatformTransactionManager transactionManager =
                    new DataSourceTransactionManager(dataSource);
            BusinessClock clock = () -> Instant.parse("2026-09-09T02:00:00Z");
            RightCodeProtector protector = new AesGcmRightCodeProtector("");
            seed(jdbc, protector);

            ReconciliationBatchRepository batches = new JdbcReconciliationBatchRepository(jdbc);
            ReconciliationImportService importer = importService(
                    batches, transactionManager, clock, protector);
            FilePair file = file();
            ReconciliationImportResult imported = importer.importFile(
                    file.manifest, file.fileName, file.csv, 2);
            assertEquals(ReconciliationBatchStatus.MATCHING, imported.status());

            ReconciliationMatchingRepository matchingRepository =
                    new JdbcReconciliationMatchingRepository(jdbc);
            ReconciliationMatchingTransactionService matchingTransactions = transactional(
                    new ReconciliationMatchingTransactionService(matchingRepository, clock),
                    transactionManager);
            ReconciliationMatchingCompletionService matchingCompletion = transactional(
                    new ReconciliationMatchingCompletionService(matchingRepository, clock),
                    transactionManager);
            ReconciliationMatchSummary summary = new ReconciliationMatchingService(
                    matchingTransactions, matchingCompletion).match(batchId(jdbc), 2);

            assertEquals(0, summary.unmatchedRows());
            assertEquals(4, summary.matchedRows());
            assertEquals(5, summary.differenceRows());
            assertEquals(1, summary.directMatchedRows());
            assertEquals(1, summary.franchiseEligibleRows());
            assertEquals("COMPLETED", jdbc.queryForObject("SELECT status FROM qh_recon_batch "
                    + "WHERE batch_no='POSB20260908099'", String.class));
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM qh_settlement_detail "
                    + "WHERE status='PENDING_CONFIRM' AND settlement_batch_id IS NULL",
                    Integer.class));
            assertEquals("MATCHED", jdbc.queryForObject("SELECT status FROM qh_subsidy_candidate "
                    + "WHERE redemption_id=101", String.class));
            assertEquals("DIFFERENCE", jdbc.queryForObject("SELECT status FROM qh_subsidy_candidate "
                    + "WHERE redemption_id=104", String.class));
            assertEquals("DIFFERENCE", jdbc.queryForObject("SELECT status FROM qh_subsidy_candidate "
                    + "WHERE redemption_id=105", String.class));
            assertEquals("CANCELLED", jdbc.queryForObject("SELECT status FROM qh_subsidy_candidate "
                    + "WHERE redemption_id=103", String.class));
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM qh_recon_difference "
                    + "WHERE difference_type='DIFFERENCE_POS_ONLY'", Integer.class));
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM qh_recon_difference "
                    + "WHERE difference_type='DIFFERENCE_PLATFORM_ONLY'", Integer.class));
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM qh_recon_difference "
                    + "WHERE difference_type='DIFFERENCE_STATUS'", Integer.class));
            assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM qh_recon_difference "
                    + "WHERE difference_type='DIFFERENCE_DATA'", Integer.class));
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM qh_recon_difference "
                    + "WHERE detail='duplicate POS request in the same reconciliation batch'",
                    Integer.class));

            ReconciliationMatchSummary replay = new ReconciliationMatchingService(
                    matchingTransactions, matchingCompletion).match(batchId(jdbc), 2);
            assertEquals(4, replay.matchedRows());
            assertEquals(5, replay.differenceRows());
            assertEquals(1, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM qh_settlement_detail", Integer.class));

            SettlementRepository settlementRepository = new JdbcSettlementRepository(jdbc);
            SettlementGenerationService generation = transactional(
                    new SettlementGenerationService(settlementRepository,
                            new BusinessIdGenerator(clock), clock), transactionManager);
            long reconVersion = jdbc.queryForObject("SELECT version FROM qh_recon_batch "
                    + "WHERE batch_no='POSB20260908099'", Long.class);
            SettlementGenerationResult generated = generation.generate(imported.reconBatchNo(),
                    new SettlementGenerateCommand(reconVersion, "对账完成，申请生成结算批次",
                            "FIN-008", "settlement-generate-001"));
            assertEquals(SettlementGenerationOutcome.CREATED, generated.outcome());
            assertEquals(SettlementBatchStatus.PENDING_CONFIRM, generated.batch().status());
            assertEquals(1, generated.batch().detailCount());
            assertEquals(1, generated.batch().storeCount());
            assertEquals(350, generated.batch().totalFen());
            assertEquals(1, generated.batch().version());

            SettlementGenerationResult generationReplay = generation.generate(
                    imported.reconBatchNo(), new SettlementGenerateCommand(reconVersion,
                            "重复生成请求", "FIN-008", "settlement-generate-002"));
            assertEquals(SettlementGenerationOutcome.ALREADY_EXISTS,
                    generationReplay.outcome());
            assertEquals(generated.batch().batchNo(), generationReplay.batch().batchNo());

            SettlementConfirmationService confirmation = transactional(
                    new SettlementConfirmationService(settlementRepository, clock),
                    transactionManager);
            QingheBusinessException changed = assertThrows(QingheBusinessException.class,
                    () -> confirmation.confirm(generated.batch().batchNo(),
                            new SettlementConfirmCommand(1, 2, 350, "错误笔数",
                                    "FIN-008", "settlement-confirm-bad")));
            assertEquals(QingheErrorCode.SETTLEMENT_BATCH_CHANGED, changed.errorCode());
            SettlementBatch confirmed = confirmation.confirm(generated.batch().batchNo(),
                    new SettlementConfirmCommand(1, 1, 350, "笔数金额复核一致",
                            "FIN-008", "settlement-confirm-001"));
            assertEquals(SettlementBatchStatus.CONFIRMED, confirmed.status());
            assertEquals(2, confirmed.version());
            assertEquals("FIN-008", confirmed.confirmedBy());
            SettlementBatch confirmReplay = confirmation.confirm(generated.batch().batchNo(),
                    new SettlementConfirmCommand(1, 1, 350, "重复确认",
                            "FIN-008", "settlement-confirm-002"));
            assertEquals(SettlementBatchStatus.CONFIRMED, confirmReplay.status());
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM qh_settlement_detail "
                    + "WHERE status='CONFIRMED'", Integer.class));

            SettlementExportService exportService = transactional(
                    new SettlementExportService(settlementRepository, clock), transactionManager);
            SettlementExport export = exportService.export(generated.batch().batchNo(),
                    "FIN-008", "settlement-export-001");
            String exported = new String(export.content(), StandardCharsets.UTF_8);
            assertTrue(export.fileName().startsWith(
                    "QH_SETTLEMENT_" + generated.batch().batchNo() + "_"));
            assertTrue(exported.contains("RDM-1"));
            assertTrue(exported.contains(",350,MATCHED,CONFIRMED"));
            assertFalse(exported.contains("RIGHT-1"));
            assertEquals(2, exported.split("\\r\\n").length);
            assertEquals(5, jdbc.queryForObject("SELECT COUNT(*) FROM qh_operation_log "
                    + "WHERE business_type IN ('SETTLEMENT_BATCH','RECON_BATCH')", Integer.class));

            FilePair empty = emptyFile();
            ReconciliationImportResult emptyImport = importer.importFile(
                    empty.manifest, empty.fileName, empty.csv, 100);
            ReconciliationMatchSummary emptyMatch = new ReconciliationMatchingService(
                    matchingTransactions, matchingCompletion).match(
                    jdbc.queryForObject("SELECT id FROM qh_recon_batch "
                            + "WHERE batch_no='POSB20260907099'", Long.class), 10);
            assertEquals(0, emptyMatch.matchedRows());
            long emptyVersion = jdbc.queryForObject("SELECT version FROM qh_recon_batch "
                    + "WHERE batch_no='POSB20260907099'", Long.class);
            SettlementGenerationResult noSettlement = generation.generate(
                    emptyImport.reconBatchNo(), new SettlementGenerateCommand(emptyVersion,
                            "合法空文件无加盟补贴", "FIN-008", "settlement-generate-empty"));
            assertEquals(SettlementGenerationOutcome.NO_SETTLEMENT_REQUIRED,
                    noSettlement.outcome());
            assertEquals(1, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM qh_settlement_batch", Integer.class));

            try (Connection connection = DriverManager.getConnection(url, USER, PASSWORD)) {
                executeScript(connection,
                        "db/qinghe/rollback/R008__drop_reconciliation_import_support.sql");
                assertEquals(29, tableCount(connection));
            }
        } finally {
            dropDatabase(database);
        }
    }

    private static long batchId(JdbcTemplate jdbc) {
        return jdbc.queryForObject("SELECT id FROM qh_recon_batch "
                + "WHERE batch_no='POSB20260908099'", Long.class);
    }

    private static void seed(JdbcTemplate jdbc, RightCodeProtector protector) {
        jdbc.update("INSERT INTO qh_store (id,external_store_code,name,ownership_type,status,"
                        + "source_version,version,created_at,updated_at) VALUES "
                        + "(1,'QH001','直营店','DIRECT','ACTIVE','v1',0,?,?),"
                        + "(2,'QH006','加盟店','FRANCHISE','ACTIVE','v1',0,?,?)",
                NOW, NOW, NOW, NOW);
        jdbc.update("INSERT INTO qh_benefit_template (id,template_no,type,title,rules_snapshot,"
                        + "validity_type,validity_value,status,version,created_at,updated_at) "
                        + "VALUES (1,'TPL-1','FREE_PRODUCT','免费饮品',CAST('{}' AS JSON),"
                        + "'RELATIVE_DAYS',7,'ACTIVE',0,?,?)", NOW, NOW);
        jdbc.update("INSERT INTO qh_campaign (id,campaign_no,template_id,name,status,begin_at,end_at,"
                        + "member_claim_limit,franchise_subsidy_fen,rule_version,version,created_by,"
                        + "created_at,updated_at) VALUES (10,'CAM-1',1,'对账活动','ACTIVE',?,?,1,350,"
                        + "1,0,'MKT',?,?)", NOW.minusDays(2), NOW.plusDays(2), NOW, NOW);
        for (int index = 1; index <= 6; index++) {
            long memberId = 20L + index;
            long claimId = 30L + index;
            long entitlementId = 40L + index;
            jdbc.update("INSERT INTO qh_member_mapping (id,external_member_no,platform_user_id,"
                            + "status_snapshot,version,created_at,updated_at) VALUES (?,? ,?,'ACTIVE',0,?,?)",
                    memberId, "MEM-" + index, 1000L + index, NOW, NOW);
            jdbc.update("INSERT INTO qh_claim_request (id,claim_no,request_id,request_digest,"
                            + "campaign_id,member_id,claim_cycle,reservation_id,status,version,created_at,"
                            + "updated_at) VALUES (?,?,?,REPEAT('a',64),10,?,?,?,'SUCCESS',0,?,?)",
                    claimId, "CLM-" + index, "CLAIM-REQ-" + index, memberId,
                    "CYCLE-" + index, "RSV-" + index, NOW, NOW);
            jdbc.update("INSERT INTO qh_member_entitlement (id,entitlement_no,right_code_hash,"
                            + "encrypted_right_code,source_claim_id,campaign_id,member_id,status,"
                            + "valid_from,valid_until,version,created_at,updated_at) VALUES "
                            + "(?,?,?,X'01',?,10,?,'USED',?,?,0,?,?)", entitlementId,
                    "ENT-" + index, protector.hash("RIGHT-" + index), claimId, memberId,
                    NOW.minusDays(2), NOW.plusDays(2), NOW, NOW);
        }
        redemption(jdbc, 101, 1, "QH006", "REDEEM-1", "RDM-1", "ORDER-1", "T1", "10:00:00", "SUCCESS");
        redemption(jdbc, 102, 2, "QH001", "REDEEM-2", "RDM-2", "ORDER-2", "T2", "10:10:00", "SUCCESS");
        redemption(jdbc, 103, 3, "QH006", "REDEEM-3", "RDM-3", "ORDER-3", "T3", "10:20:00", "REVERSED");
        redemption(jdbc, 104, 4, "QH006", "REDEEM-4", "RDM-4", "ORDER-4", "T4", "11:00:00", "SUCCESS");
        redemption(jdbc, 105, 5, "QH006", "REDEEM-5", "RDM-5", "ORDER-5", "T5", "11:10:00", "SUCCESS");
        redemption(jdbc, 106, 6, "QH001", "REDEEM-6", "RDM-6", "ORDER-6", "T6", "11:20:00", "SUCCESS");
        jdbc.update("INSERT INTO qh_redemption_reversal (id,reversal_no,pos_client_id,pos_request_no,"
                        + "request_digest,redemption_id,status,reason_code,operator_no,occurred_at,"
                        + "first_processed_at,created_at,updated_at) VALUES "
                        + "(201,'REV-3','POS-QH006','REVERSE-3',REPEAT('c',64),103,'SUCCESS',"
                        + "'POS_ORDER_CANCELLED','OP-3','2026-09-08 10:30:00',"
                        + "'2026-09-08 10:30:00','2026-09-08 10:30:00','2026-09-08 10:30:00')");
        candidate(jdbc, 301, 101, "UNRECONCILED");
        candidate(jdbc, 303, 103, "CANCELLED");
        candidate(jdbc, 304, 104, "UNRECONCILED");
        candidate(jdbc, 305, 105, "UNRECONCILED");
    }

    private static void redemption(JdbcTemplate jdbc, long id, int entitlementIndex,
                                   String storeCode, String requestNo, String redemptionNo,
                                   String orderNo, String terminal, String time, String status) {
        long storeId = "QH001".equals(storeCode) ? 1 : 2;
        jdbc.update("INSERT INTO qh_redemption (id,redemption_no,pos_client_id,pos_request_no,"
                        + "request_digest,pos_order_no,terminal_no,operator_no,entitlement_id,store_id,"
                        + "status,occurred_at,first_processed_at,version,created_at,updated_at) VALUES "
                        + "(?,?,?, ?,REPEAT('b',64),?,?, 'OP',?,?,?,CONCAT('2026-09-08 ',?),"
                        + "CONCAT('2026-09-08 ',?),0,?,?)", id, redemptionNo, "POS-" + storeCode,
                requestNo, orderNo, terminal, 40L + entitlementIndex, storeId, status,
                time, time, NOW, NOW);
    }

    private static void candidate(JdbcTemplate jdbc, long id, long redemptionId, String status) {
        jdbc.update("INSERT INTO qh_subsidy_candidate (id,redemption_id,campaign_id,store_id,"
                        + "subsidy_fen,status,snapshot_version,created_at,updated_at) "
                        + "VALUES (?, ?,10,2,350,?,1,?,?)", id, redemptionId, status, NOW, NOW);
    }

    private static FilePair file() throws Exception {
        String batch = "POSB20260908099";
        String fileName = "POS_20260908_" + batch + ".csv";
        String header = "batch_no,business_date,store_code,terminal_no,pos_order_no,pos_request_no,"
                + "platform_redemption_no,right_code,operation_type,operation_status,occurred_at\r\n";
        String csv = header
                + row(batch,"QH006","T1","ORDER-1","REDEEM-1","RDM-1","RIGHT-1","REDEEM","SUCCESS","10:00:00")
                + row(batch,"QH006","T1","ORDER-1","REDEEM-1","RDM-1","RIGHT-1","REDEEM","SUCCESS","10:00:00")
                + row(batch,"QH001","T2","ORDER-2","REDEEM-2","RDM-2","RIGHT-2","REDEEM","SUCCESS","10:10:00")
                + row(batch,"QH006","T3","ORDER-3","REDEEM-3","RDM-3","RIGHT-3","REDEEM","SUCCESS","10:20:00")
                + row(batch,"QH006","T3","ORDER-3","REVERSE-3","RDM-3","RIGHT-3","REVERSE","SUCCESS","10:30:00")
                + row(batch,"QH006","T5","ORDER-5","REDEEM-5","RDM-5","RIGHT-5","REDEEM","FAILED","11:10:00")
                + row(batch,"QH001","T6","WRONG-ORDER","REDEEM-6","RDM-6","RIGHT-6","REDEEM","SUCCESS","11:20:00")
                + row(batch,"QH006","TX","ORDER-X","REDEEM-X","","RIGHT-X","REDEEM","SUCCESS","11:30:00");
        byte[] csvBytes = csv.getBytes(StandardCharsets.UTF_8);
        String manifest = "{\"provider\":\"MOCK_POS_VENDOR\",\"batchNo\":\"" + batch
                + "\",\"businessDate\":\"2026-09-08\",\"schemaVersion\":\"1.0\","
                + "\"fileName\":\"" + fileName + "\",\"rowCount\":8,"
                + "\"checksumAlgorithm\":\"SHA-256\",\"checksum\":\"" + sha256(csvBytes)
                + "\",\"generatedAt\":\"2026-09-09T02:00:05+08:00\","
                + "\"correctionOfBatchNo\":null}";
        return new FilePair(manifest.getBytes(StandardCharsets.UTF_8), fileName, csvBytes);
    }

    private static FilePair emptyFile() throws Exception {
        String batch = "POSB20260907099";
        String fileName = "POS_20260907_" + batch + ".csv";
        byte[] csv = ("batch_no,business_date,store_code,terminal_no,pos_order_no,pos_request_no,"
                + "platform_redemption_no,right_code,operation_type,operation_status,occurred_at\r\n")
                .getBytes(StandardCharsets.UTF_8);
        String manifest = "{\"provider\":\"MOCK_POS_VENDOR\",\"batchNo\":\"" + batch
                + "\",\"businessDate\":\"2026-09-07\",\"schemaVersion\":\"1.0\","
                + "\"fileName\":\"" + fileName + "\",\"rowCount\":0,"
                + "\"checksumAlgorithm\":\"SHA-256\",\"checksum\":\"" + sha256(csv)
                + "\",\"generatedAt\":\"2026-09-08T02:00:05+08:00\","
                + "\"correctionOfBatchNo\":null}";
        return new FilePair(manifest.getBytes(StandardCharsets.UTF_8), fileName, csv);
    }

    private static String row(String batch, String store, String terminal, String order,
                              String request, String redemption, String right, String type,
                              String status, String time) {
        return String.join(",", batch, "2026-09-08", store, terminal, order, request,
                redemption, right, type, status, "2026-09-08T" + time + "+08:00") + "\r\n";
    }

    private static ReconciliationImportService importService(
            ReconciliationBatchRepository repository, PlatformTransactionManager manager,
            BusinessClock clock, RightCodeProtector protector) {
        return new ReconciliationImportService(new ReconciliationFileParser(new ObjectMapper()),
                transactional(new ReconciliationRegistrationTransactionService(repository,
                        new BusinessIdGenerator(clock), clock), manager),
                transactional(new ReconciliationChunkTransactionService(
                        repository, protector, clock), manager),
                transactional(new ReconciliationChunkFailureTransactionService(
                        repository, clock), manager),
                transactional(new ReconciliationCompletionTransactionService(
                        repository, clock), manager), repository);
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
            default: throw new IllegalArgumentException();
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
        try (Connection c = DriverManager.getConnection(HOST_URL, USER, PASSWORD);
             Statement s = c.createStatement()) { s.execute(sql); }
    }
    private static void executeScript(Connection connection, String name) throws Exception {
        for (String part : resource(name).split(";")) if (!part.trim().isEmpty()) {
            try (Statement statement = connection.createStatement()) {
                statement.execute(part.trim());
            }
        }
    }
    private static int tableCount(Connection connection) throws SQLException {
        int count = 0;
        try (ResultSet tables = connection.getMetaData().getTables(connection.getCatalog(), null,
                "qh\\_%", new String[]{"TABLE"})) { while (tables.next()) count++; }
        return count;
    }
    private static String resource(String name) throws IOException {
        try (InputStream input = QingheWp08MatchingPersistenceIT.class.getClassLoader()
                .getResourceAsStream(name)) {
            if (input == null) throw new IOException("resource not found: " + name);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[16384]; int count;
            while ((count = input.read(buffer)) >= 0) output.write(buffer, 0, count);
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
    }
    private static String credential(String property, String environment) {
        String value = System.getProperty(property);
        if (value != null && !value.trim().isEmpty()) return value;
        value = System.getenv(environment); return value == null ? "" : value;
    }
    private static final class FilePair {
        private final byte[] manifest; private final String fileName; private final byte[] csv;
        private FilePair(byte[] manifest, String fileName, byte[] csv) {
            this.manifest = manifest; this.fileName = fileName; this.csv = csv;
        }
    }
}
