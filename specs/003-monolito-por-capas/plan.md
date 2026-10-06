# Plan de implementación: Monolito por capas

**Rama**: `003-monolito-por-capas` | **Fecha**: 2026-10-03 | **Especificación**: [spec.md](./spec.md)

**Entrada**: especificación de la feature en `/specs/003-monolito-por-capas/spec.md` y el pedido
de planificación del 2026-10-03.

## Resumen

Refactor estructural sin cambios funcionales. Se mueven las 51 clases de `auth/` y `catalog/`
al árbol `<capa>/<contexto>/` del Principio I de la constitución 2.0.0, y los 14 archivos de
test que quedan en paquetes que desaparecen. La API, la base, la configuración y la build no
cambian.

Enfoque técnico:

- **Movimiento**: `git mv` archivo por archivo, para conservar el historial. En el mismo commit
  se cambian la línea `package` y los imports. El mapa clase por clase está en
  [data-model.md](./data-model.md).
- **Orden**: `modelo` → `persistence` → `service` → `controller` → tests, compilando después
  de cada capa ([research.md](./research.md) D3).
- **Contextos**: `auth/` se reparte entre `user` y `auth` según FR-008. `catalog/` se reparte
  entre `player`, `team`, `league` y `position` según FR-007. Las excepciones sueltas de
  `catalog/modelo/` pasan a `modelo/player/exception/`.
- **Documentación**: se actualizan dos comentarios que nombran paquetes viejos o dejan de
  resolver, la sección Estructura del `README.md`, y se agrega una nota de estructura histórica
  en los planes de las features 001 y 002.

## Decisiones de planificación (2026-10-03)

- **Árbol con contexto, no plano.** La tabla de movimientos del pedido (`auth/controller/*` →
  `controller/`, `auth/modelo/exception/*` → `modelo/exception/`, etc.) se aplica como mapeo de
  capas, y dentro de cada capa se agrega el contexto. El usuario lo confirmó, porque los
  paquetes planos violarían el Principio I (NO NEGOCIABLE) y FR-005, FR-007, FR-008 y FR-009.
- **Tests de persistencia**: `*/persistence/*IT` → `persistence/repository/<contexto>/`,
  porque prueban el `*Repository` y el Principio IV pide replicar capa y contexto.
- **`FakePasswordHasher`** → test `modelo/auth/`, junto al puerto que implementa (supuesto del
  spec).
- **`PlayerControllerIT`** → `e2e/`, porque usa MockMvc y su paquete desaparece (FR-017).
- **Sin nombres de clase repetidos** entre `auth/` y `catalog/` (verificado por el usuario).
  Por eso no cambian nombres de beans, de entidades JPA ni de esquemas OpenAPI
  ([research.md](./research.md) D8).
- **Reemplazos de texto** solo sobre `ar.edu.unq.desapp.futbolmarket.auth.` y
  `...catalog.`, nunca sobre `futbolmarket.auth`, que también es prefijo de propiedades
  ([research.md](./research.md) D4).

## Contexto técnico

**Lenguaje/versión**: Java 21 (Temurin), toolchain de Gradle.

**Dependencias principales**: Spring Boot 4.1.1 (webmvc, data-jpa, security, validation,
actuator), jjwt, springdoc-openapi 3.1.1, Lombok. Sin dependencias nuevas, sin cambios en
`build.gradle` ni en los `application*.yml` (FR-014).

**Almacenamiento**: H2. Perfil `local` en archivo con `ddl-auto: update`, perfil `test` en
memoria. Tablas y columnas sin cambios (FR-004).

**Testing**: JUnit 5, Mockito, AssertJ, MockMvc, `@SpringBootTest` y `@DataJpaTest`. Línea
base: 212 tests en 25 clases, 0 fallidos, 0 omitidos (commit `dc0be4d`).

**Plataforma objetivo**: servidor local con `./gradlew bootRun`, sin Docker.

**Tipo de proyecto**: servicio web (monorepo `backend/` + `frontend/` vacío).

**Objetivos de rendimiento**: no aplica, el comportamiento en ejecución no cambia.

**Restricciones**:

- Solo cambian `package`, imports y ubicación (FR-013), más los dos comentarios de
  [research.md](./research.md) D6.
