# Frontend

La Pizarra, el front de FutbolMarket: React 18 + Vite + TypeScript.

## Requisitos

- Node 20 o superior
- Backend corriendo en `http://localhost:8080` (`./gradlew bootRun` desde `backend/`)
- `FOOTBALL_DATA_TOKEN` definido al arrancar el backend: el catálogo de jugadores se carga desde Football-Data.org y, sin token, la pizarra se ve vacía. Ver el [README de la raíz](../README.md)

## Comandos

Desde `frontend/`:

| Comando | Qué hace |
| --- | --- |
| `npm ci` | Instala las dependencias exactas del `package-lock.json` |
| `npm run dev` | Servidor de desarrollo con recarga en caliente |
| `npm run lint` | ESLint sobre todo el proyecto |
| `npm test` | Tests con Vitest (genera `test-results/junit.xml`) |
| `npm run build` | Chequeo de tipos y build de producción en `dist/` |

## Pantallas

La barra superior tiene los grupos de pantallas y cada grupo, sus pestañas. Las direcciones son las mismas de siempre. Los jugadores no tienen enlace propio en la barra: el logo "La Pizarra" lleva a `/pizarra`. A la izquierda del saldo está el botón de tema (claro u oscuro); arranca siguiendo al sistema y lo elegido queda guardado en el navegador.

| Grupo | Pestaña | Ruta | Qué muestra | Acceso |
| --- | --- | --- | --- | --- |
| Jugadores | Todos | `/pizarra` | Los potenciados de la semana (si hay) y los jugadores en tarjetas, con el buscador y sus filtros | Público |
| Jugadores | Ranking | `/ranking` | El ranking por cotización, con filtros de liga, equipo y posición | Público |
| Jugadores | Mercado | `/mercado` | Las órdenes con tokens pendientes | Público |
| Mi equipo | Mis jugadores | `/portfolio` | El patrimonio y las posiciones, hoy o a una fecha | Solo `USER` |
| Mi equipo | Órdenes | `/ordenes` | Las órdenes propias, con la cancelación. Al entrar se ven las abiertas; el filtro (`?estado=ejecutadas`, `canceladas` o `todas`) muestra las demás | Solo `USER` |
| Mi equipo | Movimientos | `/operaciones` | El historial de operaciones | Solo `USER` |
| Administración | Estrategias | `/admin/estrategias` | Las estrategias con sus versiones: crear, editar, activar (eligiendo si se recotiza ahora o la semana siguiente) y eliminar, con confirmación | Solo `ADMIN` |
| Administración | Recálculo | `/admin/recalculo` | El disparo manual del recálculo y su informe, y las sincronizaciones manuales del catálogo (Football-Data.org) y de las estadísticas (WhoScored), de las cinco ligas o de una | Solo `ADMIN` |
| Administración | Simulación | `/admin/simulacion` | La simulación de una estrategia guardada o de unos parámetros, sin guardar nada; con el buscador, la de un solo jugador | Solo `ADMIN` |
| Administración | Auditoría | `/admin/auditoria` | Cada orden creada, operación y cancelación del mercado, con su autor y el antes y después de cada parte, filtrable por tipo, jugador, usuario y fechas | Solo `ADMIN` |
| Administración | Potenciados | `/admin/potenciados` | Los 4 potenciados de cada semana y su porcentaje; la semana en curso se cambia con confirmación y las cerradas quedan como registro | Solo `ADMIN` |

`/vestuario` es la cuenta (con sesión) y se abre desde el avatar.

`/jugadores/:id` es la ficha: precio, evolución, operar y órdenes pendientes. Abierta desde otra pantalla se ve como panel encima de ella, que conserva sus filtros y su hoja; abierta directamente, es una pantalla más.

`/admin` lleva a `/admin/estrategias`. El enlace "Administración" del encabezado solo aparece para
un usuario con rol `ADMIN`; un usuario común que entra por la dirección ve el aviso de falta de
permiso, y sin sesión se pasa por `/login`. Los endpoints que usa cada pantalla están en el
[README de la raíz](../README.md#cotizaciones-y-estrategias-de-valuación).

En los tests, la API se simula con MSW (`tests/mocks/`): `handlers.ts` para el catálogo y la sesión, y
`quoteHandlers.ts` para las cotizaciones, las estrategias, el recálculo y la simulación, y
`boostHandlers.ts` para los potenciados.

## Proxy a la API

En desarrollo, Vite reenvía `/api` a `http://localhost:8080`, así que el navegador siempre habla con el mismo origen y no hace falta configurar CORS.

## Más información

Recorridos de validación manual y detalles de cada feature:

- Pizarra, ficha y sesión: [`specs/004-frontend-pizarra-tactica/quickstart.md`](../specs/004-frontend-pizarra-tactica/quickstart.md)
- Cotizaciones, ranking y administración: [`specs/007-cotizacion-estrategias/quickstart.md`](../specs/007-cotizacion-estrategias/quickstart.md)
