# Contrato del snapshot de estadísticas

**Rama**: `feature/scraping-whoscored` | **Fecha**: 2026-10-13 | **Plan**: [../plan.md](../plan.md)

El snapshot es el archivo versionado del repositorio con las estadísticas ya obtenidas y los
cruces con WhoScored, ubicados por los identificadores de Football-Data (FR-023). Así una base
nueva no tiene que scrapear la temporada entera. Lo lee y lo escribe `StatsSnapshotRepository`
con `StatsSnapshotLineMapper` ([research.md](../research.md) D19 a D21).

## Archivo

| Elemento | Valor |
|---|---|
| Ruta en el repositorio | `backend/snapshot/whoscored-stats.jsonl` |
| Propiedad | `futbolmarket.whoscored.snapshot.path`, relativa al directorio de trabajo (`backend/` con `bootRun` y con la tarea de exportación) |
| Formato | JSON Lines: un objeto JSON por línea, sin líneas en blanco |
| Codificación | UTF-8 sin BOM |
| Fin de línea | LF, también en Windows: `.gitattributes` suma `backend/snapshot/*.jsonl text eol=lf` |
| Compresión | Ninguna: texto plano, para que el diff muestre solo las entradas nuevas (FR-030) |
| Tamaño esperado | Unos 1,7 KB por partido: unos 600 KB al 2026-10-13 y unos 3 MB con una temporada completa |

## Contenido

El snapshot contiene **solo** estadísticas, ids de WhoScored y los ids de Football-Data que los
ubican (FR-029). Nunca usuarios, contraseñas, claves de API, tokens, operaciones, nombres ni
ningún otro dato.

### Línea 1: encabezado

```json
{"format":"futbolmarket-whoscored-snapshot","version":1,"playerColumns":["player","whoscored","starter","minutesPlayed","goals","assists","shots","shotsOnTarget","keyPasses","passes","accuratePasses","successfulDribbles","tackles","interceptions","clearances","aerialsWon","foulsCommitted","yellowCards","redCards","rating","manOfTheMatch"]}
```

La carga exige este encabezado exacto. Un `format` distinto, otra `version` u otras columnas
hacen que el archivo se ignore entero (`UNREADABLE`).

### Entradas

Cada línea siguiente es una entrada de uno de tres tipos. Las claves van siempre en este orden y
sin espacios.

| `type` | Claves | Significado |
|---|---|---|
| `team` | `type`, `league`, `team`, `whoscored` | El equipo de Football-Data `team` es el equipo `whoscored` de WhoScored. |
| `match` | `type`, `league`, `match`, `whoscored` | El partido de Football-Data `match` es el partido `whoscored` de WhoScored. Incluye partidos todavía sin estadísticas, también futuros: así una base nueva no pide páginas de fixtures. |
| `stats` | `type`, `league`, `match`, `players` | El partido `match` ya tiene estadísticas: una fila por jugador cruzado que entró a la cancha. Puede traer `players` vacío: es un partido procesado en el que no se pudo cruzar a nadie. |

- `league` es el nombre del enum `League` (`PREMIER`, `BUNDESLIGA`, `LA_LIGA`, `SERIE_A` o
  `LIGUE_1`). Para un equipo es su liga actual en el catálogo; para un partido, la de su
  temporada.
- Todos los ids son strings, como en el catálogo.

**Fila de un jugador** (`players[]`): un array con las 21 columnas del encabezado, en ese orden.

| # | Columna | Tipo | Origen |
|---|---|---|---|
| 0 | `player` | string | Id de Football-Data del jugador |
| 1 | `whoscored` | string o `null` | Id de WhoScored del jugador. Es `null` si el jugador no está cruzado: sus estadísticas se cargaron del snapshot con un `CONFLICTING_CROSS` y el id quedó en otro jugador |
| 2 | `starter` | `0` o `1` | Titular |
| 3 | `minutesPlayed` | entero ≥ 0 | Minutos jugados |
| 4 a 18 | `goals` … `redCards` | enteros ≥ 0 | Las 15 métricas de `Metric`, en su orden |
| 19 | `rating` | número con dos decimales (`7.65`, `6.00`) o `null` | Rating |
| 20 | `manOfTheMatch` | `0` o `1` | Figura del partido |

### Ejemplo

Recortado. Los ids de Football-Data son ilustrativos, y los de WhoScored y las métricas son los
del 1983600.

```json
{"format":"futbolmarket-whoscored-snapshot","version":1,"playerColumns":["player","whoscored","starter","minutesPlayed","goals","assists","shots","shotsOnTarget","keyPasses","passes","accuratePasses","successfulDribbles","tackles","interceptions","clearances","aerialsWon","foulsCommitted","yellowCards","redCards","rating","manOfTheMatch"]}
{"type":"team","league":"PREMIER","team":"67","whoscored":"23"}
{"type":"team","league":"PREMIER","team":"341","whoscored":"19"}
{"type":"match","league":"PREMIER","match":"537800","whoscored":"1983600"}
{"type":"match","league":"PREMIER","match":"537809","whoscored":"1983609"}
{"type":"stats","league":"PREMIER","match":"537800","players":[["3754","141469",1,80,2,0,4,3,0,14,10,1,0,0,1,4,1,1,0,8.95,1],["99812","389064",0,22,0,0,0,0,1,6,4,0,1,1,0,1,1,0,0,6.27,0]]}
```

