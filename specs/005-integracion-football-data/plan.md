# Plan de implementación: Integración con Football-Data.org

**Rama**: `feature/api-footballdata` | **Fecha**: 2026-10-07 | **Especificación**: [spec.md](./spec.md)

**Entrada**: especificación de la feature en `/specs/005-integracion-football-data/spec.md` y el
pedido de planificación del 2026-10-07, con el estado del código relevado el 2026-10-06 y la API
de Football-Data.org verificada ese mismo día.

## Resumen

El catálogo deja de salir del dataset ficticio y pasa a construirse desde Football-Data.org.
La feature:

- trae de la fuente los equipos, los jugadores, la temporada en curso y todos los partidos de
  las cinco ligas;
- los guarda localmente, reconociendo cada dato por el identificador de la fuente;
- inactiva a los jugadores que dejan las cinco ligas y reactiva a los que vuelven;
- corre sola los lunes a las 04:00 de Argentina y al arrancar con el catálogo vacío, y un
  administrador la puede disparar para las cinco ligas o para una;
- tolera las fallas de la fuente liga por liga;
- suma al catálogo la fecha de nacimiento, la nacionalidad, el escudo y el estado del jugador,
  y saca a los inactivos del listado.

Enfoque técnico:

- **Adapter** (`adapter/footballdata/`): es la primera clase de la capa que agregó la
  constitución 2.2.0.
  - Usa un `RestClient` armado a mano, con `JdkClientHttpRequestFactory` y timeouts de 10 s de
    conexión y 30 s de lectura.
  - Hace dos requests por liga.
  - Lee el JSON con records tolerantes, lo traduce a modelo con su mapper y devuelve un
    `LeagueSnapshot`.
  - Traduce cada error del proveedor a `ExternalSourceException`.
  - Respeta el límite de 10 requests por minuto: lleva un ritmo preventivo con los headers de la
    fuente y, ante un 429, espera y reintenta una sola vez. La espera es inyectable.
- **Modelo rico** (`modelo/sync/`, más `season`, `match` y los cambios en `player` y `team`):
  - las reglas por liga viven en `LeagueSync`;
  - la regla del jugador en dos planteles, en `SquadAssignment`;
  - la decisión de inactivar y a quién, en `SyncRun`;
  - las actualizaciones, en `Player.updateFrom`, `Team.updateFrom` y sus pares;
  - el informe, en `SyncReport`.

  Los servicios solo orquestan.
- **Orquestación** (`service/sync/`):
  - `SyncService` verifica que la sincronización esté habilitada y toma un semáforo (una a la
    vez).
  - Después descarga todas las ligas pedidas, resuelve los duplicados y escribe cada liga en
    su propia transacción, mediante `SyncWriteService`.
  - Si la sincronización es completa y las cinco ligas salieron bien, inactiva a los ausentes.
  - Al final registra el informe.
- **Disparadores**:
  - `POST /players/sync[?league=]`, solo para `ADMIN`. Es la primera regla por rol de
    `SecurityConfig`.
  - `@Scheduled` con cron y zona configurables.
  - `@Async` sobre `ApplicationReadyEvent` para el arranque con el catálogo vacío.
- **Persistencia**:
  - `teams` suma `external_id` y `crest`.
  - `players` suma `date_of_birth`, `nationality` y `active`.
  - Hay dos tablas nuevas, `seasons` y `matches`.
  - Los upserts se hacen en lote por `externalId`, con `@EntityGraph`.
  - No hay SQL nativo.
- **Configuración**: el token sale de `FOOTBALL_DATA_TOKEN` y el repositorio no lo contiene.
  Sin token, la aplicación arranca con una advertencia y la sincronización queda deshabilitada
  (503). El perfil test no tiene token y apunta a una URL que no resuelve.
- **Dependencias**: ninguna nueva.

## Decisiones de planificación (2026-10-07)

El usuario resolvió cuatro puntos antes de escribir el diseño:

1. **Contextos: `sync` nuevo.**
   - El servicio de sincronización, su controller y el modelo de la corrida y del informe van en
     `modelo/sync/`, `service/sync/` y `controller/sync/`.
   - Se suman los contextos `season` y `match`.
   - Los snapshots de jugador, equipo y partido van con su entidad.
   - Ver [research.md](./research.md), D2.
2. **Sincronización deshabilitada: 503.**
   - Se agrega la base `ServiceUnavailableException` en `shared/` y su handler en el advice.
   - Así "este servidor no puede sincronizar" se distingue de "hay otra en curso" (409).
   - Ver D15 y Seguimiento de complejidad.
