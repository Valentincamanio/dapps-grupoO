# Feature Specification: Integracion con Football-Data.org

**Feature Branch**: `feature/api-footballdata`

**Created**: 2026-10-07

**Status**: Draft

**Input**: User description: "Integracion con Football-Data.org: el catalogo de jugadores de las cinco ligas (Premier League, Bundesliga, La Liga, Serie A y Ligue 1) deja de salir de un dataset ficticio y pasa a construirse y mantenerse actualizado desde esa fuente externa oficial. Ademas se guardan localmente la temporada en curso de cada liga y todos sus partidos (calendario y resultados), para que la cotizacion de la Entrega 2 los use. La sincronizacion corre sola todos los lunes a las 04:00 y un administrador la puede disparar a mano, para las cinco ligas o para una sola. Si la fuente falla, el sistema sigue funcionando con lo que tiene guardado."

## Clarifications

### Session 2026-10-06

Decisiones del equipo tomadas antes de escribir el spec, a partir de lo verificado contra la
fuente ese dia (plan gratis, temporada 2026/27):

- Q: Que datos de la fuente entran al sistema? -> A: Los equipos, los jugadores, la
  temporada en curso y los partidos de las cinco ligas. Se traen ahora aunque la cotizacion
  todavia no este definida, para no volver sobre la integracion. La tabla de posiciones y
  los goleadores no se guardan.
- Q: Se importan todos los jugadores de los planteles o solo una seleccion? -> A: Todos.
- Q: Que pasa con el dataset ficticio actual y su carga inicial? -> A: Se eliminan, sin
  dejarlos como respaldo.
- Q: Que pasa con un jugador que deja las cinco ligas? -> A: Queda inactivo y nunca se
  borra, porque puede haber tokens comprados; se reactiva si vuelve. Un jugador que pasa de
  una liga a otra de las cinco sigue siendo el mismo y cambia de equipo y de liga.
- Q: Cualquier sincronizacion puede inactivar jugadores? -> A: No. Solo una sincronizacion
  completa en la que las cinco ligas se procesaron bien. Una sincronizacion de una sola
  liga, o una completa con alguna liga fallida, no inactiva a nadie.
- Q: Como se traducen las posiciones de la fuente? -> A: Goalkeeper a arquero, Defence a
  defensor, Midfield a mediocampista y Offence a delantero.
- Q: Que pasa con los jugadores que la fuente informa sin posicion? -> A: Se saltean y
  quedan en el informe de la sincronizacion.
- Q: Que nombre guarda cada equipo? -> A: Su nombre oficial exacto, sin el nombre corto que
  tambien informa la fuente, y ademas su escudo. El filtro por equipo compara contra ese
  nombre exacto.
- Q: Que datos personales guarda cada jugador? -> A: Ademas de los que ya tiene el
  catalogo, la fecha de nacimiento y la nacionalidad.
- Q: Como se reconoce una temporada, un equipo, un jugador o un partido de una
  sincronizacion a otra? -> A: Por el identificador que le asigna la fuente.
- Q: Cuando corre la sincronizacion automatica? -> A: Todos los lunes a las 04:00.
- Q: Quien puede disparar la sincronizacion a mano y sobre que? -> A: Solo un administrador
  (rol ADMIN), para las cinco ligas juntas o para una sola.
- Q: Que alcance tiene una falla de la fuente? -> A: Una liga que falla no afecta a las
  demas ni a lo ya guardado: todo o nada por liga. Las consultas del catalogo nunca dependen
  de la fuente.
- Q: Donde se configura la credencial de la fuente? -> A: En una variable de entorno; nunca
  esta en el repositorio. Sin ella, la aplicacion arranca igual, registra una advertencia y
  la sincronizacion queda deshabilitada.
- Q: Se pueden tocar los tests que dependen del dataset ficticio o de la forma anterior del
  modelo del catalogo? -> A: Si. Lucas dio el si explicito el 2026-10-06 para modificarlos o
  borrarlos (Principio IV de la constitucion). Un comportamiento que no cambia conserva su
  test.

### Session 2026-10-07

Decisiones abiertas que quedaron al escribir el spec, resueltas por el equipo:

- Q: Como se muestran los jugadores inactivos en la consulta del catalogo? -> A: El
  listado muestra solo los activos, tambien al filtrar. El detalle por identificador
  responde para cualquier jugador e indica si esta activo o inactivo. Se descartaron
  listar a todos con su estado, porque el filtro por equipo traeria a los que se fueron, y
  sumar un filtro opcional para pedir los inactivos.
- Q: Que hace la aplicacion si arranca con el catalogo vacio y la credencial configurada?
  -> A: Dispara una sincronizacion completa en segundo plano, sin demorar el arranque. Si
  el catalogo ya tiene datos, no hace nada. Se descarto esperar al disparo manual o a la
  corrida del lunes.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Construccion del catalogo desde la fuente (Priority: P1)

Un administrador dispara una sincronizacion completa. El sistema trae de Football-Data.org
los equipos y los planteles de las cinco ligas, traduce las posiciones, saltea a los
jugadores que llegan sin posicion y guarda todo localmente. Al terminar devuelve un informe
de lo que hizo. Desde ese momento el catalogo muestra a los jugadores reales de las cinco
ligas, cada uno con su fecha de nacimiento, su nacionalidad y el escudo de su equipo. El
dataset ficticio deja de existir.

**Why this priority**: Es el escenario de evaluacion del enunciado: construir la base de
jugadores a partir de las fuentes externas definidas. Sin esto el catalogo sigue siendo
ficticio, y lo que viene despues (cotizacion, mercado, WhoScored) no tiene sobre que
trabajar.

