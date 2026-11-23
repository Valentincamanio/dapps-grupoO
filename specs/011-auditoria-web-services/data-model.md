# Modelo de datos: Auditoría de web services

**Feature**: `011-auditoria-web-services` | **Fecha**: 2026-11-12 | **Plan**: [plan.md](./plan.md)

Esta feature **no tiene entidades persistentes**: no agrega tablas, clases `*SQL`, mappers ni
repositories, y no toca la auditoría del mercado de la 008. Lo que se modela es una línea de log y
las reglas para armarla. El formato exacto, carácter por carácter, está en
[contracts/web-service-audit-line.md](./contracts/web-service-audit-line.md).

## Línea de auditoría de web services

El registro técnico de una invocación a un endpoint propio. Se escribe una sola vez, al terminar la
invocación, y no se relaciona con ninguna entidad del dominio.

| Campo | Origen | Valor | Reglas |
|-------|--------|-------|--------|
| timestamp | pattern de Logback (`%d`) | `2026-11-12T10:15:03.412-03:00` | ISO-8601 con milisegundos y desplazamiento, zona `America/Argentina/Buenos_Aires`. Momento en que termina la invocación (FR-005). |
| nivel | pattern de Logback (`%level`) | `INFO` | Siempre `INFO`, también con `result=ERROR` (FR-011). |
| `user` | `SecurityContextHolder` | `42(USER,JWT)` · `7(ADMIN,API_KEY)` · `anonymous` | Ver [Usuario](#usuario). Nunca el valor de la credencial (FR-014). |
| `op` | firma del join point + `HttpServletRequest` | `PortfolioController.getPortfolio GET /users/42/portfolio` | `<ControllerSimple>.<método> <VERBO> <URI sin query string>`. Sin request en el hilo: `-` en verbo y URI (FR-007). |
| `params` | argumentos del método | `{"id":42,"date":null}` | Objeto JSON nombre → valor. Ver [Parámetro registrado](#parámetro-registrado) (FR-008, FR-013, FR-015, FR-017–FR-019). |
| `time` | `System.nanoTime()` antes y después de `proceed()` | `12ms` | Milisegundos enteros, sin contar la serialización. También cuando termina en error (FR-009). |
| `result` | cómo terminó `proceed()` | `OK` · `ERROR(PlayerNotFoundException)` · `ERROR` | `OK` si volvió; `ERROR(<NombreSimple>)` si salió una `RuntimeException`; `ERROR` solo, si salió un `Error` de la JVM. Nunca el mensaje (FR-010). |

### Usuario

| Autenticación en el contexto | `user` |
|------------------------------|--------|
| `null` | `anonymous` |
| `AnonymousAuthenticationToken` o `isAuthenticated() == false` | `anonymous` |
| Autenticada, `details` = `CredentialType.JWT` | `<id>(<ROL>,JWT)` |
| Autenticada, `details` = `CredentialType.API_KEY` | `<id>(<ROL>,API_KEY)` |
| Autenticada, `details` de otro tipo o `null` | `<id>(<ROL>)` |

- `<id>`: el principal, el `Long` que pone `CredentialAuthenticationFilter`.
- `<ROL>`: la primera authority con prefijo `ROLE_`, sin el prefijo (`USER`, `ADMIN`). Si no hay
  ninguna, `?`.
- En las rutas públicas el filtro no evalúa credenciales, así que siempre es `anonymous` (spec
  revisada, research D6).

## Parámetro registrado

Un argumento del método invocado, ya filtrado, serializado, enmascarado, truncado y escapado.

### Qué argumentos entran

Todos los del método, en orden de declaración y con el nombre del parámetro Java (compilado con
`-parameters`), **salvo**:

| Se excluye | Por qué |
|------------|---------|
| El anotado con `@AuthenticationPrincipal` (o con una anotación que la tenga como meta-anotación) | La identidad va solo en `user` (FR-015). |
| `ServletRequest` / `ServletResponse` y subtipos | No son datos de la invocación. |
| `Errors` y subtipos (`BindingResult`) | Ídem. |
| `java.security.Principal` y subtipos (`Authentication`) | Identidad (FR-015). |

### Cómo se representa cada valor

1. **Serialización**: `valueToTree(argumento)` con el `JsonMapper` de la app (Jackson 3). Nunca
   `toString()`: los records de request imprimen la contraseña en su `toString()`.
2. **Enmascarado** (sobre el árbol, antes de truncar): ver
   [Reglas de enmascarado](#reglas-de-enmascarado).
3. **Truncado por valor**: todo string de más de `MAX_VALUE_LENGTH = 500` caracteres se corta a 500
   y se le agrega `...(truncado)`.
4. **Nulo**: `null` explícito (FR-019).
5. **No representable**: si la serialización de ese argumento lanza `JacksonException`, el valor es
   el string `<no representable>` y el resto de los parámetros se escribe normalmente (FR-021).

Después, el objeto completo se escribe con `writeValueAsString`. Si supera
`MAX_PARAMS_LENGTH = 2000` caracteres, se corta a 2000 y se le agrega `...(truncado)`.

Sin argumentos que registrar (por ejemplo `GET /auth/me`, cuyo único argumento es el principal):
`params={}`.

## Reglas de enmascarado

| Regla | Detalle |
|-------|---------|
| Qué es un secreto | Toda propiedad, o parámetro de primer nivel, cuyo nombre **normalizado** contiene `password`, `token`, `apikey` o `secret`. |
| Normalización del nombre | Minúsculas y sin `_` ni `-`: `newPassword` → `newpassword`, `api_key` → `apikey`, `X-Api-Key` → `xapikey`. |
| Dónde | En cualquier nivel: propiedades de objetos anidados y objetos dentro de arrays. |
| Qué se reemplaza | El valor completo, sea del tipo que sea (string, número, objeto, array, vacío o `null`). |
| Máscara | El string fijo `***`. No revela ni el valor ni su largo, ni si venía vacío. |
| Orden | Primero se enmascara, después se trunca. |

Casos del sistema hoy:

| Endpoint | Parámetro | Campos enmascarados |
|----------|-----------|---------------------|
| `POST /auth/login` | `request` (`LoginRequest`) | `password` |
| `POST /auth/register` | `request` (`RegisterRequest`) | `password` |
| `PUT /auth/me/password` | `request` (`ChangePasswordRequest`) | `currentPassword`, `newPassword` |
| `POST /auth/me/api-key` | (solo el principal, excluido) | — (la clave nueva viaja en la respuesta, que no se registra: FR-016) |

Ejemplo, `POST /auth/login`:

```text
params={"request":{"username":"messi10","password":"***"}}
```

## Escape

- Dentro de `params`, Jackson escapa los caracteres de control (`\n`, `\r`, `\t` y el resto
  menores a `0x20`) de cada string. Un valor con saltos de línea queda en la misma línea.
- `op` pasa por el mismo escape de caracteres de control, por si la URI trajera uno.
- `user` y `result` salen de datos del sistema (id, rol, nombre de clase) y no necesitan escape.

## Datos de entrada del formatter (`WebServiceInvocation`)

El aspecto junta los datos crudos de la invocación en un `record` y el formatter los convierte en
el mensaje. Así el formatter se testea sin Spring, sin AspectJ y sin servlet.

| Componente | Tipo | De dónde sale |
|------------|------|---------------|
| `authentication` | `Authentication` (nullable) | `SecurityContextHolder` |
| `controllerType` | `Class<?>` | `joinPoint.getSignature().getDeclaringType()` |
| `method` | `java.lang.reflect.Method` | `((MethodSignature) joinPoint.getSignature()).getMethod()` |
| `arguments` | `List<Object>` (admite `null`) | `Arrays.asList(joinPoint.getArgs())` |
| `httpMethod` | `String` | `HttpServletRequest#getMethod()` o `-` |
| `requestUri` | `String` | `HttpServletRequest#getRequestURI()` o `-` |
| `elapsedNanos` | `long` | `System.nanoTime()` final − inicial |
| `failure` | `RuntimeException` (nullable) | la capturada y relanzada |
| `completed` | `boolean` | `true` si `proceed()` volvió |

No hay transiciones de estado: la línea se arma una vez y se escribe.