- En los tests, solo `package` e imports (FR-016, Principio IV).
- Un único PR contra `develop` (clarificación del spec).

**Escala/alcance**: 51 clases de producción y 14 archivos de test movidos, 11 archivos de
`security/`, `config/`, `shared/` y `e2e/` con imports actualizados, 1 `.gitkeep` borrado y 4
archivos de documentación.

## Verificación constitucional

*Puerta: tiene que pasar antes de la investigación y se revisa de nuevo después del diseño.*

### Antes de la investigación

| Principio | Resultado | Evidencia |
|---|---|---|
| I. Capas estrictas (NO NEGOCIABLE) | Cumple | El objetivo de la feature es llevar el código al árbol del Principio I. Ver el detalle debajo de la tabla. |
| II. Modelo rico | Cumple | No cambia la lógica: las decisiones siguen en `AppUser`, `RegistrationAvailability`, `Player`, `PlayerFilter`, etc. |
| III. Validación en su nivel | Cumple | Las excepciones conservan nombre y jerarquía (`shared/BadRequestException`, `ConflictException`, etc.). El advice único sigue en `shared/`. |
| IV. Tests (NO NEGOCIABLE) | Cumple | Se conservan los 25 tests. Solo se mueven los 13 cuyo paquete desaparece, más un helper, cambiando solo `package` e imports. Replican capa y contexto. `PlayerControllerIT` va a `e2e/`. Los de `config/`, `security/`, `shared/` y `FutbolMarketApplicationTests` no se mueven. |
| V. Calidad medible | Cumple | Sin código nuevo. Los imports se agregan solo donde se usan. Se verifica en `main` después del merge (SC-007). |
| VI. Persistencia explícita | Cumple | `@Table`, `@Column` y JPQL sin cambios. El nombre de entidad no cambia porque el nombre de clase no cambia. |
| VII. Idioma | Cumple | Contextos en inglés y singular (`user`, `auth`, `player`, `team`, `league`, `position`). La capa `modelo/` conserva su nombre. Documentación en español. |
| Stack tecnológico | Cumple | Sin dependencias nuevas. `build.gradle` y `application*.yml` intactos. |
| Definición de terminado | Cumple | `./gradlew build`, `bootRun` con `local` y Swagger con los mismos 7 endpoints de negocio ([quickstart.md](./quickstart.md)). |

Detalle del principio I:

- **No queda ningún paquete `auth/` ni `catalog/`** de primer nivel, ni en `src/main` ni en
  `src/test`. Bajo la raíz solo quedan `controller`, `service`, `modelo`, `persistence`,
  `security`, `config`, `shared` y `FutbolMarketApplication`. En tests quedan además `e2e/` y
  los espejos de capa. Se comprueba con los pasos 2 y 4 de [quickstart.md](./quickstart.md).
  `auth` sigue existiendo solo como **contexto** dentro de una capa (`controller/auth/`,
  `service/auth/`, `modelo/auth/`), como lo prevé la constitución.
- Toda excepción de dominio queda en `modelo/<contexto>/exception/`, incluidas
  `PlayerNotFoundException` y `CatalogInvariantException`.
- Los contextos aparecen solo en las capas donde tienen clases: `league` y `position` solo en
  `modelo/`, `team` en `modelo/` y `persistence/`, y `auth` en `controller/`, `service/` y
  `modelo/`, sin persistencia. No se crean carpetas vacías y se borra `shared/.gitkeep`.
- La dirección de dependencias no cambia: el controller solo habla con el servicio, los DTO no
  cruzan, y el mapper lo invoca `repository/`.
- Las referencias entre contextos (`AppUser` → `PasswordHasher`, `PlayerSQL` → `TeamSQL`,
  `PlayerMapper` → `TeamMapper`) están permitidas por las reglas de monolito y no forman ciclos
  entre clases ([research.md](./research.md) D5).
- No se escriben contratos de integración entre contextos. El de la feature 001 se marca como
  obsoleto.

### Después del diseño

Sin cambios respecto de la evaluación anterior. El diseño confirmó que:

- Ninguna clase depende de acceso de paquete, así que el reparto entre contextos no obliga a
  cambiar modificadores de visibilidad ([research.md](./research.md) D5).
