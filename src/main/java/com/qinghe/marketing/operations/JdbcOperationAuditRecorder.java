package com.qinghe.marketing.operations;

import com.qinghe.marketing.shared.audit.OperationAudit;
import com.qinghe.marketing.shared.audit.OperationAuditRecorder;
import com.qinghe.marketing.shared.clock.BusinessClock;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public class JdbcOperationAuditRecorder implements OperationAuditRecorder {
    private final JdbcTemplate jdbc;

    public JdbcOperationAuditRecorder(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void record(OperationAudit audit) {
        LocalDateTime occurredAt = LocalDateTime.ofInstant(
                audit.occurredAt(), BusinessClock.BUSINESS_ZONE);
        jdbc.update("INSERT INTO qh_operation_log (operator_type,operator_id,action,business_type,"
                        + "business_id,before_state,after_state,reason,result,request_id,trace_id,"
                        + "created_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)", audit.operatorType(),
                audit.operatorId(), audit.action(), audit.businessType(), audit.businessId(),
                audit.beforeState(), audit.afterState(), audit.reason(), audit.result(),
                audit.requestId(), audit.traceId(), occurredAt);
    }
}
