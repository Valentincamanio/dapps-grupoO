# Contratos: el frontend consume los existentes

**Rama**: `004-frontend-pizarra-tactica` | **Fecha**: 2026-10-07 | **Plan**: [../plan.md](../plan.md)

Esta feature no define contratos nuevos ni modifica los existentes (RF-004). El frontend
consume exactamente estas operaciones:

| Operación | Uso en el frontend | Función en `services/` | Contrato vigente |
|---|---|---|---|
| `POST /auth/register` | Registro (historia 1) | `authService.register` | [`001-auth-usuarios/contracts/auth-api.yaml`](../../001-auth-usuarios/contracts/auth-api.yaml) |
| `POST /auth/login` | Inicio de sesión (historia 1) | `authService.login` | ídem |
| `GET /auth/me` | Perfil: vestuario, saldo en la barra, verificar sesión al recargar | `authService.getProfile` | ídem |
| `PUT /auth/me/password` | Cambiar contraseña (historia 6) | `authService.changePassword` | ídem |
| `POST /auth/me/api-key` | Regenerar clave (historia 6) | `authService.regenerateApiKey` | ídem |
| `GET /players` | Hoja de la pizarra (`size=12`) y catálogo completo (`size=50`) | `playerService.getPlayers` | [`002-catalogo-jugadores/contracts/players-api.yaml`](../../002-catalogo-jugadores/contracts/players-api.yaml) |
| `GET /players/{id}` | Ficha (historia 5) | `playerService.getPlayer` | ídem |
| Formato `ApiError` | Todas las notas de error | `httpClient` | ídem (compartido por 001 y 002) |

[`003-monolito-por-capas/contracts/api-sin-cambios.md`](../../003-monolito-por-capas/contracts/api-sin-cambios.md)
confirma que estos contratos siguen vigentes después de la reestructuración del backend.

## Prefijo `/api` (solo del lado del frontend)

El frontend llama a `/api/<ruta del contrato>` (por ejemplo `/api/players?size=12`). En
desarrollo, el proxy de Vite reenvía a `http://localhost:8080` y quita el prefijo, así que el
backend recibe la ruta tal cual figura en el contrato. No es un cambio de contrato ni requiere
CORS en el backend (regla de la sección Frontend de la constitución). Los handlers de MSW en
`frontend/tests/mocks/handlers.ts` responden en `/api/...` con las formas de estos contratos.

## Credenciales

El frontend solo usa el token de sesión (`Authorization: Bearer <token>`). No envía nunca
`X-API-Key`: la clave de API es para clientes externos y la interfaz solo la muestra una vez.
