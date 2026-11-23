# Feature Specification: Auditoría de web services

**Feature Branch**: `011-auditoria-web-services`

**Created**: 2026-11-12

**Status**: Draft

**Input**: User description: "Auditoría de Web-Services (Entrega 3, requerimiento core de la cátedra): loguear <timestamp, user, operación/método, parámetros, tiempoDeEjecucion> de los servicios publicados con Spring utilizando Log4j/logback. Cada invocación a un endpoint REST propio deja exactamente una línea técnica en un archivo de log propio, con datos sensibles enmascarados, sin alterar el comportamiento de la API."

## Contexto y diferencia con la auditoría del mercado

Esta feature agrega la **auditoría de web services**: un registro técnico de cada invocación a
un endpoint REST publicado por el backend, pensado para reconstruir después quién llamó a qué,
con qué datos y cuánto tardó.

NO es la auditoría del mercado de la feature 008 (FR-050 a FR-053, `GET /audit`). Aquella es un
registro de negocio, persistido en base y consultable por el administrador. La auditoría de web
services:

- va a un archivo de log, NO se guarda en base;
- NO tiene endpoint de consulta;
- NO reemplaza, NO modifica y NO lee la auditoría del mercado. Las dos conviven: una invocación a
  `POST /orders/buy` deja su línea de auditoría de web services y, además, los registros de
  auditoría del mercado que ya generaba la 008, sin cambios.

## Clarifications

### Session 2026-11-12

- Q: ¿Los requests rechazados por la validación de forma (400 antes de que el método del
  controller se ejecute, incluido un JSON mal formado) se auditan? → A: Sí, se registran con
  `ERROR` y el tipo de error de validación; lo que no se pudo leer va con un marcador fijo
  (US3 escenarios 4 y 5, FR-001, FR-001a). **Reemplazada en la revisión del plan, más abajo.**
- Q: ¿En qué formato se escribe cada línea: texto `clave=valor` o un JSON por línea? → A: Texto
  con pares `clave=valor` en orden fijo, igual en el archivo y en la consola (FR-012).
- Q: ¿El límite de largo se aplica a cada campo o a todo el parámetro? → A: A los dos: cada valor
  individual se trunca a 500 caracteres y todos los parámetros de la línea juntos, a 2000
  (FR-017).
- Q: ¿El campo user indica también con qué credencial se autenticó? → A: Sí, el tipo de
  credencial (`JWT` o `API_KEY`), nunca su valor: `user=42(USER,JWT)` (FR-006).

### Session 2026-11-12 (revisión durante el plan)

El plan encontró tres puntos de la spec que chocaban con el diseño acordado o con el código
vigente. El equipo los resolvió así:

- Q: Un request rechazado mientras se arman los argumentos del endpoint (cuerpo ilegible, cuerpo
  que no pasa la validación, parámetro que no se puede convertir o que falta) no llega a invocar
  el endpoint. ¿Se audita igual? → A: No. Reemplaza la primera clarificación: esos rechazos no
  dejan línea, igual que los 401 y 403 del filtro. Se elimina FR-001a, se ajustan FR-001, FR-003,
  US3 escenarios 4 y 5 y el edge case correspondiente.
- Q: Las rutas públicas (`POST /auth/login`, `GET /players/**`, `GET /orders`, entre otras) no
  evalúan la credencial que traiga el request. ¿Qué usuario registra la línea en esas rutas?
  → A: `anonymous`, aunque el request traiga una credencial válida: la feature no cambia cómo se
  autentica. El escenario 1 de la US1 pasa a usar un endpoint protegido y se corrige el edge case
  de la credencial en un endpoint público (FR-006).
- Q: ¿El timestamp es el momento en que empieza la invocación o el momento en que termina?
  → A: El momento en que termina, cuando se escribe la línea; el inicio se obtiene restando el
  tiempo de ejecución (Assumptions, FR-005).

## User Scenarios & Testing *(mandatory)*

Los actores son quienes operan o evalúan el sistema (los desarrolladores, la cátedra) y leen el
registro, y los clientes de la API (el frontend, un usuario con su token de sesión, un
integrador con su clave de API), que generan las invocaciones sin notar ninguna diferencia.

### User Story 1 - Cada invocación deja su línea de auditoría (Priority: P1)

