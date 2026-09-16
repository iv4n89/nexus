package com.ivan.nexus.infrastructure.persistence.deployment;

import com.ivan.nexus.domain.deployment.DeploymentStatus;
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

import java.util.List;
import java.util.UUID;

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
}
