# Especificación de funcionalidad: Frontend "Pizarra táctica" (catálogo de solo lectura)

**Rama de funcionalidad**: `004-frontend-pizarra-tactica`

**Creada**: 2026-10-07

**Estado**: Borrador

**Entrada**: Frontend web del mercado de jugadores con temática "pizarra táctica de vestuario", en modo CATÁLOGO de solo lectura, que respeta visualmente el diseño de referencia `frontend/mockups/pizarra-tactica.html` y consume únicamente las capacidades ya existentes de autenticación (001-auth-usuarios) y de catálogo de jugadores (002-catalogo-jugadores).

## Alcance

**Dentro del alcance**: registro, inicio de sesión, cuenta de la persona ("el vestuario"), catálogo paginado de jugadores ("la pizarra"), filtros por posición, liga y equipo, buscador de jugadores por nombre y ficha de un jugador. La interfaz usa exclusivamente estas capacidades ya existentes del sistema:

| Capacidad existente | Uso en esta funcionalidad |
|---------------------|---------------------------|
| Registro de usuario | Historia 1 |
| Inicio de sesión | Historia 1 |
| Consulta del perfil propio | Historia 6 (y verificación de sesión) |
| Cambio de contraseña | Historia 6 |
| Regeneración de la clave de API | Historia 6 |
| Catálogo paginado con filtros de liga, equipo y posición | Historias 2, 3 y 4 |
| Detalle de un jugador | Historia 5 |

No se agrega ninguna capacidad nueva al sistema existente.

**Fuera del alcance, de forma explícita** (en línea con RF-016 de `002-catalogo-jugadores`): precios, valores, cotizaciones, compras, ventas, órdenes, porcentajes de jugadores, portfolio, presupuesto, puntajes o "estrellas" de jugadores y armado de equipos. El diseño de referencia incluye elementos de este tipo (presupuesto, valor en créditos, estrellas, botón "+ al equipo", jugadores ubicados en la cancha, nota "no pasarse del presupuesto"): esos elementos NO se reproducen. Del diseño se toma la estética, no esas funciones.

## Escenarios de usuario y pruebas *(obligatorio)*

### Historia de usuario 1 - Acceso: registrarse e iniciar sesión (Prioridad: P1)

Como persona nueva o existente, quiero registrarme con usuario, correo y contraseña, o iniciar sesión con usuario y contraseña, para tener acceso a mi vestuario (historia 6). La pizarra, los filtros, el buscador y las fichas (historias 2 a 5) se pueden usar sin sesión; iniciar sesión solo es necesario para el vestuario. Al registrarme se me muestra una única vez mi clave de API, con opción de copiarla y una advertencia clara de que no se vuelve a mostrar. Las pantallas de acceso tienen la misma estética de pizarrón: el formulario se ve escrito en tiza.

**Por qué esta prioridad**: Sin acceso no se llega al vestuario; además, la clave de API solo puede entregarse en este momento.

**Prueba independiente**: Registrar una persona nueva, verificar que se muestra la clave con la advertencia y la opción de copiar, continuar y verificar que se llega a la pizarra; cerrar la sesión, iniciar sesión con esas credenciales y verificar que se llega a la pizarra.

**Escenarios de aceptación**:

1. **Dado** una persona sin cuenta en la pantalla de registro, **cuando** completa usuario, correo y contraseña válidos y confirma, **entonces** ve su clave de API una única vez, junto con una opción para copiarla y la advertencia "esta clave no se vuelve a mostrar".
2. **Dado** que la persona está viendo su clave recién emitida, **cuando** elige copiarla, **entonces** la clave queda copiada y se le confirma la copia con una nota en tiza.
3. **Dado** que la persona está viendo su clave recién emitida, **cuando** elige continuar, **entonces** queda con la sesión iniciada, llega a la pizarra y la clave ya no se puede volver a ver en ninguna pantalla.
4. **Dado** una persona registrada en la pantalla de inicio de sesión, **cuando** ingresa usuario y contraseña correctos, **entonces** llega a la pizarra.
5. **Dado** un registro rechazado por el sistema (por ejemplo, usuario o correo ya existentes, o una contraseña que no cumple las reglas), **cuando** se recibe el error, **entonces** cada mensaje se muestra como una nota en tiza junto al campo al que se refiere y los datos ingresados (salvo la contraseña) se conservan.
6. **Dado** credenciales incorrectas en el inicio de sesión, **cuando** se intenta entrar, **entonces** se muestra una nota en tiza con el mensaje del sistema y la persona permanece en la pantalla de acceso.
7. **Dado** una persona sin sesión, **cuando** intenta abrir directamente la dirección del vestuario, **entonces** se la lleva al inicio de sesión con el aviso "para entrar al vestuario tenés que iniciar sesión" y, al entrar, se la devuelve al vestuario.
8. **Dado** una persona sin sesión en la pizarra o en una ficha, **cuando** elige "entrar" en la barra superior e inicia sesión, **entonces** vuelve a la pantalla donde estaba, con los mismos filtros y la misma hoja.

---

### Historia de usuario 2 - La pizarra: catálogo completo (Prioridad: P1)

Como cualquier persona, con o sin sesión iniciada, quiero ver todos los jugadores del catálogo como post-its de colores clavados con chinches sobre el pizarrón, organizados en hojas que recorro con "hoja anterior" y "hoja siguiente", para conocer la oferta completa.

**Por qué esta prioridad**: Es la pantalla principal del producto y el destino natural después de entrar.

**Prueba independiente**: Sin sesión iniciada y sin filtros, recorrer todas las hojas y verificar que cada jugador del catálogo aparece exactamente una vez, que cada post-it muestra nombre, posición, equipo y liga, y que tocarlo abre su ficha.

**Escenarios de aceptación**:

1. **Dado** una persona, con o sin sesión iniciada, **cuando** abre la pizarra sin filtros, **entonces** ve la primera hoja de jugadores como post-its, cada uno con nombre, posición (en español), equipo y liga (con su nombre legible).
2. **Dado** que hay más jugadores que los que entran en una hoja, **cuando** elige "hoja siguiente" y luego "hoja anterior", **entonces** ve la hoja correspondiente sin repetir ni omitir jugadores, y se le indica en qué hoja está y cuántas hay ("hoja 2 de 6").
3. **Dado** que está en la primera hoja, **entonces** "hoja anterior" no está disponible; **dado** que está en la última, **entonces** "hoja siguiente" no está disponible.
4. **Dado** un post-it en la pizarra, **cuando** la persona lo toca o lo activa con el teclado, **entonces** se abre la ficha de ese jugador (historia 5).
5. **Dado** cualquier post-it, **entonces** no ofrece ninguna otra acción: no hay botones de comprar, vender, "sumar al plantel" ni similares (ni siquiera deshabilitados), no se puede seleccionar, marcar ni arrastrar, y no muestra precio, valor ni puntaje.

---

### Historia de usuario 3 - Filtrar por secciones de la cancha, liga y equipo (Prioridad: P1)

Como cualquier persona, con o sin sesión iniciada, quiero filtrar los post-its tocando una zona de la cancha dibujada en tiza (arco, defensa, mediocampo, delantera o "todos"), eligiendo una liga en las pestañas de cinta adhesiva (con una pestaña "todas") y eligiendo un equipo, para acotar la pizarra a lo que me interesa.

**Por qué esta prioridad**: Un catálogo de varias ligas sin filtros es difícil de recorrer; la cancha-filtro es además el elemento central del diseño.

**Prueba independiente**: Aplicar cada filtro por separado y combinados, verificar que todos los post-its cumplen los filtros activos, recargar la página y verificar que los filtros se mantienen, y abrir la dirección en otra pestaña y obtener la misma vista.

**Escenarios de aceptación**:

1. **Dado** la pizarra sin filtros, **cuando** la persona toca la zona "defensa", **entonces** solo se ven defensores, la zona queda marcada como activa y se vuelve a la primera hoja.
2. **Dado** una zona activa, **cuando** toca "todos", **entonces** se quita el filtro de posición y "todos" queda marcado como activo.
3. **Dado** la pizarra, **cuando** elige la pestaña de una liga, **entonces** solo se ven jugadores de esa liga, la pestaña queda marcada como activa y se vuelve a la primera hoja; la pestaña "todas" quita el filtro de liga.
4. **Dado** la pizarra, **cuando** elige un equipo, **entonces** solo se ven jugadores de ese equipo y se vuelve a la primera hoja. Si hay una liga elegida, la lista de equipos ofrecidos se limita a los de esa liga.
5. **Dado** filtros de posición, liga y equipo combinados, **entonces** cada post-it visible cumple todos los filtros a la vez.
6. **Dado** una combinación de filtros aplicada, **cuando** la persona recarga la página o comparte la dirección con otra persona, aunque no tenga sesión iniciada, **entonces** se ve la misma combinación de filtros y la misma hoja.
7. **Dado** una combinación sin jugadores, **entonces** se muestra una nota en tiza que describe la combinación (por ejemplo "no hay arqueros de ese equipo en la pizarra") y una opción para limpiar los filtros; al elegirla, se vuelve a la pizarra completa en la primera hoja.
8. **Dado** la cancha, **entonces** sus zonas son únicamente botones de filtro: ningún jugador se dibuja, se ubica ni se puede arrastrar dentro de la cancha.

---

### Historia de usuario 4 - Buscador de jugadores por nombre (Prioridad: P1)

Como cualquier persona, con o sin sesión iniciada, quiero un buscador escrito en tiza, siempre visible en la barra superior, para encontrar un jugador puntual por su nombre y abrir su ficha sin recorrer la pizarra.

**Por qué esta prioridad**: Es el camino más corto hacia un jugador conocido y está disponible desde todas las pantallas, sin necesidad de iniciar sesión.

**Prueba independiente**: Escribir "mbappe" en el buscador y verificar que se sugiere "Kylian Mbappé" con su posición y equipo; elegirlo solo con el teclado y verificar que se abre su ficha.

**Escenarios de aceptación**:

1. **Dado** el buscador vacío, **cuando** la persona escribe una sola letra, **entonces** no se muestran sugerencias.
2. **Dado** el buscador, **cuando** la persona escribe 2 o más letras, **entonces** se sugieren hasta 8 jugadores de todo el catálogo cuyo nombre contiene el texto, sin distinguir mayúsculas, minúsculas ni acentos ("mbappe" encuentra a "Mbappé"), y cada sugerencia muestra nombre, posición y equipo.
3. **Dado** un texto que coincide con más de 8 jugadores, **entonces** se muestran solo 8 sugerencias.
4. **Dado** un texto sin coincidencias, **entonces** se muestra "no hay nadie con ese nombre en la pizarra".
5. **Dado** sugerencias visibles, **cuando** la persona usa las flechas arriba/abajo, **entonces** se resalta la sugerencia correspondiente; **cuando** presiona Enter, **entonces** se abre la ficha del jugador resaltado; **cuando** presiona Escape, **entonces** se cierran las sugerencias y el foco queda en el buscador.
6. **Dado** sugerencias visibles, **cuando** la persona elige una con el mouse o el dedo, **entonces** se abre la ficha de ese jugador.
7. **Dado** que la búsqueda es por nombre, **entonces** no altera los filtros activos de la pizarra ni depende de ellos: busca siempre en todo el catálogo.

---

### Historia de usuario 5 - Ficha del jugador (Prioridad: P2)

Como cualquier persona, con o sin sesión iniciada, quiero ver la ficha de un jugador como una hoja de scout clavada al pizarrón, con su nombre, posición, equipo y liga, para confirmar sus datos y volver a la pizarra donde estaba.

