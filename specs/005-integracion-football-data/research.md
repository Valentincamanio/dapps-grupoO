# Investigación: Integración con Football-Data.org

**Rama**: `feature/api-footballdata` | **Fecha**: 2026-10-07 | **Plan**: [plan.md](./plan.md)

Este documento resuelve las incógnitas técnicas del plan y registra cada decisión con su
justificación y las alternativas descartadas. No quedan marcadores NEEDS CLARIFICATION. Las
decisiones D2, D9, D13 y D15 incluyen definiciones que tomó el equipo el 2026-10-07 (ver
Decisiones de planificación en el [plan](./plan.md)).

## Relevamiento del repositorio

Se leyeron los archivos existentes antes de decidir nada:

- **Rama y documentos**: se trabaja en `feature/api-footballdata`. `.specify/feature.json`
  apunta a `specs/005-integracion-football-data` y la constitución está en la versión 2.2.0,
  que agrega la capa `adapter/<proveedor>/` y permite cambiar carpetas con el aval de un
  desarrollador.
- **`backend/build.gradle`**: Spring Boot 4.1.1, Java 21, `webmvc`, `data-jpa`, `security`,
  `validation`, `actuator`, `h2console`, jjwt 0.13.0, springdoc 3.1.1 y Lombok. En test, los
  starters `*-test` de actuator, data-jpa, validation y webmvc. No está
  `spring-boot-restclient`, así que no hay un `RestClient.Builder` autoconfigurado.
- **Configuración**:
  - `application.yaml`: perfil por defecto `local`, `open-in-view: false`, exclusión del
    usuario en memoria y el bloque `futbolmarket` con `auth` y `security.jwt`.
  - `application-local.yml`: H2 en archivo (`./data/futbolmarket`), `ddl-auto: update`,
    `show-sql: true` y la consola de H2.
  - `application-test.yml`: H2 en memoria (`testdb`), `create-drop` y el secreto JWT de test.
- **Catálogo**:
  - `Player` es un record `(id, externalId, name, position, team)` con dos constructores. Valida
    en el constructor compacto con `CatalogInvariantException` y deriva `league()` del equipo.
  - `Team` es un record `(id, name, league)` con un constructor sin id. Tiene un import de
    `java.util.Objects` que no se usa.
  - `PlayerSQL` y `TeamSQL` mapean `players` y `teams`. `externalId` de `PlayerSQL` es único con
    una restricción sin nombre. `position` y `league` usan `@Enumerated(EnumType.STRING)`.
  - `AppUserSQL.role`, en cambio, es un `String` que traduce el mapper. El data-model de 001
    explica por qué: con `@Enumerated`, Hibernate 7 crea en H2 una columna `ENUM` nativa que
    `ddl-auto: update` no hace evolucionar.
  - `PlayerSQLDAO.findAllByFilters` es JPQL con filtros opcionales.
  - `TeamRepository.findOrCreate(name, league)` y `PlayerRepository.existsByExternalId` solo los
    usa el seeder. `findOrCreate` también lo usa `PlayerRepositoryIT`.
  - `PlayerController` no tiene anotaciones de Swagger. El filtro `league` se enlaza como enum:
    un valor inválido termina en `MethodArgumentTypeMismatchException`, que el advice responde
    con el mensaje genérico "Los parámetros enviados no son válidos.", sin el valor recibido.
  - `PlayerCatalogDataSeeder`, con `@Profile("local")`, carga `data/players.json`. Lo prueba
    `PlayerCatalogDataSeederIT`.
- **Transversales**:
  - `SecurityConfig` tiene un único `RequestMatcher` de rutas públicas, con
    `GET /players` y `GET /players/**` marcadas como TEMPORAL. Ninguna regla distingue por rol.
  - `JsonAccessDeniedHandler` escribe el 403 en JSON. Su test lo prueba sin HTTP, y su Javadoc
    aclara que en la feature 001 ningún endpoint exigía un rol.
  - `GlobalExceptionHandler` tiene bases para 400, 401, 404 y 409 y un fallback a 500.
    `IllegalArgumentException` también va a 400 con el mensaje genérico.
  - `AdminAccountInitializer` es el patrón de "advertencia y la aplicación arranca igual", y
    `AdminAccountIT` es el patrón de e2e con administrador: credenciales por
    `@DynamicPropertySource` y una H2 propia.
- **Tests que dependen del catálogo**: `PlayerTest`, `PlayerRepositoryIT`,
  `PlayerCatalogServiceTest` y `PlayerControllerIT` arman `Team` con los constructores actuales.
  `PlayerCatalogDataSeederIT` prueba el seeder.
  - `PlayerControllerIT` arma `MockMvc` con `webAppContextSetup` sin la cadena de seguridad, así
    que la regla nueva no lo afecta.
  - `AccessControlIT` comprueba que `POST /players` sin credencial da 401, y eso sigue igual.
- **Bytecode verificado en el caché de Gradle** (detalle en D3, D4, D12 y D13):
  - spring-web 7.0.9: `JacksonJsonHttpMessageConverter` y `JdkClientHttpRequest`.
  - jackson-databind 3.1.5: `DeserializationFeature`.
  - spring-context 7.0.9: `ScheduledAnnotationBeanPostProcessor` y `Scheduled`.
  - spring-boot-autoconfigure 4.1.1: `TaskExecutorConfigurations`.

---

## D1. Dependencias

**Decisión**: no se agrega ninguna dependencia y `build.gradle` no cambia. Todo lo necesario
ya está en el classpath:

| Necesidad | Clase | Artefacto |
|---|---|---|
| Cliente HTTP | `RestClient` | spring-web 7.0.9 |
| Timeouts | `JdkClientHttpRequestFactory` sobre `java.net.http.HttpClient` | spring-web y JDK 21 |
| JSON del proveedor | `JacksonJsonHttpMessageConverter` (Jackson 3.1.5) | spring-web y jackson-databind |
| Corrida semanal | `@Scheduled`, `@EnableScheduling` y `CronExpression` | spring-context 7.0.9 |
| Arranque en segundo plano | `@Async`, `@EnableAsync` y el executor de Boot | spring-context y spring-boot-autoconfigure 4.1.1 |
| Tests del adapter | `MockRestServiceServer` | spring-test |

**Justificación**: la constitución pide no agregar dependencias sin avisar y sin justificar.
Ninguna de las alternativas resuelve algo que el classpath no resuelva.

**Alternativas descartadas**:

- `spring-boot-restclient`: autoconfigura un `RestClient.Builder` con el `JsonMapper` de Boot.
  Suma un módulo para armar un único cliente, que a mano son pocas líneas.
- Resilience4j o spring-retry: hay un único reintento y su espera la dicta un header (D6).
- WireMock: `MockRestServiceServer` alcanza para un cliente síncrono.

## D2. Ubicación en el árbol y contextos nuevos

**Decisión**. El equipo eligió el contexto `sync` el 2026-10-07.

