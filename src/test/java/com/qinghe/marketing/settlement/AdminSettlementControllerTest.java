package com.qinghe.marketing.settlement;

import com.qinghe.marketing.identity.AdminAuthorizer;
import com.qinghe.marketing.identity.AdminPrincipal;
import com.qinghe.marketing.shared.web.QingheExceptionAdvice;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminSettlementControllerTest {
    @Test
    void shouldReturnExplicitNoSettlementRequiredOutcome() throws Exception {
        SettlementGenerationService generation=mock(SettlementGenerationService.class);
        AdminAuthorizer authorizer=mock(AdminAuthorizer.class);
        when(authorizer.require("Bearer finance","settlement:read")).thenReturn(
                new AdminPrincipal("FIN-008",Collections.singleton("settlement:read")));
        when(generation.generate(org.mockito.ArgumentMatchers.eq("REC202609090088"),
                any(SettlementGenerateCommand.class))).thenReturn(new SettlementGenerationResult(
                SettlementGenerationOutcome.NO_SETTLEMENT_REQUIRED,"REC202609090088",null));
        MockMvc mvc=mvc(generation,mock(SettlementConfirmationService.class),
                mock(SettlementExportService.class),mock(SettlementAdminQueryService.class),authorizer);

        mvc.perform(post("/api/v1/admin/recon-batches/REC202609090088/settlement-batch")
                        .header("Authorization","Bearer finance")
                        .header("X-Request-Id","settlement-generate-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedReconVersion\":5,\"comment\":\"没有加盟明细\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("NO_SETTLEMENT_REQUIRED"))
                .andExpect(jsonPath("$.data.detailCount").value(0));
        verify(authorizer).require("Bearer finance","settlement:read");
    }

    @Test
    void shouldConfirmWithFinanceIdentityAndExportCsv() throws Exception {
        SettlementConfirmationService confirmation=mock(SettlementConfirmationService.class);
        SettlementExportService exports=mock(SettlementExportService.class);
        AdminAuthorizer authorizer=mock(AdminAuthorizer.class);
        when(authorizer.require("Bearer finance","settlement:confirm")).thenReturn(
                new AdminPrincipal("FIN-008",Collections.singleton("settlement:confirm")));
        when(authorizer.require("Bearer finance","settlement:read")).thenReturn(
                new AdminPrincipal("FIN-008",Collections.singleton("settlement:read")));
        SettlementBatch confirmed=new SettlementBatch(1,"SET202609090001",88,
                "REC202609090088",LocalDate.of(2026,9,8),SettlementBatchStatus.CONFIRMED,
                1,1,350,2,"FIN-008",null);
        when(confirmation.confirm(org.mockito.ArgumentMatchers.eq("SET202609090001"),
                any(SettlementConfirmCommand.class))).thenReturn(confirmed);
        when(exports.export("SET202609090001","FIN-008","settlement-export-001"))
                .thenReturn(new SettlementExport("QH_SETTLEMENT_SET202609090001_20260909101500.csv",
                        "header\r\nrow\r\n".getBytes(StandardCharsets.UTF_8)));
        MockMvc mvc=mvc(mock(SettlementGenerationService.class),confirmation,exports,
                mock(SettlementAdminQueryService.class),authorizer);

        mvc.perform(post("/api/v1/admin/settlement-batches/SET202609090001/confirm")
                        .header("Authorization","Bearer finance")
                        .header("X-Request-Id","settlement-confirm-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":1,\"expectedDetailCount\":1,"
                                + "\"expectedTotalSubsidyFen\":350,\"comment\":\"复核一致\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.totalSubsidyFen").value(350));
        mvc.perform(get("/api/v1/admin/settlement-batches/SET202609090001/export")
                        .header("Authorization","Bearer finance")
                        .header("X-Request-Id","settlement-export-001"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"QH_SETTLEMENT_SET202609090001_20260909101500.csv\""))
                .andExpect(content().string("header\r\nrow\r\n"));
    }

    private static MockMvc mvc(SettlementGenerationService generation,
            SettlementConfirmationService confirmation,SettlementExportService exports,
            SettlementAdminQueryService queries,AdminAuthorizer authorizer) {
        return MockMvcBuilders.standaloneSetup(new AdminSettlementController(generation,
                confirmation,exports,queries,authorizer))
                .setControllerAdvice(new QingheExceptionAdvice()).build();
    }
}
