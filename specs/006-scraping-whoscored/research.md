# Investigación: Scraping de WhoScored

**Rama**: `feature/scraping-whoscored` | **Fecha**: 2026-10-13 | **Plan**: [plan.md](./plan.md)

Este documento resuelve las incógnitas técnicas del plan y registra cada decisión con su
justificación y las alternativas descartadas. No quedan marcadores NEEDS CLARIFICATION. Las
decisiones D2, D21, D22 y D26 incluyen definiciones que tomó el equipo el 2026-10-13 (ver
Decisiones de planificación en el [plan](./plan.md)).

## Relevamiento del repositorio

Se leyeron los archivos existentes antes de decidir nada:

- **Rama y documentos**: se trabaja en `feature/scraping-whoscored`, sin crear ni cambiar de
  rama. `.specify/feature.json` apunta a `specs/006-scraping-whoscored` y la constitución está en
  la versión 2.2.1: capa `adapter/<proveedor>/`, árbol de carpetas que se cambia con el aval de
  un desarrollador y la lista de status del advice como base (incluye el 503).
- **`backend/build.gradle`**: Spring Boot 4.1.1, Java 21, `webmvc`, `data-jpa`, `security`,
  `validation`, `actuator`, `h2console`, jjwt 0.13.0, springdoc 3.1.1 y Lombok. Sigue sin estar
  `spring-boot-restclient`. No tiene tareas propias.
- **Configuración**:
  - `application.yaml` tiene el bloque `futbolmarket.football-data` con el token
    `${FOOTBALL_DATA_TOKEN:}`, la URL base, los timeouts de 10 s y 30 s y `sync` (cron
    `0 0 4 * * MON`, zona `America/Argentina/Buenos_Aires` y `on-startup: true`).
  - `application-test.yml` pisa el token con `""`, usa `http://football-data.invalid/v4`, el
    cron `"-"` y `on-startup: false`.
  - `application-local.yml`: H2 en archivo (`./data/futbolmarket`), `ddl-auto: update` y
    `show-sql: true`.
- **Sincronización con Football-Data** (`service/sync/`):
  - `SyncService` revisa primero el token (503) y después su propio `Semaphore(1)` con
    `tryAcquire` (409). Descarga las ligas pedidas y escribe cada una en su transacción con
    `SyncWriteService`. No guarda nada de la corrida: el informe va al registro.
  - `StartupSync` (`@Async` + `ApplicationReadyEvent`) escribe la advertencia sin token y, con
    `on-startup` en `true`, sincroniza solo si `playerRepository.hasPlayers()` es `false`.
  - `SyncScheduler` tiene `@Scheduled` con el cron y la zona de las propiedades. Sin token deja
    un INFO y no llama al servicio.
  - `SyncController` (`POST /players/sync`) está en `controller/sync/` y su regla `ADMIN` en
    `SecurityConfig`, después del matcher público.
- **Adapter de Football-Data**: `Sleeper` y `ThreadSleeper` viven en `adapter/footballdata/`.
  `ThreadSleeper` traduce la interrupción a un `ExternalSourceException` con el motivo
  `Se interrumpió la espera por el límite de consultas de la fuente.`: es propio de ese
  proveedor. `RecordingSleeper` vive en los tests del mismo paquete.
- **Catálogo**:
  - `Team`, `Player`, `Match` y `Season` son records sin anotaciones. `Player` tiene dos
    constructores de conveniencia que usan casi todos los tests.
  - `updateFrom` arma un record nuevo con los datos de la fuente. Los mappers construyen la fila
    `*SQL` entera, así que el `saveAll` de la sincronización pisa todas las columnas: **un campo
    nuevo del modelo que `updateFrom` no conserve se borraría en cada sincronización con
    Football-Data**.
  - `MatchSQL` usa `@AllArgsConstructor` de Lombok. Ningún test construye clases `*SQL`.
  - Las temporadas no tienen marca de "en curso": la de cada liga se reconoce por ser la más
    reciente.
- **Tests de la 005 que dependen de lo que cambia**:
  - `StartupSyncTest` es el único que usa `SyncOrigin.STARTUP`, y prueba la regla del catálogo
    vacío.
  - `SyncSchedulerTest` arma `SyncScheduler(properties, syncService)` y prueba el INFO sin token.
  - `SyncServiceTest` arma el servicio con un helper `service(token, clock)` de una línea. Su
    409 hace el segundo pedido desde la respuesta del adapter, sin hilos.
  - `SyncControllerIT` prueba el 409 con un latch y una H2 propia; no depende del semáforo
    interno, sino del comportamiento.
  - `SyncWriteServiceIT`, `SeasonRepositoryIT` y `MatchRepositoryIT` comparten la H2
    `jdbc:h2:mem:sync-it` y limpian en orden: partidos, temporadas, jugadores y equipos.
- **Bytecode verificado en el caché de Gradle**:
  - hibernate-core 7.4.5.Final: existe `org.hibernate.annotations.ColumnDefault`, y
    `AbstractSchemaMigrator` tiene `applyIndexes` y `applyUniqueKeys`. Con `ddl-auto: update`,
    Hibernate agrega las columnas y los índices que faltan en tablas existentes.
  - spring-web 7.0.9: `JdkClientHttpRequestFactory` tiene `enableCompression(boolean)`, apagado
    por defecto. Encendido, manda `Accept-Encoding` y descomprime gzip.
  - jackson-databind 3.1.5: los hallazgos de la 005 (D4) siguen valiendo.

## Lo verificado contra WhoScored el 2026-10-13

Además de lo que trae el pedido, el equipo verificó en el navegador, con pocas páginas y sin
apurar el ritmo:

- **Páginas de liga de las cinco ligas**. Las cinco traen el mismo bloque
  `<script type="application/json" data-hypernova-key="tournamentfixtures">`, con el JSON
  envuelto en `<!-- ... -->` (unos 47 KB en la Premier). Ese bloque tiene la fecha en curso, no
  "las semanas cercanas": 10 partidos con 20 equipos en la Premier, LaLiga y la Serie A, y 9 con
  18 en la Bundesliga y la Ligue 1. Ids verificados:

  | Liga | regionId | tournamentId | seasonId 2026/27 | stageId |
  |---|---|---|---|---|
  | Premier League | 252 | 2 | 11141 | 25544 |
  | LaLiga | 206 | 4 | 11213 | 25662 |
  | Bundesliga | 81 | 3 | 11217 | 25666 |
  | Serie A | 108 | 5 | 11126 | 25518 |
  | Ligue 1 | 74 | 22 | 11150 | 25554 |

- **Página de fixtures** (Leeds y PSG): `fixtureMatches` trae 33 posiciones por fila e incluye
  filas de copas y de la temporada anterior (FA Cup 2025/26, Mundial de Clubes 2025, Supercopa).
  La posición 6 y la 9 son las rojas de cada equipo (Marseille-PSG trae 1 en la 6). La hora es
  **la del sitio, no la local del partido**: los partidos de la Ligue 1 de las 20:45 de París
  figuran a las 19:45, la hora de Londres, con la consulta hecha desde Argentina.
- **Quites**: en `stats`, `tacklesTotal` es la suma de `tackleSuccessful` y `tackleUnsuccesful`.
  `tackleSuccessful` cuenta los eventos `Tackle` (con cualquier resultado) y
  `tackleUnsuccesful`, los `Challenge` (el jugador fue gambeteado). Los textos del propio sitio
  (`localization_debug.json`) rotulan "Tackles" a `TackleWon`, también en la tabla de partidos
  del perfil (`TackleWon_player-matches`), y "Drb" a `ChallengeLost`. Lo que WhoScored muestra
  como quites es `tackleSuccessful`, no `tacklesTotal`.
- **Asistencias**, en cuatro partidos de dos ligas (1983600, 1983552, 1983559 y 1984103): la
  regla del pedido coincide con lo que WhoScored acredita en todos los casos. Ver D10.
- **Segunda amarilla** (Marseille-PSG, 1984103): llega un evento `Card` con `cardType`
  `SecondYellow` (32), y a la primera amarilla se le suma el calificador `VoidYellowCard`. Un
  expulsado no tiene `subbedOutExpandedMinute`.
- **Partido sin jugar** (Everton-Chelsea, 1983609): la página responde con el bloque
  `require.config.params["args"]` y `matchCentreData: null`. No es un rechazo.
- **Minutos**: `expandedMinute` suma lo agregado en el primer tiempo. En el 1983600 el primer
  tiempo termina en el 47:25, el segundo empieza en el minuto 45, que es el expandido 48, y el
  partido termina en el 94:05, que es el expandido 97 (`expandedMaxMinute`). En el resumen del
  partido, Bogle sale en el 82′ del reloj con `subbedOutExpandedMinute` 85.

---

## D1. Dependencias

**Decisión**: no se agrega ninguna dependencia de producción ni de test. `build.gradle` suma
solo la tarea `exportStatsSnapshot`, que corre una clase main del propio proyecto (D21).

| Necesidad | Clase | Artefacto |
|---|---|---|
| Bajar las páginas | `RestClient` sobre `JdkClientHttpRequestFactory` | spring-web 7.0.9 y JDK 21 |
| Leer `matchCentreData`, el bloque de la liga y el snapshot | `JsonMapper` | jackson-databind 3.1.5 |
| Normalizar nombres | `java.text.Normalizer` | JDK 21 |
| Default de una columna nueva sobre filas existentes | `@ColumnDefault` | hibernate-core 7.4.5 |
| Tests del adapter | `MockRestServiceServer` | spring-test |
| Tests del snapshot | `@TempDir` | JUnit 5 |

**Justificación**: la constitución pide no agregar dependencias sin avisar y sin justificar.
Los tres bloques que se extraen son texto con un marcador fijo y un JSON (o casi JSON) adentro:
no hace falta un parser de HTML.

**Alternativas descartadas**:

- **jsoup**: arma el árbol del HTML entero para leer un `<script>`. El bloque se ubica por un
  marcador y se recorta balanceando llaves (D4).
- **Playwright**: es el plan B y queda documentado en D5. Hoy no hace falta: el 98% de las
  páginas respondió con datos a un cliente HTTP.
- **Resilience4j o spring-retry**: hay un único reintento con una espera fija (D6).

## D2. Ubicación en el árbol y contextos

**Decisión**:

| Carpeta | Contenido |
|---|---|
| `adapter/whoscored/` | `WhoScoredAdapter` (lo que usa el servicio), `WhoScoredClient` (ritmo, validación del bloque, reintento y corte), `PageDownloader` y `HttpPageDownloader`, `DownloadedPage`, `WhoScoredSleeper` y `WhoScoredThreadSleeper`, `WhoScoredTournament`, `WhoScoredPages`, `ScriptBlockReader`, los tres extractores y `WhoScoredMapper` |
| `adapter/whoscored/dto/` | Records de `matchCentreData`, del bloque de la liga y de las filas de `fixtureMatches`. Nunca salen del adapter. |
| `modelo/performance/` | **Contexto nuevo**: `PlayerMatchStats`, `Metric`, `MetricCounts` y lo que WhoScored informa de un partido (`ReportedMatchStats`, `ReportedAppearance`, `ReportedGoal`, `ReportedCard`, `CardKind` y `Side`) |
| `modelo/sync/` | Se suman la corrida y el informe de WhoScored, los cruces, el snapshot, la última completa exitosa y la puesta al día (detalle en [data-model.md](./data-model.md)) |
| `modelo/sync/exception/` | Se suman `SourceBlockedException` y `SyncInterruptedException` |
| `modelo/season/` | Se suma `CurrentSeasons` |
| `persistence/*/performance/` | `PlayerMatchStatsSQL`, su DAO, su mapper y su repository |
| `persistence/*/sync/` | `LastFullSyncSQL`, su DAO, su mapper y su repository, más `StatsSnapshotRepository` y `StatsSnapshotLineMapper` (el snapshot, que es un archivo) |
| `service/sync/` | `SyncLock`, `SyncCoordinator`, `StatsSyncService`, `StatsWriteService`, `StatsSnapshotService` y `StatsSyncReportLogger` |
| `controller/sync/` y `controller/sync/dto/` | `StatsSyncController` y `StatsSyncReportResponse` |
| `config/` | `WhoScoredProperties`, `WhoScoredClientConfig` y `SyncProperties` |
| Paquete raíz | `StatsSnapshotExport`, la clase main del comando de desarrollo (D21) |

- **`performance`** es un sustantivo en inglés y en singular: el rendimiento de un jugador en un
  partido. Se declara en este plan, como pide el Principio I, y aparece solo en modelo y
  persistencia. La cotización de la Entrega 2 lo va a consumir desde ahí.
- **`sync`** suma por primera vez clases en persistencia (`last_full_syncs` y el snapshot).
- **Nombres `Reported*`**: en la 005, `*Snapshot` es "lo que la fuente informa en una descarga"
  (`LeagueSnapshot`, `TeamSnapshot`). En esta feature, "snapshot" es el archivo versionado del
  spec. Para no confundirlos, lo que informa WhoScored se llama `Reported*` y el archivo,
  `StatsSnapshot`.
- **El snapshot es un repository sobre un archivo**: `persistence/repository/sync/` recibe y
  devuelve modelo (`StatsSnapshot`), y el formato de las líneas vive en su mapper, como el de
  las filas vive en las clases `*SQL`. Así no se crea ninguna carpeta fuera del árbol.
- **La clase main** va en el paquete raíz, junto a `FutbolMarketApplication`. Es un punto de
  entrada, no un paquete por feature.

**Justificación**: todo entra en el árbol de la constitución 2.2.1 sin carpetas nuevas. Las
estadísticas quedan en un contexto propio que la Entrega 2 puede consumir sin arrastrar la
sincronización, y la sincronización de WhoScored queda junto a la de Football-Data, con la que
comparte el candado, el origen y el informe.

**Alternativas descartadas**:

- **Contexto `stats` o `match`**: `stats` es un plural genérico. Meter las estadísticas en
  `match` mezclaría el partido con 19 métricas por jugador que la cotización va a consultar sola.
- **Contexto `whoscored`**: es el nombre de un proveedor, y los contextos son del dominio.
- **`adapter/snapshot/`**: el snapshot no es una API externa y necesitaba una carpeta nueva con
  aval.
- **Reutilizar los tipos de la corrida de Football-Data** (`SyncRun`, `LeagueSyncResult`): sus
  conteos (creados, actualizados y omitidos por entidad) y la inactivación no tienen sentido para
  WhoScored, que cuenta partidos del snapshot y scrapeados, cruces y cortes. Se reutilizan
  `SyncType`, `SyncOrigin`, `SyncInProgressException` y `UnsupportedLeagueException`.

## D3. Cliente HTTP

**Decisión**:

- `config/WhoScoredClientConfig` declara el bean `RestClient` `whoScoredRestClient`, armado como
  el de Football-Data:
  - `JdkClientHttpRequestFactory` sobre `HttpClient.newBuilder()` con `connectTimeout` (10 s),
    `followRedirects(HttpClient.Redirect.NORMAL)` y la versión por defecto, HTTP/2 con
    *fallback* a HTTP/1.1. `setReadTimeout` de 30 s, que cubre también el cuerpo (005, D3).
  - URL base y headers por defecto tomados de las propiedades: `User-Agent`, `Accept` y
    `Accept-Language`, con los valores verificados ([contracts/whoscored-pages.md](./contracts/whoscored-pages.md)).
  - Un `applyDefaults` estático, igual que `FootballDataClientConfig`, para que el test del
    adapter arme su builder enlazado a un `MockRestServiceServer`.
  - Un `httpClient` estático, que usa el bean, para probar sin red las redirecciones y el timeout
    de conexión (`WhoScoredClientConfigTest`).
- **Compresión apagada** (el valor por defecto de spring-web 7.0.9): es la combinación verificada
  con `curl` y con Java, y el cliente del JDK no descomprime solo.
- `HttpPageDownloader` usa `exchange` para leer el status y el cuerpo como `String` sin que el
  `RestClient` lance excepciones por un 403. Una `RestClientException` (timeout, red) se traduce
  a una página fallida dentro del adapter: nunca sale una excepción del cliente HTTP.

**Justificación**: el cliente del JDK negoció HTTP/2 y respondió con datos (9 de 10 en la prueba
con Java, 49 de 50 con `curl`). La página de un partido pesa entre 1,0 y 1,3 MB y tardó hasta
4,3 s, así que 30 s de lectura sobran.

**Alternativas descartadas**:

- **Encender la compresión**: bajaría el tráfico a una fracción, pero no se probó contra
  WhoScored. Queda como una línea en `WhoScoredClientConfig` si hace falta.
- **Reutilizar `footballDataRestClient`**: tiene otra URL base y el token como header.
- **Seguir redirecciones con `NEVER`** (el default del JDK): si WhoScored redirige a la URL con
  el *slug*, la página llegaría como un 301 sin el bloque y se tomaría como un rechazo.

## D4. Interfaz chica y extracción de los bloques

**Decisión**:

- `PageDownloader` es la interfaz que pide el pedido: `DownloadedPage download(String path)`.
  `DownloadedPage` es un record del adapter con el status y el cuerpo, o con la falla de red. Es
  lo único que cambia si se pasa a Playwright (D5).
- `WhoScoredClient` recibe un extractor por pedido. Una página es **válida** solo si responde 200
  y el extractor encuentra su bloque. Si no, es un rechazo (D6).
- `ScriptBlockReader` recorta un objeto o un array desde una posición, contando `{`, `}`, `[` y
  `]` y salteando los strings con comillas simples o dobles y sus escapes (`\\`, `\"`, `\'`). Lo
  usan los tres extractores, así no se duplica el recorrido.
- **`MatchCentreExtractor`**: busca `matchCentreData:`.
  - Si sigue `null`, la página es válida pero el partido todavía no tiene datos: devuelve "sin
    datos", que no es un rechazo (lo verificado en 1983609).
  - Si sigue `{`, recorta el objeto y lo lee con Jackson a `MatchCentreDto`. El bloque es JSON
    válido.
  - Si el marcador no está, o el JSON no se puede leer, la página llegó sin los datos.
  - Devuelve los tres resultados como `Optional<Optional<MatchCentreDto>>`: vacío es un rechazo, y
    un `Optional` vacío adentro es una página válida sin datos.
- **`FixtureMatchesExtractor`**: busca `fixtureMatches:` y recorta el array. Como es JavaScript,
  lo normaliza a JSON con un recorrido de un solo paso: cada string con comillas simples pasa a
  comillas dobles (`\'` queda `'` y `"` pasa a `\"`), y cada posición vacía (`[,`, `,,` o `,]`)
  pasa a `null`. Después lo lee como `List<List<JsonNode>>` y arma un `FixtureRowDto` por fila,
  tomando las posiciones del contrato.
- **`TournamentFixturesExtractor`**: busca el `<script>` con
  `data-hypernova-key="tournamentfixtures"`, quita el `<!--` y el `-->` y lee el JSON a
  `TournamentFixturesDto`.
- Los DTO son records con `@JsonIgnoreProperties(ignoreUnknown = true)`, con wrappers en todo lo
  numérico (005, D4) y `@JsonProperty` en los campos que empiezan con `is` (`isGoal`,
  `isOwnGoal`, `isFirstEleven` e `isManOfTheMatch`). Los ids de WhoScored son números en el JSON
  y pasan a texto en el mapper, como los de Football-Data.

**Justificación**: los tres bloques tienen un marcador fijo verificado y son JSON, o JavaScript
muy cerca de JSON. Validar el bloque y no solo el status es lo que pide el pedido: el rechazo
puede no ser un 403.

**Alternativas descartadas**:

- **Expresiones regulares para recortar**: un `}` dentro de un nombre o de un calificador corta
  el bloque en el lugar equivocado.
- **Evaluar el JavaScript** (Nashorn ya no está en el JDK; GraalJS es una dependencia): es mucho
  para un array literal.
- **Árbol de Jackson en lugar de DTO**: los DTO documentan los campos que se usan y fallan con un
  tipo inesperado en el mapeo, no en el uso.

## D5. Plan B: Playwright

**Decisión**: no se agrega. Queda documentado así:

- **Cómo**: `PlaywrightPageDownloader implements PageDownloader` abre la URL en un Chromium sin
  ventana, espera `DOMContentLoaded` y devuelve `page.content()`, que incluye los `<script>`
  inline. Los extractores, el ritmo y el reintento no cambian.
- **Costo**: la dependencia `com.microsoft.playwright:playwright` y unos 150 MB de navegadores
  descargados en la primera corrida. Requiere avisar y justificar (constitución, Stack).
- **Criterio para pasar a usarlo**, cualquiera de estos:
  1. dos corridas seguidas se cortan por bloqueo antes de procesar el 10% de sus páginas;
  2. en una corrida se reintenta más del 20% de las páginas (las líneas WARN del reintento lo
     muestran, D16);
  3. WhoScored deja de servir alguno de los tres bloques en el HTML (pasa a cargarlos por XHR).

**Justificación**: con el ritmo de D6, 49 de 50 páginas respondieron con datos y la que falló
respondió bien al reintento. Un navegador sin ventana no evita el bloqueo por IP, que es la
señal principal.

**Alternativas descartadas**: **Selenium o HtmlUnit**: más pesados y con el mismo efecto.

## D6. Ritmo, rechazos y corte

**Decisión**:

- **Una página a la vez**: hay a lo sumo una sincronización (D22) y cada corrida corre en un solo
  hilo. `WhoScoredClient` no es concurrente.
- **Pausa mínima** entre un pedido y el siguiente: `request-interval` (4 s).
  - El cliente anota con el `Clock` el momento en que terminó la respuesta anterior y, antes del
    pedido siguiente, espera lo que falta para cumplir el intervalo.
  - Ese estado sobrevive entre corridas, así una manual inmediatamente después de la cadena
    también respeta la pausa.
