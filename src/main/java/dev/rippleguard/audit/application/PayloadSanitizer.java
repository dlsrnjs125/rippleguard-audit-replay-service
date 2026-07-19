package dev.rippleguard.audit.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class PayloadSanitizer {
    private final ObjectMapper objectMapper;

    public PayloadSanitizer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public JsonNode sanitize(EventEnvelope event, String payloadHash, boolean unknownVersion) {
        if (unknownVersion) {
            return unsupportedPayloadSummary(event, payloadHash);
        }
        JsonNode payload = event.payload();
        if (payload == null || payload.isNull()) {
            return objectMapper.createObjectNode();
        }
        return switch (event.eventType()) {
            case "loan.application.submitted.v1" -> allow(payload,
                    "applicationId", "applicantId", "inputSnapshotVersion", "submittedAt", "submissionChannel");
            case "governance.review.started.v1" -> allow(payload,
                    "decisionCaseId", "applicationId", "reviewStartedAt");
            case "agent.evaluation.requested.v1" -> allow(payload,
                    "evaluationRunId", "decisionCaseId", "inputSnapshotVersion", "executionPlanVersion", "evaluationMode");
            case "agent.evaluation.completed.v1" -> sanitizeEvaluationCompleted(payload);
            case "loan.decision.commanded.v1" -> allow(payload,
                    "commandId", "decisionCaseId", "applicationId", "evaluationRunId", "finalDecision",
                    "assuranceResult", "reasonCodes", "issuedAt");
            case "loan.decision.finalized.v1" -> allow(payload,
                    "commandId", "decisionCaseId", "applicationId", "decisionId", "evaluationRunId",
                    "finalDecision", "finalizedAt");
            default -> unsupportedPayloadSummary(event, payloadHash);
        };
    }

    private ObjectNode allow(JsonNode payload, String... allowedFields) {
        ObjectNode sanitized = objectMapper.createObjectNode();
        Set<String> allowed = Set.of(allowedFields);
        allowed.forEach(field -> {
            JsonNode value = payload.get(field);
            if (value != null && !value.isNull()) {
                sanitized.set(field, value);
            }
        });
        return sanitized;
    }

    private ObjectNode sanitizeEvaluationCompleted(JsonNode payload) {
        ObjectNode sanitized = allow(payload, "evaluationRunId", "decisionCaseId", "evaluationMode", "evaluatorId");
        JsonNode envelope = payload.get("decisionEnvelope");
        if (envelope != null && envelope.isObject()) {
            ObjectNode decisionEnvelope = objectMapper.createObjectNode();
            copyIfPresent(envelope, decisionEnvelope, "decisionId");
            copyIfPresent(envelope, decisionEnvelope, "evaluationRunId");
            copyIfPresent(envelope, decisionEnvelope, "decisionCaseId");
            copyIfPresent(envelope, decisionEnvelope, "evaluatorId");
            copyIfPresent(envelope, decisionEnvelope, "proposal");
            copyIfPresent(envelope, decisionEnvelope, "status");
            copyIfPresent(envelope, decisionEnvelope, "confidence");
            copyIfPresent(envelope, decisionEnvelope, "usedEvidenceRefs");
            copyIfPresent(envelope, decisionEnvelope, "validUntil");
            JsonNode generatorRef = envelope.get("generatorRef");
            if (generatorRef != null && generatorRef.isObject()) {
                decisionEnvelope.set("generatorRef", allow(generatorRef,
                        "agentName", "agentVersion", "modelName", "modelVersion", "promptName", "promptVersion"));
            }
            sanitized.set("decisionEnvelope", decisionEnvelope);
        }
        return sanitized;
    }

    private ObjectNode unsupportedPayloadSummary(EventEnvelope event, String payloadHash) {
        ObjectNode sanitized = objectMapper.createObjectNode();
        JsonNode payload = event.payload();
        sanitized.put("redacted", true);
        sanitized.put("reason", "UNSUPPORTED_SCHEMA_VERSION");
        sanitized.put("payloadHash", payloadHash);
        sanitized.put("payloadSize", payload == null || payload.isNull()
                ? 0 : payload.toString().getBytes(StandardCharsets.UTF_8).length);
        ArrayNode topLevelKeys = objectMapper.createArrayNode();
        if (payload != null && payload.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = payload.fields();
            while (fields.hasNext()) {
                topLevelKeys.add(fields.next().getKey());
            }
        }
        sanitized.set("topLevelKeys", topLevelKeys);
        return sanitized;
    }

    private void copyIfPresent(JsonNode source, ObjectNode target, String field) {
        JsonNode value = source.get(field);
        if (value != null && !value.isNull()) {
            target.set(field, value);
        }
    }
}