**Independent Test**: Se prueba disparando una sincronizacion completa como administrador
sobre una base vacia y verificando que el catalogo queda con los equipos y los jugadores de
las cinco ligas y las posiciones traducidas, que los jugadores sin posicion figuran en el
informe y no en el catalogo, y que no existe ningun dato del dataset ficticio. Entrega
valor por si sola: el catalogo pasa a ser real.

**Acceptance Scenarios**:

1. **Given** una base vacia y la credencial de la fuente configurada, **When** un
   administrador dispara una sincronizacion completa y la fuente responde con normalidad,
   **Then** el catalogo queda con los equipos y los jugadores de las cinco ligas, cada
   equipo asociado a su liga.
2. **Given** que la fuente informa jugadores con posicion Goalkeeper, Defence, Midfield y
   Offence, **When** se sincroniza, **Then** quedan guardados como arquero, defensor,
   mediocampista y delantero, respectivamente.
3. **Given** que la fuente informa un jugador nuevo sin posicion, **When** se sincroniza,
   **Then** el jugador no se guarda **And** el informe lo lista con su nombre y su equipo.
4. **Given** una sincronizacion terminada, **When** el administrador recibe la respuesta,
   **Then** obtiene el informe con el tipo de sincronizacion, el inicio y el fin y, por
   cada liga, si se proceso o fallo y cuantos equipos, jugadores y partidos se crearon, se
   actualizaron o se omitieron.
5. **Given** una sincronizacion terminada, **When** se consulta el detalle de un jugador
   del catalogo, **Then** incluye su nombre, su posicion, su equipo, su liga, su fecha de
   nacimiento, su nacionalidad, el escudo de su equipo y si esta activo o inactivo.
6. **Given** que la fuente informa el equipo "Liverpool FC", **When** se filtra el catalogo
   por "Liverpool FC", **Then** se obtienen solo jugadores de ese equipo; **And** al
   filtrar por "Liverpool", su nombre corto, se obtiene una pagina vacia.
7. **Given** la aplicacion arrancando sobre una base vacia, **When** se consulta el
   catalogo antes de cualquier sincronizacion, **Then** no aparece ninguno de los jugadores
   ni de los equipos del dataset ficticio.
8. **Given** un usuario autenticado con rol de usuario comun, **When** intenta disparar una
   sincronizacion, **Then** el sistema la rechaza por falta de permiso y no consulta la
   fuente.
9. **Given** una peticion sin credencial para disparar una sincronizacion, **When** se
   envia, **Then** el sistema la rechaza por falta de credencial y no consulta la fuente.
10. **Given** un catalogo ya sincronizado, **When** el administrador vuelve a disparar una
    sincronizacion completa y la fuente informa exactamente lo mismo, **Then** no se crea
    ningun equipo ni jugador nuevo **And** el informe cuenta a los existentes como
    actualizados.

---

### User Story 2 - Temporadas y partidos de cada liga (Priority: P2)

La misma sincronizacion guarda, para cada liga, la temporada en curso con su inicio, su fin
y su jornada actual, y todos los partidos de esa temporada: los jugados con su resultado y
los programados con su fecha. Estos datos todavia no se muestran: quedan guardados para la
cotizacion de la Entrega 2.

**Why this priority**: La cotizacion de la Entrega 2 los necesita, y traerlos ahora evita
volver sobre la integracion. No es P1 porque el catalogo tiene valor sin ellos.

**Independent Test**: Se prueba ejecutando una sincronizacion y verificando, en el informe
y en lo guardado, que cada liga tiene su temporada en curso con sus fechas y su jornada, y
que estan todos los partidos de la temporada con su estado y, si se jugaron, con su
resultado.

**Acceptance Scenarios**:

1. **Given** una sincronizacion completa exitosa, **When** se revisa lo guardado, **Then**
   cada una de las cinco ligas tiene su temporada en curso con inicio, fin y jornada actual
   (por ejemplo, la Premier League 2026/27 empieza el 21/08).
2. **Given** una sincronizacion completa exitosa, **When** se revisa lo guardado, **Then**
   estan todos los partidos de la temporada que informa la fuente (por ejemplo, los 380 de
   la Premier League), cada uno con fecha y hora, jornada, estado, equipo local y equipo
   visitante.
3. **Given** un partido ya jugado, **When** se sincroniza, **Then** queda guardado con su
   resultado final, su resultado del primer tiempo y su ganador.
4. **Given** un partido guardado como programado que la fuente ahora informa terminado,
   **When** se vuelve a sincronizar, **Then** el mismo partido se actualiza con su
   resultado y no se crea otro.
5. **Given** un partido que la fuente informa postergado, suspendido, cancelado o
   adjudicado, **When** se sincroniza, **Then** se guarda con ese estado.
6. **Given** un partido que referencia a un equipo que no esta en el catalogo, **When** se
   sincroniza, **Then** el partido no se guarda **And** figura en el informe como omitido.
7. **Given** una liga cuya temporada guardada ya no es la que la fuente informa como en
   curso, **When** se sincroniza, **Then** se guarda la temporada nueva con sus partidos
   **And** la anterior y sus partidos se conservan.

---

### User Story 3 - Funcionamiento ante fallas de la fuente (Priority: P3)

La fuente puede no responder, tardar demasiado, rechazar consultas por exceso o no estar
configurada. En cualquiera de esos casos el sistema sigue funcionando con lo que tiene
guardado: una liga que falla no arrastra a las demas ni queda a medio actualizar, y las
consultas del catalogo nunca dependen de la fuente.

