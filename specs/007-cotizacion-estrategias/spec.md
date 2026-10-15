# Feature Specification: Cotización de jugadores y estrategias de valuación

**Feature Branch**: `feature/cotizacion-estrategias`

**Created**: 2026-10-14

**Status**: Draft

**Input**: User description: "Cotización de jugadores y estrategias de valuación (Entrega 2), con su frontend. El sistema recotiza cada semana a TODOS los jugadores activos de las cinco ligas (el valor del 100 % del jugador, en créditos) a partir de sus estadísticas de la semana y de la estrategia de valuación activa, y guarda el historial. Un administrador registra y configura las estrategias y, al cambiarlas, decide si se recotiza en el acto o la semana siguiente. Se puede consultar la cotización actual de un jugador, la de una fecha, su evolución y el ranking, tanto por API como desde la aplicación web, y simular una estrategia sin guardar nada."

## Clarifications

### Session 2026-10-14

Decisiones del equipo que llegaron con el pedido de la funcionalidad:

- Q: Qué es la cotización de un jugador? -> A: El valor del 100 % del jugador, en créditos.
  Sus 100 tokens son ese 100 %: cada token es el 1 % y vale la cotización dividida 100. Un
  usuario con N tokens tiene el N % del jugador y su posición vale la cotización × N / 100
  (con 10 tokens y una cotización de 103,00, vale 10,30). Al inicio, con cada token a 1
  crédito, el jugador vale 100.
- Q: Cómo se informa? -> A: La cotización informa el valor del jugador y el precio por
  token, con 4 decimales. El redondeo a créditos se hace sobre el total que paga o recibe un
  usuario, en la funcionalidad de mercado; esta no redondea a créditos enteros.
- Q: Cuál es la fórmula? -> A: valor = valorBase + (score × factorEscala), con score = x ·
  goles + y · asistencias + n · tiros + m · pases clave + z · regates + t · quites + u ·
  rating, donde x, y, n, m, z, t y u son los pesos de la estrategia y suman 1. El valorBase
  es 100 la primera vez (100 tokens a 1 crédito) y después pasa a ser el valor anterior del
  jugador: el precio tiene memoria y puede subir o bajar.
- Q: A quiénes se recotiza cada semana? -> A: A TODOS los jugadores activos. Que jueguen
  pocos minutos, muchos o ninguno altera sus estadísticas y, con ellas, su cotización.
- Q: Qué pasa cuando empieza un campeonato? -> A: Se usan las últimas estadísticas que se
  encuentren: mientras no hay partidos, nadie cambia y cada jugador conserva el precio con el
  que cerró.
- Q: Quién registra las estrategias? -> A: El administrador, y puede variar cada parámetro.
- Q: Qué pasa al cambiar la estrategia? -> A: El administrador elige: recotizar en el acto, o
  solo cambiar la estrategia y recotizar la semana siguiente.
- Q: El frontend va aparte? -> A: No. Las funcionalidades se adaptan al frontend en esta
  misma funcionalidad.
- Q: Qué va en la funcionalidad de mercado, no en esta? -> A: La compra, la venta, las
  órdenes, los tokens y el portfolio, con sus pantallas. Para esa funcionalidad el equipo
  ya decidió que el administrador solo inicializa el mercado vendiendo cada token (1 %) a 1
  crédito y nunca recompra, y que después las órdenes se ejecutan entre usuarios, al precio
  actual del token del jugador.

Reglas de cálculo que el equipo propuso con el pedido, a confirmar:

- Q: Qué estado tienen las reglas de cálculo (semana, vigencias, pares, métricas,
  normalización, score, valor mínimo, estrategias de arranque, cambio de la estrategia
  activa, recálculo y simulación)? -> A: El equipo las propuso y este spec las escribe como
  requisitos tal cual. Quedan a confirmar o ajustar en la clarificación. Los huecos que el
  pedido no cubría y que este spec resolvió con un valor por defecto figuran en Assumptions,
  para que el equipo los revise junto con las reglas.

Pendiente antes de implementar (no es una duda de comportamiento, es una puerta del proceso):

- Q: Se pueden modificar los tests del frontend de la 004 que hoy prohíben mostrar precios,
  valores o puntajes (`NoPurchaseActions.test.tsx`, CE-009)? -> A: El equipo decidió que
  deben modificarse, porque esta funcionalidad muestra cotizaciones. El Principio IV de la
  constitución exige el "sí" explícito del equipo registrado acá antes de implementar. **El "sí"
  quedó registrado el 2026-10-15 (Valentin), en la sesión de abajo.** El plan lista qué tests y qué
  aserciones cambian y por qué (FR-072).

### Session 2026-10-15

- Q: Cuando se recotiza en el acto la última semana cerrada, ¿el cierre de la semana siguiente
  parte del valor recotizado o del valor que el jugador tenía a las 00:00 del lunes? → A: Parte
  del valor recotizado. El valor base de una semana es la última cotización del jugador (por
  vigencia) de las semanas anteriores a esa semana, incluidas las recotizadas.
- Q: ¿Los pares de un jugador son los de su misma posición de las cinco ligas juntas, o solo los de
  su misma liga? → A: De las cinco ligas juntas (confirma FR-017 y su Assumption).
- Q: ¿Las respuestas de la API y las pantallas muestran el score de cada cotización? → A: No. El
  score se guarda (FR-003) pero ninguna respuesta ni pantalla lo expone (confirma FR-006 y Fuera
  de Alcance).
- Q: ¿Se confirma la fórmula aditiva con factor 10 y mínimo 10,00, sabiendo que una simulación
  sintética (600 jugadores, 38 semanas, datos inventados) dejó a un 22 % de los jugadores en el piso
  a las 38 semanas? → A: Sí, se confirma la aditiva (FR-024 y FR-025). El factor y el mínimo se
  recalibran con la simulación sobre datos reales; no se adopta la variante proporcional.
- Q: ¿Se autoriza modificar los tests de la 004 que prohíben mostrar precios y valores? → A: Sí.
  **"Sí" explícito del equipo (Principio IV), dado por Valentin el 2026-10-15.** Alcanza solo a los
  cambios que lista el plan (FR-072): quitar `precio` y `valor` de las expresiones de
  `NoPurchaseActions.test.tsx`, `PostIt.test.tsx` y `PlayerPage.test.tsx`, actualizar sus
  descripciones, y sumar los handlers de cotizaciones a `tests/mocks/handlers.ts`. No autoriza
  borrar tests ni cambiar otras aserciones.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Cotización semanal de todos los jugadores (Priority: P1)

Un administrador dispara el recálculo. El sistema cotiza, semana cerrada por semana cerrada
y en orden, a todos los jugadores activos de las cinco ligas con la estrategia activa: cada
jugador arranca en 100,00 y, cada semana, su valor sube o baja según cómo rindió frente a los
jugadores de su misma posición. Cada cotización se guarda sin pisar las anteriores y el
administrador recibe un informe de lo que se hizo.

**Why this priority**: Es el núcleo de la Entrega 2. Sin cotización no hay mercado, ni
ranking, ni nada que mostrar. Con la estrategia A de arranque ya activa, esta historia entrega
valor sola: los jugadores tienen valor y evolución.

**Independent Test**: Se prueba con un catálogo, partidos y estadísticas de varias semanas
ya cargados (datos de prueba, sin consultar ninguna fuente), disparando el recálculo como
administrador y verificando el historial de algunos jugadores contra los ejemplos del spec
(normalización de cinco delanteros, score 0,30, 100,00 → 103,00 → 101,00, mínimo de 10,00),
que una semana sin partidos no mueve ningún precio y que repetir el recálculo no cambia nada.

**Acceptance Scenarios**:

1. **Given** un catálogo con jugadores activos, estadísticas de tres semanas cerradas y
   ninguna cotización guardada, **When** un administrador dispara el recálculo, **Then** se
   cotizan las tres semanas en orden cronológico **And** cada jugador activo queda con una
   cotización inicial de 100,00 y una calculada por semana **And** el informe lista las tres
   semanas.
2. **Given** cinco delanteros que hicieron 0; 0; 1; 2 y 3 goles en la semana, **When** se
   normaliza la métrica goles entre ellos, **Then** valen −0,6; −0,6; 0,0; +0,4 y +0,8, y su
   promedio es 0.
3. **Given** la estrategia A y un jugador cuyas métricas de la semana son goles +0,8;
   asistencias +0,4; tiros 0; pases clave 0; regates −0,4; quites −0,4 y rating +0,6,
   **When** se cotiza su primera semana, **Then** su score es 0,30 y su cotización pasa de
   100,00 a 103,00 **And** el precio por token pasa de 1,0000 a 1,0300.
