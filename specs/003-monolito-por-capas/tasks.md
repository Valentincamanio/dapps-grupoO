---

description: "Tareas ejecutables para la reestructuración del backend en monolito por capas"
---

# Tasks: Monolito por capas

**Input**: Documentos de diseño en `specs/003-monolito-por-capas/`

**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`, `contracts/api-sin-cambios.md` y `quickstart.md`

**Tests**: No se escriben tests nuevos (el spec lo deja fuera de alcance). Las tareas de verificación corren la suite existente, comparan la línea base y revisan diffs, porque los tests son la evidencia de que el refactor no cambió el comportamiento (Principio IV).

**Organization**: Es un refactor. El movimiento del código de producción es un prerrequisito de todas las historias, por eso va en la fase Foundational, una capa por vez (research D3). Cada historia agrega después lo que le es propio y su verificación independiente.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Puede correr en paralelo (archivos distintos, sin dependencias pendientes)
- **[Story]**: Historia del spec a la que pertenece (US1, US2, US3, US4)

## Path Conventions

- Producción: `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/`
- Tests: `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/`
- Paquete raíz Java: `ar.edu.unq.desapp.futbolmarket`
- Los comandos de Gradle se corren desde `backend/` con Git Bash. `$BASE` es una carpeta temporal **fuera del repo** (por ejemplo, el scratchpad) para guardar la línea base y los scripts de reemplazo.
- El origen y el destino de cada archivo están en `data-model.md`. Si una tarea y el data-model no coinciden, manda el data-model.

## Reglas para todas las tareas de movimiento

1. Crear la carpeta destino con `mkdir -p` antes del `git mv` (`git mv` no crea carpetas intermedias).
2. Mover cada archivo con `git mv origen destino`, nunca con el refactor de paquetes del IDE ni borrando y creando (research D2).
3. En cada archivo movido, reemplazar solo la línea `package` por la del paquete destino, por ejemplo: `sed -i 's/^package .*;/package ar.edu.unq.desapp.futbolmarket.modelo.user;/' <archivos>`.
4. Reescribir los imports **clase por clase** y con el nombre totalmente calificado, por ejemplo `s/ar\.edu\.unq\.desapp\.futbolmarket\.auth\.modelo\.AppUser\b/ar.edu.unq.desapp.futbolmarket.modelo.user.AppUser/g`. No reemplazar por prefijo de paquete, porque `auth/` se reparte entre `user` y `auth`. **Nunca** tocar `futbolmarket.auth` a secas: es el prefijo de las propiedades `futbolmarket.auth.*` en `config/AuthProperties`, `application*.yml` y los tests `AdminAccountInitializerIT`, `AdminAccountIT` y `AuthErrorsIT` (research D4).
5. Aplicar los reemplazos de imports sobre `backend/src/main/java` y `backend/src/test/java` a la vez (`grep -rl <patrón> | xargs sed -i -f <script>`).
6. No cambiar nada más: ni lógica, ni nombres, ni orden de imports existentes, ni finales de línea (FR-012, FR-013, FR-016). Si el compilador reclama un símbolo, se resuelve agregando un import, nunca editando el código.

---

## Phase 1: Setup (línea base)

**Purpose**: Medir el punto de partida para poder comparar al final (research D10).

- [X] T001 Confirmar que la rama `003-monolito-por-capas` está rebaseada sobre la punta de `develop` y que el working tree está limpio (`git fetch`, `git status`, `git log --oneline develop..HEAD` solo debe mostrar commits de documentación de `specs/003-monolito-por-capas/`)
- [X] T002 Medir la línea base de tests: correr `./gradlew clean test` en `backend/` y contar archivos, tests, omitidos y fallidos sobre `backend/build/test-results/test/TEST-*.xml` con los comandos del paso 1 de `specs/003-monolito-por-capas/quickstart.md`. Guardar el resultado en `$BASE/linea-base.txt`. Esperado: 25 archivos, `tests=212 skipped=0 failed=0`; si difiere, los valores medidos pasan a ser la línea base
- [X] T003 Guardar el documento OpenAPI de antes: levantar `./gradlew bootRun` (perfil `local`) en `backend/`, ejecutar `curl -s http://localhost:8080/v3/api-docs > "$BASE/api-docs-antes.json"` y detener la aplicación
- [X] T004 [P] Confirmar que no hay referencias por nombre de paquete fuera del código Java: `grep -rnE "futbolmarket\.(auth|catalog)" backend/build.gradle backend/src/main/resources backend/src/test/resources .github` solo debe mostrar claves de propiedades `futbolmarket.auth.*`. Si aparece otra referencia, detenerse y avisar (research D7)

