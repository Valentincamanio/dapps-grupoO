# Plan de implementación: Auditoría de web services

**Rama**: `011-auditoria-web-services` | **Fecha**: 2026-11-12 | **Spec**: [spec.md](./spec.md)

**Entrada**: especificación de la feature en `specs/011-auditoria-web-services/spec.md` (revisada
durante este plan, ver [Decisiones de planificación](#decisiones-de-planificación-2026-11-12)).

## Resumen

Cada invocación a un endpoint REST propio del backend deja exactamente una línea técnica,
`timestamp | nivel | user=… | op=… | params=… | time=…ms | result=…`, en un archivo propio
(`backend/logs/web-service-audit.log`, con rotación) y en la consola. Los secretos van enmascarados
y la API responde exactamente igual que hoy.

Enfoque técnico: un aspecto de Spring AOP (`@Around`) sobre los métodos públicos de los
`@RestController` de `controller/`. Mide con `System.nanoTime`, relanza la excepción intacta y
escribe la línea en un `finally` con un logger SLF4J propio (`WEB_SERVICE_AUDIT`) que Logback
manda a su propio appender. Una clase aparte, testeable sin Spring, arma la línea. Esa clase
serializa los argumentos con el `JsonMapper` de la app (Jackson 3) y enmascara y trunca sobre el
árbol JSON. Todo vive en `shared/`. La única dependencia nueva es `spring-boot-starter-aspectj`.
El cambio fuera de `shared/` es mínimo: el filtro de seguridad deja el tipo de credencial en los
`details` de la autenticación.

No hay entidades persistentes, ni endpoints nuevos, ni cambios de Swagger ni de la auditoría del
mercado.

## Decisiones de planificación (2026-11-12)

Las nueve decisiones del pedido se adoptan tal cual; el detalle de cada una está en
[research.md](./research.md). El plan suma o ajusta lo siguiente:

### Lo que se resolvió con el equipo durante el plan (spec enmendada)

1. **Los rechazos previos a la invocación no dejan línea.** La spec clarificada pedía auditar los
   400 de un cuerpo ilegible o que no pasa `@Valid` (FR-001, FR-001a). Spring MVC los lanza al
   resolver los argumentos, antes de llamar al proxy, y el aspecto no los ve. El equipo eligió
   enmendar la spec: se eliminó FR-001a y se ajustaron FR-001, FR-003, US3 (escenarios 4 y 5) y el
   edge case. Las restricciones sobre parámetros de los controllers `@Validated` (`@Min(1)` en un
   id) sí pasan por el aspecto y dejan `ERROR(ConstraintViolationException)` (research D2, D12).
2. **Rutas públicas → `anonymous`.** `GET /players/**`, `GET /orders`, `POST /auth/login` y
   `POST /auth/register` no evalúan credenciales (`shouldNotFilter`). La feature no cambia la
   autenticación: el escenario 1 de la US1 pasa a `GET /users/{id}/portfolio` y el edge case de la
   credencial en un endpoint público dice `anonymous`.
3. **El timestamp es el del fin de la invocación** (lo pone el `%d` de Logback al escribir). El
   inicio es timestamp − time.

### Lo que el plan agrega al pedido, tomado de la spec clarificada

- **Tipo de credencial** en `user` (`user=42(USER,JWT)`, FR-006): hoy el filtro arma la misma
  autenticación para JWT y para clave de API. `CredentialAuthenticationFilter` pasa a guardar
  `CredentialType.JWT` o `CredentialType.API_KEY` como `details` (research D6). Es un cambio en
  `security/`, sin cambio de comportamiento.
- **Doble límite de largo** (FR-017): 500 caracteres por valor además del total de 2000 (research
  D10).
- **`CredentialType` en `shared/`**: `shared/` no puede importar `security/` ni `modelo/`, porque
  los dos ya dependen de `shared/` y se formaría un ciclo, que el `LayerRulesTest` de la 010
  controla (research D4).
- **`catch (RuntimeException)` relanzada** para conocer el tipo de error, en lugar de
  `catch (Throwable)` (research D11).

### Contradicciones con la constitución

Ninguna. El único punto a declarar es la dependencia nueva, que la constitución permite con aviso y
justificación (research D3).

## Contexto técnico

**Lenguaje/Versión**: Java 21 (Temurin).

**Dependencias principales**: Spring Boot 4.1.1 (`spring-boot-starter-webmvc`, `-security`,
`-validation`, `-data-jpa`, `-actuator`), Logback 1.5.38 + SLF4J 2.0.18 (vía
`spring-boot-starter-logging`), Jackson 3.1.5 (`tools.jackson`). **Nueva**:
`org.springframework.boot:spring-boot-starter-aspectj` (versión 4.1.1 del BOM, AspectJ 1.9.25.1).

**Almacenamiento**: ninguno nuevo. Archivo de log rotado en `backend/logs/`, fuera de la base y del
control de versiones.

**Testing**: JUnit 5 + AssertJ; `OutputCaptureExtension` de Spring Boot para leer la consola;
`MockMvcTester` en `e2e/`; H2 en memoria con `@ActiveProfiles("test")`.

**Plataforma**: backend Spring Boot levantado con `./gradlew bootRun` (perfil `local` por
defecto).

**Tipo de proyecto**: web service (monorepo `backend/` + `frontend/`; esta feature no toca el
frontend).

**Objetivos de performance**: la auditoría no debe pesar en la respuesta. La serialización de
argumentos ya chicos (ids, filtros, un cuerpo) con el mapper de la app cuesta menos de un
milisegundo, y el tiempo informado se mide antes de serializar.

**Restricciones**: exactamente una línea por invocación; nunca un secreto en claro; no alterar
status, cuerpo ni error de ninguna respuesta; no modificar ningún test existente; menos de 10
issues de Sonar.

**Escala/Alcance**: 12 controllers y 33 endpoints propios; 4 clases nuevas en `shared/`, 1
archivo de configuración, 1 cambio puntual en `security/`.

Sin `NEEDS CLARIFICATION`: todo quedó resuelto en [research.md](./research.md).

## Verificación constitucional

*PUERTA: debe pasar antes de la investigación. Se vuelve a verificar después del diseño.*

### Antes de la investigación

| Principio | Verificación | Estado |
|-----------|--------------|--------|
| I. Capas | Todo en `shared/` (transversal), sin carpetas nuevas. Las clases nuevas no importan `controller/`, `service/`, `persistence/`, `adapter/`, `modelo/` ni `security/`; el pointcut es un `String`. `security/` importa `shared/CredentialType`, en la misma dirección que hoy (`ApiErrorResponseWriter` → `ApiError`). Ningún DTO cruza capas: el aspecto solo los serializa. | ✅ |
| II. Modelo rico | No hay lógica de dominio nueva. | ✅ N/A |
| III. Validaciones | No se crean excepciones, no se suman status codes y no se toca `GlobalExceptionHandler`. La excepción del endpoint se relanza intacta. | ✅ |
| IV. Tests | Ningún test existente se modifica. Tests nuevos: unitario en `test/.../shared/`, e2e en `test/.../e2e/`, AssertJ, nombres en español, setup - execute - verify, H2 en memoria. Sin TDD. | ✅ |
| V. Calidad | Sin `catch` genérico (`JacksonException` y `RuntimeException` relanzada); SLF4J con placeholders; constantes con nombre; `@RequiredArgsConstructor` + `private final`; métodos cortos. `throws Throwable` del `@Around` lo impone `proceed()` (exclusión `e4` solo si Sonar lo marca). | ✅ |
| VI. Persistencia | La auditoría no se persiste. | ✅ N/A |
| VII. Idioma | Identificadores en inglés; marcas de la línea (`...(truncado)`, `<no representable>`), comentarios y docs en español; claves de la línea (`user`, `op`, `params`, `time`, `result`) como las fija la spec. | ✅ |
| Stack | Dependencia nueva avisada y justificada (research D3). No se reemplaza `build.gradle` ni los `application*.yml`: se agrega una línea al primero y un `logback-spring.xml` nuevo. | ✅ |
| Definición de terminado | Tests unitarios y e2e; `./gradlew build`; `bootRun` con perfil local. Sin endpoints nuevos, así que Swagger no cambia (se verifica que siga igual). | ✅ |

### Después del diseño

Se revisó el diseño final ([data-model.md](./data-model.md),
[contracts/web-service-audit-line.md](./contracts/web-service-audit-line.md)) contra cada fila:

- **I**: las dependencias de las clases nuevas son solo Spring (`aop`, `web`, `security.core`,
  `core.annotation`), AspectJ, Jackson 3, SLF4J y `jakarta.servlet`. `WebServiceInvocation` lleva
  `Class<?>` y `Method` del controller por reflexión, sin importarlo. ✅
- **III / FR-020**: el `finally` no puede cambiar la respuesta. Escribir la línea no lanza
  excepciones (appenders de Logback, `JacksonException` capturada), y el `return`/`throw` del
  `try` es el de `proceed()`. ✅
- **IV**: el único cambio de producción fuera de `shared/` (los `details` en el filtro) no altera
  ninguna respuesta. `AccessControlIT`, `AuthControllerIT` y el resto siguen igual. ✅
- **V**: queda abierta solo la posible exclusión `e4` (S112), documentada en research D14. ✅

Resultado: **PASA**, sin violaciones que justificar.

## Estructura del proyecto

### Documentación de la feature

```text
specs/011-auditoria-web-services/
├── spec.md                          # enmendada en este plan (segunda sesión de Clarifications)
├── plan.md                          # este archivo
├── research.md                      # D1–D16
├── data-model.md                    # la línea, el parámetro registrado y las reglas
├── quickstart.md                    # cómo verlo andando con ./gradlew bootRun
├── contracts/
│   └── web-service-audit-line.md    # formato exacto de la línea
├── checklists/
│   └── requirements.md
└── tasks.md                         # lo genera /speckit-tasks
```

### Código fuente: backend

```text
backend/
├── build.gradle                                   # + spring-boot-starter-aspectj (una línea);
│                                                  #   e4 en sonar solo si S112 aparece
├── src/main/resources/
│   └── logback-spring.xml                         # NUEVO: log general como hoy + WEB_SERVICE_AUDIT
└── src/main/java/ar/edu/unq/desapp/futbolmarket/
    ├── shared/
    │   ├── WebServiceAuditAspect.java             # NUEVO: @Aspect @Component, @Around, finally
    │   ├── WebServiceAuditFormatter.java          # NUEVO: @Component, arma user/op/params/result
    │   ├── WebServiceInvocation.java              # NUEVO: record con los datos crudos
    │   └── CredentialType.java                    # NUEVO: enum JWT, API_KEY
    └── security/
        └── CredentialAuthenticationFilter.java    # CAMBIA: setDetails(CredentialType)
```

Sin cambios en `controller/`, `service/`, `modelo/`, `persistence/`, `adapter/`, `config/`, en
`GlobalExceptionHandler` ni en la auditoría del mercado (`modelo/market/Audit*`,
`AuditController`, `AuditQueryService`).

Fuera de `backend/`:

```text
.gitignore            # + backend/logs/
README.md             # + sección "Auditoría de web services"
```

### Tests: backend

```text
backend/src/test/java/ar/edu/unq/desapp/futbolmarket/
├── shared/
│   └── WebServiceAuditFormatterTest.java   # NUEVO: unitario, sin Spring
└── e2e/
    └── WebServiceAuditIT.java              # NUEVO: MockMvcTester + OutputCaptureExtension
```

Casos de cada uno en research D15. Ningún test existente cambia.

**Decisión de estructura**: monolito por capas existente; la feature es transversal y entra
completa en `shared/`, con el único toque en `security/` que exige el tipo de credencial.

## Orden de implementación sugerido

1. **Dependencia y configuración**: la línea de `spring-boot-starter-aspectj` en `build.gradle`,
   `logback-spring.xml` y `backend/logs/` en `.gitignore`. Verificación: `./gradlew test` sigue
   verde y la consola del log general se ve igual que antes.
2. **Tipo de credencial**: `shared/CredentialType` y los `details` en
   `CredentialAuthenticationFilter`. Verificación: suite completa verde.
3. **Formatter**: `WebServiceInvocation` + `WebServiceAuditFormatter` con su test unitario
   (enmascarado, truncado, escape, exclusiones, usuario, resultado).
4. **Aspecto**: `WebServiceAuditAspect` y `WebServiceAuditIT`.
5. **Sonar**: revisar el análisis; si aparece S112 en el aspecto, la exclusión `e4`.
6. **README** y la verificación manual del [quickstart.md](./quickstart.md).
7. **Integración con la 010**: después de mergear con la rama de ArchUnit, `./gradlew test`
   completo (los tests de arquitectura tienen que pasar con las clases nuevas).

Los pasos 2 y 3 son independientes entre sí; el 4 necesita los dos.

## Seguimiento de complejidad

Sin violaciones de la constitución que justificar.

| Punto | Por qué hace falta | Alternativa más simple descartada |
|-------|--------------------|-----------------------------------|
| Cambio en `security/CredentialAuthenticationFilter` | FR-006 pide el tipo de credencial y hoy no queda registrado en ningún lado. | Deducirlo de los headers en el aspecto: duplica la regla de precedencia del filtro. |
| `CredentialType` en `shared/` y no en `security/` | Evita un ciclo `shared` ↔ `security` que la 010 detectaría. | Ponerlo en `security/`: ciclo. |
| `catch (RuntimeException)` que relanza | Es la única forma de conocer el tipo de error dentro del advice. | `catch (Throwable)`: choca con S1181 y con "sin catch genérico". |

## Puntos a confirmar con el equipo

- Que el compañero de la 010 sepa que `shared/` suma cuatro clases y que `security/` importa
  `shared/CredentialType` (dirección `security` → `shared`, la misma de hoy).
- Retención por defecto: 30 días, 10 MB por archivo, 1 GB en total (propiedades del
  `logback-spring.xml`, fáciles de cambiar).