Alguien invoca un endpoint REST propio del backend, público o protegido. Al terminar la
invocación, el registro de auditoría de web services tiene exactamente una línea nueva con el
momento de la invocación, el usuario que la hizo, la operación invocada, los parámetros con los
que se invocó, cuánto tardó y si terminó bien o con error. Quien lee el registro puede
reconstruir la invocación sin consultar ninguna otra fuente.

**Why this priority**: es el requerimiento core de la cátedra. Sin esto la feature no existe; el
resto son reglas sobre el contenido de la línea.

**Independent Test**: se invoca un endpoint protegido con un token de sesión válido y un
endpoint público sin credencial; se verifica que cada invocación dejó una sola línea, con los
seis datos completos y correctos.

**Acceptance Scenarios**:

1. **Given** un usuario autenticado con su token de sesión, **When** consulta su portfolio con
   `GET /users/{id}/portfolio`, **Then** se agrega una línea con el timestamp de la invocación
   (con milisegundos y zona horaria), el id y el rol de ese usuario con el tipo de credencial
   `JWT`, la operación (controller, método, verbo `GET` y la ruta pedida), el parámetro de ruta
   `id` con su valor, el tiempo de ejecución en milisegundos y el resultado `OK`.
2. **Given** un integrador autenticado con su clave de API, **When** invoca un endpoint
   protegido, **Then** la línea identifica al usuario dueño de la clave, con su id, su rol y el
   tipo de credencial `API_KEY`; con un token de sesión, el tipo es `JWT`. El valor de la
   credencial no aparece en ningún caso.
3. **Given** un cliente, con o sin credencial, **When** invoca un endpoint público (por ejemplo
   `POST /auth/login` o `GET /players/{id}`), **Then** la línea registra el usuario como
   `anonymous`: en las rutas públicas la credencial no se evalúa.
4. **Given** una invocación con parámetros de query (por ejemplo, un listado paginado o
   filtrado), **When** termina, **Then** la línea incluye cada parámetro de query con su nombre y
   su valor.
5. **Given** una invocación con cuerpo (por ejemplo `POST /orders/buy`), **When** termina,
   **Then** la línea incluye el cuerpo como parámetro, con cada uno de sus campos con nombre y
   valor.
6. **Given** varias invocaciones seguidas o simultáneas, **When** terminan, **Then** hay
   exactamente una línea por invocación, cada una completa y sin mezclarse con otra.

---

### User Story 2 - Los datos sensibles nunca aparecen en claro (Priority: P1)

Una invocación lleva un secreto: la contraseña en el login o el registro, la contraseña actual y
la nueva en el cambio de contraseña, un token o una clave de API. La línea de auditoría la
registra igual, pero en lugar del secreto aparece una máscara fija. La identidad del usuario sale
solo en el campo user, no repetida entre los parámetros.

**Why this priority**: tiene la misma prioridad que la US1 porque un registro que guarda
contraseñas en claro es un incidente de seguridad; la US1 no se puede entregar sin esta regla.

**Independent Test**: se invocan el login, el registro, el cambio de contraseña y la generación
de clave de API con valores conocidos; se busca cada uno de esos valores en el registro y no
aparece ninguno; en su lugar aparece la máscara.

**Acceptance Scenarios**:

1. **Given** un cliente, **When** invoca `POST /auth/login` con usuario y contraseña, **Then** la
   línea muestra el nombre de usuario y, en el campo de la contraseña, la máscara fija.
2. **Given** un cliente, **When** invoca `POST /auth/register`, **Then** la contraseña aparece
   enmascarada y el resto de los campos se registran normalmente.
3. **Given** un usuario autenticado, **When** cambia su contraseña con `PUT /auth/me/password`,
   **Then** la contraseña actual y la nueva aparecen las dos enmascaradas.
4. **Given** cualquier invocación con un campo que contenga un token, una clave de API o un
   secreto, en cualquier nivel del cuerpo, **When** termina, **Then** ese valor aparece
   enmascarado.
5. **Given** un endpoint cuyo método recibe al usuario autenticado como argumento, **When** se
   invoca, **Then** el usuario aparece solo en el campo user y no se repite entre los parámetros.
6. **Given** una invocación con credencial (token de sesión o clave de API), **When** termina,
   **Then** la credencial no aparece en ninguna parte de la línea.

---

