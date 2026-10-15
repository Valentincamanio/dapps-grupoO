# Modelo de datos: Cotización de jugadores y estrategias de valuación

**Rama**: `feature/cotizacion-estrategias` | **Fecha**: 2026-10-15 | **Plan**: [plan.md](./plan.md) |
**Investigación**: [research.md](./research.md)

Este documento fija el modelo (`modelo/strategy/` y `modelo/quote/`), la persistencia, las
excepciones, la entrada que se espera de la 006, las transiciones de estado y la trazabilidad de
cada regla a la clase que la decide. Las referencias `D#` son de `research.md`.

## 1. Vista general

```text
Strategy (id = id de la v1)  1 ──── n  StrategyVersion  1 ──── n  WeightSet (por defecto + hasta 4 por posición)
                                            │ 1
                                            │
                                            │ n
Player (existente) 1 ──── n  Quote  ────────┘          (una cotización apunta a la versión que la calculó)
                             │
                             └── week: QuoteWeek   type: INITIAL | CALCULATED | REQUOTED

PlayerMatchStats (006) ──► WeeklyPerformance ──► MetricNormalization ──► score ──► StrategyVersion.valueFor ──► Quote
Match (005/006)         ──► QuotingPlan (qué semanas se cotizan)
```

- **Modelo único**: `Quote` referencia al jugador por id y a la versión por una referencia liviana
  (`StrategyVersionRef`) para no cargar 135.000 jugadores y equipos al leer el historial. Las
  clases de otros contextos (`Player`, `Position`, `League`, `Match`, `PlayerMatchStats`) se usan
  tal cual.
- **Nada de `modelo/` importa Spring ni JPA**; solo el JDK.

## 2. Modelo: contexto `strategy`

### 2.1 `ValuationMetric` y `MetricDirection`

Enum de las 19 métricas (D5) con su sentido. `fromName(String)` resuelve el nombre del request y
lanza `UnknownMetricException` con el valor recibido.

| Constante | `MetricDirection` |
|---|---|
| `GOALS`, `ASSISTS`, `SHOTS`, `SHOTS_ON_TARGET`, `KEY_PASSES`, `SUCCESSFUL_DRIBBLES`, `TACKLES`, `INTERCEPTIONS`, `CLEARANCES`, `AERIALS_WON`, `PASSES`, `MINUTES_PLAYED`, `RATING`, `PASS_ACCURACY`, `TEAM_RESULT`, `CLEAN_SHEETS` | `HIGHER_IS_BETTER` |
| `FOULS_COMMITTED`, `YELLOW_CARDS`, `RED_CARDS` | `LOWER_IS_BETTER` |

### 2.2 `WeightSet`

`record WeightSet(Map<ValuationMetric, BigDecimal> weights)`. Inmutable (copia el mapa en el
constructor compacto).

| Invariante | Excepción |
|---|---|
| Cada peso es ≥ 0 | `InvalidWeightsException` ("El peso de {métrica} no puede ser negativo.") |
| Cada peso tiene a lo sumo 2 decimales (`stripTrailingZeros().scale() <= 2`) | `InvalidWeightsException` |
| La suma es exactamente 1,00 (`compareTo`) | `InvalidWeightsException` ("Los pesos de {juego} deben sumar 1,00 y suman {suma}.") |
| Los nombres vienen de `ValuationMetric` | `UnknownMetricException` (al construir desde `Map<String, BigDecimal>`) |

- `of(String label, Map<String, BigDecimal>)`: construye desde el request. `label` identifica el
  juego en el mensaje ("por defecto", "delanteros").
- `weight(ValuationMetric)`: el peso, o `0` si la métrica no figura.
- `usedMetrics()`: las métricas con peso > 0 (para la optimización de D6).

### 2.3 `StrategyVersion`

```java
record StrategyVersion(Long id, int version, BigDecimal scaleFactor, BigDecimal minimumValue,
                       WeightSet defaultWeights, Map<Position, WeightSet> positionWeights, Instant createdAt)
```

Inmutable. Constantes con nombre: `DEFAULT_SCALE_FACTOR = 10`, `DEFAULT_MINIMUM_VALUE = 10,00`,
`MIN_ALLOWED_MINIMUM = 0,01`.

| Invariante | Excepción |
|---|---|
| `scaleFactor > 0` | `InvalidScaleFactorException` ("El factor de escala debe ser mayor que 0.") |
| `minimumValue >= 0,01` | `InvalidMinimumValueException` ("La cotización mínima debe ser al menos 0,01.") |
| `defaultWeights` obligatorio | `InvalidWeightsException` ("Los pesos por defecto son obligatorios.") |

| Método | Decide |
|---|---|
| `weightsFor(Position)` | los pesos de la posición si los tiene, y si no los por defecto (FR-023) |
| `scoreFor(Position, Map<ValuationMetric, BigDecimal>)` | `Σ peso × métrica`, escala 6 `HALF_UP` una sola vez (D4) |
| `valueFor(BigDecimal base, BigDecimal score)` | `max(mínimo, base + score × factor)`, 2 decimales `HALF_UP` una sola vez (FR-024) |
| `usedMetrics(Position)` | las métricas a normalizar para esa posición |
| `ref()` | `StrategyVersionRef(versionId, strategyId, name, version)` |

