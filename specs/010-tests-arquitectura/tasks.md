---

description: "Lista de tareas de la feature 010: tests de arquitectura del backend y del frontend"
---

# Tareas: Tests de arquitectura del backend y del frontend

**Entrada**: documentos de diseño de `/specs/010-tests-arquitectura/`

**Prerrequisitos**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md),
[data-model.md](./data-model.md), [contracts/architecture-rules.md](./contracts/architecture-rules.md),
[quickstart.md](./quickstart.md)

**Tests**: SÍ, son la feature. Cada regla tiene un caso feliz contra el código real y al menos un caso borde que
detecta una violación armada a propósito (FR-007, SC-002). **Sin TDD**. Convenciones (Principio IV y D15): AssertJ en
el backend, setup / execute / verify en tres bloques, nombres de test y mensajes en español
(`lasCapasRespetanLaDireccionControllerServicioPersistenciaYAdapter`, `it('ningún componente usa ...')`),
identificadores en inglés (`result`, `rule`, `analyze`, `sources`, `mayUse`).

**Organización**: por historia de usuario (US1 backend, US2 frontend, US3 README), en el orden de prioridad de la spec.
US1 y US2 no comparten código y se pueden hacer en paralelo; US3 va al final, sobre las reglas ya implementadas.

## Formato: `[ID] [P?] [Historia] Descripción`