3. **Jugador en dos planteles: conserva su equipo actual.**
   - Si el jugador ya está en uno de los equipos que lo informan, queda ahí. Si no, queda en la
     primera aparición según el orden de procesamiento.
   - Consecuencia: la sincronización descarga todas las ligas pedidas antes de escribir.
   - Ver D8 y D9.
4. **Propiedad `futbolmarket.football-data.sync.on-startup`.**
   - Vale `true` por defecto y `false` en `application-test.yml`.
   - Así los tests que configuran un token a propósito no compiten con una sincronización de
     arranque en segundo plano. En local y en producción, FR-033 se cumple igual.
   - Ver D13.

No se encontraron contradicciones entre el spec, el pedido y la constitución. Dos puntos rozan
el Principio III y quedan registrados en Seguimiento de complejidad:

- el 503 (decisión 2);
- la validación del parámetro `league` en el modelo.

### Validación de las decisiones técnicas propuestas en el pedido

| # | Propuesta | Resultado | Dónde |
|---|---|---|---|
| 1 | Adapter con cliente, mapper y `dto/` | Se confirma. Los errores se traducen a una excepción del modelo que no depende del proveedor, `ExternalSourceException`, para que WhoScored la reutilice. | D2 y D5 |
| 2 | `RestClient` manual con timeouts | Se confirma. Se verificó que el read timeout también cubre la lectura del body. | D3 |
| 3 | 429: esperar el reset y reintentar una vez | Se confirma y se amplía con un ritmo preventivo basado en `X-Requests-Available-Minute` y un tope de 120 s. | D6 |
| 4 | Traducción en el mapper y negocio en el modelo | Se confirma. Se suma un caso: el partido con un estado desconocido se omite y se informa. | D7 |
| 5 | Todo o nada por liga | Se confirma, con otro bean (`SyncWriteService`) en lugar de `TransactionTemplate`, y descargando antes de escribir. | D8 |
| 6 | Inactivación: modelo o JPQL masivo | Se cargan los activos y el modelo decide con `deactivate()`. Unas 2.650 filas por semana es poco. | D10 |
| 7 | Una a la vez, rechazando la segunda | `Semaphore.tryAcquire()`. `@ConcurrencyLimit` queda descartado porque encola. | D11 |
| 8 | Scheduler con cron y zona | Se confirma. Se verificó que `zone` admite placeholders y que `"-"` deshabilita la corrida. | D12 |
| 9 | Sincronización al arrancar | Se confirma, con `@Async` sobre `ApplicationReadyEvent` y la propiedad `on-startup`. | D13 |
| 10 | Credencial por variable de entorno | Se confirma. El perfil test además pisa el token y usa una URL `.invalid`. | D14 |
| 11 | `POST /players/sync`, solo `ADMIN` | Se confirma. El 503 lo eligió el equipo. `league` se recibe como texto para poder informar el valor recibido. | D15 |
| 12 | Persistencia | Se confirma. Los enums nuevos son `VARCHAR` desde un `String`, y no se agrega un índice por fecha. | D16 |
| 13 | Contextos | `sync`, `season` y `match`. | D2 |
| 14 | `PlayerResponse` | Suma `dateOfBirth`, `nationality`, `teamCrest` y `active`. | D18 |
| 15 | Tests sin la API real | Se confirma. Los tests que escriben partidos usan una H2 propia. | D21 |
| 16 | Tipo de `externalId` | Sigue siendo `String` en las cuatro entidades. | D17 |
| 17 | Jackson 3 y propiedades desconocidas | Jackson 3.1.5 no falla con propiedades desconocidas (`false` por defecto), pero sí con un `null` en un primitivo. Se usan wrappers y `@JsonIgnoreProperties` explícito. | D4 |
| 18 | Borrar la base local una vez | Se confirma. | D20 |
| 19 | Informe al registro con SLF4J | Se confirma. | D19 |
| 20 | Listo para WhoScored | Se confirma, sin reservar nada. | D22 |

## Divergencias con el spec

No hay divergencias: el diseño cumple cada requisito tal como está escrito. Estos puntos no son
divergencias, pero conviene tenerlos a la vista:

- **Jugador en dos planteles**: el spec deja al plan la elección del equipo (Supuestos). La fija
  la decisión 3, así que no hace falta enmendar el spec.
