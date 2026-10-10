# Modelo de datos del frontend: Pizarra táctica

**Rama**: `004-frontend-pizarra-tactica` | **Fecha**: 2026-10-07 | **Plan**: [plan.md](plan.md)

El frontend no tiene persistencia propia. Este documento fija los tipos de TypeScript de la API
(copiados de los contratos, sin campos inventados), los tipos propios de la interfaz, la forma
de los filtros en la dirección y el mapeo entre zonas de la cancha y posiciones.

Ningún tipo de este documento tiene precio, valor, cotización, porcentaje, puntaje, orden,
plantel ni portfolio (RF-002).

## 1. Tipos de la API (`src/types/api.ts`)

Fuente: [`auth-api.yaml`](../001-auth-usuarios/contracts/auth-api.yaml) y
[`players-api.yaml`](../002-catalogo-jugadores/contracts/players-api.yaml). Los enums se respetan
tal cual el backend.

### Enums

| Tipo | Valores | Contrato |
|---|---|---|
| `Position` | `GOALKEEPER`, `DEFENDER`, `MIDFIELDER`, `FORWARD` | players-api `Position` |
| `League` | `PREMIER`, `BUNDESLIGA`, `LA_LIGA`, `SERIE_A`, `LIGUE_1` | players-api `League` |
| `Role` | `USER`, `ADMIN` | auth-api `Role` |

Cada enum se declara como arreglo `as const` más su tipo unión (`POSITIONS`, `LEAGUES`,
`ROLES`), así `utils/filters.ts` puede validar valores de la dirección contra la misma lista.

### Catálogo (002)

| Tipo | Campos | Notas |
|---|---|---|
| `Player` (`PlayerResponse`) | `id: number`, `name: string`, `position: Position`, `team: string`, `league: League` | Todos obligatorios. |
| `PlayerPage` (`PlayerPageResponse`) | `content: Player[]`, `page: number`, `size: number`, `totalElements: number`, `totalPages: number`, `hasPrevious: boolean`, `hasNext: boolean` | `page` base 0. |
| `PlayerQuery` (parámetros de `GET /players`) | `league?: League`, `team?: string`, `position?: Position`, `page?: number`, `size?: number` | `page >= 0`, `1 <= size <= 50`. |

### Autenticación (001)

| Tipo | Campos | Uso |
|---|---|---|
| `RegisterRequest` | `username`, `email`, `password` | `POST /auth/register` |
| `RegisterResponse` | `id`, `username`, `email`, `role: Role`, `balance: number`, `apiKey` | La `apiKey` vive solo en el estado local de `RegisterPage`. |
| `LoginRequest` | `username`, `password` | `POST /auth/login` |
| `LoginResponse` | `token`, `tokenType: 'Bearer'`, `expiresAt: string` (ISO 8601) | Origen de la `Session`. |
| `Profile` (`ProfileResponse`) | `id`, `username`, `email`, `role: Role`, `balance: number` | `GET /auth/me`; vestuario y barra superior. |
| `ChangePasswordRequest` | `currentPassword`, `newPassword` | `PUT /auth/me/password` (204). |
| `ApiKeyResponse` | `apiKey` | `POST /auth/me/api-key`; vive solo en el estado local de `LockerRoomPage`. |

### Error común

| Tipo | Campos |
|---|---|
| `ApiError` | `timestamp: string`, `status: number`, `error: string`, `message: string`, `path: string`, `violations?: Violation[]` |
| `Violation` | `field: string`, `message: string`, `rejectedValue?: string` |

`httpClient` lanza una clase `ApiRequestError` que envuelve un `ApiError`. Ante error de red o
cuerpo no JSON, el `ApiError` es sintético con `status: 0` (ver research R-14).

**Reparto de errores en formularios** (RF-009): cada `violation.field` se muestra junto al campo
de igual nombre; `message` sin `violations` (por ejemplo los 409 del registro, el 401 del login
o "la contraseña actual es incorrecta") va como nota general del formulario. Para los 409 del
registro, si el mensaje menciona "usuario" se asocia también a `username` y si menciona "correo"
a `email`, porque el contrato no trae `violations` en ese caso.

## 2. Tipos propios del front (`src/types/board.ts`)

### Session

| Campo | Tipo | Regla |
|---|---|---|
| `token` | `string` | Nunca se loguea ni va en la URL. |
| `expiresAt` | `string` (ISO 8601) | Si `expiresAt <= ahora` al leerla, la sesión se descarta. |

Se guarda en `sessionStorage` bajo una única clave (`futbolmarket.session`) como JSON. El
`Profile` no se guarda: se vuelve a pedir con `GET /auth/me` al recargar.

**Estados de la sesión** (`SessionContext`):

```text
           login OK                     GET /auth/me OK
anónima ─────────────▶ autenticando ─────────────────────▶ autenticada
   ▲                        │ 401 / vencida                   │
   │                        ▼                                 │ cerrar sesión ──▶ anónima (motivo: logout)
   └──────────────── anónima (motivo: expired) ◀──────────────┘ 401 con token / vencimiento
```

El motivo (`'logout' | 'expired' | null`) lo usa `RequireSession` para elegir el aviso del
login.

### BoardFilters

