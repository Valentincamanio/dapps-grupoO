# Contrato de configuración

**Rama**: `feature/api-footballdata` | **Fecha**: 2026-10-07 | **Plan**: [../plan.md](../plan.md)

Estas son las propiedades nuevas de la feature. Las liga el record `FootballDataProperties`
(`config/`), que las valida al arrancar ([research.md](../research.md) D14). Todas se pueden
sobreescribir por variable de entorno con el binding relajado de Spring Boot: la propiedad
pasa a mayúsculas, los puntos se cambian por guiones bajos y se quitan los guiones medios. La
excepción es el token, que se configura con `FOOTBALL_DATA_TOKEN`.

## Propiedades

| Propiedad | Variable de entorno | Valor por defecto | Validación y efecto |
|---|---|---|---|
| `futbolmarket.football-data.token` | `FOOTBALL_DATA_TOKEN` | **Ninguno**. `application.yaml` solo tiene el placeholder `${FOOTBALL_DATA_TOKEN:}`, con default vacío. | Opcional. Sin token, la aplicación arranca, registra una advertencia y deja la sincronización deshabilitada: el disparo manual responde 503 y la corrida semanal se omite (FR-040 y FR-041). Nunca aparece en el registro, las respuestas ni los informes (FR-042). |
| `futbolmarket.football-data.base-url` | `FUTBOLMARKET_FOOTBALLDATA_BASEURL` | `https://api.football-data.org/v4` | Obligatoria, una URI. |
| `futbolmarket.football-data.connect-timeout` | `FUTBOLMARKET_FOOTBALLDATA_CONNECTTIMEOUT` | `10s` | Obligatoria y positiva. Es el tiempo máximo para abrir la conexión. |
| `futbolmarket.football-data.read-timeout` | `FUTBOLMARKET_FOOTBALLDATA_READTIMEOUT` | `30s` | Obligatoria y positiva. Es el tiempo máximo para recibir la respuesta completa, cuerpo incluido. Si se cumple, la liga queda fallida (FR-037). |
| `futbolmarket.football-data.sync.cron` | `FUTBOLMARKET_FOOTBALLDATA_SYNC_CRON` | `0 0 4 * * MON` | Obligatoria: una expresión cron de Spring de seis campos, o `-` para deshabilitar la corrida. Si es inválida, la aplicación no arranca (FR-026). |
| `futbolmarket.football-data.sync.zone` | `FUTBOLMARKET_FOOTBALLDATA_SYNC_ZONE` | `America/Argentina/Buenos_Aires` | Obligatoria: un `ZoneId` válido. Es la zona en la que se interpreta el cron. Si es inválida, la aplicación no arranca. |
| `futbolmarket.football-data.sync.on-startup` | `FUTBOLMARKET_FOOTBALLDATA_SYNC_ONSTARTUP` | `true` | Si vale `true` y hay token, al arrancar con el catálogo vacío se dispara una sincronización completa en segundo plano (FR-033). Vale `false` solo en el perfil test ([research.md](../research.md) D13). |

> **No usar** `FUTBOLMARKET_FOOTBALLDATA_TOKEN`. El binding relajado también la aceptaría, pero
> una variable de entorno de la propiedad le gana a `application-test.yml` y haría que los tests
> tengan token. El nombre acordado es `FOOTBALL_DATA_TOKEN`.

## Valores por perfil

| Propiedad | `application.yaml` (base) | `application-local.yml` | `application-test.yml` |
|---|---|---|---|
| `token` | `${FOOTBALL_DATA_TOKEN:}` | — (hereda la base) | `""`, explícito: pisa a `FOOTBALL_DATA_TOKEN` aunque esté exportada |
| `base-url` | `https://api.football-data.org/v4` | — | `http://football-data.invalid/v4`: el dominio `.invalid` no resuelve nunca (RFC 2606) |
| `connect-timeout` | `10s` | — | — |
| `read-timeout` | `30s` | — | — |
| `sync.cron` | `0 0 4 * * MON` | — | `-`: deshabilitada |
| `sync.zone` | `America/Argentina/Buenos_Aires` | — | — |
| `sync.on-startup` | `true` | — | `false` |

## Cambios en los archivos existentes

Solo se agregan claves: ningún archivo se reemplaza (constitución, Stack Tecnológico).

```yaml
# application.yaml: se agrega dentro del bloque futbolmarket existente, después de security
futbolmarket:
  football-data:
    token: ${FOOTBALL_DATA_TOKEN:}
    base-url: https://api.football-data.org/v4
    connect-timeout: 10s
    read-timeout: 30s
    sync:
      cron: "0 0 4 * * MON"
      zone: America/Argentina/Buenos_Aires
      on-startup: true
```

```yaml
# application-test.yml: se agrega dentro del bloque futbolmarket existente
futbolmarket:
  football-data:
    token: ""
    base-url: http://football-data.invalid/v4
    sync:
      cron: "-"
      on-startup: false
```

`application-local.yml` no cambia.

Lo que ya estaba configurado sigue igual: el perfil por defecto `local`, `open-in-view: false`,
H2 en archivo con `ddl-auto: update` y `show-sql: true` en `local`, H2 en memoria con
`create-drop` en `test`, y las propiedades de `auth` y `security.jwt`.

Se elimina `src/main/resources/data/players.json`, que no era configuración pero se cargaba al
arrancar en `local` (FR-002).

## Configurar el token en local

El token es personal y nunca se commitea (SC-011). Se obtiene al registrarse en
football-data.org con el plan gratis.

Bash, desde `backend/`:

```bash
FOOTBALL_DATA_TOKEN='<token>' ./gradlew bootRun
```

PowerShell, desde `backend/`:

```powershell
$env:FOOTBALL_DATA_TOKEN = '<token>'; ./gradlew bootRun
```

Para disparar la sincronización a mano hace falta además el administrador, con las tres
variables `FUTBOLMARKET_AUTH_ADMIN_*` de
[specs/001-auth-usuarios/contracts/configuration.md](../../001-auth-usuarios/contracts/configuration.md#configurar-el-administrador-en-local).
En una sola línea, en PowerShell:

```powershell
$env:FOOTBALL_DATA_TOKEN = '<token>'; $env:FUTBOLMARKET_AUTH_ADMIN_USERNAME = '<usuario-admin>'; $env:FUTBOLMARKET_AUTH_ADMIN_EMAIL = '<correo-admin>'; $env:FUTBOLMARKET_AUTH_ADMIN_PASSWORD = '<contrasena-admin>'; ./gradlew bootRun
```

Sin el token, la aplicación arranca igual y deja esta advertencia en el registro:

```text
WARN ... StartupSync : Falta la credencial de Football-Data.org (FOOTBALL_DATA_TOKEN): la sincronización queda deshabilitada.
```

## Probar la corrida programada sin esperar al lunes

Solo para una prueba manual en local, se puede adelantar el cron con una variable de entorno.
Por ejemplo, cada 5 minutos:

```powershell
$env:FUTBOLMARKET_FOOTBALLDATA_SYNC_CRON = '0 */5 * * * *'; $env:FOOTBALL_DATA_TOKEN = '<token>'; ./gradlew bootRun
```

Cada corrida completa consume 10 de las 10 consultas por minuto del plan gratis, así que no
conviene bajar de unos pocos minutos. Al terminar la prueba, se cierra la terminal o se borra la
variable para volver al lunes a las 04:00.
