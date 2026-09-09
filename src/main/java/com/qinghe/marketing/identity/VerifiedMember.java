package com.qinghe.marketing.identity;

import java.time.Instant;

public final class VerifiedMember {

    private final String externalMemberNo;
    private final MemberStatus status;
    private final String levelCode;
    private final Instant tokenExpiresAt;

    public VerifiedMember(String externalMemberNo, MemberStatus status, String levelCode, Instant tokenExpiresAt) {
        this.externalMemberNo = externalMemberNo;
        this.status = status;
        this.levelCode = levelCode;
        this.tokenExpiresAt = tokenExpiresAt;
    }

    public String externalMemberNo() {
        return externalMemberNo;
    }

    public MemberStatus status() {
        return status;
    }

    public String levelCode() {
        return levelCode;
    }

    public Instant tokenExpiresAt() {
        return tokenExpiresAt;
    }
}
