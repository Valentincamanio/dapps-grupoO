---

description: "Lista de tareas para implementar el frontend Pizarra táctica (catálogo de solo lectura)"
---

# Tareas: Frontend "Pizarra táctica" (catálogo de solo lectura)

**Entrada**: documentos de diseño de `specs/004-frontend-pizarra-tactica/`

**Prerrequisitos**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/README.md](contracts/README.md), [quickstart.md](quickstart.md)

**Tests**: SÍ. El plan fija la lista de tests (Vitest + RTL + MSW) y la constitución exige que,
al terminar cada tarea, sus tests existan y pasen. **No se trabaja con TDD**: cada test se
escribe junto con (o justo después de) la implementación que prueba, nunca antes. Todo test
tiene nombre en español y los tres bloques setup - execute - verify separados por una línea en
blanco. No se testea estilo. No se modifica ni se borra un test ya escrito: cuando una historia
posterior amplía un archivo de test, solo **agrega** casos nuevos.

**Organización**: las tareas se agrupan por historia de usuario para que cada una se pueda
implementar y probar por separado.

## Formato: `[ID] [P?] [Historia] Descripción`

- **[P]**: se puede hacer en paralelo (archivos distintos, sin dependencias pendientes)
- **[Historia]**: historia de usuario a la que pertenece (US1 a US6)
- Todas las rutas son relativas a la raíz del repo; el código vive en `frontend/`

## Reglas transversales (valen para todas las tareas)

- `fetch` aparece **solo** en `frontend/src/services/httpClient.ts`.
- `frontend/src/components/` no importa nada de `react-router` ni de `services/`: recibe datos y callbacks por props (research R-9).
- Identificadores en inglés; textos visibles y comentarios en español (Principio VII).
- Nada de precios, valores, compras, ventas, plantel, órdenes, puntajes ni portfolio, ni siquiera deshabilitado (RF-002, research R-4).
- Todo lo accionable es `<button>` o `<a>`; foco visible por `:focus-visible` de `global.css`.
- Sin `console.*` con datos de sesión (regla `no-console` de ESLint). Token y clave de API nunca en la URL.
- Cada componente o página con estilos tiene su `X.module.css` al lado; los colores y fuentes salen de `styles/tokens.css`.
- Sin carpetas vacías ni `.gitkeep`: cada carpeta nace con su primer archivo.

---

## Fase 1: Setup (infraestructura compartida)

**Propósito**: crear el proyecto `frontend/` con el stack de la constitución 2.1.0.

- [X] T001 Crear `frontend/package.json` con `"type": "module"`, `"engines": { "node": ">=20" }` y los scripts `dev` (`vite`), `build` (`tsc -b && vite build`), `preview` (`vite preview`), `lint` (`eslint .`) y `test` (`vitest run`); instalar las dependencias de research R-11 (runtime: `react@18`, `react-dom@18`, `react-router@7`; dev: `vite`, `@vitejs/plugin-react`, `typescript@5`, `@types/react@18`, `@types/react-dom@18`, `vitest`, `jsdom`, `@testing-library/react`, `@testing-library/user-event`, `@testing-library/jest-dom`, `msw@2`, `eslint`, `@eslint/js`, `typescript-eslint`, `eslint-plugin-react-hooks`, `eslint-plugin-jsx-a11y`, `globals`) con `npm install` desde `frontend/`, de modo que se genere `frontend/package-lock.json`. Si alguna versión vigente de Vite/Vitest exige Node > 20, fijar la última compatible con Node 20 y dejarlo anotado (research R-12)
- [X] T002 [P] Crear `frontend/tsconfig.json` (`strict: true`, `jsx: react-jsx`, `moduleResolution: bundler`, `noEmit`, `types: ["vitest/globals", "@testing-library/jest-dom"]`, incluye `src` y `tests`) y `frontend/tsconfig.node.json` para `vite.config.ts`, `vitest.config.ts` y `eslint.config.js`
- [X] T003 [P] Crear `frontend/vite.config.ts` con `@vitejs/plugin-react`, `server.port: 5173` y el proxy `'/api' -> http://localhost:8080` con `changeOrigin: true` y `rewrite: (p) => p.replace(/^\/api/, '')` (sin CORS en el backend)
- [X] T004 [P] Crear `frontend/vitest.config.ts` con `environment: 'jsdom'`, `globals: true`, `setupFiles: ['tests/setup.ts']`, `include: ['tests/**/*.test.{ts,tsx}']`, `css: { modules: { classNameStrategy: 'non-scoped' } }`, `reporters: ['default', 'junit']` y `outputFile: { junit: 'test-results/junit.xml' }` (research R-15)
- [X] T005 [P] Crear `frontend/eslint.config.js` (flat config) con `@eslint/js` recomendado, `typescript-eslint` recomendado, `eslint-plugin-react-hooks` (reglas recomendadas), `eslint-plugin-jsx-a11y` (recomendado), `globals.browser` para `src/` y `globals.node` para los archivos de configuración, la regla `no-console: 'error'` y los ignores `dist/`, `test-results/`, `mockups/`, `node_modules/`
- [X] T006 [P] Crear `frontend/index.html` con `lang="es"`, `<meta name="viewport" content="width=device-width, initial-scale=1">`, título "FutbolMarket - la pizarra", los `<link>` de Google Fonts para Caveat, Permanent Marker y Patrick Hand, `<div id="root">` y `<script type="module" src="/src/main.tsx">`
- [X] T007 [P] Agregar `frontend/test-results/` a la sección Node de `.gitignore` (raíz del repo)
- [X] T008 [P] Crear `frontend/src/styles/tokens.css` con las variables CSS tomadas de `frontend/mockups/pizarra-tactica.html`: verde del pizarrón, madera del marco, tiza (blanco y tonos), colores de post-it `--yellow`, `--blue`, `--green`, `--pink`, cinta adhesiva, chinche, sombras y las tres familias tipográficas
- [X] T009 [P] Crear `frontend/src/styles/global.css` (importa `tokens.css`): reset mínimo, `box-sizing`, fondo de madera en `body`, tipografía base manuscrita, `:focus-visible` con contorno de tiza bien visible y `overflow-x: hidden` en el contenedor raíz para no tener desplazamiento horizontal a 360 px

**Checkpoint**: `npm run lint` corre (sin archivos fuente todavía) y la configuración de TypeScript, Vite y Vitest está lista.

---

## Fase 2: Fundacional (prerrequisitos que bloquean todo)

**Propósito**: tipos, cliente HTTP, sesión, catálogo diferido, componentes de tiza base, layout,
router y la infraestructura de pruebas con MSW. Ninguna historia empieza antes de terminar esta fase.

### Tipos y utilidades puras