**Why this priority**: El enunciado exige tolerar las fallas del proveedor externo. Es P3
porque necesita de P1 y P2 para tener algo que proteger.

**Independent Test**: Se prueba simulando que la fuente no responde, que tarda mas de lo
admitido, que informa el limite de consultas excedido o que falla en una sola liga, y
verificando en cada caso que lo guardado de la liga afectada no cambia, que las demas se
procesan y que el catalogo responde igual. Tambien arrancando sin la credencial.

**Acceptance Scenarios**:

1. **Given** un catalogo sincronizado y la fuente sin responder, **When** se consulta el
   catalogo, **Then** responde con los datos guardados, igual que con la fuente disponible.
2. **Given** una sincronizacion completa en la que la fuente falla para una liga, **When**
   termina, **Then** las otras cuatro quedan procesadas **And** la liga fallida conserva
   exactamente lo que tenia **And** el informe la marca como fallida, con su motivo.
3. **Given** una liga cuyos equipos se trajeron bien pero cuyos partidos fallaron, **When**
   termina la sincronizacion, **Then** no se guarda ningun cambio de esa liga: ni de
   equipos, ni de jugadores, ni de temporada, ni de partidos.
4. **Given** una consulta a la fuente que no responde en 30 segundos, **When** se cumple
   ese tiempo, **Then** la liga se da por fallida **And** la sincronizacion sigue con las
   demas.
5. **Given** que la fuente responde que se excedio el limite de consultas, **When** el
   sistema espera el tiempo que ella indica y reintenta, **Then** si el reintento responde
   bien la liga se procesa; **And** si vuelve a fallar, la liga se da por fallida sin mas
   reintentos.
6. **Given** que la fuente no informa ningun equipo para una liga, **When** se sincroniza,
   **Then** esa liga se da por fallida y conserva lo que tenia.
7. **Given** un entorno sin la credencial de la fuente, **When** la aplicacion arranca,
   **Then** levanta con normalidad y registra una advertencia **And** la sincronizacion
   queda deshabilitada: el disparo manual se rechaza informando que esta deshabilitada y la
   corrida programada no se ejecuta.
8. **Given** una sincronizacion en la que la fuente rechaza la credencial, **When**
   termina, **Then** todas las ligas quedan fallidas con ese motivo y nada de lo guardado
   cambia.
9. **Given** cualquier sincronizacion, exitosa o fallida, **When** se revisan el registro de
   la aplicacion, la respuesta del disparo manual y el informe, **Then** la credencial de
   la fuente no aparece en ninguno.

---

### User Story 4 - Cambios entre sincronizaciones (Priority: P4)

Entre una semana y otra los planteles cambian: hay transferencias, jugadores que dejan las
cinco ligas, jugadores que vuelven y datos que la fuente corrige. Cada sincronizacion
refleja esos cambios sin borrar nada: el que se va queda inactivo, el que vuelve se
reactiva y el que cambia de club sigue siendo el mismo jugador.

**Why this priority**: Mantiene el catalogo al dia con el paso de las semanas. Una primera
carga sirve sin esto, pero se desactualiza rapido.

**Independent Test**: Se prueba con dos sincronizaciones seguidas en las que la fuente
cambia de una a otra (un jugador cambia de equipo, otro desaparece de todos los planteles,
otro reaparece y otro trae datos corregidos) y verificando el estado de cada uno despues de
la segunda.

**Acceptance Scenarios**:

1. **Given** un jugador del Liverpool FC que la fuente pasa a informar en el plantel de otro
   equipo de las cinco ligas, **When** se sincroniza esa otra liga o se hace una
   sincronizacion completa, **Then** es el mismo jugador del catalogo, ahora con su nuevo
   equipo y la liga de ese equipo.
2. **Given** un jugador que ya no aparece en ningun plantel de las cinco ligas, **When**
   termina una sincronizacion completa en la que las cinco ligas se procesaron bien,
   **Then** el jugador queda inactivo, conserva su ultimo equipo y figura en el informe como
   inactivado; **And** no se borra.
3. **Given** ese mismo jugador ausente, **When** termina una sincronizacion de una sola liga
   o una completa con alguna liga fallida, **Then** el jugador sigue activo.
4. **Given** un jugador inactivo que la fuente vuelve a informar en un plantel de las cinco
   ligas, **When** se sincroniza, **Then** queda activo con el equipo que informa la fuente
   **And** figura en el informe como reactivado.
5. **Given** un jugador o un equipo cuyos datos corrigio la fuente (nombre, posicion, fecha
   de nacimiento, nacionalidad, nombre oficial o escudo), **When** se sincroniza, **Then**
   el catalogo muestra los datos corregidos.
6. **Given** un jugador ya guardado que la fuente ahora informa sin posicion, **When** se
   sincroniza, **Then** conserva la posicion que tenia **And** cuenta como presente en el
   plantel, por lo que no se inactiva.
7. **Given** que la fuente informa al mismo jugador en dos planteles, **When** se
   sincroniza, **Then** el jugador queda en uno solo de esos equipos **And** el caso figura
   en el informe.
8. **Given** un equipo que desciende y deja de aparecer en su liga, **When** se sincroniza,
   **Then** el equipo no se borra y conserva su ultima liga.
9. **Given** un jugador inactivo cuyo ultimo equipo es el Liverpool FC, **When** se lista
   el catalogo, sin filtros o filtrando por "Liverpool FC", **Then** el jugador no aparece.
10. **Given** ese mismo jugador inactivo, **When** se consulta su detalle por
    identificador, **Then** el sistema lo devuelve con sus datos **And** indica que esta
    inactivo.
