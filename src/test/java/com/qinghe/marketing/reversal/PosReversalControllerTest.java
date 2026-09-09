package com.qinghe.marketing.reversal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qinghe.marketing.entitlement.EntitlementStatus;
import com.qinghe.marketing.identity.PosAuthenticatedStore;
import com.qinghe.marketing.identity.PosAuthenticationRequest;
import com.qinghe.marketing.identity.PosRequestAuthenticator;
import com.qinghe.marketing.redemption.PosExecutionGuard;
import com.qinghe.marketing.redemption.PosExecutionMetrics;
import com.qinghe.marketing.shared.error.QingheBusinessException;
import com.qinghe.marketing.shared.error.QingheErrorCode;
import com.qinghe.marketing.shared.web.QingheApiResponse;
import com.qinghe.marketing.store.StoreOwnershipType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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

class PosReversalControllerTest {
    private ThreadPoolTaskExecutor executor;
    private PosRequestAuthenticator authenticator;
    private PosReversalService service;
    private PosReversalController controller;

    @BeforeEach
    void setUp() {
        executor = new ThreadPoolTaskExecutor(); executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1); executor.setQueueCapacity(2); executor.initialize();
        authenticator = mock(PosRequestAuthenticator.class);
        service = mock(PosReversalService.class);
        PosAuthenticatedStore store = new PosAuthenticatedStore(
                2L, "S002", StoreOwnershipType.FRANCHISE, "POS-2");
        when(authenticator.authenticate(any(PosAuthenticationRequest.class))).thenReturn(store);
        controller = new PosReversalController(new ObjectMapper(), authenticator, service,
                new PosExecutionGuard(executor, new PosExecutionMetrics(), 2, 2000));
    }

    @AfterEach
    void tearDown() { executor.shutdown(); }

    @Test
    void shouldAuthenticatePathAndRawBodyThenReturnFrozenResponse() {
        byte[] body = body("REVREQ-0001");
        when(service.reverse(any(PosAuthenticatedStore.class), any(PosReversalCommand.class)))
                .thenReturn(new ReversalResult("REVREQ-0001", "REV-0001", "RDM-0001",
                        ReversalRequestStatus.SUCCESS, EntitlementStatus.AVAILABLE, null,
                        LocalDateTime.of(2026, 9, 9, 16, 0)));

        QingheApiResponse<PosReversalController.ReversalData> response = controller.reverse(
                "RDM-0001", body, "REVREQ-0001", "POS-2", "1788940800", "nonce-1",
                "signature", new MockHttpServletRequest());

        assertEquals("REV-0001", response.getData().getReversalNo());
        assertEquals("AVAILABLE", response.getData().getRightStatus());
        ArgumentCaptor<PosAuthenticationRequest> captured = ArgumentCaptor.forClass(
                PosAuthenticationRequest.class);
        verify(authenticator).authenticate(captured.capture());
        assertEquals("/openapi/v1/redemptions/RDM-0001/reversals",
                captured.getValue().normalizedPath());
        assertArrayEquals(body, captured.getValue().rawBody());
    }

    @Test
    void shouldRejectHeaderAndBodyRequestNumberMismatch() {
        QingheBusinessException failure = assertThrows(QingheBusinessException.class,
                () -> controller.reverse("RDM-0001", body("REVREQ-OTHER"), "REVREQ-0001",
                        "POS-2", "1788940800", "nonce-2", "signature",
                        new MockHttpServletRequest()));
        assertEquals(QingheErrorCode.INVALID_ARGUMENT, failure.errorCode());
    }

    private static byte[] body(String requestNo) {
        return ("{\"posRequestNo\":\"" + requestNo + "\",\"posOrderNo\":\"ORDER-0001\","
                + "\"storeCode\":\"S002\",\"operatorNo\":\"OP-1\","
                + "\"reasonCode\":\"POS_ORDER_CANCELLED\","
                + "\"occurredAt\":\"2026-09-09T16:00:00+08:00\"}")
                .getBytes(StandardCharsets.UTF_8);
    }
}
