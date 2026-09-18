package com.ivan.nexus.application.backup;

import com.ivan.nexus.application.activity.RecordActivity;
import com.ivan.nexus.domain.activity.ActivityType;
import com.ivan.nexus.domain.backup.Backup;
import com.ivan.nexus.domain.backup.BackupKind;
import com.ivan.nexus.domain.backup.BackupStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RunBackupTest {

    @Mock
    BackupProvider provider;
    @Mock
    BackupStore store;
    @Mock
    RecordActivity recordActivity;

    @Test
    void successPersistsRecordAndActivity() {
        given(provider.dumpPostgres("lab")).willReturn(
                new PostgresDumpResult("file:///tmp/lab.dump", 42L, "app", "ok"));
        given(store.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        Backup result = new RunBackup(provider, store, recordActivity)
                .execute("lab", BackupKind.MANUAL);

        assertThat(result.status()).isEqualTo(BackupStatus.SUCCESS);
        assertThat(result.artifactUri()).isEqualTo("file:///tmp/lab.dump");
        assertThat(result.includesDb()).isTrue();
        assertThat(result.kind()).isEqualTo(BackupKind.MANUAL);

        ArgumentCaptor<Backup> captor = ArgumentCaptor.forClass(Backup.class);
        verify(store, times(2)).save(captor.capture());
        assertThat(captor.getAllValues().get(0).status()).isEqualTo(BackupStatus.RUNNING);
        assertThat(captor.getAllValues().get(1).status()).isEqualTo(BackupStatus.SUCCESS);

        verify(recordActivity).execute(
                eq(ActivityType.BACKUP_COMPLETED),
                eq("lab"),
                eq(null),
                eq("backup completed"),
                org.mockito.ArgumentMatchers.anyMap());
    }

    @Test
    void failurePersistsFailedRecordAndActivity() {
        given(provider.dumpPostgres("lab")).willThrow(new BackupException("pg_dump missing"));
        given(store.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        Backup result = new RunBackup(provider, store, recordActivity)
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
}
