# Contrato: catálogo de reglas de arquitectura y formato del mensaje

**Versión**: 1.0.0 | **Fecha**: 2026-11-12 | **Plan**: [../plan.md](../plan.md)

Este es el contrato de la feature con quien lee un fallo y con el README: qué reglas hay, qué texto muestra cada una
y cómo se corren. El código de test (`ArchitectureRules` y la tabla de `tests/architecture.test.ts`) usa estos
identificadores, nombres y porqués **tal cual**. La sección "Tests de arquitectura" del README se arma desde este
archivo (FR-083) y se copia a las Release Notes de la entrega 3.

## 1. Formato del mensaje

### Backend

```text
[<id>] <nombre>, porque <porqué>
Infractores (<n>):
- <línea de detalle de ArchUnit, o el evento en español de una condición propia>
```

Ejemplo (B1, con la fixture del caso borde):

```text
[B1] Dirección de capas, porque Principio I: el controller solo habla con el servicio, el servicio con la persistencia y el adapter, y ninguna capa se saltea
Infractores (1):
- Constructor <ar.edu.unq.desapp.archfixture.controller.sample.RepositoryAwareController.<init>(ar.edu.unq.desapp.archfixture.persistence.repository.sample.SampleRepository)> has parameter of type <ar.edu.unq.desapp.archfixture.persistence.repository.sample.SampleRepository> in (RepositoryAwareController.java:0)
```

La primera línea y el encabezado "Infractores" los escribe el equipo, en español. La línea de detalle de las reglas de
ArchUnit queda con su redacción (FR-004). Las condiciones propias (B4.3 y B7.5) escriben su detalle en español.

### Frontend

Una línea por violación, todas juntas en la salida de Vitest:

```text
[<id>] <archivo> usa <import, paquete o identificador>: <porqué> (Constitución, sección Frontend)
[F9] ciclo <archivo> -> <archivo> -> ... -> <archivo>: <porqué> (Constitución, sección Frontend)
[F10.1] <archivo> es un test dentro de src/: <porqué> (Constitución, sección Frontend)
[F10.2] <test> no tiene src/<carpeta>/<nombre>.ts ni .tsx: <porqué> (Constitución, sección Frontend)
```

Ejemplo (F1, antes de corregir TopBar):

```text
[F1] src/components/TopBar.tsx usa react-router: los componentes reciben datos y callbacks por props; no conocen la API, el router, los hooks ni los contextos (Constitución, sección Frontend)
```

## 2. Reglas del backend (20)

Paquetes relativos a `ar.edu.unq.desapp.futbolmarket`. "Depender" incluye campo, parámetro, retorno, argumento
genérico, anotación, herencia, llamada y acceso.