| Carpeta | Contenido |
|---|---|
| `adapter/footballdata/` | `FootballDataAdapter` (lo que usa el servicio), `FootballDataClient` (HTTP, errores, ritmo y reintento), `FootballDataMapper`, `FootballDataCompetition` (liga ↔ código), `Sleeper` y `ThreadSleeper` |
| `adapter/footballdata/dto/` | Records del JSON del proveedor. Nunca salen del adapter. |
| `modelo/season/` | `Season` |
| `modelo/match/` | `Match`, `MatchSnapshot`, `MatchStatus`, `MatchWinner` y `Score` |
| `modelo/sync/` | `SyncRun`, `SyncReport`, `LeagueSync`, `LeagueSyncResult`, `SquadAssignment`, `LeagueSnapshot` y los tipos del informe |
| `modelo/sync/exception/` | `ExternalSourceException`, `SyncInProgressException` y `SyncDisabledException` |
| `modelo/league/exception/` | `UnsupportedLeagueException`. Es la carpeta `exception/` que el árbol ya prevé por contexto. |
| `modelo/player/` y `modelo/team/` | Se suman `PlayerSnapshot` y `TeamSnapshot` |
| `persistence/*/season/` y `persistence/*/match/` | Entidad, DAO, mapper y repository de cada contexto nuevo |
| `service/sync/` | `SyncService` (orquesta), `SyncWriteService` (una transacción por liga), `SyncScheduler`, `StartupSync` y `SyncReportLogger` |
| `controller/sync/` y `controller/sync/dto/` | `SyncController` y `SyncReportResponse` |
| `config/` | `FootballDataProperties` y `FootballDataClientConfig` |
| `shared/` | `ServiceUnavailableException`: base nueva para el 503 (D15) |

- Los contextos nuevos se declaran en el plan, como pide el Principio I:
  - `season`, en modelo y persistencia;
  - `match`, en modelo y persistencia;
  - `sync`, en modelo, servicio y controller.
- `sync` es una abreviatura en inglés y en singular, igual que `auth`.
- **Snapshots**: es lo que la fuente informa en una descarga.
  - Cada snapshot va con la entidad a la que pertenece: `PlayerSnapshot` en `player`,
    `TeamSnapshot` en `team` y `MatchSnapshot` en `match`.
  - `LeagueSnapshot` va en `sync`, porque agrupa a los tres y solo existe para sincronizar.
- **Disparadores**: el scheduler y el disparo de arranque viven en `service/sync/`, como
  `AdminAccountInitializer` vive en `service/user/`.
- **Árbol**: no se crean carpetas fuera del árbol de la constitución.

**Alternativas descartadas**:

- **Contexto `league`**: la liga es la unidad de la sincronización, pero el informe, la corrida
  y los disparadores no son la liga.
- **Contexto `player`**: mezclaría temporadas y partidos dentro del catálogo de jugadores.
- **Un puerto en el modelo implementado por el adapter**: el diagrama de la constitución es
  Service → Adapter. Un puerto sumaría una interfaz sin una segunda implementación, y WhoScored
  trae otros datos, no los mismos.

## D3. Cliente HTTP

**Decisión**:

- `config/FootballDataClientConfig` declara un bean `RestClient` llamado
  `footballDataRestClient`, armado con `RestClient.builder()`:
  - `baseUrl` sale de las propiedades.
  - El header `X-Auth-Token` va como header por defecto solo si el token no está en blanco.
  - El request factory es un `JdkClientHttpRequestFactory` sobre
    `HttpClient.newBuilder().connectTimeout(connectTimeout)`, con
    `setReadTimeout(readTimeout)`.
- Los valores son 10 s de conexión y 30 s de lectura (FR-037).
- Verificado en spring-web 7.0.9: `JdkClientHttpRequest` envuelve el cuerpo de la respuesta con
  un `TimeoutHandler`. Por eso los 30 s cubren también la lectura del body, no solo la espera
  de los headers. Una respuesta de 370 KB que se corta a mitad de camino también da timeout.
- `FootballDataClient` recibe el `RestClient` por constructor. Así sus tests le pasan uno
  armado con su propio builder, enlazado a un `MockRestServiceServer`.
- Nada registra los headers del request. Spring, en nivel DEBUG, solo escribe el método y la
  URL, y la URL no lleva el token. No se agregan interceptores de logging.

**Alternativas descartadas**:

- `SimpleClientHttpRequestFactory` (`HttpURLConnection`): también tiene timeouts. El cliente del
  JDK es el que se propuso y su modelo de timeouts es más claro.
- `RestTemplate`: es la API anterior. `WebClient`: pide WebFlux.
- `spring-boot-restclient`: ver D1.

## D4. Jackson 3 y los DTO del proveedor

**Hallazgos**, verificados en el bytecode del caché de Gradle:

- `RestClient.builder()` registra un `JacksonJsonHttpMessageConverter`. Su constructor por
  defecto arma el mapper con `JsonMapper.builder()` más los módulos que encuentra, y no toca
  ninguna feature. En Spring 6 era distinto: `Jackson2ObjectMapperBuilder` deshabilitaba
  `FAIL_ON_UNKNOWN_PROPERTIES`.
- En jackson-databind 3.1.5, `DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES` vale `false`
  por defecto, a diferencia de Jackson 2. **El converter por defecto no falla con propiedades
  desconocidas.**
- En cambio, `FAIL_ON_NULL_FOR_PRIMITIVES` vale `true` por defecto en Jackson 3. Un `null` que
  llega a un campo `int` hace fallar la lectura. La fuente manda `null` en `currentMatchday`,
  `score.fullTime.home` y `coach.id`, entre otros.
- Jackson 3 trae `java.time` integrado: lee `LocalDate` desde `yyyy-MM-dd` e `Instant` desde
  ISO-8601.

**Decisión**:

- Los DTO son records con `@JsonIgnoreProperties(ignoreUnknown = true)` de todos modos.
  - La tolerancia queda explícita y no depende de cómo esté configurado el mapper. Es el mismo
    criterio que `RegisterRequest` en 001 (D11).
  - Las respuestas reales traen decenas de campos que no se usan (`address`, `website`,
    `odds`, `referees`, etc.).
- Los campos numéricos que pueden venir en `null` son wrappers (`Long`, `Integer`), nunca
  primitivos.
- Tipos de fecha: `LocalDate` para `dateOfBirth`, `startDate` y `endDate`, e `Instant` para
  `utcDate`.
- Los valores enumerados del proveedor (`position`, `status`, `winner`) se leen como `String` y
  los traduce el mapper. Un valor nuevo de la fuente no rompe la lectura de toda la liga (D7).
- Cada DTO declara solo los campos que se usan (ver
  [contracts/football-data-api.md](./contracts/football-data-api.md)).
- Los tests del adapter usan fixtures armadas a partir de respuestas reales, con sus campos
  desconocidos y sus `null`.

**Alternativas descartadas**:

- **Pasarle al converter el `JsonMapper` de Boot**: haría depender la tolerancia de la
  configuración global.
- **Declarar todos los campos de la respuesta**: es ruido y acopla a campos que no se usan.

## D5. Traducción de los errores del proveedor

**Decisión**:

- Hay una única excepción, `ExternalSourceException`, en `modelo/sync/exception/`. No depende
  del proveedor y su mensaje es el motivo, en español, que va al informe.
- Extiende `RuntimeException` y nunca llega a una respuesta HTTP, porque el orquestador la
  captura por liga (D8).
- Casos:

| Caso | Cómo llega | Motivo en el informe |
|---|---|---|
| Timeout de conexión o de lectura | `ResourceAccessException` con causa `HttpTimeoutException` | `La fuente no respondió a tiempo.` |
| Otra falla de red | `ResourceAccessException` | `No se pudo conectar con la fuente.` |
| 400 | `HttpClientErrorException` | `La fuente rechazó la consulta (400).` |
| 403 | `HttpClientErrorException` | `La fuente rechazó la credencial o el recurso no está disponible en el plan contratado (403).` |
| 404 | `HttpClientErrorException` | `La fuente no encontró la competición (404).` |
| 429 después del reintento | `HttpClientErrorException` | `Se excedió el límite de consultas de la fuente (429).` |
| 5xx | `HttpServerErrorException` | `La fuente respondió con un error (5xx).` |
| JSON ilegible o de otro tipo | `RestClientException` de conversión | `La respuesta de la fuente no tiene el formato esperado.` |
| Datos que violan invariantes (un equipo sin id o sin nombre, una temporada sin fechas, un partido con el mismo equipo de local y de visitante) | `CatalogInvariantException` al mapear | Igual que el anterior |
| Ningún equipo en la liga (FR-039) | Invariante de `LeagueSnapshot` | `La fuente no informó ningún equipo para la liga.` |
| Partidos de otra temporada | Invariante de `LeagueSnapshot` | `La fuente informó partidos de otra temporada.` |
| 429 que pide esperar más de 120 s (D6) | Header `X-RequestCounter-Reset` | `Se excedió el límite de consultas de la fuente (429).` |
| Espera del límite de consultas interrumpida (D6) | `InterruptedException` en `ThreadSleeper` | `Se interrumpió la espera por el límite de consultas de la fuente.` |
| Falla al guardar una liga ya descargada (D8) | `DataAccessException` en `SyncWriteService` | `No se pudieron guardar los datos de la liga.` |

- El campo `message` del cuerpo de error de la fuente no va al informe: está en inglés y no
  agrega nada. Se registra en WARN junto con la liga, porque ayuda a diagnosticar y no contiene
  el token.
- **El token nunca aparece**:
  - Los motivos son constantes.
  - Las excepciones del `RestClient` se conservan solo como causa, para el stack trace del
    log. Sus mensajes llevan el status, el cuerpo y la URL, nunca los headers del request.
- Nunca se propaga una excepción del cliente HTTP, como pide la constitución.

**Alternativas descartadas**:

- **Una excepción por caso**: nadie las distingue; alcanza con el motivo, que es lo que
  verifican los tests.
- **Excepciones dentro de `adapter/footballdata/`**: acoplarían el servicio a este proveedor y
  WhoScored tendría que duplicarlas.

## D6. Límite de requests

**Hechos**: el plan gratis admite 10 requests por minuto y una sincronización completa hace
exactamente 10. La fuente informa `X-Requests-Available-Minute` y `X-RequestCounter-Reset`
(segundos hasta que el contador se renueva).

**Decisión**:

1. **Ritmo preventivo**.
   - Después de cada respuesta, el cliente guarda las requests disponibles y el instante de
     renovación (calculado con el `Clock`).
   - Antes de cada request, si no quedan disponibles y la renovación no pasó, espera hasta la
     renovación.
   - Con el contador libre, las 10 requests salen sin esperas. Si una sincronización anterior
     consumió parte del minuto, la siguiente espera la renovación en lugar de recibir un 429.
   - Si una respuesta no trae `X-Requests-Available-Minute`, o después de esperar la
     renovación, el estado se limpia: la request siguiente sale sin esperar hasta que otra
     respuesta informe el contador de nuevo. Así un valor viejo nunca provoca una segunda
     espera, tampoco con el `Clock` fijo de los tests.
2. **Ante un 429**:
   - Espera los segundos de `X-RequestCounter-Reset`, o 60 s si el header falta o no se puede
     leer, y reintenta una vez.
   - Si el reintento falla por cualquier motivo, la liga queda fallida (FR-038).
   - Ningún otro status se reintenta. Un timeout deja la liga fallida (FR-037).
3. **Tope de seguridad**: si el header pide más de 120 s, no se espera y la liga queda fallida
   por límite de consultas. En el plan gratis el contador se renueva cada minuto, así que con
   la fuente respondiendo con normalidad no ocurre. El tope está en FR-038 desde el
   2026-10-07.
4. **La espera es inyectable**: pasa por la interfaz `Sleeper` (`void sleep(Duration)`).
   - En producción la implementa `ThreadSleeper`.
   - En los tests, `RecordingSleeper` registra las esperas sin dormir.
   - Si el hilo se interrumpe, se restaura la marca de interrupción y la liga queda fallida.
5. Los valores son constantes con nombre: `DEFAULT_RESET_WAIT` (60 s), `MAX_RESET_WAIT` (120 s)
   y los nombres de los headers.

**Justificación**:

- El supuesto del spec dice: "La sincronización respeta ese límite; si igual lo excede, aplica
  FR-038".
- SC-003 se cumple: en el peor caso hay una espera de hasta 60 s, más 10 requests y las
  escrituras, todo en menos de 2 minutos.

**Alternativas descartadas**:

- **`@Retryable`** (spring-context 7): solo admite demoras fijas o con backoff. No puede tomar
  la demora de un header de la respuesta, y reintenta por tipo de excepción, lo que mezclaría
  el 429 con los timeouts.
- **`RetryTemplate`**: tiene la misma limitación del backoff y requiere más código que el
  reintento explícito.
- **`@ConcurrencyLimit`**: limita la concurrencia, no el ritmo, y encola (ver D11).
- **Una pausa fija de 6 s entre requests**: haría durar cada sincronización al menos un minuto,
  aunque el contador esté libre.

## D7. Traducción del proveedor y reglas de negocio

**Decisión**: el adapter traduce el vocabulario del proveedor y el modelo decide (Principio II).

