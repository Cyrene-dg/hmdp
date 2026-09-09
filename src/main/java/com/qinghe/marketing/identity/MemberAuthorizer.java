package com.qinghe.marketing.identity;

import com.qinghe.marketing.shared.error.QingheBusinessException;
import com.qinghe.marketing.shared.error.QingheErrorCode;
import org.springframework.stereotype.Component;

@Component
public class MemberAuthorizer {

    private final MemberSessionService sessionService;

    public MemberAuthorizer(MemberSessionService sessionService) {
        this.sessionService = sessionService;
    }

    public AuthenticatedMember require(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            throw unauthenticated();
        }
        String platformToken = authorizationHeader.substring("Bearer ".length()).trim();
        if (platformToken.isEmpty()) {
            throw unauthenticated();
        }
        return sessionService.authenticate(platformToken);
    }

    private static QingheBusinessException unauthenticated() {
        return new QingheBusinessException(QingheErrorCode.UNAUTHENTICATED,
                "member platform session is required");
    }
}
