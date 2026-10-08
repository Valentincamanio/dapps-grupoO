---

description: "Lista de tareas de la feature 005-integracion-football-data"
---

# Tareas: Integración con Football-Data.org

**Entrada**: documentos de diseño en `/specs/005-integracion-football-data/`

**Prerrequisitos**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md),
[data-model.md](./data-model.md), [contracts/](./contracts/), [quickstart.md](./quickstart.md) y
la constitución 2.2.1 (`.specify/memory/constitution.md`).

**Tests**: se incluyen, porque la constitución (principio IV, NO NEGOCIABLE) y la definición de
terminado los exigen. **No se trabaja con TDD**: cada tarea de código se cierra con sus tests
escritos y en verde, y cada fase termina con `./gradlew build` en verde.

**Organización**: las tareas se agrupan por historia de usuario (HU1 a HU5 del spec = US1 a US5).

- El motor de la sincronización (modelo, persistencia, adapter y servicios) es un único flujo
  (research D8) que necesitan las cinco historias, así que va en la Fase 2, en el orden de los
  pasos 2 a 6 de la "Secuencia de implementación" del plan.
- Hay un motivo más: la constitución prohíbe modificar un test ya escrito sin el "sí" del equipo.
  Por eso cada clase nace con su forma final (componentes de los records y dependencias de los
  constructores), y ninguna fase posterior obliga a tocar un test anterior.
- Cada fase de historia agrega lo propio de esa historia (endpoint, seguridad, consulta del
  catálogo y disparadores automáticos) y los tests de punta a punta que prueban sus escenarios de
  aceptación.
- Los pasos 7 a 9 del plan (endpoint manual, disparadores y consulta) no dependen entre sí, así
  que siguen el orden de prioridad de las historias.

## Formato: `[ID] [P?] [Story] Descripción`

- **[P]**: se puede hacer en paralelo con las otras tareas [P] de su bloque (otro archivo). Si
  tiene una dependencia, se indica entre paréntesis.
- **[Story]**: historia a la que pertenece la tarea (US1 a US5).
- Cada descripción incluye la ruta exacta del archivo.

## Convenciones de rutas y reglas para quien implemente

- Código de producción: `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/`.
- Tests: `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/`.
- Recursos: `backend/src/main/resources/` y `backend/src/test/resources/`.
- Todos los comandos de Gradle se corren desde `backend/`.
- Identificadores en inglés; mensajes, comentarios y Javadoc en español (principio VII).
- Inyección por constructor, SLF4J para logs, constantes con nombre, sin `catch (Exception e)`,
  sin `// TODO` y sin `System.out` (principio V).
- **Nada de `modelo/` importa `org.springframework`, `jakarta.persistence`, `adapter` ni
  `controller`** (principio I). Los DTO de `adapter/footballdata/dto/` nunca salen del adapter.
  Ninguna excepción del cliente HTTP sale del adapter: todas pasan a `ExternalSourceException`.
- **Persistencia**: solo derived queries, JPQL y `@EntityGraph`, sin SQL nativo. `@Table` con
  nombre explícito, índices únicos con nombre y enums nuevos como `VARCHAR` sobre un `String`
  (research D16).
- **Tests existentes**: solo se tocan los cinco de research D21, con el "sí" explícito de Lucas
  del 2026-10-06:
  - se borra `PlayerCatalogDataSeederIT`;
  - se modifican `PlayerTest`, `PlayerRepositoryIT`, `PlayerCatalogServiceTest` y
    `PlayerControllerIT`, solo en las líneas que indica D21.

  Cualquier otro test queda igual. Agregar métodos `@Test` nuevos a una clase existente o a una
  clase creada en esta feature está permitido; modificar o borrar un método ya escrito, no. Un
  test que falla se arregla arreglando el código.
- **Forma de los tests** (principio IV):
  - AssertJ;
  - nombres de método que describen el comportamiento en español;
  - `*Test` para unitarios e `*IT` para integración y end to end;
  - `@ActiveProfiles("test")` en todo lo que levante Spring;
  - las tres partes setup, execute y verify separadas por una línea en blanco;
  - nunca `Thread.sleep`: se usan `RecordingSleeper`, un `Clock` fijo o latches con timeout.
- **Ningún test llama a la API real** (research D21). El perfil test no tiene token y apunta a
  `http://football-data.invalid/v4`.
- **El token de Football-Data.org nunca se escribe en un archivo, un commit, un log ni el chat.**
  Lo exporta Lucas en su terminal como `FOOTBALL_DATA_TOKEN`.
- Contextos nuevos declarados en el plan: `season` y `match` (modelo y persistencia) y `sync`
  (modelo, servicio y controller). `adapter/footballdata/` es la primera clase de la capa
  adapter. No se crean otras carpetas, ni carpetas vacías ni `.gitkeep`.
- **Hasta T111, no correr `./gradlew bootRun` con el perfil `local` sobre la base vieja**
  (`backend/data/futbolmarket.mv.db`). Con `ddl-auto: update`, agregar las columnas `NOT NULL`
  sobre filas existentes hace fallar el arranque. Las fases 1 a 7 se validan con
  `./gradlew build`, que usa la H2 en memoria del perfil test. Si hace falta levantar antes, se
  adelanta T111.

---

## Fase 1: Preparación (infraestructura compartida)

**Propósito**: sacar el dataset ficticio y dejar la configuración y el cliente HTTP listos (paso 1
del plan).

- [X] T001 Borrar el dataset ficticio y su carga (FR-002, research D20): backend/src/main/java/ar/edu/unq/desapp/futbolmarket/config/PlayerCatalogDataSeeder.java, backend/src/main/resources/data/players.json (con la carpeta `data/`, que queda vacía) y backend/src/test/java/ar/edu/unq/desapp/futbolmarket/config/PlayerCatalogDataSeederIT.java. Todavía no tocar `TeamRepository.findOrCreate` ni `PlayerRepository.existsByExternalId`: se quitan en T017 y T018
- [X] T002 [P] En backend/src/main/resources/application.yaml agregar el bloque `football-data` dentro del bloque `futbolmarket` existente, después de `security`, exactamente como figura en contracts/configuration.md ("Cambios en los archivos existentes"). No reemplazar el archivo. El token es solo el placeholder `${FOOTBALL_DATA_TOKEN:}`, sin ningún valor (FR-040)
- [X] T003 [P] En backend/src/test/resources/application-test.yml agregar, dentro del bloque `futbolmarket` existente, `football-data.token: ""`, `football-data.base-url: http://football-data.invalid/v4`, `football-data.sync.cron: "-"` y `football-data.sync.on-startup: false`, como indica contracts/configuration.md. No tocar las claves existentes
- [X] T004 [P] Crear el record `FootballDataProperties` en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/config/FootballDataProperties.java, siguiendo el patrón de `JwtProperties` (research D14):
  - `@ConfigurationProperties("futbolmarket.football-data")` y `@Validated`;
  - componentes: `token` (opcional, sin `@NotBlank`), `baseUrl` (`URI`, `@NotNull`), `connectTimeout` y `readTimeout` (`Duration`, `@NotNull`) y el record anidado `Sync(String cron, String zone, boolean onStartup)` (`@NotNull @Valid`);
  - los constructores compactos lanzan `IllegalArgumentException`, con un mensaje en español, ante una duración cero o negativa, un `cron` que no es ni `CronExpression.isValidExpression` ni `"-"` (`Scheduled.CRON_DISABLED`) o una `zone` que `ZoneId.of` rechaza;
  - `hasToken()` devuelve `true` solo si el token no es `null` ni está en blanco;
  - `toString()` enmascara el token con `****`
- [X] T005 En backend/src/main/java/ar/edu/unq/desapp/futbolmarket/config/ApplicationConfig.java sumar `FootballDataProperties.class` a `@EnableConfigurationProperties` y agregar `@EnableScheduling` y `@EnableAsync` (research D12 y D13), actualizando el Javadoc (depende de T004)
- [X] T006 [P] Crear `FootballDataClientConfig` (`@Configuration`) en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/config/FootballDataClientConfig.java (research D3) (depende de T004):
  - la constante pública `AUTH_TOKEN_HEADER = "X-Auth-Token"`;
  - el método estático público `RestClient.Builder applyDefaults(RestClient.Builder builder, FootballDataProperties properties)`, que pone la `baseUrl` y, solo si `hasToken()`, el header por defecto con el token. El test del adapter (T063) lo reutiliza para verificar el header;
  - el bean `RestClient footballDataRestClient(FootballDataProperties)`: `applyDefaults(RestClient.builder(), properties)` con un `JdkClientHttpRequestFactory` sobre `HttpClient.newBuilder().connectTimeout(connectTimeout).build()` y `setReadTimeout(readTimeout)`;
  - sin interceptores de logging
- [X] T007 [P] Escribir `FootballDataPropertiesTest` (unitario, sin Spring) en backend/src/test/java/ar/edu/unq/desapp/futbolmarket/config/FootballDataPropertiesTest.java con estos casos (depende de T004):
  - acepta el cron `0 0 4 * * MON` y el cron `-`;
  - rechaza un cron inválido, una zona inexistente y un timeout cero o negativo;
  - `hasToken()` es `false` con `null`, `""` y `"   "`;
  - `toString()` no contiene el token;
  - leyendo `application.yaml` con `YamlPropertySourceLoader`, `connect-timeout` es `10s` y `read-timeout` es `30s` (FR-037)
- [X] T008 Correr `./gradlew build` en backend/ y verificar `BUILD SUCCESSFUL`

---

## Fase 2: Fundacional (el motor de la sincronización)

**Propósito**: todo lo que comparten las cinco historias: el catálogo con `externalId`, las
temporadas y los partidos, el modelo de la sincronización, el adapter de Football-Data.org y los
servicios que orquestan (pasos 2 a 6 del plan). Cada bloque deja `./gradlew build` en verde.

**⚠️ CRÍTICO**: ninguna historia empieza antes de terminar esta fase.

### 2A. Catálogo existente: modelo y persistencia (paso 2 del plan)

`Team` y `Player` cambian junto con su persistencia y con los cuatro tests de D21, porque si
cambia uno solo, el resto deja de compilar.

- [X] T009 [P] Crear el enum `PlayerSkipReason` (`MISSING_NAME` y `MISSING_POSITION`) en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/modelo/sync/PlayerSkipReason.java
- [X] T010 Crear el record `PlayerSnapshot(String externalId, String name, Position position, LocalDate dateOfBirth, String nationality)` en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/modelo/player/PlayerSnapshot.java, con las reglas de data-model.md (depende de T009):
  - `externalId` obligatorio y recortado: si falta, lanza `CatalogInvariantException` con el mensaje de `Player` (`El identificador externo del jugador es obligatorio.`);
  - `name` y `nationality` recortados, y un valor en blanco queda en `null`;
  - `isComplete()`: `true` si tiene nombre y posición;
  - `missingDataReason()`: revisa primero el nombre y después la posición. Solo se usa con un snapshot incompleto;
  - `toNewPlayer(Team team)`: un `Player` sin id y activo
