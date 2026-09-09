package com.qinghe.marketing.claim;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ClaimAcceptanceTransactionServiceTest {

    @Test
    void shouldWriteProcessingClaimAndNewOutboxWithSeparatedMeanings() throws Exception {
        ClaimRequestRepository claims = mock(ClaimRequestRepository.class);
        OutboxEventRepository outbox = mock(OutboxEventRepository.class);
        LocalDateTime now = LocalDateTime.of(2026, 9, 9, 16, 0);
        when(claims.insert(any(ClaimRequest.class))).thenAnswer(invocation -> {
            ClaimRequest value = invocation.getArgument(0);
            return new ClaimRequest(30L, value.claimNo(), value.requestId(), value.requestDigest(),
                    value.campaignId(), value.memberId(), value.claimCycle(), value.reservationId(),
                    value.status(), value.failureCode(), value.version(), value.createdAt(), value.updatedAt());
        });
        ClaimAcceptanceTransactionService service = new ClaimAcceptanceTransactionService(
                claims, outbox, new ObjectMapper());

        ClaimRequest result = service.accept(10L, 20L, "request-001", repeat('a', 64),
                "reservation-1", "CLM-1", "EVT-1", now);

        assertEquals(ClaimStatus.PROCESSING, result.status());
        ArgumentCaptor<OutboxEvent> event = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outbox).insert(event.capture());
        assertEquals(OutboxStatus.NEW, event.getValue().status());
        assertEquals("CLAIM_ACCEPTED", event.getValue().eventType());
        JsonNode payload = new ObjectMapper().readTree(event.getValue().payload());
        assertEquals("CLM-1", payload.get("claimNo").asText());
        assertEquals("reservation-1", payload.get("reservationId").asText());
    }

    private static String repeat(char value, int count) {
        StringBuilder result = new StringBuilder(count);
        for (int index = 0; index < count; index++) {
            result.append(value);
        }
        return result.toString();
    }
}
