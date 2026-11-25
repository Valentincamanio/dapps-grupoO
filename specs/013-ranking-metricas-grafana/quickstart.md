# Quickstart: validación de la 013

**Feature**: `013-ranking-metricas-grafana` | **Plan**: [plan.md](./plan.md) | **Contratos**: [metrics.md](./contracts/metrics.md), [grafana-dashboard.md](./contracts/grafana-dashboard.md)

Esta guía tiene escenarios para comprobar a mano, de punta a punta, que la feature funciona, y el
procedimiento de medición del ranking (FR-010). Los tests automáticos los cubre `./gradlew build`.
Los comandos son de PowerShell, desde la raíz del repo salvo que se indique otra cosa.

## Prerrequisitos

- Java 21 y el backend levantando con el perfil local (`cd backend; ./gradlew bootRun`).
- La clave de API de un `ADMIN` en `monitoring/admin-api-key`, en una línea. Cómo emitirla está en
  el [quickstart de la 012](../012-metricas-monitoreo/quickstart.md). Hace falta para leer
  `/actuator/prometheus`.
- Para la US3: Prometheus ≥ 2.55.0 y Grafana OSS ≥ 11.0 (zip de Windows descomprimido, por ejemplo
  en `C:\grafana`).

Para leer las métricas como `ADMIN`:

```powershell
$key = (Get-Content monitoring/admin-api-key -Raw).Trim()
```

```powershell
(Invoke-WebRequest http://localhost:8080/actuator/prometheus -Headers @{ 'X-API-Key' = $key }).Content -split "`n" | Select-String 'futbolmarket_market_(trades|tokens_traded|credits_traded)_total|cache_gets_total'
```

---

## 1. Medición del ranking (FR-010, SC-001 y SC-002)

### 1.1 El script

`monitoring/measure-ranking.ps1` (research R7):

| Parámetro | Por defecto | Qué es |
|---|---|---|
| `-BaseUrl` | `http://localhost:8080` | El backend |
| `-Query` | `page=0&size=20` | Los parámetros del ranking. Son los mismos en todas las consultas. |
| `-Requests` | `1000` | Las consultas medidas (N) |
| `-Warmup` | `20` | Las consultas previas que no se cuentan |
| `-Variant` | (obligatorio) | La etiqueta del resultado: `cache-anterior`, `sin-cache` o `cache-nuevo` |
| `-ApiKeyFile` | `monitoring/admin-api-key` | Si existe, suma las métricas del servidor |

Lo que imprime, en una tabla:
- **cliente**: N, promedio, p95 y máximo en ms;
- **servidor** (si hay clave): Δcount, el promedio (Δsum / Δcount) y el p95 interpolado de los
  buckets de `http_server_requests_seconds{uri="/players/ranking"}`;
- **caché** (si existen las series): Δhit, Δmiss y el índice de aciertos de `quotes-ranking`.

### 1.2 Procedimiento (las tres variantes, en este orden)

Las tres variantes usan la misma máquina, la misma base del perfil local, la misma `-Query` y el
mismo `-Requests`. El recálculo al arrancar se apaga para que no vacíe el caché ni cargue la base
durante la medición. Antes de medir, se espera a que el backend termine de arrancar
(`GET /actuator/health` → `UP`) y se cierran otros procesos pesados.

**A. Caché anterior: ANTES de cambiar `CacheConfig`** (con el código de la rama sin la US1).

```powershell
cd backend; ./gradlew bootRun --args="--futbolmarket.quotes.recalculation.on-startup=false"
```

En otra terminal, desde la raíz:

```powershell
powershell -ExecutionPolicy Bypass -File monitoring/measure-ranking.ps1 -Variant cache-anterior
```

**B. Sin caché** (código nuevo):

```powershell
cd backend; ./gradlew bootRun --args="--futbolmarket.quotes.recalculation.on-startup=false --spring.cache.type=none"
```

```powershell
powershell -ExecutionPolicy Bypass -File monitoring/measure-ranking.ps1 -Variant sin-cache
```

**C. Caché nuevo** (código nuevo):

```powershell
cd backend; ./gradlew bootRun --args="--futbolmarket.quotes.recalculation.on-startup=false"
```

```powershell
powershell -ExecutionPolicy Bypass -File monitoring/measure-ranking.ps1 -Variant cache-nuevo
```

Opcional, con Prometheus corriendo: el p95 del servidor para la ventana de la medición se ve en
http://localhost:9090/graph con
`histogram_quantile(0.95, sum by (le) (rate(http_server_requests_seconds_bucket{uri="/players/ranking"}[5m])))`.

### 1.3 Resultado esperado y dónde se anota

- En `specs/013-ranking-metricas-grafana/medicion-ranking.md` van la fecha, la máquina, N, la
  consulta y una tabla con el promedio y el p95 (cliente y servidor) de cada variante, más el
  índice de aciertos de C. El README enlaza ese documento y copia el resumen.
- **SC-001**: el promedio de C es ≤ 1/5 del de B, y C no es más lento que A más allá del ruido de
  la medición. En `medicion-ranking.md` se anotan los dos promedios (A y C) y su diferencia.
- **SC-002**: en C, con N = 1000 y una sola combinación de parámetros, el índice de aciertos es
  ≥ 95% (se espera 1 fallo y el resto aciertos).
- La variante A no tiene métricas de caché: el `ConcurrentMapCacheManager` no las publica. Se anota
  "n/d".

