# Investigación: Catálogo de jugadores

## Decisiones

### Mantener el stack ya provisto

- **Decisión**: Usar Java 21, Spring Boot 4.1.1, Spring MVC, Spring Data JPA, Validation, H2 y Lombok ya presentes.
- **Justificación**: El esqueleto tiene el wrapper, los starters y los perfiles requeridos. La constitución prohíbe dependencias nuevas sin justificación, Docker, PostgreSQL y Testcontainers.
- **Alternativas consideradas**: Una base externa, Testcontainers o librerías de búsqueda; se descartan por el alcance local, el dataset pequeño y las prohibiciones del proyecto.

### Separar modelo y persistencia explícitamente

- **Decisión**: Modelar `Player` y `Team` como objetos puros en `catalog/modelo`; persistir `PlayerSQL` y `TeamSQL` bajo `catalog/persistence/sql/entity`; traducir con mappers bidireccionales invocados desde repositories.
- **Justificación**: Mantiene al servicio ajeno a JPA y cumple la arquitectura en capas y el modelo rico definidos en la constitución.
- **Alternativas consideradas**: Anotar el modelo de dominio con JPA o inyectar DAOs en el service; se descartan porque rompen esas fronteras.

### Consultar con filtros opcionales y orden estable

- **Decisión**: Representar los filtros como un objeto de criterios de modelo y resolverlos mediante consultas JPA/derived queries o una especificación JPA, siempre ordenadas por identificador ascendente antes de paginar.
- **Justificación**: Los filtros son combinables con AND y el orden estable evita omisiones o repeticiones entre páginas.
- **Alternativas consideradas**: Filtrar toda la colección en el controller o usar SQL nativo; se descartan por mezclar responsabilidades y por la prohibición de SQL nativo.

### Carga local, estática e idempotente

- **Decisión**: Crear `data/players.json` como fuente única y un componente activado únicamente con el perfil `local`; antes de insertar, comprobar la presencia del catálogo/`externalId` y reutilizar o crear los equipos por nombre y liga.
- **Justificación**: H2 local usa archivo con `ddl-auto: update`; el guard evita duplicados tras reinicios. El perfil `test` conserva la base en memoria y no ejecuta la carga.
- **Alternativas consideradas**: `data.sql`, inicialización en cada perfil o una API externa; se descartan por falta de idempotencia controlada, contaminación de pruebas y alcance funcional.

### Validar entradas HTTP y preservar resultados vacíos válidos

- **Decisión**: Aceptar solo los valores de los enums `League` y `Position`, `page >= 0` y `size > 0`; los formatos/valores inválidos responden `400` mediante el formato de error común. Los criterios admitidos sin coincidencias responden `200` con contenido vacío.
- **Justificación**: El texto de entrada de planificación establece explícitamente `400` para liga, posición o paginación inválidas y preserva la semántica de búsqueda sin coincidencias.
- **Alternativas consideradas**: Devolver una página vacía para enum inválido, como dice una aclaración anterior de `spec.md`; se descarta por estar contradicha por la entrada más reciente. Antes de implementación se debe actualizar la especificación para eliminar esa inconsistencia.

### Contrato HTTP y errores

- **Decisión**: Exponer `GET /players` y `GET /players/{id}` con respuestas DTO; traducir `PlayerNotFoundException` a `404` y errores de request a `400` desde el único `@RestControllerAdvice`.
- **Justificación**: El controller no expone dominio/JPA y los códigos describen inequívocamente la situación al cliente.
- **Alternativas consideradas**: Retornar entidades JPA o propagar excepciones del framework; se descartan por violar la constitución y filtrar detalles internos.

## Hallazgos que condicionan la implementación

- `application.yaml` ya deja `spring.jpa.open-in-view: false`; el mapeo de asociaciones debe terminar antes de crear DTOs.
- `local` usa `jdbc:h2:file:./data/futbolmarket;AUTO_SERVER=TRUE` y `test` usa H2 en memoria con `create-drop`; no se alteran estos perfiles.
- El backend no tiene aún código de catálogo ni un formato de error existente. El contrato propone `ApiError` mínimo; su implementación debe integrarse con el trabajo paralelo de seguridad/Swagger sin cambiar el comportamiento de `/players`.