4. **Given** ese jugador en 103,00 y una segunda semana con score −0,20, **When** se cotiza,
   **Then** su cotización pasa a 101,00.
5. **Given** un jugador con score 0 en la semana, **When** se cotiza, **Then** su valor no
   cambia **And** se guarda igual una cotización nueva de esa semana con el mismo valor.
6. **Given** un jugador en 12,00, un score de −0,50, un factor de escala de 10 y un mínimo de
   10,00, **When** se cotiza, **Then** su cotización queda en 10,00 y no en 7,00.
7. **Given** una semana sin ningún partido en las cinco ligas, **When** se cotiza, **Then**
   todos los pares empatan en todas las métricas, todos los scores valen 0 y ninguna
   cotización cambia de valor.
8. **Given** un delantero activo que no jugó esa semana mientras otros delanteros sí
   convirtieron goles, **When** se cotiza la semana, **Then** se lo cotiza igual, con sus
   totales en 0 comparados contra los de sus pares, y su valor puede cambiar.
9. **Given** un jugador inactivo, **When** se cotiza una semana, **Then** no se le guarda
   ninguna cotización y conserva la última **And** si vuelve a estar activo, la semana
   siguiente que se cotice parte de esa última cotización.
10. **Given** una semana cerrada con un partido terminado que todavía no tiene estadísticas y
    que cerró hace menos de 7 días, **When** se dispara el recálculo, **Then** esa semana no
    se cotiza, tampoco las siguientes, **And** el informe las lista como pendientes con su
    motivo.
11. **Given** esa misma semana, ya con 7 días o más desde su cierre y el partido todavía sin
    estadísticas, **When** se dispara el recálculo, **Then** se cotiza con las estadísticas
    que haya.
12. **Given** la semana en curso (todavía no cerró), **When** se dispara el recálculo,
    **Then** no se cotiza.
13. **Given** todas las semanas cerradas ya cotizadas, **When** se repite el recálculo,
    **Then** no se crea ninguna cotización nueva **And** el informe indica que no había
    semanas para cotizar, sin tratarlo como un error.
14. **Given** una base sin cotizaciones y estadísticas desde una semana W, **When** corre la
    primera ejecución, **Then** reconstruye el historial semana por semana desde W **And**
    la cotización inicial de cada jugador rige desde el lunes 00:00 de W **And** la calculada
    de cada semana rige desde el cierre de esa semana.
15. **Given** los mismos datos y la misma estrategia, **When** se cotizan dos bases
    idénticas, **Then** las cotizaciones resultantes son idénticas.
16. **Given** un usuario con rol de usuario común, **When** intenta disparar el recálculo,
    **Then** el sistema lo rechaza por falta de permiso y no cotiza nada **And** si la
    petición viene sin credencial, la rechaza por falta de credencial.

---

### User Story 2 - Consultar cotizaciones (Priority: P2)

Cualquier persona, sin iniciar sesión, consulta la cotización actual de un jugador, la que
tenía en una fecha, su evolución en un rango de fechas y el ranking de jugadores por
cotización, con filtros de liga, equipo y posición. Todas las respuestas informan el valor
del 100 % del jugador y el precio por token.

**Why this priority**: Es lo que hace visible la cotización, para el frontend y para la
funcionalidad de mercado que viene después. Necesita de la P1 para tener datos.

**Independent Test**: Se prueba con jugadores que ya tienen un historial de cotizaciones
(sembrado por la P1 o por datos de prueba), consultando cada recurso sin credencial y
verificando valores, variaciones, orden del ranking, filtros, paginación y los casos sin
cotización.

**Acceptance Scenarios**:

1. **Given** un jugador con cotización de 103,00, **When** se consulta su cotización actual,
   **Then** la respuesta informa el valor 103,00, el precio por token 1,0300, desde cuándo
   rige, la semana, el tipo, y la estrategia y la versión con que se calculó.
2. **Given** un jugador cuya cotización pasó de 100,00 a 103,00, **When** se consulta la
   actual, **Then** la variación es de +3,00 créditos y +3,00 %.
3. **Given** un jugador con una única cotización (la inicial), **When** se consulta la
   actual, **Then** no informa variación.
4. **Given** un jugador del catálogo sin ninguna cotización, **When** se consulta la actual,
   **Then** el sistema responde que el jugador no tiene cotización, de forma distinguible de
   un jugador que no existe.
5. **Given** un identificador que no corresponde a ningún jugador, **When** se consulta su
   cotización, **Then** el sistema responde que el jugador no fue encontrado.
6. **Given** un jugador con cotización inicial de 100,00 desde el lunes 17/08/2026,
   calculada de 103,00 desde el lunes 24/08/2026 y calculada de 101,00 desde el lunes
   31/08/2026, **When** se consulta la cotización al 20/08/2026, al 24/08/2026 y al
   16/08/2026, **Then** responde 100,00, 103,00 y que no hay cotización para esa fecha,
   respectivamente.
7. **Given** una fecha futura o con formato inválido, **When** se consulta la cotización a
   esa fecha, **Then** el sistema rechaza el pedido indicando el valor no admitido.
8. **Given** un jugador con historial, **When** se consulta su evolución con un rango de
   fechas, **Then** recibe una página con los puntos de ese rango (ambos extremos incluidos),
   cada uno con su semana, desde cuándo rige, valor, precio por token, variación, tipo,
   estrategia y versión **And** los datos de paginación del catálogo.
9. **Given** un rango de fechas con el extremo inicial posterior al final, **When** se
   consulta la evolución, **Then** el sistema rechaza el pedido indicando el valor no
   admitido.
10. **Given** una semana recotizada en el acto, **When** se consulta la evolución, **Then**
    figuran las dos cotizaciones de esa semana, la calculada original y la recotizada, cada
    una con su tipo, estrategia y versión.
11. **Given** jugadores activos con cotización, **When** se consulta el ranking, **Then**
    vienen ordenados por cotización vigente descendente, cada uno con su valor, precio por
    token, variación semanal y la estrategia y la versión de su última cotización **And** la
    respuesta informa la estrategia activa.
12. **Given** el ranking, **When** se filtra por liga, por equipo, por posición o por
    combinaciones, **Then** solo aparecen jugadores que cumplen todos los filtros, con la
    misma paginación del catálogo.
13. **Given** un jugador inactivo con cotización, **When** se consulta el ranking, **Then**
    no aparece **And** su cotización actual se puede consultar por identificador.
14. **Given** un filtro del ranking con un valor no admitido (por ejemplo, una posición
    inexistente), **When** se consulta, **Then** el sistema rechaza el pedido indicando el
    valor no admitido, igual que el catálogo.
15. **Given** WhoScored y Football-Data sin responder, **When** se consulta cualquier
    cotización o el ranking, **Then** responden igual que con las fuentes disponibles.

---

### User Story 3 - Ver las cotizaciones en la aplicación web (Priority: P3)

Un visitante o un usuario abre la aplicación y ve la cotización de cada jugador: en la
pizarra, en la ficha del jugador (con un gráfico de su evolución semanal y un selector de
rango de fechas) y en una pantalla de ranking con filtros. Todo con la estética de la pizarra
táctica, usable desde 360 px de ancho y accesible.

**Why this priority**: Es lo que ve la persona usuaria y lo que se muestra en la entrega. Se
construye sobre la P2.

**Independent Test**: Se prueba con la API simulada, recorriendo la pizarra, la ficha y el
ranking sin sesión, y verificando valores, variaciones, filtros, paginación, gráfico, rango
de fechas, estados de carga, de error y de jugador sin cotización, a 360 px y con teclado.

**Acceptance Scenarios**:

1. **Given** la pizarra con jugadores que tienen cotización, **When** se abre, **Then** cada
   jugador muestra su cotización actual.
2. **Given** un jugador de la pizarra sin cotización, **When** se abre la pizarra, **Then** su
   tarjeta lo indica sin mostrar un valor inventado y la pizarra sigue funcionando.
3. **Given** la ficha de un jugador con cotización, **When** se abre, **Then** muestra el
   valor del jugador, el precio por token y la variación respecto de la semana anterior.
4. **Given** la ficha de un jugador, **When** se mira el gráfico de evolución, **Then**
   muestra un punto por semana **And** cada punto indica su valor, su semana, y la
   estrategia y la versión con que se calculó.
5. **Given** la ficha de un jugador, **When** se elige otro rango de fechas, **Then** el
   gráfico muestra solo los puntos de ese rango.
6. **Given** un rango sin ninguna cotización, **When** se elige, **Then** el gráfico lo
   indica con un mensaje y no se rompe.