- [X] T010 [P] Crear `frontend/src/types/api.ts` copiando los esquemas de `specs/001-auth-usuarios/contracts/auth-api.yaml` y `specs/002-catalogo-jugadores/contracts/players-api.yaml` según data-model §1: `POSITIONS`, `LEAGUES`, `ROLES` como arreglos `as const` con sus tipos unión `Position`, `League`, `Role`; `Player`, `PlayerPage`, `PlayerQuery`, `RegisterRequest`, `RegisterResponse`, `LoginRequest`, `LoginResponse`, `Profile`, `ChangePasswordRequest`, `ApiKeyResponse`, `ApiError`, `Violation`. Sin campos inventados
- [X] T011 [P] Crear `frontend/src/types/board.ts` con `Session` (`token`, `expiresAt`), `SessionEndReason` (`'logout' | 'expired' | null`), `BoardFilters` (`position`, `league`, `team` nulos = sin filtro; `page` desde 1), `PitchZone` (`'GOAL' | 'DEFENSE' | 'MIDFIELD' | 'ATTACK'`), `BoardSheet` (data-model §2) y `AsyncState<T>` (`status: 'idle' | 'loading' | 'success' | 'error'`, `data?`, `error?`)
- [X] T012 [P] Crear `frontend/src/utils/positions.ts` con la tabla de data-model §4: `zoneToPosition`, `positionToZone`, `zoneLabel` (arco, defensa, mediocampo, delantera), `positionLabel` (Arquero, Defensor, Mediocampista, Delantero), `positionPlural` (arqueros, ...), `leagueLabel` (Premier League, Bundesliga, La Liga, Serie A, Ligue 1) y `roleLabel` (Usuario, Administrador)
- [X] T013 [P] Crear `frontend/src/utils/format.ts` con `formatBalance(balance: number): string` (dos decimales, `Intl.NumberFormat('es-AR', { minimumFractionDigits: 2, maximumFractionDigits: 2 })`)

### Servicios

- [X] T014 Crear `frontend/src/services/httpClient.ts` (research R-5 y R-14): única función `request<T>(method, path, { query?, body?, signal? })` que arma la URL con `new URL('/api' + path, window.location.origin)`, serializa `query` con `URLSearchParams` omitiendo `undefined`/`null`/`''`, manda JSON, agrega `Authorization: Bearer <token>` solo si `getToken()` devuelve token, devuelve `undefined` en 204, parsea errores como `ApiError` y lanza la clase exportada `ApiRequestError` (con `.apiError`); ante error de red o cuerpo no JSON lanza un `ApiError` sintético con `status: 0` y mensaje "no se pudo conectar con la pizarra"; ante un 401 de una llamada que llevaba token llama a `onUnauthorized()`. Exporta `setAuthHandlers({ getToken, onUnauthorized })`. No importa React. Un `AbortError` se relanza tal cual (no es error de red)
- [X] T015 [P] Crear `frontend/src/services/sessionStorage.ts` con `readSession(): Session | null` (descarta y borra si `expiresAt <= ahora` o si el JSON es inválido), `writeSession(session)` y `clearSession()`, todo bajo la clave `futbolmarket.session` de `window.sessionStorage` (nunca `localStorage`)
- [X] T016 [P] Crear `frontend/src/services/playerService.ts` con `getPlayers(query: PlayerQuery, signal?)` -> `GET /players` y `getPlayer(id: number, signal?)` -> `GET /players/{id}`, ambos vía `httpClient.request`
- [X] T017 Crear `frontend/src/services/authService.ts` con `login(req: LoginRequest)` -> `POST /auth/login` y `getProfile()` -> `GET /auth/me` vía `httpClient.request` (register, changePassword y regenerateApiKey se agregan en US1 y US6)

### Sesión y hooks base

- [X] T018 Crear `frontend/src/context/SessionContext.tsx` (data-model §2, research R-5): `SessionProvider` que al montar lee `readSession()`; si está vigente queda en estado "autenticando" y pide `getProfile()`; expone `{ status: 'anonymous' | 'authenticating' | 'authenticated', session, profile, endReason, login(username, password), logout(), refreshProfile() }`. `login` llama a `authService.login`, guarda `{ token, expiresAt }` en memoria y con `writeSession`, y pide el perfil. `logout` limpia todo con motivo `'logout'`. Registra `setAuthHandlers` para que `httpClient` lea el token y, ante `onUnauthorized`, limpie la sesión con motivo `'expired'`. Programa un temporizador que expira la sesión (motivo `'expired'`) al llegar a `expiresAt`. Nunca loguea el token
- [X] T019 Crear `frontend/src/hooks/useSession.ts` que devuelve el valor de `SessionContext` y lanza un error claro si se usa fuera del `SessionProvider`
- [X] T020 [P] Crear `frontend/src/hooks/useAsync.ts`: recibe una función `(signal) => Promise<T>` y una lista de dependencias, ejecuta con `AbortController` (cancela el pedido anterior al cambiar dependencias o desmontar), expone `AsyncState<T> & { retry(): void }` y guarda `ApiError` en `error`
- [X] T021 Crear `frontend/src/hooks/useCatalog.ts` (research R-2): no pide nada al montarse; `ensureLoaded()` pide `getPlayers({ size: 50, page: 0 })`, sigue con `page` 1, 2, ... mientras `hasNext` sea `true` y comparte una única promesa a nivel de módulo (dos llamadas simultáneas no duplican pedidos); si falla, descarta la caché para que `retry` vuelva a pedir. Expone `{ status, players, error, ensureLoaded, retry }` y exporta `resetCatalogCache()` para los tests

### Componentes y layout base

- [ ] T022 [P] Crear los componentes de tiza `frontend/src/components/ChalkText.tsx`, `frontend/src/components/ChalkInput.tsx` (label visible, `id` asociado, mensaje de error de campo con `aria-describedby` e `aria-invalid`), `frontend/src/components/ChalkButton.tsx` (`<button>` con `disabled` y estado ocupado) y `frontend/src/components/ChalkNote.tsx` (variantes `info`, `error`, `loading` con el texto por defecto "el DT está pensando...", `success`; `role="status"` o `role="alert"` según la variante; acción opcional como botón, p. ej. "reintentar"), cada uno con su `.module.css`
- [ ] T023 [P] Crear `frontend/src/components/Board.tsx` y `frontend/src/components/Board.module.css`: pizarrón verde con marco de madera que envuelve `children`, fiel al mockup y sin elementos de presupuesto ni plantel
- [ ] T024 Crear `frontend/src/components/TopBar.tsx` y `frontend/src/components/TopBar.module.css`: logo, `searchSlot?: ReactNode`, `balance?: string` (se muestra solo si viene, sin acción asociada) y `accountLink: ReactNode` (lo arma la página). No importa router ni servicios
- [ ] T025 Crear `frontend/src/pages/AppLayout.tsx` y `frontend/src/pages/AppLayout.module.css`: usa `useSession` y `useLocation`; renderiza `TopBar` con el saldo formateado con `formatBalance` cuando hay perfil, y como `accountLink` un `Link` "entrar" a `/login` con `state = { from: location }` sin sesión o un `Link` "vestuario" a `/vestuario` con sesión; debajo `<Outlet />`
- [ ] T026 Crear `frontend/src/router/routes.tsx` con `createBrowserRouter` y una ruta raíz con `element: <AppLayout />` y `children: []` (cada historia agrega sus rutas), y `frontend/src/main.tsx` que importa `styles/global.css` y monta `<SessionProvider><RouterProvider router={router} /></SessionProvider>` en `#root` dentro de `StrictMode`

