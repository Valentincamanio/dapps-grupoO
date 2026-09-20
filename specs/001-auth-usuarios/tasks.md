---

description: "Lista de tareas de la feature 001-auth-usuarios"
---

# Tareas: Registro, credenciales y acceso autenticado

**Entrada**: documentos de diseño en `/specs/001-auth-usuarios/`

**Prerrequisitos**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md),
[data-model.md](./data-model.md), [contracts/](./contracts/), [quickstart.md](./quickstart.md) y
la constitución (`.specify/memory/constitution.md`).

**Tests**: se incluyen, porque la constitución (principio IV, NO NEGOCIABLE) y la definición de
terminado los exigen. **No se trabaja con TDD**: en cada fase los tests se escriben después de
la implementación de esa fase, y al cerrar la fase existen y pasan.

**Organización**: las tareas se agrupan por historia de usuario (HU1 a HU7 del spec = US1 a
US7) para implementar y probar cada una por separado.

## Formato: `[ID] [P?] [Story] Descripción`

- **[P]**: se puede hacer en paralelo (otro archivo, sin depender de tareas incompletas).
- **[Story]**: historia a la que pertenece la tarea (US1 a US7).
- Cada descripción incluye la ruta exacta del archivo.

## Convenciones de rutas y reglas para quien implemente

- Código de producción: `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/`.
- Tests: `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/`.
- Recursos: `backend/src/main/resources/` y `backend/src/test/resources/`.
- Todos los comandos de Gradle se corren desde `backend/`.
- Identificadores en inglés; mensajes de la API, comentarios y Javadoc en español (principio VII).
- Inyección por constructor (`@RequiredArgsConstructor` y campos `private final`), SLF4J para
  logs, constantes con nombre, sin `catch (Exception e)` y sin `// TODO` (principio V).
- **Nada de `auth/modelo/` importa `org.springframework`, `jakarta.persistence` ni el paquete
  `controller`** (principio I).
- **Las excepciones de dominio viven en `auth/modelo/exception/`**, no sueltas en `auth/modelo/`
  (es un desvío del árbol de paquetes del principio I, pendiente de una enmienda acordada con el
  equipo). El resto del modelo (`AppUser`, `CredentialPolicy`, los enums, los records y los
  puertos) queda directamente en `auth/modelo/`.
- **`AppUserRepository` se documenta como la traducción en la frontera con el mapper**, que es lo
  que mantiene al servicio ignorante de JPA. No se lo describe como el único punto donde conviven
  el modelo y la persistencia.
- Cuando un paquete deja de estar vacío, su `.gitkeep` se borra en el mismo commit.
- No se tocan `catalog/` ni `shared/`, no se modifica `FutbolMarketApplicationTests` y no se crean
  endpoints bajo `/users`.
- **Tests existentes**: varias historias agregan métodos `@Test` nuevos a una clase de test que
  creó una historia anterior (por ejemplo `AppUserTest`). Agregar métodos está permitido;
  modificar o borrar un método `@Test` ya escrito requiere el "sí" explícito del equipo
  (principio IV). Un test que falla se arregla arreglando el código.
- Tests: AssertJ (`assertThat`), nombres de método que describen el comportamiento en español
  (por ejemplo `rechazaUnNombreDeUsuarioDeDosCaracteres`), `*Test` para unitarios e `*IT` para
  integración y end to end, `@ActiveProfiles("test")` en todo lo que levante Spring. Los end to end
  usan `MockMvcTester`, no son `@Transactional` y cada test usa datos únicos.
- **Hasta la integración con `shared/` (Fase 10)**: las excepciones de dominio extienden
  `RuntimeException` y no hay advice, así que un error de dominio (400, 409 o el 401 del login)
  todavía no tiene su status definitivo. Ningún test anterior a la Fase 10 verifica esos casos
  por HTTP: se cubren con unitarios y, en la Fase 10, con `AuthErrorsIT` y `AccountErrorsIT`.

---

## Fase 1: Preparación (infraestructura compartida)

**Propósito**: dependencias, limpieza del árbol de paquetes y configuración.

- [X] T001 Mostrarle al usuario el diff de `backend/build.gradle` con exactamente las cinco dependencias de plan.md ("Dependencias a agregar": `spring-boot-starter-security`, `jjwt-api` 0.13.0, `jjwt-impl` 0.13.0 como `runtimeOnly`, `jjwt-jackson` 0.13.0 como `runtimeOnly` y `springdoc-openapi-starter-webmvc-ui` 3.1.1), **esperar su confirmación explícita** y recién entonces agregarlas en backend/build.gradle, sin reemplazar el archivo ni agregar `spring-boot-starter-security-test` ni ninguna otra
- [X] T002 [P] Eliminar backend/src/main/java/ar/edu/unq/desapp/futbolmarket/domain/.gitkeep (paquete inválido según el principio I; `domain/` desaparece del árbol)
- [X] T003 [P] En backend/src/main/resources/application.yaml agregar, dentro del bloque `spring` existente y sin duplicarlo, `autoconfigure.exclude: org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration`, y al final del archivo el bloque `futbolmarket` con `auth.initial-balance: 1000.00` y `security.jwt.expiration: 24h`, tal como indica contracts/configuration.md. No agregar ninguna clave `futbolmarket.auth.admin.*`, ni siquiera vacía (FR-037, SC-013)
- [X] T004 [P] Generar un secreto con el comando de PowerShell o Bash de contracts/configuration.md ("Generar un secreto", 48 bytes en Base64) y agregar `futbolmarket.security.jwt.secret: <valor>` en backend/src/main/resources/application-local.yml, sin tocar las claves existentes
- [X] T005 [P] Generar otro secreto, distinto del de local, y agregar `futbolmarket.security.jwt.secret: <valor>` en backend/src/test/resources/application-test.yml, sin tocar las claves existentes
- [X] T006 Correr `./gradlew build` en backend/ y verificar `BUILD SUCCESSFUL`: con Spring Security en el classpath todo queda protegido y `contextLoads` de backend/src/test/java/ar/edu/unq/desapp/futbolmarket/FutbolMarketApplicationTests.java tiene que seguir pasando sin modificarlo

---

## Fase 2: Fundacional (prerrequisitos bloqueantes)

**Propósito**: el núcleo del modelo, la persistencia de `app_user`, las propiedades, el esqueleto
de seguridad (sin filtro todavía) y OpenAPI. Al terminar, toda ruta no pública responde 401 en
JSON y las públicas responden sin credencial.

**⚠️ CRÍTICO**: ninguna historia empieza antes de terminar esta fase.

### Modelo base (`auth/modelo/`)

