package com.qinghe.marketing.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qinghe.marketing.campaign.BenefitTemplateSnapshotCodec;
import com.qinghe.marketing.campaign.JdbcCampaignRepository;
import com.qinghe.marketing.claim.ClaimRequestRepository;
import com.qinghe.marketing.claim.JdbcClaimRequestRepository;
import com.qinghe.marketing.entitlement.AesGcmRightCodeProtector;
import com.qinghe.marketing.entitlement.BenefitIssueCommand;
import com.qinghe.marketing.entitlement.ClaimIssueDeliveryRepository;
import com.qinghe.marketing.entitlement.ClaimIssueOutcome;
import com.qinghe.marketing.entitlement.ClaimIssueTransactionService;
import com.qinghe.marketing.entitlement.ClaimFailureTransactionService;
import com.qinghe.marketing.entitlement.CompensationClaim;
import com.qinghe.marketing.entitlement.JdbcClaimIssueDeliveryRepository;
import com.qinghe.marketing.entitlement.JdbcMemberEntitlementRepository;
import com.qinghe.marketing.entitlement.MemberEntitlementRepository;
import com.qinghe.marketing.shared.clock.BusinessClock;
import com.qinghe.marketing.shared.id.BusinessIdGenerator;
import com.qinghe.marketing.shared.id.BusinessIdType;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

/** Real MySQL WP-05 probe for entitlement issuance, idempotency and transaction rollback. */
class QingheWp05PersistenceIT {

    private static final String HOST_URL = System.getProperty("qinghe.it.mysql.url",
            "jdbc:mysql://127.0.0.1:3306/?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true");
    private static final String USER = System.getProperty("qinghe.it.mysql.user", "root");
    private static final String PASSWORD = credential("qinghe.it.mysql.password", "QINGHE_IT_MYSQL_PASSWORD");