### Infraestructura de pruebas

- [ ] T027 [P] Crear `frontend/tests/mocks/players.ts` con un catálogo de prueba de 55 jugadores (> 12 y > 50, para que haya varias hojas y el catálogo completo necesite dos pedidos de `size=50`), con las cinco ligas y las cuatro posiciones, varios equipos por liga, nombres con acentos (incluido "Kylian Mbappé"), un equipo sin arqueros y más de 8 nombres que contengan un mismo texto
- [ ] T028 Crear `frontend/tests/mocks/handlers.ts` con MSW 2 sobre `http://localhost:3000/api/...` (o `*/api/...`): `GET /players` que valida `size` (1..50) y `page` (>= 0), filtra por `position`, `league`, `team` y pagina de verdad devolviendo `PlayerPage`; `GET /players/:id` con 404 `ApiError` si no existe y 400 si no es numérico; `POST /auth/register`, `POST /auth/login` (401 con credenciales malas), `GET /auth/me`, `PUT /auth/me/password` (204 / 400 con `violations` / 401 sin token válido) y `POST /auth/me/api-key`, todos con las formas de los contratos y un estado de usuarios en memoria que se reinicia con una función exportada `resetMockDb()`
- [ ] T029 Crear `frontend/tests/mocks/server.ts` (`setupServer(...handlers)`) y `frontend/tests/setup.ts`: importa `@testing-library/jest-dom/vitest`, `server.listen({ onUnhandledRequest: 'error' })` en `beforeAll`, y en `afterEach` `cleanup()`, `server.resetHandlers()`, `resetMockDb()`, `sessionStorage.clear()` y `resetCatalogCache()`; `server.close()` en `afterAll`. Configurar `window.location` en `http://localhost:3000` si jsdom no lo hace
- [ ] T030 Crear `frontend/tests/mocks/renderWithRouter.tsx`: helper `renderWithRouter({ routes, initialEntries, session? })` que arma `createMemoryRouter` con las rutas reales de la app (o las indicadas), lo envuelve en `SessionProvider`, permite precargar una sesión válida o vencida en `sessionStorage` y devuelve `{ user: userEvent.setup(), router, ...render }`
- [ ] T031 [P] Escribir `frontend/tests/services/httpClient.test.ts`: agrega `Authorization` solo con token; omite query params vacíos; prefija `/api`; devuelve `undefined` en 204; lanza `ApiRequestError` con el `ApiError` del cuerpo; error de red y cuerpo no JSON dan `status: 0` y "no se pudo conectar con la pizarra"; un 401 con token llama a `onUnauthorized`; un 401 sin token (login fallido) no la llama
- [ ] T032 [P] Escribir `frontend/tests/utils/positions.test.ts`: ida y vuelta zona <-> posición para las cuatro zonas, etiquetas en español de posiciones, ligas y roles, plurales
- [ ] T033 [P] Escribir `frontend/tests/hooks/useCatalog.test.tsx`: no pide nada al montarse; `ensureLoaded` trae los 55 jugadores en dos pedidos; dos llamadas simultáneas hacen un solo recorrido; un segundo `ensureLoaded` no vuelve a pedir; si falla queda en `error` y `retry` vuelve a pedir y carga

### Integración continua

- [ ] T034 Crear `.github/workflows/frontend-ci.yml` siguiendo el estilo de `.github/workflows/backend-ci.yml` (plan, "Integración continua"): `on.push` y `on.pull_request` a `main` y `develop` con `paths: ['frontend/**', '.github/workflows/frontend-ci.yml']`; `concurrency` por workflow y ref con `cancel-in-progress: true`; `permissions: contents: read`; job `test` en `ubuntu-latest` con `defaults.run.working-directory: frontend`; pasos `actions/checkout` (misma mayor que backend-ci), `actions/setup-node` (Node 20, `cache: npm`, `cache-dependency-path: frontend/package-lock.json`), `npm ci`, `npm run lint`, `npm test`, `npm run build` y `actions/upload-artifact` (misma mayor que backend-ci) con `if: always()`, `name: frontend-test-results`, `path: frontend/test-results`, `retention-days: 7`. No tocar `backend-ci.yml`
- [ ] T035 Verificar desde `frontend/` que `npm run lint`, `npm test` y `npm run build` terminan con código 0 y que `frontend/test-results/junit.xml` se genera

**Checkpoint**: base lista. Las historias pueden empezar (en orden de prioridad o en paralelo).

---

## Fase 3: Historia 1 - Acceso: registrarse e iniciar sesión (Prioridad: P1) 🎯 MVP

**Objetivo**: registro con clave de API mostrada una sola vez, inicio de sesión, guarda de la
única ruta privada (`/vestuario`) y regreso a la pantalla de origen al entrar.

**Prueba independiente**: registrar una persona nueva, ver la clave con "copiar" y la
advertencia, continuar y llegar a `/pizarra` con sesión; cerrar sesión, entrar con las mismas
credenciales y llegar a `/pizarra`; abrir `/vestuario` sin sesión y ser llevada a `/login` con
el aviso, y al entrar volver a `/vestuario`.

### Implementación de la historia 1

