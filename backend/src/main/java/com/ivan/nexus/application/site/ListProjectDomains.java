package com.ivan.nexus.application.site;

import com.ivan.nexus.domain.site.SiteDomain;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ListProjectDomains {
    private final DomainStore domains;

    public ListProjectDomains(DomainStore domains) {
        this.domains = domains;
    }

    @Transactional(readOnly = true)
    public List<SiteDomain> execute(String projectId) {
        return domains.findByProjectId(projectId);
    }
}
