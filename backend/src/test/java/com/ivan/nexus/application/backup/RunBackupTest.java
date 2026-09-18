package com.ivan.nexus.application.backup;

import com.ivan.nexus.application.activity.RecordActivity;
import com.ivan.nexus.application.manifest.FakeManifestCatalog;
import com.ivan.nexus.domain.activity.ActivityType;
import com.ivan.nexus.domain.backup.Backup;
import com.ivan.nexus.domain.backup.BackupKind;
import com.ivan.nexus.domain.backup.BackupStatus;
import com.ivan.nexus.domain.manifest.ProjectManifest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RunBackupTest {

    @Mock
    BackupProvider provider;
    @Mock
    VolumeBackupProvider volumes;
    @Mock
    BackupStorage storage;
    @Mock
    BackupStore store;
    @Mock
    RecordActivity recordActivity;

    @TempDir
    Path tempDir;

    @Test
    void successPersistsRecordAndActivity() {
        FakeManifestCatalog manifests = new FakeManifestCatalog().add(
                "lab",
                manifest(null),
                tempDir.resolve("nexus.yml"));
        given(provider.dumpPostgres("lab")).willReturn(
                new PostgresDumpResult("file:///tmp/lab.dump", 42L, "app", "ok"));
        given(storage.store(eq("lab"), any())).willReturn("file:///tmp/lab.dump");
        given(store.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        Backup result = new RunBackup(provider, volumes, storage, store, manifests, recordActivity)
                .execute("lab", BackupKind.MANUAL);

        assertThat(result.status()).isEqualTo(BackupStatus.SUCCESS);
        assertThat(result.artifactUri()).isEqualTo("file:///tmp/lab.dump");
        assertThat(result.includesDb()).isTrue();
        assertThat(result.includesVolumes()).isFalse();
        assertThat(result.kind()).isEqualTo(BackupKind.MANUAL);

        ArgumentCaptor<Backup> captor = ArgumentCaptor.forClass(Backup.class);
        verify(store, times(2)).save(captor.capture());
        assertThat(captor.getAllValues().get(0).status()).isEqualTo(BackupStatus.RUNNING);
        assertThat(captor.getAllValues().get(1).status()).isEqualTo(BackupStatus.SUCCESS);
        verify(volumes, never()).backupVolumes(any(), any());

        verify(recordActivity).execute(
                eq(ActivityType.BACKUP_COMPLETED),
                eq("lab"),
                eq(null),
                eq("backup completed"),
                org.mockito.ArgumentMatchers.anyMap());
    }

    @Test
    void includesDeclaredVolumes() {
        FakeManifestCatalog manifests = new FakeManifestCatalog().add(
                "lab",
                manifest(new ProjectManifest.BackupBlock(List.of("lab_data"))),
                tempDir.resolve("nexus.yml"));
        given(provider.dumpPostgres("lab")).willReturn(
                new PostgresDumpResult("file:///tmp/lab.dump", 42L, "app", "ok"));
        given(volumes.backupVolumes("lab", List.of("lab_data"))).willReturn(
                new VolumeBackupResult("file:///tmp/lab-volumes.tar.gz", 10L, List.of("lab_data"), "volumes ok"));
        given(storage.store(eq("lab"), any())).willAnswer(inv -> {
            String path = inv.getArgument(1);
            return path.startsWith("file:") ? path : Path.of(path).toUri().toString();
        });
        given(store.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        Backup result = new RunBackup(provider, volumes, storage, store, manifests, recordActivity)
                .execute("lab", BackupKind.SCHEDULED);

        assertThat(result.includesDb()).isTrue();
        assertThat(result.includesVolumes()).isTrue();
        assertThat(result.summary()).contains("volumes ok");
        verify(volumes).backupVolumes("lab", List.of("lab_data"));
    }

    @Test
    void failurePersistsFailedRecordAndActivity() {
        FakeManifestCatalog manifests = new FakeManifestCatalog().add(
                "lab",
                manifest(null),
                tempDir.resolve("nexus.yml"));
        given(provider.dumpPostgres("lab")).willThrow(new BackupException("pg_dump missing"));
        given(store.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        Backup result = new RunBackup(provider, volumes, storage, store, manifests, recordActivity)
                .execute("lab", BackupKind.SCHEDULED);

        assertThat(result.status()).isEqualTo(BackupStatus.FAILED);
        assertThat(result.summary()).contains("pg_dump missing");
        assertThat(result.artifactUri()).isNull();

        verify(recordActivity).execute(
                eq(ActivityType.BACKUP_FAILED),
                eq("lab"),
                eq(null),
                eq("backup failed"),
                org.mockito.ArgumentMatchers.anyMap());
    }

    private static ProjectManifest manifest(ProjectManifest.BackupBlock backup) {
        return new ProjectManifest(
                new ProjectManifest.ProjectBlock("lab", "Lab", null, "/tmp/lab"),
                List.of("api"),
                new ProjectManifest.CommandBlock("./deploy.sh"),
                null,
                null,
                null,
                backup);
    }
}
