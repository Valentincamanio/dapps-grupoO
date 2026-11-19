# Feature Specification: Métricas y endpoints de monitoreo

**Feature Branch**: `012-metricas-monitoreo`

**Created**: 2026-11-19

**Status**: Draft

**Input**: User description: "Métricas y endpoints de monitoreo (Entrega 3, requerimiento de la cátedra): configurar métricas con Prometheus y endpoints de monitoreo con Spring Boot Actuator. Exponer las métricas en formato Prometheus con el tag común application=futbol-market y percentiles HTTP; sumar métricas propias del negocio (órdenes, recálculo de cotizaciones, sincronizaciones, logins, registros, usuarios y órdenes abiertas); exponer por web solo health, info, metrics, prometheus y loggers con control de acceso por rol; sumar health indicators propios de Football-Data y de las sincronizaciones sin llamadas HTTP externas. Sin entidades nuevas, sin tocar tests existentes, sin Docker ni Grafana."

## Contexto y diferencia con las auditorías

Esta feature agrega **observabilidad operativa**: números agregados y en memoria que describen
cómo se está usando y comportando el backend (cuántos requests, cuánto tardan, cuántas órdenes,
cuántos logins fallidos, cuándo fue la última sincronización), más un conjunto acotado de
endpoints técnicos para consultarlos.

Hoy el backend ya expone dos endpoints técnicos: `GET /actuator/health`, público y solo con el
estado, y `GET /actuator/info`, que pide la credencial de cualquier usuario autenticado y que
`AccessControlIT` usa como recurso protegido. **Ese comportamiento no cambia.**

NO es la auditoría del mercado de la feature 008 (`GET /audit`, registro de negocio persistido)
ni la auditoría de web services de la 011 (una línea de log por invocación). Las tres conviven
sin leerse entre sí:

- las métricas no se guardan en base, no reemplazan ni leen ningún registro de auditoría, y se
  pierden al reiniciar;
- los requests a `/actuator/**` siguen sin dejar línea de auditoría de web services;
- una compra deja su registro de auditoría del mercado, su línea de auditoría de web services y,
  además, incrementa la métrica de órdenes, sin que ninguna dependa de la otra.

## User Scenarios & Testing *(mandatory)*

Los actores son:

- **El operador** (los desarrolladores, la cátedra) que corre un Prometheus local y consulta el
  estado del sistema. Se autentica como `ADMIN`, con su token de sesión o con su clave de API.
- **Prometheus**, el recolector, que lee las métricas a intervalos fijos con la clave de API de
  un `ADMIN`.
- **Un `USER`** autenticado y **un visitante** sin credencial, que no deben poder leer métricas
  ni cambiar configuración, y que siguen usando la API de negocio sin notar ninguna diferencia.

### User Story 1 - Prometheus recolecta las métricas del backend (Priority: P1)

El operador levanta el backend con `./gradlew bootRun` y un Prometheus local corrido con su
binario, configurado con el archivo de ejemplo del repositorio y con la clave de API de un
`ADMIN` guardada fuera del repositorio. Prometheus muestra el target del backend en estado UP y
el operador grafica la tasa de requests por segundo, la latencia p95/p99 por ruta y el uso de
memoria y de CPU de la aplicación, todo identificado como `futbol-market`.

**Why this priority**: es el requerimiento central de la cátedra. Sin la exposición en formato
Prometheus no hay métricas que mirar, y el resto de la feature no se puede demostrar.

**Independent Test**: se pide `GET /actuator/prometheus` como `ADMIN` y se verifica que responde
200 en el formato de exposición de Prometheus, que todas las series llevan
`application="futbol-market"`, que las series de requests HTTP traen buckets de histograma y que
la ruta de un request a un jugador figura como plantilla (`/players/{id}`), no con su id.

**Acceptance Scenarios**:

1. **Given** el backend levantado, **When** un `ADMIN` pide `GET /actuator/prometheus` con su
   clave de API, **Then** recibe 200 con el texto en formato de exposición de Prometheus y cada
   serie lleva la etiqueta `application="futbol-market"`.
