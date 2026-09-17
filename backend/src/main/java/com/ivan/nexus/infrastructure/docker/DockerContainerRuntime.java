package com.ivan.nexus.infrastructure.docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.exception.NotFoundException;
import com.ivan.nexus.application.container.ContainerRuntime;
import com.ivan.nexus.domain.shared.DomainException;
import com.ivan.nexus.domain.shared.NexusErrorCode;
import org.springframework.stereotype.Component;

@Component
public class DockerContainerRuntime implements ContainerRuntime {
    private final DockerClient dockerClient;

    public DockerContainerRuntime(DockerClient dockerClient) {
        this.dockerClient = dockerClient;
    }

    @Override
    public void restart(String containerId) {
        try {
            dockerClient.restartContainerCmd(containerId)
                    .exec();
        } catch (NotFoundException ex) {
            throw new DomainException(NexusErrorCode.CONTAINER_NOT_FOUND, "Container not found");
        }
    }
}
