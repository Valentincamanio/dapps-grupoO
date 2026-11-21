---

description: "Lista de tareas de la feature 012: métricas y endpoints de monitoreo"
---

# Tareas: Métricas y endpoints de monitoreo

**Entrada**: documentos de diseño de `/specs/012-metricas-monitoreo/`

**Prerrequisitos**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md),
[data-model.md](./data-model.md), [contracts/actuator-access.md](./contracts/actuator-access.md),
[contracts/health-indicators.md](./contracts/health-indicators.md), [quickstart.md](./quickstart.md)

**Tests**: SÍ. La spec los pide (FR-053, SC-004, SC-006) y la constitución (Principio IV) también. Las clases
de test y sus casos son los de research D12. **Sin TDD**: cada test se escribe junto con (después de) la
implementación de su tarea, y al cerrar la tarea el test existe y pasa. Convenciones: AssertJ, setup / execute /
verify en tres bloques, nombres descriptivos en español (`unaCompraRechazadaSumaUnRejected`), H2 en memoria con
`@ActiveProfiles("test")`, sin Testcontainers ni Docker.

**Organización**: por historia de usuario (US1 a US4, en el orden de prioridad de la spec). US1 y US2 son las
dos P1 y forman juntas el MVP: se publican por el mismo canal (`/actuator/prometheus`).

## Formato: `[ID] [P?] [Historia] Descripción`

