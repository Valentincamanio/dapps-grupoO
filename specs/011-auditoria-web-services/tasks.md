---

description: "Lista de tareas de la feature 011: auditoría de web services"
---

# Tareas: Auditoría de web services

**Entrada**: documentos de diseño de `/specs/011-auditoria-web-services/`

**Prerrequisitos**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md),
[data-model.md](./data-model.md), [contracts/web-service-audit-line.md](./contracts/web-service-audit-line.md),
[quickstart.md](./quickstart.md)

**Tests**: SÍ. La constitución (Principio IV) y la spec (Assumptions, "Perfil de test") piden tests nuevos que
verifiquen el contenido de las líneas: un unitario sin Spring (`shared/WebServiceAuditFormatterTest`) y un end to
end con MockMvc (`e2e/WebServiceAuditIT`), los dos de research D15. **Sin TDD**: cada test se escribe junto con
(después de) la implementación de su tarea, y al cerrar la tarea el test existe y pasa. Convenciones: AssertJ,
setup / execute / verify en tres bloques, nombres descriptivos en español
(`elLoginRegistraLaContrasenaEnmascarada`), H2 en memoria con `@ActiveProfiles("test")`.

**Organización**: por historia de usuario (US1 a US4, en el orden de prioridad de la spec). US1 y US2 son las dos
P1 y forman juntas el MVP: la spec dice que la US1 no se puede entregar sin la US2 (un registro con contraseñas en
claro es un incidente de seguridad).

## Formato: `[ID] [P?] [Historia] Descripción`

