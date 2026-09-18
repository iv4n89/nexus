package com.ivan.nexus.application.env;

import com.ivan.nexus.application.secrets.SecretStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ListProjectEnv {
    private final ProjectEnvStore store;
    private final SecretStore secretStore;

    public ListProjectEnv(ProjectEnvStore store, SecretStore secretStore) {
        this.store = store;
        this.secretStore = secretStore;
    }

    @Transactional(readOnly = true)
    public List<ProjectEnvVarView> execute(String projectId) {
        return store.listByProject(projectId).stream()
                .map(this::toMaskedView)
                .toList();
    }

    private ProjectEnvVarView toMaskedView(StoredProjectEnvVar stored) {
        String value = stored.secret() ? null : secretStore.decrypt(stored.encryptedValue());
        return new ProjectEnvVarView(
                stored.id(),
                stored.projectId(),
                stored.name(),
                stored.secret(),
                value,
                stored.createdAt(),
                stored.updatedAt());
    }
}
