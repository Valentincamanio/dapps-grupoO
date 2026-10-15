# Investigación: Cotización de jugadores y estrategias de valuación

**Rama**: `feature/cotizacion-estrategias` | **Fecha**: 2026-10-15 | **Plan**: [plan.md](./plan.md)

Cada decisión tiene el formato **Decisión / Justificación / Alternativas descartadas**. No queda
ningún `NEEDS CLARIFICATION`: lo que el spec deja abierto está resuelto acá o figura en D28 como
punto a confirmar por el equipo.

Fuentes del relevamiento (2026-10-15): el spec 007, la constitución 2.2.1, el código de `develop`
(005 integrada), la rama `origin/feature/scraping-whoscored` (006, todavía sin integrar), los
tests de la 004 y los `.jar` de Spring 7.0.9 del caché de Gradle.

## Índice

| # | Tema |
|---|---|
| D1 | Dependencias |
| D2 | Contextos, capas y status codes |
| D3 | La semana y la hora de Argentina |
| D4 | Aritmética y escalas |
| D5 | Las 19 métricas y cuándo no se pueden calcular |
| D6 | Normalización contra los pares |
| D7 | Score, valor y mínimo |
| D8 | La fórmula aditiva: decisión y riesgo conocido |
| D9 | El valor base cuando hay recotizaciones |
| D10 | Tipos de cotización y vigencias |
| D11 | Qué semanas se cotizan |
| D12 | Recotizar en el acto |
| D13 | Estrategias, versiones y semilla |
| D14 | Cambiar la activa: opción, transacción y candados |
| D15 | Persistencia y volumen |
| D16 | Estadísticas de entrada (006) |
| D17 | Recálculo: orquestación y disparadores |
| D18 | Coordinación con la 006 |
| D19 | Simulación |
| D20 | Consultas y API |
| D21 | Caché |
| D22 | Seguridad |
| D23 | Cómo llega la cotización a la pizarra |
| D24 | Frontend |
| D25 | Tests de la 004 que cambian |
| D26 | Estrategia de tests |
| D27 | Riesgos conocidos |
| D28 | Puntos a confirmar con el equipo |

---

## D1. Dependencias

**Decisión**: no se agrega ninguna dependencia, ni en el backend ni en el frontend.

- **Caché**: `ConcurrentMapCacheManager`, `@EnableCaching`, `@Cacheable` y `@CacheEvict` están
  en `spring-context-7.0.9.jar`, que ya es transitiva de `spring-boot-starter-webmvc`. Se
  verificó listando el `.jar` del caché de Gradle:
  `org/springframework/cache/concurrent/ConcurrentMapCacheManager.class`,
  `.../cache/annotation/EnableCaching.class`, `Cacheable.class` y `CacheEvict.class`. Las
  anotaciones se aplican con los proxies que ya usa `@Transactional`.
- **Gráfico**: SVG escrito a mano, sin librería.
- **Frontend**: ya tiene todo lo necesario (React 18, React Router 7, Vitest 4, MSW 2, RTL,
  `eslint-plugin-jsx-a11y`).

**Justificación**: la constitución prohíbe agregar dependencias sin avisar y justificar; acá no
hace falta ninguna.

**Alternativas descartadas**:

- **Caffeine** (expiración por tiempo): sería una dependencia nueva. Se acepta no tener TTL
  (D21).
- **Recharts, Chart.js o visx**: dependencias nuevas para un gráfico de una sola serie.

**Verificación pendiente en la implementación**: un test de contexto que levante la aplicación
con `@EnableCaching` y compruebe que el bean `CacheManager` es el nuestro (`CacheConfigTest`).

## D2. Contextos, capas y status codes

**Decisión**: contextos nuevos `strategy` y `quote`, en las capas donde tienen clases. Se reutilizan
`player`, `match`, `league`, `position` y `team` sin tocar su modelo.

| Capa | `strategy` | `quote` |
|---|---|---|
| `modelo/` | `ValuationMetric`, `MetricDirection`, `WeightSet`, `StrategyVersion`, `Strategy`, `StrategyCatalog`, `StartingStrategies`, `RequoteTiming`, `exception/` | `QuoteWeek`, `Quote`, `QuoteType`, `WeeklyPerformance`, `MetricNormalization`, `WeeklyQuotation`, `QuotingPlan`, `RecalculationReport`, `Simulation`, `QuotePage`, `RankedQuote`, `QuoteDay`, `exception/` |
| `service/` | `StrategyService`, `StrategyChangeService`, `StrategyChangeWriter`, `StrategySeeder` | `QuoteQueryService`, `QuoteRecalculationService`, `QuoteWeekWriter`, `QuoteSimulationService`, `RecalculationLock`, `QuoteCacheInvalidator`, `QuoteScheduler`, `QuoteStartupRecalculation`, `RecalculationReportLogger` |
| `controller/` | `StrategyController` y `dto/` | `QuoteController`, `QuoteAdminController` y `dto/` |
| `persistence/` | `StrategyRepository`, `StrategyMapper`, `StrategySQL`, `StrategyWeightSQL`, `StrategySQLDAO` | `QuoteRepository`, `QuoteMapper`, `QuoteSQL`, `QuoteSQLDAO` |

- `config/` suma `QuoteRecalculationProperties` y `CacheConfig`; `security/SecurityConfig` suma dos
  reglas. No hay paquetes de primer nivel nuevos ni carpetas fuera del árbol de la constitución.
- **Status codes**: solo 400, 404 y 409. No se suma ninguno (no hace falta el aval que pide el
  Principio III). El 401 y el 403 los resuelve `security/` antes del controller.
- **Idioma**: contextos en inglés y en singular; endpoints `/players`, `/quotes` y `/strategies`;
  mensajes en español.

**Justificación**: es el criterio de la constitución (un contexto es una carpeta dentro de una
capa). `strategy` y `quote` son dos conceptos con ciclos de vida distintos: una estrategia es
configuración y versiones; una cotización es un hecho inmutable. `quote` depende de `strategy`
(cada cotización apunta a su versión) y `strategy` no depende de `quote`, así que no hay ciclo.

**Alternativas descartadas**:

- **Un solo contexto `valuation`**: mezclaría la configuración con el historial y el nombre no es
  un sustantivo del dominio que ya use el spec.
- **Un contexto `week`**: la semana es un detalle de la cotización (`QuoteWeek`), no tiene
  persistencia ni servicio propios.
- **Un contexto `recalculation`**: el recálculo es un caso de uso de `quote`, igual que la
  simulación.

## D3. La semana y la hora de Argentina

**Decisión**: `QuoteWeek` es un value object del modelo, identificado por el lunes (`LocalDate`) en
`America/Argentina/Buenos_Aires`.

- Va del lunes 00:00 al lunes 00:00 siguiente en esa zona (FR-007). En UTC es de las 03:00Z a las
  03:00Z, porque Argentina es UTC−3 sin horario de verano (Assumptions).
- Métodos: `containing(Instant)`, `start()`, `end()`, `next()`, `previous()`, `isClosedAt(Instant)`,
  `lastClosedAt(Instant)`.
- La zona es una constante del modelo (`ZoneId`), no una propiedad: la semana es una definición
  del dominio, y el cron y su zona sí son configurables (D17).
- En la base la semana es `week_start DATE` (el lunes, en hora de Argentina) y las vigencias son
  `Instant`.
- Un partido pertenece a la semana en que cae su `utcDate` convertido a hora de Argentina. Un
  partido del domingo 23:59 es de esa semana y uno del lunes 00:00 es de la siguiente.

**Justificación**: usar `ZoneId` y no un offset fijo deja la regla correcta si Argentina cambiara su
huso, y los tests de borde (domingo 23:59 contra lunes 00:00) son baratos en el modelo.

**Alternativas descartadas**:

- **Semana ISO de UTC**: parte los partidos de la noche del domingo en la semana equivocada.
- **Guardar el número de semana ISO**: no identifica la semana sin el año y complica el orden.

## D4. Aritmética y escalas

**Decisión**: todo el cálculo usa `BigDecimal`; nunca `double`.

| Magnitud | Escala | Redondeo | Dónde |
|---|---|---|---|
| Valor del 100 % del jugador | 2 | `HALF_UP`, una sola vez | `StrategyVersion.valueFor` |
| Posición relativa | 6 | `HALF_UP` | `MetricNormalization` |
| Métrica normalizada (−1 a +1) | 6 | exacta a partir de la posición relativa ya redondeada | `MetricNormalization` |
| Score | 6 | `HALF_UP`, una sola vez | `StrategyVersion.scoreFor` |
| Precio por token | 4 | exacto (`valor / 100` con 2 decimales de valor entra en 4) | `Quote.tokenPrice()` |
| Pesos | a lo sumo 2 | no se redondea: se rechaza lo que traiga más | `WeightSet` |

- El score es la suma de `peso × métrica` sin redondear en el medio (escala 8 por el producto) y se
  redondea una vez al final a escala 6.
- El valor es `valorBase + score × factorEscala`, redondeado una vez a 2 decimales y llevado al
  mínimo si queda por debajo.
- La métrica es `2 × posiciónRelativa − 1` calculada con la posición relativa **ya redondeada a
  escala 6**. Así las dos magnitudes son consistentes entre sí y la métrica es siempre un múltiplo
  exacto de `0,000002`. El error contra calcularla sin redondear es a lo sumo `0,000001` y no
  mueve ningún ejemplo del spec (los cinco delanteros dan −0,6; −0,6; 0,0; +0,4; +0,8 exactos).
- Mismos datos de entrada, mismo resultado (FR-026): no hay ningún `HashMap` iterado sin orden
  que alimente una suma, y el orden en que se procesan los jugadores no cambia el resultado
  porque cada score depende solo de las métricas de su grupo de pares.

**Justificación**: es lo que pidió el equipo y lo que fija el spec (Assumptions: el redondeo a dos
decimales es hacia arriba desde la mitad y se hace una sola vez).

**Alternativas descartadas**:

- **Redondear cada término del score**: acumula error y rompe el ejemplo del 0,30 en otros pesos.
- **Calcular la métrica directo como `(2·menos + empates − n) / n`**: es más exacta, pero el
  equipo definió la métrica a partir de la posición relativa y los dos números tienen que poder
  verificarse a mano contra la definición.

## D5. Las 19 métricas y cuándo no se pueden calcular

**Decisión**: `ValuationMetric` es un enum con las 19 métricas del FR-016 y su sentido
(`MetricDirection.HIGHER_IS_BETTER` o `LOWER_IS_BETTER`). No reutiliza `modelo/performance/Metric`
de la 006: aquel enum son las 15 columnas guardadas por partido; este son las 19 magnitudes
**semanales** que se comparan.

