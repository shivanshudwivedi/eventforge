# Contributing

1. Create a focused branch from `main`.
2. Run `./mvnw test` while developing.
3. Run `./mvnw verify` before opening a pull request; integration tests require
   Docker.
4. Keep database changes forward-only in a new Flyway migration.
5. Add tests for schema, retry, deduplication, or persistence behavior changes.
6. Do not weaken idempotency constraints or acknowledge Kafka records before
   durable side effects complete.

Use conventional, imperative commit subjects and explain operational tradeoffs
in the pull request description.
