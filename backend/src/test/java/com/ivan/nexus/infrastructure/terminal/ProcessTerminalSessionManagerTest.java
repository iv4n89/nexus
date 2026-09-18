package com.ivan.nexus.infrastructure.terminal;

import com.ivan.nexus.application.audit.RecordAudit;
import com.ivan.nexus.application.terminal.TerminalSessionManager;
import com.ivan.nexus.application.user.UserDirectory;
import com.ivan.nexus.domain.audit.AuditAction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ProcessTerminalSessionManagerTest {

    @Mock
    RecordAudit recordAudit;
    @Mock
    UserDirectory users;

    private ScheduledExecutorService scheduler;
    private ProcessTerminalSessionManager manager;
    private final Instant fixedNow = Instant.parse("2026-09-18T10:00:00Z");
    private final UUID userId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    @BeforeEach
    void setUp() {
        scheduler = Executors.newSingleThreadScheduledExecutor();
        given(users.findIdByUsername("admin")).willReturn(Optional.of(userId));
    }

    @AfterEach
    void tearDown() {
        if (manager != null) {
            manager.shutdown();
        } else if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }

    @Test
    void openAuditsStartAndTracksExpiry() throws Exception {
        Process process = aliveProcess(new ByteArrayInputStream(new byte[0]), new ByteArrayOutputStream());
        manager = newManager(() -> process, Duration.ofMinutes(15));

        TerminalSessionManager.TerminalSession session = manager.open("admin", "127.0.0.1");

        assertThat(session.username()).isEqualTo("admin");
        assertThat(session.openedAt()).isEqualTo(fixedNow);
        assertThat(session.expiresAt()).isEqualTo(fixedNow.plus(Duration.ofMinutes(15)));
        assertThat(manager.isOpen(session.id())).isTrue();

        ArgumentCaptor<Map<String, Object>> metadata = ArgumentCaptor.forClass(Map.class);
        verify(recordAudit).execute(
                eq(userId),
                eq(AuditAction.TERMINAL_SESSION_START),
                isNull(),
                isNull(),
                eq("127.0.0.1"),
                metadata.capture());
        assertThat(metadata.getValue()).containsEntry("sessionId", session.id().toString());
    }

    @Test
    void writeSendsBytesToProcessStdin() throws Exception {
        ByteArrayOutputStream stdin = new ByteArrayOutputStream();
        Process process = aliveProcess(new ByteArrayInputStream(new byte[0]), stdin);
        manager = newManager(() -> process, Duration.ofMinutes(15));

        TerminalSessionManager.TerminalSession session = manager.open("admin", "10.0.0.1");
        manager.write(session.id(), "echo hi\n".getBytes());

        assertThat(stdin.toString()).isEqualTo("echo hi\n");
    }

    @Test
    void closeAuditsEndAndDestroysProcess() throws Exception {
        Process process = aliveProcess(new ByteArrayInputStream(new byte[0]), new ByteArrayOutputStream());
        manager = newManager(() -> process, Duration.ofMinutes(15));

        TerminalSessionManager.TerminalSession session = manager.open("admin", "10.0.0.1");
        manager.close(session.id(), "manual");

        assertThat(manager.isOpen(session.id())).isFalse();
        verify(process).destroyForcibly();
        verify(recordAudit).execute(
                eq(userId),
                eq(AuditAction.TERMINAL_SESSION_END),
                isNull(),
                isNull(),
                eq("10.0.0.1"),
                any());
    }

    @Test
    void timeoutClosesSession() throws Exception {
        Process process = aliveProcess(new ByteArrayInputStream(new byte[0]), new ByteArrayOutputStream());
        manager = newManager(() -> process, Duration.ofMillis(50));

        TerminalSessionManager.TerminalSession session = manager.open("admin", "10.0.0.1");
        awaitClosed(session.id(), 2_000);

        assertThat(manager.isOpen(session.id())).isFalse();
        verify(recordAudit, atLeastOnce()).execute(
                eq(userId),
                eq(AuditAction.TERMINAL_SESSION_END),
                isNull(),
                isNull(),
                eq("10.0.0.1"),
                any());
    }

    @Test
    void writeToUnknownSessionFails() throws Exception {
        Process process = aliveProcess(new ByteArrayInputStream(new byte[0]), new ByteArrayOutputStream());
        manager = newManager(() -> process, Duration.ofMinutes(15));
        manager.open("admin", "10.0.0.1");

        assertThatThrownBy(() -> manager.write(UUID.randomUUID(), new byte[]{1}))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not open");
    }

    private ProcessTerminalSessionManager newManager(
            ProcessTerminalSessionManager.ShellProcessFactory factory,
            Duration timeout) {
        return new ProcessTerminalSessionManager(
                factory,
                recordAudit,
                users,
                Clock.fixed(fixedNow, ZoneOffset.UTC),
                timeout,
                scheduler,
                Executors.newCachedThreadPool());
    }

    private static Process aliveProcess(InputStream stdout, OutputStream stdin) {
        Process process = mock(Process.class);
        AtomicBoolean alive = new AtomicBoolean(true);
        org.mockito.Mockito.lenient().when(process.isAlive()).thenAnswer(inv -> alive.get());
        org.mockito.Mockito.lenient().when(process.getInputStream()).thenReturn(stdout);
        org.mockito.Mockito.lenient().when(process.getOutputStream()).thenReturn(stdin);
        org.mockito.Mockito.lenient().doAnswer(inv -> {
            alive.set(false);
            return null;
        }).when(process).destroyForcibly();
        return process;
    }

    private void awaitClosed(UUID sessionId, long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (!manager.isOpen(sessionId)) {
                return;
            }
            Thread.sleep(20);
        }
    }
}
