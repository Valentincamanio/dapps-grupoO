# Feature Specification: Registro, credenciales y acceso autenticado

**Feature Branch**: `001-auth-usuarios`

**Created**: 2026-09-14

**Status**: Draft

**Input**: User description: "El sistema permite que cualquier persona se registre como usuario para poder operar en el mercado de jugadores. Registro con nombre de usuario, correo y contrasena; entrega de una clave de API de un solo uso; inicio de sesion con token de 24 horas; proteccion de los recursos con cualquiera de las dos credenciales; y gestion de las propias credenciales (perfil, cambio de contrasena, regeneracion de la clave de API)."

## Clarifications

### Session 2026-09-18

Enmiendas surgidas de la planificacion (ver `plan.md`, seccion "Divergencias con el spec"):

- Q: Si una peticion trae a la vez un token de sesion y una clave de API, y el token es
  invalido pero la clave es valida, se atiende? -> A: No. El token de sesion tiene
  precedencia: se evalua solo el token y la clave se ignora. Reemplaza al supuesto anterior,
  que decia que alcanzaba con que una de las dos fuera valida.
- Q: El perfil y la respuesta del registro incluyen el identificador del usuario? -> A: Si.
  Ademas del nombre de usuario, el correo, el rol y el saldo, se devuelve el identificador.
- Q: La contrasena tiene un largo maximo? -> A: Si, 72 bytes, que es el limite del mecanismo
  de resguardo no reversible. Con caracteres simples equivale a 72 caracteres.
- Q: El correo tiene un largo maximo? -> A: Si, 254 caracteres.
- Q: La garantia de unicidad ante registros simultaneos cubre valores que solo difieren en
  mayusculas? -> A: No. Cubre registros simultaneos con el mismo valor exacto. Fuera de la
  simultaneidad, la diferencia solo en mayusculas se rechaza siempre. Es un riesgo aceptado
  por el volumen del sistema.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Registro de usuario (Priority: P1)

Una persona que todavia no tiene cuenta elige un nombre de usuario, informa su correo y
define una contrasena. El sistema crea la cuenta con rol de usuario comun, le acredita un
saldo inicial de creditos y, en esa misma respuesta y por unica vez, le entrega su clave de
API personal.

**Why this priority**: Sin cuenta no hay nada mas. Es la puerta de entrada al sistema y la
unica via por la que un usuario obtiene su primera credencial.

**Independent Test**: Se prueba de punta a punta enviando un registro valido y verificando
que la cuenta queda creada, con rol de usuario comun, con saldo inicial, y que la respuesta
trae la clave de API y no trae la contrasena. Entrega valor por si sola: el usuario queda
con una credencial utilizable.

**Acceptance Scenarios**:

1. **Given** que no existe ningun usuario con el nombre "lionel10" ni con el correo
   "lionel@mail.com", **When** una persona se registra con esos datos y una contrasena de
   al menos 8 caracteres, **Then** el sistema crea la cuenta, le asigna rol de usuario
   comun y el saldo inicial de creditos, y devuelve en la respuesta los datos de la cuenta
   creada (identificador, nombre de usuario, correo, rol y saldo) junto con la clave de API.
2. **Given** un registro exitoso, **When** se revisa la respuesta, **Then** no aparece la
   contrasena en ningun campo.
3. **Given** que ya existe un usuario con el nombre "lionel10", **When** otra persona
   intenta registrarse con ese mismo nombre, **Then** el sistema rechaza el registro e
   informa que el conflicto es por el nombre de usuario.
4. **Given** que ya existe un usuario con el correo "lionel@mail.com", **When** otra
   persona intenta registrarse con ese mismo correo, **Then** el sistema rechaza el
   registro e informa que el conflicto es por el correo.
5. **Given** un registro con nombre de usuario de 2 caracteres, con un correo mal formado
   o con una contrasena de 7 caracteres, **When** se envia, **Then** el sistema lo rechaza
   indicando cual regla no se cumplio y no crea ninguna cuenta.
