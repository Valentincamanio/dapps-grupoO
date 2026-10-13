# Research: Frontend "Pizarra táctica"

**Rama**: `004-frontend-pizarra-tactica` | **Fecha**: 2026-10-07 | **Plan**: [plan.md](plan.md)

El contexto técnico no dejó ningún `NEEDS CLARIFICATION`: el stack lo fija la constitución 2.1.0
y la spec quedó clarificada el 2026-10-07. Esta fase documenta las decisiones no obvias, con
sus alternativas descartadas.

---

## R-1. Búsqueda por nombre en el cliente

**Decisión**: el buscador filtra en el navegador sobre el catálogo completo que entrega
`useCatalog`. `utils/search.ts` normaliza (recorta extremos, pasa a minúsculas, quita acentos
con `normalize('NFD')` y elimina las marcas diacríticas), exige 2 o más caracteres, filtra por
"contiene" sobre el nombre normalizado y corta en 8 resultados. Las sugerencias conservan el
orden del catálogo (el del backend), así el resultado es estable.

**Razón**: `GET /players` no acepta un filtro por nombre y RF-016 de `002-catalogo-jugadores`
excluye esa búsqueda del sistema (no de la interfaz). RF-025 de esta spec pide justamente
resolverlo sobre el catálogo completo. Con 50-60 jugadores, el catálogo entra en dos pedidos de
`size=50` y el filtrado en memoria es instantáneo (CE-006).

**Alternativas descartadas**:
- *Agregar `?name=` al backend*: viola RF-004 (no se agregan capacidades) y RF-016 de 002.
- *Buscar solo en la hoja visible*: no cumple "busca siempre en todo el catálogo" (historia 4, escenario 7).
- *Librería de búsqueda difusa (Fuse.js)*: dependencia nueva para un "contiene" sin acentos que
  son tres líneas; además la spec pide "contiene", no coincidencia aproximada.

## R-2. Carga diferida y cacheada del catálogo

**Decisión**: `useCatalog` no pide nada al montarse. Expone `ensureLoaded()`, que la primera vez
pide `GET /players?size=50&page=0`, sigue con `page=1, 2, ...` mientras `hasNext` sea `true` y
guarda el resultado en una caché a nivel de módulo (una sola promesa compartida, así dos llamadas
simultáneas no duplican pedidos). La disparan el foco/escritura en el buscador y la apertura del
desplegable de equipo. Si falla, la caché se descarta para que "reintentar" vuelva a pedir. La
caché dura lo que la pestaña (memoria), no se persiste.

**Razón**: la pizarra no necesita el catálogo completo (pide su hoja de 12), así que cargarlo al
inicio duplicaría el tráfico de quien solo mira la pizarra. El catálogo es público y estático
durante la sesión (el supuesto de 002 es un catálogo semilla), por lo que una sola descarga
alcanza. `size=50` es el máximo del contrato.

**Alternativas descartadas**:
- *Cargarlo al iniciar la app*: tráfico innecesario para quien no busca ni filtra por equipo.
- *Volver a pedirlo en cada búsqueda*: lento y sin beneficio con un catálogo estático.
- *Guardarlo en `sessionStorage`/`localStorage`*: la constitución reserva `sessionStorage` para
  la sesión y el pedido prohíbe `localStorage`; no aporta nada frente a la memoria.
- *React Query/SWR para la caché*: dependencia nueva para un único recurso cacheado.

## R-3. La cancha es un filtro, no un plantel

**Decisión**: `Pitch` dibuja la cancha en SVG con cuatro zonas (arco, defensa, mediocampo,
delantera) y una opción "todos". Cada zona es un `<button type="button" aria-pressed>` con
etiqueta accesible ("Filtrar por defensores"); la forma visual de la zona va dentro del botón
como SVG decorativo (`aria-hidden`). Recibe `activeZone` y `onZoneSelect(zone | null)`;
`onZoneSelect` con la zona ya activa equivale a "todos" (clarificación 5). No recibe jugadores
ni tiene prop para dibujarlos.

**Razón**: RF-003 y RF-015 prohíben ubicar o arrastrar jugadores en la cancha. Usar `button`
nativo da teclado y foco gratis (RF-036) y `aria-pressed` comunica la zona activa (RF-037).

**Alternativas descartadas**:
- *Reproducir los imanes del mockup*: es armado de equipos, fuera de alcance.
- *Zonas como `<g onClick>` o `<rect role="button">` dentro del SVG*: hay que reimplementar foco,
  Enter/Espacio y semántica; la constitución pide `button` o link.
