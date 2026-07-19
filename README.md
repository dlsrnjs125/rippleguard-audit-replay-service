# RippleGuard Audit Replay Service

Phase 1 Audit Replay Service stores sanitized Phase 1 events and exposes a minimal case timeline.

## Run

```bash
./mvnw test
./mvnw package
docker build -t rippleguard-audit-replay-service:local .
```

## API

`GET /api/v1/cases/{caseId}/timeline`

## Baselines

- Contracts: `29f6c348fd93633476438ee36b3f93a3d036e165`
- Loan Service: `54ea344a682723d61d9beedf4ade56ee48029c0d`
- Governance Service: `29bafba0c7d9909474564204823d2616593e0223`

Detailed notes are in `docs/`.
