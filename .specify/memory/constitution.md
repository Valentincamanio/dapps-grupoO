<!--
SYNC IMPACT REPORT
==================
Version change: 2.2.0 -> 2.2.1
Bump rationale: PATCH. Aclaracion del Principio III: la lista de status del advice
suma el 503 y deja de leerse como una lista cerrada. No se quita ni se redefine
ningun principio.

Principios modificados:
  - III. Cada Validacion en su Nivel  [aclarado]
      * Status del advice: (400, 401, 403, 404, 409) -> (400, 401, 403, 404, 409 y 503).
      * Esos status son la base: una feature puede sumar otro con el aval explicito
        del desarrollador que la implementa, declarandolo en su plan.

Secciones modificadas: ninguna.
Secciones agregadas: ninguna.
Secciones removidas: ninguna.

Plantillas a revisar:
  - .specify/templates/plan-template.md   seccion de estructura de codigo
    (contemplar frontend/src, frontend/tests y adapter/<proveedor>/)  (pendiente)
  - .specify/templates/tasks-template.md  rutas de ejemplo del frontend y del
    adapter                                                           (pendiente)
  - .specify/templates/spec-template.md   sin impacto

Historico de la 2.2.0 (2.1.0 -> 2.2.0, MINOR): se agrego la capa adapter (cinco capas,
con sus reglas y adapter/<proveedor>/dto/ en el arbol), el arbol de paquetes del backend
dejo de ser una lista cerrada (se cambia con el aval de un desarrollador, declarado en el
plan) y adapter/ se organiza por proveedor. El arbol del frontend sigue siendo cerrado.

Historico de la 2.1.0 (2.0.0 -> 2.1.0, MINOR): se agrego la seccion Frontend con su
stack, estructura de carpetas, reglas, tests y definicion de terminado; el Principio
VII aplica tambien al frontend.

Historico de la 2.0.0 (1.0.0 -> 2.0.0, MAJOR): se redefinio el Principio I
(organizacion por capa -> contexto), se amplio el IV y se reformulo la excepcion del VII.

TODOs diferidos: ninguno.
-->

# futbol-market Constitution

Mercado simulado de valoracion de jugadores de futbol. API REST en Spring Boot para
la materia Desarrollo de Aplicaciones (UNQ).

El proyecto YA EXISTE y tiene un esqueleto funcionando. Esta constitucion gobierna
como se lo extiende, no como se lo crea.

## Core Principles

### I. Arquitectura en Capas Estricta (NO NEGOCIABLE)

Cinco capas: controller, servicio, modelo, persistencia y adapter. La direccion de
las dependencias es unica y no admite atajos.

    Controller  ->  Service  ->  Repository  ->  SQLDAO  ->  H2
                        |             |
                        v             v
                     Modelo  <--   Mapper   -->  *SQL

    Service  ->  Adapter  ->  API externa (Football-Data, WhoScored)
                    |
                    v
                 Modelo

Reglas verificables, sin excepciones:

- Las clases de modelo NO llevan anotaciones de persistencia. Ningun `@Entity`,
  `@Table`, `@Id`, `@Column`, `@ManyToOne` ni cualquier otra anotacion de JPA sobre
  una clase de modelo. El modelo no importa `jakarta.persistence`, ni
  `org.springframework`, ni nada del paquete `controller`.
- Cada clase de modelo tiene su contraparte en persistencia con sufijo `SQL`:
  `Player` -> `PlayerSQL`, `Team` -> `TeamSQL`, `AppUser` -> `AppUserSQL`. Las
  anotaciones de JPA viven UNICAMENTE en las clases `*SQL`.
- Existe un mapper explicito en ambas direcciones (`PlayerMapper.toDomain` /
  `toSQL`). El mapper traduce campo a campo y NO contiene logica de negocio.
- El controller solo habla con el servicio. Nunca inyecta un repository, un SQLDAO
  ni un mapper.
- Las clases `Request` y `Response` viven en `controller/<contexto>/dto/` y NUNCA
  cruzan hacia el servicio. El controller las convierte antes de delegar y arma la
  respuesta a partir de lo que el servicio le devuelve.
