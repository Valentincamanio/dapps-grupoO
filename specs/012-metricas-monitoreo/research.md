# Investigación: Métricas y endpoints de monitoreo

**Feature**: `012-metricas-monitoreo` | **Fecha**: 2026-11-19 | **Plan**: [plan.md](./plan.md)

Cada decisión sigue el formato **Decisión / Por qué / Alternativas descartadas**. La spec y sus
aclaraciones están cerradas y se respetan tal cual. Donde la spec no dice nada y el plan tiene que
elegir, la decisión está marcada con **(no estaba en la spec)**.

El código se relevó el 2026-11-19 sobre la rama `012-metricas-monitoreo` (`cd7a514`, con la 010 y
la 011 integradas). Los datos de Spring Boot 4.1.1, Micrometer y springdoc se verificaron contra
los jars y la metadata de configuración que ya están en la caché de Gradle del proyecto, no contra
tutoriales:

| Artefacto | Versión resuelta | Qué se verificó |
|-----------|------------------|-----------------|
| `spring-boot-micrometer-metrics` | 4.1.1 | `PrometheusMetricsExportAutoConfiguration`, `PrometheusScrapeEndpoint`, propiedades `management.metrics.*` |
| `spring-boot-micrometer-metrics-test` | 4.1.1 | `@AutoConfigureMetrics` y el `MetricsContextCustomizerFactory` que apaga la exportación |
| `spring-boot-health` | 4.1.1 | `HealthEndpointSupport`, `AutoConfiguredHealthEndpointGroup`, `HealthEndpointGroupsPostProcessor`, `SimpleStatusAggregator`, propiedades `management.endpoint.health.*` |
| `spring-boot-actuator` / `-actuator-autoconfigure` | 4.1.1 | `Show.isAuthorized`, propiedades `management.info.*` y `management.endpoints.web.*` |
| `spring-boot-webmvc` | 4.1.1 | `InvalidEndpointBadRequestException extends ResponseStatusException` |
| `spring-boot-dependencies` (BOM) | 4.1.1 | `micrometer.version` 1.17.1, `prometheus-client.version` 1.7.0 |
| `springdoc-openapi-starter-*` | 3.1.1 | `springdoc.show-actuator`, `GroupedOpenApi`, condición de `/v3/api-docs` |

## Índice