- [X] T007 [P] Crear `CredentialPolicy` en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/auth/modelo/CredentialPolicy.java: clase `final` con constructor privado, las constantes de data-model.md (`USERNAME_MIN_LENGTH = 3`, `USERNAME_MAX_LENGTH = 30`, `USERNAME_PATTERN = "^[A-Za-z0-9_]+$"`, `EMAIL_MAX_LENGTH = 254`, `EMAIL_PATTERN = "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$"`, `PASSWORD_MIN_LENGTH = 8`, `PASSWORD_MAX_LENGTH = 72`, `PASSWORD_MAX_BYTES = 72`, `BALANCE_SCALE = 2`), los mensajes en español de cada regla como constantes `String` (por ejemplo `"El nombre de usuario debe tener entre 3 y 30 caracteres."` y `"La contraseña debe tener entre 8 y 72 caracteres."`) para que los compartan los DTO y el modelo, y los predicados puros `isValidUsername`, `isValidEmail` e `isValidPassword` (este último: al menos 8 caracteres y a lo sumo 72 bytes en UTF-8). Borrar backend/src/main/java/ar/edu/unq/desapp/futbolmarket/auth/.gitkeep en el mismo commit
- [X] T008 [P] Crear el enum `Role` con `USER` y `ADMIN` en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/auth/modelo/Role.java
- [X] T009 [P] Crear el puerto `PasswordHasher` (`String hash(String plainText)` y `boolean matches(String plainText, String hash)`) en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/auth/modelo/PasswordHasher.java
- [X] T010 [P] Crear el objeto de valor `ApiKey` en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/auth/modelo/ApiKey.java, solo con JDK (research D4): `static ApiKey generate()` con 32 bytes de `SecureRandom` en Base64URL sin padding (43 caracteres), `value()`, `hash()` y `static String hashOf(String value)` con SHA-256 en hexadecimal en minúsculas (`MessageDigest` y `HexFormat`, 64 caracteres), y `toString()` que enmascara el valor
- [X] T011 [P] Crear las seis excepciones de dominio en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/auth/modelo/exception/ (`DuplicateUsernameException.java`, `DuplicateEmailException.java`, `DuplicateUsernameAndEmailException.java`, `InvalidCredentialsException.java`, `InvalidPasswordChangeException.java` e `InvalidUserDataException.java`), todas extendiendo `RuntimeException` por ahora, con los mensajes en español de la tabla "Excepciones de dominio" de data-model.md. `InvalidPasswordChangeException` e `InvalidUserDataException` reciben el mensaje por constructor; `InvalidCredentialsException` tiene un único mensaje fijo, `"Credenciales inválidas."`
- [X] T012 Crear `AppUser` en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/auth/modelo/AppUser.java con los siete campos de data-model.md (`id`, `username`, `email`, `passwordHash`, `apiKeyHash`, `role`, `balance` como `BigDecimal`), constructor privado, la fábrica `static AppUser reconstitute(Long id, String username, String email, String passwordHash, String apiKeyHash, Role role, BigDecimal balance)` sin validación de formato, getters (`getId`, `getUsername`, `getEmail`, `getRole`, `getBalance`, `getPasswordHash`, `getApiKeyHash`), ningún setter y un `toString()` que no incluye ninguno de los dos hashes (depende de T007 y T008)

### Persistencia (`auth/persistence/`)

- [X] T013 [P] Crear la entidad `AppUserSQL` en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/auth/persistence/sql/entity/AppUserSQL.java con `@Entity` y `@Table(name = "app_user", indexes = {...})` con los tres índices únicos con nombre (`ux_app_user_username`, `ux_app_user_email`, `ux_app_user_api_key_hash`) y las columnas exactas de la tabla de data-model.md (`@GeneratedValue(strategy = IDENTITY)`, `@Enumerated(EnumType.STRING)` con `length = 20`, `balance` con `precision = 19, scale = 2`, `api_key_hash` nullable); getters, setters y constructor sin argumentos con Lombok
- [X] T014 Crear `AppUserSQLDAO extends JpaRepository<AppUserSQL, Long>` en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/auth/persistence/sql/interfaces/AppUserSQLDAO.java con solo derived queries: `findByUsernameIgnoreCase`, `findByApiKeyHash`, `existsByUsernameIgnoreCase` y `existsByEmailIgnoreCase` (depende de T013)
- [X] T015 Crear `AppUserMapper` (`@Component`) en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/auth/persistence/mapper/AppUserMapper.java con `toDomain(AppUserSQL)`, que llama a `AppUser.reconstitute(...)`, y `toSQL(AppUser)`, que copia los siete campos; sin lógica de negocio (depende de T012 y T013)
- [X] T016 Crear `AppUserRepository` (`@Repository`) en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/auth/persistence/repository/AppUserRepository.java, que envuelve al DAO y al mapper y solo recibe y devuelve `AppUser`: `save` hace `saveAndFlush`, captura `DataIntegrityViolationException` y, buscando el nombre del índice sin distinguir mayúsculas en la causa más específica, lanza `DuplicateUsernameException` (`ux_app_user_username`) o `DuplicateEmailException` (`ux_app_user_email`) y relanza cualquier otro caso sin tocarlo; además `findById`, `findByUsername` (ignora mayúsculas), `findByApiKeyHash`, `existsByUsername` y `existsByEmail` (ignoran mayúsculas). Ver research D10 (depende de T011, T014 y T015)

### Configuración (`config/`)

- [X] T017 [P] Crear el record `AuthProperties` (`@ConfigurationProperties("futbolmarket.auth")`, `@Validated`) en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/config/AuthProperties.java con `initialBalance` (`@NotNull`, `@PositiveOrZero`, `@Digits(integer = 17, fraction = 2)`) y un record anidado `Admin(String username, String email, String password)` **sin** validaciones (la configuración del admin la valida el inicializador, FR-038) y con `toString()` sobrescrito para que nunca muestre la contraseña. Borrar backend/src/main/java/ar/edu/unq/desapp/futbolmarket/config/.gitkeep en el mismo commit
- [X] T018 [P] Crear el record `JwtProperties` (`@ConfigurationProperties("futbolmarket.security.jwt")`, `@Validated`) en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/config/JwtProperties.java con `secret` (`@NotBlank`) y `expiration` (`Duration`, `@NotNull`, y el constructor compacto rechaza una duración cero o negativa), con `toString()` sobrescrito para que nunca muestre el secreto
- [X] T019 Crear `ApplicationConfig` (`@Configuration`, `@EnableConfigurationProperties({AuthProperties.class, JwtProperties.class})`) con un bean `Clock` (`Clock.systemUTC()`) en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/config/ApplicationConfig.java (depende de T017 y T018)

### Esqueleto de seguridad (`security/`)

- [ ] T020 [P] Crear `ApiErrorResponseWriter` (`@Component`) en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/security/ApiErrorResponseWriter.java: escribe en el `HttpServletResponse` un record privado con la forma de `ApiError` (`timestamp` como `Instant`, `status`, `error` con la frase del status, `message` y `path`), con `Content-Type: application/json` en UTF-8 y serializando con el `JsonMapper` de Boot (Jackson 3, `tools.jackson.databind.json.JsonMapper`). Ver research D8. Borrar backend/src/main/java/ar/edu/unq/desapp/futbolmarket/security/.gitkeep en el mismo commit
- [ ] T021 [P] Crear `BCryptPasswordHasher` (`@Component`, implementa `PasswordHasher`) en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/security/BCryptPasswordHasher.java, que delega en el bean `PasswordEncoder` (depende de T009)
- [ ] T022 [P] Crear `JsonAuthenticationEntryPoint` (`@Component`, implementa `AuthenticationEntryPoint`) en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/security/JsonAuthenticationEntryPoint.java: responde 401 con `ApiErrorResponseWriter` y elige el mensaje por el tipo de excepción, con los textos exactos de research D8: `CredentialsExpiredException` → `"El token de sesión está vencido."`; `BadCredentialsException` → `"La credencial es inválida."`; cualquier otra (la de un request anónimo) → `"Se requiere una credencial: un token de sesión (Authorization: Bearer) o una clave de API (X-API-Key)."` (depende de T020)
- [ ] T023 [P] Crear `JsonAccessDeniedHandler` (`@Component`, implementa `AccessDeniedHandler`) en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/security/JsonAccessDeniedHandler.java: responde 403 con `"No tiene permisos para acceder a este recurso."` usando `ApiErrorResponseWriter` (depende de T020)
- [ ] T024 Crear `SecurityConfig` (`@Configuration`, `@EnableWebSecurity`) en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/security/SecurityConfig.java según research D7: bean `PasswordEncoder` (`BCryptPasswordEncoder`); **un único `RequestMatcher` de rutas públicas** accesible para reutilizarlo en el filtro de la HU3 (`POST /auth/register`, `POST /auth/login`, `/swagger-ui/**`, `/swagger-ui.html`, `/v3/api-docs/**`, `GET /actuator/health` y, en una sola línea con el comentario `// TEMPORAL: la protección de estos endpoints se activa en un PR posterior, una vez mergeadas las dos features`, `GET /players` y `GET /players/**`; nunca `/auth/**`); cadena principal con el DSL de lambdas: `csrf`, `httpBasic`, `formLogin` y `logout` deshabilitados, sesión `STATELESS`, autorización en este orden: `dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()`, rutas públicas `permitAll()`, `anyRequest().authenticated()`, y `exceptionHandling` con el entry point y el access denied handler; segunda cadena `@Profile("local")` con `@Order` de mayor precedencia, `securityMatcher("/h2-console/**")`, todo permitido, sin CSRF y `headers.frameOptions(sameOrigin)` (depende de T021, T022 y T023)
- [ ] T025 [P] Crear `OpenApiConfig` (`@Configuration`) en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/config/OpenApiConfig.java con un bean `OpenAPI` con `info` en español y dos security schemes en `components`, cuyos nombres son constantes públicas `BEARER_AUTH = "bearerAuth"` (HTTP `bearer`, `bearerFormat` `JWT`) y `API_KEY_AUTH = "apiKeyAuth"` (`apiKey` en el header `X-API-Key`); sin requirement global (research D16)