- El servicio recibe y devuelve objetos de modelo. No conoce JPA ni las clases `*SQL`.
- Ninguna clase de persistencia ni de modelo sale del backend en una respuesta HTTP.
  Lo que sale es siempre un `Response` del paquete `dto`.
- El servicio habla con una API externa SOLO a traves de su adapter. No usa un
  cliente HTTP ni conoce el JSON del proveedor.
- El adapter recibe y devuelve objetos de modelo. Los DTO del proveedor viven en
  `adapter/<proveedor>/dto/` y NUNCA salen del adapter.
- El adapter traduce con su propio mapper, campo a campo y sin logica de negocio.
  No persiste: lo que trae lo guarda el servicio a traves de los repositories.
- Los errores del proveedor (timeout, 4xx, 5xx, limite de requests) se traducen a
  excepciones propias. Nunca se propaga una excepcion del cliente HTTP.

Estructura de paquetes. El sistema es un monolito. Se organiza primero POR CAPA y,
dentro de cada capa, POR CONTEXTO del dominio. Este arbol es la base de la estructura:

    ar.edu.unq.desapp.futbolmarket
    +-- controller/
    |   +-- <contexto>/           controllers del contexto
    |       +-- dto/              Request y Response del contexto
    +-- service/
    |   +-- <contexto>/
    +-- modelo/
    |   +-- <contexto>/           modelo puro, sin anotaciones
    |       +-- exception/        excepciones de dominio del contexto
    +-- persistence/
    |   +-- repository/
    |   |   +-- <contexto>/       envuelve al SQLDAO e invoca al mapper
    |   +-- mapper/
    |   |   +-- <contexto>/       toDomain / toSQL
    |   +-- sql/
    |       +-- entity/
    |       |   +-- <contexto>/   clases *SQL con anotaciones JPA
    |       +-- interfaces/
    |           +-- <contexto>/   *SQLDAO extends JpaRepository
    +-- adapter/
    |   +-- <proveedor>/          una API externa: footballdata, whoscored
    |       +-- dto/              JSON del proveedor; nunca sale del adapter
    +-- security/                 transversal: filtros, JwtService, SecurityConfig
    +-- config/                   transversal: clases @Configuration
    +-- shared/                   transversal: ApiError, bases de excepcion y
                                  RestControllerAdvice

El arbol no es una lista cerrada. Se pueden agregar, mover o quitar carpetas con el
aval explicito de al menos uno de los dos desarrolladores; el cambio se declara en el
plan de la feature que lo introduce. Toda carpeta nueva respeta las reglas de este
principio: direccion de dependencias, modelo sin anotaciones y DTO que no cruzan capas.

Contextos vigentes: `user`, `player`, `team`, `league`, `position` y `auth`. Las entregas
siguientes agregan los suyos (por ejemplo `market`) con el mismo criterio.

Reglas de los contextos:

- Un contexto es una carpeta DENTRO de una capa, nunca una capa nueva ni un modulo
  aparte. No existen paquetes de primer nivel por feature (`auth/`, `catalog/`,
  `market/`).
- El nombre de un contexto es un sustantivo del dominio, en ingles y en singular.
- Un contexto aparece solo en las capas donde tiene clases. No se crean carpetas vacias.
- Si una clase sirve a varios contextos, va en el contexto de la entidad principal a la
  que pertenece. Ante la duda, se pregunta al equipo.
- `security/`, `config/` y `shared/` son transversales y no se dividen por contexto.
- `adapter/` se organiza por proveedor (`footballdata`, `whoscored`), no por contexto:
  un mismo proveedor trae datos de varios contextos.
- Un contexto nuevo se agrega con la feature que lo necesita y se declara en el plan de
  esa feature.

Reglas de monolito:

- **Modelo unico.** Los contextos NO son fronteras: una clase de modelo puede
  referenciar clases de modelo de otro contexto. No se duplica un mismo concepto en dos
  contextos.
- Las clases `*SQL` de distintos contextos se pueden relacionar con
  `@ManyToOne`/`@OneToMany` cuando el modelo lo requiere. El mapper de un contexto puede
  usar mappers de otros.
- Un servicio puede inyectar repositories de cualquier contexto. Un caso de uso que
  modifica objetos de varios contextos se resuelve en UN metodo de servicio
  `@Transactional`, no orquestando servicios como si fueran remotos.
