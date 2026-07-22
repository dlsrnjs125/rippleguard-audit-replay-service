package dev.rippleguard.audit.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class PayloadSanitizer {
    private static final Pattern SAFE_REFERENCE = Pattern.compile("^(synthetic|masked):[A-Za-z0-9._/-]+$");
    private static final String REDACTED_INVALID_REFERENCE = "[REDACTED_INVALID_REFERENCE]";

    private final ObjectMapper objectMapper;

    public PayloadSanitizer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public JsonNode sanitize(EventEnvelope event, String payloadHash, String unsupportedReason) {
        if (unsupportedReason != null) {
            return unsupportedPayloadSummary(event, payloadHash, unsupportedReason);
        }
        JsonNode payload = event.payload();
        if (payload == null || payload.isNull()) {
            return objectMapper.createObjectNode();
        }
        return switch (event.eventType()) {
            case "loan.application.submitted.v1" -> sanitizeSubmitted(payload);
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
            case "governance.agent-result.validated.v1" -> allow(payload,
                    "decisionCaseId", "evaluationRunId", "agentRunId", "attemptId", "agentResultReference",
                    "agentResultDigest", "validationOutcome", "validationReasonCodes", "validatedSchemaVersion",
                    "validatedAt");
            default -> unsupportedPayloadSummary(event, payloadHash);
        };
    }

    private ObjectNode sanitizeSubmitted(JsonNode payload) {
        ObjectNode sanitized = allow(payload, "applicationId", "inputSnapshotVersion", "submittedAt", "submissionChannel");
        JsonNode applicantId = payload.get("applicantId");
        if (applicantId != null && !applicantId.isNull()) {
            sanitized.set("applicantId", sanitizeReference(applicantId));
        }
        return sanitized;
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
            copyReferenceArrayIfPresent(envelope, decisionEnvelope, "usedEvidenceRefs");
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
        return unsupportedPayloadSummary(event, payloadHash, "UNSUPPORTED_EVENT_TYPE");
    }

    private ObjectNode unsupportedPayloadSummary(EventEnvelope event, String payloadHash, String reason) {
        ObjectNode sanitized = objectMapper.createObjectNode();
        JsonNode payload = event.payload();
        sanitized.put("redacted", true);
        sanitized.put("reason", reason);
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

    private JsonNode sanitizeReference(JsonNode value) {
        if (!value.isTextual()) {
            return TextNode.valueOf(REDACTED_INVALID_REFERENCE);
        }
        String text = value.asText();
        if (SAFE_REFERENCE.matcher(text).matches()) {
            return value;
        }
        return TextNode.valueOf(REDACTED_INVALID_REFERENCE);
    }

    private JsonNode sanitizeEvidenceReference(JsonNode value) {
        if (!value.isTextual()) {
            return TextNode.valueOf(REDACTED_INVALID_REFERENCE);
        }
        String text = value.asText();
        if (text.startsWith("snapshot://") || SAFE_REFERENCE.matcher(text).matches()) {
            return value;
        }
        return TextNode.valueOf(REDACTED_INVALID_REFERENCE);
    }

    private void copyReferenceArrayIfPresent(JsonNode source, ObjectNode target, String field) {
        JsonNode value = source.get(field);
        if (value == null || value.isNull()) {
            return;
        }
        ArrayNode sanitized = objectMapper.createArrayNode();
        if (value.isArray()) {
            value.forEach(item -> sanitized.add(sanitizeEvidenceReference(item)));
        }
        target.set(field, sanitized);
    }

    private void copyIfPresent(JsonNode source, ObjectNode target, String field) {
        JsonNode value = source.get(field);
        if (value != null && !value.isNull()) {
            target.set(field, value);
        }
    }
}
