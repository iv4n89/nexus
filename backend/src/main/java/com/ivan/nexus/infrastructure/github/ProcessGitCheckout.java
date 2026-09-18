package com.ivan.nexus.infrastructure.github;

import com.ivan.nexus.application.github.GitCheckout;
import com.ivan.nexus.domain.shared.DomainException;
import com.ivan.nexus.domain.shared.NexusErrorCode;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
public class ProcessGitCheckout implements GitCheckout {
    private static final Duration GIT_TIMEOUT = Duration.ofMinutes(5);
    private static final String MANIFEST_FILE = "nexus.yml";
    private static final String DEPLOY_SCRIPT = "deploy.sh";

    private final Path allowedRoot;

    public ProcessGitCheckout(@Qualifier("manifestAllowedRoot") Path allowedRoot) {
        this.allowedRoot = allowedRoot.toAbsolutePath().normalize();
    }

    @Override
    public CheckoutResult cloneOrUpdate(String repoUrl, String branch, Path targetDir) {
        if (repoUrl == null || repoUrl.isBlank()) {
            throw new DomainException(NexusErrorCode.GITHUB_CHECKOUT_FAILED, "Repository URL is required");
        }
        if (branch == null || branch.isBlank()) {
            throw new DomainException(NexusErrorCode.GITHUB_CHECKOUT_FAILED, "Branch is required");
        }
        Path target = requireContainedTarget(targetDir);
        try {
            if (Files.isDirectory(target.resolve(".git"))) {
                runGit(target, List.of("fetch", "--prune", "origin"));
                runGit(target, List.of("checkout", "--force", branch));
                runGit(target, List.of("reset", "--hard", "origin/" + branch));
            } else {
                if (Files.exists(target) && !isEmptyDirectory(target)) {
                    throw new DomainException(
                            NexusErrorCode.GITHUB_CHECKOUT_FAILED,
                            "Target directory exists and is not a git repository");
                }
                Files.createDirectories(target.getParent() == null ? allowedRoot : target.getParent());
                List<String> cloneArgs = new ArrayList<>();
                cloneArgs.add("clone");
                cloneArgs.add("--branch");
                cloneArgs.add(branch);
                cloneArgs.add("--single-branch");
                cloneArgs.add(repoUrl);
                cloneArgs.add(target.toString());
                runGit(allowedRoot, cloneArgs);
            }
            ensureContainedAfterCheckout(target);
            return new CheckoutResult(
                    target,
                    Files.isRegularFile(target.resolve(MANIFEST_FILE)),
                    Files.isRegularFile(target.resolve(DEPLOY_SCRIPT)));
        } catch (DomainException ex) {
            throw ex;
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new DomainException(NexusErrorCode.GITHUB_CHECKOUT_FAILED, "Git checkout failed");
        }
    }

    private Path requireContainedTarget(Path targetDir) {
        if (targetDir == null) {
            throw new DomainException(NexusErrorCode.GITHUB_CHECKOUT_FAILED, "Target directory is required");
        }
        Path target = targetDir.toAbsolutePath().normalize();
        if (!target.startsWith(allowedRoot) || target.equals(allowedRoot)) {
            throw new DomainException(
                    NexusErrorCode.GITHUB_CHECKOUT_FAILED,
                    "Checkout target must be under the allowed root");
        }
        Path relative = allowedRoot.relativize(target);
        if (relative.getNameCount() != 1 || !isSafePathSegment(relative.getFileName().toString())) {
            throw new DomainException(
                    NexusErrorCode.GITHUB_CHECKOUT_FAILED,
                    "Checkout target must be a single project directory under the allowed root");
        }
        return target;
    }

    private void ensureContainedAfterCheckout(Path target) throws IOException {
        Path realRoot = allowedRoot.toRealPath();
        Path realTarget = target.toRealPath();
        if (!realTarget.startsWith(realRoot) || !Files.isDirectory(realTarget)) {
            throw new DomainException(
                    NexusErrorCode.GITHUB_CHECKOUT_FAILED,
                    "Checkout escaped the allowed root");
        }
    }

    private void runGit(Path workingDirectory, List<String> args)
            throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.addAll(args);
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(workingDirectory.toFile());
        builder.redirectErrorStream(true);
        Process process = builder.start();
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (output.length() > 0) {
                    output.append('\n');
                }
                output.append(line);
            }
        }
        boolean finished = process.waitFor(GIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new DomainException(NexusErrorCode.GITHUB_CHECKOUT_FAILED, "Git command timed out");
        }
        if (process.exitValue() != 0) {
            throw new DomainException(
                    NexusErrorCode.GITHUB_CHECKOUT_FAILED,
                    "Git command failed" + (output.isEmpty() ? "" : ": " + sanitizeOutput(output.toString())));
        }
    }

    private static String sanitizeOutput(String output) {
        return output.replaceAll("(?i)(x-access-token:)[^@\\s]+", "$1***");
    }

    private static boolean isEmptyDirectory(Path path) throws IOException {
        if (!Files.isDirectory(path)) {
            return false;
        }
        try (var stream = Files.list(path)) {
            return stream.findAny().isEmpty();
        }
    }

    private static boolean isSafePathSegment(String projectId) {
        if (projectId == null
                || projectId.isBlank()
                || ".".equals(projectId)
                || "..".equals(projectId)
                || projectId.contains("/")
                || projectId.contains("\\")) {
            return false;
        }
        try {
            Path path = Path.of(projectId);
            return !path.isAbsolute() && path.getNameCount() == 1;
        } catch (InvalidPathException ignored) {
            return false;
        }
    }
}