2. **Given** que se pidió `GET /players/7` y `GET /players/12`, **When** el `ADMIN` lee
   `/actuator/prometheus`, **Then** los dos requests suman en la misma serie con
   `uri="/players/{id}"` y no existe ninguna serie con `uri="/players/7"` ni `uri="/players/12"`.
3. **Given** requests ya atendidos, **When** el `ADMIN` lee `/actuator/prometheus`, **Then** las
   series de requests HTTP incluyen buckets de histograma que permiten calcular p95 y p99 por
   ruta, método y status.
4. **Given** un Prometheus local configurado con el `prometheus.yml` de ejemplo y la clave de
   API de un `ADMIN` en un archivo o variable fuera del repositorio, **When** Prometheus hace su
   primera recolección, **Then** el target del backend figura UP y la consulta
   `rate(http_server_requests_seconds_count[1m])` devuelve datos.
5. **Given** el repositorio, **When** se busca la clave de API en `prometheus.yml` o en cualquier
   archivo versionado, **Then** no aparece: el ejemplo solo referencia el archivo o la variable
   de donde se lee.

---

### User Story 2 - Métricas propias del negocio (Priority: P1)

El operador quiere ver, además de las métricas técnicas, cómo se usa el mercado: cuántas
órdenes de compra, venta y cancelación se aceptan o se rechazan, cuánto tarda y cómo termina
cada recálculo de cotizaciones y cada sincronización, cuántos logins fallan, cuántas cuentas se
registran, y cuántos usuarios y órdenes abiertas hay en este momento.

**Why this priority**: la cátedra pide métricas del sistema, y las técnicas solas no dicen nada
del dominio. Comparte la prioridad con la US1 porque se publican por el mismo canal.

**Independent Test**: se ejercita cada caso de uso (una compra aceptada y una rechazada, un
login correcto y uno fallido, un registro, un recálculo manual, una sincronización) y se verifica
que cada métrica correspondiente subió exactamente en uno, con las etiquetas esperadas, y que la
respuesta de cada caso de uso es idéntica a la de antes de la feature.

**Acceptance Scenarios**:

1. **Given** un `USER` con saldo suficiente, **When** compra tokens con éxito, **Then** el
   contador de órdenes con `operation="buy"` y `result="success"` sube en uno.
2. **Given** un `USER` sin saldo suficiente, **When** intenta comprar y el mercado lo rechaza,
   **Then** el contador con `operation="buy"` y `result="rejected"` sube en uno y la respuesta
   HTTP (status y cuerpo de error) es la misma que antes de la feature.
3. **Given** un `USER` con una orden abierta, **When** la cancela, **Then** el contador con
   `operation="cancel"` y `result="success"` sube en uno y el gauge de órdenes abiertas baja en
   uno.
4. **Given** credenciales válidas, **When** se hace login, **Then** el contador de logins con
   `result="success"` sube en uno; **Given** una contraseña incorrecta o un usuario inexistente,
   **When** se hace login, **Then** sube el de `result="failure"`.
5. **Given** datos de registro válidos, **When** se registra una cuenta, **Then** el contador de
   registros sube en uno y el gauge de usuarios registrados también.
6. **Given** un `ADMIN`, **When** dispara el recálculo manual de cotizaciones y termina bien,
   **Then** el timer de recálculo con `trigger="manual"` y `result="success"` registra una
   ejecución con su duración; **Given** un recálculo ya en curso, **When** se dispara otro,
   **Then** el segundo se registra con `result="blocked"`.
7. **Given** una sincronización de Football-Data en curso, **When** se pide otra de la misma
   fuente, **Then** el timer de sincronización con `source="football_data"` y
   `result="in_progress"` registra el intento rechazado.
8. **Given** el backend recién levantado, **When** el `ADMIN` lee las métricas propias, **Then**
   cada combinación de etiquetas de los contadores y timers ya figura, con valor cero, antes de
   que ocurra el primer caso de uso.