- **[P]**: se puede hacer en paralelo (archivos distintos, sin dependencias pendientes)
- **[US#]**: historia de usuario a la que pertenece la tarea
- Cada descripción lleva la ruta exacta del archivo

## Convenciones de rutas

- Backend, producción: `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/`
- Backend, tests: `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/` (`shared/` para el unitario, `e2e/` para
  el end to end)
- Recursos: `backend/src/main/resources/`
- Todo el código nuevo va en `shared/` (capa transversal, research D4). Fuera de `shared/` solo cambian
  `security/CredentialAuthenticationFilter.java`, `backend/build.gradle`, `.gitignore` y `README.md`.

## Reglas que valen para todas las tareas

- **No se modifica ni se borra ningún test existente** (SC-003). Los apoyos de test existentes
  (`e2e/AuthTestHelper`, `service/market/MarketDatabase`, `service/quote/QuoteDatabase`) se **usan** tal cual, sin
  agregarles métodos. Agregar métodos de test a los dos archivos de test que crea esta feature está permitido.
- **Una sola dependencia nueva**: `spring-boot-starter-aspectj` (research D3). No se reemplaza `build.gradle` ni los
  `application*.yml`: al primero se le agrega una línea (y la exclusión `e4` solo si Sonar la pide); los segundos
  no se tocan.
- **No cambia ninguna respuesta de la API** (FR-020): ni status, ni cuerpo, ni headers. No se crean excepciones, no
  se toca `GlobalExceptionHandler`, no se agrega ningún `@RestControllerAdvice`, no se agregan ni modifican
  endpoints, DTO ni Swagger.
- **No se toca la auditoría del mercado de la 008** (`modelo/market/Audit*`, `AuditController`,
  `AuditQueryService`, la tabla `audit_entries`) (FR-004).
- Las clases nuevas de `shared/` **no importan** nada de `controller/`, `service/`, `persistence/`, `adapter/`,
  `modelo/` ni `security/` (evita ciclos que controla el `LayerRulesTest` de la 010, research D4). El pointcut es
  un `String`.
- Calidad (Principio V): sin `catch (Exception)` ni `catch (Throwable)` (se capturan `JacksonException` y
  `RuntimeException` relanzada); SLF4J con placeholders, sin concatenar; sin números mágicos (todo valor fijo es una
  constante con nombre); `@RequiredArgsConstructor` + `private final`; métodos cortos; sin `System.out`.
- Idioma (Principio VII): identificadores en inglés; marcas de la línea (`...(truncado)`, `<no representable>`),
  Javadoc y comentarios en español; las claves de la línea (`user`, `op`, `params`, `time`, `result`) como las fija
  el contrato.
- Nunca se serializa un argumento con `toString()`: los records de request imprimen la contraseña en el suyo
  (research D8).

---

## Fase 1: Setup (línea de base y configuración del proyecto)

**Propósito**: partir de una base verde, confirmar los nombres del diseño y dejar la dependencia y el
`.gitignore` listos.

- [X] T001 Correr la línea de base sin tocar nada: `cd backend && ./gradlew build`. Tiene que dar `BUILD SUCCESSFUL`; si algo ya falla, anotarlo y avisar antes de seguir. Guardar unas líneas de la consola de `./gradlew bootRun` (arranque y un request) para comparar después el formato del log general (T006)
- [X] T002 Contrastar con el código los nombres que usa el diseño y anotar cualquier diferencia al final de `specs/011-auditoria-web-services/research.md` antes de seguir: `CredentialAuthenticationFilter.resolveUser` y `authenticate` en `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/security/CredentialAuthenticationFilter.java`; el bean `tools.jackson.databind.json.JsonMapper` que inyecta `security/ApiErrorResponseWriter`; que ningún método de `controller/**` declare `throws` ni use alias en `@RequestParam("…")`/`@PathVariable("…")`; que los `.class` compilados tengan `MethodParameters` (`javap -v` sobre `build/classes/java/main/.../controller/auth/AuthController.class`); los apoyos `e2e/AuthTestHelper` (`registerUser()`, `login(TestUser)`, `TestUser.apiKey()`), `service/market/MarketDatabase` y `service/quote/QuoteDatabase` (`team`, `player`, `quote`) y cómo los usa `e2e/OrderControllerIT`; la firma de `PortfolioController.getPortfolio` (`@AuthenticationPrincipal Long requesterId`, `id`, `date`); los 12 controllers de `controller/**` con `@RestController`
- [X] T003 [P] Agregar `implementation 'org.springframework.boot:spring-boot-starter-aspectj'` (sin versión) en `backend/build.gradle`, en una línea propia del bloque `dependencies`, en orden alfabético entre `spring-boot-starter-actuator` y `spring-boot-starter-data-jpa` (research D3). No tocar ninguna otra línea
- [X] T004 [P] Agregar `backend/logs/` en `.gitignore` de la raíz, con un comentario corto en español ("auditoría de web services: archivo y rotados"); el `*.log` existente no cubre los `.log.gz` rotados (research D13)

---

## Fase 2: Fundacional (prerrequisitos bloqueantes)

**Propósito**: la configuración de Logback con el logger propio y los dos tipos de `shared/` que usan el
formatter y el filtro. Sin `logback-spring.xml` las líneas saldrían con el pattern del log general y los tests de
formato de US1 a US4 no podrían pasar.

**⚠️ CRÍTICO**: ninguna historia empieza hasta cerrar esta fase.

- [X] T005 Crear `backend/src/main/resources/logback-spring.xml` según research D13 y el contrato: (a) log general igual que hoy: `<include resource="org/springframework/boot/logging/logback/defaults.xml"/>` y `<include resource="org/springframework/boot/logging/logback/console-appender.xml"/>`, `<root level="INFO">` con `CONSOLE`; **no** incluir `base.xml` (sumaría un `FILE` con `spring.log`); (b) propiedades `WEB_SERVICE_AUDIT_FILE=logs/web-service-audit.log`, `WEB_SERVICE_AUDIT_MAX_FILE_SIZE=10MB`, `WEB_SERVICE_AUDIT_MAX_HISTORY=30`, `WEB_SERVICE_AUDIT_TOTAL_SIZE_CAP=1GB` y `WEB_SERVICE_AUDIT_PATTERN=%d{yyyy-MM-dd'T'HH:mm:ss.SSSXXX, America/Argentina/Buenos_Aires} | %level | %msg%n`; (c) appender `WEB_SERVICE_AUDIT_CONSOLE` (`ConsoleAppender` con ese pattern); (d) appender `WEB_SERVICE_AUDIT_FILE` (`RollingFileAppender` con `SizeAndTimeBasedRollingPolicy`, `fileNamePattern=logs/web-service-audit.%d{yyyy-MM-dd}.%i.log.gz`, `maxFileSize`, `maxHistory` y `totalSizeCap` desde las propiedades, mismo pattern); (e) `<springProfile name="test">`: `<logger name="WEB_SERVICE_AUDIT" level="INFO" additivity="false">` solo con la consola (los tests no crean `logs/`); (f) `<springProfile name="!test">`: el mismo logger con consola y archivo. Comentarios en español que expliquen el `additivity`, por qué el nivel es fijo (FR-026: `logging.level.root` no lo apaga, `logging.level.WEB_SERVICE_AUDIT=OFF` sí) y la zona horaria
- [X] T006 Verificar la fase de configuración (depende de T003, T004 y T005): `cd backend && ./gradlew test` en verde sin tocar tests; `./gradlew bootRun` arranca, la consola del log general tiene el mismo formato que en T001 (PID, hilo, logger), no aparece ningún `spring.log` y `backend/logs/web-service-audit.log` se crea vacío al arrancar (Logback abre el archivo cuando inicia el appender; todavía no hay quien escriba en el logger)
- [X] T007 [P] Crear `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/shared/CredentialType.java`: `enum CredentialType { JWT, API_KEY }` con Javadoc en español (lo deja el filtro de seguridad como `details` de la autenticación; vive en `shared/` y no en `security/` para no formar un ciclo, research D4 y D6). Sin imports de Spring
- [X] T008 [P] Crear `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/shared/WebServiceInvocation.java`: `record WebServiceInvocation(Authentication authentication, Class<?> controllerType, Method method, List<Object> arguments, String httpMethod, String requestUri, long elapsedNanos, RuntimeException failure, boolean completed)` según la tabla "Datos de entrada del formatter" de `specs/011-auditoria-web-services/data-model.md`. `authentication` y `failure` admiten `null`; `arguments` admite elementos `null` (no usar `List.of`, que los rechaza). Javadoc en español: datos crudos de una invocación, desacopla al formatter de AspectJ y del servlet. Imports solo de `org.springframework.security.core.Authentication` y del JDK

**Checkpoint**: Logback configurado, los dos tipos de `shared/` creados y la suite existente en verde.

---

## Fase 3: User Story 1 - Cada invocación deja su línea de auditoría (Priority: P1) 🎯 MVP (junto con US2)

**Goal**: toda invocación a un endpoint propio, público o protegido, deja exactamente una línea con timestamp,
usuario (id, rol y tipo de credencial, o `anonymous`), operación, parámetros de ruta, de query y del cuerpo, tiempo
y resultado.

**Independent Test**: se invoca `GET /users/{id}/portfolio` con un token de sesión y `GET /players/{id}` sin
credencial; cada invocación deja una sola línea con los seis datos completos (`user=<id>(USER,JWT)` y
`user=anonymous`), y `GET /actuator/health` y un 401 del filtro no dejan ninguna.

### Implementation for User Story 1

- [X] T009 [P] [US1] Cambiar `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/security/CredentialAuthenticationFilter.java` (research D6): `resolveUser` informa además con qué credencial resolvió (por ejemplo un `record ResolvedCredential(AppUser user, CredentialType type)` privado y anidado, `Optional<ResolvedCredential>`), y `authenticate` hace `setDetails(type)` sobre el `UsernamePasswordAuthenticationToken` antes de guardarlo en el contexto: `CredentialType.JWT` por el `Bearer`, `CredentialType.API_KEY` por la `X-API-Key`. No cambia la precedencia, ni `shouldNotFilter`, ni qué requests se aceptan, ni qué responde el filtro; actualizar el Javadoc de la clase con una oración sobre los `details`. Correr `./gradlew test --tests "*AccessControlIT" --tests "*AuthControllerIT" --tests "*AccountControllerIT"` en verde, sin tocarlos (depende de T007)
- [X] T010 [US1] Crear `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/shared/WebServiceAuditFormatter.java` (`@Component`, sin estado, `@RequiredArgsConstructor` con `private final JsonMapper jsonMapper`) con las reglas de `data-model.md` y del contrato que corresponden a esta historia. Constantes: `ANONYMOUS = "anonymous"`, `UNKNOWN_ROLE = "?"`, `ROLE_PREFIX = "ROLE_"`, `NO_REQUEST = "-"`, `UNREPRESENTABLE = "<no representable>"`, `TRUNCATION_MARK = "...(truncado)"`, `MAX_VALUE_LENGTH = 500`, `MAX_PARAMS_LENGTH = 2000`, `OK = "OK"`, `ERROR = "ERROR"`. Métodos públicos que usa el aspecto, cada uno devuelve el texto de un campo: (a) `user(Authentication)`: `anonymous` si es `null`, `AnonymousAuthenticationToken` o `!isAuthenticated()`; si no, `<principal>(<ROL>,<CREDENCIAL>)` con el rol de la primera authority `ROLE_*` sin el prefijo (`?` si no hay) y la credencial de `details` si es un `CredentialType`, o `<principal>(<ROL>)` si `details` es otra cosa o `null` (tabla "Usuario"); (b) `operation(WebServiceInvocation)`: `<controllerType.getSimpleName()>.<method.getName()>`; (c) `httpMethod(...)` y `requestUri(...)`: el valor o `-` si viene `null`, con los caracteres de control (`< 0x20` y `0x7F`) escapados como `\n`, `\r`, `\t` o `\uXXXX` (FR-018); (d) `params(WebServiceInvocation)`: recorre `method.getParameters()` en orden junto con `arguments`, salta los de tipo `ServletRequest`, `ServletResponse` y `Errors` (y subtipos), convierte cada argumento por separado con `jsonMapper.valueToTree(...)` (un `null` queda `null` explícito, FR-019; una `JacksonException` deja `"<no representable>"` en ese parámetro y sigue con el resto, FR-021), arma un `ObjectNode` nombre → valor con `Parameter#getName()`, trunca cada string del árbol (en objetos anidados y arrays, con métodos separados para objeto, array y string) a 500 caracteres + `...(truncado)`, lo escribe con `writeValueAsString` y, si supera 2000 caracteres, lo corta a 2000 + `...(truncado)` (FR-017); sin argumentos que registrar da `{}`; (e) `elapsedMillis(WebServiceInvocation)`: `TimeUnit.NANOSECONDS.toMillis(elapsedNanos)`; (f) `result(WebServiceInvocation)`: `OK` si `completed`, `ERROR` si no (el tipo de error lo suma la US3). Ningún método lanza excepciones: todo lo que puede fallar es la serialización y queda cubierto por el marcador. Javadoc en español con referencias a FR y a research D6 a D10 (depende de T008)
- [X] T011 [US1] Crear `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/shared/WebServiceAuditAspect.java` (`@Aspect @Component @RequiredArgsConstructor`, `private final WebServiceAuditFormatter formatter`) según research D2, D5, D7 y D11: constantes `WEB_SERVICE_AUDIT_LOGGER = "WEB_SERVICE_AUDIT"`, `CONTROLLER_ENDPOINTS = "execution(public * ar.edu.unq.desapp.futbolmarket.controller..*.*(..)) && @within(org.springframework.web.bind.annotation.RestController)"` y `MESSAGE = "user={} | op={} {} {} | params={} | time={}ms | result={}"`; logger `private static final Logger AUDIT = LoggerFactory.getLogger(WEB_SERVICE_AUDIT_LOGGER)`; un único `@Around(CONTROLLER_ENDPOINTS) public Object audit(ProceedingJoinPoint joinPoint) throws Throwable`: `start = System.nanoTime()`; `try { Object result = joinPoint.proceed(); completed = true; return result; } catch (RuntimeException e) { failure = e; throw e; } finally { elapsed = System.nanoTime() - start; escribir la línea }` (la misma instancia de la excepción, intacta). La escritura, en un método privado: arma el `WebServiceInvocation` con `SecurityContextHolder.getContext().getAuthentication()`, `joinPoint.getSignature().getDeclaringType()`, `((MethodSignature) joinPoint.getSignature()).getMethod()`, `Arrays.asList(joinPoint.getArgs())` y el `HttpServletRequest` de `RequestContextHolder.getRequestAttributes()` si es `ServletRequestAttributes` (si no, `null` → `-`), con `getMethod()` y `getRequestURI()` (sin query string), y hace **una sola** llamada `AUDIT.info(MESSAGE, …)` con los siete valores del formatter. El tiempo se mide antes de serializar. Javadoc en español: qué audita, qué no (tabla de research D12), por qué `RuntimeException` y no `Throwable`, y que el `throws Throwable` lo impone `proceed()` (depende de T010)

### Tests for User Story 1

- [X] T012 [P] [US1] Crear `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/shared/WebServiceAuditFormatterTest.java` (test plano, sin Spring: `new WebServiceAuditFormatter(JsonMapper.builder().build())`; un controller de prueba `static class` anidado en el test con métodos de las formas que hacen falta; invocaciones armadas a mano con `new WebServiceInvocation(...)` y el `Method` obtenido por reflexión). Casos de esta historia: `user` con `null`, con `AnonymousAuthenticationToken` y con un token no autenticado → `anonymous`; autenticado con `details` `JWT` → `42(USER,JWT)`, con `API_KEY` → `7(ADMIN,API_KEY)`, sin `details` → `42(USER)`, sin authority `ROLE_*` → `42(?,JWT)`; `operation` → `FakeController.getThing`; `httpMethod`/`requestUri` `null` → `-` y una URI con `\n` escapada en una línea; `params` con un `Long`, un `String` y un `null` → `{"id":42,"name":"x","date":null}`; un record anidado con una lista; un método sin argumentos → `{}`; los argumentos `HttpServletRequest` (un `MockHttpServletRequest`) y `BindingResult` no aparecen; un string de 501 caracteres sale con 500 + `...(truncado)` y uno de 500 sale entero, también dentro de una lista; un total de más de 2000 caracteres sale cortado a 2000 + `...(truncado)`; un valor con `\n`, `\r` y `\t` queda escapado y el resultado no contiene saltos de línea; un argumento que Jackson no puede serializar (un objeto con un getter que lanza una `RuntimeException`, o una referencia circular) deja `"<no representable>"` en ese parámetro y el resto se escribe; `elapsedMillis` de 12_500_000 ns → 12; `result` con `completed` → `OK` (depende de T010)
- [X] T013 [US1] Crear `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/e2e/WebServiceAuditIT.java` (`@SpringBootTest`, `@AutoConfigureMockMvc`, `@ActiveProfiles("test")`, `@ExtendWith(OutputCaptureExtension.class)`, `MockMvcTester`, `AuthTestHelper` para registrar usuarios únicos; si hace falta otra base H2, `@DynamicPropertySource` con `spring.datasource.url` propia como `OrderControllerIT`). Un método auxiliar privado que, del `CapturedOutput`, devuelve las líneas de auditoría (las que contienen ` | user=`) y otro que filtra las de una `op` dada. Casos: `GET /users/{id}/portfolio` con `Authorization: Bearer` → exactamente una línea que cumple la regex del contrato (`^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}[+-]\d{2}:\d{2} \| INFO \| user=`), con `user=<id>(USER,JWT)`, `op=PortfolioController.getPortfolio GET /users/<id>/portfolio`, `"id":<id>`, `"date":null`, `time=<n>ms` y `result=OK` (US1.1); el mismo endpoint con `X-API-Key` → `user=<id>(USER,API_KEY)` y el valor de la clave no aparece en ninguna línea (US1.2); `GET /players/{id}` (o `GET /players`) con un token válido → `user=anonymous` (US1.3); `GET /players?page=0&size=5&team=Bayern` → `"page":0`, `"size":5`, `"team":"Bayern"` y `"league":null` (US1.4); `POST /orders/buy` con cuerpo `{"playerId":…,"quantity":10}` → `params` con el cuerpo y sus campos con nombre y valor, sea cual sea el resultado (US1.5); tres invocaciones seguidas → tres líneas, una por invocación (FR-002); diez invocaciones simultáneas con un `ExecutorService` sobre `GET /players/{id}` con ids distintos → diez líneas completas, cada una con su propio id y sin mezclarse (US1.6); `GET /actuator/health`, un `GET /auth/me` sin credencial (401 del filtro) y `GET /ruta-inexistente` → ninguna línea (FR-003); todos los beans `@RestController` del contexto (`ApplicationContext#getBeansWithAnnotation`) son proxies AOP (`AopUtils.isAopProxy`) cuyo `Advised#getAdvisors()` incluye el advice de `WebServiceAuditAspect`, y son 12 (SC-001: cubre todos los endpoints sin invocarlos uno por uno) (depende de T009 y T011)
- [X] T014 [US1] Correr `cd backend && ./gradlew test` completo: todo en verde y `git status` muestra solo archivos nuevos en `backend/src/test/` (SC-003). Si algún test existente se rompe por el proxy o por los `details`, corregir la producción, nunca el test

**Checkpoint**: cada invocación deja su línea con los seis datos. Todavía **no** se puede entregar: falta el
enmascarado (US2).

---

## Fase 4: User Story 2 - Los datos sensibles nunca aparecen en claro (Priority: P1) 🎯 MVP

**Goal**: contraseñas, tokens, claves de API y secretos aparecen como `***` en cualquier nivel de los parámetros;
la identidad del usuario sale solo en `user`; la credencial del request y el cuerpo de la respuesta no aparecen.

**Independent Test**: se invocan login, registro, cambio de contraseña y generación de clave de API con valores
conocidos; ninguno de esos valores, ni el token ni la clave que devuelven, aparece en la salida capturada; en su
lugar aparece `***`.

### Implementation for User Story 2

- [X] T015 [US2] Sumar el enmascarado y las exclusiones de identidad a `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/shared/WebServiceAuditFormatter.java` (research D8 y D9, "Reglas de enmascarado" de `data-model.md`): constantes `MASK = "***"` y `SECRET_NAME_FRAGMENTS = List.of("password", "token", "apikey", "secret")`; un método `isSecret(String name)` que normaliza a minúsculas sin `_` ni `-` y busca los fragmentos; en `params`, **antes** de truncar: si el nombre del parámetro de primer nivel es secreto, su valor es `***`; si no, se recorre su árbol y toda propiedad con nombre secreto, en objetos anidados y en objetos dentro de arrays, se reemplaza por `***` sea cual sea su valor (string, número, objeto, array, vacío o `null`). Además se excluyen de `params` el parámetro anotado con `@AuthenticationPrincipal` (con `AnnotatedElementUtils.hasAnnotation`, que cubre meta-anotaciones) y los de tipo `java.security.Principal` (cubre `Authentication`) (FR-015). Actualizar el Javadoc con el riesgo aceptado de research D9 (un campo de negocio con `token` en el nombre también se enmascara) (depende de T010)

### Tests for User Story 2

- [X] T016 [US2] Agregar a `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/shared/WebServiceAuditFormatterTest.java` (métodos de prueba nuevos en el controller de prueba anidado y en records de prueba): `password`, `currentPassword` y `newPassword` de primer nivel en un record → `***`; `token`, `apiKey`, `api_key`, `X-Api-Key` (como nombre de propiedad de un `Map`) y `clientSecret` → `***`; un secreto en un objeto anidado y dentro de una lista de objetos → `***`; un secreto `null`, uno vacío y uno numérico → `***` (no revela si venía vacío); un `@RequestParam String token` de primer nivel → `"token":"***"`; un secreto de 600 caracteres sale `***` y no truncado (se enmascara antes de truncar); los campos que no son secretos (`username`, `email`) se registran normalmente; el argumento `@AuthenticationPrincipal Long userId` y uno de tipo `Authentication` no aparecen, y un método con solo el principal da `{}`; el valor de un secreto conocido no aparece en ninguna parte del resultado (depende de T015)
- [X] T017 [US2] Agregar a `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/e2e/WebServiceAuditIT.java`: `POST /auth/login` → una línea con `op=AuthController.login`, `user=anonymous`, el `username` en claro y `"password":"***"`, y ni la contraseña ni el token que devolvió la respuesta aparecen en la salida (US2.1, FR-016); `POST /auth/register` con contraseña y email conocidos → `"password":"***"`, el email y el username en claro, y la clave de API que devuelve la respuesta no aparece (US2.2); `PUT /auth/me/password` con token → `"currentPassword":"***"` y `"newPassword":"***"`, y ninguna de las dos aparece (US2.3); `POST /auth/me/api-key` → `params={}` y ni la clave vieja (la del `X-API-Key` usado) ni la nueva aparecen (US2.5, US2.6); `POST /orders/buy` con token → `params` no contiene `userId` ni el id del usuario fuera de `user=` (US2.5); ninguna línea de toda la clase contiene el token JWT ni la clave de API usados como credencial (SC-002, FR-014) (depende de T013 y T015)

**Checkpoint**: US1 + US2 completas: es el MVP entregable. Validar con quickstart §3.1 a §3.3.

---

## Fase 5: User Story 3 - Las invocaciones que terminan en error también quedan (Priority: P2)

**Goal**: una invocación que termina en excepción deja su línea en `INFO` con `result=ERROR(<Tipo>)`, sin el
mensaje, con el tiempo medido hasta el error, y el cliente recibe exactamente la misma respuesta de antes; los
rechazos previos a invocar el endpoint no dejan línea.

**Independent Test**: `GET /players/999999` y una compra que el dominio rechaza dejan cada una su línea con
`ERROR(PlayerNotFoundException)` y `ERROR(PlayerNotTradableException)` en `INFO`, con el mismo status y el mismo
cuerpo `ApiError` que verifican los IT existentes; `POST /auth/login` con `{}` responde el mismo 400 y no deja
línea.

### Implementation for User Story 3

- [X] T018 [US3] Completar `result(WebServiceInvocation)` en `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/shared/WebServiceAuditFormatter.java` (research D11, FR-010): `OK` si `completed`; `ERROR(<failure.getClass().getSimpleName()>)` si hay `failure`; `ERROR` solo si no completó y no hay `failure` (salió un `Error` de la JVM). Nunca el mensaje de la excepción. Verificar que `WebServiceAuditAspect` ya relanza la misma instancia (T011) y que el `finally` escribe también cuando sale un `Error` (depende de T010)

### Tests for User Story 3

- [X] T019 [US3] Agregar a `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/shared/WebServiceAuditFormatterTest.java`: `failure` = una `IllegalStateException("mensaje con datos")` → `ERROR(IllegalStateException)` y el mensaje no aparece; una subclase anónima o anidada de prueba → su nombre simple; sin completar y sin `failure` → `ERROR`; `completed` con `failure` `null` → `OK` (depende de T018)
- [X] T020 [US3] Agregar a `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/e2e/WebServiceAuditIT.java`: `GET /players/999999` → 404 con el mismo cuerpo que verifica `e2e/PlayerControllerIT` (status, `message` "No se encontró el jugador con id 999999.") y una línea `| INFO |` con `user=anonymous`, `"id":999999`, `time=<n>ms` y `result=ERROR(PlayerNotFoundException)`, sin el mensaje (US3.1, US3.3); `POST /orders/buy` con token para un jugador creado con `QuoteDatabase` sin cotización (setup como `OrderControllerIT.unJugadorSinCotizacionResponde409`, con `MarketDatabase` y `clean()` en `@BeforeEach`/`@AfterEach`) → el mismo 409 con "El jugador no se puede operar: todavía no tiene cotización." y una línea con `result=ERROR(PlayerNotTradableException)` y `time=` presente (US3.2); `GET /players/0` → el mismo 400 de siempre y una línea con `"id":0` y `result=ERROR(ConstraintViolationException)` (US3.5); `POST /auth/login` con `{}` y con un JSON mal formado → el mismo 400 de siempre ("El request tiene datos inválidos." / "El cuerpo del request no es un JSON válido.") y **ninguna** línea de `AuthController.login` (US3.4, FR-003); `GET /players/abc` (no convertible a `Long`) → el mismo status y cuerpo que antes de la feature y ninguna línea; ninguna línea de auditoría de toda la clase tiene nivel distinto de `INFO` (FR-011) (depende de T013 y T018)

**Checkpoint**: los errores quedan registrados y ninguna respuesta cambió. `./gradlew test` en verde.

---

## Fase 6: User Story 4 - El registro se opera aparte del log general (Priority: P3)

**Goal**: la auditoría va a su propio archivo rotado (y a la consola), no aparece en el log general, sigue
escribiéndose con el nivel general subido y no se versiona.

**Independent Test**: con `./gradlew bootRun` se invoca un endpoint y la línea aparece en
`backend/logs/web-service-audit.log` y en la consola con el formato del contrato, no con el del log general; con
`--logging.level.root=WARN` se sigue escribiendo; `git check-ignore` confirma que el archivo está ignorado.

La configuración ya está hecha en la Fase 2 (T004, T005); esta fase la verifica.

### Tests for User Story 4

- [X] T021 [US4] Agregar a `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/e2e/WebServiceAuditIT.java` (inyectando `org.springframework.boot.logging.LoggingSystem` y restaurando los niveles en un `@AfterEach`, así no hace falta otro contexto): una invocación deja su línea **una sola vez** en la salida capturada y esa línea no tiene el formato del log general (no contiene el nombre del logger `WEB_SERVICE_AUDIT` ni el hilo `[`…`]`: `additivity="false"`, FR-022, US4.2); con `loggingSystem.setLogLevel(LoggingSystem.ROOT_LOGGER_NAME, LogLevel.WARN)` la invocación sigue dejando su línea (FR-026, SC-006, US4.3); con `loggingSystem.setLogLevel("WEB_SERVICE_AUDIT", LogLevel.OFF)` no deja ninguna (se apaga solo configurando el propio registro) (depende de T013)
- [X] T022 [US4] Verificación manual de quickstart §2 y §4 (no automatizable sin un perfil distinto de `test`): `cd backend && ./gradlew bootRun`; tras el primer request, `backend/logs/web-service-audit.log` (que se crea vacío al arrancar) tiene la misma línea que la consola (FR-012, FR-025, US4.1); la línea no aparece mezclada con el formato del log general; con `./gradlew bootRun --args='--logging.level.root=WARN'` la línea se sigue escribiendo; bajar temporalmente `WEB_SERVICE_AUDIT_MAX_FILE_SIZE` a `1KB` en `backend/src/main/resources/logback-spring.xml`, hacer varios requests, ver `logs/web-service-audit.<fecha>.0.log.gz` y **revertir** el cambio (US4.4); `git check-ignore -v backend/logs/web-service-audit.log` imprime la regla y `git status` no lista nada de `backend/logs/` (US4.5, SC-007). Anotar el resultado (y cualquier desvío) en la descripción del PR

**Checkpoint**: las cuatro historias completas.

---

## Fase 7: Polish & Cross-Cutting Concerns

**Propósito**: documentación, Sonar, definición de terminado e integración con la 010.

- [X] T023 [P] Agregar en `README.md` de la raíz una sección "Auditoría de web services": qué registra (los seis datos, una línea por invocación a un endpoint propio), el formato con un ejemplo del contrato, dónde está (`backend/logs/web-service-audit.log`, rotación diaria y por tamaño, 30 días, 10 MB por archivo, 1 GB en total, y la consola), qué no se audita (401/403 del filtro, 400 previos a la invocación, actuator, Swagger, H2, procesos programados), que los secretos van como `***`, cómo apagarla (`logging.level.WEB_SERVICE_AUDIT=OFF`) y en qué se diferencia de la auditoría del mercado (`GET /audit`, en base, de la 008). En español, sin referencias a máquinas ni a herramientas personales
- [ ] T024 Revisar el análisis de Sonar del código nuevo (SonarCloud del PR, o `./gradlew sonar` si el equipo lo corre local): menos de 10 issues en total. Si aparece `java:S112` sobre el `throws Throwable` de `WebServiceAuditAspect`, agregar la exclusión `e4` en el bloque `sonar` de `backend/build.gradle` con el patrón de `e1`–`e3` (sumar `e4` a `sonar.issue.ignore.multicriteria`, `ruleKey java:S112`, `resourceKey **/shared/WebServiceAuditAspect.java`) y un comentario `// e4:` en español que explique que el `throws` lo impone `ProceedingJoinPoint.proceed()` y que la excepción se relanza intacta (research D14). Cualquier otro issue se corrige en el código
- [X] T025 Correr el resto de quickstart.md: §3 completo con `curl` (los `grep -c "secreto-de-prueba"` dan 0; el 404, el 400 de `/players/0` y el 400 de `{}` responden igual que antes) y §5: `http://localhost:8080/swagger-ui.html` lista los mismos endpoints y esquemas que antes de la feature (los controllers ahora son proxies y springdoc los resuelve sobre la clase real)
- [X] T026 Definición de terminado: `cd backend && ./gradlew build` → `BUILD SUCCESSFUL`; `git diff --name-status main -- backend/src/test` muestra solo `A` para `shared/WebServiceAuditFormatterTest.java` y `e2e/WebServiceAuditIT.java` (SC-003); `git diff --name-status main -- backend/src/main` muestra solo las cuatro clases nuevas de `shared/`, `logback-spring.xml` y `security/CredentialAuthenticationFilter.java`; nada cambió en `controller/`, `service/`, `modelo/`, `persistence/`, `adapter/`, `config/` ni en `GlobalExceptionHandler` (FR-004, FR-020)
- [ ] T027 (No bloquea; después de integrar la rama de la 010 con ArchUnit) Correr `cd backend && ./gradlew test` **completo**: `LayerRulesTest`, `CodingRulesTest` y el resto pasan con las clases nuevas de `shared/` y con el import `security/` → `shared/CredentialType`. Si el merge de `backend/build.gradle` conflictúa, conservar las dos líneas (`spring-boot-starter-aspectj` y la de ArchUnit). Si una regla falla, corregir la producción de esta feature y anotar el caso en `specs/011-auditoria-web-services/research.md`
- [ ] T028 (No bloquea) Confirmar con el equipo los "Puntos a confirmar" de `specs/011-auditoria-web-services/plan.md`: que el compañero de la 010 sabe que `shared/` suma cuatro clases y que `security/` importa `shared/CredentialType`, y la retención por defecto (30 días, 10 MB, 1 GB). Si cambia algún valor, ajustarlo en `backend/src/main/resources/logback-spring.xml`, en el README (T023) y en `specs/011-auditoria-web-services/contracts/web-service-audit-line.md`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Fase 1)**: sin dependencias. T003 y T004 en paralelo después de T001 y T002.
- **Fundacional (Fase 2)**: depende de Setup. T005 → T006; T007 y T008 en paralelo con T005. **Bloquea todas las
  historias.**