11. **Given** un jugador reactivado, **When** se lista el catalogo, **Then** vuelve a
    aparecer con el equipo que informa la fuente.

---

### User Story 5 - Sincronizacion automatica semanal y por liga (Priority: P5)

Todos los lunes a las 04:00 la sincronizacion completa corre sola, sin que nadie la
dispare. Tambien corre sola cuando la aplicacion arranca con el catalogo vacio. Ademas, un
administrador puede sincronizar una sola liga cuando lo necesita. Nunca corren dos
sincronizaciones a la vez.

**Why this priority**: Automatiza lo que P1 permite hacer a mano. El sistema es utilizable
sin esto, disparando la sincronizacion manualmente.

**Independent Test**: Se prueba llevando el reloj al lunes a las 04:00 y verificando que
arranca una sincronizacion completa; arrancando con el catalogo vacio y con datos y
verificando que solo en el primer caso se sincroniza; disparando la sincronizacion de una
sola liga y verificando que las demas no cambian; y disparando una sincronizacion mientras
otra esta en curso.

**Acceptance Scenarios**:

1. **Given** la aplicacion encendida y la credencial configurada, **When** llega el lunes a
   las 04:00 hora de Argentina, **Then** arranca sola una sincronizacion completa **And** su
   informe queda en el registro de la aplicacion.
2. **Given** un administrador, **When** dispara la sincronizacion de una sola de las cinco
   ligas, **Then** se procesa solo esa liga **And** lo guardado de las otras cuatro no
   cambia **And** no se inactiva a nadie.
3. **Given** un administrador, **When** pide sincronizar una liga que no es una de las
   cinco, **Then** el sistema rechaza el pedido indicando el valor no admitido y no consulta
   la fuente.
4. **Given** una sincronizacion en curso, **When** un administrador dispara otra, **Then**
   el sistema la rechaza informando que ya hay una en curso.
5. **Given** una sincronizacion en curso, **When** llega la hora de la corrida programada,
   **Then** esa corrida se omite **And** el motivo queda en el registro de la aplicacion.
6. **Given** la aplicacion apagada el lunes a las 04:00, **When** se vuelve a encender,
   **Then** la corrida perdida no se recupera; queda el disparo manual.
7. **Given** un catalogo vacio y la credencial configurada, **When** la aplicacion
   arranca, **Then** termina de arrancar sin esperar a la fuente **And** se dispara sola
   una sincronizacion completa en segundo plano, cuyo informe queda en el registro de la
   aplicacion.
8. **Given** un catalogo con datos y la credencial configurada, **When** la aplicacion
   arranca, **Then** no se dispara ninguna sincronizacion.
9. **Given** la sincronizacion de arranque en curso, **When** un administrador dispara
   otra, **Then** el sistema la rechaza informando que ya hay una en curso.

---

### Edge Cases

- La fuente informa al mismo jugador en dos planteles, de la misma liga o de ligas
  distintas: queda en un solo equipo y el caso va al informe.
- Un jugador pasa de una liga a otra de las cinco y se sincroniza solo la liga de origen:
  como esa sincronizacion no inactiva a nadie, el jugador sigue en su equipo anterior hasta
  que se sincronice la liga de destino o se haga una sincronizacion completa.
- Un jugador nuevo sin nombre, o con una posicion distinta de las cuatro que informa la
  fuente, se trata como un jugador sin posicion: se saltea y va al informe.
- Un jugador ya guardado que llega con una posicion distinta de las cuatro conserva la que
  tenia, igual que si llegara sin posicion; uno que llega sin nombre conserva el nombre que
  tenia. En ambos casos cuenta como presente en el plantel.
- Un jugador sin fecha de nacimiento o sin nacionalidad entra igual, con ese dato vacio.
- El nombre oficial de un equipo trae caracteres especiales, como "FC Bayern Munchen", que
  la fuente escribe con dieresis sobre la u: se guarda tal cual y el filtro por equipo solo
  coincide con el nombre escrito exactamente igual, dieresis incluida.
- La fuente informa que se excedio el limite de consultas: se espera lo que ella indica y
  se reintenta una vez; si vuelve a fallar, la liga se da por fallida.
- La fuente tarda mas de 30 segundos en responder una consulta: la liga se da por fallida y
  se sigue con las demas.
- La fuente responde bien pero no informa ningun equipo para una liga: la liga se da por
  fallida.
- Fallan las cinco ligas en una misma sincronizacion: nada de lo guardado cambia, no se
  inactiva a nadie y el disparo manual devuelve igual el informe, con las cinco fallidas.
- Un partido referencia a un equipo que no esta en el catalogo: no se guarda y va al
  informe, sin que la liga se de por fallida.
- Un partido postergado cambia de fecha entre una sincronizacion y otra: se actualiza el
  mismo partido, nunca se duplica.
- Un equipo desciende: conserva su ultima liga y no se borra. Sus jugadores que no aparecen
  en ningun otro plantel quedan inactivos tras la siguiente sincronizacion completa con las
  cinco ligas procesadas bien.
- Un equipo recien ascendido aparece en una liga: se crea, con sus jugadores, en la
  siguiente sincronizacion que procese esa liga.
- Cambia la temporada en curso de una liga: se guarda la nueva y se conservan la anterior y
  sus partidos.
- Se pide sincronizar mientras otra sincronizacion esta en curso: la manual se rechaza y la
  programada se omite, en ambos casos informando el motivo.
- La aplicacion estaba apagada el lunes a las 04:00: esa corrida no se recupera.
- Un usuario comun, o una peticion sin credencial, intenta disparar la sincronizacion: se
  rechaza por falta de permiso o de credencial, sin consultar la fuente.
