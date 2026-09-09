package com.qinghe.marketing.entitlement;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BenefitIssueCommandCodecTest {

    private final BenefitIssueCommandCodec codec = new BenefitIssueCommandCodec(new ObjectMapper());

    @Test
    void shouldDecodeThePersistedOutboxPayload() {
        BenefitIssueCommand command = codec.decode("{\"eventId\":\"EVT-1\",\"claimNo\":\"CLM-1\","
                + "\"reservationId\":\"RSV-1\",\"campaignId\":10,\"memberId\":20,"
                + "\"requestId\":\"request-001\"}");

        assertEquals("EVT-1", command.eventId());
        assertEquals("CLM-1", command.claimNo());
        assertEquals(10L, command.campaignId());
        assertEquals(20L, command.memberId());
    }

    @Test
    void shouldRejectMessagesThatCannotBeLinkedToAClaim() {
        assertThrows(IllegalArgumentException.class,
                () -> codec.decode("{\"eventId\":\"EVT-1\",\"claimNo\":\"CLM-1\"}"));
        assertThrows(IllegalArgumentException.class, () -> codec.decode("not-json"));
    }
}

