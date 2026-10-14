# Guía de validación: Scraping de WhoScored

**Rama**: `feature/scraping-whoscored` | **Fecha**: 2026-10-13 | **Plan**: [plan.md](./plan.md)

Esta guía prueba la feature de punta a punta. Los detalles están en otros documentos:

- Requests y respuestas: [contracts/sync-api.yaml](./contracts/sync-api.yaml).
- Las páginas de WhoScored: [contracts/whoscored-pages.md](./contracts/whoscored-pages.md).
- El snapshot: [contracts/snapshot-format.md](./contracts/snapshot-format.md).
- Propiedades y variables de entorno: [contracts/configuration.md](./contracts/configuration.md).
- Reglas del modelo: [data-model.md](./data-model.md).

## Precondiciones

- Java 21. No hacen falta Docker, una base externa ni ningún otro servicio.
- Todos los comandos se ejecutan desde `backend/`.
- La suite automatizada no necesita conexión a internet: nunca consulta WhoScored ni
  Football-Data.
- Para las secciones 2 a 7:
  - el administrador configurado con las tres variables `FUTBOLMARKET_AUTH_ADMIN_*` (ver
    [005: contracts/configuration.md](../005-integracion-football-data/contracts/configuration.md#configurar-el-token-en-local));
  - el token de Football-Data en `FOOTBALL_DATA_TOKEN`, salvo donde se prueba sin él;
  - **en la máquina de Lucas, AVG**: agregar `https://www.whoscored.com/*` a sus excepciones, o
    arrancar en la sesión con
    `JAVA_TOOL_OPTIONS=-Djavax.net.ssl.trustStore=NONE -Djavax.net.ssl.trustStoreType=Windows-ROOT`
    ([contracts/configuration.md](./contracts/configuration.md#correr-contra-whoscored-en-la-máquina-de-lucas-avg)).
    Sin eso, cada página falla con `PKIX path building failed` y la corrida se corta por
    bloqueo en la primera.
- WhoScored no permite el scraping en sus términos de uso. Las pruebas contra el sitio real
  (secciones 3 y 5.3) se hacen pocas veces y con el ritmo que trae la aplicación.
- Los ejemplos usan `curl` de Git Bash. En PowerShell hay que invocar `curl.exe`.
- `JwtServiceTest.unTokenConLaFirmaAlteradaSeRechazaComoInvalido` falla más o menos 1 de cada 16
  corridas en la máquina de Lucas. Si falla, se reintenta el build.

## 1. Suite automatizada

```bash
./gradlew test
```

```bash
./gradlew build
```

Resultado esperado: `BUILD SUCCESSFUL`. La suite cubre:

- **Unitarios de modelo**, sin Spring:
  - `ReportedMatchStats`:
    - un titular completo con 2 y 4 minutos agregados suma 96;
    - el reemplazado en el 70′ del reloj suma 72 y su reemplazo 24;
    - el expulsado en el 60′ suma 62 y una roja;
    - la doble amarilla cuenta como una amarilla y una roja;
    - el gol en contra no suma gol ni asistencia;
    - la asistencia de un pase con solo `ShotAssist` y la de un remate;
    - un suplente que no entró no tiene fila, aunque tenga una tarjeta.
  - `NormalizedName`: tildes, letras como `ø` o `ß`, mayúsculas y sufijos de club.
  - `TeamCrossing`:
    - el cruce por nombre confirmado por el calendario;
    - "Man Utd" cruzado solo por calendario;
    - el empate (`AMBIGUOUS`), el que no coincide (`NOT_FOUND`) y el nombre sin confirmar
      (`NOT_CONFIRMED`);
    - dos fechas reprogramadas toleradas;
    - un rival que contradice un cruce guardado.
  - `PlayerCrossing`:
    - por id de WhoScored;
    - por nombre en el equipo;
    - el desempate por edad;
    - el jugador transferido encontrado en todo el catálogo;
    - los homónimos ambiguos;
    - un jugador con otro id que nunca es candidato.
  - `LeagueStatsSync`:
    - pendientes;
    - partidos programados, postergados, de otra temporada y ya procesados;
    - las omisiones con su motivo;
    - qué página de fixtures conviene pedir.
  - `SnapshotLoad` y `StatsSnapshot`: los motivos para saltear, que la base gana ante un
    conflicto, el orden canónico y que la combinación conserva las entradas vigentes.
  - `CatchUpPolicy`: 8, 7 y 3 días, y sin ninguna, para cada fuente.
  - `StatsSyncRun` y `SyncReport.lastFullSync()`: una de una liga, una cortada y una con ligas
    fallidas no cuentan como completa exitosa.
  - `Team`, `Player` y `Match`: `crossWith` y que `updateFrom` conserva el cruce y la marca.
- **Adapter**, con `MockRestServiceServer` y fixtures reales recortadas:
  - los tres extractores (llaves dentro de strings, apóstrofos escapados, posiciones vacías);
  - las métricas, con quites desde `tackleSuccessful`;
  - el rating del último minuto;
  - los headers;
  - una pausa de 4 s entre pedidos y ninguna antes del primero;
  - un 403 que se reintenta a los 30 s y responde bien;
  - una página sin bloque que falla dos veces y corta;
  - un timeout;
  - `matchCentreData: null`, que no es un rechazo.

  Ningún test duerme: usan `RecordingWhoScoredSleeper` y un `Clock` fijo.
- **Configuración**: los valores de `application.yaml`, y que un `request-interval` menor a 4 s
  o un `retry-wait` menor a 30 s impiden el arranque.
- **Unitarios de servicio**:
  - `StatsSyncService`:
    - el snapshot de todas las ligas antes del primer pedido;
    - sin pendientes no hay pedidos;
    - un bloqueo corta la corrida en todas las ligas;
    - una de una liga solo pide esa;
    - la última completa solo se registra si fue completa y sin corte;
    - el 409.
  - `SyncCoordinator`:
    - la cadena en orden;
    - sin token, saltea Football-Data y corre WhoScored;
    - con Football-Data fallida, corre WhoScored;
    - las tres decisiones de la puesta al día.
  - `StartupSync`, `SyncScheduler` y `SyncService`, con los cambios de la decisión de tests del
    plan.
  - `StatsSyncReportLogger`, `StatsSnapshotService` (con archivos temporales) y `SyncLock`.
- **Integración**, contra H2 en memoria:
  - los índices únicos de los ids de WhoScored, con varios `NULL`;
  - el default `false` de `stats_loaded`;
  - la unicidad de jugador y partido en `player_match_stats`;
  - `StatsWriteServiceIT`:
    - un partido que falla al guardar no deja ninguna fila;
    - **una sincronización de Football-Data posterior conserva los cruces y la marca**;
  - `SyncChainIT`: la cadena con los dos adapters mockeados, con un 409 en el medio.
- **End to end**, en `e2e/` (`StatsSyncControllerIT`):
  - 200 completa, de una liga y cortada;
  - 400, 401 y 403 sin consultar WhoScored;
  - 409 contra Football-Data y contra la cadena;
  - sin token de Football-Data, WhoScored corre igual;
  - con WhoScored bloqueando, el catálogo y `POST /players/sync` responden igual.

## 2. Levantar con el perfil local

### 2.1 La base local no se borra

A diferencia de la 005, la base de `backend/data/` se conserva. Al arrancar, `ddl-auto: update`:

- agrega `whoscored_id` a `teams`, `players` y `matches`, y `stats_loaded` a `matches`, con
  `false` en las filas existentes;
- crea las tablas `player_match_stats` y `last_full_syncs`;
- crea los índices nuevos.

Como `last_full_syncs` arranca vacía, el primer arranque considera que nunca hubo una completa y
pone al día las dos fuentes (HU3, escenario 9; [research.md](./research.md) D17).

### 2.2 Primer arranque con la base de la 005

Arrancar con el token y el administrador:

```powershell
$env:FOOTBALL_DATA_TOKEN = '<token>'; $env:FUTBOLMARKET_AUTH_ADMIN_USERNAME = '<usuario-admin>'; $env:FUTBOLMARKET_AUTH_ADMIN_EMAIL = '<correo-admin>'; $env:FUTBOLMARKET_AUTH_ADMIN_PASSWORD = '<contrasena-admin>'; ./gradlew bootRun
```

Qué verificar:

1. La aplicación termina de arrancar (`Started FutbolMarketApplication`) **antes** de que
   empiece cualquier sincronización (FR-034 y SC-011). Después aparece:

   ```text
   INFO  ... SyncCoordinator    : Puesta al día al arrancar: no hay una sincronización completa exitosa de Football-Data.org registrada: se corren las dos sincronizaciones en segundo plano.
   INFO  ... SyncService        : Sincronización FULL (CATCH_UP) iniciada.
   ...
   INFO  ... SyncReportLogger   : Sincronización FULL (CATCH_UP) terminada en 41 s: 5 ligas procesadas, 0 fallidas, 0 jugadores inactivados.
   INFO  ... StatsSyncService   : Sincronización de WhoScored FULL (CATCH_UP) iniciada.
   WARN  ... StatsSyncService   : No se encontró el snapshot snapshot/whoscored-stats.jsonl: se scrapea todo lo pendiente.
   ```

   Si el repositorio ya trae el snapshot, en lugar del WARN se cargan sus partidos y se scrapean
   solo los que faltan (sección 5.3).
2. Mientras corre, `GET /players` responde enseguida (FR-046).
3. Al terminar la de WhoScored aparece el informe, una línea por liga:

   ```text
   INFO  ... StatsSyncReportLogger : PREMIER: procesada. Partidos: 0 desde el snapshot y 60 por scraping; 0 omitidos; 0 equipos sin cruzar; 14 jugadores sin cruzar; 0 entradas del snapshot salteadas.
   INFO  ... StatsSyncReportLogger : PREMIER: jugadores sin cruzar: Mason Miley (Newcastle United FC, partido 537800, NOT_FOUND), ...
   ...
   INFO  ... StatsSyncReportLogger : Sincronización de WhoScored FULL (CATCH_UP) terminada en 2830 s: 330 partidos procesados (0 desde el snapshot y 330 por scraping), 6 omitidos, sin corte.
   ```

   Los números son ilustrativos. Sin snapshot, la primera vez tarda unos 47 minutos (SC-005:
   menos de 60).
4. Si WhoScored rechaza alguna página, queda un WARN con su ruta y el reintento:

   ```text
   WARN  ... WhoScoredClient    : WhoScored rechazó la página /matches/1983620/live (403, 5 KB): se reintenta en 30 s.
   ```

### 2.3 Arranque con las dos fuentes al día

Reiniciar. El registro muestra
`Las dos fuentes están al día: no se sincroniza al arrancar.` y ninguna línea de sincronización
(HU3, escenario 3).

### 2.4 Arranque sin token de Football-Data

Detener y arrancar sin `FOOTBALL_DATA_TOKEN`, con la última completa de Football-Data vencida o
sin ninguna (por ejemplo, borrando su fila desde la consola de H2, sección 7). Qué verificar
(HU3, escenario 8):

- la advertencia de siempre:
  `Falta la credencial de Football-Data.org (FOOTBALL_DATA_TOKEN): la sincronización queda deshabilitada.`;
- `Se omite Football-Data.org en la cadena: falta la credencial (FOOTBALL_DATA_TOKEN).`;
- la sincronización de WhoScored corre igual sobre lo guardado y, si no hay pendientes, no pide
  ninguna página.

### 2.5 Solo WhoScored vencida

Con la aplicación detenida, desde la consola de H2:

```sql
UPDATE LAST_FULL_SYNCS SET FINISHED_AT = DATEADD('DAY', -9, CURRENT_TIMESTAMP) WHERE SOURCE = 'WHOSCORED';
```

Al arrancar corre solo la de WhoScored, con origen `CATCH_UP` (HU3, escenario 4).

## 3. Prueba manual contra WhoScored con pocas páginas

Sirve para probar el adapter contra el sitio real sin esperar 45 minutos. Hace falta una base con
el catálogo sincronizado y **ningún partido de la Premier con estadísticas** (por ejemplo, una
base recién creada por la sección 2.2 antes de la primera corrida de WhoScored, o una de prueba
aparte).

1. Con la aplicación detenida, marcar como procesados todos los partidos terminados de la Premier
   salvo los dos más recientes:

   ```sql
   UPDATE MATCHES SET STATS_LOADED = TRUE
   WHERE STATUS = 'FINISHED'
     AND SEASON_ID IN (SELECT ID FROM SEASONS WHERE LEAGUE = 'PREMIER')
     AND ID NOT IN (SELECT M.ID FROM MATCHES M JOIN SEASONS S ON M.SEASON_ID = S.ID
                    WHERE S.LEAGUE = 'PREMIER' AND M.STATUS = 'FINISHED'
                    ORDER BY M.UTC_DATE DESC LIMIT 2);
   ```

2. Arrancar con `FUTBOLMARKET_FOOTBALLDATA_SYNC_ONSTARTUP=false`, así no corre la puesta al día,
   y disparar solo la Premier:

   ```bash
   curl -s -X POST "$BASE/matches/stats/sync?league=PREMIER" -H "Authorization: Bearer $ADMIN_TOKEN"
   ```

   Son 1 página de liga, las de fixtures necesarias para cruzar los 20 equipos (hasta 20) y 2 de
   partidos: unos 2 a 3 minutos. El registro muestra cada pedido con su pausa.
3. Verificar en el informe: `matchesScraped: 2`, `uncrossedTeams` vacío y los jugadores sin
   cruzar.
4. Verificar en la consola de H2 los 20 equipos cruzados y las filas de los dos partidos
   (sección 7). Comparar un jugador con la página del partido en el navegador: minutos, goles,
   asistencias, quites ("Tackles" de la pestaña Defensive) y rating.
5. **Antes de exportar un snapshot, deshacer la marca artificial**: si no, el snapshot llevaría
   partidos sin estadísticas como procesados.

   ```sql
   UPDATE MATCHES SET STATS_LOADED = FALSE
   WHERE STATS_LOADED AND ID NOT IN (SELECT DISTINCT MATCH_ID FROM PLAYER_MATCH_STATS);
   ```

## 4. Escenarios HTTP por historia

```bash
BASE=http://localhost:8080
```

```bash
ADMIN_TOKEN=$(curl -s $BASE/auth/login -H 'Content-Type: application/json' -d '{"username":"<usuario-admin>","password":"<contrasena-admin>"}' | sed -E 's/.*"token":"([^"]+)".*/\1/')
```

```bash
USER_KEY=$(curl -s $BASE/auth/register -H 'Content-Type: application/json' -d '{"username":"hincha06","email":"hincha06@correo.com","password":"campeon2022"}' | sed -E 's/.*"apiKey":"([^"]+)".*/\1/')
```

### 4.1 Estadísticas de los partidos terminados (HU1)

```bash
curl -s -X POST $BASE/matches/stats/sync -H "Authorization: Bearer $ADMIN_TOKEN"
```

- **Resultado esperado**: `200` con el informe.
  - `type` es `FULL`, `origin` es `MANUAL`, `cut` es `null` y hay cinco ligas en `PROCESSED`
    (HU1, escenario 19).
  - Si no había pendientes, todas las cuentas están en 0 y no se pidió ninguna página: el
    registro no muestra ningún pedido (HU1, escenario 14; SC-009).

| Request | Resultado esperado |
|---|---|
| `POST $BASE/matches/stats/sync?league=LIGUE_1` como admin | `200` con `type: SINGLE_LEAGUE` y una sola liga (HU1, escenario 16). |
| `POST $BASE/matches/stats/sync?league=MLS` como admin | `400` con `La liga 'MLS' no es una de las admitidas: ...`. El registro no muestra ningún pedido a WhoScored (HU1, escenario 17). |
| `POST $BASE/matches/stats/sync` con `-H "X-API-Key: $USER_KEY"` | `403` (HU1, escenario 18; SC-017). |
| `POST $BASE/matches/stats/sync` sin credencial | `401` (HU1, escenario 18). |
| `POST $BASE/matches/stats/sync` y, al terminar, revisar el registro | No arranca ninguna sincronización de Football-Data (HU1, escenario 20; FR-038). |
| `POST $BASE/players/sync` y, al terminar, revisar el registro | No arranca la de WhoScored (FR-038). |

Los escenarios de minutos, goles, asistencias, cruces y omisiones (HU1, escenarios 2 a 15) los
cubren los unitarios de la sección 1. Contra datos reales se verifican en la sección 7.

### 4.2 Concurrencia y cadena (HU3)

| Acción | Resultado esperado |
|---|---|
| Disparar `POST /players/sync` mientras corre `POST /matches/stats/sync` (dos terminales), o al revés | El segundo responde `409` con `Ya hay una sincronización en curso.` (FR-041). |
| Disparar cualquiera de los dos mientras corre la puesta al día de la sección 2.2 | `409`, también entre el paso de Football-Data y el de WhoScored (HU3, escenario 10). |
| Corrida del lunes | `SyncSchedulerTest` y `SyncChainIT`. Para verla en vivo, adelantar el cron ([contracts/configuration.md](./contracts/configuration.md#probar-la-corrida-programada-sin-esperar-al-lunes)): el registro muestra `Sincronización FULL (WEEKLY) iniciada.` y, al terminar, `Sincronización de WhoScored FULL (WEEKLY) iniciada.` (HU3, escenario 6). |
| Una completa manual de Football-Data con las cinco ligas bien, y después reiniciar | `LAST_FULL_SYNCS` tiene la fila de `FOOTBALL_DATA` con el fin de esa corrida, y al arrancar no se pone al día Football-Data (HU3, escenario 12). |
| `POST /players/sync?league=SERIE_A`, o `POST /matches/stats/sync?league=SERIE_A` | La fila de esa fuente en `LAST_FULL_SYNCS` no cambia (HU3, escenarios 13 y 14). |

### 4.3 Fallas y bloqueos (HU4)

Un bloqueo real no se puede provocar a voluntad. Lo cubren `WhoScoredAdapterTest`,
`StatsSyncServiceTest` y `StatsSyncControllerIT`. En vivo se puede ver así:

| Escenario | Cómo se prueba | Resultado esperado |
|---|---|---|
| WhoScored inaccesible | Con partidos pendientes, arrancar con `FUTBOLMARKET_WHOSCORED_BASEURL=https://whoscored.invalid` y disparar | A los dos intentos de la primera página (con 30 s en el medio), `200` con `cut` en esa página. Las ligas que faltaban figuran `NOT_REACHED` y no hay ningún otro pedido (HU4, escenario 3). |
| El resto del sistema con WhoScored caído | Con lo mismo, `GET /players` y `POST /players/sync` | Responden igual que siempre (HU4, escenario 7; SC-016). |
| Corrida cortada que se retoma | Repetir el disparo con la URL real | Procesa los pendientes sin volver a pedir los ya procesados (HU4, escenario 4; SC-009). |
| Aplicación detenida en medio de un partido | Detener con Ctrl+C durante el scraping y arrancar de nuevo | Ningún partido tiene filas a medias: la consulta de la sección 7 da 0 (HU4, escenario 5; SC-008). |

## 5. Snapshot

### 5.1 Generar el snapshot

Con la aplicación detenida:

```bash
./gradlew exportStatsSnapshot
```

Resultado esperado: `BUILD SUCCESSFUL` y el archivo `backend/snapshot/whoscored-stats.jsonl`
(HU2, escenario 7). La tarea no levanta el servidor web, no sincroniza nada y termina sola.

Qué verificar:

```bash
head -c 400 snapshot/whoscored-stats.jsonl
```

```bash
grep -c '"type":"stats"' snapshot/whoscored-stats.jsonl
```

```bash
grep -ciE 'password|apikey|api_key|token|username|email|@' snapshot/whoscored-stats.jsonl
```

- La primera línea es el encabezado del [contrato](./contracts/snapshot-format.md).
- La cuenta de `stats` es la de partidos con estadísticas (sección 7).
- El último `grep` da `0`: no hay usuarios, contraseñas, claves ni ningún otro dato (FR-029 y
  SC-014).

### 5.2 Determinismo

```bash
./gradlew exportStatsSnapshot
```

```bash
git diff --stat -- snapshot/
```

Sin cambios en la base, la segunda exportación deja el archivo idéntico: el diff está vacío (HU2,
escenario 8). Después de procesar una fecha nueva, el diff son solo las líneas de esos partidos y
los cruces nuevos (HU2, escenario 9):

```bash
git diff -- snapshot/ | grep -c '^-[^-]'
```

El resultado esperado es `0`: ninguna línea vigente se borra ni cambia (FR-031).

### 5.3 Base nueva con el snapshot

1. Con la aplicación detenida, apartar la base local (no se borra, así se puede volver):

   ```bash
   mv data data-respaldo
   ```

2. Arrancar con el token y el administrador. Qué verificar (HU2, escenario 1; SC-006):
   - la cadena corre: primero Football-Data reconstruye el catálogo y después WhoScored;
   - el informe de WhoScored muestra `snapshot: LOADED` en el registro, casi todos los partidos
     como "desde el snapshot" y solo los nuevos como "por scraping";
   - el registro no muestra ningún pedido a una página de partido que trae el snapshot;
   - las estadísticas están guardadas menos de 15 minutos después del fin de la de Football-Data.
3. Volver a la base original:

   ```bash
   rm -rf data && mv data-respaldo data
   ```

Lo mismo con el snapshot renombrado (`whoscored-stats.jsonl.bak`) prueba el escenario 5: queda
el WARN de snapshot faltante y la aplicación sigue funcionando. Un archivo con una línea rota
deja el WARN de snapshot ilegible y se ignora entero.

## 6. Swagger

1. Abrir `http://localhost:8080/swagger-ui.html`.
2. En la etiqueta **Sincronización** aparecen `POST /players/sync` y `POST /matches/stats/sync`,
   los dos con candado.
3. `POST /matches/stats/sync` tiene el parámetro `league` con los cinco valores y documenta 200,
   400, 401, 403 y 409. `POST /players/sync` sigue documentando también el 503.
4. **Authorize** con el token del administrador y ejecutar `POST /matches/stats/sync` con
   `league=LIGUE_1`: `200` con el informe.
5. **Authorize** con la clave de un usuario común y ejecutar lo mismo: `403`.
6. No hay ningún endpoint que devuelva estadísticas (FR-009).

## 7. Consola de H2

Abrir `http://localhost:8080/h2-console` con la URL `jdbc:h2:file:./data/futbolmarket`, el usuario
`sa` y la contraseña vacía. Después de una completa sin corte:

```sql
SELECT COUNT(*) FROM TEAMS WHERE WHOSCORED_ID IS NOT NULL;                -- 96 (SC-003a)
SELECT COUNT(*) FROM MATCHES WHERE STATUS = 'FINISHED' AND NOT STATS_LOADED;  -- solo los omitidos del informe (SC-001)
SELECT COUNT(*) FROM PLAYER_MATCH_STATS;
SELECT SOURCE, FINISHED_AT FROM LAST_FULL_SYNCS;
SELECT P.NAME, S.STARTER, S.MINUTES_PLAYED, S.GOALS, S.ASSISTS, S.TACKLES, S.YELLOW_CARDS, S.RED_CARDS, S.RATING, S.MAN_OF_THE_MATCH
FROM PLAYER_MATCH_STATS S JOIN PLAYERS P ON P.ID = S.PLAYER_ID JOIN MATCHES M ON M.ID = S.MATCH_ID
WHERE M.WHOSCORED_ID = '1983600' ORDER BY S.MINUTES_PLAYED DESC, P.NAME;
-- partidos procesados sin ninguna fila (posible solo si no se cruzó a nadie): tiene que dar 0 o casi
SELECT COUNT(*) FROM MATCHES M WHERE M.STATS_LOADED AND NOT EXISTS (SELECT 1 FROM PLAYER_MATCH_STATS S WHERE S.MATCH_ID = M.ID);
```

Qué verificar en el Leeds 4-1 Newcastle (1983600):

- Calvert-Lewin: titular, 80 minutos, 2 goles, 0 asistencias, 0 quites, 1 amarilla, rating 8.95
  y figura;
- James Justin: 97 minutos y 1 asistencia (la de un pase con solo `ShotAssist`);
- Muharemovic: 1 asistencia;
- Melvin Bard: suplente, 22 minutos;
- nadie suma un gol por el gol en contra del 31′;
- los suplentes que no entraron no tienen fila.

Además:

- `WHOSCORED_ID` es único en `TEAMS`, `PLAYERS` y `MATCHES`, con muchos `NULL`;
- un jugador nunca tiene dos filas en el mismo partido;
- al repetir las cuentas después de otra sincronización, ninguna baja y las filas del 1983600 no
  cambian (SC-008 y SC-010).

## 8. Cobertura de los criterios de éxito

| Criterio | Dónde se valida |
|---|---|
| SC-001 | Sección 7 (terminados sin estadísticas = omitidos) y `StatsSyncServiceTest`. |
| SC-002 | Informe de la sección 2.2 (jugadores sin cruzar contra apariciones) y `PlayerCrossingTest`. |
| SC-003 y SC-003a | Sección 7 (96 equipos cruzados) y `TeamCrossingTest`. |
| SC-004 | Corrida semanal en vivo (sección 4.2) o una fecha completa: menos de 15 minutos. |
| SC-005 | Sección 2.2 sin snapshot: menos de 60 minutos. |
| SC-006 | Sección 5.3. |
| SC-007 | `WhoScoredAdapterTest` (pausa de 4 s, una página a la vez) y el registro de la sección 3. |
| SC-008 | Sección 4.3 (detención) y `StatsWriteServiceIT`. |
| SC-009 | Secciones 4.1 y 4.3 y `StatsSyncServiceTest`. |
| SC-010 | Sección 7 (filas del 1983600 idénticas) y `StatsWriteServiceIT`. |
| SC-011 | Sección 2.2, paso 1. |
| SC-012 | Secciones 2.2 a 2.5, `CatchUpPolicyTest` y `SyncCoordinatorTest`. |
| SC-013 | Sección 4.2 y el 409 determinístico de `StatsSyncControllerIT` y `SyncChainIT`. |
| SC-014 | Secciones 5.1 y 5.2 y `StatsSnapshotTest`. |
| SC-015 | Informes de las secciones 2.2 y 4.3, y `StatsSyncReportLoggerTest`. |
| SC-016 | Sección 4.3 y `StatsSyncControllerIT`. |
| SC-017 | Sección 4.1 y `StatsSyncControllerIT`. |
