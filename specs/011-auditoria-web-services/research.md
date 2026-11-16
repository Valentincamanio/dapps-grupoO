# Investigación: Auditoría de web services

**Feature**: `011-auditoria-web-services` | **Fecha**: 2026-11-12 | **Plan**: [plan.md](./plan.md)

Cada decisión sigue el formato **Decisión / Por qué / Alternativas descartadas**. Las decisiones que
el pedido de planificación ya traía resueltas se adoptan tal cual y acá se fija el detalle. Las que
el plan resuelve distinto del pedido, o que el pedido no cubría, están marcadas con
**(distinto del pedido)** o **(no estaba en el pedido)**.

El código se relevó el 2026-11-12 sobre la rama `011-auditoria-web-services` (parte de `967f8e6`,
`main` con la 009 integrada). Los tests de arquitectura de la 010 todavía no están en esta rama
(no existe `src/test/java/.../architecture/`), así que las clases nuevas se diseñan para cumplir
las reglas que esa feature anunció (D4).

## Índice

- [D1. Librería de logging: Logback](#d1-librería-de-logging-logback)
- [D2. Mecanismo: un aspecto de Spring AOP](#d2-mecanismo-un-aspecto-de-spring-aop)
- [D3. Dependencia nueva: `spring-boot-starter-aspectj`](#d3-dependencia-nueva-spring-boot-starter-aspectj)
- [D4. Ubicación, nombres y reglas de la 010](#d4-ubicación-nombres-y-reglas-de-la-010)
- [D5. Logger propio y nivel INFO para todo](#d5-logger-propio-y-nivel-info-para-todo)
- [D6. El usuario y el tipo de credencial](#d6-el-usuario-y-el-tipo-de-credencial)
- [D7. La operación](#d7-la-operación)
- [D8. Los parámetros: nombres, exclusiones y serialización](#d8-los-parámetros-nombres-exclusiones-y-serialización)
- [D9. Enmascarado de secretos](#d9-enmascarado-de-secretos)
- [D10. Truncado y escape](#d10-truncado-y-escape)
- [D11. Tiempo, resultado y no interferencia](#d11-tiempo-resultado-y-no-interferencia)
- [D12. Qué invocaciones quedan dentro y cuáles afuera](#d12-qué-invocaciones-quedan-dentro-y-cuáles-afuera)
- [D13. Configuración de Logback](#d13-configuración-de-logback)
- [D14. Sonar](#d14-sonar)
- [D15. Tests](#d15-tests)
- [D16. Revisión de la spec durante el plan](#d16-revisión-de-la-spec-durante-el-plan)

## D1. Librería de logging: Logback

**Decisión**: Logback, el que Spring Boot ya trae con `spring-boot-starter-logging` (versión
1.5.38 según el BOM de Boot 4.1.1). El código usa la API de SLF4J 2.0.18, igual que el resto del
backend (`GlobalExceptionHandler`, los adapters).

**Por qué**: la consigna acepta Log4j o Logback. Logback ya está en el classpath, no agrega
dependencias y Boot lo configura con `logback-spring.xml`, que admite `<springProfile>`.

**Alternativas descartadas**: Log4j2. Obliga a excluir `spring-boot-starter-logging` de todos los
starters del proyecto y a sumar `spring-boot-starter-log4j2`, y no aporta nada que esta feature
necesite.

## D2. Mecanismo: un aspecto de Spring AOP

**Decisión**: un aspecto `@Aspect` + `@Component` con un único advice `@Around`. El pointcut, como
constante `String`: métodos públicos de las clases anotadas con `@RestController` dentro de
`ar.edu.unq.desapp.futbolmarket.controller` y sus subpaquetes.

```text
execution(public * ar.edu.unq.desapp.futbolmarket.controller..*.*(..))
    && @within(org.springframework.web.bind.annotation.RestController)
```

**Por qué**: el advice recibe el `ProceedingJoinPoint` con el método invocado y sus argumentos ya
convertidos (el cuerpo deserializado, los ids como `Long`, los enums), y los nombres de los
parámetros salen del propio método (D8). Es lo único que da "operación + parámetros con nombre"
sin parsear el request a mano.

**Alternativas descartadas**:

- **`Filter` (`OncePerRequestFilter`)**: corre antes que la seguridad y vería también los 401 y
  403, pero no sabe qué método atiende el request ni tiene los argumentos: habría que releer el
  cuerpo (con un wrapper que lo cachee) y reconstruir los nombres a mano. Además audita rutas que
  no son endpoints propios (actuator, Swagger) y habría que excluirlas una por una.
- **`HandlerInterceptor`**: sabe el `HandlerMethod`, pero en `preHandle` los argumentos todavía no
  se resolvieron y en `afterCompletion` ya no están. Da el método, no los valores.

**Limitación de los proxies de Spring AOP**: el aspecto se aplica a través de un proxy, así que
una llamada interna `this.metodo()` dentro del mismo controller no pasa por el advice. No afecta:
Spring MVC invoca cada endpoint sobre el bean del contexto, que es el proxy, y ningún controller
llama a otro endpoint suyo.

**Notas verificadas**:

- Siete controllers ya son proxies CGLIB por su `@Validated` de clase (`PlayerController`,
  `OrderController`, `PortfolioController`, `AuditController`, `QuoteController`,
  `QuoteAdminController`, `StrategyController`; `BoostController` también lo tiene). Los otros
  cuatro (`AuthController`, `AccountController`, `SyncController`, `StatsSyncController`) pasan a
  serlo por el aspecto. Spring MVC y springdoc resuelven los handlers sobre la clase real
  (`AopUtils.getTargetClass`), así que el Swagger no cambia. El quickstart lo verifica.
- **Orden respecto de la validación de métodos**: el auto-proxy de AspectJ es un
  `BeanPostProcessor` de máxima precedencia y crea el proxy con el advice de auditoría. Después,
  `MethodValidationPostProcessor` agrega su advisor **al final** del proxy existente (no llama a
  `setBeforeExistingAdvisors(true)`; verificado con `javap` sobre `spring-context` 7.0.9). El
  advice de auditoría envuelve entonces a la validación: una `ConstraintViolationException` por un
  `@Min(1)` en un `@PathVariable` pasa por el aspecto y deja su línea con `ERROR` (US3 escenario
  5). El e2e lo verifica con `GET /players/0`.
- Hoy todos los métodos públicos de los controllers son endpoints. Un método público que no lo
  fuera, invocado sobre el proxy, también dejaría línea. `equals` y `hashCode` del proxy CGLIB no
  pasan por los advisors.

## D3. Dependencia nueva: `spring-boot-starter-aspectj`

**Decisión**: agregar `implementation 'org.springframework.boot:spring-boot-starter-aspectj'`,
sin versión, en una línea propia del bloque `dependencies`, en su lugar alfabético entre
`spring-boot-starter-actuator` y `spring-boot-starter-data-jpa`.

**Por qué**:

- En Boot 4 el starter se llama `spring-boot-starter-aspectj` (en Boot 3 era
  `spring-boot-starter-aop`). Está declarado en el BOM `spring-boot-dependencies:4.1.1` con
  versión 4.1.1, junto con `aspectj.version` 1.9.25.1; `spring-boot-starter-aop` ya no figura en
  ese BOM.
- Hoy `aspectjweaver` 1.9.25.1 ya llega **transitivamente**
  (`spring-boot-starter-data-jpa` → `spring-boot-data-jpa` → `spring-aspects` → `aspectjweaver`),
  y eso alcanza para que `AopAutoConfiguration` (en `spring-boot-autoconfigure`, condicionada a
  `org.aspectj.weaver.Advice`) habilite los proxies de `@Aspect`. Igual se declara el starter: la
  feature usa AspectJ de forma directa y no puede depender de que JPA lo siga arrastrando. La
  constitución pide no agregar dependencias sin justificar, y esta es la justificación.
- La línea aislada en el bloque de `implementation` deja el conflicto con la 010 (que agrega
  ArchUnit en el bloque de `testImplementation`) en hunks distintos o, en el peor caso, trivial.

**Alternativas descartadas**: depender solo del `aspectjweaver` transitivo (frágil); declarar
`org.aspectj:aspectjweaver` a mano (el starter es la forma idiomática y su versión la maneja el
BOM).

## D4. Ubicación, nombres y reglas de la 010

**Decisión**: todo en `shared/` (capa transversal), sin carpetas nuevas:

| Clase | Rol |
|-------|-----|
| `shared/WebServiceAuditAspect` | `@Aspect @Component`: mide, invoca, relanza y escribe la línea en un `finally`. |
| `shared/WebServiceAuditFormatter` | `@Component` sin estado: arma el mensaje de la línea, describe al usuario, elige, serializa, enmascara y trunca los parámetros. Se instancia en los tests con `new`, sin contexto de Spring. |
| `shared/WebServiceInvocation` | `record` con los datos crudos de una invocación (autenticación, controller, método, argumentos, verbo, URI, tiempo, falla). Desacopla al formatter de AspectJ y del servlet. |
| `shared/CredentialType` | `enum { JWT, API_KEY }` que el filtro de seguridad deja como `details` de la autenticación (D6). |

Los nombres no chocan con la auditoría del mercado (`modelo/market/Audit*`, `AuditController`,
`AuditQueryService`), que no se toca.

**Reglas de la 010 que se cumplen por diseño** (los tests no están en esta rama):

- Las clases nuevas de `shared/` no importan nada de `controller/`, `service/`, `persistence/` ni
  `adapter/`. El pointcut es un `String`; no hay `instanceof` ni referencias a controllers
  concretos.
- **(no estaba en el pedido)** Tampoco importan `modelo/` ni `security/`. Hoy `modelo/` depende de
  `shared/` (las excepciones de dominio extienden `NotFoundException`, `UnauthorizedException`,
  etc.) y `security/` también (`ApiErrorResponseWriter` usa `ApiError`). Si `shared/` importara
  alguno de los dos, se formaría un ciclo entre paquetes, y el `LayerRulesTest` de la 010 controla
  ciclos. Por eso `CredentialType` vive en `shared/` y es `security/` quien lo importa.
- Inyección por constructor con `@RequiredArgsConstructor` y campos `private final`.
- No se crea ninguna excepción. La que sale del controller se relanza intacta.
- Sin `System.out`/`System.err` ni `java.util.logging`: el logging es SLF4J.
- No se agrega ningún `@RestControllerAdvice`.

**Alternativas descartadas**: un contexto o paquete nuevo `audit/` (la constitución prohíbe
paquetes de primer nivel por feature, y `shared/` ya es la capa transversal); poner
`CredentialType` en `security/` o en `modelo/auth/` (ciclo, ver arriba).

## D5. Logger propio y nivel INFO para todo

**Decisión**: `LoggerFactory.getLogger(WEB_SERVICE_AUDIT_LOGGER)` con la constante
`WEB_SERVICE_AUDIT_LOGGER = "WEB_SERVICE_AUDIT"`. Todas las líneas se escriben con `info(...)`,
también las de resultado `ERROR`. En `logback-spring.xml` el logger se fija en `INFO` con
`additivity="false"`, independiente del nivel del root.

**Por qué**: la auditoría registra eventos, no problemas. Los errores inesperados ya los loguea
`GlobalExceptionHandler.handleUnexpected` en `ERROR` con su stack trace, y eso no cambia. Usar
`WARN` por cada 400 o 401 de negocio (una contraseña incorrecta, un jugador inexistente) llenaría
el log de advertencias que no lo son. Con el nivel fijo en el propio logger, `logging.level.root=WARN`
no apaga la auditoría (FR-026); solo la apaga `logging.level.WEB_SERVICE_AUDIT=OFF`, que es
configurar el propio registro de forma explícita.

**Alternativas descartadas**: `WARN` o `ERROR` para los resultados con error; un logger con el
nombre de la clase (atarlo al FQCN hace que cualquier refactor rompa la configuración).

## D6. El usuario y el tipo de credencial

**Decisión**: el usuario sale de `SecurityContextHolder.getContext().getAuthentication()`, sin
consultar la base:

- Autenticado: `user=<id>(<ROL>,<CREDENCIAL>)`, por ejemplo `user=42(USER,JWT)`. El id es el
  principal (`Long`, lo pone `CredentialAuthenticationFilter`). El rol sale de la authority con
  prefijo `ROLE_`, sin el prefijo.
- `null`, no autenticado o `AnonymousAuthenticationToken`: `user=anonymous`.

**(no estaba en el pedido)** El tipo de credencial (FR-006, clarificado en la spec) hoy no queda en
ningún lado: el filtro arma el mismo `UsernamePasswordAuthenticationToken` para JWT y para clave de
API. Cambio mínimo en `security/CredentialAuthenticationFilter`: `resolveUser` informa con qué
credencial resolvió y `authenticate` guarda `CredentialType.JWT` o `CredentialType.API_KEY` con
`setDetails(...)`. No cambia qué requests se aceptan ni qué responde el filtro, y no se modifica
ningún test. El formatter lee `details`. Si alguna vez no es un `CredentialType` (por ejemplo, un
test que arma su propia autenticación), escribe el rol solo: `user=42(USER)`.

En las rutas públicas el filtro no evalúa credenciales (`shouldNotFilter` con el mismo matcher que
`permitAll`), así que esas líneas dicen `anonymous` aunque el request traiga un token válido. Es la
revisión de la spec de D16.

**Alternativas descartadas**: deducir el tipo de los headers en el aspecto (duplica la regla de
precedencia Bearer sobre `X-API-Key` que vive en el filtro); consultar la base para traer el
username (la decisión 5 del pedido lo prohíbe y suma una consulta por request); hacer opcional la
autenticación en rutas públicas (descartada por el equipo, D16).

## D7. La operación

**Decisión**: `op=<Controller>.<método> <VERBO> <URI>`, por ejemplo
`op=PortfolioController.getPortfolio GET /users/42/portfolio`.

- Controller y método: `joinPoint.getSignature().getDeclaringType().getSimpleName()` y
  `getName()`. La firma es la del método declarado en la clase real, no la del proxy CGLIB.
- Verbo y URI: del `HttpServletRequest` que da `RequestContextHolder` (`ServletRequestAttributes`),
  con `getMethod()` y `getRequestURI()`. La URI va **sin** query string: los parámetros de query ya
  están en `params` con su nombre y su valor.
- Si no hay request en el hilo (no pasa en un endpoint, pero el código no lo asume), verbo y URI
  van como `-`.

**Alternativas descartadas**: la ruta del patrón (`/users/{id}/portfolio`): la spec pide "la ruta
pedida" y el valor del id ya está en `params`; `getRequestURL()` (agrega esquema y host, que no
aportan).

## D8. Los parámetros: nombres, exclusiones y serialización

**Decisión**:

- **Nombres**: los del parámetro Java (`Method#getParameters()` → `Parameter#getName()`). El plugin
  de Spring Boot configura todas las tareas `JavaCompile` con `-parameters`. Verificado: el
  `AuthController.class` compilado tiene el atributo `MethodParameters` con el nombre `request`.
  Ningún controller usa alias (`@RequestParam("otro")`), así que el nombre Java coincide con el
  del request.
- **Exclusiones** (FR-015): el parámetro anotado con `@AuthenticationPrincipal` (buscado con
  `AnnotatedElementUtils.hasAnnotation`, que cubre meta-anotaciones), y los de tipo
  `ServletRequest`, `ServletResponse`, `Errors` (cubre `BindingResult`) y `java.security.Principal`
  (cubre `Authentication`). Ningún dato de identidad se repite en `params`.
- **Serialización**: con el `JsonMapper` que Boot 4 autoconfigura (Jackson 3, paquete
  `tools.jackson`, `jackson-databind` 3.1.5; es el mismo bean que ya inyecta
  `security/ApiErrorResponseWriter`). Cada parámetro se convierte por separado con
  `valueToTree(...)` a un `JsonNode`, se enmascara y trunca sobre el árbol (D9, D10) y el objeto
  final se escribe con `writeValueAsString(...)`. Resultado: `params={"id":42,"date":null}`.
- **Nunca `toString()`**: los records de request (`LoginRequest`, `RegisterRequest`,
  `ChangePasswordRequest`) imprimen la contraseña en su `toString()` generado.
- **Valor no representable** (FR-021): si `valueToTree` de un parámetro lanza `JacksonException`
  (en Jackson 3 es unchecked: `tools.jackson.core.JacksonException extends RuntimeException`;
  verificado con `javap`), ese parámetro va con el marcador `"<no representable>"` y el resto se
  escribe. Se captura `JacksonException`, no `Exception`.
- Un parámetro `null` va como `null` explícito (FR-019).

**Alternativas descartadas**: `MethodSignature#getParameterNames()` (da lo mismo, pero obliga al
formatter a conocer AspectJ y lo hace más difícil de testear sin Spring); un `JsonMapper` propio
con `JsonMapper.builder().build()` (podría serializar distinto que la API, por ejemplo las fechas);
serializar todo el mapa de una vez (un solo valor raro tiraría todos los parámetros).

## D9. Enmascarado de secretos

**Decisión**: se recorre el árbol JSON de los parámetros y se reemplaza por `"***"` el valor de
**toda propiedad cuyo nombre normalizado contenga** `password`, `token`, `apikey` o `secret`. El
nombre se normaliza a minúsculas y sin `_` ni `-`, así `api_key` y `X-Api-Key` también cuentan.

- Aplica en cualquier nivel: propiedades de objetos anidados y objetos dentro de arrays.
- Aplica también al nombre del parámetro de primer nivel (un `@RequestParam String token`).
- El valor se reemplaza sea cual sea: string, número, objeto, array, vacío o `null` (la spec pide
  no revelar si venía vacío).
- La máscara es fija y no revela el largo. Se aplica **antes** de truncar.
- Cubre `password`, `currentPassword`, `newPassword`, `token`, `apiKey` y cualquier campo futuro
  con esos nombres, sin configurar nada por endpoint.

**Por qué**: la spec reconoce el secreto por el nombre del campo. Enmascarar sobre el árbol, y no
con un regex sobre el texto, no depende de comillas ni de escapes.

**Riesgo aceptado**: un campo de negocio que contenga `token` en su nombre (el mercado habla de
"tokens" de jugadores) también saldría enmascarado. Hoy ningún request tiene un campo así
(revisado en `controller/*/dto/*Request.java`). Si aparece, se pierde un dato en el log pero no
se filtra un secreto, que es el error barato.

**Alternativas descartadas**: `@JsonIgnore` o anotaciones propias en los DTO (obliga a acordarse
en cada DTO nuevo, y cambiaría la deserialización de la API); un `MixIn`/serializer de Jackson
registrado en el mapper de la app (afectaría también las respuestas de la API).

## D10. Truncado y escape

**Decisión** (FR-017, FR-018):

- Constantes `MAX_VALUE_LENGTH = 500` y `MAX_PARAMS_LENGTH = 2000`, y la marca
  `TRUNCATION_MARK = "...(truncado)"`.
- Por valor: cada string del árbol (campo, elemento de colección, parámetro de ruta o de query) de
  más de 500 caracteres se corta a 500 y se le agrega la marca. Números, booleanos y `null` no se
  tocan.
- Total: si el JSON de todos los parámetros supera los 2000 caracteres, se corta a 2000 y se le
  agrega la marca. En ese caso el texto deja de ser JSON válido, pero la marca lo deja visible.
- **Escape**: Jackson ya escapa los caracteres de control (`\n`, `\r`, `\t` y el resto < 0x20)
  dentro de los strings, así que `params` ocupa siempre una línea. El resto de los campos sale de
  datos del sistema (id, rol, nombres de clase), salvo la URI. Igual se pasa por el mismo escape
  de caracteres de control, por si un conector HTTP dejara pasar uno.

**Alternativas descartadas**: un solo límite de 2000 (el pedido lo mencionaba, pero la spec
clarificada pide los dos); truncar antes de enmascarar (podría cortar la máscara a la mitad y, peor,
dejar visible el principio de un secreto si el enmascarado fallara).

## D11. Tiempo, resultado y no interferencia

**Decisión**:

```text
start = System.nanoTime()
try        → resultado = joinPoint.proceed(); completado = true; return resultado
catch (RuntimeException e) → falla = e; throw e          // la misma instancia, intacta
finally    → elapsed = System.nanoTime() - start; escribir la línea
```

- `time=<ms>ms`: `TimeUnit.NANOSECONDS.toMillis(elapsed)`. Se mide antes de serializar, así el
  tiempo no incluye la auditoría.
- `result=OK` si `proceed()` volvió; `result=ERROR(<NombreSimple>)` con
  `getClass().getSimpleName()` de la excepción, sin el mensaje (FR-010). La excepción sigue su
  camino y `GlobalExceptionHandler` responde exactamente igual que hoy (FR-020).
- **Por qué `RuntimeException` y no `Throwable`**: ningún controller declara excepciones checked
  (revisado: no hay `throws` en `controller/`), así que lo que puede salir de un endpoint es
  `RuntimeException` o un `Error` de la JVM. Capturar `RuntimeException` para registrarla y
  relanzarla es lo más acotado que cubre todos los casos reales, y no choca con S1181 (capturar
  `Throwable`/`Error`). Si sale un `Error` (o, en el futuro, una excepción checked), el `finally`
  escribe `result=ERROR` sin tipo: la línea no se pierde.
- El advice declara `throws Throwable` porque `ProceedingJoinPoint.proceed()` lo declara (D14).
- **Si escribir falla** (FR-021): los appenders de Logback no propagan errores de escritura (los
  reportan en su status interno), así que un disco lleno no rompe el request. La serialización
  captura `JacksonException` (D8). El formatter no lanza ninguna otra excepción por diseño, y los
  tests unitarios lo cubren con valores raros.
- **Concurrencia**: cada invocación arma su mensaje con variables locales y hace una única llamada
  a `info(...)`. Los appenders de Logback serializan la escritura, así que las líneas no se mezclan.

## D12. Qué invocaciones quedan dentro y cuáles afuera

Con el aspecto sobre los controllers propios, el alcance resulta así, sin código extra:

| Caso | ¿Línea? | Por qué |
|------|---------|---------|
| Endpoint propio, termina bien | Sí, `OK` | Pasa por el proxy. |
| Excepción de servicio o de dominio (404, 409, 401 de login, 503) | Sí, `ERROR(...)` | Sale de `proceed()`. |
| Restricción sobre un parámetro (`@Min(1)` en un controller `@Validated`) | Sí, `ERROR(ConstraintViolationException)` | La validación es un advisor interno del mismo proxy (D2). |
| Cuerpo ilegible, `@Valid` del body, conversión de tipo, parámetro faltante | No | Spring MVC falla al resolver los argumentos, antes de llamar al proxy (D16). |
| 401/403 del filtro de seguridad | No | No llega al `DispatcherServlet`. |
| Ruta o verbo sin endpoint | No | No hay handler. |
| Actuator, Swagger/OpenAPI, consola de H2, frontend | No | No son `@RestController` del paquete `controller`. |
| Procesos programados y sincronización al arranque | No | Llaman a servicios, no a controllers. `POST /players/sync` sí deja la suya. |

## D13. Configuración de Logback

**Decisión**: un `backend/src/main/resources/logback-spring.xml` nuevo:

- Mantiene el log general como hoy: `include` de `org/springframework/boot/logging/logback/defaults.xml`
  y de `console-appender.xml` (el appender `CONSOLE` de Boot, con su pattern y sus propiedades
  `logging.pattern.*`/`logging.threshold.*`), y root en `INFO` con `CONSOLE`. **No** se incluye
  `base.xml`: suma un appender `FILE` que escribiría `spring.log` en el directorio temporal, y hoy
  el proyecto no escribe ningún archivo de log general.
- Propiedades de Logback (`<property>`): `WEB_SERVICE_AUDIT_FILE=logs/web-service-audit.log`,
  `WEB_SERVICE_AUDIT_MAX_FILE_SIZE=10MB`, `WEB_SERVICE_AUDIT_MAX_HISTORY=30`,
  `WEB_SERVICE_AUDIT_TOTAL_SIZE_CAP=1GB` y el pattern `WEB_SERVICE_AUDIT_PATTERN` (contrato).
- El pattern del timestamp: `%d{yyyy-MM-dd'T'HH:mm:ss.SSSXXX, America/Argentina/Buenos_Aires}`:
  ISO-8601 con milisegundos y desplazamiento, la misma zona que ya usan los crons del proyecto.
- Appender `WEB_SERVICE_AUDIT_CONSOLE` (`ConsoleAppender`, mismo pattern que el archivo: FR-012
  pide el mismo formato en los dos).
- Appender `WEB_SERVICE_AUDIT_FILE` (`RollingFileAppender` + `SizeAndTimeBasedRollingPolicy`):
  `fileNamePattern=logs/web-service-audit.%d{yyyy-MM-dd}.%i.log.gz`, rotación diaria y por tamaño,
  `maxHistory` y `totalSizeCap` desde las propiedades.
- `<springProfile name="test">`: logger `WEB_SERVICE_AUDIT` en `INFO`, `additivity="false"`, solo
  consola (los e2e lo capturan con `OutputCaptureExtension`, y los tests no crean `logs/`).
- `<springProfile name="!test">`: el mismo logger con consola y archivo.
- La ruta `logs/` es relativa al directorio de trabajo; con `./gradlew bootRun` es `backend/`, igual
  que `data/` de la base local.
- `.gitignore` de la raíz: `backend/logs/`. El `*.log` existente no cubre los rotados `.log.gz`.

**Alternativas descartadas**: configurar todo con `logging.*` en `application.yaml` (no permite un
appender propio con additivity ni rotación separada); `<springProperty>` leyendo de
`application.yaml` para la retención (suma propiedades de la app para algo que es de la
configuración de logging); `TimeBasedRollingPolicy` sola (sin tope por archivo).

## D14. Sonar

**Decisión**:

- `throws Throwable` en el `@Around`: la regla `java:S112` (excepciones genéricas) de SonarJava no
  marca un `throws` genérico cuando lo exige un método invocado, y `proceed()` declara
  `Throwable`. Si igual aparece, se agrega la exclusión `e4` en el bloque `sonar` del
  `build.gradle`, con el patrón de `e1`–`e3`: `ruleKey java:S112`, `resourceKey
  **/shared/WebServiceAuditAspect.java` y un comentario que explica que el `throws` lo impone la
  firma de `ProceedingJoinPoint.proceed()` y que la excepción se relanza intacta.
- Sin números mágicos: `MAX_VALUE_LENGTH`, `MAX_PARAMS_LENGTH`, la máscara, los marcadores y el
  nombre del logger son constantes.
- Sin concatenar en el log: `info("user={} | op={} {} {} | params={} | time={}ms | result={}", ...)`
  con placeholders de SLF4J.
- Métodos cortos: el recorrido del árbol separa "objeto", "array" y "string" en métodos propios
  para no anidar `if`.
- No hay `catch` genérico: `JacksonException` en la serialización y `RuntimeException` relanzada en
  el aspecto (D11).

## D15. Tests

**Decisión** (sin TDD, junto con cada tarea; no se modifica ningún test existente):

- **Unitario** `shared/WebServiceAuditFormatterTest` (test plano, sin Spring, con
  `new WebServiceAuditFormatter(JsonMapper.builder().build())` y un controller de prueba anidado
  en el test con métodos que reciben `@AuthenticationPrincipal`, `HttpServletRequest`, records con
  secretos anidados, etc.). Cubre: enmascarado de `password`, `currentPassword`, `newPassword`,
  `token`, `apiKey` y `api_key`, en primer nivel, anidados y dentro de listas; secreto `null` o
  vacío; truncado por valor (500) y total (2000) con la marca; saltos de línea escapados; `null`
  explícito; exclusión de `@AuthenticationPrincipal`, `HttpServletRequest` y `BindingResult`;
  usuario anónimo (`null`, `AnonymousAuthenticationToken`); usuario con JWT, con clave de API y sin
  `details`; valor no representable con marcador; resultado `OK` y `ERROR(Tipo)`.
- **e2e** `e2e/WebServiceAuditIT` (`@SpringBootTest`, `@AutoConfigureMockMvc`,
  `@ActiveProfiles("test")`, `MockMvcTester`, `@ExtendWith(OutputCaptureExtension.class)`, con
  `AuthTestHelper` para registrar usuarios únicos). Cubre: login → hay una línea con
  `AuthController.login`, nivel `INFO`, `user=anonymous`, `"password":"***"` y la contraseña no
  aparece; registro y cambio de contraseña enmascarados; endpoint protegido con token →
  `user=<id>(USER,JWT)`, y con clave → `(USER,API_KEY)` sin el valor de la clave; exactamente una
  línea por invocación; `GET /players/{id}` inexistente → `ERROR(PlayerNotFoundException)` en
  `INFO` y la misma respuesta 404 de siempre; `GET /players/0` → `ERROR(ConstraintViolationException)`
  y el mismo 400; login con cuerpo vacío → el mismo 400 y ninguna línea; `GET /actuator/health` →
  ninguna línea.
- Nombres en español y estructura setup - execute - verify, como el resto.

## D16. Revisión de la spec durante el plan

**(distinto del pedido / de la spec original)** El plan encontró tres puntos donde la spec
clarificada chocaba con el mecanismo acordado o con el código vigente. El equipo eligió enmendar la
spec en los tres (ver la segunda sesión de Clarifications en [spec.md](./spec.md)):

1. **Rechazos previos a la invocación**: la spec pedía auditar los 400 de un cuerpo inválido o
   ilegible (FR-001, FR-001a). Spring MVC los lanza al resolver los argumentos, antes de llamar al
   proxy, así que el aspecto no los ve. Se descartó complementarlo con un
   `HandlerExceptionResolver` (y un interceptor para el tiempo): no dejan línea, como los 401/403.
   FR-001a quedó eliminado.
2. **Usuario en rutas públicas**: `GET /players/**` es público y el filtro no evalúa credenciales
   ahí, así que el escenario 1 de la US1 (token en `GET /players/{id}`) daría `anonymous`. Se
   mantiene la autenticación como está y el escenario pasa a `GET /users/{id}/portfolio`.
3. **Timestamp**: la spec decía "momento en que empieza"; el `%d` de Logback marca la escritura, al
   terminar. Queda el fin; el inicio es timestamp − time.

Además, el plan adopta dos puntos de la spec clarificada que el pedido no listaba: el tipo de
credencial en `user` (D6) y el límite de 500 caracteres por valor además del de 2000 (D10). La
máscara es `***`; la spec decía "por ejemplo `****`".
