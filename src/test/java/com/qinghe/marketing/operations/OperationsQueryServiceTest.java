package com.qinghe.marketing.operations;

import com.qinghe.marketing.shared.clock.BusinessClock;
import com.qinghe.marketing.shared.error.QingheBusinessException;
import com.qinghe.marketing.shared.error.QingheErrorCode;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OperationsQueryServiceTest {
    private static final BusinessClock CLOCK = () -> Instant.parse("2026-09-10T06:00:00Z");

    @Test
    void shouldReturnTimelineForSupportedIdentifier() {
        OperationsQueryRepository repository = mock(OperationsQueryRepository.class);
        BusinessTraceNode node = new BusinessTraceNode("CLAIM_REQUEST", "CLM-1",
                "PROCESSING", null, CLOCK.dateTime());
        when(repository.findTrace(BusinessIdentifierType.CLAIM_NO, "CLM-1"))
                .thenReturn(Collections.singletonList(node));
        OperationsQueryService service = new OperationsQueryService(repository, CLOCK, 300, 86400);

        BusinessTraceView trace = service.trace(BusinessIdentifierType.CLAIM_NO, " CLM-1 ");

        assertEquals("CLM-1", trace.getIdentifierValue());
        assertEquals(1, trace.getTimeline().size());
    }

    @Test
    void shouldRejectMissingTraceAndInvalidPaging() {
        OperationsQueryRepository repository = mock(OperationsQueryRepository.class);
        when(repository.findTrace(BusinessIdentifierType.EVENT_ID, "EVT-404"))
                .thenReturn(Collections.emptyList());
        OperationsQueryService service = new OperationsQueryService(repository, CLOCK, 300, 86400);

        QingheBusinessException missing = assertThrows(QingheBusinessException.class,
                () -> service.trace(BusinessIdentifierType.EVENT_ID, "EVT-404"));
        assertEquals(QingheErrorCode.RESOURCE_NOT_FOUND, missing.errorCode());
        QingheBusinessException invalid = assertThrows(QingheBusinessException.class,
                () -> service.exceptions(null, null, 0, 20));
        assertEquals(QingheErrorCode.INVALID_ARGUMENT, invalid.errorCode());
    }

    @Test
    void shouldUseBusinessClockForExceptionCutoffs() {
        OperationsQueryRepository repository = mock(OperationsQueryRepository.class);
        when(repository.findExceptions(eq("OUTBOX_DEAD"), eq("DEAD"), any(LocalDateTime.class),
                any(LocalDateTime.class), eq(2), eq(10)))
                .thenReturn(new OperationalExceptionPage(2, 10, 0, Collections.emptyList()));
        OperationsQueryService service = new OperationsQueryService(repository, CLOCK, 300, 86400);

        service.exceptions("OUTBOX_DEAD", "DEAD", 2, 10);

        verify(repository).findExceptions("OUTBOX_DEAD", "DEAD",
                CLOCK.dateTime().minusSeconds(300), CLOCK.dateTime().minusSeconds(86400), 2, 10);
    }
}