- **US1 (Fase 3)**: depende de la Fase 2.
- **US2 (Fase 4)**: depende de US1 (amplía el formatter de T010 y los dos archivos de test de T012 y T013).
- **US3 (Fase 5)**: depende de US1 (T010, T011, T013). Es independiente de US2: se puede hacer antes o después,
  pero no a la vez, porque las dos tocan el formatter y los dos tests.
- **US4 (Fase 6)**: depende de la Fase 2 (la configuración) y de US1 (algo tiene que escribir líneas). T022 conviene
  hacerlo con US2 cerrada, para no dejar contraseñas en un archivo local.
- **Polish (Fase 7)**: depende de las historias que se quieran entregar. T027 y T028 no bloquean el PR.

### User Story Dependencies

- **US1 (P1)**: arranca después de la Fase 2. No depende de otras historias.
- **US2 (P1)**: arranca después de US1. Juntas forman el MVP.
- **US3 (P2)**: arranca después de US1. Independiente de US2 en comportamiento; secuencial por archivo.
- **US4 (P3)**: arranca después de US1. Solo agrega tests y verificación manual.

### Within Each User Story

- Producción primero, test de la misma tarea después (sin TDD); al cerrar la tarea el test existe y pasa.
- En US1: filtro (T009) y formatter (T010) en paralelo → aspecto (T011) → e2e (T013). El unitario (T012) solo
  necesita el formatter.
