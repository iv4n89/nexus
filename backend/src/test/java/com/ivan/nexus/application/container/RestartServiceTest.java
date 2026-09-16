package com.ivan.nexus.application.container;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.RestartContainerCmd;
import com.github.dockerjava.api.exception.NotFoundException;
import com.ivan.nexus.application.audit.RecordAudit;
import com.ivan.nexus.application.project.ContainerInventory;
import com.ivan.nexus.domain.audit.AuditAction;
import com.ivan.nexus.domain.container.ContainerSnapshot;
import com.ivan.nexus.domain.shared.DomainException;
import com.ivan.nexus.domain.shared.NexusErrorCode;
import com.ivan.nexus.infrastructure.persistence.user.UserEntity;
import com.ivan.nexus.infrastructure.persistence.user.UserJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RestartServiceTest {

    @Mock
    DockerClient dockerClient;
    @Mock
    ContainerInventory inventory;
    @Mock
    RecordAudit recordAudit;
    @Mock
    UserJpaRepository users;
    @Mock
    RestartContainerCmd restartCmd;
    @Mock
    UserEntity admin;

    private final UUID adminId = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private RestartService useCase;

    @BeforeEach
    void setUp() {
        useCase = new RestartService(dockerClient, inventory, recordAudit, users);
    }

    @Test
    void unknownContainerThrowsContainerNotFound() {
        when(inventory.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute("missing", "admin"))
                .isInstanceOf(DomainException.class)
                .satisfies(ex -> assertThat(((DomainException) ex).getCode())
                        .isEqualTo(NexusErrorCode.CONTAINER_NOT_FOUND));

        verify(dockerClient, never()).restartContainerCmd(any());
        verify(recordAudit, never()).execute(any(), any(), any(), any(), any(), any());
    }

    @Test
    void dockerNotFoundThrowsContainerNotFound() {
        when(inventory.findById("abc123")).thenReturn(Optional.of(snapshot()));
        when(dockerClient.restartContainerCmd("abc123")).thenReturn(restartCmd);
        when(restartCmd.exec
                ()).thenThrow(new NotFoundException("no such container"));

        assertThatThrownBy(() -> useCase.execute("abc123", "admin"))
                .isInstanceOf(DomainException.class)
                .satisfies(ex -> assertThat(((DomainException) ex).getCode())
                        .isEqualTo(NexusErrorCode.CONTAINER_NOT_FOUND));

        verify(recordAudit, never()).execute(any(), any(), any(), any(), any(), any());
    }

    @Test
    void restartsAndAuditsServiceRestartFromLabels() {
        when(inventory.findById("abc123")).thenReturn(Optional.of(snapshot()));
        when(dockerClient.restartContainerCmd("abc123")).thenReturn(restartCmd);
        when(admin.getId()).thenReturn(adminId);
        when(users.findByUsername("admin")).thenReturn(Optional.of(admin));

        useCase.execute("abc123", "admin");

        verify(dockerClient).restartContainerCmd("abc123");
        verify(restartCmd).exec
                ();
        verify(recordAudit).execute(
                eq(adminId),
                eq(AuditAction.SERVICE_RESTART),
                eq("lab"),
                eq("web"),
                isNull(),
                any());
    }

    private static ContainerSnapshot snapshot() {
        return new ContainerSnapshot(
                "abc123",
                "lab-web-1",
                "nginx:alpine",
                "Up 2 minutes",
                "running",
                "healthy",
                Instant.parse("2026-01-01T00:00:00Z"),
                Map.of("nexus.project", "lab", "nexus.service", "web"),
                List.of(),
                1,
                Instant.parse("2026-01-01T00:00:01Z"));
    }
}