- **[P]**: se puede hacer en paralelo (archivos distintos, sin dependencias pendientes)
- **[US#]**: historia de usuario a la que pertenece la tarea
- Cada descripción lleva la ruta exacta del archivo

## Convenciones de rutas

- Backend, producción: `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/` (abreviado `<main>/` en las
  dependencias y ejemplos; en las tareas va la ruta completa)
- Backend, tests: `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/` (abreviado `<test>/`)
- Recursos: `backend/src/main/resources/`
- Fuera de `backend/`: `monitoring/prometheus.yml`, `.gitignore`, `README.md`
- Carpeta nueva: `<main>/observability/` (transversal, **con aval**, plan "Cambio del árbol de paquetes").
  Los gauges van en `<main>/service/market/` y `<main>/service/user/`; los indicadores de salud en
  `<main>/service/sync/`; la configuración de Actuator en `<main>/config/` y `application.yaml`.

## Reglas que valen para todas las tareas

- **No se modifica ni se borra ningún test existente** (FR-050, SC-007). Los apoyos de test existentes
  (`<test>/e2e/AuthTestHelper`, `<test>/service/market/MarketDatabase`, `<test>/service/quote/QuoteDatabase`) se
  **usan** tal cual, sin agregarles métodos. Agregar métodos a los archivos de test que crea esta feature está
  permitido. `AccessControlIT` y `WebServiceAuditIT` tienen que seguir pasando sin cambios.
- **Una sola dependencia nueva**: `io.micrometer:micrometer-registry-prometheus` (`runtimeOnly`, sin versión,
  research D1). `build.gradle` y `application.yaml` se **modifican** (se agregan líneas), no se reemplazan;
  `application-local.yml` y `backend/src/test/resources/application-test.yml` **no se tocan**.
- **Los servicios instrumentados no cambian**: `MarketOrderService`, `AuthService`, `QuoteRecalculationService`,
  `SyncService`, `StatsSyncService`, `SyncCoordinator`. Tampoco `controller/`, `adapter/`,
  `CredentialAuthenticationFilter`, `WebServiceAuditAspect` ni la auditoría del mercado de la 008 (FR-038).
  `SyncService.runFull` y `StatsSyncService.runFull` siguen package-private.
- **Ninguna métrica cambia un resultado** (FR-018): el aspecto devuelve el valor de `proceed()` y relanza la
  **misma instancia** de la excepción; clasificar y registrar van dentro de un guard que captura
  `RuntimeException` y la loguea en `WARN`.
- **Etiquetas cerradas** (FR-019): todo valor de etiqueta sale de un enum; ningún argumento del join point
  (ids, cantidades, liga, username) llega a una etiqueta.
- **Ningún secreto** en respuestas, detalles de salud ni archivos versionados (FR-051): ni la clave de API, ni
  tokens, ni el token de Football-Data, ni la URL de la base con credenciales.
- **Sin** Docker, Testcontainers, Grafana, alertas, tracing ni probes (FR-052).
- Tests que pidan `GET /actuator/prometheus` llevan `@AutoConfigureMetrics`
  (`org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics`): sin ella Boot 4 apaga
  la exportación en `@SpringBootTest` y el endpoint da 404 (research D12). Los demás tests de métricas leen el
  `MeterRegistry` inyectado y miden **diferencias antes/después**, nunca valores absolutos.
- Calidad (Principio V): `@RequiredArgsConstructor` + `private final`; nombres de métricas, etiquetas y
  descripciones como constantes; sin `catch (Exception)` ni `catch (Throwable)`; SLF4J con placeholders; sin
  números mágicos; métodos cortos; sin código duplicado entre los advices.
- Idioma (Principio VII): identificadores, nombres de métricas, etiquetas y nombres de grupos de Swagger en
  inglés; descripciones de las métricas, `displayName` de Swagger, mensajes, Javadoc y comentarios en español.

---

## Fase 1: Setup (línea de base, aval y dependencia)

**Propósito**: partir de una base verde, tener el aval de la carpeta nueva, confirmar los nombres del diseño y
dejar el build y el `.gitignore` listos.

- [X] T001 Correr la línea de base sin tocar nada: `cd backend && ./gradlew build`. Tiene que dar `BUILD SUCCESSFUL` (incluye los tests de arquitectura de la 010); si algo ya falla, anotarlo y avisar antes de seguir
- [X] T002 Obtener el **aval explícito** de al menos uno de los dos desarrolladores para la carpeta nueva `observability/` (Principio I; plan "Cambio del árbol de paquetes" y "Puntos a confirmar"), y de paso confirmar `blocked` para `RecalculationBlockedBySyncException` (research D6). Anotar quién y cuándo en la sección "Puntos a confirmar con el equipo" de `specs/012-metricas-monitoreo/plan.md`. Sin el aval, no se empieza la Fase 4 (US2): la alternativa es la opción B de research D5 y hay que replanificar esa fase
- [X] T003 Contrastar con el código los nombres que usa el diseño y anotar cualquier diferencia al final de `specs/012-metricas-monitoreo/research.md` antes de seguir: firmas `MarketOrderService.buy/sell/cancel`, `AuthService.login/register`, `QuoteRecalculationService.recalculate(RecalculationOrigin)`, `SyncService.synchronizeAll(SyncOrigin)`/`synchronizeLeague(League)`/`runFull(SyncOrigin)` (package-private) y los mismos tres de `StatsSyncService`; `SyncCoordinator.runWeeklyChain()` y cómo llama a `runFull` sobre los proxies; las excepciones `InvalidCredentialsException`, `RecalculationInProgressException`, `RecalculationBlockedBySyncException`, `SyncInProgressException`, `SyncDisabledException` y que las de dominio del mercado extiendan `ConflictException`/`NotFoundException`/`ForbiddenException` de `shared/` (y que `MarketInvariantException` no); `RecalculationReport.wasInterrupted()`; `OrderStatus.isOpen()` y que `MarketOrderSQL` guarde el estado como `String`; `AppUserSQLDAO` extiende `JpaRepository`; `LastFullSyncRepository.find(SyncSource)`; `FootballDataProperties.hasToken()`; `SyncProperties.catchUpAfter()`; el bean `Clock` de `config/ApplicationConfig`; `CatchUpPolicy.isUpToDate` privado; la constante `INVALID_PARAMETERS` y el `handleUnexpected` de `shared/GlobalExceptionHandler`; las constantes `BEARER_AUTH` y `API_KEY_AUTH` de `config/OpenApiConfig`; cómo `e2e/AdminAccountIT` da de alta un `ADMIN` con `@DynamicPropertySource` y base H2 propia
- [X] T004 [P] Modificar `backend/build.gradle` (research D1): agregar `runtimeOnly 'io.micrometer:micrometer-registry-prometheus'` (sin versión) en una línea propia del bloque `dependencies`, junto a los demás `runtimeOnly`; y al final del archivo agregar `springBoot { buildInfo { properties { additional = [description: 'Mercado simulado de valoración de jugadores de fútbol.'] } } }`. No reemplazar ni reordenar el resto del archivo. Verificar con `./gradlew bootBuildInfo` que se genera `build/resources/main/META-INF/build-info.properties` con `build.name=futbol-market`, `build.group`, `build.version` y `build.description`
- [X] T005 [P] Agregar al `.gitignore` de la raíz las entradas `monitoring/admin-api-key` y `monitoring/data/`, con un comentario en español que explique que la primera guarda la clave de API del `ADMIN` para el Prometheus local y la segunda el almacenamiento local de Prometheus (research D10, FR-007)

**Checkpoint**: base verde, aval registrado, nombres confirmados, dependencia y build-info en el build.

---

## Fase 2: Fundacional (exposición y control de acceso)

**Propósito**: exponer los cinco endpoints **y** protegerlos en el mismo paso. Exponer `metrics`, `prometheus` y
`loggers` sin las reglas de rol dejaría que cualquier `USER` lea métricas y cambie niveles de log, así que la
configuración y la seguridad van juntas y antes de cualquier historia.

**⚠️ CRÍTICO**: ninguna historia empieza hasta cerrar esta fase.

- [ ] T006 Modificar `backend/src/main/resources/application.yaml` (research D2, agregar claves sin tocar las existentes): `management.endpoints.web.exposure.include: health,info,metrics,prometheus,loggers` (reemplaza el `health,info` actual); `management.endpoint.health.show-components: when-authorized`, `show-details: when-authorized`, `roles: ADMIN`; `management.info.env.enabled: false` y `management.info.java.enabled: true`; `management.metrics.tags.application: ${spring.application.name}`; `management.metrics.distribution.percentiles-histogram."[http.server.requests]": true` (con corchetes); y `springdoc.show-actuator: true`. Un comentario en español por bloque que diga por qué (por ejemplo: `env` apagado a propósito para no publicar variables de entorno; histograma solo para HTTP)
- [ ] T007 Modificar `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/security/SecurityConfig.java` (research D4, contracts/actuator-access.md "Reglas de la cadena"): en `apiFilterChain`, después de la regla de `/audit/**` y antes de `.anyRequest().authenticated()`, agregar en este orden `pathPattern("/actuator")` → `hasRole(Role.ADMIN.name())`, `pathPattern("/actuator/metrics/**")` → `ADMIN`, `pathPattern("/actuator/prometheus")` → `ADMIN`, `pathPattern("/actuator/loggers/**")` → `ADMIN`, y `pathPattern("/actuator/health/**"), pathPattern("/actuator/info")` → `authenticated()`. **Ninguna** regla `/actuator/**` genérica; `publicEndpoints()` no cambia (sigue con `GET /actuator/health` exacto). Actualizar el Javadoc de la clase con una oración: los endpoints no expuestos caen en `anyRequest().authenticated()` (401 visitante, 404 autenticado)
- [ ] T008 Verificar la fase (depende de T004, T006 y T007): `cd backend && ./gradlew test` en verde sin tocar tests, en particular `AccessControlIT` (`/actuator/info` protegido, `/actuator/health` público, `/v3/api-docs` con los dos esquemas) y `WebServiceAuditIT`. `./gradlew bootRun` arranca y, con un `ADMIN` (quickstart §2–3), `GET /actuator/prometheus` responde 200 en texto de Prometheus, un `USER` recibe 403 y un visitante 401

**Checkpoint**: los cinco endpoints expuestos y protegidos por rol; la suite existente en verde.

---

## Fase 3: User Story 1 - Prometheus recolecta las métricas del backend (Priority: P1) 🎯 MVP (junto con US2)

**Goal**: `/actuator/prometheus` publica todo en formato Prometheus con `application="futbol-market"`, histograma
HTTP y rutas como plantilla, y un Prometheus local con el ejemplo del repo lo recolecta con la clave de un
`ADMIN` que no está versionada.

**Independent Test**: `GET /actuator/prometheus` como `ADMIN` responde 200 en formato de exposición, toda serie
lleva `application="futbol-market"`, hay buckets de `http_server_requests_seconds` y dos requests a
`/players/7` y `/players/12` suman en `uri="/players/{id}"` sin series con el id concreto.

### Implementation for User Story 1

- [ ] T009 [US1] Crear `monitoring/prometheus.yml` (research D10, FR-006, FR-007): `global.scrape_interval: 15s`; un `scrape_configs` con `job_name: futbol-market`, `metrics_path: /actuator/prometheus`, `static_configs` con `targets: ["localhost:8080"]` y `http_headers` → `X-API-Key` → `files: ["admin-api-key"]`. Comentarios en español al principio: requiere **Prometheus 2.55.0 o posterior** (`http_headers`), la clave va en `monitoring/admin-api-key` (ignorado por git, una sola línea), la ruta es relativa al directorio del archivo, y cómo correrlo (`prometheus --config.file=monitoring/prometheus.yml --storage.tsdb.path=monitoring/data`). Ninguna clave, token ni secreto escrito

### Tests for User Story 1

- [ ] T010 [P] [US1] Crear `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/e2e/ActuatorContentIT.java` (`@SpringBootTest`, `@AutoConfigureMockMvc`, `@AutoConfigureMetrics`, `@ActiveProfiles("test")`, `@ExtendWith(OutputCaptureExtension.class)`, `MockMvcTester`; un `ADMIN` dado de alta por `@DynamicPropertySource` con base H2 propia, como `AdminAccountIT`; `AuthTestHelper` para el `USER`). Un auxiliar privado que pide `/actuator/prometheus` como `ADMIN` y devuelve las líneas de muestra (las que no empiezan con `#`). Casos: responde 200 con content type de texto de Prometheus (`text/plain` con `version=`) (US1.1, FR-001); **toda** línea de muestra contiene `application="futbol-market"` (SC-002, FR-002); después de `GET /players/7` y `GET /players/12` hay series `http_server_requests_seconds_bucket` con `uri="/players/{id}"` y `le=` y ninguna línea contiene `uri="/players/7"` ni `uri="/players/12"` (US1.2, US1.3, FR-003, FR-004); 50 ids distintos dejan la misma cantidad de series con `uri="/players/{id}"` que uno solo (SC-003); `GET /players/7/foo/bar` cuenta con un `uri` fijo (`NOT_FOUND` o el que Spring use) y no con la ruta concreta (edge case); figuran métricas técnicas (`jvm_memory_used_bytes`, `jvm_threads_live_threads`, `process_cpu_usage`, `process_uptime_seconds`, `hikaricp_connections_active`) (FR-005); ningún request a `/actuator/**` de este test deja línea con ` | user=` en la salida capturada (FR-038, US3.9) (depende de T008)
- [ ] T011 [US1] Verificación manual de quickstart.md §2 a §5 con Prometheus ≥ 2.55.0: clave del `ADMIN` en `monitoring/admin-api-key`, `git check-ignore -v monitoring/admin-api-key` la muestra ignorada, el target `futbol-market` figura UP en `http://localhost:9090/targets` dentro del primer intervalo y `rate(http_server_requests_seconds_count[1m])` devuelve series con `uri="/players/{id}"` (US1.4, SC-001); `git grep` de la clave no encuentra nada (US1.5, SC-009). Si `files` no se resuelve relativo al archivo de configuración, anotarlo en quickstart.md §4 y en el comentario de `monitoring/prometheus.yml`

**Checkpoint**: la exportación funciona y se recolecta. Todavía faltan las métricas propias (US2) para el MVP.

---

## Fase 4: User Story 2 - Métricas propias del negocio (Priority: P1) 🎯 MVP

**Goal**: las siete métricas `futbolmarket.*` (cinco contadores/timers con sus 30 series en cero desde el
arranque y dos gauges que leen la base), registradas por un aspecto que nunca cambia el resultado de un caso de
uso.

**Independent Test**: se ejercita cada caso de uso (compra aceptada y rechazada, login correcto y fallido,
registro, recálculo manual, sincronización) y cada métrica sube exactamente en uno con las etiquetas esperadas,
mientras la respuesta HTTP de cada caso es idéntica a la de antes.

**Requiere el aval de T002.**

### Modelo y persistencia

- [ ] T012 [P] [US2] Modificar `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/modelo/sync/SyncReport.java` (research D6, data-model §3): agregar `public boolean succeeded()` → `failedLeagues().isEmpty()`, con Javadoc en español ("la corrida terminó sin ligas fallidas"); `lastFullSync()` pasa a usar `type == SyncType.FULL && succeeded()` sin cambiar su resultado
- [ ] T013 [P] [US2] Modificar `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/modelo/sync/StatsSyncReport.java`: agregar `public boolean succeeded()` → `cut == null`, con Javadoc en español ("la corrida terminó sin corte"); `lastFullSync()` pasa a usar `type == SyncType.FULL && succeeded()` sin cambiar su resultado
- [ ] T014 [US2] Crear `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/modelo/sync/SyncReportOutcomeTest.java` (unitario del dominio, sin Spring): `SyncReport` sin ligas fallidas → `succeeded()` verdadero, con una fallida → falso; `StatsSyncReport` sin corte → verdadero, con corte → falso; y `lastFullSync()` de los dos sigue devolviendo lo mismo que antes para `FULL` exitosa, `FULL` fallida y `SINGLE_LEAGUE` (depende de T012 y T013)
- [ ] T015 [P] [US2] Agregar `long countByStatusIn(Collection<String> statuses)` (derived query, sin `@Query`) en `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/persistence/sql/interfaces/market/MarketOrderSQLDAO.java` y `public long countOpen()` en `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/persistence/repository/market/MarketOrderRepository.java`, que delega pasando los `name()` de los `OrderStatus` con `isOpen()` (calculados desde el enum, sin repetir `PENDING`/`PARTIAL` a mano) (research D7)
- [ ] T016 [P] [US2] Agregar `public long count()` en `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/persistence/repository/user/AppUserRepository.java`, que delega en el `count()` heredado de `JpaRepository` del `AppUserSQLDAO` (research D7)
- [ ] T017 [P] [US2] Crear `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/persistence/repository/market/OpenOrdersCountIT.java` (integración con H2, mismo estilo que los IT de repositorio existentes, base propia si hace falta): con órdenes `PENDING`, `PARTIAL`, `EXECUTED` y `CANCELLED` guardadas, `countOpen()` cuenta solo las dos primeras; sin órdenes, cero (depende de T015)
- [ ] T018 [P] [US2] Crear `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/persistence/repository/user/UserCountIT.java`: `count()` sube en uno al guardar un usuario, medido como diferencia antes/después (depende de T016)

### Catálogo y aspecto (`observability/`)

- [ ] T019 [P] [US2] Crear los enums de etiquetas en `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/observability/`: `OrderOperation.java` (`BUY`, `SELL`, `CANCEL`), `OrderResult.java` (`SUCCESS`, `REJECTED`), `LoginResult.java` (`SUCCESS`, `FAILURE`), `RecalculationTrigger.java` (`MANUAL`, `SCHEDULED`, `STARTUP`, con `static from(RecalculationOrigin)` en un `switch` exhaustivo: `MANUAL`→`MANUAL`, `WEEKLY`→`SCHEDULED`, `STARTUP`→`STARTUP`, `STRATEGY_CHANGE`→`MANUAL`), `RecalculationResult.java` (`SUCCESS`, `FAILURE`, `BLOCKED`) y `SyncResult.java` (`SUCCESS`, `FAILURE`, `IN_PROGRESS`). Cada uno con un método `tag()` que devuelve el nombre en minúsculas (`in_progress`, `rejected`, ...). Para `source` y `type` se **reutilizan** `SyncSource` y `SyncType` del modelo (en minúsculas), sin duplicarlos. Javadoc en español
- [ ] T020 [US2] Crear `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/observability/BusinessMetrics.java` (`@Component`, implementa `MeterBinder`; research D5, data-model §2): constantes con los nombres `futbolmarket.market.orders`, `futbolmarket.quotes.recalculation`, `futbolmarket.sync`, `futbolmarket.auth.logins`, `futbolmarket.auth.registrations`, las claves de etiqueta (`operation`, `result`, `trigger`, `source`, `type`) y las descripciones en español de data-model §2. `bindTo(MeterRegistry)` guarda el registro y registra **todas** las combinaciones en cero (6 + 9 + 12 + 2 + 1 = 30 series; timers sin histograma ni percentiles) (FR-016, FR-017). Métodos de registro, uno por caso: `recordOrder(OrderOperation, OrderResult)`, `recordLogin(LoginResult)`, `recordRegistration()`, `recordRecalculation(RecalculationTrigger, RecalculationResult, long nanos)`, `recordSync(SyncSource, SyncType, SyncResult, long nanos)`. Solo depende de Micrometer, `modelo/` y los enums de T019 (depende de T019)
- [ ] T021 [US2] Crear `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/observability/BusinessMetricsTest.java` (unitario, `new BusinessMetrics().bindTo(new SimpleMeterRegistry())`): las 30 combinaciones existen en cero; cada métrica tiene descripción no vacía en español; ninguna etiqueta tiene un valor fuera del vocabulario de data-model §1; cada `record*` sube exactamente la serie esperada y ninguna otra; los timers registran la duración pasada (depende de T020)
- [ ] T022 [US2] Crear `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/observability/BusinessMetricsAspect.java` (`@Aspect @Component @Order(Ordered.HIGHEST_PRECEDENCE) @RequiredArgsConstructor`, `private final BusinessMetrics metrics`; research D5 y D6, tabla de D6 completa). Pointcuts como constantes `String` (sin importar clases de `service/`): `execution(* ...service.market.MarketOrderService.buy(..))`, `sell`, `cancel`; `AuthService.login`, `register`; `QuoteRecalculationService.recalculate(..)`; `SyncService.synchronizeAll(..)`, `synchronizeLeague(..)` y `runFull(..)` **sin modificador `public`** (package-private, interceptado por el proxy CGLIB); los mismos tres de `StatsSyncService`. Cada `@Around ... throws Throwable`: `start = System.nanoTime()`, `proceed()`, clasificar y registrar, devolver el mismo valor o relanzar **la misma instancia**. Clasificación: órdenes → `SUCCESS` al volver, `REJECTED` si la excepción es `ConflictException`, `NotFoundException` o `ForbiddenException` de `shared/`, nada con otra; login → `SUCCESS` / `FAILURE` con `InvalidCredentialsException`; registro → solo al volver; recálculo → `trigger` de `RecalculationTrigger.from(origin)` (primer argumento), `FAILURE` si `report.wasInterrupted()`, si no `SUCCESS`; `BLOCKED` con `RecalculationInProgressException` o `RecalculationBlockedBySyncException`; `FAILURE` con otra `RuntimeException`; sync → `source` fijo por servicio, `type` `FULL` (`synchronizeAll`, `runFull`) o `SINGLE_LEAGUE`, `SUCCESS`/`FAILURE` según `report.succeeded()`, `IN_PROGRESS` con `SyncInProgressException`, **nada** con `SyncDisabledException`, `FAILURE` con otra `RuntimeException`. Los cuatro advices de sync comparten un método privado (sin duplicación). Todo el registro dentro de un único `recordSafely(Runnable)` que captura `RuntimeException` y loguea `WARN` con placeholders; un `Error` no se captura. Javadoc en español: qué mide, por qué afuera de la transacción (`HIGHEST_PRECEDENCE`), y que la auto-invocación `synchronizeAll` → `runFull` no se cuenta dos veces (depende de T012, T013 y T020)
- [ ] T023 [US2] Crear `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/observability/BusinessMetricsAspectTest.java` (unitario, `ProceedingJoinPoint` mockeado con Mockito, `BusinessMetrics` real sobre `SimpleMeterRegistry`): devuelve el mismo objeto que `proceed()`; relanza la misma instancia (`isSameAs`) de cada excepción; cada fila de la tabla de research D6 sube la serie esperada (incluye `MarketInvariantException` y `SyncDisabledException` que no registran nada, y `STRATEGY_CHANGE` → `manual`); con un `BusinessMetrics` mockeado cuyo `record*` lanza `RuntimeException`, el advice igual devuelve el resultado o relanza la excepción original (FR-018) (depende de T022)
- [ ] T024 [P] [US2] Crear `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/service/market/OpenOrdersGauge.java` (`@Component @RequiredArgsConstructor`, implementa `MeterBinder`): registra `futbolmarket.market.orders.open` con `Gauge.builder(name, marketOrderRepository, MarketOrderRepository::countOpen).description("<descripción en español de data-model §2>").strongReference(true)`; nombre y descripción como constantes (research D7, FR-015) (depende de T015)
- [ ] T025 [P] [US2] Crear `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/service/user/RegisteredUsersGauge.java` con el mismo patrón para `futbolmarket.users.registered` y `AppUserRepository::count` (depende de T016)

### Tests de integración de US2

- [ ] T026 [US2] Crear `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/e2e/BusinessMetricsIT.java` (`@SpringBootTest`, `@AutoConfigureMockMvc`, `@ActiveProfiles("test")`, `MockMvcTester`, `MeterRegistry` inyectado, `AuthTestHelper` y `service/market/MarketDatabase` como `OrderControllerIT`; un auxiliar que lee el conteo de una serie y cada caso mide la diferencia antes/después). Casos (research D12): compra, venta y cancelación aceptadas → `success` +1 (US2.1); compra sin saldo (409), jugador inexistente (404), orden inexistente (404) y orden ajena (403) → `rejected` +1 y status y cuerpo idénticos a los que ya verifican los IT existentes (US2.2, SC-005); un `ADMIN` que intenta comprar (403 del filtro) no suma nada; cancelar una orden abierta baja `futbolmarket.market.orders.open` en uno (US2.3); login correcto, contraseña incorrecta y usuario inexistente → `success` / `failure` +1 (US2.4); login con cuerpo inválido (400) no suma; registro exitoso → `registrations` +1 y `users.registered` +1, registro duplicado → nada (US2.5); ninguna serie propia del registro tiene etiquetas fuera del vocabulario (US2.9) (depende de T022, T024 y T025)
- [ ] T027 [P] [US2] Crear `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/service/quote/RecalculationMetricsIT.java` (integración, `QuoteDatabase` para armar semanas): `recalculate(MANUAL)` que termina bien → `trigger=manual,result=success` +1 con duración > 0 (US2.6); con el `RecalculationLock` tomado en el mismo hilo → `blocked` +1; con el `SyncLock` tomado → `blocked` +1; arranque con `on-startup=true` y sin semanas pendientes (armando `QuoteStartupRecalculation` a mano, sin `@Async`) → nada (depende de T022)
- [ ] T028 [P] [US2] Crear `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/service/sync/SyncMetricsIT.java` (integración con `@MockitoBean` del adapter de Football-Data y token por propiedad, como los IT de sincronización existentes): `synchronizeAll` y `synchronizeLeague` de las dos fuentes con `success` y `failure` (liga fallida / corte); `in_progress` con el `SyncLock` tomado (US2.7); `SyncDisabledException` (sin token) no registra; `SyncCoordinator.runWeeklyChain()` sin token registra **una sola** muestra `whoscored/full` y ninguna de Football-Data; una `synchronizeAll` manual registra **una sola** muestra (no cuenta también `runFull`) (depende de T022)
- [ ] T029 [US2] Agregar a `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/e2e/ActuatorContentIT.java` un caso: recién levantado, `/actuator/prometheus` contiene las 30 series de `futbolmarket_market_orders_total`, `futbolmarket_quotes_recalculation_seconds_count`, `futbolmarket_sync_seconds_count`, `futbolmarket_auth_logins_total` y `futbolmarket_auth_registrations_total` y los dos gauges `futbolmarket_users_registered` y `futbolmarket_market_orders_open` (US2.8, SC-004); y otro: `GET /actuator/metrics/futbolmarket.market.orders` como `ADMIN` trae la descripción en español y `availableTags` con `operation` y `result` (FR-016) (depende de T010 y T026)
- [ ] T030 [US2] Correr `cd backend && ./gradlew test` completo: todo en verde, incluidos los tests de arquitectura de la 010 (`observability/` no depende de `service/`, `persistence/`, `controller/`, `adapter/` ni `security/`; los gauges están en `service/`), y `git status` muestra solo archivos nuevos en `backend/src/test/` (SC-007). Si un test existente se rompe por el proxy del aspecto, corregir la producción, nunca el test

**Checkpoint**: MVP completo (US1 + US2): Prometheus recolecta métricas técnicas y de negocio.

---

## Fase 5: User Story 3 - Endpoints de monitoreo con acceso controlado (Priority: P2)

**Goal**: salud con componentes para cualquier autenticado y detalles solo para `ADMIN`, `info` con build y
Java, `loggers` con 400 estándar para un nivel inválido, el monitoreo en su propio grupo de Swagger, y la matriz
completa de acceso verificada.

**Independent Test**: para cada endpoint expuesto, el mismo request como visitante, `USER` y `ADMIN` da el
status de contracts/actuator-access.md con el JSON de error estándar; los no expuestos dan 404 (401 al
visitante) sin configuración en el cuerpo.

### Implementation for User Story 3

- [ ] T031 [P] [US3] Crear `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/config/ActuatorHealthConfig.java` (`@Configuration`; research D3): un `@Bean HealthEndpointGroupsPostProcessor` que envuelve el grupo primario en un `HealthEndpointGroup` propio (clase privada anidada o record) que redefine **solo** `showComponents(SecurityContext)` → `securityContext.getPrincipal() != null`, y delega `isMember`, `showDetails`, `getStatusAggregator`, `getHttpCodeStatusMapper` y `getAdditionalPath` al original. Solo clases de Boot. Javadoc en español: por qué no alcanza con propiedades (`roles` es común a componentes y detalles) y por qué la raíz pública nunca muestra componentes (el filtro no corre ahí y no hay principal)
- [ ] T032 [US3] Crear `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/config/ActuatorHealthConfigTest.java` (unitario): el grupo envuelto muestra componentes solo con principal; `showDetails`, `isMember`, agregador, mapper y path adicional son los del grupo original (con un `HealthEndpointGroup` mockeado) (depende de T031)
- [ ] T033 [P] [US3] Modificar `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/shared/GlobalExceptionHandler.java` (research D11): agregar `@ExceptionHandler(ResponseStatusException.class)` que, si `getStatusCode()` es 400, responde el `ApiError` estándar con `HttpStatus.BAD_REQUEST` y el mensaje fijo `INVALID_PARAMETERS` (nunca el `reason` de Boot); con cualquier otro status relanza la excepción, igual que hoy. Javadoc en español: solo Actuator la lanza en Spring MVC (nivel de log inválido)
- [ ] T034 [US3] Crear `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/shared/GlobalExceptionHandlerResponseStatusTest.java` (unitario): una `ResponseStatusException` 400 con un `reason` interno → `ApiError` 400 con el mensaje de `INVALID_PARAMETERS` y sin el `reason`; una 404 o 500 se relanza (depende de T033)
- [ ] T035 [P] [US3] Modificar `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/config/OpenApiConfig.java` (research D9, FR-037): agregar dos `@Bean GroupedOpenApi`: `business` (`displayName("API de negocio")`, `pathsToMatch("/**")`, `pathsToExclude("/actuator/**")`) y `monitoring` (`displayName("Monitoreo")`, `pathsToMatch("/actuator/**")`, con un `OpenApiCustomizer` que agrega a cada operación, salvo `GET /actuator/health`, un `SecurityRequirement` con `BEARER_AUTH` y otro con `API_KEY_AUTH` como alternativas). Nombres de grupo como constantes. El bean `OpenAPI` existente no cambia

### Tests for User Story 3

- [ ] T036 [US3] Crear `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/e2e/ActuatorAccessIT.java` (`@SpringBootTest`, `@AutoConfigureMockMvc`, `@AutoConfigureMetrics`, `@ActiveProfiles("test")`, `MockMvcTester`; `ADMIN` por `@DynamicPropertySource` y base H2 propia como `AdminAccountIT`; `USER` con `AuthTestHelper`). La matriz completa de contracts/actuator-access.md, preferentemente con `@ParameterizedTest`: `GET /actuator/health` 200 `{"status":"UP"}` sin `components` para visitante, `USER` y `ADMIN` con credencial (US3.1, FR-031); `/actuator/health/db` 401 / 200 sin `details` / 200 con `details` (US3.2, FR-032); `/actuator/health/inexistente` 401 / 404 / 404; `/actuator/info` 401 / 200 / 200 (US3.3); `/actuator`, `/actuator/metrics`, `/actuator/metrics/http.server.requests`, `/actuator/prometheus`, `/actuator/loggers` 401 / 403 / 200, y la raíz como `ADMIN` lista solo los cinco expuestos (US3.4, FR-034, FR-036); `GET /actuator/metrics/http.server.requests` como `ADMIN` trae `measurements` y `availableTags` (US3.5); `/actuator/env`, `/beans`, `/configprops`, `/heapdump`, `/threaddump`, `/mappings`, `/shutdown` 401 / 404 / 404 y el cuerpo no contiene `spring.`, `datasource`, `password` ni `jwt` (US3.7, FR-030); los 401 y 403 tienen el cuerpo `ApiError` (`status`, `error`, `message`, `path`); cada caso protegido pasa con `Authorization: Bearer` y con `X-API-Key` (FR-039); `GET /v3/api-docs/monitoring` lista operaciones `/actuator/**` con requisitos de seguridad, `GET /v3/api-docs/business` no tiene ninguna `/actuator/**` y `GET /v3/api-docs` sigue en 200 con `bearerAuth` y `apiKeyAuth` (US3.8, FR-037) (depende de T031, T033 y T035)
- [ ] T037 [P] [US3] Crear `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/e2e/LoggersEndpointIT.java` (mismo setup de usuarios que T036, sin `@AutoConfigureMetrics`): `ADMIN` hace `POST /actuator/loggers/ar.edu.unq.desapp.futbolmarket` con `{"configuredLevel":"DEBUG"}` → 204 y `GET` del mismo logger muestra `configuredLevel` `DEBUG` (US3.6, FR-035); `{"configuredLevel":"NOPE"}` → 400 con `ApiError` y el nivel no cambia; `USER` → 403 y el nivel no cambia; visitante → 401. Restaurar el nivel con `{"configuredLevel":null}` en un `@AfterEach` (depende de T033)
- [ ] T038 [US3] Agregar a `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/e2e/ActuatorContentIT.java` (con `@TestPropertySource` de un token de Football-Data ficticio si la clase todavía no lo tiene): `GET /actuator/info` como `USER` trae `build.name` `futbol-market`, `build.description`, `build.version`, `build.artifact`, `build.group`, `build.time` y `java.version`, y no trae `env` (FR-033); ninguna respuesta de `health`, `health/db`, `info`, `metrics`, `prometheus` ni `loggers`, para ningún rol, contiene el secreto JWT del perfil test, la clave de API usada, el token de sesión ni el token de Football-Data ficticio (FR-051, SC-009) (depende de T010 y T029)
- [ ] T039 [US3] Verificación manual de quickstart.md §6, §7 y §8: los `curl` de la matriz dan los status esperados, el cambio de nivel en caliente rige sin reiniciar, Swagger UI muestra el selector **API de negocio** / **Monitoreo** y `GET /actuator/metrics` se ejecuta desde ahí autorizado con `apiKeyAuth`, y `backend/logs/web-service-audit.log` no tiene ninguna línea con `/actuator`

**Checkpoint**: US1, US2 y US3 funcionan; la superficie de Actuator está cerrada y verificada por rol.

---

## Fase 6: User Story 4 - Salud de las integraciones y de las sincronizaciones (Priority: P3)

**Goal**: los componentes `footballData` y `sync` en `/actuator/health`, solo `UP` o `UNKNOWN`, sin llamadas
externas y sin secretos; el agregado sigue en `UP`.

**Independent Test**: como `ADMIN`, `GET /actuator/health/footballData` y `GET /actuator/health/sync` con y sin
token, sin sincronizaciones, con una reciente y con una vencida dan el estado y los detalles de
contracts/health-indicators.md, y `GET /actuator/health` sigue en 200.

### Implementation for User Story 4

- [ ] T040 [P] [US4] Crear `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/modelo/sync/SyncFreshness.java` (`enum`: `NEVER`, `UP_TO_DATE`, `STALE`, Javadoc en español) y agregar en `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/modelo/sync/CatchUpPolicy.java` `public SyncFreshness freshness(Optional<LastFullSync> lastFullSync, Instant now)`: vacío → `NEVER`; si no, `isUpToDate(...)` (el mismo método privado que usa `decide`) → `UP_TO_DATE`, si no `STALE`. `decide` no cambia (research D8)
- [ ] T041 [US4] Crear `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/modelo/sync/SyncFreshnessTest.java` (unitario del dominio): sin última → `NEVER`; terminó hace menos de `maxAge` → `UP_TO_DATE`; exactamente `maxAge` → `UP_TO_DATE`; `maxAge` + 1 ns → `STALE`; y para los mismos instantes `freshness` coincide con lo que `decide` considera al día (depende de T040)
- [ ] T042 [P] [US4] Crear `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/service/sync/FootballDataHealthIndicator.java` (`@Component @RequiredArgsConstructor`, implementa `HealthIndicator`, `private final FootballDataProperties properties`): sin token (vacío o en blanco, según `hasToken()`) → `Health.unknown().withDetail("tokenConfigured", false)`; con token → `Health.up().withDetail("tokenConfigured", true)`. Claves de detalle como constantes; nunca agrega el token ni una parte. No inyecta ningún adapter (FR-040, FR-043)
- [ ] T043 [US4] Crear `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/service/sync/FootballDataHealthIndicatorTest.java` (unitario): sin token, con token en blanco y con token → estado y `tokenConfigured` esperados; el `toString()` de los detalles nunca contiene el valor del token (depende de T042)
- [ ] T044 [US4] Crear `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/service/sync/SyncHealthIndicator.java` (`@Component @RequiredArgsConstructor`, implementa `HealthIndicator`; `LastFullSyncRepository`, `SyncProperties`, `Clock`): arma `new CatchUpPolicy(syncProperties.catchUpAfter())`, y por cada `SyncSource` (`FOOTBALL_DATA` → clave `football_data`, `WHOSCORED` → `whoscored`) calcula `freshness(find(source), clock.instant())` y traduce según contracts/health-indicators.md: `NEVER` → `{"synchronized": false}`; `UP_TO_DATE`/`STALE` → `synchronized: true`, `lastSuccessfulFullSync` (el `finishedAt` en ISO-8601) y `stale`. Agrega `catchUpAfter` como duración ISO-8601. Estado `UP` solo si las dos son `UP_TO_DATE`; si no, `UNKNOWN`. Nunca `DOWN`. Sin `if` de dominio propios: la regla del plazo es de `CatchUpPolicy` (depende de T040)
- [ ] T045 [US4] Crear `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/service/sync/SyncHealthIndicatorTest.java` (unitario con Mockito y `Clock.fixed`): las dos sin sincronizar → `UNKNOWN` con `synchronized=false`; las dos al día → `UP` con `stale=false` y la fecha; una vencida → `UNKNOWN` con `stale=true` en esa; borde exacto `catchUpAfter` → al día y +1 ns → vencida; `catchUpAfter` publicado como `PT168H` con 7 días; nunca `DOWN` en ningún caso (depende de T044)

### Tests for User Story 4

- [ ] T046 [US4] Crear `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/e2e/HealthIndicatorsIT.java` (`@SpringBootTest`, `@AutoConfigureMockMvc`, `@ActiveProfiles("test")`; `ADMIN` como en T036): perfil `test` sin token y sin sincronizaciones → `GET /actuator/health` 200 `{"status":"UP"}` (US4.6, SC-010, FR-042); `ADMIN` ve `footballData` `UNKNOWN` con `tokenConfigured=false` y `sync` `UNKNOWN` con las dos fuentes `synchronized=false` (US4.1, US4.3); una clase anidada `@Nested` o una segunda configuración con `@TestPropertySource` de un token ficticio → `footballData` `UP` con `tokenConfigured=true` y el cuerpo no contiene el token (US4.2); con un `LastFullSync` reciente guardado para las dos fuentes → `sync` `UP`; con uno vencido → `UNKNOWN` y `stale=true` (US4.4, US4.5); los dos componentes responden 200 también en `UNKNOWN` (depende de T042 y T044)

**Checkpoint**: las cuatro historias funcionan de forma independiente.

---

## Fase 7: Polish & Cross-Cutting Concerns

**Propósito**: documentación, Sonar, quickstart completo y definición de terminado.

- [ ] T047 [P] Agregar en `README.md` de la raíz una sección "Métricas y monitoreo" (FR-008): qué se publica (métricas técnicas y las siete `futbolmarket.*`, con `application="futbol-market"`), qué endpoints de Actuator hay y quién puede usarlos (tabla corta de contracts/actuator-access.md), cómo correr el Prometheus local (≥ 2.55.0, `monitoring/prometheus.yml`, la clave del `ADMIN` en `monitoring/admin-api-key` ignorado), las consultas de verificación más útiles y un enlace a `specs/012-metricas-monitoreo/quickstart.md`; en qué se diferencia de la auditoría del mercado y de la de web services. En español, sin claves ni referencias a máquinas personales
- [ ] T048 Revisar el análisis de Sonar del código nuevo (SonarCloud del PR, o `./gradlew sonar` si el equipo lo corre local): menos de 10 issues en total (SC-008). Corregir en el código cualquier issue nuevo; si aparece `java:S112` por el `throws Throwable` de `BusinessMetricsAspect`, agregar una exclusión en el bloque `sonar` de `backend/build.gradle` con el mismo patrón de las existentes y un comentario en español (research D13)
- [ ] T049 Correr quickstart.md completo de punta a punta (§0 a §9), incluida la rotación de la clave (§9: el target pasa a DOWN y vuelve a UP al actualizar `monitoring/admin-api-key`). Corregir quickstart.md si algún paso no coincide con lo que pasa
- [ ] T050 Definición de terminado: `cd backend && ./gradlew build` → `BUILD SUCCESSFUL` (con ArchUnit de la 010); `git diff --name-status main -- backend/src/test` muestra solo `A` (archivos nuevos) (SC-007); `git diff --name-status main -- backend/src/main` muestra solo los archivos listados en plan.md "Código fuente: backend" y nada en `controller/`, `adapter/` ni en los servicios instrumentados; `git diff main -- backend/src/main/resources` solo toca `application.yaml`; `bootRun` con perfil `local` arranca y los endpoints de monitoreo figuran en Swagger, en su grupo, y se ejecutan desde `/swagger-ui.html`
- [ ] T051 (No bloquea) Cerrar los "Puntos a confirmar con el equipo" de `specs/012-metricas-monitoreo/plan.md`: avisarle al compañero que los tests que pidan `/actuator/prometheus` necesitan `@AutoConfigureMetrics` y que el Prometheus local tiene que ser 2.55.0 o posterior; si el equipo decide no registrar `blocked` para `RecalculationBlockedBySyncException`, cambiar esa línea en `BusinessMetricsAspect` y su caso en `RecalculationMetricsIT` y `BusinessMetricsAspectTest`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Fase 1)**: sin dependencias. T004 y T005 en paralelo después de T001 y T003. T002 (aval) bloquea solo
  la Fase 4.
