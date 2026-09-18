package com.ivan.nexus.infrastructure.terminal;

import com.ivan.nexus.application.audit.RecordAudit;
import com.ivan.nexus.application.project.ContainerInventory;
import com.ivan.nexus.application.terminal.TerminalSessionManager;
import com.ivan.nexus.application.user.UserDirectory;
import com.ivan.nexus.domain.audit.AuditAction;
import com.ivan.nexus.domain.container.ContainerSnapshot;
import com.ivan.nexus.domain.project.ProjectGrouping;
import com.ivan.nexus.domain.shared.DomainException;
import com.ivan.nexus.domain.shared.NexusErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Spawns host bash or {@code docker exec} into a project container. Does not persist transcripts.
 */
public class ProcessTerminalSessionManager implements TerminalSessionManager {
    private static final Logger log = LoggerFactory.getLogger(ProcessTerminalSessionManager.class);

    private final ShellProcessFactory processFactory;
    private final ContainerExecFactory containerExecFactory;
    private final ContainerInventory inventory;
    private final RecordAudit recordAudit;
    private final UserDirectory users;
    private final Clock clock;
    private final Duration sessionTimeout;
    private final ScheduledExecutorService scheduler;
    private final ExecutorService ioExecutor;
    private final ConcurrentHashMap<UUID, LiveSession> sessions = new ConcurrentHashMap<>();

    public ProcessTerminalSessionManager(
            ShellProcessFactory processFactory,
            ContainerExecFactory containerExecFactory,
            ContainerInventory inventory,
            RecordAudit recordAudit,
            UserDirectory users,
            Clock clock,
            Duration sessionTimeout,
            ScheduledExecutorService scheduler,
            ExecutorService ioExecutor) {
        this.processFactory = processFactory;
        this.containerExecFactory = containerExecFactory;
        this.inventory = inventory;
        this.recordAudit = recordAudit;
        this.users = users;
        this.clock = clock;
        this.sessionTimeout = sessionTimeout;
        this.scheduler = scheduler;
        this.ioExecutor = ioExecutor;
    }

    @Override
    public TerminalSession open(String username, String clientIp) {
        Instant openedAt = clock.instant();
        Instant expiresAt = openedAt.plus(sessionTimeout);
        UUID id = UUID.randomUUID();
        try {
            Process process = processFactory.start();
            return register(id, username, clientIp, openedAt, expiresAt, process, null, null);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to start terminal session", e);
        }
    }

    @Override
    public TerminalSession openContainer(String username, String clientIp, String projectId, String containerId) {
        ContainerSnapshot container = inventory.findById(containerId)
                .orElseThrow(() -> new DomainException(NexusErrorCode.CONTAINER_NOT_FOUND, "Container not found"));
        String owner = ProjectGrouping.projectId(container.name(), container.labels());
        if (projectId == null || !projectId.equals(owner)) {
            throw new DomainException(NexusErrorCode.CONTAINER_NOT_FOUND, "Container not found for project");
        }
        Instant openedAt = clock.instant();
        Instant expiresAt = openedAt.plus(sessionTimeout);
        UUID id = UUID.randomUUID();
        try {
            Process process = containerExecFactory.start(containerId);
            return register(id, username, clientIp, openedAt, expiresAt, process, projectId, containerId);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to start container terminal session", e);
        }
    }

    private TerminalSession register(
            UUID id,
            String username,
            String clientIp,
            Instant openedAt,
            Instant expiresAt,
            Process process,
            String projectId,
            String containerId) {
        ScheduledFuture<?> timeout = scheduler.schedule(
                () -> close(id, "timeout"),
                sessionTimeout.toMillis(),
                TimeUnit.MILLISECONDS);
        LiveSession live = new LiveSession(
                id, username, clientIp, openedAt, expiresAt, process, timeout, projectId, containerId);
        sessions.put(id, live);
        audit(username, clientIp, id, projectId, containerId, AuditAction.TERMINAL_SESSION_START, "start");
        log.info("Terminal session {} started for {} (project={}, container={})",
                id, username, projectId, containerId);
        return new TerminalSession(id, username, openedAt, expiresAt, projectId, containerId);
    }

    @Override
    public void write(UUID sessionId, byte[] data) {
        LiveSession live = requireOpen(sessionId);
        try {
            OutputStream stdin = live.process.getOutputStream();
            stdin.write(data);
            stdin.flush();
        } catch (IOException e) {
            close(sessionId, "write-error");
            throw new UncheckedIOException("Failed to write to terminal session " + sessionId, e);
        }
    }

