package com.qinghe.marketing.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qinghe.marketing.claim.ClaimAcceptanceTransactionService;
import com.qinghe.marketing.claim.ClaimRequest;
import com.qinghe.marketing.claim.ClaimReservationCommand;
import com.qinghe.marketing.claim.ClaimReservationResult;
import com.qinghe.marketing.claim.JdbcClaimRequestRepository;
import com.qinghe.marketing.claim.JdbcOutboxEventRepository;
import com.qinghe.marketing.claim.RedisClaimReservationStore;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Local capacity evidence for the Redis reservation plus synchronous ClaimRequest/Outbox write path. */
class QingheWp04CapacityIT {

    private static final String MYSQL_HOST_URL = System.getProperty("qinghe.it.mysql.url",
            "jdbc:mysql://127.0.0.1:3306/?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true");
    private static final String MYSQL_USER = System.getProperty("qinghe.it.mysql.user", "root");
    private static final String MYSQL_PASSWORD = credential("qinghe.it.mysql.password", "QINGHE_IT_MYSQL_PASSWORD");
    private static final String REDIS_HOST = System.getProperty("qinghe.it.redis.host", "127.0.0.1");
    private static final int REDIS_PORT = Integer.getInteger("qinghe.it.redis.port", 6379);
    private static final String REDIS_PASSWORD = credential("qinghe.it.redis.password", "QINGHE_IT_REDIS_PASSWORD");
    private static final int WARM_UP = 20;
    private static final int SAMPLE_SIZE = 300;
    private static final int CONCURRENCY = 16;

    @Test
    void shouldMeasureClaimAcceptanceCapacityWithoutBorrowingLegacyNumbers() throws Exception {
        assertTrue(!MYSQL_PASSWORD.isEmpty(), "Set QINGHE_IT_MYSQL_PASSWORD");
        assertTrue(!REDIS_PASSWORD.isEmpty(), "Set QINGHE_IT_REDIS_PASSWORD");
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12)
                .toLowerCase(Locale.ROOT);
        String database = "qh_wp04_cap_" + suffix;
        if (!database.matches("qh_wp04_cap_[a-f0-9]{12}")) {
            throw new IllegalStateException("unsafe temporary database name");
        }
        long campaignId = Math.abs(UUID.randomUUID().getMostSignificantBits() % 800000000L) + 100000000L;
        List<String> redisKeys = redisKeys(campaignId, WARM_UP + SAMPLE_SIZE);