### Tests de la fase

- [X] T026 [P] Escribir `ApiKeyTest` (unitario, sin Spring) en backend/src/test/java/ar/edu/unq/desapp/futbolmarket/auth/modelo/ApiKeyTest.java: la clave generada tiene 43 caracteres Base64URL; dos generaciones dan valores distintos; `hashOf` es determinístico, tiene 64 caracteres hexadecimales en minúsculas y coincide con `hash()`; `toString()` no contiene el valor
- [X] T027 Escribir `AppUserRepositoryIT` en backend/src/test/java/ar/edu/unq/desapp/futbolmarket/auth/persistence/AppUserRepositoryIT.java con `@DataJpaTest`, `@ActiveProfiles("test")` e `@Import({AppUserRepository.class, AppUserMapper.class})`, creando usuarios con `AppUser.reconstitute` e id `null`: guarda y recupera los siete campos (rol como texto, saldo con escala 2); `findByUsername` y `existsByEmail` ignoran mayúsculas; un username repetido lanza `DuplicateUsernameException`; un correo repetido lanza `DuplicateEmailException`; un `api_key_hash` repetido relanza `DataIntegrityViolationException`; varios usuarios con `api_key_hash` nulo conviven (depende de T016)
- [ ] T028 [P] Escribir `JsonAccessDeniedHandlerTest` (unitario, sin contexto de Spring) en backend/src/test/java/ar/edu/unq/desapp/futbolmarket/security/JsonAccessDeniedHandlerTest.java con `MockHttpServletRequest` y `MockHttpServletResponse` y un `ApiErrorResponseWriter` construido con `JsonMapper.builder().build()`: status 403, `Content-Type` JSON y cuerpo con `status`, `error` (`Forbidden`), `message` y `path` (el 403 no se puede provocar por HTTP en esta feature)
- [ ] T029 Correr `./gradlew build` en backend/ y verificar `BUILD SUCCESSFUL`

**Checkpoint**: base lista. Las historias pueden empezar.

---

## Fase 3: Historia 1 - Registro de usuario (Prioridad: P1) 🎯 MVP

**Objetivo**: `POST /auth/register` crea la cuenta con rol `USER` y el saldo inicial configurado,
y devuelve por única vez la clave de API (FR-001 a FR-014).

**Prueba independiente**: un registro válido responde 201 con `id`, `username`, `email`, `role`
igual a `USER`, `balance` igual a `1000.00` y un `apiKey` de 43 caracteres, sin ningún campo de
contraseña (quickstart 3.1).

### Implementación de la Historia 1

- [ ] T030 [P] [US1] Crear el record `RegisteredUser(AppUser user, ApiKey apiKey)` en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/auth/modelo/RegisteredUser.java
- [ ] T031 [P] [US1] Crear el record `RegistrationAvailability(boolean usernameTaken, boolean emailTaken)` con `ensureAvailable()`, que lanza `DuplicateUsernameAndEmailException`, `DuplicateUsernameException` o `DuplicateEmailException` según la tabla de data-model.md, en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/auth/modelo/RegistrationAvailability.java
- [ ] T032 [US1] Agregar a `AppUser` en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/auth/modelo/AppUser.java: `ApiKey issueApiKey()` (genera la clave, reemplaza `apiKeyHash` y la devuelve) y la fábrica `static RegisteredUser register(String username, String email, String rawPassword, BigDecimal initialBalance, PasswordHasher hasher)`, que valida las invariantes con `CredentialPolicy` en un método privado reutilizable (lanza `InvalidUserDataException` con el mensaje de la regla, nunca con el valor recibido), hashea la contraseña, asigna `Role.USER`, normaliza el saldo a escala 2 y emite la clave (FR-010, FR-012 y FR-013)
- [ ] T033 [P] [US1] Crear el record `RegisterRequest(String username, String email, String password)` en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/auth/controller/dto/RegisterRequest.java con `@JsonIgnoreProperties(ignoreUnknown = true)` (`com.fasterxml.jackson.annotation`, que Jackson 3 conserva), un constructor compacto que recorta `username` y `email` (tolerando `null`) y nunca la contraseña, y Bean Validation con las constantes y mensajes de `CredentialPolicy`: username `@NotBlank`, `@Size(min, max)` y `@Pattern`; email `@NotBlank`, `@Email(regexp = EMAIL_PATTERN)` y `@Size(max = 254)`; password `@NotBlank` y `@Size(min = 8, max = 72)` (research D11)
- [ ] T034 [P] [US1] Crear el record `RegisterResponse(Long id, String username, String email, Role role, BigDecimal balance, String apiKey)` con `static RegisterResponse from(RegisteredUser)` en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/auth/controller/dto/RegisterResponse.java
- [ ] T035 [US1] Crear `AuthService` (`@Service`) en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/auth/service/AuthService.java con dependencias `AppUserRepository`, `PasswordHasher` y `AuthProperties`, y el método `RegisteredUser register(String username, String email, String rawPassword)`: arma `new RegistrationAvailability(repository.existsByUsername(..), repository.existsByEmail(..))`, llama a `ensureAvailable()`, crea el usuario con `AppUser.register(..., authProperties.initialBalance(), hasher)`, lo guarda y devuelve un `RegisteredUser` con el `AppUser` persistido y la clave emitida. Sin `if` de negocio y sin `@Transactional` (cada operación del repository tiene su transacción)
- [ ] T036 [US1] Crear `AuthController` (`@RestController`, `@RequestMapping("/auth")`, `@Tag(name = "Autenticación")`) en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/auth/controller/AuthController.java con `POST /register`: recibe `@Valid @RequestBody RegisterRequest`, pasa valores simples al servicio y responde 201 con `RegisterResponse`; `@Operation` con resumen y descripción en español (contracts/auth-api.yaml) y `@ApiResponse` para 201, 400 y 409, estos dos sin esquema de cuerpo hasta la Fase 10

### Tests de la Historia 1