- Un servicio puede depender de otro servicio solo si no se forma un ciclo.
- No se escriben contratos de integracion entre contextos ni entre features.

Decisiones de diseno explicitas sobre este arbol:

- **El mapper lo invoca la persistencia, no el servicio.** `repository/` es el unico
  punto del sistema donde conviven modelo y persistencia: recibe y devuelve modelo, y
  por dentro usa el `*SQLDAO` y el mapper. Esto mantiene al servicio totalmente
  ignorante de JPA.
- **El modelo vive siempre en `modelo/<contexto>/`.** No existe `domain/`.
- No se crean `.gitkeep` para reservar paquetes vacios. Si queda un `.gitkeep` en un
  paquete que ya tiene clases, se borra en el mismo commit.
- Se mantienen sin cambios respecto de la v1.0.0: las capas, la direccion de
  dependencias, el modelo sin anotaciones, las clases `*SQL`, los mappers invocados
  desde `repository/` y los DTO que no cruzan al servicio.

### II. Modelo Rico

La logica de negocio vive en los objetos de modelo, no en los servicios.

- El servicio orquesta y nada mas: resuelve ids, pide al repository, delega la decision
  en el modelo, persiste el resultado.
- El servicio NO implementa reglas de negocio propias. Un `if` en un servicio que
  decide algo del dominio es un defecto: esa decision pertenece al modelo.
- Prohibidos los modelos anemicos. Una clase de modelo que es solo getters y setters,
  con toda la logica en el service, viola este principio.

### III. Cada Validacion en su Nivel

Cada tipo de validacion tiene un unico lugar donde va:

- **Forma y tipos del request**: en el DTO de request, con Bean Validation. El trimming
  y la sanitizacion del input tambien van aca.
- **Que lo pedido exista y la accion sea posible** (los ids resuelven, las entidades se
  encuentran): en el servicio.
- **Invariantes del dominio**: en los objetos de modelo, lanzando excepciones propias
  del dominio.

Las excepciones son propias y con nombre: `PlayerNotFoundException`,
`DuplicateUsernameException`. Prohibido `throw new RuntimeException(...)` y prohibido
usar excepciones genericas de la JDK para expresar reglas de dominio.

Un unico `@RestControllerAdvice` en `shared/` centraliza el manejo y devuelve siempre el
mismo formato de error JSON, con el status code correcto (400, 401, 403, 404, 409 y 503).
Estos status son la base, no una lista cerrada: una feature puede sumar otro status code
con el aval explicito del desarrollador que la implementa, declarandolo en su plan.
Nunca se filtran stack traces ni mensajes internos al cliente.

### IV. Estrategia de Tests (NO NEGOCIABLE)

Niveles y su alcance:

- **Unitarios del dominio**: sin Spring y sin base de datos. Rapidos.
- **Unitarios de servicio**: con los repositorios mockeados con Mockito.
- **Integracion de servicios y repositorios**: contra H2 en memoria, con el perfil test
  activado via `@ActiveProfiles("test")`. Prohibidos Testcontainers y PostgreSQL.
- **End to end con MockMvc**: solo en su propio paquete. Nunca dentro de un test de
  servicio.

Convenciones:

- Siempre casos felices y casos borde.
- Aserciones con AssertJ (`assertThat`), no con los `assert` de JUnit.
- Nomenclatura: `PlayerServiceTest` para unitarios, `PlayerControllerIT` para
  integracion.
- Nombres de test descriptivos del comportamiento:
  `devuelveNotFoundCuandoElJugadorNoExiste()`.
- Los tests no dependen del orden de ejecucion ni comparten estado.
- No se escriben tests triviales solo para subir el coverage (getters, setters,
  constructores).
- Todo test sigue la estructura setup - execute - verify, en tres bloques separados por
  una linea en blanco:
  - **setup**: se arma el escenario (datos, mocks, estado previo);
  - **execute**: VARIAS acciones, las que se estan probando;
  - **verify**: las aserciones con AssertJ sobre el resultado o el efecto.

  No se mezclan acciones con aserciones ni se ejecutan varias acciones de prueba en el
  mismo test.
