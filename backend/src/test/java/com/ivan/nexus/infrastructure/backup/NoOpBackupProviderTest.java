package com.ivan.nexus.infrastructure.backup;

import com.ivan.nexus.application.backup.BackupException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NoOpBackupProviderTest {

    @Test
    void rejectsDumpsWhenDisabled() {
        assertThatThrownBy(() -> new NoOpBackupProvider().dumpPostgres("lab"))
                .isInstanceOf(BackupException.class)
                .hasMessageContaining("disabled");
    }
}
