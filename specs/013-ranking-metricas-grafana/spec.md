# Feature Specification: Ranking para alta frecuencia, métricas del mercado y dashboard de Grafana

**Feature Branch**: `013-ranking-metricas-grafana`

**Created**: 2026-11-24

**Status**: Draft

**Input**: User description: "Optimización del ranking de jugadores para alta frecuencia de consultas, métricas de negocio del mercado y un dashboard de Grafana listo para usar. Cubre dos ítems de la entrega 3: 'Optimizar Ranking de jugadores según estrategia activa para alta frecuencia de consultas' y 'Endpoint de métricas avanzadas'. El caché del ranking suma un tope de entradas, un vencimiento de seguridad y métricas de aciertos y fallos, conserva la invalidación actual y no cambia el resultado; se suman tres métricas de negocio (operaciones ejecutadas, tokens transaccionados y créditos movidos) por el mismo canal que las de la 012; y se versiona un dashboard de Grafana que aparece solo al levantarlo, sin Docker y sin credenciales en el repositorio. Fuera de alcance: Redis o caché distribuida, cambios a la consulta del ranking o a sus índices, un endpoint REST propio de métricas, alertas y métricas por jugador, liga o usuario."

## Contexto

Esta feature cubre dos ítems de la entrega 3.

**"Optimizar Ranking de jugadores según estrategia activa para alta frecuencia de consultas".**
El ranking público de jugadores por cotización vigente (`GET /players/ranking`, según la
estrategia activa) ya se guarda en memoria desde la feature 007, pero de forma simple: crece sin
límite, sus entradas nunca vencen y no informa nada sobre su uso. Se vacía entero después de cada
recálculo de cotizaciones, recotización o cambio de la estrategia activa, siempre después de
confirmado el cambio. **Esa invalidación no cambia.** Lo que cambia es que el caché queda acotado,
con un vencimiento de seguridad y observable. El otro caché de cotizaciones, el de la cotización
actual de cada jugador (`GET /players/{id}/quotes/current`), tiene el mismo problema y recibe el
mismo tratamiento.

**"Endpoint de métricas avanzadas".** El docente aclaró que significa sumar métricas del negocio
por el canal de métricas que ya existe, el de la feature 012 (Actuator y el formato de Prometheus),
por ejemplo cantidad de tokens transaccionados y cantidad de operaciones realizadas. **No es un
endpoint REST nuevo.** La 012 ya publica órdenes pedidas (compra, venta y cancelación, aceptadas o
rechazadas), órdenes abiertas, usuarios registrados, logins y registros; esta feature suma lo que
efectivamente se opera.

Para que esas métricas se puedan ver, el repositorio ya trae un ejemplo de Prometheus local
(`monitoring/prometheus.yml`), pero no hay forma de verlas en gráficos. Esta feature suma un
dashboard de Grafana versionado que aparece armado al levantar Grafana.

## Clarifications

### Session 2026-11-24

- Q: Si una consulta del ranking está en curso justo cuando se confirma un recálculo, una
  recotización o un cambio de estrategia, ¿puede guardar el resultado viejo en el caché? → A: No.
  Garantía estricta: ninguna consulta que empieza después de confirmado el cambio recibe el
  resultado viejo, aunque otra consulta en curso lo haya guardado; se demuestra con un test de
  concurrencia (FR-005, Edge Cases, SC-003).
- Q: ¿Cómo se hace la medición del tiempo de respuesta del ranking y dónde queda el resultado?
  → A: Con un script repetible, sin dependencias nuevas, contra el backend levantado en local: N
  consultas por variante, con promedio y percentil 95. La variante del caché anterior se mide antes
  de tocar el código. Resultados y procedimiento en un documento de la carpeta de la spec,
  enlazado desde el README. No hay test automático de tiempos (FR-010, SC-001).
- Q: ¿Las métricas de operaciones, tokens y créditos distinguen si la venta vino de la orden
  inicial del superusuario o de otro usuario? → A: Sí, con una etiqueta de contraparte de dos
  valores cerrados; las dos series existen en cero desde el arranque y el dashboard muestra el
  total y la apertura (FR-013, FR-017, FR-020, FR-024).