| `ValuationMetric` | Sentido | Se arma con (006 y 005) |
|---|---|---|
| `GOALS` | más es mejor | suma semanal de `GOALS` |
| `ASSISTS` | más es mejor | suma de `ASSISTS` |
| `SHOTS` | más es mejor | suma de `SHOTS` |
| `SHOTS_ON_TARGET` | más es mejor | suma de `SHOTS_ON_TARGET` |
| `KEY_PASSES` | más es mejor | suma de `KEY_PASSES` |
| `SUCCESSFUL_DRIBBLES` | más es mejor | suma de `SUCCESSFUL_DRIBBLES` |
| `TACKLES` | más es mejor | suma de `TACKLES` |
| `INTERCEPTIONS` | más es mejor | suma de `INTERCEPTIONS` |
| `CLEARANCES` | más es mejor | suma de `CLEARANCES` |
| `AERIALS_WON` | más es mejor | suma de `AERIALS_WON` |
| `PASSES` | más es mejor | suma de `PASSES` |
| `MINUTES_PLAYED` | más es mejor | suma de `minutesPlayed` |
| `RATING` | más es mejor | promedio de `rating` ponderado por `minutesPlayed` |
| `PASS_ACCURACY` | más es mejor | `Σ ACCURATE_PASSES / Σ PASSES` |
| `TEAM_RESULT` | más es mejor | promedio de victoria 1, empate 0,5, derrota 0 |
| `CLEAN_SHEETS` | más es mejor | cantidad de partidos con la valla invicta |
| `FOULS_COMMITTED` | **menos es mejor** | suma de `FOULS_COMMITTED` |
| `YELLOW_CARDS` | **menos es mejor** | suma de `YELLOW_CARDS` |
| `RED_CARDS` | **menos es mejor** | suma de `RED_CARDS` |

(12 de suma + 4 especiales + 3 invertidas = 19.) En la API los pesos se identifican con estos
nombres en inglés (`GOALS`, `TACKLES`, ...). Las etiquetas en español ("goles", "quites") se
traducen en el frontend (`utils/metrics.ts`). "Quites" es `TACKLES`, "regates" es
`SUCCESSFUL_DRIBBLES`, "tiros" es `SHOTS` y "valla invicta" es `CLEAN_SHEETS`.

**Cuándo una métrica "no se puede calcular"** (FR-021; el valor es 0 para el jugador y no entra en
la comparación de los demás):

| Métrica | No se puede calcular si |
|---|---|
| `RATING` | no jugó o ninguno de sus partidos de la semana trae rating |
| `PASS_ACCURACY` | la suma de pases de la semana es 0 |
| `TEAM_RESULT` y `CLEAN_SHEETS` | no jugó, o en ningún partido que jugó se puede resolver de qué lado estaba |
| las 15 de suma e invertidas | nunca: sin estadísticas valen 0 y **sí** entran en la comparación |

**Cómo se resuelve el lado** (consigna del equipo): se compara el equipo actual del jugador con el
local y el visitante del partido. Si no es ninguno de los dos, ese partido no aporta a
`TEAM_RESULT` ni a `CLEAN_SHEETS`. Un partido sin resultado (`winner` o `fullTime` nulos) tampoco.
Si de los partidos de la semana ninguno es resoluble, la métrica no se puede calcular.

**"Jugó"** significa tener una estadística con `minutesPlayed > 0` (la 006 solo guarda a quien
entró a la cancha, FR-004 de la 006, pero un suplente que entró en el descuento puede tener 0
minutos). `TEAM_RESULT` promedia solo los partidos que jugó.

**Valla invicta**: el partido terminó y el rival marcó 0 goles en `fullTime`.

**Interpretaciones que el equipo puede revisar** (también en D28):

- Con varios partidos en la semana y solo algunos resolubles, `TEAM_RESULT` promedia los
  resolubles y `CLEAN_SHEETS` cuenta los resolubles. La consigna solo dice "si su equipo no es
  ninguno de los dos, esas métricas valen 0"; con un partido por semana, el caso típico, las dos
  lecturas coinciden.
- Un jugador sin estadística porque la 006 no pudo cruzarlo cuenta como "no jugó" (FR-022): sus 15
  métricas de suma valen 0 y entra en la comparación; las 4 que requieren haber jugado no se
  pueden calcular.

**Justificación**: el modelo (`WeeklyPerformance`) decide todo esto, no el servicio (Principio II).
Tener un enum propio evita mezclar "lo que se guarda por partido" con "lo que se compara por
semana".

**Alternativas descartadas**:

- **Ampliar `Metric` de la 006 a 19**: obliga a tocar la 006 y a sus tests, y mezclaría columnas de
  la tabla con magnitudes derivadas.
- **Contar `TEAM_RESULT` desconocido como 0 dentro de la comparación**: el spec dice que no entra.

## D6. Normalización contra los pares

**Decisión**: `MetricNormalization` normaliza una métrica de una posición en una semana.

- **Pares**: los jugadores **activos** de la misma `Position`, de las cinco ligas juntas (Assumptions
  del spec), hayan jugado o no.
- **Grupo de comparación de una métrica**: los pares para los que se pudo calcular (FR-021). Para
  las 15 métricas de suma todos los pares la tienen (con 0 si no jugaron).
- **Posición relativa** = `(cantidad con menos + la mitad de los que empatan, él incluido) /
  cantidad`, con "menos" entendido según el sentido de la métrica (en las de menos es mejor,
  "menos" significa "peor", es decir, los que tuvieron más). **Métrica** = `2 × posición relativa − 1`.
- **Todos empatan** (o hay un solo par): la posición relativa es 0,5 y la métrica 0. La fórmula ya
  lo da sola; se deja un test explícito.
- **Algoritmo**: se ordenan los valores del grupo una vez (`O(n log n)`) y, por cada valor distinto,
  se cuentan los menores y los iguales. No se compara cada jugador contra todos (`O(n²)`).
  Con unos 650 jugadores por posición y 19 métricas, una semana son unas 50.000 operaciones de
  ordenamiento: despreciable frente a los inserts.
- **Solo se normalizan las métricas con peso > 0** en alguna posición de la estrategia en uso. Es
  una optimización pura: el resultado de las que tienen peso 0 no entra en el score.

Ejemplo del spec (delanteros con 0; 0; 1; 2; 3 goles): `(0+1)/5 = 0,2 → −0,6`; `(2+0,5)/5 = 0,5 →
0,0`; `(3+0,5)/5 = 0,7 → +0,4`; `(4+0,5)/5 = 0,9 → +0,8`; promedio 0 (SC-002).

**Justificación**: es la definición del FR-018 al FR-021. Que la normalización no dependa de la
estrategia (solo de los datos) permite a la simulación calcularla una vez por semana y probar
varios juegos de pesos con ella.

**Alternativas descartadas**:

- **z-score o min-max**: el equipo eligió la posición relativa porque es robusta a valores
  extremos (un jugador con 5 goles no aplasta al resto).
- **Pares por liga**: es una de las suposiciones a confirmar (D28); el modelo recibe el grupo
  como parámetro, así que acotarlo después no cambia la normalización.

## D7. Score, valor y mínimo

**Decisión**:

- `StrategyVersion.scoreFor(Position, Map<ValuationMetric, BigDecimal>)` suma `peso × métrica` con
  los pesos de la posición (o los por defecto). Como los pesos son ≥ 0 y suman 1,00, el score
  está entre −1 y +1.
- `StrategyVersion.valueFor(BigDecimal base, BigDecimal score)` devuelve `max(mínimo, base + score ×
  factor)` redondeado una vez a 2 decimales `HALF_UP`.
- Por defecto, el factor de escala es 10 y el mínimo es 10,00 (FR-025). Son constantes con nombre
  del modelo. El mínimo se aplica también cuando la base está por debajo de un mínimo que se subió
  (edge case del spec).
- `Quote.tokenPrice()` es `value / 100` con escala 4.

Ejemplos del spec que van como tests de dominio (SC-002):

| Caso | Entrada | Resultado |
|---|---|---|
| Score 0,30 | estrategia A; metrics goles +0,8, asistencias +0,4, tiros 0, pases clave 0, regates −0,4, quites −0,4, rating +0,6 | `0,25·0,8 + 0,15·0,4 + 0,10·(−0,4) + 0,10·(−0,4) + 0,20·0,6 = 0,30` |
| 100,00 → 103,00 | base 100,00, score 0,30, factor 10 | 103,00 (token 1,0300) |
| 103,00 → 101,00 | base 103,00, score −0,20 | 101,00 |
| Mínimo | base 12,00, score −0,50, factor 10, mínimo 10,00 | 10,00 (no 7,00) |
| Recotizada | base 103,00, score +0,10 (estrategia B) | 104,00 |
| Sin variación | score 0 | el mismo valor |

**Justificación**: Principio II: `StrategyVersion` conoce sus pesos, su factor y su mínimo, así que
es quien sabe puntuar y valuar. El servicio no tiene un solo `if` de negocio.

**Alternativas descartadas**:

- **Una clase `ValueCalculator` con métodos estáticos**: deja a `StrategyVersion` anémica.

## D8. La fórmula aditiva: decisión y riesgo conocido

**Decisión**: se implementa la fórmula que el equipo definió y el spec fija (FR-024):
`valor = valorBase + score × factorEscala` (aditiva), con factor 10 y mínimo 10,00 por defecto.
**Se deja registrado como riesgo conocido** que, con esta fórmula, una parte de los jugadores
puede terminar pegada al piso con el correr de las semanas. La simulación (D19) es la herramienta
para calibrar el factor y el mínimo con datos reales antes de decidir si el comportamiento es
aceptable. **El equipo tiene que confirmar la fórmula** (D28, punto 1).

**Evidencia**: una simulación sintética que corrió el equipo, con **600 jugadores y 38 semanas,
datos inventados, no reales**. Los números están por token (el factor 0,10 por token son los 10
créditos del 100 % del jugador; el mínimo 0,10 por token son los 10,00 créditos):

| Variante | En el piso a las 8 semanas | A las 20 semanas | A las 38 semanas |
|---|---|---|---|
| Aditiva, factor 0,10, mínimo 0,10 (**la del spec**) | 0 % | 8 % | 22 % |
| Aditiva, factor 0,20 | 3 % | 23 % | 30 % |
| Proporcional: `valor anterior × (1 + score × k)`, k = 0,10 | no informado | 0 % | 3 % |

**Lectura** (hipótesis, no medición nuestra):

- Las métricas se normalizan contra los pares, así que su promedio semanal es 0: en conjunto, los
  valores se conservan salvo cuando el piso los frena. Un jugador que rinde por debajo del promedio
  de forma persistente pierde hasta 10 créditos por semana y el piso está a 90 créditos del
  arranque: se llega en 9 semanas muy malas seguidas o en unas decenas de semanas con score medio
  negativo.
- Subir el factor no ayuda (0,20 empeora los tres cortes). La variante proporcional casi no toca
  el piso porque una baja es un porcentaje del valor que queda, y no un monto fijo.
- Como los datos son inventados, la tabla sirve para ver la **forma** del riesgo (el piso se llena
  a lo largo de la temporada), no su magnitud real.

**Qué hace este plan con eso**:

1. Implementa la aditiva, como pide el spec. La fórmula vive en un único método
   (`StrategyVersion.valueFor`), así que cambiarla después toca un lugar.
2. No agrega un parámetro "tipo de fórmula" a la estrategia: sería una decisión de diseño que el
   equipo no tomó y complicaría las versiones, los pesos y el spec.
