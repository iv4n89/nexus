package com.ivan.nexus.infrastructure.caddy;

import com.ivan.nexus.application.caddy.CaddyConfigWriter;
import com.ivan.nexus.domain.site.SiteDomain;
import com.ivan.nexus.infrastructure.config.NexusProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Component
@ConditionalOnProperty(name = "nexus.caddy.enabled", havingValue = "true")
public class FileCaddyConfigWriter implements CaddyConfigWriter {
    private static final Logger log = LoggerFactory.getLogger(FileCaddyConfigWriter.class);
    private static final Pattern UNSAFE = Pattern.compile("[^a-zA-Z0-9._-]+");

    private final Path sitesPath;

    public FileCaddyConfigWriter(NexusProperties properties) {
        this.sitesPath = Path.of(properties.getCaddy().getSitesPath());
    }

    FileCaddyConfigWriter(Path sitesPath) {
        this.sitesPath = sitesPath;
    }

    @Override
    public void writeProjectSites(String projectId, List<SiteDomain> domains) {
        Path file = sitesPath.resolve(safeFileName(projectId));
        try {
            Files.createDirectories(sitesPath);
            String content = CaddySiteSnippetRenderer.render(projectId, domains);
            if (content.isBlank()) {
                Files.deleteIfExists(file);
                log.info("Removed empty Caddy snippet for project {}", projectId);
                return;
            }
            Files.writeString(file, content, StandardCharsets.UTF_8);
            log.info("Wrote Caddy snippet for project {} ({} domain(s)) to {}",
                    projectId, domains.size(), file);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write Caddy snippet for project " + projectId, e);
        }
    }

    static String safeFileName(String projectId) {
        String safe = UNSAFE.matcher(projectId == null ? "" : projectId).replaceAll("_");
        if (safe.isBlank()) {
            safe = "unknown";
        }
        return safe.toLowerCase(Locale.ROOT) + ".caddy";
    }
}
