package com.qinghe.marketing.identity;

import java.time.Instant;

public final class SessionExchangeResult {

    private final String accessToken;
    private final Instant expiresAt;
    private final String maskedMemberNo;
    private final String levelCode;

    public SessionExchangeResult(String accessToken, Instant expiresAt,
                                 String maskedMemberNo, String levelCode) {
        this.accessToken = accessToken;
        this.expiresAt = expiresAt;
        this.maskedMemberNo = maskedMemberNo;
        this.levelCode = levelCode;
    }

    public String accessToken() {
        return accessToken;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public String maskedMemberNo() {
        return maskedMemberNo;
    }

    public String levelCode() {
        return levelCode;
    }
}
