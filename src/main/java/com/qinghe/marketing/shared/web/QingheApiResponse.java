package com.qinghe.marketing.shared.web;

public final class QingheApiResponse<T> {

    private final String code;
    private final String message;
    private final String requestId;
    private final T data;

    private QingheApiResponse(String code, String message, String requestId, T data) {
        this.code = code;
        this.message = message;
        this.requestId = requestId;
        this.data = data;
    }

    public static <T> QingheApiResponse<T> ok(String message, String requestId, T data) {
        return new QingheApiResponse<T>("OK", message, requestId, data);
    }

    public String getCode() { return code; }
    public String getMessage() { return message; }
    public String getRequestId() { return requestId; }
    public T getData() { return data; }
}