    @Test
    void shouldIssueExactlyOnceAndRollbackEntitlementWhenSuccessGateFails() throws Exception {
        assertTrue(!PASSWORD.isEmpty(), "Set QINGHE_IT_MYSQL_PASSWORD for the disposable database probe");
        String database = "qh_wp05_" + UUID.randomUUID().toString().replace("-", "")
                .substring(0, 12).toLowerCase(Locale.ROOT);
        if (!database.matches("qh_wp05_[a-f0-9]{12}")) {
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
                assertEquals(27, qingheTableCount(connection));
            }

            DriverManagerDataSource dataSource = new DriverManagerDataSource(databaseUrl, USER, PASSWORD);
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            TransactionTemplate transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
            seed(jdbc);
            ObjectMapper mapper = new ObjectMapper();
            ClaimRequestRepository claims = new JdbcClaimRequestRepository(jdbc);
            MemberEntitlementRepository entitlements = new JdbcMemberEntitlementRepository(jdbc, mapper);
            ClaimIssueDeliveryRepository deliveries = new JdbcClaimIssueDeliveryRepository(jdbc);
            BusinessIdGenerator ids = mock(BusinessIdGenerator.class);
            when(ids.next(BusinessIdType.ENTITLEMENT)).thenReturn("ENT-1", "ENT-ROLLBACK");
            BusinessClock clock = () -> Instant.parse("2026-09-09T08:00:00Z");
            String key = Base64.getEncoder().encodeToString(
                    "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8));
            ClaimIssueTransactionService service = new ClaimIssueTransactionService(
                    claims, entitlements, deliveries, new JdbcCampaignRepository(jdbc),
                    new BenefitTemplateSnapshotCodec(mapper), new AesGcmRightCodeProtector(key), ids, clock);
            BenefitIssueCommand command = command("EVT-1", "CLM-1", "RSV-1", 20L, "request-001");

            assertEquals(ClaimIssueOutcome.ISSUED,
                    transaction.execute(status -> service.issue(command)).outcome());
            assertEquals(ClaimIssueOutcome.ALREADY_ISSUED,
                    transaction.execute(status -> service.issue(command)).outcome());
            assertEquals(1, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM qh_member_entitlement WHERE source_claim_id = 30", Integer.class));
            assertEquals("SUCCESS", jdbc.queryForObject(
                    "SELECT status FROM qh_claim_request WHERE id = 30", String.class));
            assertEquals(1, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM qh_claim_issue_delivery WHERE event_id = 'EVT-1'", Integer.class));
            String entitlementNo = jdbc.queryForObject(
                    "SELECT entitlement_no FROM qh_member_entitlement WHERE source_claim_id = 30",
                    String.class);
            assertTrue(entitlements.findViewByNoAndMemberId(entitlementNo, 20L).isPresent());
            assertTrue(!entitlements.findViewByNoAndMemberId(entitlementNo, 21L).isPresent());
            assertEquals(1L, entitlements.countByMemberId(20L, null));
            jdbc.update("UPDATE qh_member_entitlement SET valid_until = ? WHERE source_claim_id = 30",
                    LocalDateTime.of(2026, 9, 9, 15, 59));
            assertEquals(1, entitlements.expireAvailableBefore(LocalDateTime.of(2026, 9, 9, 16, 0)));
            assertEquals(0, entitlements.expireAvailableBefore(LocalDateTime.of(2026, 9, 9, 16, 0)));
            assertEquals("EXPIRED", jdbc.queryForObject(
                    "SELECT status FROM qh_member_entitlement WHERE source_claim_id = 30", String.class));
            byte[] encrypted = jdbc.queryForObject(
                    "SELECT encrypted_right_code FROM qh_member_entitlement WHERE source_claim_id = 30",
                    byte[].class);
            assertNotNull(encrypted);
            assertTrue(encrypted.length > 12);

            JdbcClaimRequestRepository failingClaims = spy(new JdbcClaimRequestRepository(jdbc));
            doReturn(false).when(failingClaims).markSuccess(eq(31L), eq(0L), any(LocalDateTime.class));
            ClaimIssueTransactionService failing = new ClaimIssueTransactionService(
                    failingClaims, entitlements, deliveries, new JdbcCampaignRepository(jdbc),
                    new BenefitTemplateSnapshotCodec(mapper), new AesGcmRightCodeProtector(key), ids, clock);
            assertThrows(IllegalStateException.class, () -> transaction.execute(status ->
                    failing.issue(command("EVT-2", "CLM-2", "RSV-2", 21L, "request-002"))));
            assertEquals(0, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM qh_member_entitlement WHERE source_claim_id = 31", Integer.class));
            assertEquals("PROCESSING", jdbc.queryForObject(
                    "SELECT status FROM qh_claim_request WHERE id = 31", String.class));

            ClaimFailureTransactionService failures = new ClaimFailureTransactionService(
                    claims, deliveries, clock);
            CompensationClaim compensating = transaction.execute(status ->
                    failures.begin("CLM-3", "ISSUE_RETRY_EXHAUSTED"));
            assertTrue(compensating.actionable());
            assertEquals("COMPENSATING", jdbc.queryForObject(
                    "SELECT status FROM qh_claim_request WHERE id = 32", String.class));
            transaction.executeWithoutResult(status -> failures.complete(
                    compensating.claim(), "EVT-3", "ISSUE_RETRY_EXHAUSTED"));
            assertEquals("FAILED", jdbc.queryForObject(
                    "SELECT status FROM qh_claim_request WHERE id = 32", String.class));
            assertEquals(ClaimIssueOutcome.IGNORED_TERMINAL,
                    transaction.execute(status -> service.issue(
                            command("EVT-3", "CLM-3", "RSV-3", 22L, "request-003"))).outcome());
            assertEquals(0, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM qh_member_entitlement WHERE source_claim_id = 32", Integer.class));

            try (Connection connection = DriverManager.getConnection(databaseUrl, USER, PASSWORD)) {
                executeScript(connection, "db/qinghe/rollback/R005__drop_entitlement_issue_support.sql");
                assertEquals(26, qingheTableCount(connection));
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

    private static BenefitIssueCommand command(String eventId, String claimNo, String reservationId,
                                                long memberId, String requestId) {
        return new BenefitIssueCommand(eventId, claimNo, reservationId, 10L, memberId, requestId);
    }

    private static void seed(JdbcTemplate jdbc) {
        LocalDateTime now = LocalDateTime.of(2026, 9, 9, 16, 0);
        jdbc.update("INSERT INTO qh_store (id, external_store_code, name, ownership_type, status, "
                        + "source_version, version, created_at, updated_at) VALUES "
                        + "(1, 'S001', '青禾一店', 'DIRECT', 'ACTIVE', 'v1', 0, ?, ?)", now, now);
        jdbc.update("INSERT INTO qh_member_mapping (id, external_member_no, platform_user_id, "
                        + "status_snapshot, version, created_at, updated_at) VALUES "
                        + "(20, 'MEM-20', 200, 'ACTIVE', 0, ?, ?), "
                        + "(21, 'MEM-21', 201, 'ACTIVE', 0, ?, ?), "
                        + "(22, 'MEM-22', 202, 'ACTIVE', 0, ?, ?)",
                now, now, now, now, now, now);
        String snapshot = "{\"templateName\":\"免费饮品模板\",\"benefitType\":\"FREE_PRODUCT\","
                + "\"title\":\"免费饮品\",\"description\":null,\"productCode\":\"DRINK\","
                + "\"benefitValueFen\":null,\"validityType\":\"RELATIVE_DAYS\","
                + "\"validityValue\":7,\"validFrom\":null,\"validUntil\":null,"
                + "\"usageRules\":{\"minAmountFen\":0}}";
        jdbc.update("INSERT INTO qh_benefit_template (id, template_no, type, title, rules_snapshot, "
                        + "validity_type, validity_value, status, version, created_at, updated_at) "
                        + "VALUES (1, 'TPL-1', 'FREE_PRODUCT', '免费饮品', CAST(? AS JSON), "
                        + "'RELATIVE_DAYS', 7, 'ACTIVE', 0, ?, ?)", snapshot, now, now);
        jdbc.update("INSERT INTO qh_campaign (id, campaign_no, template_id, name, description, status, "
                        + "begin_at, end_at, member_claim_limit, franchise_subsidy_fen, rule_version, "
                        + "version, created_by, created_at, updated_at) VALUES "
                        + "(10, 'CAM-1', 1, '回馈活动', '试点', 'ACTIVE', ?, ?, 1, 300, 1, 0, 'MKT-1', ?, ?)",
                now.minusHours(1), now.plusDays(1), now, now);
        jdbc.update("INSERT INTO qh_campaign_store (campaign_id, store_id, store_type, subsidy_fen, "
                        + "participation_status, rule_version, created_at, updated_at) VALUES "
                        + "(10, 1, 'DIRECT', 0, 'ACTIVE', 1, ?, ?)", now, now);
        jdbc.update("INSERT INTO qh_campaign_publication_snapshot (campaign_id, rule_version, "
                        + "template_snapshot, claim_begin_at, claim_end_at, member_claim_limit, "
                        + "initial_stock, franchise_subsidy_fen, published_by, published_at) VALUES "
                        + "(10, 1, CAST(? AS JSON), ?, ?, 1, 2, 300, 'admin', ?)",
                snapshot, now.minusHours(1), now.plusDays(1), now.minusHours(1));
        insertClaimAndOutbox(jdbc, 30L, "CLM-1", "request-001", 20L, "RSV-1", "EVT-1", now);
        insertClaimAndOutbox(jdbc, 31L, "CLM-2", "request-002", 21L, "RSV-2", "EVT-2", now);
        insertClaimAndOutbox(jdbc, 32L, "CLM-3", "request-003", 22L, "RSV-3", "EVT-3", now);
    }

    private static void insertClaimAndOutbox(JdbcTemplate jdbc, long id, String claimNo,
                                             String requestId, long memberId, String reservationId,
                                             String eventId, LocalDateTime now) {
        jdbc.update("INSERT INTO qh_claim_request (id, claim_no, request_id, request_digest, campaign_id, "
                        + "member_id, claim_cycle, reservation_id, status, failure_code, version, created_at, "
                        + "updated_at) VALUES (?, ?, ?, ?, 10, ?, 'CAMPAIGN:10', ?, 'PROCESSING', NULL, 0, ?, ?)",
                id, claimNo, requestId, repeat('a', 64), memberId, reservationId, now, now);
        String payload = "{\"eventId\":\"" + eventId + "\",\"claimNo\":\"" + claimNo
                + "\",\"reservationId\":\"" + reservationId + "\",\"campaignId\":10,"
                + "\"memberId\":" + memberId + ",\"requestId\":\"" + requestId + "\"}";
        jdbc.update("INSERT INTO qh_outbox_event (event_id, aggregate_type, aggregate_id, event_type, "
                        + "event_version, payload, status, retry_count, next_retry_at, lease_until, created_at, "
                        + "updated_at) VALUES (?, 'CLAIM_REQUEST', ?, 'CLAIM_ACCEPTED', 1, CAST(? AS JSON), "
                        + "'PUBLISHED', 0, ?, NULL, ?, ?)", eventId, claimNo, payload, now, now, now);
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
        try (InputStream input = QingheWp05PersistenceIT.class.getClassLoader().getResourceAsStream(name)) {
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
