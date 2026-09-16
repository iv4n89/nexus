package com.ivan.nexus.interfaces.deployment;

import com.ivan.nexus.application.deployment.DeployProject;
import com.ivan.nexus.application.deployment.RollbackProject;
import com.ivan.nexus.domain.deployment.Deployment;
import com.ivan.nexus.domain.deployment.DeploymentStatus;
import com.ivan.nexus.domain.shared.DomainException;
import com.ivan.nexus.domain.shared.NexusErrorCode;
import com.ivan.nexus.infrastructure.persistence.deployment.DeploymentEntity;
import com.ivan.nexus.infrastructure.persistence.deployment.DeploymentJpaRepository;
import com.ivan.nexus.infrastructure.sse.DeploymentStreamHub;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
public class DeploymentController {
    private final DeployProject deployProject;
    private final RollbackProject rollbackProject;
    private final DeploymentJpaRepository deployments;
    private final DeploymentStreamHub hub;

    public DeploymentController(
            DeployProject deployProject,
            RollbackProject rollbackProject,
            DeploymentJpaRepository deployments,
            DeploymentStreamHub hub) {
        this.deployProject = deployProject;
        this.rollbackProject = rollbackProject;
        this.deployments = deployments;
        this.hub = hub;
    }

    @PostMapping("/api/projects/{id}/deploy")
    public ResponseEntity<AcceptedResponse> deploy(@PathVariable String id, Authentication authentication) {
        Deployment started = deployProject.execute(id, authentication.getName());
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(new AcceptedResponse(started.id(), started.status()));
    }

    @PostMapping("/api/projects/{id}/rollback")
    public ResponseEntity<AcceptedResponse> rollback(@PathVariable String id, Authentication authentication) {
        Deployment started = rollbackProject.execute(id, authentication.getName());
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(new AcceptedResponse(started.id(), started.status()));
    }

    @GetMapping("/api/projects/{id}/deployments")
    public List<DeploymentResponse> history(@PathVariable String id) {
        return deployments.findByProjectIdOrderByCreatedAtDesc(id).stream()
                .map(DeploymentResponse::from)
                .toList();
    }

    @GetMapping("/api/projects/{id}/deployments/{deploymentId}")
    public DeploymentResponse one(@PathVariable String id, @PathVariable UUID deploymentId) {
        DeploymentEntity entity = deployments.findByIdAndProjectId(deploymentId, id)
                .orElseThrow(() -> new DomainException(NexusErrorCode.DEPLOYMENT_NOT_FOUND, "Deployment not found"));
        return DeploymentResponse.from(entity);
    }

    @GetMapping(path = "/api/deployments/{id}/stream", produces = {
            MediaType.TEXT_EVENT_STREAM_VALUE,
            MediaType.APPLICATION_JSON_VALUE
    })
    public SseEmitter stream(@PathVariable UUID id, HttpServletResponse response) {
        if (deployments.findById(id).isEmpty()) {
            throw new DomainException(NexusErrorCode.DEPLOYMENT_NOT_FOUND, "Deployment not found");
        }
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("X-Accel-Buffering", "no");
        SseEmitter emitter = new SseEmitter(0L);
        hub.subscribe(id, emitter);
        return emitter;
    }

    public record AcceptedResponse(UUID id, DeploymentStatus status) {
    }

    public record DeploymentResponse(
            UUID id,
            String projectId,
            DeploymentStatus status,
            Instant startedAt,
            Instant finishedAt,
            String triggeredBy,
            String commitSha,
            Integer exitCode,
            String outputSummary,
            Boolean healthOk,
            String kind) {
        static DeploymentResponse from(DeploymentEntity entity) {
            return new DeploymentResponse(
                    entity.getId(),
                    entity.getProjectId(),
                    entity.getStatus(),
                    entity.getStartedAt(),
                    entity.getFinishedAt(),
                    entity.getTriggeredBy(),
                    entity.getCommitSha(),
                    entity.getExitCode(),
                    entity.getOutputSummary(),
                    entity.getHealthOk(),
                    entity.getKind());
        }
    }
}