- [X] T011 Crear el record `TeamSnapshot(String externalId, String name, String crest, List<PlayerSnapshot> squad)` en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/modelo/team/TeamSnapshot.java (depende de T010):
  - `externalId` y `name` con los invariantes y los mensajes de `Team`;
  - `squad` se copia con `List.copyOf`, y un `null` queda como lista vacía;
  - `toNewTeam(League league)`: un `Team` sin id
- [X] T012 Cambiar el record `Team` en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/modelo/team/Team.java a `(Long id, String externalId, String name, String crest, League league)` (data-model.md, "Team") (depende de T011):
  - `externalId` es obligatorio y se recorta. Su mensaje es `El identificador externo del equipo es obligatorio.`, y los mensajes de `name` y `league` no cambian;
  - `crest` en blanco queda en `null`;
  - el constructor sin id pasa a ser `Team(externalId, name, crest, league)` y reemplaza a `Team(name, league)`;
  - `updateFrom(TeamSnapshot snapshot, League league)` conserva `id` y `externalId`, y toma el nombre, el escudo y la liga;
  - se quita el import sin uso de `java.util.Objects`
- [X] T013 Cambiar el record `Player` en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/modelo/player/Player.java a `(Long id, String externalId, String name, Position position, Team team, LocalDate dateOfBirth, String nationality, boolean active)` (data-model.md, "Player") (depende de T010 y T012):
  - los invariantes y los mensajes existentes no cambian;
  - `nationality` se recorta, y en blanco queda en `null`;
  - se conservan `Player(externalId, name, position, team)` y `Player(id, externalId, name, position, team)`: sin fecha de nacimiento ni nacionalidad, y activo;
  - `updateFrom(PlayerSnapshot snapshot, Team team)` aplica la regla de data-model.md: el nombre y la posición del snapshot si los trae y, si no, los actuales; el equipo recibido; la fecha de nacimiento y la nacionalidad del snapshot aunque vengan vacías; y `active = true`;
  - `deactivate()` devuelve el mismo jugador con `active = false` y su último equipo;
  - `league()` no cambia
- [X] T014 [P] En backend/src/main/java/ar/edu/unq/desapp/futbolmarket/persistence/sql/entity/team/TeamSQL.java:
  - agregar `externalId` (`@Column(name = "external_id", nullable = false, length = 32)`) y `crest` (`@Column(length = 512)`);
  - cambiar a `@Table(name = "teams", indexes = @Index(name = "ux_teams_external_id", columnList = "external_id", unique = true))`;
  - ampliar el constructor con los dos campos nuevos (data-model.md, "TeamSQL")
- [X] T015 [P] En backend/src/main/java/ar/edu/unq/desapp/futbolmarket/persistence/sql/entity/player/PlayerSQL.java agregar `dateOfBirth` (`@Column(name = "date_of_birth")`), `nationality` (`@Column(length = 100)`) y `active` (`@Column(nullable = false)`, `boolean`), y ampliar el constructor (data-model.md, "PlayerSQL")
- [X] T016 Actualizar backend/src/main/java/ar/edu/unq/desapp/futbolmarket/persistence/mapper/team/TeamMapper.java (suma `externalId` y `crest`) y backend/src/main/java/ar/edu/unq/desapp/futbolmarket/persistence/mapper/player/PlayerMapper.java (suma `dateOfBirth`, `nationality` y `active`). Traducen campo a campo, sin lógica (depende de T012 a T015)
- [X] T017 Actualizar el acceso a equipos (data-model.md, "DAOs" y "Repositories") (depende de T016):
  - en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/persistence/sql/interfaces/team/TeamSQLDAO.java, quitar `findByNameAndLeague` y agregar `List<TeamSQL> findAllByExternalIdIn(Collection<String> externalIds)`;
  - en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/persistence/repository/team/TeamRepository.java, quitar `findOrCreate` y agregar `List<Team> findAllByExternalIds(Collection<String>)` (`@Transactional(readOnly = true)`; con una colección vacía devuelve `List.of()` sin consultar) y `List<Team> saveAll(List<Team>)` (`@Transactional`, devuelve los equipos con su id). `save` se conserva
- [X] T018 Actualizar el acceso a jugadores (depende de T016):
  - en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/persistence/sql/interfaces/player/PlayerSQLDAO.java, agregar `@EntityGraph(attributePaths = "team") List<PlayerSQL> findAllByExternalIdIn(Collection<String>)` y `@EntityGraph(attributePaths = "team") List<PlayerSQL> findAllByActiveTrue()`. `findAllByOrderByIdAsc` y el JPQL de `findAllByFilters` todavía no cambian (el filtro por `active` llega en T090);
  - en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/persistence/repository/player/PlayerRepository.java, quitar `existsByExternalId` y agregar `findAllByExternalIds(Collection<String>)` (con una colección vacía devuelve `List.of()` sin consultar), `findAllActive()`, `saveAll(List<Player>)` y `boolean hasPlayers()` (`playerDAO.count() > 0`). `save`, `findByExternalId`, `findById` y `findPage` se conservan
- [X] T019 [P] Modificar backend/src/test/java/ar/edu/unq/desapp/futbolmarket/modelo/player/PlayerTest.java solo como indica D21: el campo `river` pasa a `new Team(1L, "river", "River", null, League.LA_LIGA)`, y las dos construcciones de `rechazaEquipoSinNombreOLiga` pasan a llevar un `externalId` y `crest` en `null`. Los mensajes y las aserciones no cambian
- [X] T020 [P] Modificar backend/src/test/java/ar/edu/unq/desapp/futbolmarket/persistence/repository/player/PlayerRepositoryIT.java solo como indica D21: cada `teamRepository.findOrCreate(name, league)` pasa a `teamRepository.save(new Team(externalId, name, null, league))`, con un `externalId` distinto por equipo dentro de cada test (por ejemplo `"57"` para Arsenal, `"61"` para Chelsea y `"5"` para Bayern Munich). Nada más cambia
- [X] T021 [P] Modificar backend/src/test/java/ar/edu/unq/desapp/futbolmarket/service/player/PlayerCatalogServiceTest.java solo como indica D21: las dos construcciones `new Team(id, name, league)` pasan a `new Team(id, "57", name, null, league)`
- [X] T022 [P] Modificar backend/src/test/java/ar/edu/unq/desapp/futbolmarket/e2e/PlayerControllerIT.java solo como indica D21: las tres construcciones `new Team(2L, "Arsenal", League.PREMIER)` pasan a `new Team(2L, "57", "Arsenal", null, League.PREMIER)`
- [X] T023 [P] Escribir `TeamTest` (unitario) en backend/src/test/java/ar/edu/unq/desapp/futbolmarket/modelo/team/TeamTest.java con estos casos:
  - `Team` rechaza un `externalId` faltante o en blanco con su mensaje;
  - recorta el nombre y el `externalId`, y deja en `null` un escudo en blanco;
  - `updateFrom` conserva `id` y `externalId` y toma el nombre oficial, el escudo y la liga;
  - `TeamSnapshot` copia el plantel y convierte un `null` en lista vacía;
  - `toNewTeam` arma un equipo sin id
- [X] T024 Agregar métodos a `PlayerTest` en backend/src/test/java/ar/edu/unq/desapp/futbolmarket/modelo/player/PlayerTest.java (depende de T019):
  - los constructores de conveniencia dejan al jugador activo y sin fecha de nacimiento ni nacionalidad;
  - `updateFrom` conserva el nombre si el snapshot no lo trae y la posición si llega sin posición;
  - `updateFrom` cambia de equipo (y, con él, de liga), toma la fecha de nacimiento y la nacionalidad aunque vengan vacías y reactiva a un inactivo;
  - `deactivate` deja al jugador inactivo con su último equipo
- [X] T025 Agregar métodos a `PlayerRepositoryIT` en backend/src/test/java/ar/edu/unq/desapp/futbolmarket/persistence/repository/player/PlayerRepositoryIT.java (depende de T020):
  - `saveAll` devuelve los jugadores con id y con los campos nuevos;
  - `findAllByExternalIds` devuelve solo los pedidos, con el nombre de su equipo accesible, y con una colección vacía devuelve una lista vacía;
  - `findAllActive` excluye a los inactivos;
  - `hasPlayers` es `false` con la tabla vacía y `true` después de guardar uno;
  - `findById` devuelve a un jugador inactivo
- [X] T026 [P] Escribir `TeamRepositoryIT` (`@SpringBootTest`, `@ActiveProfiles("test")`, base `testdb`; en `@BeforeEach` borra jugadores y equipos con sus DAOs, como `PlayerRepositoryIT`) en backend/src/test/java/ar/edu/unq/desapp/futbolmarket/persistence/repository/team/TeamRepositoryIT.java con estos casos:
  - `saveAll` y `findAllByExternalIds` guardan y recuperan `externalId`, nombre, escudo y liga;
  - un nombre con diéresis (`FC Bayern München`) se guarda tal cual;
  - un `externalId` repetido lanza `DataIntegrityViolationException` (`ux_teams_external_id`);
  - con una colección vacía, `findAllByExternalIds` devuelve una lista vacía
- [X] T027 Correr `./gradlew build` en backend/ y verificar `BUILD SUCCESSFUL`

### 2B. Temporadas y partidos (paso 3 del plan)

- [X] T028 [P] Crear en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/modelo/match/:
  - el record `Score(Integer home, Integer away)` en `Score.java`: cada valor es `null` o mayor o igual a 0, y si no lanza `CatalogInvariantException`;
  - el enum `MatchStatus` en `MatchStatus.java`, con los once valores de data-model.md;
  - el enum `MatchWinner` en `MatchWinner.java` (`HOME_TEAM`, `AWAY_TEAM` y `DRAW`)
- [X] T029 [P] Crear el record `Season(Long id, String externalId, League league, LocalDate startDate, LocalDate endDate, Integer currentMatchday)` en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/modelo/season/Season.java (data-model.md, "Season"):
  - invariantes con `CatalogInvariantException` y mensajes en español: `externalId` y `league` obligatorios; las dos fechas obligatorias, y el fin no puede ser anterior al inicio; `currentMatchday`, si viene, mayor o igual a 1;
  - `updateFrom(Season reported)` conserva `id`, `externalId` y `league`, y toma las fechas y la jornada
- [X] T030 Crear en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/modelo/match/ los records de data-model.md (depende de T028 y T029):
  - `MatchSnapshot` en `MatchSnapshot.java`:
    - `externalId`, `utcDate` y los dos `externalId` de equipo son obligatorios, y los de equipo además tienen que ser distintos. Así `Match` nunca falla al escribir (data-model.md y research D7);
    - `status` y `seasonExternalId` pueden venir en `null`;
    - `toNewMatch(Season, Team home, Team away)`;
  - `Match` en `Match.java`:
    - `externalId`, `season`, `utcDate`, `status`, `homeTeam` y `awayTeam` son obligatorios, y los dos equipos tienen que ser distintos;
    - `matchday`, `fullTime`, `halfTime` y `winner` son opcionales;
    - `updateFrom(MatchSnapshot, Team home, Team away)` conserva `id`, `externalId` y `season`
