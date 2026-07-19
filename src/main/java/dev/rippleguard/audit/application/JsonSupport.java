package dev.rippleguard.audit.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.springframework.stereotype.Component;

@Component
public class JsonSupport {
    private final ObjectMapper objectMapper;

    public JsonSupport(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper.copy()
                .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
    }

    public EventEnvelope eventEnvelope(String rawMessage) {
        try {
            return objectMapper.readValue(rawMessage, EventEnvelope.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Malformed event envelope", exception);
        }
    }

    public String canonicalJson(JsonNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Unable to serialize JSON", exception);
        }
    }

    public String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