### 2.4 `Strategy` y `StrategyCatalog`

`record Strategy(Long id, String name, List<StrategyVersion> versions)`: `id` es el de su versión 1;
`latest()` devuelve la última versión; `nameKey()` el nombre sin espacios de los extremos y en
minúsculas.

`StrategyCatalog` es el agregado (D13). Se construye con todas las estrategias y el id de la
versión activa, y **no tiene setters**: cada operación devuelve un `StrategyChange` (el catálogo
nuevo más qué versiones hay que insertar y qué marca `active` hay que mover).

| Operación | Regla | Excepción |
|---|---|---|
| `create(name, factor?, minimum?, defaultWeights, positionWeights)` | Nombre obligatorio y único sin distinguir mayúsculas ni espacios de los extremos. Crea la versión 1, **inactiva**. Factor y mínimo omitidos → los por defecto | `InvalidStrategyNameException` (400), `DuplicateStrategyNameException` (409) |
| `newVersion(strategyId, factor?, minimum?, defaultWeights, positionWeights)` | Crea la versión siguiente (n+1). El nombre no se edita. Factor y mínimo omitidos → **los de la versión anterior**. Si la estrategia es la activa, la versión nueva pasa a ser la activa; si no, queda inactiva | `StrategyNotFoundException` (404) |
| `activate(strategyId)` | Deja activa la **versión más reciente** de esa estrategia y desactiva la anterior. Activar la ya activa no cambia nada | `StrategyNotFoundException` (404) |
| `activeVersion()` | La única versión activa (FR-030). El constructor exige que haya exactamente una | `StrategyInvariantException` |
| `isActive(strategyId)` | Si esa estrategia es la activa (decide si la opción de recotización aplica) | |

No existe borrar, desactivar sin activar otra ni activar una versión vieja (FR-030, Fuera de
Alcance).

### 2.5 `StartingStrategies`

Las dos estrategias de arranque (D13) como datos puros: nombre, factor, mínimo y pesos, construidos
con `WeightSet`. `StrategySeeder` las pasa al catálogo. Los tests verifican las sumas, que B difiere
en al menos dos posiciones y que incluye disciplina y contexto (FR-031).

### 2.6 `RequoteTiming`

`enum RequoteTiming { NOW("AHORA"), NEXT_WEEK("PROXIMA_SEMANA") }`. `fromWire(String)`: `null` →
`NEXT_WEEK` (FR-034); un valor desconocido → `InvalidRequoteTimingException`.

## 3. Modelo: contexto `quote`

### 3.1 `QuoteWeek`

`record QuoteWeek(LocalDate monday)` con la zona `America/Argentina/Buenos_Aires` como constante
(D3). Invariante: `monday` cae en lunes.

| Método | Resultado |
|---|---|
| `containing(Instant)` | la semana en cuyo intervalo cae el instante, en hora de Argentina |
| `start()` / `end()` | `Instant` del lunes 00:00 y del lunes 00:00 siguiente |
| `next()` / `previous()` | la semana siguiente o anterior |
| `isClosedAt(Instant)` | `instante >= end()` |
| `lastClosedAt(Instant)` | la última semana cerrada al instante |
| `compareTo` | orden cronológico |

### 3.2 `Quote`, `QuoteType` y `QuoteVariation`

```java
record Quote(Long id, long playerId, QuoteWeek week, Instant validFrom, BigDecimal value,
             BigDecimal baseValue, BigDecimal score, QuoteType type, StrategyVersionRef strategy)
```

Inmutable, sin operaciones de cambio (FR-005).

| Invariante | |
|---|---|
| `value` tiene escala 2 y es ≥ el mínimo de su estrategia (lo garantiza `valueFor`; el constructor exige `value > 0`) | `QuoteInvariantException` |
| `score` tiene escala 6 y está entre −1 y +1 | `QuoteInvariantException` |
| `INITIAL` no tiene `baseValue`; `CALCULATED` y `REQUOTED` sí | `QuoteInvariantException` |
| `validFrom` de una `INITIAL` es `week.start()`; de una `CALCULATED`, `week.end()` | `QuoteInvariantException` |

| Método | Resultado |
|---|---|
| `tokenPrice()` | `value / 100`, escala 4 |
| `variation()` | `Optional<QuoteVariation>`: vacío en la `INITIAL`; si no, `amount = value − baseValue` y `percent = amount / baseValue × 100` a 2 decimales `HALF_UP` |
| `isCurrentAt(Instant)` | `validFrom <= instante` |

`QuoteType`: `INITIAL`, `CALCULATED`, `REQUOTED`.

### 3.3 `WeeklyPerformance`

`record WeeklyPerformance(Player player, Map<ValuationMetric, BigDecimal> values)`. `values` solo
contiene las métricas **calculables**; la ausencia significa "no se puede calcular" (D5).

`WeeklyPerformance.of(Player, List<PlayerMatchStats> weekStats)` es el **único lugar** que conoce los
nombres de la 006 (sección 5):

