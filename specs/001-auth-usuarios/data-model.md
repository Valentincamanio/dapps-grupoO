# Modelo de datos: Registro, credenciales y acceso autenticado

**Rama**: `001-auth-usuarios` | **Fecha**: 2026-09-18 | **Plan**: [plan.md](./plan.md)

Todos los tipos del modelo viven en `auth/modelo/` y sus subpaquetes, y son Java puro: no
llevan anotaciones de JPA ni importan `jakarta.persistence`, `org.springframework` ni nada del
paquete `controller`. Lo que necesita infraestructura se resuelve con puertos, que son
interfaces del modelo implementadas en `security/`.

```text
                   auth/modelo (Java puro)
 ┌──────────────────────────────────────────────────────────────┐
 │ AppUser ─── Role                                             │
 │    │  usa ─► PasswordHasher (puerto)  ◄── security/BCryptPasswordHasher
 │    │  emite ► ApiKey (objeto de valor, SHA-256 con JDK)      │
 │    └ register(...) ─► RegisteredUser(AppUser, ApiKey)        │
 │ RegistrationAvailability ─► excepciones Duplicate*           │
 │ SessionTokenIssuer (puerto) ─► SessionToken ◄── security/JwtService
 │ CredentialPolicy (reglas de formato y constantes)            │
 └──────────────────────────────────────────────────────────────┘
          ▲ toDomain / toSQL
 auth/persistence: AppUserRepository ─► AppUserMapper ─► AppUserSQLDAO ─► AppUserSQL (app_user)
```

---

## Modelo

### AppUser

Es la persona registrada y la titular de las dos credenciales. Sus reglas viven acá y no en
el servicio.

| Campo | Tipo | Reglas |
|---|---|---|
| `id` | `Long` | `null` hasta que se persiste. Lo asigna la persistencia. |
| `username` | `String` | Se guarda tal como lo escribió el usuario, recortado. Cumple `CredentialPolicy`: de 3 a 30 caracteres, solo `[A-Za-z0-9_]`. Es único sin distinguir mayúsculas. |
| `email` | `String` | Se guarda tal como se escribió, recortado. Tiene formato válido y a lo sumo 254 caracteres. Es único sin distinguir mayúsculas. |
| `passwordHash` | `String` | Hash BCrypt de la contraseña (60 caracteres). Nunca se guarda la contraseña en claro (FR-008). |
| `apiKeyHash` | `String` | SHA-256 en hexadecimal de la clave vigente (64 caracteres). Es `null` si el usuario no tiene clave, como el administrador recién creado (FR-034). |
| `role` | `Role` | Lo asigna la fábrica. No cambia en esta feature. |
| `balance` | `BigDecimal` | Escala 2 y siempre mayor o igual a 0. **No tiene setter**: solo lo fijan las fábricas. |

**Fábricas**. No hay constructores públicos con estado arbitrario.

| Fábrica | Resultado | Reglas |
|---|---|---|
| `register(username, email, rawPassword, initialBalance, PasswordHasher)` | `RegisteredUser` | Valida las invariantes de formato, hashea la contraseña, asigna `Role.USER` y el saldo inicial normalizado a escala 2, y **emite la clave de API** (FR-010, FR-012 y FR-013). |
| `createAdmin(username, email, rawPassword, PasswordHasher)` | `AppUser` | Valida las mismas invariantes, hashea la contraseña, asigna `Role.ADMIN` y saldo `0.00`, y **no emite clave** (FR-040 a FR-042). |
| `reconstitute(id, username, email, passwordHash, apiKeyHash, role, balance)` | `AppUser` | Reconstruye el estado leído de la base. Solo la usa `AppUserMapper.toDomain`. No valida formato: el estado persistido ya fue validado al crearse. |

**Comportamiento**:

| Método | Efecto | Excepciones |
|---|---|---|
| `issueApiKey()` | Genera una `ApiKey` nueva, reemplaza `apiKeyHash` y devuelve la clave (el valor en claro existe solo en ese objeto). La clave anterior deja de valer en el acto (FR-032 a FR-034). | — |
| `verifyPassword(rawPassword, hasher)` | Verifica la contraseña contra `passwordHash`. | `InvalidCredentialsException` |
| `changePassword(currentPassword, newPassword, hasher)` | Hace tres controles, en este orden: (1) que la actual coincida; (2) que la nueva sea distinta de la actual; (3) que la nueva cumpla `CredentialPolicy`. Si pasa los tres, reemplaza `passwordHash`. Si algo falla, no modifica nada (FR-027 a FR-031). | `InvalidPasswordChangeException` |

- **Lectura**: `getId`, `getUsername`, `getEmail`, `getRole` y `getBalance`. `getPasswordHash`
  y `getApiKeyHash` existen solo para el mapper; ninguna respuesta HTTP los usa.
- **`toString`**: no incluye ninguno de los dos hashes.
- **Diseño para la entrega 2**: el saldo solo va a cambiar a través de métodos de `AppUser`
  (debitar al comprar, acreditar al vender). Nada fuera de `AppUser` puede modificarlo.

### CredentialPolicy

Es la única fuente de verdad de las reglas de formato. Es una clase final con constructor
privado, constantes y predicados puros:

- `isValidUsername(String)`
- `isValidEmail(String)`
- `isValidPassword(String)`

`AppUser` usa los predicados para decidir qué excepción lanzar. Los DTO de request usan las
constantes en sus anotaciones de Bean Validation, así el DTO y el modelo no pueden divergir.

| Constante | Valor | Requisito |
|---|---|---|
| `USERNAME_MIN_LENGTH` | `3` | FR-002 |
| `USERNAME_MAX_LENGTH` | `30` | FR-002 |
| `USERNAME_PATTERN` | `^[A-Za-z0-9_]+$` | FR-002 (sin acentos, espacios ni guiones medios) |
| `EMAIL_MAX_LENGTH` | `254` | FR-003. El largo máximo de la RFC 5321 evita un error de base por valores largos. |
| `EMAIL_PATTERN` | `^[^@\s]+@[^@\s]+\.[^@\s]+$` | FR-003 |
| `PASSWORD_MIN_LENGTH` | `8` | FR-004 y FR-029 |
| `PASSWORD_MAX_LENGTH` | `72` | Límite en caracteres que se aplica en el DTO. |
| `PASSWORD_MAX_BYTES` | `72` | Límite en bytes UTF-8 que se aplica en el modelo. Es el límite de BCrypt (ver [research.md](./research.md), D5). |
| `BALANCE_SCALE` | `2` | Escala del saldo. |

### Role

Enum con dos valores:

- `USER`: usuario común. Es el único rol que otorga el registro público (FR-010 y FR-011).
- `ADMIN`: administrador. Existe solo por el alta de arranque (FR-036).

Se persiste como texto. En Spring Security se traduce a las autoridades `ROLE_USER` y
`ROLE_ADMIN`.

### ApiKey (objeto de valor)

| Elemento | Detalle |
|---|---|
| `generate()` | Genera 32 bytes con `SecureRandom` y los codifica en Base64URL sin padding: 43 caracteres. |
| `value()` | El valor en claro. Se entrega una única vez, en la respuesta que lo emite (FR-014). |
| `hash()` y `hashOf(String)` | SHA-256 en hexadecimal en minúsculas (64 caracteres). Es determinístico, así que permite buscar por igualdad. |
| `toString()` | Enmascara el valor. |

### RegisteredUser

Record `(AppUser user, ApiKey apiKey)`. Es el resultado del registro público: el usuario creado
más la clave que se muestra una sola vez. El servicio lo devuelve tras persistir, con el
`AppUser` ya con id.

### RegistrationAvailability

Record `(boolean usernameTaken, boolean emailTaken)`. Su método `ensureAvailable()` decide qué
conflicto informar (FR-007):

| `usernameTaken` | `emailTaken` | Resultado |
|---|---|---|
| no | no | Sigue el registro. |
| sí | no | `DuplicateUsernameException` |
| no | sí | `DuplicateEmailException` |
| sí | sí | `DuplicateUsernameAndEmailException` |

