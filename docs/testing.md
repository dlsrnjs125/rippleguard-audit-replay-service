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

Run:

```bash
./mvnw test
./mvnw package
docker build -t rippleguard-audit-replay-service:local .
```