6. **Given** un registro que ademas intenta declarar el rol administrador, **When** se
   envia, **Then** la cuenta se crea igualmente con rol de usuario comun.
7. **Given** un registro exitoso, **When** el usuario intenta volver a consultar su clave
   de API por cualquier via del sistema, **Then** no existe forma de recuperarla.

---

### User Story 2 - Inicio de sesion (Priority: P2)

Un usuario ya registrado se identifica con su nombre de usuario y su contrasena y obtiene
un token de sesion con el que puede operar durante 24 horas.

**Why this priority**: Es la segunda via de acceso y la unica que no obliga a conservar la
clave de API entregada en el registro.

**Independent Test**: Se prueba registrando un usuario e iniciando sesion con sus datos,
verificando que se devuelve un token, y repitiendo con datos incorrectos para verificar el
rechazo. Entrega valor por si sola: habilita el acceso recurrente.

**Acceptance Scenarios**:

1. **Given** un usuario registrado, **When** inicia sesion con su nombre de usuario y su
   contrasena correctos, **Then** el sistema le entrega un token de sesion con una vigencia
   de 24 horas contadas desde su emision.
2. **Given** un usuario registrado, **When** inicia sesion con la contrasena equivocada,
   **Then** el sistema rechaza el intento sin indicar si el error estuvo en el nombre de
   usuario o en la contrasena.
3. **Given** un nombre de usuario que no existe, **When** se intenta iniciar sesion con el,
   **Then** el sistema devuelve exactamente el mismo rechazo que en el escenario anterior.
4. **Given** un inicio de sesion exitoso, **When** se revisa la respuesta, **Then** no
   aparece ni la contrasena ni la clave de API del usuario.

---

### User Story 3 - Proteccion de los recursos (Priority: P3)

Quien quiera consultar los recursos del sistema debe identificarse, con su clave de API o
con su token de sesion. Un conjunto acotado de recursos queda accesible sin credencial.

**Why this priority**: Es lo que le da sentido a las credenciales. Depende de que existan
las historias P1 y P2 para poder probarse con credenciales reales.

**Independent Test**: Se prueba pidiendo un recurso protegido sin credencial, con una
credencial invalida y con cada una de las dos credenciales validas, y comprobando ademas
que los recursos declarados publicos responden sin credencial.

**Acceptance Scenarios**:

1. **Given** un usuario con su clave de API, **When** pide un recurso protegido
   presentandola, **Then** el sistema lo atiende y lo reconoce como ese usuario.
2. **Given** ese mismo usuario con un token de sesion vigente, **When** pide el mismo
   recurso protegido presentando el token, **Then** el sistema lo atiende y lo reconoce
   como el mismo usuario que en el escenario anterior.
3. **Given** una peticion a un recurso protegido sin ninguna credencial, **When** se envia,
   **Then** el sistema la rechaza indicando que falta la credencial.
4. **Given** una peticion con una credencial invalida, vencida o que corresponde a un
   usuario que ya no existe, **When** se envia, **Then** el sistema la rechaza indicando el
   motivo, sin revelar nada sobre credenciales validas de otros usuarios.
5. **Given** una peticion sin credencial al registro, al inicio de sesion, a la
   documentacion de la API o a la verificacion de estado del sistema, **When** se envia,
   **Then** el sistema la atiende normalmente.
6. **Given** una peticion sin credencial a un recurso de consulta del catalogo de
   jugadores, **When** se envia, **Then** el sistema la atiende normalmente por la
   excepcion transitoria vigente.
7. **Given** una peticion a un recurso protegido que trae un token de sesion invalido o
   vencido y ademas una clave de API valida, **When** se envia, **Then** el sistema la
   rechaza: el token tiene precedencia y la clave se ignora.
