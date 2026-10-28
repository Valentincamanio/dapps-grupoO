# Plan de implementación: Mercado de tokens y portfolio de usuarios

**Rama**: `008-mercado-tokens-portfolio` | **Fecha**: 2026-10-28 | **Especificación**: [spec.md](./spec.md)

**Entrada**: especificación de la feature en `/specs/008-mercado-tokens-portfolio/spec.md` (con su clarificación
del 2026-10-27), la constitución 2.2.1 y el pedido de planificación del 2026-10-28 (backend y frontend en el mismo
plan, como en la 007), con el estado del código relevado ese mismo día: la rama parte de `8e99cd6`, con la 005, la
006 y la 007 integradas; `develop` tiene un commit más (`cf0a0dd`).

## Resumen

El superusuario (el administrador de la 001) pone a la venta, al arrancar y después de cada sincronización, los
100 tokens de cada jugador del catálogo a 1 crédito por token. Los usuarios compran y venden tokens con órdenes
que se cruzan en el acto o quedan pendientes: una compra toma primero la orden del superusuario y después las
ventas de otros usuarios; una venta está bloqueada mientras el superusuario tenga tokens de ese jugador. Cada
usuario ve su portfolio actual o a una fecha pasada, su historial y sus órdenes; cualquiera ve el listado del
mercado; el administrador consulta una auditoría inmutable. El score de las cotizaciones pasa a mostrarse. Todo
por API y desde la aplicación web.

Enfoque técnico:

- **Contexto nuevo `market`** en modelo, servicio, controller y persistencia. Se tocan `user` (saldo reservado,
  fecha de registro, permiso sobre cuentas ajenas), `quote` (score y dos consultas), `sync` (órdenes iniciales de
  los jugadores nuevos) y `auth` (el reloj del registro). **Ninguna dependencia, paquete de primer nivel ni status
  code nuevo**; `shared/` suma la base `ForbiddenException` para el 403 de dominio.
- **Modelo rico** (`modelo/market/`, sin Spring, con `BigDecimal`): `MarketOrder`, `Holding` con su `CostBasis`,
  `Trade`, `AuditEntry`, el cruce puro `OrderMatcher` y el agregado `PlayerBook`, que valida, cruza y mueve
  créditos y tokens. `Portfolio`, `PortfolioPosition` y `TradeReplay` se calculan y no se guardan.
- **Saldo**: `balance` sigue siendo el total; `reserved` es una columna nueva; el libre es la diferencia.
- **Un método `@Transactional` por pedido**, con **bloqueo pesimista**: primero la orden inicial del jugador (el
  candado del libro), después las cuentas en orden ascendente de id. Precio y saldos se leen de la base dentro de
  la transacción.
- **Persistencia**: `market_orders`, `holdings`, `market_trades` y `audit_entries`, con nombres e índices
  explícitos, sin SQL nativo; operaciones y auditoría inmutables (`@Immutable`, sin setters, repositories sin
  update ni delete). `app_user` suma `reserved_balance` y `registered_at`.
- **Sin cachés nuevos**: el portfolio hace una sola consulta de cotizaciones por pedido.
- **Score visible**: `QuoteResponse` y `PlayerQuoteResponse` suman `score`; el contrato de la 007 pasa a 1.1.0.
- **Frontend** en la misma feature, por tipo de artefacto: pantallas Mercado, Portfolio, Operaciones y Mis
  órdenes; panel de operar en la ficha con confirmación; saldo libre en la barra; score en ficha, gráfico,
  ranking y pizarra; Vitest con MSW.

## Decisiones de planificación (2026-10-28)

El pedido resolvió estos puntos antes de escribir el diseño; este plan los adopta, con el detalle en
[research.md](./research.md):

1. **Contexto nuevo `market`**, declarado en el plan; se tocan `user`, `quote`, `player` (solo lectura) y `sync`
   (D2).
2. **Modelo rico** en `modelo/market/` con orden, tenencia, operación, auditoría y un objeto puro para el cruce con
   las reglas de prioridad, determinismo, órdenes propias y remanente; portfolio calculado; excepciones con nombre
   mapeadas por las bases de `shared/` (D3, D9, D10 y D23).
3. **Saldo** con métodos de `AppUser` para reservar, liberar, pagar y cobrar, y la invariante de no quedar
   negativo; el reservado es **columna** (D5).
4. **Tenencia del superusuario** = pendiente de su orden inicial, sin fila propia (D7).
5. **Órdenes iniciales** idempotentes, al arrancar (`ApplicationReadyEvent`) y al sincronizar, con restricción de
   unicidad y auditoría (D8).
6. **Transacción y concurrencia**: un `@Transactional` por pedido, bloqueo **pesimista** por jugador y cuentas en
   orden ascendente; `@Version` descartado y justificado (D13).
7. **Precio vigente** de la base al instante del `Clock`; redondeos de 2, 4 y 2 decimales (D4 y D14).
8. **Persistencia** con las tablas y los índices pedidos, más uno (D17).
9. **Portfolio actual** sin N+1 (D19).
10. **Portfolio a una fecha** con reconstrucción pura, valuación por lote y saldo desde las operaciones; se agrega
    `registeredAt` (D6 y D20).
11. **Score visible** en todas las cotizaciones y en el contrato de la 007 (D25).
12. **API**: las cuatro rutas de la consigna tal cual, más listado, órdenes propias, cancelación y auditoría (D21 a
    D23; contrato en [contracts/market-api.yaml](./contracts/market-api.yaml)).
13. **Seguridad**: `POST /orders/**` solo `USER`, `GET /orders` público (confirmado en la clarificación),
    `/users/{id}/**` del dueño o del `ADMIN`, `/audit` solo `ADMIN`; usuario siempre del principal (D24).
