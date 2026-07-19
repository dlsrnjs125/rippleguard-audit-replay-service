# Timeline Model

`audit_event` stores the sanitized event envelope fields:

- event id, type, schema version, producer
- application id, case id, evaluation run id
- correlation id and causation id
- occurred and ingested timestamps
- sanitized payload and payload hash

`GET /api/v1/cases/{caseId}/timeline` returns events sorted by `occurredAt` and `ingestedAt`.

Trace completeness is:

- `COMPLETE` when all Phase 1 path events are present without invalid references
- `PARTIAL` when expected path events or causation references are missing
- `UNKNOWN` only for an empty internal event set

Timeline event status can be `RECORDED`, `LATE`, or `INVALID_REFERENCE`.