7. **Given** la ficha de un jugador sin cotización, **When** se abre, **Then** muestra el
   estado de "jugador sin cotización" y los datos del catálogo siguen visibles.
8. **Given** la API que tarda o falla, **When** se abre la ficha, la pizarra o el ranking,
   **Then** se muestra un estado de carga mientras espera y, si falla, un estado de error con
   forma de reintentar.
9. **Given** la pantalla de ranking, **When** se abre, **Then** lista los jugadores por
   cotización descendente, con cotización, variación semanal, y estrategia y versión usadas.
10. **Given** la pantalla de ranking, **When** se elige liga, equipo o posición, **Then** la
    lista se acota, la paginación se reinicia **And** los filtros y la página quedan en la
    dirección de la pantalla, de modo que recargar o compartir la dirección reproduce la
    misma vista.
11. **Given** un visitante sin sesión, **When** recorre la pizarra, la ficha, la evolución y
    el ranking, **Then** puede ver todo sin iniciar sesión.
12. **Given** cualquiera de esas pantallas en un ancho de 360 px, **When** se usa, **Then**
    no hay desplazamiento horizontal de la página y todo es accesible.
13. **Given** cualquiera de esas pantallas, **When** se navega solo con teclado, **Then**
    todo lo clickeable es un botón o un enlace con foco visible **And** el gráfico ofrece una
    alternativa en texto con los mismos puntos.

---

### User Story 4 - Registrar y configurar estrategias de valuación (Priority: P4)

Un administrador crea estrategias de valuación, las lista con todas sus versiones, las edita
(lo que crea una versión nueva) y activa la que quiere. Una estrategia tiene un nombre único,
un factor de escala, una cotización mínima y los pesos de cada métrica: un juego por defecto
y, opcionalmente, uno por posición. El sistema arranca con dos estrategias ya cargadas.

**Why this priority**: Permite variar cada parámetro de la valuación, como pide el equipo. Es
P4 porque con la estrategia A de arranque ya activa, la P1 funciona sin esta historia.

**Independent Test**: Se prueba como administrador creando, editando, listando y activando
estrategias válidas e inválidas, y verificando versiones, la activa única, los rechazos y que
ninguna cotización cambia por crear o editar una estrategia que no es la activa.

**Acceptance Scenarios**:

1. **Given** una base recién arrancada, **When** un administrador lista las estrategias,
   **Then** están A "Rendimiento de partido" (activa) y B "Equilibrada por posición", cada una
   en su versión 1 **And** A pesa goles 0,25; asistencias 0,15; tiros 0,10; pases clave 0,10;
   regates 0,10; quites 0,10 y rating 0,20, para todas las posiciones.
2. **Given** una aplicación que arranca de nuevo, **When** termina de arrancar, **Then** las
   estrategias de arranque no se duplican ni se modifican, tampoco si el administrador las
   editó.
3. **Given** un administrador, **When** crea una estrategia válida con nombre, factor de
   escala, mínimo y pesos por defecto, **Then** queda guardada como versión 1, inactiva,
   **And** ninguna cotización cambia.
4. **Given** una estrategia con pesos por defecto y pesos propios para delanteros, **When**
   se cotiza a un delantero y a un defensor, **Then** el delantero usa los pesos de
   delanteros y el defensor usa los por defecto.
5. **Given** una estrategia que omite el factor de escala y el mínimo, **When** se crea,
   **Then** queda con factor 10 y mínimo 10,00.
6. **Given** un pedido de crear una estrategia con pesos que no suman 1,00, **When** se
   envía, **Then** el sistema lo rechaza indicando que los pesos deben sumar 1,00 y no crea
   nada. Lo mismo ocurre con un peso negativo, una métrica desconocida, un factor de escala
   menor o igual a 0, un mínimo menor a 0,01 o un nombre repetido (este último, como
   conflicto).
7. **Given** una estrategia inactiva, **When** un administrador la edita, **Then** se crea su
   versión siguiente **And** la versión anterior queda intacta **And** la estrategia activa
   no cambia **And** ninguna cotización cambia.
8. **Given** la estrategia activa, **When** un administrador la edita, **Then** se crea su
   versión siguiente y esa versión pasa a ser la activa (el efecto sobre las cotizaciones lo
   define la historia 5).
9. **Given** cotizaciones calculadas con la versión 1 de A y una edición posterior de A,
   **When** se consulta su historial, **Then** esas cotizaciones conservan la versión 1.
10. **Given** cualquier estado del sistema, **When** se lista las estrategias, **Then**
    exactamente una está activa.
11. **Given** un administrador, **When** lista las estrategias, **Then** ve cada una con
    todas sus versiones y cuál es la activa, con su versión.
12. **Given** un usuario de rol común, **When** intenta crear, editar, activar o listar
    estrategias, **Then** el sistema lo rechaza por falta de permiso; **And** sin credencial,
    por falta de credencial.
13. **Given** la estrategia B, **When** se revisan sus pesos, **Then** son distintos en al
    menos dos posiciones, suman 1,00 en cada una e incluyen al menos una métrica de
    disciplina y una de contexto.

---

### User Story 5 - Cambiar la estrategia activa y elegir cuándo recotizar (Priority: P5)

Al cambiar la estrategia activa, el administrador elige entre dos caminos: recotizar ya, o
cambiar solo la estrategia y que la use el próximo cierre semanal. Si no elige, se aplica el
segundo.

**Why this priority**: Es el control que el equipo pidió sobre el impacto del cambio. Necesita
las historias 1 y 4.

**Independent Test**: Se prueba con jugadores que ya tienen historial, activando otra
estrategia con cada una de las dos opciones y verificando qué cotizaciones se agregan, desde
qué precio se calculan, que las anteriores se conservan y que repetir no acumula.

**Acceptance Scenarios**:

1. **Given** la estrategia A activa, **When** un administrador activa B sin elegir opción,
   **Then** solo cambia la activa **And** ninguna cotización cambia **And** el próximo
   cierre semanal usa B.
2. **Given** lo mismo con la opción "la semana siguiente", **Then** el resultado es el mismo.
3. **Given** un jugador con inicial 100,00, calculada 103,00 y calculada 101,00 con A (la
   segunda semana con score −0,20), y B con score +0,10 para esa segunda semana y factor 10,
   **When** un administrador activa B con "recotizar ahora", **Then** el jugador recibe una
   cotización recotizada de 104,00 para esa semana (parte de 103,00, el precio previo a
   esa semana, y no de 101,00) **And** rige desde ese momento **And** la calculada de 101,00
   se conserva en el historial.
4. **Given** esa recotización ya hecha, **When** el administrador la repite, **Then** no se
   crea ninguna cotización nueva y el jugador sigue en 104,00.
5. **Given** que otro cambio deja activa una tercera estrategia C y se elige "recotizar
   ahora", **When** se aplica, **Then** la semana se recotiza de nuevo con C partiendo de
   103,00 y no de 104,00: no se acumula.
6. **Given** que la última semana cerrada ya fue cotizada con la estrategia nueva, **When**
   se la activa con "recotizar ahora", **Then** no se repite ninguna cotización.
7. **Given** semanas cerradas pendientes de cotizar, **When** se activa otra estrategia con
   "recotizar ahora", **Then** esas semanas se cotizan con la estrategia nueva, según las
   reglas del recálculo **And** la última semana cerrada, si ya estaba cotizada con otra, se
   vuelve a cotizar con la nueva.
8. **Given** la estrategia activa, **When** un administrador la edita eligiendo "recotizar
   ahora", **Then** se aplica lo mismo, con la versión nueva.
9. **Given** una estrategia nueva o una estrategia inactiva editada, **When** se envía
   cualquier opción de recotización, **Then** se ignora y no se toca ninguna cotización.
10. **Given** que se activa la estrategia que ya está activa en la versión que cotizó la
    última semana, **When** se envía, **Then** no cambia nada.
11. **Given** un recálculo o una sincronización en curso, **When** un administrador cambia la
    activa con "recotizar ahora", **Then** el sistema responde con un conflicto **And** el
    cambio no se aplica: ni la activa ni las cotizaciones cambian.
12. **Given** un recálculo en curso, **When** un administrador cambia la activa eligiendo "la
    semana siguiente", **Then** el cambio se aplica **And** el recálculo en curso termina con
    la estrategia que tenía al empezar.
13. **Given** un usuario de rol común, **When** intenta activar otra estrategia, **Then** el
    sistema lo rechaza por falta de permiso y no cambia nada.
14. **Given** un sistema sin ninguna cotización todavía, **When** se activa otra estrategia,
    **Then** cambia la activa y no hay nada que recotizar.
