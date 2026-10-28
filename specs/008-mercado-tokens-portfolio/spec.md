# Feature Specification: Mercado de tokens y portfolio de usuarios

**Feature Branch**: `008-mercado-tokens-portfolio`

**Created**: 2026-10-27

**Status**: Draft

**Input**: User description: "Mercado de tokens y portfolio de usuarios (Entrega 2, funcionalidad de mercado), con su frontend. Se construye sobre la cotización y las estrategias de la 007 y las estadísticas de la 006, que ya están en develop. Al iniciar el mercado, el superusuario pone a la venta los 100 tokens de cada jugador. Los usuarios compran y venden tokens con órdenes de compra y de venta, y pueden ver en un listado del mercado qué órdenes siguen pendientes de cada jugador, con filtros propios del mercado. Cada usuario tiene su portfolio con sus posiciones, su valor actual o a una fecha pasada, y su rentabilidad. Todo se puede hacer por API y desde la aplicación web."

## Clarifications

### Session 2026-10-27

Decisiones del equipo que llegaron con el pedido de la funcionalidad (firmes):

- Q: Qué es un token y cuánto vale? -> A: Cada jugador tiene 100 tokens y cada token es el 1 %
  del jugador. El precio por token es la cotización vigente dividida 100, con 4 decimales
  (007, FR-001 y FR-002). Con N tokens, el usuario tiene el N % y su posición vale cotización ×
  N / 100.
- Q: Cómo arranca el mercado? -> A: El superusuario (el administrador de la 001) es el dueño
  inicial de los 100 tokens de cada jugador y, ni bien inicia el mercado, genera una orden de
  venta por los 100 tokens de cada jugador del catálogo, a 1 crédito por token siempre, sin
  importar la cotización. También para los jugadores que entren después, sin duplicarla nunca.
  No recompra, no crea otras órdenes, no opera como usuario y sus órdenes no se cancelan.
- Q: Se puede revender mientras el superusuario tenga tokens? -> A: No. Mientras la orden de
  venta del superusuario de un jugador tenga tokens pendientes, ningún usuario puede generar una
  orden de venta de ese jugador: el pedido se rechaza al crearlo y la orden no se crea.
- Q: A qué precio operan los usuarios entre sí? -> A: Al precio por token vigente del jugador en
  el momento de la ejecución. No hay precio límite.
- Q: Hay ejecución parcial? -> A: Sí, en los dos sentidos, y el remanente queda pendiente hasta
  que aparezca una contraparte.
- Q: Qué prioridad tienen las órdenes? -> A: Una compra se ejecuta primero contra la orden del
  superusuario de ese jugador, a 1 crédito, hasta agotarla; el resto queda pendiente y se cruza
  con las ventas de usuarios, de la más vieja a la más nueva. Una venta de un usuario se cruza
  con las compras pendientes, de la más vieja a la más nueva.
- Q: Cómo se manejan los créditos? -> A: 1000 al registrarse (001, configurable), 0 para el
  superusuario. Solo se crean al registrarse. Cada operación mueve el mismo monto del comprador
  al vendedor; el total (cantidad × precio por token) se redondea a 2 decimales, hacia arriba
  desde la mitad.
- Q: Qué muestra el portfolio? -> A: Lo que pide la consigna 3.4, por jugador y en total, con la
  cotización vigente o con la que regía en una fecha pasada (FR-036 a FR-042).
- Q: Se muestra el score? -> A: Sí. Esta funcionalidad cambia el FR-006 de la 007 y su
  clarificación del 2026-10-15: el score de cada cotización se muestra en la API y en las
  pantallas. Siguen sin exponerse las estadísticas crudas y las métricas normalizadas.
- Q: Qué endpoints pide la consigna? -> A: `POST /orders/buy`, `POST /orders/sell`,
  `GET /users/{id}/portfolio` (con fecha opcional) y `GET /users/{id}/transactions`, tal cual,
  más los que hagan falta para el listado del mercado, las órdenes propias y la auditoría.
- Q: El frontend va aparte? -> A: No, va en esta misma funcionalidad.

Reglas que el equipo propuso con el pedido, a confirmar en la clarificación (este spec las
escribe como requisitos tal cual):

- Q: Qué pasa con los créditos de una compra pendiente? -> A (propuesta): Quedan reservados al
  precio vigente al crearla. Si al ejecutarse el precio es mayor y el saldo no alcanza, se
  ejecuta la parte que alcanza y el resto se cancela (FR-019 y FR-027).
- Q: Un usuario puede tener a la vez compras y ventas pendientes del mismo jugador? -> A
  (propuesta): No, y sus órdenes nunca se cruzan entre sí (FR-017).
- Q: Las órdenes vencen? -> A (propuesta): No (FR-021).
- Q: Qué cantidades se admiten? -> A (propuesta): Un entero entre 1 y 100; una venta no puede
  superar los tokens libres del usuario (FR-014 y FR-016).
- Q: Hay comisiones? -> A (propuesta): No (FR-004).
- Q: El listado del mercado es público? -> A (propuesta): Sí, como el ranking, y nunca muestra
  quién es el dueño de una orden, solo su tipo de usuario. El portfolio, el historial y las
  órdenes propias son privados (FR-047 y FR-055).
- Q: Qué fechas admite el portfolio a una fecha? -> A (propuesta): Se rechazan la fecha futura,
  la de formato inválido y la anterior al registro del usuario (FR-041).
- Q: Cuándo "inicia el mercado"? -> A (propuesta): Al arrancar la aplicación y después de cada
  sincronización del catálogo, solo para los jugadores que todavía no tienen su orden del
  superusuario (FR-008).

Pendiente antes de implementar (no es una duda de comportamiento, es una puerta del proceso):

- Q: Se pueden modificar los tests que hoy prohíben comprar, vender, órdenes, portfolio,
  presupuesto, tokens y puntaje en el frontend (`NoPurchaseActions.test.tsx` de la 004, CE-009;
  `NoMarketActions.test.tsx` de la 007, SC-020) y los tests de la 007 que verifican que el score
  no se expone? -> A: El equipo decidió que deben modificarse, porque esta funcionalidad muestra
  operaciones de mercado y el score. Se mantiene solo la prohibición de "sumar al plantel". El
  Principio IV de la constitución exige el "sí" explícito del equipo registrado acá antes de
  implementar. **Pendiente: el "sí" todavía no está registrado.** El plan lista qué tests y qué
  aserciones cambian y por qué (FR-060).

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Comprar tokens al superusuario (Priority: P1)

Al iniciar el mercado, el superusuario pone a la venta los 100 tokens de cada jugador a 1
crédito cada uno. Un usuario registrado compra tokens de un jugador: la compra se ejecuta en el
acto contra la orden del superusuario, el usuario paga 1 crédito por token y los tokens pasan a
ser suyos. Es el escenario 3 de la consigna.

**Why this priority**: Es el núcleo del mercado y la primera operación posible: sin la venta
inicial nadie tiene tokens, y sin tokens no hay portfolio, reventa ni historial. Sola ya
entrega valor: los usuarios invierten sus créditos y tienen posiciones.

**Independent Test**: Se prueba con un catálogo con cotizaciones (datos de prueba, sin
consultar ninguna fuente), arrancando la aplicación, verificando que cada jugador tiene su
orden del superusuario por 100 tokens y, como usuario, comprando tokens de varios jugadores y
verificando saldos, tenencias y el remanente de cada orden del superusuario.

**Acceptance Scenarios**:

1. **Given** un catálogo de jugadores y una base sin órdenes, **When** la aplicación arranca,
   **Then** existe exactamente una orden de venta del superusuario por cada jugador del
   catálogo, por 100 tokens, a 1 crédito por token **And** el superusuario figura como dueño de
   los 100 tokens de cada jugador.
