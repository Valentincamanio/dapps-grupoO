# Contrato de integración con `shared/`

**Rama**: `001-auth-usuarios` | **Fecha**: 2026-09-18 | **Plan**: [../plan.md](../plan.md)

El paquete `shared/` (el `@RestControllerAdvice` y la clase base de excepciones) lo escribe la
feature de catálogo (`002-catalogo-jugadores`). Esta feature **no lo crea**, para no generar
conflictos de merge. Este documento dice qué necesita auth de `shared/` y qué garantiza auth a
cambio. Está pensado para compartirlo con quien implementa catálogo.

Secuencia acordada el 2026-09-18: auth implementa todo lo que no depende de `shared/` y se
integra al final, rebaseando sobre `develop` una vez mergeado catálogo.

## 1. Formato de error

Es el `ApiError` del contrato de catálogo (`specs/002-catalogo-jugadores/contracts/players-api.yaml`),
sin cambios:

| Campo | Tipo | Regla |
|---|---|---|
| `timestamp` | string (date-time, ISO-8601) | Momento del error. |
| `status` | integer | Código HTTP. |
| `error` | string | Frase estándar del status (`Bad Request`, `Unauthorized`, `Forbidden`, `Conflict`...). |
| `message` | string | Mensaje en español, sin detalles internos ni stack traces. |
| `path` | string | Ruta del request. |
| `violations` | array, opcional | Solo en errores de validación. Cada ítem tiene `field` y `message`, y `rejectedValue` es opcional (ver punto 4). |

Los 401 y 403 de la cadena de filtros los escribe `security/` con esta misma forma, porque el
advice no los ve (ver punto 5).

## 2. Clase base de excepciones: requisito

Las excepciones de dominio de auth viven en `auth/modelo/exception/`, y la constitución (principio I)
prohíbe que el modelo importe `org.springframework`. Por eso **la clase base de `shared/`
tiene que poder extenderse sin tipos de Spring** en sus constructores o métodos abstractos.
Por ejemplo, no puede exigir un `HttpStatus`. Sirve cualquiera de estas dos formas, y la elige
quien escribe `shared/`:

- **Bases por categoría**: por ejemplo, una para 400, otra para 401, otra para 404 y otra para
  409, cada una con un constructor `(String message)`. El advice mapea cada categoría a su
  status. Es la forma más simple para ambos lados.
- **Una sola base**: con el status expresado como `int` o como un enum propio de `shared/`, no
  de Spring.

Mientras tanto, las excepciones de auth extienden `RuntimeException`. En la integración se
cambia una línea por clase.

## 3. Mapeo que auth necesita del advice

| Excepción (`auth/modelo/exception/`) | Status | `message` |
|---|---|---|
| `DuplicateUsernameException` | 409 | El de la excepción |
| `DuplicateEmailException` | 409 | El de la excepción |
| `DuplicateUsernameAndEmailException` | 409 | El de la excepción |
| `InvalidCredentialsException` | 401 | El de la excepción. Es idéntico para un usuario inexistente y para una contraseña incorrecta. |
| `InvalidPasswordChangeException` | 400 | El de la excepción |
| `InvalidUserDataException` | 400 | El de la excepción |

Con bases por categoría, este mapeo sale solo y el advice no necesita conocer las clases de
auth.

## 4. Errores de request (400)

- `MethodArgumentNotValidException` responde 400 con `violations` (`field` y `message`). Los
  mensajes ya vienen en español desde las anotaciones de los DTO de auth.
- **Crítico: sin `rejectedValue` en campos sensibles.** El advice nunca devuelve el valor
  rechazado de `password`, `currentPassword` ni `newPassword`. Si lo hiciera, un 400 de
  registro devolvería la contraseña y se violarían FR-009 y SC-003 del spec de auth. La opción
  más simple es no incluir `rejectedValue` nunca. Si se incluye, hay que excluir esos campos.
- `HttpMessageNotReadableException` (JSON mal formado) responde 400 con un mensaje genérico en
  español, sin repetir el cuerpo recibido.

## 5. Lo que el advice no hace

- No maneja `AuthenticationException` ni `AccessDeniedException`. Se lanzan dentro de la
  cadena de filtros y nunca llegan al advice. Los resuelven el `AuthenticationEntryPoint` y el
  `AccessDeniedHandler` de `security/`.
- Su manejador genérico, si existe, responde 500 con un mensaje genérico, sin el mensaje de la
  excepción ni el stack trace (constitución, principio III).

## 6. Lo que auth garantiza a catálogo

- `GET /players` y `GET /players/**` quedan públicos por una línea marcada como TEMPORAL en
  `SecurityConfig`. La protección se activa en un PR posterior, borrando esa línea.
- La consola de H2 queda pública bajo el perfil `local` (`/h2-console/**`), con frames del
  mismo origen permitidos. No es pública en la configuración por defecto ni en `test`.
- El dispatch `ERROR` está permitido: el renderizado de errores de Spring Boot funciona aunque
  el request no tenga credencial.
- Los 401 y 403 salen en JSON con la forma de `ApiError`, nunca como HTML.
- `config/OpenApiConfig` declara los security schemes `bearerAuth` y `apiKeyAuth`, con sus
  nombres como constantes públicas. Cuando catálogo proteja `/players`, solo agrega
  `@SecurityRequirement` en sus controllers.
- Auth no toca `catalog/` ni `shared/`, y no crea endpoints bajo `/players` ni bajo `/users`.

## 7. Pasos de integración, cuando catálogo esté en `develop`

1. Rebasear `001-auth-usuarios` sobre `develop`.
2. Hacer que las excepciones de `auth/modelo/exception/` extiendan la base, o las bases por categoría, de
   `shared/`.
3. Hacer que `security/ApiErrorResponseWriter` use el `ApiError` de `shared/`, si es público.
   Si no, mantener el record propio con la misma forma y avisar.
4. Referenciar `ApiError` en los `@ApiResponse` de error de los controllers de auth.
5. Escribir los tests end to end de errores de dominio en clases nuevas (`AuthErrorsIT`,
   `AccountErrorsIT`), sin modificar las clases de test existentes. Cubren:
   - el 400 de validación, verificando que la contraseña no aparece en el cuerpo;
   - el 409 por username, por correo y por ambos;
   - el 401 del login, idéntico para un usuario inexistente y para una contraseña incorrecta;
   - los tres 400 del cambio de contraseña.
6. Verificar que `./gradlew build` pase, que los tests de catálogo sigan en verde y que
   `/players` responda sin credencial.
