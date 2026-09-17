package com.ivan.nexus.infrastructure.docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.InspectContainerCmd;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.command.ListContainersCmd;
import com.github.dockerjava.api.command.PingCmd;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.ContainerConfig;
import com.github.dockerjava.api.model.ContainerNetwork;
import com.github.dockerjava.api.model.ContainerPort;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.NetworkSettings;
import com.github.dockerjava.api.model.Ports;
import com.ivan.nexus.application.project.ContainerInspect;
import com.ivan.nexus.application.project.PublishedPort;
import com.ivan.nexus.domain.container.ContainerSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DockerContainerInventoryTest {

    private static final String CONTAINER_ID = "abc123def456";

    @Mock
    private DockerClient dockerClient;
    @Mock
    private ListContainersCmd listContainersCmd;
    @Mock
    private InspectContainerCmd inspectContainerCmd;
    @Mock
    private PingCmd pingCmd;

    private DockerContainerInventory inventory;

    @BeforeEach
    void setUp() {
        inventory = new DockerContainerInventory(dockerClient);
    }

    @Test
    void listAllMapsListApiFieldsWithoutInspect() {
        Container listed = listedContainer();

        when(dockerClient.listContainersCmd()).thenReturn(listContainersCmd);
        when(listContainersCmd.withShowAll(true)).thenReturn(listContainersCmd);
        when(listContainersCmd.exec()).thenReturn(List.of(listed));

        List<ContainerSnapshot> snapshots = inventory.listAll();

        verify(dockerClient, never()).inspectContainerCmd(any());
        assertThat(snapshots).hasSize(1);
        ContainerSnapshot snapshot = snapshots.getFirst();
        assertThat(snapshot.id()).isEqualTo(CONTAINER_ID);
        assertThat(snapshot.name()).isEqualTo("lab-api-1");
        assertThat(snapshot.image()).isEqualTo("lab-api:latest");
        assertThat(snapshot.status()).isEqualTo("Up 2 hours");
        assertThat(snapshot.state()).isEqualTo("running");
        assertThat(snapshot.health()).isNull();
        assertThat(snapshot.created()).isEqualTo(Instant.ofEpochSecond(1_700_000_000L));
        assertThat(snapshot.labels()).containsEntry("nexus.project", "lab");
        assertThat(snapshot.ports()).containsExactly(
                new ContainerSnapshot.PortMapping(8080, 80),
                new ContainerSnapshot.PortMapping(null, 5432));
        assertThat(snapshot.restartCount()).isZero();
        assertThat(snapshot.startedAt()).isNull();
        assertThat(Arrays.stream(ContainerSnapshot.class.getRecordComponents()).map(RecordComponent::getName))
                .doesNotContain("env");
    }

    @Test
    void inspectMapsEnvAndPublishedPortsWithoutPuttingEnvOnSnapshot() {
        InspectContainerResponse inspect = mock(InspectContainerResponse.class);
        ContainerConfig config = mock(ContainerConfig.class);
        NetworkSettings networks = mock(NetworkSettings.class);
        Ports ports = new Ports();
        ports.bind(ExposedPort.tcp(5432), Ports.Binding.bindPort(15432));
        ContainerNetwork bridge = mock(ContainerNetwork.class);
        when(inspect.getId()).thenReturn(CONTAINER_ID);
        when(inspect.getName()).thenReturn("/lab-db-1");
        when(inspect.getConfig()).thenReturn(config);
        when(config.getImage()).thenReturn("postgres:16-alpine");
        when(config.getLabels()).thenReturn(Map.of("nexus.project", "lab"));
        when(config.getEnv()).thenReturn(new String[]{"POSTGRES_PASSWORD=s3cret", "POSTGRES_DB=lab"});
        when(inspect.getNetworkSettings()).thenReturn(networks);
        when(networks.getPorts()).thenReturn(ports);
        when(networks.getNetworks()).thenReturn(Map.of("bridge", bridge));
        when(bridge.getIpAddress()).thenReturn("172.18.0.2");
        when(dockerClient.inspectContainerCmd(CONTAINER_ID)).thenReturn(inspectContainerCmd);
        when(inspectContainerCmd.exec()).thenReturn(inspect);

        ContainerInspect mapped = inventory.inspect(CONTAINER_ID).orElseThrow();

        assertThat(mapped.env()).containsEntry("POSTGRES_PASSWORD", "s3cret");
        assertThat(mapped.publishedPorts()).contains(new PublishedPort(5432, 15432, null));
        assertThat(mapped.networkIps()).containsExactly("172.18.0.2");
        assertThat(Arrays.stream(ContainerSnapshot.class.getRecordComponents()).map(RecordComponent::getName))
                .doesNotContain("env");
    }

    @Test
    void findByIdMissingReturnsEmpty() {
        when(dockerClient.inspectContainerCmd("missing")).thenReturn(inspectContainerCmd);
        when(inspectContainerCmd.exec()).thenThrow(new NotFoundException("no such container"));

        assertThat(inventory.findById("missing")).isEmpty();
    }

    @Test
    void pingFailureDoesNotThrow() {
        when(dockerClient.pingCmd()).thenReturn(pingCmd);
        when(pingCmd.exec()).thenThrow(new RuntimeException("connection refused"));

        assertDoesNotThrow(() -> inventory.ping());
    }

    @Test
    void pingDoesNotCallClientWhenNotInvokedFromConstructor() {
        assertDoesNotThrow(() -> new DockerContainerInventory(dockerClient));
    }

    private Container listedContainer() {
        Container container = mock(Container.class);
        when(container.getId()).thenReturn(CONTAINER_ID);
        when(container.getNames()).thenReturn(new String[]{"/lab-api-1"});
        when(container.getImage()).thenReturn("lab-api:latest");
        when(container.getStatus()).thenReturn("Up 2 hours");
        when(container.getState()).thenReturn("running");
        when(container.getCreated()).thenReturn(1_700_000_000L);
        when(container.getLabels()).thenReturn(Map.of("nexus.project", "lab"));
        when(container.getPorts()).thenReturn(new ContainerPort[]{
                new ContainerPort().withPublicPort(8080).withPrivatePort(80),
                new ContainerPort().withPrivatePort(5432)
        });
        return container;
    }
}
