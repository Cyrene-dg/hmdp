package com.qinghe.marketing.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qinghe.marketing.campaign.BenefitTemplate;
import com.qinghe.marketing.campaign.BenefitTemplateDraft;
import com.qinghe.marketing.campaign.BenefitTemplateService;
import com.qinghe.marketing.campaign.BenefitTemplateSnapshotCodec;
import com.qinghe.marketing.campaign.BenefitType;
import com.qinghe.marketing.campaign.Campaign;
import com.qinghe.marketing.campaign.CampaignApprovalTransactionService;
import com.qinghe.marketing.campaign.CampaignDraftCommand;
import com.qinghe.marketing.campaign.CampaignService;
import com.qinghe.marketing.campaign.CampaignStatus;
import com.qinghe.marketing.campaign.CampaignStockCache;
import com.qinghe.marketing.campaign.InventoryAdjustment;
import com.qinghe.marketing.campaign.InventoryAdjustmentResult;
import com.qinghe.marketing.campaign.InventoryAdjustmentService;
import com.qinghe.marketing.campaign.InventoryAdjustmentStatus;
import com.qinghe.marketing.campaign.JdbcBenefitTemplateRepository;
import com.qinghe.marketing.campaign.JdbcCampaignRepository;
import com.qinghe.marketing.campaign.JdbcInventoryAdjustmentRepository;
import com.qinghe.marketing.campaign.ReviewDecision;
import com.qinghe.marketing.campaign.ValidityType;
import com.qinghe.marketing.shared.clock.BusinessClock;
import com.qinghe.marketing.shared.error.QingheBusinessException;
import com.qinghe.marketing.shared.error.QingheErrorCode;
import com.qinghe.marketing.shared.id.BusinessIdGenerator;
import com.qinghe.marketing.store.JdbcStoreRepository;
import com.qinghe.marketing.store.StoreImportRow;
import com.qinghe.marketing.store.StoreOwnershipType;
import com.qinghe.marketing.store.StoreStatus;
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
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Real MySQL WP-03 probe. It creates and removes one qh_wp03_* database. */
class QingheWp03PersistenceIT {

    private static final String HOST_URL = System.getProperty("qinghe.it.mysql.url",
            "jdbc:mysql://127.0.0.1:3306/?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true");
    private static final String USER = System.getProperty("qinghe.it.mysql.user", "root");
    private static final String PASSWORD = credential("qinghe.it.mysql.password", "QINGHE_IT_MYSQL_PASSWORD");
    private static final Instant NOW = Instant.parse("2026-09-09T08:00:00Z");

    @Test
    void shouldPublishImmutableCampaignAndApplyInventoryOnceThenRollbackV003() throws Exception {
        assertFalse(PASSWORD.isEmpty(), "Set QINGHE_IT_MYSQL_PASSWORD for the disposable database probe");
        String database = "qh_wp03_" + UUID.randomUUID().toString().replace("-", "")
                .substring(0, 12).toLowerCase(Locale.ROOT);
        if (!database.matches("qh_wp03_[a-f0-9]{12}")) {
            throw new IllegalStateException("unsafe temporary database name");
        }

        createDatabase(database);
        try {
            String databaseUrl = HOST_URL.replace("/?", "/" + database + "?");
            try (Connection connection = DriverManager.getConnection(databaseUrl, USER, PASSWORD)) {
                executeScript(connection, "db/qinghe/migration/V001__create_qinghe_core.sql");
                executeScript(connection, "db/qinghe/migration/V002__create_identity_and_store_support.sql");
                executeScript(connection, "db/qinghe/migration/V003__add_campaign_publication_and_reviews.sql");
                assertEquals(25, qingheTableCount(connection));
            }

            DriverManagerDataSource dataSource = new DriverManagerDataSource(databaseUrl, USER, PASSWORD);
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            TransactionTemplate transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
            verifyCampaignAndInventory(jdbc, transaction);

            try (Connection connection = DriverManager.getConnection(databaseUrl, USER, PASSWORD)) {
                executeScript(connection, "db/qinghe/rollback/R003__drop_campaign_publication_and_reviews.sql");
                assertEquals(23, qingheTableCount(connection));
                executeScript(connection, "db/qinghe/rollback/R002__drop_identity_and_store_support.sql");
                assertEquals(18, qingheTableCount(connection));
                executeScript(connection, "db/qinghe/rollback/R001__drop_qinghe_core.sql");
                assertEquals(0, qingheTableCount(connection));
            }
        } finally {
            dropDatabase(database);
        }
    }

