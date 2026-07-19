# Privacy

Audit stores the minimum payload needed for Phase 1 trace inspection.

The sanitizer redacts payload fields whose names indicate:

- financial snapshots
- raw documents
- prompt content
- secrets, tokens, passwords
- SSN-like identifiers

Audit does not store complete financial snapshots, document originals, prompt text, secrets, or model inputs.
