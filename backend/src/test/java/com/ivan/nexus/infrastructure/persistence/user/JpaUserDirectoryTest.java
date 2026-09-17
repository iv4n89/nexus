package com.ivan.nexus.infrastructure.persistence.user;

import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JpaUserDirectoryTest {

    @Test
    void mapsUserEntityToItsId() {
        UserJpaRepository repository = mock(UserJpaRepository.class);
        UserEntity user = mock(UserEntity.class);
        UUID id = UUID.fromString("11111111-1111-1111-1111-111111111111");
        when(user.getId()).thenReturn(id);
        when(repository.findByUsername("admin")).thenReturn(Optional.of(user));

        assertThat(new JpaUserDirectory(repository).findIdByUsername("admin")).contains(id);
    }

    @Test
    void preservesMissingUser() {
        UserJpaRepository repository = mock(UserJpaRepository.class);
        when(repository.findByUsername("missing")).thenReturn(Optional.empty());

        assertThat(new JpaUserDirectory(repository).findIdByUsername("missing")).isEmpty();
    }
}