- 15 métricas de suma (12 más-es-mejor y 3 invertidas): siempre presentes (0 si no jugó).
- `RATING`: `Σ(rating × minutos) / Σ minutos` con los partidos con rating y minutos > 0; ausente si no
  hay ninguno.
- `PASS_ACCURACY`: `Σ pases precisos / Σ pases`, escala 6; ausente si `Σ pases = 0`.
- `TEAM_RESULT`: promedio de 1, 0,5 y 0 (victoria, empate, derrota) sobre los partidos que jugó y
  donde se resuelve el lado; ausente si ninguno.
- `CLEAN_SHEETS`: cantidad de partidos que jugó y donde se resuelve el lado con el rival en 0;
  ausente si ninguno es resoluble.

El lado se resuelve comparando `player.team()` con `match.homeTeam()` y `match.awayTeam()` (por
`externalId`). Si no es ninguno, o el partido no tiene resultado, no se resuelve.

### 3.4 `MetricNormalization`

Función pura (D6): `normalize(Map<Long, BigDecimal> valuesByPlayer, MetricDirection)` devuelve
`Map<Long, BigDecimal>` con la métrica entre −1 y +1 (escala 6) de cada jugador que tiene la métrica.
Los que no la tienen no figuran (el score los toma como 0). Recibe el grupo ya filtrado a pares de la
misma posición.

### 3.5 `WeeklyQuotation`

El caso de uso del modelo: dada una semana, una estrategia y los datos, decide qué cotizaciones
nacen. **No lee nada**: los repositories entregan los datos y el servicio guarda el resultado.

```java
List<Quote> quoteWeek(QuoteWeek week, StrategyVersion strategy, List<Player> activePlayers,
                      List<PlayerMatchStats> weekStats, Map<Long, Quote> lastBeforeWeek)
List<Quote> requoteWeek(QuoteWeek week, StrategyVersion strategy, List<Player> activePlayers,
                        List<PlayerMatchStats> weekStats, Map<Long, Quote> lastBeforeWeek,
                        Map<Long, Quote> latestOfWeek, Instant now)
```

`quoteWeek`, por cada jugador activo:

1. Agrupa por posición, arma `WeeklyPerformance` y normaliza cada métrica con peso.
2. Calcula el score con los pesos de la posición.
3. La base es `lastBeforeWeek[jugador].value`, o 100,00 si no tiene (D9).
4. Si no tiene ninguna cotización, genera además la `INITIAL` (vigente desde `week.start()`); la
   `CALCULATED` rige desde `week.end()` (D10).
5. El resultado no depende del orden de la lista de jugadores ni de las estadísticas (FR-026).

`requoteWeek` genera `REQUOTED` solo para los jugadores activos con una cotización en esa semana
cuya estrategia **no** es la versión nueva (D12), con `validFrom = now`, la base previa a la semana
y el mismo `baseValue` que la calculada que reemplaza. La lógica de score es la de `quoteWeek`, para
que simular y recotizar den lo mismo.

### 3.6 `QuotingPlan`

`QuotingPlan.of(Optional<QuoteWeek> lastQuoted, Optional<QuoteWeek> firstWithStats, List<Match>
finishedMatches, Instant now)` devuelve la lista ordenada de `WeekDecision`:

| `WeekDecision` | Significado |
|---|---|
| `QUOTE(week)` | se cotiza |
| `PENDING(week, reason)` | no se cotiza; `reason` es `MISSING_STATS` o `PREVIOUS_WEEK_PENDING` |

Reglas en D11. Constante `STATS_WAIT = Duration.ofDays(7)`. Métodos: `weeksToQuote()`,
`pendingWeeks()`, `hasClosedUnquotedWeeks()` (para decidir si el arranque dispara el recálculo).

### 3.7 `RecalculationReport`, `PendingWeek` y `RecalculationOrigin`

`RecalculationReport` (inmutable): `origin`, `startedAt`, `finishedAt`, la `StrategyVersionRef`
usada, `List<WeekResult>` (semana, cantidad de `INITIAL`, de `CALCULATED` y de `REQUOTED`),
`List<PendingWeek>` y, si una semana falló al guardarse, cuál fue (`interruptedAt`): de esa semana no
queda nada y la siguiente corrida la reintenta. El disparo manual responde igual con 200 y el informe,
como la sincronización de la 005 con una liga fallida. `isEmpty()` es "sin semanas para cotizar" (no
es un error, FR-052). Un `RecalculationReport.Builder` va acumulando mientras corre
la corrida.

`RecalculationOrigin`: `WEEKLY`, `STARTUP`, `MANUAL`, `STRATEGY_CHANGE`. (`CHAIN` se suma cuando
exista el punto P-3 de D18.)

### 3.8 Modelos de lectura

