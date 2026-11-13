# Investigación: Tests de arquitectura del backend y del frontend

**Fecha**: 2026-11-12 | **Plan**: [plan.md](./plan.md) | **Spec**: [spec.md](./spec.md)

Este documento resuelve las incógnitas del contexto técnico y registra cada decisión con su motivo y las
alternativas descartadas. Las decisiones D1 a D12 (backend) y F-D1 a F-D9 (frontend) son las del pedido de
planificación: se adoptan tal cual salvo donde se indica **Ajuste**. D13 a D17 y F-D10 a F-D12 son nuevas y salen de
las verificaciones hechas al planificar.

Base del relevamiento: rama `010-tests-arquitectura` en `af8c73a` (solo la spec), sobre `967f8e6` (`develop` y `main`).

## Índice

- [Verificaciones hechas al planificar](#verificaciones-hechas-al-planificar)
- Backend: [D1](#d1-api-core-de-archunit-con-test-de-junit) · [D2](#d2-import-único-de-las-clases-de-producción) ·
  [D3](#d3-reglas-parametrizadas-por-paquete-base) · [D4](#d4-paquete-de-test-transversal-architecture) ·
  [D5](#d5-capas-b1) · [D6](#d6-ciclos-entre-contextos-del-servicio-b6) ·
  [D7](#d7-reglas-generales-b71-a-b74) · [D8](#d8-el-porqué-de-cada-regla) · [D9](#d9-modelo-puro-b2) ·
  [D10](#d10-qué-devuelve-un-endpoint-b43) · [D11](#d11-queries-nativas-b35) ·
  [D12](#d12-failonemptyshould-y-sin-freezingarchrule) · [D13](#d13-las-fixtures-viven-fuera-del-paquete-de-la-aplicación) ·
  [D14](#d14-mensaje-de-falla-y-violationreport) · [D15](#d15-identificadores-en-inglés) ·
  [D16](#d16-fixtures-sin-issues-de-sonar) · [D17](#d17-resto-de-las-reglas-del-backend)
- Frontend: [F-D1](#f-d1-sin-herramientas-nuevas) · [F-D2](#f-d2-un-solo-archivo-en-la-raíz-de-tests) ·
  [F-D3](#f-d3-lectura-de-fuentes-con-importmetaglob) · [F-D4](#f-d4-parseo-con-el-compilador-de-typescript) ·
  [F-D5](#f-d5-tabla-declarativa-y-función-pura) · [F-D6](#f-d6-ciclos-f9) · [F-D7](#f-d7-estructura-de-los-tests) ·
  [F-D8](#f-d8-casos-borde-en-memoria) · [F-D9](#f-d9-corrección-de-topbar) ·
  [F-D10](#f-d10-entorno-jsdom-no-node) · [F-D11](#f-d11-guarda-contra-un-conjunto-vacío) ·
  [F-D12](#f-d12-correcciones-al-relevamiento-del-frontend)
- [Lo que el plan resuelve distinto del pedido](#lo-que-el-plan-resuelve-distinto-del-pedido)
- [Riesgos conocidos](#riesgos-conocidos)

## Verificaciones hechas al planificar

| Punto marcado "verificar" o dudoso | Resultado | Cómo se verificó |
|---|---|---|
| Última ArchUnit 1.5.x | **1.5.1**, que además es la última publicada (metadata actualizada el 2026-09-25). Existe `archunit-junit6` 1.5.1, que no se usa | `maven-metadata.xml` de Maven Central |
| JUnit que trae Spring Boot 4.1.1 | **JUnit 6.0.3** (Jupiter y Platform); AssertJ 3.27.7. Algunas dependencias piden 5.13/5.14 y Gradle las sube a 6.0.3 | `./gradlew dependencies --configuration testRuntimeClasspath` |
| `consideringOnlyDependenciesInAnyPackage` | Existe: `(String, String...)`. Ignora las dependencias con origen **o** destino fuera de esos paquetes. Las clases que no están en ninguna capa (modelo, config, shared) cuentan como origen: si acceden a una capa restringida, es violación | Fuente de `Architectures.java` en el tag `v1.5.1` |
| `optionalLayer` / `withOptionalLayers` | Existen los dos. La verificación "la capa no puede estar vacía" se saltea solo para las capas opcionales | Ídem |
| `JavaType.getAllInvolvedRawTypes()` | Existe (método `default`). Devuelve los tipos crudos de todos los niveles de genéricos, las cotas de los comodines y el tipo base de los arreglos | Fuente de `JavaType.java` |
| `GeneralCodingRules` | `THROW_GENERIC_EXCEPTIONS` cubre `Throwable`, `Exception` y `RuntimeException`, y excluye las llamadas desde subclases (el `super(...)` de una excepción propia). **No cubre `Error`**. `FIELD_INJECTION` cubre `@Autowired`, `@Value`, `@Inject` (javax, jakarta y Guice) y `@Resource`. `STANDARD_STREAMS` cubre `System.out`, `System.err`, `printStackTrace()` y `java.lang.IO` | Fuente de `GeneralCodingRules.java` |
| Otras APIs del diseño | Existen `CompositeArchRule.of/and/as`, `ClassesThat.areTopLevelClasses()`, `ArchCondition.init/finish`, `JavaAnnotation.get(String)`, `ArchRule.as(String)`. En los patrones de paquete, `*` es un solo segmento y `..` cualquier cantidad | Fuentes del tag `v1.5.1` |
| `@NativeQuery` | Existe en Spring Data JPA 4.1.1 (`org.springframework.data.jpa.repository.NativeQuery`) | Contenido del jar en la caché de Gradle |
| Escaneo de Spring en los tests | `FutbolMarketApplication` usa `@SpringBootApplication` sin `scanBasePackages`, sin `@EntityScan` y sin `@EnableJpaRepositories`. Hay 68 clases con `@SpringBootTest` y 1 test de slice. Hoy ningún test declara estereotipos de Spring, entidades ni repositories | `grep` sobre `src/main` y `src/test` |
| Alcance de las reglas de Sonar | `java:S106` (salida estándar), `java:S112` (excepciones genéricas) y `java:S6813` (inyección por campo) tienen alcance **Main**: no se aplican a `src/test`. `java:S1068` (campo privado sin usar) y `java:S1186` (método vacío) tienen alcance **All** | Metadata de las reglas en el repositorio de SonarJava |
| `tests/setup.ts` del frontend | Usa `window.location` al cargarse y `sessionStorage.clear()` en el `afterEach` | Lectura del archivo |
| `tsconfig.json` del frontend | Incluye `src` y `tests` (los tests pasan por `tsc -b` en `npm run build`); `types` = `vite/client`, `vitest/globals` y `jest-dom`, sin los de Node | Lectura del archivo |
| Relevamiento del backend del pedido | **Confirmado** punto por punto (ver D5 a D11 y D17) | `grep` de imports, anotaciones y firmas |
| Relevamiento del frontend del pedido | **Confirmado**, con tres precisiones (F-D12) | Prototipo del análisis de F-D4 con el compilador de TypeScript sobre `src/` (129 archivos, menos de 1 s) |

## D1. API core de ArchUnit con `@Test` de JUnit

**Decisión**: se usa la API core (`ArchRule.evaluate(JavaClasses)`) desde métodos `@Test` comunes, sin
`@AnalyzeClasses` ni `@ArchTest`. Dependencia única: `testImplementation 'com.tngtech.archunit:archunit:1.5.1'`.

Cada test:

- **setup**: se toman las clases (`ProductionClasses.get()` o las fixtures) y se arma la regla con `ArchitectureRules`;
- **execute**: `EvaluationResult result = rule.evaluate(classes);`;
- **verify**: `assertThat(result.getFailureReport().getDetails()).withFailMessage(...).isEmpty();` (el mensaje, en D14).

**Motivo**: el Principio IV pide AssertJ y setup, execute y verify, y un campo `@ArchTest static final ArchRule` no
tiene ninguna de las dos cosas. Además el classpath trae JUnit 6.0.3 (verificado): el core evita elegir entre
`archunit-junit5` y `archunit-junit6`.

**Alternativas descartadas**: `archunit-junit6` con `@ArchTest` (mismo problema con el Principio IV, y una
dependencia más); `rule.check(classes)` (lanza `AssertionError` sin AssertJ y mezcla execute con verify).

## D2. Import único de las clases de producción

**Decisión**: `ProductionClasses` guarda en un campo estático, inicializado una sola vez (holder perezoso),
`new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS).importPackages("ar.edu.unq.desapp.futbolmarket")`.
`JavaClasses` es inmutable, así que compartirla entre tests no es "compartir estado" en el sentido del Principio IV.

**Motivo**: importar las 447 clases de producción una vez por corrida, no una vez por test (SC-005).
`DO_NOT_INCLUDE_TESTS` deja afuera `build/classes/java/test` (FR-006 y escenario 8 de la US1).

**Alternativas descartadas**: importar en cada test (más lento); `@AnalyzeClasses` (descartado en D1).

## D3. Reglas parametrizadas por paquete base

**Decisión**: `ArchitectureRules` es una fábrica con un método estático por regla que recibe el paquete base, por
ejemplo `layers(String base)`. Los paquetes se arman como `base + ".controller.."`. Con
`base = "ar.edu.unq.desapp.futbolmarket"` se evalúa la producción; con `base = "ar.edu.unq.desapp.archfixture"`, las
fixtures (**Ajuste**: el paquete de las fixtures cambia, ver D13).

**Motivo**: la misma regla se prueba contra el código real (caso feliz) y contra una violación armada a propósito
(caso borde). Los patrones anclados al paquete base no matchean `jakarta.persistence` ni
`org.springframework.security`, como pasaría con `"..persistence.."` o `"..security.."`.

**Alternativas descartadas**: patrones sueltos con `..` (falsos positivos con paquetes de terceros); reglas duplicadas
para producción y fixtures (código duplicado, Principio V).

## D4. Paquete de test transversal `architecture`

**Decisión**: los tests y sus helpers van en `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/architecture/`, un
paquete de test nuevo y transversal (como `security/`, `config/` y `shared/`), declarado en este plan con el aval del
pedido. **Ajuste**: las fixtures **no** van en `architecture/fixture/`, sino en un paquete hermano fuera de la
aplicación (D13).

**Motivo**: el árbol del backend no es cerrado (constitución 2.2.0): una carpeta nueva se agrega con el aval de un
desarrollador, declarada en el plan. Las reglas de arquitectura no pertenecen a ninguna capa ni contexto.

## D5. Capas (B1)

**Decisión**:

```text
layeredArchitecture()
  .consideringOnlyDependenciesInAnyPackage(base + "..")
  .layer("Controller").definedBy(base + ".controller..")
  .layer("Service").definedBy(base + ".service..")
  .layer("Persistence").definedBy(base + ".persistence..")
  .layer("Adapter").definedBy(base + ".adapter..")
  .optionalLayer("Security").definedBy(base + ".security..")
  .optionalLayer("Bootstrap").definedBy(base)          // solo el paquete raíz
  .whereLayer("Controller").mayNotBeAccessedByAnyLayer()
  .whereLayer("Security").mayNotBeAccessedByAnyLayer()
  .whereLayer("Service").mayOnlyBeAccessedByLayers("Controller", "Security", "Bootstrap")
  .whereLayer("Persistence").mayOnlyBeAccessedByLayers("Service")
  .whereLayer("Adapter").mayOnlyBeAccessedByLayers("Service")
```

`modelo`, `config` y `shared` no son capas. Como la configuración considera todas las dependencias dentro de `base..`
(verificado), una clase de `modelo`, `config` o `shared` que use una capa restringida también es violación: eso es lo
que pide FR-010 ("ninguna clase fuera de controller depende de controller", etc.).

**Relevamiento (confirmado)**: fuera de `controller` nadie importa `controller`; fuera de `security` nadie importa
`security`; al servicio acceden los controllers (incluidos dos DTO: `SimulationRequest` y `StrategyChangeResponse`, que
son parte de la capa), `CredentialAuthenticationFilter`, `SecurityConfig` y `StatsSnapshotExport`; a la persistencia
solo el servicio; al adapter solo `service/sync` (`SyncService` y `StatsSyncService`). `config` y `shared` no importan
nada del proyecto. No hay imports con comodín. Los únicos nombres calificados fuera de los imports son tipos de
`modelo` dentro de DTO del controller, que están permitidos.

**Nota**: "a Security no accede nadie" es más estricto que FR-010, que no restringe a `security`. Hoy se cumple y el
pedido lo decide así; el README lo documenta como parte de B1.

**Alternativas descartadas**: `consideringAllDependencies()` (falsos positivos con `Object` y la JDK);
`consideringOnlyDependenciesInLayers()` (no ve un `modelo -> controller`, porque `modelo` no es capa).

## D6. Ciclos entre contextos del servicio (B6)

**Decisión**: `slices().matching(base + ".service.(*)..").should().beFreeOfCycles()`. No se aplica a `modelo`, que es
único (FR-040).

**Relevamiento (confirmado)**: `quote -> sync`, `strategy -> quote`, `sync -> market`; `auth`, `market`, `player` y
`user` no dependen de otro contexto del servicio. No hay ciclos.

## D7. Reglas generales (B7.1 a B7.4)

**Decisión**: B7.1, B7.3 y B7.4 son las constantes `NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS`,
`NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING` y `NO_CLASSES_SHOULD_USE_FIELD_INJECTION` de `GeneralCodingRules`, con su
identificador y su porqué en español (D8). **Ajustes**:

1. **B7.2 no es solo la constante.** `NO_CLASSES_SHOULD_THROW_GENERIC_EXCEPTIONS` no cubre `Error` (verificado), y
   FR-051 lo pide. B7.2 = `noClasses().should(THROW_GENERIC_EXCEPTIONS.or(<crear un Error>))`, donde "crear un Error"
   sigue el mismo patrón de la librería: llamar a un constructor declarado en `Error` desde una clase que no es
   subclase de `Error`.
2. **Las cuatro llevan caso borde con fixture.** El pedido decía que no, por ser de la librería y por Sonar. La spec lo
   pide expresamente (FR-007, SC-002, escenario 7 de la US1 y Assumptions: "también llevan su caso borde; el riesgo de
   que no chequeen nada es el mismo"), y el motivo de Sonar no aplica: `java:S106`, `java:S112` y `java:S6813` tienen
   alcance Main y no miran `src/test` (verificado, D16).

**Relevamiento (confirmado)**: no hay `System.out`, `System.err`, `printStackTrace`, `java.util.logging`,
`new RuntimeException/Exception/Throwable/Error` ni campos con anotaciones de inyección. Los tres `@Autowired` de hoy
(`WhoScoredAdapter`, `AuthService` y `AdminAccountInitializer`) están sobre constructores.

## D8. El porqué de cada regla

**Decisión**: toda regla lleva identificador, nombre y porqué en español, citando el principio. **Ajuste de forma**:
se arma con `rule.as("[B1] Dirección de capas, porque Principio I: el controller solo habla con el servicio")` en vez
de `.as(...).because(...)`, porque `because` agrega el conector en inglés (", because "). Un helper privado de
`ArchitectureRules` (`named(rule, id, name, reason)`) arma ese texto igual para las 20 reglas.

**Motivo**: FR-004 pide en español el nombre, el porqué y el encabezado de la lista; solo las líneas de detalle pueden
quedar con la redacción de la herramienta.

**Alternativa descartada**: `.because("Principio I: ...")` tal cual el pedido: deja "because" en el encabezado.

## D9. Modelo puro (B2)

**Decisión**: `classes().that().resideInAPackage(base + ".modelo..").should().onlyDependOnClassesThat(<permitidas>)`,
con `<permitidas>` = `resideInAnyPackage("java..", base + ".modelo..", "lombok..")` **o** (`resideInAPackage(base + ".shared")`
**y** `assignableTo(Throwable.class)`).

**Ajuste**: el pedido permitía todo `shared..`. FR-011 permite solo las excepciones base, y `shared` es un paquete
plano que también tiene `ApiError` y `GlobalExceptionHandler`: el predicado limita a las clases de `shared` que son
excepciones.

**Motivo de `lombok..`**: `lombok.config` tiene `lombok.addLombokGeneratedAnnotation = true`, así que
`@lombok.Generated` queda en el bytecode del modelo.

**Relevamiento (confirmado)**: el modelo importa solo `java.*`, otras clases de `modelo`, Lombok (`AccessLevel`,
`AllArgsConstructor`, `Getter`) y seis bases de `shared` (`BadRequestException`, `ConflictException`,
`ForbiddenException`, `NotFoundException`, `ServiceUnavailableException` y `UnauthorizedException`).

## D10. Qué devuelve un endpoint (B4.3)

**Decisión**: `methods().that().arePublic().and().areDeclaredInClassesThat().areAnnotatedWith(RestController.class)`
`.should(notReturnTypesFrom(base + ".modelo..", base + ".persistence.."))`. La condición propia recorre
`method.getReturnType().getAllInvolvedRawTypes()` (verificado: todos los niveles de genéricos, cotas y tipo base de los
arreglos) y reporta, en español, el método, el tipo infractor y el retorno completo.

**Relevamiento (confirmado)**: todos los métodos públicos de los 12 controllers devuelven un `*Response`, una
`List<StrategyResponse>` o `void`. No hay métodos de endpoint no públicos.

## D11. Queries nativas (B3.5)

**Decisión**: una `CompositeArchRule` con dos partes:

1. ningún método anotado con `@Query` con `nativeQuery = true` (predicado sobre la anotación, con
   `JavaAnnotation.get("nativeQuery")`) ni con `@NativeQuery`;
2. ninguna clase llama a un método `createNativeQuery` (de `EntityManager` o de la `Session` de Hibernate).

**Ajuste**: el pedido cubría solo `@Query(nativeQuery = true)`. La spec (Edge Cases y FR-016) pide también "la
anotación específica de query nativa" y "la creación de una query nativa en tiempo de ejecución". `@NativeQuery`
existe en Spring Data JPA 4.1.1 (verificado).

**Relevamiento (confirmado)**: no hay `nativeQuery`, `@NativeQuery` ni `createNativeQuery`.

## D12. `failOnEmptyShould` y sin `FreezingArchRule`

**Decisión**: `archRule.failOnEmptyShould` queda en `true` (por defecto, sin `archunit.properties`): una regla que no
selecciona nada tiene un patrón mal escrito y falla. No se usa `FreezingArchRule` ni excepciones por clase (FR-008).
Las capas no opcionales de B1 también fallan si quedan vacías (verificado en la fuente).

## D13. Las fixtures viven fuera del paquete de la aplicación

**Decisión**: las clases que rompen reglas a propósito van en `backend/src/test/java/ar/edu/unq/desapp/archfixture/`,
un paquete **hermano** de `futbolmarket`, no dentro de `futbolmarket.architecture`. Adentro replican las capas
(`controller/sample/`, `service/sample/`, `persistence/sql/entity/sample/`, ...). `base` para las fixtures =
`"ar.edu.unq.desapp.archfixture"`.

**Motivo (bloqueante)**: `@SpringBootApplication` escanea `ar.edu.unq.desapp.futbolmarket` y todos sus subpaquetes, y
en los tests el classpath incluye las clases de test. Una fixture con `@RestController`, `@RestControllerAdvice`,
`@Entity` o una interfaz `JpaRepository` dentro de ese paquete entraría al contexto de los 68 `@SpringBootTest` y del
test de slice: un controller cuyo constructor pide un bean que no existe, una tabla de más, una query nativa, un
segundo `@RestControllerAdvice`. Rompería la suite existente sin tocar ningún test. `archfixture` no es subpaquete de
`futbolmarket` (los patrones de paquete comparan por segmento), así que Spring no lo ve y
`ProductionClasses` tampoco.

**Alternativas descartadas**: excluir las fixtures del escaneo con `@ComponentScan`, `@EntityScan` o
`@EnableJpaRepositories` (cambia producción, FR-082); un source set aparte (cambia el `build.gradle` más allá de una
línea); fixtures sin anotaciones de Spring (no probarían B4.1, B4.3, B7.5 ni B3.2 a B3.5).

**Aval**: es una carpeta nueva del árbol de tests, fuera de `futbolmarket`. Se declara en el plan (puerta G2) y
recibió el aval de un desarrollador el 2026-11-12.

## D14. Mensaje de falla y `ViolationReport`

**Decisión**: un helper de test, `architecture/ViolationReport`, arma el texto de la falla a partir de la regla y del
resultado:

```text
[B1] Dirección de capas, porque Principio I: el controller solo habla con el servicio
Infractores (2):
- Constructor <...RepositoryAwareController.<init>(...SampleRepository)> has parameter of type <...SampleRepository> in (RepositoryAwareController.java:0)
- ...
```

El verify de cada caso feliz queda en una sola sentencia:
`assertThat(result.getFailureReport().getDetails()).withFailMessage(() -> ViolationReport.of(rule, result)).isEmpty();`

**Motivo**: con `getDetails()` solo, AssertJ imprime la lista con su encabezado en inglés ("Expecting empty but
was") y sin el identificador ni el porqué de la regla (FR-004, SC-004). El helper evita repetir el formato en 20 tests
(Principio V).

**Alternativas descartadas**: `.as(rule.getDescription())` (el encabezado de la lista queda en inglés); un `Assert`
propio de AssertJ (más código para lo mismo).

## D15. Identificadores en inglés

**Decisión**: clases, métodos, variables y campos de los tests nuevos van en inglés (`result`, `rule`, `classes`,
`analyze`, `sources`, `mayUse`, `mayNotUse`, `because`, `folder`, `violations`). En español van los nombres de los
métodos de test y los textos de `it(...)`, los mensajes y los comentarios.

**Motivo**: Principio VII (identificadores en inglés), que aplica también al frontend. Es lo que hacen los tests
existentes (`AuditEntryTest` usa `initial`, `change`, `entry`; los tests del frontend, `linksOf`, `requests`,
`labels`). El pedido usaba `resultado`, `regla`, `analizar`, `fuentes`, `puedeUsar`, `noPuedeUsar` y `porque`.

## D16. Fixtures sin issues de Sonar

**Decisión**: las fixtures se escriben para no sumar issues: cada campo privado se usa, ningún método queda vacío,
ninguna clase queda vacía y ningún parámetro sobra. Las reglas que las fixtures rompen a propósito (`S106`, `S112`,
`S6813`) no aplican a `src/test` (alcance Main, verificado). Si igual aparecen issues en el primer análisis de `main`,
el respaldo es excluirlas en el bloque `sonar` del `build.gradle` con
`property 'sonar.test.exclusions', '**/archfixture/**'` y un comentario con el porqué.

**Ajuste**: el pedido hablaba de excluir `architecture/fixture/**`; las fixtures son fuentes de test, así que la
propiedad es `sonar.test.exclusions` (no `sonar.exclusions`, que mira `src/main`), y la ruta es la de D13.

## D17. Resto de las reglas del backend

| Regla | Forma | Relevamiento |
|---|---|---|
| B3.1 JPA confinado | `noClasses().that().resideOutsideOfPackage(base + ".persistence.sql..").should().dependOnClassesThat().resideInAnyPackage("jakarta.persistence..", "org.hibernate..", "org.springframework.data.jpa..")` | **Confirmado**: nada fuera de `persistence/sql` importa esos paquetes. Los repositories usan `Page`, `PageRequest` y `Sort` de `org.springframework.data.domain`, que no son JPA. `@Transactional` es el de Spring (23 usos) |
| B3.2 Entidades | clases anotadas con `@Entity` o `@Embeddable` `should().resideInAPackage(base + ".persistence.sql.entity..")` `.andShould().haveSimpleNameEndingWith("SQL")` | **Confirmado**. `MarketColumns` no tiene anotación y no se selecciona. `AuditPartySQL` es `@Embeddable` y cumple |
| B3.3 SQLDAO | clases que son `assignableTo(JpaRepository)`, o viven en `persistence.sql.interfaces..`, o terminan en `SQLDAO` `should().beInterfaces()` `.andShould()` vivir en `sql.interfaces..`, terminar en `SQLDAO` y ser `assignableTo(JpaRepository)` | **Confirmado**: todo `*SQLDAO` está en `interfaces/`, todo lo de `interfaces/` termina en `SQLDAO` y extiende `JpaRepository` |
| B3.4 Uso de SQLDAO y mappers | `CompositeArchRule`: lo de `sql.interfaces..` `onlyHaveDependentClassesThat().resideInAPackage(base + ".persistence.repository..")`; lo de `mapper..` solo desde `repository..` o `mapper..` | **Confirmado**: SQLDAO solo desde `repository/`; mappers desde `repository/` y desde tres mappers |
| B3.6 Servicio sin entidades | `noClasses().that().resideInAPackage(base + ".service..").should().dependOnClassesThat().resideInAPackage(base + ".persistence.sql.entity..")` | **Confirmado**: el servicio no importa nada de `persistence.sql` ni de `persistence.mapper` |
| B4.1 Controllers REST | clases `@RestController` `should().resideInAPackage(base + ".controller.*")` `.andShould().haveSimpleNameEndingWith("Controller")` | **Confirmado**: 12, todos en `controller/<contexto>/` con sufijo |
| B4.2 DTO del controller | `CompositeArchRule`: lo de `controller.*.dto..` solo desde `controller..`; las clases de primer nivel (`areTopLevelClasses()`) de `controller.*.dto..` terminan en `Request` o `Response` | **Confirmado**: nadie fuera de `controller` las usa; los nueve tipos anidados quedan fuera de la parte de nombres (FR-022) |
| B5.1 DTO del proveedor | lo de `adapter.*.dto..` `onlyHaveDependentClassesThat().resideInAPackage(base + ".adapter..")` | **Confirmado** |
| B5.2 Adapter independiente | `noClasses()` de `adapter..` dependen de `service..`, `controller..` ni `persistence..` | **Confirmado**: el adapter usa `modelo` y `config` (`WhoScoredProperties`) |
| B5.3 Clientes HTTP | `noClasses().that().resideOutsideOfPackages(base + ".adapter..", base + ".config..")` dependen de `org.springframework.web.client..`, `org.springframework.web.reactive.function.client..`, `org.springframework.http.client..` ni `java.net.http..` | **Confirmado**: solo `FootballDataClient`, `HttpPageDownloader` y dos clases de `config` |
| B7.5 Un solo manejador | clases `areMetaAnnotatedWith(ControllerAdvice.class)` `should().resideInAPackage(base + ".shared")` y una condición propia que cuenta en `init` y reporta en `finish` si no hay exactamente una | **Confirmado**: solo `GlobalExceptionHandler`. El comentario de `ApiErrorResponseWriter` no cuenta (ArchUnit lee bytecode) |

## F-D1. Sin herramientas nuevas

**Decisión**: el análisis del frontend se escribe con lo instalado: Vitest, el compilador de TypeScript
(`typescript` ya es devDependency) y `import.meta.glob` de Vite. ArchUnit es solo para Java.

**Alternativas descartadas**: `dependency-cruiser` y `ArchUnitTS`: serían una dependencia nueva, necesitan fixtures en
disco (el árbol de `tests/` es cerrado) y no detectan `fetch` ni `sessionStorage` (F7).

## F-D2. Un solo archivo en la raíz de `tests/`

**Decisión**: `frontend/tests/architecture.test.ts`, junto a `setup.ts`. No crea carpetas, y el `include` de
`vitest.config.ts` (`tests/**/*.test.{ts,tsx}`) ya lo toma. **Ajuste**: sin `// @vitest-environment node` (F-D10).

## F-D3. Lectura de fuentes con `import.meta.glob`

**Decisión**:

- código de `src/`: `import.meta.glob<string>('../src/**/*.{ts,tsx}', { query: '?raw', import: 'default', eager: true })`;
- archivos de test dentro de `src/` (F10.1): las claves de `import.meta.glob('../src/**/*.{test,spec}.*')`, sin
  `eager`. **Ajuste**: cualquier extensión, porque FR-071 dice `.test.*` y `.spec.*`;
- tests de `tests/` (F10.2): las claves de `import.meta.glob('./{components,hooks,services,utils}/**/*.test.{ts,tsx}')`,
  sin `eager`: Vite arma funciones de import que nunca se llaman, así que esos tests no se ejecutan.

Las claves se normalizan a rutas relativas a `frontend/` (`src/components/TopBar.tsx`,
`tests/utils/format.test.ts`) y todo entra en un solo `Record<string, string>` (los tests, con contenido vacío).

**Motivo**: `node:fs` no tiene tipos (el `tsconfig` no incluye los de Node) y `tsc -b` revisa los tests en
`npm run build`. `vite/client` ya tipa `import.meta.glob`.

## F-D4. Parseo con el compilador de TypeScript

**Decisión**: `import * as ts from 'typescript'` y `ts.createSourceFile(path, code, ts.ScriptTarget.Latest, true, kind)`
con `ScriptKind.TSX` o `TS` según la extensión. Se recorre el AST y se toman:

- `ImportDeclaration` (incluidos `import type`) y `ExportDeclaration` con `moduleSpecifier`;
- `import()` dinámicos con un literal como argumento;
- los identificadores `fetch`, `sessionStorage` y `localStorage` usados como valor global, y también como propiedad de
  `window`, `globalThis` o `self` (`window.fetch`, `window['fetch']`). No cuentan los nombres de propiedad de otros
  objetos, los miembros de tipos ni los textos de los imports. Los comentarios no son nodos del AST.

Cada import relativo se normaliza contra la carpeta del archivo. Su **grupo** es la carpeta de primer nivel de `src/`
(`components`, `pages`, ...) o el propio nombre para los archivos de la raíz (`main.tsx`). Los imports que terminan en
`.css` no cuentan. Un import que no empieza con `.` es un paquete: `@scope/nombre` o el primer segmento.

**Prototipo**: el mismo recorrido, corrido sobre `src/` real con el `typescript` instalado, tardó menos de 1 s para
129 archivos y reprodujo el relevamiento (F-D12).

**Alternativa descartada**: expresiones regulares sobre el texto (cuentan comentarios y no distinguen `import type`,
propiedades ni literales).

## F-D5. Tabla declarativa y función pura

**Decisión**: las reglas de dependencias (F1 a F6.2) son una tabla:
`{ id: 'F1', appliesTo: ['components'], mayUse: ['components', 'types', 'utils', 'package:react'], because: '...' }` o
con `mayNotUse` en lugar de `mayUse`; F6.1 y F6.2 usan `appliesTo: { except: [...] }`. F7 a F10.2 son funciones
chicas. Todo lo junta `analyze(sources: Record<string, string>): Violation[]`, pura, que se usa igual con el código real
y con los casos borde. Mensaje:
`[F1] src/components/TopBar.tsx usa react-router: los componentes reciben datos y callbacks por props; no conocen la API, el router, los hooks ni los contextos (Constitución, sección Frontend)`.

**Motivo**: una sola implementación probada de los dos lados (caso feliz y borde). Identificadores en inglés (D15).

## F-D6. Ciclos (F9)

**Decisión**: DFS con colores sobre el grafo de archivos de `src/` (sin tests). Un import relativo se resuelve
probando la ruta tal cual, con `.ts`, con `.tsx`, `/index.ts` y `/index.tsx`. Cada ciclo se informa una vez, rotado
para empezar por su archivo menor, con el recorrido completo:
`[F9] ciclo src/hooks/a.ts -> src/hooks/b.ts -> src/hooks/a.ts: ...`.

**Relevamiento (confirmado)**: hoy no hay ciclos.

## F-D7. Estructura de los tests

**Decisión**: cada `it` sigue setup (las fuentes: el glob real o un objeto en memoria), execute
(`const violations = analyze(sources)`) y verify (`expect(messagesOf(violations, 'F1')).toEqual([])`, que filtra por
regla para que cada regla se informe por separado, FR-005). Nombres en español, por ejemplo
`it('ningún componente usa services, hooks, context, pages ni el router')`.

`analyze` se llama en cada `it`: no se comparte estado entre tests. El costo es chico (el parseo completo tardó menos
de 1 s en el prototipo).

## F-D8. Casos borde en memoria

**Decisión**: cada regla tiene al menos un caso borde con fuentes en memoria, sin archivos de fixture. **Ajuste**: la
lista del pedido cubría 9 casos; FR-007 pide al menos uno por cada una de las 13 reglas. La lista completa está en
[data-model.md](./data-model.md#4-casos-borde-del-frontend) e incluye los del pedido.

## F-D9. Corrección de TopBar

**Decisión** (la del pedido): `TopBar` deja de importar `react-router` y recibe el link del logo como slot
`homeLink: ReactNode`, igual que `accountLink`. `AppLayout` lo arma con `Link to="/pizarra"` y el
`aria-label="La Pizarra, ir al inicio"`.

**Consecuencia a tener en cuenta**: hoy el contenido del logo (el SVG de la pelota y el texto "La Pizarra") y sus
estilos (`.logo`, `.logo svg`, `.ball`, `.patch` y `.seams`) están en `TopBar.tsx` y `TopBar.module.css`. Con un slot
`ReactNode`, el que arma el link arma también su contenido: el SVG y esas cinco reglas pasan a `AppLayout.tsx` y
`AppLayout.module.css`, igual que hoy `accountLink` usa `.enter` y `.avatar` de `AppLayout.module.css`. Se mueven sin
cambios, así el DOM y el aspecto quedan iguales (FR-081). Son los únicos cambios de producción (FR-082).

**Cobertura**: `tests/pages/AppNavigation.test.tsx` ya verifica el link por nombre accesible y `href`
(`getByRole('link', { name: 'La Pizarra, ir al inicio' })` con `href="/pizarra"`). No hay test de `TopBar` en
`tests/components/`, y ningún otro test nombra el logo.

**Alternativa considerada**: una render prop (`renderHomeLink: (props) => ReactNode`) que deje el SVG y la clase en
`TopBar` y solo pida el componente de link. Cambia menos (sin CSS), pero rompe la simetría con `accountLink` que
eligió el pedido. Queda como opción si el equipo prefiere no mover estilos (punto a confirmar 3 del plan).

## F-D10. Entorno jsdom, no node

**Decisión**: `tests/architecture.test.ts` corre en el entorno por defecto (`jsdom`), sin
`// @vitest-environment node`.

**Motivo (bloqueante)**: `vitest.config.ts` carga `tests/setup.ts` para todos los archivos, y `setup.ts` lee
`window.location` al cargarse y llama a `sessionStorage.clear()` en el `afterEach`. En `node` eso tira
`ReferenceError` y el archivo falla antes de correr. Cambiar `setup.ts` está fuera de alcance. El análisis no usa el
DOM; jsdom solo agrega unos cientos de milisegundos.

## F-D11. Guarda contra un conjunto vacío

**Decisión**: un test más verifica que el glob leyó lo que tiene que leer: `src/main.tsx`, al menos un archivo en cada
carpeta de `src/` con código (`components`, `context`, `hooks`, `pages`, `router`, `services`, `types` y `utils`) y al
menos un test en cada carpeta de `tests/` que revisa F10.2.

**Motivo**: los casos borde usan fuentes en memoria. Si el patrón del glob quedara mal escrito, `analyze({})`
devolvería `[]` y los casos felices pasarían sin revisar nada (Edge Case "una regla que no chequea nada"). En el
backend, eso lo cubre `failOnEmptyShould` (D12) y un test equivalente sobre `ProductionClasses`.

## F-D12. Correcciones al relevamiento del frontend

El prototipo confirmó el relevamiento del pedido, con tres precisiones que **no son violaciones**:

1. `hooks` usa el **paquete** `react-router` (`useBoardFilters`, `useLeaveGuard` y `useMarketFilters`). F5 prohíbe la
   **carpeta** `src/router/`, no el paquete, así que está permitido.
2. `main.tsx` usa `context`, `hooks` y `router`, además de `react`, `react-dom` y `react-router`. Ninguna regla lo
   impide.
3. `services` usa `window.sessionStorage` y `window.localStorage`, no los globales sueltos: F7 tiene que mirar las
   propiedades de `window` (F-D4) para que el caso feliz no pase por no ver nada.

El resto coincide: la única violación es `TopBar.tsx` (F1); `fetch` solo en `services/httpClient.ts`; el
almacenamiento solo en `services/sessionStorage.ts` y `services/themeStorage.ts`; paquetes `react`, `react-dom` y
`react-router`; sin ciclos, sin `export ... from`, sin `import()` y sin tests en `src/`; los 54 tests de `components`,
`hooks`, `services` y `utils` tienen su archivo en `src/`.

## Lo que el plan resuelve distinto del pedido

| # | Punto del pedido | Qué hace el plan | Por qué | Dónde |
|---|---|---|---|---|
| 1 | Fixtures en `architecture/fixture/` | En `ar.edu.unq.desapp.archfixture`, fuera de la aplicación | Spring las escanearía y romperían los 68 `@SpringBootTest` | D13 |
| 2 | B7.1 a B7.4 sin fixtures | Con fixtures y caso borde | Lo pide la spec; Sonar no las marca (alcance Main) | D7, D16 |
| 3 | B7.2 = la constante de la librería | La constante más `Error` | La constante no cubre `Error` y FR-051 sí | D7 |
| 4 | B2 permite todo `shared..` | Solo las excepciones de `shared` | FR-011; `shared` es plano | D9 |
| 5 | B3.5 solo `@Query(nativeQuery = true)` | También `@NativeQuery` y `createNativeQuery` | Edge Cases y FR-016 | D11 |
| 6 | `.because("...")` y verify con `getDetails()` | `.as()` con "porque" y `ViolationReport` en el verify | Mensaje en español con id y porqué (FR-004) | D8, D14 |
| 7 | Identificadores en español | En inglés; en español los nombres de test y los mensajes | Principio VII | D15 |
| 8 | Respaldo de Sonar con `architecture/fixture/**` | `sonar.test.exclusions` con `**/archfixture/**` | Las fixtures son fuentes de test | D16 |
| 9 | `// @vitest-environment node` | Entorno por defecto (jsdom) | `setup.ts` usa `window` y `sessionStorage` | F-D10 |
| 10 | Nueve casos borde en el frontend | Al menos uno por cada una de las 13 reglas, más una guarda contra el glob vacío | FR-007 y Edge Cases | F-D8, F-D11 |
| 11 | Tests en `src/` con `{ts,tsx}` | Cualquier extensión `.test.*` y `.spec.*`; esos archivos no cuentan para F1 a F9 | FR-071 y FR-006 | F-D3 |

## Riesgos conocidos

| Riesgo | Mitigación |
|---|---|
| Una regla encuentra una violación que la spec no prevé | FR-008: se frena en esa regla y se consulta al equipo. El relevamiento de este documento no encontró ninguna en el backend; en el frontend, solo TopBar |
| ArchUnit no resuelve alguna clase de terceros del classpath | Se importa todo el classpath de test; si falta algo, ArchUnit lo informa como advertencia y la regla sigue con lo importado |
| Un falso positivo de F7 por un nombre de propiedad (`obj.fetch`) | La detección mira solo identificadores globales y propiedades de `window`, `globalThis` o `self` (F-D4); hay un caso borde que lo prueba |
| Mover el SVG y la CSS del logo cambia su aspecto | Se mueven sin cambios; `AppNavigation.test.tsx` cubre el link y la revisión manual del quickstart cubre el aspecto |
| Sonar marca algo en las fixtures | D16: diseño sin issues y, si hace falta, `sonar.test.exclusions` con su comentario |
