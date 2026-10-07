# Modelo de datos: Integración con Football-Data.org

**Rama**: `feature/api-footballdata` | **Fecha**: 2026-10-07 | **Plan**: [plan.md](./plan.md)

Todos los tipos del modelo viven en `modelo/<contexto>/` y son Java puro. No llevan anotaciones
de JPA ni importan `jakarta.persistence`, `org.springframework` o el paquete `controller`. Las
reglas de la sincronización viven acá, y no en los servicios ni en el adapter (Principio II y
[research.md](./research.md) D7).

```text
 adapter/footballdata ──► LeagueSnapshot ──────────────────────────────────┐
   (traduce el JSON)        ├─ Season                                      │
                            ├─ TeamSnapshot ── PlayerSnapshot*             │  modelo
                            └─ MatchSnapshot*                              │  (Java puro)
                                                                           ▼
 SyncRun ──► SquadAssignment (dos planteles) ──► LeagueSync (reglas por liga) ──► LeagueSyncResult
    │                                               │ crea / actualiza
    │ playersToDeactivate                           ▼
    │                               Team ◄── Player        Season ◄── Match ──► Team (local y visitante)
    ▼
 SyncReport (informe: va al log y a la respuesta del disparo manual; no se guarda)

 persistence: *Repository ─► *Mapper ─► *SQLDAO ─► *SQL (teams, players, seasons, matches)
```

---

## Modelo

### Team (`modelo/team/`, cambia)

Record `(Long id, String externalId, String name, String crest, League league)`.

| Campo | Tipo | Reglas |
|---|---|---|
| `id` | `Long` | `null` hasta que se persiste. |
| `externalId` | `String` | **Nuevo.** Obligatorio, recortado. Es el id de la fuente como texto (D17) y único (FR-006). |
| `name` | `String` | Obligatorio, recortado. Es el nombre oficial exacto de la fuente, nunca el corto (FR-008). |
| `crest` | `String` | **Nuevo.** Opcional. Es la URL del escudo: no se descarga ni se verifica (FR-008). Un valor en blanco queda en `null`. |
| `league` | `League` | Obligatoria. Es la última liga en la que la fuente informó al equipo (FR-009). |

Los mensajes de `name` y `league` no cambian, y el de `externalId` es nuevo:

- `El nombre del equipo es obligatorio.`
- `La liga del equipo es obligatoria.`
- `El identificador externo del equipo es obligatorio.`

Todos se lanzan con `CatalogInvariantException`.

| Elemento | Detalle |
|---|---|
| Constructor sin id | `Team(externalId, name, crest, league)`. Reemplaza a `Team(name, league)`, que no se puede conservar porque falta el `externalId`. |
| `updateFrom(TeamSnapshot snapshot, League league)` | Devuelve un `Team` con el mismo `id` y `externalId`, y con el nombre, el escudo y la liga que informa la fuente (FR-008, FR-009 y FR-020). |

Se quita el import sin uso de `java.util.Objects`.

### TeamSnapshot (`modelo/team/`, nuevo)

Record `(String externalId, String name, String crest, List<PlayerSnapshot> squad)`. Es un
equipo tal como lo informa la fuente, con su plantel en el orden de la fuente.

- Los invariantes de `externalId` y `name` son los de `Team`. `squad` se copia y nunca es `null`.
- `toNewTeam(League league)` devuelve un `Team` sin id.

### Player (`modelo/player/`, cambia)

Record
`(Long id, String externalId, String name, Position position, Team team, LocalDate dateOfBirth, String nationality, boolean active)`.

| Campo | Tipo | Reglas |
|---|---|---|
| `id`, `externalId`, `name`, `position`, `team` | sin cambios | Mismos invariantes y mismos mensajes que hoy. |
| `dateOfBirth` | `LocalDate` | **Nuevo.** Opcional (FR-014). |
| `nationality` | `String` | **Nuevo.** Opcional, recortado. Un valor en blanco queda en `null` (FR-014). |
| `active` | `boolean` | **Nuevo.** `false` cuando dejó las cinco ligas. Un jugador nunca se borra (FR-017). |

