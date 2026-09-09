package com.qinghe.marketing.shared.error;

public class QingheBusinessException extends RuntimeException {

    private final QingheErrorCode errorCode;

    public QingheBusinessException(QingheErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public QingheErrorCode errorCode() {
        return errorCode;
    }
}