- [ ] T036 [US1] Agregar `register(req: RegisterRequest): Promise<RegisterResponse>` -> `POST /auth/register` en `frontend/src/services/authService.ts`
- [ ] T037 [P] [US1] Crear `frontend/src/utils/formErrors.ts` con `toFormErrors(apiError, fields)` según data-model §1 "Reparto de errores": cada `violation.field` va a su campo; `message` sin `violations` va como nota general; en los 409 del registro, si el mensaje menciona "usuario" se asocia también a `username` y si menciona "correo" a `email`
- [ ] T038 [P] [US1] Crear `frontend/src/components/ApiKeyReveal.tsx` y `frontend/src/components/ApiKeyReveal.module.css` (research R-10): clave en un campo de solo lectura seleccionable, advertencia fija "esta clave no se vuelve a mostrar", botón "copiar" que usa `navigator.clipboard.writeText` y confirma con `ChalkNote` "clave copiada"; si falla, avisa que la copia automática no funcionó y selecciona el texto; botón "continuar" vía callback `onContinue`. No guarda la clave en ningún lado
- [ ] T039 [P] [US1] Crear `frontend/src/hooks/useLeaveGuard.ts` (research R-10): con `active = true` usa `useBlocker` de React Router para pedir confirmación al navegar dentro de la app y un listener de `beforeunload` para recargar o cerrar; expone el estado del bloqueo para que la página muestre una `ChalkNote` de confirmación ("la clave no se vuelve a mostrar, ¿salir igual?" con "salir" y "quedarme")
- [ ] T040 [US1] Crear `frontend/src/pages/LoginPage.tsx` y `frontend/src/pages/LoginPage.module.css`: formulario en tiza (usuario, contraseña) dentro de `Board`; muestra el aviso según `location.state.reason` ("para entrar al vestuario tenés que iniciar sesión" o "tu sesión venció, volvé a entrar"); al enviar deshabilita el botón y muestra "el DT está pensando..."; ante 401 muestra el mensaje del sistema como nota general y conserva el usuario; ante éxito navega con `replace` a `state.from` (pathname + search, así vuelve con filtros y hoja) o a `/pizarra`; si ya hay sesión al entrar a la página, redirige a `/pizarra`; link a "registrarse"
- [ ] T041 [US1] Crear `frontend/src/pages/RegisterPage.tsx` y `frontend/src/pages/RegisterPage.module.css`: formulario en tiza (usuario, correo, contraseña) sin doble envío; errores con `toFormErrors` junto a cada campo, conservando usuario y correo y vaciando la contraseña; ante éxito guarda la `apiKey` solo en estado local, muestra `ApiKeyReveal` y activa `useLeaveGuard`; "continuar" llama a `login` del `SessionContext` con las credenciales del estado del formulario, las descarta, desactiva el guard y navega a `/pizarra`; error de red con "reintentar" sin perder los datos ingresados
- [ ] T042 [US1] Crear `frontend/src/router/RequireSession.tsx` (research R-5): mientras la sesión está "autenticando" muestra "el DT está pensando..."; sin sesión navega a `/login` con `replace` y `state = { from: location, reason }` donde `reason` es `'expired'` si `endReason === 'expired'` y `'required'` en otro caso; con sesión renderiza `children`
- [ ] T043 [US1] Crear `frontend/src/pages/LockerRoomPage.tsx` y `frontend/src/pages/LockerRoomPage.module.css` en versión inicial: título "el vestuario" dentro de `Board` y el usuario del perfil (US6 la completa), y registrar en `frontend/src/router/routes.tsx` las rutas `/login` -> `LoginPage`, `/register` -> `RegisterPage` y `/vestuario` -> `<RequireSession><LockerRoomPage /></RequireSession>`

### Tests de la historia 1

- [ ] T044 [P] [US1] Escribir `frontend/tests/utils/formErrors.test.ts`: violations por campo, mensaje general sin violations, 409 de usuario y de correo asociados al campo correcto
- [ ] T045 [P] [US1] Escribir `frontend/tests/pages/LoginPage.test.tsx` con `renderWithRouter` y MSW: entrar con credenciales correctas lleva a `/pizarra`; credenciales incorrectas muestran la nota y siguen en `/login`; `/vestuario` sin sesión lleva a `/login` con "para entrar al vestuario tenés que iniciar sesión" y al entrar vuelve a `/vestuario`; entrar desde "entrar" con `/pizarra?position=DEFENDER&page=2` vuelve a esa misma dirección; sesión vencida en `sessionStorage` al abrir `/vestuario` muestra "tu sesión venció, volvé a entrar"; el botón no se puede accionar dos veces mientras espera
- [ ] T046 [P] [US1] Escribir `frontend/tests/pages/RegisterPage.test.tsx`: registro válido muestra la clave una vez con "copiar" y la advertencia; "copiar" confirma con nota (con `navigator.clipboard` simulado) y el fallo de copia avisa y deja la clave visible; "continuar" deja la sesión iniciada y lleva a `/pizarra`; después la clave no aparece en ninguna pantalla ni en `sessionStorage`; usuario duplicado (409) muestra la nota junto al campo usuario y conserva usuario y correo; contraseña inválida (400 con violations) muestra la nota junto a la contraseña

**Checkpoint**: la historia 1 funciona y se prueba sola (`npm test` verde).

---

## Fase 4: Historia 2 - La pizarra: catálogo completo (Prioridad: P1)

**Objetivo**: `/pizarra` pública con hojas de 12 post-its pedidas a `GET /players`, paginación
"hoja X de N" con la hoja en la dirección y post-its que solo abren la ficha.

**Prueba independiente**: sin sesión y sin filtros, recorrer todas las hojas y comprobar que
cada jugador aparece exactamente una vez, que cada post-it muestra nombre, posición, equipo y
liga, y que tocarlo navega a `/jugadores/<id>`.

### Implementación de la historia 2

- [ ] T047 [P] [US2] Crear `frontend/src/components/PostIt.tsx` y `frontend/src/components/PostIt.module.css` (research R-9, RF-014, RF-014a): recibe `player`, `href`, `linkState?` y `LinkComponent?` (por defecto `<a>`); todo el post-it es un único link con nombre, posición (`positionLabel`), equipo y liga (`leagueLabel`); clase de color por posición (amarillo, celeste, verde, rosa); chinche decorativa `aria-hidden`; `draggable={false}`; sin ninguna otra acción ni precio, valor o puntaje
- [ ] T048 [P] [US2] Crear `frontend/src/components/Pager.tsx` y `frontend/src/components/Pager.module.css`: botones "hoja anterior" y "hoja siguiente" deshabilitados según `hasPrevious`/`hasNext`, texto "hoja X de N" con `aria-live="polite"`, callbacks `onPrevious`/`onNext`
- [ ] T049 [P] [US2] Crear `frontend/src/utils/filters.ts` con `parseBoardFilters(params: URLSearchParams): BoardFilters` y `toSearchParams(filters: BoardFilters): URLSearchParams` según data-model §3: `position` y `league` solo si están en `POSITIONS`/`LEAGUES`, `team` recortado y no vacío, `page` entero `>= 1`; los inválidos se ignoran; al serializar se omiten filtros vacíos y `page=1`. Además `toPlayerQuery(filters)` que devuelve `{ position, league, team, page: page - 1, size: 12 }`
- [ ] T050 [US2] Crear `frontend/src/hooks/useBoardFilters.ts` con `useSearchParams`: devuelve `filters` (vía `parseBoardFilters`) y `setPage(page)` que escribe solo la hoja con `setSearchParams(..., { replace: false })` (US3 agrega el resto)
- [ ] T051 [US2] Crear `frontend/src/hooks/usePlayers.ts` (research R-7): sobre `useAsync`, llama a `getPlayers(toPlayerQuery(filters), signal)` cada vez que cambian los filtros, cancela el pedido anterior y expone `{ status, sheet: BoardSheet | undefined, error, retry }` con `sheet`, `totalSheets`, `hasPrevious`, `hasNext`, `isEmptyCombination` e `isOutOfRange` (data-model §2)
- [ ] T052 [US2] Crear `frontend/src/pages/BoardPage.tsx` y `frontend/src/pages/BoardPage.module.css`: dentro de `Board`, usa `useBoardFilters` y `usePlayers`; carga -> `ChalkNote` "el DT está pensando..."; error -> nota "no se pudo conectar" con "reintentar" (`retry`, sin perder filtros ni hoja); `isOutOfRange` -> nota de pizarra vacía con "volver a la primera hoja" (`setPage(1)`); `isEmptyCombination` -> nota "no hay jugadores en la pizarra" (US3 la reemplaza por la descripción de la combinación); grilla de `PostIt` de 1 a 4 columnas (una sola a 360 px) con `href` `/jugadores/<id>`, `LinkComponent = Link` y `linkState = { boardSearch: location.search }` (research R-10); `Pager` debajo con `setPage(sheet ± 1)`
- [ ] T053 [US2] Registrar en `frontend/src/router/routes.tsx` la ruta `/pizarra` -> `BoardPage`, el índice `/` -> `<Navigate to="/pizarra" replace />` y `*` -> `<Navigate to="/pizarra" replace />`