**Checkpoint**: Línea base registrada en `$BASE` y ninguna referencia externa por paquete.

---

## Phase 2: Foundational (movimiento del código de producción)

**Purpose**: Llevar las 51 clases de `auth/` y `catalog/` al árbol `<capa>/<contexto>/`, de abajo hacia arriba, compilando después de cada capa (plan, pasos 2 a 5).

**⚠️ CRITICAL**: Todas las historias verifican el estado posterior a esta fase. No comenzar ninguna historia hasta completarla.

**Nota sobre tests en paquetes viejos**: mientras los tests sigan en `backend/src/test/java/.../auth/` y `.../catalog/`, algunos usan clases de producción sin import porque comparten paquete (por ejemplo, `ApiKeyTest`, `AppUserTest`, `CredentialPolicyTest`, `RegistrationAvailabilityTest`, `FakePasswordHasher`, `PlayerTest`, `AuthServiceTest`, `AccountServiceTest`, `AdminAccountInitializerTest`, `AdminAccountInitializerIT`, `PlayerCatalogServiceTest`). En esta fase se les agregan **imports temporales** para que compilen; la fase de US3 los quita al moverlos, de modo que el diff final contra `develop` solo tenga cambios de `package` e imports necesarios.

### Capa modelo (23 clases)

- [X] T005 Crear las carpetas destino `modelo/auth/exception/`, `modelo/user/exception/`, `modelo/player/exception/`, `modelo/team/`, `modelo/league/` y `modelo/position/` bajo `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/`
- [X] T006 [P] Mover con `git mv` el contexto `auth` del modelo y actualizar su `package`: `CredentialPolicy.java`, `PasswordHasher.java`, `RegistrationAvailability.java`, `SessionToken.java` y `SessionTokenIssuer.java` de `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/auth/modelo/` a `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/modelo/auth/` (paquete `...modelo.auth`), e `InvalidCredentialsException.java` de `.../auth/modelo/exception/` a `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/modelo/auth/exception/` (paquete `...modelo.auth.exception`)
- [X] T007 [P] Mover con `git mv` el contexto `user` del modelo y actualizar su `package`: `AppUser.java`, `ApiKey.java`, `Role.java` y `RegisteredUser.java` de `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/auth/modelo/` a `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/modelo/user/` (paquete `...modelo.user`), y `DuplicateUsernameException.java`, `DuplicateEmailException.java`, `DuplicateUsernameAndEmailException.java`, `InvalidUserDataException.java` e `InvalidPasswordChangeException.java` de `.../auth/modelo/exception/` a `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/modelo/user/exception/` (paquete `...modelo.user.exception`)
- [X] T008 [P] Mover con `git mv` el contexto `player` del modelo y actualizar su `package`: `Player.java`, `PlayerFilter.java` y `PlayerPage.java` de `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/catalog/modelo/` a `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/modelo/player/` (paquete `...modelo.player`), y `PlayerNotFoundException.java` y `CatalogInvariantException.java` de `.../catalog/modelo/` a `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/modelo/player/exception/` (paquete `...modelo.player.exception`)
- [X] T009 [P] Mover con `git mv` los contextos `team`, `league` y `position` y actualizar su `package`: `Team.java` a `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/modelo/team/`, `League.java` a `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/modelo/league/` y `Position.java` a `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/modelo/position/`, todos desde `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/catalog/modelo/`
- [X] T010 Escribir `$BASE/modelo.sed` con una regla por cada una de las 23 clases del modelo (origen → destino según la sección "modelo" de `specs/003-monolito-por-capas/data-model.md`, con `\b` al final del nombre de clase) y aplicarlo a todos los `.java` de `backend/src/main/java` y `backend/src/test/java` que contengan `futbolmarket.auth.modelo.` o `futbolmarket.catalog.modelo.` (regla 4 y 5)
- [X] T011 Agregar los imports entre contextos que antes no hacían falta (research D5) en `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/modelo/`: en `user/AppUser.java` → `CredentialPolicy` y `PasswordHasher`; en `auth/SessionTokenIssuer.java` → `AppUser`; en `player/Player.java` → `Team`, `League`, `Position` y `CatalogInvariantException`; en `player/PlayerFilter.java` → `League`, `Position` y `CatalogInvariantException`; en `player/PlayerPage.java` → `CatalogInvariantException`; en `team/Team.java` → `League` y `CatalogInvariantException`. No agregar un import de `AppUser` en `modelo/auth/CredentialPolicy.java` (su única mención es Javadoc y se resuelve en US2)
- [X] T012 Agregar imports temporales de las clases del modelo en los tests que siguen en `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/auth/modelo/` (`ApiKeyTest`, `AppUserTest`, `CredentialPolicyTest`, `RegistrationAvailabilityTest`, `FakePasswordHasher`) y en `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/catalog/modelo/PlayerTest.java`, solo para las clases que el compilador no encuentre
- [X] T013 Compilar con `./gradlew compileJava compileTestJava` en `backend/` y resolver cada error solo con imports. Con la compilación en verde, commitear la capa: `refactor(003): mover capa modelo a modelo/<contexto>/`

