# Feature Specification: Scraping de WhoScored

**Feature Branch**: `feature/scraping-whoscored`

**Created**: 2026-10-13

**Status**: Draft

**Input**: User description: "Scraping de WhoScored: el sistema obtiene de WhoScored las estadisticas de cada jugador en cada partido terminado de las cinco ligas (minutos, goles, asistencias, tiros, pases clave, regates, quites, tarjetas, rating y otras), las cruza con los jugadores y partidos que ya trae Football-Data y las guarda para que la cotizacion de la Entrega 2 las use. Para no tardar cuarenta minutos cada vez que se levanta una base nueva, el repositorio incluye un snapshot de esas estadisticas y la sincronizacion solo scrapea lo que falta. Al arrancar, la aplicacion se pone al dia sola si su ultima sincronizacion completa tiene mas de siete dias. Si WhoScored bloquea o falla, el sistema sigue funcionando con lo que tiene guardado."

## Clarifications

### Session 2026-10-13

Decisiones del equipo tomadas antes de escribir el spec, a partir de lo verificado contra
WhoScored ese dia (temporada 2026/27):

- Q: Que metricas se guardan de cada jugador en cada partido? -> A: Si fue titular, los
  minutos jugados, los goles, las asistencias, los tiros, los tiros al arco, los pases
  clave, los pases, los pases precisos, los regates exitosos, los quites, las
  intercepciones, los despejes, los duelos aereos ganados, las faltas cometidas, las
  tarjetas amarillas, las tarjetas rojas, el rating y si fue la figura del partido. No se
  guardan las 42 metricas que informa WhoScored.
- Q: De que partidos se traen estadisticas? -> A: Solo de los partidos de liga de las
  cinco ligas que ya guarda la integracion con Football-Data. Ni copas, ni torneos
  europeos, ni selecciones.
- Q: Que partidos procesa cada sincronizacion? -> A: Todos los partidos terminados que
  todavia no tienen estadisticas. Asi la primera trae la temporada desde el inicio, y una
  corrida perdida se recupera en la siguiente.
- Q: Cuando corre la sincronizacion de WhoScored? -> A: Encadenada despues de la de
  Football-Data, en la corrida del lunes a las 04:00 y al arrancar. Ademas tiene su propio
  disparo manual, solo para un administrador (rol ADMIN), para las cinco ligas o para una
  sola. Los disparos manuales de las dos fuentes no se encadenan.
- Q: Que pasa si falla la sincronizacion de Football-Data? -> A: La de WhoScored corre
  igual, con lo que ya esta guardado.
- Q: Que pasa con un jugador de WhoScored que no se puede cruzar con uno del catalogo? ->
  A: Se saltea y va al informe. No se crean jugadores nuevos ni hay cruce manual.
- Q: Se vuelve a procesar un partido que ya tiene estadisticas? -> A: No, aunque
  WhoScored corrija algo despues.
- Q: Se pueden consultar las estadisticas? -> A: No en esta funcionalidad: solo se
  guardan. No hay ninguna consulta nueva para verlas.
- Q: Que pasa si un equipo no se puede cruzar? -> A: Su liga sigue; los partidos de ese
  equipo se omiten y van al informe.
- Q: Como se evita scrapear toda la temporada en cada base nueva? -> A: El repositorio
  incluye un snapshot: un archivo de texto versionado con las estadisticas ya obtenidas y
  los identificadores de WhoScored de equipos, jugadores y partidos, ubicados por los
  identificadores de Football-Data. No se sube la base local: tiene usuarios con sus
  contrasenas resguardadas y sus claves de API, es binaria, depende de la version del
  motor de base de datos y del esquema, y diverge entre los desarrolladores.
- Q: Cuando se pone al dia sola la aplicacion? -> A: Al arrancar, si la ultima
  sincronizacion completa de Football-Data tiene mas de 7 dias, o si nunca hubo una, se
  corren las dos sincronizaciones en segundo plano: primero la de Football-Data y despues
  la de WhoScored. Reemplaza al FR-033 de la 005, que solo sincronizaba con el catalogo
  vacio, y al FR-027 de la 005, que no recuperaba la corrida perdida.
- Q: Se pueden tocar los tests de la 005? -> A: Si. Lucas dio el si explicito el
  2026-10-13 para modificar los tests de la 005 afectados por los cambios que esta
  funcionalidad le hace (FR-027, FR-031, FR-033 y FR-043), segun el Principio IV de la
  constitucion. Un comportamiento de la 005 que no cambia conserva su test.

Decisiones abiertas que quedaron al escribir el spec, resueltas por el equipo el mismo
dia:

- Q: Que pasa con un equipo cuyo nombre normalizado no coincide con el del catalogo, como
  "Man Utd" en WhoScored y "Manchester United FC" en Football-Data? -> A: Se cruza igual
  cuando sus partidos de liga de la temporada coinciden en fecha, localia y rivales con
  los de un unico equipo del catalogo de su liga. Se descarto cruzar solo por nombre,
  porque varios equipos grandes quedarian sin estadisticas toda la temporada, y comparar
  contra el nombre corto de Football-Data, porque obligaba a cambiar el FR-008 de la 005
  y seguia fallando en algunos casos.
- Q: Que pasa con un jugador que cambio de equipo dentro de las cinco ligas durante la
  temporada, en los partidos que jugo con el anterior? -> A: Si no hay ningun candidato en
  el equipo del partido, se lo busca en todo el catalogo con el mismo nombre normalizado y
  la misma edad, y se cruza solo si hay uno unico. Se descartaron saltearlo y procesar los
  partidos del mas reciente al mas antiguo, que falla si el jugador todavia no jugo con su
  equipo nuevo.
- Q: La de WhoScored se pone al dia sola al arrancar por su propia ultima sincronizacion
  completa exitosa? -> A: Si. Cada fuente tiene su vencimiento: si la de Football-Data
  tiene mas de 7 dias, o nunca hubo una, corre la cadena; si no, pero la de WhoScored si,
  corre solo la de WhoScored. Asi un corte por bloqueo se recupera en un arranque
  posterior. Amplia la decision sobre la puesta al dia, que solo miraba la de
  Football-Data.
- Q: Los minutos jugados cuentan el tiempo agregado? -> A: Si. Se miden sobre el tiempo
  transcurrido desde el inicio del partido, con lo agregado en los dos tiempos. Reemplaza
  al reloj de 90 minutos sin agregado del primer borrador.

### Session 2026-10-15

Decisiones del equipo despues de la primera sincronizacion completa contra el sitio real
(293 partidos de las cinco ligas):

- Q: Que pasa con un jugador cuyo nombre se escribe distinto en Football-Data y en
  WhoScored, como "Sivera" y "Antonio Sivera", "Kang-in Lee" y "Lee Kang-In" o "Alejandro
  Baena" y "Alex Baena"? -> A: Si la regla estricta no encuentra a nadie, se lo busca en
  su equipo con seis niveles de comparacion de nombres, cada vez mas flexibles, y en cada
  nivel solo cuenta un candidato unico de la misma edad (FR-019b). Con la regla estricta
  sola, esa corrida cruzo el 87,3% de los jugadores que entraron a la cancha, por debajo
  del 95% de SC-002: 1176 apariciones de 301 jugadores no tenian el mismo nombre en las
  dos fuentes, aunque estaban en el equipo correcto del catalogo. Se descarto cruzar por
  descarte (el unico jugador sin cruzar del equipo con esa edad), porque muchos de los que
  quedan no estan en el catalogo y se cruzarian con otro jugador.
- Q: Que edad informa WhoScored de cada jugador? -> A: La del dia en que se consulta la
  pagina, no la del dia del partido (verificado el 2026-10-15). Como la temporada dura
  menos de un ano, la edad que surge de la fecha de nacimiento el dia del partido es esa
  o una menos, y se aceptan las dos (FR-019c).
- Q: Que pasa con un cruce que solo coincide en el nombre de pila? -> A: No alcanza. Con los
  niveles, la medicion siguiente cruzo el 97,5% de los jugadores que entraron, pero de doce
  cruces dudosos verificados contra la fecha de nacimiento de la pagina de cada jugador en
  WhoScored, uno estaba mal: "Mathis Saka" con "Mathis Amougou", dos jugadores distintos del
  mismo equipo que solo comparten el nombre de pila y la edad. Los otros once eran el mismo
  jugador con otro nombre en cada fuente ("Diogo Sousa" es Diogo Lobao de Sousa). El nivel 6
  deja de cruzar por el nombre de pila solo, y un jugador que los niveles no resuelven se
  confirma con la fecha de nacimiento exacta de su pagina en WhoScored (FR-019d). Se
  descarto quitar esos cruces sin confirmarlos, porque se perdian diez cruces correctos.
