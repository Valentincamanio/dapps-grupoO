# Plan de implementación: Frontend "Pizarra táctica" (catálogo de solo lectura)

**Rama**: `004-frontend-pizarra-tactica` | **Fecha**: 2026-10-07 | **Spec**: [spec.md](spec.md)

**Entrada**: especificación de `specs/004-frontend-pizarra-tactica/spec.md` (con la sesión de clarificaciones del 2026-10-07).

## Resumen

Se arranca `frontend/` como SPA en React 18 + Vite + TypeScript strict, con el stack y la
estructura que fija la sección Frontend de la constitución 2.1.0. La app consume solo los
endpoints existentes de `001-auth-usuarios` y `002-catalogo-jugadores` a través de un único
cliente HTTP (`services/httpClient.ts`) detrás del proxy de Vite (`/api` -> `:8080`).

La pantalla principal es la pizarra: post-its de 12 por hoja pedidos a `GET /players` con los
filtros de la dirección (`position`, `league`, `team`, `page`), una cancha SVG que funciona solo
como filtro de posición, pestañas de liga y un desplegable de equipo. El buscador de la barra
superior resuelve por nombre en el cliente sobre el catálogo completo, que se descarga una sola
vez por sesión y de forma diferida (`useCatalog`). La ficha es de solo lectura y el vestuario es
la única ruta privada. No existe ningún artefacto de compra, plantel u órdenes.

Un workflow nuevo (`.github/workflows/frontend-ci.yml`) corre lint, tests y build en cada push y
PR a `main` y `develop` que toque `frontend/**`.

## Contexto técnico

