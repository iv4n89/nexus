package com.ivan.nexus.domain.traffic;

public record HttpAccessEvent(
        String host,
        int status,
        long bytesOut,
        long latencyMs) {
}