9. **Given** cualquier métrica propia, **When** se revisan sus etiquetas, **Then** ninguna lleva
   ids, usernames, emails, montos, cantidades ni ningún otro valor no acotado.

---

### User Story 3 - Endpoints de monitoreo con acceso controlado (Priority: P2)

El operador consulta desde el navegador o desde Swagger el estado de salud con detalle, la
información de la aplicación, el detalle de una métrica puntual y los niveles de log, y puede
subir o bajar el nivel de log de un paquete en caliente para diagnosticar un problema sin
reiniciar. Ningún otro endpoint técnico está disponible, y nada de esto lo puede ver ni tocar un
`USER` ni un visitante.

**Why this priority**: completa el requerimiento de la cátedra sobre Actuator y cierra la
superficie de exposición, pero la US1 y la US2 ya entregan valor sin ella.

**Independent Test**: para cada endpoint expuesto se hace el mismo request como visitante, como
`USER` y como `ADMIN`, y se verifica el status esperado y que la respuesta de error sea el JSON
estándar; para cada endpoint no expuesto se verifica el 404 y que el cuerpo no contenga
configuración.

**Acceptance Scenarios**:

1. **Given** un visitante sin credencial, **When** pide `GET /actuator/health`, **Then** recibe
   200 con solo el estado agregado, sin componentes ni detalles (comportamiento actual, sin
   cambios).
2. **Given** un visitante, **When** pide `GET /actuator/health/db`, **Then** recibe 401 con el
   JSON de error estándar; **Given** un `USER`, **When** pide lo mismo, **Then** recibe 200 con
   el estado del componente y sin detalles; **Given** un `ADMIN`, **Then** recibe 200 con el
   estado y sus detalles.
3. **Given** un visitante, **When** pide `GET /actuator/info`, **Then** recibe 401 (sin cambios);
   **Given** un `USER` o un `ADMIN`, **When** pide lo mismo, **Then** recibe 200 con nombre,
   descripción y versión de la aplicación, datos del build (artefacto, versión, momento del
   build) y versión de Java.
4. **Given** un visitante, **When** pide `GET /actuator/metrics`, `GET /actuator/prometheus` o
   `GET /actuator/loggers`, **Then** recibe 401 con el JSON de error estándar; **Given** un
   `USER`, **Then** recibe 403 con el JSON de error estándar; **Given** un `ADMIN`, **Then**
   recibe 200.
5. **Given** un `ADMIN`, **When** pide `GET /actuator/metrics/http.server.requests`, **Then**
   recibe las mediciones de esa métrica y las etiquetas disponibles para filtrarla.
6. **Given** un `ADMIN`, **When** cambia el nivel de log de un paquete del backend a `DEBUG`,
   **Then** el cambio rige desde el siguiente mensaje, sin reiniciar, y `GET /actuator/loggers`
   lo muestra como nivel configurado; **Given** un `USER`, **When** intenta el mismo cambio,
   **Then** recibe 403 y el nivel no cambia.
7. **Given** un `USER` o un `ADMIN`, **When** pide `/actuator/env`, `/actuator/beans`,
   `/actuator/configprops`, `/actuator/heapdump`, `/actuator/threaddump`, `/actuator/mappings` o
   `/actuator/shutdown`, **Then** recibe 404 y el cuerpo no contiene propiedades, variables de
   entorno, secretos ni ningún dato de configuración; **Given** un visitante, **Then** recibe 401
   con el JSON de error estándar.
8. **Given** Swagger UI, **When** el operador lo abre, **Then** los endpoints de monitoreo
   figuran en una sección o grupo propio, separados de la API de negocio, y se pueden ejecutar
   desde ahí con la credencial correspondiente.
9. **Given** cualquier request a `/actuator/**`, **When** termina, **Then** no deja línea en la
   auditoría de web services.

---

### User Story 4 - Salud de las integraciones y de las sincronizaciones (Priority: P3)

