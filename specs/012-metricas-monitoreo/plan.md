# Plan de implementación: Métricas y endpoints de monitoreo

**Rama**: `012-metricas-monitoreo` | **Fecha**: 2026-11-19 | **Spec**: [spec.md](./spec.md)

**Entrada**: especificación de la feature en `specs/012-metricas-monitoreo/spec.md`, con sus
aclaraciones del 2026-11-19. La spec está cerrada: este plan no reabre ninguna decisión.

## Resumen

El backend publica sus métricas en formato Prometheus en `/actuator/prometheus`, todas con
`application="futbol-market"` y con histograma de latencia para los requests HTTP, más siete
métricas propias del negocio (órdenes, recálculo, sincronizaciones, logins, registros, usuarios y
órdenes abiertas). Por web se exponen solo `health`, `info`, `metrics`, `prometheus` y `loggers`,
con acceso por rol. Se suman dos indicadores de salud propios (`footballData` y `sync`) que nunca
llaman a las APIs externas. Un `prometheus.yml` de ejemplo permite recolectar todo con un
Prometheus local corrido con su binario.

Enfoque técnico:

- **Exportación**: `micrometer-registry-prometheus` (`runtimeOnly`, versión del BOM) y propiedades
  `management.*` en `application.yaml`. El build-info de Gradle llena `/actuator/info`.
- **Acceso**: reglas nuevas en `SecurityConfig` que nombran solo los endpoints expuestos; lo demás
  cae en `anyRequest().authenticated()` (401 visitante, 404 autenticado). Un
  `HealthEndpointGroupsPostProcessor` en `config/` muestra componentes a cualquier autenticado y
  deja los detalles para `ADMIN`.
- **Métricas propias**: un aspecto en una carpeta transversal nueva, `observability/`, que mide los
  casos de uso sin tocar los servicios y nunca cambia su resultado. Los dos gauges, que leen la
  base, son `MeterBinder` en `service/market/` y `service/user/`.
- **Salud**: dos `HealthIndicator` en `service/sync/` que leen propiedades y
  `LastFullSyncRepository`, con la regla de plazo en el modelo (`CatchUpPolicy`).
- **Swagger**: `springdoc.show-actuator` y dos grupos (`business`, `monitoring`); `/v3/api-docs`
  sigue igual.

Sin entidades, tablas ni endpoints de negocio nuevos.

## Decisiones de planificación (2026-11-19)

Las ocho decisiones del pedido se resuelven en [research.md](./research.md), una por sección:

| Pedido | Decisión | Research |
|--------|----------|----------|
| 1. Dependencias | `runtimeOnly 'io.micrometer:micrometer-registry-prometheus'` (Micrometer 1.17.1 del BOM de Boot 4.1.1); `springBoot { buildInfo { ... } }` como modificación de `build.gradle`. Sin dependencias de test nuevas. | D1 |
| 2. `application.yaml` | Exposición, tag común, histograma solo para `http.server.requests`, `info` con build y Java sin `env`. Perfiles `local` y `test` sin cambios. Salud por rol con un post-processor (con propiedades solas no se puede). | D2, D3 |
| 3. Seguridad | Cinco reglas nuevas antes de `anyRequest()`, ningún `/actuator/**` genérico; el matcher público de `/actuator/health` no cambia. | D4 |
| 4. Métricas propias | Aspecto en `observability/` (elegido frente al componente inyectado); gauges en `service/`. | D5, D6, D7 |
| 5. Health indicators | `service/sync/`, regla de plazo en `CatchUpPolicy`, nunca `DOWN`; el agregado queda `UP`. | D8 |
| 6. Swagger | `show-actuator` + grupos `business` y `monitoring`; `/v3/api-docs` intacto. | D9 |
| 7. Prometheus local | `monitoring/prometheus.yml`, `http_headers` con `files`, **Prometheus ≥ 2.55.0**; la clave en un archivo ignorado. | D10 |
| 8. Tests | Matriz de acceso, contenido, loggers, métricas, indicadores; `@AutoConfigureMetrics` para `/actuator/prometheus`. | D12 |

