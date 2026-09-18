package com.ivan.nexus.domain.database;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DotEnvCredentialMergerTest {

    @Test
    void containerValuesOverrideBlankProjectEntriesButProjectFillsMissingPassword() {
        Map<String, String> merged = DotEnvCredentialMerger.merge(
                Map.of("POSTGRES_USER", "lab", "POSTGRES_DB", "lab"),
                Map.of("POSTGRES_PASSWORD", "from-file", "POSTGRES_USER", "ignored-when-blank-container"));

        ParsedCredentials credentials = DotEnvCredentialMerger.parseWithFallback(DatabaseEngine.POSTGRES, merged);
        assertThat(credentials.username()).isEqualTo("lab");
        assertThat(credentials.password()).isEqualTo("from-file");
        assertThat(credentials.reachable()).isTrue();
    }

    @Test
    void parsesPostgresDatabaseUrl() {
        Map<String, String> merged = DotEnvCredentialMerger.merge(
                Map.of(),
                Map.of("DATABASE_URL", "postgres://app:s3cret@db.example.com:5433/appdb"));

        ParsedCredentials credentials = DotEnvCredentialMerger.parseWithFallback(DatabaseEngine.POSTGRES, merged);
        assertThat(credentials.username()).isEqualTo("app");
        assertThat(credentials.password()).isEqualTo("s3cret");
        assertThat(credentials.defaultDatabase()).isEqualTo("appdb");

        DotEnvCredentialMerger.HostHint hint = DotEnvCredentialMerger.hostHint(merged);
        assertThat(hint.host()).isEqualTo("db.example.com");
        assertThat(hint.port()).isEqualTo(5433);
        assertThat(DotEnvCredentialMerger.isRoutableHost("db.example.com")).isTrue();
        assertThat(DotEnvCredentialMerger.isRoutableHost("postgres")).isFalse();
    }
}