- **Fundacional (Fase 2)**: depende de T004. T006 → T007 → T008 (T006 y T007 son archivos distintos y se pueden
  escribir en paralelo, pero **no se commitea** la exposición sin las reglas). **Bloquea todas las historias.**
- **US1 (Fase 3)**: depende de la Fase 2.
- **US2 (Fase 4)**: depende de la Fase 2 y del aval (T002). T029 agrega casos al archivo de T010 (US1).
- **US3 (Fase 5)**: depende de la Fase 2. T038 agrega casos al archivo de T010/T029.
- **US4 (Fase 6)**: depende de la Fase 2. No depende de US3: con `roles: ADMIN` el `ADMIN` ve componentes y
  detalles aunque todavía no esté `ActuatorHealthConfig`.
- **Polish (Fase 7)**: depende de las historias que se quieran entregar. T051 no bloquea el PR.

### User Story Dependencies

- **US1 (P1)**: arranca después de la Fase 2. No depende de otras historias.
- **US2 (P1)**: arranca después de la Fase 2 y del aval. En comportamiento es independiente de US1; solo
  comparte el archivo `ActuatorContentIT` (T029). Juntas forman el MVP.
- **US3 (P2)**: arranca después de la Fase 2. Independiente en comportamiento; comparte `ActuatorContentIT`
  (T038).
