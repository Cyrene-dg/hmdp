package com.qinghe.marketing.shared.web;

public final class QingheErrorResponse {

    private final String code;
    private final String message;
    private final String requestId;

    public QingheErrorResponse(String code, String message, String requestId) {
        this.code = code;
        this.message = message;
        this.requestId = requestId;
    }

    public String getCode() { return code; }
    public String getMessage() { return message; }
    public String getRequestId() { return requestId; }
}