- Formatter antes que aspecto; aspecto antes que e2e.

### Parallel Opportunities

- Fase 1: T003 ∥ T004.
- Fase 2: T005 ∥ T007 ∥ T008.
- US1: T009 ∥ T010; después T011 ∥ T012.
- Polish: T023 ∥ T024.
- No hay paralelismo entre historias: US2, US3 y US4 tocan los mismos dos archivos de test (y US2 y US3 el mismo
  formatter).

---

## Parallel Example: User Story 1

```bash
# Después de la Fase 2, en paralelo (archivos distintos):
Task: "T009 setDetails(CredentialType) en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/security/CredentialAuthenticationFilter.java"
Task: "T010 WebServiceAuditFormatter en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/shared/WebServiceAuditFormatter.java"

# Con el formatter listo, en paralelo:
Task: "T011 WebServiceAuditAspect en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/shared/WebServiceAuditAspect.java"
Task: "T012 unitario en backend/src/test/java/ar/edu/unq/desapp/futbolmarket/shared/WebServiceAuditFormatterTest.java"
```

---

## Implementation Strategy

### MVP First (User Stories 1 + 2)

1. Fase 1: Setup (línea de base, dependencia, `.gitignore`).
2. Fase 2: Fundacional (`logback-spring.xml`, `CredentialType`, `WebServiceInvocation`).
3. Fase 3: US1 → cada invocación deja su línea.
4. Fase 4: US2 → secretos enmascarados. **No entregar la US1 sin la US2.**
5. **STOP and VALIDATE**: `./gradlew test` y quickstart §3.1 a §3.3.

### Incremental Delivery

1. Setup + Fundacional → configuración lista, suite en verde.
2. US1 + US2 → MVP (requerimiento core de la cátedra, sin secretos en claro).
3. US3 → los errores con su tipo y la verificación de que ninguna respuesta cambió.
4. US4 → la operación del registro verificada (archivo, rotación, nivel, git).
5. Polish → README, Sonar, quickstart completo, definición de terminado.

### Parallel Team Strategy

La feature es chica y casi todo pasa por dos archivos de producción y dos de test, así que conviene una sola
persona por fase. Lo único que se puede repartir es T009 (filtro, `security/`) frente a T010 (formatter, `shared/`)
y, al final, el README (T023) frente a Sonar (T024).

---

## Notes

- [P] = archivos distintos, sin dependencias pendientes.
- [US#] mapea cada tarea a su historia para la trazabilidad.
- Cada checkpoint es un punto para validar la historia por separado.
- Los commits los hace el equipo.
- Evitar: tocar tests existentes, `toString()` de los argumentos, `catch` genéricos, y cualquier cambio de
  respuesta de la API.
