package com.ivan.nexus.application.log;

import java.time.Instant;
import java.util.UUID;

public record StoredErrorFingerprint(
        UUID id,
        String projectId,
        String serviceId,
        String fingerprint,
        Instant firstSeen,
        Instant lastSeen,
        long count,
        String sampleMessage) {

    public StoredErrorFingerprint recordHit(Instant at) {
        return new StoredErrorFingerprint(
                id,
                projectId,
                serviceId,
                fingerprint,
                firstSeen,
                at,
                count + 1,
                sampleMessage);
    }
}
