# Contrato externo: páginas de WhoScored

**Rama**: `feature/scraping-whoscored` | **Fecha**: 2026-10-13 | **Plan**: [../plan.md](../plan.md)

Este es el contrato que consume `adapter/whoscored/`. WhoScored no tiene una API pública: se leen
tres bloques que vienen dentro del HTML. Los datos los verificó el equipo el 2026-10-13 con la
temporada 2026/27: Lucas con `curl` y con Java, y después en el navegador. Los ejemplos son
reales y están recortados. Lo que no figura acá no se usa.

Los términos de uso de WhoScored no permiten el scraping. Es un requisito del TP, así que se pide
poco volumen y los datos no se muestran a los usuarios (supuestos del spec).

## Acceso

| Elemento | Valor |
|---|---|
| URL base | `https://www.whoscored.com` (propiedad `futbolmarket.whoscored.base-url`) |
| Protocolo | HTTPS. El cliente del JDK negocia HTTP/2. Se siguen las redirecciones. |
| `User-Agent` | `Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0 Safari/537.36` |
| `Accept` | `text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8` |
| `Accept-Language` | `en-US,en;q=0.9` |
| `Accept-Encoding` | No se manda: la respuesta llega sin comprimir ([research.md](../research.md) D3). |
| Credencial | Ninguna. No se mandan cookies. |
| Ritmo | Una página a la vez y al menos 4 s entre la respuesta anterior y el pedido siguiente (FR-042). |
| Timeouts | 10 s de conexión y 30 s de lectura, cuerpo incluido. |
| Formato | HTML en UTF-8 con los bloques descriptos abajo. |

## Ligas

`WhoScoredTournament` traduce entre la liga y sus ids. La región y el torneo no cambian entre
temporadas. El `seasonId` y el `stageId` sí cambian y no se usan: la temporada se filtra por las
fechas de la de Football-Data.

| `League` | Región | Torneo | Página de la liga | `seasonId` 2026/27 | `stageId` 2026/27 |
|---|---|---|---|---|---|
| `PREMIER` | 252 | 2 | `/regions/252/tournaments/2/england-premier-league` | 11141 | 25544 |
| `LA_LIGA` | 206 | 4 | `/regions/206/tournaments/4/spain-laliga` | 11213 | 25662 |
| `BUNDESLIGA` | 81 | 3 | `/regions/81/tournaments/3/germany-bundesliga` | 11217 | 25666 |
| `SERIE_A` | 108 | 5 | `/regions/108/tournaments/5/italy-serie-a` | 11126 | 25518 |
| `LIGUE_1` | 74 | 22 | `/regions/74/tournaments/22/france-ligue-1` | 11150 | 25554 |

## Páginas que se consumen

| Para qué | Ruta (el *slug* final es opcional) | Bloque | Tamaño | Cuándo se pide |
|---|---|---|---|---|
| Equipos de la fecha en curso | Página de la liga (tabla de arriba) | JSON `tournamentfixtures` | unos 47 KB de JSON | Solo si la liga tiene pendientes y algún equipo sin cruzar ([research.md](../research.md) D11) |
| Temporada de un equipo | `/teams/{id}/fixtures` | Array JS `fixtureMatches` | unos 106 KB | Una vez por corrida y equipo, mientras haga falta descubrir ids |
| Estadísticas de un partido | `/matches/{id}/live` | JSON `matchCentreData` | 1,0 a 1,3 MB | Una vez por partido pendiente con id |
| Fecha de nacimiento y nombre completo de un jugador | `/players/{id}/show` | Datos del jugador (`info-label`) | unos 134 KB | Solo para un jugador que queda sin cruzar después de los niveles de nombre, o que tiene un candidato en otro equipo por nombre y edad (FR-019a), una vez por corrida (FR-019d) |

