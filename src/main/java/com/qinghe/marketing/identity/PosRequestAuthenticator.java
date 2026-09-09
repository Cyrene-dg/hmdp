package com.qinghe.marketing.identity;

import com.qinghe.marketing.shared.clock.BusinessClock;
import com.qinghe.marketing.shared.error.QingheBusinessException;
import com.qinghe.marketing.shared.error.QingheErrorCode;
import com.qinghe.marketing.store.PosCredential;
import com.qinghe.marketing.store.PosCredentialRepository;
import com.qinghe.marketing.store.StoreRecord;
import com.qinghe.marketing.store.StoreRepository;
import com.qinghe.marketing.store.StoreStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
public class PosRequestAuthenticator {

    private static final Pattern METHOD = Pattern.compile("[A-Z]{3,8}");
    private static final Pattern PATH = Pattern.compile("/openapi/v1/[A-Za-z0-9._~!$&'()*+,;=:@%/-]{1,512}");
    private static final Pattern CLIENT_ID = Pattern.compile("[A-Za-z0-9._-]{8,64}");
    private static final Pattern NONCE = Pattern.compile("[A-Za-z0-9._:-]{8,64}");
    private static final Pattern SIGNATURE = Pattern.compile("[a-f0-9]{64}");

    private final PosCredentialRepository credentialRepository;
    private final StoreRepository storeRepository;
    private final PosNonceRepository nonceRepository;
    private final PosSecretResolver secretResolver;
    private final BusinessClock clock;
    private final Duration allowedClockSkew;

    public PosRequestAuthenticator(PosCredentialRepository credentialRepository,
                                   StoreRepository storeRepository,
                                   PosNonceRepository nonceRepository,
                                   PosSecretResolver secretResolver,
                                   BusinessClock clock,
                                   @Value("${qinghe.pos.allowed-clock-skew-seconds:300}") long clockSkewSeconds) {
        if (clockSkewSeconds <= 0) {
            throw new IllegalArgumentException("POS allowed clock skew must be positive");
        }
        this.credentialRepository = credentialRepository;
        this.storeRepository = storeRepository;
        this.nonceRepository = nonceRepository;
        this.secretResolver = secretResolver;
        this.clock = clock;
        this.allowedClockSkew = Duration.ofSeconds(clockSkewSeconds);
    }

    @Transactional(rollbackFor = Exception.class)
    public PosAuthenticatedStore authenticate(PosAuthenticationRequest request) {
        validateShape(request);
        long timestamp = timestamp(request.timestamp());
        Instant now = clock.instant();
        Instant requestTime;
        try {
            requestTime = Instant.ofEpochSecond(timestamp);
        } catch (DateTimeException invalid) {
            throw expired("POS timestamp is invalid");
        }
        if (requestTime.isBefore(now.minus(allowedClockSkew))
                || requestTime.isAfter(now.plus(allowedClockSkew))) {
            throw expired("POS timestamp is outside the allowed window");
        }
        PosCredential credential = credentialRepository.findActiveByClientId(request.clientId())
                .orElseThrow(() -> signatureInvalid("POS credential is invalid"));
        StoreRecord store = storeRepository.findById(credential.storeId())
                .orElseThrow(() -> signatureInvalid("POS credential store does not exist"));
        if (store.status() != StoreStatus.ACTIVE) {
            throw new QingheBusinessException(QingheErrorCode.STORE_NOT_ELIGIBLE,
                    "store is disabled for POS operations");
        }

        char[] secret = secretResolver.resolve(credential.secretReference());
        try {
            String expected = signature(request, secret);
            if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),
                    request.signature().getBytes(StandardCharsets.US_ASCII))) {
                throw signatureInvalid("POS signature is invalid");
            }
        } finally {
            Arrays.fill(secret, '\0');
        }

        LocalDateTime localNow = clock.dateTime();
        if (!nonceRepository.reserve(request.clientId(), request.nonce(),
                localNow.plus(allowedClockSkew), localNow)) {
            throw signatureInvalid("POS nonce was already used");
        }
        return new PosAuthenticatedStore(store.id(), store.externalStoreCode(),
                store.ownershipType(), credential.clientId());
    }

    private static void validateShape(PosAuthenticationRequest request) {
        if (request == null || !matches(METHOD, request.method())
                || !matches(PATH, request.normalizedPath())
                || !matches(CLIENT_ID, request.clientId())
                || !matches(NONCE, request.nonce())
                || !matches(SIGNATURE, request.signature())) {
            throw signatureInvalid("POS authentication headers are missing or invalid");
        }
    }

    private static long timestamp(String value) {
        try {
            return Long.parseLong(value);
        } catch (RuntimeException invalid) {
            throw expired("POS timestamp is invalid");
        }
    }

    private static String signature(PosAuthenticationRequest request, char[] secret) {
        String canonical = request.method() + "\n" + request.normalizedPath() + "\n"
                + request.clientId() + "\n" + request.timestamp() + "\n" + request.nonce() + "\n"
                + sha256(request.rawBody());
        byte[] secretBytes = utf8(secret);
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secretBytes, "HmacSHA256"));
            return hex(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("HMAC-SHA256 is unavailable", exception);
        } finally {
            Arrays.fill(secretBytes, (byte) 0);
        }
    }

    private static byte[] utf8(char[] value) {
        try {
            ByteBuffer bytes = StandardCharsets.UTF_8.newEncoder().encode(CharBuffer.wrap(value));
            byte[] result = new byte[bytes.remaining()];
            bytes.get(result);
            return result;
        } catch (CharacterCodingException exception) {
            throw new IllegalArgumentException("secret is not valid UTF-8", exception);
        }
    }

    private static String sha256(byte[] body) {
        try {
            return hex(MessageDigest.getInstance("SHA-256").digest(body));
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            result.append(String.format(Locale.ROOT, "%02x", value & 0xff));
        }
        return result.toString();
    }

    private static boolean matches(Pattern pattern, String value) {
        return value != null && pattern.matcher(value).matches();
    }

    private static QingheBusinessException signatureInvalid(String message) {
        return new QingheBusinessException(QingheErrorCode.SIGNATURE_INVALID, message);
    }

    private static QingheBusinessException expired(String message) {
        return new QingheBusinessException(QingheErrorCode.REQUEST_EXPIRED, message);
    }
}
