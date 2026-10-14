# Modelo de datos: Scraping de WhoScored

**Rama**: `feature/scraping-whoscored` | **Fecha**: 2026-10-13 | **Plan**: [plan.md](./plan.md)

Todos los tipos del modelo viven en `modelo/<contexto>/` y son Java puro. No llevan anotaciones de
JPA ni importan `jakarta.persistence`, `org.springframework` o el paquete `controller`. Las reglas
del spec (minutos, goles, asistencias, tarjetas, cruces, pendientes, snapshot y puesta al día)
viven acá. El adapter solo traduce el vocabulario de WhoScored ([research.md](./research.md) D7).

```text
 adapter/whoscored ──► ReportedTeam*, ReportedFixture*  (página de liga y de fixtures)
   (traduce el HTML)  └─► ReportedMatchStats ─ ReportedAppearance*, ReportedGoal*, ReportedCard*
                                                                                   │ modelo
 StatsSnapshot (archivo) ──► SnapshotLoad ──────────┐                              ▼ (Java puro)
                                                    ▼
 StatsSyncRun ──► LeagueStatsSync (por liga) ─► TeamCrossing ─► Team.crossWith
       │               │  pendientes, omisiones ─► Match.crossWith / markStatsLoaded
       │               └─► PlayerCrossing (CatalogPlayers) ─► Player.crossWith
       │                         └─► MatchStatsLoad ─► PlayerMatchStats* (una transacción)
       ▼
 StatsSyncReport (informe: al log y a la respuesta del disparo manual; no se guarda)
       └─► lastFullSync() ─► LastFullSync ─► CatchUpPolicy (qué corre al arrancar)

 persistence: *Repository ─► *Mapper ─► *SQLDAO ─► *SQL
   (teams, players, matches, player_match_stats, last_full_syncs)
   StatsSnapshotRepository ─► StatsSnapshotLineMapper ─► backend/snapshot/whoscored-stats.jsonl
```

---

## Modelo

### Catálogo de la 005 (cambia)

Los tres records suman su identificador de WhoScored, nulo hasta que se cruza. Los constructores
actuales se conservan como constructores de conveniencia (id de WhoScored en `null` y, en
`Match`, la marca en `false`), así no cambia ninguna línea que hoy arma una entidad.

**Team** (`modelo/team/`): record
`(Long id, String externalId, String name, String crest, League league, String whoscoredId)`.

| Elemento | Detalle |
|---|---|
| `whoscoredId` | **Nuevo.** Opcional, recortado; un valor en blanco queda en `null`. Único entre los equipos (FR-016). |
| `Team(id, externalId, name, crest, league)` y `Team(externalId, name, crest, league)` | **Se conservan.** |
| `updateFrom(TeamSnapshot, League)` | **Cambia**: conserva `whoscoredId`. Si no, la sincronización con Football-Data borraría el cruce. |
| `isCrossed()` | `true` si tiene `whoscoredId`. |
| `crossWith(String whoscoredId)` | Devuelve el equipo con ese id. Si ya tiene el mismo, devuelve el mismo; si tiene otro, lanza `CatalogInvariantException`: `El equipo ya está cruzado con otro identificador de WhoScored.` |

**Player** (`modelo/player/`): record
`(Long id, String externalId, String name, Position position, Team team, LocalDate dateOfBirth, String nationality, boolean active, String whoscoredId)`.

| Elemento | Detalle |
|---|---|
| `whoscoredId` | **Nuevo.** Opcional y único (FR-020 y FR-022). |
| Constructores de 8, 5 y 4 argumentos | **Se conservan.** |
| `updateFrom(PlayerSnapshot, Team)` y `deactivate()` | **Cambian**: conservan `whoscoredId`. |
| `isCrossed()` y `crossWith(String)` | Igual que en `Team`, con el mensaje `El jugador ya está cruzado con otro identificador de WhoScored.` |
| `ageOn(LocalDate day)` | `OptionalInt` con la edad cumplida ese día; vacío si no tiene fecha de nacimiento. |

**Match** (`modelo/match/`): record
`(Long id, String externalId, Season season, Instant utcDate, Integer matchday, MatchStatus status, Team homeTeam, Team awayTeam, Score fullTime, Score halfTime, MatchWinner winner, String whoscoredId, boolean statsLoaded)`.

| Elemento | Detalle |
|---|---|
| `whoscoredId` | **Nuevo.** Opcional y único (FR-015). |
| `statsLoaded` | **Nuevo.** `true` cuando el partido ya tiene estadísticas. Nunca vuelve a `false` (FR-012). |
| Constructor de 11 argumentos | **Se conserva**, con `whoscoredId` en `null` y `statsLoaded` en `false`. |
| `updateFrom(MatchSnapshot, Team, Team)` | **Cambia**: conserva `whoscoredId` y `statsLoaded`. |
| `needsStats()` | `true` si el estado es `FINISHED` y no tiene estadísticas (FR-010 y FR-011). Que sea de la temporada en curso lo decide `LeagueStatsSync`. |
| `isCrossed()` y `crossWith(String)` | Igual que en `Team`, con `El partido ya está cruzado con otro identificador de WhoScored.` |
| `markStatsLoaded()` | Devuelve el partido con `statsLoaded = true`. |
| `utcDay()` | La fecha UTC del partido, para comparar calendarios. |

