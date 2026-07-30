# Reproducible benchmark protocol

The repository includes a workload capable of requesting 20,000 events/second.
Capacity depends on CPU, network, storage latency, Kafka broker topology,
partition count, PostgreSQL tuning, and event size, so the target must never be
presented as a measured result without a completed run.

## Environment record

Capture the following before each published result:

- commit SHA and container image digest
- host/cluster CPU model, cores, memory, operating system, and region
- number and size of API/consumer pods
- Kafka broker count, storage class, replication factor, and partitions
- PostgreSQL version, instance class, IOPS, and connection pool size
- Redis version and topology
- k6 host placement and network path
- event size, batch size, target rate, and test duration

## Procedure

1. Start from empty Kafka topics and database tables.
2. Warm up for two minutes at 25% of the target rate.
3. Run for at least ten minutes at the target rate.
4. Inject rolling consumer restarts during the middle third of the run.
5. Record k6 achieved iterations, dropped iterations, errors, and p50/p95/p99.
6. Record Kafka produce latency and consumer lag.
7. Wait for lag to return to zero.
8. Run `make verify-benchmark`.
9. Compare accepted event count, processed count, and business write count.
10. Archive metrics, configuration, SQL output, and the commit SHA together.

## Pass criteria

- HTTP error rate below the declared SLO.
- No sustained k6 dropped iterations.
- Kafka lag returns to zero after load stops.
- `processed_rows = unique_event_ids = unique_idempotency_keys`.
- `processed_rows = business_writes`.
- No unexplained dead-letter records.
- Recovery from worker termination produces no duplicate business writes.

If any criterion fails, publish the run as a failed or partial result rather
than quoting the configured arrival rate as achieved throughput.
