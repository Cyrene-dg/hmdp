package com.qinghe.marketing.operations;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.LinkedHashMap;
import java.util.Map;

@Repository
public class JdbcOperationalGaugeRepository implements OperationalGaugeRepository {
    private final JdbcTemplate jdbc;

    public JdbcOperationalGaugeRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Map<String, Long> snapshot() {
        Map<String, Long> gauges = new LinkedHashMap<String, Long>();
        statusCounts(gauges, "claim", "qh_claim_request");
        statusCounts(gauges, "outbox", "qh_outbox_event");
        statusCounts(gauges, "posRedemption", "qh_pos_redemption_request");
        statusCounts(gauges, "posReversal", "qh_pos_reversal_request");
        statusCounts(gauges, "reconciliation", "qh_recon_batch");
        statusCounts(gauges, "settlement", "qh_settlement_batch");
        put(gauges, "reconciliation.badRows",
                "SELECT COUNT(*) FROM qh_recon_import_issue");
        put(gauges, "reconciliation.differences",
                "SELECT COUNT(*) FROM qh_recon_difference");
        put(gauges, "reconciliation.missingFiles",
                "SELECT COUNT(*) FROM qh_recon_file_attempt WHERE result='MISSING'");
        put(gauges, "reconciliation.idempotentHits",
                "SELECT COUNT(*) FROM qh_recon_file_attempt WHERE result='DUPLICATE'");
        put(gauges, "settlement.pendingAmountFen",
                "SELECT COALESCE(SUM(total_fen),0) FROM qh_settlement_batch "
                        + "WHERE status='PENDING_CONFIRM'");
        put(gauges, "settlement.confirmedAmountFen",
                "SELECT COALESCE(SUM(total_fen),0) FROM qh_settlement_batch "
                        + "WHERE status='CONFIRMED'");
        return gauges;
    }

    private void statusCounts(Map<String, Long> gauges, String prefix, String table) {
        for (Map<String, Object> row : jdbc.queryForList(
                "SELECT status,COUNT(*) count_value FROM " + table + " GROUP BY status")) {
            gauges.put(prefix + ".status." + row.get("status"),
                    ((Number) row.get("count_value")).longValue());
        }
    }

    private void put(Map<String, Long> gauges, String name, String sql) {
        Long value = jdbc.queryForObject(sql, Long.class);
        gauges.put(name, value == null ? 0 : value);
    }
}