- **US4 (P3)**: arranca después de la Fase 2. Totalmente independiente de las otras tres.

### Within Each User Story

- Producción primero, test de la misma pieza después (sin TDD); al cerrar la tarea el test existe y pasa.
- US2: modelo (T012, T013) y persistencia (T015, T016) → enums (T019) → `BusinessMetrics` (T020) → aspecto
  (T022) → tests de integración (T026–T028); gauges (T024, T025) solo necesitan su repository.
- US3: post-processor, handler y Swagger en paralelo → `ActuatorAccessIT` (T036).
- US4: `SyncFreshness`/`CatchUpPolicy` (T040) → `SyncHealthIndicator` (T044); `FootballDataHealthIndicator`
  (T042) en paralelo con T040.

### Parallel Opportunities

- Fase 1: T004 ∥ T005.
- US1: T009 ∥ T010.
- US2: T012 ∥ T013 ∥ T015 ∥ T016 ∥ T019; después T017 ∥ T018 ∥ T024 ∥ T025 ∥ T020; con el aspecto listo,
  T027 ∥ T028 (∥ T026).
- US3: T031 ∥ T033 ∥ T035; después T036 ∥ T037.
- US4: T040 ∥ T042; después T041 ∥ T043 ∥ T044.
- Entre historias: con la Fase 2 cerrada, US2, US3 y US4 tocan archivos de producción distintos y se pueden
  repartir entre personas; lo único compartido es `ActuatorContentIT` (T010 → T029 → T038, en ese orden).
