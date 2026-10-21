---

description: "Lista de tareas de la feature 007: cotización de jugadores y estrategias de valuación"
---

# Tareas: Cotización de jugadores y estrategias de valuación

**Entrada**: documentos de diseño de `/specs/007-cotizacion-estrategias/`

**Prerrequisitos**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md),
[data-model.md](./data-model.md), [contracts/](./contracts/), [quickstart.md](./quickstart.md)

**Tests**: SÍ. La constitución (Principio IV, NO NEGOCIABLE) exige unitarios de dominio, de servicio,
de integración contra H2 y end to end con MockMvc; en el frontend, Vitest + RTL + MSW. **Sin TDD**:
cada test se escribe junto con (después de) la implementación de su tarea, y al cerrar la tarea el
test existe y pasa. Convenciones: casos felices y de borde, AssertJ, setup / execute / verify en tres
bloques, nombres descriptivos (`devuelveNotFoundCuandoElJugadorNoExiste`, `it("muestra ...")`).

**Organización**: por historia de usuario (US1 a US8, en el orden de prioridad del spec), para que
cada una se implemente y se pruebe por separado.

## Formato: `[ID] [P?] [Historia] Descripción`

- **[P]**: se puede hacer en paralelo (archivos distintos, sin dependencias pendientes)
- **[US#]**: historia de usuario a la que pertenece la tarea
- Cada descripción lleva la ruta exacta del archivo

## Convenciones de rutas

- Backend, producción: `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/`
- Backend, tests: `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/` (replica capa y contexto;
  los end to end van en `e2e/`)
- Frontend: `frontend/src/<tipo>/` y `frontend/tests/<tipo>/` (sin carpetas nuevas)
- Toda clase de `modelo/` usa solo el JDK (ni Spring ni JPA). Mensajes de error en español;
  identificadores en inglés.

## Reglas que valen para todas las tareas

- **No se modifica ni se borra ningún test existente.** Única excepción autorizada (G3, "sí" de
  Valentin del 2026-10-15): los cambios de T168 y la línea de `frontend/tests/mocks/handlers.ts` de
  T085. Agregar métodos de test nuevos a un archivo de test creado por esta feature no cambia
  aserciones existentes y está permitido.
- **Ninguna dependencia nueva** en `backend/build.gradle` ni en `frontend/package.json`.
- **No se reemplazan** los `application*.yml`: solo se agregan claves.
- Status codes: solo 400, 404 y 409 desde el advice existente (`shared/GlobalExceptionHandler`); 401 y
  403 los resuelve `security/`.
- Frontend (restricciones de D24): un único elemento con rol `list` en `/pizarra` (nada nuevo usa
  `ul`/`ol` en el encabezado ni en `QuoteTag`); `PostIt` sigue siendo un único link sin botones; la ficha
  no repite el texto de equipo, posición ni liga; los nombres accesibles de botones y enlaces nuevos
  **no** contienen `precio`, `valor`, `puntaje`, `comprar`, `vender`, `sumar`, `plantel`,
  `presupuesto`, `portfolio` ni `orden` (usar "cotización"); los importes van sin `$`, `€`, `USD`,
  `EUR` ni estrellas ("103,00 cr."). Todo lo clickeable es `button` o enlace con foco visible; usable
  desde 360 px.

---

## Fase 1: Setup (puertas y línea de base)

**Propósito**: cumplir las puertas G1 y G3 del plan y partir de una base verde.

- [X] T001 Verificar la puerta G1 y traer la 006: confirmar que `git log --oneline origin/develop` muestra el merge de `feature/scraping-whoscored`, mergear `origin/develop` en `feature/cotizacion-estrategias` y comprobar que existen `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/modelo/performance/PlayerMatchStats.java` y `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/service/sync/SyncLock.java`. Si la 006 no está en `develop`, DETENERSE: no se empieza ninguna tarea de backend de cotización (la Fase 5, frontend de lectura contra MSW, sí puede avanzar)
  - Verificado el 2026-10-18: la 006 **todavía no está en `origin/develop`** (su punta es `85709c5`), pero está integrada en esta rama por el merge directo `ec48506` de `origin/feature/scraping-whoscored` en su punta `029638a`. `origin/develop` ya está contenido en `HEAD`, así que no hubo nada que mergear ni commit. Los dos archivos existen. Entre `c2a46ec` (el contraste de T002) y `029638a` no cambió ninguna de las clases de la sección 5.1 de `data-model.md`. Cuando la 006 entre en `develop`, mergear `origin/develop` de nuevo
- [X] T002 Contrastar los nombres reales de la 006 contra la tabla de `specs/007-cotizacion-estrategias/data-model.md` (sección 5.1): `PlayerMatchStats.player()`, `match()`, `minutesPlayed()`, `counts().get(Metric.X)` (con las 15 constantes de `Metric`), `rating()`, `Match.needsStats()` / `statsLoaded`, `Match.winner()`, `Match.fullTime()`, `SyncLock.runExclusively(...)` y `SyncInProgressException`, y los nombres de `PlayerMatchStatsRepository` y `PlayerMatchStatsSQLDAO`. Anotar cualquier diferencia en la sección 5.4 de `specs/007-cotizacion-estrategias/data-model.md` antes de seguir (solo cambia `WeeklyPerformance` y su test)
- [X] T003 Correr la línea de base sin tocar nada: `cd backend && ./gradlew build` y `cd frontend && npm test && npm run lint && npm run build`. Todo verde, incluida la suite de la 004 en `frontend/tests/`; si algo ya falla, anotarlo y avisar antes de seguir

---

## Fase 2: Fundacional (prerrequisitos bloqueantes)

**Propósito**: lo que usan varias historias: las lecturas nuevas sobre la 006 y la 005 (puerta G2), el
dominio y la persistencia de estrategias con la semilla A/B (US1 necesita la A activa), el dominio y
la persistencia base de cotizaciones, el caché, las reglas de seguridad y el soporte de tests.

**⚠️ CRÍTICO**: ninguna historia de backend empieza hasta cerrar esta fase.

### Lecturas nuevas sobre la 006 y la 005 (puerta G2, P-1 de D18)

- [X] T004 [P] Agregar en `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/persistence/sql/interfaces/performance/PlayerMatchStatsSQLDAO.java` una consulta JPQL `findAllByMatchUtcDateBetween(Instant fromInclusive, Instant toExclusive)` con `m.utcDate >= :from and m.utcDate < :to` (ojo: el `Between` derivado de Spring Data incluye los dos extremos y un partido del lunes 00:00 hora de Argentina caería en dos semanas) y `@EntityGraph` sobre `player`, `player.team`, `match`, `match.homeTeam` y `match.awayTeam`; y en `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/persistence/repository/performance/PlayerMatchStatsRepository.java` el método `List<PlayerMatchStats> findAllByMatchBetween(Instant fromInclusive, Instant toExclusive)` (`@Transactional(readOnly = true)`, devuelve modelo vía el mapper existente). Ninguna firma existente cambia
- [X] T005 [P] Agregar `findAllByStatus(MatchStatus status)` con `@EntityGraph` (`homeTeam`, `awayTeam`, `season`) en `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/persistence/sql/interfaces/match/MatchSQLDAO.java` y `List<Match> findAllFinished()` (`@Transactional(readOnly = true)`, `MatchStatus.FINISHED`) en `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/persistence/repository/match/MatchRepository.java`, sin tocar firmas existentes
- [X] T006 [P] Escribir `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/persistence/repository/performance/PlayerMatchStatsRepositoryMatchBetweenIT.java` (`@SpringBootTest`, `@ActiveProfiles("test")`): trae solo las estadísticas de partidos dentro de `[from, to)`; un partido exactamente en `to` (lunes 00:00 hora de Argentina = 03:00Z) queda afuera y uno en `from` adentro; las asociaciones se leen fuera de la transacción sin `LazyInitializationException`; rango sin partidos devuelve lista vacía
- [X] T007 [P] Escribir `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/persistence/repository/match/MatchRepositoryFinishedIT.java`: `findAllFinished()` devuelve solo los `FINISHED` (no los `SCHEDULED` ni otros), con local y visitante cargados; sin partidos terminados devuelve vacío

### Dominio de estrategias (`modelo/strategy/`)

- [X] T008 [P] Crear las excepciones de `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/modelo/strategy/exception/`: `UnknownMetricException`, `InvalidWeightsException`, `InvalidScaleFactorException`, `InvalidMinimumValueException` e `InvalidStrategyNameException` (extienden `shared/BadRequestException`), `StrategyNotFoundException` (`NotFoundException`), `DuplicateStrategyNameException` (`ConflictException`) y `StrategyInvariantException` (`RuntimeException`), con los mensajes en español de la sección 4 de `data-model.md` (por ejemplo "La métrica 'GOLES' no existe.", "Los pesos de delanteros deben sumar 1,00 y suman 0,95.")
- [X] T009 [P] Crear `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/modelo/strategy/MetricDirection.java` (`HIGHER_IS_BETTER`, `LOWER_IS_BETTER`) y `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/modelo/strategy/ValuationMetric.java` con las 19 constantes de la sección 2.1 de `data-model.md` y su sentido (`FOULS_COMMITTED`, `YELLOW_CARDS` y `RED_CARDS` son `LOWER_IS_BETTER`), más `fromName(String)` que lanza `UnknownMetricException` con el valor recibido
- [X] T010 Crear `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/modelo/strategy/WeightSet.java` (`record WeightSet(Map<ValuationMetric, BigDecimal> weights)`, copia inmutable): cada peso ≥ 0, a lo sumo 2 decimales (`stripTrailingZeros().scale() <= 2`), suma exactamente 1,00 con `compareTo`; `of(String label, Map<String, BigDecimal>)` convierte nombres con `ValuationMetric.fromName` y usa `label` en el mensaje ("por defecto", "delanteros"); `weight(ValuationMetric)` devuelve 0 si no figura; `usedMetrics()` las de peso > 0 (depende de T008, T009)
- [X] T011 Crear `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/modelo/strategy/StrategyVersionRef.java` (`versionId`, `strategyId`, `name`, `version`) y `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/modelo/strategy/StrategyVersion.java` (record de la sección 2.3 de `data-model.md`): constantes `DEFAULT_SCALE_FACTOR = 10`, `DEFAULT_MINIMUM_VALUE = 10.00`, `MIN_ALLOWED_MINIMUM = 0.01`; invariantes (factor > 0, mínimo ≥ 0,01, pesos por defecto obligatorios); `weightsFor(Position)` (los de la posición o los por defecto); `scoreFor(Position, Map<ValuationMetric, BigDecimal>)` = Σ peso × métrica sin redondeo intermedio y una sola vez a escala 6 `HALF_UP`; `valueFor(BigDecimal base, BigDecimal score)` = `max(mínimo, base + score × factor)` redondeado una sola vez a 2 decimales `HALF_UP`; `usedMetrics(Position)`; `ref()`. Necesita el nombre y el id de la estrategia para `ref()`: recibirlos en el constructor o resolverlos desde `Strategy` (depende de T010)
  - Implementado el 2026-10-18: se reciben en el constructor. La firma queda `StrategyVersion(Long id, Long strategyId, String name, int version, BigDecimal scaleFactor, BigDecimal minimumValue, WeightSet defaultWeights, Map<Position, WeightSet> positionWeights, Instant createdAt)`; `id`, `strategyId` y `createdAt` son nulos hasta que se guarda (T021/T022 asignan `created_at` al insertar). Además: factor y mínimo con más de 2 decimales se rechazan (el contrato lo pide y las columnas son `NUMERIC(…,2)`) y se normalizan a escala 2. `WeightSet` guarda los pesos a escala 2 en un `EnumMap` inmutable, descarta los de peso 0 y suma `WeightSet.labelOf(Position)` ("de delanteros") y `DEFAULT_LABEL` ("por defecto") para los mensajes
- [X] T012 Crear `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/modelo/strategy/Strategy.java` (`id` = id de su v1, `name`, `versions`; `latest()`, `nameKey()` sin espacios de los extremos y en minúsculas), `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/modelo/strategy/StrategyChange.java` (catálogo nuevo + versiones a insertar + id de versión que pasa a activa y la que deja de serlo) y `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/modelo/strategy/StrategyCatalog.java` sin setters: `create(name, factor?, minimum?, defaultWeights, positionWeights)` (v1 inactiva; nombre obligatorio y único sin distinguir mayúsculas ni espacios de los extremos; omitidos → defaults), `newVersion(strategyId, factor?, minimum?, ...)` (n+1; omitidos → los de la versión anterior; si es la activa, la nueva queda activa), `activate(strategyId)` (deja activa la versión más reciente; activar la ya activa no cambia nada), `activeVersion()` (el constructor exige exactamente una activa, si no `StrategyInvariantException`), `isActive(strategyId)` y `find(strategyId)` (depende de T011)
  - Implementado el 2026-10-18 con un ajuste para T024: el constructor exige exactamente una activa **salvo en el catálogo vacío** (`StrategyCatalog.empty()`, sin activa), y la primera estrategia que se crea en un catálogo vacío queda activa. Así la semilla, creando A y después B, deja A activa sin pasar nunca por un catálogo no vacío sin activa. La activa se modela como "la última versión de la estrategia activa"; el constructor rechaza un `activeVersionId` que no sea la última de su estrategia. `StrategyChange(catalog, versionsToInsert, activatedVersion, deactivatedVersionId)`: `activatedVersion` puede ser una de las versiones a insertar (todavía sin id), así T022 sabe con qué `active` insertarla. También se agregaron `findByName(name)` (lo usa la semilla), `activeStrategy()` y el límite de 100 caracteres del nombre (`InvalidStrategyNameException`)
- [X] T013 [P] Crear `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/modelo/strategy/StartingStrategies.java` con los datos de D13 de `research.md`: A "Rendimiento de partido" (factor 10, mínimo 10,00; `GOALS` 0,25, `ASSISTS` 0,15, `SHOTS` 0,10, `KEY_PASSES` 0,10, `SUCCESSFUL_DRIBBLES` 0,10, `TACKLES` 0,10, `RATING` 0,20 para todas las posiciones) y B "Equilibrada por posición" (factor 10, mínimo 10,00; la tabla por posición de D13 y el juego por defecto igual al de mediocampista), construidos con `WeightSet` (depende de T010)
- [X] T014 [P] Escribir `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/modelo/strategy/ValuationMetricTest.java`: 19 métricas, las 3 invertidas, `fromName` válido y `fromName("GOLES")` con el mensaje que incluye el valor
- [X] T015 [P] Escribir `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/modelo/strategy/WeightSetTest.java`: suma 1,00 válida; suma 0,95 rechazada con la suma en el mensaje; peso negativo; peso con 3 decimales; métrica desconocida; métrica ausente pesa 0; `usedMetrics` excluye los de peso 0
- [X] T016 [P] Escribir `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/modelo/strategy/StrategyVersionTest.java` con los ejemplos de SC-002: score 0,30 de la estrategia A (goles +0,8; asistencias +0,4; tiros 0; pases clave 0; regates −0,4; quites −0,4; rating +0,6); 100,00 → 103,00; 103,00 con −0,20 → 101,00; 12,00 con −0,50, factor 10 y mínimo 10,00 → 10,00; 103,00 con +0,10 → 104,00; score 0 → mismo valor; redondeo `HALF_UP` en 0,005; base por debajo de un mínimo que subió → mínimo; `weightsFor` cae en los por defecto; factor ≤ 0 y mínimo < 0,01 rechazados
- [X] T017 [P] Escribir `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/modelo/strategy/StrategyCatalogTest.java`: crear queda v1 inactiva con defaults 10 y 10,00; nombre repetido con otra capitalización y espacios → `DuplicateStrategyNameException`; nombre vacío → 400; `newVersion` de una inactiva no cambia la activa; `newVersion` de la activa deja activa la nueva; omitir factor y mínimo en `newVersion` hereda los de la versión anterior; `activate` deja activa la última versión y desactiva la anterior; activar la ya activa no cambia nada; id inexistente → `StrategyNotFoundException`; siempre exactamente una activa
- [X] T018 [P] Escribir `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/modelo/strategy/StartingStrategiesTest.java`: A suma 1,00 y pesa igual en todas las posiciones; B suma 1,00 en cada posición y en el juego por defecto, difiere en al menos dos posiciones e incluye una métrica de disciplina y una de contexto (FR-031, US4.13)

### Persistencia de estrategias

- [X] T019 [P] Crear `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/persistence/sql/entity/strategy/StrategySQL.java` (`@Table(name = "strategies")`, columnas `id` IDENTITY, `root_id`, `name` VARCHAR(100), `version_number`, `scale_factor` NUMERIC(8,2), `minimum_value` NUMERIC(12,2), `active`, `created_at`; índice único `ux_strategies_name_version (name, version_number)`; `@OneToMany(mappedBy = "strategy", cascade = ALL)` a los pesos; solo `active` es modificable) y `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/persistence/sql/entity/strategy/StrategyWeightSQL.java` (`@Table(name = "strategy_weights")`, `strategy_id`, `weight_set` VARCHAR(20) NOT NULL con `DEFAULT`/`GOALKEEPER`/`DEFENDER`/`MIDFIELDER`/`FORWARD`, `metric` VARCHAR(30), `weight` NUMERIC(3,2); índice único `ux_strategy_weights_set_metric`), según la sección 6.1 y 6.2 de `data-model.md`
  - Implementado el 2026-10-20: `root` es un `@ManyToOne` a la misma tabla (así Hibernate crea la FK de `root_id`) y `strategyId()` devuelve el id de la raíz o el propio. `created_at` lo asigna `@CreationTimestamp`. Las columnas son `updatable = false` salvo `active`, que se mueve con `activate()` / `deactivate()`. El juego y la métrica de `StrategyWeightSQL` son texto (como `MatchSQL.status`)
- [X] T020 Crear `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/persistence/sql/interfaces/strategy/StrategySQLDAO.java` (JpaRepository de `StrategySQL`, `findAll` con `@EntityGraph` sobre los pesos, búsqueda de la fila activa) (depende de T019)
- [X] T021 Crear `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/persistence/mapper/strategy/StrategyMapper.java`: filas de `strategies` + `strategy_weights` ↔ `Strategy`/`StrategyVersion`/`WeightSet` campo a campo, agrupando versiones por `root_id` (la v1 es la raíz); solo se guardan pesos > 0 (depende de T012, T019)
- [X] T022 Crear `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/persistence/repository/strategy/StrategyRepository.java`: `StrategyCatalog findCatalog()` (`@Transactional(readOnly = true)`) y `StrategyCatalog save(StrategyChange)` (inserta las versiones nuevas, apuntando `root_id` a la v1, y mueve la marca `active`; devuelve el catálogo con los ids generados) (depende de T020, T021)
  - Implementado el 2026-10-20: `save` desactiva la fila de `deactivatedVersionId`, inserta las versiones nuevas (la que es `activatedVersion` nace con `active = true`) y, si la activada ya existía, le pone la marca; devuelve el catálogo releído en la misma transacción. Si la base tuviera más de una fila activa, el mapper lanza `StrategyInvariantException`
- [X] T023 Escribir `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/persistence/repository/strategy/StrategyRepositoryIT.java`: ida y vuelta de una estrategia con pesos por defecto y por posición; una versión nueva conserva la anterior intacta; la marca `active` queda en exactamente una fila tras activar y tras editar la activa; el índice único `(name, version_number)` rechaza un duplicado exacto

### Semilla de estrategias

- [X] T024 Crear `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/service/strategy/StrategySeeder.java` (`ApplicationRunner`, como `service/user/AdminAccountInitializer`): crea A y B de `StartingStrategies` solo si no hay una con ese nombre (sin distinguir mayúsculas ni espacios), no duplica ni modifica las existentes aunque se hayan editado, y si después no hay ninguna activa activa la A; registra en `INFO` cuáles creó y cuáles ya existían. Corre en todos los perfiles (también `test`) (depende de T013, T022)
  - Implementado el 2026-10-20: "si después no hay ninguna activa, activar la A" no necesita código propio: la primera estrategia que entra en un catálogo vacío queda activa (T012) y un catálogo con estrategias siempre tiene exactamente una activa (su constructor lo exige)
- [X] T025 [P] Escribir `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/service/strategy/StrategySeederTest.java` (Mockito): base vacía crea las dos con A activa; con las dos existentes no guarda nada; con A editada (v2) no la toca
- [X] T026 [P] Escribir `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/service/strategy/StrategySeederIT.java` (H2): el contexto arranca con A activa v1 y B v1; correr la semilla otra vez no duplica (US4.1, US4.2)

### Dominio base de cotizaciones (`modelo/quote/`)

- [X] T027 [P] Crear `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/modelo/quote/exception/QuoteInvariantException.java` (`RuntimeException`, error de programación, no se muestra)
- [X] T028 [P] Crear `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/modelo/quote/QuoteWeek.java` (`record QuoteWeek(LocalDate monday)`, constante `ZONE = ZoneId.of("America/Argentina/Buenos_Aires")`; invariante: `monday` es lunes; `containing(Instant)`, `start()`, `end()`, `next()`, `previous()`, `isClosedAt(Instant)` (`instante >= end()`), `lastClosedAt(Instant)`, `Comparable`)
- [X] T029 Crear `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/modelo/quote/QuoteType.java` (`INITIAL`, `CALCULATED`, `REQUOTED`), `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/modelo/quote/QuoteVariation.java` (`amount`, `percent`) y `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/modelo/quote/Quote.java` (record de la sección 3.2 de `data-model.md`): invariantes (valor escala 2 y > 0; score escala 6 entre −1 y +1; `INITIAL` sin `baseValue`, las otras con; `validFrom` de `INITIAL` = `week.start()` y de `CALCULATED` = `week.end()`) con `QuoteInvariantException`; `tokenPrice()` = valor / 100 escala 4; `variation()` vacía en la `INITIAL`, si no `amount = value − baseValue` y `percent = amount / baseValue × 100` a 2 decimales `HALF_UP`; `isCurrentAt(Instant)` (depende de T011, T027, T028)
- [X] T030 [P] Crear `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/modelo/quote/QuotePage.java` (`QuotePage<T>` con `content`, `page`, `size`, `totalElements`, `totalPages`, `hasPrevious`, `hasNext`, misma regla que `modelo/player/PlayerPage`)
- [X] T031 [P] Escribir `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/modelo/quote/QuoteWeekTest.java`: partido del domingo 23:59 hora de Argentina es de esa semana y el del lunes 00:00 de la siguiente; `start()` es el lunes 03:00Z; la semana en curso no está cerrada; `lastClosedAt` un lunes a las 00:00 exactas; un `LocalDate` que no es lunes se rechaza
- [X] T032 [P] Escribir `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/modelo/quote/QuoteTest.java`: 103,00 da precio por token 1,0300; variación +3,00 y +3,00 % desde 100,00; la `INITIAL` no informa variación; invariantes de vigencia, escala y `baseValue`

### Persistencia base de cotizaciones

- [X] T033 Crear `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/persistence/sql/entity/quote/QuoteSQL.java` (`@Table(name = "quotes")` según la sección 6.3 de `data-model.md`: `player_id` FK, `week_start` DATE, `valid_from` TIMESTAMP WITH TIME ZONE, `quote_value` y `base_value` NUMERIC(12,2), `score` NUMERIC(10,6), `quote_type` VARCHAR(12), `strategy_id` FK a la fila de la versión; índices `ux_quotes_player_valid_from` único, `ix_quotes_player_valid_from_desc` y `ix_quotes_week_start_type`; `@ManyToOne(fetch = LAZY)` a `PlayerSQL` y `StrategySQL`; sin setters) (depende de T019)
- [X] T034 Crear `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/persistence/sql/interfaces/quote/QuoteSQLDAO.java` (JpaRepository de `QuoteSQL`; las consultas JPQL se suman en cada historia; nada de `nativeQuery`) (depende de T033)
- [X] T035 Crear `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/persistence/mapper/quote/QuoteMapper.java`: `QuoteSQL` ↔ `Quote` campo a campo, con `StrategyVersionRef` armada desde la fila de la versión y su raíz, sin cargar el jugador completo (solo el id) (depende de T029, T033)
- [X] T036 Crear `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/persistence/repository/quote/QuoteRepository.java` con `saveAll(List<Quote>)` y **sin** ningún método de `update` ni `delete` (FR-005); las lecturas se agregan en cada historia (depende de T034, T035)
- [X] T037 Escribir `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/persistence/repository/quote/QuoteRepositoryIT.java`: ida y vuelta conserva escalas (2, 6 y 4 del precio por token), tipo, semana, vigencia y versión; el índice único `(player_id, valid_from)` rechaza dos cotizaciones del mismo jugador en el mismo instante; el repository no expone `update` ni `delete` (por reflexión sobre sus métodos públicos)

### Caché, seguridad y soporte de tests

- [X] T038 [P] Crear `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/config/CacheConfig.java` (`@Configuration @EnableCaching`, bean `CacheManager` = `new ConcurrentMapCacheManager(QUOTES_CURRENT, QUOTES_RANKING)` con las constantes `QUOTES_CURRENT = "quotes-current"` y `QUOTES_RANKING = "quotes-ranking"`) y `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/config/CacheConfigTest.java` (el contexto levanta y el `CacheManager` es el nuestro con esos dos cachés)
  - `CacheConfigTest` también verifica que `QuoteCacheInvalidator.invalidateAll()` (T039) vacía los dos cachés
- [X] T039 Crear `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/service/quote/QuoteCacheInvalidator.java` (`@Component` con `invalidateAll()` = `@CacheEvict(cacheNames = {QUOTES_CURRENT, QUOTES_RANKING}, allEntries = true)`; se llama siempre **después del commit**, desde servicios no transaccionales) (depende de T038)
- [X] T040 [P] Agregar en `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/security/SecurityConfig.java`, antes de `anyRequest().authenticated()` y junto a la regla de `POST /players/sync`, `pathPattern(HttpMethod.POST, "/quotes/**")` → `hasRole(Role.ADMIN.name())` y `pathPattern("/strategies/**")` (todos los métodos) → `hasRole(Role.ADMIN.name())`. **No tocar** `publicEndpoints()` (las lecturas bajo `GET /players/**` ya son públicas por la regla `TEMPORAL`)
- [X] T041 [P] Crear el soporte de tests `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/modelo/quote/PlayerMatchStatsBuilder.java` (arma `PlayerMatchStats` con todo en 0 y métodos `forPlayer(...)`, `inMatch(...)`, `minutes(90)`, `goals(2)`, `tackles(3)`, `rating("7.2")`, etc.; es el único test que conoce el constructor de la 006) y `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/modelo/quote/QuoteFixtures.java` (jugadores por posición, partidos terminados en una semana dada con resultado, la estrategia A como `StrategyVersion`, cotizaciones de ejemplo)
  - Implementado el 2026-10-20, con `PlayerMatchStatsBuilderTest` (archivo nuevo) para que un cambio del constructor de la 006 falle acá primero. `QuoteFixtures` suma `finishedMatchWithoutStats`, `scheduledMatch`, la estrategia B y fábricas de `INITIAL`, `CALCULATED` y `REQUOTED`. Nota de entorno: en una consola Windows con página de códigos 850, dos tests existentes de la 006 (`StatsSyncServiceTest`, la flecha `→` del registro) fallan también sobre `HEAD` sin estos cambios; con `LOGGING_CHARSET_CONSOLE=UTF-8` y `JAVA_TOOL_OPTIONS="-Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8"` el `./gradlew build` queda verde (1116 tests)

**Checkpoint**: compila, `./gradlew build` verde, el contexto arranca con A activa y B sembrada.

---

## Fase 3: Historia 1 - Cotización semanal de todos los jugadores (Prioridad: P1) 🎯 MVP

**Objetivo**: un administrador dispara el recálculo y el sistema cotiza, semana cerrada por semana
cerrada y en orden, a todos los jugadores activos con la estrategia activa, guarda el historial sin
pisar nada y devuelve el informe.

**Prueba independiente**: con catálogo, partidos y estadísticas de varias semanas cargados en H2,
`POST /quotes/recalculate` como ADMIN; el historial reproduce los ejemplos del spec, una semana sin
partidos no mueve precios y repetir el recálculo no crea nada.

### Implementación de la Historia 1

- [X] T042 [P] [US1] Crear `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/modelo/quote/exception/RecalculationInProgressException.java` ("Ya hay un recálculo en curso.") y `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/modelo/quote/exception/RecalculationBlockedBySyncException.java` ("No se puede recalcular: hay una sincronización en curso."), ambas `ConflictException`
- [X] T043 [P] [US1] Crear `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/modelo/quote/WeeklyPerformance.java` (`record WeeklyPerformance(Player player, Map<ValuationMetric, BigDecimal> values)`; `of(Player, List<PlayerMatchStats>)` es el único lugar que lee los nombres de la 006): 15 métricas de suma siempre presentes (0 si no jugó); `RATING` = Σ(rating × minutos) / Σ minutos sobre los partidos con rating y minutos > 0, ausente si ninguno; `PASS_ACCURACY` = Σ pases precisos / Σ pases a escala 6, ausente si Σ pases = 0; `TEAM_RESULT` = promedio de 1/0,5/0 sobre los partidos que jugó (minutos > 0) y donde el lado se resuelve comparando `player.team()` con local y visitante por `externalId` y el partido tiene resultado, ausente si ninguno; `CLEAN_SHEETS` = cantidad de esos partidos con el rival en 0 en `fullTime`, ausente si ninguno es resoluble (D5)
- [X] T044 [P] [US1] Crear `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/modelo/quote/MetricNormalization.java` (función pura): `normalize(Map<Long, BigDecimal> valuesByPlayer, MetricDirection)` → `Map<Long, BigDecimal>`; posición relativa = (menos + la mitad de los iguales, él incluido) / n, escala 6 `HALF_UP`, con "menos" invertido en `LOWER_IS_BETTER`; métrica = 2 × posición relativa − 1 a partir de la posición ya redondeada; ordenar una vez (`O(n log n)`), no comparar todos contra todos; todos empatan o un solo par → 0
- [X] T045 [US1] Crear `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/modelo/quote/WeeklyQuotation.java` con `quoteWeek(QuoteWeek, StrategyVersion, List<Player> activePlayers, List<PlayerMatchStats> weekStats, Map<Long, Quote> lastBeforeWeek)`: agrupa a los activos por posición (cinco ligas juntas), arma `WeeklyPerformance`, normaliza solo las métricas con peso en esa posición, calcula el score, base = `lastBeforeWeek[id].value` o 100,00; si el jugador no tiene ninguna cotización genera además la `INITIAL` (100,00, rige desde `week.start()`); la `CALCULATED` rige desde `week.end()`. El resultado no depende del orden de las listas (ordenar por id antes de iterar; nada de `HashMap` iterado alimentando una suma). Extraer el cálculo de scores por semana a un método reutilizable (lo usan `requoteWeek` en US5 y la simulación en US7) (depende de T043, T044)
  - Implementado el 2026-10-20: `quoteWeek` y `weekScores` son estáticos (función pura, como `MetricNormalization`). `weekScores(week, strategy, activePlayers, weekStats)` devuelve un `SortedMap` por id con el score de cada activo: es el cálculo reutilizable para `requoteWeek` (US5) y la simulación (US7). Filtra por su cuenta a los inactivos y a las estadísticas de partidos de otra semana
- [X] T046 [P] [US1] Crear `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/modelo/quote/PendingReason.java` (`MISSING_STATS`, `PREVIOUS_WEEK_PENDING`), `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/modelo/quote/WeekDecision.java` (`QUOTE(week)` / `PENDING(week, reason)`) y `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/modelo/quote/QuotingPlan.java`: `of(Optional<QuoteWeek> lastQuoted, Optional<QuoteWeek> firstWithStats, List<Match> finishedMatches, Instant now)` con las reglas de D11 (desde la siguiente a la última cotizada o la primera con estadísticas; hasta la última cerrada; partido terminado sin estadísticas con `needsStats()` y < 7 días desde el cierre → `MISSING_STATS`; ≥ 7 días → se cotiza; una pendiente deja las siguientes en `PREVIOUS_WEEK_PENDING`; semana sin partidos se cotiza); constante `STATS_WAIT = Duration.ofDays(7)`; `weeksToQuote()`, `pendingWeeks()`, `hasClosedUnquotedWeeks()`
  - Implementado el 2026-10-20: `WeekDecision` es un record con `quote(week)` / `pending(week, reason)` y `pendingWeeks()` devuelve `List<PendingWeek>` (T047), listo para el informe. Con `STATS_WAIT` de 7 días exactos la regla "una pendiente bloquea las siguientes" **no se puede dar**: la semana siguiente a la pendiente cierra justo cuando vence la espera. Para probar la regla se agregó una sobrecarga package-private `of(..., Instant now, Duration statsWait)`; la pública usa `STATS_WAIT`
- [X] T047 [P] [US1] Crear `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/modelo/quote/RecalculationOrigin.java` (`WEEKLY`, `STARTUP`, `MANUAL`, `STRATEGY_CHANGE`), `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/modelo/quote/WeekResult.java` (semana y cantidades de `INITIAL`, `CALCULATED`, `REQUOTED`), `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/modelo/quote/PendingWeek.java` y `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/modelo/quote/RecalculationReport.java` (inmutable, con `Builder`: `origin`, `startedAt`, `finishedAt`, `StrategyVersionRef`, `List<WeekResult>`, `List<PendingWeek>`, `interruptedAt` opcional; `isEmpty()` = sin semanas para cotizar)
  - Implementado el 2026-10-20: `isEmpty()` es "sin semanas cotizadas y sin interrupción" (si falla la primera semana, el informe no está vacío). El `Builder` se arma con `builder(origin, startedAt, strategy)` y cierra con `build(finishedAt)`; una corrida se interrumpe una sola vez. `WeekResult.of(week, quotes)` cuenta por tipo y lo puede usar `QuoteWeekWriter` (T050)
- [X] T048 [US1] Agregar a `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/persistence/sql/interfaces/quote/QuoteSQLDAO.java` las consultas JPQL y a `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/persistence/repository/quote/QuoteRepository.java` los métodos `Map<Long, Quote> findLastBeforeWeek(QuoteWeek)` (por jugador, la de mayor `validFrom` con `week_start` < la semana, incluidas las `REQUOTED`, D9) y `Optional<QuoteWeek> findLastCalculatedWeek()` (mayor `week_start` con `quote_type = CALCULATED`, usa `ix_quotes_week_start_type`)
  - Implementado el 2026-10-20: dos JPQL en `QuoteSQLDAO` (`findLastOfEachPlayerBefore`, con subconsulta correlacionada por `max(validFrom)` y `join fetch` de la versión, y `findLastWeekStartOfType`, un `max(weekStart)`). Se sumaron tres casos a `QuoteRepositoryIT` (la recotizada gana por vigencia; la semana pedida no cuenta; una `INITIAL` sola no es semana calculada)
- [X] T049 [US1] Crear `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/service/quote/RecalculationLock.java` (`@Component`): `Semaphore(1)` propio con `tryAcquire` (sin esperar ni encolar) y, adentro, `SyncLock` de la 006; segundo recálculo → `RecalculationInProgressException`; sincronización en curso → traducir `SyncInProgressException` a `RecalculationBlockedBySyncException`; orden de toma: semáforo y después `SyncLock`, se sueltan al revés; API `<T> T runExclusively(Supplier<T>)` (depende de T001, T042)
  - Implementado el 2026-10-20: solo se traduce el rechazo del propio `SyncLock`; una `SyncInProgressException` lanzada desde adentro de la acción se propaga tal cual
- [X] T050 [US1] Crear `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/service/quote/QuoteWeekWriter.java` con `@Transactional WeekResult quoteWeek(QuoteWeek, StrategyVersion)`: lee jugadores activos (`PlayerRepository`), estadísticas de la semana (`PlayerMatchStatsRepository.findAllByMatchBetween(week.start(), week.end())`) y `QuoteRepository.findLastBeforeWeek`, delega en `WeeklyQuotation.quoteWeek`, guarda con `saveAll` y devuelve las cantidades; una semana, todo o nada (FR-012). Sin `if` de negocio (depende de T004, T045, T048)
- [X] T051 [US1] Crear `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/service/quote/RecalculationReportLogger.java` (SLF4J, como `service/sync/SyncReportLogger`): `INFO` de inicio y fin, una línea por semana cotizada con iniciales/calculadas/recotizadas, una línea con las pendientes y su motivo, la línea final con duración y estrategia; `WARN` para una semana que falló al guardarse; sin datos de usuarios
- [X] T052 [US1] Crear `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/service/quote/QuoteRecalculationService.java` (sin `@Transactional`): `RecalculationReport recalculate(RecalculationOrigin)` que toma `RecalculationLock`, lee la versión activa **una sola vez** (`StrategyRepository.findCatalog().activeVersion()`, FR-039), arma `QuotingPlan` con `findLastCalculatedWeek`, la primera semana con estadísticas y `MatchRepository.findAllFinished()` y un `Clock` inyectado (usar el bean de `config/ApplicationConfig` si existe; si no, declarar `Clock.systemUTC()` ahí), llama a `QuoteWeekWriter.quoteWeek` semana por semana **en orden**, ante un `DataAccessException` corta (no intenta las siguientes) y marca `interruptedAt`, escribe el informe con `RecalculationReportLogger` e invoca `QuoteCacheInvalidator.invalidateAll()` después de soltar el candado si cotizó al menos una semana. No consulta ninguna fuente externa (depende de T039, T046, T047, T049, T050, T051)
  - Implementado el 2026-10-20: "la primera semana con estadísticas" es `QuotingPlan.firstWeekWithStats(finishedMatches)` (método nuevo del modelo, la semana del partido terminado más temprano con `statsLoaded`), así el servicio no decide nada y no hace falta otra consulta. El `Clock` es el bean de `ApplicationConfig`. El caché se vacía solo si `quotedWeeks` no está vacío (si falla la primera semana, no)
- [X] T053 [P] [US1] Crear en `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/controller/quote/dto/` los DTO `StrategyRefResponse` (`id`, `name`, `version`), `WeekResultResponse` (`weekStart`, `initial`, `calculated`, `requoted`), `PendingWeekResponse` (`weekStart`, `reason`) y `RecalculationReportResponse` (`origin`, `startedAt`, `finishedAt`, `strategy`, `quotedWeeks`, `pendingWeeks`, `nothingToQuote`, `interruptedAt` opcional) con un `from(...)` desde el modelo, según `contracts/quotes-admin-api.yaml`
- [X] T054 [US1] Crear `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/controller/quote/QuoteAdminController.java` con `POST /quotes/recalculate` (origen `MANUAL`, 200 con `RecalculationReportResponse`, también con informe vacío); Swagger `@Tag(name = "Recálculo")`, `@Operation`, `@ApiResponse` 200/401/403/409 y los dos `@SecurityRequirement` (`OpenApiConfig.BEARER_AUTH` y `OpenApiConfig.API_KEY_AUTH`) como `controller/sync/SyncController` (depende de T052, T053)

### Tests de la Historia 1

- [X] T055 [P] [US1] Escribir `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/modelo/quote/WeeklyPerformanceTest.java` (con `PlayerMatchStatsBuilder`): sumas de la semana; jugador sin estadísticas (no cruzado por WhoScored, FR-022) tiene las 15 de suma en 0 y no tiene `RATING`, `PASS_ACCURACY`, `TEAM_RESULT` ni `CLEAN_SHEETS`; rating ponderado por minutos y partido sin rating excluido; 0 pases → sin precisión; jugador cuyo equipo no es ninguno del partido → sin `TEAM_RESULT` ni `CLEAN_SHEETS`; victoria/empate/derrota y valla invicta; suplente con 0 minutos no "jugó"
- [X] T056 [P] [US1] Escribir `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/modelo/quote/MetricNormalizationTest.java`: cinco delanteros con 0; 0; 1; 2; 3 goles → −0,6; −0,6; 0,0; +0,4; +0,8 y promedio 0 (SC-002); todos empatan → 0; un solo par → 0; métrica invertida (faltas: el que tuvo menos queda mejor); jugadores sin la métrica no figuran en la salida
- [X] T057 [P] [US1] Escribir `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/modelo/quote/WeeklyQuotationTest.java` (casos de `quoteWeek`): primera semana genera `INITIAL` 100,00 desde el lunes y `CALCULATED` desde el cierre; 100,00 → 103,00 → 101,00 en dos semanas; score 0 guarda una cotización con el mismo valor; semana sin partidos → ningún valor cambia (SC-004); delantero que no jugó se cotiza contra los que convirtieron; jugador inactivo no recibe cotización y al volver parte de su última; jugador nuevo a mitad de temporada recibe su `INITIAL` desde el lunes de su primera semana; métrica no calculable fuera de la comparación; pesos por posición (delantero usa los suyos, defensor los por defecto, US4.4); determinismo con la entrada desordenada (FR-026)
- [X] T058 [P] [US1] Escribir `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/modelo/quote/QuotingPlanTest.java`: sin cotizaciones empieza en la primera semana con estadísticas; sin estadísticas en ninguna parte no hay nada; la semana en curso nunca; partido terminado sin estadísticas a < 7 días → `MISSING_STATS` y las siguientes `PREVIOUS_WEEK_PENDING`; a ≥ 7 días se cotiza; semana sin partidos se cotiza; todo cotizado → lista vacía y `hasClosedUnquotedWeeks()` falso
- [X] T059 [P] [US1] Escribir `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/modelo/quote/RecalculationReportTest.java`: el builder acumula semanas y pendientes; `isEmpty()` sin semanas; `interruptedAt` presente solo si una semana falló
- [X] T060 [P] [US1] Escribir `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/service/quote/RecalculationLockTest.java`: corre la acción con los dos candados libres; un segundo `runExclusively` concurrente lanza `RecalculationInProgressException`; con `SyncLock` tomado lanza `RecalculationBlockedBySyncException`; los dos candados quedan libres después de una excepción en la acción
- [X] T061 [P] [US1] Escribir `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/service/quote/RecalculationReportLoggerTest.java` (como `SyncReportLoggerTest`): informe con semanas, con pendientes y vacío
- [X] T062 [P] [US1] Escribir `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/service/quote/QuoteRecalculationServiceTest.java` (Mockito, `Clock` fijo): cotiza las semanas en orden; lee la activa una sola vez aunque el catálogo cambie entre semanas; corta ante una falla de la base sin intentar las siguientes; sin semanas → informe vacío sin error; invalida el caché solo después de cotizar al menos una semana; con el candado ocupado propaga el 409 sin cotizar nada
- [X] T063 [US1] Escribir `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/service/quote/QuoteWeekWriterIT.java` (H2): una falla a la mitad de una semana (forzada con un dato que viola el índice único) deja 0 cotizaciones de esa semana (SC-016) y la siguiente corrida la cotiza
  - Implementado el 2026-10-20: la falla se fuerza guardando antes la `INITIAL` de la semana siguiente de un jugador del medio (rige desde el cierre de la semana, el mismo instante que su `CALCULATED`). Soporte de tests nuevo: `service/quote/QuoteDatabase` (carga temporada, equipos, jugadores, partidos y estadísticas; lee y borra cotizaciones, que el repository no ofrece) y `service/quote/MutableClock`
- [X] T064 [US1] Escribir `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/service/quote/RecalculationIT.java` (H2, `Clock` fijo, perfil `test` sin token de Football-Data, así se comprueba que no consulta fuentes): tres semanas cerradas sin cotizaciones → se cotizan en orden y cada activo queda con `INITIAL` + 3 `CALCULATED` (US1.1, SC-001); el informe lista las tres; repetir no crea nada y `nothingToQuote` (SC-005); semana pendiente < 7 días bloquea las siguientes y ≥ 7 días se cotiza; la semana en curso no se cotiza; jugador inactivo; dos bases idénticas dan cotizaciones idénticas (SC-007)
  - Implementado el 2026-10-20 con un `MutableClock` `@Primary` (en `@TestConfiguration`). "Una pendiente bloquea las siguientes" no se puede ver en el IT con la espera real de 7 días (nota de T046); queda cubierto por `QuotingPlanTest`. El IT prueba que la pendiente < 7 días no se cotiza y que a los 7 días se cotiza junto con la semana siguiente
- [X] T065 [US1] Escribir `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/e2e/QuoteAdminAccessIT.java` (MockMvc, como `e2e/AccessControlIT` y `AuthTestHelper`): `POST /quotes/recalculate` sin credencial → 401, con `USER` → 403, y en los dos casos 0 cotizaciones creadas; con `ADMIN` por token y por clave de API → 200 con el informe (US1.16, SC-011)

**Checkpoint**: la US1 funciona sola: con la A sembrada, el recálculo manual construye el historial.

---

## Fase 4: Historia 2 - Consultar cotizaciones (Prioridad: P2)

**Objetivo**: cualquiera, sin sesión, consulta la cotización actual, la de una fecha, la evolución y el
ranking, con valor y precio por token.

**Prueba independiente**: con un historial sembrado por la US1 o por datos de prueba, cada recurso
responde sin credencial con valores, variaciones, orden, filtros, paginación y los 404 distinguibles.

### Implementación de la Historia 2

- [X] T066 [P] [US2] Crear en `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/modelo/quote/exception/` `PlayerWithoutQuoteException` ("El jugador 9 no tiene cotización.") y `QuoteNotFoundAtDateException` ("El jugador 9 no tiene cotización al 2026-08-16."), ambas `NotFoundException`, e `InvalidQuoteDateException`, `FutureQuoteDateException` e `InvalidDateRangeException` (`BadRequestException`), con los mensajes de la sección 4 de `data-model.md` que incluyen el valor no admitido
- [X] T067 [P] [US2] Crear `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/modelo/quote/QuoteDay.java`: `parse(String)` (formato `AAAA-MM-DD`, si no `InvalidQuoteDateException`), `requireNotFuture(LocalDate, LocalDate today)`, `requireOrdered(from, to)`, y los instantes de inicio del día y de inicio del día siguiente en hora de Argentina (para "la que regía al final del día" y para rangos con los dos extremos incluidos)
- [X] T068 [P] [US2] Crear `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/modelo/quote/RankedQuote.java` (`Player`, `Quote`) y `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/modelo/quote/RankingPage.java` (`QuotePage<RankedQuote>` + `StrategyVersionRef` activa)
- [X] T069 [US2] Agregar a `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/persistence/sql/interfaces/quote/QuoteSQLDAO.java` (JPQL, sin `nativeQuery`) y a `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/persistence/repository/quote/QuoteRepository.java`: `findCurrent(long playerId)` y `findCurrentOf(Collection<Long>)` (la de mayor `validFrom` ≤ ahora), `findAt(long playerId, Instant exclusiveEnd)` (la última con `validFrom` < el instante), `findHistory(long playerId, Instant from, Instant to, int page, int size)` (ascendente por `validFrom`, incluye todas, también las recotizadas) y `findRanking(PlayerFilter, int page, int size)` (última cotización de cada jugador **activo**, filtros de liga, equipo y posición combinables, `JOIN FETCH` de jugador, equipo y versión, orden valor desc, nombre asc, id asc), devolviendo `QuotePage`
- [X] T070 [US2] Crear `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/service/quote/QuoteQueryService.java`: `getCurrent(playerId)` con `@Cacheable(QUOTES_CURRENT)`; `getAt(playerId, String date)`; `getHistory(playerId, String from, String to, page, size)`; `getCurrentOf(List<Long> ids)` (máximo 50, los que no tienen cotización no figuran; sin caché); `getRanking(filter, page, size)` con `@Cacheable(QUOTES_RANKING)` que suma la `StrategyVersionRef` activa (`StrategyRepository.findCatalog()`). Jugador inexistente → `PlayerNotFoundException` existente; existente sin cotización → `PlayerWithoutQuoteException`; fecha futura → 400; sin cotización a esa fecha → `QuoteNotFoundAtDateException`. Recibe un `Clock` para "hoy" (depende de T066, T067, T068, T069)
- [X] T071 [P] [US2] Crear en `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/controller/quote/dto/` `VariationResponse`, `QuoteResponse` (`value`, `tokenPrice`, `variation` ausente en la `INITIAL`, `weekStart`, `validFrom`, `type`, `strategy`; **sin** score ni estadísticas, FR-006), `PlayerQuoteResponse`, `QuoteHistoryPageResponse`, `CurrentQuotesResponse` (`quotes`), `RankingRowResponse` (`playerId`, `name`, `position`, `team`, `league`, `teamCrest`, `quote`) y `RankingPageResponse` (+ `activeStrategy`), según `contracts/quotes-api.yaml`; importes como números JSON con su escala (`103.00`, `1.0300`)
- [X] T072 [US2] Crear `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/controller/quote/QuoteController.java` con `GET /players/{id}/quotes` (`from`, `to` como `String`; `page` default 0; `size` default 20, máximo 100), `GET /players/{id}/quotes/current`, `GET /players/{id}/quotes/at?date=` (`String`), `GET /players/quotes/current?ids=` (lista de hasta 50 ids numéricos; inválido o más de 50 → 400) y `GET /players/ranking` (`league` y `position` como enums como en `PlayerController`, `team` con el mismo `@Pattern`, `size` default 10, máximo 50); Swagger `@Tag` "Cotizaciones" y "Ranking", sin `@SecurityRequirement` (son públicos) (depende de T070, T071)

### Tests de la Historia 2

- [X] T073 [P] [US2] Escribir `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/modelo/quote/QuoteDayTest.java`: formato válido e inválido (`2026-13-45`) con el valor en el mensaje; fecha futura; rango invertido; fin del día en hora de Argentina
- [X] T074 [US2] Agregar casos (sin cambiar los existentes) a `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/persistence/repository/quote/QuoteRepositoryIT.java`: `findLastBeforeWeek` incluye recotizadas de semanas anteriores; `findLastCalculatedWeek`; `findCurrent`/`findCurrentOf`; `findAt` en el ejemplo del spec (inicial 100,00 desde 17/08, 103,00 desde 24/08, 101,00 desde 31/08: al 20/08 → 100,00, al 24/08 → 103,00, al 16/08 → vacío; US2.6); `findHistory` con rango; `findRanking` ordenado, con desempate por nombre e id, filtros combinados y sin inactivos
- [X] T075 [P] [US2] Escribir `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/service/quote/QuoteQueryServiceTest.java` (Mockito): jugador inexistente vs sin cotización; fecha futura y formato inválido; rango invertido; lote con ids sin cotización omitidos; el ranking informa la activa
- [X] T076 [US2] Escribir `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/e2e/QuoteControllerIT.java` (MockMvc, sin credencial): cotización actual 103,00 con precio 1,0300, vigencia, semana, tipo, estrategia y versión, variación +3,00 y +3,00 % (US2.1, 2.2); con solo la inicial sin variación (2.3); 404 "no tiene cotización" distinto de 404 "No existe un jugador con id" (2.4, 2.5); a una fecha (2.6); 400 por fecha futura, formato y rango invertido con el valor en el mensaje (2.7, 2.9); evolución paginada con los dos extremos incluidos (2.8); ranking ordenado, filtrado, paginado e informando la estrategia activa (2.11, 2.12); inactivo fuera del ranking pero consultable por id (2.13); 400 por `position=DELANTERO` (2.14); ninguna respuesta trae `score` ni estadísticas (FR-006); lote `/players/quotes/current` con 51 ids → 400
- [X] T077 [P] [US2] Escribir `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/e2e/RankingRoutingIT.java`: `GET /players/ranking` llega al ranking y no a `/players/{id}` con `id = "ranking"`; responde 200 sin credencial; `GET /players/{id}` sigue igual (200 con un jugador, 404 con uno inexistente, 400 con un id no numérico)
- [X] T078 [US2] Escribir `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/service/quote/QuoteCacheIT.java` (H2): la segunda consulta de la actual y del ranking sale del caché; un recálculo que cotiza invalida los dos cachés después del commit y la consulta siguiente ve la semana nueva completa

**Checkpoint**: US1 y US2 funcionan por API; las consultas son públicas y no dependen de las fuentes.

---

## Fase 5: Historia 3 - Ver las cotizaciones en la aplicación web (Prioridad: P3)

**Objetivo**: la pizarra, la ficha (con gráfico y rango de fechas) y el ranking muestran las
cotizaciones, sin sesión, desde 360 px y accesibles.

**Prueba independiente**: con MSW, recorrer pizarra, ficha y ranking sin sesión verificando valores,
variaciones, filtros, paginación, gráfico, rango, estados de carga, error y "sin cotización".

**Nota**: trabaja contra MSW con las formas de `contracts/`; puede avanzar en paralelo con el backend.

### Base de la Historia 3

- [X] T079 [P] [US3] Crear `frontend/src/types/quotes.ts` (parte de lectura, a mano desde `contracts/quotes-api.yaml`): `QUOTE_TYPES` y `QuoteType`, `StrategyRef`, `Variation`, `Quote`, `PlayerQuote`, `QuoteHistoryPage`, `CurrentQuotesResponse`, `RankingRow`, `RankingPage`, `RankingQuery`, y los tipos de interfaz `QuoteRange`, `ChartPoint`, `QuoteBadge` (`loading`, `none`, `ready`, `error`); reutilizar `League`, `Position` y la forma de página de `frontend/src/types/api.ts`
- [X] T080 [P] [US3] Crear `frontend/src/utils/quotes.ts` (funciones puras): créditos con 2 decimales y coma ("103,00 cr.", sin `$`/`€`/`USD`/`EUR`), precio por token con 4 decimales, variación con signo y texto ("+3,00 (+3,00 %) sube", "baja", "sin cambios"), etiqueta del tipo (`INITIAL` → "inicial"...), etiqueta de semana, y `lastPerWeek(quotes)` (una por semana: la última por `validFrom`, así una semana recotizada muestra la recotizada)
- [X] T081 [P] [US3] Crear `frontend/src/utils/chart.ts` (geometría del gráfico: escalar puntos a porcentajes x/y con márgenes, dominio con un solo punto o valores iguales sin dividir por cero, ticks del eje)
- [X] T082 [US3] Crear `frontend/src/services/quoteService.ts` sobre `request()` de `frontend/src/services/httpClient.ts`: `getPlayerQuotes(id, {from, to, page, size})`, `getCurrentQuote(id)`, `getQuoteAt(id, date)`, `getCurrentQuotes(ids)` (`ids` separados por coma) y `getRanking(query)` (omite filtros vacíos) (depende de T079)
- [X] T083 [P] [US3] Agregar a `frontend/src/styles/tokens.css` los tokens de subida y bajada (verde y rojo de tiza) sin tocar los existentes
- [X] T084 [US3] Crear `frontend/tests/mocks/quotes.ts` (datos: cotización actual de varios jugadores de `frontend/tests/mocks/players.ts`, al menos uno **sin** cotización, un historial con `INITIAL`, varias `CALCULATED` y una semana con `REQUOTED`) y `frontend/tests/mocks/quoteHandlers.ts` (handlers MSW de `*/api/players/quotes/current`, `*/api/players/ranking`, `*/api/players/:id/quotes`, `.../current` y `.../at`, con 404 "no tiene cotización" vs jugador inexistente, filtros y 400 de valores no admitidos, paginación del catálogo) (depende de T079)
- [X] T085 [US3] Modificar `frontend/tests/mocks/handlers.ts` **solo** para importar `quoteHandlers` y esparcirlos **antes** de `playerHandlers` en el arreglo `handlers` (una línea más el `import`; cambio autorizado por G3, D25). Ninguna otra línea cambia (depende de T084)
- [X] T086 [P] [US3] Escribir `frontend/tests/utils/quotes.test.ts` (formatos sin símbolo de moneda, signo y texto de la variación, `lastPerWeek` con una semana recotizada) y `frontend/tests/utils/chart.test.ts` (un punto, valores iguales, varios puntos)

### Hooks de la Historia 3

- [X] T087 [P] [US3] Crear `frontend/src/hooks/useCurrentQuotes.ts`: recibe los ids de la hoja visible y hace **una** consulta por lote (`getCurrentQuotes`), devuelve el `QuoteBadge` de cada id (`none` si no vino) y `retry`; con ids vacíos no consulta (usar `useAsync`)
- [X] T088 [P] [US3] Crear `frontend/src/hooks/usePlayerQuote.ts` (actual de la ficha; un 404 de la cotización con el jugador ya cargado → estado "sin cotización", distinto de error) y `frontend/src/hooks/useQuoteHistory.ts` (historial con `QuoteRange`, `size` 100, vuelve a pedir al cambiar el rango)
- [X] T089 [P] [US3] Crear `frontend/src/hooks/useRanking.ts`: lee `league`, `team`, `position` y `page` con `useBoardFilters` (mismos parámetros en la dirección, sin los vacíos) y pide `getRanking`
- [X] T090 [US3] Escribir `frontend/tests/hooks/useCurrentQuotes.test.tsx`, `frontend/tests/hooks/usePlayerQuote.test.tsx`, `frontend/tests/hooks/useQuoteHistory.test.tsx` y `frontend/tests/hooks/useRanking.test.tsx`: una sola consulta por lote; "sin cotización" vs error; el rango cambia la consulta; los filtros salen de la dirección y la página se reinicia al filtrar

### Componentes de la Historia 3

- [X] T091 [P] [US3] Crear `frontend/src/components/VariationBadge.tsx` y `VariationBadge.module.css`: signo, texto ("sube"/"baja") y triángulo ▲/▼; el color solo refuerza (FR-059); sin variación no muestra nada
- [X] T092 [P] [US3] Crear `frontend/src/components/QuoteTag.tsx` y `QuoteTag.module.css`: cotización compacta para el post-it (estados `loading`, `none` "sin cotización", `ready` con valor y `VariationBadge`, `error`); sin `ul`/`ol`, sin botones ni enlaces
- [X] T093 [P] [US3] Crear `frontend/src/components/QuotePanel.tsx` y `QuotePanel.module.css`: etiquetas de texto "Valor del jugador", "Precio por token" y la variación respecto de la semana anterior, con la estrategia y versión; no repite equipo, posición ni liga
- [X] T094 [P] [US3] Crear `frontend/src/components/QuoteChart.tsx` y `QuoteChart.module.css`: SVG propio `aria-hidden` con una línea de una cotización por semana (`lastPerWeek`), y encima un `<button>` por punto posicionado en porcentajes (`utils/chart.ts`) con nombre accesible "Cotización de la semana del …"; al enfocar, pasar el mouse o activar muestra valor, semana, tipo, estrategia y versión; rango sin puntos → mensaje "No hay cotizaciones en ese rango" sin romperse
- [X] T095 [P] [US3] Crear `frontend/src/components/QuoteTable.tsx` y `QuoteTable.module.css`: la alternativa en texto con los mismos puntos del gráfico, dentro de un `<details>`
- [X] T096 [P] [US3] Crear `frontend/src/components/DateRangePicker.tsx` y `DateRangePicker.module.css`: dos `<input type="date">` con etiqueta y tres botones rápidos ("Últimas 8 semanas", "Últimas 26 semanas", "Todo el historial"); devuelve un `QuoteRange` por callback
- [X] T097 [P] [US3] Crear `frontend/src/components/RankingList.tsx` y `RankingList.module.css`: tabla con posición en el ranking, jugador (enlace a `/jugadores/:id` recibido como prop, sin router), equipo, liga, cotización, variación, estrategia y versión; por debajo de un ancho pasa a tarjetas apiladas con CSS (sin desplazamiento horizontal a 360 px)
- [X] T098 [US3] Modificar `frontend/src/components/PostIt.tsx` (y su `.module.css`) para aceptar una prop opcional `quote?: QuoteBadge` que renderiza `QuoteTag` dentro del único enlace; sin la prop la salida es idéntica a la actual (la suite de la 004 no cambia) (depende de T092)
- [X] T099 [US3] Modificar `frontend/src/components/ScoutSheet.tsx` (y su `.module.css`) para aceptar un espacio opcional (`children` o prop `quoteSlot`) donde va el panel de cotización; sin él, idéntico a hoy
- [X] T100 [US3] Escribir `frontend/tests/components/VariationBadge.test.tsx`, `QuoteTag.test.tsx`, `QuotePanel.test.tsx`, `QuoteChart.test.tsx`, `DateRangePicker.test.tsx` y `RankingList.test.tsx` (todos en `frontend/tests/components/`): signo y texto sin depender del color; "sin cotización"; un botón por semana alcanzable con teclado y su detalle; rango vacío con mensaje; la tabla alternativa tiene los mismos puntos; botones rápidos del rango; filas del ranking con estrategia y versión

### Pantallas de la Historia 3

- [X] T101 [US3] Modificar `frontend/src/pages/BoardPage.tsx` para pedir las cotizaciones de la hoja visible con `useCurrentQuotes` (una consulta por hoja, FR-062) y pasar el `QuoteBadge` a cada `PostIt`; un error del lote no rompe la pizarra (los post-its muestran el error de cotización y hay "Reintentar"); sigue habiendo un único `list` (depende de T087, T098)
- [X] T102 [US3] Modificar `frontend/src/pages/PlayerPage.tsx` (y `PlayerPage.module.css`) para sumar, en el espacio de `ScoutSheet`, `QuotePanel` (`usePlayerQuote`) y la sección de evolución con `DateRangePicker`, `QuoteChart` y `QuoteTable` (`useQuoteHistory`); estados de carga, error con "Reintentar", "sin cotización" (los datos del catálogo siguen visibles) y rango vacío (depende de T088, T093 a T096, T099)
- [X] T103 [US3] Crear `frontend/src/pages/RankingPage.tsx` y `RankingPage.module.css`: filtros de liga, equipo y posición reutilizando los componentes de filtro de la pizarra (`TeamSelect`, los de liga y posición), `RankingList`, `Pager`; filtros y hoja en la dirección (`league`, `team`, `position`, `page`), reinicio de la hoja al filtrar; estados de carga, error con "Reintentar", vacío y hoja fuera de rango (depende de T089, T097)
- [X] T104 [US3] Modificar `frontend/src/router/routes.tsx` (ruta pública `ranking` → `RankingPage`, hija de `AppLayout`), `frontend/src/components/TopBar.tsx` y `frontend/src/pages/AppLayout.tsx` (enlace "Ranking" en el encabezado, sin `ul`/`ol`) (depende de T103)
- [X] T105 [US3] Escribir `frontend/tests/pages/BoardQuotes.test.tsx` (cotización en cada post-it con **una** consulta de lote por hoja, contando pedidos en MSW; jugador sin cotización; error del lote no rompe la pizarra; carga y error con reintentar), `frontend/tests/pages/PlayerQuotes.test.tsx` (valor, precio por token y variación; 404 de la cotización con el jugador existente conserva el catálogo; gráfico con un punto por semana; rango que recorta; rango vacío; los textos del catálogo no se repiten) y `frontend/tests/pages/RankingPage.test.tsx` (orden descendente; filtros y hoja en la dirección y recargar reproduce la vista; vacío; carga; error; navegación sin sesión, US3.11)
- [X] T106 [US3] Correr `cd frontend && npm test && npm run lint && npm run build` con la suite de la 004 **sin modificar** (salvo T085) y verificar a mano a 360 px y solo con teclado `/pizarra`, `/jugadores/:id` y `/ranking` (`quickstart.md`, 7.1)

**Checkpoint**: un visitante ve las cotizaciones en la pizarra, la ficha y el ranking.

---

## Fase 6: Historia 4 - Registrar y configurar estrategias de valuación (Prioridad: P4)

**Objetivo**: un administrador crea, lista, edita (versión nueva) y activa estrategias por API.

**Prueba independiente**: como ADMIN, crear, editar, listar y activar estrategias válidas e inválidas;
versiones, activa única, rechazos y que ninguna cotización cambia al crear o editar una no activa.

### Implementación de la Historia 4

- [X] T107 [US4] Crear `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/service/strategy/StrategyService.java` (`@Transactional` donde escribe): `create(...)` (catálogo → `StrategyRepository.save`; una `DataIntegrityViolationException` del índice de nombre se traduce a `DuplicateStrategyNameException`), `list()` (en orden de creación) y `get(id)` (404 si no existe)
  - Implementado el 2026-10-21: los servicios devuelven `modelo/strategy/StrategyDetail` (`strategy`, `active`), un record nuevo solo con JDK, porque `Strategy` no sabe si es la activa y `StrategyResponse` lo necesita. `create` recibe los `WeightSet` ya armados: el DTO los construye con `WeightSet.of` (el modelo informa la métrica desconocida y de qué juego es la suma). La violación de integridad se captura dentro de la transacción: con `IDENTITY` el insert sale en el acto. Los DTO de request ignoran propiedades desconocidas, así `requote` se descarta hasta US5
- [X] T108 [US4] Crear `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/service/strategy/StrategyChangeWriter.java` con `@Transactional StrategyCatalog apply(StrategyChange)` (guarda la versión nueva y mueve la marca `active`, sin recotizar y sin candados)
- [X] T109 [US4] Crear `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/service/strategy/StrategyChangeService.java` (sin `@Transactional`): `activate(strategyId)` y `createVersion(strategyId, parámetros)`: carga el catálogo, aplica la operación del modelo, llama a `StrategyChangeWriter.apply` y, si la versión activa cambió, invoca `QuoteCacheInvalidator.invalidateAll()` después del commit (D21). Editar una no activa no toca la activa ni las cotizaciones (depende de T108)
- [X] T110 [P] [US4] Crear en `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/controller/strategy/dto/` `StrategyParametersRequest` (`scaleFactor?`, `minimumValue?`, `defaultWeights` `Map<String, BigDecimal>` obligatorio, `positionWeights` `Map<Position, Map<String, BigDecimal>>` opcional), `CreateStrategyRequest` (`name` `@NotBlank` + parámetros), `NewVersionRequest` (parámetros), `ActivateStrategyRequest` (cuerpo opcional; el campo `requote` se suma en US5), `StrategyVersionResponse`, `StrategyResponse` (`id`, `name`, `active`, `versions`) y `StrategyChangeResponse` (`strategy`, `requotation` en `null` por ahora), según `contracts/strategies-api.yaml`. Los pesos llegan como `Map<String, ...>` para que el modelo informe la métrica no admitida (D13)
- [X] T111 [US4] Crear `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/controller/strategy/StrategyController.java`: `POST /strategies` (201), `GET /strategies`, `GET /strategies/{id}`, `POST /strategies/{id}/versions` (201, `StrategyChangeResponse`) y `POST /strategies/{id}/activate` (200, `StrategyChangeResponse`); Swagger `@Tag(name = "Estrategias")`, `@ApiResponse` 200/201/400/401/403/404/409 y los dos `@SecurityRequirement` (depende de T107, T109, T110)

### Tests de la Historia 4

- [X] T112 [P] [US4] Escribir `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/service/strategy/StrategyServiceTest.java` (Mockito): crear guarda v1 inactiva; nombre repetido → 409 (también por violación de integridad); listar y consultar; 404
- [X] T113 [P] [US4] Escribir `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/service/strategy/StrategyChangeServiceTest.java` (Mockito): activar otra cambia la activa e invalida el caché; editar la activa deja activa la versión nueva e invalida; editar una inactiva no cambia la activa ni invalida; activar la ya activa no cambia nada; no se toma ningún candado
- [X] T114 [US4] Escribir `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/e2e/StrategyControllerIT.java` (MockMvc como ADMIN): A activa y B sembradas con los pesos de A (US4.1); crear válida → 201 v1 inactiva, con factor 10 y mínimo 10,00 si se omiten (4.3, 4.5); 400 por pesos que suman 0,95 (con el mensaje), peso negativo, 3 decimales, `GOLES`, factor 0 y mínimo 0,00; 409 por "rendimiento de PARTIDO " (4.6); editar inactiva crea v2 y la v1 queda intacta (4.7); editar la activa deja activa la versión nueva (4.8); las cotizaciones guardadas conservan la v1 tras editar A (4.9, SC-006); exactamente una activa (4.10, SC-015); listado con todas las versiones (4.11); 404 de estrategia inexistente
- [X] T115 [P] [US4] Escribir `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/e2e/StrategyAccessIT.java`: los cinco endpoints de `/strategies/**` sin credencial → 401 y con `USER` → 403, y el catálogo queda igual (US4.12, US5.13, SC-011)

**Checkpoint**: las estrategias se administran por API; activar o editar con el comportamiento por defecto ("la semana siguiente").

---

## Fase 7: Historia 5 - Cambiar la estrategia activa y elegir cuándo recotizar (Prioridad: P5)

**Objetivo**: al cambiar la activa, el administrador elige `AHORA` (recotiza la última semana cerrada
sin acumular) o `PROXIMA_SEMANA` (por defecto).

**Prueba independiente**: con historial, activar otra estrategia con cada opción y verificar qué
cotizaciones se agregan, desde qué precio, que las anteriores se conservan y que repetir no acumula.

### Implementación de la Historia 5

- [X] T116 [P] [US5] Crear `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/modelo/strategy/RequoteTiming.java` (`NOW("AHORA")`, `NEXT_WEEK("PROXIMA_SEMANA")`; `fromWire(String)`: `null` → `NEXT_WEEK`, desconocido → excepción) y en `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/modelo/strategy/exception/` `InvalidRequoteTimingException` (400, "La opción 'AHORA2' no es válida: use AHORA o PROXIMA_SEMANA.") y `StrategyChangeConflictException` (409, "La estrategia cambió mientras se guardaba: reintente.")
- [X] T117 [US5] Agregar a `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/modelo/quote/WeeklyQuotation.java` `requoteWeek(QuoteWeek, StrategyVersion, List<Player> activePlayers, List<PlayerMatchStats> weekStats, Map<Long, Quote> lastBeforeWeek, Map<Long, Quote> latestOfWeek, Instant now)`: genera `REQUOTED` solo para los activos con una cotización en esa semana cuya estrategia **no** es la versión nueva, con `validFrom = now`, base = la previa a la semana (nunca las de la propia semana: no acumula) y el mismo `baseValue` que la calculada que reemplaza; pares = todos los activos de la posición; reutiliza el cálculo de scores de `quoteWeek`
- [X] T118 [US5] Agregar a `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/persistence/sql/interfaces/quote/QuoteSQLDAO.java` y `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/persistence/repository/quote/QuoteRepository.java` `Map<Long, Quote> findLatestOfWeek(QuoteWeek)` (por jugador, la de mayor `validFrom` con ese `week_start`)
- [X] T119 [US5] Agregar a `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/service/quote/QuoteWeekWriter.java` `requoteLastWeek(StrategyVersion, Instant now, RecalculationReport.Builder)` con `@Transactional(propagation = Propagation.MANDATORY)` (corre dentro de la transacción del cambio de estrategia): cotiza con la versión nueva las semanas pendientes según `QuotingPlan` (`INITIAL` + `CALCULATED`), toma la última semana cerrada `L` y, si tiene cotizaciones, genera las `REQUOTED` con `WeeklyQuotation.requoteWeek`; si `L` sigue pendiente queda en el informe con su motivo (depende de T117, T118)
- [X] T120 [US5] Agregar a `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/service/strategy/StrategyChangeWriter.java` `@Transactional RecalculationReport applyAndRequote(StrategyChange, Instant now)`: guarda el cambio y recotiza en la **misma** transacción (origen `STRATEGY_CHANGE`); una `DataIntegrityViolationException` se traduce a `StrategyChangeConflictException`; si algo falla, la activa no cambia (depende de T119)
- [X] T121 [US5] Ampliar `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/service/strategy/StrategyChangeService.java`: `activate(strategyId, RequoteTiming)` y `createVersion(strategyId, parámetros, RequoteTiming)`; la opción aplica solo si el cambio deja activa una versión distinta de la que cotizó la última semana (activar otra o editar la activa); con `NOW` toma `RecalculationLock` **fuera** de la transacción, llama a `applyAndRequote`, suelta el candado, invalida el caché y escribe el informe con `RecalculationReportLogger`; con `NEXT_WEEK` llama a `apply` sin candados; crear o editar una no activa ignora la opción; activar la ya activa en la versión que cotizó la última semana no hace nada; si el candado está ocupado propaga el 409 sin aplicar el cambio (D14, FR-038) (depende de T116, T120)
- [X] T122 [US5] Agregar el campo opcional `requote` (texto) a `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/controller/strategy/dto/ActivateStrategyRequest.java` y `NewVersionRequest.java`; en `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/controller/strategy/StrategyController.java` pasarlo por `RequoteTiming.fromWire` y completar `StrategyChangeResponse.requotation` con `RecalculationReportResponse` (o `null`); documentar en Swagger el 409 de los dos endpoints y que la opción se ignora en una no activa (depende de T121)

### Tests de la Historia 5

- [X] T123 [P] [US5] Escribir `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/modelo/strategy/RequoteTimingTest.java`: `AHORA`, `PROXIMA_SEMANA`, `null` → `NEXT_WEEK`, `AHORA2` → 400 con el valor en el mensaje
- [X] T124 [US5] Agregar casos de `requoteWeek` (sin cambiar los existentes) a `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/modelo/quote/WeeklyQuotationTest.java`: inicial 100,00, calculadas 103,00 y 101,00 con A; B con +0,10 → `REQUOTED` 104,00 desde 103,00 y la de 101,00 se conserva (US5.3, SC-002); repetir con la misma versión no genera nada (5.4); otra estrategia C parte de 103,00 y no de 104,00 (5.5); la recotizada lleva el mismo `baseValue` que la calculada; inactivos fuera
- [X] T125 [US5] Agregar casos (sin cambiar los existentes) a `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/service/strategy/StrategyChangeServiceTest.java`: `NOW` toma el candado y lo suelta después del commit; con el candado ocupado no llama al writer (la activa no cambia); `NEXT_WEEK` y opción ausente no toman candado; la opción se ignora al editar una no activa; activar la ya activa en la versión que cotizó no hace nada
- [X] T126 [US5] Escribir `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/service/quote/RequoteIT.java` (H2, `Clock` fijo): US5.3 completo con 104,00 y la calculada conservada; repetir no suma filas (5.4); C desde 103,00 (5.5); última semana ya cotizada con la versión nueva no se repite (5.6); semanas pendientes se cotizan con la nueva y la última se recotiza (5.7); editar la activa con `AHORA` (5.8); sistema sin cotizaciones solo cambia la activa (5.14); el cierre de la semana siguiente parte de la recotizada: 104,00 con +0,20 → 106,00 (5.15); la evolución muestra la calculada y la recotizada de la misma semana con su tipo, estrategia y versión (US2.10)
- [X] T127 [US5] Escribir `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/service/strategy/StrategyChangeIT.java` (H2, determinístico, sin depender de la hora): con `RecalculationLock` tomado a propósito, `AHORA` → 409 y ni la activa ni las cotizaciones cambian (5.11, FR-038); con `SyncLock` tomado, lo mismo; `PROXIMA_SEMANA` con un recálculo en curso se aplica (5.12) y el recálculo termina con la estrategia que tenía al empezar (FR-039); una falla dentro de `applyAndRequote` deja la activa intacta
- [X] T128 [US5] Agregar casos (sin cambiar los existentes) a `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/e2e/StrategyControllerIT.java`: activar sin cuerpo o con `PROXIMA_SEMANA` → `requotation: null` y ninguna cotización cambia (5.1, 5.2); `AHORA` → `requotation.origin: STRATEGY_CHANGE`; `"requote":"AHORA2"` → 400; crear o editar una no activa con `AHORA` → `requotation: null` (5.9); activar la ya activa → nada cambia (5.10)

**Checkpoint**: US1, US2, US4 y US5 completos por API.

---

## Fase 8: Historia 6 - Recálculo automático y convivencia con las sincronizaciones (Prioridad: P6)

**Objetivo**: el recálculo corre solo los lunes a las 06:00 (hora de Argentina) y al arrancar si hay
semanas pendientes; nunca dos a la vez ni junto con una sincronización.

**Prueba independiente**: scheduler y arranque con dependencias simuladas; recálculo manual con otro
recálculo o una sincronización en curso → 409.

### Implementación de la Historia 6

- [X] T129 [P] [US6] Crear `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/config/QuoteRecalculationProperties.java` (`@ConfigurationProperties("futbolmarket.quotes.recalculation")`, `@Validated`, como `FootballDataProperties.Sync`): `cron` (expresión válida de Spring o `"-"`), `zone` (un `ZoneId` válido) y `onStartup`; una propiedad inválida impide el arranque. Registrarla en `@EnableConfigurationProperties` de `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/config/ApplicationConfig.java` (ya tiene `@EnableScheduling` y `@EnableAsync`)
- [X] T130 [P] [US6] Agregar (sin reemplazar nada) las claves de `futbolmarket.quotes.recalculation` a `backend/src/main/resources/application.yaml` (`cron: "0 0 6 * * MON"`, `zone: America/Argentina/Buenos_Aires`, `on-startup: true`) y a `backend/src/test/resources/application-test.yml` (`cron: "-"`, `on-startup: false`), según `contracts/configuration.md`
- [X] T131 [US6] Agregar a `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/service/quote/QuoteRecalculationService.java` `boolean hasClosedUnquotedWeeks()` (arma `QuotingPlan` con lo guardado, sin candados ni escritura)
- [X] T132 [US6] Crear `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/service/quote/QuoteScheduler.java` (como `service/sync/SyncScheduler`): `@Scheduled(cron = "${futbolmarket.quotes.recalculation.cron}", zone = "${futbolmarket.quotes.recalculation.zone}")` que llama a `recalculate(WEEKLY)`; si hay otro recálculo o una sincronización (`RecalculationInProgressException` o `RecalculationBlockedBySyncException`), lo omite con un `WARN` que dice el motivo (depende de T129, T130)
- [X] T133 [US6] Crear `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/service/quote/QuoteStartupRecalculation.java` (como `service/sync/StartupSync`): `@Async @EventListener(ApplicationReadyEvent.class)`; solo si `onStartup` es `true` y `hasClosedUnquotedWeeks()`, llama a `recalculate(STARTUP)` sin demorar el arranque; ante conflicto, `WARN` y la semana queda para el cron (depende de T129, T131)

### Tests de la Historia 6

- [X] T134 [P] [US6] Escribir `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/config/QuoteRecalculationPropertiesTest.java` (como `FootballDataPropertiesTest`): defaults; `"-"` válido; cron inválido y zona inválida rechazados al enlazar
- [X] T135 [P] [US6] Escribir `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/service/quote/QuoteSchedulerTest.java`: llama con origen `WEEKLY` (US6.1); con recálculo o sincronización en curso no propaga la excepción y registra el motivo (US6.4)
- [X] T136 [P] [US6] Escribir `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/service/quote/QuoteStartupRecalculationTest.java`: con semanas pendientes dispara `STARTUP` (US6.2, 6.7); sin pendientes no dispara (6.3); con `onStartup` en `false` no dispara; con una sincronización en curso lo omite con `WARN` (6.4)
- [X] T137 [US6] Escribir `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/e2e/RecalculationConflictIT.java` (MockMvc como ADMIN, candados tomados a propósito, sin hilos ni esperas): con `RecalculationLock` tomado → 409 "Ya hay un recálculo en curso." (US6.6, FR-049); con `SyncLock` tomado → 409 "No se puede recalcular: hay una sincronización en curso." (US6.5, FR-050); en los dos casos 0 cotizaciones creadas

**Checkpoint**: el recálculo se dispara solo y convive con las sincronizaciones.

---

## Fase 9: Historia 7 - Simular una estrategia sin guardar nada (Prioridad: P7)

**Objetivo**: un administrador simula una estrategia guardada o parámetros, en memoria, y ve por
jugador la cotización actual y la simulada.

**Prueba independiente**: simular antes y después no cambia cotizaciones, estrategias ni activa; dos
simulaciones iguales dan lo mismo; simular la activa sobre un historial suyo coincide con lo vigente.

### Implementación de la Historia 7

- [X] T138 [P] [US7] Crear `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/modelo/quote/exception/InvalidSimulationRequestException.java` (400, "Indique una estrategia guardada o parámetros, pero no las dos cosas ni ninguna."), `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/modelo/quote/SimulatedQuote.java` (`Player`, `currentValue` nulo si no tiene, `simulatedValue`, `difference()`) y `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/modelo/quote/SimulationResult.java` (`QuotePage<SimulatedQuote>`, `StrategyVersionRef` o nula si fueron parámetros, `weeksSimulated`, `pendingWeeks`)
- [X] T139 [US7] Agregar a `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/persistence/sql/interfaces/quote/QuoteSQLDAO.java` y `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/persistence/repository/quote/QuoteRepository.java` `Map<Long, Quote> findLatestByPlayer()` (la cotización actual de cada jugador)
- [X] T140 [US7] Crear `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/service/quote/QuoteSimulationService.java` (solo lectura; no inyecta nada que escriba ni toma candados): valida "estrategia o parámetros, uno de los dos"; estrategia guardada → su versión más reciente (404 si no existe); parámetros → construye una `StrategyVersion` con ellos (mismas validaciones, FR-057); lee una sola vez jugadores activos, partidos terminados, todas las estadísticas (`PlayerMatchStatsRepository.findAll()`) y `findLatestByPlayer()`; arma `QuotingPlan` como si no hubiera nada cotizado y reproduce en memoria cada semana con `WeeklyQuotation.quoteWeek` desde 100,00 (el mismo código que el recálculo); aplica los filtros de liga, equipo y posición y pagina en memoria ordenando por cotización simulada desc, nombre e id (el criterio del ranking, para que sea determinista) (depende de T138, T139)
- [X] T141 [P] [US7] Crear en `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/controller/quote/dto/` `SimulationRequest` (`strategyId?`, `parameters?` que reutiliza `controller/strategy/dto/StrategyParametersRequest`), `SimulationRowResponse` (`playerId`, `name`, `team`, `league`, `position`, `currentValue`, `simulatedValue`, `difference`) y `SimulationPageResponse` (página + `strategy`, `weeksSimulated`, `pendingWeeks`), según `contracts/quotes-admin-api.yaml`
- [X] T142 [US7] Agregar a `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/controller/quote/QuoteAdminController.java` `POST /quotes/simulate` con `league`, `team`, `position`, `page` (default 0) y `size` (default 10, máximo 50) como en el catálogo; Swagger `@Tag(name = "Simulación")` y los dos `@SecurityRequirement` (depende de T140, T141)

### Tests de la Historia 7

- [X] T143 [P] [US7] Escribir `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/service/quote/QuoteSimulationServiceTest.java` (Mockito): las dos cosas o ninguna → 400; estrategia inexistente → 404; parámetros inválidos con las mismas reglas que una estrategia; jugador sin cotización actual → `currentValue` nulo; filtros y paginación; orden determinista
- [X] T144 [US7] Agregar un caso (sin cambiar los existentes) a `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/persistence/repository/quote/QuoteRepositoryIT.java` para `findLatestByPlayer()` con recotizadas
- [X] T145 [US7] Escribir `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/service/quote/SimulationIT.java` (H2): cotizaciones, estrategias y activa idénticas antes y después (US7.1, FR-056); dos simulaciones iguales dan resultados idénticos (7.6, SC-010); con un historial cotizado íntegramente con la versión vigente de la activa, la simulada coincide con la actual (7.7); con parámetros se calcula sin guardarlos (7.2)
- [X] T146 [US7] Escribir `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/e2e/SimulationControllerIT.java` (MockMvc como ADMIN): estrategia guardada → 200 con `strategy`; parámetros → 200 con `strategy: null`; pesos que no suman 1,00 → 400 (7.3); inexistente → 404 (7.4); las dos cosas o ninguna → 400 (7.5); filtros y paginación del catálogo y 400 por `position` inválida (7.8)
- [X] T147 [US7] Agregar casos (sin cambiar los existentes) a `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/e2e/QuoteAdminAccessIT.java`: `POST /quotes/simulate` sin credencial → 401 y con `USER` → 403 (US7.9)

**Checkpoint**: todas las historias de backend completas.

---

## Fase 10: Historia 8 - Administración desde la aplicación web (Prioridad: P8)

**Objetivo**: una sección de administración, solo para ADMIN, para listar, crear, editar y activar
estrategias (eligiendo cuándo recotizar), disparar el recálculo y simular.

**Prueba independiente**: con MSW, como ADMIN y como usuario común, recorrer cada función; quien no es
ADMIN no ve la sección ni puede usarla por la dirección.

### Base de la Historia 8

- [X] T148 [P] [US8] Agregar a `frontend/src/types/quotes.ts` los tipos de administración desde `contracts/strategies-api.yaml` y `contracts/quotes-admin-api.yaml`: `METRICS` (las 19) y `Metric`, `REQUOTE_TIMINGS` (`AHORA`, `PROXIMA_SEMANA`) y `RequoteTiming`, `PENDING_REASONS` y `PendingReason`, `WeightMap`, `StrategyVersionDto`, `StrategyDto`, `StrategyInput`, `RecalculationReport`, `StrategyChange`, `SimulationRequest`, `SimulationRow`, `SimulationPage` y `WeightsFormState` (texto por métrica y por juego)
- [X] T149 [P] [US8] Crear `frontend/src/utils/metrics.ts` (etiquetas en español de las 19 métricas: "goles", "quites" para `TACKLES`, "regates" para `SUCCESSFUL_DRIBBLES`, "valla invicta" para `CLEAN_SHEETS`...; de los motivos de semana pendiente y de los orígenes del recálculo) y `frontend/src/utils/weights.ts` (parseo del texto con coma o punto, suma exacta en centésimos enteros sin `float`, validación por juego: ≥ 0, a lo sumo 2 decimales, suma 100; conversión `WeightsFormState` ↔ `WeightMap`)
- [X] T150 [US8] Crear `frontend/src/services/strategyService.ts` (`createStrategy`, `listStrategies`, `getStrategy`, `createVersion(id, input, requote)`, `activateStrategy(id, requote)`) y `frontend/src/services/recalculationService.ts` (`recalculate()`, `simulate(request, query)`), todos sobre `request()` (depende de T148)
- [X] T151 [US8] Agregar a `frontend/tests/mocks/quotes.ts` y `frontend/tests/mocks/quoteHandlers.ts` (archivos creados en T084) los datos y handlers de `*/api/strategies`, `*/api/strategies/:id`, `.../versions`, `.../activate`, `*/api/quotes/recalculate` y `*/api/quotes/simulate`, con 400 y 409 en español y un modo de conflicto activable desde el test; el administrador de los tests se obtiene sobreescribiendo `GET */api/auth/me` con `server.use(...)` dentro de cada test (no tocar `resetMockDb()` ni los usuarios sembrados de `handlers.ts`)
  - Implementado el 2026-10-21: el estado de las estrategias y el modo de conflicto viven en `quoteHandlers.ts` y se vuelven a la semilla con `resetAdminMocks()`, que los tests de administración llaman en su `beforeEach` (`resetMockDb()` no se tocó). El conflicto se activa con `setAdminConflict(RECALCULATION_IN_PROGRESS | SYNC_IN_PROGRESS)` y afecta al recálculo y a los cambios con `AHORA` (la simulación no toma candados). El ADMIN se obtiene con `server.use(adminProfileHandler)`; los handlers de administración piden credencial (401 sin ella) pero no miran el rol. `mockStrategies()` devuelve lo guardado para verificar qué cambió
- [X] T152 [P] [US8] Escribir `frontend/tests/utils/weights.test.ts` (suma exacta 0,1 + 0,2 + 0,7 = 1,00; 0,95 inválido; 3 decimales; negativo; coma decimal) y `frontend/tests/utils/metrics.test.ts` (las 19 con etiqueta; motivos pendientes)

### Hooks, guarda y componentes de la Historia 8

- [X] T153 [P] [US8] Crear `frontend/src/hooks/useMutation.ts` (comandos: `run(args)`, estados `idle`/`running`/`success`/`error`, error con el mensaje del `ApiError` y el status para distinguir 409; no corre por dependencias como `useAsync`)
- [X] T154 [P] [US8] Crear `frontend/src/hooks/useStrategies.ts` (lista con `retry`), `frontend/src/hooks/useRecalculation.ts` (dispara con `useMutation` y guarda el último informe) y `frontend/src/hooks/useSimulation.ts` (pedido + filtros y hoja, con `useMutation`) (depende de T150, T153)
- [X] T155 [US8] Crear `frontend/src/router/RequireRole.tsx`: compone `RequireSession` (un visitante va a `/login`) y, con sesión, mira `profile.role`; si no es `ADMIN`, muestra un aviso de falta de permiso (`role="alert"`) con un enlace a la pizarra y ningún control de administración
- [ ] T156 [P] [US8] Crear `frontend/src/components/WeightsEditor.tsx` (+ `.module.css`): las 19 métricas de un juego con su etiqueta, la suma en vivo y el aviso si no suma 1,00; valores como texto, y `frontend/src/components/StrategyForm.tsx` (+ `.module.css`): nombre (solo al crear), factor, mínimo, juego por defecto y juegos por posición opcionales; envío deshabilitado mientras algún juego definido no sume exactamente 1,00 (FR-066); muestra el error del servidor en `role="alert"` y conserva lo escrito; al editar la activa incluye `RequoteChoice`
- [ ] T157 [P] [US8] Crear `frontend/src/components/StrategyList.tsx` (+ `.module.css`: cada estrategia con sus versiones y cuál es la activa, botones "Editar" y "Activar"), `frontend/src/components/RequoteChoice.tsx` (+ `.module.css`: grupo de radios "Recotizar ahora" / "Recotizar la semana siguiente" con la explicación de cada uno, la segunda preseleccionada, y botón "Confirmar"; nada se envía hasta confirmar) y `frontend/src/components/AdminNav.tsx` (+ `.module.css`: enlaces a estrategias, recálculo y simulación, sin `ul`/`ol` si comparte pantalla con la pizarra)
- [ ] T158 [P] [US8] Crear `frontend/src/components/ReportView.tsx` (+ `.module.css`: semanas cotizadas con iniciales, calculadas y recotizadas, y pendientes con su motivo traducido; informe vacío "No había semanas para cotizar") y `frontend/src/components/SimulationTable.tsx` (+ `.module.css`: por jugador cotización actual, simulada y diferencia con signo y texto; tarjetas apiladas a 360 px)

### Pantallas de la Historia 8

- [ ] T159 [US8] Crear `frontend/src/pages/AdminStrategiesPage.tsx` (+ `.module.css`): `AdminNav`, `StrategyList`, alta con `StrategyForm`, edición (versión nueva), activación con `RequoteChoice`; muestra el informe de recotización si vino; estados de carga, error y 409 con el motivo sin perder lo que se estaba haciendo (depende de T154, T156, T157)
- [ ] T160 [US8] Crear `frontend/src/pages/AdminRecalculationPage.tsx` (+ `.module.css`): botón "Recalcular cotizaciones", `ReportView`, carga, error y 409 con el motivo conservando la pantalla (depende de T154, T158)
- [ ] T161 [US8] Crear `frontend/src/pages/AdminSimulationPage.tsx` (+ `.module.css`): elegir una estrategia guardada o cargar parámetros (`WeightsEditor` y campos de factor y mínimo), filtros de liga, equipo y posición, `SimulationTable` y `Pager`; validaciones y estados (depende de T154, T156, T158)
- [ ] T162 [US8] Modificar `frontend/src/router/routes.tsx` (`admin` → redirige a `/admin/estrategias`; `admin/estrategias`, `admin/recalculo` y `admin/simulacion` envueltas en `RequireRole`), `frontend/src/components/TopBar.tsx` y `frontend/src/pages/AppLayout.tsx` (enlace "Administración" solo si `profile.role === 'ADMIN'`) (depende de T155, T159 a T161)

### Tests de la Historia 8

- [ ] T163 [US8] Escribir `frontend/tests/components/WeightsEditor.test.tsx`, `StrategyForm.test.tsx`, `RequoteChoice.test.tsx`, `ReportView.test.tsx` y `SimulationTable.test.tsx` (en `frontend/tests/components/`): suma en vivo; no deja enviar si no suma 1,00; error del servidor en español sin perder lo escrito; "la semana siguiente" preseleccionada y nada se envía hasta confirmar; informe con pendientes e informe vacío; diferencia con signo
- [ ] T164 [US8] Escribir `frontend/tests/pages/AdminAccess.test.tsx`: sin sesión `/admin/estrategias` lleva a `/login` sin controles (US8.2); usuario común sin enlace "Administración" y con aviso de falta de permiso por la dirección (8.1); ADMIN ve la sección (8.3)
- [ ] T165 [US8] Escribir `frontend/tests/pages/AdminStrategiesPage.test.tsx` (lista con versiones y activa; crear aparece en la lista; rechazo 409 por nombre muestra el motivo y conserva lo escrito; editar crea versión; activar exige elegir y confirma; 409 de "recotizar ahora" muestra el motivo), `frontend/tests/pages/AdminRecalculationPage.test.tsx` (informe completo; informe vacío; 409 conserva la pantalla) y `frontend/tests/pages/AdminSimulationPage.test.tsx` (estrategia guardada y parámetros; tabla con actual, simulada y diferencia; filtros y hoja; validaciones; carga y error)
- [ ] T166 [US8] Correr `cd frontend && npm test && npm run lint && npm run build` y verificar a mano a 360 px y con teclado las tres pantallas de administración (`quickstart.md`, 7.2 y 7.3)

**Checkpoint**: todas las historias funcionan de punta a punta.

---

## Fase 11: Pulido y aspectos transversales

**Propósito**: lo que cruza varias historias, la medición de volumen, los ajustes autorizados de la 004
y la definición de terminado.

- [ ] T167 [P] Escribir `frontend/tests/pages/NoMarketActions.test.tsx` (archivo nuevo): recorrer `/pizarra`, `/jugadores/:id`, `/ranking` y, como ADMIN, `/admin/estrategias`, `/admin/recalculo` y `/admin/simulacion`, y verificar que ningún botón ni enlace ofrece comprar, vender, sumar al plantel ni órdenes, y que no se muestra presupuesto, tokens ni portfolio de un usuario (FR-071, SC-020); el precio por token del jugador sí puede aparecer como texto
- [ ] T168 Aplicar **solo** los cambios de redacción autorizados por G3 (tabla "Tests de la 004 que cambian" de `plan.md`, D25): en `frontend/tests/pages/NoPurchaseActions.test.tsx` quitar `precio` y `valor` de la expresión `FORBIDDEN` (línea 7) y actualizar el comentario de la línea 1 ("ninguna pantalla ofrece compra, venta ni plantel"), sin tocar `PRICE_TEXT` ni las estrellas; en `frontend/tests/components/PostIt.test.tsx` quitar `precio` y `valor` de la expresión del test "no contiene botones ni textos de compra, venta, plantel, precio o valor" y renombrarlo ("no contiene botones ni textos de compra, venta ni plantel"); en `frontend/tests/pages/PlayerPage.test.tsx` quitar `precio` de la expresión del test "la ficha no tiene botones de compra, venta ni similares". Ningún otro test ni aserción cambia; correr `npm test`
- [ ] T169 [P] Escribir `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/service/quote/QuoteVolumeBenchmarkIT.java` (`@EnabledIfEnvironmentVariable(named = "QUOTES_BENCHMARK", matches = "true")`, H2 en archivo, fuera del build normal): 2.600 jugadores y 40 semanas de estadísticas sintéticas, recálculo completo; registra el tiempo de una semana, de las 40, la cantidad de filas y el tamaño del archivo, y verifica los topes de SC-008, SC-009 y SC-010. Correrlo con `cd backend && QUOTES_BENCHMARK=true ./gradlew test --tests '*QuoteVolumeBenchmarkIT' -i` y anotar los resultados en `specs/007-cotizacion-estrategias/research.md` (D15). Si un tope no se cumple, **no** aplicar el plan B de `SEQUENCE` sin el aval del equipo (D28, punto 8)
- [ ] T170 [P] Revisar Swagger en `/swagger-ui.html` con `./gradlew bootRun`: los 12 endpoints nuevos con `@Tag` (`Cotizaciones`, `Ranking`, `Recálculo`, `Simulación`, `Estrategias`), `@Operation` y `@ApiResponse`; los de administrador con los dos esquemas de Authorize y ejecutables desde la página (`backend/src/main/java/ar/edu/unq/desapp/futbolmarket/controller/quote/QuoteController.java`, `QuoteAdminController.java`, `controller/strategy/StrategyController.java`)
- [ ] T171 [P] Actualizar `README.md` y `frontend/README.md` con los endpoints nuevos, las rutas `/ranking` y `/admin/*`, las propiedades `futbolmarket.quotes.recalculation.*` y cómo correr la medición de volumen
- [ ] T172 Revisión orientada a SonarCloud (menos de 10 issues) de todo lo nuevo en `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/` (contextos `strategy` y `quote`): constantes con nombre (escalas, 7 días, defaults, nombres de caché), sin bloques duplicados (la normalización por métrica extraída a una función), sin `catch (Exception)`, sin imports ni parámetros sin usar, inyección por constructor con `@RequiredArgsConstructor`, métodos cortos, servicios sin `if` de negocio
- [ ] T173 Validar `specs/007-cotizacion-estrategias/quickstart.md` de punta a punta (escenarios A a F, con el backend en `bootRun` y el frontend en `npm run dev`) y cerrar la definición de terminado: `cd backend && ./gradlew build` y `cd frontend && npm test && npm run lint && npm run build`

---

## Dependencias y orden de ejecución

### Dependencias entre fases

- **Setup (Fase 1)**: T001 es una puerta. Sin la 006 integrada no arranca ninguna tarea de backend.
- **Fundacional (Fase 2)**: depende de la Fase 1; **bloquea todas las historias de backend**.
- **US1 (Fase 3)**: depende de la Fase 2. Es el MVP.
- **US2 (Fase 4)**: depende de la Fase 2; para probarla con datos reales usa la US1, pero sus tests
  siembran cotizaciones a mano, así que puede avanzar en paralelo con la US1 una vez cerrado T048
  (comparten `QuoteRepository` y `QuoteSQLDAO`: coordinar los cambios a esos dos archivos).
- **US3 (Fase 5)**: solo depende de los contratos (trabaja contra MSW); puede empezar en paralelo con
  la Fase 2. Para la verificación manual contra el backend necesita US1 y US2.
- **US4 (Fase 6)**: depende de la Fase 2 (dominio, persistencia y semilla de estrategias ya están).
- **US5 (Fase 7)**: depende de US1 (recálculo, `QuoteWeekWriter`, `RecalculationLock`) y de US4
  (`StrategyChangeService`, `StrategyChangeWriter`, `StrategyController`).
- **US6 (Fase 8)**: depende de US1 (`QuoteRecalculationService`).
- **US7 (Fase 9)**: depende de US1 (`WeeklyQuotation`, `QuotingPlan`) y de la parte de DTO de US4
  (`StrategyParametersRequest`).
- **US8 (Fase 10)**: depende de los mocks de US3 (T084) y de los contratos; para la verificación manual
  contra el backend necesita US4, US5, US6 y US7.
- **Pulido (Fase 11)**: depende de todas las historias que se quieran entregar; T167 necesita US3 y
  US8; T168 necesita US3 terminada.

### Dependencias entre historias (resumen)

```text
Fase 1 ─► Fase 2 ─┬─► US1 ─┬─► US2
                  │        ├─► US6
                  │        ├─► US7 ◄── (DTO de US4)
                  │        └─► US5 ◄── US4
                  └─► US4
(contratos) ─► US3 ─► US8
```

### Dentro de cada historia

- Excepciones y modelo antes que repositories; repositories antes que servicios; servicios antes que
  controllers y DTO; pantallas después de hooks y componentes.
- Los tests van **después** de la implementación de su tarea (sin TDD) y la tarea no se cierra sin
  ellos en verde.
- Agregar casos a un archivo de test de esta feature: solo métodos nuevos, nunca cambiar los
  existentes.

### Oportunidades de paralelismo

- Fase 2: T004 a T007 entre sí; T008, T009 y T013 entre sí; los tests de dominio T014 a T018 entre sí;
  T019 con el dominio; T025 y T026; T027, T028, T030, T031 y T032; T038, T040 y T041 entre sí (T039
  después de T038).
- US1: T042, T043, T044, T046 y T047 entre sí; los tests de dominio T055 a T059 y de servicio T060 a
  T062 entre sí; T053 en cuanto esté T047.
- US2: T066, T067 y T068 entre sí; T071 con el repository; T073, T075 y T077 entre sí.
- US3: T079, T080, T081 y T083 entre sí; los hooks T087 a T089 entre sí; los componentes T091 a T097
  entre sí.
- US4: T110 con los servicios; T112, T113 y T115 entre sí.
- US8: T148 y T149; T153 con los componentes T156 a T158.
- Con dos desarrolladores: uno lleva el backend (Fases 2, 3, 4, 6, 7, 8 y 9) y el otro el frontend
  (Fases 5 y 10) contra MSW, y se cruzan en la verificación manual de `quickstart.md`.

---

## Ejemplos de ejecución en paralelo

### Historia 1

```text
# Modelo de la US1, en paralelo (archivos distintos):
Tarea: "T043 WeeklyPerformance en modelo/quote/WeeklyPerformance.java"
Tarea: "T044 MetricNormalization en modelo/quote/MetricNormalization.java"
Tarea: "T046 QuotingPlan, WeekDecision y PendingReason en modelo/quote/"
Tarea: "T047 RecalculationReport, WeekResult, PendingWeek y RecalculationOrigin en modelo/quote/"

# Tests de dominio de la US1, en paralelo, una vez implementado el modelo:
Tarea: "T055 WeeklyPerformanceTest"
Tarea: "T056 MetricNormalizationTest"
Tarea: "T058 QuotingPlanTest"
Tarea: "T059 RecalculationReportTest"
```

### Historia 2

```text
Tarea: "T066 excepciones de consulta en modelo/quote/exception/"
Tarea: "T067 QuoteDay en modelo/quote/QuoteDay.java"
Tarea: "T068 RankedQuote y RankingPage en modelo/quote/"
```

### Historia 3

```text
# Componentes en paralelo (cada uno con su .module.css):
Tarea: "T091 VariationBadge.tsx"
Tarea: "T092 QuoteTag.tsx"
Tarea: "T093 QuotePanel.tsx"
Tarea: "T094 QuoteChart.tsx"
Tarea: "T095 QuoteTable.tsx"
Tarea: "T096 DateRangePicker.tsx"
Tarea: "T097 RankingList.tsx"
```

### Historia 8

```text
Tarea: "T156 WeightsEditor.tsx y StrategyForm.tsx"
Tarea: "T157 StrategyList.tsx, RequoteChoice.tsx y AdminNav.tsx"
Tarea: "T158 ReportView.tsx y SimulationTable.tsx"
```

---

## Estrategia de implementación

### MVP primero (solo la Historia 1)

1. Fase 1: puertas (sin la 006 integrada, no se sigue con el backend).
2. Fase 2: fundacional (deja A activa y B sembradas).
3. Fase 3: US1.
4. **PARAR y VALIDAR**: `POST /quotes/recalculate` reproduce los ejemplos del spec (quickstart, 2.1 y
   2.2) y `./gradlew build` está verde.
5. Mostrar o hacer un PR chico contra `develop`.

### Entrega incremental

1. Setup + Fundacional → base lista.
2. US1 → historial semanal (MVP).
3. US2 → consultas públicas por API.
4. US3 → las cotizaciones en la aplicación web (lo que se muestra en la entrega).
5. US4 → estrategias por API.
6. US5 → cambio de la activa con `AHORA` / `PROXIMA_SEMANA`.
7. US6 → recálculo automático.
8. US7 → simulación.
9. US8 → administración web.
10. Pulido → benchmark, ajustes autorizados de la 004, Swagger, README y definición de terminado.

Cada paso suma valor sin romper los anteriores; PRs chicos y frecuentes contra `develop`
(constitución, "Trabajo en paralelo").

---

## Notas

- [P] = archivos distintos, sin dependencias pendientes.
- [US#] liga cada tarea con su historia para la trazabilidad (ver la sección 8 de `data-model.md`).
- Los tests existentes de la 001 a la 006 no se tocan; los de la 004 solo como dice T168 (y T085).
- Hacer commit al cerrar cada tarea o grupo lógico; parar en cada checkpoint para validar la historia.
- Puntos abiertos que no frenan las tareas pero conviene confirmar con el equipo (D28): juego por
  defecto de B (6), métricas no calculables con varios partidos en la semana (7), plan B de `SEQUENCE`
  (8) y coordinación P-1 a P-6 con la 006 (9).