El operador quiere saber, sin salir de `/actuator/health`, si el backend tiene configurado el
token de Football-Data y cuándo fue la última sincronización completa exitosa de cada fuente, y
si alguna ya está vencida respecto del plazo de puesta al día.

**Why this priority**: es información útil para operar, pero no la pide la cátedra en forma
explícita y las US anteriores no dependen de ella.

**Independent Test**: se consulta `GET /actuator/health/footballData` y
`GET /actuator/health/sync` como `ADMIN` con y sin token configurado, sin sincronizaciones
previas, con una reciente y con una vencida, y se verifica el estado y los detalles en cada caso,
y que `GET /actuator/health` sigue respondiendo 200.

**Acceptance Scenarios**:

1. **Given** el backend sin token de Football-Data configurado (como en el perfil `test`),
   **When** un `ADMIN` pide `GET /actuator/health/footballData`, **Then** el estado es
   `UNKNOWN` y el detalle indica que no hay token configurado, sin mostrar ningún valor.
2. **Given** un token configurado, **When** se pide el mismo componente, **Then** el estado es
   `UP` y el detalle indica que hay token configurado, sin mostrar su valor ni una parte de él.
3. **Given** una fuente sin ninguna sincronización completa exitosa registrada, **When** un
   `ADMIN` pide `GET /actuator/health/sync`, **Then** el estado es `UNKNOWN` y el detalle de esa
   fuente indica que nunca se sincronizó.
4. **Given** las dos fuentes con su última sincronización completa exitosa dentro del plazo de
   puesta al día, **When** se pide el componente, **Then** el estado es `UP` y el detalle muestra,
   por fuente, el momento de la última sincronización exitosa y `stale=false`.
5. **Given** una fuente cuya última sincronización exitosa supera el plazo de puesta al día,
   **When** se pide el componente, **Then** el estado es `UNKNOWN` y esa fuente figura con
   `stale=true`.
6. **Given** cualquiera de los casos anteriores, **When** un visitante pide
   `GET /actuator/health`, **Then** recibe 200: ningún indicador propio devuelve `DOWN` por
   configuración faltante ni por datos viejos.
7. **Given** cualquier consulta de salud, **When** se evalúan los indicadores propios, **Then**
   ninguno hace llamadas a Football-Data ni a WhoScored.

---

### Edge Cases

- **Ruta inexistente**: un request a una ruta que no existe (por ejemplo `/players/7/foo/bar`)
  se cuenta con una etiqueta de ruta fija para "no encontrada", nunca con la ruta concreta. Mil
  rutas inventadas distintas no crean mil series.
- **Requests rechazados antes del controller**: un 401 o un 403 del filtro de seguridad y un 400
  por forma inválida del request se cuentan en las métricas HTTP con su status, pero no cuentan
  como orden, login ni registro: el caso de uso nunca se ejecutó.
- **Falla al registrar una métrica**: si registrar o actualizar una métrica falla, el caso de uso
  termina igual, con el mismo resultado, el mismo status y las mismas excepciones que tendría sin
  la feature.
- **Excepción de dominio**: una orden rechazada se cuenta como `rejected` y la excepción de
  dominio original llega intacta al manejador de errores; la métrica no la envuelve ni la
  reemplaza.
- **Error inesperado en el caso de uso** (no una regla de negocio): una orden que falla por un
  error interno no se cuenta como `rejected`, porque `rejected` significa que el mercado la
  rechazó por una regla del dominio.
- **Recálculo o sincronización que falla a mitad de camino**: se registra con `result="failure"`
  y con la duración hasta la falla.
- **Reinicio**: todas las métricas vuelven a cero; los gauges se recalculan con los datos
  actuales de la base, así que muestran los valores reales desde el primer request.
- **Visitante en un endpoint no expuesto**: un visitante que pide `/actuator/env` recibe 401,
  igual que en cualquier ruta no pública, antes de saber si el endpoint existe. Un usuario
  autenticado (`USER` o `ADMIN`) recibe 404. En ningún caso se revela configuración.
