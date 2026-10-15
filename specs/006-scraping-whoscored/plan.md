# Plan de implementación: Scraping de WhoScored

**Rama**: `feature/scraping-whoscored` | **Fecha**: 2026-10-13 | **Especificación**: [spec.md](./spec.md)

**Entrada**: especificación de la feature en `/specs/006-scraping-whoscored/spec.md` y el pedido
de planificación del 2026-10-13, con el estado del código de la 005 y lo verificado contra
WhoScored ese día con fragmentos reales de sus páginas.

## Resumen

La feature trae de WhoScored las estadísticas de cada jugador en cada partido terminado de las
cinco ligas y las guarda para la cotización de la Entrega 2. En concreto:

- cruza los equipos, los partidos y los jugadores de WhoScored con los del catálogo de la 005 y
  guarda, por cada jugador que entró a la cancha, las 19 métricas del spec;
- procesa todos los partidos terminados que todavía no tienen estadísticas, y nunca reprocesa uno;
- arranca rápido en una base nueva: un snapshot versionado completa lo que ya se obtuvo y solo se
  scrapea lo que falta;
- se pone al día sola al arrancar si la última completa exitosa de una fuente tiene más de 7
  días, y encadena las dos fuentes los lunes a las 04:00;
- tolera los rechazos de WhoScored y se corta ante un bloqueo sin perder lo guardado;
- un administrador la puede disparar a mano, para las cinco ligas o para una.

Las estadísticas no se exponen: ningún endpoint las devuelve.

Enfoque técnico:

- **Adapter** (`adapter/whoscored/`):
  - Baja cada página con un `RestClient` propio sobre `JdkClientHttpRequestFactory`, detrás de
    la interfaz chica `PageDownloader`, así Playwright puede reemplazarlo sin tocar el resto
    (plan B, [research.md](./research.md) D5).
  - Lee tres bloques del HTML: el JSON `tournamentfixtures` de la página de la liga, el array
    `fixtureMatches` de la de un equipo y el JSON `matchCentreData` de la de un partido. Los
    recorta balanceando llaves y los traduce con su mapper.
  - Pide una página a la vez, con al menos 4 s entre una y la siguiente. Una página rechazada, o
    sin su bloque, se reintenta una vez a los 30 s. Al segundo fallo lanza
    `SourceBlockedException`, que corta la corrida entera.
- **Modelo rico**:
  - `modelo/performance/` (contexto nuevo): `PlayerMatchStats` y lo que WhoScored informa de un
    partido. Ahí viven las reglas de los minutos, los goles, las asistencias y las tarjetas.
  - `modelo/sync/`:
    - los cruces (`TeamCrossing`, por nombre y calendario, y `PlayerCrossing`, por nombre, edad e
      id);
    - las reglas de una liga (`LeagueStatsSync`);
    - el snapshot (`StatsSnapshot` y `SnapshotLoad`);
    - la corrida y su informe (`StatsSyncRun` y `StatsSyncReport`);
    - la puesta al día (`CatchUpPolicy`).

  Los servicios solo orquestan.
- **Orquestación** (`service/sync/`):
  - `SyncLock` es el candado único de las dos fuentes y reemplaza al semáforo de `SyncService`.
  - `SyncCoordinator` encadena Football-Data y WhoScored con el candado tomado de punta a punta,
    para la corrida semanal y para la puesta al día al arrancar.
  - `StatsSyncService` primero carga el snapshot de todas las ligas pedidas y después, liga por
    liga, descubre los ids que faltan, scrapea los pendientes y arma el informe.
  - `StatsWriteService` guarda cada partido en su propia transacción, así la corrida es
    reanudable.
- **Disparadores**:
  - `POST /matches/stats/sync[?league=]`, solo `ADMIN`, que siempre responde 200 con el informe,
    también si hubo corte;
  - la cadena del lunes (`SyncScheduler`);
  - la puesta al día al arrancar (`StartupSync`).
- **Persistencia**:
  - `whoscored_id` en `teams`, `players` y `matches`, nulo y único;
  - `stats_loaded` en `matches`;
  - las tablas `player_match_stats` y `last_full_syncs`.

  Todo es compatible con `ddl-auto: update` sobre la base de la 005: no hay que borrarla.
- **Snapshot**:
  - `backend/snapshot/whoscored-stats.jsonl`, JSON Lines en texto plano y determinístico;
  - lo regenera `./gradlew exportStatsSnapshot`, que nunca es un endpoint.
- **Dependencias**: ninguna nueva. `build.gradle` suma solo la tarea de exportación.

## Decisiones de planificación (2026-10-13)

El usuario resolvió cuatro puntos antes de escribir el diseño:

1. **Retomar al arrancar los pendientes de una corrida cortada: se sigue el spec.**
   - El pedido proponía que WhoScored los retomara al arrancar aunque estuviera al día.
   - El spec (FR-035 y el caso borde de la corrida cortada por bloqueo) dice que corre solo si
     su última completa exitosa tiene más de 7 días; si no, lo pendiente espera a la cadena del
     lunes o a un disparo manual.
   - No se agrega la regla: como casi siempre hay algún partido que WhoScored todavía no
     publica, correría en casi todos los arranques. Ver D22.
2. **`SyncOrigin`: `STARTUP` se renombra a `CATCH_UP`.**
   - El pedido decía que el enum "suma" el origen de la puesta al día; FR-053 dice que la puesta
     al día al arrancar reemplaza al arranque con el catálogo vacío.
   - Quedan tres orígenes: `WEEKLY`, `CATCH_UP` y `MANUAL`. Solo `StartupSyncTest` usaba
     `STARTUP`, y ese test ya cambia por FR-033. Ver D22.
3. **Tests de la 005 en el borde del aval: sí a los dos.**
   - `SyncSchedulerTest` cambia porque la corrida del lunes pasa a ser la cadena (FR-026 de la
     005, ampliado por FR-033 y FR-036 de la 006).
   - El helper de `SyncServiceTest` recibe, además del candado común (FR-031, cubierto), el
     repositorio de la última completa exitosa (FR-047).
   - La lista exacta está en [research.md](./research.md) D26.
4. **Exportación del snapshot: tarea de Gradle.**
   - `./gradlew exportStatsSnapshot` corre una clase main que levanta Spring sin servidor web ni
     sincronizaciones, exporta y termina.
   - `build.gradle` suma la tarea y ninguna dependencia. Ver D21.

No quedan otras contradicciones entre el spec, el pedido y la constitución:

- la tarea de Gradle no reemplaza `build.gradle`, solo lo amplía;
- el árbol de carpetas no cambia: `performance` es un contexto nuevo, que la constitución permite
  declarando en el plan, y la clase main va en el paquete raíz, sin crear un paquete;