**CurrentSeasons** (`modelo/season/`, nuevo): `of(List<Season>)` y
`current(League) → Optional<Season>`. La temporada en curso de una liga es la de fecha de inicio
más reciente. Cuando Football-Data informa una temporada nueva, la anterior deja de procesarse
(caso borde del cambio de temporada).

### Rendimiento (`modelo/performance/`, contexto nuevo)

**Metric**: enum con las 15 métricas que se cuentan, en este orden, que es el de la tabla, el del
mapper y el de las filas del snapshot: `GOALS`, `ASSISTS`, `SHOTS`, `SHOTS_ON_TARGET`,
`KEY_PASSES`, `PASSES`, `ACCURATE_PASSES`, `SUCCESSFUL_DRIBBLES`, `TACKLES`, `INTERCEPTIONS`,
`CLEARANCES`, `AERIALS_WON`, `FOULS_COMMITTED`, `YELLOW_CARDS` y `RED_CARDS`.

**MetricCounts**: valor inmutable sobre un `EnumMap<Metric, Integer>`.

- `of(Map<Metric, Integer>)` copia el mapa. Un valor negativo lanza `CatalogInvariantException`:
  `Una métrica no puede ser negativa.`
- `get(Metric)` devuelve 0 si la métrica falta.
- `with(Metric, int)` devuelve una copia con ese valor.

**PlayerMatchStats**: record
`(Long id, Player player, Match match, boolean starter, int minutesPlayed, MetricCounts counts, BigDecimal rating, boolean manOfTheMatch)`.
Son las 19 métricas de FR-002: titular, minutos, las 15 de `Metric`, rating y figura.

| Invariante | Mensaje (`CatalogInvariantException`) |
|---|---|
| `player` y `match` obligatorios | `El jugador de la estadística es obligatorio.` / `El partido de la estadística es obligatorio.` |
| `minutesPlayed` ≥ 0 | `Los minutos jugados no pueden ser negativos.` |
| `rating` nulo o entre 0 y 10, con a lo sumo dos decimales (FR-008) | `El rating tiene que estar entre 0 y 10, con a lo sumo dos decimales.` |

Una vez guardada, nunca cambia (Entidad del spec).

**Lo que WhoScored informa de un partido** (lo arma `WhoScoredMapper`):

| Tipo | Definición |
|---|---|
| `Side` | Enum `HOME` o `AWAY`. |
| `CardKind` | Enum `YELLOW`, `SECOND_YELLOW` o `RED`. `isRed()` es `true` en los dos últimos. |
| `ReportedAppearance` | Record `(String whoscoredId, String name, Integer age, Side side, boolean starter, Integer enteredAt, Integer leftAt, MetricCounts sourceCounts, BigDecimal rating, boolean manOfTheMatch)`. `enteredAt` y `leftAt` son minutos expandidos; `sourceCounts` trae las 11 métricas que salen de `stats`, ya sumadas. |
| `ReportedGoal` | Record `(String scorerWhoscoredId, boolean ownGoal, String assisterWhoscoredId)`. `assisterWhoscoredId` es nulo si el gol no trae `Assisted`. |
| `ReportedCard` | Record `(String playerWhoscoredId, CardKind kind, int minute)`, con el minuto expandido. |
| `ReportedMatchStats` | Record `(String whoscoredId, boolean finished, String homeTeamWhoscoredId, String awayTeamWhoscoredId, int endMinute, List<ReportedAppearance> appearances, List<ReportedGoal> goals, List<ReportedCard> cards)`. |

Reglas de `ReportedAppearance` y `ReportedMatchStats`:

| Método | Regla |
|---|---|
| `ReportedAppearance.entered()` | `true` si fue titular o tiene `enteredAt`. Un suplente que no entró no tiene estadísticas (FR-004), aunque haya recibido una tarjeta. |
| `minutesPlayed(appearance)` | Inicio: 0 para un titular, `enteredAt` para un suplente. Fin: el menor entre `leftAt`, el minuto de su segunda amarilla o su roja, y `endMinute`. Devuelve fin − inicio, nunca menos de 0 (FR-005; [research.md](./research.md) D9). |
| `goals(appearance)` | Goles de los que es autor y que no son en contra (FR-006). |
| `assists(appearance)` | Goles que no son en contra cuyo asistidor es él (FR-006). |
| `yellowCards(appearance)` | Tarjetas `YELLOW`, también la que anula una segunda amarilla (FR-007). |
| `redCards(appearance)` | Tarjetas `SECOND_YELLOW` o `RED` (FR-007). |
| `onPitch()` | Las apariciones que entraron, en el orden de la fuente. |
| `isFor(Match match)` | `true` si los dos equipos coinciden con los cruzados del partido. Si no, el partido se omite con `NOT_IN_SOURCE`. |
| `toStats(appearance, Player player, Match match)` | Arma el `PlayerMatchStats` sin id: `sourceCounts` más `GOALS`, `ASSISTS`, `YELLOW_CARDS` y `RED_CARDS`, con los minutos, el rating y la figura (FR-002 y FR-008). |

