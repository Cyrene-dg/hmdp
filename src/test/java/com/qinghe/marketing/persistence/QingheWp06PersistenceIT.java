package com.qinghe.marketing.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qinghe.marketing.campaign.BenefitTemplateSnapshotCodec;
import com.qinghe.marketing.campaign.JdbcCampaignRepository;
import com.qinghe.marketing.entitlement.AesGcmRightCodeProtector;
import com.qinghe.marketing.entitlement.EntitlementStatus;
import com.qinghe.marketing.identity.PosAuthenticatedStore;
import com.qinghe.marketing.redemption.CampaignStorePolicy;
import com.qinghe.marketing.redemption.JdbcRedemptionRepository;
import com.qinghe.marketing.redemption.PosRedemptionCommand;
import com.qinghe.marketing.redemption.PosRedemptionService;
import com.qinghe.marketing.redemption.PosRequestStatus;
import com.qinghe.marketing.redemption.PosVerificationCommand;
import com.qinghe.marketing.redemption.PosVerificationService;
import com.qinghe.marketing.redemption.RedemptionResult;
import com.qinghe.marketing.redemption.RedemptionTransactionService;
import com.qinghe.marketing.shared.clock.BusinessClock;
import com.qinghe.marketing.shared.error.QingheBusinessException;
import com.qinghe.marketing.shared.error.QingheErrorCode;
import com.qinghe.marketing.shared.id.BusinessIdGenerator;
import com.qinghe.marketing.shared.id.BusinessIdType;
import com.qinghe.marketing.store.StoreOwnershipType;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Real MySQL WP-06 probe for row locking, recoverable idempotency and subsidy atomicity. */
class QingheWp06PersistenceIT {