- Q: Alcanzan el nombre y la edad para cruzar a un jugador con uno de otro equipo (FR-019a)?
  -> A: No. Revisando los 97 jugadores que esa medicion dejo sin cruzar, el "Vitinha" del
  Paris Saint-Germain quedo sin cruzar en seis partidos aunque el catalogo lo tiene: otro
  "Vitinha", el del Genoa, de la misma edad, se proceso antes y se cruzo con el del Paris
  Saint-Germain por FR-019a. Los dos tienen 26 anos, pero nacieron el 15/03/2000 y el
  13/02/2000, y el catalogo tiene al del Genoa como "Vitor Oliveira" (la medicion siguiente
  lo cruzo con el por la fecha). Un candidato de otro equipo se confirma con la fecha de
  nacimiento exacta de la pagina del jugador, y mientras tanto el jugador no pasa a los
  niveles de su equipo. Reemplaza la decision de cruzar al transferido solo por nombre y edad.
- Q: Que pasa con un jugador que cambio de equipo y figura con otro nombre en cada fuente?
  -> A: Se cruza con el de otro equipo que tiene exactamente su fecha de nacimiento si los
  nombres coinciden por los niveles 1 a 3 (FR-019d). "Jose Maria Gimenez" jugo para el
  Atletico de Madrid antes de pasar al Deportivo de La Coruna, donde el catalogo lo tiene como
  "Jose Gimenez"; los dos nacieron el 20/01/1995.
- Q: Que pasa con un jugador cuya fecha de nacimiento en el catalogo no es la exacta? -> A: Se
  cruza con el de su equipo nacido el mismo ano si su nombre del catalogo esta dentro del nombre
  completo de su pagina de WhoScored (FR-019d). "Rafita" es "Rafael Garrido Hierro", nacido el
  09/11/2004; el catalogo lo tiene como "Rafa Garrido", con el 01/01/2004. En el catalogo hay 19
  jugadores nacidos un 1 de enero, cuando por azar se esperan unos 7. De los 97 sin cruzar, 83
  no tienen a nadie con un nombre parecido en el catalogo: salvo apodos como "Rafita", no estan
  en el plantel de Football-Data y siguen sin poder cruzarse (FR-021).
- Q: Alcanza la fecha de nacimiento exacta para cruzar con un jugador del mismo equipo cuyo
  nombre no tiene nada que ver? -> A: No. Con la confirmacion por la pagina, la medicion
  siguiente cruzo el 97,8% de los jugadores que entraron, pero cruzo a "Riccardo Ciervo" con
  "Ignace Van Der Brempt": dos jugadores del Sassuolo nacidos el mismo dia (01/04/2002). El
  catalogo tiene a Ciervo en el Cagliari, adonde paso despues. El paso 1 de FR-019d exige
  ademas alguna relacion de nombre: una palabra en comun, un nivel de FR-019b o el nombre del
  catalogo dentro del nombre completo. Con eso, la medicion siguiente (97,7%) cruzo a Ciervo
  con el del Cagliari por el paso 2 y a Van Der Brempt con el suyo. Se pierden dos cruces
  probablemente correctos de apodos sin ninguna relacion ("Youssef Enriquez" con "Yusi" y
  "Yeremay Hernandez" con "Peke"), pero SC-003 no admite ningun cruce equivocado.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Estadisticas de los partidos terminados (Priority: P1)

Un administrador dispara la sincronizacion de WhoScored. El sistema busca los partidos
terminados de las cinco ligas que todavia no tienen estadisticas, cruza los equipos y los
jugadores de WhoScored con los del catalogo y guarda, por cada jugador que entro a la
cancha, sus metricas en ese partido. Al terminar devuelve un informe con lo que proceso y
con lo que no pudo cruzar. Las estadisticas no se muestran: quedan guardadas para la
cotizacion de la Entrega 2.

**Why this priority**: Es lo que pide el enunciado: el rendimiento de cada jugador sale
de WhoScored. Los escenarios de evaluacion (la cotizacion de un jugador en distintas
fechas y los usuarios que compran al inicio del campeonato) necesitan las estadisticas
desde el inicio de la temporada. Sin esto la cotizacion no tiene sobre que calcular.

**Independent Test**: Se prueba con un catalogo ya sincronizado con Football-Data y con
partidos terminados sin estadisticas, disparando la sincronizacion de WhoScored como
administrador contra un WhoScored simulado, y verificando que cada partido terminado
queda con las metricas de cada jugador que entro a la cancha, que los partidos no
terminados no se tocan y que los jugadores sin cruzar figuran en el informe. Entrega
valor por si sola: la cotizacion ya tiene datos de rendimiento.

**Acceptance Scenarios**:

1. **Given** un catalogo sincronizado y un partido terminado de la Premier League sin
   estadisticas, **When** un administrador dispara la sincronizacion de WhoScored y
   WhoScored responde con normalidad, **Then** el partido queda con las estadisticas de
   cada jugador de los dos equipos que entro a la cancha **And** el informe lo cuenta como
   procesado por scraping.
2. **Given** un partido procesado, **When** se revisan las estadisticas de uno de sus
   jugadores, **Then** incluyen si fue titular, los minutos jugados, los goles, las
   asistencias, los tiros, los tiros al arco, los pases clave, los pases, los pases
   precisos, los regates exitosos, los quites, las intercepciones, los despejes, los
   duelos aereos ganados, las faltas cometidas, las tarjetas amarillas, las tarjetas
   rojas, el rating y si fue la figura del partido.
3. **Given** un suplente que no entro a la cancha, **When** se procesa el partido,
   **Then** no tiene estadisticas en ese partido.
4. **Given** un partido con 2 minutos agregados en el primer tiempo y 4 en el segundo,
   **When** se procesa, **Then** un titular que juega el partido completo queda con 96
   minutos jugados; **And** un titular reemplazado en el minuto 70 del reloj queda con 72
   y el suplente que entra en su lugar, con 24.
5. **Given** ese mismo partido y un titular expulsado en el minuto 60 del reloj, **When**
   se procesa, **Then** queda con 62 minutos jugados y una tarjeta roja.
6. **Given** un gol que no es en contra y cuyo pase previo WhoScored marca como
   asistencia, **When** se procesa el partido, **Then** el autor del gol suma un gol
   **And** el autor del pase suma una asistencia.
7. **Given** un gol en contra, **When** se procesa el partido, **Then** no suma un gol a
   su autor ni una asistencia a nadie.
8. **Given** un jugador de WhoScored que no se puede cruzar con ninguno del catalogo,
   **When** se procesa su partido, **Then** el partido se guarda con los demas jugadores
   **And** el informe lista a ese jugador con su nombre, su equipo y el partido.
9. **Given** un jugador que en agosto jugo para el Brentford y que el catalogo ya tiene en
   el Tottenham, **When** se procesa un partido de agosto del Brentford, **Then** no hay
   candidato en el Brentford **And** se lo cruza con el jugador del Tottenham porque es el
   unico del catalogo con el mismo nombre normalizado y la misma edad **And** su pagina de
   jugador en WhoScored trae su misma fecha de nacimiento.
10. **Given** un jugador cruzado en un partido anterior, **When** aparece en otro partido,
    **Then** se lo reconoce por su identificador de WhoScored, sin volver a comparar
    nombres.
11. **Given** el equipo "Man Utd" de WhoScored, cuyo nombre normalizado no coincide con el
    de ningun equipo del catalogo, y cuyos partidos de liga coinciden en fecha, localia y
    rivales con los del "Manchester United FC", **When** se sincroniza la Premier League,
    **Then** queda cruzado con el "Manchester United FC".
12. **Given** un equipo ya cruzado con WhoScored, **When** corre otra sincronizacion,
    **Then** no se vuelve a cruzar: se lo reconoce por su identificador de WhoScored.
13. **Given** un equipo que no se puede cruzar, **When** se sincroniza su liga, **Then**
    los partidos de ese equipo se omiten y figuran en el informe con ese motivo **And**
    los demas partidos de la liga se procesan.
14. **Given** un partido ya procesado, **When** corre otra sincronizacion, **Then** su
    pagina no se vuelve a pedir a WhoScored **And** sus estadisticas no cambian.
15. **Given** un partido programado, postergado, suspendido, cancelado o adjudicado,
    **When** se sincroniza, **Then** no se procesa; **And** si mas adelante figura como
    terminado, se procesa en la primera sincronizacion posterior.
16. **Given** un administrador, **When** dispara la sincronizacion de WhoScored de una
    sola de las cinco ligas, **Then** se procesan solo partidos de esa liga.
17. **Given** un administrador, **When** pide sincronizar con WhoScored una liga que no es
    una de las cinco, **Then** el sistema rechaza el pedido indicando el valor no admitido
    **And** no consulta WhoScored.
18. **Given** un usuario con rol de usuario comun, o una peticion sin credencial, **When**
    intenta disparar la sincronizacion de WhoScored, **Then** el sistema la rechaza por
    falta de permiso o de credencial **And** no consulta WhoScored.
19. **Given** una sincronizacion de WhoScored disparada a mano, **When** termina, **Then**
    el administrador recibe el informe con el tipo, el origen, el inicio y el fin y, por
    cada liga, los partidos procesados (del snapshot y scrapeados), los omitidos con su
    motivo y los equipos y jugadores sin cruzar.