### Sincronización (`modelo/sync/`, se amplía)

#### Origen, fuente y puesta al día

| Tipo | Definición |
|---|---|
| `SyncOrigin` | **Cambia**: `WEEKLY`, `CATCH_UP` y `MANUAL`. `CATCH_UP` reemplaza a `STARTUP` (FR-053). |
| `SyncSource` | **Nuevo.** Enum `FOOTBALL_DATA` o `WHOSCORED`. |
| `LastFullSync` | **Nuevo.** Record `(SyncSource source, Instant finishedAt)`, los dos obligatorios. Es lo único que se guarda de las corridas (FR-047 y FR-054). |
| `CatchUp` | **Nuevo.** Enum `CHAIN`, `WHOSCORED_ONLY` o `NONE`. |
| `CatchUpPolicy` | **Nuevo.** Record `(Duration maxAge)`, positiva. `decide(Optional<LastFullSync> footballData, Optional<LastFullSync> whoScored, Instant now)` devuelve la decisión de la tabla de abajo. |
| `SyncReport.lastFullSync()` | **Nuevo en la 005.** `Optional<LastFullSync>` de `FOOTBALL_DATA`, presente solo si el tipo es `FULL` y no hay ligas fallidas (FR-047). |

Una última completa está vencida si `now − finishedAt` es mayor que `maxAge`; con exactamente
7 días todavía está al día.

| Football-Data | WhoScored | `decide` |
|---|---|---|
| Ninguna o vencida | Cualquiera | `CHAIN` (FR-034) |
| Al día | Ninguna o vencida | `WHOSCORED_ONLY` (FR-035) |
| Al día | Al día | `NONE` |

#### Nombres y lo que informan las páginas de liga y de fixtures

**NormalizedName**: record `(String value)` con dos fábricas, `ofPlayer(String)` y
`ofTeam(String)`. Aplican NFD, quitan las marcas, reemplazan las letras que NFD no descompone,
pasan a minúsculas y dejan solo letras y dígitos separados por un espacio. `ofTeam` además quita
las palabras de club (`fc`, `afc`, `cf`, `sc`, ...). La lista completa está en
[research.md](./research.md) D12.

| Tipo | Definición |
|---|---|
| `ReportedTeam` | Record `(String whoscoredId, String name)`: un equipo de la página de la liga o de una fila de fixtures. |
| `ReportedFixture` | Record `(String whoscoredId, LocalDate date, String homeTeamWhoscoredId, String homeTeamName, String awayTeamWhoscoredId, String awayTeamName, boolean finished)`. El adapter ya filtró el torneo; la fecha es la del sitio. |

#### Cruces

**TeamCrossing**: decide qué equipo de WhoScored es cada equipo del catálogo de la liga (FR-016 a
FR-018; [research.md](./research.md) D12).

| Elemento | Detalle |
|---|---|
| `of(List<Team> seasonTeams, List<Match> seasonMatches, Collection<ReportedTeam> sourceTeams, Collection<ReportedFixture> fixtures)` | Los equipos y partidos de la temporada en curso, y lo conocido de WhoScored en la corrida, filtrado por las fechas de la temporada con un día de margen. |
| `crossed()` | Los equipos que quedan cruzados en esta evaluación, ya con `crossWith`. |
| `uncrossed()` | `UncrossedTeam` por cada equipo del catálogo que sigue sin cruzar, con su motivo. |
| Constantes | `MIN_ALIGNED_FIXTURES = 5`, `MAX_UNALIGNED_FIXTURES = 2` y `DATE_TOLERANCE = 1 día`. |

Regla:

- Un equipo W de WhoScored y uno T del catálogo **coinciden en calendario** si:
  - al menos 5 partidos de W se alinean con uno de T de la misma localía y a lo sumo un día de
    diferencia;
  - a lo sumo 2 partidos de W quedan sin alinear;
  - los pares de rivales que proponen las alineaciones forman una correspondencia uno a uno que
    no contradice ningún cruce existente.
- Si hay un único T sin cruzar con el mismo `NormalizedName.ofTeam`, W se cruza con él solo si
  coinciden en calendario (FR-017). Si no coinciden, T queda sin cruzar con `NOT_CONFIRMED`.
- Si no hay ninguno con ese nombre, W se cruza con el único T cuyo calendario coincide (FR-017a).
  Con más de uno, el motivo es `AMBIGUOUS`; con ninguno, `NOT_FOUND` (FR-018).
- Se repite mientras haya cruces nuevos. Un equipo ya cruzado no se evalúa (FR-016).

**CatalogPlayers**: índice en memoria de todos los jugadores del catálogo, activos o inactivos,
con su equipo.

- `of(List<Player>)`.
- `byWhoscoredId(String)`.
- `inTeam(Team, NormalizedName)`: los jugadores cuyo equipo actual es ese y tienen ese nombre.
- `named(NormalizedName)`: todos los jugadores con ese nombre.
- `confirm(List<Player> crossed)`: actualiza el índice con los cruces de un partido ya confirmado
  en la base.

**PlayerCrossing**: cruza los jugadores de WhoScored de un partido (FR-019 a FR-022;
[research.md](./research.md) D14). Se crea con `(CatalogPlayers catalog, Match match, ReportedMatchStats reported)`.

