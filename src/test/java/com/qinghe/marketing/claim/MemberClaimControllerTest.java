package com.qinghe.marketing.claim;

import com.qinghe.marketing.identity.AuthenticatedMember;
import com.qinghe.marketing.identity.MemberAuthorizer;
import com.qinghe.marketing.shared.web.QingheExceptionAdvice;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MemberClaimControllerTest {

    @Test
    void shouldReturn202ForNewAcceptanceAnd200ForIdempotentReplay() throws Exception {
        ClaimService claims = mock(ClaimService.class);
        MemberAuthorizer authorizer = mock(MemberAuthorizer.class);
        when(authorizer.require("Bearer platform-token"))
                .thenReturn(new AuthenticatedMember(20L, 200L, "MEM-20"));
        ClaimRequest accepted = claim();
        when(claims.submit(eq("CAM-1"), eq(20L), eq("request-001"), any(OffsetDateTime.class)))
                .thenReturn(new ClaimSubmissionResult(accepted, false))
                .thenReturn(new ClaimSubmissionResult(accepted, true));
        MockMvc mvc = mvc(claims, authorizer);
        String body = "{\"clientRequestedAt\":\"2026-09-09T16:00:00+08:00\"}";

        mvc.perform(post("/api/v1/member/campaigns/CAM-1/claims")
                        .header("Authorization", "Bearer platform-token")
                        .header("X-Request-Id", "request-001")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.code").value("ACCEPTED"))
                .andExpect(jsonPath("$.data.claimNo").value("CLM-1"))
                .andExpect(jsonPath("$.data.status").value("PROCESSING"))
                .andExpect(jsonPath("$.data.nextPollAfterMillis").value(500));

        mvc.perform(post("/api/v1/member/campaigns/CAM-1/claims")
                        .header("Authorization", "Bearer platform-token")
                        .header("X-Request-Id", "request-001")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"));
    }

    @Test
    void shouldReturnOnlyTheCurrentMembersClaimAndRequireRequestId() throws Exception {
        ClaimService claims = mock(ClaimService.class);
        MemberAuthorizer authorizer = mock(MemberAuthorizer.class);
        when(authorizer.require("Bearer platform-token"))
                .thenReturn(new AuthenticatedMember(20L, 200L, "MEM-20"));
        when(claims.requireOwnedClaim("CLM-1", 20L)).thenReturn(claim());
        MockMvc mvc = mvc(claims, authorizer);

        mvc.perform(get("/api/v1/member/claims/CLM-1")
                        .header("Authorization", "Bearer platform-token")
                        .header("X-Request-Id", "query-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.claimNo").value("CLM-1"))
                .andExpect(jsonPath("$.data.updatedAt").exists());

        mvc.perform(get("/api/v1/member/claims/CLM-1")
                        .header("Authorization", "Bearer platform-token"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"));
    }

    private static MockMvc mvc(ClaimService claims, MemberAuthorizer authorizer) {
        return MockMvcBuilders.standaloneSetup(new MemberClaimController(claims, authorizer))
                .setControllerAdvice(new QingheExceptionAdvice()).build();
    }

    private static ClaimRequest claim() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 9, 16, 0);
        return new ClaimRequest(30L, "CLM-1", "request-001", repeat('a', 64),
                10L, 20L, "CAMPAIGN:10", "reservation-1", ClaimStatus.PROCESSING,
                null, 0L, now, now);
    }

    private static String repeat(char value, int count) {
        StringBuilder result = new StringBuilder(count);
        for (int index = 0; index < count; index++) {
            result.append(value);
        }
        return result.toString();
    }
}