- [ ] T037 [P] [US1] Crear el doble de test `FakePasswordHasher` (implementa `PasswordHasher` de forma determinística, por ejemplo `"hashed:" + plainText`) en backend/src/test/java/ar/edu/unq/desapp/futbolmarket/auth/modelo/FakePasswordHasher.java
- [ ] T038 [US1] Escribir `AppUserTest` (unitario, sin Spring, con `FakePasswordHasher`) en backend/src/test/java/ar/edu/unq/desapp/futbolmarket/auth/modelo/AppUserTest.java con los casos de `register` e `issueApiKey`: rol `USER`; saldo inicial con escala 2; el hash no es la contraseña en claro; `apiKeyHash` igual a `ApiKey.hashOf(apiKey.value())`; username de 3 y de 30 caracteres válidos, de 2 y de 31 rechazados, y con espacio, guion medio o acento rechazados; correo de 254 caracteres válido, de 255 y mal formado rechazados; contraseña de 8 y de 72 caracteres simples válidas, de 7 rechazada, y de 72 caracteres o menos pero más de 72 bytes rechazada; el mensaje de `InvalidUserDataException` no contiene el valor recibido; `issueApiKey` reemplaza el hash y el anterior deja de coincidir; `toString()` no contiene los hashes (depende de T037)
- [ ] T039 [P] [US1] Escribir `RegistrationAvailabilityTest` con las cuatro combinaciones de la tabla de data-model.md en backend/src/test/java/ar/edu/unq/desapp/futbolmarket/auth/modelo/RegistrationAvailabilityTest.java
- [ ] T040 [US1] Escribir `AuthServiceTest` (Mockito para `AppUserRepository`, `FakePasswordHasher` real y un `AuthProperties` real) en backend/src/test/java/ar/edu/unq/desapp/futbolmarket/auth/service/AuthServiceTest.java con los casos de registro: con username y correo libres guarda un usuario `USER` con el saldo configurado y devuelve la clave; con username tomado, correo tomado o ambos lanza la excepción correspondiente y nunca llama a `save`
- [ ] T041 [P] [US1] Crear el helper `AuthTestHelper` en backend/src/test/java/ar/edu/unq/desapp/futbolmarket/e2e/AuthTestHelper.java: recibe un `MockMvcTester`, registra por `POST /auth/register` un usuario con datos únicos (sufijo aleatorio que respete el máximo de 30 caracteres del username) y devuelve un record `TestUser(Long id, String username, String email, String password, String apiKey)`
- [ ] T042 [US1] Escribir `AuthControllerIT` (`@SpringBootTest`, `@AutoConfigureMockMvc` de `org.springframework.boot.webmvc.test.autoconfigure`, `@ActiveProfiles("test")`, `MockMvcTester`) en backend/src/test/java/ar/edu/unq/desapp/futbolmarket/e2e/AuthControllerIT.java con los caminos felices del registro: 201 cuyo cuerpo tiene exactamente las claves `id`, `username`, `email`, `role`, `balance` y `apiKey`; `role` igual a `USER` y `balance` igual a `1000.00`; la contraseña enviada no aparece en ningún lugar del cuerpo; un cuerpo con `"role": "ADMIN"` igualmente crea un `USER`; un username y un correo con espacios en los extremos vuelven recortados (depende de T041)

**Checkpoint**: la HU1 funciona sola. El usuario queda con una credencial (la clave de API),
aunque todavía no hay ningún recurso protegido donde usarla.

---

## Fase 4: Historia 2 - Inicio de sesión (Prioridad: P2)

**Objetivo**: `POST /auth/login` entrega un JWT HS256 con vigencia de 24 horas y rechaza de forma
idéntica un usuario inexistente y una contraseña incorrecta (FR-015 a FR-018).

**Prueba independiente**: se registra un usuario, se inicia sesión y se recibe 200 con `token`,
`tokenType` igual a `Bearer` y `expiresAt` unas 24 horas después (quickstart 3.2).

### Implementación de la Historia 2

- [ ] T043 [P] [US2] Crear el record `SessionToken(String value, Instant expiresAt)` en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/auth/modelo/SessionToken.java
- [ ] T044 [P] [US2] Crear el puerto `SessionTokenIssuer` (`SessionToken issueFor(AppUser user)`) en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/auth/modelo/SessionTokenIssuer.java
- [ ] T045 [US2] Agregar `void verifyPassword(String rawPassword, PasswordHasher hasher)` a `AppUser`, que lanza `InvalidCredentialsException` si no coincide, en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/auth/modelo/AppUser.java
- [ ] T046 [US2] Crear `JwtService` (`@Component`, implementa `SessionTokenIssuer`) en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/security/JwtService.java según research D3: constructor explícito que recibe `JwtProperties` y `Clock` y arma la `SecretKey` con `Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret))` (un secreto de menos de 256 bits corta el arranque con `WeakKeyException`); `issueFor` firma con `Jwts.SIG.HS256` explícito, `sub` igual al id como texto, `iat` igual a `clock.instant()` y `exp` igual a `iat` más `expiration`, sin otros claims, y devuelve un `SessionToken` (depende de T043 y T044)
- [ ] T047 [US2] Agregar `SessionToken login(String username, String rawPassword)` a `AuthService` en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/auth/service/AuthService.java: busca por username sin distinguir mayúsculas; si existe, `user.verifyPassword(...)` y `sessionTokenIssuer.issueFor(user)`; si no existe, igual ejecuta `hasher.matches` contra un hash descartable calculado una sola vez y lanza `InvalidCredentialsException`, para que el tiempo de respuesta no revele si el usuario existe (research D5). Pasar a un constructor explícito si hace falta calcular ese hash al construir
- [ ] T048 [P] [US2] Crear el record `LoginRequest(String username, String password)` con `@NotBlank` en los dos campos y un constructor compacto que recorta solo el username, en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/auth/controller/dto/LoginRequest.java
- [ ] T049 [P] [US2] Crear el record `LoginResponse(String token, String tokenType, Instant expiresAt)` con `tokenType` siempre `"Bearer"` (constante con nombre) y `static LoginResponse from(SessionToken)` en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/auth/controller/dto/LoginResponse.java
- [ ] T050 [US2] Agregar `POST /auth/login` a `AuthController` en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/auth/controller/AuthController.java: `@Valid @RequestBody LoginRequest`, responde 200 con `LoginResponse`; `@Operation` en español y `@ApiResponse` para 200, 400 y 401

### Tests de la Historia 2

- [ ] T051 [US2] Agregar a `AppUserTest` en backend/src/test/java/ar/edu/unq/desapp/futbolmarket/auth/modelo/AppUserTest.java los casos de `verifyPassword`: la contraseña correcta pasa y una incorrecta lanza `InvalidCredentialsException`
- [ ] T052 [P] [US2] Escribir `JwtServiceTest` (unitario, sin Spring, con `Clock.fixed` y un secreto de 256 bits generado en el test) en backend/src/test/java/ar/edu/unq/desapp/futbolmarket/security/JwtServiceTest.java con los casos de emisión: el token se parsea con la misma clave, el header dice `HS256`, `sub` es el id, `exp` es `iat` más 24 horas y `expiresAt` coincide; un secreto de menos de 256 bits hace fallar la construcción con `WeakKeyException`
- [ ] T053 [P] [US2] Agregar a `AuthServiceTest` en backend/src/test/java/ar/edu/unq/desapp/futbolmarket/auth/service/AuthServiceTest.java los casos de login (con `SessionTokenIssuer` mockeado): credenciales correctas devuelven el token del emisor; contraseña incorrecta lanza `InvalidCredentialsException`; usuario inexistente lanza la misma excepción con el mismo mensaje y, con un `PasswordHasher` mockeado, se verifica que igual se invocó `matches`
- [ ] T054 [US2] Agregar a `AuthTestHelper` en backend/src/test/java/ar/edu/unq/desapp/futbolmarket/e2e/AuthTestHelper.java un método que inicia sesión con un `TestUser` por `POST /auth/login` y devuelve el token
- [ ] T055 [US2] Agregar a `AuthControllerIT` en backend/src/test/java/ar/edu/unq/desapp/futbolmarket/e2e/AuthControllerIT.java los caminos felices del login: 200 con `token`, `tokenType` igual a `Bearer` y `expiresAt` 24 horas después (con tolerancia de algunos segundos); el cuerpo no contiene la contraseña ni la clave de API; el username escrito con otras mayúsculas también inicia sesión (depende de T054)

**Checkpoint**: las HU1 y HU2 funcionan. El usuario obtiene sus dos credenciales.

---

## Fase 5: Historia 3 - Protección de los recursos (Prioridad: P3)

**Objetivo**: un único filtro acepta `Authorization: Bearer` o `X-API-Key`, con precedencia del
JWT; los rechazos salen en JSON con el motivo y las rutas públicas no pasan por el filtro
(FR-019 a FR-023, research D6).

**Prueba independiente**: contra un recurso protegido que ya existe (`GET /actuator/info`), sin
credencial y con credenciales inválidas se recibe 401 con el motivo, y con cualquiera de las dos
credenciales válidas se recibe 200; las rutas públicas responden sin credencial (quickstart 3.4
y 3.5). Que las dos credenciales resuelven **el mismo usuario** (escenarios 1 y 2) se verifica
en la HU4, porque requiere `GET /auth/me`.

