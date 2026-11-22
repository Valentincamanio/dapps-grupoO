# Quickstart: métricas y monitoreo

**Feature**: `012-metricas-monitoreo` | **Plan**: [plan.md](./plan.md)

Cómo ver la feature andando con `./gradlew bootRun` y un Prometheus local corrido con su binario,
sin Docker. Los contratos están en [contracts/](./contracts/) y el catálogo de métricas en
[data-model.md](./data-model.md); acá solo se ejecuta y se mira.

## 0. Tests automáticos

```bash
cd backend && ./gradlew build
```

Esperado: BUILD SUCCESSFUL, con los tests nuevos (research D12), todos los existentes sin cambios y
los de arquitectura de la 010.

## 1. Requisitos

- JDK 21 y el repositorio clonado.
- **Prometheus 2.55.0 o posterior** (`http_headers` en `scrape_config` existe desde 2.55.0), binario
  oficial de <https://prometheus.io/download/> descomprimido fuera del repositorio. Se comprueba
  con `prometheus --version`.
- `curl` (o PowerShell con `Invoke-RestMethod`).

## 2. Levantar el backend con un administrador

El administrador se da de alta al arrancar con las variables `FUTBOLMARKET_AUTH_ADMIN_*` (las
mismas del README). Las credenciales son tuyas: no las escribas en ningún archivo del repo.

```bash
cd backend && FUTBOLMARKET_AUTH_ADMIN_USERNAME='<usuario-admin>' FUTBOLMARKET_AUTH_ADMIN_EMAIL='<correo-admin>' FUTBOLMARKET_AUTH_ADMIN_PASSWORD='<contrasena-admin>' ./gradlew bootRun
```

PowerShell:

```powershell
cd backend; $env:FUTBOLMARKET_AUTH_ADMIN_USERNAME = '<usuario-admin>'; $env:FUTBOLMARKET_AUTH_ADMIN_EMAIL = '<correo-admin>'; $env:FUTBOLMARKET_AUTH_ADMIN_PASSWORD = '<contrasena-admin>'; ./gradlew bootRun
```

## 3. Obtener la clave de API del `ADMIN` y guardarla fuera del control de versiones

1. Login para obtener un token de sesión:

   ```bash
   curl -s -X POST localhost:8080/auth/login -H 'Content-Type: application/json' -d '{"username":"<usuario-admin>","password":"<contrasena-admin>"}'
   ```

   Respuesta: `{"token":"<jwt>","tokenType":"Bearer","expiresAt":"..."}`.

2. Emitir la clave de API (se muestra **una sola vez**; emitir otra invalida la anterior):

   ```bash
   curl -s -X POST localhost:8080/auth/me/api-key -H 'Authorization: Bearer <jwt>'
   ```

   Respuesta: `{"apiKey":"<clave>"}`.

3. Guardarla, sola y en una línea, en `monitoring/admin-api-key` (desde la raíz del repo):

   ```bash
   printf '%s' '<clave>' > monitoring/admin-api-key
   ```

4. Verificar que git la ignora (tiene que aparecer la regla de `.gitignore`):

   ```bash
   git check-ignore -v monitoring/admin-api-key
   ```

## 4. Levantar Prometheus

`monitoring/prometheus.yml` (versionado, sin claves) recolecta `localhost:8080/actuator/prometheus`
cada 15 s y manda `X-API-Key` leído de `admin-api-key`, ruta relativa al directorio del propio
`prometheus.yml`. Desde la raíz del repo:

```bash
<ruta-a-prometheus>/prometheus --config.file=monitoring/prometheus.yml --storage.tsdb.path=monitoring/data
```

`monitoring/data/` también está ignorado. Verificado con Prometheus 3.16.0 (T011, 2026-11-21): lanzado
desde la raíz del repo, `files` se resuelve relativo a `monitoring/` y el target queda UP. Si tu versión no resolviera `files` relativo al archivo
de configuración (el target queda DOWN con un error de lectura del archivo), cambiá la entrada por
la ruta absoluta en tu copia local, sin commitearla.

Abrir <http://localhost:9090/targets>: el target `futbol-market` tiene que figurar **UP** dentro del
primer intervalo (SC-001).

## 5. Consultas de verificación (en <http://localhost:9090/graph>)

Generar algo de tráfico antes, por ejemplo `curl -s localhost:8080/players/1` y
`curl -s localhost:8080/players/2` varias veces.