| Regla | Dónde | Por qué |
|---|---|---|
| `Goalkeeper` → `GOALKEEPER`, `Defence` → `DEFENDER`, `Midfield` → `MIDFIELDER` y `Offence` → `FORWARD` (FR-011). Cualquier otro valor, o `null`, queda sin posición. | `FootballDataMapper` | Traduce el vocabulario del proveedor. |
| `PL`, `BL1`, `PD`, `SA` y `FL1` ↔ `League` | `FootballDataCompetition` | Ídem. |
| `status` y `winner` → `MatchStatus` y `MatchWinner`; un valor desconocido queda en `null` | `FootballDataMapper` | Ídem. |
| `fullTime` o `halfTime` con `home` y `away` en `null` → sin resultado (`Score` nulo) | `FootballDataMapper` | Es la ausencia del dato. |
| Id numérico → `externalId` de texto (D17) | `FootballDataMapper` | Ídem. |
| Un nombre en blanco cuenta como sin nombre | `PlayerSnapshot` | Normalización del modelo. |
| Saltear al jugador nuevo sin nombre o sin posición (FR-012) | `LeagueSync` | Negocio. |
| Conservar el nombre y la posición del jugador existente (FR-013) | `Player.updateFrom` | Negocio. |
| Jugador en dos planteles (FR-015) | `SquadAssignment` | Negocio (D9). |
| Omitir el partido con un equipo fuera del catálogo (FR-024) o con estado desconocido | `LeagueSync` | Negocio. |
| Dar por fallida la liga sin equipos (FR-039) | `LeagueSnapshot` | Negocio. |
| Si se puede inactivar (FR-017 y FR-018) y a quién | `SyncRun.canDeactivate` y `SyncRun.playersToDeactivate` | Negocio (D10). |
| Reactivar y cambiar de equipo (FR-016 y FR-019) | `Player.updateFrom` | Negocio. |
| Conteos y listas del informe (FR-043 y FR-044) | `LeagueSync`, `SyncRun` y `SyncReport` | Negocio. |
| Por qué no se inactivó a nadie (una sola liga o ligas fallidas) | `SyncReport.inactivationSkipReason()` | Negocio: el logger solo lo escribe (D19). |

El partido con estado desconocido es una decisión de este plan. El spec no lo menciona, y la
opción es omitirlo e informarlo como con un equipo desconocido: guardarlo sin estado violaría
FR-023, y dar por fallida la liga entera por un valor nuevo de la fuente sería
desproporcionado.

Un partido con el mismo equipo de local y de visitante es distinto: no es un valor nuevo de la
fuente sino un dato imposible. `MatchSnapshot` lo rechaza y la liga falla por error de formato
(D5). Así `Match` nunca falla al escribir, dentro de la transacción de la liga, con una excepción
que el orquestador no captura.

## D8. Flujo de una sincronización y transacciones

**Decisión**:

```text
SyncService.synchronizeAll(origin)  |  SyncService.synchronizeLeague(league)
  1. ¿Está habilitada?           no → SyncDisabledException (503)
  2. semaphore.tryAcquire()      no → SyncInProgressException (409)
  3. run = SyncRun.full(origin, ahora)  |  SyncRun.singleLeague(league, ahora)
  4. Descarga: por cada liga pedida, en el orden del enum League
       adapter.fetchLeague(liga) → LeagueSnapshot                (2 requests)
       ExternalSourceException → run.recordFailure(liga, motivo)
  5. Dos planteles (D9):
       run.registerSnapshots(snapshots)
       actuales = playerRepository.findAllByExternalIds(run.duplicatedPlayerExternalIds())
       run.resolveDuplicates(actuales)
  6. Escritura: por cada liga descargada
       SyncWriteService.applyLeague(snapshot, run.squadAssignment())  ← @Transactional
       ok → run.recordSuccess(resultado)
       ExternalSourceException | DataAccessException → run.recordFailure(liga, motivo)
  7. Inactivación: si run.canDeactivate()
       SyncWriteService.deactivateMissing(run)                        ← @Transactional
  8. reporte = run.finish(ahora, inactivados); SyncReportLogger.log(reporte)
  finally: semaphore.release()
```

- **Escritura de una liga** (`SyncWriteService.applyLeague`), en una sola transacción:
  1. Equipos: carga los existentes por `externalId`, el modelo arma la lista y se guarda con
     `saveAll`.
  2. Jugadores: carga los existentes por `externalId`, con su equipo; el modelo arma la lista
     con los equipos ya guardados y se guarda con `saveAll`.
  3. Temporada: la busca por `externalId`, el modelo decide y se guarda.
  4. Partidos: carga los existentes por `externalId` y los equipos que referencian; el modelo
     arma la lista y se guarda con `saveAll`.
  5. Devuelve el `LeagueSyncResult` que arma el modelo.
- **Transacciones**: la de cada liga vive en otro bean (`SyncWriteService`), así no hay
  auto-invocación de `@Transactional`.
  - El orquestador no es transaccional. Por eso un rollback en una liga no deja nada marcado
    como rollback-only: es el mismo motivo por el que `AdminAccountInitializer` no es
    transaccional (001, D12).
  - Es "un método de servicio `@Transactional`" por caso de uso, como pide la regla de
    monolito. La sincronización completa son varias transacciones a propósito (FR-034 y
    FR-035).
- **Descargar antes de escribir** da dos garantías:
  - una liga cuyos partidos no se pudieron descargar no escribe nada (HU3, escenario 3);
  - la regla de los dos planteles ve todos los planteles antes de decidir (D9).
- **Consultas durante la sincronización**: cada liga se confirma entera. H2 (MVStore) lee lo
  confirmado sin bloquear, así que una consulta ve lo de antes o lo de después de la liga,
  nunca algo intermedio (supuesto del spec y FR-036).
- **Qué se captura**: por liga se capturan solo `ExternalSourceException` y
  `DataAccessException` de Spring.
  - Un error de programación no se disfraza de falla de la fuente: aborta la corrida (500 en el
    disparo manual) y el semáforo se libera en el `finally`.
  - No hay `catch (Exception e)` genérico (Principio V).

**Alternativas descartadas**:

- **`TransactionTemplate` en el orquestador**: funciona, pero mete transacciones programáticas
  en un servicio cuando el resto del código es declarativo.
- **Una transacción para toda la sincronización**: viola FR-034 y FR-035.
- **Descargar y escribir liga por liga**: usaría menos memoria, pero la regla de D9 necesita
  todos los planteles antes de decidir. Los cinco snapshots juntos son unos 2.650 jugadores y
  unos 1.750 partidos en memoria: es poco.
- **Capturar `RuntimeException`**: va contra el Principio V y enmascararía errores.

## D9. Jugador informado en dos planteles

**Decisión**. El equipo decidió el 2026-10-07 que el jugador conserva su equipo actual. Ese
mismo día se precisó qué pasa con un jugador nuevo (análisis de `/speckit-analyze`, I2).

1. `SquadAssignment` recorre los snapshots descargados en orden: las ligas en el orden del
   enum, y los equipos y sus planteles en el orden de la fuente. Por cada `externalId` junta
   los equipos distintos en los que aparece.
2. Si un jugador aparece en más de un equipo:
   - si ya está guardado y su equipo actual es uno de ellos, queda en ese equipo;
   - si ya está guardado y su equipo actual no es uno de ellos, queda en el primero según el
     orden de procesamiento;
   - si es nuevo, queda en la primera aparición con nombre y posición. Si ninguna los tiene,
     queda en la primera, y se saltea (FR-012).
3. Las demás apariciones se ignoran al escribir. Cada una va al informe como un
   `DuplicatedPlayer`, con el `externalId`, el nombre, el equipo que queda y el equipo ignorado.
4. El jugador cuenta como presente para la inactivación.

Detalles:

- Aplica igual dentro de una liga y entre ligas. Una sincronización de una sola liga solo ve
  los planteles de esa liga.
