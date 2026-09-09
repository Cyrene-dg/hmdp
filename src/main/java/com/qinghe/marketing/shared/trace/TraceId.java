package com.qinghe.marketing.shared.trace;

import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

public final class TraceId {

    private static final Pattern SAFE = Pattern.compile("[A-Za-z0-9._:-]{8,64}");

    private TraceId() {
    }

    public static boolean isValid(String candidate) {
        return candidate != null && SAFE.matcher(candidate).matches();
    }

    public static String acceptOrCreate(String candidate) {
        if (isValid(candidate)) {
            return candidate;
        }
        return "QH-" + UUID.randomUUID().toString().replace("-", "").toUpperCase(Locale.ROOT);
    }
}
