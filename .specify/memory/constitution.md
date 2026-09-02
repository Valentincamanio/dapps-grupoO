<!--
SYNC IMPACT REPORT
==================
Version change: (plantilla sin completar) -> 1.0.0
Bump rationale: MAJOR inicial. Primera ratificacion: el documento pasa de ser la
plantilla con placeholders a una constitucion con principios vinculantes.

Principios agregados (7, expandidos desde las 5 ranuras de la plantilla):
  - I. Arquitectura en Capas Estricta (NO NEGOCIABLE)
  - II. Modelo Rico
  - III. Cada Validacion en su Nivel
  - IV. Estrategia de Tests (NO NEGOCIABLE)
  - V. Calidad de Codigo Medible
  - VI. Persistencia Explicita y Portable
  - VII. Idioma

Secciones agregadas:
  - Stack Tecnologico                            (reemplaza [SECTION_2_NAME])
  - Flujo de Trabajo y Definicion de Terminado   (reemplaza [SECTION_3_NAME])
  - Governance                                   (completada)

Secciones removidas: ninguna.

Ambiguedades resueltas por consulta al equipo (2026-09-02):
  - El modelo vive siempre en <feature>/modelo/. El paquete domain/ de primer
    nivel se elimina del arbol.
  - persistence/repository/ envuelve al *SQLDAO e invoca al mapper. El servicio
    nunca ve el mapper, el SQLDAO ni las clases *SQL.

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
- Las clases `Request` y `Response` viven en `<feature>/controller/dto/` y NUNCA
  cruzan hacia el servicio. El controller las convierte antes de delegar y arma la
  respuesta a partir de lo que el servicio le devuelve.
- El servicio recibe y devuelve objetos de modelo. No conoce JPA ni las clases `*SQL`.
- Ninguna clase de persistencia ni de modelo sale del backend en una respuesta HTTP.
  Lo que sale es siempre un `Response` del paquete `dto`.

Estructura de paquetes. Se organiza por feature y dentro de cada feature por capa.
Este arbol es el unico valido; no se inventan paquetes nuevos:

    ar.edu.unq.desapp.futbolmarket
    +-- catalog/
    |   +-- controller/
    |   |   +-- dto/              Request y Response
    |   +-- service/
    |   +-- modelo/               modelo puro, sin anotaciones
    |   +-- persistence/
    |       +-- repository/       PlayerRepository: envuelve al DAO + mapper
    |       +-- sql/
    |       |   +-- entity/       PlayerSQL, con anotaciones JPA
    |       |   +-- interfaces/   PlayerSQLDAO extends JpaRepository<PlayerSQL, Long>
    |       +-- mapper/           PlayerMapper.toDomain / toSQL
    +-- auth/                     misma estructura
    +-- security/                 filtros, JwtService, SecurityConfig
    +-- config/                   clases @Configuration
    +-- shared/                   excepciones y RestControllerAdvice

Decisiones de diseno explicitas sobre este arbol:

- **El mapper lo invoca la persistencia, no el servicio.** `repository/` es el unico
  punto del sistema donde conviven modelo y persistencia: recibe y devuelve modelo, y
  por dentro usa el `*SQLDAO` y el mapper. Esto mantiene al servicio totalmente
  ignorante de JPA.
- **El modelo vive siempre dentro de su feature**, en `<feature>/modelo/`. NO existe un
  paquete `domain/` de primer nivel; si aparece en el repo con un `.gitkeep`, se
  elimina.
- Los paquetes ya existen en el repo con un `.gitkeep`. Se usan tal cual. Cuando un
  paquete deja de estar vacio, se borra su `.gitkeep` en el mismo commit.

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

**NO trabajamos con TDD.** Los tests se escriben junto con la implementacion de cada
tarea, no antes. Escribir el test primero, verlo fallar y despues implementar consume
demasiadas idas y vueltas. Lo obligatorio es: al terminar cada tarea, los tests existen
y pasan.

**REGLA INNEGOCIABLE**: no se modifica ni se borra un test existente, en ninguna fase del
flujo, sin pedir permiso al equipo y recibir un "si" explicito. Un test que falla se
arregla arreglando el codigo, no el test.

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
- Excepcion aceptada y unica: el paquete `modelo/` conserva su nombre en espanol por
  decision del equipo. No es precedente para otros paquetes.

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
- **No se reemplaza** el `build.gradle`, los `application*.yml` ni la estructura de
  paquetes existente. El esqueleto ya funciona.
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

**Trabajo en paralelo.** Somos dos desarrolladores trabajando en simultaneo, cada uno con
su propio agente de IA. Este documento es la unica fuente de verdad sobre estructura y
estilo: el codigo de los dos tiene que parecerse. Ante una duda de forma, se sigue lo que
dice aca, no la preferencia del agente.

**Como se espera que trabaje el agente:**

- Si algo del pedido es ambiguo, PREGUNTAR antes de escribir codigo.
- Explicar las decisiones de diseno no obvias en dos lineas.
- No agregar dependencias sin avisar y sin justificar.
- No hacer refactors grandes que no se pidieron.
- No inventar APIs, anotaciones ni metodos.
- Si un pedido va contra esta constitucion, senalarlo antes de hacerlo.
- Prohibido entregar codigo con `// TODO: implementar aca`.
- Cuando un paquete deja de estar vacio, se borra su `.gitkeep` en el mismo commit.

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

**Version**: 1.0.0 | **Ratified**: 2026-09-02 | **Last Amended**: 2026-09-02
