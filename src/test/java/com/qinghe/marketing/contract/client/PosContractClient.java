package com.qinghe.marketing.contract.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class PosContractClient {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final String clientId;
    private final byte[] secret;

    public PosContractClient(String clientId, String secret) {
        if (clientId == null || clientId.trim().isEmpty()) {
            throw new IllegalArgumentException("clientId is required");
        }
        if (secret == null || secret.isEmpty()) {
            throw new IllegalArgumentException("secret is required");
        }
        this.clientId = clientId;
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    public SignedRequest verify(Map<String, Object> body, long timestamp, String nonce) throws Exception {
        return signedJson("POST", "/openapi/v1/rights/verify", requiredRequestNo(body), body, timestamp, nonce);
    }

    public SignedRequest redeem(Map<String, Object> body, long timestamp, String nonce) throws Exception {
        return signedJson("POST", "/openapi/v1/redemptions", requiredRequestNo(body), body, timestamp, nonce);
    }

    public SignedRequest queryRedemption(String posRequestNo, long timestamp, String nonce) throws Exception {
        if (posRequestNo == null || posRequestNo.trim().isEmpty()) {
            throw new IllegalArgumentException("posRequestNo is required");
        }
        String path = "/openapi/v1/redemptions/by-request/" + posRequestNo;
        return sign("GET", path, null, new byte[0], timestamp, nonce);
    }

    public SignedRequest reverse(String redemptionNo, Map<String, Object> body,
                                 long timestamp, String nonce) throws Exception {
        if (redemptionNo == null || redemptionNo.trim().isEmpty()) {
            throw new IllegalArgumentException("redemptionNo is required");
        }
        return signedJson("POST", "/openapi/v1/redemptions/" + redemptionNo + "/reversals",
                requiredRequestNo(body), body, timestamp, nonce);
    }

    public ClientResponse execute(String baseUrl, SignedRequest request) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(baseUrl + request.path).openConnection();
        connection.setRequestMethod(request.method);
        connection.setConnectTimeout(1000);
        connection.setReadTimeout(2000);
        for (Map.Entry<String, String> header : request.headers.entrySet()) {
            connection.setRequestProperty(header.getKey(), header.getValue());
        }
        if (request.body.length > 0) {
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            try (OutputStream output = connection.getOutputStream()) {
                output.write(request.body);
            }
        }

        int status = connection.getResponseCode();
        InputStream responseStream = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
        Map<String, Object> responseBody = Collections.emptyMap();
        if (responseStream != null) {
            try (InputStream input = responseStream) {
                responseBody = JSON.readValue(input, new TypeReference<Map<String, Object>>() { });
            }
        }
        connection.disconnect();
        return new ClientResponse(status, responseBody);
    }

    private SignedRequest signedJson(String method, String path, String posRequestNo,
                                     Map<String, Object> body, long timestamp, String nonce) throws Exception {
        byte[] json = JSON.writeValueAsBytes(body);
        return sign(method, path, posRequestNo, json, timestamp, nonce);
    }

    private SignedRequest sign(String method, String path, String posRequestNo,
                               byte[] body, long timestamp, String nonce) throws Exception {
        if (nonce == null || nonce.trim().isEmpty()) {
            throw new IllegalArgumentException("nonce is required");
        }
        String canonical = method + "\n"
                + path + "\n"
                + clientId + "\n"
                + timestamp + "\n"
                + nonce + "\n"
                + sha256(body);
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret, "HmacSHA256"));
        String signature = hex(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));

        Map<String, String> headers = new LinkedHashMap<String, String>();
        if (posRequestNo != null) {
            headers.put("X-POS-Request-Id", posRequestNo);
        }
        headers.put("X-Client-Id", clientId);
        headers.put("X-Timestamp", String.valueOf(timestamp));
        headers.put("X-Nonce", nonce);
        headers.put("X-Signature", signature);
        return new SignedRequest(method, path, canonical, headers, body);
    }

    private static String requiredRequestNo(Map<String, Object> body) {
        Object value = body == null ? null : body.get("posRequestNo");
        if (value == null || String.valueOf(value).trim().isEmpty()) {
            throw new IllegalArgumentException("body.posRequestNo is required");
        }
        return String.valueOf(value);
    }

    private static String sha256(byte[] value) throws Exception {
        return hex(MessageDigest.getInstance("SHA-256").digest(value));
    }

    private static String hex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            builder.append(String.format("%02x", value & 0xff));
        }
        return builder.toString();
    }

    public static final class SignedRequest {
        private final String method;
        private final String path;
        private final String canonical;
        private final Map<String, String> headers;
        private final byte[] body;

        private SignedRequest(String method, String path, String canonical,
                              Map<String, String> headers, byte[] body) {
            this.method = method;
            this.path = path;
            this.canonical = canonical;
            this.headers = Collections.unmodifiableMap(new LinkedHashMap<String, String>(headers));
            this.body = body.clone();
        }

        public String getMethod() {
            return method;
        }

        public String getPath() {
            return path;
        }

        public String getCanonical() {
            return canonical;
        }

        public Map<String, String> getHeaders() {
            return headers;
        }

        public byte[] getBody() {
            return body.clone();
        }
    }

    public static final class ClientResponse {
        private final int status;
        private final Map<String, Object> body;

        private ClientResponse(int status, Map<String, Object> body) {
            this.status = status;
            this.body = Collections.unmodifiableMap(new LinkedHashMap<String, Object>(body));
        }

        public int getStatus() {
            return status;
        }

        public Map<String, Object> getBody() {
            return body;
        }
    }
}
