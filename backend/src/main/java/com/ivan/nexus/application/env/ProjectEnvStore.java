package com.ivan.nexus.application.env;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectEnvStore {
    List<StoredProjectEnvVar> listByProject(String projectId);

    Optional<StoredProjectEnvVar> findByProjectAndName(String projectId, String name);

    Optional<StoredProjectEnvVar> findById(UUID id);

    StoredProjectEnvVar upsert(StoredProjectEnvVar envVar);

    void delete(String projectId, String name);
}