| Elemento | Detalle |
|---|---|
| `Player(externalId, name, position, team)` | **Se conserva.** Arma un jugador sin id, sin fecha de nacimiento ni nacionalidad, y activo. |
| `Player(id, externalId, name, position, team)` | **Se conserva** como constructor de conveniencia, con los mismos valores por defecto. Así no cambian las líneas de los tests que arman jugadores. |
| `league()` | Sin cambios: la liga del equipo. |
| `updateFrom(PlayerSnapshot snapshot, Team team)` | Devuelve el jugador actualizado con el mismo `id` y `externalId`. Ver la regla debajo de la tabla. |
| `deactivate()` | Devuelve el jugador con `active = false`. Conserva su último equipo (FR-017). |

Regla de `updateFrom`:

- `name` y `position` toman el valor del snapshot si lo trae y, si no, conservan el actual
  (FR-013).
- `team` es el que se recibe (FR-016).
- `dateOfBirth` y `nationality` son los del snapshot, aunque vengan vacíos (FR-020).
- `active` queda en `true` (FR-019).

### PlayerSnapshot (`modelo/player/`, nuevo)

Record `(String externalId, String name, Position position, LocalDate dateOfBirth, String nationality)`.
Es un jugador tal como figura en un plantel de la fuente.

| Regla | Detalle |
|---|---|
| `externalId` | Obligatorio. Si falta, es un error de formato y la liga falla. |
| `name` | Opcional. Un valor en blanco queda en `null`. |
| `position` | Opcional. El adapter deja `null` cuando la fuente no informa una de las cuatro posiciones (D7). |
| `isComplete()` | `true` si tiene nombre y posición. Un jugador nuevo incompleto se saltea (FR-012). |
| `missingDataReason()` | `MISSING_NAME` o `MISSING_POSITION`. El nombre se revisa primero. |
| `toNewPlayer(Team team)` | Devuelve un `Player` sin id y activo. Solo vale si `isComplete()`. |

### Season (`modelo/season/`, nuevo)

Record `(Long id, String externalId, League league, LocalDate startDate, LocalDate endDate, Integer currentMatchday)`.

| Campo | Reglas |
|---|---|
| `externalId` | Obligatorio. Es único por temporada (FR-006). |
| `league` | Obligatoria. |
| `startDate`, `endDate` | Obligatorias. `endDate` no puede ser anterior a `startDate`. |
| `currentMatchday` | Opcional. Si viene, es mayor o igual a 1. |

- `updateFrom(Season reported)` conserva `id`, `externalId` y `league`, y toma las fechas y la
  jornada (FR-020 y FR-021).
- Una temporada con otro `externalId` es una temporada nueva. La anterior se conserva con sus
  partidos (FR-025).
- Los invariantes se lanzan con `CatalogInvariantException`, igual que en `Team`.

### Match (`modelo/match/`, nuevo)

Record
`(Long id, String externalId, Season season, Instant utcDate, Integer matchday, MatchStatus status, Team homeTeam, Team awayTeam, Score fullTime, Score halfTime, MatchWinner winner)`.

| Campo | Reglas |
|---|---|
| `externalId` | Obligatorio. Es único por partido (FR-006). |
| `season` | Obligatoria. |
| `utcDate` | Obligatoria. Es la fecha y hora en UTC. |
| `matchday` | Opcional. |
| `status` | Obligatorio (FR-023). |
| `homeTeam`, `awayTeam` | Obligatorios y distintos entre sí. |
| `fullTime`, `halfTime`, `winner` | Opcionales: solo cuando la fuente los informa (FR-022). |

- `updateFrom(MatchSnapshot snapshot, Team home, Team away)` conserva `id`, `externalId` y
  `season`, y toma el resto del snapshot. Un partido postergado o que pasó a `FINISHED` es el
  mismo, actualizado (HU2, escenario 4).

**Score**: record `(Integer home, Integer away)`. Cada valor es `null` o mayor o igual a 0. Un
partido sin jugar tiene `fullTime` y `halfTime` en `null`.

**MatchStatus**: enum con los valores de la fuente, `SCHEDULED`, `TIMED`, `IN_PLAY`, `PAUSED`,
`EXTRA_TIME`, `PENALTY_SHOOTOUT`, `FINISHED`, `SUSPENDED`, `POSTPONED`, `CANCELLED` y `AWARDED`.

**MatchWinner**: enum `HOME_TEAM`, `AWAY_TEAM` y `DRAW`.