| Método | Regla |
|---|---|
| `cross(ReportedAppearance)` | `Optional<Player>`. Primero por `whoscoredId` (FR-020). Si no, entre los del equipo del catálogo de ese lado, con el mismo nombre normalizado, desempatando por edad el día del partido (FR-019). Si no hay ninguno, en todo el catálogo por nombre normalizado y misma edad, solo si hay uno único (FR-019a). Nunca devuelve un jugador que ya tiene otro id de WhoScored ni uno ya elegido en el mismo partido (FR-022). |
| `newlyCrossed()` | Los jugadores que quedaron cruzados en este partido, con `crossWith`. |
| `uncrossed()` | `UncrossedPlayer` por cada jugador que entró y no se pudo cruzar (FR-021). |

**MatchStatsLoad**: lo que se guarda de un partido en una transacción.

- Record `(Match match, List<Player> crossedPlayers, List<PlayerMatchStats> stats, LoadSource source)`.
- `match` ya viene con `markStatsLoaded()` y, si hacía falta, con su `crossWith`.
- `LoadSource` es un enum `SNAPSHOT` o `SCRAPING`.

#### Snapshot

| Tipo | Definición |
|---|---|
| `SnapshotTeamEntry` | Record `(League league, String teamExternalId, String whoscoredId)`. |
| `SnapshotMatchEntry` | Record `(League league, String matchExternalId, String whoscoredId)`. |
| `SnapshotPlayerRow` | Record `(String playerExternalId, String whoscoredId, boolean starter, int minutesPlayed, MetricCounts counts, BigDecimal rating, boolean manOfTheMatch)`. |
| `SnapshotStatsEntry` | Record `(League league, String matchExternalId, List<SnapshotPlayerRow> players)`. Puede no tener filas: es un partido procesado en el que no se cruzó a nadie. |
| `SnapshotStatus` | Enum `LOADED`, `MISSING` o `UNREADABLE`. |
| `SnapshotRead` | Record `(SnapshotStatus status, StatsSnapshot snapshot, String problem)`, con las fábricas `loaded`, `missing` y `unreadable(problem)`. Fuera de `LOADED`, `snapshot` es el vacío. |
| `SnapshotSkipReason` | Enum `TEAM_NOT_IN_CATALOG`, `MATCH_NOT_IN_CATALOG`, `PLAYER_NOT_IN_CATALOG`, `MATCH_NOT_FINISHED` o `CONFLICTING_CROSS`. |

**StatsSnapshot**: record `(List<SnapshotTeamEntry> teams, List<SnapshotMatchEntry> matches, List<SnapshotStatsEntry> stats)`.

| Elemento | Regla |
|---|---|
| Constructor | Copia y ordena en orden canónico: liga en el orden del enum y, dentro de cada liga, id de Football-Data por largo y después alfabético; las filas de jugadores, por id de Football-Data del jugador. Una clave repetida (tipo e id de Football-Data) lanza `CatalogInvariantException`. |
| `empty()` | El snapshot sin entradas. |
| `of(List<Team> crossedTeams, List<Match> crossedMatches, List<Match> loadedMatches, List<PlayerMatchStats> stats)` | Arma las entradas desde la base (FR-023 y FR-029). |
| `merge(StatsSnapshot fromDatabase)` | Conserva cada entrada vigente tal cual, también las que la base no tiene, y suma las de la base cuya clave no está (FR-030 y FR-031). |
| `forLeague(League)` | Las entradas de esa liga. |

**SnapshotLoad**: lo que se carga del snapshot en una liga (FR-024 a FR-026; [research.md](./research.md) D20).

- Se crea con `(StatsSnapshot forLeague, List<Match> seasonMatches, List<Team> seasonTeams, CatalogPlayers catalog)`.
- Expone:
  - `teamCrosses()` y `matchCrosses()`: los equipos y partidos sin cruzar a los que el snapshot
    les da un id libre;
  - `loads()`: un `MatchStatsLoad` con origen `SNAPSHOT` por cada partido pendiente del snapshot;
  - `skipped()`: `Map<SnapshotSkipReason, Integer>` con las entradas salteadas.
- Un partido ya procesado o de otra temporada se ignora sin contarlo. Ante un cruce en conflicto
  gana la base, pero las estadísticas del jugador se cargan igual, porque se ubican por su id de
  Football-Data.

#### Una liga de una corrida de WhoScored

**LeagueStatsSync**: aplica las reglas de una liga. Se crea con
`(League league, Season currentSeason, List<Match> seasonMatches)`, acumula los resultados y el
servicio lo usa paso a paso. No decide nada el servicio.