8. **Given** una peticion a un recurso protegido que trae un token de sesion valido y ademas
   una clave de API invalida, **When** se envia, **Then** el sistema la atiende como el
   usuario del token.

---

### User Story 4 - Consulta del perfil propio (Priority: P4)

Un usuario autenticado consulta sus propios datos: identificador, nombre de usuario, correo,
rol y saldo.

**Why this priority**: Es la primera funcion que consume la autenticacion y le permite al
usuario verificar su saldo antes de operar en el mercado.

**Independent Test**: Se prueba autenticandose con cada una de las dos credenciales y
verificando que la respuesta trae esos cinco datos y ninguno mas.

**Acceptance Scenarios**:

1. **Given** un usuario autenticado, **When** consulta su perfil, **Then** el sistema
   devuelve su identificador, su nombre de usuario, su correo, su rol y su saldo de creditos.
2. **Given** un usuario autenticado, **When** consulta su perfil, **Then** la respuesta no
   contiene ni su contrasena ni su clave de API.
3. **Given** dos usuarios distintos, **When** cada uno consulta su perfil, **Then** cada
   uno recibe unicamente sus propios datos.

---

### User Story 5 - Cambio de contrasena (Priority: P5)

Un usuario autenticado cambia su contrasena informando la actual y la nueva.

**Why this priority**: Es higiene de seguridad esperable, pero el sistema es utilizable sin
ella.

**Independent Test**: Se prueba cambiando la contrasena y verificando que la anterior deja
de servir para iniciar sesion y que la nueva si sirve.

**Acceptance Scenarios**:

1. **Given** un usuario autenticado, **When** cambia su contrasena informando la actual
   correcta y una nueva valida y distinta, **Then** el sistema acepta el cambio.
2. **Given** una contrasena recien cambiada, **When** el usuario intenta iniciar sesion con
   la contrasena anterior, **Then** el sistema rechaza el intento; **And** con la nueva, lo
   acepta.
3. **Given** un usuario autenticado, **When** informa mal su contrasena actual, **Then** el
   sistema rechaza el cambio y la contrasena vigente no se modifica.
4. **Given** un usuario autenticado, **When** propone una contrasena nueva de menos de 8
   caracteres, **Then** el sistema rechaza el cambio.
5. **Given** un usuario autenticado, **When** propone como nueva contrasena exactamente la
   que ya tiene, **Then** el sistema rechaza el cambio.
6. **Given** un usuario con un token de sesion vigente, **When** cambia su contrasena,
   **Then** ese token sigue siendo valido hasta su vencimiento.

---

### User Story 6 - Regeneracion de la clave de API (Priority: P6)

Un usuario autenticado pide una clave de API nueva; la anterior deja de valer en el acto.

**Why this priority**: Cubre la perdida o la filtracion de la clave, pero no es necesaria
para operar en el escenario feliz.

**Independent Test**: Se prueba regenerando la clave y verificando en la peticion siguiente
que la vieja es rechazada y la nueva es aceptada.

**Acceptance Scenarios**:

1. **Given** un usuario autenticado, **When** pide regenerar su clave de API, **Then** el
   sistema le devuelve una clave nueva en esa respuesta y por unica vez.
2. **Given** una clave de API recien regenerada, **When** el usuario intenta usar la clave
   anterior, **Then** el sistema la rechaza por invalida.
3. **Given** una clave de API recien regenerada, **When** el usuario usa la nueva, **Then**
   el sistema lo reconoce como el mismo usuario de siempre.
4. **Given** un usuario que regenera su clave dos veces seguidas, **When** intenta usar la
   penultima, **Then** el sistema la rechaza: solo hay una clave vigente a la vez.
5. **Given** un usuario con un token de sesion vigente, **When** regenera su clave de API,
   **Then** ese token sigue siendo valido hasta su vencimiento.

---

### User Story 7 - Alta del administrador inicial (Priority: P7)

