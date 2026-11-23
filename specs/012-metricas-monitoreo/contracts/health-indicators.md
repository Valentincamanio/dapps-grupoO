# Contrato: indicadores de salud propios

**Feature**: `012-metricas-monitoreo` | **Plan**: [../plan.md](../plan.md) |
**Research**: D3, D8 | **Modelo**: [../data-model.md](../data-model.md#4-indicadores-de-salud-propios)

Dos componentes nuevos de `/actuator/health`: `footballData` y `sync`. Los dos:

- devuelven solo `UP` o `UNKNOWN`; nunca `DOWN` ni `OUT_OF_SERVICE` (FR-042);
- responden HTTP 200 en los dos estados (el mapeo por defecto solo da 503 a `DOWN` y
  `OUT_OF_SERVICE`);
- no hacen ninguna llamada a Football-Data ni a WhoScored (FR-043);
- muestran `details` solo a un `ADMIN`; un `USER` ve solo `status`; un visitante recibe 401
  ([actuator-access.md](./actuator-access.md)).

## `GET /actuator/health/footballData`

Sin token configurado (perfil `test`, o `FOOTBALL_DATA_TOKEN` vacío):

```json
{ "status": "UNKNOWN", "details": { "tokenConfigured": false } }
```

Con token:

```json
{ "status": "UP", "details": { "tokenConfigured": true } }
```

Invariante: el cuerpo nunca contiene el valor del token ni una parte de él (FR-040, FR-051).

## `GET /actuator/health/sync`

Claves de fuente: `football_data`, `whoscored`. `catchUpAfter` es
`futbolmarket.sync.catch-up-after` como duración ISO-8601 (`PT168H` = 7 días).
`lastSuccessfulFullSync` es el `finishedAt` de la última completa exitosa, en ISO-8601 UTC.

Nunca sincronizada (perfil `test`, base vacía):

```json
{
  "status": "UNKNOWN",
  "details": {
    "catchUpAfter": "PT168H",
    "football_data": { "synchronized": false },
    "whoscored": { "synchronized": false }
  }
}
```

Las dos al día:

```json
{
  "status": "UP",
  "details": {
    "catchUpAfter": "PT168H",
    "football_data": { "synchronized": true, "lastSuccessfulFullSync": "2026-11-17T07:12:45Z", "stale": false },
    "whoscored": { "synchronized": true, "lastSuccessfulFullSync": "2026-11-17T09:40:02Z", "stale": false }
  }
}
```

Una vencida:

```json
{
  "status": "UNKNOWN",
  "details": {
    "catchUpAfter": "PT168H",
    "football_data": { "synchronized": true, "lastSuccessfulFullSync": "2026-11-01T07:12:45Z", "stale": true },
    "whoscored": { "synchronized": true, "lastSuccessfulFullSync": "2026-11-17T09:40:02Z", "stale": false }
  }
}
```

Reglas:

| Por fuente | Condición (`now` = reloj de la app) | Detalle |
|------------|-------------------------------------|---------|
| `NEVER` | no hay última completa exitosa | `synchronized: false` |
| `UP_TO_DATE` | `now - finishedAt <= catchUpAfter` | `stale: false` |
| `STALE` | `now - finishedAt > catchUpAfter` | `stale: true` |

Borde: con antigüedad exactamente igual a `catchUpAfter` la fuente está al día; un nanosegundo más,
vencida. Es la misma regla que `CatchUpPolicy.decide` usa para la puesta al día al arrancar.

Estado del componente: `UP` solo si las dos fuentes son `UP_TO_DATE`; si no, `UNKNOWN`.

## Efecto en el agregado

`GET /actuator/health` (público) sigue respondiendo `{"status":"UP"}` con 200 aunque los dos
propios estén en `UNKNOWN`: el agregador por defecto (`DOWN, OUT_OF_SERVICE, UP, UNKNOWN`) toma
`UP` antes que `UNKNOWN` (SC-010).
