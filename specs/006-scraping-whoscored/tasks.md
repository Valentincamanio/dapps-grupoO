---

description: "Lista de tareas de la feature 006-scraping-whoscored"
---

# Tareas: Scraping de WhoScored

**Entrada**: documentos de diseño en `/specs/006-scraping-whoscored/`

**Prerrequisitos**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md),
[data-model.md](./data-model.md), [contracts/](./contracts/), [quickstart.md](./quickstart.md) y
la constitución 2.2.1 (`.specify/memory/constitution.md`).

**Tests**: se incluyen, porque la constitución (principio IV, NO NEGOCIABLE) y la definición de
terminado los exigen. **No se trabaja con TDD**: cada tarea de código se cierra con sus tests
escritos y en verde, y cada bloque termina con `./gradlew build` en verde.

**Organización**: las tareas se agrupan por historia de usuario (HU1 a HU4 del spec = US1 a US4).

- Las cuatro historias comparten un único motor: el catálogo con los ids de WhoScored, el
  rendimiento, el modelo de la sincronización, el adapter, el snapshot y el servicio que orquesta.
  Va en la Fase 2, en el orden de los pasos 2 a 6 de la "Secuencia de implementación" del plan.
- La constitución prohíbe modificar un test ya escrito sin el "sí" del equipo. Por eso cada clase
  nace con su forma final (componentes de los records y dependencias de los constructores), y
  ninguna fase posterior obliga a tocar un test anterior. Esto obliga a un único ajuste al orden
  del plan: la lectura del snapshot (paso 6) se adelanta al bloque 2E, antes de `StatsSyncService`
  (paso 5). Si no, `StatsSyncServiceTest` nacería sin el snapshot y habría que cambiar su armado en
  el paso 6.
- Cada fase de historia agrega lo propio y los tests de punta a punta de sus escenarios:
  - HU1: el disparo manual (paso 8 del plan);
  - HU2: el comando de exportación del snapshot (resto del paso 6);
  - HU3: el candado común, la cadena y la puesta al día (paso 7), con los tres tests de la 005;
  - HU4: los bloqueos y las fallas de punta a punta.
- El paso 8 del plan (disparo manual) va antes que el 7 (coordinador) porque es la HU1 y el MVP.
  No hay dependencia técnica: `StatsSyncController` solo llama a `StatsSyncService`. Los 409 que
  necesitan el candado común se prueban en la HU3.

## Formato: `[ID] [P?] [Story] Descripción`

- **[P]**: se puede hacer en paralelo con las otras tareas [P] de su bloque (otro archivo). Si
  tiene una dependencia, se indica entre paréntesis.
- **[Story]**: historia a la que pertenece la tarea (US1 a US4).
- Cada descripción incluye la ruta exacta del archivo.

## Convenciones de rutas y reglas para quien implemente

- Código de producción: `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/`.
- Tests: `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/`.
- Recursos: `backend/src/main/resources/` y `backend/src/test/resources/`.
- Todos los comandos de Gradle se corren desde `backend/`.
- Identificadores en inglés; mensajes, comentarios y Javadoc en español (principio VII).
- Inyección por constructor (`@RequiredArgsConstructor` y campos `private final`), SLF4J para logs,
  constantes con nombre, sin `catch (Exception e)`, sin `// TODO` y sin `System.out` (principio V).
  Los servicios capturan solo `SourceBlockedException`, `DataAccessException`,
  `SyncInProgressException` y, al pedir y preparar un partido scrapeado, `CatalogInvariantException`
  (un valor imposible de la fuente). El adapter captura `RestClientException` (research D3) y
  `JacksonException` (D4); el repository del snapshot, `JacksonException` y
  `CatalogInvariantException` (D20). `InterruptedException` restaura la marca de interrupción.
- **Nada de `modelo/` importa `org.springframework`, `jakarta.persistence`, `tools.jackson`,
  `adapter` ni `controller`** (principio I). Los DTO de `adapter/whoscored/dto/` nunca salen del
  adapter. Ninguna excepción del cliente HTTP sale del adapter: `HttpPageDownloader` la vuelve una
  página fallida y el bloqueo sale como `SourceBlockedException`.
- **Persistencia**: solo derived queries, JPQL y `@EntityGraph`, sin SQL nativo (la única
  excepción aprobada es el `INSERT` de prueba de T028, research D17). `@Table` con
  nombre explícito, índices únicos con nombre y enums nuevos como `VARCHAR` sobre un `String`.
  Las columnas nuevas de tablas existentes son nulas o tienen `@ColumnDefault`: **la base local
  no se borra** y `./gradlew bootRun` se puede correr en cualquier momento (research D17).
- **Tests existentes**: solo se tocan los tres de research D26, con el "sí" explícito de Lucas del
  2026-10-13 y el del 2026-10-14 para los nombres:
  - `StartupSyncTest` se adapta, se renombran dos métodos y se borra
    `conJugadoresNoDisparaYDejaElInfo` (T128);
  - `SyncSchedulerTest` se adapta, se renombra un método, se borra
    `sinTokenNoLlamaAlServicioYDejaUnInfo` y el método del cron no cambia (T129);
  - en `SyncServiceTest` cambian solo el helper `service(token, clock)` y un `@Mock` (T126).

  Cualquier otro test queda igual: `SyncControllerIT`, `SyncDisabledIT`, `SyncRunTest`,
  `SyncReportLoggerTest`, `SyncWriteServiceIT`, `FootballDataPropertiesTest`,
  `FootballDataAdapterTest`, `TeamTest`, `PlayerTest`, `MatchTest`, `LeagueSyncTest`,
  `SnapshotFixtures`, los IT de repositories y `AccessControlIT`, entre otros. Los comportamientos
  nuevos de clases de la 005 se prueban en clases de test nuevas (`TeamWhoScoredTest`,
  `SyncServiceLastFullSyncTest`, etc.). A una clase de test creada en esta feature se le pueden
  agregar métodos `@Test` y campos en una fase posterior; modificar o borrar un método ya escrito,
  no. Un test que falla se arregla arreglando el código.
- **Forma de los tests** (principio IV):
  - AssertJ;
  - nombres de método que describen el comportamiento en español;
  - `*Test` para unitarios e `*IT` para integración y end to end;
  - `@ActiveProfiles("test")` en todo lo que levante Spring;
  - las tres partes setup, execute y verify separadas por una línea en blanco;
  - nunca `Thread.sleep`: se usan `RecordingWhoScoredSleeper`, un `Clock` fijo o latches con
    timeout.
- **Ningún test consulta WhoScored ni Football-Data** (research D26). El perfil test apunta a
  `http://whoscored.invalid` y a un snapshot inexistente; el adapter se prueba con
  `MockRestServiceServer` y el resto con `@MockitoBean WhoScoredAdapter`.
- **H2 de los IT nuevos**: `jdbc:h2:mem:stats-it;DB_CLOSE_DELAY=-1`, con
  `@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:stats-it;DB_CLOSE_DELAY=-1")`.
  Cada IT de `stats-it` limpia **antes y después de cada test** (`@BeforeEach` y `@AfterEach`), en
  este orden: `player_match_stats`, `matches`, `seasons`, `players`, `teams` y `last_full_syncs`
  (las tablas que existan en ese bloque). Así una clase nunca deja filas que bloqueen la limpieza
  de otra. Los end to end usan su propia H2, como `SyncControllerIT`.
- **Contexto nuevo** declarado en el plan: `performance` (modelo y persistencia). `sync` suma
  clases en persistencia. `adapter/whoscored/` es el segundo proveedor. `StatsSnapshotExport` va en
  el paquete raíz. No se crean otras carpetas, ni carpetas vacías ni `.gitkeep`.
  `backend/snapshot/` nace con el primer snapshot (T142).
- **WhoScored no permite el scraping en sus términos de uso**: solo se consulta el sitio real en
  las tareas de la Fase 7, con pocas páginas y el ritmo de la aplicación.
- **El token de Football-Data.org nunca se escribe en un archivo, un commit, un log ni el chat.**

---

## Fase 1: Preparación (infraestructura compartida)

**Propósito**: propiedades, configuración del cliente HTTP, `.gitattributes` y `.gitignore` (paso
1 del plan).

- [X] T001 [P] En backend/src/main/resources/application.yaml agregar los bloques `whoscored` y `sync` dentro del bloque `futbolmarket` existente, después de `football-data`, exactamente como figuran en contracts/configuration.md ("Cambios en los archivos existentes"). No reemplazar el archivo ni tocar las claves existentes
- [X] T002 [P] En backend/src/test/resources/application-test.yml agregar, dentro del bloque `futbolmarket` existente, `whoscored.base-url: http://whoscored.invalid` y `whoscored.snapshot.path: build/test-snapshot/absent.jsonl`, como indica contracts/configuration.md. No tocar las claves existentes
- [X] T003 [P] En .gitattributes (raíz del repositorio) agregar la línea `backend/snapshot/*.jsonl text eol=lf` (research D19). En .gitignore (raíz del repositorio), debajo de `backend/data/`, agregar `backend/data-*/`: así un respaldo de la base local, como el de quickstart.md 5.3 (`data-respaldo`), nunca se versiona (FR-032). No crear todavía la carpeta `backend/snapshot/`
- [X] T004 [P] Crear el record `WhoScoredProperties` en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/config/WhoScoredProperties.java, siguiendo el patrón de `FootballDataProperties` (research D25):
  - `@ConfigurationProperties("futbolmarket.whoscored")` y `@Validated`;
  - componentes: `URI baseUrl` (`@NotNull`), `String userAgent`, `String accept` y `String acceptLanguage` (`@NotBlank`), `Duration connectTimeout`, `Duration readTimeout`, `Duration requestInterval` y `Duration retryWait` (`@NotNull`), y el record anidado `Snapshot(@NotBlank String path)` (`@NotNull @Valid`);
  - constantes públicas `MIN_REQUEST_INTERVAL = Duration.ofSeconds(4)` (FR-042) y `MIN_RETRY_WAIT = Duration.ofSeconds(30)` (FR-043);
  - el constructor compacto lanza `IllegalArgumentException`, con un mensaje en español, ante un timeout cero o negativo, un `requestInterval` menor a `MIN_REQUEST_INTERVAL` o un `retryWait` menor a `MIN_RETRY_WAIT`. Así la aplicación no arranca con valores por debajo del spec
- [X] T005 [P] Crear el record `SyncProperties(Duration catchUpAfter)` en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/config/SyncProperties.java: `@ConfigurationProperties("futbolmarket.sync")`, `@Validated`, `@NotNull` y un constructor compacto que rechaza con `IllegalArgumentException` una duración cero o negativa (research D25)
- [X] T006 En backend/src/main/java/ar/edu/unq/desapp/futbolmarket/config/ApplicationConfig.java sumar `WhoScoredProperties.class` y `SyncProperties.class` a `@EnableConfigurationProperties` y actualizar el Javadoc (depende de T004 y T005)
- [X] T007 [P] Crear `WhoScoredClientConfig` (`@Configuration`) en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/config/WhoScoredClientConfig.java (research D3) (depende de T004):
  - el método estático público `RestClient.Builder applyDefaults(RestClient.Builder builder, WhoScoredProperties properties)`, que pone la `baseUrl` y los headers por defecto `User-Agent`, `Accept` y `Accept-Language` de las propiedades. No manda `Accept-Encoding` ni cookies. El test del adapter (T096) lo reutiliza;
  - el método estático público `HttpClient httpClient(WhoScoredProperties)`: `HttpClient.newBuilder().connectTimeout(connectTimeout).followRedirects(HttpClient.Redirect.NORMAL).build()`, con la versión por defecto (HTTP/2 con *fallback* a HTTP/1.1);
  - el bean `RestClient whoScoredRestClient(WhoScoredProperties)`: `applyDefaults(RestClient.builder(), properties)` con un `JdkClientHttpRequestFactory` sobre `httpClient(properties)` y `setReadTimeout(readTimeout)`;
  - la compresión queda apagada (no se llama a `enableCompression`) y no hay interceptores de logging;
  - `FootballDataClient` sigue recibiendo el suyo con `@Qualifier("footballDataRestClient")`: no cambia;
  - escribir `WhoScoredClientConfigTest` (unitario, sin red) en backend/src/test/java/ar/edu/unq/desapp/futbolmarket/config/WhoScoredClientConfigTest.java: con las propiedades de application.yaml, `httpClient` sigue las redirecciones (`followRedirects()` es `NORMAL`) y tiene 10 s de `connectTimeout()` (research D3)
- [X] T008 [P] Escribir `WhoScoredPropertiesTest` (unitario) en backend/src/test/java/ar/edu/unq/desapp/futbolmarket/config/WhoScoredPropertiesTest.java (depende de T001, T002 y T004):
  - acepta exactamente 4 s de pausa y 30 s de espera;
  - rechaza 3999 ms de pausa y 29 s de espera, con sus mensajes, y un timeout cero o negativo;
  - leyendo application.yaml con `YamlPropertySourceLoader`, como `FootballDataPropertiesTest`: la URL base, los tres headers, `10s`, `30s`, `4s`, `30s` y `snapshot/whoscored-stats.jsonl` (contracts/configuration.md);
  - leyendo application-test.yml: `http://whoscored.invalid` y `build/test-snapshot/absent.jsonl`;
  - con `ApplicationContextRunner`, `@EnableConfigurationProperties(WhoScoredProperties.class)` y los valores de application.yaml, un `request-interval` de `3s` o un `retry-wait` de `10s` hacen fallar el contexto (quickstart 1, "Configuración")
- [X] T009 [P] Escribir `SyncPropertiesTest` (unitario) en backend/src/test/java/ar/edu/unq/desapp/futbolmarket/config/SyncPropertiesTest.java (depende de T001 y T005): `catch-up-after` es `7d` en application.yaml, y una duración cero o negativa se rechaza con su mensaje
- [X] T010 Correr `./gradlew build` en backend/ y verificar `BUILD SUCCESSFUL`

---

## Fase 2: Fundacional (el motor de WhoScored)

**Propósito**: todo lo que comparten las cuatro historias: el catálogo con los ids de WhoScored,
el rendimiento, el modelo de la sincronización, el adapter, el snapshot y los servicios de
WhoScored (pasos 2 a 6 del plan). Cada bloque deja `./gradlew build` en verde.

**⚠️ CRÍTICO**: ninguna historia empieza antes de terminar esta fase.

### 2A. Catálogo con el id de WhoScored (pasos 2 y 3 del plan)

`Team`, `Player` y `Match` cambian junto con sus mappers, porque los mappers usan los
constructores canónicos.

- [X] T011 [P] Cambiar el record `Team` en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/modelo/team/Team.java a `(Long id, String externalId, String name, String crest, League league, String whoscoredId)` (data-model.md, "Team"):
  - `whoscoredId` se recorta y un valor en blanco queda en `null`;
  - los constructores `Team(id, externalId, name, crest, league)` y `Team(externalId, name, crest, league)` se conservan y dejan `whoscoredId` en `null`;
  - `updateFrom(TeamSnapshot, League)` conserva `whoscoredId`: si no, la sincronización con Football-Data borraría el cruce;
  - `isCrossed()`: `true` si tiene `whoscoredId`;
  - `crossWith(String whoscoredId)`: devuelve el equipo con ese id; si ya tiene el mismo, devuelve uno igual; si tiene otro, lanza `CatalogInvariantException` con `El equipo ya está cruzado con otro identificador de WhoScored.`
- [X] T012 [P] Cambiar el record `Player` en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/modelo/player/Player.java a `(Long id, String externalId, String name, Position position, Team team, LocalDate dateOfBirth, String nationality, boolean active, String whoscoredId)` (data-model.md, "Player"):
  - los constructores de 8, 5 y 4 argumentos se conservan y dejan `whoscoredId` en `null`;
  - `updateFrom(PlayerSnapshot, Team)` y `deactivate()` conservan `whoscoredId`;
  - `isCrossed()` y `crossWith(String)` como en `Team`, con el mensaje `El jugador ya está cruzado con otro identificador de WhoScored.`;
  - `OptionalInt ageOn(LocalDate day)`: la edad cumplida ese día (`Period.between(dateOfBirth, day).getYears()`); vacío si no tiene fecha de nacimiento
- [X] T013 [P] Cambiar el record `Match` en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/modelo/match/Match.java a `(..., MatchWinner winner, String whoscoredId, boolean statsLoaded)` (data-model.md, "Match"):
  - el constructor de 11 argumentos se conserva, con `whoscoredId` en `null` y `statsLoaded` en `false`;
  - `updateFrom(MatchSnapshot, Team, Team)` conserva `whoscoredId` y `statsLoaded`;
  - `needsStats()`: `true` si el estado es `FINISHED` y no tiene estadísticas (FR-010 y FR-011);
  - `isCrossed()` y `crossWith(String)` como en `Team`, con `El partido ya está cruzado con otro identificador de WhoScored.`;
  - `markStatsLoaded()`: el partido con `statsLoaded = true`; nunca vuelve a `false` (FR-012);
  - `LocalDate utcDay()`: la fecha UTC de `utcDate`
- [X] T014 [P] Crear `CurrentSeasons` en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/modelo/season/CurrentSeasons.java: `static CurrentSeasons of(List<Season>)` y `Optional<Season> current(League)`, que devuelve la de fecha de inicio más reciente de esa liga. Una temporada nueva deja de lado a la anterior (caso borde del cambio de temporada)
- [X] T015 [P] En backend/src/main/java/ar/edu/unq/desapp/futbolmarket/persistence/sql/entity/team/TeamSQL.java agregar `whoscoredId` con `@Column(name = "whoscored_id", length = 32)` y sumar a los índices de `@Table` `@Index(name = "ux_teams_whoscored_id", columnList = "whoscored_id", unique = true)`; en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/persistence/mapper/team/TeamMapper.java traducirlo en las dos direcciones (depende de T011)
- [X] T016 [P] En backend/src/main/java/ar/edu/unq/desapp/futbolmarket/persistence/sql/entity/player/PlayerSQL.java agregar `whoscoredId` con `@Column(name = "whoscored_id", length = 32)` y pasar a `@Table(name = "players", indexes = @Index(name = "ux_players_whoscored_id", columnList = "whoscored_id", unique = true))`; la restricción única de `external_id` no cambia. En backend/src/main/java/ar/edu/unq/desapp/futbolmarket/persistence/mapper/player/PlayerMapper.java traducirlo en las dos direcciones (depende de T012)
- [X] T017 [P] En backend/src/main/java/ar/edu/unq/desapp/futbolmarket/persistence/sql/entity/match/MatchSQL.java agregar al final `whoscoredId` (`@Column(name = "whoscored_id", length = 32)`) y `statsLoaded` (`boolean`, `@Column(name = "stats_loaded", nullable = false)` con `@ColumnDefault("false")` de `org.hibernate.annotations`), y sumar a `@Table` el índice `ux_matches_whoscored_id`, único. En backend/src/main/java/ar/edu/unq/desapp/futbolmarket/persistence/mapper/match/MatchMapper.java traducir los dos campos, también en el `@AllArgsConstructor` (depende de T013)
- [X] T018 [P] En backend/src/main/java/ar/edu/unq/desapp/futbolmarket/persistence/sql/interfaces/team/TeamSQLDAO.java agregar `List<TeamSQL> findAllByWhoscoredIdIsNotNull()` y en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/persistence/repository/team/TeamRepository.java `List<Team> findAllCrossed()` (depende de T015)
- [X] T019 [P] En backend/src/main/java/ar/edu/unq/desapp/futbolmarket/persistence/sql/interfaces/player/PlayerSQLDAO.java agregar `@Override @EntityGraph(attributePaths = "team") List<PlayerSQL> findAll()` y en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/persistence/repository/player/PlayerRepository.java `List<Player> findAllWithTeam()`: todos, activos o inactivos, con su equipo, en una consulta. `hasPlayers()` se conserva: lo usa `PlayerRepositoryIT` (depende de T016)
- [X] T020 [P] En backend/src/main/java/ar/edu/unq/desapp/futbolmarket/persistence/sql/interfaces/match/MatchSQLDAO.java agregar `findAllBySeasonExternalId(String)`, `findAllByWhoscoredIdIsNotNull()` y `findAllByStatsLoadedTrue()`, los tres con `@EntityGraph(attributePaths = {"season", "homeTeam", "awayTeam"})`, porque `MatchMapper` traduce los dos equipos fuera de la transacción (data-model.md, "DAOs"). En backend/src/main/java/ar/edu/unq/desapp/futbolmarket/persistence/repository/match/MatchRepository.java agregar `findAllBySeason(String seasonExternalId)`, `findAllCrossed()` y `findAllWithStats()` (depende de T017)
- [X] T021 [P] En backend/src/main/java/ar/edu/unq/desapp/futbolmarket/persistence/repository/season/SeasonRepository.java agregar `List<Season> findAll()`, para `CurrentSeasons`
- [X] T022 [P] Escribir `TeamWhoScoredTest` (clase nueva; `TeamTest` no cambia) en backend/src/test/java/ar/edu/unq/desapp/futbolmarket/modelo/team/TeamWhoScoredTest.java (depende de T011): el id se recorta y en blanco queda `null`; los constructores de conveniencia lo dejan en `null`; `updateFrom` lo conserva; `crossWith` lo pone, con el mismo id devuelve uno igual y con otro lanza la excepción con su mensaje; `isCrossed`
- [X] T023 [P] Escribir `PlayerWhoScoredTest` (clase nueva; `PlayerTest` no cambia) en backend/src/test/java/ar/edu/unq/desapp/futbolmarket/modelo/player/PlayerWhoScoredTest.java (depende de T012): lo mismo que T022, más que `deactivate` conserva el id y que `ageOn` da la edad el día anterior al cumpleaños y el mismo día, y vacío sin fecha de nacimiento
- [X] T024 [P] Escribir `MatchStatsStateTest` (clase nueva; `MatchTest` no cambia) en backend/src/test/java/ar/edu/unq/desapp/futbolmarket/modelo/match/MatchStatsStateTest.java (depende de T013):
  - el constructor de 11 argumentos deja `null` y `false`;
  - `needsStats` es `true` solo para `FINISHED` sin estadísticas, y `false` para todos los demás valores de `MatchStatus` (FR-011, HU1 escenario 15);
  - después de `markStatsLoaded` ya no necesita estadísticas;
  - `updateFrom` conserva el id y la marca, también cuando pasa a `FINISHED`;
  - `crossWith` como en `Team`;
  - `utcDay` de un partido a las 23:30Z es ese día UTC
