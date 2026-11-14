package ar.edu.unq.desapp.futbolmarket.architecture;

import static com.tngtech.archunit.base.DescribedPredicate.describe;
import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.AccessTarget.Predicates.constructor;
import static com.tngtech.archunit.core.domain.JavaAccess.Predicates.originOwner;
import static com.tngtech.archunit.core.domain.JavaAccess.Predicates.targetOwner;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.assignableTo;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.equivalentTo;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.lang.conditions.ArchConditions.callCodeUnitWhere;
import static com.tngtech.archunit.lang.conditions.ArchPredicates.is;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_USE_FIELD_INJECTION;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING;
import static com.tngtech.archunit.library.GeneralCodingRules.THROW_GENERIC_EXCEPTIONS;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import java.util.Collection;
import java.util.List;

import org.hibernate.Session;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.NativeQuery;
import org.springframework.data.jpa.repository.Query;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.RestController;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaAnnotation;
import com.tngtech.archunit.core.domain.JavaCall;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.domain.JavaType;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.CompositeArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityManager;

/**
 * Las reglas de arquitectura del backend (B1 a B7.5). Cada método arma una regla con su identificador, su nombre y su
 * porqué. Las que reciben {@code base} arman sus paquetes a partir de él: con {@link #PRODUCTION_BASE} se evalúa la
 * aplicación y con {@link #FIXTURE_BASE}, las clases que rompen cada regla a propósito.
 */
public final class ArchitectureRules {

    public static final String PRODUCTION_BASE = "ar.edu.unq.desapp.futbolmarket";
    public static final String FIXTURE_BASE = "ar.edu.unq.desapp.archfixture";

    private ArchitectureRules() {
    }

    public static ArchRule layers(String base) {
        ArchRule rule = layeredArchitecture()
                .consideringOnlyDependenciesInAnyPackage(base + "..")
                .layer("Controller").definedBy(base + ".controller..")
                .layer("Service").definedBy(base + ".service..")
                .layer("Persistence").definedBy(base + ".persistence..")
                .layer("Adapter").definedBy(base + ".adapter..")
                .optionalLayer("Security").definedBy(base + ".security..")
                .optionalLayer("Bootstrap").definedBy(base)
                .whereLayer("Controller").mayNotBeAccessedByAnyLayer()
                .whereLayer("Security").mayNotBeAccessedByAnyLayer()
                .whereLayer("Service").mayOnlyBeAccessedByLayers("Controller", "Security", "Bootstrap")
                .whereLayer("Persistence").mayOnlyBeAccessedByLayers("Service")
                .whereLayer("Adapter").mayOnlyBeAccessedByLayers("Service");
        return named(rule, "B1", "Dirección de capas",
                "Principio I: el controller solo habla con el servicio, el servicio con la persistencia y el adapter,"
                        + " y ninguna capa se saltea");
    }

    public static ArchRule serviceContextsWithoutCycles(String base) {
        ArchRule rule = slices().matching(base + ".service.(*)..").should().beFreeOfCycles();
        return named(rule, "B6", "Servicios sin ciclos",
                "Principio I (reglas de monolito): un servicio puede depender de otro solo si no se forma un ciclo");
    }

    public static ArchRule pureModel(String base) {
        DescribedPredicate<JavaClass> allowed = resideInAnyPackage("java..", base + ".modelo..", "lombok..")
                .or(resideInAPackage(base + ".shared").and(assignableTo(Throwable.class)));
        ArchRule rule = classes().that().resideInAPackage(base + ".modelo..")
                .should().onlyDependOnClassesThat(allowed);
        return named(rule, "B2", "Modelo puro",
                "Principio I: el modelo no lleva anotaciones de persistencia ni conoce Spring ni otras capas");
    }