### Capa persistence (12 clases)

- [X] T014 Crear las carpetas destino `persistence/repository/{user,player,team}/`, `persistence/mapper/{user,player,team}/`, `persistence/sql/entity/{user,player,team}/` y `persistence/sql/interfaces/{user,player,team}/` bajo `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/`
- [X] T015 [P] Mover con `git mv` la persistencia de `user` y actualizar su `package`: `AppUserRepository.java`, `AppUserMapper.java`, `AppUserSQL.java` y `AppUserSQLDAO.java` desde `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/auth/persistence/{repository,mapper,sql/entity,sql/interfaces}/` a `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/persistence/{repository,mapper,sql/entity,sql/interfaces}/user/`
- [X] T016 [P] Mover con `git mv` la persistencia de `player` y actualizar su `package`: `PlayerRepository.java`, `PlayerMapper.java`, `PlayerSQL.java` y `PlayerSQLDAO.java` desde `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/catalog/persistence/{repository,mapper,sql/entity,sql/interfaces}/` a `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/persistence/{repository,mapper,sql/entity,sql/interfaces}/player/`
- [X] T017 [P] Mover con `git mv` la persistencia de `team` y actualizar su `package`: `TeamRepository.java`, `TeamMapper.java`, `TeamSQL.java` y `TeamSQLDAO.java` desde `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/catalog/persistence/{repository,mapper,sql/entity,sql/interfaces}/` a `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/persistence/{repository,mapper,sql/entity,sql/interfaces}/team/`
- [X] T018 Escribir `$BASE/persistence.sed` con una regla por cada una de las 12 clases de persistencia (sección "persistence" de `specs/003-monolito-por-capas/data-model.md`) y aplicarlo a `backend/src/main/java` y `backend/src/test/java`. Debe alcanzar, entre otros, a `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/config/PlayerCatalogDataSeeder.java` y `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/config/PlayerCatalogDataSeederIT.java`. No tocar las consultas JPQL de `PlayerSQLDAO.java` (research D8)
- [X] T019 Agregar los imports entre contextos de persistencia (research D5): `TeamMapper` en `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/persistence/mapper/player/PlayerMapper.java` y `TeamSQL` en `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/persistence/sql/entity/player/PlayerSQL.java`, más los que reclame el compilador
- [X] T020 Compilar con `./gradlew compileJava compileTestJava` en `backend/` y resolver cada error solo con imports. Con la compilación en verde, commitear: `refactor(003): mover capa persistence a persistence/<rol>/<contexto>/`

### Capa service (4 clases)