- El equipo actual sale de una consulta por los `externalId` duplicados, que suelen ser pocos o
  ninguno.
- Si la liga del equipo elegido falla al escribir, el jugador no se mueve y la otra aparición
  igual se ignora. Es la opción conservadora: nada cambia por un dato dudoso.
- **Jugador nuevo incompleto en una aparición**: si otra aparición trae nombre y posición, se
  elige esa. Así un jugador nuevo se saltea solo si ninguna aparición está completa, como pide
  FR-004. Un jugador guardado no necesita esta preferencia: conserva su nombre y su posición
  aunque la aparición elegida no los traiga (FR-013).

**Justificación**: un jugador que la fuente lista en dos clubes no se muda de liga por el orden
de la respuesta.

**Alternativa descartada**: primera aparición en el orden de procesamiento. Permitía escribir
liga por liga, pero podía mudar al jugador a un equipo nuevo solo por el orden de la respuesta.

## D10. Inactivación y reactivación

**Decisión**:

- La inactivación corre solo si `run.canDeactivate()`: la sincronización es completa y las cinco
  ligas quedaron `SUCCEEDED`, en la descarga y en la escritura (FR-017 y FR-018).
- `SyncWriteService.deactivateMissing(run)` es `@Transactional`:
  1. Carga los jugadores activos con su equipo, en una consulta de unas 2.650 filas.
  2. El modelo, con `run.playersToDeactivate(activos)`, devuelve los que no aparecieron en
     ningún plantel descargado.
  3. Se llama a `player.deactivate()` en cada uno y se guardan con `saveAll` solo esos.
  4. Devuelve los inactivados para el informe.
- **Cuentan como vistos** todos los `externalId` de los planteles de los cinco snapshots,
  incluidos los omitidos, los duplicados y los que llegan sin posición (FR-013: cuenta como
  presente).
- **La reactivación ocurre en la escritura de la liga**: `Player.updateFrom` deja al jugador
  activo, y `LeagueSync` registra como reactivado al que estaba inactivo (FR-019).
- **Costo**: una consulta y unas decenas de UPDATE por semana. Menos de un segundo en H2.

**Alternativas descartadas**:

- **UPDATE masivo en JPQL** (`... SET active = false WHERE active = true AND externalId NOT IN
  :vistos`): es más rápido, pero saca la regla del modelo (Principio II) y necesita igual una
  consulta previa para listar a los inactivados en el informe.
- **Una columna `lastSeenAt` y un UPDATE por fecha**: suma una columna, y la decisión seguiría
  siendo una consulta.

## D11. Una sincronización a la vez

**Decisión**:

- `SyncService` tiene un `Semaphore(1)`. Todos los disparos (manual, semanal y de arranque)
  usan `tryAcquire()` sin esperar, y liberan con `release()` en un `finally`.
- **Con otra en curso**:
  - el disparo manual lanza `SyncInProgressException` (409, `Ya hay una sincronización en
    curso.`);
  - la corrida semanal registra un WARN y se omite (FR-031);
  - la de arranque registra un WARN. Solo pasa si un administrador se le adelantó.
- **Orden de los controles**: primero se verifica que la sincronización esté habilitada (503) y
  después el semáforo (409).
- El lock en memoria alcanza: el sistema es una única instancia del monolito con H2 embebida.
- Se usa un semáforo y no un `ReentrantLock` porque el permiso no queda atado a un hilo ni es
  reentrante.

**Alternativas descartadas**:

- **`@ConcurrencyLimit(1)`**: encola, es decir, bloquea en lugar de rechazar.
- **`synchronized`**: también bloquea.
- **Una tabla de lock en la base**: solo hace falta con varias instancias.

## D12. Corrida semanal

**Decisión**:

- `@EnableScheduling` va en `ApplicationConfig`.
- `SyncScheduler.runWeeklySync()` lleva
  `@Scheduled(cron = "${futbolmarket.football-data.sync.cron}", zone = "${futbolmarket.football-data.sync.zone}")`.
  - Verificado en spring-context 7.0.9: `cron` y `zone` pasan por el resolvedor de
    placeholders, y `"-"` (`Scheduled.CRON_DISABLED`) deshabilita la tarea.
  - En `application.yaml` los valores son `0 0 4 * * MON` y `America/Argentina/Buenos_Aires`
    (UTC-3, sin horario de verano).
  - En `application-test.yml` el cron es `-`.
- **Comportamiento**:
  - Si la sincronización está deshabilitada (sin token), registra un INFO y no hace nada
    (FR-041).
  - Si hay otra en curso, registra un WARN y la omite (FR-031).
  - Si no, corre una completa con origen `WEEKLY` y el informe queda en el registro.
- **Corridas perdidas**: no se recuperan. El disparador de cron calcula la próxima ejecución a
  partir del momento actual y no guarda las ejecuciones (FR-027).
- `FootballDataProperties` valida el cron (`CronExpression.isValidExpression` o `-`) y la zona
  (`ZoneId.of`) al arrancar.
- **Tests, sin esperar**:
  - `SyncSchedulerTest`: unitario, con el servicio mockeado.
  - `FootballDataPropertiesTest`: lee `application.yaml` y, con `CronExpression`, comprueba
    que la próxima ejecución desde el domingo 2026-10-11 a las 10:00 de Argentina es el lunes
    2026-10-12 a las 04:00 de Argentina (07:00 UTC), y que desde ese lunes a las 04:00 es el
    lunes siguiente.

**Alternativas descartadas**:

- **`fixedRate` de una semana**: se corre de las 04:00 y depende de la hora de arranque.
- **Un cron sin zona**: usaría la zona del servidor y fallaría con un servidor en UTC.
- **Quartz**: suma una dependencia, y recuperar las corridas perdidas es justamente lo que
  FR-027 no quiere.

## D13. Sincronización al arrancar con el catálogo vacío

**Decisión**:

- `StartupSync` (`service/sync/`) tiene un método con `@Async` y
  `@EventListener(ApplicationReadyEvent.class)`. Corre cuando la aplicación ya levantó, en un
  hilo del executor de Boot, así que no demora el arranque (FR-033 y SC-018).
- `@EnableAsync` va en `ApplicationConfig`. Boot 4.1.1 autoconfigura `applicationTaskExecutor`
  y lo conecta con `@Async` mediante `TaskExecutorConfigurations$ApplicationTaskExecutorAsyncConfigurer`
  (verificado en spring-boot-autoconfigure 4.1.1).
- **Flujo**:
  1. Sin token: registra en WARN que falta la credencial y que la sincronización queda
     deshabilitada (FR-041), y termina. Esta es la advertencia de arranque: aparece en cada
     arranque sin token, en cualquier perfil.
  2. Si `sync.on-startup` es `false`, termina.
  3. Si el catálogo tiene jugadores (`playerRepository.hasPlayers()`), registra un INFO y
     termina.
  4. Si no, corre una completa con origen `STARTUP`. Si hay otra en curso, registra un WARN.
