package com.qinghe.marketing.campaign;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qinghe.marketing.identity.AdminAuthorizer;
import com.qinghe.marketing.identity.AdminPrincipal;
import com.qinghe.marketing.shared.web.QingheExceptionAdvice;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminCampaignControllerTest {

    @Test
    void shouldCreateTemplateThroughCampaignEditBoundary() throws Exception {
        BenefitTemplateService templates = mock(BenefitTemplateService.class);
        AdminAuthorizer authorizer = mock(AdminAuthorizer.class);
        when(authorizer.require("Bearer admin", "campaign:edit"))
                .thenReturn(new AdminPrincipal("MKT-001", Collections.singleton("campaign:edit")));
        BenefitTemplateDraft draft = new BenefitTemplateDraft("饮品券", BenefitType.FREE_PRODUCT,
                "免费饮品", null, "DRINK-1", null, ValidityType.RELATIVE_DAYS,
                7, null, null, "{}");
        when(templates.create(any(BenefitTemplateDraft.class))).thenReturn(
                new BenefitTemplate(1L, "TPL-1", draft, BenefitTemplateStatus.ACTIVE, 0L));
        MockMvc mvc = mvc(templates, mock(CampaignService.class),
                mock(InventoryAdjustmentService.class), authorizer);

        mvc.perform(post("/api/v1/admin/benefit-templates")
                        .header("Authorization", "Bearer admin")
                        .header("X-Request-Id", "template-create-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"templateName\":\"饮品券\",\"benefitType\":\"FREE_PRODUCT\","
                                + "\"title\":\"免费饮品\",\"productCode\":\"DRINK-1\","
                                + "\"validityType\":\"RELATIVE_DAYS\",\"validityValue\":7,"
                                + "\"usageRules\":{\"allowStacking\":false}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.templateNo").value("TPL-1"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
        verify(authorizer).require("Bearer admin", "campaign:edit");
    }

    @Test
    void shouldCreateCampaignAndUseAuthenticatedOperatorInsteadOfRequestBodyIdentity() throws Exception {
        CampaignService campaigns = mock(CampaignService.class);
        AdminAuthorizer authorizer = mock(AdminAuthorizer.class);
        when(authorizer.require("Bearer admin", "campaign:edit"))
                .thenReturn(new AdminPrincipal("MKT-001", Collections.singleton("campaign:edit")));
        Campaign created = campaign(CampaignStatus.DRAFT, 0L);
        when(campaigns.createDraft(any(CampaignDraftCommand.class), eq("MKT-001"))).thenReturn(created);
        MockMvc mvc = mvc(mock(BenefitTemplateService.class), campaigns,
                mock(InventoryAdjustmentService.class), authorizer);

        mvc.perform(post("/api/v1/admin/campaigns")
                        .header("Authorization", "Bearer admin")
                        .header("X-Request-Id", "campaign-create-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"九月回馈\",\"description\":\"试点\","
                                + "\"templateNo\":\"TPL-1\","
                                + "\"claimBeginAt\":\"2026-09-15T10:00:00+08:00\","
                                + "\"claimEndAt\":\"2026-09-22T22:00:00+08:00\","
                                + "\"initialStock\":1000,\"memberClaimLimit\":1,"
                                + "\"storeCodes\":[\"QH001\",\"QH006\"],"
                                + "\"franchiseSubsidyFen\":350}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.campaignNo").value("CAM-1"))
                .andExpect(jsonPath("$.data.status").value("DRAFT"));
        verify(campaigns).createDraft(any(CampaignDraftCommand.class), eq("MKT-001"));
    }

    @Test
    void shouldReturnAppliedInventoryTotalsFromReview() throws Exception {
        InventoryAdjustmentService inventory = mock(InventoryAdjustmentService.class);
        AdminAuthorizer authorizer = mock(AdminAuthorizer.class);
        when(authorizer.require("Bearer reviewer", "inventory:review"))
                .thenReturn(new AdminPrincipal("OPS-002", Collections.singleton("inventory:review")));
        InventoryAdjustment applied = new InventoryAdjustment(20L, "IAD-1", 10L, 200L,
                InventoryAdjustmentStatus.APPLIED, "追加", "MKT-001", "OPS-002", 3L);
        when(inventory.review("IAD-1", ReviewDecision.APPROVE, 1L, "OPS-002", "同意"))
                .thenReturn(new InventoryAdjustmentResult(applied, 1000L, 1200L));
        MockMvc mvc = mvc(mock(BenefitTemplateService.class), mock(CampaignService.class),
                inventory, authorizer);

        mvc.perform(post("/api/v1/admin/inventory-adjustments/IAD-1/reviews")
                        .header("Authorization", "Bearer reviewer")
                        .header("X-Request-Id", "inventory-review-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"APPROVE\",\"expectedVersion\":1,\"comment\":\"同意\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPLIED"))
                .andExpect(jsonPath("$.data.beforeTotalStock").value(1000))
                .andExpect(jsonPath("$.data.afterTotalStock").value(1200));
    }

    @Test
    void shouldRejectWriteWithoutStableRequestId() throws Exception {
        AdminAuthorizer authorizer = mock(AdminAuthorizer.class);
        when(authorizer.require("Bearer admin", "campaign:edit"))
                .thenReturn(new AdminPrincipal("MKT-001", Collections.singleton("campaign:edit")));
        MockMvc mvc = mvc(mock(BenefitTemplateService.class), mock(CampaignService.class),
                mock(InventoryAdjustmentService.class), authorizer);

        mvc.perform(post("/api/v1/admin/campaigns")
                        .header("Authorization", "Bearer admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"));
    }

    private static MockMvc mvc(BenefitTemplateService templates, CampaignService campaigns,
                               InventoryAdjustmentService inventory, AdminAuthorizer authorizer) {
        AdminCampaignController controller = new AdminCampaignController(
                templates, campaigns, inventory, authorizer, new ObjectMapper());
        return MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new QingheExceptionAdvice()).build();
    }

    private static Campaign campaign(CampaignStatus status, long version) {
        return new Campaign(10L, "CAM-1", 1L, "九月回馈", "试点", status,
                LocalDateTime.of(2026, 9, 15, 10, 0),
                LocalDateTime.of(2026, 9, 22, 22, 0),
                1, 350L, 1L, version, "MKT-001");
    }
}
