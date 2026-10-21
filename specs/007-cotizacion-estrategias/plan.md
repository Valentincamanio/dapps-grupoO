# Plan de implementación: Cotización de jugadores y estrategias de valuación

**Rama**: `feature/cotizacion-estrategias` | **Fecha**: 2026-10-15 | **Especificación**: [spec.md](./spec.md)

**Entrada**: especificación de la feature en `/specs/007-cotizacion-estrategias/spec.md`, la
constitución 2.2.1 y el pedido de planificación del 2026-10-15 (backend y frontend en el mismo plan),
con el estado del código relevado ese mismo día: `develop` con la 005 integrada, la rama
`feature/scraping-whoscored` (006, sin integrar) y la suite de la 004.

## Resumen

El sistema recotiza cada semana a **todos** los jugadores activos de las cinco ligas y guarda el
historial. La cotización es el valor del 100 % del jugador, en créditos (con 100 tokens que valen
cada uno el 1 %); sube o baja según cómo rindió el jugador frente a los de su misma posición, con la
estrategia de valuación activa. Un administrador registra y configura las estrategias y, al cambiarlas,
elige recotizar en el acto o la semana siguiente. Cualquiera consulta la cotización actual, la de una
fecha, la evolución y el ranking, por API y desde la aplicación web; un administrador además dispara el
recálculo y simula una estrategia sin guardar nada.

Enfoque técnico:

- **Dos contextos nuevos**, `strategy` y `quote`, en modelo, servicio, controller y persistencia. Se
  reutilizan `player`, `match`, `league`, `position` y `team`. No se agrega ninguna dependencia, ningún
  paquete de primer nivel ni ningún status code.
- **Modelo rico** (`modelo/quote/` y `modelo/strategy/`, sin Spring): la semana, los pares, la posición
  relativa, la métrica entre −1 y +1, el score, el valor, el mínimo, la validación de pesos, la
  inmutabilidad de versiones y cotizaciones y el precio por token. Los servicios solo orquestan.
- **Aritmética con `BigDecimal`**: valor con 2 decimales y `HALF_UP` una sola vez; posición relativa,
  métrica y score con 6; precio por token (valor / 100) con 4. Mismos datos, mismo resultado.
- **Cotizaciones inmutables** de tres tipos: `INITIAL`, `CALCULATED` (cierre semanal, una por jugador y
  semana) y `REQUOTED` (recotización en el acto; puede haber varias en la misma semana).
- **Recálculo** (`service/quote/`): una transacción por semana, todo o nada; un recálculo a la vez y no junto
  con una sincronización (409); disparado por cron (`0 0 6 * * MON`, hora de Argentina), por el arranque y a
  mano. El informe no se persiste.
- **Cambio de estrategia con `AHORA`**: el cambio y la recotización van en **un** método `@Transactional`; los
  candados se toman afuera. Si se rechaza (409), la activa no cambia.
- **Persistencia**: `strategies` (una fila por versión), `strategy_weights` y `quotes`, con nombres y
  índices explícitos y sin SQL nativo. Se consumen las estadísticas de la 006 por su repository.
- **Caché** en memoria con `ConcurrentMapCacheManager` (ya en `spring-context`) para la cotización vigente y el
  ranking.
- **Frontend** en la misma feature, por tipo de artefacto: pantallas de ranking y de administración de
  estrategias, la ficha y la pizarra ampliadas, un gráfico SVG propio, una guarda de ruta por rol y
  tests con Vitest y MSW.

## Decisiones de planificación (2026-10-15)

El pedido resolvió estos puntos antes de escribir el diseño; este plan los adopta tal cual:

1. **Contextos nuevos `strategy` y `quote`**, sin paquetes por feature (D2).
2. **Estadísticas por la 006**: se consumen por su repository y esta feature no crea la entidad; el
   contrato de entrada está en `data-model.md`, sección 5 (D16).
3. **Fórmula aditiva** con riesgo conocido documentado y la simulación como herramienta de calibración
   (D8).
4. **Pesos de la estrategia B** por posición, como punto de partida y sin atajadas (D13).
5. **Contratos OpenAPI** para las consultas, el recálculo, la simulación y las estrategias; la opción de
   recotización para crear o editar una no activa **se ignora** (D14).
6. **Seguridad**: `POST /quotes/**` y `/strategies/**` solo `ADMIN`; las lecturas bajo `/players/**` ya
   son públicas por la marca `TEMPORAL` y queda señalado (D22).
7. **Recálculo**: `futbolmarket.quotes.recalculation.cron`, `zone` y `on-startup`; un recálculo a la vez (409)
   con el patrón de `SyncService`; coordinación con la 006 como puntos a acordar (D17 y D18).
8. **Caché** con `ConcurrentMapCacheManager` y sin dependencia nueva, verificado (D1 y D21).
9. **Frontend**: SVG propio, guarda por rol, 360 px, accesibilidad y Vitest con MSW; el plan lista los tests
   de la 004 que cambian y no se tocan hasta el "sí" (D24 y D25).

### Lo que el plan resuelve distinto del pedido, o que hay que confirmar

Estos puntos **no frenan** el diseño, pero cambian números o contratos. Están detallados en
`research.md` (D28):