| Tipo | Contenido |
|---|---|
| `QuotePage<T>` | `content`, `page`, `size`, `totalElements`, `totalPages`, `hasPrevious`, `hasNext` (misma regla que `PlayerPage`) |
| `RankedQuote` | `Player player`, `Quote quote` (la última de un jugador activo) |
| `RankingPage` | `QuotePage<RankedQuote>` más la `StrategyVersionRef` activa |
| `SimulatedQuote` | `Player player`, `BigDecimal currentValue` (nulo si no tiene), `BigDecimal simulatedValue`; `difference()` |
| `SimulationResult` | `QuotePage<SimulatedQuote>`, la `StrategyVersionRef` usada (o nula si fueron parámetros), `weeksSimulated`, `pendingWeeks` |
| `QuoteDay` | `parse(String)` → `LocalDate` o `InvalidQuoteDateException`; `requireNotFuture(LocalDate, LocalDate hoy)` → `FutureQuoteDateException`; `requireOrdered(from, to)` → `InvalidDateRangeException` |

## 4. Excepciones de dominio

Todas con nombre, mensaje en español y heredan de las bases de `shared/` (que el advice único ya
traduce). **No se suma ningún status code.**

| Excepción | Base | Status | Mensaje (ejemplo) | Se lanza en |
|---|---|---|---|---|
| `UnknownMetricException` | `BadRequestException` | 400 | "La métrica 'GOLES' no existe." | `ValuationMetric.fromName` |
| `InvalidWeightsException` | `BadRequestException` | 400 | "Los pesos de delanteros deben sumar 1,00 y suman 0,95." | `WeightSet` |
| `InvalidScaleFactorException` | `BadRequestException` | 400 | "El factor de escala debe ser mayor que 0." | `StrategyVersion` |
| `InvalidMinimumValueException` | `BadRequestException` | 400 | "La cotización mínima debe ser al menos 0,01." | `StrategyVersion` |
| `InvalidStrategyNameException` | `BadRequestException` | 400 | "El nombre de la estrategia es obligatorio." | `StrategyCatalog` |
| `InvalidRequoteTimingException` | `BadRequestException` | 400 | "La opción 'AHORA2' no es válida: use AHORA o PROXIMA_SEMANA." | `RequoteTiming` |
| `InvalidSimulationRequestException` | `BadRequestException` | 400 | "Indique una estrategia guardada o parámetros, pero no las dos cosas ni ninguna." | `QuoteSimulationService` |
| `InvalidQuoteDateException` | `BadRequestException` | 400 | "La fecha '2026-13-45' no es válida: use el formato AAAA-MM-DD." | `QuoteDay` |
| `FutureQuoteDateException` | `BadRequestException` | 400 | "La fecha 2027-01-01 es futura." | `QuoteDay` |
| `InvalidDateRangeException` | `BadRequestException` | 400 | "El inicio del rango (2026-09-01) es posterior al final (2026-08-01)." | `QuoteDay` |
| `PlayerNotFoundException` | `NotFoundException` | 404 | (existente) "No existe un jugador con id 9." | `QuoteQueryService` |
| `PlayerWithoutQuoteException` | `NotFoundException` | 404 | "El jugador 9 no tiene cotización." | `QuoteQueryService` |
| `QuoteNotFoundAtDateException` | `NotFoundException` | 404 | "El jugador 9 no tiene cotización al 2026-08-16." | `QuoteQueryService` |
| `StrategyNotFoundException` | `NotFoundException` | 404 | "No existe una estrategia con id 7." | `StrategyCatalog`, `QuoteSimulationService` |
| `DuplicateStrategyNameException` | `ConflictException` | 409 | "Ya existe una estrategia con el nombre 'Equilibrada'." | `StrategyCatalog` |
| `RecalculationInProgressException` | `ConflictException` | 409 | "Ya hay un recálculo en curso." | `RecalculationLock` |
| `RecalculationBlockedBySyncException` | `ConflictException` | 409 | "No se puede recalcular: hay una sincronización en curso." | `RecalculationLock` |
| `StrategyChangeConflictException` | `ConflictException` | 409 | "La estrategia cambió mientras se guardaba: reintente." | `StrategyChangeWriter` (violación de integridad) |
| `QuoteInvariantException`, `StrategyInvariantException` | `RuntimeException` | 500 | no se muestra (error de programación) | invariantes internas |

## 5. Entrada esperada de la 006

Esta feature **no crea ni modifica la entidad de estadísticas**. Consume `PlayerMatchStats` y
`Match` de la 006 (rama `feature/scraping-whoscored`, **todavía no integrada en `develop`**).

### 5.1 Qué campos usa (los del FR-002 de la 006)

