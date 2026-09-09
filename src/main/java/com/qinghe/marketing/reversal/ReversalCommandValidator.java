package com.qinghe.marketing.reversal;

import com.qinghe.marketing.shared.error.QingheBusinessException;
import com.qinghe.marketing.shared.error.QingheErrorCode;
import java.util.regex.Pattern;

final class ReversalCommandValidator {
    private static final Pattern REQUEST_NO = Pattern.compile("[A-Za-z0-9._:-]{8,64}");
    private ReversalCommandValidator() { }

    static void validate(PosReversalCommand command) {
        if (command == null || !matches(command.posRequestNo())
                || !text(command.redemptionNo(), 40) || !text(command.posOrderNo(), 64)
                || !text(command.storeCode(), 32) || !text(command.operatorNo(), 32)
                || command.reasonCode() == null || command.occurredAt() == null
                || command.reasonRemark() != null && command.reasonRemark().length() > 256) {
            throw new QingheBusinessException(QingheErrorCode.INVALID_ARGUMENT,
                    "POS reversal request is invalid");
        }
    }

    private static boolean matches(String value) {
        return value != null && REQUEST_NO.matcher(value).matches();
    }
    private static boolean text(String value, int max) {
        return value != null && !value.trim().isEmpty() && value.length() <= max;
    }
}