### User Story 3 - Las invocaciones que terminan en error también quedan (Priority: P2)

Una invocación termina en error: el recurso no existe, una regla del dominio la rechaza, el
usuario no tiene saldo o tokens suficientes, un proveedor externo no responde. La línea se
registra igual, con resultado `ERROR` y el tipo de error, y el cliente recibe exactamente la
misma respuesta de error que recibiría sin la auditoría.

**Why this priority**: los errores son las invocaciones que más interesa reconstruir, pero el
registro de las exitosas (US1) ya aporta valor por sí solo.

**Independent Test**: se pide un jugador inexistente y se ejecuta una orden que el dominio
rechaza; se verifica que cada una dejó una línea con `ERROR` y el tipo de error, y que el status
y el cuerpo de error que recibe el cliente son idénticos a los de antes de la feature.

**Acceptance Scenarios**:

1. **Given** un id de jugador inexistente, **When** se invoca `GET /players/{id}`, **Then** la
   línea tiene resultado `ERROR` con el tipo de error de "jugador no encontrado", y el cliente
   recibe el mismo 404 con el mismo cuerpo de error que sin auditoría.
2. **Given** una orden que el dominio rechaza, **When** se invoca el endpoint de la orden,
   **Then** la línea tiene resultado `ERROR` con el tipo de error correspondiente, y el tiempo de
   ejecución medido hasta el momento del error.
3. **Given** una invocación que termina en error, **When** se escribe su línea, **Then** la
   línea tiene la misma severidad informativa que las exitosas.
4. **Given** un request con un cuerpo que no cumple la forma pedida (un campo obligatorio
   faltante, un valor fuera de rango) o con un JSON mal formado, **When** se envía al endpoint,
   **Then** el cliente recibe el mismo 400 que sin auditoría y NO se registra línea: el request
   se rechazó antes de invocar el endpoint.
5. **Given** un parámetro de ruta o de query con un valor que se puede leer pero que está fuera
   de rango (por ejemplo, un id que tiene que ser positivo), **When** se invoca el endpoint,
   **Then** la restricción se evalúa como parte de la invocación: la línea tiene resultado
   `ERROR` con el tipo de error de validación y el cliente recibe el mismo 400 que sin
   auditoría.

---

### User Story 4 - El registro se opera aparte del log general (Priority: P3)

Quien opera el sistema encuentra la auditoría de web services en un archivo propio, separado
del log general de la aplicación, que rota solo y no se sube al repositorio. En desarrollo
también la ve por consola. Si alguien baja el nivel general de log (por ejemplo, para ver solo
advertencias), la auditoría sigue escribiéndose.

**Why this priority**: es la forma de operar el registro; las líneas (US1 a US3) ya cumplen el
requerimiento aunque se lean en otro lugar.

**Independent Test**: se levanta la aplicación en desarrollo, se invoca un endpoint y se
verifica la línea en el archivo propio y en la consola, y que no aparece en el log general; se
sube el nivel general a advertencia, se repite la invocación y la línea se sigue escribiendo.

**Acceptance Scenarios**:

1. **Given** la aplicación levantada en desarrollo, **When** se invoca un endpoint, **Then** la
   línea aparece en el archivo de auditoría de web services y en la consola.
2. **Given** la aplicación levantada, **When** se invoca un endpoint, **Then** la línea NO
   aparece en el archivo del log general.
3. **Given** el nivel general de log configurado en advertencia o error, **When** se invoca un
   endpoint, **Then** la línea de auditoría se escribe igual.
4. **Given** el archivo de auditoría alcanzó el criterio de rotación, **When** llegan nuevas
   invocaciones, **Then** se escriben en un archivo nuevo y los anteriores se conservan según la
   política de retención.
5. **Given** el repositorio del proyecto, **When** se listan los archivos versionados, **Then**
   no figura ningún archivo de auditoría de web services.

---

### Edge Cases

- **Valor muy largo**: un valor que supera los 500 caracteres se registra truncado, con una marca
  visible de que fue truncado. La línea sigue siendo una sola.
- **Muchos parámetros o una colección enorme**: si todos los parámetros juntos superan los 2000
  caracteres, se corta lo que exceda el tope, con la misma marca visible.
- **Saltos de línea o caracteres de control en un valor**: se escapan, de modo que la invocación
  siga ocupando exactamente una línea y nadie pueda fabricar una línea falsa a través de un
  parámetro.
