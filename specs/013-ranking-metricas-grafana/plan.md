# Plan de implementación: Ranking para alta frecuencia, métricas del mercado y dashboard de Grafana

**Rama**: `013-ranking-metricas-grafana` | **Fecha**: 2026-11-24 | **Spec**: [spec.md](./spec.md)

**Entrada**: la especificación de la feature en `specs/013-ranking-metricas-grafana/spec.md`, con
sus aclaraciones del 2026-11-24, y las decisiones técnicas que dio el equipo al pedir el plan. La
spec está cerrada: este plan no reabre ninguna decisión.

## Resumen

Tres cosas, ninguna con cambios a la base ni endpoints nuevos:

1. **Caché del ranking (US1)**: los dos cachés de cotizaciones (`quotes-ranking` y
   `quotes-current`) pasan de `ConcurrentMapCacheManager` a Caffeine. Tienen tope de entradas,
   vencimiento de seguridad de 10 minutos y estadísticas, configurables en `futbolmarket.cache.*` y
   validados al arrancar. Siguen vaciándose en los mismos momentos y con el mismo
   `QuoteCacheInvalidator`. Un número de generación en el caché garantiza que una consulta en vuelo
   no deje guardado un resultado viejo después del vaciado. Las estadísticas salen solas en
   `/actuator/prometheus` (`cache_gets_total{result=hit|miss}`, `cache_evictions_total` y
   `cache_size`), gracias a la autoconfiguración de métricas de caché de Boot 4.1.1. La mejora se
   mide con un script de PowerShell versionado, en tres variantes: caché anterior (se mide primero,
   antes de tocar el código), sin caché y caché nuevo.
2. **Métricas de negocio (US2)**: tres contadores nuevos en `BusinessMetrics`
   (`futbolmarket.market.trades`, `.tokens.traded` y `.credits.traded`) con la etiqueta
   `counterparty` (`initial_order` | `user`). Son 6 series en cero desde el arranque. Se registran
   desde los advices de `buy` y `sell` que ya existen, a partir del `BookOutcome` que devolvió el
   servicio, afuera de la transacción y con `recordSafely`. Los servicios no cambian.
3. **Grafana (US3)**: en `monitoring/grafana/` se versionan el data source, el provider, el
   dashboard JSON y un script que levanta el binario de Grafana OSS para Windows con todas las
   rutas apuntando al repo. La credencial del administrador vive en un archivo local ignorado por
   git, que el script lee si existe.

El detalle de cada decisión está en [research.md](./research.md).

## Contexto técnico

**Lenguaje/Versión**: Java 21 (Temurin). Los scripts son de PowerShell 5.1 o 7.

**Dependencias principales**: Spring Boot 4.1.1 (webmvc, data-jpa, security, validation, actuator,
aspectj) y Micrometer 1.17.1 con `micrometer-registry-prometheus`. Hay dos dependencias nuevas,
justificadas en research R2: `spring-boot-starter-cache` (trae `spring-context-support` y la
autoconfiguración de caché y de sus métricas) y `com.github.ben-manes.caffeine:caffeine` (3.2.4,
del BOM). Grafana OSS ≥ 11.0 y Prometheus ≥ 2.55.0 son binarios externos, no dependencias del build.

**Almacenamiento**: H2, sin cambios: no hay entidades, tablas, índices ni consultas nuevas, y
`findRanking` no se toca. Los cachés y los contadores viven en memoria. Los datos de Grafana van a
`monitoring/grafana/data/` (ignorado por git).

**Tests**: JUnit 5, AssertJ, Mockito y MockMvc (`spring-boot-starter-*-test`), y ArchUnit 1.5.1 en
la suite de la 010. Sin TDD: los tests se escriben junto con cada tarea (Principio IV).

**Plataforma**: el backend corre en local con `./gradlew bootRun` (perfil `local`). Grafana y
Prometheus corren como binarios locales en Windows, sin Docker.

**Tipo de proyecto**: web service (monorepo `backend/` + `frontend/`). El frontend no cambia.

**Objetivos de rendimiento** (SC-001 y SC-002):
- En la medición, el promedio de una consulta repetida del ranking es ≥ 5 veces menor que sin
  caché, y el caché nuevo no es más lento que el anterior más allá del ruido de la medición. Se
  reportan los dos valores y la diferencia.
- Con 1.000 consultas repetidas o más, al menos el 95% son aciertos.

**Restricciones**:
- La respuesta del ranking es idéntica a la de hoy (FR-006).
- Ningún resultado viejo después de un cambio confirmado (FR-005).
- Medir nunca cambia el resultado de un caso de uso (FR-021).
- No hay credenciales en el repo (FR-028).
- Las métricas de la 012 no cambian (FR-022).