- Polish: T047 ∥ T048.

---

## Parallel Example: User Story 2

```bash
# Con la Fase 2 cerrada y el aval de T002, en paralelo (archivos distintos):
Task: "T012 SyncReport.succeeded() en <main>/modelo/sync/SyncReport.java"
Task: "T013 StatsSyncReport.succeeded() en <main>/modelo/sync/StatsSyncReport.java"
Task: "T015 countOpen() en <main>/persistence/repository/market/MarketOrderRepository.java y MarketOrderSQLDAO.java"
Task: "T016 count() en <main>/persistence/repository/user/AppUserRepository.java"
Task: "T019 enums de etiquetas en <main>/observability/"

# Con el aspecto (T022) listo, en paralelo:
Task: "T027 RecalculationMetricsIT en <test>/service/quote/RecalculationMetricsIT.java"
Task: "T028 SyncMetricsIT en <test>/service/sync/SyncMetricsIT.java"
```

## Parallel Example: User Story 3

```bash
Task: "T031 ActuatorHealthConfig en <main>/config/ActuatorHealthConfig.java"
Task: "T033 handler de ResponseStatusException 400 en <main>/shared/GlobalExceptionHandler.java"
Task: "T035 grupos business y monitoring en <main>/config/OpenApiConfig.java"
```

