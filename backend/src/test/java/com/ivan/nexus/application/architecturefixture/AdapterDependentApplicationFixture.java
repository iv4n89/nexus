package com.ivan.nexus.application.architecturefixture;

import org.springframework.data.jpa.repository.JpaRepository;

public final class AdapterDependentApplicationFixture {
    private JpaRepository<?, ?> repository;
}
