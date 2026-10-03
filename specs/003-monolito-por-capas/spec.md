# Especificación de funcionalidad: Monolito por capas

**Rama de funcionalidad**: `003-monolito-por-capas`  
**Creada**: 2026-10-03  
**Estado**: Borrador  
**Entrada**: Reestructuración del backend de "por feature" a "por capa" según la constitución 2.0.0. Es un refactor sin cambios funcionales: todos los endpoints existentes (`/auth/**`, `/players/**`, `/actuator/health`) mantienen exactamente el mismo contrato, los mismos códigos de estado y el mismo formato de error. Se unifican `auth/` y `catalog/` en `controller/`, `service/`, `modelo/` (con `modelo/exception/`) y `persistence/` (repository, mapper, sql/entity, sql/interfaces). `security/`, `config/` y `shared/` no cambian de lugar. Los tests se mueven al árbol por capa sin modificar ninguna aserción. Criterios de éxito: `./gradlew build` en verde, la misma cantidad de tests ejecutados que antes del cambio, `bootRun` levanta con el perfil local, Swagger muestra los mismos endpoints y SonarCloud queda con menos de 10 issues. Fuera de alcance: cualquier funcionalidad de mercado, renombrar clases o cambiar lógica.

## Clarificaciones

### Sesión 2026-10-03

- P: ¿Cómo se reparten las clases de `auth/` entre los contextos `auth` y `user`? → R: Por responsabilidad (opción A). La cuenta de usuario, su modelo, su persistencia, el perfil, el cambio de contraseña, la API key y el admin inicial van a `user`. Registro, login, tokens de sesión, política de credenciales y hashing van a `auth`. El detalle clase por clase está en FR-008.

## Contexto

La constitución 2.0.0 redefinió el Principio I: el sistema es un único monolito organizado primero **por capa** (`controller`, `service`, `modelo`, `persistence`) y, dentro de cada capa, **por contexto del dominio** (`user`, `player`, `team`, `league`, `position`, `auth`). El código actual sigue el árbol de la v1.0.0, con paquetes de primer nivel por feature (`auth/`, `catalog/`), que la constitución ya no admite.

Esta funcionalidad lleva el código existente al árbol vigente **sin alterar ningún comportamiento observable**. Las personas afectadas son dos:

- **Quienes consumen la API** (frontend futuro, docentes que evalúan, herramientas de prueba): no deben notar ninguna diferencia.
- **El equipo de desarrollo** (dos personas): necesita que el código esté donde la constitución dice que está, para que las próximas entregas (por ejemplo, el mercado) se agreguen sin excepciones ni reubicaciones.

**Línea base medida antes del cambio** (commit `dc0be4d`, 2026-10-03): 25 clases de test, 212 tests ejecutados, 0 fallidos, 0 omitidos.

## Escenarios de usuario y pruebas *(obligatorio)*

### Historia de usuario 1 - La API se comporta igual que antes (Prioridad: P1)

Como consumidor de la API, quiero que todos los endpoints respondan exactamente igual que antes de la reestructuración, para no tener que adaptar nada de lo que ya integré.

**Por qué esta prioridad**: Es la condición que define a un refactor. Si un solo endpoint cambia su contrato, la entrega deja de ser un refactor y rompe a quien ya consume la API.

**Prueba independiente**: Se puede comprobar ejecutando el mismo conjunto de pedidos (casos felices y de error) contra la versión anterior y contra la nueva, y comparando ruta, método, código de estado, estructura del cuerpo y formato de error de cada respuesta.

**Escenarios de aceptación**:

1. **Dado** el sistema reestructurado, **cuando** una persona se registra, inicia sesión, consulta su perfil, cambia su contraseña o genera su API key, **entonces** recibe los mismos códigos de estado y los mismos campos de respuesta que antes del cambio.
2. **Dado** el sistema reestructurado, **cuando** se consulta el catálogo de jugadores paginado, filtrado o por id, **entonces** la respuesta tiene la misma estructura, el mismo orden y los mismos metadatos de paginación que antes.
3. **Dado** un pedido inválido, sin autenticación, sin permisos, sobre un recurso inexistente o en conflicto, **cuando** se envía al sistema reestructurado, **entonces** se obtiene el mismo código (400, 401, 403, 404 o 409) y el mismo formato de error JSON que antes, sin stack traces ni mensajes internos.
4. **Dado** el sistema reestructurado, **cuando** se consulta el estado de salud, **entonces** responde igual que antes.