- Q: ¿Cómo se define el usuario y la contraseña del administrador de Grafana? → A: Se versiona
  una configuración de ejemplo sin contraseña; la real va en un archivo local ignorado por git
  que el README explica cómo crear, como `monitoring/admin-api-key`. Sin ese archivo, Grafana usa
  su comportamiento por defecto (FR-028, FR-029).
- Q: ¿El caché de la cotización actual de cada jugador también recibe tope, vencimiento de
  seguridad y métricas de aciertos y fallos? → A: Sí, con sus propios valores; sigue devolviendo
  lo mismo, se vacía en los mismos momentos y tiene la misma garantía estricta. El dashboard
  muestra solo el del ranking (FR-030, Key Entities, SC-004, Assumptions).

### Session 2026-11-25

- Q: La medición dio un factor sin caché / caché nuevo de 2,41 en el servidor y 1,51 en el cliente,
  por debajo de las 5 veces de la primera versión de SC-001. ¿Se busca otro umbral? → A: No. Cada
  pedido tiene un costo fijo de unos 3,4 ms (seguridad, auditoría, métrica HTTP y JSON) que el
  caché no toca, así que el factor depende del costo de la consulta a la base y no del caché. SC-001
  pasa a pedir que las consultas repetidas no vayan a la base (SC-002) y que el tiempo medio del
  servidor baje respecto de sin caché, informando el factor y el ahorro absoluto medidos, sin otro
  umbral. La comparación con el caché anterior dentro del ruido se mantiene.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Ranking rápido bajo consultas frecuentes (Priority: P1)

Un visitante consulta el ranking de jugadores muchas veces, con las mismas hojas y los mismos
filtros. Las consultas repetidas se responden sin ir a la base cada vez, con exactamente el mismo
resultado que hoy, y el sistema nunca le sirve un ranking de una estrategia o de cotizaciones que
ya no están vigentes. La memoria que ocupa el caché tiene un techo, y su uso se ve en las métricas.

**Why this priority**: es un ítem explícito de la entrega 3 y la consulta pública más pedida. Sin
tope ni vencimiento, el caché actual puede crecer sin control y, si alguna invalidación fallara,
serviría datos viejos para siempre.

**Independent Test**: se pide varias veces la misma hoja del ranking y se verifica que solo la
primera llega a la base, que la métrica de aciertos y fallos lo refleja y que el resultado es
idéntico al de hoy; después se dispara cada uno de los tres cambios (recálculo, recotización,
cambio de estrategia activa) y se verifica que la siguiente consulta trae los datos nuevos.

**Acceptance Scenarios**:

1. **Given** el ranking con cotizaciones vigentes, **When** un visitante pide dos veces la misma
   hoja con los mismos filtros, **Then** las dos respuestas son idénticas, la segunda no consulta
   la base y la métrica del caché suma un fallo y un acierto.
2. **Given** una hoja del ranking ya guardada en el caché, **When** se confirma un recálculo de
   cotizaciones, **Then** la siguiente consulta de esa hoja consulta la base y devuelve las
   cotizaciones nuevas.
3. **Given** una hoja del ranking ya guardada en el caché, **When** se confirma una recotización,
   **Then** la siguiente consulta de esa hoja devuelve las cotizaciones de la recotización.
4. **Given** una hoja del ranking ya guardada en el caché, **When** se confirma un cambio de la
   estrategia activa, **Then** la siguiente consulta de esa hoja informa la estrategia nueva y el
   orden que corresponde a ella.
5. **Given** el caché lleno hasta su tope, **When** llega una consulta con una combinación de
   hoja, tamaño y filtros que no está guardada, **Then** la respuesta es correcta, se guarda, y la
   cantidad de entradas no supera el tope.
