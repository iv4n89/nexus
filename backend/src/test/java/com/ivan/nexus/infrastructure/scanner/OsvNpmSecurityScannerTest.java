package com.ivan.nexus.infrastructure.scanner;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ivan.nexus.application.manifest.LoadedManifest;
import com.ivan.nexus.application.manifest.ManifestCatalog;
import com.ivan.nexus.application.security.RawSecurityFinding;
import com.ivan.nexus.domain.manifest.ProjectManifest;
import com.ivan.nexus.domain.security.SecuritySeverity;
import com.ivan.nexus.infrastructure.config.NexusProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class OsvNpmSecurityScannerTest {

    @TempDir
    Path tempDir;

    @Mock
    ManifestCatalog manifests;

    private NexusProperties.Security.NpmAudit settings;
    private NpmAuditReportParser parser;
    private String fixtureJson;

    @BeforeEach
    void setUp() throws Exception {
        settings = new NexusProperties.Security.NpmAudit();
        settings.setExecutable("npm");
        parser = new NpmAuditReportParser(new ObjectMapper());
        fixtureJson = Files.readString(
                Path.of(getClass().getResource("/npm-audit/sample-report.json").toURI()));
    }

    @Test
    void runsNpmAuditWhenPackageJsonExists() throws Exception {
        Path projectDir = tempDir.resolve("lab");
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("package.json"), "{\"name\":\"lab\"}");
        Path manifestPath = projectDir.resolve("nexus.yml");
        Files.writeString(manifestPath, "project:\n  id: lab\n");
        given(manifests.loadRequired("lab")).willReturn(new LoadedManifest(
                new ProjectManifest(
                        new ProjectManifest.ProjectBlock("lab", "Lab", null, projectDir.toString()),
                        List.of(),
                        null,
                        null,
                        null,
                        null),
                manifestPath));

        AtomicReference<List<String>> captured = new AtomicReference<>();
        OsvNpmSecurityScanner scanner = new OsvNpmSecurityScanner(
                manifests,
                settings,
                parser,
                argv -> {
                    captured.set(argv);
                    return new OsvNpmSecurityScanner.ProcessResult(1, fixtureJson, "");
                });

        List<RawSecurityFinding> findings = scanner.scan("lab");

        assertThat(captured.get()).containsExactly("npm", "audit", "--json");
        assertThat(findings).hasSize(2);
        assertThat(findings.getFirst().severity()).isEqualTo(SecuritySeverity.HIGH);
        assertThat(findings.getFirst().source()).isEqualTo("npm-audit");
    }

    @Test
    void returnsEmptyWhenNoPackageJson() throws Exception {
        Path projectDir = tempDir.resolve("lab");
        Files.createDirectories(projectDir);
        Path manifestPath = projectDir.resolve("nexus.yml");
        Files.writeString(manifestPath, "project:\n  id: lab\n");
        given(manifests.loadRequired("lab")).willReturn(new LoadedManifest(
                new ProjectManifest(
                        new ProjectManifest.ProjectBlock("lab", "Lab", null, projectDir.toString()),
                        List.of(),
                        null,
                        null,
                        null,
                        null),
                manifestPath));

        OsvNpmSecurityScanner scanner = new OsvNpmSecurityScanner(
                manifests,
                settings,
                parser,
                argv -> new OsvNpmSecurityScanner.ProcessResult(0, fixtureJson, ""));

        assertThat(scanner.scan("lab")).isEmpty();
    }

    @Test
    void supportsFakeOsvJsonSupplierForTests() throws Exception {
        Path projectDir = tempDir.resolve("lab");
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("package.json"), "{}");
        Path manifestPath = projectDir.resolve("nexus.yml");
        Files.writeString(manifestPath, "project:\n  id: lab\n");
        given(manifests.loadRequired("lab")).willReturn(new LoadedManifest(
                new ProjectManifest(
                        new ProjectManifest.ProjectBlock("lab", "Lab", null, projectDir.toString()),
                        List.of(),
                        null,
                        null,
                        null,
                        null),
                manifestPath));

        OsvNpmSecurityScanner scanner = new OsvNpmSecurityScanner(
                manifests,
                settings,
                parser,
                argv -> {
                    throw new AssertionError("process should not run");
                },
                dir -> fixtureJson);

        assertThat(scanner.scan("lab")).hasSize(2);
    }
}
