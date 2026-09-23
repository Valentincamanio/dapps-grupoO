# Especificación de funcionalidad: Catálogo de jugadores

**Rama de funcionalidad**: `002-catalogo-jugadores`  
**Creada**: 2026-09-16  
**Estado**: Borrador  
**Entrada**: Catálogo de jugadores de fútbol de las cinco principales ligas europeas, con filtros, paginación y detalle.

## Clarificaciones

### Sesión 2026-09-16

- P: ¿Cómo debe responder el catálogo si recibe un valor de filtro no admitido, por ejemplo una posición distinta de las cuatro permitidas? → R: Devolver `400 Bad Request` con el formato `ApiError`.
- P: ¿Con qué número debe comenzar la numeración de las páginas del catálogo? → R: La primera página es la 0.

## Escenarios de usuario y pruebas *(obligatorio)*

### Historia de usuario 1 - Explorar el catálogo paginado (Prioridad: P1)

Como persona usuaria, quiero consultar todos los jugadores disponibles en un catálogo paginado para conocer la oferta completa sin recibir una lista excesivamente extensa.

**Por qué esta prioridad**: Es el acceso principal al contenido del producto; sin él, el catálogo no aporta valor.

**Prueba independiente**: Se puede comprobar solicitando el catálogo sin filtros y verificando que se muestra una porción de jugadores junto con información suficiente para continuar o retroceder entre páginas.

**Escenarios de aceptación**:

1. **Dado** un catálogo inicializado, **cuando** una persona consulta la primera página sin filtros, **entonces** recibe jugadores del catálogo y datos para identificar la página actual, el tamaño de página, el total de resultados y las opciones de navegación disponibles.
2. **Dado** que existen más resultados que los incluidos en una página, **cuando** una persona solicita una página posterior válida, **entonces** recibe la porción correspondiente de jugadores sin repetir ni omitir los resultados de las demás páginas.
3. **Dado** que una persona consulta cualquier página del catálogo, **cuando** recibe los resultados, **entonces** cada jugador incluye nombre, posición, equipo y liga.

---

### Historia de usuario 2 - Acotar jugadores por criterios (Prioridad: P2)

Como persona usuaria, quiero filtrar el catálogo por liga, equipo y/o posición para encontrar rápidamente el grupo de jugadores que me interesa.

**Por qué esta prioridad**: Los filtros vuelven útil un catálogo de varias ligas al reducir resultados relevantes para la consulta de la persona.

**Prueba independiente**: Se puede comprobar aplicando cada filtro por separado y en combinación, y verificando que todos los resultados cumplen simultáneamente los criterios indicados.

**Escenarios de aceptación**:

1. **Dado** un catálogo con jugadores de las cinco ligas admitidas, **cuando** una persona filtra por una liga, **entonces** solo recibe jugadores pertenecientes a esa liga.
2. **Dado** un catálogo con equipos y posiciones variados, **cuando** una persona filtra por equipo y posición, **entonces** solo recibe jugadores de ese equipo que tienen esa posición.
3. **Dado** una consulta con filtros válidos que no tiene coincidencias, **cuando** se consulta el catálogo, **entonces** se recibe una página vacía y no se informa un error.

---

### Historia de usuario 3 - Consultar un jugador específico (Prioridad: P3)

Como persona usuaria, quiero ver el detalle de un jugador identificado del catálogo para confirmar sus datos.

**Por qué esta prioridad**: Complementa la exploración al permitir confirmar con precisión la información de un resultado individual.

**Prueba independiente**: Se puede comprobar consultando un jugador existente y otro inexistente, verificando respectivamente sus datos completos y una respuesta clara de no encontrado.

**Escenarios de aceptación**:

1. **Dado** un jugador existente, **cuando** una persona consulta su detalle, **entonces** recibe su nombre, posición, equipo y liga correctos.
2. **Dado** un identificador que no corresponde a ningún jugador, **cuando** una persona consulta el detalle, **entonces** el sistema informa claramente que el jugador no fue encontrado y no entrega datos de otro jugador.

### Casos límite

- Una consulta sin filtros devuelve jugadores de todas las ligas disponibles, respetando la paginación.
- La combinación de filtros se interpreta como una condición acumulativa: todo jugador devuelto satisface todos los filtros aplicados.
- Una liga, equipo o posición válidos que no producen coincidencias devuelven una página vacía, no un error.
- Un filtro con un valor no admitido devuelve `400 Bad Request` con el formato `ApiError`.
- Una solicitud de página fuera del rango de resultados devuelve una página vacía con metadatos de paginación consistentes.
- La primera página del catálogo se identifica con el número 0.
- No se admiten posiciones distintas de arquero, defensor, mediocampista y delantero en los datos del catálogo ni como valores de filtro.
- Una relación inconsistente entre equipo y liga no puede exponerse: cada equipo y cada jugador se asocian a una única liga.
- Reiniciar la aplicación no crea copias adicionales de jugadores ni equipos, ni altera los datos iniciales establecidos.

## Requisitos *(obligatorio)*

### Requisitos funcionales

