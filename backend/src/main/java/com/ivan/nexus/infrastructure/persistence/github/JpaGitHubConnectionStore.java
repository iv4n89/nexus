package com.ivan.nexus.infrastructure.persistence.github;

import com.ivan.nexus.application.github.GitHubConnectionStore;
import com.ivan.nexus.application.github.StoredGitHubConnection;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Component
public class JpaGitHubConnectionStore implements GitHubConnectionStore {
    private final GitHubConnectionJpaRepository repository;

    JpaGitHubConnectionStore(GitHubConnectionJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StoredGitHubConnection> load() {
        return repository.findAll().stream().findFirst().map(this::toDomain);
    }

    @Override
    @Transactional
    public void save(StoredGitHubConnection connection) {
        List<GitHubConnectionEntity> existing = repository.findAll();
        if (existing.isEmpty()) {
            repository.save(toEntity(connection));
            return;
        }
        GitHubConnectionEntity entity = existing.getFirst();
        if (!entity.getId().equals(connection.id())) {
            repository.deleteAll();
            repository.save(toEntity(connection));
            return;
        }
        entity.replace(
                connection.githubLogin(),
                connection.encryptedToken(),
                connection.encryptedRefreshToken(),
                connection.scopes(),
                connection.connectedAt(),
                connection.updatedAt());
        repository.save(entity);
        if (existing.size() > 1) {
            existing.stream().skip(1).forEach(repository::delete);
        }
    }

    @Override
    @Transactional
    public void clear() {
        repository.deleteAll();
    }

    private StoredGitHubConnection toDomain(GitHubConnectionEntity entity) {
        return new StoredGitHubConnection(
                entity.getId(),
                entity.getGithubLogin(),
                entity.getEncryptedToken(),
                entity.getEncryptedRefresh(),
                entity.getScopes(),
                entity.getConnectedAt(),
                entity.getUpdatedAt());
    }

    private static GitHubConnectionEntity toEntity(StoredGitHubConnection connection) {
        return new GitHubConnectionEntity(
                connection.id(),
                connection.githubLogin(),
                connection.encryptedToken(),
                connection.encryptedRefreshToken(),
                connection.scopes(),
                connection.connectedAt(),
                connection.updatedAt());
    }
}