- El arranque no tiene escaneos ni propiedades por paquete ([research.md](./research.md) D7).
- El único desvío es el de los dos comentarios de producción, registrado en Seguimiento de
  complejidad.

Resultado: **la puerta pasa**.

## Estructura del proyecto

### Documentación de la feature

```text
specs/003-monolito-por-capas/
├── spec.md
├── plan.md                    # este archivo
├── research.md                # decisiones D1 a D11
├── data-model.md              # entidades (sin cambios) y mapa de ubicaciones clase por clase
├── quickstart.md              # validación: estructura, historial, tests, arranque, Swagger
├── contracts/
│   └── api-sin-cambios.md     # la API vigente es la de 001 y 002; cómo se verifica
├── checklists/
│   └── requirements.md
└── tasks.md                   # lo genera /speckit-tasks
```

### Código fuente

Árbol de producción después del cambio, en `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/`:

```text
FutbolMarketApplication.java
controller/
├── auth/            AuthController
│   └── dto/         LoginRequest, LoginResponse, RegisterRequest, RegisterResponse
├── user/            AccountController
│   └── dto/         ProfileResponse, ApiKeyResponse, ChangePasswordRequest
└── player/          PlayerController
    └── dto/         PlayerResponse, PlayerPageResponse
service/
├── auth/            AuthService
├── user/            AccountService, AdminAccountInitializer
└── player/          PlayerCatalogService
modelo/
├── auth/            CredentialPolicy, PasswordHasher, RegistrationAvailability,
│   │                SessionToken, SessionTokenIssuer
│   └── exception/   InvalidCredentialsException
├── user/            AppUser, ApiKey, Role, RegisteredUser
│   └── exception/   DuplicateUsernameException, DuplicateEmailException,
│                    DuplicateUsernameAndEmailException, InvalidUserDataException,
│                    InvalidPasswordChangeException
├── player/          Player, PlayerFilter, PlayerPage
│   └── exception/   PlayerNotFoundException, CatalogInvariantException
├── team/            Team
├── league/          League
└── position/        Position
persistence/
├── repository/
│   ├── user/        AppUserRepository
│   ├── player/      PlayerRepository
│   └── team/        TeamRepository
├── mapper/
│   ├── user/        AppUserMapper
│   ├── player/      PlayerMapper
│   └── team/        TeamMapper
└── sql/
    ├── entity/
    │   ├── user/    AppUserSQL
    │   ├── player/  PlayerSQL
    │   └── team/    TeamSQL
    └── interfaces/
        ├── user/    AppUserSQLDAO
        ├── player/  PlayerSQLDAO
        └── team/    TeamSQLDAO
security/            sin mover; solo imports
config/              sin mover; solo imports
shared/              sin mover; solo imports; se borra .gitkeep
```

Árbol de tests después del cambio, en `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/`:

```text
FutbolMarketApplicationTests.java     sin mover
modelo/
├── auth/        CredentialPolicyTest, RegistrationAvailabilityTest, FakePasswordHasher
├── user/        AppUserTest, ApiKeyTest
└── player/      PlayerTest
service/
├── auth/        AuthServiceTest
├── user/        AccountServiceTest, AdminAccountInitializerTest, AdminAccountInitializerIT
└── player/      PlayerCatalogServiceTest
persistence/
└── repository/
    ├── user/    AppUserRepositoryIT
    └── player/  PlayerRepositoryIT
e2e/             PlayerControllerIT (movido) + los 7 archivos existentes (3 cambian imports)
config/          PlayerCatalogDataSeederIT, solo imports
security/        JwtServiceTest, JsonAccessDeniedHandlerTest, solo imports
shared/          GlobalExceptionHandlerIT, GlobalExceptionHandlerTest, solo imports
```

**Decisión de estructura**: monorepo `backend/` + `frontend/`, sin cambios. Dentro de
`backend/` se aplica el árbol del Principio I. El origen y el destino de cada archivo están en
[data-model.md](./data-model.md).

### Archivos que solo cambian imports

