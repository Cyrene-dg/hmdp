package com.qinghe.marketing.reversal;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

final class ReversalRequestDigest {
    private ReversalRequestDigest() { }

    static String calculate(PosReversalCommand command) {
        return sha256(join(command.redemptionNo(), command.posOrderNo(), command.storeCode(),
                command.operatorNo(), command.reasonCode().name(), command.reasonRemark(),
                command.occurredAt().toString()));
    }

    private static String join(String... values) {
        StringBuilder canonical = new StringBuilder();
        for (int index = 0; index < values.length; index++) {
            if (index > 0) canonical.append('\n');
            canonical.append(values[index] == null ? "" : values[index]);
        }
        return canonical.toString();
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte item : digest) result.append(String.format(Locale.ROOT, "%02x", item & 0xff));
            return result.toString();
        } catch (Exception unavailable) {
            throw new IllegalStateException("SHA-256 is unavailable", unavailable);
        }
    }
}
