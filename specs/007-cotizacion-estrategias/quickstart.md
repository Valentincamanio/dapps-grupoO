# Guía de validación: Cotización de jugadores y estrategias de valuación

Pasos para comprobar de punta a punta que la feature funciona, en el backend (`./gradlew
bootRun`) y en el frontend (`npm run dev`). Los contratos están en [contracts/](./contracts/) y el
modelo en [data-model.md](./data-model.md); esta guía no los repite.

Los escenarios cubren: la **evolución semanal de un jugador de cada liga** (escenario 2 de la
consigna), el **cambio de estrategia con las dos opciones**, la **simulación sin efectos** y la
**pantalla de administración**.

## 0. Puertas antes de empezar

| Puerta | Cómo se verifica |
|---|---|
| La **006 está integrada en `develop`** y esta rama la incluye | `git log --oneline origin/develop` muestra el merge de `feature/scraping-whoscored`, y existen `modelo/performance/PlayerMatchStats.java` y `service/sync/SyncLock.java` |
| El **"sí" del equipo** para modificar los tests de la 004 figura en Clarifications de `spec.md` (nombre y fecha) | Se lee en la sección Clarifications. **Sin él, la tarea de ajuste de esos tests no se hace**; el resto de la validación sigue igual (D25) |
| Existen `PlayerMatchStatsRepository.findAllByMatchBetween` y `MatchRepository.findAllFinished` (puntos P-1 de `research.md`) | Compila `./gradlew compileJava` |

## 1. Preparación

Requisitos: Java 21, Node 20 o más. Sin Docker.

**1.1. Compilar y correr los tests del backend**

```bash
cd backend && ./gradlew build
```

Resultado esperado: `BUILD SUCCESSFUL`. No incluye la medición de volumen (se pide aparte, en el
escenario G).

**1.2. Levantar el backend con un administrador y con datos**

La cuenta de administrador sale de las variables de entorno (feature 001). Con la contraseña vacía,
no se crea el administrador y la aplicación avisa.

```bash
cd backend && FUTBOLMARKET_AUTH_ADMIN_USERNAME=admin FUTBOLMARKET_AUTH_ADMIN_EMAIL=admin@correo.com FUTBOLMARKET_AUTH_ADMIN_PASSWORD=admin12345 FOOTBALL_DATA_TOKEN=<tu token> ./gradlew bootRun
```

El perfil `local` usa H2 en archivo (`./data/futbolmarket`): los datos sobreviven al reinicio.

Resultado esperado en el registro: se creó el administrador, se sembraron las estrategias (`A` y
`B`) y las tablas `strategies`, `strategy_weights` y `quotes` existen. Si el catálogo está vacío, la
sincronización de arranque de la 005/006 los carga; esperar a que termine.

**1.3. Tener estadísticas**: el recálculo solo cotiza semanas con estadísticas (006). Disparar, con
el token del paso 1.4, la sincronización de estadísticas y esperar a que termine:

```bash
curl -s -X POST http://localhost:8080/matches/stats/sync -H "Authorization: Bearer $TOKEN"
```