**Escala y alcance**:
- `quotes-ranking`: hasta 500 entradas, cada una una hoja de ≤ 100 filas.
- `quotes-current`: hasta 5.000 entradas, una por jugador.
- 6 series de negocio nuevas y 12 series de caché (6 por caché).
- Un dashboard de 3 filas.

## Revisión de la constitución

*GATE: tiene que pasar antes de la Fase 0 y se vuelve a revisar después de la Fase 1.*

| Principio | Cómo lo cumple el diseño | Estado |
|---|---|---|
| **I. Capas estrictas** | Las clases nuevas de producción van en paquetes transversales que ya existen. En `config/` van `QuoteCacheManager`, `GenerationGuardedCaffeineCache` y `QuoteCacheProperties`. En `observability/` va `TradeCounterparty`, y crecen `BusinessMetrics` y `BusinessMetricsAspect`. No se crean carpetas ni contextos. `config/` no depende de `service/`. El aspecto sigue con pointcuts de texto. Ninguna regla de ArchUnit de la 010 cambia. | ✅ |
| **II. Modelo rico** | No se agrega lógica de negocio en servicios. Las operaciones las sigue decidiendo `PlayerBook`, y la métrica solo lee `Trade`. | ✅ |
| **III. Validación en su nivel** | Los parámetros del ranking se siguen validando en el controller (`@Min`/`@Max`), antes de llegar al caché (FR-007). Las propiedades del caché se validan al enlazarse (FR-003). No hay status nuevos. | ✅ |
| **IV. Tests (NO NEGOCIABLE)** | Hay unitarios de `config/` y `observability/`, IT de servicio (concurrencia) y e2e (`/actuator/prometheus` y el dashboard), todos con setup, execute y verify y con AssertJ. **Un test existente cambia con el "sí" explícito** del desarrollador que implementa (2026-11-24): solo la aserción de tipo de `CacheConfigTest` (ver Complexity Tracking). Ningún otro test existente se toca. | ✅ con excepción autorizada |
| **V. Calidad medible** | Los valores van en propiedades, y los nombres y etiquetas en constantes. No hay `catch` genéricos: `recordSafely` captura `RuntimeException`, como en la 012. Se loguea con SLF4J, la inyección es por constructor y no hay credenciales. | ✅ |
| **VI. Persistencia portable** | No hay cambios a la persistencia. | ✅ |
| **VII. Idioma** | Los identificadores van en inglés (`TradeCounterparty`, `counterparty` e `initial_order`). Las descripciones de métricas, los Javadoc, el README y los mensajes de validación van en español. | ✅ |
| **Stack** | Hay dos dependencias nuevas, avisadas y justificadas (research R2), con versiones del BOM. No hay Docker. No se reemplazan `build.gradle` ni `application.yaml`: se les agregan líneas. | ✅ |
| **Definición de terminado** | Tests felices y de borde, `./gradlew build` y `bootRun` con el perfil local. No hay endpoints nuevos (FR-018), así que Swagger no cambia. | ✅ |

**Resultado**: el gate pasa. La única desviación (modificar un test existente) tiene la
autorización que exige el Principio IV.

**Revisión después del diseño (Fase 1)**: el gate sigue pasando. El data model, los contratos y el
quickstart no agregan paquetes, capas, endpoints ni dependencias distintas de las de R2. El test del
dashboard (`e2e/GrafanaDashboardIT`) lee archivos del repo y `/actuator/prometheus` con MockMvc,
dentro de `e2e/` como manda el Principio IV.

## Estructura del proyecto

### Documentación (esta feature)

```text
specs/013-ranking-metricas-grafana/
├── plan.md                 # este archivo
├── research.md             # Fase 0
├── data-model.md           # Fase 1: cachés, métricas y archivos de Grafana
├── quickstart.md           # Fase 1: validación y procedimiento de medición
├── contracts/
│   ├── metrics.md          # series nuevas en /actuator/prometheus (negocio y caché)
│   └── grafana-dashboard.md# provisioning, data source, paneles y PromQL
├── medicion-ranking.md     # resultados de la medición (lo llena tasks; FR-010)
├── checklists/
└── tasks.md                # Fase 2 ($speckit-tasks; no lo crea este comando)
```

### Código fuente (raíz del repositorio)

