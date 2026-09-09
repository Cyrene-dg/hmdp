package com.qinghe.marketing.contract;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenApiContractStructureTest {

    private static final Path CONTRACT_DIR = Paths.get("docs", "contracts");
    private static final Set<String> HTTP_METHODS = new HashSet<String>(Arrays.asList(
            "get", "post", "put", "patch", "delete", "head", "options", "trace"
    ));

    @Test
    void allOpenApiContractsAreParseableAndHaveUniqueOperationIds() throws IOException {
        Set<String> allOperationIds = new HashSet<String>();

        ContractSummary memberCenter = inspect(
                CONTRACT_DIR.resolve("member-center-api-v0.1.yaml"), allOperationIds);
        ContractSummary pos = inspect(
                CONTRACT_DIR.resolve("pos-rights-api-v0.1.yaml"), allOperationIds);
        ContractSummary platform = inspect(
                CONTRACT_DIR.resolve("qinghe-platform-api-v1.0.yaml"), allOperationIds);

        assertEquals(1, memberCenter.operationCount);
        assertEquals(4, pos.operationCount);
        assertEquals(35, platform.operationCount);
    }

    @Test
    void platformContractContainsEveryReviewedMemberAndAdminOperation() throws IOException {
        Map<String, Object> root = load(CONTRACT_DIR.resolve("qinghe-platform-api-v1.0.yaml"));
        Map<String, Object> paths = map(root.get("paths"), "paths");

        assertTrue(paths.containsKey("/api/v1/member/sessions/exchange"));
        assertTrue(paths.containsKey("/api/v1/member/campaigns/{campaignNo}/claims"));
        assertTrue(paths.containsKey("/api/v1/member/claims/{claimNo}"));
        assertTrue(paths.containsKey("/api/v1/member/entitlements/{entitlementNo}"));
        assertTrue(paths.containsKey("/api/v1/admin/campaigns/{campaignNo}/reviews"));
        assertTrue(paths.containsKey("/api/v1/admin/inventory-adjustments/{adjustmentNo}/reviews"));
        assertTrue(paths.containsKey("/api/v1/admin/business-traces"));
        assertTrue(paths.containsKey("/api/v1/admin/recon-batches/{reconBatchNo}/retry"));
        assertTrue(paths.containsKey("/api/v1/admin/settlement-batches/{settlementBatchNo}/confirm"));
        assertTrue(paths.containsKey("/api/v1/admin/settlement-batches/{settlementBatchNo}/export"));
    }

    @Test
    void posContractKeepsUnknownResultRecoveryOperations() throws IOException {
        Map<String, Object> root = load(CONTRACT_DIR.resolve("pos-rights-api-v0.1.yaml"));
        Map<String, Object> paths = map(root.get("paths"), "paths");

        assertTrue(paths.containsKey("/openapi/v1/redemptions"));
        assertTrue(paths.containsKey("/openapi/v1/redemptions/by-request/{posRequestNo}"));
        assertTrue(paths.containsKey("/openapi/v1/redemptions/{redemptionNo}/reversals"));
    }

    private ContractSummary inspect(Path file, Set<String> allOperationIds) throws IOException {
        Map<String, Object> root = load(file);
        assertEquals("3.0.3", String.valueOf(root.get("openapi")), file.toString());
        assertNotNull(root.get("info"), file.toString());

        Map<String, Object> paths = map(root.get("paths"), file + " paths");
        assertTrue(!paths.isEmpty(), file.toString());
        assertLocalReferencesResolve(root, root, file.toString());

        Set<String> localOperationIds = new LinkedHashSet<String>();
        for (Map.Entry<String, Object> pathEntry : paths.entrySet()) {
            assertTrue(pathEntry.getKey().startsWith("/"), pathEntry.getKey());
            Map<String, Object> pathItem = map(pathEntry.getValue(), pathEntry.getKey());
            for (Map.Entry<String, Object> operationEntry : pathItem.entrySet()) {
                if (!HTTP_METHODS.contains(operationEntry.getKey())) {
                    continue;
                }
                Map<String, Object> operation = map(operationEntry.getValue(), operationEntry.getKey());
                String operationId = String.valueOf(operation.get("operationId"));
                assertTrue(operationId != null && !"null".equals(operationId) && !operationId.trim().isEmpty(),
                        file + " " + pathEntry.getKey() + " " + operationEntry.getKey());
                assertTrue(localOperationIds.add(operationId), "duplicate operationId in " + file + ": " + operationId);
                assertTrue(allOperationIds.add(operationId), "duplicate operationId across contracts: " + operationId);
                assertNotNull(operation.get("responses"), operationId + " responses");
            }
        }
        return new ContractSummary(localOperationIds.size());
    }

    private void assertLocalReferencesResolve(Object node, Map<String, Object> root, String file) {
        if (node instanceof Map) {
            Map<?, ?> object = (Map<?, ?>) node;
            Object reference = object.get("$ref");
            if (reference != null) {
                String value = String.valueOf(reference);
                assertTrue(value.startsWith("#/"), file + " contains unsupported external ref: " + value);
                resolveJsonPointer(root, value, file);
            }
            for (Object child : object.values()) {
                assertLocalReferencesResolve(child, root, file);
            }
        } else if (node instanceof List) {
            for (Object child : (List<?>) node) {
                assertLocalReferencesResolve(child, root, file);
            }
        }
    }

    private void resolveJsonPointer(Map<String, Object> root, String reference, String file) {
        Object current = root;
        String[] segments = reference.substring(2).split("/");
        for (String rawSegment : segments) {
            String segment = rawSegment.replace("~1", "/").replace("~0", "~");
            assertTrue(current instanceof Map, file + " ref does not resolve: " + reference);
            Map<?, ?> object = (Map<?, ?>) current;
            assertTrue(object.containsKey(segment), file + " ref does not resolve: " + reference);
            current = object.get(segment);
        }
        assertNotNull(current, file + " ref resolves to null: " + reference);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> load(Path file) throws IOException {
        assertTrue(Files.isRegularFile(file), file.toAbsolutePath().toString());
        try (InputStream input = Files.newInputStream(file)) {
            Object value = new Yaml().load(input);
            return map(value, file.toString());
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value, String label) {
        assertTrue(value instanceof Map, label + " must be an object");
        return (Map<String, Object>) value;
    }

    private static final class ContractSummary {
        private final int operationCount;

        private ContractSummary(int operationCount) {
            this.operationCount = operationCount;
        }
    }
}
