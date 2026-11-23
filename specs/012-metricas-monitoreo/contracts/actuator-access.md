# Contrato: acceso a los endpoints de Actuator

**Feature**: `012-metricas-monitoreo` | **Plan**: [../plan.md](../plan.md) |
**Research**: D3, D4, D11

Base path: `/actuator`. Expuestos por web: `health`, `info`, `metrics`, `prometheus`, `loggers`
(FR-030). Credenciales aceptadas en todo endpoint protegido, con las mismas reglas de rol
(FR-039): `Authorization: Bearer <token de sesión>` o `X-API-Key: <clave de API>`.

## Matriz de status

"Visitante" = sin credencial. Una credencial inválida responde 401 en cualquier fila salvo
`GET /actuator/health`, que no la evalúa.

| Request | Visitante | `USER` | `ADMIN` | Requisito |
|---------|-----------|--------|---------|-----------|
| `GET /actuator/health` | 200, solo estado | 200, solo estado | 200, solo estado | FR-031 (sin cambios) |
| `GET /actuator/health/{componente}` | 401 | 200, solo estado | 200, estado y `details` | FR-032 |
| `GET /actuator/health/{inexistente}` | 401 | 404 | 404 | — |
| `GET /actuator/info` | 401 | 200 | 200 | FR-033 (sin cambios de acceso) |
| `GET /actuator` | 401 | 403 | 200, solo los 5 expuestos | FR-036 |
| `GET /actuator/metrics` | 401 | 403 | 200 | FR-034 |
| `GET /actuator/metrics/{nombre}` | 401 | 403 | 200 (404 si no existe) | FR-034 |
| `GET /actuator/prometheus` | 401 | 403 | 200, `text/plain` de Prometheus | FR-001, FR-034 |
| `GET /actuator/loggers` | 401 | 403 | 200 | FR-034, FR-035 |
| `GET /actuator/loggers/{nombre}` | 401 | 403 | 200 | FR-034, FR-035 |
| `POST /actuator/loggers/{nombre}` nivel válido | 401 | 403, sin cambio | 204, rige desde el próximo mensaje | FR-035 |
| `POST /actuator/loggers/{nombre}` nivel inválido | 401 | 403 | 400 `ApiError`, sin cambio | FR-035, edge case |
| `/actuator/env`, `/beans`, `/configprops`, `/heapdump`, `/threaddump`, `/mappings`, `/shutdown`, cualquier otro no expuesto | 401 | 404 | 404 | FR-030 |

Reglas de la cadena que producen la matriz, en orden (research D4):

1. `dispatcherTypeMatchers(ERROR)` → permitido (sin cambios).
2. `publicEndpoints` → permitido; incluye `GET /actuator/health` exacto (sin cambios). El filtro de
   credenciales no se ejecuta ahí, así que la raíz de salud es anónima para todos.
3. Reglas existentes de negocio (sin cambios).
4. `/actuator` → `ADMIN`.
5. `/actuator/metrics/**` → `ADMIN`.
6. `/actuator/prometheus` → `ADMIN`.
7. `/actuator/loggers/**` → `ADMIN`.
8. `/actuator/health/**`, `/actuator/info` → autenticado.
9. `anyRequest()` → autenticado.

No hay ninguna regla `/actuator/**` genérica.

## Cuerpos de error

401 y 403 salen de la cadena de seguridad, con los entry points JSON existentes y el formato
`ApiError` de `shared/` (`violations` no aparece):

```json
{
  "timestamp": "2026-11-19T15:04:05.123Z",
  "status": 401,
  "error": "Unauthorized",
  "message": "Se requiere una credencial: un token de sesión (Authorization: Bearer) o una clave de API (X-API-Key).",
  "path": "/actuator/prometheus"
}
```

```json
{
  "timestamp": "2026-11-19T15:04:05.123Z",
  "status": 403,
  "error": "Forbidden",
  "message": "No tiene permisos para acceder a este recurso.",
  "path": "/actuator/metrics"
}
```

400 de un nivel de log inválido, desde `GlobalExceptionHandler` (handler nuevo para
`ResponseStatusException` 400, mensaje fijo existente `INVALID_PARAMETERS`; nunca el `reason`
interno de Boot):

```json
{
  "timestamp": "2026-11-19T15:04:05.123Z",
  "status": 400,
  "error": "Bad Request",
  "message": "<INVALID_PARAMETERS de GlobalExceptionHandler>",
  "path": "/actuator/loggers/ar.edu.unq.desapp.futbolmarket"
}
```

404 de un endpoint no expuesto: no hay handler; la respuesta es la de cualquier ruta inexistente
del backend. En ningún caso el cuerpo contiene propiedades, variables de entorno, secretos ni
configuración (FR-030, FR-051).

## Formas de las respuestas exitosas

- `GET /actuator/health` (cualquiera): `{"status":"UP"}`. Nunca `components` ni `details`.
- `GET /actuator/health/db` como `USER`: `{"status":"UP"}`.
- `GET /actuator/health/db` como `ADMIN`: `{"status":"UP","details":{"database":"H2","validationQuery":"isValid()"}}`
  (sin URL de la base ni credenciales).
- Componentes propios: [health-indicators.md](./health-indicators.md).
- `GET /actuator/info`:

  ```json
  {
    "build": {
      "name": "futbol-market",
      "description": "Mercado simulado de valoración de jugadores de fútbol.",
      "version": "0.0.1-SNAPSHOT",
      "artifact": "futbol-market",
      "group": "ar.edu.unq.desapp",
      "time": "2026-11-19T14:00:00.000Z"
    },
    "java": { "version": "21.0.x", "vendor": { "name": "Eclipse Adoptium" }, "runtime": { "...": "..." }, "jvm": { "...": "..." } }
  }
  ```

  Sin `env`, `os`, `process` ni `git`.
- `GET /actuator/prometheus`: texto de exposición de Prometheus; cada serie con
  `application="futbol-market"` (SC-002). Ejemplo de líneas esperables:

  ```text
  http_server_requests_seconds_bucket{application="futbol-market",error="none",exception="none",method="GET",outcome="SUCCESS",status="200",uri="/players/{id}",le="0.1"} 2
  futbolmarket_market_orders_total{application="futbol-market",operation="buy",result="success"} 0.0
  futbolmarket_sync_seconds_count{application="futbol-market",result="in_progress",source="whoscored",type="full"} 0
  futbolmarket_users_registered{application="futbol-market"} 3.0
  ```

- `GET /actuator/metrics/futbolmarket.market.orders` (ADMIN): `name`, `description` en español,
  `measurements` y `availableTags` con `operation` y `result`.
- `GET /actuator/loggers/{nombre}`: `{"configuredLevel": "DEBUG" | null, "effectiveLevel": "..."}`.
- `POST /actuator/loggers/{nombre}` con `Content-Type: application/json` y cuerpo
  `{"configuredLevel":"DEBUG"}` (o `null` para volver al heredado): 204 sin cuerpo.

## Auditoría

Ningún request a `/actuator/**`, de ningún rol ni resultado, deja línea en el logger
`WEB_SERVICE_AUDIT` ni registros en la auditoría del mercado (FR-038): el aspecto de la 011 solo
intercepta `@RestController` de `controller/`.

## Swagger

Grupo `monitoring` (display "Monitoreo") en `/v3/api-docs/monitoring`, con todas las operaciones de
arriba; las protegidas declaran `bearerAuth` o `apiKeyAuth`. Grupo `business` ("API de negocio")
sin `/actuator/**`. `GET /v3/api-docs` sigue respondiendo 200 con los dos esquemas (FR-037).