- [ ] T021 Crear `service/auth/`, `service/user/` y `service/player/` bajo `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/` y mover con `git mv`, actualizando el `package`: `AuthService.java` a `service/auth/`, `AccountService.java` y `AdminAccountInitializer.java` a `service/user/` (los tres desde `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/auth/service/`), y `PlayerCatalogService.java` a `service/player/` (desde `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/catalog/service/`)
- [ ] T022 Escribir `$BASE/service.sed` con una regla por cada una de las 4 clases y aplicarlo a `backend/src/main/java` y `backend/src/test/java` (alcanza, entre otros, a `security/CredentialAuthenticationFilter.java` y `security/SecurityConfig.java`). Agregar imports temporales de los servicios en los tests que siguen en `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/auth/service/` y `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/catalog/service/`, solo donde el compilador lo pida
- [ ] T023 Compilar con `./gradlew compileJava compileTestJava` en `backend/` y resolver cada error solo con imports. Con la compilación en verde, commitear: `refactor(003): mover capa service a service/<contexto>/`

### Capa controller (12 clases)

- [ ] T024 Crear `controller/auth/dto/`, `controller/user/dto/` y `controller/player/dto/` bajo `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/`
- [ ] T025 [P] Mover con `git mv` el controller de `auth` y actualizar su `package`: `AuthController.java` de `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/auth/controller/` a `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/controller/auth/`, y `LoginRequest.java`, `LoginResponse.java`, `RegisterRequest.java` y `RegisterResponse.java` de `.../auth/controller/dto/` a `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/controller/auth/dto/`
- [ ] T026 [P] Mover con `git mv` el controller de `user` y actualizar su `package`: `AccountController.java` de `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/auth/controller/` a `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/controller/user/`, y `ProfileResponse.java`, `ApiKeyResponse.java` y `ChangePasswordRequest.java` de `.../auth/controller/dto/` a `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/controller/user/dto/`
- [ ] T027 [P] Mover con `git mv` el controller de `player` y actualizar su `package`: `PlayerController.java` de `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/catalog/controller/` a `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/controller/player/`, y `PlayerResponse.java` y `PlayerPageResponse.java` de `.../catalog/controller/dto/` a `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/controller/player/dto/`
- [ ] T028 Escribir `$BASE/controller.sed` con una regla por cada una de las 12 clases y aplicarlo a `backend/src/main/java` y `backend/src/test/java`. Agregar un import temporal en `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/catalog/controller/PlayerControllerIT.java` solo si el compilador lo pide
- [ ] T029 Verificar que `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/auth/` y `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/catalog/` no contienen archivos (`find ... -type f` sin salida) y borrar esas carpetas. Compilar con `./gradlew compileJava compileTestJava` y, en verde, commitear: `refactor(003): mover capa controller a controller/<contexto>/`

**Checkpoint**: Producción completa en `<capa>/<contexto>/`, sin `auth/` ni `catalog/` en `src/main`, compilando. Las historias pueden comenzar.

---

## Phase 3: User Story 1 - La API se comporta igual que antes (Priority: P1) 🎯 MVP

**Goal**: Demostrar que los 8 endpoints conservan ruta, método, códigos, cuerpos, seguridad y formato de error (FR-001 a FR-004).

**Independent Test**: La suite HTTP existente pasa sin modificaciones y el JSON de `/v3/api-docs` es idéntico al de la línea base (`specs/003-monolito-por-capas/contracts/api-sin-cambios.md`).

- [ ] T030 [US1] Correr `./gradlew test` en `backend/` y confirmar que pasan todos los tests HTTP: los de `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/e2e/`, `PlayerControllerIT` (todavía en `.../catalog/controller/`) y `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/shared/GlobalExceptionHandlerIT.java`. Si alguno falla, corregir el código de producción (imports, ubicación), **nunca** el test
- [ ] T031 [US1] Levantar `./gradlew bootRun` en `backend/`, guardar `curl -s http://localhost:8080/v3/api-docs > "$BASE/api-docs-despues.json"` y compararlo con `$BASE/api-docs-antes.json` usando el comando del paso 7 de `specs/003-monolito-por-capas/quickstart.md`. Esperado: sin diferencias. Cualquier diferencia es un defecto de la feature
- [ ] T032 [US1] Con la aplicación levantada, hacer un smoke de contrato con `curl -i`: `GET /actuator/health` (200, `UP`), `GET /players?size=1` (200 con metadatos de página), `GET /players/999999` (404 con el `ApiError` en español) y `GET /auth/me` sin token (401 con el `ApiError`). Comparar con lo que describe `specs/002-catalogo-jugadores/contracts/players-api.yaml` y `specs/001-auth-usuarios/contracts/auth-api.yaml`. Detener la aplicación

