package com.ivan.nexus.application.site;

import com.ivan.nexus.application.caddy.ReloadProjectDomains;
import com.ivan.nexus.application.env.ProjectDotEnvStore;
import com.ivan.nexus.application.manifest.LoadedManifest;
import com.ivan.nexus.application.manifest.ManifestCatalog;
import com.ivan.nexus.application.project.ContainerInspect;
import com.ivan.nexus.application.project.ContainerInventory;
import com.ivan.nexus.application.project.EnsureManagedProject;
import com.ivan.nexus.application.project.PublishedPort;
import com.ivan.nexus.domain.container.ContainerSnapshot;
import com.ivan.nexus.domain.project.ProjectGrouping;
import com.ivan.nexus.domain.site.CertStatus;
import com.ivan.nexus.domain.site.DomainHostExtractor;
import com.ivan.nexus.domain.site.SiteDomain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class SyncProjectDomainsFromEnv {
    private static final Logger log = LoggerFactory.getLogger(SyncProjectDomainsFromEnv.class);

    private final ProjectDotEnvStore dotEnvStore;
    private final DomainStore domains;
    private final ContainerInventory inventory;
    private final ManifestCatalog manifests;
    private final EnsureManagedProject ensureManagedProject;
    private final Optional<ReloadProjectDomains> reloadProjectDomains;

    public SyncProjectDomainsFromEnv(
            ProjectDotEnvStore dotEnvStore,
            DomainStore domains,
            ContainerInventory inventory,
            ManifestCatalog manifests,
            EnsureManagedProject ensureManagedProject,
            Optional<ReloadProjectDomains> reloadProjectDomains) {
        this.dotEnvStore = dotEnvStore;
        this.domains = domains;
        this.inventory = inventory;
        this.manifests = manifests;
        this.ensureManagedProject = ensureManagedProject;
        this.reloadProjectDomains = reloadProjectDomains;
    }

    @Transactional
    public List<SiteDomain> execute(String projectId) {
        ensureManagedProject.execute(projectId);
        String directoryName = directoryName(projectId);
        Map<String, String> env = dotEnvStore.read(projectId);
        Set<String> fromEnv = DomainHostExtractor.fromEnv(env);
        LinkedHashSet<String> fromCaddy = new LinkedHashSet<>();
        for (String snippet : dotEnvStore.caddySnippets(projectId)) {
            fromCaddy.addAll(DomainHostExtractor.fromCaddy(snippet));
        }

        LinkedHashSet<String> fromLabels = new LinkedHashSet<>();
        String defaultService = "app";
        int defaultPort = 80;
        for (ContainerSnapshot snapshot : inventory.listAll()) {
            if (!ProjectGrouping.belongsTo(snapshot.name(), snapshot.labels(), projectId, directoryName)) {
                continue;
            }
            fromLabels.addAll(DomainHostExtractor.fromLabels(snapshot.labels()));
            Optional<ContainerInspect> inspect;
            try {
                inspect = inventory.inspect(snapshot.id());
            } catch (RuntimeException ex) {
                log.debug("inspect failed for {}: {}", snapshot.id(), ex.toString());
                continue;
            }
            if (inspect.isEmpty()) {
                continue;
            }
            WebHint hint = webHint(inspect.get());
            if (hint != null) {
                defaultService = hint.service();
                defaultPort = hint.port();
            }
        }

        Map<String, String> candidates = DomainHostExtractor.mergePreferFirst(fromEnv, fromCaddy, fromLabels);
        List<SiteDomain> added = new ArrayList<>();
        Instant now = Instant.now();
        for (String hostname : candidates.keySet()) {
            if (domains.findByHostname(hostname).isPresent()) {
                continue;
            }
            SiteDomain created = domains.save(new SiteDomain(
                    UUID.randomUUID(),
                    projectId,
                    hostname,
                    defaultService,
                    defaultPort,
                    now,
                    now,
                    CertStatus.PENDING));
            added.add(created);
        }
        if (!added.isEmpty()) {
            reloadProjectDomains.ifPresent(reload -> reload.execute(projectId));
        }
        return domains.findByProjectId(projectId);
    }

    private String directoryName(String projectId) {
        try {
            LoadedManifest loaded = manifests.loadRequired(projectId);
            Path working = Path.of(loaded.manifest().project().workingDirectory()).getFileName();
            return working == null ? null : working.toString();
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static WebHint webHint(ContainerInspect inspect) {
        String service = ProjectGrouping.serviceId(inspect.name(), inspect.labels());
        Integer httpPort = null;
        for (PublishedPort port : inspect.publishedPorts()) {
            if (port.privatePort() == 80 || port.privatePort() == 443 || port.privatePort() == 8080
                    || port.privatePort() == 3000) {
                httpPort = port.privatePort() == 443 ? 443 : port.privatePort();
                break;
            }
        }
        if (httpPort == null && inspect.labels() != null) {
            for (String key : inspect.labels().keySet()) {
                if (key != null && key.toLowerCase(Locale.ROOT).contains("caddy")) {
                    httpPort = 80;
                    break;
                }
            }
        }
        if (httpPort == null) {
            return null;
        }
        String normalizedService = service == null || service.isBlank() ? "app" : service;
        if (looksLikeDatabase(normalizedService, inspect.image())) {
            return null;
        }
        return new WebHint(normalizedService, httpPort);
    }

    private static boolean looksLikeDatabase(String service, String image) {
        String hay = ((service == null ? "" : service) + " " + (image == null ? "" : image))
                .toLowerCase(Locale.ROOT);
        return hay.contains("postgres")
                || hay.contains("mysql")
                || hay.contains("mariadb")
                || hay.contains("mongo")
                || hay.contains("redis");
    }

    private record WebHint(String service, int port) {}
}
