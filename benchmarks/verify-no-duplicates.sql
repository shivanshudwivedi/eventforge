\set ON_ERROR_STOP on

SELECT
    count(*) AS processed_rows,
    count(DISTINCT event_id) AS unique_event_ids,
    count(DISTINCT idempotency_key) AS unique_idempotency_keys
FROM processed_events;

SELECT count(*) AS business_writes
FROM event_records;

DO $$
BEGIN
    IF EXISTS (
        SELECT idempotency_key
        FROM processed_events
        GROUP BY idempotency_key
        HAVING count(*) > 1
    ) THEN
        RAISE EXCEPTION 'duplicate idempotency keys found';
    END IF;

    IF (SELECT count(*) FROM processed_events)
       <> (SELECT count(*) FROM event_records) THEN
        RAISE EXCEPTION 'processed event and business write counts differ';
    END IF;
END
$$;
