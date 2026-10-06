<!--
SYNC IMPACT REPORT
==================
Version change: 1.0.0 -> 2.0.0
Bump rationale: MAJOR. Se redefine el Principio I de forma incompatible: la
organizacion por feature (auth/, catalog/) se reemplaza por una organizacion por
capa y, dentro de cada capa, por contexto del dominio. El sistema es UN monolito
con un unico modelo de dominio; los contextos no son fronteras.

Principios modificados:
  - I. Arquitectura en Capas Estricta (NO NEGOCIABLE)  [redefinido]
      * Nuevo arbol de paquetes por capa -> contexto.
      * Contextos vigentes: user, player, team, league, position.
      * Reglas de contextos y reglas de monolito agregadas.
      * "El modelo vive en <feature>/modelo/" -> "El modelo vive en
        modelo/<contexto>/. No existe domain/."
      * DTO: <feature>/controller/dto/ -> controller/<contexto>/dto/.
      * Regla de .gitkeep alineada con "no se crean carpetas vacias".
  - IV. Estrategia de Tests (NO NEGOCIABLE)  [ampliado]
      * Estructura setup - execute - verify obligatoria.
      * Los tests replican capa y contexto; e2e/ para MockMvc.
      * REGLA INNEGOCIABLE: mover un test (solo package e imports) no cuenta como
        modificarlo.
  - VII. Idioma  [excepcion reformulada: modelo/ en espanol, contextos en ingles]

Secciones modificadas:
  - Stack Tecnologico: la prohibicion de reemplazar "la estructura de paquetes
    existente" pasa a remitir al arbol del Principio I.
  - Flujo de Trabajo y Definicion de Terminado: "Trabajo en paralelo" reemplazado
    (sin duenos de paquete ni de contexto); dos reglas nuevas para el agente; la
    regla de .gitkeep se alinea con "no se crean carpetas vacias".

Secciones agregadas: ninguna.
Secciones removidas: ninguna.

Plantillas a revisar:
  - .specify/templates/plan-template.md   seccion de estructura de codigo  (pendiente)
  - .specify/templates/tasks-template.md  rutas de ejemplo                 (pendiente)
  - .specify/templates/spec-template.md   sin impacto

Historicos: los planes de specs/001 y specs/002 describen la estructura por feature
de la v1.0.0 y quedan como registro historico; no se reescriben.

TODOs diferidos: ninguno.
-->

# futbol-market Constitution

Mercado simulado de valoracion de jugadores de futbol. API REST en Spring Boot para
la materia Desarrollo de Aplicaciones (UNQ).

El proyecto YA EXISTE y tiene un esqueleto funcionando. Esta constitucion gobierna
como se lo extiende, no como se lo crea.

## Core Principles

### I. Arquitectura en Capas Estricta (NO NEGOCIABLE)

Cuatro capas: controller, servicio, modelo y persistencia. La direccion de las
dependencias es unica y no admite atajos.

    Controller  ->  Service  ->  Repository  ->  SQLDAO  ->  H2
                        |             |
                        v             v
                     Modelo  <--   Mapper   -->  *SQL

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

Estructura de paquetes. El sistema es un monolito. Se organiza primero POR CAPA y,
dentro de cada capa, POR CONTEXTO del dominio. Este arbol es el unico valido:

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
    +-- security/                 transversal: filtros, JwtService, SecurityConfig
    +-- config/                   transversal: clases @Configuration
    +-- shared/                   transversal: ApiError, bases de excepcion y
                                  RestControllerAdvice

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
mismo formato de error JSON, con el status code correcto (400, 401, 403, 404, 409).
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
- `frontend/` React + Vite. Vacio por ahora, arranca en la entrega 2.

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
  `<capa>/<contexto>/`. Si no queda claro a que contexto pertenece, preguntar.
- No reescribir tests existentes al moverlos: solo `package` e imports.
- No crear carpetas vacias ni `.gitkeep`. Si queda un `.gitkeep` en un paquete con
  clases, se borra en el mismo commit.

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

**Version**: 2.0.0 | **Ratified**: 2026-09-02 | **Last Amended**: 2026-10-03
