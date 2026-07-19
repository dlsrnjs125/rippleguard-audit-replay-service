package dev.rippleguard.audit.interfaces.rest;

import dev.rippleguard.audit.application.TimelineService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/cases")
public class TimelineController {
    private final TimelineService timelineService;

    public TimelineController(TimelineService timelineService) {
        this.timelineService = timelineService;
    }

    @GetMapping("/{caseId}/timeline")
    public CaseTimelineResponse timeline(@PathVariable String caseId) {
        return timelineService.timeline(caseId);
    }
}
