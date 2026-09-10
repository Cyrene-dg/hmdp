package com.qinghe.marketing.system;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qinghe.marketing.campaign.BenefitTemplate;
import com.qinghe.marketing.campaign.BenefitTemplateDraft;
import com.qinghe.marketing.campaign.BenefitTemplateSnapshotCodec;
import com.qinghe.marketing.campaign.BenefitTemplateService;
import com.qinghe.marketing.campaign.BenefitType;
import com.qinghe.marketing.campaign.Campaign;
import com.qinghe.marketing.campaign.CampaignApprovalTransactionService;
import com.qinghe.marketing.campaign.CampaignDraftCommand;
import com.qinghe.marketing.campaign.CampaignService;
import com.qinghe.marketing.campaign.CampaignStatus;
import com.qinghe.marketing.campaign.JdbcBenefitTemplateRepository;
import com.qinghe.marketing.campaign.JdbcCampaignRepository;
import com.qinghe.marketing.campaign.RedisCampaignStockCache;
import com.qinghe.marketing.campaign.ReviewDecision;
import com.qinghe.marketing.campaign.ValidityType;
import com.qinghe.marketing.claim.ClaimAcceptanceTransactionService;
import com.qinghe.marketing.claim.ClaimRequest;
import com.qinghe.marketing.claim.ClaimService;
import com.qinghe.marketing.claim.ClaimSubmissionResult;
import com.qinghe.marketing.claim.JdbcClaimRequestRepository;
import com.qinghe.marketing.claim.JdbcOutboxEventRepository;
import com.qinghe.marketing.claim.LeasedOutboxEvent;
import com.qinghe.marketing.claim.OutboxStatus;
import com.qinghe.marketing.claim.RedisClaimReservationStore;
import com.qinghe.marketing.entitlement.AesGcmRightCodeProtector;
import com.qinghe.marketing.entitlement.BenefitIssueCommand;
import com.qinghe.marketing.entitlement.BenefitIssueCommandCodec;
import com.qinghe.marketing.entitlement.ClaimIssueOutcome;
import com.qinghe.marketing.entitlement.ClaimIssueService;
import com.qinghe.marketing.entitlement.ClaimIssueTransactionService;
import com.qinghe.marketing.entitlement.ClaimIssueDeliveryRepository;
import com.qinghe.marketing.entitlement.EntitlementPage;
import com.qinghe.marketing.entitlement.EntitlementStatus;
import com.qinghe.marketing.entitlement.JdbcClaimIssueDeliveryRepository;
import com.qinghe.marketing.entitlement.JdbcMemberEntitlementRepository;
import com.qinghe.marketing.entitlement.MemberEntitlement;
import com.qinghe.marketing.entitlement.MemberEntitlementRepository;
import com.qinghe.marketing.entitlement.MemberEntitlementService;
import com.qinghe.marketing.identity.PosAuthenticatedStore;
import com.qinghe.marketing.operations.JdbcOperationAuditRecorder;
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
import com.qinghe.marketing.redemption.CampaignStorePolicy;
import com.qinghe.marketing.redemption.JdbcRedemptionRepository;
import com.qinghe.marketing.redemption.PosRedemptionCommand;
import com.qinghe.marketing.redemption.PosRedemptionService;
import com.qinghe.marketing.redemption.PosRequestStatus;
import com.qinghe.marketing.redemption.RedemptionRepository;
import com.qinghe.marketing.redemption.RedemptionResult;
import com.qinghe.marketing.redemption.RedemptionTransactionService;
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
import com.qinghe.marketing.shared.clock.BusinessClock;
import com.qinghe.marketing.shared.id.BusinessIdGenerator;
import com.qinghe.marketing.store.StoreOwnershipType;
import com.qinghe.marketing.store.JdbcStoreRepository;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Collections;
import java.util.Locale;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WP-10 system journey on one disposable MySQL schema and one isolated Redis.
 * RabbitMQ delivery and failure recovery remain covered by the WP-04/WP-05 integration tests.
 */