| # | Punto | Qué hace este plan | Dónde |
|---|---|---|---|
| 1 | **Score en las respuestas.** El pedido lo lista entre los datos de cada cotización; el spec lo prohíbe (FR-006 y Fuera de Alcance) | **Sigue el spec**: el score se guarda y no se expone. Exponerlo es un campo más y una enmienda a FR-006 | D20 |
| 2 | **Valor base.** El pedido dice "el valor vigente al empezar esa semana"; leído al pie de la letra, el cierre de la semana siguiente descartaría una recotizada | La base es la **última cotización de las semanas anteriores**, incluidas las recotizadas. Es lo que piden FR-024 y FR-036 | D9 |
| 3 | **Una métrica no calculable** en el pedido "vale 0"; el spec agrega que no entra en la comparación de los demás | Sigue el spec (FR-021) | D5 y D6 |
| 4 | **Índices.** El `DESC` pedido es redundante con el único `(player_id, valid_from)` | Se crean los dos como se pidió; se anota que el `DESC` se puede quitar. Se agrega `ix_quotes_week_start_type` | D15 |
| 5 | **Juego de pesos por defecto de B**: el pedido no lo fija y FR-027 lo exige | Igual al de mediocampista; nunca se usa mientras B defina las cuatro posiciones | D13 |
| 6 | **`.specify/feature.json` apuntaba a la 004**, no a esta feature | Se corrigió a `specs/007-cotizacion-estrategias` para generar este plan | n/a |
| 7 | **Los tests de la 004 no fallan** con el diseño propuesto, aunque su descripción prohíbe lo que esta feature hace | Se implementa sin tocarlos; los cambios de redacción se piden aparte | D25 |

## Puertas antes de implementar

Ninguna es una duda de comportamiento: son condiciones del proceso. La implementación no empieza hasta que
las dos primeras estén cumplidas.

| # | Puerta | Estado al 2026-10-15 |
|---|---|---|
| G1 | **La 006 integrada en `develop`** (la estadística por jugador y partido y `SyncLock`). Es una dependencia declarada del spec | **Pendiente**: está en `origin/feature/scraping-whoscored`, 15 commits por delante de `origin/develop` |
| G2 | **Los métodos de lectura nuevos** sobre los repositories de la 006 y la 005 (P-1 de D18) | Pendiente: se agregan al arrancar, después de G1 |
| G3 | **El "sí" explícito del equipo** para modificar los tests de la 004, registrado en Clarifications con nombre y fecha | **Cumplida el 2026-10-15** (Valentin), solo para los cambios de la tabla de FR-072; no autoriza borrar tests ni otras aserciones |
| G4 | La confirmación de los puntos de D28 | Conviene antes de implementar, en `/speckit-clarify`; no bloquea el diseño |

## Contexto técnico

**Lenguaje y versión**: Java 21 (Temurin) con el wrapper de Gradle; TypeScript en modo `strict`.

**Dependencias principales**:

- Backend: Spring Boot 4.1.1 (Spring Framework 7.0.9) con `webmvc`, `data-jpa`, `security`, `validation`,
  `actuator` y `h2console`; Lombok, springdoc-openapi 3.1.1 y Jackson 3.1.5. **Ninguna nueva**: el caché usa
  `spring-context`.
- Frontend: React 18.3, React Router 7.18, Vite 8, Vitest 4, React Testing Library, MSW 2.15 y ESLint con
  `jsx-a11y`. **Ninguna nueva**: el gráfico es SVG propio.

**Almacenamiento**: H2. En `local`, en archivo con `ddl-auto: update`; en `test`, en memoria con
`create-drop`. Tres tablas nuevas: `strategies`, `strategy_weights` y `quotes`. Sin SQL nativo.

**Tests**: JUnit 5, Mockito, AssertJ, `MockMvcTester` con `@SpringBootTest` y `@ActiveProfiles("test")`
en el backend; Vitest, RTL y MSW en el frontend. Sin Testcontainers, sin TDD.

**Plataforma**: API REST en el puerto 8080 (`./gradlew bootRun`) y aplicación web con `npm run dev`
(proxy de Vite `/api` → `:8080`). La feature no sale a internet: no consulta ninguna fuente externa.

**Tipo de proyecto**: aplicación web en un monorepo (`backend/` y `frontend/`). Se tocan los dos.

**Objetivos de rendimiento** (del spec, con unos 2.600 jugadores activos y la base local):

- Una página del ranking y la cotización de un jugador, en menos de 2 s (SC-008).
- Un recálculo de una semana, en menos de 1 minuto; la reconstrucción de 40 semanas, en menos de 5
  (SC-009).
- La primera página de una simulación, en menos de 30 s (SC-010).
- Volumen: unas 137.800 filas por año; se mide en H2 (D15).

**Restricciones**:

- Aritmética con `BigDecimal`; un solo redondeo por magnitud.
- Cada semana se cotiza completa o no se cotiza; las cotizaciones nunca se modifican ni se borran.
- Un recálculo a la vez y nunca junto con una sincronización.
- El recálculo no consulta ninguna fuente externa.
- No se reemplazan `build.gradle` ni los `application*.yml`: solo se agregan claves.
- Frontend: sin carpetas nuevas, tests solo en `frontend/tests/`, sin tocar los tests de la 004 hasta el
  "sí".

**Escala y alcance**:

- 5 ligas, unos 2.600 jugadores activos, unas 40 semanas por temporada.
- 12 endpoints nuevos (5 públicos y 7 de administrador: 5 de estrategias, el recálculo y la simulación) en 3
  archivos de contrato.
