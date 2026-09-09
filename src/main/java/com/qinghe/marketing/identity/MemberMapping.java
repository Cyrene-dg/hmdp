package com.qinghe.marketing.identity;

public final class MemberMapping {

    private final long id;
    private final String externalMemberNo;
    private final long platformUserId;
    private final MemberStatus statusSnapshot;

    public MemberMapping(long id, String externalMemberNo, long platformUserId, MemberStatus statusSnapshot) {
        this.id = id;
        this.externalMemberNo = externalMemberNo;
        this.platformUserId = platformUserId;
        this.statusSnapshot = statusSnapshot;
    }

    public long id() {
        return id;
    }

    public String externalMemberNo() {
        return externalMemberNo;
    }

    public long platformUserId() {
        return platformUserId;
    }

    public MemberStatus statusSnapshot() {
        return statusSnapshot;
    }
}