class QingheSystemJourneyIT {
    private static final String MYSQL_HOST_URL = System.getProperty("qinghe.it.mysql.url",
            "jdbc:mysql://127.0.0.1:3306/?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true");
    private static final String MYSQL_USER = System.getProperty("qinghe.it.mysql.user", "root");
    private static final String MYSQL_PASSWORD = credential(
            "qinghe.it.mysql.password", "QINGHE_IT_MYSQL_PASSWORD");
    private static final String REDIS_HOST = System.getProperty("qinghe.it.redis.host", "127.0.0.1");
    private static final int REDIS_PORT = Integer.parseInt(
            System.getProperty("qinghe.it.redis.port", "6379"));
    private static final String REDIS_PASSWORD = credential(
            "qinghe.it.redis.password", "QINGHE_IT_REDIS_PASSWORD");
    private static final Instant BUSINESS_INSTANT = Instant.parse("2026-09-10T08:00:00Z");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 10, 16, 0);

    @Test
    void shouldCompletePublishedCampaignJourneyFromClaimToSettlementExport() throws Exception {
        assertFalse(MYSQL_PASSWORD.isEmpty(),
                "Set QINGHE_IT_MYSQL_PASSWORD for the disposable system journey");
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12)
                .toLowerCase(Locale.ROOT);
        String database = "qh_wp10_journey_" + suffix;
        long memberId = 920_000_000L + Long.parseLong(suffix.substring(0, 6), 16);
        long storeId = memberId + 1;
        long[] campaignIdHolder = new long[]{0L};
        String requestId = "REQ-JOURNEY-" + suffix;
        if (!database.matches("qh_wp10_journey_[a-f0-9]{12}")) {
            throw new IllegalStateException("unsafe temporary database name");
        }

        createDatabase(database);
        LettuceConnectionFactory redisFactory = null;
        StringRedisTemplate redis = null;
        try {
            String databaseUrl = MYSQL_HOST_URL.replace("/?", "/" + database + "?");
            migrate(databaseUrl);
            DriverManagerDataSource dataSource = new DriverManagerDataSource(
                    databaseUrl, MYSQL_USER, MYSQL_PASSWORD);
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            PlatformTransactionManager transactionManager =
                    new DataSourceTransactionManager(dataSource);
            TransactionTemplate transaction = new TransactionTemplate(transactionManager);
            BusinessClock clock = () -> BUSINESS_INSTANT;
            ObjectMapper mapper = new ObjectMapper();
            AesGcmRightCodeProtector protector = new AesGcmRightCodeProtector(
                    Base64.getEncoder().encodeToString(
                            "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8)));
            seedIdentity(jdbc, memberId, storeId);

            RedisStandaloneConfiguration redisConfiguration =
                    new RedisStandaloneConfiguration(REDIS_HOST, REDIS_PORT);
            if (!REDIS_PASSWORD.isEmpty()) {
                redisConfiguration.setPassword(RedisPassword.of(REDIS_PASSWORD));
            }
            redisFactory = new LettuceConnectionFactory(redisConfiguration);
            redisFactory.afterPropertiesSet();
            redis = new StringRedisTemplate(redisFactory);
            redis.afterPropertiesSet();

            JdbcCampaignRepository campaigns = new JdbcCampaignRepository(jdbc);
            BenefitTemplateSnapshotCodec templateCodec = new BenefitTemplateSnapshotCodec(mapper);
            BusinessIdGenerator ids = new BusinessIdGenerator(clock);
            JdbcBenefitTemplateRepository templates =
                    new JdbcBenefitTemplateRepository(jdbc, templateCodec);
            BenefitTemplate template = new BenefitTemplateService(templates, templateCodec, ids, clock)
                    .create(new BenefitTemplateDraft("青禾招牌饮品券", BenefitType.FREE_PRODUCT,
                            "招牌饮品免费券", "限活动门店使用", "DRINK", null,
                            ValidityType.RELATIVE_DAYS, 7, null, null,
                            "{\"allowStacking\":false,\"minimumOrderFen\":0}"));
            CampaignApprovalTransactionService approval = transactional(
                    new CampaignApprovalTransactionService(campaigns, ids, clock),
                    transactionManager);
            CampaignService campaignService = new CampaignService(campaigns, templates,
                    templateCodec, new JdbcStoreRepository(jdbc), approval,
                    new RedisCampaignStockCache(redis), ids, clock);
            Campaign draft = transaction.execute(status -> campaignService.createDraft(
                    new CampaignDraftCommand("青禾整链验收活动", "WP10模拟业务",
                            template.templateNo(), NOW.minusHours(1), NOW.plusDays(2), 1, 1,
                            Collections.singletonList("QH-F001"), 350L), "MKT-WP10"));
            Campaign pending = campaignService.submit(
                    draft.campaignNo(), draft.version(), "MKT-WP10");
            Campaign scheduled = campaignService.review(draft.campaignNo(), ReviewDecision.APPROVE,
                    pending.version(), "OPS-WP10", "同意进入模拟试点");
            assertEquals(CampaignStatus.SCHEDULED, scheduled.status());
            assertEquals(1, campaignService.advanceTimeBasedStates());
            Campaign active = campaignService.require(draft.campaignNo());
            assertEquals(CampaignStatus.ACTIVE, active.status());
            assertEquals(1, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM qh_campaign_publication_snapshot WHERE campaign_id=?",
                    Integer.class, active.id()));
            long campaignId = active.id();
            campaignIdHolder[0] = campaignId;
            String stockKey = "qh:campaign:stock:" + campaignId;
            assertEquals("1", redis.opsForValue().get(stockKey));

            JdbcClaimRequestRepository claims = new JdbcClaimRequestRepository(jdbc);
            JdbcOutboxEventRepository outbox = new JdbcOutboxEventRepository(jdbc);
            RedisClaimReservationStore reservations = new RedisClaimReservationStore(redis);
            ClaimAcceptanceTransactionService acceptance = transactional(
                    new ClaimAcceptanceTransactionService(claims, outbox, mapper), transactionManager);
            ClaimService claimService = new ClaimService(campaigns, claims, reservations,
                    acceptance, new BusinessIdGenerator(clock), clock);

            OffsetDateTime clientTime = BUSINESS_INSTANT.atOffset(ZoneOffset.UTC);
            ClaimSubmissionResult submitted = claimService.submit(
                    active.campaignNo(), memberId, requestId, clientTime);
            ClaimRequest accepted = submitted.claimRequest();
            assertFalse(submitted.replay());
            assertEquals("PROCESSING", accepted.status().name());
            assertEquals("0", redis.opsForValue().get(stockKey));
            assertEquals(1, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM qh_outbox_event WHERE status='NEW'", Integer.class));

            LeasedOutboxEvent leased = transaction.execute(status -> outbox.leaseBatch(
                    "wp10-system", NOW, NOW.plusMinutes(1), 1)).get(0);
            OutboxStatus publication = transaction.execute(status -> outbox.completePublication(
                    leased.eventId(), "wp10-system", true, null, null, 3, NOW));
            assertEquals(OutboxStatus.PUBLISHED, publication);
            BenefitIssueCommand issueCommand = new BenefitIssueCommandCodec(mapper)
                    .decode(leased.payload());

            MemberEntitlementRepository entitlements =
                    new JdbcMemberEntitlementRepository(jdbc, mapper);
            ClaimIssueDeliveryRepository deliveries = new JdbcClaimIssueDeliveryRepository(jdbc);
            ClaimIssueTransactionService issueTransaction = transactional(
                    new ClaimIssueTransactionService(claims, entitlements, deliveries, campaigns,
                            new BenefitTemplateSnapshotCodec(mapper), protector,
                            new BusinessIdGenerator(clock), clock), transactionManager);
            assertEquals(ClaimIssueOutcome.ISSUED,
                    new ClaimIssueService(issueTransaction, reservations).issue(issueCommand).outcome());
            MemberEntitlement entitlement = entitlements.findBySourceClaimId(accepted.id())
                    .orElseThrow(() -> new AssertionError("issued entitlement missing"));
            EntitlementPage cardPack = new MemberEntitlementService(entitlements, protector)
                    .list(memberId, EntitlementStatus.AVAILABLE, 1, 20);
            assertEquals(1, cardPack.total());
            assertEquals(entitlement.entitlementNo(),
                    cardPack.items().get(0).entitlement().entitlementNo());

            String rightCode = protector.reveal(entitlement.encryptedRightCode());
            RedemptionRepository redemptions = new JdbcRedemptionRepository(jdbc,
                    new BenefitTemplateSnapshotCodec(mapper));
            com.qinghe.marketing.redemption.PosEntitlementSnapshot redeemable = redemptions
                    .findEntitlementByRightCodeHash(protector.hash(rightCode))
                    .orElseThrow(() -> new AssertionError("issued right is not resolvable by POS"));
            assertEquals(EntitlementStatus.AVAILABLE, redeemable.status());
            assertFalse(redeemable.validFrom().isAfter(NOW),
                    "issued right starts after the shared business clock: " + redeemable.validFrom());
            assertTrue(redeemable.validUntil().isAfter(NOW),
                    "issued right expires before the shared business clock: " + redeemable.validUntil());
            RedemptionTransactionService redemptionTransaction = transactional(
                    new RedemptionTransactionService(redemptions, protector,
                            new CampaignStorePolicy(campaigns), new BusinessIdGenerator(clock), clock),
                    transactionManager);
            PosRedemptionService pos = new PosRedemptionService(redemptionTransaction, redemptions);
            PosAuthenticatedStore franchise = new PosAuthenticatedStore(
                    storeId, "QH-F001", StoreOwnershipType.FRANCHISE, "POS-QH-F001");
            PosRedemptionCommand redeemCommand = new PosRedemptionCommand(
                    "REDEEM-JOURNEY-001", "ORDER-JOURNEY-001", "QH-F001",
                    "T-JOURNEY", "OP-JOURNEY", rightCode, NOW);
            RedemptionResult redeemed = pos.redeem(franchise, redeemCommand);
            RedemptionResult replay = pos.redeem(franchise, redeemCommand);
            assertEquals(PosRequestStatus.SUCCESS, redeemed.status());
            assertTrue(replay.replay());
            assertEquals(redeemed.redemptionNo(), replay.redemptionNo());
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM qh_redemption", Integer.class));
            assertEquals(1, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM qh_subsidy_candidate WHERE status='UNRECONCILED'",
                    Integer.class));

            ReconciliationBatchRepository reconBatches =
                    new JdbcReconciliationBatchRepository(jdbc);
            ReconciliationImportService importer = importService(
                    reconBatches, transactionManager, clock, protector);
            FilePair file = reconciliationFile(redeemed.redemptionNo(), rightCode);
            ReconciliationImportResult imported = importer.importFile(
                    file.manifest, file.fileName, file.csv, 1);
            assertEquals(ReconciliationBatchStatus.MATCHING, imported.status());
            long reconId = jdbc.queryForObject(
                    "SELECT id FROM qh_recon_batch WHERE recon_batch_no=?", Long.class,
                    imported.reconBatchNo());
            ReconciliationMatchingRepository matchingRepository =
                    new JdbcReconciliationMatchingRepository(jdbc);
            ReconciliationMatchSummary matched = new ReconciliationMatchingService(
                    transactional(new ReconciliationMatchingTransactionService(
                            matchingRepository, clock), transactionManager),
                    transactional(new ReconciliationMatchingCompletionService(
                            matchingRepository, clock), transactionManager)).match(reconId, 1);
            assertEquals(1, matched.matchedRows());
            assertEquals(0, matched.differenceRows());
            assertEquals(1, matched.franchiseEligibleRows());

            SettlementRepository settlements = new JdbcSettlementRepository(jdbc);
            JdbcOperationAuditRecorder audits = new JdbcOperationAuditRecorder(jdbc);
            SettlementGenerationService generation = transactional(
                    new SettlementGenerationService(settlements, new BusinessIdGenerator(clock),
                            clock, audits), transactionManager);
            long reconVersion = jdbc.queryForObject(
                    "SELECT version FROM qh_recon_batch WHERE id=?", Long.class, reconId);
            SettlementGenerationResult generated = generation.generate(imported.reconBatchNo(),
                    new SettlementGenerateCommand(reconVersion, "系统旅程生成结算",
                            "FIN-WP10", "SET-GEN-JOURNEY-001"));
            assertEquals(SettlementGenerationOutcome.CREATED, generated.outcome());
            assertEquals(1, generated.batch().detailCount());
            assertEquals(350, generated.batch().totalFen());

            SettlementConfirmationService confirmation = transactional(
                    new SettlementConfirmationService(settlements, clock, audits),
                    transactionManager);
            SettlementBatch confirmed = confirmation.confirm(generated.batch().batchNo(),
                    new SettlementConfirmCommand(generated.batch().version(), 1, 350,
                            "系统旅程金额笔数一致", "FIN-WP10", "SET-CONF-JOURNEY-001"));
            assertEquals(SettlementBatchStatus.CONFIRMED, confirmed.status());
            SettlementExport export = transactional(
                    new SettlementExportService(settlements, clock, audits), transactionManager)
                    .export(confirmed.batchNo(), "FIN-WP10", "SET-EXPORT-JOURNEY-001");
            String csv = new String(export.content(), StandardCharsets.UTF_8);
            assertTrue(csv.contains(redeemed.redemptionNo()));
            assertTrue(csv.contains("QH-F001,青禾加盟一店"));
            assertTrue(csv.contains(",350,MATCHED,CONFIRMED"));
            assertFalse(csv.contains(rightCode));
            assertEquals(2, csv.split("\\r\\n").length);
            assertNotNull(export.fileName());
        } finally {
            if (redis != null) {
                long campaignId = campaignIdHolder[0];
                String stockKey = "qh:campaign:stock:" + campaignId;
                redis.delete(stockKey);
                redis.delete("qh:claim:request:" + campaignId + ":" + memberId + ":" + requestId);
                redis.delete("qh:claim:member:" + campaignId + ":" + memberId);
                redis.delete("qh:claim:reservation:pending:" + campaignId);
                java.util.Set<String> reservationKeys = redis.keys(
                        "qh:claim:reservation:" + campaignId + ":*");
                if (reservationKeys != null && !reservationKeys.isEmpty()) {
                    redis.delete(reservationKeys);
                }
            }
            if (redisFactory != null) {
                redisFactory.destroy();
            }
            dropDatabase(database);
        }
    }

    private static void seedIdentity(JdbcTemplate jdbc, long memberId, long storeId) {
        jdbc.update("INSERT INTO qh_store (id,external_store_code,name,ownership_type,status,"
                        + "source_version,version,created_at,updated_at) VALUES "
                        + "(?,'QH-F001','青禾加盟一店','FRANCHISE','ACTIVE','wp10',0,?,?)",
                storeId, NOW, NOW);
        jdbc.update("INSERT INTO qh_member_mapping (id,external_member_no,platform_user_id,"
                        + "status_snapshot,version,created_at,updated_at) VALUES "
                        + "(?,'MEM-JOURNEY',?,'ACTIVE',0,?,?)",
                memberId, memberId + 1000, NOW, NOW);
    }

    private static ReconciliationImportService importService(
            ReconciliationBatchRepository repository, PlatformTransactionManager manager,
            BusinessClock clock, AesGcmRightCodeProtector protector) {
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

    private static FilePair reconciliationFile(String redemptionNo, String rightCode)
            throws Exception {
        String batch = "POSB20260910001";
        String fileName = "POS_20260910_" + batch + ".csv";
        String header = "batch_no,business_date,store_code,terminal_no,pos_order_no,pos_request_no,"
                + "platform_redemption_no,right_code,operation_type,operation_status,occurred_at\r\n";
        String row = String.join(",", batch, "2026-09-10", "QH-F001", "T-JOURNEY",
                "ORDER-JOURNEY-001", "REDEEM-JOURNEY-001", redemptionNo, rightCode,
                "REDEEM", "SUCCESS", "2026-09-10T16:00:00+08:00") + "\r\n";
        byte[] csv = (header + row).getBytes(StandardCharsets.UTF_8);
        String manifest = "{\"provider\":\"MOCK_POS_VENDOR\",\"batchNo\":\"" + batch
                + "\",\"businessDate\":\"2026-09-10\",\"schemaVersion\":\"1.0\","
                + "\"fileName\":\"" + fileName + "\",\"rowCount\":1,"
                + "\"checksumAlgorithm\":\"SHA-256\",\"checksum\":\"" + sha256(csv)
                + "\",\"generatedAt\":\"2026-09-11T02:00:05+08:00\","
                + "\"correctionOfBatchNo\":null}";
        return new FilePair(manifest.getBytes(StandardCharsets.UTF_8), fileName, csv);
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

    private static void migrate(String databaseUrl) throws Exception {
        try (Connection connection = DriverManager.getConnection(
                databaseUrl, MYSQL_USER, MYSQL_PASSWORD)) {
            for (int version = 1; version <= 8; version++) {
                executeScript(connection, migration(version));
            }
        }
    }

    private static String migration(int version) {
        return String.format(Locale.ROOT, "db/qinghe/migration/V%03d__%s.sql", version,
                new String[]{"create_qinghe_core", "create_identity_and_store_support",
                        "add_campaign_publication_and_reviews", "add_claim_outbox_delivery_support",
                        "add_entitlement_issue_support", "add_pos_redemption_support",
                        "add_reversal_idempotency_and_audit",
                        "add_reconciliation_import_support"}[version - 1]);
    }

    private static String sha256(byte[] source) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(source);
        StringBuilder value = new StringBuilder(64);
        for (byte item : digest) {
            value.append(String.format(Locale.ROOT, "%02x", item & 0xff));
        }
        return value.toString();
    }

    private static void createDatabase(String database) throws SQLException {
        executeHost("CREATE DATABASE " + database
                + " CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
    }

    private static void dropDatabase(String database) throws SQLException {
        if (database.matches("qh_wp10_journey_[a-f0-9]{12}")) {
            executeHost("DROP DATABASE IF EXISTS " + database);
        }
    }

    private static void executeHost(String sql) throws SQLException {
        try (Connection connection = DriverManager.getConnection(
                MYSQL_HOST_URL, MYSQL_USER, MYSQL_PASSWORD);
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static void executeScript(Connection connection, String resource) throws Exception {
        for (String statementText : resource(resource).split(";")) {
            if (!statementText.trim().isEmpty()) {
                try (Statement statement = connection.createStatement()) {
                    statement.execute(statementText.trim());
                }
            }
        }
    }

    private static String resource(String name) throws IOException {
        try (InputStream input = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream(name)) {
            if (input == null) {
                throw new IllegalArgumentException("missing classpath resource " + name);
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                output.write(buffer, 0, read);
            }
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static String credential(String property, String environment) {
        String value = System.getProperty(property);
        if (value == null || value.isEmpty()) {
            value = System.getenv(environment);
        }
        return value == null ? "" : value;
    }

    private static final class FilePair {
        private final byte[] manifest;
        private final String fileName;
        private final byte[] csv;

        private FilePair(byte[] manifest, String fileName, byte[] csv) {
            this.manifest = manifest;
            this.fileName = fileName;
            this.csv = csv;
        }
    }
}
