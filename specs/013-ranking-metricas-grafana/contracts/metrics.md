# Contrato: series nuevas en `/actuator/prometheus`

**Feature**: `013-ranking-metricas-grafana` | **Data model**: [../data-model.md](../data-model.md) §2

No hay endpoints nuevos (FR-018). Esto es lo que se agrega a la salida de
`GET /actuator/prometheus` de la 012, que sigue siendo **solo para `ADMIN`**, con token de sesión o
`X-API-Key`. Un visitante recibe 401 y un `USER`, 403, igual que hoy (US2, escenario 8). Toda serie
lleva además `application="futbol-market"`.

## 1. Métricas del mercado (US2)

Las seis series existen desde el arranque, con valor `0.0` (FR-017):

```text
# HELP futbolmarket_market_trades_total Operaciones ejecutadas en el mercado: ...
# TYPE futbolmarket_market_trades_total counter
futbolmarket_market_trades_total{application="futbol-market",counterparty="initial_order"} 0.0
futbolmarket_market_trades_total{application="futbol-market",counterparty="user"} 0.0
# TYPE futbolmarket_market_tokens_traded_total counter
futbolmarket_market_tokens_traded_total{application="futbol-market",counterparty="initial_order"} 0.0
futbolmarket_market_tokens_traded_total{application="futbol-market",counterparty="user"} 0.0
# TYPE futbolmarket_market_credits_traded_total counter
futbolmarket_market_credits_traded_total{application="futbol-market",counterparty="initial_order"} 0.0
futbolmarket_market_credits_traded_total{application="futbol-market",counterparty="user"} 0.0
```

| Serie | Etiquetas permitidas | Valores de `counterparty` |
|---|---|---|
| `futbolmarket_market_trades_total` | `application`, `counterparty` | `initial_order`, `user` |
| `futbolmarket_market_tokens_traded_total` | `application`, `counterparty` | `initial_order`, `user` |
| `futbolmarket_market_credits_traded_total` | `application`, `counterparty` | `initial_order`, `user` |

Por cada operación confirmada de un `buy` o un `sell`, en la serie de su contraparte:

| Escenario de la spec | trades | tokens | credits |
|---|---|---|---|
| Compra de 3 tokens que se cruza con la orden inicial (escenario 2) | `+1` en `initial_order` | `+3` en `initial_order` | `+total` en `initial_order` |
| Compra que se cruza contra dos ventas de usuarios (escenario 3) | `+2` en `user` | `+Σ quantity` en `user` | `+Σ total` en `user` |
| Orden que queda pendiente, compra o venta rechazada, falla al grabar o cancelación (escenarios 4 a 7) | sin cambio | sin cambio | sin cambio |
| Venta pendiente que se cruza después (escenario 4) | `+1` cuando la cruza el `buy` | `+quantity` | `+total` |

`credits` es un `double`: conserva los centavos y no redondea a enteros (FR-019, research R11).
Los tests comparan con una tolerancia de 0,005.

Las siete métricas de la 012 siguen con los mismos nombres, etiquetas y cantidad de series
(FR-022).

## 2. Métricas de los cachés (US1 y FR-030)

Existen desde el arranque, para los dos cachés (`cache` = `quotes-ranking` | `quotes-current`):

```text
cache_gets_total{application="futbol-market",cache="quotes-ranking",cache_manager="cacheManager",name="quotes-ranking",result="hit"} 0.0
cache_gets_total{application="futbol-market",cache="quotes-ranking",cache_manager="cacheManager",name="quotes-ranking",result="miss"} 0.0
cache_evictions_total{application="futbol-market",cache="quotes-ranking",cache_manager="cacheManager",name="quotes-ranking"} 0.0
cache_eviction_weight_total{...,cache="quotes-ranking",...} 0.0
cache_puts_total{...,cache="quotes-ranking",...} 0.0
cache_size{...,cache="quotes-ranking",...} 0.0
```

Lo mismo existe con `cache="quotes-current"`.

| Comportamiento | Efecto en las series |
|---|---|
| Primera consulta de una hoja del ranking | `cache_gets_total{cache="quotes-ranking",result="miss"}` `+1` y `cache_size` `+1` |
| Segunda consulta igual, sin cambios en el medio | `result="hit"` `+1` (US1, escenario 1) |
| Pedido inválido al ranking (hoja negativa o tamaño fuera de rango) | sin cambio en `hit` ni `miss` (FR-007) |
| Vaciado por recálculo, recotización o cambio de estrategia | `cache_size` vuelve a 0, y `cache_evictions_total` sin cambio |
| Más combinaciones que el tope | `cache_evictions_total` sube y `cache_size` ≤ tope (SC-004) |
| Vencimiento de seguridad | la entrada no se sirve: la siguiente consulta es un `miss` |
| `GET /players/{id}/quotes/current` | lo mismo, pero en `cache="quotes-current"`. Los 404 no se guardan. |

Índice de aciertos del ranking, en PromQL:

```promql
sum(rate(cache_gets_total{cache="quotes-ranking",result="hit"}[5m]))
  / sum(rate(cache_gets_total{cache="quotes-ranking"}[5m]))
```

## 3. Tiempo de respuesta del ranking (FR-009, sin cambios)

Ya existe desde la 012: `http_server_requests_seconds_bucket|_count|_sum|_max` con
`uri="/players/ranking"`, con histograma de buckets (`management.metrics.distribution.percentiles-histogram`).

```promql
histogram_quantile(0.95, sum by (le) (rate(http_server_requests_seconds_bucket{uri="/players/ranking"}[5m])))
```