6. **Given** una hoja guardada en el caché y ningún cambio de cotizaciones ni de estrategia,
   **When** pasa el vencimiento de seguridad, **Then** la siguiente consulta de esa hoja vuelve a
   la base y devuelve el mismo resultado.
7. **Given** las mismas consultas del ranking con cualquier combinación de filtros, hoja y tamaño
   que se usa hoy, **When** se comparan las respuestas antes y después de esta feature, **Then**
   el contenido, el orden, los datos de paginación y la estrategia informada son exactamente los
   mismos.
8. **Given** un pedido del ranking con parámetros inválidos (hoja negativa o tamaño fuera de
   rango), **When** se procesa, **Then** responde el mismo error de hoy, no ocupa una entrada del
   caché y no cuenta como acierto ni como fallo.

---

### User Story 2 - Métricas de negocio del mercado (Priority: P1)

Un administrador que monitorea el sistema quiere ver cuánto se opera en el mercado: cuántas
operaciones se ejecutaron, cuántos tokens cambiaron de mano y cuántos créditos se pagaron por
ellos. Las ve por el mismo canal y con el mismo control de acceso que el resto de las métricas de
la 012.

**Why this priority**: es el otro ítem explícito de la entrega 3. Las métricas de la 012 cuentan
pedidos de órdenes, pero no lo que efectivamente se ejecuta: una compra puede quedar pendiente,
cruzarse contra varias ventas o cruzarse recién más tarde.

**Independent Test**: con el backend recién levantado se verifica que las tres métricas existen
en cero; se arma un escenario con compras, ventas, cancelaciones y pedidos rechazados, y se
verifica que las tres métricas coinciden exactamente con las operaciones que quedaron guardadas en
el mercado.

**Acceptance Scenarios**:

1. **Given** el backend recién levantado, **When** un administrador consulta las métricas,
   **Then** las de operaciones ejecutadas, tokens transaccionados y créditos movidos existen y
   valen cero.
2. **Given** la orden inicial del superusuario para un jugador, **When** un usuario compra 3
   tokens y la compra se cruza contra esa orden, **Then** operaciones suma 1, tokens suma 3 y
   créditos suma el total pagado en esa operación, todo en la serie de la orden inicial del
   superusuario.
3. **Given** dos órdenes de venta de distintos usuarios para el mismo jugador, **When** una compra
   se cruza contra las dos, **Then** operaciones suma 2, tokens suma la cantidad total ejecutada y
   créditos suma la suma de los totales de las dos operaciones, todo en la serie de venta de un
   usuario.
4. **Given** una orden de venta que no encuentra compra, **When** queda pendiente, **Then** las
   tres métricas no cambian; **And When** más tarde una compra se cruza contra ella, **Then**
   suman exactamente la operación que la ejecuta, una sola vez.
5. **Given** una compra o venta que el mercado rechaza (saldo insuficiente, tokens
   insuficientes, jugador sin cotización u otra regla), **When** se procesa, **Then** las tres
   métricas no cambian.
6. **Given** una compra cuya grabación falla después de haberse armado el cruce, **When** el
   cambio no queda confirmado, **Then** las tres métricas no cambian.
7. **Given** una orden pendiente, **When** el usuario la cancela, **Then** las tres métricas no
   cambian.
8. **Given** un visitante sin credencial o un usuario sin rol de administrador, **When** pide las
   métricas, **Then** recibe el mismo rechazo que hoy para las métricas de la 012.
9. **Given** cualquier compra, venta o cancelación, **When** se compara su resultado con y sin
   las métricas nuevas, **Then** la respuesta, los saldos, los tokens y los registros de auditoría
   son exactamente los mismos.

---

### User Story 3 - Dashboard de Grafana listo para usar (Priority: P2)

Un integrante del equipo o el docente levanta Prometheus y Grafana en su máquina, siguiendo el
README y sin Docker, y encuentra un dashboard ya armado con los números del negocio, del ranking
y del tráfico general, sin configurar nada a mano en la interfaz de Grafana.

**Why this priority**: es lo que hace visibles las métricas de las dos historias anteriores y de
la 012, pero el valor de esas métricas existe aunque no haya dashboard.