- **`sync.on-startup`** (el equipo lo decidió el 2026-10-07): vale `true` en
  `application.yaml` y `false` en `application-test.yml`.
  - Sin ella, cualquier contexto de test que configure un token a propósito dispararía, en
    segundo plano, una sincronización contra el adapter mockeado. Esa sincronización competiría
    por el semáforo y sumaría invocaciones a los mocks del primer test: los tests dejarían de
    ser determinísticos.
  - En local y en producción la propiedad no se toca, así que FR-033 se cumple.

**Alternativas descartadas**:

- **`ApplicationRunner`**: bloquea el arranque, salvo que se lo haga asíncrono igual.
- **Un hilo propio o un hilo virtual**: queda fuera del ciclo de vida de Spring y no se apaga
  ordenadamente.
- **Inyectar un `TaskExecutor`**: hay dos beans de ese tipo (el executor y el scheduler), así
  que hay que calificarlo por nombre.

## D14. Credencial y propiedades

**Decisión**:

- `FootballDataProperties` es un record con prefijo `futbolmarket.football-data` y
  `@Validated`, registrado en `ApplicationConfig`. Sigue el patrón de `JwtProperties`:
  - `token`: opcional.
  - `baseUrl`: `URI`, obligatoria.
  - `connectTimeout` y `readTimeout`: `Duration`, obligatorias y positivas.
  - `sync`: un record anidado con `cron` y `zone` (obligatorios y válidos) y `onStartup`.
  - `hasToken()` indica si hay token, y `toString()` lo enmascara.
- **`application.yaml`**: `token: ${FOOTBALL_DATA_TOKEN:}`. Es la única referencia: un
  placeholder con default vacío, sin ningún valor (FR-040 y SC-011).
- **`application-test.yml`**, con dos redes de seguridad:
  - `token: ""` explícito. Un archivo de perfil pisa a la base, así que un desarrollador con
    `FOOTBALL_DATA_TOKEN` exportado no hace que los tests consulten la fuente real.
  - `base-url: http://football-data.invalid/v4`. El dominio `.invalid` está reservado por la
    RFC 2606 y nunca resuelve.
- **Sin token**:
  - la aplicación arranca y registra el WARN de D13;
  - el disparo manual da 503;
  - la corrida semanal se omite con un INFO.

  El token no se valida con `@NotBlank`, porque eso impediría el arranque (FR-041).
- **El token nunca aparece en logs, respuestas ni informes**:
  - `toString` lo enmascara;
  - el header lo agrega el `RestClient` y nada registra headers;
  - los motivos de error son constantes (D5);
  - el informe no lleva configuración.

  Un e2e con un token al azar comprueba que no aparece en la respuesta ni en la salida capturada
  (`OutputCaptureExtension`), como pide SC-011.

**Alternativas descartadas**:

- **Usar solo el binding relajado (`FUTBOLMARKET_FOOTBALLDATA_TOKEN`)**: el pedido nombra
  `FOOTBALL_DATA_TOKEN`, y una variable de entorno de la propiedad le ganaría a
  `application-test.yml`.
- **`@NotBlank` sobre el token**: impediría el arranque.

## D15. Endpoint de disparo manual y seguridad

**Decisión**:

- **Rutas**:
  - `POST /players/sync`: sincronización completa.
  - `POST /players/sync?league=PREMIER`: una sola liga.

  Siempre responden 200 con el informe, aunque fallen todas las ligas (FR-032).
- **Ubicación**: `controller/sync/SyncController`. La ruta va bajo `/players` porque lo que el
  cliente ve es el catálogo. Las temporadas y los partidos todavía no se exponen.
- **Seguridad**: en `SecurityConfig` se agrega
  `.requestMatchers(HttpMethod.POST, "/players/sync").hasRole(Role.ADMIN.name())`, después del
  matcher público y antes de `anyRequest().authenticated()`.
  - La regla es por método HTTP, así que `GET /players/**` sigue público (TEMPORAL).
  - Es la primera regla por rol.
  - Sin credencial responde 401 (entry point). Un `USER` recibe 403 de `JsonAccessDeniedHandler`:
    es el primer 403 real.
  - Los dos rechazos ocurren antes del controller, así que no se consulta la fuente (FR-029 y
    SC-010).
- **Parámetro `league`**: se recibe como `String` y se convierte con `League.fromName`, en el
  modelo. Que sea una de las cinco ligas es una invariante del dominio (Entidad Liga del spec),
  así que validarlo en el modelo cumple el Principio III.
  - Un valor que no es una de las cinco ligas lanza `UnsupportedLeagueException` (400), con el
    mensaje `La liga 'X' no es una de las admitidas: PREMIER, BUNDESLIGA, LA_LIGA, SERIE_A,
    LIGUE_1.` (FR-030).
  - `fromName` recorta los espacios de los extremos y distingue mayúsculas, igual que el enlace
    de enums de Spring: ` PREMIER ` se acepta y `premier` no.
  - El enlace a enum de Spring falla con `MethodArgumentTypeMismatchException`, que el advice
    responde con el mensaje genérico, sin el valor. Cambiar ese handler cambiaría también
    `GET /players` (FR-048) y sus tests.
  - `?league=` vacío da el mismo 400. En esto se aparta del enlace de Spring, que convierte el
    texto vacío en `null`: acá un parámetro presente pero vacío es un pedido mal formado.
- **Errores**:
  - 409: `SyncInProgressException`, que extiende `ConflictException`.
  - 503: `SyncDisabledException`, que extiende la base nueva `ServiceUnavailableException` de
    `shared/` (decisión del equipo, que admite el Principio III desde la constitución 2.2.1). El
    advice suma su handler. El mensaje es `La
    sincronización con Football-Data.org está deshabilitada: falta configurar la credencial de
    la fuente (FOOTBALL_DATA_TOKEN).`
- **Swagger**:
  - `@Tag(name = "Sincronización")`.
  - `@Operation` y un `@ApiResponse` por código (200, 400, 401, 403, 409 y 503), con el esquema
    `ApiError` en los errores.
  - `@SecurityRequirement` de `bearerAuth` y de `apiKeyAuth`, igual que en
    `AccountController`.
  - `@Parameter` con los cinco valores admitidos.
- **Respuesta**: `SyncReportResponse`, con records anidados y un `from(SyncReport)` estático,
  como `ProfileResponse.from`.

**Alternativas descartadas**:

- **`@PreAuthorize` con `@EnableMethodSecurity`**: deja una regla fuera de `SecurityConfig` y
  habilita la seguridad por método para un solo endpoint.
- **`/sync` o `/admin/sync`**: también servirían, pero el spec y el pedido hablan del
  catálogo.
- **202 Accepted y procesar en segundo plano**: FR-032 pide esperar y devolver el informe.
- **409 para la sincronización deshabilitada**: el equipo eligió el 503, que la distingue de
  "hay otra en curso".
- **Validar `league` con Bean Validation (`@Pattern` sobre el `String`)**: la violación termina
  en `HandlerMethodValidationException`, que el advice responde con el mensaje genérico, sin el
  valor que pide FR-030.
- **Cambiar el handler de `MethodArgumentTypeMismatchException` para que repita el valor**:
  alteraría la respuesta de `GET /players` (FR-048) y sus tests.