- [X] T025 [P] Escribir `CurrentSeasonsTest` en backend/src/test/java/ar/edu/unq/desapp/futbolmarket/modelo/season/CurrentSeasonsTest.java (depende de T014): la más reciente de cada liga; vacío para una liga sin temporadas; una temporada nueva reemplaza a la anterior
- [X] T026 Escribir `TeamCrossRepositoryIT` en backend/src/test/java/ar/edu/unq/desapp/futbolmarket/persistence/repository/team/TeamCrossRepositoryIT.java, que fija el armado de los IT de `stats-it` (ver Convenciones) (depende de T018):
  - `@SpringBootTest`, `@ActiveProfiles("test")` y `@TestPropertySource` con la H2 `stats-it`;
  - limpieza antes y después de cada test con los DAOs: partidos, temporadas, jugadores y equipos;
  - casos: el id de WhoScored se guarda y se lee; varios equipos con el id en `null` conviven; dos equipos con el mismo id lanzan `DataIntegrityViolationException`; `findAllCrossed` trae solo los cruzados
- [X] T027 [P] Escribir `PlayerCrossRepositoryIT` en backend/src/test/java/ar/edu/unq/desapp/futbolmarket/persistence/repository/player/PlayerCrossRepositoryIT.java con el armado de T026 (depende de T019 y T026): varios `null` conviven; el id repetido lanza `DataIntegrityViolationException`; `findAllWithTeam` trae activos e inactivos, y el nombre de su equipo se puede leer fuera de una transacción
- [X] T028 [P] Escribir `MatchStatsRepositoryIT` en backend/src/test/java/ar/edu/unq/desapp/futbolmarket/persistence/repository/match/MatchStatsRepositoryIT.java con el armado de T026 (depende de T020, T021 y T026):
  - `findAllBySeason` trae solo los de esa temporada, con sus equipos legibles;
  - `findAllCrossed` y `findAllWithStats` traen solo los que corresponden;
  - el índice único de `whoscored_id` admite varios `NULL` y rechaza un id repetido;
  - **el default de `stats_loaded`**: insertar con `JdbcTemplate` una fila de `matches` sin la columna `stats_loaded` (como una fila de la 005) y leerla con el repository: `statsLoaded()` es `false` (research D17). Es el único SQL nativo de la feature, aprobado por el equipo el 2026-10-14: un `INSERT` estándar, sin funciones de H2;
  - `SeasonRepository.findAll` trae todas las temporadas
- [X] T029 Correr `./gradlew build` en backend/ y verificar `BUILD SUCCESSFUL`, con todos los tests de la 005 sin cambios

### 2B. Rendimiento: contexto `performance` (pasos 2 y 3 del plan)

- [X] T030 [P] Crear el enum `Metric` en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/modelo/performance/Metric.java con las 15 métricas en este orden, que es el de la tabla, el del mapper y el de las filas del snapshot: `GOALS`, `ASSISTS`, `SHOTS`, `SHOTS_ON_TARGET`, `KEY_PASSES`, `PASSES`, `ACCURATE_PASSES`, `SUCCESSFUL_DRIBBLES`, `TACKLES`, `INTERCEPTIONS`, `CLEARANCES`, `AERIALS_WON`, `FOULS_COMMITTED`, `YELLOW_CARDS` y `RED_CARDS`
- [X] T031 Crear `MetricCounts` (valor inmutable sobre un `EnumMap<Metric, Integer>`) en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/modelo/performance/MetricCounts.java (depende de T030): `static of(Map<Metric, Integer>)` copia el mapa y un valor negativo lanza `CatalogInvariantException` con `Una métrica no puede ser negativa.`; `int get(Metric)` devuelve 0 si falta; `with(Metric, int)` devuelve una copia con ese valor (con la misma validación); `equals` y `hashCode` por contenido
- [X] T032 Crear el record `PlayerMatchStats(Long id, Player player, Match match, boolean starter, int minutesPlayed, MetricCounts counts, BigDecimal rating, boolean manOfTheMatch)` en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/modelo/performance/PlayerMatchStats.java con los invariantes y los mensajes de data-model.md: jugador y partido obligatorios, minutos no negativos y rating nulo o entre 0 y 10 con a lo sumo dos decimales (`stripTrailingZeros().scale() <= 2`). Un `counts` nulo queda vacío (depende de T031 y T013)
- [X] T033 [P] Crear los enums `Side` (`HOME` y `AWAY`) y `CardKind` (`YELLOW`, `SECOND_YELLOW` y `RED`, con `isRed()` en los dos últimos) en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/modelo/performance/Side.java y backend/src/main/java/ar/edu/unq/desapp/futbolmarket/modelo/performance/CardKind.java
- [X] T034 [P] Crear los records `ReportedGoal(String scorerWhoscoredId, boolean ownGoal, String assisterWhoscoredId)` y `ReportedCard(String playerWhoscoredId, CardKind kind, int minute)` en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/modelo/performance/ReportedGoal.java y backend/src/main/java/ar/edu/unq/desapp/futbolmarket/modelo/performance/ReportedCard.java (depende de T033)
- [X] T035 Crear el record `ReportedAppearance(String whoscoredId, String name, Integer age, Side side, boolean starter, Integer enteredAt, Integer leftAt, MetricCounts sourceCounts, BigDecimal rating, boolean manOfTheMatch)` en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/modelo/performance/ReportedAppearance.java, con `entered()`: `true` si fue titular o tiene `enteredAt` (FR-004) (depende de T031 y T033)
- [X] T036 Crear el record `ReportedMatchStats(String whoscoredId, boolean finished, String homeTeamWhoscoredId, String awayTeamWhoscoredId, int endMinute, List<ReportedAppearance> appearances, List<ReportedGoal> goals, List<ReportedCard> cards)` en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/modelo/performance/ReportedMatchStats.java, con las listas copiadas y las reglas de data-model.md (research D9 y D10) (depende de T032, T034 y T035):
  - `int minutesPlayed(ReportedAppearance)`: inicio 0 para un titular y `enteredAt` para un suplente; fin, el menor entre `leftAt`, el minuto de su primera tarjeta `SECOND_YELLOW` o `RED` y `endMinute`; devuelve fin − inicio, nunca menos de 0 (FR-005);
  - `goals(appearance)` y `assists(appearance)`: goles que no son en contra de los que es autor, y goles que no son en contra cuyo asistidor es él (FR-006);
  - `yellowCards(appearance)`: tarjetas `YELLOW`; `redCards(appearance)`: `SECOND_YELLOW` o `RED` (FR-007);
  - `List<ReportedAppearance> onPitch()`: las que entraron, en el orden de la fuente;
  - `boolean isFor(Match)`: los dos ids de equipo coinciden con los de los equipos cruzados del partido, con la misma localía;
  - `PlayerMatchStats toStats(ReportedAppearance, Player, Match)`: sin id, con `sourceCounts` más `GOALS`, `ASSISTS`, `YELLOW_CARDS` y `RED_CARDS`, los minutos, el rating y la figura (FR-002 y FR-008)
- [X] T037 [P] Crear `PlayerMatchStatsSQL` en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/persistence/sql/entity/performance/PlayerMatchStatsSQL.java, con las columnas de data-model.md ("PlayerMatchStatsSQL"): `@Table(name = "player_match_stats", indexes = @Index(name = "ux_player_match_stats_player_match", columnList = "player_id, match_id", unique = true))`, `@ManyToOne(fetch = LAZY, optional = false)` a `PlayerSQL` y `MatchSQL`, una columna `INTEGER` no nula por métrica, `rating` `DECIMAL(4,2)` nulo y `man_of_the_match`. Lombok como `MatchSQL`, más `@Setter`
- [X] T038 [P] Crear `PlayerMatchStatsSQLDAO extends JpaRepository<PlayerMatchStatsSQL, Long>` en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/persistence/sql/interfaces/performance/PlayerMatchStatsSQLDAO.java con `@Override @EntityGraph(attributePaths = {"player", "player.team", "match", "match.season", "match.homeTeam", "match.awayTeam"}) List<PlayerMatchStatsSQL> findAll()` (depende de T037)
- [X] T039 Crear `PlayerMatchStatsMapper` (`@Component`) en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/persistence/mapper/performance/PlayerMatchStatsMapper.java (depende de T032 y T037):
  - usa `PlayerMapper` y `MatchMapper`;
  - las 15 columnas se traducen en un único recorrido de `Metric.values()`, con dos tablas `EnumMap` constantes: una de lectores (`ToIntFunction<PlayerMatchStatsSQL>`) y otra de escritores (`ObjIntConsumer<PlayerMatchStatsSQL>`), así no se repite un bloque por métrica;
  - sin lógica de negocio
- [X] T040 Crear `PlayerMatchStatsRepository` (`@Repository`) en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/persistence/repository/performance/PlayerMatchStatsRepository.java con `List<PlayerMatchStats> saveAll(List<PlayerMatchStats>)` y `List<PlayerMatchStats> findAll()`. No borra nada (SC-008) (depende de T038 y T039)
- [X] T041 [P] Escribir `MetricCountsTest` en backend/src/test/java/ar/edu/unq/desapp/futbolmarket/modelo/performance/MetricCountsTest.java (depende de T031): una métrica que falta vale 0; un negativo se rechaza con su mensaje, también en `with`; `of` copia el mapa (cambiar el original no lo afecta); `with` no cambia el original
- [X] T042 [P] Escribir `PlayerMatchStatsTest` en backend/src/test/java/ar/edu/unq/desapp/futbolmarket/modelo/performance/PlayerMatchStatsTest.java (depende de T032): cada invariante con su mensaje; el rating admite `null`, `0`, `10`, `7.65` y `6.00`, y rechaza `-0.01`, `10.01` y `7.655` (FR-008)
- [X] T043 [P] Escribir `ReportedMatchStatsTest` en backend/src/test/java/ar/edu/unq/desapp/futbolmarket/modelo/performance/ReportedMatchStatsTest.java (depende de T036). Partido con 2 minutos agregados en el primer tiempo y 4 en el segundo (`endMinute` 96, desfasaje de 2), como research D9:
  - un titular completo suma 96 (HU1, escenario 4);
  - el titular reemplazado en el 70′ del reloj (`leftAt` 72) suma 72 y su reemplazo (`enteredAt` 72) suma 24;
  - el expulsado en el 60′ (roja en el 62) suma 62 y una roja (escenario 5);
  - la doble amarilla cuenta una amarilla y una roja, y sus minutos terminan en la segunda amarilla (FR-007);
  - el gol en contra no suma gol a su autor ni asistencia a nadie, aunque traiga asistidor (escenario 7);
  - un gol con asistidor suma un gol al autor y una asistencia al asistidor (escenario 6);
  - un suplente que no entró no está en `onPitch`, aunque tenga una tarjeta (FR-004, caso borde);
  - un suplente que entra en el agregado del segundo tiempo suma los minutos hasta el final;
  - `isFor` con los equipos cruzados, al revés y con otro equipo;
  - `toStats` arma las 15 métricas, los minutos, el rating y la figura
- [X] T044 Escribir `PlayerMatchStatsRepositoryIT` en backend/src/test/java/ar/edu/unq/desapp/futbolmarket/persistence/repository/performance/PlayerMatchStatsRepositoryIT.java con el armado de T026, sumando `player_match_stats` primera en la limpieza (depende de T040 y T026):
  - una fila guardada se lee con las 15 métricas, el rating `7.65` y la figura; otra con el rating `null`;
  - dos filas del mismo jugador en el mismo partido lanzan `DataIntegrityViolationException`;
  - `findAll` trae el jugador con su equipo y el partido con su temporada y sus equipos, legibles fuera de una transacción
- [X] T045 Correr `./gradlew build` en backend/ y verificar `BUILD SUCCESSFUL`

### 2C. Modelo de la sincronización de WhoScored (pasos 2 y 3 del plan)

- [X] T046 [P] Crear en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/modelo/sync/ el enum `PageKind` (`LEAGUE`, `TEAM_FIXTURES` y `MATCH`) y los records `BlockedPage(PageKind kind, String whoscoredId, String path)` y `RunCut(BlockedPage page, League league, String matchExternalId)` (PageKind.java, BlockedPage.java y RunCut.java). `matchExternalId` es el id de Football-Data del partido, o `null` si la página no era la de un partido
- [X] T047 [P] Crear el enum `SyncSource` (`FOOTBALL_DATA` y `WHOSCORED`) y el record `LastFullSync(SyncSource source, Instant finishedAt)`, con los dos obligatorios (`CatalogInvariantException`), en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/modelo/sync/SyncSource.java y backend/src/main/java/ar/edu/unq/desapp/futbolmarket/modelo/sync/LastFullSync.java (FR-047)
- [X] T048 Crear en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/modelo/sync/exception/ las excepciones `SourceBlockedException` y `SyncInterruptedException`, las dos `extends RuntimeException` (data-model.md, "Excepciones") (depende de T046):
  - `SourceBlockedException(BlockedPage page)`, con `page()` y el mensaje `WhoScored rechazó dos veces seguidas la página <ruta>.`;
  - `SyncInterruptedException(Throwable cause)`, con `Se interrumpió la espera entre pedidos a WhoScored.`;
  - ninguna llega a HTTP ni al advice
- [X] T049 [P] Crear el record `NormalizedName(String value)` en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/modelo/sync/NormalizedName.java con `ofPlayer(String)` y `ofTeam(String)`, siguiendo los cinco pasos de research D12: NFD sin marcas, las letras que NFD no descompone (`ø`, `æ`, `œ`, `ß`, `đ`, `ð`, `ł`, `ı` y `þ`), minúsculas con `Locale.ROOT`, todo lo que no es letra ni dígito pasa a un espacio y se colapsan los espacios; `ofTeam` además quita las palabras de club de D12 (constante con nombre)
- [X] T050 [P] Crear los records `ReportedTeam(String whoscoredId, String name)` y `ReportedFixture(String whoscoredId, LocalDate date, String homeTeamWhoscoredId, String homeTeamName, String awayTeamWhoscoredId, String awayTeamName, boolean finished)` en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/modelo/sync/ReportedTeam.java y backend/src/main/java/ar/edu/unq/desapp/futbolmarket/modelo/sync/ReportedFixture.java
- [X] T051 [P] Crear en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/modelo/sync/ los tipos del informe (data-model.md, "Corrida e informe de WhoScored" y "Snapshot"):
  - enums `StatsSkipReason` (`UNCROSSED_TEAM`, `NOT_IN_SOURCE`, `NOT_FINISHED_IN_SOURCE`, `INVALID_IN_SOURCE` y `WRITE_FAILED`), `CrossFailure` (`NOT_FOUND`, `AMBIGUOUS` y `NOT_CONFIRMED`), `LeagueStatsStatus` (`PROCESSED`, `CUT` y `NOT_REACHED`), `LoadSource` (`SNAPSHOT` y `SCRAPING`), `SnapshotStatus` (`LOADED`, `MISSING` y `UNREADABLE`) y `SnapshotSkipReason` (`TEAM_NOT_IN_CATALOG`, `MATCH_NOT_IN_CATALOG`, `PLAYER_NOT_IN_CATALOG`, `MATCH_NOT_FINISHED` y `CONFLICTING_CROSS`);
  - records `SkippedStatsMatch(String matchExternalId, String homeTeamName, String awayTeamName, Instant utcDate, StatsSkipReason reason)`, `UncrossedTeam(String teamExternalId, String name, CrossFailure reason)` y `UncrossedPlayer(String whoscoredId, String name, String teamName, String matchExternalId, CrossFailure reason)`
- [X] T052 Crear `TeamCrossing` en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/modelo/sync/TeamCrossing.java con la regla de data-model.md y research D12 (depende de T011, T013, T049, T050 y T051):
  - `static of(List<Team> seasonTeams, List<Match> seasonMatches, Collection<ReportedTeam> sourceTeams, Collection<ReportedFixture> fixtures)`; las filas ya llegan filtradas por torneo y por fechas de la temporada;
  - constantes `MIN_ALIGNED_FIXTURES = 5`, `MAX_UNALIGNED_FIXTURES = 2` y `DATE_TOLERANCE` de un día;
  - coincidencia de calendario entre un equipo W de WhoScored y uno T del catálogo: cada partido de W se alinea con el de T de la misma localía y a lo sumo un día de diferencia (`Match.utcDay()` contra `ReportedFixture.date()`); al menos 5 alineados, a lo sumo 2 de W sin alinear, y los pares de rivales forman una correspondencia uno a uno que no contradice ningún cruce existente;
  - con un único T sin cruzar del mismo `NormalizedName.ofTeam`, W se cruza con él solo si coinciden en calendario (FR-017); si no, T queda `NOT_CONFIRMED`. Sin ninguno con ese nombre, W se cruza con el único T cuyo calendario coincide (FR-017a); con más de uno, `AMBIGUOUS`; con ninguno, `NOT_FOUND` (FR-018);
  - se repite mientras haya cruces nuevos; un equipo ya cruzado no se evalúa (FR-016);
  - `List<Team> crossed()`: los cruzados en esta evaluación, ya con `crossWith`; `List<UncrossedTeam> uncrossed()`: cada equipo del catálogo que sigue sin cruzar, con su motivo
- [X] T053 [P] Crear `CatalogPlayers` en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/modelo/sync/CatalogPlayers.java, el índice en memoria de todos los jugadores del catálogo, activos o inactivos, con su equipo (research D14) (depende de T012 y T049):
  - `static of(List<Player>)`;
  - `Optional<Player> byWhoscoredId(String)` y `Optional<Player> byExternalId(String)` (este último lo usa `SnapshotLoad`, porque las filas del snapshot ubican al jugador por su id de Football-Data);
  - `List<Player> inTeam(Team, NormalizedName)` y `List<Player> named(NormalizedName)`;
  - `confirm(List<Player> crossed)`: actualiza el índice con los cruces de un partido ya guardado
- [X] T054 Crear `PlayerCrossing` en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/modelo/sync/PlayerCrossing.java, que se crea con `(CatalogPlayers catalog, Match match, ReportedMatchStats reported)` (data-model.md y research D14) (depende de T036, T051 y T053):
  - `Optional<Player> cross(ReportedAppearance)`: primero por id de WhoScored (FR-020); si no, entre los del equipo del catálogo de ese lado con el mismo `NormalizedName.ofPlayer`, desempatando por la edad el día del partido (`Player.ageOn(match.utcDay())` contra `age`) (FR-019); si no hay ninguno en el equipo, en todo el catálogo por nombre y misma edad, solo si hay uno único (FR-019a);
  - nunca es candidato un jugador que ya tiene otro id de WhoScored ni uno ya elegido en el mismo partido (FR-022); un jugador sin fecha de nacimiento no gana un desempate por edad;
  - `List<Player> newlyCrossed()`: los cruzados en este partido, con `crossWith`;
  - `List<UncrossedPlayer> uncrossed()`: cada jugador que entró y no se cruzó, con su nombre, el nombre del equipo del catálogo de su lado, el partido y `NOT_FOUND` o `AMBIGUOUS` (FR-021)