| Método | Regla |
|---|---|
| `pendingMatches()` | Los partidos de la temporada con `needsStats()`, del más antiguo al más reciente (FR-010 y FR-011). |
| `recordSnapshot(SnapshotLoad, List<MatchStatsLoad> saved)` | Cuenta los procesados desde el snapshot y las entradas salteadas. |
| `needsLeaguePage()` | `true` si hay pendientes y algún equipo de la temporada no está cruzado. |
| `registerSourceTeams(List<ReportedTeam>)` | Suma los equipos de la página de la liga. |
| `nextFixturesPage()` | `Optional<String>` con el id de WhoScored del próximo equipo cuya página de fixtures conviene pedir, nunca dos veces el mismo en la corrida. Primero, los equipos de WhoScored que no corresponden a ninguno del catálogo, en el orden en que se conocieron. Después, el equipo cruzado que cubre más partidos pendientes sin id. ([research.md](./research.md) D11) |
| `registerFixtures(String teamWhoscoredId, List<ReportedFixture>)` | Suma las filas, los rivales como equipos conocidos y marca la página como pedida. |
| `teamCrosses()` | Corre `TeamCrossing` y devuelve los equipos a guardar. |
| `matchCrosses()` | Los partidos de la temporada sin id, con sus dos equipos cruzados, que tienen una única fila con ese local y ese visitante, ya con `crossWith` (FR-015). |
| `confirmTeams(List<Team>)` y `confirmMatches(List<Match>)` | Toma los guardados como estado actual. |
| `closeDiscovery()` | Omite los pendientes que siguen sin id: `UNCROSSED_TEAM` si les falta un equipo cruzado y `NOT_IN_SOURCE` si no (FR-014 y FR-018). |
| `matchesToScrape()` | Los pendientes con id, en orden de fecha. |
| `prepare(Match, Optional<ReportedMatchStats>, CatalogPlayers)` | `Optional<MatchStatsLoad>` con origen `SCRAPING`. Vacío y omitido con `NOT_FINISHED_IN_SOURCE` si no hay datos o no está terminado, y con `NOT_IN_SOURCE` si los equipos no coinciden. Si no, corre `PlayerCrossing` y arma las estadísticas de los que entraron. Los jugadores sin cruzar van al informe (FR-021). |
| `recordScraped(MatchStatsLoad)` y `recordWriteFailure(Match)` | El segundo omite con `WRITE_FAILED`. |
| `result(LeagueStatsStatus)` | `LeagueStatsResult` de la liga. |

#### Corrida e informe de WhoScored

**StatsSyncRun**: la corrida en curso. Es mutable y la usa un solo hilo, igual que `SyncRun`.

| Método | Regla |
|---|---|
| `static full(SyncOrigin, Instant)` | Tipo `FULL`, con las cinco ligas en el orden del enum. |
| `static singleLeague(League, Instant)` | Tipo `SINGLE_LEAGUE`, con origen `MANUAL`. |
| `leagues()` | Las ligas pedidas. |
| `snapshotRead(SnapshotRead)` | Registra el estado del snapshot. |
| `record(LeagueStatsResult)` | Un resultado por liga. |
| `cut(BlockedPage, League, String matchExternalId)` | Registra el corte. Las ligas pedidas sin resultado quedan `NOT_REACHED` al terminar (FR-044). |
| `isCut()` | Si hubo corte. |
| `finish(Instant)` | Devuelve el `StatsSyncReport`. |

**StatsSyncReport**: record
`(SyncType type, SyncOrigin origin, Instant startedAt, Instant finishedAt, SnapshotStatus snapshot, RunCut cut, List<LeagueStatsResult> leagues)`
(FR-048).

- `leagues` va en el orden del enum, una por liga pedida.
- `cut` es nulo si no hubo corte.
- `duration()`, `processedMatches()` y `skippedMatches()` sirven para el registro.
- `lastFullSync()` devuelve `Optional<LastFullSync>` de `WHOSCORED`, presente si el tipo es
  `FULL` y no hubo corte (FR-047).

**LeagueStatsResult**: record
`(League league, LeagueStatsStatus status, int matchesFromSnapshot, int matchesScraped, List<SkippedStatsMatch> skippedMatches, List<UncrossedTeam> uncrossedTeams, List<UncrossedPlayer> uncrossedPlayers, Map<SnapshotSkipReason, Integer> skippedSnapshotEntries)`.
`static notReached(League, int matchesFromSnapshot, Map<...> skipped)` arma el de una liga a la que
el scraping no llegó, con lo que haya cargado del snapshot.

| Tipo | Definición |
|---|---|
| `LeagueStatsStatus` | Enum `PROCESSED`, `CUT` o `NOT_REACHED`. |
| `SkippedStatsMatch` | Record `(String matchExternalId, String homeTeamName, String awayTeamName, Instant utcDate, StatsSkipReason reason)`. |
| `StatsSkipReason` | Enum `UNCROSSED_TEAM`, `NOT_IN_SOURCE`, `NOT_FINISHED_IN_SOURCE` o `WRITE_FAILED` ([research.md](./research.md) D13). |
| `UncrossedTeam` | Record `(String teamExternalId, String name, CrossFailure reason)`: un equipo del catálogo. |
| `UncrossedPlayer` | Record `(String whoscoredId, String name, String teamName, String matchExternalId, CrossFailure reason)`: su nombre, su equipo y el partido (FR-021). |
| `CrossFailure` | Enum `NOT_FOUND`, `AMBIGUOUS` o `NOT_CONFIRMED` (este último, solo para equipos). |
| `PageKind` | Enum `LEAGUE`, `TEAM_FIXTURES` o `MATCH`. |
| `BlockedPage` | Record `(PageKind kind, String whoscoredId, String path)`. |
| `RunCut` | Record `(BlockedPage page, League league, String matchExternalId)`. `matchExternalId` es el id de Football-Data del partido, si la página era la de un partido. |

