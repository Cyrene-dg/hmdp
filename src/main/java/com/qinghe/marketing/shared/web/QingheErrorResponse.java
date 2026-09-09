package com.qinghe.marketing.shared.web;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public final class QingheErrorResponse {

    private final String code;
    private final String message;
    private final String requestId;
    private final String originalRedemptionNo;

    public QingheErrorResponse(String code, String message, String requestId) {
        this(code, message, requestId, null);
    }

    public QingheErrorResponse(String code, String message, String requestId,
                               String originalRedemptionNo) {
        this.code = code;
        this.message = message;
        this.requestId = requestId;
        this.originalRedemptionNo = originalRedemptionNo;
    }

    public String getCode() { return code; }
    public String getMessage() { return message; }
    public String getRequestId() { return requestId; }
    public String getOriginalRedemptionNo() { return originalRedemptionNo; }
}
