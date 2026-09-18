package com.ivan.nexus.domain.site;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class DomainHostExtractorTest {

    @Test
    void extractsFromEnvKeysAndCsv() {
        assertThat(DomainHostExtractor.fromEnv(Map.of(
                "DOMAIN", "App.Example.COM",
                "DOMAINS", "a.example.com, b.example.com",
                "VIRTUAL_HOST", "https://virt.example.com:443/path",
                "HOST", "not a host!!!")))
                .containsExactly("app.example.com", "a.example.com", "b.example.com", "virt.example.com");
    }

    @Test
    void extractsFromCaddyTraefikAndNexusLabels() {
        assertThat(DomainHostExtractor.fromLabels(Map.of(
                "nexus.domain", "nexus.example.com",
                "caddy", "caddy.example.com",
                "traefik.http.routers.app.rule", "Host(`traefik.example.com`)")))
                .contains("nexus.example.com", "caddy.example.com", "traefik.example.com");
    }

    @Test
    void mergePreferFirstKeepsEnvOrder() {
        Map<String, String> merged = DomainHostExtractor.mergePreferFirst(
                Set.of("a.example.com"),
                Set.of("a.example.com", "b.example.com"));
        assertThat(merged.keySet()).containsExactly("a.example.com", "b.example.com");
    }
}
