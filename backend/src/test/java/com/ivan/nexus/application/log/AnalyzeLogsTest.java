package com.ivan.nexus.application.log;

import com.ivan.nexus.application.project.ContainerInventory;
import com.ivan.nexus.application.project.DiscoverProjects;
import com.ivan.nexus.application.activity.RecordActivity;
import com.ivan.nexus.domain.activity.ActivityType;
import com.ivan.nexus.domain.container.ContainerSnapshot;
import com.ivan.nexus.domain.log.ErrorNormalizer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AnalyzeLogsTest {

    @Mock
    LogProvider logProvider;

    @Mock
    FingerprintStore fingerprints;

    @Mock
    RecordActivity recordActivity;

    @Test
    void insertsNewFingerprintForErrorLine() {
        given(logProvider.fetch(eq("web-id"), eq(2000), anyInt(), isNull(), eq(false)))
                .willReturn(List.of("Connection to 10.0.0.31 failed at 12:42"));
        given(fingerprints.findByProjectIdAndServiceIdAndFingerprint(eq("lab"), eq("web"), any()))
                .willReturn(Optional.empty());
        given(fingerprints.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        analyze("web").execute();

        ArgumentCaptor<StoredErrorFingerprint> captor = ArgumentCaptor.forClass(StoredErrorFingerprint.class);
        verify(fingerprints).save(captor.capture());
        StoredErrorFingerprint saved = captor.getValue();
        assertThat(saved.projectId()).isEqualTo("lab");
        assertThat(saved.serviceId()).isEqualTo("web");
        assertThat(saved.count()).isEqualTo(1L);
        assertThat(saved.sampleMessage()).isEqualTo("Connection to 10.0.0.31 failed at 12:42");
        assertThat(saved.fingerprint()).isEqualTo(
                ErrorNormalizer.normalize("Connection to 10.0.0.31 failed at 12:42").orElseThrow().fingerprint());
        assertThat(saved.firstSeen()).isNotNull();
        assertThat(saved.lastSeen()).isEqualTo(saved.firstSeen());
        verify(recordActivity).execute(
                eq(ActivityType.ERROR_DETECTED), eq("lab"), eq("web"), eq("error detected"), any());
    }

    @Test
    void incrementsExistingFingerprintAndUpdatesLastSeen() {
        String line = "ERROR boom";
        String hash = ErrorNormalizer.normalize(line).orElseThrow().fingerprint();
        Instant firstSeen = Instant.parse("2026-01-01T00:00:00Z");
        StoredErrorFingerprint existing = new StoredErrorFingerprint(
                UUID.randomUUID(), "lab", "web", hash, firstSeen, firstSeen, 3L, line);
        given(logProvider.fetch(eq("web-id"), eq(2000), anyInt(), isNull(), eq(false)))
                .willReturn(List.of(line));
        given(fingerprints.findByProjectIdAndServiceIdAndFingerprint("lab", "web", hash))
                .willReturn(Optional.of(existing));

        analyze("web").execute();

        ArgumentCaptor<StoredErrorFingerprint> captor = ArgumentCaptor.forClass(StoredErrorFingerprint.class);
        verify(fingerprints).save(captor.capture());
        StoredErrorFingerprint saved = captor.getValue();
        assertThat(saved.id()).isEqualTo(existing.id());
        assertThat(saved.count()).isEqualTo(4L);
        assertThat(saved.lastSeen()).isAfter(firstSeen);
        assertThat(saved.firstSeen()).isEqualTo(firstSeen);
        assertThat(existing.count()).isEqualTo(3L);
        verify(recordActivity, never()).execute(any(), any(), any(), any(), any());
    }

    @Test
    void skipsNonErrorLines() {
        given(logProvider.fetch(eq("web-id"), eq(2000), anyInt(), isNull(), eq(false)))
                .willReturn(List.of("INFO started", "request completed"));

        analyze("web").execute();

        verify(fingerprints, never()).save(any());
        verify(fingerprints, never()).findByProjectIdAndServiceIdAndFingerprint(any(), any(), any());
    }

    @Test
    void skipsStoppedContainers() {
        ContainerSnapshot stopped = new ContainerSnapshot(
                "nginx-id",
                "nexus-nginx-1",
                "nginx:alpine",
                "Exited (1)",
                "exited",
                null,
                Instant.parse("2026-01-01T00:00:00Z"),
                Map.of("com.docker.compose.project", "nexus", "com.docker.compose.service", "nginx"),
                List.of(),
                0,
                Instant.parse("2026-01-01T00:00:01Z"));
        AnalyzeLogs analyzeLogs = new AnalyzeLogs(
                new DiscoverProjects(inventory(stopped), "/tmp/nexus-no-manifests"),
                logProvider,
                fingerprints,
                recordActivity,
                120);

        analyzeLogs.execute();

        verify(logProvider, never()).fetch(any(), anyInt(), any(), any(), anyBoolean());
        verify(fingerprints, never()).save(any());
    }

    @Test
    void subsequentScanUsesWatermarkInsteadOfFullWindow() {
        given(logProvider.fetch(eq("web-id"), eq(2000), anyInt(), isNull(), eq(false)))
                .willReturn(List.of("ERROR boom"));
        given(fingerprints.findByProjectIdAndServiceIdAndFingerprint(eq("lab"), eq("web"), any()))
                .willReturn(Optional.empty());
        given(fingerprints.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        AnalyzeLogs analyzeLogs = analyze("web");
        analyzeLogs.execute();

        ArgumentCaptor<Integer> firstSince = ArgumentCaptor.forClass(Integer.class);
        verify(logProvider).fetch(eq("web-id"), eq(2000), firstSince.capture(), isNull(), eq(false));
        int windowStart = firstSince.getValue();
        int nowAfterFirst = (int) Instant.now().getEpochSecond();
        assertThat(nowAfterFirst - windowStart).isBetween(119, 122);

        analyzeLogs.execute();

        ArgumentCaptor<Integer> secondSince = ArgumentCaptor.forClass(Integer.class);
        verify(logProvider, times(2))
                .fetch(eq("web-id"), eq(2000), secondSince.capture(), isNull(), eq(false));
        assertThat(secondSince.getAllValues().get(1)).isGreaterThan(windowStart);
        assertThat(secondSince.getAllValues().get(1)).isGreaterThanOrEqualTo(nowAfterFirst - 2);
    }

    @Test
    void failedFetchDoesNotAdvanceWatermark() {
        given(logProvider.fetch(eq("web-id"), eq(2000), anyInt(), isNull(), eq(false)))
                .willThrow(new RuntimeException("docker timeout"))
                .willReturn(List.of("ERROR boom"));
        given(fingerprints.findByProjectIdAndServiceIdAndFingerprint(eq("lab"), eq("web"), any()))
                .willReturn(Optional.empty());
        given(fingerprints.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        AnalyzeLogs analyzeLogs = analyze("web");
        analyzeLogs.execute();

        ArgumentCaptor<Integer> firstSince = ArgumentCaptor.forClass(Integer.class);
        verify(logProvider).fetch(eq("web-id"), eq(2000), firstSince.capture(), isNull(), eq(false));
        int windowStart = firstSince.getValue();
        verify(fingerprints, never()).save(any());

        analyzeLogs.execute();

        ArgumentCaptor<Integer> secondSince = ArgumentCaptor.forClass(Integer.class);
        verify(logProvider, times(2))
                .fetch(eq("web-id"), eq(2000), secondSince.capture(), isNull(), eq(false));
        assertThat(secondSince.getAllValues().get(1)).isEqualTo(windowStart);
        verify(fingerprints).save(any());
    }

    @Test
    void usesEmptyServiceIdWhenGroupingHasNone() {
        given(logProvider.fetch(eq("solo-id"), eq(2000), anyInt(), isNull(), eq(false)))
                .willReturn(List.of("FATAL crash"));
        given(fingerprints.findByProjectIdAndServiceIdAndFingerprint(eq("orphan"), eq("orphan"), any()))
                .willReturn(Optional.empty());
        given(fingerprints.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        ContainerSnapshot snapshot = new ContainerSnapshot(
                "solo-id",
                "orphan",
                "nginx:alpine",
                "Up",
                "running",
                null,
                Instant.parse("2026-01-01T00:00:00Z"),
                Map.of(),
                List.of(),
                0,
                Instant.parse("2026-01-01T00:00:01Z"));
        AnalyzeLogs analyzeLogs = new AnalyzeLogs(
                new DiscoverProjects(inventory(snapshot), "/tmp/nexus-no-manifests"),
                logProvider,
                fingerprints,
                recordActivity,
                120);
        analyzeLogs.execute();

        ArgumentCaptor<StoredErrorFingerprint> captor = ArgumentCaptor.forClass(StoredErrorFingerprint.class);
        verify(fingerprints).save(captor.capture());
        assertThat(captor.getValue().serviceId()).isEqualTo("orphan");
    }

    private AnalyzeLogs analyze(String service) {
        return new AnalyzeLogs(
                new DiscoverProjects(inventory(snapshot(service)), "/tmp/nexus-no-manifests"),
                logProvider,
                fingerprints,
                recordActivity,
                120);
    }

    private static ContainerInventory inventory(ContainerSnapshot... snapshots) {
        List<ContainerSnapshot> all = List.of(snapshots);
        return new ContainerInventory() {
            @Override
            public List<ContainerSnapshot> listAll() {
                return all;
            }

            @Override
            public Optional<ContainerSnapshot> findById(String containerId) {
                return all.stream().filter(snapshot -> snapshot.id().equals(containerId)).findFirst();
            }
        };
    }

    private static ContainerSnapshot snapshot(String service) {
        return new ContainerSnapshot(
                service + "-id",
                "lab-" + service + "-1",
                "nginx:alpine",
                "Up",
                "running",
                "healthy",
                Instant.parse("2026-01-01T00:00:00Z"),
                Map.of("nexus.project", "lab", "nexus.service", service),
                List.of(),
                0,
                Instant.parse("2026-01-01T00:00:01Z"));
    }
}