---

### Historia de usuario 2 - El código está donde la constitución dice (Prioridad: P2)

Como integrante del equipo, quiero que cada clase esté en `<capa>/<contexto>/` según el Principio I, para encontrar cualquier pieza sin conocer la historia de las features y agregar las próximas sin crear excepciones.

**Por qué esta prioridad**: Es el objetivo de la reestructuración. Sin esto, la constitución vigente no se cumple y cada entrega nueva arrastra la deuda.

**Prueba independiente**: Se puede comprobar recorriendo el árbol de paquetes de producción y verificando que coincide con el del Principio I: no existen paquetes de primer nivel por feature, no hay carpetas vacías y cada clase está en la capa y el contexto que le corresponden.

**Escenarios de aceptación**:

1. **Dado** el árbol reestructurado, **cuando** se listan los paquetes de primer nivel bajo la raíz de la aplicación, **entonces** solo aparecen `controller`, `service`, `modelo`, `persistence`, `security`, `config` y `shared`, además de la clase de arranque.
2. **Dado** una excepción de dominio, **cuando** se busca su ubicación, **entonces** está en `modelo/<contexto>/exception/`.
3. **Dado** una clase de persistencia, **cuando** se busca su ubicación, **entonces** está en `persistence/repository/<contexto>/`, `persistence/mapper/<contexto>/`, `persistence/sql/entity/<contexto>/` o `persistence/sql/interfaces/<contexto>/` según su rol.
4. **Dado** una clase de `security/`, `config/` o `shared/`, **cuando** se compara con la versión anterior, **entonces** sigue en el mismo paquete y solo cambiaron sus imports.

---

### Historia de usuario 3 - La suite de tests se conserva completa (Prioridad: P3)

Como integrante del equipo, quiero que todos los tests existentes sigan existiendo, se ejecuten y pasen, ubicados en el árbol por capa y sin modificar su contenido, para tener la garantía de que el refactor no cambió el comportamiento.

**Por qué esta prioridad**: Los tests son la evidencia objetiva de la historia 1. La constitución (Principio IV) prohíbe modificarlos sin permiso; moverlos solo está permitido si cambian únicamente `package` e imports.

**Prueba independiente**: Se puede comprobar comparando la cantidad de tests ejecutados con la línea base y revisando el diff de cada archivo de test movido.

**Escenarios de aceptación**:

1. **Dado** la suite reestructurada, **cuando** se ejecuta completa, **entonces** corren 212 tests en 25 clases, todos pasan y ninguno se omite.
2. **Dado** un archivo de test movido, **cuando** se revisa su diff contra la versión anterior, **entonces** las únicas líneas cambiadas son la declaración `package` y los imports.
3. **Dado** un test que usa MockMvc, **cuando** se busca su ubicación, **entonces** está en `e2e/`.
4. **Dado** un test de modelo, servicio o persistencia, **cuando** se busca su ubicación, **entonces** replica la capa y el contexto de la clase que prueba.

---

### Historia de usuario 4 - La aplicación levanta, se documenta y pasa el control de calidad (Prioridad: P4)

Como integrante del equipo, quiero que la aplicación compile, levante con el perfil local, siga mostrando la misma documentación interactiva y cumpla el umbral de calidad, para que la entrega cumpla la definición de terminado.

**Por qué esta prioridad**: Cierra la definición de terminado de la constitución. Depende de las historias anteriores.

**Prueba independiente**: Se puede comprobar construyendo el proyecto, levantándolo con el perfil local, abriendo la documentación interactiva y revisando el informe del análisis estático.

**Escenarios de aceptación**:

1. **Dado** el código reestructurado, **cuando** se construye el proyecto, **entonces** el build termina con éxito.
2. **Dado** el código reestructurado y una base local con datos de una corrida anterior, **cuando** se levanta con el perfil local, **entonces** arranca sin errores y los datos existentes siguen accesibles.
3. **Dado** la aplicación levantada, **cuando** se abre la documentación interactiva, **entonces** muestra las mismas operaciones, agrupadas bajo las mismas secciones, que antes del cambio.
4. **Dado** el código reestructurado, **cuando** se lo analiza con SonarCloud, **entonces** reporta menos de 10 issues.

### Casos borde