### Implementación de la Historia 3

- [ ] T056 [US3] Agregar `Long parseUserId(String token)` a `JwtService` en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/security/JwtService.java: parser con `verifyWith(key)`, el reloj inyectado (`clock(() -> Date.from(clock.instant()))`) y margen de reloj cero; `ExpiredJwtException` → `CredentialsExpiredException`; cualquier otra `JwtException`, un token vacío o un `sub` no numérico → `BadCredentialsException` (research D3)
- [ ] T057 [P] [US3] Agregar a `AuthService` en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/auth/service/AuthService.java los métodos de resolución para el filtro: `Optional<AppUser> findById(Long userId)` y `Optional<AppUser> findByApiKey(String rawApiKey)`, que busca por `ApiKey.hashOf(rawApiKey)`
- [ ] T058 [US3] Crear `CredentialAuthenticationFilter extends OncePerRequestFilter` (**no** `@Component`) en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/security/CredentialAuthenticationFilter.java, con constructor que recibe `JwtService`, `AuthService`, `AuthenticationEntryPoint` y el `RequestMatcher` de rutas públicas: `shouldNotFilter` devuelve `true` para las rutas públicas; si llega `Authorization: Bearer <token>` evalúa solo el JWT e ignora `X-API-Key`; si no, y `X-API-Key` no está vacía, evalúa la clave; si no hay ninguna (o el `Authorization` es de otro esquema, como Basic), sigue la cadena como anónimo; con usuario encontrado arma `UsernamePasswordAuthenticationToken.authenticated(userId, null, List.of(new SimpleGrantedAuthority("ROLE_" + role)))` en el `SecurityContextHolder`; usuario inexistente → `BadCredentialsException`; ante una `AuthenticationException` limpia el contexto, llama a `entryPoint.commence(...)` y corta la cadena. Los nombres de headers y el prefijo `Bearer ` son constantes (depende de T056 y T057)
- [ ] T059 [US3] En backend/src/main/java/ar/edu/unq/desapp/futbolmarket/security/SecurityConfig.java construir el `CredentialAuthenticationFilter` con el matcher de rutas públicas y agregarlo a la cadena principal con `addFilterBefore(filter, UsernamePasswordAuthenticationFilter.class)`, sin tocar la línea TEMPORAL de `/players` (depende de T058)

### Tests de la Historia 3

- [ ] T060 [P] [US3] Agregar a `JwtServiceTest` en backend/src/test/java/ar/edu/unq/desapp/futbolmarket/security/JwtServiceTest.java los casos de validación: un token vigente devuelve el id; con el reloj un segundo antes de `exp` se acepta y un segundo después lanza `CredentialsExpiredException`; firma alterada, token firmado con otra clave, `"abc"` y cadena vacía lanzan `BadCredentialsException`
- [ ] T061 [P] [US3] Agregar a `AuthServiceTest` en backend/src/test/java/ar/edu/unq/desapp/futbolmarket/auth/service/AuthServiceTest.java los casos de resolución: `findByApiKey` busca por el hash SHA-256 de la clave en claro; una clave desconocida y un id inexistente devuelven `Optional.empty()`
- [ ] T062 [US3] Escribir `AccessControlIT` (`@SpringBootTest`, `@AutoConfigureMockMvc`, `@ActiveProfiles("test")`, `MockMvcTester`, `AuthTestHelper`) en backend/src/test/java/ar/edu/unq/desapp/futbolmarket/e2e/AccessControlIT.java, usando `GET /actuator/info` como recurso protegido. Para los JWT a medida, inyectar `JwtProperties` y firmar con jjwt en el test. Casos: sin credencial → 401 con `Content-Type` JSON, forma de `ApiError` y el mensaje de credencial faltante (no HTML); `X-API-Key` inventada, `Bearer abc`, JWT firmado con otra clave y JWT válido con un id inexistente → 401 `"La credencial es inválida."`, y los dos últimos con el mismo cuerpo salvo `timestamp`; JWT vencido → 401 `"El token de sesión está vencido."`; clave válida → 200; token válido → 200; JWT inválido con clave válida → 401; JWT válido con clave inválida → 200; `Authorization: Basic ...` → 401 de credencial faltante; ninguna respuesta trae `Set-Cookie` con `JSESSIONID`. Rutas públicas sin credencial: `GET /actuator/health` → 200; `GET /v3/api-docs` → 200 con `bearerAuth` y `apiKeyAuth` en `components.securitySchemes`; `GET /swagger-ui.html` → redirección a `/swagger-ui/index.html`; `GET /players` → no es 401; `POST /players` → 401 (la excepción es solo `GET`); `POST /auth/login` con credenciales correctas y una `X-API-Key` inválida → 200; `POST /auth/register` con un `Bearer` inválido → 201; `GET /h2-console` en el perfil `test` → 401

**Checkpoint**: las HU1 a HU3 funcionan. Las credenciales protegen los recursos.

---

## Fase 6: Historia 4 - Consulta del perfil propio (Prioridad: P4)

**Objetivo**: `GET /auth/me` devuelve `id`, `username`, `email`, `role` y `balance` del dueño de
la credencial, y nada más (FR-024 a FR-026).

**Prueba independiente**: con la clave de API y con el token del mismo usuario, `GET /auth/me`
responde 200 con los cinco datos y el mismo `id` (quickstart 3.3).

### Implementación de la Historia 4

- [ ] T063 [US4] Crear `AccountService` (`@Service`) en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/auth/service/AccountService.java con `AppUser getProfile(Long userId)`: busca por id y, si el usuario ya no existe, lanza `InvalidCredentialsException` (el filtro ya lo resolvió, así que solo ocurre en una carrera)
- [ ] T064 [P] [US4] Crear el record `ProfileResponse(Long id, String username, String email, Role role, BigDecimal balance)` con `static ProfileResponse from(AppUser)` en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/auth/controller/dto/ProfileResponse.java
- [ ] T065 [US4] Crear `AccountController` (`@RestController`, `@RequestMapping("/auth/me")`, `@Tag(name = "Cuenta")`) en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/auth/controller/AccountController.java con dos `@SecurityRequirement` a nivel de clase, uno con `OpenApiConfig.BEARER_AUTH` y otro con `OpenApiConfig.API_KEY_AUTH` (alternativas), y `GET` que recibe `@AuthenticationPrincipal @Parameter(hidden = true) Long userId` y responde 200 con `ProfileResponse`; `@Operation` en español y `@ApiResponse` para 200 y 401 (depende de T063 y T064)

### Tests de la Historia 4

- [ ] T066 [P] [US4] Escribir `AccountServiceTest` (Mockito para `AppUserRepository`) en backend/src/test/java/ar/edu/unq/desapp/futbolmarket/auth/service/AccountServiceTest.java con los casos de perfil: devuelve el usuario del id; un id inexistente lanza `InvalidCredentialsException`
- [ ] T067 [US4] Escribir `AccountControllerIT` (`@SpringBootTest`, `@AutoConfigureMockMvc`, `@ActiveProfiles("test")`, `MockMvcTester`, `AuthTestHelper`) en backend/src/test/java/ar/edu/unq/desapp/futbolmarket/e2e/AccountControllerIT.java con los casos de perfil: con la clave de API y con el token del mismo usuario responde 200 con el mismo `id` (cubre además los escenarios 1 y 2 de la HU3); el cuerpo tiene exactamente las claves `id`, `username`, `email`, `role` y `balance`, sin contraseña, hash ni clave; `role` es `USER` y `balance` es `1000.00`; dos usuarios distintos ven cada uno solo sus datos

**Checkpoint**: las HU1 a HU4 funcionan.

---

## Fase 7: Historia 5 - Cambio de contraseña (Prioridad: P5)

