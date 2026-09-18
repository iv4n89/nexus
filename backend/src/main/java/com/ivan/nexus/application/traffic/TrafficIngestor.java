package com.ivan.nexus.application.traffic;

public interface TrafficIngestor {
    void ingestRaw(String projectId, String serviceId, String host, int status, long bytes, long latencyMs);
}