- [X] T031 [P] Crear las entidades (data-model.md, "SeasonSQL" y "MatchSQL"; los enums como `String`, sin `@Enumerated`):
  - `SeasonSQL`, tabla `seasons` con el índice único `ux_seasons_external_id`, en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/persistence/sql/entity/season/SeasonSQL.java;
  - `MatchSQL`, tabla `matches` con el índice único `ux_matches_external_id`, en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/persistence/sql/entity/match/MatchSQL.java. Tiene `season`, `homeTeam` y `awayTeam` como `@ManyToOne(fetch = LAZY, optional = false)` con sus `@JoinColumn`, `utcDate` como `Instant` y las cuatro columnas del resultado;
  - las dos con Lombok, como `TeamSQL`
- [X] T032 Crear los DAOs (depende de T031):
  - `SeasonSQLDAO` con `Optional<SeasonSQL> findByExternalId(String)`, en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/persistence/sql/interfaces/season/SeasonSQLDAO.java;
  - `MatchSQLDAO` con `@EntityGraph(attributePaths = {"season", "homeTeam", "awayTeam"}) List<MatchSQL> findAllByExternalIdIn(Collection<String>)`, en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/persistence/sql/interfaces/match/MatchSQLDAO.java
- [X] T033 Crear los mappers (`@Component`), que traducen campo a campo (depende de T029, T030 y T031):
  - `SeasonMapper` en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/persistence/mapper/season/SeasonMapper.java: `league` pasa de `String` a `League` con `name()` y `valueOf`;
  - `MatchMapper` en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/persistence/mapper/match/MatchMapper.java: usa `SeasonMapper` y `TeamMapper`. `Score` se reparte en dos columnas y, si las dos vienen en `null`, queda `null`. `status` y `winner` se traducen entre `String` y enum
- [X] T034 Crear los repositories (`@Repository`), que reciben y devuelven modelo (depende de T032 y T033):
  - `SeasonRepository` (`findByExternalId` y `save`) en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/persistence/repository/season/SeasonRepository.java;
  - `MatchRepository` (`findAllByExternalIds`, que con una colección vacía devuelve `List.of()`, y `saveAll`) en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/persistence/repository/match/MatchRepository.java;
  - ninguno borra datos (FR-007)
- [X] T035 [P] Escribir `SeasonTest` (unitario) en backend/src/test/java/ar/edu/unq/desapp/futbolmarket/modelo/season/SeasonTest.java con estos casos (depende de T029):
  - rechaza la falta de cada dato obligatorio;
  - rechaza un fin anterior al inicio;
  - rechaza la jornada 0 y acepta una jornada `null`;
  - `updateFrom` conserva id, `externalId` y liga, y toma las fechas y la jornada
- [X] T036 [P] Escribir `MatchTest` (unitario) en backend/src/test/java/ar/edu/unq/desapp/futbolmarket/modelo/match/MatchTest.java con estos casos (depende de T030):
  - `Match` rechaza los datos obligatorios faltantes y un mismo equipo como local y visitante;
  - `Score` rechaza un valor negativo y acepta los dos en `null`;
  - `MatchSnapshot` rechaza la falta de un `externalId`, de la fecha o de un equipo, y dos equipos iguales;
  - un partido `TIMED` sin resultado pasa con `updateFrom` a `FINISHED` con su resultado y su ganador, y conserva id, `externalId` y temporada
- [X] T037 Escribir `SeasonRepositoryIT` en backend/src/test/java/ar/edu/unq/desapp/futbolmarket/persistence/repository/season/SeasonRepositoryIT.java (depende de T034):
  - configuración: `@SpringBootTest`, `@ActiveProfiles("test")` y `@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:sync-it;DB_CLOSE_DELAY=-1")`. Es la misma configuración que T038 y T069, para compartir el contexto (research D21);
  - `@BeforeEach` borra partidos, temporadas, jugadores y equipos, en ese orden;
  - casos: guarda y recupera por `externalId` la liga, las fechas y la jornada; la jornada `null` vuelve como `null`; un `externalId` repetido lanza `DataIntegrityViolationException`
- [X] T038 Escribir `MatchRepositoryIT`, con la misma configuración y la misma limpieza que T037, en backend/src/test/java/ar/edu/unq/desapp/futbolmarket/persistence/repository/match/MatchRepositoryIT.java (depende de T034):
  - `saveAll` guarda partidos con su temporada y sus equipos;
  - `findAllByExternalIds` los devuelve con la temporada y los dos equipos accesibles;
  - un partido sin jugar vuelve con `fullTime`, `halfTime` y `winner` en `null`;
  - un partido jugado vuelve con su resultado y su ganador;
  - un `externalId` repetido lanza `DataIntegrityViolationException`
- [X] T039 Correr `./gradlew build` en backend/ y verificar `BUILD SUCCESSFUL`

### 2C. Modelo de la sincronización (paso 4 del plan)

- [X] T040 [P] Crear los tipos del informe en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/modelo/sync/, según la tabla "Tipos del informe" de data-model.md:
  - los enums `SyncType.java`, `SyncOrigin.java`, `LeagueSyncStatus.java`, `MatchSkipReason.java` e `InactivationSkipReason.java` (`SINGLE_LEAGUE` y `FAILED_LEAGUES`);
  - los records `EntityCounts.java`, `SkippedPlayer.java`, `SkippedMatch.java` y `DuplicatedPlayer.java`
- [X] T041 [P] Crear la base `ServiceUnavailableException extends RuntimeException` en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/shared/ServiceUnavailableException.java, sin tipos de Spring y con el Javadoc de `ConflictException` adaptado al 503, que admite el Principio III desde la constitución 2.2.1. El handler del advice llega en T084 (HU3): mientras tanto, un disparo sin token respondería 500, pero ningún test lo ejecuta antes de esa tarea
- [X] T042 Crear las excepciones de backend/src/main/java/ar/edu/unq/desapp/futbolmarket/modelo/sync/exception/ (data-model.md, "Excepciones", y research D5) (depende de T041):
  - `ExternalSourceException.java` extiende `RuntimeException`, con constructores `(String reason)` y `(String reason, Throwable cause)`. Declara como constantes públicas los motivos que comparten el adapter y el modelo: `La respuesta de la fuente no tiene el formato esperado.`, `La fuente no informó ningún equipo para la liga.` y `La fuente informó partidos de otra temporada.`;
  - `SyncInProgressException.java` extiende `ConflictException`, con `Ya hay una sincronización en curso.`;
  - `SyncDisabledException.java` extiende `ServiceUnavailableException`, con el mensaje exacto de data-model.md
- [X] T043 Crear `LeagueSyncResult` (con `static failed(League, String reason)`) y `SyncReport` (con `duration()`, `failedLeagues()` y `Optional<InactivationSkipReason> inactivationSkipReason()`: vacío si se aplicó la inactivación, `SINGLE_LEAGUE` si fue de una sola liga y `FAILED_LEAGUES` si hubo ligas fallidas) en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/modelo/sync/LeagueSyncResult.java y backend/src/main/java/ar/edu/unq/desapp/futbolmarket/modelo/sync/SyncReport.java, con los componentes exactos de data-model.md y las listas copiadas (depende de T040)
- [X] T044 Crear el record `LeagueSnapshot(League league, Season season, List<TeamSnapshot> teams, List<MatchSnapshot> matches)` en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/modelo/sync/LeagueSnapshot.java (depende de T042):
  - los tres invariantes de data-model.md lanzan `ExternalSourceException` con su motivo constante: sin equipos (FR-039), una temporada nula o de otra liga, y un partido de otra temporada;
  - un partido con `seasonExternalId` en `null` se acepta;
  - las listas se copian;
  - `teamExternalIds()`, `playerExternalIds()`, `matchExternalIds()` y `matchTeamExternalIds()` devuelven los ids de local y visitante
- [X] T045 Crear `SquadAssignment` en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/modelo/sync/SquadAssignment.java, con los métodos y las reglas de data-model.md y research D9 (depende de T044):
  - `static of(List<LeagueSnapshot>)` recorre las ligas en el orden recibido y los equipos y planteles en el orden de la fuente;
  - `duplicatedPlayerExternalIds()`;
  - `resolve(List<Player> currentPlayers)`: queda el equipo actual si es uno de los informados. Si no, un jugador guardado queda en el primero, y uno nuevo en la primera aparición con nombre y posición (`PlayerSnapshot.isComplete()`), o en la primera si ninguna lo está (research D9);
  - `keeps(playerExternalId, teamExternalId)`;
  - `duplicates()` arma un `DuplicatedPlayer` por cada aparición ignorada, con el nombre del jugador y los nombres de los dos equipos;
  - el mismo jugador dos veces en el mismo equipo no cuenta como duplicado
- [X] T046 Crear `LeagueSync` en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/modelo/sync/LeagueSync.java (depende de T043 y T045):
  - se construye con `(LeagueSnapshot snapshot, SquadAssignment assignment)` y acumula los conteos, los omitidos y los reactivados;
  - `teamsToSave`, `playersToSave` (las cinco reglas en orden de data-model.md), `seasonToSave` y `matchesToSave` (primero el estado desconocido con `UNKNOWN_STATUS`; después el equipo fuera del catálogo con `UNKNOWN_TEAM`, sin que la liga falle; después los existentes y los nuevos), con las firmas de data-model.md;
  - `matchesToSave` guarda la temporada recibida para el resultado;
  - `result()` devuelve un `LeagueSyncResult` `SUCCEEDED` con esa temporada;
  - un existente que vuelve a llegar cuenta como actualizado, haya cambiado o no
- [X] T047 Crear la clase mutable `SyncRun` en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/modelo/sync/SyncRun.java, con los métodos y las reglas de data-model.md (depende de T045 y T046):
  - `full(origin, startedAt)` y `singleLeague(league, startedAt)`, este último siempre `MANUAL`;
  - `leagues()`;
  - `registerSnapshots`, que guarda como vistos todos los `externalId` de los planteles: omitidos, duplicados y sin posición incluidos;
  - `duplicatedPlayerExternalIds`, `resolveDuplicates` y `squadAssignment`;
  - `recordSuccess` y `recordFailure`;
  - `canDeactivate()`: solo si es `FULL` y las cinco ligas quedaron `SUCCEEDED`;
  - `playersToDeactivate(activePlayers)`;
  - `finish(finishedAt, inactivated)`: devuelve el `SyncReport` con las ligas en el orden del enum, aunque los resultados se hayan registrado en otro orden
- [X] T048 [P] Crear el helper de test `SnapshotFixtures` (clase pública `final` con fábricas estáticas) en backend/src/test/java/ar/edu/unq/desapp/futbolmarket/modelo/sync/SnapshotFixtures.java (depende de T044):
  - `snapshot(League)` devuelve un `LeagueSnapshot` válido y chico: una temporada, dos equipos con jugadores de las cuatro posiciones y dos partidos entre ellos, uno `FINISHED` con resultado y uno `TIMED` sin resultado. Ningún id se repite entre ligas;
  - la Premier usa los datos reales de contracts/football-data-api.md: `Liverpool FC` (64, con su escudo) y `Chelsea FC` (61), con Alisson Becker, Kostas Tsimikas y Federico Chiesa. La Bundesliga incluye a `FC Bayern München`;
  - fábricas sueltas (`season`, `team`, `player` y `match`) para armar variantes.

  Lo reutilizan los tests de modelo, de servicio, de integración y end to end, para no duplicar datos
