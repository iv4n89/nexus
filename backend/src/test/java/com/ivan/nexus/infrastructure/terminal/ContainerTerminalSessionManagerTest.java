package com.ivan.nexus.infrastructure.terminal;

import com.ivan.nexus.application.audit.RecordAudit;
import com.ivan.nexus.application.project.ContainerInventory;
import com.ivan.nexus.application.terminal.TerminalSessionManager;
import com.ivan.nexus.application.user.UserDirectory;
import com.ivan.nexus.domain.audit.AuditAction;
import com.ivan.nexus.domain.container.ContainerSnapshot;
import com.ivan.nexus.domain.shared.DomainException;
import com.ivan.nexus.domain.shared.NexusErrorCode;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ContainerTerminalSessionManagerTest {

    @Mock
    RecordAudit recordAudit;
    @Mock
    UserDirectory users;
    @Mock
    ContainerInventory inventory;

    private ScheduledExecutorService scheduler;
    private ProcessTerminalSessionManager manager;
    private final Instant fixedNow = Instant.parse("2026-09-18T10:00:00Z");
    private final UUID userId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    @BeforeEach
    void setUp() {
        scheduler = Executors.newSingleThreadScheduledExecutor();
        org.mockito.Mockito.lenient().when(users.findIdByUsername("admin")).thenReturn(Optional.of(userId));
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
    void openContainerAuditsWithProjectAndContainer() throws Exception {
        Process process = aliveProcess(new ByteArrayInputStream(new byte[0]), new ByteArrayOutputStream());
        AtomicReference<String> execId = new AtomicReference<>();
        given(inventory.findById("ctr1")).willReturn(Optional.of(snapshot("ctr1", "lab")));

        manager = new ProcessTerminalSessionManager(
                () -> process,
                id -> {
                    execId.set(id);
                    return process;
                },
                inventory,
                recordAudit,
                users,
                Clock.fixed(fixedNow, ZoneOffset.UTC),
                Duration.ofMinutes(15),
                scheduler,
                Executors.newCachedThreadPool());

        TerminalSessionManager.TerminalSession session =
                manager.openContainer("admin", "127.0.0.1", "lab", "ctr1");

        assertThat(session.projectId()).isEqualTo("lab");
        assertThat(session.containerId()).isEqualTo("ctr1");
        assertThat(execId.get()).isEqualTo("ctr1");

        ArgumentCaptor<Map<String, Object>> metadata = ArgumentCaptor.forClass(Map.class);
        verify(recordAudit).execute(
                eq(userId),
                eq(AuditAction.TERMINAL_SESSION_START),
                eq("lab"),
                isNull(),
                eq("127.0.0.1"),
                metadata.capture());
        assertThat(metadata.getValue())
                .containsEntry("projectId", "lab")
                .containsEntry("containerId", "ctr1");
    }

    @Test
    void rejectsContainerOutsideProject() {
        given(inventory.findById("ctr1")).willReturn(Optional.of(snapshot("ctr1", "other")));
        manager = new ProcessTerminalSessionManager(
                () -> aliveProcess(new ByteArrayInputStream(new byte[0]), new ByteArrayOutputStream()),
                id -> aliveProcess(new ByteArrayInputStream(new byte[0]), new ByteArrayOutputStream()),
                inventory,
                recordAudit,
                users,
                Clock.fixed(fixedNow, ZoneOffset.UTC),
                Duration.ofMinutes(15),
                scheduler,
                Executors.newCachedThreadPool());

        assertThatThrownBy(() -> manager.openContainer("admin", "127.0.0.1", "lab", "ctr1"))
                .isInstanceOf(DomainException.class)
                .satisfies(ex -> assertThat(((DomainException) ex).getCode())
                        .isEqualTo(NexusErrorCode.CONTAINER_NOT_FOUND));
    }

    @Test
    void originCheckAllowsSameHost() {
        TerminalWebSocketConfig.OriginCheckHandshakeInterceptor interceptor =
                new TerminalWebSocketConfig.OriginCheckHandshakeInterceptor(List.of());
        org.springframework.http.server.ServerHttpRequest request =
                mock(org.springframework.http.server.ServerHttpRequest.class);
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.add("Origin", "http://localhost:3000");
        headers.add("Host", "localhost:8080");
        given(request.getHeaders()).willReturn(headers);
        assertThat(interceptor.beforeHandshake(
                request,
                mock(org.springframework.http.server.ServerHttpResponse.class),
                mock(org.springframework.web.socket.WebSocketHandler.class),
                new java.util.HashMap<>())).isTrue();
    }

    @Test
    void originCheckRejectsForeignOrigin() {
        TerminalWebSocketConfig.OriginCheckHandshakeInterceptor interceptor =
                new TerminalWebSocketConfig.OriginCheckHandshakeInterceptor(List.of());
        org.springframework.http.server.ServerHttpRequest request =
                mock(org.springframework.http.server.ServerHttpRequest.class);
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.add("Origin", "https://evil.example");
        headers.add("Host", "localhost:8080");
        given(request.getHeaders()).willReturn(headers);
        assertThat(interceptor.beforeHandshake(
                request,
                mock(org.springframework.http.server.ServerHttpResponse.class),
                mock(org.springframework.web.socket.WebSocketHandler.class),
                new java.util.HashMap<>())).isFalse();
    }

    private static ContainerSnapshot snapshot(String id, String project) {
        return new ContainerSnapshot(
                id,
                "web",
                "img",
                "running",
                "running",
                null,
                Instant.parse("2026-09-18T09:00:00Z"),
                Map.of("nexus.project", project),
                List.of(),
                0,
                null);
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
}
