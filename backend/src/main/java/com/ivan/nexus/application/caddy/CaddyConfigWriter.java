package com.ivan.nexus.application.caddy;

import com.ivan.nexus.domain.site.SiteDomain;

import java.util.List;

/**
 * Outbound port that materializes per-project Caddy site snippets.
 */
public interface CaddyConfigWriter {
    /**
     * Writes (or replaces) the Caddy snippet for {@code projectId} from the given domains.
     * An empty list removes the project snippet.
     */
    void writeProjectSites(String projectId, List<SiteDomain> domains);
}
