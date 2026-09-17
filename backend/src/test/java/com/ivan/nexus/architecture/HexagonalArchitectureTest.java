package com.ivan.nexus.architecture;

import com.ivan.nexus.application.architecturefixture.AdapterDependentApplicationFixture;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class HexagonalArchitectureTest {
    private static final String APPLICATION_PACKAGES = "com.ivan.nexus.application..";

    private static final String[] DOMAIN_ALLOWED_DEPENDENCIES = {
            "com.ivan.nexus.domain..",
            "java.."
    };

    private static final String[] APPLICATION_FORBIDDEN_DEPENDENCIES = {
            "com.ivan.nexus.infrastructure..",
            "com.ivan.nexus.interfaces..",
            "com.fasterxml.jackson..",
            "com.github.dockerjava..",
            "com.mongodb..",
            "com.mysql..",
            "org.bson..",
            "org.mongodb..",
            "org.postgresql..",
            "java.sql..",
            "javax.sql..",
            "jakarta.persistence..",
            "javax.persistence..",
            "jakarta.servlet..",
            "org.hibernate..",
            "org.springframework.data..",
            "org.springframework.dao..",
            "org.springframework.jdbc..",
            "org.springframework.web..",
            "org.flywaydb..",
            "org.apache.hc.."
    };

    private static final ArchRule APPLICATION_DEPENDENCY_RULE =
            noClasses()
                    .that().resideInAPackage(APPLICATION_PACKAGES)
                    .should().dependOnClassesThat()
                    .resideInAnyPackage(APPLICATION_FORBIDDEN_DEPENDENCIES)
                    .as("application layer must not depend on interface or infrastructure adapters, "
                            + "persistence frameworks, serialization libraries, or adapter SDKs");

    private static final JavaClasses NEXUS_CLASSES =
            new ClassFileImporter()
                    .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                    .importPackages("com.ivan.nexus");

    @Test
    void domainMayDependOnlyOnDomainAndJdkClasses() {
        classes()
                .that().resideInAPackage("com.ivan.nexus.domain..")
                .should().onlyDependOnClassesThat().resideInAnyPackage(DOMAIN_ALLOWED_DEPENDENCIES)
                .check(NEXUS_CLASSES);
    }

    @Test
    void applicationMustNotDependOnAdaptersOrAdapterFrameworks() {
        APPLICATION_DEPENDENCY_RULE.check(NEXUS_CLASSES);
    }

    @Test
    void globalApplicationRuleRejectsSyntheticAdapterDependencyInAnyApplicationPackage() {
        JavaClasses fixture = new ClassFileImporter()
                .importClasses(AdapterDependentApplicationFixture.class);

        assertThatThrownBy(() -> APPLICATION_DEPENDENCY_RULE.check(fixture))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("application layer must not depend")
                .hasMessageContaining("JpaRepository");
    }
}