**1.4. Obtener el token de administrador**

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/auth/login -H "Content-Type: application/json" -d '{"username":"admin","password":"admin12345"}' | sed 's/.*"token":"\([^"]*\)".*/\1/')
```

## 2. Escenario A: cotización semanal y evolución de un jugador de cada liga

Cubre HU1 y HU2 (escenario 2 de la consigna).

**2.1. Disparar el recálculo (primera ejecución)**

```bash
curl -s -X POST http://localhost:8080/quotes/recalculate -H "Authorization: Bearer $TOKEN"
```

Esperado (200, informe en JSON):

- `origin: MANUAL`, `strategy` con el nombre `Rendimiento de partido` y versión `1`.
- `quotedWeeks` con una entrada por semana cerrada, **en orden**; en la primera, `initial` igual a
  `calculated` (todos los jugadores activos); en las siguientes, `initial: 0`.
- `pendingWeeks` con las semanas que esperan estadísticas, con su motivo (o vacío).
- La semana en curso **no** figura.

**2.2. Repetir el recálculo: no crea nada (SC-005)**

```bash
curl -s -X POST http://localhost:8080/quotes/recalculate -H "Authorization: Bearer $TOKEN"
```

Esperado: `nothingToQuote: true` y `quotedWeeks: []`, con 200.

**2.3. Elegir un jugador de cada liga.** El ranking acepta `league`; tomar el primero de cada una:

```bash
for L in PREMIER BUNDESLIGA LA_LIGA SERIE_A LIGUE_1; do curl -s "http://localhost:8080/players/ranking?league=$L&size=1"; echo; done
```

Anotar el `playerId` de cada uno (los llamamos `P1` a `P5`).

**2.4. Ver la evolución semanal de cada uno** (repetir con cada id; sin credencial):

```bash
curl -s "http://localhost:8080/players/P1/quotes?size=100"
```

Esperado para **cada uno de los cinco**:

| Chequeo | Esperado |
|---|---|
| Primera cotización | `type: INITIAL`, `value: 100.00`, `tokenPrice: 1.0000`, sin `variation`, `validFrom` el lunes 00:00 (hora de Argentina) de su primera semana |
| Las siguientes | una `CALCULATED` por semana, `validFrom` en cada cierre (lunes 00:00 siguiente), orden ascendente |
| Variación | el valor absoluto de `variation.amount` es a lo sumo 10,00 (el factor de la estrategia A), salvo que el mínimo la suba |
| Piso | ninguna `value` por debajo de 10,00 y ninguna negativa (SC-003) |
| Estrategia | `strategy.name` y `strategy.version` presentes en cada punto |
| Sin datos internos | ninguna respuesta trae `score`, estadísticas ni métricas |
| Semanas sin partidos | la cotización existe y tiene la misma `value` que la anterior (SC-004) |

**2.5. Cotización actual y a una fecha**

```bash
curl -s "http://localhost:8080/players/P1/quotes/current"
curl -s "http://localhost:8080/players/P1/quotes/at?date=2026-09-01"
curl -s "http://localhost:8080/players/P1/quotes/at?date=2001-01-01"
curl -s "http://localhost:8080/players/P1/quotes/at?date=2099-01-01"
```

Esperado: la primera responde 200 con la última cotización; la segunda, la que ya regía al final de
ese día; la tercera responde **404** ("no tiene cotización al 2001-01-01"); la cuarta **400** ("La
fecha 2099-01-01 es futura").

**2.6. Distinguir "sin cotización" de "inexistente"**

```bash
curl -s "http://localhost:8080/players/999999/quotes/current"
```

Esperado: 404 "No se encontró el jugador con id 999999." Para un jugador del catálogo sin cotización
(por ejemplo uno recién agregado por una sincronización posterior al recálculo): 404 "El jugador N no
tiene cotización".

**2.7. Ranking y filtros**

```bash
curl -s "http://localhost:8080/players/ranking?position=FORWARD&page=0&size=5"
curl -s "http://localhost:8080/players/ranking?position=DELANTERO"
```

Esperado: la primera, jugadores activos con cotización ordenados por valor descendente y la
`activeStrategy`; la segunda, **400**. `GET /players/ranking` responde sin credencial y
`GET /players/{id}` sigue respondiendo igual (200 con un jugador existente).

## 3. Escenario B: cambio de la estrategia activa, con las dos opciones

Cubre HU4 y HU5. Antes de empezar, anotar el `id` de la estrategia B (`GET /strategies` con el token).

**3.1. Ver las estrategias sembradas**

```bash
curl -s http://localhost:8080/strategies -H "Authorization: Bearer $TOKEN"
```

Esperado: `Rendimiento de partido` (activa, versión 1) y `Equilibrada por posición` (versión 1), y
exactamente una `active: true`.

**3.2. `PROXIMA_SEMANA` (por defecto): solo cambia la activa**

Guardar la cotización actual del jugador `P1`, activar B sin opción y compararla.

```bash
curl -s -X POST http://localhost:8080/strategies/IDB/activate -H "Authorization: Bearer $TOKEN"
```

Esperado: 200 con `requotation: null`; `GET /strategies` muestra B activa; **ninguna cotización cambió** y
el historial de `P1` tiene los mismos puntos que antes. `GET /players/ranking` informa B como
`activeStrategy`. Volver a la A con `POST /strategies/IDA/activate` y con cuerpo
`{"requote":"PROXIMA_SEMANA"}`: mismo resultado.

**3.3. `AHORA`: recotiza la última semana cerrada y no acumula**

```bash
curl -s -X POST http://localhost:8080/strategies/IDB/activate -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d '{"requote":"AHORA"}'
```

Esperado: 200 con `requotation.origin: STRATEGY_CHANGE` y, para la última semana cerrada, `requoted`
igual a la cantidad de jugadores que tenían una cotización cuya estrategia no era B. En el historial
de `P1`: aparece una cotización `REQUOTED` de la última semana, con `strategy` B, `validFrom` el
momento del pedido, y **la `CALCULATED` anterior sigue ahí**. El `value` de la recotizada parte del
valor que `P1` tenía **antes de esa semana** (`baseValue`), no del de la calculada.

**3.4. Repetir no acumula**

Repetir el comando del paso 3.3. Esperado: 200 con `requotation: null`, porque la estrategia ya es la
activa y la última semana cerrada ya está cotizada con ella; el historial de `P1` **no** suma ninguna
cotización.

**3.5. Cambiar a otra estrategia con `AHORA` parte del mismo valor previo**

Crear una tercera estrategia (paso 3.6), activarla con `AHORA` y comprobar que la nueva `REQUOTED`
de `P1` tiene el mismo `baseValue` que la del paso 3.3, no el `value` de esa.

**3.6. Crear y editar**

```bash
curl -s -X POST http://localhost:8080/strategies -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d '{"name":"Ofensiva","defaultWeights":{"GOALS":0.30,"ASSISTS":0.20,"SHOTS_ON_TARGET":0.10,"KEY_PASSES":0.10,"SUCCESSFUL_DRIBBLES":0.10,"RATING":0.20}}'
```

Esperado: 201, versión 1, **inactiva**, `scaleFactor: 10` y `minimumValue: 10.00`; la activa no cambia y
ninguna cotización cambia. Editar una inactiva (`POST /strategies/ID/versions` con pesos válidos y
`"requote":"AHORA"`): 201, `requotation: null` (**la opción se ignora**), versión 2 inactiva, la activa
no cambia. Editar la **activa** con `PROXIMA_SEMANA`: la versión nueva pasa a ser la activa y las
cotizaciones ya guardadas conservan su versión anterior.

**3.7. Rechazos**

| Pedido | Esperado |
|---|---|
| Pesos que suman 0,95 | 400 "Los pesos por defecto deben sumar 1,00 y suman 0,95." |
| Un peso negativo, o con 3 decimales | 400 |
| Métrica `GOLES` | 400 "La métrica 'GOLES' no existe." |
| `scaleFactor: 0` | 400 |
| `minimumValue: 0.00` | 400 |
| Nombre `rendimiento de PARTIDO ` | 409 |
| `"requote":"AHORA2"` | 400 |
| Estrategia inexistente | 404 |

**3.8. `AHORA` con un recálculo o una sincronización en curso: 409 y la activa no cambia**

Con el backend corriendo, disparar `POST /quotes/recalculate` en una terminal y, mientras corre,
activar con `AHORA` desde otra (o con `POST /players/sync` en curso). Esperado: 409 ("Ya hay un recálculo
en curso." o "No se puede recalcular: hay una sincronización en curso.") y `GET /strategies` sigue
mostrando la misma activa. Hacer lo mismo con `PROXIMA_SEMANA` **sí** se aplica. (El test
`StrategyChangeIT` cubre esto de forma determinística; esta comprobación es manual.)

## 4. Escenario C: simulación sin efectos

Cubre HU7.

**4.1. Foto del estado**: guardar la salida de estos tres comandos.

```bash
curl -s http://localhost:8080/strategies -H "Authorization: Bearer $TOKEN" > antes-estrategias.json
curl -s "http://localhost:8080/players/P1/quotes?size=100" > antes-historial.json
curl -s "http://localhost:8080/players/ranking?size=50" > antes-ranking.json
```

**4.2. Simular una estrategia guardada**

```bash
curl -s -X POST "http://localhost:8080/quotes/simulate?size=5" -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d '{"strategyId":IDB}'
```

Esperado: 200, por jugador `currentValue`, `simulatedValue` y `difference`, `weeksSimulated` igual a la
cantidad de semanas cotizadas, y `strategy` B.

**4.3. Simular con parámetros** (factor 5, mínimo 10,00 y los pesos de A): 200 y `strategy: null`.
Con pesos que no suman 1,00: 400. Con `strategyId` y `parameters` a la vez, o con ninguno: 400. Con un
`strategyId` inexistente: 404.

**4.4. Determinismo y ausencia de efectos**

Repetir 4.2 y comparar las dos respuestas: **idénticas**. Volver a guardar la foto del paso 4.1 y
compararla:

```bash
curl -s http://localhost:8080/strategies -H "Authorization: Bearer $TOKEN" | diff - antes-estrategias.json
curl -s "http://localhost:8080/players/P1/quotes?size=100" | diff - antes-historial.json
curl -s "http://localhost:8080/players/ranking?size=50" | diff - antes-ranking.json
```

Esperado: ninguna diferencia (la simulación no guarda nada, no cambia la activa y no invalida el caché).

**4.5. Coincidencia con lo vigente (US7.7)**: con un historial cotizado íntegramente con la versión
vigente de la activa, simular esa estrategia da `difference: 0` para todos los jugadores. Si hubo
recotizaciones con otra estrategia, la diferencia es la esperada y no hace falta que sea 0.

**4.6. Calibración (riesgo R1)**: para decidir factor y mínimo con datos reales, simular con distintos
factores (por ejemplo 5, 10 y 20) y anotar cuántos jugadores activos quedan con `simulatedValue` igual al
mínimo (10,00). Es el indicador que `research.md` (D8) propone para confirmar la fórmula aditiva.

## 5. Escenario D: seguridad

```bash
curl -s -o /dev/null -w "%{http_code}\n" -X POST http://localhost:8080/quotes/recalculate
curl -s -o /dev/null -w "%{http_code}\n" -X POST http://localhost:8080/quotes/simulate
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/strategies
```

Esperado: `401` en las tres. Registrar un usuario común (`POST /auth/register`), iniciar sesión y repetir
con su token: `403` en las tres, y `POST /strategies/ID/activate` también `403`. En ningún caso cambia
nada. Las consultas (`/players/ranking` y las de cotizaciones) responden 200 sin credencial.

## 6. Escenario E: recálculo automático

**6.1. Disparo programado.** Arrancar el backend con un cron corto (la zona queda la de Argentina):

```bash
cd backend && FUTBOLMARKET_QUOTES_RECALCULATION_CRON="0 */2 * * * *" ./gradlew bootRun
```

Esperado: cada dos minutos el registro muestra el informe del recálculo (con "sin semanas para cotizar"
si no hay nada). Con una sincronización en curso, se omite y el `WARN` dice el motivo. Con
`FUTBOLMARKET_QUOTES_RECALCULATION_CRON=-` no se programa nada.

**6.2. Arranque.** Con semanas cerradas sin cotizar (por ejemplo, borrando las cotizaciones de la última
semana de la base de prueba), arrancar con `on-startup: true`: la aplicación termina de arrancar **sin
esperar** al recálculo y el informe aparece después en el registro. Sin semanas pendientes no se dispara
ninguno.

**6.3. Tolerancia a fuentes caídas.** Con el token de Football-Data vacío y sin internet, el recálculo
y las consultas funcionan igual con lo guardado (FR-051).

## 7. Escenario F: la aplicación web

```bash
cd frontend && npm run dev
```

Abrir `http://localhost:5173`. Probar a 360 px de ancho (herramientas de desarrollo del navegador) y solo
con teclado (Tab, Enter y Espacio): todo lo clickeable es un botón o un enlace con foco visible, y no hay
desplazamiento horizontal de la página.

