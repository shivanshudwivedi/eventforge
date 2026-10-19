# Operations and failure walkthrough

## Run one order through the system

Start the root README's Compose stack. Then run the following in a second terminal. Python uses only its standard library; no paid service or real customer data is required.

```bash
python3 - <<'PY'
import json
import os
import time
import urllib.error
import urllib.request
import uuid

base = os.environ.get('EVENTFORGE_URL', 'http://localhost:8080').rstrip('/')
headers = {'Content-Type': 'application/json',
           'X-API-Key': os.environ.get('EVENTFORGE_API_KEY', 'local-development-key')}
identity = 'demo-' + str(uuid.uuid4())
body = {'eventType': 'order.created', 'schemaVersion': 1,
        'idempotencyKey': identity,
        'payload': {'aggregateId': identity, 'customerId': 'synthetic-customer', 'amount': 129.99},
        'metadata': {'source': 'documentation-walkthrough'}}
request = urllib.request.Request(base + '/api/v1/events',
    data=json.dumps(body).encode(), headers=headers)
with urllib.request.urlopen(request, timeout=15) as response:
    assert response.status == 202
    accepted = json.load(response)
print('Kafka acknowledged:', accepted['eventId'])
for _ in range(90):
    try:
        request = urllib.request.Request(base + '/api/v1/events/' + accepted['eventId'], headers=headers)
        with urllib.request.urlopen(request, timeout=10) as response:
            event = json.load(response)
        break
    except urllib.error.HTTPError as error:
        if error.code != 404:
            raise
        time.sleep(1)
else:
    raise TimeoutError('No terminal database record; inspect consumer lag and logs')
assert event['status'] == 'PROCESSED', event
assert event['schemaVersion'] == 2, event
assert event['payload']['currency'] == 'USD', event
print(json.dumps(event, indent=2))
PY
```

The v1 order is stored as v2 with a default USD currency. Initial 404 responses are expected while processing is pending; the service stores terminal status only. This walkthrough is a procedure, not a retained end-to-end benchmark result.

## Exercise retries and the dead-letter path

Recreate the application with local failure injection enabled:

```bash
EVENTFORGE_FAILURE_INJECTION=true docker compose up --build -d
```

In the script above, add `injectFailures: '2'` inside `metadata`. The Redis attempt counter should fail the first two deliveries, then permit processing. Use a fresh idempotency key for each logical test. At or above the configured total attempts (default 4), the record should exhaust retries and reach `DEAD_LETTERED`; replace the final success assertions when exercising that case.

Inspect:

```bash
docker compose logs --tail=150 eventforge
docker compose exec -T kafka /opt/kafka/bin/kafka-consumer-groups.sh \
  --bootstrap-server localhost:9092 --all-groups --describe
docker compose exec -T postgres psql -U eventforge -d eventforge -c \
  'SELECT event_id, error, failed_at FROM dead_letter_events ORDER BY failed_at DESC LIMIT 10;'
```

Do not leave failure injection enabled outside a controlled test.

## Incident guide

| Symptom | Inspect | Recovery boundary |
|---|---|---|
| Publish timeout / 503 | API logs, Kafka availability and acknowledgement latency | The broker may have accepted the event. Reuse the original idempotency key on retry. |
| Growing consumer lag | PostgreSQL locks, connection pool, consumer restarts, retry-topic backlog | Fix the dependency before increasing consumer count; concurrency is bounded by partitions. |
| Accepted ID remains 404 | Source/retry lag, DLT, earlier submission with the same identity | A duplicate's newer event ID may never receive a row. Use the original accepted ID. |
| DLT backlog | Kafka exception headers, schema compatibility, injected failures | Preserve original records. Correct the cause and design a reviewed replay; there is no automatic replay API. |
| Duplicate delivery | `processed_events`, `event_records` and identity constraints | Expected after an offset/commit race; verify one business row per identity. |
| Redis outage | Failure injection configuration | Normal processing skips Redis when injection is disabled; injected tests can fail while Redis is unavailable. |

Never truncate identity tables to resolve a production backlog: that removes replay protection. Retention must account for the maximum Kafka replay horizon. Database restores and consumer offset resets need coordinated recovery plans.

## Deployment checklist

The supplied manifests are building blocks. Use immutable image references, managed durable PostgreSQL/Kafka, TLS/SASL/ACLs, rotated secrets, ingress request-size limits, backups with restore drills, and network restrictions for actuator endpoints. Configure a Kafka replication factor and minimum in-sync replicas appropriate to the deployment; the local single-broker configuration cannot survive broker loss.

The API uses one shared key and global event identities. Tenant isolation, RBAC, sensitive-payload redaction, automatic retention and operator approval workflows are not implemented. Avoid sending sensitive production payloads to an unqualified deployment.

## Local verification

```bash
./mvnw -B -ntp verify
docker compose config --quiet
```

Java 21 and Docker are required. `verify` runs schema unit tests and PostgreSQL Testcontainers tests; it does not boot a complete Kafka application stack. Review [architecture and guarantees](architecture.md) and [benchmarking](benchmarking.md) for the remaining qualification steps.