- **El informe suma dos datos**: la temporada guardada de cada liga y si se aplicó la
  inactivación (`inactivationApplied`). Son agregados y ayudan a la prueba independiente de la
  HU2.
- **Partido con un estado desconocido**: el spec no lo menciona. Se omite y se informa, igual que
  un partido con un equipo fuera del catálogo (FR-024). Guardarlo sin estado violaría FR-023.
- **`sync.on-startup`**: solo vale `false` en el perfil test. FR-033 se cumple en local y en
  producción.

## Contexto técnico

**Lenguaje y versión**: Java 21 (Temurin), con el wrapper de Gradle.

**Dependencias principales**:

- Spring Boot 4.1.1 (Spring Framework 7.0.9), con `webmvc`, `data-jpa`, `security`,
  `validation`, `actuator` y `h2console`.
- jjwt 0.13.0, springdoc-openapi 3.1.1, Lombok y Jackson 3.1.5.
- **Ninguna dependencia nueva** (D1).

**Almacenamiento**: H2.

- En `local`, en archivo con `ddl-auto: update`. La base se borra una vez (D20).
- En `test`, en memoria con `create-drop`.
- Cambian dos tablas (`teams` y `players`) y hay dos nuevas (`seasons` y `matches`).

**Tests**:

- JUnit 5, Mockito y AssertJ.
- `MockMvc` y `MockMvcTester` con `@SpringBootTest` y `@AutoConfigureMockMvc`.
- `@MockitoBean`, `MockRestServiceServer` y `OutputCaptureExtension`.
- Todo con `@ActiveProfiles("test")`, sin Testcontainers y sin TDD.
- Ningún test llama a la API real.

**Plataforma**: API REST local en el puerto 8080, con `./gradlew bootRun`. Solo la
sincronización sale a internet, hacia `api.football-data.org`.

**Tipo de proyecto**: servicio web en un monorepo (`backend/` y `frontend/`). Solo se toca el
backend.

**Objetivos de rendimiento**:

- El disparo manual completo responde en menos de 2 minutos (SC-003).
- Al arrancar con el catálogo vacío, el catálogo queda construido en menos de 2 minutos
  (SC-018).
- Una sincronización completa hace 10 requests y unos 4.500 inserts en la primera carga.
- Las consultas del catálogo no cambian de costo: siguen paginadas y ahora filtran por `active`.

**Restricciones**:

- El plan gratis admite 10 requests por minuto, y cada request tiene un timeout de 30 s.
- El token nunca aparece en el repositorio, el registro, las respuestas ni los informes.
- Hay a lo sumo una sincronización a la vez.
- Cada liga se escribe toda o nada.
- No se reemplazan `build.gradle` ni los `application*.yml`: solo se agregan claves.
- Solo se tocan los tests listados en D21, con el sí del 2026-10-06.

**Escala y alcance**:

- Datos del 2026-10-06: 96 equipos, 2.649 jugadores (2.634 importables), unos 1.750 partidos y
  5 temporadas.
- 1 endpoint nuevo y 2 que cambian.
- 3 contextos nuevos.
- 51 requisitos funcionales (FR-001 a FR-050, más FR-049a) y 18 criterios de éxito.

No queda ningún NEEDS CLARIFICATION: todo se resolvió en [research.md](./research.md).

## Verificación constitucional

*Puerta: tiene que pasar antes de la investigación y se revisa de nuevo después del diseño.*

### Antes de la investigación

