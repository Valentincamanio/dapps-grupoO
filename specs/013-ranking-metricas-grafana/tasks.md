---

description: "Lista de tareas de la feature 013: ranking para alta frecuencia, métricas del mercado y dashboard de Grafana"
---

# Tareas: Ranking para alta frecuencia, métricas del mercado y dashboard de Grafana

**Entrada**: documentos de diseño de `/specs/013-ranking-metricas-grafana/`

**Prerrequisitos**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md),
[data-model.md](./data-model.md), [contracts/metrics.md](./contracts/metrics.md),
[contracts/grafana-dashboard.md](./contracts/grafana-dashboard.md), [quickstart.md](./quickstart.md)

**Tests**: SÍ. La spec los pide (SC-003, SC-004, SC-005, SC-008) y la constitución (Principio IV) también.
**Sin TDD**: cada test se escribe junto con (después de) la implementación de su tarea, y al cerrar la tarea el
test existe y pasa. Convenciones: AssertJ, setup / execute / verify en tres bloques, nombres descriptivos en
español (`unPutDespuesDelVaciadoSeDescarta`), H2 en memoria con `@ActiveProfiles("test")`, sin Testcontainers ni
Docker.

**Organización**: por historia de usuario (US1 a US3, en el orden de prioridad de la spec). US1 y US2 son las
dos P1 y forman juntas el MVP; son independientes entre sí. US3 (P2) depende de los nombres de métricas de las
dos.

## Formato: `[ID] [P?] [Historia] Descripción`

