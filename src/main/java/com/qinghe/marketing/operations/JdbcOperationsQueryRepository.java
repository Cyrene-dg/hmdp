package com.qinghe.marketing.operations;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Repository
public class JdbcOperationsQueryRepository implements OperationsQueryRepository {
    private static final int TRACE_LIMIT = 500;
    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate namedJdbc;

    public JdbcOperationsQueryRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.namedJdbc = new NamedParameterJdbcTemplate(jdbc);
    }

    @Override
    public List<BusinessTraceNode> findTrace(BusinessIdentifierType type, String value) {
        List<Long> claimIds = claimIds(type, value);
        List<Long> reconIds = reconIds(type, value, claimIds);
        List<Long> settlementIds = settlementIds(type, value, reconIds);
        List<BusinessTraceNode> nodes = new ArrayList<BusinessTraceNode>();
        if (!claimIds.isEmpty()) nodes.addAll(claimNodes(claimIds));
        if (!reconIds.isEmpty()) nodes.addAll(reconciliationNodes(reconIds));
        if (!settlementIds.isEmpty()) nodes.addAll(settlementNodes(settlementIds));
        if (type == BusinessIdentifierType.POS_REQUEST_NO) {
            nodes.addAll(directPosRequestNodes(value));
        }
        return orderedDistinct(nodes);
    }

    @Override
    public OperationalExceptionPage findExceptions(String exceptionType, String status,
                                                    LocalDateTime stuckCutoff,
                                                    LocalDateTime settlementCutoff,
                                                    int pageNo, int pageSize) {
        String base = exceptionUnion();
        String filter = " FROM (" + base + ") exceptions WHERE (? IS NULL OR exception_type = ?) "
                + "AND (? IS NULL OR status = ?)";
        Object[] common = new Object[]{stuckCutoff, stuckCutoff, settlementCutoff,
                exceptionType, exceptionType, status, status};
        Long total = jdbc.queryForObject("SELECT COUNT(*)" + filter, common, Long.class);
        List<Object> pageParameters = new ArrayList<Object>();
        Collections.addAll(pageParameters, common);
        pageParameters.add(pageSize);
        pageParameters.add((pageNo - 1) * pageSize);
        List<OperationalExceptionView> items = jdbc.query("SELECT exception_type,business_id,"
                        + "status,reason_code,detected_at,severity,allowed_action" + filter
                        + " ORDER BY detected_at DESC, exception_type, business_id LIMIT ? OFFSET ?",
                pageParameters.toArray(), exceptionMapper());
        return new OperationalExceptionPage(pageNo, pageSize, total == null ? 0 : total, items);
    }

    private List<Long> claimIds(BusinessIdentifierType type, String value) {
        switch (type) {
            case CLAIM_NO:
                return ids("SELECT id FROM qh_claim_request WHERE claim_no = ?", value);
            case EVENT_ID:
                return ids("SELECT c.id FROM qh_outbox_event o JOIN qh_claim_request c "
                        + "ON c.claim_no = o.aggregate_id WHERE o.event_id = ?", value);
            case ENTITLEMENT_NO:
                return ids("SELECT c.id FROM qh_member_entitlement e JOIN qh_claim_request c "
                        + "ON c.id = e.source_claim_id WHERE e.entitlement_no = ?", value);
            case REDEMPTION_NO:
                return ids("SELECT c.id FROM qh_redemption r JOIN qh_member_entitlement e "
                        + "ON e.id = r.entitlement_id JOIN qh_claim_request c "
                        + "ON c.id = e.source_claim_id WHERE r.redemption_no = ?", value);
            case POS_REQUEST_NO:
                return ids("SELECT c.id FROM qh_redemption r JOIN qh_member_entitlement e "
                        + "ON e.id=r.entitlement_id JOIN qh_claim_request c ON c.id=e.source_claim_id "
                        + "WHERE r.pos_request_no=? UNION SELECT c.id FROM qh_redemption_reversal v "
                        + "JOIN qh_redemption r ON r.id=v.redemption_id JOIN qh_member_entitlement e "
                        + "ON e.id=r.entitlement_id JOIN qh_claim_request c ON c.id=e.source_claim_id "
                        + "WHERE v.pos_request_no=? UNION SELECT c.id FROM qh_pos_redemption_request p "
                        + "JOIN qh_redemption r ON r.id=p.redemption_id JOIN qh_member_entitlement e "
                        + "ON e.id=r.entitlement_id JOIN qh_claim_request c ON c.id=e.source_claim_id "
                        + "WHERE p.pos_request_no=? UNION SELECT c.id FROM qh_pos_reversal_request p "
                        + "JOIN qh_redemption r ON r.redemption_no=p.target_redemption_no "
                        + "JOIN qh_member_entitlement e ON e.id=r.entitlement_id "
                        + "JOIN qh_claim_request c ON c.id=e.source_claim_id WHERE p.pos_request_no=? "
                        + "UNION SELECT c.id FROM qh_recon_record rr JOIN qh_redemption r "
                        + "ON r.id=rr.matched_redemption_id JOIN qh_member_entitlement e "
                        + "ON e.id=r.entitlement_id JOIN qh_claim_request c ON c.id=e.source_claim_id "
                        + "WHERE rr.pos_request_no=?", value, value, value, value, value);
            case RECON_BATCH_NO:
                return ids("SELECT DISTINCT c.id FROM qh_recon_batch rb "
                        + "JOIN qh_recon_record rr ON rr.batch_id=rb.id "
                        + "JOIN qh_redemption r ON r.id=rr.matched_redemption_id "
                        + "JOIN qh_member_entitlement e ON e.id=r.entitlement_id "
                        + "JOIN qh_claim_request c ON c.id=e.source_claim_id "
                        + "WHERE rb.recon_batch_no=?", value);
            case SETTLEMENT_BATCH_NO:
                return ids("SELECT DISTINCT c.id FROM qh_settlement_batch sb "
                        + "JOIN qh_settlement_detail sd ON sd.settlement_batch_id=sb.id "
                        + "JOIN qh_redemption r ON r.id=sd.redemption_id "
                        + "JOIN qh_member_entitlement e ON e.id=r.entitlement_id "
                        + "JOIN qh_claim_request c ON c.id=e.source_claim_id "
                        + "WHERE sb.batch_no=?", value);
            default:
                return Collections.emptyList();
        }
    }

    private List<Long> reconIds(BusinessIdentifierType type, String value, List<Long> claimIds) {
        if (type == BusinessIdentifierType.RECON_BATCH_NO) {
            return ids("SELECT id FROM qh_recon_batch WHERE recon_batch_no=?", value);
        }
        if (type == BusinessIdentifierType.SETTLEMENT_BATCH_NO) {
            return ids("SELECT rb.id FROM qh_settlement_batch sb JOIN qh_recon_batch rb "
                    + "ON rb.id=sb.recon_batch_id WHERE sb.batch_no=?", value);
        }
        if (claimIds.isEmpty()) return Collections.emptyList();
        return namedIds("SELECT DISTINCT rr.batch_id FROM qh_recon_record rr "
                + "JOIN qh_redemption r ON r.id=rr.matched_redemption_id "
                + "JOIN qh_member_entitlement e ON e.id=r.entitlement_id "
                + "WHERE e.source_claim_id IN (:ids)", claimIds);
    }

    private List<Long> settlementIds(BusinessIdentifierType type, String value,
                                     List<Long> reconIds) {
        if (type == BusinessIdentifierType.SETTLEMENT_BATCH_NO) {
            return ids("SELECT id FROM qh_settlement_batch WHERE batch_no=?", value);
        }
        if (reconIds.isEmpty()) return Collections.emptyList();
        return namedIds("SELECT id FROM qh_settlement_batch WHERE recon_batch_id IN (:ids)",
                reconIds);
    }

    private List<BusinessTraceNode> claimNodes(List<Long> claimIds) {
        MapSqlParameterSource parameters = idsParameter(claimIds);
        List<BusinessTraceNode> nodes = new ArrayList<BusinessTraceNode>();
        nodes.addAll(namedJdbc.query("SELECT 'CLAIM_REQUEST' node_type,c.claim_no business_id,"
                + "c.status,c.failure_code,c.updated_at occurred_at FROM qh_claim_request c "
                + "WHERE c.id IN (:ids)", parameters, traceMapper()));
        nodes.addAll(namedJdbc.query("SELECT 'OUTBOX_EVENT' node_type,o.event_id business_id,"
                + "o.status,o.last_error failure_code,o.updated_at occurred_at FROM qh_outbox_event o "
                + "JOIN qh_claim_request c ON c.claim_no=o.aggregate_id WHERE c.id IN (:ids)",
                parameters, traceMapper()));
        nodes.addAll(namedJdbc.query("SELECT 'CLAIM_DELIVERY' node_type,d.event_id business_id,"
                + "d.outcome status,d.failure_code,d.processed_at occurred_at "
                + "FROM qh_claim_issue_delivery d WHERE d.claim_id IN (:ids)", parameters,
                traceMapper()));
        nodes.addAll(namedJdbc.query("SELECT 'ENTITLEMENT' node_type,e.entitlement_no business_id,"
                + "e.status,NULL failure_code,e.updated_at occurred_at FROM qh_member_entitlement e "
                + "WHERE e.source_claim_id IN (:ids)", parameters, traceMapper()));
        nodes.addAll(namedJdbc.query("SELECT 'POS_REDEMPTION_REQUEST' node_type,p.pos_request_no "
                + "business_id,p.status,p.failure_code,p.updated_at occurred_at "
                + "FROM qh_pos_redemption_request p JOIN qh_redemption r ON r.id=p.redemption_id "
                + "JOIN qh_member_entitlement e ON e.id=r.entitlement_id "
                + "WHERE e.source_claim_id IN (:ids)", parameters, traceMapper()));
        nodes.addAll(namedJdbc.query("SELECT 'REDEMPTION' node_type,r.redemption_no business_id,"
                + "r.status,NULL failure_code,r.occurred_at FROM qh_redemption r "
                + "JOIN qh_member_entitlement e ON e.id=r.entitlement_id "
                + "WHERE e.source_claim_id IN (:ids)", parameters, traceMapper()));
        nodes.addAll(namedJdbc.query("SELECT 'POS_REVERSAL_REQUEST' node_type,p.pos_request_no "
                + "business_id,p.status,p.failure_code,p.updated_at occurred_at "
                + "FROM qh_pos_reversal_request p JOIN qh_redemption r "
                + "ON r.redemption_no=p.target_redemption_no JOIN qh_member_entitlement e "
                + "ON e.id=r.entitlement_id WHERE e.source_claim_id IN (:ids)", parameters,
                traceMapper()));
        nodes.addAll(namedJdbc.query("SELECT 'REVERSAL' node_type,v.reversal_no business_id,"
                + "v.status,v.reason_code failure_code,v.occurred_at FROM qh_redemption_reversal v "
                + "JOIN qh_redemption r ON r.id=v.redemption_id JOIN qh_member_entitlement e "
                + "ON e.id=r.entitlement_id WHERE e.source_claim_id IN (:ids)", parameters,
                traceMapper()));
        return nodes;
    }

    private List<BusinessTraceNode> reconciliationNodes(List<Long> reconIds) {
        MapSqlParameterSource parameters = idsParameter(reconIds);
        List<BusinessTraceNode> nodes = new ArrayList<BusinessTraceNode>();
        nodes.addAll(namedJdbc.query("SELECT 'RECON_BATCH' node_type,rb.recon_batch_no business_id,"
                + "rb.status,rb.last_error_code failure_code,rb.updated_at occurred_at "
                + "FROM qh_recon_batch rb WHERE rb.id IN (:ids)", parameters, traceMapper()));
        nodes.addAll(namedJdbc.query("SELECT 'RECON_RECORD' node_type,CONCAT(rb.recon_batch_no,"
                + "':line:',rr.line_no) business_id,rr.match_status status,rr.match_reason failure_code,"
                + "COALESCE(rr.matched_at,rr.updated_at) occurred_at FROM qh_recon_record rr "
                + "JOIN qh_recon_batch rb ON rb.id=rr.batch_id WHERE rr.batch_id IN (:ids)",
                parameters, traceMapper()));
        nodes.addAll(namedJdbc.query("SELECT 'RECON_DIFFERENCE' node_type,CONCAT(rb.recon_batch_no,"
                + "':difference:',d.id) business_id,d.difference_type status,NULL failure_code,"
                + "d.created_at occurred_at FROM qh_recon_difference d JOIN qh_recon_batch rb "
                + "ON rb.id=d.batch_id WHERE d.batch_id IN (:ids)", parameters, traceMapper()));
        nodes.addAll(namedJdbc.query("SELECT 'RECON_FILE_ATTEMPT' node_type,CONCAT(a.provider,':',"
                + "a.provider_batch_no) business_id,a.result status,NULL failure_code,"
                + "a.received_at occurred_at FROM qh_recon_file_attempt a "
                + "WHERE a.recon_batch_id IN (:ids)", parameters, traceMapper()));
        nodes.addAll(namedJdbc.query("SELECT 'OPERATION_AUDIT' node_type,"
                + "CONCAT(l.business_id,':',l.action,':',l.id) business_id,l.result status,"
                + "NULL failure_code,l.created_at occurred_at FROM qh_operation_log l "
                + "JOIN qh_recon_batch r ON r.recon_batch_no=l.business_id "
                + "WHERE r.id IN (:ids)", parameters, traceMapper()));
        return nodes;
    }

    private List<BusinessTraceNode> settlementNodes(List<Long> settlementIds) {
        MapSqlParameterSource parameters = idsParameter(settlementIds);
        List<BusinessTraceNode> nodes = new ArrayList<BusinessTraceNode>();
        nodes.addAll(namedJdbc.query("SELECT 'SETTLEMENT_BATCH' node_type,s.batch_no business_id,"
                + "s.status,NULL failure_code,s.updated_at occurred_at FROM qh_settlement_batch s "
                + "WHERE s.id IN (:ids)", parameters, traceMapper()));
        nodes.addAll(namedJdbc.query("SELECT 'SETTLEMENT_DETAIL' node_type,CONCAT(s.batch_no,"
                + "':detail:',d.id) business_id,d.status,NULL failure_code,d.updated_at occurred_at "
                + "FROM qh_settlement_detail d JOIN qh_settlement_batch s "
                + "ON s.id=d.settlement_batch_id WHERE d.settlement_batch_id IN (:ids)", parameters,
                traceMapper()));
        nodes.addAll(namedJdbc.query("SELECT 'OPERATION_AUDIT' node_type,"
                + "CONCAT(l.business_id,':',l.action,':',l.id) business_id,l.result status,"
                + "NULL failure_code,l.created_at occurred_at FROM qh_operation_log l "
                + "JOIN qh_settlement_batch s ON s.batch_no=l.business_id "
                + "WHERE s.id IN (:ids)", parameters, traceMapper()));
        return nodes;
    }

    private List<BusinessTraceNode> directPosRequestNodes(String value) {
        List<BusinessTraceNode> nodes = new ArrayList<BusinessTraceNode>();
        nodes.addAll(jdbc.query("SELECT 'POS_REDEMPTION_REQUEST' node_type,pos_request_no business_id,"
                + "status,failure_code,updated_at occurred_at FROM qh_pos_redemption_request "
                + "WHERE pos_request_no=?", new Object[]{value}, traceMapper()));
        nodes.addAll(jdbc.query("SELECT 'POS_REVERSAL_REQUEST' node_type,pos_request_no business_id,"
                + "status,failure_code,updated_at occurred_at FROM qh_pos_reversal_request "
                + "WHERE pos_request_no=?", new Object[]{value}, traceMapper()));
        nodes.addAll(jdbc.query("SELECT 'RECON_RECORD' node_type,CONCAT(rb.recon_batch_no,':line:',"
                + "rr.line_no) business_id,rr.match_status status,rr.match_reason failure_code,"
                + "COALESCE(rr.matched_at,rr.updated_at) occurred_at FROM qh_recon_record rr "
                + "JOIN qh_recon_batch rb ON rb.id=rr.batch_id WHERE rr.pos_request_no=?",
                new Object[]{value}, traceMapper()));
        return nodes;
    }

    private String exceptionUnion() {
        return "SELECT 'CLAIM_PROCESSING_STUCK' exception_type,c.claim_no business_id,c.status,"
                + "COALESCE(c.failure_code,'PROCESSING_TIMEOUT') reason_code,c.updated_at detected_at,"
                + "'HIGH' severity,'TRACE_ONLY' allowed_action FROM qh_claim_request c "
                + "WHERE c.status='PROCESSING' AND c.updated_at<=? UNION ALL "
                + "SELECT 'CLAIM_COMPENSATING_STUCK',c.claim_no,c.status,"
                + "COALESCE(c.failure_code,'COMPENSATION_TIMEOUT'),c.updated_at,'HIGH','TRACE_ONLY' "
                + "FROM qh_claim_request c WHERE c.status='COMPENSATING' AND c.updated_at<=? UNION ALL "
                + "SELECT 'OUTBOX_DEAD',o.event_id,o.status,COALESCE(o.last_error,'RETRY_EXHAUSTED'),"
                + "o.updated_at,'HIGH','TRACE_ONLY' FROM qh_outbox_event o WHERE o.status='DEAD' "
                + "UNION ALL SELECT 'CLAIM_DLQ_FAILED',d.event_id,d.outcome,"
                + "COALESCE(d.failure_code,'DELIVERY_FAILED'),d.processed_at,'HIGH','TRACE_ONLY' "
                + "FROM qh_claim_issue_delivery d WHERE d.outcome='FAILED' "
                + "UNION ALL SELECT 'POS_REDEMPTION_FAILED',p.pos_request_no,p.status,"
                + "p.failure_code,p.updated_at,'MEDIUM','TRACE_ONLY' FROM qh_pos_redemption_request p "
                + "WHERE p.status='FAILED' UNION ALL SELECT 'REVERSAL_REJECTED',p.pos_request_no,"
                + "p.status,p.failure_code,p.updated_at,'MEDIUM','TRACE_ONLY' "
                + "FROM qh_pos_reversal_request p WHERE p.status='FAILED' UNION ALL "
                + "SELECT 'RECON_BATCH_FAILURE',r.recon_batch_no,r.status,r.last_error_code,"
                + "r.updated_at,'HIGH','RECON_RETRY' FROM qh_recon_batch r "
                + "WHERE r.status IN ('PARTIAL_FAILED','CONFLICT','MISSING') UNION ALL "
                + "SELECT 'RECON_FILE_MISSING',CONCAT(a.provider,':',a.provider_batch_no),a.result,"
                + "'CSV_PAIR_MISSING',a.received_at,'HIGH','RECON_RETRY' "
                + "FROM qh_recon_file_attempt a WHERE a.result='MISSING' UNION ALL "
                + "SELECT 'RECON_IMPORT_BAD_ROW',CONCAT(r.recon_batch_no,':line:',i.line_no),"
                + "'OPEN',i.error_code,i.created_at,'MEDIUM','TRACE_ONLY' "
                + "FROM qh_recon_import_issue i JOIN qh_recon_batch r ON r.id=i.batch_id UNION ALL "
                + "SELECT 'RECON_DIFFERENCE',CONCAT(r.recon_batch_no,':difference:',d.id),"
                + "'OPEN',d.difference_type,d.created_at,'HIGH','TRACE_ONLY' "
                + "FROM qh_recon_difference d JOIN qh_recon_batch r ON r.id=d.batch_id UNION ALL "
                + "SELECT 'SETTLEMENT_PENDING_STALE',s.batch_no,s.status,'PENDING_CONFIRM_TIMEOUT',"
                + "s.updated_at,'HIGH','REVIEW_SETTLEMENT' FROM qh_settlement_batch s "
                + "WHERE s.status='PENDING_CONFIRM' AND s.updated_at<=?";
    }

    private List<Long> ids(String sql, Object... parameters) {
        return jdbc.query(sql, parameters, (rs, rowNum) -> rs.getLong(1));
    }

    private List<Long> namedIds(String sql, List<Long> ids) {
        return namedJdbc.query(sql, idsParameter(ids), (rs, rowNum) -> rs.getLong(1));
    }

    private static MapSqlParameterSource idsParameter(List<Long> ids) {
        return new MapSqlParameterSource("ids", ids);
    }

    private static RowMapper<BusinessTraceNode> traceMapper() {
        return (rs, rowNum) -> new BusinessTraceNode(rs.getString("node_type"),
                rs.getString("business_id"), rs.getString("status"),
                rs.getString("failure_code"),
                rs.getObject("occurred_at", LocalDateTime.class));
    }

    private static RowMapper<OperationalExceptionView> exceptionMapper() {
        return (rs, rowNum) -> new OperationalExceptionView(rs.getString("exception_type"),
                rs.getString("business_id"), rs.getString("status"),
                rs.getString("reason_code"),
                rs.getObject("detected_at", LocalDateTime.class), rs.getString("severity"),
                rs.getString("allowed_action") == null ? Collections.emptyList()
                        : Collections.singletonList(rs.getString("allowed_action")));
    }

    private static List<BusinessTraceNode> orderedDistinct(List<BusinessTraceNode> source) {
        Map<String, BusinessTraceNode> distinct = new LinkedHashMap<String, BusinessTraceNode>();
        for (BusinessTraceNode node : source) {
            String key = node.getNodeType() + "|" + node.getBusinessId() + "|" + node.getStatus()
                    + "|" + node.getOccurredAt();
            distinct.putIfAbsent(key, node);
        }
        List<BusinessTraceNode> nodes = new ArrayList<BusinessTraceNode>(distinct.values());
        nodes.sort(Comparator.comparing(BusinessTraceNode::getOccurredAt,
                Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(BusinessTraceNode::getNodeType)
                .thenComparing(BusinessTraceNode::getBusinessId));
        return nodes.size() <= TRACE_LIMIT ? nodes
                : new ArrayList<BusinessTraceNode>(nodes.subList(0, TRACE_LIMIT));
    }
}
