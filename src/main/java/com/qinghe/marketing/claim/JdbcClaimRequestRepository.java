package com.qinghe.marketing.claim;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;

@Repository
public class JdbcClaimRequestRepository implements ClaimRequestRepository {

    private static final String COLUMNS = "id, claim_no, request_id, request_digest, campaign_id, "
            + "member_id, claim_cycle, reservation_id, status, failure_code, version, created_at, updated_at";

    private final JdbcTemplate jdbcTemplate;

    public JdbcClaimRequestRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public ClaimRequest insert(ClaimRequest claim) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO qh_claim_request (claim_no, request_id, request_digest, campaign_id, "
                            + "member_id, claim_cycle, reservation_id, status, failure_code, version, "
                            + "created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, claim.claimNo());
            statement.setString(2, claim.requestId());
            statement.setString(3, claim.requestDigest());
            statement.setLong(4, claim.campaignId());
            statement.setLong(5, claim.memberId());
            statement.setString(6, claim.claimCycle());
            statement.setString(7, claim.reservationId());
            statement.setString(8, claim.status().name());
            statement.setString(9, claim.failureCode());
            statement.setObject(10, claim.createdAt());
            statement.setObject(11, claim.updatedAt());
            return statement;
        }, keys);
        return findByReservationId(claim.reservationId()).orElseThrow(
                () -> new IllegalStateException("inserted claim request cannot be reloaded"));
    }

    @Override
    public Optional<ClaimRequest> findByMemberAndRequestId(long memberId, String requestId) {
        return find("member_id = ? AND request_id = ?", memberId, requestId);
    }

    @Override
    public Optional<ClaimRequest> findByReservationId(String reservationId) {
        return find("reservation_id = ?", reservationId);
    }

    @Override
    public Optional<ClaimRequest> findByClaimNoAndMemberId(String claimNo, long memberId) {
        return find("claim_no = ? AND member_id = ?", claimNo, memberId);
    }

    @Override
    public Optional<ClaimRequest> findByClaimNo(String claimNo) {
        return find("claim_no = ?", claimNo);
    }

    @Override
    public Optional<ClaimRequest> findByClaimNoForUpdate(String claimNo) {
        List<ClaimRequest> rows = jdbcTemplate.query("SELECT " + COLUMNS
                        + " FROM qh_claim_request WHERE claim_no = ? FOR UPDATE",
                new Object[]{claimNo}, mapper());
        return rows.stream().findFirst();
    }

    @Override
    public Optional<ClaimRequest> findByMemberCampaignAndCycle(long memberId, long campaignId,
                                                                String claimCycle) {
        return find("member_id = ? AND campaign_id = ? AND claim_cycle = ?",
                memberId, campaignId, claimCycle);
    }

    @Override
    public Optional<String> findEntitlementNo(long claimId) {
        List<String> rows = jdbcTemplate.query(
                "SELECT entitlement_no FROM qh_member_entitlement WHERE source_claim_id = ?",
                new Object[]{claimId}, (resultSet, rowNum) -> resultSet.getString("entitlement_no"));
        return rows.stream().findFirst();
    }

    @Override
    public boolean markSuccess(long claimId, long expectedVersion, LocalDateTime now) {
        return jdbcTemplate.update("UPDATE qh_claim_request SET status = 'SUCCESS', failure_code = NULL, "
                        + "version = version + 1, updated_at = ? WHERE id = ? AND status = 'PROCESSING' "
                        + "AND version = ?", now, claimId, expectedVersion) == 1;
    }

    @Override
    public boolean beginCompensation(long claimId, long expectedVersion, String failureCode,
                                     LocalDateTime now) {
        return jdbcTemplate.update("UPDATE qh_claim_request SET status = 'COMPENSATING', failure_code = ?, "
                        + "version = version + 1, updated_at = ? WHERE id = ? AND status = 'PROCESSING' "
                        + "AND version = ?", failureCode, now, claimId, expectedVersion) == 1;
    }

    @Override
    public boolean markFailed(long claimId, long expectedVersion, String failureCode,
                              LocalDateTime now) {
        return jdbcTemplate.update("UPDATE qh_claim_request SET status = 'FAILED', failure_code = ?, "
                        + "version = version + 1, updated_at = ? WHERE id = ? AND status = 'COMPENSATING' "
                        + "AND version = ?", failureCode, now, claimId, expectedVersion) == 1;
    }

    @Override
    public List<ClaimRequest> findCompensatingBefore(LocalDateTime cutoff, int limit) {
        if (limit <= 0) {
            return java.util.Collections.emptyList();
        }
        return jdbcTemplate.query("SELECT " + COLUMNS + " FROM qh_claim_request "
                        + "WHERE status = 'COMPENSATING' AND updated_at <= ? ORDER BY updated_at, id LIMIT ?",
                new Object[]{cutoff, limit}, mapper());
    }

    private Optional<ClaimRequest> find(String where, Object... parameters) {
        List<ClaimRequest> rows = jdbcTemplate.query("SELECT " + COLUMNS
                        + " FROM qh_claim_request WHERE " + where,
                parameters, mapper());
        return rows.stream().findFirst();
    }

    private org.springframework.jdbc.core.RowMapper<ClaimRequest> mapper() {
        return (resultSet, rowNum) -> new ClaimRequest(
                resultSet.getLong("id"), resultSet.getString("claim_no"),
                resultSet.getString("request_id"), resultSet.getString("request_digest"),
                resultSet.getLong("campaign_id"), resultSet.getLong("member_id"),
                resultSet.getString("claim_cycle"), resultSet.getString("reservation_id"),
                ClaimStatus.valueOf(resultSet.getString("status")),
                resultSet.getString("failure_code"), resultSet.getLong("version"),
                resultSet.getTimestamp("created_at").toLocalDateTime(),
                resultSet.getTimestamp("updated_at").toLocalDateTime());
    }
}