2. **Given** esas órdenes ya generadas, **When** la aplicación arranca de nuevo, **Then** no se
   genera ninguna orden nueva del superusuario.
3. **Given** una sincronización del catálogo que suma un jugador nuevo, **When** termina,
   **Then** ese jugador tiene su orden del superusuario por 100 tokens **And** los demás no
   tienen una segunda.
4. **Given** un usuario con 1000 créditos y un jugador cotizado en 103,00 cuya orden del
   superusuario tiene 100 tokens, **When** el usuario compra 10 tokens, **Then** la compra se
   ejecuta en el acto a 1,0000 por token, por 10,00 créditos, sin importar la cotización **And**
   el usuario queda con 990,00 créditos y 10 tokens (10 %) **And** el superusuario recibe 10,00
   créditos **And** a su orden le quedan 90 tokens.
5. **Given** una orden del superusuario con 3 tokens pendientes, **When** un usuario pide comprar
   10, **Then** se ejecutan 3 a 1 crédito **And** quedan 7 en una orden de compra pendiente
   **And** la orden del superusuario queda ejecutada por completo.
6. **Given** un usuario con 5,00 créditos libres, **When** pide comprar 10 tokens al
   superusuario, **Then** el sistema rechaza el pedido por saldo insuficiente **And** no se crea
   ninguna orden ni cambia ningún saldo ni tenencia.
7. **Given** el administrador con sesión, **When** pide comprar o vender tokens, **Then** el
   sistema lo rechaza por falta de permiso **And** no cambia nada.
8. **Given** un pedido de compra sin credencial, **When** se envía, **Then** el sistema lo
   rechaza por falta de credencial.
9. **Given** un pedido de compra con una cantidad 0, negativa, decimal o mayor que 100, o un
   jugador inexistente, **When** se envía, **Then** el sistema lo rechaza indicando el valor no
   admitido o que el jugador no fue encontrado, y no crea nada.
10. **Given** un pedido de compra que trae en el cuerpo el identificador de otro usuario,
    **When** se envía, **Then** la orden queda a nombre del usuario de la credencial.
11. **Given** cuatro usuarios recién registrados y cinco jugadores con orden del superusuario
    completa, **When** cada usuario compra tokens de los cinco jugadores, **Then** todas las
    compras se ejecutan a 1 crédito **And** cada usuario ve las cinco posiciones en su portfolio.

---

### User Story 2 - Ver el portfolio actual (Priority: P2)

Un usuario consulta su portfolio: por cada jugador del que tiene tokens, cuántos tiene, qué
porcentaje del jugador representan, cuántos tiene comprometidos en ventas, a cuánto los compró
en promedio, cuánto invirtió, cuánto valen hoy con la cotización vigente (con su score) y cuánto
gana o pierde. En total, su saldo libre y reservado, lo invertido, el valor de sus posiciones,
la ganancia o pérdida, la ganancia realizada en ventas y su patrimonio.

**Why this priority**: Es lo que pide la consigna 3.4 y lo que cierra el escenario 3: ver la
valoración de lo comprado. Necesita la P1 para tener posiciones.

**Independent Test**: Se prueba con usuarios que ya tienen tenencias (por compras de la P1 o
datos de prueba) y cotizaciones conocidas, verificando cada campo por jugador y los totales
contra los ejemplos del spec, y los rechazos de acceso.

**Acceptance Scenarios**:

1. **Given** un usuario con 10 tokens comprados a 1,0000 y el jugador cotizado hoy en 103,00,
   **When** consulta su portfolio, **Then** la posición informa 10 tokens, 10 % del jugador, 0
   comprometidos, precio promedio 1,0000, invertido 10,00, precio por token 1,0300, el score de
   esa cotización, valor 10,30, ganancia +0,30 y rentabilidad +3,00 %.
2. **Given** un usuario que compró 10 tokens a 1,0000 y después 5 a 1,0300, **When** consulta
   su portfolio, **Then** el precio promedio es 1,0100 (15,15 pagados / 15 tokens) y el
   invertido es 15,15.
3. **Given** ese usuario, que después vende 5 tokens a 1,0500, **When** consulta su portfolio,
   **Then** le quedan 10 tokens con precio promedio 1,0100 (no cambia al vender) e invertido
   10,10 **And** su ganancia realizada acumulada es +0,20 (5,25 recibidos − 5 × 1,0100).
4. **Given** un usuario que vendió todos sus tokens de un jugador y después vuelve a comprar,
   **When** consulta su portfolio, **Then** el precio promedio de ese jugador se calcula solo
   con las compras posteriores a quedar en 0.
5. **Given** un usuario con 990,00 créditos libres, 6,18 reservados y posiciones que valen
   20,60, **When** consulta su portfolio, **Then** el total informa saldo libre 990,00,
   reservado 6,18 y patrimonio 1016,78.
6. **Given** un usuario con una posición de un jugador que pasó a inactivo, **When** consulta su
   portfolio, **Then** esa posición se valúa con la última cotización del jugador.
7. **Given** un usuario sin ninguna tenencia, **When** consulta su portfolio, **Then** ve la
   lista de posiciones vacía y su saldo como patrimonio.
8. **Given** un usuario común, **When** pide el portfolio de otro usuario, **Then** el sistema lo
   rechaza por falta de permiso **And** el administrador sí puede verlo.
9. **Given** un identificador de usuario inexistente, **When** el administrador pide su
   portfolio, **Then** el sistema responde que el usuario no fue encontrado.
10. **Given** WhoScored y Football-Data sin responder, **When** un usuario consulta su
    portfolio, **Then** responde igual que con las fuentes disponibles.

---

### User Story 3 - Comprar y vender desde la aplicación web (Priority: P3)

Un usuario con sesión abre la ficha de un jugador, ve sus órdenes pendientes (incluida la del
superusuario con los tokens que le quedan) y compra o vende con una confirmación previa que
muestra la cantidad, el precio por token y el total estimado. Después ve el resultado real. Ve
su saldo en todo momento y, si todavía no puede vender porque el superusuario tiene tokens, la
ficha se lo avisa.

**Why this priority**: Es como opera la persona usuaria y lo que se muestra en la entrega. Se
construye sobre la P1 y la P2.

**Independent Test**: Se prueba con la API simulada, recorriendo la ficha con y sin sesión, como
usuario y como administrador, comprando y vendiendo con confirmación, y verificando el resultado,
los avisos, los rechazos, los estados de carga y de error, a 360 px y con teclado.

**Acceptance Scenarios**:

1. **Given** un usuario con sesión, **When** navega por la aplicación, **Then** ve su saldo
   libre de créditos.
2. **Given** un usuario con sesión en la ficha de un jugador operable, **When** elige comprar
   10 tokens, **Then** antes de enviar ve una confirmación con la cantidad, el precio por token y
   el total estimado **And** nada se envía hasta que confirma.
3. **Given** esa confirmación aceptada, **When** el sistema responde, **Then** la ficha muestra
   el resultado real: tokens ejecutados, precio, total pagado y, si corresponde, el remanente que
   quedó pendiente **And** el saldo visible se actualiza.
4. **Given** la ficha de un jugador cuya orden del superusuario todavía tiene tokens, **When** la
   abre un usuario con tokens de ese jugador, **Then** la ficha avisa que todavía no se puede
   vender porque el superusuario tiene tokens disponibles, e indica cuántos le quedan.
5. **Given** la ficha de un jugador, **When** se abre, **Then** muestra sus órdenes pendientes,
   con tipo, tipo de usuario dueño, cantidades pedida, ejecutada y pendiente y fecha, incluida
   la del superusuario.