**Objetivo**: `PUT /auth/me/password` cambia la contraseña si la actual coincide y la nueva es
válida y distinta; si algo falla, no cambia nada. Los tokens ya emitidos siguen valiendo
(FR-027 a FR-031 y FR-035).

**Prueba independiente**: tras un cambio que responde 204, el login con la contraseña nueva
responde 200 y el token emitido antes del cambio sigue sirviendo en `GET /auth/me`
(quickstart 3.6). El rechazo de la contraseña anterior se prueba en la Fase 10.

### Implementación de la Historia 5

- [ ] T068 [US5] Agregar `void changePassword(String currentPassword, String newPassword, PasswordHasher hasher)` a `AppUser` en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/auth/modelo/AppUser.java con tres controles en este orden, cada uno con su mensaje de data-model.md en una `InvalidPasswordChangeException`: la actual coincide; la nueva es distinta de la actual; la nueva cumple `CredentialPolicy.isValidPassword`. Solo si pasa los tres reemplaza `passwordHash`
- [ ] T069 [US5] Agregar `void changePassword(Long userId, String currentPassword, String newPassword)` a `AccountService` en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/auth/service/AccountService.java: carga el usuario, delega en `user.changePassword(...)` y lo guarda (depende de T068)
- [ ] T070 [P] [US5] Crear el record `ChangePasswordRequest(String currentPassword, String newPassword)` en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/auth/controller/dto/ChangePasswordRequest.java, sin recorte, con `currentPassword` `@NotBlank` y `newPassword` `@NotBlank` y `@Size(min = 8, max = 72)` con las constantes y el mensaje de `CredentialPolicy` (FR-029)
- [ ] T071 [US5] Agregar `PUT /password` a `AccountController` en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/auth/controller/AccountController.java: `@Valid @RequestBody ChangePasswordRequest`, responde 204 sin cuerpo; `@Operation` en español y `@ApiResponse` para 204, 400 y 401 (depende de T069 y T070)

### Tests de la Historia 5

- [ ] T072 [US5] Agregar a `AppUserTest` en backend/src/test/java/ar/edu/unq/desapp/futbolmarket/auth/modelo/AppUserTest.java los casos de `changePassword`: un cambio válido hace que la nueva verifique y la anterior no; la actual incorrecta, la nueva igual a la actual, la nueva de 7 caracteres y la nueva de más de 72 bytes lanzan `InvalidPasswordChangeException` y el hash queda igual; con la actual incorrecta y la nueva inválida, el mensaje es el de la actual incorrecta
- [ ] T073 [P] [US5] Agregar a `AccountServiceTest` en backend/src/test/java/ar/edu/unq/desapp/futbolmarket/auth/service/AccountServiceTest.java los casos de cambio de contraseña: con un cambio válido guarda el usuario; si el modelo rechaza el cambio, no llama a `save`
- [ ] T074 [US5] Agregar a `AccountControllerIT` en backend/src/test/java/ar/edu/unq/desapp/futbolmarket/e2e/AccountControllerIT.java los caminos felices del cambio: responde 204 sin cuerpo; el login con la contraseña nueva responde 200; el token emitido antes del cambio sigue respondiendo 200 en `GET /auth/me` (FR-035)

**Checkpoint**: las HU1 a HU5 funcionan.

---

## Fase 8: Historia 6 - Regeneración de la clave de API (Prioridad: P6)

**Objetivo**: `POST /auth/me/api-key` emite una clave nueva por única vez e invalida la anterior
en el acto; los tokens ya emitidos siguen valiendo (FR-032 a FR-035).

**Prueba independiente**: tras regenerar, la clave anterior responde 401 y la nueva responde 200
con el mismo `id` en `GET /auth/me` (quickstart 3.7).

### Implementación de la Historia 6

- [ ] T075 [US6] Agregar `ApiKey regenerateApiKey(Long userId)` a `AccountService` en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/auth/service/AccountService.java: carga el usuario, llama a `user.issueApiKey()`, lo guarda y devuelve la clave
- [ ] T076 [P] [US6] Crear el record `ApiKeyResponse(String apiKey)` con `static ApiKeyResponse from(ApiKey)` en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/auth/controller/dto/ApiKeyResponse.java
- [ ] T077 [US6] Agregar `POST /api-key` (sin cuerpo) a `AccountController` en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/auth/controller/AccountController.java, que responde 200 con `ApiKeyResponse`; `@Operation` en español y `@ApiResponse` para 200 y 401 (depende de T075 y T076)

### Tests de la Historia 6

- [ ] T078 [P] [US6] Agregar a `AccountServiceTest` en backend/src/test/java/ar/edu/unq/desapp/futbolmarket/auth/service/AccountServiceTest.java los casos de regeneración: el usuario guardado tiene como `apiKeyHash` el hash de la clave devuelta, distinto del anterior
- [ ] T079 [US6] Agregar a `AccountControllerIT` en backend/src/test/java/ar/edu/unq/desapp/futbolmarket/e2e/AccountControllerIT.java los casos de regeneración: responde 200 con un `apiKey` distinto del original; la clave anterior responde 401 `"La credencial es inválida."`; la nueva responde 200 con el mismo `id`; tras regenerar dos veces, la penúltima responde 401; un token emitido antes de regenerar sigue respondiendo 200

**Checkpoint**: las HU1 a HU6 funcionan.

---

## Fase 9: Historia 7 - Alta del administrador inicial (Prioridad: P7)

**Objetivo**: al arrancar, si la configuración del administrador está completa y es válida, se
crea una única cuenta `ADMIN` con saldo cero y sin clave; si no, se advierte y la aplicación
levanta igual. El alta es idempotente (FR-036 a FR-042, research D12).

**Prueba independiente**: con las tres propiedades configuradas, la cuenta existe con rol
`ADMIN`, saldo `0.00` y sin clave, y ejecutar el alta otra vez no la modifica; sin la contraseña,
no se crea nada y queda la advertencia.

### Implementación de la Historia 7

- [ ] T080 [US7] Agregar la fábrica `static AppUser createAdmin(String username, String email, String rawPassword, PasswordHasher hasher)` a `AppUser` en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/auth/modelo/AppUser.java, reutilizando el método privado de validación de `register` (sin duplicar código): `Role.ADMIN`, saldo `0.00` con escala 2, `apiKeyHash` en `null` y contraseña hasheada
- [ ] T081 [US7] Crear `AdminAccountInitializer` (`@Component`, implementa `ApplicationRunner`, **sin** `@Transactional`) en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/auth/service/AdminAccountInitializer.java con dependencias `AuthProperties`, `AppUserRepository` y `PasswordHasher`, y el flujo de research D12 en métodos cortos: falta la contraseña → `warn` y se omite; falta el username o el correo → `warn` y se omite; el username ya existe (sin distinguir mayúsculas) → `info` y no se toca nada; el correo ya está tomado → `warn` y se omite; `AppUser.createAdmin(...)` lanza `InvalidUserDataException` → `warn` con el mensaje de la regla y se omite; si no, se guarda y se registra en `info`. Captura también `DuplicateUsernameException` y `DuplicateEmailException` de una carrera con un registro público y advierte. Ningún log contiene la contraseña (depende de T080)

### Tests de la Historia 7

