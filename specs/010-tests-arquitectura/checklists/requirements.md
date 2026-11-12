# Specification Quality Checklist: Tests de arquitectura del backend y del frontend

**Purpose**: Validate specification completeness and quality before proceeding to planning
**Created**: 2026-11-12
**Feature**: [spec.md](../spec.md)

## Content Quality

- [x] No implementation details (languages, frameworks, APIs)
- [x] Focused on user value and business needs
- [x] Written for non-technical stakeholders
- [x] All mandatory sections completed

## Requirement Completeness

- [x] No [NEEDS CLARIFICATION] markers remain
- [x] Requirements are testable and unambiguous
- [x] Success criteria are measurable
- [x] Success criteria are technology-agnostic (no implementation details)
- [x] All acceptance scenarios are defined
- [x] Edge cases are identified
- [x] Scope is clearly bounded
- [x] Dependencies and assumptions identified

## Feature Readiness

- [x] All functional requirements have clear acceptance criteria
- [x] User scenarios cover primary flows
- [x] Feature meets measurable outcomes defined in Success Criteria
- [x] No implementation details leak into specification

## Notes

- Iteración 1 de la validación: pasaban todos los ítems menos uno. Quedaban tres marcas
  [NEEDS CLARIFICATION]: el idioma del detalle del mensaje, los tipos anidados en los DTO y dos reglas
  de la constitución que la lista no cubría.
- Iteración 2, con las respuestas del equipo (registradas en Clarifications, sesión 2026-11-12):
  - FR-022: la regla de nombres alcanza solo a las clases de primer nivel.
  - B3.6 (FR-017): se suma "el servicio no usa las clases `*SQL`".
  - El árbol cerrado del frontend queda fuera de alcance.
  - FR-004: el detalle de cada violación puede quedar con la redacción de la herramienta.

  Quedan 33 reglas (20 del backend y 13 del frontend) y pasan todos los ítems.
- Detalles técnicos que no cuentan como "de implementación": la feature trata sobre la arquitectura
  del código, así que las reglas nombran paquetes, carpetas, anotaciones y tipos (`JpaRepository`,
  `@RestControllerAdvice`, `fetch`, `sessionStorage`). Son el contenido de las reglas que pidió el
  equipo, no decisiones de diseño. ArchUnit, `./gradlew test`, `./gradlew build` y `npm test` figuran
  porque los exigen la cátedra y el pedido. Cómo se escriben las verificaciones y la herramienta del
  frontend quedan para el plan.
- Los "stakeholders" de esta feature son los desarrolladores y el docente. La spec explica cada regla
  con su porqué y deja a la vista lo único que cambia para el usuario final (el logo de TopBar).
- Lecturas propias de la spec, comprobadas contra el código actual y listadas en Edge Cases y
  Assumptions: `MarketColumns` no es entidad (B3.2), la paginación de Spring Data no es JPA (B3.1),
  `@Autowired` en constructores es inyección por constructor (B7.4), los imports de solo tipos cuentan
  como dependencia en el frontend, F10.2 compara el nombre base sin la extensión, y "regla propia"
  incluye las que reusan chequeos predefinidos.