- No hay credencial de la fuente configurada: la aplicacion arranca, advierte y deja la
  sincronizacion deshabilitada; el catalogo responde con lo que tiene guardado.
- La fuente rechaza la credencial configurada: todas las ligas quedan fallidas con ese
  motivo.
- Se repite la sincronizacion con la fuente informando exactamente lo mismo: no se crea
  nada nuevo y los datos existentes cuentan como actualizados.
- Se consulta el catalogo mientras corre una sincronizacion: responde con lo guardado, sin
  esperar a que termine.
- Una base local todavia tiene el dataset ficticio: se reinicia una vez al adoptar esta
  funcionalidad y esos datos no se migran. Al arrancar sobre la base reiniciada, el
  catalogo esta vacio y se sincroniza solo.
- La sincronizacion de arranque falla en las cinco ligas: el catalogo sigue vacio y la
  proxima sincronizacion la da el siguiente arranque, un disparo manual o la corrida del
  lunes, lo que ocurra primero.
- La sincronizacion de arranque falla en algunas ligas: el catalogo ya no esta vacio, asi
  que el siguiente arranque no sincroniza; las ligas faltantes llegan con un disparo
  manual o con la corrida del lunes.
- La aplicacion arranca con el catalogo vacio pero sin credencial: no se sincroniza,
  porque la sincronizacion esta deshabilitada.
- El arranque con el catalogo vacio coincide con el lunes a las 04:00: la corrida
  programada se omite porque ya hay una sincronizacion en curso.
- Un jugador que queda inactivo tiene tokens comprados: el jugador no se borra y su detalle
  se sigue pudiendo consultar; que pasa con esos tokens queda fuera de alcance.
- Se filtra el catalogo por el ultimo equipo de un jugador inactivo: el jugador no aparece,
  aunque conserve ese equipo.
- La paginacion del listado cuenta solo a los jugadores activos: el total de resultados y
  de paginas no incluye a los inactivos.

## Requirements *(mandatory)*

### Functional Requirements

#### Fuente y datos que se guardan

- **FR-001**: El sistema MUST construir y mantener el catalogo de equipos y jugadores de
  las cinco ligas (Premier League, Bundesliga, La Liga, Serie A y Ligue 1) exclusivamente a
  partir de Football-Data.org.
- **FR-002**: El sistema MUST NOT contener ni cargar el dataset ficticio de jugadores y
  equipos. Su carga inicial se elimina, sin dejarla como respaldo.
- **FR-003**: El sistema MUST guardar localmente, de cada una de las cinco ligas, sus
  equipos, los jugadores de sus planteles, su temporada en curso y todos los partidos de
  esa temporada.
- **FR-004**: El sistema MUST importar a todos los jugadores de los planteles que informa la
  fuente, con las unicas excepciones de FR-012.
- **FR-005**: El sistema MUST NOT guardar la tabla de posiciones ni los goleadores, aunque
  la fuente los ofrezca.
- **FR-006**: El sistema MUST reconocer cada temporada, equipo, jugador y partido por el
  identificador que le asigna la fuente. Una sincronizacion posterior MUST actualizar el
  mismo dato guardado y MUST NOT crear un duplicado.
- **FR-007**: El sistema MUST NOT borrar temporadas, equipos, jugadores ni partidos como
  consecuencia de una sincronizacion.

#### Equipos

- **FR-008**: Cada equipo MUST guardar su nombre oficial exacto, tal como lo informa la
  fuente, y su escudo como la direccion de la imagen. El sistema MUST NOT guardar el nombre
  corto ni descargar la imagen del escudo.
- **FR-009**: Cada equipo MUST pertenecer a la ultima liga en la que lo informo la fuente.
  Un equipo que deja de aparecer en las cinco ligas MUST conservar su ultima liga.

#### Jugadores

- **FR-010**: Cada jugador MUST guardar su nombre, su posicion, su equipo (y, a traves de
  el, su liga), su fecha de nacimiento, su nacionalidad y si esta activo o inactivo.
- **FR-011**: El sistema MUST traducir las posiciones de la fuente asi: Goalkeeper a
  arquero, Defence a defensor, Midfield a mediocampista y Offence a delantero.
- **FR-012**: El sistema MUST saltear a todo jugador nuevo que llega sin posicion, sin
  nombre o con una posicion distinta de las cuatro de FR-011, y MUST listarlo en el informe
  con su equipo y con su nombre, si lo tiene.
- **FR-013**: Un jugador ya guardado que llega sin posicion, o con una posicion distinta de
  las cuatro, MUST conservar la que tenia; uno que llega sin nombre MUST conservar el
  nombre que tenia. En ambos casos MUST contar como presente en el plantel.
- **FR-014**: El sistema MUST guardar igual a un jugador que llega sin fecha de nacimiento
  o sin nacionalidad, con ese dato vacio.
- **FR-015**: Si la fuente informa al mismo jugador en dos planteles, el sistema MUST
  dejarlo en uno solo de esos equipos y MUST listar el caso en el informe.

#### Altas, bajas y transferencias

- **FR-016**: Un jugador que pasa de un equipo a otro de las cinco ligas MUST seguir siendo
  el mismo jugador del catalogo, con su nuevo equipo y la liga de ese equipo.
- **FR-017**: Al terminar una sincronizacion completa en la que las cinco ligas se
  procesaron bien, el sistema MUST marcar como inactivo a todo jugador guardado que no
  aparecio en ningun plantel de las cinco ligas. El jugador inactivo MUST conservar su
  ultimo equipo y MUST NOT borrarse.
