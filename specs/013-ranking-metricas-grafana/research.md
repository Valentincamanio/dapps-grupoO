# Research: Ranking para alta frecuencia, métricas del mercado y dashboard de Grafana

**Feature**: `013-ranking-metricas-grafana` | **Fecha**: 2026-11-24 | **Plan**: [plan.md](./plan.md)

Cada decisión sigue el formato Decisión / Por qué / Alternativas. Las fuentes son el código del
repositorio y el código fuente publicado de Spring Boot 4.1.1, Spring Framework 7.0.x, Micrometer y
Grafana. Lo que no se pudo confirmar en la fuente queda marcado como **a verificar en la
implementación**, con la tarea que lo verifica.

---

## Caché del ranking y de la cotización actual (US1)

### R1. Caffeine detrás de un `CaffeineCacheManager` con cachés fijos

**Decisión**: `config/CacheConfig` deja de crear un `ConcurrentMapCacheManager` y crea un
`QuoteCacheManager` propio. Es una subclase de `CaffeineCacheManager` y registra los dos cachés de
hoy con nombres fijos (`CacheConfig.QUOTES_CURRENT` = `quotes-current` y
`CacheConfig.QUOTES_RANKING` = `quotes-ranking`), cada uno con su propio `Caffeine`:

- `maximumSize(n)`: el tope de entradas (FR-001, FR-030). Al llegar al tope, Caffeine descarta con
  su política W-TinyLFU, que conserva las entradas más usadas.
- `expireAfterWrite(d)`: el vencimiento de seguridad contado desde que se guardó la entrada (FR-002).
- `recordStats()`: sin esto Micrometer no registra `cache.gets` ni `cache.evictions` (R4).
- `ticker(t)`: `Ticker.systemTicker()` en producción y un reloj controlable en los tests (R6).

Modo estático: el manager llama a `setCacheNames(List.of())` y después a `registerCustomCache` por
cada caché. En Spring Framework 7.0.x, `setCacheNames` con una colección no nula pasa el manager a
modo estático (`dynamic = false`). En ese modo `getCache("otro")` devuelve `null`, igual que hoy, y
`registerCustomCache` no cambia el modo. `getCacheNames()` devuelve las claves de `cacheMap`, donde
están los cachés registrados.

**Por qué**: es la decisión técnica que ya se tomó. Caffeine es el caché local de referencia en
Spring: tiene tope por tamaño, vencimiento y estadísticas, y Boot lo integra con Micrometer.
`registerCustomCache` permite valores distintos para cada caché (FR-030).

**Alternativas**:
- Seguir con `ConcurrentMapCacheManager` y limpiarlo con un `@Scheduled`: no da tope ni
  estadísticas.
- Un `LinkedHashMap` LRU propio: es reinventar Caffeine y no trae métricas.
- `CaffeineCacheManager.setCaffeine(...)` con una sola especificación: obligaría a usar los mismos
  valores en los dos cachés, y FR-030 pide valores propios.
- Redis: queda fuera de alcance.

### R2. Dependencias nuevas: `spring-boot-starter-cache` y `caffeine`

**Decisión**: se agregan dos dependencias a `backend/build.gradle`, sin versión, con la que fija el
BOM de Boot 4.1.1:

```groovy
implementation 'org.springframework.boot:spring-boot-starter-cache'
implementation 'com.github.ben-manes.caffeine:caffeine'   // 3.2.4 según el BOM de Boot 4.1.1
```

