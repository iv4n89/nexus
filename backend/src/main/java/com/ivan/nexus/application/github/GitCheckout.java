package com.ivan.nexus.application.github;

import java.nio.file.Path;

/**
 * Outbound port for cloning or updating a git working tree under the manifest root.
 */
public interface GitCheckout {
    CheckoutResult cloneOrUpdate(String repoUrl, String branch, Path targetDir);

    record CheckoutResult(Path directory, boolean hasNexusYml, boolean hasDeploySh) {
    }
}