- [X] T049 [P] Escribir `LeagueSnapshotTest` en backend/src/test/java/ar/edu/unq/desapp/futbolmarket/modelo/sync/LeagueSnapshotTest.java con estos casos (depende de T048):
  - una liga sin equipos lanza `ExternalSourceException` con `La fuente no informó ningún equipo para la liga.`;
  - una temporada de otra liga falla con el motivo de formato;
  - un partido de otra temporada falla con su motivo;
  - un partido sin `seasonExternalId` se acepta;
  - los cuatro métodos de ids devuelven los esperados
- [X] T050 [P] Escribir `SquadAssignmentTest` en backend/src/test/java/ar/edu/unq/desapp/futbolmarket/modelo/sync/SquadAssignmentTest.java con estos casos (depende de T048):
  - sin duplicados, `keeps` es `true` para todos;
  - detecta duplicados dentro de una liga y entre dos ligas;
  - un jugador guardado cuyo equipo actual es uno de los informados queda ahí, aunque no sea el primero;
  - uno guardado cuyo equipo actual no está entre los informados queda en el primero según el orden, aunque esa aparición venga sin posición;
  - uno nuevo queda en el primero si está completo;
  - uno nuevo cuya primera aparición viene sin posición queda en la segunda, que está completa;
  - uno nuevo sin ninguna aparición completa queda en la primera;
  - `duplicates()` lista cada aparición ignorada con el nombre del jugador, el equipo que queda y el ignorado;
  - el mismo jugador dos veces en un equipo no es duplicado
- [X] T051 [P] Escribir `LeagueSyncTest` en backend/src/test/java/ar/edu/unq/desapp/futbolmarket/modelo/sync/LeagueSyncTest.java, con los casos de quickstart.md sección 1 (depende de T048):
  - equipos nuevos y existentes, con sus conteos;
  - un jugador nuevo completo se crea;
  - uno nuevo sin nombre se omite con `MISSING_NAME` y uno sin posición con `MISSING_POSITION`, los dos con su equipo en `SkippedPlayer` (FR-012);
  - uno existente sin posición conserva la suya y cuenta como actualizado (FR-013);
  - uno existente inactivo queda activo y va a reactivados;
  - uno transferido desde un equipo de otra liga queda con el equipo nuevo;
  - una aparición que `SquadAssignment` no conserva no se escribe ni se cuenta;
  - un jugador repetido en el mismo equipo se escribe una vez;
  - la temporada existente se actualiza y una nueva se crea;
  - un partido con estado desconocido se omite con `UNKNOWN_STATUS` y uno con un equipo fuera del catálogo con `UNKNOWN_TEAM`;
  - un partido existente se actualiza y uno nuevo se crea;
  - `result()` trae los conteos, las listas y la temporada
- [X] T052 [P] Escribir `SyncRunTest` en backend/src/test/java/ar/edu/unq/desapp/futbolmarket/modelo/sync/SyncRunTest.java con estos casos (depende de T048):
  - `full` pide las cinco ligas en el orden del enum, y `singleLeague` una sola, `SINGLE_LEAGUE` y `MANUAL`;
  - `canDeactivate` es `true` solo con `FULL` y cinco éxitos, y es `false` con una liga fallida (en la descarga o en la escritura) y con una sola liga;
  - `playersToDeactivate` devuelve los activos no vistos, y una lista vacía si no se puede inactivar;
  - los omitidos, los duplicados y los sin posición cuentan como vistos;
  - `finish` ordena las ligas por el enum y fija `inactivationApplied` (FR-017, FR-018 y SC-009);
  - `inactivationSkipReason()` del informe está vacío si se inactivó, es `SINGLE_LEAGUE` en una de una sola liga y `FAILED_LEAGUES` en una completa con una liga fallida
- [X] T053 Correr `./gradlew build` en backend/ y verificar `BUILD SUCCESSFUL`

### 2D. Adapter de Football-Data.org (paso 5 del plan)

- [ ] T054 [P] Crear los records del JSON del proveedor en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/adapter/footballdata/dto/ (research D4 y contracts/football-data-api.md):
  - `CompetitionTeamsDto(SeasonDto season, List<TeamDto> teams)`;
  - `SeasonDto(Long id, LocalDate startDate, LocalDate endDate, Integer currentMatchday)`;
  - `TeamDto(Long id, String name, String crest, List<PersonDto> squad)`;
  - `PersonDto(Long id, String name, String position, LocalDate dateOfBirth, String nationality)`;
  - `CompetitionMatchesDto(List<MatchDto> matches)`;
  - `MatchDto(Long id, SeasonDto season, Instant utcDate, String status, Integer matchday, MatchTeamDto homeTeam, MatchTeamDto awayTeam, ScoreDto score)`;
  - `MatchTeamDto(Long id)`;
  - `ScoreDto(String winner, ScoreLineDto fullTime, ScoreLineDto halfTime)`;
  - `ScoreLineDto(Integer home, Integer away)`;
  - `ErrorDto(String message, Integer errorCode)`.

  Todos llevan `@JsonIgnoreProperties(ignoreUnknown = true)` (`com.fasterxml.jackson.annotation`) y usan wrappers, nunca primitivos
- [ ] T055 [P] Crear el enum `FootballDataCompetition` (`PL` ↔ `PREMIER`, `BL1` ↔ `BUNDESLIGA`, `PD` ↔ `LA_LIGA`, `SA` ↔ `SERIE_A` y `FL1` ↔ `LIGUE_1`, con `code()` y `static of(League)`) en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/adapter/footballdata/FootballDataCompetition.java
- [ ] T056 [P] Crear la espera inyectable (research D6):
  - la interfaz `Sleeper` (`void sleep(Duration duration)`) en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/adapter/footballdata/Sleeper.java;
  - `ThreadSleeper` (`@Component`) en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/adapter/footballdata/ThreadSleeper.java: usa `Thread.sleep` y, ante `InterruptedException`, restaura la marca de interrupción y lanza `ExternalSourceException` con el motivo constante `Se interrumpió la espera por el límite de consultas de la fuente.` (research D5)
- [ ] T057 Crear `FootballDataMapper` (`@Component`) en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/adapter/footballdata/FootballDataMapper.java, que traduce el vocabulario del proveedor y no decide nada de negocio (research D7) (depende de T054):
  - `toLeagueSnapshot(League, CompetitionTeamsDto, CompetitionMatchesDto)`;
  - posiciones: `Goalkeeper`, `Defence`, `Midfield` y `Offence` pasan a la posición del modelo, y cualquier otro valor o `null` queda en `null` (FR-011);
  - `status` y `winner` pasan al enum del mismo nombre, y un valor desconocido queda en `null`;
  - un `fullTime` o `halfTime` con `home` y `away` en `null` queda como `Score` nulo;
  - cada id pasa a texto con `String.valueOf`, pero un id `null` queda `null` y nunca la cadena `"null"`, para que el invariante lo rechace;
  - las listas `null` quedan vacías
- [ ] T058 Crear `FootballDataClient` (`@Component`) en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/adapter/footballdata/FootballDataClient.java (depende de T054 y T056):
  - recibe por constructor el `RestClient` `footballDataRestClient`, el `Sleeper` y el `Clock`;
  - `fetchTeams(String code)` (`GET /competitions/{code}/teams`) y `fetchMatches(String code)` (`GET /competitions/{code}/matches`);
  - ritmo preventivo: después de cada respuesta, también las de error, guarda `X-Requests-Available-Minute` y el instante de renovación (`X-RequestCounter-Reset` más `clock.instant()`). Antes de cada request, si no quedan disponibles y la renovación no pasó, duerme hasta la renovación. Una respuesta sin `X-Requests-Available-Minute`, o una espera hasta la renovación ya cumplida, limpia el estado: la request siguiente sale sin esperar (research D6);
  - ante un 429 duerme `X-RequestCounter-Reset` segundos (o `DEFAULT_RESET_WAIT` de 60 s si falta o no se puede leer) y reintenta una sola vez. Si el header pide más que `MAX_RESET_WAIT` (120 s), no espera y falla con el motivo del 429 (FR-038). Ningún otro status se reintenta;
  - traduce cada error a `ExternalSourceException` con los motivos constantes de la tabla de research D5: timeout (`ResourceAccessException` con causa `HttpTimeoutException`), otra falla de red, 400, 403, 404, 429 después del reintento, 5xx y JSON ilegible (`RestClientException`). Conserva la excepción original solo como causa;
  - registra en WARN el `message` del `ErrorDto` de la fuente junto con el código de la competición;
  - nunca registra headers, ni el token ni `X-Authenticated-Client`;
  - los nombres de headers y las esperas son constantes con nombre
- [ ] T059 Crear `FootballDataAdapter` (`@Component`) en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/adapter/footballdata/FootballDataAdapter.java (depende de T055, T057 y T058):
  - `LeagueSnapshot fetchLeague(League league)` hace exactamente las dos requests de la liga, primero equipos y después partidos (FR-005), y mapea con `FootballDataMapper`;
  - un `CatalogInvariantException` o un cuerpo `null` pasa a `ExternalSourceException` con el motivo de formato;
  - no persiste nada
- [ ] T060 [P] Crear las fixtures en backend/src/test/resources/footballdata/, armadas a partir de las respuestas reales recortadas de contracts/football-data-api.md, con sus campos desconocidos y sus `null` (research D21):
  - `teams-pl.json`: Liverpool FC y Chelsea FC, las cuatro posiciones y un jugador con `position: null`;
  - `matches-pl.json`: partidos `FINISHED`, `TIMED` y `POSTPONED`, con `odds` y `referees`;
  - `teams-empty.json`: temporada válida y `teams: []`;
  - `error-403.json`: el cuerpo real del 403
- [ ] T061 [P] Crear el doble `RecordingSleeper` (implementa `Sleeper`: registra las duraciones en una lista y no duerme) en backend/src/test/java/ar/edu/unq/desapp/futbolmarket/adapter/footballdata/RecordingSleeper.java
- [ ] T062 Escribir `FootballDataMapperTest` (unitario, sin HTTP) en backend/src/test/java/ar/edu/unq/desapp/futbolmarket/adapter/footballdata/FootballDataMapperTest.java con estos casos (depende de T057):
  - las cuatro posiciones traducidas, y `null` o un valor desconocido sin posición (HU1, escenario 2);
  - los once estados, y uno desconocido en `null`;
  - los tres ganadores, y `null`;
  - un resultado con `home` y `away` en `null` queda sin `Score`;
  - los ids como texto;
  - el nombre oficial y el escudo del equipo, sin `shortName`;
  - las listas `null` quedan vacías