- **Rechazo**: un status distinto de 200, una falla de red o de timeout, o una página sin su
  bloque (D4).
  - El primero de una página se registra en WARN, con la ruta, el status y el tamaño, y se espera
    `retry-wait` (30 s). Esa espera cubre la pausa: el reintento no vuelve a esperar 4 s.
  - Si el reintento también falla, se lanza `SourceBlockedException` con la página
    (`BlockedPage`: tipo, id de WhoScored y ruta). La corrida entera se corta (D18) y no hace
    ningún otro pedido.
  - `matchCentreData: null` no es un rechazo (D4).
- **Las esperas son inyectables**: `WhoScoredSleeper` (`void sleep(Duration)`) con
  `WhoScoredThreadSleeper` en producción y `RecordingWhoScoredSleeper` en test. Si el hilo se
  interrumpe (la aplicación se está apagando), se restaura la marca y se lanza
  `SyncInterruptedException`, que termina la corrida sin informe: lo guardado ya está confirmado
  partido por partido (D18).
- **Las propiedades validan los mínimos del spec**: `request-interval` de al menos 4 s y
  `retry-wait` de al menos 30 s (FR-042 y FR-043). Ningún test espera de verdad: el adapter se
  prueba con el doble y los e2e lo mockean.

**Justificación**: es lo verificado el 2026-10-13. El bloqueo de WhoScored es por IP: dos fallas
seguidas de la misma página se toman como bloqueo y no se insiste.

**Sobre `Sleeper`**: WhoScored tiene su propia abstracción.

- `ThreadSleeper` traduce la interrupción a un `ExternalSourceException` con un motivo de
  Football-Data, y la espera de WhoScored termina distinto: corta la corrida, no una liga.
- Moverlo a un lugar común pedía una carpeta compartida fuera de `adapter/<proveedor>/` y tocar
  `RecordingSleeper` y los imports de `FootballDataAdapterTest`.
- Los nombres son distintos a propósito: dos clases `ThreadSleeper` darían el mismo nombre de
  bean y la aplicación no arrancaría.

**Alternativas descartadas**:

- **Pausa fija antes de cada pedido sin mirar el reloj**: sumaría el tiempo de escritura en la
  base a cada pausa (unos minutos más por corrida).
- **Contar fallas acumuladas en la corrida**: el spec pide cortar por dos fallas seguidas de la
  misma página, no por un total.
- **Reintentar más de una vez**: insistir empeora un bloqueo por IP.

## D7. Traducción en el adapter y reglas en el modelo

**Decisión**: el pedido proponía que el mapper calculara los minutos y resolviera las
asistencias. Se ajusta, con el mismo criterio que la 005 (D7): el adapter traduce el
vocabulario de WhoScored y el modelo aplica las reglas del spec.

| Regla | Dónde | Por qué |
|---|---|---|
| Sumar cada mapa minuto → valor de `stats` | `WhoScoredMapper` | Es el formato del proveedor. |
| Clave de `stats` → `Metric` (tabla de D8) | `WhoScoredMapper` | Vocabulario del proveedor. |
| Rating = el valor del mayor minuto de `ratings` | `WhoScoredMapper` | Formato del proveedor. |
| `cardType` 31, 32 y 33 → `CardKind` `YELLOW`, `SECOND_YELLOW` y `RED` | `WhoScoredMapper` | Vocabulario del proveedor. |
| Gol en contra (`isOwnGoal` o calificador `OwnGoal`) y asistidor (`relatedPlayerId` si el gol trae `Assisted`) | `WhoScoredMapper` | Traducción de los calificadores: el mapper no cuenta nada. |
| Minuto de entrada y de salida (expandidos) y minuto final (`expandedMaxMinute`) | `WhoScoredMapper` | Son datos de la fuente. |
| Estado terminado (`statusCode` 6) y fila terminada (`FT`, `AET` o `PEN`) | `WhoScoredMapper` | Vocabulario del proveedor. |
| Liga ↔ región y torneo | `WhoScoredTournament` | Ídem. |
| Quién entró a la cancha (FR-004) | `ReportedAppearance.entered()` | Negocio. |
| Minutos jugados, con expulsión (FR-005) | `ReportedMatchStats.minutesPlayed` | Negocio. |
| Goles y asistencias, sin goles en contra (FR-006) | `ReportedMatchStats` | Negocio. |
| Doble amarilla = una amarilla y una roja (FR-007) | `ReportedMatchStats` | Negocio. |
| Partidos pendientes y omisiones (FR-010 a FR-014) | `LeagueStatsSync` y `Match.needsStats()` | Negocio. |
| Cruce de equipos, partidos y jugadores (FR-015 a FR-022) | `TeamCrossing`, `LeagueStatsSync` y `PlayerCrossing` | Negocio. |
| Filtro de la temporada en curso por fechas (FR-003) | `LeagueStatsSync` | Negocio. |
| Qué se carga del snapshot y qué se saltea (FR-024 a FR-026) | `SnapshotLoad` | Negocio. |
| Combinar el snapshot vigente con la base (FR-030 y FR-031) | `StatsSnapshot.merge` | Negocio. |
| Cuándo una corrida es completa exitosa (FR-047) | `SyncReport.lastFullSync()` y `StatsSyncReport.lastFullSync()` | Negocio. |
| Qué corre al arrancar (FR-034 y FR-035) | `CatchUpPolicy` | Negocio. |

**Justificación**: los minutos, los goles en contra y la doble amarilla son reglas escritas en el
spec (FR-004 a FR-007). Si las calculara el mapper, el modelo recibiría números ya decididos y
ningún unitario del modelo las cubriría.

**Alternativa descartada**: **el modelo con eventos y calificadores**: metería el vocabulario de
WhoScored (`Assisted`, `VoidYellowCard`, `expandedMinute`) en el modelo.

## D8. Métricas

**Decisión** (verificado en el 1983600 contra la tabla del propio partido):

| Métrica del spec (FR-002) | `Metric` | Origen en `matchCentreData` |
|---|---|---|
| Si fue titular | — (campo `starter`) | `isFirstEleven` (falta en los suplentes: `false`) |
| Minutos jugados | — (campo `minutesPlayed`) | Minutos expandidos (D9) |
| Goles | `GOALS` | Eventos con `isGoal`, sin gol en contra (D10) |
| Asistencias | `ASSISTS` | `relatedPlayerId` de un gol con `Assisted` (D10) |
| Tiros | `SHOTS` | Suma de `shotsTotal` |
| Tiros al arco | `SHOTS_ON_TARGET` | Suma de `shotsOnTarget` |
| Pases clave | `KEY_PASSES` | Suma de `passesKey` |
| Pases | `PASSES` | Suma de `passesTotal` |
| Pases precisos | `ACCURATE_PASSES` | Suma de `passesAccurate` |
| Regates exitosos | `SUCCESSFUL_DRIBBLES` | Suma de `dribblesWon` |
| Quites | `TACKLES` | Suma de **`tackleSuccessful`** |
| Intercepciones | `INTERCEPTIONS` | Suma de `interceptions` |
| Despejes | `CLEARANCES` | Suma de `clearances` |
| Duelos aéreos ganados | `AERIALS_WON` | Suma de `aerialsWon` |
| Faltas cometidas | `FOULS_COMMITTED` | Suma de `foulsCommited` (así viene escrito) |
| Tarjetas amarillas | `YELLOW_CARDS` | Eventos `Card` con `cardType` 31 (D10) |
| Tarjetas rojas | `RED_CARDS` | Eventos `Card` con `cardType` 32 o 33 (D10) |
| Rating | — (campo `rating`) | Valor del mayor minuto de `ratings`, con sus decimales |
| Figura del partido | — (campo `manOfTheMatch`) | `isManOfTheMatch` |

- **Quites: `tackleSuccessful` y no `tacklesTotal`**. `tacklesTotal` incluye las veces que el
  jugador fue gambeteado (`tackleUnsuccesful`), y WhoScored muestra como "Tackles" los quites
  ganados (ver Lo verificado). Ejemplo: James Justin tiene 3 intentados, 2 ganados y 1 gambeteado.
- **Mapas por minuto**: solo traen los minutos con valor distinto de cero, así que una métrica
  que falta suma 0.
- **Rating**: la tabla del partido muestra el último valor del mapa (8,95 para Calvert-Lewin,
  7,76 para Muharemovic). Un jugador que entró y no tiene `ratings` queda con rating `null`. El
  valor se redondea a dos decimales (`HALF_UP`), que es como lo muestra la tabla; los ratings
  verificados ya traen dos.
- Las 23 claves restantes de `stats` (`touches`, `possession`, `totalSaves`, etc.) se ignoran
  (FR-002).

**Justificación**: cada métrica sale del campo que WhoScored usa para mostrarla. Así un valor
guardado se puede comparar a ojo con la página del partido (quickstart, sección 3).

**Alternativa descartada**: **`tacklesTotal` como propuso el pedido**: le sumaría los regates
sufridos y no coincidiría con lo que muestra el sitio.

## D9. Minutos jugados

**Decisión**: los minutos se miden en los **minutos expandidos** de WhoScored, que ya suman lo
agregado en el primer tiempo:

- inicio: 0 para un titular; `subbedInExpandedMinute` para un suplente;
- fin: el menor entre `subbedOutExpandedMinute`, el `expandedMinute` de su segunda amarilla o su
  roja, y `expandedMaxMinute`;
- minutos jugados = fin − inicio. Un suplente que no entró no tiene fila (FR-004).

Verificado:

- En el 1983600 el primer tiempo termina en el 47:25 y el segundo empieza en el minuto expandido
  48 (desfasaje de 3). Calvert-Lewin sale en el 77′ del reloj con
  `subbedOutExpandedMinute` 80, y un titular completo suma 97 (`expandedMaxMinute`).
- En el 1984103 el desfasaje es de 5: la segunda amarilla de Weah es en el 76′ del reloj y en el
  expandido 81, que es el fin de sus minutos.

Los escenarios 4 y 5 de la HU1 se reproducen con un partido cuyo primer tiempo termina en el
minuto 46 (desfasaje 2) y el segundo en el 94: un titular completo suma 96, el que sale en el 70′
del reloj (expandido 72) suma 72, su reemplazo 24 y el expulsado en el 60′ (expandido 62), 62.

**Justificación**: es la escala entera que usa la propia fuente y cumple FR-005: tiempo
transcurrido desde el inicio, con lo agregado en los dos tiempos. Un titular completo suma 90
más lo agregado, redondeado al minuto como lo muestra WhoScored.

**Alternativas descartadas**:

- **Calcular con los segundos de los eventos `End`**: da 96,5 en el 1983600, pero ningún dato de
  entrada o salida trae segundos en la misma escala, y mezclar las dos daría restas
  inconsistentes.