| Principio | Resultado | Evidencia |
|---|---|---|
| I. Capas estrictas (NO NEGOCIABLE) | Cumple | Ver el detalle debajo de la tabla. |
| II. Modelo rico | Cumple | Las decisiones viven en el modelo: `LeagueSync` (crear, actualizar, saltear, reactivar, omitir partidos), `SquadAssignment` (dos planteles), `SyncRun` (si se inactiva y a quién) y `Player`, `Team`, `Season` y `Match` (`updateFrom` y `deactivate`). Los `if` de los servicios deciden cosas de la aplicación, no del dominio: si hay token, si el semáforo está libre y si el catálogo está vacío al arrancar. |
| III. Validación en su nivel | Cumple, con dos notas | Las invariantes están en el modelo, con excepciones propias y con nombre. Los controles de existencia y posibilidad (habilitada, en curso) están en el servicio. El advice sigue siendo único. Las dos notas (el 503 y `league` validado por el modelo) están en Seguimiento de complejidad. |
| IV. Tests (NO NEGOCIABLE) | Cumple | Hay unitarios de modelo sin Spring, de servicio con mocks, del adapter con `MockRestServiceServer`, integración contra H2 y end to end en `e2e/`. Los únicos tests existentes que se tocan son cuatro que se modifican y uno que se borra, con el sí explícito de Lucas del 2026-10-06 (D21). Los demás quedan iguales, y los constructores de conveniencia de `Player` evitan cambios innecesarios. |
| V. Calidad medible | Cumple | Ver el detalle debajo de la tabla. |
| VI. Persistencia explícita | Cumple | Las cuatro tablas tienen `@Table(name = ...)` y los índices únicos tienen nombre. Solo hay derived queries, JPQL y `@EntityGraph`. Los enums nuevos son `VARCHAR`. `Instant` se mapea a `TIMESTAMP WITH TIME ZONE`. `open-in-view` y los perfiles no cambian. |
| VII. Idioma | Cumple | Los identificadores y el endpoint (`/players/sync`) están en inglés. Los contextos (`sync`, `season` y `match`) están en inglés y en singular. Los mensajes, la documentación y los comentarios están en español. |
| Stack tecnológico | Cumple | Sin dependencias nuevas, y `build.gradle` no cambia. |
| Definición de terminado | Cumple | `./gradlew build` y `./gradlew bootRun` con `local`. `POST /players/sync` queda documentado y se puede ejecutar desde `/swagger-ui.html` con los dos esquemas de Authorize ([quickstart.md](./quickstart.md), sección 4). |

Detalle del principio I:

- **El servicio habla con la API externa solo a través de su adapter**. `SyncService` usa
  `FootballDataAdapter` y no conoce `RestClient` ni el JSON de la fuente.
- **El adapter recibe una `League` y devuelve un `LeagueSnapshot`**, que es modelo. Sus DTO viven
  en `adapter/footballdata/dto/` y no salen del adapter.
- **El adapter traduce con su propio mapper y no persiste**: lo que trae lo guarda
  `SyncWriteService` a través de los repositories.
- **Ninguna excepción del cliente HTTP se propaga**: todas pasan a `ExternalSourceException`.
- **El modelo es puro**. Los tipos nuevos de `modelo/` solo usan el JDK (`java.time` y
  colecciones).
- **Persistencia**: las anotaciones de JPA viven solo en `SeasonSQL`, `MatchSQL` y las clases
  `*SQL` existentes. Los mappers los invoca `repository/`.
- **Controller y DTO**: el controller solo habla con `SyncService`. `SyncReportResponse` vive en
  `controller/sync/dto/` y lo arma el controller a partir del `SyncReport`.
- **Contextos nuevos declarados en este plan**:
  - `season`, en modelo y persistencia;
  - `match`, en modelo y persistencia;
  - `sync`, en modelo, servicio y controller.

  Cada uno aparece solo en las capas donde tiene clases.
- **`adapter/` se organiza por proveedor** (`footballdata`).
- **No se crean carpetas fuera del árbol**. `modelo/league/exception/` es la carpeta `exception/`
  que el árbol ya prevé por contexto.
- **Monolito**: la escritura de una liga modifica equipos, jugadores, temporadas y partidos en
  UN método `@Transactional` (`SyncWriteService.applyLeague`). La sincronización completa son
  varias transacciones a propósito (FR-034 y FR-035). `SyncService` depende de
  `SyncWriteService` y no hay ciclos.

Detalle del principio V:

- **Inyección y logging**: inyección por constructor y SLF4J. Los headers, los valores por
  defecto de espera y los motivos son constantes con nombre.
- **Excepciones**:
  - No hay `catch (Exception e)`. Por liga se capturan solo `ExternalSourceException` y
    `DataAccessException` (D8).
  - `InterruptedException` restaura la marca de interrupción.
- **Tests sin `Thread.sleep`**: usan `RecordingSleeper` y un `Clock` fijo.
- **Secretos**: no hay secretos en el código. El token sale de una variable de entorno y su
  `toString` lo enmascara.
- **Limpieza**: se quita el import sin uso de `Team`.

**Resultado**: la puerta pasa, con las dos notas del Principio III justificadas.

### Después del diseño

Se revisaron [data-model.md](./data-model.md), [contracts/](./contracts/) y
[quickstart.md](./quickstart.md) contra cada principio:

