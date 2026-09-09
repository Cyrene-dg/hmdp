package com.qinghe.marketing.entitlement;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class BenefitIssueCommandCodec {

    private final ObjectMapper objectMapper;

    public BenefitIssueCommandCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public BenefitIssueCommand decode(String payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            BenefitIssueCommand command = new BenefitIssueCommand(
                    text(root, "eventId"), text(root, "claimNo"), text(root, "reservationId"),
                    positiveLong(root, "campaignId"), positiveLong(root, "memberId"),
                    text(root, "requestId"));
            return command;
        } catch (RuntimeException invalid) {
            throw invalid;
        } catch (Exception invalid) {
            throw new IllegalArgumentException("benefit issue message is invalid", invalid);
        }
    }

    private static String text(JsonNode root, String field) {
        JsonNode value = root == null ? null : root.get(field);
        if (value == null || !value.isTextual() || value.asText().trim().isEmpty()) {
            throw new IllegalArgumentException("benefit issue message misses " + field);
        }
        return value.asText();
    }

    private static long positiveLong(JsonNode root, String field) {
        JsonNode value = root == null ? null : root.get(field);
        if (value == null || !value.canConvertToLong() || value.asLong() <= 0) {
            throw new IllegalArgumentException("benefit issue message has invalid " + field);
        }
        return value.asLong();
    }
}