15. **Given** un jugador con una recotizada de 104,00 de la última semana cerrada, hecha durante
    la semana siguiente, y un score de +0,20 en esa semana siguiente con factor 10, **When**
    cierra esa semana siguiente, **Then** su cotización calculada pasa a 106,00 (parte de la
    recotizada de 104,00 y no de la calculada anterior de 101,00).

---

### User Story 6 - Recálculo automático y convivencia con las sincronizaciones (Priority: P6)

El recálculo corre solo todos los lunes a las 06:00 (hora de Argentina), después de las
sincronizaciones de las 04:00, y al arrancar la aplicación si hay semanas pendientes. Nunca
corren dos recálculos a la vez ni uno junto con una sincronización. Si las fuentes externas
fallan, el recálculo trabaja con lo ya guardado.

**Why this priority**: Automatiza lo que la P1 permite hacer a mano. El sistema es utilizable
sin esto, disparando el recálculo manualmente.

**Independent Test**: Se prueba llevando el reloj al lunes a las 06:00, arrancando con y sin
semanas pendientes, y disparando un recálculo con otro o una sincronización en curso.

**Acceptance Scenarios**:

1. **Given** la aplicación encendida, **When** llega el lunes a las 06:00 hora de Argentina,
   **Then** arranca sola un recálculo **And** su informe queda en el registro de la
   aplicación.
2. **Given** semanas cerradas sin cotizar, **When** la aplicación arranca, **Then** termina
   de arrancar sin esperar al recálculo **And** se dispara solo en segundo plano, cuyo
   informe queda en el registro.
3. **Given** que no hay semanas cerradas sin cotizar, **When** la aplicación arranca,
   **Then** no se dispara ningún recálculo.
4. **Given** una sincronización en curso (de Football-Data o de WhoScored), **When** llega el
   lunes a las 06:00 o la aplicación arranca con semanas pendientes, **Then** el recálculo se
   omite **And** el motivo queda en el registro de la aplicación.
5. **Given** una sincronización en curso, **When** un administrador dispara el recálculo,
   **Then** el sistema lo rechaza informando el conflicto.
6. **Given** un recálculo en curso, **When** un administrador dispara otro, **Then** el
   sistema lo rechaza informando que ya hay uno en curso.
7. **Given** la aplicación apagada el lunes a las 06:00, **When** se vuelve a encender,
   **Then** la semana perdida se cotiza en el arranque, porque queda pendiente.
8. **Given** WhoScored y Football-Data sin responder, **When** corre el recálculo, **Then**
   cotiza con lo ya guardado **And** no consulta ninguna fuente.
9. **Given** un recálculo, **When** termina, **Then** el informe indica las semanas cotizadas,
   los jugadores con cotización inicial, calculada y recotizada de cada una, y las semanas
   pendientes con su motivo.
10. **Given** un recálculo en curso, **When** alguien consulta cotizaciones o el ranking,
    **Then** ve lo de antes o lo de después de cada semana, nunca una semana a medias.
11. **Given** una semana cuya cotización falla a la mitad, **When** el recálculo termina con
    error, **Then** de esa semana no queda ninguna cotización guardada **And** el recálculo
    siguiente la vuelve a intentar.

---

### User Story 7 - Simular una estrategia sin guardar nada (Priority: P7)

Un administrador prueba cómo quedarían las cotizaciones con una estrategia guardada o con
parámetros que envía, sin tocar nada. El sistema reproduce todas las semanas cerradas desde
100,00 y devuelve, por jugador, su cotización actual y la simulada.

**Why this priority**: Ayuda al administrador a decidir antes de cambiar la activa. Es
opcional para que el sistema funcione.

**Independent Test**: Se prueba como administrador simulando una estrategia guardada y una
con parámetros, verificando que las cotizaciones, las estrategias y la activa son idénticas
antes y después, que dos simulaciones iguales dan el mismo resultado y que la simulación de
la estrategia activa sobre un historial calculado solo con ella coincide con lo vigente.

**Acceptance Scenarios**:

1. **Given** un historial de cotizaciones, **When** un administrador simula una estrategia
   guardada, **Then** recibe, por jugador, su cotización actual y la simulada **And** nada de
   lo guardado cambia (cotizaciones, estrategias y activa).
2. **Given** parámetros enviados (factor, mínimo y pesos), **When** se simula, **Then** se
   calcula con ellos sin guardarlos como estrategia.
3. **Given** parámetros inválidos (pesos que no suman 1,00, métrica desconocida y demás
   reglas de las estrategias), **When** se simula, **Then** el sistema los rechaza con las
   mismas reglas que al crear una estrategia.
4. **Given** una estrategia guardada que no existe, **When** se simula, **Then** el sistema
   responde que no fue encontrada.
5. **Given** un pedido que trae a la vez una estrategia guardada y parámetros, o ninguno de
   los dos, **When** se simula, **Then** el sistema lo rechaza.
6. **Given** el mismo pedido y los mismos datos, **When** se simula dos veces, **Then** las
   dos respuestas son idénticas.
7. **Given** un historial cotizado íntegramente con la versión vigente de la estrategia
   activa, **When** se simula esa estrategia, **Then** la cotización simulada de cada jugador
   coincide con la actual.
8. **Given** la simulación, **When** se pide con filtros de liga, equipo y posición, **Then**
   solo devuelve esos jugadores, con la paginación del catálogo.
9. **Given** un usuario de rol común, **When** intenta simular, **Then** el sistema lo
   rechaza por falta de permiso; **And** sin credencial, por falta de credencial.

---

### User Story 8 - Administración desde la aplicación web (Priority: P8)

Un administrador entra a una sección de administración de la aplicación web. Desde ahí lista
las estrategias, crea y edita, activa una y elige cuándo recotizar, dispara el recálculo y ve
su informe, y simula una estrategia comparándola con la actual. Nadie más la ve.

**Why this priority**: Pone en la aplicación lo que las historias 4 a 7 hacen por API. Es la
última capa y el administrador podría usar la API mientras tanto.

**Independent Test**: Se prueba con la API simulada, iniciando sesión como administrador y
como usuario común, y recorriendo cada función de la sección, y verificando que quien no es
administrador no ve la sección ni puede usarla entrando por la dirección.

**Acceptance Scenarios**:

1. **Given** un usuario con rol de usuario común, **When** mira la aplicación, **Then** no
   hay ninguna entrada a la administración **And** si entra por su dirección, recibe un aviso
   de falta de permiso y no ve ningún control de administración.
2. **Given** un visitante sin sesión, **When** entra por la dirección de la administración,
   **Then** lo lleva a iniciar sesión, igual que el resto de las pantallas privadas **And**
   no ve ningún control de administración.
3. **Given** un administrador con sesión, **When** abre la administración, **Then** ve la
   lista de estrategias con sus versiones y cuál es la activa.
4. **Given** un administrador, **When** crea una estrategia con nombre, factor, mínimo y
   pesos, **Then** la aplicación le muestra la suma de los pesos mientras los completa, no
   deja enviar un formulario cuyos pesos no suman 1,00, y al guardar la estrategia aparece en
   la lista.
5. **Given** que el servidor rechaza una estrategia, **When** el administrador la envía,
   **Then** la aplicación muestra el motivo en español y conserva lo que escribió.
6. **Given** un administrador, **When** edita una estrategia, **Then** se crea una versión
   nueva y la lista la muestra.
7. **Given** un administrador, **When** cambia la estrategia activa, **Then** debe elegir
   entre "Recotizar ahora" y "Recotizar la semana siguiente", con una explicación de qué hace
   cada una; la segunda viene preseleccionada; **And** nada cambia hasta que confirma.
8. **Given** un administrador, **When** dispara el recálculo, **Then** ve el informe con las
   semanas cotizadas, los jugadores con cotización inicial, calculada y recotizada, y las
   semanas pendientes con su motivo.
9. **Given** un recálculo o una sincronización en curso, **When** el administrador dispara
   el recálculo o un cambio con "recotizar ahora", **Then** la aplicación muestra el motivo
   del conflicto y no pierde lo que el administrador estaba haciendo.
10. **Given** un administrador, **When** simula una estrategia guardada o parámetros,
    **Then** ve una tabla con la cotización actual, la simulada y la diferencia de cada
    jugador, con los filtros de liga, equipo y posición y la paginación.
11. **Given** cualquiera de las pantallas de administración, **When** la API tarda o falla,
    **Then** muestra estados de carga y de error.
12. **Given** cualquiera de las pantallas de administración, **When** se usa a 360 px de
    ancho o solo con teclado, **Then** es usable y accesible, como las demás pantallas.

---

### Edge Cases

- Un jugador activo que no jugó ningún minuto se cotiza igual: sus totales valen 0 y se
  comparan con los de sus pares. En las métricas que premian tener menos (faltas, tarjetas),
  esos 0 lo dejan en la mejor posición. Es una consecuencia de las reglas propuestas, no un
  error; el equipo la puede revisar.