**7.1. Sin sesión**

| Pantalla | Esperado |
|---|---|
| `/pizarra` | Cada post-it muestra la cotización actual y su variación con signo y texto (no solo color). Un jugador sin cotización lo indica sin inventar un valor. La pizarra pide **una** consulta de cotizaciones por hoja, no una por jugador (pestaña de red) |
| `/jugadores/:id` | Muestra la cotización (valor del jugador y precio por token) y la variación semanal, un gráfico con un punto por semana y un selector de rango de fechas. Elegir un rango recorta el gráfico; un rango sin cotizaciones muestra un mensaje. Cada punto, al enfocarlo o activarlo, dice su valor, su semana, y la estrategia y la versión. Hay una tabla con los mismos puntos (alternativa en texto). Un jugador sin cotización muestra "sin cotización" y los datos del catálogo siguen visibles |
| `/ranking` | Ordenado por cotización descendente, con filtros de liga, equipo y posición, paginación, y la estrategia y versión de cada fila. Los filtros y la hoja quedan en la dirección (`league`, `team`, `position`, `page`): recargar reproduce la vista |
| Estados | Con el backend apagado: carga y luego error con "reintentar" |
| `/admin/estrategias` | Lleva a iniciar sesión, sin mostrar ningún control de administración |

**7.2. Con un usuario común**: no hay ninguna entrada "administración" en el encabezado. Entrar por
`/admin/estrategias` muestra el aviso de falta de permiso y ningún control.

