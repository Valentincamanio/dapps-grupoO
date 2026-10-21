# Valoración de mercado de jugadores de fútbol

TP de Desarrollo de Aplicaciones — 2do Semestre 2026

## Cómo levantarlo

Requisitos: Java 21. La base es embebida (H2): no hacen falta Docker ni otros servicios.

    cd backend
    ./gradlew bootRun

- API: http://localhost:8080
- Swagger: http://localhost:8080/swagger-ui.html
- Health: http://localhost:8080/actuator/health
- Consola H2: http://localhost:8080/h2-console

### Catálogo de jugadores (Football-Data.org)

El catálogo de las cinco ligas se sincroniza con [Football-Data.org](https://www.football-data.org).
Hace falta un token propio del plan gratis en la variable `FOOTBALL_DATA_TOKEN`. El token es
personal y nunca se commitea.

- Sin token, la aplicación levanta igual, pero con la sincronización deshabilitada: el catálogo
  queda vacío, o con lo que ya tenía guardado.
- Al arrancar, si no hay una sincronización completa exitosa registrada (por ejemplo, con la base
  vacía) o la última tiene más de 7 días, se pone al día en segundo plano, seguida de la de
  WhoScored (ver la sección siguiente). En menos de un minuto quedan unos 2.600 jugadores.
- Después se sincroniza sola los lunes a las 04:00 de Argentina, si la aplicación está
  encendida a esa hora, también seguida de la de WhoScored. Una corrida perdida la recupera la
  puesta al día del próximo arranque.
- Un administrador puede dispararla a mano: `POST /players/sync` sincroniza las cinco ligas, y
  `POST /players/sync?league=SERIE_A`, una sola. El administrador se configura con las tres
  variables `FUTBOLMARKET_AUTH_ADMIN_*`.

Bash, desde `backend/`:

    FOOTBALL_DATA_TOKEN='<token>' FUTBOLMARKET_AUTH_ADMIN_USERNAME='<usuario-admin>' FUTBOLMARKET_AUTH_ADMIN_EMAIL='<correo-admin>' FUTBOLMARKET_AUTH_ADMIN_PASSWORD='<contrasena-admin>' ./gradlew bootRun

PowerShell, desde `backend/`:

    $env:FOOTBALL_DATA_TOKEN = '<token>'; $env:FUTBOLMARKET_AUTH_ADMIN_USERNAME = '<usuario-admin>'; $env:FUTBOLMARKET_AUTH_ADMIN_EMAIL = '<correo-admin>'; $env:FUTBOLMARKET_AUTH_ADMIN_PASSWORD = '<contrasena-admin>'; ./gradlew bootRun

Las variables están detalladas en
[contracts/configuration.md](specs/005-integracion-football-data/contracts/configuration.md), y
la validación de punta a punta, en
[quickstart.md](specs/005-integracion-football-data/quickstart.md).

### Estadísticas de los partidos (WhoScored)

Las estadísticas de cada jugador en cada partido terminado de las cinco ligas se obtienen de
[WhoScored](https://www.whoscored.com) leyendo sus páginas, sin ninguna credencial. Se guardan,
pero ningún endpoint las devuelve todavía.

- **La cadena del lunes**: a las 04:00 de Argentina corre Football-Data y, al terminar, WhoScored,
  como una sola sincronización. Sin `FOOTBALL_DATA_TOKEN` saltea Football-Data y WhoScored corre
  igual sobre lo guardado.
- **La puesta al día al arrancar**: si la última sincronización completa exitosa de
  Football-Data tiene más de 7 días o no hay ninguna, corre la cadena; si solo la de WhoScored
  está vencida, corre solo WhoScored; si las dos están al día, no corre nada. Los 7 días se
  cambian con `FUTBOLMARKET_SYNC_CATCHUPAFTER`.
- **El disparo manual**: el administrador llama a `POST /matches/stats/sync` para las cinco ligas
  o a `POST /matches/stats/sync?league=PREMIER` para una sola. No necesita el token de
  Football-Data.
- Hay a lo sumo una sincronización a la vez, de cualquiera de las dos fuentes: un disparo manual
  con otra en curso responde 409.
- WhoScored se consulta una página a la vez, con al menos 4 segundos entre una y la siguiente.
  Sin snapshot, la primera vez tarda unos 47 minutos. Si WhoScored bloquea, la corrida se corta,
  lo guardado se conserva y lo que falta queda para la próxima.

**El snapshot**: `backend/snapshot/whoscored-stats.jsonl` está versionado. Cada sincronización
carga primero desde ahí los partidos pendientes que trae, sin consultar WhoScored, y solo scrapea
los demás. Se regenera con la aplicación detenida:

    cd backend
    ./gradlew exportStatsSnapshot

La tarea no levanta el servidor web ni sincroniza: lee la base local, escribe el archivo y
termina. Solo lleva equipos, partidos, jugadores y estadísticas, nunca usuarios ni claves.

**La base local no se borra**: una base que ya tiene el catálogo de Football-Data se conserva.
Al arrancar, `ddl-auto: update` agrega las columnas y las tablas nuevas sin tocar lo guardado, y
como todavía no hay ninguna completa registrada, la primera vez se ponen al día las dos fuentes.

Las propiedades están detalladas en
[contracts/configuration.md](specs/006-scraping-whoscored/contracts/configuration.md), y la
validación de punta a punta, en [quickstart.md](specs/006-scraping-whoscored/quickstart.md).

### Cotizaciones y estrategias de valuación

Cada jugador activo tiene una cotización semanal en créditos, calculada con las estadísticas de
WhoScored y la estrategia de valuación activa. Arranca en 100,00 y cada semana cerrada parte del
valor que dejó la anterior. Al arrancar quedan sembradas dos estrategias, "Rendimiento de partido"
(activa) y "Equilibrada por posición".

Endpoints públicos (sin credencial):

| Método y ruta | Qué devuelve |
|---|---|
| `GET /players/{id}/quotes/current` | La cotización vigente, el precio por token y la variación semanal |
| `GET /players/{id}/quotes?from=&to=&page=&size=` | El historial, con rango de fechas opcional (`AAAA-MM-DD`) |
| `GET /players/{id}/quotes/at?date=` | La cotización vigente en una fecha |
| `GET /players/quotes/current?ids=1,2,3` | Las vigentes de hasta 50 jugadores (la usa la pizarra) |
| `GET /players/ranking?league=&team=&position=&page=&size=` | El ranking por cotización, con la estrategia activa |

Endpoints de administrador (JWT o API key de un usuario con rol `ADMIN`):

| Método y ruta | Qué hace |
|---|---|
| `GET /strategies` y `GET /strategies/{id}` | Lista las estrategias, o una, con todas sus versiones |
| `POST /strategies` | Registra una estrategia (nombre, factor de escala, mínimo y pesos por posición) |
| `POST /strategies/{id}/versions` | Edita una estrategia: crea su versión siguiente, sin tocar las anteriores |
| `POST /strategies/{id}/activate` | La activa; con `{"requote": "AHORA"}` recotiza la última semana cerrada, y con `PROXIMA_SEMANA` (o sin cuerpo) rige desde la semana siguiente |
| `POST /quotes/recalculate` | Cotiza todas las semanas cerradas que falten y devuelve el informe |
| `POST /quotes/simulate?league=&team=&position=&page=&size=` | Simula una estrategia guardada o unos parámetros sin guardar nada |

- **El recálculo automático**: corre los lunes a las 06:00 de Argentina, después de la cadena de
  sincronización, y al arrancar si hay semanas cerradas sin cotizar. Una semana con partidos sin
  estadísticas espera hasta 7 días desde su cierre.
- Hay a lo sumo un recálculo a la vez y nunca junto con una sincronización: un disparo manual en
  ese caso responde 409, y el automático se omite con un aviso en el registro.
- Las propiedades, con sus valores por defecto:

  | Propiedad | Valor | Qué controla |
  |---|---|---|
  | `futbolmarket.quotes.recalculation.cron` | `0 0 6 * * MON` | Cuándo corre el recálculo semanal; `-` lo deshabilita |
  | `futbolmarket.quotes.recalculation.zone` | `America/Argentina/Buenos_Aires` | La zona en la que se interpreta el cron |
  | `futbolmarket.quotes.recalculation.on-startup` | `true` | Si recalcula al arrancar cuando hay semanas pendientes |

  Como las demás, se pisan con variables de entorno, por ejemplo
  `FUTBOLMARKET_QUOTES_RECALCULATION_ONSTARTUP=false`.

**La medición de volumen**: un test aparte carga 2.600 jugadores y 40 semanas de estadísticas
sintéticas en una H2 en archivo, reconstruye la temporada y verifica los tiempos del spec. Tarda
unos minutos y no corre en el build normal. Desde `backend/`:

    QUOTES_BENCHMARK=true ./gradlew test --tests '*QuoteVolumeBenchmarkIT' -i

En PowerShell: `$env:QUOTES_BENCHMARK = 'true'; ./gradlew test --tests '*QuoteVolumeBenchmarkIT' -i`.

Los contratos están en [contracts/](specs/007-cotizacion-estrategias/contracts/), y la validación de
punta a punta, en [quickstart.md](specs/007-cotizacion-estrategias/quickstart.md).

### Base local con el dataset ficticio

Si tu base local es de antes de Football-Data.org, tiene el dataset ficticio y tablas sin las
columnas nuevas. Hay que borrarla una sola vez, con la aplicación detenida:

    cd backend
    rm -f data/futbolmarket.mv.db data/futbolmarket.trace.db

Con eso también se pierden los usuarios locales. El administrador se recrea en el próximo
arranque con sus variables, y los usuarios comunes se registran de nuevo.

### Frontend

Requisitos: Node 20 o superior. Con el backend corriendo en `http://localhost:8080` (y con
`FOOTBALL_DATA_TOKEN`, si no el catálogo se ve vacío):

    cd frontend
    npm ci
    npm run dev

La app queda en http://localhost:5173. Más detalle en [frontend/README.md](frontend/README.md).

## Tests

    cd backend
    ./gradlew test

La suite no necesita token ni conexión a internet: nunca llama a la API de Football-Data.org
ni consulta WhoScored.

## Estructura

    backend/   Spring Boot 4.1.1 + Java 21 + Gradle
    frontend/  React + Vite + TypeScript (pizarra táctica)

El backend es un monolito organizado primero por capa y, dentro de cada capa, por contexto
(`auth`, `user`, `player`, `team`, `league`, `position`, `season`, `match`, `performance`,
`sync`, `strategy`, `quote`). `performance` tiene las estadísticas de cada jugador en cada
partido; `strategy`, las estrategias de valuación, y `quote`, las cotizaciones semanales. Las fuentes
externas van en la capa `adapter`, organizada por proveedor: `footballdata` (la API de
Football-Data.org) y `whoscored` (las páginas de WhoScored). El árbol lo fija el Principio I de
[la constitución](.specify/memory/constitution.md):

    backend/src/main/java/ar/edu/unq/desapp/futbolmarket/
    ├── controller/<contexto>/          controllers del contexto
    │   └── dto/                        Request y Response
    ├── service/<contexto>/             casos de uso
    ├── modelo/<contexto>/              modelo puro, sin anotaciones
    │   └── exception/                  excepciones de dominio
    ├── persistence/
    │   ├── repository/<contexto>/      envuelve al SQLDAO e invoca al mapper
    │   ├── mapper/<contexto>/          toDomain / toSQL
    │   └── sql/
    │       ├── entity/<contexto>/      clases *SQL con anotaciones JPA
    │       └── interfaces/<contexto>/  *SQLDAO extends JpaRepository
    ├── adapter/<proveedor>/            una fuente externa (footballdata, whoscored): cliente,
    │   │                               mapper y adapter
    │   └── dto/                        lo que devuelve el proveedor; nunca sale del adapter
    ├── security/                       transversal: filtros, JwtService, SecurityConfig
    ├── config/                         transversal: clases @Configuration
    └── shared/                         transversal: errores de la API y excepciones base

Los tests replican el mismo árbol en `backend/src/test/java/`.