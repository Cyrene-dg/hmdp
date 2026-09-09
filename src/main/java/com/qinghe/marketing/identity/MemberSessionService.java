package com.qinghe.marketing.identity;

import com.qinghe.marketing.shared.clock.BusinessClock;
import com.qinghe.marketing.shared.error.QingheBusinessException;
import com.qinghe.marketing.shared.error.QingheErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class MemberSessionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(MemberSessionService.class);

    private final MemberCenterClient memberCenterClient;
    private final MemberSessionTransactionService transactionService;
    private final PlatformSessionRepository platformSessionRepository;
    private final PlatformSessionCache platformSessionCache;
    private final PlatformSessionTokenIssuer tokenIssuer;
    private final BusinessClock clock;
    private final Duration sessionTtl;

    @Autowired
    public MemberSessionService(MemberCenterClient memberCenterClient,
                                MemberSessionTransactionService transactionService,
                                PlatformSessionRepository platformSessionRepository,
                                PlatformSessionCache platformSessionCache,
                                PlatformSessionTokenIssuer tokenIssuer,
                                BusinessClock clock,
                                @Value("${qinghe.member-session.ttl-seconds:1800}") long sessionTtlSeconds) {
        if (sessionTtlSeconds <= 0) {
            throw new IllegalArgumentException("member session TTL must be positive");
        }
        this.memberCenterClient = memberCenterClient;
        this.transactionService = transactionService;
        this.platformSessionRepository = platformSessionRepository;
        this.platformSessionCache = platformSessionCache;
        this.tokenIssuer = tokenIssuer;
        this.clock = clock;
        this.sessionTtl = Duration.ofSeconds(sessionTtlSeconds);
    }

    public MemberSessionService(MemberCenterClient memberCenterClient,
                                MemberSessionTransactionService transactionService,
                                PlatformSessionRepository platformSessionRepository,
                                PlatformSessionTokenIssuer tokenIssuer,
                                BusinessClock clock,
                                long sessionTtlSeconds) {
        this(memberCenterClient, transactionService, platformSessionRepository,
                PlatformSessionCache.noOp(), tokenIssuer, clock, sessionTtlSeconds);
    }

    public SessionExchangeResult exchange(String memberToken, String requestId) {
        validateMemberToken(memberToken);
        VerifiedMember verified = memberCenterClient.introspect(memberToken, requestId);
        if (verified == null || verified.status() == null || verified.tokenExpiresAt() == null
                || verified.externalMemberNo() == null || verified.externalMemberNo().trim().isEmpty()) {
            throw new QingheBusinessException(QingheErrorCode.TEMPORARY_UNAVAILABLE,
                    "member center returned an incomplete identity");
        }
        if (verified.status() == MemberStatus.FROZEN) {
            throw new QingheBusinessException(QingheErrorCode.MEMBER_FROZEN, "member is frozen");
        }
        if (verified.status() == MemberStatus.CANCELLED) {
            throw new QingheBusinessException(QingheErrorCode.MEMBER_CANCELLED, "member is cancelled");
        }
        Instant now = clock.instant();
        Instant expiresAt = now.plus(sessionTtl);
        if (verified.tokenExpiresAt().isBefore(expiresAt)) {
            expiresAt = verified.tokenExpiresAt();
        }
        if (!expiresAt.isAfter(now)) {
            throw new QingheBusinessException(QingheErrorCode.TOKEN_EXPIRED, "member token is expired");
        }
        return transactionService.createSession(verified, expiresAt);
    }

    public AuthenticatedMember authenticate(String accessToken) {
        if (accessToken == null || accessToken.trim().isEmpty()) {
            throw new QingheBusinessException(QingheErrorCode.UNAUTHENTICATED, "platform session is required");
        }
        LocalDateTime now = clock.dateTime();
        String accessTokenHash = tokenIssuer.hash(accessToken);
        try {
            Optional<AuthenticatedMember> cached = platformSessionCache.find(accessTokenHash);
            if (cached.isPresent() && cached.get().expiresAt().isAfter(now)) {
                return cached.get();
            }
        } catch (RuntimeException cacheFailure) {
            LOGGER.warn("Platform session cache read failed; falling back to MySQL");
        }
        AuthenticatedMember authenticated = platformSessionRepository.findActiveMember(accessTokenHash, now)
                .orElseThrow(() -> new QingheBusinessException(
                        QingheErrorCode.TOKEN_EXPIRED, "platform session is invalid or expired"));
        try {
            platformSessionCache.put(accessTokenHash, authenticated);
        } catch (RuntimeException cacheFailure) {
            LOGGER.warn("Platform session cache write failed; MySQL authentication remains authoritative");
        }
        return authenticated;
    }

    private static void validateMemberToken(String memberToken) {
        if (memberToken == null || memberToken.trim().length() < 8 || memberToken.length() > 2048) {
            throw new QingheBusinessException(QingheErrorCode.TOKEN_INVALID, "member token is invalid");
        }
    }
}