6. **Given** un rechazo del servidor (saldo insuficiente, tokens insuficientes, jugador no
   operable, venta bloqueada por el superusuario), **When** se recibe, **Then** la ficha muestra
   el motivo en español y no da la operación por hecha.
7. **Given** un visitante sin sesión o el administrador, **When** abre la ficha, **Then** no ve
   controles de compra ni de venta **And** sí ve la cotización, el score y las órdenes
   pendientes.
8. **Given** un jugador inactivo o sin cotización, **When** un usuario abre su ficha, **Then**
   los controles de compra y venta no están disponibles y la ficha explica por qué.
9. **Given** la ficha, la confirmación y el resultado en un ancho de 360 px, **When** se usan,
   **Then** no hay desplazamiento horizontal de la página **And** todo se puede hacer con
   teclado, con foco visible.

---

### User Story 4 - Revender tokens entre usuarios (Priority: P4)

Cuando el superusuario ya vendió los 100 tokens de un jugador, los usuarios pueden poner a la
venta los suyos. Las ventas y las compras pendientes se cruzan entre usuarios al precio por
token vigente en el momento de la ejecución, con ejecución parcial y remanente pendiente en los
dos sentidos.

**Why this priority**: Es el mercado secundario que pide la consigna. Necesita que el
superusuario haya agotado algún jugador (P1).

**Independent Test**: Se prueba con un jugador cuya orden del superusuario ya está agotada y
usuarios con tenencias y saldos conocidos, creando órdenes de compra y de venta en distinto
orden y verificando cruces, prioridades, precios, totales redondeados, remanentes, reservas y
los invariantes.

**Acceptance Scenarios**:

1. **Given** un jugador cuya orden del superusuario tiene 1 token pendiente y un usuario con 10
   tokens de ese jugador, **When** el usuario pide vender 5, **Then** el sistema rechaza el
   pedido porque el superusuario todavía tiene tokens de ese jugador **And** no se crea ninguna
   orden, ni siquiera pendiente.
2. **Given** ese mismo jugador después de que el superusuario vendió su último token, **When**
   el usuario pide vender 5, **Then** la orden se crea.
3. **Given** un jugador agotado por el superusuario, cotizado en 103,00, y un usuario A con
   1000,00 créditos libres, **When** A pide comprar 10 tokens y no hay ventas pendientes,
   **Then** queda una orden de compra pendiente por 10 **And** se reservan 10,30 créditos (al
   precio vigente al crearla) **And** A queda con 989,70 libres y 10,30 reservados.
4. **Given** esa compra pendiente de A, la cotización ya en 105,00 y un usuario B que vende 4,
   **When** se ejecuta, **Then** se operan 4 tokens a 1,0500 por 4,20 créditos **And** A queda
   con una compra pendiente por 6, con 6,18 reservados y 989,62 libres **And** B recibe 4,20.
5. **Given** una compra pendiente de 5 tokens y un usuario que vende 8, **When** se ejecuta,
   **Then** se venden 5 **And** quedan 3 en una orden de venta pendiente.
6. **Given** dos compras pendientes del mismo jugador, la de A creada antes que la de C, **When**
   un usuario vende menos tokens que la suma de las dos, **Then** se completa primero la de A y
   el resto se ejecuta contra la de C.
7. **Given** dos ventas pendientes de un jugador, la de B creada antes que la de D, **When** un
   usuario compra, **Then** se ejecuta primero contra la de B.
8. **Given** una compra de 10 tokens con el precio por token en 1,0345, **When** se ejecuta
   completa, **Then** el total es 10,35 (10,345 redondeado hacia arriba desde la mitad) **And**
   el comprador paga exactamente lo que recibe el vendedor.
9. **Given** una compra pendiente con 10,30 reservados para 10 tokens, un saldo libre de 0 y el
   precio que subió a 1,1000, **When** aparece un vendedor de 10, **Then** se ejecutan 9 tokens
   por 9,90 **And** el remanente de 1 se cancela por saldo insuficiente **And** se liberan los
   0,40 reservados que sobran.
10. **Given** un usuario con 10 tokens y una venta pendiente de 6, **When** pide vender 5,
    **Then** el sistema lo rechaza porque solo tiene 4 tokens libres.
11. **Given** un usuario con una compra pendiente de un jugador, **When** pide vender ese mismo
    jugador, **Then** el sistema lo rechaza **And** lo mismo al revés.
12. **Given** dos usuarios que piden a la vez comprar los últimos 3 tokens disponibles de un
    jugador, **When** se procesan, **Then** uno se ejecuta y el otro queda pendiente, sin estados
    intermedios **And** la suma de las tenencias de ese jugador sigue siendo 100.
13. **Given** un recálculo de cotizaciones en curso, **When** se ejecuta una operación, **Then**
    usa la última cotización completa vigente, nunca una semana a medias.

---

### User Story 5 - Listado del mercado (Priority: P5)

Cualquier persona, sin iniciar sesión, consulta el listado del mercado para ver qué órdenes
siguen pendientes de cada jugador, con filtros de tipo de orden, liga, equipo y tipo de usuario
dueño, y la paginación del catálogo. Desde la web, en la pantalla "Mercado".

**Why this priority**: Hace visible la oferta y la demanda, y cuántos tokens le quedan al
superusuario de cada jugador. Necesita la P1 para tener órdenes.

**Independent Test**: Se prueba con órdenes pendientes, parciales, ejecutadas y canceladas de
varios jugadores, ligas, equipos y tipos de usuario, consultando sin credencial con cada filtro
y combinaciones, y recorriendo la pantalla "Mercado" con la API simulada.

**Acceptance Scenarios**:

1. **Given** órdenes de varios jugadores, **When** se consulta el listado sin filtros, **Then**
   se listan las órdenes con remanente pendiente, de la más vieja a la más nueva, cada una con
   jugador, equipo, liga, tipo de orden, tipo de usuario dueño, cantidades pedida, ejecutada y
   pendiente, estado y fecha **And** los datos de paginación del catálogo.
2. **Given** el listado, **When** se filtra por tipo de orden, liga, equipo, tipo de usuario o
   combinaciones, **Then** solo aparecen las órdenes que cumplen todos los filtros.
3. **Given** el listado filtrado por tipo de usuario administrador, **When** se consulta,
   **Then** se ven las órdenes del superusuario con los tokens que le quedan de cada jugador.
4. **Given** cualquier orden del listado, **When** se consulta, **Then** no aparece el nombre,
   el correo ni el identificador del usuario dueño, solo su tipo.
5. **Given** un filtro con un valor no admitido, **When** se consulta, **Then** el sistema
   rechaza el pedido indicando el valor no admitido, igual que el catálogo.
6. **Given** la pantalla "Mercado", **When** se eligen filtros o se cambia de hoja, **Then** la
   lista se acota, la paginación se reinicia al cambiar un filtro **And** los filtros y la hoja
   quedan en la dirección, de modo que recargar o compartir la dirección reproduce la vista.
7. **Given** la pantalla "Mercado" a 360 px o solo con teclado, **When** se usa, **Then** no hay
   desplazamiento horizontal y todo lo clickeable tiene foco visible.

---

### User Story 6 - Historial de operaciones y órdenes propias (Priority: P6)

Un usuario consulta su historial de operaciones, de la más nueva a la más vieja, con filtros de
jugador, tipo y rango de fechas, y sus órdenes con su estado y cantidades. Puede cancelar el
remanente pendiente de una orden propia.

**Why this priority**: Da trazabilidad al usuario y le permite corregir una orden que ya no
quiere. Necesita la P1 y la P4.

**Independent Test**: Se prueba con un usuario con compras al superusuario, compras y ventas
entre usuarios y órdenes en todos los estados, verificando el contenido de cada operación, los
filtros, el orden, la paginación, la cancelación y los rechazos de acceso.