De la página de jugador solo se leen la fecha de nacimiento y el nombre completo, y solo para
esos jugadores: sus estadísticas se cargan por JavaScript, y pedirla para todos serían 2.649
páginas.
Tampoco se usa el feed `statisticsfeed`, que exige un header propio del sitio.

### Página de la liga: bloque `tournamentfixtures`

**Cómo se extrae**: se busca el `<script type="application/json" data-hypernova-key="tournamentfixtures">`,
se quita el `<!--` del principio y el `-->` del final, y se lee el JSON.

La misma clave aparece antes en el `<div data-hypernova-key="tournamentfixtures">` que el servidor
ya dibujó con esos datos (verificado el 2026-10-15). Por eso se toma la primera aparición que está
dentro de una etiqueta `<script>`, no la primera de la página.

```html
<script type="application/json" data-hypernova-key="tournamentfixtures" data-hypernova-id="8c4e..."><!--{"createdAt":"2026-10-13T23:41:33Z","tournaments":[{"tournamentId":2,"stageId":25544,"regionId":252,"seasonName":"2026/2027","seasonId":11141,"matches":[{"stageId":25544,"id":1983609,"status":1,"startTime":"2026-10-17T12:30:00","homeTeamId":31,"homeTeamName":"Everton","homeRedCards":0,"awayTeamId":15,"awayTeamName":"Chelsea","awayRedCards":0,"startTimeUtc":"2026-10-17T11:30:00Z","homeScore":null,"awayScore":null,"bets":{...}}, ...]}], ...}--></script>
```

| Campo | Uso | Destino en el modelo |
|---|---|---|
| `tournaments[].tournamentId` | Sí | Se elige el torneo de la liga. |
| `tournaments[].matches[].homeTeamId` y `awayTeamId` | Sí | `ReportedTeam.whoscoredId` (como texto) |
| `tournaments[].matches[].homeTeamName` y `awayTeamName` | Sí | `ReportedTeam.name` |
| `id`, `status`, `startTimeUtc`, `seasonId`, `stageId`, `bets`, `incidents` y el resto | No | Se ignoran. |

Trae una sola fecha: 10 partidos con 20 equipos en la Premier, LaLiga y la Serie A, y 9 con 18 en
la Bundesliga y la Ligue 1. Un equipo que no juega esa fecha aparece después como rival en las
páginas de fixtures.

### Página de fixtures: array `fixtureMatches`

**Cómo se extrae**: se busca `fixtureMatches:` y se recorta el array balanceando corchetes,
respetando los strings. Es JavaScript, no JSON: usa comillas simples y deja posiciones vacías
(`,,`) en los partidos sin jugar. Se normaliza a JSON (las comillas simples pasan a dobles y cada
posición vacía pasa a `null`) y se lee.

```text
require.config.params['args'] = {
    teamId: 19,
    fixtureMatches: [[1954193,1,'11-01-26','12:00',20,'Derby',0,19,'Leeds',0,'1 : 3','1 : 0',1,0,'FT','2025/2026','FA Cup','2',26,252,10935,24887,'FAC','gb-eng','gb-eng',0,0,1,'England','England','England','1','3']
,[1983549,1,'22-08-26','15:00',31,'Everton',0,162,'Crystal Palace',0,'2 : 0','1 : 0',1,1,'FT','2026/2027','Premier League','1',2,252,11141,25544,'EPL','gb-eng','gb-eng',0,1,0,'England','England','England','2','0']
,[1983609,4,'17-10-26','12:30',31,'Everton',0,15,'Chelsea',0,'vs',,0,0,,'2026/2027','Premier League','-1',2,252,11141,25544,'EPL','gb-eng','gb-eng',0,1,0,'England','England','England',,]
]
```

