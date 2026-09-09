package com.qinghe.marketing.shared.error;

public class QingheBusinessException extends RuntimeException {

    private final QingheErrorCode errorCode;
    private final String originalRedemptionNo;

    public QingheBusinessException(QingheErrorCode errorCode, String message) {
        this(errorCode, message, null);
    }

    public QingheBusinessException(QingheErrorCode errorCode, String message,
                                    String originalRedemptionNo) {
        super(message);
        this.errorCode = errorCode;
        this.originalRedemptionNo = originalRedemptionNo;
    }

    public QingheErrorCode errorCode() {
        return errorCode;
    }

    public String originalRedemptionNo() {
        return originalRedemptionNo;
    }
}