**Por qué esta prioridad**: Complementa la pizarra y el buscador; sin ella, ambos siguen siendo útiles para explorar.

**Prueba independiente**: Abrir la dirección de la ficha de un jugador existente y verificar sus datos; abrir la de un identificador inexistente y verificar el mensaje "jugador no encontrado".

**Escenarios de aceptación**:

1. **Dado** un jugador existente, **cuando** se abre su ficha desde un post-it, desde el buscador o directamente desde su dirección, **entonces** se ven su nombre, posición, equipo y liga, obtenidos del detalle de ese jugador.
2. **Dado** que se abrió la ficha desde la pizarra con filtros y una hoja determinada, **cuando** la persona elige "volver a la pizarra", **entonces** vuelve a la pizarra con los mismos filtros y la misma hoja.
3. **Dado** que se abrió la ficha directamente desde su dirección o desde el buscador sin filtros previos, **cuando** elige "volver a la pizarra", **entonces** vuelve a la pizarra completa en la primera hoja.
4. **Dado** un identificador que no corresponde a ningún jugador (o que no es válido), **entonces** se informa "jugador no encontrado" y se ofrece volver a la pizarra.
5. **Dado** cualquier ficha, **entonces** es de solo lectura: no tiene acciones de compra, venta ni similares, ni muestra precio, valor ni puntaje.

---

### Historia de usuario 6 - El vestuario: la cuenta de la persona (Prioridad: P2)

Como persona con sesión iniciada (es la única sección que la exige), quiero una sección propia, "el vestuario", separada del catálogo y con su propia dirección, donde vea mi usuario, correo, rol y saldo, pueda cambiar mi contraseña, regenerar mi clave de API y cerrar sesión.

**Por qué esta prioridad**: Reúne la gestión de la cuenta y deja preparado el lugar donde, en una funcionalidad futura, se agregarán compras y ventas; el catálogo funciona sin ella.

**Prueba independiente**: Entrar al vestuario y verificar los datos del perfil; cambiar la contraseña e iniciar sesión con la nueva; regenerar la clave confirmando antes; cerrar sesión y verificar que se vuelve al inicio de sesión.

**Escenarios de aceptación**:

1. **Dado** una persona con sesión iniciada, **cuando** abre el vestuario, **entonces** ve su usuario, correo, rol (con etiqueta legible) y saldo real con dos decimales, sin ninguna acción asociada al saldo.
2. **Dado** el formulario de cambio de contraseña, **cuando** ingresa la contraseña actual correcta y una nueva válida, **entonces** se confirma el cambio con una nota en tiza y el formulario se vacía.
3. **Dado** el formulario de cambio de contraseña, **cuando** el sistema rechaza el cambio (contraseña actual incorrecta, nueva igual a la actual o que no cumple las reglas), **entonces** cada mensaje se muestra como nota en tiza junto al campo correspondiente.
4. **Dado** el vestuario, **cuando** la persona elige regenerar la clave de API, **entonces** primero se le pide confirmación advirtiendo que la clave actual dejará de funcionar; si cancela, no cambia nada.
5. **Dado** que confirmó la regeneración, **entonces** ve la clave nueva una única vez, con opción de copiarla y la advertencia de que no se vuelve a mostrar; al salir de esa vista, la clave ya no se puede volver a ver.
6. **Dado** el vestuario, **cuando** elige cerrar sesión, **entonces** la sesión termina, se vuelve a la pizarra sin sesión, que sigue funcionando completa (filtros, buscador y fichas), y el vestuario vuelve a pedir inicio de sesión.
7. **Dado** el vestuario abierto o una acción del vestuario en curso, **cuando** la sesión venció (24 h) o el sistema responde que la persona no está autenticada, **entonces** la sesión se descarta, se vuelve al inicio de sesión con el aviso "tu sesión venció, volvé a entrar" y, al entrar, se vuelve a la dirección donde estaba.

---