| Posición | Contenido | Uso | Destino |
|---|---|---|---|
| 0 | Id del partido | Sí | `ReportedFixture.whoscoredId` |
| 2 | Fecha `dd-mm-yy`, en la zona del sitio | Sí | `ReportedFixture.date` |
| 3 | Hora, en la zona del sitio | No | La hora es la de Londres para todas las ligas, también desde Argentina: la Ligue 1 de las 20:45 de París figura a las 19:45. Por eso se compara por día con un día de margen. |
| 4 y 5 | Id y nombre del local | Sí | `homeTeamWhoscoredId` y `homeTeamName` |
| 6 y 9 | Rojas del local y del visitante | No | — |
| 7 y 8 | Id y nombre del visitante | Sí | `awayTeamWhoscoredId` y `awayTeamName` |
| 10 y 11 | Resultado y resultado del primer tiempo (`vs` y vacío si no se jugó; `*` si se definió por penales) | No | — |
| 14 | Estado: `FT`, `AET` o `PEN` si terminó; vacío si no | Sí | `ReportedFixture.finished` |
| 15 y 16 | Temporada y nombre del torneo | No | — |
| 18 | `tournamentId` | Sí | Se descartan las filas de otro torneo (FR-003). |
| 19, 20 y 21 | `regionId`, `seasonId` y `stageId` | No | — |
| 1, 12, 13, 17 y 22 a 32 | Otros datos | No | Se ignoran. |

Una página trae la temporada entera del equipo, también los partidos futuros, más copas,
torneos europeos y filas de la temporada anterior: la de Everton tenía 42 partidos, la de Leeds
45 y la del PSG 52. Las 20 páginas de la Premier dieron los ids de los 60 partidos terminados al
2026-10-13.

### Página de un partido: objeto `matchCentreData`

**Cómo se extrae**: se busca `matchCentreData:`.

- Si sigue `null`, el partido todavía no tiene datos. **No es un rechazo**: se omite con
  `NOT_FINISHED_IN_SOURCE` y queda pendiente.
- Si sigue `{`, se recorta el objeto balanceando llaves y respetando los strings y sus escapes, y
  se lee con Jackson. El bloque es JSON válido.

```text
require.config.params["args"] = {
            matchId:1983600,
            matchCentreData: {"playerIdNameDictionary":{...}, ... ,"timeoutInSeconds":0},
            matchCentreEventTypeJson: {"shotSixYardBox":0, ...
```

Partido sin jugar (Everton-Chelsea, 1983609):

```text
            matchCentreData: null,
            matchCentreEventTypeJson: {"shotSixYardBox":0,"shotPenaltyArea":1, ...
```

**Campos que se usan**:

| Campo | Uso | Destino |
|---|---|---|
| `statusCode` | Sí. 6 es terminado (`elapsed` `FT`). | `ReportedMatchStats.finished` |
| `expandedMaxMinute` | Sí | `ReportedMatchStats.endMinute` |
| `home.teamId` y `away.teamId` | Sí | `homeTeamWhoscoredId` y `awayTeamWhoscoredId` |
| `home.players[]` y `away.players[]` | Sí | Una `ReportedAppearance` por jugador, con su `Side` |
| `players[].playerId`, `name` y `age` | Sí | `whoscoredId`, `name` y `age` |
| `players[].isFirstEleven` | Sí. Falta en los suplentes. | `starter` |
| `players[].subbedInExpandedMinute` | Sí | `enteredAt` |
| `players[].subbedOutExpandedMinute` | Sí. Falta en un expulsado. | `leftAt` |
| `players[].isManOfTheMatch` | Sí | `manOfTheMatch` |
| `players[].stats` | Sí: las 11 claves de la tabla de métricas y `ratings` | `sourceCounts` y `rating` |
| `events[]` con `type.value` 16 (`Goal`) o `isGoal` | Sí | `ReportedGoal` |
| `events[]` con `type.value` 17 (`Card`) | Sí | `ReportedCard` |
| `events[].playerId`, `expandedMinute`, `isOwnGoal`, `relatedPlayerId`, `qualifiers[].type.displayName` y `cardType.value` | Sí | Ver las reglas de abajo |
| `playerIdNameDictionary`, `startTime`, `score`, `periodEndMinutes`, `maxMinute`, `home.formations`, `incidentEvents`, `players[].position`, `height`, `weight`, el resto de los eventos y de `stats` | No | Se ignoran. `position` es `Sub` en los suplentes, no su posición real. |

