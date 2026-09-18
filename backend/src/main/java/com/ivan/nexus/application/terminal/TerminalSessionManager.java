package com.ivan.nexus.application.terminal;

import java.time.Instant;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Outbound port for interactive VPS shell sessions (Phase G).
 * Implementations must not persist terminal transcripts.
 */
public interface TerminalSessionManager {
    TerminalSession open(String username, String clientIp);

    void write(UUID sessionId, byte[] data);

    void onOutput(UUID sessionId, Consumer<byte[]> sink);

    void close(UUID sessionId, String reason);

    boolean isOpen(UUID sessionId);

    record TerminalSession(UUID id, String username, Instant openedAt, Instant expiresAt) {
    }
}