| Archivo | Imports que cambian |
|---|---|
| `security/BCryptPasswordHasher` | `PasswordHasher` |
| `security/JwtService` | `AppUser`, `SessionToken`, `SessionTokenIssuer` |
| `security/CredentialAuthenticationFilter` | `AppUser`, `AuthService` |
| `security/SecurityConfig` | `AuthService` |
| `config/PlayerCatalogDataSeeder` | `Player`, `Team`, `League`, `Position`, `PlayerRepository`, `TeamRepository` |
| test `security/JwtServiceTest` | `AppUser`, `Role`, `SessionToken` |
| test `config/PlayerCatalogDataSeederIT` | `PlayerRepository`, `TeamRepository`, `PlayerSQLDAO`, `TeamSQLDAO` |
| test `shared/GlobalExceptionHandlerIT` | `PlayerNotFoundException` |
| test `e2e/AuthControllerIT`, `AccountControllerIT`, `AdminAccountIT` | `Role` |

Más los imports internos de las clases movidas. El compilador encuentra cualquier otro caso.

### Documentación del repo

| Archivo | Cambio |
|---|---|
| `README.md`, sección **Estructura** | Agregar el árbol de `backend/` por capa (`controller`, `service`, `modelo`, `persistence`, `security`, `config`, `shared`), con los contextos vigentes y una referencia al Principio I de la constitución. Las líneas de `backend/` y `frontend/` se conservan. |
| `specs/001-auth-usuarios/plan.md` | Al principio, antes del título, la nota: "> **Estructura de paquetes histórica**: reemplazada por la constitución 2.0.0 (ver [specs/003](../003-monolito-por-capas/plan.md))." El resto no se reescribe. |
| `specs/002-catalogo-jugadores/plan.md` | La misma nota. |
| `specs/001-auth-usuarios/contracts/shared-integration.md` | Al principio, la marca: "> **OBSOLETO**: la constitución 2.0.0 prohíbe los contratos de integración entre contextos y entre features (ver [specs/003](../../003-monolito-por-capas/plan.md)). Se conserva como registro histórico." |

`specs/002-catalogo-jugadores/contracts/` no tiene `shared-integration.md`, así que en 002 solo
cambia el plan.

### Comentarios de código

Detalle y motivo en [research.md](./research.md) D6:

- `modelo/player/exception/CatalogInvariantException`: "de auth" → "del contexto `user`".
- `modelo/auth/CredentialPolicy`: `{@link AppUser}` → nombre calificado
  `{@link ar.edu.unq.desapp.futbolmarket.modelo.user.AppUser}`.

## Secuencia de implementación

Cada paso termina con `./gradlew compileJava compileTestJava` en verde, salvo el último, que
corre la build completa.

1. **Línea base**: medir tests y guardar `/v3/api-docs` ([quickstart.md](./quickstart.md),
   paso 1).
2. **modelo**: crear las carpetas destino, hacer `git mv` de las 23 clases, actualizar
   `package`, agregar los imports entre contextos (research D5) y actualizar los imports en todo
   `src/main` y `src/test`.
3. **persistence**: mover las 12 clases y actualizar imports.
4. **service**: mover las 4 clases y actualizar imports.
5. **controller**: mover las 12 clases y actualizar imports. Borrar las carpetas `auth/` y
   `catalog/` de `src/main`, que deberían quedar vacías.
6. **tests**: mover los 14 archivos según [data-model.md](./data-model.md), actualizar solo
   `package` e imports, y borrar las carpetas `auth/` y `catalog/` de `src/test`.
7. **Limpieza**: `git rm` de `shared/.gitkeep` y los dos comentarios de research D6.
8. **Documentación**: `README.md`, notas en los planes 001 y 002 y marca de obsoleto en
   `shared-integration.md`.
9. **Validación**: [quickstart.md](./quickstart.md), pasos 2 a 8.

## Seguimiento de complejidad

| Desvío | Por qué hace falta | Alternativa más simple descartada |
|---|---|---|
| Dos comentarios de producción cambian, aunque FR-013 limita los cambios a `package`, imports y ubicación. | El usuario pidió actualizar los comentarios que nombran paquetes viejos. El `{@link AppUser}` de `CredentialPolicy` dejaría de resolverse al separar `auth` de `user`. Ninguno toca lógica. | Dejar los comentarios: el Javadoc quedaría apuntando a un paquete que no existe y con un link roto. |