**Independent Test**: con el backend y Prometheus corriendo, se levanta Grafana con la
configuración del repositorio y se verifica que la conexión a Prometheus y el dashboard aparecen
solos y que sus paneles muestran datos después de unas consultas y operaciones.

**Acceptance Scenarios**:

1. **Given** el backend y Prometheus corriendo como explica el README, **When** se levanta
   Grafana con la configuración del repositorio, **Then** la conexión a Prometheus y el dashboard
   ya están disponibles, sin pasos manuales en la interfaz.
2. **Given** el dashboard abierto, **When** se hacen compras y ventas que se ejecutan, **Then**
   los paneles de operaciones, tokens y créditos muestran el aumento.
3. **Given** el dashboard abierto, **When** se consulta el ranking varias veces, **Then** los
   paneles del ranking muestran su tiempo de respuesta y el porcentaje de respuestas desde el
   caché.
4. **Given** el dashboard abierto, **When** se hacen pedidos al backend, algunos con error,
   **Then** los paneles generales muestran los pedidos y los errores.
5. **Given** el backend se reinicia y sus contadores vuelven a cero, **When** se mira el
   dashboard, **Then** los paneles siguen mostrando valores coherentes, sin saltos negativos.
6. **Given** el repositorio clonado, **When** se busca cualquier credencial (contraseñas, claves
   de API, tokens), **Then** no hay ninguna versionada; las que hagan falta están nombradas en
   archivos ignorados por git y el README explica cómo crearlas.

---

### Edge Cases

- **Invalidación con una consulta en vuelo**: una consulta del ranking que empezó antes de
  confirmarse un cambio puede terminar después del vaciado con el resultado viejo. Ese resultado
  puede llegarle a esa misma consulta, pero NUNCA a una consulta que empieza después de confirmado
  el cambio: un resultado calculado con los datos anteriores no se sirve más, aunque haya quedado
  guardado.
- **Fallo de una invalidación**: si alguna vez el vaciado no ocurre, ninguna entrada sobrevive más
  que el vencimiento de seguridad.
- **Muchas combinaciones distintas de filtros, hoja y tamaño**: el caché nunca guarda más
  entradas que su tope; al llegar a él descarta las que menos se usan y el resultado de cada
  consulta sigue siendo correcto.
- **Ranking vacío** (ningún jugador con cotización o filtros sin resultados): la hoja vacía se
  responde igual que hoy y puede guardarse en el caché como cualquier otra.
- **Consultas simultáneas de la misma hoja sin guardar**: todas reciben el resultado correcto;
  que más de una llegue a la base es aceptable.
- **Compra o venta que se cruza parcialmente**: suma solo los tokens y créditos efectivamente
  ejecutados; el resto pendiente no suma nada hasta que se cruce.
- **Operación contra la orden inicial del superusuario**: cuenta igual que una entre usuarios.
- **Reinicio del backend**: las métricas son en memoria y vuelven a cero, como las de la 012; el
  historial queda en Prometheus.
- **Prometheus caído o sin datos**: el dashboard muestra los paneles sin datos, sin errores que
  impidan abrirlo.
- **Grafana levantado sin Prometheus**: la conexión figura configurada aunque no responda; al
  levantarse Prometheus, los paneles se llenan solos.

## Requirements *(mandatory)*

### Functional Requirements

**Caché del ranking (US1)**

- **FR-001**: El caché del ranking DEBE tener un tope de entradas. Al llegar al tope, DEBE
  descartar entradas para hacer lugar, priorizando conservar las más usadas.
- **FR-002**: Cada entrada del caché del ranking DEBE vencer después de un tiempo de seguridad
  desde que se guardó, aunque no haya ocurrido ningún cambio.
- **FR-003**: El tope y el vencimiento DEBEN ser configurables, con valores por defecto
  (ver Assumptions), y validarse al arrancar: un valor inválido impide arrancar con un mensaje
  claro.