### MatchSnapshot (`modelo/match/`, nuevo)

Record
`(String externalId, String seasonExternalId, Instant utcDate, Integer matchday, MatchStatus status, String homeTeamExternalId, String awayTeamExternalId, Score fullTime, Score halfTime, MatchWinner winner)`.
Es un partido tal como lo informa la fuente: los equipos van por `externalId`.

- `externalId`, `utcDate` y los dos `externalId` de equipo son obligatorios, y los dos equipos
  tienen que ser distintos. Si no se cumple, es un error de formato y la liga falla (D5 y D7).
  Así `Match` nunca falla al escribir, dentro de la transacción de la liga.
- `status` queda en `null` si la fuente informa un estado desconocido (D7).
- `toNewMatch(Season season, Team home, Team away)` devuelve un `Match` sin id.

### League (`modelo/league/`, cambia)

El enum no cambia sus valores. Suma:

| Elemento | Detalle |
|---|---|
| `static League fromName(String value)` | Devuelve la liga cuyo nombre coincide con el valor recortado, distinguiendo mayúsculas, igual que el enlace de enums de Spring: ` PREMIER ` se acepta y `premier` no. Si no coincide, o si es `null` o está en blanco, lanza `UnsupportedLeagueException`, cuyo mensaje repite el valor recibido. |

### Snapshot de una liga y reglas de la sincronización (`modelo/sync/`, nuevo)

**LeagueSnapshot**: record `(League league, Season season, List<TeamSnapshot> teams, List<MatchSnapshot> matches)`.

| Invariante | Excepción y motivo |
|---|---|
| `teams` no está vacía (FR-039) | `ExternalSourceException`: `La fuente no informó ningún equipo para la liga.` |
| `season.league()` es igual a `league` | `ExternalSourceException`: `La respuesta de la fuente no tiene el formato esperado.` |
| Todo partido con `seasonExternalId` informado pertenece a `season` | `ExternalSourceException`: `La fuente informó partidos de otra temporada.` |

Expone `teamExternalIds()`, `playerExternalIds()`, `matchExternalIds()` y
`matchTeamExternalIds()` para que el servicio cargue los existentes en una consulta por tipo.

**SquadAssignment**: decide en qué equipo queda un jugador que la fuente informa en más de un
plantel (FR-015; [research.md](./research.md) D9).

| Método | Regla |
|---|---|
| `static of(List<LeagueSnapshot> snapshots)` | Recorre las ligas en el orden recibido, que es el del enum, y los equipos y planteles en el orden de la fuente. Junta los equipos distintos en los que aparece cada `externalId`. |
| `duplicatedPlayerExternalIds()` | Los jugadores que aparecen en más de un equipo. |
| `resolve(List<Player> currentPlayers)` | Elige para cada duplicado su equipo actual, si es uno de los informados. Si no, un jugador guardado queda en el primero en el orden, y uno nuevo en la primera aparición con nombre y posición (o en la primera, si ninguna los tiene). Arma un `DuplicatedPlayer` por cada aparición ignorada. |
| `keeps(String playerExternalId, String teamExternalId)` | `true` si esa aparición se escribe: el jugador no está duplicado, o ese es el equipo elegido. |
| `duplicates()` | Los `DuplicatedPlayer` para el informe. |

**LeagueSync**: aplica las reglas de una liga. Se crea con `(LeagueSnapshot snapshot, SquadAssignment assignment)`,
acumula los conteos y las listas, y el servicio lo usa paso a paso dentro de la transacción de
la liga.

| Método | Recibe | Devuelve | Reglas |
|---|---|---|---|
| `teamsToSave(List<Team> existing)` | Los equipos guardados cuyo `externalId` está en el snapshot | Equipos a guardar | El existente pasa por `updateFrom` y cuenta como actualizado. El nuevo pasa por `toNewTeam` y cuenta como creado. |
| `playersToSave(List<Player> existing, List<Team> savedTeams)` | Los jugadores guardados del snapshot, con su equipo, y los equipos ya guardados de la liga | Jugadores a guardar | Ver la lista debajo de la tabla. |
| `seasonToSave(Optional<Season> existing)` | La temporada guardada con ese `externalId`, si existe | Temporada a guardar | Si existe, `updateFrom`. Si no, la del snapshot. |
| `matchesToSave(List<Match> existing, Season savedSeason, List<Team> catalogTeams)` | Los partidos guardados del snapshot, la temporada guardada y los equipos del catálogo que referencian los partidos | Partidos a guardar | Ver la lista debajo de la tabla. |
| `result()` | — | `LeagueSyncResult` con estado `SUCCEEDED` | Conteos, omitidos y reactivados. |

