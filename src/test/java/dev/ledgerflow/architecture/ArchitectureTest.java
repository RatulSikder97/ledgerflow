package dev.ledgerflow.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

@AnalyzeClasses(packages = "dev.ledgerflow", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule layers = layeredArchitecture()
            .consideringOnlyDependenciesInLayers()
            .layer("Api").definedBy("dev.ledgerflow.api..")
            .layer("Messaging").definedBy("dev.ledgerflow.messaging..")
            .layer("Application").definedBy("dev.ledgerflow.application..")
            .layer("Persistence").definedBy("dev.ledgerflow.persistence..")
            .layer("Domain").definedBy("dev.ledgerflow.domain..")
            .whereLayer("Api").mayNotBeAccessedByAnyLayer()
            .whereLayer("Messaging").mayNotBeAccessedByAnyLayer()
            .whereLayer("Application").mayOnlyBeAccessedByLayers("Api", "Messaging")
            .whereLayer("Persistence").mayOnlyBeAccessedByLayers("Application", "Messaging")
            .whereLayer("Domain").mayOnlyBeAccessedByLayers("Api", "Messaging", "Application", "Persistence");

    @ArchTest
    static final ArchRule controllersDoNotTouchRepositories = noClasses()
            .that().resideInAPackage("dev.ledgerflow.api..")
            .should().dependOnClassesThat().resideInAPackage("dev.ledgerflow.persistence..");

    @ArchTest
    static final ArchRule domainIsFrameworkFree = classes()
            .that().resideInAPackage("dev.ledgerflow.domain..")
            .should().onlyDependOnClassesThat().resideInAnyPackage(
                    "dev.ledgerflow.domain..",
                    "java..",
                    "jakarta.persistence..",
                    "org.hibernate.annotations..",
                    // Persistable only: lets entities with application-assigned ids skip a SELECT-before-INSERT.
                    "org.springframework.data.domain..");

    @ArchTest
    static final ArchRule domainHasNoSpringStereotypes = noClasses()
            .that().resideInAPackage("dev.ledgerflow.domain..")
            .should().beAnnotatedWith("org.springframework.stereotype.Component")
            .orShould().beAnnotatedWith("org.springframework.stereotype.Service");

    @ArchTest
    static final ArchRule transactionsLiveInTheApplicationLayer = noClasses()
            .that().resideInAnyPackage("dev.ledgerflow.api..", "dev.ledgerflow.domain..",
                    "dev.ledgerflow.persistence..")
            .should().beAnnotatedWith("org.springframework.transaction.annotation.Transactional")
            .orShould().dependOnClassesThat()
            .haveFullyQualifiedName("org.springframework.transaction.annotation.Transactional");

    @ArchTest
    static final ArchRule noCycles = slices().matching("dev.ledgerflow.(*)..").should().beFreeOfCycles();
}