- *`role="radiogroup"`*: el escenario 2 de la historia 3 pide que tocar la zona activa la
  desactive, que no es el comportamiento de un radio.

## R-4. Sin compras hasta que exista el backend de órdenes

**Decisión**: no se crea ningún hook, servicio, tipo ni componente de compra, venta, plantel,
órdenes o portfolio, ni siquiera deshabilitado o detrás de una bandera. El saldo se muestra en
el vestuario y en la barra superior, sin acción. El vestuario (`LockerRoomPage`) y
`authService` quedan como el lugar donde se va a enchufar la futura feature de compra y venta.
Un test de páginas (`NoPurchaseActions.test.tsx`) recorre todas las pantallas y verifica que no
hay botones ni links cuyo nombre accesible coincida con comprar/vender/sumar/plantel/precio.

**Razón**: RF-002, RF-003 y CE-009. No existe ningún endpoint de órdenes ni de cotizaciones, y
la constitución prohíbe inventar APIs. Código muerto "para después" agrega superficie sin uso.

**Alternativas descartadas**:
- *Botones deshabilitados "próximamente"*: RF-002 los prohíbe explícitamente.
- *Tipos `Order`/`Quote` adelantados*: inventarían campos que ningún contrato define.

## R-5. Sesión, 401 y rutas privadas

**Decisión**:
- `SessionContext` guarda `{ token, expiresAt }` en memoria y en `sessionStorage` (vía
  `services/sessionStorage.ts`) y el perfil de `GET /auth/me`. Al iniciar, si hay sesión
  guardada y vencida (`expiresAt <= ahora`), la descarta sin llamar a la API; si está vigente,
  pide el perfil.
- `httpClient` lee el token desde un "token provider" que registra el `SessionContext` (función
  `setAuthHandlers({ getToken, onUnauthorized })`), así el cliente no importa React.
- Agrega `Authorization: Bearer <token>` solo si hay token. Ante un 401 **de una llamada que
  llevaba token**, invoca `onUnauthorized()`: el contexto limpia la sesión y marca el motivo
  `"expired"`. Un 401 sin token (por ejemplo, `POST /auth/login` con credenciales malas) se
  devuelve como `ApiError` común, sin tocar la sesión.
- La redirección la hace `router/RequireSession.tsx`, que envuelve solo a `/vestuario`: sin
  sesión, navega a `/login` con `state = { from, reason }` y el aviso correspondiente
  ("para entrar al vestuario tenés que iniciar sesión" o "tu sesión venció, volvé a entrar").
  En la pizarra o en una ficha, perder la sesión no redirige: la barra pasa a mostrar "entrar".
- Al entrar, `LoginPage` navega a `state.from` si existe (incluye `search`, así vuelve con los
  mismos filtros y hoja) o a `/pizarra`.

**Razón**: el pedido planteaba redirigir a `/login` ante cualquier 401 y proteger todas las
rutas privadas; la spec clarificada hace públicas la pizarra, el buscador y la ficha (RF-010,
RF-011, CE-013) y `POST /auth/login` usa 401 para credenciales inválidas. Separar "limpiar" (en
el cliente) de "redirigir" (en la ruta privada) cumple ambas cosas. El `state` de la navegación
no viaja en la URL, así que no expone nada (RF-012).

**Alternativas descartadas**:
- *Redirección global en `httpClient` con `window.location`*: rompe la pizarra pública y el
  login con credenciales malas, y recarga la app perdiendo estado.
- *`?redirect=` en la URL*: funciona, pero `state` evita URLs largas y no hace falta compartirlo.
- *Decodificar el JWT para leer `exp`*: el login ya devuelve `expiresAt`.
- *Token en `localStorage`*: lo prohíben la constitución y el pedido.

## R-6. Filtros en la dirección de la página

**Decisión**: `useBoardFilters` usa `useSearchParams` con los nombres de la clarificación 4:
`position`, `league`, `team`, `page` (1 en adelante). `utils/filters.ts` parsea con validación:
`position` y `league` solo si pertenecen al enum, `team` recortado y no vacío, `page` entero
`>= 1`; cualquier valor inválido se ignora (caso límite de la spec). Serializa omitiendo los
filtros vacíos y `page=1`. `setFilter` cambia un filtro y quita `page` (vuelve a la primera
hoja, RF-019); `setPage` cambia solo la hoja; `clear()` deja la dirección limpia. Cambiar de liga
con un equipo elegido quita `team` si ese equipo no pertenece a la nueva liga (se consulta el
catálogo con `useCatalog`; si el equipo vino por la dirección y el catálogo no está cargado, se
carga en ese momento).