- no se agrega ningún status al advice: el disparo de WhoScored usa 200, 400, 401, 403 y 409.

### Decisiones posteriores al análisis (2026-10-14)

Después de `/speckit-analyze`, Lucas resolvió estos puntos:

1. **Nombres de tests de la 005**: se renombran tres métodos de `StartupSyncTest` y
   `SyncSchedulerTest` y se borra `SyncSchedulerTest.sinTokenNoLlamaAlServicioYDejaUnInfo`, porque
   sus nombres dejaban de describir lo que prueban (principio IV). Ver D26.
2. **SQL nativo en un test**: se aprueba un `INSERT` estándar en `MatchStatsRepositoryIT` para
   probar el default de `stats_loaded` (principio VI). Ver D17.
3. **Valores imposibles de la fuente**: el rating se redondea a dos decimales, y un rating fuera
   de 0 a 10 o una métrica negativa omiten el partido con `INVALID_IN_SOURCE`. Ver D8, D13 y D18.
4. **Fila del snapshot sin id de WhoScored**: la columna `whoscored` admite `null`. Ver D19 y
   [contracts/snapshot-format.md](./contracts/snapshot-format.md).
5. **Referencias del snapshot**: `SnapshotLoad` recibe los equipos y partidos del catálogo que
   nombra el snapshot (`CatalogReferences`), así lo que es de otra temporada se ignora sin
   contarse. Ver D20.
6. **Respaldos de la base local**: `.gitignore` suma `backend/data-*/`, para que un respaldo como
   el de [quickstart.md](./quickstart.md) 5.3 nunca se versione (FR-032).

### Validación de las decisiones técnicas propuestas en el pedido

| # | Propuesta | Resultado | Dónde |
|---|---|---|---|
| 1 | Adapter con un cliente detrás de una interfaz chica, extractores, `dto/` y mapper; el mapper suma, toma el último rating, calcula los minutos y resuelve las asistencias | Se confirma con un ajuste. El mapper traduce (sumas, último rating, tipo de tarjeta, gol en contra, asistidor y minutos expandidos de entrada y salida) y **el modelo** aplica FR-004 a FR-007. Así los minutos, el gol en contra y la doble amarilla tienen unitarios del modelo. | D4 y D7 |
| 2 | `RestClient` propio con `JdkClientHttpRequestFactory`, headers de propiedades y sin dependencias; Playwright como plan B | Se confirma. Se suman `followRedirects(NORMAL)` y la compresión apagada (verificado en spring-web 7.0.9). El plan B tiene tres criterios para activarlo. | D3 y D5 |
| 3 | Pausa de 4 s, un reintento a los 30 s, dos fallas seguidas cortan; esperas inyectables, decidir sobre `Sleeper` | Se confirma. WhoScored tiene su propio `WhoScoredSleeper`, porque `ThreadSleeper` traduce la interrupción con un motivo de Football-Data, y moverlo pedía una carpeta común y tocar tests de la 005. La pausa se mide con el `Clock`, la espera del reintento la cubre, las propiedades validan los mínimos del spec y `matchCentreData: null` no es un rechazo. | D6 |
| 4 | Página de la liga (5 pedidos), cruce por nombre y confirmación por partidos, y página de fixtures una vez por corrida y equipo; verificar las otras cuatro ligas | Se confirma con ajustes. Las cinco ligas tienen el mismo bloque JSON, pero trae **la fecha en curso, no las semanas cercanas**. La confirmación se hace con el calendario de la temporada de la página de fixtures, y esa página se usa para cruzar **todos** los partidos de la temporada, incluidos los futuros: así las corridas semanales no piden páginas de fixtures. La hora de `fixtureMatches` es la del sitio (Londres), no la local del partido. | D11 a D13 |
| 5 | Contexto nuevo para las estadísticas, normalización con `Normalizer`, informe propio, decidir si se reutiliza `modelo/sync/` | `performance` para las estadísticas. La normalización suma las letras que NFD no descompone (`ø`, `ß`, `ł`...). La corrida y el informe de WhoScored tienen tipos propios en `modelo/sync/` y reutilizan `SyncType`, `SyncOrigin` y `SyncInProgressException`. Lo que informa WhoScored se llama `Reported*`, para no chocar con el "snapshot" del spec. | D2, D12, D15 y D16 |
| 6 | `player_match_stats`, `whoscored_id` nulo y único, tabla de estado, una transacción por partido | Se confirma, más `matches.stats_loaded` como marca de procesado (un partido en el que no se cruzó a nadie también queda procesado). Columnas nulas y `@ColumnDefault("false")`, para que `ddl-auto: update` funcione sobre la base de la 005 sin borrarla. Los cruces de una liga van en su propia transacción. | D17 y D18 |
| 7 | Snapshot de texto con una línea por partido y los cruces de equipos; comprimido o plano; dónde vive; carga antes de scrapear; exportación por comando | JSON Lines en texto plano, en `backend/snapshot/whoscored-stats.jsonl`, con entradas `team`, `match` y `stats`, cada una con su liga. Las `match` incluyen partidos futuros. La exportación es la tarea de Gradle (decisión 4), y `.gitattributes` fija LF. | D19 a D21 |
| 8 | Coordinador, candado común, puesta al día con umbral de 7 días, retomar los cortados, sin token WhoScored igual, origen nuevo | Se confirma, salvo retomar los cortados, que no se agrega (decisión 1). El catálogo vacío no hace falta como condición aparte: una base vacía no tiene ninguna completa registrada. `STARTUP` pasa a `CATCH_UP` (decisión 2). | D22 y D23 |
| 9 | `POST /matches/stats/sync`, solo `ADMIN`, 409 y 400; decidir qué devuelve un bloqueo | Se confirma. Un bloqueo responde **200 con el corte en el informe**, como pide FR-039. No hay 503, porque WhoScored no usa credencial, ni ningún status nuevo. | D24 |
| 10 | Propiedades `futbolmarket.whoscored.*` y el umbral; perfil test sin esperas ni red | Se confirma. Se suman `accept` y `accept-language`, y el umbral va en `futbolmarket.sync.catch-up-after`. El perfil test usa una URL `.invalid` y un snapshot inexistente. Las esperas no se bajan en test: ningún test duerme, porque el adapter usa el doble y los e2e lo mockean. | D25 |
| 11 | Tests sin WhoScored, fixtures en `src/test/resources/whoscored/`, `MockRestServiceServer`, unitarios del modelo, IT, e2e con `@MockitoBean`, snapshot con archivos temporales | Se confirma. Los comportamientos nuevos de `Team`, `Player` y `Match` se prueban en clases de test nuevas, así no se toca ningún test de esas entidades. Los IT nuevos usan su propia H2 (`stats-it`). | D26 |
| 12 | Parsear 1 MB con Jackson no es un problema; evitar una transacción de cientos de partidos | Se confirma. Primera vez: unas 430 páginas y unos 47 minutos. Semana normal: 48 páginas, unos 5 minutos. Ninguna transacción supera un partido o los cruces de una liga. | D27 |