- **FR-004**: El sistema DEBE seguir vaciando el caché del ranking después de confirmar cada
  recálculo de cotizaciones, cada recotización y cada cambio de la estrategia activa, en los
  mismos momentos que hoy.
- **FR-005**: Una consulta del ranking que empieza después de confirmado uno de esos cambios
  NUNCA DEBE recibir un resultado calculado con la estrategia o las cotizaciones anteriores,
  tampoco cuando una consulta que estaba en curso durante el cambio guarda su resultado en el
  caché después del vaciado. El vencimiento de seguridad no es parte de esta garantía: es solo
  una red por si falla la invalidación.
- **FR-006**: El resultado del ranking (contenido, orden, datos de paginación, filtros aplicados
  y estrategia informada) DEBE ser exactamente el mismo que hoy para cualquier combinación de
  parámetros válida.
- **FR-007**: Los pedidos del ranking con parámetros inválidos DEBEN responder el mismo error que
  hoy, sin ocupar entradas del caché ni contar como acierto o fallo.
- **FR-008**: El sistema DEBE publicar, por el mismo canal y con el mismo control de acceso que
  las métricas de la 012, cuántas consultas del ranking se respondieron desde el caché (aciertos)
  y cuántas tuvieron que ir a la base (fallos), desde cero al arrancar.
- **FR-009**: El tiempo de respuesta del ranking DEBE poder verse por separado del resto de los
  pedidos en las métricas técnicas que ya publica la 012.
- **FR-010**: La mejora DEBE demostrarse con una medición del tiempo de respuesta del ranking que
  compare tres variantes: consultas que van siempre a la base, el caché anterior y el caché nuevo
  (ver SC-001). La medición:
  - se hace con un script repetible, versionado y sin dependencias nuevas, contra el backend
    levantado en local, con la misma cantidad de consultas por variante;
  - informa, por variante, el promedio y el percentil 95 del tiempo de respuesta;
  - mide la variante del caché anterior antes de cambiar el código;
  - deja sus resultados y su procedimiento en un documento de la carpeta de esta spec, enlazado
    desde el README.

  No es un test automático: los tiempos dependen de la máquina y no se verifican en el build.
- **FR-011**: El ranking DEBE seguir siendo público, sin credencial, como hoy.
- **FR-030**: El caché de la cotización actual de cada jugador DEBE cumplir FR-001 a FR-005, FR-007
  y FR-008 con sus propios valores de tope y vencimiento, independientes de los del ranking: su
  consulta DEBE devolver exactamente lo mismo que hoy (incluidos el 404 de jugador inexistente y
  el de jugador sin cotización, que siguen sin guardarse), DEBE vaciarse en los mismos momentos y
  con la misma garantía estricta, y sus aciertos y fallos DEBEN publicarse por separado de los del
  ranking. No necesita panel propio en el dashboard.

**Métricas de negocio del mercado (US2)**

- **FR-012**: El sistema DEBE publicar tres métricas nuevas del mercado: **operaciones
  ejecutadas** (cada cruce entre una compra y una venta que queda guardado), **tokens
  transaccionados** (la suma de los tokens de esas operaciones) y **créditos movidos** (la suma
  de lo pagado en esas operaciones).
- **FR-013**: Las tres métricas DEBEN contar todas las operaciones ejecutadas, incluidas las que
  se cruzan contra la orden inicial del superusuario, y DEBEN distinguir con una etiqueta de
  contraparte de dónde vino la venta: de la orden inicial del superusuario o de un usuario. El
  total de cada métrica es la suma de sus dos series.
- **FR-014**: Cada operación DEBE contarse una sola vez y solo cuando quedó guardada y confirmada.
  Una compra o venta rechazada, una que falla o una cuyo cambio no se confirma NO DEBE sumar nada.
- **FR-015**: Una orden que queda pendiente sin cruzarse NO DEBE sumar operaciones, tokens ni
  créditos. Cuando más tarde se cruza, DEBE sumar la operación que la ejecuta, en el momento en
  que esa operación queda confirmada.
