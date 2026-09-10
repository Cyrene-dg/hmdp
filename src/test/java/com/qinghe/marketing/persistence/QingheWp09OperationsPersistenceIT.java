package com.qinghe.marketing.persistence;

import com.qinghe.marketing.operations.BusinessIdentifierType;
import com.qinghe.marketing.operations.BusinessTraceNode;
import com.qinghe.marketing.operations.JdbcOperationsQueryRepository;
import com.qinghe.marketing.operations.OperationalExceptionPage;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Real MySQL probe for WP-09 cross-domain trace and exception queries. */
class QingheWp09OperationsPersistenceIT {
    private static final String HOST_URL = System.getProperty("qinghe.it.mysql.url",
            "jdbc:mysql://127.0.0.1:3306/?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true");
    private static final String USER = System.getProperty("qinghe.it.mysql.user", "root");
    private static final String PASSWORD = credential(
            "qinghe.it.mysql.password", "QINGHE_IT_MYSQL_PASSWORD");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 10, 14, 0);

    @Test
    void shouldTraceEverySupportedIdentifierAndListOperationalExceptions() throws Exception {
        assertFalse(PASSWORD.isEmpty(),
                "Set QINGHE_IT_MYSQL_PASSWORD for the disposable database probe");
        String database = "qh_wp09_ops_" + UUID.randomUUID().toString().replace("-", "")
                .substring(0, 12).toLowerCase(Locale.ROOT);
        if (!database.matches("qh_wp09_ops_[a-f0-9]{12}")) {
            throw new IllegalStateException("unsafe database name");
        }
        createDatabase(database);
        try {
            String url = HOST_URL.replace("/?", "/" + database + "?");
            try (Connection connection = DriverManager.getConnection(url, USER, PASSWORD)) {
                for (int version = 1; version <= 8; version++) {
                    executeScript(connection, migration(version));
                }
            }
            JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(url, USER, PASSWORD));
            seed(jdbc);
            JdbcOperationsQueryRepository repository = new JdbcOperationsQueryRepository(jdbc);

            assertTrace(repository, BusinessIdentifierType.CLAIM_NO, "CLM-1");
            assertTrace(repository, BusinessIdentifierType.EVENT_ID, "EVT-1");
            assertTrace(repository, BusinessIdentifierType.ENTITLEMENT_NO, "ENT-1");
            assertTrace(repository, BusinessIdentifierType.POS_REQUEST_NO, "POS-1");
            assertTrace(repository, BusinessIdentifierType.REDEMPTION_NO, "RDM-1");
            assertTrace(repository, BusinessIdentifierType.RECON_BATCH_NO, "RCB-1");
            List<BusinessTraceNode> settlement = repository.findTrace(
                    BusinessIdentifierType.SETTLEMENT_BATCH_NO, "SET-1");
            assertTrue(types(settlement).contains("SETTLEMENT_BATCH"));
            assertTrue(types(settlement).contains("OPERATION_AUDIT"));

            List<BusinessTraceNode> failedPos = repository.findTrace(
                    BusinessIdentifierType.POS_REQUEST_NO, "POS-FAILED");
            assertEquals(1, failedPos.size());
            assertEquals("RIGHT_NOT_AVAILABLE", failedPos.get(0).getFailureCode());

            OperationalExceptionPage page = repository.findExceptions(null, null,
                    NOW.minusMinutes(5), NOW.minusHours(24), 1, 100);
            Set<String> exceptionTypes = page.getItems().stream()
                    .map(item -> item.getExceptionType()).collect(Collectors.toSet());
            assertEquals(page.getTotal(), page.getItems().size());
            assertTrue(exceptionTypes.contains("CLAIM_PROCESSING_STUCK"));
            assertTrue(exceptionTypes.contains("OUTBOX_DEAD"));
            assertTrue(exceptionTypes.contains("POS_REDEMPTION_FAILED"));
            assertTrue(exceptionTypes.contains("REVERSAL_REJECTED"));
            assertTrue(exceptionTypes.contains("RECON_DIFFERENCE"));
            assertTrue(exceptionTypes.contains("SETTLEMENT_PENDING_STALE"));
            OperationalExceptionPage filtered = repository.findExceptions(
                    "OUTBOX_DEAD", "DEAD", NOW.minusMinutes(5), NOW.minusHours(24), 1, 20);
            assertEquals(1, filtered.getTotal());
            assertEquals("EVT-DEAD", filtered.getItems().get(0).getBusinessId());
        } finally {
            dropDatabase(database);
        }
    }

    private static void assertTrace(JdbcOperationsQueryRepository repository,
                                    BusinessIdentifierType type, String value) {
        List<BusinessTraceNode> trace = repository.findTrace(type, value);
        assertFalse(trace.isEmpty(), type.name());
        assertTrue(types(trace).contains("CLAIM_REQUEST"), type.name());
        assertTrue(types(trace).contains("REDEMPTION"), type.name());
    }

    private static Set<String> types(List<BusinessTraceNode> trace) {
        return trace.stream().map(BusinessTraceNode::getNodeType).collect(Collectors.toSet());
    }

    private static void seed(JdbcTemplate jdbc) {
        LocalDateTime old = NOW.minusDays(2);
        jdbc.update("INSERT INTO qh_store (id,external_store_code,name,ownership_type,status,"
                        + "source_version,version,created_at,updated_at) VALUES "
                        + "(1,'QH006','加盟店','FRANCHISE','ACTIVE','v1',0,?,?)", old, old);
        jdbc.update("INSERT INTO qh_benefit_template (id,template_no,type,title,rules_snapshot,"
                        + "validity_type,validity_value,status,version,created_at,updated_at) VALUES "
                        + "(1,'TPL-1','FREE_PRODUCT','免费饮品',CAST('{}' AS JSON),"
                        + "'RELATIVE_DAYS',7,'ACTIVE',0,?,?)", old, old);
        jdbc.update("INSERT INTO qh_campaign (id,campaign_no,template_id,name,status,begin_at,end_at,"
                        + "member_claim_limit,franchise_subsidy_fen,rule_version,version,created_by,"
                        + "created_at,updated_at) VALUES (10,'CAM-1',1,'运营查询活动','ACTIVE',?,?,1,350,"
                        + "1,0,'MKT',?,?)", old, NOW.plusDays(2), old, old);
        jdbc.update("INSERT INTO qh_member_mapping (id,external_member_no,platform_user_id,"
                        + "status_snapshot,version,created_at,updated_at) VALUES "
                        + "(20,'MEM-1',1001,'ACTIVE',0,?,?)", old, old);
        jdbc.update("INSERT INTO qh_claim_request (id,claim_no,request_id,request_digest,campaign_id,"
                        + "member_id,claim_cycle,reservation_id,status,version,created_at,updated_at) "
                        + "VALUES (30,'CLM-1','REQ-1',REPEAT('a',64),10,20,'CYCLE-1','RSV-1',"
                        + "'SUCCESS',0,?,?),(31,'CLM-STUCK','REQ-2',REPEAT('b',64),10,20,'CYCLE-2',"
                        + "'RSV-2','PROCESSING',0,?,?)", old, old, old, old);
        jdbc.update("INSERT INTO qh_outbox_event (id,event_id,aggregate_type,aggregate_id,event_type,"
                        + "event_version,payload,status,retry_count,last_error,created_at,updated_at) "
                        + "VALUES (40,'EVT-1','CLAIM_REQUEST','CLM-1','CLAIM_ACCEPTED',1,CAST('{}' AS JSON),"
                        + "'PUBLISHED',0,NULL,?,?),(41,'EVT-DEAD','CLAIM_REQUEST','CLM-STUCK',"
                        + "'CLAIM_ACCEPTED',1,CAST('{}' AS JSON),'DEAD',8,'broker unavailable',?,?)",
                old, old, old, old);
        jdbc.update("INSERT INTO qh_member_entitlement (id,entitlement_no,right_code_hash,"
                        + "encrypted_right_code,source_claim_id,campaign_id,member_id,status,valid_from,"
                        + "valid_until,version,created_at,updated_at) VALUES "
                        + "(50,'ENT-1',REPEAT('c',64),X'01',30,10,20,'USED',?,?,0,?,?)",
                old, NOW.plusDays(1), old, old);
        jdbc.update("INSERT INTO qh_redemption (id,redemption_no,pos_client_id,pos_request_no,"
                        + "request_digest,pos_order_no,terminal_no,operator_no,entitlement_id,store_id,"
                        + "status,occurred_at,first_processed_at,version,created_at,updated_at) VALUES "
                        + "(60,'RDM-1','POS-QH006','POS-1',REPEAT('d',64),'ORDER-1','T1','OP',50,1,"
                        + "'SUCCESS',?,?,0,?,?)", old.plusHours(1), old.plusHours(1), old, old);
        jdbc.update("INSERT INTO qh_pos_redemption_request (id,pos_client_id,pos_request_no,"
                        + "request_digest,store_id,status,redemption_id,right_status,first_processed_at,"
                        + "version,created_at,updated_at) VALUES "
                        + "(70,'POS-QH006','POS-1',REPEAT('d',64),1,'SUCCESS',60,'USED',?,1,?,?),"
                        + "(71,'POS-QH006','POS-FAILED',REPEAT('e',64),1,'FAILED',NULL,'USED',?,1,?,?)",
                old.plusHours(1), old, old, old.plusHours(2), old, old);
        jdbc.update("UPDATE qh_pos_redemption_request SET failure_code='RIGHT_NOT_AVAILABLE' "
                + "WHERE id=71");
        jdbc.update("INSERT INTO qh_pos_reversal_request (id,pos_client_id,pos_request_no,"
                        + "request_digest,target_redemption_no,store_id,status,redemption_id,failure_code,"
                        + "right_status,first_processed_at,version,created_at,updated_at) VALUES "
                        + "(80,'POS-QH006','REV-FAILED',REPEAT('f',64),'RDM-1',1,'FAILED',60,"
                        + "'REVERSAL_NOT_ALLOWED','USED',?,1,?,?)", old.plusHours(3), old, old);
        jdbc.update("INSERT INTO qh_subsidy_candidate (id,redemption_id,campaign_id,store_id,"
                        + "subsidy_fen,status,snapshot_version,created_at,updated_at) VALUES "
                        + "(90,60,10,1,350,'MATCHED',1,?,?)", old, old);
        jdbc.update("INSERT INTO qh_recon_batch (id,recon_batch_no,provider,batch_no,business_date,"
                        + "checksum,file_name,schema_version,checksum_algorithm,status,total_rows,"
                        + "imported_rows,success_rows,error_rows,matched_rows,difference_rows,"
                        + "franchise_eligible_rows,next_line_no,version,created_at,updated_at) VALUES "
                        + "(100,'RCB-1','MOCK_POS','PB-1','2026-09-08',REPEAT('1',64),'POS.csv','1.0',"
                        + "'SHA-256','COMPLETED',1,1,1,0,1,1,1,3,3,?,?)", old, old);
        jdbc.update("INSERT INTO qh_recon_record (id,batch_id,line_no,store_code,pos_order_no,"
                        + "pos_request_no,redemption_no,operation_type,operation_status,occurred_at,"
                        + "match_status,match_reason,matched_redemption_id,store_ownership,"
                        + "settlement_eligible,matched_at,raw_digest,created_at,updated_at) VALUES "
                        + "(110,100,2,'QH006','ORDER-1','POS-1','RDM-1','REDEEM','SUCCESS',?,"
                        + "'MATCHED',NULL,60,'FRANCHISE',1,?,REPEAT('2',64),?,?)",
                old.plusHours(1), old.plusHours(4), old, old);
        jdbc.update("INSERT INTO qh_recon_difference (id,batch_id,record_id,redemption_id,"
                        + "difference_type,business_key,detail,created_at) VALUES "
                        + "(120,100,110,60,'DIFFERENCE_DATA','POS-1','simulated mismatch',?)", old);
        jdbc.update("INSERT INTO qh_settlement_batch (id,batch_no,recon_batch_id,business_date,status,"
                        + "detail_count,total_fen,version,created_at,updated_at) VALUES "
                        + "(130,'SET-1',100,'2026-09-08','PENDING_CONFIRM',1,350,1,?,?)", old, old);
        jdbc.update("INSERT INTO qh_settlement_detail (id,settlement_batch_id,candidate_id,"
                        + "redemption_id,recon_batch_id,subsidy_fen,status,created_at,updated_at) VALUES "
                        + "(140,130,90,60,100,350,'PENDING_CONFIRM',?,?)", old, old);
        jdbc.update("INSERT INTO qh_operation_log (operator_type,operator_id,action,business_type,"
                        + "business_id,before_state,after_state,reason,result,request_id,trace_id,created_at) "
                        + "VALUES ('ADMIN','FIN-1','SETTLEMENT_GENERATE','SETTLEMENT_BATCH','SET-1',"
                        + "NULL,'PENDING_CONFIRM','复核','SUCCESS','REQ-AUDIT','TRACE-AUDIT',?)", old);
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

    private static String resource(String name) throws IOException {
        try (InputStream input = QingheWp09OperationsPersistenceIT.class.getClassLoader()
                .getResourceAsStream(name)) {
            if (input == null) throw new IOException("resource not found: " + name);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[16384];
            int count;
            while ((count = input.read(buffer)) >= 0) output.write(buffer, 0, count);
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static String credential(String property, String environment) {
        String value = System.getProperty(property);
        if (value != null && !value.trim().isEmpty()) return value;
        value = System.getenv(environment);
        return value == null ? "" : value;
    }
}
