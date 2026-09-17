package com.ivan.nexus.infrastructure.persistence.project;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface ManagedProjectJpaRepository extends JpaRepository<ManagedProjectEntity, String> {

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Transactional
    @Query(value = """
            INSERT INTO projects (
                id, name, description, working_directory, manifest_path, created_at, updated_at
            )
            VALUES (
                :id, :name, :description, :workingDirectory, :manifestPath,
                clock_timestamp(), clock_timestamp()
            )
            ON CONFLICT (id) DO UPDATE SET
                name = EXCLUDED.name,
                description = EXCLUDED.description,
                working_directory = EXCLUDED.working_directory,
                manifest_path = EXCLUDED.manifest_path,
                updated_at = clock_timestamp()
            """, nativeQuery = true)
    int upsertProject(
            @Param("id") String id,
            @Param("name") String name,
            @Param("description") String description,
            @Param("workingDirectory") String workingDirectory,
            @Param("manifestPath") String manifestPath);
}