**Razón**: RF-018 y CE-004 (recargar o compartir reproduce la vista). Usar los nombres del
backend evita una tabla de traducción en la URL. El pedido mencionaba `zona/liga/equipo/pagina`
y "página 0", pero la clarificación de la spec manda.

**Alternativas descartadas**:
- *Parámetros en español (`zona=defensa`)*: contradicen la clarificación 4 y RF-018.
- *Estado en React sin URL*: no sobrevive a recargar ni se puede compartir.
- *`page` base 0 en la URL*: la spec la cuenta desde 1, como la ve la persona.

## R-7. Pedido de la hoja de la pizarra

**Decisión**: `usePlayers(filters)` llama a `GET /players?position=&league=&team=&page=<page-1>&size=12`
cada vez que cambian los filtros, cancela el pedido anterior con `AbortController` y expone
`{ status, data, error, retry }`. El filtrado y la paginación son del backend, así que cada hoja
viene completa y `totalPages` alimenta "hoja X de N". Si la hoja está fuera de rango (contenido
vacío con `totalElements > 0`), la página muestra la nota de pizarra vacía con "volver a la
primera hoja"; si `totalElements == 0`, muestra la nota de combinación sin jugadores con
"limpiar filtros".

**Razón**: RF-013, RF-017 y los casos límite de hoja fuera de rango. Pedir solo la hoja visible
es lo que la API ofrece; el catálogo completo queda para el buscador y los equipos.

**Alternativas descartadas**:
- *Filtrar y paginar en el cliente sobre `useCatalog`*: obliga a cargar todo el catálogo para ver
  la primera hoja y duplica lógica del backend.

## R-8. Lista de equipos

**Decisión**: `utils/teams.ts` (`deriveTeams(players, league?)`) devuelve los nombres de equipo
sin repetir, ordenados con `localeCompare(…, 'es')`, opcionalmente acotados a una liga.
`TeamSelect` es un botón "equipo: todos ▾" que abre una lista de opciones (`role="listbox"` con
`role="option"`, flechas, Enter, Escape) e incluye "todos". La lista se arma desde el catálogo
diferido; mientras carga, muestra "el DT está pensando...".

**Razón**: no existe un endpoint de equipos y RF-016 pide los equipos presentes en el catálogo,
acotados por liga y ordenados.

**Alternativas descartadas**:
- *Lista de equipos fija en el front*: se desincroniza del catálogo semilla.
- *`<select>` nativo*: no admite la estética de tiza que fija la clarificación 1.

## R-9. Componentes sin router ni API

**Decisión**: los componentes de `components/` reciben datos y callbacks. `PostIt` recibe el
`href` y un `LinkComponent` opcional (por defecto `<a>`); la página le pasa el `Link` de React
Router con el `state` para "volver a la pizarra". `TopBar` recibe `searchSlot`, `balance` y el
link de cuenta como props; el `SearchBox` que se le pasa ya viene conectado por `AppLayout`.

