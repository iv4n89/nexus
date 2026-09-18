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
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * Runs {@code trivy fs} or {@code trivy image} via ProcessBuilder and maps JSON findings.
 */
public class TrivySecurityScanner implements SecurityScanner {
    private static final Logger log = LoggerFactory.getLogger(TrivySecurityScanner.class);

    private final ManifestCatalog manifests;
    private final NexusProperties.Security.Trivy settings;
    private final TrivyReportParser parser;
    private final Function<List<String>, ProcessResult> processRunner;

    public TrivySecurityScanner(
            ManifestCatalog manifests,
            NexusProperties.Security.Trivy settings,
            TrivyReportParser parser) {
        this(manifests, settings, parser, TrivySecurityScanner::runProcess);
    }

    TrivySecurityScanner(
            ManifestCatalog manifests,
            NexusProperties.Security.Trivy settings,
            TrivyReportParser parser,
            Function<List<String>, ProcessResult> processRunner) {
        this.manifests = manifests;
        this.settings = settings;
        this.parser = parser;
        this.processRunner = processRunner;
    }

    @Override
    public List<RawSecurityFinding> scan(String projectId) {
        List<String> argv = buildArgv(projectId);
        log.info("Running Trivy scan for project {}: {}", projectId, argv);
        ProcessResult result = processRunner.apply(argv);
        if (result.exitCode() != 0 && result.stdout().isBlank()) {
            throw new IllegalStateException(
                    "Trivy exited with code " + result.exitCode() + ": " + truncate(result.stderr()));
        }
        if (!result.stderr().isBlank() && result.exitCode() != 0) {
            log.warn("Trivy stderr for {}: {}", projectId, truncate(result.stderr()));
        }
        try (InputStream in = new ByteArrayInputStream(result.stdout().getBytes(StandardCharsets.UTF_8))) {
            return parser.parse(in);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read Trivy output", ex);
        }
    }

    List<String> buildArgv(String projectId) {
        String mode = settings.getMode() == null ? "fs" : settings.getMode().trim().toLowerCase(Locale.ROOT);
        List<String> argv = new ArrayList<>();
        argv.add(blankToDefault(settings.getExecutable(), "trivy"));
        argv.add(mode);
        argv.add("--format");
        argv.add("json");
        argv.add("--quiet");
        argv.add("--scanners");
        argv.add("vuln");
        if ("image".equals(mode)) {
            argv.add(resolveImageRef(projectId));
        } else {
            argv.add(resolveFsPath(projectId).toString());
        }
        return List.copyOf(argv);
    }

    private Path resolveFsPath(String projectId) {
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

    private String resolveImageRef(String projectId) {
        String template = settings.getImageRef();
        if (template == null || template.isBlank()) {
            throw new IllegalStateException(
                    "nexus.security.trivy.image-ref is required when mode=image");
        }
        return template.replace("{projectId}", projectId);
    }

    private static ProcessResult runProcess(List<String> argv) {
        ProcessBuilder builder = new ProcessBuilder(argv);
        builder.redirectErrorStream(false);
        Process process;
        try {
            process = builder.start();
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to start Trivy process: " + argv.getFirst(), ex);
        }
        try {
            String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
            boolean finished = process.waitFor(Duration.ofMinutes(10).toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new IllegalStateException("Trivy timed out");
            }
            return new ProcessResult(process.exitValue(), stdout, stderr);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new IllegalStateException("Trivy interrupted", ex);
        } catch (IOException ex) {
            process.destroyForcibly();
            throw new IllegalStateException("Failed to read Trivy process output", ex);
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