### Excepciones

| Excepción | Paquete | Base | Status | Mensaje | La lanza |
|---|---|---|---|---|---|
| `SourceBlockedException` | `modelo/sync/exception/` | `RuntimeException` | Nunca llega a HTTP: la captura `StatsSyncService` y la vuelve un corte. | `WhoScored rechazó dos veces seguidas la página <ruta>.` | `WhoScoredClient` |
| `SyncInterruptedException` | `modelo/sync/exception/` | `RuntimeException` | Nunca llega a HTTP: termina la corrida al apagarse la aplicación. | `Se interrumpió la espera entre pedidos a WhoScored.` | `WhoScoredThreadSleeper` |
| `SyncInProgressException`, existente | `modelo/sync/exception/` | `ConflictException` | 409 | `Ya hay una sincronización en curso.` | `SyncLock` |
| `UnsupportedLeagueException`, existente | `modelo/league/exception/` | `BadRequestException` | 400 | El de la 005, con el valor recibido | `League.fromName` |
| `CatalogInvariantException`, existente | `modelo/player/exception/` | `BadRequestException` | 400 | Los invariantes de esta sección | Constructores y `crossWith` |

No se agrega ningún status al advice.

---

## Persistencia

Ningún cambio necesita borrar la base local: las columnas nuevas son nulas o tienen un default, y
`ddl-auto: update` las agrega a las tablas de la 005 con filas ([research.md](./research.md) D17).

### TeamSQL → tabla `teams` (cambia)

| Columna | Tipo | Nulo | Mapeo | Cambio |
|---|---|---|---|---|
| `whoscored_id` | `VARCHAR(32)` | sí | `@Column(name = "whoscored_id", length = 32)` | nueva |

Índices: `ux_teams_external_id` (existente) y **`ux_teams_whoscored_id`**, único sobre
`whoscored_id`.

### PlayerSQL → tabla `players` (cambia)

| Columna | Tipo | Nulo | Mapeo | Cambio |
|---|---|---|---|---|
| `whoscored_id` | `VARCHAR(32)` | sí | `@Column(name = "whoscored_id", length = 32)` | nueva |

`@Table(name = "players", indexes = @Index(name = "ux_players_whoscored_id", columnList = "whoscored_id", unique = true))`.
La restricción única existente de `external_id` no cambia.

### MatchSQL → tabla `matches` (cambia)

| Columna | Tipo | Nulo | Mapeo | Cambio |
|---|---|---|---|---|
| `whoscored_id` | `VARCHAR(32)` | sí | `@Column(name = "whoscored_id", length = 32)` | nueva |
| `stats_loaded` | `BOOLEAN` | no | `@Column(name = "stats_loaded", nullable = false)` con `@ColumnDefault("false")` | nueva |

Índices: `ux_matches_external_id` (existente) y **`ux_matches_whoscored_id`**, único.

### PlayerMatchStatsSQL → tabla `player_match_stats` (nueva)

| Columna | Tipo | Nulo | Mapeo |
|---|---|---|---|
| `id` | `BIGINT` | no | `@Id @GeneratedValue(strategy = IDENTITY)` |
| `player_id` | `BIGINT`, FK a `players` | no | `@ManyToOne(fetch = LAZY, optional = false) @JoinColumn(name = "player_id")` |
| `match_id` | `BIGINT`, FK a `matches` | no | `@ManyToOne(fetch = LAZY, optional = false) @JoinColumn(name = "match_id")` |
| `starter` | `BOOLEAN` | no | `@Column(nullable = false)` |
| `minutes_played` | `INTEGER` | no | `@Column(name = "minutes_played", nullable = false)` |
| `goals`, `assists`, `shots`, `shots_on_target`, `key_passes`, `passes`, `accurate_passes`, `successful_dribbles`, `tackles`, `interceptions`, `clearances`, `aerials_won`, `fouls_committed`, `yellow_cards`, `red_cards` | `INTEGER` | no | Una columna por `Metric`, `nullable = false` |
| `rating` | `DECIMAL(4,2)` | sí | `@Column(precision = 4, scale = 2)` sobre un `BigDecimal` |
| `man_of_the_match` | `BOOLEAN` | no | `@Column(name = "man_of_the_match", nullable = false)` |

Índice: `ux_player_match_stats_player_match`, único sobre (`player_id`, `match_id`). H2 crea un
índice por cada clave foránea. El constructor lo genera Lombok (`@AllArgsConstructor`), como en
`MatchSQL`.

### LastFullSyncSQL → tabla `last_full_syncs` (nueva)

| Columna | Tipo | Nulo | Mapeo |
|---|---|---|---|
| `source` | `VARCHAR(20)` | no | `@Id @Column(length = 20)` sobre un `String`. El mapper traduce con `name()` y `valueOf`. |
| `finished_at` | `TIMESTAMP(6) WITH TIME ZONE` | no | `@Column(name = "finished_at", nullable = false)` sobre un `Instant` |

Una fila por fuente. Una base de la 005 no la tiene: Hibernate la crea vacía (HU3, escenario 9).

### DAOs

