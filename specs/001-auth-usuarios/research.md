# Investigación: Registro, credenciales y acceso autenticado

**Rama**: `001-auth-usuarios` | **Fecha**: 2026-09-18 | **Plan**: [plan.md](./plan.md)

Este documento resuelve las incógnitas técnicas del plan y registra cada decisión con su
justificación y las alternativas descartadas. No quedan marcadores NEEDS CLARIFICATION.

## Relevamiento del repositorio

Se leyeron los archivos existentes antes de decidir nada:

- `backend/build.gradle`: Spring Boot 4.1.1, `io.spring.dependency-management` 1.1.7, Java 21
  por toolchain y Gradle wrapper 9.7.1. Dependencias ya presentes: `spring-boot-h2console`,
  `spring-boot-starter-actuator`, `spring-boot-starter-data-jpa`,
  `spring-boot-starter-validation`, `spring-boot-starter-webmvc`, Lombok y H2. En test:
  `spring-boot-starter-actuator-test`, `spring-boot-starter-data-jpa-test`,
  `spring-boot-starter-validation-test` y `spring-boot-starter-webmvc-test`.
- Archivos de configuración reales (se verificaron los nombres, porque las extensiones
  difieren):
  - `src/main/resources/application.yaml` (base, con extensión `.yaml`): define
    `spring.profiles.default: local`, `spring.jpa.open-in-view: false` y expone por web los
    endpoints `health` e `info` de actuator.
  - `src/main/resources/application-local.yml`: `jdbc:h2:file:./data/futbolmarket;AUTO_SERVER=TRUE`,
    `ddl-auto: update` (ya cumple el requisito de que los datos sobrevivan al reinicio) y la
    consola de H2 habilitada en `/h2-console`.
  - `src/test/resources/application-test.yml`: `jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1`,
    `ddl-auto: create-drop` y la consola de H2 deshabilitada.
- `FutbolMarketApplication` en `ar.edu.unq.desapp.futbolmarket`, `lombok.config`
  (`stopBubbling`, `addLombokGeneratedAnnotation`) y `FutbolMarketApplicationTests` con
  `@ActiveProfiles("test")`.
- Paquetes vacíos con `.gitkeep`: `auth/`, `catalog/`, `config/`, `domain/`, `security/` y
  `shared/`. El paquete `domain/` es inválido según la constitución y se elimina.
- Rama paralela `origin/002-catalogo-jugadores`, que por ahora solo contiene documentación.
  Expone `GET /players` y `GET /players/{id}`, que son públicos. Su contrato define el formato
  de error común `ApiError`: `timestamp`, `status`, `error`, `message`, `path` y un
  `violations[]` opcional. Deja a `shared/` el advice y las excepciones transversales.

---

## D1. Dependencias nuevas y versiones

**Decisión**: agregar exactamente estas dependencias. El usuario confirma antes de que se
toque `build.gradle`.

| Configuración | Artefacto | Versión |
|---|---|---|
| `implementation` | `org.springframework.boot:spring-boot-starter-security` | gestionada por Boot 4.1.1 (Spring Security 7.1) |
| `implementation` | `io.jsonwebtoken:jjwt-api` | `0.13.0` |
| `runtimeOnly` | `io.jsonwebtoken:jjwt-impl` | `0.13.0` |
| `runtimeOnly` | `io.jsonwebtoken:jjwt-jackson` | `0.13.0` |
| `implementation` | `org.springdoc:springdoc-openapi-starter-webmvc-ui` | `3.1.1` |

No se agregan:

- `spring-boot-starter-validation` ni `spring-boot-starter-actuator`, porque ya están.
- `spring-boot-starter-security-test`. Los tests end to end usan credenciales reales (se
  registra un usuario y se usa su clave o su token), así que no hace falta `@WithMockUser`.
  `@AutoConfigureMockMvc` ya aplica la cadena de filtros de seguridad.
- Ninguna otra dependencia.

**Justificación**:

- springdoc 2.x es la línea de Spring Boot 3 y no sirve acá. La línea 3.x es la de Boot 4: la
  3.1.0 migró a Spring Boot 4.1.0 y la 3.1.1 es la última estable, publicada en Maven Central
  el 2026-09-06.
