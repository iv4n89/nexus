CREATE TABLE traffic_hourly (
    id UUID PRIMARY KEY,
    project_id VARCHAR(64) NOT NULL,
    domain_id VARCHAR(64) NOT NULL DEFAULT '',
    bucket_start TIMESTAMPTZ NOT NULL,
    requests BIGINT NOT NULL DEFAULT 0,
    bytes_in BIGINT NOT NULL DEFAULT 0,
    bytes_out BIGINT NOT NULL DEFAULT 0,
    status_2xx BIGINT NOT NULL DEFAULT 0,
    status_3xx BIGINT NOT NULL DEFAULT 0,
    status_4xx BIGINT NOT NULL DEFAULT 0,
    status_5xx BIGINT NOT NULL DEFAULT 0,
    latency_avg DOUBLE PRECISION NOT NULL DEFAULT 0,
    latency_p95 DOUBLE PRECISION,
    CONSTRAINT uq_traffic_hourly_bucket UNIQUE (project_id, domain_id, bucket_start)
);
CREATE INDEX idx_traffic_hourly_project_bucket
    ON traffic_hourly (project_id, bucket_start DESC);