- [X] T055 [P] Crear el record `MatchStatsLoad(Match match, List<Player> crossedPlayers, List<PlayerMatchStats> stats, LoadSource source)` en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/modelo/sync/MatchStatsLoad.java, con las listas copiadas. `match` ya viene con `markStatsLoaded()` y, si hacía falta, con su `crossWith` (depende de T032 y T051)
- [X] T056 [P] Crear el record `LeagueStatsResult(League league, LeagueStatsStatus status, int matchesFromSnapshot, int matchesScraped, List<SkippedStatsMatch> skippedMatches, List<UncrossedTeam> uncrossedTeams, List<UncrossedPlayer> uncrossedPlayers, Map<SnapshotSkipReason, Integer> skippedSnapshotEntries)` en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/modelo/sync/LeagueStatsResult.java, con las colecciones copiadas (el mapa como `EnumMap` inmodificable) y `static notReached(League, int matchesFromSnapshot, Map<SnapshotSkipReason, Integer> skipped)` (depende de T051)
- [X] T057 Crear `LeagueStatsSync` en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/modelo/sync/LeagueStatsSync.java con los métodos de data-model.md ("Una liga de una corrida de WhoScored") y research D11 y D13. El servicio lo usa paso a paso y no decide nada (depende de T052 a T056):
  - constructor `(League league, Season currentSeason, List<Match> seasonMatches)`. `currentSeason` puede ser `null` si la liga no tiene temporada en el catálogo: la liga queda sin pendientes;
  - `List<Team> seasonTeams()`: los locales y visitantes de los partidos de la temporada, sin repetir. Los usan `TeamCrossing` y el servicio, para armar `SnapshotLoad`;
  - `pendingMatches()`, del más antiguo al más reciente (FR-010 y FR-011);
  - `recordSnapshot(SnapshotLoad, List<MatchStatsLoad> saved)`: cuenta los guardados como procesados desde el snapshot, toma sus partidos como estado actual y suma las entradas salteadas;
  - `needsLeaguePage()`: `true` si hay pendientes y algún equipo de la temporada no está cruzado;
  - `registerSourceTeams(List<ReportedTeam>)`;
  - `Optional<String> nextFixturesPage()`: vacío si no hay pendientes. Primero, los equipos de WhoScored que no corresponden a ninguno del catálogo, en el orden en que se conocieron, mientras quede algún equipo del catálogo sin cruzar. Después, el equipo cruzado que cubre más pendientes sin id con sus dos equipos cruzados; a igual cantidad, el que aparece primero en los partidos de la temporada. Nunca dos veces el mismo en la corrida;
  - `registerFixtures(String teamWhoscoredId, List<ReportedFixture>)`: descarta las filas fuera de las fechas de la temporada con un día de margen (FR-003), suma los rivales como equipos conocidos y marca la página como pedida;
  - `List<Team> teamCrosses()`: corre `TeamCrossing` y devuelve los equipos a guardar;
  - `List<Match> matchCrosses()`: los partidos de la temporada sin id, con sus dos equipos cruzados, que tienen una única fila con ese local y ese visitante, ya con `crossWith` (FR-015);
  - `confirmTeams(List<Team>)` y `confirmMatches(List<Match>)`: toma los guardados como estado actual;
  - `closeDiscovery()`: omite los pendientes que siguen sin id, con `UNCROSSED_TEAM` si les falta un equipo cruzado y `NOT_IN_SOURCE` si no (FR-014 y FR-018);
  - `matchesToScrape()`: los pendientes con id, en orden de fecha;
  - `Optional<MatchStatsLoad> prepare(Match, Optional<ReportedMatchStats>, CatalogPlayers)`: vacío y omitido con `NOT_FINISHED_IN_SOURCE` si no hay datos o no está terminado, y con `NOT_IN_SOURCE` si `isFor` da `false`; si no, corre `PlayerCrossing` y arma un `MatchStatsLoad` `SCRAPING` con las estadísticas de los que entraron y se cruzaron; los no cruzados van al informe (FR-021);
  - `recordScraped(MatchStatsLoad)`, `recordWriteFailure(Match)` (omite con `WRITE_FAILED`) y `recordInvalidSource(Match)` (omite con `INVALID_IN_SOURCE`: un valor imposible de la fuente, research D18);
  - `LeagueStatsResult result(LeagueStatsStatus)`; con `NOT_REACHED` usa `LeagueStatsResult.notReached` con lo cargado del snapshot
- [X] T058 [P] Crear los records del snapshot `SnapshotTeamEntry(League league, String teamExternalId, String whoscoredId)`, `SnapshotMatchEntry(League league, String matchExternalId, String whoscoredId)`, `SnapshotPlayerRow(String playerExternalId, String whoscoredId, boolean starter, int minutesPlayed, MetricCounts counts, BigDecimal rating, boolean manOfTheMatch)` y `SnapshotStatsEntry(League league, String matchExternalId, List<SnapshotPlayerRow> players)` en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/modelo/sync/ (un archivo por record). `SnapshotPlayerRow.whoscoredId` es `null` si el jugador no está cruzado (contracts/snapshot-format.md); `players` puede estar vacía (depende de T031)
- [X] T059 Crear el record `StatsSnapshot(List<SnapshotTeamEntry> teams, List<SnapshotMatchEntry> matches, List<SnapshotStatsEntry> stats)` en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/modelo/sync/StatsSnapshot.java (data-model.md y contracts/snapshot-format.md, "Orden canónico") (depende de T032 y T058):
  - el constructor copia y ordena: liga en el orden del enum y, dentro de cada liga, id de Football-Data por largo y después alfabético; las filas de jugadores, por id de Football-Data del jugador. Una clave repetida (tipo e id de Football-Data) lanza `CatalogInvariantException`;
  - `static empty()`;
  - `static of(List<Team> crossedTeams, List<Match> crossedMatches, List<Match> loadedMatches, List<PlayerMatchStats> stats)`: una entrada `team` por equipo (liga actual del equipo), una `match` por partido cruzado (liga de su temporada) y una `stats` por partido con estadísticas, con sus filas (FR-023 y FR-029);
  - `StatsSnapshot merge(StatsSnapshot fromDatabase)`: conserva cada entrada vigente tal cual, también las que la base no tiene o tiene distintas, y suma las de la base cuya clave no está (FR-030 y FR-031);
  - `StatsSnapshot forLeague(League)`, `Set<String> teamExternalIds()` (los ids de Football-Data de las entradas `team`) y `Set<String> matchExternalIds()` (los de las entradas `match` y `stats`)
- [X] T060 Crear el record `SnapshotRead(SnapshotStatus status, StatsSnapshot snapshot, String problem)` en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/modelo/sync/SnapshotRead.java con las fábricas `loaded(StatsSnapshot)`, `missing()` y `unreadable(String problem)`; fuera de `LOADED`, `snapshot` es `StatsSnapshot.empty()` (depende de T059)
- [X] T061 Crear el record `CatalogReferences` en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/modelo/sync/CatalogReferences.java y `SnapshotLoad` en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/modelo/sync/SnapshotLoad.java, con las reglas de research D20 y de contracts/snapshot-format.md ("Cómo se carga") (depende de T053, T055 y T059):
  - `CatalogReferences(List<Team> teams, List<Match> matches)`: los equipos y partidos del catálogo, de cualquier temporada, que nombra el snapshot de la liga, con las listas copiadas. Los arma `StatsSnapshotService.references` (T100);
  - constructor de `SnapshotLoad`: `(StatsSnapshot forLeague, List<Match> seasonMatches, List<Team> seasonTeams, CatalogPlayers catalog, CatalogReferences referenced)`;
  - lo que está en `referenced` pero no en la temporada en curso (un equipo que dejó la liga o un partido de otra temporada) se ignora sin contarlo; `TEAM_NOT_IN_CATALOG` y `MATCH_NOT_IN_CATALOG` son solo para lo que no está en el catálogo;
  - `team` de la temporada: sin cruzar y con el id libre, se cruza; con el mismo id, nada; con otro id, o con el id de otro equipo, gana la base (`CONFLICTING_CROSS`);
  - `match` de la temporada: igual que `team`;
  - `stats` de un partido de la temporada: ya tiene estadísticas → se ignora sin contarlo; no está terminado → `MATCH_NOT_FINISHED`; pendiente → un `MatchStatsLoad` `SNAPSHOT`;
  - fila de jugador: si `catalog.byExternalId` no lo encuentra, `PLAYER_NOT_IN_CATALOG` y el resto del partido se carga (FR-026); su id de WhoScored se cruza con las reglas de `team`, y ante un conflicto (`CONFLICTING_CROSS`) sus estadísticas se cargan igual; una fila con `whoscored` en `null` se carga sin cruzar a nadie;
  - un id de WhoScored asignado en esta misma carga cuenta como ocupado para las entradas siguientes;
  - `List<Team> teamCrosses()`, `List<Match> matchCrosses()`, `List<MatchStatsLoad> loads()` y `Map<SnapshotSkipReason, Integer> skipped()`;
  - no consulta WhoScored ni depende de él (FR-025)
- [X] T062 Crear el record `StatsSyncReport(SyncType type, SyncOrigin origin, Instant startedAt, Instant finishedAt, SnapshotStatus snapshot, RunCut cut, List<LeagueStatsResult> leagues)` en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/modelo/sync/StatsSyncReport.java (FR-048), con `Duration duration()`, `int processedMatches()`, `int skippedMatches()` y `Optional<LastFullSync> lastFullSync()`: presente, de `WHOSCORED` y con `finishedAt`, solo si el tipo es `FULL` y `cut` es `null` (FR-047) (depende de T046, T047 y T056)
- [X] T063 Crear `StatsSyncRun` (mutable, de un solo hilo, como `SyncRun`) en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/modelo/sync/StatsSyncRun.java (depende de T060 y T062):
  - `static full(SyncOrigin, Instant)`: `FULL`, con las cinco ligas en el orden del enum; `static singleLeague(League, Instant)`: `SINGLE_LEAGUE` y `MANUAL`;
  - `leagues()`, `snapshotRead(SnapshotRead)`, `record(LeagueStatsResult)`, `cut(BlockedPage, League, String matchExternalId)` e `isCut()`;
  - `StatsSyncReport finish(Instant)`: las ligas en el orden del enum, una por liga pedida; una liga pedida sin resultado queda `LeagueStatsResult.notReached(league, 0, Map.of())` (FR-044)
- [X] T064 [P] Crear la persistencia de la última completa exitosa en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/persistence/ (data-model.md, "LastFullSyncSQL") (depende de T047):
  - `LastFullSyncSQL` en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/persistence/sql/entity/sync/LastFullSyncSQL.java: `@Table(name = "last_full_syncs")`, `@Id @Column(length = 20) String source` y `@Column(name = "finished_at", nullable = false) Instant finishedAt`;
  - `LastFullSyncSQLDAO extends JpaRepository<LastFullSyncSQL, String>` en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/persistence/sql/interfaces/sync/LastFullSyncSQLDAO.java;
  - `LastFullSyncMapper` en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/persistence/mapper/sync/LastFullSyncMapper.java (`name()` y `valueOf`);
  - `LastFullSyncRepository` en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/persistence/repository/sync/LastFullSyncRepository.java con `Optional<LastFullSync> find(SyncSource)` y `LastFullSync save(LastFullSync)`
- [X] T065 Crear `StatsFixtures` (datos de prueba compartidos, sin Spring) en backend/src/test/java/ar/edu/unq/desapp/futbolmarket/modelo/sync/StatsFixtures.java, que usan los tests del modelo, del servicio y de punta a punta (depende de T036, T050 y T058):
  - la temporada 2026/27 de la Premier y seis equipos del catálogo con sus ids de Football-Data, entre ellos "Manchester United FC" y "Everton FC";
  - el calendario de una doble ronda entre los seis (diez partidos por equipo), con fechas UTC, algunos `FINISHED` y otros `TIMED`;
  - lo mismo del lado de WhoScored: `ReportedTeam` con sus ids y nombres cortos ("Man Utd", "Everton") y `ReportedFixture` alineadas, con fechas en la hora de Londres (a lo sumo un día de diferencia);
  - jugadores con fecha de nacimiento, homónimos y un transferido (Brentford → Tottenham, HU1 escenario 9);
  - un `ReportedMatchStats` con 2 y 4 minutos agregados (`endMinute` 96) y las apariciones de los escenarios 4 a 7 de la HU1;
  - helpers para armar un `StatsSnapshot` chico de esos partidos
- [X] T066 [P] Escribir `NormalizedNameTest` en backend/src/test/java/ar/edu/unq/desapp/futbolmarket/modelo/sync/NormalizedNameTest.java (depende de T049): tildes ("Müller", "Martínez"), letras que NFD no descompone ("Ødegaard", "Groß", "Łukasz"), mayúsculas, guiones y apóstrofos; "Everton FC" y "Everton" son iguales con `ofTeam`; "Hull City AFC" y "Hull" no; `ofPlayer` no quita palabras de club
- [X] T067 [P] Escribir `TeamCrossingTest` en backend/src/test/java/ar/edu/unq/desapp/futbolmarket/modelo/sync/TeamCrossingTest.java con `StatsFixtures` (depende de T052 y T065):
  - cruce por nombre confirmado por el calendario (FR-017);
  - "Man Utd" cruzado solo por calendario con "Manchester United FC" (HU1, escenario 11; FR-017a);
  - dos equipos del catálogo con calendarios que coinciden → `AMBIGUOUS`; ninguno → `NOT_FOUND`; el nombre coincide y el calendario no → `NOT_CONFIRMED` (FR-018);
  - dos fechas reprogramadas toleradas y tres no;
  - un rival que contradice un cruce guardado impide el cruce;
  - un equipo ya cruzado no se evalúa (HU1, escenario 12);
  - un cruce habilita el de un rival en la siguiente vuelta
- [X] T068 [P] Escribir `CatalogPlayersTest` en backend/src/test/java/ar/edu/unq/desapp/futbolmarket/modelo/sync/CatalogPlayersTest.java (depende de T053): búsquedas por id de WhoScored, por id de Football-Data, por equipo y nombre, y por nombre; los inactivos se incluyen; `confirm` actualiza las búsquedas por id de WhoScored
- [X] T069 [P] Escribir `PlayerCrossingTest` en backend/src/test/java/ar/edu/unq/desapp/futbolmarket/modelo/sync/PlayerCrossingTest.java con `StatsFixtures` (depende de T054 y T065):
  - por id de WhoScored, sin mirar nombres (HU1, escenario 10);
  - por nombre en el equipo;
  - dos homónimos en el equipo: desempata la edad; con la misma edad, `AMBIGUOUS` (caso borde);
  - el transferido, encontrado en todo el catálogo por nombre y edad (HU1, escenario 9);
  - dos homónimos de la misma edad fuera del equipo → `AMBIGUOUS`;
  - un jugador con otro id de WhoScored nunca es candidato, y uno no se elige dos veces en el mismo partido (FR-022);
  - los no cruzados salen con su nombre, su equipo y el partido (HU1, escenario 8)
- [X] T070 [P] Escribir `LeagueStatsSyncTest` en backend/src/test/java/ar/edu/unq/desapp/futbolmarket/modelo/sync/LeagueStatsSyncTest.java con `StatsFixtures` (depende de T057, T061 y T065):
  - pendientes del más antiguo al más reciente; los programados, postergados, ya procesados y de otra temporada no son pendientes (HU1, escenarios 14 y 15);
  - sin pendientes no pide ninguna página; con todos los equipos cruzados no pide la de la liga (HU1, escenario 12);
  - el orden de `nextFixturesPage`: primero los no correspondidos y después el que cubre más partidos; nunca repite;
  - las filas fuera de las fechas de la temporada se descartan (FR-003);
  - las omisiones con su motivo: `UNCROSSED_TEAM` (HU1, escenario 13), `NOT_IN_SOURCE`, `NOT_FINISHED_IN_SOURCE` (HU4, escenario 8), `INVALID_IN_SOURCE` y `WRITE_FAILED`;
  - `prepare` arma las estadísticas de los que entraron y deja a los no cruzados en el informe;
  - `recordSnapshot` cuenta lo cargado y saca esos partidos de los pendientes;
  - `result` con cada estado
- [X] T071 [P] Escribir `StatsSnapshotTest` en backend/src/test/java/ar/edu/unq/desapp/futbolmarket/modelo/sync/StatsSnapshotTest.java (depende de T059 y T065): el orden canónico (ligas, "9" antes que "10", filas por jugador); una clave repetida se rechaza; `of` arma las tres clases de entradas; `merge` conserva las vigentes (también las que la base no tiene o tiene distintas) y suma solo las nuevas (HU2, escenarios 9 y 10); `forLeague`
- [X] T072 [P] Escribir `SnapshotLoadTest` en backend/src/test/java/ar/edu/unq/desapp/futbolmarket/modelo/sync/SnapshotLoadTest.java (depende de T061 y T065): cada motivo de `SnapshotSkipReason`; los cruces de un equipo, un jugador y un partido, también de partidos futuros (HU2, escenario 2); la base gana ante un conflicto y las estadísticas del jugador se cargan igual; un partido ya procesado conserva las suyas sin contarse (HU2, escenario 6); un partido de otra temporada o un equipo que dejó la liga se ignoran sin contarse, y lo que no está en el catálogo se cuenta; una fila sin id de WhoScored se carga sin cruzar; un partido sin filas se carga igual (HU2, escenario 4)
- [X] T073 [P] Escribir `StatsSyncRunTest` en backend/src/test/java/ar/edu/unq/desapp/futbolmarket/modelo/sync/StatsSyncRunTest.java (depende de T063): completa y de una liga; el informe ordena las ligas; las que no tienen resultado quedan `NOT_REACHED`; `lastFullSync()` presente solo con `FULL` sin corte, y vacío en una de una liga o en una cortada (HU3, escenario 14); los totales del informe
- [X] T074 [P] Escribir `LastFullSyncRepositoryIT` en backend/src/test/java/ar/edu/unq/desapp/futbolmarket/persistence/repository/sync/LastFullSyncRepositoryIT.java con el armado de T026, sumando `last_full_syncs` a la limpieza (depende de T064 y T026): sin filas, `find` es vacío para las dos fuentes (HU3, escenario 9); guardar y leer; guardar dos veces la misma fuente deja una fila con el último momento
- [X] T075 Correr `./gradlew build` en backend/ y verificar `BUILD SUCCESSFUL`

### 2D. Adapter de WhoScored (paso 4 del plan)

- [X] T076 [P] Crear los records de `adapter/whoscored/dto/`, con `@JsonIgnoreProperties(ignoreUnknown = true)`, wrappers en todo lo numérico y `@JsonProperty` en los campos que empiezan con `is` (research D4), en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/adapter/whoscored/dto/ (un archivo por record):
  - `MatchCentreDto(Integer statusCode, Integer expandedMaxMinute, MatchSideDto home, MatchSideDto away, List<MatchEventDto> events)`;
  - `MatchSideDto(Long teamId, String name, List<MatchPlayerDto> players)`;
  - `MatchPlayerDto(Long playerId, String name, Integer age, Boolean isFirstEleven, Boolean isManOfTheMatch, Integer subbedInExpandedMinute, Integer subbedOutExpandedMinute, Map<String, Map<String, BigDecimal>> stats)`: cada métrica es un mapa minuto → valor, también `ratings`;
  - `MatchEventDto(Long eventId, Integer expandedMinute, Long teamId, Long playerId, CodeDto type, Boolean isGoal, Boolean isOwnGoal, List<QualifierDto> qualifiers, Long relatedPlayerId, CodeDto cardType)`, `CodeDto(Integer value, String displayName)` y `QualifierDto(CodeDto type)`;
  - `TournamentFixturesDto(List<TournamentDto> tournaments)`, `TournamentDto(Integer tournamentId, List<TournamentMatchDto> matches)` y `TournamentMatchDto(Long homeTeamId, String homeTeamName, Long awayTeamId, String awayTeamName)`;
  - `FixtureRowDto(Long id, String date, Long homeTeamId, String homeTeamName, Long awayTeamId, String awayTeamName, String status, Integer tournamentId)`
- [X] T077 [P] Crear el enum `WhoScoredTournament` en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/adapter/whoscored/WhoScoredTournament.java con la región, el torneo y la ruta de la página de cada liga de contracts/whoscored-pages.md ("Ligas"), y `static WhoScoredTournament of(League)`
- [X] T078 Crear `WhoScoredPages` en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/adapter/whoscored/WhoScoredPages.java con las tres rutas como `BlockedPage` (tipo, id y ruta): `league(League)` (id: el `tournamentId`), `teamFixtures(String teamId)` → `/teams/{id}/fixtures` y `match(String matchId)` → `/matches/{id}/live` (depende de T046 y T077)
- [X] T079 [P] Crear `ScriptBlockReader` en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/adapter/whoscored/ScriptBlockReader.java: `Optional<String> read(String text, int from)` recorta el objeto o el array que empieza en `from`, contando `{`, `}`, `[` y `]` y salteando los strings con comillas simples o dobles y sus escapes (`\\`, `\"` y `\'`); vacío si no cierra (research D4)
- [X] T080 Crear `MatchCentreExtractor` (`@Component`, con el `JsonMapper` de Boot) en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/adapter/whoscored/MatchCentreExtractor.java (depende de T076 y T079): `Optional<Optional<MatchCentreDto>> extract(String html)` busca `matchCentreData:`; si sigue `null`, devuelve `Optional.of(Optional.empty())` (página válida sin datos, no es un rechazo); si sigue `{`, recorta con `ScriptBlockReader` y lee el JSON; si el marcador falta o el JSON no se puede leer, devuelve `Optional.empty()` (rechazo). Captura solo `JacksonException`
- [X] T081 [P] Crear `FixtureMatchesExtractor` (`@Component`, con el `JsonMapper` de Boot) en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/adapter/whoscored/FixtureMatchesExtractor.java (depende de T076 y T079): `Optional<List<FixtureRowDto>> extract(String html)` busca `fixtureMatches:`, recorta el array y lo normaliza a JSON en un solo recorrido (cada string con comillas simples pasa a dobles, `\'` queda `'` y `"` pasa a `\"`; cada posición vacía `[,`, `,,` o `,]` pasa a `null`); lo lee como `List<List<JsonNode>>` y arma un `FixtureRowDto` por fila con las posiciones 0, 2, 4, 5, 7, 8, 14 y 18 (constantes con nombre, contracts/whoscored-pages.md)
- [X] T082 [P] Crear `TournamentFixturesExtractor` (`@Component`, con el `JsonMapper` de Boot) en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/adapter/whoscored/TournamentFixturesExtractor.java (depende de T076): `Optional<TournamentFixturesDto> extract(String html)` busca el `<script>` con `data-hypernova-key="tournamentfixtures"`, toma su contenido hasta `</script>`, quita el `<!--` y el `-->` y lee el JSON
- [X] T083 Crear `WhoScoredMapper` (`@Component`, SLF4J) en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/adapter/whoscored/WhoScoredMapper.java, que traduce y no cuenta nada (research D7, D8 y D10) (depende de T036, T050, T076 y T077):
  - `List<ReportedTeam> toTeams(TournamentFixturesDto, League)`: los equipos del torneo de la liga, sin repetir y en orden de aparición;
  - `List<ReportedFixture> toFixtures(List<FixtureRowDto>, League)`: solo las filas del torneo de la liga; fecha `dd-MM-yy`; terminado con `FT`, `AET` o `PEN`; ids como texto;
  - `ReportedMatchStats toMatchStats(String matchWhoscoredId, MatchCentreDto)`: terminado con `statusCode` 6; `endMinute` de `expandedMaxMinute`; una `ReportedAppearance` por jugador de cada lado, con las 11 métricas de `stats` sumadas según la tabla de D8 (**quites desde `tackleSuccessful`**, faltas desde `foulsCommited`) y el rating del **mayor minuto numérico** de `ratings` ("10" va después de "9"), redondeado a dos decimales con `RoundingMode.HALF_UP` (como lo muestra la tabla del partido), o `null` sin `ratings`;
  - goles: eventos con `isGoal`; en contra con `isOwnGoal` o el calificador `OwnGoal`; asistidor `relatedPlayerId` solo si el gol trae el calificador `Assisted`;
  - tarjetas: eventos con `type.value` 17; `cardType` 31, 32 y 33 → `YELLOW`, `SECOND_YELLOW` y `RED`, con el `expandedMinute`; otro valor se ignora con un WARN;
  - códigos y nombres de calificadores como constantes con nombre