**Acceptance Scenarios**:

1. **Given** un usuario con varias operaciones, **When** consulta su historial, **Then** recibe
   una página de la más nueva a la más vieja, cada operación con fecha y hora, jugador, tipo
   (compra o venta), cantidad, precio por token, total, contraparte, la orden a la que pertenece
   y la cotización usada (vigencia, score, estrategia y versión).
2. **Given** una compra al superusuario, **When** se ve en el historial, **Then** la contraparte
   figura como "administrador" **And** una operación con otro usuario figura como "otro usuario",
   sin revelar quién.
3. **Given** una venta, **When** se ve en el historial, **Then** informa la ganancia o pérdida
   realizada.
4. **Given** el historial, **When** se filtra por jugador, por tipo, por rango de fechas o
   combinaciones, **Then** solo aparecen las operaciones que cumplen todos los filtros **And**
   un rango con el inicio posterior al final se rechaza.
5. **Given** un usuario común, **When** pide el historial de otro usuario, **Then** el sistema
   lo rechaza por falta de permiso **And** el administrador sí puede verlo.
6. **Given** un usuario con órdenes, **When** consulta sus órdenes, **Then** ve cada una con su
   jugador, tipo, estado y cantidades pedida, ejecutada y pendiente.
7. **Given** una compra propia con remanente pendiente y créditos reservados, **When** el
   usuario la cancela, **Then** el remanente se cancela, los créditos reservados vuelven al saldo
   libre **And** lo ya ejecutado se conserva.
8. **Given** una venta propia con remanente pendiente, **When** el usuario la cancela, **Then**
   los tokens comprometidos vuelven a estar libres.
9. **Given** una orden ya ejecutada por completo o ya cancelada, **When** el usuario la cancela,
   **Then** el sistema lo rechaza como conflicto y no cambia nada.
10. **Given** una orden de otro usuario o del superusuario, **When** un usuario o el
    administrador intenta cancelarla, **Then** el sistema lo rechaza y no cambia nada.
11. **Given** las pantallas de historial y de mis órdenes, **When** se usan, **Then** el
    historial tiene sus filtros y la hoja en la dirección **And** cada orden con remanente
    ofrece cancelar con una confirmación previa.

---

### User Story 7 - Portfolio a una fecha pasada (Priority: P7)

Un usuario pide su portfolio a una fecha pasada: sus tenencias, su saldo y su ganancia
realizada se reconstruyen con sus operaciones hasta el final de ese día (hora de Argentina) y
cada posición se valúa con la cotización que regía en esa fecha.

**Why this priority**: Completa el escenario 3 ("también la de fechas pasadas"). Necesita la
P2 y el historial de cotizaciones de la 007.

**Independent Test**: Se prueba con un usuario con operaciones en varios días y jugadores con
historial de cotizaciones conocido, pidiendo el portfolio a distintas fechas y verificando
tenencias, saldo, ganancia realizada y valuación contra el historial, y los rechazos de fechas.

**Acceptance Scenarios**:

1. **Given** un usuario que compró 10 tokens el 20/08/2026 y 5 más el 26/08/2026, y un jugador
   cotizado en 100,00 desde el 17/08, 103,00 desde el 24/08 y 101,00 desde el 31/08, **When**
   pide su portfolio al 25/08/2026, **Then** ve 10 tokens valuados a 1,0300 por token (10,30),
   con el score de esa cotización.
2. **Given** ese usuario, **When** pide su portfolio al 01/09/2026, **Then** ve 15 tokens
   valuados a 1,0100 por token.
3. **Given** una operación hecha a las 23:30 hora de Argentina de un día, **When** se pide el
   portfolio a ese día, **Then** la operación se incluye.
4. **Given** un usuario que vendió tokens antes de la fecha pedida, **When** pide el portfolio a
   esa fecha, **Then** la ganancia realizada acumulada incluye esas ventas y no las posteriores.
5. **Given** una fecha futura, con formato inválido o anterior al registro del usuario, **When**
   se pide el portfolio a esa fecha, **Then** el sistema rechaza el pedido indicando el valor no
   admitido.
6. **Given** la pantalla de portfolio, **When** el usuario elige una fecha, **Then** ve el
   portfolio a esa fecha **And** puede volver al actual **And** la fecha queda en la dirección.

---

### User Story 8 - Score visible en las cotizaciones (Priority: P8)

El score de cada cotización pasa a mostrarse, en la API y en las pantallas: cotización actual,
a una fecha, evolución, ranking, ficha, gráfico y portfolio. Las estadísticas crudas y las
métricas normalizadas siguen ocultas.

**Why this priority**: Es un cambio chico sobre la 007 que le da al usuario una pista de por qué
se movió un precio antes de operar. No bloquea el mercado.

**Independent Test**: Se prueba consultando cada recurso de cotización y recorriendo cada
pantalla con la API simulada, verificando que el score aparece y que ninguna estadística cruda
ni métrica normalizada aparece.

**Acceptance Scenarios**:

1. **Given** un jugador cuya cotización calculada tuvo score 0,30, **When** se consulta su
   cotización actual, a una fecha, su evolución o el ranking, **Then** cada cotización informa
   score 0,30.
2. **Given** la cotización inicial de 100,00, **When** se consulta, **Then** informa score 0, igual en
   la API y en las pantallas.
3. **Given** la ficha, el gráfico, el ranking y el portfolio, **When** se abren, **Then** cada
   cotización mostrada lleva su score.
4. **Given** cualquier respuesta o pantalla, **When** se revisa, **Then** no aparece ninguna
   estadística cruda (goles, minutos, pases y demás) ni métrica normalizada.

---

### User Story 9 - Auditoría del mercado (Priority: P9)

Cada orden creada (también las del superusuario), cada operación ejecutada y cada cancelación
queda registrada, sin poder modificarse ni borrarse. Solo el administrador la consulta.

**Why this priority**: Lo pide la consigna 5.2 y permite reconstruir qué pasó ante un reclamo.
No cambia lo que ve el usuario.

**Independent Test**: Se prueba ejecutando órdenes, cruces y cancelaciones y verificando que cada
una dejó su registro con autor, fecha y hora, detalle y estado anterior y posterior, que no hay
forma de modificarlo ni borrarlo, y que solo el administrador lo consulta.

**Acceptance Scenarios**:

1. **Given** el arranque del mercado, **When** se generan las órdenes del superusuario, **Then**
   cada una queda en la auditoría con el superusuario como autor.
2. **Given** una operación ejecutada, **When** se consulta la auditoría, **Then** figura con
   fecha y hora, comprador, vendedor, jugador, cantidad, precio, total, cotización usada y el
   saldo y la tenencia de cada parte antes y después.
3. **Given** una cancelación por el usuario o por saldo insuficiente, **When** se consulta la
   auditoría, **Then** figura con su autor (el usuario o el sistema), el motivo y el estado
   anterior y posterior.
4. **Given** un usuario común, **When** pide la auditoría, **Then** el sistema lo rechaza por
   falta de permiso.
5. **Given** cualquier registro de auditoría, **When** alguien intenta modificarlo o borrarlo
   por cualquier vía que ofrece el sistema, **Then** no hay forma de hacerlo.
6. **Given** un pedido rechazado (por ejemplo, saldo insuficiente), **When** se consulta la
   auditoría, **Then** no figura ninguna orden creada por ese pedido.

---

### Edge Cases

- No existe la cuenta del superusuario (la 001 no la crea si falta su contraseña): el mercado no
  inicia, no se genera ninguna orden del superusuario, el motivo queda en el registro de la
  aplicación y ningún jugador es operable hasta que exista y se generen sus órdenes.
