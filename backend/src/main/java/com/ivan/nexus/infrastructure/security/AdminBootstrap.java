package com.ivan.nexus.infrastructure.security;

import com.ivan.nexus.infrastructure.persistence.user.UserEntity;
import com.ivan.nexus.infrastructure.persistence.user.UserJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class AdminBootstrap implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);
    private static final String DEFAULT_DEV_PASSWORD = "changeme";

    private final UserJpaRepository users;
    private final PasswordEncoder passwordEncoder;
    private final Environment environment;
    private final String adminUsername;
    private final String adminPassword;

    public AdminBootstrap(
            UserJpaRepository users,
            PasswordEncoder passwordEncoder,
            Environment environment,
            @Value("${NEXUS_ADMIN_USERNAME:admin}") String adminUsername,
            @Value("${NEXUS_ADMIN_PASSWORD:#{null}}") String adminPassword) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.environment = environment;
        this.adminUsername = adminUsername;
        this.adminPassword = adminPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (users.count() > 0) {
            return;
        }

        users.save(new UserEntity(adminUsername, passwordEncoder.encode(resolvePassword()), "ADMIN"));
        log.info("Bootstrapped initial admin user '{}'", adminUsername);
    }

    private String resolvePassword() {
        if (adminPassword != null && !adminPassword.isBlank()) {
            return adminPassword;
        }
        if (environment.acceptsProfiles(Profiles.of("prod"))) {
            throw new IllegalStateException("NEXUS_ADMIN_PASSWORD is required when the prod profile is active");
        }
        return DEFAULT_DEV_PASSWORD;
    }
}