### Tests de la historia 2

- [ ] T054 [P] [US2] Escribir `frontend/tests/components/PostIt.test.tsx`: muestra nombre, "Defensor", equipo y "La Liga"; es un único link al `href`; no contiene botones ni textos de comprar, vender, plantel, precio o valor; no es arrastrable
- [ ] T055 [P] [US2] Escribir `frontend/tests/utils/filters.test.ts` con los casos de parseo y serialización: valores válidos, `position=XYZ` y `league=XYZ` ignorados, `page=abc`, `page=0` y `page=-3` ignorados (= 1), `team` con espacios recortado y vacío ignorado, `page=1` y filtros vacíos omitidos al serializar, `toPlayerQuery` resta 1 a la hoja y fija `size=12`
- [ ] T056 [P] [US2] Escribir `frontend/tests/hooks/usePlayers.test.tsx`: la primera hoja trae 12 jugadores y 5 hojas para 55; la última trae 7; `isOutOfRange` con `page` 40; `isEmptyCombination` con un equipo inexistente; error de red y `retry` exitoso
- [ ] T057 [US2] Escribir `frontend/tests/pages/BoardPage.test.tsx` con los casos de la historia 2: sin sesión se ve la primera hoja con 12 post-its y "hoja 1 de 5"; "hoja anterior" deshabilitada en la primera y "hoja siguiente" en la última; recorrer todas las hojas muestra cada jugador del catálogo exactamente una vez; la hoja va en la dirección (`page=2`) y se respeta al cargar `/pizarra?page=2`; `?page=40` muestra "volver a la primera hoja" y al elegirlo vuelve a la hoja 1; activar un post-it con teclado navega a `/jugadores/<id>`; error de red muestra "reintentar" y al reintentar carga la hoja

**Checkpoint**: las historias 1 y 2 funcionan cada una por separado.

---

## Fase 5: Historia 3 - Filtrar por secciones de la cancha, liga y equipo (Prioridad: P1)

**Objetivo**: cancha SVG con zonas-filtro, pestañas de liga de cinta adhesiva y desplegable de
equipo, combinables, reflejados en la dirección, con nota de combinación vacía.

**Prueba independiente**: aplicar cada filtro por separado y combinados y verificar que todos
los post-its cumplen los filtros; recargar `/pizarra?position=DEFENDER&league=LA_LIGA&team=...`
y obtener la misma vista, también sin sesión.

### Implementación de la historia 3

- [ ] T058 [P] [US3] Crear `frontend/src/components/Pitch.tsx` y `frontend/src/components/Pitch.module.css` (research R-3): cancha en tiza con cuatro zonas (arco, defensa, mediocampo, delantera) y la opción "todos"; cada una es `<button type="button" aria-pressed>` con etiqueta accesible ("Filtrar por defensores", "Ver todos") y su dibujo SVG decorativo `aria-hidden`; props `activeZone: PitchZone | null` y `onZoneSelect(zone: PitchZone | null)`; tocar la zona activa llama `onZoneSelect(null)`; sin props ni elementos para jugadores; versión compacta en celular
- [ ] T059 [P] [US3] Crear `frontend/src/components/TapeTab.tsx` y `frontend/src/components/TapeTab.module.css`: pestaña de cinta adhesiva como `<button type="button" aria-pressed>` con `label`, `active` y `onSelect`
- [ ] T060 [P] [US3] Crear `frontend/src/utils/teams.ts` (research R-8, data-model §5) con `deriveTeams(players, league)` (sin repetidos, acotado por liga si la hay, ordenado con `localeCompare(…, 'es')`) y `teamBelongsToLeague(players, team, league)`
- [ ] T061 [US3] Crear `frontend/src/components/TeamSelect.tsx` y `frontend/src/components/TeamSelect.module.css` (clarificación 1): botón en tiza "equipo: <elegido o todos> ▾" con `aria-haspopup="listbox"` y `aria-expanded`; al abrirse muestra `role="listbox"` con `role="option"` y `aria-selected` para "todos" más los equipos recibidos por props; flechas, Enter y Escape; mouse y toque; props `teams`, `value`, `status` (muestra "el DT está pensando..." o error con "reintentar" dentro de la lista), `onOpen` (para disparar la carga del catálogo), `onSelect(team | null)` y `onRetry`
- [ ] T062 [US3] Agregar `describeEmptyCombination(filters: BoardFilters): string` en `frontend/src/utils/filters.ts` (data-model §3): arma "no hay <plural de la posición o 'jugadores'>[ de <liga legible>][ de ese equipo] en la pizarra", p. ej. "no hay arqueros de ese equipo en la pizarra" o "no hay defensores de La Liga en la pizarra"
- [ ] T063 [US3] Ampliar `frontend/src/hooks/useBoardFilters.ts` (research R-6): `setPosition`, `setLeague`, `setTeam` (cada uno quita `page`, RF-019) y `clear()` que deja `/pizarra` sin parámetros; `setLeague` quita `team` si ese equipo no pertenece a la nueva liga según `teamBelongsToLeague` sobre `useCatalog` (si el catálogo no está cargado, llama a `ensureLoaded()` y resuelve al terminar)
- [ ] T064 [US3] Integrar los filtros en `frontend/src/pages/BoardPage.tsx` y `frontend/src/pages/BoardPage.module.css`: `Pitch` con `positionToZone`/`zoneToPosition`, pestañas `TapeTab` "todas" + las cinco ligas con `leagueLabel`, `TeamSelect` debajo de las pestañas con `deriveTeams(catalog.players, filters.league)` y `onOpen = ensureLoaded`; reemplazar la nota de combinación vacía por `describeEmptyCombination(filters)` con la acción "limpiar filtros" (`clear()`); a 360 px la cancha compacta va arriba y los post-its abajo en una columna, sin desplazamiento horizontal

### Tests de la historia 3