### Casos límite

- **Carga**: mientras se espera una respuesta (pizarra, ficha, buscador, vestuario, formularios), se muestra "el DT está pensando..." y los botones de envío no se pueden accionar dos veces.
- **Error de red o del servidor**: se muestra una nota en tiza que explica que no se pudo conectar y una opción "reintentar" que repite la última consulta, sin perder filtros, hoja ni datos ingresados.
- **Dirección con filtros inválidos** (posición o liga inexistentes, hoja negativa o no numérica): se ignora el valor inválido y se muestra la pizarra como si ese filtro no estuviera, sin pantalla de error.
- **Hoja fuera de rango** en la dirección (por ejemplo, hoja 40 de 6): se muestra la nota de pizarra vacía con la opción de volver a la primera hoja.
- **Equipo de la dirección que no pertenece a la liga elegida**: se muestra la nota en tiza de combinación sin jugadores, con la opción de limpiar filtros.
- **Cambio de liga con un equipo elegido de otra liga**: el filtro de equipo se quita al cambiar a una liga a la que ese equipo no pertenece.
- **Buscador con espacios** al principio o al final: se ignoran; el mínimo de 2 letras se cuenta sin esos espacios.
- **Buscador mientras el catálogo todavía no está disponible o falla**: se muestra "el DT está pensando..." o la nota de error con "reintentar" dentro del panel de sugerencias.
- **Copiar la clave cuando el dispositivo no permite copiar automáticamente**: la clave queda visible y seleccionable para copiarla a mano, y se avisa que la copia automática no funcionó.
- **Cerrar o recargar la vista de la clave recién emitida**: la clave no se recupera; se advierte antes de salir de esa vista que no se volverá a mostrar.
- **Ancho de 360 px**: la cancha-filtro va arriba en versión compacta y los post-its abajo en una sola columna; no hay desplazamiento horizontal.
- **Respuesta de no autenticado durante un formulario** (por ejemplo, al cambiar la contraseña): se vuelve al inicio de sesión con el aviso de sesión vencida.

## Requisitos *(obligatorio)*

### Requisitos funcionales

**Estética y alcance**

- **RF-001**: La interfaz DEBE respetar visualmente el diseño de referencia `frontend/mockups/pizarra-tactica.html`: pizarrón verde con marco de madera, cancha y flechas dibujadas en tiza, tipografías manuscritas, jugadores como post-its de colores clavados con chinches y ligas como pestañas de cinta adhesiva. Las pantallas de acceso, ficha y vestuario usan la misma estética.
- **RF-002**: La interfaz NO DEBE incluir ningún botón, enlace ni acción de comprar, vender, "sumar al plantel" o similar, ni siquiera deshabilitado, y NO DEBE mostrar precios, valores, cotizaciones, presupuesto, porcentajes, puntajes ni portfolio.
- **RF-003**: Ningún jugador DEBE poder seleccionarse, marcarse, arrastrarse ni ubicarse dentro de la cancha; la cancha DEBE servir solo como filtro.
- **RF-004**: La funcionalidad DEBE usar únicamente las capacidades existentes de registro, inicio de sesión, perfil propio, cambio de contraseña, regeneración de clave de API, catálogo paginado con filtros y detalle de jugador, sin requerir capacidades nuevas del sistema.
- **RF-005**: Todos los textos visibles DEBEN estar en español; las posiciones, ligas y roles DEBEN mostrarse con etiquetas legibles en español (por ejemplo "Arquero", "La Liga", "Usuario").

**Acceso y sesión**

