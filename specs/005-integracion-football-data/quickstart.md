# Guía de validación: Integración con Football-Data.org

**Rama**: `feature/api-footballdata` | **Fecha**: 2026-10-07 | **Plan**: [plan.md](./plan.md)

Esta guía prueba la feature de punta a punta. Los detalles están en otros documentos:

- Requests y respuestas: [contracts/players-api.yaml](./contracts/players-api.yaml).
- La API de la fuente: [contracts/football-data-api.md](./contracts/football-data-api.md).
- Propiedades y variables de entorno: [contracts/configuration.md](./contracts/configuration.md).
- Reglas del modelo: [data-model.md](./data-model.md).

## Precondiciones

- Java 21. No hacen falta Docker, una base externa ni ningún otro servicio.
- Todos los comandos se ejecutan desde `backend/`.
- La suite automatizada no necesita token ni conexión a internet: nunca llama a la API real.
- Para las secciones 2 a 5 hacen falta:
  - un token propio de Football-Data.org con el plan gratis, en `FOOTBALL_DATA_TOKEN`;
  - el administrador configurado con las tres variables `FUTBOLMARKET_AUTH_ADMIN_*` (ver
    [contracts/configuration.md](./contracts/configuration.md#configurar-el-token-en-local)).
- El plan gratis admite 10 consultas por minuto y cada sincronización completa usa 10. Dos
  completas seguidas hacen que la segunda espere a que se renueve el contador, hasta un minuto.
  Es el comportamiento esperado ([research.md](./research.md) D6).
- Los ejemplos usan `curl` de Git Bash. En PowerShell hay que invocar `curl.exe`, porque `curl`
  es un alias de `Invoke-WebRequest`.

## 1. Suite automatizada

```bash
./gradlew test
```

```bash
./gradlew build
```

Resultado esperado: `BUILD SUCCESSFUL`. La suite cubre:

- **Unitarios de modelo**, sin Spring:
  - `Team`, `Player`, `Season` y `Match`: invariantes y `updateFrom`. `Player.updateFrom`
    conserva el nombre y la posición, cambia de equipo y reactiva. `Player.deactivate` conserva
    el equipo.
  - `League.fromName`.
  - `LeagueSnapshot`: una liga sin equipos falla, igual que una con partidos de otra temporada.
  - `LeagueSync`:
    - creación y actualización;
    - jugadores nuevos sin nombre o sin posición, salteados e informados;
    - jugadores existentes sin posición, que conservan la suya;
    - reactivados;
    - partidos con un equipo fuera del catálogo o un estado desconocido;
    - conteos.
  - `SquadAssignment`: duplicados en la misma liga y entre ligas, y la regla de "conserva su
    equipo actual; si no, la primera aparición".
  - `SyncRun`: solo la completa con las cinco ligas en éxito inactiva, a quiénes inactiva y el
    armado del informe.
- **Adapter**, con `MockRestServiceServer` y fixtures reales:
  - traducción de posiciones, estados, ganador y resultados `null`;
  - campos desconocidos ignorados;
  - el header `X-Auth-Token`;
  - 403, 404, 5xx, timeout y JSON inválido, que terminan en `ExternalSourceException` con su
    motivo;
  - 429 con espera y un reintento, y 429 dos veces;
  - el ritmo preventivo con `X-Requests-Available-Minute`.

  Ningún test duerme: usan `RecordingSleeper` y un `Clock` fijo.
- **Configuración**: el cron de `application.yaml` da como próxima ejecución el lunes a las 04:00
  de Argentina, el token se enmascara en `toString`, y un cron o una zona inválidos impiden el
  arranque.
- **Unitarios de servicio**:
  - `SyncService`: deshabilitada → 503; en curso → 409; una liga fallida no frena a las demás;
    una sola liga no inactiva; las cinco fallidas devuelven igual el informe.
  - `SyncScheduler` y `StartupSync`: catálogo vacío o con datos, sin token, con otra en curso.
- **Integración**, contra H2 en memoria:
  - los repositories de equipos, jugadores, temporadas y partidos: upsert por `externalId`,
    índices únicos, listado solo de activos y carga con su equipo;
  - `SyncWriteServiceIT`:
    - una segunda sincronización idéntica no crea nada (SC-005);
    - una falla en los partidos revierte toda la liga (SC-008);
    - la inactivación conserva el equipo;
    - las cantidades nunca bajan (SC-006).
- **End to end**, en `e2e/`: las secciones 3.1 a 3.5, salvo lo que requiere la API real.
  Incluyen el primer 403 del sistema (un `USER` que intenta sincronizar) y un token al azar que
  no aparece ni en la respuesta ni en el registro (SC-011).

## 2. Levantar con el perfil local

### 2.1 Borrar la base H2 local (una sola vez)

La base local tiene el dataset ficticio y tablas sin las columnas nuevas. Con
`ddl-auto: update`, agregar una columna obligatoria sobre filas existentes falla, y los datos
ficticios tienen que desaparecer igual (SC-002; [research.md](./research.md) D20).

Con la aplicación detenida, desde `backend/`:

```bash
rm -f data/futbolmarket.mv.db data/futbolmarket.trace.db
```

```powershell
Remove-Item data\futbolmarket.mv.db, data\futbolmarket.trace.db -ErrorAction SilentlyContinue
```

También se pierden los usuarios. El administrador se recrea en el próximo arranque con sus
variables, y los usuarios comunes se registran de nuevo.

### 2.2 Arranque sin token

```bash
./gradlew bootRun
```

Qué verificar:

- La aplicación levanta en el puerto 8080 y el registro muestra la advertencia (FR-041 y
  SC-012):

  ```text
  WARN ... StartupSync : Falta la credencial de Football-Data.org (FOOTBALL_DATA_TOKEN): la sincronización queda deshabilitada.
  ```

- No aparece ninguna línea del seeder ni ningún INSERT en `players`.
- `GET http://localhost:8080/players` responde `200` con `content` vacío y `totalElements: 0`:
  no existe ningún dato ficticio (HU1, escenario 7).

### 2.3 Arranque con token y catálogo vacío

Detener la aplicación y arrancarla con el token y el administrador (ver
[contracts/configuration.md](./contracts/configuration.md#configurar-el-token-en-local)).

Qué verificar:

1. La aplicación termina de arrancar (`Started FutbolMarketApplication`) **antes** de que
   termine la sincronización. Las líneas siguientes aparecen después (FR-033 y SC-018):

   ```text
   INFO  ... StartupSync        : El catálogo está vacío: se dispara una sincronización completa en segundo plano.
   INFO  ... SyncService        : Sincronización FULL (STARTUP) iniciada.
   INFO  ... SyncReportLogger   : PREMIER: procesada. Equipos 20/0/0, jugadores 545/0/3, partidos 380/0/0 (creados/actualizados/omitidos). Temporada 2502: 2026-08-21 a 2027-05-30, jornada 6.
   INFO  ... SyncReportLogger   : PREMIER: jugadores omitidos: Mahdi Nicoll-Jazuli (Chelsea FC, MISSING_POSITION), ...
   ...
   INFO  ... SyncReportLogger   : Sincronización FULL (STARTUP) terminada en 41 s: 5 ligas procesadas, 0 fallidas, 0 jugadores inactivados.
   ```

   Los números son los del 2026-10-06 y cambian con la fuente. `show-sql: true` intercala unas
   4.500 líneas de SQL: es lo esperado en `local`.
2. Mientras corre, `GET /players` responde enseguida con lo ya guardado, sin esperar (FR-036).
3. En menos de 2 minutos desde el arranque, `GET /players` tiene unos 2.634 jugadores en
   `totalElements` (SC-001 y SC-018).
4. El registro no contiene el token en ninguna línea (SC-011). Para comprobarlo, arrancar en
   Git Bash guardando la salida en un archivo y, con la aplicación ya detenida, buscar el token:

   ```bash
   ./gradlew bootRun 2>&1 | tee bootrun.log
   ```

   ```bash
   grep -c "$FOOTBALL_DATA_TOKEN" bootrun.log
   ```

   El resultado esperado es `0`. Después se borra `bootrun.log`.

### 2.4 Arranque con datos

Reiniciar con el token. El registro muestra
`El catálogo ya tiene jugadores: no se sincroniza al arrancar.` y no aparece ninguna línea de
sincronización (FR-033 y SC-018).

## 3. Escenarios HTTP por historia

```bash
BASE=http://localhost:8080
```

```bash
ADMIN_TOKEN=$(curl -s $BASE/auth/login -H 'Content-Type: application/json' -d '{"username":"<usuario-admin>","password":"<contrasena-admin>"}' | sed -E 's/.*"token":"([^"]+)".*/\1/')
```

```bash
USER_KEY=$(curl -s $BASE/auth/register -H 'Content-Type: application/json' -d '{"username":"hincha01","email":"hincha01@correo.com","password":"campeon2022"}' | sed -E 's/.*"apiKey":"([^"]+)".*/\1/')
```

### 3.1 Construcción del catálogo (HU1)

```bash
curl -s -X POST $BASE/players/sync -H "Authorization: Bearer $ADMIN_TOKEN"
```

- **Resultado esperado**: `200` con el informe.
  - `type` es `FULL` y `origin` es `MANUAL`.
  - Las cinco ligas están en `SUCCEEDED`, con su `season`.
  - Los equipos y los jugadores se cuentan en `created` en una base recién sincronizada al
    arrancar, o en `updated` si ya estaban.
  - `skippedPlayers` lista a los jugadores sin posición, con su equipo y `MISSING_POSITION`
    (HU1, escenarios 1 a 4).
- **Repetir el disparo**: no crea nada. `created` queda en 0 en las tres entidades y los
  existentes se cuentan en `updated` (HU1, escenario 10; SC-005). La segunda puede esperar hasta
  un minuto por el límite de consultas.

| Request | Resultado esperado |
|---|---|
| `GET $BASE/players?team=Liverpool%20FC` | Solo jugadores del Liverpool FC, con `dateOfBirth`, `nationality`, `teamCrest` y `active: true` (HU1, escenarios 5 y 6). |
| `GET $BASE/players?team=Liverpool` | Página vacía: el nombre corto no coincide. Era además el nombre del equipo ficticio (HU1, escenario 6; SC-002). |
| `GET $BASE/players?position=GOALKEEPER&league=PREMIER` | Solo arqueros de la Premier: `Goalkeeper` se tradujo a `GOALKEEPER` (HU1, escenario 2). |
| `GET $BASE/players?team=FC%20Bayern%20M%C3%BCnchen` | Jugadores del Bayern. El nombre se guarda tal cual, con la diéresis (caso borde). |
| `GET $BASE/players/{id}` de cualquier jugador | Todos los campos, incluido `active` (HU1, escenario 5). |
| `POST $BASE/players/sync` con `-H "X-API-Key: $USER_KEY"` | `403`, con el mensaje de falta de permiso. El registro no muestra ninguna línea de sincronización (HU1, escenario 8; SC-010). |
| `POST $BASE/players/sync` sin credencial | `401`, sin consultar la fuente (HU1, escenario 9). |

### 3.2 Temporadas y partidos (HU2)

No tienen endpoints en esta feature: se validan en la consola de H2 (sección 5) y en el informe.

- En el informe, cada liga tiene su `season` con `startDate`, `endDate` y `currentMatchday`. Por
  ejemplo, la Premier 2026/27 empieza el `2026-08-21` (HU2, escenario 1).
- `matches.created` de la Premier es 380 en la primera sincronización, o `updated` en las
  siguientes (HU2, escenario 2; SC-004).
- **Un partido programado que pasa a jugado**: en una sincronización posterior a un fin de semana
  de fecha, los partidos que estaban en `TIMED` aparecen en `FINISHED` con su resultado, y la
  cantidad de partidos no cambia (HU2, escenarios 3 y 4).
- Los partidos postergados, suspendidos o cancelados se guardan con ese estado (HU2, escenario
  5). Los partidos con un equipo desconocido (escenario 6) y el cambio de temporada (escenario 7)
  los cubren `LeagueSyncTest` y `SyncWriteServiceIT`.

### 3.3 Fallas de la fuente (HU3)

| Escenario | Cómo se prueba | Resultado esperado |
|---|---|---|
| Sin credencial | Arrancar sin `FOOTBALL_DATA_TOKEN` y disparar como admin | `503`, con el mensaje de sincronización deshabilitada. El catálogo responde con lo guardado (HU3, escenario 7; SC-012). |
| Credencial rechazada | Arrancar con `FOOTBALL_DATA_TOKEN=token-invalido` y disparar | `200`. Las cinco ligas en `FAILED` con el motivo del 403 y `inactivationApplied: false`. `GET /players` muestra lo mismo que antes (HU3, escenario 8). El registro no contiene `token-invalido` (escenario 9). |
| Fuente caída | Cortar la conexión a internet y disparar | Las cinco ligas en `FAILED` con `No se pudo conectar con la fuente.` o `La fuente no respondió a tiempo.`. Las consultas del catálogo responden igual (HU3, escenarios 1 y 4; SC-007). |
| Falla de una sola liga, partidos fallidos después de equipos bien, 429 con reintento, liga sin equipos | Automatizados | Los cubren `FootballDataAdapterTest`, `SyncServiceTest` y `SyncWriteServiceIT` (HU3, escenarios 2, 3, 5 y 6; SC-008). |

### 3.4 Cambios entre sincronizaciones (HU4)

Los cambios reales de planteles tardan semanas. Para verlos en local, se simulan desde la consola
de H2 (sección 5) y después se dispara una completa:

1. **Reactivación** (escenarios 4 y 11). Elegir un jugador y anotar su `id`. Ejecutar
   `UPDATE PLAYERS SET ACTIVE = FALSE WHERE ID = <id>`. Después:
   - `GET /players/<id>` muestra `active: false` (escenario 10);
   - el listado filtrado por su equipo no lo incluye (escenario 9).

   Tras la completa, el informe lo lista en `reactivatedPlayers` y vuelve a aparecer en el
   listado.
2. **Inactivación** (escenarios 2 y 3). Ejecutar
   `UPDATE PLAYERS SET EXTERNAL_ID = '999999999' WHERE ID = <otro id>`. Así, ese registro ya no
   corresponde a ningún jugador de la fuente.
   - Después de una sincronización **de una sola liga**, sigue activo e
     `inactivationApplied` es `false`.
   - Después de una **completa** con las cinco ligas bien, aparece en `inactivatedPlayers`,
     conserva su equipo y su detalle sigue respondiendo con `active: false`.
   - El jugador real se vuelve a crear con su `externalId` original.
3. Los traspasos, los datos corregidos, la posición que se conserva, el jugador en dos planteles
   y el equipo que desciende (escenarios 1, 5, 6, 7 y 8) los cubren `LeagueSyncTest`,
   `SquadAssignmentTest` y `SyncWriteServiceIT`.

### 3.5 Una sola liga, concurrencia y corrida semanal (HU5)

| Request o acción | Resultado esperado |
|---|---|
| `POST $BASE/players/sync?league=SERIE_A` como admin | `200` con `type: SINGLE_LEAGUE` y una sola liga en `leagues`. `inactivationApplied` es `false`. Las otras cuatro no cambian (escenario 2). |
| `POST $BASE/players/sync?league=MLS` como admin | `400`, con `La liga 'MLS' no es una de las admitidas: ...`. No se consulta la fuente (escenario 3). |
| Dos disparos completos casi simultáneos, desde dos terminales | El segundo responde `409` con `Ya hay una sincronización en curso.` (escenario 4). Lo cubre además `SyncControllerIT` de forma determinística. |
| Disparar como admin mientras corre la sincronización de arranque (2.3) | `409` (escenario 9). |
| Corrida semanal | `FootballDataPropertiesTest` y `SyncSchedulerTest` (escenarios 1, 5 y 6). Para verla en vivo, adelantar el cron como indica [contracts/configuration.md](./contracts/configuration.md#probar-la-corrida-programada-sin-esperar-al-lunes): el registro muestra `Sincronización FULL (WEEKLY) iniciada.` y el informe. |
| Arranque con el catálogo vacío o con datos | Secciones 2.3 y 2.4 (escenarios 7 y 8). |

## 4. Swagger

1. Abrir `http://localhost:8080/swagger-ui.html`.
2. Aparece la etiqueta **Sincronización** con `POST /players/sync`:
   - lleva candado;
   - tiene el parámetro `league` con los cinco valores;
   - documenta las respuestas 200, 400, 401, 403, 409 y 503.
3. En `GET /players`, el esquema de `PlayerResponse` muestra `dateOfBirth`, `nationality`,
   `teamCrest` y `active`.
4. **Authorize** con el token del administrador (`bearerAuth`) y ejecutar `POST /players/sync`
   con `league=LIGUE_1`: responde `200` con el informe.
5. **Authorize** con la clave de un usuario común (`apiKeyAuth`) y ejecutar lo mismo: responde
   `403`.

## 5. Consola de H2

Abrir `http://localhost:8080/h2-console` con la URL `jdbc:h2:file:./data/futbolmarket`, el
usuario `sa` y la contraseña vacía. Después de una completa:

```sql
SELECT COUNT(*) FROM TEAMS;                                   -- 96 al 2026-10-06
SELECT COUNT(*) FROM PLAYERS WHERE ACTIVE;                    -- unos 2.634
SELECT COUNT(*) FROM PLAYERS WHERE EXTERNAL_ID LIKE 'premier-%';  -- 0: no queda nada del dataset ficticio
SELECT LEAGUE, EXTERNAL_ID, START_DATE, END_DATE, CURRENT_MATCHDAY FROM SEASONS;
SELECT S.LEAGUE, COUNT(*) FROM MATCHES M JOIN SEASONS S ON M.SEASON_ID = S.ID GROUP BY S.LEAGUE;
SELECT EXTERNAL_ID, UTC_DATE, STATUS, FULL_TIME_HOME, FULL_TIME_AWAY, WINNER FROM MATCHES WHERE STATUS = 'FINISHED' LIMIT 5;
SELECT NAME, CREST FROM TEAMS WHERE NAME = 'Liverpool FC';
```

Qué verificar:

- `STATUS`, `WINNER` y `SEASONS.LEAGUE` son texto, sin `CHECK`.
- `EXTERNAL_ID` es único en las cuatro tablas.
- Ninguna columna contiene el token.
- Al repetir las cuentas después de otra completa, ninguna cantidad baja (SC-006).

## 6. Cobertura de los criterios de éxito

| Criterio | Dónde se valida |
|---|---|
| SC-001 | 2.3 y 3.1: unos 2.634 jugadores y 96 equipos. Sección 5. |
| SC-002 | 2.2 (catálogo vacío sin token), 3.1 (`team=Liverpool` vacío) y la sección 5 (`premier-%`). |
| SC-003 | 3.1: el disparo manual responde en menos de 2 minutos, con el contador libre o con una espera. |
| SC-004 | 3.2 y la sección 5: 380 partidos en la Premier. |
| SC-005 | 3.1 (repetición) y `SyncWriteServiceIT`. |
| SC-006 | La sección 5 (cuentas repetidas) y `SyncWriteServiceIT`. |
| SC-007 | 3.3 (fuente caída) y 2.3 (consultas durante la sincronización). |
| SC-008 | `SyncWriteServiceIT` y `SyncServiceTest`. |
| SC-009 | 3.4, paso 2, y `SyncRunTest`. |
| SC-010 | 3.1 (`403` y `401`) y `SyncControllerIT`. |
| SC-011 | 2.3, paso 4; 3.3 (token inválido); `SyncControllerIT` con un token al azar; y que ningún archivo del repositorio contenga un token. |
| SC-012 | 2.2 y 3.3 (sin credencial), además de `SyncDisabledIT`. |
| SC-013 | `FootballDataPropertiesTest` (próximo lunes a las 04:00 de Argentina) y `SyncSchedulerTest`. Opcionalmente, 3.5 con el cron adelantado. |
| SC-014 | 3.5 (dos disparos) y el 409 determinístico de `SyncControllerIT`. |
| SC-015 | 3.1 (omitidos), 3.4 (reactivados e inactivados) y `LeagueSyncTest`, `SquadAssignmentTest` y `SyncRunTest`. |
| SC-016 | 3.1: escudo, fecha de nacimiento y nacionalidad en el listado y el detalle. |
| SC-017 | 3.4: el inactivo queda fuera del listado y su detalle responde con `active: false`. Además, `PlayerRepositoryIT` y `PlayerControllerIT`. |
| SC-018 | 2.3 y 2.4. |
