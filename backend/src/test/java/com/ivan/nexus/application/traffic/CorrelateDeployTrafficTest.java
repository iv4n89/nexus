package com.ivan.nexus.application.traffic;

import com.ivan.nexus.application.activity.RecordActivity;
import com.ivan.nexus.application.deployment.DeploymentStore;
import com.ivan.nexus.domain.activity.ActivityType;
import com.ivan.nexus.domain.deployment.Deployment;
import com.ivan.nexus.domain.deployment.DeploymentStatus;
import com.ivan.nexus.domain.traffic.DeployTrafficDelta;
import com.ivan.nexus.domain.traffic.TrafficSnapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CorrelateDeployTrafficTest {

    @Mock
    DeploymentStore deployments;
    @Mock
    TrafficStore traffic;
    @Mock
    RecordActivity recordActivity;

    @Test
    void comparesBeforeAfterWindowsAndRecordsActivity() {
        UUID deploymentId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        Instant finished = Instant.parse("2026-09-18T12:00:00Z");
        Deployment deployment = new Deployment(
                deploymentId,
                "lab",
                DeploymentStatus.SUCCESS,
                Instant.parse("2026-09-18T11:55:00Z"),
                finished,
                "admin",
                "abc123",
                0,
                "ok",
                true,
                "manual");
        given(deployments.findById(deploymentId)).willReturn(Optional.of(deployment));

        TrafficSnapshot before = new TrafficSnapshot(
                "lab",
                Instant.parse("2026-09-18T11:00:00Z"),
                finished,
                100,
                0,
                0,
                95,
                0,
                0,
                1,
                40.0,
                50.0,
                List.of());
        TrafficSnapshot after = new TrafficSnapshot(
                "lab",
                finished,
                Instant.parse("2026-09-18T13:00:00Z"),
                200,
                0,
                0,
                180,
                0,
                0,
                10,
                80.0,
                120.0,
                List.of());
        given(traffic.snapshot(eq("lab"), any(), eq(finished))).willReturn(before);
        given(traffic.snapshot(eq("lab"), eq(finished), any())).willReturn(after);

        Clock clock = Clock.fixed(Instant.parse("2026-09-18T14:00:00Z"), ZoneOffset.UTC);
        DeployTrafficDelta delta = new CorrelateDeployTraffic(deployments, traffic, recordActivity, clock)
                .execute("lab", deploymentId);

        assertThat(delta.afterStatus5xx()).isEqualTo(10);
        assertThat(delta.beforeStatus5xx()).isEqualTo(1);
        assertThat(delta.latencyP95DeltaMs()).isEqualTo(70.0);
        assertThat(delta.trafficDeltaPercent()).isEqualTo(100.0);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> metadata = ArgumentCaptor.forClass(Map.class);
        verify(recordActivity).execute(
                eq(ActivityType.TRAFFIC_DEPLOY_DELTA),
                eq("lab"),
                eq(null),
                eq("post-deploy traffic delta"),
                metadata.capture());
        assertThat(metadata.getValue()).containsEntry("deploymentId", deploymentId.toString());
    }

    @Test
    void usesLatestSuccessfulDeploymentWhenIdOmitted() {
        UUID deploymentId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        Instant finished = Instant.parse("2026-09-18T10:00:00Z");
        given(deployments.findProjectHistoryNewestFirst("lab")).willReturn(List.of(
                new Deployment(
                        deploymentId,
                        "lab",
                        DeploymentStatus.SUCCESS,
                        finished.minusSeconds(60),
                        finished,
                        "admin",
                        "def",
                        0,
                        "ok",
                        true,
                        "manual")));
        TrafficSnapshot empty = new TrafficSnapshot(
                "lab", finished.minusSeconds(3600), finished, 0, 0, 0, 0, 0, 0, 0, 0.0, null, List.of());
        given(traffic.snapshot(eq("lab"), any(), any())).willReturn(empty);

        Clock clock = Clock.fixed(Instant.parse("2026-09-18T12:00:00Z"), ZoneOffset.UTC);
        DeployTrafficDelta delta = new CorrelateDeployTraffic(deployments, traffic, recordActivity, clock)
                .execute("lab", null);

        assertThat(delta.deploymentId()).isEqualTo(deploymentId);
        verify(recordActivity).execute(
                eq(ActivityType.TRAFFIC_DEPLOY_DELTA),
                eq("lab"),
                eq(null),
                eq("post-deploy traffic delta"),
                any());
    }
}