**Checkpoint**: El contrato de la API está verificado sobre el código ya movido.

---

## Phase 4: User Story 2 - El código está donde la constitución dice (Priority: P2)

**Goal**: Dejar el árbol de producción exactamente como el del Principio I, sin restos de la estructura por feature (FR-005 a FR-011).

**Independent Test**: El paso 2 de `quickstart.md` no muestra paquetes por feature, carpetas vacías ni `.gitkeep`, y el diff de `security/`, `config/` y `shared/` solo tiene imports.

- [ ] T033 [US2] Borrar con `git rm` el archivo `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/shared/.gitkeep` (el paquete tiene clases; FR-011)
- [ ] T034 [P] [US2] En el Javadoc de `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/modelo/player/exception/CatalogInvariantException.java`, cambiar "de auth" por "del contexto `user`" en la mención a `InvalidUserDataException`, sin tocar ninguna otra línea (research D6)
- [ ] T035 [P] [US2] En el Javadoc de `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/modelo/auth/CredentialPolicy.java`, reemplazar `{@link AppUser}` por `{@link ar.edu.unq.desapp.futbolmarket.modelo.user.AppUser}`, sin agregar un import (research D6)
- [ ] T036 [US2] Ejecutar los controles del paso 2 de `specs/003-monolito-por-capas/quickstart.md` desde `backend/src/` (raíz con solo `config controller FutbolMarketApplication.java modelo persistence security service shared`; sin carpetas `auth`/`catalog` en `main`; sin nombres calificados viejos en `main`; sin carpetas vacías ni `.gitkeep` en `main`) y comparar el listado de `find main/java/ar/edu/unq/desapp/futbolmarket/{controller,service,modelo,persistence} -name '*.java' | sort` con las tablas de producción de `specs/003-monolito-por-capas/data-model.md` (51 archivos)
- [ ] T037 [US2] Verificar que en `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/security/`, `.../config/` y `.../shared/` solo cambiaron imports: `git diff -M develop -- <esas tres carpetas> | grep -E '^[+-][^+-]' | grep -vE '^[+-]import '` sin salida (FR-010; el borrado de `.gitkeep` no aporta líneas)
- [ ] T038 [US2] Ejecutar el segundo comando del paso 4 de `specs/003-monolito-por-capas/quickstart.md` sobre `backend/src/main`. Esperado: solo las dos líneas de comentario de T034 y T035. Si aparece cualquier otra línea, revertirla. Commitear: `refactor(003): limpiar .gitkeep y comentarios de paquetes viejos`

**Checkpoint**: Producción conforme al Principio I y a FR-005 a FR-011.

---

## Phase 5: User Story 3 - La suite de tests se conserva completa (Priority: P3)

**Goal**: Mover los 13 tests y el helper que quedan en paquetes que desaparecen, replicando capa y contexto, cambiando solo `package` e imports (FR-015 a FR-017).

**Independent Test**: Corren 212 tests en 25 clases, 0 fallidos y 0 omitidos, y el diff de `backend/src/test` solo tiene líneas `package` e `import`.

**Al mover cada test**: cambiar el `package` al del destino, **quitar los imports temporales** agregados en la fase Foundational que ahora apuntan al mismo paquete, y dejar los imports que sigan siendo necesarios. Ningún otro cambio (FR-016). No tocar las cadenas `"futbolmarket.auth.admin.*"` de `AdminAccountInitializerIT`.