- Los tests replican la estructura de produccion: capa y contexto (`modelo/<contexto>/`,
  `service/<contexto>/`, `persistence/.../<contexto>/`). Los end to end con MockMvc
  viven en `e2e/`. Los tests de `security/`, `config/` y `shared/` quedan en su paquete
  transversal.

**NO trabajamos con TDD.** Los tests se escriben junto con la implementacion de cada
tarea, no antes. Escribir el test primero, verlo fallar y despues implementar consume
demasiadas idas y vueltas. Lo obligatorio es: al terminar cada tarea, los tests existen
y pasan.

**REGLA INNEGOCIABLE**: no se modifica ni se borra un test existente, en ninguna fase del
flujo, sin pedir permiso al equipo y recibir un "si" explicito. Un test que falla se
arregla arreglando el codigo, no el test.

Mover un test de paquete NO cuenta como modificarlo cuando solo cambian la declaracion
`package` y los imports, y ningun metodo, nombre, asercion ni dato. Cualquier otro cambio
sigue requiriendo el "si" explicito del equipo. En una reestructuracion se conservan
TODOS los tests existentes y solo se mueven los que quedan en un paquete que deja de
existir.

### V. Calidad de Codigo Medible

El proyecto se analiza con SonarCloud y se exige **menos de 10 issues**. El codigo se
escribe pensando en eso desde el principio, no se limpia despues:

- Sin codigo duplicado. Si el mismo bloque aparece dos veces, se extrae.
- Sin imports, variables ni parametros sin usar.
- Sin complejidad cognitiva alta: nada de `if` anidados de cuatro niveles.
- Sin `catch` vacios ni `catch (Exception e)` genericos.
- Nada de `System.out.println`. Logging con SLF4J.
- Sin numeros magicos: constantes con nombre.
- Sin credenciales, tokens ni secretos hardcodeados.
- Inyeccion de dependencias por constructor, nunca `@Autowired` sobre el campo. Con
  Lombok: `@RequiredArgsConstructor` y campos `private final`.
- Metodos cortos, con una sola responsabilidad.
- Los comentarios explican el PORQUE, no el QUE.

### VI. Persistencia Explicita y Portable

Desarrollamos contra H2, pero el mapeo no depende de H2:

- Los nombres de tabla se mapean SIEMPRE explicito con `@Table(name = "...")`. `user` y
  `order` son palabras reservadas en PostgreSQL, por lo tanto se usa `app_user`,
  `orders`, etc.
- Prohibido `@Query(nativeQuery = true)`. Solo derived queries y JPQL. Si algun caso lo
  requiere de verdad, se avisa al equipo y se aprueba antes de escribirlo.
- `spring.jpa.open-in-view` queda en `false`.
- Perfil `local`: `jdbc:h2:file` (los datos sobreviven al reinicio). Perfil `test`:
  `jdbc:h2:mem` (base limpia en cada corrida).

### VII. Idioma

- **Identificadores del codigo** (clases, metodos, variables, paquetes, endpoints): en
  ingles. Los endpoints son `/players`, `/quotes`, `/orders` y se respetan tal cual.
- **Mensajes de error de la API, documentacion, comentarios y specs**: en espanol.
- Los identificadores no llevan acentos ni enie.
- Terminos tecnicos y normativos: en ingles.
- Excepcion aceptada y unica: la capa `modelo/` conserva su nombre en espanol. Los
  nombres de contexto (`user`, `player`, `team`, `league`, `position`, ...) van en
  ingles.

## Stack Tecnologico

El stack esta decidido. Cambiarlo requiere una enmienda a esta constitucion.

- Java 21 (Temurin)
- Spring Boot 4.1.1
- Gradle con wrapper (`backend/gradlew`)
- H2 embebida como base de datos
- Spring Data JPA
- Spring Security + jjwt para autenticacion
- Spring Validation (Bean Validation)
- Lombok
- springdoc-openapi para Swagger v3
- JUnit 5 + Mockito + AssertJ + MockMvc para tests

Prohibiciones y advertencias:

- **NO usamos Docker** en ningun momento del desarrollo local. El proyecto se levanta con
  `./gradlew bootRun` y nada mas.
