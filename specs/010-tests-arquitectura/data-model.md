# Modelo de datos: Tests de arquitectura del backend y del frontend

**Fecha**: 2026-11-12 | **Plan**: [plan.md](./plan.md) | **Spec**: [spec.md](./spec.md)

La feature no tiene entidades persistentes. Este documento fija las piezas que el código de test comparte: la regla,
la violación y el ejemplo de violación (fixture en el backend, fuente en memoria en el frontend), y el mapa de qué
caso borde prueba cada regla. El catálogo de las 33 reglas con sus textos está en
[contracts/architecture-rules.md](./contracts/architecture-rules.md).

## 1. Vista general

| Entidad de la spec | Backend | Frontend |
|---|---|---|
| Regla de arquitectura | Un `ArchRule` que devuelve un método de `ArchitectureRules`, con descripción `[id] nombre, porque principio` | Una fila de la tabla de reglas de dependencias (F1 a F6.2) o una función de chequeo (F7 a F10.2) dentro de `tests/architecture.test.ts` |
| Violación | Una línea de `EvaluationResult.getFailureReport().getDetails()` | Un `Violation { ruleId, file, message }` |
| Ejemplo de violación | Una clase de `ar.edu.unq.desapp.archfixture` | Una entrada de un `Record<string, string>` armado dentro del `it` |

Reglas de las tres piezas:

- Una regla tiene un identificador único (B1 a B7.5, F1 a F10.2), un nombre y un porqué que cita la constitución.
- Una regla se evalúa sobre un conjunto de código y devuelve **todas** sus violaciones (FR-005).
- La misma regla, sin cambios, se evalúa contra el código real (caso feliz: cero violaciones) y contra los ejemplos
  (caso borde: al menos una violación que nombra al infractor).
- Los ejemplos de violación nunca entran al análisis del código real (FR-007): en el backend, porque viven fuera de
  `ar.edu.unq.desapp.futbolmarket` y en `build/classes/java/test`; en el frontend, porque están en memoria.

## 2. Backend

### 2.1 `ArchitectureRules`

Clase final con constructor privado y un método estático por regla. Los que llevan `base` arman todos sus paquetes
como `base + ".<capa>..."`.

| Id | Método | Test feliz (clase) |
|---|---|---|
| B1 | `layers(String base)` | `LayerRulesTest` |
| B2 | `pureModel(String base)` | `ModelRulesTest` |
| B3.1 | `jpaOnlyInPersistenceSql(String base)` | `PersistenceRulesTest` |
| B3.2 | `entities(String base)` | `PersistenceRulesTest` |
| B3.3 | `sqlDaos(String base)` | `PersistenceRulesTest` |
| B3.4 | `sqlDaoAndMapperAccess(String base)` | `PersistenceRulesTest` |
| B3.5 | `noNativeQueries(String base)` | `PersistenceRulesTest` |
| B3.6 | `serviceWithoutEntities(String base)` | `PersistenceRulesTest` |
| B4.1 | `restControllers(String base)` | `ControllerRulesTest` |
| B4.2 | `controllerDtos(String base)` | `ControllerRulesTest` |
| B4.3 | `endpointsReturnNoModel(String base)` | `ControllerRulesTest` |
| B5.1 | `providerDtos(String base)` | `AdapterRulesTest` |
| B5.2 | `independentAdapter(String base)` | `AdapterRulesTest` |
| B5.3 | `httpClientsOnlyInAdapterAndConfig(String base)` | `AdapterRulesTest` |
| B6 | `serviceContextsWithoutCycles(String base)` | `LayerRulesTest` |
| B7.1 | `noStandardStreams()` | `CodingRulesTest` |
| B7.2 | `noGenericExceptions()` | `CodingRulesTest` |
| B7.3 | `noJavaUtilLogging()` | `CodingRulesTest` |
| B7.4 | `noFieldInjection()` | `CodingRulesTest` |
| B7.5 | `singleExceptionHandler(String base)` | `CodingRulesTest` |

Constantes públicas: `PRODUCTION_BASE = "ar.edu.unq.desapp.futbolmarket"` y
`FIXTURE_BASE = "ar.edu.unq.desapp.archfixture"`.

Piezas privadas:

