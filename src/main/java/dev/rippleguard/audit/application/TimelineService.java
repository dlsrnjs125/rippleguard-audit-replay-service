package dev.rippleguard.audit.application;

import dev.rippleguard.audit.domain.TimelineEventStatus;
import dev.rippleguard.audit.domain.TimelineWarning;
import dev.rippleguard.audit.domain.TraceCompleteness;
import dev.rippleguard.audit.infrastructure.persistence.AuditEventEntity;
import dev.rippleguard.audit.infrastructure.persistence.AuditEventRepository;
import dev.rippleguard.audit.interfaces.rest.CaseTimelineResponse;
import dev.rippleguard.audit.interfaces.rest.TimelineEventResponse;
import java.time.Instant;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TimelineService {
    private static final List<String> COMPLETE_PATH = List.of(
            "loan.application.submitted.v1",
            "governance.review.started.v1",
            "agent.evaluation.requested.v1",
            "agent.evaluation.completed.v1",
            "loan.decision.commanded.v1",
            "loan.decision.finalized.v1"
    );

    private final AuditEventRepository auditEvents;

    public TimelineService(AuditEventRepository auditEvents) {
        this.auditEvents = auditEvents;
    }

    @Transactional(readOnly = true)
    public CaseTimelineResponse timeline(String caseId) {
        TimelineQueryResult queryResult = eventsForCaseTimeline(caseId);
        List<AuditEventEntity> events = queryResult.events();
        if (events.isEmpty()) {
            throw new NotFoundException("No audit timeline found for case: " + caseId);
        }

        Set<UUID> eventIds = new HashSet<>();
        Set<String> eventTypes = new HashSet<>();
        EnumSet<TimelineWarning> warnings = EnumSet.noneOf(TimelineWarning.class);
        for (AuditEventEntity event : events) {
            eventIds.add(event.getEventId());
            eventTypes.add(event.getEventType());
            if (event.isUnknownVersion()) {
                warnings.add(TimelineWarning.EVENT_GAP_DETECTED);
            }
            if (event.getCausationId() != null && !eventIds.contains(event.getCausationId())) {
                warnings.add(TimelineWarning.INVALID_REFERENCE);
                warnings.add(TimelineWarning.EVENT_GAP_DETECTED);
            }
        }
        if (!eventTypes.containsAll(COMPLETE_PATH)) {
            warnings.add(TimelineWarning.EVENT_GAP_DETECTED);
        }
        if (queryResult.ambiguousCorrelation()) {
            warnings.add(TimelineWarning.INVALID_REFERENCE);
            warnings.add(TimelineWarning.EVENT_GAP_DETECTED);
        }

        Set<UUID> invalidReferences = invalidReferenceEventIds(events);
        Set<UUID> lateEvents = lateEventIds(events);
        if (!lateEvents.isEmpty()) {
            warnings.add(TimelineWarning.LATE_EVENT_PENDING);
        }
        List<TimelineEventResponse> responseEvents = events.stream()
                .map(event -> toResponse(caseId, event, invalidReferences, lateEvents, queryResult.ambiguousCorrelation()))
                .toList();

        TraceCompleteness completeness = traceCompleteness(eventTypes, warnings);
        return new CaseTimelineResponse(
                "1.0.0",
                caseId,
                events.get(0).getApplicationId(),
                responseEvents,
                completeness,
                warnings.stream().map(Enum::name).toList()
        );
    }

    private TimelineQueryResult eventsForCaseTimeline(String caseId) {
        List<AuditEventEntity> directlyMatched = auditEvents.findByCaseIdOrderByOccurredAtAscIngestedAtAsc(caseId);
        if (!directlyMatched.isEmpty()) {
            Set<String> correlationIds = new LinkedHashSet<>();
            directlyMatched.forEach(event -> correlationIds.add(event.getCorrelationId()));
            if (correlationIds.size() > 1) {
                return new TimelineQueryResult(directlyMatched, true);
            }
            return new TimelineQueryResult(
                    auditEvents.findByCorrelationIdOrderByOccurredAtAscIngestedAtAsc(correlationIds.iterator().next()),
                    false
            );
        }
        return new TimelineQueryResult(auditEvents.findByCorrelationIdOrderByOccurredAtAscIngestedAtAsc(caseId), false);
    }

    private Set<UUID> invalidReferenceEventIds(List<AuditEventEntity> events) {
        Set<UUID> seen = new HashSet<>();
        Set<UUID> invalid = new HashSet<>();
        for (AuditEventEntity event : events) {
            if (event.getCausationId() != null && !seen.contains(event.getCausationId())) {
                invalid.add(event.getEventId());
            }
            seen.add(event.getEventId());
        }
        return invalid;
    }

    private Set<UUID> lateEventIds(List<AuditEventEntity> events) {
        List<AuditEventEntity> ingestionOrder = events.stream()
                .sorted(Comparator.comparing(AuditEventEntity::getIngestedAt))
                .toList();
        Set<UUID> late = new HashSet<>();
        Instant maxOccurredAt = Instant.MIN;
        for (AuditEventEntity event : ingestionOrder) {
            if (event.getOccurredAt().isBefore(maxOccurredAt)) {
                late.add(event.getEventId());
            }
            if (event.getOccurredAt().isAfter(maxOccurredAt)) {
                maxOccurredAt = event.getOccurredAt();
            }
        }
        return late;
    }

    private TimelineEventResponse toResponse(String timelineCaseId, AuditEventEntity event,
                                             Set<UUID> invalidReferences, Set<UUID> lateEvents,
                                             boolean ambiguousCorrelation) {
        TimelineEventStatus status = TimelineEventStatus.RECORDED;
        if (ambiguousCorrelation || invalidReferences.contains(event.getEventId())) {
            status = TimelineEventStatus.INVALID_REFERENCE;
        } else if (lateEvents.contains(event.getEventId())) {
            status = TimelineEventStatus.LATE;
        }
        return new TimelineEventResponse(
                event.getEventId(),
                event.getEventType(),
                timelineCaseId,
                event.getOccurredAt(),
                event.getProducer(),
                event.getEvaluationRunId(),
                event.getCorrelationId(),
                event.getCausationId(),
                status,
                summary(event)
        );
    }

    private TraceCompleteness traceCompleteness(Set<String> eventTypes, Set<TimelineWarning> warnings) {
        if (eventTypes.isEmpty()) {
            return TraceCompleteness.UNKNOWN;
        }
        if (eventTypes.containsAll(COMPLETE_PATH) && warnings.stream().noneMatch(w -> w != TimelineWarning.LATE_EVENT_PENDING)) {
            return TraceCompleteness.COMPLETE;
        }
        return TraceCompleteness.PARTIAL;
    }

    private String summary(AuditEventEntity event) {
        return switch (event.getEventType()) {
            case "loan.application.submitted.v1" -> "Loan application submitted";
            case "governance.review.started.v1" -> "Governance review started";
            case "agent.evaluation.requested.v1" -> "Mock evaluation requested";
            case "agent.evaluation.completed.v1" -> "Mock evaluation completed";
            case "loan.decision.commanded.v1" -> "Loan decision commanded";
            case "loan.decision.finalized.v1" -> "Loan decision finalized";
            default -> "Unknown Phase 1 event version or type recorded";
        };
    }

    private record TimelineQueryResult(List<AuditEventEntity> events, boolean ambiguousCorrelation) {
    }
}