Reglas de `playersToSave`, para cada aparición en orden:

1. Si `assignment` no la conserva, no hace nada: el caso ya está en `duplicates()`.
2. Si es la repetición de un jugador ya procesado en el mismo equipo, no hace nada.
3. Si el jugador existe, pasa por `updateFrom` y cuenta como actualizado. Si estaba inactivo,
   además va a reactivados (FR-013, FR-016, FR-019 y FR-020).
4. Si es nuevo y está completo, pasa por `toNewPlayer` y cuenta como creado.
5. Si es nuevo e incompleto, se arma un `SkippedPlayer` y cuenta como omitido (FR-012).

Reglas de `matchesToSave`:

- Sin estado: `SkippedMatch` con `UNKNOWN_STATUS`.
- Con el local o el visitante fuera del catálogo: `SkippedMatch` con `UNKNOWN_TEAM`, sin que la
  liga falle (FR-024).
- Existente: `updateFrom`, cuenta como actualizado.
- Nuevo: `toNewMatch`, cuenta como creado.

Los conteos siguen el supuesto del spec: un dato existente que vuelve a llegar cuenta como
actualizado, haya cambiado o no.

**SyncRun**: la corrida en curso. Es una clase mutable y no un record, porque acumula resultados
mientras corre. No la comparten dos hilos: hay a lo sumo una corrida a la vez.

| Método | Regla |
|---|---|
| `static full(SyncOrigin origin, Instant startedAt)` | Tipo `FULL`, con las cinco ligas en el orden del enum. |
| `static singleLeague(League league, Instant startedAt)` | Tipo `SINGLE_LEAGUE`, con origen `MANUAL`: solo el disparo manual puede ser de una liga. |
| `leagues()` | Las ligas pedidas, en orden. |
| `registerSnapshots(List<LeagueSnapshot> snapshots)` | Arma el `SquadAssignment` y guarda como vistos todos los `externalId` de los planteles. |
| `duplicatedPlayerExternalIds()` / `resolveDuplicates(List<Player> current)` / `squadAssignment()` | Delegan en el `SquadAssignment`. |
| `recordSuccess(LeagueSyncResult)` / `recordFailure(League, String reason)` | Un resultado por liga. Una liga que se descargó bien pero falla al escribir queda como fallida. |
| `canDeactivate()` | `true` solo si el tipo es `FULL` y las cinco ligas quedaron `SUCCEEDED` (FR-017 y FR-018). |
| `playersToDeactivate(List<Player> activePlayers)` | Si no `canDeactivate()`, lista vacía. Si no, los activos cuyo `externalId` no está entre los vistos. |
| `finish(Instant finishedAt, List<Player> inactivated)` | Devuelve el `SyncReport`. |

**SyncReport**: record
`(SyncType type, SyncOrigin origin, Instant startedAt, Instant finishedAt, List<LeagueSyncResult> leagues, List<DuplicatedPlayer> duplicatedPlayers, List<Player> inactivatedPlayers, boolean inactivationApplied)`
(FR-043 y FR-044).

- `leagues` va en el orden del enum, una por liga pedida.
- `inactivationApplied` es el valor de `canDeactivate()` al terminar.
- `duration()` y `failedLeagues()` sirven para el log.
- `Optional<InactivationSkipReason> inactivationSkipReason()` dice por qué no se inactivó a
  nadie: vacío si se aplicó la inactivación, `SINGLE_LEAGUE` si fue de una sola liga y
  `FAILED_LEAGUES` si hubo ligas fallidas. Así esa decisión queda en el modelo y el logger solo
  la escribe (Principio II).

