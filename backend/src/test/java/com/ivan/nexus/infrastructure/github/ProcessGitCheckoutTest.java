package com.ivan.nexus.infrastructure.github;

import com.ivan.nexus.application.github.GitCheckout;
import com.ivan.nexus.domain.shared.DomainException;
import com.ivan.nexus.domain.shared.NexusErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProcessGitCheckoutTest {
    @TempDir
    Path tempDir;

    private Path allowedRoot;
    private ProcessGitCheckout checkout;

    @BeforeEach
    void setUp() throws Exception {
        allowedRoot = tempDir.resolve("projects");
        Files.createDirectories(allowedRoot);
        checkout = new ProcessGitCheckout(allowedRoot);
    }

    @Test
    void rejectsTargetOutsideAllowedRoot() {
        assertThatThrownBy(() -> checkout.cloneOrUpdate(
                        "https://example.com/repo.git",
                        "main",
                        tempDir.resolve("outside")))
                .isInstanceOf(DomainException.class)
                .extracting(ex -> ((DomainException) ex).getCode())
                .isEqualTo(NexusErrorCode.GITHUB_CHECKOUT_FAILED);
    }

    @Test
    void rejectsNestedTargetUnderAllowedRoot() {
        assertThatThrownBy(() -> checkout.cloneOrUpdate(
                        "https://example.com/repo.git",
                        "main",
                        allowedRoot.resolve("lab").resolve("nested")))
                .isInstanceOf(DomainException.class)
                .extracting(ex -> ((DomainException) ex).getCode())
                .isEqualTo(NexusErrorCode.GITHUB_CHECKOUT_FAILED);
    }

    @Test
    void clonesLocalBareRepositoryAndDetectsManifestFiles() throws Exception {
        Path bare = tempDir.resolve("bare.git");
        run(tempDir, "git", "init", "--bare", bare.toString());

        Path seed = tempDir.resolve("seed");
        Files.createDirectories(seed);
        run(seed, "git", "init");
        run(seed, "git", "config", "user.email", "test@example.com");
        run(seed, "git", "config", "user.name", "Test");
        Files.writeString(seed.resolve("nexus.yml"), "project:\n  id: lab\n");
        Files.writeString(seed.resolve("deploy.sh"), "#!/bin/sh\necho ok\n");
        run(seed, "git", "add", ".");
        run(seed, "git", "commit", "-m", "init");
        run(seed, "git", "branch", "-M", "main");
        run(seed, "git", "remote", "add", "origin", bare.toString());
        run(seed, "git", "push", "-u", "origin", "main");

        Path target = allowedRoot.resolve("lab");
        GitCheckout.CheckoutResult result = checkout.cloneOrUpdate(bare.toString(), "main", target);

        assertThat(result.directory()).isEqualTo(target.toAbsolutePath().normalize());
        assertThat(result.hasNexusYml()).isTrue();
        assertThat(result.hasDeploySh()).isTrue();
        assertThat(Files.isDirectory(target.resolve(".git"))).isTrue();
    }

    private static void run(Path workingDirectory, String... command) throws Exception {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(workingDirectory.toFile());
        builder.redirectErrorStream(true);
        Process process = builder.start();
        String output = new String(process.getInputStream().readAllBytes());
        int exit = process.waitFor();
        if (exit != 0) {
            throw new IllegalStateException("Command failed (" + exit + "): " + String.join(" ", command)
                    + "\n" + output);
        }
    }
}
