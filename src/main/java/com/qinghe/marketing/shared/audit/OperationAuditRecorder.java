package com.qinghe.marketing.shared.audit;

/** Port implemented by the operations module; domain code must not write audit tables directly. */
public interface OperationAuditRecorder {
    void record(OperationAudit audit);
}
