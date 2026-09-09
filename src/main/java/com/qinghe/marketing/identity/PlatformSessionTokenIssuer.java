package com.qinghe.marketing.identity;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public class PlatformSessionTokenIssuer {

    private final SecureRandom secureRandom;

    public PlatformSessionTokenIssuer() {
        this(new SecureRandom());
    }

    PlatformSessionTokenIssuer(SecureRandom secureRandom) {
        this.secureRandom = secureRandom;
    }

    public IssuedToken issue() {
        byte[] random = new byte[32];
        secureRandom.nextBytes(random);
        String raw = "qh_" + Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        return new IssuedToken(raw, hash(raw));
    }

    public String hash(String rawToken) {
        if (rawToken == null || rawToken.trim().isEmpty()) {
            return "";
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                hex.append(String.format("%02x", value & 0xff));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public static final class IssuedToken {
        private final String raw;
        private final String hash;

        IssuedToken(String raw, String hash) {
            this.raw = raw;
            this.hash = hash;
        }

        public String raw() {
            return raw;
        }

        public String hash() {
            return hash;
        }
    }
}