Lo verificado contra WhoScored el 2026-10-13 que corrige o completa el pedido:

- **Quites**: WhoScored rotula "Tackles" a los quites ganados, que en `stats` son
  `tackleSuccessful`. `tacklesTotal` también cuenta las veces que el jugador fue gambeteado.
- **Asistencias**: la regla se confirmó en cuatro partidos de dos ligas. El evento relacionado
  puede ser un remate, no solo un pase, y `eventId` es único por equipo, no por partido.
- **Segunda amarilla**: llega como `cardType` `SecondYellow` (32), y la primera amarilla queda con
  el calificador `VoidYellowCard`.
- **Partido sin jugar**: `matchCentreData: null`, dentro del bloque normal.

## Divergencias con el spec

No hay divergencias: el diseño cumple cada requisito tal como está escrito. Estos puntos no son
divergencias, pero conviene tenerlos a la vista:

- **Quites**: el spec dice "quites" y se guardan los quites ganados (`tackleSuccessful`), que es lo
  que muestra WhoScored.
- **Rating**: se guarda con dos decimales, redondeando si WhoScored informara más, que es como lo
  muestra el sitio (FR-008).
- **Minutos**: se miden en los minutos expandidos de WhoScored, que ya suman lo agregado. Es la
  definición de FR-005 con la precisión de un minuto que usa la fuente. Los números de la HU1
  (96, 72, 24 y 62) se reproducen en los tests con un partido de 2 y 4 minutos agregados.
- **El informe suma dos datos**: el estado del snapshot (`LOADED`, `MISSING` o `UNREADABLE`) y el
  estado de cada liga (`PROCESSED`, `CUT` o `NOT_REACHED`). Ayudan a la prueba independiente de
  la HU2 y de la HU4.
- **El corte puede ser en una página que no es de un partido**: FR-048 pide "en qué partido". Si
  el bloqueo ocurre en la página de la liga o en la de un equipo, el informe dice esa página y su
  liga.
- **Entradas del snapshot en conflicto**: el spec no lo menciona. Si un cruce del snapshot choca
  con uno de la base, gana la base y la entrada se cuenta como `CONFLICTING_CROSS`. Las
  estadísticas del jugador se cargan igual, porque se ubican por su id de Football-Data.
- **Entradas del snapshot que no se cuentan como salteadas**: las de un partido que ya tiene
  estadísticas, que conserva las suyas (HU2, escenario 6), y las de otra temporada o de un equipo
  que ya no juega la liga. No son una anomalía: aparecerían en casi todas las corridas.
- **Partido que falla al guardarse**: el spec no lo menciona. Se omite con `WRITE_FAILED`, queda
  pendiente y la corrida sigue.
- **Valor imposible de la fuente**: el spec no lo menciona. Un rating fuera de 0 a 10 o una
  métrica negativa omiten el partido con `INVALID_IN_SOURCE`; queda pendiente y la corrida sigue.
- **Tolerancia del cruce por calendario**: el spec dice que los partidos "coinciden". Se admiten
  hasta dos fechas sin alinear, para no dejar sin cruzar a un equipo por una fecha reprogramada
  que una fuente todavía no actualizó, y se exige que los rivales correspondan uno a uno.
- **Partido con los equipos al revés en la página**: si la página de un partido no trae los
  equipos cruzados del partido de Football-Data, se omite con `NOT_IN_SOURCE`.

## Contexto técnico

**Lenguaje y versión**: Java 21 (Temurin), con el wrapper de Gradle.

**Dependencias principales**:

- Spring Boot 4.1.1 (Spring Framework 7.0.9), con `webmvc`, `data-jpa`, `security`,
  `validation`, `actuator` y `h2console`.
- jjwt 0.13.0, springdoc-openapi 3.1.1, Lombok, Jackson 3.1.5 y Hibernate 7.4.5.
- **Ninguna dependencia nueva** (D1).

**Almacenamiento**: H2.

- En `local`, en archivo con `ddl-auto: update`. **La base no se borra**: las columnas nuevas son
  nulas o tienen un default.
- En `test`, en memoria con `create-drop`.
- Cambian tres tablas (`teams`, `players` y `matches`) y hay dos nuevas (`player_match_stats` y
  `last_full_syncs`).
- Además, un archivo versionado: `backend/snapshot/whoscored-stats.jsonl`.

**Tests**:

- JUnit 5, Mockito y AssertJ.
- `MockMvcTester` con `@SpringBootTest` y `@AutoConfigureMockMvc`.
- `@MockitoBean`, `MockRestServiceServer`, `OutputCaptureExtension` y `@TempDir`.
- Todo con `@ActiveProfiles("test")`, sin Testcontainers y sin TDD.
- Ningún test consulta WhoScored ni Football-Data.

**Plataforma**: API REST local en el puerto 8080, con `./gradlew bootRun`. Salen a internet solo
las sincronizaciones: hacia `api.football-data.org` y hacia `www.whoscored.com`.

**Tipo de proyecto**: servicio web en un monorepo (`backend/` y `frontend/`). Solo se toca el
backend: el frontend no cambia, porque las estadísticas no se exponen.

**Objetivos de rendimiento**:

- Una fecha completa (48 partidos), en menos de 15 minutos (SC-004): unos 5.
- La temporada desde el inicio sin snapshot (unos 330 partidos), en menos de 60 minutos (SC-005):
  unos 47.
- Con un snapshot al día hasta la fecha anterior, todo guardado menos de 15 minutos después de
  Football-Data (SC-006).
- El arranque no espera a ninguna fuente (SC-011).

**Restricciones**:

- Una página de WhoScored a la vez, con al menos 4 s entre una y la siguiente.
- Un único reintento, a los 30 s; un segundo fallo corta la corrida.
- Hay a lo sumo una sincronización a la vez de las dos fuentes, y la cadena cuenta como una.
- Cada partido se guarda todo o nada.
- Las estadísticas no se exponen. El snapshot no lleva ningún otro dato.
- No se reemplazan `build.gradle` ni los `application*.yml`: solo se agregan claves y una tarea.
- Solo se tocan los tests listados en D26, con el sí del 2026-10-13.
- Los términos de WhoScored no permiten el scraping: se pide poco volumen.

