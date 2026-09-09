package com.qinghe.marketing.identity;

import java.time.LocalDateTime;

public final class PlatformSessionRecord {

    private final String sessionNo;
    private final String accessTokenHash;
    private final long memberId;
    private final LocalDateTime issuedAt;
    private final LocalDateTime expiresAt;

    public PlatformSessionRecord(String sessionNo, String accessTokenHash, long memberId,
                                 LocalDateTime issuedAt, LocalDateTime expiresAt) {
        this.sessionNo = sessionNo;
        this.accessTokenHash = accessTokenHash;
        this.memberId = memberId;
        this.issuedAt = issuedAt;
        this.expiresAt = expiresAt;
    }

    public String sessionNo() {
        return sessionNo;
    }

    public String accessTokenHash() {
        return accessTokenHash;
    }

    public long memberId() {
        return memberId;
    }

    public LocalDateTime issuedAt() {
        return issuedAt;
    }

    public LocalDateTime expiresAt() {
        return expiresAt;
    }
}