- **RF-006**: La persona DEBE poder registrarse con usuario, correo y contraseña, y DEBE poder iniciar sesión con usuario y contraseña.
- **RF-007**: Tras un registro exitoso, la interfaz DEBE mostrar la clave de API una única vez, con una opción de copiarla y una advertencia visible de que no se vuelve a mostrar; la clave NO DEBE guardarse ni poder recuperarse después de abandonar esa vista.
- **RF-008**: Tras un registro exitoso y al continuar desde la vista de la clave, la persona DEBE quedar con la sesión iniciada y llegar a la pizarra sin volver a escribir sus credenciales.
- **RF-009**: Los errores que devuelve el sistema DEBEN mostrarse como notas en tiza junto al campo al que se refieren; los errores no asociados a un campo DEBEN mostrarse como una nota general del formulario.
- **RF-010**: La pizarra, los filtros, el buscador y las fichas NO DEBEN requerir sesión iniciada. El vestuario DEBE requerir sesión iniciada: sin sesión, DEBE llevarse a la persona al inicio de sesión con un aviso y, al entrar, devolverla al vestuario.
- **RF-011**: La sesión DEBE sobrevivir a una recarga de la página dentro de la misma pestaña y DEBE terminar al cerrar sesión, al vencer (24 h) o cuando el sistema responde que la persona no está autenticada; en los dos últimos casos, si la persona estaba en el vestuario, se DEBE llevarla al inicio de sesión con el aviso de sesión vencida, y si estaba en la pizarra o en una ficha, la pantalla DEBE seguir funcionando sin sesión.
- **RF-012**: Las credenciales, el token de sesión y las claves de API NO DEBEN aparecer en la dirección de la página ni en registros de diagnóstico.

**Pizarra y filtros**

- **RF-013**: La pizarra DEBE mostrar todos los jugadores del catálogo como post-its, organizados en hojas, con controles "hoja anterior" y "hoja siguiente" y una indicación de hoja actual y total de hojas.
- **RF-014**: Cada post-it DEBE mostrar nombre, posición, equipo y liga, y su única acción DEBE ser abrir la ficha del jugador.
- **RF-015**: La pizarra DEBE ofrecer una cancha dibujada en tiza con cuatro zonas (arco, defensa, mediocampo, delantera) y una opción "todos"; activar una zona DEBE filtrar por la posición correspondiente (arquero, defensor, mediocampista, delantero) y marcarla como activa.
- **RF-016**: La pizarra DEBE ofrecer pestañas de liga para las cinco ligas del catálogo y una pestaña "todas", y DEBE ofrecer un filtro por equipo cuyas opciones son los equipos presentes en el catálogo, limitadas a la liga elegida si la hay.
- **RF-017**: Los filtros de posición, liga y equipo DEBEN combinarse de forma acumulativa.
- **RF-018**: Los filtros activos y la hoja actual DEBEN reflejarse en la dirección de la página, de modo que una recarga o una dirección compartida reproduzcan la misma vista.
- **RF-019**: Al cambiar cualquier filtro, la pizarra DEBE volver a la primera hoja.
- **RF-020**: Cuando la combinación de filtros no tiene jugadores, la pizarra DEBE mostrar una nota en tiza que describa la combinación y una opción para limpiar todos los filtros.

**Buscador**

- **RF-021**: Todas las pantallas, con o sin sesión, DEBEN mostrar un buscador escrito en tiza en la barra superior.
- **RF-022**: A partir de 2 letras (sin contar espacios de los extremos), el buscador DEBE sugerir hasta 8 jugadores de todo el catálogo cuyo nombre contiene el texto, sin distinguir mayúsculas, minúsculas ni acentos; cada sugerencia DEBE mostrar nombre, posición y equipo.
- **RF-023**: Elegir una sugerencia DEBE abrir la ficha del jugador; sin coincidencias, DEBE mostrarse "no hay nadie con ese nombre en la pizarra".
- **RF-024**: El buscador DEBE poder usarse solo con teclado: flechas para recorrer sugerencias, Enter para abrir la resaltada y Escape para cerrar las sugerencias.
- **RF-025**: Como el sistema no ofrece búsqueda por nombre, el buscador DEBE resolver las coincidencias sobre el catálogo completo obtenido de la consulta paginada existente.

**Ficha del jugador**

