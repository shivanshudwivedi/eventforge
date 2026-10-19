# Architecture and delivery semantics

## The transaction boundary

EventForge accepts an HTTP event only after Kafka acknowledges publication. A consumer normalizes its schema, optionally exercises the development failure injector, and calls a Spring-managed transactional repository.

[`ProcessedEventRepository.persist`](../src/main/java/io/eventforge/processing/ProcessedEventRepository.java) executes two writes in one PostgreSQL transaction:

1. Insert the identity claim into `processed_events` using `ON CONFLICT DO NOTHING`.
2. If the claim was inserted, insert the business payload into `event_records`.

The [Flyway migration](../src/main/resources/db/migration/V1__create_event_store.sql) defines three identities: primary key `event_id`, unique `idempotency_key`, and unique `(topic, partition_id, kafka_offset)`. A conflict on any identity suppresses the business write. A failure in the second insert rolls back the first. The caller must use the Spring proxy for `@Transactional` to apply; constructing this repository directly does not create a transaction.

```mermaid
sequenceDiagram
    participant C as Client
    participant A as HTTP API
    participant K as Kafka
    participant W as Listener
    participant P as PostgreSQL
    C->>A: POST event + stable idempotency key
    A->>K: Publish envelope
    K-->>A: Broker acknowledgement
    A-->>C: 202 + event ID
    K->>W: Deliver record
    W->>W: Validate/upcast; optional fault injection
    W->>P: BEGIN; claim identity; write business record
    P-->>W: COMMIT
    W-->>K: Listener returns; record offset can advance
    Note over K,P: Crash here can redeliver; identity conflict suppresses repeat write
```

This is at-least-once Kafka delivery with idempotent PostgreSQL side effects. It does not provide a transaction spanning Kafka, PostgreSQL and an arbitrary external service.

## Read the implementation

| Concern | Source |
|---|---|
| HTTP validation and acceptance | [`EventController`](../src/main/java/io/eventforge/event/EventController.java), [`EventFactory`](../src/main/java/io/eventforge/event/EventFactory.java) |
| Producer acknowledgement and timeout | [`EventPublisher`](../src/main/java/io/eventforge/event/EventPublisher.java) |
| Version support and v1 → v2 conversion | [`EventSchemaRegistry`](../src/main/java/io/eventforge/event/EventSchemaRegistry.java) |
| Retry topics and dead-letter callback | [`EventConsumer`](../src/main/java/io/eventforge/processing/EventConsumer.java) |
| Processing order and counters | [`EventProcessor`](../src/main/java/io/eventforge/processing/EventProcessor.java) |
| Database identity and transaction | [`ProcessedEventRepository`](../src/main/java/io/eventforge/processing/ProcessedEventRepository.java) |
| Terminal status lookup | [`EventQueryService`](../src/main/java/io/eventforge/event/EventQueryService.java) |
| Local fault injection | [`FailureInjector`](../src/main/java/io/eventforge/processing/FailureInjector.java) |

## Important contract details

- **Acceptance is asynchronous.** An immediate GET can return 404 while the record is waiting. There is no persisted HTTP-side `PENDING` record.
- **Idempotency keys are global.** This service has one configured API credential, no tenant namespace and no RBAC. Unrelated producers must coordinate their key space.
- **Duplicate submissions get new event IDs.** The first database claim owns the business row. A later accepted ID suppressed as a duplicate may never have a status row. Preserve the original accepted ID; this is not a same-ID idempotent HTTP API.
- **Conflicting payloads are not compared.** Reusing an identity with different content suppresses the later write. Clients must use the key only for the same logical event.
- **Batch publication is not atomic.** Some sends can succeed before another fails. A timeout is ambiguous; retry with the same logical idempotency keys rather than inventing new identities.
- **Retries can reorder events.** Retry topics avoid blocking a source partition, but strict per-key ordering is not guaranteed through failures.
- **PostgreSQL failure rolls back the claim.** An external side effect inside the transaction would need its own idempotency design.
- **DLT handling has two steps.** The DLT record and the PostgreSQL dead-letter row do not share a transaction. The stored error is a summary; original exception details live in Kafka headers. There is no automatic operator replay endpoint.

## Verification scope

[`ProcessedEventRepositoryIT`](../src/test/java/io/eventforge/processing/ProcessedEventRepositoryIT.java) runs against PostgreSQL 17 through Testcontainers and a Spring transaction proxy. It checks each of the three identity constraints and verifies rollback when the business insert fails, followed by a corrected replay. Unit tests cover schema behavior.

These tests do not establish broker restart recovery, concurrent load capacity, DLT end-to-end handling, cross-region durability, or a production SLO. The [benchmark protocol](benchmarking.md) specifies the additional evidence needed before making a capacity claim. The k6 target of 20,000 events/second is configuration, not a measured result.
