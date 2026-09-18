package com.ivan.nexus.infrastructure.caddy;

import com.ivan.nexus.domain.site.CertStatus;
import com.ivan.nexus.domain.site.SiteDomain;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CaddySiteSnippetRendererTest {

    @Test
    void rendersSortedSiteBlocksWithReverseProxy() {
        Instant now = Instant.parse("2026-09-18T10:00:00Z");
        List<SiteDomain> domains = List.of(
                domain("pantry", "app.example.com", "frontend", 3000, now),
                domain("pantry", "api.example.com", "backend", 8080, now));

        String snippet = CaddySiteSnippetRenderer.render("pantry", domains);

        assertThat(snippet).isEqualTo("""
                # nexus project=pantry
                api.example.com {
                \t# service=backend
                \treverse_proxy host.docker.internal:8080
                }

                app.example.com {
                \t# service=frontend
                \treverse_proxy host.docker.internal:3000
                }
                """);
    }

    @Test
    void emptyDomainsRenderBlank() {
        assertThat(CaddySiteSnippetRenderer.render("pantry", List.of())).isEmpty();
    }

    @Test
    void fileWriterWritesAndDeletesSnippet(@TempDir Path tempDir) throws Exception {
        FileCaddyConfigWriter writer = new FileCaddyConfigWriter(tempDir);
        Instant now = Instant.parse("2026-09-18T10:00:00Z");
        SiteDomain domain = domain("lab", "lab.example.com", "web", 3000, now);

        writer.writeProjectSites("lab", List.of(domain));

        Path file = tempDir.resolve("lab.caddy");
        assertThat(Files.readString(file)).contains("lab.example.com {")
                .contains("reverse_proxy host.docker.internal:3000");

        writer.writeProjectSites("lab", List.of());
        assertThat(file).doesNotExist();
    }

    @Test
    void safeFileNameSanitizesProjectId() {
        assertThat(FileCaddyConfigWriter.safeFileName("My Project!")).isEqualTo("my_project_.caddy");
        assertThat(FileCaddyConfigWriter.safeFileName("")).isEqualTo("unknown.caddy");
    }

    private static SiteDomain domain(
            String projectId,
            String hostname,
            String service,
            int port,
            Instant now) {
        return new SiteDomain(
                UUID.randomUUID(),
                projectId,
                hostname,
                service,
                port,
                now,
                now,
                CertStatus.PENDING);
    }
}
