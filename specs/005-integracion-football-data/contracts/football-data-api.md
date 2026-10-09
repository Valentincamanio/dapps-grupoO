# Contrato externo: API v4 de Football-Data.org

**Rama**: `feature/api-footballdata` | **Fecha**: 2026-10-07 | **Plan**: [../plan.md](../plan.md)

Este es el contrato que consume `adapter/footballdata/`. Los datos los verificó el equipo el
2026-10-06 contra la API real, con el plan gratis y la temporada 2026/27. Las respuestas de
ejemplo son reales y están recortadas. Lo que no figura acá no se usa.

## Acceso

| Elemento | Valor |
|---|---|
| URL base | `https://api.football-data.org/v4` (propiedad `futbolmarket.football-data.base-url`) |
| Credencial | Header `X-Auth-Token: <token>`, que sale de `FOOTBALL_DATA_TOKEN` ([configuration.md](./configuration.md)) |
| Límite del plan gratis | 10 requests por minuto |
| Formato | JSON en UTF-8. Las fechas vienen como `yyyy-MM-dd` y los instantes en ISO-8601 UTC (`2026-08-21T19:00:00Z`). |

## Competiciones

`FootballDataCompetition` traduce entre la liga y su código:

| Código | Id de la fuente | `League` |
|---|---|---|
| `PL` | 2021 | `PREMIER` |
| `BL1` | 2002 | `BUNDESLIGA` |
| `PD` | 2014 | `LA_LIGA` |
| `SA` | 2019 | `SERIE_A` |
| `FL1` | 2015 | `LIGUE_1` |

## Endpoints que se consumen

Son dos por liga, así que una sincronización completa hace 10 requests y una de una sola liga
hace 2. No se usa `/teams/{id}`, que trae el mismo plantel y llevaría a 96 requests. Tampoco se
consultan la tabla de posiciones ni los goleadores (FR-005).

### `GET /competitions/{code}/teams`

Devuelve la temporada en curso y los equipos con su plantel.

| Campo | Tipo | Uso | Destino en el modelo |
|---|---|---|---|
| `season.id` | número | Sí | `Season.externalId` (como texto) |
| `season.startDate` | fecha | Sí | `Season.startDate` |
| `season.endDate` | fecha | Sí | `Season.endDate` |
| `season.currentMatchday` | número o `null` | Sí | `Season.currentMatchday` |
| `teams[].id` | número | Sí | `TeamSnapshot.externalId` |
| `teams[].name` | texto | Sí | `TeamSnapshot.name`: el nombre oficial exacto (FR-008) |
| `teams[].crest` | texto (URL) | Sí | `TeamSnapshot.crest` |
| `teams[].squad[].id` | número | Sí | `PlayerSnapshot.externalId` |
| `teams[].squad[].name` | texto | Sí | `PlayerSnapshot.name` |
| `teams[].squad[].position` | texto o `null` | Sí | `PlayerSnapshot.position`, traducida (ver Valores) |
| `teams[].squad[].dateOfBirth` | fecha o `null` | Sí | `PlayerSnapshot.dateOfBirth` |
| `teams[].squad[].nationality` | texto o `null` | Sí | `PlayerSnapshot.nationality` |
| `count`, `filters`, `competition`, `area`, `shortName`, `tla`, `address`, `website`, `founded`, `clubColors`, `venue`, `runningCompetitions`, `coach`, `staff`, `lastUpdated` | — | No | Se ignoran. `shortName` no se guarda por FR-008. `coach` llega con todos sus campos en `null`. |

```json
{
  "count": 20,
  "filters": { "season": "2026" },
  "competition": { "id": 2021, "name": "Premier League", "code": "PL", "type": "LEAGUE",
                   "emblem": "https://crests.football-data.org/PL.png" },
  "season": { "id": 2502, "startDate": "2026-08-21", "endDate": "2027-05-30",
              "currentMatchday": 6, "winner": null },
  "teams": [
    {
      "area": { "id": 2072, "name": "England", "code": "ENG", "flag": "..." },
      "id": 64, "name": "Liverpool FC", "shortName": "Liverpool", "tla": "LIV",
      "crest": "https://crests.football-data.org/64.png",
      "address": "...", "website": "...", "founded": 1892, "clubColors": "...", "venue": "Anfield",
      "runningCompetitions": ["..."],
      "coach": { "id": null, "name": null },
      "squad": [
        { "id": 1795, "name": "Alisson Becker", "position": "Goalkeeper",
          "dateOfBirth": "1992-10-02", "nationality": "Brazil" },
        { "id": 7383, "name": "Kostas Tsimikas", "position": "Defence",
          "dateOfBirth": "1996-05-12", "nationality": "Greece" },
        { "id": 1780, "name": "Federico Chiesa", "position": "Offence",
          "dateOfBirth": "1997-10-25", "nationality": "Italy" }
      ],
      "staff": [],
      "lastUpdated": "2022-02-10T19:30:22Z"
    },
    {
      "id": 61, "name": "Chelsea FC", "crest": "https://crests.football-data.org/61.png",
      "squad": [
        { "id": 301113, "name": "Mahdi Nicoll-Jazuli", "position": null,
          "dateOfBirth": "2010-01-06", "nationality": "England" }
      ]
    }
  ]
}
```