Al arrancar, la aplicacion se asegura de que exista la cuenta administradora del mercado.
Sus credenciales vienen de la configuracion del entorno; si no estan, el arranque sigue sin
crearla y deja una advertencia.

**Why this priority**: Es la historia mas chica y la unica que ningun usuario final ejecuta.
Lo que necesita de ella la funcionalidad de mercado es solo que la cuenta exista con su rol.
Su urgencia real la marca esa funcionalidad, no esta.

**Independent Test**: Se prueba arrancando con la configuracion completa y verificando que
la cuenta existe con rol administrador y saldo cero; arrancando sin la contrasena y
verificando que no se crea nada y que la aplicacion levanta igual; y arrancando dos veces
seguidas para verificar que la segunda no toca nada.

**Acceptance Scenarios**:

1. **Given** un entorno con el nombre de usuario, el correo y la contrasena del
   administrador configurados, **When** la aplicacion arranca sobre una base vacia,
   **Then** queda creada una unica cuenta con rol administrador, con saldo de creditos en
   cero y sin clave de API.
2. **Given** un entorno sin la contrasena del administrador configurada, **When** la
   aplicacion arranca, **Then** no se crea ninguna cuenta administradora, se registra una
   advertencia y la aplicacion levanta con normalidad.
3. **Given** una base donde el administrador ya fue sembrado y despues regenero su clave de
   API, **When** la aplicacion vuelve a arrancar, **Then** la cuenta no se modifica: ni su
   contrasena ni su clave de API se pisan.
4. **Given** una base donde ya existe un usuario comun con el mismo nombre configurado para
   el administrador, **When** la aplicacion arranca, **Then** el sistema no lo crea ni le
   cambia el rol ni ningun otro dato.
5. **Given** el administrador sembrado, **When** inicia sesion con su contrasena y regenera
   su clave de API, **Then** obtiene una clave valida por el mismo mecanismo que cualquier
   otro usuario.
6. **Given** el administrador sembrado, **When** consulta su perfil, **Then** ve su rol
   administrador y su saldo en cero.

---

### Edge Cases

- El registro llega con el nombre de usuario y el correo ya tomados al mismo tiempo: la
  respuesta informa los dos conflictos, no solo uno.
- El nombre de usuario o el correo se envian con diferencias de mayusculas y minusculas
  respecto de uno ya existente: se tratan como el mismo y el registro se rechaza por
  conflicto.
- El nombre de usuario o el correo llegan con espacios al principio o al final: se recortan
  antes de validar y de comparar.
- Valores exactamente en el limite: nombre de usuario de 3 y de 30 caracteres, contrasena
  de 8 y de 72 caracteres simples, correo de 254 caracteres. Todos son validos; un caracter
  mas sobre cualquiera de los maximos se rechaza.
- La contrasena tiene 72 caracteres o menos pero, por incluir acentos o simbolos, supera los
  72 bytes: se rechaza por largo.
- El nombre de usuario trae caracteres no admitidos (espacios, guiones medios, acentos,
  simbolos): se rechaza.
- Dos registros con exactamente el mismo nombre de usuario, o exactamente el mismo correo,
  llegan casi simultaneamente: solo uno crea la cuenta; el otro recibe el rechazo por
  conflicto. Si llegan a la vez y solo difieren en mayusculas, esta garantia no aplica (ver
  Assumptions).
- La peticion trae a la vez la clave de API y un token de sesion: se evalua unicamente el
  token y la clave se ignora. Con un token valido se atiende aunque la clave sea invalida;
  con un token invalido o vencido se rechaza aunque la clave sea valida.
- La credencial esta bien formada pero el usuario al que apunta ya no existe: se rechaza
  como credencial invalida, sin distinguirla de cualquier otra invalida.
- El token de sesion se presenta apenas pasado su vencimiento: se rechaza por vencido.
- Se presenta una credencial invalida contra un recurso publico: el recurso publico
  responde igual, la credencial invalida no lo bloquea.
