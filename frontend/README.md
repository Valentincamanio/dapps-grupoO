# Frontend

Pizarra táctica de FutbolMarket: React 18 + Vite + TypeScript.

## Requisitos

- Node 20 o superior
- Backend corriendo en `http://localhost:8080` (`./gradlew bootRun` desde la raíz del repo)

## Comandos

Desde `frontend/`:

| Comando | Qué hace |
| --- | --- |
| `npm ci` | Instala las dependencias exactas del `package-lock.json` |
| `npm run dev` | Servidor de desarrollo con recarga en caliente |
| `npm run lint` | ESLint sobre todo el proyecto |
| `npm test` | Tests con Vitest (genera `test-results/junit.xml`) |
| `npm run build` | Chequeo de tipos y build de producción en `dist/` |

## Proxy a la API

En desarrollo, Vite reenvía `/api` a `http://localhost:8080`, así que el navegador siempre habla con el mismo origen y no hace falta configurar CORS.

## Más información

Recorridos de validación manual y detalles de la feature: [`specs/004-frontend-pizarra-tactica/quickstart.md`](../specs/004-frontend-pizarra-tactica/quickstart.md).
