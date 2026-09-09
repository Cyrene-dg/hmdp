package com.qinghe.marketing.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qinghe.marketing.claim.ClaimAcceptanceTransactionService;
import com.qinghe.marketing.claim.ClaimRequest;
import com.qinghe.marketing.claim.JdbcClaimRequestRepository;
import com.qinghe.marketing.claim.JdbcOutboxEventRepository;
import com.qinghe.marketing.claim.LeasedOutboxEvent;
import com.qinghe.marketing.claim.OutboxEvent;
import com.qinghe.marketing.claim.OutboxEventRepository;
import com.qinghe.marketing.claim.OutboxStatus;
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
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Real MySQL WP-04 probe. It creates and removes one qh_wp04_* database. */
class QingheWp04PersistenceIT {

    private static final String HOST_URL = System.getProperty("qinghe.it.mysql.url",
            "jdbc:mysql://127.0.0.1:3306/?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true");
    private static final String USER = System.getProperty("qinghe.it.mysql.user", "root");
    private static final String PASSWORD = credential("qinghe.it.mysql.password", "QINGHE_IT_MYSQL_PASSWORD");

    @Test
    void shouldPersistClaimWithOutboxAndLeaseDeliveryUntilPublishedThenRollbackV004() throws Exception {
        assertTrue(!PASSWORD.isEmpty(), "Set QINGHE_IT_MYSQL_PASSWORD for the disposable database probe");
        String database = "qh_wp04_" + UUID.randomUUID().toString().replace("-", "")
                .substring(0, 12).toLowerCase(Locale.ROOT);
        if (!database.matches("qh_wp04_[a-f0-9]{12}")) {
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
                assertEquals(26, qingheTableCount(connection));
            }

            DriverManagerDataSource dataSource = new DriverManagerDataSource(databaseUrl, USER, PASSWORD);
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            TransactionTemplate transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
            seedClaimParents(jdbc);
            verifyClaimAndOutboxTransaction(jdbc, transaction);
            verifyOutboxLeaseRetryAndAck(jdbc, transaction);
            verifyClaimRollsBackWhenOutboxInsertFails(jdbc, transaction);

            try (Connection connection = DriverManager.getConnection(databaseUrl, USER, PASSWORD)) {
                executeScript(connection, "db/qinghe/rollback/R004__drop_claim_outbox_delivery_support.sql");
                assertEquals(25, qingheTableCount(connection));
                executeScript(connection, "db/qinghe/rollback/R003__drop_campaign_publication_and_reviews.sql");
                executeScript(connection, "db/qinghe/rollback/R002__drop_identity_and_store_support.sql");
                executeScript(connection, "db/qinghe/rollback/R001__drop_qinghe_core.sql");
                assertEquals(0, qingheTableCount(connection));
            }
        } finally {
            dropDatabase(database);
        }
    }

    private void seedClaimParents(JdbcTemplate jdbc) {
        LocalDateTime now = LocalDateTime.of(2026, 9, 9, 16, 0);
        jdbc.update("INSERT INTO qh_member_mapping (id, external_member_no, platform_user_id, "
                        + "status_snapshot, version, created_at, updated_at) VALUES (20, 'MEM-20', 200, "
                        + "'ACTIVE', 0, ?, ?)", now, now);
        jdbc.update("INSERT INTO qh_member_mapping (id, external_member_no, platform_user_id, "
                        + "status_snapshot, version, created_at, updated_at) VALUES (21, 'MEM-21', 201, "
                        + "'ACTIVE', 0, ?, ?)", now, now);
        jdbc.update("INSERT INTO qh_benefit_template (id, template_no, type, title, rules_snapshot, "
                        + "validity_type, validity_value, status, version, created_at, updated_at) "
                        + "VALUES (1, 'TPL-1', 'FREE_PRODUCT', '免费饮品', CAST('{}' AS JSON), "
                        + "'RELATIVE_DAYS', 7, 'ACTIVE', 0, ?, ?)", now, now);
        jdbc.update("INSERT INTO qh_campaign (id, campaign_no, template_id, name, description, status, "
                        + "begin_at, end_at, member_claim_limit, franchise_subsidy_fen, rule_version, "
                        + "version, created_by, created_at, updated_at) VALUES (10, 'CAM-1', 1, '回馈活动', "
                        + "'试点', 'ACTIVE', ?, ?, 1, 300, 1, 0, 'MKT-1', ?, ?)",
                now.minusHours(1), now.plusDays(1), now, now);
    }

    private void verifyClaimAndOutboxTransaction(JdbcTemplate jdbc, TransactionTemplate transaction) {
        JdbcClaimRequestRepository claims = new JdbcClaimRequestRepository(jdbc);
        JdbcOutboxEventRepository outbox = new JdbcOutboxEventRepository(jdbc);
        ClaimAcceptanceTransactionService service = new ClaimAcceptanceTransactionService(
                claims, outbox, new ObjectMapper());
        LocalDateTime now = LocalDateTime.of(2026, 9, 9, 16, 0);

        ClaimRequest accepted = transaction.execute(status -> service.accept(
                10L, 20L, "request-001", repeat('a', 64), "reservation-1",
                "CLM-1", "EVT-1", now));

        assertNotNull(accepted);
        assertEquals("PROCESSING", jdbc.queryForObject(
                "SELECT status FROM qh_claim_request WHERE claim_no = 'CLM-1'", String.class));

        assertEquals("NEW", jdbc.queryForObject(
                "SELECT status FROM qh_outbox_event WHERE event_id = 'EVT-1'", String.class));
        assertEquals("CLM-1", jdbc.queryForObject(
                "SELECT JSON_UNQUOTE(JSON_EXTRACT(payload, '$.claimNo')) "
                        + "FROM qh_outbox_event WHERE event_id = 'EVT-1'", String.class));
    }

    private void verifyOutboxLeaseRetryAndAck(JdbcTemplate jdbc, TransactionTemplate transaction) {
        JdbcOutboxEventRepository outbox = new JdbcOutboxEventRepository(jdbc);
        LocalDateTime now = LocalDateTime.of(2026, 9, 9, 16, 0);
        List<LeasedOutboxEvent> firstLease = transaction.execute(status ->
                outbox.leaseBatch("instance-a", now, now.plusSeconds(30), 10));
        List<LeasedOutboxEvent> blockedLease = transaction.execute(status ->
                outbox.leaseBatch("instance-b", now.plusSeconds(1), now.plusSeconds(31), 10));

        assertEquals(1, firstLease.size());
        assertEquals(0, blockedLease.size());
        OutboxStatus retry = transaction.execute(status -> outbox.completePublication(
                "EVT-1", "instance-a", false, "broker nack", now.plusSeconds(2), 3, now));
        assertEquals(OutboxStatus.RETRY, retry);
        assertEquals(0, transaction.execute(status -> outbox.leaseBatch(
                "instance-b", now.plusSeconds(1), now.plusSeconds(31), 10)).size());

        List<LeasedOutboxEvent> secondLease = transaction.execute(status -> outbox.leaseBatch(
                "instance-b", now.plusSeconds(2), now.plusSeconds(32), 10));
        assertEquals(1, secondLease.size());
        OutboxStatus published = transaction.execute(status -> outbox.completePublication(
                "EVT-1", "instance-b", true, null, now.plusSeconds(3), 3, now.plusSeconds(2)));

        assertEquals(OutboxStatus.PUBLISHED, published);
        assertEquals(2, jdbc.queryForObject(
                "SELECT COUNT(*) FROM qh_outbox_delivery_attempt WHERE event_id = 'EVT-1'", Integer.class));
        assertEquals("PUBLISHED", jdbc.queryForObject(
                "SELECT status FROM qh_outbox_event WHERE event_id = 'EVT-1'", String.class));
        assertNotNull(jdbc.queryForObject(
                "SELECT published_at FROM qh_outbox_event WHERE event_id = 'EVT-1'", LocalDateTime.class));
        assertEquals("PROCESSING", jdbc.queryForObject(
                "SELECT status FROM qh_claim_request WHERE claim_no = 'CLM-1'", String.class));

        outbox.insert(new OutboxEvent("EVT-DEAD", "CLAIM_REQUEST", "CLM-DEAD",
                "CLAIM_ACCEPTED", 1L, "{}", OutboxStatus.NEW, now.plusSeconds(4)));
        List<LeasedOutboxEvent> deadLease = transaction.execute(status -> outbox.leaseBatch(
                "instance-c", now.plusSeconds(4), now.plusSeconds(34), 10));
        assertEquals(1, deadLease.size());
        OutboxStatus dead = transaction.execute(status -> outbox.completePublication(
                "EVT-DEAD", "instance-c", false, "permanent nack",
                now.plusSeconds(5), 1, now.plusSeconds(4)));
        assertEquals(OutboxStatus.DEAD, dead);
        assertEquals("DEAD", jdbc.queryForObject(
                "SELECT status FROM qh_outbox_event WHERE event_id = 'EVT-DEAD'", String.class));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM qh_outbox_delivery_attempt WHERE event_id = 'EVT-DEAD'",
                Integer.class));
    }

    private void verifyClaimRollsBackWhenOutboxInsertFails(JdbcTemplate jdbc,
                                                            TransactionTemplate transaction) {
        JdbcClaimRequestRepository claims = new JdbcClaimRequestRepository(jdbc);
        OutboxEventRepository failingOutbox = new OutboxEventRepository() {
            @Override
            public void insert(OutboxEvent event) {
                throw new IllegalStateException("simulated outbox failure");
            }
            @Override
            public List<LeasedOutboxEvent> leaseBatch(String owner, LocalDateTime now,
                                                       LocalDateTime until, int limit) {
                throw new UnsupportedOperationException();
            }
            @Override
            public OutboxStatus completePublication(String eventId, String owner, boolean acknowledged,
                                                     String error, LocalDateTime retryAt,
                                                     int maxRetries, LocalDateTime now) {
                throw new UnsupportedOperationException();
            }
        };
        ClaimAcceptanceTransactionService service = new ClaimAcceptanceTransactionService(
                claims, failingOutbox, new ObjectMapper());
        LocalDateTime now = LocalDateTime.of(2026, 9, 9, 16, 1);

        assertThrows(IllegalStateException.class, () -> transaction.execute(status -> service.accept(
                10L, 21L, "request-rollback", repeat('b', 64), "reservation-rollback",
                "CLM-ROLLBACK", "EVT-ROLLBACK", now)));
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM qh_claim_request WHERE claim_no = 'CLM-ROLLBACK'", Integer.class));
    }

    private void createDatabase(String database) throws SQLException {
        try (Connection host = DriverManager.getConnection(HOST_URL, USER, PASSWORD);
             Statement statement = host.createStatement()) {
            statement.execute("CREATE DATABASE " + database
                    + " CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
        }
    }

    private void dropDatabase(String database) throws SQLException {
        try (Connection host = DriverManager.getConnection(HOST_URL, USER, PASSWORD);
             Statement statement = host.createStatement()) {
            statement.execute("DROP DATABASE " + database);
        }
    }

    private void executeScript(Connection connection, String resource) throws Exception {
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

    private int qingheTableCount(Connection connection) throws SQLException {
        int count = 0;
        try (ResultSet tables = connection.getMetaData().getTables(
                connection.getCatalog(), null, "qh\\_%", new String[]{"TABLE"})) {
            while (tables.next()) {
                count++;
            }
        }
        return count;
    }

    private String resource(String name) throws IOException {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(name)) {
            if (input == null) {
                throw new IOException("resource not found: " + name);
            }
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
        for (int index = 0; index < count; index++) {
            result.append(value);
        }
        return result.toString();
    }

    private static String credential(String propertyName, String environmentName) {
        String propertyValue = System.getProperty(propertyName);
        if (propertyValue != null && !propertyValue.trim().isEmpty()) {
            return propertyValue;
        }
        String environmentValue = System.getenv(environmentName);
        return environmentValue == null ? "" : environmentValue;
    }
}
