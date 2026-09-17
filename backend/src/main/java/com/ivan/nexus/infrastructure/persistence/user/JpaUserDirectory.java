package com.ivan.nexus.infrastructure.persistence.user;

import com.ivan.nexus.application.user.UserDirectory;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class JpaUserDirectory implements UserDirectory {
    private final UserJpaRepository users;

    public JpaUserDirectory(UserJpaRepository users) {
        this.users = users;
    }

    @Override
    public Optional<UUID> findIdByUsername(String username) {
        return users.findByUsername(username).map(UserEntity::getId);
    }
}