---

## 2. US1: caché del ranking

1. **Acierto y fallo**: con el backend recién levantado, se pide dos veces
   `GET http://localhost:8080/players/ranking?page=0&size=10`.
   - Las dos respuestas son idénticas.
   - `cache_gets_total{cache="quotes-ranking",result="miss"}` vale 1 y `result="hit"` vale 1.
2. **Invalidación**: se cambia la estrategia activa como `ADMIN`, con los endpoints de estrategias
   de la 007, y se vuelve a pedir la misma hoja.
   - La respuesta informa la estrategia nueva.
   - `miss` suma 1.
   - Lo mismo vale después de un recálculo manual.
3. **Pedido inválido**: `GET /players/ranking?page=-1` y `GET /players/ranking?size=500`
   responden el mismo 400 de hoy, y `cache_gets_total` no cambia.
4. **Propiedad inválida**: arrancar con `--futbolmarket.cache.ranking.maximum-size=0` falla con un
   mensaje en español que nombra la propiedad (FR-003).
5. **Cotización actual**: dos `GET /players/{id}/quotes/current` del mismo jugador. El segundo
   suma en `cache_gets_total{cache="quotes-current",result="hit"}`. Un jugador inexistente sigue
   dando 404 y no ocupa entrada.

Lo que no se puede ver a mano (el tope, el vencimiento y la consulta en vuelo) lo cubren
`QuoteCacheManagerTest`, `GenerationGuardedCaffeineCacheTest` y `QuoteCacheConcurrencyIT`.

---

## 3. US2: métricas del mercado

1. **En cero al arrancar**: con el backend recién levantado, las 6 series de
   [metrics.md §1](./contracts/metrics.md) existen con `0.0`.
2. **Cruce con la orden inicial**: un usuario con saldo compra 3 tokens de un jugador cotizado que
   no tiene otras ventas abiertas (`POST` de compra de la 008).
   - `futbolmarket_market_trades_total{counterparty="initial_order"}` vale 1.
   - `tokens_traded` vale 3.
   - `credits_traded` es igual al `total` de la operación que devuelve la respuesta o
     `GET` de transacciones.
3. **Compra pendiente**: una compra de un jugador sin ventas disponibles queda `PENDING`. Las
   seis series no cambian. Si después otro usuario vende y se cruza, suma 1 en
   `counterparty="user"`.
4. **Rechazo y cancelación**: una compra con saldo insuficiente (409) y la cancelación de una orden
   pendiente no cambian las seis series. Las de `futbolmarket_market_orders_total` de la 012 sí
   suman, igual que hoy.
5. **Acceso**: `/actuator/prometheus` sin credencial da 401, y con un `USER` da 403 (sin cambios).

---

## 4. US3: Grafana

1. **Credencial local (opcional)**: se copia el ejemplo y se completa la contraseña.
   `monitoring/grafana/admin-credentials` no se versiona. Si no se crea, se entra con
   `admin`/`admin`.

   ```powershell
   Copy-Item monitoring/grafana/admin-credentials.example monitoring/grafana/admin-credentials
   ```

2. **Prometheus**, como en la 012:

   ```powershell
   <ruta-a-prometheus>/prometheus --config.file=monitoring/prometheus.yml --storage.tsdb.path=monitoring/data
   ```

3. **Grafana**, con un solo comando desde la raíz:

   ```powershell
   powershell -ExecutionPolicy Bypass -File monitoring/grafana/start-grafana.ps1 -GrafanaHome C:\grafana
   ```

4. **Verificación** en http://localhost:3000:
   - En *Connections → Data sources* figura "Prometheus futbol-market" (no editable), y *Test* da
     OK.
   - En *Dashboards → futbol-market* está el dashboard **futbol-market**, sin haber configurado
     nada a mano (US3, escenario 1).
   - En el log de Grafana (`monitoring/grafana/data/log/`) no hay errores de provisioning. Si se
     queja por carpetas de provisioning que no existen, ver research R15.
5. **Datos**:
   - Se hacen unas compras que se cruzan (§3.2) y se consulta el ranking varias veces (§2.1).
   - Después de uno o dos scrapes (15 s), los paneles de operaciones, tokens, créditos, p95 del
     ranking y aciertos del caché muestran valores.
   - Con unos `GET /players/999999` (404), la tasa de 4xx sube (escenarios 2 a 4).
6. **Reinicio**: se reinicia el backend y los paneles siguen sin valores negativos (escenario 5).
7. **Sin Prometheus**: se detiene Prometheus y se recarga el dashboard. Abre con "No data", sin
   errores. Al levantarlo de nuevo, se llena solo.
8. **Sin credenciales versionadas** (SC-007): esta búsqueda no tiene que mostrar valores:

   ```powershell
   git grep -nIiE "(password|api[-_]?key|token)\s*[:=]\s*\S" -- monitoring
   ```

   Solo aparecen nombres de archivos o claves vacías.

---

## 5. Cierre (definición de terminado)

```powershell
cd backend; ./gradlew build
```

- Debe dar BUILD SUCCESSFUL, con la suite completa, la de arquitectura de la 010 y los tests
  nuevos.
- El único test existente modificado es la aserción de tipo de `CacheConfigTest` (autorizada, ver
  plan).
- `./gradlew bootRun` levanta con el perfil local.
- Swagger no cambia, porque no hay endpoints nuevos.