    private static final String HOST_URL = System.getProperty("qinghe.it.mysql.url",
            "jdbc:mysql://127.0.0.1:3306/?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true");
    private static final String USER = System.getProperty("qinghe.it.mysql.user", "root");
    private static final String PASSWORD = credential("qinghe.it.mysql.password", "QINGHE_IT_MYSQL_PASSWORD");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 9, 16, 0);

    @Test
    void shouldRedeemExactlyOnceAndCreateOnlyFranchiseSubsidyInSameTransaction() throws Exception {
        assertTrue(!PASSWORD.isEmpty(), "Set QINGHE_IT_MYSQL_PASSWORD for the disposable database probe");
        String database = "qh_wp06_" + UUID.randomUUID().toString().replace("-", "")
                .substring(0, 12).toLowerCase(Locale.ROOT);
        if (!database.matches("qh_wp06_[a-f0-9]{12}")) {
            throw new IllegalStateException("unsafe temporary database name");
        }

        createDatabase(database);
        try {
            String databaseUrl = HOST_URL.replace("/?", "/" + database + "?");
            try (Connection connection = DriverManager.getConnection(databaseUrl, USER, PASSWORD)) {
                executeScript(connection, "db/qinghe/migration/V001__create_qinghe_core.sql");
                executeScript(connection, "db/qinghe/migration/V002__create_identity_and_store_support.sql");
                executeScript(connection, "db/qinghe/migration/V003__add_campaign_publication_and_reviews.sql");
                executeScript(connection, "db/qinghe/migration/V004__add_claim_outbox_delivery_support.sql");
                executeScript(connection, "db/qinghe/migration/V005__add_entitlement_issue_support.sql");
                executeScript(connection, "db/qinghe/migration/V006__add_pos_redemption_support.sql");
                assertEquals(28, qingheTableCount(connection));
            }

            DriverManagerDataSource dataSource = new DriverManagerDataSource(databaseUrl, USER, PASSWORD);
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            TransactionTemplate transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
            String key = Base64.getEncoder().encodeToString(
                    "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8));
            AesGcmRightCodeProtector protector = new AesGcmRightCodeProtector(key);
            seed(jdbc, protector);
            JdbcRedemptionRepository repository = new JdbcRedemptionRepository(jdbc,
                    new BenefitTemplateSnapshotCodec(new ObjectMapper()));
            CampaignStorePolicy policy = new CampaignStorePolicy(new JdbcCampaignRepository(jdbc));
            BusinessClock clock = () -> Instant.parse("2026-09-09T08:00:00Z");
            BusinessIdGenerator ids = mock(BusinessIdGenerator.class);
            when(ids.next(BusinessIdType.REDEMPTION))
                    .thenReturn("RDM-DIRECT", "RDM-FRANCHISE", "RDM-CONCURRENT");
            RedemptionTransactionService transactional = new RedemptionTransactionService(
                    repository, protector, policy, ids, clock);
            PosRedemptionService service = new PosRedemptionService(transactional, repository);
            PosVerificationService verification = new PosVerificationService(
                    repository, protector, policy, clock);
            PosAuthenticatedStore direct = new PosAuthenticatedStore(
                    1L, "S001", StoreOwnershipType.DIRECT, "POS-DIRECT");
            PosAuthenticatedStore franchise = new PosAuthenticatedStore(
                    2L, "S002", StoreOwnershipType.FRANCHISE, "POS-FRANCHISE");

            assertEquals("ENT-DIRECT", verification.verify(direct,
                    verifyCommand("VERIFY-0001", "S001", "RIGHT-DIRECT")).rightNo());
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM qh_redemption", Integer.class));

            RedemptionResult first = transaction.execute(status -> service.redeem(direct,
                    redeemCommand("REQUEST-DIRECT-1", "ORDER-DIRECT-1", "S001", "RIGHT-DIRECT")));
            RedemptionResult replay = transaction.execute(status -> service.redeem(direct,
                    redeemCommand("REQUEST-DIRECT-1", "ORDER-DIRECT-1", "S001", "RIGHT-DIRECT")));
            assertEquals(first.redemptionNo(), replay.redemptionNo());
            assertTrue(replay.replay());
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM qh_redemption", Integer.class));
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM qh_subsidy_candidate", Integer.class));
            assertEquals(first.redemptionNo(), service.query(direct, "REQUEST-DIRECT-1").redemptionNo());

            QingheBusinessException conflict = assertThrows(QingheBusinessException.class,
                    () -> transaction.execute(status -> service.redeem(direct,
                            redeemCommand("REQUEST-DIRECT-1", "ORDER-DIFFERENT", "S001", "RIGHT-DIRECT"))));
            assertEquals(QingheErrorCode.REQUEST_CONFLICT, conflict.errorCode());
            assertEquals(first.redemptionNo(), conflict.originalRedemptionNo());

            RedemptionResult franchiseResult = transaction.execute(status -> service.redeem(franchise,
                    redeemCommand("REQUEST-FRANCHISE-1", "ORDER-FRANCHISE-1", "S002", "RIGHT-FRANCHISE")));
            assertEquals(PosRequestStatus.SUCCESS, franchiseResult.status());
            assertEquals(350L, jdbc.queryForObject(
                    "SELECT subsidy_fen FROM qh_subsidy_candidate", Long.class));
            assertEquals("UNRECONCILED", jdbc.queryForObject(
                    "SELECT status FROM qh_subsidy_candidate", String.class));
            assertEquals(7L, jdbc.queryForObject(
                    "SELECT snapshot_version FROM qh_subsidy_candidate", Long.class));

            assertConcurrentSingleConsumption(transaction, transactional, franchise);
            assertEquals(3, jdbc.queryForObject("SELECT COUNT(*) FROM qh_redemption", Integer.class));
            assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM qh_subsidy_candidate", Integer.class));
            assertEquals(1, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM qh_pos_redemption_request WHERE failure_code = 'ALREADY_REDEEMED'",
                    Integer.class));

            try (Connection connection = DriverManager.getConnection(databaseUrl, USER, PASSWORD)) {
                executeScript(connection, "db/qinghe/rollback/R006__drop_pos_redemption_support.sql");
                assertEquals(27, qingheTableCount(connection));
                executeScript(connection, "db/qinghe/rollback/R005__drop_entitlement_issue_support.sql");
                executeScript(connection, "db/qinghe/rollback/R004__drop_claim_outbox_delivery_support.sql");
                executeScript(connection, "db/qinghe/rollback/R003__drop_campaign_publication_and_reviews.sql");
                executeScript(connection, "db/qinghe/rollback/R002__drop_identity_and_store_support.sql");
                executeScript(connection, "db/qinghe/rollback/R001__drop_qinghe_core.sql");
                assertEquals(0, qingheTableCount(connection));
            }
        } finally {
            dropDatabase(database);
        }
    }

    private static void assertConcurrentSingleConsumption(TransactionTemplate transaction,
                                                          RedemptionTransactionService service,
                                                          PosAuthenticatedStore store) throws Exception {
        ExecutorService workers = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Object> left = workers.submit(() -> concurrentAttempt(transaction, service, store,
                    "REQUEST-CONCURRENT-A", "ORDER-CONCURRENT-A", ready, start));
            Future<Object> right = workers.submit(() -> concurrentAttempt(transaction, service, store,
                    "REQUEST-CONCURRENT-B", "ORDER-CONCURRENT-B", ready, start));
            assertTrue(ready.await(2, java.util.concurrent.TimeUnit.SECONDS));
            start.countDown();
            Object first = unwrap(left);
            Object second = unwrap(right);
            RedemptionResult firstResult = (RedemptionResult) first;
            RedemptionResult secondResult = (RedemptionResult) second;
            assertNotEquals(firstResult.status(), secondResult.status());
            RedemptionResult failure = firstResult.status() == PosRequestStatus.FAILED
                    ? firstResult : secondResult;
            assertEquals(QingheErrorCode.ALREADY_REDEEMED.name(), failure.failureCode());
            assertTrue(failure.originalRedemptionNo() != null);
        } finally {
            workers.shutdownNow();
        }
    }

    private static Object concurrentAttempt(TransactionTemplate transaction,
                                            RedemptionTransactionService service,
                                            PosAuthenticatedStore store, String requestNo,
                                            String orderNo, CountDownLatch ready,
                                            CountDownLatch start) {
        ready.countDown();
        try {
            start.await();
            return transaction.execute(status -> service.redeem(store,
                    redeemCommand(requestNo, orderNo, "S002", "RIGHT-CONCURRENT")));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        }
    }

    private static Object unwrap(Future<Object> future) throws Exception {
        try {
            return future.get();
        } catch (ExecutionException failed) {
            if (failed.getCause() instanceof Exception) throw (Exception) failed.getCause();
            throw failed;
        }
    }

    private static PosVerificationCommand verifyCommand(String requestNo, String storeCode,
                                                         String rightCode) {
        return new PosVerificationCommand(requestNo, storeCode, "T-1", rightCode, NOW);
    }

    private static PosRedemptionCommand redeemCommand(String requestNo, String orderNo,
                                                       String storeCode, String rightCode) {
        return new PosRedemptionCommand(requestNo, orderNo, storeCode, "T-1", "OP-1",
                rightCode, NOW);
    }

    private static void seed(JdbcTemplate jdbc, AesGcmRightCodeProtector protector) {
        jdbc.update("INSERT INTO qh_store (id, external_store_code, name, ownership_type, status, "
                        + "source_version, version, created_at, updated_at) VALUES "
                        + "(1, 'S001', '青禾直营店', 'DIRECT', 'ACTIVE', 'v1', 0, ?, ?), "
                        + "(2, 'S002', '青禾加盟店', 'FRANCHISE', 'ACTIVE', 'v1', 0, ?, ?)",
                NOW, NOW, NOW, NOW);
        jdbc.update("INSERT INTO qh_member_mapping (id, external_member_no, platform_user_id, "
                        + "status_snapshot, version, created_at, updated_at) VALUES "
                        + "(20, 'MEM-20', 200, 'ACTIVE', 0, ?, ?), "
                        + "(21, 'MEM-21', 201, 'ACTIVE', 0, ?, ?), "
                        + "(22, 'MEM-22', 202, 'ACTIVE', 0, ?, ?)",
                NOW, NOW, NOW, NOW, NOW, NOW);
        String snapshot = "{\"templateName\":\"免费饮品模板\",\"benefitType\":\"FREE_PRODUCT\","
                + "\"title\":\"免费饮品\",\"description\":null,\"productCode\":\"DRINK\","
                + "\"benefitValueFen\":null,\"validityType\":\"RELATIVE_DAYS\","
                + "\"validityValue\":7,\"validFrom\":null,\"validUntil\":null,"
                + "\"usageRules\":{\"minAmountFen\":0}}";
        jdbc.update("INSERT INTO qh_benefit_template (id, template_no, type, title, rules_snapshot, "
                        + "validity_type, validity_value, status, version, created_at, updated_at) "
                        + "VALUES (1, 'TPL-1', 'FREE_PRODUCT', '免费饮品', CAST(? AS JSON), "
                        + "'RELATIVE_DAYS', 7, 'ACTIVE', 0, ?, ?)", snapshot, NOW, NOW);
        jdbc.update("INSERT INTO qh_campaign (id, campaign_no, template_id, name, description, status, "
                        + "begin_at, end_at, member_claim_limit, franchise_subsidy_fen, rule_version, "
                        + "version, created_by, created_at, updated_at) VALUES "
                        + "(10, 'CAM-1', 1, '回馈活动', '试点', 'ACTIVE', ?, ?, 1, 350, 7, 0, 'MKT-1', ?, ?)",
                NOW.minusHours(1), NOW.plusDays(1), NOW, NOW);
        jdbc.update("INSERT INTO qh_campaign_store (campaign_id, store_id, store_type, subsidy_fen, "
                        + "participation_status, rule_version, created_at, updated_at) VALUES "
                        + "(10, 1, 'DIRECT', 0, 'ACTIVE', 7, ?, ?), "
                        + "(10, 2, 'FRANCHISE', 350, 'ACTIVE', 7, ?, ?)",
                NOW, NOW, NOW, NOW);
        jdbc.update("INSERT INTO qh_campaign_publication_snapshot (campaign_id, rule_version, "
                        + "template_snapshot, claim_begin_at, claim_end_at, member_claim_limit, "
                        + "initial_stock, franchise_subsidy_fen, published_by, published_at) VALUES "
                        + "(10, 7, CAST(? AS JSON), ?, ?, 1, 3, 350, 'admin', ?)",
                snapshot, NOW.minusHours(1), NOW.plusDays(1), NOW.minusHours(1));
        insertClaimAndEntitlement(jdbc, protector, 30L, 20L, "ENT-DIRECT", "RIGHT-DIRECT");
        insertClaimAndEntitlement(jdbc, protector, 31L, 21L, "ENT-FRANCHISE", "RIGHT-FRANCHISE");
        insertClaimAndEntitlement(jdbc, protector, 32L, 22L, "ENT-CONCURRENT", "RIGHT-CONCURRENT");
    }

    private static void insertClaimAndEntitlement(JdbcTemplate jdbc,
                                                  AesGcmRightCodeProtector protector,
                                                  long claimId, long memberId,
                                                  String entitlementNo, String rightCode) {
        String claimNo = "CLM-" + claimId;
        jdbc.update("INSERT INTO qh_claim_request (id, claim_no, request_id, request_digest, campaign_id, "
                        + "member_id, claim_cycle, reservation_id, status, failure_code, version, created_at, "
                        + "updated_at) VALUES (?, ?, ?, ?, 10, ?, ?, ?, 'SUCCESS', NULL, 0, ?, ?)",
                claimId, claimNo, "CLAIM-REQUEST-" + claimId, repeat('a', 64), memberId,
                "CAMPAIGN:10:" + memberId, "RSV-" + claimId, NOW, NOW);
        jdbc.update("INSERT INTO qh_member_entitlement (entitlement_no, right_code_hash, "
                        + "encrypted_right_code, source_claim_id, campaign_id, member_id, status, "
                        + "valid_from, valid_until, version, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, 10, ?, 'AVAILABLE', ?, ?, 0, ?, ?)",
                entitlementNo, protector.hash(rightCode), new byte[]{1, 2, 3}, claimId, memberId,
                NOW.minusDays(1), NOW.plusDays(7), NOW, NOW);
    }

    private static void createDatabase(String database) throws SQLException {
        try (Connection host = DriverManager.getConnection(HOST_URL, USER, PASSWORD);
             Statement statement = host.createStatement()) {
            statement.execute("CREATE DATABASE " + database
                    + " CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
        }
    }

    private static void dropDatabase(String database) throws SQLException {
        try (Connection host = DriverManager.getConnection(HOST_URL, USER, PASSWORD);
             Statement statement = host.createStatement()) {
            statement.execute("DROP DATABASE " + database);
        }
    }

    private static void executeScript(Connection connection, String resource) throws Exception {
        String sql = resource(resource);
        for (String part : sql.split(";")) {
            String statementText = part.trim();
            if (!statementText.isEmpty()) {
                try (Statement statement = connection.createStatement()) {
                    statement.execute(statementText);
                }
            }
        }
    }

    private static int qingheTableCount(Connection connection) throws SQLException {
        int count = 0;
        try (ResultSet tables = connection.getMetaData().getTables(
                connection.getCatalog(), null, "qh\\_%", new String[]{"TABLE"})) {
            while (tables.next()) count++;
        }
        return count;
    }

    private static String resource(String name) throws IOException {
        try (InputStream input = QingheWp06PersistenceIT.class.getClassLoader()
                .getResourceAsStream(name)) {
            if (input == null) throw new IOException("resource not found: " + name);
            byte[] bytes = new byte[16384];
            int count;
            StringBuilder result = new StringBuilder();
            while ((count = input.read(bytes)) >= 0) {
                result.append(new String(bytes, 0, count, StandardCharsets.UTF_8));
            }
            return result.toString();
        }
    }

    private static String repeat(char value, int count) {
        StringBuilder result = new StringBuilder(count);
        for (int index = 0; index < count; index++) result.append(value);
        return result.toString();
    }

    private static String credential(String propertyName, String environmentName) {
        String propertyValue = System.getProperty(propertyName);
        if (propertyValue != null && !propertyValue.trim().isEmpty()) return propertyValue;
        String environmentValue = System.getenv(environmentName);
        return environmentValue == null ? "" : environmentValue;
    }
}
