# Guía de validación: Registro, credenciales y acceso autenticado

**Rama**: `001-auth-usuarios` | **Fecha**: 2026-09-18 | **Plan**: [plan.md](./plan.md)

Esta guía prueba la feature de punta a punta. Los formatos exactos de requests y respuestas
están en [contracts/auth-api.yaml](./contracts/auth-api.yaml); las reglas del modelo, en
[data-model.md](./data-model.md); las propiedades y variables de entorno, en
[contracts/configuration.md](./contracts/configuration.md).

## Precondiciones

- Java 21. No hace falta Docker, una base externa ni ningún otro servicio.
- Todos los comandos se ejecutan desde `backend/`.
- `build.gradle` ya tiene las dependencias nuevas, que el usuario confirmó antes de que se
  agregaran: Spring Security, jjwt (tres artefactos) y springdoc 3.1.1.
- Los ejemplos usan `curl` de Git Bash. En PowerShell, invocar `curl.exe`, porque `curl` es
  alias de `Invoke-WebRequest`.

## 1. Suite automatizada

```bash
./gradlew test
```

```bash
./gradlew build
```

Resultado esperado: `BUILD SUCCESSFUL`. La suite cubre:

- **Unitarios de modelo**, sin Spring:
  - `AppUser`: registro, alta del admin, emisión de clave, verificación y cambio de
    contraseña, y los límites de 3 y 30 caracteres del username y de 8 caracteres y 72 bytes
    de la contraseña.
  - `ApiKey`.
  - `RegistrationAvailability`: las cuatro combinaciones.
- **Unitarios de servicio**, con Mockito: `AuthService`, `AccountService` y
  `AdminAccountInitializer`, incluidas las advertencias del alta.
- **Unitarios de seguridad**: `JwtService` (emisión, vencimiento con reloj fijo, firma
  alterada, token mal formado, otra clave) y el JSON del 403.
- **Integración**, contra H2 en memoria:
  - `AppUserRepositoryIT`: los índices únicos de `username`, `email` y `api_key_hash`
    rechazan duplicados, el rol se guarda como texto y el saldo con escala 2.
  - `AdminAccountInitializerIT`: el alta es idempotente.
- **End to end**, en `e2e/`: los escenarios de las secciones 3 y 4.

## 2. Levantar la aplicación con el perfil local

```bash
./gradlew bootRun
```

Qué verificar en el log:

- La aplicación levanta en el puerto 8080 con el perfil `local`.
- Sin las variables del administrador aparece una advertencia de alta omitida, y la aplicación
  arranca igual (FR-038 y SC-012).
- **No** aparece `Using generated security password`: el usuario en memoria de Boot está
  excluido.

