CREATE TABLE traffic_minute (
    id UUID PRIMARY KEY,
    project_id VARCHAR(64) NOT NULL,
    service_id VARCHAR(128) NOT NULL,
    host VARCHAR(253) NOT NULL DEFAULT '',
    bucket_start TIMESTAMPTZ NOT NULL,
    requests BIGINT NOT NULL DEFAULT 0,
    bytes_in BIGINT NOT NULL DEFAULT 0,
    bytes_out BIGINT NOT NULL DEFAULT 0,
    status_2xx BIGINT NOT NULL DEFAULT 0,
    status_3xx BIGINT NOT NULL DEFAULT 0,
    status_4xx BIGINT NOT NULL DEFAULT 0,
    status_5xx BIGINT NOT NULL DEFAULT 0,
    latency_avg_ms DOUBLE PRECISION NOT NULL DEFAULT 0,
    latency_max_ms DOUBLE PRECISION,
    CONSTRAINT uq_traffic_minute_bucket UNIQUE (project_id, service_id, host, bucket_start)
);
CREATE INDEX idx_traffic_minute_bucket ON traffic_minute (bucket_start);
CREATE INDEX idx_traffic_minute_project_bucket
    ON traffic_minute (project_id, bucket_start DESC);