- [ ] T065 [P] [US3] Escribir `frontend/tests/components/Pitch.test.tsx`: cada zona es un botón con nombre accesible; la activa tiene `aria-pressed="true"` y "todos" cuando no hay zona; tocar una zona llama `onZoneSelect` con ella; tocar la activa llama `onZoneSelect(null)`; se activa con teclado (Tab + Enter/Espacio); no se dibuja ningún jugador
- [ ] T066 [P] [US3] Escribir `frontend/tests/utils/teams.test.ts`: sin repetidos, orden alfabético en español (acentos), acotado por liga, `teamBelongsToLeague` verdadero y falso
- [ ] T067 [P] [US3] Agregar a `frontend/tests/utils/filters.test.ts` (sin tocar los casos existentes) los casos de `describeEmptyCombination`: solo posición, posición + equipo, posición + liga, solo equipo, solo liga
- [ ] T068 [P] [US3] Escribir `frontend/tests/hooks/useBoardFilters.test.tsx`: cada setter escribe su parámetro con el valor del backend y quita `page`; filtros vacíos no aparecen en la dirección; `clear()` deja la dirección limpia; cambiar a una liga a la que no pertenece el equipo elegido quita `team`; cambiar a una liga a la que sí pertenece lo conserva
- [ ] T069 [US3] Agregar a `frontend/tests/pages/BoardPage.test.tsx` (sin tocar los casos existentes) los casos de la historia 3: tocar "defensa" deja solo defensores, marca la zona activa y vuelve a la hoja 1; tocar otra vez "defensa" o "todos" quita el filtro; elegir "La Liga" deja solo jugadores de La Liga; el desplegable de equipo solo ofrece equipos de la liga elegida, ordenados, y elegir uno filtra y vuelve a la hoja 1; con posición, liga y equipo combinados cada post-it cumple los tres; `/pizarra?position=DEFENDER&league=LA_LIGA&page=2` sin sesión reproduce filtros y hoja; la combinación vacía muestra la descripción y "limpiar filtros" vuelve a la pizarra completa en la hoja 1; `?position=XYZ&page=abc` muestra la pizarra completa sin error; el catálogo completo (`size=50`) no se pide hasta abrir el desplegable de equipo

**Checkpoint**: historias 1, 2 y 3 funcionan cada una por separado.

---

## Fase 6: Historia 4 - Buscador de jugadores por nombre (Prioridad: P1)

**Objetivo**: buscador en tiza en la barra superior de todas las pantallas que sugiere hasta 8
jugadores de todo el catálogo, sin distinguir acentos, usable solo con teclado.

**Prueba independiente**: escribir "mbappe" y ver "Kylian Mbappé" con posición y equipo;
elegirlo con flechas y Enter y llegar a su ficha (`/jugadores/<id>`).

### Implementación de la historia 4

- [ ] T070 [P] [US4] Crear `frontend/src/utils/search.ts` (research R-1, data-model §5): `normalize(text)` (recorta, minúsculas, `normalize('NFD')` y quita marcas diacríticas) y `searchPlayers(players, query)` (`[]` si la consulta normalizada tiene menos de 2 caracteres; "contiene" sobre el nombre normalizado; máximo 8; respeta el orden de entrada)
- [ ] T071 [US4] Crear `frontend/src/components/SearchBox.tsx` y `frontend/src/components/SearchBox.module.css`: combobox accesible (`role="combobox"`, `aria-expanded`, `aria-controls`, `aria-activedescendant`, lista `role="listbox"` con `role="option"`) escrito en tiza; debounce de 200 ms; con menos de 2 letras (sin espacios de los extremos) no muestra sugerencias; cada sugerencia muestra nombre, posición (`positionLabel`) y equipo; sin coincidencias muestra "no hay nadie con ese nombre en la pizarra"; flechas arriba/abajo resaltan, Enter elige la resaltada, Escape cierra y deja el foco en el input; clic/toque elige; props `players`, `status` (carga "el DT está pensando..." o error con "reintentar" dentro del panel), `onFocus` (para `ensureLoaded`), `onRetry` y `onSelect(player)`. No importa router ni servicios
- [ ] T072 [US4] Conectar el buscador en `frontend/src/pages/AppLayout.tsx`: pasa a `TopBar` un `searchSlot` con `SearchBox` alimentado por `useCatalog` (`onFocus = ensureLoaded`, `onRetry = retry`) y `onSelect` que navega a `/jugadores/<id>` sin `state` (vuelve a la pizarra completa) y vacía el texto; no lee ni modifica los filtros de la pizarra

### Tests de la historia 4

- [ ] T073 [P] [US4] Escribir `frontend/tests/utils/search.test.ts`: una letra devuelve `[]`; " m " cuenta como una letra; "mbappe" encuentra "Kylian Mbappé"; mayúsculas y acentos indistintos; tope de 8; orden del catálogo; sin coincidencias devuelve `[]`
- [ ] T074 [P] [US4] Escribir `frontend/tests/components/SearchBox.test.tsx` (con timers simulados para el debounce): una letra no muestra sugerencias; "mbappe" muestra "Kylian Mbappé" con posición y equipo; más de 8 coincidencias muestran 8; "zzz" muestra "no hay nadie con ese nombre en la pizarra"; flechas cambian `aria-activedescendant`; Enter llama `onSelect` con el resaltado; Escape cierra y deja el foco en el input; clic llama `onSelect`; estado de carga y de error con "reintentar"
- [ ] T075 [US4] Agregar a `frontend/tests/pages/BoardPage.test.tsx` (sin tocar los casos existentes) un caso de integración del buscador: con filtros activos en `/pizarra?position=DEFENDER`, buscar un delantero por nombre lo sugiere igual (busca en todo el catálogo), elegirlo con teclado navega a `/jugadores/<id>` y los filtros de la dirección de la pizarra no cambiaron mientras se buscaba

**Checkpoint**: las cuatro historias P1 funcionan cada una por separado.

---

## Fase 7: Historia 5 - Ficha del jugador (Prioridad: P2)

**Objetivo**: `/jugadores/:id` pública con la hoja de scout de solo lectura y "volver a la
pizarra" que restaura filtros y hoja.

**Prueba independiente**: abrir `/jugadores/<id existente>` y ver nombre, posición, equipo y
liga; abrir `/jugadores/999999` y `/jugadores/abc` y ver "jugador no encontrado" con la opción de
volver.

### Implementación de la historia 5