### SessionToken

Record `(String value, Instant expiresAt)`. `expiresAt` es la emisión más la duración
configurada (24 horas por defecto, FR-016).

### Puertos

| Puerto | Operaciones | Implementación |
|---|---|---|
| `PasswordHasher` | `String hash(String plainText)` y `boolean matches(String plainText, String hash)` | `security/BCryptPasswordHasher`, que envuelve al `PasswordEncoder` BCrypt de Spring Security. |
| `SessionTokenIssuer` | `SessionToken issueFor(AppUser user)` | `security/JwtService`: HS256, `sub` igual al id y `exp` igual a `iat` más la duración. |

### Excepciones de dominio

Viven en `auth/modelo/exception/`. Hasta la integración con `shared/` extienden `RuntimeException`;
después, la clase base de `shared/` (ver [contracts/shared-integration.md](./contracts/shared-integration.md)).
Los mensajes están en español.

| Excepción | Status HTTP | Mensaje | La lanza |
|---|---|---|---|
| `DuplicateUsernameException` | 409 | El nombre de usuario ya está registrado. | `RegistrationAvailability` o el repository (índice único) |
| `DuplicateEmailException` | 409 | El correo electrónico ya está registrado. | `RegistrationAvailability` o el repository (índice único) |
| `DuplicateUsernameAndEmailException` | 409 | El nombre de usuario y el correo electrónico ya están registrados. | `RegistrationAvailability` |
| `InvalidCredentialsException` | 401 | Credenciales inválidas. | `AppUser.verifyPassword`; el servicio, si el usuario no existe |
| `InvalidPasswordChangeException` | 400 | Uno de tres, según el caso: "La contraseña actual es incorrecta.", "La nueva contraseña debe ser distinta de la actual." o "La nueva contraseña no cumple las reglas de formato." | `AppUser.changePassword` |
| `InvalidUserDataException` | 400 | El mensaje de la regla incumplida, sin repetir el valor recibido. | Fábricas de `AppUser` |

`InvalidCredentialsException` usa el mismo mensaje para un usuario inexistente y para una
contraseña incorrecta (FR-017 y SC-007).

---

## Persistencia

### AppUserSQL → tabla `app_user`

Está en `auth/persistence/sql/entity/`. Es la única clase con anotaciones de JPA de esta
feature y no tiene relaciones JPA con otras entidades.

| Columna | Tipo | Nulo | Mapeo |
|---|---|---|---|
| `id` | `BIGINT` | no | `@Id @GeneratedValue(strategy = IDENTITY)` |
| `username` | `VARCHAR(30)` | no | `@Column(nullable = false, length = 30)` |
| `email` | `VARCHAR(254)` | no | `@Column(nullable = false, length = 254)` |
| `password_hash` | `VARCHAR(60)` | no | `@Column(name = "password_hash", nullable = false, length = 60)` |
| `api_key_hash` | `VARCHAR(64)` | sí | `@Column(name = "api_key_hash", length = 64)` |
| `role` | `VARCHAR(20)` | no | `@Enumerated(EnumType.STRING)`, nunca `ORDINAL` |
| `balance` | `DECIMAL(19,2)` | no | `@Column(nullable = false, precision = 19, scale = 2)` |

`@Table(name = "app_user")` es obligatorio porque `user` es palabra reservada en PostgreSQL.
Los índices únicos se declaran explícitamente en `@Table(indexes = ...)`:

| Índice | Columna | Consultado en |
|---|---|---|
| `ux_app_user_username` | `username` | Registro (disponibilidad) y login |
| `ux_app_user_email` | `email` | Registro (disponibilidad) |
| `ux_app_user_api_key_hash` | `api_key_hash` | Cada request autenticado con clave de API |

Los índices únicos admiten varios `NULL` en `api_key_hash`. Las búsquedas por username y
correo ignoran mayúsculas, por la limitación aceptada de [research.md](./research.md) (D9).

### AppUserSQLDAO

Está en `auth/persistence/sql/interfaces/` y extiende `JpaRepository<AppUserSQL, Long>`. Solo
usa derived queries, sin `@Query` nativo:

- `Optional<AppUserSQL> findByUsernameIgnoreCase(String username)`
- `Optional<AppUserSQL> findByApiKeyHash(String apiKeyHash)`
- `boolean existsByUsernameIgnoreCase(String username)`
- `boolean existsByEmailIgnoreCase(String email)`

### AppUserMapper

Está en `auth/persistence/mapper/`. Traduce campo a campo en las dos direcciones y no contiene
lógica de negocio:

- `toDomain(AppUserSQL)` llama a `AppUser.reconstitute(...)`.
- `toSQL(AppUser)` copia los siete campos. Si el id es `null`, JPA inserta; si no, actualiza.

### AppUserRepository

Está en `auth/persistence/repository/`. Es el único punto donde conviven el modelo y la
persistencia: recibe y devuelve `AppUser` y por dentro usa `AppUserSQLDAO` y `AppUserMapper`.
El servicio no ve ninguno de los dos, ni `AppUserSQL`.

| Método | Comportamiento |
|---|---|
| `AppUser save(AppUser)` | Mapea, hace `saveAndFlush` y devuelve el `AppUser` persistido. Si el flush viola un índice único, traduce la excepción según el nombre del índice (`ux_app_user_username` → `DuplicateUsernameException`, `ux_app_user_email` → `DuplicateEmailException`) y relanza cualquier otra. |
| `Optional<AppUser> findById(Long)` | Búsqueda por clave primaria. La usa la resolución del JWT. |
| `Optional<AppUser> findByUsername(String)` | Ignora mayúsculas. La usa el login. |
| `Optional<AppUser> findByApiKeyHash(String)` | Igualdad exacta sobre el hash. La usa la resolución de la clave de API. |
| `boolean existsByUsername(String)` | Ignora mayúsculas. La usan el registro y el alta del admin. |
| `boolean existsByEmail(String)` | Ignora mayúsculas. La usan el registro y el alta del admin. |

---

## Transiciones de estado

```text
                 register (registro público)
 (no existe) ──────────────────────────────────► USER, saldo = inicial, clave vigente K1
 (no existe) ──────────────────────────────────► ADMIN, saldo = 0.00, sin clave
                 createAdmin (alta de arranque)

 Clave de API:   sin clave ──issueApiKey──► K1 ──issueApiKey──► K2 ──issueApiKey──► K3
                 (cada emisión invalida la anterior en el acto: a lo sumo una vigente)

 Contraseña:     P1 ──changePassword(P1, P2)──► P2
                 (se rechaza si la actual no coincide, si la nueva es igual a la actual
                  o si la nueva no cumple el formato; en todos esos casos nada cambia)

 Rol y saldo:    no tienen transiciones en esta feature (solo se crean y se leen).
 Tokens de sesión: no son estado del usuario. Valen hasta su vencimiento aunque cambien
                 la contraseña o la clave (FR-035).
```

## Trazabilidad con el spec

| Requisitos | Elemento del modelo |
|---|---|
| FR-001 a FR-004 y FR-029 | `CredentialPolicy`, las anotaciones de los DTO y las invariantes de las fábricas |
| FR-005 a FR-007 | `RegistrationAvailability`, los índices únicos y la traducción en `AppUserRepository` |
| FR-008, FR-009 y FR-040 | `passwordHash` con BCrypt vía `PasswordHasher`. Ningún DTO de respuesta lleva la contraseña ni su hash. |
| FR-010 a FR-012 y FR-042 | `AppUser.register` y `AppUser.createAdmin` (rol y saldo) |
| FR-013, FR-014 y FR-032 a FR-034 | `ApiKey`, `issueApiKey` y `apiKeyHash` nullable |
| FR-015 a FR-018 y FR-035 | `verifyPassword`, `SessionTokenIssuer` y `SessionToken` (`sub` igual al id) |
| FR-027 a FR-031 | `AppUser.changePassword` |
| FR-036 a FR-041 | `AppUser.createAdmin` y `AdminAccountInitializer` (ver [research.md](./research.md), D12) |