- **RF-026**: Cada jugador DEBE tener una ficha con dirección propia que muestre nombre, posición, equipo y liga obtenidos de la consulta de detalle de ese jugador.
- **RF-027**: La ficha DEBE ofrecer "volver a la pizarra" que restaure los filtros y la hoja desde los que se abrió, o la pizarra completa si no había.
- **RF-028**: Si el jugador no existe o el identificador no es válido, la ficha DEBE informar "jugador no encontrado" y ofrecer volver a la pizarra.

**Vestuario**

- **RF-029**: El vestuario DEBE ser una pantalla con dirección propia, separada de la pizarra. La barra superior DEBE mostrar un acceso "entrar" cuando no hay sesión y un acceso "vestuario" cuando la hay.
- **RF-030**: El vestuario DEBE mostrar usuario, correo, rol y saldo real (con dos decimales) obtenidos del perfil propio; el saldo NO DEBE tener acciones asociadas.
- **RF-031**: El vestuario DEBE permitir cambiar la contraseña indicando la actual y la nueva, y confirmar el resultado con una nota en tiza.
- **RF-032**: El vestuario DEBE permitir regenerar la clave de API, previa confirmación explícita que advierta que la clave actual deja de funcionar, y mostrar la clave nueva una única vez con opción de copiarla y la misma advertencia de RF-007.
- **RF-033**: El vestuario DEBE permitir cerrar sesión.

**Estados, accesibilidad y presentación**

- **RF-034**: Toda espera de una respuesta DEBE mostrar el estado de carga "el DT está pensando..." y DEBE impedir el doble envío de formularios.
- **RF-035**: Todo error de red o del servidor DEBE mostrarse con una nota en tiza y una opción "reintentar" que repita la consulta sin perder filtros, hoja ni datos ingresados.
- **RF-036**: Las zonas de la cancha, las pestañas de liga, el filtro de equipo, los post-its, la paginación, el buscador y los formularios DEBEN poder usarse sin mouse, con foco visible en todo elemento accionable; todo lo accionable DEBE ser un botón o un enlace.
- **RF-037**: Las zonas de la cancha y las pestañas de liga DEBEN comunicar a tecnologías de asistencia cuál está activa.
- **RF-038**: La interfaz DEBE ser usable desde 360 px de ancho sin desplazamiento horizontal; en anchos de celular, la cancha-filtro DEBE ubicarse arriba en versión compacta y los post-its abajo en una sola columna.

**Integración continua**

- **RF-039**: Los tests, el análisis de estilo (lint) y la compilación del frontend DEBEN ejecutarse automáticamente en cada push y en cada pull request hacia `main` y `develop`, igual que el backend, y el pipeline DEBE fallar si cualquiera de ellos falla.

### Entidades clave

- **Sesión**: Estado de la persona autenticada; incluye el token de sesión y su vencimiento. Vive solo mientras dure la pestaña o hasta cerrar sesión o vencer.
- **Perfil**: Datos propios de la persona: usuario, correo, rol y saldo.
- **Clave de API**: Credencial alternativa entregada en el registro o al regenerarla; se muestra una única vez y nunca se guarda en la interfaz.
- **Jugador**: Elemento del catálogo con identificador, nombre, posición, equipo y liga; se representa como post-it en la pizarra, como sugerencia en el buscador y como hoja de scout en la ficha.
- **Filtros de la pizarra**: Combinación de posición, liga, equipo y hoja actual; se refleja en la dirección de la página.
- **Hoja de la pizarra**: Porción paginada del catálogo filtrado, con número de hoja, total de hojas y disponibilidad de hoja anterior y siguiente.
- **Error del sistema**: Respuesta de error con un mensaje general y, cuando corresponde, mensajes asociados a campos concretos.

## Criterios de éxito *(obligatorio)*

### Resultados medibles