- [ ] T082 [US7] Agregar a `AppUserTest` en backend/src/test/java/ar/edu/unq/desapp/futbolmarket/auth/modelo/AppUserTest.java los casos de `createAdmin`: rol `ADMIN`, saldo `0.00`, `apiKeyHash` nulo y contraseña hasheada; un username con guion medio, un correo mal formado o una contraseña de 7 caracteres lanzan `InvalidUserDataException`
- [ ] T083 [P] [US7] Escribir `AdminAccountInitializerTest` (Mockito para `AppUserRepository`, `FakePasswordHasher` y `@ExtendWith(OutputCaptureExtension.class)`) en backend/src/test/java/ar/edu/unq/desapp/futbolmarket/auth/service/AdminAccountInitializerTest.java: sin contraseña, sin username o sin correo → advertencia y nunca `save`; username existente (aunque sea de un usuario común o con otras mayúsculas) → no llama a `save`; correo tomado → advertencia y nunca `save`; username inválido → advertencia con la regla y nunca `save`; configuración completa → guarda un `ADMIN` con saldo cero y sin clave; `save` que lanza `DuplicateUsernameException` → advertencia sin propagar; en ningún caso la salida capturada contiene la contraseña configurada
- [ ] T084 [US7] Escribir `AdminAccountInitializerIT` (`@SpringBootTest`, `@ActiveProfiles("test")`) en backend/src/test/java/ar/edu/unq/desapp/futbolmarket/auth/service/AdminAccountInitializerIT.java con `@DynamicPropertySource` que genera al azar `futbolmarket.auth.admin.username`, `.email` y `.password` y fija una URL de H2 en memoria propia (`jdbc:h2:mem:admin-<uuid>;DB_CLOSE_DELAY=-1`) para no pisar la `testdb` de otros contextos (research D17): tras el arranque existe exactamente una cuenta con el username configurado, rol `ADMIN`, saldo `0.00` y sin clave; invocar `run(...)` varias veces deja el `passwordHash` idéntico (SC-011); después de emitirle una clave y guardarlo, invocar `run(...)` no cambia su `apiKeyHash` (escenario 3)
- [ ] T085 [US7] Escribir `AdminAccountIT` (`@SpringBootTest`, `@AutoConfigureMockMvc`, `@ActiveProfiles("test")`, `MockMvcTester`) en backend/src/test/java/ar/edu/unq/desapp/futbolmarket/e2e/AdminAccountIT.java con el mismo patrón de `@DynamicPropertySource` (credenciales al azar y URL de H2 propia): el admin inicia sesión con su contraseña; `GET /auth/me` muestra `role` `ADMIN` y `balance` `0.00`; `POST /auth/me/api-key` le entrega una clave que responde 200 en `GET /auth/me` (escenarios 5 y 6; depende de las HU2, HU4 y HU6)

**Checkpoint**: las siete historias funcionan, salvo los errores de dominio por HTTP, que
dependen de `shared/`.

---

## Fase 10: Integración con `shared/` (⛔ BLOQUEADA hasta que catálogo esté en `develop`)

**Propósito**: darles a los errores de dominio su status y su formato definitivos y escribir los
end to end de errores (plan, decisión 3; contracts/shared-integration.md, sección 7).

**⚠️ No empezar** hasta que la feature `002-catalogo-jugadores` esté mergeada en `develop`.

- [ ] T086 Rebasear la rama `001-auth-usuarios` sobre `develop`; en backend/src/main/resources/application.yaml conservar las claves de las dos features (el bloque `futbolmarket` al final y la exclusión dentro de `spring`), y verificar que `./gradlew build` pase antes de seguir
- [ ] T087 Leer la clase base de excepciones y el `ApiError` de backend/src/main/java/ar/edu/unq/desapp/futbolmarket/shared/ y confirmar que la base se puede extender sin tipos de Spring (contracts/shared-integration.md, sección 2). Si exige un `HttpStatus` u otro tipo de Spring, **detenerse y avisar al equipo** antes de tocar `auth/modelo/`
- [ ] T088 [P] Hacer que las seis excepciones de backend/src/main/java/ar/edu/unq/desapp/futbolmarket/auth/modelo/exception/ (`DuplicateUsernameException`, `DuplicateEmailException`, `DuplicateUsernameAndEmailException`, `InvalidCredentialsException`, `InvalidPasswordChangeException` e `InvalidUserDataException`) extiendan la base, o la base por categoría, de `shared/` según el mapeo de la sección 3 del contrato (409, 401 y 400)
- [ ] T089 [P] Hacer que backend/src/main/java/ar/edu/unq/desapp/futbolmarket/security/ApiErrorResponseWriter.java use el `ApiError` de `shared/` si es público y borrar el record privado; si no es público, mantener el record con la misma forma y avisar
- [ ] T090 [P] Agregar `content = @Content(schema = @Schema(implementation = ApiError.class))` a los `@ApiResponse` de error de backend/src/main/java/ar/edu/unq/desapp/futbolmarket/auth/controller/AuthController.java y backend/src/main/java/ar/edu/unq/desapp/futbolmarket/auth/controller/AccountController.java
- [ ] T091 [P] Escribir la clase nueva `AuthErrorsIT` (`@SpringBootTest`, `@AutoConfigureMockMvc`, `@ActiveProfiles("test")`, `MockMvcTester`; `@DynamicPropertySource` con credenciales de admin al azar y URL de H2 propia, como en T085) en backend/src/test/java/ar/edu/unq/desapp/futbolmarket/e2e/AuthErrorsIT.java: registro con username de 2 caracteres, correo mal formado y contraseña de 7 caracteres → 400 con `violations` que nombran el campo, **sin la contraseña enviada en ningún lugar del cuerpo**, y sin cuenta creada (el mismo registro corregido luego responde 201); username tomado → 409 con el mensaje del username; correo tomado → 409 con el del correo; los dos tomados → 409 con el de ambos; username o correo tomados con otras mayúsculas o con espacios → 409; registro público con el username del admin → 409; login con usuario inexistente y login con contraseña incorrecta → 401 con el mismo cuerpo salvo `timestamp` (SC-007)
- [ ] T092 [P] Escribir la clase nueva `AccountErrorsIT` (`@SpringBootTest`, `@AutoConfigureMockMvc`, `@ActiveProfiles("test")`, `MockMvcTester`, `AuthTestHelper`) en backend/src/test/java/ar/edu/unq/desapp/futbolmarket/e2e/AccountErrorsIT.java: contraseña actual incorrecta → 400 `"La contraseña actual es incorrecta."` y la contraseña vigente sigue iniciando sesión; nueva igual a la actual → 400 `"La nueva contraseña debe ser distinta de la actual."`; nueva de 7 caracteres → 400 con `violations` sin los valores de las contraseñas; tras un cambio exitoso, el login con la contraseña anterior → 401 (escenario 2 de la HU5)
- [ ] T093 Correr `./gradlew build` en backend/ y verificar `BUILD SUCCESSFUL` con los tests de catálogo en verde; con la aplicación levantada, `GET /players` sin credencial responde 200 y la sección 6 de quickstart.md da los resultados esperados

**Checkpoint**: la feature queda completa, con los errores en su formato definitivo.

---

## Fase 11: Cierre y definición de terminado

**Propósito**: verificaciones transversales y la definición de terminado de la constitución.
Las tareas T094 a T101 se pueden correr mientras la Fase 10 está bloqueada, y se repiten T098 y
T099 después de integrarla.

