package com.ivan.nexus.application.site;

import com.ivan.nexus.domain.site.SiteDomain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DomainStore {
    SiteDomain save(SiteDomain domain);

    Optional<SiteDomain> findById(UUID id);

    List<SiteDomain> findByProjectId(String projectId);

    void delete(UUID id);

    Optional<SiteDomain> findByHostname(String hostname);
}
