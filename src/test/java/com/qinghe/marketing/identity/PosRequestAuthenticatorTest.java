package com.qinghe.marketing.identity;

import com.qinghe.marketing.shared.clock.BusinessClock;
import com.qinghe.marketing.shared.error.QingheBusinessException;
import com.qinghe.marketing.shared.error.QingheErrorCode;
import com.qinghe.marketing.store.PosCredential;
import com.qinghe.marketing.store.PosCredentialRepository;
import com.qinghe.marketing.store.StoreImportRow;
import com.qinghe.marketing.store.StoreOwnershipType;
import com.qinghe.marketing.store.StoreRecord;
import com.qinghe.marketing.store.StoreRepository;
import com.qinghe.marketing.store.StoreStatus;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PosRequestAuthenticatorTest {

    private static final Instant NOW = Instant.parse("2026-09-08T07:00:00Z");
    private static final String CLIENT_ID = "pos-client-qh006";
    private static final String SECRET_REFERENCE = "secret://env/QH_POS_006_SECRET";
    private static final String SECRET = "test-only-pos-secret-32-characters";
    private static final String PATH = "/openapi/v1/redemptions";
    private static final byte[] BODY = "{\"storeCode\":\"QH006\"}".getBytes(StandardCharsets.UTF_8);
    private final BusinessClock clock = () -> NOW;

    @Test
    void shouldRejectNonPositiveAllowedClockSkewAtStartup() {
        assertThrows(IllegalArgumentException.class,
                () -> new PosRequestAuthenticator(null, null, null, null, clock, 0L));
    }

    @Test
    void shouldAuthenticateSignatureAndReturnCredentialBoundStore() {
        InMemoryNonceRepository nonces = new InMemoryNonceRepository();
        PosRequestAuthenticator authenticator = authenticator(StoreStatus.ACTIVE, nonces);
        String nonce = "nonce-valid-0001";
        PosAuthenticationRequest request = signedRequest(NOW.getEpochSecond(), nonce, BODY, SECRET);

        PosAuthenticatedStore principal = authenticator.authenticate(request);

        assertEquals(6L, principal.storeId());
        assertEquals("QH006", principal.storeCode());
        assertEquals(StoreOwnershipType.FRANCHISE, principal.ownershipType());
        assertEquals(CLIENT_ID, principal.clientId());
        assertEquals(1, nonces.values.size());
    }

    @Test
    void shouldRejectInvalidSignatureWithoutPoisoningNonce() {
        InMemoryNonceRepository nonces = new InMemoryNonceRepository();
        PosRequestAuthenticator authenticator = authenticator(StoreStatus.ACTIVE, nonces);
        String nonce = "nonce-valid-0002";
        PosAuthenticationRequest invalid = signedRequest(NOW.getEpochSecond(), nonce, BODY,
                "different-test-secret-32-characters");

        QingheBusinessException exception = assertThrows(QingheBusinessException.class,
                () -> authenticator.authenticate(invalid));

        assertEquals(QingheErrorCode.SIGNATURE_INVALID, exception.errorCode());
        assertEquals(0, nonces.values.size());
        authenticator.authenticate(signedRequest(NOW.getEpochSecond(), nonce, BODY, SECRET));
        assertEquals(1, nonces.values.size());
    }

    @Test
    void shouldRejectReplayAndTimestampOutsideWindow() {
        InMemoryNonceRepository nonces = new InMemoryNonceRepository();
        PosRequestAuthenticator authenticator = authenticator(StoreStatus.ACTIVE, nonces);
        PosAuthenticationRequest first = signedRequest(NOW.getEpochSecond(), "nonce-replay-001", BODY, SECRET);
        authenticator.authenticate(first);

        assertEquals(QingheErrorCode.SIGNATURE_INVALID,
                assertThrows(QingheBusinessException.class,
                        () -> authenticator.authenticate(first)).errorCode());
        assertEquals(QingheErrorCode.REQUEST_EXPIRED,
                assertThrows(QingheBusinessException.class,
                        () -> authenticator.authenticate(signedRequest(
                                NOW.minusSeconds(301).getEpochSecond(), "nonce-stale-0001", BODY, SECRET)))
                        .errorCode());
    }

    @Test
    void shouldRejectDisabledCredentialBoundStore() {
        PosRequestAuthenticator authenticator = authenticator(
                StoreStatus.DISABLED, new InMemoryNonceRepository());

        QingheBusinessException exception = assertThrows(QingheBusinessException.class,
                () -> authenticator.authenticate(signedRequest(
                        NOW.getEpochSecond(), "nonce-disabled-1", BODY, SECRET)));

        assertEquals(QingheErrorCode.STORE_NOT_ELIGIBLE, exception.errorCode());
    }

    private PosRequestAuthenticator authenticator(StoreStatus status, PosNonceRepository nonces) {
        StoreRecord store = new StoreRecord(6L, "QH006", "青禾加盟六店",
                StoreOwnershipType.FRANCHISE, status, "POS-3.2", "STORE-20260908-01", 0L);
        InMemoryStoreRepository stores = new InMemoryStoreRepository(store);
        PosCredential credential = new PosCredential(store.id(), CLIENT_ID, SECRET_REFERENCE, 1);
        PosCredentialRepository credentials = new InMemoryCredentialRepository(credential);
        PosSecretResolver secrets = reference -> {
            if (!SECRET_REFERENCE.equals(reference)) {
                throw new IllegalArgumentException("unknown reference");
            }
            return SECRET.toCharArray();
        };
        return new PosRequestAuthenticator(credentials, stores, nonces, secrets, clock, 300L);
    }

    private static PosAuthenticationRequest signedRequest(long timestamp, String nonce, byte[] body, String secret) {
        String timestampText = String.valueOf(timestamp);
        String canonical = "POST\n" + PATH + "\n" + CLIENT_ID + "\n" + timestampText + "\n"
                + nonce + "\n" + sha256(body);
        return new PosAuthenticationRequest("POST", PATH, CLIENT_ID, timestampText, nonce,
                hmac(canonical, secret), body);
    }

    private static String hmac(String canonical, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return hex(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static String sha256(byte[] body) {
        try {
            return hex(MessageDigest.getInstance("SHA-256").digest(body));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            result.append(String.format(Locale.ROOT, "%02x", value & 0xff));
        }
        return result.toString();
    }

    private static final class InMemoryCredentialRepository implements PosCredentialRepository {
        private final PosCredential credential;

        private InMemoryCredentialRepository(PosCredential credential) {
            this.credential = credential;
        }

        @Override
        public Optional<PosCredential> findActiveByClientId(String clientId) {
            return credential.clientId().equals(clientId) ? Optional.of(credential) : Optional.empty();
        }

        @Override
        public void activate(PosCredential credential, LocalDateTime now) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class InMemoryStoreRepository implements StoreRepository {
        private final StoreRecord store;

        private InMemoryStoreRepository(StoreRecord store) {
            this.store = store;
        }

        @Override
        public Optional<StoreRecord> findById(long id) {
            return store.id() == id ? Optional.of(store) : Optional.empty();
        }

        @Override
        public Optional<StoreRecord> findByExternalStoreCode(String externalStoreCode) {
            return store.externalStoreCode().equals(externalStoreCode) ? Optional.of(store) : Optional.empty();
        }

        @Override
        public void upsert(StoreImportRow row, LocalDateTime now) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class InMemoryNonceRepository implements PosNonceRepository {
        private final Map<String, LocalDateTime> values = new LinkedHashMap<String, LocalDateTime>();

        @Override
        public boolean reserve(String clientId, String nonce, LocalDateTime expiresAt, LocalDateTime now) {
            String key = clientId + ":" + nonce;
            LocalDateTime previous = values.get(key);
            if (previous != null && !previous.isBefore(now)) {
                return false;
            }
            values.put(key, expiresAt);
            return true;
        }
    }
}