- **Raíz `/actuator`**: pide credencial y solo lista los endpoints expuestos.
- **Nivel de log inválido**: cambiar un logger a un nivel inexistente responde 400 con el JSON de
  error estándar y no cambia nada.
- **Cambio de nivel de log y reinicio**: el nivel cambiado en caliente se pierde al reiniciar; el
  backend vuelve a los niveles de su configuración.
- **Logger de la auditoría de web services**: un `ADMIN` puede cambiar su nivel como el de
  cualquier otro; si lo apaga, deja de escribirse la auditoría hasta que lo restaure o hasta el
  reinicio. Es una herramienta de diagnóstico del administrador.
- **Credencial de `ADMIN` revocada o rotada**: Prometheus empieza a recibir 401 y el target pasa
  a DOWN hasta que se actualice el archivo o la variable con la clave nueva.
- **Detalle de salud del componente `db` u otros de la plataforma**: para un `ADMIN` muestran sus
  detalles habituales, que nunca incluyen la URL completa de la base con credenciales ni otros
  secretos.
- **Especificación OpenAPI por defecto**: separar los endpoints de monitoreo en Swagger no rompe
  `GET /v3/api-docs`, que sigue respondiendo 200 y declarando los dos esquemas de seguridad.

## Requirements *(mandatory)*

### Functional Requirements

**Exposición para Prometheus (US1)**

- **FR-001**: El sistema MUST exponer `GET /actuator/prometheus` con todas sus métricas en el
  formato de exposición de texto de Prometheus.
- **FR-002**: Toda métrica publicada MUST llevar la etiqueta común `application` con el valor
  `futbol-market`.
- **FR-003**: Las métricas de requests HTTP MUST publicar un histograma de duración que permita
  calcular percentiles p95 y p99 en el recolector, por ruta, método y status.
- **FR-004**: La etiqueta de ruta de las métricas HTTP MUST ser la plantilla de la ruta
  (`/players/{id}`), nunca el valor concreto de un parámetro de ruta. Una ruta inexistente MUST
  contarse bajo una etiqueta fija.
- **FR-005**: El sistema MUST seguir publicando las métricas técnicas estándar de la plataforma
  (memoria, hilos, recolección de basura, CPU, tiempo activo, pool de conexiones a la base).
- **FR-006**: El repositorio MUST incluir un `prometheus.yml` de ejemplo para un Prometheus local
  corrido con su binario, que recolecte `/actuator/prometheus` del backend en `localhost:8080`
  autenticándose con el header `X-API-Key` de un `ADMIN`.
- **FR-007**: El `prometheus.yml` de ejemplo MUST leer la clave de API de un archivo o de una
  variable de entorno. Ninguna clave de API, token ni secreto MUST quedar escrito en un archivo
  versionado; el archivo con la clave MUST quedar excluido del control de versiones.
- **FR-008**: El repositorio MUST documentar cómo correr el Prometheus local, cómo obtener la
  clave de API de un `ADMIN` y dónde guardarla, y qué consultas usar para verificar la feature.

**Métricas propias del negocio (US2)**

- **FR-010**: El sistema MUST contar las órdenes del mercado con la etiqueta `operation`
  (`buy`, `sell`, `cancel`) y la etiqueta `result` (`success`, `rejected`). `rejected` MUST
  significar que el caso de uso rechazó la operación por una regla del dominio o porque lo
  pedido no existe.
- **FR-011**: El sistema MUST medir la duración de cada recálculo de cotizaciones con la etiqueta
  `trigger` (`manual`, `scheduled`, `startup`) y la etiqueta `result` (`success`, `failure`,
  `blocked`). `blocked` MUST significar que no se ejecutó porque ya había otro recálculo en
  curso.
- **FR-012**: El sistema MUST medir la duración de cada sincronización con la etiqueta `source`
  (`football_data`, `whoscored`) y la etiqueta `result` (`success`, `failure`, `in_progress`).
  `in_progress` MUST significar que no se ejecutó porque ya había otra sincronización en curso.
