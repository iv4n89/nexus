package com.ivan.nexus.infrastructure.sse;

import com.ivan.nexus.application.deployment.DeploymentEventStore;
import com.ivan.nexus.application.deployment.DeploymentProgress;
import com.ivan.nexus.application.deployment.DeploymentStore;
import com.ivan.nexus.domain.deployment.Deployment;
import com.ivan.nexus.domain.deployment.DeploymentStatus;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DeploymentStreamHubTest {
    @Test
    void implementsApplicationDeploymentProgressPort() {
        assertThat(DeploymentStreamHub.class).isAssignableTo(DeploymentProgress.class);
    }

    @Test
    void appendPersistsBeforeLiveFanOutUsingNamedLogEvent() throws Exception {
        UUID id = UUID.randomUUID();
        DeploymentEventStore events = mock(DeploymentEventStore.class);
        DeploymentStore deployments = mock(DeploymentStore.class);
        SseEmitter emitter = mock(SseEmitter.class);
        when(events.findLinesOldestFirst(id)).thenReturn(List.of());
        when(deployments.findById(id)).thenReturn(Optional.of(deployment(id, DeploymentStatus.RUNNING)));
        DeploymentStreamHub hub = new DeploymentStreamHub(events, deployments);
        hub.subscribe(id, emitter);
        clearInvocations(events, deployments, emitter);

        hub.append(id, "building image");

        ArgumentCaptor<SseEmitter.SseEventBuilder> event =
                ArgumentCaptor.forClass(SseEmitter.SseEventBuilder.class);
        InOrder order = inOrder(events, emitter);
        order.verify(events).append(id, "building image");
        order.verify(emitter).send(event.capture());
        assertThat(eventPayload(event.getValue()))
                .contains("event:log")
                .contains("building image");
    }

    @Test
    void subscribeReplaysPersistedLinesOldestFirstBeforeLiveRegistration() throws Exception {
        UUID id = UUID.randomUUID();
        DeploymentEventStore events = mock(DeploymentEventStore.class);
        DeploymentStore deployments = mock(DeploymentStore.class);
        SseEmitter emitter = mock(SseEmitter.class);
        when(events.findLinesOldestFirst(id)).thenReturn(List.of("first", "second"));
        when(deployments.findById(id)).thenReturn(Optional.of(deployment(id, DeploymentStatus.RUNNING)));
        DeploymentStreamHub hub = new DeploymentStreamHub(events, deployments);

        hub.subscribe(id, emitter);

        ArgumentCaptor<SseEmitter.SseEventBuilder> replayed =
                ArgumentCaptor.forClass(SseEmitter.SseEventBuilder.class);
        InOrder order = inOrder(events, deployments, emitter);
        order.verify(events).findLinesOldestFirst(id);
        order.verify(emitter, times(2)).send(replayed.capture());
        order.verify(deployments).findById(id);
        order.verify(emitter).onCompletion(any());
        List<String> replayPayloads = replayed.getAllValues().stream()
                .map(DeploymentStreamHubTest::eventPayload)
                .toList();
        assertThat(replayPayloads).allSatisfy(payload -> assertThat(payload).contains("event:log"));
        assertThat(replayPayloads.get(0)).contains("first");
        assertThat(replayPayloads.get(1)).contains("second");

        clearInvocations(emitter);
        hub.append(id, "live");
        ArgumentCaptor<SseEmitter.SseEventBuilder> live =
                ArgumentCaptor.forClass(SseEmitter.SseEventBuilder.class);
        verify(emitter).send(live.capture());
        assertThat(eventPayload(live.getValue())).contains("live");
    }

    @Test
    void terminalSubscriptionCompletesAfterReplayAndIsNotRetained() throws Exception {
        UUID id = UUID.randomUUID();
        DeploymentEventStore events = mock(DeploymentEventStore.class);
        DeploymentStore deployments = mock(DeploymentStore.class);
        SseEmitter emitter = mock(SseEmitter.class);
        when(events.findLinesOldestFirst(id)).thenReturn(List.of("finished"));
        when(deployments.findById(id)).thenReturn(Optional.of(deployment(id, DeploymentStatus.SUCCESS)));
        DeploymentStreamHub hub = new DeploymentStreamHub(events, deployments);

        hub.subscribe(id, emitter);

        InOrder order = inOrder(events, deployments, emitter);
        order.verify(events).findLinesOldestFirst(id);
        order.verify(emitter).send(any(SseEmitter.SseEventBuilder.class));
        order.verify(deployments).findById(id);
        order.verify(emitter).complete();
        verify(emitter, never()).onCompletion(any());

        clearInvocations(events, emitter);
        hub.append(id, "late");
        verify(events).append(id, "late");
        verifyNoInteractions(emitter);
    }

    @Test
    void completeFansOutAndRemovesSubscribers() throws Exception {
        UUID id = UUID.randomUUID();
        DeploymentEventStore events = mock(DeploymentEventStore.class);
        DeploymentStore deployments = mock(DeploymentStore.class);
        SseEmitter first = mock(SseEmitter.class);
        SseEmitter second = mock(SseEmitter.class);
        when(events.findLinesOldestFirst(id)).thenReturn(List.of());
        when(deployments.findById(id)).thenReturn(Optional.of(deployment(id, DeploymentStatus.RUNNING)));
        DeploymentStreamHub hub = new DeploymentStreamHub(events, deployments);
        hub.subscribe(id, first);
        hub.subscribe(id, second);
        clearInvocations(events, deployments, first, second);

        hub.complete(id);

        verify(first).complete();
        verify(second).complete();
        clearInvocations(events, first, second);

        hub.append(id, "after completion");

        verify(events).append(id, "after completion");
        verifyNoInteractions(first, second);
    }

    private static String eventPayload(SseEmitter.SseEventBuilder event) {
        return event.build().stream()
                .map(item -> String.valueOf(item.getData()))
                .collect(Collectors.joining());
    }

    private static Deployment deployment(UUID id, DeploymentStatus status) {
        return new Deployment(
                id,
                "lab",
                status,
                null,
                null,
                "admin",
                null,
                null,
                null,
                null,
                "deploy");
    }
}