- Un jugador sin orden del superusuario (por ejemplo, entró al catálogo y la generación todavía
  no corrió): no es operable hasta que la tenga.
- Un jugador sin cotización: tiene su orden del superusuario visible pero no se ejecuta; no se
  aceptan órdenes nuevas suyas hasta su primera cotización. A 1 crédito o no, no se opera.
- Un jugador que pasa a inactivo con órdenes pendientes: ninguna se ejecuta mientras siga así,
  tampoco la del superusuario; las de usuarios se pueden cancelar; al reactivarse vuelven a
  estar disponibles tal como quedaron.
- La cotización cambia entre que el usuario ve la confirmación en la web y la orden se ejecuta:
  rige el precio del momento de la ejecución y el resultado informa el real, que puede diferir
  del estimado.
- Una compra pendiente que se ejecuta a un precio menor que el de su reserva: el comprador paga
  menos y la diferencia reservada de los tokens ejecutados vuelve al saldo libre.
- Una compra pendiente cuyo saldo (reserva más saldo libre) no alcanza ni para 1 token al precio
  de ejecución: no se ejecuta ningún token, el remanente se cancela y la reserva se libera; la
  venta sigue con la próxima compra pendiente.
- Un usuario vende tokens de un jugador y su tenencia llega a 0: el jugador desaparece de su
  portfolio y el precio promedio se reinicia para la próxima compra.
- Durante un recálculo de la 007: las operaciones usan la última cotización completa vigente;
  si el recálculo termina entre dos operaciones, cada una usa la que regía en su momento.
- WhoScored o Football-Data caídos: el mercado, el portfolio, el historial y el listado
  funcionan igual.
- Dos pedidos del mismo usuario a la vez que juntos superan su saldo libre o sus tokens libres:
  se procesan uno detrás del otro y el que ya no alcanza se rechaza; nunca queda un saldo ni
  una tenencia negativa.
- El administrador pide el portfolio o el historial de la cuenta del superusuario: se informa
  como el de cualquier titular (sus tokens sin vender y los créditos recibidos).
- El portfolio a la fecha de hoy: equivale al actual con la cotización que rige al final del día,
  y no separa saldo libre de reservado.

## Requirements *(mandatory)*

### Functional Requirements

#### Tokens, precios y créditos

- **FR-001**: Cada jugador del catálogo MUST tener exactamente 100 tokens; cada token es el 1 %
  del jugador. Con N tokens, el titular tiene el N % y su posición vale cotización × N / 100.
- **FR-002**: El precio por token de una operación entre usuarios MUST ser la cotización
  vigente del jugador en el momento de la ejecución dividida 100, con 4 decimales (007, FR-001 y
  FR-002). Las operaciones contra la orden del superusuario MUST ser a 1,0000 crédito por token,
  sin importar la cotización.
- **FR-003**: El total de cada operación MUST ser cantidad × precio por token, redondeado una
  sola vez a 2 decimales, hacia arriba desde la mitad. El comprador MUST pagar exactamente el
  total y el vendedor MUST recibir exactamente el mismo total.
- **FR-004**: Las operaciones MUST NOT cobrar comisiones.
- **FR-005**: Cada usuario MUST empezar con el saldo inicial de la 001 (1000 créditos por
  defecto, configurable) y el superusuario con 0. Los créditos MUST crearse solo al registrarse
  un usuario: ninguna operación del mercado crea ni destruye créditos.
- **FR-006**: El saldo de un usuario MUST separarse en libre (lo que puede gastar) y reservado
  (lo comprometido en sus compras pendientes). Ningún saldo ni tenencia MUST quedar negativo en
  ningún momento, tampoco con pedidos simultáneos.

#### Órdenes del superusuario

- **FR-007**: El superusuario (el administrador de la 001) MUST ser el dueño inicial de los 100
  tokens de cada jugador y MUST tener, por cada jugador del catálogo, exactamente una orden de
  venta por 100 tokens a 1 crédito por token.
- **FR-008**: La generación de las órdenes del superusuario MUST correr al arrancar la
  aplicación y después de cada sincronización del catálogo, solo para los jugadores que todavía
  no tienen la suya, y MUST NOT duplicarlas nunca, tampoco al reiniciar.
- **FR-009**: Las órdenes del superusuario MUST ser visibles en el listado del mercado y en la
  ficha del jugador, e informar cuántos tokens le quedan por vender de cada jugador.
- **FR-010**: El superusuario MUST NOT comprar, crear otras órdenes ni operar como usuario: sus
  pedidos de compra o venta MUST rechazarse por falta de permiso. Sus órdenes MUST NOT
  cancelarse.
- **FR-011**: Mientras la orden del superusuario de un jugador tenga tokens pendientes, el
  sistema MUST rechazar al crearlo cualquier pedido de venta de ese jugador de un usuario: la
  orden no se crea, ni siquiera pendiente. Cuando el superusuario vendió sus 100 tokens de ese
  jugador, los usuarios MUST poder vender.
- **FR-012**: Si no existe la cuenta del superusuario, el mercado MUST NOT iniciar, MUST dejar el
  motivo en el registro de la aplicación y ningún jugador MUST ser operable hasta que existan sus
  órdenes.

#### Órdenes de usuarios: creación y validación

- **FR-013**: Un usuario común autenticado MUST poder crear órdenes de compra y de venta de
  tokens de un jugador, indicando el jugador y la cantidad. El usuario MUST salir siempre de la
  credencial (token de sesión o clave de API), nunca del cuerpo del pedido.
- **FR-014**: La cantidad de una orden MUST ser un entero entre 1 y 100. Otro valor MUST
  rechazarse con error de validación.
- **FR-015**: Una orden de compra MUST rechazarse si el saldo libre del usuario no alcanza para
  pagar la parte que se ejecuta en el acto más la reserva del remanente (FR-019). Un pedido
  rechazado MUST NOT crear ninguna orden ni cambiar ningún saldo ni tenencia.
- **FR-016**: Una orden de venta MUST NOT superar los tokens libres del usuario de ese jugador
  (su tenencia menos los tokens comprometidos en sus otras ventas pendientes); si los supera,
  MUST rechazarse. Al crearse, sus tokens MUST quedar comprometidos.
- **FR-017**: Un usuario MUST NOT tener a la vez órdenes pendientes de compra y de venta del
  mismo jugador: el pedido del sentido contrario MUST rechazarse. Las órdenes de un mismo
  usuario MUST NOT cruzarse entre sí.
- **FR-018**: Un pedido sobre un jugador inexistente MUST responder que el jugador no fue
  encontrado; sobre un jugador inactivo, sin cotización o sin orden del superusuario MUST
  rechazarse porque el jugador no es operable.
- **FR-019**: El remanente pendiente de una orden de compra MUST reservar créditos al precio por
  token vigente al crearla (cantidad pendiente × ese precio, con el redondeo de FR-003). La
  reserva MUST bajar a medida que se ejecuta el remanente y MUST liberarse al cancelarlo.
- **FR-020**: Toda orden MUST informar jugador, tipo (compra o venta), tipo de usuario dueño
  (usuario común o administrador), cantidad pedida, ejecutada y pendiente, estado y fecha de
  creación. Los estados MUST ser: **pendiente** (nada ejecutado), **parcial** (algo ejecutado y
  remanente pendiente), **ejecutada** (todo ejecutado) y **cancelada** (remanente cancelado, con
  o sin parte ejecutada).
- **FR-021**: Las órdenes pendientes MUST NOT vencer.

#### Ejecución y prioridad

- **FR-022**: Una orden de compra MUST ejecutarse primero contra la orden del superusuario de
  ese jugador, a 1 crédito por token, hasta agotarla. Lo que falte MUST cruzarse con las ventas
  pendientes de otros usuarios, de la más vieja a la más nueva, al precio vigente; lo que siga
  faltando MUST quedar como compra pendiente.