- **I**: ningún tipo de `modelo/` importa Spring, JPA ni el adapter.
  - El grafo de dependencias entre paquetes no tiene ciclos:
    - `controller.sync → service.sync, modelo`
    - `service.sync → adapter.footballdata, persistence.repository, modelo`
    - `adapter.footballdata → modelo`
    - `persistence → modelo`
    - `security → modelo.user`
  - `config/FootballDataClientConfig` arma el `RestClient` que usa el adapter: es transversal y
    no cruza ninguna capa.
- **II**: `SyncService` y `SyncWriteService` no tienen un `if` de negocio.
  - Cada decisión de los escenarios de las HU1 a HU5 tiene su método de modelo en la tabla de
    trazabilidad de [data-model.md](./data-model.md).
  - Las consultas que cargan existentes son por `externalId` y no deciden nada.
- **III**: los mensajes de error están en español y no repiten datos sensibles. El de
  `UnsupportedLeagueException` repite el valor de la liga pedida, que no es sensible.
- **IV**: la lista exacta de tests tocados está en D21 y en la Estructura del proyecto. El 409 se
  prueba de forma determinística y la sincronización de arranque no corre en los tests.
- **VI**: no hay SQL nativo. Las columnas nuevas no dependen de tipos propios de H2.
- **Stack**: no aparece ninguna dependencia.

**Resultado**: la puerta sigue pasando.

## Estructura del proyecto

### Documentación de la feature

```text
specs/005-integracion-football-data/
├── spec.md                        # especificación (sin cambios en este plan)
├── plan.md                        # este archivo
├── research.md                    # decisiones D1 a D22
├── data-model.md                  # modelo, persistencia, transiciones y trazabilidad
├── quickstart.md                  # guía de validación de punta a punta
├── contracts/
│   ├── players-api.yaml           # OpenAPI 3.0.3: cambios en /players y POST /players/sync
│   ├── configuration.md           # propiedades, variables de entorno y valores por perfil
│   └── football-data-api.md       # contrato externo: endpoints, campos, valores, headers y errores
├── checklists/requirements.md     # existente
└── tasks.md                       # lo genera después /speckit-tasks
```

### Código fuente

Producción, en `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/`:

```text
adapter/                                          # NUEVO: primera clase de la capa adapter
└── footballdata/
    ├── FootballDataAdapter.java                  # fetchLeague(League) → LeagueSnapshot (2 requests)
    ├── FootballDataClient.java                   # GET /competitions/{code}/teams y /matches; errores → ExternalSourceException;
    │                                             #   ritmo preventivo y un reintento ante 429
    ├── FootballDataMapper.java                   # DTO → modelo, campo a campo (posiciones, estados, resultados, ids)
    ├── FootballDataCompetition.java              # enum: League ↔ código (PL, BL1, PD, SA, FL1)
    ├── Sleeper.java                              # espera inyectable
    ├── ThreadSleeper.java                        # @Component con Thread.sleep; restaura la interrupción
    └── dto/                                      # records con @JsonIgnoreProperties(ignoreUnknown = true)
        ├── CompetitionTeamsDto.java   SeasonDto.java   TeamDto.java   PersonDto.java
        ├── CompetitionMatchesDto.java   MatchDto.java   MatchTeamDto.java   ScoreDto.java   ScoreLineDto.java
        └── ErrorDto.java
config/
├── ApplicationConfig.java                        # CAMBIA: + FootballDataProperties, @EnableScheduling y @EnableAsync
├── FootballDataProperties.java                   # NUEVO: futbolmarket.football-data.* (token enmascarado)
├── FootballDataClientConfig.java                 # NUEVO: bean RestClient (URL base, X-Auth-Token, timeouts)
└── PlayerCatalogDataSeeder.java                  # SE BORRA (FR-002)
controller/
├── player/PlayerController.java                  # CAMBIA: arma PlayerResponse con los campos nuevos
├── player/dto/PlayerResponse.java                # CAMBIA: + dateOfBirth, nationality, teamCrest y active
└── sync/                                         # NUEVO contexto
    ├── SyncController.java                       # POST /players/sync[?league=]; Swagger; solo ADMIN
    └── dto/SyncReportResponse.java               # el informe (records anidados, from(SyncReport))
modelo/
├── league/League.java                            # CAMBIA: fromName
├── league/exception/UnsupportedLeagueException.java   # NUEVO (400)
├── match/                                        # NUEVO contexto
│   └── Match.java   MatchSnapshot.java   MatchStatus.java   MatchWinner.java   Score.java
├── player/Player.java                            # CAMBIA: + dateOfBirth, nationality y active; updateFrom y deactivate
├── player/PlayerSnapshot.java                    # NUEVO
├── season/Season.java                            # NUEVO contexto
├── sync/                                         # NUEVO contexto
│   ├── LeagueSnapshot.java   SquadAssignment.java   LeagueSync.java   SyncRun.java
│   ├── SyncReport.java   LeagueSyncResult.java   EntityCounts.java
│   ├── SyncType.java   SyncOrigin.java   LeagueSyncStatus.java
│   ├── SkippedPlayer.java   PlayerSkipReason.java   SkippedMatch.java   MatchSkipReason.java   DuplicatedPlayer.java
│   └── exception/
│       └── ExternalSourceException.java   SyncInProgressException.java   SyncDisabledException.java
├── team/Team.java                                # CAMBIA: + externalId y crest; updateFrom; sin el import sin uso
└── team/TeamSnapshot.java                        # NUEVO
persistence/
├── mapper/
│   ├── player/PlayerMapper.java   team/TeamMapper.java          # CAMBIAN: campos nuevos
│   └── season/SeasonMapper.java   match/MatchMapper.java        # NUEVOS
├── repository/
│   ├── player/PlayerRepository.java              # CAMBIA: listado solo de activos; findAllByExternalIds, findAllActive,
│   │                                             #   saveAll y hasPlayers; se quita existsByExternalId
│   ├── team/TeamRepository.java                  # CAMBIA: findAllByExternalIds y saveAll; se quita findOrCreate
│   └── season/SeasonRepository.java   match/MatchRepository.java   # NUEVOS
└── sql/
    ├── entity/
    │   ├── player/PlayerSQL.java   team/TeamSQL.java            # CAMBIAN: columnas nuevas e índice ux_teams_external_id
    │   └── season/SeasonSQL.java   match/MatchSQL.java          # NUEVOS: tablas seasons y matches
    └── interfaces/
        ├── player/PlayerSQLDAO.java   team/TeamSQLDAO.java      # CAMBIAN: filtro por active, cargas en lote con @EntityGraph
        └── season/SeasonSQLDAO.java   match/MatchSQLDAO.java    # NUEVOS
security/
└── SecurityConfig.java                           # CAMBIA: POST /players/sync → hasRole(ADMIN), primera regla por rol
service/
├── player/PlayerCatalogService.java              # sin cambios
└── sync/                                         # NUEVO contexto
    ├── SyncService.java                          # orquesta: habilitada → semáforo → descarga → duplicados → escritura → inactivación
    ├── SyncWriteService.java                     # @Transactional: applyLeague (una por liga) y deactivateMissing
    ├── SyncScheduler.java                        # @Scheduled: lunes a las 04:00 de Argentina
    ├── StartupSync.java                          # @Async @EventListener(ApplicationReadyEvent): advertencia o sincronización de arranque
    └── SyncReportLogger.java                     # informe al registro con SLF4J
shared/
├── ServiceUnavailableException.java              # NUEVO: base del 503, sin tipos de Spring
└── GlobalExceptionHandler.java                   # CAMBIA: handler del 503
```

Recursos de producción, en `backend/src/main/resources/`:

```text
application.yaml                                  # CAMBIA: + bloque futbolmarket.football-data
application-local.yml                             # sin cambios
data/players.json                                 # SE BORRA, junto con la carpeta data/
```

Tests, en `backend/src/test/`:

```text
java/ar/edu/unq/desapp/futbolmarket/
├── adapter/footballdata/
│   ├── FootballDataAdapterTest.java              # NUEVO: MockRestServiceServer y fixtures (errores, 429, ritmo, header)
│   ├── FootballDataMapperTest.java               # NUEVO: traducciones sin HTTP
│   └── RecordingSleeper.java                     # NUEVO: doble de Sleeper
├── config/
│   ├── FootballDataPropertiesTest.java           # NUEVO: validación, máscara del token, próximo lunes a las 04:00 de Argentina
│   └── PlayerCatalogDataSeederIT.java            # SE BORRA
├── e2e/
│   ├── PlayerControllerIT.java                   # SE MODIFICA: Team con externalId; + campos nuevos y detalle de un inactivo
│   ├── SyncControllerIT.java                     # NUEVO: 200 (completa y una liga), 400, 401, 403 (el primero), 409; token ausente
│   └── SyncDisabledIT.java                       # NUEVO: 503 sin token, sin consultar la fuente
├── modelo/
│   ├── league/LeagueTest.java                    # NUEVO
│   ├── match/MatchTest.java                      # NUEVO
│   ├── player/PlayerTest.java                    # SE MODIFICA: Team con externalId; + updateFrom y deactivate
│   ├── season/SeasonTest.java                    # NUEVO
│   ├── sync/
│   │   └── LeagueSnapshotTest.java   LeagueSyncTest.java   SquadAssignmentTest.java   SyncRunTest.java   # NUEVOS
│   └── team/TeamTest.java                        # NUEVO
├── persistence/repository/
│   ├── match/MatchRepositoryIT.java              # NUEVO (H2 sync-it)
│   ├── player/PlayerRepositoryIT.java            # SE MODIFICA: save de Team en vez de findOrCreate; + activos y cargas en lote
│   ├── season/SeasonRepositoryIT.java            # NUEVO (H2 sync-it)
│   └── team/TeamRepositoryIT.java                # NUEVO
└── service/
    ├── player/PlayerCatalogServiceTest.java      # SE MODIFICA: Team con externalId
    └── sync/
        └── SyncServiceTest.java   SyncSchedulerTest.java   StartupSyncTest.java   SyncWriteServiceIT.java   # NUEVOS
resources/
├── application-test.yml                          # CAMBIA: + football-data (sin token, URL .invalid, cron "-", on-startup false)
└── footballdata/                                 # NUEVO: teams-pl.json, matches-pl.json, teams-empty.json, error-403.json
```

**Tests existentes que cambian**: el detalle de cada cambio y su motivo está en
[research.md](./research.md), D21. En resumen:

- se borra `PlayerCatalogDataSeederIT`;
- se modifican `PlayerTest`, `PlayerRepositoryIT`, `PlayerCatalogServiceTest` y
  `PlayerControllerIT`, solo para armar `Team` con `externalId`, más métodos nuevos;
- el resto queda sin cambios, incluidos `AccessControlIT`, `GlobalExceptionHandlerIT` y
  `JsonAccessDeniedHandlerTest`.

**Decisión de estructura**:

- Se usa el árbol de la constitución 2.2.0. Es el primer uso de `adapter/<proveedor>/`.
- Los contextos `season`, `match` y `sync` quedan declarados en este plan.
- No se crea ninguna carpeta fuera del árbol, ni carpetas vacías ni `.gitkeep`.
- Los tests replican capa y contexto. Los end to end viven en `e2e/` y las fixtures en
  `src/test/resources/footballdata/`.

## Dependencias a agregar

**Ninguna.** `build.gradle` no cambia. Todo lo que hace falta ya está en el classpath:

- `RestClient` y `JdkClientHttpRequestFactory` (spring-web);
- `@Scheduled`, `@Async` y `CronExpression` (spring-context);
- `MockRestServiceServer` (spring-test).

La verificación y las alternativas descartadas están en [research.md](./research.md), D1.

## Secuencia de implementación

Estas son las restricciones de orden para `/speckit-tasks`. Cada fase deja `./gradlew build` en
verde.

1. **Preparación**:
   - Borrar `PlayerCatalogDataSeeder`, `data/players.json` y `PlayerCatalogDataSeederIT`.
   - Agregar las propiedades a `application.yaml` y `application-test.yml`, según
     [contracts/configuration.md](./contracts/configuration.md).
   - Crear `FootballDataProperties` con su test y registrarla en `ApplicationConfig`, junto con
     `@EnableScheduling` y `@EnableAsync`.
   - Crear `FootballDataClientConfig`.
2. **Catálogo existente: modelo y persistencia**. `Team` y `Player` cambian juntos con su
   persistencia, porque los mappers dejan de compilar si cambia uno solo.
   - Modelo: `Team` (`externalId`, `crest`, `updateFrom`), `TeamSnapshot`, `Player` (campos
     nuevos, `updateFrom`, `deactivate`), `PlayerSnapshot`, `League.fromName` y
     `UnsupportedLeagueException`.
   - Persistencia: `TeamSQL`, `PlayerSQL`, sus mappers y DAOs, y los repositories, con las
     cargas en lote y `saveAll`. Se quitan `findOrCreate` y `existsByExternalId`.
   - Se modifican los cuatro tests existentes, según D21.
   - Tests nuevos: `TeamTest`, `LeagueTest` y los métodos nuevos de `PlayerTest`,
     `PlayerRepositoryIT` y `TeamRepositoryIT`.
   - El filtro por `active` todavía no se aplica: llega en la fase 9.
