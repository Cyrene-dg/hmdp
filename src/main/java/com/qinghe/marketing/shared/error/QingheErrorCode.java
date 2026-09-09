package com.qinghe.marketing.shared.error;

public enum QingheErrorCode {
    INVALID_ARGUMENT(400),
    TOKEN_INVALID(401),
    TOKEN_EXPIRED(401),
    UNAUTHENTICATED(401),
    FORBIDDEN(403),
    MEMBER_FROZEN(403),
    MEMBER_CANCELLED(403),
    RESOURCE_NOT_FOUND(404),
    IDEMPOTENCY_CONFLICT(409),
    STORE_IMPORT_CONFLICT(409),
    BUSINESS_STATE_CONFLICT(409),
    CAMPAIGN_STATE_CONFLICT(409),
    VERSION_CONFLICT(409),
    SELF_APPROVAL_NOT_ALLOWED(409),
    TEMPLATE_VERSION_LOCKED(409),
    CAMPAIGN_INCOMPLETE(422),
    STORE_NOT_ELIGIBLE(422),
    RATE_LIMITED(429),
    TEMPORARY_UNAVAILABLE(503),
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