3. La simulación (`POST /quotes/simulate`) permite probar factor y mínimo con datos reales sobre
   toda la temporada. Una medida concreta de calibración es "porcentaje de jugadores activos en
   el piso al final de la temporada simulada", que se puede leer de la respuesta paginada o de
   `quickstart.md` (escenario de calibración).
4. **Lo que la simulación no puede hacer**: evaluar la variante proporcional. Hoy el endpoint solo
   varía factor, mínimo y pesos de la fórmula aditiva. Si el equipo prefiere la proporcional, es
   un cambio de spec (FR-024, FR-025, SC-003) y de `valueFor`, no un parámetro.

**Alternativas descartadas** (por ahora, a decisión del equipo):

- **Proporcional con k = 0,10**: es la que mejor se comporta en la simulación sintética, pero
  cambia el significado de "factor de escala" (deja de ser un monto máximo por semana, SC-003) y
  hace que el valor sea una función multiplicativa del historial: un jugador en 10 sube 1 y uno en
  100 sube 10 con el mismo score.
- **Subir el factor a 0,20**: empeora el piso en la simulación.
- **Bajar el mínimo**: retrasa el piso pero no lo evita; se puede probar con la simulación.

## D9. El valor base cuando hay recotizaciones

**Decisión**: el **valor base de la semana W** de un jugador es el valor de **su última cotización
(por vigencia) de las semanas anteriores a W**, o 100,00 si no tiene ninguna.

Con esa regla:

- Una semana normal parte de la cotización anterior del jugador, que casi siempre es la calculada
  de la semana anterior (FR-024: "el valor anterior del jugador").