| Campo de la 006 (en `PlayerMatchStats`) | Tipo | Lo usa para |
|---|---|---|
| `player()` | `Player` (con `team` y `position`) | de qué jugador es y su posición (los pares) |
| `match()` | `Match` | la semana (`utcDate`), el lado del jugador (`homeTeam`, `awayTeam`), el resultado (`winner`, `fullTime`) |
| `minutesPlayed()` | `int` ≥ 0 | `MINUTES_PLAYED`, el peso del rating, si "jugó" |
| `counts().get(Metric.GOALS)` | `int` | `GOALS` |
| `counts().get(Metric.ASSISTS)` | `int` | `ASSISTS` |
| `counts().get(Metric.SHOTS)` | `int` | `SHOTS` |
| `counts().get(Metric.SHOTS_ON_TARGET)` | `int` | `SHOTS_ON_TARGET` |
| `counts().get(Metric.KEY_PASSES)` | `int` | `KEY_PASSES` |
| `counts().get(Metric.PASSES)` | `int` | `PASSES` y el denominador de `PASS_ACCURACY` |
| `counts().get(Metric.ACCURATE_PASSES)` | `int` | el numerador de `PASS_ACCURACY` |
| `counts().get(Metric.SUCCESSFUL_DRIBBLES)` | `int` | `SUCCESSFUL_DRIBBLES` |
| `counts().get(Metric.TACKLES)` | `int` | `TACKLES` |
| `counts().get(Metric.INTERCEPTIONS)` | `int` | `INTERCEPTIONS` |
| `counts().get(Metric.CLEARANCES)` | `int` | `CLEARANCES` |
| `counts().get(Metric.AERIALS_WON)` | `int` | `AERIALS_WON` |
| `counts().get(Metric.FOULS_COMMITTED)` | `int` | `FOULS_COMMITTED` |
| `counts().get(Metric.YELLOW_CARDS)` | `int` | `YELLOW_CARDS` |
| `counts().get(Metric.RED_CARDS)` | `int` | `RED_CARDS` |
| `rating()` | `BigDecimal` o `null` (escala 2, 0 a 10) | `RATING` |

Y de `Match`: `status()` (solo `FINISHED`), `utcDate()`, `homeTeam()`, `awayTeam()`,
`winner()` (`HOME_TEAM`, `AWAY_TEAM`, `DRAW`), `fullTime()` y `needsStats()` /
`statsLoaded` (para saber si a un partido terminado todavía le faltan estadísticas).

**No usa**: `starter`, `manOfTheMatch`, `id` de la estadística.

### 5.2 Qué supone

- Hay a lo sumo una fila por (jugador, partido) (índice único de la 006).
- Solo hay filas de quien entró a la cancha (FR-004 de la 006); quien no entró no tiene fila.
- Los conteos no son negativos y el rating, si existe, está entre 0 y 10.
- La estadística de un partido terminado se guarda de una vez y **nunca cambia** (FR-012 de la 006).
- `PlayerMatchStats.player().team()` es el **equipo actual** del jugador, no el que tenía en el
  partido (la 006 no guarda el equipo de cada partido). Por eso el lado se resuelve comparando
  contra el local y el visitante (D5).

### 5.3 Qué hay que agregarle a la 006 (puntos P-1 de D18)

```java
// PlayerMatchStatsRepository
List<PlayerMatchStats> findAllByMatchBetween(Instant fromInclusive, Instant toExclusive);
// MatchRepository
List<Match> findAllFinished();
```

Son lecturas nuevas; ninguna firma existente cambia. Los repositories deben traer las asociaciones
con `@EntityGraph`, como `findAll()` hoy.

### 5.4 Qué cambia si la 006 nombra distinto

El resto del modelo trabaja con `ValuationMetric`; los nombres de la 006 solo los lee
`WeeklyPerformance.of`. Si la 006 renombra un campo, cambia esa clase y su test
(`WeeklyPerformanceTest`) y nada más. Si la 006 **quita** una métrica, la correspondiente de
`ValuationMetric` queda como "no calculable" (peso efectivo 0 en la comparación), y los pesos de las
estrategias que la usan siguen validando. Si la 006 **suma** una métrica, esta feature la ignora
hasta que el equipo decida sumarla a `ValuationMetric`.

| Si la 006... | Se toca |
|---|---|
| Renombra `Metric.TACKLES` | `WeeklyPerformance.of` y su test |
| Cambia `minutesPlayed` de `int` a `Integer` | `WeeklyPerformance.of` y el builder de test |
| Cambia el rating a otra escala | `WeeklyPerformance.of` y su test |
| Guarda el equipo del jugador en cada partido | `WeeklyPerformance` (resolver el lado con ese equipo) y se simplifica D5 |
| Renombra `Match.needsStats()` | `QuotingPlan` y sus tests |

### 5.5 Builder de test

`PlayerMatchStatsBuilder` (en `src/test/.../modelo/quote/`) arma un `PlayerMatchStats` con valores
por defecto en 0 y métodos `goals(2)`, `tackles(3)`, `minutes(90)`, `rating("7.2")`, `forPlayer(...)`,
`inMatch(...)`. Ningún test de esta feature construye `PlayerMatchStats` a mano ni usa la entidad
`PlayerMatchStatsSQL`, salvo los IT de los dos repositories nuevos. Si la 006 cambia el constructor,
se arregla un solo archivo.

## 6. Persistencia

Cada clase de modelo tiene su `*SQL` y su mapper; las anotaciones de JPA viven solo en las `*SQL`.
Nombres de tabla explícitos (Principio VI). Sin SQL nativo.

### 6.1 `strategies` (`StrategySQL`)