- El usuario cambia su contrasena y despues intenta cambiarla de nuevo usando la anterior
  como contrasena actual: se rechaza.
- Alguien intenta registrarse publicamente con el mismo nombre que tiene configurado el
  administrador, y el administrador ya fue sembrado: se rechaza por conflicto de nombre de
  usuario, como cualquier otro.
- El nombre configurado para el administrador no cumple las reglas de FR-002, o el correo no
  tiene formato valido: el alta no se ejecuta y queda registrada la advertencia
  correspondiente.
- La contrasena del administrador se cambia en la configuracion del entorno y la aplicacion
  se reinicia: por idempotencia, la cuenta existente no se toca y sigue valiendo la
  contrasena original.

## Requirements *(mandatory)*

### Functional Requirements

#### Registro

- **FR-001**: El sistema MUST permitir que cualquier persona se registre indicando nombre
  de usuario, correo electronico y contrasena.
- **FR-002**: El sistema MUST aceptar unicamente nombres de usuario de entre 3 y 30
  caracteres compuestos por letras, numeros y guiones bajos.
- **FR-003**: El sistema MUST aceptar unicamente correos electronicos con formato valido y
  de a lo sumo 254 caracteres.
- **FR-004**: El sistema MUST aceptar unicamente contrasenas de al menos 8 caracteres y de a
  lo sumo 72 bytes.
- **FR-005**: El sistema MUST garantizar que el nombre de usuario sea unico en todo el
  sistema.
- **FR-006**: El sistema MUST garantizar que el correo electronico sea unico en todo el
  sistema.
- **FR-007**: El sistema MUST informar, ante un registro en conflicto, cual de los dos
  datos (nombre de usuario o correo) esta tomado; si los dos lo estan, MUST informar ambos.
- **FR-008**: El sistema MUST NOT almacenar la contrasena tal como la escribio el usuario;
  MUST guardarla de forma no reversible.
- **FR-009**: El sistema MUST NOT incluir la contrasena en ninguna respuesta, en ningun
  escenario.
- **FR-010**: El sistema MUST asignar rol de usuario comun a todo usuario creado por el
  registro publico.
- **FR-011**: El sistema MUST contemplar un rol administrador reservado para operaciones de
  gestion del mercado, y MUST impedir que se obtenga por el registro publico, ignorando
  cualquier rol que el cliente intente declarar. La unica via por la que existe un
  administrador es el alta de arranque descripta en FR-036.
- **FR-012**: El sistema MUST acreditarle a cada usuario creado por el registro publico un
  saldo inicial de creditos. El monto MUST ser un parametro de configuracion del sistema,
  resuelto por entorno, MUST ser el mismo para todos los usuarios que se registren bajo esa
  configuracion, y MUST tener un valor por defecto que se aplica cuando el parametro no
  esta definido.
- **FR-013**: El sistema MUST entregar una clave de API personal en la respuesta al
  registro publico exitoso.
- **FR-014**: El sistema MUST mostrar esa clave de API una unica vez y MUST NOT ofrecer
  ninguna via para volver a consultarla.

#### Inicio de sesion

- **FR-015**: Los usuarios registrados MUST poder iniciar sesion con su nombre de usuario y
  su contrasena.
- **FR-016**: El sistema MUST entregar, ante credenciales correctas, un token de sesion con
  una vigencia de 24 horas contadas desde su emision.
- **FR-017**: El sistema MUST rechazar los intentos con credenciales incorrectas con una
  respuesta identica, sin revelar si el error estuvo en el nombre de usuario o en la
  contrasena.
- **FR-018**: El token de sesion MUST identificar al mismo usuario que su clave de API.

#### Acceso a los recursos

- **FR-019**: El sistema MUST exigir una credencial valida (clave de API o token de sesion)
  para atender cualquier recurso que no este declarado publico; ambas credenciales MUST ser
  igualmente validas y MUST resolver la misma identidad de usuario.