**Razón**: regla de la constitución ("los componentes de `components/` no conocen la API ni el
router"). Así los tests de componentes no necesitan router ni MSW.

**Alternativas descartadas**: importar `Link` o `useNavigate` dentro de `components/`.

## R-10. Volver a la pizarra y clave de API mostrada una vez

**Decisión**:
- El link del post-it navega a `/jugadores/:id` con `state = { boardSearch: location.search }`.
  `PlayerPage` arma "volver a la pizarra" con `/pizarra${boardSearch ?? ''}` (RF-027). Desde el
  buscador o una dirección directa, no hay `state` y vuelve a `/pizarra`.
- `ApiKeyReveal` muestra la clave en un campo de solo lectura seleccionable, botón "copiar"
  (`navigator.clipboard.writeText`; si falla, avisa que la copia automática no funcionó y deja
  la clave seleccionada) y la advertencia fija. La clave vive solo en el estado local de la
  página que la recibió; nunca entra al contexto ni a `sessionStorage`.
- `useLeaveGuard(active)` usa `useBlocker` de React Router para pedir confirmación al navegar
  dentro de la app y `beforeunload` para recargar o cerrar mientras la clave está visible.
- Tras el registro, "continuar" llama a `login` con las credenciales que siguen en el estado del
  formulario (no se guardan en ningún otro lado) y luego las descarta.

**Razón**: RF-007, RF-008, RF-027, RF-032 y los casos límite de copiar y recargar. `useBlocker`
requiere un data router, por eso `createBrowserRouter`.

**Alternativas descartadas**:
- *`window.confirm` para regenerar la clave*: no respeta la estética; se usa una `ChalkNote` de
  confirmación con "sí, regenerar" y "cancelar".
- *Guardar `boardSearch` en el contexto*: se pierde en una recarga; el `state` del historial
  sobrevive.

## R-11. Dependencias

Todas se agregan con aviso, como pide la constitución:

| Paquete | Tipo | Justificación |
|---|---|---|
| `react`, `react-dom` 18 | runtime | Stack de la constitución. |
| `react-router` 7 | runtime | Stack de la constitución. La v7 soporta React 18; se usa en modo librería con `createBrowserRouter` por `useBlocker` (R-10). |
| `vite`, `@vitejs/plugin-react` | dev | Stack de la constitución; el plugin es el oficial para JSX y Fast Refresh. |
| `typescript` | dev | Stack de la constitución. |
| `vitest`, `jsdom` | dev | Vitest es el runner de la constitución; `jsdom` es el entorno DOM que necesita RTL. |
| `@testing-library/react`, `@testing-library/user-event`, `@testing-library/jest-dom` | dev | RTL es de la constitución; `user-event` simula teclado real (flechas, Enter, Escape) que piden los tests de accesibilidad; `jest-dom` da matchers como `toHaveAttribute('aria-pressed')`. |
| `msw` 2 | dev | Stack de la constitución. |
| `eslint`, `@eslint/js`, `typescript-eslint`, `eslint-plugin-react-hooks` | dev | ESLint es de la constitución; los otros son las reglas mínimas para TS y para las reglas de hooks. |
| `eslint-plugin-jsx-a11y` | dev | Controla en el lint que todo lo clickeable sea `button` o link (regla de accesibilidad de la constitución). |
| `globals` | dev | Declara los globales de navegador y Node en el flat config de ESLint. |

No se agregan librerías de UI, de estado, de fetching, de formularios ni de iconos.

## R-12. Versión de Node

**Decisión**: Node 20, como fija el pedido, tanto en el CI como en `package.json` (`engines`).

**Nota de riesgo**: Node 20 terminó su soporte LTS en abril de 2026. Si una versión vigente de
Vite o Vitest exige una mayor al implementar, se fija la última versión compatible con Node 20
y se avisa al equipo, o se propone pasar a Node 22 LTS en un cambio aparte.

**Alternativas descartadas**: subir a Node 22 por cuenta propia (contradice la decisión del pedido).

## R-13. Rutas en español

**Decisión**: las rutas visibles son `/pizarra`, `/jugadores/:id` y `/vestuario`; `/login` y
`/register` quedan como en el pedido.

**Razón**: el Principio VII pide identificadores de código en inglés; las rutas de la interfaz
son texto visible para la persona (RF-005), no endpoints de la API. Los nombres de componentes
y archivos sí van en inglés (`BoardPage`, `LockerRoomPage`).

## R-14. Cliente HTTP y URLs relativas en los tests

**Decisión**: `httpClient` arma la URL con `new URL('/api' + path, window.location.origin)` y
serializa los query params con `URLSearchParams`, omitiendo los vacíos. Parsea el cuerpo de
error como `ApiError`; si el cuerpo no es JSON o hay un error de red, lanza un `ApiError`
sintético con `status: 0` y el mensaje "no se pudo conectar con la pizarra" para que las
páginas muestren la nota con "reintentar". Un `204` devuelve `undefined`.

**Razón**: en Node (Vitest), `fetch` no resuelve URLs relativas; anclarla a `location.origin`
funciona igual en el navegador (detrás del proxy) y en jsdom, donde MSW intercepta
`http://localhost:3000/api/...`.

**Alternativas descartadas**: variable de entorno con la URL del backend (agrega CORS, que la
constitución descarta).

## R-15. Tests y reporte

**Decisión**: Vitest con `environment: 'jsdom'`, `setupFiles: ['tests/setup.ts']`,
`include: ['tests/**/*.test.{ts,tsx}']` y `reporters: ['default', 'junit']` con
`outputFile: { junit: 'test-results/junit.xml' }`. `tests/setup.ts` levanta el servidor de MSW
con `onUnhandledRequest: 'error'`, limpia `sessionStorage` y resetea la caché de `useCatalog`
entre tests (los tests no comparten estado). Los handlers de MSW filtran y paginan el catálogo de
prueba igual que el contrato, así los tests de páginas validan la combinación real de filtros.
`frontend/test-results/` se agrega al `.gitignore`.

**Razón**: el CI publica el reporte como artifact, igual que el backend. `onUnhandledRequest:
'error'` hace fallar cualquier pedido no previsto (por ejemplo, un `/players` disparado por
`useCatalog` antes del primer uso).
