package com.ivan.nexus.architecture;

import com.ivan.nexus.application.architecturefixture.AdapterDependentApplicationFixture;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

class HexagonalArchitectureTest {
    private static final String APPLICATION_PACKAGES = "com.ivan.nexus.application..";

    private static final String[] DOMAIN_ALLOWED_DEPENDENCIES = {
            "com.ivan.nexus.domain..",
            "java.."
    };

    private static final String[] APPLICATION_ALLOWED_DEPENDENCIES = {
            "com.ivan.nexus.application..",
            "com.ivan.nexus.domain..",
            "java..",
            "org.slf4j..",
            "org.springframework.stereotype..",
            "org.springframework.transaction.annotation..",
            "org.springframework.scheduling.annotation..",
            "org.springframework.beans.factory.annotation..",
            "org.springframework.boot.autoconfigure.condition.."
    };

    private static final ArchRule APPLICATION_DEPENDENCY_RULE =
            classes()
                    .that().resideInAPackage(APPLICATION_PACKAGES)
                    .should().onlyDependOnClassesThat()
                    .resideInAnyPackage(APPLICATION_ALLOWED_DEPENDENCIES)
                    .as("application layer may depend only on application/domain code, the JDK, "
                            + "SLF4J, and narrowly approved Spring orchestration annotations");

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
    void applicationMayDependOnlyOnExplicitlyAllowedPackages() {
        APPLICATION_DEPENDENCY_RULE.check(NEXUS_CLASSES);
    }

    @Test
    void globalApplicationAllowlistRejectsUnknownThirdPartyDependencyInAnyApplicationPackage() {
        JavaClasses fixture = new ClassFileImporter()
                .importClasses(AdapterDependentApplicationFixture.class);

        assertThatThrownBy(() -> APPLICATION_DEPENDENCY_RULE.check(fixture))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("application layer may depend only")
                .hasMessageContaining("UnknownSdkType");
    }
}
