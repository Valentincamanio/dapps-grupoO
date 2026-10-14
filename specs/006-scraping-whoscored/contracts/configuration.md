# Contrato de configuración

**Rama**: `feature/scraping-whoscored` | **Fecha**: 2026-10-13 | **Plan**: [../plan.md](../plan.md)

Estas son las propiedades nuevas de la feature y las de la 005 que cambian de sentido. Las nuevas
las ligan dos records de `config/`:

- `WhoScoredProperties` (`futbolmarket.whoscored`);
- `SyncProperties` (`futbolmarket.sync`).

Los dos se validan al arrancar ([research.md](../research.md) D25). Todas se pueden sobreescribir
por variable de entorno con el binding relajado de Spring Boot: la propiedad pasa a mayúsculas,
los puntos a guiones bajos y se quitan los guiones medios. WhoScored no usa ninguna credencial.

## Propiedades nuevas

| Propiedad | Variable de entorno | Valor por defecto | Validación y efecto |
|---|---|---|---|
| `futbolmarket.whoscored.base-url` | `FUTBOLMARKET_WHOSCORED_BASEURL` | `https://www.whoscored.com` | Obligatoria, una URI. |
| `futbolmarket.whoscored.user-agent` | `FUTBOLMARKET_WHOSCORED_USERAGENT` | `Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0 Safari/537.36` | Obligatoria y no en blanco. Es el header verificado ([whoscored-pages.md](./whoscored-pages.md)). |
| `futbolmarket.whoscored.accept` | `FUTBOLMARKET_WHOSCORED_ACCEPT` | `text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8` | Obligatoria y no en blanco. |
| `futbolmarket.whoscored.accept-language` | `FUTBOLMARKET_WHOSCORED_ACCEPTLANGUAGE` | `en-US,en;q=0.9` | Obligatoria y no en blanco. |
| `futbolmarket.whoscored.connect-timeout` | `FUTBOLMARKET_WHOSCORED_CONNECTTIMEOUT` | `10s` | Obligatoria y positiva. |
| `futbolmarket.whoscored.read-timeout` | `FUTBOLMARKET_WHOSCORED_READTIMEOUT` | `30s` | Obligatoria y positiva. Cubre también el cuerpo, de hasta 1,3 MB. |
| `futbolmarket.whoscored.request-interval` | `FUTBOLMARKET_WHOSCORED_REQUESTINTERVAL` | `4s` | Obligatoria y de **al menos 4 s** (FR-042). Si es menor, la aplicación no arranca. Es la pausa mínima entre la respuesta anterior y el pedido siguiente. |
| `futbolmarket.whoscored.retry-wait` | `FUTBOLMARKET_WHOSCORED_RETRYWAIT` | `30s` | Obligatoria y de **al menos 30 s** (FR-043). Si es menor, la aplicación no arranca. Es la espera antes del único reintento. |
| `futbolmarket.whoscored.snapshot.path` | `FUTBOLMARKET_WHOSCORED_SNAPSHOT_PATH` | `snapshot/whoscored-stats.jsonl` | Obligatoria y no en blanco. Es relativa al directorio de trabajo, que es `backend/` con `./gradlew bootRun` y con `./gradlew exportStatsSnapshot`. Si el archivo no existe, la sincronización de WhoScored deja un WARN y scrapea todo lo pendiente (FR-027). |
| `futbolmarket.sync.catch-up-after` | `FUTBOLMARKET_SYNC_CATCHUPAFTER` | `7d` | Obligatoria y positiva. Una última sincronización completa exitosa más vieja que esto está vencida y dispara la puesta al día al arrancar (FR-034 y FR-035). Con exactamente ese tiempo, todavía está al día. |

## Propiedades de la 005 que cambian de sentido

Ninguna cambia de nombre, de valor ni de validación. `FootballDataProperties` no cambia.

| Propiedad | En la 005 | Ahora |
|---|---|---|
| `futbolmarket.football-data.sync.cron` y `.zone` | La corrida semanal de Football-Data | La corrida semanal de **la cadena**: Football-Data y, al terminar, WhoScored (FR-033). Con `-` no corre ninguna de las dos. |
| `futbolmarket.football-data.sync.on-startup` | Sincronizar al arrancar con el catálogo vacío | Habilitar **la puesta al día al arrancar** de las dos fuentes (FR-034 y FR-035). Con `false`, como en el perfil test, no corre ninguna. |
| `futbolmarket.football-data.token` | Sin token, la sincronización de Football-Data queda deshabilitada | Igual. Además, sin token la cadena saltea Football-Data con un INFO y **WhoScored corre igual** (FR-036 y FR-040). |