- [ ] T063 Escribir `FootballDataAdapterTest` en backend/src/test/java/ar/edu/unq/desapp/futbolmarket/adapter/footballdata/FootballDataAdapterTest.java (depende de T059, T060 y T061):
  - armado: un `RestClient.Builder` configurado con `FootballDataClientConfig.applyDefaults` y un token de prueba, enlazado a `MockRestServiceServer.bindTo(builder)`, más `RecordingSleeper` y un `Clock` fijo. Sin Spring;
  - camino feliz: dos requests (`/competitions/PL/teams` y `/competitions/PL/matches`) con el header `X-Auth-Token`, y `server.verify()` confirma que no hubo otras (FR-005). El snapshot trae la temporada, los equipos, los jugadores con sus posiciones traducidas, el jugador sin posición y los partidos. Los campos desconocidos se ignoran;
  - errores, cada uno con su motivo de D5: 403 con `error-403.json`, 404, 500, timeout simulado con `withException(new HttpTimeoutException(...))`, otra `IOException` y JSON inválido;
  - `teams-empty.json` falla con el motivo de FR-039;
  - equipos bien y partidos con 500 lanza `ExternalSourceException` (HU3, escenario 3);
  - 429 con `X-RequestCounter-Reset: 42` y después 200: se registra una espera de 42 s y la liga se procesa;
  - 429 dos veces falla con el motivo del 429 y una sola espera;
  - 429 sin el header espera 60 s, y 429 con 121 s falla sin esperar;
  - una respuesta con `X-Requests-Available-Minute: 0` y `X-RequestCounter-Reset: 30` hace que la request siguiente espere 30 s, y con requests disponibles no hay espera;
  - con el reloj fijo, después de esa espera de 30 s la request que sigue no vuelve a esperar;
  - una respuesta sin `X-Requests-Available-Minute` no deja una espera pendiente;
  - el mensaje de ninguna `ExternalSourceException` contiene el token
- [ ] T064 Correr `./gradlew build` en backend/ y verificar `BUILD SUCCESSFUL`

### 2E. Servicios de la sincronización (paso 6 del plan)

- [ ] T065 [P] Crear `SyncReportLogger` (`@Component`, SLF4J) en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/service/sync/SyncReportLogger.java con `void log(SyncReport)`, según research D19 y los ejemplos de quickstart.md 2.3, y su test `SyncReportLoggerTest` (unitario, con `OutputCaptureExtension`) en backend/src/test/java/ar/edu/unq/desapp/futbolmarket/service/sync/SyncReportLoggerTest.java (depende de T043 y T048):
  - una línea por liga: en INFO con los conteos (creados/actualizados/omitidos) y la temporada, o en WARN con el motivo;
  - líneas de detalle en INFO solo si hay algo para listar: jugadores omitidos con nombre y equipo, partidos omitidos, duplicados, reactivados e inactivados;
  - una línea final en INFO con la duración en segundos y los totales. Si no se inactivó a nadie, dice por qué, traduciendo a texto `report.inactivationSkipReason()`: el logger no decide el motivo (Principio II);
  - la línea de inicio no es de este componente: la escribe `SyncService` (T067), como se ve en quickstart.md 2.3;
  - casos del test: una liga procesada y una fallida con su motivo; los omitidos aparecen con nombre y equipo; sin nada para listar, no hay líneas de detalle; la línea final dice el motivo de cada `InactivationSkipReason` y no lo dice si se inactivó (FR-044 y FR-045)
- [ ] T066 [P] Crear `SyncWriteService` (`@Service`) en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/service/sync/SyncWriteService.java con los repositories de equipos, jugadores, temporadas y partidos (research D8 y D10):
  - `@Transactional LeagueSyncResult applyLeague(LeagueSnapshot, SquadAssignment)`: arma un `LeagueSync` y sigue los cinco pasos de D8. Cada paso es una carga por `externalId`, la decisión del modelo y un `saveAll` o `save`. Los equipos que referencian los partidos se cargan después de guardar los de la liga;
  - `@Transactional List<Player> deactivateMissing(SyncRun run)`: `run.playersToDeactivate(playerRepository.findAllActive())`, `deactivate()` sobre cada uno y `saveAll` solo de esos;
  - sin `if` de negocio
- [ ] T067 Crear `SyncService` (`@Service`, no transaccional) en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/service/sync/SyncService.java con el flujo de research D8 y D11 (depende de T059, T065 y T066):
  - dependencias: `FootballDataProperties`, `FootballDataAdapter`, `SyncWriteService`, `PlayerRepository`, `SyncReportLogger` y `Clock`;
  - `SyncReport synchronizeAll(SyncOrigin origin)` y `SyncReport synchronizeLeague(League league)`;
  - primero, sin token, lanza `SyncDisabledException`. Después, si `semaphore.tryAcquire()` falla (`Semaphore(1)`), lanza `SyncInProgressException`. Todo lo demás va en un `try` con `release()` en el `finally`;
  - registra en INFO `Sincronización {tipo} ({origen}) iniciada.`;
  - descarga cada liga en el orden del enum y captura solo `ExternalSourceException` (`recordFailure` con su mensaje);
  - resuelve los duplicados con `playerRepository.findAllByExternalIds(run.duplicatedPlayerExternalIds())`;
  - escribe cada liga descargada con `applyLeague` y captura solo `ExternalSourceException | DataAccessException`. Para `DataAccessException` usa el motivo constante `No se pudieron guardar los datos de la liga.` (research D5) y registra la excepción en WARN;
  - si `run.canDeactivate()`, llama a `deactivateMissing`;
  - cierra con `run.finish(clock.instant(), inactivados)` y `SyncReportLogger.log`;
  - sin `catch (Exception e)`
- [ ] T068 Escribir `SyncServiceTest` en backend/src/test/java/ar/edu/unq/desapp/futbolmarket/service/sync/SyncServiceTest.java (depende de T048 y T067):
  - armado: Mockito para el adapter, `SyncWriteService`, `PlayerRepository` y `SyncReportLogger`; un `FootballDataProperties` real con token y un `Clock` fijo;
  - sin token lanza `SyncDisabledException` con su mensaje y no toca el adapter;
  - una segunda sincronización pedida desde la respuesta del adapter mockeado lanza `SyncInProgressException`. Es determinístico, sin hilos;
  - al terminar, y también después de una `RuntimeException` inesperada de la escritura, que se propaga, se puede volver a sincronizar;
  - una completa descarga y escribe las cinco ligas en orden, inactiva una vez y registra el informe una vez;
  - una liga que falla al descargar no se escribe, las demás sí, y no se inactiva;
  - una `DataIntegrityViolationException` al escribir una liga la deja `FAILED` con su motivo;
  - con las cinco fallidas, devuelve igual el informe sin lanzar nada (FR-032);
  - una sola liga descarga solo esa, es `SINGLE_LEAGUE` y `MANUAL`, y no inactiva;
  - consulta los jugadores actuales por los ids duplicados;
  - `startedAt` y `finishedAt` salen del reloj
- [ ] T069 Escribir `SyncWriteServiceIT` en backend/src/test/java/ar/edu/unq/desapp/futbolmarket/service/sync/SyncWriteServiceIT.java (depende de T048 y T066):
  - configuración y limpieza: las de T037 (H2 `sync-it` y contexto compartido);
  - caso base: `applyLeague` con `SnapshotFixtures.snapshot(PREMIER)` y un `SquadAssignment` resuelto guarda equipos, jugadores, temporada y partidos (cantidades por DAO) y devuelve los conteos de creados.

  Las historias le suman métodos
- [ ] T070 Correr `./gradlew build` en backend/ y verificar `BUILD SUCCESSFUL`

**Checkpoint**: el motor está listo y probado. Las historias pueden empezar.

---

## Fase 3: Historia 1 - Construcción del catálogo desde la fuente (Prioridad: P1) 🎯 MVP

**Objetivo**: un administrador dispara `POST /players/sync` y el catálogo pasa a ser el de las
cinco ligas, con fecha de nacimiento, nacionalidad, escudo y estado en cada jugador (FR-001 a
FR-012, FR-028, FR-029, FR-032, FR-043, FR-044, FR-046 y FR-047).

**Prueba independiente**: como administrador se dispara una completa, con el adapter mockeado en
los tests y con la fuente real en quickstart 3.1. El informe trae las cinco ligas procesadas. El
catálogo tiene los equipos y los jugadores con sus datos nuevos, y el jugador sin posición figura
en el informe y no en el catálogo. Un usuario común recibe 403 y una petición sin credencial 401,
sin consultar la fuente.

**Cobertura de los escenarios**: 1, 4, 5, 6, 8, 9 y 10 → T075; 2 → T062; 3 → T051 y T075;
5 → además T076; 7 → T001, T086 y T112; 10 → además T077.

### Implementación de la Historia 1

- [ ] T071 [P] [US1] Crear el record `SyncReportResponse` con records anidados y `static SyncReportResponse from(SyncReport)`, como `ProfileResponse.from`, en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/controller/sync/dto/SyncReportResponse.java:
  - los campos y los nombres son exactamente los del esquema `SyncReportResponse` de contracts/players-api.yaml;
  - el modelo se traduce a esos nombres: por ejemplo, `teamName` pasa a `team`, `keptTeamName` a `keptTeam` y `ignoredTeamName` a `ignoredTeam`. Los jugadores van como `PlayerSummary(id, externalId, name, team)` y la temporada como `SeasonSummary`, o `null` si la liga falló
- [ ] T072 [US1] Crear `SyncController` en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/controller/sync/SyncController.java (depende de T071):
  - `@RestController` y `@RequestMapping("/players/sync")`;
  - `@Tag(name = "Sincronización")` y dos `@SecurityRequirement` a nivel de clase (`OpenApiConfig.BEARER_AUTH` y `OpenApiConfig.API_KEY_AUTH`), igual que `AccountController`;
  - `@PostMapping` sin parámetros, que llama a `syncService.synchronizeAll(SyncOrigin.MANUAL)` y responde 200 con `SyncReportResponse.from(report)`;
  - `@Operation` en español, tomado de contracts/players-api.yaml;
  - `@ApiResponse` para 200, 401 y 403, con el esquema `ApiError` en los errores. El 503 se agrega en T085 y el 400 y el 409 en T099;
  - solo habla con `SyncService`
- [ ] T073 [P] [US1] En backend/src/main/java/ar/edu/unq/desapp/futbolmarket/security/SecurityConfig.java agregar `.requestMatchers(pathPattern(HttpMethod.POST, "/players/sync")).hasRole(Role.ADMIN.name())` después del matcher de rutas públicas y antes de `anyRequest().authenticated()` (research D15). Es la primera regla por rol. No tocar la línea TEMPORAL de `GET /players`
- [ ] T074 [P] [US1] Agregar al final de `PlayerResponse` los campos `LocalDate dateOfBirth`, `String nationality`, `String teamCrest` y `boolean active` en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/controller/player/dto/PlayerResponse.java, y pasarlos desde `toResponse(Player)` en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/controller/player/PlayerController.java (FR-046 y research D18). El resto del controller no cambia

### Tests de la Historia 1

