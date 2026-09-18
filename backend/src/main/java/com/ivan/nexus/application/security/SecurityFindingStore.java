package com.ivan.nexus.application.security;

import com.ivan.nexus.domain.security.SecurityFinding;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SecurityFindingStore {
    SecurityFinding upsertByFingerprint(SecurityFinding finding);

    List<SecurityFinding> listByProject(String projectId);

    Optional<SecurityFinding> findById(UUID id);

    Optional<SecurityFinding> acknowledge(UUID id, Instant at);
}
