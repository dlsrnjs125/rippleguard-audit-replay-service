# Troubleshooting

## Duplicate Events

Check `inbox_event.duplicate_count`. Duplicate records do not create extra `audit_event` rows.

## Missing Timeline Events

If the API returns `PARTIAL` with `EVENT_GAP_DETECTED`, one or more expected Phase 1 events have not been ingested.

## Invalid Causation

`INVALID_REFERENCE` means an event points to a causation id that is not present earlier in the case timeline.

## Unknown Version

Unknown event versions are recorded with `unknown_version=true`. Their payload bodies are redacted, and incomplete timelines surface contract-supported warnings such as `EVENT_GAP_DETECTED`.

## Malformed Event Envelope

Malformed raw JSON or invalid envelope fields are quarantined at the consumer boundary. They are not written to `audit_event` or `inbox_event`; logs include only a payload hash and reason.