- 72 requisitos funcionales (FR-001 a FR-072) y 20 criterios de éxito.
- 4 pantallas nuevas y 2 ampliadas.

No queda ningún `NEEDS CLARIFICATION`: todo se resolvió en [research.md](./research.md) o figura en
D28 como punto a confirmar.

## Verificación constitucional

*Puerta: tiene que pasar antes de la investigación y se revisa de nuevo después del diseño.*

### Antes de la investigación

| Principio | Resultado | Evidencia |
|---|---|---|
| I. Capas estrictas (NO NEGOCIABLE) | Cumple | Ver el detalle debajo de la tabla |
| II. Modelo rico | Cumple | Las decisiones viven en el modelo: `QuoteWeek`, `WeeklyPerformance`, `MetricNormalization`, `WeeklyQuotation`, `QuotingPlan`, `StrategyVersion`, `WeightSet`, `StrategyCatalog` y `Quote`. Los `if` de los servicios deciden cosas de la aplicación (si el candado está libre, si hay una opción de recotización), no del dominio |
| III. Validación en su nivel | Cumple | Forma y tipos en los DTO (Bean Validation); que la estrategia o el jugador existan, en el servicio; invariantes (pesos, factor, mínimo, fechas, opción) en el modelo con excepciones con nombre. Un único advice; status 400, 404 y 409, sin sumar ninguno. Las fechas se reciben como texto y las valida el modelo para informar el valor |
| IV. Tests (NO NEGOCIABLE) | Cumple, con una puerta | Hay unitarios de dominio sin Spring, de servicio con Mockito, integración contra H2 y end to end en `e2e/`. **No se modifica ni se borra ningún test existente**; los cambios a tests de la 004 son solo los que cubre el "sí" del 2026-10-15 (G3, D25) |
| V. Calidad medible | Cumple | Constantes con nombre para las escalas, los 7 días, los defaults y los nombres de caché; inyección por constructor; SLF4J; sin `catch (Exception)`; métodos cortos. La normalización se extrae a una función para no repetir el bloque por métrica |
| VI. Persistencia explícita | Cumple | Las tres tablas con `@Table(name = ...)` y los índices con nombre; solo derived queries, JPQL y `@EntityGraph`; `open-in-view: false`; columnas sin palabras reservadas (`quote_value`, `quote_type`, `version_number`) |
| VII. Idioma | Cumple | Identificadores en inglés; endpoints `/players`, `/quotes` y `/strategies`; contextos `strategy` y `quote` en inglés y en singular; mensajes, documentación y comentarios en español. Los valores `AHORA` y `PROXIMA_SEMANA` de la API los fijó el equipo y se traducen a `RequoteTiming.NOW` y `NEXT_WEEK` |
| Stack tecnológico | Cumple | Sin dependencias nuevas; `ConcurrentMapCacheManager` verificado en `spring-context-7.0.9.jar` |
| Frontend | Cumple | Estructura por tipo de artefacto sin carpetas nuevas; tests solo en `frontend/tests/`; componentes sin `fetch`; tipos a mano desde `contracts/`; accesible y usable desde 360 px |
| Definición de terminado | Cumple | `./gradlew build` y `bootRun`; Swagger con los endpoints nuevos; `npm test`, `npm run lint`, `npm run build` y `npm run dev` ([quickstart.md](./quickstart.md), sección 9) |

Detalle del principio I:

- **Declaración de contextos nuevos** (la constitución exige declararlos en el plan): `strategy` y `quote`.
  Cada uno aparece solo en las capas donde tiene clases (D2).
- **Declaración de status codes**: solo 400, 404 y 409. No se suma ningún status.
- **Declaración de carpetas**: no se crea ninguna fuera del árbol. `exception/` es la subcarpeta que el
  árbol ya prevé por contexto.
- **El modelo es puro**: `modelo/strategy/` y `modelo/quote/` solo usan el JDK y clases de otros contextos
  de modelo (`Player`, `Position`, `Match`, `PlayerMatchStats`). Ninguna anotación de JPA ni de Spring.
- **`*SQL` y mappers**: `QuoteSQL`, `StrategySQL` y `StrategyWeightSQL` llevan las anotaciones; `QuoteMapper`
  y `StrategyMapper` traducen campo a campo y no deciden nada; los invoca `repository/`.
- **Controller y DTO**: los controllers solo hablan con servicios. Los `Request` y `Response` viven en
  `controller/<contexto>/dto/` y no cruzan al servicio; ningún `*SQL` sale en una respuesta.
- **El servicio no conoce JPA**: recibe y devuelve modelo.
- **Monolito**: el cambio de estrategia con `AHORA` modifica estrategias y cotizaciones de dos contextos en
  UN método `@Transactional` (`StrategyChangeWriter.applyAndRequote`). `service/quote` depende de `SyncLock`
  de `service/sync` y de `service/strategy` sin ciclos.
- **Sin contratos entre features**: la coordinación con la 006 son puntos a acordar en `research.md` (D18),
  no un archivo de `contracts/`.

### Después del diseño

Se revisaron [data-model.md](./data-model.md), [contracts/](./contracts/) y [quickstart.md](./quickstart.md)
contra cada principio:

- **I**: el grafo de dependencias entre paquetes no tiene ciclos:
  - `controller.quote → service.quote, modelo`
  - `controller.strategy → service.strategy, modelo`
  - `service.quote → service.sync (SyncLock), persistence.repository, modelo`
  - `service.strategy → service.quote (RecalculationLock, QuoteWeekWriter y QuoteCacheInvalidator, para
    recotizar), persistence.repository, modelo`
  - `persistence → modelo`; `security → modelo.user`.

  La dependencia es en un solo sentido: `service.strategy` usa a `service.quote`, y `service.quote` **no**
  usa a `service.strategy`. El recálculo, las consultas y la simulación leen la estrategia por
  `StrategyRepository` (persistencia), no por un servicio, así que no hay ciclo.
- **II**: `QuoteRecalculationService`, `QuoteWeekWriter`, `StrategyChangeService` y `QuoteSimulationService` no
  tienen un `if` de negocio. La tabla de trazabilidad de `data-model.md` (sección 8) asigna cada regla a una
  clase del modelo.
- **III**: los mensajes están en español y no repiten datos sensibles. Las excepciones de la sección 4 de
  `data-model.md` son todas con nombre y no se usa ninguna excepción genérica de la JDK.
- **IV**: la lista de tests incluye los ejemplos numéricos del spec y los casos de borde pedidos. El 409 se
  prueba de forma determinística con el candado tomado a propósito, y ningún test depende de la hora real.
- **V**: la medición de volumen no corre en el build normal (opt-in), así no demora `./gradlew build`.
- **VI**: no hay SQL nativo. Las columnas no dependen de tipos propios de H2.
- **Frontend**: los archivos nuevos van cada uno en la carpeta de su tipo; `RequireRole` va en `router/`;
  el cliente de `fetch` sigue siendo uno solo.

**Resultado**: la puerta pasa, antes y después del diseño.

## Estructura del proyecto

### Documentación de la feature

```text
specs/007-cotizacion-estrategias/
├── spec.md                        # especificación (sin cambios en este plan)
├── plan.md                        # este archivo
├── research.md                    # decisiones D1 a D28
├── data-model.md                  # modelo, persistencia, entrada de la 006, transiciones y trazabilidad
├── quickstart.md                  # guía de validación de punta a punta
├── contracts/
│   ├── quotes-api.yaml            # OpenAPI 3.0.3: consultas públicas y ranking
│   ├── strategies-api.yaml        # OpenAPI 3.0.3: estrategias (solo ADMIN)
│   ├── quotes-admin-api.yaml      # OpenAPI 3.0.3: recálculo y simulación (solo ADMIN)
│   └── configuration.md           # propiedades, perfiles, seguridad, caché y registro
├── checklists/requirements.md     # existente
└── tasks.md                       # lo genera después /speckit-tasks
```

### Código fuente: backend

En `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/`. `NUEVO` es un archivo nuevo; `CAMBIA` es uno
existente al que se le **agrega** código (ninguna firma ni test existente cambia).