Ejemplo real, recortado (Leeds 4-1 Newcastle del 14/09/2026, partido 1983600):

```json
{
  "startTime": "2026-09-14T20:00:00", "score": "4 : 1", "statusCode": 6, "elapsed": "FT",
  "maxMinute": 94, "expandedMaxMinute": 97, "periodEndMinutes": { "1": 47, "2": 94 },
  "home": {
    "teamId": 19, "name": "Leeds",
    "players": [
      { "playerId": 141469, "name": "Dominic Calvert-Lewin", "position": "FW", "age": 29,
        "isFirstEleven": true, "isManOfTheMatch": true, "subbedOutExpandedMinute": 80,
        "stats": { "ratings": { "0": 6, "9": 5.99, "10": 5.99 },
                   "shotsTotal": { "33": 1, "37": 2, "45": 1 },
                   "shotsOnTarget": { "33": 1, "37": 1, "45": 1 }, "dribblesWon": { "45": 1 } } },
      { "playerId": 389064, "name": "Melvin Bard", "position": "Sub", "age": 25,
        "isManOfTheMatch": false, "subbedInExpandedMinute": 75,
        "stats": { "ratings": { "75": 6, "78": 6 }, "passesKey": { "87": 1 } } }
    ]
  },
  "away": { "teamId": 23, "name": "Newcastle", "players": ["..."] },
  "events": [
    { "eventId": 280, "minute": 31, "expandedMinute": 31, "teamId": 23, "playerId": 474264,
      "type": { "value": 16, "displayName": "Goal" }, "isGoal": true, "isOwnGoal": true,
      "qualifiers": [ { "type": { "displayName": "OwnGoal" } } ] },
    { "eventId": 343, "minute": 33, "expandedMinute": 33, "teamId": 19, "playerId": 141469,
      "type": { "value": 16, "displayName": "Goal" }, "isGoal": true,
      "qualifiers": [ { "type": { "value": 29, "displayName": "Assisted" } } ],
      "relatedEventId": 342, "relatedPlayerId": 301441 },
    { "eventId": 436, "minute": 43, "expandedMinute": 43, "teamId": 19, "playerId": 141469,
      "type": { "value": 17, "displayName": "Card" },
      "cardType": { "value": 31, "displayName": "Yellow" } }
  ]
}
```

## Métricas

Cada métrica de `stats` es un mapa minuto → valor que trae solo los minutos con valor distinto
de cero. El total es la suma, y una métrica que falta suma 0.

| Métrica (FR-002) | Clave de `stats` u origen |
|---|---|
| Tiros | `shotsTotal` |
| Tiros al arco | `shotsOnTarget` |
| Pases clave | `passesKey` |
| Pases | `passesTotal` |
| Pases precisos | `passesAccurate` |
| Regates exitosos | `dribblesWon` |
| Quites | `tackleSuccessful`: los quites ganados, que es lo que WhoScored muestra como "Tackles". **No** `tacklesTotal`, que también cuenta las veces que el jugador fue gambeteado (`tackleUnsuccesful`). |
| Intercepciones | `interceptions` |
| Despejes | `clearances` |
| Duelos aéreos ganados | `aerialsWon` |
| Faltas cometidas | `foulsCommited` (así, mal escrito) |
| Rating | El valor del mayor minuto de `ratings`, con sus decimales. Sin `ratings`, nulo. |
| Titular | `isFirstEleven` |
| Figura del partido | `isManOfTheMatch` |
| Minutos, goles, asistencias y tarjetas | Las reglas de abajo |

