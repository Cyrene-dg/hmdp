package com.qinghe.marketing.reconciliation;

import com.qinghe.marketing.store.StoreOwnershipType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class JdbcReconciliationMatchingRepository implements ReconciliationMatchingRepository {
    private static final String REDEMPTION_FACT = "SELECT r.id AS redemption_id, "
            + "NULL AS reversal_id, r.redemption_no, r.pos_request_no, r.pos_order_no, "
            + "s.external_store_code AS store_code, r.terminal_no, e.right_code_hash, "
            + "'SUCCESS' AS operation_status, r.status AS redemption_status, r.occurred_at, "
            + "s.ownership_type, c.id AS candidate_id, c.subsidy_fen, c.status AS candidate_status "
            + "FROM qh_redemption r JOIN qh_store s ON s.id = r.store_id "
            + "JOIN qh_member_entitlement e ON e.id = r.entitlement_id "
            + "LEFT JOIN qh_subsidy_candidate c ON c.redemption_id = r.id ";
    private static final String REVERSAL_FACT = "SELECT r.id AS redemption_id, "
            + "v.id AS reversal_id, r.redemption_no, v.pos_request_no, r.pos_order_no, "
            + "s.external_store_code AS store_code, r.terminal_no, e.right_code_hash, "
            + "v.status AS operation_status, r.status AS redemption_status, v.occurred_at, "
            + "s.ownership_type, c.id AS candidate_id, c.subsidy_fen, c.status AS candidate_status "
            + "FROM qh_redemption_reversal v JOIN qh_redemption r ON r.id = v.redemption_id "
            + "JOIN qh_store s ON s.id = r.store_id "
            + "JOIN qh_member_entitlement e ON e.id = r.entitlement_id "
            + "LEFT JOIN qh_subsidy_candidate c ON c.redemption_id = r.id ";
    private final JdbcTemplate jdbc;

    public JdbcReconciliationMatchingRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<ReconciliationBatch> findBatchForUpdate(long batchId) {
        return first(jdbc.query("SELECT id, recon_batch_no, provider, batch_no, business_date, "
                        + "checksum, status, total_rows, imported_rows, success_rows, error_rows, "
                        + "version FROM qh_recon_batch WHERE id = ? FOR UPDATE",
                new Object[]{batchId}, (rs, rowNum) -> new ReconciliationBatch(rs.getLong("id"),
                        rs.getString("recon_batch_no"), rs.getString("provider"),
                        rs.getString("batch_no"), rs.getDate("business_date").toLocalDate(),
                        rs.getString("checksum"),
                        ReconciliationBatchStatus.valueOf(rs.getString("status")),
                        rs.getInt("total_rows"), rs.getInt("imported_rows"),
                        rs.getInt("success_rows"), rs.getInt("error_rows"),
                        rs.getLong("version"))));
    }

    @Override
    public List<ReconciliationRecord> findUnmatchedForUpdate(long batchId, int limit) {
        return jdbc.query("SELECT id, batch_id, line_no, store_code, terminal_no, pos_order_no, "
                        + "pos_request_no, redemption_no, right_code_hash, operation_type, "
                        + "operation_status, occurred_at FROM qh_recon_record "
                        + "WHERE batch_id = ? AND match_status = 'UNMATCHED' "
                        + "ORDER BY line_no LIMIT ? FOR UPDATE",
                new Object[]{batchId, limit}, (rs, rowNum) -> new ReconciliationRecord(
                        rs.getLong("id"), rs.getLong("batch_id"), rs.getInt("line_no"),
                        rs.getString("store_code"), rs.getString("terminal_no"),
                        rs.getString("pos_order_no"), rs.getString("pos_request_no"),
                        rs.getString("redemption_no"), rs.getString("right_code_hash"),
                        rs.getString("operation_type"), rs.getString("operation_status"),
                        rs.getTimestamp("occurred_at").toLocalDateTime()));
    }

    @Override
    public Optional<PlatformOperationFact> findPlatformFact(ReconciliationRecord record) {
        if ("REDEEM".equals(record.operationType())) return findRedemptionFact(record);
        if ("REVERSE".equals(record.operationType())) return findReversalFact(record);
        return Optional.empty();
    }

    private Optional<PlatformOperationFact> findRedemptionFact(ReconciliationRecord record) {
        if (record.redemptionNo() != null) {
            Optional<PlatformOperationFact> byNumber = first(jdbc.query(REDEMPTION_FACT
                            + "WHERE r.redemption_no = ?", new Object[]{record.redemptionNo()},
                    factMapper()));
            if (byNumber.isPresent()) return byNumber;
        }
        Optional<PlatformOperationFact> byRequest = first(jdbc.query(REDEMPTION_FACT
                        + "WHERE r.pos_request_no = ? AND s.external_store_code = ?",
                new Object[]{record.posRequestNo(), record.storeCode()}, factMapper()));
        if (byRequest.isPresent()) return byRequest;
        return first(jdbc.query("SELECT NULL AS redemption_id, NULL AS reversal_id, "
                        + "NULL AS redemption_no, p.pos_request_no, NULL AS pos_order_no, "
                        + "s.external_store_code AS store_code, NULL AS terminal_no, "
                        + "NULL AS right_code_hash, p.status AS operation_status, "
                        + "NULL AS redemption_status, p.first_processed_at AS occurred_at, "
                        + "s.ownership_type, NULL AS candidate_id, NULL AS subsidy_fen, "
                        + "NULL AS candidate_status FROM qh_pos_redemption_request p "
                        + "JOIN qh_store s ON s.id = p.store_id "
                        + "WHERE p.pos_request_no = ? AND s.external_store_code = ?",
                new Object[]{record.posRequestNo(), record.storeCode()}, factMapper()));
    }

    private Optional<PlatformOperationFact> findReversalFact(ReconciliationRecord record) {
        Optional<PlatformOperationFact> byRequest = first(jdbc.query(REVERSAL_FACT
                        + "WHERE v.pos_request_no = ? AND s.external_store_code = ?",
                new Object[]{record.posRequestNo(), record.storeCode()}, factMapper()));
        if (byRequest.isPresent()) return byRequest;
        if (record.redemptionNo() != null) {
            Optional<PlatformOperationFact> byRedemption = first(jdbc.query(REVERSAL_FACT
                            + "WHERE r.redemption_no = ?", new Object[]{record.redemptionNo()},
                    factMapper()));
            if (byRedemption.isPresent()) return byRedemption;
        }
        return first(jdbc.query("SELECT p.redemption_id, p.reversal_id, "
                        + "p.target_redemption_no AS redemption_no, p.pos_request_no, "
                        + "r.pos_order_no, s.external_store_code AS store_code, r.terminal_no, "
                        + "e.right_code_hash, p.status AS operation_status, r.status AS redemption_status, "
                        + "p.first_processed_at AS occurred_at, s.ownership_type, "
                        + "c.id AS candidate_id, c.subsidy_fen, c.status AS candidate_status "
                        + "FROM qh_pos_reversal_request p JOIN qh_store s ON s.id = p.store_id "
                        + "LEFT JOIN qh_redemption r ON r.id = p.redemption_id "
                        + "LEFT JOIN qh_member_entitlement e ON e.id = r.entitlement_id "
                        + "LEFT JOIN qh_subsidy_candidate c ON c.redemption_id = r.id "
                        + "WHERE p.pos_request_no = ? AND s.external_store_code = ?",
                new Object[]{record.posRequestNo(), record.storeCode()}, factMapper()));
    }

    @Override
    public SettlementPreparationOutcome prepareSettlementDetail(long batchId,
                                                                 PlatformOperationFact fact,
                                                                 LocalDateTime now) {
        if (fact.candidateId() == null || fact.redemptionId() == null) {
            return SettlementPreparationOutcome.CONFLICT;
        }
        List<CandidateRow> candidates = jdbc.query("SELECT id, redemption_id, subsidy_fen, status "
                        + "FROM qh_subsidy_candidate WHERE id = ? FOR UPDATE",
                new Object[]{fact.candidateId()}, (rs, rowNum) -> new CandidateRow(
                        rs.getLong("id"), rs.getLong("redemption_id"),
                        rs.getLong("subsidy_fen"), rs.getString("status")));
        if (candidates.isEmpty()) return SettlementPreparationOutcome.CONFLICT;
        CandidateRow candidate = candidates.get(0);
        if ("CANCELLED".equals(candidate.status)) return SettlementPreparationOutcome.CANCELLED;
        if ("DIFFERENCE".equals(candidate.status)) return SettlementPreparationOutcome.CONFLICT;
        if ("UNRECONCILED".equals(candidate.status)) {
            int changed = jdbc.update("UPDATE qh_subsidy_candidate SET status = 'MATCHED', "
                            + "updated_at = ? WHERE id = ? AND status = 'UNRECONCILED'",
                    now, candidate.id);
            if (changed != 1) return SettlementPreparationOutcome.CONFLICT;
        } else if (!"MATCHED".equals(candidate.status)) {
            return SettlementPreparationOutcome.CONFLICT;
        }
        jdbc.update("INSERT IGNORE INTO qh_settlement_detail (settlement_batch_id, candidate_id, "
                        + "redemption_id, recon_batch_id, subsidy_fen, status, confirmed_at, "
                        + "created_at, updated_at) VALUES (NULL, ?, ?, ?, ?, 'PENDING_CONFIRM', "
                        + "NULL, ?, ?)", candidate.id, candidate.redemptionId, batchId,
                candidate.subsidyFen, now, now);
        List<DetailRow> details = jdbc.query("SELECT recon_batch_id, status FROM qh_settlement_detail "
                        + "WHERE candidate_id = ? FOR UPDATE", new Object[]{candidate.id},
                (rs, rowNum) -> new DetailRow(rs.getLong("recon_batch_id"), rs.getString("status")));
        if (details.size() == 1 && details.get(0).batchId == batchId
                && "PENDING_CONFIRM".equals(details.get(0).status)) {
            return SettlementPreparationOutcome.ELIGIBLE;
        }
        return SettlementPreparationOutcome.CONFLICT;
    }

    @Override
    public void markCandidateDifference(Long candidateId, LocalDateTime now) {
        if (candidateId == null) return;
        jdbc.update("UPDATE qh_subsidy_candidate SET status = 'DIFFERENCE', updated_at = ? "
                + "WHERE id = ? AND status = 'UNRECONCILED'", now, candidateId);
    }

    @Override
    public void markRecord(long recordId, ReconciliationMatchStatus status, String reason,
                           PlatformOperationFact fact, boolean settlementEligible,
                           LocalDateTime now) {
        int changed = jdbc.update("UPDATE qh_recon_record SET match_status = ?, match_reason = ?, "
                        + "matched_redemption_id = ?, matched_reversal_id = ?, store_ownership = ?, "
                        + "settlement_eligible = ?, matched_at = ?, updated_at = ? "
                        + "WHERE id = ? AND match_status = 'UNMATCHED'", status.name(), reason,
                fact == null ? null : fact.redemptionId(),
                fact == null ? null : fact.reversalId(),
                fact == null || fact.ownershipType() == null ? null : fact.ownershipType().name(),
                settlementEligible, now, now, recordId);
        if (changed != 1) throw new IllegalStateException("reconciliation record state gate was lost");
    }

    @Override
    public void insertDifference(long batchId, Long recordId, Long redemptionId, Long reversalId,
                                 ReconciliationMatchStatus type, String businessKey, String detail,
                                 LocalDateTime now) {
        jdbc.update("INSERT IGNORE INTO qh_recon_difference (batch_id, record_id, redemption_id, "
                        + "reversal_id, difference_type, business_key, detail, created_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)", batchId, recordId, redemptionId,
                reversalId, type.name(), businessKey, detail, now);
    }

    @Override
    public void insertPlatformOnlyDifferences(long batchId, LocalDate businessDate,
                                              LocalDateTime now) {
        jdbc.update("INSERT IGNORE INTO qh_recon_difference (batch_id, record_id, redemption_id, "
                        + "reversal_id, difference_type, business_key, detail, created_at) "
                        + "SELECT ?, NULL, r.id, NULL, 'DIFFERENCE_PLATFORM_ONLY', "
                        + "CONCAT('PLATFORM:REDEEM:', r.redemption_no), "
                        + "'platform redemption is absent from POS file', ? FROM qh_redemption r "
                        + "WHERE DATE(r.occurred_at) = ? AND NOT EXISTS (SELECT 1 FROM qh_recon_record x "
                        + "WHERE x.batch_id = ? AND x.matched_redemption_id = r.id "
                        + "AND x.operation_type = 'REDEEM')",
                batchId, now, businessDate, batchId);
        jdbc.update("UPDATE qh_subsidy_candidate c JOIN qh_redemption r ON r.id = c.redemption_id "
                        + "SET c.status = 'DIFFERENCE', c.updated_at = ? WHERE DATE(r.occurred_at) = ? "
                        + "AND r.status = 'SUCCESS' AND c.status = 'UNRECONCILED' "
                        + "AND NOT EXISTS (SELECT 1 FROM qh_recon_record x WHERE x.batch_id = ? "
                        + "AND x.matched_redemption_id = r.id AND x.operation_type = 'REDEEM')",
                now, businessDate, batchId);
        jdbc.update("INSERT IGNORE INTO qh_recon_difference (batch_id, record_id, redemption_id, "
                        + "reversal_id, difference_type, business_key, detail, created_at) "
                        + "SELECT ?, NULL, r.id, v.id, 'DIFFERENCE_PLATFORM_ONLY', "
                        + "CONCAT('PLATFORM:REVERSE:', v.reversal_no), "
                        + "'platform reversal is absent from POS file', ? "
                        + "FROM qh_redemption_reversal v JOIN qh_redemption r ON r.id = v.redemption_id "
                        + "WHERE DATE(v.occurred_at) = ? AND NOT EXISTS (SELECT 1 FROM qh_recon_record x "
                        + "WHERE x.batch_id = ? AND x.matched_reversal_id = v.id "
                        + "AND x.operation_type = 'REVERSE')",
                batchId, now, businessDate, batchId);
    }

    @Override
    public ReconciliationMatchSummary summarize(long batchId) {
        return jdbc.queryForObject("SELECT "
                        + "COALESCE(SUM(CASE WHEN match_status='UNMATCHED' THEN 1 ELSE 0 END),0) unmatched, "
                        + "COALESCE(SUM(CASE WHEN match_status IN ('MATCHED','MATCHED_REVERSAL') "
                        + "THEN 1 ELSE 0 END),0) matched, "
                        + "(SELECT COUNT(*) FROM qh_recon_difference d WHERE d.batch_id = ?) differences, "
                        + "COALESCE(SUM(CASE WHEN match_status='MATCHED' AND operation_type='REDEEM' "
                        + "AND operation_status='SUCCESS' AND store_ownership='DIRECT' THEN 1 ELSE 0 END),0) direct_matched, "
                        + "COALESCE(SUM(CASE WHEN settlement_eligible=1 THEN 1 ELSE 0 END),0) franchise_eligible "
                        + "FROM qh_recon_record WHERE batch_id = ?", new Object[]{batchId, batchId},
                (rs, rowNum) -> new ReconciliationMatchSummary(rs.getInt("unmatched"),
                        rs.getInt("matched"), rs.getInt("differences"),
                        rs.getInt("direct_matched"), rs.getInt("franchise_eligible")));
    }

    @Override
    public void completeBatch(long batchId, ReconciliationMatchSummary summary,
                              long expectedVersion, LocalDateTime now) {
        int changed = jdbc.update("UPDATE qh_recon_batch SET status='COMPLETED', matched_rows=?, "
                        + "difference_rows=?, direct_matched_rows=?, franchise_eligible_rows=?, "
                        + "completed_at=?, version=version+1, updated_at=? "
                        + "WHERE id=? AND version=? AND status='MATCHING' AND error_rows=0",
                summary.matchedRows(), summary.differenceRows(), summary.directMatchedRows(),
                summary.franchiseEligibleRows(), now, now, batchId, expectedVersion);
        if (changed != 1) throw new IllegalStateException("reconciliation completion gate was lost");
    }

    private RowMapper<PlatformOperationFact> factMapper() {
        return (rs, rowNum) -> new PlatformOperationFact((Long) rs.getObject("redemption_id"),
                (Long) rs.getObject("reversal_id"), rs.getString("redemption_no"),
                rs.getString("pos_request_no"), rs.getString("pos_order_no"),
                rs.getString("store_code"), rs.getString("terminal_no"),
                rs.getString("right_code_hash"), rs.getString("operation_status"),
                rs.getString("redemption_status"),
                rs.getTimestamp("occurred_at") == null ? null
                        : rs.getTimestamp("occurred_at").toLocalDateTime(),
                StoreOwnershipType.valueOf(rs.getString("ownership_type")),
                (Long) rs.getObject("candidate_id"), (Long) rs.getObject("subsidy_fen"),
                rs.getString("candidate_status"));
    }

    private static <T> Optional<T> first(List<T> values) {
        return values.stream().findFirst();
    }

    private static final class CandidateRow {
        private final long id;
        private final long redemptionId;
        private final long subsidyFen;
        private final String status;
        private CandidateRow(long id, long redemptionId, long subsidyFen, String status) {
            this.id = id; this.redemptionId = redemptionId;
            this.subsidyFen = subsidyFen; this.status = status;
        }
    }

    private static final class DetailRow {
        private final long batchId;
        private final String status;
        private DetailRow(long batchId, String status) {
            this.batchId = batchId; this.status = status;
        }
    }
}
