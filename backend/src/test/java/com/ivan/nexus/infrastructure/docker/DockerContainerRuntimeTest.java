package com.ivan.nexus.infrastructure.docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.RestartContainerCmd;
import com.github.dockerjava.api.exception.NotFoundException;
import com.ivan.nexus.domain.shared.DomainException;
import com.ivan.nexus.domain.shared.NexusErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DockerContainerRuntimeTest {

    @Mock
    private DockerClient dockerClient;
    @Mock
    private RestartContainerCmd restartCmd;

    private DockerContainerRuntime runtime;

    @BeforeEach
    void setUp() {
        runtime = new DockerContainerRuntime(dockerClient);
    }

    @Test
    void notFoundBecomesContainerNotFound() {
        when(dockerClient.restartContainerCmd("missing")).thenReturn(restartCmd);
        when(restartCmd.exec()).thenThrow(new NotFoundException("no such container"));

        assertThatThrownBy(() -> runtime.restart("missing"))
                .isInstanceOf(DomainException.class)
                .hasMessage("Container not found")
                .satisfies(ex -> assertThat(((DomainException) ex).getCode())
                        .isEqualTo(NexusErrorCode.CONTAINER_NOT_FOUND));
    }

    @Test
    void restartDelegatesToDockerClient() {
        when(dockerClient.restartContainerCmd("abc123")).thenReturn(restartCmd);

        runtime.restart("abc123");

        verify(restartCmd).exec();
    }
}