- **Reloj de 90 minutos**: el spec lo reemplazó (Clarifications).

## D10. Goles, asistencias y tarjetas

**Decisión**:

- **Gol**: evento con `isGoal: true`. Si trae `isOwnGoal: true` o el calificador `OwnGoal`, es en
  contra: no suma gol a su autor ni asistencia a nadie (FR-006). El evento del gol en contra trae
  el `teamId` del jugador que lo hizo, no el del equipo que lo festeja.
- **Asistencia**: un gol que no es en contra y trae el calificador `Assisted`. El asistidor es
  `relatedPlayerId`.
  - `IntentionalGoalAssist` no alcanza: el pase de Justin (1983600) solo tenía `ShotAssist`, y el
    de Muharemovic sí lo tenía. Los dos figuran en los `incidentEvents` del partido.
  - El evento relacionado puede ser **un remate, no un pase**: en el 1983559, Calvert-Lewin
    convierte el rebote de un tiro de Nmecha (`MissedShots`), y WhoScored le acredita la
    asistencia a Nmecha (su ícono trae `data-event-satisfier-assist`).
  - **`eventId` es único por equipo, no por partido**: en el 1983559 el 721 es a la vez una
    tarjeta de Brentford y el tiro de Nmecha. Para confirmar con `relatedEventId` hay que buscar
    en el mismo equipo. El mapper usa `relatedPlayerId`, que ya es la persona.
  - Un tiro con `Assisted` que no es gol (el palo de Willock en el 1983600) no suma nada.
  - En los cuatro partidos verificados la regla coincide con lo que acredita WhoScored: Justin y
    Muharemovic; ninguna en un gol de tiro libre directo (1983552); Lewis-Potter y Nmecha; Doué y
    Gouiri, y ninguna en el gol de Marquinhos (1984103).
- **Tarjetas**: eventos `Card` (17).
  - `cardType` 31 (`Yellow`) suma una amarilla, también la que trae `VoidYellowCard`.
  - 32 (`SecondYellow`) suma una roja. Así la doble amarilla es una amarilla y una roja (FR-007).
  - 33 (`Red`) suma una roja.
  - Otro valor se ignora y se registra en WARN.
- Una tarjeta a un suplente que no entró no se guarda: no hay fila (caso borde del spec).

**Justificación**: es la regla verificada y es la que usa la propia página para sus eventos
clave.

**Alternativas descartadas**:

- **Contar `IntentionalGoalAssist`**: le saca la asistencia a Justin.
- **Exigir que el evento relacionado sea un pase**: le saca la asistencia a Nmecha.

## D11. Descubrimiento de los identificadores

**Decisión**: por liga, y solo si hay partidos pendientes:

1. **Página de la liga** (1 pedido), solo si algún equipo de la temporada en curso no está
   cruzado. Da los ids y los nombres de WhoScored de los equipos de la fecha en curso.
2. **Página de fixtures** de cada equipo de WhoScored que todavía no corresponde a ningún equipo
   del catálogo, de a una, en el orden de la página de la liga. Después de cada una se intenta
   cruzar (D12). Los rivales que aparecen en las filas también se suman a los equipos conocidos,
   así un equipo que no juega la fecha en curso igual se descubre.
3. **Cruce de todos los partidos de la temporada**, no solo de los pendientes: cada página de
   fixtures trae la temporada entera del equipo, incluidos los partidos futuros. Cada partido de
   Football-Data con sus dos equipos cruzados se cruza con su fila (D13) y se guarda su id. Así,
   las corridas siguientes no piden páginas de fixtures: los partidos que terminan ya tienen su
   id.
4. Si quedan partidos pendientes sin id con sus dos equipos cruzados, se pide la página de
   fixtures del equipo cruzado que cubre más de ellos y todavía no se pidió en la corrida. Se
   repite hasta cubrirlos o quedarse sin equipos.
5. **Página de cada partido pendiente con id** (1 pedido por partido), del más antiguo al más
   reciente.

Cada página de fixtures se pide a lo sumo una vez por corrida y equipo. La elección de la
página siguiente la hace el modelo (`LeagueStatsSync.nextFixturesPage()`).

Volumen al 2026-10-13, sin snapshot: 5 páginas de liga, hasta 96 de fixtures y unas 330 de
partidos, es decir unas 430 páginas y unos 47 minutos con las pausas. Una semana normal: los
48 partidos de la fecha, sin páginas de liga ni de fixtures, en unos 5 minutos.

**Justificación**: la página de la liga es la única forma de conocer los ids de WhoScored de los
equipos al principio, y la de fixtures es la única que lista los ids de los partidos pasados.
Cruzar también los futuros convierte el costo de las páginas de fixtures en uno por temporada.

**Alternativas descartadas**:

- **Solo la página de la liga para cruzar equipos**: trae una sola fecha, y una fecha no alcanza
  para confirmar por calendario a un equipo con otro nombre (D12).
- **Las páginas de jugadores**: cargan las estadísticas por JavaScript y serían 2.649 páginas
  (fuera de alcance).
- **Pedir siempre la página de liga**: cinco pedidos por corrida que, con todos los equipos
  cruzados, no aportan nada.

## D12. Cruce de equipos

**Decisión**:

- **Nombre normalizado** (`NormalizedName`):
  1. descomposición NFD de `Normalizer` y eliminación de las marcas (`\p{M}`);
  2. reemplazo de las letras que NFD no descompone: `ø` → `o`, `æ` → `ae`, `œ` → `oe`,
     `ß` → `ss`, `đ` y `ð` → `d`, `ł` → `l`, `ı` → `i` y `þ` → `th`;
  3. minúsculas con `Locale.ROOT`;
  4. todo lo que no es letra o dígito pasa a espacio, y se colapsan los espacios;
  5. solo para equipos, se quitan las palabras sueltas `fc`, `afc`, `cf`, `sc`, `ac`, `as`,
     `ss`, `ssc`, `us`, `cd`, `ud`, `sd`, `rc`, `rcd`, `ca`, `sv`, `fsv`, `vfb`, `vfl`, `tsg`,
     `bv` y `1`.

  "Everton FC" y "Everton" quedan iguales; "Hull City AFC" y "Hull" no.
- **Calendario**: los partidos de liga de la temporada en curso de un equipo, con su fecha, su
  localía y su rival.
  - Del lado de WhoScored salen de las filas de fixtures conocidas en la corrida, filtradas por
    el torneo de la liga y por las fechas de la temporada de Football-Data, con un día de margen.
  - Del lado del catálogo, de los partidos guardados de esa temporada.
- **Coincidencia de calendario** entre un equipo de WhoScored W y uno del catálogo T:
  1. cada partido de W se alinea con el partido de T de la misma localía con a lo sumo un día de
     diferencia (la hora de las filas es la del sitio, D13);
  2. cada alineación propone un par de rivales (rival en WhoScored, rival en el catálogo);
  3. coinciden si se alinean al menos `MIN_ALIGNED_FIXTURES` (5) partidos, a lo sumo
     `MAX_UNALIGNED_FIXTURES` (2) de W quedan sin alinear (fechas reprogramadas), y los pares de
     rivales forman una correspondencia uno a uno que no contradice ningún cruce guardado.
- **Regla** (`TeamCrossing`), sobre los equipos sin cruzar de la liga:
  - si hay un único equipo del catálogo con el mismo nombre normalizado, se cruza con él solo si
    sus calendarios coinciden (FR-017); si no coinciden, queda sin cruzar con el motivo
    `NOT_CONFIRMED`;
  - si no hay ninguno con ese nombre, se cruza con el único equipo del catálogo cuyo calendario
    coincide (FR-017a); con más de uno, el motivo es `AMBIGUOUS`, y con ninguno, `NOT_FOUND`
    (FR-018);
  - se repite mientras aparezcan cruces nuevos, porque cada cruce habilita la verificación de
    los rivales de otros equipos.
- Un equipo cruzado guarda su id de WhoScored en su propia transacción y no se vuelve a cruzar
  (FR-016). El índice único sobre `teams.whoscored_id` impide dos equipos con el mismo id.

**Justificación**:

