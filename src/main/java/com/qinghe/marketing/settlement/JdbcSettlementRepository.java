package com.qinghe.marketing.settlement;

import com.qinghe.marketing.reconciliation.ReconciliationBatch;
import com.qinghe.marketing.reconciliation.ReconciliationBatchStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public class JdbcSettlementRepository implements SettlementRepository {
    private static final String BATCH_SELECT = "SELECT b.id, b.batch_no, b.recon_batch_id, "
            + "r.recon_batch_no, b.business_date, b.status, b.detail_count, b.total_fen, b.version, "
            + "b.confirmed_by, b.confirmed_at, (SELECT COUNT(DISTINCT c.store_id) "
            + "FROM qh_settlement_detail d JOIN qh_subsidy_candidate c ON c.id=d.candidate_id "
            + "WHERE d.settlement_batch_id=b.id) AS store_count FROM qh_settlement_batch b "
            + "JOIN qh_recon_batch r ON r.id=b.recon_batch_id";
    private final JdbcTemplate jdbc;

    public JdbcSettlementRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<ReconciliationBatch> findReconBatchForUpdate(String reconBatchNo) {
        return first(jdbc.query("SELECT id,recon_batch_no,provider,batch_no,business_date,checksum,"
                        + "status,total_rows,imported_rows,success_rows,error_rows,version "
                        + "FROM qh_recon_batch WHERE recon_batch_no=? FOR UPDATE",
                new Object[]{reconBatchNo}, (rs, rowNum) -> new ReconciliationBatch(
                        rs.getLong("id"), rs.getString("recon_batch_no"),
                        rs.getString("provider"), rs.getString("batch_no"),
                        rs.getDate("business_date").toLocalDate(), rs.getString("checksum"),
                        ReconciliationBatchStatus.valueOf(rs.getString("status")),
                        rs.getInt("total_rows"), rs.getInt("imported_rows"),
                        rs.getInt("success_rows"), rs.getInt("error_rows"),
                        rs.getLong("version"))));
    }

    @Override
    public Optional<SettlementBatch> findByReconBatchIdForUpdate(long reconBatchId) {
        return first(jdbc.query(BATCH_SELECT + " WHERE b.recon_batch_id=? FOR UPDATE",
                new Object[]{reconBatchId}, batchMapper()));
    }

    @Override
    public Optional<SettlementBatch> findByBatchNoForUpdate(String settlementBatchNo) {
        return first(jdbc.query(BATCH_SELECT + " WHERE b.batch_no=? FOR UPDATE",
                new Object[]{settlementBatchNo}, batchMapper()));
    }

    @Override
    public Optional<SettlementBatch> findByBatchNo(String settlementBatchNo) {
        return first(jdbc.query(BATCH_SELECT + " WHERE b.batch_no=?",
                new Object[]{settlementBatchNo}, batchMapper()));
    }

    @Override
    public SettlementTotals lockEligibleDetails(long reconBatchId,
                                                 Long existingSettlementBatchId) {
        String association = existingSettlementBatchId == null
                ? "d.settlement_batch_id IS NULL"
                : "(d.settlement_batch_id IS NULL OR d.settlement_batch_id=?)";
        List<Object> arguments = new ArrayList<Object>();
        arguments.add(reconBatchId);
        if (existingSettlementBatchId != null) arguments.add(existingSettlementBatchId);
        List<EligibleRow> rows = jdbc.query("SELECT d.id,c.store_id,d.subsidy_fen "
                        + "FROM qh_settlement_detail d "
                        + "JOIN qh_subsidy_candidate c ON c.id=d.candidate_id "
                        + "JOIN qh_redemption r ON r.id=d.redemption_id "
                        + "JOIN qh_store s ON s.id=c.store_id "
                        + "WHERE d.recon_batch_id=? AND d.status='PENDING_CONFIRM' AND "
                        + association + " AND c.status='MATCHED' AND r.status='SUCCESS' "
                        + "AND s.ownership_type='FRANCHISE' ORDER BY d.id FOR UPDATE",
                arguments.toArray(), (rs, rowNum) -> new EligibleRow(rs.getLong("id"),
                        rs.getLong("store_id"), rs.getLong("subsidy_fen")));
        return totals(rows);
    }

    @Override
    public SettlementBatch insertBatch(String batchNo, ReconciliationBatch recon,
                                       SettlementTotals totals, LocalDateTime now) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO qh_settlement_batch (batch_no,recon_batch_id,business_date,status,"
                            + "detail_count,total_fen,version,created_at,updated_at) "
                            + "VALUES (?,?,?,'PENDING_CONFIRM',?,?,1,?,?)",
                    Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, batchNo);
            statement.setLong(2, recon.id());
            statement.setObject(3, recon.businessDate());
            statement.setInt(4, totals.detailCount());
            statement.setLong(5, totals.totalFen());
            statement.setObject(6, now);
            statement.setObject(7, now);
            return statement;
        }, keys);
        Number id = keys.getKey();
        if (id == null) throw new IllegalStateException("settlement batch insert returned no id");
        return new SettlementBatch(id.longValue(), batchNo, recon.id(), recon.reconBatchNo(),
                recon.businessDate(), SettlementBatchStatus.PENDING_CONFIRM,
                totals.detailCount(), totals.storeCount(), totals.totalFen(), 1, null, null);
    }

    @Override
    public void attachDetails(long settlementBatchId, List<Long> detailIds, LocalDateTime now) {
        int attached = 0;
        for (Long detailId : detailIds) {
            attached += jdbc.update("UPDATE qh_settlement_detail SET settlement_batch_id=?, "
                            + "updated_at=? WHERE id=? AND status='PENDING_CONFIRM' "
                            + "AND (settlement_batch_id IS NULL OR settlement_batch_id=?)",
                    settlementBatchId, now, detailId, settlementBatchId);
        }
        if (attached != detailIds.size()) {
            throw new IllegalStateException("eligible settlement details changed during generation");
        }
    }

    @Override
    public SettlementBatch rebuildBatch(SettlementBatch batch, SettlementTotals totals,
                                        LocalDateTime now) {
        if (totals.detailCount() == 0) return batch;
        attachDetails(batch.id(), totals.detailIds(), now);
        int changed = jdbc.update("UPDATE qh_settlement_batch SET status='PENDING_CONFIRM', "
                        + "detail_count=?,total_fen=?,version=version+1,updated_at=? "
                        + "WHERE id=? AND version=? AND status='DRAFT'",
                totals.detailCount(), totals.totalFen(), now, batch.id(), batch.version());
        requireOne(changed, "draft settlement batch changed during rebuild");
        return findByBatchNoForUpdate(batch.batchNo()).orElseThrow(() ->
                new IllegalStateException("rebuilt settlement batch cannot be reloaded"));
    }

    @Override
    public SettlementTotals lockAttachedEligibleDetails(long settlementBatchId) {
        List<EligibleRow> rows = jdbc.query("SELECT d.id,c.store_id,d.subsidy_fen "
                        + "FROM qh_settlement_detail d "
                        + "JOIN qh_subsidy_candidate c ON c.id=d.candidate_id "
                        + "JOIN qh_redemption r ON r.id=d.redemption_id "
                        + "JOIN qh_store s ON s.id=c.store_id "
                        + "WHERE d.settlement_batch_id=? AND d.status='PENDING_CONFIRM' "
                        + "AND c.status='MATCHED' AND r.status='SUCCESS' "
                        + "AND s.ownership_type='FRANCHISE' ORDER BY d.id FOR UPDATE",
                new Object[]{settlementBatchId}, (rs, rowNum) -> new EligibleRow(
                        rs.getLong("id"), rs.getLong("store_id"),
                        rs.getLong("subsidy_fen")));
        return totals(rows);
    }

    @Override
    public void confirmDetails(long settlementBatchId, int expectedCount, LocalDateTime now) {
        int changed = jdbc.update("UPDATE qh_settlement_detail d "
                        + "JOIN qh_subsidy_candidate c ON c.id=d.candidate_id "
                        + "JOIN qh_redemption r ON r.id=d.redemption_id "
                        + "JOIN qh_store s ON s.id=c.store_id "
                        + "SET d.status='CONFIRMED',d.confirmed_at=?,d.updated_at=? "
                        + "WHERE d.settlement_batch_id=? AND d.status='PENDING_CONFIRM' "
                        + "AND c.status='MATCHED' AND r.status='SUCCESS' "
                        + "AND s.ownership_type='FRANCHISE'", now, now, settlementBatchId);
        if (changed != expectedCount) {
            throw new IllegalStateException("settlement detail confirmation count changed");
        }
    }

    @Override
    public void confirmBatch(long settlementBatchId, long expectedVersion, String operatorId,
                             LocalDateTime now) {
        int changed = jdbc.update("UPDATE qh_settlement_batch SET status='CONFIRMED',"
                        + "confirmed_by=?,confirmed_at=?,version=version+1,updated_at=? "
                        + "WHERE id=? AND version=? AND status='PENDING_CONFIRM'",
                operatorId, now, now, settlementBatchId, expectedVersion);
        requireOne(changed, "settlement batch confirmation state gate was lost");
    }

    @Override
    public List<SettlementExportRow> exportRows(long settlementBatchId) {
        return jdbc.query("SELECT DISTINCT b.batch_no,b.business_date,camp.campaign_no,s.external_store_code,"
                        + "r.redemption_no,r.pos_request_no,d.subsidy_fen,rr.match_status,d.status "
                        + "FROM qh_settlement_detail d "
                        + "JOIN qh_settlement_batch b ON b.id=d.settlement_batch_id "
                        + "JOIN qh_subsidy_candidate c ON c.id=d.candidate_id "
                        + "JOIN qh_campaign camp ON camp.id=c.campaign_id "
                        + "JOIN qh_store s ON s.id=c.store_id "
                        + "JOIN qh_redemption r ON r.id=d.redemption_id "
                        + "JOIN qh_recon_record rr ON rr.batch_id=d.recon_batch_id "
                        + "AND rr.matched_redemption_id=d.redemption_id AND rr.operation_type='REDEEM' "
                        + "AND rr.match_status='MATCHED' AND rr.settlement_eligible=1 "
                        + "WHERE d.settlement_batch_id=? ORDER BY s.external_store_code,r.redemption_no",
                new Object[]{settlementBatchId}, (rs, rowNum) -> new SettlementExportRow(
                        rs.getString("batch_no"), rs.getDate("business_date").toLocalDate(),
                        rs.getString("campaign_no"), rs.getString("external_store_code"),
                        rs.getString("redemption_no"), rs.getString("pos_request_no"),
                        rs.getLong("subsidy_fen"), rs.getString("match_status"),
                        rs.getString("status")));
    }

    @Override
    public void insertAudit(String operatorId, String action, String businessType,
                            String businessId, String beforeState, String afterState,
                            String reason, String result, String requestId, LocalDateTime now) {
        jdbc.update("INSERT INTO qh_operation_log (operator_type,operator_id,action,business_type,"
                        + "business_id,before_state,after_state,reason,result,request_id,trace_id,"
                        + "created_at) VALUES ('ADMIN',?,?,?,?,?,?,?,?,?,?,?)", operatorId, action,
                businessType, businessId, beforeState, afterState, reason, result,
                requestId, requestId, now);
    }

    private org.springframework.jdbc.core.RowMapper<SettlementBatch> batchMapper() {
        return (rs, rowNum) -> new SettlementBatch(rs.getLong("id"), rs.getString("batch_no"),
                rs.getLong("recon_batch_id"), rs.getString("recon_batch_no"),
                rs.getDate("business_date").toLocalDate(),
                SettlementBatchStatus.valueOf(rs.getString("status")),
                rs.getInt("detail_count"), rs.getInt("store_count"), rs.getLong("total_fen"),
                rs.getLong("version"), rs.getString("confirmed_by"),
                rs.getTimestamp("confirmed_at") == null ? null
                        : rs.getTimestamp("confirmed_at").toLocalDateTime());
    }

    private static SettlementTotals totals(List<EligibleRow> rows) {
        List<Long> ids = new ArrayList<Long>();
        Set<Long> stores = new HashSet<Long>();
        long total = 0;
        for (EligibleRow row : rows) {
            ids.add(row.id); stores.add(row.storeId); total += row.subsidyFen;
        }
        return new SettlementTotals(ids, stores.size(), total);
    }

    private static <T> Optional<T> first(List<T> rows) { return rows.stream().findFirst(); }
    private static void requireOne(int changed, String message) {
        if (changed != 1) throw new IllegalStateException(message);
    }
    private static final class EligibleRow {
        private final long id; private final long storeId; private final long subsidyFen;
        private EligibleRow(long id, long storeId, long subsidyFen) {
            this.id=id; this.storeId=storeId; this.subsidyFen=subsidyFen;
        }
    }
}