- **Parámetro ausente o nulo**: se registra con su nombre y un valor nulo explícito, no se omite.
- **Secreto anidado o en una lista**: un campo sensible dentro de un objeto o una colección del
  cuerpo se enmascara igual que uno de primer nivel.
- **Secreto vacío o nulo**: se registra con la máscara, sin revelar si venía vacío.
- **Falla al escribir la línea** (archivo inaccesible, disco lleno, un valor que no se puede
  representar): el request termina exactamente igual que sin auditoría. Si un valor no se puede
  representar, se usa un marcador fijo en su lugar y el resto de la línea se escribe.
- **Request rechazado antes de invocar el endpoint** (400 por un cuerpo ilegible o que no pasa
  la validación, o por un parámetro de ruta o de query que falta o que no se puede convertir a
  su tipo): no deja línea, igual que los rechazos del filtro de seguridad. Un parámetro que se
  pudo leer pero está fuera de rango se valida dentro de la invocación y sí deja su línea, con
  `ERROR` (US3 escenario 5).
- **Request rechazado por seguridad antes de llegar al endpoint** (401 o 403 del filtro): no deja
  línea de auditoría de web services.
- **Ruta inexistente o verbo no soportado** (no hay endpoint que lo atienda): no deja línea.
- **Endpoints técnicos** (actuator, Swagger/OpenAPI, consola de H2) y el frontend: no dejan línea.
- **Procesos programados** (la sincronización semanal o la del arranque): no son invocaciones de
  web services y no dejan línea. La sincronización manual por endpoint sí deja la suya.
- **Credencial en un endpoint público**: las rutas públicas no evalúan la credencial (ni válida
  ni inválida), así que el campo user registra `anonymous`. La feature no cambia cómo se
  autentica.
- **Invocaciones simultáneas**: cada una tiene su propia línea, con su propio usuario y su propio
  tiempo; ninguna toma datos de otra.

## Requirements *(mandatory)*

### Functional Requirements

**Alcance**

- **FR-001**: El sistema MUST registrar en la auditoría de web services cada invocación a un
  endpoint REST propio del backend, público o protegido, tanto si termina bien como si termina en
  error. Una invocación empieza cuando el endpoint recibe sus argumentos ya leídos; las
  restricciones sobre esos argumentos que se evalúan al invocarlo forman parte de la invocación.
- **FR-001a**: *(Eliminado en la revisión del plan: los requests rechazados antes de invocar el
  endpoint no dejan línea; ver FR-003.)*
- **FR-002**: Cada invocación MUST generar exactamente una línea de auditoría: ni cero ni más de
  una.
- **FR-003**: El sistema MUST NOT registrar en la auditoría de web services los requests
  rechazados por seguridad antes de llegar al endpoint (401 y 403 del filtro), los rechazados
  antes de invocar el endpoint mientras se leen sus argumentos (cuerpo ilegible o inválido,
  parámetro faltante o que no se puede convertir a su tipo), los endpoints de
  actuator, Swagger/OpenAPI, la consola de H2, los recursos del frontend, los requests a rutas o
  verbos sin endpoint, ni los procesos programados que no son invocaciones de web services.
- **FR-004**: La auditoría de web services MUST NOT persistirse en base de datos, MUST NOT tener
  endpoint de consulta y MUST NOT modificar ni reemplazar la auditoría del mercado de la feature
  008.

**Contenido de la línea**

- **FR-005**: Cada línea MUST contener el timestamp de la invocación: fecha y hora con
  milisegundos y zona horaria, del momento en que la invocación termina.
- **FR-006**: Cada línea MUST contener el usuario: el id y el rol del usuario autenticado y el
  tipo de credencial con la que se autenticó (`JWT` para el token de sesión, `API_KEY` para la
  clave de API), por ejemplo `user=42(USER,JWT)`; o `anonymous` si la invocación llegó a un
  endpoint público, donde la credencial no se evalúa. Del tipo de credencial se registra solo el
  tipo, nunca su valor.
- **FR-007**: Cada línea MUST contener la operación: el controller y el método invocados, el
  verbo HTTP y la ruta pedida.