## Reglas de los eventos

### Minutos

`expandedMinute`, `subbedInExpandedMinute`, `subbedOutExpandedMinute` y `expandedMaxMinute` cuentan
el tiempo transcurrido desde el inicio, con lo agregado en el primer tiempo. En el 1983600 el
primer tiempo termina en el 47:25, el minuto 45 del segundo tiempo es el expandido 48, y el
partido termina en el 94:05, que es el expandido 97.

- Titular: desde 0 hasta su salida o, si no salió, hasta `expandedMaxMinute`.
- Suplente: desde `subbedInExpandedMinute` hasta su salida o el final.
- Expulsado: hasta el `expandedMinute` de su segunda amarilla o su roja. No trae
  `subbedOutExpandedMinute`.
- Un suplente que no entró no tiene fila.

### Goles y asistencias

- **Gol**: `isGoal: true`. Si trae `isOwnGoal: true` o el calificador `OwnGoal`, es en contra: no
  suma gol a su autor ni asistencia a nadie. Su `teamId` es el del equipo del jugador que lo hizo.
- **Asistencia**: un gol que no es en contra y trae el calificador `Assisted` (29). El asistidor es
  `relatedPlayerId`.
  - `IntentionalGoalAssist` no alcanza: el pase de James Justin del 33′ solo tiene `ShotAssist`
    (210), y WhoScored le acredita la asistencia.
  - El evento relacionado (`relatedEventId`) puede ser un pase o **un remate**. En el 1983559,
    Calvert-Lewin convierte el rebote de un tiro de Nmecha (`MissedShots`), y la asistencia es de
    Nmecha.
  - `eventId` es **único por equipo**, no por partido: en el 1983559 el 721 es una tarjeta de
    Brentford y también el tiro de Nmecha. Para resolver `relatedEventId` hay que buscar en el
    mismo equipo.
  - Un remate con `Assisted` que no es gol no suma nada.
- Verificado en cuatro partidos, contra los eventos clave que muestra el propio sitio:

  | Partido | Asistencias según la regla | Según WhoScored |
  |---|---|---|
  | 1983600 Leeds 4-1 Newcastle | Justin y Muharemovic; ninguna en el gol en contra ni en el de Okafor | Igual |
  | 1983552 Nottingham Forest 0-1 Leeds | Ninguna (tiro libre directo) | Igual |
  | 1983559 Leeds 1-1 Brentford | Lewis-Potter (pase) y Nmecha (remate) | Igual |
  | 1984103 Marseille 1-2 PSG | Doué y Gouiri; ninguna en el gol de Marquinhos | Igual |

### Tarjetas

Eventos `Card` (17), por su `cardType.value`:

| `cardType` | Significado | Cuenta como |
|---|---|---|
| 31 `Yellow` | Amarilla. A la primera de una doble amarilla se le suma el calificador `VoidYellowCard` (11114). | Una amarilla, también con `VoidYellowCard` |
| 32 `SecondYellow` | Segunda amarilla (verificado en Weah, 1984103) | Una roja |
| 33 `Red` | Roja directa | Una roja |
| Otro | — | Se ignora y queda un WARN |

Así, la doble amarilla cuenta como una amarilla y una roja (FR-007).

### Página de un jugador: fecha de nacimiento y nombre completo

**Cómo se extrae**: los dos datos vienen en el HTML del servidor, en el bloque de datos del
jugador (verificado el 2026-10-15 en `/players/497764/show`, `/players/508349/show`,
`/players/384887/show`, `/players/407949/show` y `/players/407149/show`). No hay JSON-LD ni un bloque de script con
ellos.

- **La fecha**: se busca la etiqueta `Age: ` de un `info-label` y se lee la fecha del `<i>` que
  la sigue, en formato `dd-MM-yyyy`.
