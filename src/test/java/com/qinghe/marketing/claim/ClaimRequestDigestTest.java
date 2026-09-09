package com.qinghe.marketing.claim;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class ClaimRequestDigestTest {

    @Test
    void shouldCanonicalizeTheSameInstantAndDetectChangedContent() {
        String first = ClaimRequestDigest.calculate("CAM-1",
                OffsetDateTime.parse("2026-09-09T16:00:00+08:00"));
        String sameInstant = ClaimRequestDigest.calculate("CAM-1",
                OffsetDateTime.parse("2026-09-09T08:00:00Z"));
        String changed = ClaimRequestDigest.calculate("CAM-1",
                OffsetDateTime.parse("2026-09-09T08:00:01Z"));

        assertEquals(first, sameInstant);
        assertNotEquals(first, changed);
        assertEquals(64, first.length());
    }
}
