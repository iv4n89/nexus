package com.ivan.nexus.infrastructure.persistence.deployment;

import com.ivan.nexus.application.deployment.DeploymentStore;
import com.ivan.nexus.application.deployment.ManagedProjectStore;
import com.ivan.nexus.domain.deployment.DeploymentStatus;
import com.ivan.nexus.domain.manifest.ProjectManifest;
import com.ivan.nexus.domain.shared.DomainException;
import com.ivan.nexus.domain.shared.NexusErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
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
    DeploymentJpaRepository deployments;

    @Autowired
    DeploymentStore deploymentStore;

    @Autowired
    ManagedProjectStore managedProjectStore;

    @Autowired
    DeploymentEventJpaRepository events;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void contextLoads() {}

    @Test
    void persistsProjectPendingDeploymentAndEventLine() {
        String projectId = "demo-" + UUID.randomUUID();
        UUID deploymentId = UUID.randomUUID();

        managedProjectStore.upsert(
                manifest(projectId, "Demo", "A demo project"),
                Path.of("/tmp/demo"),
                Path.of("/tmp/demo/nexus.yml"));

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

        ProjectRow foundProject = loadProject(projectId);
        assertThat(foundProject.name()).isEqualTo("Demo");
        assertThat(foundProject.description()).isEqualTo("A demo project");
        assertThat(foundProject.workingDirectory()).isEqualTo("/tmp/demo");
        assertThat(foundProject.manifestPath()).isEqualTo("/tmp/demo/nexus.yml");
        assertThat(foundProject.createdAt()).isNotNull();
        assertThat(foundProject.updatedAt()).isNotNull();

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
        managedProjectStore.upsert(
                manifest(projectId, "Busy", null),
                Path.of("/tmp/busy"),
                Path.of("/tmp/busy/nexus.yml"));

        deployments.saveAndFlush(runningDeployment(projectId, "admin"));

        assertThatThrownBy(() -> deployments.saveAndFlush(runningDeployment(projectId, "admin")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deploymentStoreTranslatesActiveIndexRace() {
        String projectId = "store-race-" + UUID.randomUUID();
        managedProjectStore.upsert(
                manifest(projectId, "Store race", null),
                Path.of("/tmp/store-race"),
                Path.of("/tmp/store-race/nexus.yml"));
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
        ProjectRow created = loadProject(projectId);
        Instant createdAt = created.createdAt();
        Instant firstUpdatedAt = created.updatedAt();
        assertThat(created.name()).isEqualTo(projectId);
        assertThat(created.description()).isEqualTo("first");
        assertThat(created.workingDirectory()).isEqualTo("/srv/first");
        assertThat(created.manifestPath()).isEqualTo("/srv/first/nexus.yml");
        Thread.sleep(20);

        managedProjectStore.upsert(
                manifest(projectId, "Renamed", "second"),
                Path.of("/srv/second"),
                Path.of("/srv/second/custom.yml"));

        ProjectRow updated = loadProject(projectId);
        assertThat(updated.name()).isEqualTo("Renamed");
        assertThat(updated.description()).isEqualTo("second");
        assertThat(updated.workingDirectory()).isEqualTo("/srv/second");
        assertThat(updated.manifestPath()).isEqualTo("/srv/second/custom.yml");
        assertThat(updated.createdAt()).isEqualTo(createdAt);
        assertThat(updated.updatedAt()).isAfter(firstUpdatedAt);
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

        Integer rowCount = jdbc.queryForObject(
                "SELECT count(*) FROM projects WHERE id = ?",
                Integer.class,
                projectId);
        assertThat(rowCount).isEqualTo(1);
        ProjectRow row = loadProject(projectId);
        Map<String, List<String>> expectedByName = Map.of(
                "First", List.of("first description", "/srv/first", "/srv/first/nexus.yml"),
                "Second", List.of("second description", "/srv/second", "/srv/second/nexus.yml"));
        assertThat(expectedByName).containsKey(row.name());
        assertThat(List.of(row.description(), row.workingDirectory(), row.manifestPath()))
                .isEqualTo(expectedByName.get(row.name()));
        assertThat(row.createdAt()).isNotNull();
        assertThat(row.updatedAt()).isNotNull();
    }

    private ProjectRow loadProject(String projectId) {
        return jdbc.queryForObject(
                """
                SELECT name, description, working_directory, manifest_path, created_at, updated_at
                FROM projects
                WHERE id = ?
                """,
                (resultSet, rowNum) -> new ProjectRow(
                        resultSet.getString("name"),
                        resultSet.getString("description"),
                        resultSet.getString("working_directory"),
                        resultSet.getString("manifest_path"),
                        resultSet.getTimestamp("created_at").toInstant(),
                        resultSet.getTimestamp("updated_at").toInstant()),
                projectId);
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

    private record ProjectRow(
            String name,
            String description,
            String workingDirectory,
            String manifestPath,
            Instant createdAt,
            Instant updatedAt) {
    }
}
