package com.qinghe.marketing.identity;

import com.qinghe.marketing.shared.error.QingheBusinessException;
import com.qinghe.marketing.shared.error.QingheErrorCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthenticationBoundaryTest {

    @Test
    void shouldPassOnlyPlatformBearerTokenToMemberSessionService() {
        MemberSessionService sessions = mock(MemberSessionService.class);
        AuthenticatedMember expected = new AuthenticatedMember(1L, 86L, "M100086");
        when(sessions.authenticate("platform-token")).thenReturn(expected);
        MemberAuthorizer authorizer = new MemberAuthorizer(sessions);

        assertEquals(expected, authorizer.require("Bearer platform-token"));
        verify(sessions).authenticate("platform-token");
        assertEquals(QingheErrorCode.UNAUTHENTICATED,
                assertThrows(QingheBusinessException.class,
                        () -> authorizer.require("Admin admin-token")).errorCode());
    }

    @Test
    void shouldNotEnableAdminAccessWhenNoCredentialIsConfigured() {
        ConfiguredAdminAuthorizer authorizer = new ConfiguredAdminAuthorizer(
                "", "local-admin", "store:import");

        assertEquals(QingheErrorCode.UNAUTHENTICATED,
                assertThrows(QingheBusinessException.class,
                        () -> authorizer.require("Bearer any-token", "store:import")).errorCode());
    }
}
