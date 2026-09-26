# Plan de implementación: Catálogo de jugadores

**Rama**: `002-catalogo-jugadores` | **Fecha**: 2026-09-16 | **Especificación**: [spec.md](./spec.md)

## Resumen

Implementar un catálogo REST de 50 a 60 jugadores de las cinco ligas europeas indicadas. El catálogo se persiste en H2 y se carga, de forma idempotente, desde un único archivo JSON bajo el perfil `local`. Expone listado paginado, filtros combinables por liga, equipo y posición, y detalle por identificador. Se conserva el modelo puro en `catalog/modelo`, con adaptadores JPA y DTOs HTTP separados según la constitución.

La entrada de planificación más reciente precisa que los valores inválidos de liga, posición o paginación reciben `400`; por tanto, esa regla prevalece para el contrato sobre la aclaración anterior de la especificación que indicaba una página vacía para filtros no admitidos. Los filtros con valores admitidos que no encuentran jugadores siguen devolviendo una página vacía.

## Contexto técnico

**Lenguaje/versión**: Java 21 (toolchain de Gradle).

**Dependencias principales**: Spring Boot 4.1.1, Spring MVC, Spring Data JPA, Spring Validation, H2 y Lombok; ya declaradas en `backend/build.gradle`.

**Almacenamiento**: H2 embebida: archivo persistente para `local` y memoria para `test`.

**Pruebas**: JUnit 5, Mockito, AssertJ, Spring Boot Test, H2 y MockMvc.

**Plataforma destino**: API REST Spring Boot, puerto 8080, ejecutable localmente con el Gradle wrapper.

**Tipo de proyecto**: Monorepo; esta entrega modifica exclusivamente el backend de una aplicación web.

**Objetivos de rendimiento**: Navegación determinista de un catálogo inicial de 50–60 jugadores; no se define un objetivo de latencia o concurrencia cuantificado para Entrega 1.

**Restricciones**: Sin Docker, servicios externos, scraping, scheduler, autenticación ni dependencias nuevas. `spring.jpa.open-in-view` permanece en `false`; las relaciones JPA no salen por HTTP. El seeder corre solo en `local`, y debe ser idempotente.

**Escala/alcance**: Un dataset estático de 50–60 jugadores, 10–12 por liga, cinco ligas, cuatro posiciones y dos endpoints públicos de catálogo.

## Verificación constitucional

### Antes de investigación

| Puerta | Resultado | Evidencia en el diseño |
|---|---|---|
| Capas estrictas | Cumple | Controller → service → repository → SQLDAO; DTOs, modelo, entidades SQL y mappers tienen paquetes separados. |
| Modelo rico | Cumple | `Player` y `Team` protegen sus invariantes; el service solo coordina consultas y carga. |
| Validación por nivel | Cumple | Bean Validation/parámetros HTTP para forma, service para existencia, modelo para invariantes; excepciones propias y advice centralizado. |
| Estrategia de pruebas | Cumple | Se planifican unitarias de dominio/service/mapper, integración con H2 test y E2E aislados con MockMvc. |
| Persistencia portable | Cumple | Tablas explícitas, enums como texto, DAO Spring Data con derived queries/JPQL y sin SQL nativo. |
| Calidad e idioma | Cumple | Identificadores ingleses; documentación y mensajes españoles; inyección por constructor. |

No hay violaciones ni excepciones que justificar.

### Después del diseño

El modelo, contratos y guía de validación mantienen todas las puertas anteriores. No se incorporan dependencias, nuevas capas ni persistencia no portable.

## Estructura del proyecto

### Documentación de la feature

```text
specs/002-catalogo-jugadores/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── players-api.yaml
└── tasks.md                 # generado posteriormente por speckit-tasks
```

### Código fuente

```text
backend/
├── src/main/java/ar/edu/unq/desapp/futbolmarket/
│   ├── FutbolMarketApplication.java
│   ├── catalog/
│   │   ├── controller/
│   │   │   ├── dto/
│   │   │   └── PlayerController.java
│   │   ├── service/
│   │   ├── modelo/
│   │   └── persistence/
│   │       ├── mapper/
│   │       ├── repository/
│   │       └── sql/
│   │           ├── entity/
│   │           └── interfaces/
│   ├── config/
│   └── shared/
├── src/main/resources/
│   ├── application.yaml
│   ├── application-local.yml
│   └── data/players.json
└── src/test/
    ├── java/ar/edu/unq/desapp/futbolmarket/catalog/
    └── resources/application-test.yml
```

**Decisión de estructura**: Se usa el monorepo existente y solo `backend/`. `catalog/` agrupa la feature; `modelo/` sustituye cualquier placeholder `domain/` de primer nivel, como obliga la constitución. `shared/` aloja el advice y las excepciones transversales que requiera el formato de error común.

## Seguimiento de complejidad

No aplica: la verificación constitucional no tiene violaciones.

## Revisión final de arquitectura

Revisado el 2026-09-25: la ruta de dependencias se mantiene como controller → service → repository → DAO. Los DTOs permanecen en `catalog/controller/dto`, el modelo en `catalog/modelo` no usa anotaciones JPA y los mappers aíslan las entidades `*SQL` dentro de persistencia. `GlobalExceptionHandler` centraliza los mensajes HTTP en español. No se agregaron dependencias al `build.gradle` para esta entrega.
