# Modelo de datos: Catálogo de jugadores

## League

Enum de dominio persistido como texto.

| Valor | Liga visible |
|---|---|
| `PREMIER` | Premier League (Inglaterra) |
| `BUNDESLIGA` | Bundesliga (Alemania) |
| `LA_LIGA` | La Liga (España) |
| `SERIE_A` | Serie A (Italia) |
| `LIGUE_1` | Ligue 1 (Francia) |

## Position

Enum de dominio persistido como texto: `GOALKEEPER`, `DEFENDER`, `MIDFIELDER` y `FORWARD`.

## Team / TeamSQL

| Campo | Tipo | Reglas |
|---|---|---|
| `id` | `Long` | Identificador interno único; asignado por persistencia. |
| `name` | `String` | Requerido; identifica al club dentro del dataset. |
| `league` | `League` | Requerida; uno y solo un valor, almacenado con `EnumType.STRING`. |

Relación: un equipo puede tener muchos jugadores. La tabla `teams` se declara explícitamente.

## Player / PlayerSQL

| Campo | Tipo | Reglas |
|---|---|---|
| `id` | `Long` | Identificador interno único y estable mientras persista el catálogo. |
| `externalId` | `String` | Requerido y único en `players`; clave de idempotencia del dataset. No es un contrato de búsqueda. |
| `name` | `String` | Requerido, no vacío. |
| `position` | `Position` | Requerida; solo los cuatro valores permitidos; se almacena como texto. |
| `team` | `Team` | Requerido; `PlayerSQL` referencia a `TeamSQL` mediante `@ManyToOne(fetch = LAZY)`. |
| `league` | `League` | Valor de dominio derivado/expuesto desde `team.league`; debe coincidir siempre con la liga del equipo. |

La tabla `players` se declara explícitamente. El modelo puro valida que haya equipo, nombre y posición válidos y que su liga sea consistente con la del equipo mediante excepciones de dominio propias.

## Criterios de consulta

`PlayerFilter` es un valor de modelo para consultas, con `league`, `team` y `position` opcionales. Todos los valores presentes se aplican con AND. No contiene paginación ni tipos HTTP.

## Página de catálogo

`PlayerPage` es un valor de modelo que encapsula el resultado de la consulta:

| Campo | Tipo | Regla |
|---|---|---|
| `content` | `List<Player>` | Solo los jugadores de la página actual. |
| `page` | `int` | Base cero. |
| `size` | `int` | Tamaño solicitado válido. |
| `totalElements` | `long` | Total tras aplicar filtros. |
| `totalPages` | `int` | Total de páginas para tamaño solicitado. |
| `hasPrevious` | `boolean` | `true` si hay página anterior. |
| `hasNext` | `boolean` | `true` si hay página siguiente. |

Una página fuera de rango o una consulta válida sin coincidencias conserva los metadatos y tiene `content` vacío.

## Transiciones

No hay estados de negocio mutables en Entrega 1. El único ciclo de datos es: leer registro JSON → validar modelo → resolver/crear `Team` → persistir `Player`; una carga posterior reconoce `externalId` y no genera duplicados.
