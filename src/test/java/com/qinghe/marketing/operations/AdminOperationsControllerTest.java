package com.qinghe.marketing.operations;

import com.qinghe.marketing.identity.AdminAuthorizer;
import com.qinghe.marketing.identity.AdminPrincipal;
import com.qinghe.marketing.shared.web.QingheExceptionAdvice;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.Collections;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminOperationsControllerTest {
    @Test
    void shouldQueryTraceAndExceptionsThroughOperationReadPermission() throws Exception {
        OperationsQueryService queries = mock(OperationsQueryService.class);
        AdminAuthorizer authorizer = mock(AdminAuthorizer.class);
        when(authorizer.require("Bearer ops", "operation:read")).thenReturn(
                new AdminPrincipal("OPS-009", Collections.singleton("operation:read")));
        when(queries.trace(BusinessIdentifierType.POS_REQUEST_NO, "POS-001"))
                .thenReturn(new BusinessTraceView(BusinessIdentifierType.POS_REQUEST_NO,
                        "POS-001", Collections.singletonList(new BusinessTraceNode("REDEMPTION",
                        "RDM-001", "SUCCESS", null,
                        LocalDateTime.of(2026, 9, 10, 13, 0)))));
        when(queries.exceptions("OUTBOX_DEAD", "DEAD", 1, 20)).thenReturn(
                new OperationalExceptionPage(1, 20, 1,
                        Collections.singletonList(new OperationalExceptionView("OUTBOX_DEAD",
                                "EVT-001", "DEAD", "RETRY_EXHAUSTED",
                                LocalDateTime.of(2026, 9, 10, 13, 1), "HIGH",
                                Collections.singletonList("TRACE_ONLY")))));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                        new AdminOperationsController(queries, authorizer))
                .setControllerAdvice(new QingheExceptionAdvice()).build();

        mvc.perform(get("/api/v1/admin/business-traces")
                        .param("identifierType", "POS_REQUEST_NO")
                        .param("identifierValue", "POS-001")
                        .header("Authorization", "Bearer ops"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.timeline[0].businessId").value("RDM-001"));
        mvc.perform(get("/api/v1/admin/exceptions")
                        .param("exceptionType", "OUTBOX_DEAD").param("status", "DEAD")
                        .header("Authorization", "Bearer ops"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].severity").value("HIGH"));
        verify(authorizer, org.mockito.Mockito.times(2))
                .require("Bearer ops", "operation:read");
    }

    @Test
    void shouldRejectUnknownIdentifierType() throws Exception {
        AdminAuthorizer authorizer = mock(AdminAuthorizer.class);
        when(authorizer.require("Bearer ops", "operation:read")).thenReturn(
                new AdminPrincipal("OPS-009", Collections.singleton("operation:read")));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new AdminOperationsController(
                        mock(OperationsQueryService.class), authorizer))
                .setControllerAdvice(new QingheExceptionAdvice()).build();

        mvc.perform(get("/api/v1/admin/business-traces")
                        .param("identifierType", "PHONE")
                        .param("identifierValue", "13800000000")
                        .header("Authorization", "Bearer ops"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"));
    }
}