- **FR-018**: Una sincronizacion de una sola liga, o una completa con al menos una liga
  fallida, MUST NOT inactivar a ningun jugador.
- **FR-019**: Un jugador inactivo que la fuente vuelve a informar en un plantel de las cinco
  ligas MUST quedar activo de nuevo, con el equipo que informa la fuente.
- **FR-020**: Salvo en los casos de FR-013, los datos de los equipos y de los jugadores ya
  guardados MUST actualizarse con lo que informa la ultima sincronizacion que los incluye.

#### Temporadas y partidos

- **FR-021**: Cada liga MUST tener guardada su temporada en curso, con su inicio, su fin y
  su jornada actual.
- **FR-022**: Cada partido de la temporada MUST guardar su fecha y hora, su jornada, su
  estado, su equipo local, su equipo visitante, su resultado final, su resultado del primer
  tiempo y su ganador; estos tres ultimos, cuando la fuente los informa.
- **FR-023**: Cada partido MUST guardarse con el estado que informa la fuente, incluidos
  los postergados, suspendidos, cancelados y adjudicados.
- **FR-024**: Un partido que referencia a un equipo que no esta en el catalogo MUST NOT
  guardarse y MUST listarse en el informe como omitido, sin que la liga se de por fallida.
- **FR-025**: Cuando la fuente informa una temporada en curso distinta de la guardada, el
  sistema MUST guardar la nueva con sus partidos y MUST conservar la anterior y sus
  partidos.

#### Disparo de la sincronizacion

- **FR-026**: El sistema MUST ejecutar automaticamente una sincronizacion completa todos
  los lunes a las 04:00, hora de Argentina (UTC-3).
- **FR-027**: Si la aplicacion no esta encendida a esa hora, el sistema MUST NOT recuperar
  la corrida perdida.
- **FR-028**: Un administrador (rol ADMIN) MUST poder disparar a mano una sincronizacion
  completa o una de una sola de las cinco ligas.
- **FR-029**: El sistema MUST rechazar el disparo manual por falta de permiso cuando lo pide
  un usuario que no es administrador, y por falta de credencial cuando la peticion no trae
  ninguna; en ambos casos, sin consultar la fuente.
- **FR-030**: El sistema MUST rechazar, indicando el valor no admitido, el pedido de
  sincronizar una liga que no es una de las cinco.
- **FR-031**: El sistema MUST ejecutar a lo sumo una sincronizacion a la vez. Si hay una en
  curso, MUST rechazar el disparo manual informando el motivo y MUST omitir la corrida
  programada, dejando el motivo en el registro de la aplicacion.
- **FR-032**: El disparo manual MUST esperar a que la sincronizacion termine y MUST
  devolver su informe, aunque alguna liga, o todas, haya fallado.
- **FR-033**: Al arrancar con el catalogo vacio (sin ningun jugador guardado) y la
  credencial de la fuente configurada, el sistema MUST disparar una sincronizacion
  completa en segundo plano, sin demorar el arranque. Si el catalogo ya tiene datos, MUST
  NOT disparar ninguna. Esta sincronizacion respeta FR-031 como cualquier otra.

#### Tolerancia a fallas de la fuente

- **FR-034**: Cada liga MUST procesarse como un todo: si falla, el sistema MUST NOT guardar
  ningun cambio de esa liga (equipos, jugadores, temporada ni partidos) y MUST conservar lo
  que tenia.
- **FR-035**: La falla de una liga MUST NOT impedir que se procesen las demas.
- **FR-036**: Las consultas del catalogo MUST NOT depender de la fuente: MUST responder con
  lo guardado aunque la fuente no este disponible o haya una sincronizacion en curso.
- **FR-037**: El sistema MUST dar por fallida una liga cuando una consulta a la fuente no
  responde en 30 segundos, y MUST seguir con las demas.
- **FR-038**: Cuando la fuente informa que se excedio el limite de consultas, el sistema
  MUST esperar el tiempo que ella indica y reintentar una sola vez; si vuelve a fallar, MUST
  dar la liga por fallida.
- **FR-039**: El sistema MUST dar por fallida una liga para la que la fuente no informa
  ningun equipo.

#### Credencial de la fuente

- **FR-040**: La credencial de Football-Data.org MUST configurarse por variable de entorno.
  El repositorio MUST NOT contenerla, ni siquiera como valor por defecto.
- **FR-041**: Sin la credencial configurada, la aplicacion MUST arrancar igual, MUST
  registrar una advertencia y MUST dejar la sincronizacion deshabilitada: el disparo manual
  se rechaza informando que esta deshabilitada y la corrida programada no se ejecuta.
- **FR-042**: La credencial MUST NOT aparecer en el registro de la aplicacion, en las
  respuestas ni en los informes.

#### Informe de la sincronizacion

- **FR-043**: Cada sincronizacion MUST producir un informe con el tipo (completa o de una
  liga), el origen (la corrida semanal, el arranque con el catalogo vacio o un disparo
  manual), el inicio y el fin y, por cada liga, si se proceso o fallo (con el motivo) y
  cuantos equipos, jugadores y partidos se crearon, se actualizaron o se omitieron.
- **FR-044**: El informe MUST listar ademas los jugadores omitidos (con su nombre y su
  equipo), los partidos omitidos, los jugadores que la fuente informo en dos planteles y
  los jugadores inactivados y reactivados.
- **FR-045**: El informe MUST quedar en el registro de la aplicacion. El sistema MUST NOT
  guardar un historial de informes.

#### Consulta del catalogo

