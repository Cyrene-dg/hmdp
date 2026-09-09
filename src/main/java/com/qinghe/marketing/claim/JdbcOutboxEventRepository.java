package com.qinghe.marketing.claim;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Repository
public class JdbcOutboxEventRepository implements OutboxEventRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcOutboxEventRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void insert(OutboxEvent event) {
        jdbcTemplate.update("INSERT INTO qh_outbox_event (event_id, aggregate_type, aggregate_id, "
                        + "event_type, event_version, payload, status, retry_count, next_retry_at, "
                        + "lease_until, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, CAST(? AS JSON), ?, 0, ?, NULL, ?, ?)",
                event.eventId(), event.aggregateType(), event.aggregateId(), event.eventType(),
                event.eventVersion(), event.payload(), event.status().name(), event.createdAt(),
                event.createdAt(), event.createdAt());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<LeasedOutboxEvent> leaseBatch(String leaseOwner, LocalDateTime now,
                                               LocalDateTime leaseUntil, int limit) {
        if (limit <= 0) {
            return Collections.emptyList();
        }
        List<Long> ids = jdbcTemplate.query(
                "SELECT id FROM qh_outbox_event WHERE "
                        + "((status IN ('NEW', 'RETRY') AND (next_retry_at IS NULL OR next_retry_at <= ?)) "
                        + "OR (status = 'PUBLISHING' AND lease_until < ?)) "
                        + "ORDER BY id LIMIT ? FOR UPDATE SKIP LOCKED",
                new Object[]{now, now, limit},
                (resultSet, rowNum) -> resultSet.getLong("id"));
        if (ids.isEmpty()) {
            return Collections.emptyList();
        }
        for (Long id : ids) {
            jdbcTemplate.update("UPDATE qh_outbox_event SET status = 'PUBLISHING', lease_owner = ?, "
                            + "lease_until = ?, updated_at = ? WHERE id = ?",
                    leaseOwner, leaseUntil, now, id);
        }
        List<LeasedOutboxEvent> leased = new ArrayList<LeasedOutboxEvent>(ids.size());
        for (Long id : ids) {
            leased.add(jdbcTemplate.queryForObject(
                    "SELECT id, event_id, aggregate_type, aggregate_id, event_type, event_version, "
                            + "CAST(payload AS CHAR) AS payload_text, retry_count, lease_owner "
                            + "FROM qh_outbox_event WHERE id = ?",
                    new Object[]{id}, (resultSet, rowNum) -> new LeasedOutboxEvent(
                            resultSet.getLong("id"), resultSet.getString("event_id"),
                            resultSet.getString("aggregate_type"), resultSet.getString("aggregate_id"),
                            resultSet.getString("event_type"), resultSet.getLong("event_version"),
                            resultSet.getString("payload_text"), resultSet.getInt("retry_count"),
                            resultSet.getString("lease_owner"))));
        }
        return leased;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OutboxStatus completePublication(String eventId, String leaseOwner, boolean acknowledged,
                                             String errorMessage, LocalDateTime nextRetryAt,
                                             int maxRetries, LocalDateTime now) {
        List<Integer> retries = jdbcTemplate.query(
                "SELECT retry_count FROM qh_outbox_event WHERE event_id = ? AND status = 'PUBLISHING' "
                        + "AND lease_owner = ? FOR UPDATE",
                new Object[]{eventId, leaseOwner},
                (resultSet, rowNum) -> resultSet.getInt("retry_count"));
        if (retries.isEmpty()) {
            throw new IllegalStateException("outbox lease is no longer owned by " + leaseOwner);
        }
        int attemptNo = jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(attempt_no), 0) + 1 FROM qh_outbox_delivery_attempt WHERE event_id = ?",
                Integer.class, eventId);
        OutboxStatus nextStatus;
        int nextRetryCount = retries.get(0);
        if (acknowledged) {
            nextStatus = OutboxStatus.PUBLISHED;
            jdbcTemplate.update("UPDATE qh_outbox_event SET status = 'PUBLISHED', lease_owner = NULL, "
                            + "lease_until = NULL, last_error = NULL, published_at = ?, updated_at = ? "
                            + "WHERE event_id = ? AND lease_owner = ?",
                    now, now, eventId, leaseOwner);
        } else {
            nextRetryCount++;
            nextStatus = nextRetryCount >= maxRetries ? OutboxStatus.DEAD : OutboxStatus.RETRY;
            jdbcTemplate.update("UPDATE qh_outbox_event SET status = ?, retry_count = ?, "
                            + "next_retry_at = ?, lease_owner = NULL, lease_until = NULL, last_error = ?, "
                            + "updated_at = ? WHERE event_id = ? AND lease_owner = ?",
                    nextStatus.name(), nextRetryCount,
                    nextStatus == OutboxStatus.DEAD ? null : nextRetryAt,
                    truncate(errorMessage), now, eventId, leaseOwner);
        }
        jdbcTemplate.update("INSERT INTO qh_outbox_delivery_attempt "
                        + "(event_id, attempt_no, outcome, error_message, attempted_at) VALUES (?, ?, ?, ?, ?)",
                eventId, attemptNo, acknowledged ? "ACK" : "NACK", truncate(errorMessage), now);
        return nextStatus;
    }

    private static String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= 512 ? value : value.substring(0, 512);
    }
}