- **FR-023**: Una orden de venta de un usuario MUST cruzarse con las compras pendientes de otros
  usuarios, de la más vieja a la más nueva, al precio vigente; lo que no se venda MUST quedar
  como venta pendiente.
- **FR-024**: La ejecución MUST ser parcial cuando la contraparte no alcanza, en los dos
  sentidos, y una orden pendiente MUST seguir ejecutándose cada vez que aparezca una
  contraparte.
- **FR-025**: Cada ejecución MUST ser atómica: la transferencia de tokens, la de créditos, la
  actualización de las órdenes y su registro de auditoría ocurren todas o ninguna. Si dos
  órdenes compiten por los mismos tokens, una gana y la otra queda pendiente, sin estados
  intermedios.
- **FR-026**: Para cada jugador, la suma de las tenencias de todos los titulares (superusuario
  incluido) MUST ser siempre 100.
- **FR-027**: Si al ejecutarse una compra pendiente el precio es mayor que el de su reserva y la
  reserva más el saldo libre del comprador no alcanzan, MUST ejecutarse la mayor cantidad de
  tokens que alcance y el resto del remanente MUST cancelarse, liberando la reserva sobrante. Si
  el precio es menor, la diferencia reservada de los tokens ejecutados MUST volver al saldo
  libre.
- **FR-028**: Cada operación ejecutada MUST registrar fecha y hora, jugador, comprador,
  vendedor, cantidad, precio por token, total, las órdenes de cada parte y la cotización usada
  (vigencia, score, estrategia y versión); en una operación contra el superusuario, la
  cotización vigente en ese momento, aunque el precio sea 1,0000.
- **FR-029**: Cada venta MUST registrar la ganancia o pérdida realizada del vendedor: total
  recibido − cantidad × su precio promedio de compra, a 2 decimales.
- **FR-030**: El mercado MUST NOT consultar WhoScored ni Football-Data y MUST seguir funcionando
  si fallan. Durante un recálculo de la 007 MUST usar siempre una cotización completa, nunca una
  semana a medias.

#### Jugadores no operables

- **FR-031**: Un jugador inactivo o sin cotización MUST NOT operarse: no se aceptan órdenes
  nuevas y ninguna de sus órdenes pendientes, incluida la del superusuario, se ejecuta mientras
  siga así.
- **FR-032**: Las órdenes de usuarios de un jugador no operable MUST poder cancelarse.
- **FR-033**: Cuando el jugador se reactiva o recibe su primera cotización, sus órdenes
  pendientes MUST volver a estar disponibles tal como quedaron, y MUST cruzarse si tienen
  contraparte.

#### Cancelación

- **FR-034**: Un usuario MUST poder cancelar el remanente pendiente de una orden propia. Lo
  ejecutado se conserva; la reserva de créditos o los tokens comprometidos del remanente se
  liberan.
- **FR-035**: Cancelar una orden ejecutada o ya cancelada MUST rechazarse como conflicto.
  Cancelar una orden de otro usuario o del superusuario MUST rechazarse y no cambiar nada; una
  orden inexistente MUST responder que no fue encontrada.

#### Portfolio

- **FR-036**: El portfolio de un usuario MUST informar, por cada jugador del que tiene tokens:
  cantidad de tokens, participación % del jugador, tokens comprometidos en ventas pendientes,
  precio promedio de compra, invertido, precio por token y score de la cotización usada, valor,
  ganancia o pérdida y rentabilidad %.
- **FR-037**: El precio promedio de compra MUST ser el ponderado por lo pagado (totales de las
  compras / tokens comprados), con 4 decimales; MUST NOT cambiar al vender y MUST reiniciarse
  cuando la tenencia del jugador llega a 0. El invertido MUST ser tokens × precio promedio; el
  valor, tokens × precio por token; la ganancia o pérdida, valor − invertido; y la rentabilidad,
  ganancia o pérdida / invertido × 100. Los montos van a 2 decimales.
- **FR-038**: El total del portfolio MUST informar saldo libre y reservado, invertido, valor de
  las posiciones, ganancia o pérdida, ganancia realizada acumulada en ventas y patrimonio (saldo
  libre + reservado + valor de las posiciones).
- **FR-039**: Sin fecha, el portfolio MUST calcularse con la cotización vigente de cada jugador;
  un jugador inactivo MUST valuarse con su última cotización.
- **FR-040**: Con fecha, las tenencias, el saldo y la ganancia realizada MUST reconstruirse con
  las operaciones del usuario hasta el final de ese día, hora de Argentina, y cada posición MUST
  valuarse con la cotización que regía en esa fecha (la "cotización a una fecha" de la 007,
  FR-043). El saldo a una fecha se informa total, sin separar libre de reservado, y sin tokens
  comprometidos.
- **FR-041**: Una fecha futura, con formato inválido o anterior al día de registro del usuario
  MUST rechazarse indicando el valor no admitido.
- **FR-042**: El portfolio MUST verlo solo su dueño o el administrador. Otro usuario MUST
  recibir un rechazo por falta de permiso; un usuario inexistente, que no fue encontrado.

#### Historial y órdenes propias

- **FR-043**: El historial de operaciones de un usuario MUST ser paginado (con la paginación del
  catálogo), de la más nueva a la más vieja, con filtros opcionales y combinables de jugador,
  tipo (compra o venta) y rango de fechas (ambos extremos incluidos). Un rango con el inicio
  posterior al final MUST rechazarse.
- **FR-044**: Cada operación del historial MUST informar fecha y hora, jugador, tipo desde el
  punto de vista del usuario, cantidad, precio por token, total, contraparte ("administrador" u
  "otro usuario", sin revelar quién), la orden propia a la que pertenece, la cotización usada
  (vigencia, score, estrategia y versión) y, en las ventas, la ganancia o pérdida realizada.
- **FR-045**: Un usuario MUST poder listar sus órdenes, paginadas y de la más nueva a la más
  vieja, con su jugador, tipo, estado y cantidades pedida, ejecutada y pendiente.
- **FR-046**: El historial y las órdenes de un usuario MUST verlos solo su dueño o el
  administrador, con los mismos rechazos que el portfolio (FR-042).

#### Listado del mercado

- **FR-047**: El listado del mercado MUST ser público, como el ranking, y MUST listar las
  órdenes con remanente pendiente (estado pendiente o parcial), de la más vieja a la más nueva,
  con la paginación del catálogo.
- **FR-048**: El listado MUST admitir filtros opcionales y combinables de tipo de orden (compra o
  venta), liga, equipo, tipo de usuario dueño (usuario común o administrador) y jugador. Un
  filtro con valor no admitido MUST rechazarse, igual que en el catálogo.
- **FR-049**: Cada orden del listado MUST informar jugador, equipo, liga, tipo de orden, tipo de
  usuario dueño, cantidades pedida, ejecutada y pendiente, estado y fecha, y MUST NOT revelar la
  identidad del dueño.

#### Auditoría

- **FR-050**: El sistema MUST registrar en una auditoría cada orden creada (también las
  generadas para el superusuario), cada operación ejecutada y cada cancelación (por el usuario o
  por saldo insuficiente), con autor (el usuario, el superusuario o el sistema), fecha y hora,
  detalle y estado anterior y posterior: saldo libre y reservado, y tenencia del jugador, de cada
  parte involucrada.
- **FR-051**: Los registros de auditoría MUST NOT poder modificarse ni borrarse por ninguna vía
  que ofrece el sistema.