- **FR-008**: Cada línea MUST contener los parámetros con los que se invocó el endpoint (de ruta,
  de query y del cuerpo), cada uno con su nombre y su valor. Los campos del cuerpo se registran
  con su nombre y su valor.
- **FR-009**: Cada línea MUST contener el tiempo de ejecución de la invocación, en milisegundos,
  medido desde que empieza hasta que termina, también cuando termina en error.
- **FR-010**: Cada línea MUST contener el resultado: `OK` si la invocación terminó bien, o
  `ERROR` junto con el tipo de error si terminó en excepción. El mensaje del error no forma parte
  de la línea.
- **FR-011**: Todas las líneas MUST tener severidad informativa, incluidas las de invocaciones
  terminadas en error. Los errores del sistema se siguen registrando como hasta ahora en el log
  general, sin cambios.
- **FR-012**: Cada línea MUST ser texto con pares `clave=valor` en este orden fijo: timestamp,
  severidad, `user`, `op`, `params`, `time` y `result`, igual en el archivo y en la consola, de
  modo que una persona o una herramienta de búsqueda de texto pueda ubicar cada dato. Ejemplo
  ilustrativo: `2026-11-12T10:15:03.412-03:00 | INFO | user=42(USER,JWT) |
  op=PortfolioController.getPortfolio GET /users/42/portfolio | params={"id":42,"date":null} |
  time=12ms | result=OK` (en una sola línea; el formato exacto lo fija el contrato del plan).

**Datos sensibles**

- **FR-013**: Las contraseñas (la del login, la del registro, y la actual y la nueva del cambio de
  contraseña), los tokens, las claves de API y cualquier otro secreto MUST reemplazarse por una
  máscara fija, que no revela ni el valor ni su largo, en cualquier nivel del cuerpo y en
  cualquier parámetro.
- **FR-014**: Las credenciales con las que se autenticó el request (token de sesión y clave de
  API) MUST NOT aparecer en la línea.
- **FR-015**: La identidad del usuario MUST aparecer solo en el campo user; si el método invocado
  recibe al usuario autenticado como argumento, ese argumento MUST NOT repetirse entre los
  parámetros.
- **FR-016**: La auditoría de web services MUST NOT registrar el cuerpo de la respuesta (por
  ejemplo, el token que devuelve el login o la clave que devuelve su generación).

**Representación de los valores**

- **FR-017**: Cada valor individual (un campo del cuerpo, un elemento de una colección, un
  parámetro de ruta o de query) que supere los 500 caracteres MUST registrarse truncado a ese
  largo, con una marca visible de que fue truncado. Además, la representación de todos los
  parámetros de la línea juntos MUST NOT superar los 2000 caracteres: lo que exceda ese tope se
  corta con la misma marca visible. El enmascarado de secretos se aplica antes de truncar.
- **FR-018**: Los saltos de línea y caracteres de control dentro de un valor MUST escaparse para
  que cada invocación ocupe exactamente una línea.
- **FR-019**: Un parámetro nulo o ausente MUST registrarse con su nombre y un valor nulo
  explícito.

**No interferencia**

- **FR-020**: La auditoría de web services MUST NOT alterar el comportamiento de la API: la
  respuesta, el status y el error que recibe el cliente MUST ser exactamente los mismos que sin
  auditoría.
- **FR-021**: Si escribir la línea falla, o si un valor no se puede representar, el request MUST
  NOT fallar por eso. Un valor que no se puede representar se reemplaza por un marcador fijo y el
  resto de la línea se escribe.

**Destino y operación del registro**

- **FR-022**: Las líneas MUST escribirse en un archivo propio, separado del log general de la
  aplicación, y MUST NOT aparecer en el log general.
- **FR-023**: El archivo de auditoría MUST rotar automáticamente y conservar una cantidad acotada
  de archivos anteriores.
- **FR-024**: Los archivos de auditoría MUST quedar excluidos del control de versiones.
- **FR-025**: En el entorno de desarrollo, las líneas MUST verse también por consola.
- **FR-026**: Cambiar el nivel general de log de la aplicación MUST NOT desactivar la auditoría
  de web services. Solo se puede desactivar configurando de forma explícita el propio registro de
  auditoría.

### Key Entities

