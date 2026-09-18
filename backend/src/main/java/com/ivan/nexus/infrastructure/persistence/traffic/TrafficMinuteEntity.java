package com.ivan.nexus.infrastructure.persistence.traffic;

import com.ivan.nexus.domain.traffic.TrafficMinuteBucket;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "traffic_minute",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_traffic_minute_bucket",
                columnNames = {"project_id", "service_id", "host", "bucket_start"}))
public class TrafficMinuteEntity {

    @Id
    private UUID id;

    @Column(name = "project_id", nullable = false, length = 64)
    private String projectId;

    @Column(name = "service_id", nullable = false, length = 128)
    private String serviceId;

    @Column(nullable = false, length = 253)
    private String host;

    @Column(name = "bucket_start", nullable = false)
    private Instant bucketStart;

    @Column(nullable = false)
    private long requests;

    @Column(name = "bytes_in", nullable = false)
    private long bytesIn;

    @Column(name = "bytes_out", nullable = false)
    private long bytesOut;

    @Column(name = "status_2xx", nullable = false)
    private long status2xx;

    @Column(name = "status_3xx", nullable = false)
    private long status3xx;

    @Column(name = "status_4xx", nullable = false)
    private long status4xx;

    @Column(name = "status_5xx", nullable = false)
    private long status5xx;

    @Column(name = "latency_avg_ms", nullable = false)
    private double latencyAvgMs;

    @Column(name = "latency_max_ms")
    private Double latencyMaxMs;

    protected TrafficMinuteEntity() {
    }

    public TrafficMinuteEntity(TrafficMinuteBucket bucket) {
        this.id = bucket.id();
        apply(bucket);
    }

    public void apply(TrafficMinuteBucket bucket) {
        this.projectId = bucket.projectId();
        this.serviceId = bucket.serviceId();
        this.host = bucket.host();
        this.bucketStart = bucket.bucketStart();
        this.requests = bucket.requests();
        this.bytesIn = bucket.bytesIn();
        this.bytesOut = bucket.bytesOut();
        this.status2xx = bucket.status2xx();
        this.status3xx = bucket.status3xx();
        this.status4xx = bucket.status4xx();
        this.status5xx = bucket.status5xx();
        this.latencyAvgMs = bucket.latencyAvgMs();
        this.latencyMaxMs = bucket.latencyMaxMs();
    }

    public TrafficMinuteBucket toDomain() {
        return new TrafficMinuteBucket(
                id,
                projectId,
                serviceId,
                host,
                bucketStart,
                requests,
                bytesIn,
                bytesOut,
                status2xx,
                status3xx,
                status4xx,
                status5xx,
                latencyAvgMs,
                latencyMaxMs);
    }

    public UUID getId() {
        return id;
    }
}
