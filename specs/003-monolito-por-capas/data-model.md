# Modelo de datos y mapa de ubicaciones: Monolito por capas

**Rama**: `003-monolito-por-capas` | **Fecha**: 2026-10-03 | **Plan**: [plan.md](./plan.md)

## Entidades

Sin cambios. Ninguna entidad de dominio, campo, tabla, columna, índice ni regla de validación
se agrega, se quita ni se modifica. El modelo vigente está descripto en
[`specs/001-auth-usuarios/data-model.md`](../001-auth-usuarios/data-model.md) y
[`specs/002-catalogo-jugadores/data-model.md`](../002-catalogo-jugadores/data-model.md). Solo
cambia el paquete de cada clase.

Contexto asignado a cada entidad, según FR-007 y FR-008:

| Entidad / concepto | Contexto | Tabla (sin cambios) |
|---|---|---|
| `AppUser`, `ApiKey`, `Role`, `RegisteredUser` | `user` | `app_user` |
| `CredentialPolicy`, `RegistrationAvailability`, `SessionToken` y los puertos `PasswordHasher` y `SessionTokenIssuer` | `auth` | — |
| `Player`, `PlayerFilter`, `PlayerPage` | `player` | `player` |
| `Team` | `team` | `team` |
| `League` (enum) | `league` | — (columna en `team`) |
| `Position` (enum) | `position` | — (columna en `player`) |

## Mapa de ubicaciones: producción

Raíz: `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/`. Las 51 clases conservan el
nombre. Los paquetes `security/`, `config/` y `shared/` no se mueven (FR-010); solo cambian
sus imports.

### controller

| Origen | Destino |
|---|---|
| `auth/controller/AuthController` | `controller/auth/AuthController` |
| `auth/controller/dto/LoginRequest` | `controller/auth/dto/LoginRequest` |
| `auth/controller/dto/LoginResponse` | `controller/auth/dto/LoginResponse` |
| `auth/controller/dto/RegisterRequest` | `controller/auth/dto/RegisterRequest` |
| `auth/controller/dto/RegisterResponse` | `controller/auth/dto/RegisterResponse` |
| `auth/controller/AccountController` | `controller/user/AccountController` |
| `auth/controller/dto/ProfileResponse` | `controller/user/dto/ProfileResponse` |
| `auth/controller/dto/ApiKeyResponse` | `controller/user/dto/ApiKeyResponse` |
| `auth/controller/dto/ChangePasswordRequest` | `controller/user/dto/ChangePasswordRequest` |
| `catalog/controller/PlayerController` | `controller/player/PlayerController` |
| `catalog/controller/dto/PlayerResponse` | `controller/player/dto/PlayerResponse` |
| `catalog/controller/dto/PlayerPageResponse` | `controller/player/dto/PlayerPageResponse` |

### service

| Origen | Destino |
|---|---|
| `auth/service/AuthService` | `service/auth/AuthService` |
| `auth/service/AccountService` | `service/user/AccountService` |
| `auth/service/AdminAccountInitializer` | `service/user/AdminAccountInitializer` |
| `catalog/service/PlayerCatalogService` | `service/player/PlayerCatalogService` |

### modelo

| Origen | Destino |
|---|---|
| `auth/modelo/CredentialPolicy` | `modelo/auth/CredentialPolicy` |
| `auth/modelo/PasswordHasher` | `modelo/auth/PasswordHasher` |
| `auth/modelo/RegistrationAvailability` | `modelo/auth/RegistrationAvailability` |
| `auth/modelo/SessionToken` | `modelo/auth/SessionToken` |
| `auth/modelo/SessionTokenIssuer` | `modelo/auth/SessionTokenIssuer` |
| `auth/modelo/exception/InvalidCredentialsException` | `modelo/auth/exception/InvalidCredentialsException` |
| `auth/modelo/AppUser` | `modelo/user/AppUser` |
| `auth/modelo/ApiKey` | `modelo/user/ApiKey` |
| `auth/modelo/Role` | `modelo/user/Role` |
| `auth/modelo/RegisteredUser` | `modelo/user/RegisteredUser` |
| `auth/modelo/exception/DuplicateUsernameException` | `modelo/user/exception/DuplicateUsernameException` |
| `auth/modelo/exception/DuplicateEmailException` | `modelo/user/exception/DuplicateEmailException` |
| `auth/modelo/exception/DuplicateUsernameAndEmailException` | `modelo/user/exception/DuplicateUsernameAndEmailException` |
| `auth/modelo/exception/InvalidUserDataException` | `modelo/user/exception/InvalidUserDataException` |
| `auth/modelo/exception/InvalidPasswordChangeException` | `modelo/user/exception/InvalidPasswordChangeException` |
| `catalog/modelo/Player` | `modelo/player/Player` |
| `catalog/modelo/PlayerFilter` | `modelo/player/PlayerFilter` |
| `catalog/modelo/PlayerPage` | `modelo/player/PlayerPage` |
| `catalog/modelo/PlayerNotFoundException` | `modelo/player/exception/PlayerNotFoundException` |
| `catalog/modelo/CatalogInvariantException` | `modelo/player/exception/CatalogInvariantException` |
| `catalog/modelo/Team` | `modelo/team/Team` |
| `catalog/modelo/League` | `modelo/league/League` |
| `catalog/modelo/Position` | `modelo/position/Position` |