**Escala y alcance**:

- Datos del 2026-10-13: 96 equipos, unos 2.650 jugadores, 1.752 partidos por temporada, unos 330
  terminados y unas 32 apariciones por partido.
- Unas 10.500 filas de estadísticas hoy y unas 56.000 por temporada.
- El snapshot pesa unos 600 KB hoy y unos 3 MB por temporada.
- 1 endpoint nuevo y 1 que cambia solo en su documentación.
- 1 contexto nuevo (`performance`).
- 57 requisitos funcionales (FR-001 a FR-055, más FR-017a y FR-019a) y 18 criterios de éxito.

No queda ningún NEEDS CLARIFICATION: todo se resolvió en [research.md](./research.md).

## Verificación constitucional

*Puerta: tiene que pasar antes de la investigación y se revisa de nuevo después del diseño.*

### Antes de la investigación

| Principio | Resultado | Evidencia |
|---|---|---|
| I. Capas estrictas (NO NEGOCIABLE) | Cumple | Ver el detalle debajo de la tabla. |
| II. Modelo rico | Cumple | Las reglas viven en el modelo: `ReportedMatchStats` (minutos, goles, asistencias y tarjetas), `TeamCrossing` y `PlayerCrossing` (cruces), `LeagueStatsSync` (pendientes, omisiones y qué página pedir), `SnapshotLoad` y `StatsSnapshot.merge` (snapshot), `CatchUpPolicy` (puesta al día) y `lastFullSync()` (éxito). Los `if` de los servicios son de la aplicación: si hay token, si el candado está libre y si `on-startup` está prendido. |
| III. Validación en su nivel | Cumple | Las invariantes están en el modelo, con excepciones propias: `crossWith` no cambia un cruce, `MetricCounts` no admite negativos y el rating tiene su rango. `league` se valida con `League.fromName` (005, D15). El advice sigue siendo único y no suma ningún status. |
| IV. Tests (NO NEGOCIABLE) | Cumple | Hay unitarios de modelo sin Spring, de servicio con mocks, del adapter con `MockRestServiceServer`, integración contra H2 y end to end en `e2e/`. Los tests existentes que se tocan son tres, con el sí de Lucas del 2026-10-13: el aval del spec más el sí para los dos casos del borde (decisión 3). Se borran un método de `StartupSyncTest` y uno de `SyncSchedulerTest`, y se renombran tres, con el sí del 2026-10-14. El resto queda igual, y los constructores de conveniencia de las entidades evitan cambios innecesarios. |
| V. Calidad medible | Cumple | Ver el detalle debajo de la tabla. |
| VI. Persistencia explícita | Cumple | Las tablas nuevas tienen `@Table(name = ...)` y los índices únicos tienen nombre. En producción solo hay derived queries y `@EntityGraph`; el único SQL nativo es un `INSERT` estándar en `MatchStatsRepositoryIT`, aprobado por el equipo el 2026-10-14 (D17). Los enums nuevos son `VARCHAR`. `@ColumnDefault("false")` genera un `default false` que también existe en PostgreSQL. `open-in-view` y los perfiles no cambian. |
| VII. Idioma | Cumple | Los identificadores, el endpoint (`/matches/stats/sync`) y el contexto (`performance`) están en inglés y en singular. Los mensajes, la documentación y los comentarios están en español. |
| Stack tecnológico | Cumple | Sin dependencias nuevas. `build.gradle` suma una tarea `JavaExec` y no se reemplaza. |
| Definición de terminado | Cumple | `./gradlew build` y `./gradlew bootRun` con `local`, sobre la base existente. `POST /matches/stats/sync` queda documentado y se puede ejecutar desde `/swagger-ui.html` con los dos esquemas de Authorize ([quickstart.md](./quickstart.md), sección 6). |

Detalle del principio I:

- **El servicio habla con WhoScored solo a través de su adapter.** `StatsSyncService` usa
  `WhoScoredAdapter` y no conoce `RestClient`, el HTML ni el JSON de la fuente.
- **El adapter devuelve modelo** (`ReportedTeam`, `ReportedFixture` y `ReportedMatchStats`). Sus
  DTO viven en `adapter/whoscored/dto/` y no salen del adapter.
- **El adapter traduce con su propio mapper y no persiste**: lo que trae lo guarda
  `StatsWriteService` a través de los repositories.
- **Ninguna excepción del cliente HTTP se propaga**: `HttpPageDownloader` la vuelve una página
  fallida, y el bloqueo sale como `SourceBlockedException`, que es del modelo.
- **El modelo es puro**: los tipos nuevos usan solo el JDK (`java.time`, `java.text.Normalizer`,
  `java.math` y colecciones).
- **Persistencia**: las anotaciones de JPA y de Hibernate viven solo en las clases `*SQL`. Los
  mappers los invoca `repository/`. `StatsSnapshotRepository` recibe y devuelve modelo, y el
  formato de las líneas vive en su mapper.
- **Controller y DTO**: `StatsSyncController` solo habla con `StatsSyncService`.
  `StatsSyncReportResponse` vive en `controller/sync/dto/` y lo arma el controller a partir del
  `StatsSyncReport`.
- **Contexto nuevo declarado en este plan**: `performance`, en modelo y persistencia. `sync` suma
  clases en persistencia. Cada contexto aparece solo en las capas donde tiene clases.
- **`adapter/` se organiza por proveedor**: `whoscored` es hermano de `footballdata`.
- **No se crean carpetas fuera del árbol.** `StatsSnapshotExport` es una clase del paquete raíz,
  junto a `FutbolMarketApplication`: es un punto de entrada, no un paquete por feature.
- **Monolito**: un partido modifica jugadores (sus cruces), estadísticas y el partido en UN
  método `@Transactional` (`StatsWriteService.saveMatchStats`). La corrida son muchas
  transacciones a propósito (FR-013 y FR-045). No hay ciclos:
  - `SyncCoordinator` depende de `SyncService` y de `StatsSyncService`;
  - cada uno de ellos depende de `SyncLock` y de su servicio de escritura.

Detalle del principio V:

- **Inyección y logging**: inyección por constructor y SLF4J.
- **Constantes con nombre**: los marcadores de los bloques, las posiciones de `fixtureMatches`, los
  códigos de tarjeta, los umbrales del cruce (`MIN_ALIGNED_FIXTURES`, `MAX_UNALIGNED_FIXTURES` y
  `DATE_TOLERANCE`) y las palabras de club.
