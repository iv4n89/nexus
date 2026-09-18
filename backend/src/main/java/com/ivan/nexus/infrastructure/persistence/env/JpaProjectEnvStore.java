package com.ivan.nexus.infrastructure.persistence.env;

import com.ivan.nexus.application.env.ProjectEnvStore;
import com.ivan.nexus.application.env.StoredProjectEnvVar;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class JpaProjectEnvStore implements ProjectEnvStore {
    private final ProjectEnvVarJpaRepository repository;

    public JpaProjectEnvStore(ProjectEnvVarJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<StoredProjectEnvVar> listByProject(String projectId) {
        return repository.findByProjectIdOrderByNameAsc(projectId).stream()
                .map(JpaProjectEnvStore::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StoredProjectEnvVar> findByProjectAndName(String projectId, String name) {
        return repository.findByProjectIdAndName(projectId, name).map(JpaProjectEnvStore::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StoredProjectEnvVar> findById(UUID id) {
        return repository.findById(id).map(JpaProjectEnvStore::toDomain);
    }

    @Override
    @Transactional
    public StoredProjectEnvVar upsert(StoredProjectEnvVar envVar) {
        Optional<ProjectEnvVarEntity> existing =
                repository.findByProjectIdAndName(envVar.projectId(), envVar.name());
        if (existing.isPresent()) {
            ProjectEnvVarEntity entity = existing.get();
            entity.replace(envVar.encryptedValue(), envVar.secret(), envVar.updatedAt());
            return toDomain(repository.save(entity));
        }
        return toDomain(repository.save(new ProjectEnvVarEntity(
                envVar.id(),
                envVar.projectId(),
                envVar.name(),
                envVar.encryptedValue(),
                envVar.secret(),
                envVar.createdAt(),
                envVar.updatedAt())));
    }

    @Override
    @Transactional
    public void delete(String projectId, String name) {
        repository.deleteByProjectIdAndName(projectId, name);
    }

    private static StoredProjectEnvVar toDomain(ProjectEnvVarEntity entity) {
        return new StoredProjectEnvVar(
                entity.getId(),
                entity.getProjectId(),
                entity.getName(),
                entity.getEncryptedValue(),
                entity.isSecret(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
