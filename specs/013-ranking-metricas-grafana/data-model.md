# Data model: Ranking para alta frecuencia, métricas del mercado y dashboard de Grafana

**Feature**: `013-ranking-metricas-grafana` | **Plan**: [plan.md](./plan.md) | **Research**: [research.md](./research.md)

No hay entidades, tablas, índices ni migraciones nuevas. Todo lo de esta feature vive en memoria
(cachés y contadores) o en archivos versionados (Grafana). Las entidades del mercado (`Trade`,
`BookOutcome`) y del ranking (`RankingPage`, `Quote`) no cambian: se leen.

---

## 1. Cachés de cotizaciones

### 1.1 Configuración (`QuoteCacheProperties`, prefijo `futbolmarket.cache`)

| Propiedad | Tipo | Por defecto | Validación al arrancar (FR-003) |
|---|---|---|---|
| `futbolmarket.cache.ranking.maximum-size` | `Long` | `500` | no nulo y > 0 |
| `futbolmarket.cache.ranking.expire-after-write` | `Duration` | `10m` | no nulo y > 0 |
| `futbolmarket.cache.current.maximum-size` | `Long` | `5000` | no nulo y > 0 |
| `futbolmarket.cache.current.expire-after-write` | `Duration` | `10m` | no nulo y > 0 |

- Un valor inválido corta el arranque con un mensaje en español que nombra la propiedad, por
  ejemplo: "El tope del caché del ranking (futbolmarket.cache.ranking.maximum-size) tiene que ser
  mayor que cero."
- Los valores por defecto viven en `application.yaml`, y los perfiles `local` y `test` no los pisan.
- `spring.cache.type=none` (propiedad estándar de Boot) apaga los dos cachés. **Solo se usa para
  la variante "sin caché" de la medición** (research R7).

### 1.2 Cachés

| Caché | Constante | Clave | Valor | Qué lo llena | Tope / vencimiento |
|---|---|---|---|---|---|
| `quotes-ranking` | `CacheConfig.QUOTES_RANKING` | `SimpleKey(PlayerFilter, page, size)` (la de hoy) | `RankingPage` | `QuoteQueryService.getRanking` | `ranking.*` |
| `quotes-current` | `CacheConfig.QUOTES_CURRENT` | `playerId` (`key = "#playerId"`, la de hoy) | `Quote` | `QuoteQueryService.getCurrent` | `current.*` |

**Entrada del caché**: tiene la clave y el valor ya calculados y el momento en que se guardó.

Una entrada **deja de servirse** cuando pasa una de estas cosas:
- **Vaciado**: `QuoteCacheInvalidator.invalidateAll()`, después del commit de un recálculo, una
  recotización o un cambio de la estrategia activa. Saca todas las entradas de los dos cachés y
  sube la generación.
- **Tope**: al pasarse de `maximum-size`, Caffeine descarta las menos usadas (W-TinyLFU) en su
  mantenimiento.
- **Vencimiento**: pasado `expire-after-write` desde que se guardó, la entrada ya no se devuelve.

Hay tres casos que nunca se guardan:
- un pedido inválido, que se rechaza en el controller antes de llegar al servicio (FR-007);
- un 404, porque el método lanza y no hay `put`;
- el `put` de una consulta cuya búsqueda se hizo con una generación anterior a la vigente
  (FR-005).

### 1.3 Generación (`GenerationGuardedCaffeineCache`)

| Estado | Tipo | Significado |
|---|---|---|
| `generation` | `long` (vía `AtomicLong`) | Sube en cada `clear()` / `invalidate()` |
| `lookupGeneration` | `ThreadLocal<Long>` | La generación de la última búsqueda sin entrada de este hilo. No hay valor si la última búsqueda encontró la entrada o si ya se consumió en un `put`. |
| `lock` | `ReentrantReadWriteLock` | Lectura para el par comprobación-`put`, y escritura para el par incremento-vaciado |

Transiciones (por hilo):

```text
get(k) sin entrada        → lookupGeneration = generation
get(k) con entrada        → lookupGeneration = (vacío)
put(k, v) [lock lectura]  → g = lookupGeneration; lookupGeneration = (vacío)
                            si g vacío o g == generation → guarda; si no → descarta
clear() [lock escritura]  → generation++ ; invalidateAll()
```