| Id | Nombre | Qué verifica | Porqué (texto del mensaje) | FR |
|---|---|---|---|---|
| B1 | Dirección de capas | Nadie fuera de `controller` depende de `controller`, ni nadie fuera de `security` de `security`. De `service` solo dependen `controller`, `security` y el paquete raíz. De `persistence` y de `adapter`, solo `service`. Vale también para `modelo`, `config` y `shared` | Principio I: el controller solo habla con el servicio, el servicio con la persistencia y el adapter, y ninguna capa se saltea | FR-010 |
| B2 | Modelo puro | `modelo` depende solo de la JDK, de `modelo`, de Lombok y de las excepciones base de `shared` | Principio I: el modelo no lleva anotaciones de persistencia ni conoce Spring ni otras capas | FR-011 |
| B3.1 | JPA solo en persistence/sql | Fuera de `persistence.sql` nadie depende de `jakarta.persistence`, Hibernate ni Spring Data JPA (la paginación de Spring Data no cuenta) | Principio I: las anotaciones y la API de JPA viven únicamente en las clases *SQL y en sus SQLDAO | FR-012 |
| B3.2 | Entidades *SQL | Toda `@Entity` o `@Embeddable` vive en `persistence.sql.entity` y termina en `SQL` | Principio I: cada clase de modelo tiene su contraparte *SQL en persistence/sql/entity | FR-013 |
| B3.3 | SQLDAO | Todo lo que extiende `JpaRepository`, vive en `persistence.sql.interfaces` o termina en `SQLDAO` es una interfaz de `persistence.sql.interfaces`, termina en `SQLDAO` y extiende `JpaRepository` | Principio I: los *SQLDAO son interfaces de persistence/sql/interfaces que extienden JpaRepository | FR-014 |
| B3.4 | SQLDAO y mappers solo desde repository | Los SQLDAO se usan solo desde `persistence.repository`; los mappers, desde `persistence.repository` y otros mappers | Principio I: repository es el único punto donde conviven modelo y persistencia, y es quien invoca al SQLDAO y al mapper | FR-015 |
| B3.5 | Sin queries nativas | Ningún `@Query(nativeQuery = true)`, ningún `@NativeQuery`, ninguna llamada a `createNativeQuery` | Principio VI: solo derived queries y JPQL, nunca SQL nativo | FR-016 |
| B3.6 | El servicio no conoce las entidades | `service` no depende de `persistence.sql.entity` | Principio I: el servicio recibe y devuelve modelo y no conoce JPA ni las clases *SQL | FR-017 |
| B4.1 | Controllers REST | Todo `@RestController` vive en `controller.<contexto>` y termina en `Controller` | Principio I: los controllers viven en controller/<contexto>/ y se llaman *Controller | FR-020 |
| B4.2 | DTO del controller | Lo de `controller.<contexto>.dto` se usa solo desde `controller`; sus clases de primer nivel terminan en `Request` o `Response` (los tipos anidados no necesitan sufijo) | Principio I: los Request y Response viven en controller/<contexto>/dto/ y nunca cruzan hacia el servicio | FR-021, FR-022 |
| B4.3 | Qué devuelve un endpoint | Ningún método público de un `@RestController` devuelve una clase de `modelo` ni de `persistence`, ni directa ni dentro de un genérico (en cualquier nivel) ni en un arreglo | Principio I: ninguna clase de modelo ni de persistencia sale en una respuesta HTTP; sale siempre un Response | FR-023 |
| B5.1 | DTO del proveedor | Lo de `adapter.<proveedor>.dto` se usa solo desde `adapter` | Principio I: los DTO del proveedor viven en adapter/<proveedor>/dto/ y nunca salen del adapter | FR-030 |
| B5.2 | Adapter independiente | `adapter` no depende de `service`, `controller` ni `persistence` | Principio I: el adapter recibe y devuelve modelo; no conoce el servicio, el controller ni la persistencia | FR-031 |
| B5.3 | Clientes HTTP | Solo `adapter` y `config` usan clientes HTTP: `RestClient` y compañía (`org.springframework.web.client`), `WebClient`, `org.springframework.http.client` y `java.net.http` | Principio I: el servicio habla con una API externa solo a través de su adapter y nunca usa un cliente HTTP | FR-032 |
| B6 | Servicios sin ciclos | Los contextos de `service` no forman ciclos | Principio I (reglas de monolito): un servicio puede depender de otro solo si no se forma un ciclo | FR-040 |
| B7.1 | Sin salida estándar | Nadie usa `System.out`, `System.err` ni `printStackTrace()` | Principio V: nada de System.out; el logging va con SLF4J | FR-050 |
| B7.2 | Sin excepciones genéricas | Nadie crea `Exception`, `RuntimeException`, `Throwable` ni `Error` (las subclases propias sí) | Principio III: las excepciones son propias y con nombre; prohibido throw new RuntimeException(...) | FR-051 |
| B7.3 | Sin java.util.logging | Nadie usa `java.util.logging` | Principio V: el logging va con SLF4J | FR-052 |
| B7.4 | Sin inyección por campo | Ningún campo con `@Autowired`, `@Value`, `@Inject` ni `@Resource` | Principio V: inyección por constructor, nunca @Autowired sobre el campo | FR-053 |
| B7.5 | Un solo manejador de excepciones | Hay exactamente un `@RestControllerAdvice` (o `@ControllerAdvice`) y vive en `shared` | Principio III: un único @RestControllerAdvice en shared/ centraliza el manejo de errores | FR-054 |