- **FR-013**: El sistema MUST contar los logins con la etiqueta `result` (`success`,
  `failure`). `failure` MUST incluir usuario inexistente y contraseña incorrecta, sin distinguir
  entre ambos en la etiqueta.
- **FR-014**: El sistema MUST contar los registros de cuentas exitosos.
- **FR-015**: El sistema MUST publicar un gauge con la cantidad actual de cuentas registradas y
  otro con la cantidad actual de órdenes abiertas del mercado (pendientes o parcialmente
  ejecutadas). Los dos MUST reflejar el estado de la base en el momento de la lectura.
- **FR-016**: Las métricas propias MUST tener nombres con el prefijo `futbolmarket` y una
  descripción en español visible en `/actuator/metrics/{nombre}`:
  - `futbolmarket.market.orders` (contador; `operation`, `result`);
  - `futbolmarket.quotes.recalculation` (timer; `trigger`, `result`);
  - `futbolmarket.sync` (timer; `source`, `result`);
  - `futbolmarket.auth.logins` (contador; `result`);
  - `futbolmarket.auth.registrations` (contador);
  - `futbolmarket.users.registered` (gauge);
  - `futbolmarket.market.orders.open` (gauge).
- **FR-017**: Cada combinación de etiquetas de los contadores y timers propios MUST existir con
  valor cero desde el arranque, para que el recolector calcule tasas sin huecos.
- **FR-018**: Registrar o actualizar una métrica MUST NOT cambiar el resultado, el status HTTP,
  el cuerpo de la respuesta ni las excepciones de ningún caso de uso. Si registrar una métrica
  falla, el caso de uso MUST terminar igual.
- **FR-019**: Ninguna etiqueta de ninguna métrica propia MUST llevar ids, usernames, emails,
  montos, cantidades, mensajes de error ni otros valores de cardinalidad no acotada. Los valores
  posibles de cada etiqueta son los listados en FR-010 a FR-013.
- **FR-020**: Las métricas MUST vivir solo en memoria: no se crean entidades ni tablas, y se
  pierden al reiniciar. Los gauges se calculan sobre los datos que ya existen.

**Endpoints de monitoreo (US3)**

- **FR-030**: El sistema MUST exponer por web solo `health`, `info`, `metrics`, `prometheus` y
  `loggers`. Cualquier otro endpoint técnico (`env`, `beans`, `configprops`, `heapdump`,
  `threaddump`, `shutdown`, `mappings`, entre otros) MUST responder 404 a un usuario autenticado
  y 401 a un visitante, y su respuesta MUST NOT contener configuración ni secretos.
- **FR-031**: `GET /actuator/health` MUST seguir siendo público y responder solo el estado
  agregado, sin componentes ni detalles, para cualquier caller.
- **FR-032**: `GET /actuator/health/{componente}` MUST pedir credencial: 401 para un visitante;
  200 con solo el estado para un `USER`; 200 con estado y detalles para un `ADMIN`.
- **FR-033**: `GET /actuator/info` MUST seguir pidiendo la credencial de cualquier usuario
  autenticado (401 sin credencial, 200 para `USER` y `ADMIN`) y MUST mostrar nombre, descripción
  y versión de la aplicación, datos del build (artefacto, grupo, versión, momento del build) y
  versión de Java. MUST NOT mostrar variables de entorno ni propiedades de configuración.
- **FR-034**: `/actuator/metrics`, `/actuator/metrics/{nombre}`, `/actuator/prometheus`,
  `/actuator/loggers` y `/actuator/loggers/{nombre}` MUST ser solo de `ADMIN`: 401 sin
  credencial y 403 para un `USER`, en los dos casos con el JSON de error estándar del backend.
- **FR-035**: Un `ADMIN` MUST poder consultar los niveles de log configurados y efectivos y
  cambiar en caliente el nivel de un logger; el cambio rige desde el siguiente mensaje y no
  sobrevive a un reinicio. Un nivel inválido MUST responder 400.