- **Clases que sirven a varios contextos** (por ejemplo, una excepción de invariante usada por `Player` y por `Team`): se ubican en el contexto de la entidad principal a la que pertenecen, según la regla de contextos de la constitución.
- **Helpers de test** sin tests propios (`FakePasswordHasher`, `AuthTestHelper`): se mueven junto a los tests que los usan; si los usan tests de varios paquetes, quedan donde los usan los tests de mayor nivel (por ejemplo, `e2e/`).
- **Referencias por nombre de paquete fuera del código** (escaneo de componentes, configuración de logging, exclusiones de calidad, workflows de CI): se verifica que ninguna quede apuntando a `auth` o `catalog`. A la fecha no se encontraron referencias de este tipo.
- **Base local persistida** (`jdbc:h2:file`): como los nombres de tabla y de columna no cambian, una base creada antes del refactor sigue siendo legible después.
- **`.gitkeep` en un paquete con clases** (hoy `shared/.gitkeep`): se borra en el mismo cambio, según la constitución.
- **Contextos sin clases en una capa**: no se crea la carpeta. Por ejemplo, si un contexto no tiene controller, no existe `controller/<contexto>/`.
- **Test que falla después del movimiento**: se corrige el código de producción (imports, ubicación), nunca el test.

## Requisitos *(obligatorio)*

### Requisitos funcionales

**Contrato de la API (sin cambios)**

- **FR-001**: El sistema DEBE conservar, para cada una de estas operaciones, la misma ruta, el mismo método, la misma estructura de pedido y de respuesta, los mismos códigos de estado y los mismos requisitos de autenticación y rol:
  - `POST /auth/register`
  - `POST /auth/login`
  - `GET /auth/me`
  - `PUT /auth/me/password`
  - `POST /auth/me/api-key`
  - `GET /players`
  - `GET /players/{id}`
  - `GET /actuator/health`
- **FR-002**: El sistema DEBE conservar el formato de error JSON único y los mismos mensajes de error en español para cada situación de error (400, 401, 403, 404, 409).
- **FR-003**: La documentación interactiva DEBE mostrar las mismas operaciones agrupadas bajo las mismas secciones ("Autenticación", "Cuenta" y la del catálogo de jugadores).
- **FR-004**: El sistema DEBE conservar los nombres de tabla y de columna existentes, de modo que una base local creada antes del cambio siga siendo utilizable.

**Estructura por capa y contexto**

- **FR-005**: El código de producción DEBE organizarse según el árbol del Principio I de la constitución 2.0.0: `controller/<contexto>/` (con `dto/`), `service/<contexto>/`, `modelo/<contexto>/` (con `exception/`) y `persistence/{repository,mapper}/<contexto>/` y `persistence/sql/{entity,interfaces}/<contexto>/`.
- **FR-006**: NO DEBEN quedar paquetes de primer nivel por feature (`auth/`, `catalog/`).
- **FR-007**: Las clases del antiguo `catalog/` DEBEN distribuirse por contexto según la entidad principal a la que pertenecen: `player` (jugador, filtro, página de jugadores y sus excepciones), `team`, `league` y `position`.
- **FR-008**: Las clases del antiguo `auth/` DEBEN distribuirse entre los contextos `user` y `auth` según su responsabilidad:
  - **`user`** (la cuenta y su entidad principal): `AppUser`, `ApiKey`, `Role`, `RegisteredUser`; las excepciones `DuplicateUsernameException`, `DuplicateEmailException`, `DuplicateUsernameAndEmailException`, `InvalidUserDataException` e `InvalidPasswordChangeException`; `AccountController` y sus DTO (`ProfileResponse`, `ApiKeyResponse`, `ChangePasswordRequest`); `AccountService` y `AdminAccountInitializer`; y toda la persistencia de `AppUser` (`AppUserRepository`, `AppUserMapper`, `AppUserSQL`, `AppUserSQLDAO`).
  - **`auth`** (el acto de autenticarse): `AuthController` y sus DTO (`LoginRequest`, `LoginResponse`, `RegisterRequest`, `RegisterResponse`); `AuthService`; `CredentialPolicy`, `PasswordHasher`, `RegistrationAvailability`, `SessionToken` y `SessionTokenIssuer`; y la excepción `InvalidCredentialsException`.
