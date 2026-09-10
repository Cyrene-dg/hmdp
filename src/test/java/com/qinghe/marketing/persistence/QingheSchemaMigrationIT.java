package com.qinghe.marketing.persistence;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Real MySQL probe. Maven Surefire does not select *IT by default; run explicitly with
 * -Dtest=QingheSchemaMigrationIT. It only creates and drops a uniquely named qh_wp01_* database.
 */
class QingheSchemaMigrationIT {

    private static final String HOST_URL = System.getProperty("qinghe.it.mysql.url",
            "jdbc:mysql://127.0.0.1:3306/?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true");
    private static final String USER = System.getProperty("qinghe.it.mysql.user", "root");
    private static final String PASSWORD = credential("qinghe.it.mysql.password", "QINGHE_IT_MYSQL_PASSWORD");

    @Test
    void shouldApplyAndRollbackV001OnDisposableDatabase() throws Exception {
        assertFalse(PASSWORD.isEmpty(),
                "Set -Dqinghe.it.mysql.password or QINGHE_IT_MYSQL_PASSWORD for the disposable database probe");
        String database = "qh_wp01_" + UUID.randomUUID().toString().replace("-", "")
                .substring(0, 12).toLowerCase(Locale.ROOT);
        if (!database.matches("qh_wp01_[a-f0-9]{12}")) {
            throw new IllegalStateException("unsafe temporary database name");
        }

        try (Connection host = DriverManager.getConnection(HOST_URL, USER, PASSWORD);
             Statement statement = host.createStatement()) {
            statement.execute("CREATE DATABASE " + database
                    + " CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
        }

        try {
            String databaseUrl = HOST_URL.replace("/?", "/" + database + "?");
            try (Connection connection = DriverManager.getConnection(databaseUrl, USER, PASSWORD)) {
                executeScript(connection, "db/qinghe/migration/V001__create_qinghe_core.sql");
                assertEquals(18, qingheTableCount(connection));
                executeScript(connection, "db/qinghe/rollback/R001__drop_qinghe_core.sql");
                assertEquals(0, qingheTableCount(connection));
            }
        } finally {
            try (Connection host = DriverManager.getConnection(HOST_URL, USER, PASSWORD);
                 Statement statement = host.createStatement()) {
                statement.execute("DROP DATABASE " + database);
            }
        }
    }

    @Test
    void shouldApplyAndRollbackCompleteQingheSchemaInReleaseOrder() throws Exception {
        assertFalse(PASSWORD.isEmpty(),
                "Set -Dqinghe.it.mysql.password or QINGHE_IT_MYSQL_PASSWORD for the release rehearsal");
        String database = "qh_wp10_release_" + UUID.randomUUID().toString().replace("-", "")
                .substring(0, 12).toLowerCase(Locale.ROOT);
        if (!database.matches("qh_wp10_release_[a-f0-9]{12}")) {
            throw new IllegalStateException("unsafe temporary database name");
        }
        try (Connection host = DriverManager.getConnection(HOST_URL, USER, PASSWORD);
             Statement statement = host.createStatement()) {
            statement.execute("CREATE DATABASE " + database
                    + " CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
        }

        try {
            String databaseUrl = HOST_URL.replace("/?", "/" + database + "?");
            try (Connection connection = DriverManager.getConnection(databaseUrl, USER, PASSWORD)) {
                String[] migrations = new String[]{
                        "db/qinghe/migration/V001__create_qinghe_core.sql",
                        "db/qinghe/migration/V002__create_identity_and_store_support.sql",
                        "db/qinghe/migration/V003__add_campaign_publication_and_reviews.sql",
                        "db/qinghe/migration/V004__add_claim_outbox_delivery_support.sql",
                        "db/qinghe/migration/V005__add_entitlement_issue_support.sql",
                        "db/qinghe/migration/V006__add_pos_redemption_support.sql",
                        "db/qinghe/migration/V007__add_reversal_idempotency_and_audit.sql",
                        "db/qinghe/migration/V008__add_reconciliation_import_support.sql"};
                for (String migration : migrations) {
                    executeScript(connection, migration);
                }
                assertEquals(33, qingheTableCount(connection));

                String[] rollbacks = new String[]{
                        "db/qinghe/rollback/R008__drop_reconciliation_import_support.sql",
                        "db/qinghe/rollback/R007__drop_reversal_idempotency_and_audit.sql",
                        "db/qinghe/rollback/R006__drop_pos_redemption_support.sql",
                        "db/qinghe/rollback/R005__drop_entitlement_issue_support.sql",
                        "db/qinghe/rollback/R004__drop_claim_outbox_delivery_support.sql",
                        "db/qinghe/rollback/R003__drop_campaign_publication_and_reviews.sql",
                        "db/qinghe/rollback/R002__drop_identity_and_store_support.sql",
                        "db/qinghe/rollback/R001__drop_qinghe_core.sql"};
                int[] expectedTables = new int[]{29, 28, 27, 26, 25, 23, 18, 0};
                for (int index = 0; index < rollbacks.length; index++) {
                    executeScript(connection, rollbacks[index]);
                    assertEquals(expectedTables[index], qingheTableCount(connection),
                            "unexpected table count after " + rollbacks[index]);
                }
            }
        } finally {
            try (Connection host = DriverManager.getConnection(HOST_URL, USER, PASSWORD);
                 Statement statement = host.createStatement()) {
                statement.execute("DROP DATABASE IF EXISTS " + database);
            }
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