Solo hay derived queries, JPQL y `@EntityGraph`. No hay SQL nativo.

| DAO | Métodos nuevos |
|---|---|
| `TeamSQLDAO` | `List<TeamSQL> findAllByWhoscoredIdIsNotNull()` |
| `PlayerSQLDAO` | `@Override @EntityGraph(attributePaths = "team") List<PlayerSQL> findAll()` |
| `MatchSQLDAO` | `@EntityGraph(attributePaths = {"season", "homeTeam", "awayTeam"}) List<MatchSQL> findAllBySeasonExternalId(String)`, `@EntityGraph(attributePaths = "season") List<MatchSQL> findAllByWhoscoredIdIsNotNull()` y `@EntityGraph(attributePaths = "season") List<MatchSQL> findAllByStatsLoadedTrue()` |
| `PlayerMatchStatsSQLDAO` (nuevo) | `@Override @EntityGraph(attributePaths = {"player", "player.team", "match", "match.season", "match.homeTeam", "match.awayTeam"}) List<PlayerMatchStatsSQL> findAll()` |
| `LastFullSyncSQLDAO` (nuevo) | `JpaRepository<LastFullSyncSQL, String>`: `findById` y `save` |

### Mappers

Traducen campo a campo y no contienen lógica de negocio.

| Mapper | Cambio |
|---|---|
| `TeamMapper`, `PlayerMapper` y `MatchMapper` | Suman `whoscoredId` y, en `MatchMapper`, `statsLoaded`. |
| `PlayerMatchStatsMapper` (nuevo) | Una columna por `Metric`, en un único recorrido de `Metric.values()`. Usa `PlayerMapper` y `MatchMapper`. |
| `LastFullSyncMapper` (nuevo) | `source` de `String` a `SyncSource` y de vuelta. |
| `StatsSnapshotLineMapper` (nuevo) | `StatsSnapshot` ↔ líneas JSON del formato de [contracts/snapshot-format.md](./contracts/snapshot-format.md). Los records de cada línea son anidados del mapper; las filas de jugadores son arrays en el orden de `Metric`. |

### Repositories

Reciben y devuelven modelo.

| Repository | Método | Comportamiento |
|---|---|---|
| `TeamRepository` | `List<Team> findAllCrossed()` | Los equipos con id de WhoScored (snapshot). |
| `PlayerRepository` | `List<Player> findAllWithTeam()` | Todos, activos o inactivos, con su equipo, en una consulta (`CatalogPlayers`). |
| `SeasonRepository` | `List<Season> findAll()` | Para `CurrentSeasons`. |
| `MatchRepository` | `List<Match> findAllBySeason(String seasonExternalId)` | Con su temporada y sus equipos. |
| | `List<Match> findAllCrossed()` y `List<Match> findAllWithStats()` | Para el snapshot. |
| `PlayerMatchStatsRepository` (nuevo) | `List<PlayerMatchStats> saveAll(List<PlayerMatchStats>)` y `List<PlayerMatchStats> findAll()` | `findAll` trae el jugador con su equipo y el partido con su temporada y sus equipos. |
| `LastFullSyncRepository` (nuevo) | `Optional<LastFullSync> find(SyncSource)` y `LastFullSync save(LastFullSync)` | — |
| `StatsSnapshotRepository` (nuevo, sobre el archivo) | `SnapshotRead read()` y `void write(StatsSnapshot)` | Lee el archivo entero o informa `MISSING`/`UNREADABLE`. Escribe a un temporal en la misma carpeta y lo mueve sobre el vigente. La ruta sale de `futbolmarket.whoscored.snapshot.path`. |

Ningún repository de esta feature borra datos (SC-008).

---

## Transiciones de estado

```text
 Partido (estadísticas)
   SCHEDULED/TIMED/POSTPONED/... ──────────────────────► no es pendiente (FR-011)
   FINISHED, sin estadísticas, temporada en curso ──────► PENDIENTE
   PENDIENTE ──snapshot o scraping, en una transacción──► CON ESTADÍSTICAS (statsLoaded); nunca vuelve (FR-012)
   PENDIENTE ──equipo sin cruzar / no está en WS / WS no lo da por terminado / falla al guardar──► PENDIENTE (va al informe)
   PENDIENTE de una temporada que dejó de estar en curso ──► queda sin estadísticas; no se procesa más

 Partido (cruce)
   SIN ID DE WS ──página de fixtures o snapshot, con sus dos equipos cruzados──► CON ID DE WS (para siempre)

 Equipo y jugador (cruce)
   SIN CRUZAR ──nombre + calendario, solo calendario, snapshot / nombre, edad, snapshot──► CRUZADO (para siempre)
   SIN CRUZAR ──no se puede──► SIN CRUZAR (va al informe; se reintenta en la corrida siguiente)
   Un cruce nunca cambia, y la sincronización con Football-Data lo conserva.

 Candado (SyncLock): LIBRE ──manual FD | manual WS | cadena──► EN CURSO ──termina, en el finally──► LIBRE
   Con uno EN CURSO: un manual → 409; la corrida semanal y la puesta al día → se omiten con WARN.

 Cadena: [Football-Data, o se saltea sin token] ──termina, aunque falle──► [WhoScored] (un solo EN CURSO)

 Corrida de WhoScored
   fase 1: snapshot de todas las ligas pedidas
   fase 2, por liga: descubrimiento ──► scraping ──► PROCESSED
   bloqueo ──► la liga en curso queda CUT y las que faltan NOT_REACHED; no hay más pedidos

 Última completa exitosa (por fuente)
   NINGUNA ──completa exitosa──► REGISTRADA(fin) ──otra completa exitosa──► REGISTRADA(fin nuevo)
   Una de una liga, una con ligas fallidas (FD) o una cortada (WS) no la cambia.

 Entrada del snapshot: NUEVA ──exportación──► VIGENTE; una vigente nunca cambia (FR-031)
```