---

## 2. Métricas

### 2.1 Métricas de negocio nuevas (`BusinessMetrics`)

| Constante | Nombre | Tipo | Etiqueta | Incremento por cada `Trade` confirmado | Descripción |
|---|---|---|---|---|---|
| `TRADES` | `futbolmarket.market.trades` | contador | `counterparty` | `1` | Operaciones ejecutadas en el mercado: cada cruce confirmado entre una compra y una venta. |
| `TOKENS_TRADED` | `futbolmarket.market.tokens.traded` | contador | `counterparty` | `quantity` | Tokens que cambiaron de mano en las operaciones ejecutadas. |
| `CREDITS_TRADED` | `futbolmarket.market.credits.traded` | contador | `counterparty` | `total.doubleValue()` | Créditos pagados en las operaciones ejecutadas. |

**`TradeCounterparty`** (enum en `observability/`):

| Valor | `tag()` | `from(OwnerType)` |
|---|---|---|
| `INITIAL_ORDER` | `initial_order` | `OwnerType.ADMIN`: la venta vino de la orden inicial del superusuario |
| `USER` | `user` | `OwnerType.USER`: la venta vino de un usuario |

Reglas:
- `bindTo` da de alta las 3 × 2 = **6 series en cero**, y el catálogo pasa de 30 a 36 series
  (FR-017).
- El total de cada métrica es la suma de sus dos series (FR-013).
- Se registra solo si `buy` o `sell` volvió bien, y por eso después del commit (FR-014 y FR-015).
  `cancel` nunca registra (FR-016).
- Una falla al registrar se loguea en `WARN` y no cambia el resultado (FR-021).
- No hay etiquetas por jugador, liga ni usuario (FR-020). Las métricas de la 012 no cambian
  (FR-022).

### 2.2 Métricas de los cachés (autoconfiguración de Boot y Micrometer)

Son seis por caché, con las etiquetas `cache`, `name`, `cache_manager="cacheManager"` y
`application`:

| Métrica Prometheus | Tipo | Etiqueta extra | Uso |
|---|---|---|---|
| `cache_gets_total` | contador | `result` = `hit` \| `miss` | Aciertos y fallos (FR-008). Es la base del índice de aciertos. |
| `cache_evictions_total` | contador | — | Descartes por tope o vencimiento (no cuenta los vaciados) |
| `cache_eviction_weight_total` | contador | — | Peso descartado (cada entrada pesa 1) |
| `cache_puts_total` | contador | — | Cargas de Caffeine. Con `@Cacheable` queda en 0 (research R4) y no se usa. |
| `cache_size` | gauge | — | Entradas guardadas (estimado de Caffeine). Nunca supera el tope después del mantenimiento (SC-004). |

Contrato completo: [contracts/metrics.md](./contracts/metrics.md).

---

## 3. Grafana (archivos versionados)

| Archivo | Contenido | Claves fijas |
|---|---|---|
| `monitoring/grafana/provisioning/datasources/prometheus.yaml` | El data source Prometheus | `uid: futbol-market-prometheus` y `url: http://localhost:9090` |
| `monitoring/grafana/provisioning/dashboards/futbol-market.yaml` | El provider de archivos | `options.path: $FUTBOLMARKET_GRAFANA_DASHBOARDS` y `folder: futbol-market` |
| `monitoring/grafana/dashboards/futbol-market.json` | El dashboard | `uid: futbol-market`, `schemaVersion: 39` y cada panel con `datasource.uid` = el del data source |
| `monitoring/grafana/start-grafana.ps1` | El lanzador | Parámetro `-GrafanaHome` (o `$env:GRAFANA_HOME`) |
| `monitoring/grafana/admin-credentials.example` | El ejemplo sin contraseña | `admin_user=admin` y `admin_password=` |

Hay dos cosas locales que git ignora:
- `monitoring/grafana/admin-credentials`: `clave=valor` con `admin_user` y `admin_password`. Se
  aplica solo la primera vez que Grafana crea su base.
- `monitoring/grafana/data/`: la base sqlite de Grafana, los logs y los plugins.

Contrato completo: [contracts/grafana-dashboard.md](./contracts/grafana-dashboard.md).