| Consulta | Qué se espera |
|----------|---------------|
| `up{job="futbol-market"}` | `1` |
| `rate(http_server_requests_seconds_count[1m])` | al menos una serie; `uri="/players/{id}"`, nunca `uri="/players/1"` |
| `sum by (uri) (rate(http_server_requests_seconds_count[1m]))` | requests por segundo por ruta |
| `histogram_quantile(0.95, sum by (le, uri, method) (rate(http_server_requests_seconds_bucket[5m])))` | p95 por ruta y método |
| `histogram_quantile(0.99, sum by (le, uri, method) (rate(http_server_requests_seconds_bucket[5m])))` | p99 por ruta y método |
| `count({application!="futbol-market", job="futbol-market", __name__!~"up\|scrape_.*"})` | vacío: todas las series del backend llevan la etiqueta (SC-002). Se excluyen `up` y `scrape_*`, que las agrega Prometheus a cada target y no vienen del backend |
| `jvm_memory_used_bytes{area="heap"}`, `process_cpu_usage`, `hikaricp_connections_active` | métricas técnicas (FR-005) |
| `futbolmarket_market_orders_total` | 6 series (`operation` × `result`), desde el arranque |
| `futbolmarket_auth_logins_total`, `futbolmarket_auth_registrations_total` | 2 y 1 series |
| `futbolmarket_quotes_recalculation_seconds_count` | 9 series (`trigger` × `result`) |
| `futbolmarket_sync_seconds_count` | 12 series (`source` × `type` × `result`) |
| `futbolmarket_users_registered`, `futbolmarket_market_orders_open` | valores actuales de la base |
| `increase(futbolmarket_auth_logins_total{result="failure"}[5m])` | sube después de un login con contraseña incorrecta |

## 6. Verificación manual del acceso

Con `K` = la clave del `ADMIN` y `U` = la clave de un `USER` (registrar uno con
`POST /auth/register`; la respuesta trae su `apiKey`). La matriz completa está en
[contracts/actuator-access.md](./contracts/actuator-access.md).

```bash
curl -s -o /dev/null -w '%{http_code}\n' localhost:8080/actuator/health
```

200 y cuerpo `{"status":"UP"}`, también si se manda `-H "X-API-Key: $K"`.

```bash
curl -s -o /dev/null -w '%{http_code}\n' localhost:8080/actuator/prometheus
```

401 (visitante). Con `-H "X-API-Key: $U"`: 403. Con `-H "X-API-Key: $K"`: 200.

```bash
curl -s -H "X-API-Key: $K" localhost:8080/actuator/health/sync
```

Detalles por fuente ([contracts/health-indicators.md](./contracts/health-indicators.md)); con
`$U`, solo `{"status":"..."}`.

```bash
curl -s -o /dev/null -w '%{http_code}\n' -H "X-API-Key: $U" localhost:8080/actuator/env
```

404 (y 401 sin credencial).

Cambio de nivel de log en caliente:

```bash
curl -s -o /dev/null -w '%{http_code}\n' -X POST -H "X-API-Key: $K" -H 'Content-Type: application/json' -d '{"configuredLevel":"DEBUG"}' localhost:8080/actuator/loggers/ar.edu.unq.desapp.futbolmarket
```

204, y `GET /actuator/loggers/ar.edu.unq.desapp.futbolmarket` muestra `"configuredLevel":"DEBUG"`.
Con `{"configuredLevel":"NOPE"}`: 400 con el JSON de error estándar. Con `$U`: 403. Volver a
`{"configuredLevel":null}` al terminar (igual se pierde al reiniciar).

## 7. Swagger

Abrir <http://localhost:8080/swagger-ui.html>: el selector de arriba muestra **API de negocio** y
**Monitoreo**. En Monitoreo, autorizar con `apiKeyAuth` (la clave del `ADMIN`) y ejecutar
`GET /actuator/metrics`. `GET /v3/api-docs` sigue respondiendo 200.

## 8. Auditoría

Después de los pasos anteriores, `backend/logs/web-service-audit.log` no tiene ninguna línea con
`/actuator` (FR-038).

## 9. Rotación de la clave

Si se emite una clave nueva para el `ADMIN`, Prometheus empieza a recibir 401 y el target pasa a
DOWN. Se actualiza `monitoring/admin-api-key`; si el target no vuelve a UP en el siguiente
scrape, se reinicia Prometheus para que relea el archivo.

Verificado con Prometheus 3.16.0 (T049, 2026-11-22): el target pasó a DOWN con `server returned
HTTP status 401` y volvió a UP en el scrape siguiente a actualizar el archivo, sin reiniciar.
