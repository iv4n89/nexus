package com.ivan.nexus.infrastructure.traffic;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.model.Container;
import com.ivan.nexus.application.log.LogProvider;
import com.ivan.nexus.application.traffic.ResolveTrafficTarget;
import com.ivan.nexus.application.traffic.TrafficIngestor;
import com.ivan.nexus.domain.shared.DomainException;
import com.ivan.nexus.domain.shared.NexusErrorCode;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

@Component
@ConditionalOnProperty(name = "nexus.traffic.ingest-enabled", havingValue = "true", matchIfMissing = true)
public class CaddyAccessLogFollower {
    private static final Logger log = LoggerFactory.getLogger(CaddyAccessLogFollower.class);
    private static final String THREAD_NAME = "nexus-caddy-traffic";
    private static final String COMPOSE_SERVICE_LABEL = "com.docker.compose.service";
    private static final Pattern COMPOSE_CADDY_NAME = Pattern.compile(".*[-_]caddy[-_]1");
    private static final long BACKOFF_MIN_MS = 1_000L;
    private static final long BACKOFF_MAX_MS = 30_000L;

    private final DockerClient dockerClient;
    private final LogProvider logProvider;
    private final CaddyJsonAccessLogParser parser;
    private final ResolveTrafficTarget resolve;
    private final TrafficIngestor ingestor;
    private final String configuredContainer;

    private final AtomicReference<AutoCloseable> currentFollow = new AtomicReference<>();
    private final AtomicBoolean running = new AtomicBoolean();
    private volatile Thread worker;

    public CaddyAccessLogFollower(
            DockerClient dockerClient,
            LogProvider logProvider,
            CaddyJsonAccessLogParser parser,
            ResolveTrafficTarget resolve,
            TrafficIngestor ingestor,
            @Value("${nexus.traffic.caddy-container:}") String configuredContainer) {
        this.dockerClient = dockerClient;
        this.logProvider = logProvider;
        this.parser = parser;
        this.resolve = resolve;
        this.ingestor = ingestor;
        this.configuredContainer = configuredContainer == null ? "" : configuredContainer;
    }

    @PostConstruct
    void start() {
        running.set(true);
        worker = new Thread(this::runLoop, THREAD_NAME);
        worker.setDaemon(true);
        worker.start();
    }

    @PreDestroy
    void stop() {
        running.set(false);
        closeFollow();
        Thread thread = worker;
        if (thread != null) {
            thread.interrupt();
            try {
                thread.join(TimeUnit.SECONDS.toMillis(5));
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }
    }

    void handleLine(String line) {
        if (!running.get()) {
            return;
        }
        try {
            parser.parse(line).ifPresent(event -> {
                ResolveTrafficTarget.ResolvedTarget target = resolve.execute(event.host());
                ingestor.ingestRaw(
                        target.projectId(),
                        target.serviceId(),
                        target.host(),
                        event.status(),
                        event.bytesOut(),
                        event.latencyMs());
            });
        } catch (RuntimeException ex) {
            log.warn("Failed to ingest Caddy access log line", ex);
        }
    }

    Optional<Container> selectContainer(List<Container> running) {
        return selectContainer(running, configuredContainer);
    }

    static Optional<Container> selectContainer(List<Container> running, String configuredIdOrName) {
        if (running == null || running.isEmpty()) {
            return Optional.empty();
        }
        if (configuredIdOrName != null && !configuredIdOrName.isBlank()) {
            String configured = stripLeadingSlash(configuredIdOrName.trim());
            return running.stream()
                    .filter(container -> matchesConfigured(container, configured))
                    .findFirst();
        }
        Optional<Container> labeled = running.stream()
                .filter(CaddyAccessLogFollower::hasCaddyComposeLabel)
                .findFirst();
        if (labeled.isPresent()) {
            return labeled;
        }
        return running.stream()
                .filter(CaddyAccessLogFollower::matchesCaddyName)
                .findFirst();
    }

    private void runLoop() {
        long backoffMs = BACKOFF_MIN_MS;
        while (running.get() && !Thread.currentThread().isInterrupted()) {
            try {
                Optional<String> containerId = resolveContainerId();
                if (!running.get()) {
                    break;
                }
                if (containerId.isEmpty()) {
                    log.warn("Caddy container not found; retrying in {} ms", backoffMs);
                    sleep(backoffMs);
                    backoffMs = nextBackoff(backoffMs);
                    continue;
                }
                int since = (int) Instant.now().getEpochSecond();
                CountDownLatch completed = new CountDownLatch(1);
                try {
                    AutoCloseable handle = logProvider.follow(
                            containerId.get(),
                            0,
                            since,
                            this::handleLine,
                            completed::countDown);
                    currentFollow.set(handle);
                    if (!running.get()) {
                        closeFollow();
                        break;
                    }
                    backoffMs = BACKOFF_MIN_MS;
                    completed.await();
                } catch (DomainException ex) {
                    if (ex.getCode() != NexusErrorCode.CONTAINER_NOT_FOUND) {
                        throw ex;
                    }
                    log.warn("Caddy container {} not found; retrying in {} ms", containerId.get(), backoffMs);
                    sleep(backoffMs);
                    backoffMs = nextBackoff(backoffMs);
                } finally {
                    closeFollow();
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                break;
            } catch (RuntimeException ex) {
                log.warn("Caddy access log follow failed; retrying in {} ms", backoffMs, ex);
                try {
                    sleep(backoffMs);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    break;
                }
                backoffMs = nextBackoff(backoffMs);
            }
        }
    }

    private Optional<String> resolveContainerId() {
        try {
            List<Container> runningContainers = dockerClient.listContainersCmd().withShowAll(false).exec();
            return selectContainer(runningContainers).map(Container::getId);
        } catch (RuntimeException ex) {
            log.warn("Failed to list Docker containers for Caddy access logs", ex);
            return Optional.empty();
        }
    }

    private void closeFollow() {
        AutoCloseable follow = currentFollow.getAndSet(null);
        if (follow == null) {
            return;
        }
        try {
            follow.close();
        } catch (Exception ex) {
            log.debug("Failed to close Caddy access log follow", ex);
        }
    }

    private static void sleep(long backoffMs) throws InterruptedException {
        TimeUnit.MILLISECONDS.sleep(backoffMs);
    }

    private static long nextBackoff(long backoffMs) {
        return Math.min(BACKOFF_MAX_MS, backoffMs * 2);
    }

    private static boolean matchesConfigured(Container container, String configured) {
        String id = container.getId();
        if (id != null && (id.equals(configured) || id.startsWith(configured))) {
            return true;
        }
        return namesOf(container).contains(configured);
    }

    private static boolean hasCaddyComposeLabel(Container container) {
        Map<String, String> labels = container.getLabels();
        return labels != null && "caddy".equals(labels.get(COMPOSE_SERVICE_LABEL));
    }

    private static boolean matchesCaddyName(Container container) {
        for (String name : namesOf(container)) {
            if ("caddy".equals(name) || COMPOSE_CADDY_NAME.matcher(name).matches()) {
                return true;
            }
        }
        return false;
    }

    private static List<String> namesOf(Container container) {
        String[] names = container.getNames();
        if (names == null || names.length == 0) {
            return List.of();
        }
        List<String> stripped = new ArrayList<>(names.length);
        for (String name : names) {
            if (name == null || name.isBlank()) {
                continue;
            }
            stripped.add(stripLeadingSlash(name));
        }
        return stripped;
    }

    private static String stripLeadingSlash(String value) {
        return value.startsWith("/") ? value.substring(1) : value;
    }
}
