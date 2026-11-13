# Quickstart: Auditoría de web services

**Feature**: `011-auditoria-web-services` | **Fecha**: 2026-11-12 | **Plan**: [plan.md](./plan.md)

Guía para ver la auditoría funcionando y validar los escenarios de la spec. El formato esperado de
cada línea está en [contracts/web-service-audit-line.md](./contracts/web-service-audit-line.md);
las reglas de enmascarado y truncado, en [data-model.md](./data-model.md).

## Requisitos

- Java 21 y el wrapper de Gradle de `backend/`.
- `curl` (o Swagger UI en `http://localhost:8080/swagger-ui.html`).
- Nada más: sin Docker y sin base externa.

## 1. Tests automáticos

Desde `backend/`:

```bash
./gradlew test
```

Tienen que pasar todos los tests existentes sin cambios, más los dos nuevos:

- `shared/WebServiceAuditFormatterTest`: enmascarado, truncado, escape, exclusiones, usuario y
  resultado, sin Spring.
- `e2e/WebServiceAuditIT`: líneas reales capturadas de la consola con `OutputCaptureExtension`.

```bash
./gradlew build
```

Tiene que dar `BUILD SUCCESSFUL` (definición de terminado).

> **Después de integrar con la 010** (tests de arquitectura con ArchUnit): correr de nuevo
> `./gradlew test` **completo**, no solo los tests de esta feature. Las reglas de
> `LayerRulesTest`, `CodingRulesTest` y compañía tienen que pasar con las cuatro clases nuevas de
> `shared/` y con el import de `shared/CredentialType` desde `security/`. Si el merge de
> `build.gradle` conflictúa, conservar las dos líneas: `spring-boot-starter-aspectj` (esta feature)
> y la de ArchUnit (la 010).

## 2. Levantar la aplicación

Desde `backend/`:

```bash
./gradlew bootRun
```

Arranca con el perfil `local`. Al primer request auditado aparece el archivo
`backend/logs/web-service-audit.log`.

En otra terminal, seguir el archivo:

```bash
tail -f logs/web-service-audit.log
```

## 3. Escenarios

Los comandos se corren desde cualquier carpeta; el servidor escucha en `http://localhost:8080`.

### 3.1 Registro y login: la contraseña nunca aparece (US2)

```bash
curl -s -X POST http://localhost:8080/auth/register -H "Content-Type: application/json" -d '{"username":"auditoria01","email":"auditoria01@correo.com","password":"secreto-de-prueba"}'
```

```bash
curl -s -X POST http://localhost:8080/auth/login -H "Content-Type: application/json" -d '{"username":"auditoria01","password":"secreto-de-prueba"}'
```

Esperado en el archivo y en la consola, dos líneas:

- `op=AuthController.register POST /auth/register`, `user=anonymous`, `"password":"***"`,
  `result=OK`.
- `op=AuthController.login POST /auth/login`, `user=anonymous`, `"password":"***"`, `result=OK`.

Verificación de que el secreto no quedó escrito (no tiene que devolver nada):

```bash
grep -c "secreto-de-prueba" logs/web-service-audit.log
```

Tampoco tienen que aparecer el token que devolvió el login ni la clave de API que devolvió el
registro: la respuesta no se registra (FR-016).

### 3.2 Endpoint protegido: usuario, rol y credencial (US1)

Con el `token` y el `id` de las respuestas anteriores:

```bash
curl -s http://localhost:8080/users/<id>/portfolio -H "Authorization: Bearer <token>"
```

Esperado: `user=<id>(USER,JWT) | op=PortfolioController.getPortfolio GET /users/<id>/portfolio |
params={"id":<id>,"date":null}`.

Con la clave de API (`apiKey` del registro):

```bash
curl -s http://localhost:8080/auth/me -H "X-API-Key: <apiKey>"
```

Esperado: `user=<id>(USER,API_KEY) | op=AccountController.getProfile GET /auth/me | params={}`. El
valor de la clave no aparece.

### 3.3 Cambio de contraseña: los dos campos enmascarados (US2)

```bash
curl -s -X PUT http://localhost:8080/auth/me/password -H "Authorization: Bearer <token>" -H "Content-Type: application/json" -d '{"currentPassword":"secreto-de-prueba","newPassword":"otro-secreto-123"}'
```

Esperado: `params={"request":{"currentPassword":"***","newPassword":"***"}}`.

### 3.4 Errores: misma respuesta, línea en INFO (US3)

```bash
curl -s -i http://localhost:8080/players/999999
```

Esperado: el cliente recibe el mismo 404 con el mismo cuerpo `ApiError` de siempre, y la línea
dice `INFO`, `user=anonymous` (ruta pública) y `result=ERROR(PlayerNotFoundException)`.

```bash
curl -s -i http://localhost:8080/players/0
```

Esperado: el mismo 400 de siempre y `result=ERROR(ConstraintViolationException)`.

```bash
curl -s -i -X POST http://localhost:8080/auth/login -H "Content-Type: application/json" -d '{}'
```

Esperado: el mismo 400 de siempre y **ninguna** línea nueva (el request se rechaza antes de
invocar el endpoint).

### 3.5 Lo que no se audita

```bash
curl -s http://localhost:8080/actuator/health
```

```bash
curl -s -i http://localhost:8080/auth/me
```

Esperado: ninguna línea nueva, ni por actuator ni por el 401 del filtro.

## 4. Operación del registro (US4)

- **Separado del log general**: las líneas de la consola tienen el formato de la auditoría
  (`<timestamp> | INFO | user=…`), no el del log de Spring (sin PID, hilo ni nombre de logger).
  El log general se sigue viendo como antes.
- **Nivel general subido**: parar la aplicación y levantarla con el root en `WARN`:

  ```bash
  ./gradlew bootRun --args='--logging.level.root=WARN'
  ```

  Repetir 3.4: el log general queda casi mudo, pero las líneas de auditoría se siguen escribiendo.
- **Fuera del control de versiones** (desde la raíz del repo; tiene que imprimir la ruta, que
  indica que está ignorada):

  ```bash
  git check-ignore -v backend/logs/web-service-audit.log
  ```

- **Rotación**: los archivos rotados quedan como `logs/web-service-audit.<fecha>.<i>.log.gz`. Para
  verlo sin esperar un día, bajar temporalmente `WEB_SERVICE_AUDIT_MAX_FILE_SIZE` en
  `logback-spring.xml` (por ejemplo a `1KB`), hacer varios requests y revertir el cambio.

## 5. Swagger sin cambios

`http://localhost:8080/swagger-ui.html` tiene que listar los mismos endpoints y los mismos
esquemas que antes de la feature (los controllers ahora son proxies, y springdoc los resuelve sobre
la clase real). No hay endpoints nuevos que documentar.