    private void verifyCampaignAndInventory(JdbcTemplate jdbc, TransactionTemplate transaction) {
        BusinessClock clock = () -> NOW;
        BusinessIdGenerator ids = new BusinessIdGenerator(clock);
        JdbcStoreRepository stores = new JdbcStoreRepository(jdbc);
        LocalDateTime now = clock.dateTime();
        stores.upsert(new StoreImportRow(1, "STORE-1", "QH001", "直营店",
                StoreOwnershipType.DIRECT, StoreStatus.ACTIVE, "POS-3.2", null), now);
        stores.upsert(new StoreImportRow(2, "STORE-1", "QH006", "加盟店",
                StoreOwnershipType.FRANCHISE, StoreStatus.ACTIVE, "POS-3.2", null), now);

        BenefitTemplateSnapshotCodec codec = new BenefitTemplateSnapshotCodec(new ObjectMapper());
        JdbcBenefitTemplateRepository templates = new JdbcBenefitTemplateRepository(jdbc, codec);
        BenefitTemplateService templateService = new BenefitTemplateService(templates, codec, ids, clock);
        BenefitTemplate template = templateService.create(new BenefitTemplateDraft(
                "鲜奶茶兑换券", BenefitType.FREE_PRODUCT, "免费兑换指定中杯饮品", "限参与门店",
                "DRINK-M-001", null, ValidityType.RELATIVE_DAYS, 7,
                null, null, "{\"allowStacking\":false,\"minimumOrderFen\":0}"));

        JdbcCampaignRepository campaigns = new JdbcCampaignRepository(jdbc);
        CampaignApprovalTransactionService approvals = new CampaignApprovalTransactionService(campaigns, ids, clock);
        InMemoryStockCache stockCache = new InMemoryStockCache();
        CampaignService campaignService = new CampaignService(campaigns, templates, codec, stores,
                approvals, stockCache, ids, clock);
        Campaign draft = transaction.execute(status -> campaignService.createDraft(
                new CampaignDraftCommand("九月回馈", "两家试点", template.templateNo(),
                        now.plusHours(1), now.plusDays(7), 1000L, 1,
                        Arrays.asList("QH001", "QH006"), 350L), "MKT-001"));
        Campaign pending = campaignService.submit(draft.campaignNo(), draft.version(), "MKT-001");
        Campaign scheduled = transaction.execute(status -> campaignService.review(
                draft.campaignNo(), ReviewDecision.APPROVE, pending.version(), "OPS-002", "同意"));

        assertEquals(CampaignStatus.SCHEDULED, scheduled.status());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM qh_campaign_publication_snapshot", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM qh_campaign_review", Integer.class));
        assertEquals(0L, jdbc.queryForObject("SELECT subsidy_fen FROM qh_campaign_store cs "
                + "JOIN qh_store s ON s.id = cs.store_id WHERE s.external_store_code = 'QH001'", Long.class));
        assertEquals(350L, jdbc.queryForObject("SELECT subsidy_fen FROM qh_campaign_store cs "
                + "JOIN qh_store s ON s.id = cs.store_id WHERE s.external_store_code = 'QH006'", Long.class));
        assertEquals(1000L, stockCache.currentAvailableStock(scheduled.id()).getAsLong());

        Campaign repeated = transaction.execute(status -> campaignService.review(
                draft.campaignNo(), ReviewDecision.APPROVE, pending.version(), "OPS-002", "网络重试"));
        assertEquals(CampaignStatus.SCHEDULED, repeated.status());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM qh_campaign_publication_snapshot", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM qh_campaign_review", Integer.class));

        QingheBusinessException locked = assertThrows(QingheBusinessException.class,
                () -> templateService.update(template.templateNo(), template.draft(), template.version()));
        assertEquals(QingheErrorCode.TEMPLATE_VERSION_LOCKED, locked.errorCode());

        JdbcInventoryAdjustmentRepository adjustmentRepository = new JdbcInventoryAdjustmentRepository(jdbc);
        InventoryAdjustmentService inventoryService = new InventoryAdjustmentService(
                adjustmentRepository, campaigns, stockCache, ids, clock);
        InventoryAdjustment adjustment = inventoryService.create(
                draft.campaignNo(), 200L, "领取速度超预期", scheduled.version(), "MKT-001");
        InventoryAdjustment submitted = inventoryService.submit(
                adjustment.adjustmentNo(), adjustment.version(), "MKT-001");
        InventoryAdjustmentResult applied = transaction.execute(status -> inventoryService.review(
                adjustment.adjustmentNo(), ReviewDecision.APPROVE, submitted.version(), "OPS-003", "同意"));
        InventoryAdjustmentResult repeatedAdjustment = transaction.execute(status -> inventoryService.review(
                adjustment.adjustmentNo(), ReviewDecision.APPROVE, submitted.version(), "OPS-003", "重试"));

        assertEquals(InventoryAdjustmentStatus.APPLIED, applied.adjustment().status());
        assertEquals(1200L, applied.afterTotalStock());
        assertEquals(1200L, repeatedAdjustment.afterTotalStock());
        assertEquals(1200L, jdbc.queryForObject(
                "SELECT total_stock FROM qh_campaign_inventory WHERE campaign_id = ?",
                Long.class, scheduled.id()));
        assertEquals(1200L, stockCache.currentAvailableStock(scheduled.id()).getAsLong());
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

    private static final class InMemoryStockCache implements CampaignStockCache {
        private final Map<Long, Long> stocks = new HashMap<Long, Long>();
        private final Set<String> applied = new HashSet<String>();

        @Override
        public void initialize(long campaignId, long initialStock) {
            Long existing = stocks.putIfAbsent(campaignId, initialStock);
            if (existing != null && existing.longValue() != initialStock) {
                throw new IllegalStateException("conflicting stock initialization");
            }
        }

        @Override
        public long applyApprovedIncrease(long campaignId, String adjustmentNo, long incrementStock) {
            if (applied.add(adjustmentNo)) {
                stocks.put(campaignId, Math.addExact(stocks.get(campaignId), incrementStock));
            }
            return stocks.get(campaignId);
        }

        @Override
        public OptionalLong currentAvailableStock(long campaignId) {
            Long value = stocks.get(campaignId);
            return value == null ? OptionalLong.empty() : OptionalLong.of(value);
        }
    }
}