- **Excepciones**:
  - No hay `catch (Exception e)`. Los servicios capturan solo `SourceBlockedException` (corte),
    `DataAccessException` (un partido que no se guarda), `SyncInProgressException`
    (disparadores automáticos) y, al pedir y preparar un partido scrapeado,
    `CatalogInvariantException` (un valor imposible de la fuente, D18).
  - El adapter captura `RestClientException` (una página fallida, D3) y `JacksonException` (un
    bloque ilegible es un rechazo, D4). El repository del snapshot captura `JacksonException` y
    `CatalogInvariantException` (un archivo ilegible, D20).
  - `InterruptedException` restaura la marca de interrupción.
- **Sin duplicación**:
  - `ScriptBlockReader` recorta los tres bloques;
  - `Metric` ordena las métricas en un único recorrido para la tabla, el adapter y el snapshot;
  - `SyncLock` reemplaza los dos candados que habría.
- **Tests sin `Thread.sleep`**: `RecordingWhoScoredSleeper` y un `Clock` fijo.
- **Secretos**: WhoScored no usa credencial. El snapshot no lleva datos de usuarios.

**Resultado**: la puerta pasa.

### Después del diseño

Se revisaron [data-model.md](./data-model.md), [contracts/](./contracts/) y
[quickstart.md](./quickstart.md) contra cada principio:

- **I**: ningún tipo de `modelo/` importa Spring, JPA, Jackson ni el adapter.
  - El grafo de dependencias entre paquetes no tiene ciclos:
    - `controller.sync → service.sync, modelo`
    - `service.sync → adapter.footballdata, adapter.whoscored, persistence.repository, modelo`
    - `adapter.whoscored → modelo`
    - `persistence → modelo`
  - `config/WhoScoredClientConfig` arma el `RestClient` que usa el adapter y `StatsSnapshotExport`
    usa `service.sync`: los dos son transversales y no cruzan ninguna capa.
- **II**: `StatsSyncService`, `StatsWriteService` y `SyncCoordinator` no tienen un `if` de
  negocio.
  - Cada decisión de los escenarios de las HU1 a HU4 tiene su método de modelo en la tabla de
    trazabilidad de [data-model.md](./data-model.md).
  - Las consultas cargan por temporada o por id y no deciden nada.
- **III**: los mensajes de error están en español y no repiten datos sensibles. El de
  `SourceBlockedException` lleva la ruta de la página, que es pública.
- **IV**: la lista exacta de tests tocados está en D26 y en la Estructura del proyecto. El 409 se
  prueba de forma determinística, con un latch, y la puesta al día no corre en los tests.
- **VI**: no hay SQL nativo en producción; la única excepción, un `INSERT` en un test, está
  aprobada (D17). Las columnas nuevas no dependen de tipos propios de H2.
- **Stack**: no aparece ninguna dependencia.

**Resultado**: la puerta sigue pasando.

## Estructura del proyecto

### Documentación de la feature

```text
specs/006-scraping-whoscored/
├── spec.md                        # especificación (sin cambios en este plan)
├── plan.md                        # este archivo
├── research.md                    # decisiones D1 a D27 y lo verificado contra WhoScored
├── data-model.md                  # modelo, persistencia, transiciones y trazabilidad
├── quickstart.md                  # guía de validación de punta a punta
├── contracts/
│   ├── sync-api.yaml              # OpenAPI 3.0.3: POST /matches/stats/sync y cambios en POST /players/sync
│   ├── configuration.md           # propiedades nuevas, valores por perfil y variables de entorno
│   ├── whoscored-pages.md         # contrato externo: páginas, bloques, campos, reglas y señales de rechazo
│   └── snapshot-format.md         # formato del snapshot, orden canónico, exportación y carga
├── checklists/requirements.md     # existente
└── tasks.md                       # lo genera después /speckit-tasks
```

### Código fuente

Producción, en `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/`:

```text
StatsSnapshotExport.java                          # NUEVO: main de ./gradlew exportStatsSnapshot (Spring sin web ni sincronizaciones)
adapter/
├── footballdata/                                 # sin cambios
└── whoscored/                                    # NUEVO proveedor
    ├── WhoScoredAdapter.java                     # fetchLeagueTeams, fetchTeamFixtures y fetchMatchStats → modelo
    ├── WhoScoredClient.java                      # una página a la vez: pausa ≥ 4 s, bloque obligatorio, un reintento a los 30 s;
    │                                             #   SourceBlockedException al segundo fallo; WARN por cada rechazo
    ├── PageDownloader.java                       # interfaz chica: bajar el HTML de una ruta (plan B: Playwright)
    ├── HttpPageDownloader.java                   # con whoScoredRestClient; ninguna excepción del cliente sale de acá
    ├── DownloadedPage.java                       # status y cuerpo, o la falla de red
    ├── WhoScoredSleeper.java                     # espera inyectable
    ├── WhoScoredThreadSleeper.java               # @Component; la interrupción → SyncInterruptedException
    ├── WhoScoredTournament.java                  # enum League ↔ región, torneo y página de la liga
    ├── WhoScoredPages.java                       # rutas de las tres páginas
    ├── ScriptBlockReader.java                    # recorta un objeto o un array, respetando strings y escapes
    ├── MatchCentreExtractor.java                 # matchCentreData: objeto, null (sin datos) o ausente (rechazo)
    ├── FixtureMatchesExtractor.java              # fixtureMatches: JavaScript → JSON (comillas y posiciones vacías)
    ├── TournamentFixturesExtractor.java          # <script data-hypernova-key="tournamentfixtures"><!--{...}-->
    ├── WhoScoredMapper.java                      # DTO → modelo: sumas, último rating, tarjetas, gol en contra, asistidor,
    │                                             #   minutos expandidos y filtro de torneo
    └── dto/                                      # records con @JsonIgnoreProperties(ignoreUnknown = true)
        ├── MatchCentreDto.java   MatchSideDto.java   MatchPlayerDto.java   MatchEventDto.java   CodeDto.java   QualifierDto.java
        ├── TournamentFixturesDto.java   TournamentDto.java   TournamentMatchDto.java
        └── FixtureRowDto.java
config/
├── ApplicationConfig.java                        # CAMBIA: + WhoScoredProperties y SyncProperties
├── WhoScoredProperties.java                      # NUEVO: futbolmarket.whoscored.* (mínimos de 4 s y 30 s)
├── WhoScoredClientConfig.java                    # NUEVO: bean whoScoredRestClient; applyDefaults estático para el test del adapter
└── SyncProperties.java                           # NUEVO: futbolmarket.sync.catch-up-after
controller/sync/
├── StatsSyncController.java                      # NUEVO: POST /matches/stats/sync[?league=]; Swagger; solo ADMIN
├── SyncController.java                           # CAMBIA: solo la descripción del 409 en Swagger
└── dto/StatsSyncReportResponse.java              # NUEVO: el informe (records anidados y from(StatsSyncReport))
modelo/
├── match/Match.java                              # CAMBIA: + whoscoredId y statsLoaded; needsStats, crossWith, markStatsLoaded y utcDay;
│                                                 #   updateFrom los conserva; se conserva el constructor de 11
├── performance/                                  # NUEVO contexto
│   ├── PlayerMatchStats.java   Metric.java   MetricCounts.java
│   └── ReportedMatchStats.java   ReportedAppearance.java   ReportedGoal.java   ReportedCard.java   CardKind.java   Side.java
├── player/Player.java                            # CAMBIA: + whoscoredId; crossWith y ageOn; updateFrom y deactivate lo conservan
├── season/CurrentSeasons.java                    # NUEVO
├── sync/
│   ├── SyncOrigin.java                           # CAMBIA: STARTUP → CATCH_UP
│   ├── SyncReport.java                           # CAMBIA: + lastFullSync()
│   ├── SyncSource.java   LastFullSync.java   CatchUp.java   CatchUpPolicy.java                       # NUEVOS
│   ├── NormalizedName.java   ReportedTeam.java   ReportedFixture.java                                # NUEVOS
│   ├── TeamCrossing.java   CatalogPlayers.java   PlayerCrossing.java   MatchStatsLoad.java   LoadSource.java   CatalogReferences.java   # NUEVOS
│   ├── LeagueStatsSync.java   StatsSyncRun.java   StatsSyncReport.java   LeagueStatsResult.java   LeagueStatsStatus.java   # NUEVOS
│   ├── SkippedStatsMatch.java   StatsSkipReason.java   UncrossedTeam.java   UncrossedPlayer.java   CrossFailure.java   # NUEVOS
│   ├── RunCut.java   BlockedPage.java   PageKind.java                                                # NUEVOS
│   ├── StatsSnapshot.java   SnapshotTeamEntry.java   SnapshotMatchEntry.java   SnapshotStatsEntry.java   SnapshotPlayerRow.java   # NUEVOS
│   ├── SnapshotLoad.java   SnapshotRead.java   SnapshotStatus.java   SnapshotSkipReason.java          # NUEVOS
│   └── exception/SourceBlockedException.java   SyncInterruptedException.java                        # NUEVAS
└── team/Team.java                                # CAMBIA: + whoscoredId; crossWith; updateFrom lo conserva
persistence/
├── mapper/
│   ├── match/MatchMapper.java                    # CAMBIA: + whoscoredId y statsLoaded
│   ├── player/PlayerMapper.java                  # CAMBIA: + whoscoredId
│   ├── team/TeamMapper.java                      # CAMBIA: + whoscoredId
│   ├── performance/PlayerMatchStatsMapper.java   # NUEVO
│   └── sync/LastFullSyncMapper.java   StatsSnapshotLineMapper.java   # NUEVOS
├── repository/
│   ├── match/MatchRepository.java                # CAMBIA: + findAllBySeason, findAllCrossed y findAllWithStats
│   ├── player/PlayerRepository.java              # CAMBIA: + findAllWithTeam
│   ├── season/SeasonRepository.java              # CAMBIA: + findAll
│   ├── team/TeamRepository.java                  # CAMBIA: + findAllCrossed
│   ├── performance/PlayerMatchStatsRepository.java   # NUEVO
│   └── sync/LastFullSyncRepository.java   StatsSnapshotRepository.java   # NUEVOS (el segundo, sobre el archivo)
└── sql/
    ├── entity/
    │   ├── match/MatchSQL.java                   # CAMBIA: + whoscored_id (ux_matches_whoscored_id) y stats_loaded (@ColumnDefault)
    │   ├── player/PlayerSQL.java                 # CAMBIA: + whoscored_id (ux_players_whoscored_id)
    │   ├── team/TeamSQL.java                     # CAMBIA: + whoscored_id (ux_teams_whoscored_id)
    │   ├── performance/PlayerMatchStatsSQL.java  # NUEVO: tabla player_match_stats
    │   └── sync/LastFullSyncSQL.java             # NUEVO: tabla last_full_syncs
    └── interfaces/
        ├── match/MatchSQLDAO.java                # CAMBIA: por temporada, cruzados y con estadísticas
        ├── player/PlayerSQLDAO.java              # CAMBIA: findAll con @EntityGraph del equipo
        ├── team/TeamSQLDAO.java                  # CAMBIA: findAllByWhoscoredIdIsNotNull
        ├── performance/PlayerMatchStatsSQLDAO.java   # NUEVO
        └── sync/LastFullSyncSQLDAO.java          # NUEVO
security/
└── SecurityConfig.java                           # CAMBIA: POST /matches/stats/sync → hasRole(ADMIN)
service/sync/
├── SyncService.java                              # CAMBIA: SyncLock en lugar del Semaphore; runFull para la cadena; guarda la última completa
├── SyncScheduler.java                            # CAMBIA: la corrida semanal es la cadena
├── StartupSync.java                              # CAMBIA: la puesta al día (la advertencia sin token no cambia)
├── SyncLock.java                                 # NUEVO: el candado de las dos fuentes
├── SyncCoordinator.java                          # NUEVO: la cadena y la puesta al día
├── StatsSyncService.java                         # NUEVO: snapshot → descubrimiento → scraping → informe; corte por bloqueo
├── StatsWriteService.java                        # NUEVO: @Transactional: un partido, y los cruces de una liga
├── StatsSnapshotService.java                     # NUEVO: lectura y exportación del snapshot
└── StatsSyncReportLogger.java                    # NUEVO: el informe al registro
```

No se borra ninguna clase de producción. Del enum `SyncOrigin` desaparece `STARTUP`, renombrado.

Recursos y archivos del repositorio:

```text
backend/src/main/resources/application.yaml       # CAMBIA: + bloques futbolmarket.whoscored y futbolmarket.sync
backend/src/main/resources/application-local.yml  # sin cambios
backend/build.gradle                              # CAMBIA: + tarea exportStatsSnapshot (JavaExec), sin dependencias
backend/snapshot/whoscored-stats.jsonl            # NUEVO: el primer snapshot, generado en la fase 9
.gitattributes                                    # CAMBIA: + backend/snapshot/*.jsonl text eol=lf
.gitignore                                        # CAMBIA: + backend/data-*/ (respaldos de la base local, FR-032)
```