- jjwt 0.13.0 es la última versión publicada (2025-08-20). jjwt no está en el BOM de Spring
  Boot, así que la versión va explícita. Lo mismo vale para springdoc.

**Alternativas descartadas**:

- springdoc 2.8.x: es para Boot 3.
- springdoc 3.0.x: es para Boot 4.0.
- `jjwt-gson` y `jjwt-orgjson`: el pedido fue `jjwt-jackson`.
- OAuth2 Resource Server con Nimbus para validar el JWT: suma dependencias que nadie pidió.

## D2. jjwt-jackson y la convivencia de Jackson 2 con Jackson 3

**Hallazgo**: Spring Boot 4 serializa con Jackson 3 (`tools.jackson`). jjwt-jackson 0.13.0
depende de Jackson 2 (`com.fasterxml.jackson`) y todavía no hay un adaptador de jjwt para
Jackson 3 (el pedido está en jwtk/jjwt#1029). Boot 4 sigue gestionando las versiones de
Jackson 2, y las dos líneas conviven sin conflicto porque usan paquetes distintos. Además,
springdoc 3 ya trae Jackson 2 de forma transitiva a través de swagger-core.

**Decisión**: se mantiene `jjwt-jackson` tal como se pidió. jjwt usa su propio mapper interno
para los claims, así que no afecta el JSON de la API, que sigue en Jackson 3.

**Riesgo**: bajo. Si en el futuro se quitara Jackson 2 del classpath, habría que cambiar al
adaptador de Jackson 3 cuando exista.

## D3. Token de sesión (JWT)

**Decisión**:

- La firma usa HS256 declarado explícitamente con `Jwts.SIG.HS256`. Sin algoritmo explícito,
  jjwt elige según el tamaño de la clave: con un secreto de 64 bytes firmaría con HS512.
- Claims:
  - `sub`: el id del usuario, como texto.
  - `iat`: la fecha de emisión.
  - `exp`: `iat` más la duración configurada, que por defecto es 24 horas (FR-016).

  No se agregan más claims. El rol no viaja en el token: el filtro lee el usuario de la base
  en cada request y así detecta usuarios inexistentes (FR-020).
- El secreto se configura en Base64 y tiene que tener al menos 256 bits.
  `Keys.hmacShaKeyFor` rechaza claves más cortas con `WeakKeyException` durante el arranque,
  así que un secreto débil corta el inicio de la aplicación.
- La hora sale de un `java.time.Clock` inyectado, tanto al emitir como al validar (con
  `JwtParserBuilder.clock`). El margen de reloj es cero, porque el spec pide rechazar el token
  apenas pasado su vencimiento. El `Clock` permite testear el vencimiento sin esperar.
- Al validar se distinguen dos casos:
  - `ExpiredJwtException` se informa como credencial vencida.
  - Cualquier otra `JwtException`, o un token vacío, se informa como credencial inválida.
- No hay lista negra ni versionado de tokens. Un token vale hasta su vencimiento aunque el
  usuario cambie la contraseña o regenere la clave (FR-035).
- `JwtService` vive en `security/` e implementa el puerto de modelo `SessionTokenIssuer`. Así
  `auth/service` emite tokens sin depender de `security/`, y se evita un ciclo entre los
  paquetes `auth` y `security`, que el control de ArchUnit de la entrega 3 marcaría.

**Alternativas descartadas**:

- Usar el username como `sub`: el id es inmutable y se busca por clave primaria.
- Validar el token solo con la firma, sin leer la base: no detectaría usuarios inexistentes.

## D4. Clave de API

**Decisión**:

- **Generación**: 32 bytes de `SecureRandom` codificados en Base64URL sin padding. Resultan 43
  caracteres, seguros para usar en un header.
- **Persistencia**: se guarda el hash SHA-256 de la clave en hexadecimal (64 caracteres),
  nunca la clave. La búsqueda es por igualdad exacta sobre una columna con índice único.
- **Por qué SHA-256 y no BCrypt**: BCrypt usa sal y produce un hash distinto en cada
  invocación, así que no permite buscar al usuario por su clave. SHA-256 es determinístico. Es
  seguro en este caso porque la clave tiene 256 bits de entropía y la fuerza bruta es
  inviable. Con una contraseña elegida por una persona no alcanzaría.
- **Ubicación**: la generación y el hash viven en el modelo, en el objeto de valor `ApiKey`.
  Solo usa JDK (`SecureRandom`, `MessageDigest`, `HexFormat`), así que no hace falta un puerto
  y el modelo no importa Spring. El filtro calcula el hash a través del servicio, que usa
  `ApiKey.hashOf`.
- **Una sola clave vigente por usuario**: emitir una clave pisa el hash anterior, así que la
  clave vieja deja de funcionar en ese mismo momento (FR-033 y FR-034).
- **Columna nullable**: el administrador nace sin clave (FR-041). El índice único admite
  varios `NULL` tanto en H2 como en PostgreSQL.
- El `toString` de `ApiKey` no muestra el valor en claro.

## D5. Contraseñas

**Decisión**:

- El puerto `PasswordHasher` vive en `auth/modelo/`, en Java puro, con dos operaciones:
  `hash(textoPlano)` y `matches(textoPlano, hash)`. El adaptador `BCryptPasswordHasher` vive en
  `security/` y envuelve al bean `PasswordEncoder` (`BCryptPasswordEncoder`, costo 10 por
  defecto).
- **Límite de 72 bytes de BCrypt**: desde el fix de CVE-2025-22228, `encode()` lanza
  `IllegalArgumentException` si la contraseña supera los 72 bytes. Para devolver 400 en lugar
  de 500:
  - El DTO acota la contraseña con `@Size(min = 8, max = 72)`, que cuenta caracteres.
  - El modelo verifica además que no supere los 72 bytes en UTF-8, lo que cubre a las
    contraseñas con caracteres multibyte.

  El spec solo fija el mínimo de 8. El máximo es un límite técnico y hay que registrarlo como
  supuesto en el spec.
- **Tiempo de respuesta en el login**: si el usuario no existe, el servicio igual hace una
  verificación BCrypt contra un hash descartable calculado una sola vez. Así el tiempo de
  respuesta no revela si el usuario existe (SC-007). Es la misma técnica que usa
  `DaoAuthenticationProvider`.
- La contraseña nunca aparece en respuestas ni en logs. Ver D13 para el `rejectedValue` del
  advice.

## D6. Filtro único de autenticación

**Decisión**:

- Es un `OncePerRequestFilter` en `security/` que construye `SecurityConfig`. No se declara
  como `@Component`: Spring Boot registra cualquier bean `Filter` como filtro del servlet, y
  el filtro correría además fuera de la cadena de seguridad.
- `shouldNotFilter` usa el mismo `RequestMatcher` de rutas públicas que la autorización, así
  que el filtro no se ejecuta sobre rutas públicas. Por eso una credencial inválida no bloquea
  un recurso público, como pide el spec en sus casos borde.
- **Precedencia**, decidida por el usuario el 2026-09-18:
  1. Si llega `Authorization: Bearer <token>`, se evalúa solo el JWT y se ignora `X-API-Key`,
     aunque la clave sea válida.
  2. Si no, y `X-API-Key` no está vacía, se evalúa la clave.
  3. Si no hay ninguna de las dos, el request sigue como anónimo y el entry point responde
     que falta la credencial.

  Un `Authorization` con otro esquema, por ejemplo Basic, no es una credencial de este
  sistema y se ignora.
- **Credencial válida**: se carga el usuario a través de `AuthService`, por id si vino un JWT
  o por hash si vino una clave. Si el usuario no existe, la credencial es inválida. El
  `Authentication` que se arma tiene:
  - principal: el id del usuario, como `Long`;
  - autoridades: `ROLE_USER` o `ROLE_ADMIN`.

  Los controllers lo reciben con `@AuthenticationPrincipal Long userId`. Así no importan
  tipos de `security/` y no se crea una dependencia de `auth` hacia `security`.
- **Credencial inválida o vencida**: el filtro invoca directamente al entry point con la
  `AuthenticationException` de Spring Security que corresponde y corta la cadena:
  - `BadCredentialsException` si la credencial es inválida;
  - `CredentialsExpiredException` si el token está vencido.

## D7. SecurityConfig

**Decisión**:

- Se escribe con el DSL de lambdas. Spring Security 7 eliminó los encadenamientos con `and()`.
- Se deshabilitan `csrf`, `httpBasic`, `formLogin` y `logout`, y la sesión es `STATELESS`. No
  queda ninguna página HTML de login.
- `exceptionHandling` usa el entry point y el access denied handler propios (D8).
- **Orden de la autorización**:
  1. `dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()`.
  2. Las rutas públicas.
  3. `anyRequest().authenticated()`.

  La documentación de Spring Security 7 recomienda permitir el dispatch `ERROR` para que
  Spring Boot pueda renderizar errores. Si no se permite, un error en una ruta pública (por
  ejemplo un 400 de Bean Validation antes de integrar `shared/`, o un 404) sale disfrazado de
  401. No es una ruta pública: pedir `/error` directamente sigue exigiendo credencial.
- **Rutas públicas**, declaradas una por una en un único `RequestMatcher` definido en
  `SecurityConfig`, que comparte el filtro:
  - `POST /auth/register`
  - `POST /auth/login`
  - `/swagger-ui/**`, `/swagger-ui.html` y `/v3/api-docs/**`
  - `GET /actuator/health`. Actuator está en `build.gradle`.
  - `GET /players` y `GET /players/**`, en una sola línea con el comentario "TEMPORAL: la
    protección de estos endpoints se activa en un PR posterior, una vez mergeadas las dos
    features". Activarla es borrar esa línea. Se restringe a `GET` porque FR-023 acota la
    excepción a los recursos de consulta: un `POST /players` futuro nace protegido.
  - No se usa `/auth/**`: dejaría abiertos `/auth/me` y sus subrutas.
- **Consola de H2**: un segundo `SecurityFilterChain`, con `@Profile("local")` y precedencia
  mayor que la cadena principal. Usa `securityMatcher("/h2-console/**")`, permite todo, sin
  CSRF, y configura `headers.frameOptions(sameOrigin)` porque la consola usa frames. Esta
  cadena no existe en la configuración por defecto ni en test.
- **Matchers en Spring Security 7**: `requestMatchers(String...)` usa `PathPatternRequestMatcher`
  y trabaja con rutas absolutas. Ya no aparece el error de Spring Security 6 "cannot decide
  whether these patterns are Spring MVC patterns" que provocaba el servlet de la consola de
  H2. Se verifica al arrancar la implementación.
- **Usuario en memoria de Spring Boot**: se excluye
  `org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration` (el
  nombre completo en Boot 4.1) con `spring.autoconfigure.exclude`. Sin eso, Boot crea un
  usuario en memoria y deja su contraseña generada en el log. Ese usuario no sirve para nada,
  porque no hay basic ni form login, pero confunde.
- El bean `PasswordEncoder` se declara acá.

## D8. Formato JSON de los 401 y 403

**Decisión**:

- Los rechazos de la cadena de filtros ocurren antes de llegar a un controller y el
  `@RestControllerAdvice` de `shared/` no los ve. Por eso hay un `AuthenticationEntryPoint` y
  un `AccessDeniedHandler` propios en `security/`. Los dos usan un único escritor,
  `ApiErrorResponseWriter`, que produce el mismo `ApiError` del contrato común:
  `timestamp`, `status`, `error` (la frase del status HTTP), `message` y `path`.
- Mensajes, en español:

  | Motivo | Status | `message` |
  |---|---|---|
  | Falta la credencial | 401 | `Se requiere una credencial: un token de sesión (Authorization: Bearer) o una clave de API (X-API-Key).` |
  | Credencial inválida (firma, formato, usuario inexistente, clave desconocida) | 401 | `La credencial es inválida.` |
  | Token vencido | 401 | `El token de sesión está vencido.` |
  | Sin permisos | 403 | `No tiene permisos para acceder a este recurso.` |

  Ningún mensaje revela datos de otros usuarios ni cuál parte de la credencial falló (FR-021).
- Se responde con `Content-Type: application/json` en UTF-8 y se serializa con el
  `JsonMapper` de Boot (Jackson 3), el mismo que usa el advice. Así el formato de `timestamp`
  coincide.
- Antes de la integración, el escritor usa un record privado con la misma forma. Después pasa
  a usar el `ApiError` de `shared/` (D15).

## D9. Unicidad de username y correo sin distinguir mayúsculas

**Tensión**: el spec pide comparar sin distinguir mayúsculas y conservar el valor tal como lo
escribió el usuario. El input pide índices únicos sobre `username`, `email` y `api_key_hash`.

**Decisión**:

- El servicio consulta la disponibilidad de los dos campos con `existsBy...IgnoreCase`. Pregunta
  por los dos para poder informar ambos conflictos (FR-007).
- Los índices únicos sobre las columnas tal cual son la última línea de defensa ante dos
  registros idénticos simultáneos, que es el caso borde del spec.
- El repository traduce la violación del índice a la excepción de dominio (D10).

**Limitación aceptada**:

- Dos registros simultáneos que solo difieren en mayúsculas (por ejemplo `Lionel10` y
  `lionel10`) podrían pasar ambos.
- Las búsquedas que ignoran mayúsculas (registro y login) comparan `upper(columna)` y no
  aprovechan el índice. Con el volumen del TP es irrelevante.
- La búsqueda por hash de la clave de API, que es la que corre en cada request, sí usa el
  índice. La búsqueda por id del JWT usa la clave primaria.
- Si algún día hiciera falta, la evolución es una columna normalizada en minúsculas con el
  índice único. No se hace ahora porque agrega campos al modelo acordado.

**Alternativas descartadas**:

- Guardar en minúsculas: contradice el supuesto del spec de conservar el valor original.
- `IGNORECASE=TRUE` en la URL de H2: es específico de H2 y cambiaría la comparación de todas
  las columnas de texto, incluidas las del catálogo. Viola el principio VI.
- `columnDefinition = "VARCHAR_IGNORECASE"`: no es portable.

## D10. Índices y traducción de violaciones de unicidad

**Decisión**:

- `AppUserSQL` declara los índices explícitamente con
  `@Table(name = "app_user", indexes = @Index(..., unique = true))`, con nombres fijos:
  `ux_app_user_username`, `ux_app_user_email` y `ux_app_user_api_key_hash`. Un índice único
  garantiza la unicidad en H2 y en PostgreSQL, y es el índice declarado explícitamente que se
  pidió.
- El repository persiste con `saveAndFlush`, para que la violación aparezca dentro de su
  método. Captura `DataIntegrityViolationException` y reconoce el índice por su nombre en la
  causa más específica, sin distinguir mayúsculas porque H2 informa los nombres en mayúsculas:
  - `ux_app_user_username` → `DuplicateUsernameException`.
  - `ux_app_user_email` → `DuplicateEmailException`.
  - Cualquier otro caso se relanza sin tocar. Una colisión de hash de clave de API es
    imposible en la práctica.
- Después del fallo no se vuelve a consultar la base: la sesión de Hibernate queda inutilizable
  tras un flush fallido.
- Solo se usan derived queries: `findByUsernameIgnoreCase`, `existsByUsernameIgnoreCase`,
  `existsByEmailIgnoreCase` y `findByApiKeyHash`. No hay SQL nativo.

## D11. Validación en cada nivel

**Decisión**:

- **DTO** (records en `auth/controller/dto/`): Bean Validation con mensajes en español. El
  username y el correo se recortan en el constructor compacto del record, antes de validar y
  de comparar. La contraseña nunca se recorta.

  | Campo | Reglas |
  |---|---|
  | username | `@NotBlank`, `@Size(min = 3, max = 30)`, `@Pattern(^[A-Za-z0-9_]+$)`. Solo ASCII, así que los acentos, espacios y guiones medios quedan afuera. |
  | email | `@NotBlank`, `@Email(regexp = ...)`, `@Size(max = 254)` |
  | password (registro y nueva contraseña) | `@NotBlank`, `@Size(min = 8, max = 72)`. Es la misma regla en los dos lugares (FR-029). |

  Los límites son constantes nombradas de `CredentialPolicy`, en el modelo, que es la única
  fuente de verdad.
- `RegisterRequest` lleva `@JsonIgnoreProperties(ignoreUnknown = true)`. Si el cliente manda
  `"role": "ADMIN"`, se ignora y la cuenta se crea con rol de usuario común (escenario 6 de la
  HU1). El comportamiento es explícito y no depende de la configuración global de Jackson.
- **Servicio**: verifica la disponibilidad de username y correo, y la existencia del usuario
  por username o id.
- **Modelo**:
  - Invariantes de negocio: el rol lo asigna la fábrica, el saldo es no negativo con escala 2,
    y el cambio de contraseña tiene sus reglas.
  - Además, las invariantes de formato de las credenciales (username, correo y contraseña,
    incluido el límite en bytes). El canal de alta del administrador (FR-038) no pasa por un
    DTO. Ver Seguimiento de complejidad en el plan.
- **Doble conflicto**: el objeto de valor `RegistrationAvailability` decide qué excepción
  lanzar:
  - Solo el username tomado: `DuplicateUsernameException`.
  - Solo el correo tomado: `DuplicateEmailException`.
  - Los dos tomados: `DuplicateUsernameAndEmailException`. Es una tercera excepción, que el
    pedido admite porque lista "como mínimo" cuatro.

  La decisión queda en el modelo y el servicio no tiene un `if` de negocio.

## D12. Alta del administrador (HU7, incluida por decisión del 2026-09-18)

**Decisión**:

- `AdminAccountInitializer` vive en `auth/service/` e implementa `ApplicationRunner`. Corre en
  todos los perfiles; lo que la habilita es solo que la configuración esté completa.
- Las credenciales salen de `futbolmarket.auth.admin.username`, `.email` y `.password`, y se
  resuelven únicamente desde variables de entorno (`FUTBOLMARKET_AUTH_ADMIN_USERNAME`,
  `FUTBOLMARKET_AUTH_ADMIN_EMAIL` y `FUTBOLMARKET_AUTH_ADMIN_PASSWORD`). Ningún archivo del
  repositorio las define, ni siquiera con un valor vacío (FR-037 y SC-013).
- Flujo:
  1. Si falta la contraseña: advertencia y se omite el alta (FR-038).
  2. Si falta el username o el correo: advertencia y se omite.
  3. Si el username ya existe, sin distinguir mayúsculas: se registra en nivel INFO y no se
     toca nada, aunque sea un usuario común (FR-039, escenario 4).
  4. Si el correo ya está tomado: advertencia y se omite.
  5. `AppUser.createAdmin(...)`. Si una invariante falla: advertencia con la regla
     incumplida, nunca con la contraseña, y se omite.
  6. Se guarda y se registra en nivel INFO. El administrador queda con rol `ADMIN`, saldo
     `0.00`, sin clave de API y con la contraseña en BCrypt (FR-040 a FR-042).
- El inicializador no es `@Transactional`. Si capturara una excepción de dominio dentro de
  una transacción marcada rollback-only, terminaría en `UnexpectedRollbackException`. Cada
  operación del repository usa su propia transacción.
- Si una carrera con un registro público termina en violación de unicidad, se captura la
  excepción `Duplicate*` y se advierte.
- Todos los logs usan SLF4J y ninguno contiene la contraseña.

## D13. Contrato que auth necesita del advice de shared/

**Decisión**: el detalle está en [contracts/shared-integration.md](./contracts/shared-integration.md).
Resumen:

- **Formato**: el `ApiError` del contrato de catálogo.
- **Clase base de excepciones**: tiene que poder extenderse desde `auth/modelo/` sin importar
  `org.springframework`. Es decir, su API pública no puede pedir `HttpStatus`.
- **Mapeo**:
  - 409 para las tres excepciones de conflicto.
  - 401 para `InvalidCredentialsException`.
  - 400 para `InvalidPasswordChangeException` e `InvalidUserDataException`.
- **Validación**: `MethodArgumentNotValidException` se responde con 400 y `violations`.
- **Crítico**: el advice no incluye `rejectedValue` en campos sensibles (`password`,
  `currentPassword`, `newPassword`), o directamente no lo incluye nunca. Si lo hiciera,
  devolvería la contraseña en un 400 y violaría FR-009 y SC-003.

## D14. Configuración y perfiles

**Decisión**: el detalle está en [contracts/configuration.md](./contracts/configuration.md).

- **`application.yaml` (base)**, en un bloque nuevo `futbolmarket`:
  - `auth.initial-balance: 1000.00`, que es el supuesto del spec para FR-012.
  - `security.jwt.expiration: 24h`.
  - La exclusión del usuario en memoria (D7).
- **`application-local.yml`**: `futbolmarket.security.jwt.secret` con un secreto de
  desarrollo en Base64 de al menos 256 bits.
- **`application-test.yml`**: otro secreto propio de test.
- La base no tiene secreto. Cualquier perfil que no sea `local` ni `test` exige la variable
  `FUTBOLMARKET_SECURITY_JWT_SECRET`. Si falta, `@Validated` y `@NotBlank` cortan el arranque.
- Los records de propiedades se validan al arrancar, salvo el bloque del administrador. Una
  configuración de admin inválida no puede impedir que la aplicación levante (FR-038), así
  que la valida el inicializador.
- `ddl-auto: update` en local ya estaba configurado y no cambia.

## D15. Secuencia de integración con shared/ (decisión del 2026-09-18: integrar al final)

**Decisión**:

- **Antes de la integración**:
  - Las excepciones de auth extienden `RuntimeException`.
  - El escritor de errores de seguridad usa su propio record con la forma de `ApiError`.
  - Los `@ApiResponse` de error no declaran un esquema de cuerpo.

  Se implementa y testea todo lo que no depende de `shared/`: modelo, persistencia,
  servicios, seguridad, controllers, OpenAPI y los end to end de los caminos felices y de la
  cadena de filtros.
- **Integración**, una vez mergeado catálogo en `develop`:
  1. Rebase sobre `develop`.
  2. Las excepciones pasan a extender la base de `shared/`.
  3. El escritor usa el `ApiError` de `shared/`.
  4. Los `@ApiResponse` referencian `ApiError`.
  5. Se escriben los end to end de errores de dominio: 400, 409 y el 401 del login.
- La constitución prohíbe modificar un test existente sin permiso. Por eso los tests de
  errores de dominio se escriben una sola vez, en la integración, con todas sus aserciones, y
  no se escribe antes una versión parcial para completarla después. Así `./gradlew build`
  queda en verde en todo momento.

## D16. OpenAPI y Swagger UI

**Decisión**:

- `OpenApiConfig` vive en `config/` y declara un bean `OpenAPI` con `info` en español y dos
  security schemes en `components`, cuyos nombres son constantes públicas:
  - `bearerAuth`: esquema HTTP `bearer` con `bearerFormat: JWT`.
  - `apiKeyAuth`: esquema `apiKey` en el header `X-API-Key`.
- No hay un requirement global. Así los endpoints públicos (registro, login y el `/players` de
  la otra feature) se muestran sin candado. `AccountController` declara a nivel de clase los
  dos requirements como alternativas, lo que en OpenAPI significa que alcanza con cualquiera
  de los dos. El botón Authorize acepta los dos esquemas y permite ejecutar los endpoints
  protegidos con cualquiera. La otra feature solo agrega anotaciones en sus controllers cuando
  proteja `/players`.
- Cada endpoint lleva `@Operation` (resumen y descripción en español) y un `@ApiResponse` por
  cada código posible.
- El parámetro `@AuthenticationPrincipal` no aparece en la documentación. Si springdoc lo
  mostrara, se oculta con `@Parameter(hidden = true)`.

## D17. Estrategia de tests

**Decisión**:

- **Unitarios de modelo**, sin Spring: `AppUserTest`, `ApiKeyTest` y
  `RegistrationAvailabilityTest`. Usan un `FakePasswordHasher` de test en lugar de BCrypt.
- **Unitarios de servicio**, con Mockito: `AuthServiceTest`, `AccountServiceTest` y
  `AdminAccountInitializerTest`. Las advertencias se verifican con `OutputCaptureExtension`.
- **Unitarios de seguridad**, sin Spring: `JwtServiceTest` (con un `Clock` fijo) y
  `JsonAccessDeniedHandlerTest`. El 403 no se puede provocar por HTTP en esta feature, porque
  ninguna ruta distingue por rol.
- **Integración**:
  - `AppUserRepositoryIT` usa `@DataJpaTest`, `@ActiveProfiles("test")` e `@Import` del
    repository y del mapper. Trabaja sobre una H2 en memoria embebida con nombre único y
    verifica que los tres índices únicos rechacen duplicados.
  - `AdminAccountInitializerIT` usa `@SpringBootTest` y `@ActiveProfiles("test")`. Genera
    credenciales de admin aleatorias y una URL de H2 propia con `@DynamicPropertySource`.
- **End to end**, solo en el paquete `e2e/`: `@SpringBootTest`, `@AutoConfigureMockMvc`
  (`org.springframework.boot.webmvc.test.autoconfigure`), `@ActiveProfiles("test")` y
  `MockMvcTester` para escribir las aserciones con AssertJ.
  - El helper `AuthTestHelper` registra un usuario con datos únicos y devuelve sus
    credenciales: username, correo, contraseña, clave de API y, si se pide, un token.
  - Los tests no son `@Transactional`, para ejercitar commits reales. Cada test usa datos
    únicos, así que ninguno depende del orden ni comparte estado.
- **Aislamiento entre contextos**: el contexto con administrador configurado usa otra URL de
  H2 en memoria. Con `create-drop` sobre la misma `testdb`, cada contexto nuevo borraría las
  tablas de los contextos cacheados y dejaría un administrador visible para otros tests.
- **Nombres**: `*Test` para los unitarios e `*IT` para los de integración y los end to end.
  Gradle ejecuta todas las clases de test sin filtrar por nombre, así que los `*IT` corren con
  `./gradlew test`.
- Las aserciones se escriben con AssertJ, y hay casos felices y casos borde, como pide la
  constitución.
- `FutbolMarketApplicationTests` no se modifica.

## D18. Saldo de créditos

**Decisión**:

- Es un `BigDecimal`, nunca `double` ni `float`. Se persiste como `DECIMAL(19,2)`, con
  precisión y escala explícitas en `AppUserSQL`, y el modelo lo normaliza a escala 2.
- El saldo inicial sale de `futbolmarket.auth.initial-balance`, validado con
  `@PositiveOrZero` y `@Digits(integer = 17, fraction = 2)`. Un valor inválido corta el
  arranque, porque un saldo mal configurado no admite un default silencioso.
- `AppUser` no tiene setter del saldo. Solo lo fijan sus fábricas:
  - `register` asigna el saldo configurado.
  - `createAdmin` asigna cero.
  - `reconstitute` reconstruye el estado leído de la base y lo usa únicamente el mapper.

  Las operaciones de débito y crédito de la entrega 2 van a ser métodos de `AppUser`.

---

## Fuentes consultadas

- Metadata de Maven Central para `springdoc-openapi-starter-webmvc-ui` (última: 3.1.1) y
  `jjwt-api` (última: 0.13.0).
- [Releases de springdoc-openapi](https://github.com/springdoc/springdoc-openapi/releases): la
  3.1.0 migra a Spring Boot 4.1.0.
- [FAQ de springdoc](https://springdoc.org/faq.html): la línea 3.x es compatible con Spring
  Boot 4.
- [Introducing Jackson 3 support in Spring](https://spring.io/blog/2025/10/07/introducing-jackson-3-support-in-spring/):
  gestión de dependencias de Jackson 2 y 3, y su convivencia.
- [jwtk/jjwt#1029](https://github.com/jwtk/jjwt/issues/1029): el adaptador para Jackson 3
  sigue pendiente.
- [Spring Security: Authorize HttpServletRequests](https://docs.spring.io/spring-security/reference/servlet/authorization/authorize-http-requests.html):
  `PathPatternRequestMatcher` por defecto y el dispatch `ERROR` permitido.
- [Spring Boot: Spring Security](https://docs.spring.io/spring-boot/reference/web/spring-security.html):
  nombre completo de `UserDetailsServiceAutoConfiguration` en Boot 4.1 y condiciones en que se
  desactiva.
- [Spring Boot: Testing Spring Boot Applications](https://docs.spring.io/spring-boot/reference/testing/spring-boot-applications.html):
  `@AutoConfigureMockMvc` en `spring-boot-webmvc-test` y `MockMvcTester`.
- [CVE-2025-22228](https://spring.io/security/cve-2025-22228/) y
  [spring-security#18133](https://github.com/spring-projects/spring-security/issues/18133):
  límite de 72 bytes de BCrypt.
