package com.qinghe.marketing.campaign;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

@Component
public class BenefitTemplateSnapshotCodec {

    private final ObjectMapper objectMapper;

    public BenefitTemplateSnapshotCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String encode(BenefitTemplateDraft draft) {
        try {
            JsonNode usageRules = objectMapper.readTree(draft.usageRulesJson());
            if (usageRules == null || !usageRules.isObject()) {
                throw new IllegalArgumentException("usage rules must be a JSON object");
            }
            ObjectNode root = objectMapper.createObjectNode();
            root.put("templateName", draft.templateName());
            root.put("benefitType", draft.benefitType().name());
            root.put("title", draft.title());
            putNullable(root, "description", draft.description());
            putNullable(root, "productCode", draft.productCode());
            if (draft.benefitValueFen() == null) {
                root.putNull("benefitValueFen");
            } else {
                root.put("benefitValueFen", draft.benefitValueFen());
            }
            root.put("validityType", draft.validityType().name());
            if (draft.validityValue() == null) {
                root.putNull("validityValue");
            } else {
                root.put("validityValue", draft.validityValue());
            }
            putNullable(root, "validFrom", draft.validFrom() == null ? null : draft.validFrom().toString());
            putNullable(root, "validUntil", draft.validUntil() == null ? null : draft.validUntil().toString());
            root.set("usageRules", usageRules);
            return objectMapper.writeValueAsString(root);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("usage rules are not valid JSON", exception);
        }
    }

    public BenefitTemplateDraft decode(String snapshotJson) {
        try {
            JsonNode root = objectMapper.readTree(snapshotJson);
            return new BenefitTemplateDraft(
                    text(root, "templateName"),
                    BenefitType.valueOf(text(root, "benefitType")),
                    text(root, "title"), nullableText(root, "description"),
                    nullableText(root, "productCode"), nullableLong(root, "benefitValueFen"),
                    ValidityType.valueOf(text(root, "validityType")), nullableInteger(root, "validityValue"),
                    dateTime(root, "validFrom"), dateTime(root, "validUntil"),
                    objectMapper.writeValueAsString(root.path("usageRules")));
        } catch (RuntimeException | JsonProcessingException exception) {
            throw new IllegalStateException("stored benefit template snapshot is invalid", exception);
        }
    }

    private static void putNullable(ObjectNode root, String name, String value) {
        if (value == null) {
            root.putNull(name);
        } else {
            root.put(name, value);
        }
    }

    private static String text(JsonNode root, String name) {
        String value = nullableText(root, name);
        if (value == null) {
            throw new IllegalArgumentException("missing snapshot field: " + name);
        }
        return value;
    }

    private static String nullableText(JsonNode root, String name) {
        JsonNode value = root.get(name);
        return value == null || value.isNull() ? null : value.asText();
    }

    private static Long nullableLong(JsonNode root, String name) {
        JsonNode value = root.get(name);
        return value == null || value.isNull() ? null : value.asLong();
    }

    private static Integer nullableInteger(JsonNode root, String name) {
        JsonNode value = root.get(name);
        return value == null || value.isNull() ? null : value.asInt();
    }

    private static java.time.LocalDateTime dateTime(JsonNode root, String name) {
        String value = nullableText(root, name);
        return value == null ? null : java.time.LocalDateTime.parse(value);
    }
}
