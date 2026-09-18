package com.ivan.nexus.infrastructure.scanner;

import com.ivan.nexus.application.manifest.LoadedManifest;
import com.ivan.nexus.application.manifest.ManifestCatalog;
import com.ivan.nexus.application.security.RawSecurityFinding;
import com.ivan.nexus.application.security.SecurityScanner;
import com.ivan.nexus.infrastructure.config.NexusProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * Runs {@code npm audit --json} when {@code package.json} exists for the project.
 * Tests can inject a process runner or a canned JSON supplier (OSV/npm stub).
 */
public class OsvNpmSecurityScanner implements SecurityScanner {
    private static final Logger log = LoggerFactory.getLogger(OsvNpmSecurityScanner.class);

    private final ManifestCatalog manifests;
    private final NexusProperties.Security.NpmAudit settings;
    private final NpmAuditReportParser parser;
    private final Function<List<String>, ProcessResult> processRunner;
    private final Function<Path, String> auditJsonSupplier;

    public OsvNpmSecurityScanner(
            ManifestCatalog manifests,
            NexusProperties.Security.NpmAudit settings,
            NpmAuditReportParser parser) {
        this(manifests, settings, parser, OsvNpmSecurityScanner::runProcess, null);
    }

    OsvNpmSecurityScanner(
            ManifestCatalog manifests,
            NexusProperties.Security.NpmAudit settings,
            NpmAuditReportParser parser,
            Function<List<String>, ProcessResult> processRunner) {
        this(manifests, settings, parser, processRunner, null);
    }

    /**
     * @param auditJsonSupplier optional stub that returns npm-audit JSON without shelling out
     *                          (used for tests / fake OSV responses)
     */
    OsvNpmSecurityScanner(
            ManifestCatalog manifests,
            NexusProperties.Security.NpmAudit settings,
            NpmAuditReportParser parser,
            Function<List<String>, ProcessResult> processRunner,
            Function<Path, String> auditJsonSupplier) {
        this.manifests = manifests;
        this.settings = settings;
        this.parser = parser;
        this.processRunner = processRunner;
        this.auditJsonSupplier = auditJsonSupplier;
    }

    @Override
    public List<RawSecurityFinding> scan(String projectId) {
        Path projectDir = resolveProjectDir(projectId);
        Path packageJson = projectDir.resolve("package.json");
        if (!Files.isRegularFile(packageJson)) {
            log.debug("Skipping npm audit for {}: no package.json at {}", projectId, packageJson);
            return List.of();
        }

        String json;
        if (auditJsonSupplier != null) {
            json = auditJsonSupplier.apply(projectDir);
        } else {
            List<String> argv = buildArgv();
            log.info("Running npm audit for project {}: {}", projectId, argv);
            ProcessResult result = processRunner.apply(argv);
            // npm audit exits non-zero when vulnerabilities exist; still parse stdout
            if (result.stdout().isBlank()) {
                throw new IllegalStateException(
                        "npm audit exited with code " + result.exitCode() + ": " + truncate(result.stderr()));
            }
            if (!result.stderr().isBlank() && result.exitCode() > 1) {
                log.warn("npm audit stderr for {}: {}", projectId, truncate(result.stderr()));
            }
            json = result.stdout();
        }

        try (InputStream in = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8))) {
            return parser.parse(in);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read npm audit output", ex);
        }
    }

    List<String> buildArgv() {
        List<String> argv = new ArrayList<>();
        argv.add(blankToDefault(settings.getExecutable(), "npm"));
        argv.add("audit");
        argv.add("--json");
        return List.copyOf(argv);
    }

    private Path resolveProjectDir(String projectId) {
        LoadedManifest loaded = manifests.loadRequired(projectId);
        String workingDirectory = loaded.manifest().project().workingDirectory();
        if (workingDirectory != null && !workingDirectory.isBlank()) {
            Path path = Path.of(workingDirectory).toAbsolutePath().normalize();
            if (Files.isDirectory(path)) {
                return path;
            }
        }
        return loaded.manifestPath().getParent();
    }

    private static ProcessResult runProcess(List<String> argv) {
        ProcessBuilder builder = new ProcessBuilder(argv);
        builder.redirectErrorStream(false);
        Process process;
        try {
            process = builder.start();
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to start npm process: " + argv.getFirst(), ex);
        }
        try {
            String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
            boolean finished = process.waitFor(Duration.ofMinutes(5).toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new IllegalStateException("npm audit timed out");
            }
            return new ProcessResult(process.exitValue(), stdout, stderr);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new IllegalStateException("npm audit interrupted", ex);
        } catch (IOException ex) {
            process.destroyForcibly();
            throw new IllegalStateException("Failed to read npm audit process output", ex);
        }
    }

    private static String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String truncate(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.length() <= 500 ? trimmed : trimmed.substring(0, 500) + "...";
    }

    record ProcessResult(int exitCode, String stdout, String stderr) {}
}
