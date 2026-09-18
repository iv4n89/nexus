package com.ivan.nexus.infrastructure.persistence.deployment;

import com.ivan.nexus.application.deployment.DeploymentStore;
import com.ivan.nexus.domain.deployment.Deployment;
import com.ivan.nexus.domain.deployment.DeploymentStatus;
import com.ivan.nexus.domain.shared.DomainException;
import com.ivan.nexus.domain.shared.NexusErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JpaDeploymentStoreTest {
    private DeploymentJpaRepository repository;
    private DeploymentStore store;

    @BeforeEach
    void setUp() {
        repository = mock(DeploymentJpaRepository.class);
        store = new JpaDeploymentStore(repository);
    }

    @Test
    void createsPendingDeploymentAndMapsKind() {
        UUID id = UUID.randomUUID();
        when(repository.saveAndFlush(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Deployment result = store.createPending(id, "lab", "admin", "rollback", null);

        ArgumentCaptor<DeploymentEntity> captor = ArgumentCaptor.forClass(DeploymentEntity.class);
        verify(repository).saveAndFlush(captor.capture());
        DeploymentEntity entity = captor.getValue();
        assertThat(entity.getStatus()).isEqualTo(DeploymentStatus.PENDING);
        assertThat(entity.getMetadata()).containsEntry("kind", "rollback");
        assertThat(result.kind()).isEqualTo("rollback");
    }

    @Test
    void translatesActiveIndexRaceOnCreate() {
        when(repository.saveAndFlush(org.mockito.ArgumentMatchers.any()))
                .thenThrow(new DataIntegrityViolationException(
                        "duplicate key value violates unique constraint \"uq_deployments_running\""));

        assertThatThrownBy(() -> store.createPending(UUID.randomUUID(), "lab", "admin", "deploy", null))
                .isInstanceOf(DomainException.class)
                .hasMessage("Deployment already in progress")
                .extracting(ex -> ((DomainException) ex).getCode())
                .isEqualTo(NexusErrorCode.DEPLOYMENT_IN_PROGRESS);
    }

    @Test
    void preservesUnrelatedCreateIntegrityFailures() {
        DataIntegrityViolationException failure = new DataIntegrityViolationException("projects_fk");
        when(repository.saveAndFlush(org.mockito.ArgumentMatchers.any())).thenThrow(failure);

        assertThatThrownBy(() -> store.createPending(UUID.randomUUID(), "missing", "admin", "deploy", null))
                .isSameAs(failure);
    }

    @Test
    void storesCommitShaOnCreatePending() {
        UUID id = UUID.randomUUID();
        when(repository.saveAndFlush(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Deployment result = store.createPending(id, "lab", "admin", "deploy", "abc123");

        assertThat(result.commitSha()).isEqualTo("abc123");
        ArgumentCaptor<DeploymentEntity> captor = ArgumentCaptor.forClass(DeploymentEntity.class);
        verify(repository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getCommitSha()).isEqualTo("abc123");
    }

    @Test
    void loadsBeforeLifecycleUpdatesAndUsesTransitions() {
        UUID id = UUID.randomUUID();
        DeploymentEntity pending = entity(id, DeploymentStatus.PENDING, "deploy");
        when(repository.findById(id)).thenReturn(Optional.of(pending));
        when(repository.save(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        Instant startedAt = Instant.parse("2026-09-18T00:00:00Z");

        Deployment running = store.markRunning(id, startedAt);
        store.recordExitCode(id, 0);
        store.recordHealthResult(id, true);
        Deployment finished = store.finish(
                id,
                DeploymentStatus.SUCCESS,
                Instant.parse("2026-09-18T00:01:00Z"),
                "done");

        assertThat(running.status()).isEqualTo(DeploymentStatus.RUNNING);
        assertThat(finished.status()).isEqualTo(DeploymentStatus.SUCCESS);
        assertThat(finished.startedAt()).isEqualTo(startedAt);
        assertThat(finished.exitCode()).isZero();
        assertThat(finished.healthOk()).isTrue();
        assertThat(finished.outputSummary()).isEqualTo("done");
        verify(repository, org.mockito.Mockito.times(4)).findById(id);
    }

    @Test
    void rejectsInvalidTerminalTransition() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(entity(id, DeploymentStatus.PENDING, "deploy")));

        assertThatThrownBy(() -> store.finish(id, DeploymentStatus.SUCCESS, Instant.now(), "done"))
                .isInstanceOf(DomainException.class)
                .extracting(ex -> ((DomainException) ex).getCode())
                .isEqualTo(NexusErrorCode.INVALID_TRANSITION);
    }

    @Test
    void mapsFindAndPreservesRepositoryHistoryOrder() {
        UUID newerId = UUID.randomUUID();
        UUID olderId = UUID.randomUUID();
        DeploymentEntity newer = entity(newerId, DeploymentStatus.SUCCESS, "deploy");
        DeploymentEntity older = entity(olderId, DeploymentStatus.FAILED, "rollback");
        when(repository.findById(newerId)).thenReturn(Optional.of(newer));
        when(repository.findByProjectIdOrderByCreatedAtDesc("lab")).thenReturn(List.of(newer, older));

        assertThat(store.findById(newerId)).contains(JpaDeploymentStore.toDomain(newer));
        assertThat(store.findProjectHistoryNewestFirst("lab"))
                .extracting(Deployment::id)
                .containsExactly(newerId, olderId);
    }

    private static DeploymentEntity entity(UUID id, DeploymentStatus status, String kind) {
        return new DeploymentEntity(
                id,
                "lab",
                status,
                Instant.parse("2026-09-18T00:00:00Z"),
                null,
                "admin",
                null,
                null,
                null,
                null,
                Map.of("kind", kind));
    }
}