- **RF-001**: El sistema DEBE ofrecer un catálogo inicial de entre 50 y 60 jugadores, distribuido entre Premier League (Inglaterra), Bundesliga (Alemania), La Liga (España), Serie A (Italia) y Ligue 1 (Francia), con entre 10 y 12 jugadores por liga.
- **RF-002**: El sistema DEBE conservar el catálogo disponible desde el primer inicio y permitir consultarlo sin depender de servicios externos durante la consulta.
- **RF-003**: Cada jugador DEBE tener un nombre, una posición, un equipo y una liga.
- **RF-004**: El sistema DEBE admitir exclusivamente las posiciones arquero, defensor, mediocampista y delantero.
- **RF-005**: Cada equipo DEBE tener un nombre y pertenecer a exactamente una de las cinco ligas del catálogo.
- **RF-006**: Cada jugador DEBE pertenecer a exactamente un equipo y, por esa relación, a una única liga; la liga informada para el jugador DEBE coincidir con la de su equipo.
- **RF-007**: El sistema DEBE permitir consultar el catálogo completo de jugadores de manera paginada.
- **RF-008**: Cada resultado paginado DEBE incluir los jugadores de la página solicitada y los datos necesarios para navegar: número de página actual, tamaño de página, total de resultados, total de páginas y disponibilidad de página anterior y siguiente.
- **RF-008a**: El sistema DEBE identificar la primera página del catálogo con el número 0.
- **RF-009**: El sistema DEBE permitir aplicar de forma opcional filtros por liga, equipo y posición al consultar el catálogo.
- **RF-010**: Cuando se aplican varios filtros, el sistema DEBE devolver solamente jugadores que cumplan todos los criterios indicados.
- **RF-011**: Cuando no se aplican filtros, el sistema DEBE devolver jugadores de todas las ligas disponibles.
- **RF-012**: Cuando una consulta con valores de filtro válidos no encuentra coincidencias, el sistema DEBE devolver un resultado paginado vacío y no tratarlo como error.
- **RF-013**: El sistema DEBE permitir consultar el detalle de un jugador específico mediante su identificador.
- **RF-014**: Si el jugador solicitado no existe, el sistema DEBE comunicar claramente que no fue encontrado, sin devolver datos de otro jugador ni un error genérico.
- **RF-015**: En inicios posteriores de la aplicación, el sistema DEBE mantener datos iniciales consistentes y no duplicar jugadores ni equipos existentes.
- **RF-016**: La funcionalidad NO DEBE incluir cotizaciones, estadísticas de rendimiento, historial de partidos, goles, asistencias, tarjetas, edad, nacionalidad, partidos, resultados, búsqueda por nombre, rankings, órdenes, compra o venta de tokens ni portfolio.
- **RF-017**: Cuando una consulta incluye un filtro con un valor no admitido, el sistema DEBE devolver `400 Bad Request` con el formato `ApiError`.

### Entidades clave

- **Jugador**: Persona incluida en el catálogo, identificada de forma única, con nombre, posición, equipo y liga.
- **Equipo**: Club identificado por su nombre que agrupa jugadores y pertenece a una única liga.
- **Liga**: Una de las cinco competiciones europeas admitidas; agrupa equipos y jugadores conforme a su pertenencia.
- **Resultado paginado del catálogo**: Conjunto de jugadores que corresponde a una página de consulta y sus datos de navegación.

## Criterios de éxito *(obligatorio)*

### Resultados medibles

- **CE-001**: Desde el primer inicio, el catálogo contiene entre 50 y 60 jugadores y cada una de las cinco ligas requeridas tiene entre 10 y 12 jugadores.
- **CE-002**: El 100 % de los jugadores mostrados incluye nombre, posición, equipo y liga, y utiliza una de las cuatro posiciones admitidas.
- **CE-003**: En pruebas con filtros individuales y combinaciones de liga, equipo y posición, el 100 % de los resultados devueltos cumple todos los filtros solicitados.
- **CE-004**: El 100 % de las consultas válidas sin coincidencias finaliza con un resultado vacío, sin error.
- **CE-005**: El 100 % de las consultas de detalle sobre jugadores inexistentes comunica que el jugador no fue encontrado y no presenta datos de un jugador distinto.
- **CE-006**: Tras tres reinicios consecutivos, la cantidad de jugadores y equipos se mantiene sin duplicados y las relaciones jugador-equipo-liga continúan siendo consistentes.
- **CE-007**: En una prueba de navegación por todas las páginas, cada jugador del catálogo puede ser alcanzado una vez mediante la paginación y no aparece fuera de los resultados que cumplen los filtros activos.

## Supuestos

- La consulta del catálogo es pública y no requiere autenticación, ya que el alcance no define roles ni restricciones de acceso.
- Los filtros se interpretan según los valores admitidos por el catálogo; cualquier valor no admitido produce `400 Bad Request` con el formato `ApiError`.
- Si no se especifican datos de paginación, se usa la página 0 con un tamaño predeterminado razonable; la interfaz de consulta permite elegir una página y tamaño de página válidos.
- Los identificadores internos de los jugadores son estables entre reinicios y se usan para acceder al detalle; no forman parte de una capacidad de búsqueda por nombre.
- La carga inicial incluye jugadores y equipos representativos, sin pretender ser una plantilla oficial, completa ni actualizada de las ligas.
- Las funcionalidades explícitamente fuera de alcance permanecen excluidas incluso si los datos existentes pudieran permitir inferirlas.
