package com.qinghe.marketing.operations;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

public final class OperationalExceptionView {
    private final String exceptionType;
    private final String businessId;
    private final String status;
    private final String reasonCode;
    private final LocalDateTime detectedAt;
    private final String severity;
    private final List<String> allowedActions;

    public OperationalExceptionView(String exceptionType, String businessId, String status,
                                    String reasonCode, LocalDateTime detectedAt, String severity,
                                    List<String> allowedActions) {
        this.exceptionType = exceptionType;
        this.businessId = businessId;
        this.status = status;
        this.reasonCode = reasonCode;
        this.detectedAt = detectedAt;
        this.severity = severity;
        this.allowedActions = Collections.unmodifiableList(allowedActions);
    }

    public String getExceptionType() { return exceptionType; }
    public String getBusinessId() { return businessId; }
    public String getStatus() { return status; }
    public String getReasonCode() { return reasonCode; }
    public LocalDateTime getDetectedAt() { return detectedAt; }
    public String getSeverity() { return severity; }
    public List<String> getAllowedActions() { return allowedActions; }
}