- **Línea de auditoría de web services**: el registro técnico de una invocación. Tiene
  timestamp, usuario (id, rol y tipo de credencial, o `anonymous`), operación (controller, método, verbo HTTP y ruta),
  parámetros (nombre y valor, con secretos enmascarados y valores largos truncados), tiempo de
  ejecución en milisegundos y resultado (`OK`, o `ERROR` con el tipo de error). No se persiste en
  base ni se relaciona con ninguna entidad del dominio.
- **Parámetro registrado**: un argumento de la invocación (de ruta, de query o del cuerpo), con
  su nombre y su valor ya enmascarado, truncado y escapado según corresponda.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El 100% de las invocaciones a endpoints propios produce exactamente una línea de
  auditoría de web services, verificado sobre todos los endpoints publicados, en casos de éxito y
  de error.
- **SC-002**: Cero apariciones en claro de contraseñas, tokens o claves de API en el registro,
  verificado buscando los valores usados en las pruebas de login, registro, cambio de contraseña,
  generación de clave de API y autenticación con token y con clave.
- **SC-003**: Los tests existentes pasan sin modificarse.
- **SC-004**: El status y el cuerpo de la respuesta de cada endpoint son idénticos con y sin la
  auditoría, en el 100% de los casos probados, incluidos los de error.
- **SC-005**: A partir de una sola línea, una persona identifica quién invocó, qué operación, con
  qué parámetros, cuándo, cuánto tardó y cómo terminó, sin consultar otra fuente.
- **SC-006**: Con el nivel general de log subido a advertencia o error, el 100% de las
  invocaciones sigue dejando su línea.
- **SC-007**: Cero líneas de auditoría de web services aparecen en el log general y cero archivos
  de auditoría quedan versionados en el repositorio.

## Assumptions

- **Restricción de la consigna**: la cátedra pide hacerlo con Log4j o logback sobre los servicios
  publicados con Spring. Se usa la opción que el proyecto ya trae, sin agregar dependencias si no
  hace falta; la elección concreta se fija en el plan.
- **Límite de largo**: 500 caracteres por valor individual y 2000 para todos los parámetros de
  la línea (FR-017). Alcanza para cualquier campo normal del dominio y acota el tamaño de la línea
  con cuerpos grandes o listas largas.
- **Máscara**: un texto fijo (por ejemplo `****`) igual para todos los secretos.
- **Qué es un secreto**: todo campo o parámetro cuyo nombre indique contraseña, token, clave de
  API o secreto. Se reconoce por el nombre, sin importar en qué endpoint o en qué nivel del cuerpo
  aparezca, así un endpoint nuevo queda cubierto sin configurarlo.
- **Tipo de error**: es el nombre del tipo de error con el que terminó la invocación (por ejemplo,
  el de "jugador no encontrado"). El mensaje no se registra porque puede llevar datos del request.
- **Timestamp**: es el momento en que termina la invocación, cuando se escribe la línea; el
  inicio es el timestamp menos el tiempo de ejecución. Va en la zona horaria de Argentina, la
  misma que usan los procesos programados, con el desplazamiento expresado en la propia línea.
- **Tiempo de ejecución**: es el tiempo que tarda el endpoint en atender la invocación, no el
  tiempo de red del cliente. En una invocación rechazada por una restricción sobre sus
  parámetros es el tiempo hasta el rechazo, por lo que puede ser casi nulo.
- **Rotación y retención**: rotación diaria y por tamaño, conservando los archivos de los últimos
  30 días con un tope total de espacio. No es un requisito de la consigna; es un valor razonable
  para un proyecto académico.
- **Ubicación del archivo**: dentro de una carpeta de logs del backend, excluida del control de
  versiones junto con sus archivos rotados.
- **Perfil de test**: la auditoría está activa en los tests (para poder verificarla) y no es
  obligatorio mostrarla por consola. Los tests existentes no se modifican; los tests nuevos de esta
  feature verifican el contenido de las líneas.
- **Usuario**: el id, el rol y el tipo de credencial salen del usuario ya resuelto por la
  autenticación vigente (token de sesión o clave de API). Esta feature no cambia cómo se autentica ni qué endpoints son públicos.
- **Sin cambios de contrato**: no se agregan ni modifican endpoints, DTO ni códigos de error. No
  hay contrato nuevo de API.
- **Fuera de alcance**: correlation id, métricas, logs estructurados del log general, consulta o
  búsqueda del registro desde la aplicación, y cualquier cambio en la auditoría del mercado.