## D16. Persistencia

**Decisión** (el detalle está en [data-model.md](./data-model.md)):

- **Tablas**:
  - `teams` suma `external_id` y `crest`.
  - `players` suma `date_of_birth`, `nationality` y `active`.
  - Hay dos tablas nuevas, `seasons` y `matches`.
- **Índices únicos con nombre**: `ux_teams_external_id`, `ux_seasons_external_id` y
  `ux_matches_external_id`. `players` conserva su restricción única existente sobre
  `external_id`.
- **Enums nuevos como texto**: las columnas enumeradas nuevas (`seasons.league`,
  `matches.status` y `matches.winner`) son `VARCHAR`, mapeadas desde un `String` en la clase
  `*SQL`. El mapper traduce con `name()` y `valueOf`, igual que `AppUserSQL.role`.
  - Con `@Enumerated`, Hibernate 7 crea columnas `ENUM` nativas en H2 que `ddl-auto: update`
    no hace evolucionar.
  - `players.position` y `teams.league` conservan su `@Enumerated`: no se pidió cambiarlas, y la
    base local se recrea igual.
- **`Instant`** se mapea a `TIMESTAMP WITH TIME ZONE`, el tipo por defecto de Hibernate para
  `Instant`, que también existe en PostgreSQL.
- **Upserts en lote**:
  - Cada paso hace una consulta `findAllByExternalIdIn` con `@EntityGraph`, que trae en la misma
    consulta el equipo del jugador, o la temporada y los equipos del partido. Así se evita el
    N+1 de las relaciones LAZY al mapear.
  - Después se guarda con `saveAll`.
  - Como los existentes se cargaron en la misma transacción, guardar la copia mapeada hace un
    merge sobre la instancia administrada, sin otro SELECT. El dirty checking de Hibernate solo
    emite el UPDATE si algo cambió.
- **IDENTITY**: impide los inserts JDBC en lote. Con unos 4.500 inserts repartidos en cinco
  transacciones sobre H2 embebida, la primera sincronización tarda segundos. No se pasa a
  SEQUENCE, porque cambiaría las tablas existentes.
- **Sin índice por fecha en `matches`**: ninguna consulta de esta feature lo usa. Lo agrega la
  feature que consulte "los partidos de la semana" (WhoScored), junto con su consulta. H2 ya
  crea un índice por cada clave foránea.
- Solo hay derived queries, JPQL y `@EntityGraph`. No hay SQL nativo.

**Alternativas descartadas**:

- **`@Embeddable` para el resultado**: son más anotaciones para cuatro columnas.
- **`MERGE` de SQL**: es SQL nativo.
- **Cargar los existentes por liga en lugar de por `externalId`**: un jugador transferido viene
  de un equipo de otra liga.

## D17. Tipo del identificador externo

**Decisión**: `externalId` es `String` en las cuatro entidades, como ya lo es en `Player`. El
adapter convierte el id numérico con `String.valueOf`. Las columnas nuevas son `VARCHAR(32)`.

**Justificación**:

- Es un identificador opaco: nunca se opera con él.
- `Player` queda como está, igual que los tests que usan ids como `premier-01`.
- El tipo es el mismo en las cuatro entidades.
- Un proveedor futuro podría usar ids alfanuméricos.

**Alternativas descartadas**:

- **`Long` en todas**: cambia el tipo en `Player` y en cada test que lo arma, sin ningún
  beneficio.
- **Tipos mixtos**: son inconsistentes.

## D18. Cambios en la consulta del catálogo

**Decisión**:

- **El listado muestra solo activos**:
  - `PlayerRepository.findPage(...)` conserva sus nombres, y así sus tests no cambian.
  - La consulta pasa a filtrar por `active = true`, con la derived query
    `findAllByActiveTrueOrderByIdAsc` y en el JPQL de los filtros.
  - La paginación cuenta solo a los activos.
- **El detalle** (`findById`) no cambia: devuelve también a los inactivos (FR-049a).
- **`PlayerResponse`** suma al final:
  - `dateOfBirth` (`yyyy-MM-dd`, o `null` si la fuente no la informa);
  - `nationality`;
  - `teamCrest`;
  - `active`, que en el listado siempre es `true`.
- `PlayerFilter` y la semántica de los filtros no cambian. El filtro por equipo compara contra el
  nombre oficial porque es el que se guarda (FR-047).
- `PlayerCatalogService` no cambia.

**Alternativas descartadas**:

- **Un DTO aparte para el detalle**: daría dos formas al mismo recurso.
- **Un filtro opcional para pedir los inactivos**: el equipo lo descartó en el spec.

## D19. Informe y registro

**Decisión**:

- El informe es modelo (`SyncReport`), lo arma `SyncRun` y no se guarda (FR-045).
- La línea de inicio (`Sincronización FULL (STARTUP) iniciada.`) la escribe `SyncService` en
  INFO al tomar el semáforo, como se ve en quickstart 2.3.
- `SyncReportLogger` (`service/sync/`) registra el informe con SLF4J:
  - una línea por liga: en INFO si se procesó, con los conteos y la temporada, y en WARN si
    falló, con el motivo;
  - líneas de detalle en INFO, solo si hay algo para listar: jugadores omitidos (nombre y
    equipo), partidos omitidos, duplicados, reactivados e inactivados;
  - una línea final en INFO con la duración y los totales y, si no se inactivó a nadie, por qué.
    El motivo lo da el modelo (`SyncReport.inactivationSkipReason()`); el logger solo lo
    traduce a texto.
- `SyncReportLoggerTest` verifica esas líneas con `OutputCaptureExtension` (FR-044 y FR-045).
- El disparo manual devuelve el mismo informe en la respuesta.
- Hay ejemplos de las líneas en [quickstart.md](./quickstart.md).

## D20. Base H2 local y dataset ficticio

**Decisión**:

- En la fase 1 se borran `config/PlayerCatalogDataSeeder`, `src/main/resources/data/players.json`
  y el test `config/PlayerCatalogDataSeederIT` (FR-002, con el aval del 2026-10-06).
- La base local (`backend/data/futbolmarket.mv.db`) se borra una vez, como indica el
  [quickstart](./quickstart.md).
  - Con `ddl-auto: update`, agregar `teams.external_id` y `players.active` como `NOT NULL` sobre
    tablas con filas falla.
  - Los datos ficticios tienen que desaparecer igual (SC-002).
  - También se pierden los usuarios: el administrador se recrea con sus variables de entorno, y
    los usuarios comunes se registran de nuevo.
- No se migra nada, como dice el supuesto del spec.
- `show-sql: true` del perfil local imprime cada INSERT de la primera sincronización, unas 4.500
  líneas. No se cambia, porque está fuera de alcance; el quickstart lo advierte.

## D21. Estrategia de tests

**Decisión**:

- **Nunca se llama a la API real**:
  - el adapter se prueba con `MockRestServiceServer`;
  - el servicio, con el adapter mockeado;
  - los e2e, con `@MockitoBean FootballDataAdapter`.

  El perfil test no tiene token y su URL base es `.invalid`.
