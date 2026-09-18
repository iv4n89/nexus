package com.ivan.nexus.application.backup;

import com.ivan.nexus.application.alert.AlertRuleStore;
import com.ivan.nexus.application.alert.AlertStore;
import com.ivan.nexus.application.manifest.FakeManifestCatalog;
import com.ivan.nexus.domain.alert.Alert;
import com.ivan.nexus.domain.alert.AlertKey;
import com.ivan.nexus.domain.alert.AlertRule;
import com.ivan.nexus.domain.alert.AlertType;
import com.ivan.nexus.domain.backup.Backup;
import com.ivan.nexus.domain.backup.BackupKind;
import com.ivan.nexus.domain.backup.BackupPolicy;
import com.ivan.nexus.domain.backup.BackupStatus;
import com.ivan.nexus.domain.manifest.ProjectManifest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EnforceBackupRetentionTest {

    @Mock
    BackupPolicyStore policies;
    @Mock
    BackupStore store;
    @Mock
    BackupStorage storage;
    @Mock
    AlertStore alerts;
    @Mock
    AlertRuleStore rules;

    @TempDir
    Path tempDir;

    private final Instant now = Instant.parse("2026-09-18T12:00:00Z");
    private final Clock clock = Clock.fixed(now, ZoneOffset.UTC);

    @Test
    void opensFailedAndStaleAlertsAndDeletesExcess() {
        FakeManifestCatalog manifests = new FakeManifestCatalog().add(
                "lab",
                new ProjectManifest(
                        new ProjectManifest.ProjectBlock("lab", "Lab", null, tempDir.toString()),
                        List.of(),
                        new ProjectManifest.CommandBlock("./deploy.sh"),
                        null,
                        null,
                        null),
                tempDir.resolve("nexus.yml"));
        UUID keep = UUID.randomUUID();
        UUID drop = UUID.randomUUID();
        Backup failed = backup(UUID.randomUUID(), BackupStatus.FAILED, Instant.parse("2026-09-18T11:00:00Z"), null);
        Backup newest = backup(keep, BackupStatus.SUCCESS, Instant.parse("2026-09-16T03:00:00Z"), "file:///a");
        Backup older = backup(drop, BackupStatus.SUCCESS, Instant.parse("2026-09-10T03:00:00Z"), "file:///b");
        given(policies.findByProjectId("lab")).willReturn(Optional.of(
                new BackupPolicy("lab", 1, 4, 3, true, null)));
        given(store.findByProjectIdNewestFirst("lab")).willReturn(List.of(failed, newest, older));
        given(alerts.findOpen(any())).willReturn(Optional.empty());
        given(rules.findEnabled()).willReturn(List.of(
                new AlertRule(UUID.randomUUID(), null, AlertType.BACKUP_FAILED, null, true),
                new AlertRule(UUID.randomUUID(), null, AlertType.BACKUP_STALE, null, true)));

        new EnforceBackupRetention(manifests, policies, store, storage, alerts, rules, clock)
                .enforceProject("lab", now);

        ArgumentCaptor<Alert> alertCaptor = ArgumentCaptor.forClass(Alert.class);
        verify(alerts, org.mockito.Mockito.atLeast(2)).open(alertCaptor.capture());
        assertThat(alertCaptor.getAllValues())
                .extracting(Alert::type)
                .contains(AlertType.BACKUP_FAILED, AlertType.BACKUP_STALE);
        verify(storage).delete("file:///b");
        verify(store).delete(drop);
        verify(store, never()).delete(keep);
    }

    private static Backup backup(UUID id, BackupStatus status, Instant createdAt, String uri) {
        return new Backup(
                id,
                "lab",
                status,
                BackupKind.SCHEDULED,
                createdAt,
                createdAt,
                uri,
                status.name(),
                true,
                false);
    }
}
