package com.qinghe.marketing.identity;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qinghe.marketing.shared.clock.BusinessClock;
import com.qinghe.marketing.shared.error.QingheBusinessException;
import com.qinghe.marketing.shared.error.QingheErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class HttpMemberCenterClient implements MemberCenterClient {

    private static final String PATH = "/openapi/v1/member-sessions/introspect";

    private final ObjectMapper objectMapper;
    private final BusinessClock clock;
    private final String baseUrl;
    private final String clientId;
    private final String clientSecret;
    private final int connectTimeoutMillis;
    private final int readTimeoutMillis;

    public HttpMemberCenterClient(ObjectMapper objectMapper,
                                  BusinessClock clock,
                                  @Value("${qinghe.member-center.base-url:http://127.0.0.1:18081}") String baseUrl,
                                  @Value("${qinghe.member-center.client-id:qinghe-rights}") String clientId,
                                  @Value("${qinghe.member-center.client-secret:}") String clientSecret,
                                  @Value("${qinghe.member-center.connect-timeout-ms:300}") int connectTimeoutMillis,
                                  @Value("${qinghe.member-center.read-timeout-ms:800}") int readTimeoutMillis) {
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.baseUrl = trimTrailingSlash(baseUrl);
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.connectTimeoutMillis = connectTimeoutMillis;
        this.readTimeoutMillis = readTimeoutMillis;
    }

    @Override
    public VerifiedMember introspect(String memberToken, String requestId) {
        if (clientSecret == null || clientSecret.isEmpty()) {
            throw new QingheBusinessException(QingheErrorCode.TEMPORARY_UNAVAILABLE,
                    "member center credential is not configured");
        }
        HttpURLConnection connection = null;
        try {
            byte[] body = requestBody(memberToken);
            long timestamp = clock.instant().getEpochSecond();
            String nonce = UUID.randomUUID().toString().replace("-", "");
            String stableRequestId = requestId == null || requestId.trim().isEmpty()
                    ? "req-" + nonce : requestId;
            connection = (HttpURLConnection) new URL(baseUrl + PATH).openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(connectTimeoutMillis);
            connection.setReadTimeout(readTimeoutMillis);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("X-Request-Id", stableRequestId);
            connection.setRequestProperty("X-Client-Id", clientId);
            connection.setRequestProperty("X-Timestamp", String.valueOf(timestamp));
            connection.setRequestProperty("X-Nonce", nonce);
            connection.setRequestProperty("X-Signature", signature(body, timestamp, nonce));
            try (OutputStream output = connection.getOutputStream()) {
                output.write(body);
            }
            int status = connection.getResponseCode();
            Map<String, Object> response = readResponse(connection, status);
            if (status == 200) {
                return verifiedMember(response);
            }
            throw mappedFailure(status, stringValue(response.get("code")));
        } catch (QingheBusinessException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new QingheBusinessException(QingheErrorCode.TEMPORARY_UNAVAILABLE,
                    "member center is temporarily unavailable");
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private byte[] requestBody(String memberToken) throws IOException {
        Map<String, Object> request = new LinkedHashMap<String, Object>();
        request.put("memberToken", memberToken);
        return objectMapper.writeValueAsBytes(request);
    }

    private Map<String, Object> readResponse(HttpURLConnection connection, int status) throws IOException {
        InputStream stream = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
        if (stream == null) {
            return Collections.emptyMap();
        }
        try (InputStream input = stream) {
            return objectMapper.readValue(input, new TypeReference<Map<String, Object>>() { });
        }
    }

    @SuppressWarnings("unchecked")
    private VerifiedMember verifiedMember(Map<String, Object> response) {
        if (!"OK".equals(stringValue(response.get("code"))) || !(response.get("data") instanceof Map)) {
            throw new QingheBusinessException(QingheErrorCode.TEMPORARY_UNAVAILABLE,
                    "member center returned an incomplete identity");
        }
        Map<String, Object> data = (Map<String, Object>) response.get("data");
        String memberNo = stringValue(data.get("memberNo"));
        String status = stringValue(data.get("status"));
        String expiresAt = stringValue(data.get("tokenExpiresAt"));
        if (memberNo.isEmpty() || status.isEmpty() || expiresAt.isEmpty()) {
            throw new QingheBusinessException(QingheErrorCode.TEMPORARY_UNAVAILABLE,
                    "member center returned an incomplete identity");
        }
        try {
            return new VerifiedMember(memberNo, MemberStatus.valueOf(status),
                    nullableString(data.get("levelCode")), OffsetDateTime.parse(expiresAt).toInstant());
        } catch (RuntimeException malformed) {
            throw new QingheBusinessException(QingheErrorCode.TEMPORARY_UNAVAILABLE,
                    "member center returned an invalid identity");
        }
    }

    private QingheBusinessException mappedFailure(int httpStatus, String code) {
        if (httpStatus == 401 && "TOKEN_EXPIRED".equals(code)) {
            return new QingheBusinessException(QingheErrorCode.TOKEN_EXPIRED, "member token is expired");
        }
        if (httpStatus == 401) {
            return new QingheBusinessException(QingheErrorCode.TOKEN_INVALID, "member token is invalid");
        }
        if (httpStatus == 403 && "MEMBER_CANCELLED".equals(code)) {
            return new QingheBusinessException(QingheErrorCode.MEMBER_CANCELLED, "member is cancelled");
        }
        if (httpStatus == 403) {
            return new QingheBusinessException(QingheErrorCode.MEMBER_FROZEN, "member is frozen");
        }
        if (httpStatus == 429) {
            return new QingheBusinessException(QingheErrorCode.RATE_LIMITED, "member center rate limited the request");
        }
        return new QingheBusinessException(QingheErrorCode.TEMPORARY_UNAVAILABLE,
                "member center is temporarily unavailable");
    }

    private String signature(byte[] body, long timestamp, String nonce) {
        String canonical = "POST\n" + PATH + "\n" + clientId + "\n" + timestamp + "\n"
                + nonce + "\n" + sha256(body);
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(clientSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return hex(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("HMAC-SHA256 is unavailable", exception);
        }
    }

    private static String sha256(byte[] body) {
        try {
            return hex(MessageDigest.getInstance("SHA-256").digest(body));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            result.append(String.format("%02x", value & 0xff));
        }
        return result.toString();
    }

    private static String nullableString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static String trimTrailingSlash(String value) {
        return value != null && value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
