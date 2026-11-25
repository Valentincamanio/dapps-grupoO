# Medición del ranking: caché anterior, sin caché y caché nuevo

**Feature**: `013-ranking-metricas-grafana` | **Requisitos**: FR-010, SC-001 y SC-002 |
**Procedimiento**: [quickstart §1](./quickstart.md#1-medición-del-ranking-fr-010-sc-001-y-sc-002) |
**Script**: [`monitoring/measure-ranking.ps1`](../../monitoring/measure-ranking.ps1)

## Condiciones

| Dato | Valor |
|---|---|
| Fecha | 2026-11-25 (A a las 01:37 y 01:38; B a las 02:27; C a las 02:29, 03:41 y 03:42) |
| Equipo | AMD FX-8320E (4 núcleos, 8 hilos), 16 GB de RAM, Windows 10 Pro |
| Java | OpenJDK 21.0.12.1 (Temurin) |
| Perfil y base | `local`, la misma base H2 en las tres variantes |
| Arranque | `./gradlew bootRun --args="--futbolmarket.quotes.recalculation.on-startup=false"` (más `--spring.cache.type=none` en la variante B) |
| Consulta | `GET /players/ranking?page=0&size=20`, siempre la misma |
| Consultas medidas (N) | 1000 |
| Calentamiento | 20 consultas que no se cuentan |

- **Cliente**: el tiempo de cada consulta medido con un `Stopwatch` alrededor de un único
  `HttpClient` de .NET. El p95 es el valor de la posición `ceil(0,95 × N)` de los tiempos ordenados.
- **Servidor**: la diferencia de `http_server_requests_seconds{uri="/players/ranking"}` en
  `/actuator/prometheus` antes y después de las consultas medidas. El promedio es Δsum / Δcount, y
  el p95 sale de los buckets con la interpolación lineal de `histogram_quantile`, así que es más
  grueso que el del cliente.
- **Aciertos**: la diferencia de `cache_gets_total{cache="quotes-ranking"}`. La variante A no los
  tiene: el `ConcurrentMapCacheManager` no publica métricas.

## Resultados

Cada fila es el promedio de las corridas de esa variante: dos de A, una de B y tres de C.

| Variante | Corridas | Promedio cliente (ms) | p95 cliente (ms) | Promedio servidor (ms) | p95 servidor (ms) | Índice de aciertos |
|---|---|---|---|---|---|---|
| A. Caché anterior (`ConcurrentMapCacheManager`) | 2 | 9,673 | 11,895 | 2,896 | 4,120 | n/d |
| B. Sin caché (`spring.cache.type=none`) | 1 | 15,757 | 22,273 | 8,275 | 13,540 | n/d |
| C. Caché nuevo (Caffeine) | 3 | 10,156 | 12,977 | 3,388 | 4,713 | 100,00 % |

El detalle de cada corrida:

| Variante | Corrida | Promedio cliente (ms) | p95 cliente (ms) | Máximo cliente (ms) | Promedio servidor (ms) | p95 servidor (ms) | Aciertos / fallos |
|---|---|---|---|---|---|---|---|
| A | 1 (01:37) | 10,050 | 12,488 | 86,818 | 3,147 | 4,988 | n/d |
| A | 2 (01:38) | 9,296 | 11,302 | 36,346 | 2,645 | 3,251 | n/d |
| B | 1 (02:27) | 15,757 | 22,273 | 51,703 | 8,275 | 13,540 | n/d |
| C | 1 (02:29) | 10,433 | 13,546 | 41,177 | 3,440 | 5,170 | 1000 / 0 |
| C | 2 (03:41) | 10,663 | 14,044 | 49,242 | 3,794 | 5,524 | 1000 / 0 |
| C | 3 (03:42) | 9,372 | 11,340 | 35,992 | 2,930 | 3,446 | 1000 / 0 |

Las dos corridas de A son seguidas, con el mismo backend levantado; lo mismo las corridas 2 y 3 de
C. En C no hay ningún fallo dentro de las consultas medidas: el único fallo de la hoja ocurre en el
calentamiento, que llena el caché.

## Análisis

**Caché nuevo contra caché anterior (C contra A)**:

| | A (2 corridas) | C (3 corridas) | Diferencia de los promedios |
|---|---|---|---|
| Cliente | 9,673 ms (de 9,296 a 10,050) | 10,156 ms (de 9,372 a 10,663) | +0,483 ms (+5,0 %) |
| Servidor | 2,896 ms (de 2,645 a 3,147) | 3,388 ms (de 2,930 a 3,794) | +0,492 ms (+17,0 %) |

Entre corridas de una misma variante, el promedio del servidor varía hasta 0,502 ms en A y hasta
0,864 ms en C, y los rangos de A y C se superponen, en el cliente y en el servidor: la mejor corrida
de C (2,930 ms en el servidor) es más rápida que la peor de A (3,147 ms). La diferencia de 0,49 ms
está dentro del ruido de la medición: **el caché nuevo no es más lento que el anterior más allá del
ruido**.

**Caché nuevo contra sin caché (C contra B)**, tiempo medio del servidor:

| Sin caché (B) | Caché nuevo (C) | Factor B / C | Ahorro absoluto B − C |
|---|---|---|---|
| 8,275 ms | 3,388 ms | 2,44 | 4,887 ms por consulta (59 % del tiempo de B) |

En el cliente, el factor es 1,55 y el ahorro, 5,601 ms por consulta.

**El costo fijo de cada pedido**: en C todas las consultas medidas fueron aciertos y aun así el
servidor tarda unos 3,4 ms por pedido. Ese es el piso de un pedido que no va a la base: la cadena
de filtros de seguridad, la auditoría de web services, el registro de la métrica HTTP y la
serialización a JSON de las 20 filas. El caché no toca ese costo: lo que elimina es la consulta del
ranking a la base, que es la diferencia B − C. Por eso el factor B / C depende de cuánto cueste esa
consulta y no del caché. Con la base H2 local cuesta unos 5 ms; con una base remota, donde cada
consulta suma la ida y vuelta por la red, o con más jugadores cotizados, la consulta costaría más y
el factor sería mayor, con el mismo costo fijo por pedido.

**SC-002**: con N = 1000 y una sola combinación de parámetros, el índice de aciertos es del 100 %
en las tres corridas de C (≥ 95 %). Las consultas repetidas no van a la base.

## Conclusión

- **SC-001** (versión del 2026-11-25, sin umbral contra "sin caché"): **se cumple**.
  - Las consultas repetidas no van a la base: 100 % de aciertos (SC-002).
  - El tiempo medio del servidor baja de 8,275 ms sin caché a 3,388 ms con el caché nuevo: factor
    2,44 y ahorro de 4,887 ms por consulta.
  - El caché nuevo no es más lento que el anterior más allá del ruido: +0,49 ms de diferencia entre
    promedios, con rangos superpuestos.
- **SC-002**: **se cumple**, con el 100 % de aciertos en 1000 consultas repetidas.

El resumen está en la sección "Métricas y monitoreo" del [README](../../README.md).