14. **Sin cachés nuevos** (D26).
15. **Frontend** con las rutas en español, la ficha con `TradePanel`, filtros y hoja en la dirección, saldo en la
    barra, score en las pantallas y mocks de MSW (D27).
16. **Tests** de dominio, servicio, integración y e2e, y del frontend, con los ejemplos del spec (D29).
17. **Puerta del Principio IV**: la lista exacta de tests que cambian y por qué (sección "Tests existentes que
    cambian" y D28).
18. **Orden de implementación** del pedido (sección "Orden de implementación sugerido").

### Lo que el plan resuelve distinto del pedido, o que hay que confirmar

Estos puntos **no frenan** el diseño. Detalle en `research.md` (D31):

| # | Punto | Qué hace este plan | Dónde |
|---|---|---|---|
| 1 | **Filtro `status` en `GET /orders`** (por defecto `PENDING`) | **Sigue el spec**: sin filtro de estado, solo pendientes y parciales (FR-047, clarificado) | D22 |
| 2 | **Portfolio con `QuoteQueryService.getCurrentOf`** "que usa el caché" | Ese método no está cacheado y rechaza más de 50 ids; se usa `QuoteRepository.findCurrentOf` (una consulta, sin tope) | D19 |
| 3 | **Órdenes iniciales "al terminar cada sincronización"** | Dentro de `SyncWriteService.applyLeague`, en la transacción de la liga: `SyncService` y `SyncCoordinator` los construyen tests a mano. Más la propiedad `futbolmarket.market.initial-orders.automatic`, apagada en el perfil test porque dos IT existentes borran `players` | D8 |
| 4 | **`Holding` con "precio promedio"** | Dos acumuladores (tokens y costo adquiridos desde la última vez en 0) y el promedio derivado: exacto, como lo define FR-037 | D12 |
| 5 | **Nombres `Order` y `Position`** | `MarketOrder` y `PortfolioPosition`, por los choques con `@Order`, `Sort.Order` y el enum `Position` | D3 |
| 6 | **Score "en QuotePanel, RankingList, QuoteChart y QuoteTable"** | También en `QuoteTag` de la pizarra (FR-071) | D27 |
| 7 | **Componentes y hooks** | Se suman `ProfitBadge` (FR-070) y `useMarketFilters` | D27 |
| 8 | **FR-057 (saldo libre y reservado en el perfil)** | Chocaba con `AccountControllerIT`, que fija las claves exactas del perfil y que el "sí" del 2026-10-27 no cubría: **puerta G3, cumplida el 2026-10-28** (Lucas y Valentin) | D28 |
| 9 | **Reserva de un remanente que ya no se puede reservar completa** (hueco de FR-027) | Queda con lo disponible y el libre en 0, sin cancelar | D11 |
| 10 | **`.specify/feature.json` apuntaba a la 006** | Se corrigió a `specs/008-mercado-tokens-portfolio` para generar este plan | n/a |

## Puertas antes de implementar

Ninguna es una duda de comportamiento: son condiciones del proceso.

| # | Puerta | Estado al 2026-10-28 |
|---|---|---|
| G1 | **La rama tiene todo `develop`**: la 005, la 006, la 007 y `cf0a0dd` (exclusiones de Sonar y `ScheduleValidation`) | **Pendiente**: falta traer `cf0a0dd`. No hay conflicto esperado (toca `build.gradle` y dos clases de `config/`) |
| G2 | **El "sí" explícito del equipo** para modificar `NoPurchaseActions.test.tsx`, `NoMarketActions.test.tsx` y los tests de la 007 que verifican que el score no se expone | **Cumplida el 2026-10-27** (Valentin), solo para los cambios de la tabla "Tests existentes que cambian" marcados como cubiertos |
| G3 | **Un "sí" nuevo** para cambiar la aserción de `AccountControllerIT.elPerfilTieneExactamenteLasClavesDelContratoYNingunSecreto` (FR-057) | **Cumplida el 2026-10-28** (Lucas y Valentin), registrada en Clarifications. Alcanza solo a sumar `availableBalance` y `reservedBalance` al conjunto exacto de claves de esa aserción |
| G4 | La confirmación de los puntos 9 de la tabla anterior y 2 a 4 de D31 | Conviene antes de implementar; no bloquea el diseño |

## Contexto técnico

**Lenguaje y versión**: Java 21 (Temurin) con el wrapper de Gradle; TypeScript en modo `strict`.

**Dependencias principales**:

- Backend: Spring Boot 4.1.1 con `webmvc`, `data-jpa`, `security`, `validation`, `actuator` y `h2console`; Lombok,
  springdoc-openapi 3.1.1 y Jackson 3. **Ninguna nueva**: `@Lock` es de Spring Data JPA; `@Immutable` y
  `@ColumnDefault`, de Hibernate (D1).
- Frontend: React 18.3, React Router 7.18, Vite 8, Vitest 4, React Testing Library, MSW 2.15 y ESLint con
  `jsx-a11y`. **Ninguna nueva**.

**Almacenamiento**: H2. En `local`, en archivo con `ddl-auto: update` (las tablas nuevas se crean y las dos
columnas de `app_user` se agregan sin romper una base existente); en `test`, en memoria con `create-drop`. Cuatro
tablas nuevas. Sin SQL nativo. `LOCK_TIMEOUT=10000` en la URL de H2 de `local` y de los IT del mercado (D13).

**Tests**: JUnit 5, Mockito, AssertJ, `MockMvcTester` con `@SpringBootTest` y `@ActiveProfiles("test")` en el
backend; Vitest, RTL y MSW en el frontend. Sin Testcontainers, sin TDD.

**Plataforma**: API REST en el puerto 8080 (`./gradlew bootRun`) y aplicación web con `npm run dev` (proxy de Vite
`/api` → `:8080`). El mercado no consulta ninguna fuente externa (FR-030).

**Tipo de proyecto**: aplicación web en un monorepo (`backend/` y `frontend/`). Se tocan los dos.

**Objetivos de rendimiento** (del spec, con unos 2.600 jugadores y la base local):

- Una orden ejecutada y su resultado, en menos de 2 s (SC-009).
- El portfolio actual o a una fecha, una hoja del historial y una del listado, en menos de 2 s (SC-009).
- Comprar desde la ficha, confirmación incluida, en menos de 30 s (SC-008).
- 50 pedidos simultáneos sin romper las invariantes (SC-002).

**Restricciones**:

- Aritmética con `BigDecimal`; un solo redondeo por total.
- Ningún saldo ni tenencia negativo; Σ tenencias de cada jugador = 100; Σ saldos = Σ saldos iniciales.
- Cada pedido, todo o nada; operaciones y auditoría nunca se modifican ni se borran.
- No se reemplazan `build.gradle` ni los `application*.yml`: se agrega una clave y se completa la URL local.
- Frontend: sin carpetas nuevas; tests solo en `frontend/tests/`; ningún test existente se toca fuera de lo que
  cubren G2 y G3.

**Escala y alcance**:

- 2.600 jugadores con su orden inicial; unas 2.600 entradas de auditoría al primer arranque.
- 8 endpoints nuevos (1 público, 6 de usuario y 1 de administrador) en un contrato, y 2 cambios: el perfil de la
  001 (G3) y el score de la 007.
- 72 requisitos funcionales (FR-001 a FR-072) y 15 criterios de éxito.
- 4 pantallas nuevas y 6 ampliadas (ficha, pizarra, ranking, barra, gráfico y su tabla).

No queda ningún `NEEDS CLARIFICATION`: todo se resolvió en [research.md](./research.md) o figura en D31 como punto a
confirmar.

## Verificación constitucional

*Puerta: tiene que pasar antes de la investigación y se revisa de nuevo después del diseño.*

### Antes de la investigación

| Principio | Resultado | Evidencia |
|---|---|---|
| I. Capas estrictas (NO NEGOCIABLE) | Cumple | Ver el detalle debajo de la tabla |
| II. Modelo rico | Cumple | Las decisiones viven en `PlayerBook`, `OrderMatcher`, `MarketOrder`, `Holding`, `CostBasis`, `AppUser`, `Portfolio` y `TradeReplay`. Los servicios bloquean, cargan, delegan y guardan; los `if` que quedan son de la aplicación (la propiedad `automatic`, si hay superusuario) |
| III. Validación en su nivel | Cumple | Forma en los DTO (cantidad entera de 1 a 100, `playerId` ≥ 1, filtros por tipo); existencia (jugador, usuario, orden) en el servicio; invariantes (saldo, tokens, operabilidad, bloqueo, propiedad, fechas) en el modelo con excepciones con nombre. Un único advice; status 400, 401, 403, 404 y 409, todos de la base |
| IV. Tests (NO NEGOCIABLE) | Cumple | Unitarios de dominio sin Spring, de servicio con Mockito, integración en H2 con `@ActiveProfiles("test")`, e2e en `e2e/`. Sin TDD. **Ningún test existente se modifica fuera de lo que cubren los "sí" de G2 y G3**, los dos registrados en Clarifications. El diseño evita tocar `AuthServiceTest`, `SyncServiceTest`, `SyncCoordinatorTest`, `LockerRoomPage.test.tsx` y los tests de componentes de la 007 (D28) |
| V. Calidad medible | Cumple | Escalas y topes como constantes (`MarketMath`); `CostBasis` compartido para no duplicar el cálculo; métodos cortos (el cruce separado de su aplicación); inyección por constructor; SLF4J; sin `catch (Exception)`; Sonar con menos de 10 issues |
| VI. Persistencia explícita | Cumple | Cuatro tablas con `@Table(name = ...)` que evitan `order` y `transaction`; índices con nombre; derived queries, JPQL y `@Lock`; `open-in-view: false`; enums como texto |
| VII. Idioma | Cumple | Identificadores, endpoints (`/orders`, `/users`, `/audit`) y contexto (`market`) en inglés; mensajes, documentación y comentarios en español; rutas de la web en español (`/mercado`, `/portfolio`, `/operaciones`, `/ordenes`) |
| Stack tecnológico | Cumple | Sin dependencias nuevas (D1) |
| Frontend | Cumple | Por tipo de artefacto, sin carpetas nuevas; tests solo en `frontend/tests/`; componentes sin `fetch` ni router; tipos a mano desde el contrato; accesible y desde 360 px |
| Definición de terminado | Cumple | `./gradlew build` y `bootRun`; Swagger con los endpoints nuevos; `npm test`, `npm run lint`, `npm run build` y `npm run dev` ([quickstart.md](./quickstart.md), sección 12) |

Detalle del principio I:

- **Declaración de contexto nuevo** (la constitución exige declararlo en el plan): `market`, en `modelo/`,
  `service/`, `controller/` y las cuatro carpetas de `persistence/`. No aparece en `adapter/`.
- **Declaración de status codes**: 400, 401, 403, 404 y 409. **No se suma ningún status**: el 403 ya existe (lo
  emite Spring Security) y `ForbiddenException` es la base de dominio que faltaba para emitirlo desde el advice.
- **Declaración de carpetas**: no se crea ninguna fuera del árbol; `exception/` y `dto/` son las subcarpetas que el
  árbol ya prevé.
- **El modelo es puro**: `modelo/market/` solo usa el JDK y clases de modelo (`AppUser`, `Player`, `Quote`,
  `QuoteDay`, `QuotePage`, `PlayerFilter`). Ninguna anotación de JPA ni de Spring.
- **`*SQL` y mappers**: `MarketOrderSQL`, `HoldingSQL`, `TradeSQL`, `AuditEntrySQL` y el `@Embeddable`
  `AuditPartySQL` llevan las anotaciones; los mappers traducen campo a campo (incluido enum ↔ texto y la marca de
  inicial ↔ `initial_player_id`) y los invoca `repository/`.
- **Controller y DTO**: los tres controllers solo hablan con servicios; los `Request` y `Response` viven en
  `controller/market/dto/` y no cruzan al servicio; ningún `*SQL` sale en una respuesta.
- **Monolito**: un pedido modifica cuentas (`user`), órdenes, tenencias, operaciones y auditoría (`market`) en UN
  método `@Transactional` de `MarketOrderService`. `service.sync → service.market` (órdenes iniciales) sin ciclo.
- **Sin contratos entre features**: el cambio a la 007 es una versión de su propio contrato, no un contrato de
  integración.

### Después del diseño

Se revisaron [data-model.md](./data-model.md), [contracts/](./contracts/) y [quickstart.md](./quickstart.md) contra
cada principio:

- **I**: el grafo de dependencias entre paquetes no tiene ciclos:
  - `controller.market → service.market, modelo`
  - `service.market → persistence.repository (market, user, player, quote), config (MarketProperties), modelo`
  - `service.sync → service.market (InitialOrderService)`; `service.market` no usa `service.sync` ni
    `service.quote`.
  - `service.auth` y `service.user` → `Clock` (nuevo colaborador, sin ciclo).
  - `persistence → modelo`; `security → modelo.user`.
- **II**: `MarketOrderService`, `InitialOrderService`, `PortfolioService`, `MarketQueryService` y
  `AuditQueryService` no tienen un `if` de negocio; la tabla de trazabilidad de `data-model.md` (sección 8) asigna
  cada regla a una clase del modelo.
- **III**: los mensajes están en español, con el tono de los existentes ("No tiene permisos..."), y no repiten
  datos de otros usuarios. Las excepciones de la sección 4 de `data-model.md` son todas con nombre.
- **IV**: la lista de tests incluye los ejemplos numéricos del spec, los bordes pedidos y la concurrencia. Los
  tests de concurrencia verifican el estado final, no el orden de los hilos. Ninguno depende de la hora real
  (`MutableClock`).
- **V**: la medición de volumen es opt-in (`MARKET_BENCHMARK`), así no demora `./gradlew build`.
- **VI**: no hay SQL nativo. Las consultas con `FOR UPDATE` son de una tabla; la unicidad de la orden inicial no
  necesita un índice parcial.
- **Frontend**: cada archivo nuevo va en la carpeta de su tipo; el cliente de `fetch` sigue siendo uno solo;
  `RequireRole` suma una prop sin cambiar su comportamiento por defecto.

**Resultado**: la puerta pasa, antes y después del diseño, con G2 y G3 cumplidas.

## Estructura del proyecto

### Documentación de la feature

```text
specs/008-mercado-tokens-portfolio/
├── spec.md                        # especificación (sin cambios en este plan)
├── plan.md                        # este archivo
├── research.md                    # decisiones D1 a D31
├── data-model.md                  # modelo, persistencia, consultas, transiciones, trazabilidad y tipos del frontend
├── quickstart.md                  # guía de validación de punta a punta (curl y web)
├── contracts/
│   └── market-api.yaml            # OpenAPI 3.0.3: órdenes, mercado, portfolio, historial, auditoría y el perfil
├── checklists/requirements.md     # existente
└── tasks.md                       # lo genera después /speckit-tasks

specs/007-cotizacion-estrategias/contracts/
└── quotes-api.yaml                # CAMBIA: versión 1.1.0, score en Quote (descripción, esquema y ejemplos)
```

### Código fuente: backend

En `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/`. `NUEVO` es un archivo nuevo; `CAMBIA` es uno existente
al que se le **agrega** código sin cambiar las firmas que usan los tests existentes.

```text
config/
├── MarketProperties.java                   # NUEVO: futbolmarket.market.initial-orders.automatic
└── ApplicationConfig.java                  # CAMBIA: + MarketProperties en @EnableConfigurationProperties

controller/
├── market/                                 # NUEVO contexto
│   ├── OrderController.java                # POST /orders/buy, /orders/sell, /orders/{id}/cancel; GET /orders
│   ├── PortfolioController.java            # GET /users/{id}/portfolio, /users/{id}/transactions, /users/{id}/orders
│   ├── AuditController.java                # GET /audit
│   └── dto/                                # OrderRequest, PlayerRefResponse, MarketOrderResponse, MarketOrderPageResponse,
│                                           #   UserOrderResponse, UserOrderPageResponse, QuoteUsedResponse, TradeSummaryResponse,
│                                           #   OrderExecutionResponse, OrderCancellationResponse, PortfolioResponse,
│                                           #   PortfolioPositionResponse, PortfolioTotalsResponse, TransactionResponse,
│                                           #   TransactionPageResponse, AuditEntryResponse, AuditPartyResponse, AuditPageResponse
├── quote/dto/
│   ├── QuoteResponse.java                  # CAMBIA: + score; el comentario deja de decir que no lo lleva
│   └── PlayerQuoteResponse.java            # CAMBIA: + score
└── user/dto/
    └── ProfileResponse.java                # CAMBIA (G3): + availableBalance y reservedBalance

modelo/
├── market/                                 # NUEVO contexto
│   ├── OrderSide.java  OrderStatus.java  OwnerType.java  CancelReason.java  NotTradableReason.java
│   ├── AuditEventType.java  AuditActorType.java
│   ├── MarketMath.java                     # escalas, total, mayor cantidad que alcanza, promedio y porcentaje
│   ├── MarketOrder.java                    # cantidades, estado derivado, reserva, inicial
│   ├── CostBasis.java  Disposal.java       # precio promedio y ganancia realizada (compartido con TradeReplay)
│   ├── Holding.java                        # tenencia de un usuario: CostBasis y comprometidos
│   ├── Trade.java  TradeView.java          # la operación (inmutable) y su lectura para el historial
│   ├── PartyState.java  PartyChange.java   # estados anterior y posterior de una parte
│   ├── AuditEntry.java                     # inmutable, con sus fábricas y el detalle en español
│   ├── OrderMatcher.java  MatchPlan.java  Fill.java  Shortfall.java   # el cruce puro
│   ├── PlayerBook.java  BookOutcome.java   # el agregado del libro: valida, cruza y aplica
│   ├── Portfolio.java  PortfolioPosition.java  PortfolioTotals.java
│   ├── TradeReplay.java                    # el portfolio a una fecha
│   ├── MarketOrderFilter.java  TradeFilter.java  AuditFilter.java
│   └── exception/                          # InsufficientCredits, InsufficientTokens, PlayerNotTradable,
│                                           #   SaleBlockedByInitialOrder, OppositeOrderPending, OrderNotCancellable,
│                                           #   MarketBusy, OrderNotFound, OrderNotOwned, TradingNotAllowed, MarketInvariant
└── user/
    ├── AppUser.java                        # CAMBIA: + reserved, registeredAt y sus métodos; se conservan las fábricas actuales
    └── exception/                          # NUEVO: UserNotFound, AccountAccessDenied, DateBeforeRegistration

persistence/
├── repository/
│   ├── market/                             # NUEVO: MarketOrderRepository, HoldingRepository, TradeRepository, AuditRepository
│   ├── user/AppUserRepository.java         # CAMBIA: + lockById, lockAllInOrder, findSuperuser
│   └── quote/QuoteRepository.java          # CAMBIA: + findInForceAt, findAtOf
├── mapper/
│   ├── market/                             # NUEVO: MarketOrderMapper, HoldingMapper, TradeMapper, AuditEntryMapper
│   └── user/AppUserMapper.java             # CAMBIA: + reserved y registeredAt
└── sql/
    ├── entity/
    │   ├── market/                         # NUEVO: MarketOrderSQL, HoldingSQL, TradeSQL (@Immutable), AuditEntrySQL (@Immutable),
    │   │                                   #   AuditPartySQL (@Embeddable)
    │   └── user/AppUserSQL.java            # CAMBIA: + reserved_balance (@ColumnDefault) y registered_at
    └── interfaces/
        ├── market/                         # NUEVO: MarketOrderSQLDAO, HoldingSQLDAO, TradeSQLDAO, AuditEntrySQLDAO
        ├── user/AppUserSQLDAO.java         # CAMBIA: + findWithLockById (@Lock), findFirstByRoleOrderByIdAsc
        └── quote/QuoteSQLDAO.java          # CAMBIA: + findInForceAt, findLastBeforeOf (JPQL)

security/
└── SecurityConfig.java                     # CAMBIA: GET /orders público; POST /orders/** solo USER; /audit/** solo ADMIN

service/
├── market/                                 # NUEVO contexto
│   ├── MarketOrderService.java             # buy, sell, cancel: un @Transactional cada uno
│   ├── PlayerBookLoader.java               # bloquea y carga en el orden de D13 y arma el PlayerBook
│   ├── InitialOrderService.java            # openMissing y openFor, idempotentes
│   ├── InitialOrderStartup.java            # @Async sobre ApplicationReadyEvent
│   ├── PortfolioService.java               # actual y a una fecha (readOnly)
│   ├── MarketQueryService.java             # listado, historial y órdenes propias (readOnly)
│   └── AuditQueryService.java              # auditoría (readOnly)
├── auth/AuthService.java                   # CAMBIA: + constructor con Clock; se conserva el de cuatro argumentos
├── user/AdminAccountInitializer.java       # CAMBIA: + Clock para registeredAt
└── sync/SyncWriteService.java              # CAMBIA: applyLeague llama a InitialOrderService.openFor

shared/
├── ForbiddenException.java                 # NUEVO: base de las excepciones de dominio que el advice traduce a 403
└── GlobalExceptionHandler.java             # CAMBIA: + handler de ForbiddenException
```

Recursos: `application.yaml` suma `futbolmarket.market.initial-orders.automatic: true`; `application-test.yml` la
pone en `false`; `application-local.yml` completa la URL de H2 con `;LOCK_TIMEOUT=10000`. `build.gradle` no cambia.

### Código fuente: frontend

En `frontend/src/`, por tipo de artefacto, sin carpetas nuevas.

```text
types/
├── market.ts                               # NUEVO: tipos a mano desde contracts/market-api.yaml
├── api.ts                                  # CAMBIA: Profile + availableBalance y reservedBalance (G3)
└── quotes.ts                               # CAMBIA: Quote + score? (opcional a propósito, D27) y el comentario del encabezado

services/
├── marketService.ts                        # NUEVO: getMarketOrders, buyTokens, sellTokens, cancelOrder, getUserOrders
└── portfolioService.ts                     # NUEVO: getPortfolio, getTransactions

hooks/
├── useMarketOrders.ts                      # NUEVO: listado y pendientes de un jugador
├── useMarketFilters.ts                     # NUEVO: filtros y hoja en la dirección
├── usePortfolio.ts                         # NUEVO: actual o a una fecha
├── useTransactions.ts                      # NUEVO
├── useMyOrders.ts                          # NUEVO
└── useOrderCommands.ts                     # NUEVO: comprar, vender y cancelar sobre useMutation; refresca el perfil

components/                                 # cada uno con su .module.css; reciben datos y callbacks, sin API ni router
├── TradePanel.tsx                          # NUEVO: cantidad, comprar o vender, avisos, estimado y resultado
├── ConfirmDialog.tsx                       # NUEVO: role="dialog", foco en confirmar, Escape cancela
├── OrderList.tsx                           # NUEVO: órdenes (listado, ficha y propias); tarjetas a 360 px
├── MarketFilters.tsx                       # NUEVO: tipo, liga, equipo y tipo de usuario
├── PositionTable.tsx                       # NUEVO: posiciones del portfolio
├── PortfolioTotals.tsx                     # NUEVO: totales
├── ProfitBadge.tsx                         # NUEVO: ganancia o pérdida con signo, texto y triángulo
├── QuotePanel.tsx                          # CAMBIA: + par "Score"
├── RankingList.tsx                         # CAMBIA: + columna de score, al final
├── QuoteChart.tsx                          # CAMBIA: + score al final del detalle de cada punto
├── QuoteTable.tsx                          # CAMBIA: + columna de score, al final
├── QuoteTag.tsx                            # CAMBIA: + score en la pizarra
└── TopBar.tsx                              # CAMBIA: la etiqueta dice "Saldo libre"

pages/
├── MarketPage.tsx                          # NUEVO: /mercado
├── PortfolioPage.tsx                       # NUEVO: /portfolio (con selector de fecha)
├── TransactionsPage.tsx                    # NUEVO: /operaciones
├── MyOrdersPage.tsx                        # NUEVO: /ordenes
├── PlayerPage.tsx                          # CAMBIA: + órdenes pendientes y TradePanel (solo USER)
└── AppLayout.tsx                           # CAMBIA: enlaces por rol y saldo libre

router/
├── RequireRole.tsx                         # CAMBIA: + prop role (ADMIN por defecto)
└── routes.tsx                              # CAMBIA: + /mercado, /portfolio, /operaciones, /ordenes

utils/
└── market.ts                               # NUEVO: etiquetas en español, estimado de compra y venta, formatos con signo
```

### Tests: backend

En `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/`, replicando la estructura de producción. Todos **nuevos**.

```text
modelo/market/          MarketMathTest  CostBasisTest  MarketOrderTest  HoldingTest  OrderMatcherTest  PlayerBookTest
                        TradeReplayTest  PortfolioTest  AuditEntryTest  MarketFixtures (apoyo)
modelo/user/            AppUserMarketTest
service/market/         MarketOrderServiceTest  InitialOrderServiceTest  InitialOrderStartupTest  PortfolioServiceTest
                        MarketQueryServiceTest  AuditQueryServiceTest
                        MarketOrderServiceIT  MarketConcurrencyIT  InitialOrderIT  AuditAtomicityIT  PortfolioIT
                        MarketVolumeIT (opt-in)  MarketDatabase (apoyo: limpia las tablas del mercado y después
                        delega en QuoteDatabase)
persistence/repository/ market/MarketOrderRepositoryIT  market/HoldingRepositoryIT  market/TradeRepositoryIT
                        market/AuditRepositoryIT  quote/QuoteRepositoryMarketIT
shared/                 GlobalExceptionHandlerForbiddenTest
config/                 MarketPropertiesTest
e2e/                    OrderControllerIT  MarketListingIT  PortfolioControllerIT  TransactionsControllerIT
                        AuditControllerIT  MarketAccessIT  QuoteScoreIT
```

**Casos que tienen que estar** (Principio IV; setup-execute-verify, `assertThat`, nombres descriptivos):

| Grupo | Casos |
|---|---|
| **Ejemplos numéricos del spec**, en dominio | compra de 10 a la inicial con 100 (990,00, 10 %, quedan 90); inicial con 3 y compra de 10 (3 ejecutados, 7 pendientes); compra de 10 contra una venta de 5 (5 pendientes); venta rechazada mientras la inicial tiene 1; reserva de 10,30 que baja a 6,18 con libre 989,62; `3 × 1,0333 = 3,10` y `10 × 1,0345 = 10,35`; promedio 1,0100 (y 1,1000), invertido 10,10, ganancia realizada +0,20 (y +1,00); 9 ejecutados y 1 cancelado a 1,1000 con 0,40 liberados; 10 tokens que valen 10,00, 12,00 y 9,00; patrimonio 1016,78; portfolio al 25/08 (10 a 1,0300) y al 01/09 (15 a 1,0100) |
| **Reglas de cruce** | el superusuario primero; de la más vieja a la más nueva con desempate por id; las propias no se cruzan; ejecución parcial en los dos sentidos; compra sin saldo para un token se cancela y la venta sigue; el libro nunca tiene pendientes de los dos lados; determinismo con la entrada desordenada; el hueco de D11 |
| **Bordes del spec** | sin superusuario (nada se genera, se registra el motivo); jugador sin orden inicial, sin cotización o inactivo (409); cancelar con el jugador inactivo; compra que se ejecuta más barata que su reserva; venta que deja la tenencia en 0 y la recompra con promedio nuevo; dos pedidos del mismo usuario que juntos superan su saldo; operación durante un recálculo (usa la última semana completa) |
| **Integración en H2** | invariantes suma = 100, conservación de créditos y `reserved` = Σ reservas después de cada escenario; **20 compras simultáneas contra 10 disponibles**; 50 pedidos mezclados; órdenes iniciales **sin duplicar al reiniciar y al sincronizar** (y las dos a la vez); **auditoría en la misma transacción** (falla forzada → ni orden ni auditoría); un pedido rechazado no deja auditoría |
| **API** | 400 (cuerpo, cantidad 0, negativa, decimal y 101, filtros, fechas), 401, **403** (administrador en `/orders/buy`, `/orders/sell` y cancelar; usuario común en `/audit` y en las cuentas ajenas), 404 (jugador, usuario y orden), 409 (cada conflicto); el `userId` del cuerpo se ignora; el listado es público y no revela al dueño; el historial dice "administrador" u "otro usuario"; **el score en cada respuesta de cotización** y ninguna estadística |

### Tests: frontend

En `frontend/tests/`, todos **archivos nuevos** (los existentes solo cambian lo que cubre G2):

```text
utils/       market.test.ts
components/  TradePanel.test.tsx  ConfirmDialog.test.tsx  OrderList.test.tsx  MarketFilters.test.tsx
             PositionTable.test.tsx  PortfolioTotals.test.tsx  ProfitBadge.test.tsx
hooks/       useMarketOrders.test.tsx  useMarketFilters.test.tsx  usePortfolio.test.tsx  useTransactions.test.tsx
             useMyOrders.test.tsx  useOrderCommands.test.tsx
pages/       MarketPage.test.tsx  PortfolioPage.test.tsx  TransactionsPage.test.tsx  MyOrdersPage.test.tsx
             PlayerTrade.test.tsx  MarketAccess.test.tsx  QuoteScore.test.tsx
mocks/       marketHandlers.ts  market.ts   (nuevos)    handlers.ts  quotes.ts   (CAMBIAN, apoyo: ver la tabla de abajo)
```

**Casos que tienen que estar** (felices y de borde, en español):

| Pantalla o pieza | Casos |
|---|---|
| Ficha | sin sesión y como administrador, sin controles de operar pero con órdenes pendientes, cotización y score; como usuario, comprar con confirmación (nada se envía antes), resultado real y saldo actualizado; aviso de venta bloqueada con los tokens que le quedan al superusuario; jugador inactivo o sin cotización explica por qué no se opera; rechazo del servidor en español sin dar la operación por hecha |
| Mercado | listado público; filtros y hoja en la dirección (`side`, `league`, `team`, `ownerType`, `page`), la hoja vuelve a 0 al cambiar un filtro, recargar reproduce la vista; nunca muestra al dueño; carga, error con reintentar y vacío |
| Portfolio | posiciones y totales; ganancia o pérdida con signo **y texto**; fecha elegida en la dirección y vuelta al actual; a una fecha no muestra libre ni reservado; vacío |
| Operaciones | historial con filtros y hoja en la dirección; contraparte "administrador" u "otro usuario"; rango invertido rechazado |
| Mis órdenes | estados y cantidades; cancelar con confirmación; tras cancelar, el saldo de la barra cambia |
| Acceso | visitante en `/portfolio`, `/operaciones` y `/ordenes` → `/login`; administrador → aviso de falta de permiso; enlaces por rol en la barra |
| Score | `QuotePanel`, `RankingList`, `QuoteChart`, `QuoteTable`, `QuoteTag` y el portfolio lo muestran; ninguna estadística |
| Accesibilidad | todo lo clickeable es `button` o enlace; el diálogo toma el foco, Enter confirma y Escape cancela |

### Tests existentes que cambian (FR-060)

**El "sí" explícito del equipo (Valentin, 2026-10-27) está registrado en Clarifications y cubre solo los cambios
marcados con G2; no autoriza borrar tests ni otras aserciones.** El de `AccountControllerIT` lo cubre otro "sí"
(Lucas y Valentin, 2026-10-28, G3), también registrado en Clarifications y acotado a esa aserción. Detalle y
verificación de cada uno en D28.

| Test | Cambio | Por qué | Cubierto |
|---|---|---|---|
| `frontend/tests/pages/NoPurchaseActions.test.tsx`, `FORBIDDEN` (línea 7) | `/comprar\|vender\|sumar\|plantel\|presupuesto\|puntaje\|portfolio\|orden/i` → `/sumar\|plantel/i` | Con sesión, la barra tiene "Portfolio" y la ficha "Comprar" y "Vender" | G2 |
| ídem, comentario (línea 1) y `describe` | Redacción: sin acciones de "sumar al plantel" | La descripción afirma lo que la feature levanta | G2 |
| `frontend/tests/pages/NoMarketActions.test.tsx`, `MARKET_ACTION` (línea 13) | `/compra\|comprar\|vend\|sumar\|plantel\|orden/i` → `/sumar\|plantel/i` | Mismo motivo | G2 |
| ídem, `INVESTMENT_DATA` (línea 14) | `/presupuesto\|portfolio\|cartera\|\btokens\b\|mis acciones/i` → `/cartera\|mis acciones/i` | La ficha muestra las órdenes pendientes con sus tokens, también al administrador | G2 |
| ídem, comentario (líneas 1 a 3) y `describe` | Redacción | Mismo motivo | G2 |
| `backend/.../e2e/QuoteControllerIT.java`, `ningunaRespuestaTraeElScoreNiEstadisticas` | Sacar `"score"` del `doesNotContain`, sumar `contains("\"score\"")` y renombrar a `ningunaRespuestaTraeEstadisticasYTodasTraenElScore` | FR-058; el valor base y las estadísticas siguen ocultos | G2 |
| `backend/.../e2e/AccountControllerIT.java`, `elPerfilTieneExactamenteLasClavesDelContratoYNingunSecreto` (línea 75) | Sumar `"availableBalance"` y `"reservedBalance"` al conjunto exacto | FR-057 | G3 |

**No cambian** (verificado): `PostIt.test.tsx` (el post-it no suma textos de compra ni venta), `PlayerPage.test.tsx`
(renderiza sin sesión y un visitante no ve `TradePanel`), los tests de componentes y utilidades de la 007 (el score
va al final y es opcional en el tipo), `LockerRoomPage.test.tsx` (el vestuario no cambia), `AuthControllerIT`,
`AuthServiceTest`, `AppUserTest`, `AppUserRepositoryIT`, `JwtServiceTest`, `AccountServiceTest` (se conservan sus
firmas), `SyncServiceTest`, `SyncServiceLastFullSyncTest`, `SyncCoordinatorTest`, `SyncWriteServiceIT`,
`SyncControllerIT` y `StatsSyncControllerIT` (la generación automática está apagada en el perfil test).

**Apoyo que cambia (no son tests)**: `frontend/tests/mocks/handlers.ts` (suma `marketHandlers` y los dos saldos al
perfil simulado; sin los handlers, `onUnhandledRequest: 'error'` hace fallar la ficha) y
`frontend/tests/mocks/quotes.ts` (`score` en las cotizaciones simuladas y los saldos del perfil del administrador).
Son aditivos.

**Se conserva la prohibición de "sumar al plantel"** en los dos tests del frontend.

**Cuándo se tocan**: el de `QuoteControllerIT` en el paso 1 del orden de implementación (junto con el score); los dos
del frontend en el paso 11, en la misma tarea que agrega los enlaces de la barra y el `TradePanel`; el de
`AccountControllerIT` en el paso 6, en la misma tarea que suma los saldos al perfil.

## Orden de implementación sugerido

Esto no son las tareas (las genera `/speckit-tasks`), sino el orden de dependencias para ordenarlas. Es el del pedido.

0. **Puertas**: G1 (traer `cf0a0dd`). G2 y G3 ya están cumplidas.
1. **Score en la 007**: `QuoteResponse` y `PlayerQuoteResponse`, el comentario, `QuoteScoreIT` y el cambio de
   `QuoteControllerIT` cubierto por G2. El contrato 1.1.0 ya está en este plan.
2. **Dominio del mercado y del cruce**: `MarketMath`, `CostBasis`, `MarketOrder`, `Holding`, `Trade`, `AuditEntry`,
   `OrderMatcher`, `PlayerBook`, los cambios de `AppUser` y sus excepciones, con los ejemplos del spec.
3. **Persistencia**: las cuatro tablas, las columnas de `app_user`, mappers, DAOs con `@Lock`, repositories y las dos
   consultas nuevas de la 007; IT de repositories.
4. **Órdenes iniciales**: `MarketProperties`, `InitialOrderService`, `InitialOrderStartup`, el llamado desde
   `SyncWriteService` y el reloj del registro (`AuthService`, `AdminAccountInitializer`); `InitialOrderIT`.
5. **Comprar, vender y cancelar con concurrencia**: `PlayerBookLoader`, `MarketOrderService`, `ForbiddenException`
   y su handler, `LOCK_TIMEOUT`; `MarketOrderServiceIT`, `MarketConcurrencyIT` y `AuditAtomicityIT`.
6. **Portfolio actual**: `Portfolio`, `PortfolioService`; el perfil con los saldos y el cambio de
   `AccountControllerIT` cubierto por G3.
7. **Historial, órdenes propias y listado del mercado**: `MarketQueryService` y sus consultas.
8. **Portfolio a una fecha**: `TradeReplay`, `findAtOf`, el saldo a la fecha y `requireRegisteredBy`; `PortfolioIT`.
9. **Auditoría y su consulta**: `AuditQueryService` y su filtro.
10. **Seguridad y Swagger**: `SecurityConfig`, los tres controllers con sus `@Operation` y `@ApiResponse`, los IT de
    `e2e/`.
11. **Frontend**: tipos, servicios, utilidades, hooks y mocks; ficha con `TradePanel` y órdenes; Mercado; Portfolio;
    Operaciones; Mis órdenes; score en las pantallas; barra y rutas; los cambios de los dos tests cubiertos por G2.
12. **Validación con el [quickstart](./quickstart.md)**, incluido el escenario 3 de la consigna (4 usuarios, 5
    jugadores, valoración actual y a fechas pasadas), la medición de volumen y la definición de terminado.

El frontend (paso 11) puede empezar en paralelo desde el paso 2: trabaja contra MSW con las formas de `contracts/`.

## Seguimiento de complejidad

Sin violaciones de la constitución que justificar. Decisiones que conviene tener a la vista:

| Decisión | Por qué hace falta | Alternativa más simple descartada |
|---|---|---|
| Bloqueo pesimista con un orden fijo (libro, después cuentas ascendentes) | Atomicidad e invariantes con pedidos simultáneos, sin reintentos (D13) | `@Version` con reintento: la cuenta del superusuario cambia en casi todas las compras iniciales y el reintento necesita otra capa o una dependencia |
| `reserved` como columna redundante | El modelo verifica la invariante solo y el bloqueo de la cuenta alcanza (D5) | Derivarlo de las órdenes pendientes: obliga a bloquear órdenes de otros jugadores |
| Órdenes iniciales dentro de la transacción de cada liga y una propiedad para apagarlas en test | No tocar tests existentes y que un jugador nuevo nunca exista sin su orden (D8) | Llamarlas desde `SyncService`: cambia tres tests que lo construyen a mano |
| Sobrecargas de `AppUser.register`, `createAdmin` y `reconstitute`, y un constructor más en `AuthService` | Que los tests de la 001 no cambien (Principio IV) (D5 y D6) | Cambiar las firmas: obliga a tocar cinco clases de test |
| `score` opcional en el tipo `Quote` del frontend | Que cuatro tests de la 007 con objetos `Quote` literales sigan compilando (D27) | Obligatorio: habría que tocarlos sin "sí" |

## Puntos a confirmar con el equipo

Resumen de D31 de [research.md](./research.md):

1. ~~**G3**: el "sí" para la aserción de `AccountControllerIT` (FR-057)~~: **dado por Lucas y Valentin el 2026-10-28**.
2. La **reserva de un remanente que ya no se puede reservar completa** queda con lo disponible, sin cancelar (D11).
3. El **portfolio del superusuario** sin datos de costo ni ganancia realizada (D7 y D19).
4. El **plan B del punto caliente del superusuario** (acreditación atómica) si la medición lo pide (D13).
