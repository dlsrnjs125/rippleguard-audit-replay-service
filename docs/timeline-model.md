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
