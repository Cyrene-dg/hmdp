package com.qinghe.marketing.persistence;

import com.qinghe.marketing.identity.AuthenticatedMember;
import com.qinghe.marketing.identity.JdbcMemberMappingRepository;
import com.qinghe.marketing.identity.JdbcPosNonceRepository;
import com.qinghe.marketing.identity.JdbcPlatformSessionRepository;
import com.qinghe.marketing.identity.MemberSessionService;
import com.qinghe.marketing.identity.MemberSessionTransactionService;
import com.qinghe.marketing.identity.MemberStatus;
import com.qinghe.marketing.identity.PlatformMemberIdGenerator;
import com.qinghe.marketing.identity.PlatformSessionTokenIssuer;
import com.qinghe.marketing.identity.SessionExchangeResult;
import com.qinghe.marketing.identity.VerifiedMember;
import com.qinghe.marketing.shared.clock.BusinessClock;
import com.qinghe.marketing.shared.id.BusinessIdGenerator;
import com.qinghe.marketing.store.JdbcPosCredentialRepository;
import com.qinghe.marketing.store.JdbcStoreImportRepository;
import com.qinghe.marketing.store.JdbcStoreRepository;
import com.qinghe.marketing.store.PosCredentialService;
import com.qinghe.marketing.store.StoreEligibilityService;
import com.qinghe.marketing.store.StoreImportPreview;
import com.qinghe.marketing.store.StoreImportService;
import com.qinghe.marketing.store.StoreImportStatus;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

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
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Real MySQL WP-02 probe. Run explicitly with -Dtest=QingheWp02PersistenceIT.
 * It creates and removes one uniquely named qh_wp02_* database.
 */
class QingheWp02PersistenceIT {

    private static final String HOST_URL = System.getProperty("qinghe.it.mysql.url",
            "jdbc:mysql://127.0.0.1:3306/?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true");
    private static final String USER = System.getProperty("qinghe.it.mysql.user", "root");
    private static final String PASSWORD = credential("qinghe.it.mysql.password", "QINGHE_IT_MYSQL_PASSWORD");
    private static final Instant NOW = Instant.parse("2026-09-08T07:00:00Z");

    @Test
    void shouldPersistIdentityAndStoreFlowsThenRollbackV002() throws Exception {
        assertFalse(PASSWORD.isEmpty(),
                "Set -Dqinghe.it.mysql.password or QINGHE_IT_MYSQL_PASSWORD for the disposable database probe");
        String database = "qh_wp02_" + UUID.randomUUID().toString().replace("-", "")
                .substring(0, 12).toLowerCase(Locale.ROOT);
        if (!database.matches("qh_wp02_[a-f0-9]{12}")) {
            throw new IllegalStateException("unsafe temporary database name");
        }

        createDatabase(database);
        try {
            String databaseUrl = HOST_URL.replace("/?", "/" + database + "?");
            try (Connection connection = DriverManager.getConnection(databaseUrl, USER, PASSWORD)) {
                executeScript(connection, "db/qinghe/migration/V001__create_qinghe_core.sql");
                executeScript(connection, "db/qinghe/migration/V002__create_identity_and_store_support.sql");
                assertEquals(23, qingheTableCount(connection));
            }

            DriverManagerDataSource dataSource = new DriverManagerDataSource(databaseUrl, USER, PASSWORD);
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            BusinessClock clock = () -> NOW;

            verifyIdentityPersistence(jdbc, clock);
            verifyStorePersistence(jdbc, clock);

            try (Connection connection = DriverManager.getConnection(databaseUrl, USER, PASSWORD)) {
                executeScript(connection, "db/qinghe/rollback/R002__drop_identity_and_store_support.sql");
                assertEquals(18, qingheTableCount(connection));
                executeScript(connection, "db/qinghe/rollback/R001__drop_qinghe_core.sql");
                assertEquals(0, qingheTableCount(connection));
            }
        } finally {
            dropDatabase(database);
        }
    }

