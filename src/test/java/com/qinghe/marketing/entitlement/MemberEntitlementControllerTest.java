package com.qinghe.marketing.entitlement;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qinghe.marketing.campaign.BenefitType;
import com.qinghe.marketing.identity.AuthenticatedMember;
import com.qinghe.marketing.identity.MemberAuthorizer;
import com.qinghe.marketing.shared.web.QingheExceptionAdvice;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.Arrays;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MemberEntitlementControllerTest {

    @Test
    void shouldListOnlySafeCardFieldsAndRevealOnlyAMaskedCodeInOwnedDetail() throws Exception {
        MemberEntitlementService service = mock(MemberEntitlementService.class);
        MemberAuthorizer authorizer = mock(MemberAuthorizer.class);
        when(authorizer.require("Bearer platform-token"))
                .thenReturn(new AuthenticatedMember(20L, 200L, "MEM-20"));
        EntitlementView view = view();
        when(service.list(20L, EntitlementStatus.AVAILABLE, 1, 20))
                .thenReturn(new EntitlementPage(Arrays.asList(view), 1, 20, 1));
        when(service.requireOwned("ENT-1", 20L)).thenReturn(view);
        when(service.protectedRightCode(view)).thenReturn("****123456");
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                        new MemberEntitlementController(service, authorizer, new ObjectMapper()))
                .setControllerAdvice(new QingheExceptionAdvice()).build();

        mvc.perform(get("/api/v1/member/entitlements")
                        .param("status", "AVAILABLE")
                        .header("Authorization", "Bearer platform-token")
                        .header("X-Request-Id", "wallet-list-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].entitlementNo").value("ENT-1"))
                .andExpect(jsonPath("$.data.items[0].protectedRightCode").doesNotExist());

        mvc.perform(get("/api/v1/member/entitlements/ENT-1")
                        .header("Authorization", "Bearer platform-token")
                        .header("X-Request-Id", "wallet-detail-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.protectedRightCode").value("****123456"))
                .andExpect(jsonPath("$.data.usageRules.minAmountFen").value(0));
    }

    private static EntitlementView view() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 9, 16, 0);
        MemberEntitlement entitlement = new MemberEntitlement(40L, "ENT-1", repeat('a', 64),
                new byte[]{1, 2, 3}, 30L, 10L, 20L, EntitlementStatus.AVAILABLE,
                now, now.plusDays(7), 0L, now, now);
        return new EntitlementView(entitlement, "免费饮品", BenefitType.FREE_PRODUCT,
                "{\"minAmountFen\":0}", 2);
    }

    private static String repeat(char value, int count) {
        StringBuilder result = new StringBuilder(count);
        for (int index = 0; index < count; index++) result.append(value);
        return result.toString();
    }
}