    public static ArchRule jpaOnlyInPersistenceSql(String base) {
        ArchRule rule = noClasses().that().resideOutsideOfPackage(base + ".persistence.sql..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("jakarta.persistence..", "org.hibernate..", "org.springframework.data.jpa..");
        return named(rule, "B3.1", "JPA solo en persistence/sql",
                "Principio I: las anotaciones y la API de JPA viven únicamente en las clases *SQL y en sus SQLDAO");
    }

    public static ArchRule entities(String base) {
        ArchRule rule = classes().that().areAnnotatedWith(Entity.class).or().areAnnotatedWith(Embeddable.class)
                .should().resideInAPackage(base + ".persistence.sql.entity..")
                .andShould().haveSimpleNameEndingWith("SQL");
        return named(rule, "B3.2", "Entidades *SQL",
                "Principio I: cada clase de modelo tiene su contraparte *SQL en persistence/sql/entity");
    }

    public static ArchRule sqlDaos(String base) {
        String interfaces = base + ".persistence.sql.interfaces..";
        ArchRule rule = classes().that().areAssignableTo(JpaRepository.class)
                .or().resideInAPackage(interfaces)
                .or().haveSimpleNameEndingWith("SQLDAO")
                .should().beInterfaces()
                .andShould().resideInAPackage(interfaces)
                .andShould().haveSimpleNameEndingWith("SQLDAO")
                .andShould().beAssignableTo(JpaRepository.class);
        return named(rule, "B3.3", "SQLDAO",
                "Principio I: los *SQLDAO son interfaces de persistence/sql/interfaces que extienden JpaRepository");
    }

    public static ArchRule sqlDaoAndMapperAccess(String base) {
        String repository = base + ".persistence.repository..";
        String mapper = base + ".persistence.mapper..";
        ArchRule sqlDaoAccess = classes().that().resideInAPackage(base + ".persistence.sql.interfaces..")
                .should().onlyHaveDependentClassesThat().resideInAPackage(repository);
        ArchRule mapperAccess = classes().that().resideInAPackage(mapper)
                .should().onlyHaveDependentClassesThat().resideInAnyPackage(repository, mapper);
        return named(CompositeArchRule.of(sqlDaoAccess).and(mapperAccess), "B3.4",
                "SQLDAO y mappers solo desde repository",
                "Principio I: repository es el único punto donde conviven modelo y persistencia, y es quien invoca al"
                        + " SQLDAO y al mapper");
    }

    public static ArchRule noNativeQueries(String base) {
        ArchRule annotations = noMethods().that().areDeclaredInClassesThat().resideInAPackage(base + "..")
                .should().beAnnotatedWith(nativeQueryAnnotation())
                .orShould().beAnnotatedWith(NativeQuery.class);
        ArchRule calls = noClasses().that().resideInAPackage(base + "..")
                .should().callMethodWhere(createNativeQueryCall());
        return named(CompositeArchRule.of(annotations).and(calls), "B3.5", "Sin queries nativas",
                "Principio VI: solo derived queries y JPQL, nunca SQL nativo");
    }

    public static ArchRule serviceWithoutEntities(String base) {
        ArchRule rule = noClasses().that().resideInAPackage(base + ".service..")
                .should().dependOnClassesThat().resideInAPackage(base + ".persistence.sql.entity..");
        return named(rule, "B3.6", "El servicio no conoce las entidades",
                "Principio I: el servicio recibe y devuelve modelo y no conoce JPA ni las clases *SQL");
    }

    public static ArchRule restControllers(String base) {
        ArchRule rule = classes().that().areAnnotatedWith(RestController.class)
                .should().resideInAPackage(base + ".controller.*")
                .andShould().haveSimpleNameEndingWith("Controller");
        return named(rule, "B4.1", "Controllers REST",
                "Principio I: los controllers viven en controller/<contexto>/ y se llaman *Controller");
    }

    public static ArchRule controllerDtos(String base) {
        String dto = base + ".controller.*.dto..";
        ArchRule usage = classes().that().resideInAPackage(dto)
                .should().onlyHaveDependentClassesThat().resideInAPackage(base + ".controller..");
        ArchRule names = classes().that().resideInAPackage(dto).and().areTopLevelClasses()
                .should().haveSimpleNameEndingWith("Request")
                .orShould().haveSimpleNameEndingWith("Response");
        return named(CompositeArchRule.of(usage).and(names), "B4.2", "DTO del controller",
                "Principio I: los Request y Response viven en controller/<contexto>/dto/ y nunca cruzan hacia el"
                        + " servicio");
    }

    public static ArchRule endpointsReturnNoModel(String base) {
        ArchRule rule = methods().that().arePublic()
                .and().areDeclaredInClassesThat().areAnnotatedWith(RestController.class)
                .should(notReturnClassesFrom(base + ".modelo..", base + ".persistence.."));
        return named(rule, "B4.3", "Qué devuelve un endpoint",
                "Principio I: ninguna clase de modelo ni de persistencia sale en una respuesta HTTP; sale siempre un"
                        + " Response");
    }

    public static ArchRule providerDtos(String base) {
        ArchRule rule = classes().that().resideInAPackage(base + ".adapter.*.dto..")
                .should().onlyHaveDependentClassesThat().resideInAPackage(base + ".adapter..");
        return named(rule, "B5.1", "DTO del proveedor",
                "Principio I: los DTO del proveedor viven en adapter/<proveedor>/dto/ y nunca salen del adapter");
    }

    public static ArchRule independentAdapter(String base) {
        ArchRule rule = noClasses().that().resideInAPackage(base + ".adapter..")
                .should().dependOnClassesThat()
                .resideInAnyPackage(base + ".service..", base + ".controller..", base + ".persistence..");
        return named(rule, "B5.2", "Adapter independiente",
                "Principio I: el adapter recibe y devuelve modelo; no conoce el servicio, el controller ni la"
                        + " persistencia");
    }

    public static ArchRule httpClientsOnlyInAdapterAndConfig(String base) {
        ArchRule rule = noClasses().that().resideOutsideOfPackages(base + ".adapter..", base + ".config..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework.web.client..",
                        "org.springframework.web.reactive.function.client..",
                        "org.springframework.http.client..",
                        "java.net.http..");
        return named(rule, "B5.3", "Clientes HTTP",
                "Principio I: el servicio habla con una API externa solo a través de su adapter y nunca usa un"
                        + " cliente HTTP");
    }

    public static ArchRule noStandardStreams() {
        return named(NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS, "B7.1", "Sin salida estándar",
                "Principio V: nada de System.out; el logging va con SLF4J");
    }

    public static ArchRule noGenericExceptions() {
        ArchRule rule = noClasses().should(THROW_GENERIC_EXCEPTIONS.or(createErrors()));
        return named(rule, "B7.2", "Sin excepciones genéricas",
                "Principio III: las excepciones son propias y con nombre; prohibido throw new RuntimeException(...)");
    }

    public static ArchRule noJavaUtilLogging() {
        return named(NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING, "B7.3", "Sin java.util.logging",
                "Principio V: el logging va con SLF4J");
    }

    public static ArchRule noFieldInjection() {
        return named(NO_CLASSES_SHOULD_USE_FIELD_INJECTION, "B7.4", "Sin inyección por campo",
                "Principio V: inyección por constructor, nunca @Autowired sobre el campo");
    }

    public static ArchRule singleExceptionHandler(String base) {
        ArchRule rule = classes().that().areMetaAnnotatedWith(ControllerAdvice.class)
                .should().resideInAPackage(base + ".shared")
                .andShould(beTheOnlyExceptionHandler());
        return named(rule, "B7.5", "Un solo manejador de excepciones",
                "Principio III: un único @RestControllerAdvice en shared/ centraliza el manejo de errores");
    }

    private static ArchRule named(ArchRule rule, String id, String name, String reason) {
        return rule.as("[" + id + "] " + name + ", porque " + reason);
    }

    private static DescribedPredicate<JavaAnnotation<?>> nativeQueryAnnotation() {
        return describe("@Query con nativeQuery = true",
                annotation -> annotation.getRawType().isEquivalentTo(Query.class)
                        && Boolean.TRUE.equals(annotation.get("nativeQuery").orElse(Boolean.FALSE)));
    }

    // La Session de Hibernate también es un EntityManager; se nombra para no depender de esa herencia.
    private static DescribedPredicate<JavaMethodCall> createNativeQueryCall() {
        return describe("createNativeQuery de EntityManager o de la Session de Hibernate",
                call -> "createNativeQuery".equals(call.getName())
                        && (call.getTargetOwner().isAssignableTo(EntityManager.class)
                                || call.getTargetOwner().isAssignableTo(Session.class)));
    }

    // Mismo patrón que THROW_GENERIC_EXCEPTIONS: el super(...) de una subclase propia de Error no cuenta.
    private static ArchCondition<JavaClass> createErrors() {
        return callCodeUnitWhere(JavaCall.Predicates.target(is(constructor()))
                .and(targetOwner(is(equivalentTo(Error.class))))
                .and(not(originOwner(is(assignableTo(Error.class))))))
                .as("crear un Error");
    }

    private static ArchCondition<JavaMethod> notReturnClassesFrom(String... packageIdentifiers) {
        DescribedPredicate<JavaClass> forbidden = resideInAnyPackage(packageIdentifiers);
        return new ArchCondition<>("no devolver clases de modelo ni de persistencia") {

            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                JavaType returnType = method.getReturnType();
                returnType.getAllInvolvedRawTypes().stream()
                        .filter(forbidden::test)
                        .forEach(type -> events.add(SimpleConditionEvent.violated(method,
                                "El endpoint " + method.getFullName() + " devuelve " + type.getName()
                                        + " (dentro de " + returnType.getName() + ")")));
            }
        };
    }

    private static ArchCondition<JavaClass> beTheOnlyExceptionHandler() {
        return new ArchCondition<>("ser el único manejador global de excepciones") {

            private List<String> handlers = List.of();

            @Override
            public void init(Collection<JavaClass> allObjectsToTest) {
                handlers = allObjectsToTest.stream().map(JavaClass::getName).sorted().toList();
            }

            @Override
            public void check(JavaClass handler, ConditionEvents events) {
                events.add(SimpleConditionEvent.satisfied(handler,
                        handler.getName() + " es un manejador global de excepciones"));
            }

            @Override
            public void finish(ConditionEvents events) {
                if (handlers.size() != 1) {
                    events.add(SimpleConditionEvent.violated(handlers,
                            "Hay " + handlers.size() + " manejadores globales de excepciones ("
                                    + String.join(", ", handlers)
                                    + "): tiene que haber exactamente uno, en shared"));
                }
            }
        };
    }
}