- [X] T084 [P] Crear la interfaz `PageDownloader` (`DownloadedPage download(String path)`) y el record `DownloadedPage(int status, String body, String failure)` en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/adapter/whoscored/PageDownloader.java y backend/src/main/java/ar/edu/unq/desapp/futbolmarket/adapter/whoscored/DownloadedPage.java: fábricas `of(int status, String body)` y `failed(String reason)`, `isOk()` (200 con cuerpo) y el tamaño en KB para el registro. Es lo único que cambia si se pasa a Playwright (research D5)
- [X] T085 Crear `HttpPageDownloader` (`@Component`) en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/adapter/whoscored/HttpPageDownloader.java con `@Qualifier("whoScoredRestClient") RestClient` (depende de T007 y T084): `get().uri(path).exchange(...)` lee el status y el cuerpo como `String` UTF-8 sin lanzar por un 403; una `RestClientException` (timeout, red) se vuelve `DownloadedPage.failed`, así ninguna excepción del cliente sale del adapter
- [X] T086 [P] Crear la interfaz `WhoScoredSleeper` (`void sleep(Duration)`) y `WhoScoredThreadSleeper` (`@Component`) en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/adapter/whoscored/WhoScoredSleeper.java y backend/src/main/java/ar/edu/unq/desapp/futbolmarket/adapter/whoscored/WhoScoredThreadSleeper.java: ante `InterruptedException` restaura la marca y lanza `SyncInterruptedException`. El nombre es distinto del `ThreadSleeper` de Football-Data a propósito (research D6). Escribir `WhoScoredThreadSleeperTest` en backend/src/test/java/ar/edu/unq/desapp/futbolmarket/adapter/whoscored/WhoScoredThreadSleeperTest.java: con el hilo ya interrumpido, `sleep` lanza `SyncInterruptedException` con su mensaje y la marca de interrupción sigue puesta (el test la limpia al final con `Thread.interrupted()`); una espera de cero termina sin lanzar (depende de T048)
- [X] T087 Crear `WhoScoredClient` (`@Component`, SLF4J, no concurrente) en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/adapter/whoscored/WhoScoredClient.java con `PageDownloader`, `WhoScoredSleeper`, `Clock` y `WhoScoredProperties` (research D6) (depende de T004, T048, T084 y T086):
  - `<T> T fetch(BlockedPage page, Function<String, Optional<T>> extractor)`;
  - antes de cada pedido espera lo que falta para cumplir `requestInterval` desde que terminó la respuesta anterior, medido con el `Clock`; antes del primero no espera. Ese momento se conserva entre corridas;
  - una página es válida solo si responde 200 con cuerpo y el extractor encuentra su bloque;
  - cada pedido deja un INFO `Pedido a WhoScored: {ruta} ({status o falla}, {n} KB; pausa previa de {s} s).` (research D16; quickstart.md 3, 4.1 y 5.3);
  - ante el primer rechazo, WARN `WhoScored rechazó la página {ruta} ({status o falla}, {n} KB): se reintenta en 30 s.`, espera `retryWait` (que ya cubre la pausa) y reintenta una vez (FR-043);
  - si el reintento también falla, WARN y `SourceBlockedException` con la página (FR-044)
- [X] T088 Crear `WhoScoredAdapter` (`@Component`) en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/adapter/whoscored/WhoScoredAdapter.java, lo único que usa el servicio (depende de T078, T080 a T083 y T087):
  - `List<ReportedTeam> fetchLeagueTeams(League)`;
  - `List<ReportedFixture> fetchTeamFixtures(League, String teamWhoscoredId)`;
  - `Optional<ReportedMatchStats> fetchMatchStats(String matchWhoscoredId)`: vacío con `matchCentreData: null`;
  - recibe y devuelve modelo; los DTO no salen
- [X] T089 [P] Crear las fixtures en backend/src/test/resources/whoscored/, recortadas de las páginas guardadas el 2026-10-13 o, si no están, armadas con los fragmentos reales de contracts/whoscored-pages.md y los valores de quickstart.md sección 7, con los marcadores, las claves y los formatos exactos del contrato (research D26). Nunca se bajan de nuevo:
  - match-1983600.html: el bloque `require.config.params["args"]` con `matchCentreData` del Leeds 4-1 Newcastle: el gol en contra del 31′, el gol de Calvert-Lewin asistido por un pase con solo `ShotAssist` y `Assisted`, la amarilla del 43′, Calvert-Lewin titular que sale en el 80, Bard que entra en el 75, un suplente que no entra, Justin con `tackleSuccessful` 2 y `tacklesTotal` 3, un `ratings` donde el mayor minuto numérico no es el mayor alfabético, y un string con `}` dentro de un nombre;
  - match-second-yellow.html: una amarilla con `VoidYellowCard`, la `SecondYellow` (32) del mismo jugador sin `subbedOutExpandedMinute`, un gol asistido por un remate y uno sin asistencia;
  - match-unavailable.html: el bloque con `matchCentreData: null,`;
  - fixtures-everton.html: `fixtureMatches` con filas jugadas, sin jugar (posiciones vacías), de copa, de la temporada anterior y un nombre con apóstrofo escapado;
  - league-premier.html: el `<script data-hypernova-key="tournamentfixtures">` con el JSON entre `<!--` y `-->`;
  - rejected-403.html: un HTML corto sin ninguno de los tres bloques
- [X] T090 [P] Crear `RecordingWhoScoredSleeper` (doble de `WhoScoredSleeper` que registra cada espera, sin dormir) en backend/src/test/java/ar/edu/unq/desapp/futbolmarket/adapter/whoscored/RecordingWhoScoredSleeper.java (depende de T086)
- [X] T091 [P] Escribir `ScriptBlockReaderTest` en backend/src/test/java/ar/edu/unq/desapp/futbolmarket/adapter/whoscored/ScriptBlockReaderTest.java (depende de T079): llaves y corchetes dentro de strings, comillas escapadas, strings con comillas simples, anidamiento y un bloque que no cierra
- [X] T092 [P] Escribir `MatchCentreExtractorTest` en backend/src/test/java/ar/edu/unq/desapp/futbolmarket/adapter/whoscored/MatchCentreExtractorTest.java (depende de T080 y T089): el objeto de match-1983600.html; `null` en match-unavailable.html (válida, sin datos); rejected-403.html y un JSON roto son rechazos
- [X] T093 [P] Escribir `FixtureMatchesExtractorTest` en backend/src/test/java/ar/edu/unq/desapp/futbolmarket/adapter/whoscored/FixtureMatchesExtractorTest.java (depende de T081 y T089): las filas de fixtures-everton.html con sus posiciones; las vacías pasan a `null`; el apóstrofo escapado; una comilla doble dentro de un string simple; sin el marcador es un rechazo
- [X] T094 [P] Escribir `TournamentFixturesExtractorTest` en backend/src/test/java/ar/edu/unq/desapp/futbolmarket/adapter/whoscored/TournamentFixturesExtractorTest.java (depende de T082 y T089): el torneo y los partidos de league-premier.html; sin el script es un rechazo
- [X] T095 [P] Escribir `WhoScoredMapperTest` (sin HTTP) en backend/src/test/java/ar/edu/unq/desapp/futbolmarket/adapter/whoscored/WhoScoredMapperTest.java, leyendo las fixtures con los extractores (depende de T083 y T089):
  - las métricas del 1983600: Calvert-Lewin con 2 goles, 1 amarilla, rating 8.95 y figura; Justin con 2 quites (no 3) y 1 asistencia; Bard con `enteredAt` 75;
  - un rating de 6.666 queda en 6.67;
  - el gol en contra marcado como tal; un remate con `Assisted` que no es gol no da asistencia;
  - la `SecondYellow` como `SECOND_YELLOW` y la amarilla con `VoidYellowCard` como `YELLOW`; un `cardType` desconocido se ignora con un WARN;
  - `toFixtures` descarta la copa y lee "17-10-26" como 2026-10-17; las filas terminadas;
  - `toTeams` sin repetir
- [X] T096 Escribir `WhoScoredAdapterTest` en backend/src/test/java/ar/edu/unq/desapp/futbolmarket/adapter/whoscored/WhoScoredAdapterTest.java con `MockRestServiceServer` sobre `WhoScoredClientConfig.applyDefaults(RestClient.builder(), properties)`, `RecordingWhoScoredSleeper`, un `Clock` fijo y las fixtures (research D26). Para la pausa descontada, un reloj mutable de test: una clase anidada que extiende `Clock` y que el test avanza (depende de T088 a T090):
  - los tres headers y sin `Accept-Encoding`; la ruta de cada tipo de página;
  - cada pedido deja su INFO `Pedido a WhoScored: ...` con la ruta, el status, el tamaño y la pausa;
  - ninguna pausa antes del primer pedido y 4 s antes del segundo; con un reloj que avanza 3 s entre pedidos, la pausa es de 1 s (SC-007);
  - un 403 (rejected-403.html) y después 200: un WARN, una espera de 30 s sin la pausa de 4 s, y el partido se lee (HU4, escenario 1);
  - una página sin bloque y después bien (HU4, escenario 2);
  - una página sin bloque dos veces: `SourceBlockedException` con su `BlockedPage`, y exactamente dos pedidos (HU4, escenario 3);
  - un timeout (`withException(new SocketTimeoutException())`) dos veces corta, y una vez se reintenta; ninguna excepción del cliente HTTP sale;
  - `matchCentreData: null` devuelve vacío sin reintentar;
  - la página de la liga y la de fixtures, con el filtro de torneo
- [X] T097 Correr `./gradlew build` en backend/ y verificar `BUILD SUCCESSFUL`

### 2E. Snapshot: archivo y servicio (paso 6 del plan, adelantado)

Se adelanta para que `StatsSyncService` nazca con su forma final (ver Organización).

- [X] T098 Crear `StatsSnapshotLineMapper` (`@Component`) en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/persistence/mapper/sync/StatsSnapshotLineMapper.java con el formato de contracts/snapshot-format.md (depende de T059):
  - un `JsonMapper` propio (`JsonMapper.builder().build()`), así el formato no depende de la configuración de Boot (FR-030);
  - records anidados del mapper para el encabezado y cada tipo de línea, con `@JsonPropertyOrder` y sin espacios; las filas de jugadores son arrays de 21 columnas en el orden de `Metric`; `starter` y `manOfTheMatch` como `0` o `1`; el rating con dos decimales (`7.65`, `6.00`) o `null`; el id de WhoScored del jugador como string o `null`;
  - `List<String> toLines(StatsSnapshot)`: el encabezado exacto y las entradas en orden canónico;
  - `boolean isHeader(String line)` y `StatsSnapshot fromLines(List<String> entries)`. Una línea ilegible propaga la `JacksonException`
- [X] T099 Crear `StatsSnapshotRepository` (`@Repository`) en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/persistence/repository/sync/StatsSnapshotRepository.java, con la ruta de `futbolmarket.whoscored.snapshot.path` (research D20 y D21) (depende de T060 y T098):
  - `SnapshotRead read()`: si el archivo no existe, `missing()`; lo lee entero en UTF-8; un encabezado distinto, otra versión, una línea ilegible (`JacksonException`) o una clave repetida (`CatalogInvariantException`) dan `unreadable(problema)`, sin cargar nada (FR-027);
  - `void write(StatsSnapshot)`: crea la carpeta si falta, escribe con LF y UTF-8 sin BOM a un temporal en la misma carpeta y lo mueve sobre el vigente (`REPLACE_EXISTING` y `ATOMIC_MOVE`); un `IOException` sale como `UncheckedIOException`;
  - recibe y devuelve modelo
- [X] T100 Crear `StatsSnapshotService` (`@Service`) en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/service/sync/StatsSnapshotService.java con `StatsSnapshotRepository`, `TeamRepository`, `MatchRepository` y `PlayerMatchStatsRepository` (research D20 y D21) (depende de T018, T020, T040, T061 y T099):
  - `SnapshotRead read()`;
  - `CatalogReferences references(StatsSnapshot forLeague)`: `teamRepository.findAllByExternalIds(forLeague.teamExternalIds())` y `matchRepository.findAllByExternalIds(forLeague.matchExternalIds())`, los dos de la 005;
  - `boolean export()`: lee el vigente; si es `UNREADABLE`, registra un ERROR con el problema, no lo pisa y devuelve `false`; si no, arma `StatsSnapshot.of(teamRepository.findAllCrossed(), matchRepository.findAllCrossed(), matchRepository.findAllWithStats(), statsRepository.findAll())`, lo combina con `vigente.merge(...)`, lo escribe y devuelve `true`;
  - solo lee equipos, partidos y estadísticas: nunca usuarios, contraseñas, claves ni operaciones (FR-029)
- [X] T101 [P] Crear backend/src/test/resources/whoscored/snapshot-sample.jsonl con el ejemplo de contracts/snapshot-format.md (encabezado, dos `team`, dos `match` y un `stats`), en orden canónico y con fin de línea LF
- [X] T102 Escribir `StatsSnapshotRepositoryTest` (sin Spring, con `@TempDir`) en backend/src/test/java/ar/edu/unq/desapp/futbolmarket/persistence/repository/sync/StatsSnapshotRepositoryTest.java (depende de T099 y T101):
  - snapshot-sample.jsonl se lee `LOADED` con sus entradas;
  - un archivo que no existe da `MISSING`;
  - una línea rota en el medio, otro encabezado, la versión 2 o una clave repetida dan `UNREADABLE` (HU2, escenario 5);
  - leer snapshot-sample.jsonl y escribirlo da el mismo archivo byte a byte, con LF y sin BOM;
  - la escritura crea la carpeta y no deja temporales
- [X] T103 Escribir `StatsSnapshotServiceTest` en backend/src/test/java/ar/edu/unq/desapp/futbolmarket/service/sync/StatsSnapshotServiceTest.java, con el repository real sobre un `@TempDir`, los repositories de la base mockeados y `StatsFixtures` (depende de T065 y T100):
  - sin snapshot vigente, la exportación escribe las entradas de la base en orden canónico, sin nombres ni datos de usuarios (HU2, escenario 7; FR-029);
  - dos exportaciones sin cambios dan el mismo archivo byte a byte (HU2, escenario 8);
  - después de un partido nuevo, solo se agregan sus líneas y los cruces nuevos; ninguna línea vigente cambia ni se borra (HU2, escenario 9; FR-031);
  - las entradas vigentes que la base no tiene, o tiene distintas, se conservan (HU2, escenario 10);
  - con un vigente ilegible, devuelve `false` y el archivo queda igual byte a byte;
  - `references` trae los equipos y los partidos del catálogo que nombra el snapshot, de cualquier temporada
- [X] T104 Correr `./gradlew build` en backend/ y verificar `BUILD SUCCESSFUL`

### 2F. Servicios de WhoScored (paso 5 del plan y fase 1 del paso 6)

- [X] T105 [P] Crear `SyncLock` (`@Component`) en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/service/sync/SyncLock.java, el candado único de las dos fuentes (research D22): `<T> T runExclusively(Supplier<T>)` hace `tryAcquire` de un `Semaphore(1)`, lanza `SyncInProgressException` si está tomado y libera en un `finally`. No espera ni encola. Por ahora lo usa solo WhoScored; `SyncService` pasa a usarlo en T126
- [X] T106 [P] Crear `StatsSyncReportLogger` (`@Component`, SLF4J) en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/service/sync/StatsSyncReportLogger.java con `void log(StatsSyncReport)`, según research D16 y los ejemplos de quickstart.md 2.2 (depende de T062):
  - una línea por liga, en INFO (en WARN la liga `CUT`): `{LIGA}: {procesada | cortada por bloqueo | no alcanzada por el corte}. Partidos: {a} desde el snapshot y {b} por scraping; {c} omitidos; {d} equipos sin cruzar; {e} jugadores sin cruzar; {f} entradas del snapshot salteadas.`;
  - líneas de detalle en INFO solo si hay algo para listar, con los elementos separados por `, `: `{LIGA}: partidos omitidos: {id} ({local} - {visitante}, {motivo})`, `{LIGA}: equipos sin cruzar: {nombre} ({id}, {motivo})`, `{LIGA}: jugadores sin cruzar: {nombre} ({equipo}, partido {id}, {motivo})` y `{LIGA}: entradas del snapshot salteadas: {motivo} {n}`;
  - una línea final en INFO: `Sincronización de WhoScored {tipo} ({origen}) terminada en {s} s: {n} partidos procesados ({a} desde el snapshot y {b} por scraping), {m} omitidos, sin corte.`; si hubo corte, en lugar de `sin corte.` dice `cortada por bloqueo en la página {ruta} ({liga}, partido {id}).`, sin `, partido {id}` si la página no era la de un partido (quickstart.md 2.2);
  - la línea de inicio la escribe `StatsSyncService`
- [X] T107 [P] Crear `StatsWriteService` (`@Service`) en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/service/sync/StatsWriteService.java con los repositories de equipos, jugadores, partidos y estadísticas (research D18) (depende de T018 a T020 y T040):
  - `@Transactional List<Team> saveTeamCrosses(List<Team>)` y `@Transactional List<Match> saveMatchCrosses(List<Match>)`;
  - `@Transactional void saveMatchStats(MatchStatsLoad)`: los ids de WhoScored de `crossedPlayers`, las filas de `stats` y el partido con su marca, todo o nada (FR-013);
  - sin `if` de negocio; es otro bean para no depender de la auto-invocación
- [X] T108 Crear `StatsSyncService` (`@Service`, no transaccional) en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/service/sync/StatsSyncService.java con el flujo de research D18 y D20 (depende de T057, T061, T063, T064, T088, T100 y T105 a T107):
  - dependencias: `WhoScoredAdapter`, `StatsWriteService`, `StatsSnapshotService`, `SyncLock`, `SeasonRepository`, `MatchRepository`, `PlayerRepository`, `LastFullSyncRepository`, `StatsSyncReportLogger`, `WhoScoredProperties` (para la ruta en el WARN) y `Clock`. **No recibe `FootballDataProperties`** (FR-040);
  - `StatsSyncReport synchronizeAll(SyncOrigin)` y `StatsSyncReport synchronizeLeague(League)`: con `lock.runExclusively` (409); `StatsSyncReport runFull(SyncOrigin)`, de visibilidad de paquete, sin tomar el candado (lo usa la cadena, T127). La corrida se crea recién con el candado tomado;
  - INFO `Sincronización de WhoScored {tipo} ({origen}) iniciada.`;
  - lee el snapshot una vez (`run.snapshotRead`); si no es `LOADED`, WARN `No se encontró el snapshot {ruta}: se scrapea todo lo pendiente.` o `No se pudo leer el snapshot {ruta} ({problema}): se ignora entero y se scrapea todo lo pendiente.` (FR-027);
  - arma `CatalogPlayers.of(playerRepository.findAllWithTeam())` y `CurrentSeasons.of(seasonRepository.findAll())`;
  - **fase 1, para todas las ligas pedidas antes de cualquier pedido a WhoScored** (D18): por liga, la temporada en curso, sus partidos (`findAllBySeason`), un `LeagueStatsSync`, las referencias del catálogo (`snapshotService.references(snapshot.forLeague(liga))`) y un `SnapshotLoad`; guarda sus cruces de equipos y de partidos y los confirma; guarda cada carga con `saveMatchStats` y confirma sus jugadores en `CatalogPlayers`; una `DataAccessException` en una carga deja un WARN y el partido sigue pendiente para la fase 2 (research D18); cierra con `recordSnapshot`;
  - **fase 2, liga por liga**: si la corrida ya se cortó, registra `result(NOT_REACHED)` sin pedir nada; si no, pide la página de la liga si `needsLeaguePage()`; mientras `nextFixturesPage()` tenga un equipo, pide su página, la registra, guarda `teamCrosses()` y confirma, y guarda `matchCrosses()` y confirma; cierra el descubrimiento; pide cada partido de `matchesToScrape()`, lo prepara y lo guarda (`recordScraped`); lo omite con `recordWriteFailure` ante una `DataAccessException` al guardarlo, y con `recordInvalidSource` ante una `CatalogInvariantException` al pedirlo o prepararlo (un valor imposible de la fuente, research D18), los dos con un WARN; registra `result(PROCESSED)`;
  - **corte**: una `SourceBlockedException` se captura, se registra con `run.cut(página, liga, id de Football-Data del partido o null)`, la liga queda `CUT`, las que faltan `NOT_REACHED` y no se hace ningún otro pedido (FR-044 y FR-045). El informe se devuelve igual (FR-039). Sin duplicar el bloque del corte;
  - cierra con `run.finish(clock.instant())`, `StatsSyncReportLogger.log` y `report.lastFullSync().ifPresent(lastFullSyncRepository::save)` (FR-047);
  - `SyncInterruptedException` y los errores de programación no se capturan; el candado se libera igual