- Un jugador es el único de su posición: su grupo de pares es él solo y todas sus métricas
  valen 0.
- Una métrica que solo existe si el jugador jugó (rating, precisión de pase, resultado del
  equipo) no se puede calcular para quien no jugó, o no tuvo pases, o no tiene rating en
  ninguno de sus partidos: vale 0 para ese jugador y no entra en la comparación de los demás.
- No se sabe de qué equipo era el jugador en un partido (cambió de equipo): su resultado del
  equipo y su valla invicta de ese partido no se pueden calcular y valen 0.
- Un partido de la semana sin rating para un jugador: ese partido no entra en su promedio de
  rating.
- Todos los pares empatan en una métrica: vale 0 para todos.
- El valor calculado cae por debajo del mínimo (o la base está por debajo de un mínimo que
  se subió): la cotización queda en el mínimo.
- Una semana pendiente por falta de estadísticas bloquea las siguientes, porque no se
  saltea ninguna. Si pasan 7 días del cierre, se cotiza con lo que haya y las estadísticas
  que lleguen después no la modifican: cada semana se cotiza una sola vez.
- Un partido terminado de una semana ya cotizada recibe sus estadísticas tarde: no cambia
  esa semana. La corrección solo es posible con la recotización en el acto de la última
  semana cerrada.
- Un jugador que se inactiva y vuelve: no se cotiza mientras está inactivo, conserva la
  última cotización y retoma desde ella. No se reconstruyen las semanas en que estuvo
  inactivo.
- Un jugador que aparece en el catálogo después de que empezó el campeonato: su cotización
  inicial de 100,00 rige desde el inicio de la primera semana que se cotiza para él.
- El administrador cambia la estrategia activa antes de que exista ninguna cotización: se
  cambia la activa y no hay nada que recotizar.
- El administrador edita la estrategia activa dos veces seguidas con "la semana siguiente":
  se crean dos versiones y el próximo cierre usa la última.
- La última semana cerrada está pendiente por falta de estadísticas y se pide "recotizar
  ahora": se cotizan las pendientes que las reglas permiten; la que sigue pendiente queda en
  el informe con su motivo y no hay nada que volver a cotizar de ella.
- Se pide la cotización a una fecha anterior a la primera cotización del jugador: responde
  que no hay cotización. Una fecha futura se rechaza.
- Dos consultas de la cotización actual de un jugador durante un recálculo pueden dar
  distinto resultado si el recálculo termina entre las dos, pero nunca una semana a medias.
- Un recálculo termina con error a la mitad de una semana: de esa semana no queda nada
  guardado y se reintenta en el siguiente.

## Requirements *(mandatory)*

### Functional Requirements

#### Cotización: qué es y cómo se guarda

- **FR-001**: La cotización de un jugador MUST ser el valor del 100 % del jugador, en
  créditos, con dos decimales. Sus 100 tokens son ese 100 %: cada token es el 1 % y vale la
  cotización dividida 100.
- **FR-002**: Toda respuesta que informe una cotización MUST incluir el valor del jugador (2
  decimales) y el precio por token (4 decimales; el valor dividido 100, sin otro redondeo).
  El sistema MUST NOT redondear a créditos enteros: ese redondeo se hace en la
  funcionalidad de mercado sobre el total que paga o recibe un usuario.
- **FR-003**: Por cada semana que se cotiza, el sistema MUST guardar una cotización por cada
  jugador activo, también cuando el valor no cambia, y además la cotización inicial de los
  jugadores que se cotizan por primera vez (FR-015). Cada cotización MUST registrar el
  jugador, la semana, desde cuándo rige, el valor, el score, el tipo, la estrategia y su
  versión.
- **FR-004**: El tipo de una cotización MUST ser uno de tres: **inicial** (el 100,00 de
  partida), **calculada** (la del cierre semanal) o **recotizada** (la que reemplaza a la de
  la última semana cerrada por un cambio de estrategia en el acto).
- **FR-005**: Las cotizaciones MUST NOT modificarse ni borrarse. Una recotización agrega
  cotizaciones nuevas y conserva las anteriores en el historial.
- **FR-006**: Ninguna respuesta MUST exponer estadísticas crudas de un jugador (goles,
  minutos, pases, tarjetas y demás), sus métricas normalizadas ni el score de una cotización.

#### Semanas, vigencias y orden

- **FR-007**: Una semana MUST ir del lunes 00:00 al lunes 00:00 siguiente, hora de
  Argentina. Un partido pertenece a la semana en que cae su fecha y hora, en hora de
  Argentina.
- **FR-008**: El sistema MUST cotizar solo semanas cerradas, en orden cronológico y sin
  saltear ninguna. Cada semana MUST cotizarse una sola vez; la única excepción es la
  recotización en el acto de la última semana cerrada (FR-036).
- **FR-009**: La cotización calculada de una semana MUST regir desde el cierre de esa semana.
  La cotización inicial de 100,00 MUST regir desde el lunes 00:00 de la primera semana que se
  cotiza para ese jugador. La recotizada MUST regir desde el momento en que se hace.
- **FR-010**: La primera ejecución MUST reconstruir el historial semana por semana, desde la
  primera semana que tiene estadísticas hasta la última cerrada.
- **FR-011**: Una semana MUST NOT cotizarse mientras falten estadísticas de partidos
  terminados de esa semana, salvo que hayan pasado 7 días desde su cierre: entonces MUST
  cotizarse con las estadísticas que haya. Una semana pendiente MUST impedir que se coticen
  las siguientes.
- **FR-012**: Cada semana MUST cotizarse completa o no cotizarse: si falla a la mitad, no
  queda ninguna cotización suya guardada.

#### Quiénes se cotizan

- **FR-013**: El sistema MUST cotizar a todos los jugadores activos de las cinco ligas cada
  semana, hayan jugado poco, mucho o nada.
- **FR-014**: Un jugador inactivo MUST NOT cotizarse. MUST conservar su última cotización,
  que se puede consultar por identificador, y si vuelve a estar activo MUST retomar desde
  ella.
- **FR-015**: Un jugador activo sin ninguna cotización MUST recibir, en la primera semana que
  se cotiza para él, su cotización inicial de 100,00 y, al cierre de esa misma semana, su
  calculada con base 100,00.

#### Métricas y normalización

- **FR-016**: Las métricas, todas de la semana, MUST ser las siguientes. Más es mejor: goles,
  asistencias, tiros, tiros al arco, pases clave, regates exitosos, quites, intercepciones,
  despejes, duelos aéreos ganados, pases y minutos jugados (todas, la suma de la semana);
  rating (promedio ponderado por minutos); precisión de pase (pases precisos sobre pases de
  la semana); resultado del equipo (promedio de victoria 1, empate 0,5 y derrota 0 en los
  partidos que el jugador jugó); valla invicta (cantidad de partidos que jugó con la valla
  de su equipo invicta). Menos es mejor: faltas cometidas, tarjetas amarillas y tarjetas
  rojas. Salen de las estadísticas por jugador y partido (006) y de los resultados de los
  partidos (005).
- **FR-017**: Los pares de un jugador MUST ser los jugadores activos de su misma posición,
  hayan jugado o no esa semana.
- **FR-018**: Cada métrica MUST expresarse entre −1 y +1 según la posición relativa del
  jugador entre sus pares: posición relativa = proporción de pares que tuvieron menos que él,
  más la mitad de los que tuvieron igual (él incluido); métrica = 2 × posición relativa − 1.
  Un 0 es el rendimiento promedio de sus pares, +1 el mejor y −1 el peor.
- **FR-019**: Si todos los pares empatan en una métrica, esa métrica MUST valer 0 para todos.
- **FR-020**: En las métricas donde menos es mejor, el orden MUST invertirse: el que tuvo
  menos queda mejor.
- **FR-021**: Si una métrica no se puede calcular para un jugador (no jugó, no tuvo pases, no
  tiene rating, o no se sabe de qué equipo era en ese partido), esa métrica MUST valer 0 para
  él y MUST NOT entrar en la comparación de los demás pares.
- **FR-022**: Un jugador sin estadísticas porque la integración con WhoScored no pudo
  cruzarlo MUST contar como un jugador que no jugó.

#### Score y valor

- **FR-023**: El score de un jugador MUST ser la suma, sobre las métricas, de peso × métrica,
  con los pesos de la estrategia para su posición (los de la posición si los tiene y, si no,
  los por defecto). Como los pesos son mayores o iguales a 0 y suman 1,00, el score queda
  entre −1 y +1.