- [ ] T094 [P] Verificar el árbol: no existe backend/src/main/java/ar/edu/unq/desapp/futbolmarket/domain/; no queda `.gitkeep` en `auth/`, `security/` ni `config/`; `git diff --stat main -- backend/src/main/java/ar/edu/unq/desapp/futbolmarket/catalog backend/src/main/java/ar/edu/unq/desapp/futbolmarket/shared` no muestra cambios de esta feature; backend/src/test/java/ar/edu/unq/desapp/futbolmarket/FutbolMarketApplicationTests.java no cambió
- [ ] T095 [P] Verificar la pureza del modelo: ningún archivo de backend/src/main/java/ar/edu/unq/desapp/futbolmarket/auth/modelo/ y sus subpaquetes importa `org.springframework`, `jakarta.persistence` ni `...auth.controller`; ningún controller inyecta un repository, un DAO ni un mapper; ningún DTO llega al servicio (principio I)
- [ ] T096 [P] Verificar SC-013 y la ausencia de secretos: ningún archivo del repositorio define `futbolmarket.auth.admin.*` ni las variables `FUTBOLMARKET_AUTH_ADMIN_*` con valor, y el único secreto JWT está en backend/src/main/resources/application-local.yml y backend/src/test/resources/application-test.yml
- [ ] T097 [P] Revisión orientada a SonarCloud del código nuevo en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/auth/, `security/` y `config/`: sin imports ni parámetros sin usar, sin números mágicos, sin código duplicado (la validación de `register` y `createAdmin` es un solo método), sin `System.out`, sin `catch` genéricos, métodos cortos y comentarios que explican el porqué (principio V)
- [ ] T098 Correr `./gradlew build` en backend/ y verificar `BUILD SUCCESSFUL`
- [ ] T099 Levantar con `./gradlew bootRun` desde backend/ y seguir las secciones 2 y 3 de quickstart.md: arranca con el perfil `local`, sin variables del admin deja la advertencia y levanta igual, **no** aparece `Using generated security password`, y los escenarios HTTP 3.1 a 3.8 dan los resultados esperados (el 3.8 con las tres variables `FUTBOLMARKET_AUTH_ADMIN_*` de contracts/configuration.md)
- [ ] T100 Seguir la sección 4 de specs/001-auth-usuarios/quickstart.md en `http://localhost:8080/swagger-ui.html`: los cinco endpoints de `/auth` tienen descripción y códigos de respuesta, solo los tres de `/auth/me` muestran candado, **Authorize** ofrece `bearerAuth` y `apiKeyAuth`, y `GET /auth/me` responde 200 autorizando con cada uno por separado
- [ ] T101 Seguir la sección 5 de specs/001-auth-usuarios/quickstart.md en `http://localhost:8080/h2-console`: la consola abre sin credencial de la API y con frames, en `APP_USER` el `PASSWORD_HASH` empieza con `$2`, `API_KEY_HASH` tiene 64 caracteres hexadecimales, `ROLE` es texto, `BALANCE` tiene 2 decimales y los usuarios sobreviven a un reinicio

---

## Dependencias y orden de ejecución

### Dependencias entre fases

- **Preparación (Fase 1)**: sin dependencias. T001 espera la confirmación del usuario.
- **Fundacional (Fase 2)**: depende de la Fase 1. **Bloquea todas las historias.**
- **HU1 (Fase 3)**: depende solo de la Fase 2.
- **HU2 (Fase 4)**: depende de la Fase 2. Sus end to end usan el registro de la HU1 para tener
  un usuario (`AuthTestHelper`), así que en la práctica va después de la HU1.
- **HU3 (Fase 5)**: depende de la HU1 y la HU2 (necesita credenciales reales), como dice el spec.
- **HU4 (Fase 6)**: depende de la HU3 (sin filtro, `GET /auth/me` siempre responde 401).
- **HU5 (Fase 7)** y **HU6 (Fase 8)**: dependen de la HU4 (crean y extienden `AccountService` y
  `AccountController`). Son independientes entre sí, pero tocan los mismos archivos: conviene
  hacerlas en secuencia.
- **HU7 (Fase 9)**: T080 a T084 dependen solo de la Fase 2 y de la HU1 (T032, porque reutilizan
  la validación de `register`). T085 (`AdminAccountIT`) depende además de la HU2, la HU4 y la HU6.
- **Integración (Fase 10)**: bloqueada por un evento externo (catálogo en `develop`) y depende de
  todas las historias.
- **Cierre (Fase 11)**: T094 a T101 pueden correr después de la Fase 9; T098 y T099 se repiten
  después de la Fase 10.

```text
Fase 1 ─► Fase 2 ─► HU1 ─► HU2 ─► HU3 ─► HU4 ─► HU5 ─► HU6 ─► Fase 11 ─► Fase 10 (⛔) ─► T098, T099 otra vez
                     └────────► HU7 (T080–T084) ─────────────┘ (T085 espera HU2, HU4 y HU6)
```

### Dentro de cada historia

- Modelo → servicio → DTO → controller → tests de esa historia (sin TDD).
- Una historia termina cuando sus tests existen y pasan y `./gradlew build` está en verde.

### Oportunidades de paralelismo

- Fase 1: T002 a T005 en paralelo mientras se espera la confirmación de T001.
- Fase 2: T007 a T011, T013, T017, T018, T020, T021 y T025 arrancan juntos; después T022 y T023;
  T026 y T028 cuando existan `ApiKey` y el access denied handler.
- En cada historia, los records del modelo y los DTO marcados [P] (por ejemplo T030, T031, T033 y
  T034 en la HU1) se escriben en paralelo.
- Con dos personas: una sigue HU1 → HU2 → HU3 → HU4 → HU5 → HU6 y la otra hace la HU7 (T080 a
  T084) apenas termina T032.
- En la Fase 10, T088 a T092 van en paralelo después de T087.

---

## Ejemplo de paralelismo: Historia 1

```bash
# Modelo y DTO de la HU1, en paralelo:
Task: "Crear RegisteredUser en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/auth/modelo/RegisteredUser.java"
Task: "Crear RegistrationAvailability en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/auth/modelo/RegistrationAvailability.java"
Task: "Crear RegisterRequest en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/auth/controller/dto/RegisterRequest.java"
Task: "Crear RegisterResponse en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/auth/controller/dto/RegisterResponse.java"

# Tests independientes de la HU1, en paralelo (después de la implementación):
Task: "Escribir RegistrationAvailabilityTest en backend/src/test/java/ar/edu/unq/desapp/futbolmarket/auth/modelo/RegistrationAvailabilityTest.java"
Task: "Crear AuthTestHelper en backend/src/test/java/ar/edu/unq/desapp/futbolmarket/e2e/AuthTestHelper.java"
```

## Ejemplo de paralelismo: Historia 3

```bash
# Resolución de credenciales, en paralelo:
Task: "Agregar parseUserId a JwtService en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/security/JwtService.java"
Task: "Agregar findById y findByApiKey a AuthService en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/auth/service/AuthService.java"

# Unitarios de la HU3, en paralelo:
Task: "Casos de validación en backend/src/test/java/ar/edu/unq/desapp/futbolmarket/security/JwtServiceTest.java"
Task: "Casos de resolución en backend/src/test/java/ar/edu/unq/desapp/futbolmarket/auth/service/AuthServiceTest.java"
```

## Ejemplo de paralelismo: Historia 7

```bash
# Con la HU1 terminada, en paralelo a las HU2 a HU6:
Task: "Agregar createAdmin a AppUser en backend/src/main/java/ar/edu/unq/desapp/futbolmarket/auth/modelo/AppUser.java"
Task: "Escribir AdminAccountInitializerTest en backend/src/test/java/ar/edu/unq/desapp/futbolmarket/auth/service/AdminAccountInitializerTest.java"
```

---

## Estrategia de implementación

### MVP primero (solo la HU1)

1. Fase 1: Preparación (con la confirmación de las dependencias).
2. Fase 2: Fundacional.
3. Fase 3: HU1.
4. **Parar y validar**: `./gradlew build` en verde y quickstart 3.1 con `bootRun`.

El MVP deja registrarse y obtener la clave de API. Para que la clave sirva de algo hacen falta
la HU3 y la HU4, así que la primera demo con valor completo es **HU1 a HU4**.

### Entrega incremental

1. Preparación + Fundacional → base lista.
2. HU1 → registro (MVP).
3. HU2 → login.
4. HU3 + HU4 → recursos protegidos y perfil: primera demo completa.
5. HU5 → cambio de contraseña.
6. HU6 → regeneración de la clave.
7. HU7 → administrador inicial (lo necesita la feature de mercado).
8. Cierre (Fase 11) con todo lo anterior.
9. Integración con `shared/` (Fase 10) cuando catálogo esté en `develop`, y repetir T098 y T099.

Cada paso deja `./gradlew build` en verde (plan, "Secuencia de implementación").

---

## Notas

- [P] = otro archivo y sin dependencias pendientes.
- [USn] vincula cada tarea con su historia para la trazabilidad.
- Hacer un commit por tarea o por grupo lógico, y borrar cada `.gitkeep` en el commit en que su
  paquete deja de estar vacío.
- Parar en cada checkpoint para validar la historia por separado.
- Ante una duda de forma, gana la constitución; ante una ambigüedad del pedido, preguntar antes de
  escribir código.
