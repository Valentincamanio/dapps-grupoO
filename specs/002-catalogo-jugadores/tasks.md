---

description: "Tareas ejecutables para la entrega del catálogo de jugadores"
---

# Tasks: Catálogo de jugadores

**Input**: Documentos de diseño en `specs/002-catalogo-jugadores/`

**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`, `contracts/players-api.yaml` y `quickstart.md`

**Tests**: Se incluyen porque la especificación define escenarios verificables y la constitución exige pruebas unitarias, de integración y HTTP para cada incremento.

**Organization**: Las tareas se agrupan por historia para que cada incremento sea implementable y comprobable de forma independiente.

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Confirmar el esqueleto Spring Boot y preparar los recursos de la feature sin cambiar el stack ni los perfiles ya definidos.

- [X] T001 Verificar el baseline y ejecutar la suite existente con `backend/build.gradle` y `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/FutbolMarketApplicationTests.java`
- [X] T002 Crear el árbol de paquetes de catálogo definido en el plan y retirar los `.gitkeep` reemplazados bajo `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/catalog/`
- [X] T003 Revisar que los perfiles local y test conservan H2, `open-in-view: false` y sus modos de DDL en `backend/src/main/resources/application-local.yml` y `backend/src/test/resources/application-test.yml`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Establecer las convenciones de error y resolver la contradicción documental antes de exponer endpoints.

**⚠️ CRITICAL**: No comenzar historias de usuario hasta completar esta fase.

- [X] T004 Corregir la regla contradictoria de filtros inválidos para que indique `400`, conforme a plan y contrato, en `specs/002-catalogo-jugadores/spec.md`
- [X] T005 [P] Crear el DTO de error HTTP uniforme `ApiError` en `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/shared/ApiError.java`
- [X] T006 [P] Crear las excepciones de dominio `PlayerNotFoundException` e invariantes del catálogo en `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/catalog/modelo/`
- [X] T007 Implementar el único `@RestControllerAdvice` que traduzca validación, argumentos inválidos y jugador inexistente a errores en español en `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/shared/GlobalExceptionHandler.java`
- [X] T008 Crear pruebas HTTP del formato `ApiError` para respuestas 400 y 404 en `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/shared/GlobalExceptionHandlerIT.java`

**Checkpoint**: La base de errores es consistente y las historias pueden comenzar.

---

## Phase 3: User Story 1 - Explorar el catálogo paginado (Priority: P1) 🎯 MVP

**Goal**: Entregar un catálogo inicial persistente y un listado público ordenado, paginado y navegable.

**Independent Test**: Con perfil `local`, `GET /players?page=0&size=10` devuelve jugadores con id, nombre, posición, equipo y liga, más todos los metadatos; al recorrer las páginas cada jugador aparece una vez.

### Implementation and tests for User Story 1

- [X] T009 [P] [US1] Crear los enums `League` y `Position` con los valores admitidos en `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/catalog/modelo/League.java` y `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/catalog/modelo/Position.java`
- [X] T010 [P] [US1] Implementar el modelo rico `Team`, con nombre y liga obligatorios, en `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/catalog/modelo/Team.java`
- [X] T011 [P] [US1] Implementar el modelo rico `Player`, sus invariantes y la liga derivada del equipo en `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/catalog/modelo/Player.java`
- [X] T012 [P] [US1] Implementar el valor de página `PlayerPage` con metadatos de navegación base cero en `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/catalog/modelo/PlayerPage.java`
- [X] T013 [P] [US1] Cubrir invariantes de `Team`, `Player` y metadatos de `PlayerPage` con pruebas unitarias AssertJ en `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/catalog/modelo/PlayerTest.java`
- [X] T014 [P] [US1] Crear las entidades JPA `TeamSQL` y `PlayerSQL`, con tablas explícitas, enums como texto y asociación lazy, en `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/catalog/persistence/sql/entity/TeamSQL.java` y `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/catalog/persistence/sql/entity/PlayerSQL.java`
- [X] T015 [P] [US1] Crear los DAOs Spring Data para equipos y jugadores en `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/catalog/persistence/sql/interfaces/TeamSQLDAO.java` y `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/catalog/persistence/sql/interfaces/PlayerSQLDAO.java`
- [X] T016 [US1] Implementar los mappers bidireccionales sin lógica de negocio en `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/catalog/persistence/mapper/TeamMapper.java` y `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/catalog/persistence/mapper/PlayerMapper.java`
- [X] T017 [US1] Implementar los repositorios que encapsulan DAOs y mappers, incluida la página ordenada por id ascendente, en `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/catalog/persistence/repository/TeamRepository.java` y `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/catalog/persistence/repository/PlayerRepository.java`
- [X] T018 [US1] Crear `PlayerCatalogService` para obtener páginas de modelos sin conocer DTOs ni JPA en `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/catalog/service/PlayerCatalogService.java`
- [X] T019 [P] [US1] Crear los DTOs HTTP de respuesta del jugador y de la página en `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/catalog/controller/dto/PlayerResponse.java` y `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/catalog/controller/dto/PlayerPageResponse.java`
- [X] T020 [US1] Implementar `GET /players` con valores por defecto, validación de página/tamaño y conversión modelo-a-DTO en `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/catalog/controller/PlayerController.java`
- [X] T021 [P] [US1] Crear el dataset estático de 50–60 jugadores, 10–12 por liga y cuatro posiciones válidas en `backend/src/main/resources/data/players.json`
- [X] T022 [US1] Implementar el seeder exclusivo del perfil local, idempotente por `externalId` y reutilizando equipos por nombre/liga, en `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/config/PlayerCatalogDataSeeder.java`
- [ ] T023 [P] [US1] Probar mapeo, repositorio paginado y seeder idempotente contra H2 test en `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/catalog/persistence/PlayerRepositoryIT.java` y `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/config/PlayerCatalogDataSeederIT.java`
- [ ] T024 [P] [US1] Probar el servicio con repositorio mockeado, incluyendo página fuera de rango vacía, en `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/catalog/service/PlayerCatalogServiceTest.java`
- [ ] T025 [US1] Probar con MockMvc la respuesta, navegación y validaciones 400 de `GET /players` en `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/catalog/controller/PlayerControllerIT.java`

**Checkpoint**: El listado paginado, el dataset persistente y la navegación son funcionales y verificables sin filtros.

---

## Phase 4: User Story 2 - Acotar jugadores por criterios (Priority: P2)

**Goal**: Permitir filtros combinables por liga, equipo y posición manteniendo el orden y la paginación.

**Independent Test**: Cada filtro individual y combinación en `GET /players` devuelve exclusivamente jugadores que cumplen todos los criterios; una combinación válida sin coincidencias devuelve `200` y `content: []`.

### Implementation and tests for User Story 2

- [ ] T026 [US2] Crear el criterio puro opcional `PlayerFilter` que represente liga, equipo normalizado y posición en `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/catalog/modelo/PlayerFilter.java`
- [ ] T027 [US2] Extender la consulta JPA del repositorio para aplicar con AND los filtros opcionales y conservar `id` ascendente antes de paginar en `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/catalog/persistence/repository/PlayerRepository.java`
- [ ] T028 [US2] Extender `PlayerCatalogService` para recibir `PlayerFilter` y delegar la consulta filtrada en `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/catalog/service/PlayerCatalogService.java`
- [ ] T029 [US2] Añadir parámetros `league`, `team` y `position`, normalización de espacios y validación HTTP al listado en `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/catalog/controller/PlayerController.java`
- [ ] T030 [P] [US2] Probar filtros individuales, combinados y sin coincidencias contra H2 en `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/catalog/persistence/PlayerRepositoryIT.java`
- [ ] T031 [P] [US2] Probar la coordinación de filtros en el servicio con Mockito en `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/catalog/service/PlayerCatalogServiceTest.java`
- [ ] T032 [US2] Probar con MockMvc filtros válidos, combinación AND, parámetros enum inválidos y equipo vacío en `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/catalog/controller/PlayerControllerIT.java`

**Checkpoint**: Las consultas filtradas son independientes, deterministas y preservan la semántica de resultado vacío válido.

---

## Phase 5: User Story 3 - Consultar un jugador específico (Priority: P3)

**Goal**: Exponer el detalle público de un jugador y un 404 claro cuando el id no existe.

**Independent Test**: `GET /players/{id}` entrega los cinco campos del DTO para un id existente y devuelve el `ApiError` en español con 404 para un id inexistente.

### Implementation and tests for User Story 3

- [ ] T033 [US3] Añadir la búsqueda de jugador por id que devuelve modelo o ausencia en `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/catalog/persistence/repository/PlayerRepository.java`
- [ ] T034 [US3] Añadir la resolución de detalle y el lanzamiento de `PlayerNotFoundException` en `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/catalog/service/PlayerCatalogService.java`
- [ ] T035 [US3] Implementar `GET /players/{id}` y mapear el modelo a `PlayerResponse` en `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/catalog/controller/PlayerController.java`
- [ ] T036 [P] [US3] Probar la resolución existente e inexistente del servicio con Mockito en `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/catalog/service/PlayerCatalogServiceTest.java`
- [ ] T037 [US3] Probar con MockMvc el detalle exitoso, id inválido y 404 sin filtrado de datos en `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/catalog/controller/PlayerControllerIT.java`

**Checkpoint**: El detalle y el error de no encontrado cumplen el contrato sin depender de los filtros.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Validar el contrato completo, perfiles y definición de terminado.

- [ ] T038 [P] Validar que el contrato y ejemplos documentan los comportamientos finales de 200, 400 y 404 en `specs/002-catalogo-jugadores/contracts/players-api.yaml` y `specs/002-catalogo-jugadores/quickstart.md`
- [ ] T039 Ejecutar `test` y `build`, y registrar la verificación de perfiles en `specs/002-catalogo-jugadores/quickstart.md`
- [ ] T040 Ejecutar tres reinicios con perfil local y documentar que no hay duplicados ni relaciones inconsistentes en `specs/002-catalogo-jugadores/quickstart.md`
- [ ] T041 Revisar la separación controller→service→repository→DAO, DTOs, modelo puro, mensajes españoles y ausencia de dependencias nuevas en `specs/002-catalogo-jugadores/plan.md`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: no tiene dependencias.
- **Foundational (Phase 2)**: depende de Setup y bloquea todas las historias.
- **US1 (Phase 3)**: depende de Foundational; entrega el MVP y crea el catálogo base.
- **US2 (Phase 4)**: depende de la ruta de listado, servicio y repositorio de US1.
- **US3 (Phase 5)**: depende de las entidades, mapper, repositorio y DTO de US1; es independiente de los filtros de US2.
- **Polish (Phase 6)**: depende de las historias que se decidan entregar.

### User Story Dependency Graph

```text
Setup → Foundational → US1 (MVP) → US2
                              └──→ US3
