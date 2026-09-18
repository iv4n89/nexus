package com.ivan.nexus.application.terminal;

import java.time.Instant;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Outbound port for interactive shell sessions (Phase G).
 * Implementations must not persist terminal transcripts.
 */
public interface TerminalSessionManager {
    TerminalSession open(String username, String clientIp);

    /**
     * Opens an interactive shell inside a project container via {@code docker exec}.
     */
    TerminalSession openContainer(String username, String clientIp, String projectId, String containerId);

    void write(UUID sessionId, byte[] data);

    void onOutput(UUID sessionId, Consumer<byte[]> sink);

    void close(UUID sessionId, String reason);

    boolean isOpen(UUID sessionId);

    record TerminalSession(
            UUID id,
            String username,
            Instant openedAt,
            Instant expiresAt,
            String projectId,
            String containerId) {
        public TerminalSession(UUID id, String username, Instant openedAt, Instant expiresAt) {
            this(id, username, openedAt, expiresAt, null, null);
        }
    }
}