### persistence

| Origen | Destino |
|---|---|
| `auth/persistence/repository/AppUserRepository` | `persistence/repository/user/AppUserRepository` |
| `auth/persistence/mapper/AppUserMapper` | `persistence/mapper/user/AppUserMapper` |
| `auth/persistence/sql/entity/AppUserSQL` | `persistence/sql/entity/user/AppUserSQL` |
| `auth/persistence/sql/interfaces/AppUserSQLDAO` | `persistence/sql/interfaces/user/AppUserSQLDAO` |
| `catalog/persistence/repository/PlayerRepository` | `persistence/repository/player/PlayerRepository` |
| `catalog/persistence/mapper/PlayerMapper` | `persistence/mapper/player/PlayerMapper` |
| `catalog/persistence/sql/entity/PlayerSQL` | `persistence/sql/entity/player/PlayerSQL` |
| `catalog/persistence/sql/interfaces/PlayerSQLDAO` | `persistence/sql/interfaces/player/PlayerSQLDAO` |
| `catalog/persistence/repository/TeamRepository` | `persistence/repository/team/TeamRepository` |
| `catalog/persistence/mapper/TeamMapper` | `persistence/mapper/team/TeamMapper` |
| `catalog/persistence/sql/entity/TeamSQL` | `persistence/sql/entity/team/TeamSQL` |
| `catalog/persistence/sql/interfaces/TeamSQLDAO` | `persistence/sql/interfaces/team/TeamSQLDAO` |

### Otros cambios de archivos en producción

| Archivo | Acción |
|---|---|
| `shared/.gitkeep` | `git rm` (el paquete tiene clases) |
| `security/*`, `config/*`, `shared/*` | Solo imports |

## Mapa de ubicaciones: tests

Raíz: `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/`. Se mueven 13 clases de test y
un helper. Quedan en su lugar `FutbolMarketApplicationTests`, `config/`, `security/`, `shared/`
y `e2e/` (solo cambian imports).

| Origen | Destino | Prueba a |
|---|---|---|
| `auth/modelo/AppUserTest` | `modelo/user/AppUserTest` | `modelo/user/AppUser` |
| `auth/modelo/ApiKeyTest` | `modelo/user/ApiKeyTest` | `modelo/user/ApiKey` |
| `auth/modelo/CredentialPolicyTest` | `modelo/auth/CredentialPolicyTest` | `modelo/auth/CredentialPolicy` |
| `auth/modelo/RegistrationAvailabilityTest` | `modelo/auth/RegistrationAvailabilityTest` | `modelo/auth/RegistrationAvailability` |
| `auth/modelo/FakePasswordHasher` (helper) | `modelo/auth/FakePasswordHasher` | implementa `modelo/auth/PasswordHasher` |
| `catalog/modelo/PlayerTest` | `modelo/player/PlayerTest` | `modelo/player/Player` |
| `auth/service/AuthServiceTest` | `service/auth/AuthServiceTest` | `service/auth/AuthService` |
| `auth/service/AccountServiceTest` | `service/user/AccountServiceTest` | `service/user/AccountService` |
| `auth/service/AdminAccountInitializerTest` | `service/user/AdminAccountInitializerTest` | `service/user/AdminAccountInitializer` |
| `auth/service/AdminAccountInitializerIT` | `service/user/AdminAccountInitializerIT` | `service/user/AdminAccountInitializer` |
| `catalog/service/PlayerCatalogServiceTest` | `service/player/PlayerCatalogServiceTest` | `service/player/PlayerCatalogService` |
| `auth/persistence/AppUserRepositoryIT` | `persistence/repository/user/AppUserRepositoryIT` | `persistence/repository/user/AppUserRepository` |
| `catalog/persistence/PlayerRepositoryIT` | `persistence/repository/player/PlayerRepositoryIT` | `persistence/repository/player/PlayerRepository` |
| `catalog/controller/PlayerControllerIT` | `e2e/PlayerControllerIT` | MockMvc sobre `/players` |

`AuthTestHelper` ya está en `e2e/` y no se mueve. `FakePasswordHasher` lo usan
`AppUserTest`, `AuthServiceTest`, `AccountServiceTest` y `AdminAccountInitializerTest`, que lo
importan desde `modelo/auth/` (supuesto del spec).

Total de clases de test después del movimiento: 25, las mismas que en la línea base.

## Paquetes que dejan de existir

`auth/` y `catalog/`, en `src/main` y en `src/test`, con todas sus subcarpetas. Al terminar no
queda ningún archivo ni carpeta bajo esas rutas.
