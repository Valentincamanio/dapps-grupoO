# Contrato: la API no cambia

**Rama**: `003-monolito-por-capas` | **Fecha**: 2026-10-03 | **Plan**: [../plan.md](../plan.md)

Esta feature no define interfaces nuevas. El contrato vigente sigue siendo el de las features
anteriores, y esta feature se compromete a no alterarlo (FR-001 a FR-004):

| Operaciones | Contrato vigente |
|---|---|
| `POST /auth/register`, `POST /auth/login`, `GET /auth/me`, `PUT /auth/me/password`, `POST /auth/me/api-key` | [`specs/001-auth-usuarios/contracts/auth-api.yaml`](../../001-auth-usuarios/contracts/auth-api.yaml) |
| `GET /players`, `GET /players/{id}` y el formato `ApiError` | [`specs/002-catalogo-jugadores/contracts/players-api.yaml`](../../002-catalogo-jugadores/contracts/players-api.yaml) |
| `GET /actuator/health` | Actuator, sin cambios de configuración |

`specs/001-auth-usuarios/contracts/configuration.md` sigue vigente: las claves
`futbolmarket.auth.*` y `futbolmarket.security.jwt.*` no cambian.
`specs/001-auth-usuarios/contracts/shared-integration.md` queda obsoleto: la constitución 2.0.0
prohíbe los contratos de integración entre contextos y entre features.

## Cómo se verifica

1. **Suite de tests**: los tests e2e e IT existentes cubren cada operación, sus casos felices y
   sus errores 400, 401, 403, 404 y 409. Se ejecutan sin cambios (SC-001, SC-003).
2. **Documento OpenAPI**: el JSON de `/v3/api-docs` generado antes y después del cambio tiene
   que ser idéntico, salvo el orden de claves si springdoc lo altera. Si son iguales, también lo
   son las rutas, los métodos, los esquemas, las secciones (tags) y los requisitos de seguridad
   (SC-006). El procedimiento está en [../quickstart.md](../quickstart.md).

Cualquier diferencia en cualquiera de las dos verificaciones es un defecto de esta feature.