Para probar el alta del administrador (HU7), arrancar con las tres variables
`FUTBOLMARKET_AUTH_ADMIN_*` como indica
[contracts/configuration.md](./contracts/configuration.md#configurar-el-administrador-en-local).

## 3. Escenarios HTTP

```bash
BASE=http://localhost:8080
```

### 3.1 Registro (HU1)

Se hace un `POST $BASE/auth/register` con un cuerpo que tenga `username`, `email` y
`password`.

- **Resultado esperado**: `201` con `id`, `username`, `email`, `role` igual a `USER`, `balance`
  igual a `1000.00` y `apiKey`. No hay ningún campo de contraseña. Guardar el `apiKey`: no se
  vuelve a mostrar.
- Si se repite con `"role": "ADMIN"` en el cuerpo y otros datos, el resultado es igualmente
  `201` con `role` igual a `USER`.
- Si se envían espacios al principio o al final del username, la respuesta muestra el valor
  recortado.

### 3.2 Inicio de sesión (HU2)

Se hace un `POST $BASE/auth/login` con `username` y `password`.

- **Resultado esperado**: `200` con `token`, `tokenType` igual a `Bearer` y `expiresAt` unas
  24 horas después de la emisión.
- La respuesta no incluye la contraseña ni la clave de API.
- El username también funciona con otras mayúsculas.

### 3.3 Acceso con cada credencial y perfil (HU3 y HU4)

```bash
curl -s $BASE/auth/me -H "X-API-Key: $API_KEY"
```

```bash
curl -s $BASE/auth/me -H "Authorization: Bearer $TOKEN"
```

- **Resultado esperado**: las dos devuelven `200` con el mismo `id`, más `username`, `email`,
  `role` y `balance`.
- Ninguna incluye la contraseña, su hash ni la clave de API.
- Con las credenciales de otro usuario, se ven solo los datos de ese otro usuario.

### 3.4 Rechazos de la cadena de filtros (HU3)

| Request a `GET /auth/me` | Resultado esperado |
|---|---|
| Sin credencial | `401` con `Content-Type: application/json` y la forma de `ApiError`; el mensaje dice que falta la credencial. **No** es HTML. |
| `X-API-Key` inventada | `401`, credencial inválida |
| `Authorization: Bearer abc` | `401`, credencial inválida |
| JWT firmado con otra clave, o con un id inexistente | `401`, credencial inválida. El rechazo no distingue entre los dos casos. |
| JWT vencido | `401`, token vencido. Lo cubre el test automatizado; manualmente requiere esperar 24 horas o bajar `FUTBOLMARKET_SECURITY_JWT_EXPIRATION`. |
| JWT inválido y `X-API-Key` válida | `401`: el JWT tiene precedencia y la clave se ignora. |
| JWT válido y `X-API-Key` inválida | `200` |

Además:

- Ninguna respuesta crea una sesión: no aparece `Set-Cookie: JSESSIONID`.
- `GET $BASE/actuator/info` sin credencial devuelve `401`.

### 3.5 Recursos públicos (HU3)

| Request sin credencial | Resultado esperado |
|---|---|
| `GET /actuator/health` | `200` |
| `GET /v3/api-docs` | `200`. El JSON contiene los security schemes `bearerAuth` y `apiKeyAuth`. |
| `GET /swagger-ui.html` | Redirige a `/swagger-ui/index.html`, que responde `200`. |
| `GET /players` | No es `401`. Responde `404` mientras catálogo no esté mergeado y `200` después (excepción transitoria de FR-023). |
| `POST /auth/login` con una `X-API-Key` inválida | Se procesa normalmente: una credencial inválida no bloquea una ruta pública. |
| `GET /h2-console` en el perfil `test` | `401`: la consola solo es pública en `local`. Lo cubre el test automatizado. |

### 3.6 Cambio de contraseña (HU5)

Se hace un `PUT $BASE/auth/me/password` con credencial y un cuerpo con `currentPassword` y
`newPassword`.

- **Resultado esperado**: `204` sin cuerpo.
- El login con la contraseña nueva devuelve `200`.
- El token emitido antes del cambio sigue sirviendo en `GET /auth/me` (FR-035).
- El login con la contraseña anterior se rechaza. El status (`401`) y su formato JSON dependen
  del advice de `shared/`, así que se validan en la sección 6.

### 3.7 Regeneración de la clave (HU6)

Se hace un `POST $BASE/auth/me/api-key` con credencial.

- **Resultado esperado**: `200` con un `apiKey` nuevo.
- La clave anterior en `GET /auth/me` devuelve `401`, credencial inválida.
- La clave nueva devuelve `200` con el mismo `id`.
- Si se regenera dos veces seguidas, la penúltima clave devuelve `401`.
- Un token emitido antes de regenerar sigue siendo válido.

### 3.8 Administrador inicial (HU7)

Arrancar con las tres variables `FUTBOLMARKET_AUTH_ADMIN_*` y verificar:

1. El log informa el alta y no muestra la contraseña.
2. El login con las credenciales configuradas devuelve un token. `GET /auth/me` muestra
   `role` igual a `ADMIN` y `balance` igual a `0.00`.
3. `POST /auth/me/api-key` entrega una clave, y esa clave sirve en `GET /auth/me`.
4. Al reiniciar, con las mismas variables o con otra contraseña, el administrador no cambia:
   sigue valiendo la contraseña original y la clave regenerada.
5. Si se arranca sin `FUTBOLMARKET_AUTH_ADMIN_PASSWORD` sobre una base sin admin, se registra
   una advertencia, no se crea ninguna cuenta y la aplicación levanta.

Los arranques con configuración inválida y el caso de un usuario común que ya tiene el nombre
del admin los cubren `AdminAccountInitializerTest` y `AdminAccountInitializerIT`.

## 4. Swagger UI

1. Abrir `http://localhost:8080/swagger-ui.html`.
2. Verificar que los cinco endpoints de `/auth` tienen su descripción y sus códigos de
   respuesta, y que solo los tres de `/auth/me` muestran candado.
3. Tocar **Authorize**. Tienen que aparecer `bearerAuth` (el token, sin el prefijo `Bearer`) y
   `apiKeyAuth` (la clave).
4. Autorizar solo con uno de los dos y ejecutar `GET /auth/me`: responde `200`. Repetir con el
   otro.

## 5. Consola de H2 (perfil local)

1. Abrir `http://localhost:8080/h2-console`. Se conecta sin credencial de la API, con la URL
   `jdbc:h2:file:./data/futbolmarket`, el usuario `sa` y la contraseña vacía.
2. La consola se muestra completa: los frames del mismo origen están permitidos.
3. En la tabla `APP_USER`:
   - `PASSWORD_HASH` empieza con `$2`: es BCrypt.
   - `API_KEY_HASH` tiene 64 caracteres hexadecimales.
   - No aparece ninguna contraseña ni clave en claro.
   - `ROLE` es texto.
   - `BALANCE` tiene 2 decimales.
4. Reiniciar la aplicación: los usuarios siguen ahí, porque el perfil local usa
   `ddl-auto: update`.

## 6. Checkpoint de integración con `shared/`

Esta sección se corre cuando catálogo ya está en `develop` y la rama se rebaseó (ver
[contracts/shared-integration.md](./contracts/shared-integration.md)). Antes de eso, los
errores de dominio no tienen el formato definitivo. Todas las respuestas siguientes tienen la
forma de `ApiError`:

| Escenario | Resultado esperado |
|---|---|
| Registro con username de 2 caracteres, correo mal formado o contraseña de 7 caracteres | `400` con `violations` que indican la regla incumplida. **La contraseña no aparece en el cuerpo.** No se crea la cuenta. |
| Registro con username tomado | `409` que informa el username |
| Registro con correo tomado | `409` que informa el correo |
| Registro con los dos tomados | `409` que informa ambos |
| Username o correo tomados con otras mayúsculas o con espacios | `409` |
| Registro con el username del admin ya creado | `409` |
| Login con un usuario inexistente y login con una contraseña incorrecta | Los dos responden `401` con el mismo cuerpo, salvo `timestamp`. |
| Cambio de contraseña con la actual incorrecta | `400`, y la contraseña vigente sigue sirviendo. |
| Cambio de contraseña con la nueva igual a la actual | `400` |
| Cambio de contraseña con una nueva de 7 caracteres | `400` |

## 7. Cobertura de los criterios de éxito

| Criterio | Dónde se valida |
|---|---|
| SC-001 | 3.1: el registro devuelve una credencial utilizable. |
| SC-002 | Sección 6: los `409`. |
| SC-003 | 3.1, 3.2, 3.3 y 3.7; en la sección 6, la contraseña ausente del `400`. |
| SC-004 | 3.2 y el test de `JwtService` con reloj fijo. |
| SC-005 | 3.4 |
| SC-006 | 3.3 y 3.7: la misma identidad con las dos credenciales. |
| SC-007 | Sección 6: los `401` del login, idénticos. |
| SC-008 | 3.7 |
| SC-009 | 3.1: un rol `ADMIN` declarado se ignora. |
| SC-010 | 3.5 |
| SC-011 y SC-012 | 2 y 3.8, más `AdminAccountInitializerIT` y `AdminAccountInitializerTest`. |
| SC-013 | Revisión del repositorio: ningún archivo contiene las credenciales del admin. Los tests las generan al azar. |
