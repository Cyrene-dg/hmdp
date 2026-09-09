package com.qinghe.marketing.shared.error;

public class QingheBusinessException extends RuntimeException {

    private final QingheErrorCode errorCode;
    private final String originalRedemptionNo;
    private final Integer httpStatusOverride;

    public QingheBusinessException(QingheErrorCode errorCode, String message) {
        this(errorCode, message, null);
    }

    public QingheBusinessException(QingheErrorCode errorCode, String message,
                                    String originalRedemptionNo) {
        this(errorCode, message, originalRedemptionNo, null);
    }

    public QingheBusinessException(QingheErrorCode errorCode, String message,
                                    String originalRedemptionNo, Integer httpStatusOverride) {
        super(message);
        this.errorCode = errorCode;
        this.originalRedemptionNo = originalRedemptionNo;
        this.httpStatusOverride = httpStatusOverride;
    }

    public QingheErrorCode errorCode() {
        return errorCode;
    }

    public String originalRedemptionNo() {
        return originalRedemptionNo;
    }

    public int httpStatus() {
        return httpStatusOverride == null ? errorCode.httpStatus() : httpStatusOverride;
    }
}