**LeagueSyncResult**: record
`(League league, LeagueSyncStatus status, String failureReason, Season season, EntityCounts teams, EntityCounts players, EntityCounts matches, List<SkippedPlayer> skippedPlayers, List<SkippedMatch> skippedMatches, List<Player> reactivatedPlayers)`.
`static failed(League, String reason)` arma un resultado `FAILED`, sin temporada, con conteos en
cero y listas vacías.

**Tipos del informe**:

| Tipo | Definición |
|---|---|
| `SyncType` | Enum `FULL` o `SINGLE_LEAGUE`. |
| `SyncOrigin` | Enum `WEEKLY`, `STARTUP` o `MANUAL`. |
| `LeagueSyncStatus` | Enum `SUCCEEDED` o `FAILED`. |
| `InactivationSkipReason` | Enum `SINGLE_LEAGUE` o `FAILED_LEAGUES`. |
| `EntityCounts` | Record `(int created, int updated, int skipped)`. |
| `SkippedPlayer` | Record `(String externalId, String name, String teamName, PlayerSkipReason reason)`. `name` puede ser `null`. |
| `PlayerSkipReason` | Enum `MISSING_NAME` o `MISSING_POSITION`. |
| `SkippedMatch` | Record `(String externalId, String homeTeamExternalId, String awayTeamExternalId, Instant utcDate, MatchSkipReason reason)`. |
| `MatchSkipReason` | Enum `UNKNOWN_TEAM` o `UNKNOWN_STATUS`. |
| `DuplicatedPlayer` | Record `(String externalId, String name, String keptTeamName, String ignoredTeamName)`. |

### Excepciones

| Excepción | Paquete | Base | Status | Mensaje | La lanza |
|---|---|---|---|---|---|
| `UnsupportedLeagueException` | `modelo/league/exception/` | `BadRequestException` | 400 | `La liga '<valor>' no es una de las admitidas: PREMIER, BUNDESLIGA, LA_LIGA, SERIE_A, LIGUE_1.` | `League.fromName` |
| `SyncInProgressException` | `modelo/sync/exception/` | `ConflictException` | 409 | `Ya hay una sincronización en curso.` | `SyncService` |
| `SyncDisabledException` | `modelo/sync/exception/` | `ServiceUnavailableException`, nueva en `shared/` | 503 | `La sincronización con Football-Data.org está deshabilitada: falta configurar la credencial de la fuente (FOOTBALL_DATA_TOKEN).` | `SyncService` |
| `ExternalSourceException` | `modelo/sync/exception/` | `RuntimeException` | Nunca llega a HTTP: el orquestador la captura por liga. | El motivo de [research.md](./research.md) D5 | El adapter y `LeagueSnapshot` |
| `CatalogInvariantException`, existente | `modelo/player/exception/` | `BadRequestException` | 400 | Los invariantes de `Team`, `Player`, `Season`, `Match` y los snapshots | Los constructores |

`ServiceUnavailableException` sigue el patrón de las otras bases de `shared/`: no usa tipos de
Spring, así que el modelo la puede extender. `GlobalExceptionHandler` suma su handler, que
responde 503 con `ApiError`.

---

## Persistencia

### TeamSQL → tabla `teams` (cambia)

| Columna | Tipo | Nulo | Mapeo | Cambio |
|---|---|---|---|---|
| `id` | `BIGINT` | no | `@Id @GeneratedValue(strategy = IDENTITY)` | — |
| `external_id` | `VARCHAR(32)` | no | `@Column(name = "external_id", nullable = false, length = 32)` | nueva |
| `name` | `VARCHAR(255)` | no | `@Column(nullable = false)` | — |
| `crest` | `VARCHAR(512)` | sí | `@Column(length = 512)` | nueva |
| `league` | `ENUM` de H2 | no | `@Enumerated(EnumType.STRING)` | — |

Índice: `@Table(name = "teams", indexes = @Index(name = "ux_teams_external_id", columnList = "external_id", unique = true))`.

### PlayerSQL → tabla `players` (cambia)

