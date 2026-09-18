package com.ivan.nexus.application.site;

import com.ivan.nexus.domain.site.SiteDomain;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ListProjectDomains {
    private final SyncProjectDomainsFromEnv syncProjectDomainsFromEnv;

    public ListProjectDomains(SyncProjectDomainsFromEnv syncProjectDomainsFromEnv) {
        this.syncProjectDomainsFromEnv = syncProjectDomainsFromEnv;
    }

    @Transactional
    public List<SiteDomain> execute(String projectId) {
        return syncProjectDomainsFromEnv.execute(projectId);
    }
}
