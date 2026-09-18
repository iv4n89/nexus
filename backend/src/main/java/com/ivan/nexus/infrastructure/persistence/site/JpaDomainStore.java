package com.ivan.nexus.infrastructure.persistence.site;

import com.ivan.nexus.application.site.DomainStore;
import com.ivan.nexus.domain.site.SiteDomain;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class JpaDomainStore implements DomainStore {
    private final SiteDomainJpaRepository repository;

    public JpaDomainStore(SiteDomainJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public SiteDomain save(SiteDomain domain) {
        return toDomain(repository.save(toEntity(domain)));
    }

    @Override
    public Optional<SiteDomain> findById(UUID id) {
        return repository.findById(id).map(JpaDomainStore::toDomain);
    }

    @Override
    public List<SiteDomain> findByProjectId(String projectId) {
        return repository.findByProjectIdOrderByCreatedAtDesc(projectId).stream()
                .map(JpaDomainStore::toDomain)
                .toList();
    }

    @Override
    public List<SiteDomain> findAll() {
        return repository.findAll().stream().map(JpaDomainStore::toDomain).toList();
    }

    @Override
    public void delete(UUID id) {
        repository.deleteById(id);
    }

    @Override
    public Optional<SiteDomain> findByHostname(String hostname) {
        return repository.findByHostname(hostname).map(JpaDomainStore::toDomain);
    }

    private static SiteDomainEntity toEntity(SiteDomain domain) {
        return new SiteDomainEntity(
                domain.id(),
                domain.projectId(),
                domain.hostname(),
                domain.serviceName(),
                domain.targetPort(),
                domain.certStatus(),
                domain.createdAt(),
                domain.updatedAt());
    }

    private static SiteDomain toDomain(SiteDomainEntity entity) {
        return new SiteDomain(
                entity.getId(),
                entity.getProjectId(),
                entity.getHostname(),
                entity.getServiceName(),
                entity.getTargetPort(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getCertStatus());
    }
}
