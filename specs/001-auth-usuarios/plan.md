# Plan de implementación: Registro, credenciales y acceso autenticado

**Rama**: `001-auth-usuarios` | **Fecha**: 2026-09-18 | **Especificación**: [spec.md](./spec.md)

**Entrada**: especificación de la feature en `/specs/001-auth-usuarios/spec.md` y el pedido de
planificación del 2026-09-18.

## Resumen

La feature agrega al backend existente:

- registro público con clave de API de un solo uso;
- inicio de sesión con un JWT HS256 de 24 horas;
- protección de todos los recursos no públicos con cualquiera de las dos credenciales;
- gestión de las credenciales propias: perfil, cambio de contraseña y regeneración de la
  clave;
- alta idempotente del administrador al arrancar (HU7).

Enfoque técnico:

- **Modelo**: `AppUser` es un modelo rico y puro en `auth/modelo/`. La infraestructura entra
  por dos puertos propios: `PasswordHasher`, implementado con BCrypt, y `SessionTokenIssuer`,
  implementado por `JwtService`. La clave de API se guarda como hash SHA-256 y se busca por
  igualdad.
- **Persistencia**: `AppUserSQL` mapea la tabla `app_user`, con índices únicos explícitos.
  `AppUserRepository` recibe y devuelve modelo, y traduce las violaciones de unicidad a
  excepciones de dominio.
- **Seguridad**: un único filtro acepta `Authorization: Bearer` y `X-API-Key`; si llegan los
  dos, tiene precedencia el JWT. Las rutas públicas se declaran una por una en
  `SecurityConfig`. Los 401 y 403 se escriben en JSON con el mismo `ApiError` del advice.
- **Documentación**: Swagger con dos security schemes.
- **Alcance de paquetes**: se tocan únicamente `auth/`, `security/` y `config/`. Además se
  elimina el paquete inválido `domain/`.

## Decisiones de planificación (2026-09-18)

El spec y el pedido de planificación se contradecían en tres puntos. Los resolvió el usuario:

1. **HU7 (alta del administrador): se incluye.** Prevalece el spec y la decisión de equipo del
   14/09 registrada en `checklists/requirements.md` sobre la frase del pedido "en esta feature
   NO se crea ningún administrador". La "creación de datos de prueba al arrancar" sigue fuera
   de alcance: el administrador no es un dato de prueba.
2. **Dos credenciales en el mismo request: tiene precedencia el JWT.** Si llega
   `Authorization: Bearer`, se evalúa solo el token y `X-API-Key` se ignora. Un JWT inválido
   con una clave válida da 401. Prevalece el pedido sobre el spec, que decía "alcanza con que
   una sea válida".
3. **Integración con `shared/`: al final.** Se implementa todo lo que no depende del advice
   de `shared/`. Una vez mergeado catálogo en `develop`, se rebasea, las excepciones pasan a
   extender la base de `shared/` y recién ahí se escriben los tests end to end de errores de
   dominio. El detalle está en [contracts/shared-integration.md](./contracts/shared-integration.md).

## Divergencias con el spec (enmiendas aplicadas el 2026-09-18)

Estas diferencias entre el spec original y el plan ya se aplicaron en `spec.md` (sección
Clarifications, sesión 2026-09-18) y quedaron registradas en la iteración 3 de
`checklists/requirements.md`. El spec y el plan están alineados.

| Tema | Spec original | Plan | Enmienda aplicada |
|---|---|---|---|
| Dos credenciales en un request | Caso borde y supuesto: "alcanza con que una de las dos sea válida" | Precedencia del JWT (decisión 2) | Se reescribieron el caso borde y el supuesto. Se agregaron FR-019a y los escenarios 7 y 8 de la HU3. |
| Datos del perfil y del registro | HU4: "los cuatro datos y ninguno más"; FR-024 lista cuatro | Se agrega `id` en `/auth/me` y en el registro, como pide el pedido | Se agregó el identificador en FR-024, en la HU4, en el escenario 1 de la HU1 y en la entidad Usuario. |
| Largo máximo de la contraseña | Solo el mínimo de 8 (FR-004) | Máximo de 72 caracteres y 72 bytes, por el límite de BCrypt (research D5) | FR-004 ahora dice "a lo sumo 72 bytes". Se agregaron un supuesto y dos casos borde. |
| Largo máximo del correo | Sin límite | 254 caracteres (RFC 5321) | FR-003 ahora dice "a lo sumo 254 caracteres". Se agregó un supuesto. |
| Unicidad sin distinguir mayúsculas con registros concurrentes | "Dos registros con el mismo nombre ... casi simultáneamente" | Garantizada para nombres idénticos. Para variantes de mayúsculas simultáneas, riesgo aceptado (research D9) | El caso borde se acotó a valores idénticos y un supuesto documenta el riesgo aceptado. |