- [ ] T039 [US3] Crear las carpetas destino `modelo/auth/`, `modelo/user/`, `modelo/player/`, `service/auth/`, `service/user/`, `service/player/`, `persistence/repository/user/` y `persistence/repository/player/` bajo `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/`
- [ ] T040 [P] [US3] Mover con `git mv` `CredentialPolicyTest.java`, `RegistrationAvailabilityTest.java` y el helper `FakePasswordHasher.java` de `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/auth/modelo/` a `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/modelo/auth/` (paquete `...modelo.auth`)
- [ ] T041 [P] [US3] Mover con `git mv` `AppUserTest.java` y `ApiKeyTest.java` de `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/auth/modelo/` a `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/modelo/user/` (paquete `...modelo.user`); `AppUserTest` pasa a importar `ar.edu.unq.desapp.futbolmarket.modelo.auth.FakePasswordHasher` y las clases de `modelo.auth` que use
- [ ] T042 [P] [US3] Mover con `git mv` `PlayerTest.java` de `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/catalog/modelo/` a `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/modelo/player/` (paquete `...modelo.player`)
- [ ] T043 [P] [US3] Mover con `git mv` `AuthServiceTest.java` de `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/auth/service/` a `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/service/auth/` (paquete `...service.auth`)
- [ ] T044 [P] [US3] Mover con `git mv` `AccountServiceTest.java`, `AdminAccountInitializerTest.java` y `AdminAccountInitializerIT.java` de `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/auth/service/` a `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/service/user/` (paquete `...service.user`)
- [ ] T045 [P] [US3] Mover con `git mv` `PlayerCatalogServiceTest.java` de `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/catalog/service/` a `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/service/player/` (paquete `...service.player`)
- [ ] T046 [P] [US3] Mover con `git mv` `AppUserRepositoryIT.java` de `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/auth/persistence/` a `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/persistence/repository/user/` y `PlayerRepositoryIT.java` de `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/catalog/persistence/` a `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/persistence/repository/player/`
- [ ] T047 [P] [US3] Mover con `git mv` `PlayerControllerIT.java` de `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/catalog/controller/` a `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/e2e/` (paquete `...e2e`; FR-017)
- [ ] T048 [US3] Reescribir en `backend/src/test/java` el import `ar.edu.unq.desapp.futbolmarket.auth.modelo.FakePasswordHasher` por `ar.edu.unq.desapp.futbolmarket.modelo.auth.FakePasswordHasher` (lo usan `AuthServiceTest`, `AccountServiceTest` y `AdminAccountInitializerTest`). Verificar que `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/auth/` y `.../catalog/` no tienen archivos y borrarlas. Compilar con `./gradlew compileJava compileTestJava`
- [ ] T049 [US3] Ejecutar el primer comando del paso 4 de `specs/003-monolito-por-capas/quickstart.md` sobre `backend/src/test`. Esperado: sin salida. Además, revisar que ningún import agregado quedó sin usar o apuntando al mismo paquete (Principio V); si queda alguno, quitarlo
- [ ] T050 [US3] Correr `./gradlew clean test` en `backend/` y repetir el conteo del paso 1 de `quickstart.md`. Esperado: los valores de `$BASE/linea-base.txt` (25 archivos, `tests=212 skipped=0 failed=0`). Si un test falla, corregir producción, nunca el test. Commitear: `refactor(003): mover tests al árbol por capa`
- [ ] T051 [US3] Verificar el historial con el paso 3 de `specs/003-monolito-por-capas/quickstart.md` (usando `git diff -M --stat develop` sobre lo commiteado): 65 renombres detectados (51 de producción y 14 de test) y `git log --follow` de `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/modelo/user/AppUser.java` muestra commits de la feature 001

**Checkpoint**: Suite completa, ubicada por capa y contexto, sin cambios de contenido.

---

## Phase 6: User Story 4 - La aplicación levanta, se documenta y pasa el control de calidad (Priority: P4)

**Goal**: Cumplir la definición de terminado: build, arranque con datos existentes, Swagger y umbral de calidad.

**Independent Test**: `./gradlew clean build` en verde, `bootRun` con la base local existente responde `UP` y datos, y Swagger muestra las 7 operaciones bajo las mismas secciones.