- [ ] T075 [US1] Escribir `SyncControllerIT` en backend/src/test/java/ar/edu/unq/desapp/futbolmarket/e2e/SyncControllerIT.java, siguiendo el patrón de `AdminAccountIT` (research D21) (depende de T072 a T074):
  - anotaciones: `@SpringBootTest`, `@AutoConfigureMockMvc`, `@ActiveProfiles("test")` y, desde ahora, `@ExtendWith(OutputCaptureExtension.class)`, porque la usa la HU3;
  - `@DynamicPropertySource` con un administrador al azar, `futbolmarket.football-data.token` al azar y una H2 propia (`jdbc:h2:mem:sync-e2e-<sufijo>;DB_CLOSE_DELAY=-1`);
  - `@MockitoBean FootballDataAdapter`, cuyo `fetchLeague` devuelve `SnapshotFixtures.snapshot(liga)`;
  - `@BeforeEach` borra partidos, temporadas, jugadores y equipos con sus DAOs (los usuarios quedan) y obtiene el token del administrador con `AuthTestHelper`;
  - una completa responde 200 con `type: FULL`, `origin: MANUAL` y las cinco ligas `SUCCEEDED`, en el orden del enum y con sus conteos de creados (escenarios 1 y 4);
  - después, `GET /players/{id}` y `GET /players` muestran el nombre oficial del equipo, la liga, `dateOfBirth`, `nationality`, `teamCrest` y `active: true` (escenario 5);
  - un jugador nuevo sin posición figura en `skippedPlayers` con su nombre, su equipo y `MISSING_POSITION`, y no está en el catálogo (escenario 3);
  - `team=Liverpool FC` trae solo jugadores de ese equipo y `team=Liverpool` da una página vacía (escenario 6);
  - un usuario común, con su clave de `AuthTestHelper`, recibe 403 con el mensaje de falta de permiso, y `verifyNoInteractions(adapter)` (escenario 8 y SC-010);
  - sin credencial responde 401 sin tocar el adapter (escenario 9);
  - una segunda completa idéntica da `created` 0 y `updated` igual a los creados en la primera, con el mismo `totalElements` (escenario 10)
- [ ] T076 [P] [US1] Agregar métodos a `PlayerControllerIT` en backend/src/test/java/ar/edu/unq/desapp/futbolmarket/e2e/PlayerControllerIT.java, con el servicio mockeado como los existentes:
  - el listado y el detalle devuelven `dateOfBirth` en `yyyy-MM-dd`, `nationality`, `teamCrest` y `active`;
  - una fecha de nacimiento y una nacionalidad ausentes salen como `null` (FR-014 y FR-046)
- [ ] T077 [P] [US1] Agregar a `SyncWriteServiceIT` en backend/src/test/java/ar/edu/unq/desapp/futbolmarket/service/sync/SyncWriteServiceIT.java: aplicar dos veces el mismo snapshot deja las mismas cantidades de equipos, jugadores, temporadas y partidos, y la segunda vez los conteos dan `created` 0 y todo en `updated` (SC-005)
- [ ] T078 [US1] Correr `./gradlew build` en backend/ y verificar `BUILD SUCCESSFUL`

**Checkpoint**: la HU1 funciona sola. El catálogo se construye desde la fuente con un disparo
manual. Es el MVP.

---

## Fase 4: Historia 2 - Temporadas y partidos de cada liga (Prioridad: P2)

**Objetivo**: cada sincronización guarda la temporada en curso de cada liga y todos sus partidos
(FR-021 a FR-025).

**Prueba independiente**: después de una sincronización, cada liga tiene su temporada con fechas
y jornada, y todos sus partidos con su estado y, si se jugaron, con su resultado (quickstart 3.2
y sección 5).

**Nota**: la HU2 no agrega código de producción. Lo implementan T028 a T038, T046, T057 y T066.
Esta fase prueba sus escenarios de punta a punta.

**Cobertura de los escenarios**: 1 a 3 → T079; 4 y 5 → T080; 6 y 7 → T081; los datos de la
temporada en el informe → T082. Además, T051, T062 y T063.

### Tests de la Historia 2

- [ ] T079 [US2] Agregar a `SyncWriteServiceIT` en backend/src/test/java/ar/edu/unq/desapp/futbolmarket/service/sync/SyncWriteServiceIT.java:
  - la temporada queda guardada con su liga, su inicio, su fin y su jornada actual (escenario 1);
  - todos los partidos del snapshot quedan con fecha y hora, jornada, estado, local y visitante (escenario 2);
  - el partido `FINISHED` tiene su resultado final, su resultado del primer tiempo y su ganador (escenario 3)
- [ ] T080 [US2] Agregar a `SyncWriteServiceIT` en backend/src/test/java/ar/edu/unq/desapp/futbolmarket/service/sync/SyncWriteServiceIT.java (depende de T079):
  - un partido guardado como `TIMED` que el snapshot siguiente informa `FINISHED` es la misma fila, con su resultado, y la cantidad de partidos no cambia. También un postergado que cambia de fecha (escenario 4);
  - partidos `POSTPONED`, `SUSPENDED`, `CANCELLED` y `AWARDED` se guardan con ese estado (escenario 5)
- [ ] T081 [US2] Agregar a `SyncWriteServiceIT` en backend/src/test/java/ar/edu/unq/desapp/futbolmarket/service/sync/SyncWriteServiceIT.java (depende de T080):
  - un partido con un equipo fuera del catálogo no se guarda, figura en `skippedMatches` con `UNKNOWN_TEAM` y la liga queda `SUCCEEDED` (escenario 6 y FR-024);
  - un snapshot con otra temporada en curso crea la temporada nueva con sus partidos y conserva la anterior y los suyos (escenario 7 y FR-025)
- [ ] T082 [P] [US2] Agregar a `SyncControllerIT` en backend/src/test/java/ar/edu/unq/desapp/futbolmarket/e2e/SyncControllerIT.java: después de una completa, cada liga del informe trae su `season` (`externalId`, `startDate`, `endDate` y `currentMatchday`) y los partidos en `matches.created`
- [ ] T083 [US2] Correr `./gradlew build` en backend/ y verificar `BUILD SUCCESSFUL`

**Checkpoint**: las HU1 y HU2 funcionan. Lo que necesita la cotización de la Entrega 2 ya se
guarda.

---

## Fase 5: Historia 3 - Funcionamiento ante fallas de la fuente (Prioridad: P3)

**Objetivo**: una liga que falla no arrastra a las demás ni queda a medio escribir, el catálogo
nunca depende de la fuente, y sin credencial la sincronización responde 503 (FR-034 a FR-042).

**Prueba independiente**: con el adapter mockeado fallando en una liga, en todas o sin token, el
informe marca las fallidas con su motivo, lo guardado de esas ligas no cambia, el catálogo
responde igual y el token no aparece en ningún lado.

**Cobertura de los escenarios**: 1 → T086 (el catálogo no usa el adapter) y quickstart 3.3;
2 → T087 y T068; 3 → T063, T068 y T088; 4 y 5 → T063; 6 → T049 y T063; 7 → T086, T102 y T103
(el WARN de arranque lo escribe `StartupSync`, de la HU5); 8 → T087; 9 → T087, T063 y T108.

### Implementación de la Historia 3

- [ ] T084 [US3] Agregar a backend/src/main/java/ar/edu/unq/desapp/futbolmarket/shared/GlobalExceptionHandler.java el handler `@ExceptionHandler(ServiceUnavailableException.class)`, que responde 503 con `ApiError` y el mensaje de la excepción, con el mismo patrón que el de `ConflictException` (research D15). El resto del advice no cambia
- [ ] T085 [P] [US3] Agregar a `SyncController` en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/controller/sync/SyncController.java el `@ApiResponse` del 503 con el esquema `ApiError`

### Tests de la Historia 3

- [ ] T086 [P] [US3] Escribir `SyncDisabledIT` en backend/src/test/java/ar/edu/unq/desapp/futbolmarket/e2e/SyncDisabledIT.java (depende de T084):
  - configuración: `@SpringBootTest`, `@AutoConfigureMockMvc` y `@ActiveProfiles("test")`, con el administrador por `@DynamicPropertySource`, sin token (el del perfil test) y con una H2 propia;
  - `@MockitoBean FootballDataAdapter`;
  - el disparo del administrador responde 503 con el mensaje exacto de `SyncDisabledException`, y `verifyNoInteractions(adapter)` (escenario 7, FR-041 y SC-012);
  - `GET /players` responde 200 con el catálogo vacío: la aplicación arrancó y no hay datos ficticios (HU1, escenario 7, y SC-002)
- [ ] T087 [US3] Agregar a `SyncControllerIT` en backend/src/test/java/ar/edu/unq/desapp/futbolmarket/e2e/SyncControllerIT.java:
  - después de una completa, una segunda en la que el adapter lanza `ExternalSourceException` para `BUNDESLIGA` responde 200, con `BUNDESLIGA` `FAILED` y su motivo, las otras cuatro `SUCCEEDED` e `inactivationApplied: false`. `GET /players?league=BUNDESLIGA` da lo mismo que antes (escenario 2 y SC-008);
  - las cinco fallidas con el motivo del 403 de research D5 responden 200, con cinco `FAILED` y el catálogo igual que antes (escenario 8 y FR-032);
  - con un `CapturedOutput`, ni el cuerpo de la respuesta ni la salida capturada contienen el token configurado, en una sincronización exitosa y en una fallida (escenario 9 y SC-011)
- [ ] T088 [P] [US3] Agregar a `SyncWriteServiceIT` en backend/src/test/java/ar/edu/unq/desapp/futbolmarket/service/sync/SyncWriteServiceIT.java el caso de rollback (FR-034 y SC-008):
  - se aplica un snapshot válido;
  - después, uno con el equipo renombrado, un jugador nuevo y un partido nuevo con un `externalId` de 33 caracteres, que la columna `VARCHAR(32)` rechaza;
  - `applyLeague` lanza `DataAccessException`;
  - el equipo conserva su nombre, el jugador nuevo no existe y las cantidades de las cuatro tablas son las de antes
- [ ] T089 [US3] Correr `./gradlew build` en backend/ y verificar `BUILD SUCCESSFUL`

**Checkpoint**: las HU1 a HU3 funcionan. El sistema tolera las fallas de la fuente.

---

## Fase 6: Historia 4 - Cambios entre sincronizaciones (Prioridad: P4)

**Objetivo**: transferencias, bajas, regresos y correcciones se reflejan sin borrar nada, y el
listado del catálogo muestra solo a los activos (FR-013, FR-015 a FR-020, FR-049 y FR-049a).

**Prueba independiente**: dos o tres sincronizaciones seguidas en las que la fuente cambia. Hay
un jugador transferido, uno ausente, uno que vuelve y uno corregido, y se verifica el estado de
cada uno, en el listado y en el detalle (quickstart 3.4).

**Cobertura de los escenarios**: 1, 4, 5, 6 y 8 → T093; 2 → T094 y T095; 3 → T095, T052 y T105;
7 → T050 y T095; 9 → T091 y T095; 10 → T092 y T095; 11 → T095.

### Implementación de la Historia 4