- [X] T109 [P] Escribir `SyncLockTest` en backend/src/test/java/ar/edu/unq/desapp/futbolmarket/service/sync/SyncLockTest.java (depende de T105): devuelve el valor; un segundo pedido desde adentro lanza `SyncInProgressException` con `Ya hay una sincronización en curso.`; se libera al terminar y también después de una excepción
- [X] T110 [P] Escribir `StatsSyncReportLoggerTest` (con `OutputCaptureExtension`) en backend/src/test/java/ar/edu/unq/desapp/futbolmarket/service/sync/StatsSyncReportLoggerTest.java (depende de T106 y T065): una liga procesada en INFO y una cortada en WARN; los detalles aparecen solo si hay algo; la línea final con los totales y `sin corte`, o con la página, la liga y el partido del corte (FR-049 y SC-015)
- [X] T111 Escribir `StatsSyncServiceTest` en backend/src/test/java/ar/edu/unq/desapp/futbolmarket/service/sync/StatsSyncServiceTest.java (depende de T065 y T108):
  - armado: Mockito para el adapter, `StatsWriteService`, `StatsSnapshotService`, los repositories y el logger; un `SyncLock` real; `WhoScoredProperties` reales; un `Clock` fijo; datos de `StatsFixtures`;
  - el snapshot se lee una vez y sus cargas de todas las ligas se guardan antes del primer pedido al adapter (`InOrder`) (HU2, escenarios 1 y 3);
  - `MISSING` y `UNREADABLE` dejan su WARN con la ruta y se scrapea todo lo pendiente; el estado va al informe (HU2, escenario 5);
  - sin pendientes no hay ningún pedido (HU1, escenario 14; SC-009);
  - con todo cruzado se piden solo las páginas de los partidos, en orden de fecha;
  - con un equipo sin cruzar se pide la página de la liga y las de fixtures, y los cruces se guardan antes del scraping;
  - un jugador cruzado en un partido se reconoce por su id en el siguiente (HU1, escenario 10);
  - un bloqueo en la página de un partido: corte con la página, la liga y el id de Football-Data del partido; la liga `CUT`, las siguientes `NOT_REACHED` con lo que cargaron del snapshot; ningún otro pedido (`verifyNoMoreInteractions`); lo guardado antes se conserva; devuelve el informe sin lanzar (HU4, escenario 3);
  - un bloqueo en la página de la liga: corte con `LEAGUE` y sin partido;
  - una `DataIntegrityViolationException` al guardar un partido scrapeado lo omite con `WRITE_FAILED` y sigue con el siguiente;
  - una `DataIntegrityViolationException` al guardar una carga del snapshot deja el partido pendiente y se scrapea en la fase 2;
  - el adapter sin datos para un partido lo omite con `NOT_FINISHED_IN_SOURCE` (HU4, escenario 8);
  - un partido con un rating de 11 se omite con `INVALID_IN_SOURCE` y la corrida sigue con el siguiente;
  - una `SyncInterruptedException` del adapter se propaga, no se guarda la última completa y el candado queda libre;
  - una de una liga lee y pide solo esa liga, es `SINGLE_LEAGUE` y `MANUAL`, y no guarda la última completa (HU1, escenario 16; HU3, escenario 14);
  - una completa sin corte guarda `LastFullSync(WHOSCORED, fin)`; una cortada no;
  - un segundo pedido desde la respuesta del adapter lanza `SyncInProgressException`; al terminar, y después de una `RuntimeException` inesperada que se propaga, se puede volver a sincronizar;
  - `runFull` funciona con el candado ya tomado por quien lo llama;
  - el informe va una vez al logger, y el inicio y el fin salen del reloj
- [X] T112 Escribir `StatsWriteServiceIT` en backend/src/test/java/ar/edu/unq/desapp/futbolmarket/service/sync/StatsWriteServiceIT.java con el armado de T026 y la limpieza completa (depende de T107 y T065):
  - `saveMatchStats` guarda las filas, los ids de WhoScored de los jugadores y la marca del partido;
  - una carga con dos filas del mismo jugador lanza `DataIntegrityViolationException` y no deja nada: ninguna fila, la marca en `false` y el jugador sin id (FR-013; HU4, escenario 5; SC-008);
  - `saveTeamCrosses` y `saveMatchCrosses` guardan los ids;
  - **una sincronización de Football-Data posterior conserva los cruces y la marca**: con el catálogo armado por `SyncWriteService.applyLeague` con `SnapshotFixtures.snapshot(PREMIER)` (como `SyncWriteServiceIT`), cruzar un equipo, un jugador y un partido y guardar sus estadísticas; aplicar otra vez la misma liga deja los ids de WhoScored, la marca y las filas iguales (SC-010)
- [X] T113 Correr `./gradlew build` en backend/ y verificar `BUILD SUCCESSFUL`

**Checkpoint**: el motor de WhoScored está listo y probado. Las historias pueden empezar.

---

## Fase 3: Historia 1 - Estadísticas de los partidos terminados (Prioridad: P1) 🎯 MVP

**Objetivo**: un administrador dispara `POST /matches/stats/sync[?league=]` y cada partido
terminado sin estadísticas queda con las de cada jugador que entró a la cancha, con un informe de
lo procesado y lo que no se pudo cruzar (FR-001 a FR-022, FR-037 a FR-040, FR-048 y FR-049).

**Prueba independiente**: con un catálogo sincronizado y partidos terminados sin estadísticas, el
administrador dispara la sincronización contra un WhoScored simulado (`@MockitoBean`). Cada partido
terminado queda con las métricas de sus jugadores, los no terminados no se tocan y los jugadores
sin cruzar figuran en el informe. Un usuario común recibe 403 y una petición sin credencial 401,
sin consultar WhoScored.

**Cobertura de los escenarios**: 1, 3, 8, 13, 14, 15, 16, 17, 18, 19 y 20 → T117; 2 → T042, T044
y T117; 4 a 7 → T043 y T095; 9 y 10 → T069; 11 y 12 → T067 y T070; 14 → además T111; FR-040 →
T118.

### Implementación de la Historia 1

- [X] T114 [P] [US1] Crear el record `StatsSyncReportResponse`, con records anidados y `static StatsSyncReportResponse from(StatsSyncReport)`, como `SyncReportResponse.from`, en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/controller/sync/dto/StatsSyncReportResponse.java:
  - los campos y los nombres son exactamente los del esquema `StatsSyncReportResponse` de contracts/sync-api.yaml;
  - el modelo se traduce a esos nombres: `matchExternalId` pasa a `externalId` en los omitidos y a `match` en los jugadores y en el corte; `homeTeamName` a `homeTeam`; `awayTeamName` a `awayTeam`; `teamExternalId` a `externalId`; `teamName` a `team`; la página del corte a `page`, `whoscoredId` y `path`;
  - `skippedSnapshotEntries` es una lista de `{reason, count}` en el orden del enum, vacía si no hay;
  - `cut` sale `null` si no hubo corte
- [X] T115 [US1] Crear `StatsSyncController` en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/controller/sync/StatsSyncController.java (depende de T114):
  - `@RestController` y `@RequestMapping("/matches/stats/sync")`;
  - `@Tag(name = "Sincronización")` y dos `@SecurityRequirement` a nivel de clase (`OpenApiConfig.BEARER_AUTH` y `OpenApiConfig.API_KEY_AUTH`), como `SyncController`;
  - `@PostMapping` con `@RequestParam(required = false) String league`: sin `league` llama a `statsSyncService.synchronizeAll(SyncOrigin.MANUAL)`; con `league`, a `synchronizeLeague(League.fromName(league))`, que rechaza un valor no admitido con 400 antes de consultar nada (research D24). Responde 200 con `StatsSyncReportResponse.from(report)`, también con un corte (FR-039);
  - `@Operation` y el parámetro en español, tomados de contracts/sync-api.yaml; un `@ApiResponse` por código: 200, 400, 401, 403 y 409, con el esquema `ApiError` en los errores;
  - solo habla con `StatsSyncService`; no dispara la de Football-Data (FR-038)
- [X] T116 [P] [US1] En backend/src/main/java/ar/edu/unq/desapp/futbolmarket/security/SecurityConfig.java agregar `.requestMatchers(pathPattern(HttpMethod.POST, "/matches/stats/sync")).hasRole(Role.ADMIN.name())` junto a la regla de `/players/sync`, antes de `anyRequest().authenticated()` (research D24)

### Tests de la Historia 1

- [X] T117 [US1] Escribir `StatsSyncControllerIT` en backend/src/test/java/ar/edu/unq/desapp/futbolmarket/e2e/StatsSyncControllerIT.java, siguiendo el patrón de `SyncControllerIT` (depende de T115 y T116). Nace con el armado que usan también la HU2, la HU3 y la HU4:
  - anotaciones: `@SpringBootTest`, `@AutoConfigureMockMvc`, `@ActiveProfiles("test")` y `@ExtendWith(OutputCaptureExtension.class)`;
  - `@DynamicPropertySource` con un administrador al azar, `futbolmarket.football-data.token` al azar, una H2 propia (`jdbc:h2:mem:stats-e2e-<sufijo>;DB_CLOSE_DELAY=-1`) y `futbolmarket.whoscored.snapshot.path` en `build/test-snapshot/stats-e2e-<sufijo>.jsonl`;
  - `@MockitoBean WhoScoredAdapter` y `@MockitoBean FootballDataAdapter`;
  - `@BeforeEach`: borra estadísticas, partidos, temporadas, jugadores, equipos y últimas completas con sus DAOs (los usuarios quedan), borra el archivo del snapshot si existe (`Files.deleteIfExists`), siembra con los repositories un catálogo de `StatsFixtures` (equipos y partidos de la Premier ya cruzados, dos terminados sin estadísticas y uno programado) y obtiene el token del administrador con `AuthTestHelper`;
  - una completa responde 200 con `type: FULL`, `origin: MANUAL`, `snapshot: MISSING`, `cut: null` y las cinco ligas `PROCESSED` en el orden del enum; la Premier con `matchesScraped: 2`. En la base, cada jugador que entró tiene su fila con las métricas, los dos partidos tienen la marca y el programado no se tocó (escenarios 1, 2, 3, 15 y 19);
  - un jugador que WhoScored informa y no está en el catálogo figura en `uncrossedPlayers` con su nombre, su equipo y el partido, y el partido se guarda con los demás (escenario 8);
  - un equipo sin cruzar: sus partidos van a `skippedMatches` con `UNCROSSED_TEAM` y el equipo a `uncrossedTeams`; los demás se procesan (escenario 13);
  - una segunda completa no pide ninguna página de partido y las filas quedan iguales (escenario 14; SC-009 y SC-010);
  - `league=PREMIER` responde `SINGLE_LEAGUE` con una sola liga y el adapter solo recibe pedidos de esa liga (escenario 16);
  - `league=MLS` responde 400 con `La liga 'MLS' no es una de las admitidas: ...` y `verifyNoInteractions(whoScoredAdapter)` (escenario 17);
  - un usuario común recibe 403 y una petición sin credencial 401, sin tocar el adapter (escenario 18; SC-017);
  - después de la de WhoScored, `verifyNoInteractions(footballDataAdapter)`; y después de `POST /players/sync`, `verifyNoInteractions(whoScoredAdapter)` (escenario 20; FR-038)
- [X] T118 [P] [US1] Escribir `StatsSyncWithoutTokenIT` en backend/src/test/java/ar/edu/unq/desapp/futbolmarket/e2e/StatsSyncWithoutTokenIT.java, como `SyncDisabledIT` de la 005: sin token de Football-Data (el del perfil test), con su H2 propia, `@MockitoBean WhoScoredAdapter` y un catálogo sembrado, `POST /matches/stats/sync` como administrador responde 200 y guarda las estadísticas (FR-040). Es una clase aparte porque `StatsSyncControllerIT` necesita el token para los 409 de la HU3 (research D26) (depende de T115 y T116)
- [X] T119 [US1] Correr `./gradlew build` en backend/ y verificar `BUILD SUCCESSFUL`

**Checkpoint**: la HU1 funciona sola. Las estadísticas se obtienen con un disparo manual. Es el MVP.

---

## Fase 4: Historia 2 - Arranque rápido con el snapshot (Prioridad: P2)

**Objetivo**: la sincronización completa primero desde el snapshot los partidos pendientes que
este trae, sin consultar WhoScored, y un desarrollador lo regenera con un comando de desarrollo
(FR-023 a FR-032).

**Prueba independiente**: con una base sin estadísticas y un snapshot que cubre parte de los
partidos, esos partidos quedan con las estadísticas del snapshot sin pedir su página, y solo se
scrapean los demás. Regenerar el snapshot sin cambios en la base lo deja idéntico.

**Cobertura de los escenarios**: 1, 3, 4, 5 y 6 → T122 y T111; 2 → T072 y T122; 7 a 10 → T103 y
T071; 8 y 9 → además T142; 11 → T120 y T145.

### Implementación de la Historia 2

- [X] T120 [US2] Crear la clase main `StatsSnapshotExport` (final, con constructor privado) en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/StatsSnapshotExport.java, junto a `FutbolMarketApplication` (research D21):
  - levanta Spring con `new SpringApplicationBuilder(FutbolMarketApplication.class).web(WebApplicationType.NONE)` y argumentos que pisan cualquier otra fuente: `--spring.profiles.active=local`, `--futbolmarket.football-data.sync.on-startup=false` y `--futbolmarket.football-data.sync.cron=-`. No corre ninguna sincronización;
  - llama a `StatsSnapshotService.export()`, cierra el contexto con `SpringApplication.exit` y termina con código 0, o 1 si el vigente no se pudo leer (única llamada a `System.exit`, en `main`);
  - no es un recurso de la aplicación: no hay endpoint (FR-028)
- [X] T121 [US2] En backend/build.gradle agregar la tarea `exportStatsSnapshot` sin dependencias nuevas (research D21): `tasks.register('exportStatsSnapshot', JavaExec)` con `group`, una `description` en español, `classpath = sourceSets.main.runtimeClasspath`, `mainClass = 'ar.edu.unq.desapp.futbolmarket.StatsSnapshotExport'` y `workingDir = projectDir`. No reemplazar el archivo ni tocar lo existente (depende de T120)

### Tests de la Historia 2

- [X] T122 [US2] Agregar métodos a `StatsSyncControllerIT` en backend/src/test/java/ar/edu/unq/desapp/futbolmarket/e2e/StatsSyncControllerIT.java, escribiendo el snapshot del test en su ruta (después de crear la carpeta con `Files.createDirectories`) con `StatsSnapshotLineMapper` y datos de `StatsFixtures` (depende de T117):
  - un snapshot con uno de los dos partidos terminados: queda con las estadísticas del snapshot sin que el adapter reciba su página, el otro se scrapea, y el informe dice `snapshot: LOADED`, `matchesFromSnapshot: 1` y `matchesScraped: 1` (escenario 1);
  - los cruces del snapshot quedan guardados (escenario 2);
  - con el adapter lanzando `SourceBlockedException` desde el primer pedido, lo del snapshot se carga igual y el corte llega después (escenario 3);
  - una entrada de un partido que no está en el catálogo y una fila de un jugador que no está se saltean y se cuentan en `skippedSnapshotEntries`; el resto se carga (escenario 4);
  - un snapshot con una línea rota: `snapshot: UNREADABLE`, un WARN en el registro y se scrapea todo (escenario 5);
  - un partido que ya tenía estadísticas conserva las suyas (escenario 6)
- [X] T123 [US2] Correr `./gradlew build` en backend/ y verificar `BUILD SUCCESSFUL`. Con la aplicación detenida, correr `./gradlew exportStatsSnapshot` sobre la base local y verificar que termina solo, sin levantar el servidor web ni sincronizar. Si generó backend/snapshot/whoscored-stats.jsonl, no commitearlo todavía: el primer snapshot se genera en T142

**Checkpoint**: las HU1 y HU2 funcionan. Una base nueva se completa desde el snapshot.

---

## Fase 5: Historia 3 - Ponerse al día solo (Prioridad: P3)

**Objetivo**: un candado común para las dos fuentes, la cadena del lunes (Football-Data y después
WhoScored) y la puesta al día al arrancar según la última completa exitosa de cada fuente
(FR-033 a FR-036, FR-041, FR-047 y FR-050 a FR-055; paso 7 del plan).

**Prueba independiente**: con las últimas completas de cada fuente de más de 7 días, de menos y
sin ninguna, se verifica qué corre al arrancar y en qué orden; con el reloj en el lunes a las
04:00 corre la cadena; con Football-Data fallando, WhoScored corre igual; y con una cadena en
curso, cualquier disparo manual responde 409.

**Cobertura de los escenarios**: 1 → T127, T128 y T140; 2 a 5 → T125 y T127; 3 a 5 → además
T141; 6 → T129 y T131; 7 → T127 y T131; 8 → T127, T128 y T141; 9 → T074 y T125; 10 → T131 y
T132; 11 → T129; 12 y 13 → T124, T126 y T131; 14 → T073 y T111.

### Implementación y tests de la Historia 3

Cada tarea cierra con sus tests. T126, T128 y T129 modifican los tres tests de la 005 que lista
research D26, y nada más.

- [X] T124 [P] [US3] Agregar `Optional<LastFullSync> lastFullSync()` al record `SyncReport` de la 005 en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/modelo/sync/SyncReport.java: presente, de `FOOTBALL_DATA` y con `finishedAt`, solo si el tipo es `FULL` y ninguna liga falló (FR-047). Escribir `SyncReportLastFullSyncTest` (clase nueva; `SyncRunTest` no cambia) en backend/src/test/java/ar/edu/unq/desapp/futbolmarket/modelo/sync/SyncReportLastFullSyncTest.java, armando los informes con `SyncRun` como `SyncRunTest`: completa con las cinco ligas bien → presente; con una liga fallida → vacío; de una liga → vacío (HU3, escenario 13)
- [X] T125 [P] [US3] Crear el enum `CatchUp` (`CHAIN`, `WHOSCORED_ONLY` y `NONE`) y el record `CatchUpPolicy(Duration maxAge)` en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/modelo/sync/CatchUp.java y backend/src/main/java/ar/edu/unq/desapp/futbolmarket/modelo/sync/CatchUpPolicy.java (data-model.md, "Origen, fuente y puesta al día"): `maxAge` positiva (`CatalogInvariantException`) y `CatchUp decide(Optional<LastFullSync> footballData, Optional<LastFullSync> whoScored, Instant now)` con la tabla de research D22; una completa con exactamente 7 días está al día. Escribir `CatchUpPolicyTest` en backend/src/test/java/ar/edu/unq/desapp/futbolmarket/modelo/sync/CatchUpPolicyTest.java: Football-Data sin ninguna, de 8 días, de 7 días exactos y de 3 días, combinadas con WhoScored sin ninguna, de 9 días y de 2 días (HU3, escenarios 2 a 5 y 9; FR-034 y FR-035)
- [X] T126 [US3] Pasar `SyncService` al candado común en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/service/sync/SyncService.java (research D22 y D23) (depende de T105, T064 y T124):
  - el `Semaphore` propio se reemplaza por `SyncLock`. Los campos quedan en este orden: `properties`, `adapter`, `writeService`, `playerRepository`, `reportLogger`, `lastFullSyncRepository`, `syncLock` y `clock`;
  - `synchronizeAll` y `synchronizeLeague` mantienen sus firmas: revisan el token (503) y después toman el candado (409), en el mismo orden de la 005;
  - suma `SyncReport runFull(SyncOrigin)`, de visibilidad de paquete, que corre una completa sin tomar el candado ni revisar el token (lo hace la cadena);
  - al terminar cualquier corrida, `report.lastFullSync().ifPresent(lastFullSyncRepository::save)`, también con un disparo manual (HU3, escenario 12);
  - **modificar `SyncServiceTest`** en backend/src/test/java/ar/edu/unq/desapp/futbolmarket/service/sync/SyncServiceTest.java, solo como dice D26: agregar `@Mock LastFullSyncRepository lastFullSyncRepository` y que el helper `service(token, clock)` arme `new SyncService(properties(token), adapter, writeService, playerRepository, reportLogger, lastFullSyncRepository, new SyncLock(), clock)`. Ningún método, aserción ni dato cambia, incluido el 409;
  - escribir `SyncServiceLastFullSyncTest` (clase nueva, research D26) en backend/src/test/java/ar/edu/unq/desapp/futbolmarket/service/sync/SyncServiceLastFullSyncTest.java: una completa con las cinco ligas bien guarda `LastFullSync(FOOTBALL_DATA, fin)`, también la manual; con una liga fallida o de una sola liga no guarda nada (HU3, escenarios 7, 12 y 13); `runFull` funciona con el candado ya tomado por quien lo llama
