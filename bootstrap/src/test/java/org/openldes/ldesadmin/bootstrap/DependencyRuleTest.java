package org.openldes.ldesadmin.bootstrap;

import static com.tngtech.archunit.base.DescribedPredicate.alwaysTrue;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.library.Architectures.onionArchitecture;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.openldes.ldesadmin.application.service.ApplicationService;

/**
 * Guards the hexagonal architecture. The Maven modules already prevent inner rings from depending
 * on outer ones; these rules also cover dependencies within a module.
 */
@AnalyzeClasses(packages = "org.openldes.ldesadmin",
    importOptions = ImportOption.DoNotIncludeTests.class)
class DependencyRuleTest {

  @ArchTest
  static final ArchRule ringsOnlyDependInwards = layeredArchitecture()
      .consideringOnlyDependenciesInLayers()
      .layer("Model").definedBy("..model..")
      .layer("Application").definedBy("..application..")
      .layer("Adapter").definedBy("..adapter..")
      .layer("Bootstrap").definedBy("..bootstrap..")
      .whereLayer("Bootstrap").mayNotBeAccessedByAnyLayer()
      .whereLayer("Adapter").mayOnlyBeAccessedByLayers("Bootstrap")
      .whereLayer("Application").mayOnlyBeAccessedByLayers("Adapter", "Bootstrap")
      .whereLayer("Model").mayOnlyBeAccessedByLayers("Application", "Adapter", "Bootstrap");

  /**
   * Adapters only talk to each other through the application ring. The bootstrap ring is checked
   * by {@link #ringsOnlyDependInwards}.
   */
  @ArchTest
  static final ArchRule adaptersAreIndependent = onionArchitecture()
      .domainModels("..model..")
      .applicationServices("..application..")
      .adapter("rest", "..adapter.in.rest..")
      .adapter("scheduling", "..adapter.in.scheduling..")
      .adapter("persistence", "..adapter.out.persistence..")
      .adapter("remote instances", "..adapter.out.ldes..", "..adapter.out.ldio..",
          "..adapter.out.health..", "..adapter.out.http..")
      .adapter("local runtime", "..adapter.out.runtime..")
      .adapter("sandbox runtime", "..adapter.out.sandbox..")
      .withOptionalLayers(true)
      .ignoreDependency(resideInAPackage("..bootstrap.."), alwaysTrue());

  @ArchTest
  static final ArchRule innerRingsAreFrameworkFree = noClasses()
      .that().resideInAnyPackage("..model..", "..application..")
      .should().dependOnClassesThat().resideInAnyPackage(
          "org.springframework..", "jakarta..", "tools.jackson..", "com.fasterxml..",
          "org.apache.jena..", "org.openldes.ldi..", "org.eclipse.rdf4j..", "io.carml..",
          "org.testcontainers..", "lombok..");

  @ArchTest
  static final ArchRule inboundAdaptersUseUseCasesNotServices = noClasses()
      .that().resideInAPackage("..adapter.in..")
      .should().dependOnClassesThat().resideInAPackage("..application.service..");

  @ArchTest
  static final ArchRule outboundAdaptersOnlyImplementPorts = noClasses()
      .that().resideInAPackage("..adapter.out..")
      .should().dependOnClassesThat().resideInAnyPackage(
          "..application.service..", "..application.port.in..");

  @ArchTest
  static final ArchRule applicationServicesLiveInTheServicePackage = classes()
      .that().areAnnotatedWith(ApplicationService.class)
      .should().resideInAPackage("..application.service..");
}
