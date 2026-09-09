package com.qinghe.marketing.contract;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qinghe.marketing.contract.client.PosContractClient;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PosContractClientTest {

    private static final String CLIENT_ID = "pos-client-qh006";
    private static final String SECRET = "local-test-secret-not-for-production";
    private static final long TIMESTAMP = 1788849000L;

    @Test
    void signedRedeemRequestFollowsReviewedCanonicalForm() throws Exception {
        PosContractClient client = new PosContractClient(CLIENT_ID, SECRET);
        Map<String, Object> body = redeemBody("REDEEM-QH006-20260908-0001", "RGT-F4B8N2K9Q6");

        PosContractClient.SignedRequest request = client.redeem(body, TIMESTAMP, "nonce-test-001");

        assertEquals("POST", request.getMethod());
        assertEquals("/openapi/v1/redemptions", request.getPath());
        assertEquals(body.get("posRequestNo"), request.getHeaders().get("X-POS-Request-Id"));
        assertEquals(CLIENT_ID, request.getHeaders().get("X-Client-Id"));
        assertEquals(String.valueOf(TIMESTAMP), request.getHeaders().get("X-Timestamp"));
        assertTrue(request.getCanonical().startsWith("POST\n/openapi/v1/redemptions\n" + CLIENT_ID + "\n"));
        assertFalse(request.getCanonical().endsWith("\n"));
        assertTrue(request.getHeaders().get("X-Signature").matches("[a-f0-9]{64}"));

        Map<?, ?> encoded = new ObjectMapper().readValue(request.getBody(), Map.class);
        assertEquals(body.get("posRequestNo"), encoded.get("posRequestNo"));
    }

    @Test
    void sameInputsProduceSameSignatureButBusinessChangesDoNot() throws Exception {
        PosContractClient client = new PosContractClient(CLIENT_ID, SECRET);
        Map<String, Object> firstBody = redeemBody("REDEEM-QH006-20260908-0001", "RGT-F4B8N2K9Q6");
        Map<String, Object> changedBody = redeemBody("REDEEM-QH006-20260908-0001", "RGT-CHANGED-0001");

        PosContractClient.SignedRequest first = client.redeem(firstBody, TIMESTAMP, "nonce-test-001");
        PosContractClient.SignedRequest duplicate = client.redeem(firstBody, TIMESTAMP, "nonce-test-001");
        PosContractClient.SignedRequest changed = client.redeem(changedBody, TIMESTAMP, "nonce-test-001");

        assertEquals(first.getHeaders().get("X-Signature"), duplicate.getHeaders().get("X-Signature"));
        assertNotEquals(first.getHeaders().get("X-Signature"), changed.getHeaders().get("X-Signature"));
    }

    @Test
    void resultQueryKeepsOriginalRequestNumberInPathAndHasNoWriteHeader() throws Exception {
        PosContractClient client = new PosContractClient(CLIENT_ID, SECRET);
        PosContractClient.SignedRequest request = client.queryRedemption(
                "REDEEM-QH006-20260908-0001", TIMESTAMP, "nonce-query-001");

        assertEquals("GET", request.getMethod());
        assertEquals("/openapi/v1/redemptions/by-request/REDEEM-QH006-20260908-0001", request.getPath());
        assertFalse(request.getHeaders().containsKey("X-POS-Request-Id"));
        assertEquals(0, request.getBody().length);
    }

    @Test
    void writeRequestCannotBeCreatedWithoutStablePosRequestNumber() {
        PosContractClient client = new PosContractClient(CLIENT_ID, SECRET);
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("rightCode", "RGT-F4B8N2K9Q6");

        assertThrows(IllegalArgumentException.class,
                () -> client.redeem(body, TIMESTAMP, "nonce-test-001"));
    }

    @Test
    void signedBodyDoesNotLogOrExposeSecretInCanonicalText() throws Exception {
        PosContractClient client = new PosContractClient(CLIENT_ID, SECRET);
        PosContractClient.SignedRequest request = client.redeem(
                redeemBody("REDEEM-QH006-20260908-0001", "RGT-F4B8N2K9Q6"),
                TIMESTAMP, "nonce-test-001");

        assertFalse(request.getCanonical().contains(SECRET));
        assertFalse(new String(request.getBody(), StandardCharsets.UTF_8).contains(SECRET));
    }

    private static Map<String, Object> redeemBody(String posRequestNo, String rightCode) {
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("posRequestNo", posRequestNo);
        body.put("posOrderNo", "ORDER-QH006-880012");
        body.put("storeCode", "QH006");
        body.put("terminalNo", "T03");
        body.put("operatorNo", "OP018");
        body.put("rightCode", rightCode);
        body.put("occurredAt", "2026-09-08T14:52:10+08:00");
        return body;
    }
}