- **FR-016**: Una cancelación NO DEBE sumar nada a estas tres métricas.
- **FR-017**: Las tres métricas, con sus dos valores de contraparte (seis series), DEBEN existir
  en cero desde el arranque, antes de la primera operación.
- **FR-018**: Las tres métricas DEBEN publicarse por el mismo canal que las métricas de la 012,
  con su misma familia de nombres y su mismo control de acceso. No se crea ningún endpoint nuevo.
- **FR-019**: Los créditos movidos DEBEN conservar la precisión de los totales del mercado, sin
  redondear a enteros.
- **FR-020**: La única etiqueta de las tres métricas es la de contraparte, con valores cerrados.
  NO DEBEN llevar etiquetas por jugador, liga ni usuario.
- **FR-021**: Registrar estas métricas NUNCA DEBE cambiar el resultado de una compra, una venta
  ni una cancelación: ni la respuesta, ni los saldos, ni los tokens, ni la auditoría del mercado.
  Si registrar una métrica falla, la operación sigue su curso normal.
- **FR-022**: Las métricas de la 012 DEBEN seguir publicándose sin cambios de nombre, etiquetas
  ni significado.

**Dashboard de Grafana (US3)**

- **FR-023**: El repositorio DEBE incluir, versionadas, la conexión de Grafana a Prometheus y un
  dashboard, de modo que aparezcan solos al levantar Grafana con esa configuración.
- **FR-024**: El dashboard DEBE tener paneles de negocio: operaciones ejecutadas, tokens
  transaccionados y créditos movidos, cada uno con su total y su apertura por contraparte
  (orden inicial del superusuario o usuario), y órdenes (pedidas por operación y resultado, y
  abiertas).
- **FR-025**: El dashboard DEBE tener paneles del ranking: tiempo de respuesta y porcentaje de
  respuestas desde el caché.
- **FR-026**: El dashboard DEBE tener paneles generales: pedidos al backend y errores.
- **FR-027**: Los paneles basados en contadores DEBEN mostrar valores coherentes aunque el
  backend se reinicie y sus contadores vuelvan a cero.
- **FR-028**: El repositorio NO DEBE contener ninguna credencial. El usuario y la contraseña del
  administrador de Grafana DEBEN vivir en un archivo local ignorado por git, como hoy la clave de
  API de Prometheus; el repositorio versiona solo una configuración de ejemplo sin contraseña que
  nombra ese archivo. Si el archivo no existe, Grafana arranca igual con su comportamiento por
  defecto y el dashboard aparece igual.
- **FR-029**: El README DEBE explicar cómo levantar Prometheus y Grafana con esta configuración,
  sin Docker: versiones mínimas, cómo crear el archivo local con la credencial del administrador
  de Grafana a partir del ejemplo, comandos y dónde ver el dashboard.

### Key Entities

- **Entrada del caché del ranking**: una hoja del ranking ya calculada para una combinación de
  filtros, hoja y tamaño, con la estrategia activa con la que se calculó. Tiene un momento en que
  vence y puede descartarse antes por el tope o por una invalidación. Vive solo en memoria.
- **Entrada del caché de la cotización actual**: la cotización vigente de un jugador. Sigue las
  mismas reglas que la del ranking, con su propio tope y vencimiento.
- **Operación ejecutada**: el cruce guardado entre una compra y una venta de un jugador, con su
  cantidad de tokens y su total pagado. Ya existe desde la 008; esta feature solo la cuenta.
- **Métricas del mercado**: tres contadores en memoria (operaciones, tokens, créditos), cada uno
  abierto por contraparte (orden inicial del superusuario o usuario), que arrancan en cero y solo
  crecen; no se guardan en la base.
- **Dashboard**: la definición versionada de los paneles y de la conexión a Prometheus.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: En la medición documentada, las consultas repetidas del ranking no van a la base
  (el índice de aciertos que fija SC-002) y el tiempo medio del servidor con el caché nuevo es
  menor que sin caché: se informan el factor (sin caché / caché nuevo) y el ahorro absoluto
  medidos, sin un umbral fijo. Además, el caché nuevo no es más lento que el anterior más allá del
  ruido de la medición: se reportan los dos valores y la diferencia, con al menos dos corridas de
  cada uno.