- `named(ArchRule rule, String id, String name, String reason)`: devuelve
  `rule.as("[" + id + "] " + name + ", porque " + reason)` (D8).
- Condición de B4.3: para cada método, recorre `getReturnType().getAllInvolvedRawTypes()` y, por cada tipo que viva en
  `base.modelo..` o `base.persistence..`, agrega un evento en español: `El endpoint <método completo> devuelve <tipo>
  (dentro de <retorno completo>)`.
- Condición de B7.5: en `init` cuenta las clases seleccionadas; en `finish`, si no es exactamente una, agrega un evento:
  `Hay <n> manejadores globales de excepciones (<nombres>): tiene que haber exactamente uno, en shared`.
- Condición "crear un `Error`" de B7.2: constructor declarado en `Error` llamado desde una clase que no es subclase de
  `Error` (mismo patrón que `GeneralCodingRules`).

### 2.2 `ProductionClasses`

- `static JavaClasses get()`: devuelve las clases de `PRODUCTION_BASE` importadas con `DO_NOT_INCLUDE_TESTS`, una sola
  vez por corrida (holder perezoso). `JavaClasses` es inmutable.

### 2.3 `ViolationReport`

- `static String of(ArchRule rule, EvaluationResult result)`: devuelve

  ```text
  <rule.getDescription()>
  Infractores (<cantidad>):
  - <detalle 1>
  - <detalle 2>
  ```

  El detalle es la línea de ArchUnit (en inglés, permitido por FR-004) o el evento en español de las condiciones
  propias.

### 2.4 Forma de los tests

Caso feliz (uno por regla, en el `*RulesTest` de su grupo):

```java
@Test
void lasCapasRespetanLaDireccionControllerServicioPersistenciaYAdapter() {
    JavaClasses classes = ProductionClasses.get();
    ArchRule rule = ArchitectureRules.layers(ArchitectureRules.PRODUCTION_BASE);

    EvaluationResult result = rule.evaluate(classes);

    assertThat(result.getFailureReport().getDetails())
            .withFailMessage(() -> ViolationReport.of(rule, result))
            .isEmpty();
}
```

Caso borde (en `ArchitectureRulesDetectViolationsTest`, que importa las fixtures una vez en una constante con
`new ClassFileImporter().importPackages(FIXTURE_BASE)`):

```java
@Test
void laReglaDeCapasDetectaUnControllerQueUsaUnRepository() {
    ArchRule rule = ArchitectureRules.layers(ArchitectureRules.FIXTURE_BASE);

    EvaluationResult result = rule.evaluate(FIXTURES);

    assertThat(result.hasViolation()).isTrue();
    assertThat(result.getFailureReport().getDetails()).anyMatch(detail -> detail.contains("RepositoryAwareController"));
}
```

Guarda del import (en `LayerRulesTest`): `elImportDeProduccionNoIncluyeTestsNiFixtures()` verifica que
`ProductionClasses.get()` contiene `FutbolMarketApplication` y no contiene `LayerRulesTest` ni ninguna clase de
`archfixture`.

## 3. Fixtures del backend

Paquete raíz: `ar.edu.unq.desapp.archfixture` (`backend/src/test/java/ar/edu/unq/desapp/archfixture/`). Todas las
clases son mínimas y se escriben sin issues de Sonar (D16): cada campo se usa, ningún método ni clase queda vacío,
ningún parámetro sobra. Llevan un comentario de una línea que dice qué regla rompen a propósito.

### 3.1 De apoyo (no rompen nada por sí solas)

Mantienen las capas no vacías (D12) y dan un ejemplo correcto contra el cual comparar.

| Clase (paquete relativo) | Qué es |
|---|---|
| `controller.sample.SampleController` | `@RestController` que usa `SampleService` y devuelve un `SampleResponse` |
| `controller.sample.dto.SampleResponse` | `record` con un `record Line` anidado (frontera de FR-022) |
| `service.sample.SampleService` | Usa `SampleRepository` y devuelve `SampleItem` (frontera de B3.6) |
| `modelo.sample.SampleItem` | Modelo puro |
| `persistence.repository.sample.SampleRepository` | Usa `SampleSQLDAO` y `SampleMapper` |
| `persistence.mapper.sample.SampleMapper` | `SampleSQL` -> `SampleItem` |
| `persistence.sql.entity.sample.SampleSQL` | `@Entity` con `@Id` |
| `persistence.sql.interfaces.sample.SampleSQLDAO` | `interface ... extends JpaRepository<SampleSQL, Long>` |
| `adapter.sample.SampleAdapter` | Traduce `SampleProviderDto` a `SampleItem` |
| `adapter.sample.dto.SampleProviderDto` | `record` del proveedor |
| `shared.SampleExceptionHandler` | `@RestControllerAdvice` en su lugar |

