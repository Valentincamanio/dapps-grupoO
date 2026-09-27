# Contrato de configuración

**Rama**: `001-auth-usuarios` | **Fecha**: 2026-09-18 | **Plan**: [../plan.md](../plan.md)

Estas son las propiedades nuevas que introduce la feature. Todas se pueden sobreescribir por
variable de entorno con el binding relajado de Spring Boot. La variable se arma pasando la
propiedad a mayúsculas, cambiando los puntos por guiones bajos y quitando los guiones medios.

## Propiedades

| Propiedad | Variable de entorno | Valor por defecto | Validación y efecto |
|---|---|---|---|
| `futbolmarket.auth.initial-balance` | `FUTBOLMARKET_AUTH_INITIALBALANCE` | `1000.00`, en `application.yaml` | Obligatoria, mayor o igual a 0 y con a lo sumo 2 decimales. Si es inválida, la aplicación no arranca. Es el saldo de cada usuario creado por el registro público (FR-012). |
| `futbolmarket.auth.admin.username` | `FUTBOLMARKET_AUTH_ADMIN_USERNAME` | **Ninguno**. No aparece en ningún archivo del repositorio. | La valida el alta del administrador. Si falta o es inválida, se advierte, se omite el alta y la aplicación arranca igual (FR-037 y FR-038). |
| `futbolmarket.auth.admin.email` | `FUTBOLMARKET_AUTH_ADMIN_EMAIL` | **Ninguno** | Igual que el anterior. |
| `futbolmarket.auth.admin.password` | `FUTBOLMARKET_AUTH_ADMIN_PASSWORD` | **Ninguno** | Igual que el anterior. Nunca se escribe en el log. |
| `futbolmarket.security.jwt.secret` | `FUTBOLMARKET_SECURITY_JWT_SECRET` | No está en la base. Los perfiles `local` y `test` traen cada uno un valor propio de desarrollo. | Obligatoria, en Base64 y de al menos 256 bits. Si falta o es débil, la aplicación no arranca. En producción va solo por variable de entorno. |
| `futbolmarket.security.jwt.expiration` | `FUTBOLMARKET_SECURITY_JWT_EXPIRATION` | `24h`, en `application.yaml` | Obligatoria y positiva. Acepta formatos de `Duration` como `24h` o `PT24H` (FR-016). |

También se agrega en `application.yaml` la exclusión
`spring.autoconfigure.exclude: org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration`.
Evita que Boot cree un usuario en memoria y deje su contraseña en el log (ver
[research.md](../research.md), D7).

## Cambios en los archivos existentes

Solo se agregan claves: ningún archivo se reemplaza (constitución, Stack Tecnológico). Los
valores de los secretos se generan en la implementación y no se copian en esta
documentación.

```yaml
# application.yaml: se agrega bajo el bloque spring existente, sin duplicar la clave spring
spring:
  autoconfigure:
    exclude: org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration

# application.yaml: bloque nuevo al final del archivo
futbolmarket:
  auth:
    initial-balance: 1000.00
  security:
    jwt:
      expiration: 24h
```

```yaml
# application-local.yml y application-test.yml: bloque nuevo, cada uno con su propio valor
futbolmarket:
  security:
    jwt:
      secret: <Base64 de al menos 256 bits; solo para desarrollo o tests>
```

Lo que ya estaba configurado sigue igual:

- `spring.profiles.default: local`
- `spring.jpa.open-in-view: false`
- En `local`: H2 en archivo, `ddl-auto: update` y la consola de H2 en `/h2-console`.
- En `test`: H2 en memoria, `create-drop` y la consola deshabilitada.
- La exposición web de actuator (`health` e `info`). Solo `/actuator/health` es público; para
  `/actuator/info` hace falta una credencial.

## Generar un secreto

Bash:

```bash
openssl rand -base64 48
```

PowerShell 5.1:

```powershell
$b = New-Object byte[] 48; [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($b); [Convert]::ToBase64String($b)
```

## Configurar el administrador en local

Los valores entre `<...>` son de cada entorno y nunca se commitean (SC-013). El username tiene
que respetar las reglas del registro: de 3 a 30 caracteres, solo letras, números y guiones
bajos. El correo tiene que tener formato válido y la contraseña, al menos 8 caracteres.

Bash, desde `backend/`:

```bash
FUTBOLMARKET_AUTH_ADMIN_USERNAME='<usuario-admin>' FUTBOLMARKET_AUTH_ADMIN_EMAIL='<correo-admin>' FUTBOLMARKET_AUTH_ADMIN_PASSWORD='<contrasena-admin>' ./gradlew bootRun
```

PowerShell, desde `backend/`:

```powershell
$env:FUTBOLMARKET_AUTH_ADMIN_USERNAME = '<usuario-admin>'; $env:FUTBOLMARKET_AUTH_ADMIN_EMAIL = '<correo-admin>'; $env:FUTBOLMARKET_AUTH_ADMIN_PASSWORD = '<contrasena-admin>'; ./gradlew bootRun
```

Si faltan estas variables, la aplicación arranca igual y deja una advertencia en el log. El
alta es idempotente: en los arranques siguientes, un administrador existente no se toca,
aunque cambien las variables.
