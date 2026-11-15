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
| `DELETE /strategies/{id}` | La elimina (baja lógica): deja de listarse y de poder usarse, pero se conserva para las cotizaciones que calculó, renombrada a "X (eliminada el dd/mm/aaaa hh:mm)", así el nombre queda libre. Una de arranque eliminada no vuelve al reiniciar. La activa no se elimina (409) |
| `POST /quotes/recalculate` | Cotiza todas las semanas cerradas que falten y devuelve el informe |
| `POST /quotes/simulate?league=&team=&position=&playerId=&page=&size=` | Simula una estrategia guardada o unos parámetros sin guardar nada; con `playerId`, la hoja trae solo a ese jugador |

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

### Jugadores potenciados

El administrador elige 4 jugadores por semana (de lunes a domingo, en hora de Argentina) y cuánto se
potencia cada uno, de 200 % a 400 % de a 25 %. Todos los ven destacados arriba de la pizarra durante
esa semana. En el cierre, cuando se calcula la cotización de la semana, el factor de escala de cada
potenciado se multiplica por su porcentaje, así su cotización sube o baja más que la del resto. La
cotización vigente no cambia al elegirlos. Mientras la semana está en curso, también rigen en cualquier
recotización (editar o activar la estrategia con `AHORA` recotiza la última semana cerrada con los
potenciados que se ven en el inicio); al cerrar la semana se les aplica una vez más, y después rigen
los de la semana siguiente. La simulación usa los de cada semana que reproduce.

| Método y ruta | Acceso | Qué hace |
|---|---|---|
| `GET /players/boosts/current` | Público | Los potenciados de la semana en curso, con su porcentaje y el instante del cierre; vacía si no hay |
| `GET /quotes/boosts?week=AAAA-MM-DD` | `ADMIN` | Los de cualquier semana (cualquier día indica la semana que lo contiene) y si está cerrada, en curso o es próxima |
| `PUT /quotes/boosts/{AAAA-MM-DD}` | `ADMIN` | Reemplaza los de la semana: exactamente 4 jugadores distintos, o una lista vacía para dejarla sin potenciados. La semana en curso se puede cambiar; una cerrada responde 409 |

La pantalla es `/admin/potenciados`. Cambiar los de la semana en curso pide confirmación, porque
todos ya los ven en el inicio.

### Mercado de tokens y portfolio

Cada jugador tiene 100 tokens. Los usuarios comunes los compran y los venden con sus créditos, al
precio por token de la cotización vigente (el 1 % de su valor), y siguen su portfolio.

- **El superusuario es el administrador de la 001**: la cuenta que se crea con las tres variables
  `FUTBOLMARKET_AUTH_ADMIN_*`. Es el dueño de los 100 tokens iniciales de cada jugador y los vende
  a 1,0000 en una orden inicial. **Sin su contraseña no se crea, y el mercado no inicia**: no se
  generan órdenes iniciales (el registro lo advierte) y se generan en el próximo arranque con la
  cuenta configurada. El administrador consulta y audita, pero no compra ni vende.
- **Las órdenes iniciales**: al arrancar se crean en segundo plano las que falten, una por jugador
  del catálogo (unas 2.600 con el catálogo completo), y cada sincronización de una liga crea las de
  sus jugadores nuevos. Reiniciar o sincronizar nunca las duplica. La propiedad
  `futbolmarket.market.initial-orders.automatic` (por defecto `true`; `false` en el perfil `test`)
  apaga las dos cosas. Como las demás, se pisa con una variable de entorno:
  `FUTBOLMARKET_MARKET_INITIALORDERS_AUTOMATIC=false`.
- **Concurrencia**: cada pedido bloquea el libro del jugador y las cuentas que toca. En el perfil
  `local`, la URL de H2 lleva `;LOCK_TIMEOUT=10000` para que un pedido espere hasta 10 segundos a
  otro en vez de 1; si igual no consigue el bloqueo, responde 409 ("El mercado está ocupado") sin
  cambiar nada.

Endpoints y la pantalla de la web que usa cada uno:

| Método y ruta | Acceso | Qué hace | Pantalla |
|---|---|---|---|
| `POST /orders/buy` | `USER` | Compra tokens de un jugador (`{"playerId": 12, "quantity": 10}`, de 1 a 100) | La ficha del jugador (`/jugadores/{id}`), botón "Comprar" |
| `POST /orders/sell` | `USER` | Vende tokens propios; bloqueada mientras al superusuario le queden tokens del jugador | La ficha del jugador, botón "Vender" |
| `POST /orders/{id}/cancel` | `USER` (dueño) | Cancela el remanente de una orden propia | `/ordenes` (Mis órdenes) |
| `GET /orders?side=&league=&team=&ownerType=&playerId=&page=&size=` | Público | Las órdenes pendientes, de la más vieja a la más nueva, sin datos del dueño | `/mercado`, y las pendientes de la ficha |
| `GET /users/{id}/portfolio[?date=AAAA-MM-DD]` | Dueño o `ADMIN` | El portfolio actual, o reconstruido a una fecha | `/portfolio` |
| `GET /users/{id}/transactions?playerId=&side=&from=&to=&page=&size=` | Dueño o `ADMIN` | El historial de operaciones, de la más nueva a la más vieja | `/operaciones` |
| `GET /users/{id}/orders?status=&page=&size=` | Dueño o `ADMIN` | Las órdenes del usuario; sin `status`, en cualquier estado, y con uno o varios (`status=PENDING,PARTIAL`), solo esas | `/ordenes` (Mis órdenes) |
| `GET /audit?eventType=&userId=&playerId=&from=&to=&page=&size=` | `ADMIN` | La auditoría: cada orden creada, operación y cancelación, con el antes y el después de cada parte | `/admin/auditoria` (Administración → Auditoría) |

Sin credencial, los endpoints que la piden responden 401; el administrador en `POST /orders/**` y
un usuario común en `/audit` reciben 403 antes del controller, y un usuario común que pide la
cuenta de otro, 403. Los saldos libre y reservado se ven en `GET /auth/me` y en la barra de la web,
y cada cotización informa su score.

**La medición de volumen del mercado**: un test aparte carga 2.600 jugadores con su cotización en
una H2 en archivo, crea sus órdenes iniciales (registra cuánto tarda) y verifica que una compra, el
portfolio actual y a una fecha, una hoja del historial y una del listado tarden menos de 2 s cada
uno. No corre en el build normal. Desde `backend/`:

    MARKET_BENCHMARK=true ./gradlew test --tests '*MarketVolumeIT' -i

En PowerShell: `$env:MARKET_BENCHMARK = 'true'; ./gradlew test --tests '*MarketVolumeIT' -i`.

El contrato está en [contracts/](specs/008-mercado-tokens-portfolio/contracts/), y la validación de
punta a punta, en [quickstart.md](specs/008-mercado-tokens-portfolio/quickstart.md).

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

## Tests de arquitectura

Son tests comunes, del backend (JUnit con ArchUnit) y del frontend (Vitest), que leen el código
y verifican que respete la arquitectura de [la constitución](.specify/memory/constitution.md):
qué capa o carpeta puede usar a cuál, dónde vive cada cosa y qué no se usa nunca. Corren solos
en cada `./gradlew build` y cada `npm test`, en local y en el CI, sin Spring, sin base y sin red.
Fallan cuando un cambio rompe una regla, aunque compile y todos los demás tests pasen.

**Ante un fallo se corrige el código, no la regla.** Si la regla parece equivocada para ese caso,
se frena y se consulta al equipo: no se agregan excepciones por clase o archivo, no se congela la
violación (`FreezingArchRule`, `archunit.properties`) y no se desactiva ninguna parte de la regla.

### Cómo se corren

| Qué | Dónde | Comando |
|---|---|---|
| Solo las reglas del backend | `backend/` | `./gradlew test --tests "ar.edu.unq.desapp.futbolmarket.architecture.*"` |
| Solo las reglas del frontend | `frontend/` | `npx vitest run tests/architecture.test.ts` |
| Todo, como en el CI | `backend/` y `frontend/` | `./gradlew build` y `npm test` |

Los nombres de los tests (en español) dicen qué regla verifican. En el backend, cada regla es un
test de un `*RulesTest` (contra el código real) y uno o más de
`ArchitectureRulesDetectViolationsTest` (contra clases que la rompen a propósito, en
`ar.edu.unq.desapp.archfixture`). En el frontend, cada regla tiene un `it` en "código real" y al
menos uno en "casos borde".

### Reglas del backend (20)

Paquetes relativos a `ar.edu.unq.desapp.futbolmarket`. "Depender" incluye campo, parámetro,
retorno, argumento genérico, anotación, herencia, llamada y acceso.