- [ ] T090 [US4] Aplicar el filtro de activos al listado (FR-049 y research D18). `findById` no cambia (FR-049a):
  - en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/persistence/sql/interfaces/player/PlayerSQLDAO.java, reemplazar `findAllByOrderByIdAsc` por `Page<PlayerSQL> findAllByActiveTrueOrderByIdAsc(Pageable)` y sumar `AND player.active = true` al JPQL de `findAllByFilters`;
  - en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/persistence/repository/player/PlayerRepository.java, usar el método nuevo en `findPage(int, int)`. Los nombres y las firmas de `findPage` no cambian

### Tests de la Historia 4

- [ ] T091 [US4] Agregar a `PlayerRepositoryIT` en backend/src/test/java/ar/edu/unq/desapp/futbolmarket/persistence/repository/player/PlayerRepositoryIT.java (depende de T090):
  - un jugador inactivo no aparece en `findPage` sin filtros ni filtrando por su último equipo, su liga o su posición;
  - `totalElements` cuenta solo a los activos.

  Que `findById` devuelve a un inactivo ya lo prueba T025
- [ ] T092 [P] [US4] Agregar a `PlayerControllerIT` en backend/src/test/java/ar/edu/unq/desapp/futbolmarket/e2e/PlayerControllerIT.java: el detalle de un jugador inactivo, con el servicio mockeado, responde 200 con `active: false` y su último equipo, no 404 (escenario 10 y FR-049a)
- [ ] T093 [P] [US4] Agregar a `SyncWriteServiceIT` en backend/src/test/java/ar/edu/unq/desapp/futbolmarket/service/sync/SyncWriteServiceIT.java:
  - un jugador de un equipo de la Premier que llega en el plantel de un equipo de la Bundesliga es la misma fila, con el equipo y la liga nuevos (escenario 1 y FR-016);
  - un jugador inactivo que vuelve a llegar queda activo con el equipo informado y figura en `reactivatedPlayers` (escenario 4);
  - el nombre, la posición, la fecha de nacimiento y la nacionalidad corregidos del jugador, y el nombre oficial y el escudo del equipo, se actualizan (escenario 5);
  - un jugador guardado que llega sin posición conserva la suya (escenario 6);
  - un equipo que no llega, porque descendió, queda igual y conserva su liga (escenario 8)
- [ ] T094 [US4] Agregar a `SyncWriteServiceIT` en backend/src/test/java/ar/edu/unq/desapp/futbolmarket/service/sync/SyncWriteServiceIT.java (depende de T093):
  - con un `SyncRun` completo que registró las cinco ligas en éxito, `deactivateMissing` inactiva al jugador guardado que no llegó, que conserva su equipo y sigue en la tabla, y lo devuelve (escenario 2);
  - con un `SyncRun` con una liga fallida, no inactiva a nadie;
  - las cantidades de las cuatro tablas nunca bajan entre dos sincronizaciones en las que la segunda trae menos jugadores y equipos (SC-006)
- [ ] T095 [P] [US4] Agregar a `SyncControllerIT` en backend/src/test/java/ar/edu/unq/desapp/futbolmarket/e2e/SyncControllerIT.java (depende de T090):
  - una segunda completa en la que falta un jugador y falla `LIGUE_1` no lo inactiva (`inactivationApplied: false` y su detalle con `active: true`) (escenario 3);
  - una completa con las cinco ligas bien en la que falta un jugador lo lista en `inactivatedPlayers`. Su detalle responde `active: false` con su último equipo, y no aparece en `GET /players` ni filtrando por ese equipo (escenarios 2, 9 y 10);
  - una completa posterior que lo vuelve a informar lo lista en `reactivatedPlayers` y lo vuelve a mostrar en el listado (escenarios 4 y 11);
  - un jugador informado en dos planteles de ligas distintas figura en `duplicatedPlayers` con el equipo que queda y el ignorado, y aparece una sola vez en el catálogo (escenario 7 y FR-015)
- [ ] T096 [US4] Correr `./gradlew build` en backend/ y verificar `BUILD SUCCESSFUL`

**Checkpoint**: las HU1 a HU4 funcionan. El catálogo se mantiene al día sin borrar nada.

---

## Fase 7: Historia 5 - Sincronización automática semanal y por liga (Prioridad: P5)

**Objetivo**: la corrida de los lunes a las 04:00 de Argentina, la sincronización al arrancar con
el catálogo vacío, el disparo de una sola liga y el rechazo de una segunda sincronización en curso
(FR-026 a FR-028, FR-030, FR-031 y FR-033).

**Prueba independiente**: el cron da como próxima ejecución el lunes a las 04:00 de Argentina.
El arranque sincroniza solo con el catálogo vacío. Una sola liga no toca las otras cuatro. Una
liga no admitida da 400, y un segundo disparo con otro en curso da 409.

**Cobertura de los escenarios**: 1 → T102 y T104; 2, 3 y 4 → T105; 3 → además T098; 5 → T102;
6 → T104 (el cron no recupera corridas, D12); 7 y 8 → T103 y quickstart 2.3 y 2.4; 9 → T068 y
T105 (el mismo semáforo).

### Implementación de la Historia 5

- [ ] T097 [P] [US5] Agregar `static League fromName(String value)` al enum de backend/src/main/java/ar/edu/unq/desapp/futbolmarket/modelo/league/League.java y crear `UnsupportedLeagueException extends BadRequestException` en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/modelo/league/exception/UnsupportedLeagueException.java:
  - `fromName` recorta los espacios de los extremos y distingue mayúsculas, igual que el enlace de enums de Spring (data-model.md y research D15);
  - un valor `null`, en blanco o no admitido lanza la excepción con `La liga '<valor>' no es una de las admitidas: PREMIER, BUNDESLIGA, LA_LIGA, SERIE_A, LIGUE_1.`;
  - la lista de ligas del mensaje se arma desde `values()` (FR-030 y research D15)
- [ ] T098 [US5] Escribir `LeagueTest` (unitario) en backend/src/test/java/ar/edu/unq/desapp/futbolmarket/modelo/league/LeagueTest.java con estos casos (depende de T097):
  - devuelve cada una de las cinco ligas por su nombre, y `" PREMIER "` devuelve `PREMIER`;
  - rechaza `MLS`, `premier` (minúsculas), `""` y `null` con `UnsupportedLeagueException`;
  - el mensaje repite el valor recibido
- [ ] T099 [US5] En backend/src/main/java/ar/edu/unq/desapp/futbolmarket/controller/sync/SyncController.java sumar el parámetro opcional `@RequestParam(required = false) String league` (depende de T097):
  - si es `null`, sincroniza las cinco como hasta ahora;
  - si no, llama a `syncService.synchronizeLeague(League.fromName(league))`. Un `?league=` vacío también da 400;
  - Swagger: `@Parameter` con los cinco valores admitidos y los `@ApiResponse` del 400 y del 409, con el esquema `ApiError`
- [ ] T100 [P] [US5] Crear `SyncScheduler` (`@Component`) en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/service/sync/SyncScheduler.java (research D12):
  - `runWeeklySync()` con `@Scheduled(cron = "${futbolmarket.football-data.sync.cron}", zone = "${futbolmarket.football-data.sync.zone}")`;
  - sin token, registra un INFO y no hace nada;
  - si no, llama a `synchronizeAll(SyncOrigin.WEEKLY)` y, ante `SyncInProgressException`, registra un WARN con el motivo y omite la corrida (FR-031)
- [ ] T101 [P] [US5] Crear `StartupSync` (`@Component`) en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/service/sync/StartupSync.java, con un método `@Async` y `@EventListener(ApplicationReadyEvent.class)` que sigue el flujo de research D13:
  - sin token, registra el WARN exacto de contracts/configuration.md y termina (FR-041);
  - con `sync.on-startup` en `false`, termina;
  - con jugadores (`playerRepository.hasPlayers()`), registra en INFO `El catálogo ya tiene jugadores: no se sincroniza al arrancar.`;
  - si no, registra en INFO `El catálogo está vacío: se dispara una sincronización completa en segundo plano.` y llama a `synchronizeAll(SyncOrigin.STARTUP)`. Ante `SyncInProgressException`, registra un WARN
- [ ] T102 [P] [US5] Escribir `SyncSchedulerTest` (unitario, `SyncService` mockeado y `OutputCaptureExtension`) en backend/src/test/java/ar/edu/unq/desapp/futbolmarket/service/sync/SyncSchedulerTest.java con estos casos (depende de T100):
  - con token, dispara una completa `WEEKLY`;
  - sin token, no llama al servicio y deja el INFO;
  - con otra en curso, no propaga la excepción y deja el WARN (escenarios 1 y 5)
- [ ] T103 [P] [US5] Escribir `StartupSyncTest` (unitario, `PlayerRepository` y `SyncService` mockeados, `FootballDataProperties` real y `OutputCaptureExtension`) en backend/src/test/java/ar/edu/unq/desapp/futbolmarket/service/sync/StartupSyncTest.java con estos casos (depende de T101):
  - con el catálogo vacío y token, dispara una completa `STARTUP` (escenario 7);
  - con datos, no dispara y deja el INFO (escenario 8);
  - sin token, deja el WARN exacto y no consulta el catálogo (HU3, escenario 7);
  - con `on-startup` en `false`, no hace nada;
  - con otra en curso, deja el WARN sin propagar
- [ ] T104 [P] [US5] Agregar a `FootballDataPropertiesTest` en backend/src/test/java/ar/edu/unq/desapp/futbolmarket/config/FootballDataPropertiesTest.java los casos del cron (research D12 y SC-013):
  - leer `futbolmarket.football-data.sync.cron` y `.zone` de `application.yaml` con `YamlPropertySourceLoader`;
  - con `CronExpression`, desde el domingo 2026-10-11 a las 10:00 de Argentina la próxima ejecución es el lunes 2026-10-12 a las 04:00 de Argentina (07:00 UTC);
  - desde ese lunes a las 04:00, la próxima es el lunes siguiente
- [ ] T105 [US5] Agregar a `SyncControllerIT` en backend/src/test/java/ar/edu/unq/desapp/futbolmarket/e2e/SyncControllerIT.java (depende de T099):
  - después de una completa, `?league=SERIE_A` responde 200 con `type: SINGLE_LEAGUE`, una sola liga e `inactivationApplied: false`. El adapter se invoca solo para `SERIE_A`, y `GET /players?league=PREMIER` da lo mismo que antes (escenario 2);
  - `?league=MLS` responde 400 con el mensaje exacto y `verifyNoInteractions(adapter)` (escenario 3);
  - `?league=` y `?league=premier` responden 400;
  - el 409 determinístico de research D21 (escenarios 4 y 9): el adapter mockeado avisa con un latch que entró y espera un segundo latch. El primer disparo corre en otro hilo, con `CompletableFuture`. El test espera la entrada, manda el segundo, que responde 409 con `Ya hay una sincronización en curso.`, libera el latch y verifica que el primero respondió 200. Todas las esperas tienen timeout
- [ ] T106 [US5] Correr `./gradlew build` en backend/ y verificar `BUILD SUCCESSFUL`

**Checkpoint**: las cinco historias funcionan.

---

## Fase 8: Cierre y definición de terminado

**Propósito**: verificaciones transversales y la definición de terminado de la constitución
(paso 10 del plan).

