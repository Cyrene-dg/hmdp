package com.qinghe.marketing.persistence;

import com.qinghe.marketing.entitlement.EntitlementStatus;
import com.qinghe.marketing.identity.PosAuthenticatedStore;
import com.qinghe.marketing.reversal.JdbcReversalRepository;
import com.qinghe.marketing.reversal.PosReversalCommand;
import com.qinghe.marketing.reversal.ReversalReason;
import com.qinghe.marketing.reversal.ReversalRequestStatus;
import com.qinghe.marketing.reversal.ReversalResult;
import com.qinghe.marketing.reversal.ReversalTransactionService;
import com.qinghe.marketing.shared.clock.BusinessClock;
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
import java.util.Locale;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Real MySQL WP-07 probe for auditable reversal and settlement lock gates. */
class QingheWp07PersistenceIT {
    private static final String HOST_URL = System.getProperty("qinghe.it.mysql.url",
            "jdbc:mysql://127.0.0.1:3306/?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true");
    private static final String USER = System.getProperty("qinghe.it.mysql.user", "root");
    private static final String PASSWORD = credential("qinghe.it.mysql.password", "QINGHE_IT_MYSQL_PASSWORD");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 9, 16, 0);

    @Test
    void shouldReversePendingFactsButPreserveConfirmedSettlement() throws Exception {
        assertTrue(!PASSWORD.isEmpty(), "Set QINGHE_IT_MYSQL_PASSWORD for the disposable database probe");
        String database = "qh_wp07_" + UUID.randomUUID().toString().replace("-", "")
                .substring(0, 12).toLowerCase(Locale.ROOT);
        if (!database.matches("qh_wp07_[a-f0-9]{12}")) throw new IllegalStateException("unsafe database name");
        createDatabase(database);
        try {
            String url = HOST_URL.replace("/?", "/" + database + "?");
            try (Connection connection = DriverManager.getConnection(url, USER, PASSWORD)) {
                for (int version = 1; version <= 7; version++) executeScript(connection, migration(version));
                assertEquals(29, tableCount(connection));
            }
            DriverManagerDataSource dataSource = new DriverManagerDataSource(url, USER, PASSWORD);
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            TransactionTemplate transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
            seed(jdbc);
            BusinessIdGenerator ids = mock(BusinessIdGenerator.class);
            when(ids.next(BusinessIdType.REVERSAL)).thenReturn("REV-1", "REV-LOCKED");
            BusinessClock clock = () -> Instant.parse("2026-09-09T08:00:00Z");
            ReversalTransactionService service = new ReversalTransactionService(
                    new JdbcReversalRepository(jdbc), ids, clock);
            PosAuthenticatedStore store = new PosAuthenticatedStore(
                    2L, "S002", StoreOwnershipType.FRANCHISE, "POS-2");

            ReversalResult success = transaction.execute(status -> service.reverse(store,
                    command("REVREQ-0001", "RDM-1", "ORDER-1")));
            ReversalResult replay = transaction.execute(status -> service.reverse(store,
                    command("REVREQ-0001", "RDM-1", "ORDER-1")));
            assertEquals(success.reversalNo(), replay.reversalNo());
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM qh_redemption_reversal", Integer.class));
            assertEquals("REVERSED", jdbc.queryForObject("SELECT status FROM qh_redemption WHERE id=101", String.class));
            assertEquals("AVAILABLE", jdbc.queryForObject("SELECT status FROM qh_member_entitlement WHERE id=31", String.class));
            assertEquals("CANCELLED", jdbc.queryForObject("SELECT status FROM qh_subsidy_candidate WHERE id=201", String.class));
            assertEquals("CANCELLED", jdbc.queryForObject("SELECT status FROM qh_settlement_detail WHERE id=401", String.class));
            assertNull(jdbc.queryForObject("SELECT settlement_batch_id FROM qh_settlement_detail WHERE id=401", Long.class));
            assertEquals("DRAFT", jdbc.queryForObject("SELECT status FROM qh_settlement_batch WHERE id=301", String.class));
            assertEquals(0, jdbc.queryForObject("SELECT detail_count FROM qh_settlement_batch WHERE id=301", Integer.class));

            ReversalResult locked = transaction.execute(status -> service.reverse(store,
                    command("REVREQ-0002", "RDM-2", "ORDER-2")));
            assertEquals(ReversalRequestStatus.FAILED, locked.status());
            assertEquals(QingheErrorCode.SETTLEMENT_LOCKED.name(), locked.failureCode());
            assertEquals("SUCCESS", jdbc.queryForObject("SELECT status FROM qh_redemption WHERE id=102", String.class));
            assertEquals("USED", jdbc.queryForObject("SELECT status FROM qh_member_entitlement WHERE id=32", String.class));
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM qh_pos_reversal_request "
                    + "WHERE failure_code='SETTLEMENT_LOCKED'", Integer.class));

            try (Connection connection = DriverManager.getConnection(url, USER, PASSWORD)) {
                executeScript(connection, "db/qinghe/rollback/R007__drop_reversal_idempotency_and_audit.sql");
                assertEquals(28, tableCount(connection));
            }
        } finally {
            dropDatabase(database);
        }
    }

    private static PosReversalCommand command(String requestNo, String redemptionNo, String orderNo) {
        return new PosReversalCommand(redemptionNo, requestNo, orderNo, "S002", "OP-1",
                ReversalReason.POS_ORDER_CANCELLED, null, NOW);
    }

    private static void seed(JdbcTemplate jdbc) {
        jdbc.update("INSERT INTO qh_store (id, external_store_code, name, ownership_type, status, source_version, version, created_at, updated_at) "
                + "VALUES (2,'S002','加盟店','FRANCHISE','ACTIVE','v1',0,?,?)", NOW, NOW);
        jdbc.update("INSERT INTO qh_member_mapping (id, external_member_no, platform_user_id, status_snapshot, version, created_at, updated_at) "
                + "VALUES (20,'MEM-20',200,'ACTIVE',0,?,?),(21,'MEM-21',201,'ACTIVE',0,?,?)", NOW, NOW, NOW, NOW);
        jdbc.update("INSERT INTO qh_benefit_template (id,template_no,type,title,rules_snapshot,validity_type,validity_value,status,version,created_at,updated_at) "
                + "VALUES (1,'TPL-1','FREE_PRODUCT','免费饮品',CAST('{}' AS JSON),'RELATIVE_DAYS',7,'ACTIVE',0,?,?)", NOW, NOW);
        jdbc.update("INSERT INTO qh_campaign (id,campaign_no,template_id,name,status,begin_at,end_at,member_claim_limit,franchise_subsidy_fen,rule_version,version,created_by,created_at,updated_at) "
                + "VALUES (10,'CAM-1',1,'活动','ACTIVE',?,?,1,350,1,0,'MKT',?,?)", NOW.minusDays(1), NOW.plusDays(1), NOW, NOW);
        for (int index = 1; index <= 2; index++) {
            long claimId = 20 + index;
            long entitlementId = 30 + index;
            jdbc.update("INSERT INTO qh_claim_request (id,claim_no,request_id,request_digest,campaign_id,member_id,claim_cycle,reservation_id,status,version,created_at,updated_at) "
                            + "VALUES (?,?,?,REPEAT('a',64),10,?,?,?,?,0,?,?)", claimId, "CLM-" + index,
                    "CLAIM-REQ-" + index, 19L + index, "CYCLE-" + index, "RSV-" + index, "SUCCESS", NOW, NOW);
            jdbc.update("INSERT INTO qh_member_entitlement (id,entitlement_no,right_code_hash,encrypted_right_code,source_claim_id,campaign_id,member_id,status,valid_from,valid_until,version,created_at,updated_at) "
                            + "VALUES (?,?,REPEAT(?,64),X'01',?,10,?,'USED',?,?,0,?,?)", entitlementId,
                    "ENT-" + index, index == 1 ? "a" : "b", claimId, 19L + index,
                    NOW.minusDays(1), NOW.plusDays(1), NOW, NOW);
            jdbc.update("INSERT INTO qh_redemption (id,redemption_no,pos_client_id,pos_request_no,request_digest,pos_order_no,entitlement_id,store_id,status,occurred_at,version,created_at,updated_at) "
                            + "VALUES (?,?,?, ?,REPEAT('c',64),?,?,2,'SUCCESS',?,0,?,?)", 100L + index,
                    "RDM-" + index, "POS-2", "REDEEM-REQ-" + index, "ORDER-" + index,
                    entitlementId, NOW, NOW, NOW);
            jdbc.update("INSERT INTO qh_subsidy_candidate (id,redemption_id,campaign_id,store_id,subsidy_fen,status,snapshot_version,created_at,updated_at) "
                            + "VALUES (?, ?,10,2,350,'MATCHED',1,?,?)", 200L + index, 100L + index, NOW, NOW);
        }
        jdbc.update("INSERT INTO qh_recon_batch (id,provider,batch_no,business_date,checksum,status,total_rows,error_rows,created_at,updated_at) "
                + "VALUES (501,'POS','RB-1','2026-09-09',REPEAT('d',64),'MATCHED',2,0,?,?)", NOW, NOW);
        jdbc.update("INSERT INTO qh_settlement_batch (id,batch_no,recon_batch_id,business_date,status,detail_count,total_fen,version,created_at,updated_at) "
                + "VALUES (301,'STB-1',501,'2026-09-09','PENDING_CONFIRM',1,350,0,?,?)", NOW, NOW);
        jdbc.update("INSERT INTO qh_settlement_detail (id,settlement_batch_id,candidate_id,redemption_id,recon_batch_id,subsidy_fen,status,created_at,updated_at) "
                + "VALUES (401,301,201,101,501,350,'PENDING_CONFIRM',?,?),"
                + "(402,NULL,202,102,501,350,'CONFIRMED',?,?)", NOW, NOW, NOW, NOW);
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
            default: throw new IllegalArgumentException();
        }
    }
    private static void createDatabase(String db) throws SQLException { executeHost("CREATE DATABASE " + db + " CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci"); }
    private static void dropDatabase(String db) throws SQLException { executeHost("DROP DATABASE " + db); }
    private static void executeHost(String sql) throws SQLException {
        try (Connection c = DriverManager.getConnection(HOST_URL, USER, PASSWORD); Statement s = c.createStatement()) { s.execute(sql); }
    }
    private static void executeScript(Connection connection, String name) throws Exception {
        for (String part : resource(name).split(";")) if (!part.trim().isEmpty())
            try (Statement statement = connection.createStatement()) { statement.execute(part.trim()); }
    }
    private static int tableCount(Connection connection) throws SQLException {
        int count = 0;
        try (ResultSet tables = connection.getMetaData().getTables(connection.getCatalog(), null, "qh\\_%", new String[]{"TABLE"})) {
            while (tables.next()) count++;
        }
        return count;
    }
    private static String resource(String name) throws IOException {
        try (InputStream input = QingheWp07PersistenceIT.class.getClassLoader().getResourceAsStream(name)) {
            if (input == null) throw new IOException("resource not found: " + name);
            byte[] buffer = new byte[16384]; int count; StringBuilder result = new StringBuilder();
            while ((count = input.read(buffer)) >= 0) result.append(new String(buffer, 0, count, StandardCharsets.UTF_8));
            return result.toString();
        }
    }
    private static String credential(String property, String environment) {
        String value = System.getProperty(property);
        if (value != null && !value.trim().isEmpty()) return value;
        value = System.getenv(environment); return value == null ? "" : value;
    }
}