- **[P]**: se puede hacer en paralelo (archivos distintos, sin dependencias pendientes)
- **[US#]**: historia de usuario a la que pertenece la tarea
- Cada descripción lleva la ruta exacta del archivo

## Reglas que valen para todas las tareas

- **FR-008**: si una regla encuentra una violación que la spec no prevé, se frena esa regla y se consulta al equipo. No
  se agregan excepciones por clase o archivo, no se usa `FreezingArchRule`, no se crea `archunit.properties` y no se
  desactiva ninguna parte de una regla.
- **FR-082 y SC-006**: el único cambio de producción es `TopBar` y `AppLayout`, con sus CSS Modules (T045 y T046).
- **Principio IV**: ningún test existente se modifica ni se borra; `frontend/tests/setup.ts` no se toca.
- **Textos de las reglas**: el identificador, el nombre y el porqué de cada regla se copian **tal cual** de
  [contracts/architecture-rules.md](./contracts/architecture-rules.md) (secciones 2 y 3), y el formato del mensaje, de
  la sección 1 de ese archivo.

---

## Fase 1: Setup (punto de partida)

**Propósito**: confirmar que los dos lados están en verde antes de tocar nada y tener la referencia de tiempos para
SC-005.

- [X] T001 Verificar el punto de partida del backend: en `backend/`, `./gradlew build` da BUILD SUCCESSFUL con el código actual; anotar cuánto tarda la tarea `test` (referencia de SC-005) y confirmar en `backend/build.gradle` que no hay ninguna dependencia de ArchUnit
  - 2026-11-14: `./gradlew clean build` da BUILD SUCCESSFUL en 7 min 56 s; la tarea `test` tarda 5 min 33 s (2028 tests, 0 fallas, 2 omitidos). `build.gradle` no tiene ArchUnit.
- [X] T002 [P] Verificar el punto de partida del frontend: en `frontend/`, `npm ci`, `npm test`, `npm run lint` y `npm run build` terminan sin errores, y `frontend/vitest.config.ts` incluye `tests/**/*.test.{ts,tsx}`, usa jsdom y carga `tests/setup.ts` (F-D2, F-D10)
  - 2026-11-14: los cuatro comandos terminan sin errores; `npm test` pasa 85 archivos y 659 tests en 177 s. `vitest.config.ts` cumple los tres puntos.

---

## Fase 2: Fundacional (puerta G3)

**Propósito**: cerrar la puerta G3 del plan, que decide dos puntos que usan US1 y US2.

**⚠️ CRÍTICO**: sin esta confirmación no se empiezan las fixtures de B7.1 a B7.4 (US1) ni la corrección de TopBar
(US2).

- [X] T003 Confirmar con el equipo la puerta G3 (ajustes 3 a 12 de la tabla "Lo que el plan resuelve distinto del pedido") y los dos puntos abiertos: fixtures con caso borde para B7.1 a B7.4 y slot `homeLink: ReactNode` para el logo (con el SVG y las cinco reglas de CSS movidas a `AppLayout`). Si no hay objeción, quedan las opciones del plan. Registrar la respuesta y la fecha en la tabla "Puertas antes de implementar" de `specs/010-tests-arquitectura/plan.md`

**Checkpoint**: decisiones cerradas; US1 y US2 pueden arrancar en paralelo.

---

## Fase 3: Historia 1 - Las reglas de arquitectura del backend se verifican solas (Prioridad: P1) 🎯 MVP

**Objetivo**: 20 reglas de ArchUnit (B1 a B7.5) que corren dentro de `./gradlew test` y `./gradlew build`, sin Spring,
sin base y sin red, con mensaje en español (`[id] nombre, porque porqué` + `Infractores (n):`), un caso feliz contra
las 447 clases de producción y al menos un caso borde contra fixtures fuera de `ar.edu.unq.desapp.futbolmarket`.

**Prueba independiente**: en `backend/`, `./gradlew test --tests "ar.edu.unq.desapp.futbolmarket.architecture.*"`
pasa (20 casos felices, la guarda del import y 25 casos borde); `./gradlew build` completo pasa con los 68
`@SpringBootTest` sin cambios.

### Infraestructura de las reglas

- [X] T004 [US1] Agregar `testImplementation 'com.tngtech.archunit:archunit:1.5.1'` en el bloque `dependencies` de `backend/build.gradle`, debajo de los `testImplementation` existentes (solo esa línea; ni `archunit-junit5` ni `archunit-junit6`), y comprobar con `./gradlew dependencies --configuration testRuntimeClasspath` que resuelve junto a JUnit 6.0.3 (D1)
  - 2026-11-14: `archunit:1.5.1` resuelve en `testRuntimeClasspath` y trae `junit-platform-engine` 6.0.3, la misma versión que el resto.
- [X] T005 [P] [US1] Crear `ProductionClasses` (clase final, constructor privado, holder perezoso) con `static JavaClasses get()` que importa una sola vez `ar.edu.unq.desapp.futbolmarket` con `new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)` (D2, data-model 2.2) en `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/architecture/ProductionClasses.java`
- [X] T006 [P] [US1] Crear `ViolationReport` con `static String of(ArchRule rule, EvaluationResult result)` que devuelve `rule.getDescription()`, la línea `Infractores (<n>):` y una línea `- <detalle>` por cada elemento de `result.getFailureReport().getDetails()` (D14, data-model 2.3) en `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/architecture/ViolationReport.java`
- [X] T007 [P] [US1] Crear `ArchitectureRules` (clase final, constructor privado) con las constantes públicas `PRODUCTION_BASE = "ar.edu.unq.desapp.futbolmarket"` y `FIXTURE_BASE = "ar.edu.unq.desapp.archfixture"` y el helper privado `named(ArchRule rule, String id, String name, String reason)` que devuelve `rule.as("[" + id + "] " + name + ", porque " + reason)`, sin `.because()` (D8, data-model 2.1), en `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/architecture/ArchitectureRules.java`

### Reglas en `ArchitectureRules` (mismo archivo: en orden)

- [X] T008 [US1] Agregar B1 `layers(String base)` con el `layeredArchitecture()` exacto de D5 (`consideringOnlyDependenciesInAnyPackage(base + "..")`, capas Controller, Service, Persistence y Adapter, capas opcionales Security y Bootstrap = solo `base`, Controller y Security sin accesos, Service solo desde Controller, Security y Bootstrap, Persistence y Adapter solo desde Service) y B6 `serviceContextsWithoutCycles(String base)` con `slices().matching(base + ".service.(*)..").should().beFreeOfCycles()` (D6), cada una envuelta con `named(...)`, en `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/architecture/ArchitectureRules.java`
- [X] T009 [US1] Agregar B2 `pureModel(String base)`: las clases de `base + ".modelo.."` solo dependen de clases en `java..`, `base + ".modelo.."` o `lombok..`, o de clases de `base + ".shared"` que sean `assignableTo(Throwable.class)` (D9; no todo `shared`), en `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/architecture/ArchitectureRules.java`
- [X] T010 [US1] Agregar B3.1 `jpaOnlyInPersistenceSql`, B3.2 `entities`, B3.3 `sqlDaos`, B3.4 `sqlDaoAndMapperAccess` (`CompositeArchRule`: SQLDAO solo desde `persistence.repository..`; mappers solo desde `persistence.repository..` o `persistence.mapper..`), B3.5 `noNativeQueries` (`CompositeArchRule`: ningún método con `@Query` y `nativeQuery = true` leído con `JavaAnnotation.get("nativeQuery")`, ninguno con `@NativeQuery`, ninguna llamada a `createNativeQuery` de `EntityManager` ni de la `Session` de Hibernate, D11) y B3.6 `serviceWithoutEntities`, todas con `base` y con las formas de la tabla de D17, en `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/architecture/ArchitectureRules.java`
- [X] T011 [US1] Agregar B4.1 `restControllers`, B4.2 `controllerDtos` (`CompositeArchRule`: lo de `base + ".controller.*.dto.."` solo desde `base + ".controller.."`, y las `areTopLevelClasses()` de ese paquete terminan en `Request` o `Response`, FR-022) y B4.3 `endpointsReturnNoModel` con la condición propia de D10: para los métodos públicos de clases `@RestController`, recorre `getReturnType().getAllInvolvedRawTypes()` y, por cada tipo de `base + ".modelo.."` o `base + ".persistence.."`, agrega el evento `El endpoint <método completo> devuelve <tipo> (dentro de <retorno completo>)` (data-model 2.1), en `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/architecture/ArchitectureRules.java`
- [X] T012 [US1] Agregar B5.1 `providerDtos`, B5.2 `independentAdapter` y B5.3 `httpClientsOnlyInAdapterAndConfig` (fuera de `adapter..` y `config..` nadie depende de `org.springframework.web.client..`, `org.springframework.web.reactive.function.client..`, `org.springframework.http.client..` ni `java.net.http..`), con las formas de D17, en `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/architecture/ArchitectureRules.java`
- [X] T013 [US1] Agregar B7.1 `noStandardStreams()`, B7.3 `noJavaUtilLogging()` y B7.4 `noFieldInjection()` a partir de las constantes de `GeneralCodingRules`; B7.2 `noGenericExceptions()` como `noClasses().should(THROW_GENERIC_EXCEPTIONS.or(<crear un Error>))`, donde la condición propia detecta la llamada a un constructor declarado en `Error` desde una clase que no es subclase de `Error` (D7); y B7.5 `singleExceptionHandler(String base)`: las clases `areMetaAnnotatedWith(ControllerAdvice.class)` viven en `base + ".shared"` y una condición propia que cuenta en `init` y, en `finish`, si no hay exactamente una, agrega `Hay <n> manejadores globales de excepciones (<nombres>): tiene que haber exactamente uno, en shared` (D17, data-model 2.1), en `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/architecture/ArchitectureRules.java`

### Casos felices (código real)

Forma de cada test (data-model 2.4): setup con `ProductionClasses.get()` y la regla con `PRODUCTION_BASE`; execute con
`rule.evaluate(classes)`; verify con
`assertThat(result.getFailureReport().getDetails()).withFailMessage(() -> ViolationReport.of(rule, result)).isEmpty()`.

- [X] T014 [P] [US1] Crear `LayerRulesTest` con los casos felices de B1 y B6 y la guarda `elImportDeProduccionNoIncluyeTestsNiFixtures` (`ProductionClasses.get()` contiene `FutbolMarketApplication` y no contiene `LayerRulesTest` ni ninguna clase de `ar.edu.unq.desapp.archfixture`) en `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/architecture/LayerRulesTest.java`
- [X] T015 [P] [US1] Crear `ModelRulesTest` con el caso feliz de B2 en `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/architecture/ModelRulesTest.java`
- [X] T016 [P] [US1] Crear `PersistenceRulesTest` con los seis casos felices de B3.1 a B3.6 en `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/architecture/PersistenceRulesTest.java`
- [X] T017 [P] [US1] Crear `ControllerRulesTest` con los tres casos felices de B4.1 a B4.3 en `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/architecture/ControllerRulesTest.java`
- [X] T018 [P] [US1] Crear `AdapterRulesTest` con los tres casos felices de B5.1 a B5.3 en `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/architecture/AdapterRulesTest.java`
- [X] T019 [P] [US1] Crear `CodingRulesTest` con los cinco casos felices de B7.1 a B7.5 (el de B7.1 se llama `ningunaClaseUsaLaSalidaEstandar`, como en el escenario B del quickstart) en `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/architecture/CodingRulesTest.java`
- [X] T020 [US1] Correr en `backend/` `./gradlew test --tests "ar.edu.unq.desapp.futbolmarket.architecture.*"`: pasan los 20 casos felices y la guarda, y en la salida no aparece el banner de Spring Boot. Si una regla falla por algo que la spec no prevé, frenar esa regla y consultar al equipo (FR-008); no aflojarla
  - 2026-11-14: BUILD SUCCESSFUL; pasan los 21 tests (20 casos felices y la guarda), 0 fallas, sin banner de Spring. Las seis clases de `architecture` suman unos 9 s, casi todo el import único de producción. Ninguna regla encontró violaciones.

### Fixtures (`ar.edu.unq.desapp.archfixture`, fuera del escaneo de Spring, D13)

Todas las clases son mínimas y sin issues de Sonar (D16): cada campo y cada parámetro se usa, ningún método ni clase
queda vacío, inyección por constructor (salvo `FieldInjectedService`) y un comentario de una línea que dice qué regla
rompen a propósito o que son de apoyo. La lista completa está en data-model, sección 3.

- [ ] T021 [US1] Crear las fixtures de apoyo de modelo y persistencia en `backend/src/test/java/ar/edu/unq/desapp/archfixture/`: `modelo/sample/SampleItem.java` (modelo puro), `persistence/sql/entity/sample/SampleSQL.java` (`@Entity` con `@Id`), `persistence/sql/interfaces/sample/SampleSQLDAO.java` (`interface SampleSQLDAO extends JpaRepository<SampleSQL, Long>`), `persistence/mapper/sample/SampleMapper.java` (`SampleSQL` -> `SampleItem`) y `persistence/repository/sample/SampleRepository.java` (usa `SampleSQLDAO` y `SampleMapper`, devuelve `SampleItem`)
- [ ] T022 [US1] Crear las fixtures de apoyo de servicio, controller, adapter y shared en `backend/src/test/java/ar/edu/unq/desapp/archfixture/`: `service/sample/SampleService.java` (usa `SampleRepository` y devuelve `SampleItem`), `controller/sample/dto/SampleResponse.java` (`record` con un `record Line` anidado), `controller/sample/SampleController.java` (`@RestController` que usa `SampleService` y devuelve `SampleResponse`), `adapter/sample/dto/SampleProviderDto.java` (`record`), `adapter/sample/SampleAdapter.java` (traduce `SampleProviderDto` a `SampleItem`) y `shared/SampleExceptionHandler.java` (`@RestControllerAdvice` con un `@ExceptionHandler`)
- [ ] T023 [P] [US1] Crear las fixtures que rompen B1, B2 y B3.1 en `backend/src/test/java/ar/edu/unq/desapp/archfixture/`: `controller/sample/RepositoryAwareController.java` (su constructor recibe `SampleRepository`), `modelo/sample/TableMappedItem.java` (lleva `@jakarta.persistence.Table`) y `persistence/repository/sample/LockingRepository.java` (usa `jakarta.persistence.LockModeType`)
- [ ] T024 [P] [US1] Crear las fixtures que rompen B3.2 a B3.6 en `backend/src/test/java/ar/edu/unq/desapp/archfixture/`: `persistence/sql/entity/sample/SampleRecord.java` (`@Entity` con `@Id`, sin sufijo `SQL`), `persistence/sql/interfaces/sample/SampleDao.java` (extiende `JpaRepository<SampleSQL, Long>` sin sufijo `SQLDAO`), `service/sample/DirectDaoService.java` (usa `SampleSQLDAO`), `service/sample/MapperAwareService.java` (usa `SampleMapper`), `persistence/sql/interfaces/sample/NativeQuerySQLDAO.java` (`findAllNative` con `@Query(value = ..., nativeQuery = true)` y `findAllWithNativeQuery` con `@NativeQuery`), `persistence/sql/NativeQueryRunner.java` (recibe `EntityManager` por constructor y llama a `createNativeQuery`) y `service/sample/EntityAwareService.java` (un método recibe `SampleSQL`)
- [ ] T025 [P] [US1] Crear las fixtures que rompen B4.1 a B4.3 en `backend/src/test/java/ar/edu/unq/desapp/archfixture/`: `controller/sample/SampleEndpoint.java` (`@RestController` sin sufijo `Controller`), `controller/sample/dto/SampleDto.java` (clase de primer nivel sin `Request` ni `Response`), `service/sample/DtoAwareService.java` (usa `SampleResponse`) y `controller/sample/ModelLeakController.java` (cuatro endpoints públicos: `SampleItem item()`, `ResponseEntity<List<SampleItem>> items()`, `SampleItem[] itemArray()` y `Optional<SampleSQL> entity()`)
- [ ] T026 [P] [US1] Crear las fixtures que rompen B5.1 a B5.3 y B6 en `backend/src/test/java/ar/edu/unq/desapp/archfixture/`: `service/sample/ProviderDtoAwareService.java` (usa `SampleProviderDto`), `adapter/sample/ServiceAwareAdapter.java` (usa `SampleService`), `service/sample/HttpCallingService.java` (usa `java.net.http.HttpClient`), `service/alpha/AlphaService.java` y `service/beta/BetaService.java` (se usan mutuamente, por ejemplo un método de cada una recibe a la otra, sin inyección circular)
- [ ] T027 [P] [US1] Crear las fixtures que rompen B7.1 a B7.5 en `backend/src/test/java/ar/edu/unq/desapp/archfixture/`: `service/sample/StandardStreamService.java` (escribe en `System.out`), `service/sample/GenericExceptionService.java` (un método crea y lanza un `RuntimeException` y otro un `Error`), `service/sample/JavaUtilLoggingService.java` (usa `java.util.logging.Logger`), `service/sample/FieldInjectedService.java` (campo con `@Autowired` que usa en un método) y `controller/sample/StrayExceptionHandler.java` (segundo `@RestControllerAdvice`, fuera de `shared`)

### Casos borde (`ArchitectureRulesDetectViolationsTest`, mismo archivo: en orden)

Forma de cada test (data-model 2.4): setup con la regla armada con `FIXTURE_BASE`; execute con
`rule.evaluate(FIXTURES)`; verify con `assertThat(result.hasViolation()).isTrue()` y
`assertThat(result.getFailureReport().getDetails()).anyMatch(detail -> detail.contains("<infractor>"))`. Los nombres
dicen la regla y la violación (`laReglaDeCapasDetectaUnControllerQueUsaUnRepository`).

- [ ] T028 [US1] Crear `ArchitectureRulesDetectViolationsTest` con la constante `FIXTURES = new ClassFileImporter().importPackages(ArchitectureRules.FIXTURE_BASE)` y los casos borde de B1 (nombra `RepositoryAwareController`), B2 (nombra `TableMappedItem`) y B3.1 (nombra `TableMappedItem` y `LockingRepository`) en `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/architecture/ArchitectureRulesDetectViolationsTest.java`
- [ ] T029 [US1] Agregar los ocho casos borde de persistencia: B3.2 (`SampleRecord`), B3.3 (`SampleDao`), B3.4 con SQLDAO (`DirectDaoService`) y con mapper (`MapperAwareService`), B3.5 con `@Query` nativa (`findAllNative`), con `@NativeQuery` (`findAllWithNativeQuery`) y con `createNativeQuery` (`NativeQueryRunner`), y B3.6 (`EntityAwareService`, y ningún detalle nombra `SampleService`) en `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/architecture/ArchitectureRulesDetectViolationsTest.java`
- [ ] T030 [US1] Agregar los cuatro casos borde de controllers: B4.1 (`SampleEndpoint`), B4.2 de nombres (`SampleDto`, y ningún detalle nombra `SampleResponse$Line`), B4.2 de uso (`DtoAwareService`) y B4.3 (`laReglaDeEndpointsDetectaModeloDirectoEnUnaListaYEnUnArreglo`: los detalles nombran `item`, `items`, `itemArray` y `entity`) en `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/architecture/ArchitectureRulesDetectViolationsTest.java`
- [ ] T031 [US1] Agregar los cuatro casos borde de adapter y ciclos: B5.1 (`ProviderDtoAwareService`), B5.2 (`ServiceAwareAdapter`), B5.3 (`HttpCallingService`) y B6 (el ciclo entre las slices `alpha` y `beta`, nombrando `AlphaService` y `BetaService`) en `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/architecture/ArchitectureRulesDetectViolationsTest.java`
- [ ] T032 [US1] Agregar los seis casos borde de reglas generales: B7.1 (`StandardStreamService`), B7.2 con `RuntimeException` y B7.2 con `Error` (`laReglaDeExcepcionesGenericasDetectaUnError`; los dos nombran `GenericExceptionService`), B7.3 (`JavaUtilLoggingService`), B7.4 (`FieldInjectedService`) y B7.5 (`StrayExceptionHandler` y el conteo `Hay 2 manejadores`) en `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/architecture/ArchitectureRulesDetectViolationsTest.java`

### Verificación de la historia

- [ ] T033 [US1] Correr en `backend/` `./gradlew test --tests "ar.edu.unq.desapp.futbolmarket.architecture.*"` (20 felices, la guarda y 25 casos borde) y después `./gradlew build` completo: pasan los 68 `@SpringBootTest` y el test de slice sin cambios (Spring no ve `archfixture`, D13), y las clases de `architecture` suman menos de 30 s según `backend/build/reports/tests/test/index.html` (SC-005)
- [ ] T034 [US1] Prueba manual del mensaje (quickstart, escenario B; escenario 9 de la US1): agregar a la vez un `System.out.println("prueba");` en un método de una clase de `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/service/` y un repository en el constructor de un controller de `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/controller/`; correr el comando filtrado y confirmar que fallan `CodingRulesTest` y `LayerRulesTest`, cada uno con `[id] nombre, porque porqué`, `Infractores (n):` y la clase infractora (SC-004). Descartar los dos cambios con `git checkout -- <archivo>` y volver a correr en verde

**Checkpoint**: US1 completa. Sola, cumple el ítem Core "Crear un test de arquitectura" de la entrega 3.

---

## Fase 4: Historia 2 - Las reglas de arquitectura del frontend se verifican solas (Prioridad: P2)

**Objetivo**: 13 reglas (F1 a F10.2) en un solo archivo, `frontend/tests/architecture.test.ts`, sin dependencias
nuevas: lee `src/` con `import.meta.glob`, lo parsea con el compilador de TypeScript y aplica una función pura,
`analyze(sources)`, igual sobre el código real y sobre fuentes en memoria. Corrige `TopBar`, la única violación de hoy.

**Prueba independiente**: en `frontend/`, `npx vitest run tests/architecture.test.ts` pasa con TopBar corregido (13
casos de código real, la guarda y 23 casos borde), y `npm test` pasa con `tests/pages/AppNavigation.test.tsx` sin
cambios.

### Análisis (`frontend/tests/architecture.test.ts`, mismo archivo: en orden)

- [ ] T035 [US2] Crear `frontend/tests/architecture.test.ts`, en la raíz de `tests/` y sin `// @vitest-environment node` (F-D2, F-D10), con los tipos `Sources`, `DependencyRule` y `Violation` de data-model 5.1 y la lectura de fuentes de F-D3: `import.meta.glob<string>('../src/**/*.{ts,tsx}', { query: '?raw', import: 'default', eager: true })` para el código; las claves de `import.meta.glob('../src/**/*.{test,spec}.*')` (sin `eager`) para F10.1; las claves de `import.meta.glob('./{components,hooks,services,utils}/**/*.test.{ts,tsx}')` (sin `eager`) para F10.2. Normalizar las claves a rutas relativas a `frontend/` (`src/components/TopBar.tsx`, `tests/utils/format.test.ts`) y juntarlas en un solo `Record<string, string>` (los tests, con contenido vacío)
- [ ] T036 [US2] Agregar el parseo y la clasificación de imports de F-D4 y data-model 5.2 en `frontend/tests/architecture.test.ts`: `ts.createSourceFile(path, code, ts.ScriptTarget.Latest, true, kind)` con `ScriptKind.TSX` o `TS` según la extensión; destinos de `ImportDeclaration` (incluido `import type`), `ExportDeclaration` con módulo e `import('...')` con literal; `.css` se ignora; un relativo se normaliza contra la carpeta del archivo (fuera de `src/` es F8.2, adentro toma el grupo de la ruta normalizada aunque el archivo no exista); `/...` es fuera de `src/`; el resto es `package:<@scope/nombre o primer segmento>`; grupo de un archivo = carpeta de primer nivel de `src/` o su nombre si está en la raíz (`main.tsx`); archivo de producción = clave de `src/` que no matchea `.test.` ni `.spec.` (FR-006)
- [ ] T037 [US2] Agregar la tabla declarativa de F1 a F6.2 (data-model 5.3: `appliesTo` como lista o `{ except: [...] }`, `mayUse` o `mayNotUse`, `because` copiado de contracts sección 3) y su evaluación sobre los archivos de producción: el propio grupo siempre está permitido en las listas blancas, y el mensaje es `[<id>] <archivo> usa <import>: <porqué> (Constitución, sección Frontend)`, en `frontend/tests/architecture.test.ts`
- [ ] T038 [US2] Agregar F7 en `frontend/tests/architecture.test.ts`: fuera de `services/`, informar cada uso de `fetch`, `sessionStorage` o `localStorage` como valor global o como propiedad de `window`, `globalThis` o `self` (`.x` o `['x']`); no cuentan los nombres de propiedad de otros objetos (`config.fetch`), los nombres en declaraciones de tipos o de propiedades, los literales de imports (`../services/sessionStorage`) ni los comentarios (F-D4, F-D12 punto 3). Mensaje `[F7] <archivo> usa <identificador>: ...`
- [ ] T039 [US2] Agregar F8.1 (solo `react`, `react-dom` y `react-router` comparando el nombre del paquete: `react-dom/client` pasa y `react-router-dom` no), F8.2 (ningún import fuera de `src/`, `mockups/` incluido), F9 (grafo de archivos de producción resolviendo la ruta tal cual, `.ts`, `.tsx`, `/index.ts` y `/index.tsx`; DFS con colores; cada ciclo una vez, rotado para empezar por su archivo menor: `[F9] ciclo a -> b -> a: ...`, F-D6), F10.1 (claves de `src/` con `.test.` o `.spec.`: `[F10.1] <archivo> es un test dentro de src/: ...`) y F10.2 (cada `tests/<components|hooks|services|utils>/**/X.test.ts(x)` tiene `src/<misma carpeta y subruta>/X.ts` o `X.tsx`; `tests/pages/`, `tests/mocks/` y `tests/setup.ts` quedan afuera: `[F10.2] <test> no tiene src/<carpeta>/<nombre>.ts ni .tsx: ...`) en `frontend/tests/architecture.test.ts`
- [ ] T040 [US2] Agregar `analyze(sources: Sources): Violation[]`, pura, que junta todas las reglas (F1 a F9 solo sobre archivos de producción), y el helper `messagesOf(violations, ruleId)` que filtra por regla para que cada una se informe por separado (FR-005, F-D5, F-D7), en `frontend/tests/architecture.test.ts`

### Tests del análisis

- [ ] T041 [US2] Agregar el `describe` "código real" en `frontend/tests/architecture.test.ts`: la guarda de F-D11 (están `src/main.tsx`, al menos un archivo en `components`, `context`, `hooks`, `pages`, `router`, `services`, `types` y `utils`, y al menos un test en cada carpeta de `tests/` que revisa F10.2) y un `it` por cada una de las 13 reglas (setup: las fuentes del glob; execute: `analyze(sources)`; verify: `expect(messagesOf(violations, '<id>')).toEqual([])`), con nombres en español
- [ ] T042 [US2] Agregar el `describe` "casos borde" con los casos 1 a 11 de data-model sección 4 (F1 a F6.2: services, `import type` de hooks, `react-router`, re-export, la frontera del CSS Module con `types`, `utils` y `react`, y los de F2, F3, F4, F5, F6.1 e `import()` dinámico de F6.2), cada uno con su `Record<string, string>` en memoria y el mensaje esperado, en `frontend/tests/architecture.test.ts`
- [ ] T043 [US2] Agregar al `describe` "casos borde" los casos 12 a 23 de data-model sección 4 (F7 con `fetch`, con `window.sessionStorage` y `globalThis.localStorage`, y la frontera de `SessionContext`; F8.1 con `@mui/material` y con `react-router-dom` frente a `react-dom/client`; F8.2 con `mockups/`; F9 directo e indirecto; F10.1 y su frontera de FR-006; F10.2 y su frontera con `useCatalog.test.tsx` y `tests/pages/`) en `frontend/tests/architecture.test.ts`
- [ ] T044 [US2] Correr en `frontend/` `npx vitest run tests/architecture.test.ts` con TopBar **sin corregir**: falla solo el caso de código real de F1, con `[F1] src/components/TopBar.tsx usa react-router: ...` (escenario 2 de la US2, quickstart escenario C), y pasan la guarda, los demás casos de código real y los 23 casos borde. Si aparece otra violación, frenar esa regla y consultar al equipo (FR-008)

### Corrección de TopBar (único cambio de producción)

- [ ] T045 [US2] En `frontend/src/components/TopBar.tsx`, sacar `import { Link } from 'react-router'`, agregar la prop obligatoria `homeLink: ReactNode` (como `accountLink`) y dibujar `{homeLink}` donde estaba el `Link` del logo, ajustando el comentario de cabecera; en `frontend/src/components/TopBar.module.css`, sacar `.logo`, `.logo svg`, `.ball`, `.patch` y `.seams` (antes, confirmar que ninguna otra regla de ese archivo los nombra) (FR-080, F-D9)
- [ ] T046 [US2] En `frontend/src/pages/AppLayout.tsx`, armar `homeLink` con `<Link to="/pizarra" className={styles.logo} aria-label="La Pizarra, ir al inicio">`, el mismo SVG (clases `styles.ball`, `styles.patch` y `styles.seams`) y el texto "La Pizarra", y pasarlo a `TopBar`; copiar sin cambios las cinco reglas sacadas en T045 a `frontend/src/pages/AppLayout.module.css` (FR-080, FR-081)
- [ ] T047 [US2] Correr en `frontend/` `npx vitest run tests/architecture.test.ts` (todo verde, en menos de 30 s, SC-005), `npm test` (pasa `tests/pages/AppNavigation.test.tsx` sin cambios), `npm run lint` y `npm run build` (el `tsc -b` revisa también el test nuevo)
- [ ] T048 [US2] Revisión manual del logo (quickstart, escenario F; escenario 3 de la US2) con `./gradlew bootRun` en `backend/` y `npm run dev` en `frontend/`: se ve igual en modo claro y oscuro, el clic y Enter (con foco visible por Tab) llevan a `/pizarra` y se anuncia como link "La Pizarra, ir al inicio" (FR-081)

**Checkpoint**: US2 completa; las 33 reglas se verifican en cada corrida local y de CI (SC-001).

---

## Fase 5: Historia 3 - Las reglas quedan documentadas para la entrega (Prioridad: P3)

**Objetivo**: el README tiene la sección "Tests de arquitectura" con las 33 reglas y los comandos, lista para copiar a
las Release Notes de la entrega 3.

**Prueba independiente**: alguien que no implementó la feature encuentra en el README, para cada regla, qué verifica,
por qué existe y con qué comando se corre, en menos de 5 minutos y sin abrir los tests (SC-007).

- [ ] T049 [US3] Agregar en `README.md` la sección `## Tests de arquitectura`, después de `## Tests` y antes de `## Estructura`: qué hacen y cuándo fallan; qué hacer ante un fallo (se corrige el código, no la regla; FR-008); la tabla de comandos (contracts sección 4); la tabla de las 20 reglas del backend y la de las 13 del frontend (Id, Nombre, Qué verifica, Por qué: principio o sección de la constitución) desde contracts secciones 2 y 3, aclarando en B1 que tampoco se depende de `security`; y cómo leer un fallo (quickstart sección 6), en breve (FR-083)
- [ ] T050 [US3] Verificar `README.md` contra el código: cada uno de los 33 identificadores (B1, B2, B3.1 a B3.6, B4.1 a B4.3, B5.1 a B5.3, B6, B7.1 a B7.5, F1 a F5, F6.1, F6.2, F7, F8.1, F8.2, F9, F10.1 y F10.2) aparece en las tablas con el mismo nombre y el mismo porqué que en `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/architecture/ArchitectureRules.java` y `frontend/tests/architecture.test.ts` (escenarios 1 y 2 de la US3)

**Checkpoint**: las tres historias están completas.

---

## Fase 6: Pulido y cierre

**Propósito**: calidad (Principio V) y la definición de terminado del quickstart.

- [ ] T051 [P] Revisar las 35 fixtures de `backend/src/test/java/ar/edu/unq/desapp/archfixture/` y las clases de `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/architecture/` contra D16 y el Principio V (sin campos ni parámetros sin usar, sin métodos ni clases vacías, sin duplicación). Solo si el análisis de SonarCloud marca issues en las fixtures, agregar `property 'sonar.test.exclusions', '**/archfixture/**'` con un comentario del porqué en el bloque `sonar` de `backend/build.gradle`
- [ ] T052 Recorrer la definición de terminado de `specs/010-tests-arquitectura/quickstart.md` (sección 9): `./gradlew build` en `backend/`; `npm test`, `npm run lint` y `npm run build` en `frontend/`; `git diff --stat develop` muestra como producción solo `TopBar.tsx`, `TopBar.module.css`, `AppLayout.tsx` y `AppLayout.module.css`, en `backend/build.gradle` solo la línea de ArchUnit (y la de T051 si hizo falta), ningún test existente modificado ni borrado y `frontend/tests/setup.ts` sin cambios; y no hay `FreezingArchRule`, `archunit.properties` ni excepciones por clase o archivo (FR-008)

---

## Dependencias y orden de ejecución

### Dependencias entre fases

- **Setup (Fase 1)**: sin dependencias.
- **Fundacional (Fase 2)**: después del Setup. Bloquea las fixtures de B7 (T027) y la corrección de TopBar (T045 y
  T046); en la práctica, se cierra antes de empezar cualquier historia.
- **US1 (Fase 3) y US2 (Fase 4)**: después de la Fase 2. Son independientes entre sí (backend y frontend, sin archivos
  compartidos) y se pueden hacer en paralelo.
- **US3 (Fase 5)**: después de US1 y US2, porque documenta las reglas ya implementadas (plan, paso 5).
- **Pulido (Fase 6)**: al final.

### Dentro de US1

- T004 (la dependencia) antes de todo lo demás de la historia.
- T005, T006 y T007 en paralelo; T008 a T013 en orden (mismo archivo), después de T007.
- Cada caso feliz depende de sus reglas y de T005 y T006: T014 de T008; T015 de T009; T016 de T010; T017 de T011;
  T018 de T012; T019 de T013. T020 cierra los casos felices.
- Fixtures: T021 antes de T022; T023 a T027 en paralelo, después de T022 (T027, además, después de T003).
- Casos borde en orden (mismo archivo): T028 (de T023), T029 (de T024), T030 (de T025), T031 (de T026), T032 (de T027).
- T033 y T034 cierran la historia.

### Dentro de US2

- T035 a T043 en orden (mismo archivo).
- T044 después de T043 y **antes** de T045: así se ve fallar F1 por TopBar (escenario 2 de la US2).
- T045 antes de T046 (`AppLayout` le pasa a `TopBar` una prop que T045 crea). T047 y T048 cierran la historia.

### Oportunidades de paralelismo

- T001 y T002.
- US1 entera en paralelo con US2 entera.
- En US1: T005, T006 y T007; los seis `*RulesTest` (T014 a T019) en cuanto está su regla; las cinco tareas de
  fixtures que rompen reglas (T023 a T027).
- T051 en paralelo con T049 y T050.

---

## Ejemplos de paralelismo

### Historia 1

```text
# Después de T004:
Tarea: "T005 ProductionClasses en backend/src/test/java/ar/edu/unq/desapp/futbolmarket/architecture/ProductionClasses.java"
Tarea: "T006 ViolationReport en backend/src/test/java/ar/edu/unq/desapp/futbolmarket/architecture/ViolationReport.java"
Tarea: "T007 ArchitectureRules (constantes y named) en backend/src/test/java/ar/edu/unq/desapp/futbolmarket/architecture/ArchitectureRules.java"

# Después de T013:
Tarea: "T014 LayerRulesTest"
Tarea: "T015 ModelRulesTest"
Tarea: "T016 PersistenceRulesTest"
Tarea: "T017 ControllerRulesTest"
Tarea: "T018 AdapterRulesTest"
Tarea: "T019 CodingRulesTest"

# Después de T022:
Tarea: "T023 fixtures de B1, B2 y B3.1"
Tarea: "T024 fixtures de B3.2 a B3.6"
Tarea: "T025 fixtures de B4.1 a B4.3"
Tarea: "T026 fixtures de B5.1 a B5.3 y B6"
Tarea: "T027 fixtures de B7.1 a B7.5"
```

### Historias 1 y 2

```text
# Después de T003, dos personas:
Integrante A: T004 a T034 (backend)
Integrante B: T035 a T048 (frontend)
```

---

## Estrategia de implementación

### MVP primero (solo la Historia 1)

1. Fase 1: punto de partida en verde.
2. Fase 2: puerta G3.
3. Fase 3: US1 (reglas, casos felices, fixtures, casos borde).
4. **PARAR y VALIDAR**: el comando filtrado y `./gradlew build` en verde (T033) y el mensaje leído a mano (T034). Con
   esto ya se cumple el ítem Core de la cátedra.

### Entrega incremental

1. Setup + Fundacional → decisiones cerradas.
2. US1 → reglas del backend (MVP).
3. US2 → reglas del frontend y TopBar corregido.
4. US3 → README listo para las Release Notes.
5. Pulido → Sonar y definición de terminado.

### Con dos integrantes

1. Los dos cierran el Setup y la puerta G3.
2. Integrante A: US1. Integrante B: US2.
3. Cualquiera de los dos: US3 y el pulido, con las dos historias terminadas.

---

## Notas

- [P] = archivos distintos, sin dependencias pendientes.
- [US#] liga cada tarea con su historia; la trazabilidad de cada regla a su FR está en
  [contracts/architecture-rules.md](./contracts/architecture-rules.md), y la de cada fixture a su regla, en
  [data-model.md](./data-model.md), sección 3.
- Algunas fixtures rompen además otra regla de rebote (por ejemplo, `ServiceAwareAdapter` también rompe B1). Los casos
  borde verifican solo la regla que les toca: esas violaciones extra no molestan.
- Las tareas no incluyen commits ni push: los hace el equipo.
- Parar en cada checkpoint para validar la historia por separado.