### `GET /competitions/{code}/matches`

Devuelve todos los partidos de la temporada en curso: los jugados con su resultado y los
programados con su fecha.

| Campo | Tipo | Uso | Destino en el modelo |
|---|---|---|---|
| `matches[].id` | número | Sí | `MatchSnapshot.externalId` |
| `matches[].season.id` | número | Sí | `MatchSnapshot.seasonExternalId`. Se controla que coincida con la temporada de `/teams`. |
| `matches[].utcDate` | instante | Sí | `MatchSnapshot.utcDate` |
| `matches[].status` | texto | Sí | `MatchSnapshot.status`, traducido (ver Valores) |
| `matches[].matchday` | número o `null` | Sí | `MatchSnapshot.matchday` |
| `matches[].homeTeam.id` / `awayTeam.id` | número | Sí | `homeTeamExternalId` / `awayTeamExternalId` |
| `matches[].score.winner` | texto o `null` | Sí | `MatchSnapshot.winner` |
| `matches[].score.fullTime.home` / `.away` | número o `null` | Sí | `MatchSnapshot.fullTime` |
| `matches[].score.halfTime.home` / `.away` | número o `null` | Sí | `MatchSnapshot.halfTime` |
| `filters`, `resultSet`, `competition`, `stage`, `group`, `lastUpdated`, `homeTeam.name` y demás datos del equipo, `score.duration`, `odds`, `referees` | — | No | Se ignoran. `odds` trae un mensaje de paquete pago. |

```json
{
  "filters": { "season": "2026" },
  "resultSet": { "count": 380, "first": "2026-08-21", "last": "2027-05-30", "played": 50 },
  "competition": { "id": 2021, "name": "Premier League", "code": "PL", "type": "LEAGUE" },
  "matches": [
    {
      "season": { "id": 2502, "startDate": "2026-08-21", "endDate": "2027-05-30",
                  "currentMatchday": 6, "winner": null },
      "id": 560542, "utcDate": "2026-08-21T19:00:00Z", "status": "FINISHED", "matchday": 1,
      "stage": "REGULAR_SEASON", "group": null, "lastUpdated": "2026-10-06T00:20:38Z",
      "homeTeam": { "id": 57, "name": "Arsenal FC", "shortName": "Arsenal", "tla": "ARS",
                    "crest": "https://crests.football-data.org/57.png" },
      "awayTeam": { "id": 1076, "name": "Coventry City FC", "shortName": "Coventry City",
                    "tla": "COV", "crest": "https://crests.football-data.org/1076.png" },
      "score": { "winner": "HOME_TEAM", "duration": "REGULAR",
                 "fullTime": { "home": 3, "away": 0 }, "halfTime": { "home": 2, "away": 0 } },
      "odds": { "msg": "Activate Odds-Package in User-Panel to retrieve odds." },
      "referees": [ { "id": 11620, "name": "Thomas Bramall", "type": "REFEREE",
                      "nationality": "England" } ]
    },
    {
      "id": 560593, "utcDate": "2026-10-10T11:30:00Z", "status": "TIMED", "matchday": 6,
      "homeTeam": { "id": 57, "name": "Arsenal FC" },
      "awayTeam": { "id": 341, "name": "Leeds United FC" },
      "score": { "winner": null, "duration": "REGULAR",
                 "fullTime": { "home": null, "away": null },
                 "halfTime": { "home": null, "away": null } },
      "referees": []
    }
  ]
}
```

## Valores y su traducción

La traducción la hace `FootballDataMapper`. Las decisiones de negocio sobre esos valores las
toma el modelo ([research.md](../research.md) D7).

