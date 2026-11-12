# Feature Specification: Tests de arquitectura del backend y del frontend

**Feature Branch**: `010-tests-arquitectura`

**Created**: 2026-11-12

**Status**: Draft

**Input**: User description: "Tests de arquitectura de toda la aplicación, backend y frontend (Entrega 3, ítem Core
'Crear un test de arquitectura'). Para el backend, la cátedra pide usar ArchUnit (https://www.archunit.org/). Hoy las
reglas de arquitectura de la constitución se controlan solo a mano en la revisión de código. En el backend son los
Principios I, III y V; en el frontend, la sección Frontend (estructura de carpetas y reglas). Queremos que se verifiquen
solas. Hace falta un conjunto de tests que analice el código del backend y el del frontend y haga fallar el build cuando
alguien rompe una regla. El mensaje de error va en español y dice qué regla se rompió, qué clase o archivo la rompió y
por qué existe la regla. Historia 1 (P1): reglas del backend [siete grupos de reglas]. Historia 2 (P2): reglas del
frontend, sobre src/ y tests/ [diez reglas]. Requisitos para los dos lados, la corrección de TopBar y lo que queda fuera
de alcance." El detalle de cada regla del pedido quedó volcado, sin cambios de fondo, en los requerimientos funcionales.

## Clarifications

### Session 2026-11-12

Decisiones del equipo que llegaron con el pedido (firmes):

- Q: ¿Con qué se verifica el backend? -> A: Con ArchUnit, porque lo pide la cátedra. Las verificaciones corren con
  `./gradlew test` y `./gradlew build`.
- Q: ¿Y el frontend? -> A: Con `npm test`. No se pidió una herramienta en particular.
- Q: ¿Dónde corren? -> A: En el CI que ya existe, sin levantar Spring, la base ni el navegador, y tienen que ser rápidas.
- Q: ¿Qué código se analiza? -> A: Solo el de producción. Las clases y archivos de test no cuentan como infractores,
  salvo en la regla F10, que trata justamente sobre los tests.
- Q: ¿Cómo se demuestra que una regla chequea algo? -> A: Cada regla tiene un caso feliz (el código actual la cumple) y
  un caso borde (detecta una violación real).
- Q: ¿El código actual cumple las reglas? -> A: Todas menos una: `src/components/TopBar.tsx` importa `Link` de
  `react-router` para el link del logo, y eso rompe la regla F1. Se corrige en esta feature: TopBar recibe ese link por
  props, igual que ya recibe el link de la cuenta.
- Q: ¿Qué pasa si aparece otra violación? -> A: No se afloja ni se congela la regla en silencio. Se avisa al equipo y
  se decide. Fuera de la corrección de TopBar, la feature no cambia código de producción.
- Q: ¿Dónde se documentan las reglas? -> A: En el README: qué verifica cada una y cómo correrlas. Se usan en las Release
  Notes de la entrega 3.

Lo que encontró la revisión del código al escribir esta spec. Las reglas del pedido se probaron a mano contra el código
actual:

- Se confirmó que hoy la única violación es TopBar. Solo la usa `src/pages/AppLayout.tsx` y no tiene un test propio en
  `tests/components/`.
- Hay un caso que, leído al pie de la letra, sería una segunda violación: los tipos anidados dentro de los DTO del
  controller. Lo decidió el equipo en las respuestas de abajo (FR-022).
- El resto son lecturas de las reglas que hacen falta para que no den falsos positivos sobre código que cumple la
  constitución. Están en Edge Cases y en Assumptions.

Respuestas de la clarificación:

- Q: ¿La regla de nombres de los DTO del controller (B4.2) alcanza a los tipos anidados dentro de un Request o
  Response? → A: No. Alcanza solo a las clases de primer nivel, una por archivo. Los tipos anidados son parte de su
  Request o Response. Los nueve anidados de hoy no son una violación y no se renombran (FR-022).
- Q: ¿Se suman dos reglas de la constitución que la lista no cubre y que hoy el código cumple: (a) el servicio no usa
  las clases `*SQL` (Principio I) y (b) el árbol de carpetas del frontend es cerrado (sección Frontend)? → A: Solo la
  (a). Se suma como B3.6 (FR-017). La (b) no se verifica automáticamente: sigue controlándose en la revisión de código.
- Q: ¿Todo el texto del mensaje va en español, incluido el detalle de cada violación que genera la herramienta? → A: No
  hace falta. Van en español la regla (identificador y nombre), el porqué y la lista de infractores. La línea de detalle
  de cada violación puede quedar con la redacción de la herramienta, siempre que nombre al infractor (FR-004).

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Las reglas de arquitectura del backend se verifican solas (Priority: P1)

Un integrante del equipo cambia código del backend y corre los tests o el build, en su máquina o en el CI. Si el cambio
rompe una regla de arquitectura de la constitución, el build falla. Por ejemplo, rompe la regla si un controller usa un
repository, si una clase de modelo tiene una anotación de persistencia o si un endpoint devuelve un objeto de modelo. El
mensaje dice qué regla se rompió, qué clase la rompió y por qué existe la regla. Hoy esas reglas dependen de que alguien
las vea en la revisión del PR.

**Why this priority**: es el ítem Core de la entrega 3 que pide la cátedra, y el backend es donde la constitución pone
las reglas NO NEGOCIABLES (Principio I). Sola, esta historia ya cumple la consigna.

**Independent Test**: se corre el build del backend sobre el código actual y pasa. Después se corre cada caso borde, que
arma un ejemplo de violación solo para el test, y se comprueba que la regla falla y nombra al infractor.

**Acceptance Scenarios**:

1. **Given** el código actual del backend, **When** se corre `./gradlew test` o `./gradlew build`, **Then** las 20
   reglas del backend pasan y el build termina bien.
2. **Given** un controller que inyecta un repository, **When** se corren las verificaciones, **Then** falla la regla B1
   (dirección de capas). El mensaje, en español, nombra al controller y al repository y explica que el controller solo
   habla con el servicio.
3. **Given** una clase de modelo con una anotación de persistencia, **When** se corren las verificaciones, **Then** fallan
   las reglas B2 (modelo puro) y B3.1 (JPA solo en `persistence/sql`). Cada una nombra la clase.
4. **Given** un servicio que recibe o devuelve una entidad `*SQL`, **When** se corren las verificaciones, **Then** falla
   la regla B3.6 y nombra el servicio y la entidad.
5. **Given** un endpoint que devuelve un objeto de modelo, directo o dentro de una respuesta HTTP genérica o de una
   lista, **When** se corren las verificaciones, **Then** falla la regla B4.3 y nombra el método del endpoint.
6. **Given** dos contextos del servicio que se usan mutuamente, **When** se corren las verificaciones, **Then** falla la
   regla B6 y el mensaje muestra el ciclo.
7. **Given** una clase que escribe en la salida estándar, lanza una `RuntimeException` o tiene un campo inyectado,
   **When** se corren las verificaciones, **Then** falla la regla correspondiente (B7.1, B7.2 o B7.4).
8. **Given** una clase de test que hace algo que una regla prohíbe, **When** se corren las verificaciones sobre el
   código de producción, **Then** ninguna regla la informa como infractora.
9. **Given** un cambio que rompe varias reglas a la vez, **When** se corre el build, **Then** el reporte muestra todas
   las reglas rotas y todos sus infractores en la misma corrida.

---

### User Story 2 - Las reglas de arquitectura del frontend se verifican solas (Priority: P2)

Un integrante cambia código del frontend y corre `npm test`, en su máquina o en el CI. Si el cambio rompe una regla de
estructura de la sección Frontend de la constitución, la suite falla. Por ejemplo, la rompe si un componente usa un
hook o el router, si una página llama a `fetch` o si aparece un test dentro de `src/`. El mensaje dice qué regla se
rompió, qué archivo la rompió y por qué existe la regla.

**Why this priority**: completa la consigna para toda la aplicación. Depende solo de la corrección de TopBar, no de la
historia 1, pero va segunda porque la cátedra pide explícitamente el test del backend.

**Independent Test**: se corre `npm test` sobre el código actual, con TopBar ya corregido, y pasa. Después se corre cada
caso borde, que arma un ejemplo de violación solo para el test, y se comprueba que la regla falla y nombra el archivo.

**Acceptance Scenarios**:

1. **Given** el código actual del frontend con TopBar corregido, **When** se corre `npm test`, **Then** las 13 reglas
   del frontend pasan junto con el resto de la suite.
2. **Given** el código actual sin corregir TopBar, **When** se corren las verificaciones, **Then** falla la regla F1 y
   el mensaje nombra `src/components/TopBar.tsx` y el import de `react-router`.
3. **Given** TopBar corregido, **When** un usuario usa el logo de la barra superior, **Then** el logo sigue llevando a la
   pizarra, con el mismo texto accesible y el mismo acceso por teclado que antes.
4. **Given** un componente que importa un hook, un service, un contexto, una página o el router, **When** se corren las
   verificaciones, **Then** falla la regla F1 y nombra el archivo y el import.
5. **Given** un archivo fuera de `services/` que llama a `fetch` o usa `sessionStorage` o `localStorage`, **When** se
   corren las verificaciones, **Then** falla la regla F7 y nombra el archivo.
6. **Given** un import de un paquete que no es `react`, `react-dom` ni `react-router`, por ejemplo un framework de UI,
   **When** se corren las verificaciones, **Then** falla la regla F8.1 y nombra el archivo y el paquete.
7. **Given** dos archivos de `src/` que se importan mutuamente, directa o indirectamente, **When** se corren las
   verificaciones, **Then** falla la regla F9 y el mensaje muestra el ciclo completo.
8. **Given** un archivo `.test` o `.spec` dentro de `src/`, o un test en `tests/utils/` sin un archivo del mismo nombre
   en `src/utils/`, **When** se corren las verificaciones, **Then** falla la regla F10.1 o F10.2 y nombra el archivo.
9. **Given** un test en `tests/pages/` que prueba un flujo completo y no tiene una página del mismo nombre, **When** se
   corren las verificaciones, **Then** la regla F10.2 no lo informa.

---

### User Story 3 - Las reglas quedan documentadas para la entrega (Priority: P3)

Un integrante o el docente abre el README. Encuentra la lista de reglas de arquitectura del backend y del frontend, qué
verifica cada una, por qué existe y cómo correrlas. El equipo copia esa lista a las Release Notes de la entrega 3.

**Why this priority**: es necesaria para la entrega, pero no cambia qué se verifica. Se escribe al final, sobre las
reglas ya implementadas.

**Independent Test**: un integrante que no implementó la feature lee el README y puede decir, para cada regla, qué
verifica y con qué comando se corre, sin abrir el código de los tests.

**Acceptance Scenarios**:

1. **Given** la feature terminada, **When** se lee el README, **Then** aparecen las 33 reglas con su identificador (B1 a
   B7, F1 a F10), qué verifica cada una, por qué existe (el principio o la sección de la constitución) y los comandos
   para correrlas.
2. **Given** el mensaje de error de una regla rota, **When** se busca el identificador de la regla en el README,
   **Then** se encuentra su explicación.

---

### Edge Cases

Lecturas de las reglas para no dar falsos positivos sobre código que cumple la constitución. Cada una se comprobó contra
el código actual:

- **Clases del paquete raíz**: `FutbolMarketApplication` y `StatsSnapshotExport` (la exportación de estadísticas) son
  clases de arranque y pueden usar el servicio (B1). `StatsSnapshotExport` hoy lo usa.
- **Security usa el servicio**: `CredentialAuthenticationFilter` y `SecurityConfig` usan el servicio, y B1 lo permite.
- **Clases auxiliares en `persistence/sql/entity`**: `MarketColumns` está en esa carpeta, pero no es una entidad (son
  constantes de columnas). B3.2 se aplica a las clases marcadas como entidad o embebible de JPA, no a toda clase de la
  carpeta.
- **Paginación de Spring Data**: los repositories de `persistence/repository` usan los tipos de paginación y orden de
  Spring Data (`Page`, `Pageable`, `PageRequest`, `Sort`). No son JPA, así que B3.1 no los cuenta. Lo que B3.1 restringe
  a `persistence/sql` es la API de persistencia de Java, su implementación (Hibernate) y Spring Data JPA.
- **`@Autowired` en constructores**: `WhoScoredAdapter`, `AuthService` y `AdminAccountInitializer` tienen `@Autowired`
  sobre el constructor, porque tienen más de uno. Eso es inyección por constructor. B7.4 prohíbe solo los campos
  anotados para inyección.
- **Excepciones propias que heredan de las genéricas**: B7.2 prohíbe crear y lanzar directamente `Exception`,
  `RuntimeException`, `Throwable` o `Error`, no lanzar subclases propias con nombre.
- **Menciones en comentarios**: un comentario que nombra `@RestControllerAdvice` (como el de `ApiErrorResponseWriter`)
  o `sessionStorage` (como los de `SessionContext.tsx` y `types/board.ts`) no es un uso. Las reglas miran el código, no
  el texto.
- **Módulos propios con nombres de la plataforma**: `context/SessionContext.tsx` importa el módulo
  `services/sessionStorage`. Eso es usar `services/`, no el almacenamiento del navegador, y F7 no lo cuenta.
- **Formas indirectas de `fetch` y del almacenamiento**: `window.fetch`, `globalThis.fetch`, `window.sessionStorage` y
  equivalentes cuentan como uso para F7.
- **Imports solo de tipos**: en el frontend, un import que trae solo tipos cuenta como dependencia para F1 a F6 y F9. Si
  un componente necesita un tipo de un hook, ese tipo va en `types/`.
- **Subrutas de paquetes permitidos**: `react-dom/client` es parte de `react-dom` y F8.1 lo permite.
- **Estilos**: que un componente importe su propio CSS Module, en la misma carpeta, no rompe F1. Los archivos CSS no se
  analizan, porque los estilos están fuera de alcance.
- **Tests de hooks con JSX**: `tests/hooks/useCatalog.test.tsx` corresponde a `src/hooks/useCatalog.ts`. F10.2 compara el
  nombre base del archivo, sin la extensión `.ts` o `.tsx`.
- **Archivos de apoyo de los tests**: `tests/mocks/` y `tests/setup.ts` no son tests de una carpeta de `src/`, así que
  F10.2 no los revisa. `tests/pages/` queda afuera, porque ahí también van flujos completos.
- **Tipos anidados en los DTO del controller**: `AuditPartyResponse.Role`, `AuditPartyResponse.State`,
  `BoostWeekRequest.Boost` y los seis records anidados de `StatsSyncReportResponse` no terminan en `Request` ni
  `Response`. Son parte de su clase de primer nivel, así que la regla de nombres de B4.2 no los cuenta. La regla de uso
  de B4.2 sí los alcanza.
- **El servicio y las entidades a través del repository**: los repositories usan entidades `*SQL` por dentro. B3.6 mira
  solo las dependencias directas del servicio, así que usar un repository no la rompe.
- **Tipos genéricos anidados en un endpoint**: B4.3 revisa el tipo que devuelve el método del endpoint en cualquier nivel
  de anidamiento (`ResponseEntity<List<X>>`, `Optional<X>`, `Page<X>`, arreglos de `X`). No revisa los campos de los
  Response: hoy `PlayerResponse` expone los enums `League` y `Position` del modelo, y eso no rompe B4.3.
- **Formas de declarar queries nativas**: B3.5 cuenta la opción nativa de la anotación de consultas y también las otras
  formas que ofrece el framework, como la anotación específica de query nativa y la creación de una query nativa en
  tiempo de ejecución.
- **Una regla que no chequea nada**: si una regla queda mal escrita o analiza el conjunto equivocado de clases o
  archivos, por ejemplo uno vacío, su caso borde falla. Así el error no pasa desapercibido.

## Requirements *(mandatory)*

### Functional Requirements

#### Requisitos comunes a backend y frontend

- **FR-001**: `./gradlew test` y `./gradlew build` del backend MUST correr las verificaciones de arquitectura del backend
  y fallar cuando cualquiera de sus reglas se rompe.
- **FR-002**: `npm test` del frontend MUST correr las verificaciones de arquitectura del frontend y fallar cuando
  cualquiera de sus reglas se rompe.
- **FR-003**: Las verificaciones MUST correr en los workflows de CI existentes, dentro de los comandos que ya ejecutan,
  sin levantar la aplicación, la base de datos ni un navegador, y sin pedir acceso a la red.
- **FR-004**: Cuando una regla se rompe, el mensaje MUST decir en español qué regla se rompió (su identificador y su
  nombre) y por qué existe, citando el principio o la sección de la constitución. MUST listar también cada clase o
  archivo infractor con la dependencia o el uso concreto que rompe la regla. Lo que escribe el equipo va en español: el
  nombre de la regla, el porqué y el encabezado de la lista. La línea de detalle de cada violación puede quedar con la
  redacción que genera la herramienta, siempre que nombre al infractor.
- **FR-005**: Cada regla MUST informarse por separado. Una corrida muestra todas las reglas rotas y, para cada una,
  todos sus infractores, no solo el primero.
- **FR-006**: Las verificaciones MUST analizar solo el código de producción: en el backend, las clases de
  `backend/src/main`; en el frontend, los archivos `.ts` y `.tsx` de `frontend/src/`. Las clases y archivos de test
  nunca cuentan como infractores, salvo en las reglas F10.1 y F10.2.
- **FR-007**: Cada regla MUST tener un caso feliz, que verifica que el código de producción actual la cumple, y al menos
  un caso borde, que arma un ejemplo de violación solo para el test y verifica que la regla falla y nombra al infractor.
  Los ejemplos de violación viven solo en el código de test y nunca entran en el análisis del código de producción.
- **FR-008**: Las reglas MUST NOT tener excepciones por clase o por archivo, violaciones congeladas ni partes
  desactivadas. Si al implementar aparece una violación que esta spec no prevé, la implementación se detiene en esa regla
  y se consulta al equipo. No se afloja la regla para que pase.
- **FR-009**: Los tests de las verificaciones MUST seguir las convenciones de tests de la constitución: casos felices y
  de borde, estructura setup - execute - verify y nombres descriptivos en español. No se modifica ni se borra ningún
  test existente.

#### Historia 1: reglas del backend

Los paquetes se nombran relativos a `ar.edu.unq.desapp.futbolmarket`. "Depender" incluye usar un tipo como campo,
parámetro, retorno, argumento genérico, anotación, herencia, llamada o acceso.

- **FR-010 (B1, dirección de capas)**: Las dependencias entre capas MUST ir solo en la dirección
  controller -> service -> persistence, y service -> adapter:
  - ninguna clase fuera de `controller` depende de `controller`;
  - de `service` solo dependen `controller`, `security` y las clases del paquete raíz;
  - de `persistence` solo depende `service`;
  - de `adapter` solo depende `service`.
- **FR-011 (B2, modelo puro)**: Las clases de `modelo` MUST depender solo de la JDK (`java.*`), de otras clases de
  `modelo`, de las excepciones base de `shared` y de Lombok. En particular, no dependen de JPA, de Spring ni de
  `controller`, `service`, `persistence`, `adapter`, `security` o `config`. De `shared`, solo pueden usar las excepciones
  base.
- **FR-012 (B3.1, JPA confinado)**: Las clases fuera de `persistence.sql` (y sus subpaquetes) MUST NOT depender de JPA. JPA
  es la API de persistencia de Java, su implementación (Hibernate) y Spring Data JPA.
- **FR-013 (B3.2, entidades)**: Toda clase marcada como entidad o como embebible de JPA MUST vivir en
  `persistence.sql.entity` (o en un contexto dentro de ese paquete) y su nombre MUST terminar en `SQL`.
- **FR-014 (B3.3, SQLDAO)**: Todo tipo que extiende `JpaRepository`, que vive en `persistence.sql.interfaces` o cuyo
  nombre termina en `SQLDAO` MUST cumplir las cuatro condiciones a la vez:
  - es una interfaz;
  - vive en `persistence.sql.interfaces` (o en un contexto dentro de ese paquete);
  - su nombre termina en `SQLDAO`;
  - extiende `JpaRepository`.
- **FR-015 (B3.4, uso de SQLDAO y mappers)**: Los SQLDAO MUST usarse solo desde `persistence.repository`. Los mappers
  (`persistence.mapper`) MUST usarse solo desde `persistence.repository` y desde otros mappers.
- **FR-016 (B3.5, sin queries nativas)**: Las consultas MUST NOT declararse como nativas, de ninguna de las formas que
  ofrece el framework.
- **FR-017 (B3.6, servicio sin entidades)**: Las clases de `service` MUST NOT depender de las clases de
  `persistence.sql.entity`. El servicio recibe y devuelve modelo y no conoce las clases `*SQL` (Principio I). Junto con
  B3.4, esto deja al servicio usando solo `persistence.repository`.
- **FR-020 (B4.1, controllers REST)**: Toda clase anotada como controller REST MUST vivir en `controller` (en un
  contexto) y su nombre MUST terminar en `Controller`.
- **FR-021 (B4.2, DTO del controller)**: Las clases de `controller.<contexto>.dto` MUST usarse solo desde `controller`.
- **FR-022 (B4.2, nombres de los DTO)**: Las clases de primer nivel de `controller.<contexto>.dto` (una por archivo)
  MUST terminar en `Request` o `Response`. Los tipos anidados dentro de un Request o Response son parte de él y no
  necesitan el sufijo. Hoy hay nueve: `AuditPartyResponse.Role`, `AuditPartyResponse.State`, `BoostWeekRequest.Boost`
  y seis records anidados de `StatsSyncReportResponse` (`RunCutReport`, `LeagueStatsReport`, `SkippedStatsMatch`,
  `UncrossedTeam`, `UncrossedPlayer` y `SnapshotSkipCount`). FR-021 sí los alcanza: tampoco se usan fuera de
  `controller`.
- **FR-023 (B4.3, qué devuelve un endpoint)**: Los métodos de endpoint de un controller REST MUST NOT devolver una clase
  de `modelo` ni de `persistence`. La regla vale para el tipo directo y para cualquier argumento genérico, en cualquier
  nivel: dentro de una respuesta HTTP genérica, de una lista o colección, de un opcional o de una página. Vale también
  para los arreglos.
- **FR-030 (B5.1, DTO del proveedor)**: Las clases de `adapter.<proveedor>.dto` MUST usarse solo desde `adapter`.
- **FR-031 (B5.2, adapter independiente)**: Las clases de `adapter` MUST NOT depender de `service`, `controller` ni
  `persistence`.
- **FR-032 (B5.3, clientes HTTP)**: Los clientes HTTP MUST usarse solo en `adapter` y `config`. Son los clientes REST
  del framework y el cliente HTTP de la JDK, con sus excepciones.
- **FR-040 (B6, servicios sin ciclos)**: Los contextos de `service` (`service.<contexto>`) MUST NOT formar ciclos de
  dependencias entre sí. Los contextos de `modelo` sí se pueden referenciar entre sí, porque el modelo es único, y
  ninguna regla lo impide.
- **FR-050 (B7.1)**: Las clases MUST NOT usar `System.out` ni `System.err`.
- **FR-051 (B7.2)**: Las clases MUST NOT crear y lanzar `Exception`, `RuntimeException`, `Throwable` ni `Error`.
- **FR-052 (B7.3)**: Las clases MUST NOT usar `java.util.logging`.
- **FR-053 (B7.4)**: Los campos MUST NOT estar anotados para inyección de dependencias. La inyección es por constructor.
- **FR-054 (B7.5)**: El backend MUST tener exactamente un manejador global de excepciones de controllers
  (`@RestControllerAdvice` o `@ControllerAdvice`), y MUST vivir en `shared`.

#### Historia 2: reglas del frontend

Las rutas son relativas a `frontend/`. "Usar" una carpeta es importar cualquier archivo de ella, incluso si el import
trae solo tipos.

- **FR-060 (F1, components)**: Los archivos de `src/components/` MUST usar solo `src/components/`, `src/types/`,
  `src/utils/` y el paquete `react`. No usan `services/`, `hooks/`, `context/`, `pages/`, `router/` ni el paquete
  `react-router`: reciben datos y callbacks por props.
- **FR-061 (F2, utils puro)**: Los archivos de `src/utils/` MUST usar solo `src/utils/` y `src/types/`. No usan React,
  ningún otro paquete ni `services/`.
- **FR-062 (F3, types)**: Los archivos de `src/types/` MUST usar solo `src/types/`.
- **FR-063 (F4, services)**: Los archivos de `src/services/` MUST usar solo `src/services/` y `src/types/`. No usan
  React ni ninguna carpeta de la interfaz.
- **FR-064 (F5, hooks y context)**: Los archivos de `src/hooks/` y `src/context/` MUST NOT usar `src/components/`,
  `src/pages/` ni `src/router/`.
- **FR-065 (F6.1, quién usa pages)**: Los archivos fuera de `src/router/` y `src/pages/` MUST NOT usar `src/pages/`.
- **FR-066 (F6.2, quién usa router)**: Los archivos de `src/` distintos de `src/main.tsx` MUST NOT usar `src/router/`,
  salvo los propios archivos de `src/router/` entre sí.
- **FR-067 (F7, red y almacenamiento)**: Los archivos fuera de `src/services/` MUST NOT llamar a `fetch` ni usar
  `sessionStorage` o `localStorage`, tampoco a través de `window` o `globalThis`.
- **FR-068 (F8.1, paquetes permitidos)**: Los archivos de `src/` MUST importar solo los paquetes `react`, `react-dom` y
  `react-router`, y sus subrutas. No hay frameworks de UI.
- **FR-069 (F8.2, nada de afuera)**: Los archivos de `src/` MUST NOT importar nada de `mockups/` ni de ninguna ruta
  fuera de `src/`.
- **FR-070 (F9, sin ciclos)**: Los imports entre archivos de `src/` MUST NOT formar ciclos, directos ni indirectos.
- **FR-071 (F10.1, tests fuera de src)**: `src/` MUST NOT contener archivos `.test.*` ni `.spec.*`.
- **FR-072 (F10.2, correspondencia de tests)**: Cada test de `tests/components/`, `tests/hooks/`, `tests/services/` y
  `tests/utils/` MUST corresponder a un archivo de `src/` con el mismo nombre base, en la carpeta del mismo nombre. Se
  compara el nombre sin `.test` ni la extensión: `tests/hooks/useCatalog.test.tsx` corresponde a
  `src/hooks/useCatalog.ts`. `tests/pages/`, `tests/mocks/` y `tests/setup.ts` quedan afuera.

#### Corrección de TopBar y documentación

- **FR-080**: `src/components/TopBar.tsx` MUST recibir el link del logo por props, igual que ya recibe el link de la
  cuenta, y MUST dejar de importar `react-router`. La página que arma la barra (`src/pages/AppLayout.tsx`) MUST pasarle
  ese link.
- **FR-081**: Para el usuario, el logo MUST comportarse igual que antes: lleva a la pizarra, con el mismo texto
  accesible ("La Pizarra, ir al inicio"), el mismo contenido visual y el mismo acceso por teclado.
- **FR-082**: Fuera de FR-080, la feature MUST NOT cambiar código de producción del backend ni del frontend.
- **FR-083**: El README MUST documentar cada regla con su identificador, qué verifica, por qué existe (principio o
  sección de la constitución) y cómo correr las verificaciones del backend y del frontend. La documentación debe poder
  copiarse a las Release Notes de la entrega 3.

### Key Entities

- **Regla de arquitectura**: una restricción verificable de la constitución. Tiene un identificador (B1 a B7 con sus
  sub-reglas en el backend, F1 a F10 en el frontend), un nombre, qué verifica, sobre qué código se aplica y por qué
  existe (el principio o la sección de la constitución que la origina).
- **Violación**: un incumplimiento concreto de una regla. Tiene la regla, el infractor (clase o archivo) y el detalle
  (la dependencia, el import, la llamada o el nombre que la rompe).
- **Ejemplo de violación**: código que vive solo en los tests y rompe una regla a propósito. Lo usa el caso borde de esa
  regla para demostrar que la regla detecta violaciones reales.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Las 33 reglas (20 del backend, con la B3.6 que se sumó en la clarificación, y 13 del frontend) se
  verifican automáticamente en cada corrida local y de CI. Ninguna depende solo de la revisión de código.
- **SC-002**: El 100 % de las reglas tiene un caso feliz y al menos un caso borde. Si se vacía o se desactiva la
  verificación de cualquier regla, su caso borde falla.
- **SC-003**: Con TopBar corregido, el código de producción actual pasa todas las reglas en la primera corrida, sin
  excepciones, violaciones congeladas ni reglas desactivadas.
- **SC-004**: Ante una violación introducida a propósito en cualquier regla, la corrida falla. Con solo leer el mensaje,
  un integrante sabe qué regla se rompió, qué clase o archivo la rompió y por qué existe la regla, sin abrir el código
  de las verificaciones.
- **SC-005**: Las verificaciones de cada lado agregan menos de 30 segundos a la corrida de tests en una máquina de
  desarrollo y en el CI.
- **SC-006**: El único cambio de código de producción es la corrección de TopBar (TopBar y la página que lo arma). Todos
  los tests existentes siguen pasando sin modificarse.
- **SC-007**: Un integrante que no implementó la feature encuentra en el README, para cada una de las 33 reglas, qué
  verifica, por qué existe y cómo correrla, en menos de 5 minutos.

## Assumptions

- Los "usuarios" de esta feature son los dos desarrolladores del equipo y el docente que corrige la entrega. La única
  parte visible para el usuario final es el logo de TopBar, que no cambia de comportamiento.
- "Cada regla propia" se interpreta como cada regla de esta feature. Las que reutilizan un chequeo ya hecho por la
  herramienta (salida estándar, excepciones genéricas, `java.util.logging`, inyección por campo, ciclos) también llevan
  su caso borde. El riesgo de que no chequeen nada, por ejemplo por analizar el conjunto equivocado de clases, es el
  mismo.
- Las sub-reglas (B3.1 a B3.6, B4.1 a B4.3, B5.1 a B5.3, B7.1 a B7.5, F6.1 y F6.2, F8.1 y F8.2, F10.1 y F10.2) cuentan
  como reglas, cada una con su caso feliz y su caso borde. Por eso son 20 y 13.
- ArchUnit entra como dependencia de test del backend, con la justificación de que lo pide la cátedra. Se declara en el
  plan, como exige la constitución para toda dependencia nueva. En el frontend se prefiere no agregar dependencias. Si
  hace falta una, se avisa y se justifica en el plan.
- Las verificaciones del frontend no crean carpetas nuevas en `src/` ni en `tests/`, porque ese árbol es cerrado en la
  constitución y cambiarla está fuera de alcance. Los ejemplos de violación del frontend se arman dentro de los propios
  tests. El paquete de test de las verificaciones del backend se declara en el plan con el aval de un desarrollador,
  porque el árbol del backend no es cerrado.
- Los workflows de CI no necesitan cambios: el backend ya corre `./gradlew build` y el frontend ya corre `npm test`.
- El código nuevo de tests cumple el Principio V y no suma issues de SonarCloud.
- Corregir TopBar no exige modificar tests existentes: TopBar no tiene un test propio y los tests de páginas siguen
  viendo el mismo link. Si alguno fallara, se arregla el código, no el test (Principio IV).

**Fuera de alcance**:

- Cambios en la constitución.
- Reglas sobre lógica que un análisis de dependencias no puede verificar: modelo rico, mappers sin lógica, qué estado
  vive en un componente, traducción de errores del proveedor.
- Reglas de estilo (CSS, tokens), que no son de arquitectura. Los archivos CSS no se analizan.
- Verificar automáticamente que el árbol de carpetas del frontend sea cerrado (sección Frontend). El equipo decidió no
  sumar esa regla en la clarificación: sigue controlándose en la revisión de código.
- Agregar o quitar pasos en los workflows de CI.