    private void verifyIdentityPersistence(JdbcTemplate jdbc, BusinessClock clock) {
        JdbcMemberMappingRepository mappings = new JdbcMemberMappingRepository(jdbc);
        JdbcPlatformSessionRepository sessions = new JdbcPlatformSessionRepository(jdbc);
        PlatformSessionTokenIssuer tokenIssuer = new PlatformSessionTokenIssuer();
        MemberSessionTransactionService transactionService = new MemberSessionTransactionService(
                mappings, sessions, new PlatformMemberIdGenerator(), tokenIssuer,
                new BusinessIdGenerator(clock), clock);
        MemberSessionService service = new MemberSessionService(
                (token, requestId) -> new VerifiedMember(
                        "M100086", MemberStatus.ACTIVE, "NORMAL", NOW.plusSeconds(3600)),
                transactionService, sessions, tokenIssuer, clock, 1800L);

        SessionExchangeResult first = service.exchange("external-member-token-one", "request-member-one");
        SessionExchangeResult second = service.exchange("external-member-token-two", "request-member-two");
        AuthenticatedMember authenticated = service.authenticate(first.accessToken());

        assertEquals("M100086", authenticated.externalMemberNo());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM qh_member_mapping", Integer.class));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM qh_platform_session", Integer.class));
        assertFalse(first.accessToken().equals(second.accessToken()));
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM qh_platform_session WHERE access_token_hash = ?",
                Integer.class, first.accessToken()));
    }

    private void verifyStorePersistence(JdbcTemplate jdbc, BusinessClock clock) {
        JdbcStoreImportRepository imports = new JdbcStoreImportRepository(jdbc);
        JdbcStoreRepository stores = new JdbcStoreRepository(jdbc);
        StoreImportService importService = new StoreImportService(
                imports, stores, new BusinessIdGenerator(clock), clock);
        String csv = "source_version,store_code,store_name,ownership_type,status,pos_version\n"
                + "STORE-20260908-01,QH001,青禾中心直营店,DIRECT,ACTIVE,POS-3.2\n"
                + "STORE-20260908-01,QH006,青禾湖滨加盟店,FRANCHISE,ACTIVE,POS-3.2\n";

        StoreImportPreview preview = importService.preview(csv.getBytes(StandardCharsets.UTF_8), "operator-001");
        StoreImportPreview repeated = importService.preview(csv.getBytes(StandardCharsets.UTF_8), "operator-002");
        StoreImportPreview committed = importService.commit(preview.importNo());

        assertEquals(preview.importNo(), repeated.importNo());
        assertEquals(StoreImportStatus.COMMITTED, committed.status());
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM qh_store", Integer.class));
        assertTrue(stores.findByExternalStoreCode("QH006").orElseThrow(IllegalStateException::new).franchise());

        PosCredentialService credentials = new PosCredentialService(
                new StoreEligibilityService(stores), new JdbcPosCredentialRepository(jdbc), clock);
        credentials.activate("QH006", "pos-client-qh006", "vault://qinghe/pos/QH006", 1);
        assertEquals("vault://qinghe/pos/QH006", jdbc.queryForObject(
                "SELECT secret_reference FROM qh_pos_credential WHERE client_id = 'pos-client-qh006'",
                String.class));
        credentials.activate("QH006", "pos-client-qh006", "vault://qinghe/pos/QH006-v2", 2);
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM qh_pos_credential WHERE client_id = 'pos-client-qh006'",
                Integer.class));
        assertEquals(2, jdbc.queryForObject(
                "SELECT secret_version FROM qh_pos_credential WHERE client_id = 'pos-client-qh006'",
                Integer.class));
        assertTrue(new JdbcPosCredentialRepository(jdbc)
                .findActiveByClientId("pos-client-qh006").isPresent());

        JdbcPosNonceRepository nonces = new JdbcPosNonceRepository(jdbc);
        LocalDateTime now = LocalDateTime.ofInstant(NOW, ZoneOffset.UTC);
        assertTrue(nonces.reserve("pos-client-qh006", "nonce-persistence-01",
                now.plusMinutes(5), now));
        assertFalse(nonces.reserve("pos-client-qh006", "nonce-persistence-01",
                now.plusMinutes(5), now));
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

    private static String credential(String propertyName, String environmentName) {
        String propertyValue = System.getProperty(propertyName);
        if (propertyValue != null && !propertyValue.trim().isEmpty()) {
            return propertyValue;
        }
        String environmentValue = System.getenv(environmentName);
        return environmentValue == null ? "" : environmentValue;
    }
}
