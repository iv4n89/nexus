package com.ivan.nexus.infrastructure.caddy;

import com.ivan.nexus.domain.site.SiteDomain;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Renders Caddyfile site blocks for managed domains (no file I/O).
 */
final class CaddySiteSnippetRenderer {
    private CaddySiteSnippetRenderer() {
    }

    static String render(String projectId, List<SiteDomain> domains) {
        if (domains == null || domains.isEmpty()) {
            return "";
        }
        String header = "# nexus project=" + projectId + "\n";
        String body = domains.stream()
                .sorted(Comparator.comparing(SiteDomain::hostname))
                .map(CaddySiteSnippetRenderer::renderSite)
                .collect(Collectors.joining("\n"));
        return header + body;
    }

    private static String renderSite(SiteDomain domain) {
        return domain.hostname() + " {\n"
                + "\t# service=" + domain.serviceName() + "\n"
                + "\treverse_proxy host.docker.internal:" + domain.targetPort() + "\n"
                + "}\n";
    }
}