- Los nombres de WhoScored son cortos y sin tildes ("Atletico Madrid", "Deportivo Alaves", "Man
  Utd", "Inter"), y los de Football-Data, oficiales. El nombre solo resuelve una parte; el
  calendario de una temporada es prácticamente único.
- Con la página de un equipo se conocen sus 38 partidos y 2 de cada rival, así que después de
  tres o cuatro páginas se pueden cruzar también rivales cuya página no se pidió.
- La tolerancia de dos partidos cubre las fechas que una fuente actualizó y la otra todavía no.

**Alternativas descartadas**:

- **Similitud de nombres (Levenshtein, prefijos)**: cruza "Paris FC" con "Paris Saint-Germain"
  sin forma de confirmarlo.
- **Una tabla fija de equivalencias de nombres**: es un cruce manual (fuera de alcance) y hay que
  mantenerla cada temporada.
- **Exigir todos los partidos alineados**: una sola fecha reprogramada en una de las dos fuentes
  dejaría al equipo sin estadísticas hasta que se actualice (SC-003a).

## D13. Cruce de partidos y omisiones

**Decisión**:

- **Fila de WhoScored de la liga y la temporada**: torneo de la liga (posición 18 de
  `fixtureMatches`) y fecha dentro de la temporada de Football-Data con un día de margen. Se
  descartan las copas (FA Cup, League Cup, Coupe de France), los torneos europeos y la temporada
  anterior (FR-003).
- **Partido** (FR-015): un partido de Football-Data con sus dos equipos cruzados corresponde a la
  única fila con ese local y ese visitante de WhoScored. No se mira la fecha: en una liga esos
  dos equipos se enfrentan una sola vez con esa localía.
- **Partido pendiente**: `FINISHED`, sin estadísticas y de la temporada en curso de su liga, que
  es la de fecha de inicio más reciente (`CurrentSeasons`). Los programados, postergados,
  suspendidos, cancelados y adjudicados no son pendientes (FR-011).
- **Omisiones** (no cuentan como falla y el partido sigue pendiente):

  | Motivo | Cuándo |
  |---|---|
  | `UNCROSSED_TEAM` | El local o el visitante no quedó cruzado (FR-018). |
  | `NOT_IN_SOURCE` | Sus dos equipos están cruzados, pero no hay fila con ese local y ese visitante, o la página trae otros equipos (FR-014). |
  | `NOT_FINISHED_IN_SOURCE` | La página trae `matchCentreData: null` o un `statusCode` distinto de 6 (FR-014). |
  | `INVALID_IN_SOURCE` | La página trae un valor imposible: un rating fuera de 0 a 10 o una métrica negativa (D18). |
  | `WRITE_FAILED` | La base rechazó la escritura del partido (D18). |

- Un partido procesado no se vuelve a pedir: deja de ser pendiente (FR-012).

**Justificación**: la localía y los dos equipos identifican al partido de liga sin depender de
la fecha, que puede estar en otra zona horaria o reprogramada.

**Alternativa descartada**: **filtrar por el `seasonId` de WhoScored**: solo se conoce si se pidió
la página de la liga en la corrida, y cambia cada temporada.

## D14. Cruce de jugadores

**Decisión**:

- `CatalogPlayers` es un índice en memoria de todos los jugadores del catálogo, activos o
  inactivos, con su equipo. Se carga una vez por corrida (unas 2.650 filas) y se indexa por id
  de WhoScored, por equipo y nombre normalizado, y por nombre normalizado.
- `PlayerCrossing`, para cada jugador de WhoScored que entró a la cancha:
  1. si algún jugador del catálogo ya tiene su id de WhoScored, es ese, sin mirar nombres ni
     equipo (FR-020);
  2. si no, los candidatos son los jugadores del equipo del catálogo cruzado con ese lado del
     partido que tienen el mismo nombre normalizado (FR-019). Con uno, es ese. Con más de uno,
     desempata la edad: la que surge de la fecha de nacimiento el día del partido tiene que ser
     la que informa WhoScored. Si sigue ambiguo, motivo `AMBIGUOUS`;
  3. si no hay candidatos en el equipo, se busca en todo el catálogo por nombre normalizado y
     misma edad, y se cruza solo si hay uno único (FR-019a). Si no hay ninguno, motivo
     `NOT_FOUND`; si hay más de uno, `AMBIGUOUS`;
  4. nunca es candidato un jugador que ya tiene otro id de WhoScored, ni uno ya elegido para otro
     jugador del mismo partido (FR-022).
- Un jugador sin fecha de nacimiento no participa de los desempates por edad.
- Un jugador sin cruzar se saltea y va al informe con su nombre, su equipo y el partido (FR-021).
  Nunca se crea un jugador.
- El índice se actualiza con los cruces nuevos recién cuando el partido se confirmó en la base:
  si la escritura falla, no queda un cruce que no existe.

**Justificación**: es la regla del spec. Cargar el catálogo entero es más barato que una consulta
por nombre, y la comparación por nombre normalizado no se puede expresar en JPQL portable.

**Alternativa descartada**: **una columna con el nombre normalizado**: duplica un dato derivado y
obliga a recalcularlo en cada sincronización con Football-Data.

## D15. Modelo del rendimiento

**Decisión**:

- `PlayerMatchStats` (`modelo/performance/`): jugador, partido, si fue titular, minutos
  jugados, `MetricCounts` con las 15 métricas que se cuentan, rating (`BigDecimal`, opcional) y
  si fue la figura. Son las 19 métricas de FR-002.
- `Metric` es un enum con las 15 métricas que se cuentan, en el orden del contrato del snapshot.
  `MetricCounts` envuelve un `EnumMap<Metric, Integer>` sin negativos y devuelve 0 para una
  métrica que falta.
- Lo que informa WhoScored de un partido también es modelo: `ReportedMatchStats`,
  `ReportedAppearance`, `ReportedGoal` y `ReportedCard` (D7).
- Invariantes, con `CatalogInvariantException`: jugador y partido obligatorios, minutos y
  conteos no negativos, y rating entre 0 y 10 con a lo sumo dos decimales.

**Justificación**: un enum con los conteos evita un record de 22 componentes, recorre las métricas
en un único orden (el del snapshot, el de la tabla y el del mapper) y no duplica bloques.

**Alternativas descartadas**:

- **Un campo por métrica en el record**: 22 componentes y el mismo bloque de código repetido en
  el mapper de la base, el del snapshot y el del adapter.
- **Guardar las 42 métricas de WhoScored**: FR-002 lo prohíbe.

## D16. Corrida e informe de WhoScored

**Decisión**:

- `StatsSyncRun` es la corrida en curso, mutable como `SyncRun`, y la usa un solo hilo. Se crea
  con `full(origin, inicio)` o `singleLeague(league, inicio)`, que siempre es `MANUAL`.
- `StatsSyncReport` lleva el tipo, el origen, el inicio y el fin, el estado del snapshot
  (`LOADED`, `MISSING` o `UNREADABLE`), el corte si lo hubo, y una entrada por liga pedida en el
  orden del enum (FR-048).
- `LeagueStatsResult` lleva el estado de la liga (`PROCESSED`, `CUT` o `NOT_REACHED`), los
  partidos procesados desde el snapshot y por scraping, los omitidos con su motivo, los equipos y
  jugadores sin cruzar, y las entradas del snapshot salteadas, contadas por motivo.
- `RunCut` dice en qué página se cortó (`LEAGUE`, `TEAM_FIXTURES` o `MATCH`, con su id de
  WhoScored), en qué liga y, si era la de un partido, cuál.
- `StatsSyncReport.lastFullSync()` devuelve la última completa exitosa solo si la corrida fue
  `FULL` y no se cortó (FR-047). Los omitidos no le quitan el éxito.
- `StatsSyncReportLogger` lo registra con SLF4J: una línea por liga en INFO (en WARN la liga
  cortada), las líneas de detalle que tengan algo (omitidos, sin cruzar y entradas salteadas) y
  una línea final con la duración, los totales y, si hubo, el corte.
- `WhoScoredClient` registra en INFO cada pedido, con su ruta, su status, su tamaño y la pausa
  previa (`Pedido a WhoScored: {ruta} ({status}, {n} KB; pausa previa de {s} s).`), y en WARN
  cada rechazo y si se reintenta. Esas líneas alimentan el criterio del plan B (D5), las
  verificaciones del quickstart (secciones 3, 4.1 y 5.3) y la medición de SC-004.
- El informe no se guarda (FR-049).

**Justificación**: separa lo que el spec pide por liga de lo que pasa en la corrida entera (el
corte y el snapshot) y deja la decisión del éxito en el modelo, como `inactivationSkipReason` en
la 005.

**Alternativas descartadas**:

- **Listar una por una las entradas salteadas del snapshot**: en una base vacía serían todas, en
  cada corrida. FR-048 pide cuántas y por qué motivo.
- **Contar los reintentos en el informe**: el registro ya tiene una línea por rechazo, y el
  contador obligaba a compartir estado entre el cliente y la corrida.

## D17. Persistencia

**Decisión** (el detalle está en [data-model.md](./data-model.md)):

- **Columnas nuevas, todas compatibles con `ddl-auto: update` sobre las tablas de la 005 con
  datos**:
  - `teams.whoscored_id`, `players.whoscored_id` y `matches.whoscored_id`: `VARCHAR(32)` nulos,
    con índices únicos con nombre (`ux_teams_whoscored_id`, `ux_players_whoscored_id` y
    `ux_matches_whoscored_id`). H2 y PostgreSQL admiten varios `NULL` en un índice único.
  - `matches.stats_loaded`: `BOOLEAN NOT NULL` con `@ColumnDefault("false")`, así las filas
    existentes quedan en `false` al agregar la columna.
- **Tablas nuevas**:
  - `player_match_stats`, con una fila por jugador y partido (`ux_player_match_stats_player_match`
    único sobre `player_id` y `match_id`);
  - `last_full_syncs`, con una fila por fuente (`source` es la clave primaria).
- **La base local no se borra**. Es lo que pide el escenario 9 de la HU3: una base de la 005 con
  datos se pone al día en el primer arranque.
- **Prueba del default**: `MatchStatsRepositoryIT` inserta con `JdbcTemplate` una fila de
  `matches` sin `stats_loaded`, como una de la 005, y la lee con el repository. Es el único SQL
  nativo de la feature: un `INSERT` estándar, sin funciones de H2, solo en ese test. El equipo lo
  aprobó el 2026-10-14, como pide el principio VI. El código de producción no usa SQL nativo.
- **Marca de procesado** `stats_loaded` y no "tiene filas": un partido en el que no se pudo cruzar
  a nadie igual queda procesado (FR-012 y FR-013).
- **Consultas** (solo derived queries y `@EntityGraph`):
  - los partidos de una temporada, con su temporada y sus equipos;
  - todos los jugadores con su equipo, para `CatalogPlayers`;
  - todas las temporadas, para `CurrentSeasons`;
  - para el snapshot: los equipos y los partidos con id de WhoScored, y las estadísticas con su
    jugador y su partido.
- **Los mappers y `updateFrom` conservan las columnas nuevas**: `Team.updateFrom`,
  `Player.updateFrom`, `Player.deactivate` y `Match.updateFrom` copian el id de WhoScored y la
  marca. Si no, la sincronización semanal con Football-Data las borraría (ver Relevamiento).
- **Enums como texto**: `last_full_syncs.source` es `VARCHAR(20)` desde un `String`, como
  `seasons.league` (005, D16).
- `rating` es `DECIMAL(4,2)`.

**Justificación**: los cruces son atributos de las entidades (Entidades clave del spec), la marca
de procesado no depende de cuántos jugadores se cruzaron, y todo se agrega sin tocar las filas que
ya existen.

**Alternativas descartadas**:

- **Columnas `NOT NULL` sin default**: fallan con `ddl-auto: update` sobre filas existentes y
  obligarían a borrar la base local otra vez.
- **Tablas aparte para los cruces** (`whoscored_crossings`): el spec define los ids de WhoScored
  como parte de equipo, jugador y partido, y el modelo los necesita en el mismo objeto.
- **Guardar el momento en que se procesó cada partido**: FR-047 prohíbe guardar otros datos de
  las corridas.

## D18. Transacciones, corte y reanudación

**Decisión**:

- **Dos fases por corrida**:
  1. el snapshot, para todas las ligas pedidas;
  2. el descubrimiento y el scraping, liga por liga.

  Así un bloqueo desde el primer pedido igual deja cargado todo lo que trae el snapshot (HU2,
  escenario 3).
- **Un partido es una transacción** (`StatsWriteService.saveMatchStats`): sus filas de
  estadísticas, los ids de WhoScored de los jugadores cruzados en ese partido y la marca del
  partido. O se guarda todo o no se guarda nada (FR-013).
- **Los cruces de equipos y de partidos de una liga** se guardan en su propia transacción apenas
  se deciden, antes del scraping. Si la corrida se corta, la siguiente no vuelve a pedir esas
  páginas.
- `StatsWriteService` es otro bean, como `SyncWriteService`, para no depender de la
  auto-invocación. El orquestador no es transaccional.
- **Corte** (`SourceBlockedException`): se captura en `StatsSyncService`, se registra en la
  corrida y no se hace ningún otro pedido. La liga en la que ocurre queda `CUT` y las que faltan,
  `NOT_REACHED`. Lo ya confirmado se conserva (FR-044 y FR-045).
- **Falla de la base en un partido** (`DataAccessException`): ese partido queda omitido con
  `WRITE_FAILED` y la corrida sigue. Si lo que falla es una carga del snapshot, queda un WARN y el
  partido sigue pendiente: se scrapea en la fase 2 de la misma corrida, sin contarse como
  `WRITE_FAILED`.
- **Valor imposible de la fuente** (`CatalogInvariantException` al pedir y preparar un partido
  scrapeado: un rating fuera de 0 a 10 o una métrica negativa): ese partido queda omitido con
  `INVALID_IN_SOURCE`, con un WARN con el motivo, y la corrida sigue. Se captura solo ahí: en
  cualquier otro lugar, un invariante roto es un error de programación y aborta la corrida.
- **Aplicación detenida**: lo confirmado queda y el partido que se estaba escribiendo se revierte
  solo, porque la transacción no llegó a confirmarse. La corrida siguiente lo encuentra pendiente
  (HU4, escenario 5).
- **Errores de programación**: no se capturan. Abortan la corrida, y el candado se libera en el
  `finally`.

**Justificación**: la puesta al día de la primera vez son unas 430 páginas y unos 47 minutos. Una
transacción por partido mantiene cada escritura chica y hace la corrida reanudable.

**Alternativas descartadas**:

- **Una transacción por liga**: un corte a mitad de la liga perdería todo lo scrapeado de esa
  liga.
- **`TransactionTemplate` en el orquestador**: mismo motivo que en la 005 (D8).

## D19. Snapshot: formato y ubicación

**Decisión** (contrato completo en [contracts/snapshot-format.md](./contracts/snapshot-format.md)):

- **JSON Lines en texto plano**, UTF-8 con fin de línea LF:
  `backend/snapshot/whoscored-stats.jsonl`.
- Una primera línea de encabezado con el formato y la versión (`1`), y después una entrada por
  línea, de tres tipos:
  - `team`: liga, id de Football-Data e id de WhoScored del equipo;
  - `match`: liga, id de Football-Data e id de WhoScored del partido;
  - `stats`: liga, id de Football-Data del partido y una fila por jugador, que es un array con
    el id de Football-Data y el de WhoScored del jugador (`null` si no está cruzado) y las 19
    métricas en orden fijo.
- **Orden canónico**: encabezado, equipos, partidos y estadísticas; dentro de cada tipo, por liga
  en el orden del enum y por id de Football-Data (primero por largo y después alfabético, que en
  ids numéricos es el orden numérico); las filas de jugadores, por id de Football-Data del
  jugador.
- Las claves de cada objeto van en orden fijo (`@JsonPropertyOrder`) y sin espacios. El rating se
  escribe con dos decimales (`7.65`, `6.00`).
- **Tamaño**: unos 1,7 KB por partido. Al 2026-10-13 (unos 330 partidos) son unos 600 KB, y una
  temporada completa (1.752 partidos), unos 3 MB.
- `.gitattributes` suma `backend/snapshot/*.jsonl text eol=lf`, así el archivo queda idéntico
  byte a byte en Windows.

**Justificación**:

- En texto plano, una fecha nueva son unas 48 líneas agregadas en el diff (FR-030). Dos
  desarrolladores que agregan la misma fecha producen las mismas líneas.
- JSON Lines se lee y se escribe con Jackson, sin un formato propio que escapar.
- Las entradas `match` guardan también los partidos futuros cruzados (D11), así una base nueva no
  pide páginas de fixtures.
- La liga en cada entrada permite que una sincronización de una sola liga cargue solo lo suyo y
  que el informe cuente las salteadas por liga.

**Alternativas descartadas**:

- **Comprimido (gzip)**: es binario. Cada regeneración reemplaza el archivo entero, el diff no se
  puede leer, y el repositorio crece con el tamaño completo en cada commit.
- **Un JSON con claves por jugador**: unas cinco veces más grande.
- **TSV**: obliga a escapar a mano los separadores y no se describe solo.
- **Dentro de `src/main/resources`**: la exportación tiene que escribir el archivo fuente, y la
  carga leería la copia de la última compilación.

## D20. Snapshot: carga

**Decisión** (`StatsSnapshotService.read` y `SnapshotLoad`):

- **Se lee entero antes de usarlo**. Si el archivo falta, el estado es `MISSING`. Si una línea no
  se puede leer, si el encabezado no es el esperado o si la versión no es `1`, el estado es
  `UNREADABLE` y **se ignora entero**. En los dos casos queda un WARN y se scrapea todo lo
  pendiente (FR-027).
- **Por liga, en la fase 1** (D18), se aplican solo las entradas de las ligas pedidas:
  - `team`: si el equipo no está en el catálogo, se saltea (`TEAM_NOT_IN_CATALOG`); si está pero
    no juega la temporada en curso, se ignora sin contarlo; si no tiene id de WhoScored y ese id
    está libre, se cruza; si ya tiene el mismo, no hace nada; si tiene otro, o el id ya es de otro
    equipo, gana la base (`CONFLICTING_CROSS`).
  - `match`: lo mismo con el partido (`MATCH_NOT_IN_CATALOG` o `CONFLICTING_CROSS`); uno de otra
    temporada se ignora sin contarlo.
  - `stats`, solo para partidos pendientes (FR-024):
    - si el partido no está en el catálogo, se saltea (`MATCH_NOT_IN_CATALOG`);
    - si ya tiene estadísticas, se ignora sin contarlo: conserva las suyas (HU2, escenario 6);
    - si no está terminado, se saltea (`MATCH_NOT_FINISHED`) y se carga en una corrida
      posterior;
    - si es de otra temporada, se ignora sin contarlo.

    Cada fila de un jugador que no está en el catálogo se saltea (`PLAYER_NOT_IN_CATALOG`) y el
    resto del partido se carga (FR-026). El id de WhoScored del jugador se guarda con las mismas
    reglas que `team` (`CONFLICTING_CROSS` si choca), pero sus estadísticas se cargan igual,
    porque se ubican por el id de Football-Data. Una fila con el id de WhoScored en `null` se
    carga sin cruzar a nadie.
- **Qué está en el catálogo**: `StatsSnapshotService.references` busca por id de Football-Data,
  con los `findAllByExternalIds` de la 005, los equipos y partidos que nombra el snapshot de la
  liga, de cualquier temporada (`CatalogReferences`). Así se distingue lo que no está en el
  catálogo, que se cuenta, de lo que es de otra temporada o de un equipo que dejó la liga, que no.
- Cada partido cargado es una transacción, como uno scrapeado (D18), y cuenta como "procesado
  desde el snapshot".
- La carga no hace ningún pedido a WhoScored (FR-025).

**Justificación**: es lo que piden FR-024 a FR-027 y HU2. Que la base gane ante un conflicto
mantiene FR-022 sin borrar cruces confirmados.

**Alternativas descartadas**:

- **Leer y aplicar línea por línea**: una línea rota a mitad del archivo dejaría cargada solo una
  parte, y FR-027 pide ignorarlo entero.
- **Que el snapshot gane ante un conflicto**: pisaría un cruce confirmado en la base y podría dejar
  dos jugadores con el mismo id de WhoScored.
- **Cargar el snapshot liga por liga, intercalado con el scraping**: un bloqueo en la primera liga
  dejaría sin cargar lo que el snapshot trae de las demás (HU2, escenario 3).

## D21. Snapshot: exportación

**Decisión** (el equipo eligió la tarea de Gradle el 2026-10-13):

- **Comando**: `./gradlew exportStatsSnapshot`, desde `backend/`. `build.gradle` suma una tarea
  `JavaExec` con `classpath = sourceSets.main.runtimeClasspath`,
  `mainClass = 'ar.edu.unq.desapp.futbolmarket.StatsSnapshotExport'` y `workingDir = projectDir`.
  No suma dependencias.
- **`StatsSnapshotExport`** levanta Spring con `SpringApplicationBuilder(FutbolMarketApplication.class)`:
  - perfil `local`, sin servidor web (`WebApplicationType.NONE`);
  - con `futbolmarket.football-data.sync.on-startup=false` y `futbolmarket.football-data.sync.cron=-`,
    así no corre ninguna sincronización;
  - llama a `StatsSnapshotService.export()` y cierra el contexto.

  No es un recurso de la aplicación: no hay endpoint (FR-028).
- **`export()`**:
  1. lee el snapshot vigente; si existe y no se puede leer, **no lo pisa**: `export()` devuelve
     `false` y `StatsSnapshotExport` termina con código 1;
  2. arma desde la base las entradas `team` (equipos con id de WhoScored), `match` (partidos con
     id de WhoScored) y `stats` (partidos con estadísticas, con sus filas);
  3. `StatsSnapshot.merge` conserva sin cambios cada entrada vigente, también las que la base no
     tiene, y suma las de la base cuya clave (tipo e id de Football-Data) no está (FR-031);
  4. escribe en orden canónico a un archivo temporal en la misma carpeta y lo mueve sobre el
     vigente, así nunca queda un archivo a medio escribir.
- **Solo lee** equipos, partidos, temporadas, jugadores y estadísticas: nunca usuarios,
  contraseñas, claves ni operaciones (FR-029).
- **Determinismo** (FR-030): el orden canónico y el formato fijo hacen que dos exportaciones sin
  cambios en la base den el mismo archivo, byte a byte.

**Justificación**: una tarea de Gradle exporta y termina sola, sin dejar la aplicación levantada
ni sincronizar en el medio.

**Alternativas descartadas**:

- **Modo de arranque de `bootRun`**: la aplicación quedaba levantada después de exportar y había
  que apagar a mano la puesta al día.
- **Un endpoint**: FR-028 lo prohíbe.
- **Que la base gane ante una entrada vigente distinta**: rompería FR-031 y los diffs entre los
  dos desarrolladores.

## D22. Orquestación: candado común, cadena y puesta al día

**Decisión**:

- **`SyncLock`** (`service/sync/`) es el candado único de las dos fuentes. Reemplaza al
  `Semaphore` propio de `SyncService` (FR-041 y FR-052):
  - `runExclusively(Supplier<T>)` hace `tryAcquire`, lanza `SyncInProgressException` (409) si
    está tomado y libera en un `finally`;
  - no espera ni encola, como en la 005 (D11).
- **`SyncService`** (Football-Data) mantiene sus firmas públicas.
  - `synchronizeAll` y `synchronizeLeague` revisan el token (503) y después toman el candado
    (409), en el mismo orden de la 005.
  - Suma `runFull(origin)`, de visibilidad de paquete, que usa la cadena cuando ya tiene el
    candado.
- **`StatsSyncService`** (WhoScored) tiene el mismo esquema: `synchronizeAll` y
  `synchronizeLeague` para el disparo manual, y `runFull(origin)` para la cadena. No revisa
  ninguna credencial (FR-040).
- **`SyncCoordinator`** encadena las dos fuentes con el candado tomado de punta a punta, así la
  cadena cuenta como una sola sincronización (FR-041):
  - el paso de Football-Data se saltea con un INFO si no hay token;
  - el paso de WhoScored corre siempre, aunque Football-Data haya fallado en alguna liga o en
    todas (FR-036);
  - un error de programación no se captura y aborta la cadena.
- **Corrida semanal**: `SyncScheduler.runWeeklySync()` conserva su `@Scheduled`, llama a
  `coordinator.runWeeklyChain()` y, si hay otra en curso, deja el WARN de siempre (FR-033).
- **Puesta al día al arrancar**: `StartupSync` conserva la advertencia exacta sin token y el
  interruptor `on-startup`, y llama a `coordinator.catchUpOnStartup()`.
  - El coordinador lee las dos últimas completas y le pide la decisión a `CatchUpPolicy` con el
    `Clock` y `futbolmarket.sync.catch-up-after` (7 días):

    | Última completa de Football-Data | Última completa de WhoScored | Decisión |
    |---|---|---|
    | Ninguna o de más de 7 días | Cualquiera | `CHAIN`: las dos, en ese orden (FR-034) |
    | De 7 días o menos | Ninguna o de más de 7 días | `WHOSCORED_ONLY` (FR-035) |
    | De 7 días o menos | De 7 días o menos | `NONE` |

  - Una completa con exactamente 7 días todavía está al día.
- **Sin regla extra para los cortes** (decisión del equipo): un partido que quedó pendiente
  después de un corte se recupera en el primer arranque en que la última completa de WhoScored
  tenga más de 7 días, en la cadena del lunes o con un disparo manual, como dice el spec.
- **El catálogo vacío no es una condición aparte**: una base vacía no tiene ninguna completa
  registrada, así que corre la cadena.
- **`SyncOrigin`** queda en `WEEKLY`, `CATCH_UP` y `MANUAL`. `STARTUP` se renombra a `CATCH_UP`
  (decisión del equipo; FR-053).
- **Los disparos manuales no se encadenan** (FR-038): cada controller llama solo a su servicio.

**Justificación**: un candado que se toma por separado en cada fuente dejaría un hueco entre las
dos sincronizaciones de la cadena, y un disparo manual podría meterse ahí.

**Alternativas descartadas**:

- **`ReentrantLock` para que la cadena reentre al servicio**: el permiso quedaría atado al hilo y
  la reentrada sería implícita (005, D11).
- **Encadenar con un evento al terminar Football-Data**: el candado se liberaría entre los dos
  pasos.
- **Un scheduler aparte para WhoScored a las 04:00**: chocaría con el candado de Football-Data y
  no encadenaría nada.

## D23. Última sincronización completa exitosa

**Decisión**:

- `LastFullSync(SyncSource source, Instant finishedAt)`, con `SyncSource` `FOOTBALL_DATA` o
  `WHOSCORED`, se guarda en `last_full_syncs` con una fila por fuente. Es lo único que se guarda
  de las corridas (FR-047 y FR-054).
- **Football-Data**: `SyncReport.lastFullSync()` está presente si el tipo es `FULL` y las cinco
  ligas quedaron `SUCCEEDED`. `SyncService` la guarda al terminar, también con un disparo manual
  (HU3, escenario 12).
- **WhoScored**: `StatsSyncReport.lastFullSync()` está presente si el tipo es `FULL` y no hubo
  corte.
- **Una de una sola liga nunca cuenta** (HU3, escenarios 13 y 14).
- Una base de la 005 no tiene la tabla: Hibernate la crea vacía, así que se considera que nunca
  hubo una (HU3, escenario 9).
- El momento es el fin de la corrida, que sale del `Clock`.

**Justificación**: el spec define el éxito por fuente, y que el modelo devuelva la última completa
o nada evita un `if` de negocio en los servicios.

**Alternativas descartadas**:

- **Una fila por corrida (historial)**: FR-047 y FR-054 permiten guardar solo el momento de la
  última completa exitosa.
- **Deducirlo de los datos** (por ejemplo, el partido más reciente con estadísticas): no distingue
  una corrida completa de una de una liga ni de una cortada.

## D24. Disparo manual y seguridad

**Decisión**:

- **Rutas**:
  - `POST /matches/stats/sync`: las cinco ligas;
  - `POST /matches/stats/sync?league=PREMIER`: una sola.

  Siempre responde 200 con el informe, también cuando WhoScored bloqueó: el corte va en `cut`
  (FR-039).
- **Ubicación**: `controller/sync/StatsSyncController`. La ruta va bajo `/matches` porque las
  estadísticas son por partido. No se agrega ningún `GET`: las estadísticas no se exponen
  (FR-009).
- **Seguridad**: `SecurityConfig` suma
  `.requestMatchers(pathPattern(HttpMethod.POST, "/matches/stats/sync")).hasRole(Role.ADMIN.name())`
  junto a la de `/players/sync`. Sin credencial responde 401 y un `USER` recibe 403, los dos
  antes del controller y sin consultar WhoScored (FR-037 y SC-017).
- **`league`**: se convierte con `League.fromName` antes de llamar al servicio. Un valor que no
  es una de las cinco responde 400 con el mensaje de la 005, sin consultar WhoScored.
- **Errores**: 409 con `Ya hay una sincronización en curso.` si hay cualquier sincronización en
  curso (FR-041). No hay 503: WhoScored no necesita credencial. No se agrega ningún status nuevo
  al advice.
- **Swagger**: `@Tag(name = "Sincronización")`, `@Operation`, un `@ApiResponse` por código
  (200, 400, 401, 403 y 409) y los dos `@SecurityRequirement` (`bearerAuth` y `apiKeyAuth`).
- **`POST /players/sync`** no cambia de comportamiento. Solo cambia el texto de su 409 en
  Swagger: ahora también responde 409 si corre WhoScored o la cadena.
- **Respuesta**: `StatsSyncReportResponse`, con records anidados y un `from(StatsSyncReport)`
  estático, como `SyncReportResponse`.

**Justificación**: un 200 con el corte cumple FR-039 y separa "la corrida terminó, cortada" de un
error del pedido.

**Alternativas descartadas**:

- **Un 503 o un status propio para el bloqueo**: FR-039 pide devolver el informe también cuando
  se cortó.
- **202 y segundo plano**: FR-039 pide esperar.
- **`/players/stats/sync`**: las estadísticas no son del catálogo de jugadores.

## D25. Propiedades

**Decisión** (contrato en [contracts/configuration.md](./contracts/configuration.md)):

- `WhoScoredProperties` (`futbolmarket.whoscored`, `@Validated`): `baseUrl`, `userAgent`,
  `accept`, `acceptLanguage`, `connectTimeout`, `readTimeout`, `requestInterval` (al menos 4 s),
  `retryWait` (al menos 30 s) y `snapshot.path`.
- `SyncProperties` (`futbolmarket.sync`): `catchUpAfter` (positiva, 7 días).
- La puesta al día se prende y se apaga con la propiedad existente
  `futbolmarket.football-data.sync.on-startup`. La corrida semanal sigue usando el cron y la zona
  existentes. `FootballDataProperties` no cambia.
- **Perfil test**:
  - `base-url: http://whoscored.invalid`, que nunca resuelve (RFC 2606);
  - `snapshot.path: build/test-snapshot/absent.jsonl`, que no existe;
  - `on-startup: false`, que ya estaba.

  Los tests que necesitan un snapshot lo apuntan a un `@TempDir`.

**Justificación**: los valores verificados quedan configurables sin tocar código, y los mínimos
del spec no se pueden bajar por error.

**Alternativas descartadas**:

- **Una propiedad `on-startup` nueva para la puesta al día**: la existente ya apaga el arranque en
  el perfil test, y dos interruptores para lo mismo se contradirían.
- **Agregar el umbral a `FootballDataProperties.Sync`**: cambia la aridad de un record que
  construyen tres tests de la 005 fuera del aval.

## D26. Estrategia de tests

**Decisión**:

- **Nunca se consulta WhoScored**:
  - el adapter se prueba con `MockRestServiceServer`, `RecordingWhoScoredSleeper` y un `Clock`
    fijo;
  - el servicio, con el adapter mockeado;
  - los e2e, con `@MockitoBean WhoScoredAdapter`.

  El perfil test usa una URL `.invalid`.
- **Fixtures** en `src/test/resources/whoscored/`, recortadas de las páginas guardadas el
  2026-10-13; si no están, armadas con los fragmentos reales de
  [contracts/whoscored-pages.md](./contracts/whoscored-pages.md) y los valores de
  [quickstart.md](./quickstart.md), sección 7, con los marcadores, las claves y los formatos
  exactos del contrato. Nunca se bajan de nuevo para los tests:
  - `match-1983600.html`: el bloque del ejemplo del pedido, con el gol en contra, la asistencia
    de un pase con solo `ShotAssist`, un suplente que entra, uno que no entra y un titular que
    sale;
  - `match-second-yellow.html`: una segunda amarilla con `VoidYellowCard` y un gol asistido por
    un remate;
  - `match-unavailable.html`: el bloque con `matchCentreData: null`;
  - `fixtures-everton.html`: un fragmento de `fixtureMatches` con filas jugadas, sin jugar
    (posiciones vacías), de copa y de la temporada anterior, y un nombre con apóstrofo escapado;
  - `league-premier.html`: el bloque `tournamentfixtures`;
  - `rejected-403.html`: la página de rechazo;
  - `snapshot-sample.jsonl`: un snapshot chico.
- **Niveles**:

| Nivel | Clases |
|---|---|
| Unitarios de modelo | `NormalizedNameTest`, `TeamCrossingTest`, `PlayerCrossingTest`, `CatalogPlayersTest`, `ReportedMatchStatsTest` (minutos, goles, asistencias, tarjetas y quién entró), `PlayerMatchStatsTest`, `MetricCountsTest`, `LeagueStatsSyncTest`, `SnapshotLoadTest`, `StatsSnapshotTest` (orden y combinación), `StatsSyncRunTest`, `CatchUpPolicyTest`, `CurrentSeasonsTest`, `SyncReportLastFullSyncTest`, `TeamWhoScoredTest`, `PlayerWhoScoredTest` y `MatchStatsStateTest` |
| Adapter | `ScriptBlockReaderTest`, `MatchCentreExtractorTest`, `FixtureMatchesExtractorTest`, `TournamentFixturesExtractorTest`, `WhoScoredMapperTest` (sin HTTP), `WhoScoredThreadSleeperTest` (la interrupción) y `WhoScoredAdapterTest` (`MockRestServiceServer`: headers, el INFO de cada pedido, pausa de 4 s, reintento a los 30 s, corte, 403, página sin bloque, timeout y `matchCentreData: null`) |
| Configuración | `WhoScoredPropertiesTest`, `SyncPropertiesTest` y `WhoScoredClientConfigTest` (redirecciones y timeout del `HttpClient`, sin red) |
| Unitarios de servicio | `StatsSyncServiceTest`, `SyncCoordinatorTest`, `SyncLockTest`, `StatsSyncReportLoggerTest`, `StatsSnapshotServiceTest` (con `@TempDir`), `SyncServiceLastFullSyncTest` (la última completa de Football-Data, en una clase nueva para no tocar `SyncServiceTest` más de lo avalado), y los modificados `StartupSyncTest`, `SyncSchedulerTest` y `SyncServiceTest` |
| Persistencia sin Spring | `StatsSnapshotRepositoryTest`, con `@TempDir`: lectura entera, archivo faltante o ilegible, y escritura con el temporal y el movimiento |
| Integración contra H2 | `TeamCrossRepositoryIT`, `PlayerCrossRepositoryIT`, `MatchStatsRepositoryIT`, `PlayerMatchStatsRepositoryIT`, `LastFullSyncRepositoryIT`, `StatsWriteServiceIT` (incluye que una sincronización de Football-Data posterior conserva los ids de WhoScored y la marca) y `SyncChainIT` (la cadena con los dos adapters mockeados) |
| End to end | `StatsSyncControllerIT`: 200 completa y de una liga, 200 cortada, 400, 401, 403, 409 contra Football-Data y contra la cadena, y que el catálogo responde igual con WhoScored caído. `StatsSyncWithoutTokenIT`: sin token de Football-Data, WhoScored corre igual; es una clase aparte, como `SyncDisabledIT`, porque `StatsSyncControllerIT` necesita el token para los 409 |

- **Los comportamientos nuevos de las entidades de la 005 se prueban en clases de test nuevas**
  (`TeamWhoScoredTest`, `PlayerWhoScoredTest`, `MatchStatsStateTest`, `TeamCrossRepositoryIT`,
  etc.), así no se toca ningún test existente de esas entidades.
- **Aislamiento de la base**: los IT nuevos usan su propia H2 (`jdbc:h2:mem:stats-it`) y limpian,
  antes y después de cada test, en orden: estadísticas, partidos, temporadas, jugadores, equipos y últimas completas. Así la
  `sync-it` de la 005 nunca tiene filas en `player_match_stats` que bloqueen su limpieza. Los e2e
  usan su H2 propia, como `SyncControllerIT`.
- **Determinismo**: `on-startup` es `false` en test, no hay `Thread.sleep`, el 409 se prueba con
  un latch como en la 005, y ningún test comparte estado.
- **Tests existentes que cambian**, con el sí de Lucas del 2026-10-13 (aval del spec más el sí
  del mismo día para los dos casos del borde) y el del 2026-10-14 para renombrar tres métodos y
  borrar uno cuyo nombre dejaba de describir lo que prueban (principio IV):

| Test | Cambio | Por qué |
|---|---|---|
| `service/sync/StartupSyncTest` | El helper arma `StartupSync(properties, coordinator)` y el método probado pasa a ser `catchUp()`. `conElCatalogoVacioYTokenDisparaUnaCompletaDeArranque` pasa a llamarse `conTokenDelegaLaPuestaAlDiaEnElCoordinador` y verifica que delega la puesta al día en el coordinador. `sinTokenDejaLaAdvertenciaExactaYNoConsultaElCatalogo` pasa a llamarse `sinTokenDejaLaAdvertenciaExactaYSePoneAlDiaIgual`: conserva la advertencia exacta y verifica que la puesta al día corre igual. `conLaSincronizacionDeArranqueApagadaNoHaceNada` cambia solo el mock que verifica. `conOtraEnCursoDejaUnWarnSinPropagarLaExcepcion` hace que el coordinador lance `SyncInProgressException`, con el mismo WARN. **Se borra** `conJugadoresNoDisparaYDejaElInfo`. | FR-033 de la 005 queda reemplazado por la puesta al día (FR-051), y `STARTUP` pasa a `CATCH_UP` (FR-053). Las decisiones de qué corre se prueban en `CatchUpPolicyTest` y `SyncCoordinatorTest`. |
| `service/sync/SyncSchedulerTest` | El helper arma `SyncScheduler(coordinator)`. `conTokenDisparaUnaSincronizacionCompletaSemanal` pasa a llamarse `disparaLaCadenaSemanal` y verifica `runWeeklyChain()`. **Se borra** `sinTokenNoLlamaAlServicioYDejaUnInfo`: el scheduler ya no mira el token y quedaría igual al anterior; el salteo de Football-Data sin token y su INFO se prueban en `SyncCoordinatorTest`. `conOtraEnCursoNoPropagaLaExcepcionYDejaUnWarnConElMotivo` hace que lance el coordinador, con el mismo WARN. `laCorridaSemanalTomaElCronYLaZonaDeLaConfiguracion` **no cambia**. | La corrida del lunes encadena WhoScored (FR-033 y FR-036 de la 006, que amplían FR-026 de la 005) y el candado es común (FR-031). |
| `service/sync/SyncServiceTest` | Solo el helper `service(token, clock)` y un `@Mock` nuevo: el constructor recibe `new SyncLock()` y el `LastFullSyncRepository` mockeado. Ningún método, aserción ni dato cambia, incluido el 409. | Candado común (FR-031) y última completa exitosa (FR-047). |

- **Se conservan sin cambios** todos los demás, entre ellos `SyncControllerIT`, `SyncDisabledIT`,
  `SyncRunTest`, `SyncReportLoggerTest`, `SyncWriteServiceIT`, `FootballDataPropertiesTest`,
  `FootballDataAdapterTest`, `TeamTest`, `PlayerTest`, `MatchTest`, `LeagueSyncTest`,
  `SnapshotFixtures`, los IT de repositories y `AccessControlIT`.
  - Los constructores actuales de `Team` (5 y 4 argumentos), `Player` (8, 5 y 4) y `Match` (11)
    se conservan como constructores de conveniencia, con el id de WhoScored en `null` y la marca
    en `false`, así no cambia ninguna línea que arma una entidad.
  - `JwtServiceTest.unTokenConLaFirmaAlteradaSeRechazaComoInvalido` es intermitente en la máquina
    de Lucas y no se toca: si falla, se reintenta el build.

**Justificación**: cada nivel de la constitución cubre lo suyo sin salir a internet, y la lista de
tests tocados es la mínima: solo cambian los que prueban un comportamiento de la 005 que esta
feature reemplaza o amplía.

**Alternativas descartadas**:

- **Sumar métodos a `TeamTest`, `PlayerTest`, `MatchTest` y los IT de la 005**: es modificar
  tests existentes sin necesidad, cuando una clase nueva prueba lo mismo.
- **Reescribir `StartupSyncTest` y `SyncSchedulerTest` como clases nuevas y borrar las viejas**:
  borra más de lo que cambia. Se conservan los métodos cuyo comportamiento sigue; los que cambian de
  comportamiento cambian también de nombre, para que el nombre siga describiendo lo que prueban
  (principio IV).
- **Un e2e que levante la aplicación con `on-startup` en `true`**: la puesta al día corre en otro
  hilo al arrancar y competiría con los demás tests del contexto (005, D13). Se prueba con
  `SyncCoordinatorTest` y `SyncChainIT`.

## D27. Rendimiento y volumen

**Decisión y números**:

- **Primera puesta al día sin snapshot** (2026-10-13): unas 430 páginas, con unos 6,5 s por
  página (4 s de pausa y entre 1,2 y 4,3 s de descarga), es decir unos 47 minutos (SC-005: menos
  de 60).
- **Una fecha completa**: 48 páginas, unos 5 minutos (SC-004: menos de 15).
- **Con un snapshot al día hasta la fecha anterior**: la carga son unas 330 transacciones con unas
  30 filas cada una, del orden de un minuto en H2 en archivo, más el scraping de la última fecha
  (SC-006: menos de 15 minutos).
- **Memoria**: la página más grande (1,3 MB) y su bloque leído (1.527 eventos) son unos pocos MB
  por partido, y se liberan antes del siguiente. `CatalogPlayers` son unas 2.650 entradas.
- **Filas**: unas 32 por partido; unas 10.500 al 2026-10-13 y unas 56.000 en una temporada.
- **`show-sql: true`** del perfil local imprime cada INSERT de la carga del snapshot. Es lo
  esperado y no se cambia (fuera de alcance), igual que en la 005.

**Justificación**: ninguna transacción abarca más de un partido o de los cruces de una liga, y el
cuello de botella es la pausa que exige el spec.

**Alternativas descartadas**:

- **Pedir páginas en paralelo**: FR-042 lo prohíbe y aceleraría el bloqueo.
- **Leer el HTML como flujo para no tenerlo entero en memoria**: 1,3 MB por página no justifica un
  parser incremental.

## D28. AVG y Java en la máquina de Lucas

**Decisión**: el quickstart avisa que AVG intercepta HTTPS y Java falla con
`PKIX path building failed`. Hay dos salidas:

- agregar `https://www.whoscored.com/*` a las excepciones de AVG, como se hizo con
  Football-Data;
- o, solo en la sesión, usar
  `JAVA_TOOL_OPTIONS=-Djavax.net.ssl.trustStore=NONE -Djavax.net.ssl.trustStoreType=Windows-ROOT`,
  que hace que Java confíe en el almacén de certificados de Windows.

La suite automatizada no lo necesita, porque nunca sale a internet.

**Justificación**: es el mismo problema que ya se resolvió para Football-Data, y sin aviso la
primera prueba real se cortaría por "bloqueo" en la primera página, que es un diagnóstico
equivocado.

**Alternativa descartada**: **un truststore propio en el repositorio, o desactivar la validación
de certificados en el cliente**: es un cambio de seguridad del código para resolver un problema
de una máquina.

---

## Fuentes consultadas

- **WhoScored**, verificado por el equipo el 2026-10-13 con el navegador:
  - las páginas de liga de las cinco ligas (`/regions/{r}/tournaments/{t}/...`);
  - las páginas de fixtures de Leeds (19) y del Paris Saint-Germain (304);
  - las páginas `/live` de los partidos 1983600, 1983552, 1983559, 1984103 y 1983609, y
    `/livestatistics` del 1983559 (íconos de eventos clave con `data-event-satisfier-assist`);
  - `localization_debug.json`, con los rótulos de las columnas (`TackleWon` → "Tackles",
    `ChallengeLost` → "Drb").
- Lo verificado por Lucas el 2026-10-13 con `curl` y con Java, en el pedido de planificación:
  headers, 49 de 50 respuestas con datos, la señal de rechazo (un 403 de 5 a 6 KB en menos de
  0,3 s), tiempos y tamaños.
- Bytecode del caché de Gradle del proyecto:
  - hibernate-core 7.4.5.Final: `org.hibernate.annotations.ColumnDefault` y
    `AbstractSchemaMigrator.applyIndexes` y `applyUniqueKeys`;
  - spring-web 7.0.9: `JdkClientHttpRequestFactory.enableCompression` y el header
    `Accept-Encoding` de `JdkClientHttpRequest`.
- JDK 21: `java.text.Normalizer` (NFD) y `java.net.http.HttpClient` (HTTP/2 por defecto,
  `Redirect.NEVER` por defecto y sin descompresión automática).
- [specs/005-integracion-football-data/research.md](../005-integracion-football-data/research.md):
  D3 (cliente), D4 (Jackson 3), D7 (traducción y negocio), D8 (transacciones), D11 (semáforo),
  D13 (arranque), D16 (persistencia) y D21 (tests).
- `.specify/memory/constitution.md`, versión 2.2.1.