**Posición** (`squad[].position`). Con los datos reales había 296, 875, 742 y 721 jugadores en
cada una de las cuatro posiciones, y 15 sin posición.

| Fuente | Modelo |
|---|---|
| `Goalkeeper` | `GOALKEEPER` |
| `Defence` | `DEFENDER` |
| `Midfield` | `MIDFIELDER` |
| `Offence` | `FORWARD` |
| `null` o cualquier otro valor | sin posición (`null`). El modelo saltea al jugador nuevo y conserva la posición del existente (FR-012 y FR-013). |

**Estado** (`status`): `SCHEDULED`, `TIMED`, `IN_PLAY`, `PAUSED`, `EXTRA_TIME`,
`PENALTY_SHOOTOUT`, `FINISHED`, `SUSPENDED`, `POSTPONED`, `CANCELLED` y `AWARDED` pasan a
`MatchStatus` con el mismo nombre. Un valor desconocido queda en `null`, y el modelo omite el
partido con `UNKNOWN_STATUS`.

**Ganador** (`score.winner`): `HOME_TEAM`, `AWAY_TEAM` y `DRAW` pasan a `MatchWinner` con el mismo
nombre. `null` queda en `null`.

**Resultado** (`fullTime` y `halfTime`): si `home` y `away` vienen en `null`, el partido no se
jugó y el resultado queda en `null`. `score.duration` (`REGULAR`, `EXTRA_TIME` o
`PENALTY_SHOOTOUT`) no se guarda.

**Identificadores**: los ids son números y se guardan como texto (`String.valueOf`), según
[research.md](../research.md) D17.

## Headers

| Header | Dirección | Uso |
|---|---|---|
| `X-Auth-Token` | Request | La credencial. Nunca se registra. |
| `X-Requests-Available-Minute` | Respuesta | Requests que quedan en el minuto. Alimenta el ritmo preventivo ([research.md](../research.md) D6). |
| `X-RequestCounter-Reset` | Respuesta | Segundos hasta que el contador se renueva. Es la espera ante un 429; si falta o no se puede leer, se esperan 60 s. Si pide más de 120 s, no se espera y la liga queda fallida. |
| `X-API-Version` | Respuesta | `v4`. No se usa. |
| `X-Authenticated-Client` | Respuesta | Identifica la cuenta dueña del token. **No se registra.** |

## Errores

El cuerpo de error es JSON con `message` y `errorCode`. El `message` se registra en WARN junto con
la liga y no va al informe. En el informe va el motivo en español de
[research.md](../research.md) D5.

| Status | Significado en la fuente | Efecto |
|---|---|---|
| 400 | Token inválido (`message`: `Your API token is invalid.`) o filtro inválido | La liga queda fallida. Con un token inválido fallan las cinco ligas con el motivo de la credencial rechazada (HU3, escenario 8). Cualquier otro 400 lleva el motivo genérico; no debería ocurrir, porque no se mandan filtros. |
| 403 | Recurso restringido, plan insuficiente o falta de token | La liga queda fallida. |
| 404 | El recurso no existe | La liga queda fallida. |
| 429 | Se excedió el límite (10 por minuto en el plan gratis) | Se espera `X-RequestCounter-Reset` y se reintenta una vez. Si vuelve a fallar, la liga queda fallida (FR-038). |
| 5xx | Error de la fuente | La liga queda fallida, sin reintento. |
| Sin respuesta en 30 s | — | La liga queda fallida, sin reintento (FR-037). |

Ejemplo real de un 400 con un token inválido (verificado el 2026-10-09):

```json
{"message":"Your API token is invalid.","errorCode":400}
```

Ejemplo real de un 403:

```json
{"message":"The resource you are looking for is restricted and apparently not within your permissions. Please check your subscription.","errorCode":403}
```

## Volumen

Datos del 2026-10-06:

- 96 equipos y 2.649 jugadores en las cinco ligas. 15 no tienen posición, así que se importan
  2.634 (SC-001).
- Unos 1.750 partidos por temporada; 380 en la Premier League (SC-004).
- La respuesta de `/teams` pesa entre 65 y 80 KB por liga, y la de `/matches` de la Premier unos
  370 KB.

## No disponible en el plan gratis

Las alineaciones, los autores de los goles, las tarjetas y los cambios no vienen ni en
`/matches/{id}` ni con los headers `X-Unfold-*`. Tampoco están disponibles los totales por
jugador de `/persons/{id}/matches`. Estos datos van a salir de WhoScored en una feature
posterior.
