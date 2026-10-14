# Specification Quality Checklist: Scraping de WhoScored

**Purpose**: Validate specification completeness and quality before proceeding to planning
**Created**: 2026-10-13
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

### Iteracion 1 - 2026-10-13

Quedaron 3 marcadores [NEEDS CLARIFICATION] abiertos, sobre ambiguedades que las decisiones
del prompt no resuelven:

1. **FR-017**: si un equipo cuyo nombre normalizado no coincide con el del catalogo
   ("Man Utd" contra "Manchester United FC") se cruza solo por sus partidos o queda sin
   cruzar.
2. **FR-021**: si un jugador que cambio de equipo dentro de las cinco ligas se busca fuera
   de su equipo para los partidos que jugo con el anterior.
3. **FR-035**: si la de WhoScored se pone al dia sola al arrancar por su propia ultima
   sincronizacion completa exitosa, o solo dentro de la cadena de FR-034.

Del tercero depende el escenario 3 de la User Story 3, que por ahora remite al requisito.
Por eso tambien quedan abiertos "Requirements are testable and unambiguous" y "All
acceptance scenarios are defined".

Las 12 decisiones tomadas quedaron en Clarifications (Session 2026-10-13) y los criterios
por defecto, como requisitos o en Assumptions. Los cambios sobre la 005 quedaron
explicitos en FR-050 a FR-055 y en Dependencies.

Sobre los detalles de implementacion: el spec nombra WhoScored y Football-Data.org porque
el enunciado los exige, el rol ADMIN porque lo define 001 y "cliente HTTP o navegador sin
ventana" solo para dejar esa eleccion fuera de alcance. No nombra recursos, codigos de
respuesta, clases, paquetes, librerias ni campos de los datos de WhoScored.

Inferencias propias, que no estaban en el prompt y conviene revisar:

- **FR-005**: la expulsion cuenta como salida para los minutos.
- **FR-006**: un gol en contra no suma gol a su autor. El prompt solo lo decia para las
  asistencias.
- **FR-007**: la doble amarilla cuenta como una amarilla y una roja.
- **FR-014**: un partido que WhoScored todavia no da por terminado, o que no aparece, se
  omite sin contar como falla. Sin esto, un partido sin publicar cortaria cada corrida
  como si fuera un bloqueo.
- **FR-015**: el partido se cruza por local y visitante dentro de la liga y la temporada,
  sin depender de la fecha (que puede diferir por zona horaria o por reprogramacion).
- **FR-022**: el cruce de jugadores es uno a uno.
- **FR-027**: un snapshot que no se puede leer completo se ignora entero.
- **FR-031**: regenerar el snapshot conserva las entradas del vigente que la base local no
  tiene y nunca las cambia. El prompt decia "lo regenera desde su base local"; sin esto,
  regenerar desde una base nueva perderia, por ejemplo, a los jugadores que ya dejaron las
  cinco ligas.
- **FR-041**: una cadena cuenta como una sola sincronizacion en curso de punta a punta. El
  prompt decia que la corrida programada o de arranque "espera o se omite"; se interpreto
  que la de WhoScored espera a la de Football-Data dentro de su cadena y que una cadena
  que encuentra otra sincronizacion en curso se omite, como en la 005.
- **FR-047**: que es una sincronizacion completa exitosa de WhoScored (cinco ligas, sin
  corte por bloqueo; los omitidos no le quitan el exito).
- **SC-002**: el piso de 95% de jugadores cruzados.
- **SC-004 a SC-006**: los tiempos (15 minutos por fecha, 60 desde el inicio sin
  snapshot), con margen sobre lo medido el 2026-10-13.
- **Assumptions**: el entorno de tests no consulta WhoScored real y no corre la puesta al
  dia salvo que un test la habilite; el disparo manual puede tardar hasta unos 47 minutos.

### Iteracion 2 - 2026-10-13

Las tres consultas se resolvieron con Lucas (opcion A en las tres) y quedaron en
Clarifications, Session 2026-10-13, junto con un cambio que pidio sobre los minutos:

1. **Equipos con nombres distintos (FR-017a, FR-018)**: si el nombre normalizado no
   coincide, el equipo se cruza por la coincidencia de sus partidos de liga (fecha,
   localia y rivales) con los de un unico equipo del catalogo de su liga. Se sumaron el
   escenario 11 de la User Story 1, dos casos borde y SC-003a (100% de los 96 equipos
   cruzados).
2. **Jugadores transferidos (FR-019a)**: sin candidato en el equipo del partido, se busca
   en todo el catalogo, activos o inactivos, con el mismo nombre normalizado y la misma
   edad, y se cruza solo si hay uno unico. Se sumaron el escenario 9 de la User Story 1 y
   tres casos borde.
3. **Puesta al dia de WhoScored (FR-035)**: cada fuente tiene su vencimiento; con la de
   Football-Data al dia y la de WhoScored de mas de 7 dias (o sin ninguna), corre solo la
   de WhoScored. Se reescribio el escenario 3 de la User Story 3 y se sumaron los
   escenarios 4 y 14, un caso borde y la nueva forma de SC-012.
4. **Minutos (FR-005)**: incluyen el tiempo agregado de los dos tiempos. Se reescribieron
   los escenarios 4 y 5 de la User Story 1 con un partido de 2 + 4 minutos agregados, los
   casos borde del agregado y el supuesto sobre el umbral de 90 minutos, que ahora queda
   para la Entrega 2.

Ajustes de consistencia derivados: la entidad Sincronizacion (la puesta al dia puede
correr solo la de WhoScored), la entidad Ultima sincronizacion completa exitosa (decide la
puesta al dia de cada fuente), el escenario 9 de la User Story 3 (una base de la 005 no
tiene registro de ninguna fuente) y el supuesto sobre la edad, que ahora cubre tambien la
busqueda en todo el catalogo. Los requisitos nuevos usan sufijo (FR-017a, FR-019a,
SC-003a) para no renumerar los que ya cita la seccion de cambios sobre la 005.

### Resultado

16 de 16 items en verde. Estado final: 4 historias de usuario, 57 requisitos funcionales
(FR-001 a FR-055 mas FR-017a y FR-019a), 18 criterios de exito (SC-001 a SC-017 mas
SC-003a) y 0 marcadores [NEEDS CLARIFICATION]. Listo para `/speckit-plan` (o
`/speckit-clarify` si se quiere una pasada mas).