```text
config/
├── ApplicationConfig.java                  # CAMBIA: + QuoteRecalculationProperties en @EnableConfigurationProperties
├── QuoteRecalculationProperties.java       # NUEVO: cron, zone y onStartup, validados al enlazar
└── CacheConfig.java                        # NUEVO: @EnableCaching y ConcurrentMapCacheManager (quotes-current, quotes-ranking)

controller/
├── quote/                                  # NUEVO contexto
│   ├── QuoteController.java                # GET /players/{id}/quotes, /quotes/current, /quotes/at; /players/quotes/current; /players/ranking
│   ├── QuoteAdminController.java           # POST /quotes/recalculate y /quotes/simulate
│   └── dto/                                # QuoteResponse, PlayerQuoteResponse, VariationResponse, StrategyRefResponse,
│                                           #   QuoteHistoryPageResponse, CurrentQuotesResponse, RankingRowResponse, RankingPageResponse,
│                                           #   RecalculationReportResponse, WeekResultResponse, PendingWeekResponse,
│                                           #   SimulationRequest, SimulationRowResponse, SimulationPageResponse
└── strategy/                               # NUEVO contexto
    ├── StrategyController.java             # POST/GET /strategies, GET /strategies/{id}, POST .../versions y .../activate
    └── dto/                                # CreateStrategyRequest, NewVersionRequest, ActivateStrategyRequest,
                                            #   StrategyParametersRequest, StrategyResponse, StrategyVersionResponse, StrategyChangeResponse

modelo/
├── strategy/                               # NUEVO contexto
│   ├── ValuationMetric.java                # 19 métricas con su sentido
│   ├── MetricDirection.java
│   ├── WeightSet.java                      # pesos: ≥ 0, ≤ 2 decimales, suma exacta 1,00
│   ├── StrategyVersion.java                # factor, mínimo, pesos; scoreFor y valueFor
│   ├── StrategyVersionRef.java             # id de versión, id de estrategia, nombre y versión
│   ├── Strategy.java
│   ├── StrategyCatalog.java                # agregado: nombre único, versiones, activa única
│   ├── StrategyChange.java                 # resultado de una operación del catálogo
│   ├── StartingStrategies.java             # A y B de arranque
│   ├── RequoteTiming.java                  # NOW (AHORA) y NEXT_WEEK (PROXIMA_SEMANA)
│   └── exception/                          # UnknownMetric, InvalidWeights, InvalidScaleFactor, InvalidMinimumValue,
│                                           #   InvalidStrategyName, InvalidRequoteTiming, StrategyNotFound,
│                                           #   DuplicateStrategyName, StrategyChangeConflict, StrategyInvariant
└── quote/                                  # NUEVO contexto
    ├── QuoteWeek.java                      # lunes 00:00 a lunes 00:00, hora de Argentina
    ├── Quote.java   QuoteType.java   QuoteVariation.java
    ├── WeeklyPerformance.java              # las métricas semanales de un jugador (único que lee los nombres de la 006)
    ├── MetricNormalization.java            # posición relativa y métrica entre −1 y +1
    ├── WeeklyQuotation.java                # decide qué cotizaciones nacen (calculadas, iniciales y recotizadas)
    ├── QuotingPlan.java   WeekDecision.java   PendingReason.java
    ├── RecalculationReport.java   WeekResult.java   PendingWeek.java   RecalculationOrigin.java
    ├── QuotePage.java   RankedQuote.java   RankingPage.java   SimulatedQuote.java   SimulationResult.java
    ├── QuoteDay.java                       # parseo y validación de fechas
    └── exception/                          # PlayerWithoutQuote, QuoteNotFoundAtDate, InvalidQuoteDate, FutureQuoteDate,
                                            #   InvalidDateRange, InvalidSimulationRequest, RecalculationInProgress,
                                            #   RecalculationBlockedBySync, QuoteInvariant

persistence/
├── repository/
│   ├── quote/QuoteRepository.java          # NUEVO
│   ├── strategy/StrategyRepository.java    # NUEVO
│   ├── performance/PlayerMatchStatsRepository.java   # CAMBIA (006): + findAllByMatchBetween
│   └── match/MatchRepository.java          # CAMBIA: + findAllFinished
├── mapper/
│   ├── quote/QuoteMapper.java              # NUEVO
│   └── strategy/StrategyMapper.java        # NUEVO
└── sql/
    ├── entity/
    │   ├── quote/QuoteSQL.java             # NUEVO: tabla quotes; sin setters
    │   └── strategy/StrategySQL.java   StrategyWeightSQL.java   # NUEVO: tablas strategies y strategy_weights
    └── interfaces/
        ├── quote/QuoteSQLDAO.java          # NUEVO: JPQL de última cotización, historial y ranking
        ├── strategy/StrategySQLDAO.java    # NUEVO
        ├── performance/PlayerMatchStatsSQLDAO.java   # CAMBIA (006): + findAllByMatchUtcDateBetween
        └── match/MatchSQLDAO.java          # CAMBIA: + findAllByStatus

security/
└── SecurityConfig.java                     # CAMBIA: + reglas hasRole(ADMIN) para POST /quotes/** y /strategies/**

service/
├── quote/                                  # NUEVO contexto
│   ├── QuoteQueryService.java              # consultas; @Cacheable en actual y ranking
│   ├── QuoteRecalculationService.java      # orquesta una corrida (sin @Transactional)
│   ├── QuoteWeekWriter.java                # @Transactional: una semana, todo o nada; y recotizar
│   ├── QuoteSimulationService.java         # en memoria, sin escribir
│   ├── RecalculationLock.java              # semáforo propio + SyncLock de la 006
│   ├── QuoteCacheInvalidator.java
│   ├── QuoteScheduler.java                 # @Scheduled
│   ├── QuoteStartupRecalculation.java      # @Async sobre ApplicationReadyEvent
│   └── RecalculationReportLogger.java
└── strategy/                               # NUEVO contexto
    ├── StrategyService.java                # crear, editar y consultar
    ├── StrategyChangeService.java          # activar y editar la activa; candados fuera de la transacción
    ├── StrategyChangeWriter.java           # @Transactional: apply y applyAndRequote
    └── StrategySeeder.java                 # ApplicationRunner idempotente
```

`shared/` no cambia: `BadRequestException`, `NotFoundException` y `ConflictException` alcanzan.
`ApplicationConfig` no cambia de forma. `build.gradle` no cambia. `application.yaml` y
`application-test.yml` suman las claves de `futbolmarket.quotes.recalculation` (ver
[contracts/configuration.md](./contracts/configuration.md)).

### Código fuente: frontend

En `frontend/src/`, por tipo de artefacto, sin carpetas nuevas.