**7.3. Con el administrador (pantalla de administración)**

| Función | Esperado |
|---|---|
| Lista de estrategias | Cada una con sus versiones y cuál es la activa |
| Crear | El formulario muestra la suma de los pesos de cada juego mientras se completan y **no deja enviar** si algún juego definido no suma 1,00. Al guardar, aparece en la lista |
| Rechazo del servidor | Muestra el motivo en español y **conserva lo escrito** (por ejemplo, con un nombre repetido) |
| Editar | Crea la versión siguiente y la lista la muestra |
| Activar otra | Pide elegir "Recotizar ahora" o "Recotizar la semana siguiente" con la explicación de cada una; **la segunda viene preseleccionada**; nada cambia hasta confirmar |
| Recálculo (`/admin/recalculo`) | Dispara y muestra el informe: semanas cotizadas, jugadores con inicial, calculada y recotizada, y semanas pendientes con su motivo. Con otro en curso muestra el motivo del conflicto y no pierde la pantalla |
| Simulación (`/admin/simulacion`) | Simula una estrategia guardada o parámetros y muestra la tabla con cotización actual, simulada y diferencia, con filtros y paginación |
| Estados | Carga y error en cada pantalla |

**7.4. Sin acciones de mercado (SC-020)**: recorrer todas las pantallas buscando comprar, vender, "sumar al
plantel", órdenes, presupuesto, tokens o portfolio de un usuario. Esperado: ninguno. (Solo se muestra el
precio por token del jugador, que es un dato de la cotización.)

