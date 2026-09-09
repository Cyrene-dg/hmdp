package com.qinghe.marketing.shared.trace;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TraceIdTest {

    @Test
    void shouldPreserveSafeCallerTraceId() {
        assertEquals("client-request_20260908", TraceId.acceptOrCreate("client-request_20260908"));
    }

    @Test
    void shouldReplaceUnsafeOrMissingTraceId() {
        String generated = TraceId.acceptOrCreate("bad trace\r\nforged-log");
        assertTrue(generated.matches("QH-[A-F0-9]{32}"));
        assertNotEquals(generated, TraceId.acceptOrCreate(null));
    }
}
