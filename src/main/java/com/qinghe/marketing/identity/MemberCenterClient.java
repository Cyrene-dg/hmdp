package com.qinghe.marketing.identity;

public interface MemberCenterClient {

    VerifiedMember introspect(String memberToken, String requestId);
}
