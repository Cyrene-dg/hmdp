package com.qinghe.marketing.claim;

import com.qinghe.marketing.shared.error.QingheBusinessException;
import com.qinghe.marketing.shared.error.QingheErrorCode;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;

public final class ClaimRequestDigest {

    private ClaimRequestDigest() {
    }

    public static String calculate(String campaignNo, OffsetDateTime clientRequestedAt) {
        if (campaignNo == null || campaignNo.trim().isEmpty() || clientRequestedAt == null) {
            throw new QingheBusinessException(QingheErrorCode.INVALID_ARGUMENT,
                    "campaignNo and clientRequestedAt are required");
        }
        String canonical = campaignNo.trim() + "\n" + clientRequestedAt.toInstant().toString();
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8));
            StringBuilder value = new StringBuilder(64);
            for (byte item : digest) {
                value.append(String.format("%02x", item & 0xff));
            }
            return value.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}