| Columna | Tipo | Nulo | Mapeo | Cambio |
|---|---|---|---|---|
| `id` | `BIGINT` | no | `@Id @GeneratedValue(strategy = IDENTITY)` | — |
| `external_id` | `VARCHAR(255)` | no | `@Column(nullable = false, unique = true)` | — |
| `name` | `VARCHAR(255)` | no | `@Column(nullable = false)` | — |
| `position` | `ENUM` de H2 | no | `@Enumerated(EnumType.STRING)` | — |
| `team_id` | `BIGINT`, FK a `teams` | no | `@ManyToOne(fetch = LAZY, optional = false)` | — |
| `date_of_birth` | `DATE` | sí | `@Column(name = "date_of_birth")` | nueva |
| `nationality` | `VARCHAR(100)` | sí | `@Column(length = 100)` | nueva |
| `active` | `BOOLEAN` | no | `@Column(nullable = false)` | nueva |

### SeasonSQL → tabla `seasons` (nueva)

| Columna | Tipo | Nulo | Mapeo |
|---|---|---|---|
| `id` | `BIGINT` | no | `@Id @GeneratedValue(strategy = IDENTITY)` |
| `external_id` | `VARCHAR(32)` | no | `@Column(name = "external_id", nullable = false, length = 32)` |
| `league` | `VARCHAR(20)` | no | `@Column(nullable = false, length = 20)` sobre un `String`. El mapper traduce con `name()` y `valueOf`. |
| `start_date` | `DATE` | no | `@Column(name = "start_date", nullable = false)` |
| `end_date` | `DATE` | no | `@Column(name = "end_date", nullable = false)` |
| `current_matchday` | `INTEGER` | sí | `@Column(name = "current_matchday")` |

Índice: `ux_seasons_external_id`, único sobre `external_id`.

### MatchSQL → tabla `matches` (nueva)

| Columna | Tipo | Nulo | Mapeo |
|---|---|---|---|
| `id` | `BIGINT` | no | `@Id @GeneratedValue(strategy = IDENTITY)` |
| `external_id` | `VARCHAR(32)` | no | `@Column(name = "external_id", nullable = false, length = 32)` |
| `season_id` | `BIGINT`, FK a `seasons` | no | `@ManyToOne(fetch = LAZY, optional = false) @JoinColumn(name = "season_id")` |
| `utc_date` | `TIMESTAMP(6) WITH TIME ZONE` | no | `@Column(name = "utc_date", nullable = false)` sobre un `Instant` |
| `matchday` | `INTEGER` | sí | `@Column` |
| `status` | `VARCHAR(20)` | no | `@Column(nullable = false, length = 20)` sobre un `String` |
| `home_team_id` | `BIGINT`, FK a `teams` | no | `@ManyToOne(fetch = LAZY, optional = false) @JoinColumn(name = "home_team_id")` |
| `away_team_id` | `BIGINT`, FK a `teams` | no | `@ManyToOne(fetch = LAZY, optional = false) @JoinColumn(name = "away_team_id")` |
| `full_time_home`, `full_time_away` | `INTEGER` | sí | `@Column` |
| `half_time_home`, `half_time_away` | `INTEGER` | sí | `@Column` |
| `winner` | `VARCHAR(10)` | sí | `@Column(length = 10)` sobre un `String` |

Índice: `ux_matches_external_id`, único sobre `external_id`. No se agrega un índice por fecha
([research.md](./research.md) D16).

`@Table` lleva siempre el nombre explícito. `matches` y `seasons` no son palabras reservadas en
H2 ni en PostgreSQL. Las columnas enumeradas nuevas son `VARCHAR` sin `CHECK`, por el mismo
motivo que `app_user.role` en 001.

### DAOs

Solo hay derived queries, JPQL y `@EntityGraph`. No hay SQL nativo.

| DAO | Métodos |
|---|---|
| `TeamSQLDAO` | `List<TeamSQL> findAllByExternalIdIn(Collection<String>)`. **Se elimina** `findByNameAndLeague`, que solo usaba el seeder. |
| `PlayerSQLDAO` | `Page<PlayerSQL> findAllByActiveTrueOrderByIdAsc(Pageable)` reemplaza a `findAllByOrderByIdAsc`. `findAllByFilters` (JPQL) suma `AND player.active = true`. Se suman `@EntityGraph(attributePaths = "team") List<PlayerSQL> findAllByExternalIdIn(Collection<String>)`, `@EntityGraph(attributePaths = "team") List<PlayerSQL> findAllByActiveTrue()`. `findByExternalId` se conserva. Para saber si hay jugadores alcanza con `count()` de `JpaRepository`. |
| `SeasonSQLDAO` | `Optional<SeasonSQL> findByExternalId(String)` |
| `MatchSQLDAO` | `@EntityGraph(attributePaths = {"season", "homeTeam", "awayTeam"}) List<MatchSQL> findAllByExternalIdIn(Collection<String>)` |