## Orden canónico

El orden hace que el archivo sea determinístico (FR-030):

1. el encabezado;
2. las entradas `team`, después las `match` y después las `stats`;
3. dentro de cada tipo, por `league` en el orden del enum (`PREMIER`, `BUNDESLIGA`, `LA_LIGA`,
   `SERIE_A`, `LIGUE_1`);
4. dentro de cada liga, por id de Football-Data: primero por largo y después alfabético, que en
   ids numéricos es el orden numérico;
5. dentro de `players`, por id de Football-Data del jugador, con el mismo criterio.

La clave de una entrada es su `type` más su id de Football-Data (`team` o `match`). No puede
haber dos entradas con la misma clave.

## Cómo se genera

```bash
./gradlew exportStatsSnapshot
```

Se corre desde `backend/`, con la aplicación detenida (la base H2 en archivo admite un solo
proceso que escriba). La tarea corre `StatsSnapshotExport`, que levanta Spring con el perfil
`local`, sin servidor web y sin sincronizaciones, y llama a `StatsSnapshotService.export()`:

1. **Lee el snapshot vigente.** Si existe y no se puede leer, no lo pisa: termina con un error.
2. **Arma las entradas desde la base**:
   - `team` por cada equipo con id de WhoScored;
   - `match` por cada partido con id de WhoScored;
   - `stats` por cada partido con estadísticas, con sus filas.
3. **Las combina** (FR-031):
   - cada entrada vigente se conserva tal cual, también si la base no la tiene o la tiene
     distinta;
   - se suman las entradas de la base cuya clave no está.
4. **Escribe** en orden canónico a un archivo temporal en la misma carpeta y lo mueve sobre el
   vigente.

Consecuencias:

- Exportar dos veces sin cambios en la base da el mismo archivo, byte a byte.
- Después de procesar una fecha, el diff son solo las líneas de esos partidos y los cruces
  nuevos.
- Dos desarrolladores que agregan la misma fecha generan las mismas líneas. Si agregan fechas
  distintas, un conflicto de merge se resuelve conservando las dos partes y exportando otra vez.

## Cómo se carga

Lo carga `StatsSyncService` al principio de cada sincronización de WhoScored, antes de pedir
cualquier página y sin depender de WhoScored (FR-024 y FR-025):

1. **Se lee entero.**
   - Si falta, el estado es `MISSING`.
   - Si una línea no se puede leer, si el encabezado no es el esperado o si hay una clave
     repetida, el estado es `UNREADABLE` y el archivo se ignora entero.

   En los dos casos queda un WARN y se scrapea todo lo pendiente (FR-027). El estado va al
   informe.
2. **Se aplican solo las entradas de las ligas pedidas**, con estas reglas:

   | Entrada | Situación | Efecto |
   |---|---|---|
   | `team` | El equipo no está en el catálogo | Se saltea: `TEAM_NOT_IN_CATALOG` |
   | | Está en el catálogo pero no juega la temporada en curso | Se ignora sin contarlo |
   | | Está sin cruzar y el id está libre | Se cruza |
   | | Ya tiene ese id | Nada |
   | | Tiene otro id, o el id es de otro equipo | Gana la base: `CONFLICTING_CROSS` |
   | `match` | Igual que `team` | `MATCH_NOT_IN_CATALOG` o `CONFLICTING_CROSS`; uno de otra temporada se ignora sin contarlo |
   | `stats` | El partido no está en el catálogo | Se saltea: `MATCH_NOT_IN_CATALOG` |
   | | Ya tiene estadísticas | Se ignora sin contarlo: conserva las suyas |
   | | No está terminado | Se saltea: `MATCH_NOT_FINISHED`. Se carga en la primera sincronización posterior a que figure terminado. |
   | | Es de una temporada que ya no está en curso | Se ignora sin contarlo |
   | | Está pendiente | Se carga en una transacción y cuenta como "procesado desde el snapshot" |
   | Fila de `stats` | El jugador no está en el catálogo | Se saltea la fila: `PLAYER_NOT_IN_CATALOG`. El resto del partido se carga (FR-026). |
   | | El id de WhoScored del jugador choca con otro cruce | Gana la base: `CONFLICTING_CROSS`. Sus estadísticas se cargan igual. |
   | | `whoscored` es `null` | Sus estadísticas se cargan; no se cruza a nadie |

   "En el catálogo" se decide buscando por id de Football-Data, de cualquier temporada, los
   equipos y partidos que nombra el snapshot de la liga (`CatalogReferences`, research D20).

3. Las entradas salteadas se cuentan por motivo en el informe de cada liga (FR-048).

## Versiones

`version` es `1`. Un cambio que no sea compatible (otra columna, otro tipo de entrada) sube la
versión, y la feature que lo introduzca define cómo se convierte el archivo vigente. Mientras
tanto, la carga ignora entero un archivo de otra versión y la exportación se niega a pisarlo,
igual que con uno que no se puede leer.