| Columna | Tipo | Restricciones |
|---|---|---|
| `id` | `BIGINT` IDENTITY | PK |
| `root_id` | `BIGINT` | FK a `strategies(id)`; `NULL` en la versión 1 |
| `name` | `VARCHAR(100)` | `NOT NULL` |
| `version_number` | `INT` | `NOT NULL`, ≥ 1 |
| `scale_factor` | `NUMERIC(8,2)` | `NOT NULL` |
| `minimum_value` | `NUMERIC(12,2)` | `NOT NULL` |
| `active` | `BOOLEAN` | `NOT NULL`; **exactamente una fila en `true`** (lo garantiza `StrategyCatalog` dentro de una transacción) |
| `created_at` | `TIMESTAMP WITH TIME ZONE` | `NOT NULL` |

Índice único: `ux_strategies_name_version (name, version_number)`.

`StrategySQL` tiene `@OneToMany(mappedBy = "strategy", cascade = ALL)` a `StrategyWeightSQL`. Las
columnas de una fila no se actualizan salvo `active`.

### 6.2 `strategy_weights` (`StrategyWeightSQL`)

| Columna | Tipo | Restricciones |
|---|---|---|
| `id` | `BIGINT` IDENTITY | PK |
| `strategy_id` | `BIGINT` | `NOT NULL`, FK a `strategies(id)` |
| `weight_set` | `VARCHAR(20)` | `NOT NULL`: `DEFAULT`, `GOALKEEPER`, `DEFENDER`, `MIDFIELDER`, `FORWARD` |
| `metric` | `VARCHAR(30)` | `NOT NULL`: nombre de `ValuationMetric` |
| `weight` | `NUMERIC(3,2)` | `NOT NULL`, entre 0,00 y 1,00 |

Índice único: `ux_strategy_weights_set_metric (strategy_id, weight_set, metric)`.
Solo se guardan los pesos > 0 (una métrica sin fila pesa 0).

### 6.3 `quotes` (`QuoteSQL`)

| Columna | Tipo | Restricciones |
|---|---|---|
| `id` | `BIGINT` IDENTITY | PK |
| `player_id` | `BIGINT` | `NOT NULL`, FK a `players(id)` |
| `week_start` | `DATE` | `NOT NULL`: el lunes, en hora de Argentina |
| `valid_from` | `TIMESTAMP WITH TIME ZONE` | `NOT NULL` |
| `quote_value` | `NUMERIC(12,2)` | `NOT NULL` |
| `base_value` | `NUMERIC(12,2)` | nulo en la `INITIAL` |
| `score` | `NUMERIC(10,6)` | `NOT NULL` |
| `quote_type` | `VARCHAR(12)` | `NOT NULL`: `INITIAL`, `CALCULATED`, `REQUOTED` |
| `strategy_id` | `BIGINT` | `NOT NULL`, FK a `strategies(id)` (la fila de la versión) |

Índices:

| Nombre | Columnas | Para qué |
|---|---|---|
| `ux_quotes_player_valid_from` | `(player_id, valid_from)` **único** | una cotización por instante y jugador; la `CALCULATED` única por semana (D10); búsquedas por jugador y fecha |
| `ix_quotes_player_valid_from_desc` | `(player_id, valid_from DESC)` | el pedido del equipo; "la última cotización" (D15, nota de redundancia) |
| `ix_quotes_week_start_type` | `(week_start, quote_type)` | "la última semana calculada" (agregado por este plan) |

`QuoteSQL` no tiene setters. El repository no expone `update` ni `delete`.

### 6.4 Consultas (JPQL o derived, sin `nativeQuery`)

| Repository | Método | Para qué |
|---|---|---|
| `QuoteRepository` | `saveAll(List<Quote>)` | escribir una semana |
| | `findLastBeforeWeek(QuoteWeek)` → `Map<Long, Quote>` | la base de cada jugador (D9): última por `validFrom` con `week_start` < la semana |
| | `findLatestOfWeek(QuoteWeek)` → `Map<Long, Quote>` | la última cotización de cada jugador en una semana (recotizar, D12) |
| | `findLastCalculatedWeek()` → `Optional<QuoteWeek>` | la última semana cotizada |
| | `findCurrent(long playerId)` / `findCurrentOf(Collection<Long>)` | la última por `validFrom` |
| | `findAt(long playerId, Instant)` | la última con `validFrom` < el instante |
| | `findHistory(long playerId, Instant from, Instant to, page, size)` | historial ascendente y paginado |
| | `findRanking(PlayerFilter, page, size)` | JPQL con la última por jugador, `JOIN FETCH` de jugador, equipo y versión, ordenado por valor desc, nombre, id |
| | `findLatestByPlayer()` | la cotización actual de cada jugador (simulación) |
| `StrategyRepository` | `findCatalog()` → `StrategyCatalog` | todas las estrategias con sus versiones y pesos, y la activa |
| | `save(StrategyChange)` | inserta las versiones nuevas y mueve la marca `active` |

## 7. Transiciones de estado

### 7.1 Una semana (la deduce `QuotingPlan`, no se guarda)

