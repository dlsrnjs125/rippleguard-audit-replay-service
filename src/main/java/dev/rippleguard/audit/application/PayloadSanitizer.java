package dev.rippleguard.audit.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class PayloadSanitizer {
    private static final Set<String> SECRET_KEYS = Set.of(
            "prompt",
            "promptText",
            "secret",
            "token",
            "password",
            "documentText",
            "rawDocument",
            "financialSnapshot",
            "snapshot",
            "ssn"
    );
    private final ObjectMapper objectMapper;

    public PayloadSanitizer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public JsonNode sanitize(JsonNode payload) {
        if (payload == null || payload.isNull()) {
            return objectMapper.createObjectNode();
        }
        return sanitizeNode(payload);
    }

    private JsonNode sanitizeNode(JsonNode node) {
        if (node.isObject()) {
            ObjectNode sanitized = objectMapper.createObjectNode();
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                if (isSensitiveKey(field.getKey())) {
                    sanitized.put(field.getKey(), "[REDACTED]");
                } else {
                    sanitized.set(field.getKey(), sanitizeNode(field.getValue()));
                }
            }
            return sanitized;
        }
        if (node.isArray()) {
            ArrayNode sanitized = objectMapper.createArrayNode();
            node.forEach(item -> sanitized.add(sanitizeNode(item)));
            return sanitized;
        }
        return node;
    }

    private boolean isSensitiveKey(String key) {
        String normalized = key.toLowerCase(Locale.ROOT);
        return SECRET_KEYS.stream().anyMatch(sensitive -> normalized.contains(sensitive.toLowerCase(Locale.ROOT)));
    }
}