## 3. Reglas del frontend (13)

Rutas relativas a `frontend/`. "Usar" una carpeta es importar cualquier archivo de ella, aunque traiga solo tipos.

| Id | Nombre | Qué verifica | Porqué (texto del mensaje) | FR |
|---|---|---|---|---|
| F1 | Componentes sin API ni router | `src/components/` usa solo `components/`, `types/`, `utils/` y el paquete `react` | los componentes reciben datos y callbacks por props; no conocen la API, el router, los hooks ni los contextos | FR-060 |
| F2 | utils puro | `src/utils/` usa solo `utils/` y `types/`, sin paquetes | utils son funciones puras: no usan React, otros paquetes ni services | FR-061 |
| F3 | types | `src/types/` usa solo `types/` | types tiene solo los tipos de la API y del dominio del front | FR-062 |
| F4 | services | `src/services/` usa solo `services/` y `types/`, sin paquetes | services es el cliente HTTP y una función por endpoint; no conoce React ni la interfaz | FR-063 |
| F5 | hooks y context | `src/hooks/` y `src/context/` no usan `components/`, `pages/` ni `router/` | la lógica con estado vive en hooks y context, que no conocen los componentes, las pantallas ni las rutas | FR-064 |
| F6.1 | Quién usa pages | Fuera de `router/` y `pages/` nadie usa `pages/` | hay una pantalla por ruta: las pantallas las usa el router | FR-065 |
| F6.2 | Quién usa router | Fuera de `main.tsx` y `router/` nadie usa `router/` | la definición de rutas la usa solo main.tsx | FR-066 |
| F7 | Red y almacenamiento | Fuera de `services/` nadie usa `fetch`, `sessionStorage` ni `localStorage`, tampoco por `window`, `globalThis` o `self` | los componentes y las páginas nunca llaman a fetch directo, y la sesión se guarda solo desde services | FR-067 |
| F8.1 | Paquetes permitidos | `src/` importa solo `react`, `react-dom` y `react-router`, y sus subrutas | sin frameworks de UI ni paquetes fuera del stack | FR-068 |
| F8.2 | Nada de afuera | `src/` no importa nada fuera de `src/` (ni de `mockups/`) | mockups son diseños de referencia y no se importan desde src | FR-069 |
| F9 | Sin ciclos | Los imports entre archivos de `src/` no forman ciclos | un ciclo ata dos archivos que tienen que poder entenderse y probarse por separado | FR-070 |
| F10.1 | Tests fuera de src | `src/` no tiene archivos `.test.*` ni `.spec.*` | los tests viven solo en frontend/tests/, nunca junto al código | FR-071 |
| F10.2 | Correspondencia de tests | Cada test de `tests/components/`, `tests/hooks/`, `tests/services/` y `tests/utils/` tiene un archivo de `src/` con el mismo nombre base en la carpeta del mismo nombre | tests/ replica la estructura de src/: el test de src/<carpeta>/X.tsx está en tests/<carpeta>/X.test.tsx | FR-072 |

## 4. Cómo se corren

| Qué | Dónde | Comando |
|---|---|---|
| Solo las reglas del backend | `backend/` | `./gradlew test --tests "ar.edu.unq.desapp.futbolmarket.architecture.*"` |
| Solo las reglas del frontend | `frontend/` | `npx vitest run tests/architecture.test.ts` |
| Todo, como en el CI | `backend/` y `frontend/` | `./gradlew build` y `npm test` |

Los nombres de los tests (en español) dicen qué regla verifican. En el backend, cada regla es un test de un
`*RulesTest` (caso feliz) y uno o más de `ArchitectureRulesDetectViolationsTest` (caso borde). En el frontend, cada
regla tiene un `it` en "código real" y al menos uno en "casos borde".

## 5. Cambios a este contrato

Sumar, quitar o cambiar una regla cambia este archivo, el README y los tests en el mismo PR. Si el cambio afloja una
regla para que pase un código que no la cumple, no es un cambio de contrato: es una decisión del equipo (FR-008).