- **FR-019a**: Si la peticion trae ambas credenciales, el sistema MUST evaluar unicamente el
  token de sesion e ignorar la clave de API: la peticion se atiende o se rechaza segun el
  token.
- **FR-020**: El sistema MUST rechazar la peticion indicando el motivo cuando no trae
  credencial, cuando la credencial es invalida, cuando esta vencida o cuando corresponde a
  un usuario inexistente.
- **FR-021**: El sistema MUST NOT revelar, en esos rechazos, informacion que permita
  deducir credenciales validas ni la existencia de otros usuarios.
- **FR-022**: El sistema MUST mantener accesibles sin credencial el registro, el inicio de
  sesion, la documentacion de la API y la verificacion de estado del sistema.
- **FR-023**: El sistema MUST mantener accesibles sin credencial, de forma transitoria, los
  recursos de consulta del catalogo de jugadores. Esta excepcion MUST estar acotada
  exclusivamente a esos recursos de consulta y MUST retirarse en una etapa posterior, una
  vez integradas ambas funcionalidades.

#### Perfil propio

- **FR-024**: Los usuarios autenticados MUST poder consultar sus propios datos de perfil:
  identificador, nombre de usuario, correo, rol y saldo de creditos.
- **FR-025**: El perfil MUST NOT incluir la contrasena ni la clave de API.
- **FR-026**: El sistema MUST devolver unicamente los datos del usuario que presenta la
  credencial.

#### Cambio de contrasena

- **FR-027**: Los usuarios autenticados MUST poder cambiar su contrasena indicando la
  actual y la nueva.
- **FR-028**: El sistema MUST rechazar el cambio, sin modificar nada, si la contrasena
  actual informada no coincide.
- **FR-029**: El sistema MUST exigirle a la contrasena nueva las mismas reglas que en el
  registro.
- **FR-030**: El sistema MUST exigir que la contrasena nueva sea distinta de la actual.
- **FR-031**: Tras un cambio exitoso, el sistema MUST aceptar la contrasena nueva y MUST
  rechazar la anterior en los inicios de sesion posteriores.

#### Regeneracion de la clave de API

- **FR-032**: Los usuarios autenticados MUST poder regenerar su clave de API, y el sistema
  MUST entregar la clave nueva una unica vez en esa respuesta.
- **FR-033**: El sistema MUST invalidar la clave anterior de inmediato.
- **FR-034**: El sistema MUST mantener a lo sumo una clave de API vigente por usuario. Un
  usuario puede no tener ninguna, como ocurre con el administrador antes de regenerarla.

#### Vigencia de las sesiones

- **FR-035**: El sistema MUST mantener validos hasta su vencimiento los tokens de sesion ya
  emitidos, aunque el usuario cambie su contrasena o regenere su clave de API.

#### Alta del administrador inicial

- **FR-036**: El sistema MUST dar de alta exactamente un usuario con rol administrador al
  arrancar la aplicacion.
- **FR-037**: El sistema MUST leer el nombre de usuario, el correo y la contrasena de ese
  administrador de la configuracion del sistema, resuelta desde variables de entorno. El
  repositorio MUST NOT contener ningun valor por defecto para esas tres credenciales.
- **FR-038**: El sistema MUST omitir el alta cuando la contrasena del administrador no esta
  configurada, MUST registrar una advertencia en ese caso y MUST arrancar igualmente. El
  mismo tratamiento MUST aplicarse cuando la configuracion del administrador esta incompleta
  o no cumple las reglas de FR-002, FR-003 y FR-004: se omite el alta, se advierte y la
  aplicacion levanta.
- **FR-039**: El alta MUST ser idempotente: si ya existe un usuario con ese nombre, el
  sistema MUST NOT crearlo de nuevo ni modificar ninguno de sus datos, incluidas su
  contrasena y su clave de API.