```text
backend/
├── build.gradle                                  # + spring-boot-starter-cache, + caffeine
└── src/
    ├── main/
    │   ├── java/ar/edu/unq/desapp/futbolmarket/
    │   │   ├── config/
    │   │   │   ├── CacheConfig.java              # crea QuoteCacheManager; condicionado a spring.cache.type
    │   │   │   ├── QuoteCacheManager.java        # NUEVO: CaffeineCacheManager estático con los dos cachés
    │   │   │   ├── GenerationGuardedCaffeineCache.java  # NUEVO: CaffeineCache con generación (FR-005)
    │   │   │   ├── QuoteCacheProperties.java     # NUEVO: futbolmarket.cache.{ranking,current}.*
    │   │   │   └── ApplicationConfig.java        # + QuoteCacheProperties en @EnableConfigurationProperties
    │   │   ├── observability/
    │   │   │   ├── BusinessMetrics.java          # + TRADES, TOKENS_TRADED, CREDITS_TRADED y recordTrade
    │   │   │   ├── BusinessMetricsAspect.java    # buy/sell: + recordTrades(outcome)
    │   │   │   └── TradeCounterparty.java        # NUEVO: INITIAL_ORDER | USER
    │   │   └── service/quote/                    # SIN CAMBIOS: QuoteQueryService, QuoteCacheInvalidator
    │   └── resources/application.yaml            # + futbolmarket.cache.*
    └── test/java/ar/edu/unq/desapp/futbolmarket/
        ├── config/
        │   ├── CacheConfigTest.java              # EXISTENTE: cambia solo la aserción de tipo (autorizado)
        │   ├── QuoteCacheManagerTest.java        # NUEVO: tope, vencimiento (Ticker), stats, nombres fijos
        │   ├── GenerationGuardedCaffeineCacheTest.java # NUEVO: put descartado tras vaciado
        │   └── QuoteCachePropertiesTest.java     # NUEVO: valores inválidos impiden arrancar
        ├── observability/
        │   ├── BusinessMetricsTradesTest.java    # NUEVO: 6 series en cero e incrementos
        │   └── BusinessMetricsAspectTradesTest.java # NUEVO: 0/1/varias operaciones, rechazo y falla al medir
        ├── service/quote/
        │   └── QuoteCacheConcurrencyIT.java      # NUEVO: consulta en vuelo durante los tres cambios (SC-003)
        └── e2e/
            ├── MarketTradeMetricsIT.java         # NUEVO: cruce con la orden inicial y compra pendiente
            ├── CacheMetricsIT.java               # NUEVO: cache_gets de quotes-ranking en Prometheus
            └── GrafanaDashboardIT.java           # NUEVO: el JSON parsea y solo usa métricas que existen

monitoring/
├── prometheus.yml                                # SIN CAMBIOS
├── measure-ranking.ps1                           # NUEVO: medición repetible (FR-010)
└── grafana/
    ├── start-grafana.ps1                         # NUEVO: levanta Grafana con las rutas del repo
    ├── admin-credentials.example                 # NUEVO: ejemplo sin contraseña (FR-028)
    ├── provisioning/
    │   ├── datasources/prometheus.yaml           # NUEVO: uid futbol-market-prometheus
    │   └── dashboards/futbol-market.yaml         # NUEVO: provider de archivos
    └── dashboards/futbol-market.json             # NUEVO: el dashboard

.gitignore                                        # + monitoring/grafana/data/, + monitoring/grafana/admin-credentials
README.md                                         # sección "Métricas y monitoreo": caché, métricas nuevas, Grafana, medición
```

Los nombres de los tests nuevos son orientativos: tasks puede agruparlos distinto, pero sin tocar
los tests existentes.

**Decisión de estructura**: es el monorepo de siempre. Todo el código Java nuevo cae en paquetes
transversales que ya existen (`config/` y `observability/`), así que no hace falta el aval para
carpetas nuevas del Principio I. Lo de Grafana vive junto al Prometheus de la 012, en
`monitoring/`, fuera del árbol de paquetes.

## Diseño por historia

### US1: caché del ranking

- **`CacheConfig`**:
  - El `@Bean cacheManager` devuelve un `QuoteCacheManager(properties, Ticker.systemTicker(), null)`.
  - El bean lleva `@ConditionalOnProperty(name = "spring.cache.type", havingValue = "caffeine",
    matchIfMissing = true)`. Con `spring.cache.type=none`, Boot pone un `NoOpCacheManager`: es la
    variante "sin caché" de la medición (research R7).
  - Las constantes `QUOTES_CURRENT` y `QUOTES_RANKING` no cambian.
- **`QuoteCacheManager extends CaffeineCacheManager`**:
  - Llama a `setCacheNames(List.of())` (modo estático) y registra los dos cachés con
    `registerCustomCache`, cada uno con `maximumSize`, `expireAfterWrite`, `recordStats` y `ticker`
    (el `executor` es opcional, para los tests).
  - Sobrescribe `adaptCaffeineCache` para devolver `GenerationGuardedCaffeineCache`.
  - Detalle en research R1, R3 y R6.
- **`GenerationGuardedCaffeineCache extends CaffeineCache`**: la generación, el `ThreadLocal` y el
  `ReentrantReadWriteLock` de research R3.
