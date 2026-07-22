# Testing

Coverage includes:

- event storage
- duplicate event handling
- out-of-order delivery
- causation chain linking
- missing predecessor warnings
- timeline sorting
- sanitization
- API lookup
- contract example deserialization
- PostgreSQL Flyway migration validation
- Phase 2 Governance validation event contract fixtures
- Agent Run and Attempt projection
- invalid producer quarantine
- broken causation quarantine
- same event id conflicting payload quarantine
- Agent Run API lookup

Run:

```bash
./mvnw test
./mvnw package
docker build -t rippleguard-audit-replay-service:local .
```
