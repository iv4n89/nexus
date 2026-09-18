package com.ivan.nexus.application.backup;

import com.ivan.nexus.application.activity.RecordActivity;
import com.ivan.nexus.application.audit.RecordAudit;
import com.ivan.nexus.application.manifest.FakeManifestCatalog;
import com.ivan.nexus.application.user.UserDirectory;
import com.ivan.nexus.domain.activity.ActivityType;
import com.ivan.nexus.domain.audit.AuditAction;
import com.ivan.nexus.domain.backup.Backup;
import com.ivan.nexus.domain.backup.BackupKind;
import com.ivan.nexus.domain.backup.BackupStatus;
import com.ivan.nexus.domain.manifest.ProjectManifest;
import com.ivan.nexus.domain.shared.DomainException;
import com.ivan.nexus.domain.shared.NexusErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RestoreBackupTest {

    @Mock
    BackupStore store;
    @Mock
    RunBackup runBackup;
    @Mock
    BackupRestoreProvider restoreProvider;
    @Mock
    PostRestoreHealthCheck healthCheck;
    @Mock
    RecordActivity recordActivity;
    @Mock
    RecordAudit recordAudit;
    @Mock
    UserDirectory users;

    @TempDir
    Path tempDir;

    @Test
    void requiresConfirmation() {
        RestoreBackup useCase = newUseCase(new FakeManifestCatalog());

        assertThatThrownBy(() -> useCase.execute("lab", UUID.randomUUID(), false, "admin", "127.0.0.1"))
                .isInstanceOf(DomainException.class)
                .satisfies(ex -> assertThat(((DomainException) ex).getCode())
                        .isEqualTo(NexusErrorCode.CONFIRMATION_REQUIRED));
    }

    @Test
    void createsSafetyBackupRestoresAndAudits() {
        UUID backupId = UUID.randomUUID();
        Backup target = new Backup(
                backupId,
                "lab",
                BackupStatus.SUCCESS,
                BackupKind.SCHEDULED,
                Instant.parse("2026-09-18T03:00:00Z"),
                Instant.parse("2026-09-18T03:01:00Z"),
                "file:///tmp/lab.dump",
                "ok",
                true,
                false);
        Backup safety = new Backup(
                UUID.randomUUID(),
                "lab",
                BackupStatus.SUCCESS,
                BackupKind.SAFETY,
                Instant.parse("2026-09-18T12:00:00Z"),
                Instant.parse("2026-09-18T12:01:00Z"),
                "file:///tmp/safety.dump",
                "safety",
                true,
                false);
        FakeManifestCatalog manifests = new FakeManifestCatalog().add(
                "lab",
                new ProjectManifest(
                        new ProjectManifest.ProjectBlock("lab", "Lab", null, tempDir.toString()),
                        List.of("api"),
                        new ProjectManifest.CommandBlock("./deploy.sh"),
                        null,
                        new ProjectManifest.HealthBlock("http://127.0.0.1:18080", 5),
                        null,
                        null),
                tempDir.resolve("nexus.yml"));
        given(store.findById(backupId)).willReturn(Optional.of(target));
        given(runBackup.execute("lab", BackupKind.SAFETY)).willReturn(safety);
        given(healthCheck.verify("lab")).willReturn(true);
        given(users.findIdByUsername("admin")).willReturn(Optional.empty());

        RestoreBackup.RestoreResult result = newUseCase(manifests)
                .execute("lab", backupId, true, "admin", "10.0.0.1");

        assertThat(result.safetyBackup()).isEqualTo(safety);
        assertThat(result.healthy()).isTrue();
        verify(restoreProvider).restorePostgres("lab", "file:///tmp/lab.dump");
        verify(recordAudit).execute(
                eq(null),
                eq(AuditAction.BACKUP_RESTORE),
                eq("lab"),
                eq(null),
                eq("10.0.0.1"),
                any());
        verify(recordActivity).execute(
                eq(ActivityType.BACKUP_RESTORED),
                eq("lab"),
                eq(null),
                eq("backup restored"),
                any());
    }

    private RestoreBackup newUseCase(FakeManifestCatalog manifests) {
        return new RestoreBackup(
                store, runBackup, restoreProvider, healthCheck, manifests, recordActivity, recordAudit, users);
    }
}
