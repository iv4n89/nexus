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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class TrivySecurityScannerTest {

    @TempDir
    Path tempDir;

    @Mock
    ManifestCatalog manifests;

    private NexusProperties.Security.Trivy settings;
    private TrivyReportParser parser;
    private String fixtureJson;

    @BeforeEach
    void setUp() throws Exception {
        settings = new NexusProperties.Security.Trivy();
        settings.setExecutable("trivy");
        settings.setMode("fs");
        parser = new TrivyReportParser(new ObjectMapper());
        fixtureJson = Files.readString(
                Path.of(getClass().getResource("/trivy/sample-report.json").toURI()));
    }

    @Test
    void runsFsScanAndParsesStdout() throws Exception {
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

        AtomicReference<List<String>> captured = new AtomicReference<>();
        TrivySecurityScanner scanner = new TrivySecurityScanner(
                manifests,
                settings,
                parser,
                argv -> {
                    captured.set(argv);
                    return new TrivySecurityScanner.ProcessResult(0, fixtureJson, "");
                });

        List<RawSecurityFinding> findings = scanner.scan("lab");

        assertThat(captured.get()).containsExactly(
                "trivy", "fs", "--format", "json", "--quiet", "--scanners", "vuln", projectDir.toString());
        assertThat(findings).hasSize(3);
        assertThat(findings.getFirst().severity()).isEqualTo(SecuritySeverity.HIGH);
        assertThat(findings.getFirst().source()).isEqualTo("trivy");
    }

    @Test
    void buildsImageArgvWithProjectIdSubstitution() {
        settings.setMode("image");
        settings.setImageRef("ghcr.io/example/{projectId}:latest");

        TrivySecurityScanner scanner = new TrivySecurityScanner(
                manifests,
                settings,
                parser,
                argv -> new TrivySecurityScanner.ProcessResult(0, "{\"Results\":[]}", ""));

        assertThat(scanner.buildArgv("lab")).containsExactly(
                "trivy",
                "image",
                "--format",
                "json",
                "--quiet",
                "--scanners",
                "vuln",
                "ghcr.io/example/lab:latest");
    }

    @Test
    void failsWhenProcessExitsNonZeroWithoutStdout() {
        TrivySecurityScanner scanner = new TrivySecurityScanner(
                manifests,
                settings,
                parser,
                argv -> new TrivySecurityScanner.ProcessResult(1, "", "trivy not found"));

        given(manifests.loadRequired("lab")).willReturn(new LoadedManifest(
                new ProjectManifest(
                        new ProjectManifest.ProjectBlock("lab", "Lab", null, tempDir.toString()),
                        List.of(),
                        null,
                        null,
                        null,
                        null),
                tempDir.resolve("nexus.yml")));

        assertThatThrownBy(() -> scanner.scan("lab"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Trivy exited with code 1");
    }
}