- **FR-040**: El sistema MUST guardar la contrasena del administrador de forma no
  reversible, igual que en el registro publico.
- **FR-041**: El sistema MUST NOT emitirle una clave de API al administrador al sembrarlo.
  El administrador MUST poder obtener una iniciando sesion con su contrasena y usando el
  mecanismo de regeneracion de FR-032.
- **FR-042**: El sistema MUST crear al administrador con saldo de creditos en cero.

### Key Entities

- **Usuario**: la persona registrada en el sistema. Atributos: identificador, nombre de
  usuario (unico), correo electronico (unico), contrasena guardada de forma no reversible,
  rol y saldo de creditos. Es el titular de las dos credenciales.
- **Rol**: la categoria que determina que puede hacer un usuario. Dos valores: usuario
  comun, el unico que otorga el registro publico, y administrador, reservado para la gestion
  del mercado. El administrador existe por una sola via, el alta de arranque de FR-036, y su
  cuenta nace con saldo cero y sin clave de API. Esta funcionalidad garantiza unicamente que
  esa cuenta exista con su rol; que puede hacer un administrador lo define la funcionalidad
  de mercado.
- **Clave de API**: credencial personal y permanente del usuario. Una sola vigente por
  usuario. Se entrega una unica vez, al crearse o al regenerarse, y no se puede volver a
  consultar.
- **Token de sesion**: credencial temporal que el sistema emite al iniciar sesion. Vigencia
  de 24 horas desde su emision. Identifica al mismo usuario que su clave de API y no se
  revoca antes de vencer.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Una persona pasa de no tener cuenta a tener una credencial utilizable en un
  unico paso, sin ninguna intervencion manual de por medio.
- **SC-002**: El 100% de los registros con un nombre de usuario o un correo ya tomados se
  rechazan e indican cual de los dos datos genero el conflicto.
- **SC-003**: La contrasena no aparece en ninguna respuesta del sistema en ningun
  escenario, y la clave de API aparece unicamente en las dos respuestas que la emiten: la
  del registro y la de la regeneracion.
- **SC-004**: Un token de sesion habilita el acceso durante 24 horas y deja de ser aceptado
  a partir de ese momento.
- **SC-005**: El 100% de las peticiones a recursos protegidos sin credencial valida se
  rechazan, y cada rechazo indica el motivo.
- **SC-006**: Las dos credenciales dan acceso al mismo conjunto de recursos y resuelven la
  misma identidad de usuario.
- **SC-007**: Los rechazos de inicio de sesion por usuario inexistente y por contrasena
  incorrecta son indistinguibles entre si.
- **SC-008**: Inmediatamente despues de regenerar la clave de API, la clave anterior deja
  de ser aceptada y la nueva es aceptada.
- **SC-009**: Ningun registro publico produce un usuario con rol administrador.
- **SC-010**: Los recursos declarados publicos responden sin credencial en el 100% de los
  casos, incluida la excepcion transitoria del catalogo de jugadores.
- **SC-011**: Con la configuracion del administrador completa, arrancar la aplicacion N
  veces seguidas deja exactamente una cuenta administradora, identica despues del primer
  arranque.
- **SC-012**: Sin la contrasena del administrador configurada, la aplicacion arranca igual,
  deja registrada la advertencia y no crea ninguna cuenta.
- **SC-013**: El repositorio no contiene en ningun archivo el nombre de usuario, el correo
  ni la contrasena del administrador.

## Assumptions

- El nombre de usuario y el correo se comparan sin distinguir mayusculas de minusculas,
  tanto para verificar unicidad como para iniciar sesion; se conservan tal como los
  escribio el usuario, salvo el recorte de espacios al principio y al final.
- El registro no emite un token de sesion. Para obtenerlo, el usuario inicia sesion.
- La clave de API se guarda de forma no reversible, igual que la contrasena. Es la razon
  por la que no se puede volver a mostrar.
