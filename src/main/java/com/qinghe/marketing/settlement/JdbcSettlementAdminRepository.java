package com.qinghe.marketing.settlement;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class JdbcSettlementAdminRepository implements SettlementAdminRepository {
    private static final String SELECT = "SELECT b.id,b.batch_no,r.recon_batch_no,b.business_date,"
            + "b.status,b.detail_count,b.total_fen,b.version,b.confirmed_by,b.confirmed_at,"
            + "(SELECT COUNT(DISTINCT c.store_id) FROM qh_settlement_detail d "
            + "JOIN qh_subsidy_candidate c ON c.id=d.candidate_id "
            + "WHERE d.settlement_batch_id=b.id) store_count FROM qh_settlement_batch b "
            + "JOIN qh_recon_batch r ON r.id=b.recon_batch_id";
    private final JdbcTemplate jdbc;
    public JdbcSettlementAdminRepository(JdbcTemplate jdbc) { this.jdbc=jdbc; }

    @Override
    public List<SettlementBatchView> list(SettlementBatchStatus status,int offset,int limit) {
        if (status == null) return jdbc.query(SELECT + " ORDER BY b.business_date DESC,b.id DESC "
                + "LIMIT ? OFFSET ?",new Object[]{limit,offset},mapper());
        return jdbc.query(SELECT + " WHERE b.status=? ORDER BY b.business_date DESC,b.id DESC "
                + "LIMIT ? OFFSET ?",new Object[]{status.name(),limit,offset},mapper());
    }
    @Override
    public long count(SettlementBatchStatus status) {
        Long count = status == null
                ? jdbc.queryForObject("SELECT COUNT(*) FROM qh_settlement_batch",Long.class)
                : jdbc.queryForObject("SELECT COUNT(*) FROM qh_settlement_batch WHERE status=?",
                        new Object[]{status.name()},Long.class);
        return count == null ? 0 : count;
    }
    @Override
    public Optional<SettlementBatchView> findDetail(String settlementBatchNo) {
        List<SettlementBatchView> rows=jdbc.query(SELECT + " WHERE b.batch_no=?",
                new Object[]{settlementBatchNo},mapper());
        if (rows.isEmpty()) return Optional.empty();
        SettlementBatchView batch=rows.get(0);
        List<SettlementDetailView> details=jdbc.query("SELECT camp.campaign_no,"
                        + "s.external_store_code,s.name,r.redemption_no,r.pos_request_no,"
                        + "r.pos_order_no,d.subsidy_fen,rr.match_status,d.status,d.confirmed_at "
                        + "FROM qh_settlement_detail d JOIN qh_subsidy_candidate c ON c.id=d.candidate_id "
                        + "JOIN qh_campaign camp ON camp.id=c.campaign_id "
                        + "JOIN qh_store s ON s.id=c.store_id JOIN qh_redemption r ON r.id=d.redemption_id "
                        + "JOIN qh_recon_record rr ON rr.batch_id=d.recon_batch_id "
                        + "AND rr.matched_redemption_id=d.redemption_id AND rr.operation_type='REDEEM' "
                        + "AND rr.match_status='MATCHED' AND rr.settlement_eligible=1 "
                        + "WHERE d.settlement_batch_id=? ORDER BY d.id",
                new Object[]{batch.id()},(rs,rowNum)->new SettlementDetailView(
                        rs.getString("campaign_no"),rs.getString("external_store_code"),
                        rs.getString("name"),rs.getString("redemption_no"),
                        rs.getString("pos_request_no"),rs.getString("pos_order_no"),
                        rs.getLong("subsidy_fen"),rs.getString("match_status"),
                        rs.getString("status"),
                        rs.getObject("confirmed_at",java.time.LocalDateTime.class)));
        return Optional.of(batch.withDetails(details));
    }
    private RowMapper<SettlementBatchView> mapper() {
        return (rs,rowNum)->new SettlementBatchView(rs.getLong("id"),rs.getString("batch_no"),
                rs.getString("recon_batch_no"),rs.getDate("business_date").toLocalDate(),
                rs.getString("status"),rs.getInt("detail_count"),rs.getInt("store_count"),
                rs.getLong("total_fen"),rs.getLong("version"),rs.getString("confirmed_by"),
                rs.getObject("confirmed_at",java.time.LocalDateTime.class),null);
    }
}