- [X] T127 [US3] Crear `SyncCoordinator` (`@Service`, SLF4J) en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/service/sync/SyncCoordinator.java con `FootballDataProperties`, `SyncProperties`, `SyncLock`, `SyncService`, `StatsSyncService`, `LastFullSyncRepository` y `Clock` (research D22) (depende de T108, T125 y T126):
  - `void runWeeklyChain()`: la cadena con origen `WEEKLY`;
  - la cadena toma el candado de punta a punta (`lock.runExclusively`), así cuenta como una sola sincronización (FR-041): sin token, INFO `Se omite Football-Data.org en la cadena: falta la credencial (FOOTBALL_DATA_TOKEN).`; con token, `syncService.runFull(origen)`; después, siempre, `statsSyncService.runFull(origen)`, aunque Football-Data haya fallado en alguna liga o en todas (FR-036). Un error de programación no se captura;
  - `void catchUpOnStartup()`: lee las dos últimas completas, decide con `new CatchUpPolicy(syncProperties.catchUpAfter()).decide(..., clock.instant())` y deja uno de estos INFO (quickstart.md 2.2, 2.3 y 2.5), con la fecha como `Instant`:
    - `CHAIN` sin ninguna de Football-Data: `Puesta al día al arrancar: no hay una sincronización completa exitosa de Football-Data.org registrada: se corren las dos sincronizaciones en segundo plano.`;
    - `CHAIN` con la de Football-Data vencida: `Puesta al día al arrancar: la última sincronización completa exitosa de Football-Data.org terminó el {finishedAt}: se corren las dos sincronizaciones en segundo plano.`;
    - `WHOSCORED_ONLY` sin ninguna de WhoScored: `Puesta al día al arrancar: no hay una sincronización completa exitosa de WhoScored registrada: se corre solo la de WhoScored en segundo plano.`;
    - `WHOSCORED_ONLY` con la de WhoScored vencida: `Puesta al día al arrancar: la última sincronización completa exitosa de WhoScored terminó el {finishedAt}: se corre solo la de WhoScored en segundo plano.`;
    - `NONE`: `Las dos fuentes están al día: no se sincroniza al arrancar.`

    Después, `CHAIN` corre la cadena con `CATCH_UP` y `WHOSCORED_ONLY` llama a `statsSyncService.synchronizeAll(CATCH_UP)`;
  - con otra en curso, `SyncInProgressException` se propaga: la manejan `StartupSync` y `SyncScheduler`;
  - escribir `SyncCoordinatorTest` (con `OutputCaptureExtension`) en backend/src/test/java/ar/edu/unq/desapp/futbolmarket/service/sync/SyncCoordinatorTest.java, con `SyncLock` real, los dos servicios y el repository mockeados y un `Clock` fijo: la cadena en orden (`InOrder`); sin token saltea Football-Data con el INFO exacto y corre WhoScored (HU3, escenario 8); con Football-Data fallida corre WhoScored (escenario 7); un disparo manual pedido desde adentro de cada paso lanza `SyncInProgressException` (escenario 10); las tres decisiones de la puesta al día con su origen `CATCH_UP` y sus líneas (escenarios 1 a 5); con el candado tomado, `runWeeklyChain` y `catchUpOnStartup` lanzan `SyncInProgressException`
- [X] T128 [US3] Renombrar `STARTUP` a `CATCH_UP` en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/modelo/sync/SyncOrigin.java (con su Javadoc) y pasar `StartupSync` a la puesta al día en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/service/sync/StartupSync.java (FR-034, FR-035, FR-051 y FR-053) (depende de T127):
  - constructor `(FootballDataProperties properties, SyncCoordinator coordinator)`; el método pasa a `catchUp()`, con `@Async` y `@EventListener(ApplicationReadyEvent.class)`;
  - sin token, escribe la misma advertencia exacta de la 005 y **sigue**: WhoScored corre igual (HU3, escenario 8);
  - con `on-startup` en `false` termina sin hacer nada; si no, llama a `coordinator.catchUpOnStartup()`;
  - con `SyncInProgressException`, el mismo WARN de la 005: `Se omite la sincronización de arranque: {motivo}`;
  - la regla del catálogo vacío desaparece (`hasPlayers()` queda en `PlayerRepository`);
  - **modificar `StartupSyncTest`** en backend/src/test/java/ar/edu/unq/desapp/futbolmarket/service/sync/StartupSyncTest.java, solo como dice D26: `@Mock SyncCoordinator coordinator` en lugar de los mocks de `PlayerRepository` y `SyncService`, y el helper arma `new StartupSync(properties, coordinator)`; `conElCatalogoVacioYTokenDisparaUnaCompletaDeArranque` pasa a llamarse `conTokenDelegaLaPuestaAlDiaEnElCoordinador` y verifica que delega en `coordinator.catchUpOnStartup()`; `sinTokenDejaLaAdvertenciaExactaYNoConsultaElCatalogo` pasa a llamarse `sinTokenDejaLaAdvertenciaExactaYSePoneAlDiaIgual`, conserva la advertencia exacta y verifica que la puesta al día corre igual; `conLaSincronizacionDeArranqueApagadaNoHaceNada` cambia solo el mock que verifica; `conOtraEnCursoDejaUnWarnSinPropagarLaExcepcion` hace que el coordinador lance `SyncInProgressException`, con el mismo WARN; **se borra** `conJugadoresNoDisparaYDejaElInfo`. Se quitan los imports que quedan sin uso
- [X] T129 [US3] Pasar `SyncScheduler` a la cadena en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/service/sync/SyncScheduler.java (FR-033) (depende de T127):
  - constructor `(SyncCoordinator coordinator)`; `runWeeklySync()` conserva su `@Scheduled` con el cron y la zona de la configuración y llama a `coordinator.runWeeklyChain()`; el INFO sin token pasa al coordinador;
  - con `SyncInProgressException`, el mismo WARN de la 005: `Se omite la sincronización semanal: {motivo}` (HU3, escenario 11);
  - **modificar `SyncSchedulerTest`** en backend/src/test/java/ar/edu/unq/desapp/futbolmarket/service/sync/SyncSchedulerTest.java, solo como dice D26: `@Mock SyncCoordinator` en lugar del de `SyncService` y el helper arma `new SyncScheduler(coordinator)`; `conTokenDisparaUnaSincronizacionCompletaSemanal` pasa a llamarse `disparaLaCadenaSemanal` y verifica `runWeeklyChain()`; **se borra** `sinTokenNoLlamaAlServicioYDejaUnInfo`, que quedaría igual al anterior (el salteo de Football-Data sin token y su INFO se prueban en `SyncCoordinatorTest`); `conOtraEnCursoNoPropagaLaExcepcionYDejaUnWarnConElMotivo` hace que lance el coordinador, con el mismo WARN; `laCorridaSemanalTomaElCronYLaZonaDeLaConfiguracion` **no cambia**. Se quitan los imports que quedan sin uso
- [X] T130 [P] [US3] En backend/src/main/java/ar/edu/unq/desapp/futbolmarket/controller/sync/SyncController.java cambiar solo la descripción del `@ApiResponse` 409: también responde 409 si corre WhoScored o la cadena (contracts/sync-api.yaml, `SyncInProgress`). El comportamiento no cambia y `SyncControllerIT` tampoco
- [X] T131 [US3] Escribir `SyncChainIT` en backend/src/test/java/ar/edu/unq/desapp/futbolmarket/service/sync/SyncChainIT.java con el armado de T026 (H2 `stats-it` y limpieza completa), un token de Football-Data de prueba en `@TestPropertySource`, `@MockitoBean FootballDataAdapter` (devuelve `SnapshotFixtures.snapshot(liga)`) y `@MockitoBean WhoScoredAdapter` (depende de T127 a T129):
  - `runWeeklyChain()` corre Football-Data y después WhoScored (`InOrder` sobre los adapters) y guarda la última completa de las dos fuentes (HU3, escenario 6);
  - con Football-Data fallando en las cinco ligas, WhoScored corre igual y la última completa de Football-Data no cambia (escenario 7);
  - con el adapter de Football-Data detenido en un latch, y después con el de WhoScored detenido (entre los pasos), `syncService.synchronizeAll(MANUAL)` y `statsSyncService.synchronizeAll(MANUAL)` lanzan `SyncInProgressException` (escenario 10; SC-013); latches con timeout, sin `Thread.sleep`;
  - una completa manual de Football-Data con las cinco ligas bien guarda su última completa, y una de una liga no la cambia (escenarios 12 y 13)
- [X] T132 [US3] Agregar métodos a `StatsSyncControllerIT` en backend/src/test/java/ar/edu/unq/desapp/futbolmarket/e2e/StatsSyncControllerIT.java, con latches como el 409 de `SyncControllerIT` (depende de T117 y T127). Para la cadena, agregar un campo `@Autowired SyncCoordinator` (agregar un campo no modifica ningún método existente):
  - `POST /matches/stats/sync` mientras corre `POST /players/sync` responde 409 con `Ya hay una sincronización en curso.`, y al revés también (FR-041);
  - con la cadena corriendo en otro hilo, detenida en Football-Data y después en WhoScored, los dos disparos manuales responden 409 (HU3, escenario 10)
- [X] T133 [US3] Correr `./gradlew build` en backend/ y verificar `BUILD SUCCESSFUL`

**Checkpoint**: las HU1 a HU3 funcionan. La aplicación se pone al día sola.

---

## Fase 6: Historia 4 - Fallas y bloqueos de WhoScored (Prioridad: P4)

**Objetivo**: un rechazo aislado se reintenta, dos fallas seguidas de una página cortan la corrida
sin perder lo guardado, la siguiente sigue donde quedó y el resto del sistema nunca depende de
WhoScored (FR-042 a FR-046). El reintento y el corte viven en el motor (T087 y T108); esta fase
los prueba de punta a punta.

**Prueba independiente**: con el adapter simulando un bloqueo a mitad de corrida, la respuesta es
200 con el corte, lo guardado se conserva, no hay más pedidos y la corrida siguiente procesa solo
los pendientes. Con WhoScored bloqueando, el catálogo y la sincronización de Football-Data
responden igual.

**Cobertura de los escenarios**: 1, 2 y 6 → T096; 3 → T096, T111 y T134; 4 → T111 y T134; 5 →
T112; 7 → T135; 8 → T070, T111 y T135; 9 → T136.

### Tests de la Historia 4

- [X] T134 [US4] Agregar métodos a `StatsSyncControllerIT` en backend/src/test/java/ar/edu/unq/desapp/futbolmarket/e2e/StatsSyncControllerIT.java (depende de T117):
  - con el adapter lanzando `SourceBlockedException` en el segundo partido, una completa responde 200 con `cut` (`page: MATCH`, su `whoscoredId`, su `path`, `league: PREMIER` y el id de Football-Data en `match`), la Premier `CUT` y las demás `NOT_REACHED`; el primer partido quedó guardado y el adapter no recibió ningún otro pedido (HU4, escenario 3);
  - la siguiente completa, con el adapter respondiendo, procesa solo el partido pendiente sin volver a pedir el guardado (escenario 4; SC-009)
- [X] T135 [US4] Agregar más métodos a `StatsSyncControllerIT` en backend/src/test/java/ar/edu/unq/desapp/futbolmarket/e2e/StatsSyncControllerIT.java (depende de T134):
  - con el adapter de WhoScored lanzando `SourceBlockedException` en cada pedido, `GET /players` y `POST /players/sync` (con el adapter de Football-Data mockeado) responden igual que siempre (escenario 7; FR-046 y SC-016);
  - un partido que el adapter devuelve sin datos se omite con `NOT_FINISHED_IN_SOURCE`, sin corte, y se procesa en la corrida siguiente (escenario 8)
- [X] T136 [US4] Agregar un método a `SyncChainIT` en backend/src/test/java/ar/edu/unq/desapp/futbolmarket/service/sync/SyncChainIT.java: con el adapter de WhoScored bloqueando desde el primer pedido, la cadena termina sin propagar nada, guarda la última completa de Football-Data y no la de WhoScored, y los partidos quedan pendientes (HU4, escenario 9) (depende de T131)
- [X] T137 [US4] Correr `./gradlew build` en backend/ y verificar `BUILD SUCCESSFUL`

**Checkpoint**: las cuatro historias funcionan.

---

## Fase 7: Cierre y definición de terminado

**Propósito**: el paso 9 del plan: el build, la aplicación contra los sitios reales, el primer
snapshot y la documentación. La prueba con pocas páginas (quickstart.md 3) va antes de la primera
puesta al día completa, porque necesita una base sin estadísticas de la Premier.

- [X] T138 Correr `./gradlew clean build` en backend/ y verificar `BUILD SUCCESSFUL`. Revisar con `git fetch origin` y `git diff --stat origin/develop...HEAD -- backend/src/test/` que los únicos tests existentes modificados son `StartupSyncTest`, `SyncSchedulerTest` y `SyncServiceTest` (research D26). `JwtServiceTest.unTokenConLaFirmaAlteradaSeRechazaComoInvalido`, que era intermitente, ya está corregido
- [X] T139 Seguir quickstart.md sección 3 sobre la base local de la 005, arrancando con `FUTBOLMARKET_FOOTBALLDATA_SYNC_ONSTARTUP=false`:
  - `ddl-auto: update` agrega las columnas, las tablas y los índices sin borrar la base (sección 2.1);
  - marcar los partidos de la Premier menos los dos últimos, disparar `?league=PREMIER` y verificar `matchesScraped: 2`, los 20 equipos cruzados y las métricas contra la página del partido en el navegador;
  - **deshacer la marca artificial** con el `UPDATE` del paso 5 antes de seguir
- [X] T140 Seguir quickstart.md 2.2 con el token y el administrador exportados en la terminal (nunca escritos en un archivo): la aplicación termina de arrancar antes de que empiece la cadena; `GET /players` responde mientras corre; los dos informes quedan en el registro con `CATCH_UP`; la de WhoScored sin snapshot tarda menos de 60 minutos (SC-005); con su informe, `filas de PLAYER_MATCH_STATS / (filas + jugadores sin cruzar)` es al menos 0,95 (SC-002); y con su duración y la cantidad de líneas `Pedido a WhoScored`, `duración / pedidos × 48` da menos de 15 minutos (SC-004)
- [X] T141 Seguir quickstart.md 2.3, 2.4 y 2.5: con las dos fuentes al día no corre nada; sin token se saltea Football-Data y WhoScored corre; con solo WhoScored vencida corre solo WhoScored (HU3, escenarios 3, 4 y 8)
- [X] T142 Seguir quickstart.md 5.1 y 5.2 con la aplicación detenida: `./gradlew exportStatsSnapshot` genera backend/snapshot/whoscored-stats.jsonl con el encabezado del contrato y sin datos de usuarios (el `grep` da 0); una segunda exportación deja el diff vacío. Commitear el primer snapshot (paso 9 del plan)
- [X] T143 Seguir quickstart.md 5.3: con la base apartada (`mv data data-respaldo`, que `.gitignore` ignora desde T003), arrancar y verificar que WhoScored carga casi todo desde el snapshot, sin pedir esas páginas, en menos de 15 minutos después de Football-Data (SC-006); probar también el snapshot renombrado (WARN de faltante) y volver a la base original
- [X] T144 Seguir los escenarios HTTP de quickstart.md 4.1 a 4.3 con el administrador configurado y verificar cada resultado esperado
- [X] T145 Seguir quickstart.md sección 6 en `http://localhost:8080/swagger-ui.html`: la etiqueta Sincronización con `POST /players/sync` y `POST /matches/stats/sync`, los dos con candado; el parámetro `league` y las respuestas 200, 400, 401, 403 y 409; ningún endpoint devuelve estadísticas (FR-009); 200 con el administrador y 403 con un usuario común
- [X] T146 Seguir quickstart.md sección 7 en `http://localhost:8080/h2-console`: 96 equipos cruzados (SC-003a), los terminados sin estadísticas son solo los omitidos (SC-001), las filas del 1983600 coinciden con lo esperado, el muestreo de cruces no encuentra ninguno mal cruzado (SC-003) y ninguna cantidad baja después de otra sincronización (SC-008 y SC-010)
- [X] T147 Actualizar README.md en la raíz del repositorio:
  - en "Cómo levantarlo": la sincronización de WhoScored (la cadena del lunes, la puesta al día al arrancar y `POST /matches/stats/sync` con el administrador), el snapshot y `./gradlew exportStatsSnapshot`; los enlaces a contracts/configuration.md y quickstart.md;
  - que la base local de la 005 **no** se borra;
  - en "Estructura", el contexto `performance` y `adapter/whoscored/`; en "Tests", que la suite no consulta WhoScored

---

## Fase 8: Ajuste del cruce de jugadores (SC-002)

**Propósito**: T140 midió SC-002 contra el sitio real en 87,3%, por debajo del 95%. 1176
apariciones de 301 jugadores no se cruzaron porque Football-Data y WhoScored escriben distinto su
nombre, aunque estaban en el equipo correcto del catálogo. Además, WhoScored informa la edad del
día en que se sirve la página, no la del partido. Esta fase implementa FR-019b y FR-019c (spec,
Session 2026-10-15; research D14; data-model "Cruces").

**Orden**: T140 se marca con la medición de T156. T142, T143 y T146 van después de T156, para
que el primer snapshot y las verificaciones de la base tengan los cruces nuevos. T141, T144 y
T145 no dependen de esta fase.

**Tests**: las clases de test de esta fase son nuevas o de esta feature, y solo suman métodos.
`PlayerCrossingTest` no cambia y tiene que seguir en verde con la tolerancia de edad. Si un test
existente falla, se arregla el código.

### Implementación y tests del ajuste

- [X] T148 [P] En backend/src/main/java/ar/edu/unq/desapp/futbolmarket/modelo/sync/NormalizedName.java agregar `List<String> words()`, `String lastWord()` y `String compact()` (data-model.md, "Nombres"). Un nombre vacío da una lista vacía, una última palabra vacía y una forma compacta vacía. Agregar métodos a `NormalizedNameTest` en backend/src/test/java/ar/edu/unq/desapp/futbolmarket/modelo/sync/NormalizedNameTest.java: `"N'Diaye"` da las palabras `n` y `diaye`, la última palabra `diaye` y la forma compacta `ndiaye`; `"Lee Kang-In"` da `lee`, `kang` e `in`; el nombre vacío
- [X] T149 [P] En backend/src/main/java/ar/edu/unq/desapp/futbolmarket/modelo/sync/CatalogPlayers.java agregar `List<Player> inTeam(Team)`: los jugadores cuyo equipo actual es ese, activos o inactivos, con los cruces que confirmó `confirm` (data-model.md). Agregar métodos a `CatalogPlayersTest` en backend/src/test/java/ar/edu/unq/desapp/futbolmarket/modelo/sync/CatalogPlayersTest.java: trae solo los del equipo; después de `confirm`, el cruzado viene con su id de WhoScored
- [X] T150 Crear el enum `NameMatchLevel` en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/modelo/sync/NameMatchLevel.java (research D14; data-model.md) (depende de T148):
  - los seis niveles en orden: `SAME_WORDS`, `CONTAINED_WORDS`, `SAME_SURNAME`, `SAME_COMPACT`, `SIMILAR_SURNAME` y `WORD_PREFIX`, cada uno con `boolean matches(NormalizedName reported, NormalizedName catalog)`, simétrico;
  - las constantes públicas `SIMILAR_SURNAME_MIN_LENGTH = 5`, `SIMILAR_SURNAME_MAX_CHANGES = 2` y `WORD_PREFIX_MIN_LENGTH = 4`, y la privada `MAX_PERMUTED_WORDS = 4`: con más palabras, `SAME_COMPACT` compara la forma compacta solo en el orden original;
  - la distancia de Levenshtein y las permutaciones de palabras como métodos privados, sin dependencias nuevas;
  - escribir `NameMatchLevelTest` (clase nueva) en backend/src/test/java/ar/edu/unq/desapp/futbolmarket/modelo/sync/NameMatchLevelTest.java: por nivel, el ejemplo real de research D14 coincide y uno parecido no (por ejemplo, en `SAME_SURNAME`, `Álex Baena` y `Alejandro Baena` sí, y `Álex Baena` y `Álex Remiro` no); en `SIMILAR_SURNAME`, tres cambios o un apellido de cuatro letras no coinciden; en `WORD_PREFIX`, una palabra de tres letras no; y cada nivel da lo mismo con los nombres invertidos
