# Timeline Model

`audit_event` stores the sanitized event envelope fields:

- event id, type, schema version, producer
- application id, case id, evaluation run id
- correlation id and causation id
- occurred and ingested timestamps
- sanitized payload and payload hash

`GET /api/v1/cases/{caseId}/timeline` first discovers the case correlation id from events matching the requested case id, then returns all events in that correlation sorted by `occurredAt` and `ingestedAt`. This keeps the original `loan.application.submitted.v1` event in the timeline even though Loan Service uses `caseId = applicationId` before Governance creates `case-<applicationId>`.

Trace completeness is:

- `COMPLETE` when all Phase 1 path events are present without invalid references
- `PARTIAL` when expected path events or causation references are missing
- `UNKNOWN` only for an empty internal event set

Timeline event status can be `RECORDED`, `LATE`, or `INVALID_REFERENCE`.

## Phase 2 Agent Run Read Model

`agent_run_audit` stores Governance validation outcomes for Phase 2 Agent Results:

- decision case id, evaluation run id, agent run id
- `VALIDATED` or `REJECTED` validation outcome
- validation reason codes
- agent result reference and digest
- source event id and schema version
- validated timestamp and latest attempt id

`agent_attempt_audit` stores the attempt identity that is present in the Governance validation event. It does not invent earlier retry attempts when those attempts were not emitted as audit events.

The validation event does not currently carry full model, snapshot, feature, preprocessing, or threshold metadata, so those read-model fields remain nullable. The service does not fill them from current Governance DB state, Agent Runtime output, model manifests, or defaults.

`GET /api/v1/cases/{caseId}/timeline` includes `governance.agent-result.validated.v1` with a summary derived from the Agent Run projection outcome. Rejected results are displayed as Governance rejection and are not represented as final loan decisions.