```text
abierta ──(llega su cierre)──► cerrada
cerrada ──(sin partidos terminados sin estadísticas)──────────────────► se cotiza
cerrada ──(le faltan estadísticas y pasaron < 7 días)─► pendiente (MISSING_STATS) ─┐
cerrada ──(le faltan estadísticas y pasaron ≥ 7 días)─► se cotiza con lo que haya  │
cerrada ──(hay una semana anterior pendiente)─────────► pendiente (PREVIOUS_WEEK_PENDING)
```

Una semana cotizada no vuelve a cotizarse; solo la última cerrada admite `REQUOTED` (D12).

### 7.2 Las cotizaciones de un jugador

```text
(sin cotizaciones) ──► INITIAL (rige desde el lunes de su primera semana) ──► CALCULATED (rige desde el cierre)
CALCULATED(W) ──► CALCULATED(W+1) ──► ...
CALCULATED(L) ──(recotizar en el acto)──► REQUOTED(L) (rige desde ahora; la CALCULATED(L) se conserva)
REQUOTED(L) ──(otra recotización)──► otra REQUOTED(L) (base previa a L: no acumula)
inactivo ──► no recibe cotizaciones; al volver, la base es su última cotización
```

### 7.3 La estrategia activa

```text
create ──► v1 inactiva
activate(X) ──► la última versión de X pasa a activa; la anterior activa pasa a inactiva
newVersion(X) con X activa ──► v(n+1) activa; v(n) inactiva
newVersion(X) con X inactiva ──► v(n+1) inactiva; la activa no cambia
```

Siempre hay exactamente una versión activa (SC-015).

## 8. Trazabilidad: dónde se decide cada regla

| Requisito | Decide | Prueba |
|---|---|---|
| FR-001, FR-002 (escalas, precio por token) | `Quote.tokenPrice`, `StrategyVersion.valueFor` | `QuoteTest`, `StrategyVersionTest` |
| FR-003, FR-004, FR-009 (campos, tipos, vigencias) | `Quote`, `WeeklyQuotation` | `QuoteTest`, `WeeklyQuotationTest` |
| FR-005 (inmutables) | `Quote` (record), `QuoteRepository` sin `update` | `QuoteTest`, `QuoteRepositoryIT` |
| FR-006 (sin estadísticas ni score en las respuestas) | `QuoteResponse` (sin esos campos) | `QuoteControllerIT` |
| FR-007 (semana) | `QuoteWeek` | `QuoteWeekTest` |
| FR-008, FR-010, FR-011 (semanas cerradas, en orden, 7 días) | `QuotingPlan` | `QuotingPlanTest` |
| FR-012 (semana completa o nada) | `QuoteWeekWriter.quoteWeek` (`@Transactional`) | `QuoteWeekWriterIT` |
| FR-013, FR-014, FR-015 (quiénes se cotizan, inactivos, iniciales) | `WeeklyQuotation` | `WeeklyQuotationTest` |
| FR-016, FR-021, FR-022 (métricas y no calculables) | `WeeklyPerformance`, `ValuationMetric` | `WeeklyPerformanceTest` |
| FR-017 a FR-020 (pares, posición relativa, empates, inversión) | `MetricNormalization` | `MetricNormalizationTest` |
| FR-023 a FR-026 (score, valor, mínimo, determinismo) | `StrategyVersion`, `WeeklyQuotation` | `StrategyVersionTest`, `WeeklyQuotationTest` |
| FR-027, FR-028 (estrategia y validaciones) | `WeightSet`, `StrategyVersion`, `StrategyCatalog` | `WeightSetTest`, `StrategyVersionTest`, `StrategyCatalogTest` |
| FR-029, FR-030 (versiones, activa única) | `StrategyCatalog` | `StrategyCatalogTest`, `StrategyRepositoryIT` |
| FR-031 (semilla) | `StartingStrategies`, `StrategySeeder` | `StartingStrategiesTest`, `StrategySeederTest`, `StrategySeederIT` |
| FR-032, FR-053, FR-054 (solo ADMIN) | `SecurityConfig` | `QuoteAdminAccessIT`, `StrategyAccessIT` |
| FR-033 a FR-035 (opción de recotizar) | `RequoteTiming`, `StrategyChangeService` | `RequoteTimingTest`, `StrategyChangeServiceTest` |
| FR-036, FR-037 (recotizar, no acumular) | `WeeklyQuotation.requoteWeek` | `WeeklyQuotationTest`, `RequoteIT` |
| FR-038, FR-039 (409 sin efecto; activa de la corrida) | `RecalculationLock`, `StrategyChangeService`, `QuoteRecalculationService` | `RecalculationLockTest`, `StrategyChangeServiceTest`, `StrategyChangeIT` |
| FR-040 a FR-046 (consultas) | `QuoteQueryService`, `QuoteRepository`, `QuoteDay` | `QuoteQueryServiceTest`, `QuoteControllerIT`, `RankingRoutingIT` |
| FR-047 a FR-052 (recálculo) | `QuoteRecalculationService`, `QuoteScheduler`, `QuoteStartupRecalculation` | `QuoteRecalculationServiceTest`, `QuoteSchedulerTest`, `QuoteStartupRecalculationTest`, `RecalculationIT` |
| FR-054 a FR-057 (simulación) | `QuoteSimulationService`, `WeeklyQuotation` | `QuoteSimulationServiceTest`, `SimulationIT` |
| FR-058 a FR-070 (aplicación web) | `pages/`, `components/`, `hooks/` | ver `plan.md`, sección de tests del frontend |
| FR-071, FR-072 (sin compra; tests de la 004) | restricciones de D24 y D25 | `NoMarketActions.test.tsx` (nuevo) y la suite de la 004 sin tocar |

