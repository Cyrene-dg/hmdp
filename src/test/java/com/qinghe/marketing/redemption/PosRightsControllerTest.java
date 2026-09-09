package com.qinghe.marketing.redemption;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qinghe.marketing.campaign.BenefitType;
import com.qinghe.marketing.entitlement.EntitlementStatus;
import com.qinghe.marketing.identity.PosAuthenticatedStore;
import com.qinghe.marketing.identity.PosAuthenticationRequest;
import com.qinghe.marketing.identity.PosRequestAuthenticator;
import com.qinghe.marketing.shared.error.QingheBusinessException;
import com.qinghe.marketing.shared.error.QingheErrorCode;
import com.qinghe.marketing.shared.web.QingheApiResponse;
import com.qinghe.marketing.store.StoreOwnershipType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PosRightsControllerTest {

    private ThreadPoolTaskExecutor executor;
    private PosRequestAuthenticator authenticator;
    private PosVerificationService verificationService;
    private PosRedemptionService redemptionService;
    private PosRightsController controller;
    private PosAuthenticatedStore store;

    @BeforeEach
    void setUp() {
        executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(2);
        executor.initialize();
        authenticator = mock(PosRequestAuthenticator.class);
        verificationService = mock(PosVerificationService.class);
        redemptionService = mock(PosRedemptionService.class);
        store = new PosAuthenticatedStore(1L, "S001", StoreOwnershipType.DIRECT, "POS-1");
        when(authenticator.authenticate(any(PosAuthenticationRequest.class))).thenReturn(store);
        PosExecutionGuard guard = new PosExecutionGuard(executor, new PosExecutionMetrics(), 2, 2000);
        controller = new PosRightsController(new ObjectMapper(), authenticator,
                verificationService, redemptionService, guard);
    }

    @AfterEach
    void tearDown() {
        executor.shutdown();
    }

    @Test
    void shouldAuthenticateExactRawBodyAndReturnVerificationData() {
        byte[] body = ("{\"posRequestNo\":\"REQ-0001\",\"storeCode\":\"S001\","
                + "\"terminalNo\":\"T-1\",\"rightCode\":\"RIGHT-0001\","
                + "\"requestedAt\":\"2026-09-09T16:00:00+08:00\"}")
                .getBytes(StandardCharsets.UTF_8);
        PosEntitlementSnapshot snapshot = new PosEntitlementSnapshot(1L, "ENT-1", 10L,
                EntitlementStatus.AVAILABLE, LocalDateTime.of(2026, 9, 9, 0, 0),
                LocalDateTime.of(2026, 9, 16, 0, 0), 0L, "免费饮品",
                BenefitType.FREE_PRODUCT, "DRINK", null);
        when(verificationService.verify(any(PosAuthenticatedStore.class),
                any(PosVerificationCommand.class))).thenReturn(new PosVerificationResult(snapshot));

        QingheApiResponse<PosRightsController.RightData> response = controller.verify(body,
                "REQ-0001", "POS-1", "1788940800", "nonce-0001", "signature",
                new MockHttpServletRequest());

        assertEquals("OK", response.getCode());
        assertEquals("ENT-1", response.getData().getRightNo());
        ArgumentCaptor<PosAuthenticationRequest> captured =
                ArgumentCaptor.forClass(PosAuthenticationRequest.class);
        verify(authenticator).authenticate(captured.capture());
        assertEquals("/openapi/v1/rights/verify", captured.getValue().normalizedPath());
        assertArrayEquals(body, captured.getValue().rawBody());
    }

    @Test
    void shouldRejectUnknownBodyFieldBeforeBusinessExecution() {
        byte[] body = ("{\"posRequestNo\":\"REQ-0001\",\"storeCode\":\"S001\","
                + "\"terminalNo\":\"T-1\",\"rightCode\":\"RIGHT-0001\","
                + "\"requestedAt\":\"2026-09-09T16:00:00+08:00\",\"extra\":1}")
                .getBytes(StandardCharsets.UTF_8);

        QingheBusinessException failure = assertThrows(QingheBusinessException.class,
                () -> controller.verify(body, "REQ-0001", "POS-1", "1788940800",
                        "nonce-0002", "signature", new MockHttpServletRequest()));

        assertEquals(QingheErrorCode.INVALID_ARGUMENT, failure.errorCode());
    }

    @Test
    void shouldReturnAcceptedWhileOriginalRequestIsStillProcessing() {
        when(redemptionService.query(store, "REQ-0001")).thenReturn(new RedemptionResult(
                "REQ-0001", PosRequestStatus.PROCESSING, null, null, null, null, null));

        ResponseEntity<QingheApiResponse<PosRightsController.RedemptionData>> response =
                controller.query("REQ-0001", "POS-1", "1788940800", "nonce-0003",
                        "signature", new MockHttpServletRequest());

        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
        assertEquals("PROCESSING", response.getBody().getData().getStatus());
    }
}