### Mappers

Traducen campo a campo y no contienen lógica de negocio.

| Mapper | Cambio |
|---|---|
| `TeamMapper` | Suma `externalId` y `crest`. |
| `PlayerMapper` | Suma `dateOfBirth`, `nationality` y `active`. Sigue usando `TeamMapper`. |
| `SeasonMapper` | Es nuevo. `league` se traduce de `String` a `League` y de vuelta. |
| `MatchMapper` | Es nuevo. Usa `SeasonMapper` y `TeamMapper`. `Score` se reparte en dos columnas y se rearma: si las dos están en `null`, queda `null`. `status` y `winner` se traducen de `String` al enum y de vuelta. |

### Repositories

Reciben y devuelven modelo. Por dentro usan el DAO y el mapper.

| Repository | Método | Comportamiento |
|---|---|---|
| `TeamRepository` | `List<Team> findAllByExternalIds(Collection<String>)` | Una consulta. |
| | `List<Team> saveAll(List<Team>)` | Devuelve los equipos con su id. |
| | `Team save(Team)` | Se conserva. |
| | ~~`findOrCreate(name, league)`~~ | **Se elimina**: reconocía a los equipos por nombre. |
| `PlayerRepository` | `PlayerPage findPage(int, int)` y `findPage(int, int, PlayerFilter)` | Mismas firmas. **Ahora devuelven solo jugadores activos** (FR-049). |
| | `Optional<Player> findById(Long)` | Sin cambios: activo o inactivo (FR-049a). |
| | `List<Player> findAllByExternalIds(Collection<String>)` | Con su equipo, en una consulta. |
| | `List<Player> findAllActive()` | Con su equipo, en una consulta. Lo usa la inactivación. |
| | `List<Player> saveAll(List<Player>)` | Devuelve los jugadores con su id. |
| | `boolean hasPlayers()` | Indica si el catálogo tiene algún jugador. Lo usa el arranque (FR-033). |
| | `Player save(Player)` y `Optional<Player> findByExternalId(String)` | Se conservan. |
| | ~~`existsByExternalId`~~ | **Se elimina**: solo lo usaba el seeder. |
| `SeasonRepository` | `Optional<Season> findByExternalId(String)` y `Season save(Season)` | — |
| `MatchRepository` | `List<Match> findAllByExternalIds(Collection<String>)` | Con la temporada y los equipos. |
| | `List<Match> saveAll(List<Match>)` | — |

Ningún repository que use la sincronización borra datos (FR-007).

---

## Transiciones de estado

```text
 Jugador
   (no existe) ──aparece con nombre y posición──────────────► ACTIVO
   (no existe) ──aparece sin nombre o sin posición─────────► (no se crea; va al informe como omitido)
   ACTIVO ──una sincronización lo incluye──────────────────► ACTIVO, con los datos de la fuente
             (conserva nombre y posición si faltan; equipo = el informado, salvo dos planteles)
   ACTIVO ──completa con las 5 ligas en éxito y no aparece──► INACTIVO (conserva su último equipo)
   ACTIVO ──una sola liga, o completa con alguna fallida────► ACTIVO (nadie se inactiva)
   INACTIVO ──una sincronización lo incluye────────────────► ACTIVO (va al informe como reactivado)
   Nunca se borra. INACTIVO: fuera del listado, pero consultable por id.

 Equipo
   (no existe) ──aparece en una liga──► creado
   creado ──aparece de nuevo──► actualizado (nombre oficial, escudo y liga)
   deja de aparecer (por ejemplo, desciende) ──► sin cambios: conserva su última liga
   Nunca se borra.

 Temporada
   la fuente informa otra temporada en curso ──► se crea la nueva; la anterior queda con sus partidos

 Partido
   SCHEDULED/TIMED ──► IN_PLAY/PAUSED/EXTRA_TIME/PENALTY_SHOOTOUT ──► FINISHED/AWARDED
   SCHEDULED/TIMED ──► POSTPONED/SUSPENDED/CANCELLED
   Cada sincronización actualiza el mismo partido (fecha, estado y resultado). Nunca se borra.

 Corrida (semáforo de SyncService)
   LIBRE ──tryAcquire──► EN CURSO ──termina (siempre, en el finally)──► LIBRE
   Otro disparo con una EN CURSO: el manual → 409; el semanal y el de arranque → se omiten con WARN.
   Sin token: el manual → 503; el semanal → se omite con INFO; el de arranque → WARN y no corre.
```

