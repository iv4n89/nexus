package com.ivan.nexus.infrastructure.persistence.traffic;

import com.ivan.nexus.domain.traffic.TrafficEndpointStat;
import com.ivan.nexus.domain.traffic.TrafficHourlyBucket;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(
        name = "traffic_hourly",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_traffic_hourly_bucket",
                columnNames = {"project_id", "domain_id", "bucket_start"}))
public class TrafficHourlyEntity {

    @Id
    private UUID id;

    @Column(name = "project_id", nullable = false, length = 64)
    private String projectId;

    @Column(name = "domain_id", nullable = false, length = 64)
    private String domainId;

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

    @Column(name = "latency_avg", nullable = false)
    private double latencyAvg;

    @Column(name = "latency_p95")
    private Double latencyP95;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "top_endpoints", nullable = false, columnDefinition = "jsonb")
    private List<Map<String, Object>> topEndpoints = new ArrayList<>();

    protected TrafficHourlyEntity() {
    }

    public TrafficHourlyEntity(TrafficHourlyBucket bucket) {
        this.id = bucket.id();
        apply(bucket);
    }

    public void apply(TrafficHourlyBucket bucket) {
        this.projectId = bucket.projectId();
        this.domainId = bucket.domainId();
        this.bucketStart = bucket.bucketStart();
        this.requests = bucket.requests();
        this.bytesIn = bucket.bytesIn();
        this.bytesOut = bucket.bytesOut();
        this.status2xx = bucket.status2xx();
        this.status3xx = bucket.status3xx();
        this.status4xx = bucket.status4xx();
        this.status5xx = bucket.status5xx();
        this.latencyAvg = bucket.latencyAvgMs();
        this.latencyP95 = bucket.latencyP95Ms();
        this.topEndpoints = toMaps(bucket.topEndpoints());
    }

    public TrafficHourlyBucket toDomain() {
        return new TrafficHourlyBucket(
                id,
                projectId,
                domainId,
                bucketStart,
                requests,
                bytesIn,
                bytesOut,
                status2xx,
                status3xx,
                status4xx,
                status5xx,
                latencyAvg,
                latencyP95,
                toStats(topEndpoints));
    }

    public UUID getId() {
        return id;
    }

    private static List<Map<String, Object>> toMaps(List<TrafficEndpointStat> stats) {
        List<Map<String, Object>> maps = new ArrayList<>(stats.size());
        for (TrafficEndpointStat stat : stats) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("path", stat.path());
            row.put("requests", stat.requests());
            row.put("latencyAvgMs", stat.latencyAvgMs());
            maps.add(row);
        }
        return maps;
    }

    private static List<TrafficEndpointStat> toStats(List<Map<String, Object>> maps) {
        if (maps == null || maps.isEmpty()) {
            return List.of();
        }
        List<TrafficEndpointStat> stats = new ArrayList<>(maps.size());
        for (Map<String, Object> row : maps) {
            String path = row.get("path") == null ? "/" : String.valueOf(row.get("path"));
            long req = asLong(row.get("requests"));
            double latency = asDouble(row.get("latencyAvgMs"));
            stats.add(new TrafficEndpointStat(path, req, latency));
        }
        return List.copyOf(stats);
    }

    private static long asLong(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private static double asDouble(Object value) {
        return value instanceof Number number ? number.doubleValue() : 0.0;
    }
}