| Campo | Tipo | Valor ausente |
|---|---|---|
| `position` | `Position \| null` | `null` = "todos" |
| `league` | `League \| null` | `null` = "todas" |
| `team` | `string \| null` | `null` = "todos" |
| `page` | `number` (1 en adelante) | `1` |

### PitchZone

`'GOAL' | 'DEFENSE' | 'MIDFIELD' | 'ATTACK'`. "Todos" no es una zona: es `null`.

### BoardSheet (lo que expone `usePlayers`)

| Campo | Origen |
|---|---|
| `players: Player[]` | `PlayerPage.content` |
| `sheet: number` | `PlayerPage.page + 1` |
| `totalSheets: number` | `PlayerPage.totalPages` |
| `hasPrevious`, `hasNext` | iguales al `PlayerPage` |
| `isEmptyCombination` | `totalElements === 0` |
| `isOutOfRange` | `totalElements > 0 && content.length === 0` |

### Estado asíncrono (`useAsync`)

`{ status: 'idle' | 'loading' | 'success' | 'error', data?: T, error?: ApiError, retry(): void }`.
`'loading'` muestra "el DT está pensando..." (RF-034); `'error'` muestra la `ChalkNote` con
"reintentar" (RF-035).

## 3. Filtros en la dirección

Forma (clarificación 4 y RF-018):

```text
/pizarra?position=DEFENDER&league=LA_LIGA&team=Real%20Madrid&page=2
```

| Parámetro | Valor válido | Si es inválido | Hacia `GET /players` |
|---|---|---|---|
| `position` | un valor de `Position` | se ignora | `position` igual |
| `league` | un valor de `League` | se ignora | `league` igual |
| `team` | texto no vacío tras recortar | se ignora | `team` igual |
| `page` | entero `>= 1` | se ignora (= 1) | `page - 1` |
| (fijo) | — | — | `size=12` |

Reglas de escritura (`useBoardFilters`):

- Los filtros sin valor no aparecen en la dirección; `page=1` tampoco.
- Cambiar `position`, `league` o `team` quita `page` (primera hoja, RF-019).
- Cambiar `league` quita `team` si ese equipo no pertenece a la nueva liga (según el catálogo).
- `clear()` deja `/pizarra` sin parámetros.
- Se escribe con `setSearchParams(..., { replace: false })` para que "atrás" del navegador
  recorra los cambios de filtro.

**Descripción de la combinación vacía** (`utils/filters.ts`, RF-020): arma la frase a partir de
los filtros activos, por ejemplo "no hay arqueros de ese equipo en la pizarra", "no hay
defensores de La Liga en la pizarra" o "no hay jugadores de ese equipo en la pizarra".

## 4. Mapeo zona de la cancha ↔ posición (`src/utils/positions.ts`)

| Zona (`PitchZone`) | Etiqueta de la zona | `Position` | Etiqueta singular | Plural (notas) | Color del post-it |
|---|---|---|---|---|---|
| `GOAL` | arco | `GOALKEEPER` | Arquero | arqueros | amarillo (`--yellow`) |
| `DEFENSE` | defensa | `DEFENDER` | Defensor | defensores | celeste (`--blue`) |
| `MIDFIELD` | mediocampo | `MIDFIELDER` | Mediocampista | mediocampistas | verde (`--green`) |
| `ATTACK` | delantera | `FORWARD` | Delantero | delanteros | rosa (`--pink`) |
| `null` | todos | — (sin filtro) | — | jugadores | — |

Funciones: `zoneToPosition(zone)`, `positionToZone(position)`, `positionLabel(position)`,
`positionPlural(position)`. El color se aplica con una clase CSS por posición, no desde `utils/`.

### Etiquetas de liga y rol

| `League` | Etiqueta |
|---|---|
| `PREMIER` | Premier League |
| `BUNDESLIGA` | Bundesliga |
| `LA_LIGA` | La Liga |
| `SERIE_A` | Serie A |
| `LIGUE_1` | Ligue 1 |

| `Role` | Etiqueta |
|---|---|
| `USER` | Usuario |
| `ADMIN` | Administrador |

## 5. Derivados puros (`src/utils/`)

| Función | Entrada | Salida | Reglas |
|---|---|---|---|
| `normalize(text)` | `string` | `string` | recorta, minúsculas, sin acentos |
| `searchPlayers(players, query)` | `Player[]`, `string` | `Player[]` | `[]` si la consulta normalizada tiene menos de 2 caracteres; "contiene" sobre el nombre normalizado; máximo 8; respeta el orden de entrada |
| `deriveTeams(players, league?)` | `Player[]`, `League \| null` | `string[]` | sin repetidos, acotado por liga si hay, ordenado con `localeCompare(…, 'es')` |
| `teamBelongsToLeague(players, team, league)` | | `boolean` | para quitar `team` al cambiar de liga |
| `parseBoardFilters(params)` / `toSearchParams(filters)` | `URLSearchParams` ↔ `BoardFilters` | | tabla de la sección 3 |
| `describeEmptyCombination(filters)` | `BoardFilters` | `string` | sección 3 |
| `formatBalance(balance)` | `number` | `string` | dos decimales con formato `es-AR` |
