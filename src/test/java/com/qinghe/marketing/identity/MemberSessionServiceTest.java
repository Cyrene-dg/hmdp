package com.qinghe.marketing.identity;

import com.qinghe.marketing.shared.clock.BusinessClock;
import com.qinghe.marketing.shared.error.QingheBusinessException;
import com.qinghe.marketing.shared.error.QingheErrorCode;
import com.qinghe.marketing.shared.id.BusinessIdGenerator;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MemberSessionServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-08T07:00:00Z");
    private final BusinessClock clock = () -> NOW;

    @Test
    void shouldRejectNonPositiveSessionTtlAtStartup() {
        assertThrows(IllegalArgumentException.class,
                () -> new MemberSessionService(null, null, null, null, null, clock, 0L));
    }

    @Test
    void shouldCreateDifferentPlatformTokensForOneStableMemberMapping() {
        InMemoryMemberMappingRepository mappings = new InMemoryMemberMappingRepository();
        InMemoryPlatformSessionRepository sessions = new InMemoryPlatformSessionRepository(mappings);
        MemberSessionService service = service(mappings, sessions,
                (token, requestId) -> activeMember(NOW.plusSeconds(3600)));

        SessionExchangeResult first = service.exchange("external-token-one", "request-one");
        SessionExchangeResult second = service.exchange("external-token-two", "request-two");

        assertEquals(1, mappings.size());
        assertEquals(2, sessions.size());
        assertNotEquals(first.accessToken(), second.accessToken());
        assertEquals("M10***86", first.maskedMemberNo());
        assertEquals("M100086", service.authenticate(first.accessToken()).externalMemberNo());
    }

    @Test
    void shouldLimitPlatformSessionToExternalTokenExpiry() {
        InMemoryMemberMappingRepository mappings = new InMemoryMemberMappingRepository();
        InMemoryPlatformSessionRepository sessions = new InMemoryPlatformSessionRepository(mappings);
        MemberSessionService service = service(mappings, sessions,
                (token, requestId) -> activeMember(NOW.plusSeconds(60)));

        SessionExchangeResult result = service.exchange("external-token-one", "request-one");

        assertEquals(NOW.plusSeconds(60), result.expiresAt());
    }

    @Test
    void shouldNotPersistAnythingWhenMemberCenterRejectsOrTimesOut() {
        InMemoryMemberMappingRepository mappings = new InMemoryMemberMappingRepository();
        InMemoryPlatformSessionRepository sessions = new InMemoryPlatformSessionRepository(mappings);
        MemberSessionService frozen = service(mappings, sessions, (token, requestId) -> {
            throw new QingheBusinessException(QingheErrorCode.MEMBER_FROZEN, "member is frozen");
        });
        MemberSessionService timeout = service(mappings, sessions, (token, requestId) -> {
            throw new QingheBusinessException(QingheErrorCode.TEMPORARY_UNAVAILABLE, "timeout");
        });

        assertEquals(QingheErrorCode.MEMBER_FROZEN,
                assertThrows(QingheBusinessException.class,
                        () -> frozen.exchange("external-token-one", "request-one")).errorCode());
        assertEquals(QingheErrorCode.TEMPORARY_UNAVAILABLE,
                assertThrows(QingheBusinessException.class,
                        () -> timeout.exchange("external-token-two", "request-two")).errorCode());
        assertEquals(0, mappings.size());
        assertEquals(0, sessions.size());
    }

    @Test
    void shouldCacheValidatedPlatformSessionAndAvoidRepeatedDatabaseReads() {
        InMemoryMemberMappingRepository mappings = new InMemoryMemberMappingRepository();
        InMemoryPlatformSessionRepository sessions = new InMemoryPlatformSessionRepository(mappings);
        InMemoryPlatformSessionCache cache = new InMemoryPlatformSessionCache();
        MemberSessionService service = service(mappings, sessions,
                (token, requestId) -> activeMember(NOW.plusSeconds(3600)), cache);
        SessionExchangeResult exchanged = service.exchange("external-token-one", "request-one");

        assertEquals("M100086", service.authenticate(exchanged.accessToken()).externalMemberNo());
        assertEquals("M100086", service.authenticate(exchanged.accessToken()).externalMemberNo());
        assertEquals(1, sessions.readCount);
        assertEquals(1, cache.values.size());
    }

    @Test
    void shouldUseMysqlAuthorityWhenRedisCacheFails() {
        InMemoryMemberMappingRepository mappings = new InMemoryMemberMappingRepository();
        InMemoryPlatformSessionRepository sessions = new InMemoryPlatformSessionRepository(mappings);
        PlatformSessionCache failingCache = new PlatformSessionCache() {
            @Override
            public Optional<AuthenticatedMember> find(String accessTokenHash) {
                throw new IllegalStateException("redis unavailable");
            }

            @Override
            public void put(String accessTokenHash, AuthenticatedMember member) {
                throw new IllegalStateException("redis unavailable");
            }
        };
        MemberSessionService service = service(mappings, sessions,
                (token, requestId) -> activeMember(NOW.plusSeconds(3600)), failingCache);
        SessionExchangeResult exchanged = service.exchange("external-token-one", "request-one");

        assertEquals("M100086", service.authenticate(exchanged.accessToken()).externalMemberNo());
        assertEquals(1, sessions.readCount);
    }

    private MemberSessionService service(InMemoryMemberMappingRepository mappings,
                                         InMemoryPlatformSessionRepository sessions,
                                         MemberCenterClient client) {
        PlatformSessionTokenIssuer tokenIssuer = new PlatformSessionTokenIssuer(new SecureRandom());
        MemberSessionTransactionService transactionService = new MemberSessionTransactionService(
                mappings, sessions, new PlatformMemberIdGenerator(new SecureRandom()), tokenIssuer,
                new BusinessIdGenerator(clock), clock);
        return new MemberSessionService(client, transactionService, sessions, tokenIssuer,
                clock, 1800L);
    }

    private MemberSessionService service(InMemoryMemberMappingRepository mappings,
                                         InMemoryPlatformSessionRepository sessions,
                                         MemberCenterClient client,
                                         PlatformSessionCache cache) {
        PlatformSessionTokenIssuer tokenIssuer = new PlatformSessionTokenIssuer(new SecureRandom());
        MemberSessionTransactionService transactionService = new MemberSessionTransactionService(
                mappings, sessions, new PlatformMemberIdGenerator(new SecureRandom()), tokenIssuer,
                new BusinessIdGenerator(clock), clock);
        return new MemberSessionService(client, transactionService, sessions, cache, tokenIssuer,
                clock, 1800L);
    }

    private static VerifiedMember activeMember(Instant expiresAt) {
        return new VerifiedMember("M100086", MemberStatus.ACTIVE, "NORMAL", expiresAt);
    }

    private static final class InMemoryMemberMappingRepository implements MemberMappingRepository {
        private final Map<String, MemberMapping> mappings = new LinkedHashMap<String, MemberMapping>();

        @Override
        public Optional<MemberMapping> findByExternalMemberNo(String externalMemberNo) {
            return Optional.ofNullable(mappings.get(externalMemberNo));
        }

        @Override
        public MemberMapping findOrCreate(String externalMemberNo, long candidatePlatformUserId,
                                          MemberStatus statusSnapshot, LocalDateTime now) {
            MemberMapping existing = mappings.get(externalMemberNo);
            if (existing != null) {
                return existing;
            }
            MemberMapping created = new MemberMapping(mappings.size() + 1L,
                    externalMemberNo, candidatePlatformUserId, statusSnapshot);
            mappings.put(externalMemberNo, created);
            return created;
        }

        int size() {
            return mappings.size();
        }
    }

    private static final class InMemoryPlatformSessionRepository implements PlatformSessionRepository {
        private final InMemoryMemberMappingRepository mappings;
        private final List<PlatformSessionRecord> sessions = new ArrayList<PlatformSessionRecord>();
        private int readCount;

        private InMemoryPlatformSessionRepository(InMemoryMemberMappingRepository mappings) {
            this.mappings = mappings;
        }

        @Override
        public void save(PlatformSessionRecord session) {
            sessions.add(session);
        }

        @Override
        public Optional<AuthenticatedMember> findActiveMember(String accessTokenHash, LocalDateTime now) {
            readCount++;
            for (PlatformSessionRecord session : sessions) {
                if (session.accessTokenHash().equals(accessTokenHash) && session.expiresAt().isAfter(now)) {
                    for (MemberMapping mapping : mappings.mappings.values()) {
                        if (mapping.id() == session.memberId()) {
                            return Optional.of(new AuthenticatedMember(mapping.id(),
                                    mapping.platformUserId(), mapping.externalMemberNo(), session.expiresAt()));
                        }
                    }
                }
            }
            return Optional.empty();
        }

        int size() {
            return sessions.size();
        }
    }

    private static final class InMemoryPlatformSessionCache implements PlatformSessionCache {
        private final Map<String, AuthenticatedMember> values =
                new LinkedHashMap<String, AuthenticatedMember>();

        @Override
        public Optional<AuthenticatedMember> find(String accessTokenHash) {
            return Optional.ofNullable(values.get(accessTokenHash));
        }

        @Override
        public void put(String accessTokenHash, AuthenticatedMember member) {
            values.put(accessTokenHash, member);
        }
    }
}
