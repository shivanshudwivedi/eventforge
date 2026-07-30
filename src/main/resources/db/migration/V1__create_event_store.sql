CREATE TABLE processed_events (
    event_id UUID PRIMARY KEY,
    idempotency_key VARCHAR(255) NOT NULL UNIQUE,
    event_type VARCHAR(120) NOT NULL,
    schema_version INTEGER NOT NULL CHECK (schema_version > 0),
    topic VARCHAR(255) NOT NULL,
    partition_id INTEGER NOT NULL,
    kafka_offset BIGINT NOT NULL,
    trace_id VARCHAR(64) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_processed_kafka_position UNIQUE (topic, partition_id, kafka_offset)
);

CREATE TABLE event_records (
    event_id UUID PRIMARY KEY REFERENCES processed_events(event_id) ON DELETE RESTRICT,
    aggregate_id VARCHAR(255) NOT NULL,
    event_type VARCHAR(120) NOT NULL,
    schema_version INTEGER NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    payload JSONB NOT NULL,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_event_records_aggregate_id ON event_records (aggregate_id, occurred_at DESC);
CREATE INDEX idx_event_records_type ON event_records (event_type, occurred_at DESC);
CREATE INDEX idx_event_records_payload ON event_records USING GIN (payload);

CREATE TABLE dead_letter_events (
    event_id UUID PRIMARY KEY,
    idempotency_key VARCHAR(255) NOT NULL,
    event_type VARCHAR(120) NOT NULL,
    schema_version INTEGER NOT NULL,
    source_topic VARCHAR(255) NOT NULL,
    payload JSONB NOT NULL,
    error TEXT NOT NULL,
    failed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_dead_letters_failed_at ON dead_letter_events (failed_at DESC);