- **FR-046**: La consulta del catalogo MUST mostrar, ademas de lo que ya mostraba, la fecha
  de nacimiento y la nacionalidad del jugador y el escudo de su equipo.
- **FR-047**: El filtro por equipo MUST comparar contra el nombre oficial exacto del equipo.
- **FR-048**: El resto de la consulta del catalogo MUST NOT cambiar: paginacion, filtros
  por liga y por posicion, rechazo de los valores de filtro no admitidos y respuesta de no
  encontrado para un jugador inexistente.
- **FR-049**: El listado del catalogo MUST mostrar solo jugadores activos, con o sin
  filtros. Un jugador inactivo MUST NOT aparecer en el listado aunque se filtre por su
  ultimo equipo.
- **FR-049a**: El detalle por identificador MUST responder para cualquier jugador guardado,
  activo o inactivo, y MUST indicar si esta activo o inactivo. Un jugador inactivo MUST NOT
  tratarse como no encontrado.
- **FR-050**: Las consultas del catalogo MUST seguir accesibles sin credencial, por la
  excepcion transitoria de FR-023 de la funcionalidad de autenticacion.

### Key Entities

- **Liga**: una de las cinco competiciones admitidas (Premier League, Bundesliga, La Liga,
  Serie A y Ligue 1). Agrupa equipos y temporadas. Es la unidad de la sincronizacion: se
  procesa como un todo y falla como un todo.
- **Temporada**: la edicion de una liga que la fuente informa como en curso. Atributos:
  identificador de la fuente, liga, inicio, fin y jornada actual. Las temporadas anteriores
  se conservan con sus partidos.
- **Equipo**: un club de una de las cinco ligas. Atributos: identificador de la fuente,
  nombre oficial exacto, escudo (direccion de la imagen) y liga, la ultima en la que lo
  informo la fuente. Nunca se borra.
- **Jugador**: una persona de un plantel de las cinco ligas. Atributos: identificador de la
  fuente, nombre, posicion (arquero, defensor, mediocampista o delantero), equipo, fecha de
  nacimiento, nacionalidad y estado (activo o inactivo). Su liga es la de su equipo. Nunca
  se borra: si deja las cinco ligas queda inactivo con su ultimo equipo, fuera del listado
  del catalogo pero consultable por su identificador.
- **Partido**: un encuentro de una temporada. Atributos: identificador de la fuente,
  temporada, fecha y hora, jornada, estado, equipo local, equipo visitante, resultado
  final, resultado del primer tiempo y ganador (local, visitante o empate). Nunca se borra.
- **Sincronizacion**: una corrida que trae datos de la fuente. Es completa (las cinco
  ligas) o de una sola liga. Su origen es la corrida semanal, el arranque con el catalogo
  vacio o un disparo manual; solo la manual puede ser de una sola liga. Hay a lo sumo una a
  la vez.
- **Informe de sincronizacion**: el resumen de una corrida, descripto en FR-043 y FR-044.
  Va al registro de la aplicacion y, si el disparo fue manual, a la respuesta; no se
  guarda.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Despues de una sincronizacion completa con la fuente respondiendo con
  normalidad, el catalogo contiene el 100% de los equipos y de los jugadores con posicion
  que la fuente informa para las cinco ligas (al 2026-10-06: 96 equipos y 2.634 jugadores,
  es decir, 2.649 menos los 15 sin posicion).
- **SC-002**: Ningun jugador ni equipo del dataset ficticio existe en ningun entorno.
- **SC-003**: Un disparo manual de la sincronizacion completa devuelve su informe en menos
  de 2 minutos con la fuente respondiendo con normalidad.
- **SC-004**: Despues de una sincronizacion completa, cada liga tiene su temporada en curso
  y el 100% de los partidos de esa temporada que informa la fuente, salvo los omitidos que
  lista el informe (al 2026-10-06, 380 partidos en la Premier League).
- **SC-005**: Repetir la sincronizacion con la fuente informando lo mismo deja exactamente
  la misma cantidad de temporadas, equipos, jugadores y partidos.
- **SC-006**: La cantidad de temporadas, equipos, jugadores y partidos guardados nunca
  disminuye de una sincronizacion a la siguiente.
- **SC-007**: Con la fuente caida, el 100% de las consultas del catalogo responden igual
  que con la fuente disponible.
- **SC-008**: Cuando una liga falla, sus datos guardados quedan identicos a los de antes de
  la sincronizacion y las demas ligas se procesan.
- **SC-009**: Solo las sincronizaciones completas con las cinco ligas procesadas bien
  inactivan jugadores; cualquier otra inactiva a cero.
- **SC-010**: El 100% de los intentos de disparo manual de quien no es administrador se
  rechazan sin consultar la fuente.
- **SC-011**: La credencial de la fuente aparece cero veces en el repositorio, en el
  registro de la aplicacion, en las respuestas y en los informes.
- **SC-012**: Sin la credencial configurada, la aplicacion arranca, deja la advertencia y el
  catalogo responde con lo guardado, en el 100% de los arranques.
- **SC-013**: Con la aplicacion encendida, cada lunes a las 04:00 hora de Argentina arranca
  exactamente una sincronizacion completa sin intervencion de nadie.
- **SC-014**: En ningun momento hay dos sincronizaciones en curso a la vez.
- **SC-015**: El 100% de los jugadores omitidos, los partidos omitidos, los jugadores
  informados en dos planteles y los jugadores inactivados y reactivados figuran en el
  informe de la sincronizacion en la que ocurrieron.
- **SC-016**: El 100% de los jugadores del catalogo muestra el escudo de su equipo, y su
  fecha de nacimiento y su nacionalidad cuando la fuente las informa.