## Trazabilidad con el spec

| Requisitos | Elemento del diseño |
|---|---|
| FR-001 y FR-003 | `FootballDataAdapter.fetchLeague`, `LeagueSnapshot` y `SyncWriteService.applyLeague` |
| FR-002 y SC-002 | Se borran `PlayerCatalogDataSeeder`, `data/players.json` y su IT. La base local se borra una vez. |
| FR-004 y FR-012 | `LeagueSync.playersToSave`, `PlayerSnapshot.isComplete` y `SkippedPlayer` |
| FR-005 | El adapter consulta solo `/competitions/{code}/teams` y `/competitions/{code}/matches`. |
| FR-006 y SC-005 | `externalId` en las cuatro entidades, los índices únicos y los upserts por `externalId` |
| FR-007 y SC-006 | Ningún repository que use la sincronización borra. La inactivación no borra. |
| FR-008, FR-009 y FR-047 | `Team.name` (nombre oficial), `Team.crest`, `Team.updateFrom` y el filtro exacto existente |
| FR-010, FR-014 y FR-046 | Los campos de `Player`, `PlayerResponse` y `PlayerMapper` |
| FR-011 | La tabla de posiciones de `FootballDataMapper` |
| FR-013, FR-016, FR-019 y FR-020 | `Player.updateFrom`, `Team.updateFrom`, `Season.updateFrom` y `Match.updateFrom` |
| FR-015 | `SquadAssignment` y `DuplicatedPlayer` ([research.md](./research.md) D9) |
| FR-017, FR-018 y SC-009 | `SyncRun.canDeactivate`, `SyncRun.playersToDeactivate`, `Player.deactivate` y `SyncWriteService.deactivateMissing` |
| FR-021, FR-022, FR-023 y FR-025 | `Season`, `Match`, `Score`, `MatchStatus`, `MatchWinner` y las tablas `seasons` y `matches` |
| FR-024 | `LeagueSync.matchesToSave` y `SkippedMatch` con `UNKNOWN_TEAM` |
| FR-026 y FR-027 | `SyncScheduler` con el cron y la zona configurados ([research.md](./research.md) D12) |
| FR-028, FR-029, FR-032 y SC-010 | `SyncController` y la regla de `SecurityConfig` ([research.md](./research.md) D15) |
| FR-030 | `League.fromName` y `UnsupportedLeagueException` |
| FR-031 y SC-014 | El semáforo de `SyncService` y `SyncInProgressException` ([research.md](./research.md) D11) |
| FR-033 y SC-018 | `StartupSync` y `PlayerRepository.hasPlayers` ([research.md](./research.md) D13) |
| FR-034, FR-035 y SC-008 | La descarga antes de la escritura y una transacción por liga en `SyncWriteService` ([research.md](./research.md) D8) |
| FR-036 y SC-007 | Las consultas del catálogo no usan el adapter. H2 lee lo confirmado sin bloquear. |
| FR-037 y FR-038 | El read timeout de 30 s, el reintento único ante un 429 y `Sleeper` ([research.md](./research.md) D3 y D6) |
| FR-039 | El invariante de `LeagueSnapshot` |
| FR-040, FR-041, FR-042 y SC-011 a SC-012 | `FootballDataProperties` (token desde `FOOTBALL_DATA_TOKEN`, `toString` enmascarado), `SyncDisabledException` y el WARN de `StartupSync` |
| FR-043, FR-044, FR-045 y SC-015 | `SyncReport`, `LeagueSyncResult`, `SyncReportLogger` y `SyncReportResponse` |
| FR-048 y FR-050 | `PlayerController`, `PlayerFilter` y la regla pública de `SecurityConfig`, sin cambios |
| FR-049, FR-049a y SC-017 | El filtro `active = true` en los DAOs del listado y `findById` sin cambios |
