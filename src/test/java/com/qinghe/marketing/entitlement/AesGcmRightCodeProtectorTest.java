package com.qinghe.marketing.entitlement;

import com.qinghe.marketing.shared.error.QingheBusinessException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AesGcmRightCodeProtectorTest {

    @Test
    void shouldEncryptWithRandomIvAndKeepStableLookupHash() {
        String key = Base64.getEncoder().encodeToString(
                "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8));
        AesGcmRightCodeProtector protector = new AesGcmRightCodeProtector(key);

        ProtectedRightCode first = protector.protect("QH-SECRET-123456");
        ProtectedRightCode second = protector.protect("QH-SECRET-123456");

        assertEquals(first.hash(), second.hash());
        assertFalse(java.util.Arrays.equals(first.encrypted(), second.encrypted()));
        assertEquals("QH-SECRET-123456", protector.reveal(first.encrypted()));
        assertNotEquals("QH-SECRET-123456",
                new String(first.encrypted(), StandardCharsets.UTF_8));
    }

    @Test
    void shouldRefuseIssuanceWithoutAnEnvironmentKey() {
        AesGcmRightCodeProtector protector = new AesGcmRightCodeProtector("");
        assertThrows(QingheBusinessException.class, () -> protector.protect("QH-SECRET"));
    }
}