- [ ] T052 [US4] Correr `./gradlew clean build` en `backend/`. Esperado: `BUILD SUCCESSFUL` (SC-002)
- [ ] T053 [US4] Sin borrar `backend/data/`, levantar `./gradlew bootRun` y comprobar `curl -s http://localhost:8080/actuator/health` (`{"status":"UP"}`) y `curl -s "http://localhost:8080/players?size=1"` (página con los datos ya cargados). Revisar que el log no tenga errores de Hibernate ni de creación de beans (SC-005, FR-004, research D9)
- [ ] T054 [US4] Con la aplicación levantada, abrir `http://localhost:8080/swagger-ui.html` y confirmar las 7 operaciones de negocio bajo "Autenticación", "Cuenta" y la sección del catálogo de jugadores (SC-006, FR-003). Detener la aplicación

**Checkpoint**: Definición de terminado cumplida en local. SC-007 se verifica después del merge (Phase 7).

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Documentación del repo, validación final y entrega.

- [ ] T055 [P] Actualizar la sección **Estructura** de `README.md`: conservar las líneas de `backend/` y `frontend/` y agregar el árbol de `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/` por capa (`controller`, `service`, `modelo`, `persistence`, `security`, `config`, `shared`) con los contextos vigentes (`auth`, `user`, `player`, `team`, `league`, `position`) y una referencia al Principio I de `.specify/memory/constitution.md`
- [ ] T056 [P] Agregar al principio de `specs/001-auth-usuarios/plan.md`, antes del título, la nota: `> **Estructura de paquetes histórica**: reemplazada por la constitución 2.0.0 (ver [specs/003](../003-monolito-por-capas/plan.md)).` Sin reescribir el resto
- [ ] T057 [P] Agregar la misma nota al principio de `specs/002-catalogo-jugadores/plan.md`, antes del título
- [ ] T058 [P] Agregar al principio de `specs/001-auth-usuarios/contracts/shared-integration.md` la marca: `> **OBSOLETO**: la constitución 2.0.0 prohíbe los contratos de integración entre contextos y entre features (ver [specs/003](../../003-monolito-por-capas/plan.md)). Se conserva como registro histórico.`
- [ ] T059 Commitear la documentación (`docs(003): estructura por capas en README y notas históricas`) y ejecutar de punta a punta los pasos 2 a 8 de `specs/003-monolito-por-capas/quickstart.md` sobre el estado final
- [ ] T060 Abrir un único PR de `003-monolito-por-capas` contra `develop` (clarificación del spec), con el resumen de movimientos, los resultados de la línea base vs. el final y el aviso de que las ramas abiertas deben rebasearse después del merge. Pedir confirmación al usuario antes de pushear y abrir el PR
- [ ] T061 Después del merge a `main`, revisar el análisis de SonarCloud del push a `main`: menos de 10 issues (SC-007). Si no se cumple, seguir research D11 y corregir en un PR de seguimiento solo los issues que surjan del movimiento

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: Sin dependencias. Tiene que terminar antes de mover nada, porque mide la línea base.
- **Foundational (Phase 2)**: Depende de Setup. Bloquea todas las historias. Dentro de la fase, el orden de capas es obligatorio: modelo → persistence → service → controller (cada capa compila y se commitea antes de la siguiente).
- **US1 (Phase 3)**: Depende de Foundational.
- **US2 (Phase 4)**: Depende de Foundational. Independiente de US1 y US3.
- **US3 (Phase 5)**: Depende de Foundational. Independiente de US1 y US2 (toca solo `backend/src/test`).
- **US4 (Phase 6)**: Depende de US1, US2 y US3, porque valida el estado final completo.
- **Polish (Phase 7)**: T055 a T058 pueden empezar después de Foundational. T059 depende de US4. T060 depende de T059. T061 depende del merge.

### User Story Dependencies

- **US1 (P1)**: Solo Foundational. Verifica el contrato aunque los tests sigan en paquetes viejos.
- **US2 (P2)**: Solo Foundational.
- **US3 (P3)**: Solo Foundational. T048 depende de T040 (ubicación nueva de `FakePasswordHasher`).
- **US4 (P4)**: US1, US2 y US3.

