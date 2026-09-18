package com.ivan.nexus.application.backup;

import java.util.List;

public record VolumeBackupResult(
        String artifactUri,
        long sizeBytes,
        List<String> volumes,
        String summary) {
}
