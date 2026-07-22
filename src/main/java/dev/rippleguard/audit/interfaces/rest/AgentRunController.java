package dev.rippleguard.audit.interfaces.rest;

import dev.rippleguard.audit.application.AgentRunService;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class AgentRunController {
    private final AgentRunService agentRunService;

    public AgentRunController(AgentRunService agentRunService) {
        this.agentRunService = agentRunService;
    }

    @GetMapping("/agent-runs/{agentRunId}")
    public AgentRunResponse agentRun(@PathVariable UUID agentRunId) {
        return agentRunService.get(agentRunId);
    }

    @GetMapping("/evaluation-runs/{evaluationRunId}/agent-runs")
    public List<AgentRunResponse> evaluationRunAgentRuns(@PathVariable UUID evaluationRunId) {
        return agentRunService.byEvaluationRun(evaluationRunId);
    }
}