- [X] T151 Ajustar `PlayerCrossing` en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/modelo/sync/PlayerCrossing.java (FR-019b y FR-019c; research D14) (depende de T149 y T150):
  - la misma edad acepta que la que surge de la fecha de nacimiento el día del partido sea la informada o una menos, en el desempate entre homónimos, en FR-019a y en los niveles. Sin fecha de nacimiento, o sin edad en la aparición, nunca es de la misma edad;
  - si FR-019 y FR-019a no encuentran a nadie, se prueban los niveles de `NameMatchLevel` en orden sobre `catalog.inTeam(equipo del lado)`, solo con los candidatos elegibles (FR-022) y de la misma edad. Con uno, se cruza; con más de uno, `AMBIGUOUS` sin bajar de nivel; si ningún nivel encuentra a nadie, `NOT_FOUND`;
  - crear el record `NameCross(String reportedName, Player player, NameMatchLevel level)` en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/modelo/sync/NameCross.java y agregar `List<NameCross> nameCrosses()`, con el jugador ya cruzado;
  - actualizar el Javadoc de la clase;
  - `PlayerCrossingTest` no cambia y tiene que seguir en verde;
  - escribir `PlayerCrossingNameLevelsTest` (clase nueva) en backend/src/test/java/ar/edu/unq/desapp/futbolmarket/modelo/sync/PlayerCrossingNameLevelsTest.java, con un catálogo propio armado con los ejemplos reales de research D14:
    - un caso por nivel que cruza y deja su `NameCross` con ese nivel (HU1, escenario 21);
    - con la edad distinta, o sin fecha de nacimiento, no cruza y queda `NOT_FOUND`;
    - dos candidatos de la misma edad en un nivel dan `AMBIGUOUS`, aunque un nivel siguiente tenga uno solo (HU1, escenario 22);
    - el nombre exacto en el equipo le gana a un nivel, y un transferido que se encuentra por FR-019a no se cruza con un parecido de su equipo viejo;
    - un jugador ya cruzado con otro id, o ya elegido en el mismo partido, no es candidato de ningún nivel;
    - un jugador que cumplió años después del partido se reconoce en el desempate, en FR-019a y en los niveles (HU1, escenario 23)
- [X] T152 En backend/src/main/java/ar/edu/unq/desapp/futbolmarket/modelo/sync/LeagueStatsSync.java guardar los `NameCross` que deja `prepare` para cada partido y agregar `List<NameCross> nameCrossesOf(Match)` (data-model.md) (depende de T151). Agregar métodos a `LeagueStatsSyncTest` en backend/src/test/java/ar/edu/unq/desapp/futbolmarket/modelo/sync/LeagueStatsSyncTest.java: un partido preparado con un jugador cruzado por nombre trae su `NameCross`; uno sin cruces por nombre, la lista vacía
- [X] T153 En backend/src/main/java/ar/edu/unq/desapp/futbolmarket/service/sync/StatsSyncService.java, después de guardar un partido scrapeado, dejar un INFO por cada `NameCross` de `sync.nameCrossesOf(match)`: `{liga}: jugador cruzado por nombre (nivel {número}, {NIVEL}): '{nombre de WhoScored}' → '{nombre del catálogo}' ({equipo}), partido {id de Football-Data}.` Si el partido no se pudo guardar, no se escribe ninguno (FR-019b) (depende de T152). Agregar un método a `StatsSyncControllerIT` en backend/src/test/java/ar/edu/unq/desapp/futbolmarket/e2e/StatsSyncControllerIT.java: un jugador que WhoScored informa con otro nombre (`Tarkowski` para `James Tarkowski`) queda cruzado y con su fila, y el registro tiene su línea con el nivel 2
- [X] T154 [P] En specs/006-scraping-whoscored/quickstart.md: en la sección 2.2, cómo calcular SC-002 con el informe y que las líneas `jugador cruzado por nombre` muestran los cruces de los niveles; en la sección 7, sumar al muestreo de SC-003 diez cruces por nombre de los niveles 4 a 6, elegidos de esas líneas
- [X] T155 Correr `./gradlew build` en backend/ y verificar `BUILD SUCCESSFUL`

### Confirmación por la fecha de nacimiento (FR-019d)

La primera medición de T156 (2026-10-15) dio SC-002 en 97,5%, pero de doce cruces dudosos
verificados contra la página de cada jugador en WhoScored, uno estaba mal: `Mathis Saka` con
`Mathis Amougou`, cruzados por el nivel 6 solo por el nombre de pila (spec, Session 2026-10-15;
research D14 y D28). T157 a T163 cierran ese hueco; después se repite T156.

- [X] T157 [P] En backend/src/main/java/ar/edu/unq/desapp/futbolmarket/modelo/sync/NameMatchLevel.java, `WORD_PREFIX` deja de coincidir cuando los dos nombres tienen más de una palabra y la única coincidencia es entre sus primeras palabras (data-model.md); un nombre de una sola palabra sigue en la regla general. En backend/src/test/java/ar/edu/unq/desapp/futbolmarket/modelo/sync/NameMatchLevelTest.java, **con el sí del equipo** (es un test de T150), la fila `WORD_PREFIX | Abdul Fatawu | Abdul Issahaku` pasa de las que coinciden a las que no, y se suman `WORD_PREFIX | Kiko Femenía | Femenía Far` a las que coinciden y `WORD_PREFIX | Mathis Saka | Mathis Amougou` a las que no
- [X] T158 Crear el record `BirthDateCross(String reportedName, Player player, LocalDate birthDate)` en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/modelo/sync/BirthDateCross.java y ajustar `PlayerCrossing` (FR-019d; research D14; data-model.md) (depende de T157):
  - el constructor nuevo `(CatalogPlayers, Match, ReportedMatchStats, Map<String, LocalDate> birthDates)`; el de tres argumentos pasa un mapa vacío, así ningún test existente cambia;
  - si después de FR-019 a FR-019b el jugador queda `NOT_FOUND` o `AMBIGUOUS` y el mapa trae su fecha: el único elegible de su equipo con exactamente esa fecha de nacimiento, o, si en el equipo no hay ninguno, el único elegible de todo el catálogo con esa fecha y el mismo nombre normalizado. Con uno, se cruza y deja su `BirthDateCross`; si no, queda con su motivo;
  - `List<BirthDateCross> birthDateCrosses()` y `List<String> needsBirthDate()`: los ids de WhoScored de los que quedaron sin cruzar y no tenían fecha en el mapa;
  - en backend/src/test/java/ar/edu/unq/desapp/futbolmarket/modelo/sync/PlayerCrossingNameLevelsTest.java, **con el sí del equipo** (es un test de T151), el ejemplo del nivel 6 pasa a ser `Kiko Femenía` con `Femenía Far`;
  - escribir `PlayerCrossingBirthDateTest` (clase nueva) en backend/src/test/java/ar/edu/unq/desapp/futbolmarket/modelo/sync/PlayerCrossingBirthDateTest.java:
    - `Moustapha Mbow` con la fecha de `Mamadou Mbow` se cruza con él y deja su `BirthDateCross` (HU1, escenario 24);
    - `Mathis Saka` y `Mathis Amougou`, del mismo equipo y la misma edad, con fechas distintas, no se cruzan (HU1, escenario 25);
    - sin fecha en el mapa, el jugador sin cruzar figura en `needsBirthDate()` y el cruzado por nombre no;
    - dos elegibles del equipo con la misma fecha no se cruzan; un jugador del catálogo sin fecha de nacimiento nunca es candidato;
    - dos homónimos ambiguos por edad se resuelven por la fecha; un transferido se encuentra en todo el catálogo por fecha y nombre;
    - un jugador ya cruzado o ya elegido en el partido no es candidato
- [X] T159 En backend/src/main/java/ar/edu/unq/desapp/futbolmarket/modelo/sync/LeagueStatsSync.java agregar `birthDatesNeeded(Match, Optional<ReportedMatchStats>, CatalogPlayers)`, que no cambia nada de la liga; `prepare` con el mapa de fechas, con la versión de tres argumentos pasando uno vacío; y `birthDateCrossesOf(Match)` (data-model.md) (depende de T158). Agregar métodos a `LeagueStatsSyncTest`: `birthDatesNeeded` devuelve el id del jugador desconocido y nada para un partido que se omitiría; después de `birthDatesNeeded`, el resultado de la liga no cambió; `prepare` con su fecha lo cruza y deja el `BirthDateCross`
- [X] T160 [P] En el adapter (research D28; contracts/whoscored-pages.md, "Página de un jugador"):
  - `WhoScoredPages.playerPath(String playerId)`, que da `/players/{id}/show`;
  - `WhoScoredClient.fetchIfAvailable(String path, Function<String, Optional<T>> extractor)`: el mismo ritmo y el mismo reintento que `fetch`, pero después del segundo rechazo un WARN `No se pudo leer la página {ruta} de WhoScored: se sigue sin ella.` y vacío, sin lanzar nada;
  - crear `PlayerProfileExtractor` (`@Component`) en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/adapter/whoscored/PlayerProfileExtractor.java: vacío (rechazo) sin ningún `info-label`; si no, la fecha del `<i>` que sigue a `Age: `, o ninguna si falta o no se puede leer;
  - `WhoScoredAdapter.fetchPlayerBirthDate(String playerWhoscoredId)`: `Optional<LocalDate>`, vacío si la página no llegó o no trae la fecha;
  - crear la fixture backend/src/test/resources/whoscored/player-profile.html con el bloque del contrato, y escribir `PlayerProfileExtractorTest` (clase nueva): lee `20-01-2006`; sin `Age:` no hay fecha y no es rechazo; sin `info-label` es rechazo;
  - agregar métodos a `WhoScoredAdapterTest`: la fecha de una página de jugador; con dos rechazos seguidos devuelve vacío, deja los dos WARN y no lanza; respeta la pausa de 4 s; una página sin la edad no se reintenta
- [X] T161 En backend/src/main/java/ar/edu/unq/desapp/futbolmarket/service/sync/StatsSyncService.java, al scrapear un partido: pedir `sync.birthDatesNeeded`, pedir con `adapter.fetchPlayerBirthDate` las fechas que no estén en un mapa de la corrida (que guarda también las vacías, así cada jugador se pide una sola vez), y pasar las que llegaron a `prepare`. Después de guardar el partido, un INFO por cada `BirthDateCross`: `{liga}: jugador cruzado por fecha de nacimiento ({dd/MM/yyyy}): '{nombre de WhoScored}' → '{nombre del catálogo}' ({equipo}), partido {id de Football-Data}.` (FR-019d) (depende de T159 y T160). Agregar métodos a `StatsSyncServiceTest` (clase de esta feature): solo se piden las páginas de los que quedarían sin cruzar; un mismo jugador en dos partidos de la corrida se pide una sola vez; una página de jugador vacía no corta la corrida. Agregar un método a `StatsSyncControllerIT`: un jugador con otro nombre y la fecha de uno del catálogo queda cruzado, con su fila y su línea en el registro
- [X] T162 [P] En specs/006-scraping-whoscored/quickstart.md: en la sección 2.2, las líneas `jugador cruzado por fecha de nacimiento` y las páginas de jugador entre los pedidos; en la sección 7, sumar al muestreo de SC-003 cinco cruces por fecha de nacimiento
- [X] T163 Correr `./gradlew build` en backend/ y verificar `BUILD SUCCESSFUL`

### Candidatos de otro equipo, transferidos con otro nombre y nombre completo (FR-019a y FR-019d)

Antes de repetir T156 se revisaron los 97 jugadores que la primera medición dejó sin cruzar
(spec, Session 2026-10-15; research D14 y D28). 83 no están en el plantel de Football-Data, pero
aparecieron tres huecos de la regla: el `Vitinha` del Genoa se cruzó por nombre y edad con el del
PSG del catálogo, que después quedó sin cruzar en seis partidos; `José María Giménez`, transferido
y con otro nombre en el catálogo, no se cruzó aunque tiene la misma fecha; y `Rafita` tiene en el
catálogo una fecha de relleno (01/01/2004). T164 a T172 los resuelven; después se repite T156.

**Tests que cambian, con el sí del equipo**: el cruce por nombre y edad en otro equipo deja de
cruzar sin la fecha, y la página de jugador pasa a traer también el nombre completo. Cambian solo
estos métodos, y solo como dice su tarea:

- `PlayerCrossingTest.elTransferidoSeEncuentraEnTodoElCatalogoPorNombreYEdad` (T167);
- `PlayerCrossingNameLevelsTest.unTransferidoSeEncuentraPorNombreExactoAntesQueUnParecidoDeSuEquipoViejo` y `enTodoElCatalogoSeReconoceAlTransferidoQueCumplioAniosDespuesDelPartido` (T167);
- el método privado `crossing` de `PlayerCrossingBirthDateTest` (T167);
- `LeagueStatsSyncTest.prepareConLaFechaDeNacimientoCruzaAlJugadorYDejaElCruce` (T168);
- los tres métodos de `PlayerProfileExtractorTest` que esperan un `PlayerProfileDto` y los cuatro de `WhoScoredAdapterTest` que piden `fetchPlayerBirthDate` (T169);
- `StatsSyncServiceTest.soloSePidenLasPaginasDeLosJugadoresQueQuedanSinCruzar`, `unMismoJugadorEnDosPartidosDeLaCorridaSePideUnaSolaVez` y `unaPaginaDeJugadorVaciaNoCortaLaCorrida`, y `StatsSyncControllerIT.unJugadorConOtroNombreYLaFechaDeUnoDelCatalogoQuedaCruzadoYElRegistroLoDice` (T170).

Cualquier otro test existente que falle se arregla en el código.

- [X] T164 [P] En backend/src/main/java/ar/edu/unq/desapp/futbolmarket/modelo/sync/NormalizedName.java agregar `boolean isWithin(NormalizedName fullName)` (FR-019d; data-model.md, "Nombres"): cada palabra de este nombre es una palabra distinta de `fullName`, o el comienzo de una si tiene al menos `NameMatchLevel.WORD_PREFIX_MIN_LENGTH` letras; primero la palabra igual y después una que empiece con ella; un nombre vacío nunca está dentro. Agregar métodos a `NormalizedNameTest`: `Rafa Garrido` está dentro de `Rafael Garrido Hierro`, `Diogo Sousa` dentro de `Diogo Lobão de Sousa` y `Manu Hernando` dentro de `José Manuel Hernando Riol`; `Pablo García` no está dentro de `Mauro Echegoyen García`; una palabra de tres letras solo vale entera (`Edu` no está dentro de `Eduardo Camavinga`); una palabra del nombre completo no se usa dos veces (`Garrido Garrido` no está dentro de `Rafael Garrido`); un nombre vacío no está dentro de ninguno
- [X] T165 [P] En backend/src/main/java/ar/edu/unq/desapp/futbolmarket/modelo/sync/CatalogPlayers.java agregar `List<Player> bornOn(LocalDate)`: los jugadores con esa fecha de nacimiento, de cualquier equipo, activos o inactivos, con los cruces que confirmó `confirm` (data-model.md). Agregar métodos a `CatalogPlayersTest`: trae los de esa fecha de dos equipos distintos y un inactivo; nunca trae a uno sin fecha; después de `confirm`, el jugador viene con su id de WhoScored
- [X] T166 [P] Crear los records `ReportedPlayerProfile(LocalDate birthDate, String fullName)` en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/modelo/sync/ReportedPlayerProfile.java y `FullNameCross(String reportedName, String fullName, Player player)` en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/modelo/sync/FullNameCross.java, con su Javadoc (data-model.md)
- [X] T167 Ajustar `PlayerCrossing` en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/modelo/sync/PlayerCrossing.java (FR-019a y FR-019d; research D14; data-model.md) (depende de T164, T165 y T166):
  - el constructor de cuatro argumentos recibe `Map<String, ReportedPlayerProfile> profiles` en lugar del mapa de fechas; el de tres argumentos sigue y pasa un mapa vacío;
  - FR-019a: si hay candidatos en otro equipo por nombre y edad, no se cruza ni se prueban los niveles: queda `NOT_CONFIRMED` con uno y `AMBIGUOUS` con más, y entra en `needsBirthDate()` si no tiene página;
  - FR-019d, con la fecha de la página y entre los elegibles: (1) los de su equipo con esa fecha; (2) si no hay ninguno, `catalog.bornOn(fecha)` de otros equipos cuyo nombre coincide con el de WhoScored por `SAME_WORDS`, `CONTAINED_WORDS` o `SAME_SURNAME`, o está dentro del nombre completo; (3) si tampoco, los de su equipo nacidos el mismo año cuyo nombre está dentro del nombre completo. Con uno se cruza; con más de uno queda su motivo sin pasar al paso siguiente. Los pasos 1 y 2 dejan un `BirthDateCross` y el 3, un `FullNameCross`;
  - agregar `List<FullNameCross> fullNameCrosses()` y actualizar el Javadoc de la clase;
  - **con el sí del equipo**: en backend/src/test/java/ar/edu/unq/desapp/futbolmarket/modelo/sync/PlayerCrossingTest.java, `elTransferidoSeEncuentraEnTodoElCatalogoPorNombreYEdad` pasa a crear la `PlayerCrossing` con la página de Wissa con su fecha, y sigue esperando el cruce; en backend/src/test/java/ar/edu/unq/desapp/futbolmarket/modelo/sync/PlayerCrossingNameLevelsTest.java, `unTransferidoSeEncuentraPorNombreExactoAntesQueUnParecidoDeSuEquipoViejo` pasa la página de Yoane Wissa con su fecha y sigue esperando a Yoane, y `enTodoElCatalogoSeReconoceAlTransferidoQueCumplioAniosDespuesDelPartido` espera, sin página, que quede `NOT_CONFIRMED` (se lo reconoce como candidato) en lugar del cruce; en backend/src/test/java/ar/edu/unq/desapp/futbolmarket/modelo/sync/PlayerCrossingBirthDateTest.java, el método privado `crossing` arma una `ReportedPlayerProfile` sin nombre completo por cada fecha, y sus métodos de test no cambian;
  - agregar métodos a `PlayerCrossingBirthDateTest` (clase de esta feature):
    - dos `Vitinha` de la misma edad, uno del catálogo en otro equipo: sin página, el del partido queda `NOT_CONFIRMED`, sin cruzar y en `needsBirthDate()`; con una página de otra fecha, tampoco se cruza; y nunca se cruza con un compañero de su edad con el mismo apellido (HU1, escenario 27);
    - un transferido con la página de su misma fecha se cruza y deja su `BirthDateCross` (HU1, escenario 9);
    - `José María Giménez` con la fecha de `José Giménez`, de otro equipo, se cruza; con esa fecha y un nombre sin relación, no (HU1, escenario 28);
    - `Rafita` con el nombre completo `Rafael Garrido Hierro` y el 09/11/2004 se cruza con `Rafa Garrido` de su equipo, nacido el 01/01/2004, y deja su `FullNameCross` (HU1, escenario 29); con otro año, o sin nombre completo en la página, no; con dos compañeros del mismo año dentro del nombre completo, no;
    - el paso 1 le gana al 2: con alguien de su equipo con esa fecha, no se cruza con el de otro equipo; con dos de otros equipos en el paso 2, no se cruza ni pasa al paso 3
- [X] T168 En backend/src/main/java/ar/edu/unq/desapp/futbolmarket/modelo/sync/LeagueStatsSync.java, `prepare` recibe el mapa de `ReportedPlayerProfile` (la versión de tres argumentos sigue y pasa uno vacío) y se agrega `fullNameCrossesOf(Match)` (data-model.md) (depende de T167). **Con el sí del equipo**, en backend/src/test/java/ar/edu/unq/desapp/futbolmarket/modelo/sync/LeagueStatsSyncTest.java, `prepareConLaFechaDeNacimientoCruzaAlJugadorYDejaElCruce` pasa la fecha de Barry dentro de una `ReportedPlayerProfile` sin nombre completo. Agregar un método a `LeagueStatsSyncTest`: `prepare` con una página con el nombre completo deja el `FullNameCross` del partido
- [X] T169 [P] En el adapter (research D28; contracts/whoscored-pages.md, "Página de un jugador") (depende de T166):
  - `PlayerProfileDto` pasa a `(LocalDate birthDate, String fullName)`;
  - `PlayerProfileExtractor` lee además el texto que sigue a `Full Name: ` hasta el `</div>`, sin los espacios de los bordes, con los espacios seguidos reducidos a uno y las entidades decodificadas con `HtmlUtils.htmlUnescape`; sin la etiqueta, o con el texto vacío, el nombre completo es nulo y no es un rechazo;
  - `WhoScoredAdapter.fetchPlayerProfile(String playerWhoscoredId)` reemplaza a `fetchPlayerBirthDate`: `Optional<ReportedPlayerProfile>`, vacío si la página no llegó;
  - **con el sí del equipo**: en backend/src/test/java/ar/edu/unq/desapp/futbolmarket/adapter/whoscored/PlayerProfileExtractorTest.java, los tres métodos que esperan un `PlayerProfileDto` lo esperan con el nombre completo (`Diogo Lobão de Sousa` en la fixture, nulo en los demás); en backend/src/test/java/ar/edu/unq/desapp/futbolmarket/adapter/whoscored/WhoScoredAdapterTest.java, los cuatro métodos de la página de jugador piden `fetchPlayerProfile` y esperan la `ReportedPlayerProfile`, con lo mismo que verificaban;
  - agregar métodos a `PlayerProfileExtractorTest`: una página sin `Full Name` trae la fecha y ningún nombre completo; las entidades se decodifican (`N&#39;Diaye` da `N'Diaye`) y los espacios seguidos se reducen. Agregar un método a `WhoScoredAdapterTest`: la página de la fixture da la fecha y el nombre completo
