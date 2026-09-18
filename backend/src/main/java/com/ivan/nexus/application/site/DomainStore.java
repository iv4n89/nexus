package com.ivan.nexus.application.site;

import com.ivan.nexus.domain.site.SiteDomain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DomainStore {
    SiteDomain save(SiteDomain domain);

    Optional<SiteDomain> findById(UUID id);

    List<SiteDomain> findByProjectId(String projectId);

    List<SiteDomain> findAll();

    void delete(UUID id);

    Optional<SiteDomain> findByHostname(String hostname);
}
