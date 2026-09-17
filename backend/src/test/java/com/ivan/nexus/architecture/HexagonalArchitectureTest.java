package com.ivan.nexus.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class HexagonalArchitectureTest {
    private static final String APPLICATION_PACKAGES = "com.ivan.nexus.application..";
    private static final String JACKSON_PACKAGES = "com.fasterxml.jackson..";
    private static final String MONGO_STATEMENT_PARSER =
            "com.ivan.nexus.application.database.MongoStatementParser";

    // Add application.database.. after its remaining adapter dependencies are migrated.
    private static final String[] CLEAN_APPLICATION_PACKAGES = {
            "com.ivan.nexus.application.audit..",
            "com.ivan.nexus.application.container..",
            "com.ivan.nexus.application.log..",
            "com.ivan.nexus.application.metrics..",
            "com.ivan.nexus.application.project..",
            "com.ivan.nexus.application.user.."
    };

    private static final String[] DOMAIN_ALLOWED_DEPENDENCIES = {
            "com.ivan.nexus.domain..",
            "java.."
    };

    private static final String[] APPLICATION_FORBIDDEN_DEPENDENCIES = {
            "com.ivan.nexus.infrastructure..",
            "com.ivan.nexus.interfaces..",
            "com.github.dockerjava..",
            "jakarta.persistence..",
            "javax.persistence..",
            "org.hibernate..",
            "org.springframework.data..",
            "org.springframework.dao.."
    };

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
    void cleanApplicationPackagesMustNotDependOnAdaptersOrPersistenceFrameworks() {
        noClasses()
                .that().resideInAnyPackage(CLEAN_APPLICATION_PACKAGES)
                .should().dependOnClassesThat().resideInAnyPackage(APPLICATION_FORBIDDEN_DEPENDENCIES)
                .check(NEXUS_CLASSES);
    }

    @Test
    void applicationMustNotDependOnJackson() {
        noClasses()
                .that().resideInAPackage(APPLICATION_PACKAGES)
                .should().dependOnClassesThat().resideInAPackage(JACKSON_PACKAGES)
                .check(NEXUS_CLASSES);
    }

    @Test
    void mongoStatementParserMustRemainAFrameworkIndependentPort() {
        noClasses()
                .that().haveFullyQualifiedName(MONGO_STATEMENT_PARSER)
                .should().dependOnClassesThat().resideInAnyPackage(APPLICATION_FORBIDDEN_DEPENDENCIES)
                .check(NEXUS_CLASSES);
    }
}
