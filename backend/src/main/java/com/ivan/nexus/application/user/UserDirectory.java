package com.ivan.nexus.application.user;

import java.util.Optional;
import java.util.UUID;

public interface UserDirectory {
    Optional<UUID> findIdByUsername(String username);
}