No son divergencias:

- `/players` queda público solo para `GET`. Coincide con FR-023, que habla de "recursos de
  consulta", y es más estricto que "/players y /players/**" del pedido.
- `/actuator/info` exige credencial. Coincide con FR-022, que solo hace pública la
  verificación de estado.

## Contexto técnico

**Lenguaje y versión**: Java 21 (toolchain de Gradle, wrapper 9.7.1).

**Dependencias principales**:

- Ya presentes: Spring Boot 4.1.1 (`webmvc`, `data-jpa`, `validation`, `actuator`,
  `h2console`), Lombok y H2.
- Nuevas, pendientes de confirmación antes de tocar `build.gradle`:
  `spring-boot-starter-security` (Spring Security 7.1), jjwt 0.13.0 en tres artefactos y
  springdoc-openapi 3.1.1 (ver [research.md](./research.md), D1 y D2).

**Almacenamiento**: H2. En `local`, `jdbc:h2:file` con `ddl-auto: update`, ya configurado.
En `test`, `jdbc:h2:mem` con `create-drop`. Una tabla nueva, `app_user`.

**Tests**:

- JUnit 5, Mockito y AssertJ.
- `MockMvcTester` con `@SpringBootTest` y `@AutoConfigureMockMvc`.
- `@DataJpaTest`, `OutputCaptureExtension` y `@DynamicPropertySource`.
- Todo con `@ActiveProfiles("test")`, sin Testcontainers ni TDD.

**Plataforma**: API REST local en el puerto 8080, levantada con `./gradlew bootRun`, sin
Docker ni servicios externos.

**Tipo de proyecto**: servicio web en un monorepo (`backend/` y `frontend/`, vacío). Solo se
toca el backend.

**Objetivos de rendimiento**: sin objetivos cuantificados para la entrega 1.

- BCrypt con costo 10 tarda unos 100 ms por verificación, solo en el login y el cambio de
  contraseña.
- La autenticación por request resuelve por índice (hash de la clave) o por clave primaria
  (id del JWT).

**Restricciones**:

- Sesiones stateless y sin CSRF.
- No se tocan `catalog/`, `shared/`, `market/` ni `quotes/`.
- No hay endpoints bajo `/users`.
- Ninguna dependencia fuera de las cinco listadas.
- No se modifican tests existentes.
- El mapeo de errores de dominio a HTTP depende del advice de `shared/` (decisión 3).

**Escala y alcance**: 5 endpoints, 1 tabla, 7 historias (HU1 a HU7), 43 requisitos
funcionales (incluido FR-019a) y 13 criterios de éxito. Del orden de decenas a cientos de usuarios.

No queda ningún NEEDS CLARIFICATION: todo se resolvió en [research.md](./research.md).

## Verificación constitucional

*Puerta: tiene que pasar antes de la investigación y se revisa de nuevo después del diseño.*

### Antes de la investigación

| Principio | Resultado | Evidencia en el diseño |
|---|---|---|
| I. Capas estrictas (NO NEGOCIABLE) | Cumple | Ver el detalle debajo de la tabla. |
| II. Modelo rico | Cumple | `AppUser` decide el rol, el saldo inicial, la emisión de la clave y la verificación y el cambio de contraseña. `RegistrationAvailability` decide qué conflicto informar. Los servicios solo orquestan: resuelven, delegan al modelo y persisten. |
| III. Validación en su nivel | Cumple, con una nota | Forma y trimming en los DTO con Bean Validation. Existencia y disponibilidad en el servicio. Invariantes en el modelo. Excepciones propias con nombre. El advice único es el de `shared/`, que auth no crea. Los 401 y 403 de la cadena de filtros los escriben el entry point y el handler de `security/` con el mismo formato: no son un segundo advice, porque el advice no puede verlos. La nota sobre formato en dos niveles está en Seguimiento de complejidad. |
| IV. Tests (NO NEGOCIABLE) | Cumple | Unitarios de modelo sin Spring, de servicio con Mockito, integración contra H2 en memoria con `@ActiveProfiles("test")`, y end to end con MockMvc en su propio paquete `e2e/`. Aserciones con AssertJ, nombres `*Test` e `*IT`, casos felices y de borde, sin TDD. `FutbolMarketApplicationTests` no se toca, y los tests de errores de dominio se escriben una sola vez, en la integración. |
| V. Calidad medible | Cumple | Ver el detalle debajo de la tabla. |
| VI. Persistencia explícita | Cumple | `@Table(name = "app_user")`, índices únicos con nombre, `EnumType.STRING`, `DECIMAL(19,2)` y solo derived queries. `open-in-view` en `false`, `local` en archivo con `update` y `test` en memoria, todo ya configurado. |
| VII. Idioma | Cumple | Identificadores y endpoints en inglés. Mensajes de la API, documentación y comentarios en español. El paquete `modelo/` conserva su nombre. |
| Stack tecnológico | Cumple, con aviso | Spring Security, jjwt y springdoc están en el stack de la constitución. Las versiones se verificaron para Boot 4 (springdoc 3.x, no la 2.x de Boot 3). Se avisan y se justifican, y `build.gradle` se toca solo después de la confirmación del usuario. |
| Definición de terminado | Cumple | `./gradlew build`, `bootRun` con `local` y los cinco endpoints documentados y ejecutables desde `/swagger-ui.html`, con los dos métodos de Authorize. |

Detalle del principio I:

- El modelo es puro. La infraestructura entra por los puertos `PasswordHasher` y
  `SessionTokenIssuer`, y `ApiKey` usa solo JDK.
- Las anotaciones de JPA viven solo en `AppUserSQL`.
- `AppUserMapper` traduce en las dos direcciones y lo invoca `AppUserRepository`.
- Los controllers solo hablan con `AuthService` y `AccountService`. Los DTO viven en
  `controller/dto/` y no cruzan al servicio: el controller pasa valores simples. El servicio
  recibe y devuelve modelo.
- No se inventan paquetes y se elimina `domain/`.
- El principal de seguridad es el id del usuario, un `Long`, así que `auth` no depende de
  `security` y no hay ciclos entre paquetes.

Detalle del principio V:

- Inyección por constructor con `@RequiredArgsConstructor`, SLF4J para los logs y constantes
  con nombre (`CredentialPolicy`).
- Métodos cortos y sin `catch` genéricos.
- No hay secretos en el código. El secreto de desarrollo vive solo en los perfiles `local` y
  `test`. La base no tiene ninguno, así que la aplicación no arranca sin la variable de
  entorno.

**Resultado**: la puerta pasa, sin violaciones que bloqueen.

### Después del diseño

Se revisaron [data-model.md](./data-model.md), [contracts/](./contracts/) y
[quickstart.md](./quickstart.md) contra cada principio:

- **I**: ningún tipo de `auth/modelo/` importa Spring ni JPA. `ApiKey` usa solo
  `SecureRandom`, `MessageDigest` y `HexFormat`.
- **I**: el grafo de dependencias entre paquetes no tiene ciclos:
  - `security → auth.service, auth.modelo, config`
  - `auth.controller → auth.service, auth.modelo, config`
  - `auth.service → auth.modelo, auth.persistence.repository, config`
- **II**: ningún servicio contiene un `if` de negocio. Las reglas de conflicto viven en
  `RegistrationAvailability`, y las del cambio de contraseña en `AppUser`. Los `if` del
  inicializador del admin deciden si la configuración está presente, que es un problema de
  la aplicación y no del dominio.
- **III**: los mensajes de error están en español y no revelan datos. El contrato con
  `shared/` exige que el advice no devuelva `rejectedValue` de las contraseñas.
- **VI**: no hay SQL nativo, y el mapeo no depende de H2. Se descartaron `IGNORECASE` y
  `VARCHAR_IGNORECASE`.
- **Stack**: no aparecen dependencias nuevas fuera de las cinco.

**Resultado**: la puerta sigue pasando.

## Estructura del proyecto

### Documentación de la feature

```text
specs/001-auth-usuarios/
├── spec.md                        # especificación (con las enmiendas del 2026-09-18 aplicadas)
├── plan.md                        # este archivo
├── research.md                    # decisiones D1 a D18
├── data-model.md                  # modelo, persistencia, transiciones y trazabilidad
├── quickstart.md                  # guía de validación de punta a punta
├── contracts/
│   ├── auth-api.yaml              # OpenAPI 3.0.3 de los cinco endpoints y los dos security schemes
│   ├── shared-integration.md      # lo que auth necesita de shared/ y los pasos de integración
│   └── configuration.md           # propiedades, variables de entorno y valores por perfil
├── checklists/requirements.md     # existente
└── tasks.md                       # lo genera después /speckit-tasks
```

### Código fuente

```text
backend/
├── build.gradle                                   # + security, + jjwt (3), + springdoc (tras confirmación)
├── src/main/java/ar/edu/unq/desapp/futbolmarket/
│   ├── FutbolMarketApplication.java               # sin cambios
│   ├── domain/.gitkeep                            # SE ELIMINA: la constitución declara inválido este paquete
│   ├── auth/                                      # se borra auth/.gitkeep
│   │   ├── controller/
│   │   │   ├── AuthController.java                # POST /auth/register, POST /auth/login (públicos)
│   │   │   ├── AccountController.java             # GET /auth/me, PUT /auth/me/password, POST /auth/me/api-key
│   │   │   └── dto/
│   │   │       ├── RegisterRequest.java           # trimming, Bean Validation, ignora propiedades desconocidas
│   │   │       ├── RegisterResponse.java          # id, username, email, role, balance, apiKey
│   │   │       ├── LoginRequest.java
│   │   │       ├── LoginResponse.java             # token, tokenType, expiresAt
│   │   │       ├── ProfileResponse.java           # id, username, email, role, balance
│   │   │       ├── ChangePasswordRequest.java
│   │   │       └── ApiKeyResponse.java
│   │   ├── service/
│   │   │   ├── AuthService.java                   # registro, login y resolución de credenciales para el filtro
│   │   │   ├── AccountService.java                # perfil, cambio de contraseña y regeneración de la clave
│   │   │   └── AdminAccountInitializer.java       # ApplicationRunner: alta idempotente del admin (HU7)
│   │   ├── modelo/
│   │   │   ├── AppUser.java
│   │   │   ├── Role.java
│   │   │   ├── CredentialPolicy.java              # reglas de formato y constantes (única fuente de verdad)
│   │   │   ├── ApiKey.java
│   │   │   ├── RegisteredUser.java
│   │   │   ├── RegistrationAvailability.java
│   │   │   ├── SessionToken.java
│   │   │   ├── PasswordHasher.java                # puerto
│   │   │   ├── SessionTokenIssuer.java            # puerto
│   │   │   ├── DuplicateUsernameException.java
│   │   │   ├── DuplicateEmailException.java
│   │   │   ├── DuplicateUsernameAndEmailException.java
│   │   │   ├── InvalidCredentialsException.java
│   │   │   ├── InvalidPasswordChangeException.java
│   │   │   └── InvalidUserDataException.java
│   │   └── persistence/
│   │       ├── repository/AppUserRepository.java  # envuelve DAO y mapper, traduce la unicidad
│   │       ├── sql/entity/AppUserSQL.java         # @Table(name = "app_user") con índices únicos
│   │       ├── sql/interfaces/AppUserSQLDAO.java  # JpaRepository<AppUserSQL, Long>, derived queries
│   │       └── mapper/AppUserMapper.java          # toDomain / toSQL
│   ├── security/                                  # se borra security/.gitkeep
│   │   ├── SecurityConfig.java                    # cadena principal y cadena de la consola de H2 (@Profile("local"))
│   │   ├── CredentialAuthenticationFilter.java    # JWT o X-API-Key; no es @Component
│   │   ├── JwtService.java                        # implementa SessionTokenIssuer; HS256
│   │   ├── BCryptPasswordHasher.java              # implementa PasswordHasher
│   │   ├── JsonAuthenticationEntryPoint.java      # 401 en JSON
│   │   ├── JsonAccessDeniedHandler.java           # 403 en JSON
│   │   └── ApiErrorResponseWriter.java            # escribe la forma de ApiError
│   ├── config/                                    # se borra config/.gitkeep
│   │   ├── ApplicationConfig.java                 # bean Clock + @EnableConfigurationProperties
│   │   ├── AuthProperties.java                    # futbolmarket.auth.* (saldo inicial y admin)
│   │   ├── JwtProperties.java                     # futbolmarket.security.jwt.*
│   │   └── OpenApiConfig.java                     # bearerAuth + apiKeyAuth
│   ├── catalog/  shared/                          # NO SE TOCAN (feature paralela)
├── src/main/resources/
│   ├── application.yaml                           # + bloque futbolmarket + exclusión del usuario en memoria
│   └── application-local.yml                      # + secreto JWT de desarrollo
└── src/test/
    ├── java/ar/edu/unq/desapp/futbolmarket/
    │   ├── FutbolMarketApplicationTests.java      # existente, NO se modifica
    │   ├── auth/
    │   │   ├── modelo/
    │   │   │   ├── AppUserTest.java
    │   │   │   ├── ApiKeyTest.java
    │   │   │   ├── RegistrationAvailabilityTest.java
    │   │   │   └── FakePasswordHasher.java        # doble de test del puerto
    │   │   ├── service/
    │   │   │   ├── AuthServiceTest.java
    │   │   │   ├── AccountServiceTest.java
    │   │   │   ├── AdminAccountInitializerTest.java
    │   │   │   └── AdminAccountInitializerIT.java
    │   │   └── persistence/
    │   │       └── AppUserRepositoryIT.java       # los índices únicos rechazan duplicados
    │   ├── security/
    │   │   ├── JwtServiceTest.java                # emisión, vencimiento con Clock fijo, firma alterada
    │   │   └── JsonAccessDeniedHandlerTest.java   # el 403 no se puede provocar por HTTP en esta feature
    │   └── e2e/                                   # todos los tests con MockMvc
    │       ├── AuthTestHelper.java                # registra un usuario y devuelve sus credenciales
    │       ├── AuthControllerIT.java              # HU1 y HU2: caminos felices
    │       ├── AccountControllerIT.java           # HU4, HU5 y HU6: caminos felices y vigencia de tokens
    │       ├── AccessControlIT.java               # HU3: rechazos, precedencia, rutas públicas, consola de H2 en test
    │       ├── AdminAccountIT.java                # HU7: el admin inicia sesión, regenera su clave y ve su perfil
    │       ├── AuthErrorsIT.java                  # [integración] 400, 409 y 401 del login
    │       └── AccountErrorsIT.java               # [integración] los 400 del cambio de contraseña
    └── resources/application-test.yml             # + secreto JWT de test
```

**Decisión de estructura**:

- Se usa el árbol de la constitución tal cual, sin paquetes nuevos. El modelo vive en
  `auth/modelo/` y se elimina `domain/`.
- En test se sigue la estructura base pedida: `auth/{modelo,service,persistence}` y `e2e/`.
  Se suma `security/`, porque esta feature también abarca ese paquete y `JwtService` necesita
  unitarios sin Spring.
- No se crean `catalog/`, `quotes/`, `market/` ni `architecture/` en test.
- Cada `.gitkeep` se borra en el mismo commit en que su paquete deja de estar vacío.

## Dependencias a agregar

**Pendiente**: requiere la confirmación explícita del usuario antes de tocar `build.gradle`.

```groovy
implementation 'org.springframework.boot:spring-boot-starter-security'
implementation 'io.jsonwebtoken:jjwt-api:0.13.0'
runtimeOnly 'io.jsonwebtoken:jjwt-impl:0.13.0'
runtimeOnly 'io.jsonwebtoken:jjwt-jackson:0.13.0'
implementation 'org.springdoc:springdoc-openapi-starter-webmvc-ui:3.1.1'
```

- `validation` y `actuator` ya estaban, así que no se agregan.
- No se agrega `spring-boot-starter-security-test`.
- La justificación y las fuentes están en [research.md](./research.md) (D1 y D2).

## Secuencia de implementación

Estas son las restricciones de orden para `/speckit-tasks`. Cada fase deja `./gradlew build`
en verde.

1. **Preparación** (bloquea todo lo demás):
   - Mostrar el diff de `build.gradle`, esperar la confirmación y agregar las dependencias.
   - Eliminar `domain/.gitkeep`.
   - Agregar las propiedades en los tres `application*.yml` (ver
     [contracts/configuration.md](./contracts/configuration.md)).

   Al agregar Spring Security todo queda protegido, y `contextLoads` debe seguir pasando.
2. **Modelo y persistencia**:
   - `CredentialPolicy`, `Role`, `ApiKey`, `AppUser`, `RegisteredUser`,
     `RegistrationAvailability`, `SessionToken`, los dos puertos y las seis excepciones, que
     por ahora extienden `RuntimeException`.
   - `AppUserSQL`, `AppUserSQLDAO`, `AppUserMapper` y `AppUserRepository`.
   - Tests unitarios de modelo y `AppUserRepositoryIT`.
3. **Configuración y servicios**:
   - `AuthProperties`, `JwtProperties` y `ApplicationConfig`.
   - `AuthService`, `AccountService` y `AdminAccountInitializer`.
   - Tests unitarios de servicio y `AdminAccountInitializerIT`.
4. **Seguridad**:
   - `BCryptPasswordHasher`, `JwtService`, `CredentialAuthenticationFilter`,
     `ApiErrorResponseWriter`, el entry point, el access denied handler y `SecurityConfig`,
     con la línea TEMPORAL de `/players` y la cadena de H2 solo en `local`.
   - `JwtServiceTest` y `JsonAccessDeniedHandlerTest`.
5. **Web y OpenAPI**:
   - Los DTO, `AuthController`, `AccountController` y `OpenApiConfig`.
   - Los end to end de caminos felices, de la cadena de filtros y del admin: `AuthControllerIT`,
     `AccountControllerIT`, `AccessControlIT` y `AdminAccountIT`, con `AuthTestHelper`.
6. **Integración con `shared/`**. Está **bloqueada hasta que catálogo esté mergeado en
   `develop`**. Sigue los pasos de
   [contracts/shared-integration.md](./contracts/shared-integration.md#7-pasos-de-integración-cuando-catálogo-esté-en-develop),
   incluidos `AuthErrorsIT` y `AccountErrorsIT`.
7. **Cierre y definición de terminado**: `./gradlew build`, `./gradlew bootRun` con `local`, y
   las secciones 2 a 5 de [quickstart.md](./quickstart.md): Swagger Authorize con las dos
   credenciales y la consola de H2.

Hay dos riesgos de merge con catálogo, y los dos son de bajo impacto:

- Los dos agregan claves a `application.yaml`. El bloque `futbolmarket` va al final del
  archivo y la exclusión dentro del bloque `spring` existente.
- Los dos pueden borrar `domain/.gitkeep` o `config/.gitkeep`. Dos borrados del mismo archivo
  no generan conflicto.

## Seguimiento de complejidad

Esta sección registra una desviación justificada de la regla "cada validación en su nivel".

| Desviación | Por qué hace falta | Alternativa más simple descartada |
|---|---|---|
| Las reglas de formato de las credenciales (username, correo y contraseña) se validan en el DTO y también como invariantes del modelo (`CredentialPolicy`, usado por las fábricas de `AppUser`). | FR-038 exige aplicar las mismas reglas a la configuración del administrador, que no entra por un DTO. El modelo es el único punto común a los dos canales, y además cubre el límite de 72 bytes de BCrypt, que un `@Size` en caracteres no garantiza. Las constantes son compartidas, así que las reglas no pueden divergir. | Validar la configuración del admin con Bean Validation en un record de propiedades: duplica las anotaciones en otro paquete y no cubre el límite en bytes. Validar solo en el modelo: pierde el 400 por campo (`violations`) que la constitución ubica en el DTO. |