- [X] T170 En backend/src/main/java/ar/edu/unq/desapp/futbolmarket/service/sync/StatsSyncService.java (FR-019d) (depende de T168 y T169):
  - el mapa de la corrida guarda `Optional<ReportedPlayerProfile>` de `adapter.fetchPlayerProfile`, también los vacíos, y `prepare` recibe las páginas que llegaron;
  - un `BirthDateCross` cuyo jugador no es de ninguno de los dos equipos del partido deja `{liga}: jugador cruzado por fecha de nacimiento en otro equipo ({dd/MM/yyyy}): '{nombre de WhoScored}' → '{nombre del catálogo}' ({equipo del catálogo}), partido {id de Football-Data}.`; los demás, la línea de T161;
  - un INFO por cada `FullNameCross`: `{liga}: jugador cruzado por nombre completo y año de nacimiento ({yyyy}): '{nombre de WhoScored}' ('{nombre completo}') → '{nombre del catálogo}' ({equipo}), partido {id de Football-Data}.`;
  - **con el sí del equipo**: en backend/src/test/java/ar/edu/unq/desapp/futbolmarket/service/sync/StatsSyncServiceTest.java, `soloSePidenLasPaginasDeLosJugadoresQueQuedanSinCruzar`, `unMismoJugadorEnDosPartidosDeLaCorridaSePideUnaSolaVez` y `unaPaginaDeJugadorVaciaNoCortaLaCorrida` piden `fetchPlayerProfile` en lugar de `fetchPlayerBirthDate`, con la fecha dentro de una `ReportedPlayerProfile`; en backend/src/test/java/ar/edu/unq/desapp/futbolmarket/e2e/StatsSyncControllerIT.java, `unJugadorConOtroNombreYLaFechaDeUnoDelCatalogoQuedaCruzadoYElRegistroLoDice`, igual;
  - agregar métodos a `StatsSyncServiceTest`: un candidato de otro equipo por nombre y edad pide su página aunque los niveles lo cruzarían; un transferido cruzado por la fecha deja la línea `en otro equipo`. Agregar un método a `StatsSyncControllerIT`: un jugador con el mismo nombre y la misma edad que uno del catálogo de otro equipo, con otra fecha en su página, figura en el informe como `NOT_CONFIRMED` y el del catálogo sigue sin cruzar
- [X] T171 [P] En specs/006-scraping-whoscored/quickstart.md: en la sección 2.2, las líneas `jugador cruzado por fecha de nacimiento en otro equipo` y `jugador cruzado por nombre completo y año de nacimiento`; en la sección 7, sumar al muestreo de SC-003 todas las líneas de esos dos tipos
- [X] T172 Correr `./gradlew build` en backend/ y verificar `BUILD SUCCESSFUL`

### Relación de nombre en el paso 1 de FR-019d

La segunda medición de T156 (2026-10-15) dio SC-002 en 97,8%, pero cruzó a `Riccardo Ciervo`
con `Ignace Van Der Brempt`, un compañero del Sassuolo nacido el mismo día, antes de buscarlo en
el Cagliari, donde lo tiene el catálogo (spec, Session 2026-10-15; research D14). El paso 1 pasa a exigir alguna relación de nombre.
También se corrigió el caso de `Vitinha`: el del Genoa está en el catálogo como `Vitor Oliveira`.
T173 a T176 cierran ese hueco; después se repite T156.

**Tests que cambian, con el sí del equipo**: un cruce por la fecha en el equipo sin ninguna
relación de nombre deja de hacerse. Cambian solo estos métodos, y solo como dice su tarea:

- `PlayerCrossingBirthDateTest.unApodoSinNingunaPalabraEnComunSeCruzaPorLaFechaDeNacimiento`, `unJugadorYaElegidoEnElPartidoNoEsCandidato` y `laFechaEnSuEquipoLeGanaALaFechaEnOtroEquipo` (T174);
- `LeagueStatsSyncTest.prepareConLaFechaDeNacimientoCruzaAlJugadorYDejaElCruce`, `StatsSyncServiceTest.soloSePidenLasPaginasDeLosJugadoresQueQuedanSinCruzar` y `StatsSyncControllerIT.unJugadorConOtroNombreYLaFechaDeUnoDelCatalogoQuedaCruzadoYElRegistroLoDice` (T175).

Cualquier otro test existente que falle se arregla en el código.

- [X] T173 [P] En backend/src/main/java/ar/edu/unq/desapp/futbolmarket/modelo/sync/NormalizedName.java agregar `boolean sharesWordWith(NormalizedName other)`: los dos nombres tienen una palabra igual de al menos `NameMatchLevel.WORD_PREFIX_MIN_LENGTH` letras (data-model.md, "Nombres"). Agregar métodos a `NormalizedNameTest`: `Abdul Fatawu` y `Abdul Issahaku` comparten una palabra; `Mousa Al Tamari` y `Musa Al Taamari` no, porque `al` es corta; `Riccardo Ciervo` e `Ignace Van Der Brempt` no; un nombre vacío no comparte ninguna
- [X] T174 En backend/src/main/java/ar/edu/unq/desapp/futbolmarket/modelo/sync/PlayerCrossing.java, el paso 1 de FR-019d solo toma a los de su equipo con esa fecha que tienen una relación de nombre con el de WhoScored: `sharesWordWith`, `matches` de algún `NameMatchLevel` sin la condición de la edad, o el nombre del catálogo `isWithin` el nombre completo; si no queda ninguno, sigue el paso 2 (research D14; data-model.md) (depende de T173). Actualizar el Javadoc de la clase.
  - **con el sí del equipo**, en backend/src/test/java/ar/edu/unq/desapp/futbolmarket/modelo/sync/PlayerCrossingBirthDateTest.java: `unApodoSinNingunaPalabraEnComunSeCruzaPorLaFechaDeNacimiento` pasa una página con el nombre completo `Rafael Garrido Ruiz`, y sigue esperando el cruce y su `BirthDateCross`; en `unJugadorYaElegidoEnElPartidoNoEsCandidato`, el primer jugador de WhoScored pasa a llamarse `Rafael` en lugar de `Rafita`; en `laFechaEnSuEquipoLeGanaALaFechaEnOtroEquipo`, el compañero nacido el mismo día pasa a llamarse `Vitinha Costa`;
  - agregar métodos a `PlayerCrossingBirthDateTest`: un jugador que no está en el catálogo y un compañero sin ninguna relación de nombre, nacidos el mismo día, no se cruzan (`Riccardo Ciervo` e `Ignace Van Der Brempt`; HU1, escenario 30); con la misma fecha se cruzan los que solo comparten el nombre de pila (`Abdul Fatawu` y `Abdul Issahaku`), los que coinciden por un nivel sin la misma edad (`Mousa Al Tamari` y `Musa Al Taamari`) y el que está dentro del nombre completo (`Pote`, `Pedro António Pereira Gonçalves`, y `Pedro Gonçalves`)
- [X] T175 **Con el sí del equipo**, en los tests en los que `Titi` se cruzaba con Thierno Barry solo por la fecha, la página de Titi trae además el nombre completo `Thierno Barry Diallo`, y siguen esperando el mismo cruce, el mismo `BirthDateCross` y la misma línea en el registro: `LeagueStatsSyncTest.prepareConLaFechaDeNacimientoCruzaAlJugadorYDejaElCruce`, `StatsSyncServiceTest.soloSePidenLasPaginasDeLosJugadoresQueQuedanSinCruzar` y `StatsSyncControllerIT.unJugadorConOtroNombreYLaFechaDeUnoDelCatalogoQuedaCruzadoYElRegistroLoDice` (depende de T174)
- [X] T176 Correr `./gradlew build` en backend/ y verificar `BUILD SUCCESSFUL`

### Medición final

- [X] T156 Volver a medir SC-002 contra el sitio real sobre la base local (depende de T176):
  - con la aplicación detenida, borrar las filas de `PLAYER_MATCH_STATS`, desmarcar todos los partidos, borrar la fila `WHOSCORED` de `LAST_FULL_SYNCS` y **borrar los ids de WhoScored de los jugadores**, así cada cruce sale de la regla nueva. Los cruces de equipos y partidos se conservan;
  - arrancar con el registro en `build/`: con la de Football-Data al día, corre solo la puesta al día de WhoScored (quickstart.md 2.5), con las páginas de jugador de FR-019d;
  - mientras corre, `GET /players` responde enseguida (FR-046);
  - al terminar, `filas de PLAYER_MATCH_STATS / (filas + jugadores sin cruzar)` da al menos 0,95, lo más cerca posible de 1 (SC-002); los cruces dudosos de la primera medición, verificados en el navegador, quedan como corresponde (`Mathis Saka` sin cruzar, los otros once cruzados); el `Vitinha` del catálogo queda cruzado con el del PSG (384887) y `Vitor Oliveira` con el del Genoa (407949), `José María Giménez` queda cruzado en los partidos del Atlético y `Rafita` (508349) con `Rafa Garrido`; `Riccardo Ciervo` queda cruzado con el del Cagliari por el paso 2 e `Ignace Van Der Brempt` con el suyo, y `Youssef Enriquez` y `Yeremay Hernández` quedan sin cruzar; y una muestra de las líneas `jugador cruzado por nombre` de los niveles 4 a 6 y `jugador cruzado por fecha de nacimiento`, y todas las de `en otro equipo` y `nombre completo y año de nacimiento`, revisadas en el navegador, no tienen ningún cruce equivocado (SC-003);
  - con esta medición se marca también T140
  - resultado (2026-10-15, research D14, "Evidencia de la medición final"): 295 partidos en 2143 s, sin corte ni rechazos; SC-002 en 97,7% (9077 filas y 213 apariciones sin cruzar); ningún cruce equivocado conocido

**Checkpoint**: SC-002 se cumple contra el sitio real. Se retoma la Fase 7 desde T141.

---

## Dependencias y orden de ejecución

### Dependencias entre fases

- **Preparación (Fase 1)**: sin dependencias.
- **Fundacional (Fase 2)**: depende de la Fase 1 y **bloquea todas las historias**. Sus bloques van
  en orden (2A → 2B → 2C → 2D → 2E → 2F). Cada bloque deja el build en verde.
- **HU1 (Fase 3)**: depende solo de la Fase 2. Crea `StatsSyncControllerIT`, que amplían las
  demás historias.
- **HU2 (Fase 4)**: T120 y T121 dependen solo de la Fase 2. T122 depende además de T117.
- **HU3 (Fase 5)**: T124 a T131 dependen solo de la Fase 2. T132 depende además de T117.
- **HU4 (Fase 6)**: T134 y T135 dependen de T117 (HU1); T136 depende de T131 (HU3).
- **Cierre (Fase 7)**: depende de todas las historias. T139 va antes de T140 (la prueba con pocas
  páginas necesita una base sin estadísticas de la Premier), y T142 antes de T143.
- **Ajuste del cruce de jugadores (Fase 8)**: nace de la medición de T140 y depende de todas las
  historias. T140 se marca con T156. T142, T143 y T146 van después de T156; T141, T144 y T145
  no dependen de la Fase 8. Dentro de la fase: T148 y T149 juntos, T150 después de T148, T151
  después de T149 y T150, y T152 y T153 en orden; T154 en paralelo con cualquiera. La
  confirmación por fecha de nacimiento va después de T155: T157 y T160 juntos, T158 después de
  T157, T159 después de T158, T161 después de T159 y T160, T162 en paralelo con cualquiera, y
  T163. Los candidatos de otro equipo y el nombre completo van después de T163: T164, T165 y
  T166 juntos, T167 después de los tres, T168 después de T167, T169 después de T166, T170
  después de T168 y T169, T171 en paralelo con cualquiera, y T172. La relación de nombre del
  paso 1 va después de T172: T173, T174, T175 y T176 en orden; después, T156.

```text
Fase 1 ─► Fase 2 (2A ─► 2B ─► 2C ─► 2D ─► 2E ─► 2F) ─► HU1 ─┬─► HU2 ───────┐
                                                             ├─► HU3 ─┬─────┼─► Fase 7
                                                             └────────┴─► HU4 ┘
```

### Dentro de cada fase

- Fundacional: modelo → persistencia → tests del bloque → build.
- Historias: implementación → tests de la historia → build (sin TDD).
- Una historia termina cuando sus tests existen y pasan, y `./gradlew build` está en verde.
- Dos tareas sobre el mismo archivo nunca van en paralelo:
  - `StatsSyncControllerIT`, que crea la HU1 (T117) y amplían la HU2 (T122), la HU3 (T132) y la
    HU4 (T134 y T135);
  - `SyncChainIT`, que crea la HU3 (T131) y amplía la HU4 (T136);
  - `SyncService`, `StartupSync`, `SyncScheduler` y sus tests, que cambian una sola vez (T126, T128
    y T129).

### Oportunidades de paralelismo

- Fase 1: T001 a T005 juntos; después T006, T007, T008 y T009.
- 2A: T011 a T014 juntos; T015 a T017 juntos; T018 a T021 juntos; T022 a T025 juntos; con T026
  listo, T027 y T028.
- 2B: T030, T033, T037 y T038 juntos (con sus dependencias); T041 a T043 juntos.
- 2C: T046, T047, T049, T050 y T051 juntos; T053, T055, T056 y T058 juntos; con T065 listo, T066
  a T074 en paralelo.
- 2D: T076, T077, T079, T084 y T086 juntos; T081, T082 y T089 a T091 juntos; T092 a T095 juntos.
- 2E: T101 en paralelo con T098 a T100.
- 2F: T105, T106 y T107 juntos; T109 y T110 en paralelo con T108.
- Después de la HU1, la HU2 y la HU3 pueden avanzar en paralelo si se coordina
  `StatsSyncControllerIT`. En la HU3, T124, T125 y T130 arrancan juntos.

---

## Ejemplo de paralelismo: Fundacional 2C

```bash
# Tipos sin dependencias entre sí:
Task: "PageKind, BlockedPage y RunCut en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/modelo/sync/"
Task: "SyncSource y LastFullSync en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/modelo/sync/"
Task: "NormalizedName en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/modelo/sync/NormalizedName.java"
Task: "ReportedTeam y ReportedFixture en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/modelo/sync/"
Task: "Enums y records del informe en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/modelo/sync/"

# Con StatsFixtures listo, los unitarios del bloque:
Task: "TeamCrossingTest en backend/src/test/java/ar/edu/unq/desapp/futbolmarket/modelo/sync/TeamCrossingTest.java"
Task: "PlayerCrossingTest en backend/src/test/java/ar/edu/unq/desapp/futbolmarket/modelo/sync/PlayerCrossingTest.java"
Task: "LeagueStatsSyncTest en backend/src/test/java/ar/edu/unq/desapp/futbolmarket/modelo/sync/LeagueStatsSyncTest.java"
Task: "SnapshotLoadTest en backend/src/test/java/ar/edu/unq/desapp/futbolmarket/modelo/sync/SnapshotLoadTest.java"
```

## Ejemplo de paralelismo: Fundacional 2D

```bash
# DTO, torneos, lector de bloques, descarga y espera, en paralelo:
Task: "Records de backend/src/main/java/ar/edu/unq/desapp/futbolmarket/adapter/whoscored/dto/"
Task: "WhoScoredTournament en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/adapter/whoscored/WhoScoredTournament.java"
Task: "ScriptBlockReader en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/adapter/whoscored/ScriptBlockReader.java"
Task: "PageDownloader y DownloadedPage en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/adapter/whoscored/"
Task: "WhoScoredSleeper y WhoScoredThreadSleeper en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/adapter/whoscored/"
```

## Ejemplo de paralelismo: Historia 1

```bash
# Respuesta y seguridad, en paralelo:
Task: "StatsSyncReportResponse en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/controller/sync/dto/StatsSyncReportResponse.java"
Task: "Regla de ADMIN en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/security/SecurityConfig.java"

# Con el controller listo, los dos end to end:
Task: "StatsSyncControllerIT en backend/src/test/java/ar/edu/unq/desapp/futbolmarket/e2e/StatsSyncControllerIT.java"
Task: "StatsSyncWithoutTokenIT en backend/src/test/java/ar/edu/unq/desapp/futbolmarket/e2e/StatsSyncWithoutTokenIT.java"
```

## Ejemplo de paralelismo: Historia 3

```bash
# Con la Fase 2 terminada, sin esperar a la HU1:
Task: "SyncReport.lastFullSync y SyncReportLastFullSyncTest en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/modelo/sync/SyncReport.java"
Task: "CatchUp, CatchUpPolicy y CatchUpPolicyTest en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/modelo/sync/"
Task: "Texto del 409 en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/controller/sync/SyncController.java"
```

---

## Estrategia de implementación

### MVP primero (solo la HU1)

1. Fase 1: Preparación.
2. Fase 2: Fundacional, bloque por bloque, con el build en verde al cerrar cada uno.
3. Fase 3: HU1.
4. **Parar y validar**: `./gradlew build` en verde y quickstart.md 3 (pocas páginas) con
   `bootRun`.

El MVP deja las estadísticas guardadas con un disparo manual del administrador. Como el motor está
completo desde la Fase 2, en el MVP ya se lee el snapshot (si existe) y un bloqueo ya corta la
corrida sin perder nada; las historias siguientes lo prueban de punta a punta y suman lo que
falta.

### Entrega incremental

1. Preparación + Fundacional → motor listo.
2. HU1 → estadísticas con disparo manual (MVP).
3. HU2 → snapshot verificado de punta a punta y comando de exportación.
4. HU3 → candado común, cadena del lunes y puesta al día al arrancar.
5. HU4 → bloqueos y fallas verificados de punta a punta.
6. Cierre (Fase 7): primer snapshot y documentación.

Cada paso deja `./gradlew build` en verde.

### Riesgo de merge con la rama del compañero

El compañero puede tocar `application.yaml`, `ApplicationConfig`, `SecurityConfig` o las entidades
del catálogo. Los cambios de esta feature son agregados dentro de bloques existentes: ante un
conflicto, se conservan las dos partes. El snapshot es un archivo nuevo, y sus conflictos se
resuelven exportando otra vez (plan, "Secuencia de implementación").

---

## Notas

- [P] = otro archivo, sin dependencias pendientes dentro de su bloque.
- [USn] vincula la tarea con su historia, para la trazabilidad.
- Hacer un commit por tarea o por grupo lógico.
- Parar en cada checkpoint para validar.
- Ante una duda de forma gana la constitución, y ante una ambigüedad del diseño se pregunta antes
  de escribir código.
- **Ajustes del 2026-10-14, después de `/speckit-analyze`**, ya volcados en el spec, el plan,
  research, data-model, los contratos y el quickstart (plan, "Decisiones posteriores al análisis"):
  - orden: la lectura del snapshot (paso 6) se adelanta al bloque 2E para que `StatsSyncService`
    nazca con su forma final, y el disparo manual (paso 8) va antes que el coordinador (paso 7)
    porque es el MVP;
  - con el "sí" de Lucas, se renombran tres métodos de `StartupSyncTest` y `SyncSchedulerTest` y
    se borra `SyncSchedulerTest.sinTokenNoLlamaAlServicioYDejaUnInfo`, porque sus nombres dejaban
    de describir lo que prueban (principio IV; T128 y T129);
  - el equipo aprobó un único SQL nativo: el `INSERT` de prueba del default de `stats_loaded`
    (principio VI; T028);
  - el rating se redondea a dos decimales, y un valor imposible de la fuente (rating fuera de 0 a
    10, métrica negativa) omite el partido con `INVALID_IN_SOURCE` (T051, T057, T083 y T108);
  - la columna `whoscored` de una fila del snapshot admite `null` (T058 y T098);
  - `SnapshotLoad` recibe `CatalogReferences` (los equipos y partidos del catálogo que nombra el
    snapshot), así lo que es de otra temporada se ignora sin contarse (T059, T061 y T100);
  - las consultas nuevas de partidos llevan `@EntityGraph` con la temporada y los dos equipos
    (T020); `LeagueStatsSync.seasonTeams()` y `currentSeason` nulo (T057);
    `CatalogPlayers.byExternalId` (T053);
  - una carga del snapshot que falla al guardarse se scrapea en la misma corrida, sin contarse
    como `WRITE_FAILED` (T108);
  - `MatchCentreExtractor` devuelve `Optional<Optional<MatchCentreDto>>` (T080), y
    `StatsSnapshotExport` termina con código 1 si el vigente no se pudo leer (T100 y T120);
  - cada pedido a WhoScored deja un INFO (T087), y los textos de log de T106 y T127 son exactos;
  - tests nuevos fuera del armado original: `StatsSyncWithoutTokenIT` (T118),
    `SyncServiceLastFullSyncTest` (T126), `WhoScoredThreadSleeperTest` (T086) y
    `WhoScoredClientConfigTest` (T007);
  - `.gitignore` suma `backend/data-*/` (T003), y la Fase 7 mide SC-002, SC-003 y SC-004 (T140 y
    T146);
  - los IT de `stats-it` limpian antes y después de cada test, para que una clase nunca deje filas
    de estadísticas que bloqueen la limpieza de otra.
