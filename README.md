# Valoración de mercado de jugadores de fútbol

TP de Desarrollo de Aplicaciones — 2do Semestre 2026

## Cómo levantarlo

Requisitos: Java 21. Nada más — la base es embebida.

    cd backend
    ./gradlew bootRun

- API: http://localhost:8080
- Health: http://localhost:8080/actuator/health
- Consola H2: http://localhost:8080/h2-console

## Tests

    cd backend
    ./gradlew test

## Estructura

    backend/   Spring Boot 4.1.1 + Java 21 + Gradle
    frontend/  React + Vite (entrega 2)

El backend es un monolito organizado primero por capa y, dentro de cada capa, por contexto
(`auth`, `user`, `player`, `team`, `league`, `position`). El árbol lo fija el Principio I de
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
    ├── security/                       transversal: filtros, JwtService, SecurityConfig
    ├── config/                         transversal: clases @Configuration
    └── shared/                         transversal: errores de la API y excepciones base

Los tests replican el mismo árbol en `backend/src/test/java/`.