    @Override
    public void onOutput(UUID sessionId, Consumer<byte[]> sink) {
        LiveSession live = requireOpen(sessionId);
        if (!live.outputAttached.compareAndSet(false, true)) {
            throw new IllegalStateException("Output already attached for session " + sessionId);
        }
        ioExecutor.execute(() -> pumpOutput(live, sink));
    }

    @Override
    public void close(UUID sessionId, String reason) {
        LiveSession live = sessions.remove(sessionId);
        if (live == null) {
            return;
        }
        live.timeout.cancel(false);
        live.process.destroyForcibly();
        audit(live.username, live.clientIp, sessionId, live.projectId, live.containerId,
                AuditAction.TERMINAL_SESSION_END, reason);
        log.info("Terminal session {} closed ({})", sessionId, reason);
    }

    @Override
    public boolean isOpen(UUID sessionId) {
        LiveSession live = sessions.get(sessionId);
        return live != null && live.process.isAlive();
    }

    public void shutdown() {
        for (UUID id : sessions.keySet()) {
            close(id, "shutdown");
        }
        scheduler.shutdownNow();
        ioExecutor.shutdownNow();
    }

    private LiveSession requireOpen(UUID sessionId) {
        LiveSession live = sessions.get(sessionId);
        if (live == null || !live.process.isAlive()) {
            throw new IllegalStateException("Terminal session not open: " + sessionId);
        }
        return live;
    }

    private void pumpOutput(LiveSession live, Consumer<byte[]> sink) {
        try (InputStream stdout = live.process.getInputStream()) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = stdout.read(buffer)) != -1) {
                if (!sessions.containsKey(live.id)) {
                    break;
                }
                byte[] chunk = new byte[read];
                System.arraycopy(buffer, 0, chunk, 0, read);
                sink.accept(chunk);
            }
        } catch (IOException e) {
            log.debug("Terminal output ended for {}: {}", live.id, e.toString());
        } finally {
            close(live.id, "process-exit");
        }
    }

    private void audit(
            String username,
            String clientIp,
            UUID sessionId,
            String projectId,
            String containerId,
            AuditAction action,
            String reason) {
        UUID userId = users.findIdByUsername(username).orElse(null);
        Map<String, Object> metadata = new java.util.LinkedHashMap<>();
        metadata.put("sessionId", sessionId.toString());
        metadata.put("reason", reason == null ? "" : reason);
        if (projectId != null) {
            metadata.put("projectId", projectId);
        }
        if (containerId != null) {
            metadata.put("containerId", containerId);
        }
        recordAudit.execute(userId, action, projectId, null, clientIp, metadata);
    }

    @FunctionalInterface
    public interface ShellProcessFactory {
        Process start() throws IOException;
    }

    @FunctionalInterface
    public interface ContainerExecFactory {
        Process start(String containerId) throws IOException;
    }

    private static final class LiveSession {
        private final UUID id;
        private final String username;
        private final String clientIp;
        private final Instant openedAt;
        private final Instant expiresAt;
        private final Process process;
        private final ScheduledFuture<?> timeout;
        private final String projectId;
        private final String containerId;
        private final java.util.concurrent.atomic.AtomicBoolean outputAttached =
                new java.util.concurrent.atomic.AtomicBoolean(false);

        private LiveSession(
                UUID id,
                String username,
                String clientIp,
                Instant openedAt,
                Instant expiresAt,
                Process process,
                ScheduledFuture<?> timeout,
                String projectId,
                String containerId) {
            this.id = id;
            this.username = username;
            this.clientIp = clientIp;
            this.openedAt = openedAt;
            this.expiresAt = expiresAt;
            this.process = process;
            this.timeout = timeout;
            this.projectId = projectId;
            this.containerId = containerId;
        }
    }

    public static ProcessTerminalSessionManager createDefault(
            RecordAudit recordAudit,
            UserDirectory users,
            ContainerInventory inventory,
            Duration sessionTimeout) {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "terminal-timeout");
            t.setDaemon(true);
            return t;
        });
        ExecutorService ioExecutor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "terminal-io");
            t.setDaemon(true);
            return t;
        });
        ShellProcessFactory factory = () -> new ProcessBuilder("/bin/bash")
                .redirectErrorStream(true)
                .start();
        ContainerExecFactory containerFactory = containerId -> new ProcessBuilder(
                "docker", "exec", "-i", containerId, "/bin/sh")
                .redirectErrorStream(true)
                .start();
        return new ProcessTerminalSessionManager(
                factory,
                containerFactory,
                inventory,
                recordAudit,
                users,
                Clock.systemUTC(),
                sessionTimeout,
                scheduler,
                ioExecutor);
    }
}
