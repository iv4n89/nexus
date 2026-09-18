package com.ivan.nexus.application.traffic;

public interface TrafficIngestor {
    void ingestRaw(String projectId, String domainId, int status, long bytes, long latencyMs);
}