- **FR-024**: El valor MUST ser valorBase + score × factorEscala, redondeado una sola vez a
  2 decimales, y MUST NOT ser menor que la cotización mínima de la estrategia. El valorBase
  es 100,00 la primera vez y después es el valor anterior del jugador: el de su última
  cotización (por vigencia) de las semanas anteriores a la que se cotiza, incluidas las
  recotizadas. Una recotizada de la última semana cerrada, hecha durante la semana siguiente,
  es entonces la base del cierre de esa semana siguiente. El valor puede bajar, y la baja
  máxima por semana es el factor de escala.
- **FR-025**: El factor de escala por defecto MUST ser 10 (como máximo ±10 créditos por
  semana sobre el 100 % del jugador, o ±0,10 por token) y la cotización mínima por defecto
  MUST ser 10,00 (0,10 por token). Valen cuando una estrategia no los indica.
- **FR-026**: El cálculo MUST ser determinista: los mismos datos y la misma estrategia dan
  siempre las mismas cotizaciones, sin depender del orden en que se procesen los jugadores.

#### Estrategias de valuación

- **FR-027**: Una estrategia MUST tener un nombre único, un factor de escala, una cotización
  mínima y pesos por métrica: un juego por defecto, obligatorio, y opcionalmente uno por
  posición (arquero, defensor, mediocampista, delantero). Una métrica sin peso en un juego
  pesa 0.
- **FR-028**: El sistema MUST rechazar con error de validación: pesos que no suman 1,00;
  pesos negativos; métricas desconocidas; un factor de escala menor o igual a 0; una
  cotización mínima menor a 0,01. MUST rechazar un nombre repetido (sin distinguir
  mayúsculas ni espacios de los extremos) como conflicto.
- **FR-029**: Cada versión de una estrategia MUST ser inmutable. Editar una estrategia MUST
  crear su versión siguiente (1, 2, 3...), y las cotizaciones ya guardadas MUST conservar la
  versión con que se calcularon. Editar cambia el factor, el mínimo y los pesos; el nombre
  no se edita.
- **FR-030**: En todo momento MUST haber exactamente una estrategia activa. Se cambia la
  activa activando otra (queda activa su versión más reciente) o editando la activa (su
  versión nueva pasa a ser la activa). No se puede desactivar sin activar otra ni borrar una
  estrategia.
- **FR-031**: Al arrancar, el sistema MUST dejar cargadas dos estrategias, sin duplicarlas ni
  modificarlas si ya existen: A "Rendimiento de partido", activa, con los pesos goles 0,25;
  asistencias 0,15; tiros 0,10; pases clave 0,10; regates 0,10; quites 0,10 y rating 0,20,
  para todas las posiciones; y B "Equilibrada por posición", con pesos distintos por
  posición, que suman 1,00 en cada una e incluyen al menos una métrica de disciplina (faltas
  cometidas, tarjetas amarillas o rojas) y una de contexto (resultado del equipo o valla
  invicta). Los valores exactos de B los fija el plan.
- **FR-032**: Crear, editar, activar, listar y consultar estrategias MUST ser exclusivo del
  rol administrador. El listado MUST mostrar cada estrategia con todas sus versiones y cuál
  es la activa, con su versión.

#### Cambio de la estrategia activa

- **FR-033**: El cambio de la activa MUST ofrecer opción de recotización cuando deja activa
  una versión distinta de la que cotizó la última semana: al editar la activa o al activar
  otra. Crear una estrategia, o editar una que no es la activa, MUST NOT tocar ninguna
  cotización.
- **FR-034**: El administrador MUST poder elegir entre **recotizar ahora** y **recotizar la
  semana siguiente**. Si no elige, MUST aplicarse la segunda. Fuera de los casos de FR-033
  la opción se ignora.
- **FR-035**: **Recotizar la semana siguiente** MUST cambiar solo la estrategia activa: el
  próximo cierre semanal usa la nueva y no se toca ninguna cotización.
- **FR-036**: **Recotizar ahora** MUST cotizar con la estrategia nueva las semanas cerradas
  pendientes, según las reglas del recálculo, y MUST volver a cotizar con ella la última
  semana cerrada, partiendo del precio que cada jugador tenía antes de esa semana (su última
  cotización vigente de la semana anterior, o 100,00 si es la primera). Las cotizaciones
  resultantes son de tipo recotizada, rigen desde ese momento y las anteriores se conservan.
- **FR-037**: Recotizar ahora MUST NOT acumular: repetirlo, o hacerlo con otra estrategia,
  siempre parte del precio previo a esa semana. Si la última semana ya fue cotizada con la
  versión nueva, MUST NOT repetirse.
- **FR-038**: Recotizar ahora MUST seguir las reglas del recálculo: solo administrador, uno
  a la vez y no durante una sincronización. Si no puede correr, el sistema MUST responder
  con un conflicto (409) y MUST NOT aplicar el cambio de estrategia: no cambia ni la activa
  ni las cotizaciones.
- **FR-039**: Un recálculo en curso MUST terminar con la estrategia activa que tenía al
  empezar, aunque durante su corrida se cambie la activa con "la semana siguiente".

#### Consultas

- **FR-040**: Las consultas de cotización (actual, a una fecha, evolución y ranking) MUST ser
  públicas, como el catálogo.
- **FR-041**: La cotización actual de un jugador MUST informar el valor, el precio por token,
  desde cuándo rige, la semana, el tipo, la estrategia y su versión, y la variación. Un
  jugador sin cotización MUST responder que no tiene cotización, de forma distinguible de uno
  inexistente.
- **FR-042**: La variación de una cotización MUST ser la diferencia, en créditos y en
  porcentaje, contra el valor desde el que se calculó (el de la semana anterior, o 100,00
  para la primera calculada). La inicial MUST NOT informar variación.
- **FR-043**: La cotización a una fecha MUST ser la última que ya regía al final de ese día,
  hora de Argentina. Una fecha anterior a la primera cotización del jugador MUST responder
  que no hay cotización. Una fecha futura o con formato inválido MUST rechazarse.
- **FR-044**: La evolución MUST ser un historial paginado, ordenado por vigencia ascendente,
  con rango de fechas opcional (ambos extremos incluidos) y la paginación del catálogo. Cada
  punto MUST informar semana, desde cuándo rige, valor, precio por token, variación, tipo,
  estrategia y versión. MUST incluir todas las cotizaciones, también las que una
  recotización dejó de vigentes. Un rango con el inicio posterior al final MUST rechazarse.
- **FR-045**: El ranking MUST listar a los jugadores activos con cotización, ordenados por
  cotización vigente descendente (ante empate, por nombre y luego por identificador), con
  filtros opcionales de liga, equipo y posición que se combinan, y la paginación del
  catálogo. Cada fila MUST informar el jugador, su cotización, su variación semanal, y la
  estrategia y la versión de su última cotización. La respuesta MUST informar la estrategia
  activa. Un filtro con valor no admitido MUST rechazarse, igual que en el catálogo.
- **FR-046**: Las consultas MUST NOT depender de WhoScored ni de Football-Data, y MUST ver
  cada semana completa o no verla: nunca una semana a medias.

#### Recálculo

- **FR-047**: El recálculo MUST correr solo los lunes a las 06:00 hora de Argentina, MUST
  correr al arrancar la aplicación, en segundo plano y sin demorar el arranque, si hay
  semanas cerradas sin cotizar, y MUST poder dispararse a mano, solo por un administrador.
  La corrida programada perdida por estar la aplicación apagada se recupera en el arranque
  siguiente.
- **FR-048**: El recálculo MUST devolver un informe con las semanas cotizadas, por cada una
  la cantidad de jugadores con cotización inicial, calculada y recotizada, y las semanas
  pendientes con su motivo (faltan estadísticas de partidos terminados de esa semana, o hay
  una semana anterior pendiente). El informe va al registro de la aplicación y a la
  respuesta del disparo manual; no se guarda.
- **FR-049**: MUST haber a lo sumo un recálculo a la vez. Un disparo manual con otro en
  curso MUST responder con un conflicto (409).
- **FR-050**: El recálculo MUST NOT correr mientras haya una sincronización de Football-Data
  o de WhoScored en curso. El disparo manual MUST responder con un conflicto (409); la
  corrida programada y la de arranque MUST omitirse y dejar el motivo en el registro.
- **FR-051**: El recálculo MUST funcionar con lo ya guardado y MUST NOT consultar ninguna
  fuente externa. Si WhoScored o Football-Data fallan, no lo afecta.
- **FR-052**: Un recálculo sin semanas para cotizar MUST terminar con un informe vacío, sin
  error. Repetir el recálculo MUST NOT crear cotizaciones nuevas.