- Si una peticion presenta las dos credenciales a la vez, el token de sesion tiene
  precedencia: se evalua solo el token y la clave de API se ignora. Se prefiere un resultado
  deterministico a probar una credencial tras otra.
- El largo maximo de la contrasena es de 72 bytes, el limite del mecanismo de resguardo no
  reversible: los bytes que pasaran ese limite no se tendrian en cuenta al verificarla. Con
  caracteres simples equivale a 72 caracteres; con acentos o simbolos el limite se alcanza
  antes.
- El largo maximo del correo es de 254 caracteres, el maximo practico que admite el estandar
  de correo electronico.
- La unicidad sin distinguir mayusculas se garantiza siempre entre registros sucesivos. Ante
  registros simultaneos, la garantia cubre valores identicos: dos registros simultaneos que
  solo difieran en mayusculas podrian crearse ambos. Es un riesgo aceptado por el volumen del
  sistema.
- Los recursos declarados publicos responden aunque la peticion traiga una credencial
  invalida: una credencial invalida no bloquea un recurso publico.
- El saldo inicial es igual para todos los usuarios que se registran bajo una misma
  configuracion y no se modifica dentro de esta funcionalidad: las operaciones de compra y
  venta quedan fuera de alcance.
- **Valor por defecto del saldo inicial: 1000 creditos.** El equipo no fijo un monto, y
  FR-012 lo deja como parametro de configuracion. Se eligio 1000 por coherencia con la venta
  inicial a 1 credito mencionada por el equipo: alcanza para un catalogo de varios cientos
  de jugadores sin volver trivial el juego. Al ser configuracion, ajustarlo es cambiar un
  valor en un solo lugar.
- El rol de un usuario no cambia dentro del alcance de esta funcionalidad, ni siquiera en el
  alta de arranque: si ya existe un usuario con el nombre configurado para el administrador,
  se lo deja como esta.
- El alta del administrador corre en todos los entornos por igual. Lo que la habilita o la
  omite es unicamente si la configuracion esta presente, no en que entorno corre. Por eso el
  entorno de tests, que no la define, queda sin cuenta administradora salvo que un test la
  configure a proposito.
- La advertencia de FR-038 queda en el registro de la aplicacion; no es una respuesta a
  ningun cliente.
- No existe ningun mecanismo de revocacion anticipada de tokens: un token emitido vale
  hasta su vencimiento en todos los casos.
- Los mensajes de error del sistema se redactan en espanol, segun el principio VII de la
  constitucion del proyecto.

## Dependencies

- **Catalogo de jugadores** (en desarrollo en paralelo): esta funcionalidad depende de el
  en un unico punto, la excepcion transitoria de FR-023, que deja sus recursos de consulta
  accesibles sin credencial. Esa excepcion se retira en una etapa posterior, una vez
  integradas ambas funcionalidades.
- **Mercado** (posterior): depende de esta funcionalidad en un unico punto, el alta de la
  cuenta administradora de FR-036. Esta funcionalidad garantiza que esa cuenta exista con su
  rol y con saldo cero; la carga inicial de jugadores y su venta son responsabilidad del
  mercado.

## Fuera de Alcance

No forman parte de esta funcionalidad:

- La recuperacion de contrasena por correo electronico.
- La verificacion del correo electronico.
- La renovacion y la revocacion anticipada de tokens de sesion.
- El inicio de sesion con proveedores externos.
- La eliminacion de cuentas.
- La administracion de usuarios por parte de un administrador.
- La limitacion de intentos por unidad de tiempo.
- Las operaciones de compra y venta en el mercado de jugadores.
- La carga inicial de jugadores y su venta a 1 credito por parte del administrador: son
  parte de la funcionalidad de mercado. Aca solo se garantiza que la cuenta administradora
  exista con su rol.
- Que puede hacer un administrador mas alla de existir con su rol: ningun recurso de esta
  funcionalidad distingue por rol.
