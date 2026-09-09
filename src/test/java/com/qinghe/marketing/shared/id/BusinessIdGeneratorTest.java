package com.qinghe.marketing.shared.id;

import com.qinghe.marketing.shared.clock.BusinessClock;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class BusinessIdGeneratorTest {

    @Test
    void shouldGenerateOpaqueTypedIdUsingBusinessTime() {
        BusinessClock clock = () -> Instant.parse("2026-09-08T16:00:01Z");
        UUID first = UUID.fromString("01234567-89ab-cdef-0123-456789abcdef");
        UUID second = UUID.fromString("fedcba98-7654-3210-fedc-ba9876543210");
        UUID[] values = {first, second};
        int[] index = {0};
        BusinessIdGenerator generator = new BusinessIdGenerator(clock, () -> values[index[0]++]);

        String campaignNo = generator.next(BusinessIdType.CAMPAIGN);
        String claimNo = generator.next(BusinessIdType.CLAIM);

        assertEquals("CAM-20260909000001-0123456789AB", campaignNo);
        assertEquals("CLM-20260909000001-FEDCBA987654", claimNo);
        assertNotEquals(campaignNo, claimNo);
    }
}