### 3.2 Las que rompen una regla

| Regla | Clase (paquete relativo) | Qué hace mal | El caso borde verifica que el detalle nombre |
|---|---|---|---|
| B1 | `controller.sample.RepositoryAwareController` | Su constructor recibe `SampleRepository` | `RepositoryAwareController` |
| B2, B3.1 | `modelo.sample.TableMappedItem` | Lleva `@jakarta.persistence.Table` | `TableMappedItem` (en las dos reglas) |
| B3.1 | `persistence.repository.sample.LockingRepository` | Usa `jakarta.persistence.LockModeType` fuera de `persistence.sql` | `LockingRepository` |
| B3.2 | `persistence.sql.entity.sample.SampleRecord` | `@Entity` sin sufijo `SQL` | `SampleRecord` |
| B3.3 | `persistence.sql.interfaces.sample.SampleDao` | Extiende `JpaRepository` sin sufijo `SQLDAO` | `SampleDao` |
| B3.4 | `service.sample.DirectDaoService` | Usa `SampleSQLDAO` | `DirectDaoService` |
| B3.4 | `service.sample.MapperAwareService` | Usa `SampleMapper` | `MapperAwareService` |
| B3.5 | `persistence.sql.interfaces.sample.NativeQuerySQLDAO` | Un método con `@Query(value = ..., nativeQuery = true)` y otro con `@NativeQuery` | `findAllNative` y `findAllWithNativeQuery` |
| B3.5 | `persistence.sql.NativeQueryRunner` | Llama a `EntityManager.createNativeQuery` | `NativeQueryRunner` |
| B3.6 | `service.sample.EntityAwareService` | Un método recibe `SampleSQL` | `EntityAwareService`, y ningún detalle nombra `SampleService` |
| B4.1 | `controller.sample.SampleEndpoint` | `@RestController` sin sufijo `Controller` | `SampleEndpoint` |
| B4.2 | `controller.sample.dto.SampleDto` | Clase de primer nivel sin `Request` ni `Response` | `SampleDto`, y ningún detalle nombra `SampleResponse$Line` |
| B4.2 | `service.sample.DtoAwareService` | Usa `SampleResponse` fuera de `controller` | `DtoAwareService` |
| B4.3 | `controller.sample.ModelLeakController` | Cuatro endpoints: `SampleItem item()`, `ResponseEntity<List<SampleItem>> items()`, `SampleItem[] itemArray()` y `Optional<SampleSQL> entity()` | Los cuatro métodos |
| B5.1 | `service.sample.ProviderDtoAwareService` | Usa `SampleProviderDto` fuera de `adapter` | `ProviderDtoAwareService` |
| B5.2 | `adapter.sample.ServiceAwareAdapter` | Usa `SampleService` | `ServiceAwareAdapter` |
| B5.3 | `service.sample.HttpCallingService` | Usa `java.net.http.HttpClient` | `HttpCallingService` |
| B6 | `service.alpha.AlphaService` y `service.beta.BetaService` | Se usan mutuamente | El ciclo entre las slices `alpha` y `beta` |
| B7.1 | `service.sample.StandardStreamService` | Escribe en `System.out` | `StandardStreamService` |
| B7.2 | `service.sample.GenericExceptionService` | Crea un `RuntimeException` y un `Error` | `GenericExceptionService`, una vez por cada tipo |
| B7.3 | `service.sample.JavaUtilLoggingService` | Usa `java.util.logging.Logger` | `JavaUtilLoggingService` |
| B7.4 | `service.sample.FieldInjectedService` | Campo con `@Autowired` (que usa en un método) | `FieldInjectedService` |
| B7.5 | `controller.sample.StrayExceptionHandler` | Segundo `@RestControllerAdvice`, fuera de `shared` | `StrayExceptionHandler` y el conteo (2) |

