# Contrato: línea de auditoría de web services

**Feature**: `011-auditoria-web-services` | **Fecha**: 2026-11-12 | **Plan**: [../plan.md](../plan.md)

Esta feature no expone endpoints nuevos ni cambia los existentes: no hay contrato OpenAPI. Su
interfaz externa es el **formato de la línea** que leen las personas y las herramientas de búsqueda
de texto (`grep`, `findstr`). Este documento lo fija. Las reglas de cada valor están en
[../data-model.md](../data-model.md).

## Destino

| Entorno | Consola | Archivo |
|---------|---------|---------|
| Perfil `test` | Sí | No |
| Cualquier otro perfil (`local` por defecto) | Sí | `logs/web-service-audit.log`, relativo al directorio de trabajo (`backend/` con `./gradlew bootRun`) |

- Logger SLF4J: `WEB_SERVICE_AUDIT`, nivel `INFO`, `additivity="false"`. Las líneas no aparecen en
  los appenders del log general.
- Rotación: diaria y por tamaño. Rotados: `logs/web-service-audit.<yyyy-MM-dd>.<i>.log.gz`.
  Retención: `WEB_SERVICE_AUDIT_MAX_HISTORY` días (30) con tope total
  `WEB_SERVICE_AUDIT_TOTAL_SIZE_CAP` (1GB) y `WEB_SERVICE_AUDIT_MAX_FILE_SIZE` (10MB) por archivo.
- Para apagarla: `logging.level.WEB_SERVICE_AUDIT=OFF`. Cambiar `logging.level.root` no la afecta.

## Gramática

Una línea por invocación, terminada en salto de línea. Los campos van separados por ` | ` (espacio,
barra vertical, espacio), en este orden fijo:

```text
<timestamp> | <nivel> | user=<user> | op=<op> | params=<params> | time=<ms>ms | result=<result>
```

| Elemento | Forma |
|----------|-------|
| `<timestamp>` | `yyyy-MM-dd'T'HH:mm:ss.SSSXXX` en `America/Argentina/Buenos_Aires`, por ejemplo `2026-11-12T10:15:03.412-03:00` |
| `<nivel>` | `INFO` |
| `<user>` | `anonymous` \| `<id>(<ROL>,<CREDENCIAL>)` \| `<id>(<ROL>)`; `<CREDENCIAL>` es `JWT` o `API_KEY` |
| `<op>` | `<Controller>.<método> <VERBO> <URI>`; sin request en el hilo: `<Controller>.<método> - -` |
| `<params>` | objeto JSON en una línea; truncado: los primeros 2000 caracteres + `...(truncado)` |
| `<ms>` | entero ≥ 0 |
| `<result>` | `OK` \| `ERROR(<NombreSimpleDeLaExcepcion>)` \| `ERROR` |

Pattern de Logback (archivo y consola, el mismo):

```text
%d{yyyy-MM-dd'T'HH:mm:ss.SSSXXX, America/Argentina/Buenos_Aires} | %level | %msg%n
```

Mensaje que escribe el código (placeholders de SLF4J):

```text
user={} | op={} {} {} | params={} | time={}ms | result={}
```

## Valores fijos

| Constante | Valor |
|-----------|-------|
| Usuario sin autenticar | `anonymous` |
| Máscara de secretos | `***` |
| Marca de truncado | `...(truncado)` |
| Valor no representable | `<no representable>` |
| Verbo/URI sin request | `-` |
| Rol sin authority `ROLE_*` | `?` |
| Largo máximo por valor | 500 caracteres |
| Largo máximo de `params` | 2000 caracteres |

## Ejemplos

Todos en una sola línea; acá se cortan solo para leerlos.

Login (público, contraseña enmascarada):

```text
2026-11-12T10:15:03.412-03:00 | INFO | user=anonymous | op=AuthController.login POST /auth/login
  | params={"request":{"username":"messi10","password":"***"}} | time=87ms | result=OK
```

Login con contraseña incorrecta (sigue en `INFO`):

```text
2026-11-12T10:15:09.120-03:00 | INFO | user=anonymous | op=AuthController.login POST /auth/login
  | params={"request":{"username":"messi10","password":"***"}} | time=81ms
  | result=ERROR(InvalidCredentialsException)
```

Endpoint protegido con token de sesión (el principal no se repite en `params`):

```text
2026-11-12T10:16:40.007-03:00 | INFO | user=42(USER,JWT)
  | op=PortfolioController.getPortfolio GET /users/42/portfolio | params={"id":42,"date":null}
  | time=12ms | result=OK
```

Cambio de contraseña con clave de API:

```text
2026-11-12T10:17:02.551-03:00 | INFO | user=42(USER,API_KEY)
  | op=AccountController.changePassword PUT /auth/me/password
  | params={"request":{"currentPassword":"***","newPassword":"***"}} | time=95ms | result=OK
```

Jugador inexistente (público; el 404 que recibe el cliente no cambia):

```text
2026-11-12T10:18:30.300-03:00 | INFO | user=anonymous | op=PlayerController.getPlayer GET /players/999999
  | params={"id":999999} | time=3ms | result=ERROR(PlayerNotFoundException)
```

Restricción sobre un parámetro (`@Min(1)`), evaluada dentro de la invocación:

```text
2026-11-12T10:18:31.010-03:00 | INFO | user=anonymous | op=PlayerController.getPlayer GET /players/0
  | params={"id":0} | time=1ms | result=ERROR(ConstraintViolationException)
```

Endpoint sin parámetros que registrar:

```text
2026-11-12T10:19:00.000-03:00 | INFO | user=42(USER,JWT) | op=AccountController.getProfile GET /auth/me
  | params={} | time=4ms | result=OK
```

## Qué NO genera línea

- 401 y 403 del filtro de seguridad.
- 400 previos a la invocación: cuerpo ilegible, cuerpo que no pasa `@Valid`, parámetro faltante o
  que no se puede convertir a su tipo.
- Rutas o verbos sin endpoint (404/405 de Spring MVC).
- Actuator (`/actuator/health`), Swagger/OpenAPI, consola de H2 y el frontend.
- Procesos programados y sincronizaciones al arranque.

## Garantías

- Exactamente una línea por invocación auditada, completa y sin mezclarse con otra.
- Ningún secreto en claro: ni contraseñas, ni tokens, ni claves de API, ni la credencial del request.
- No se registra el cuerpo de la respuesta.
- La respuesta (status, cuerpo, headers) es la misma con y sin auditoría.
