package com.qinghe.marketing.operations;

import java.time.LocalDateTime;

public final class BusinessTraceNode {
    private final String nodeType;
    private final String businessId;
    private final String status;
    private final String failureCode;
    private final LocalDateTime occurredAt;

    public BusinessTraceNode(String nodeType, String businessId, String status,
                             String failureCode, LocalDateTime occurredAt) {
        this.nodeType = nodeType;
        this.businessId = businessId;
        this.status = status;
        this.failureCode = failureCode;
        this.occurredAt = occurredAt;
    }

    public String getNodeType() { return nodeType; }
    public String getBusinessId() { return businessId; }
    public String getStatus() { return status; }
    public String getFailureCode() { return failureCode; }
    public LocalDateTime getOccurredAt() { return occurredAt; }
}