## Trazabilidad con el spec

| Requisitos | Elemento del diseño |
|---|---|
| FR-001, FR-002 y FR-008 | `PlayerMatchStats`, `Metric`, `MetricCounts`, la tabla `player_match_stats` y la tabla de métricas de [research.md](./research.md) D8 |
| FR-003 | `WhoScoredTournament` (torneo) y el filtro por fechas de la temporada en `LeagueStatsSync` y `TeamCrossing` |
| FR-004 y FR-005 | `ReportedAppearance.entered()` y `ReportedMatchStats.minutesPlayed` (D9) |
| FR-006 y FR-007 | `ReportedGoal`, `ReportedCard`, `CardKind` y `ReportedMatchStats` (D10) |
| FR-009 | Ningún endpoint ni `Response` expone estadísticas ni ids de WhoScored. |
| FR-010, FR-011 y FR-012 | `Match.needsStats()`, `CurrentSeasons` y `LeagueStatsSync.pendingMatches()` |
| FR-013 y SC-008 | `MatchStatsLoad` y `StatsWriteService.saveMatchStats`, una transacción por partido (D18) |
| FR-014 y FR-015 | `LeagueStatsSync.matchCrosses()`, `closeDiscovery()` y `prepare`, con `StatsSkipReason` (D13) |
| FR-016, FR-017, FR-017a, FR-018, SC-003 y SC-003a | `TeamCrossing`, `NormalizedName`, `Team.crossWith` y `ux_teams_whoscored_id` (D12) |
| FR-019, FR-019a, FR-020, FR-021, FR-022 y SC-002 | `PlayerCrossing`, `CatalogPlayers`, `Player.crossWith`, `Player.ageOn` y `ux_players_whoscored_id` (D14) |
| FR-023, FR-029 y FR-032 | `StatsSnapshot.of`, el formato de [contracts/snapshot-format.md](./contracts/snapshot-format.md) y `backend/snapshot/whoscored-stats.jsonl`. La base sigue en `backend/data/`, que está en `.gitignore`. |
| FR-024, FR-025, FR-026 y SC-006 | `SnapshotLoad` y la fase 1 de `StatsSyncService` (D18 y D20) |
| FR-027 | `StatsSnapshotRepository.read()`, `SnapshotRead` y el WARN de `StatsSyncService` |
| FR-028, FR-030, FR-031 y SC-014 | `StatsSnapshotExport`, la tarea `exportStatsSnapshot`, `StatsSnapshotService.export()` y `StatsSnapshot.merge` (D21) |
| FR-033 | `SyncScheduler.runWeeklySync()` → `SyncCoordinator.runWeeklyChain()` |
| FR-034, FR-035, SC-011 y SC-012 | `StartupSync`, `SyncCoordinator.catchUpOnStartup()` y `CatchUpPolicy` (D22) |
| FR-036, FR-038 y FR-040 | La cadena de `SyncCoordinator`; cada controller llama solo a su servicio; `StatsSyncService` no usa la credencial |
| FR-037, FR-039 y SC-017 | `StatsSyncController`, la regla de `SecurityConfig` y `League.fromName` (D24) |
| FR-041, FR-052 y SC-013 | `SyncLock`, compartido por `SyncService`, `StatsSyncService` y `SyncCoordinator` |
| FR-042, FR-043, FR-044, FR-045 y SC-007 | `WhoScoredClient`, `WhoScoredSleeper`, `SourceBlockedException` y `StatsSyncRun.cut` (D6 y D18) |
| FR-046 y SC-016 | Ni el catálogo ni la sincronización con Football-Data usan el adapter de WhoScored, y `SourceBlockedException` nunca sale de `StatsSyncService`. |
| FR-047, FR-054 y FR-055 | `LastFullSync`, `last_full_syncs`, `SyncReport.lastFullSync()` y `StatsSyncReport.lastFullSync()` (D23); los tests de D26 |
| FR-048, FR-049 y SC-015 | `StatsSyncReport`, `LeagueStatsResult`, `StatsSyncReportLogger` y `StatsSyncReportResponse` (D16) |
| FR-050 y FR-051 | `CatchUpPolicy` reemplaza la regla del catálogo vacío de `StartupSync` y recupera la corrida perdida. |
| FR-053 | `SyncOrigin.CATCH_UP` |
| SC-001, SC-004, SC-005, SC-009 y SC-010 | `LeagueStatsSync` (pendientes y omitidos), el descubrimiento de D11, el volumen de D27 y `Match.statsLoaded` |