- [ ] T076 [P] [US5] Crear `frontend/src/hooks/usePlayer.ts`: valida que `id` sea un entero positivo (si no, estado "no encontrado" sin pedir nada); sobre `useAsync` llama a `getPlayer(id, signal)`; mapea 404 y 400 a `notFound: true`; expone `{ status, player, notFound, error, retry }`
- [ ] T077 [P] [US5] Crear `frontend/src/components/ScoutSheet.tsx` y `frontend/src/components/ScoutSheet.module.css`: hoja de scout clavada al pizarrón con nombre, posición (`positionLabel`), equipo y liga (`leagueLabel`); recibe el link de vuelta como `backLink: ReactNode`; sin acciones, precio, valor ni puntaje
- [ ] T078 [US5] Crear `frontend/src/pages/PlayerPage.tsx` y `frontend/src/pages/PlayerPage.module.css`: lee `:id` con `useParams` y `location.state?.boardSearch`; arma "volver a la pizarra" como `Link` a `/pizarra${boardSearch ?? ''}` (research R-10); carga -> "el DT está pensando..."; `notFound` -> `ChalkNote` "jugador no encontrado" con el mismo link; error de red -> nota con "reintentar"; éxito -> `ScoutSheet` dentro de `Board`
- [ ] T079 [US5] Registrar en `frontend/src/router/routes.tsx` la ruta `/jugadores/:id` -> `PlayerPage`

### Tests de la historia 5

- [ ] T080 [US5] Escribir `frontend/tests/pages/PlayerPage.test.tsx`: abrir `/jugadores/<id>` sin sesión muestra nombre, posición, equipo y liga legibles; abrir la ficha desde un post-it de `/pizarra?position=DEFENDER&page=2` y elegir "volver a la pizarra" vuelve a esa misma dirección; abrirla directamente y volver lleva a `/pizarra` sin parámetros; `/jugadores/999999` y `/jugadores/abc` muestran "jugador no encontrado" con la opción de volver; la ficha no tiene botones de compra, venta ni similares; error de red muestra "reintentar"

**Checkpoint**: historias 1 a 5 funcionan cada una por separado.

---

## Fase 8: Historia 6 - El vestuario: la cuenta de la persona (Prioridad: P2)

**Objetivo**: completar `/vestuario` con perfil, cambio de contraseña, regeneración de clave con
confirmación y cierre de sesión, más el manejo de sesión vencida.

**Prueba independiente**: entrar al vestuario y ver usuario, correo, rol legible y saldo con dos
decimales; cambiar la contraseña y entrar con la nueva; regenerar la clave confirmando antes;
cerrar sesión y ver la pizarra sin sesión funcionando.

### Implementación de la historia 6

- [ ] T081 [US6] Agregar en `frontend/src/services/authService.ts` `changePassword(req: ChangePasswordRequest): Promise<void>` -> `PUT /auth/me/password` y `regenerateApiKey(): Promise<ApiKeyResponse>` -> `POST /auth/me/api-key`
- [ ] T082 [US6] Completar `frontend/src/pages/LockerRoomPage.tsx` y `frontend/src/pages/LockerRoomPage.module.css`: sección de perfil con usuario, correo, `roleLabel(role)` y saldo con `formatBalance`, sin acción asociada al saldo; formulario "cambiar contraseña" (actual, nueva) sin doble envío, errores con `toFormErrors` junto a cada campo, éxito con `ChalkNote` de confirmación y formulario vacío; "regenerar clave" abre una `ChalkNote` de confirmación que advierte que la clave actual deja de funcionar, con "sí, regenerar" y "cancelar" (cancelar no llama a la API); al confirmar muestra `ApiKeyReveal` con la clave nueva solo en estado local y activa `useLeaveGuard` hasta que se elige "listo"; "cerrar sesión" llama a `logout()` y navega a `/pizarra`; errores de red con "reintentar" sin perder lo ingresado
- [ ] T083 [US6] Verificar en `frontend/src/context/SessionContext.tsx` y `frontend/src/router/RequireSession.tsx` que un 401 durante una acción del vestuario (p. ej. cambiar la contraseña) o el vencimiento de `expiresAt` con el vestuario abierto descartan la sesión con motivo `'expired'` y llevan a `/login` con "tu sesión venció, volvé a entrar" y `from` = `/vestuario`; y que en la pizarra o en una ficha el mismo 401 o vencimiento solo cambia el acceso de la barra a "entrar" sin redirigir. Ajustar lo que falte

### Tests de la historia 6

- [ ] T084 [US6] Escribir `frontend/tests/pages/LockerRoomPage.test.tsx`: con sesión muestra usuario, correo, "Usuario" y saldo con dos decimales, sin botones asociados al saldo; cambiar la contraseña con la actual correcta confirma y vacía el formulario; con la actual incorrecta o una nueva inválida muestra la nota junto al campo; "regenerar clave" + "cancelar" no llama a la API; confirmar muestra la clave nueva con "copiar" y la advertencia, y después de "listo" la clave ya no aparece; "cerrar sesión" lleva a `/pizarra` sin sesión, con la barra en "entrar", y `/vestuario` vuelve a pedir inicio de sesión; un 401 al cambiar la contraseña lleva a `/login` con "tu sesión venció, volvé a entrar" y al entrar vuelve a `/vestuario`; una sesión que vence en la pizarra no redirige

**Checkpoint**: las seis historias funcionan cada una por separado.

---

## Fase 9: Pulido y transversales

**Propósito**: verificaciones que cruzan todas las historias y la definición de terminado.

- [ ] T085 [P] Escribir `frontend/tests/pages/NoPurchaseActions.test.tsx` (research R-4, CE-009): recorre `/pizarra` (con filtros y desplegable abierto), `/jugadores/<id>`, `/login`, `/register` y `/vestuario` (con sesión) y verifica que no hay botones ni links cuyo nombre accesible coincida con `/comprar|vender|sumar|plantel|precio|valor|presupuesto|puntaje|portfolio|orden/i` y que no hay texto de precio ni estrellas
- [ ] T086 [P] Revisar que `fetch` aparece solo en `frontend/src/services/httpClient.ts`, que `frontend/src/components/` no importa `react-router` ni `services/`, que no hay `localStorage` ni `console.*` en `frontend/src/` y que no se creó ningún tipo, hook, servicio ni componente de compra, venta, plantel u órdenes; corregir lo que aparezca
- [ ] T087 [P] Revisar la presentación a 360 px y en escritorio en todas las pantallas (`frontend/src/**/*.module.css`): cancha compacta arriba, post-its en una columna, barra superior con buscador sin desbordar, sin desplazamiento horizontal; foco visible en cada elemento accionable; contrastes de tiza legibles; fidelidad visual al mockup sin reproducir presupuesto, estrellas, imanes ni "+ al equipo"
- [ ] T088 [P] Actualizar `frontend/README.md` con los requisitos (Node 20), los comandos `npm ci`, `npm run dev`, `npm run lint`, `npm test` y `npm run build`, el proxy `/api` -> `:8080` y un enlace a `specs/004-frontend-pizarra-tactica/quickstart.md`
- [ ] T089 Correr desde `frontend/` `npm ci`, `npm run lint`, `npm test` y `npm run build` y confirmar que los cuatro terminan con código 0 y que se genera `frontend/test-results/junit.xml` (definición de terminado)
- [ ] T090 Validar a mano los recorridos de `specs/004-frontend-pizarra-tactica/quickstart.md` (secciones 1 a 3) con `npm run dev` contra `./gradlew bootRun`, incluida la sesión vencida editando `futbolmarket.session` y el error de red con el backend detenido; registrar cualquier desvío como tarea nueva