- **SC-017**: El 100% de los jugadores inactivos queda fuera del listado del catalogo, con
  o sin filtros, y el 100% de ellos se puede consultar por su identificador con la
  indicacion de que esta inactivo.
- **SC-018**: Con la credencial configurada y la fuente respondiendo con normalidad, una
  aplicacion que arranca con el catalogo vacio lo tiene construido en menos de 2 minutos,
  sin intervencion de nadie; una que arranca con datos no consulta la fuente.

## Assumptions

- Las 04:00 de la corrida programada son hora de Argentina (UTC-3).
- El administrador que dispara la sincronizacion es la cuenta que crea el alta de arranque
  de la funcionalidad de autenticacion (su FR-036). Si esa cuenta no existe, la
  sincronizacion corre unicamente en forma automatica: la corrida semanal y la de arranque
  con el catalogo vacio.
- El entorno de tests no configura la credencial de la fuente, asi que no dispara la
  sincronizacion de arranque ni consulta la fuente real, salvo que un test la configure a
  proposito.
- Los 2 minutos de SC-003 suponen la fuente respondiendo con normalidad y el limite del plan
  gratis, 10 consultas por minuto. La sincronizacion respeta ese limite; si igual lo
  excede, aplica FR-038.
- La fuente publica los resultados con demora, no en vivo. Como la sincronizacion corre una
  vez por semana, un partido recien jugado puede figurar sin resultado hasta la corrida
  siguiente o hasta un disparo manual.
- En los conteos del informe, un dato que ya existia y vuelve a llegar de la fuente cuenta
  como actualizado, haya cambiado o no.
- Cuando la fuente informa al mismo jugador en dos planteles, cual de los dos equipos queda
  lo resuelve el plan; el spec fija que quede en uno solo y que el caso se informe.
- Una consulta del catalogo hecha durante una sincronizacion ve lo que habia antes de la
  liga en proceso o lo que quedo despues, nunca una liga a medio actualizar.
- El escudo se guarda como la direccion de la imagen que informa la fuente; la imagen no se
  descarga ni se verifica.
- Las bases locales que tienen el dataset ficticio se reinician una vez al adoptar esta
  funcionalidad. Esos datos no se migran.
- Los datos que el plan gratis no ofrece (alineaciones, autores de los goles, tarjetas,
  cambios, minutos jugados, estadisticas del partido, cuotas y director tecnico) van a
  salir de WhoScored en una funcionalidad posterior.
- Los tests que dependen del dataset ficticio o de la forma anterior del modelo del
  catalogo se pueden modificar o borrar, con el si explicito que dio Lucas el 2026-10-06
  (Principio IV de la constitucion). Un comportamiento que no cambia conserva su test.
- La advertencia por falta de credencial y los informes quedan en el registro de la
  aplicacion; el unico informe que llega a un cliente es el que devuelve el disparo manual.
- Los mensajes de error del sistema se redactan en espanol, segun el Principio VII de la
  constitucion.

## Dependencies

- **Autenticacion (001)**: el disparo manual exige el rol ADMIN. Es el primer recurso del
  sistema que distingue por rol; el administrador es el que da de alta su FR-036. Las
  consultas del catalogo siguen publicas por su FR-023.
- **Catalogo de jugadores (002)**: esta funcionalidad le cambia la fuente de datos, el
  filtro por equipo (que compara contra el nombre oficial) y los datos que muestra (fecha
  de nacimiento, nacionalidad, escudo y estado en el detalle) y el listado, que deja afuera
  a los jugadores inactivos. Quedan superados su RF-001 (catalogo inicial de 50 a 60
  jugadores), junto con el CE-001 que lo mide, y su RF-016 en cuanto excluia la edad y la
  nacionalidad. De su RF-002 se mantiene que la consulta no depende de servicios externos,
  pero el catalogo ya no esta disponible desde el primer inicio sino desde que termina la
  primera sincronizacion, que corre sola al arrancar con el catalogo vacio (FR-033). El
  resto de 002 sigue vigente.
- **Monolito por capas (003)**: define la estructura en la que se implementa.
- **Constitucion 2.2.0**: agrega la capa para integrar APIs externas y el arbol de carpetas
  flexible con el aval de un desarrollador. Ya esta aplicada; la necesita el plan, no este
  spec.
- **Cotizacion (Entrega 2, posterior)**: consume las temporadas, los partidos, la posicion
  y el estado de los jugadores que guarda esta funcionalidad.
- **WhoScored (posterior)**: usa el catalogo (nombre, equipo y fecha de nacimiento del
  jugador) y los partidos de la semana para cruzar a cada jugador con su rendimiento.

## Fuera de Alcance

No forman parte de esta funcionalidad:

- La integracion con WhoScored y el rendimiento de cada jugador.
- El calculo de cotizaciones, el ranking y el mercado de tokens.
- Las alineaciones, los autores de los goles, las tarjetas, los cambios, los minutos
  jugados y las estadisticas del partido: el plan gratis de la fuente no los ofrece.
- Guardar la tabla de posiciones y los goleadores.
- Las temporadas anteriores a la que esta en curso en la primera sincronizacion.
- Otras competiciones, como las copas nacionales y los torneos europeos.
- Recursos para consultar temporadas y partidos: en esta funcionalidad solo se guardan.
- El canje de los tokens de jugadores inactivos.
- El historial persistido de los informes de sincronizacion.
- Los datos en vivo.
- La capa de cache para consultas frecuentes.
- Retirar la excepcion que deja publicas las consultas del catalogo.