- [ ] T107 [P] Verificar el árbol y las capas (principio I):
  - ningún archivo de backend/src/main/java/ar/edu/unq/desapp/futbolmarket/modelo/ importa `org.springframework`, `jakarta.persistence`, `...adapter` ni `...controller`;
  - `...adapter.footballdata.dto` solo se importa dentro de `adapter/footballdata/`;
  - `SyncController` solo inyecta `SyncService`;
  - no hay `catch (Exception`, ni `Thread.sleep` en `src/test`, ni carpetas vacías o `.gitkeep`;
  - `git diff --stat origin/develop -- backend/src/test` muestra solo archivos nuevos, los cuatro modificados de D21 y el borrado de `PlayerCatalogDataSeederIT`. `FutbolMarketApplicationTests`, `AccessControlIT`, `GlobalExceptionHandlerIT`, `GlobalExceptionHandlerTest` y `JsonAccessDeniedHandlerTest` no cambiaron
- [ ] T108 [P] Verificar los secretos y el dataset ficticio (FR-040, SC-002 y SC-011):
  - la única referencia al token en el repositorio es `${FOOTBALL_DATA_TOKEN:}` en backend/src/main/resources/application.yaml y `token: ""` en backend/src/test/resources/application-test.yml;
  - no queda `players.json` ni ninguna referencia a `PlayerCatalogDataSeeder` en `backend/`;
  - ningún log escribe headers ni `X-Authenticated-Client`
- [ ] T109 [P] Hacer una revisión orientada a SonarCloud del código nuevo en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/ (`adapter/`, `modelo/sync/`, `modelo/season/`, `modelo/match/`, `service/sync/` y `controller/sync/`) (principio V): sin imports ni parámetros sin usar, sin números mágicos, sin código duplicado (los datos de los tests salen de `SnapshotFixtures`), métodos cortos y comentarios que explican el porqué
- [ ] T110 Correr `./gradlew build` en backend/ y verificar `BUILD SUCCESSFUL`
- [ ] T111 Borrar la base H2 local una sola vez, con la aplicación detenida, como indica quickstart.md 2.1 (research D20). **Avisarle antes a Lucas**: se pierden también los usuarios locales
- [ ] T112 Seguir quickstart.md 2.2 (`./gradlew bootRun` sin token): la aplicación levanta, deja el WARN de `StartupSync`, no aparece ninguna línea del seeder y `GET /players` devuelve `totalElements: 0`
- [ ] T113 Seguir quickstart.md 2.3 y 2.4 con el token que Lucas exporta en su terminal (nunca escrito en un archivo):
  - la aplicación termina de arrancar antes que la sincronización de arranque;
  - el catálogo tiene unos 2.634 jugadores en menos de 2 minutos;
  - el token no aparece en el registro;
  - un segundo arranque no sincroniza
- [ ] T114 Seguir los escenarios HTTP de quickstart.md 3.1 a 3.5 con el administrador configurado y verificar cada resultado esperado
- [ ] T115 Seguir quickstart.md sección 4 en `http://localhost:8080/swagger-ui.html`:
  - aparece la etiqueta Sincronización con candado, el parámetro `league` y las respuestas 200, 400, 401, 403, 409 y 503;
  - `PlayerResponse` muestra los cuatro campos nuevos;
  - `POST /players/sync?league=LIGUE_1` responde 200 con `bearerAuth` del administrador y 403 con `apiKeyAuth` de un usuario común
- [ ] T116 Seguir quickstart.md sección 5 en `http://localhost:8080/h2-console`:
  - 96 equipos, unos 2.634 jugadores activos, cinco temporadas y 380 partidos de la Premier;
  - ningún `EXTERNAL_ID` empieza con `premier-`;
  - `STATUS`, `WINNER` y `SEASONS.LEAGUE` son texto;
  - ninguna cantidad baja después de otra completa

---

## Dependencias y orden de ejecución

### Dependencias entre fases

- **Preparación (Fase 1)**: sin dependencias.
- **Fundacional (Fase 2)**: depende de la Fase 1 y **bloquea todas las historias**. Sus bloques van
  en orden (2A → 2B → 2C → 2D → 2E), que es la restricción de orden del plan (pasos 2 a 6). Cada
  bloque deja el build en verde.
- **HU1 (Fase 3)**: depende solo de la Fase 2. Crea `SyncController` y `SyncControllerIT`, que
  usan las demás historias.
- **HU2 (Fase 4)**: depende de la Fase 2. T082 depende además de T075 (`SyncControllerIT`).
- **HU3 (Fase 5)**: depende de la Fase 2. T085 y T087 dependen de la HU1 (`SyncController` y
  `SyncControllerIT`).
- **HU4 (Fase 6)**: depende de la Fase 2. T095 depende además de la HU1.
- **HU5 (Fase 7)**: T097, T098 y T100 a T104 dependen solo de la Fase 2. T099 y T105 dependen
  de la HU1.
- **Cierre (Fase 8)**: depende de todas las historias.

```text
Fase 1 ─► Fase 2 (2A ─► 2B ─► 2C ─► 2D ─► 2E) ─► HU1 ─┬─► HU2 ─┐
                                                       ├─► HU3 ─┤
                                                       ├─► HU4 ─┼─► Fase 8
                                                       └─► HU5 ─┘
```

### Dentro de cada fase

- Fundacional: modelo → persistencia → tests del bloque → build.
- Historias: implementación → tests de la historia → build (sin TDD).
- Una historia termina cuando sus tests existen y pasan, y `./gradlew build` está en verde.
- Varias historias tocan los mismos archivos, y dos tareas sobre el mismo archivo nunca van en
  paralelo:
  - `SyncController`, que crea la HU1 (T072) y amplían la HU3 (T085) y la HU5 (T099);
  - `SyncControllerIT`, `SyncWriteServiceIT`, `PlayerControllerIT` y `PlayerRepositoryIT`, que
    reciben métodos de varias historias.

### Oportunidades de paralelismo

- Fase 1: T002, T003 y T004 juntos; después T005, T006 y T007.
- 2A: T009, T014 y T015 juntos. Con T012 y T013 listos, los cuatro tests de D21 (T019 a T022)
  y T023 y T026 van en paralelo.
- 2B: T028, T029 y T031 juntos; T035 y T036 en paralelo.
- 2C: T040 y T041 juntos; con T047 listo, T049 a T052 en paralelo.
- 2D: T054, T055, T056, T060 y T061 juntos.
- 2E: T065 y T066 juntos.
- Después de la HU1, las HU2 a HU5 pueden avanzar en paralelo si se coordinan los archivos
  compartidos. En la HU5, T097, T100, T101 y T104 arrancan juntos.

---

## Ejemplo de paralelismo: Fundacional 2D

```bash
# DTO, competiciones, espera y fixtures, en paralelo:
Task: "Crear los records del JSON en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/adapter/footballdata/dto/"
Task: "Crear FootballDataCompetition en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/adapter/footballdata/FootballDataCompetition.java"
Task: "Crear Sleeper y ThreadSleeper en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/adapter/footballdata/"
Task: "Crear las fixtures en backend/src/test/resources/footballdata/"
Task: "Crear RecordingSleeper en backend/src/test/java/ar/edu/unq/desapp/futbolmarket/adapter/footballdata/RecordingSleeper.java"
```

## Ejemplo de paralelismo: Historia 1

```bash
# Respuesta, seguridad y catálogo, en paralelo:
Task: "Crear SyncReportResponse en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/controller/sync/dto/SyncReportResponse.java"
Task: "Regla de ADMIN en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/security/SecurityConfig.java"
Task: "Campos nuevos en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/controller/player/dto/PlayerResponse.java"

# Tests de la HU1 sobre archivos distintos, en paralelo:
Task: "Campos nuevos en backend/src/test/java/ar/edu/unq/desapp/futbolmarket/e2e/PlayerControllerIT.java"
Task: "Idempotencia en backend/src/test/java/ar/edu/unq/desapp/futbolmarket/service/sync/SyncWriteServiceIT.java"
```

## Ejemplo de paralelismo: Historia 5

```bash
# Con la Fase 2 terminada, sin esperar a la HU1:
Task: "League.fromName en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/modelo/league/League.java"
Task: "SyncScheduler en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/service/sync/SyncScheduler.java"
Task: "StartupSync en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/service/sync/StartupSync.java"
Task: "Casos del cron en backend/src/test/java/ar/edu/unq/desapp/futbolmarket/config/FootballDataPropertiesTest.java"
```

---

## Estrategia de implementación

### MVP primero (solo la HU1)

1. Fase 1: Preparación.
2. Fase 2: Fundacional, bloque por bloque, con el build en verde al cerrar cada uno.
3. Fase 3: HU1.
4. **Parar y validar**: `./gradlew build` en verde, y quickstart 2.2 y 3.1 con `bootRun` y el
   token.

El MVP deja el catálogo real con un disparo manual del administrador. Como el motor está completo
desde la Fase 2, en el MVP ya se guardan las temporadas y los partidos, y una liga fallida ya no
afecta a las demás. Las historias siguientes los prueban de punta a punta y suman lo que falta.

### Entrega incremental

1. Preparación + Fundacional → motor listo.
2. HU1 → catálogo real con disparo manual (MVP).
3. HU2 → temporadas y partidos verificados.
4. HU3 → 503 sin credencial y fallas verificadas.
5. HU4 → listado solo de activos y cambios entre sincronizaciones verificados.
6. HU5 → corrida semanal, arranque con el catálogo vacío, una sola liga y 409.
7. Cierre (Fase 8).

Cada paso deja `./gradlew build` en verde.

### Riesgo de merge con la rama del compañero (004)

Las dos ramas pueden tocar `application.yaml`, `SecurityConfig` y `GlobalExceptionHandler`. Los
cambios de esta feature son agregados dentro de bloques existentes: ante un conflicto, se
conservan las dos partes (plan, "Secuencia de implementación").

---

## Notas

- [P] = otro archivo, sin dependencias pendientes dentro de su bloque.
- [USn] vincula la tarea con su historia, para la trazabilidad.
- Hacer un commit por tarea o por grupo lógico.
- Parar en cada checkpoint para validar.
- Ante una duda de forma gana la constitución, y ante una ambigüedad del diseño se pregunta antes
  de escribir código.
- Ajustes del 2026-10-07, después de `/speckit-analyze`, ya volcados en el spec, el plan,
  research, data-model, el contrato y el quickstart:
  - el 503 lo admite la constitución 2.2.1;
  - FR-038 tiene un tope de 2 minutos (T058 y T063);
  - un jugador nuevo en dos planteles queda en la primera aparición completa (T045 y T050);
  - `MatchSnapshot` exige equipos distintos (T030);
  - el ritmo preventivo limpia su estado (T058 y T063);
  - los motivos de una liga que falla al escribir y de una espera interrumpida están en research
    D5 (T056 y T067);
  - el motivo de que no se inactive a nadie lo da `SyncReport` y el logger tiene su test (T040,
    T043 y T065);
  - `League.fromName` recorta los espacios (T097 y T098).
- La advertencia de arranque sin token (HU3, escenario 7) la escribe `StartupSync`, de la HU5. Se
  acepta: la HU3 queda completa en ese punto cuando se termina la HU5.
