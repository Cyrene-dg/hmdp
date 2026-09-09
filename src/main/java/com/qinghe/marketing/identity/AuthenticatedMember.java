package com.qinghe.marketing.identity;

import java.time.LocalDateTime;

public final class AuthenticatedMember {

    private final long memberId;
    private final long platformUserId;
    private final String externalMemberNo;
    private final LocalDateTime expiresAt;

    public AuthenticatedMember(long memberId, long platformUserId, String externalMemberNo) {
        this(memberId, platformUserId, externalMemberNo, LocalDateTime.MAX);
    }

    public AuthenticatedMember(long memberId, long platformUserId, String externalMemberNo,
                               LocalDateTime expiresAt) {
        this.memberId = memberId;
        this.platformUserId = platformUserId;
        this.externalMemberNo = externalMemberNo;
        this.expiresAt = expiresAt;
    }

    public long memberId() {
        return memberId;
    }

    public long platformUserId() {
        return platformUserId;
    }

    public String externalMemberNo() {
        return externalMemberNo;
    }

    public LocalDateTime expiresAt() {
        return expiresAt;
    }
}