20. **Given** un disparo manual de cualquiera de las dos fuentes, **When** termina,
    **Then** no se dispara la sincronizacion de la otra.
21. **Given** un jugador que WhoScored informa como "Antonio Sivera" y que el catalogo
    tiene como "Sivera" en el mismo equipo y con la misma edad, **When** se procesa su
    partido, **Then** se lo cruza con ese jugador del catalogo.
22. **Given** un jugador de WhoScored con el mismo apellido que dos jugadores sin cruzar
    de su equipo y de su misma edad, **When** se procesa su partido, **Then** no se cruza
    **And** figura en el informe como ambiguo.
23. **Given** un jugador que cumplio anos despues de un partido que se procesa mas tarde,
    **When** WhoScored informa su edad nueva, **Then** se lo reconoce igual por su edad.
24. **Given** un jugador cuyo nombre en WhoScored no coincide con ninguno de su equipo en el
    catalogo, **When** su pagina de jugador en WhoScored trae la misma fecha de nacimiento que
    un unico jugador sin cruzar de su equipo con el que tiene alguna relacion de nombre, como
    "Moustapha Mbow" y "Mamadou Mbow", **Then** se lo cruza con ese jugador.
25. **Given** dos jugadores del mismo equipo que solo comparten el nombre de pila y la edad,
    **When** sus fechas de nacimiento son distintas, **Then** no se cruzan **And** el de
    WhoScored figura en el informe.
26. **Given** la pagina de un jugador que WhoScored no entrega ni en el reintento, **When**
    se procesa su partido, **Then** el jugador queda sin cruzar **And** la corrida sigue.
27. **Given** dos jugadores de WhoScored de equipos distintos con el mismo nombre y la misma
    edad, y solo uno de ellos en el catalogo, **When** se procesa un partido del que no esta,
    **Then** no se cruza con el del catalogo, porque su fecha de nacimiento es otra, **And**
    tampoco con un parecido de su equipo **And** figura en el informe como no confirmado.
28. **Given** un jugador que cambio de equipo y figura con otro nombre en cada fuente,
    como "Jose Maria Gimenez" y "Jose Gimenez", **When** su pagina de jugador trae la misma
    fecha de nacimiento que el del catalogo, **Then** se lo cruza con el.
29. **Given** un jugador cuya fecha de nacimiento en el catalogo no es la exacta, **When**
    su pagina de jugador trae un nombre completo que contiene su nombre del catalogo y el mismo
    ano de nacimiento, **Then** se lo cruza con el jugador de su equipo.
30. **Given** un jugador de WhoScored que no esta en el catalogo y un companero de su equipo,
    sin ninguna relacion de nombre, nacido el mismo dia, **When** se procesa su partido,
    **Then** no se cruza con el companero **And** figura en el informe.

---

### User Story 2 - Arranque rapido con el snapshot (Priority: P2)

Ponerse al dia desde el inicio de la temporada son unas 430 paginas de WhoScored y cerca
de 47 minutos. Para no repetirlo en cada base nueva, el repositorio incluye un snapshot con
las estadisticas ya obtenidas y los cruces con WhoScored. La sincronizacion de WhoScored
completa primero desde el snapshot los partidos pendientes que este contiene y despues
scrapea solo lo que sigue faltando. Un desarrollador regenera el snapshot desde su base
local y lo versiona.

**Why this priority**: Los dos desarrolladores levantan bases nuevas seguido, y cada pedido
que se evita a WhoScored baja el riesgo de bloqueo y el volumen que se le pide a un sitio
cuyos terminos no permiten el scraping. No es P1 porque las estadisticas se pueden obtener
sin el snapshot, aunque tarden.

**Independent Test**: Se prueba con una base vacia y un snapshot que cubre los partidos
hasta una fecha dada, verificando que despues de la sincronizacion esos partidos tienen
las estadisticas del snapshot sin que se haya pedido su pagina a WhoScored y que solo se
scrapean los posteriores. Tambien regenerando el snapshot desde una base y revisando su
contenido y su diferencia con la version anterior.

**Acceptance Scenarios**:

1. **Given** una base vacia, un snapshot con las estadisticas de los partidos hasta la
   fecha 7 y partidos terminados hasta la fecha 8, **When** la aplicacion arranca y corren
   las dos sincronizaciones, **Then** los partidos hasta la fecha 7 quedan con las
   estadisticas del snapshot sin pedir sus paginas a WhoScored **And** solo se scrapean
   los de la fecha 8 **And** el informe distingue cuantos partidos se cargaron del
   snapshot y cuantos se scrapearon.
2. **Given** un snapshot que trae el cruce de un equipo, de un jugador y de un partido,
   **When** se carga, **Then** esos cruces quedan guardados **And** las sincronizaciones
   siguientes los usan sin volver a comparar nombres.
3. **Given** WhoScored bloqueando desde el primer pedido, **When** corre la sincronizacion
   con un snapshot que cubre partidos pendientes, **Then** esos partidos se cargan igual
   desde el snapshot **And** la corrida se corta recien al scrapear lo que falta.
4. **Given** un snapshot con entradas de un partido, un equipo o un jugador que no estan
   en el catalogo, **When** se carga, **Then** esas entradas se saltean y figuran en el
   informe **And** el resto del snapshot se carga.
5. **Given** que el snapshot falta o no se puede leer, **When** corre la sincronizacion de
   WhoScored, **Then** la aplicacion sigue funcionando **And** queda una advertencia en el
   registro de la aplicacion **And** se scrapea todo lo pendiente.
6. **Given** un partido que ya tiene estadisticas en la base y que tambien esta en el
   snapshot, **When** corre la sincronizacion, **Then** conserva las que tenia.
7. **Given** una base local con estadisticas, **When** un desarrollador regenera el
   snapshot, **Then** el archivo contiene las estadisticas y los identificadores de
   WhoScored de los equipos, los jugadores y los partidos, ubicados por los
   identificadores de Football-Data **And** no contiene usuarios, contrasenas, claves de
   API, operaciones ni ningun otro dato.
8. **Given** un snapshot recien regenerado, **When** se lo vuelve a regenerar sin cambios
   en la base, **Then** el archivo queda identico.
9. **Given** un snapshot vigente y una base que proceso una fecha nueva, **When** se
   regenera, **Then** la diferencia con la version anterior son solo las entradas de esos
   partidos y los cruces nuevos.
10. **Given** un snapshot vigente con entradas que la base local no tiene, **When** se
    regenera, **Then** el snapshot nuevo las conserva sin cambios.
11. **Given** la aplicacion en ejecucion, **When** se revisan sus recursos, **Then**
    ninguno permite regenerar el snapshot: se regenera con un comando de desarrollo.

---

### User Story 3 - Ponerse al dia solo (Priority: P3)

Los desarrolladores casi nunca tienen la aplicacion encendida un lunes a las 04:00. Por
eso, al arrancar, si la ultima sincronizacion completa exitosa de Football-Data tiene mas
de 7 dias, o si nunca hubo una, la aplicacion corre en segundo plano las dos
sincronizaciones, primero la de Football-Data y despues la de WhoScored, sin demorar el
arranque. Si la de Football-Data esta al dia pero la de WhoScored no, por ejemplo porque
la ultima se corto por bloqueo, corre solo la de WhoScored. La corrida del lunes a las
04:00 tambien encadena las dos.

**Why this priority**: Sin esto los datos dependen de que alguien se acuerde de disparar
las dos sincronizaciones. Es P3 porque P1 y P2 ya permiten tener los datos a mano.

**Independent Test**: Se prueba arrancando con la ultima sincronizacion completa exitosa
de cada fuente de hace mas de 7 dias, de menos de 7 dias y sin ninguna, y verificando en
cada caso que sincronizaciones corren en segundo plano y en que orden; llevando el reloj
al lunes a las 04:00; y haciendo fallar la de Football-Data para verificar que la de
WhoScored corre igual.

**Acceptance Scenarios**:

1. **Given** una base vacia y la credencial de Football-Data configurada, **When** la
   aplicacion arranca, **Then** termina de arrancar sin esperar a ninguna fuente **And**
   en segundo plano corre la sincronizacion completa de Football-Data y, al terminar, la
   completa de WhoScored **And** los dos informes quedan en el registro de la aplicacion
   con el origen "puesta al dia al arrancar".
2. **Given** la ultima sincronizacion completa exitosa de Football-Data de hace 8 dias,
   **When** la aplicacion arranca, **Then** se ponen al dia las dos fuentes, en ese orden.
3. **Given** la ultima sincronizacion completa exitosa de Football-Data de hace 3 dias y
   la de WhoScored de hace 2, **When** la aplicacion arranca, **Then** no se dispara
   ninguna sincronizacion.
4. **Given** la ultima sincronizacion completa exitosa de Football-Data de hace 3 dias y
   la de WhoScored de hace 9, porque las siguientes se cortaron por bloqueo, **When** la
   aplicacion arranca, **Then** corre en segundo plano solo la sincronizacion completa de
   WhoScored, con el origen "puesta al dia al arrancar".