- **Sin cambios**: `QuoteCacheInvalidator`, `QuoteQueryService` (sus `@Cacheable`),
  `QuoteRecalculationService`, `StrategyService`, `StrategyChangeService`, `QuoteController` y
  `findRanking`.
- **Métricas**: las da la autoconfiguración de Boot (research R4). Contrato en
  [contracts/metrics.md](./contracts/metrics.md).
- **Medición**: `monitoring/measure-ranking.ps1`, en tres variantes. La primera tarea mide el
  caché anterior. Procedimiento en [quickstart.md](./quickstart.md) y resultados en
  `medicion-ranking.md`.

### US2: métricas de negocio

- **`TradeCounterparty`**: enum con `tag()` y `from(OwnerType)`.
- **`BusinessMetrics`**:
  - Tres contadores nuevos con su descripción y la etiqueta `counterparty`.
  - `bindTo` da de alta 6 series más (36 en total).
  - `recordTrade(OwnerType sellerType, int quantity, BigDecimal total)` incrementa los tres.
- **`BusinessMetricsAspect`**:
  - `measureBuy` y `measureSell` pasan el resultado por `recordTrades`, que solo actúa sobre un
    `BookOutcome` y registra dentro de `recordSafely`.
  - `measureCancel` no cambia.
  - El Javadoc deja anotado que `buy` y `sell` son la única ruta que ejecuta operaciones
    (research R10).
- **Precisión de créditos**: `BigDecimal.doubleValue()`, documentado (research R11).

### US3: Grafana

- **Archivos**: los de `monitoring/grafana/`, con el contrato en
  [contracts/grafana-dashboard.md](./contracts/grafana-dashboard.md).
- **Script**: `start-grafana.ps1` pone `GF_PATHS_*`, `FUTBOLMARKET_GRAFANA_DASHBOARDS` y, si
  existe el archivo local, `GF_SECURITY_ADMIN_USER` y `GF_SECURITY_ADMIN_PASSWORD`, todo solo para
  el proceso. Después lanza `grafana.exe server --homepath` (research R13 y R14).
- **`.gitignore`**: suma `monitoring/grafana/data/` y `monitoring/grafana/admin-credentials`.
- **README**, en la sección "Métricas y monitoreo":
  - la tabla de métricas nuevas (3 de negocio y las de caché);
  - cómo levantar Grafana (versión mínima, el archivo de credenciales a partir del ejemplo, el
    comando y la URL del dashboard);
  - el resumen de la medición con el enlace a `medicion-ranking.md`.

## Orden de implementación (lo que tasks tiene que respetar)

1. **Primero, antes de tocar `CacheConfig`**: escribir `monitoring/measure-ranking.ps1` y correr la
   variante **caché anterior**. Anotar los resultados en `medicion-ranking.md`.
2. Agregar las dependencias, `QuoteCacheProperties`, `QuoteCacheManager`,
   `GenerationGuardedCaffeineCache` y `CacheConfig`, con sus tests y el cambio autorizado de
   `CacheConfigTest`.
3. Escribir el IT de concurrencia y el IT de métricas del caché.
4. Medir las variantes **sin caché** y **caché nuevo**, completar `medicion-ranking.md` y verificar
   SC-001 y SC-002.
5. Agregar las métricas de negocio (US2) con sus unitarios y su IT. Es independiente de 2 a 4.
6. Hacer lo de Grafana (US3), el test del dashboard y el README. Depende de que existan los nombres
   de 2 y 5.
7. Cierre: `./gradlew build`, suite completa (incluida la de arquitectura) y `bootRun` local.

## Complexity Tracking

| Desviación | Por qué hace falta | Alternativa más simple descartada |
|---|---|---|
| Se modifica un test existente: `CacheConfigTest.elCacheManagerEsElNuestroConLosDosCachesDeCotizaciones` cambia `isInstanceOf(ConcurrentMapCacheManager.class)` por `isInstanceOf(CaffeineCacheManager.class)`, y su import. Es una excepción a SC-008, con el "sí" explícito del desarrollador que implementa (2026-11-24). | El test fija el tipo de manager que esta feature reemplaza a propósito. El resto del test (los dos nombres y que no exista otro caché) sigue valiendo y no cambia. | Una subclase de `ConcurrentMapCacheManager` que arme cachés de Caffeine: el test pasaría sin cambios, pero contradice la decisión de usar `CaffeineCacheManager` y es más confuso. El equipo eligió autorizar el cambio. |
| Dos dependencias nuevas (`spring-boot-starter-cache` y `caffeine`). | Tope, vencimiento y estadísticas del caché, y la autoconfiguración de sus métricas. Hoy falta `spring-context-support`. | Un caché propio sobre `ConcurrentHashMap`: reinventa Caffeine y necesita métricas a mano (research R1 y R2). |
