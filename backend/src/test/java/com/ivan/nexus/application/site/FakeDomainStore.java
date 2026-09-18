package com.ivan.nexus.application.site;

import com.ivan.nexus.domain.site.SiteDomain;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory {@link DomainStore} for Caddy adapter unit tests. */
public final class FakeDomainStore implements DomainStore {
    private final Map<UUID, SiteDomain> byId = new ConcurrentHashMap<>();

    @Override
    public SiteDomain save(SiteDomain domain) {
        byId.put(domain.id(), domain);
        return domain;
    }

    @Override
    public Optional<SiteDomain> findById(UUID id) {
        return Optional.ofNullable(byId.get(id));
    }

    @Override
    public List<SiteDomain> findByProjectId(String projectId) {
        List<SiteDomain> result = new ArrayList<>();
        for (SiteDomain domain : byId.values()) {
            if (domain.projectId().equals(projectId)) {
                result.add(domain);
            }
        }
        return result;
    }

    @Override
    public void delete(UUID id) {
        byId.remove(id);
    }

    @Override
    public Optional<SiteDomain> findByHostname(String hostname) {
        return byId.values().stream()
                .filter(d -> d.hostname().equals(hostname))
                .findFirst();
    }
}