**Por qué** (constitución, Stack Tecnológico: "ninguna dependencia nueva sin avisar y sin
justificar"):
- Hoy el classpath no tiene `spring-context-support`, que es donde viven `CaffeineCacheManager` y
  `CaffeineCache`, ni `spring-boot-cache`, el módulo de Boot 4 con la autoconfiguración de caché y
  de sus métricas. Se comprobó con `gradlew dependencies --configuration runtimeClasspath`.
- `module/spring-boot-cache/build.gradle` (v4.1.1) declara
  `api("org.springframework:spring-context-support")`, así que el starter lo trae transitivamente.
  Caffeine no viene en el módulo: la aplicación lo tiene que agregar.
- El starter es el artefacto con el nombre real de Boot 4 (constitución: "no copiar dependencias de
  tutoriales de Boot 3 sin verificar el nombre"). Figura en el BOM 4.1.1 como
  `spring-boot-starter-cache`.

**Alternativas**:
- `org.springframework:spring-context-support` suelto, sin el starter: no trae
  `CacheMetricsAutoConfiguration`, así que habría que registrar los dos cachés en Micrometer a mano
  (R4). Tampoco trae el `NoOpCacheManager` para `spring.cache.type=none` (R7).
- `caffeine-jcache` o `jcache`: es más complejo y no aporta nada acá.

### R3. Garantía estricta ante una consulta en vuelo (FR-005, SC-003)

**El problema**: con `@Cacheable` sin `sync`, Spring hace `cache.get(key)`. Si no encuentra la
entrada, ejecuta el método y después hace `cache.put(key, value)`, todo en el mismo hilo
(`CacheAspectSupport.findCachedValue` → `doGet`, y después `performCachePut` → `doPut`). Si el
vaciado (`QuoteCacheInvalidator`, `@CacheEvict(allEntries = true)` → `cache.clear()`) ocurre entre
la lectura de la base y el `put`, la consulta en vuelo guarda el resultado viejo después del vaciado,
y toda consulta posterior lo recibe. Ni `ConcurrentMapCache` ni `CaffeineCache` evitan esto. Según
la documentación de Caffeine, `invalidateAll()` tampoco descarta de forma garantizada una carga en
curso.

**Decisión**: cada caché es un `GenerationGuardedCaffeineCache`, una subclase de `CaffeineCache` en
`config/`. Lo crea `QuoteCacheManager.adaptCaffeineCache(...)`, que es el punto de extensión
documentado: según su Javadoc, devuelve "the Spring CaffeineCache adapter (or a decorator
thereof)". El caché lleva un número de generación:

1. `clear()` e `invalidate()` toman el lock de escritura, incrementan la generación y recién
   después vacían con `super`. El orden importa: un `put` que llega en el medio ya ve la generación
   nueva.
2. `get(key)` anota en un `ThreadLocal` la generación vigente si no encontró la entrada. Si la
   encontró, borra la anotación.
3. `put(key, value)` toma el lock de lectura, lee y borra la anotación del hilo, y guarda **solo si**
   no hay anotación o si la generación no cambió desde esa búsqueda. Si cambió, descarta el valor:
   esa consulta recibe su resultado, pero no queda guardado.
4. Hacer la comprobación y el `put` bajo el lock de lectura, y el incremento y el vaciado bajo el de
   escritura, hace atómico el par comprobación-`put` respecto del vaciado. Sin eso, un `put` que
   pasó la comprobación podría escribir justo después del vaciado. Los `put` no se bloquean entre
   sí.

Con eso, toda consulta que **empieza** después de confirmado el cambio:
- busca con la generación nueva, porque el vaciado ocurre después del commit, como hoy;
- no encuentra ninguna entrada vieja: el vaciado las borró, y un `put` viejo o se hizo antes del
  vaciado (y lo borró) o se descartó.

`QuoteCacheInvalidator`, los `@Cacheable` de `QuoteQueryService` y los tres servicios que invalidan
no cambian.

**Por qué es una subclase y no un decorador**: `CacheMetricsRegistrar` (Boot 4.1.1) elige el
`CacheMeterBinderProvider` por el tipo genérico del proveedor
(`CaffeineCacheMeterBinderProvider implements CacheMeterBinderProvider<CaffeineCache>`). Solo
desenvuelve `TransactionAwareCacheDecorator`. Un decorador propio que no fuera `CaffeineCache`
quedaría sin métricas automáticas. La subclase sí es un `CaffeineCache` y `getNativeCache()` sigue
devolviendo el `com.github.benmanes.caffeine.cache.Cache` con las estadísticas.

**Límites aceptados, que se documentan en el Javadoc**:
- La anotación del `ThreadLocal` es una por hilo, la de la última búsqueda. Alcanza porque
  `getRanking` y `getCurrent` no anidan otra consulta cacheada entre su `get` y su `put`.
- Si el método lanza (el 404 de `getCurrent`), no hay `put` y la anotación queda hasta la próxima
  búsqueda de ese hilo, que la pisa. Un `put` directo sin búsqueda previa siempre guarda.
- No se usa `@Cacheable(sync = true)`, que pasa por `get(key, Callable)`. Ese camino no se protege.
- Consultas simultáneas de la misma hoja sin guardar pueden llegar las dos a la base (Edge Cases:
  aceptable).

**Alternativas**:
- Meter la generación en la clave con un `KeyGenerator` propio: la clave se calcula una vez antes
  del `get`, así que funcionaría para `getRanking`. Pero `getCurrent` usa `key = "#playerId"`, que
  ignora el `KeyGenerator`, y habría que cambiar su anotación.
- `@Cacheable(sync = true)` con Caffeine: cambia las anotaciones y Caffeine no garantiza que
  `invalidateAll` descarte una carga en curso.
- Vaciar dos veces (antes y después del commit): no cierra la carrera, solo la achica.

### R4. Métricas de los cachés en `/actuator/prometheus`

**Decisión**: alcanza con la autoconfiguración de Boot. No se registra nada a mano.

**Cómo funciona en Boot 4.1.1** (código fuente de `module/spring-boot-cache`):
- `CacheMetricsAutoConfiguration` corre después de `CacheAutoConfiguration` y del registro
  compuesto. Lleva `@ConditionalOnBean(CacheManager.class)` y
  `@ConditionalOnClass(MeterRegistry.class)`. Importa `CacheMeterBinderProvidersConfiguration`, que
  registra `CaffeineCacheMeterBinderProvider` si están `CaffeineCache` y el `Cache` de Caffeine, y
  `CacheMetricsRegistrarConfiguration`.
- `CacheMetricsRegistrarConfiguration` registra en su constructor **los cachés que existen en ese
  momento** (`cacheManager.getCacheNames()`). Un caché que se crea después no se registra. Por eso
  `QuoteCacheManager` registra los dos cachés al construirse (R1).
- Etiquetas: Boot agrega `cache.manager`, el nombre del bean. Se llama `cacheManager` y no se
  recorta porque no es más largo que el sufijo. También agrega `name` (el nombre del caché).
  Micrometer agrega `cache` con el mismo nombre.
- Medidores de `CaffeineCacheMetrics` (Micrometer, verificado en `main`; **a verificar en la
  implementación** que 1.17.1 coincide, en la tarea del IT de métricas del caché):
  `cache.gets{result=hit|miss}`, `cache.puts`, `cache.evictions`, `cache.size` y
  `cache.eviction.weight`. Sin `recordStats()` se registra solo `cache.size`, con un `WARN`.
  **Verificado en la implementación (T016)**: con Micrometer 1.17.1 salen las cinco, para los dos
  cachés y con `cache_manager="cacheManager"` (`e2e/CacheMetricsIT`).

**Cómo queda en Prometheus** (más `application="futbol-market"` en toda serie):

```text
cache_gets_total{cache="quotes-ranking",cache_manager="cacheManager",name="quotes-ranking",result="hit"}
cache_gets_total{cache="quotes-ranking",cache_manager="cacheManager",name="quotes-ranking",result="miss"}
cache_puts_total{cache="quotes-ranking",...}
cache_evictions_total{cache="quotes-ranking",...}
cache_eviction_weight_total{cache="quotes-ranking",...}
cache_size{cache="quotes-ranking",...}
```

Lo mismo con `cache="quotes-current"`. El índice de aciertos sale de `cache_gets_total`.
`cache_puts_total` cuenta cargas (`stats().loadCount()`), no `put` manuales, así que con
`@Cacheable` queda en cero. Se documenta y no se usa en el dashboard.

`cache.evictions` cuenta los descartes por tope y por vencimiento, no los vaciados explícitos:
Caffeine no cuenta `invalidateAll` como desalojo.

El acceso es el de la 012: `/actuator/prometheus` es solo para `ADMIN` (FR-008). No cambia
`SecurityConfig`.

**Alternativas**:
- Registrar `CaffeineCacheMetrics.monitor(...)` a mano en un `MeterBinder`: duplicaría la
  autoconfiguración y arriesga series repetidas.
- Contadores propios de aciertos y fallos en `BusinessMetrics`: duplican lo que Caffeine ya mide.

### R5. Propiedades `futbolmarket.cache.*` y sus valores por defecto

**Decisión**: hay un record `QuoteCacheProperties` en `config/`, con
`@ConfigurationProperties("futbolmarket.cache")` y `@Validated`, que se registra en
`ApplicationConfig` como los demás. Tiene un registro anidado `Spec(maximumSize, expireAfterWrite)`
por caché:

```yaml
futbolmarket:
  cache:
    ranking:
      maximum-size: 500
      expire-after-write: 10m
    current:
      maximum-size: 5000
      expire-after-write: 10m
```

Validación al arrancar (FR-003): el constructor compacto de `QuoteCacheProperties` comprueba que
cada caché tenga su bloque, y que `maximum-size` y `expire-after-write` estén y sean mayores que
cero. Un valor inválido corta el arranque con un mensaje en español que nombra la propiedad
completa, con el mismo estilo de `QuoteRecalculationProperties`.

**Verificado en la implementación (T007)**: la primera versión llevaba `@NotNull @Positive` en el
registro anidado `Spec`. El error de enlace de esas anotaciones nombra el campo en camelCase
(`ranking.maximumSize`), no la propiedad (`futbolmarket.cache.ranking.maximum-size`), y como
`Spec` se comparte entre los dos cachés, su mensaje tampoco puede decir cuál es. Por eso toda la
validación pasó al constructor del registro de afuera, que conoce el nombre de cada caché.

**Por qué esos valores** (los fija Assumptions de la spec):
- **Ranking, 500 entradas**: la clave es `(PlayerFilter, page, size)`. Los filtros que se usan son
  pocos (liga, equipo, posición) y las hojas que se piden son las primeras. 500 combinaciones dan
  mucho margen, y cada `RankingPage` pesa unos KB (hasta 100 filas), así que el techo queda en el
  orden de pocos MB.
- **Cotización actual, 5.000 entradas**: es una entrada por jugador, y el catálogo de las ligas que
  se sincronizan entra con margen. Cada `Quote` es chico.
- **10 minutos para los dos**: el recálculo es semanal y cada cambio vacía el caché, así que el
  vencimiento es solo una red por si falla una invalidación (FR-005 aclara que no es parte de la
  garantía). Es corto, para que un dato viejo no dure, y largo, para que en uso normal casi no haya
  fallos de más: una hoja popular se recalcula a lo sumo cada 10 minutos.

**Alternativas**:
- `spring.cache.caffeine.spec`: es una sola especificación para todos los cachés y Boot no la usa
  si definimos nuestro `CacheManager`.
- `expireAfterAccess`: una hoja muy pedida no vencería nunca, y eso contradice FR-002.

### R6. Tests del caché con un reloj controlable

**Decisión**: `QuoteCacheManager` recibe en su constructor las propiedades, un `Ticker` y un
`Executor` opcional (`null` = el de Caffeine por defecto). Producción usa `Ticker.systemTicker()` y
el executor por defecto. Los unitarios (`config/QuoteCacheManagerTest`,
`config/GenerationGuardedCaffeineCacheTest`) usan:
- un `Ticker` falso propio del test (una clase chica con un `AtomicLong` de nanos), sin la
  dependencia `guava-testlib`;
- `Runnable::run` como executor y `getNativeCache().cleanUp()` antes de verificar. Caffeine aplica
  el tope y el vencimiento en su mantenimiento, que es asíncrono, así que puede pasarse
  momentáneamente del tope (SC-004 se verifica después del mantenimiento).

Lo que verifican: que es un `CaffeineCacheManager`, que tiene los dos cachés y ninguno más, que el
tope y el vencimiento se cumplen por caché y con valores distintos, que las estadísticas están
prendidas, la carrera de R3 armada en un solo hilo (búsqueda sin entrada, vaciado, `put`
descartado), un `put` sin búsqueda previa que sí guarda y la invalidación existente que vacía los
dos cachés.

La garantía estricta con hilos reales (SC-003) va en un IT de servicio
(`service/quote/QuoteCacheConcurrencyIT`). Un `@MockitoSpyBean` sobre `QuoteRepository` frena
`findRanking` con un `CountDownLatch` en un hilo, se confirman los cambios (recálculo, recotización
y cambio de estrategia) desde el hilo del test y se libera la consulta. La consulta que empieza
después devuelve los datos nuevos.

### R7. Medición antes/después (FR-010, SC-001, SC-002)

**Decisión**: un script versionado de PowerShell, `monitoring/measure-ranking.ps1`, sin
dependencias nuevas: usa `System.Net.Http.HttpClient` de .NET, que viene con Windows PowerShell 5.1
y con PowerShell 7. Mide N consultas seguidas a `GET /players/ranking` con los mismos parámetros y
reporta:

- **Del lado del cliente** (siempre): promedio, p95 y máximo, con `Stopwatch` por consulta. Hay un
  calentamiento previo que no se cuenta.
- **Del lado del servidor** (si existe `monitoring/admin-api-key`): lee `/actuator/prometheus` antes
  y después y calcula, sobre la diferencia, la cantidad de pedidos, el promedio
  (Δ`_sum` / Δ`_count`) y el p95 interpolado de los buckets de
  `http_server_requests_seconds{uri="/players/ranking"}` (la misma interpolación lineal de
  `histogram_quantile`). Si están, también calcula los aciertos y fallos de
  `cache_gets_total{cache="quotes-ranking"}` y el índice de aciertos.

Tres variantes con la misma cantidad de consultas (FR-010 y Assumptions):

| Variante | Código | Cómo se levanta |
|---|---|---|
| Caché anterior | **antes** de tocar `CacheConfig` (`ConcurrentMapCacheManager`) | `bootRun` normal |
| Sin caché | código nuevo | `bootRun` con `--spring.cache.type=none` |
| Caché nuevo | código nuevo | `bootRun` normal |

- **"Sin caché" sin código extra**: el `@Bean` de `CacheConfig` lleva
  `@ConditionalOnProperty(name = "spring.cache.type", havingValue = "caffeine", matchIfMissing = true)`.
  Con `spring.cache.type=none`, nuestro bean no se crea y `CacheAutoConfiguration` crea un
  `NoOpCacheManager`. Cada consulta va a la base por el mismo camino de siempre (`findRanking` no
  cambia) y la invalidación sigue funcionando contra el no-op. Es la propiedad estándar de Boot, no
  una propiedad propia, y sirve solo para medir.
- **Comparabilidad**: las tres variantes se miden con el perfil local, la misma base y
  `--futbolmarket.quotes.recalculation.on-startup=false`, para que un recálculo al arrancar no vacíe
  el caché ni cargue la base durante la medición.
- **El orden se impone en tasks**: la medición del caché anterior es una de las primeras tareas,
  antes del cambio de `CacheConfig`.
- **Resultados**: van en `specs/013-ranking-metricas-grafana/medicion-ranking.md`, con el
  procedimiento, la máquina, N, la consulta y una tabla por variante. Para comparar el caché nuevo
  con el anterior (SC-001) se reportan los dos valores y la diferencia, sin un umbral fijo: el
  caché nuevo no puede ser más lento que el anterior más allá del ruido de la medición, y por eso
  A y C se corren dos veces cada una. Contra la variante sin caché se informan el factor B / C y el
  ahorro absoluto B − C del tiempo medio del servidor, también sin umbral. El README lleva un
  resumen y el enlace (FR-010). No hay test automático de tiempos.
- **Por qué sin umbral contra "sin caché"** (cambio del 2026-11-25, después de medir): la primera
  versión de SC-001 pedía que la consulta repetida fuera 5 veces más rápida que sin caché. La
  medición dio 2,41 en el servidor y 1,51 en el cliente. En C todas las consultas medidas fueron
  aciertos y el servidor igual tardó 3,44 ms por pedido: es el costo fijo de la cadena de
  seguridad, la auditoría de web services, la métrica HTTP y la serialización a JSON, que el caché
  no toca. El caché elimina la consulta a la base (4,8 ms con la base H2 local), así que el factor
  depende de cuánto cueste esa consulta: con una base remota o con más datos sería mayor. Un umbral
  fijo mediría la base y no el caché.

**Alternativas**:
- JMeter, k6 o `hey`: son dependencias nuevas. La spec pide "sin dependencias nuevas".
- `Invoke-WebRequest` en un bucle: su costo por pedido, de varios milisegundos, tapa el tiempo de
  un acierto del caché.
- Usar solo el p95 de Prometheus: necesita Prometheus corriendo, y el p95 de buckets es grueso.
  Igual queda en el quickstart como consulta opcional.
- Medir "sin caché" variando la hoja en cada consulta: ya no son las mismas consultas.

---

## Métricas de negocio del mercado (US2)

### R8. Nombres, etiqueta y cómo salen en Prometheus

**Decisión**: tres contadores nuevos en `observability/BusinessMetrics`, con una sola etiqueta,
`counterparty`, de dos valores cerrados que salen de un enum nuevo, `TradeCounterparty`
(`observability/`), igual que `OrderResult`:

| Constante | Nombre Micrometer | Prometheus | Incremento por operación |
|---|---|---|---|
| `TRADES` | `futbolmarket.market.trades` | `futbolmarket_market_trades_total` | 1 |
| `TOKENS_TRADED` | `futbolmarket.market.tokens.traded` | `futbolmarket_market_tokens_traded_total` | `Trade.quantity()` |
| `CREDITS_TRADED` | `futbolmarket.market.credits.traded` | `futbolmarket_market_credits_traded_total` | `Trade.total().doubleValue()` |

| `TradeCounterparty` | `tag()` | Sale de `Trade.sellerType()` |
|---|---|---|
| `INITIAL_ORDER` | `initial_order` | `OwnerType.ADMIN` (la orden inicial del superusuario) |
| `USER` | `user` | `OwnerType.USER` |

- El registro Prometheus de Micrometer agrega `_total` a los contadores y cambia los puntos por
  guiones bajos. Pasa lo mismo con `futbolmarket_market_orders_total` de la 012, que verifica
  `ActuatorContentIT`.
- **No se usa `baseUnit`**: Micrometer lo agregaría al nombre (`..._tokens_traded_tokens_total`).
- Las descripciones van en español, como las de la 012 (`BusinessMetricsTest` verifica eso para
  las suyas).
- `bindTo` da de alta las 6 combinaciones en cero (FR-017). El catálogo pasa de 30 a 36 series, y
  se actualiza el Javadoc.
- **Tests existentes**: `BusinessMetricsTest` cuenta solo los nombres de su conjunto `NAMES`, y
  `ActuatorContentIT` filtra por prefijo (`futbolmarket_market_orders_total{`), que no coincide con
  los nombres nuevos. **Corregido en la implementación**: `BusinessMetricsIT` sí se rompe, porque
  verifica que toda serie `futbolmarket.*` use solo etiquetas de su vocabulario, que no tenía
  `counterparty`. Se le agregó esa entrada con los dos valores cerrados, con el "sí" explícito del
  desarrollador (2026-11-25; plan, Complexity Tracking).

**Por qué**: es la misma familia de nombres de la 012 (FR-018). Una etiqueta de valores cerrados
mantiene acotada la cardinalidad (FR-020). Los nombres del pedido del usuario se mantienen tal cual.

**Alternativas**:
- `counterparty="admin"`: es el nombre técnico del dueño, pero la spec habla de "orden inicial del
  superusuario". `initial_order` dice de dónde vino la venta.
- Un solo contador con la etiqueta `kind=trades|tokens|credits`: mezcla unidades en una métrica.

### R9. Dónde se registra: los advices de `buy` y `sell`

**Decisión**: en `observability/BusinessMetricsAspect`, `measureBuy` y `measureSell` pasan el
resultado de `measureOrder(...)` por un método `recordTrades(Object result)`. Si el resultado es un
`BookOutcome`, registra **dentro de `recordSafely`** cada `Trade` de `outcome.trades()` con
`metrics.recordTrade(sellerType, quantity, total)`. `measureCancel` no registra operaciones
(FR-016). Los servicios no cambian.

Por qué cuenta exactamente lo confirmado (FR-014):
- El aspecto tiene `@Order(Ordered.HIGHEST_PRECEDENCE)` y el advisor de transacciones de Spring
  tiene el orden por defecto (el más bajo), así que el aspecto envuelve a la transacción. Cuando
  `proceed()` vuelve bien, el commit ya ocurrió.
- Un rechazo del dominio, una falla al grabar o un commit que falla llegan como excepción:
  `measureOrder` la relanza y no se llega a `recordTrades`. No suma nada (escenarios 5 y 6).
- `recordSafely` captura `RuntimeException` y la deja en un `WARN`: medir nunca cambia el
  resultado (FR-021). El aspecto devuelve el mismo objeto de `proceed()`.
- Una orden pendiente no tiene operaciones en su `BookOutcome`. Cuando se cruza más tarde, la
  operación aparece en el `BookOutcome` del `buy` o el `sell` que la cruza, y se cuenta ahí una sola
  vez (FR-015, escenario 4).

`recordTrade` lo hace `BusinessMetrics`, que recibe `OwnerType`, `int` y `BigDecimal`. El aspecto
ya depende de clases de `modelo/`, y `observability/` no es una capa de las reglas de ArchUnit, así
que no se rompe ninguna regla de la 010 (B1 solo restringe quién accede a `service`, `persistence`,
`adapter`, `controller` y `security`). Los pointcuts siguen siendo texto.

**Alternativas**:
- Registrar en `MarketOrderService.save`: los servicios no se tocan (decisión tomada) y quedaría
  dentro de la transacción, así que contaría operaciones de un rollback.
- `TransactionSynchronization.afterCommit`: es correcto, pero necesita tocar el servicio o un
  listener nuevo, y el aspecto ya cumple la misma garantía.

### R10. ¿Hay otra ruta que ejecute operaciones?

**Revisado**: `TradeRepository.saveAll` se llama **solo** desde `MarketOrderService.save`, el paso
común de `buy`, `sell` y `cancel`. `grep` sobre `service/` y `persistence/repository/`: el resto de
los usos de `TradeRepository` son lecturas (`MarketQueryService` y `PortfolioService`).

- `cancel`: `PlayerBook.cancel` no ejecuta cruces y su `BookOutcome` no tiene operaciones. Igual,
  el advice de `cancel` no registra.
- `InitialOrderService` (al arrancar y después de una sincronización) solo guarda órdenes iniciales
  y su auditoría (`orderRepository.saveAll` y `auditRepository.saveAll`). No cruza contra compras
  pendientes ni crea operaciones.
- No hay procesos programados que crucen el libro.

**Conclusión**: `buy` y `sell` cubren todas las operaciones ejecutadas. Si una feature futura
agregara un cruce disparado por otro proceso, tendría que registrar sus operaciones por el mismo
`BusinessMetrics.recordTrade`. Queda anotado en el Javadoc del aspecto.

### R11. Créditos: de `BigDecimal` a `double`

**Decisión**: `Counter.increment(double)` recibe `trade.total().doubleValue()`.

**Precisión aceptada**: `Trade.total` tiene escala 2 (`MarketMath.CREDIT_SCALE`). Un `double` tiene
entre 15 y 17 dígitos significativos, así que un total como `309.00` se representa con un error
menor a 10⁻¹³, y la suma acumulada conserva los centavos mientras el total quede por debajo de unos
10¹³ créditos, muy lejos de cualquier escenario del mercado simulado. No se redondea a enteros
(FR-019). Prometheus guarda las muestras como float64, así que no hay forma de transportar un
decimal exacto por este canal. La fuente de verdad exacta sigue siendo la tabla de operaciones, y
el IT compara con tolerancia de un centavo (`isCloseTo(..., within(0.005))`).

**Alternativas**:
- Contar centavos como `long` (×100): cambia la unidad de la métrica respecto de los saldos del
  mercado (Assumptions: "en la misma unidad que los saldos") y Prometheus igual lo guarda como
  float64.

---

## Grafana (US3)

### R12. Versión mínima de Grafana OSS y cómo se corre en Windows sin Docker

**Decisión**: es el zip oficial de Grafana OSS para Windows, descomprimido en cualquier carpeta
(`<GRAFANA_HOME>`). El README fija **11.0 o posterior**: el dashboard usa `schemaVersion` 39, que
es el de la 11.0, referencias a data sources por objeto `{type, uid}` y el subcomando
`grafana server`. Las páginas oficiales de hoy muestran la 13 como versión actual. **A verificar en
la implementación**: la versión con la que se probó queda escrita en el README, en la tarea del
README. **Verificado (T033)**: se probó con Grafana OSS 13.3.2 y Prometheus 3.16.0.

- El ejecutable es `<GRAFANA_HOME>\bin\grafana.exe server`. Las páginas de instalación más viejas
  todavía nombran `grafana-server.exe`, que está deprecado, y la de arranque usa `grafana server`.
  Se pasa `--homepath <GRAFANA_HOME>` para que encuentre `conf\defaults.ini` y `public\` sin
  importar desde dónde se lance.
- No se edita `defaults.ini` (la documentación lo prohíbe) ni hace falta un `custom.ini` en la
  instalación.

### R13. Configuración con variables de entorno desde un script del repo

**Decisión**: un script versionado, `monitoring/grafana/start-grafana.ps1`, que se corre desde la
raíz con un solo comando:

```powershell
powershell -ExecutionPolicy Bypass -File monitoring/grafana/start-grafana.ps1 -GrafanaHome C:\grafana
```

Si no se pasa `-GrafanaHome`, usa `$env:GRAFANA_HOME`. El script:
1. Calcula las rutas absolutas a partir de `$PSScriptRoot` y las pone como variables de entorno
   **solo del proceso** que lanza:
   - `GF_PATHS_PROVISIONING` = `<repo>\monitoring\grafana\provisioning`
   - `GF_PATHS_DATA` = `<repo>\monitoring\grafana\data`
   - `GF_PATHS_LOGS` = `<repo>\monitoring\grafana\data\log`
   - `GF_PATHS_PLUGINS` = `<repo>\monitoring\grafana\data\plugins`
   - `FUTBOLMARKET_GRAFANA_DASHBOARDS` = `<repo>\monitoring\grafana\dashboards` (la usa el provider)
2. Si existe el archivo local de credenciales (R14), pone `GF_SECURITY_ADMIN_USER` y
   `GF_SECURITY_ADMIN_PASSWORD` con sus valores. Si no existe, no pone nada y Grafana usa lo suyo
   por defecto.
3. Lanza `& "$GrafanaHome\bin\grafana.exe" server --homepath $GrafanaHome`.

**Por qué variables de entorno y no un `custom.ini` con rutas**:
- En `pkg/setting/setting.go`, Grafana resuelve las rutas relativas de `[paths]` contra el
  **HomePath** (`makeAbsolute(path, cfg.HomePath)`), no contra el directorio actual. Una ruta
  relativa al repo en un `.ini` no funciona, y una absoluta no se puede versionar.
- `GF_<SECCIÓN>_<CLAVE>` pisa cualquier valor del `.ini` (es el formato documentado).
- Los archivos de provisioning aceptan `$VAR` / `${VAR}` (documentado), así que el provider apunta
  a `$FUTBOLMARKET_GRAFANA_DASHBOARDS`. Las variables no se pueden usar dentro del JSON del
  dashboard, y no hace falta.

**Alternativas**:
- `--config monitoring/grafana/grafana.ini` con `$__env{...}` en las rutas: igual necesita que
  alguien ponga las variables, y suma un archivo.
- Copiar los archivos a `<GRAFANA_HOME>\conf\provisioning`: deja de ser "un solo comando desde el
  repo", y una copia se desactualiza.
- Docker: prohibido por la constitución.

### R14. Credencial del administrador de Grafana sin versionar nada (FR-028)

**Decisión**:
- Se versiona `monitoring/grafana/admin-credentials.example`, la configuración de ejemplo sin
  contraseña:

  ```text
  # Copiar a monitoring/grafana/admin-credentials (ignorado por git) y completar.
  admin_user=admin
  admin_password=
  ```

- El archivo real, `monitoring/grafana/admin-credentials`, va en `.gitignore`, junto con
  `monitoring/grafana/data/`. Es el mismo criterio que `monitoring/admin-api-key`.
- El script lo lee como pares `clave=valor`, ignora las líneas con `#` y no muestra la contraseña.
  Una clave vacía no se aplica.

**Por qué no `$__file{...}`**: en `pkg/setting/expanders.go`, el expander `file` hace `os.Stat` y
devuelve un error si el archivo no existe, y `loadConfiguration` corta la carga. Sería incompatible
con "si el archivo no existe, Grafana arranca igual" (FR-028). El sufijo `__FILE` en variables de
entorno no está en el binario (`applyEnvVariableOverrides` solo procesa `GF_*`): es de la imagen de
Docker.

**Comportamiento a documentar en el README**:
- Grafana aplica `admin_password` **solo la primera vez**, cuando crea el usuario administrador en
  una base nueva ("Set once on first-run", según la documentación de configuración). Para cambiarla
  después se borra `monitoring/grafana/data/` (se pierden solo los datos locales: el dashboard y el
  data source vuelven a salir del provisioning) o se usa `grafana cli admin reset-admin-password`.
- Sin archivo, el usuario y la contraseña son `admin` / `admin`, y Grafana pide cambiarla en el
  primer ingreso.

### R15. Provisioning: data source y provider de dashboards

**Decisión** (formato `apiVersion: 1` de la documentación de provisioning):

- `monitoring/grafana/provisioning/datasources/prometheus.yaml`: un data source `type: prometheus`,
  `access: proxy`, `url: http://localhost:9090`, `uid: futbol-market-prometheus` (fijo),
  `isDefault: true`, `editable: false` y `jsonData.timeInterval: 15s`, que es el
  `scrape_interval` de `monitoring/prometheus.yml`, para que `$__rate_interval` nunca quede más
  corto que dos scrapes. No lleva credencial: el Prometheus local no la pide (Assumptions).
- `monitoring/grafana/provisioning/dashboards/futbol-market.yaml`: un provider `type: file` con
  `folder: futbol-market`, `disableDeletion: true`, `allowUiUpdates: false`,
  `updateIntervalSeconds: 10` y `options.path: $FUTBOLMARKET_GRAFANA_DASHBOARDS`.
- `monitoring/grafana/dashboards/futbol-market.json`: el dashboard en el formato que exporta
  Grafana (sin el envoltorio `{"dashboard": …}` de la API), con `uid: futbol-market`, `title`,
  `schemaVersion: 39` y cada panel con `datasource: {"type": "prometheus", "uid":
  "futbol-market-prometheus"}`.

Si Prometheus no responde, el data source igual figura y los paneles muestran "No data" (Edge
Cases). **A verificar en la implementación**: que Grafana no se queje por las carpetas de
provisioning que no usamos (`plugins`, `alerting`). Si fallara, se agrega un archivo mínimo válido,
nunca un `.gitkeep`. Se verifica en la tarea del quickstart de Grafana.

**Verificado en la implementación (T033, Grafana 13.3.2)**: sin esas carpetas, el log de Grafana
muestra dos errores de provisioning, uno por `provisioning/plugins` y otro por
`provisioning/alerting`. Cada carpeta lleva ahora un `futbol-market.yaml` mínimo y válido que no
configura nada: `apiVersion: 1` y `apps: []` para los plugins, y `apiVersion: 1` para las
alertas (contracts/grafana-dashboard.md §4.1).

`monitoring/prometheus.yml` **no cambia**: recolecta el mismo endpoint, y las métricas nuevas salen
solas.

### R16. Paneles y PromQL

**Decisión**: tres filas. Las series temporales usan `increase`/`rate`, que absorben los
reinicios de los contadores (FR-027, escenario 5). Se excluye `/actuator/**` del tráfico general, para que el scrape de
Prometheus no ensucie los pedidos. El detalle de cada expresión está en
[contracts/grafana-dashboard.md](./contracts/grafana-dashboard.md).

- **Negocio**: operaciones, tokens y créditos, cada uno con un *stat* del total desde el arranque
  del backend y una serie temporal por `counterparty` más el total (FR-024). Órdenes pedidas por `operation`/`result`
  y órdenes abiertas (`futbolmarket_market_orders_open`).
- **Ranking**: el p95 de `http_server_requests_seconds_bucket{uri="/players/ranking"}` con
  `histogram_quantile` (el histograma ya está prendido desde la 012, FR-009) y el índice de
  aciertos de `quotes-ranking` (FR-025).
- **General**: pedidos por segundo y tasa de 4xx y de 5xx, con la etiqueta `outcome` de Micrometer
  (`CLIENT_ERROR` y `SERVER_ERROR`) (FR-026).

**Cambio después de la validación manual (T033)**: los tres *stats* de totales usaban
`sum(increase(...[$__range]))`. `increase` extrapola a los bordes de la ventana, así que con pocas
operaciones mostraba valores no enteros que cambiaban solos (por ejemplo, 4,13 operaciones). Ahora
muestran el valor del contador, `sum(futbolmarket_market_trades_total)` y los equivalentes de
tokens y créditos, con 0 decimales en operaciones y tokens y 2 en créditos. Es el total exacto
desde que arrancó el backend, y el título lo dice: después de un reinicio vuelve a cero, que es
coherente con lo que el panel promete (FR-027). Las series temporales por período no cambian.

### R17. Test del dashboard

**Decisión**: `e2e/GrafanaDashboardIT` (MockMvc, perfil test, con el apoyo de `ActuatorITSupport`
de la 012):
1. Lee `../monitoring/grafana/dashboards/futbol-market.json`, relativo a `backend/`, que es el
   directorio de trabajo de los tests de Gradle, y lo parsea con `tools.jackson.databind.json.JsonMapper`,
   el Jackson 3 que ya usan los tests.
2. Junta los `expr` de todos los `targets` y extrae los nombres de métrica con los prefijos que se
   usan (`futbolmarket_`, `http_server_requests_`, `cache_`).
3. Hace un pedido al ranking (así aparecen los buckets de `uri="/players/ranking"`) y verifica que
   cada nombre figura en `/actuator/prometheus` pedido como `ADMIN`.
4. Verifica que todo panel con consultas apunta al `uid` del data source provisionado, leído del
   YAML como texto, y que no hay paneles sin `datasource`.

Es un test de archivos: no necesita Grafana ni Prometheus corriendo.

---

## Constitución y tests de la 010

### R18. Test existente que cambia, con autorización

`config/CacheConfigTest.elCacheManagerEsElNuestroConLosDosCachesDeCotizaciones` verifica
`isInstanceOf(ConcurrentMapCacheManager.class)`. Con la decisión R1 deja de cumplirse. **El
desarrollador que implementa la feature dio el "sí" explícito el 2026-11-24** (Principio IV) para
cambiar **solo esa aserción** (y su import) a `CaffeineCacheManager.class`. El nombre del método,
los demás tests de la clase y las demás aserciones no cambian. Es la única excepción a SC-008 y
queda declarada en el plan (Complexity Tracking). El resto de los tests (`QuoteCacheIT`,
`RankingRoutingIT`, `BusinessMetricsTest`, `ActuatorContentIT` y la suite de arquitectura de la
010) pasan sin cambios:
- `QuoteCacheIT` compara con `isSameAs`: Caffeine guarda la referencia sin copiar.
- La invalidación sigue siendo síncrona: `invalidateAll` saca las entradas del mapa en el momento.
- Las clases nuevas van en `config/` y `observability/`, que no son capas de B1. No dependen de
  `service/`, `controller/` ni `persistence/`. No usan clientes HTTP (B5.3), `System.out` (B7.1),
  excepciones genéricas (B7.2) ni inyección por campo (B7.4).