- **FR-053**: Disparar el recálculo MUST exigir el rol administrador: un usuario común se
  rechaza por falta de permiso y una petición sin credencial, por falta de credencial.

#### Simulación

- **FR-054**: Simular MUST ser exclusivo del rol administrador. El pedido MUST traer, a
  elección, una estrategia guardada o parámetros (factor de escala, cotización mínima y
  pesos); uno de los dos, no ambos ni ninguno.
- **FR-055**: La simulación MUST reproducir, desde 100,00, todas las semanas que el sistema
  cotizaría hoy según las reglas del recálculo, para los jugadores activos, y devolver por
  jugador su cotización actual y la simulada. La respuesta MUST admitir los filtros de liga,
  equipo y posición y la paginación del catálogo.
- **FR-056**: La simulación MUST NOT guardar nada ni modificar cotizaciones, estrategias ni
  la activa, y MUST ser determinista.
- **FR-057**: Los parámetros enviados MUST validarse con las mismas reglas que una
  estrategia (FR-028). Una estrategia guardada inexistente MUST responder que no fue
  encontrada.

#### Aplicación web

- **FR-058**: La aplicación MUST mantener la estética de la pizarra táctica de la 004 y MUST
  ser usable desde 360 px de ancho sin desplazamiento horizontal de la página.
- **FR-059**: Todo lo clickeable MUST ser un botón o un enlace con foco visible y MUST
  poder usarse con teclado. El color MUST NOT ser el único modo de distinguir una suba de
  una baja: la variación lleva signo y texto.
- **FR-060**: La ficha del jugador MUST mostrar su cotización actual (valor del jugador y
  precio por token) con su variación respecto de la semana anterior, y un gráfico de
  evolución semanal con selección de rango de fechas. El gráfico MUST mostrar una sola
  cotización por semana (la última de esa semana), indicar en cada punto su valor, su semana,
  y la estrategia y la versión, y ofrecer una alternativa en texto con los mismos puntos.
- **FR-061**: La ficha, la pizarra y el ranking MUST tener estados de carga y de error; la
  ficha y la pizarra MUST tener además el estado de jugador sin cotización, sin ocultar los
  datos del catálogo.
- **FR-062**: Cada jugador de la pizarra MUST mostrar su cotización actual. La pizarra MUST
  obtenerla sin hacer una consulta por jugador.
- **FR-063**: El ranking MUST ser una pantalla con filtros de liga, equipo y posición,
  paginación, y por jugador su cotización, su variación semanal y la estrategia y la versión
  usadas. Los filtros y la hoja MUST reflejarse en la dirección, con los mismos nombres que
  la pizarra (`league`, `team`, `position`, `page`), y los filtros sin valor no aparecen.
- **FR-064**: La administración MUST estar disponible solo para el rol administrador. Quien
  no lo es MUST NOT ver ninguna entrada a ella y, si entra por la dirección, MUST recibir un
  aviso de falta de permiso. Un visitante sin sesión MUST ser llevado a iniciar sesión.
- **FR-065**: La administración MUST permitir listar las estrategias con sus versiones y
  cuál es la activa; crear una; editarla (versión nueva); activar una; y elegir "recotizar
  ahora" o "la semana siguiente" al cambiar la activa, con la segunda preseleccionada y sin
  aplicar nada hasta confirmar.
- **FR-066**: El formulario de estrategias MUST mostrar la suma de los pesos mientras se
  completan, MUST impedir enviar pesos que no suman 1,00, y MUST mostrar en español el motivo
  de cualquier rechazo del servidor sin perder lo escrito.
- **FR-067**: La administración MUST permitir disparar el recálculo y ver su informe, y
  simular una estrategia guardada o parámetros y compararla con la actual: por jugador, la
  cotización actual, la simulada y la diferencia, con filtros y paginación.
- **FR-068**: Ante un conflicto (un recálculo o una sincronización en curso), la
  administración MUST mostrar el motivo.
- **FR-069**: Un visitante sin sesión MUST poder ver la pizarra, la ficha, la evolución y el
  ranking, pero no la administración.
- **FR-070**: Los mensajes y las etiquetas de la aplicación MUST estar en español; los
  valores de los enums (`GOALKEEPER`, `PREMIER` y demás) se respetan como los devuelve el
  backend y se traducen en la presentación.
- **FR-071**: La aplicación MUST NOT ofrecer acciones de compra, venta, "sumar al plantel" ni
  órdenes, y MUST NOT mostrar presupuesto, tokens ni portfolio de un usuario: eso es de la
  funcionalidad de mercado.
- **FR-072**: El plan MUST listar cada test existente de la 004 que cambia, qué aserciones
  cambian y por qué. Ningún test MUST modificarse ni borrarse hasta que el "sí" explícito del
  equipo figure en Clarifications. El cambio levanta solo la prohibición de mostrar precios,
  valores y cotizaciones; la de mostrar puntajes se conserva (el score no se expone, FR-006), igual
  que las prohibiciones de FR-071.

### Key Entities

- **Cotización**: el valor del 100 % de un jugador en una semana, en créditos. Atributos:
  jugador, semana, desde cuándo rige, valor (2 decimales), precio por token (valor / 100, 4
  decimales), score, tipo (inicial, calculada o recotizada), estrategia y versión con que se
  calculó. Nunca se modifica ni se borra; un jugador tiene muchas, y una semana puede tener
  más de una por jugador (la calculada y las recotizadas).
- **Variación**: la diferencia, en créditos y en porcentaje, entre una cotización y el valor
  desde el que se calculó. No se guarda: se informa.
- **Semana**: el período de lunes 00:00 a lunes 00:00 siguiente, hora de Argentina, que se
  cotiza una sola vez cuando cierra. Puede estar pendiente por falta de estadísticas.
- **Métrica**: una de las 19 magnitudes de rendimiento semanal de un jugador (FR-016), con su
  sentido (más es mejor o menos es mejor). Se normaliza entre −1 y +1 contra los pares.
- **Estrategia de valuación**: la forma de valuar. Nombre único, factor de escala, cotización
  mínima y un juego de pesos por defecto más uno opcional por posición. Tiene versiones y una
  de ellas es la activa.
- **Versión de estrategia**: una foto inmutable de una estrategia. Editar crea la siguiente.
  Cada cotización apunta a la que la calculó.
- **Juego de pesos**: los pesos por métrica de una estrategia, mayores o iguales a 0, que
  suman 1,00.
- **Recálculo**: una corrida que cotiza las semanas pendientes. Su origen es la corrida de
  los lunes, el arranque o un disparo manual. Hay a lo sumo una a la vez y no corre junto con
  una sincronización.
- **Informe del recálculo**: el resumen de una corrida: semanas cotizadas, jugadores con
  cotización inicial, calculada y recotizada de cada una, y semanas pendientes con su motivo.
  Va al registro de la aplicación y, si el disparo fue manual, a la respuesta; no se guarda.
- **Simulación**: el resultado de reproducir las semanas cerradas con una estrategia o con
  parámetros enviados. Por jugador, su cotización actual y la simulada. No se guarda.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Después de la primera ejecución, el 100 % de los jugadores activos tiene una
  cotización inicial de 100,00 y una calculada por cada semana cerrada que se cotizó.
- **SC-002**: Los ejemplos del spec (cinco delanteros con −0,6; −0,6; 0,0; +0,4 y +0,8; score
  0,30; 100,00 → 103,00 → 101,00; mínimo de 10,00 sobre 12,00 con score −0,50; 104,00
  recotizado desde 103,00) se reproducen exactamente, con 0 diferencias.
- **SC-003**: En todo el historial, 0 cotizaciones calculadas o recotizadas difieren de su
  valor de partida en más del factor de escala de su estrategia (salvo cuando el mínimo de
  la estrategia las sube), 0 están por debajo del mínimo de su estrategia y 0 son negativas.
- **SC-004**: En una semana sin partidos, 0 cotizaciones cambian de valor.
- **SC-005**: Repetir un recálculo sin datos nuevos crea 0 cotizaciones.
- **SC-006**: El 100 % de las cotizaciones guardadas conserva la estrategia y la versión con
  que se calculó, y crear o editar estrategias deja en 0 las cotizaciones modificadas.
- **SC-007**: Dos cotizaciones hechas con los mismos datos y la misma estrategia son
  idénticas en el 100 % de los casos.
- **SC-008**: Una persona ve la cotización de un jugador en su ficha en menos de 2 segundos
  desde que la abre, y una página del ranking en menos de 2 segundos, con el catálogo
  completo (alrededor de 2.600 jugadores).
- **SC-009**: Un recálculo que cotiza una semana para todo el catálogo termina en menos de 1
  minuto, y la reconstrucción de una temporada completa (hasta 40 semanas) en menos de 5
  minutos.