- **FR-036**: La raíz `/actuator` MUST pedir credencial y listar solo los endpoints expuestos.
- **FR-037**: Los endpoints de monitoreo MUST figurar en Swagger en un grupo o sección propia,
  separada de la API de negocio, y ejecutarse desde Swagger UI con los dos esquemas de
  autenticación existentes. `GET /v3/api-docs` MUST seguir respondiendo 200 y declarando
  `bearerAuth` y `apiKeyAuth`.
- **FR-038**: Los requests a `/actuator/**` MUST NOT dejar línea en la auditoría de web services
  ni registros en la auditoría del mercado.
- **FR-039**: Las dos credenciales existentes (token de sesión y clave de API) MUST funcionar en
  todos los endpoints de monitoreo protegidos, con las mismas reglas de rol.

**Indicadores de salud propios (US4)**

- **FR-040**: El sistema MUST agregar un indicador de salud `footballData` que responda `UNKNOWN`
  si no hay token de Football-Data configurado y `UP` si lo hay, con un detalle que diga si está
  configurado, sin mostrar nunca el token ni una parte de él.
- **FR-041**: El sistema MUST agregar un indicador de salud `sync` que muestre, por fuente
  (`football_data`, `whoscored`), el momento de la última sincronización completa exitosa y si
  está vencida (`stale`) respecto del plazo de puesta al día ya configurado. Responde `UP` si
  ninguna fuente está vencida ni sin sincronizar, y `UNKNOWN` si alguna nunca se sincronizó o
  tiene `stale=true`.
- **FR-042**: Ningún indicador propio MUST devolver `DOWN` ni `OUT_OF_SERVICE` por configuración
  faltante ni por datos viejos. Con los indicadores propios en `UNKNOWN`, el estado agregado de
  `/actuator/health` MUST seguir siendo `UP` y responder 200.
- **FR-043**: Ningún indicador propio MUST hacer llamadas a Football-Data ni a WhoScored; solo
  leen la configuración y los datos que ya están en la base.

**Restricciones transversales**

- **FR-050**: Ningún test existente se modifica ni se borra; todos MUST seguir pasando sin
  cambios.
- **FR-051**: Ningún endpoint, métrica, detalle de salud ni archivo versionado MUST exponer
  secretos: claves de API, tokens de sesión, el secreto de firma, el token de Football-Data,
  contraseñas ni la URL de la base con credenciales.
- **FR-052**: La feature MUST NOT introducir Docker, Testcontainers, Grafana, alertas, tracing
  distribuido ni probes de liveness y readiness.
- **FR-053**: Cada endpoint de monitoreo expuesto MUST tener tests de acceso como visitante,
  como `USER` y como `ADMIN`, y cada métrica propia, tests que verifiquen que sube al ejercitar
  su caso de uso, felices y de borde.

### Key Entities

No se crean entidades persistentes. Los conceptos que maneja la feature viven en memoria o se
leen de datos existentes:

- **Métrica**: una serie con nombre, tipo (contador, timer o gauge), descripción y un conjunto
  acotado de etiquetas. Se pierde al reiniciar.
- **Etiqueta**: un par nombre-valor de cardinalidad acotada (`operation`, `result`, `trigger`,
  `source`, `application`, y las de las métricas HTTP: ruta como plantilla, método, status,
  resultado).
- **Indicador de salud**: un componente con estado (`UP` o `UNKNOWN` en los propios) y detalles
  sin secretos.
- **Última sincronización completa**: el registro que ya existe por fuente; el indicador `sync`
  solo lo lee.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Con el `prometheus.yml` de ejemplo, un Prometheus local muestra el target del
  backend en estado UP dentro del primer intervalo de recolección, y la consulta
  `rate(http_server_requests_seconds_count[1m])` devuelve al menos una serie.
