# Guía de validación: Tests de arquitectura del backend y del frontend

**Fecha**: 2026-11-12 | **Plan**: [plan.md](./plan.md) | **Spec**: [spec.md](./spec.md)

Cómo correr las verificaciones de arquitectura, qué tiene que pasar y cómo leer un fallo. El catálogo de reglas con
sus mensajes está en [contracts/architecture-rules.md](./contracts/architecture-rules.md); las fixtures y los casos
borde, en [data-model.md](./data-model.md).

## 0. Puertas antes de empezar

- **G2** del plan: aval para `backend/src/test/java/ar/edu/unq/desapp/archfixture/`. **Cumplida el 2026-11-12.**

## 1. Preparación

Java 21 (Temurin) y Node 20 o superior. No hace falta token, red, base ni el backend corriendo, salvo para la revisión
manual del logo (escenario F).

```bash
cd frontend
npm ci
```

## 2. Escenario A: reglas del backend contra el código real (US1, SC-001, SC-003)

```bash
cd backend
./gradlew test --tests "ar.edu.unq.desapp.futbolmarket.architecture.*"
```

En PowerShell, el mismo comando funciona igual.

Esperado:

- Pasan los seis `*RulesTest` (los 20 casos felices, uno por regla, más la guarda del import) y
  `ArchitectureRulesDetectViolationsTest` (los casos borde).
- Ningún test arranca Spring ni H2: en la salida no aparece el banner de Spring Boot.
- La tarea `test` filtrada tarda menos de 30 s en una máquina de desarrollo (SC-005). El tiempo de cada clase está en
  `build/reports/tests/test/index.html`.

## 3. Escenario B: los casos borde del backend detectan violaciones (US1, SC-002, SC-004)

Lo cubre `ArchitectureRulesDetectViolationsTest`: cada regla se evalúa contra las fixtures de
`ar.edu.unq.desapp.archfixture` y el test exige que falle y nombre a la clase infractora.

Prueba manual opcional, para ver el mensaje completo de una regla rota (y después **descartar el cambio**, sin
commitear):

1. En cualquier clase de `service/`, agregar `System.out.println("prueba");` dentro de un método.
2. Correr el comando del escenario A.
3. Falla `CodingRulesTest.ningunaClaseUsaLaSalidaEstandar` con un mensaje como:

   ```text
   [B7.1] Sin salida estándar, porque Principio V: nada de System.out; el logging va con SLF4J
   Infractores (1):
   - Method <...SomeService.someMethod()> gets field <java.lang.System.out> in (SomeService.java:42)
   ```

4. Descartar el cambio (`git checkout -- <archivo>`) y volver a correr: pasa.

Para ver que el reporte junta varias reglas (escenario 9 de la US1), repetir con dos violaciones a la vez, por ejemplo
el `System.out` y un controller que inyecta un repository: fallan los dos tests, cada uno con todos sus infractores.

## 4. Escenario C: reglas del frontend contra el código real (US2)

```bash
cd frontend
npx vitest run tests/architecture.test.ts
```

Esperado, con TopBar corregido: pasan los 13 casos de "código real" (uno por regla), la guarda que verifica que el
análisis leyó todo `src/` y los casos borde.

Antes de corregir TopBar (paso 3 del orden de implementación), el caso de F1 falla con:

```text
[F1] src/components/TopBar.tsx usa react-router: los componentes reciben datos y callbacks por props; no conocen la API, el router, los hooks ni los contextos (Constitución, sección Frontend)
```

Es la única violación del código actual (escenario 2 de la US2).

## 5. Escenario D: los casos borde del frontend (US2, SC-002)

Están en el mismo archivo, en el grupo "casos borde": cada uno arma fuentes en memoria y verifica la violación
esperada, o que no haya ninguna en los casos de frontera (comentarios que nombran `sessionStorage`, el CSS Module del
componente, `react-dom/client`, un test de `tests/pages/` sin página). La lista completa está en
[data-model.md](./data-model.md#4-casos-borde-del-frontend).

## 6. Cómo leer un fallo

1. **El identificador** (`[B1]`, `[F7]`) dice qué regla se rompió. Se busca en la sección "Tests de arquitectura" del
   README o en [contracts/architecture-rules.md](./contracts/architecture-rules.md): ahí está qué verifica y por qué.
2. **El porqué** cita el principio o la sección de la constitución que origina la regla.
3. **Los infractores**:
   - Backend: debajo de "Infractores (n):", una línea por violación con la clase, el método o el campo, el tipo del
     que depende y el archivo con su línea. En inglés (es la redacción de ArchUnit), salvo B4.3 y B7.5, que escriben
     en español. B6 muestra el ciclo entre contextos y las dependencias que lo forman.
   - Frontend: una línea por violación con el archivo y el import, paquete o identificador que la rompe. F9 muestra el
     ciclo completo.
4. **Qué hacer**: se corrige el código, no la regla. Si la regla parece equivocada para ese caso, se frena y se
   consulta al equipo (FR-008): no se agregan excepciones, no se congela la violación y no se desactiva la regla.

## 7. Escenario E: build completo y suite existente (FR-001, FR-002, SC-006)

```bash
cd backend
./gradlew build
```

```bash
cd frontend
npm test
```

```bash
cd frontend
npm run lint
```

```bash
cd frontend
npm run build
```

Esperado: todo en verde. En particular, en el backend pasan los 68 `@SpringBootTest` sin cambios: las fixtures están
fuera de `ar.edu.unq.desapp.futbolmarket` y Spring no las escanea. En el frontend pasa
`tests/pages/AppNavigation.test.tsx` sin cambios, que verifica el link del logo por su nombre accesible y su `href`.
`git diff --stat` no muestra ningún test existente modificado.

## 8. Escenario F: el logo de la barra superior (FR-081, escenario 3 de la US2)

Con el backend corriendo (`./gradlew bootRun`) y `npm run dev` en `frontend/`, en http://localhost:5173:

1. El logo se ve igual que antes: la pelota y "La Pizarra", del mismo tamaño y color, en modo claro y oscuro.
2. Un clic en el logo lleva a `/pizarra`.
3. Con Tab, el logo recibe el foco visible y Enter lleva a `/pizarra`.
4. Un lector de pantalla (o las herramientas de accesibilidad del navegador) lo anuncia como link "La Pizarra, ir al
   inicio".

## 9. Definición de terminado

- [ ] `./gradlew build` da BUILD SUCCESSFUL.
- [ ] `npm test`, `npm run lint` y `npm run build` terminan sin errores.
- [ ] Las 20 reglas del backend y las 13 del frontend tienen su caso feliz y al menos un caso borde (SC-002).
- [ ] Ninguna regla tiene excepciones, violaciones congeladas ni partes desactivadas (FR-008).
- [ ] Ningún test existente modificado ni borrado; `tests/setup.ts` sin cambios.
- [ ] El único cambio de producción es `TopBar` y `AppLayout`, con sus CSS Modules (SC-006).
- [ ] El `build.gradle` suma solo la dependencia de ArchUnit (y la exclusión de Sonar de D16, solo si hizo falta).
- [ ] Cada lado tarda menos de 30 s en sus verificaciones (SC-005).
- [ ] El README tiene la sección "Tests de arquitectura" con las 33 reglas y los comandos (US3, SC-007).
- [ ] El logo se comporta igual que antes (escenario F).
