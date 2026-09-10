package com.qinghe.marketing.reconciliation;

import com.qinghe.marketing.identity.AdminAuthorizer;
import com.qinghe.marketing.identity.AdminPrincipal;
import com.qinghe.marketing.shared.web.QingheExceptionAdvice;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminReconciliationControllerTest {
    @Test
    void shouldListAndRetryThroughReconOperatePermission() throws Exception {
        ReconciliationAdminQueryService queries=mock(ReconciliationAdminQueryService.class);
        ReconciliationRetryService retries=mock(ReconciliationRetryService.class);
        AdminAuthorizer authorizer=mock(AdminAuthorizer.class);
        when(authorizer.require("Bearer recon","recon:operate")).thenReturn(
                new AdminPrincipal("OPS-008",Collections.singleton("recon:operate")));
        when(queries.list(null,ReconciliationBatchStatus.MATCHING,1,20))
                .thenReturn(new ReconciliationPage(1,20,0,Collections.emptyList()));
        ReconciliationBatchView completed=view("COMPLETED",4);
        when(retries.retry("REC202609090088",3,"继续匹配","OPS-008",
                "recon-retry-001")).thenReturn(completed);
        MockMvc mvc=MockMvcBuilders.standaloneSetup(
                new AdminReconciliationController(queries,retries,authorizer))
                .setControllerAdvice(new QingheExceptionAdvice()).build();

        mvc.perform(get("/api/v1/admin/recon-batches").param("status","MATCHING")
                        .header("Authorization","Bearer recon"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(0));
        mvc.perform(post("/api/v1/admin/recon-batches/REC202609090088/retry")
                        .header("Authorization","Bearer recon")
                        .header("X-Request-Id","recon-retry-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":3,\"comment\":\"继续匹配\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));
        verify(authorizer,org.mockito.Mockito.times(2))
                .require("Bearer recon","recon:operate");
        verify(retries).retry("REC202609090088",3,"继续匹配","OPS-008",
                "recon-retry-001");
    }

    @Test
    void shouldRejectUnknownStatusBeforeQuery() throws Exception {
        AdminAuthorizer authorizer=mock(AdminAuthorizer.class);
        when(authorizer.require("Bearer recon","recon:operate")).thenReturn(
                new AdminPrincipal("OPS-008",Collections.singleton("recon:operate")));
        MockMvc mvc=MockMvcBuilders.standaloneSetup(new AdminReconciliationController(
                mock(ReconciliationAdminQueryService.class),mock(ReconciliationRetryService.class),
                authorizer)).setControllerAdvice(new QingheExceptionAdvice()).build();
        mvc.perform(get("/api/v1/admin/recon-batches").param("status","SUCCESS")
                        .header("Authorization","Bearer recon"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"));
    }

    private static ReconciliationBatchView view(String status,long version) {
        return new ReconciliationBatchView(88,"REC202609090088","MOCK_POS",
                "POSB20260908088",null,"checksum","POS_20260908_POSB20260908088.csv",
                status,1,1,0,1,0,0,1,null,version,null,null,null);
    }
}
