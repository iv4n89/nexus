package com.ivan.nexus.application.alert;

import com.ivan.nexus.application.activity.RecordActivity;
import com.ivan.nexus.domain.activity.ActivityType;
import com.ivan.nexus.domain.container.ContainerSnapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ContainerLifecycleNotifierTest {

    @Mock
    RecordActivity recordActivity;

    @Test
    void firstSeenRunningIsIgnoredUntilPrimed() {
        ContainerLifecycleNotifier notifier = new ContainerLifecycleNotifier(recordActivity);
        notifier.emitContainerActivity(grouped(snapshot("running", 0)), Map.of(), false);
        verify(recordActivity, never()).execute(any(), any(), any(), any(), any());
    }

    @Test
    void firstSeenRunningAfterPrimeRecordsStarted() {
        ContainerLifecycleNotifier notifier = new ContainerLifecycleNotifier(recordActivity);
        notifier.emitContainerActivity(grouped(snapshot("running", 0)), Map.of(), true);
        verify(recordActivity).execute(
                eq(ActivityType.CONTAINER_STARTED), eq("lab"), eq("web"), eq("started"), any());
    }

    @Test
    void restartCountIncreaseRecordsRestarted() {
        ContainerLifecycleNotifier notifier = new ContainerLifecycleNotifier(recordActivity);
        notifier.emitContainerActivity(
                grouped(snapshot("running", 2)),
                Map.of("web-id", snapshot("running", 0)),
                true);
        verify(recordActivity).execute(
                eq(ActivityType.CONTAINER_RESTARTED), eq("lab"), eq("web"), eq("restarted"), any());
    }

    @Test
    void runningToExitedRecordsStopped() {
        ContainerLifecycleNotifier notifier = new ContainerLifecycleNotifier(recordActivity);
        notifier.emitContainerActivity(
                grouped(snapshot("exited", 0)),
                Map.of("web-id", snapshot("running", 0)),
                true);
        verify(recordActivity).execute(
                eq(ActivityType.CONTAINER_STOPPED), eq("lab"), eq("web"), eq("stopped"), any());
    }

    private static Map<String, List<ContainerSnapshot>> grouped(ContainerSnapshot container) {
        return Map.of("lab", List.of(container));
    }

    private static ContainerSnapshot snapshot(String state, int restartCount) {
        return new ContainerSnapshot(
                "web-id",
                "lab-web-1",
                "nginx:alpine",
                state,
                state,
                "running".equals(state) ? "healthy" : null,
                Instant.parse("2026-01-01T00:00:00Z"),
                Map.of("nexus.project", "lab", "nexus.service", "web"),
                List.of(),
                restartCount,
                Instant.parse("2026-01-01T00:00:01Z"));
    }
}