### Within Each Phase

- Crear carpetas → `git mv` + `package` → reescritura de imports → imports nuevos → compilar → commit.
- Las tareas [P] de una misma capa mueven archivos distintos y se pueden hacer en cualquier orden, pero la reescritura de imports y la compilación esperan a que terminen todas.

### Parallel Opportunities

- T004 en paralelo con T002 y T003.
- Modelo: T006, T007, T008 y T009. Persistence: T015, T016 y T017. Controller: T025, T026 y T027.
- Después de Foundational, US1, US2 y US3 pueden avanzar en paralelo (dos personas): US2 toca `backend/src/main`, US3 toca `backend/src/test`, US1 solo lee y ejecuta.
- US2: T034 y T035. US3: T040 a T047.
- Polish: T055, T056, T057 y T058.

---

## Parallel Example: Foundational, capa modelo

```bash
# Después de T005, mover los cuatro grupos de contextos a la vez:
Task: "T006 Mover el contexto auth del modelo a modelo/auth/ y modelo/auth/exception/"
Task: "T007 Mover el contexto user del modelo a modelo/user/ y modelo/user/exception/"
Task: "T008 Mover el contexto player del modelo a modelo/player/ y modelo/player/exception/"
Task: "T009 Mover Team, League y Position a modelo/team/, modelo/league/ y modelo/position/"
# Después, en secuencia: T010 (imports) → T011 (imports entre contextos) → T012 → T013 (compilar y commitear)
```

## Parallel Example: User Story 3

```bash
# Después de T039:
Task: "T040 Mover CredentialPolicyTest, RegistrationAvailabilityTest y FakePasswordHasher a test modelo/auth/"
Task: "T041 Mover AppUserTest y ApiKeyTest a test modelo/user/"
Task: "T042 Mover PlayerTest a test modelo/player/"
Task: "T043 Mover AuthServiceTest a test service/auth/"
Task: "T044 Mover AccountServiceTest, AdminAccountInitializerTest y AdminAccountInitializerIT a test service/user/"
Task: "T045 Mover PlayerCatalogServiceTest a test service/player/"
Task: "T046 Mover AppUserRepositoryIT y PlayerRepositoryIT a test persistence/repository/<contexto>/"
Task: "T047 Mover PlayerControllerIT a e2e/"
```

---

## Implementation Strategy

### MVP First (User Story 1)

1. Phase 1: Setup (línea base).
2. Phase 2: Foundational (producción movida, compilando, un commit por capa).
3. Phase 3: US1 (contrato verificado).
4. **STOP and VALIDATE**: la API es idéntica con el código ya en `<capa>/<contexto>/`. Ese estado no se mergea solo: el spec pide un único PR, así que el MVP es un punto de control interno, no una entrega.

### Incremental Delivery (dentro del único PR)

1. Setup + Foundational → producción por capa.
2. US1 → contrato verificado.
3. US2 → árbol limpio y comentarios corregidos.
4. US3 → tests por capa, 212/212.
5. US4 → build, arranque, Swagger.
6. Polish → documentación, quickstart completo, PR a `develop`, SonarCloud después del merge.

### Parallel Team Strategy

Con las dos personas del equipo:

1. Una persona hace Setup y Foundational (el orden por capas no se puede repartir sin conflictos en los imports).
2. Después de Foundational:
   - Persona A: US2 y luego T055 a T058.
   - Persona B: US3.
   - Cualquiera: US1 (solo lectura y ejecución).
3. Juntas: US4 y Phase 7.

---

## Notes

- [P] = archivos distintos, sin dependencias pendientes.
- [Story] relaciona cada tarea con su historia del spec.
- Un test que falla después de un movimiento se arregla en producción, nunca en el test (casos borde del spec).
- Nunca usar reemplazos sobre `futbolmarket.auth` a secas (research D4).
- Commitear al terminar cada capa y cada historia para que la rama se pueda bisecar (research D3).
- No crear carpetas vacías: cada contexto aparece solo en las capas donde tiene clases (FR-011).
