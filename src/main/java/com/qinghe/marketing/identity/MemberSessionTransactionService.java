package com.qinghe.marketing.identity;

import com.qinghe.marketing.shared.clock.BusinessClock;
import com.qinghe.marketing.shared.id.BusinessIdGenerator;
import com.qinghe.marketing.shared.id.BusinessIdType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;

@Service
public class MemberSessionTransactionService {

    private final MemberMappingRepository memberMappingRepository;
    private final PlatformSessionRepository platformSessionRepository;
    private final PlatformMemberIdGenerator memberIdGenerator;
    private final PlatformSessionTokenIssuer tokenIssuer;
    private final BusinessIdGenerator businessIdGenerator;
    private final BusinessClock clock;

    public MemberSessionTransactionService(MemberMappingRepository memberMappingRepository,
                                           PlatformSessionRepository platformSessionRepository,
                                           PlatformMemberIdGenerator memberIdGenerator,
                                           PlatformSessionTokenIssuer tokenIssuer,
                                           BusinessIdGenerator businessIdGenerator,
                                           BusinessClock clock) {
        this.memberMappingRepository = memberMappingRepository;
        this.platformSessionRepository = platformSessionRepository;
        this.memberIdGenerator = memberIdGenerator;
        this.tokenIssuer = tokenIssuer;
        this.businessIdGenerator = businessIdGenerator;
        this.clock = clock;
    }

    @Transactional(rollbackFor = Exception.class)
    public SessionExchangeResult createSession(VerifiedMember verifiedMember, Instant expiresAt) {
        LocalDateTime now = clock.dateTime();
        MemberMapping mapping = memberMappingRepository.findOrCreate(
                verifiedMember.externalMemberNo(), memberIdGenerator.next(), verifiedMember.status(), now);
        PlatformSessionTokenIssuer.IssuedToken token = tokenIssuer.issue();
        String sessionNo = businessIdGenerator.next(BusinessIdType.MEMBER_SESSION);
        LocalDateTime localExpiresAt = LocalDateTime.ofInstant(expiresAt, BusinessClock.BUSINESS_ZONE);
        platformSessionRepository.save(new PlatformSessionRecord(
                sessionNo, token.hash(), mapping.id(), now, localExpiresAt));
        return new SessionExchangeResult(token.raw(), expiresAt,
                mask(verifiedMember.externalMemberNo()), verifiedMember.levelCode());
    }

    private static String mask(String memberNo) {
        if (memberNo == null || memberNo.length() <= 4) {
            return "****";
        }
        int visible = Math.min(3, memberNo.length() - 2);
        return memberNo.substring(0, visible) + "***" + memberNo.substring(memberNo.length() - 2);
    }
}
