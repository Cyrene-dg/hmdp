package com.qinghe.marketing.contract.mock;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MemberCenterMockServer implements AutoCloseable {

    public static final String PATH = "/openapi/v1/member-sessions/introspect";

    private final HttpServer server;
    private final ExecutorService executor;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final long timeoutDelayMillis;

    private MemberCenterMockServer(int port, long timeoutDelayMillis) throws IOException {
        this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        this.executor = Executors.newCachedThreadPool();
        this.timeoutDelayMillis = timeoutDelayMillis;
        this.server.createContext(PATH, new IntrospectHandler());
        this.server.setExecutor(executor);
    }

    public static MemberCenterMockServer start(int port, long timeoutDelayMillis) throws IOException {
        MemberCenterMockServer mock = new MemberCenterMockServer(port, timeoutDelayMillis);
        mock.server.start();
        return mock;
    }

    public String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @Override
    public void close() {
        server.stop(0);
        executor.shutdownNow();
    }

    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 18081;
        MemberCenterMockServer mock = MemberCenterMockServer.start(port, 1500L);
        Runtime.getRuntime().addShutdownHook(new Thread(mock::close));
        System.out.println("Qinghe member-center Mock listening on " + mock.baseUrl() + PATH);
        System.out.println("This is a simulated contract server. Press Ctrl+C to stop.");
        new CountDownLatch(1).await();
    }

    private final class IntrospectHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                    respond(exchange, 405, error("METHOD_NOT_ALLOWED", requestId(exchange)));
                    return;
                }
                if (!hasRequiredHeaders(exchange)) {
                    respond(exchange, 400, error("INVALID_ARGUMENT", requestId(exchange)));
                    return;
                }
                Map<String, Object> request = readRequest(exchange.getRequestBody());
                String token = request.get("memberToken") == null ? "" : String.valueOf(request.get("memberToken"));
                String requestId = requestId(exchange);

                if ("mock-member-token-active".equals(token)) {
                    respond(exchange, 200, activeMember(requestId));
                } else if ("mock-member-token-invalid".equals(token)) {
                    respond(exchange, 401, error("TOKEN_INVALID", requestId));
                } else if ("mock-member-token-expired".equals(token)) {
                    respond(exchange, 401, error("TOKEN_EXPIRED", requestId));
                } else if ("mock-member-token-frozen".equals(token)) {
                    respond(exchange, 403, error("MEMBER_FROZEN", requestId));
                } else if ("mock-member-token-cancelled".equals(token)) {
                    respond(exchange, 403, error("MEMBER_CANCELLED", requestId));
                } else if ("mock-member-token-rate-limited".equals(token)) {
                    exchange.getResponseHeaders().set("Retry-After", "1");
                    respond(exchange, 429, error("RATE_LIMITED", requestId));
                } else if ("mock-member-token-timeout".equals(token)) {
                    sleep(timeoutDelayMillis);
                    respond(exchange, 503, error("SYSTEM_BUSY", requestId));
                } else if ("mock-member-token-malformed".equals(token)) {
                    Map<String, Object> malformed = new LinkedHashMap<String, Object>();
                    malformed.put("code", "OK");
                    malformed.put("message", "intentionally malformed Mock response");
                    malformed.put("requestId", requestId);
                    respond(exchange, 200, malformed);
                } else {
                    respond(exchange, 401, error("TOKEN_INVALID", requestId));
                }
            } catch (RuntimeException exception) {
                respond(exchange, 400, error("INVALID_ARGUMENT", requestId(exchange)));
            } finally {
                exchange.close();
            }
        }
    }

    private boolean hasRequiredHeaders(HttpExchange exchange) {
        return present(exchange, "X-Request-Id")
                && present(exchange, "X-Client-Id")
                && present(exchange, "X-Timestamp")
                && present(exchange, "X-Nonce")
                && present(exchange, "X-Signature");
    }

    private static boolean present(HttpExchange exchange, String name) {
        String value = exchange.getRequestHeaders().getFirst(name);
        return value != null && !value.trim().isEmpty();
    }

    private Map<String, Object> readRequest(InputStream input) throws IOException {
        return objectMapper.readValue(input, new TypeReference<Map<String, Object>>() { });
    }

    private Map<String, Object> activeMember(String requestId) {
        Map<String, Object> member = new LinkedHashMap<String, Object>();
        member.put("memberNo", "M100086");
        member.put("status", "ACTIVE");
        member.put("levelCode", "NORMAL");
        member.put("tokenExpiresAt", OffsetDateTime.now(ZoneOffset.ofHours(8)).plusHours(1).toString());

        Map<String, Object> response = new LinkedHashMap<String, Object>();
        response.put("code", "OK");
        response.put("message", "success");
        response.put("requestId", requestId);
        response.put("data", member);
        return response;
    }

    private static Map<String, Object> error(String code, String requestId) {
        Map<String, Object> response = new LinkedHashMap<String, Object>();
        response.put("code", code);
        response.put("message", code.toLowerCase());
        response.put("requestId", requestId);
        return response;
    }

    private void respond(HttpExchange exchange, int status, Map<String, Object> body) throws IOException {
        byte[] bytes = objectMapper.writeValueAsString(body).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    private static String requestId(HttpExchange exchange) {
        String value = exchange.getRequestHeaders().getFirst("X-Request-Id");
        return value == null || value.trim().isEmpty() ? "mock-generated-request" : value;
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
