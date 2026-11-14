# Plan de implementación: Tests de arquitectura del backend y del frontend

**Rama**: `010-tests-arquitectura` | **Fecha**: 2026-11-12 | **Especificación**: [spec.md](./spec.md)

**Entrada**: especificación de la feature en `/specs/010-tests-arquitectura/spec.md` (con su clarificación del
2026-11-12), la constitución 2.2.1 y el pedido de planificación del 2026-11-12 (decisiones D1 a D12 del backend y F-D1
a F-D9 del frontend), con el código relevado ese mismo día: la rama parte de `af8c73a` (solo la spec) sobre
`967f8e6`, que es `develop` y `main`.

## Resumen

Hoy las reglas de arquitectura de la constitución (Principios I, III, V y VI en el backend; sección Frontend en el
frontend) se controlan a mano en la revisión. Esta feature agrega dos suites de tests que las verifican solas y hacen
fallar el build: **20 reglas del backend** (B1 a B7.5) y **13 del frontend** (F1 a F10.2). Cada regla tiene un
identificador, un mensaje en español que cita la constitución, un caso feliz contra el código real y al menos un caso
borde que prueba que detecta una violación. El único cambio de producción es sacar `react-router` de `TopBar`.

Enfoque técnico:

- **Backend**: ArchUnit 1.5.1 (única dependencia nueva, `testImplementation`, pedida por la cátedra) con la API core
  desde `@Test` de JUnit y AssertJ. Las reglas salen de una fábrica, `ArchitectureRules`, parametrizada por paquete
  base: la misma regla se evalúa contra las 447 clases de producción (importadas una vez, sin tests) y contra
  fixtures que la rompen a propósito. Sin Spring, sin base y sin red.
- **Fixtures del backend fuera de la aplicación**: viven en `ar.edu.unq.desapp.archfixture`, hermano de
  `ar.edu.unq.desapp.futbolmarket`, porque dentro de la aplicación el escaneo de Spring las cargaría en los 68
  `@SpringBootTest` existentes (D13).
- **Frontend**: un solo archivo, `tests/architecture.test.ts`, sin dependencias nuevas. Lee `src/` con
  `import.meta.glob` (`?raw`), lo parsea con el compilador de TypeScript ya instalado y aplica una tabla declarativa
  de reglas con una función pura, `analyze(sources)`, que se usa igual con el código real y con fuentes en memoria.
- **TopBar**: recibe el link del logo como slot (`homeLink: ReactNode`), como ya recibe `accountLink`; `AppLayout` lo
  arma con `Link`. El SVG y los estilos del logo se mueven sin cambios a `AppLayout` (F-D9).
- **CI y documentación**: los workflows no cambian (`./gradlew build` y `npm test` ya corren todo). El README suma la
  sección "Tests de arquitectura" con las dos tablas de reglas y los comandos.

## Decisiones de planificación (2026-11-12)

El pedido resolvió estos puntos antes de escribir el diseño. Este plan los adopta, con el detalle y lo verificado en
[research.md](./research.md):

1. **API core de ArchUnit con `@Test`** y AssertJ, en tres bloques; JUnit 6.0.3 confirmado en el classpath (D1).
2. **Import único** de producción con `DO_NOT_INCLUDE_TESTS` (D2).
3. **Reglas parametrizadas por paquete base** en `ArchitectureRules` (D3).
4. **Paquete de test transversal `architecture/`**, declarado en este plan (D4).
5. **Capas** con `layeredArchitecture()` y `consideringOnlyDependenciesInAnyPackage(base + "..")`; `Security` y
   `Bootstrap` opcionales (D5, verificado en la fuente de 1.5.1).
6. **Ciclos** entre contextos del servicio con `slices()` (D6).
7. **Reglas generales** de `GeneralCodingRules` (D7).
8. **Porqué en español** citando el principio (D8).
9. **Modelo** con lista blanca, Lombok incluido (D9).
10. **Endpoints** con una condición propia sobre `getAllInvolvedRawTypes()` (D10, verificado).
11. **Sin queries nativas** (D11).
12. **`failOnEmptyShould` en `true`**, sin `FreezingArchRule` (D12).
13. **Frontend sin herramientas nuevas**: un archivo en la raíz de `tests/`, `import.meta.glob`, compilador de
    TypeScript, tabla declarativa, DFS para ciclos y casos borde en memoria (F-D1 a F-D8).
14. **TopBar con slot `homeLink`** (F-D9).
15. **CI sin cambios** y **documentación** en el README y en [quickstart.md](./quickstart.md).