| Id | Nombre | Qué verifica | Por qué |
|---|---|---|---|
| B1 | Dirección de capas | Nadie fuera de `controller` depende de `controller`, y nadie fuera de `security` depende de `security`. De `service` solo dependen `controller`, `security` y el paquete raíz. De `persistence` y de `adapter`, solo `service`. Vale también para `modelo`, `config` y `shared` | Principio I: el controller solo habla con el servicio, el servicio con la persistencia y el adapter, y ninguna capa se saltea |
| B2 | Modelo puro | `modelo` depende solo de la JDK, de `modelo`, de Lombok y de las excepciones base de `shared` | Principio I: el modelo no lleva anotaciones de persistencia ni conoce Spring ni otras capas |
| B3.1 | JPA solo en persistence/sql | Fuera de `persistence.sql` nadie depende de `jakarta.persistence`, Hibernate ni Spring Data JPA (la paginación de Spring Data no cuenta) | Principio I: las anotaciones y la API de JPA viven únicamente en las clases \*SQL y en sus SQLDAO |
| B3.2 | Entidades \*SQL | Toda `@Entity` o `@Embeddable` vive en `persistence.sql.entity` y termina en `SQL` | Principio I: cada clase de modelo tiene su contraparte \*SQL en persistence/sql/entity |
| B3.3 | SQLDAO | Todo lo que extiende `JpaRepository`, vive en `persistence.sql.interfaces` o termina en `SQLDAO` es una interfaz de `persistence.sql.interfaces`, termina en `SQLDAO` y extiende `JpaRepository` | Principio I: los \*SQLDAO son interfaces de persistence/sql/interfaces que extienden JpaRepository |
| B3.4 | SQLDAO y mappers solo desde repository | Los SQLDAO se usan solo desde `persistence.repository`; los mappers, desde `persistence.repository` y otros mappers | Principio I: repository es el único punto donde conviven modelo y persistencia, y es quien invoca al SQLDAO y al mapper |
| B3.5 | Sin queries nativas | Ningún `@Query(nativeQuery = true)`, ningún `@NativeQuery`, ninguna llamada a `createNativeQuery` | Principio VI: solo derived queries y JPQL, nunca SQL nativo |
| B3.6 | El servicio no conoce las entidades | `service` no depende de `persistence.sql.entity` | Principio I: el servicio recibe y devuelve modelo y no conoce JPA ni las clases \*SQL |
| B4.1 | Controllers REST | Todo `@RestController` vive en `controller.<contexto>` y termina en `Controller` | Principio I: los controllers viven en controller/\<contexto>/ y se llaman \*Controller |
| B4.2 | DTO del controller | Lo de `controller.<contexto>.dto` se usa solo desde `controller`; sus clases de primer nivel terminan en `Request` o `Response` (los tipos anidados no necesitan sufijo) | Principio I: los Request y Response viven en controller/\<contexto>/dto/ y nunca cruzan hacia el servicio |
| B4.3 | Qué devuelve un endpoint | Ningún método público de un `@RestController` devuelve una clase de `modelo` ni de `persistence`, ni directa ni dentro de un genérico (en cualquier nivel) ni en un arreglo | Principio I: ninguna clase de modelo ni de persistencia sale en una respuesta HTTP; sale siempre un Response |
| B5.1 | DTO del proveedor | Lo de `adapter.<proveedor>.dto` se usa solo desde `adapter` | Principio I: los DTO del proveedor viven en adapter/\<proveedor>/dto/ y nunca salen del adapter |
| B5.2 | Adapter independiente | `adapter` no depende de `service`, `controller` ni `persistence` | Principio I: el adapter recibe y devuelve modelo; no conoce el servicio, el controller ni la persistencia |
| B5.3 | Clientes HTTP | Solo `adapter` y `config` usan clientes HTTP: `RestClient` y compañía (`org.springframework.web.client`), `WebClient`, `org.springframework.http.client` y `java.net.http` | Principio I: el servicio habla con una API externa solo a través de su adapter y nunca usa un cliente HTTP |
| B6 | Servicios sin ciclos | Los contextos de `service` no forman ciclos | Principio I (reglas de monolito): un servicio puede depender de otro solo si no se forma un ciclo |
| B7.1 | Sin salida estándar | Nadie usa `System.out`, `System.err` ni `printStackTrace()` | Principio V: nada de System.out; el logging va con SLF4J |
| B7.2 | Sin excepciones genéricas | Nadie crea `Exception`, `RuntimeException`, `Throwable` ni `Error` (las subclases propias sí) | Principio III: las excepciones son propias y con nombre; prohibido throw new RuntimeException(...) |
| B7.3 | Sin java.util.logging | Nadie usa `java.util.logging` | Principio V: el logging va con SLF4J |
| B7.4 | Sin inyección por campo | Ningún campo con `@Autowired`, `@Value`, `@Inject` ni `@Resource` | Principio V: inyección por constructor, nunca @Autowired sobre el campo |
| B7.5 | Un solo manejador de excepciones | Hay exactamente un `@RestControllerAdvice` (o `@ControllerAdvice`) y vive en `shared` | Principio III: un único @RestControllerAdvice en shared/ centraliza el manejo de errores |

### Reglas del frontend (13)