```text
types/
└── quotes.ts                               # NUEVO: tipos a mano desde contracts/*.yaml (enums tal cual el backend)

services/                                   # un cliente por endpoint, todos sobre request() de httpClient.ts
├── quoteService.ts                         # NUEVO: getPlayerQuotes, getCurrentQuote, getQuoteAt, getCurrentQuotes (lote), getRanking
├── strategyService.ts                      # NUEVO: createStrategy, listStrategies, getStrategy, createVersion, activateStrategy
└── recalculationService.ts                 # NUEVO: recalculate y simulate

hooks/
├── useCurrentQuotes.ts                     # NUEVO: lote de la hoja de la pizarra
├── usePlayerQuote.ts                       # NUEVO: cotización actual de la ficha (distingue "sin cotización")
├── useQuoteHistory.ts                      # NUEVO: evolución con rango de fechas
├── useRanking.ts                           # NUEVO: usa los filtros de useBoardFilters
├── useStrategies.ts                        # NUEVO
├── useMutation.ts                          # NUEVO: comandos (crear, editar, activar, recalcular, simular)
├── useRecalculation.ts                     # NUEVO
└── useSimulation.ts                        # NUEVO

components/                                 # cada uno con su .module.css; reciben datos y callbacks, sin API ni router
├── QuoteTag.tsx                            # NUEVO: cotización compacta dentro del post-it
├── VariationBadge.tsx                      # NUEVO: variación con signo, texto y triángulo
├── QuotePanel.tsx                          # NUEVO: valor, precio por token y variación de la ficha
├── QuoteChart.tsx                          # NUEVO: SVG propio con un botón por punto
├── QuoteTable.tsx                          # NUEVO: la alternativa en texto del gráfico
├── DateRangePicker.tsx                     # NUEVO: dos fechas y rangos rápidos
├── RankingList.tsx                         # NUEVO: tabla que pasa a tarjetas a 360 px
├── WeightsEditor.tsx                       # NUEVO: pesos de un juego con su suma exacta
├── StrategyForm.tsx                        # NUEVO: nombre, factor, mínimo y juegos de pesos
├── StrategyList.tsx                        # NUEVO: estrategias, versiones y activa
├── RequoteChoice.tsx                       # NUEVO: "Recotizar ahora" o "la semana siguiente" (preseleccionada)
├── ReportView.tsx                          # NUEVO: informe del recálculo
├── SimulationTable.tsx                     # NUEVO: actual, simulada y diferencia
├── AdminNav.tsx                            # NUEVO: pestañas de la administración (enlaces)
├── PostIt.tsx                              # CAMBIA: prop opcional quote; sin ella, idéntico a hoy
├── ScoutSheet.tsx                          # CAMBIA: espacio opcional para el panel de cotización
└── TopBar.tsx                              # CAMBIA: enlaces de navegación (ranking y, solo ADMIN, administración)

pages/
├── RankingPage.tsx                         # NUEVO: /ranking
├── AdminStrategiesPage.tsx                 # NUEVO: /admin/estrategias
├── AdminRecalculationPage.tsx              # NUEVO: /admin/recalculo
├── AdminSimulationPage.tsx                 # NUEVO: /admin/simulacion
├── BoardPage.tsx                           # CAMBIA: pide las cotizaciones de la hoja por lote
├── PlayerPage.tsx                          # CAMBIA: suma el panel y el gráfico
└── AppLayout.tsx                           # CAMBIA: enlaces del encabezado según el rol

router/
├── RequireRole.tsx                         # NUEVO: sesión (RequireSession) y rol ADMIN; aviso de falta de permiso
└── routes.tsx                              # CAMBIA: rutas /ranking, /admin (redirige) y /admin/*

utils/
├── quotes.ts                               # NUEVO: créditos, precio por token, variación, tipos, última por semana
├── metrics.ts                              # NUEVO: etiquetas en español de las 19 métricas y de los motivos pendientes
├── weights.ts                              # NUEVO: suma exacta en centésimos y validación de juegos
└── chart.ts                                # NUEVO: geometría del gráfico

styles/tokens.css                           # CAMBIA: tokens de subida y bajada
```

### Tests: backend

En `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/`, replicando la estructura de producción.

```text
modelo/strategy/        ValuationMetricTest  WeightSetTest  StrategyVersionTest  StrategyCatalogTest
                        StartingStrategiesTest  RequoteTimingTest
modelo/quote/           QuoteWeekTest  QuoteTest  WeeklyPerformanceTest  MetricNormalizationTest
                        WeeklyQuotationTest  QuotingPlanTest  RecalculationReportTest  QuoteDayTest
                        PlayerMatchStatsBuilder (apoyo)  QuoteFixtures (apoyo)
service/quote/          QuoteQueryServiceTest  QuoteRecalculationServiceTest  RecalculationLockTest
                        QuoteSimulationServiceTest  QuoteSchedulerTest  QuoteStartupRecalculationTest
                        RecalculationReportLoggerTest  QuoteWeekWriterIT  RecalculationIT  RequoteIT
                        SimulationIT  QuoteCacheIT  QuoteVolumeBenchmarkIT (opt-in)
service/strategy/       StrategyServiceTest  StrategyChangeServiceTest  StrategySeederTest
                        StrategySeederIT  StrategyChangeIT
persistence/repository/ quote/QuoteRepositoryIT  strategy/StrategyRepositoryIT
                        performance/PlayerMatchStatsRepositoryMatchBetweenIT  match/MatchRepositoryFinishedIT
config/                 QuoteRecalculationPropertiesTest  CacheConfigTest
e2e/                    QuoteControllerIT  RankingRoutingIT  QuoteAdminAccessIT  StrategyControllerIT
                        StrategyAccessIT  RecalculationConflictIT  SimulationControllerIT
```

**Casos que tienen que estar** (Principio IV; setup-execute-verify, `assertThat`, nombres descriptivos):