### Lo que el plan resuelve distinto del pedido, o que hay que confirmar

Ninguno frena el diseño. Los puntos 1 y 2 son correcciones necesarias; el resto son ajustes para cumplir la spec o la
constitución. Detalle en [research.md](./research.md#lo-que-el-plan-resuelve-distinto-del-pedido).

| # | Punto | Qué hace este plan | Dónde |
|---|---|---|---|
| 1 | **Fixtures en `architecture/fixture/`** | En `ar.edu.unq.desapp.archfixture/`, fuera de la aplicación: si no, Spring escanea sus `@RestController`, `@RestControllerAdvice`, `@Entity` y `JpaRepository` y rompe los 68 `@SpringBootTest` | D13 |
| 2 | **`// @vitest-environment node`** | Entorno por defecto (jsdom): `tests/setup.ts` usa `window` y `sessionStorage` y falla en `node` | F-D10 |
| 3 | **B7.1 a B7.4 sin fixtures** | Con fixtures y caso borde, como pide la spec (FR-007, SC-002, US1-7). Sonar no las marca: `S106`, `S112` y `S6813` no aplican a `src/test` | D7, D16 |
| 4 | **B7.2 = la constante de la librería** | La constante más `Error`, que la constante no cubre y FR-051 sí | D7 |
| 5 | **B2 permite todo `shared..`** | Solo las excepciones base de `shared` (FR-011); `shared` es plano y también tiene `ApiError` y `GlobalExceptionHandler` | D9 |
| 6 | **B3.5 solo `@Query(nativeQuery = true)`** | También `@NativeQuery` y `createNativeQuery` (Edge Cases y FR-016) | D11 |
| 7 | **`.because()` y verify con `getDetails()` solo** | El porqué con `.as("[B1] ..., porque ...")` y el verify con `ViolationReport`: así el encabezado, el id y el porqué salen en español (FR-004) | D8, D14 |
| 8 | **Identificadores en español** (`resultado`, `analizar`, `puedeUsar`, ...) | En inglés (`result`, `analyze`, `mayUse`, ...); en español los nombres de test y los mensajes (Principio VII) | D15 |
| 9 | **Respaldo de Sonar con `architecture/fixture/**`** | `sonar.test.exclusions` con `**/archfixture/**`, solo si hace falta | D16 |
| 10 | **Nueve casos borde del frontend** | Al menos uno por cada una de las 13 reglas, más una guarda contra un glob vacío | F-D8, F-D11 |
| 11 | **Tests dentro de `src/` con `{ts,tsx}`** | Cualquier `.test.*` o `.spec.*`; esos archivos no cuentan para F1 a F9 (FR-006, FR-071) | F-D3 |
| 12 | **TopBar con slot `ReactNode`** | Se adopta. Consecuencia: el SVG y cinco reglas de CSS del logo pasan a `AppLayout`. Alternativa: render prop, sin mover CSS | F-D9 |

## Puertas antes de implementar

| # | Puerta | Estado al 2026-11-12 |
|---|---|---|
| G1 | **La rama tiene todo `develop`** | **Cumplida**: la rama sale de `967f8e6`, que es `develop` |
| G2 | **Aval de un desarrollador para las dos carpetas nuevas del árbol de tests del backend**: `futbolmarket/architecture/` (D4) y `ar/edu/unq/desapp/archfixture/` (D13) | **Cumplida el 2026-11-12**: `architecture/` venía en el pedido; `archfixture/` recibió el aval después de leer este plan |
| G3 | **Confirmación del equipo** de los puntos 3 a 12 de la tabla anterior | **Cumplida el 2026-11-14**: sin objeción a los puntos 3 a 12; B7.1 a B7.4 llevan fixtures con caso borde y el logo va como slot `homeLink: ReactNode` (el SVG y las cinco reglas de CSS pasan a `AppLayout`) |

## Contexto técnico

**Lenguaje y versión**: Java 21 (Temurin) con el wrapper de Gradle; TypeScript 5.9 en modo `strict`.

**Dependencias principales**:

- Backend: Spring Boot 4.1.1, que trae JUnit **6.0.3** y AssertJ 3.27.7 (verificado con
  `./gradlew dependencies --configuration testRuntimeClasspath`; la constitución dice "JUnit 5", pero el arranque de
  Boot 4.1 trae la 6). **Una nueva**: `testImplementation 'com.tngtech.archunit:archunit:1.5.1'`, la última 1.5.x y
  la última publicada en Maven Central. Justificación: la pide la cátedra para el ítem Core "Crear un test de
  arquitectura". Solo el core: ni `archunit-junit5` ni `archunit-junit6`.
- Frontend: Vitest 4.1, TypeScript 5.9 (su API de compilador, ya devDependency), Vite 8 (`import.meta.glob`). **Ninguna
  nueva**.

**Almacenamiento**: no aplica.

**Tests**: JUnit 6 + AssertJ en el backend, sin Spring ni H2; Vitest en el entorno jsdom por defecto en el frontend.
Sin TDD.

**Plataforma**: las máquinas del equipo (Windows) y el CI (Ubuntu, Java 21 y Node 20). Corren dentro de
`./gradlew test`/`build` y `npm test`, que el CI ya ejecuta.

**Tipo de proyecto**: aplicación web en un monorepo (`backend/` y `frontend/`). Se tocan los dos.

**Objetivos de rendimiento**: menos de 30 s por lado (SC-005). Estimación: en el backend, el import de 447 clases y 20
reglas, unos segundos; en el frontend, el parseo de los 129 archivos de `src/` tardó menos de 1 s en el prototipo, y
cada `it` lo repite.

**Restricciones**:

- Sin levantar la aplicación, la base ni el navegador, y sin red (FR-003).
- Sin excepciones por clase o archivo, sin violaciones congeladas y sin reglas apagadas (FR-008). Si aparece una
  violación no prevista, se frena esa regla y se consulta al equipo.
- Ningún test existente se modifica ni se borra (FR-009, Principio IV). `tests/setup.ts` tampoco se toca.
- Único cambio de producción: `TopBar` y `AppLayout`, con sus CSS Modules (FR-080 a FR-082).
- El `build.gradle` suma una línea (la dependencia) y, solo si Sonar lo pide, la exclusión de D16.
- El árbol del frontend es cerrado: ninguna carpeta nueva en `src/` ni en `tests/`.

**Escala y alcance**:

- Backend: 447 clases de producción, 12 controllers, 13 contextos de modelo, 7 de servicio, 2 proveedores.
- Frontend: 129 archivos `.ts`/`.tsx` en `src/` y 85 archivos de test.
- 33 reglas, 33 casos felices y unos 50 casos borde; 34 requisitos funcionales de reglas (FR-010 a FR-072; B4.2
  tiene dos) y 7 criterios de éxito.

## Verificación constitucional

### Antes de la investigación

| Principio o sección | Cómo lo cumple el plan | Estado |
|---|---|---|
| I. Capas estrictas (NO NEGOCIABLE) | No toca código de producción del backend. Las reglas B1 a B6 verifican el principio. El árbol de tests suma `architecture/` y `archfixture/`, declarados aquí y con aval (G2) | Cumple |
| II. Modelo rico | No aplica: no hay lógica nueva | Cumple |
| III. Validación en su nivel | No cambia validaciones. B7.2 (sin excepciones genéricas) y B7.5 (un solo advice en `shared/`) lo verifican | Cumple |
| IV. Tests (NO NEGOCIABLE) | AssertJ; setup, execute y verify; casos felices y borde; nombres en español; sufijo `Test`; sin Spring en estos tests. Ningún test existente cambia. Paquete transversal declarado | Cumple |
| V. Calidad medible | Sin duplicación (`ArchitectureRules` y `ViolationReport` concentran lo común); fixtures sin issues de Sonar (D16) | Cumple |
| VI. Persistencia portable | No toca persistencia. B3.5 verifica que no haya queries nativas | Cumple |
| VII. Idioma | Identificadores en inglés; mensajes, comentarios, nombres de test y documentación en español (ajuste 8 de la tabla) | Cumple (con el ajuste) |
| Stack | Una dependencia nueva, de test, avisada y justificada. El `build.gradle` no se reemplaza | Cumple |
| Frontend | Sin dependencias nuevas, sin carpetas nuevas, tests solo en `tests/`. `TopBar` deja de conocer el router, que es lo que pide la sección | Cumple |
| Definición de terminado | `./gradlew build`; `npm test`, `npm run lint` y `npm run build`; la app sigue levantando (no cambia nada que afecte `bootRun`); sin endpoints nuevos | Cumple |

### Después del diseño

| Punto | Revisión | Estado |
|---|---|---|
| Fixtures y Spring | Fuera de `futbolmarket`: ni el escaneo de componentes, ni el de entidades, ni el de repositories de Spring Data las ven (D13). `ProductionClasses` tampoco | Cumple |
| Reglas contra el código actual | Las 20 del backend se probaron a mano contra el código con el relevamiento de research.md: ninguna violación. Las 13 del frontend se corrieron con un prototipo: solo F1 en `TopBar` | Cumple |
| Mensajes (FR-004) | `[id] nombre, porque principio` más "Infractores (N):" y las líneas de detalle de ArchUnit (D14); en el frontend, `[id] archivo usa X: porqué (Constitución, sección Frontend)` | Cumple |
| Cada regla con caso borde (FR-007) | Backend: 35 clases de fixture (24 que rompen reglas y 11 de apoyo), al menos una por regla. Frontend: al menos un caso en memoria por regla | Cumple |
| Sonar | Las reglas que rompen las fixtures tienen alcance Main; las de alcance All se evitan por diseño | Cumple |
| Ningún test existente cambia | `AppNavigation.test.tsx` cubre el logo y sigue igual; `setup.ts` no se toca | Cumple |

Sin violaciones sin justificar. Las dos carpetas nuevas del árbol de tests y la dependencia nueva están en
"Seguimiento de complejidad".

## Estructura del proyecto

### Documentación de la feature

```text
specs/010-tests-arquitectura/
├── spec.md                         # Especificación (con clarificación)
├── plan.md                         # Este archivo
├── research.md                     # Fase 0: decisiones y verificaciones
├── data-model.md                   # Fase 1: reglas, violaciones y fixtures
├── quickstart.md                   # Fase 1: cómo correrlas y leer un fallo
├── contracts/
│   └── architecture-rules.md       # Fase 1: catálogo de las 33 reglas y formato del mensaje
├── checklists/requirements.md
└── tasks.md                        # Fase 2 ($speckit-tasks; no lo crea este comando)
```

### Código: backend

Producción: **sin cambios**. `build.gradle`: una línea en `dependencies`.

```text
backend/
├── build.gradle                                   # + testImplementation 'com.tngtech.archunit:archunit:1.5.1'
└── src/test/java/ar/edu/unq/desapp/
    ├── futbolmarket/architecture/                 # NUEVO, transversal (D4)
    │   ├── ArchitectureRules.java                 # fábrica: un método por regla, parametrizado por base (D3)
    │   ├── ProductionClasses.java                 # import único de producción, sin tests (D2)
    │   ├── ViolationReport.java                   # mensaje de falla en español (D14)
    │   ├── LayerRulesTest.java                    # B1, B6 y la guarda del import
    │   ├── ModelRulesTest.java                    # B2
    │   ├── PersistenceRulesTest.java              # B3.1 a B3.6
    │   ├── ControllerRulesTest.java               # B4.1 a B4.3
    │   ├── AdapterRulesTest.java                  # B5.1 a B5.3
    │   ├── CodingRulesTest.java                   # B7.1 a B7.5
    │   └── ArchitectureRulesDetectViolationsTest.java   # casos borde: cada regla contra las fixtures
    └── archfixture/                               # NUEVO, fuera de la aplicación (D13)
        ├── controller/sample/                     # RepositoryAwareController, ModelLeakController,
        │   │                                      # SampleEndpoint, StrayExceptionHandler, SampleController
        │   └── dto/                               # SampleResponse (con un record anidado), SampleDto
        ├── service/sample/                        # SampleService y un servicio por violación
        ├── service/alpha/ y service/beta/         # AlphaService <-> BetaService (ciclo)
        ├── modelo/sample/                         # SampleItem, TableMappedItem
        ├── persistence/
        │   ├── repository/sample/                 # SampleRepository, LockingRepository
        │   ├── mapper/sample/                     # SampleMapper
        │   └── sql/                               # NativeQueryRunner
        │       ├── entity/sample/                 # SampleSQL, SampleRecord
        │       └── interfaces/sample/             # SampleSQLDAO, SampleDao, NativeQuerySQLDAO
        ├── adapter/sample/                        # SampleAdapter, ServiceAwareAdapter
        │   └── dto/                               # SampleProviderDto
        └── shared/                                # SampleExceptionHandler
```

La lista completa de fixtures, con la regla que rompe cada una, está en
[data-model.md](./data-model.md#3-fixtures-del-backend).

### Código: frontend

```text
frontend/
├── src/
│   ├── components/
│   │   ├── TopBar.tsx              # sin react-router; prop homeLink: ReactNode (F-D9)
│   │   └── TopBar.module.css       # salen .logo, .logo svg, .ball, .patch y .seams
│   └── pages/
│       ├── AppLayout.tsx           # arma homeLink con Link to="/pizarra", el aria-label y el SVG
│       └── AppLayout.module.css    # entran las cinco reglas, sin cambios
└── tests/
    └── architecture.test.ts        # NUEVO, en la raíz de tests/ junto a setup.ts (F-D2)
```

### Documentación del repositorio

- `README.md` (raíz): sección nueva "Tests de arquitectura", después de "Tests", con dos tablas (regla -> qué
  verifica -> principio o sección de la constitución) y los comandos para correr cada suite sola. El contenido sale de
  [contracts/architecture-rules.md](./contracts/architecture-rules.md).

### Tests existentes que cambian

**Ninguno.** `tests/pages/AppNavigation.test.tsx` cubre el logo (nombre accesible y `href`) y tiene que pasar sin
cambios después de mover el link. Si algún test fallara, se arregla el código, no el test (Principio IV).

**Structure Decision**: monorepo web existente (`backend/` y `frontend/`). En el backend, tests en el paquete
transversal nuevo `futbolmarket/architecture/` y fixtures en `ar/edu/unq/desapp/archfixture/`, fuera del escaneo de
Spring. En el frontend, un archivo en la raíz de `tests/` y el cambio de `TopBar`/`AppLayout`.

## Orden de implementación sugerido

1. **Backend, casos felices** (US1): la dependencia; `ProductionClasses`, `ArchitectureRules` y `ViolationReport`; los
   seis `*RulesTest`. Correr `./gradlew test --tests "ar.edu.unq.desapp.futbolmarket.architecture.*"`: tienen que
   pasar las 20 reglas. Si una falla por algo que la spec no prevé, se frena y se consulta (FR-008).
2. **Backend, casos borde**: las fixtures de `archfixture/` y `ArchitectureRulesDetectViolationsTest`. Antes de seguir,
   `./gradlew build` completo: confirma que los `@SpringBootTest` no ven las fixtures.
3. **Frontend, análisis** (US2): `tests/architecture.test.ts` con `analyze`, la tabla, los casos borde y la guarda.
   Con el código actual, F1 tiene que fallar nombrando `src/components/TopBar.tsx` y `react-router` (escenario 2 de la
   US2).
4. **Frontend, TopBar**: mover el link del logo a `AppLayout`. `npm test` (con `AppNavigation.test.tsx` sin cambios),
   `npm run lint` y `npm run build`; revisión manual del logo con `npm run dev` (quickstart).
5. **Documentación** (US3): sección del README desde el catálogo de contracts.
6. **Cierre**: la definición de terminado de [quickstart.md](./quickstart.md#6-definición-de-terminado).

Los pasos 1-2 y 3-4 son independientes entre sí; el 5 va al final, sobre las reglas ya implementadas.

## Seguimiento de complejidad

| Agregado | Por qué hace falta | Alternativa más simple descartada |
|---|---|---|
| Dependencia `archunit` 1.5.1 (test) | La pide la cátedra; analiza bytecode con capas, ciclos y reglas generales listas | Escribir el análisis de bytecode a mano: más código y menos confiable |
| Paquete de test `futbolmarket/architecture/` | Las reglas de arquitectura no son de una capa ni de un contexto | Repartirlas por capa: duplica la fábrica y el import |
| Paquete de test `ar/edu/unq/desapp/archfixture/` | Las fixtures necesitan anotaciones de Spring y JPA; dentro de `futbolmarket` las escanearían los `@SpringBootTest` | Excluirlas del escaneo cambia producción; un source set aparte cambia el `build.gradle` |

## Puntos a confirmar con el equipo

1. ~~**G2**: el aval para `ar/edu/unq/desapp/archfixture/` (D13).~~ **Resuelto el 2026-11-12**: aval dado.
2. ~~**B7.1 a B7.4 con fixtures** (punto 3 de la tabla de ajustes): sigue la spec; si el equipo prefiere no tenerlas,
   hay que cambiar la spec (FR-007 y SC-002), no solo el plan.~~ **Resuelto el 2026-11-14**: con fixtures y caso borde.
3. ~~**Forma del slot del logo** (punto 12): `homeLink: ReactNode` como pidió el equipo, moviendo el SVG y la CSS del
   logo a `AppLayout`, o una render prop que deja todo en `TopBar` y no mueve estilos.~~ **Resuelto el 2026-11-14**:
   slot `homeLink: ReactNode`.