- **FR-052**: Un pedido rechazado MUST NOT dejar registro de una orden creada.
- **FR-053**: Solo el administrador MUST poder consultar la auditoría, paginada y de la más nueva
  a la más vieja, con filtros opcionales de tipo de evento, usuario, jugador y rango de fechas.

#### API y permisos

- **FR-054**: La API MUST ofrecer, tal cual los nombra la consigna, `POST /orders/buy`,
  `POST /orders/sell`, `GET /users/{id}/portfolio` (con fecha opcional) y
  `GET /users/{id}/transactions`, y además las consultas del listado del mercado, de las órdenes
  propias, la cancelación de una orden y la consulta de la auditoría. Todo MUST quedar
  documentado en la especificación OpenAPI.
- **FR-055**: Comprar, vender, cancelar y consultar el portfolio, el historial y las órdenes
  propias MUST exigir credencial (token de sesión o clave de API); sin ella, MUST rechazarse por
  falta de credencial. El listado del mercado y las órdenes pendientes de un jugador MUST ser
  públicos.
- **FR-056**: Los rechazos MUST distinguirse: error de validación (forma del pedido), falta de
  credencial, falta de permiso, no encontrado y conflicto (saldo o tokens insuficientes, venta
  bloqueada por el superusuario, órdenes de sentido contrario, jugador no operable, orden ya
  cerrada), con el formato de error único del sistema y mensajes en español.
- **FR-057**: El perfil del usuario (001) MUST informar su saldo libre y reservado.

#### Score visible y cambios en la 004 y la 007

- **FR-058**: Toda cotización que informa la API (actual, a una fecha, evolución, ranking y
  portfolio) MUST incluir su score. Esto reemplaza el FR-006 de la 007 en lo que prohíbe exponer
  el score; las estadísticas crudas y las métricas normalizadas MUST seguir sin exponerse.
- **FR-059**: Esta funcionalidad levanta el FR-071 y el SC-020 de la 007 y el CE-009 de la 004 en
  lo que prohíben compra, venta, órdenes, portfolio, presupuesto, tokens y puntajes. Se mantiene
  la prohibición de "sumar al plantel".
- **FR-060**: El plan MUST listar cada test existente que cambia (`NoPurchaseActions.test.tsx`,
  `NoMarketActions.test.tsx` y los de la 007 que verifican que el score no se expone), qué
  aserciones cambian y por qué. Ningún test MUST modificarse ni borrarse hasta que el "sí"
  explícito del equipo figure en Clarifications.

#### Aplicación web

- **FR-061**: La aplicación MUST mantener la estética de la pizarra táctica, MUST ser usable
  desde 360 px de ancho sin desplazamiento horizontal de la página, y todo lo clickeable MUST
  ser un botón o un enlace con foco visible, usable con teclado.
- **FR-062**: Con sesión de usuario común, la aplicación MUST mostrar su saldo libre en todo
  momento y actualizarlo después de cada operación o cancelación.
- **FR-063**: La ficha del jugador MUST permitir comprar y vender a un usuario común con una
  confirmación previa que muestre cantidad, precio por token y total estimado (avisando que el
  precio final es el del momento de la ejecución), y MUST mostrar después el resultado real:
  tokens ejecutados, precio, total y remanente pendiente.
- **FR-064**: La ficha MUST mostrar las órdenes pendientes del jugador, incluida la del
  superusuario con los tokens que le quedan, y MUST avisar cuando todavía no se puede vender
  porque el superusuario tiene tokens. Con un jugador no operable, MUST explicar por qué no se
  puede operar.
- **FR-065**: Un visitante sin sesión y el administrador MUST NOT ver controles de compra ni de
  venta. La pizarra, la ficha, el ranking y el listado del mercado MUST seguir siendo públicos.
- **FR-066**: La pantalla "Mercado" MUST listar las órdenes con remanente pendiente con los
  filtros de FR-048 y la paginación; los filtros y la hoja MUST reflejarse en la dirección, con
  los mismos nombres que la pizarra y el ranking donde coincidan, y los filtros sin valor no
  aparecen.
- **FR-067**: La pantalla de portfolio MUST mostrar las posiciones y los totales de FR-036 a
  FR-038, con un selector de fecha para verlo a una fecha pasada y volver al actual; la fecha
  elegida MUST quedar en la dirección.
- **FR-068**: La pantalla de historial MUST mostrar las operaciones de FR-044 con sus filtros y
  paginación, reflejados en la dirección.
- **FR-069**: La pantalla de mis órdenes MUST listar las órdenes propias con estado y cantidades,
  y permitir cancelar el remanente de las pendientes o parciales con confirmación previa.
- **FR-070**: La ganancia o pérdida y la rentabilidad MUST distinguirse con signo y texto, no
  solo con color.
- **FR-071**: El score MUST mostrarse donde se muestra una cotización: ficha, gráfico (y su
  alternativa en texto), ranking, pizarra si muestra la cotización, y portfolio.
- **FR-072**: Las pantallas de portfolio, historial y mis órdenes MUST estar disponibles solo
  para usuarios comunes con sesión; un visitante MUST ser llevado a iniciar sesión. Todas las
  pantallas nuevas MUST tener estados de carga, de error con forma de reintentar y de lista
  vacía, y sus mensajes y etiquetas MUST estar en español.

### Key Entities

- **Token**: el 1 % de un jugador. Un jugador tiene 100. No tiene identidad propia: se cuenta
  por titular.
- **Tenencia**: cuántos tokens de un jugador tiene un titular (usuario o superusuario), con su
  precio promedio de compra. Para cada jugador, las tenencias suman 100.
- **Orden**: un pedido de compra o de venta de tokens de un jugador, de un titular, por una
  cantidad. Atributos: tipo, jugador, dueño, tipo de usuario dueño, cantidad pedida, ejecutada y
  pendiente, estado, fecha de creación, créditos reservados (compras) y si es la orden inicial
  del superusuario.
- **Operación**: una ejecución entre una compra y una venta: jugador, comprador, vendedor, sus
  órdenes, cantidad, precio por token, total, fecha y hora, cotización usada y ganancia o
  pérdida realizada del vendedor. Nunca se modifica ni se borra.
- **Saldo**: los créditos de un usuario, separados en libre y reservado.
- **Portfolio**: la vista calculada de las tenencias y el saldo de un usuario, valuada con la
  cotización vigente o con la de una fecha. No se guarda.
- **Registro de auditoría**: un hecho del mercado (orden creada, operación ejecutada,
  cancelación) con autor, fecha y hora, detalle y estado anterior y posterior de cada parte.
  Inmutable.
- **Superusuario**: la cuenta administradora de la 001 en su papel de vendedor inicial. Tiene
  tenencias y saldo como cualquier titular, pero no compra ni crea órdenes propias.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Después del arranque, el 100 % de los jugadores del catálogo tiene exactamente una
  orden del superusuario, y reiniciar la aplicación 3 veces seguidas deja 0 órdenes duplicadas.
- **SC-002**: En todo momento, para el 100 % de los jugadores, las tenencias suman 100, y 0
  saldos o tenencias son negativos, también después de 50 pedidos simultáneos sobre los mismos
  tokens.
- **SC-003**: La suma de los saldos de todos los titulares es siempre igual a la suma de los
  saldos iniciales otorgados al registrarse: 0 créditos creados o destruidos por el mercado.
- **SC-004**: El 100 % de las operaciones contra el superusuario se ejecutan a 1,0000 por token,
  y el 100 % de las operaciones entre usuarios al precio por token vigente en su momento.
- **SC-005**: Los ejemplos del spec (10 tokens a 1,0345 por 10,35; promedio 1,0100; ganancia
  realizada +0,20; reserva de 10,30 que baja a 6,18; 9 tokens ejecutados y 1 cancelado a 1,1000;
  portfolio al 25/08 a 1,0300) se reproducen exactamente, con 0 diferencias.