---

## Implementation Strategy

### MVP First (User Stories 1 + 2)

1. Fase 1: Setup (línea de base, aval, dependencia, `.gitignore`).
2. Fase 2: Fundacional (`application.yaml` + reglas de `SecurityConfig`, juntas).
3. Fase 3: US1 → exportación verificada y Prometheus local recolectando.
4. Fase 4: US2 → métricas de negocio.
5. **STOP and VALIDATE**: `./gradlew test` y quickstart §4–§5 (las consultas `futbolmarket_*`).

### Incremental Delivery

1. Setup + Fundacional → endpoints expuestos y protegidos, suite en verde.
2. US1 + US2 → MVP (requerimiento central de la cátedra: Prometheus con métricas técnicas y de negocio).
3. US3 → salud por rol, 400 estándar en loggers, Swagger separado y la matriz de acceso completa.
4. US4 → indicadores `footballData` y `sync`.
5. Polish → README, Sonar, quickstart completo, definición de terminado.

### Parallel Team Strategy

Con dos desarrolladores, después de la Fase 2:

- Desarrollador A: US1 y US2 (exportación y `observability/`).
- Desarrollador B: US3 y después US4 (`config/`, `shared/`, `service/sync/`).
- `ActuatorContentIT` lo abre A (T010, T029) y B suma sus casos al final (T038).

---

## Notes

- [P] = archivos distintos, sin dependencias pendientes.
- [US#] mapea cada tarea a su historia para la trazabilidad.
- Cada checkpoint es un punto para validar la historia por separado.
- Los commits los hace el equipo.
- Evitar: tocar tests existentes o los servicios instrumentados, una regla `/actuator/**` genérica, etiquetas con
  valores no acotados, `catch` genéricos, y cualquier secreto en `monitoring/prometheus.yml`.
