package com.qinghe.marketing.reconciliation;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
public class JdbcReconciliationAdminRepository implements ReconciliationAdminRepository {
    private static final String SELECT = "SELECT id,recon_batch_no,provider,batch_no,business_date,"
            + "checksum,file_name,status,total_rows,success_rows,error_rows,matched_rows,"
            + "difference_rows,direct_matched_rows,franchise_eligible_rows,last_error_code,"
            + "version,completed_at FROM qh_recon_batch";
    private final JdbcTemplate jdbc;

    public JdbcReconciliationAdminRepository(JdbcTemplate jdbc) { this.jdbc=jdbc; }

    @Override
    public List<ReconciliationBatchView> list(LocalDate businessDate,
            ReconciliationBatchStatus status, int offset, int limit) {
        Filter filter = filter(businessDate, status);
        List<Object> args = new ArrayList<Object>(filter.args);
        args.add(limit); args.add(offset);
        return jdbc.query(SELECT + filter.sql + " ORDER BY business_date DESC,id DESC LIMIT ? OFFSET ?",
                args.toArray(), mapper());
    }

    @Override
    public long count(LocalDate businessDate, ReconciliationBatchStatus status) {
        Filter filter = filter(businessDate, status);
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM qh_recon_batch" + filter.sql,
                filter.args.toArray(), Long.class);
        return count == null ? 0 : count;
    }

    @Override
    public Optional<ReconciliationBatchView> findDetail(String reconBatchNo) {
        List<ReconciliationBatchView> rows = jdbc.query(SELECT + " WHERE recon_batch_no=?",
                new Object[]{reconBatchNo}, mapper());
        if (rows.isEmpty()) return Optional.empty();
        ReconciliationBatchView batch = rows.get(0);
        List<ReconciliationIssueView> issues = jdbc.query("SELECT line_no,error_code,raw_digest "
                        + "FROM qh_recon_import_issue WHERE batch_id=? ORDER BY line_no",
                new Object[]{batch.id()}, (rs,rowNum) -> new ReconciliationIssueView(
                        rs.getInt("line_no"),rs.getString("error_code"),rs.getString("raw_digest")));
        List<ReconciliationDifferenceView> differences = jdbc.query(
                "SELECT difference_type,business_key,detail FROM qh_recon_difference "
                        + "WHERE batch_id=? ORDER BY id", new Object[]{batch.id()},
                (rs,rowNum) -> new ReconciliationDifferenceView(rs.getString("difference_type"),
                        rs.getString("business_key"),rs.getString("detail")));
        List<ReconciliationAttemptView> attempts=jdbc.query("SELECT checksum,result,received_at "
                        + "FROM qh_recon_file_attempt WHERE recon_batch_id=? ORDER BY id",
                new Object[]{batch.id()},(rs,rowNum)->new ReconciliationAttemptView(
                        rs.getString("checksum"),rs.getString("result"),
                        rs.getObject("received_at",java.time.LocalDateTime.class)));
        return Optional.of(batch.withDetails(issues,differences,attempts));
    }

    private RowMapper<ReconciliationBatchView> mapper() {
        return (rs,rowNum) -> new ReconciliationBatchView(rs.getLong("id"),
                rs.getString("recon_batch_no"),rs.getString("provider"),rs.getString("batch_no"),
                rs.getDate("business_date").toLocalDate(),rs.getString("checksum"),
                rs.getString("file_name"),rs.getString("status"),rs.getInt("total_rows"),
                rs.getInt("success_rows"),rs.getInt("error_rows"),rs.getInt("matched_rows"),
                rs.getInt("difference_rows"),rs.getInt("direct_matched_rows"),
                rs.getInt("franchise_eligible_rows"),rs.getString("last_error_code"),
                rs.getLong("version"),
                rs.getObject("completed_at",java.time.LocalDateTime.class),null,null,null);
    }

    private static Filter filter(LocalDate businessDate, ReconciliationBatchStatus status) {
        StringBuilder sql = new StringBuilder(" WHERE 1=1");
        List<Object> args = new ArrayList<Object>();
        if (businessDate != null) { sql.append(" AND business_date=?"); args.add(businessDate); }
        if (status != null) { sql.append(" AND status=?"); args.add(status.name()); }
        return new Filter(sql.toString(),args);
    }
    private static final class Filter {
        private final String sql; private final List<Object> args;
        private Filter(String sql,List<Object> args) { this.sql=sql; this.args=args; }
    }
}
