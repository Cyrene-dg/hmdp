package com.qinghe.marketing.redemption;

import com.qinghe.marketing.shared.error.QingheBusinessException;
import com.qinghe.marketing.shared.error.QingheErrorCode;

import java.util.regex.Pattern;

final class PosCommandValidator {

    private static final Pattern REQUEST_NO = Pattern.compile("[A-Za-z0-9._:-]{8,64}");

    private PosCommandValidator() { }

    static void validate(PosVerificationCommand command) {
        if (command == null || !matchesRequest(command.posRequestNo())
                || !text(command.storeCode(), 32) || !text(command.terminalNo(), 32)
                || !text(command.rightCode(), 128) || command.rightCode().length() < 8
                || command.requestedAt() == null) {
            throw invalid("POS verification request is invalid");
        }
    }

    static void validate(PosRedemptionCommand command) {
        if (command == null || !matchesRequest(command.posRequestNo())
                || !text(command.posOrderNo(), 64) || !text(command.storeCode(), 32)
                || !text(command.terminalNo(), 32) || !text(command.operatorNo(), 32)
                || !text(command.rightCode(), 128) || command.rightCode().length() < 8
                || command.occurredAt() == null) {
            throw invalid("POS redemption request is invalid");
        }
    }

    static void validateRequestNo(String posRequestNo) {
        if (!matchesRequest(posRequestNo)) {
            throw invalid("POS request number is invalid");
        }
    }

    private static boolean matchesRequest(String value) {
        return value != null && REQUEST_NO.matcher(value).matches();
    }

    private static boolean text(String value, int max) {
        return value != null && !value.trim().isEmpty() && value.length() <= max;
    }

    private static QingheBusinessException invalid(String message) {
        return new QingheBusinessException(QingheErrorCode.INVALID_ARGUMENT, message);
    }
}
