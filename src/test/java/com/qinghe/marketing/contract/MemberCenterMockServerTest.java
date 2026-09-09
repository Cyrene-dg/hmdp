package com.qinghe.marketing.contract;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qinghe.marketing.contract.mock.MemberCenterMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MemberCenterMockServerTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private MemberCenterMockServer server;

    @BeforeEach
    void startMock() throws IOException {
        server = MemberCenterMockServer.start(0, 300L);
    }

    @AfterEach
    void stopMock() {
        if (server != null) {
            server.close();
        }
    }

    @Test
    void activeTokenReturnsMinimumMemberIdentity() throws Exception {
        Response response = call("mock-member-token-active", true);
        assertEquals(200, response.status);
        assertEquals("OK", response.body.get("code"));
        Map<?, ?> data = (Map<?, ?>) response.body.get("data");
        assertEquals("M100086", data.get("memberNo"));
        assertEquals("ACTIVE", data.get("status"));
    }

    @Test
    void invalidFrozenAndRateLimitedTokensProduceStableFailures() throws Exception {
        assertFailure("mock-member-token-invalid", 401, "TOKEN_INVALID");
        assertFailure("mock-member-token-frozen", 403, "MEMBER_FROZEN");
        Response limited = call("mock-member-token-rate-limited", true);
        assertEquals(429, limited.status);
        assertEquals("RATE_LIMITED", limited.body.get("code"));
        assertEquals("1", limited.retryAfter);
    }

    @Test
    void timeoutScenarioIsDeterministicAndDoesNotDefaultToActive() throws Exception {
        long started = System.nanoTime();
        Response response = call("mock-member-token-timeout", true);
        long elapsedMillis = (System.nanoTime() - started) / 1_000_000L;

        assertEquals(503, response.status);
        assertEquals("SYSTEM_BUSY", response.body.get("code"));
        assertTrue(elapsedMillis >= 250L, "timeout scenario returned too early: " + elapsedMillis);
    }

    @Test
    void missingContractHeadersAreRejected() throws Exception {
        Response response = call("mock-member-token-active", false);
        assertEquals(400, response.status);
        assertEquals("INVALID_ARGUMENT", response.body.get("code"));
    }

    private void assertFailure(String token, int status, String code) throws Exception {
        Response response = call(token, true);
        assertEquals(status, response.status);
        assertEquals(code, response.body.get("code"));
    }

    private Response call(String token, boolean includeHeaders) throws Exception {
        URL url = new URL(server.baseUrl() + MemberCenterMockServer.PATH);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(1000);
        connection.setReadTimeout(2000);
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        connection.setRequestProperty("X-Request-Id", "req-member-test-001");
        if (includeHeaders) {
            connection.setRequestProperty("X-Client-Id", "qinghe-contract-test");
            connection.setRequestProperty("X-Timestamp", "1788849000");
            connection.setRequestProperty("X-Nonce", "nonce-test-001");
            connection.setRequestProperty("X-Signature", "0123456789abcdef0123456789abcdef");
        }
        byte[] request = ("{\"memberToken\":\"" + token + "\"}").getBytes(StandardCharsets.UTF_8);
        try (OutputStream output = connection.getOutputStream()) {
            output.write(request);
        }

        int status = connection.getResponseCode();
        String retryAfter = connection.getHeaderField("Retry-After");
        InputStream bodyStream = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
        Map<String, Object> body;
        try (InputStream input = bodyStream) {
            body = JSON.readValue(input, new TypeReference<Map<String, Object>>() { });
        } finally {
            connection.disconnect();
        }
        return new Response(status, body, retryAfter);
    }

    private static final class Response {
        private final int status;
        private final Map<String, Object> body;
        private final String retryAfter;

        private Response(int status, Map<String, Object> body, String retryAfter) {
            this.status = status;
            this.body = body;
            this.retryAfter = retryAfter;
        }
    }
}