- **SC-010**: Una simulación sobre el catálogo completo devuelve su primera página en menos
  de 30 segundos y dos simulaciones iguales devuelven respuestas idénticas.
- **SC-011**: El 100 % de los intentos de un usuario común de crear, editar, activar o
  listar estrategias, disparar el recálculo o simular se rechazan, y 0 de ellos cambia el
  estado del sistema.
- **SC-012**: En ningún momento hay dos recálculos en curso, ni un recálculo junto con una
  sincronización.
- **SC-013**: Con la aplicación encendida y sin sincronización en curso, cada lunes a las
  06:00 hora de Argentina arranca exactamente un recálculo sin intervención de nadie.
- **SC-014**: Con las dos fuentes caídas, el 100 % de las consultas y de los recálculos
  funciona con lo guardado.
- **SC-015**: En todo momento hay exactamente una estrategia activa.
- **SC-016**: Un recálculo que falla a la mitad de una semana deja 0 cotizaciones de esa
  semana, y las consultas nunca muestran una semana a medias.
- **SC-017**: La ficha, la pizarra, el ranking y la administración se usan desde 360 px de
  ancho sin desplazamiento horizontal de la página, y el 100 % de lo clickeable se alcanza
  con teclado con foco visible.
- **SC-018**: Un visitante sin sesión llega de la pizarra a la ficha, a la evolución y al
  ranking sin iniciar sesión; 0 controles de administración le resultan visibles, ni a un
  usuario común.
- **SC-019**: Un administrador crea una estrategia, la simula, la activa eligiendo cuándo
  recotizar y ve el resultado en menos de 5 minutos, solo desde la aplicación web.
- **SC-020**: Una revisión de todas las pantallas encuentra 0 acciones de compra, venta,
  "sumar al plantel" u órdenes, y 0 datos de presupuesto, tokens o portfolio.

## Assumptions

- Hora de Argentina es UTC−3, sin cambio de hora de verano. Los lunes a las 06:00 son hora
  de Argentina.
- Los pares de un jugador son los activos de su misma posición **de las cinco ligas juntas**,
  no de su liga, y el equipo lo confirmó el 2026-10-15 (Clarifications).
- Para las métricas que solo existen si el jugador jugó (rating, precisión de pase, resultado
  del equipo), el grupo con que se compara es el de los pares que sí la tienen. Los que no la
  tienen valen 0. Es una interpretación de la regla "si no se puede calcular, vale 0".
- El rating semanal es el promedio de los partidos de la semana, ponderado por minutos; un
  partido sin rating no entra. Los minutos jugados son los de la 006.
- El redondeo de 2 decimales del valor es hacia arriba desde la mitad (0,005 sube). El score
  se calcula sin redondear y el valor se redondea una sola vez. El precio por token es el
  valor dividido 100, y con 2 decimales de valor entra exacto en 4.
- Los pesos admiten a lo sumo dos decimales y la suma se verifica de forma exacta.
- El nombre de una estrategia es único sin distinguir mayúsculas ni espacios de los extremos,
  y no se edita. Las estrategias no se borran.
- Activar otra estrategia deja activa su versión más reciente. No se activan versiones
  viejas.
- Los pesos exactos de la estrategia B no están en el pedido: el plan los fija y los
  documenta, respetando FR-031.
- Crear o editar una estrategia no queda sujeto a la exclusión del recálculo (FR-049 y
  FR-050). Solo lo está "recotizar ahora", que cotiza.
- El estado "activo" de un jugador es el de cuando se cotiza: no se reconstruye el que tenía
  en semanas pasadas, ni en la primera ejecución ni en la simulación.
- La variación de una cotización recotizada se mide contra el mismo valor de partida que la
  calculada que reemplaza, no contra esa calculada.
- La cotización a una fecha toma "el final del día" en hora de Argentina; si ese día hubo
  una recotización en el acto, rige la recotizada.
- El tamaño máximo de página del historial alcanza para los puntos semanales de una
  temporada entera, de modo que el gráfico no necesita varias consultas.
- Un jugador sin cotización no aparece en el ranking.
- La simulación es de solo lectura y no queda sujeta a la exclusión del recálculo (FR-049 y
  FR-050). Trabaja sobre una foto consistente de los datos: lo que una sincronización guarde
  mientras corre no la altera.
- Si el administrador no envía la opción de recotización, el servidor aplica "la semana
  siguiente". La aplicación web no deja el campo vacío: preselecciona esa opción.
- Una semana con partidos cuyos jugadores no se pudieron cruzar queda cotizada con lo que
  haya; los jugadores sin cruzar cuentan como si no hubieran jugado.
- El administrador de esta funcionalidad es el de la 001 (su FR-036). La aplicación web sabe
  que una sesión es de administrador por el rol que ya devuelve el perfil del usuario.
- El plan decide cómo llega la cotización a la pizarra (un campo en el listado del catálogo
  o una consulta por lote); el spec solo exige que no cueste una consulta por jugador.
- El plan declara los contextos nuevos del backend y el status code que sume, con el aval de
  un desarrollador, según la constitución. En el frontend no se agregan carpetas fuera de
  las del árbol vigente.
- Los tiempos de los criterios de éxito suponen alrededor de 2.600 jugadores activos y la
  base local del proyecto.
- Los mensajes de error se redactan en español, según el Principio VII de la constitución.
- Riesgo conocido de la fórmula aditiva (confirmada el 2026-10-15): con el correr de las semanas una
  parte de los jugadores puede quedar en la cotización mínima. El factor de escala y el mínimo por
  defecto son un punto de partida y se recalibran con la simulación (historia 7) sobre datos reales.

## Dependencies

- **Estadística de un jugador en un partido (006)**: aporta las métricas por jugador y
  partido (minutos, goles, asistencias, tiros, tiros al arco, pases clave, pases, pases
  precisos, regates, quites, intercepciones, despejes, duelos aéreos, faltas, tarjetas y
  rating) y la marca de que un partido ya tiene sus estadísticas. Está en la rama
  `feature/scraping-whoscored` y todavía no está integrada en `develop`: esta funcionalidad
  no se puede implementar hasta que lo esté.
- **Catálogo y partidos (005)**: aporta los jugadores y su estado activo o inactivo, el
  equipo, la liga y la posición, y los partidos con su fecha, estado y resultado. También
  aporta las sincronizaciones, con las que el recálculo no puede correr junto. La pizarra
  necesita la cotización junto a cada jugador del catálogo (FR-062).
- **Catálogo de jugadores (002)**: aporta la paginación (la primera hoja es la 0), los filtros
  de liga, equipo y posición, y que los valores no admitidos se rechazan. El ranking y la
  simulación los reutilizan.
- **Frontend de la pizarra táctica (004)**: aporta la estética, la pizarra, la ficha, las
  rutas y el cliente. Esta funcionalidad modifica su RF-002 y sus escenarios que prohíben
  mostrar precio, valor o puntaje (US1.5, US2.5), y su CE-009 con el test
  `NoPurchaseActions.test.tsx`, en lo que prohíben mostrar precios, valores y cotizaciones. Se
  conserva todo lo que prohíben de compra y venta (FR-071) y de puntajes (FR-006). Su RF-018 se
  extiende al ranking. El resto de la 004 sigue vigente.
- **Autenticación (001)**: aporta el rol ADMIN y la cuenta de administrador de arranque. Las
  consultas siguen el criterio público del catálogo; las funciones de administrador exigen el
  rol.
- **Constitución 2.2.1**: el Principio IV (no se modifica ni se borra un test sin el "sí"
  explícito del equipo), el Principio III (manejo único de errores y sus status code) y la
  sección Frontend (árbol de carpetas cerrado, tests en `frontend/tests/`, responsive,
  accesible).
- **Mercado (posterior)**: consume la cotización y el precio por token para ejecutar órdenes.

## Fuera de Alcance

No forman parte de esta funcionalidad:

- La compra, la venta, las órdenes, los tokens y el portfolio, con sus pantallas: van en la
  funcionalidad de mercado.
- La auditoría general y la observabilidad.
- Recotizar todo el pasado con otra estrategia: la recotización en el acto solo rehace la
  última semana cerrada.
- El ajuste manual de precios.
- El scraping y la sincronización de las fuentes (005 y 006).
- Borrar estrategias, activar una versión vieja y renombrar una estrategia.
- Guardar el historial de los informes del recálculo y de las simulaciones.
- Mostrar estadísticas crudas, métricas normalizadas o el score de una cotización.
- Cotizaciones en vivo o por día: la cotización es semanal.
- Notificaciones por cambios de cotización.