- **[P]**: se puede hacer en paralelo (archivos distintos, sin dependencias pendientes)
- **[US#]**: historia de usuario a la que pertenece la tarea
- Cada descripción lleva la ruta exacta del archivo

## Convenciones de rutas

- Backend, producción: `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/` (abreviado `<main>/` en las
  dependencias y ejemplos; en las tareas va la ruta completa)
- Backend, tests: `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/` (abreviado `<test>/`)
- Recursos: `backend/src/main/resources/`
- Fuera de `backend/`: `monitoring/`, `.gitignore`, `README.md`, `specs/013-ranking-metricas-grafana/`
- **No hay carpetas de paquetes nuevas**: todo el Java nuevo va en `<main>/config/` y `<main>/observability/`,
  que ya existen (plan, "Decisión de estructura").

## Reglas que valen para todas las tareas

- **Un solo test existente cambia** (plan, Complexity Tracking; research R18): en
  `<test>/config/CacheConfigTest.java`, el método `elCacheManagerEsElNuestroConLosDosCachesDeCotizaciones` pasa de
  `isInstanceOf(ConcurrentMapCacheManager.class)` a `isInstanceOf(CaffeineCacheManager.class)`, con su import. Ni el
  nombre del método ni las demás aserciones ni los demás tests de la clase cambian. **Ningún otro test existente se
  modifica ni se borra** (SC-008): `QuoteCacheIT`, `RankingRoutingIT`, `BusinessMetricsTest`,
  `BusinessMetricsAspectTest`, `BusinessMetricsIT`, `ActuatorContentIT`, `ActuatorAccessIT` y la suite de
  arquitectura de la 010 pasan tal cual. Los apoyos de test existentes (`<test>/e2e/ActuatorITSupport`,
  `<test>/e2e/AuthTestHelper`, `<test>/service/market/MarketDatabase`, `<test>/service/quote/QuoteDatabase`,
  `<test>/service/quote/MutableClock`) se **usan** sin agregarles métodos. Agregar métodos a los archivos de test que
  crea esta feature está permitido.
  **Segunda excepción autorizada (2026-11-25)**: el build completo mostró que
  `BusinessMetricsIT.ningunaSeriePropiaTieneEtiquetasFueraDelVocabulario` rechaza la etiqueta
  `counterparty`. Con el "sí" explícito del desarrollador, su mapa `VOCABULARY` suma
  `"counterparty", Set.of("initial_order", "user")`, y nada más (plan, Complexity Tracking; research R8).
- **Dos dependencias nuevas, y solo esas** (research R2): `org.springframework.boot:spring-boot-starter-cache` y
  `com.github.ben-manes.caffeine:caffeine`, sin versión (las fija el BOM de Boot 4.1.1). `build.gradle` y
  `application.yaml` se **modifican** (se agregan líneas), no se reemplazan; `application-local.yml` y
  `backend/src/test/resources/application-test.yml` **no se tocan**.
- **Sin cambios** en `<main>/service/quote/QuoteCacheInvalidator.java`, `<main>/service/quote/QuoteQueryService.java`
  (sus `@Cacheable`), `QuoteRecalculationService`, `StrategyService`, `StrategyChangeService`, `QuoteController`,
  `QuoteRepository.findRanking`, `MarketOrderService`, `PlayerBook`, `BookOutcome`, `Trade` ni `SecurityConfig`.
  No hay entidades, tablas, índices ni endpoints nuevos (FR-018); Swagger no cambia.
- **Ninguna métrica cambia un resultado** (FR-021): el aspecto devuelve el mismo objeto de `proceed()` y relanza la
  misma excepción; registrar va dentro de `recordSafely`, que captura `RuntimeException` y la loguea en `WARN`.
- **Etiquetas cerradas** (FR-020): la única etiqueta nueva es `counterparty`, con valores de un enum
  (`initial_order` | `user`). Nada por jugador, liga ni usuario.
- **Ninguna credencial versionada** (FR-028, SC-007): ni contraseñas, ni claves de API, ni tokens. Los archivos
  locales (`monitoring/admin-api-key`, `monitoring/grafana/admin-credentials`) van en `.gitignore`.
- **Idioma** (Principio VII): identificadores en inglés; Javadoc, descripciones de métricas, mensajes de validación,
  comentarios de scripts y README en español.
- **Sin** Docker, Redis, alertas ni métricas por jugador/liga/usuario (Assumptions, fuera de alcance).

---

## Fase 1: Setup (línea de base de la medición)

**Propósito**: dejar medida la variante **caché anterior** antes de tocar `CacheConfig` (FR-010; plan, "Orden de
implementación", paso 1). **Ninguna tarea de la Fase 2 ni de la US1 puede empezar antes de cerrar T003.**

- [X] T001 Confirmar la línea de base: `cd backend; ./gradlew build` da BUILD SUCCESSFUL en la rama sin cambios de
  código, y anotar la cantidad de tests que corren (para comparar en T038). No se modifica ningún archivo.
  **Línea de base**: BUILD SUCCESSFUL, 2368 tests (2 saltados, 0 fallas) en 215 clases.
- [X] T002 Crear el script de medición `monitoring/measure-ranking.ps1` (research R7; quickstart §1.1), compatible
  con Windows PowerShell 5.1 y PowerShell 7, sin dependencias nuevas:
  - Parámetros: `-BaseUrl` (`http://localhost:8080`), `-Query` (`page=0&size=20`), `-Requests` (`1000`),
    `-Warmup` (`20`), `-Variant` (obligatorio, `ValidateSet('cache-anterior','sin-cache','cache-nuevo')`) y
    `-ApiKeyFile` (por defecto `monitoring/admin-api-key`, resuelto relativo a la raíz del repo con `$PSScriptRoot`).
  - Usa un único `System.Net.Http.HttpClient` (no `Invoke-WebRequest`) contra `GET {BaseUrl}/players/ranking?{Query}`;
    hace `-Warmup` consultas sin contar y después `-Requests` consultas medidas con un
    `System.Diagnostics.Stopwatch` por consulta. Si una respuesta no es 2xx, termina con un mensaje en español.
  - **Cliente**: imprime N, promedio, p95 (por orden, índice `ceil(0.95*N)-1`) y máximo en ms.
  - **Servidor** (solo si existe el archivo de clave): lee `GET {BaseUrl}/actuator/prometheus` con la cabecera
    `X-API-Key` antes y después de las consultas medidas; sobre la diferencia de
    `http_server_requests_seconds_{count,sum,bucket}` con `uri="/players/ranking"` (sumando las series de todos
    los `status`/`outcome`) calcula Δcount, promedio (Δsum/Δcount, en ms) y el p95 por interpolación lineal de
    buckets (la de `histogram_quantile`). Si existen las series `cache_gets_total{cache="quotes-ranking",...}`,
    calcula Δhit, Δmiss y el índice de aciertos; si no existen, imprime "n/d".
  - Imprime una tabla final con `Variant`, fecha y las cifras, lista para copiar a `medicion-ranking.md`. Nunca
    imprime la clave de API. Comentarios y mensajes en español.
- [X] T003 Medir la variante **caché anterior** con el código actual (`ConcurrentMapCacheManager`, sin cambios en
  `CacheConfig`), como dice quickstart §1.2.A: levantar `cd backend; ./gradlew bootRun
  --args="--futbolmarket.quotes.recalculation.on-startup=false"`, esperar `GET /actuator/health` → `UP` y correr
  `powershell -ExecutionPolicy Bypass -File monitoring/measure-ranking.ps1 -Variant cache-anterior`. Crear
  `specs/013-ranking-metricas-grafana/medicion-ranking.md` con: procedimiento (enlace a quickstart §1), fecha,
  descripción genérica del equipo (CPU, RAM, SO; sin nombres de personas ni de máquinas), versión de Java, N,
  warmup, la consulta exacta, y una tabla con una fila por variante (A. caché anterior, B. sin caché, C. caché
  nuevo) y columnas promedio/p95 cliente, promedio/p95 servidor, índice de aciertos. Completar solo la fila A
  (aciertos "n/d": el `ConcurrentMapCacheManager` no publica métricas). Dejar B y C como "pendiente".

**Checkpoint**: la línea de base de la medición está anotada; recién ahora se puede tocar el caché.

---

## Fase 2: Fundacional (dependencias)

**Propósito**: lo que bloquea a la US1. La US2 no depende de esta fase (puede arrancar en paralelo después de la
Fase 1, o incluso antes si no se toca `build.gradle` al mismo tiempo).

- [X] T004 Agregar en `backend/build.gradle`, en el bloque `dependencies`, sin versión y con un comentario en
  español que remita a research R2: `implementation 'org.springframework.boot:spring-boot-starter-cache'` y
  `implementation 'com.github.ben-manes.caffeine:caffeine'`. Verificar con
  `./gradlew dependencies --configuration runtimeClasspath` que aparecen `spring-context-support`,
  `spring-boot-cache` y `caffeine` 3.2.4 (si el BOM resuelve otra 3.2.x, anotarla en el Javadoc de
  `QuoteCacheManager` en T008). `./gradlew build` sigue en verde sin otros cambios.

**Checkpoint**: classpath listo para Caffeine y la autoconfiguración de métricas de caché.

---

## Fase 3: User Story 1 - Ranking rápido bajo consultas frecuentes (Priority: P1) 🎯 MVP (junto con US2)

**Objetivo**: los cachés `quotes-ranking` y `quotes-current` pasan a Caffeine con tope, vencimiento de seguridad y
estadísticas, configurables y validados al arrancar; mantienen la invalidación de hoy, garantizan que ninguna
consulta que empieza después de un cambio confirmado recibe datos viejos, y publican aciertos y fallos en
`/actuator/prometheus`. El resultado del ranking y de la cotización actual no cambia.

**Prueba independiente**: pedir dos veces la misma hoja del ranking → respuestas idénticas, `cache_gets_total`
de `quotes-ranking` suma un `miss` y un `hit`; disparar recálculo, recotización y cambio de estrategia → la
siguiente consulta trae datos nuevos (también con una consulta en vuelo); pedir más combinaciones que el tope →
`cache_size` ≤ tope. Ver quickstart §2.

### Configuración (`config/`)

- [X] T005 [P] [US1] Crear `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/config/QuoteCacheProperties.java`
  (research R5; data-model §1.1): `record QuoteCacheProperties(@Valid @NotNull Spec ranking, @Valid @NotNull Spec
  current)` con `@ConfigurationProperties("futbolmarket.cache")` y `@Validated`, y un record anidado
  `Spec(@NotNull @Positive Long maximumSize, @NotNull Duration expireAfterWrite)` cuyo constructor compacto lanza
  `IllegalArgumentException` si `expireAfterWrite` es cero o negativo. Mensajes en español que nombran la propiedad,
  con el estilo de `config/QuoteRecalculationProperties.java` (por ejemplo: "El tope del caché del ranking
  (futbolmarket.cache.ranking.maximum-size) tiene que ser mayor que cero."). Como `Spec` se comparte entre los dos
  cachés, el mensaje de `@Positive`/`@NotNull` puede nombrar la propiedad relativa (`maximum-size`) y el binder de
  Boot agrega el prefijo completo; verificar en T007 que el mensaje final de arranque incluye
  `futbolmarket.cache.ranking.` o `futbolmarket.cache.current.` y ajustar si no. Javadoc en español.
  **Ajuste (T007)**: el error de enlace de las anotaciones nombra el campo en camelCase
  (`ranking.maximumSize`), así que toda la validación quedó en el constructor de
  `QuoteCacheProperties`, con mensajes que nombran la propiedad completa (research R5).
- [X] T006 [US1] Agregar a `backend/src/main/resources/application.yaml` el bloque
  `futbolmarket.cache.ranking.{maximum-size: 500, expire-after-write: 10m}` y
  `futbolmarket.cache.current.{maximum-size: 5000, expire-after-write: 10m}`, con un comentario en español que
  remita a la spec (Assumptions) y a research R5; y registrar `QuoteCacheProperties.class` en la lista de
  `@EnableConfigurationProperties` de `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/config/ApplicationConfig.java`.
  No tocar `application-local.yml` ni `application-test.yml`. Depende de T005.
- [X] T007 [US1] Crear `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/config/QuoteCachePropertiesTest.java`
  con el estilo de `QuoteRecalculationPropertiesTest` (`ApplicationContextRunner` con
  `@EnableConfigurationProperties(QuoteCacheProperties.class)` y validación): los valores por defecto de
  `application.yaml` enlazan (500 / 10m y 5000 / 10m, leídos con `ConfigurationFiles` si ese apoyo existente lo
  permite sin agregarle métodos); y con `maximum-size=0`, `maximum-size=-1`, `expire-after-write=0s`,
  `expire-after-write=-1m` o una propiedad ausente, el contexto no arranca y el mensaje nombra la propiedad
  (FR-003). Depende de T005 y T006.

### Caché con generación y manager

- [X] T008 [US1] Crear `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/config/GenerationGuardedCaffeineCache.java`
  (research R3; data-model §1.3): `extends org.springframework.cache.caffeine.CaffeineCache`, con
  `AtomicLong generation`, `ThreadLocal<Long> lookupGeneration` y `ReentrantReadWriteLock lock`.
  - Constructor `(String name, com.github.benmanes.caffeine.cache.Cache<Object,Object> cache, boolean allowNullValues)`
    que delega en `super`.
  - Sobrescribir **todas** las lecturas que usa `CacheAspectSupport` sin `sync`: `lookup(Object key)` (el que
    llaman `get(key)` y `get(key, Class)` en `AbstractValueAdaptingCache`): si `super.lookup` devuelve `null`, anota
    `lookupGeneration.set(generation.get())`; si devuelve valor, `lookupGeneration.remove()`. Verificar en el
    código de Spring Framework 7.0.x qué método invoca `CacheAspectSupport.doGet` (`cache.get(key)` →
    `lookup`) y, si hay otro camino de lectura sin `sync` (por ejemplo `retrieve(key)` para reactivos), dejarlo sin
    proteger pero documentado.
  - `put(key, value)`: bajo `lock.readLock()`, lee y borra `lookupGeneration`; guarda con `super.put` solo si no
    había anotación o si coincide con `generation.get()`; si no, descarta (log en `DEBUG`, sin datos de la clave).
    Aplicar la misma regla a `putIfAbsent` si se sobrescribe (si no, documentar que no lo usa `@Cacheable`).
  - `clear()` e `invalidate()`: bajo `lock.writeLock()`, `generation.incrementAndGet()` y **después**
    `super.clear()` / `super.invalidate()`. `evict` / `evictIfPresent` no cambian.
  - No sobrescribir `get(key, Callable)` (`sync = true`, no se usa).
  - Javadoc en español con el problema de la consulta en vuelo, la garantía (FR-005) y los límites aceptados de R3
    (una anotación por hilo, el 404 que deja la anotación hasta la próxima búsqueda, `sync = true` no protegido,
    consultas simultáneas que pueden llegar las dos a la base). Que sea subclase y no decorador para que
    `CaffeineCacheMeterBinderProvider` lo reconozca (R3, "Por qué es una subclase").
- [X] T009 [US1] Crear `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/config/QuoteCacheManager.java`
  (research R1, R4, R6): `extends org.springframework.cache.caffeine.CaffeineCacheManager`.
  - Constructor `(QuoteCacheProperties properties, com.github.benmanes.caffeine.cache.Ticker ticker,
    java.util.concurrent.Executor executor)` (`executor` puede ser `null` = el de Caffeine por defecto).
  - Llama a `setCacheNames(List.of())` (modo estático) y después a `registerCustomCache(CacheConfig.QUOTES_RANKING,
    build(properties.ranking()))` y `registerCustomCache(CacheConfig.QUOTES_CURRENT, build(properties.current()))`,
    donde `build(spec)` arma `Caffeine.newBuilder().maximumSize(spec.maximumSize()).expireAfterWrite(spec.expireAfterWrite())
    .recordStats().ticker(ticker)` y, si `executor != null`, `.executor(executor)`.
  - Sobrescribe `protected Cache adaptCaffeineCache(String name, com.github.benmanes.caffeine.cache.Cache<Object,Object> cache)`
    para devolver `new GenerationGuardedCaffeineCache(name, cache, isAllowNullValues())`.
  - Verificar contra Spring Framework 7.0.x que `registerCustomCache` pasa por `adaptCaffeineCache` y que, en modo
    estático, `getCache("otro")` devuelve `null` y `getCacheNames()` devuelve solo los dos nombres; si alguna de las
    dos cosas no se cumple, ajustar (por ejemplo sobrescribiendo `getCache`) y anotarlo en el Javadoc.
  - Javadoc en español: por qué Caffeine (W-TinyLFU, tope, vencimiento, stats), valores propios por caché (FR-030)
    y que los cachés se registran al construirse porque `CacheMetricsRegistrarConfiguration` registra solo los que
    existen en ese momento (R4).
- [X] T010 [US1] Modificar `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/config/CacheConfig.java`
  (plan, "Diseño por historia US1"; research R7): el `@Bean cacheManager(QuoteCacheProperties properties)` devuelve
  `new QuoteCacheManager(properties, Ticker.systemTicker(), null)` y lleva
  `@ConditionalOnProperty(name = "spring.cache.type", havingValue = "caffeine", matchIfMissing = true)`. El nombre
  del bean sigue siendo `cacheManager` (la etiqueta `cache_manager` de las métricas depende de eso). Las
  constantes `QUOTES_CURRENT` y `QUOTES_RANKING` y `@EnableCaching` no cambian. Actualizar el Javadoc de la clase
  (ya no es `ConcurrentMapCacheManager`; entradas con tope y vencimiento; `spring.cache.type=none` solo para la
  variante "sin caché" de la medición).
- [X] T011 [US1] Cambio autorizado de un test existente (research R18): en
  `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/config/CacheConfigTest.java`, reemplazar **solo**
  `isInstanceOf(ConcurrentMapCacheManager.class)` por `isInstanceOf(CaffeineCacheManager.class)` y el import
  `org.springframework.cache.concurrent.ConcurrentMapCacheManager` por
  `org.springframework.cache.caffeine.CaffeineCacheManager`. Nada más en ese archivo. Correr `CacheConfigTest`,
  `QuoteCacheIT`, `RankingRoutingIT` y `QuoteControllerIT`: pasan sin otros cambios.

### Tests unitarios del caché (`config/`)

- [X] T012 [P] [US1] Crear `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/config/FakeTicker.java`
  (research R6): clase de test package-private que implementa `com.github.benmanes.caffeine.cache.Ticker` con un
  `AtomicLong` de nanos y un método `advance(Duration)`. Sin `guava-testlib`.
- [X] T013 [US1] Crear `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/config/QuoteCacheManagerTest.java`
  (unitario, sin Spring; `new QuoteCacheManager(props, fakeTicker, Runnable::run)`, y
  `((com.github.benmanes.caffeine.cache.Cache<?,?>) cache.getNativeCache()).cleanUp()` antes de verificar):
  - es un `CaffeineCacheManager`; `getCacheNames()` tiene exactamente `quotes-ranking` y `quotes-current`;
    `getCache("otro")` es `null`; los dos cachés son `GenerationGuardedCaffeineCache`;
  - **tope** (SC-004): con `ranking.maximum-size=3` y `current.maximum-size=5`, después de 10 `put` distintos en
    cada uno y `cleanUp()`, `estimatedSize()` ≤ 3 y ≤ 5 respectivamente (valores distintos por caché, FR-030);
  - **vencimiento** (FR-002): con `expire-after-write=10m`, una entrada sigue a los 9m59s y no se devuelve a los
    10m01s (`fakeTicker.advance`), en los dos cachés y con vencimientos distintos entre sí;
  - **estadísticas**: `stats()` cuenta un `miss` y un `hit` después de `get` sin entrada, `put` y `get` (prueba de
    que `recordStats()` está prendido).
  Depende de T009 y T012.
- [X] T014 [P] [US1] Crear `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/config/GenerationGuardedCaffeineCacheTest.java`
  (unitario, un solo hilo, la carrera de research R3 armada a mano):
  - `get(k)` sin entrada → `clear()` → `put(k, v)` → `get(k)` es `null` (el `put` se descartó);
  - lo mismo con `invalidate()` en lugar de `clear()`;
  - `get(k)` sin entrada → `put(k, v)` sin vaciado en el medio → `get(k)` devuelve `v`;
  - `put(k, v)` sin búsqueda previa → se guarda;
  - `get(k)` con entrada (borra la anotación) → `clear()` → `put(k2, v2)` sin búsqueda de `k2` → se guarda;
  - un `put` consume la anotación: `get(k)` sin entrada → `put(k, v)` → `clear()` → `put(k, v)` directo → se guarda;
  - `evict(k)` no sube la generación: `get(k)` sin entrada → `evict(otra)` → `put(k, v)` → se guarda;
  - con dos hilos (`ExecutorService` de 2 hilos y `CountDownLatch`): la anotación de un hilo no afecta al otro.
  Depende de T008 y T012.

### Tests de integración del caché

- [X] T015 [US1] Crear `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/service/quote/QuoteCacheConcurrencyIT.java`
  (research R6; SC-003; FR-005; `@SpringBootTest`, `@ActiveProfiles("test")`, datos con `QuoteDatabase` sin
  modificarla): un `@MockitoSpyBean` sobre `QuoteRepository` que, en la primera llamada a `findRanking` de la
  prueba, avisa con un `CountDownLatch` que entró y se frena en otro `CountDownLatch` hasta que el test lo libera,
  después de haber leído los datos viejos (`callRealMethod()` primero, esperar después). Tres pruebas, una por
  cambio, cada una con el mismo esquema:
  1. hilo A pide `QuoteQueryService.getRanking(...)` y queda frenado con el resultado viejo;
  2. el hilo del test confirma el cambio: **recálculo** (`QuoteRecalculationService`), **recotización** (la
     operación de recotización de la 007) o **cambio de la estrategia activa** (`StrategyChangeService`), con su
     invalidación de siempre;
  3. se libera A, que termina y hace su `put` (descartado);
  4. una consulta B que empieza después devuelve los datos nuevos (cotizaciones nuevas o la estrategia nueva y su
     orden), y `findRanking` se llamó de nuevo para B.
  Una cuarta prueba hace lo mismo con `getCurrent(playerId)` y `QuoteRepository` del método que use
  (FR-030). Timeouts en los `await` (por ejemplo 10 s) para que el test nunca cuelgue. Usar solo métodos públicos de
  los servicios existentes; si algún cambio no es invocable así, armarlo con los apoyos existentes sin modificarlos.
- [X] T016 [P] [US1] Crear `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/e2e/CacheMetricsIT.java`
  (contracts/metrics.md §2; MockMvc, perfil test, credencial `ADMIN` con `ActuatorITSupport`/`AuthTestHelper` sin
  modificarlos). Lee `/actuator/prometheus` y filtra por `cache="quotes-ranking"` o `cache="quotes-current"`:
  - desde el arranque existen, para los dos cachés, `cache_gets_total` con `result="hit"` y `result="miss"`,
    `cache_evictions_total`, `cache_size` (y, si Micrometer 1.17.1 los publica, `cache_puts_total` y
    `cache_eviction_weight_total`: **verificar** los nombres reales de esa versión, research R4, y dejar en el test
    solo los que existen), con `cache_manager="cacheManager"`;
  - dos `GET /players/ranking?page=0&size=10` iguales suman exactamente +1 `miss` y +1 `hit` en `quotes-ranking`
    (comparar contra la lectura previa, no contra cero) y las dos respuestas son idénticas (US1, escenario 1);
  - `GET /players/ranking?page=-1` y `?size=500` responden el mismo 400 que hoy y no cambian `hit` ni `miss` (FR-007);
  - dos `GET /players/{id}/quotes/current` del mismo jugador suman +1 `miss` y +1 `hit` en `quotes-current`; un
    jugador inexistente da 404 y no suma `cache_size` (FR-030);
  - una ráfaga de 1.000 consultas iguales al ranking tiene ≥ 95% de aciertos medidos con la diferencia de
    `cache_gets_total` (SC-002);
  - sin credencial `/actuator/prometheus` da 401 y con `USER` 403 (sin cambios, FR-008).
  Si los datos de otras pruebas del mismo contexto llenan el caché, vaciarlo con `QuoteCacheInvalidator` al empezar
  cada prueba.
- [X] T017 [US1] Verificar la invalidación de punta a punta y el arranque inválido: agregar a
  `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/e2e/CacheMetricsIT.java` una prueba que guarde una hoja,
  cambie la estrategia activa como `ADMIN` por HTTP (endpoints de la 007) y verifique que la siguiente consulta
  informa la estrategia nueva y suma un `miss` (US1, escenario 4); y agregar a
  `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/config/QuoteCachePropertiesTest.java` (si no quedó en T007)
  que el contexto completo con `futbolmarket.cache.ranking.maximum-size=0` no arranca.

### Medición después del cambio

- [X] T018 [US1] Medir las variantes **sin caché** y **caché nuevo** con el código nuevo (quickstart §1.2.B y C;
  research R7), con la misma máquina, base, `-Query` y `-Requests` que T003:
  `./gradlew bootRun --args="--futbolmarket.quotes.recalculation.on-startup=false --spring.cache.type=none"` →
  `-Variant sin-cache`; `./gradlew bootRun --args="--futbolmarket.quotes.recalculation.on-startup=false"` →
  `-Variant cache-nuevo`. Antes de medir B, comprobar que con `spring.cache.type=none` el contexto levanta (Boot
  crea un `NoOpCacheManager`) y que `QuoteCacheInvalidator` no falla contra él; si fallara, documentar el ajuste
  mínimo en `CacheConfig` y en research. Completar las filas B y C de
  `specs/013-ranking-metricas-grafana/medicion-ranking.md`, más: la razón B/C de promedios (SC-001: ≥ 5; ver el ajuste abajo), los
  promedios de A y C con su diferencia absoluta y relativa y una línea sobre el ruido (por ejemplo, repetir A o C
  dos veces y anotar la dispersión), y el índice de aciertos de C (SC-002: ≥ 95%, se espera 1 fallo). Si SC-001 o
  SC-002 no se cumplen, anotarlo y no cerrar la tarea hasta revisar la causa.
  **Ajuste (2026-11-25)**: la razón B/C medida fue 2,41 en el servidor, por el costo fijo de cada pedido
  (research R7). SC-001 se reescribió sin umbral: se informan el factor y el ahorro absoluto, y C se corre
  una segunda vez para comparar con A con dos corridas de cada lado.
  **Resultado**: C quedó con tres corridas (02:29, 03:41 y 03:42). Factor B / C 2,44 y ahorro de 4,887 ms en el
  servidor; C contra A +0,49 ms, dentro del ruido; 100 % de aciertos. SC-001 y SC-002 se cumplen
  (medicion-ranking.md).

**Checkpoint**: US1 completa. Los dos cachés son Caffeine con tope, vencimiento y stats; la garantía estricta tiene
su IT; las métricas salen en `/actuator/prometheus`; la medición está documentada.

---

## Fase 4: User Story 2 - Métricas de negocio del mercado (Priority: P1) 🎯 MVP

**Objetivo**: tres contadores nuevos (`futbolmarket.market.trades`, `.tokens.traded`, `.credits.traded`) con la
etiqueta `counterparty` (`initial_order` | `user`), seis series en cero desde el arranque, que suman solo
operaciones confirmadas de `buy` y `sell`, por el mismo canal y control de acceso de la 012.

**Prueba independiente**: backend recién levantado → las seis series en `0.0`; escenario con compras, ventas,
cruces parciales, pendientes que se cruzan después, cancelaciones y rechazos → las métricas coinciden con las
operaciones guardadas en el mercado. Ver quickstart §3.

**Independencia**: no depende de la Fase 2 ni de la US1. Se puede hacer en paralelo con la Fase 3 (archivos
distintos).

### Catálogo y aspecto (`observability/`)

- [X] T019 [P] [US2] Crear `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/observability/TradeCounterparty.java`
  (research R8; data-model §2.1): enum `INITIAL_ORDER("initial_order")`, `USER("user")` con `tag()` y
  `static TradeCounterparty from(OwnerType sellerType)` (`ADMIN` → `INITIAL_ORDER`, `USER` → `USER`; `null` →
  `IllegalArgumentException` con mensaje en español). Mismo estilo que `observability/OrderResult.java`. Javadoc en
  español.
- [X] T020 [US2] Modificar `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/observability/BusinessMetrics.java`
  (research R8, R11; contracts/metrics.md §1):
  - constantes `TRADES = "futbolmarket.market.trades"`, `TOKENS_TRADED = "futbolmarket.market.tokens.traded"`,
    `CREDITS_TRADED = "futbolmarket.market.credits.traded"` y la de la etiqueta `COUNTERPARTY = "counterparty"`;
  - descripciones en español (las de data-model §2.1), **sin `baseUnit`** (Micrometer lo agregaría al nombre);
  - en `bindTo`, dar de alta las 3 × 2 = 6 series en cero con cada `TradeCounterparty`;
  - `public void recordTrade(OwnerType sellerType, int quantity, BigDecimal total)` incrementa
    `TRADES` en 1, `TOKENS_TRADED` en `quantity` y `CREDITS_TRADED` en `total.doubleValue()` en la serie de
    `TradeCounterparty.from(sellerType)`;
  - actualizar el Javadoc: el catálogo pasa de 30 a 36 series, y la precisión de créditos (research R11).
  No cambiar nombres, etiquetas ni descripciones de las métricas de la 012 (FR-022); no agregar los nombres nuevos
  al conjunto `NAMES` que usa `BusinessMetricsTest` si eso cambiara lo que ese test cuenta (verificarlo leyendo el
  test, sin modificarlo).
- [X] T021 [US2] Modificar `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/observability/BusinessMetricsAspect.java`
  (research R9, R10): `measureBuy` y `measureSell` guardan el resultado de `measureOrder(...)`, lo pasan por un
  nuevo `private void recordTrades(Object result)` y devuelven **el mismo objeto**. `recordTrades` actúa solo si
  `result instanceof BookOutcome outcome` y, dentro de `recordSafely(BusinessMetrics.TRADES, ...)`, recorre
  `outcome.trades()` y llama `metrics.recordTrade(trade.sellerType(), trade.quantity(), trade.total())`.
  `measureCancel` no cambia (FR-016). Los pointcuts siguen siendo texto. Agregar al Javadoc de la clase que `buy` y
  `sell` son la única ruta que ejecuta operaciones (R10) y que una feature futura que cruce el libro por otro
  proceso tiene que llamar a `BusinessMetrics.recordTrade`; y que el aspecto envuelve a la transacción
  (`@Order(HIGHEST_PRECEDENCE)`), así que cuando `proceed()` vuelve bien el commit ya ocurrió (FR-014).

### Tests unitarios de US2

- [X] T022 [P] [US2] Crear `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/observability/TradeCounterpartyTest.java`:
  `from(ADMIN)` es `INITIAL_ORDER`, `from(USER)` es `USER`, los `tag()` son `initial_order` y `user`, y `from(null)`
  lanza. Depende de T019.
- [X] T023 [P] [US2] Crear `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/observability/BusinessMetricsTradesTest.java`
  (`SimpleMeterRegistry`, mismo estilo que `BusinessMetricsTest` sin modificarlo):
  - después de `bindTo` existen las 6 series (3 nombres × 2 `counterparty`) con valor 0 y las descripciones están en
    español (FR-017);
  - `recordTrade(ADMIN, 3, 309.00)` deja trades=1, tokens=3, credits=309.00 (`within(0.005)`) en `initial_order` y
    0 en `user`;
  - varias llamadas acumulan y conservan centavos (por ejemplo 10.25 + 0.10 = 10.35, FR-019);
  - ninguna serie nueva tiene otra etiqueta que `counterparty` (FR-020);
  - las series de la 012 siguen siendo las mismas (cantidad y nombres) (FR-022).
  Depende de T020.
- [X] T024 [US2] Crear `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/observability/BusinessMetricsAspectTradesTest.java`
  (mismo esquema que `BusinessMetricsAspectTest`, sin modificarlo; `ProceedingJoinPoint` de Mockito y
  `BusinessMetrics` sobre `SimpleMeterRegistry`; `BookOutcome` y `Trade` armados con sus constructores públicos o
  con los apoyos de test existentes del mercado, sin modificarlos):
  - `buy` con un `BookOutcome` sin operaciones (pendiente) → no suma nada; con una operación contra `ADMIN` → +1 /
    +quantity / +total en `initial_order`; con dos operaciones contra `USER` → +2 / Σ quantity / Σ total en `user`;
  - `sell` con una operación → suma en la serie del `sellerType` de esa operación;
  - `buy` o `sell` que lanzan (rechazo del dominio o falla al grabar) → relanza la **misma** excepción y no suma
    operaciones (las de órdenes de la 012 sí registran `REJECTED`, como hoy);
  - `cancel` con un `BookOutcome` → no suma operaciones;
  - si `BusinessMetrics.recordTrade` lanza `RuntimeException` (mock/spy), el aspecto devuelve el mismo objeto de
    `proceed()` (`isSameAs`) y no propaga (FR-021).
  Depende de T021.

### Test de integración de US2

- [X] T025 [US2] Crear `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/e2e/MarketTradeMetricsIT.java`
  (SC-005; contracts/metrics.md §1; MockMvc, perfil test, usuarios con `AuthTestHelper` y datos con
  `MarketDatabase`, sin modificarlos; lecturas de `/actuator/prometheus` como `ADMIN` y comparación por diferencia
  contra una lectura previa):
  1. las 6 series existen (US2, escenario 1);
  2. un usuario compra 3 tokens de un jugador cotizado sin otras ventas → trades +1, tokens +3 y credits +total de la
     operación (leído de la respuesta o de `TradeRepository`) en `initial_order` (escenario 2);
  3. dos usuarios venden y una compra se cruza contra las dos → trades +2, tokens y credits por la suma, en `user`
     (escenario 3);
  4. una venta que queda pendiente no suma; cuando una compra posterior la cruza, suma exactamente una vez
     (escenario 4), incluido un cruce parcial (Edge Cases);
  5. compra con saldo insuficiente, venta sin tokens y compra de jugador sin cotización → sin cambio (escenario 5);
  6. cancelación de una pendiente → sin cambio (escenario 7);
  7. al final del escenario, la suma de cada métrica (las dos series) coincide con la cantidad de filas, la suma de
     `quantity` y la suma de `total` de las operaciones guardadas en el mercado (`TradeRepository`), con
     `within(0.005)` para créditos (SC-005);
  8. `/actuator/prometheus` sin credencial → 401 y con `USER` → 403 (escenario 8).
  El escenario 6 (falla al grabar después del cruce) y el 9 (resultado idéntico con y sin métricas) quedan cubiertos
  por T024; si se puede provocar la falla al grabar con un `@MockitoSpyBean` sin tocar servicios, agregarlo acá.

**Checkpoint**: US2 completa. Las seis series salen en `/actuator/prometheus` y cuentan exactamente lo confirmado.

---

## Fase 5: User Story 3 - Dashboard de Grafana listo para usar (Priority: P2)

**Objetivo**: conexión a Prometheus y dashboard versionados en `monitoring/grafana/`, que aparecen solos al levantar
Grafana OSS ≥ 11.0 con el script del repo, sin Docker y sin credenciales versionadas.

**Prueba independiente**: con backend y Prometheus corriendo, `start-grafana.ps1` levanta Grafana, el data source y
el dashboard aparecen sin pasos manuales, y los paneles muestran datos después de unas consultas y operaciones.
Ver quickstart §4.

**Depende de**: los nombres de métricas de US1 (T010, T016) y US2 (T020). Los archivos de esta fase se pueden
escribir antes, pero `GrafanaDashboardIT` (T032) solo pasa cuando existen esas métricas.

### Archivos versionados de Grafana

- [X] T026 [P] [US3] Crear `monitoring/grafana/provisioning/datasources/prometheus.yaml` exactamente como
  contracts/grafana-dashboard.md §3 (`apiVersion: 1`; `name: Prometheus futbol-market`, `type: prometheus`,
  `uid: futbol-market-prometheus`, `access: proxy`, `url: http://localhost:9090`, `isDefault: true`,
  `editable: false`, `jsonData.timeInterval: 15s`), con comentarios en español. Sin credenciales.
- [X] T027 [P] [US3] Crear `monitoring/grafana/provisioning/dashboards/futbol-market.yaml` exactamente como
  contracts/grafana-dashboard.md §4 (provider `type: file`, `folder: futbol-market`, `disableDeletion: true`,
  `allowUiUpdates: false`, `updateIntervalSeconds: 10`, `options.path: $FUTBOLMARKET_GRAFANA_DASHBOARDS`), con
  comentarios en español.
- [X] T028 [P] [US3] Crear `monitoring/grafana/dashboards/futbol-market.json` (contracts/grafana-dashboard.md §5;
  research R16), en formato de exportación sin envoltorio de API: `uid: "futbol-market"`, `title: "futbol-market"`,
  `schemaVersion: 39`, `time: {from: "now-6h", to: "now"}`, `refresh: "30s"`, `editable: false`, ids de panel
  únicos y `gridPos` sin superposiciones. Tres filas (`type: "row"`) con sus paneles y la PromQL **literal** del
  contrato:
  - **Negocio**: stats de operaciones, tokens y créditos en el rango (créditos con `decimals: 2`); series temporales
    de operaciones, tokens y créditos por período con dos targets (total y `sum by (counterparty)`), con overrides
    por nombre que traducen `initial_order` → "orden inicial del superusuario" y `user` → "usuario"; órdenes
    pedidas por `operation`/`result`; stat de órdenes abiertas.
  - **Ranking**: p95 de `/players/ranking` (unidad `s`); stat y serie temporal de respuestas desde el caché
    (unidad `percentunit`).
  - **General**: pedidos por segundo; tasa de errores 4xx y 5xx (`percentunit`), excluyendo `/actuator.*`.
  Todo panel con `targets` lleva `"datasource": {"type": "prometheus", "uid": "futbol-market-prometheus"}`, y cada
  target también. Títulos y descripciones de paneles en español. Solo `increase`/`rate` sobre contadores (FR-027).
  **Cambio (T033)**: los tres *stats* de totales de la fila Negocio muestran el contador
  (`sum(futbolmarket_market_trades_total)` y los equivalentes), con el título "(desde el arranque
  del backend)", 0 decimales en operaciones y tokens y 2 en créditos (research R16).
- [X] T029 [P] [US3] Crear `monitoring/grafana/admin-credentials.example` (research R14; contracts §2): una línea de
  comentario en español que diga que se copia a `monitoring/grafana/admin-credentials` (ignorado por git) y se
  completa, más `admin_user=admin` y `admin_password=` vacío.
- [X] T030 [P] [US3] Agregar a `.gitignore` (raíz), junto a `monitoring/admin-api-key` y `monitoring/data/`, las
  líneas `monitoring/grafana/data/` y `monitoring/grafana/admin-credentials`. Verificar con
  `git check-ignore -v monitoring/grafana/admin-credentials monitoring/grafana/data/x` y que
  `admin-credentials.example` **no** queda ignorado.
- [X] T031 [US3] Crear `monitoring/grafana/start-grafana.ps1` (research R12, R13, R14; contracts §1), compatible con
  PowerShell 5.1 y 7:
  - `param([string]$GrafanaHome = $env:GRAFANA_HOME)`; si falta, o no existe `$GrafanaHome\bin\grafana.exe`,
    termina con un mensaje en español y código distinto de 0;
  - calcula la raíz del repo desde `$PSScriptRoot` y pone, **solo para el proceso hijo**, `GF_PATHS_PROVISIONING`,
    `GF_PATHS_DATA`, `GF_PATHS_LOGS`, `GF_PATHS_PLUGINS` y `FUTBOLMARKET_GRAFANA_DASHBOARDS` con las rutas absolutas
    de la tabla del contrato (crea `monitoring/grafana/data/` si no existe);
  - si existe `monitoring/grafana/admin-credentials`, lo lee como `clave=valor`, ignora líneas vacías y con `#`, y
    pone `GF_SECURITY_ADMIN_USER` / `GF_SECURITY_ADMIN_PASSWORD` solo si el valor no está vacío; nunca imprime la
    contraseña (sí puede decir "se aplicó la credencial local" o "sin credencial local: admin/admin");
  - lanza `& "$GrafanaHome\bin\grafana.exe" server --homepath "$GrafanaHome"` y restaura las variables de entorno
    del proceso actual al terminar (bloque `try/finally`), para que no queden en la sesión del usuario;
  - imprime la URL `http://localhost:3000/d/futbol-market` al arrancar. Comentarios en español.

### Test del dashboard

- [X] T032 [US3] Crear `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/e2e/GrafanaDashboardIT.java`
  (research R17; contracts/grafana-dashboard.md §6; MockMvc, perfil test, `ADMIN` con `ActuatorITSupport` sin
  modificarlo). Rutas relativas a `backend/` (el directorio de trabajo de los tests de Gradle):
  1. `../monitoring/grafana/dashboards/futbol-market.json` parsea con `tools.jackson.databind.json.JsonMapper`, y
     tiene `uid` `futbol-market` y `schemaVersion` 39;
  2. junta los `expr` de todos los `targets` (también los de paneles anidados en filas) y extrae con regex los
     nombres de métrica con prefijo `futbolmarket_`, `http_server_requests_` o `cache_`; hace un
     `GET /players/ranking` (para que existan los buckets de `uri="/players/ranking"`) y verifica que cada nombre
     aparece en `/actuator/prometheus`;
  3. todo panel con `targets` tiene `datasource.uid` igual al `uid` leído como texto de
     `../monitoring/grafana/provisioning/datasources/prometheus.yaml`, y no hay paneles con consultas sin
     `datasource`;
  4. `../monitoring/grafana/admin-credentials.example` deja `admin_password` vacío; el archivo local
     `admin-credentials` no se lee aunque exista (SC-007).
  Es un test de archivos: no necesita Grafana ni Prometheus. Depende de T016, T020 y T026 a T029.
- [X] T033 [US3] Validación manual de Grafana (quickstart §4, pasos 1 a 7): con Grafana OSS ≥ 11.0 descomprimido,
  Prometheus ≥ 2.55.0 con `monitoring/prometheus.yml` sin cambios y el backend en local, correr
  `start-grafana.ps1` y comprobar: data source "Prometheus futbol-market" no editable con *Test* OK; dashboard
  `futbol-market` en la carpeta `futbol-market` sin pasos manuales; log de `monitoring/grafana/data/log/` sin
  errores de provisioning (si Grafana se queja por carpetas que no existen, agregar un archivo mínimo válido según
  research R15, nunca un `.gitkeep`); paneles con datos tras compras, consultas al ranking y unos 404; sin saltos
  negativos al reiniciar el backend; "No data" sin errores con Prometheus detenido. Anotar la versión de Grafana
  con la que se probó (para T035). Si algún PromQL del contrato no funciona en Grafana, corregirlo en
  `futbol-market.json` y en contracts/grafana-dashboard.md a la vez.
  **Resultado (2026-11-25, Grafana 13.3.2 y Prometheus 3.16.0)**: data source y dashboard provisionados
  sin pasos manuales y paneles con datos. El primer arranque dejó dos errores de provisioning, por las
  carpetas `plugins` y `alerting`; cada una lleva ahora un `futbol-market.yaml` mínimo (research R15) y,
  después de reiniciar, el log no tiene ningún `level=error`. Los tres *stats* de totales pasaron a
  mostrar el contador desde el arranque del backend (research R16).

**Checkpoint**: US3 completa. El dashboard aparece solo y muestra negocio, ranking y tráfico general.

---

## Fase 6: Polish & Cross-Cutting Concerns

**Propósito**: documentación, verificación de credenciales y cierre (definición de terminado).

- [X] T034 [P] Revisar el Javadoc y los comentarios de las clases nuevas y modificadas
  (`config/QuoteCacheProperties`, `config/GenerationGuardedCaffeineCache`, `config/QuoteCacheManager`,
  `config/CacheConfig`, `observability/TradeCounterparty`, `observability/BusinessMetrics`,
  `observability/BusinessMetricsAspect`) en `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/`: en español,
  sin referencias a personas ni máquinas, y con los enlaces a research que correspondan.
- [X] T035 Actualizar la sección "Métricas y monitoreo" de `README.md` (FR-010, FR-029; plan, "US3"), agregando sin
  reemplazar lo de la 012:
  - **Caché del ranking**: tope, vencimiento y propiedades `futbolmarket.cache.*` con sus valores por defecto; que
    la invalidación no cambia; las series `cache_gets_total`, `cache_evictions_total`, `cache_size` y el PromQL del
    índice de aciertos;
  - **Métricas del mercado**: tabla con las tres series nuevas, la etiqueta `counterparty` y sus dos valores;
  - **Grafana**: versión mínima (≥ 11.0) y la versión con la que se probó (T033), Prometheus ≥ 2.55.0, cómo
    descomprimir el zip de Windows, cómo crear `monitoring/grafana/admin-credentials` desde el ejemplo (y que se
    aplica solo en el primer arranque o tras borrar `monitoring/grafana/data/`; sin el archivo, `admin`/`admin`),
    el comando `powershell -ExecutionPolicy Bypass -File monitoring/grafana/start-grafana.ps1 -GrafanaHome C:\grafana`
    y la URL del dashboard;
  - **Medición del ranking**: resumen de la tabla (promedio de A, B y C, razón B/C, diferencia A/C, índice de
    aciertos) y enlace a `specs/013-ranking-metricas-grafana/medicion-ranking.md`.
  Sin Docker. Sin credenciales. Sin nombres de personas ni de máquinas.
- [X] T036 [P] Completar `specs/013-ranking-metricas-grafana/medicion-ranking.md` con una conclusión corta que diga
  si se cumplen SC-001 y SC-002 con los números de T003 y T018, y el enlace de vuelta al README.
- [X] T037 Búsqueda de credenciales (SC-007; quickstart §4.8): correr
  `git grep -nIiE "(password|api[-_]?key|token)\s*[:=]\s*\S" -- monitoring README.md specs/013-ranking-metricas-grafana`
  y verificar que solo aparecen nombres de archivos, nombres de variables o claves vacías. `git status` no muestra
  `monitoring/grafana/admin-credentials` ni `monitoring/grafana/data/`.
- [X] T038 Cierre (quickstart §5; definición de terminado): `cd backend; ./gradlew build` da BUILD SUCCESSFUL con la
  suite completa (incluida la de arquitectura de la 010 y todos los tests nuevos); la cantidad de tests es la de
  T001 más los nuevos; `git diff main -- backend/src/test` muestra como único cambio en tests existentes la
  aserción y el import de `CacheConfigTest` (T011); `./gradlew bootRun` levanta con el perfil local; con
  `--futbolmarket.cache.ranking.maximum-size=0` no arranca y el mensaje nombra la propiedad (quickstart §2.4);
  Swagger no cambió. Repasar los escenarios manuales de quickstart §2 y §3.
  **Cierre (2026-11-25)**: BUILD SUCCESSFUL, 2435 tests (2 saltados, 0 fallas): los 2368 de T001 más 67
  nuevos. Tests existentes modificados: `CacheConfigTest` (T011) y `BusinessMetricsIT` (vocabulario,
  segunda excepción autorizada). `maximum-size=0` no arranca (`QuoteCachePropertiesTest`); no hay
  endpoints nuevos.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Fase 1)**: sin dependencias. **T003 (medición del caché anterior) tiene que cerrarse antes de T004 y de
  cualquier cambio en `config/`**: es la regla de orden del plan.
- **Fundacional (Fase 2)**: depende de la Fase 1. Bloquea solo a la US1.
- **US1 (Fase 3)**: depende de la Fase 2.
- **US2 (Fase 4)**: depende solo de la Fase 1 (para no mezclar código nuevo con la medición A). Es independiente de
  la Fase 2 y de la US1.
- **US3 (Fase 5)**: los archivos (T026 a T031) se pueden escribir en cualquier momento después de la Fase 1; T032 y
  T033 dependen de que existan las métricas de US1 (T010) y US2 (T020).
- **Polish (Fase 6)**: depende de las tres historias. T035 depende de T018 y T033.

### User Story Dependencies

- **US1 (P1)**: después de la Fase 2. No depende de otras historias.
- **US2 (P1)**: después de la Fase 1. No depende de otras historias.
- **US3 (P2)**: usa los nombres de métricas de US1 y US2; su test (T032) las necesita implementadas.

### Within Each User Story

- **US1**: T005 → T006 → T007; T008 → T009 → T010 → T011; T012 → T013 y T014; T010 → T015, T016 → T017; todo →
  T018.
- **US2**: T019 → T020 → T021; T022 tras T019; T023 tras T020; T024 tras T021; T025 tras T021.
- **US3**: T026 a T030 en paralelo; T031 tras T029 y T030; T032 tras T026 a T029 (y US1/US2); T033 tras T031 y
  T032.
- Sin TDD: cada test se escribe después de su implementación y la tarea cierra con el test en verde.

### Parallel Opportunities

- Fase 3: T005 en paralelo con T008 y T012 (archivos distintos); T013 y T014 entre sí; T015 y T016 entre sí.
- Fase 4 completa en paralelo con la Fase 3: no comparten archivos (US1 toca `config/`, `build.gradle` y
  `application.yaml`; US2 toca `observability/`).
- Fase 4: T019 en paralelo con todo lo de US1; T022 y T023 entre sí.
- Fase 5: T026, T027, T028, T029 y T030 en paralelo entre sí y con US1/US2.
- Fase 6: T034 y T036 en paralelo.

---

## Parallel Example: User Story 1

```text
# Después de T004, en paralelo (archivos distintos):
Task: "T005 Crear QuoteCacheProperties en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/config/QuoteCacheProperties.java"
Task: "T008 Crear GenerationGuardedCaffeineCache en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/config/GenerationGuardedCaffeineCache.java"
Task: "T012 Crear FakeTicker en backend/src/test/java/ar/edu/unq/desapp/futbolmarket/config/FakeTicker.java"

# Después de T010, en paralelo:
Task: "T015 QuoteCacheConcurrencyIT en backend/src/test/java/ar/edu/unq/desapp/futbolmarket/service/quote/QuoteCacheConcurrencyIT.java"
Task: "T016 CacheMetricsIT en backend/src/test/java/ar/edu/unq/desapp/futbolmarket/e2e/CacheMetricsIT.java"
```

## Parallel Example: User Story 2

```text
# En paralelo con toda la US1:
Task: "T019 Crear TradeCounterparty en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/observability/TradeCounterparty.java"

# Después de T020:
Task: "T022 TradeCounterpartyTest en backend/src/test/java/ar/edu/unq/desapp/futbolmarket/observability/TradeCounterpartyTest.java"
Task: "T023 BusinessMetricsTradesTest en backend/src/test/java/ar/edu/unq/desapp/futbolmarket/observability/BusinessMetricsTradesTest.java"
```

## Parallel Example: User Story 3

```text
# En cualquier momento después de la Fase 1:
Task: "T026 Data source en monitoring/grafana/provisioning/datasources/prometheus.yaml"
Task: "T027 Provider en monitoring/grafana/provisioning/dashboards/futbol-market.yaml"
Task: "T028 Dashboard en monitoring/grafana/dashboards/futbol-market.json"
Task: "T029 Ejemplo de credencial en monitoring/grafana/admin-credentials.example"
Task: "T030 Entradas nuevas en .gitignore"
```

---

## Implementation Strategy

### MVP First (User Stories 1 + 2)

1. Fase 1: línea de base y **medición del caché anterior** (T001 a T003). No saltear: después no se puede medir.
2. Fase 2: dependencias (T004).
3. Fase 3 (US1) y Fase 4 (US2), en paralelo o una tras otra.
4. **PARAR y VALIDAR**: `./gradlew build`, quickstart §2 y §3, y la medición completa (T018).
5. Con eso quedan cubiertos los dos ítems de la entrega 3.

### Incremental Delivery

1. Setup + Fundacional → base lista y medición A anotada.
2. US1 → caché acotado, observable y con garantía estricta; medición B y C.
3. US2 → métricas del mercado en `/actuator/prometheus`.
4. US3 → dashboard de Grafana sobre las métricas de 1, 2 y la 012.
5. Polish → README, búsqueda de credenciales y cierre.

### Parallel Team Strategy

1. Una persona hace la Fase 1 (la medición A tiene que hacerse en la máquina donde después se miden B y C).
2. Después:
   - Persona A: Fase 2 + US1 (`config/`, `build.gradle`, `application.yaml`).
   - Persona B: US2 (`observability/`).
   - Persona C: archivos de US3 (`monitoring/grafana/`, `.gitignore`), y T032/T033 cuando A y B terminen.
3. Cierre conjunto: Fase 6.

---

## Notes

- [P] = archivos distintos, sin dependencias pendientes.
- [US#] mapea cada tarea a su historia para la trazabilidad.
- Lo que research marca como **a verificar en la implementación** tiene su tarea: nombres de métricas de caché de
  Micrometer 1.17.1 (T016), versión de Grafana probada (T033 → T035), carpetas de provisioning no usadas (T033), y
  el comportamiento de `CaffeineCacheManager` en modo estático (T009).
- Si una verificación contradice research, se corrige la implementación **y** el documento de diseño
  correspondiente en la misma tarea.
- Parar en cualquier checkpoint para validar la historia por separado.
