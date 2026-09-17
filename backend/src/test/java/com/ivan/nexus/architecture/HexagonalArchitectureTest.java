package com.ivan.nexus.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class HexagonalArchitectureTest {
    private static final String[] CLEAN_APPLICATION_PACKAGES = {
            "com.ivan.nexus.application.log..",
            "com.ivan.nexus.application.metrics..",
            "com.ivan.nexus.application.project.."
    };

    private static final String[] DOMAIN_FORBIDDEN_DEPENDENCIES = {
            "org.springframework..",
            "com.fasterxml.jackson..",
            "com.ivan.nexus.application..",
            "com.ivan.nexus.infrastructure..",
            "com.ivan.nexus.interfaces.."
    };

    private static final String[] APPLICATION_FORBIDDEN_DEPENDENCIES = {
            "com.ivan.nexus.infrastructure..",
            "com.ivan.nexus.interfaces..",
            "com.github.dockerjava..",
            "jakarta.persistence..",
            "javax.persistence..",
            "org.hibernate..",
            "org.springframework.data..",
            "org.springframework.dao..",
            "com.fasterxml.jackson.."
    };

    private static final JavaClasses NEXUS_CLASSES =
            new ClassFileImporter()
                    .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                    .importPackages("com.ivan.nexus");

    @Test
    void domainMustNotDependOnFrameworkOrOuterLayers() {
        noClasses()
                .that().resideInAPackage("com.ivan.nexus.domain..")
                .should().dependOnClassesThat().resideInAnyPackage(DOMAIN_FORBIDDEN_DEPENDENCIES)
                .check(NEXUS_CLASSES);
    }

    @Test
    void cleanApplicationPackagesMustNotDependOnAdaptersOrPersistenceFrameworks() {
        noClasses()
                .that().resideInAnyPackage(CLEAN_APPLICATION_PACKAGES)
                .should().dependOnClassesThat().resideInAnyPackage(APPLICATION_FORBIDDEN_DEPENDENCIES)
                .check(NEXUS_CLASSES);
    }
}
