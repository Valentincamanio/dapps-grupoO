# Specification Quality Checklist: Registro, credenciales y acceso autenticado

**Purpose**: Validate specification completeness and quality before proceeding to planning
**Created**: 2026-09-14
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

### Iteracion 1 - 2026-09-14

Quedaron 2 marcadores [NEEDS CLARIFICATION] abiertos: el monto del saldo inicial (FR-012) y
si esta funcionalidad debia dar de alta algun usuario administrador (entidad Rol).

El resto de los puntos ambiguos se resolvio con valores por defecto razonables, documentados
en la seccion Assumptions del spec: comparacion de nombre de usuario y correo sin distinguir
mayusculas, el registro no emite token de sesion, un conflicto doble informa los dos campos,
alcanza con que una credencial sea valida si llegan las dos, y una credencial invalida no
bloquea un recurso publico.

### Iteracion 2 - 2026-09-14

Las dos consultas se resolvieron con el equipo y el spec se actualizo:

1. **Saldo inicial (FR-012)**: pasa a ser un parametro de configuracion resuelto por
   entorno, igual para todos los usuarios que se registren bajo esa configuracion, con un
   valor por defecto. El valor por defecto elegido, 1000 creditos, queda documentado y
   justificado en Assumptions; el equipo no fijo un monto, y al ser configuracion se ajusta
   en un solo lugar.
2. **Administrador (User Story 7, FR-036 a FR-042)**: esta funcionalidad da de alta
   exactamente un administrador al arrancar, con credenciales leidas de variables de
   entorno, sin ningun valor por defecto en el repositorio. El alta es idempotente, omite
   ejecutarse y advierte si la configuracion falta o es invalida, guarda la contrasena de
   forma no reversible, no emite clave de API y deja el saldo en cero. Lo unico que se
   garantiza es que la cuenta exista con su rol.

Ajustes de consistencia derivados:

- **FR-034** pasa de "una unica clave de API vigente" a "a lo sumo una": el administrador
  nace sin ninguna.
- **FR-013** se acota explicitamente al registro publico, ya que el administrador no recibe
  clave de API al sembrarse.
- **FR-011** apunta a FR-036 como la unica via por la que existe un administrador.
- **FR-038** extiende el tratamiento de la contrasena faltante a la configuracion incompleta
  o invalida segun FR-002, FR-003 y FR-004. Es una inferencia sobre lo que pidio el equipo,
  senalada aca a proposito.
- Se sumaron SC-011 a SC-013, cuatro casos borde del alta y la dependencia con la
  funcionalidad de mercado.

### Iteracion 3 - 2026-09-18

Enmiendas surgidas de `/speckit-plan`, registradas en la seccion Clarifications del spec:

1. **Dos credenciales a la vez**: el token de sesion tiene precedencia y la clave de API se
   ignora. Cambian el caso borde y el supuesto; se agregan FR-019a y los escenarios 7 y 8 de
   la User Story 3. Lo decidio el equipo el 2026-09-18 y reemplaza la regla por defecto de la
   iteracion 1, "alcanza con que una sea valida".
2. **Identificador en el perfil y en el registro**: FR-024, la User Story 4, el escenario 1 de
   la User Story 1 y la entidad Usuario pasan a incluir el identificador.
3. **Largo maximo de la contrasena**: FR-004 agrega un tope de 72 bytes, que es el limite del
   mecanismo de resguardo no reversible. Se suman un supuesto y dos casos borde.
4. **Largo maximo del correo**: FR-003 agrega un tope de 254 caracteres y un supuesto.
5. **Unicidad ante registros simultaneos**: el caso borde se acota a valores identicos, y un
   supuesto documenta el riesgo aceptado cuando solo difieren en mayusculas.

Tambien sigue vigente la User Story 7 (alta del administrador), confirmada por el equipo el
2026-09-18.

### Resultado

16 de 16 items en verde. Estado final, despues de la iteracion 3: 7 historias de usuario, 43
requisitos funcionales (42 mas FR-019a), 13 criterios de exito y 0 marcadores
[NEEDS CLARIFICATION]. El spec y el plan estan alineados y listos para `/speckit-tasks`.