- **Fixtures** en `src/test/resources/footballdata/`, armadas a partir de respuestas reales
  recortadas:
  - `teams-pl.json`: con campos desconocidos, un jugador con `position: null` y las cuatro
    posiciones;
  - `matches-pl.json`: con partidos `FINISHED`, `TIMED` y `POSTPONED`;
  - `teams-empty.json`;
  - `error-403.json`, con el cuerpo real del 403.
- **Niveles**:

| Nivel | Clases |
|---|---|
| Unitarios de modelo | `TeamTest`, `PlayerTest` (modificado y ampliado), `SeasonTest`, `MatchTest`, `LeagueTest`, `LeagueSnapshotTest`, `LeagueSyncTest`, `SquadAssignmentTest` y `SyncRunTest` |
| Adapter | `FootballDataMapperTest` (sin HTTP) y `FootballDataAdapterTest` (`MockRestServiceServer`, `RecordingSleeper` y un `Clock` fijo) |
| Configuración | `FootballDataPropertiesTest` |
| Unitarios de servicio | `SyncServiceTest`, `SyncSchedulerTest`, `StartupSyncTest`, `SyncReportLoggerTest` y `PlayerCatalogServiceTest` (modificado) |
| Integración contra H2 | `TeamRepositoryIT`, `SeasonRepositoryIT`, `MatchRepositoryIT`, `PlayerRepositoryIT` (modificado y ampliado) y `SyncWriteServiceIT` |
| End to end | `SyncControllerIT`, `SyncDisabledIT` y `PlayerControllerIT` (modificado y ampliado) |

- **Tests existentes que cambian**, con el sí explícito de Lucas del 2026-10-06:

| Test | Cambio | Por qué |
|---|---|---|
| `config/PlayerCatalogDataSeederIT` | Se borra. | Prueba el seeder, que se elimina (FR-002). |
| `modelo/player/PlayerTest` | El campo `river` y las dos construcciones de `Team` de `rechazaEquipoSinNombreOLiga` pasan a llevar `externalId` (y `crest` en `null`). Los mensajes y las aserciones no cambian. Se suman métodos para `updateFrom` y `deactivate`. | `Team` exige `externalId` (FR-006). |
| `persistence/repository/player/PlayerRepositoryIT` | Las llamadas a `teamRepository.findOrCreate(name, league)` pasan a `teamRepository.save(new Team(externalId, name, null, league))`. Se suman métodos para el filtro de activos y la carga por `externalId`. | `findOrCreate` se elimina: los equipos se reconocen por `externalId` y no por nombre. |
| `service/player/PlayerCatalogServiceTest` | Las dos construcciones `new Team(id, name, league)` pasan a llevar `externalId`. | `Team` exige `externalId`. El comportamiento probado no cambia. |
| `e2e/PlayerControllerIT` | Las tres construcciones de `Team` pasan a llevar `externalId`. Se suman métodos para los campos nuevos y para el detalle de un jugador inactivo. | Ídem, más FR-046 y FR-049a. |

- **Se conservan sin cambios** los demás tests, entre ellos `AccessControlIT`,
  `GlobalExceptionHandlerIT`, `GlobalExceptionHandlerTest` y `FutbolMarketApplicationTests`.
  - `JsonAccessDeniedHandlerTest` también queda igual: su Javadoc habla de la feature 001.
  - Los constructores de conveniencia de `Player` (con y sin id) se mantienen, así que las
    líneas que arman jugadores no cambian en ningún test.
- **Aislamiento de la base**:
  - Los tests que escriben temporadas o partidos (`SeasonRepositoryIT`, `MatchRepositoryIT` y
    `SyncWriteServiceIT`) usan una H2 en memoria propia, `jdbc:h2:mem:sync-it`, con la misma
    `@TestPropertySource`. Comparten un único contexto de Spring y limpian en `@BeforeEach`
    respetando las claves foráneas: partidos, temporadas, jugadores y equipos.
  - Así `PlayerRepositoryIT` sigue limpiando solo jugadores y equipos sobre `testdb`, sin
    tropezar con partidos que referencian equipos.
  - Los e2e de sincronización usan su propia H2, con el patrón de `AdminAccountIT`.
- **`SyncControllerIT`**:
  - administrador por propiedades, un token al azar, una H2 propia y
    `@MockitoBean FootballDataAdapter`;
  - el 409 se prueba de forma determinística: el mock del adapter se bloquea en un latch
    durante el primer request, que corre en otro hilo; el test manda el segundo request y
    después libera el latch.
- **Determinismo**:
  - `sync.on-startup` vale `false` en test;
  - no hay `Thread.sleep`: se usan `RecordingSleeper` y un `Clock` fijo;
  - ningún test comparte estado: cada uno tiene su base o limpia la suya.

## D22. Preparado para WhoScored

- `adapter/whoscored/` sería un hermano de `adapter/footballdata/`. `ExternalSourceException` no
  depende del proveedor.
- Ya se guarda lo que WhoScored necesita para cruzar a cada jugador con su rendimiento:
  - el nombre, el nombre oficial del equipo y la fecha de nacimiento (FR-010);
  - los partidos con su fecha y sus equipos (FR-022).
- No se implementa nada de WhoScored ni se reservan carpetas vacías.

---

## Fuentes consultadas

- Bytecode del caché de Gradle del proyecto:
  - spring-web 7.0.9:
    - `JacksonJsonHttpMessageConverter`: su constructor por defecto usa `JsonMapper.builder()` y
      `MapperBuilder.findModules`, sin tocar features.
    - `JdkClientHttpRequest` y su `TimeoutHandler`: el read timeout cubre el body.
  - jackson-databind 3.1.5, `DeserializationFeature`:
    - `FAIL_ON_UNKNOWN_PROPERTIES` vale `false` por defecto;
    - `FAIL_ON_NULL_FOR_PRIMITIVES` y `FAIL_ON_TRAILING_TOKENS` valen `true`.
  - spring-context 7.0.9: `ScheduledAnnotationBeanPostProcessor` resuelve placeholders en `cron`
    y en `zone`, y `Scheduled.CRON_DISABLED` vale `"-"`.
  - spring-boot-autoconfigure 4.1.1: `TaskExecutorConfigurations$ApplicationTaskExecutorAsyncConfigurer`.
- API v4 de Football-Data.org: las respuestas, los valores, los headers y los errores los
  verificó el equipo el 2026-10-06 con el plan gratis. Están en
  [contracts/football-data-api.md](./contracts/football-data-api.md).
- [specs/001-auth-usuarios/data-model.md](../001-auth-usuarios/data-model.md): por qué
  `AppUserSQL.role` es un `String`.
- [specs/001-auth-usuarios/research.md](../001-auth-usuarios/research.md): D11 (propiedades
  desconocidas explícitas), D12 (inicializador no transaccional) y D17 (patrón de e2e con
  administrador).
- `.specify/memory/constitution.md`, versión 2.2.1 (la 2.2.0 más la enmienda del 2026-10-07,
  que suma el 503 al Principio III).