Total: 35 clases (11 de apoyo y 24 que rompen reglas). Algunas rompen además otra regla de rebote (por ejemplo,
`ServiceAwareAdapter` también rompe B1): los casos borde solo verifican la regla que les toca, así que esas
violaciones extra no molestan y muestran que el reporte junta todas (escenario 9 de la US1).

### 3.3 Casos borde del backend

`ArchitectureRulesDetectViolationsTest` tiene al menos un test por regla, 25 en total: uno por regla y uno más en
B3.4 (SQLDAO y mapper), B4.2 (nombres y uso), B7.2 (`RuntimeException` y `Error`) y dos más en B3.5 (las dos
anotaciones y la llamada en tiempo de ejecución). Los nombres, en español, dicen la regla y la violación:
`laReglaDeCapasDetectaUnControllerQueUsaUnRepository`, `laReglaDeEndpointsDetectaModeloDirectoEnUnaListaYEnUnArreglo`,
`laReglaDeExcepcionesGenericasDetectaUnError`, etc.

## 4. Casos borde del frontend

Cada caso arma un `Record<string, string>` en memoria (rutas relativas a `frontend/`), llama a `analyze` y compara los
mensajes (o los identificadores) de la regla que prueba. Un caso marcado "frontera" verifica que **no** se informe
nada.

| # | Regla | Fuentes en memoria | Resultado esperado |
|---|---|---|---|
| 1 | F1 | `src/components/Card.tsx` importa `../services/httpClient` | `[F1] src/components/Card.tsx usa ../services/httpClient: ...` |
| 2 | F1 | `src/components/Card.tsx` hace `import type { X } from '../hooks/useThing'` | `[F1]` con el import de hooks |
| 3 | F1 | `src/components/Logo.tsx` importa `Link` de `react-router` (la situación de TopBar antes de corregir) | `[F1] src/components/Logo.tsx usa react-router: ...` |
| 4 | F1 | `src/components/Card.tsx` hace `export { useThing } from '../hooks/useThing'` | `[F1]` con el re-export |
| 5 | F1 frontera | `src/components/Card.tsx` importa `./Card.module.css`, `../types/player`, `../utils/format` y `react` | Nada |
| 6 | F2 | `src/utils/format.ts` importa `react` | `[F2]` |
| 7 | F3 | `src/types/player.ts` importa `../services/api` | `[F3]` |
| 8 | F4 | `src/services/api.ts` importa `../hooks/useSession` | `[F4]` |
| 9 | F5 | `src/hooks/useThing.ts` importa `../components/Card` | `[F5]` |
| 10 | F6.1 | `src/main.tsx` importa `./pages/BoardPage` | `[F6.1]` |
| 11 | F6.2 | `src/pages/BoardPage.tsx` hace `import('../router/AppRouter')` (import dinámico) | `[F6.2]` |
| 12 | F7 | `src/hooks/useThing.ts` llama a `fetch('/api/players')` | `[F7] src/hooks/useThing.ts usa fetch: ...` |
| 13 | F7 | `src/pages/BoardPage.tsx` usa `window.sessionStorage.getItem(...)` y `globalThis.localStorage` | Dos `[F7]` |
| 14 | F7 frontera | `src/context/SessionContext.tsx` tiene un comentario que nombra `sessionStorage`, importa `../services/sessionStorage` y lee `config.fetch` de un objeto propio | Nada |
| 15 | F8.1 | `src/pages/BoardPage.tsx` importa `@mui/material` | `[F8.1] ... usa @mui/material: ...` |
| 16 | F8.1 | `src/pages/BoardPage.tsx` importa `react-router-dom`; `src/main.tsx` importa `react-dom/client` | Solo `[F8.1]` por `react-router-dom` |
| 17 | F8.2 | `src/pages/BoardPage.tsx` importa `../../mockups/board` | `[F8.2]` |
| 18 | F9 | `src/utils/a.ts` importa `./b` y `src/utils/b.ts` importa `./a` | Un `[F9]` con `src/utils/a.ts -> src/utils/b.ts -> src/utils/a.ts` |
| 19 | F9 | Ciclo indirecto `a -> b -> c -> a` en `src/utils/` | Un `[F9]` con los tres archivos |
| 20 | F10.1 | `src/components/Card.test.tsx` | `[F10.1]` |
| 21 | F10.1 frontera de FR-006 | `src/components/Card.test.tsx` importa `../services/api` | Solo `[F10.1]`, ningún `[F1]` |
| 22 | F10.2 | `tests/utils/foo.test.ts` sin `src/utils/foo.ts` | `[F10.2]` |
| 23 | F10.2 frontera | `tests/hooks/useCatalog.test.tsx` con `src/hooks/useCatalog.ts`, y `tests/pages/Flow.test.tsx` sin página | Nada |

