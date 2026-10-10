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
- Con token y el catálogo vacío, al arrancar dispara una sincronización completa en segundo
  plano. En menos de un minuto quedan unos 2.600 jugadores.
- Después se sincroniza sola los lunes a las 04:00 de Argentina, si la aplicación está
  encendida a esa hora. Una corrida perdida no se recupera.
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

### Base local con el dataset ficticio

Si tu base local es de antes de Football-Data.org, tiene el dataset ficticio y tablas sin las
columnas nuevas. Hay que borrarla una sola vez, con la aplicación detenida:

    cd backend
    rm -f data/futbolmarket.mv.db data/futbolmarket.trace.db

Con eso también se pierden los usuarios locales. El administrador se recrea en el próximo
arranque con sus variables, y los usuarios comunes se registran de nuevo.

## Tests

    cd backend
    ./gradlew test

La suite no necesita token ni conexión a internet: nunca llama a la API real.

## Estructura

    backend/   Spring Boot 4.1.1 + Java 21 + Gradle
    frontend/  React + Vite (entrega 2)

El backend es un monolito organizado primero por capa y, dentro de cada capa, por contexto
(`auth`, `user`, `player`, `team`, `league`, `position`, `season`, `match`, `sync`). Las APIs
externas van en la capa `adapter`, organizada por proveedor. El árbol lo fija el Principio I de
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
    ├── adapter/<proveedor>/            una API externa (footballdata): cliente, mapper y adapter
    │   └── dto/                        JSON del proveedor; nunca sale del adapter
    ├── security/                       transversal: filtros, JwtService, SecurityConfig
    ├── config/                         transversal: clases @Configuration
    └── shared/                         transversal: errores de la API y excepciones base

Los tests replican el mismo árbol en `backend/src/test/java/`.