- La recotización de la última semana cerrada `L` parte del precio previo a `L` (FR-036, "su
  última cotización vigente de la semana anterior, o 100,00 si es la primera") y no acumula: las
  cotizaciones de la propia `L` no cuentan (FR-037).
- **Una recotizada de `L` hecha durante `L+1` se arrastra al cierre de `L+1`**: la semana `L+1` parte
  del valor recotizado de `L`. Sin esto, el cierre siguiente descartaría la corrección y la
  recotización solo duraría hasta el próximo lunes.

**Esto matiza la consigna del equipo**, que dice "el valor vigente al empezar esa semana". Leída
al pie de la letra, una recotizada de `L` (que rige desde el momento en que se hace, dentro de
`L+1`) **no** estaba vigente al empezar `L+1`, y el cierre de `L+1` la ignoraría. Se eligió la
lectura que conserva el efecto de la recotización, porque es la que corresponde al spec: FR-024
("después es el valor anterior del jugador: el precio tiene memoria") y FR-036 ("última cotización
vigente de la semana anterior"). **Se deja para que el equipo confirme** (D28, punto 2). Si el
equipo prefiere la lectura literal, cambia una línea del modelo (el filtro de la consulta del
último valor) y el test de "recotizada que se arrastra".

El mismo criterio cubre los demás casos: el jugador inactivo que vuelve parte de su última
cotización, y uno nuevo parte de 100,00.

**Alternativas descartadas**:

- **"Vigente al empezar la semana" literal**: descarta la recotización en el siguiente cierre, y
  eso deja a la opción "recotizar ahora" sin efecto duradero.
- **Guardar la base en la cotización anterior**: la base de cada cotización calculada se guarda
  igual (D15, `base_value`), pero como dato de auditoría, no para elegirla.

## D10. Tipos de cotización y vigencias

**Decisión**: `QuoteType` es `INITIAL`, `CALCULATED` o `REQUOTED`.

| Tipo | Cuándo | Vigencia (`validFrom`) | Semana | Base |
|---|---|---|---|---|
| `INITIAL` | la primera semana que se cotiza para el jugador | lunes 00:00 de esa semana | esa semana | ninguna (100,00 de partida) |
| `CALCULATED` | al cerrar cada semana cotizada | cierre de la semana (lunes 00:00 siguiente) | la semana cerrada | D9 |
| `REQUOTED` | recotizar en el acto, sobre la última semana cerrada | el momento en que se hace | la semana recotizada | D9, sobre las semanas anteriores |

- **Una `CALCULATED` por (jugador, semana)**: su `validFrom` es el cierre de su semana, así que el
  índice único `(player_id, valid_from)` ya lo garantiza en la base (dos semanas distintas tienen
  cierres distintos). El modelo además verifica antes de escribir, para que el error sea de
  dominio y no una violación de integridad.
- **Puede haber varias `REQUOTED` en la misma semana** (cada una con su `validFrom`).
- La `INITIAL` rige desde el inicio de la primera semana cotizada, que puede ser **anterior** al
  momento en que se la creó: por eso la cotización a una fecha (D20) la encuentra aunque se haya
  creado después.
- **Inmutabilidad (FR-005)**: `Quote` es un `record` sin operaciones de cambio; no existe
  `update` ni `delete` en `QuoteRepository`; `QuoteSQL` no tiene setters. Un test de modelo y uno
  de repositorio lo fijan.
- **Choque de vigencia**: una recotizada tomaría el mismo `validFrom` que otra solo si dos
  recotizaciones del mismo jugador caen en el mismo instante, imposible porque van detrás de los
  candados de D14 y D17. Si pasara, el índice único lo rechaza y el cambio de estrategia
  responde 409 (`StrategyChangeConflictException`), sin dejar nada a medias.

**Alternativas descartadas**:

- **Un `UPDATE` de la calculada para "reemplazarla"**: viola FR-005.
- **Guardar la variación**: se informa, no se guarda (spec, Key Entities). Sí se guarda el dato de
  entrada (`base_value`, D15).

## D11. Qué semanas se cotizan

**Decisión**: `QuotingPlan` (modelo) decide, a partir de datos ya leídos, qué semanas se cotizan y
cuáles quedan pendientes. Es una función pura de:

- la última semana cotizada (la mayor `week_start` con una `CALCULATED`, o ninguna);
- la primera semana con estadísticas (la de la fecha más temprana de un partido terminado con
  `statsLoaded`);
- los partidos terminados y si ya tienen estadísticas (`Match.needsStats()`, de la 006);
- el instante actual (`Clock`).

Reglas, en orden y sin saltear nada:

1. **Desde dónde**: la semana siguiente a la última cotizada; si no hay ninguna, la primera con
   estadísticas (FR-010). Si no hay estadísticas en ninguna parte, no hay nada para cotizar.
2. **Hasta dónde**: la última semana **cerrada** al instante actual. La semana en curso nunca
   (FR-008). Una semana está cerrada si `now ≥ fin de la semana`.
3. **Una semana con partidos terminados sin estadísticas** espera. Espera 7 días desde su cierre
   (constante `STATS_WAIT` del modelo, FR-011); pasado ese plazo se cotiza con lo que haya.
4. **Una semana pendiente bloquea las siguientes** (FR-011): las que vienen después quedan
   pendientes con otro motivo.
5. **Una semana sin partidos se cotiza** (todos los pares empatan, ningún valor cambia, SC-004).
   También se cotiza una semana sin partidos terminados sin estadísticas pendientes.

Motivos del informe: `MISSING_STATS` (faltan estadísticas de partidos terminados de esa semana) y
`PREVIOUS_WEEK_PENDING` (hay una semana anterior pendiente).

**"Cotizar con lo que haya" después de los 7 días**: las estadísticas que lleguen después no
modifican esa semana (cada semana se cotiza una sola vez, FR-008); solo la recotización en el
acto de la última semana cerrada puede corregirla.

**Volumen**: un jugador activo recibe una cotización por semana cotizada aunque no cambie de
valor. Con 2.600 jugadores son 135.200 filas por año (52 × 2.600) más 2.600 iniciales, unas
137.800 en el primer año; la reconstrucción de una temporada de 40 semanas son unas 106.600 (D15).

**Alternativas descartadas**:

- **Saltear la semana pendiente y cotizar las siguientes**: rompe la memoria del precio (D9) y
  FR-011.
- **No guardar la cotización de un jugador sin cambios**: el spec lo exige guardada (FR-003, US1.5)
  para que el historial semanal sea continuo.

## D12. Recotizar en el acto

**Decisión**: `QuoteWeekWriter.requoteLastWeek` aplica esta secuencia **dentro de la transacción
del cambio de estrategia (D14)**:

1. Cotiza con la estrategia nueva las semanas pendientes según `QuotingPlan` (D11). Son
   cotizaciones `INITIAL` + `CALCULATED`, como en cualquier recálculo.
2. Toma `L`, la última semana cerrada. Si `L` no tiene cotizaciones (porque sigue pendiente), no hay
   nada que volver a cotizar y queda en el informe con su motivo.
3. Por cada jugador activo que tiene una cotización en `L` cuya estrategia **no** es la versión
   nueva, agrega una `REQUOTED` con `validFrom = ahora`, base según D9 (las semanas anteriores a
   `L`) y los puntos de la semana `L` recalculados con la estrategia nueva.
4. Los pares de la normalización son todos los jugadores activos de la posición (como en el
   recálculo normal), no solo los que tienen cotización.

**No acumula** (FR-037): la base nunca incluye las cotizaciones de `L`. Repetirlo con la misma
versión no hace nada (el paso 3 salta a los jugadores cuya última cotización de `L` ya es de esa
versión). Hacerlo con otra estrategia vuelve a partir de la base previa a `L`: US5.5 (C parte de
103,00 y no de 104,00).

**Ejemplo del spec (US5.3)**: inicial 100,00; calculada 103,00; calculada 101,00 con A. Se activa B
con score +0,10 para esa semana y factor 10: la recotizada parte de 103,00 → **104,00**, rige desde
ahora, y la de 101,00 se conserva.

**Justificación**: la decisión de "a quién y de cuánto" es del modelo (`WeeklyQuotation`); el
servicio solo pasa los datos y guarda. Reusar el mismo cálculo para `CALCULATED` y `REQUOTED`
garantiza que simular la estrategia activa sobre un historial calculado solo con ella da lo mismo
que lo vigente (US7.7).

**Alternativas descartadas**:

- **Borrar la calculada y reemplazarla**: viola FR-005.
- **Recotizar todo el pasado**: fuera de alcance del spec.

## D13. Estrategias, versiones y semilla

**Decisión**:

- **Una fila de `strategies` es una versión** (`unique (name, version_number)`, como pidió el
  equipo). El **id de una estrategia en la API es el id de su versión 1** (la "raíz"); las demás
  versiones guardan `root_id` apuntando a ella. Así no hace falta una tabla de estrategias aparte
  ni actualizar la fila recién insertada para conocer su propio id (IDENTITY).
- **Los parámetros de una versión son inmutables** (`StrategyVersion` es un `record` y la fila no
  se actualiza). Lo único que cambia en una fila es la marca `active`, que es estado, no
  parámetro: exactamente una fila de toda la tabla la tiene en `true`.
- **`StrategyCatalog`** (modelo) es el agregado que conoce todas las estrategias: valida el nombre
  único (sin distinguir mayúsculas ni espacios de los extremos, FR-028), crea la versión 1 inactiva,
  crea la versión siguiente, y al activar o editar la activa deja una única versión activa
  (FR-030). El servicio carga el catálogo, llama a un método y le pide al repository que guarde
  los cambios. Hay unas pocas estrategias, así que cargarlas todas es barato.
- **Activar otra estrategia deja activa su versión más reciente**; no se activan versiones viejas
  (Assumptions). Por eso la versión activa de la estrategia activa es siempre su última.
- **Pesos**: `WeightSet` valida que no haya pesos negativos, que cada uno tenga a lo sumo dos
  decimales, que no haya métricas desconocidas y que la suma sea exactamente 1,00 (comparación
  con `BigDecimal.compareTo`). Una métrica sin peso pesa 0. En la base se guardan como filas de
  `strategy_weights` con la columna `weight_set` (`DEFAULT`, `GOALKEEPER`, `DEFENDER`,
  `MIDFIELDER`, `FORWARD`), que es `NOT NULL` para que el índice único
  `(strategy_id, weight_set, metric)` funcione (un `NULL` no choca consigo mismo).
- **Métricas desconocidas**: el request trae los pesos como `Map<String, BigDecimal>` y la
  conversión a `ValuationMetric` la hace el modelo. Si fuera un `Map<ValuationMetric, ...>` en el
  DTO, Jackson fallaría con el mensaje genérico "El cuerpo del request no es un JSON válido." y no
  se podría indicar la métrica no admitida.
- **Nombre repetido**: la regla "sin distinguir mayúsculas" vive en el catálogo (invariante del
  dominio); el índice único `(name, version_number)` de la base es la red de seguridad ante dos
  altas simultáneas, y se traduce a 409.

**Semilla** (`StrategySeeder`, un `ApplicationRunner` como `AdminAccountInitializer`):

- Idempotente: crea cada estrategia de arranque solo si no hay una con ese nombre (sin distinguir
  mayúsculas). No duplica ni modifica las existentes, aunque el administrador las haya editado
  (FR-031, US4.2). Si después de sembrar no hay ninguna activa, activa la A.
- **A "Rendimiento de partido"** (activa, versión 1, factor 10, mínimo 10,00; mismos pesos para
  todas las posiciones): `GOALS` 0,25; `ASSISTS` 0,15; `SHOTS` 0,10; `KEY_PASSES` 0,10;
  `SUCCESSFUL_DRIBBLES` 0,10; `TACKLES` 0,10; `RATING` 0,20. Suma 1,00.
- **B "Equilibrada por posición"** (versión 1, inactiva, factor 10, mínimo 10,00). Los pesos los
  fija el equipo como punto de partida. No incluyen atajadas porque el scraper no las guarda.

  | Métrica | Delantero | Mediocampista | Defensor | Arquero |
  |---|---|---|---|---|
  | `GOALS` | 0,28 | 0,10 | | |
  | `ASSISTS` | 0,14 | 0,14 | | |
  | `SHOTS_ON_TARGET` | 0,12 | | | |
  | `KEY_PASSES` | 0,06 | 0,16 | | |
  | `SUCCESSFUL_DRIBBLES` | 0,08 | 0,06 | | |
  | `PASS_ACCURACY` | | 0,10 | 0,06 | 0,10 |
  | `PASSES` | | 0,06 | 0,04 | 0,04 |
  | `TACKLES` | | 0,08 | 0,16 | |
  | `INTERCEPTIONS` | | 0,06 | 0,16 | |
  | `CLEARANCES` | | | 0,10 | 0,08 |
  | `AERIALS_WON` | | | 0,08 | 0,07 |
  | `RATING` | 0,20 | 0,18 | 0,22 | 0,40 |
  | `TEAM_RESULT` | 0,05 | 0,02 | 0,02 | 0,02 |
  | `CLEAN_SHEETS` | | | 0,08 | 0,25 |
  | `MINUTES_PLAYED` | 0,03 | 0,02 | 0,02 | 0,02 |
  | `FOULS_COMMITTED` | 0,01 | | 0,02 | |
  | `YELLOW_CARDS` | 0,02 | 0,02 | 0,02 | 0,01 |
  | `RED_CARDS` | 0,01 | | 0,02 | 0,01 |
  | **Suma** | **1,00** | **1,00** | **1,00** | **1,00** |

  Sumas verificadas a mano: delantero 0,28+0,14+0,12+0,06+0,08+0,20+0,05+0,03+0,01+0,02+0,01 = 1,00;
  mediocampista 0,10+0,14+0,16+0,06+0,10+0,06+0,08+0,06+0,18+0,02+0,02+0,02 = 1,00; defensor
  0,16+0,16+0,10+0,08+0,06+0,04+0,22+0,08+0,02+0,02+0,02+0,02+0,02 = 1,00; arquero
  0,40+0,25+0,10+0,08+0,07+0,04+0,02+0,02+0,01+0,01 = 1,00. El test de la semilla las vuelve a
  verificar con `WeightSet`.
- **El juego por defecto de B** es obligatorio (FR-027) y el equipo no lo fijó. Como B define las
  cuatro posiciones, el juego por defecto no se usa nunca mientras existan solo esas cuatro. Se
  deja **igual al de mediocampista** (el más equilibrado). Se anota en D28.
- B cumple FR-031: pesos distintos en al menos dos posiciones, suma 1,00 en cada una, una métrica
  de disciplina (`FOULS_COMMITTED`, `YELLOW_CARDS`, `RED_CARDS`) y una de contexto (`TEAM_RESULT`,
  `CLEAN_SHEETS`).

**Alternativas descartadas**:

- **Una tabla `strategies` por estrategia y otra por versión**: el equipo pidió tres tablas y el
  índice único `(name, version)`.
- **Un singleton `active_strategy`**: es más limpio para la inmutabilidad de las filas, pero es una
  cuarta tabla que el equipo no pidió. Se anota la marca `active` como el único estado mutable.
- **Id de estrategia = nombre**: obliga a escapar el nombre en la URL y a permitir espacios.
- **Pesos como JSON en una columna**: no se pueden consultar ni tienen índice único; contradice la
  persistencia explícita del Principio VI.

## D14. Cambiar la activa: opción, transacción y candados

**Decisión**:

- **Opción**: `AHORA` o `PROXIMA_SEMANA` (valores de la API; en Java `RequoteTiming.NOW` y
  `NEXT_WEEK`, con la traducción en `RequoteTiming.fromWire`). Si no viene, **`PROXIMA_SEMANA`**
  (FR-034). Un valor que no es ninguno de los dos se rechaza con 400 (validación de forma del
  DTO, Principio III).
- **Dónde aplica**: en `POST /strategies/{id}/activate` y en `POST /strategies/{id}/versions`
  cuando esa estrategia es la activa (editar la activa).
- **Crear una estrategia o editar una que no es la activa NO admite la opción: se IGNORA**, como
  dice el spec (FR-034, US5.9). No se rechaza con 400:
  - `POST /strategies` (crear) ni siquiera define el campo; si el cliente lo manda, Jackson lo
    descarta (el proyecto no falla con propiedades desconocidas, D4 de la 005).
  - `POST /strategies/{id}/versions` sobre una estrategia no activa acepta el campo y lo ignora.
  - Se documenta en `contracts/strategies-api.yaml`. Rechazar con 400 sería más estricto, pero
    haría fallar a un cliente que manda siempre el mismo formulario, y el spec pide ignorar.
  - Un valor de la opción que no existe (por ejemplo `"AHORA2"`) **sí** es 400 en todos los casos
    donde el campo se lee.
- **Con `AHORA`** (y la estrategia activa cambia: activar o editar la activa), el cambio de
  estrategia y la recotización van en **UN método `@Transactional`**
  (`StrategyChangeWriter.applyAndRequote`). Si cualquier paso falla, la activa **no cambia**.
- **Los candados van afuera de la transacción**, en `StrategyChangeService` (que no es
  `@Transactional`):
  1. toma `RecalculationLock` (D17): un recálculo a la vez y no junto con una sincronización;
  2. con el candado tomado, llama a `StrategyChangeWriter.applyAndRequote` (la transacción abre,
     escribe y **commitea al volver**);
  3. suelta el candado y recién entonces invalida el caché (D21).

  Así, si el candado está tomado, el 409 sale **antes** de abrir la transacción: ni la activa ni
  las cotizaciones cambian (FR-038). Y el candado se suelta **después** del commit, de modo que un
  recálculo que arranque enseguida ya ve la activa nueva. Es el mismo esquema de la 005
  (`SyncService` sin transacción + `SyncWriteService` transaccional).
- **Con `PROXIMA_SEMANA`**: se llama a `StrategyChangeWriter.apply` (transaccional, sin recotizar y
  **sin tomar candados**). Un recálculo en curso termina con la estrategia que tenía al empezar
  (FR-039) porque lee la versión activa **una sola vez** al principio (D17).
- **Atomicidad ampliada**: como las semanas pendientes también se cotizan dentro de esa transacción
  (D12, paso 1), un fallo en cualquiera de ellas deshace todo, incluida la recotización. Es más
  estricto que el recálculo normal (una transacción por semana). En el caso normal son una o dos
  semanas; en la primera ejecución con una temporada entera serían unas 106.600 filas en una
  transacción (D15). Si la medición de D15 la ve inaceptable, el recurso es aplicar primero el
  recálculo normal y recotizar después, a costa de perder el "todo o nada" del cambio.
- **Respuesta**: `{ strategy, requotation }`; `requotation` es el informe de la recotización con
  `AHORA` y `null` en cualquier otro caso (FR del pedido y US5).

**Justificación**: el pedido del equipo fija el método único y el rechazo sin efecto lateral; esta
estructura lo cumple y deja el candado fuera de la transacción por la razón de la 005 (que un
tercero vea el commit antes de que se libere).

**Alternativas descartadas**:

- **Tomar el candado dentro del método `@Transactional`**: se liberaría antes del commit y otro
  recálculo podría leer la activa vieja.
- **`@Transactional` en `StrategyChangeService` con `TransactionTemplate` para el interior**: el
  equipo pidió "un método de servicio `@Transactional`"; un bean aparte lo hace explícito y evita
  la auto-invocación sin proxy.
- **Rechazar con 400 la opción en crear y en editar una no activa**: se descartó arriba.

## D15. Persistencia y volumen

**Decisión**: tres tablas, con nombre explícito en `@Table`, sin SQL nativo (Principio VI).

| Tabla | Columnas | Notas |
|---|---|---|
| `strategies` | `id` (IDENTITY), `root_id` (FK `strategies`, nulo en la v1), `name`, `version_number`, `scale_factor`, `minimum_value`, `active`, `created_at` | único `(name, version_number)` |
| `strategy_weights` | `id`, `strategy_id` (FK), `weight_set`, `metric`, `weight` | único `(strategy_id, weight_set, metric)` |
| `quotes` | `id`, `player_id` (FK), `week_start`, `valid_from`, `quote_value`, `base_value` (nulo), `score`, `quote_type`, `strategy_id` (FK a la versión) | único `(player_id, valid_from)` |

- **Índices pedidos**: `ix_quotes_player_valid_from_desc (player_id, valid_from DESC)`,
  `ux_quotes_player_valid_from (player_id, valid_from)` y `ux_strategies_name_version (name,
  version_number)`. Se declaran con `@Table(indexes = ...)`, como `ux_matches_external_id` de la 005.
  - **Nota**: el índice único `(player_id, valid_from)` ya sirve para recorrer en los dos sentidos;
    el `DESC` es redundante para el motor y cuesta una escritura más por fila. Se crea porque el
    equipo lo pidió; si la medición de abajo lo muestra como costo relevante, se puede quitar sin
    cambiar ninguna consulta.
  - **Índice agregado**: `ix_quotes_week_start_type (week_start, quote_type)`, para resolver "la
    última semana calculada" sin recorrer las 135.000 filas. Es una decisión de este plan, no del
    pedido.
- **Palabras reservadas**: `value` es palabra reservada en H2 2.x y `type` y `version` son
  delicadas en otros motores, por eso las columnas se llaman `quote_value`, `quote_type` y
  `version_number`.
- **`base_value`**: el valor base con el que se calculó la cotización. No es una cotización ni es la
  variación (que se informa y no se guarda): es el dato de entrada. Con él, la variación de una
  cotización es `valor − base_value` sin buscar a la anterior (evita N+1 en el historial y en el
  ranking), y una recotizada se mide contra el mismo `base_value` que la calculada que reemplaza
  (Assumptions). La `INITIAL` lo lleva en `NULL`.
- **Tipos**: `quote_value` y `base_value` son `NUMERIC(12,2)`; `score` `NUMERIC(10,6)`;
  `scale_factor` `NUMERIC(8,2)`; `minimum_value` `NUMERIC(12,2)`; `weight` `NUMERIC(3,2)`; los
  enums, `VARCHAR`; `Instant`, `TIMESTAMP WITH TIME ZONE` (como `matches.utc_date`).
- **Consultas** (derived queries y JPQL, con `@EntityGraph` donde hay que traer asociaciones):
  último valor por jugador antes de una semana, últimas cotizaciones de una semana por jugador,
  actual de un jugador o de varios, la vigente a un instante, historial con rango, ranking con
  filtros y la última semana calculada. Las subconsultas "última por jugador" usan el índice
  `(player_id, valid_from)`.
- **`open-in-view: false`**: los repositories son `@Transactional(readOnly = true)` y los mappers
  corren adentro, así la carga lazy de `PlayerSQL`/`TeamSQL` funciona.

**Volumen**: estimación, **a medir en H2** (SC-009).

| Escenario | Filas | Qué se mide |
|---|---|---|
| Una semana, 2.600 jugadores | 2.600 `CALCULATED` | tiempo de la transacción de una semana |
| Primera reconstrucción de una temporada (40 semanas) | 2.600 `INITIAL` + 104.000 `CALCULATED` ≈ 106.600 | tiempo total (objetivo SC-009: menos de 5 min) y una semana (menos de 1 min) |
| Un año | 135.200 + 2.600 ≈ 137.800 | tamaño de la base de archivo y tiempo de las consultas |

- **IDENTITY no permite inserts JDBC en lote** (igual que en la 005, D16): cada `save` de una fila
  nueva es un `INSERT` suelto. Con 2.600 filas por semana es del orden de segundos en H2; la
  medición lo confirma o lo refuta.
- **La medición**: `QuoteVolumeBenchmarkIT`, un test de integración con H2 en archivo que solo corre
  si existe la variable de entorno `QUOTES_BENCHMARK=true` (`@EnabledIfEnvironmentVariable`; queda
  fuera del `./gradlew build` normal, que no debe demorarse minutos), y que además verifica los
  topes de SC-009 cuando corre. Se usa una variable de entorno y no una propiedad `-D` porque Gradle
  no reenvía sus propiedades al proceso que ejecuta los tests. La guía está en `quickstart.md`.
- **Plan B si SC-009 no se cumple**: pasar `QuoteSQL.id` a `GenerationType.SEQUENCE` con
  `allocationSize` y activar `hibernate.jdbc.batch_size`. H2 y PostgreSQL lo soportan, así que sigue
  siendo portable. Cambia la estrategia de id de una sola entidad, algo que el equipo debe avalar
  (D28) antes de hacerlo, y no se hace a priori.

**Alternativas descartadas**:

- **No guardar las semanas sin cambio**: el spec las pide (FR-003).
- **`JdbcTemplate` para el insert masivo**: agrega una vía de persistencia fuera del
  repository/DAO que la constitución define.
- **`@Query(nativeQuery = true)`**: prohibido por el Principio VI.

## D16. Estadísticas de entrada (006)

**Decisión**: esta feature **no crea** la entidad de estadísticas: consume `PlayerMatchStats` de la
006 (`modelo/performance/`) a través de `PlayerMatchStatsRepository`, que existe en la rama
`feature/scraping-whoscored`. El **contrato de entrada esperado** (qué campos usa y qué pasa si la
006 los nombra distinto) está en [data-model.md](./data-model.md), sección "Entrada esperada de la
006".

Lo que la 006 expone hoy y esta feature **necesita agregarle** (puntos P-1 y P-2 de D18):

- `PlayerMatchStatsRepository.findAllByMatchBetween(Instant fromInclusive, Instant toExclusive)`,
  con `@EntityGraph` sobre `player`, `player.team`, `match`, `match.homeTeam` y `match.awayTeam`.
  Hoy solo existe `findAll()`, que trae todo el historial: sirve para la simulación, pero no para
  cotizar una semana.
- `MatchRepository.findAllFinished()` (partidos terminados con sus equipos y su temporada), para
  armar `QuotingPlan`.

Ambos son métodos de lectura nuevos sobre clases existentes; no cambian ninguna firma. **Los tests
arman las estadísticas con un builder de test** (`PlayerMatchStatsBuilder`, en `src/test`): ninguna
prueba de esta feature depende de la entidad `PlayerMatchStatsSQL` salvo los de integración de los
repositories nuevos.

**Qué cambia si la 006 nombra distinto**: los nombres que usa el modelo están concentrados en una
clase, `WeeklyPerformance`, que lee los campos de `PlayerMatchStats` (`counts().get(Metric.X)`,
`minutesPlayed()`, `rating()`, `player()`, `match()`). Si la 006 cambia un nombre, se toca esa
clase y su test; el resto del modelo (`MetricNormalization`, `WeeklyQuotation`, `QuotingPlan`)
trabaja con `ValuationMetric` y no ve los nombres de la 006.

**Puerta**: esta feature **no se puede implementar hasta que la 006 esté integrada en `develop`**
(Dependencies del spec). La fase de implementación empieza con esa verificación.

**Alternativas descartadas**:

- **Que esta feature defina su propia entidad de estadísticas**: duplicaría la 006.
- **Leer la tabla `player_match_stats` con JPQL desde `QuoteRepository`**: cruzaría contextos por
  debajo del repository correspondiente.

## D17. Recálculo: orquestación y disparadores

**Decisión**:

- **`QuoteRecalculationService`** (no `@Transactional`, como `SyncService`):
  1. toma `RecalculationLock`;
  2. **lee la estrategia activa una sola vez** (FR-039): una `StrategyVersion` inmutable que usa
     toda la corrida, aunque durante ella se cambie la activa con `PROXIMA_SEMANA`;
  3. carga lo necesario para `QuotingPlan` (el último cierre cotizado y los partidos terminados) y
     le pide el plan;
  4. por cada semana a cotizar **en orden**, llama a `QuoteWeekWriter.quoteWeek(...)`, que es
     `@Transactional`: una transacción por semana, todo o nada (FR-012, SC-016);
  5. si una semana falla con un error de la base, **corta**: las semanas siguientes no se intentan
     (no se saltea ninguna) y el informe indica el error; la siguiente corrida la reintenta (US6.11);
  6. arma el `RecalculationReport`, lo escribe en el registro (`RecalculationReportLogger`) e
     invalida el caché (D21).
- **`RecalculationLock`** (`service/quote/`, un `@Component`): un `Semaphore(1)` propio más el candado
  de sincronización de la 006 (`SyncLock`).
  - Un segundo recálculo: `RecalculationInProgressException` ("Ya hay un recálculo en curso."), 409.
  - Una sincronización de cualquiera de las dos fuentes en curso:
    `RecalculationBlockedBySyncException` ("No se puede recalcular: hay una sincronización en
    curso."), 409. Hoy `SyncLock.runExclusively` lanza `SyncInProgressException` y el candado lo
    traduce.
  - No espera ni encola (patrón de `SyncService`).
  - Orden: primero el semáforo propio, después `SyncLock`; se sueltan al revés.
  - Mientras corre un recálculo, la 006 no puede sincronizar (su `SyncLock` está tomado) y recibe 409.
    **Es un efecto sobre la 006 que el equipo tiene que aceptar** (P-2 de D18).
- **Disparadores** (FR-047):
  - **Cron**: `QuoteScheduler` con `@Scheduled(cron = "${futbolmarket.quotes.recalculation.cron}",
    zone = "${futbolmarket.quotes.recalculation.zone}")`. Por defecto `0 0 6 * * MON`, zona
    `America/Argentina/Buenos_Aires`. El valor `"-"` deshabilita la tarea (como en la 005). Si hay
    otro recálculo o una sincronización en curso, se omite con un `WARN` con el motivo.
  - **Arranque**: `QuoteStartupRecalculation`, `@Async` sobre `ApplicationReadyEvent`, no demora el
    arranque. Solo corre si `on-startup` es `true` **y** `QuotingPlan` dice que hay semanas cerradas
    sin cotizar (US6.3). El perfil test lo pone en `false`. Si hay una sincronización en curso, se
    omite con un `WARN`; la semana queda pendiente para el cron.
  - **Manual**: `POST /quotes/recalculate` (solo `ADMIN`), espera y devuelve el informe con 200. Con
    la primera reconstrucción puede tardar minutos (SC-009); es el mismo criterio de espera de
    `POST /players/sync`.
  - **Al terminar la cadena de sincronización**: **todavía no existe** (D18, P-3). Mientras tanto
    valen el cron de las 06:00 (dos horas después de la cadena de las 04:00), el arranque y el
    disparo manual.
- **Propiedades** (`QuoteRecalculationProperties`, `futbolmarket.quotes.recalculation.*`): `cron`,
  `zone` y `on-startup`, validadas al enlazarse como `FootballDataProperties.Sync`. `application-test.yml`
  pone `cron: "-"` y `on-startup: false`.
- **El informe no se persiste** (como en la 005 y la 006): va al registro y a la respuesta del
  disparo manual. Contiene las semanas cotizadas (con la cantidad de cotizaciones `INITIAL`,
  `CALCULATED` y `REQUOTED` de cada una), las semanas pendientes con su motivo, el origen, la
  estrategia usada y la duración.
- **Un recálculo sin semanas para cotizar** termina con informe vacío, sin error (FR-052).
- **Ningún recálculo consulta una fuente externa** (FR-051): solo repositories.

**Alternativas descartadas**:

- **`@ConcurrencyLimit` o `synchronized`**: encolan en lugar de rechazar (005, D11).
- **`SyncLock` como único candado**: serviría para recálculo contra sincronización, pero un
  recálculo contra otro recálculo diría "ya hay una sincronización en curso", que es mentira. Se
  necesitan los dos motivos distintos (FR-049 y FR-050).
- **Una sola transacción para toda la corrida**: una falla en la semana 38 perdería las 37 buenas, y
  contradice FR-012 (por semana).
- **Un hilo propio con `ExecutorService` para el disparo manual**: el spec y la 005 lo hacen
  síncrono.

## D18. Coordinación con la 006

Estos son **puntos a acordar con la 006** (el dueño de esa rama), no decisiones tomadas. Ninguno
frena el diseño; los dos primeros frenan la implementación.

| # | Punto | Propuesta de esta feature | Mientras tanto |
|---|---|---|---|
| P-1 | Métodos de lectura nuevos en `PlayerMatchStatsRepository` y `MatchRepository` (D16) | Agregarlos como métodos nuevos, sin tocar firmas existentes. Los agrega quien implemente esta feature, **después** de integrar la 006 | Sin ellos no se puede cotizar una semana: es una puerta |
| P-2 | **Exclusión mutua con la sincronización** | El recálculo toma `SyncLock` (D22 de la 006) dentro de su propio candado. Pedido a la 006: un `tryRunExclusively` que devuelva vacío en lugar de lanzar `SyncInProgressException`, para no traducir excepciones. Y aceptar que un recálculo en curso hace que una sincronización responda 409 | Se traduce la excepción (D17) |
| P-3 | **Disparo al terminar la cadena** | `SyncCoordinator` publica un evento de aplicación (por ejemplo `SyncChainFinishedEvent`) **después de soltar el `SyncLock`**, y esta feature lo escucha con `@EventListener` y dispara un recálculo con origen `CHAIN` (omitiéndolo con `WARN` si hay conflicto). Hay que publicar después de soltar el candado, porque el recálculo lo necesita | No existe: valen el cron, el arranque y el disparo manual |
| P-4 | **El caché tras una sincronización**: los jugadores activos y los equipos cambian | El mismo evento de P-3 invalida el caché del ranking | La próxima invalidación es el recálculo del lunes a las 06:00; una sincronización manual a deshoras deja el ranking desactualizado hasta entonces (D27) |
| P-5 | `SyncOrigin` de la 006 (`WEEKLY`, `CATCH_UP`, `MANUAL`) | No se reutiliza: esta feature tiene su propio `RecalculationOrigin` (`WEEKLY`, `STARTUP`, `MANUAL`, `STRATEGY_CHANGE`, y `CHAIN` cuando exista P-3). Así no se acoplan los enums | n/a |
| P-6 | La 006 cambia el constructor de `Match` (suma `whoscoredId` y `statsLoaded`) | Esta feature usa `Match.needsStats()` y los accesores públicos; no construye `Match` directamente en producción. Los tests usan los constructores de conveniencia | El builder de test se adapta al integrar |

**Justificación**: la constitución prohíbe "contratos de integración entre contextos ni entre
features"; por eso esto queda como puntos a acordar en la investigación y no como un archivo de
`contracts/`.

## D19. Simulación

**Decisión**: `QuoteSimulationService.simulate(...)` reproduce **en memoria y sin persistir** todas
las semanas que el sistema cotizaría hoy.

- **Entrada**: una estrategia guardada (su **versión más reciente**) o parámetros (factor, mínimo y
  pesos), uno de los dos (FR-054). Con los dos o con ninguno: `InvalidSimulationRequestException`
  (400). Los parámetros se validan con las **mismas** reglas que una estrategia: se construye una
  `StrategyVersion` con ellos y el modelo la rechaza igual (FR-057). Una estrategia inexistente:
  `StrategyNotFoundException` (404).
- **Foto consistente**: se leen de una vez, en una sola consulta por tipo, los jugadores activos,
  los partidos terminados y las estadísticas, y se calcula todo en memoria sobre esos datos. Lo que
  una sincronización guarde después no la altera (Assumptions). No toma el candado de sincronización
  ni el de recálculo.
- **Cálculo**: `QuotingPlan` para saber qué semanas se cotizarían hoy (incluida la espera de 7 días)
  y luego `WeeklyQuotation` semana por semana, **el mismo código que el recálculo**, partiendo todos
  de 100,00. Por eso, si el historial se cotizó íntegramente con la versión vigente de la activa, la
  simulación de esa estrategia da lo mismo que lo vigente (US7.7).
- **Salida**: por jugador, su cotización actual (la última guardada, o ninguna) y la simulada, con
  los filtros de liga, equipo y posición y la paginación del catálogo (FR-055). También informa la
  estrategia usada, cuántas semanas simuló y las semanas pendientes.
- **Sin efectos**: ninguna clase de la simulación inyecta un repository de escritura. Un test de
  integración compara cotizaciones, estrategias y activa antes y después, y dos llamadas iguales
  dan respuestas idénticas (SC-010, US7.6).
- **Rendimiento**: carga todas las estadísticas de la temporada de una vez (unas 60.000 a 80.000
  filas en una temporada completa) y hace un `O(n log n)` por métrica y semana. **Cada página
  vuelve a calcular todo** (el servicio no tiene estado); la meta del spec es menos de 30 segundos
  para la primera página (SC-010). Si la medición en `quickstart.md` mostrara que paginar es
  incómodo, el recurso es guardar el último resultado en un caché de una entrada, no hace falta
  hacerlo a priori.

**Alternativas descartadas**:

- **Persistir la simulación en tablas temporales**: el spec la define como sin efectos.
- **Simular dentro de una transacción con rollback**: un rollback no equivale a "no tocar nada"
  (secuencias IDENTITY, bloqueos) y contradice la simulación sin efectos.

## D20. Consultas y API

**Decisión**: tres controllers y cinco grupos de recursos. Los contratos completos están en
[contracts/](./contracts/).

| Recurso | Acceso | Controller |
|---|---|---|
| `GET /players/{id}/quotes` (historial paginado, `from`, `to`, `page`, `size`) | público | `QuoteController` |
| `GET /players/{id}/quotes/current` | público | `QuoteController` |
| `GET /players/{id}/quotes/at?date=` | público | `QuoteController` |
| `GET /players/quotes/current?ids=` (lote, para la pizarra, D23) | público | `QuoteController` |
| `GET /players/ranking` (filtros `league`, `team`, `position`, `page`, `size`) | público | `QuoteController` |
| `POST /quotes/recalculate` | `ADMIN` | `QuoteAdminController` |
| `POST /quotes/simulate` | `ADMIN` | `QuoteAdminController` |
| `POST /strategies`, `GET /strategies`, `GET /strategies/{id}` | `ADMIN` | `StrategyController` |
| `POST /strategies/{id}/versions`, `POST /strategies/{id}/activate` | `ADMIN` | `StrategyController` |

**Qué informa cada cotización**: valor del 100 %, precio por token, variación (monto y porcentaje
contra `base_value`; ausente en la `INITIAL`), semana (`weekStart`), vigencia (`validFrom`), tipo,
y la estrategia con su versión (`id`, `name`, `version`). **Nunca estadísticas crudas ni métricas
normalizadas.**

**Sobre el score**: el pedido de planificación lista "score" entre los datos de cada cotización,
pero el spec lo prohíbe (FR-006: "Ninguna respuesta MUST exponer [...] el score de una cotización";
y "Fuera de alcance: mostrar [...] el score de una cotización"). **Este plan sigue el spec: el
score se guarda (FR-003) y no se expone** (D28, punto 3). Si el equipo decide exponerlo, es un campo
más en `QuoteResponse`, en el contrato y en `types/quotes.ts`, y se enmienda FR-006.

**Otras decisiones**:

- **Cotización actual de un jugador sin cotización**: `404` con un mensaje propio
  (`PlayerWithoutQuoteException`: "El jugador {id} no tiene cotización."), distinto del
  `PlayerNotFoundException` existente ("No existe un jugador con id {id}."). Los dos son 404 con
  excepciones y mensajes distintos (FR-041). El frontend los distingue por el flujo: pide el
  jugador primero y la cotización después, así que un 404 de la cotización sobre un jugador que
  existe significa "sin cotización".
- **Cotización a una fecha**: la última cotización con `validFrom` anterior al comienzo del día
  siguiente (hora de Argentina) (FR-043). Una fecha sin cotización → 404
  `QuoteNotFoundAtDateException`. Una fecha futura → 400. Un formato inválido → 400.
- **Fechas como texto**: `date`, `from` y `to` se reciben como `String` y los parsea el modelo
  (`QuoteDay.parse`), para que el mensaje indique **el valor no admitido** (FR del spec). Con
  `LocalDate` como tipo del parámetro, el advice contestaría el genérico "Los parámetros enviados
  no son válidos." sin decir cuál. Es el mismo criterio de `league` en `POST /players/sync`.
- **Rango**: `from` y `to` incluyen sus dos extremos (el día completo en hora de Argentina);
  `from > to` → 400.
- **Paginación**: la del catálogo (primera hoja 0; respuesta `content`, `page`, `size`,
  `totalElements`, `totalPages`, `hasPrevious`, `hasNext`), con `QuotePage<T>`. Historial: `size`
  por defecto 20 y **máximo 100**, porque el supuesto del spec exige que una temporada entera
  (unos 52 puntos semanales, más iniciales y recotizadas) entre en una página. Ranking y simulación:
  por defecto 10 y máximo 50, igual que el catálogo.
- **Ranking**: JPQL con la última cotización de cada jugador activo (`validFrom` máximo hasta ahora)
  y los filtros opcionales, ordenado por valor descendente, luego por nombre y luego por id. La
  respuesta incluye la estrategia activa (FR-045). Los valores inválidos de `league` y `position`
  se rechazan como en el catálogo (400 por el binding de enums) y `team` con el mismo `@Pattern`.
- **Un jugador inactivo con cotización** no aparece en el ranking pero su cotización actual se
  consulta por id (FR-014).
- **Ranking contra `/players/{id}`**: `/players/ranking` es una ruta literal y Spring MVC la prefiere
  sobre `/players/{id}`. Se agrega un test que fija el routing (D22).
- **Modelo de la paginación**: `QuotePage<T>` calcula `totalPages`, `hasPrevious` y `hasNext` con la
  misma regla que `PlayerPage`. Hay una duplicación menor y deliberada: consolidar `PlayerPage`
  sería un refactor de la 002 que nadie pidió.

**Alternativas descartadas**:

- **Un solo `GET /players/{id}/quotes?at=`**: mezcla un recurso con un cálculo y el pedido del
  equipo definió las tres rutas.
- **204 para "sin cotización"**: es distinguible, pero el spec dice que el sistema "responde que no
  tiene cotización" y el cliente HTTP del frontend tendría un caso más.
- **Exponer `PlayerResponse.currentQuote`**: ver D23.

## D21. Caché

**Decisión**: caché en memoria con `ConcurrentMapCacheManager` para dos cosas: la cotización vigente
de un jugador y el ranking.

- `CacheConfig` (`config/`): `@Configuration @EnableCaching` con un bean `CacheManager`
  (`ConcurrentMapCacheManager("quotes-current", "quotes-ranking")`). Los valores cacheados son
  objetos de modelo inmutables (`Quote`, `RankingPage`), no entidades JPA.
- `QuoteQueryService` marca `getCurrent(playerId)` y `getRanking(...)` con `@Cacheable`; las claves
  son el id y los criterios (liga, equipo, posición, hoja, tamaño). El historial, la consulta a una
  fecha, el lote de la pizarra y la simulación **no** se cachean.
- **Cuándo se invalida** (todo el caché, `@CacheEvict(allEntries = true)` a través de
  `QuoteCacheInvalidator`):
  - al **terminar** un recálculo (con o sin semanas cotizadas, si cotizó al menos una);
  - al **terminar** una recotización en el acto;
  - al **cambiar la activa** (el ranking informa la estrategia activa), también con
    `PROXIMA_SEMANA`.
- **La invalidación va después del commit**, en el servicio no transaccional (D14 y D17). Si
  fuera dentro de la transacción, un lector podría repoblar el caché con los datos viejos antes del
  commit.
- **Sin TTL**: `ConcurrentMapCacheManager` no vence entradas. Es un límite conocido (D27): una
  sincronización que cambie jugadores activos o equipos no invalida el caché hasta P-4.
- **Memoria**: la cotización vigente son a lo sumo unas 2.600 entradas pequeñas; el ranking, una por
  combinación de filtros pedida (unas decenas), así que no hace falta un tope.

**Alternativas descartadas**:

- **Caché en la base de datos o por `@Query` de vista materializada**: SQL no portable.
- **Cachear dentro del repository**: lo trata como detalle de persistencia, pero la clave natural
  (los filtros del caso de uso) es del servicio.

## D22. Seguridad

**Decisión**:

- `SecurityConfig` suma dos reglas, antes de `anyRequest().authenticated()`:
  - `pathPattern(HttpMethod.POST, "/quotes/**")` → `hasRole(ADMIN)`;
  - `pathPattern("/strategies/**")` (todos los métodos, porque listar y consultar también son de
    administrador, FR-032) → `hasRole(ADMIN)`.
  Sin credencial responde 401 y un `USER` recibe 403, los dos antes del controller y sin llegar al
  servicio (mismo criterio de `POST /players/sync`).
- **Las lecturas bajo `/players/**` ya son públicas** por la regla `TEMPORAL` de
  `publicEndpoints()`: `GET /players` y `GET /players/**` (con la marca "la protección de estos
  endpoints se activa en un PR posterior"). Eso cubre sin cambios `GET /players/ranking`,
  `GET /players/{id}/quotes`, `/quotes/current`, `/quotes/at` y `GET /players/quotes/current`.
  **Queda señalado**: cuando el PR posterior active la protección del catálogo, estas rutas
  quedarían protegidas por el mismo cambio, y el spec exige que sean públicas (FR-040). Hay que
  coordinarlo entonces; el plan no toca esa regla.
- **Test pedido**: `GET /players/ranking` contra `GET /players/{id}` (`RankingRoutingIT`), que
  prueba tres cosas: que `/players/ranking` se resuelve al ranking y no a `/players/{id}` con
  `id = "ranking"`; que es público (200 sin credencial); y que `/players/{id}` sigue respondiendo
  como antes (200 con un jugador, 404 con uno inexistente, 400 con un id no numérico).

**Alternativas descartadas**:

- **Mover las consultas de cotización a `/quotes/**` con su propia regla pública**: contradice la
  indicación del equipo (la lectura bajo `/players/**`) y agrega una segunda regla pública que
  mantener.
- **Un único `/admin/**`**: cambia el prefijo de `/players/sync` (005) y de los endpoints que el
  equipo ya definió.

## D23. Cómo llega la cotización a la pizarra

**Decisión**: **una consulta por lote**, `GET /players/quotes/current?ids=1,2,3`, con un máximo de 50
ids (el tamaño máximo de página del catálogo). Responde `{ "quotes": [ ... ] }` con la cotización
actual de cada jugador que tiene una; los que no tienen **no figuran** (la pizarra los muestra
"sin cotización"). Un id inválido o más de 50 → 400. Va bajo `/players/**`, así queda público sin
tocar `SecurityConfig`.

La pizarra hace **una consulta de jugadores y una de cotizaciones por hoja**, no una por jugador
(FR-062).

**Justificación**: es una ruta nueva y aditiva: no cambia `PlayerResponse` ni sus tests.

**Alternativas descartadas**:

- **Sumar `currentQuote` a `PlayerResponse`**: obliga a que `PlayerCatalogService` (002) dependa de
  `quote`, cambia un contrato público que los tests de `PlayerControllerIT` y de `PlayerResponse`
  asertan, y acopla el catálogo al recálculo. Habría que tocar tests existentes con el "sí" del
  equipo.
- **Usar el ranking**: pagina distinto que la pizarra (la pizarra ordena por id y filtra igual) y
  no sirve para una hoja cualquiera del catálogo.
- **Una consulta por jugador**: prohibido por FR-062.

## D24. Frontend

**Decisión**: la estructura de la constitución (por tipo de artefacto), sin carpetas nuevas. El
detalle de archivos está en `plan.md`.

- **Rutas nuevas**: `/ranking` (pública), `/admin/estrategias`, `/admin/recalculo` y
  `/admin/simulacion` (solo `ADMIN`; `/admin` redirige a `/admin/estrategias`).
- **Guarda por rol** (`router/RequireRole.tsx`): compone la guarda de sesión que ya existe
  (`RequireSession`, que lleva a `/login` a un visitante, igual que las demás pantallas privadas,
  FR-064) y, con la sesión autenticada, mira `profile.role` (la sesión ya trae el rol). Si no es
  `ADMIN`, muestra un aviso de falta de permiso con salida a la pizarra, sin ningún control de
  administración. La entrada "administración" del encabezado solo se renderiza si el rol es
  `ADMIN`.
- **Pantallas** (`pages/`): `RankingPage`, `AdminStrategiesPage`, `AdminRecalculationPage` y
  `AdminSimulationPage`. La ficha (`PlayerPage`) y la pizarra (`BoardPage`) se amplían.
- **Servicios**: un archivo por área y una función por endpoint (`quoteService`, `strategyService`,
  `recalculationService`). Todos pasan por el cliente `request` existente; ninguna pantalla llama a
  `fetch`.
- **Hooks**: `useCurrentQuotes` (lote), `usePlayerQuote`, `useQuoteHistory`, `useRanking`,
  `useStrategies`, `useRecalculation`, `useSimulation` y `useMutation` (nuevo, para los comandos:
  `useAsync` solo corre por dependencias). El ranking reutiliza `useBoardFilters` (mismos
  parámetros `league`, `team`, `position`, `page`, FR-063).
- **Tipos**: `types/quotes.ts`, escritos a mano desde `contracts/*.yaml`, con los enums tal cual el
  backend (`INITIAL`, `GOALS`, `AHORA`...). Los importes llegan como números JSON.
- **Utilidades** (`utils/`): `quotes.ts` (créditos con 2 decimales y precio por token con 4,
  variación con signo y texto, tipo de cotización, la última por semana, etiquetas de semana),
  `metrics.ts` (etiquetas en español de las 19 métricas), `weights.ts` (suma exacta de pesos en
  centésimos enteros, para no usar `float` en un total que debe ser 1,00), `chart.ts` (geometría).
- **Gráfico**: SVG propio.
  - Una serie con **una cotización por semana: la última de esa semana** (FR-060). Una semana con
    la calculada y una recotizada muestra la recotizada.
  - El SVG es decorativo (`aria-hidden`) y sobre él hay **un `<button>` por punto**, posicionado en
    porcentajes. Así todo lo clickeable es un botón con foco visible. Al enfocar, pasar el mouse o
    activar un punto se muestra su detalle: valor, semana, tipo, estrategia y versión.
  - **Alternativa en texto**: una tabla con los mismos puntos dentro de un `<details>` (FR-060).
  - **Selector de rango**: dos `<input type="date">` y tres botones rápidos (últimas 8 semanas,
    últimas 26, todo el historial); pide el historial con `from` y `to`.
  - **Estados**: carga, error con "reintentar", sin cotización (el jugador existe pero no tiene) y
    rango vacío con mensaje (US3.4 a 3.8).
- **La variación no depende del color** (FR-059): lleva signo (`+3,00`), texto ("sube"/"baja") y un
  triángulo (▲/▼); el color solo refuerza.
- **Formulario de estrategias**: un editor de pesos por juego (por defecto, arquero, defensor,
  mediocampista, delantero) con las 19 métricas, la suma de cada juego en vivo y el envío
  deshabilitado mientras algún juego definido no sume exactamente 1,00 (FR-066). Los valores se
  guardan como texto en el estado del formulario, así un rechazo del servidor no pierde lo escrito.
  El motivo del rechazo se muestra en español (el backend ya lo trae así).
- **Cambio de la activa**: un componente de elección (`RequoteChoice`) con dos opciones y la
  explicación de cada una, **"la semana siguiente" preseleccionada**; no se envía nada hasta
  confirmar (FR-065). Al editar la activa, la elección forma parte del mismo formulario.
- **Recálculo**: un botón que dispara y muestra el informe (semanas, iniciales/calculadas/recotizadas,
  pendientes con su motivo traducido). En un 409 muestra el motivo y conserva la pantalla.
- **Simulación**: elige una estrategia guardada o carga parámetros, con filtros y paginación; la
  tabla muestra por jugador la cotización actual, la simulada y la diferencia con signo.
- **Responsive y a11y**: usable desde 360 px sin desplazamiento horizontal de la página; las tablas
  (ranking, simulación, informe) se transforman en tarjetas apiladas con CSS por debajo de un
  ancho. Todo lo clickeable es `button` o `a`, con foco visible; los errores van en `role="alert"`
  y los estados en `role="status"` (como `ChalkNote`).
- **Estética**: la de la pizarra (tiza, post-its, cinta), con los tokens de `styles/tokens.css`;
  sumar tokens de subida y bajada (verde y rojo) si hace falta, sin librerías.

**Restricciones que impone la 004 a este diseño** (los tests de la 004 quedan sin modificar hasta
el "sí", D25):

1. `/pizarra` tiene **un único** elemento con rol `list` (los tests usan `getByRole('list')`):
   ni el encabezado ni la nueva `QuoteTag` usan `ul`/`ol`.
2. `PostIt` sigue siendo **un único link sin botones**, y su texto sin cotización no cambia.
3. La ficha no repite el texto exacto del equipo, la posición ni la liga (los tests usan
   `getByText` por esos textos): el panel de cotización no los vuelve a escribir.
4. Los nombres accesibles de **botones y enlaces** no contienen las palabras que el test de la 004
   prohíbe (`precio`, `valor`, `puntaje`, `comprar`, `vender`, `sumar`, `plantel`, `presupuesto`,
   `portfolio`, `orden`): se usa "cotización" en lugar de "valor" o "precio" en todo lo
   interactivo. "Valor del jugador" y "Precio por token" aparecen solo como etiquetas de texto.
5. Las cantidades se muestran sin `$`, `€`, `USD` ni `EUR` ("103,00 cr." o "103,00 créditos"), y sin
   estrellas.
6. Los handlers de MSW de cotizaciones van **antes** que `*/api/players/:id` en la lista, porque
   `:id` también captura `ranking`.

**Alternativas descartadas**:

- **Librería de gráficos**: dependencia nueva; la constitución la prohíbe sin justificar.
- **Puntos del gráfico como `<circle tabindex=0 role="button">`**: no es un `button`, y la
  constitución exige `button` o enlace.
- **Una sola página de administración con pestañas internas**: los componentes no conocen el router
  y habría que subir todo el estado; tres rutas con `AdminNav` mantienen cada pantalla simple y
  enlazable.

## D25. Tests de la 004 que cambian

**Decisión**: el spec exige que el plan liste cada test existente de la 004 que cambia, qué
aserciones cambian y por qué (FR-072), y la constitución prohíbe modificarlos hasta el "sí"
explícito del equipo registrado en Clarifications del spec (con nombre y fecha). **Ese "sí" todavía
no figura.** Hasta entonces no se modifica ni se borra ningún test.

Se revisaron los tests de `frontend/tests/` buscando aserciones sobre precio, valor, puntaje,
estrellas y compra. Hay tres tests que **codifican la prohibición vigente**:

| # | Test existente | Aserción actual | Qué cambiaría con el "sí" | Por qué |
|---|---|---|---|---|
| 1 | `tests/pages/NoPurchaseActions.test.tsx` (CE-009), constante `FORBIDDEN` (línea 7) y comentario de la línea 1 | Ningún botón ni enlace de `/pizarra`, `/jugadores/<id>`, `/login`, `/register` ni `/vestuario` tiene un nombre que contenga `comprar`, `vender`, `sumar`, `plantel`, `precio`, `valor`, `presupuesto`, `puntaje`, `portfolio` u `orden` | Quitar `precio` y `valor` de la expresión y actualizar el comentario ("ninguna pantalla ofrece compra, venta ni plantel") | Esta feature muestra precios, valores y cotizaciones. Se conservan `comprar`, `vender`, `sumar`, `plantel`, `presupuesto`, `puntaje`, `portfolio` y `orden` (FR-071 y FR-006) |
| 2 | `tests/pages/NoPurchaseActions.test.tsx`, las otras dos aserciones de `expectNoPurchaseActions` (`PRICE_TEXT` y las estrellas) | Sin `$`, `€`, `USD`, `EUR` ni `★☆⭐` en el texto de la pantalla | **No cambian** | Las cotizaciones se muestran en créditos, sin símbolo de moneda ni estrellas |
| 3 | `tests/components/PostIt.test.tsx`, test "no contiene botones ni textos de compra, venta, plantel, precio o valor" (líneas 33 a 40) | `textContent` sin `compr`, `vend`, `plantel`, `precio` ni `valor`; cero botones | Quitar `precio` y `valor` de la expresión y renombrar el test | Su descripción afirma una prohibición que esta feature levanta |
| 4 | `tests/pages/PlayerPage.test.tsx`, test "la ficha no tiene botones de compra, venta ni similares" (línea 54) | Sin botones con nombre `comprar`, `vender`, `sumar`, `plantel` o `precio` | Quitar `precio` de la expresión | Mismo motivo; `precio` ya no es una acción prohibida |

**Estos cambios son de redacción, no de comportamiento**: con el diseño de D24 (restricciones 1 a 5)
**los cuatro tests pasan sin modificarse**. Los nombres accesibles de los botones y los enlaces
nuevos evitan esas palabras y las cantidades no llevan símbolo de moneda. Por eso:

- **Antes del "sí"**: se implementa la feature sin tocar esos tests, y la suite de la 004
  completa tiene que seguir pasando en cada tarea de frontend (se corre sin editar nada).
- **Después del "sí"**: se aplican los cuatro cambios de la tabla en una tarea propia, al final, y se
  suman los tests nuevos de las pantallas nuevas en archivos nuevos (no en los existentes).
- **Si el equipo decide que no**: el diseño ya cumple, y los tests quedan como están.

**Archivos de soporte que hay que tocar sí o sí** (no son tests, pero están en `tests/`):

| Archivo | Cambio | Por qué |
|---|---|---|
| `tests/mocks/handlers.ts` | Sumar `...quoteHandlers` **antes** de `...playerHandlers` en el arreglo `handlers` (una línea más un `import`) | `tests/setup.ts` usa `onUnhandledRequest: 'error'`: sin handlers de cotizaciones, la pizarra y la ficha existentes fallarían al pedirlas |

Los handlers de cotizaciones y de estrategias nuevos van en archivos **nuevos**
(`tests/mocks/quoteHandlers.ts`, `tests/mocks/quotes.ts`). El usuario administrador de los tests
nuevos se obtiene sobreescribiendo `GET /auth/me` con `server.use(...)` dentro de cada test, así no
se toca `resetMockDb()` ni los usuarios sembrados. Aun así, el cambio de `handlers.ts` se incluye
en lo que se le pide al equipo, por prudencia.

## D26. Estrategia de tests

Principio IV (sin TDD: los tests se escriben junto con cada tarea) y la sección Frontend de la
constitución. El detalle por archivo está en `plan.md`.

**Backend**

- **Dominio** (sin Spring ni base): `QuoteWeek`, `MetricNormalization`, `WeeklyPerformance`,
  `WeightSet`, `StrategyVersion`, `StrategyCatalog`, `StartingStrategies`, `WeeklyQuotation`,
  `QuotingPlan`, `RecalculationReport`, `Quote`, `QuoteDay`, `RequoteTiming`.
  - **Los ejemplos numéricos del spec** van como tests de dominio: los cinco delanteros (−0,6; −0,6;
    0,0; +0,4; +0,8), el score 0,30, 100,00 → 103,00 → 101,00, el mínimo 10,00 (12,00 con −0,50) y la
    recotizada 104,00 desde 103,00.
  - **Casos de borde**: todos los pares empatan; una sola posición con un jugador; jugador inactivo
    que vuelve; jugador nuevo a mitad de temporada; semana con estadísticas incompletas antes y
    después de los 7 días; recotización repetida sin acumular; semana sin partidos; métrica no
    calculable fuera de la comparación; jugador cuyo equipo no es ninguno de los del partido; redondeo
    `HALF_UP` en 0,005; determinismo con la entrada desordenada.
- **Servicio** (Mockito): conflictos del candado (409 de los dos motivos), un solo valor de activa por
  corrida, orden de las semanas, corte ante falla, `AHORA` sin tomar la activa cuando el candado
  falla, `PROXIMA_SEMANA` sin candado, opción ignorada, cache invalidation.
- **Integración** (H2, `@ActiveProfiles("test")`): repositories (únicos, activa única, pesos),
  atomicidad por semana (una falla a la mitad deja 0 filas), cambio de activa con `AHORA` atómico,
  caché y la medición de volumen (D15).
- **End to end** (`e2e/`, MockMvc): lecturas públicas sin credencial, 404 distinguibles, 400 de
  fechas, ranking y su ruta, y **401, 403 y 409** de cada endpoint de administrador.
- **Fixtures**: un `PlayerMatchStatsBuilder` de test arma las estadísticas; el H2 de los IT parte
  con las estrategias A y B sembradas (el `StrategySeeder` corre en todos los perfiles).

**Frontend** (Vitest + RTL + MSW, solo en `frontend/tests/`): utilidades, componentes, hooks,
pantallas y flujos, con casos felices y de borde, nombres descriptivos en español y estructura
setup-execute-verify. Todos los archivos son nuevos, salvo lo indicado en D25.

## D27. Riesgos conocidos

| # | Riesgo | Efecto | Mitigación |
|---|---|---|---|
| R1 | **Fórmula aditiva**: jugadores pegados al piso (D8) | Parte del plantel pierde variación y la cotización pierde información | Simulación para calibrar; el equipo confirma (D28.1) |
| R2 | **Volumen** y `IDENTITY` sin lotes (D15) | Una temporada entera puede tardar más de 5 minutos | `QuoteVolumeBenchmarkIT`; plan B de `SEQUENCE` con aval |
| R3 | **006 no integrada** | No hay estadísticas para consumir | Puerta de implementación (D16) |
| R4 | **Caché sin TTL** | Ranking viejo tras una sincronización manual a deshoras | P-4 de D18; el recálculo del lunes lo invalida |
| R5 | **Cada recálculo toma `SyncLock`** | Una sincronización durante un recálculo recibe 409 | Acordar con la 006 (P-2) |
| R6 | **Pares de las cinco ligas juntas** | Un jugador de una liga más difícil queda relativo a las demás | El modelo recibe el grupo por parámetro; el equipo puede acotarlo (D28) |
| R7 | **Un jugador que no jugó** queda al tope de "menos es mejor" (faltas, tarjetas) | Premia a los suplentes que no entraron | Consecuencia de las reglas propuestas (spec, Edge Cases); el equipo la revisa |
| R8 | **`AHORA` en una sola transacción** con muchas semanas pendientes | Una transacción grande en la primera ejecución | Caso raro (D14); la medición de D15 lo cubre |
| R9 | **Ruta pública `TEMPORAL`** | Si se protege el catálogo, las consultas dejarían de ser públicas | Señalado (D22) |
| R10 | **Simulación por página** | Cada página recalcula todo | Meta de 30 s para la primera (SC-010); caché de una entrada si hiciera falta (D19) |
| R11 | **Dos altas simultáneas con el mismo nombre en distinta capitalización** pasan el chequeo del catálogo | Una duplicada con el nombre casi igual | Solo `ADMIN`; el índice único atrapa el caso exacto |

## D28. Puntos a confirmar con el equipo

Ninguno impide planificar. **Los puntos 1 a 5 se resolvieron en `/speckit-clarify` el 2026-10-15** (ver
Clarifications del spec): fórmula aditiva confirmada, la base incluye las recotizadas, el score no se
expone, los pares son de las cinco ligas juntas y el "sí" de la 004 lo dio Valentin.

1. **La fórmula aditiva** (D8): ¿se confirma con factor 10 y mínimo 10,00, sabiendo el riesgo del
   piso? ¿O se prefiere la proporcional (cambio de spec)?
2. **El valor base y las recotizaciones** (D9): ¿"el valor vigente al empezar esa semana" incluye
   una recotizada de la semana anterior hecha durante esta (lo que hace este plan) o solo lo que
   regía a las 00:00 del lunes?
3. **El score en las respuestas** (D20): el pedido lo lista y el spec (FR-006) lo prohíbe. Este plan
   no lo expone.
4. **Pares de las cinco ligas juntas** o de cada liga (D6).
5. **El "sí" de la 004** (D25): quién lo da y cuándo, para registrarlo en Clarifications.
6. **Juego de pesos por defecto de B** (D13): igual al de mediocampista.
7. **Interpretación de las métricas no calculables** con varios partidos en la semana (D5).
8. **Plan B de `SEQUENCE`** (D15): solo si la medición lo exige.
9. **Puntos de coordinación P-1 a P-6 con la 006** (D18).