- **NO usamos Testcontainers. NO usamos PostgreSQL.**
- **No se reemplaza** el `build.gradle` ni los `application*.yml`. El esqueleto ya
  funciona. La estructura de paquetes es la que fija el Principio I.
- Spring Boot 4 renombro varios starters respecto de Boot 3 (por ejemplo
  `spring-boot-starter-web` pasa a llamarse `spring-boot-starter-webmvc`). No se copian
  dependencias de tutoriales de Boot 3 sin verificar el nombre real del artefacto.
- Ninguna dependencia nueva se agrega sin avisar y sin justificar.

Monorepo:

- `backend/` Spring Boot. El wrapper de Gradle vive aca.
- `frontend/` React 18 + Vite + TypeScript. Estructura y reglas en la seccion Frontend.

## Frontend

Los Principios I a VI aplican solo al backend. El Principio VII (Idioma) aplica tambien
al frontend.

Stack (en `frontend/`):

- React 18 + Vite + TypeScript en modo `strict`.
- React Router para la navegacion.
- Vitest + React Testing Library para tests; MSW para simular la API en los tests.
- ESLint para el lint.
- CSS Modules + variables CSS en un archivo de tokens. Sin frameworks de UI (ni
  Tailwind, ni MUI, ni Bootstrap). Fuentes de Google Fonts.
- Ninguna dependencia nueva se agrega sin avisar y sin justificar, igual que en el
  backend.

Estructura de carpetas. Se organiza POR TIPO DE ARTEFACTO, NO por feature. Este arbol es
el unico valido; no se inventan carpetas nuevas:

    frontend/
    +-- src/
    |   +-- components/   componentes visuales reutilizables (PostIt, Magnet, Pitch,
    |   |                 TapeTab, ChalkInput, ChalkButton, Board, ...)
    |   +-- pages/        una pantalla por ruta (LoginPage, BoardPage, PlayerPage, ...);
    |   |                 componen components/ y usan hooks/
    |   +-- hooks/        hooks propios (useSession, usePlayers, useScoutingEleven, ...)
    |   +-- services/     cliente HTTP unico (fetch) y una funcion por endpoint
    |   +-- context/      contextos de React (sesion)
    |   +-- types/        tipos de TypeScript de la API y del dominio del front
    |   +-- utils/        funciones puras (traduccion de enums, formateo, ...)
    |   +-- styles/       tokens.css y estilos globales
    |   +-- router/       definicion de rutas
    |   +-- main.tsx
    +-- tests/            replica la estructura de src/
    |   +-- components/   tests de componentes
    |   +-- pages/        tests de pantallas y flujos completos con MSW
    |   +-- hooks/        tests de hooks
    |   +-- services/     tests del cliente HTTP
    |   +-- utils/        tests de funciones puras
    |   +-- mocks/        handlers y server de MSW, datos de prueba
    |   +-- setup.ts      configuracion global de Vitest
    +-- mockups/          disenos de referencia en HTML (no se importan desde src/)

Reglas:

- Los tests viven SOLO en `frontend/tests/`, nunca junto al codigo en `src/`. El test de
  `src/<carpeta>/X.tsx` esta en `tests/<carpeta>/X.test.tsx`.
- Los componentes y las paginas nunca llaman a `fetch` directo: pasan por `services/`.
- Los componentes de `components/` no conocen la API ni el router: reciben datos y
  callbacks por props. La logica con estado vive en `hooks/` o en `context/`.
- Los tipos de la API se escriben a mano a partir de los `contracts/*.yaml` de cada spec
  y viven en `types/`. Los enums se respetan tal cual el backend (`PREMIER`,
  `GOALKEEPER`, ...); la traduccion a etiquetas en espanol se hace en `utils/`.
- El token de sesion se guarda en memoria y en `sessionStorage`; nunca se loguea.
- En desarrollo se usa el proxy de Vite (`/api` -> `http://localhost:8080`). No se
  agrega configuracion de CORS al backend.
- Accesible: todo lo clickeable es un `button` o un link, con foco visible.
- Responsive: usable desde 360px de ancho.
- No se crean carpetas vacias ni `.gitkeep` (misma regla que el backend).

Tests del frontend. Rigen las convenciones del Principio IV que no son propias de Java:

- Siempre casos felices y casos borde.
- Estructura setup - execute - verify, en tres bloques separados por una linea en blanco.
- Nombres descriptivos del comportamiento, en espanol:
  `it("muestra 'jugador no encontrado' cuando la API responde 404")`.
- Sin TDD: los tests se escriben junto con la implementacion de cada tarea.
- **REGLA INNEGOCIABLE** del Principio IV: no se modifica ni se borra un test existente
  sin el "si" explicito del equipo.
- No se testean detalles de estilo (colores, tamanos); se testea comportamiento.

**Definicion de terminado del frontend** (complementa la del backend):

1. `npm test` pasa, con tests felices y de borde.
2. `npm run lint` y `npm run build` terminan sin errores.
3. `npm run dev` levanta y funciona contra el backend corriendo con `./gradlew bootRun`.

## Flujo de Trabajo y Definicion de Terminado

**Definicion de terminado.** Un requerimiento esta terminado cuando se cumplen las cuatro
condiciones:

1. Tiene tests unitarios y de integracion, felices y de borde, y pasan.
2. `./gradlew build` da BUILD SUCCESSFUL.
3. La aplicacion compila y levanta con el perfil local (`./gradlew bootRun`).
4. Los endpoints nuevos estan documentados en Swagger y se pueden ejecutar desde
   `/swagger-ui.html`.

**Trabajo en paralelo.** Somos dos desarrolladores. Ninguno es dueno de un paquete ni de
un contexto: los dos trabajan sobre el mismo arbol. Los conflictos se evitan con PRs
chicos y frecuentes contra `develop`, no con fronteras de paquetes. Este documento es la
unica fuente de verdad sobre estructura y estilo: ante una duda de forma, se sigue lo que
dice aca, no la preferencia del agente.

**Como se espera que trabaje el agente:**

- Si algo del pedido es ambiguo, PREGUNTAR antes de escribir codigo.
- Explicar las decisiones de diseno no obvias en dos lineas.
- No agregar dependencias sin avisar y sin justificar.
- No hacer refactors grandes que no se pidieron.
- No inventar APIs, anotaciones ni metodos.
- Si un pedido va contra esta constitucion, senalarlo antes de hacerlo.
- Prohibido entregar codigo con `// TODO: implementar aca`.
- No crear paquetes de primer nivel por feature. Una clase nueva va en
  `<capa>/<contexto>/` o en `adapter/<proveedor>/`. Si no queda claro donde va,
  preguntar.
- Si una carpeta nueva ayuda a organizar, proponerla y esperar el aval de un
  desarrollador. No rechazarla solo porque no figura en el arbol.
- No reescribir tests existentes al moverlos: solo `package` e imports.
- No crear carpetas vacias ni `.gitkeep`. Si queda un `.gitkeep` en un paquete con
  clases, se borra en el mismo commit.
- En el frontend, un archivo nuevo va en la carpeta de `src/` que corresponde a su
  tipo. Si no queda claro, preguntar.

## Governance

Esta constitucion supersede cualquier otra practica, convencion heredada o preferencia
individual. Ante un conflicto entre este documento y un tutorial, la respuesta de un
agente o el codigo existente, gana este documento.

**Enmiendas.** Toda enmienda requiere: propuesta escrita, acuerdo explicito de los dos
desarrolladores, y bump de version en el mismo commit que introduce el cambio.

**Versionado.** Se usa versionado semantico sobre el numero de version de este documento:

- **MAJOR**: se quita o se redefine un principio de forma incompatible con lo anterior.
- **MINOR**: se agrega un principio o seccion, o se expande materialmente una guia.
- **PATCH**: aclaraciones, redaccion, correcciones sin cambio semantico.

**Cumplimiento.** Toda revision de codigo verifica el cumplimiento de estos principios.
Un cambio que viola un principio no se integra: o se corrige el cambio, o se enmienda la
constitucion primero. Las reglas marcadas NO NEGOCIABLE no admiten excepcion puntual;
solo se levantan por enmienda.

**Uso en runtime.** Los agentes leen este documento antes de cada tarea y lo citan cuando
rechazan o corrigen un pedido.

**Version**: 2.2.1 | **Ratified**: 2026-09-02 | **Last Amended**: 2026-10-07