Tests, en `backend/src/test/`:

```text
java/ar/edu/unq/desapp/futbolmarket/
├── adapter/whoscored/
│   ├── ScriptBlockReaderTest.java                # NUEVO
│   ├── MatchCentreExtractorTest.java   FixtureMatchesExtractorTest.java   TournamentFixturesExtractorTest.java   # NUEVOS
│   ├── WhoScoredMapperTest.java                  # NUEVO: traducciones sin HTTP (quites, rating, tarjetas, asistidor)
│   ├── WhoScoredAdapterTest.java                 # NUEVO: MockRestServiceServer (headers, pausa, reintento, corte, null)
│   ├── WhoScoredThreadSleeperTest.java           # NUEVO: la interrupción → SyncInterruptedException
│   └── RecordingWhoScoredSleeper.java            # NUEVO: doble de WhoScoredSleeper
├── config/
│   ├── WhoScoredPropertiesTest.java              # NUEVO: valores de application.yaml y mínimos
│   ├── WhoScoredClientConfigTest.java            # NUEVO: redirecciones y timeout del HttpClient, sin red
│   └── SyncPropertiesTest.java                   # NUEVO
├── e2e/
│   ├── StatsSyncControllerIT.java                # NUEVO: 200 (completa, una liga, cortada), 400, 401, 403, 409
│   └── StatsSyncWithoutTokenIT.java              # NUEVO: sin token de FD, WhoScored corre igual (como SyncDisabledIT)
├── modelo/
│   ├── match/MatchStatsStateTest.java            # NUEVO (MatchTest no cambia)
│   ├── performance/
│   │   └── PlayerMatchStatsTest.java   MetricCountsTest.java   ReportedMatchStatsTest.java   # NUEVOS
│   ├── player/PlayerWhoScoredTest.java           # NUEVO (PlayerTest no cambia)
│   ├── season/CurrentSeasonsTest.java            # NUEVO
│   ├── sync/
│   │   ├── NormalizedNameTest.java   TeamCrossingTest.java   CatalogPlayersTest.java   PlayerCrossingTest.java   # NUEVOS
│   │   ├── LeagueStatsSyncTest.java   SnapshotLoadTest.java   StatsSnapshotTest.java   StatsSyncRunTest.java   # NUEVOS
│   │   ├── CatchUpPolicyTest.java   SyncReportLastFullSyncTest.java   # NUEVOS
│   │   └── StatsFixtures.java                    # NUEVO: datos de prueba compartidos (temporada, calendarios, apariciones)
│   └── team/TeamWhoScoredTest.java               # NUEVO (TeamTest no cambia)
├── persistence/repository/
│   ├── match/MatchStatsRepositoryIT.java         # NUEVO (H2 stats-it)
│   ├── player/PlayerCrossRepositoryIT.java       # NUEVO (H2 stats-it)
│   ├── team/TeamCrossRepositoryIT.java           # NUEVO (H2 stats-it)
│   ├── performance/PlayerMatchStatsRepositoryIT.java   # NUEVO (H2 stats-it)
│   └── sync/
│       ├── LastFullSyncRepositoryIT.java         # NUEVO (H2 stats-it)
│       └── StatsSnapshotRepositoryTest.java      # NUEVO: @TempDir, sin Spring
└── service/sync/
    ├── StatsSyncServiceTest.java   SyncCoordinatorTest.java   SyncLockTest.java   # NUEVOS
    ├── StatsSyncReportLoggerTest.java   StatsSnapshotServiceTest.java   SyncServiceLastFullSyncTest.java   # NUEVOS
    ├── StatsWriteServiceIT.java                  # NUEVO (H2 stats-it): rollback por partido; FD conserva los cruces y la marca
    ├── SyncChainIT.java                          # NUEVO (H2 stats-it): la cadena con los dos adapters mockeados y un 409 en el medio
    ├── StartupSyncTest.java                      # SE MODIFICA (se renombran dos métodos y se borra uno)
    ├── SyncSchedulerTest.java                    # SE MODIFICA (se renombra un método y se borra uno; el del cron no cambia)
    └── SyncServiceTest.java                      # SE MODIFICA solo el helper y un @Mock
resources/
├── application-test.yml                          # CAMBIA: + whoscored (URL .invalid y snapshot inexistente)
└── whoscored/                                    # NUEVO: match-1983600.html, match-second-yellow.html, match-unavailable.html,
                                                  #   fixtures-everton.html, league-premier.html, rejected-403.html, snapshot-sample.jsonl
```

**Tests existentes que cambian**: el detalle de cada cambio y su motivo está en
[research.md](./research.md), D26. En resumen:

- `StartupSyncTest`: se adapta a la puesta al día, se renombran dos métodos y se borra
  `conJugadoresNoDisparaYDejaElInfo`, cuya regla (FR-033 de la 005) desaparece;
- `SyncSchedulerTest`: la corrida semanal es la cadena; se renombra un método, se borra
  `sinTokenNoLlamaAlServicioYDejaUnInfo`, que quedaría repetido, y el método del cron no cambia;
- `SyncServiceTest`: solo el helper del constructor y un `@Mock`;
- el resto queda sin cambios, incluidos `SyncControllerIT`, `SyncDisabledIT`, `TeamTest`,
  `PlayerTest`, `MatchTest`, `LeagueSyncTest`, `SyncWriteServiceIT` y `FootballDataPropertiesTest`.

**Decisión de estructura**:

- Se usa el árbol de la constitución 2.2.1, con `adapter/whoscored/` como segundo proveedor.
- El contexto `performance` queda declarado en este plan.
- No se crea ninguna carpeta fuera del árbol, ni carpetas vacías ni `.gitkeep`.
  `backend/snapshot/` es una carpeta de datos del repositorio, no un paquete.
- Los tests replican capa y contexto. Los end to end viven en `e2e/` y las fixtures en
  `src/test/resources/whoscored/`.

## Dependencias a agregar

**Ninguna.** `build.gradle` suma solo la tarea `exportStatsSnapshot`, de tipo `JavaExec`, sin
dependencias. Todo lo que hace falta ya está en el classpath:

- `RestClient` y `JdkClientHttpRequestFactory` (spring-web 7.0.9);
- `JsonMapper` (jackson-databind 3.1.5), para los bloques y el snapshot;
- `@ColumnDefault` (hibernate-core 7.4.5);
- `java.text.Normalizer` (JDK 21);
- `MockRestServiceServer` (spring-test) y `@TempDir` (JUnit 5).

Playwright queda documentado como plan B, con su criterio para activarlo, y **no** se agrega. La
verificación y las alternativas descartadas están en [research.md](./research.md), D1 y D5.