- **SC-006**: El 100 % de los pedidos de venta de un jugador cuya orden del superusuario tiene
  tokens pendientes se rechazan sin crear ninguna orden.
- **SC-007**: El 100 % de los intentos del superusuario de comprar, vender o cancelar sus órdenes,
  y de un usuario común de ver el portfolio, el historial, las órdenes de otro o la auditoría, se
  rechazan, y 0 de ellos cambia el estado del sistema.
- **SC-008**: Una persona compra tokens desde la ficha (confirmación incluida) y ve el resultado
  y su saldo actualizado en menos de 30 segundos.
- **SC-009**: Una orden se ejecuta y su resultado llega en menos de 2 segundos; el portfolio
  actual o a una fecha, una hoja del historial y una hoja del listado del mercado se ven en menos
  de 2 segundos, con el catálogo completo (alrededor de 2.600 jugadores y sus órdenes del
  superusuario).
- **SC-010**: El escenario 3 de la consigna (4 usuarios que compran tokens de 5 jugadores cada uno
  y ven su portfolio actual y a fechas pasadas) se completa de punta a punta, por API y desde la
  aplicación web.
- **SC-011**: El 100 % de las órdenes creadas, operaciones ejecutadas y cancelaciones tienen su
  registro de auditoría con estado anterior y posterior, y 0 registros se pueden modificar o
  borrar.
- **SC-012**: Con las dos fuentes externas caídas, el 100 % de las operaciones y consultas del
  mercado funciona.
- **SC-013**: El listado del mercado y el historial no revelan la identidad de ningún otro
  usuario en el 100 % de las respuestas.
- **SC-014**: El score aparece en el 100 % de las cotizaciones que muestran la API y las
  pantallas, y 0 estadísticas crudas o métricas normalizadas aparecen.
- **SC-015**: Las pantallas de mercado, portfolio, historial, mis órdenes y la ficha se usan desde
  360 px sin desplazamiento horizontal, el 100 % de lo clickeable se alcanza con teclado con foco
  visible, y 0 controles de compra o venta le resultan visibles a un visitante o al
  administrador.

## Assumptions

- Hora de Argentina es UTC−3, sin cambio de hora de verano, igual que en la 007.
- El saldo inicial de 1000 créditos y su configuración ya existen en la 001; esta funcionalidad
  no los cambia. Los usuarios registrados antes de esta funcionalidad conservan su saldo.
- El superusuario es la cuenta administradora de la 001; en el mercado es "el administrador" y
  aparece como tipo de usuario "administrador" en el listado y como contraparte en el historial.
- Para verificar el saldo de una compra, la parte contra el superusuario se cobra a 1 crédito y
  el remanente se reserva al precio vigente; la orden se rechaza entera si no alcanza para las
  dos cosas.
- En la ejecución de una compra pendiente a un precio mayor que el de su reserva, el comprador
  puede usar, además de la reserva, su saldo libre (FR-027). Es una interpretación de "si el
  saldo no alcanza" a confirmar en la clarificación.
- La mayor cantidad que alcanza es la mayor cantidad entera de tokens cuyo total redondeado
  (FR-003) no supera lo disponible.
- El portfolio a una fecha informa el saldo total, sin separar libre de reservado ni tokens
  comprometidos, porque las reservas no se reconstruyen hacia atrás. La fecha de hoy se admite.
- El listado del mercado muestra solo órdenes con remanente pendiente (estado pendiente o
  parcial), que es lo que sirve para ver qué queda de cada jugador; las ejecutadas y canceladas
  se ven en las órdenes propias y en la auditoría. Suma el filtro de jugador para alimentar la
  ficha.
- La operación contra el superusuario registra la cotización vigente de ese momento como
  referencia, aunque el precio sea 1,0000.
- El precio promedio de compra de los tokens comprados al superusuario es 1,0000.
- La rentabilidad de una posición con invertido 0 no se informa.
- Los montos de crédito van con 2 decimales y los precios por token con 4, hacia arriba desde la
  mitad.
- Al reactivarse un jugador sus órdenes quedan como estaban; como mientras estuvo inactivo no se
  aceptaron órdenes nuevas, normalmente no hay nada que cruzar, pero si lo hubiera se cruza
  (FR-033).
- La paginación es la del catálogo (002): la primera hoja es la 0, con su tamaño por defecto y
  máximo.
- La pantalla "Mercado" es pública. Las de portfolio, historial y mis órdenes son de usuarios
  comunes: el administrador no las ve en la web (las pantallas de administración del mercado
  están fuera de alcance), pero puede consultar por API el portfolio, el historial y la
  auditoría de cualquiera.
- El plan fija los nombres de los endpoints que no nombra la consigna, los nombres de los
  parámetros en la dirección y las tablas, con nombres explícitos (`order` y `transaction` son
  palabras reservadas), sin SQL nativo, en el contexto nuevo `market`, según la constitución
  2.2.1.
- Los tiempos de los criterios de éxito suponen alrededor de 2.600 jugadores y la base local del
  proyecto.
- Los mensajes de error se redactan en español, según el Principio VII de la constitución.

## Dependencies

- **Autenticación (001)**: aporta el saldo inicial configurable, los roles, la cuenta
  administradora de arranque (el superusuario, con saldo 0), las credenciales (token de sesión y
  clave de API) y el perfil, que suma el saldo reservado (FR-057).
- **Catálogo de jugadores (002)**: aporta la paginación, los filtros de liga y equipo y el
  rechazo de valores no admitidos, que reutilizan el listado del mercado y el historial.
- **Frontend de la pizarra táctica (004)**: aporta la estética, la ficha, las rutas, la sesión y
  el cliente. Esta funcionalidad modifica su CE-009 y el test `NoPurchaseActions.test.tsx` en lo
  que prohíben compra, venta, órdenes, portfolio, presupuesto, tokens y puntajes (FR-059). Se
  conserva la prohibición de "sumar al plantel".
- **Integración con Football-Data (005)**: aporta el estado activo o inactivo de cada jugador y
  la sincronización del catálogo, después de la cual se generan las órdenes del superusuario de
  los jugadores nuevos.
- **Estadísticas de WhoScored (006)**: dependencia indirecta, a través de la cotización de la
  007.
- **Cotización y estrategias (007)**: aporta la cotización vigente y a una fecha, el precio por
  token, la vigencia, el score, la estrategia y su versión, y la garantía de que nunca se ve una
  semana a medias. Esta funcionalidad modifica su FR-006 (el score pasa a mostrarse), su FR-071,
  su SC-020, su Fuera de Alcance y el test `NoMarketActions.test.tsx`, además de los tests que
  verifican que el score no se expone (FR-058 a FR-060).
- **Constitución 2.2.1**: el Principio I (contexto nuevo `market`), el Principio III (manejo
  único de errores y sus status code), el Principio IV (no se modifica ni se borra un test sin el
  "sí" explícito del equipo), el Principio VI (nombres de tabla explícitos, sin SQL nativo) y la
  sección Frontend (árbol de carpetas cerrado, tests en `frontend/tests/`, responsive,
  accesible).

## Fuera de Alcance

No forman parte de esta funcionalidad:

- La observabilidad general (logs estructurados, correlation id, métricas) y la auditoría de lo
  que no es del mercado.
- Las órdenes con precio límite: el precio lo fija siempre la cotización.
- La recompra por parte del superusuario.
- Las comisiones.
- La venta en corto.
- Las transferencias y las recargas de saldo.
- Las operaciones con fecha retroactiva.
- Mostrar estadísticas crudas o métricas normalizadas.
- Las pantallas de administración del mercado en la web.
- Las notificaciones.