- **SC-002**: El 100% de las series publicadas lleva `application="futbol-market"`.
- **SC-003**: Después de pedir 50 jugadores distintos por id, la cantidad de series de requests
  HTTP para esa ruta es la misma que después de pedir uno solo.
- **SC-004**: Cada una de las siete métricas propias aparece desde el arranque y sube exactamente
  en uno (o registra exactamente una ejecución) por cada vez que se ejercita su caso de uso, con
  las etiquetas correctas.
- **SC-005**: Para cada caso de uso instrumentado, la respuesta (status y cuerpo) es idéntica con
  y sin la feature, incluidos los casos de error.
- **SC-006**: Cada endpoint de monitoreo expuesto tiene tests de acceso para visitante, `USER` y
  `ADMIN`, y el 100% pasa.
- **SC-007**: El 100% de los tests que existían antes de la feature pasa sin modificaciones.
- **SC-008**: `./gradlew build` termina con BUILD SUCCESSFUL y el análisis de calidad sigue con
  menos de 10 issues.
- **SC-009**: Ninguna respuesta de ningún endpoint de monitoreo, para ningún rol, contiene un
  secreto de la lista de FR-051; ningún archivo versionado contiene una clave de API.
- **SC-010**: En el perfil `test` (sin token de Football-Data y sin sincronizaciones),
  `GET /actuator/health` responde 200.

## Assumptions

- **Restricción de la cátedra**: las métricas se publican para Prometheus y los endpoints de
  monitoreo son los de Spring Boot Actuator, siguiendo las guías de Baeldung indicadas. Agregar
  el registro de Prometheus es una dependencia nueva que se declara y justifica en el plan, con
  el nombre de artefacto verificado para Spring Boot 4.
- **Prometheus local**: se corre con su binario oficial, en la máquina del operador, fuera del
  repositorio y sin Docker. El ejemplo apunta a `localhost:8080` y usa un intervalo de
  recolección de 15 segundos.
- **Clave de API del `ADMIN`**: se obtiene con los mecanismos que ya existen para el
  administrador; la feature no agrega endpoints para generarla.
- **Visitante en endpoints no expuestos**: el pedido dice "responde 404". Se interpreta como 404
  para cualquier usuario autenticado; un visitante recibe 401, como en cualquier ruta no
  pública, porque la autenticación se evalúa antes de saber si la ruta existe. Ninguno de los
  dos revela configuración.
- **Detalle de salud para `USER`**: se le muestra el estado del componente pero no los detalles;
  los detalles son solo de `ADMIN`.
- **Logins**: se cuentan los intentos que llegan al caso de uso de login; la autenticación de
  cada request con token o clave de API no es un login y no se cuenta.
- **Registros**: se cuentan solo los registros exitosos; los rechazados (usuario o email
  duplicado, contraseña inválida) no suben el contador.
- **Órdenes**: se cuentan las compras, ventas y cancelaciones pedidas por usuarios a través de
  sus casos de uso. Las órdenes iniciales que crea el sistema no se cuentan en
  `futbolmarket.market.orders`, pero sí suman al gauge de órdenes abiertas mientras estén
  abiertas.
- **Usuarios registrados**: el gauge cuenta todas las cuentas existentes, incluida la del
  administrador.
- **Sincronizaciones**: el timer cuenta las sincronizaciones completas de cada fuente, sin
  importar si las disparó el cron, el arranque o un `ADMIN`; la fuente y el resultado alcanzan
  para operar y no se agrega una etiqueta de disparador.
- **Plazo de puesta al día**: el indicador `sync` usa el mismo plazo que ya rige la puesta al día
  al arrancar (hoy, 7 días), con la misma regla de borde: con exactamente ese plazo, todavía está
  al día.
- **Histograma HTTP**: se publica solo para las métricas de requests HTTP; los timers propios
  publican cantidad, suma y máximo, que alcanzan para tasas y promedios.
- **Swagger**: la separación entre API de negocio y monitoreo se resuelve con grupos o secciones
  de la documentación existente; no se agrega otra herramienta de documentación.
