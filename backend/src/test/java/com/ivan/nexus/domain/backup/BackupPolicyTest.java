package com.ivan.nexus.domain.backup;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BackupPolicyTest {

    @Test
    void defaultsMatchRoadmapRetention() {
        assertThat(BackupPolicy.defaults("lab")).isEqualTo(new BackupPolicy("lab", 7, 4, 3, false, null));
    }
}