- [D1. Dependencias y build-info](#d1-dependencias-y-build-info)
- [D2. Configuración de Actuator en `application.yaml`](#d2-configuración-de-actuator-en-applicationyaml)
- [D3. Salud: estado agregado público y componentes por rol](#d3-salud-estado-agregado-público-y-componentes-por-rol)
- [D4. Reglas de seguridad](#d4-reglas-de-seguridad)
- [D5. Dónde vive la instrumentación](#d5-dónde-vive-la-instrumentación)
- [D6. Semántica de cada contador y timer](#d6-semántica-de-cada-contador-y-timer)
- [D7. Gauges leídos de la base](#d7-gauges-leídos-de-la-base)
- [D8. Indicadores de salud `footballData` y `sync`](#d8-indicadores-de-salud-footballdata-y-sync)
- [D9. Swagger: grupo propio para el monitoreo](#d9-swagger-grupo-propio-para-el-monitoreo)
- [D10. Prometheus local sin Docker](#d10-prometheus-local-sin-docker)
- [D11. El 400 del nivel de log inválido](#d11-el-400-del-nivel-de-log-inválido)
- [D12. Tests](#d12-tests)
- [D13. Sonar](#d13-sonar)

---

## D1. Dependencias y build-info

**Decisión**: una sola dependencia nueva, en `build.gradle`:

```groovy
runtimeOnly 'io.micrometer:micrometer-registry-prometheus'
```

Sin versión: la fija el BOM de Boot 4.1.1 (Micrometer 1.17.1, que trae el cliente de Prometheus
1.7.0, `io.prometheus:prometheus-metrics-*`). Scope `runtimeOnly` porque ninguna clase de producción
ni de test la importa: Boot la detecta por classpath y los tests leen `/actuator/prometheus` por
HTTP.

Y una modificación (no un reemplazo) de `build.gradle`, al final del archivo:

```groovy
springBoot {
    buildInfo {
        properties {
            additional = [description: 'Mercado simulado de valoración de jugadores de fútbol.']
        }
    }
}
```

`buildInfo()` registra la tarea `bootBuildInfo`, que genera `META-INF/build-info.properties` antes
de `classes`. Boot la convierte en el bean `BuildProperties` y el `InfoContributor` de build la
publica en `/actuator/info` con `name` (`futbol-market`, el `rootProject.name`), `artifact`, `group`
(`ar.edu.unq.desapp`), `version` (`0.0.1-SNAPSHOT`, el `version` del proyecto), `time` y la
`description` adicional. Así la versión sale de un solo lugar.

No hace falta ninguna dependencia de test nueva: `spring-boot-starter-actuator-test`, que ya está,
trae `spring-boot-starter-micrometer-metrics-test` y con él `@AutoConfigureMetrics` (D12).

**Por qué**:

- Boot 4 partió la observabilidad en módulos. `spring-boot-starter-actuator` (ya presente) depende
  de `spring-boot-starter-micrometer-metrics`, que contiene la autoconfiguración de la exportación
  a Prometheus y el endpoint `prometheus`. Lo único que falta es el registro de Micrometer; sin él,
  la condición `@ConditionalOnClass(PrometheusMeterRegistry)` no se cumple y el endpoint no existe.
- En Boot 4 no hay un starter `spring-boot-starter-prometheus`: el artefacto correcto sigue siendo
  el de Micrometer. Se verificó en el BOM (`micrometer-registry-prometheus` 1.17.1).
- El nombre, la descripción y la versión van en el build-info y no en propiedades `info.*` porque
  publicar `info.*` exige el contributor `env`, y ese contributor enlaza **todo** el `Environment`
  bajo el prefijo `info`, incluidas variables de entorno del sistema (`INFO_ALGO=...` aparecería
  como `info.algo`). La consigna pide no mostrar variables de entorno.

**Alternativas descartadas**:

- `micrometer-registry-prometheus-simpleclient`: es el cliente viejo (0.16), que Micrometer
  mantiene solo por compatibilidad.
- `implementation` en lugar de `runtimeOnly`: expone en el classpath de compilación una API que el
  código no usa.
- `management.info.env.enabled: true` con `info.app.name/description/version` (la receta de
  Baeldung): por la razón de arriba, y porque `info.app.version` habría que escribirla a mano o
  filtrar `application.yaml` en `processResources` (choca con los `${...}` del archivo).
- Un `InfoContributor` propio: código y test para algo que Gradle ya genera.

---

## D2. Configuración de Actuator en `application.yaml`

**Decisión**: se agregan claves a `application.yaml` (base, para todos los perfiles). No se tocan
`application-local.yml` ni `src/test/resources/application-test.yml`.

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus,loggers
  endpoint:
    health:
      show-components: when-authorized
      show-details: when-authorized
      roles: ADMIN
  info:
    env:
      enabled: false
    java:
      enabled: true
  metrics:
    tags:
      application: ${spring.application.name}
    distribution:
      percentiles-histogram:
        "[http.server.requests]": true

springdoc:
  show-actuator: true
```

- `exposure.include`: reemplaza el `health,info` actual. Todo lo que no está en la lista (env,
  beans, configprops, heapdump, threaddump, mappings, shutdown, ...) no se expone por web y no tiene
  handler: un request autenticado responde 404.
- `metrics.tags.application`: `${spring.application.name}` vale `futbol-market`; se referencia para
  no repetir el nombre. Es un `MeterFilter` común que Boot aplica a todo meter, incluidos los
  propios y los gauges.
- `percentiles-histogram`: la clave lleva corchetes porque el nombre del meter tiene puntos. Solo
  `http.server.requests` publica buckets (`http_server_requests_seconds_bucket`); los timers propios
  publican `count`, `sum` y `max` (Assumptions de la spec).
- `info`: `env` queda explícitamente apagado (ya es el default de Boot 4.1.1, se deja escrito para
  que nadie lo prenda sin pensar); `java` se prende (default `false`); `build` está prendido por
  default y se llena con D1. `os`, `process`, `ssl` y `git` quedan en su default (apagados o sin
  datos: no hay `git.properties`).
- `health`: ver D3.
- `springdoc.show-actuator`: ver D9.

**Por qué**: todos los nombres de propiedades se verificaron en la metadata de Boot 4.1.1
(`management.endpoints.web.exposure.include` con default `[health]`,
`management.metrics.tags`, `management.metrics.distribution.percentiles-histogram`,
`management.info.env.enabled` default `false`, `management.info.java.enabled` default `false`,
`management.endpoint.health.show-components`, `show-details`, `roles`). El perfil `test` no
necesita nada propio: hereda la base, y `@SpringBootTest` ya apaga la exportación salvo con
`@AutoConfigureMetrics` (D12).

**Alternativas descartadas**:

- `exposure.include: "*"` con `exclude`: invierte la lista y cualquier endpoint nuevo de una
  versión futura de Boot quedaría expuesto.
- Poner la configuración en `application-local.yml`: los tests no la verían y el perfil `test`
  tendría otra superficie de exposición que producción.
- `management.metrics.web.server.request.autotime.percentiles-histogram`: es una propiedad de
  Boot 2 que en 4.1.1 solo sobrevive como metadata deprecada.

---

## D3. Salud: estado agregado público y componentes por rol

**Decisión**: `show-components: when-authorized`, `show-details: when-authorized`, `roles: ADMIN`,
más un `HealthEndpointGroupsPostProcessor` en `config/` que envuelve el grupo primario y redefine
**solo** `showComponents`: se muestran a cualquier caller autenticado. Todo lo demás (miembros,
`showDetails`, agregador, mapeo de status HTTP, path adicional) se delega al grupo original.

El resultado, por caller y path:

| Request | Principal que ve Actuator | Componentes | Detalles | Respuesta |
|---------|---------------------------|-------------|----------|-----------|
| `GET /actuator/health`, visitante | ninguno | no | no | 200 `{"status":"UP"}` |
| `GET /actuator/health`, `USER` o `ADMIN` con credencial | **ninguno** | no | no | 200 `{"status":"UP"}` |
| `GET /actuator/health/db`, visitante | — | — | — | 401 de la cadena de seguridad |
| `GET /actuator/health/db`, `USER` | `USER` | sí | no | 200 `{"status":"UP"}` |
| `GET /actuator/health/db`, `ADMIN` | `ADMIN` | sí | sí | 200 con `details` |

**Por qué**:

1. **El path de un componente devuelve 404 si no se muestran componentes.** En
   `HealthEndpointSupport.getResult` de Boot 4.1.1, si `showComponents` es falso y el path no es la
   raíz, la operación devuelve `null` y el endpoint responde 404. Para que un `USER` vea
   `/actuator/health/db` (FR-032), `showComponents` tiene que ser verdadero para él.
2. **`roles` es uno solo para los dos `Show`.** `AutoConfiguredHealthEndpointGroup` evalúa
   `showComponents` y `showDetails` con la misma colección de roles. Con `roles: ADMIN`, un `USER`
   no ve componentes (404); sin roles, un `USER` vería los detalles. Con propiedades solas no se
   puede "componentes para cualquiera autenticado, detalles solo para `ADMIN`".
3. **`show-components: always` rompe FR-031**: la raíz le mostraría la lista de componentes al
   visitante.
4. **La raíz es anónima para todos.** `GET /actuator/health` está en `publicEndpoints()` y
   `CredentialAuthenticationFilter.shouldNotFilter` usa ese mismo matcher: el filtro no evalúa la
   credencial, la autenticación queda anónima y `request.getUserPrincipal()` devuelve `null` (el
   wrapper de Spring Security trata al anónimo como sin principal). Por eso, con el post-processor,
   la raíz nunca muestra componentes, aunque el que llama sea un `ADMIN` con su clave (FR-031).
   Si alguien un día sacara la raíz de las rutas públicas, el test "ADMIN con credencial en la raíz
   ve solo el estado" lo detecta.
5. **Cómo compara Boot el rol con `ROLE_ADMIN`.** `Show.isAuthorized` recorre los roles
   configurados y, por cada uno, primero llama a `SecurityContext.isUserInRole(role)`, que en
   servlet es `request.isUserInRole`. El `SecurityContextHolderAwareRequestWrapper` de Spring
   Security antepone el prefijo `ROLE_` si el rol no lo trae, así que `ADMIN` se compara contra la
   authority `ROLE_ADMIN` que arma el filtro. Además compara el nombre del rol, tal cual, contra
   cada `GrantedAuthority` (ahí `ADMIN` no matchearía `ROLE_ADMIN`, pero no hace falta). Se
   configura `ADMIN`, que es el nombre del enum `Role` y lo que usa el resto de `SecurityConfig`.
   El wrapper existe porque `servletApi()` está activo por defecto en `HttpSecurity` y la cadena no
   lo apaga.
6. `HealthEndpointGroupsPostProcessor` es la extensión pública de Boot para esto: el
   `HealthEndpointGroupsBeanPostProcessor` de `HealthEndpointConfiguration` aplica todos los beans
   de ese tipo (es el mismo mecanismo que usa Boot para las probes). La interfaz
   `HealthEndpointGroup` tiene seis métodos y ninguno es nuevo en Boot 4.

**Alternativas descartadas**:

- Un grupo de salud (`management.endpoint.health.group.*`) con sus propios roles: los grupos viven
  en `/actuator/health/{grupo}`, no en `/actuator/health/{componente}`.
- Reemplazar `HealthEndpointWebExtension`: mucho más código y atado a internals.
- Pedir credencial en la raíz: cambia un comportamiento que la spec dice que no cambia.

---

## D4. Reglas de seguridad

**Decisión**: en `apiFilterChain`, después de las reglas que ya existen y antes de
`anyRequest().authenticated()`, se agregan las reglas de Actuator **nombrando solo los endpoints
expuestos**. `publicEndpoints()` no cambia: sigue con `pathPattern(HttpMethod.GET,
"/actuator/health")`.

```java
.requestMatchers(publicEndpoints).permitAll()                 // incluye GET /actuator/health (sin cambios)
// ... reglas existentes, sin cambios ...
.requestMatchers(pathPattern("/actuator")).hasRole(Role.ADMIN.name())
.requestMatchers(pathPattern("/actuator/metrics/**")).hasRole(Role.ADMIN.name())
.requestMatchers(pathPattern("/actuator/prometheus")).hasRole(Role.ADMIN.name())
.requestMatchers(pathPattern("/actuator/loggers/**")).hasRole(Role.ADMIN.name())
.requestMatchers(pathPattern("/actuator/health/**"), pathPattern("/actuator/info")).authenticated()
.anyRequest().authenticated()
```

Cómo se resuelve cada caso (el primer matcher que coincide gana):

| Request | Regla que aplica | Visitante | `USER` | `ADMIN` |
|---------|------------------|-----------|--------|---------|
| `GET /actuator/health` | `publicEndpoints` | 200 | 200 | 200 |
| `GET /actuator/health/db` | `health/**` autenticado | 401 | 200 | 200 |
| `GET /actuator/info` | `info` autenticado | 401 | 200 | 200 |
| `GET /actuator` | raíz `ADMIN` | 401 | 403 | 200 |
| `GET /actuator/metrics[/x]` | `metrics/**` `ADMIN` | 401 | 403 | 200 |
| `GET /actuator/prometheus` | `prometheus` `ADMIN` | 401 | 403 | 200 |
| `GET`/`POST /actuator/loggers[/x]` | `loggers/**` `ADMIN` | 401 | 403 | 200 / 204 |
| `GET /actuator/env` (no expuesto) | `anyRequest` autenticado | 401 | 404 | 404 |

**Por qué**:

- `PathPattern` con `/**` al final también matchea el path base: `/actuator/metrics/**` cubre
  `/actuator/metrics` y `/actuator/loggers/**` cubre `/actuator/loggers`. `/actuator` es exacto.
- La raíz se protege para todos los métodos, no solo `GET`: es más estricto y un `POST /actuator`
  de un `USER` responde 403 en lugar de 405, sin revelar nada.
- **Un `/actuator/** hasRole ADMIN` genérico rompería FR-030**: un `USER` recibiría 403 en
  `/actuator/env` en lugar de 404, y además taparía `health/**` e `info`. Por eso las reglas nombran
  solo lo expuesto y lo demás cae en `anyRequest().authenticated()`: el visitante recibe 401 antes
  de saber si la ruta existe y el autenticado llega a Spring MVC, que no tiene handler y responde
  404.
- La regla de `health/**` e `info` repite lo que ya haría `anyRequest().authenticated()`; se escribe
  igual para que la intención quede en el código y no dependa del orden de lo que se agregue
  después.
- El 401 y el 403 los escriben `JsonAuthenticationEntryPoint` y `JsonAccessDeniedHandler`, que ya
  están configurados en `exceptionHandling`: no se agrega nada.
- Las dos credenciales funcionan porque el filtro no distingue rutas salvo las públicas: arma la
  misma autenticación con `ROLE_<rol>` para `Bearer` y para `X-API-Key` (FR-039).
- `AccessControlIT` usa `GET /actuator/info` como recurso protegido "con cualquier credencial" y
  `GET /actuator/health` como público: los dos comportamientos se mantienen.
  `WebServiceAuditIT` verifica que `/actuator/health` no deja línea: el aspecto de la 011 solo mira
  `@RestController` de `controller/`, que Actuator no es.

**Alternativas descartadas**:

- Una `SecurityFilterChain` aparte con `securityMatcher("/actuator/**")`: duplica la configuración
  de la cadena (CSRF, sesión, filtro, entry points) para cuatro reglas.
- `EndpointRequest.to(...)` de Boot: es la API de Boot para esto, pero arma matchers por endpoint
  que también cubren los paths de endpoints que existan aunque no estén expuestos según la versión,
  y mezcla un estilo distinto del `pathPattern` que usa todo `SecurityConfig`. Con cinco paths
  fijos, `pathPattern` es más legible.
- Agregar `/actuator/**` a las rutas públicas y decidir el acceso en otro lado: rompe FR-030 y la
  regla "no se abren rutas `/actuator/**` en la cadena" de la spec.

---

## D5. Dónde vive la instrumentación

**Decisión**: **(cambio del árbol, requiere el aval explícito de un desarrollador)** una carpeta
transversal nueva, `observability/`, con:

- `BusinessMetrics`: `@Component` que implementa `MeterBinder`. Declara los nombres, descripciones
  y etiquetas de los cinco contadores y timers propios, los registra todos en cero en `bindTo`
  (FR-017) y expone un método de registro por caso de uso.
- `BusinessMetricsAspect`: `@Aspect @Component @Order(Ordered.HIGHEST_PRECEDENCE)` con un `@Around`
  por caso de uso. Mide con `System.nanoTime`, llama a `proceed()`, clasifica el resultado (D6),
  registra **dentro de un guard** que captura `RuntimeException` y la loguea en `WARN`, y devuelve
  el mismo valor o relanza la misma instancia de la excepción.

Los gauges no pueden vivir ahí (D7): leen repositories, y ArchUnit solo deja que la persistencia
la use el servicio. Van en `service/market/` y `service/user/`, también como `MeterBinder`.

Evaluación de las dos opciones pedidas:

| Criterio | A. Aspecto (elegida) | B. Componente inyectado en los servicios |
|----------|----------------------|------------------------------------------|
| Principio II | Los servicios no cambian: siguen orquestando y nada más. | Cada servicio suma llamadas y `try/catch` que no son orquestación del caso de uso. |
| FR-018 (no cambia resultado ni excepción) | Un único lugar: `proceed()` y relanzar la misma instancia; el guard envuelve solo el registro. | Hay que repetir el patrón en cada método instrumentado (siete métodos en cinco clases). |
| ArchUnit (B1) | `observability/` no es una capa: puede depender de `modelo/` y `shared/`. Los pointcuts son `String`, sin dependencia de bytecode hacia `service/`. | El servicio depende de `observability/` (permitido). Igual de válido. |
| Precedente | Igual que `WebServiceAuditAspect` de la 011 (research D2 de la 011). | Ninguno en el proyecto. |
| Transacciones | `@Order(HIGHEST_PRECEDENCE)` deja el aspecto **afuera** del `TransactionInterceptor` (que tiene `LOWEST_PRECEDENCE`): una falla al commitear se ve. | Dentro del método `@Transactional`: una falla al commitear no pasa por el registro. |
| Riesgos | Auto-invocación (una llamada interna no pasa por el proxy) y métodos package-private (ver abajo). | Duplicación y ruido en servicios. |

**Los dos riesgos del aspecto, y cómo se resuelven:**

1. **La cadena de sincronización llama a `runFull`, que es package-private.** `SyncCoordinator`
   (cron del lunes y puesta al día al arrancar) no pasa por `synchronizeAll`: llama a
   `syncService.runFull(origin)` y `statsSyncService.runFull(origin)` sobre los proxies inyectados.
   Spring AOP con CGLIB intercepta también métodos package-visible cuando el proxy se genera en el
   mismo paquete y class loader que la clase (lo documenta la referencia de Spring AOP: "public and
   protected method calls on the proxy are intercepted (and even package-visible methods, if
   necessary)"). El pointcut de `runFull` no lleva modificador `public`. Un test de integración
   verifica que la cadena registra exactamente una muestra por fuente (D12).
2. **`synchronizeAll` llama a `runFull` internamente.** Es auto-invocación: no pasa por el proxy y
   no se intercepta. Una sincronización manual completa cuenta una sola vez (en `synchronizeAll`) y
   una de la cadena, una sola vez (en `runFull`). Un test lo verifica.

`BusinessMetrics` en `observability/` y no en `shared/`: `shared/` no puede depender de `modelo/`,
porque `modelo/` ya depende de `shared/` (las bases de excepción) y se formaría un ciclo (research
D4 de la 011). El aspecto necesita los informes y excepciones de `modelo/`.

**Alternativas descartadas**:

- B, componente inyectado: ver la tabla.
- `@Timed`/`@Counted` de Micrometer con `TimedAspect`: no permiten la etiqueta `result` con el
  vocabulario de la spec (ponen `exception` con el nombre de la clase, que es cardinalidad no
  acotada en la práctica) ni registran las combinaciones en cero.
- Instrumentar los `*ReportLogger`: ven las corridas que terminan, pero no los rechazos por
  candado ni las que abortan con una excepción.
- Hacer `runFull` público para no depender de la intercepción package-private: abriría a cualquier
  servicio una entrada que no toma el candado ni revisa el token.

---

## D6. Semántica de cada contador y timer

**Decisión**: el aspecto clasifica así. Una excepción que no figura en la tabla **no registra
nada** y se relanza intacta.

| Join point | Métrica | Etiquetas fijas | Retorno normal | Excepción |
|------------|---------|-----------------|----------------|-----------|
| `MarketOrderService.buy/sell/cancel` | `futbolmarket.market.orders` | `operation=buy\|sell\|cancel` | `result=success` | `ConflictException`, `NotFoundException` o `ForbiddenException` de `shared/` → `result=rejected` |
| `AuthService.login` | `futbolmarket.auth.logins` | — | `result=success` | `InvalidCredentialsException` → `result=failure` |
| `AuthService.register` | `futbolmarket.auth.registrations` | — | +1 | nada |
| `QuoteRecalculationService.recalculate(origin)` | `futbolmarket.quotes.recalculation` | `trigger` según `origin` | `report.wasInterrupted()` → `failure`; si no, `success` | `RecalculationInProgressException` o `RecalculationBlockedBySyncException` → `blocked`; otra `RuntimeException` → `failure` |
| `SyncService.synchronizeAll`, `runFull` | `futbolmarket.sync` | `source=football_data`, `type=full` | `report.succeeded()` → `success`; si no, `failure` | `SyncInProgressException` → `in_progress`; `SyncDisabledException` → nada; otra `RuntimeException` → `failure` |
| `SyncService.synchronizeLeague` | `futbolmarket.sync` | `source=football_data`, `type=single_league` | ídem | ídem |
| `StatsSyncService.synchronizeAll`, `runFull` | `futbolmarket.sync` | `source=whoscored`, `type=full` | `report.succeeded()` → `success`; si no, `failure` | `SyncInProgressException` → `in_progress`; otra `RuntimeException` → `failure` |
| `StatsSyncService.synchronizeLeague` | `futbolmarket.sync` | `source=whoscored`, `type=single_league` | ídem | ídem |

Detalles:

- **Órdenes `rejected`** (FR-010): las excepciones de dominio del mercado y del catálogo extienden
  las bases de `shared/` (`InsufficientCreditsException`, `MarketBusyException`, ... → 409;
  `PlayerNotFoundException`, `OrderNotFoundException` → 404; `OrderNotOwnedException`,
  `TradingNotAllowedException` → 403). Se clasifica por la base, así una excepción de dominio nueva
  del mercado queda cubierta sin tocar el aspecto. `MarketInvariantException` (extiende
  `RuntimeException`, sale como 500), una `DataAccessException` o cualquier otro error interno no
  cuentan.
- **Rechazos antes del caso de uso** (401/403 del filtro, 400 de forma): el servicio nunca se
  invoca y el aspecto no los ve. Un `ADMIN` que intenta comprar recibe 403 del filtro y no cuenta.
- **Login `failure`** (FR-013): `AuthService.login` lanza `InvalidCredentialsException` tanto para
  usuario inexistente como para contraseña incorrecta; la etiqueta no los distingue. La
  autenticación de cada request por el filtro usa `findById`/`findByApiKey`, no `login`, así que no
  cuenta (Assumptions).
- **Registro**: solo el retorno normal cuenta (FR-014); duplicados y datos inválidos lanzan y no
  suman.
- **`trigger`** (FR-011): `MANUAL` → `manual`, `WEEKLY` → `scheduled`, `STARTUP` → `startup`.
  **(no estaba en la spec)** `RecalculationOrigin` tiene un cuarto valor, `STRATEGY_CHANGE`, pero
  ese recálculo no pasa por `QuoteRecalculationService.recalculate`: lo hace `StrategyChangeWriter`
  dentro del caso de uso de cambio de estrategia, que no se instrumenta. El mapeo igual es total
  (`STRATEGY_CHANGE` → `manual`, porque lo dispara un `ADMIN`) para que un `switch` exhaustivo no
  necesite rama por defecto.
- **`blocked`** (FR-011, "lock tomado" en el pedido): `RecalculationLock` rechaza sin ejecutar en
  dos casos: otro recálculo en curso (`RecalculationInProgressException`) o una sincronización en
  curso (`RecalculationBlockedBySyncException`). Los dos son el mismo candado tomado y ninguno es
  una falla a mitad de camino, así que los dos se registran como `blocked`. Ver "Puntos a
  confirmar" del plan.
- **Recálculo al arrancar sin semanas pendientes**: `QuoteStartupRecalculation` vuelve antes de
  llamar a `recalculate`, así que el aspecto no ve nada y no se registra nada (Assumptions).
- **Recálculo interrumpido**: `QuoteRecalculationService` captura la `DataAccessException` de una
  semana y devuelve un informe con `interruptedAt`; `RecalculationReport.wasInterrupted()` ya
  existe y decide `failure`.
- **Éxito de una sincronización** (Principio II): **(no estaba en la spec)** la decisión "esta
  corrida fue exitosa" es del modelo. Se agrega `SyncReport.succeeded()` (sin ligas fallidas) y
  `StatsSyncReport.succeeded()` (sin corte), con el mismo criterio que ya usa `lastFullSync()` en
  cada uno; `lastFullSync()` pasa a reutilizarlo. Una de una sola liga que falla en su liga es
  `failure`.
- **`SyncDisabledException`** **(no estaba en la spec)**: Football-Data sin token responde 503
  antes de tomar el candado. La sincronización no se intentó y ninguno de los tres valores de
  FR-012 la describe (no está en curso otra, no falló a mitad de camino), así que no se registra,
  igual que el recálculo al arrancar sin semanas.
- **Rechazo de la cadena completa** **(no estaba en la spec)**: si el cron del lunes o la puesta al
  día al arrancar encuentran el candado tomado, la `SyncInProgressException` sale de
  `SyncCoordinator.runChain` (privado) antes de decidir qué fuentes corren, y `SyncScheduler` o
  `StartupSync` la registran como advertencia. No se registra `in_progress`: la cadena no es un
  pedido de una fuente, y asignarla a las dos inventaría un intento que no existió (sin token,
  Football-Data se saltea). `in_progress` cubre los pedidos de una fuente: los manuales y la
  puesta al día solo de WhoScored, que pasa por `StatsSyncService.synchronizeAll` (escenario 7 de
  la US2).
- **Duración**: la de la invocación entera, medida en el aspecto, también para `in_progress` y
  `blocked` (que duran microsegundos) y para las que abortan ("con la duración hasta la falla").
- **Etiquetas** (FR-019): todos los valores salen de enums cerrados; los de `source` y `type` son
  `SyncSource` y `SyncType` del modelo en minúsculas (`football_data`, `whoscored`, `full`,
  `single_league`). Ningún argumento del join point (ids, cantidades, liga) llega a una etiqueta.
- **FR-018**: el `return` y el `throw` son los de `proceed()`. Clasificar y registrar van dentro de
  un único método `recordSafely(Runnable)` que captura `RuntimeException`, la loguea en `WARN` con
  el nombre de la métrica y sigue. Un `Error` de la JVM no se captura ni registra.

**Alternativas descartadas**:

- Contar como `rejected` cualquier excepción: contradice FR-010 y la aclaración de la spec.
- `failure` para `SyncDisabledException`: mezclaría "no configurado" con "se rompió".
- Agregar un valor de etiqueta (`disabled`, `chain_in_progress`): FR-019 fija los valores posibles.

---

## D7. Gauges leídos de la base

**Decisión**:

- `service/user/RegisteredUsersGauge` (`@Component`, `MeterBinder`): registra
  `futbolmarket.users.registered` con `Gauge.builder(name, appUserRepository,
  AppUserRepository::count).strongReference(true)`. `AppUserRepository.count()` es nuevo y delega
  en el `count()` heredado de `JpaRepository` del `AppUserSQLDAO`.
- `service/market/OpenOrdersGauge` (`@Component`, `MeterBinder`): registra
  `futbolmarket.market.orders.open` con `MarketOrderRepository.countOpen()`, nuevo, que delega en
  una derived query nueva del DAO: `long countByStatusIn(Collection<String> statuses)`. Los
  estados abiertos los decide el modelo: el repository pasa los nombres de los `OrderStatus` con
  `isOpen()` (`PENDING`, `PARTIAL`), sin repetir la lista.

Cada gauge se evalúa en cada lectura (scrape de Prometheus cada 15 s, o un `GET
/actuator/metrics/{nombre}`): refleja la base en el momento (FR-015) y, después de un reinicio,
muestra los valores reales desde el primer request (Edge Cases).

**Costo por scrape**: dos `SELECT COUNT(*)` en transacciones de solo lectura de Spring Data.
`app_user` es chica (decenas de filas). `market_orders` tiene el índice
`ix_market_orders_status_created (status, created_at)`, cuyo primer campo es `status`: el
`WHERE status IN ('PENDING','PARTIAL')` se resuelve con un rango del índice. Cada 15 s es
despreciable frente a cualquier request de negocio. Si la base fallara, Micrometer captura la
excepción de la función del gauge, loguea y publica `NaN`: el scrape no se rompe.

**Por qué**: ArchUnit (B1) solo deja que la persistencia la use la capa de servicio, así que un
gauge que lee repositories tiene que ser una clase de `service/<contexto>/`. `MeterBinder` es el
mecanismo de Boot para registrar meters después de configurar el registro (filtros y tags comunes
aplicados), y `strongReference(true)` evita que el gauge pierda su objeto por GC.

**Alternativas descartadas**:

- Un gauge mantenido en memoria (incrementar/decrementar en cada orden): se desincroniza con las
  órdenes iniciales, los cruces y los reinicios; FR-015 pide el estado de la base.
- Cachear el conteo N segundos: el scrape ya fija la frecuencia; la caché solo agrega desfasaje.
- `@Query("select count ...")` JPQL: la derived query alcanza y es lo que pide el Principio VI.

---

## D8. Indicadores de salud `footballData` y `sync`

**Decisión**: los dos en `service/sync/`, como `@Component` que implementan `HealthIndicator`:

- `FootballDataHealthIndicator` → componente `footballData` (Boot quita el sufijo
  `HealthIndicator` del nombre del bean). Lee `FootballDataProperties.hasToken()`. Sin token:
  `UNKNOWN` con `tokenConfigured=false`; con token: `UP` con `tokenConfigured=true`. Nunca agrega
  el token ni una parte de él.
- `SyncHealthIndicator` → componente `sync`. Lee `LastFullSyncRepository.find(source)` para
  `FOOTBALL_DATA` y `WHOSCORED`, `SyncProperties.catchUpAfter()` y el `Clock` de la aplicación.
  Por fuente: nunca sincronizada, al día o vencida. `UP` si las dos están al día; `UNKNOWN` en
  cualquier otro caso. La forma exacta está en
  [contracts/health-indicators.md](./contracts/health-indicators.md).

**La regla del plazo vive en el modelo** (Principio II): **(no estaba en la spec)** se agrega a
`CatchUpPolicy` un método público `freshness(Optional<LastFullSync>, Instant)` que devuelve un enum
nuevo `SyncFreshness` (`NEVER`, `UP_TO_DATE`, `STALE`) usando el mismo `isUpToDate` privado que ya
usa `decide`: vencida si terminó hace **más** de `maxAge`; con exactamente `maxAge` sigue al día.
El indicador solo traduce `SyncFreshness` a estado y detalles. `decide` no cambia.

**Ninguno devuelve `DOWN`** (FR-042): los únicos estados posibles son `UP` y `UNKNOWN`. El
agregador por defecto de Boot 4.1.1 (`SimpleStatusAggregator`, orden
`DOWN, OUT_OF_SERVICE, UP, UNKNOWN`, verificado en la metadata de
`management.endpoint.health.status.order`) elige el primer estado del orden que aparezca: con `db`,
`diskSpace` y `ping` en `UP` y los dos propios en `UNKNOWN`, el agregado es `UP`. El
`HttpCodeStatusMapper` por defecto solo devuelve 503 para `DOWN` y `OUT_OF_SERVICE`: `UNKNOWN`
responde 200, así que `GET /actuator/health/sync` también responde 200 cuando está vencida.

**Perfil `test`** (SC-010): sin token y con la base vacía, los dos propios dan `UNKNOWN`, la base
`UP` y el agregado `UP` → `GET /actuator/health` responde 200. Lo cubre un test e2e.

**Sin HTTP externo** (FR-043): ninguno inyecta un adapter; solo propiedades, un repository y el
reloj. ArchUnit B1 lo garantiza a medias (el servicio puede usar el adapter); el test unitario lo
construye sin adapter.

**Alternativas descartadas**:

- `config/` para `FootballDataHealthIndicator`: `config/` es para clases `@Configuration`; y los dos
  indicadores son de la sincronización.
- `controller/` o `security/`: no pueden usar la persistencia (B1).
- Repetir la comparación de plazos en el indicador: duplicaría la regla de borde de la 006.
- `DOWN` para "vencida": FR-042.

---

## D9. Swagger: grupo propio para el monitoreo

**Decisión**: `springdoc.show-actuator: true` (D2) y dos `GroupedOpenApi` nuevos en
`config/OpenApiConfig`:

- `business` (display name "API de negocio"): `pathsToMatch("/**")`,
  `pathsToExclude("/actuator/**")`.
- `monitoring` (display name "Monitoreo"): `pathsToMatch("/actuator/**")`, con un
  `OpenApiCustomizer` que agrega a cada operación, salvo `GET /actuator/health`, el requirement
  `bearerAuth` o `apiKeyAuth` (como alternativas, igual que los controllers protegidos), para que
  Swagger UI mande la credencial al ejecutarlas.

El bean `OpenAPI` existente no cambia: springdoc lo usa como base de cada grupo, así que los dos
esquemas de seguridad están en los dos grupos y en la especificación por defecto.

**`GET /v3/api-docs` sigue respondiendo**: en springdoc 3.1.1, el bean `OpenApiWebMvcResource`
(que sirve `/v3/api-docs`) es `@ConditionalOnExpression("(${springdoc.use-management-port:false}
== false) and ${springdoc.enable-default-api-docs:true}")`; no depende de que haya grupos. Los
grupos se sirven aparte en `/v3/api-docs/{grupo}` (`MultipleOpenApiWebMvcResource`). La
especificación por defecto incluye todo (negocio y Actuator) y sigue declarando `bearerAuth` y
`apiKeyAuth`, que es lo que verifica `AccessControlIT`. Swagger UI pasa a mostrar un selector con
los dos grupos.

Los nombres de grupo van en inglés porque forman parte de la URL (`/v3/api-docs/monitoring`,
Principio VII); lo que ve el usuario va en español (`displayName`).

**Alternativas descartadas**:

- Solo `show-actuator` sin grupos: springdoc pone Actuator en un tag propio, pero en la misma
  especificación que la API de negocio; la spec pide un grupo o sección separada y el pedido,
  grupos.
- `springdoc.group-configs` en YAML: no permite el customizer de seguridad.
- `use-management-port`: no hay puerto de management separado.

---

## D10. Prometheus local sin Docker

**Decisión**: un ejemplo versionado en `monitoring/prometheus.yml`, en la raíz del repositorio
(no es parte del build de Gradle, así que no va en `backend/`):

```yaml
global:
  scrape_interval: 15s

scrape_configs:
  - job_name: futbol-market
    metrics_path: /actuator/prometheus
    static_configs:
      - targets: ["localhost:8080"]
    http_headers:
      X-API-Key:
        files: ["admin-api-key"]
```

- La clave del `ADMIN` va en `monitoring/admin-api-key`, una sola línea, **fuera del control de
  versiones**. Se agregan al `.gitignore` `monitoring/admin-api-key` y `monitoring/data/` (el
  almacenamiento local de Prometheus si se lo corre como dice el quickstart).
- Las rutas de `files` se resuelven contra el directorio del archivo de configuración (el
  `SetDirectory` de la configuración HTTP de `prometheus/common`, que Prometheus aplica a toda la
  config), así que `admin-api-key` funciona sin importar desde dónde se lance el binario. El
  quickstart lo verifica y, si una versión no lo hiciera, se usa una ruta absoluta.
- **Versión mínima: Prometheus 2.55.0** (2024-10-22). El changelog de Prometheus lo registra en esa
  versión ("[FEATURE] Scraping: Add the ability to set custom `http_headers` in config. #14817");
  ninguna entrada de 2.49 a 2.54 lo menciona, y la documentación de configuración de 2.55 ya
  muestra `http_headers` con `values`, `secrets` y `files` dentro de `<scrape_config>`. Cualquier
  3.x lo soporta.
- El header `X-API-Key` no es de los que Prometheus reserva para sí.

**Por qué**:

- FR-006 y FR-007: el ejemplo no contiene ninguna clave, solo el nombre del archivo.
- La clave de API y no el token de sesión: el token vence a las 24 h (`futbolmarket.security.jwt.
  expiration`); la clave no vence hasta que se regenera.
- `files` y no una variable de entorno: Prometheus no expande variables de entorno en
  `prometheus.yml` (salvo con flags experimentales que cambian entre versiones); el archivo es el
  mecanismo estable.

**Alternativas descartadas**:

- `authorization` con `credentials_file`: manda `Authorization: <tipo> <credencial>`; el backend
  espera la clave en `X-API-Key` y un `Authorization: Bearer` con la clave haría que el filtro la
  evalúe como JWT y responda 401.
- `params` en la URL: la clave quedaría en la URL y en los logs de acceso.
- Docker/Grafana: prohibidos (FR-052).

---

## D11. El 400 del nivel de log inválido

**Decisión**: **(no estaba en la spec, la implementa)** se agrega a `GlobalExceptionHandler` un
`@ExceptionHandler(ResponseStatusException.class)` que, si el status es 400, responde el `ApiError`
estándar con el mensaje fijo `INVALID_PARAMETERS` que ya existe; con cualquier otro status relanza
la excepción, igual que hoy.

**Por qué**: un `POST /actuator/loggers/{nombre}` con `{"configuredLevel":"NOPE"}` falla al convertir
el parámetro y Boot 4.1.1 lanza `InvalidEndpointBadRequestException`, que extiende
`ResponseStatusException`. Hoy el `handleUnexpected` del advice la relanza (es un `ErrorResponse`),
Spring MVC hace `sendError(400)` y el contenedor responde el JSON de error de Boot
(`timestamp`, `status`, `error`, `path`), no el `ApiError` del backend. La spec pide "400 con el
JSON de error estándar". El advice ya se aplica a los handlers de Actuator (no tiene selectores de
paquete). En Spring MVC ningún otro camino del backend lanza `ResponseStatusException` (es la
familia de excepciones de WebFlux y de Actuator), así que el cambio no altera ninguna respuesta
existente. El mensaje es fijo: el `reason` de Boot describe la conversión interna y no se filtra
(Principio III).

**Alternativas descartadas**:

- Aceptar el JSON de error de Boot para este caso: contradice el edge case de la spec.
- Capturar `InvalidEndpointBadRequestException`: es una clase privada de Boot.
- Un handler para cualquier `ResponseStatusException`: cambiaría el cuerpo de casos que hoy no
  pasan por el advice y no hace falta.

---

## D12. Tests

**Decisión**: ningún test existente se modifica ni se borra (FR-050). Tests nuevos, con
setup - execute - verify, AssertJ y nombres en español; H2 en memoria con `@ActiveProfiles("test")`;
sin Testcontainers ni Docker.

**Exportación en `@SpringBootTest` (verificado en Boot 4.1.1)**: el `MetricsContextCustomizerFactory`
de `spring-boot-micrometer-metrics-test` agrega a todo contexto de test
`management.defaults.metrics.export.enabled=false` y `management.simple.metrics.export.enabled=true`,
salvo que la clase lleve **`@AutoConfigureMetrics`**
(`org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics`, atributo
`export` con default `true`). Es el reemplazo de `@AutoConfigureObservability` de Boot 3, que en
Boot 4 ya no existe con ese nombre. Sin la anotación no hay `PrometheusMeterRegistry` y
`/actuator/prometheus` responde 404 incluso a un `ADMIN`. Las métricas propias sí existen sin la
anotación (van al `SimpleMeterRegistry`), así que los tests de métricas leen el `MeterRegistry`
inyectado y solo los que piden `/actuator/prometheus` llevan `@AutoConfigureMetrics`.

**Aislamiento**: los contadores son acumulativos en el contexto cacheado; cada test mide la
diferencia antes/después de su acción, nunca un valor absoluto. "Aparece en cero al arrancar" se
prueba con `BusinessMetrics.bindTo(new SimpleMeterRegistry())` en un unitario, y la presencia de
todas las series en `/actuator/prometheus`, en el e2e.

**Clases nuevas** (rutas bajo `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/`):

| Clase | Nivel | Qué cubre |
|-------|-------|-----------|
| `e2e/ActuatorAccessIT` | e2e, `@AutoConfigureMetrics`, admin por `@DynamicPropertySource` y base propia como `AdminAccountIT` | La matriz de [contracts/actuator-access.md](./contracts/actuator-access.md): cada endpoint expuesto, la raíz y `env`, `beans`, `configprops`, `heapdump`, `threaddump`, `mappings`, `shutdown`, como visitante, `USER` y `ADMIN`; con `Bearer` y con `X-API-Key`; cuerpo `ApiError` en 401/403; `ADMIN` con credencial en `GET /actuator/health` ve solo el estado; `USER` ve el estado de `db` sin detalles y `ADMIN` con detalles. |
| `e2e/ActuatorContentIT` | e2e, `@AutoConfigureMetrics` | `/actuator/prometheus`: formato de exposición, `application="futbol-market"` en cada serie, buckets de `http_server_requests_seconds`, `uri="/players/{id}"` y ninguna serie con el id concreto; ruta inexistente con `uri` fija; métricas técnicas (jvm, process, hikaricp); las siete propias presentes. `/actuator/info` con `build` y `java`, sin `env`. Ninguna respuesta de ningún endpoint, para ningún rol, contiene el secreto JWT del perfil test, la clave de API, el token de sesión ni un token de Football-Data puesto por `@TestPropertySource`. Ningún request a `/actuator/**` deja línea `WEB_SERVICE_AUDIT` (`OutputCaptureExtension`). |
| `e2e/LoggersEndpointIT` | e2e | `ADMIN` cambia un logger del backend a `DEBUG` (204) y `GET /actuator/loggers/{nombre}` lo muestra como `configuredLevel`; nivel inválido → 400 `ApiError` sin cambio; `USER` → 403 sin cambio; visitante → 401. Restaura el nivel al final del test. |
| `e2e/BusinessMetricsIT` | e2e + `MeterRegistry` | Compra, venta y cancelación aceptadas y rechazadas (409 sin saldo, 404 jugador u orden inexistente, 403 orden ajena); `ADMIN` que intenta comprar (403 del filtro) no cuenta; login correcto, contraseña incorrecta, usuario inexistente; login con cuerpo inválido (400) no cuenta; registro exitoso y duplicado; gauges de usuarios y órdenes abiertas suben y bajan; la respuesta de cada caso es la misma de siempre (status y cuerpo). |
| `service/quote/RecalculationMetricsIT` | integración | `manual` + `success`; `blocked` con el `RecalculationLock` tomado y con el `SyncLock` tomado (en el mismo hilo, sin concurrencia); arranque con `on-startup=true` y sin semanas pendientes no registra nada (se arma el `QuoteStartupRecalculation` a mano para llamarlo sin `@Async`). |
| `service/sync/SyncMetricsIT` | integración, `@MockitoBean` del adapter de Football-Data y token por propiedad, como los IT de sincronización existentes | `synchronizeAll` y `synchronizeLeague` de las dos fuentes: `success`, `failure` (liga fallida / corte), `in_progress` (con el `SyncLock` tomado); `SyncDisabledException` no registra; la cadena (`SyncCoordinator.runWeeklyChain()`) registra una sola muestra `whoscored/full` y ninguna de Football-Data sin token; una manual completa registra una sola. |
| `service/sync/FootballDataHealthIndicatorTest` | unitario | con y sin token (también en blanco); el detalle nunca contiene el token. |
| `service/sync/SyncHealthIndicatorTest` | unitario, Mockito + `Clock` fijo | sin sincronizar, al día, vencida, borde exacto (`catchUpAfter` justo → al día, un nanosegundo más → vencida), una fuente al día y otra no; nunca `DOWN`. |
| `e2e/HealthIndicatorsIT` | e2e | perfil `test`: `GET /actuator/health` 200 `UP`; `ADMIN` ve `footballData` y `sync` en `UNKNOWN` con sus detalles; con token por propiedad, `footballData` `UP`. |
| `observability/BusinessMetricsTest` | unitario | todas las combinaciones registradas en cero, con descripción en español; ningún tag fuera del vocabulario. |
| `observability/BusinessMetricsAspectTest` | unitario, `ProceedingJoinPoint` mockeado | devuelve el mismo objeto y relanza la misma instancia; clasificación de cada excepción; un registro que lanza no cambia el resultado (FR-018). |
| `config/ActuatorHealthConfigTest` | unitario | el grupo envuelto muestra componentes solo con principal y delega todo lo demás. |
| `modelo/sync/SyncFreshnessTest`, `modelo/sync/SyncReportOutcomeTest` | unitarios del dominio | `CatchUpPolicy.freshness` (mismo borde que `decide`) y `succeeded()` de los dos informes. |
| `persistence/repository/market/OpenOrdersCountIT`, `persistence/repository/user/UserCountIT` | integración | `countOpen()` cuenta `PENDING` y `PARTIAL` y no `EXECUTED` ni `CANCELLED`; `count()` de usuarios. |
| `shared/GlobalExceptionHandlerResponseStatusTest` | unitario | 400 → `ApiError` con mensaje fijo; otro status se relanza. |

Los tests de arquitectura de la 010 no cambian y tienen que seguir pasando con las clases nuevas
(B1: `observability/` y `config/` no tocan servicio ni persistencia; los gauges y los indicadores
están en `service/`).

**Alternativas descartadas**:

- `@DirtiesContext` para tener contadores en cero: multiplica los contextos y el tiempo de la
  suite; la diferencia antes/después alcanza.
- Medir con hilos concurrentes reales para `blocked`/`in_progress`: los candados no son
  reentrantes, así que tomarlos en el mismo hilo reproduce el rechazo de forma determinista.

---

## D13. Sonar

**Decisión**: se escribe para no sumar issues:

- `throws Throwable` en los `@Around` lo impone `proceed()`; el aspecto de la 011 tiene la misma
  firma y no generó issue (no hizo falta la exclusión `e4` que se había previsto).
- El guard captura `RuntimeException`, no `Exception` ni `Throwable`, y loguea con placeholders.
- Nombres de métricas, etiquetas y descripciones como constantes; enums para los valores.
- Los cuatro advices de sincronización comparten un método privado: sin código duplicado.
- Nada de secretos: el ejemplo de Prometheus solo nombra un archivo ignorado.

**Por qué**: el Principio V exige menos de 10 issues y la feature no debería sumar ninguno.

**Alternativas descartadas**: agregar exclusiones preventivas en `build.gradle`; se agregan solo si
el análisis marca algo y queda justificado ahí.

---

## Verificación de nombres contra el código (T003, 2026-11-21)

Se contrastaron con el código todos los nombres que usa el diseño. **No hay diferencias**: firmas,
visibilidades, excepciones, constantes y beans coinciden con lo que dan por hechos D3 a D12.
Detalles que confirman el diseño:

- `MarketOrderService.buy/sell(long, long, int)` y `cancel(long, long)` devuelven `BookOutcome`;
  `AuthService.login` devuelve `SessionToken` y `register` devuelve `RegisteredUser`;
  `QuoteRecalculationService.recalculate(RecalculationOrigin)` devuelve `RecalculationReport`.
- `SyncService` y `StatsSyncService`: `synchronizeAll(SyncOrigin)` y `synchronizeLeague(League)`
  públicos, `runFull(SyncOrigin)` package-private. `synchronizeAll` llama a `runFull` por
  auto-invocación (en `SyncService` a través del método privado `synchronize`, en
  `StatsSyncService` dentro de `lock.runExclusively`), así que el proxy no la cuenta dos veces.
  `SyncCoordinator.runWeeklyChain()` llama a `runFull` sobre los beans inyectados (proxies), dentro
  de `lock.runExclusively`, y omite Football-Data si `footballDataProperties.hasToken()` es falso.
- Excepciones: `InvalidCredentialsException` extiende `UnauthorizedException`;
  `RecalculationInProgressException`, `RecalculationBlockedBySyncException` y
  `SyncInProgressException` extienden `ConflictException`; `SyncDisabledException` extiende
  `ServiceUnavailableException`. Las diez excepciones de `modelo/market/exception/` extienden
  `ConflictException`, `NotFoundException` o `ForbiddenException` de `shared/`, salvo
  `MarketInvariantException`, que extiende `RuntimeException`. Como los pointcuts de órdenes y de
  recálculo son distintos, que las de recálculo también sean `ConflictException` no mezcla las
  clasificaciones.
- `RecalculationReport.wasInterrupted()`, `OrderStatus.isOpen()` (`PENDING`, `PARTIAL`),
  `MarketOrderSQL.status` como `String`, `AppUserSQLDAO extends JpaRepository<AppUserSQL, Long>`,
  `LastFullSyncRepository.find(SyncSource)`, `FootballDataProperties.hasToken()`,
  `SyncProperties.catchUpAfter()` (componente de record, `@NotNull` y positivo), el bean `Clock`
  (`Clock.systemUTC()`) de `config/ApplicationConfig`, `CatchUpPolicy.isUpToDate` privado (con
  `<= maxAge`, así que el borde exacto cuenta como al día), `INVALID_PARAMETERS` y
  `handleUnexpected` en `shared/GlobalExceptionHandler`, y `BEARER_AUTH`/`API_KEY_AUTH` públicos
  en `config/OpenApiConfig`: todos existen como los nombra el diseño.
- `e2e/AdminAccountIT` da de alta el `ADMIN` con `@DynamicPropertySource`
  (`futbolmarket.auth.admin.username/email/password`) y una base H2 propia
  (`spring.datasource.url` = `jdbc:h2:mem:admin-e2e-<sufijo>`).
