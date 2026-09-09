package com.qinghe.marketing.shared.audit;

import java.time.Instant;
import java.util.Objects;

/** Persistence-neutral audit fact. The operations module persists it in a later work package. */
public final class OperationAudit {

    private final String operatorType;
    private final String operatorId;
    private final String action;
    private final String businessType;
    private final String businessId;
    private final String beforeState;
    private final String afterState;
    private final String reason;
    private final String result;
    private final String traceId;
    private final Instant occurredAt;

    public OperationAudit(String operatorType, String operatorId, String action,
                          String businessType, String businessId, String beforeState,
                          String afterState, String reason, String result,
                          String traceId, Instant occurredAt) {
        this.operatorType = required(operatorType, "operatorType");
        this.operatorId = required(operatorId, "operatorId");
        this.action = required(action, "action");
        this.businessType = required(businessType, "businessType");
        this.businessId = required(businessId, "businessId");
        this.beforeState = beforeState;
        this.afterState = afterState;
        this.reason = reason;
        this.result = required(result, "result");
        this.traceId = required(traceId, "traceId");
        this.occurredAt = Objects.requireNonNull(occurredAt, "occurredAt");
    }

    private static String required(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }

    public String operatorType() { return operatorType; }
    public String operatorId() { return operatorId; }
    public String action() { return action; }
    public String businessType() { return businessType; }
    public String businessId() { return businessId; }
    public String beforeState() { return beforeState; }
    public String afterState() { return afterState; }
    public String reason() { return reason; }
    public String result() { return result; }
    public String traceId() { return traceId; }
    public Instant occurredAt() { return occurredAt; }
}