3. **Temporadas y partidos**:
   - `Season`, `Match`, `MatchSnapshot`, `MatchStatus`, `MatchWinner` y `Score`.
   - `SeasonSQL` y `MatchSQL`, con sus DAOs, mappers y repositories.
   - `SeasonTest`, `MatchTest`, `SeasonRepositoryIT` y `MatchRepositoryIT`, sobre la H2
     `sync-it`.
4. **Modelo de la sincronización**:
   - `LeagueSnapshot`, `SquadAssignment`, `LeagueSync`, `SyncRun`, `SyncReport` y los tipos del
     informe.
   - `ExternalSourceException`, `SyncInProgressException`, `SyncDisabledException`, y
     `ServiceUnavailableException` con su handler en el advice.
   - Sus tests unitarios.
5. **Adapter**:
   - Los DTO, `FootballDataCompetition`, `FootballDataMapper`, `FootballDataClient`,
     `FootballDataAdapter`, `Sleeper` y `ThreadSleeper`.
   - Las fixtures, `FootballDataMapperTest` y `FootballDataAdapterTest`.
6. **Servicios**:
   - `SyncWriteService`, `SyncService` y `SyncReportLogger`.
   - `SyncServiceTest` y `SyncWriteServiceIT`: idempotencia, rollback por liga, inactivación y
     cantidades que no bajan.
7. **Endpoint manual**:
   - `SyncController` y `SyncReportResponse`, con Swagger.
   - La regla de ADMIN en `SecurityConfig`.
   - `SyncControllerIT`, con el 403 y el 409 determinístico, y `SyncDisabledIT`.
8. **Disparadores automáticos**: `SyncScheduler` y `StartupSync`, con `SyncSchedulerTest` y
   `StartupSyncTest`.
9. **Consulta del catálogo**:
   - El filtro por `active` en `findAllByActiveTrueOrderByIdAsc` y en el JPQL de los filtros.
   - Los campos nuevos de `PlayerResponse`.
   - Los métodos nuevos de `PlayerRepositoryIT` (inactivos fuera del listado) y de
     `PlayerControllerIT` (campos nuevos y detalle de un inactivo).
10. **Cierre y definición de terminado**:
    - `./gradlew build`.
    - Borrar la base local una vez.
    - `./gradlew bootRun` con `local`: primero sin token y después con un token real.
    - Las secciones 2 a 5 de [quickstart.md](./quickstart.md).

**Riesgo de merge**: el compañero trabaja en otra rama (numeración 004). Los dos pueden agregar
claves al bloque `futbolmarket` de `application.yaml` y tocar `SecurityConfig` o
`GlobalExceptionHandler`. Los cambios de esta feature son agregados dentro de bloques existentes,
así que un conflicto se resuelve conservando las dos partes.

## Seguimiento de complejidad

Esta sección registra dos desvíos justificados del Principio III.

| Desvío | Por qué hace falta | Alternativa más simple descartada |
|---|---|---|
| El parámetro `league` de `POST /players/sync` se recibe como `String` y lo valida el modelo (`League.fromName` lanza `UnsupportedLeagueException`), en lugar de validarlo Bean Validation en el request. | FR-030 pide rechazar indicando el valor no admitido. El enlace a enum de Spring termina en el handler genérico del advice, que no repite el valor. Cambiar ese handler cambiaría también la respuesta de `GET /players` (FR-048) y sus tests. Además, "una de las cinco ligas" es una invariante del dominio (Entidad Liga del spec), y el principio ubica las invariantes en el modelo. | Bean Validation con `@Pattern` sobre el `String`: la violación termina en `HandlerMethodValidationException`, que el advice responde con el mensaje genérico, sin el valor. Cambiar el handler de `MethodArgumentTypeMismatchException`: altera `GET /players`. |
| Se agrega el status 503 con una base nueva, `ServiceUnavailableException`, en `shared/`. El principio enumera 400, 401, 403, 404 y 409. | Decisión del equipo del 2026-10-07: "la sincronización está deshabilitada en este servidor" es un problema de configuración del servidor, y no tiene que confundirse con "hay otra en curso" (409). La base sigue el patrón de las demás (sin tipos de Spring), el advice sigue siendo único y el formato de error es el mismo. | Responder 409 en los dos casos: el cliente los distinguiría solo por el texto del mensaje. |