5. **Given** la aplicacion apagada el lunes a las 04:00 y la ultima sincronizacion
   completa exitosa de Football-Data de hace 9 dias, **When** la aplicacion arranca el
   miercoles, **Then** se ponen al dia las dos fuentes: la corrida perdida se recupera.
6. **Given** la aplicacion encendida, **When** llega el lunes a las 04:00 hora de
   Argentina, **Then** corre la sincronizacion completa de Football-Data y, al terminar,
   la completa de WhoScored, con el origen "corrida semanal".
7. **Given** una cadena en la que la sincronizacion de Football-Data falla en alguna liga
   o en todas, **When** termina, **Then** la de WhoScored corre igual con lo guardado
   **And** el momento de la ultima sincronizacion completa exitosa de Football-Data no
   cambia.
8. **Given** un entorno sin la credencial de Football-Data y la ultima sincronizacion
   completa exitosa de hace mas de 7 dias, o ninguna, **When** la aplicacion arranca,
   **Then** la de Football-Data no corre, por estar deshabilitada **And** la de WhoScored
   corre igual sobre lo guardado.
9. **Given** una base creada con la 005 que tiene datos pero ningun registro de la ultima
   sincronizacion completa de ninguna fuente, **When** la aplicacion arranca por primera
   vez con esta funcionalidad, **Then** se considera que nunca hubo una **And** se ponen
   al dia las dos fuentes.
10. **Given** una cadena en curso, en cualquiera de sus dos pasos o entre ellos, **When**
    un administrador dispara a mano cualquiera de las dos sincronizaciones, **Then** el
    sistema la rechaza informando que ya hay una en curso.
11. **Given** una cadena en curso, **When** llega la hora de la corrida programada,
    **Then** esa corrida se omite **And** el motivo queda en el registro de la aplicacion.
12. **Given** una sincronizacion completa de Football-Data disparada a mano con las cinco
    ligas procesadas bien, **When** la aplicacion arranca dos dias despues, **Then** no se
    pone al dia la de Football-Data: la manual cuenta como ultima sincronizacion completa
    exitosa.
13. **Given** una sincronizacion de Football-Data de una sola liga, o una completa con
    alguna liga fallida, **When** termina, **Then** el momento de la ultima sincronizacion
    completa exitosa de Football-Data no cambia.
14. **Given** una sincronizacion de WhoScored de una sola liga, o una completa que se
    corto por bloqueo, **When** termina, **Then** el momento de la ultima sincronizacion
    completa exitosa de WhoScored no cambia.

---

### User Story 4 - Fallas y bloqueos de WhoScored (Priority: P4)

WhoScored puede rechazar pedidos aislados, devolver paginas sin los datos o bloquear por
completo. Un rechazo aislado se reintenta; una pagina que falla dos veces seguidas se toma
como bloqueo y corta la corrida, sin perder lo guardado. Lo que falta queda pendiente y la
proxima corrida sigue donde quedo. El resto del sistema nunca depende de WhoScored.

**Why this priority**: El enunciado exige tolerar las fallas del proveedor externo y
WhoScored bloquea por IP: insistir lo empeora. Es P4 porque necesita de las historias
anteriores para tener algo que proteger.

**Independent Test**: Se prueba simulando un rechazo aislado, una pagina sin los datos, un
bloqueo a mitad de corrida y una aplicacion detenida en medio de un partido, y
verificando que lo guardado se conserva, que no queda ningun partido a medias y que la
corrida siguiente sigue con los pendientes.

**Acceptance Scenarios**:

1. **Given** una pagina que WhoScored rechaza una vez, **When** el sistema espera al menos
   30 segundos, la reintenta y esta vez responde bien, **Then** el partido se procesa con
   normalidad **And** la corrida sigue.
2. **Given** una pagina que llega sin los datos del partido, **When** se la reintenta y
   esta vez llega bien, **Then** el partido se procesa con normalidad **And** la corrida
   sigue.
3. **Given** una pagina que falla tambien en el reintento, **When** ocurre, **Then** la
   corrida entera se corta, en todas las ligas que faltaban **And** no se hace ningun
   otro pedido a WhoScored en esa corrida **And** los partidos ya guardados se conservan
   **And** el informe indica que se corto por bloqueo y en que partido.
4. **Given** una corrida cortada por bloqueo con 30 partidos pendientes, **When** corre la
   siguiente sincronizacion y WhoScored responde, **Then** procesa esos 30 partidos sin
   volver a pedir los ya procesados.
5. **Given** la aplicacion detenida mientras se procesa un partido, **When** vuelve a
   arrancar, **Then** ese partido no tiene ninguna estadistica guardada **And** se procesa
   entero en la siguiente sincronizacion.
6. **Given** cualquier sincronizacion de WhoScored, **When** se observan los pedidos,
   **Then** nunca hay dos en curso a la vez **And** entre uno y el siguiente pasan al
   menos 4 segundos.
7. **Given** WhoScored caido o bloqueando, **When** se consulta el catalogo o corre la
   sincronizacion de Football-Data, **Then** responden igual que con WhoScored
   disponible.
8. **Given** un partido terminado segun Football-Data que WhoScored todavia no da por
   terminado, o que no aparece en WhoScored, **When** se sincroniza, **Then** se omite con
   ese motivo, sin contar como falla **And** queda pendiente para la proxima.
9. **Given** un bloqueo durante la puesta al dia al arrancar, **When** se corta la corrida,
   **Then** la aplicacion sigue funcionando con lo guardado **And** los partidos que
   faltan quedan pendientes para la proxima sincronizacion de WhoScored.

---

### Edge Cases

- Un jugador pasa de un equipo a otro de las cinco ligas durante la temporada: el catalogo
  lo tiene en su equipo actual y WhoScored lo informa en el anterior en los partidos
  viejos. Como en ese equipo no hay candidato, se lo busca en todo el catalogo por nombre
  normalizado y edad, y se cruza con el unico que tiene exactamente la fecha de nacimiento de
  su pagina de jugador. Si en cada fuente figura con otro nombre, se lo cruza igual con el que
  tiene esa fecha si los nombres coinciden por los niveles 1 a 3.
- Dos jugadores de WhoScored de equipos distintos tienen el mismo nombre y la misma edad
  ("Vitinha" del Genoa y del Paris Saint-Germain): uno no se cruza con el del catalogo que
  tiene ese nombre en otro equipo, porque su fecha de nacimiento es distinta, ni con un
  parecido de su propio equipo. Si el catalogo lo tiene con otro nombre ("Vitor Oliveira"),
  se cruza con el por la fecha; si no lo tiene, queda sin cruzar como no confirmado.
