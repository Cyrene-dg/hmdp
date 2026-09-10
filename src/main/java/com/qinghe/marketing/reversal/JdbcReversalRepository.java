package com.qinghe.marketing.reversal;

import com.qinghe.marketing.entitlement.EntitlementStatus;
import com.qinghe.marketing.redemption.RedemptionStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class JdbcReversalRepository implements ReversalRepository {

    private static final String REQUEST_SELECT = "SELECT q.id, q.pos_request_no, q.request_digest, "
            + "q.target_redemption_no, q.status, v.reversal_no, q.failure_code, q.right_status, "
            + "q.first_processed_at, q.version FROM qh_pos_reversal_request q "
            + "LEFT JOIN qh_redemption_reversal v ON v.id = q.reversal_id "
            + "WHERE q.pos_client_id = ? AND q.pos_request_no = ?";
    private final JdbcTemplate jdbc;

    public JdbcReversalRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public void createRequestIfAbsent(String clientId, String requestNo, String digest,
                                      String redemptionNo, long storeId, LocalDateTime now) {
        jdbc.update("INSERT IGNORE INTO qh_pos_reversal_request (pos_client_id, pos_request_no, "
                        + "request_digest, target_redemption_no, store_id, status, version, created_at, "
                        + "updated_at) VALUES (?, ?, ?, ?, ?, 'PROCESSING', 0, ?, ?)",
                clientId, requestNo, digest, redemptionNo, storeId, now, now);
    }

    @Override
    public Optional<PosReversalRequest> findRequestForUpdate(String clientId, String requestNo) {
        return first(jdbc.query(REQUEST_SELECT + " FOR UPDATE", new Object[]{clientId, requestNo},
                requestMapper()));
    }

    @Override
    public Optional<ReversibleRedemption> findRedemptionForUpdate(String redemptionNo) {
        return first(jdbc.query("SELECT id, redemption_no, pos_order_no, entitlement_id, store_id, "
                        + "status, occurred_at, version FROM qh_redemption WHERE redemption_no = ? FOR UPDATE",
                new Object[]{redemptionNo}, (rs, rowNum) -> new ReversibleRedemption(
                        rs.getLong("id"), rs.getString("redemption_no"), rs.getString("pos_order_no"),
                        rs.getLong("entitlement_id"), rs.getLong("store_id"),
                        RedemptionStatus.valueOf(rs.getString("status")),
                        rs.getObject("occurred_at", LocalDateTime.class), rs.getLong("version"))));
    }

    @Override
    public Optional<ReversalEntitlement> findEntitlementForUpdate(long entitlementId) {
        return first(jdbc.query("SELECT id, status, valid_until, version FROM qh_member_entitlement "
                        + "WHERE id = ? FOR UPDATE", new Object[]{entitlementId},
                (rs, rowNum) -> new ReversalEntitlement(rs.getLong("id"),
                        EntitlementStatus.valueOf(rs.getString("status")),
                        rs.getObject("valid_until", LocalDateTime.class), rs.getLong("version"))));
    }

    @Override
    public boolean isSettlementLocked(long redemptionId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM qh_settlement_detail d "
                        + "LEFT JOIN qh_settlement_batch b ON b.id = d.settlement_batch_id "
                        + "WHERE d.redemption_id = ? AND (d.status = 'CONFIRMED' OR d.confirmed_at IS NOT NULL "
                        + "OR b.status = 'CONFIRMED' OR b.confirmed_at IS NOT NULL)",
                new Object[]{redemptionId}, Integer.class);
        return count != null && count > 0;
    }

    @Override
    public void markRedemptionReversed(long redemptionId, long version, LocalDateTime now) {
        requireOne(jdbc.update("UPDATE qh_redemption SET status = 'REVERSED', version = version + 1, "
                        + "updated_at = ? WHERE id = ? AND version = ? AND status = 'SUCCESS'",
                now, redemptionId, version), "redemption reversal state gate was lost");
    }

    @Override
    public void restoreEntitlement(long entitlementId, long version, EntitlementStatus target,
                                   LocalDateTime now) {
        requireOne(jdbc.update("UPDATE qh_member_entitlement SET status = ?, version = version + 1, "
                        + "updated_at = ? WHERE id = ? AND version = ? AND status = 'USED'",
                target.name(), now, entitlementId, version), "entitlement reversal state gate was lost");
    }

    @Override
    public void cancelSubsidyCandidate(long redemptionId, LocalDateTime now) {
        jdbc.update("UPDATE qh_subsidy_candidate SET status = 'CANCELLED', updated_at = ? "
                        + "WHERE redemption_id = ? AND status IN ('UNRECONCILED', 'MATCHED')",
                now, redemptionId);
    }

    @Override
    public void cancelPendingSettlementDetail(long redemptionId, LocalDateTime now) {
        List<PendingDetail> rows = jdbc.query("SELECT id, settlement_batch_id, subsidy_fen, status "
                        + "FROM qh_settlement_detail WHERE redemption_id = ? FOR UPDATE",
                new Object[]{redemptionId}, (rs, rowNum) -> new PendingDetail(rs.getLong("id"),
                        (Long) rs.getObject("settlement_batch_id"), rs.getLong("subsidy_fen"),
                        rs.getString("status")));
        if (rows.isEmpty() || "CANCELLED".equals(rows.get(0).status)) return;
        PendingDetail detail = rows.get(0);
        if (!"PENDING_CONFIRM".equals(detail.status)) {
            throw new IllegalStateException("unsupported settlement detail state during reversal");
        }
        if (detail.batchId != null) {
            int changed = jdbc.update("UPDATE qh_settlement_batch SET status = 'DRAFT', "
                            + "detail_count = detail_count - 1, total_fen = total_fen - ?, "
                            + "version = version + 1, updated_at = ? WHERE id = ? "
                            + "AND status IN ('DRAFT', 'PENDING_CONFIRM') AND detail_count > 0 "
                            + "AND total_fen >= ?",
                    detail.subsidyFen, now, detail.batchId, detail.subsidyFen);
            requireOne(changed, "settlement batch became locked during reversal");
        }
        requireOne(jdbc.update("UPDATE qh_settlement_detail SET status = 'CANCELLED', "
                        + "settlement_batch_id = NULL, updated_at = ? WHERE id = ? "
                        + "AND status = 'PENDING_CONFIRM'", now, detail.id),
                "settlement detail reversal state gate was lost");
    }

    @Override
    public RedemptionReversal insertReversal(RedemptionReversal reversal, LocalDateTime now) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO qh_redemption_reversal (reversal_no, pos_client_id, pos_request_no, "
                            + "request_digest, redemption_id, status, reason_code, operator_no, "
                            + "reason_remark, occurred_at, first_processed_at, created_at, updated_at) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, reversal.reversalNo());
            statement.setString(2, reversal.posClientId());
            statement.setString(3, reversal.posRequestNo());
            statement.setString(4, reversal.requestDigest());
            statement.setLong(5, reversal.redemptionId());
            statement.setString(6, reversal.status().name());
            statement.setString(7, reversal.reasonCode().name());
            statement.setString(8, reversal.operatorNo());
            statement.setString(9, reversal.reasonRemark());
            statement.setObject(10, reversal.occurredAt());
            statement.setObject(11, reversal.firstProcessedAt());
            statement.setObject(12, now);
            statement.setObject(13, now);
            return statement;
        }, keys);
        Number id = keys.getKey();
        if (id == null) throw new IllegalStateException("reversal insert did not return an id");
        return new RedemptionReversal(id.longValue(), reversal.reversalNo(),
                reversal.posClientId(), reversal.posRequestNo(), reversal.requestDigest(),
                reversal.redemptionId(), reversal.status(), reversal.reasonCode(),
                reversal.operatorNo(), reversal.reasonRemark(), reversal.occurredAt(),
                reversal.firstProcessedAt());
    }

    @Override
    public void completeSuccess(long requestId, long version, long redemptionId, long reversalId,
                                EntitlementStatus rightStatus, LocalDateTime now) {
        requireOne(jdbc.update("UPDATE qh_pos_reversal_request SET status = 'SUCCESS', "
                        + "redemption_id = ?, reversal_id = ?, right_status = ?, first_processed_at = ?, "
                        + "version = version + 1, updated_at = ? WHERE id = ? AND version = ? "
                        + "AND status = 'PROCESSING'", redemptionId, reversalId, rightStatus.name(),
                now, now, requestId, version), "reversal request success state gate was lost");
    }

    @Override
    public void completeFailure(long requestId, long version, String failureCode,
                                EntitlementStatus rightStatus, LocalDateTime now) {
        requireOne(jdbc.update("UPDATE qh_pos_reversal_request SET status = 'FAILED', failure_code = ?, "
                        + "right_status = ?, first_processed_at = ?, version = version + 1, updated_at = ? "
                        + "WHERE id = ? AND version = ? AND status = 'PROCESSING'", failureCode,
                rightStatus == null ? null : rightStatus.name(), now, now, requestId, version),
                "reversal request failure state gate was lost");
    }

    private RowMapper<PosReversalRequest> requestMapper() {
        return (rs, rowNum) -> new PosReversalRequest(rs.getLong("id"),
                rs.getString("pos_request_no"), rs.getString("request_digest"),
                rs.getString("target_redemption_no"),
                ReversalRequestStatus.valueOf(rs.getString("status")),
                rs.getString("reversal_no"), rs.getString("failure_code"),
                nullableStatus(rs.getString("right_status")),
                rs.getObject("first_processed_at", LocalDateTime.class),
                rs.getLong("version"));
    }

    private static EntitlementStatus nullableStatus(String value) {
        return value == null ? null : EntitlementStatus.valueOf(value);
    }
    private static <T> Optional<T> first(List<T> values) { return values.stream().findFirst(); }
    private static void requireOne(int changed, String message) {
        if (changed != 1) throw new IllegalStateException(message);
    }
    private static final class PendingDetail {
        private final long id;
        private final Long batchId;
        private final long subsidyFen;
        private final String status;
        private PendingDetail(long id, Long batchId, long subsidyFen, String status) {
            this.id = id; this.batchId = batchId; this.subsidyFen = subsidyFen; this.status = status;
        }
    }
}
