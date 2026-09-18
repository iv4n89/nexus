package com.ivan.nexus.interfaces.env;

import com.ivan.nexus.application.env.DeleteProjectEnv;
import com.ivan.nexus.application.env.ListProjectEnv;
import com.ivan.nexus.application.env.ProjectEnvVarView;
import com.ivan.nexus.application.env.UpsertProjectEnv;
import com.ivan.nexus.infrastructure.security.ClientIp;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/projects/{id}/env")
public class ProjectEnvController {
    private final ListProjectEnv listProjectEnv;
    private final UpsertProjectEnv upsertProjectEnv;
    private final DeleteProjectEnv deleteProjectEnv;

    public ProjectEnvController(
            ListProjectEnv listProjectEnv,
            UpsertProjectEnv upsertProjectEnv,
            DeleteProjectEnv deleteProjectEnv) {
        this.listProjectEnv = listProjectEnv;
        this.upsertProjectEnv = upsertProjectEnv;
        this.deleteProjectEnv = deleteProjectEnv;
    }

    @GetMapping
    public List<EnvVarResponse> list(@PathVariable String id) {
        return listProjectEnv.execute(id).stream().map(EnvVarResponse::from).toList();
    }

    @PutMapping
    public EnvVarResponse upsert(
            @PathVariable String id,
            @RequestBody UpsertEnvRequest request,
            Authentication authentication,
            HttpServletRequest httpRequest) {
        ProjectEnvVarView view = upsertProjectEnv.execute(
                id,
                request.name(),
                request.value(),
                request.secret(),
                authentication.getName(),
                ClientIp.resolve(httpRequest));
        return EnvVarResponse.from(view);
    }

    @DeleteMapping("/{name}")
    public void delete(
            @PathVariable String id,
            @PathVariable String name,
            Authentication authentication,
            HttpServletRequest httpRequest) {
        deleteProjectEnv.execute(id, name, authentication.getName(), ClientIp.resolve(httpRequest));
    }

    public record UpsertEnvRequest(String name, String value, boolean secret) {
    }

    public record EnvVarResponse(
            UUID id,
            String projectId,
            String name,
            boolean secret,
            String value,
            Instant createdAt,
            Instant updatedAt) {
        static EnvVarResponse from(ProjectEnvVarView view) {
            return new EnvVarResponse(
                    view.id(),
                    view.projectId(),
                    view.name(),
                    view.secret(),
                    view.value(),
                    view.createdAt(),
                    view.updatedAt());
        }
    }
}
