package com.qinghe.marketing.shared.error;

public enum QingheErrorCode {
    INVALID_ARGUMENT(400),
    UNAUTHENTICATED(401),
    FORBIDDEN(403),
    RESOURCE_NOT_FOUND(404),
    IDEMPOTENCY_CONFLICT(409),
    BUSINESS_STATE_CONFLICT(409),
    STORE_NOT_ELIGIBLE(422),
    EXTERNAL_SERVICE_UNAVAILABLE(503),
    INTERNAL_ERROR(500);

    private final int httpStatus;

    QingheErrorCode(int httpStatus) {
        this.httpStatus = httpStatus;
    }

    public int httpStatus() {
        return httpStatus;
    }
}