---

## Dependencias y orden de ejecución

### Dependencias entre fases

- **Setup (fase 1)**: sin dependencias; empieza ya.
- **Fundacional (fase 2)**: depende de la fase 1. **Bloquea todas las historias.**
- **Historias (fases 3 a 8)**: todas dependen solo de la fase 2. Se pueden hacer en paralelo o en orden de prioridad (US1 -> US2 -> US3 -> US4 -> US5 -> US6).
- **Pulido (fase 9)**: depende de las historias que se quieran entregar; T085 necesita todas las pantallas.

### Dependencias entre historias

- **US1 (P1)**: solo fase 2. Crea `RequireSession` y una versión inicial de `LockerRoomPage` para probar la guarda.
- **US2 (P1)**: solo fase 2. Crea `utils/filters.ts`, `useBoardFilters` (solo hoja) y `BoardPage`.
- **US3 (P1)**: amplía archivos de US2 (`BoardPage.tsx`, `useBoardFilters.ts`, `utils/filters.ts`, `BoardPage.test.tsx`), así que se integra después de US2. Sus componentes (`Pitch`, `TapeTab`, `TeamSelect`, `utils/teams.ts`) se pueden hacer en paralelo con US2.
- **US4 (P1)**: solo fase 2 para `search.ts`, `SearchBox` y `AppLayout`. T075 agrega un caso a `BoardPage.test.tsx`, que necesita US2. Navega a `/jugadores/:id`, que existe recién con US5 (antes cae en `*` -> `/pizarra`; el test verifica la dirección, no la ficha).
- **US5 (P2)**: solo fase 2 para la ficha. "Volver a la pizarra" con filtros usa el `state` que pone el post-it de US2; el test T080 que abre desde un post-it necesita US2.
- **US6 (P2)**: completa `LockerRoomPage` y amplía `authService.ts` de US1, así que se integra después de US1.

### Dentro de cada historia

- La implementación va primero y sus tests se escriben en la misma tarea o inmediatamente después (sin TDD), antes de cerrar la historia.
- Utilidades puras y componentes de presentación antes que hooks; hooks antes que páginas; páginas antes que el registro de rutas.
- Al cerrar cada historia, `npm run lint`, `npm test` y `npm run build` deben pasar.

### Oportunidades de paralelismo

- Fase 1: T002 a T009 en paralelo tras T001.
- Fase 2: T010 a T013 en paralelo; T015 y T016 en paralelo tras T014; T020, T022 y T023 en paralelo; T027 en paralelo con todo lo de `src/`; T031 a T033 en paralelo tras T029-T030.
- Una vez terminada la fase 2: US1, US2, US4 (salvo T075) y US5 (salvo T080) pueden avanzar en paralelo con personas distintas; US3 y US6 en paralelo con las demás en sus archivos nuevos.

---

## Ejemplos de paralelismo por historia

### Historia 1

```text
Tarea: "T037 Crear frontend/src/utils/formErrors.ts"
Tarea: "T038 Crear frontend/src/components/ApiKeyReveal.tsx"
Tarea: "T039 Crear frontend/src/hooks/useLeaveGuard.ts"
# luego, ya con las páginas:
Tarea: "T044 tests/utils/formErrors.test.ts"
Tarea: "T045 tests/pages/LoginPage.test.tsx"
Tarea: "T046 tests/pages/RegisterPage.test.tsx"
```

### Historia 2

```text
Tarea: "T047 Crear frontend/src/components/PostIt.tsx"
Tarea: "T048 Crear frontend/src/components/Pager.tsx"
Tarea: "T049 Crear frontend/src/utils/filters.ts"
# tests:
Tarea: "T054 tests/components/PostIt.test.tsx"
Tarea: "T055 tests/utils/filters.test.ts"
Tarea: "T056 tests/hooks/usePlayers.test.tsx"
```

### Historia 3

```text
Tarea: "T058 Crear frontend/src/components/Pitch.tsx"
Tarea: "T059 Crear frontend/src/components/TapeTab.tsx"
Tarea: "T060 Crear frontend/src/utils/teams.ts"
# tests:
Tarea: "T065 tests/components/Pitch.test.tsx"
Tarea: "T066 tests/utils/teams.test.ts"
Tarea: "T067 casos nuevos en tests/utils/filters.test.ts"
Tarea: "T068 tests/hooks/useBoardFilters.test.tsx"
```

### Historia 4

```text
Tarea: "T070 Crear frontend/src/utils/search.ts"
# tests:
Tarea: "T073 tests/utils/search.test.ts"
Tarea: "T074 tests/components/SearchBox.test.tsx"
```

### Historia 5

```text
Tarea: "T076 Crear frontend/src/hooks/usePlayer.ts"
Tarea: "T077 Crear frontend/src/components/ScoutSheet.tsx"
```

### Historia 6

```text
# T081 -> T082 -> T083 -> T084 son secuenciales (mismos archivos o dependencia directa)
```

---

## Estrategia de implementación

### Primero el MVP (historia 1 + historia 2)

1. Fase 1: Setup.
2. Fase 2: Fundacional (bloquea todo).
3. Fase 3: Historia 1 (acceso).
4. Fase 4: Historia 2 (pizarra): sin ella la historia 1 lleva a una ruta que todavía redirige; juntas son el primer incremento demostrable.
5. **Parar y validar**: `npm test`, `npm run lint`, `npm run build` y el recorrido "Pizarra sin sesión" + "Acceso" del quickstart.

### Entrega incremental

1. Setup + Fundacional -> base lista (CI corriendo).
2. US1 + US2 -> MVP: registrarse, entrar y recorrer la pizarra.
3. US3 -> filtros por cancha, liga y equipo en la dirección.
4. US4 -> buscador en la barra superior.
5. US5 -> ficha del jugador con "volver a la pizarra".
6. US6 -> vestuario completo.
7. Fase 9 -> verificación sin compras, presentación a 360 px y definición de terminado.

### Estrategia con equipo

1. Todo el equipo completa Setup + Fundacional.
2. Después:
   - Persona A: US1 y luego US6 (comparten `authService` y `LockerRoomPage`).
   - Persona B: US2 y luego US3 (comparten `BoardPage` y `useBoardFilters`).
   - Persona C: US4 y luego US5.
3. Cada historia se integra y se prueba sola.

---

## Notas

- [P] = archivos distintos, sin dependencias pendientes.
- La etiqueta [USn] vincula la tarea con su historia para trazabilidad.
- Ninguna tarea toca `backend/` ni `.github/workflows/backend-ci.yml`.
- No se modifica ni borra un test existente sin un "sí" explícito del equipo; ampliar un archivo de test significa solo agregar casos.
- Commit al cerrar cada tarea o grupo lógico; parar en cada checkpoint para validar la historia.
