package com.qinghe.marketing.entitlement;

import com.qinghe.marketing.shared.error.QingheBusinessException;
import com.qinghe.marketing.shared.error.QingheErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public class AesGcmRightCodeProtector implements RightCodeProtector {

    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;

    private final String encodedKey;
    private final SecureRandom secureRandom = new SecureRandom();

    public AesGcmRightCodeProtector(
            @Value("${qinghe.entitlement.right-code-key-base64:}") String encodedKey) {
        this.encodedKey = encodedKey == null ? "" : encodedKey.trim();
    }

    @Override
    public ProtectedRightCode protect(String plaintext) {
        if (plaintext == null || plaintext.trim().isEmpty()) {
            throw new IllegalArgumentException("right code is required");
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] packed = ByteBuffer.allocate(iv.length + ciphertext.length)
                    .put(iv).put(ciphertext).array();
            return new ProtectedRightCode(hex(MessageDigest.getInstance("SHA-256")
                    .digest(plaintext.getBytes(StandardCharsets.UTF_8))), packed);
        } catch (QingheBusinessException failure) {
            throw failure;
        } catch (Exception failure) {
            throw unavailable("right code protection failed", failure);
        }
    }

    @Override
    public String reveal(byte[] encrypted) {
        try {
            if (encrypted == null || encrypted.length <= IV_LENGTH) {
                throw new IllegalArgumentException("encrypted right code is invalid");
            }
            ByteBuffer buffer = ByteBuffer.wrap(encrypted);
            byte[] iv = new byte[IV_LENGTH];
            buffer.get(iv);
            byte[] ciphertext = new byte[buffer.remaining()];
            buffer.get(ciphertext);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (QingheBusinessException failure) {
            throw failure;
        } catch (Exception failure) {
            throw unavailable("right code cannot be revealed", failure);
        }
    }

    private SecretKeySpec key() {
        if (encodedKey.isEmpty()) {
            throw unavailable("right code encryption key is not configured", null);
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(encodedKey);
            if (decoded.length != 16 && decoded.length != 24 && decoded.length != 32) {
                throw new IllegalArgumentException("AES key must be 128, 192 or 256 bits");
            }
            return new SecretKeySpec(decoded, "AES");
        } catch (IllegalArgumentException invalid) {
            throw unavailable("right code encryption key is invalid", invalid);
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            result.append(String.format("%02x", value & 0xff));
        }
        return result.toString();
    }

    private static QingheBusinessException unavailable(String message, Throwable cause) {
        QingheBusinessException exception = new QingheBusinessException(
                QingheErrorCode.TEMPORARY_UNAVAILABLE, message);
        if (cause != null) {
            exception.initCause(cause);
        }
        return exception;
    }
}
