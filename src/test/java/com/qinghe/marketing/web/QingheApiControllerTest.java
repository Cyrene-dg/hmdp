package com.qinghe.marketing.web;

import com.qinghe.marketing.identity.AdminAuthorizer;
import com.qinghe.marketing.identity.ConfiguredAdminAuthorizer;
import com.qinghe.marketing.identity.MemberSessionController;
import com.qinghe.marketing.identity.MemberSessionService;
import com.qinghe.marketing.identity.SessionExchangeResult;
import com.qinghe.marketing.shared.error.QingheBusinessException;
import com.qinghe.marketing.shared.error.QingheErrorCode;
import com.qinghe.marketing.shared.web.QingheExceptionAdvice;
import com.qinghe.marketing.store.AdminStoreImportController;
import com.qinghe.marketing.store.StoreImportPreview;
import com.qinghe.marketing.store.StoreImportService;
import com.qinghe.marketing.store.StoreImportStatus;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class QingheApiControllerTest {

    @Test
    void shouldReturnContractShapedMemberSessionWithoutEchoingExternalToken() throws Exception {
        MemberSessionService service = mock(MemberSessionService.class);
        when(service.exchange(eq("external-member-token"), eq("req-member-0001")))
                .thenReturn(new SessionExchangeResult("platform-token", Instant.parse("2026-09-08T08:00:00Z"),
                        "M10***86", "NORMAL"));
        MockMvc mvc = memberMvc(service);

        mvc.perform(post("/api/v1/member/sessions/exchange")
                        .header("X-Request-Id", "req-member-0001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"memberToken\":\"external-member-token\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.requestId").value("req-member-0001"))
                .andExpect(jsonPath("$.data.accessToken").value("platform-token"))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.member.memberNoMasked").value("M10***86"))
                .andExpect(jsonPath("$..memberToken").doesNotExist());
    }

    @Test
    void shouldPreserveFrozenAndDependencyFailureSemantics() throws Exception {
        MemberSessionService service = mock(MemberSessionService.class);
        when(service.exchange(eq("frozen-member-token"), any()))
                .thenThrow(new QingheBusinessException(QingheErrorCode.MEMBER_FROZEN, "member is frozen"));
        when(service.exchange(eq("timeout-member-token"), any()))
                .thenThrow(new QingheBusinessException(QingheErrorCode.TEMPORARY_UNAVAILABLE,
                        "member center is temporarily unavailable"));
        MockMvc mvc = memberMvc(service);

        mvc.perform(post("/api/v1/member/sessions/exchange")
                        .header("X-Request-Id", "req-frozen-0001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"memberToken\":\"frozen-member-token\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MEMBER_FROZEN"));
        mvc.perform(post("/api/v1/member/sessions/exchange")
                        .header("X-Request-Id", "req-timeout-0001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"memberToken\":\"timeout-member-token\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("SYSTEM_BUSY"));
    }

    @Test
    void shouldRejectMemberWriteWithoutValidRequestId() throws Exception {
        MemberSessionService service = mock(MemberSessionService.class);

        memberMvc(service).perform(post("/api/v1/member/sessions/exchange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"memberToken\":\"external-member-token\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    void shouldRejectMissingAndUnderprivilegedAdminTokens() throws Exception {
        StoreImportService service = mock(StoreImportService.class);
        MockMultipartFile file = csvFile();

        MockMvc missing = storeMvc(service, new ConfiguredAdminAuthorizer("secret-token", "ops-01", "store:import"));
        missing.perform(multipart("/api/v1/admin/store-imports/preview").file(file))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        MockMvc forbidden = storeMvc(service,
                new ConfiguredAdminAuthorizer("secret-token", "ops-01", "campaign:edit"));
        forbidden.perform(multipart("/api/v1/admin/store-imports/preview").file(csvFile())
                        .header("Authorization", "Bearer secret-token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void shouldAuthorizeAndPreviewStoreFileWithoutUsingCallerSuppliedOperatorId() throws Exception {
        StoreImportService service = mock(StoreImportService.class);
        when(service.preview(any(byte[].class), eq("ops-01"))).thenReturn(new StoreImportPreview(
                "SIMP-20260908120000-ABCDEF012345", StoreImportStatus.PREVIEWED,
                "STORE-20260908-01", 1, 1, Collections.emptyList()));
        AdminAuthorizer authorizer = new ConfiguredAdminAuthorizer(
                "secret-token", "ops-01", "store:import,campaign:edit");

        storeMvc(service, authorizer).perform(multipart("/api/v1/admin/store-imports/preview")
                        .file(csvFile())
                        .header("Authorization", "Bearer secret-token")
                        .header("X-Request-Id", "store-import-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestId").value("store-import-001"))
                .andExpect(jsonPath("$.data.status").value("PREVIEWED"))
                .andExpect(jsonPath("$.data.sourceVersion").value("STORE-20260908-01"));
        verify(service).preview(any(byte[].class), eq("ops-01"));
    }

    @Test
    void shouldReturnContractErrorForMalformedJsonAndMissingMultipartFile() throws Exception {
        MemberSessionService memberSessions = mock(MemberSessionService.class);
        memberMvc(memberSessions).perform(post("/api/v1/member/sessions/exchange")
                        .header("X-Request-Id", "req-malformed-01")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not-json}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"))
                .andExpect(jsonPath("$.requestId").value("req-malformed-01"));

        StoreImportService storeImports = mock(StoreImportService.class);
        AdminAuthorizer authorizer = new ConfiguredAdminAuthorizer(
                "secret-token", "ops-01", "store:import");
        storeMvc(storeImports, authorizer).perform(multipart("/api/v1/admin/store-imports/preview")
                        .header("Authorization", "Bearer secret-token")
                        .header("X-Request-Id", "req-no-file-0001"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"));
    }

    private static MockMvc memberMvc(MemberSessionService service) {
        return MockMvcBuilders.standaloneSetup(new MemberSessionController(service))
                .setControllerAdvice(new QingheExceptionAdvice()).build();
    }

    private static MockMvc storeMvc(StoreImportService service, AdminAuthorizer authorizer) {
        return MockMvcBuilders.standaloneSetup(new AdminStoreImportController(service, authorizer))
                .setControllerAdvice(new QingheExceptionAdvice()).build();
    }

    private static MockMultipartFile csvFile() {
        return new MockMultipartFile("file", "stores.csv", "text/csv",
                ("source_version,store_code,store_name,ownership_type,status,pos_version\n"
                        + "STORE-20260908-01,QH001,青禾一店,DIRECT,ACTIVE,POS-3.2\n")
                        .getBytes(StandardCharsets.UTF_8));
    }
}
