package com.qinghe.marketing.entitlement;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public class JdbcClaimIssueDeliveryRepository implements ClaimIssueDeliveryRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcClaimIssueDeliveryRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void record(String eventId, long claimId, String outcome, String failureCode,
                       LocalDateTime processedAt) {
        if (!sourceEventMatches(eventId, claimId)) {
            throw new IllegalArgumentException("issue event does not belong to the claim");
        }
        jdbcTemplate.update("INSERT INTO qh_claim_issue_delivery "
                        + "(event_id, claim_id, outcome, failure_code, processed_at, created_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE event_id = event_id",
                eventId, claimId, outcome, failureCode, processedAt, processedAt);
    }

    @Override
    public boolean sourceEventMatches(String eventId, long claimId) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM qh_outbox_event o "
                        + "JOIN qh_claim_request c ON c.claim_no = o.aggregate_id "
                        + "WHERE o.event_id = ? AND c.id = ? AND o.aggregate_type = 'CLAIM_REQUEST' "
                        + "AND o.event_type = 'CLAIM_ACCEPTED'",
                Integer.class, eventId, claimId);
        return count != null && count == 1;
    }

    @Override
    public boolean exists(String eventId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM qh_claim_issue_delivery WHERE event_id = ?",
                Integer.class, eventId);
        return count != null && count > 0;
    }
}