## 5. Frontend: tipos y algoritmo de `tests/architecture.test.ts`

### 5.1 Tipos

```ts
// Ruta relativa a frontend/ -> código fuente ('' para los archivos de tests/ y los tests de src/ sin ?raw)
type Sources = Record<string, string>;

interface DependencyRule {
  id: string;                                   // 'F1' ... 'F6.2'
  appliesTo: string[] | { except: string[] };   // grupos
  mayUse?: string[];                            // lista blanca: grupos y 'package:<nombre>'
  mayNotUse?: string[];                         // lista negra: grupos
  because: string;                              // porqué en español
}

interface Violation {
  ruleId: string;
  file: string;
  message: string;  // `[${ruleId}] ${file} ...: ${because} (Constitución, sección Frontend)`
}
```

### 5.2 Grupos y destinos de un import

- **Grupo** de un archivo de `src/`: su carpeta de primer nivel (`components`, `context`, `hooks`, `pages`, `router`,
  `services`, `types`, `utils`) o su nombre si está en la raíz (`main.tsx`).
- **Archivo de producción**: clave que empieza con `src/` y no matchea `.test.` ni `.spec.` (FR-006). Solo esos entran
  en F1 a F9.
- **Destino de un import** (de `ImportDeclaration`, `ExportDeclaration` con módulo o `import('...')`):
  - termina en `.css`: se ignora;
  - empieza con `.`: se normaliza contra la carpeta del archivo. Si queda fuera de `src/`, es F8.2. Si queda adentro,
    su grupo es el de la ruta normalizada (no hace falta que el archivo exista: los casos en memoria pueden omitirlo);
  - empieza con `/`: fuera de `src/` (F8.2);
  - cualquier otro: paquete. Nombre = `@scope/nombre` o el primer segmento; se compara como `package:<nombre>`.
- **Grafo de F9**: arista de archivo a archivo cuando el import relativo resuelve a una clave existente probando la
  ruta, `.ts`, `.tsx`, `/index.ts` y `/index.tsx`.

### 5.3 Reglas

| Id | Aplica a | Regla |
|---|---|---|
| F1 | `components` | `mayUse`: `components`, `types`, `utils`, `package:react` |
| F2 | `utils` | `mayUse`: `utils`, `types` |
| F3 | `types` | `mayUse`: `types` |
| F4 | `services` | `mayUse`: `services`, `types` |
| F5 | `hooks`, `context` | `mayNotUse`: `components`, `pages`, `router` |
| F6.1 | todos menos `router` y `pages` | `mayNotUse`: `pages` |
| F6.2 | todos menos `main.tsx` y `router` | `mayNotUse`: `router` |
| F7 | todos menos `services` | Ningún identificador `fetch`, `sessionStorage` ni `localStorage` usado como global ni como propiedad de `window`, `globalThis` o `self` (`.x` o `['x']`). No cuentan: nombres de propiedad de otros objetos, nombres en declaraciones de tipos o de propiedades, literales de imports, comentarios |
| F8.1 | todos | Paquetes permitidos: `react`, `react-dom`, `react-router` (comparando el nombre del paquete, no el prefijo: `react-router-dom` no pasa) |
| F8.2 | todos | Ningún import fuera de `src/` |
| F9 | todos | Sin ciclos en el grafo |
| F10.1 | claves de `src/` | Ninguna matchea `.test.` ni `.spec.` |
| F10.2 | claves `tests/<carpeta>/**/X.test.ts` o `.test.tsx`, con `<carpeta>` = `components`, `hooks`, `services` o `utils` | Existe `src/<misma carpeta>/<misma subruta>/X.ts` o `X.tsx` |

En F1 a F4, el propio grupo siempre está en `mayUse`; los imports a archivos de la misma carpeta nunca son violación.