| Grupo | Casos |
|---|---|
| **Ejemplos numéricos del spec**, en dominio | los cinco delanteros con 0, 0, 1, 2 y 3 goles dan −0,6; −0,6; 0,0; +0,4 y +0,8 y promedio 0; el score 0,30 de la estrategia A; 100,00 → 103,00 → 101,00 con precio por token 1,0300; el mínimo de 10,00 con 12,00 y −0,50; la recotizada de 104,00 desde 103,00 y la de C que parte de 103,00 |
| **Casos de borde del pedido** | todos los pares empatan (métrica 0 para todos); una sola posición con un solo jugador; jugador inactivo que vuelve (parte de su última cotización); jugador nuevo a mitad de temporada (inicial desde el lunes de su primera semana); semana con estadísticas incompletas **antes** de los 7 días (pendiente, bloquea las siguientes) y **después** (se cotiza con lo que haya); recotización repetida sin acumular; **401, 403 y 409** de cada endpoint de administrador |
| **Más bordes** | semana sin partidos (ningún valor cambia, se guarda igual); redondeo `HALF_UP` en 0,005; partido del domingo 23:59 contra el lunes 00:00 (hora de Argentina); jugador cuyo equipo no es ninguno del partido; métrica no calculable fuera de la comparación; rating ponderado por minutos; la semana en curso no se cotiza; repetir el recálculo no crea nada; determinismo con la entrada desordenada; una falla a la mitad de una semana deja 0 filas de esa semana; el recálculo en curso usa la estrategia que tenía al empezar; `AHORA` con el candado tomado deja la activa intacta; la opción se ignora al crear o al editar una no activa; la semilla no se duplica ni pisa una edición |
| **API** | consultas públicas sin credencial; 404 de jugador inexistente distinto del de jugador sin cotización; 400 de fecha futura, de formato y de rango invertido con el valor en el mensaje; ranking ordenado, filtrado y sin inactivos; **`GET /players/ranking` contra `GET /players/{id}`** (ruta, acceso público y que el detalle no cambia); la simulación no cambia nada y dos llamadas iguales dan lo mismo |

### Tests: frontend

En `frontend/tests/`, todos **archivos nuevos** (los de la 004 no se tocan salvo lo de D25).

```text
utils/       quotes.test.ts  weights.test.ts  chart.test.ts  metrics.test.ts
components/  QuoteTag.test.tsx  VariationBadge.test.tsx  QuotePanel.test.tsx  QuoteChart.test.tsx
             DateRangePicker.test.tsx  WeightsEditor.test.tsx  StrategyForm.test.tsx  RequoteChoice.test.tsx
             ReportView.test.tsx  RankingList.test.tsx  SimulationTable.test.tsx
hooks/       useCurrentQuotes.test.tsx  usePlayerQuote.test.tsx  useQuoteHistory.test.tsx  useRanking.test.tsx
pages/       BoardQuotes.test.tsx  PlayerQuotes.test.tsx  RankingPage.test.tsx  AdminAccess.test.tsx
             AdminStrategiesPage.test.tsx  AdminRecalculationPage.test.tsx  AdminSimulationPage.test.tsx
             NoMarketActions.test.tsx
mocks/       quoteHandlers.ts  quotes.ts   (nuevos)    handlers.ts  (CAMBIA: una línea, ver D25)
```

**Casos que tienen que estar** (felices y de borde, en español):

| Pantalla o pieza | Casos |
|---|---|
| Pizarra | muestra la cotización de cada post-it con **una** consulta de lote por hoja; un jugador sin cotización lo indica; el error del lote no rompe la pizarra; carga y error con "reintentar" |
| Ficha | valor, precio por token y variación; sin cotización (404 de la cotización con el jugador existente) conserva los datos del catálogo; carga y error; los textos del catálogo no se repiten |
| Gráfico | un punto por semana (la última de la semana); cada punto dice valor, semana, estrategia y versión; el rango recorta; un rango vacío muestra el mensaje; la tabla alternativa tiene los mismos puntos; los puntos son botones y se alcanzan con teclado |
| Ranking | orden descendente; filtros de liga, equipo y posición y la hoja en la dirección (`league`, `team`, `position`, `page`), sin los vacíos; recargar reproduce la vista; vacío, carga, error y hoja fuera de rango |
| Acceso a la administración | sin sesión → `/login`; usuario común → aviso de falta de permiso y sin controles ni enlace; `ADMIN` → ve las pantallas |
| Estrategias | lista con versiones y activa; el formulario muestra la suma de los pesos y no deja enviar si algún juego no suma 1,00; el rechazo del servidor se muestra en español y **conserva lo escrito**; editar crea una versión; activar pide elegir, con "la semana siguiente" preseleccionada, y no envía nada hasta confirmar |
| Recálculo | muestra el informe (semanas, iniciales, calculadas, recotizadas, pendientes con motivo); informe vacío; 409 muestra el motivo y conserva la pantalla |
| Simulación | estrategia guardada o parámetros; tabla con actual, simulada y diferencia; filtros y hoja; validaciones |
| `NoMarketActions` | ninguna pantalla nueva ofrece comprar, vender, sumar al plantel ni órdenes, ni muestra presupuesto, tokens o portfolio de un usuario (FR-071) |

### Tests de la 004 que cambian (FR-072)

**El "sí" explícito del equipo (Valentin, 2026-10-15) está registrado en Clarifications y cubre solo
estos cambios (G3); no autoriza borrar tests ni otras aserciones.** Esta es la lista exacta de lo que
cambia y por qué (detalle en D25):

