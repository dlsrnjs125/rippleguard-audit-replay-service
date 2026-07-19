# Privacy

Audit stores the minimum payload needed for Phase 1 trace inspection. Payload handling is event-type allowlist based, not denylist based.

Supported events persist only approved reference and status fields. Unknown event types or schema versions do not persist payload bodies; they store a redacted summary with payload hash, payload size, and top-level key names.

Audit does not store complete financial snapshots, document originals, prompt text, secrets, or model inputs.