## Secuencia de implementación

Estas son las restricciones de orden para `/speckit-tasks`. Cada fase deja `./gradlew build` en
verde.

1. **Preparación**:
   - Las propiedades de [contracts/configuration.md](./contracts/configuration.md) en
     `application.yaml` y `application-test.yml`.
   - `WhoScoredProperties` y `SyncProperties` con sus tests, registradas en `ApplicationConfig`.
   - `WhoScoredClientConfig`.
   - La línea de `.gitattributes` y la de `.gitignore` (`backend/data-*/`).
2. **Modelo nuevo**:
   - `performance`: `Metric`, `MetricCounts`, `PlayerMatchStats` y los `Reported*`, con
     `ReportedMatchStatsTest` (minutos, goles, asistencias y tarjetas).
   - `Team`, `Player` y `Match` con su id de WhoScored y la marca: `crossWith`, `ageOn`,
     `needsStats` y `updateFrom` que los conserva, con los constructores de conveniencia. Sus
     tests son clases nuevas.
   - `CurrentSeasons`.
   - En `sync`: `NormalizedName`, `TeamCrossing`, `CatalogPlayers`, `PlayerCrossing`,
     `MatchStatsLoad`, `LeagueStatsSync`, el snapshot (`StatsSnapshot`, entradas y
     `SnapshotLoad`), la corrida y el informe, `SyncSource`, `LastFullSync`, `CatchUpPolicy`,
     `SyncReport.lastFullSync()` y las dos excepciones, con sus unitarios.
3. **Persistencia**:
   - Las columnas nuevas de `TeamSQL`, `PlayerSQL` y `MatchSQL`, con sus mappers.
   - `PlayerMatchStatsSQL` y `LastFullSyncSQL`, con sus DAOs, mappers y repositories.
   - Las consultas nuevas de los repositories existentes.
   - Los IT sobre la H2 `stats-it`, incluido el default de `stats_loaded`.
   - Los tests existentes de los repositories tienen que seguir pasando sin cambios.
4. **Adapter**:
   - Los DTO, `ScriptBlockReader`, los tres extractores, `WhoScoredTournament`, `WhoScoredPages`
     y `WhoScoredMapper`.
   - `PageDownloader`, `HttpPageDownloader`, `WhoScoredSleeper`, `WhoScoredThreadSleeper`,
     `WhoScoredClient` y `WhoScoredAdapter`.
   - Las fixtures de `src/test/resources/whoscored/` y los tests del adapter.
5. **Servicio de WhoScored**:
   - `SyncLock` (todavía lo usa solo WhoScored), `StatsWriteService`, `StatsSyncService` (sin el
     snapshot) y `StatsSyncReportLogger`.
   - `StatsSyncServiceTest`, `SyncLockTest`, `StatsSyncReportLoggerTest` y `StatsWriteServiceIT`.
     Este último incluye que una sincronización de Football-Data posterior conserva los cruces y
     la marca.
6. **Snapshot**:
   - `StatsSnapshotLineMapper`, `StatsSnapshotRepository` y `StatsSnapshotService` (lectura y
     exportación).
   - La fase 1 de `StatsSyncService`: el snapshot de todas las ligas antes del primer pedido.
   - `StatsSnapshotExport` y la tarea `exportStatsSnapshot` en `build.gradle`.
   - `StatsSnapshotRepositoryTest`, `StatsSnapshotServiceTest` y los casos de snapshot de
     `StatsSyncServiceTest`.
7. **Coordinador**:
   - `SyncService` pasa a `SyncLock`, suma `runFull` y guarda la última completa.
   - `SyncOrigin`: `STARTUP` → `CATCH_UP`.
   - `SyncCoordinator`, `StartupSync` y `SyncScheduler` con la cadena y la puesta al día.
   - **Acá se modifican los tres tests de la 005**, según D26.
   - `SyncCoordinatorTest` y `SyncChainIT`.
8. **Disparo manual**:
   - `StatsSyncController` y `StatsSyncReportResponse`, con Swagger.
   - La regla `ADMIN` en `SecurityConfig` y el texto del 409 de `SyncController`.
   - `StatsSyncControllerIT`, con el 409 determinístico.
9. **Cierre y definición de terminado**:
   - `./gradlew build`.
   - `./gradlew bootRun` con `local` sobre la base existente: la puesta al día de las dos fuentes
     ([quickstart.md](./quickstart.md), sección 2).
   - La prueba con pocas páginas (sección 3).
   - La primera puesta al día completa, la generación del primer snapshot con
     `./gradlew exportStatsSnapshot` y su commit (sección 5).
   - Las secciones 4, 6 y 7.

**Ajustes de orden de tasks.md (2026-10-14)**:

- La lectura del snapshot y `StatsSnapshotService` (paso 6) van antes de `StatsSyncService`
  (paso 5), para que el servicio y su test nazcan con su forma final (principio IV). La fase 1 de
  `StatsSyncService` queda en el paso 5, y `StatsSnapshotExport` con su tarea de Gradle, en la HU2.
- El disparo manual (paso 8) va antes que el coordinador (paso 7), porque es la HU1 y el MVP. No
  hay dependencia técnica: los 409 que necesitan el candado común se prueban con el coordinador.

**Riesgo de merge**: el compañero puede tocar `application.yaml`, `ApplicationConfig`,
`SecurityConfig` o las entidades del catálogo en otra rama. Los cambios de esta feature son
agregados dentro de bloques existentes, así que un conflicto se resuelve conservando las dos
partes. El snapshot es un archivo nuevo, y sus conflictos se resuelven exportando otra vez
([contracts/snapshot-format.md](./contracts/snapshot-format.md)).

## Seguimiento de complejidad

No hay desvíos de la constitución. Estos puntos quedan registrados porque se apartan de lo que hizo
la 005, sin violar ningún principio:

- **Una tarea en `build.gradle`**: la constitución prohíbe reemplazarlo, no ampliarlo. La tarea no
  suma dependencias y la eligió el equipo (decisión 4).
- **Una segunda clase main en el paquete raíz**: es un punto de entrada, como
  `FutbolMarketApplication`, y no crea un paquete por feature.
- **Un repository sobre un archivo** (`StatsSnapshotRepository`): cumple la regla de
  `repository/` (recibe y devuelve modelo) y deja el formato en su mapper, como las clases `*SQL`
  dejan el de las filas.
- **`@ColumnDefault` de Hibernate**: es una anotación de persistencia en una clase `*SQL`, como
  las de JPA. Genera un `default false` portable y evita borrar la base local.