- Un jugador nacio el mismo dia que un companero de su equipo ("Riccardo Ciervo" e "Ignace
  Van Der Brempt", del Sassuolo): sin ninguna relacion de nombre, la fecha sola no los cruza.
  Si el catalogo lo tiene en otro equipo, como a Ciervo en el Cagliari, se lo cruza con ese
  por el paso 2 de FR-019d.
- Un jugador que dejo las cinco ligas antes de que se creara la base no esta en el
  catalogo: no se puede cruzar, se saltea y va al informe.
- Un jugador inactivo que sigue en el catalogo (dejo las cinco ligas despues de creada la
  base) se puede cruzar como cualquier otro.
- Dos jugadores del catalogo del mismo equipo tienen el mismo nombre normalizado: desempata
  la edad o la fecha de nacimiento; si sigue siendo ambiguo, no se cruza y va al informe.
- Un jugador sin candidato en su equipo tiene dos homonimos de la misma edad en el resto
  del catalogo: no se cruza y va al informe.
- El nombre de un equipo en WhoScored no se parece al oficial ("Man Utd" y "Manchester
  United FC", "PSG" y "Paris Saint-Germain FC"): se cruza por la coincidencia de sus
  partidos de liga con los de un unico equipo del catalogo.
- Los partidos de un equipo de WhoScored coinciden con los de mas de un equipo del
  catalogo, o con los de ninguno: queda sin cruzar y sus partidos van al informe.
- Un mismo jugador del catalogo nunca queda cruzado con dos identificadores de WhoScored,
  ni un identificador de WhoScored con dos jugadores del catalogo.
- Un jugador figura con otro nombre en cada fuente: nombre corto o completo, otro orden,
  un apodo, otra transliteracion, apostrofos o guiones. Si la regla estricta no lo
  encuentra, se lo busca en su equipo por niveles cada vez mas flexibles, siempre con la
  misma edad; si en un nivel hay mas de un candidato, no se cruza y va al informe.
- Un jugador cumple anos entre el partido y la sincronizacion: WhoScored ya informa la
  edad nueva, y se lo reconoce igual.
- Un juvenil o un refuerzo reciente todavia no esta en el plantel de Football-Data: no
  esta en el catalogo, asi que no se cruza y va al informe. Nunca se lo cruza por
  descarte con otro jugador del equipo.
- Un jugador figura con un nombre que no se parece en nada en cada fuente (un apodo, o
  "Moustapha Mbow" y "Mamadou Mbow"): si su pagina de jugador en WhoScored trae la misma
  fecha de nacimiento que un unico jugador sin cruzar de su equipo, se lo cruza con el.
- Dos jugadores del mismo equipo comparten el nombre de pila y la edad: el nombre de pila
  solo no los cruza; los distingue la fecha de nacimiento de la pagina del jugador.
- La fecha de nacimiento del catalogo no es la exacta (un 1 de enero de relleno): ni la
  fecha ni a veces la edad coinciden con las de WhoScored. Si la pagina del jugador trae su
  nombre completo, y el nombre del catalogo esta dentro de el, se lo cruza con el jugador de su
  equipo nacido el mismo ano ("Rafita", "Rafael Garrido Hierro", y "Rafa Garrido").
- La pagina de un jugador no trae el nombre completo: solo se usa su fecha de nacimiento.
- La pagina de un jugador en WhoScored falla siempre: ese jugador queda sin cruzar en cada
  corrida, pero la corrida no se corta.
- Un suplente entra en el tiempo agregado del segundo tiempo: tiene estadisticas, con los
  minutos desde que entra hasta el final, agregado incluido.
- Un jugador sale en el tiempo agregado del primer tiempo: sus minutos incluyen lo que se
  jugo de ese agregado.
- Un jugador es expulsado por doble amarilla: cuenta una tarjeta amarilla y una roja.
- Un suplente recibe una tarjeta sin haber entrado a la cancha: no tiene estadisticas en
  ese partido, asi que esa tarjeta no se guarda.
- Un partido terminado que WhoScored todavia no publica, o que publica sin terminar: se
  omite sin contar como falla y queda pendiente.
- Un partido postergado se juega semanas despues: se procesa en la primera sincronizacion
  posterior a que figure terminado.
- Un partido adjudicado sin jugarse: no tiene estadisticas.
- WhoScored corrige las estadisticas de un partido ya procesado: no se reprocesa.
- WhoScored informa un partido de copa entre dos equipos de la misma liga: no se confunde
  con el de liga, porque solo se cruzan partidos de liga.
- La pagina de un mismo partido falla siempre, aunque WhoScored no este bloqueando: cada
  corrida se corta en ese partido y el informe lo identifica, para que se pueda revisar.
- La aplicacion se detiene en medio de una sincronizacion: lo guardado se conserva y el
  partido que se estaba procesando no queda a medias.
- Cambia la temporada en curso: la sincronizacion pasa a procesar solo la nueva; los
  partidos de la anterior que quedaron sin estadisticas no se procesan.
- No hay credencial de Football-Data: la de WhoScored corre igual sobre lo guardado. Como
  la de Football-Data nunca queda al dia, cada arranque corre la cadena; la de WhoScored
  solo pide paginas si hay partidos pendientes.
- La de Football-Data esta al dia pero la ultima de WhoScored se corto por bloqueo: si la
  ultima completa exitosa de WhoScored tiene mas de 7 dias, el siguiente arranque corre
  solo la de WhoScored; si no, lo pendiente espera a la siguiente cadena o a un disparo
  manual.
- El catalogo esta vacio y no hay credencial de Football-Data: no hay partidos pendientes,
  no se consulta WhoScored y las entradas del snapshot se saltean por no estar en el
  catalogo.
- El snapshot falta, o no se puede leer completo: se ignora entero, queda una advertencia
  y se scrapea todo lo pendiente.
- El snapshot trae un partido que el catalogo todavia no da por terminado: no se carga; se
  carga en la primera sincronizacion posterior a que figure terminado.
- Un desarrollador regenera el snapshot desde una base a la que le faltan entradas del
  snapshot vigente: el snapshot nuevo las conserva.
- Dos desarrolladores regeneran el snapshot por separado: como una entrada nunca cambia una
  vez que esta en el snapshot, las dos versiones solo difieren en lo que cada uno agrego.
- El arranque con puesta al dia coincide con el lunes a las 04:00: la corrida programada
  se omite porque ya hay una sincronizacion en curso.
- Un administrador dispara a mano la de WhoScored sin snapshot sobre una temporada
  avanzada: el disparo espera a que termine, lo que puede llevar unos 47 minutos.
- Una base creada con la 005 no tiene registrada su ultima sincronizacion completa: el
  primer arranque con esta funcionalidad se pone al dia.
- La puesta al dia falla en las cinco ligas de Football-Data: la de WhoScored corre igual,
  y como el momento de la ultima completa exitosa no cambia, el siguiente arranque vuelve
  a ponerse al dia.
- Un usuario comun, o una peticion sin credencial, intenta disparar la de WhoScored: se
  rechaza por falta de permiso o de credencial, sin consultar WhoScored.

## Requirements *(mandatory)*

### Functional Requirements

#### Datos que se guardan

- **FR-001**: El sistema MUST obtener de WhoScored las estadisticas de cada jugador que
  entro a la cancha en cada partido terminado de la temporada en curso de las cinco ligas,
  y MUST guardarlas asociadas al jugador del catalogo y al partido que guarda la
  integracion con Football-Data.
- **FR-002**: Las estadisticas de un jugador en un partido MUST incluir: si fue titular,
  los minutos jugados, los goles, las asistencias, los tiros, los tiros al arco, los pases
  clave, los pases, los pases precisos, los regates exitosos, los quites, las
  intercepciones, los despejes, los duelos aereos ganados, las faltas cometidas, las
  tarjetas amarillas, las tarjetas rojas, el rating y si fue la figura del partido. El
  sistema MUST NOT guardar las demas metricas que informa WhoScored.
- **FR-003**: El sistema MUST traer estadisticas solo de los partidos de liga de la
  temporada en curso que guarda la integracion con Football-Data. MUST NOT traerlas de
  copas, torneos europeos, selecciones ni temporadas anteriores.
- **FR-004**: El sistema MUST NOT guardar estadisticas de un suplente que no entro a la
  cancha.
- **FR-005**: Los minutos jugados MUST medirse sobre el tiempo transcurrido desde el
  inicio del partido, incluido el tiempo agregado de los dos tiempos: los de un titular,
  desde el inicio hasta que sale (por cambio o por expulsion) o hasta el final; los de un
  suplente, desde que entra hasta que sale o hasta el final. Un titular que juega el
  partido completo suma 90 minutos mas lo agregado en los dos tiempos.
- **FR-006**: Los goles y las asistencias MUST contarse como los cuenta WhoScored: todo
  gol que no es en contra suma un gol a su autor y, si tiene un pase marcado como
  asistencia, una asistencia al autor de ese pase. Un gol en contra MUST NOT sumar un gol
  a su autor ni una asistencia a nadie.
- **FR-007**: Una expulsion por doble amarilla MUST contar como una tarjeta amarilla y una
  roja.
- **FR-008**: El rating MUST guardarse como lo informa WhoScored, con sus decimales, y la
  figura del partido MUST ser la que marca WhoScored.
- **FR-009**: Las estadisticas MUST NOT exponerse en esta funcionalidad: ningun recurso de
  la aplicacion ni el frontend las muestra.

#### Partidos que se procesan

- **FR-010**: Cada sincronizacion de WhoScored MUST procesar todos los partidos terminados
  de las ligas que sincroniza que todavia no tienen estadisticas, desde el inicio de la
  temporada.
- **FR-011**: El sistema MUST NOT procesar un partido programado, postergado, suspendido,
  cancelado o adjudicado. Si despues figura como terminado, MUST procesarlo en la primera
  sincronizacion posterior.
- **FR-012**: Un partido procesado MUST NOT volver a procesarse ni pedirse a WhoScored, y
  sus estadisticas MUST NOT cambiar, aunque WhoScored corrija algo despues.
- **FR-013**: El partido es la unidad de guardado: MUST guardarse con las estadisticas de
  todos sus jugadores cruzados o no guardarse. Una corrida interrumpida, por bloqueo, por
  una falla o porque se detuvo la aplicacion, MUST NOT dejar un partido a medias.
- **FR-014**: Un partido que WhoScored todavia no da por terminado, o que no aparece entre
  los partidos de liga de sus equipos en WhoScored, MUST omitirse con ese motivo, MUST
  quedar pendiente y MUST NOT contar como una falla de WhoScored.
- **FR-015**: Un partido MUST cruzarse con el de WhoScored por su equipo local y su equipo
  visitante, ya cruzados, dentro de la liga y la temporada, en las que esos dos equipos se
  enfrentan una sola vez con esa localia.

#### Cruce de equipos

- **FR-016**: Cada equipo MUST cruzarse una sola vez. Una vez cruzado, el sistema MUST
  guardar su identificador de WhoScored y MUST reconocerlo por el en las sincronizaciones
  siguientes.
- **FR-017**: Un equipo MUST cruzarse comparando su nombre normalizado (sin distinguir
  mayusculas, sin tildes y sin sufijos como FC, AFC o CF) y confirmando el cruce con
  partidos de la misma fecha entre los mismos rivales.
- **FR-017a**: Si el nombre normalizado no coincide con el de ningun equipo del catalogo
  de su liga (por ejemplo, "Man Utd" en WhoScored y "Manchester United FC" en
  Football-Data), el equipo MUST cruzarse igual cuando sus partidos de liga de la
  temporada coinciden en fecha, localia y rivales con los de un unico equipo del catalogo
  de su liga.
- **FR-018**: Un equipo que no se cruza por ninguno de los dos criterios, o cuyos partidos
  coinciden con los de mas de un equipo del catalogo, MUST quedar sin cruzar. Su liga MUST
  seguir: los partidos de ese equipo MUST omitirse y MUST figurar en el informe con ese
  motivo.

#### Cruce de jugadores

- **FR-019**: Un jugador de WhoScored MUST buscarse primero entre los jugadores del
  catalogo del mismo equipo en ese partido, comparando el nombre normalizado. Si hay mas
  de un candidato, MUST desempatar la edad (FR-019c); si sigue siendo ambiguo, MUST NOT
  cruzarse.
- **FR-019a**: Si no hay ningun candidato en ese equipo, el jugador MUST buscarse entre
  todos los jugadores del catalogo, activos o inactivos, con el mismo nombre normalizado y
  la misma edad (FR-019c). El nombre y la edad no alcanzan para cruzarlo con un jugador de
  otro equipo, porque dos jugadores distintos pueden compartirlos ("Vitinha" del Genoa y
  "Vitinha" del Paris Saint-Germain, los dos de 26 anos). Si hay alguno, el jugador MUST NOT
  buscarse por los niveles de FR-019b y MUST confirmarse con la fecha de nacimiento de su
  pagina de jugador (FR-019d). Sin confirmar, queda sin cruzar como no confirmado si el
  candidato era uno, y como ambiguo si eran mas. Asi se cruza a un jugador que cambio de
  equipo dentro de las cinco ligas en los partidos que jugo con el anterior.
- **FR-019b**: Si con FR-019 y FR-019a no hay ningun candidato, el jugador MUST buscarse
  entre los jugadores del mismo equipo que todavia no tienen identificador de WhoScored
  ni fueron elegidos para otro jugador del partido, con estos niveles en orden:
  1. las mismas palabras, en otro orden ("Lee Kang-In" y "Kang-in Lee");
  2. todas las palabras de un nombre estan en el otro ("Antonio Sivera" y "Sivera");
  3. el mismo apellido, que es la ultima palabra ("Alex Baena" y "Alejandro Baena");
  4. el mismo nombre escrito sin espacios, guiones ni apostrofos, en cualquier orden de
     sus palabras, o uno de al menos cuatro letras contenido en el otro escrito asi
     ("Ndicka" y "N'Dicka", "Gavi" y "Pablo Gavira");
  5. el apellido con a lo sumo dos letras distintas, si los dos tienen al menos cinco
     ("Yarmoliuk" y "Yarmolyuk");
  6. una palabra de un nombre es el comienzo de una del otro, las dos de al menos cuatro
     letras ("Chupe" y "Chupete", "Kiko Femenia" y "Femenia Far"), salvo que los dos
     nombres tengan mas de una palabra y la unica coincidencia sea la primera palabra de
     los dos: el nombre de pila solo no alcanza ("Mathis Saka" y "Mathis Amougou" no
     coinciden).

  En cada nivel solo cuentan los candidatos de la misma edad (FR-019c). Con uno, el
  jugador MUST cruzarse con el; con mas de uno, MUST NOT cruzarse ni pasar al nivel
  siguiente. El registro MUST dejar cada cruce hecho por estos niveles, con el nivel y los
  dos nombres, para poder revisarlo.
- **FR-019c**: La edad que informa WhoScored es la del dia en que se consulta la pagina.
  Un jugador del catalogo MUST considerarse de la misma edad si la que surge de su fecha
  de nacimiento el dia del partido es esa o una menos. Un jugador sin fecha de nacimiento,
  o uno de WhoScored sin edad, nunca es de la misma edad.
- **FR-019d**: Si con FR-019 a FR-019b el jugador queda sin cruzar, por no encontrado, por
  ambiguo o por no confirmado, el sistema MUST pedir una vez su pagina de jugador en
  WhoScored y leer su fecha de nacimiento y, si la trae, su nombre completo. Los candidatos
  son los jugadores del catalogo sin cruzar y no elegidos para otro jugador del partido, y
  el sistema MUST probar estos pasos en orden:
  1. los de su equipo con exactamente esa fecha de nacimiento y alguna relacion de nombre:
     comparten una palabra de al menos cuatro letras, coinciden por alguno de los niveles de
     FR-019b (sin la condicion de la edad), o el nombre del catalogo esta dentro del nombre
     completo. Dos jugadores del mismo equipo pueden nacer el mismo dia ("Riccardo Ciervo" e
     "Ignace Van Der Brempt", del Sassuolo, nacidos el 01/04/2002);
  2. si el paso 1 no encuentra ninguno, los de otros equipos, activos o inactivos, con
     exactamente esa fecha y un nombre que coincide con el de WhoScored por los niveles 1 a 3
     de FR-019b (el primero incluye el nombre igual) o que esta dentro del nombre completo.
     Asi se cruza al que cambio de equipo aunque cada fuente escriba distinto su nombre
     ("Jose Maria Gimenez" y "Jose Gimenez", nacidos el 20/01/1995);
  3. si tampoco hay ninguno, los de su equipo nacidos el mismo ano cuyo nombre esta dentro
     del nombre completo. Asi se cruza al que tiene en el catalogo una fecha que no es la
     exacta ("Rafita", nombre completo "Rafael Garrido Hierro", nacido el 09/11/2004, y "Rafa
     Garrido", con el 01/01/2004 en el catalogo).

  Un nombre del catalogo esta dentro del nombre completo si cada una de sus palabras es una
  palabra distinta del nombre completo, o el comienzo de una si tiene al menos cuatro letras
  ("Rafa Garrido" esta dentro de "Rafael Garrido Hierro"; "Pablo Garcia" no esta dentro de
  "Mauro Echegoyen Garcia"). Con un candidato en un paso, el jugador MUST cruzarse con el;
  con mas de uno, MUST NOT cruzarse ni pasar al paso siguiente. Con ninguno en los tres, o
  si la pagina no trae la fecha, MUST NOT cruzarse y queda con su motivo.
  - La pagina de cada jugador MUST pedirse a lo sumo una vez por corrida, con el mismo
    ritmo que las demas (FR-042) y un unico reintento (FR-043).
  - Si la pagina no llega ni en el reintento, o no trae la fecha de nacimiento, el jugador
    queda sin cruzar y la corrida MUST seguir: a diferencia de FR-044, una pagina de
    jugador no corta la corrida.
  - El registro MUST dejar cada cruce hecho con la pagina del jugador, con los dos nombres
    y: en los pasos 1 y 2, la fecha, y si el jugador del catalogo es de otro equipo, que lo
    es; en el paso 3, el ano y el nombre completo.
- **FR-020**: Una vez cruzado, el sistema MUST guardar el identificador de WhoScored del
  jugador y MUST reconocerlo por el en los partidos siguientes, sin volver a comparar
  nombres.
- **FR-021**: Un jugador de WhoScored que no se puede cruzar MUST saltearse y MUST figurar
  en el informe con su nombre, su equipo y el partido. El sistema MUST NOT crear jugadores
  nuevos ni ofrecer un cruce manual.
- **FR-022**: Un jugador del catalogo MUST tener a lo sumo un identificador de WhoScored,
  y un identificador de WhoScored MUST corresponder a lo sumo a un jugador del catalogo.

#### Snapshot

- **FR-023**: El repositorio MUST incluir un snapshot: un archivo de texto versionado con
  las estadisticas ya obtenidas y los identificadores de WhoScored de los equipos, los
  jugadores y los partidos, ubicados por los identificadores de Football-Data.
- **FR-024**: Antes de scrapear, la sincronizacion de WhoScored MUST completar desde el
  snapshot los partidos pendientes que este contiene, junto con los cruces de sus equipos y
  sus jugadores, y despues MUST scrapear solo lo que sigue faltando.
- **FR-025**: Lo que trae el snapshot MUST tomarse tal cual: el sistema MUST NOT pedir a
  WhoScored un partido que el snapshot trajo. La carga desde el snapshot MUST NOT depender
  de que WhoScored este disponible.
- **FR-026**: Las entradas del snapshot cuyo partido, equipo o jugador no esta en el
  catalogo MUST saltearse y MUST figurar en el informe; el resto MUST cargarse igual. Un
  partido del snapshot se carga con los jugadores que estan en el catalogo.
- **FR-027**: Si el snapshot falta o no se puede leer completo, el sistema MUST ignorarlo
  entero, MUST dejar una advertencia en el registro de la aplicacion, MUST scrapear todo
  lo pendiente y MUST seguir funcionando.
- **FR-028**: Un desarrollador MUST poder regenerar el snapshot desde su base local con un
  comando de desarrollo. Regenerarlo MUST NOT ser un recurso de la aplicacion.
- **FR-029**: El snapshot MUST contener solo las estadisticas, los identificadores de
  WhoScored y los de Football-Data que los ubican. MUST NOT contener usuarios,
  contrasenas, claves de API, operaciones ni ningun otro dato.
- **FR-030**: Regenerar el snapshot MUST ser deterministico: con los mismos datos, el
  archivo MUST quedar identico; despues de procesar partidos nuevos, MUST cambiar solo en
  las entradas de esos partidos y en los cruces nuevos.
- **FR-031**: Al regenerar el snapshot, el sistema MUST conservar sin cambios las entradas
  del snapshot vigente, incluidas las que la base local no tiene, y MUST sumar las de la
  base local que todavia no estan en el.
- **FR-032**: La base local MUST NOT versionarse en el repositorio.

#### Disparo de la sincronizacion

- **FR-033**: Todos los lunes a las 04:00, hora de Argentina (UTC-3), el sistema MUST
  correr la sincronizacion completa de Football-Data y, al terminar, la completa de
  WhoScored.
- **FR-034**: Al arrancar, si la ultima sincronizacion completa exitosa de Football-Data
  tiene mas de 7 dias, o si nunca hubo una, el sistema MUST correr en segundo plano, sin
  demorar el arranque, la sincronizacion completa de Football-Data y, al terminar, la
  completa de WhoScored. Si tiene 7 dias o menos, MUST NOT correr la de Football-Data.
- **FR-035**: Al arrancar, si la ultima sincronizacion completa exitosa de Football-Data
  tiene 7 dias o menos pero la de WhoScored tiene mas de 7 dias, o nunca hubo una, el
  sistema MUST correr en segundo plano, sin demorar el arranque, solo la sincronizacion
  completa de WhoScored. Si las dos tienen 7 dias o menos, MUST NOT correr ninguna.
- **FR-036**: En una cadena, la sincronizacion de WhoScored MUST correr cuando termina la
  de Football-Data, aunque esta haya fallado en alguna liga o en todas, o no haya corrido
  por falta de credencial.
- **FR-037**: Un administrador (rol ADMIN) MUST poder disparar a mano la sincronizacion de
  WhoScored, completa o de una sola de las cinco ligas. El sistema MUST rechazar el
  disparo por falta de permiso cuando lo pide un usuario que no es administrador, por
  falta de credencial cuando la peticion no trae ninguna y, indicando el valor no
  admitido, cuando la liga no es una de las cinco; en todos los casos, sin consultar
  WhoScored.
- **FR-038**: Los disparos manuales de las dos fuentes MUST NOT encadenarse: cada uno
  corre solo su sincronizacion.
- **FR-039**: El disparo manual de WhoScored MUST esperar a que la sincronizacion termine
  y MUST devolver su informe, tambien cuando se corto por bloqueo.
- **FR-040**: La sincronizacion de WhoScored MUST NOT necesitar la credencial de
  Football-Data: MUST poder correr sobre lo que ya esta guardado.
- **FR-041**: El sistema MUST ejecutar a lo sumo una sincronizacion a la vez, de
  cualquiera de las dos fuentes. Una cadena cuenta como una sola sincronizacion en curso,
  desde que empieza la de Football-Data hasta que termina la de WhoScored. Si hay una en
  curso, el sistema MUST rechazar el disparo manual informando el motivo y MUST omitir la
  corrida programada o la puesta al dia, dejando el motivo en el registro de la
  aplicacion.

#### Ritmo y fallas de WhoScored

- **FR-042**: El sistema MUST pedir a WhoScored una pagina a la vez, con al menos 4
  segundos entre un pedido y el siguiente.
- **FR-043**: Una pagina que WhoScored rechaza, o que llega sin los datos, MUST
  reintentarse una sola vez, despues de esperar al menos 30 segundos.
- **FR-044**: Si el reintento tambien falla, el sistema MUST tomarlo como un bloqueo: MUST
  cortar la corrida entera, en todas las ligas que faltaban, y MUST NOT hacer ningun otro
  pedido a WhoScored en esa corrida. La pagina de un jugador es la excepcion (FR-019d): si
  WhoScored esta bloqueando, el corte llega con la pagina siguiente de un partido.
- **FR-045**: Ante un corte, el sistema MUST conservar todo lo ya guardado; los partidos
  que faltan MUST quedar pendientes para la proxima corrida, que MUST seguir donde quedo
  esta.
- **FR-046**: Una falla o un bloqueo de WhoScored MUST NOT afectar al resto del sistema:
  el arranque, las consultas del catalogo y la sincronizacion de Football-Data MUST
  funcionar igual que con WhoScored disponible.

#### Ultima sincronizacion completa e informe

- **FR-047**: Por cada fuente, el sistema MUST guardar el momento de su ultima
  sincronizacion completa exitosa, y ningun otro dato de las corridas. Es completa exitosa
  la de Football-Data con las cinco ligas procesadas bien, y la de WhoScored de las cinco
  ligas que no se corto por bloqueo; los partidos y jugadores omitidos no le quitan el
  exito. Una sincronizacion de una sola liga nunca cuenta.
- **FR-048**: Cada sincronizacion de WhoScored MUST producir un informe con el tipo
  (completa o de una liga), el origen (corrida semanal, puesta al dia al arrancar o
  disparo manual), el inicio y el fin, si se corto por bloqueo y en que partido, y, por
  cada liga, cuantos partidos se procesaron desde el snapshot y cuantos por scraping, los
  partidos omitidos con su motivo, los equipos sin cruzar, los jugadores sin cruzar (con
  su nombre, su equipo y el partido) y cuantas entradas del snapshot se saltearon y por
  que motivo.
- **FR-049**: El informe MUST quedar en el registro de la aplicacion y, si el disparo fue
  manual, en la respuesta. El sistema MUST NOT guardar un historial de informes.

#### Cambios sobre la integracion con Football-Data (005)

- **FR-050**: El FR-027 de la 005, que no recuperaba la corrida del lunes perdida, MUST
  quedar reemplazado por la puesta al dia al arrancar de FR-034.
- **FR-051**: El FR-033 de la 005, que sincronizaba al arrancar solo con el catalogo
  vacio, MUST quedar reemplazado por FR-034: se sincroniza con la base vacia o con la
  ultima sincronizacion completa exitosa de mas de 7 dias, y la sigue la de WhoScored.
- **FR-052**: El FR-031 de la 005, una sincronizacion a la vez, MUST pasar a abarcar las
  dos fuentes, segun FR-041.
- **FR-053**: El origen del informe del FR-043 de la 005 MUST pasar a ser la corrida
  semanal, la puesta al dia al arrancar o un disparo manual: "puesta al dia al arrancar"
  reemplaza a "arranque con el catalogo vacio".
- **FR-054**: El FR-045 de la 005, sin historial de informes, MUST mantenerse, con la
  unica excepcion del momento de la ultima sincronizacion completa exitosa de FR-047.
- **FR-055**: Los tests de la 005 que cubren FR-027, FR-031, FR-033 y FR-043 MUST
  modificarse para reflejar estos cambios, con el si explicito del equipo (Clarifications,
  Session 2026-10-13); el plan MUST listar cuales cambian y por que. Los tests de los
  comportamientos de la 005 que no cambian MUST NOT modificarse.

### Key Entities

- **Estadistica de un jugador en un partido**: las metricas de FR-002 de un jugador del
  catalogo en un partido terminado. Hay una por cada jugador que entro a la cancha. Nunca
  cambia una vez guardada.
- **Equipo** (amplia la 005): suma su identificador de WhoScored, una vez cruzado.
- **Jugador** (amplia la 005): suma su identificador de WhoScored, una vez cruzado. Un
  jugador tiene a lo sumo uno, y un identificador corresponde a lo sumo a un jugador.
- **Partido** (amplia la 005): suma su identificador de WhoScored, una vez cruzado, y si
  ya tiene estadisticas. Un partido con estadisticas no se vuelve a procesar.
- **Snapshot**: archivo de texto versionado en el repositorio con las estadisticas ya
  obtenidas y los identificadores de WhoScored de equipos, jugadores y partidos, ubicados
  por los de Football-Data. Lo regenera un desarrollador; sus entradas no cambian una vez
  que estan.
- **Sincronizacion** (amplia la 005): suma la fuente, Football-Data o WhoScored. Es
  completa o de una sola liga; su origen es la corrida semanal, la puesta al dia al
  arrancar o un disparo manual. La corrida semanal encadena las dos fuentes; la puesta al
  dia las encadena o corre solo la de WhoScored (FR-034 y FR-035). Una cadena cuenta como
  una sola sincronizacion en curso. Hay a lo sumo una a la vez.
- **Ultima sincronizacion completa exitosa**: el momento en que termino, uno por fuente.
  Es el unico dato que se guarda de las corridas y decide la puesta al dia de cada fuente.
- **Informe de la sincronizacion de WhoScored**: el resumen de una corrida, descripto en
  FR-048. Va al registro de la aplicacion y, si el disparo fue manual, a la respuesta; no
  se guarda.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Despues de una sincronizacion de WhoScored completa y sin corte, el 100% de
  los partidos terminados de las cinco ligas tiene estadisticas, salvo los que el informe
  lista como omitidos.
- **SC-002**: En los partidos procesados, al menos el 95% de los jugadores que entraron a
  la cancha queda cruzado con un jugador del catalogo; el 100% de los que no, figura en el
  informe.
- **SC-003**: Ningun equipo, jugador ni partido queda cruzado con uno de WhoScored que no
  le corresponde, y el 100% de los equipos sin cruzar figura en el informe.
- **SC-003a**: Despues de la primera sincronizacion completa de WhoScored sin corte, el
  100% de los equipos de la temporada en curso de las cinco ligas (96) queda cruzado, sin
  cruce manual, incluidos los que WhoScored nombra distinto que Football-Data.
- **SC-004**: Con WhoScored respondiendo con normalidad, ponerse al dia con una fecha
  completa de las cinco ligas (unos 48 partidos) lleva menos de 15 minutos.
- **SC-005**: Con WhoScored respondiendo con normalidad y sin snapshot, ponerse al dia
  desde el inicio de la temporada (unos 330 partidos al 2026-10-13) lleva menos de 60
  minutos.
- **SC-006**: La sincronizacion pide a WhoScored cero paginas de partidos que trae el
  snapshot; con una base nueva y un snapshot al dia hasta la fecha anterior, las
  estadisticas de todos los partidos terminados estan guardadas menos de 15 minutos
  despues de que termina la sincronizacion de Football-Data.
- **SC-007**: En el 100% de las corridas nunca hay dos pedidos a WhoScored en curso a la
  vez, y entre uno y el siguiente pasan al menos 4 segundos.
- **SC-008**: Un corte por bloqueo, una falla o una detencion de la aplicacion nunca deja
  un partido a medias, y la cantidad de partidos con estadisticas nunca disminuye.
- **SC-009**: Despues de una corrida cortada, la siguiente pide cero paginas de partidos
  ya procesados.
- **SC-010**: Las estadisticas de un partido procesado quedan identicas despues de
  cualquier sincronizacion posterior.
- **SC-011**: En el 100% de los arranques la aplicacion atiende consultas sin esperar a
  ninguna fuente, haya o no puesta al dia.
- **SC-012**: En el 100% de los arranques, sin intervencion de nadie: con la ultima
  sincronizacion completa exitosa de Football-Data de hace mas de 7 dias, o sin ninguna,
  se ponen al dia las dos fuentes; con la de Football-Data al dia y la de WhoScored de
  hace mas de 7 dias, o sin ninguna, se pone al dia solo la de WhoScored; con las dos al
  dia, no corre ninguna.
- **SC-013**: En ningun momento hay dos sincronizaciones en curso a la vez, de ninguna de
  las dos fuentes.
- **SC-014**: El snapshot contiene cero usuarios, contrasenas, claves de API y
  operaciones; regenerarlo sin datos nuevos lo deja identico, y regenerarlo despues de una
  fecha nueva solo agrega las entradas de esos partidos y los cruces nuevos.
- **SC-015**: El 100% de los partidos omitidos, los equipos y jugadores sin cruzar, las
  entradas del snapshot salteadas y los cortes por bloqueo figura en el informe de la
  sincronizacion en la que ocurrieron.
- **SC-016**: Con WhoScored caido o bloqueando, el 100% de las consultas del catalogo y
  de las sincronizaciones de Football-Data responden igual que con WhoScored disponible.
- **SC-017**: El 100% de los intentos de disparo manual de la sincronizacion de WhoScored
  de quien no es administrador se rechazan sin consultar WhoScored.

## Assumptions

- Las 04:00 de la corrida programada son hora de Argentina (UTC-3), como en la 005.
- El administrador que dispara la sincronizacion es la cuenta del alta de arranque de la
  funcionalidad de autenticacion (su FR-036), como en la 005.
- El ritmo de una pagina cada 4 segundos y el reintento despues de 30 segundos salen de
  lo verificado el 2026-10-13: a ese ritmo, 49 de 50 paginas respondieron con los datos, y
  la que fallo respondio bien al reintentarla. El bloqueo de WhoScored es por IP, asi que
  insistir lo empeora: por eso dos fallas seguidas cortan la corrida.
- El volumen sale de lo verificado ese dia: un fin de semana completo son 48 partidos
  entre las cinco ligas, en la temporada ya se jugaron unos 330, y ponerse al dia desde el
  inicio son unas 430 paginas (5 de liga, hasta 96 de equipos y unas 330 de partidos), cerca
  de 47 minutos al ritmo de FR-042.
- Los terminos de uso de WhoScored no permiten el scraping. Es un requisito del TP, asi
  que el sistema pide poco volumen y no expone los datos a los usuarios.
- WhoScored publica las estadisticas poco despues de cada partido, no en vivo. Un partido
  recien terminado puede quedar pendiente hasta la sincronizacion siguiente.
- El nombre normalizado ignora mayusculas, tildes y sufijos como FC, AFC o CF. Los
  nombres no coinciden exactamente entre las fuentes ("Hull" en WhoScored es "Hull City
  AFC" en Football-Data), y el plan define como se comparan.
- Los minutos incluyen el tiempo agregado, asi que un partido completo vale mas de 90 y
  varia de un partido a otro, y un jugador que no completo el partido puede sumar mas de
  90. Como aplica la cotizacion el umbral del enunciado ("jugar menos de 90 minutos") lo
  define la Entrega 2.
- WhoScored informa la edad de cada jugador del partido el dia en que se consulta la
  pagina, y el catalogo tiene la fecha de nacimiento; las comparaciones por edad aceptan
  la que surge de esa fecha el dia del partido o una mas (FR-019c).
- Los nombres de los jugadores tambien difieren entre las fuentes (nombre corto o
  completo, apodos, transliteraciones). FR-019b define los niveles de comparacion. Los
  jugadores que no estan en el plantel de Football-Data no se pueden cruzar.
- La pagina de cada jugador en WhoScored muestra su fecha de nacimiento completa, que
  confirma un cruce cuando los nombres no alcanzan (FR-019d), y muchas veces su nombre
  completo (verificado el 2026-10-15: lo traen "Rafita" y los dos "Vitinha", y no lo traen
  "Riccardo Ciervo" ni "Youssef Enriquez"). Pedirla solo para los que quedan sin cruzar
  y para los que tienen un candidato en otro equipo suma alrededor de cien paginas la
  primera vez y pocas despues.
- El entorno de tests no consulta WhoScored real: los tests usan un WhoScored simulado, y
  la puesta al dia al arrancar no corre en ese entorno salvo que un test la habilite a
  proposito, con el mismo criterio que la 005 aplica a su credencial.
- Las bases locales creadas con la 005 no tienen registrada la ultima sincronizacion
  completa: el primer arranque con esta funcionalidad se pone al dia, y asi trae por
  primera vez las estadisticas.
- El disparo manual de WhoScored puede tardar lo que tarde la sincronizacion: unos pocos
  minutos para una fecha y hasta unos 47 sin snapshot desde el inicio de la temporada.
- La herramienta con la que se bajan las paginas (un cliente HTTP o un navegador sin
  ventana) la decide el plan.
- Los mensajes de error del sistema se redactan en espanol, segun el Principio VII de la
  constitucion.

## Dependencies

- **Integracion con Football-Data (005)**: da el catalogo, los partidos con su estado, los
  identificadores de la fuente, la sincronizacion, el bloqueo de una a la vez y el
  informe. Esta funcionalidad modifica sus FR-027, FR-031, FR-033 y FR-043, y acota su
  FR-045 (ver FR-050 a FR-055). Su FR-026 (la corrida del lunes) se mantiene y ahora la
  sigue la de WhoScored; su FR-041 (sin credencial, la sincronizacion de Football-Data
  queda deshabilitada) se mantiene y no alcanza a la de WhoScored.
- **Autenticacion (001)**: el disparo manual exige el rol ADMIN.
- **Constitucion 2.2.1**: la capa de adapters para fuentes externas, con WhoScored como
  proveedor. La necesita el plan, no este spec.
- **Cotizacion (Entrega 2, posterior)**: consume estas estadisticas.
- **Frontend (004)**: no cambia, porque las estadisticas no se exponen en esta
  funcionalidad.

## Fuera de Alcance

No forman parte de esta funcionalidad:

- Mostrar las estadisticas por la API o en el frontend.
- Las copas, los torneos europeos, las selecciones y las temporadas anteriores.
- El cruce manual de jugadores o de equipos.
- Volver a procesar partidos ya procesados.
- Los datos en vivo.
- Las paginas de perfil de jugador como fuente de estadisticas.
- El calculo de las cotizaciones.
- La eleccion de la herramienta para bajar las paginas (cliente HTTP o navegador sin
  ventana): la decide el plan.
