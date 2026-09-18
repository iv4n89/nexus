package com.ivan.nexus.application.backup;

import com.ivan.nexus.domain.backup.BackupPolicy;
import com.ivan.nexus.domain.shared.DomainException;
import com.ivan.nexus.domain.shared.NexusErrorCode;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GetBackupPolicyTest {

    @Test
    void returnsStoredPolicyWhenPresent() {
        BackupPolicyStore store = mock(BackupPolicyStore.class);
        BackupPolicy stored = new BackupPolicy("lab", 14, 8, 6, true, "0 3 * * *");
        when(store.findByProjectId("lab")).thenReturn(Optional.of(stored));

        assertThat(new GetBackupPolicy(store).execute("lab")).isEqualTo(stored);
    }

    @Test
    void returnsDefaultsWhenMissing() {
        BackupPolicyStore store = mock(BackupPolicyStore.class);
        when(store.findByProjectId("lab")).thenReturn(Optional.empty());

        assertThat(new GetBackupPolicy(store).execute("lab")).isEqualTo(BackupPolicy.defaults("lab"));
    }
}

class UpsertBackupPolicyTest {

    @Test
    void upsertsNormalizedPolicy() {
        BackupPolicyStore store = mock(BackupPolicyStore.class);
        BackupPolicy saved = new BackupPolicy("lab", 7, 4, 3, true, "0 3 * * *");
        when(store.upsert(any())).thenReturn(saved);

        BackupPolicy result = new UpsertBackupPolicy(store).execute(
                new BackupPolicy("lab", 7, 4, 3, true, " 0 3 * * * "));

        assertThat(result).isEqualTo(saved);
        verify(store).upsert(new BackupPolicy("lab", 7, 4, 3, true, "0 3 * * *"));
    }

    @Test
    void rejectsNegativeRetention() {
        BackupPolicyStore store = mock(BackupPolicyStore.class);

        assertThatThrownBy(() -> new UpsertBackupPolicy(store).execute(
                        new BackupPolicy("lab", -1, 4, 3, false, null)))
                .isInstanceOf(DomainException.class)
                .extracting(ex -> ((DomainException) ex).getCode())
                .isEqualTo(NexusErrorCode.OPERATION_NOT_ALLOWED);

        verify(store, never()).upsert(any());
    }
}

class ListBackupsTest {

    @Test
    void delegatesToStore() {
        BackupStore store = mock(BackupStore.class);
        when(store.findByProjectIdNewestFirst("lab")).thenReturn(java.util.List.of());

        assertThat(new ListBackups(store).execute("lab")).isEmpty();
        verify(store).findByProjectIdNewestFirst("lab");
    }
}