- **SC-002**: Bajo una ráfaga de al menos 1.000 consultas repetidas sobre un conjunto de hojas
  que entra en el tope, al menos el 95% se responde desde el caché, y la métrica de aciertos y
  fallos lo muestra.
- **SC-003**: Para cada uno de los tres cambios (recálculo, recotización y cambio de estrategia
  activa), el 100% de las consultas que empiezan después de confirmado el cambio devuelven los
  datos nuevos, también en un test de concurrencia donde una consulta en curso durante el cambio
  guarda su resultado después del vaciado.
- **SC-004**: En cada uno de los dos cachés, después de consultar más combinaciones distintas
  (o más jugadores distintos) que su tope, la cantidad de entradas guardadas nunca supera ese
  tope.
- **SC-005**: Después de un escenario de prueba con compras, ventas, cruces parciales, órdenes
  pendientes que se cruzan después, cancelaciones y pedidos rechazados, las tres métricas del
  mercado coinciden exactamente con la cantidad de operaciones, la suma de tokens y la suma de
  totales guardados en el mercado.
- **SC-006**: Una persona que sigue el README desde un clon limpio ve el dashboard con datos en
  menos de 15 minutos, sin ningún paso de configuración en la interfaz de Grafana.
- **SC-007**: Una búsqueda de credenciales en los archivos versionados no encuentra ninguna.
- **SC-008**: Todos los tests existentes, incluidos los del ranking y los de las métricas de la
  012, pasan sin modificarse.

## Assumptions

- **Valores por defecto del caché del ranking**: tope de 500 entradas y vencimiento de seguridad de 10 minutos. El
  recálculo es semanal y se vacía el caché en cada cambio, así que el vencimiento solo actúa como
  red de seguridad; 500 entradas cubre con holgura las combinaciones de filtros, hojas y tamaños
  que se piden en la práctica, con una memoria acotada.
- **Medición "antes y después"**: el ranking ya tenía un caché, así que "antes" se mide en dos
  variantes: sin caché (cada consulta va a la base, que es lo que la optimización evita) y con el
  caché anterior. La medición se hace en local, con los datos del perfil local (detalle en
  FR-010).
- **Valores por defecto del caché de la cotización actual**: tope de 5.000 entradas y el mismo
  vencimiento de seguridad de 10 minutos. Hay una entrada por jugador, así que el tope cubre el
  catálogo completo de las ligas que se sincronizan, con margen.
- **Control de acceso**: las métricas nuevas heredan el de la 012 (el formato de Prometheus solo
  para administradores; las demás reglas de Actuator sin cambios).
- **Créditos**: "créditos movidos" es la suma de los totales de las operaciones (lo que paga el
  comprador y cobra el vendedor), en la misma unidad que los saldos del mercado.
- **Conexión de Grafana a Prometheus**: el Prometheus local del ejemplo no pide credencial, así
  que la conexión no necesita ninguna. La credencial del administrador de Grafana se resuelve
  como dice FR-028.
- **Instalación de Grafana**: se usa el binario oficial instalado a mano, sin Docker
  (constitución). El README fija una versión mínima que soporte la configuración versionada de
  conexiones y dashboards.
- **Dependencias**: si el tope y el vencimiento del caché necesitan una dependencia nueva, el
  plan la justifica antes de agregarla (constitución, Stack Tecnológico). Grafana y Prometheus
  son herramientas externas, no dependencias del build.
- **Sin cambios a la base**: no hay entidades, tablas ni índices nuevos; la consulta del ranking
  a la base no cambia.
- **Fuera de alcance**: Redis o cualquier caché distribuida, cambios a la consulta del ranking o
  a sus índices, un endpoint REST propio de métricas, alertas de Grafana o de Prometheus y
  métricas por jugador, liga o usuario.
