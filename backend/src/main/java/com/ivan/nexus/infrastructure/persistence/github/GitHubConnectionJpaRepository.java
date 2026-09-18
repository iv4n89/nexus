package com.ivan.nexus.infrastructure.persistence.github;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface GitHubConnectionJpaRepository extends JpaRepository<GitHubConnectionEntity, UUID> {
}