## 8. Escenario G: volumen y rendimiento en H2

Mide lo que `research.md` (D15) estima: unas 137.800 filas por año con 2.600 jugadores.

```bash
cd backend && QUOTES_BENCHMARK=true ./gradlew test --tests '*QuoteVolumeBenchmarkIT' -i
```

El test carga 2.600 jugadores y 40 semanas de estadísticas sintéticas en una H2 en archivo, ejecuta el
recálculo completo y escribe en el registro: el tiempo de una semana, el tiempo de la reconstrucción
de 40 semanas, la cantidad de filas y el tamaño del archivo. Cuando corre, verifica los topes del spec:

| Criterio | Tope |
|---|---|
| SC-009: una semana para todo el catálogo | menos de 1 minuto |
| SC-009: la temporada completa (40 semanas) | menos de 5 minutos |
| SC-010: la primera página de una simulación | menos de 30 segundos |
| SC-008: una página del ranking y la cotización de un jugador | menos de 2 segundos |

Si un tope no se cumple, se aplica el plan B de `research.md` (D15): `SEQUENCE` para el id de
`quotes`, con el aval del equipo. El benchmark **no** corre en `./gradlew build` normal.

## 9. Definición de terminado

**Backend** (constitución):

1. Tests unitarios y de integración, felices y de borde, y pasan (`./gradlew test`).
2. `./gradlew build` da `BUILD SUCCESSFUL`.
3. La aplicación compila y levanta con el perfil local (`./gradlew bootRun`).
4. Los endpoints nuevos están en Swagger y se ejecutan desde `/swagger-ui.html`, con los dos
   esquemas de Authorize para los de administrador.

**Frontend** (constitución):

1. `npm test` pasa, con casos felices y de borde, **incluida la suite de la 004 sin modificar** (o
   modificada solo como lo avale el "sí" del equipo, D25).
2. `npm run lint` y `npm run build` terminan sin errores.
3. `npm run dev` levanta y funciona contra el backend corriendo con `./gradlew bootRun`.

```bash
cd frontend && npm test && npm run lint && npm run build
```
