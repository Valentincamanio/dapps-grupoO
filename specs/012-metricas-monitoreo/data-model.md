# Modelo de datos: Métricas y endpoints de monitoreo

**Feature**: `012-metricas-monitoreo` | **Plan**: [plan.md](./plan.md) |
**Research**: [research.md](./research.md)

**No hay entidades ni tablas nuevas.** Las métricas viven en memoria y se pierden al reiniciar; los
gauges y el indicador `sync` leen datos que ya existen (`app_user`, `market_orders`,
`last_full_syncs`). Este documento es el catálogo de lo que se publica: métricas, etiquetas con sus
valores posibles e indicadores de salud con sus estados y detalles.

## 1. Etiquetas

Todas las etiquetas tienen un conjunto **cerrado** de valores (FR-019). Ninguna lleva ids,
usernames, emails, montos, cantidades, nombres de liga ni mensajes de error.

| Etiqueta | Valores posibles | Origen del valor |
|----------|------------------|------------------|
| `application` | `futbol-market` | `management.metrics.tags.application` (todas las series, FR-002) |
| `operation` | `buy`, `sell`, `cancel` | el método interceptado de `MarketOrderService` |
| `result` (órdenes) | `success`, `rejected` | retorno normal / excepción 409, 404 o 403 del dominio |
| `result` (logins) | `success`, `failure` | retorno normal / `InvalidCredentialsException` |
| `trigger` | `manual`, `scheduled`, `startup` | `RecalculationOrigin`: `MANUAL`, `WEEKLY`, `STARTUP` (`STRATEGY_CHANGE` → `manual`, no ocurre por este camino) |
| `result` (recálculo) | `success`, `failure`, `blocked` | informe sin / con interrupción; excepción del candado |
| `source` | `football_data`, `whoscored` | `SyncSource` del modelo, en minúsculas |
| `type` | `full`, `single_league` | `SyncType` del modelo, en minúsculas |
| `result` (sync) | `success`, `failure`, `in_progress` | `report.succeeded()`; `SyncInProgressException` |

Las métricas HTTP de la plataforma (`http.server.requests`) llevan además `method`, `status`,
`outcome`, `exception`, `error` y `uri`. `uri` es la plantilla de la ruta (`/players/{id}`); sin
handler, Spring usa un valor fijo (`NOT_FOUND`, `REDIRECTION`, `UNKNOWN`, `root`), y Boot corta en
100 valores distintos (`management.metrics.web.server.max-uri-tags`) como red de seguridad (FR-004).

## 2. Catálogo de métricas propias

Prefijo `futbolmarket`; nombres en inglés, descripciones en español visibles en
`/actuator/metrics/{nombre}` (FR-016). La columna "Prometheus" es el nombre que publica el registro
de Micrometer en `/actuator/prometheus` (puntos → guiones bajos, sufijos por tipo).

| Métrica | Tipo | Etiquetas | Series | Prometheus | Descripción |
|---------|------|-----------|--------|------------|-------------|
| `futbolmarket.market.orders` | contador | `operation`, `result` | 3 × 2 = 6 | `futbolmarket_market_orders_total` | Órdenes de compra, venta y cancelación pedidas por usuarios, aceptadas o rechazadas por el mercado. |
| `futbolmarket.quotes.recalculation` | timer | `trigger`, `result` | 3 × 3 = 9 | `futbolmarket_quotes_recalculation_seconds_count`, `_sum`, `_max` | Duración de cada recálculo de cotizaciones, según qué lo disparó y cómo terminó. |
| `futbolmarket.sync` | timer | `source`, `type`, `result` | 2 × 2 × 3 = 12 | `futbolmarket_sync_seconds_count`, `_sum`, `_max` | Duración de cada sincronización con una fuente externa, completa o de una liga, y cómo terminó. |
| `futbolmarket.auth.logins` | contador | `result` | 2 | `futbolmarket_auth_logins_total` | Intentos de inicio de sesión, correctos o fallidos. |
| `futbolmarket.auth.registrations` | contador | — | 1 | `futbolmarket_auth_registrations_total` | Cuentas registradas con éxito. |
| `futbolmarket.users.registered` | gauge | — | 1 | `futbolmarket_users_registered` | Cantidad actual de cuentas registradas, incluida la del administrador. |
| `futbolmarket.market.orders.open` | gauge | — | 1 | `futbolmarket_market_orders_open` | Cantidad actual de órdenes abiertas del mercado (pendientes o parcialmente ejecutadas). |

A cada serie se le suma `application="futbol-market"`.

**Registro en cero (FR-017)**: las 30 series de contadores y timers se registran en
`BusinessMetrics.bindTo(registry)`, antes del primer caso de uso. Los gauges existen desde que se
registran y valen lo que dice la base.

**Timers**: publican `count`, `sum` y `max`; sin histograma ni percentiles (solo
`http.server.requests` tiene histograma, Assumptions).

### Cuándo se registra cada una

El detalle completo está en research D6. Resumen por caso:

| Caso | Efecto |
|------|--------|
| Compra/venta/cancelación aceptada | `orders{operation, result=success}` +1 |
| Rechazada por el dominio (409, 404, 403 del modelo) | `orders{operation, result=rejected}` +1; la excepción llega intacta al advice |
| Error interno (500) o rechazo del filtro / 400 de forma | nada |
| Login correcto / usuario inexistente o contraseña incorrecta | `logins{result=success}` / `logins{result=failure}` +1 |
| Registro exitoso / rechazado | `registrations` +1 / nada |
| Recálculo terminado / interrumpido por una semana que no se guardó | `recalculation{trigger, result=success}` / `result=failure`, con su duración |
| Recálculo rechazado por el candado (otro recálculo o una sincronización) | `recalculation{trigger, result=blocked}` |
| Recálculo que aborta con una excepción | `recalculation{trigger, result=failure}`, duración hasta la falla |
| Arranque sin semanas cerradas sin cotizar | nada |
| Sincronización sin ligas fallidas / sin corte | `sync{source, type, result=success}` |
| Con ligas fallidas / con corte / que aborta | `sync{source, type, result=failure}` |
| Pedido de una fuente con el candado tomado | `sync{source, type, result=in_progress}` |
| Football-Data sin token (503) | nada |
| Cadena (cron o puesta al día) rechazada por el candado | nada (cada fuente de una cadena que sí corre se mide por separado) |

### Gauges: de dónde leen

| Gauge | Lectura | Consulta |
|-------|---------|----------|
| `futbolmarket.users.registered` | `AppUserRepository.count()` | `count()` heredado de `JpaRepository` → `select count(*) from app_user` |
| `futbolmarket.market.orders.open` | `MarketOrderRepository.countOpen()` | derived query `countByStatusIn(statuses)` con los `OrderStatus` que cumplen `isOpen()` (`PENDING`, `PARTIAL`); usa `ix_market_orders_status_created` |

Las órdenes iniciales del sistema suman al gauge mientras estén abiertas, pero no a
`futbolmarket.market.orders` (no pasan por `MarketOrderService`).

## 3. Agregados del modelo (cambios sin persistencia)

| Elemento | Cambio | Regla |
|----------|--------|-------|
| `SyncReport.succeeded()` | nuevo | `failedLeagues()` vacío. `lastFullSync()` pasa a ser `type == FULL && succeeded()`. |
| `StatsSyncReport.succeeded()` | nuevo | `cut == null`. `lastFullSync()` pasa a ser `type == FULL && succeeded()`. |
| `CatchUpPolicy.freshness(Optional<LastFullSync>, Instant)` | nuevo | vacío → `NEVER`; terminó hace ≤ `maxAge` → `UP_TO_DATE`; hace más → `STALE`. Mismo `isUpToDate` que `decide`. |
| `SyncFreshness` | enum nuevo | `NEVER`, `UP_TO_DATE`, `STALE`. |

## 4. Indicadores de salud propios

Los dos devuelven solo `UP` o `UNKNOWN`, nunca `DOWN` ni `OUT_OF_SERVICE` (FR-042). La forma JSON
exacta y los ejemplos están en [contracts/health-indicators.md](./contracts/health-indicators.md).

### `footballData`

| Condición | Estado | Detalles (solo `ADMIN`) |
|-----------|--------|-------------------------|
| `futbolmarket.football-data.token` vacío o en blanco | `UNKNOWN` | `tokenConfigured: false` |
| Token presente | `UP` | `tokenConfigured: true` |

Nunca incluye el token ni una parte de él (FR-040). No llama a Football-Data.

### `sync`

Por fuente (`football_data`, `whoscored`), a partir de la última completa exitosa
(`LastFullSyncRepository.find(source)`) y del plazo `futbolmarket.sync.catch-up-after` (hoy 7 días):

| `SyncFreshness` | Detalle de la fuente |
|-----------------|----------------------|
| `NEVER` | `synchronized: false` |
| `UP_TO_DATE` | `synchronized: true`, `lastSuccessfulFullSync: <ISO-8601>`, `stale: false` |
| `STALE` | `synchronized: true`, `lastSuccessfulFullSync: <ISO-8601>`, `stale: true` |

Estado del componente: `UP` si las dos fuentes están `UP_TO_DATE`; `UNKNOWN` si alguna es `NEVER`
o `STALE`. Además publica `catchUpAfter` (el plazo, como duración ISO-8601). Borde: con
exactamente `catchUpAfter` de antigüedad, la fuente está al día (misma regla que la puesta al día
al arrancar).

### Agregado de `/actuator/health`

Componentes: `db`, `diskSpace`, `ping` (de la plataforma), `footballData`, `sync`. El agregador por
defecto (orden `DOWN, OUT_OF_SERVICE, UP, UNKNOWN`) toma el primero del orden que aparezca: con los
de la plataforma en `UP` y los propios en `UNKNOWN`, el agregado es `UP` y la respuesta, 200.

## 5. Endpoints expuestos

| Endpoint | Operaciones | Contenido |
|----------|-------------|-----------|
| `health` | `GET /actuator/health`, `GET /actuator/health/{componente}` | estado agregado; estado y detalles por componente según rol |
| `info` | `GET /actuator/info` | `build` (`name`, `description`, `version`, `artifact`, `group`, `time`) y `java` |
| `metrics` | `GET /actuator/metrics`, `GET /actuator/metrics/{nombre}` | nombres; mediciones, etiquetas disponibles y descripción |
| `prometheus` | `GET /actuator/prometheus` | formato de exposición de texto de Prometheus |
| `loggers` | `GET /actuator/loggers`, `GET`/`POST /actuator/loggers/{nombre}` | niveles configurados y efectivos; cambio en caliente (se pierde al reiniciar) |

Raíz `GET /actuator`: lista solo estos cinco. Acceso por rol en
[contracts/actuator-access.md](./contracts/actuator-access.md).