Lo que el plan decide porque la spec no lo cubre (marcado en research):

- `SyncDisabledException` (Football-Data sin token) **no registra** nada en `futbolmarket.sync`:
  la sincronización no se intentó, igual que el recálculo al arrancar sin semanas (D6).
- El rechazo de la **cadena completa** (cron o puesta al día con el candado tomado) **no registra**
  `in_progress`: no es un pedido de una fuente. Las corridas de la cadena sí se miden, por fuente
  (D6).
- `RecalculationBlockedBySyncException` se registra como `blocked`, igual que
  `RecalculationInProgressException`: las dos son el mismo `RecalculationLock` que rechaza sin
  ejecutar (D6). Ver [Puntos a confirmar](#puntos-a-confirmar-con-el-equipo).
- El recálculo dentro de un cambio de estrategia (`STRATEGY_CHANGE`) no pasa por
  `QuoteRecalculationService.recalculate` y no se mide (D6).
- El "éxito" de una sincronización lo decide el modelo: `SyncReport.succeeded()` y
  `StatsSyncReport.succeeded()` (D6).
- El 400 de un nivel de log inválido sale con el `ApiError` estándar: un handler nuevo en
  `GlobalExceptionHandler` para `ResponseStatusException` 400 (D11).

### Contradicciones con la constitución

Ninguna. Hay dos puntos que la constitución pide declarar y se declaran acá:

1. **Dependencia nueva**: `micrometer-registry-prometheus`, avisada y justificada (research D1).
2. **Cambio del árbol de paquetes**: carpeta transversal nueva `observability/` (ver
   [Cambio del árbol](#cambio-del-árbol-de-paquetes)). **Requiere el aval explícito de al menos uno
   de los dos desarrolladores antes de implementarla** (Principio I, "el árbol no es una lista
   cerrada").

## Contexto técnico

**Lenguaje/Versión**: Java 21 (Temurin).

**Dependencias principales**: Spring Boot 4.1.1 (`spring-boot-starter-webmvc`, `-security`,
`-validation`, `-data-jpa`, `-actuator`, `-aspectj`), Micrometer 1.17.1 (vía
`spring-boot-starter-micrometer-metrics`, que ya trae el actuator), springdoc-openapi 3.1.1.
**Nueva**: `io.micrometer:micrometer-registry-prometheus` 1.17.1 (`runtimeOnly`, versión del BOM;
trae el cliente de Prometheus 1.7.0).

**Almacenamiento**: ninguno nuevo. Las métricas viven en memoria; los gauges leen tablas existentes
(`app_user`, `market_orders`) con derived queries de conteo.

**Testing**: JUnit 5 + AssertJ + Mockito; `MockMvcTester` en `e2e/`; `@AutoConfigureMetrics`
(Boot 4) para los tests que piden `/actuator/prometheus`; `OutputCaptureExtension` para verificar
que Actuator no deja línea de auditoría; H2 en memoria con `@ActiveProfiles("test")`.

**Plataforma**: backend Spring Boot con `./gradlew bootRun` (perfil `local` por defecto).
Prometheus ≥ 2.55.0 con su binario oficial, fuera del repositorio, solo para la verificación
manual.

**Tipo de proyecto**: web service (monorepo `backend/` + `frontend/`; el frontend no cambia).

**Objetivos de performance**: el registro de una métrica cuesta lo mismo que un incremento en un
mapa concurrente; los dos gauges hacen un `COUNT` cada uno por scrape (15 s), el de órdenes por un
índice cuyo primer campo es `status` (research D7).

**Restricciones**: ninguna métrica cambia el resultado, el status ni las excepciones de un caso de
uso; ningún secreto en respuestas ni en archivos versionados; etiquetas de cardinalidad acotada;
ningún test existente se modifica; Sonar < 10 issues; sin Docker, Testcontainers, Grafana, alertas,
tracing ni probes.

**Escala/Alcance**: 7 métricas propias (30 series de contadores y timers + 2 gauges), 5 endpoints
expuestos, 2 indicadores de salud. Unas 10 clases de producción nuevas o modificadas en `backend/`,
2 archivos de configuración y un ejemplo de Prometheus.

Sin `NEEDS CLARIFICATION`: todo quedó resuelto en [research.md](./research.md).

## Verificación constitucional

*PUERTA: debe pasar antes de la investigación. Se vuelve a verificar después del diseño.*

### Antes de la investigación

| Principio | Verificación | Estado |
|-----------|--------------|--------|
| I. Capas | `observability/` es transversal: depende de `modelo/` y `shared/`, y sus pointcuts son `String` (no importa `service/`). Gauges e indicadores, que leen repositories, viven en `service/<contexto>/`, la única capa que B1 deja usar la persistencia. El post-processor de salud en `config/` solo usa clases de Boot. Ningún DTO cruza capas; ninguna clase de modelo sale en una respuesta (Actuator serializa sus propios descriptores). Carpeta nueva declarada, con aval pendiente. | ✅ con aval |
| II. Modelo rico | Los servicios no cambian. Las decisiones de dominio nuevas van al modelo: `SyncReport.succeeded()`, `StatsSyncReport.succeeded()`, `CatchUpPolicy.freshness()`; los estados abiertos salen de `OrderStatus.isOpen()`. El aspecto y los indicadores solo traducen a etiquetas y estados. | ✅ |
| III. Validaciones | Sin excepciones nuevas ni status codes nuevos. `GlobalExceptionHandler` suma un handler para el 400 de Actuator con el `ApiError` estándar y un mensaje fijo. El aspecto relanza la misma instancia de excepción. | ✅ |
| IV. Tests | Ningún test existente se modifica ni se borra. Tests nuevos en `e2e/`, `service/<contexto>/`, `modelo/sync/`, `persistence/repository/<contexto>/`, `config/`, `shared/` y `observability/` (replica el paquete de producción). AssertJ, setup - execute - verify, H2 en memoria, sin TDD, sin Testcontainers. | ✅ |
| V. Calidad | `@RequiredArgsConstructor` + `private final`; constantes para nombres, etiquetas y descripciones; enums para los valores; guard con `catch (RuntimeException)` y log SLF4J; sin duplicación entre advices; ningún secreto en el ejemplo de Prometheus. | ✅ |
| VI. Persistencia | Sin tablas ni entidades. Una derived query nueva (`countByStatusIn`) y el `count()` heredado de `JpaRepository`. Sin `@Query` nativas. | ✅ |
| VII. Idioma | Identificadores, nombres de métricas, etiquetas y grupos de Swagger en inglés; descripciones de las métricas, `displayName` de Swagger, mensajes, comentarios y docs en español. | ✅ |
| Stack | Una dependencia nueva, avisada y justificada. `build.gradle` y `application.yaml` se **modifican** (se agregan líneas), no se reemplazan; `application-local.yml` y `application-test.yml` no se tocan. Sin Docker. | ✅ |
| Definición de terminado | Tests felices y de borde; `./gradlew build`; `bootRun` con perfil local; los endpoints de monitoreo figuran en Swagger, en su grupo, y se ejecutan desde `/swagger-ui.html`. | ✅ |

### Después del diseño

Se revisó el diseño final ([data-model.md](./data-model.md),
[contracts/actuator-access.md](./contracts/actuator-access.md),
[contracts/health-indicators.md](./contracts/health-indicators.md)) contra cada fila:

- **I**: las dependencias de `observability/` son Micrometer, AspectJ, Spring (`core.annotation`,
  `stereotype`), SLF4J, `modelo/` (informes, enums, excepciones) y `shared/` (bases de excepción).
  Nadie depende de `observability/`, así que no hay ciclos. `service/market/OpenOrdersGauge` y
  `service/user/RegisteredUsersGauge` usan solo su repository y Micrometer: no forman ciclos entre
  contextos de servicio (B6). ✅
- **II**: `BusinessMetricsAspect` no tiene ningún `if` de dominio: clasifica por tipo de excepción
  y delega "¿fue exitosa?" en el informe. `SyncHealthIndicator` traduce `SyncFreshness`. ✅
- **III / FR-018**: el `return` y el `throw` del advice son los de `proceed()`; el guard envuelve
  clasificar + registrar. El handler nuevo solo atrapa `ResponseStatusException` 400, que en Spring
  MVC solo lanza Actuator. ✅
- **IV**: `AccessControlIT` (info protegido, health público, `/v3/api-docs` con los dos esquemas) y
  `WebServiceAuditIT` (health sin línea) siguen cumpliéndose por diseño (research D4, D9). ✅
- **VI**: `countByStatusIn(Collection<String>)` es derived; el estado se guarda como `String` en
  `MarketOrderSQL`. ✅

Resultado: **PASA**. El único punto condicionado es el aval de `observability/`.

## Estructura del proyecto

### Documentación de la feature

```text
specs/012-metricas-monitoreo/
├── spec.md                           # cerrada (Clarifications 2026-11-19)
├── plan.md                           # este archivo
├── research.md                       # D1–D13
├── data-model.md                     # catálogo de métricas, etiquetas e indicadores
├── quickstart.md                     # Prometheus local y verificación manual
├── contracts/
│   ├── actuator-access.md            # status por endpoint y rol, cuerpos de error
│   └── health-indicators.md          # forma de footballData y sync
├── checklists/
│   └── requirements.md
└── tasks.md                          # lo genera /speckit-tasks
```

### Cambio del árbol de paquetes

**Requiere aval explícito de un desarrollador** (Principio I):

```text
ar.edu.unq.desapp.futbolmarket
+-- observability/        NUEVO, transversal: métricas propias del negocio (el catálogo de
                          contadores y timers y el aspecto que los registra). Puede depender de
                          modelo/ y shared/; no depende de controller/, service/, persistence/,
                          adapter/ ni security/, y nadie depende de él.
```

Por qué una carpeta nueva y no `shared/` ni `config/`: `shared/` no puede depender de `modelo/`
(ciclo, research D4 de la 011) y el aspecto necesita los informes y excepciones del modelo;
`config/` es para clases `@Configuration`. Los gauges y los indicadores de salud **no** van en
`observability/` porque leen la persistencia (research D5, D7, D8).

### Código fuente: backend

```text
backend/
├── build.gradle                                       # + runtimeOnly micrometer-registry-prometheus
│                                                      # + springBoot { buildInfo { ... } }
├── src/main/resources/
│   └── application.yaml                               # CAMBIA: management.* y springdoc.show-actuator
└── src/main/java/ar/edu/unq/desapp/futbolmarket/
    ├── observability/                                 # NUEVO (con aval)
    │   ├── BusinessMetrics.java                       # MeterBinder: catálogo, registro en cero, record*
    │   ├── BusinessMetricsAspect.java                 # @Aspect @Order(HIGHEST_PRECEDENCE), @Around por caso de uso
    │   ├── OrderOperation.java                        # enum buy, sell, cancel
    │   ├── OrderResult.java                           # enum success, rejected
    │   ├── LoginResult.java                           # enum success, failure
    │   ├── RecalculationTrigger.java                  # enum manual, scheduled, startup (+ from(RecalculationOrigin))
    │   ├── RecalculationResult.java                   # enum success, failure, blocked
    │   └── SyncResult.java                            # enum success, failure, in_progress
    ├── config/
    │   ├── ActuatorHealthConfig.java                  # NUEVO: HealthEndpointGroupsPostProcessor
    │   └── OpenApiConfig.java                         # CAMBIA: + GroupedOpenApi business y monitoring
    ├── security/
    │   └── SecurityConfig.java                        # CAMBIA: 5 reglas de /actuator
    ├── shared/
    │   └── GlobalExceptionHandler.java                # CAMBIA: + handler ResponseStatusException 400
    ├── service/
    │   ├── market/OpenOrdersGauge.java                # NUEVO: MeterBinder, futbolmarket.market.orders.open
    │   ├── user/RegisteredUsersGauge.java             # NUEVO: MeterBinder, futbolmarket.users.registered
    │   └── sync/
    │       ├── FootballDataHealthIndicator.java       # NUEVO: componente footballData
    │       └── SyncHealthIndicator.java               # NUEVO: componente sync
    ├── modelo/sync/
    │   ├── CatchUpPolicy.java                         # CAMBIA: + freshness(Optional<LastFullSync>, Instant)
    │   ├── SyncFreshness.java                         # NUEVO: enum NEVER, UP_TO_DATE, STALE
    │   ├── SyncReport.java                            # CAMBIA: + succeeded(); lastFullSync() lo reutiliza
    │   └── StatsSyncReport.java                       # CAMBIA: + succeeded(); lastFullSync() lo reutiliza
    └── persistence/
        ├── repository/market/MarketOrderRepository.java   # CAMBIA: + countOpen()
        ├── repository/user/AppUserRepository.java         # CAMBIA: + count()
        └── sql/interfaces/market/MarketOrderSQLDAO.java   # CAMBIA: + countByStatusIn(Collection<String>)
```

Sin cambios en `controller/`, `adapter/`, en los servicios instrumentados (`MarketOrderService`,
`AuthService`, `QuoteRecalculationService`, `SyncService`, `StatsSyncService`, `SyncCoordinator`),
en `CredentialAuthenticationFilter`, en `WebServiceAuditAspect` ni en la auditoría del mercado.

Los nombres de los enums de `observability/` son tentativos: si alguno coincide con uno del modelo
(`SyncSource` y `SyncType` sí se reutilizan del modelo para `source` y `type`), se reutiliza en vez
de duplicarlo.

Fuera de `backend/`:

```text
monitoring/prometheus.yml   # NUEVO: ejemplo para Prometheus ≥ 2.55.0, sin claves
.gitignore                  # + monitoring/admin-api-key, + monitoring/data/
README.md                   # + sección "Métricas y monitoreo" que apunta al quickstart
```

### Tests: backend

```text
backend/src/test/java/ar/edu/unq/desapp/futbolmarket/
├── e2e/
│   ├── ActuatorAccessIT.java                 # matriz de acceso (contracts/actuator-access.md)
│   ├── ActuatorContentIT.java                # formato Prometheus, tags, histograma, info, sin secretos, sin auditoría
│   ├── LoggersEndpointIT.java                # cambio de nivel, 400, 403, 401
│   ├── BusinessMetricsIT.java                # órdenes, logins, registros y gauges vía HTTP
│   └── HealthIndicatorsIT.java               # health 200 en perfil test, footballData/sync por rol
├── service/
│   ├── quote/RecalculationMetricsIT.java
│   └── sync/
│       ├── SyncMetricsIT.java
│       ├── FootballDataHealthIndicatorTest.java
│       └── SyncHealthIndicatorTest.java
├── modelo/sync/
│   ├── SyncFreshnessTest.java
│   └── SyncReportOutcomeTest.java
├── persistence/repository/
│   ├── market/OpenOrdersCountIT.java
│   └── user/UserCountIT.java
├── observability/
│   ├── BusinessMetricsTest.java
│   └── BusinessMetricsAspectTest.java
├── config/ActuatorHealthConfigTest.java
└── shared/GlobalExceptionHandlerResponseStatusTest.java
```

Casos de cada uno en research D12. Ningún test existente cambia.

**Decisión de estructura**: monolito por capas existente. Lo transversal de métricas va en la
carpeta nueva `observability/`; lo que lee la base (gauges, indicador `sync`) va en
`service/<contexto>/`; la configuración de Actuator en `config/` y `application.yaml`; el acceso en
`security/SecurityConfig`.

## Orden de implementación sugerido

1. **Aval de `observability/`** de un desarrollador.
2. **Dependencia y configuración**: `build.gradle` (registro de Prometheus y build-info),
   `application.yaml`. Verificación: `./gradlew test` verde sin cambios de comportamiento salvo la
   exposición.
3. **Seguridad y salud** (US3): reglas de `SecurityConfig`, `ActuatorHealthConfig`, handler del 400
   en `GlobalExceptionHandler`; `ActuatorAccessIT`, `LoggersEndpointIT`,
   `ActuatorHealthConfigTest`, `GlobalExceptionHandlerResponseStatusTest`. `AccessControlIT` y
   `WebServiceAuditIT` siguen verdes.
4. **Exportación** (US1): `ActuatorContentIT` (formato, tags, histograma, plantilla de ruta, info,
   sin secretos, sin auditoría).
5. **Modelo y persistencia**: `succeeded()`, `freshness()`, `SyncFreshness`, `countOpen()`,
   `count()` con sus tests.
6. **Métricas propias** (US2): `BusinessMetrics`, `BusinessMetricsAspect`, los dos gauges;
   `BusinessMetricsTest`, `BusinessMetricsAspectTest`, `BusinessMetricsIT`,
   `RecalculationMetricsIT`, `SyncMetricsIT`.
7. **Indicadores** (US4): `FootballDataHealthIndicator`, `SyncHealthIndicator` y sus tests,
   `HealthIndicatorsIT`.
8. **Swagger**: grupos en `OpenApiConfig`; verificar `/v3/api-docs`, `/v3/api-docs/business`,
   `/v3/api-docs/monitoring` y Swagger UI.
9. **Prometheus local**: `monitoring/prometheus.yml`, `.gitignore`, README; verificación manual del
   [quickstart.md](./quickstart.md).
10. **Cierre**: `./gradlew build` completo (incluye ArchUnit de la 010) y revisión de Sonar.

Los pasos 3, 4 y 5 son independientes entre sí después del 2; el 6 necesita el 5; el 7 necesita el
5.

## Seguimiento de complejidad

Sin violaciones de la constitución que justificar.

| Punto | Por qué hace falta | Alternativa más simple descartada |
|-------|--------------------|-----------------------------------|
| Carpeta `observability/` | El aspecto necesita `modelo/` y no puede ir en `shared/` (ciclo) ni en `config/`. | Meterlo en `shared/`: ciclo `modelo` ↔ `shared`. |
| `HealthEndpointGroupsPostProcessor` | `roles` es común a componentes y detalles: con propiedades solas, o el `USER` recibe 404 en `/actuator/health/db` o ve los detalles. | Solo propiedades: incumple FR-032 de un lado o del otro. |
| Intercepción de `runFull` (package-private) | La cadena del cron y de la puesta al día no pasa por `synchronizeAll`; sin esto, sus corridas no se miden. | Hacer `runFull` público: abre una entrada sin candado ni chequeo de token. |
| Handler de `ResponseStatusException` 400 | El 400 de Actuator saldría con el JSON de error de Boot y no con `ApiError`. | Aceptar el JSON de Boot: contradice el edge case. |

## Puntos a confirmar con el equipo

- **Aval de `observability/`** (Principio I): sin él, la alternativa es la opción B de research D5
  (componente inyectado en los servicios), que no necesita carpeta nueva pero toca cinco servicios.
  **Confirmado** por Valentin el 2026-11-21 (T002): se crea `observability/` según la opción A.
- **`blocked` para `RecalculationBlockedBySyncException`**: FR-011 define `blocked` como "otro
  recálculo en curso"; el pedido de plan dice "lock tomado". Las dos excepciones son el mismo
  `RecalculationLock` que rechaza sin ejecutar y ningún otro valor las describe, así que el plan
  las registra como `blocked`. Si el equipo prefiere no registrar el caso de la sincronización,
  es una línea del aspecto. **Confirmado** por Valentin el 2026-11-21 (T002): las dos se
  registran como `blocked`.
- Que el compañero sepa que los tests que pidan `/actuator/prometheus` necesitan
  `@AutoConfigureMetrics` (Boot 4 apaga la exportación en `@SpringBootTest`).
- Que Prometheus local tiene que ser **2.55.0 o posterior** por `http_headers`.