        createDatabase(database);
        HikariDataSource dataSource = null;
        LettuceConnectionFactory redisFactory = null;
        StringRedisTemplate redis = null;
        try {
            String databaseUrl = MYSQL_HOST_URL.replace("/?", "/" + database + "?");
            try (Connection connection = DriverManager.getConnection(databaseUrl, MYSQL_USER, MYSQL_PASSWORD)) {
                executeScript(connection, "db/qinghe/migration/V001__create_qinghe_core.sql");
                executeScript(connection, "db/qinghe/migration/V002__create_identity_and_store_support.sql");
                executeScript(connection, "db/qinghe/migration/V003__add_campaign_publication_and_reviews.sql");
                executeScript(connection, "db/qinghe/migration/V004__add_claim_outbox_delivery_support.sql");
            }
            dataSource = dataSource(databaseUrl);
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            seed(jdbc, campaignId, WARM_UP + SAMPLE_SIZE);

            RedisStandaloneConfiguration redisConfiguration =
                    new RedisStandaloneConfiguration(REDIS_HOST, REDIS_PORT);
            redisConfiguration.setPassword(RedisPassword.of(REDIS_PASSWORD));
            redisFactory = new LettuceConnectionFactory(redisConfiguration);
            redisFactory.afterPropertiesSet();
            redis = new StringRedisTemplate(redisFactory);
            redis.afterPropertiesSet();
            redis.opsForValue().set(stockKey(campaignId), String.valueOf(WARM_UP + SAMPLE_SIZE));

            TransactionTemplate transaction = new TransactionTemplate(
                    new DataSourceTransactionManager(dataSource));
            ClaimAcceptanceTransactionService acceptance = new ClaimAcceptanceTransactionService(
                    new JdbcClaimRequestRepository(jdbc), new JdbcOutboxEventRepository(jdbc),
                    new ObjectMapper());
            RedisClaimReservationStore reservations = new RedisClaimReservationStore(redis);
            LocalDateTime now = LocalDateTime.of(2026, 9, 9, 16, 0);

            for (int index = 0; index < WARM_UP; index++) {
                acceptOne(index, campaignId, reservations, acceptance, transaction, now);
            }

            CapacityResult result = measure(campaignId, reservations, acceptance, transaction, now);
            assertEquals(0, result.errors);
            assertEquals(SAMPLE_SIZE + WARM_UP, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM qh_claim_request", Integer.class));
            assertEquals(SAMPLE_SIZE + WARM_UP, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM qh_outbox_event", Integer.class));
            assertEquals(SAMPLE_SIZE + WARM_UP, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM qh_claim_request WHERE status = 'PROCESSING'", Integer.class));
            assertEquals(SAMPLE_SIZE + WARM_UP, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM qh_outbox_event WHERE status = 'NEW'", Integer.class));
            assertEquals("0", redis.opsForValue().get(stockKey(campaignId)));
            System.out.println(result.line(
                    jdbc.queryForObject("SELECT VERSION()", String.class),
                    redis.getConnectionFactory().getConnection().serverCommands().info("server")
                            .getProperty("redis_version")));
        } finally {
            if (redis != null) {
                redis.delete(redisKeys);
            }
            if (redisFactory != null) {
                redisFactory.destroy();
            }
            if (dataSource != null) {
                dataSource.close();
            }
            dropDatabase(database);
        }
    }

    private CapacityResult measure(long campaignId, RedisClaimReservationStore reservations,
                                   ClaimAcceptanceTransactionService acceptance,
                                   TransactionTemplate transaction, LocalDateTime now) throws Exception {
        ExecutorService workers = Executors.newFixedThreadPool(CONCURRENCY);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger errors = new AtomicInteger();
        List<Future<Long>> futures = new ArrayList<Future<Long>>(SAMPLE_SIZE);
        try {
            for (int sample = 0; sample < SAMPLE_SIZE; sample++) {
                final int index = sample + WARM_UP;
                futures.add(workers.submit(new Callable<Long>() {
                    @Override
                    public Long call() throws Exception {
                        start.await();
                        long started = System.nanoTime();
                        try {
                            acceptOne(index, campaignId, reservations, acceptance, transaction, now);
                            return System.nanoTime() - started;
                        } catch (RuntimeException failure) {
                            errors.incrementAndGet();
                            throw failure;
                        }
                    }
                }));
            }
            long wallStarted = System.nanoTime();
            start.countDown();
            List<Long> latencies = new ArrayList<Long>(SAMPLE_SIZE);
            for (Future<Long> future : futures) {
                latencies.add(future.get(30, TimeUnit.SECONDS));
            }
            long wallNanos = System.nanoTime() - wallStarted;
            Collections.sort(latencies);
            return new CapacityResult(latencies, wallNanos, errors.get());
        } finally {
            workers.shutdownNow();
            workers.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private void acceptOne(int index, long campaignId, RedisClaimReservationStore reservations,
                           ClaimAcceptanceTransactionService acceptance,
                           TransactionTemplate transaction, LocalDateTime now) {
        long memberId = 1000L + index;
        String serial = String.format(Locale.ROOT, "%04d", index);
        String requestId = "REQ-CAP-" + serial;
        String reservationId = "RSV-CAP-" + serial;
        String claimNo = "CLM-CAP-" + serial;
        String eventId = "EVT-CAP-" + serial;
        ClaimReservationResult reservation = reservations.reserve(new ClaimReservationCommand(
                campaignId, memberId, requestId, repeat((char) ('a' + index % 6), 64),
                reservationId, claimNo, eventId, now, now.minusMinutes(1), now.plusHours(1)));
        if (reservation.outcome() != ClaimReservationResult.Outcome.RESERVED) {
            throw new IllegalStateException("unexpected reservation outcome " + reservation.outcome());
        }
        ClaimRequest claim = transaction.execute(status -> acceptance.accept(
                campaignId, memberId, requestId, repeat((char) ('a' + index % 6), 64),
                reservationId, claimNo, eventId, now));
        if (claim == null) {
            throw new IllegalStateException("claim transaction returned null");
        }
        reservations.markPersisted(campaignId, reservationId);
    }

    private void seed(JdbcTemplate jdbc, long campaignId, int memberCount) {
        LocalDateTime now = LocalDateTime.of(2026, 9, 9, 16, 0);
        jdbc.update("INSERT INTO qh_benefit_template (id, template_no, type, title, rules_snapshot, "
                        + "validity_type, validity_value, status, version, created_at, updated_at) "
                        + "VALUES (1, 'TPL-CAP', 'FREE_PRODUCT', '容量测试券', CAST('{}' AS JSON), "
                        + "'RELATIVE_DAYS', 7, 'ACTIVE', 0, ?, ?)", now, now);
        jdbc.update("INSERT INTO qh_campaign (id, campaign_no, template_id, name, description, status, "
                        + "begin_at, end_at, member_claim_limit, franchise_subsidy_fen, rule_version, "
                        + "version, created_by, created_at, updated_at) VALUES (?, 'CAM-CAP', 1, '容量测试', "
                        + "'本机证据', 'ACTIVE', ?, ?, 1, 300, 1, 0, 'TEST', ?, ?)",
                campaignId, now.minusMinutes(1), now.plusHours(1), now, now);
        List<Object[]> members = new ArrayList<Object[]>(memberCount);
        for (int index = 0; index < memberCount; index++) {
            long memberId = 1000L + index;
            members.add(new Object[]{memberId, "MEM-CAP-" + index, 100000L + index, now, now});
        }
        jdbc.batchUpdate("INSERT INTO qh_member_mapping (id, external_member_no, platform_user_id, "
                        + "status_snapshot, version, created_at, updated_at) VALUES (?, ?, ?, 'ACTIVE', 0, ?, ?)",
                members);
    }

    private static HikariDataSource dataSource(String databaseUrl) {
        HikariConfig configuration = new HikariConfig();
        configuration.setJdbcUrl(databaseUrl);
        configuration.setUsername(MYSQL_USER);
        configuration.setPassword(MYSQL_PASSWORD);
        configuration.setMaximumPoolSize(CONCURRENCY);
        configuration.setMinimumIdle(4);
        configuration.setPoolName("qh-wp04-capacity");
        return new HikariDataSource(configuration);
    }

    private static List<String> redisKeys(long campaignId, int count) {
        List<String> keys = new ArrayList<String>(count * 3 + 2);
        keys.add(stockKey(campaignId));
        keys.add("qh:claim:reservation:pending:" + campaignId);
        for (int index = 0; index < count; index++) {
            String serial = String.format(Locale.ROOT, "%04d", index);
            long memberId = 1000L + index;
            keys.add("qh:claim:request:" + campaignId + ":" + memberId + ":REQ-CAP-" + serial);
            keys.add("qh:claim:member:" + campaignId + ":" + memberId);
            keys.add("qh:claim:reservation:" + campaignId + ":RSV-CAP-" + serial);
        }
        return keys;
    }

    private static String stockKey(long campaignId) {
        return "qh:campaign:stock:" + campaignId;
    }

    private static String repeat(char value, int count) {
        StringBuilder result = new StringBuilder(count);
        for (int index = 0; index < count; index++) {
            result.append(value);
        }
        return result.toString();
    }

    private static void createDatabase(String database) throws SQLException {
        try (Connection host = DriverManager.getConnection(MYSQL_HOST_URL, MYSQL_USER, MYSQL_PASSWORD);
             Statement statement = host.createStatement()) {
            statement.execute("CREATE DATABASE " + database
                    + " CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
        }
    }

    private static void dropDatabase(String database) throws SQLException {
        try (Connection host = DriverManager.getConnection(MYSQL_HOST_URL, MYSQL_USER, MYSQL_PASSWORD);
             Statement statement = host.createStatement()) {
            statement.execute("DROP DATABASE IF EXISTS " + database);
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

    private static String resource(String name) throws IOException {
        try (InputStream input = QingheWp04CapacityIT.class.getClassLoader().getResourceAsStream(name)) {
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

    private static final class CapacityResult {
        private final List<Long> latencies;
        private final long wallNanos;
        private final int errors;
        private CapacityResult(List<Long> latencies, long wallNanos, int errors) {
            this.latencies = latencies;
            this.wallNanos = wallNanos;
            this.errors = errors;
        }
        private String line(String mysqlVersion, String redisVersion) {
            double seconds = wallNanos / 1_000_000_000.0;
            return String.format(Locale.ROOT,
                    "WP04_CAPACITY_RESULT total=%d concurrency=%d success=%d error=%d "
                            + "throughput_rps=%.2f p50_ms=%.2f p95_ms=%.2f p99_ms=%.2f max_ms=%.2f "
                            + "processors=%d java=%s mysql=%s redis=%s",
                    SAMPLE_SIZE, CONCURRENCY, SAMPLE_SIZE - errors, errors,
                    (SAMPLE_SIZE - errors) / seconds, millis(percentile(0.50)),
                    millis(percentile(0.95)), millis(percentile(0.99)),
                    millis(latencies.get(latencies.size() - 1)),
                    Runtime.getRuntime().availableProcessors(), System.getProperty("java.version"),
                    mysqlVersion, redisVersion);
        }
        private long percentile(double percentile) {
            int index = Math.max(0, (int) Math.ceil(latencies.size() * percentile) - 1);
            return latencies.get(index);
        }
        private static double millis(long nanos) {
            return nanos / 1_000_000.0;
        }
    }
}