Los servicios no deciden nada del dominio: `QuoteRecalculationService`, `QuoteWeekWriter`,
`StrategyChangeService` y `QuoteSimulationService` resuelven ids, piden datos al repository,
delegan en el modelo y guardan.

## 9. Respuestas HTTP (resumen; el contrato completo está en `contracts/`)

| DTO (en `controller/<contexto>/dto/`) | Campos |
|---|---|
| `QuoteResponse` | `value`, `tokenPrice`, `variation` (`{amount, percent}` o ausente), `weekStart`, `validFrom`, `type`, `strategy` (`{id, name, version}`) |
| `PlayerQuoteResponse` | `QuoteResponse` más `playerId` (lo usa el lote de la pizarra) |
| `QuoteHistoryPageResponse` | página de `QuoteResponse` más `playerId` |
| `RankingRowResponse` | `playerId`, `name`, `position`, `team`, `league`, `teamCrest` y `QuoteResponse` |
| `RankingPageResponse` | página de `RankingRowResponse` más `activeStrategy` |
| `RecalculationReportResponse` | `origin`, `startedAt`, `finishedAt`, `strategy`, `quotedWeeks[]` (`weekStart`, `initial`, `calculated`, `requoted`), `pendingWeeks[]` (`weekStart`, `reason`), `nothingToQuote`, `interruptedAt` (opcional) |
| `StrategyResponse` | `id`, `name`, `active`, `versions[]` (`version`, `active`, `scaleFactor`, `minimumValue`, `defaultWeights`, `positionWeights`, `createdAt`) |
| `StrategyChangeResponse` | `strategy` y `requotation` (informe o `null`) |
| `SimulationPageResponse` | página de filas (`playerId`, `name`, `team`, `league`, `position`, `currentValue`, `simulatedValue`, `difference`), `strategy`, `weeksSimulated`, `pendingWeeks[]` |
| Requests | `CreateStrategyRequest`, `NewVersionRequest`, `ActivateStrategyRequest`, `SimulationRequest` (Bean Validation en cada uno; los pesos llegan como `Map<String, BigDecimal>`) |

Los importes salen como números JSON con su escala (`103.00`, `1.0300`). **Ninguna respuesta lleva
estadísticas crudas, métricas normalizadas ni el score** (FR-006).

## 10. Tipos del frontend (`frontend/src/types/quotes.ts`)

Escritos a mano a partir de `contracts/*.yaml`, con los enums tal cual el backend (D24).

```text
QUOTE_TYPES   = ['INITIAL', 'CALCULATED', 'REQUOTED']          → QuoteType
METRICS       = [las 19 de ValuationMetric]                     → Metric
REQUOTE_TIMINGS = ['AHORA', 'PROXIMA_SEMANA']                   → RequoteTiming
PENDING_REASONS = ['MISSING_STATS', 'PREVIOUS_WEEK_PENDING']    → PendingReason

StrategyRef      { id, name, version }
Variation        { amount, percent }
Quote            { value, tokenPrice, variation?, weekStart, validFrom, type, strategy }
PlayerQuote      = Quote & { playerId }
QuoteHistoryPage = Page<Quote> & { playerId }
RankingRow       = { playerId, name, position, team, league, teamCrest?, quote: Quote }
RankingPage      = Page<RankingRow> & { activeStrategy }
WeightMap        = Partial<Record<Metric, number>>
StrategyVersionDto { version, active, scaleFactor, minimumValue, defaultWeights, positionWeights, createdAt }
StrategyDto      { id, name, active, versions }
StrategyInput    { scaleFactor?, minimumValue?, defaultWeights, positionWeights? }
RecalculationReport { origin, startedAt, finishedAt, strategy, quotedWeeks, pendingWeeks, nothingToQuote }
StrategyChange   { strategy, requotation: RecalculationReport | null }
SimulationRequest { strategyId?, parameters? }
SimulationPage   = Page<SimulationRow> & { strategy, weeksSimulated, pendingWeeks }
```

Tipos propios de la interfaz: `QuoteRange { from?, to? }`, `ChartPoint { week, value, quote }`,
`WeightsFormState` (texto por métrica y por juego), `QuoteBadge` (`loading`, `none`, `ready`,
`error`).

## 11. Configuración

Propiedades nuevas (detalle en [contracts/configuration.md](./contracts/configuration.md)):

| Propiedad | Por defecto | `test` |
|---|---|---|
| `futbolmarket.quotes.recalculation.cron` | `0 0 6 * * MON` | `"-"` |
| `futbolmarket.quotes.recalculation.zone` | `America/Argentina/Buenos_Aires` | (igual) |
| `futbolmarket.quotes.recalculation.on-startup` | `true` | `false` |