- **El nombre completo**: se busca la etiqueta `Full Name: ` de un `info-label` y se lee el
  texto que sigue hasta el `</div>`, sin los espacios de los bordes, con los espacios seguidos
  reducidos a uno y las entidades HTML decodificadas. El HTML llega con los acentos tal cual
  (`Vítor Machado Ferreira`). No todas las páginas lo traen: la de `Riccardo Ciervo`
  (`/players/407149/show`) pasa de `Name` a `Current Team`.

```html
<div class="col12-lg-6 col12-m-6 col12-s-6 col12-xs-12">
    <span class="info-label">Name: </span>Rafita
</div>
<div class="col12-lg-6 col12-m-6 col12-s-6 col12-xs-12">
    <span class="info-label">Full Name: </span>Rafael Garrido Hierro
</div>
...
<div class="col12-lg-6 col12-m-6 col12-s-6 col12-xs-12">
    <span class="info-label">Age: </span>21 years old (<i>09-11-2004</i>)
</div>
```

| Dato | Uso | Destino |
|---|---|---|
| La fecha del `<i>` que sigue a `Age: ` | Sí | La fecha de nacimiento de los pasos de FR-019d |
| El texto que sigue a `Full Name: ` | Sí | El nombre completo de los pasos 2 y 3 de FR-019d |
| `Name`, `Current Team`, `Shirt Number`, `Height`, `Nationality`, `Positions` y el resto | No | Se ignoran |

- Una página sin ningún `info-label` es un rechazo, como una página de partido sin su bloque.
- Una página con los datos del jugador pero sin la edad, o con una fecha que no se puede leer,
  **no es un rechazo**: el jugador no tiene fecha y no se reintenta.
- Una página sin `Full Name`, o con el texto vacío, **no es un rechazo**: el jugador no tiene
  nombre completo.

## Señales de rechazo

Una página es válida solo si responde 200 **y** trae su bloque. Cualquier otra cosa es un
rechazo:

| Señal | Ejemplo | Efecto |
|---|---|---|
| 403 de unos 5 a 6 KB que llega en menos de 0,3 s | Visto una vez en 50 pedidos; al reintentar, la misma página respondió 200 con datos | Rechazo |
| Otro status distinto de 200 | — | Rechazo |
| Timeout o falla de red | — | Rechazo |
| 200 sin el bloque esperado, o con un bloque que no se puede leer | Una página de "no existe" o de verificación | Rechazo |
| 200 con `matchCentreData: null` | Partido sin jugar o recién terminado | **No es un rechazo**: el partido se omite y queda pendiente |
| 200 de una página de jugador con sus datos pero sin la edad | Jugador sin fecha de nacimiento en WhoScored | **No es un rechazo**: el jugador queda sin fecha |

Ante un rechazo:

1. queda un WARN con la ruta, el status y el tamaño;
2. se espera al menos 30 s y se reintenta una vez (FR-043);
3. si el reintento también falla, es un bloqueo: la corrida se corta entera y no se hace ningún
   otro pedido (FR-044);
4. salvo en la página de un jugador: el jugador queda sin cruzar, queda un WARN y la corrida
   sigue (FR-019d). Una página de jugador rota cortaría cada corrida en el mismo jugador; si
   WhoScored está bloqueando, el corte llega con la página siguiente de un partido. Se vio una
   página de jugador que respondía 502 siempre.

La página de rechazo de los tests (`rejected-403.html`) es un HTML corto sin ninguno de los tres
bloques.

## Volumen

Datos del 2026-10-13:

- unos 330 partidos terminados en las cinco ligas y 48 en una fecha completa;
- 96 equipos;
- unas 32 apariciones por partido;
- 1.527 eventos en el 1983600.

Ponerse al día desde el inicio de la temporada sin snapshot son unas 430 páginas (5 de liga,
hasta 96 de fixtures y unas 330 de partidos) y unos 47 minutos. Una fecha son 48 páginas y unos
5 minutos ([research.md](../research.md) D27).