## Valores por perfil

| Propiedad | `application.yaml` (base) | `application-local.yml` | `application-test.yml` |
|---|---|---|---|
| `whoscored.base-url` | `https://www.whoscored.com` | — (hereda la base) | `http://whoscored.invalid`: el dominio `.invalid` no resuelve nunca (RFC 2606) |
| `whoscored.user-agent`, `accept` y `accept-language` | Los verificados | — | — |
| `whoscored.connect-timeout` y `read-timeout` | `10s` y `30s` | — | — |
| `whoscored.request-interval` y `retry-wait` | `4s` y `30s` | — | — (los tests no esperan: el adapter usa `RecordingWhoScoredSleeper` y los e2e lo mockean) |
| `whoscored.snapshot.path` | `snapshot/whoscored-stats.jsonl` | — | `build/test-snapshot/absent.jsonl`, que no existe. Los tests que necesitan un snapshot lo apuntan a un `@TempDir`. |
| `sync.catch-up-after` | `7d` | — | — |
| `football-data.sync.on-startup` (existente) | `true` | — | `false` (sin cambios) |
| `football-data.sync.cron` (existente) | `0 0 4 * * MON` | — | `-` (sin cambios) |

## Cambios en los archivos existentes

Solo se agregan claves: ningún archivo se reemplaza (constitución, Stack Tecnológico).

```yaml
# application.yaml: se agrega dentro del bloque futbolmarket existente, después de football-data
futbolmarket:
  whoscored:
    base-url: https://www.whoscored.com
    user-agent: "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0 Safari/537.36"
    accept: "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
    accept-language: "en-US,en;q=0.9"
    connect-timeout: 10s
    read-timeout: 30s
    request-interval: 4s
    retry-wait: 30s
    snapshot:
      path: snapshot/whoscored-stats.jsonl
  sync:
    catch-up-after: 7d
```

```yaml
# application-test.yml: se agrega dentro del bloque futbolmarket existente
futbolmarket:
  whoscored:
    base-url: http://whoscored.invalid
    snapshot:
      path: build/test-snapshot/absent.jsonl
```

`application-local.yml` no cambia.

Fuera de los `application*.yml`:

- **`backend/build.gradle`** suma la tarea `exportStatsSnapshot`, de tipo `JavaExec`, sin
  dependencias nuevas ([research.md](../research.md) D21). La tarea levanta Spring con:
  - `spring.profiles.active=local`;
  - `spring.main.web-application-type=none`;
  - `futbolmarket.football-data.sync.on-startup=false`;
  - `futbolmarket.football-data.sync.cron=-`.

  La clase main los fija, así que no hace falta pasarlos a mano.
- **`.gitattributes`** suma `backend/snapshot/*.jsonl text eol=lf`.

## Correr contra WhoScored en la máquina de Lucas (AVG)

AVG intercepta HTTPS y Java no reconoce su certificado: el pedido falla con
`PKIX path building failed`, que la sincronización trata como un rechazo y termina cortando la
corrida. Hay dos salidas:

1. **Recomendada**: agregar `https://www.whoscored.com/*` a las excepciones de la protección web
   de AVG, como se hizo con Football-Data.
2. **Solo para la sesión**: hacer que Java use el almacén de certificados de Windows.

   ```powershell
   $env:JAVA_TOOL_OPTIONS = '-Djavax.net.ssl.trustStore=NONE -Djavax.net.ssl.trustStoreType=Windows-ROOT'; ./gradlew bootRun
   ```

   Al terminar se cierra la terminal o se borra la variable. La suite automatizada no lo
   necesita: nunca sale a internet.

## Forzar la puesta al día al arrancar

La puesta al día corre sola si la última completa exitosa de cada fuente tiene más de 7 días o
si nunca hubo una. Para probarla sin esperar, hay dos formas:

- con la aplicación detenida, borrar las filas de `LAST_FULL_SYNCS` desde la consola de H2
  ([quickstart.md](../quickstart.md), sección 7);
- o arrancar con el umbral bajo, solo en esa sesión:

  ```powershell
  $env:FUTBOLMARKET_SYNC_CATCHUPAFTER = '1m'; ./gradlew bootRun
  ```

## Probar la corrida programada sin esperar al lunes

Igual que en la 005, se puede adelantar el cron con `FUTBOLMARKET_FOOTBALLDATA_SYNC_CRON`. Ahora
cada corrida es la cadena: Football-Data, que gasta las 10 consultas por minuto del plan gratis,
y después WhoScored, que pide páginas solo si hay partidos pendientes. No conviene bajar de unos
pocos minutos.