**Lenguaje/Versión**: TypeScript 5.x en modo `strict`, sobre Node 20 LTS (versión fijada por el pedido; ver [research.md](research.md#r-12-versión-de-node)).

**Dependencias principales**: React 18, React DOM 18, React Router 7 (modo librería, `createBrowserRouter`), Vite (con `@vitejs/plugin-react`). Fuentes de Google Fonts (Caveat, Permanent Marker, Patrick Hand). Sin librería de UI.

**Dependencias de desarrollo**: Vitest, jsdom, React Testing Library (`@testing-library/react`, `@testing-library/user-event`, `@testing-library/jest-dom`), MSW 2, ESLint (flat config) con `typescript-eslint`, `eslint-plugin-react-hooks`, `eslint-plugin-jsx-a11y` y `globals`. `frontend/test-results/` se agrega al `.gitignore`. Cada una se justifica en [research.md](research.md#r-11-dependencias).

**Almacenamiento**: ninguno persistente. El token de sesión y su vencimiento viven en memoria y en `sessionStorage` (constitución). No se usa `localStorage`. La clave de API nunca se guarda.

**Testing**: Vitest + React Testing Library + MSW (handlers en `frontend/tests/mocks/`). `npm test` = `vitest run`. Reporte JUnit en `frontend/test-results/junit.xml` para el artifact del CI.

**Plataforma objetivo**: navegadores modernos de escritorio y celular, desde 360 px de ancho. Desarrollo con `npm run dev` (Vite, puerto 5173) contra `./gradlew bootRun` (puerto 8080).

**Tipo de proyecto**: aplicación web (monorepo con `backend/` existente y `frontend/` nuevo).

**Objetivos de rendimiento**: sugerencias del buscador en menos de 1 s desde la segunda letra con el catálogo cargado (CE-006); con 50-60 jugadores, el filtrado en memoria es instantáneo y el costo es el debounce de 200 ms.

**Restricciones**: solo los endpoints existentes (RF-004); sin CORS en el backend (proxy de Vite); sin precios, compras ni plantel (RF-002, RF-003); credenciales y token fuera de la URL y de los logs (RF-012); textos en español (RF-005); accesible con teclado (RF-036, RF-037).

**Escala/Alcance**: 4 rutas de pantalla (+ redirección de `/`), unos 15 componentes, 50-60 jugadores en el catálogo, 7 endpoints consumidos.

## Verificación de la constitución

*GATE: se evalúa antes de la fase 0 y se vuelve a evaluar después del diseño de la fase 1.*

| Regla de la constitución 2.1.0 | Cómo la cumple el plan | Estado |
|---|---|---|
| Stack Frontend: React 18 + Vite + TS strict + React Router + Vitest + RTL + MSW + ESLint | Es exactamente el stack elegido. | OK |
| CSS Modules + variables CSS en un archivo de tokens; sin frameworks de UI; Google Fonts | `src/styles/tokens.css` + `global.css`; cada componente con su `X.module.css`. Sin Tailwind/MUI/Bootstrap. | OK |
| Ninguna dependencia nueva sin avisar y justificar | Las dependencias fuera de la lista nominal de la constitución (jsdom, user-event, jest-dom, typescript-eslint, plugins de ESLint, plugin de React para Vite) se listan y justifican en research R-11. | OK (se avisa) |
| Estructura por tipo de artefacto; árbol único; sin carpetas nuevas | Solo `components/ pages/ hooks/ services/ context/ types/ utils/ styles/ router/` en `src/` y su réplica en `tests/` + `tests/mocks/` + `tests/setup.ts`. | OK |
| Tests solo en `frontend/tests/`, replicando `src/` | `src/<carpeta>/X.tsx` -> `tests/<carpeta>/X.test.tsx`. | OK |
| Componentes y páginas nunca llaman a `fetch` | `fetch` aparece solo en `services/httpClient.ts`. | OK |
| `components/` no conoce la API ni el router | `PostIt` recibe `href`/`renderLink` por props; `Pitch`, `SearchBox`, `TeamSelect`, `Pager` reciben datos y callbacks. La lógica con estado vive en `hooks/` y `context/`. | OK (ver research R-9) |
| Tipos de la API a mano desde `contracts/*.yaml`, enums tal cual; traducción en `utils/` | `types/api.ts` replica los esquemas; etiquetas en `utils/positions.ts`. | OK |
| Token en memoria + `sessionStorage`, nunca logueado | `SessionContext`; no hay `console.*` con datos de sesión (regla de ESLint `no-console`). | OK |
| Proxy de Vite `/api` -> `http://localhost:8080`; sin CORS en el backend | `vite.config.ts` con `rewrite` que quita `/api`. El backend no se toca. | OK |
| Accesible: todo lo clickeable es `button` o link, foco visible | Zonas de la cancha = `<button aria-pressed>`; pestañas de liga = `button aria-pressed`; post-it = `<a>`; `:focus-visible` en tokens. `eslint-plugin-jsx-a11y` lo controla. | OK |
| Responsive desde 360 px | Grilla de post-its de 1 a 4 columnas; cancha compacta arriba en celular. | OK |
| Sin carpetas vacías ni `.gitkeep` | Cada carpeta nace con su primer archivo. | OK |
| Tests: felices y de borde, setup - execute - verify, nombres en español, sin TDD, no se testea estilo | Lista de tests del pedido; todos con nombres en español y los tres bloques. | OK |
| Principio IV: no se modifica ni borra un test existente | No hay tests de frontend existentes; no se toca ningún test del backend. | OK |
| Principio VII (Idioma): identificadores en inglés, textos y comentarios en español | Rutas de la UI en español (`/pizarra`, `/vestuario`) porque son texto visible; identificadores en inglés. Ver research R-13. | OK |
| Definición de terminado del frontend: `npm test`, `npm run lint`, `npm run build`, `npm run dev` contra `bootRun` | Los tres scripts existen en `package.json`, el CI corre los tres y el quickstart cubre `npm run dev`. | OK |
| No se modifica el backend | El plan no toca `backend/`. Solo agrega `frontend/**` y un workflow. | OK |

**Resultado del gate (pre-fase 0)**: sin violaciones. No hace falta la tabla de complejidad.

### Divergencias entre el pedido del plan y la spec clarificada

El input de `/speckit-plan` contradice dos puntos que la spec ya cerró. Se sigue la spec
(fuente de verdad de la feature) y se deja registrado:

1. **Nombres de los filtros en la dirección.** El pedido menciona `/pizarra?zona=&liga=&equipo=&pagina=`
   y "resetea la página a 0". La clarificación de la spec y RF-018 fijan `position`, `league`,
   `team` y `page`, con los valores del backend (`DEFENDER`, `LA_LIGA`) y `page` contado desde 1.
   El plan usa `?position=&league=&team=&page=`; "resetear a la página 0" se interpreta como
   "volver a la primera hoja", que en la dirección es quitar `page` (equivale a `page=1`) y hacia
   el backend es `page=0`.
2. **Rutas privadas y 401.** El pedido dice "rutas privadas protegidas por la sesión" y que ante
   un 401 el cliente "limpia la sesión y redirige a `/login`". La spec (RF-010, RF-011, CE-013)
   hace públicas la pizarra, el buscador y la ficha: la única ruta privada es `/vestuario`, y si
   la sesión vence estando en la pizarra o en una ficha, la pantalla sigue funcionando sin sesión.
   Además, `POST /auth/login` responde 401 ante credenciales inválidas, que no es una sesión
   vencida. Por eso `httpClient` limpia la sesión ante un 401 de una llamada autenticada y
   notifica al `SessionContext`; la redirección a `/login` con el aviso "tu sesión venció" la
   hace la ruta protegida del vestuario, no el cliente HTTP. Ver research R-5.

## Estructura del proyecto

### Documentación (esta feature)

```text
specs/004-frontend-pizarra-tactica/
├── plan.md              # este archivo
├── research.md          # fase 0
├── data-model.md        # fase 1
├── quickstart.md        # fase 1
├── contracts/
│   └── README.md        # fase 1: solo referencias a los contratos de 001 y 002
├── checklists/
│   └── requirements.md  # de /speckit-specify
└── tasks.md             # fase 2 (/speckit-tasks; no lo crea /speckit-plan)
```

### Código fuente

```text
.github/workflows/
└── frontend-ci.yml            # nuevo; backend-ci.yml no se toca

frontend/
├── index.html                 # carga Google Fonts y monta #root
├── package.json               # scripts: dev, build, preview, lint, test
├── package-lock.json
├── tsconfig.json              # strict
├── tsconfig.node.json
├── vite.config.ts             # proxy /api -> http://localhost:8080 (rewrite sin /api)
├── vitest.config.ts           # jsdom, setupFiles, reporters default + junit
├── eslint.config.js           # flat config
├── mockups/
│   └── pizarra-tactica.html   # existente; no se importa desde src/
├── src/
│   ├── main.tsx
│   ├── components/
│   │   ├── Board.tsx                  # pizarrón con marco de madera
│   │   ├── Pitch.tsx                  # cancha SVG; zonas = <button aria-pressed>; sin jugadores
│   │   ├── PostIt.tsx                 # link a la ficha; color por posición; sin acciones
│   │   ├── TapeTab.tsx                # pestaña de liga (cinta adhesiva)
│   │   ├── TeamSelect.tsx             # desplegable "equipo: todos ▾"
│   │   ├── Pager.tsx                  # hoja anterior / siguiente, "hoja 2 de 5"
│   │   ├── SearchBox.tsx              # combobox accesible con debounce 200 ms
│   │   ├── ChalkText.tsx
│   │   ├── ChalkInput.tsx
│   │   ├── ChalkButton.tsx
│   │   ├── ChalkNote.tsx              # mensajes, errores, carga, confirmaciones
│   │   ├── ScoutSheet.tsx             # ficha del jugador
│   │   ├── ApiKeyReveal.tsx           # clave mostrada una sola vez, con copiar
│   │   ├── TopBar.tsx                 # logo, buscador, saldo, entrar/vestuario
│   │   └── *.module.css               # un CSS Module por componente
│   ├── pages/
│   │   ├── AppLayout.tsx              # TopBar + <Outlet/>
│   │   ├── LoginPage.tsx
│   │   ├── RegisterPage.tsx
│   │   ├── BoardPage.tsx
│   │   ├── PlayerPage.tsx
│   │   ├── LockerRoomPage.tsx
│   │   └── *.module.css
│   ├── hooks/
│   │   ├── useSession.ts              # lee SessionContext
│   │   ├── usePlayers.ts              # hoja de 12 desde GET /players
│   │   ├── useCatalog.ts              # catálogo completo, diferido y cacheado
│   │   ├── useBoardFilters.ts         # filtros <-> URL (useSearchParams)
│   │   ├── usePlayer.ts               # GET /players/{id} para la ficha
│   │   ├── useAsync.ts                # estado loading/error/data + reintentar
│   │   └── useLeaveGuard.ts           # aviso al salir de la vista de la clave
│   ├── services/
│   │   ├── httpClient.ts              # ÚNICO punto con fetch
│   │   ├── authService.ts             # register, login, getProfile, changePassword, regenerateApiKey
│   │   ├── playerService.ts           # getPlayers, getPlayer
│   │   └── sessionStorage.ts          # lectura/escritura de la sesión en sessionStorage
│   ├── context/
│   │   └── SessionContext.tsx         # token + vencimiento + perfil (GET /auth/me)
│   ├── types/
│   │   ├── api.ts                     # tipos de 001 y 002 (sin campos inventados)
│   │   └── board.ts                   # BoardFilters, PitchZone, Session
│   ├── utils/
│   │   ├── search.ts                  # normalizar + "contiene" + tope 8
│   │   ├── positions.ts               # zona <-> Position, etiquetas de posiciones, ligas y roles
│   │   ├── teams.ts                   # equipos sin repetidos, ordenados, acotados por liga
│   │   ├── filters.ts                 # parsear/serializar la URL y describir combinación vacía
│   │   └── format.ts                  # saldo con dos decimales
│   ├── styles/
│   │   ├── tokens.css                 # colores, fuentes, sombras del mockup
│   │   └── global.css                 # reset, fondo de madera, :focus-visible
│   └── router/
│       ├── routes.tsx                 # createBrowserRouter
│       └── RequireSession.tsx         # guarda de /vestuario
└── tests/
    ├── setup.ts                       # jest-dom, MSW server, limpieza de sessionStorage
    ├── mocks/
    │   ├── server.ts
    │   ├── handlers.ts                # /api/auth/*, /api/players (filtra y pagina de verdad)
    │   ├── players.ts                 # catálogo de prueba (> 12 y > 50 jugadores, con acentos)
    │   └── renderWithRouter.tsx       # helper de render con router y SessionProvider
    ├── utils/      search.test.ts, teams.test.ts, positions.test.ts, filters.test.ts
    ├── hooks/      useCatalog.test.tsx, useBoardFilters.test.tsx, usePlayers.test.tsx
    ├── services/   httpClient.test.ts
    ├── components/ Pitch.test.tsx, SearchBox.test.tsx, PostIt.test.tsx
    └── pages/      LoginPage.test.tsx, RegisterPage.test.tsx, BoardPage.test.tsx,
                    PlayerPage.test.tsx, LockerRoomPage.test.tsx, NoPurchaseActions.test.tsx
```

**Decisión de estructura**: aplicación web en monorepo. `backend/` existe y no se toca;
`frontend/` sigue al pie de la letra el árbol de la sección Frontend de la constitución.
Respecto de la lista del pedido se agregan, dentro de las carpetas permitidas:
`pages/AppLayout.tsx` (layout común con la barra superior), `router/RequireSession.tsx`
(guarda de la única ruta privada), `hooks/usePlayer.ts`, `hooks/useAsync.ts` y
`hooks/useLeaveGuard.ts`, `hooks/useSession.ts`, `services/sessionStorage.ts`,
`utils/filters.ts` y `utils/format.ts`. `tests/mocks/renderWithRouter.tsx` vive en `mocks/`
porque es infraestructura de pruebas, no un test. Los nombres de ejemplo de la constitución
`Magnet` y `useScoutingEleven` no se crean: corresponden al armado de equipos, que está fuera
de alcance.

### Rutas

| Ruta | Página | Acceso |
|---|---|---|
| `/` | redirige a `/pizarra` | pública |
| `/login` | `LoginPage` | pública; si ya hay sesión, redirige a `/pizarra` |
| `/register` | `RegisterPage` | pública |
| `/pizarra?position=&league=&team=&page=` | `BoardPage` | pública |
| `/jugadores/:id` | `PlayerPage` | pública |
| `/vestuario` | `LockerRoomPage` | privada (`RequireSession`) |
| `*` | redirige a `/pizarra` | pública |

Todas cuelgan de `AppLayout` (barra superior con logo, buscador, saldo si hay sesión y acceso
"entrar" o "vestuario").

### Integración continua

`.github/workflows/frontend-ci.yml`, con el estilo de `backend-ci.yml`:

- `on.push` y `on.pull_request` a `main` y `develop`, con `paths: ['frontend/**', '.github/workflows/frontend-ci.yml']`.
- `concurrency: { group: ${{ github.workflow }}-${{ github.ref }}, cancel-in-progress: true }` y `permissions: contents: read`.
- Job `test` en `ubuntu-latest`, `defaults.run.working-directory: frontend`.
- Pasos: `actions/checkout`, `actions/setup-node` (Node 20, `cache: npm`, `cache-dependency-path: frontend/package-lock.json`), `npm ci`, `npm run lint`, `npm test`, `npm run build`.
- `actions/upload-artifact` con `if: always()`, `name: frontend-test-results`, `path: frontend/test-results`, `retention-days: 7`.
- Las versiones mayores de las actions siguen las que usa `backend-ci.yml` (`checkout@v7`, `upload-artifact@v7`) y la mayor vigente de `setup-node` al implementar.

No se agrega job de SonarCloud para el frontend (no lo pide la spec ni la constitución).

## Verificación de la constitución (post-diseño, fase 1)

Revisados [data-model.md](data-model.md), [contracts/README.md](contracts/README.md) y
[quickstart.md](quickstart.md):

- Los tipos de `data-model.md` copian los esquemas de `auth-api.yaml` y `players-api.yaml`
  sin agregar campos; los tipos propios del front (`BoardFilters`, `PitchZone`, `Session`) no
  modelan precio, compra ni plantel. OK.
- `contracts/` no crea contratos nuevos: solo referencia los de 001 y 002 y documenta el
  prefijo `/api` del proxy. OK (RF-004, "no se escriben contratos de integración entre features").
- El quickstart usa `./gradlew bootRun` sin Docker y los mismos tres comandos que el CI. OK.
- Ningún archivo nuevo cae fuera del árbol permitido. OK.

**Resultado del gate (post-fase 1)**: sin violaciones.

## Seguimiento de complejidad

No aplica: no hay violaciones de la constitución que justificar.
