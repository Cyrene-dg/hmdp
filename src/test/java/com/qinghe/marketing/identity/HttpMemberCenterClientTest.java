package com.qinghe.marketing.identity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qinghe.marketing.contract.mock.MemberCenterMockServer;
import com.qinghe.marketing.shared.clock.BusinessClock;
import com.qinghe.marketing.shared.error.QingheBusinessException;
import com.qinghe.marketing.shared.error.QingheErrorCode;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HttpMemberCenterClientTest {

    private static final BusinessClock CLOCK = () -> Instant.parse("2026-09-08T07:00:00Z");

    @Test
    void shouldMapActiveMemberFromMockContract() throws Exception {
        try (MemberCenterMockServer mock = MemberCenterMockServer.start(0, 300L)) {
            VerifiedMember member = client(mock, 800).introspect("mock-member-token-active", "req-active-001");

            assertEquals("M100086", member.externalMemberNo());
            assertEquals(MemberStatus.ACTIVE, member.status());
            assertEquals("NORMAL", member.levelCode());
        }
    }

    @Test
    void shouldKeepInvalidFrozenAndRateLimitedFailuresDistinct() throws Exception {
        try (MemberCenterMockServer mock = MemberCenterMockServer.start(0, 300L)) {
            assertCode(QingheErrorCode.TOKEN_INVALID,
                    () -> client(mock, 800).introspect("mock-member-token-invalid", "req-invalid-001"));
            assertCode(QingheErrorCode.MEMBER_FROZEN,
                    () -> client(mock, 800).introspect("mock-member-token-frozen", "req-frozen-001"));
            assertCode(QingheErrorCode.RATE_LIMITED,
                    () -> client(mock, 800).introspect("mock-member-token-rate-limited", "req-rate-001"));
        }
    }

    @Test
    void shouldTreatTimeoutAndMalformedSuccessAsTemporaryFailure() throws Exception {
        try (MemberCenterMockServer mock = MemberCenterMockServer.start(0, 400L)) {
            assertCode(QingheErrorCode.TEMPORARY_UNAVAILABLE,
                    () -> client(mock, 50).introspect("mock-member-token-timeout", "req-timeout-001"));
            assertCode(QingheErrorCode.TEMPORARY_UNAVAILABLE,
                    () -> client(mock, 800).introspect("mock-member-token-malformed", "req-malformed-001"));
        }
    }

    private HttpMemberCenterClient client(MemberCenterMockServer mock, int readTimeoutMillis) {
        return new HttpMemberCenterClient(new ObjectMapper(), CLOCK, mock.baseUrl(),
                "qinghe-rights", "test-only-member-center-key", 200, readTimeoutMillis);
    }

    private static void assertCode(QingheErrorCode expected, ThrowingAction action) {
        QingheBusinessException exception = assertThrows(QingheBusinessException.class, action::run);
        assertEquals(expected, exception.errorCode());
    }

    private interface ThrowingAction {
        void run();
    }
}