- **CE-001**: Una persona nueva completa el registro, guarda su clave de API y llega a la pizarra en menos de 2 minutos.
- **CE-002**: En un recorrido por todas las hojas sin filtros, el 100 % de los jugadores del catálogo aparece exactamente una vez.
- **CE-003**: En pruebas con cada filtro y con combinaciones de posición, liga y equipo, el 100 % de los post-its visibles cumple todos los filtros activos.
- **CE-004**: El 100 % de las vistas filtradas se reproduce idéntica (filtros y hoja) al recargar la página o al abrir la dirección en otra pestaña, con o sin sesión.
- **CE-005**: Una persona encuentra y abre la ficha de un jugador cuyo nombre conoce en menos de 10 segundos usando el buscador, incluso escribiéndolo sin acentos.
- **CE-006**: Las sugerencias del buscador aparecen en menos de 1 segundo desde que se escribe la segunda letra, una vez cargado el catálogo.
- **CE-007**: Todos los recorridos de las seis historias pueden completarse solo con teclado, con foco visible en cada paso.
- **CE-008**: Todas las pantallas son usables a 360 px de ancho sin desplazamiento horizontal.
- **CE-009**: Una revisión de todas las pantallas encuentra 0 acciones o datos de compra, venta, precio, valor, presupuesto, puntaje o portfolio.
- **CE-010**: La clave de API no puede volver a verse en ninguna pantalla después de abandonar la vista donde se emitió (0 casos en pruebas).
- **CE-011**: Ante una sesión vencida o rechazada, el 100 % de los intentos de usar el vestuario lleva al inicio de sesión con el aviso correspondiente.
- **CE-012**: El 100 % de los push y pull requests a `main` y `develop` ejecutan tests, lint y compilación del frontend, y un fallo en cualquiera de ellos marca el pipeline como fallido.
- **CE-013**: Una persona sin sesión completa los recorridos de las historias 2 a 5 (pizarra, filtros, buscador y ficha) sin que se le pida iniciar sesión en ningún paso (0 pedidos), y el 100 % de los intentos de abrir el vestuario sin sesión llevan al inicio de sesión.

## Supuestos

- El catálogo tiene entre 50 y 60 jugadores (RF-001 de `002-catalogo-jugadores`), por lo que el buscador y la lista de equipos pueden armarse obteniendo el catálogo completo con la consulta paginada existente (como máximo 50 jugadores por consulta), sin agregar una búsqueda por nombre al sistema. RF-016 de 002 excluye la búsqueda por nombre del sistema, no de la interfaz.
- La pizarra, los filtros, el buscador y las fichas son públicos, igual que la consulta del catálogo en el sistema (supuesto de `002-catalogo-jugadores`); solo el vestuario exige sesión, porque es lo único que usa el perfil y la gestión de la cuenta.
- El registro no inicia sesión por sí mismo (no devuelve token); al continuar desde la vista de la clave, la interfaz inicia sesión con las credenciales recién ingresadas, sin guardarlas.
- La hoja de la pizarra muestra una cantidad fija razonable de post-its (por defecto, la del sistema: 10); la persona no elige el tamaño de hoja.
- En la dirección de la página, la hoja se muestra numerada desde 1 para la persona, aunque el sistema numere desde 0.
- La sesión se conserva al recargar dentro de la misma pestaña, pero no se comparte entre pestañas ni sobrevive al cierre del navegador, según la regla de la constitución sobre el token de sesión.
- El saldo se muestra tal como lo informa el perfil, en créditos y con dos decimales; no se actualiza en tiempo real.
- Las reglas de validación de usuario, correo y contraseña son las del sistema (001-auth-usuarios); la interfaz puede adelantar validaciones de forma para guiar a la persona, pero el mensaje que manda es el del sistema.
- Los post-its toman su color de la posición del jugador para que la pizarra sea legible de un vistazo; el color no es la única señal de la posición (también se muestra en texto).
- El pipeline de integración continua del backend ya existe en GitHub Actions; el del frontend se agrega con los mismos disparadores.
- No se requiere soporte para navegadores sin JavaScript ni modo sin conexión.