| Test | Cambio propuesto | Por qué |
|---|---|---|
| `tests/pages/NoPurchaseActions.test.tsx` (CE-009): `FORBIDDEN` (línea 7) y el comentario de la línea 1 | Quitar `precio` y `valor` de la expresión y actualizar el comentario | Esta feature muestra precios, valores y cotizaciones. Se conservan `comprar`, `vender`, `sumar`, `plantel`, `presupuesto`, `puntaje`, `portfolio` y `orden` (el score no se expone, FR-006) |
| `tests/pages/NoPurchaseActions.test.tsx`: `PRICE_TEXT` y las estrellas | **No cambian** | Las cotizaciones se muestran en créditos, sin símbolo de moneda ni estrellas |
| `tests/components/PostIt.test.tsx`, "no contiene botones ni textos de compra, venta, plantel, precio o valor" | Quitar `precio` y `valor` y renombrar el test | Su descripción afirma lo que esta feature levanta |
| `tests/pages/PlayerPage.test.tsx`, "la ficha no tiene botones de compra, venta ni similares" | Quitar `precio` de la expresión | Mismo motivo |
| `tests/mocks/handlers.ts` (apoyo, no es un test) | Sumar los handlers de cotizaciones **antes** de `playerHandlers` | `setup.ts` usa `onUnhandledRequest: 'error'`; sin esto la pizarra y la ficha existentes fallan al pedir cotizaciones |

**Con el diseño de D24, la suite de la 004 pasa sin modificarse**, porque los nombres accesibles de los botones y
los enlaces nuevos evitan esas palabras y las cantidades no llevan `$`, `€`, `USD` ni `EUR`. Por eso no hay
urgencia: se implementa sin tocar nada, se corre la suite de la 004 completa en cada tarea de frontend, y los
cambios de redacción de la tabla se aplican en una tarea final **solo si el equipo da el sí**. El cambio de
`handlers.ts` es inevitable y aditivo (una línea y un `import`); se incluye en lo que se le pide al equipo por
prudencia.

**Restricciones de diseño que cuida esto** (D24): un único `list` en `/pizarra`; un único link y ningún botón en
`PostIt`; el texto de equipo, posición y liga no se repite en la ficha; los handlers de cotizaciones van antes de
`/players/:id` porque `:id` también captura `ranking`.

## Orden de implementación sugerido

Esto no son las tareas (las genera `/speckit-tasks`), sino el orden de dependencias para ordenarlas.

1. **Puertas** G1 a G3. Sin G1 y G2 no se empieza el backend de cotización.
2. **Dominio de estrategias**: `ValuationMetric`, `WeightSet`, `StrategyVersion`, `StrategyCatalog`,
   `StartingStrategies`, `RequoteTiming` y sus tests.
3. **Dominio de cotizaciones**: `QuoteWeek`, `Quote`, `WeeklyPerformance`, `MetricNormalization`,
   `WeeklyQuotation`, `QuotingPlan`, el informe y sus tests (con los ejemplos numéricos del spec).
4. **Persistencia**: `*SQL`, mappers y repositories de las dos tablas, más los métodos nuevos sobre los
   repositories de la 006 y la 005; tests de integración en H2.
5. **Servicios y orquestación**: semilla, estrategias, escritura por semana, recálculo, candado, cambio de
   estrategia con `AHORA`, simulación y consultas con caché.
6. **API, seguridad y configuración**: controllers y DTO, `SecurityConfig`, propiedades, scheduler y arranque,
   Swagger; los IT de `e2e/` (incluidos 401, 403 y 409).
7. **Frontend, base**: tipos, servicios, utilidades, hooks, mocks y handlers (sin tocar los tests de la 004).
8. **Frontend, lectura**: `QuoteTag` y la pizarra; el panel, el gráfico y la ficha; el ranking.
9. **Frontend, administración**: guarda por rol, estrategias, recálculo y simulación.
10. **Cierre**: medición de volumen (D15), ajustes de los tests de la 004 **si hay "sí"**, y la definición
    de terminado del backend y del frontend.

El frontend (pasos 7 a 9) puede avanzar en paralelo con el backend: trabaja contra MSW con las formas de
`contracts/`.

## Seguimiento de complejidad

Sin violaciones de la constitución que justificar. Dos decisiones que conviene tener a la vista:

| Decisión | Por qué hace falta | Alternativa más simple descartada |
|---|---|---|
| Dos transacciones distintas para el recálculo y para el cambio con `AHORA`, y los candados fuera de la transacción | Que el 409 salga antes de abrir la transacción y que el candado se suelte después del commit (D14) | `@Transactional` sobre el servicio con el candado adentro: se liberaría antes del commit |
| `base_value` y el índice `ix_quotes_week_start_type` en `quotes`, que el pedido no mencionó | Evitar un N+1 al informar la variación y no recorrer 135.000 filas para saber la última semana cotizada (D15) | Calcular la variación buscando la cotización anterior de cada fila |

## Puntos a confirmar con el equipo

Resumen de D28 de [research.md](./research.md). Los puntos 1 a 5 **se resolvieron en `/speckit-clarify` el
2026-10-15** (Clarifications del spec); quedan abiertos del 6 al 9.

1. ~~La fórmula aditiva y su riesgo del piso~~: **confirmada** (D8); se recalibra con la simulación.
2. ~~El valor base cuando hay recotizaciones~~: **parte del valor recotizado** (D9, FR-024).
3. ~~El score en las respuestas~~: **no se expone** (D20, FR-006).
4. ~~Pares de las cinco ligas juntas o de cada liga~~: **las cinco juntas** (D6).
5. ~~El "sí" de la 004~~: **dado por Valentin el 2026-10-15** (G3).
6. El **juego de pesos por defecto de B** (D13).
7. La **interpretación de las métricas no calculables** con varios partidos en la semana (D5).
8. El **plan B de `SEQUENCE`** si la medición lo exige (D15).
9. Los **puntos de coordinación con la 006**, P-1 a P-6 (D18).
