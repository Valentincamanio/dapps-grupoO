# Contrato: provisioning y dashboard de Grafana

**Feature**: `013-ranking-metricas-grafana` | **Data model**: [../data-model.md](../data-model.md) §3 | **Research**: R12 a R17

Grafana OSS **≥ 11.0**, con el binario zip de Windows y sin Docker. Todo lo que sigue está
versionado en `monitoring/grafana/`, salvo lo marcado como local.

## 1. Lanzador: `monitoring/grafana/start-grafana.ps1`

Se usa desde la raíz del repositorio:

```powershell
powershell -ExecutionPolicy Bypass -File monitoring/grafana/start-grafana.ps1 -GrafanaHome C:\grafana
```

| Entrada | Obligatoria | Efecto |
|---|---|---|
| `-GrafanaHome <ruta>` o `$env:GRAFANA_HOME` | sí, una de las dos | La carpeta del zip descomprimido. Si falta, o no existe `bin\grafana.exe`, el script termina con un mensaje en español. |
| `monitoring/grafana/admin-credentials` (local) | no | Si existe, `admin_user` va a `GF_SECURITY_ADMIN_USER` y `admin_password` a `GF_SECURITY_ADMIN_PASSWORD`. Las claves vacías no se aplican. La contraseña nunca se imprime. |

Variables que pone, **solo para el proceso de Grafana**:

| Variable | Valor |
|---|---|
| `GF_PATHS_PROVISIONING` | `<repo>\monitoring\grafana\provisioning` |
| `GF_PATHS_DATA` | `<repo>\monitoring\grafana\data` |
| `GF_PATHS_LOGS` | `<repo>\monitoring\grafana\data\log` |
| `GF_PATHS_PLUGINS` | `<repo>\monitoring\grafana\data\plugins` |
| `FUTBOLMARKET_GRAFANA_DASHBOARDS` | `<repo>\monitoring\grafana\dashboards` |

Después ejecuta `<GrafanaHome>\bin\grafana.exe server --homepath <GrafanaHome>`. Grafana queda en
http://localhost:3000.

## 2. Credencial del administrador

- Lo versionado es `admin-credentials.example`, con `admin_user=admin` y `admin_password=` vacío,
  más un comentario que dice que se copia a `admin-credentials`.
- Lo local es `admin-credentials`, ignorado por git.
- Sin el archivo local, se entra con `admin` / `admin` y Grafana pide cambiarla.
- El valor se aplica solo cuando Grafana crea su base: en el primer arranque, o después de borrar
  `monitoring/grafana/data/`.

## 3. Data source: `provisioning/datasources/prometheus.yaml`

```yaml
apiVersion: 1
datasources:
  - name: Prometheus futbol-market
    type: prometheus
    uid: futbol-market-prometheus      # fijo: el dashboard lo referencia
    access: proxy
    url: http://localhost:9090
    isDefault: true
    editable: false
    jsonData:
      timeInterval: 15s                # = scrape_interval de monitoring/prometheus.yml
```

No lleva credencial: el Prometheus local no la pide. La clave del `ADMIN` del backend la usa
Prometheus (`monitoring/admin-api-key`), no Grafana.

## 4. Provider: `provisioning/dashboards/futbol-market.yaml`

```yaml
apiVersion: 1
providers:
  - name: futbol-market
    type: file
    folder: futbol-market
    disableDeletion: true
    allowUiUpdates: false
    updateIntervalSeconds: 10
    options:
      path: $FUTBOLMARKET_GRAFANA_DASHBOARDS
```

## 5. Dashboard: `dashboards/futbol-market.json`

Encabezado: `uid: "futbol-market"`, `title: "futbol-market"`, `schemaVersion: 39`,
`time: {from: "now-6h", to: "now"}` y `refresh: "30s"`. El formato es el de exportación, sin el
envoltorio de la API.

Todo panel con consultas lleva `"datasource": {"type": "prometheus", "uid": "futbol-market-prometheus"}`.
`$__range` y `$__rate_interval` son variables de Grafana. Con `increase` y `rate`, un reinicio del
backend no produce saltos negativos (FR-027).

### Fila "Negocio" (FR-024)

| Panel | Tipo | PromQL |
|---|---|---|
| Operaciones ejecutadas (en el rango) | stat | `sum(increase(futbolmarket_market_trades_total[$__range]))` |
| Operaciones por período | time series | `sum(increase(futbolmarket_market_trades_total[$__rate_interval]))` (total) y `sum by (counterparty) (increase(futbolmarket_market_trades_total[$__rate_interval]))` |
| Tokens transaccionados (en el rango) | stat | `sum(increase(futbolmarket_market_tokens_traded_total[$__range]))` |
| Tokens por período | time series | lo mismo con `futbolmarket_market_tokens_traded_total`: total y `by (counterparty)` |
| Créditos movidos (en el rango) | stat, 2 decimales | `sum(increase(futbolmarket_market_credits_traded_total[$__range]))` |
| Créditos por período | time series | lo mismo con `futbolmarket_market_credits_traded_total`: total y `by (counterparty)` |
| Órdenes pedidas por operación y resultado | time series | `sum by (operation, result) (increase(futbolmarket_market_orders_total[$__rate_interval]))` |
| Órdenes abiertas | stat | `sum(futbolmarket_market_orders_open)` |

En las series por contraparte, la leyenda traduce `initial_order` → "orden inicial del
superusuario" y `user` → "usuario" (overrides por nombre de serie).

### Fila "Ranking" (FR-025)

| Panel | Tipo | PromQL |
|---|---|---|
| p95 de `/players/ranking` | time series (s) | `histogram_quantile(0.95, sum by (le) (rate(http_server_requests_seconds_bucket{uri="/players/ranking"}[$__rate_interval])))` |
| Respuestas desde el caché | stat y time series (percentunit) | `sum(rate(cache_gets_total{cache="quotes-ranking",result="hit"}[$__rate_interval])) / sum(rate(cache_gets_total{cache="quotes-ranking"}[$__rate_interval]))` |

### Fila "General" (FR-026)

| Panel | Tipo | PromQL |
|---|---|---|
| Pedidos por segundo | time series | `sum(rate(http_server_requests_seconds_count{uri!~"/actuator.*"}[$__rate_interval]))` |
| Tasa de errores 4xx | time series (percentunit) | `sum(rate(http_server_requests_seconds_count{uri!~"/actuator.*",outcome="CLIENT_ERROR"}[$__rate_interval])) / sum(rate(http_server_requests_seconds_count{uri!~"/actuator.*"}[$__rate_interval]))` |
| Tasa de errores 5xx | time series (percentunit) | la misma, con `outcome="SERVER_ERROR"` |

## 6. Lo que verifica `e2e/GrafanaDashboardIT`

1. `futbol-market.json` parsea con `JsonMapper`.
2. Cada nombre de métrica que aparece en un `expr`, con los prefijos `futbolmarket_`,
   `http_server_requests_` y `cache_`, figura en `/actuator/prometheus` (pedido como `ADMIN`
   después de una consulta al ranking).
3. Todo panel con `targets` tiene `datasource.uid` = el `uid` de `prometheus.yaml`.
4. Para SC-007, `admin-credentials.example` deja `admin_password` vacío. El archivo local
   `admin-credentials` no se lee, aunque exista en la máquina.