Rutas relativas a `frontend/`. "Usar" una carpeta es importar cualquier archivo de ella, aunque
traiga solo tipos. El porqué de todas sale de la sección Frontend de la constitución.

| Id | Nombre | Qué verifica | Por qué |
|---|---|---|---|
| F1 | Componentes sin API ni router | `src/components/` usa solo `components/`, `types/`, `utils/` y el paquete `react` | los componentes reciben datos y callbacks por props; no conocen la API, el router, los hooks ni los contextos |
| F2 | utils puro | `src/utils/` usa solo `utils/` y `types/`, sin paquetes | utils son funciones puras: no usan React, otros paquetes ni services |
| F3 | types | `src/types/` usa solo `types/` | types tiene solo los tipos de la API y del dominio del front |
| F4 | services | `src/services/` usa solo `services/` y `types/`, sin paquetes | services es el cliente HTTP y una función por endpoint; no conoce React ni la interfaz |
| F5 | hooks y context | `src/hooks/` y `src/context/` no usan `components/`, `pages/` ni `router/` | la lógica con estado vive en hooks y context, que no conocen los componentes, las pantallas ni las rutas |
| F6.1 | Quién usa pages | Fuera de `router/` y `pages/` nadie usa `pages/` | hay una pantalla por ruta: las pantallas las usa el router |
| F6.2 | Quién usa router | Fuera de `main.tsx` y `router/` nadie usa `router/` | la definición de rutas la usa solo main.tsx |
| F7 | Red y almacenamiento | Fuera de `services/` nadie usa `fetch`, `sessionStorage` ni `localStorage`, tampoco por `window`, `globalThis` o `self` | los componentes y las páginas nunca llaman a fetch directo, y la sesión se guarda solo desde services |
| F8.1 | Paquetes permitidos | `src/` importa solo `react`, `react-dom` y `react-router`, y sus subrutas | sin frameworks de UI ni paquetes fuera del stack |
| F8.2 | Nada de afuera | `src/` no importa nada fuera de `src/` (ni de `mockups/`) | mockups son diseños de referencia y no se importan desde src |
| F9 | Sin ciclos | Los imports entre archivos de `src/` no forman ciclos | un ciclo ata dos archivos que tienen que poder entenderse y probarse por separado |
| F10.1 | Tests fuera de src | `src/` no tiene archivos `.test.*` ni `.spec.*` | los tests viven solo en frontend/tests/, nunca junto al código |
| F10.2 | Correspondencia de tests | Cada test de `tests/components/`, `tests/hooks/`, `tests/services/` y `tests/utils/` tiene un archivo de `src/` con el mismo nombre base en la carpeta del mismo nombre | tests/ replica la estructura de src/: el test de src/\<carpeta>/X.tsx está en tests/\<carpeta>/X.test.tsx |

### Cómo leer un fallo

1. **El identificador** (`[B1]`, `[F7]`) dice qué regla se rompió; se busca en las tablas de
   arriba.
2. **El porqué** cita el principio o la sección de la constitución que origina la regla.
3. **Los infractores**:
   - Backend: debajo de `Infractores (n):`, una línea por violación con la clase, el método o
     el campo, el tipo del que depende y el archivo con su línea. Está en inglés (es la
     redacción de ArchUnit), salvo B4.3 y B7.5, que escriben en español. B6 muestra el ciclo
     entre contextos y las dependencias que lo forman.
   - Frontend: una línea por violación con el archivo y el import, paquete o identificador que
     la rompe. F9 muestra el ciclo completo.

Por ejemplo:

    [B1] Dirección de capas, porque Principio I: el controller solo habla con el servicio, el servicio con la persistencia y el adapter, y ninguna capa se saltea
    Infractores (1):
    - Constructor <...RepositoryAwareController.<init>(...SampleRepository)> has parameter of type <...SampleRepository> in (RepositoryAwareController.java:0)

    [F1] src/components/TopBar.tsx usa react-router: los componentes reciben datos y callbacks por props; no conocen la API, el router, los hooks ni los contextos (Constitución, sección Frontend)

## Estructura

    backend/   Spring Boot 4.1.1 + Java 21 + Gradle
    frontend/  React + Vite + TypeScript (pizarra táctica)

El backend es un monolito organizado primero por capa y, dentro de cada capa, por contexto
(`auth`, `user`, `player`, `team`, `league`, `position`, `season`, `match`, `performance`,
`sync`, `strategy`, `quote`, `market`). `performance` tiene las estadísticas de cada jugador en cada
partido; `strategy`, las estrategias de valuación; `quote`, las cotizaciones semanales, y `market`,
las órdenes, las operaciones, las tenencias, el portfolio y la auditoría. Las fuentes
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