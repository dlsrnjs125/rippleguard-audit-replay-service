package dev.rippleguard.audit.interfaces.rest;

import dev.rippleguard.audit.domain.TraceCompleteness;
import java.util.List;
import java.util.UUID;

public record CaseTimelineResponse(String schemaVersion,
                                   String caseId,
                                   UUID applicationId,
                                   List<TimelineEventResponse> events,
                                   TraceCompleteness traceCompleteness,
                                   List<String> warnings) {
}