US2 + US3 → Polish
```

### Parallel Opportunities

- T005 y T006; T009–T015; T019 y T021; y T023–T024 pueden dividirse entre personas al respetar sus dependencias declaradas.
- Tras completar US1, US2 y US3 pueden ejecutarse en paralelo, pues modifican en parte los mismos puntos de integración; coordinar la edición de `PlayerController`, `PlayerCatalogService` y `PlayerRepository` para evitar conflictos.
- Las tareas marcadas `[P]` trabajan en archivos distintos o en conjuntos de pruebas separados y pueden ejecutarse a la vez.

## Parallel Example: User Story 1

```text
Task: "Crear League y Position en catalog/modelo/League.java y catalog/modelo/Position.java"
Task: "Crear Team en catalog/modelo/Team.java"
Task: "Crear Player en catalog/modelo/Player.java"
Task: "Crear PlayerPage en catalog/modelo/PlayerPage.java"

Task: "Crear TeamSQL y PlayerSQL en catalog/persistence/sql/entity/"
Task: "Crear el dataset en src/main/resources/data/players.json"
Task: "Crear DTOs de respuesta en catalog/controller/dto/"
```

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Completar Setup y Foundational.
2. Implementar y probar US1 hasta T025.
3. Ejecutar la prueba independiente del listado, paginación y carga idempotente.
4. Demostrar o desplegar el MVP si procede.

### Incremental Delivery

1. Entregar US1: catálogo inicial y listado paginado.
2. Añadir US2: filtros combinables con resultados vacíos válidos.
3. Añadir US3: detalle y 404 consistente.
4. Completar comprobaciones transversales de la Phase 6.

## Notes

- Todas las tareas cumplen el formato obligatorio de checklist: checkbox, ID secuencial, etiquetas `[P]` y `[USn]` cuando corresponde, y rutas exactas.
- Los tests se escriben junto con su implementación y deben pasar al cerrar cada tarea; no se adopta TDD, conforme a la constitución.