- **FR-009**: Toda excepción de dominio DEBE ubicarse en `modelo/<contexto>/exception/`, incluidas las que hoy están sueltas en `catalog/modelo/` (`PlayerNotFoundException`, `CatalogInvariantException`).
- **FR-010**: `security/`, `config/` y `shared/` DEBEN permanecer en su ubicación actual. En sus clases solo pueden cambiar los imports.
- **FR-011**: Un contexto DEBE aparecer únicamente en las capas donde tiene clases. NO DEBEN quedar carpetas vacías ni archivos `.gitkeep` en paquetes con clases.

**Alcance del cambio**

- **FR-012**: Ninguna clase DEBE cambiar de nombre.
- **FR-013**: Ninguna clase DEBE cambiar su lógica. En el código de producción solo pueden cambiar la declaración `package`, los imports y la ubicación del archivo.
- **FR-014**: NO DEBEN agregarse, quitarse ni actualizarse dependencias, ni reemplazarse la configuración de build o los archivos de configuración de la aplicación.

**Tests**

- **FR-015**: Todos los tests existentes DEBEN conservarse. Solo se mueven los que quedan en un paquete que deja de existir.
- **FR-016**: En cada test movido, solo pueden cambiar la declaración `package` y los imports. Ningún método, nombre, aserción ni dato puede cambiar.
- **FR-017**: Los tests movidos DEBEN replicar la capa y el contexto de la clase que prueban. Los tests con MockMvc DEBEN vivir en `e2e/`. Los tests de `security/`, `config/` y `shared/` quedan en su paquete transversal.

### Entidades clave

No aplica: no se agregan ni se modifican conceptos de dominio ni datos. Los conceptos existentes (usuario, API key, rol, jugador, equipo, liga, posición) se conservan tal cual y solo cambian de ubicación.

## Criterios de éxito *(obligatorio)*

### Resultados medibles

- **SC-001**: El 100 % de las 8 operaciones de FR-001 responde con el mismo código de estado y la misma estructura de cuerpo que antes del cambio, tanto en los casos felices como en cada caso de error cubierto por la suite.
- **SC-002**: `./gradlew build` termina en BUILD SUCCESSFUL.
- **SC-003**: Se ejecutan 212 tests en 25 clases, con 0 fallidos y 0 omitidos, igual que la línea base.
- **SC-004**: En el diff de los archivos de test hay 0 líneas cambiadas que no sean `package` o imports.
- **SC-005**: La aplicación levanta con el perfil local en el primer intento y el estado de salud informa que está operativa.
- **SC-006**: La documentación interactiva muestra las mismas 7 operaciones de negocio, bajo las mismas secciones, que antes del cambio.
- **SC-007**: SonarCloud reporta menos de 10 issues sobre el código reestructurado.
- **SC-008**: Hay 0 paquetes de primer nivel por feature, 0 carpetas vacías y 0 archivos `.gitkeep` en paquetes con clases.

## Supuestos

- La línea base de tests (212 tests en 25 clases) se midió el 2026-10-03 sobre el commit `dc0be4d`. Si la rama base cambia antes de implementar, la línea base se vuelve a medir.
- El paquete raíz de la aplicación no cambia, por lo que el descubrimiento automático de componentes, entidades y repositorios sigue cubriendo el árbol nuevo sin configuración adicional.
- `PlayerCatalogDataSeeder` sigue en `config/`, porque `config/` no cambia de lugar.
- `CatalogInvariantException` se ubica en el contexto `player`, porque el jugador es la entidad principal del antiguo catálogo, aunque `Team` también la use.
- `AdminAccountInitializer` es un servicio y se ubica en `service/user/`.
- Los tests de `auth/` siguen el mismo reparto que FR-008. Por ejemplo, `AppUserTest` y `ApiKeyTest` van a `modelo/user/`, `CredentialPolicyTest` y `RegistrationAvailabilityTest` a `modelo/auth/`, y `AuthServiceTest` a `service/auth/`. `FakePasswordHasher` implementa `PasswordHasher` y va a `modelo/auth/`; si lo usan tests de otro contexto, esos tests lo importan desde ahí.
- SonarCloud solo analiza la rama principal en el CI actual. El umbral de SC-007 se verifica sobre el análisis disponible (análisis local o el de `main` después del merge), y la forma de verificarlo se define en el plan.
- Los planes de `specs/001` y `specs/002` describen la estructura por feature de la v1.0.0. Quedan como registro histórico y no se reescriben.
- Fuera de alcance: cualquier funcionalidad de mercado, renombrar clases, cambiar lógica, agregar tests nuevos y corregir issues de calidad que no surjan del propio movimiento.
