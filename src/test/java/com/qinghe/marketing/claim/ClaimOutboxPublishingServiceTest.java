package com.qinghe.marketing.claim;

import com.qinghe.marketing.shared.clock.BusinessClock;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ClaimOutboxPublishingServiceTest {

    private static final BusinessClock CLOCK = () -> Instant.parse("2026-09-09T08:00:00Z");

    @Test
    void shouldCompleteAckAndScheduleNackWithoutChangingClaimStatus() {
        OutboxEventRepository repository = mock(OutboxEventRepository.class);
        ClaimOutboxSender sender = mock(ClaimOutboxSender.class);
        LeasedOutboxEvent ack = event("EVT-1", 0);
        LeasedOutboxEvent nack = event("EVT-2", 1);
        when(repository.leaseBatch(eq("instance-a"), any(LocalDateTime.class),
                any(LocalDateTime.class), eq(10))).thenReturn(Arrays.asList(ack, nack));
        when(sender.send(ack)).thenReturn(OutboxPublishResult.acknowledged());
        when(sender.send(nack)).thenReturn(OutboxPublishResult.failed("broker nack"));
        when(repository.completePublication(eq("EVT-1"), eq("instance-a"), eq(true),
                any(), any(LocalDateTime.class), eq(8), any(LocalDateTime.class)))
                .thenReturn(OutboxStatus.PUBLISHED);
        when(repository.completePublication(eq("EVT-2"), eq("instance-a"), eq(false),
                eq("broker nack"), any(LocalDateTime.class), eq(8), any(LocalDateTime.class)))
                .thenReturn(OutboxStatus.RETRY);
        ClaimOutboxPublishingService service = new ClaimOutboxPublishingService(repository, sender, CLOCK);

        assertEquals(2, service.publishBatch("instance-a", 10, Duration.ofSeconds(30), 8));

        ArgumentCaptor<LocalDateTime> leaseUntil = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(repository).leaseBatch(eq("instance-a"), eq(CLOCK.dateTime()),
                leaseUntil.capture(), eq(10));
        assertEquals(CLOCK.dateTime().plusSeconds(30), leaseUntil.getValue());
        ArgumentCaptor<LocalDateTime> retryAt = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(repository).completePublication(eq("EVT-2"), eq("instance-a"), eq(false),
                eq("broker nack"), retryAt.capture(), eq(8), eq(CLOCK.dateTime()));
        assertEquals(CLOCK.dateTime().plusSeconds(2), retryAt.getValue());
    }

    @Test
    void shouldCapExponentialRetryDelayAtFiveMinutes() {
        assertEquals(Duration.ofSeconds(1), ClaimOutboxPublishingService.retryDelay(1));
        assertEquals(Duration.ofSeconds(8), ClaimOutboxPublishingService.retryDelay(4));
        assertEquals(Duration.ofSeconds(256), ClaimOutboxPublishingService.retryDelay(9));
        assertEquals(Duration.ofSeconds(300), ClaimOutboxPublishingService.retryDelay(20));
    }

    private static LeasedOutboxEvent event(String eventId, int retryCount) {
        return new LeasedOutboxEvent(1L, eventId, "CLAIM_REQUEST", "CLM-1",
                "CLAIM_ACCEPTED", 1L, "{}", retryCount, "instance-a");
    }
}
