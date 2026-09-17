package com.ivan.nexus.infrastructure.persistence.deployment;

import com.ivan.nexus.application.deployment.DeploymentStore;
import com.ivan.nexus.application.deployment.ManagedProjectStore;
import com.ivan.nexus.domain.deployment.DeploymentStatus;
import com.ivan.nexus.domain.manifest.ProjectManifest;
import com.ivan.nexus.domain.shared.DomainException;
import com.ivan.nexus.domain.shared.NexusErrorCode;
import com.ivan.nexus.infrastructure.persistence.project.ManagedProjectEntity;
import com.ivan.nexus.infrastructure.persistence.project.ManagedProjectJpaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers
class DeploymentPersistenceIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    ManagedProjectJpaRepository projects;

    @Autowired
    DeploymentJpaRepository deployments;

    @Autowired
    DeploymentStore deploymentStore;

    @Autowired
    ManagedProjectStore managedProjectStore;

    @Autowired
    DeploymentEventJpaRepository events;

    @Test
    void contextLoads() {}

    @Test
    void persistsProjectPendingDeploymentAndEventLine() {
        String projectId = "demo-" + UUID.randomUUID();
        UUID deploymentId = UUID.randomUUID();

        projects.saveAndFlush(new ManagedProjectEntity(
                projectId,
                "Demo",
                "A demo project",
                "/tmp/demo",
                "/tmp/demo/nexus.yml"));

        deployments.saveAndFlush(new DeploymentEntity(
                deploymentId,
                projectId,
                DeploymentStatus.PENDING,
                null,
                null,
                "admin",
                null,
                null,
                null,
                null));

        events.saveAndFlush(new DeploymentEventEntity(deploymentId, "starting deploy"));

        ManagedProjectEntity foundProject = projects.findById(projectId).orElseThrow();
        assertThat(foundProject.getName()).isEqualTo("Demo");
        assertThat(foundProject.getDescription()).isEqualTo("A demo project");
        assertThat(foundProject.getWorkingDirectory()).isEqualTo("/tmp/demo");
        assertThat(foundProject.getManifestPath()).isEqualTo("/tmp/demo/nexus.yml");
        assertThat(foundProject.getCreatedAt()).isNotNull();
        assertThat(foundProject.getUpdatedAt()).isNotNull();

        List<DeploymentEntity> foundDeployments = deployments.findByProjectIdOrderByCreatedAtDesc(projectId);
        assertThat(foundDeployments).hasSize(1);
        DeploymentEntity foundDeployment = foundDeployments.getFirst();
        assertThat(foundDeployment.getId()).isEqualTo(deploymentId);
        assertThat(foundDeployment.getStatus()).isEqualTo(DeploymentStatus.PENDING);
        assertThat(foundDeployment.getTriggeredBy()).isEqualTo("admin");
        assertThat(foundDeployment.getCreatedAt()).isNotNull();
        assertThat(deployments.existsByProjectIdAndStatusIn(
                projectId, List.of(DeploymentStatus.PENDING, DeploymentStatus.RUNNING)))
                .isTrue();

        List<DeploymentEventEntity> foundEvents = events.findByDeploymentIdOrderByIdAsc(deploymentId);
        assertThat(foundEvents).hasSize(1);
        assertThat(foundEvents.getFirst().getLine()).isEqualTo("starting deploy");
        assertThat(foundEvents.getFirst().getCreatedAt()).isNotNull();
    }

    @Test
    void rejectsSecondRunningDeploymentForSameProject() {
        String projectId = "busy-" + UUID.randomUUID();
        projects.saveAndFlush(new ManagedProjectEntity(
                projectId,
                "Busy",
                null,
                "/tmp/busy",
                "/tmp/busy/nexus.yml"));

        deployments.saveAndFlush(runningDeployment(projectId, "admin"));

        assertThatThrownBy(() -> deployments.saveAndFlush(runningDeployment(projectId, "admin")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deploymentStoreTranslatesActiveIndexRace() {
        String projectId = "store-race-" + UUID.randomUUID();
        projects.saveAndFlush(new ManagedProjectEntity(
                projectId,
                "Store race",
                null,
                "/tmp/store-race",
                "/tmp/store-race/nexus.yml"));
        UUID firstId = UUID.randomUUID();
        deploymentStore.createPending(firstId, projectId, "admin", "deploy");
        deploymentStore.markRunning(firstId, java.time.Instant.now());

        assertThatThrownBy(() ->
                deploymentStore.createPending(UUID.randomUUID(), projectId, "admin", "rollback"))
                .isInstanceOf(DomainException.class)
                .hasMessage("Deployment already in progress")
                .extracting(ex -> ((DomainException) ex).getCode())
                .isEqualTo(NexusErrorCode.DEPLOYMENT_IN_PROGRESS);
    }

    @Test
    void managedProjectStoreInsertsAndUpdatesAllFieldsWithoutResettingCreatedAt() throws Exception {
        String projectId = "managed-" + UUID.randomUUID();
        managedProjectStore.upsert(
                manifest(projectId, "  ", "first"),
                Path.of("/srv/first"),
                Path.of("/srv/first/nexus.yml"));
        ManagedProjectEntity created = projects.findById(projectId).orElseThrow();
        Instant createdAt = created.getCreatedAt();
        Instant firstUpdatedAt = created.getUpdatedAt();
        assertThat(created.getName()).isEqualTo(projectId);
        assertThat(created.getDescription()).isEqualTo("first");
        assertThat(created.getWorkingDirectory()).isEqualTo("/srv/first");
        assertThat(created.getManifestPath()).isEqualTo("/srv/first/nexus.yml");
        Thread.sleep(20);

        managedProjectStore.upsert(
                manifest(projectId, "Renamed", "second"),
                Path.of("/srv/second"),
                Path.of("/srv/second/custom.yml"));

        ManagedProjectEntity updated = projects.findById(projectId).orElseThrow();
        assertThat(updated.getName()).isEqualTo("Renamed");
        assertThat(updated.getDescription()).isEqualTo("second");
        assertThat(updated.getWorkingDirectory()).isEqualTo("/srv/second");
        assertThat(updated.getManifestPath()).isEqualTo("/srv/second/custom.yml");
        assertThat(updated.getCreatedAt()).isEqualTo(createdAt);
        assertThat(updated.getUpdatedAt()).isAfter(firstUpdatedAt);
    }

    @Test
    void concurrentFirstManagedProjectUpsertsCreateOneCompleteRow() throws Exception {
        String projectId = "concurrent-" + UUID.randomUUID();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> {
                ready.countDown();
                start.await();
                managedProjectStore.upsert(
                        manifest(projectId, "First", "first description"),
                        Path.of("/srv/first"),
                        Path.of("/srv/first/nexus.yml"));
                return null;
            });
            var second = executor.submit(() -> {
                ready.countDown();
                start.await();
                managedProjectStore.upsert(
                        manifest(projectId, "Second", "second description"),
                        Path.of("/srv/second"),
                        Path.of("/srv/second/nexus.yml"));
                return null;
            });
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
        }

        List<ManagedProjectEntity> rows = projects.findAllById(List.of(projectId));
        assertThat(rows).hasSize(1);
        ManagedProjectEntity row = rows.getFirst();
        Map<String, List<String>> expectedByName = Map.of(
                "First", List.of("first description", "/srv/first", "/srv/first/nexus.yml"),
                "Second", List.of("second description", "/srv/second", "/srv/second/nexus.yml"));
        assertThat(expectedByName).containsKey(row.getName());
        assertThat(List.of(row.getDescription(), row.getWorkingDirectory(), row.getManifestPath()))
                .isEqualTo(expectedByName.get(row.getName()));
        assertThat(row.getCreatedAt()).isNotNull();
        assertThat(row.getUpdatedAt()).isNotNull();
    }

    private static DeploymentEntity runningDeployment(String projectId, String triggeredBy) {
        return new DeploymentEntity(
                UUID.randomUUID(),
                projectId,
                DeploymentStatus.RUNNING,
                null,
                null,
                triggeredBy,
                null,
                null,
                null,
                null);
    }

    private static ProjectManifest manifest(
            String projectId,
            String name,
            String description) {
        return new ProjectManifest(
                new ProjectManifest.ProjectBlock(projectId, name, description, "/ignored"),
                List.of(),
                new ProjectManifest.CommandBlock("./deploy.sh"),
                null,
                null,
                null);
    }
}
