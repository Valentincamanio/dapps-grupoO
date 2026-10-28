---

description: "Lista de tareas de la feature 008: mercado de tokens y portfolio de usuarios"
---

# Tareas: Mercado de tokens y portfolio de usuarios

**Entrada**: documentos de diseño de `/specs/008-mercado-tokens-portfolio/`

**Prerrequisitos**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md),
[data-model.md](./data-model.md), [contracts/market-api.yaml](./contracts/market-api.yaml),
[quickstart.md](./quickstart.md)

**Tests**: SÍ. La constitución (Principio IV, NO NEGOCIABLE) exige unitarios de dominio sin Spring, de
servicio con Mockito, de integración contra H2 con `@ActiveProfiles("test")` y end to end con MockMvc en
`e2e/`; en el frontend, Vitest + RTL + MSW en `frontend/tests/`. **Sin TDD**: cada test se escribe junto
con (después de) la implementación de su tarea, y al cerrar la tarea el test existe y pasa. Convenciones:
casos felices y de borde, AssertJ, setup / execute / verify en tres bloques, nombres descriptivos en
español (`rechazaLaVentaMientrasLaOrdenInicialTieneTokens`, `it("muestra ...")`).

**Organización**: por historia de usuario (US1 a US9, en el orden de prioridad del spec), para que cada
una se implemente y se pruebe por separado. El orden de dependencias del plan ("Orden de implementación
sugerido") se respeta dentro de cada historia.

## Formato: `[ID] [P?] [Historia] Descripción`

- **[P]**: se puede hacer en paralelo (archivos distintos, sin dependencias pendientes)
- **[US#]**: historia de usuario a la que pertenece la tarea
- Cada descripción lleva la ruta exacta del archivo

## Convenciones de rutas

- Backend, producción: `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/`
- Backend, tests: `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/` (replica capa y contexto; los
  end to end van en `e2e/`; los de `shared/` y `config/` en su paquete transversal)
- Recursos: `backend/src/main/resources/` y `backend/src/test/resources/`
- Frontend: `frontend/src/<tipo>/` y `frontend/tests/<tipo>/` (sin carpetas nuevas)
- Contexto nuevo `market` en `modelo/`, `service/`, `controller/` y las cuatro carpetas de `persistence/`
  (declarado en el plan, Principio I). Toda clase de `modelo/` usa solo el JDK y clases de modelo (ni
  Spring ni JPA). Mensajes de error en español; identificadores en inglés.

## Reglas que valen para todas las tareas

- **No se modifica ni se borra ningún test existente**, salvo exactamente los cambios de la tabla "Tests
  existentes que cambian" del plan (D28 de `research.md`), y cada uno **solo en la tarea que lo hace
  necesario**:
  - `frontend/tests/pages/NoPurchaseActions.test.tsx` (G2): en T071, la primera tarea que suma un enlace
    de usuario a la barra ("Portfolio"). Si US6 se hace antes que la parte web de US2, el cambio va en
    T110 ("Operaciones" y "Mis órdenes" también rompen esa expresión).
  - `frontend/tests/pages/NoMarketActions.test.tsx` (G2): en T076, la tarea que muestra las órdenes
    pendientes (con sus tokens) en la ficha.
  - `backend/.../e2e/AccountControllerIT.java` (G3): en T066, con los saldos del perfil.
  - `backend/.../e2e/QuoteControllerIT.java` (G2): en T123, con el score de la 007.
  - Apoyo que cambia (no son tests, solo se agrega): `frontend/tests/mocks/handlers.ts` y
    `frontend/tests/mocks/quotes.ts` en T035.
  Agregar métodos de test nuevos a un archivo de test **creado por esta feature** no cambia aserciones
  existentes y está permitido. A los tests de la 001 a la 007 no se les agregan casos.
- **Ninguna dependencia nueva** en `backend/build.gradle` ni en `frontend/package.json`.
- **No se reemplazan** los `application*.yml`: solo se agrega la clave `futbolmarket.market.initial-orders.automatic`
  y se completa la URL de H2 de `local` con `;LOCK_TIMEOUT=10000`.
- **No se cambian firmas que usan los tests existentes**: se conservan `AppUser.register` (5 argumentos),
  `AppUser.createAdmin`, `AppUser.reconstitute` (7 argumentos) y el constructor de cuatro argumentos de
  `AuthService`; lo nuevo entra como sobrecarga (D5 y D6). `SyncService` y `SyncCoordinator` no cambian (D8).
- Status codes: 400, 401, 403, 404 y 409, todos desde las bases de `shared/` y el advice único; el 403 de
  dominio sale de la base nueva `shared/ForbiddenException`. Sin `catch (Exception)`, sin números mágicos
  (escalas y topes en `MarketMath`), SLF4J, inyección por constructor.
- Sin SQL nativo: derived queries, JPQL y `@Lock`. Las consultas con `@Lock` son de una tabla y sin
  `join fetch`; las de lectura con `join fetch` son `readOnly`. Enums como `VARCHAR` traducidos por el
  mapper (no `@Enumerated`).
- **Se bloquea antes de leer** (D13): libro del jugador (fila de su orden inicial) primero, después las
  cuentas en orden ascendente de id, de a una; ninguna cuenta ni orden se carga antes de su bloqueo.
- Frontend: los componentes reciben datos y callbacks (sin `fetch` ni router); el cliente HTTP sigue
  siendo uno (`services/httpClient.ts`); todo lo clickeable es `button` o enlace con foco visible; usable
  desde 360 px (las tablas pasan a tarjetas); importes en créditos, sin `$`, `€`, `USD`, `EUR` ni estrellas;
  se conserva la prohibición de "sumar al plantel".

---

## Fase 1: Setup (puertas y línea de base)

**Propósito**: cumplir la puerta G1 del plan y partir de una base verde.

- [X] T001 Cumplir la puerta G1: traer `develop` (con `cf0a0dd`: exclusiones de Sonar y `ScheduleValidation`) a la rama `008-mercado-tokens-portfolio` con un merge y comprobar que `git merge-base --is-ancestor cf0a0dd HEAD` da verdadero. Al generar estas tareas (2026-10-28) `cf0a0dd` **todavía no estaba** en la rama. No se espera conflicto (toca `backend/build.gradle` y dos clases de `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/config/`). G2 y G3 ya figuran en Clarifications de `specs/008-mercado-tokens-portfolio/spec.md`
- [X] T002 Contrastar con el código los nombres que usa el diseño y anotar cualquier diferencia en `specs/008-mercado-tokens-portfolio/data-model.md` antes de seguir: `Quote.tokenPrice()`, `Quote.score()`, `Quote.validFrom()`, la estrategia y su versión en `Quote`; `QuoteDay.parse`, `requireNotFuture`, `requireOrdered`, `startOf` y `endOf`; `QuotePage<T>`; la normalización de `team` en `PlayerFilter` y `CatalogInvariantException`; `QuoteRepository.findCurrentOf`; `AppUserRepository.translate`; las bases `BadRequestException`, `NotFoundException` y `ConflictException` de `shared/`; `PlayerNotFoundException`; el bean `Clock` de `ApplicationConfig`; los apoyos de test `service/quote/QuoteDatabase` y `service/quote/MutableClock`; cómo configuran el administrador los IT existentes; en el frontend, `useMutation`, `useAsync`, `refreshProfile` de `context/SessionContext.tsx`, `request()` de `services/httpClient.ts`, `useBoardFilters`, `Pager`, `DateRangePicker`, `TeamSelect`, `VariationBadge` y `RequoteChoice`
- [X] T003 Correr la línea de base sin tocar nada: `cd backend && ./gradlew build` y `cd frontend && npm test && npm run lint && npm run build`. Todo verde; si algo ya falla, anotarlo y avisar antes de seguir
- [ ] T004 (No bloquea) Confirmar con el equipo la puerta G4 del plan: la reserva de un remanente que ya no se puede reservar completa queda con lo disponible, sin cancelar (D11); el portfolio del superusuario sin datos de costo ni ganancia realizada (D7 y D19); el plan B del punto caliente del superusuario si la medición lo pide (D13). Registrar las respuestas en D31 de `specs/008-mercado-tokens-portfolio/research.md`

---

## Fase 2: Fundacional (prerrequisitos bloqueantes)

**Propósito**: lo que usan varias historias: la base del 403, la configuración, todo el dominio
compartido del mercado (orden, tenencia, operación, auditoría, saldo de `AppUser`), la persistencia de las
cuatro tablas con las consultas del camino de bloqueo, el reloj del registro, las reglas de seguridad, el
soporte de tests y la base del frontend.

**⚠️ CRÍTICO**: ninguna historia de backend empieza hasta cerrar la parte de backend de esta fase; las
partes web de US2, US3, US5, US6 y US7 esperan solo la "Base del frontend" (T032 a T041), que puede
avanzar en paralelo con el backend.

### Base compartida y configuración

- [X] T005 [P] Crear `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/shared/ForbiddenException.java` (base abstracta sin tipos de Spring, como `ConflictException`) y sumar su handler en `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/shared/GlobalExceptionHandler.java` que responde 403 con `ApiError` y el mensaje de la excepción; escribir `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/shared/GlobalExceptionHandlerForbiddenTest.java` (una subclase de prueba produce 403 con el formato `ApiError` y el mensaje en español; las otras bases siguen dando su status)
- [X] T006 [P] Crear `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/config/MarketProperties.java` (`record` con `@ConfigurationProperties("futbolmarket.market")` y un `record InitialOrders(boolean automatic)` anidado), registrarlo en `@EnableConfigurationProperties` de `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/config/ApplicationConfig.java`, agregar `futbolmarket.market.initial-orders.automatic: true` en `backend/src/main/resources/application.yaml` y `false` en `backend/src/test/resources/application-test.yml` (solo agregar la clave), y escribir `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/config/MarketPropertiesTest.java` (enlaza la clave; en el perfil test vale `false`)
- [X] T007 [P] Completar la URL de H2 de `backend/src/main/resources/application-local.yml` con `;LOCK_TIMEOUT=10000` (D13), sin cambiar nada más del archivo

### Dominio compartido del mercado (`modelo/market/`, solo JDK)

- [X] T008 [P] Crear los enums de la sección 2.1 de `data-model.md` en `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/modelo/market/`: `OrderSide.java` (`BUY`, `SELL`), `OrderStatus.java` (`PENDING`, `PARTIAL`, `EXECUTED`, `CANCELLED`, con `isOpen()` verdadero para los dos primeros), `OwnerType.java` (`USER`, `ADMIN`), `CancelReason.java` (`USER_REQUEST`, `INSUFFICIENT_CREDITS`), `NotTradableReason.java` (`INACTIVE`, `WITHOUT_QUOTE`, `WITHOUT_INITIAL_ORDER`), `AuditEventType.java` (`ORDER_CREATED`, `TRADE_EXECUTED`, `ORDER_CANCELLED`) y `AuditActorType.java` (`USER`, `SUPERUSER`, `SYSTEM`)
- [X] T009 [P] Crear `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/modelo/market/MarketMath.java` (clase final sin estado) con `CREDIT_SCALE = 2`, `PRICE_SCALE = 4`, `PERCENT_SCALE = 2`, `TOKENS_PER_PLAYER = 100`, `MAX_ORDER_QUANTITY = 100`, `INITIAL_TOKEN_PRICE = 1.0000` y las funciones `total`, `maxAffordable` (`n = floor(disponible / precio)` y `n − 1` si el redondeo se pasa, tope `limit`), `averagePrice` y `percent` (vacío si el todo es 0), todo `BigDecimal` con `HALF_UP` y un solo redondeo (D4); escribir `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/modelo/market/MarketMathTest.java`: `total(3, 1.0333) = 3.10`, `total(10, 1.0345) = 10.35`, `maxAffordable(10.30, 1.1000, 10) = 9`, el borde del medio centavo, tope por `limit`, disponible menor que un token → 0, `averagePrice(15.15, 15) = 1.0100`, `percent(0.30, 10.00) = 3.00` y `percent` con todo 0 vacío
- [X] T010 [P] Crear las excepciones de dominio de la sección 4 de `data-model.md` en `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/modelo/market/exception/`: `InsufficientCreditsException`, `InsufficientTokensException`, `PlayerNotTradableException` (recibe `NotTradableReason`), `SaleBlockedByInitialOrderException` (recibe los tokens que le quedan al superusuario), `OppositeOrderPendingException`, `OrderNotCancellableException` y `MarketBusyException` (extienden `ConflictException`); `OrderNotFoundException` (`NotFoundException`); `OrderNotOwnedException` y `TradingNotAllowedException` (`ForbiddenException`); `MarketInvariantException` (`RuntimeException`, como `QuoteInvariantException`). Mensajes en español con los montos en formato es-AR (coma decimal), como los ejemplos de la tabla
- [X] T011 [P] Crear en la carpeta existente `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/modelo/user/exception/`: `UserNotFoundException.java` (`NotFoundException`, "No se encontró el usuario con id 999."), `AccountAccessDeniedException.java` (`ForbiddenException`, "No tiene permiso para ver la cuenta de otro usuario.") y `DateBeforeRegistrationException.java` (`BadRequestException`, "La fecha 2026-08-01 es anterior al registro del usuario (2026-08-17).")
- [X] T012 [P] Crear `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/modelo/market/OrderView.java`: `record OrderView(MarketOrder order, Player player)`, modelo de lectura (no se guarda, análogo a `TradeView`) que usan el portfolio del superusuario (US2), el listado (US5) y las órdenes propias (US6). Es una lectura que el plan no nombra: anotarla en la sección 2 de `specs/008-mercado-tokens-portfolio/data-model.md`
- [X] T013 Crear `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/modelo/market/MarketOrder.java` con los campos, derivados (`pending()`, `status()` según la tabla de D9, `ownerType()`, `isOpen()`), fábricas (`initialFor`, `buy`, `sell`) y métodos (`fill`, `consumeReservation`, `adjustReservation` con la regla de D11, `cancelRemainder`, `cancelBeyond`, `requireOwnedBy` que rechaza también la inicial) de la sección 2.3 de `data-model.md`, sin setters (depende de T008 a T010); escribir `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/modelo/market/MarketOrderTest.java`: cantidades fuera de 1..100 → `MarketInvariantException`; transiciones `PENDING → PARTIAL → EXECUTED`, `PENDING → CANCELLED`, `PARTIAL → CANCELLED` con parte ejecutada; `fill` mayor que el pendiente; cancelar ejecutada o cancelada → `OrderNotCancellableException` con cada mensaje; `requireOwnedBy` ajena e inicial → `OrderNotOwnedException`; `adjustReservation` con disponible suficiente y con el hueco de D11; `ownerType()` `ADMIN` solo en la inicial
- [X] T014 [P] Crear `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/modelo/market/CostBasis.java` (valor inmutable con `quantity`, `acquiredQuantity` y `acquiredCost`; `acquire`, `dispose`, `averagePrice`, `invested`) y `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/modelo/market/Disposal.java` (`record` con el `CostBasis` resultante y la ganancia realizada), según la sección 2.4 y D12; escribir `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/modelo/market/CostBasisTest.java`: `acquire(10, 10.00)` y `acquire(5, 5.15)` → promedio 1,0100 e invertido 15,15; `dispose(5, 5.25)` → realizada +0,20, quedan 10, promedio 1,0100, invertido 10,10; una tercera compra de 5 por 5,25 → promedio 1,0200 (no 1,0233); vender todo reinicia los acumuladores y la recompra arranca un promedio nuevo (US2.4); sin compras el promedio es 0; vender más de lo que tiene → `MarketInvariantException`
- [X] T015 Crear `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/modelo/market/Holding.java` (sección 2.5: `id`, `ownerId`, `playerId`, `CostBasis`, `committed`; `freeTokens`, `commit` que lanza `InsufficientTokensException` con los tokens libres, `releaseCommitted`, `buy`, `sell` que devuelve la realizada y baja `quantity` y `committed`, `Holding.empty`) (depende de T014); escribir `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/modelo/market/HoldingTest.java`: 10 tokens con 6 comprometidos → 4 libres y `commit(5)` rechazado con "tiene 4 tokens libres"; `sell` baja cantidad y comprometidos y devuelve la realizada; `0 ≤ committed ≤ quantity` siempre
- [X] T016 [P] Crear `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/modelo/market/Trade.java` (`record` inmutable de la sección 2.6, con `sideFor(userId)` y `counterpartyFor(userId)`), `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/modelo/market/PartyState.java` y `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/modelo/market/PartyChange.java` (sección 2.7); escribir `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/modelo/market/TradeTest.java`: `sideFor` devuelve `BUY` al comprador y `SELL` al vendedor; `counterpartyFor` devuelve `ADMIN` al comprador de una venta del superusuario y `USER` en las operaciones entre usuarios, sin exponer ids
- [X] T017 Crear `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/modelo/market/AuditEntry.java` (`record` inmutable de la sección 2.8 con las fábricas `orderCreated`, `tradeExecuted` y `orderCancelled`, que arman el `detail` en español de hasta 500 caracteres; `actorId` nulo solo con `SYSTEM`) (depende de T013 y T016); escribir `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/modelo/market/AuditEntryTest.java`: cada fábrica completa los campos que le corresponden y deja nulos los demás; `tradeExecuted` lleva comprador como primaria y vendedor como secundaria; el detalle de una compra contra la inicial dice "Compra de 10 tokens de <jugador> a 1,0000 por 10,00 contra la orden inicial"; una cancelación del sistema no tiene `actorId`
- [X] T018 Cambiar `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/modelo/user/AppUser.java` (sección 3.1, D5 y D6): campos `reserved` (2 decimales, ≥ 0) y `registeredAt` (nulo en cuentas anteriores); métodos `availableBalance`, `reserve` (`InsufficientCreditsException` con lo necesario y el libre), `release`, `payFromReserve`, `receive`, `requireCanTrade` (`TradingNotAllowedException` si el rol no es `USER`), `requireCanAccessAccountOf` (`AccountAccessDeniedException` si no es el dueño ni `ADMIN`) y `requireRegisteredBy(LocalDate)` (día de registro en hora de Argentina; sin `registeredAt` no se aplica); sobrecargas `register(..., registeredAt)`, `createAdmin(..., registeredAt)` y `reconstitute` de 9 argumentos, conservando las actuales, que delegan con `reserved = 0` y `registeredAt = null`; escribir `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/modelo/user/AppUserMarketTest.java` (sin tocar `AppUserTest`): 1000,00 y reserva de 10,30 → libre 989,70; pagar 4,20 con la reserva y ajustar a 6,18 → total 995,80, reservado 6,18, libre 989,62 (US4.3 y US4.4); reservar más que el libre → 409 sin cambiar nada; liberar o pagar más que lo reservado → `MarketInvariantException`; el administrador no opera; dueño y `ADMIN` acceden a la cuenta, otro usuario no; registro el 17/08 a las 23:30 hora de Argentina admite el 17/08 y rechaza el 16/08; sin `registeredAt` admite cualquier fecha; las fábricas viejas dejan reservado 0

### Persistencia compartida (camino de bloqueo y escritura)

- [X] T019 Cambiar `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/persistence/sql/entity/user/AppUserSQL.java` (`reserved_balance NUMERIC(19,2)`, `nullable = false`, `@ColumnDefault("0.00")`; `registered_at TIMESTAMP` nullable y `updatable = false`) y `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/persistence/mapper/user/AppUserMapper.java` (los dos campos en los dos sentidos, con la `reconstitute` nueva); sumar en `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/persistence/sql/interfaces/user/AppUserSQLDAO.java` `findWithLockById` (`@Lock(PESSIMISTIC_WRITE)`) y `findFirstByRoleOrderByIdAsc`, y en `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/persistence/repository/user/AppUserRepository.java` `lockById(id)`, `lockAllInOrder(ids)` (ordena ascendente y bloquea de a una) y `findSuperuser()` (primera cuenta `ADMIN`, D7), traduciendo `PessimisticLockingFailureException` a `MarketBusyException` con el patrón de `translate`. Ninguna firma existente cambia (`AppUserRepositoryIT` sigue igual) (depende de T018)
- [X] T020 [P] Crear `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/persistence/sql/entity/market/MarketOrderSQL.java` (tabla `market_orders`, columnas e índices de la sección 6.1 con nombre, `@ManyToOne(fetch = LAZY)` a `AppUserSQL` y `PlayerSQL` no actualizables, enums como `VARCHAR`), `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/persistence/mapper/market/MarketOrderMapper.java` (enum ↔ texto, inicial ↔ `initial_player_id`, copia del estado derivado), `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/persistence/sql/interfaces/market/MarketOrderSQLDAO.java` con `findByInitialPlayerId` (`@Lock(PESSIMISTIC_WRITE)`), `findOpenOfPlayer` (JPQL: no iniciales, `PENDING` o `PARTIAL`, `order by createdAt, id`), `findPlayerIdsWithoutInitialOrder` y `findPlayerIdsWithoutInitialOrderIn(ids)` (JPQL con `not exists`), y `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/persistence/repository/market/MarketOrderRepository.java` (`save`, `saveAll`, `lockBook(playerId)` → `Optional<MarketOrder>`, `findOpenOfPlayer`, `playerIdsWithoutInitialOrder()`, `playerIdsWithoutInitialOrderIn(ids)`, `findById`; recibe y devuelve modelo; traduce `PessimisticLockingFailureException` a `MarketBusyException`) (depende de T013)
- [X] T021 [P] Crear `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/persistence/sql/entity/market/HoldingSQL.java` (tabla `holdings`, sección 6.2, índice único `ux_holdings_owner_player`), `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/persistence/mapper/market/HoldingMapper.java` (incluye el `CostBasis`), `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/persistence/sql/interfaces/market/HoldingSQLDAO.java` con `findByPlayerIdAndOwnerIdIn` y `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/persistence/repository/market/HoldingRepository.java` (`findOfPlayer(playerId, ownerIds)` y `saveAll`) (depende de T015)
- [X] T022 [P] Crear `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/persistence/sql/entity/market/TradeSQL.java` (tabla `market_trades`, sección 6.3, `@Immutable`, sin setters, todas las columnas `updatable = false`, FK a `players`, `market_orders`, `app_user` y `quotes`, índices `ix_market_trades_buyer_executed` y `ix_market_trades_seller_executed`), `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/persistence/mapper/market/TradeMapper.java`, `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/persistence/sql/interfaces/market/TradeSQLDAO.java` y `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/persistence/repository/market/TradeRepository.java` con solo `saveAll` (devuelve las operaciones con id) y lecturas: ningún método de actualizar ni de borrar (depende de T016)
- [X] T023 [P] Crear `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/persistence/sql/entity/market/AuditPartySQL.java` (`@Embeddable` con usuario, libre, reservado y tokens antes y después) y `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/persistence/sql/entity/market/AuditEntrySQL.java` (tabla `audit_entries`, sección 6.4, `@Immutable`, sin setters, `updatable = false`, dos `@Embedded` con `@AttributeOverrides` `primary_*` y `secondary_*`, ids sin FK, índices con nombre), `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/persistence/mapper/market/AuditEntryMapper.java`, `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/persistence/sql/interfaces/market/AuditEntrySQLDAO.java` y `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/persistence/repository/market/AuditRepository.java` con solo `saveAll` y lecturas (depende de T017)
- [X] T024 [P] Sumar `findInForceAt(playerId, instant)` (JPQL, la de mayor `validFrom` con `validFrom <= :instant`, D14) en `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/persistence/sql/interfaces/quote/QuoteSQLDAO.java` y `Optional<Quote> findInForceAt(long playerId, Instant instant)` (`@Transactional(readOnly = true)`) en `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/persistence/repository/quote/QuoteRepository.java`, sin tocar firmas existentes
- [X] T025 [P] Escribir `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/persistence/repository/market/MarketOrderRepositoryIT.java` (`@SpringBootTest`, `@ActiveProfiles("test")`): ida y vuelta de todos los campos (enums, reserva, inicial); una segunda orden inicial del mismo jugador viola `ux_market_orders_initial_player`; varias órdenes no iniciales (nulo) conviven; `findOpenOfPlayer` trae solo pendientes y parciales no iniciales en orden `createdAt, id`; `playerIdsWithoutInitialOrder` e `...In` (depende de T020)
- [X] T026 [P] Escribir `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/persistence/repository/market/HoldingRepositoryIT.java`: ida y vuelta con `CostBasis` y comprometidos; una segunda tenencia del mismo titular y jugador viola `ux_holdings_owner_player`; `findOfPlayer` trae solo las de esos titulares (depende de T021)
- [X] T027 [P] Escribir `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/persistence/repository/market/TradeRepositoryIT.java`: ida y vuelta con estados antes y después y realizada nula del superusuario; `TradeRepository` no expone métodos de actualizar ni de borrar y `TradeSQL` no tiene setters (por reflexión, SC-011) (depende de T022)
- [X] T028 [P] Escribir `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/persistence/repository/market/AuditRepositoryIT.java`: ida y vuelta de los tres tipos de evento con una y dos partes; `AuditRepository` sin métodos de actualizar ni de borrar y `AuditEntrySQL` sin setters (FR-051) (depende de T023)
- [X] T029 [P] Escribir `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/persistence/repository/quote/QuoteRepositoryMarketIT.java`: `findInForceAt` devuelve la última con vigencia anterior o igual al instante, ignora una con vigencia futura, devuelve la de un jugador inactivo y vacío sin cotizaciones (depende de T024)

### Registro con reloj (D6)

- [X] T030 Cambiar `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/service/auth/AuthService.java` (constructor nuevo con `Clock`, el que usa Spring con `@Autowired`; se conserva el de cuatro argumentos, que delega con `Clock.systemUTC()`; `register` pasa `clock.instant()` a la sobrecarga nueva de `AppUser.register`) y `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/service/user/AdminAccountInitializer.java` (suma el `Clock` a sus campos `private final` y llama a `AppUser.createAdmin(..., clock.instant())`). Verificar que `AuthServiceTest`, `AuthControllerIT` y `AccountServiceTest` pasan sin cambios (depende de T018 y T019)

### Seguridad (D24)

- [X] T031 Cambiar `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/security/SecurityConfig.java`: `GET /orders` (exacto, no `/orders/**`) en `publicEndpoints()`; `POST /orders/**` con `hasRole(USER)` (el administrador recibe 403 antes del controller y un visitante 401); `/audit/**` con `hasRole(ADMIN)`; `/users/**` sigue en `anyRequest().authenticated()`. Verificar que `AccessControlIT` pasa sin cambios

### Soporte de tests del mercado

- [X] T032 [P] Crear `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/modelo/market/MarketFixtures.java` (apoyo, no es test): jugador activo o inactivo, cotización con valor, score y vigencia, `AppUser` `USER` o `ADMIN` con saldo y reservado, orden inicial con N pendientes, compras y ventas con `createdAt` e id, tenencias con `CostBasis`, instantes en hora de Argentina
- [X] T033 [P] Crear `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/service/market/MarketDatabase.java` (apoyo, no es test): limpia `audit_entries`, `market_trades`, `holdings` y `market_orders` en ese orden y después delega en `service/quote/QuoteDatabase`; verificaciones de invariantes reutilizables para todos los IT del mercado: pendiente de la inicial + Σ tenencias = 100 por jugador (FR-026), Σ `balance` = Σ saldos iniciales (SC-003), `reserved` = Σ `reservedCredits` de las compras abiertas de cada usuario (D5), `committed` = Σ pendientes de sus ventas abiertas, ningún saldo ni tenencia negativo. Documentar en su comentario la configuración estándar de los IT del mercado: base propia por clase con `spring.datasource.url=jdbc:h2:mem:<clase>;LOCK_TIMEOUT=10000` en `@TestPropertySource`, `MutableClock` y las órdenes iniciales creadas llamando directo a `InitialOrderService.openMissing()` (la generación automática queda apagada salvo en `InitialOrderIT`, para no competir con el `@Async` del arranque)

### Base del frontend (bloquea solo las partes web)

- [ ] T034 [P] Crear `frontend/src/types/market.ts` a mano desde `specs/008-mercado-tokens-portfolio/contracts/market-api.yaml` y la sección 10 de `data-model.md` (`ORDER_SIDES`, `ORDER_STATUSES`, `OWNER_TYPES`, `PlayerRef`, `MarketOrder`, `UserOrder`, `QuoteUsed`, `TradeSummary`, `OrderExecution`, `OrderCancellation`, `PortfolioPosition`, `PortfolioTotals`, `Portfolio`, `Transaction`, páginas con `PageInfo` de `types/quotes.ts`, y los tipos de interfaz `MarketFilters`, `TransactionFilters`, `TradeEstimate` y `TradeBlock`); sumar `availableBalance` y `reservedBalance` a `Profile` en `frontend/src/types/api.ts`; sumar `score?: number` (opcional a propósito, D27) a `Quote` en `frontend/src/types/quotes.ts` y actualizar su comentario de encabezado
- [ ] T035 Crear los mocks de MSW: `frontend/tests/mocks/market.ts` (datos: órdenes iniciales con pendiente, órdenes de usuarios en los cuatro estados, un portfolio actual y uno a una fecha, operaciones con contraparte `ADMIN` y `USER`, respuestas de ejecución y cancelación, rechazos 409 con los mensajes del contrato) y `frontend/tests/mocks/marketHandlers.ts` (`GET /api/orders` con filtros y hojas, `POST /api/orders/buy`, `/sell` y `/:id/cancel`, `GET /api/users/:id/portfolio`, `/transactions` y `/orders`, con el prefijo que usan los handlers existentes); registrar `marketHandlers` en `frontend/tests/mocks/handlers.ts` y sumar `availableBalance` y `reservedBalance` al perfil simulado; en `frontend/tests/mocks/quotes.ts` sumar `score` a las cotizaciones simuladas y los dos saldos al perfil del administrador. Cambios solo aditivos (sin los handlers, `onUnhandledRequest: 'error'` hace fallar la ficha) (depende de T034)
- [ ] T036 [P] Crear `frontend/src/utils/market.ts`: etiquetas en español (compra, venta; pendiente, parcial, ejecutada, cancelada; administrador, usuario; contraparte "administrador" u "otro usuario"), estimado de compra (`min(cantidad, pendiente de la inicial) × 1,0000 + resto × precio vigente`) y de venta, con el total redondeado a 2 decimales hacia arriba desde la mitad trabajando en enteros (sin errores de coma flotante), formatos con signo (`+0,30`, `−0,30`, `+3,00 %`), créditos sin `$` y `formatScore` para el score de las cotizaciones; escribir `frontend/tests/utils/market.test.ts` con los ejemplos del spec (`3 × 1,0333 = 3,10`, `10 × 1,0345 = 10,35`, compra de 10 con 3 de la inicial a 1,0000 y 7 a 1,0300) y los bordes (cantidad 0, sin inicial, signo de 0) (depende de T034)
- [ ] T037 [P] Crear `frontend/src/services/marketService.ts` (`getMarketOrders(filters)`, `buyTokens`, `sellTokens`, `cancelOrder`, `getUserOrders(userId, page)`) y `frontend/src/services/portfolioService.ts` (`getPortfolio(userId, date?)`, `getTransactions(userId, filters)`) sobre `request()` de `frontend/src/services/httpClient.ts`, con los parámetros del contrato y sin los filtros vacíos (depende de T034)
- [ ] T038 [P] Cambiar `frontend/src/router/RequireRole.tsx`: prop `role` con `ADMIN` por defecto (sus usos actuales no cambian); un visitante va a `/login` y otro rol ve el aviso de falta de permiso existente. Verificar que `frontend/tests/pages/AdminAccess.test.tsx` pasa sin cambios
- [ ] T039 [P] Crear `frontend/src/components/ConfirmDialog.tsx` y `ConfirmDialog.module.css` (`role="dialog"` con `aria-labelledby`, foco inicial en confirmar, Enter confirma, Escape cancela, nada se ejecuta hasta confirmar; el patrón de `RequoteChoice`, sin `<dialog>` nativo); escribir `frontend/tests/components/ConfirmDialog.test.tsx`
- [ ] T040 [P] Crear `frontend/src/components/OrderList.tsx` y `OrderList.module.css` (props: órdenes, variante `market` u `own` y un `onCancel` opcional; muestra jugador, tipo, tipo de usuario dueño, cantidades pedida, ejecutada y pendiente, estado y fecha; la variante `own` suma cancelada y reserva y un botón de cancelar solo en las abiertas; nunca la identidad del dueño; tabla que pasa a tarjetas a 360 px; mensaje de lista vacía); escribir `frontend/tests/components/OrderList.test.tsx` (depende de T036)
- [ ] T041 [P] Crear `frontend/src/components/ProfitBadge.tsx` y `ProfitBadge.module.css` (ganancia o pérdida con signo, texto "gana", "pierde" o "sin cambios" y triángulo, como `VariationBadge`; sin valor muestra un guion; FR-070: no solo color); escribir `frontend/tests/components/ProfitBadge.test.tsx` (depende de T036)
- [ ] T042 Crear `frontend/src/hooks/useMarketOrders.ts` (listado con filtros y hojas; con `playerId`, las pendientes de un jugador para la ficha; carga, error con reintentar) y `frontend/src/hooks/useOrderCommands.ts` (comprar, vender y cancelar sobre `useMutation`; después de cada éxito llama a `refreshProfile` para el saldo de la barra; expone el resultado y el mensaje del `ApiError`); escribir `frontend/tests/hooks/useMarketOrders.test.tsx` y `frontend/tests/hooks/useOrderCommands.test.tsx` (depende de T035 y T037)

**Checkpoint**: `./gradlew build` y `npm test` verdes; las cuatro tablas y las dos columnas de `app_user` se
crean; el dominio compartido tiene sus tests; el frontend tiene tipos, servicios, mocks y piezas comunes.

---

## Fase 3: Historia 1 - Comprar tokens al superusuario (Prioridad: P1) 🎯 MVP

**Objetivo**: al arrancar y después de cada sincronización, cada jugador del catálogo tiene su orden del
superusuario por 100 tokens a 1 crédito; un usuario compra con `POST /orders/buy` y se ejecuta en el acto
contra esa orden, con el remanente pendiente y reservado si la orden no alcanza.

**Prueba independiente**: con un catálogo con cotizaciones de prueba, generar las órdenes iniciales,
verificar una por jugador y, como usuario, comprar tokens de varios jugadores verificando saldos,
tenencias, el remanente de cada orden inicial, la auditoría y los rechazos (quickstart, escenarios A, B.1
y F).

### Implementación de la Historia 1

- [ ] T043 [P] [US1] Crear `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/modelo/market/OrderMatcher.java`, `MatchPlan.java` y `Fill.java` en `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/modelo/market/` con el lado de compra de D10: primero la orden inicial a `INITIAL_TOKEN_PRICE` hasta agotarla; después las ventas pendientes de usuarios ordenadas por `createdAt` y `id` (sin importar el orden de entrada), al precio vigente, salteando las del mismo dueño; el resto es el remanente; cada `Fill` con su total de `MarketMath.total`. El lado de venta lo suma T078
- [ ] T044 [US1] Crear `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/modelo/market/PlayerBook.java` y `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/modelo/market/BookOutcome.java` (sección 2.10; `BookOutcome` también expone el `Player`, la `Quote` usada y la cuenta del pedido, para armar las respuestas): `isTradable()` (D15) y `placeBuy(ownerId, quantity, at)` con las validaciones en orden (`requireCanTrade`; operable, si no `PlayerNotTradableException` con el motivo; sin ventas propias pendientes, si no `OppositeOrderPendingException`; lo necesario = Σ totales de los `Fill` + `total(remanente, precio vigente)`, y `reserve` lanza `InsufficientCreditsException` antes de cambiar nada). Aplicación (D10): crea la compra con toda la reserva y `reservationPrice`; cada `Fill` se paga desde la reserva (`consumeReservation` y `payFromReserve`), el vendedor cobra (`receive`; el superusuario contra la inicial, que hace `fill`; un usuario con su venta y su tenencia), los tokens pasan a la tenencia del comprador (`Holding.empty` si no tenía); cada `Trade` registra saldos totales y tokens antes y después (los del superusuario son el pendiente de su inicial) y la cotización vigente también contra la inicial (FR-028); auditoría `ORDER_CREATED` (autor el usuario) y un `TRADE_EXECUTED` por operación, en ese orden (depende de T043)
- [ ] T045 [US1] Crear `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/service/market/PlayerBookLoader.java` (D13), con `load(playerId, requesterId, side, at)`: 1) `MarketOrderRepository.lockBook(playerId)`; sin orden inicial arma un libro no operable; 2) junta las cuentas (el que pide; el dueño de la inicial si el pedido es compra y la inicial tiene pendiente; los dueños de todas las órdenes abiertas del otro lado) y las bloquea con `AppUserRepository.lockAllInOrder`; 3) recién entonces lee las órdenes abiertas del jugador, sus tenencias de esos titulares, el `Player` y la cotización con `QuoteRepository.findInForceAt(playerId, at)`; arma el `PlayerBook`. Sin `if` de negocio
- [ ] T046 [US1] Crear `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/service/market/MarketOrderService.java` con `buy(requesterId, playerId, quantity)` en un único método `@Transactional` (D13): `at = clock.instant()`; jugador inexistente → `PlayerNotFoundException` antes de bloquear nada; `PlayerBookLoader.load`; `placeBuy`; guarda el resultado en el orden órdenes → cuentas y tenencias → operaciones → auditoría, porque cada paso necesita los ids del anterior (cómo pasa `BookOutcome` los ids de las órdenes guardadas a `Trade` y `AuditEntry` sin romper su inmutabilidad queda en el modelo; anotar la forma elegida en la sección 2.10 de `data-model.md`). Si el modelo rechaza, no se guarda nada (depende de T044 y T045)
- [ ] T047 [US1] Crear `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/service/market/InitialOrderService.java` (D8): `openMissing()` y `openFor(Collection<Long> playerIds)` directos e idempotentes: busca el superusuario (`findSuperuser`; si no existe, advierte en el registro el motivo de FR-012 y no crea nada), **bloquea su cuenta** con `lockById` antes de buscar los jugadores sin orden inicial, crea cada `MarketOrder.initialFor` con su `AuditEntry.orderCreated` (autor `SUPERUSER`, tenencia del superusuario de 0 a 100) y registra cuántas creó; y las entradas automáticas `onStartup()` y `afterSync(playerIds)`, que delegan solo si `MarketProperties.initialOrders().automatic()` es verdadero
- [ ] T048 [US1] Crear `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/service/market/InitialOrderStartup.java` (`@Async` y `@EventListener(ApplicationReadyEvent.class)` → `initialOrderService.onStartup()`, con el patrón de `QuoteStartupRecalculation`) y cambiar `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/service/sync/SyncWriteService.java` para que `applyLeague` llame a `initialOrderService.afterSync(ids de los jugadores guardados)` antes de devolver, en la misma transacción de la liga (inyección por constructor). Verificar que `SyncServiceTest`, `SyncServiceLastFullSyncTest`, `SyncCoordinatorTest`, `SyncWriteServiceIT`, `SyncControllerIT` y `StatsSyncControllerIT` pasan sin cambios (depende de T047)
- [ ] T049 [US1] Crear en `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/controller/market/dto/` los DTO de la compra con las formas del contrato: `OrderRequest.java` (`playerId` obligatorio y ≥ 1; `quantity` `BigDecimal` obligatoria con `@Digits(integer = 3, fraction = 0)`, `@DecimalMin("1")` y `@DecimalMax("100")`, D23; mensajes en español; los campos desconocidos como `userId` se ignoran), `PlayerRefResponse.java`, `QuoteUsedResponse.java` (`validFrom`, `value`, `tokenPrice`, `score`, `strategy` con `StrategyRefResponse` de la 007), `TradeSummaryResponse.java`, `UserOrderResponse.java` y `OrderExecutionResponse.java` (`order`, `trades`, `executedQuantity`, `executedTotal`, `balance`, `availableBalance`, `reservedBalance`), con fábricas desde `BookOutcome` (depende de T044; en paralelo con T045)
- [ ] T050 [US1] Crear `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/controller/market/OrderController.java` con `POST /orders/buy`: usuario de `@AuthenticationPrincipal Long` (nunca del cuerpo), `@Valid @RequestBody OrderRequest`, `quantity.intValueExact()` antes de delegar, 201 con `OrderExecutionResponse`; `@Tag(name = "Órdenes")`, `@Operation` y `@ApiResponse` 201, 400, 401, 403, 404 y 409 con el esquema `ApiError` y los ejemplos del contrato (depende de T046 y T049)

### Tests de la Historia 1

- [ ] T051 [P] [US1] Escribir `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/modelo/market/OrderMatcherTest.java` (lado de compra): compra de 10 contra la inicial con 100 → un `Fill` de 10 a 1,0000 por 10,00; inicial con 3 y compra de 10 → `Fill` de 3 y remanente 7; inicial agotada y una venta de 5 → `Fill` de 5 al precio vigente y remanente 5; la inicial antes que las ventas; dos ventas, la de B antes que la de D, recibidas desordenadas → primero B; empate de `createdAt` resuelto por id; las ventas del mismo dueño se saltean (depende de T043)
- [ ] T052 [P] [US1] Escribir `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/modelo/market/PlayerBookTest.java` (compra): US1.4 (1000,00, cotización 103,00, compra de 10 → 990,00, 10 tokens, el superusuario cobra 10,00, a la inicial le quedan 90, la operación a 1,0000 con la cotización vigente); US1.5 (inicial con 3 → 3 ejecutados, compra `PARTIAL` con 7 pendientes y reserva `total(7, 1,0300) = 7,21`, inicial `EXECUTED`); US1.6 (libre 5,00 y compra de 10 → `InsufficientCreditsException` sin auditoría ni cambios); administrador → `TradingNotAllowedException`; inactivo, sin cotización o sin inicial → `PlayerNotTradableException` con cada mensaje; venta propia pendiente → `OppositeOrderPendingException`; la auditoría sale en el orden de los hechos con estados anterior y posterior (depende de T044)
- [ ] T053 [P] [US1] Escribir `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/service/market/InitialOrderServiceTest.java` (Mockito): sin superusuario no crea nada y advierte; bloquea la cuenta del superusuario antes de consultar los jugadores sin orden (`InOrder`); sin jugadores pendientes no guarda nada (idempotencia); cada orden lleva su auditoría `SUPERUSER`; con la propiedad apagada `onStartup` y `afterSync` no tocan ningún repositorio y con la propiedad prendida delegan (depende de T047)
- [ ] T054 [P] [US1] Escribir `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/service/market/InitialOrderStartupTest.java`: el evento de arranque llama a `onStartup()` (depende de T048)
- [ ] T055 [P] [US1] Escribir `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/service/market/MarketOrderServiceTest.java` (compra, Mockito): jugador inexistente → 404 sin bloquear; el libro se bloquea antes que las cuentas y las cuentas en orden ascendente (`InOrder`); un rechazo del modelo no guarda nada en ningún repository; el orden de guardado es órdenes, cuentas y tenencias, operaciones y auditoría (depende de T046)
- [ ] T056 [US1] Escribir `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/service/market/InitialOrderIT.java` (`@SpringBootTest`, `@ActiveProfiles("test")`, base propia, administrador configurado y `futbolmarket.market.initial-orders.automatic=true` en `@TestPropertySource`): después del arranque cada jugador del catálogo tiene exactamente una orden inicial (venta, 100, `PENDING`, dueño el superusuario) con su `ORDER_CREATED` de autor `SUPERUSER`; llamar tres veces más a `openMissing` crea 0 (SC-001); `SyncWriteService.applyLeague` con un jugador nuevo le crea la suya y los demás no tienen una segunda; `openMissing` y `applyLeague` a la vez no duplican; sin la cuenta del administrador no se crea ninguna y queda la advertencia en el registro. Esperar al `@Async` del arranque con el mecanismo que usan los IT existentes de los procesos de arranque (depende de T048)
- [ ] T057 [US1] Escribir `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/service/market/MarketOrderServiceIT.java` (configuración de `MarketDatabase`, invariantes verificadas después de cada escenario): US1.4, US1.5, US1.6; US1.11 (cuatro usuarios compran 5 tokens de cada uno de cinco jugadores, todas a 1,0000, cada uno queda con 975,00 y a cada inicial le quedan 80); dos pedidos del mismo usuario sobre jugadores distintos que juntos superan su libre → el segundo 409 y ningún saldo negativo (depende de T046 y T033)
- [ ] T058 [US1] Escribir `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/service/market/MarketConcurrencyIT.java` (`ExecutorService` y `CountDownLatch`, solo se verifica el estado final): 20 usuarios piden a la vez 1 token contra una inicial con 10 pendientes → 10 compras ejecutadas, 10 pendientes con su reserva, inicial `EXECUTED`, sin estados intermedios y con las invariantes; un mismo usuario con dos pedidos simultáneos que juntos superan su libre → uno 409 y ningún saldo negativo; ningún pedido termina en 500 (depende de T046 y T033)
- [ ] T059 [US1] Escribir `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/e2e/OrderControllerIT.java` (compra, `MockMvcTester`): 201 con la forma del contrato (orden `EXECUTED`, operación con `counterparty: ADMIN`, `tokenPrice: 1.0000`, `total: 10.00`, cotización con `score`, saldos); 201 con remanente `PARTIAL`; 400 sin cuerpo, JSON inválido, `quantity` 0, −1, 1.5 y 101, sin `playerId` y `playerId` 0, con `violations`; 401 sin credencial; 403 con el administrador y nada cambia; 404 jugador inexistente; 409 saldo insuficiente; 409 jugador inactivo, sin cotización y sin orden inicial, con los mensajes en español; el `userId` del cuerpo se ignora; funciona con token y con `X-API-Key` (depende de T050)

**Checkpoint**: la US1 funciona sola: el mercado arranca con una orden inicial por jugador y se compra por
API contra el superusuario. MVP listo para un PR chico contra `develop`.

---

## Fase 4: Historia 2 - Ver el portfolio actual (Prioridad: P2)

**Objetivo**: `GET /users/{id}/portfolio` informa cada posición y los totales (FR-036 a FR-039), solo al
dueño o al administrador; el perfil suma el saldo libre y el reservado; la web tiene la pantalla
Portfolio.

**Prueba independiente**: con usuarios con tenencias (compras de US1 o sembradas) y cotizaciones conocidas,
verificar cada campo por jugador y los totales contra los ejemplos del spec y los rechazos de acceso; en la
web, recorrer `/portfolio` contra MSW (quickstart, escenario B.3).

### Implementación de la Historia 2

- [ ] T060 [P] [US2] Crear `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/modelo/market/HoldingView.java`: `record HoldingView(Holding holding, Player player)`, lectura (no se guarda) para el portfolio actual; anotarla en la sección 2 de `data-model.md` junto con `OrderView`
- [ ] T061 [US2] Crear `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/modelo/market/PortfolioPosition.java`, `PortfolioTotals.java` y `Portfolio.java` en `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/modelo/market/` (sección 2.11): `Portfolio.current(account, holdingViews, quotesByPlayer, realized)` y `Portfolio.currentOfSuperuser(account, initialOrderViews, quotesByPlayer)` (sin precio promedio, invertido, ganancia ni rentabilidad, D7); participación = tokens; valor = `total(tokens, tokenPrice)`; ganancia = valor − invertido; rentabilidad con `percent` (nula con invertido 0); totales con libre, reservado, saldo, invertido, valor, ganancia, realizada y patrimonio = saldo + valor; posiciones en orden estable por nombre del jugador y después id (depende de T060)
- [ ] T062 [P] [US2] Escribir `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/modelo/market/PortfolioTest.java`: 10 tokens a 1,0000 con cotización 103,00 → 10 %, 0 comprometidos, promedio 1,0000, invertido 10,00, precio 1,0300, valor 10,30, ganancia +0,30, rentabilidad +3,00 % y el score de la cotización (US2.1); libre 990,00, reservado 6,18 y posiciones por 20,60 → patrimonio 1016,78 (US2.5); sin tenencias → posiciones vacías y patrimonio = saldo (US2.7); 10 tokens con cotizaciones 100,00, 120,00 y 90,00 → valor 10,00, 12,00 y 9,00; el del superusuario sin datos de costo (depende de T061)
- [ ] T063 [US2] Sumar las lecturas del portfolio actual (todas `readOnly`): `findPositions(ownerId)` (JPQL con `join fetch` de jugador y equipo, `quantity > 0`) en `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/persistence/sql/interfaces/market/HoldingSQLDAO.java` y `findPositionsOf(ownerId)` → `List<HoldingView>` en `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/persistence/repository/market/HoldingRepository.java`; `sumRealizedProfitOfSeller(userId)` en `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/persistence/sql/interfaces/market/TradeSQLDAO.java` y `realizedProfitOf(userId)` (0 sin ventas) en `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/persistence/repository/market/TradeRepository.java`; `findInitialOrdersOf(ownerId)` (iniciales con pendiente > 0, con jugador y equipo) en `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/persistence/sql/interfaces/market/MarketOrderSQLDAO.java` y `findInitialOrdersOf` → `List<OrderView>` en `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/persistence/repository/market/MarketOrderRepository.java`; sumar sus casos como métodos nuevos en `HoldingRepositoryIT`, `TradeRepositoryIT` y `MarketOrderRepositoryIT` (depende de T060)
- [ ] T064 [US2] Crear `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/service/market/PortfolioService.java` con `current(requesterId, userId)` (`readOnly`, D19 y D24): carga al que pide y llama a `requireCanAccessAccountOf(userId)` **antes** de buscar la cuenta pedida (un usuario común recibe 403 tanto por una ajena como por una inexistente; el administrador recibe 404 con `UserNotFoundException`); tenencias con `findPositionsOf`; cotizaciones con **una** llamada a `QuoteRepository.findCurrentOf(ids)` (no `QuoteQueryService.getCurrentOf`, D19); realizada con `realizedProfitOf`; para el superusuario, `findInitialOrdersOf` y `Portfolio.currentOfSuperuser`; no consulta fuentes externas (depende de T061 y T063)
- [ ] T065 [P] [US2] Escribir `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/service/market/PortfolioServiceTest.java` (Mockito): el permiso se verifica antes de buscar la cuenta pedida (403 sin consultar la cuenta); el administrador recibe 404 por un usuario inexistente; una sola consulta de cotizaciones por pedido; el superusuario sale de sus órdenes iniciales (depende de T064)
- [ ] T066 [US2] Sumar `availableBalance` y `reservedBalance` (de `AppUser.availableBalance()` y del reservado) a `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/controller/user/dto/ProfileResponse.java` (FR-057) y, **en esta misma tarea y solo esto** (puerta G3, "sí" de Lucas y Valentin del 2026-10-28), sumar `"availableBalance"` y `"reservedBalance"` al `containsExactlyInAnyOrder` de `elPerfilTieneExactamenteLasClavesDelContratoYNingunSecreto` en `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/e2e/AccountControllerIT.java`, sin tocar ninguna otra aserción ni las verificaciones de que el perfil no trae contraseña, clave de API ni hash
- [ ] T067 [US2] Crear en `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/controller/market/dto/` `PortfolioResponse.java`, `PortfolioPositionResponse.java` (jugador con posición y `active`, `committed`, `quote` con `QuoteUsedResponse`) y `PortfolioTotalsResponse.java` (`availableBalance` y `reservedBalance` omitidos cuando son nulos), y `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/controller/market/PortfolioController.java` con `GET /users/{id}/portfolio` (actual; la fecha la suma T117), usuario de `@AuthenticationPrincipal Long`, `@Tag(name = "Portfolio")`, `@ApiResponse` 200, 401, 403 y 404 (depende de T064)

### Tests de la Historia 2

- [ ] T068 [US2] Escribir `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/e2e/PortfolioControllerIT.java` (sembrando tenencias, operaciones y reservas con los repositories cuando el caso necesita compras entre usuarios): US2.1; US2.2 (promedio 1,0100, invertido 15,15); US2.3 (quedan 10 a 1,0100, invertido 10,10, realizada +0,20); US2.4 (promedio reiniciado); US2.5 (patrimonio 1016,78); US2.6 (jugador inactivo con su última cotización); US2.7 (vacío); US2.8 (otro usuario 403, administrador 200); US2.9 (inexistente: administrador 404, usuario 403); 401 sin credencial; el administrador pide el portfolio del superusuario (sus tokens sin vender, sin costo); US1.11 y SC-010 por API (cuatro usuarios compran con `POST /orders/buy` cinco jugadores y cada uno ve sus cinco posiciones); US2.10 y SC-012 (con los clientes de WhoScored y Football-Data reemplazados por dobles que fallan, con el mecanismo que usan los IT de la 005 y la 006, responde igual) (depende de T050 y T067)

### Parte web de la Historia 2

- [ ] T069 [P] [US2] Crear `frontend/src/hooks/usePortfolio.ts` (portfolio actual del usuario de la sesión; carga, error con reintentar) y escribir `frontend/tests/hooks/usePortfolio.test.tsx` (depende de T037)
- [ ] T070 [P] [US2] Crear `frontend/src/components/PositionTable.tsx` y `PositionTable.module.css` (jugador, tokens, %, comprometidos, precio promedio, invertido, precio por token, score, valor, ganancia con `ProfitBadge` y rentabilidad; guion en los campos nulos; tarjetas a 360 px) y `frontend/src/components/PortfolioTotals.tsx` y `PortfolioTotals.module.css` (libre y reservado solo si vienen, saldo, invertido, valor, ganancia, realizada y patrimonio); escribir `frontend/tests/components/PositionTable.test.tsx` y `frontend/tests/components/PortfolioTotals.test.tsx` (depende de T041)
- [ ] T071 [US2] Crear `frontend/src/pages/PortfolioPage.tsx` y `PortfolioPage.module.css` (`/portfolio`, actual: `PositionTable` y `PortfolioTotals`, estados de carga, error con reintentar y vacío, mensajes en español); registrar la ruta en `frontend/src/router/routes.tsx` con `RequireRole role="USER"`; sumar el enlace "Portfolio" solo para `USER` en `frontend/src/pages/AppLayout.tsx`; y, **en esta misma tarea** (G2), en `frontend/tests/pages/NoPurchaseActions.test.tsx` cambiar `FORBIDDEN` a `/sumar|plantel/i` y redactar el comentario de la línea 1 y el `describe` como "ninguna pantalla ofrece sumar al plantel", sin tocar `PRICE_TEXT`, las estrellas ni ningún otro caso (depende de T038, T069 y T070)
- [ ] T072 [US2] Escribir `frontend/tests/pages/PortfolioPage.test.tsx`: posiciones y totales; ganancia o pérdida con signo **y** texto; lista vacía; error con reintentar (depende de T071)

**Checkpoint**: US1 y US2 funcionan por API; el perfil informa libre y reservado; `/portfolio` muestra el
actual.

---

## Fase 5: Historia 3 - Comprar y vender desde la aplicación web (Prioridad: P3)

**Objetivo**: la ficha muestra las órdenes pendientes del jugador a todos y, a un usuario común, el panel
de operar con confirmación, resultado real, avisos y rechazos en español; la barra muestra el saldo libre.

**Prueba independiente**: con la API simulada, recorrer la ficha sin sesión, como usuario y como
administrador, comprando y vendiendo con confirmación y verificando resultado, avisos, rechazos, carga y
error, con teclado (quickstart, escenario I).

### Implementación de la Historia 3

- [ ] T073 [P] [US3] Crear `frontend/src/components/TradePanel.tsx` y `TradePanel.module.css` (sin API ni router): cantidad entera de 1 a 100 con su etiqueta, botones "Comprar" y "Vender"; aviso de venta bloqueada mientras la inicial tiene pendiente, con cuántos tokens le quedan al administrador (deshabilita vender); jugador inactivo o sin cotización deshabilita los dos y explica por qué; abre `ConfirmDialog` con cantidad, precio por token y total estimado de `utils/market.ts`, avisando que el precio final es el del momento de la ejecución; muestra el resultado real (tokens ejecutados, precio, total y remanente pendiente) o el mensaje del rechazo sin dar la operación por hecha; usable a 360 px (depende de T036 y T039)
- [ ] T074 [P] [US3] Escribir `frontend/tests/components/TradePanel.test.tsx`: estimado con tokens de la inicial y al precio vigente; nada se confirma sin el diálogo; avisos de venta bloqueada y de no operable; resultado con remanente; rechazo mostrado en español; Enter confirma y Escape cancela (depende de T073)
- [ ] T075 [P] [US3] Cambiar `frontend/src/components/TopBar.tsx` para que la etiqueta diga "Saldo libre" y `frontend/src/pages/AppLayout.tsx` para pasarle `formatBalance(profile.availableBalance)` (FR-062). Verificar que `frontend/tests/pages/LockerRoomPage.test.tsx` pasa sin cambios (el saldo del vestuario sigue fuera de la barra)
- [ ] T076 [US3] Cambiar `frontend/src/pages/PlayerPage.tsx` y `PlayerPage.module.css`: siempre una sección de órdenes pendientes con `OrderList` (variante `market`) desde `useMarketOrders({ playerId })`, incluida la inicial con los tokens que le quedan (FR-064); `TradePanel` solo con sesión de rol `USER` (FR-065), conectado a `useOrderCommands` (comprar y vender) y recargando las pendientes después de cada éxito; y, **en esta misma tarea** (G2), en `frontend/tests/pages/NoMarketActions.test.tsx` cambiar `MARKET_ACTION` a `/sumar|plantel/i` e `INVESTMENT_DATA` a `/cartera|mis acciones/i` y redactar el comentario de las líneas 1 a 3 y el `describe` como "sin acciones de plantel", sin tocar ningún otro caso. Verificar que `PlayerPage.test.tsx` y `PostIt.test.tsx` pasan sin cambios (depende de T040, T042 y T073)

### Tests de la Historia 3

- [ ] T077 [US3] Escribir `frontend/tests/pages/PlayerTrade.test.tsx`: sin sesión y como administrador, sin controles de operar pero con las órdenes pendientes y la cotización; como usuario, comprar con confirmación (nada se envía antes de confirmar, verificado en el handler de MSW), el resultado real y el saldo de la barra actualizado; aviso de venta bloqueada con los tokens del administrador; jugador inactivo o sin cotización explica por qué; un 409 del servidor muestra el motivo en español y no muestra resultado; foco en el diálogo, Enter y Escape (depende de T075 y T076)

**Checkpoint**: un usuario compra y vende desde la ficha; visitantes y administrador ven las pendientes sin
controles.

---

## Fase 6: Historia 4 - Revender tokens entre usuarios (Prioridad: P4)

**Objetivo**: cuando la inicial de un jugador se agotó, los usuarios venden con `POST /orders/sell`; ventas
y compras pendientes se cruzan al precio vigente, con ejecución parcial, reservas, cancelación por saldo
insuficiente (FR-027, D11) e invariantes.

**Prueba independiente**: con un jugador con la inicial agotada y usuarios con tenencias y saldos
conocidos, crear órdenes de los dos lados en distinto orden y verificar cruces, prioridades, precios,
totales, remanentes, reservas e invariantes (quickstart, escenario C).

### Implementación de la Historia 4

- [ ] T078 [US4] Sumar el lado de venta a `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/modelo/market/OrderMatcher.java` y crear `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/modelo/market/Shortfall.java` (D10 y D11): cada compra pendiente llega con su disponible (su reserva + el libre de su dueño); de la más vieja a la más nueva se ejecuta `min(pendiente, lo que falta vender, maxAffordable(disponible, precio))`; si alcanza para menos que su pendiente y que lo que falta vender, el resto de esa compra va a `shortfalls` (entera si no alcanza para 1 token) y la venta sigue con la próxima; saltea las del mismo dueño; determinista
- [ ] T079 [US4] Sumar `placeSell(ownerId, quantity, at)` a `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/modelo/market/PlayerBook.java` con las validaciones en orden (`requireCanTrade`; operable; inicial con pendiente → `SaleBlockedByInitialOrderException` con los tokens que le quedan, sin crear nada; compras propias pendientes → `OppositeOrderPendingException`; tokens libres → `InsufficientTokensException`). Aplicación: crea la venta y compromete sus tokens (`ORDER_CREATED`); por cada `Fill`, si el total supera la reserva de la compra se reserva antes la diferencia desde el libre del comprador y se suma a la reserva de la orden (agregar a `MarketOrder.java` el método que haga falta, con su caso nuevo en `MarketOrderTest`), se paga desde la reserva, el vendedor cobra, `Holding.sell` registra la realizada y la operación la lleva; después `adjustReservation` deja la reserva del remanente en `min(pendiente × reservationPrice, disponible)` y mueve la diferencia en la cuenta (D11; si el precio bajó, vuelve al libre); cada `shortfall` cancela con `INSUFFICIENT_CREDITS`, libera la reserva y deja `ORDER_CANCELLED` con autor `SYSTEM`; el remanente de la venta queda pendiente con sus tokens comprometidos (depende de T078)
- [ ] T080 [US4] Sumar `sell(requesterId, playerId, quantity)` a `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/service/market/MarketOrderService.java`, en un único `@Transactional` con el mismo camino que `buy` (el `PlayerBookLoader` ya bloquea a los dueños de todas las compras abiertas, D13) (depende de T079)
- [ ] T081 [US4] Sumar `POST /orders/sell` (201 con `OrderExecutionResponse`, misma validación y documentación Swagger que la compra) a `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/controller/market/OrderController.java` (depende de T080)

### Tests de la Historia 4

- [ ] T082 [P] [US4] Sumar a `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/modelo/market/OrderMatcherTest.java` los casos de venta como métodos nuevos: venta de 8 contra una compra de 5 → `Fill` de 5 y remanente 3; dos compras, la de A antes que la de C → completa A y sigue con C; compra con disponible 10,30 a 1,1000 contra una venta de 10 → `Fill` de 9 y `shortfall` de 1; compra con disponible menor que un token → `shortfall` entero y la venta sigue con la próxima; las del mismo dueño se saltean; el resultado no depende del orden de entrada (depende de T078)
- [ ] T083 [P] [US4] Sumar a `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/modelo/market/PlayerBookTest.java` los casos de venta como métodos nuevos: US4.1 (inicial con 1 → rechazo sin crear orden); US4.2 (inicial agotada → la venta se crea); US4.3 (compra pendiente de 10 reserva 10,30, libre 989,70); US4.4 (venta de 4 a 1,0500 → 4,20, A con 6 pendientes, 6,18 reservados y 989,62 libres, B cobra 4,20); US4.5 a US4.7; US4.8 (10 × 1,0345 = 10,35, el comprador paga lo que recibe el vendedor); US4.9 (9 por 9,90, 1 cancelado, 0,40 liberados, `ORDER_CANCELLED` del sistema); US4.10 (4 libres); US4.11 en los dos sentidos; el hueco de D11 (10,30 reservados, libre 0, precio 1,1000 y venta de 4 → paga 4,40, remanente 6 con 5,90 reservados, libre 0, sin cancelar); compra que se ejecuta más barata que su reserva; venta que deja la tenencia en 0 y la recompra con promedio nuevo; el libro nunca queda con pendientes de los dos lados (depende de T079)
- [ ] T084 [P] [US4] Sumar a `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/service/market/MarketOrderServiceTest.java` los casos de venta como métodos nuevos: se bloquean el libro, el vendedor y los dueños de las compras abiertas en orden ascendente; un rechazo no guarda nada (depende de T080)
- [ ] T085 [US4] Sumar a `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/service/market/MarketOrderServiceIT.java` los escenarios de US4 como métodos nuevos, con `MutableClock` y la cotización que pasa de 103,00 a 105,00 e invariantes después de cada uno; US4.13 (una cotización con vigencia posterior al instante del pedido no se usa: rige la última completa) (depende de T080)
- [ ] T086 [US4] Sumar a `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/service/market/MarketConcurrencyIT.java` como métodos nuevos: US4.12 (dos usuarios piden a la vez los últimos 3 tokens de la inicial → uno ejecutado y el otro pendiente, suma 100); 50 pedidos mezclados de compra y venta sobre el mismo jugador → tenencias suman 100, ningún saldo ni tenencia negativo y Σ saldos sin cambios (SC-002 y SC-003); si algún pedido vence el candado responde 409 `MarketBusyException`, nunca 500 (depende de T080)
- [ ] T087 [US4] Sumar a `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/e2e/OrderControllerIT.java` los casos de venta como métodos nuevos: 201 con operación de contraparte `USER`; 409 venta bloqueada con los tokens que le quedan al administrador; 409 tokens insuficientes; 409 órdenes de sentido contrario en los dos sentidos; 403 del administrador; los 400 del cuerpo; 401 (depende de T081)

**Checkpoint**: el mercado secundario funciona por API con las invariantes verificadas bajo concurrencia.

---

## Fase 7: Historia 5 - Listado del mercado (Prioridad: P5)

**Objetivo**: `GET /orders` público con las órdenes con remanente pendiente, filtros combinables y la
paginación del catálogo, sin revelar al dueño; la pantalla "Mercado".

**Prueba independiente**: con órdenes en todos los estados de varios jugadores, ligas, equipos y tipos de
usuario, consultar sin credencial con cada filtro y combinaciones; recorrer `/mercado` contra MSW
(quickstart, escenarios D e I).

### Implementación de la Historia 5

- [ ] T088 [P] [US5] Crear `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/modelo/market/MarketOrderFilter.java` (`side`, `league`, `team`, `ownerType`, `playerId`; `team` normalizado como en `PlayerFilter`, vacío → `CatalogInvariantException`, 400)
- [ ] T089 [US5] Sumar `findMarket(...)` (JPQL con `join fetch` de jugador y equipo, solo `PENDING` y `PARTIAL`, filtros nulos que no filtran, `ownerType` `ADMIN` ↔ `initialPlayerId is not null`, `order by createdAt, id`) y su consulta de conteo en `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/persistence/sql/interfaces/market/MarketOrderSQLDAO.java`, y `findMarket(filter, page, size)` → `QuotePage<OrderView>` (`readOnly`) en `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/persistence/repository/market/MarketOrderRepository.java`; sumar como métodos nuevos en `MarketOrderRepositoryIT` cada filtro, combinaciones, la exclusión de ejecutadas y canceladas, el orden y las hojas (depende de T088)
- [ ] T090 [US5] Crear `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/service/market/MarketQueryService.java` con `market(filter, page, size)` (`readOnly`) y escribir `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/service/market/MarketQueryServiceTest.java` con sus casos (depende de T089)
- [ ] T091 [US5] Crear `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/controller/market/dto/MarketOrderResponse.java` (sin ningún dato del dueño) y `MarketOrderPageResponse.java` (con `PageInfo` del catálogo), y sumar `GET /orders` a `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/controller/market/OrderController.java` con `side`, `league`, `team`, `ownerType`, `playerId` (`@Min(1)`), `page` y `size` validados como en `PlayerController` (por defecto 10, de 1 a 50), un valor no admitido responde 400 "Los parámetros enviados no son válidos." como el catálogo, `@Tag(name = "Mercado")` en el método (depende de T090)

### Tests de la Historia 5

- [ ] T092 [US5] Escribir `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/e2e/MarketListingIT.java`: 200 sin credencial y con una credencial inválida; solo pendientes y parciales, de la más vieja a la más nueva, con cada campo de FR-049; cada filtro y combinaciones; `ownerType=ADMIN` muestra las iniciales con lo que les queda; ninguna respuesta trae `owner`, `username`, `email` ni el id del dueño (SC-013); 400 con `side=X`, `ownerType=X`, `league=X`, `playerId=0` y `team=`; `status=EXECUTED` se ignora; validación de `page` y `size` (depende de T091)

### Parte web de la Historia 5

- [ ] T093 [P] [US5] Crear `frontend/src/hooks/useMarketFilters.ts` (filtros `side`, `league`, `team`, `ownerType` y `page` en la dirección con los mismos nombres que la pizarra y el ranking, los vacíos no aparecen, la hoja vuelve a la primera al cambiar un filtro; como `useBoardFilters`) y escribir `frontend/tests/hooks/useMarketFilters.test.tsx`
- [ ] T094 [P] [US5] Crear `frontend/src/components/MarketFilters.tsx` y `MarketFilters.module.css` (tipo, liga, equipo y tipo de usuario, accesibles y usables a 360 px; reutilizar `TeamSelect` si sus props alcanzan, sin modificarlo) y escribir `frontend/tests/components/MarketFilters.test.tsx` (depende de T036)
- [ ] T095 [US5] Crear `frontend/src/pages/MarketPage.tsx` y `MarketPage.module.css` (`/mercado`, pública: `MarketFilters`, `OrderList` variante `market` y `Pager`, con carga, error con reintentar y vacío); registrar la ruta pública en `frontend/src/router/routes.tsx` y sumar el enlace "Mercado" para todos en `frontend/src/pages/AppLayout.tsx` (depende de T040, T042, T093 y T094)
- [ ] T096 [US5] Escribir `frontend/tests/pages/MarketPage.test.tsx`: listado sin sesión; filtros y hoja en la dirección; la hoja vuelve a la primera al cambiar un filtro; abrir una dirección con filtros reproduce la vista; nunca muestra al dueño; carga, error con reintentar y vacío (depende de T095)

**Checkpoint**: el listado es público por API y en `/mercado`.

---

## Fase 8: Historia 6 - Historial de operaciones y órdenes propias (Prioridad: P6)

**Objetivo**: `GET /users/{id}/transactions`, `GET /users/{id}/orders` y `POST /orders/{id}/cancel`, con el
permiso del dueño o del administrador; las pantallas Operaciones y Mis órdenes.

**Prueba independiente**: con un usuario con compras al superusuario, operaciones entre usuarios y órdenes
en todos los estados, verificar el contenido, los filtros, el orden, las hojas, la cancelación y los
rechazos (quickstart, escenario E).

### Implementación de la Historia 6

- [ ] T097 [P] [US6] Crear `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/modelo/market/TradeView.java` (lectura: la operación con el jugador y la cotización con su estrategia y versión) y `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/modelo/market/TradeFilter.java` (`playerId`, `side`, `from`, `to`; fechas con `QuoteDay.parse`, `QuoteDay.requireOrdered` si vienen las dos, extremos `startOf(from)` y `endOf(to)`)
- [ ] T098 [US6] Sumar `cancel(order, requesterId, at)` a `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/modelo/market/PlayerBook.java` (D16): `requireOwnedBy` (ajena o inicial → 403), `cancelRemainder(USER_REQUEST)` (cerrada → 409), una compra libera su reserva al libre y una venta sus tokens comprometidos, `ORDER_CANCELLED` con autor el usuario y estados anterior y posterior; se puede cancelar con el jugador no operable (FR-032); sumar sus casos como métodos nuevos en `PlayerBookTest` (US6.7 a US6.10 y jugador inactivo)
- [ ] T099 [US6] Sumar las lecturas (todas `readOnly` salvo la escalar del bloqueo): `findPlayerIdById` (JPQL escalar) y `findByOwnerId(ownerId, Pageable)` (con jugador, `order by createdAt desc, id desc`) con su conteo en `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/persistence/sql/interfaces/market/MarketOrderSQLDAO.java`, y `findPlayerIdOf(orderId)` y `findOfOwner(ownerId, page, size)` → `QuotePage<OrderView>` en `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/persistence/repository/market/MarketOrderRepository.java`; `findHistory(...)` (JPQL, comprador o vendedor = usuario, `side` desde su punto de vista, rango de fechas, `join fetch` de jugador, equipo, cotización y estrategia, `order by executedAt desc, id desc`) con su conteo en `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/persistence/sql/interfaces/market/TradeSQLDAO.java` y `findHistory(userId, filter, page, size)` → `QuotePage<TradeView>` en `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/persistence/repository/market/TradeRepository.java`; sumar sus casos como métodos nuevos en `MarketOrderRepositoryIT` y `TradeRepositoryIT` (depende de T097)
- [ ] T100 [US6] Sumar `loadForCancel(orderId, requesterId, at)` a `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/service/market/PlayerBookLoader.java`: lee el jugador con `findPlayerIdOf` (sin cargar la orden; si no existe, `OrderNotFoundException`), bloquea el libro, bloquea la cuenta del dueño y recién entonces lee la orden, la tenencia y el jugador; y `cancel(requesterId, orderId)` en un único `@Transactional` en `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/service/market/MarketOrderService.java`; sumar sus casos como métodos nuevos en `MarketOrderServiceTest` (404 antes de bloquear, orden de bloqueo, un rechazo no guarda nada) (depende de T098 y T099)
- [ ] T101 [US6] Sumar `transactions(requesterId, userId, filter, page, size)` y `ordersOf(requesterId, userId, page, size)` a `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/service/market/MarketQueryService.java`, con el permiso verificado antes de buscar la cuenta como en `PortfolioService` (extraer esa secuencia a un colaborador común de `service/market/` en vez de repetirla, Principio V); sumar sus casos como métodos nuevos en `MarketQueryServiceTest` (403 y 404, rango invertido 400) (depende de T099)
- [ ] T102 [US6] Crear en `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/controller/market/dto/` `OrderCancellationResponse.java`, `UserOrderPageResponse.java`, `TransactionResponse.java` (lado del usuario, contraparte `ADMIN` o `USER`, la orden propia, la cotización usada y `realizedProfit` solo en las ventas) y `TransactionPageResponse.java`; sumar `POST /orders/{id}/cancel` (200) a `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/controller/market/OrderController.java` y `GET /users/{id}/transactions` (filtros `playerId`, `side`, `from`, `to`, `page`, `size`) y `GET /users/{id}/orders` (`page`, `size`) a `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/controller/market/PortfolioController.java` (si la parte de backend de US2 no se hizo, crear el controller acá), con su documentación Swagger (depende de T100 y T101)

### Tests de la Historia 6

- [ ] T103 [US6] Sumar a `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/service/market/MarketOrderServiceIT.java` la cancelación como métodos nuevos: compra `PARTIAL` con 4 ejecutados → `CANCELLED`, cancelados 6, reserva 0 y el libre recuperado (US6.7); venta con remanente → tokens libres de nuevo (US6.8); con el jugador inactivo; repetir → 409; de otro usuario → 403; la inicial → 403; invariantes después de cada una (depende de T100)
- [ ] T104 [US6] Escribir `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/e2e/TransactionsControllerIT.java`: historial de la más nueva a la más vieja con cada campo de FR-044; contraparte "ADMIN" o "USER" sin identidad; venta con ganancia realizada; filtros y combinaciones; rango invertido y fecha inválida → 400; hojas; otro usuario 403, administrador 200, inexistente 404 o 403, sin credencial 401; `GET /users/{id}/orders` de la más nueva a la más vieja con estados y cantidades y las mismas reglas de acceso (depende de T102)
- [ ] T105 [US6] Sumar a `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/e2e/OrderControllerIT.java` la cancelación como métodos nuevos: 200 con la forma del contrato; 409 orden cerrada; 403 orden ajena; 403 orden inicial; 403 administrador antes del controller sin cambios; 404 orden inexistente; 401 (depende de T102)

### Parte web de la Historia 6

- [ ] T106 [P] [US6] Crear `frontend/src/hooks/useTransactions.ts` (historial del usuario de la sesión con filtros y hoja) y escribir `frontend/tests/hooks/useTransactions.test.tsx` (depende de T037)
- [ ] T107 [P] [US6] Crear `frontend/src/hooks/useMyOrders.ts` (órdenes propias con hoja y recarga) y escribir `frontend/tests/hooks/useMyOrders.test.tsx` (depende de T037)
- [ ] T108 [US6] Crear `frontend/src/pages/TransactionsPage.tsx` y `TransactionsPage.module.css` (`/operaciones`: filtros `player`, `side`, `from`, `to` y `page` en la dirección con `useSearchParams`, reutilizando `DateRangePicker` y `Pager` si sus props alcanzan, sin modificarlos; cada operación con fecha y hora, jugador, compra o venta, cantidad, precio, total, contraparte "administrador" u "otro usuario", la orden, la cotización usada con score, estrategia y versión, y la realizada con `ProfitBadge` en las ventas; rango invertido rechazado con un mensaje; carga, error con reintentar y vacío; tarjetas a 360 px) (depende de T041 y T106)
- [ ] T109 [US6] Crear `frontend/src/pages/MyOrdersPage.tsx` y `MyOrdersPage.module.css` (`/ordenes`: `OrderList` variante `own`; cancelar una pendiente o parcial abre `ConfirmDialog` y usa `useOrderCommands`; después recarga la lista y el saldo de la barra; `page` en la dirección; carga, error con reintentar y vacío) (depende de T039, T040, T042 y T107)
- [ ] T110 [US6] Registrar `/operaciones` y `/ordenes` en `frontend/src/router/routes.tsx` con `RequireRole role="USER"` y sumar los enlaces "Operaciones" y "Mis órdenes" solo para `USER` en `frontend/src/pages/AppLayout.tsx` (si el cambio G2 de `NoPurchaseActions.test.tsx` todavía no se hizo en T071, hacerlo acá, con el mismo alcance) (depende de T108 y T109)
- [ ] T111 [P] [US6] Escribir `frontend/tests/pages/TransactionsPage.test.tsx`: historial con filtros y hoja en la dirección; contraparte "administrador" u "otro usuario"; rango invertido rechazado; carga, error con reintentar y vacío (depende de T110)
- [ ] T112 [P] [US6] Escribir `frontend/tests/pages/MyOrdersPage.test.tsx`: estados y cantidades; cancelar con confirmación (nada se envía antes); después de cancelar cambia el saldo de la barra; un rechazo se muestra en español (depende de T110)

**Checkpoint**: historial, órdenes propias y cancelación por API y en la web.

---

## Fase 9: Historia 7 - Portfolio a una fecha pasada (Prioridad: P7)

**Objetivo**: `GET /users/{id}/portfolio?date=AAAA-MM-DD` reconstruye tenencias, saldo total y realizada
hasta el final de ese día (hora de Argentina) y valúa con la cotización de esa fecha; la pantalla elige la
fecha.

**Prueba independiente**: con operaciones en varios días y un historial de cotizaciones conocido, pedir el
portfolio a distintas fechas y verificar contra el historial, y los rechazos de fechas (quickstart,
escenario B.4 y `PortfolioIT`).

### Implementación de la Historia 7

- [ ] T113 [P] [US7] Crear `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/modelo/market/TradeReplay.java` (sección 2.12 y D20): `ofUser(userId, trades)` reproduce compras y ventas en orden (`executedAt`, `id`) con `CostBasis` y acumula la realizada; `ofSuperuser(initialOrders, trades)` cuenta 100 por inicial creada antes del final del día menos lo vendido, sin costo; los jugadores en 0 no figuran; y la regla del saldo a la fecha (el posterior de la última operación anterior al final del día; si no hay, el previo de la primera posterior; si no hay ninguna, el actual); escribir `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/modelo/market/TradeReplayTest.java` con el ejemplo de US7.1 y US7.2, las ventas anteriores incluidas y las posteriores no (US7.4), el jugador que quedó en 0 y las tres ramas del saldo
- [ ] T114 [US7] Sumar `Portfolio.at(day, balance, replay, playersById, quotesByPlayer)` a `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/modelo/market/Portfolio.java` (sin libre, reservado ni comprometidos; patrimonio = saldo + valor) y sus casos como métodos nuevos en `PortfolioTest` (al 25/08, 10 tokens a 1,0300 por 10,30 con el score de esa cotización; al 01/09, 15 a 1,0100 por 15,15) (depende de T113)
- [ ] T115 [US7] Sumar las lecturas a una fecha (`readOnly`): `findOfUserBefore(userId, end)` (`order by executedAt, id`), `findLastOfUserBefore(userId, end)` y `findFirstOfUserAtOrAfter(userId, end)` (JPQL con hoja de tamaño 1) en `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/persistence/sql/interfaces/market/TradeSQLDAO.java` y sus métodos en `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/persistence/repository/market/TradeRepository.java`; `findLastBeforeOf(playerIds, end)` (JPQL, por jugador la de mayor vigencia anterior a `end`) en `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/persistence/sql/interfaces/quote/QuoteSQLDAO.java` y `findAtOf(playerIds, exclusiveEnd)` → `Map<Long, Quote>` en `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/persistence/repository/quote/QuoteRepository.java`; una lectura por lote de jugadores con su equipo en `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/persistence/repository/player/PlayerRepository.java` (método nuevo, sin tocar firmas); sumar sus casos como métodos nuevos en `TradeRepositoryIT` y `QuoteRepositoryMarketIT`
- [ ] T116 [US7] Sumar `at(requesterId, userId, date)` a `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/service/market/PortfolioService.java` (D20): permiso y cuenta como en `current`; después `QuoteDay.parse` (400), `QuoteDay.requireNotFuture` (400) y `requireRegisteredBy` (400) en ese orden; `QuoteDay.endOf(day)`; operaciones anteriores (para el superusuario, también sus iniciales creadas antes); `TradeReplay`; una consulta `findAtOf`; jugadores por lote; saldo a la fecha; `Portfolio.at`; sumar sus casos como métodos nuevos en `PortfolioServiceTest` (orden de las validaciones, el superusuario, una sola consulta de cotizaciones) (depende de T114 y T115)
- [ ] T117 [US7] Sumar el parámetro opcional `date` a `GET /users/{id}/portfolio` en `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/controller/market/PortfolioController.java` (texto que valida el servicio, para dar el mismo mensaje que la 007; con fecha la respuesta no trae libre, reservado ni comprometidos) y documentarlo en Swagger (depende de T116)

### Tests de la Historia 7

- [ ] T118 [US7] Escribir `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/service/market/PortfolioIT.java` (configuración de `MarketDatabase`, `MutableClock`): usuario registrado en agosto; cotizaciones de 100,00 desde el 17/08, 103,00 desde el 24/08 y 101,00 desde el 31/08; compra de 10 el 20/08 y de 5 el 26/08 a 1,0300 → al 25/08, 10 tokens a 1,0300 (10,30) con el score de esa cotización; al 01/09, 15 tokens a 1,0100 (15,15) (SC-005); una operación a las 23:30 hora de Argentina entra en ese día (US7.3); la realizada incluye solo las ventas anteriores (US7.4); el saldo a la fecha sale de las operaciones; la fecha de hoy equivale al actual con la cotización del final del día; el superusuario a una fecha (depende de T117)
- [ ] T119 [US7] Sumar a `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/e2e/PortfolioControllerIT.java` los casos con fecha como métodos nuevos: `date=2026-13-01`, fecha futura y anterior al registro → 400 con el valor no admitido; con fecha válida, la forma sin `availableBalance`, `reservedBalance` ni `committed`; una cuenta sin `registeredAt` admite fechas anteriores (depende de T117)

### Parte web de la Historia 7

- [ ] T120 [US7] Sumar la fecha opcional a `frontend/src/hooks/usePortfolio.ts` y sus casos como métodos nuevos en `frontend/tests/hooks/usePortfolio.test.tsx` (depende de T069)
- [ ] T121 [US7] Sumar a `frontend/src/pages/PortfolioPage.tsx` el selector de fecha (`input type="date"` con su etiqueta, `date` en la dirección, botón para volver al actual; con fecha no muestra libre, reservado ni comprometidos) y sus casos como métodos nuevos en `frontend/tests/pages/PortfolioPage.test.tsx` (fecha en la dirección, vuelta al actual, sin libre ni reservado a una fecha, un 400 del servidor mostrado en español) (depende de T071 y T120)

**Checkpoint**: el escenario 3 de la consigna completo: portfolio actual y a fechas pasadas, por API y en
la web.

---

## Fase 10: Historia 8 - Score visible en las cotizaciones (Prioridad: P8)

**Objetivo**: el score aparece en toda cotización de la API y de las pantallas; las estadísticas crudas y
las métricas normalizadas siguen ocultas.

**Prueba independiente**: consultar cada recurso de cotización y recorrer cada pantalla contra MSW,
verificando que el score aparece y ninguna estadística (quickstart, escenario H). Solo depende de la Fase 1:
el plan sugiere hacerla primero.

### Implementación de la Historia 8

- [ ] T122 [P] [US8] Verificar que `specs/007-cotizacion-estrategias/contracts/quotes-api.yaml` está en la versión 1.1.0 con `score` obligatorio en `Quote`, sus descripciones y ejemplos (el plan ya lo dejó así; si falta algo, completarlo)
- [ ] T123 [US8] Sumar `score` (`BigDecimal` de 6 decimales, después de `tokenPrice`) a `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/controller/quote/dto/QuoteResponse.java` (y actualizar su comentario, que dice que a propósito no lo lleva) y a `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/controller/quote/dto/PlayerQuoteResponse.java`; y, **en esta misma tarea** (G2), en `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/e2e/QuoteControllerIT.java` cambiar `ningunaRespuestaTraeElScoreNiEstadisticas` a `ningunaRespuestaTraeEstadisticasYTodasTraenElScore`, sacar `"score"` del `doesNotContain` y sumar `contains("\"score\"")`, sin tocar nada más
- [ ] T124 [US8] Escribir `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/e2e/QuoteScoreIT.java`: la cotización actual, a una fecha, la evolución, el lote y el ranking traen `score` (0,30 en una calculada, 0 en la inicial) y ninguna trae `baseValue`, goles, minutos, rating ni métricas normalizadas (SC-014) (depende de T123)
- [ ] T125 [P] [US8] Sumar el par "Score" al final de `frontend/src/components/QuotePanel.tsx`, solo cuando la cotización lo trae, con `formatScore` de `frontend/src/utils/market.ts`
- [ ] T126 [P] [US8] Sumar la columna de score, al final, en `frontend/src/components/RankingList.tsx`
- [ ] T127 [P] [US8] Sumar el score al final del detalle de cada punto de `frontend/src/components/QuoteChart.tsx` y de su alternativa en texto
- [ ] T128 [P] [US8] Sumar la columna de score, al final, en `frontend/src/components/QuoteTable.tsx`
- [ ] T129 [P] [US8] Sumar el score al final de `frontend/src/components/QuoteTag.tsx` (pizarra, FR-071), sin botones ni listas nuevas (sigue habiendo un único elemento con rol `list` en `/pizarra`)
- [ ] T130 [US8] Escribir `frontend/tests/pages/QuoteScore.test.tsx`: la ficha (panel, gráfico y tabla), el ranking, la pizarra y el portfolio muestran el score; ningún texto de estadísticas crudas ni métricas normalizadas. Verificar que los tests de componentes y utilidades de la 007 pasan sin cambios (depende de T125 a T129 y T071)

**Checkpoint**: score visible en la API y en todas las pantallas con cotizaciones.

---

## Fase 11: Historia 9 - Auditoría del mercado (Prioridad: P9)

**Objetivo**: cada orden creada, operación y cancelación queda auditada en la misma transacción (ya lo
hacen US1, US4 y US6) y `GET /audit` la consulta, solo para el administrador.

**Prueba independiente**: ejecutar órdenes, cruces y cancelaciones y verificar el registro de cada una con
autor, fecha, detalle y estados anterior y posterior, que no se puede modificar ni borrar y que solo el
administrador lo consulta (quickstart, escenario G). El contenido de los cruces y cancelaciones necesita
US4 y US6.

### Implementación de la Historia 9

- [ ] T131 [P] [US9] Crear `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/modelo/market/AuditFilter.java` (`eventType`, `userId`, `playerId`, `from`, `to`, con las reglas de fecha de `TradeFilter`)
- [ ] T132 [US9] Sumar `findAudit(...)` (JPQL: el usuario como autor o cualquiera de las dos partes, filtros nulos que no filtran, `order by occurredAt desc, id desc`) y su conteo en `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/persistence/sql/interfaces/market/AuditEntrySQLDAO.java` y `findAudit(filter, page, size)` → `QuotePage<AuditEntry>` (`readOnly`) en `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/persistence/repository/market/AuditRepository.java`; sumar sus casos como métodos nuevos en `AuditRepositoryIT` (depende de T131)
- [ ] T133 [US9] Crear `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/service/market/AuditQueryService.java` (`readOnly`) y escribir `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/service/market/AuditQueryServiceTest.java` (depende de T132)
- [ ] T134 [US9] Crear en `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/controller/market/dto/` `AuditEntryResponse.java`, `AuditPartyResponse.java` y `AuditPageResponse.java`, y `backend/src/main/java/ar/edu/unq/desapp/futbolmarket/controller/market/AuditController.java` con `GET /audit` (`eventType`, `userId` y `playerId` con `@Min(1)`, `from`, `to`, `page`, `size`), `@Tag(name = "Auditoría")` y `@ApiResponse` 200, 400, 401 y 403 (depende de T133)

### Tests de la Historia 9

- [ ] T135 [US9] Escribir `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/service/market/AuditAtomicityIT.java` (configuración de `MarketDatabase`): con una falla forzada al guardar las operaciones de una compra que se ejecuta (`@MockitoSpyBean` de `TradeRepository` que lanza en `saveAll`) no queda la orden, ni su auditoría, ni cambia ningún saldo (FR-025); un pedido rechazado por saldo no deja ningún `ORDER_CREATED` (US9.6); una falla al guardar la auditoría de `openFor` no deja la orden inicial (depende de T046 y T047)
- [ ] T136 [US9] Escribir `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/e2e/AuditControllerIT.java`: el administrador recibe 200 de la más nueva a la más vieja; usuario común 403; sin credencial 401; las iniciales con autor `SUPERUSER` (US9.1); un `TRADE_EXECUTED` con comprador, vendedor, jugador, cantidad, precio, total, cotización y libre, reservado y tenencia de cada parte antes y después (US9.2); un `ORDER_CANCELLED` del usuario (`USER_REQUEST`) y uno del sistema (`INSUFFICIENT_CREDITS`, `actorType: SYSTEM`, sin `actorId`) (US9.3); cada filtro y valores no admitidos → 400; `PUT`, `PATCH` y `DELETE` sobre `/audit` y `/audit/{id}` no modifican nada y el registro sigue igual (US9.5) (depende de T134, T081 y T102)

**Checkpoint**: todas las historias funcionan de punta a punta.

---

## Fase 12: Pulido y aspectos transversales

**Propósito**: seguridad de punta a punta, volumen, Swagger, responsive y teclado, documentación,
verificación de la regla de tests y la definición de terminado.

- [ ] T137 [P] Escribir `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/e2e/MarketAccessIT.java`: la matriz de los ocho endpoints del mercado contra sin credencial, credencial inválida, usuario dueño, otro usuario, administrador y clave de API, con los 200, 401 y 403 de D24; `GET /orders` sigue público con una credencial inválida; ninguno de los rechazos cambia el estado (SC-007)
- [ ] T138 [P] Escribir `frontend/tests/pages/MarketAccess.test.tsx`: un visitante en `/portfolio`, `/operaciones` y `/ordenes` va a `/login`; el administrador ve el aviso de falta de permiso; enlaces de la barra por rol (visitante: Mercado; usuario: Mercado, Portfolio, Operaciones y Mis órdenes; administrador: Mercado y Administración, sin los de usuario)
- [ ] T139 [P] Escribir `backend/src/test/java/ar/edu/unq/desapp/futbolmarket/service/market/MarketVolumeIT.java` (opt-in con `@EnabledIfEnvironmentVariable(named = "MARKET_BENCHMARK", matches = "true")`, base en archivo bajo `build/benchmark/`, como `QuoteVolumeBenchmarkIT`): 2.600 jugadores con su orden inicial (registra el tiempo de `openMissing`); una compra, el portfolio actual y a una fecha, una hoja del historial y una del listado, cada uno en menos de 2 s (SC-009). No corre en `./gradlew build`
- [ ] T140 Pasada de 360 px y teclado por `MarketPage`, `PortfolioPage`, `TransactionsPage`, `MyOrdersPage`, la ficha con `TradePanel` y `ConfirmDialog` con `npm run dev`: sin desplazamiento horizontal (tablas en tarjetas), foco visible, todo lo clickeable es `button` o enlace; corregir en los `.module.css` con los tokens de `frontend/src/styles/tokens.css` (FR-061, SC-015)
- [ ] T141 Verificar Swagger con `./gradlew bootRun` (perfil `local`): `/swagger-ui.html` muestra los tags Órdenes, Mercado, Portfolio y Auditoría con los ocho endpoints, sus ejemplos y códigos, y se ejecutan con `Bearer` y con `X-API-Key`; el perfil muestra los dos saldos nuevos y las cotizaciones el score
- [ ] T142 [P] Documentar el mercado en `README.md` (en español): el superusuario es el administrador de la 001 y sin su contraseña el mercado no inicia; la propiedad `futbolmarket.market.initial-orders.automatic`; los endpoints nuevos y la pantalla de cada uno; `LOCK_TIMEOUT` en `local`; la medición opt-in con `MARKET_BENCHMARK`
- [ ] T143 Verificar la regla de tests (Principio IV): `git diff develop --stat -- backend/src/test frontend/tests` muestra solo archivos nuevos más `AccountControllerIT.java`, `QuoteControllerIT.java`, `NoPurchaseActions.test.tsx`, `NoMarketActions.test.tsx`, `mocks/handlers.ts` y `mocks/quotes.ts`, y el diff de cada uno coincide exactamente con la tabla de D28 de `research.md`; ningún test de la 001 a la 007 tiene casos agregados
- [ ] T144 Validación final con [quickstart.md](./quickstart.md), escenarios A a J (incluido el escenario 3 de la consigna por API y en la web, y `MARKET_BENCHMARK=true ./gradlew test --tests "*MarketVolumeIT"`), y definición de terminado: `./gradlew build` con BUILD SUCCESSFUL, `./gradlew bootRun` con el perfil `local`, `npm test`, `npm run lint` y `npm run build` sin errores, `npm run dev` contra el backend, y SonarCloud con menos de 10 issues

---

## Dependencias y orden de ejecución

### Dependencias entre fases

- **Setup (Fase 1)**: T001 es una puerta: sin `cf0a0dd` en la rama no se empieza el backend. T004 no
  bloquea.
- **Fundacional (Fase 2)**: depende de la Fase 1. La parte de backend (T005 a T033) **bloquea todas las
  historias de backend**; la base del frontend (T034 a T042) bloquea solo las partes web y puede avanzar
  en paralelo con el backend.
- **US1 (Fase 3)**: depende de la Fase 2. Es el MVP.
- **US2 (Fase 4)**: depende de la Fase 2; sus IT siembran tenencias, salvo el escenario 3 de T068, que usa
  la compra de US1 (T050). La parte web solo necesita la base del frontend.
- **US3 (Fase 5)**: solo la base del frontend (trabaja contra MSW); para la verificación manual necesita
  US1 y US4.
- **US4 (Fase 6)**: depende de US1 (`OrderMatcher`, `PlayerBook`, `PlayerBookLoader`, `MarketOrderService`
  y `OrderController`).
- **US5 (Fase 7)**: el backend depende de la Fase 2 y de `OrderController` (US1); la web, de la base del
  frontend.
- **US6 (Fase 8)**: depende de US1 (libro, servicio y controller) y usa `PortfolioController` de US2 (o lo
  crea); la web, de la base del frontend.
- **US7 (Fase 9)**: depende de US2 (`Portfolio`, `PortfolioService`, `PortfolioController` y
  `PortfolioPage`).
- **US8 (Fase 10)**: solo de la Fase 1 (y de T034 y T036 en el frontend); el plan sugiere hacerla primera.
  T130 verifica también el portfolio (T071).
- **US9 (Fase 11)**: depende de la Fase 2; sus tests de contenido necesitan US1, US4 y US6.
- **Pulido (Fase 12)**: depende de todas las historias que se quieran entregar.

### Dependencias entre historias (resumen)

```text
Fase 1 ─► Fase 2 (backend) ─┬─► US1 ─┬─► US4
                            │        ├─► US5
                            │        └─► US6      (usa PortfolioController de US2)
                            ├─► US2 ─► US7
                            └─► US9               (sus tests de contenido piden US1, US4 y US6)
Fase 1 ─► US8 (en cualquier momento)
Fase 2 (base del frontend) ─► web de US2, US3, US5, US6 y US7
```

### Dentro de cada historia

- Excepciones y modelo antes que repositories; repositories antes que servicios; servicios antes que
  controllers y DTO; hooks y componentes antes que pantallas.
- Los tests van **después** de la implementación de su tarea (sin TDD) y la tarea no se cierra sin ellos
  en verde.
- Varias historias suman métodos a los mismos archivos (`PlayerBook`, `OrderMatcher`, `MarketOrderService`,
  `OrderController`, los DAO y repositories del mercado, y sus tests): coordinar los cambios a esos
  archivos y no hacer en paralelo dos tareas que los toquen.

### Oportunidades de paralelismo

- Fase 2: T005, T006 y T007; los enums, `MarketMath`, las excepciones y `OrderView` (T008 a T012); T014 y
  T016 entre sí; las cuatro tablas (T020 a T023) y T024 entre sí; los IT de repositories T025 a T029 entre
  sí; T032 y T033; en el frontend, T036, T037 y T038 entre sí y los componentes T039 a T041 entre sí.
- US1: T045 y T049, una vez cerrado T044; los tests de dominio T051 y T052 y los de servicio T053 a T055 entre sí.
- US2: T060 con el dominio; T062 y T065; en la web, T069 y T070.
- US3: T073 y T075; T074 en cuanto esté T073.
- US4: T082, T083 y T084 entre sí.
- US5: T088 con el resto; en la web, T093 y T094.
- US6: T097 con el resto; en la web, T106 y T107; T111 y T112.
- US8: los cinco componentes T125 a T129 entre sí, y con T122.
- Con dos desarrolladores: uno lleva el backend (Fases 2 backend, 3, 4, 6, 7, 8, 9, 10 backend y 11) y el
  otro el frontend (base de la Fase 2 y las partes web) contra MSW, y se cruzan en la verificación manual
  de `quickstart.md`.

---

## Ejemplos de ejecución en paralelo

### Fundacional

```text
# Dominio compartido, en paralelo (archivos distintos):
Tarea: "T008 enums en modelo/market/"
Tarea: "T009 MarketMath y MarketMathTest"
Tarea: "T010 excepciones en modelo/market/exception/"
Tarea: "T011 excepciones en modelo/user/exception/"
Tarea: "T012 OrderView"

# Las cuatro tablas, en paralelo:
Tarea: "T020 market_orders (SQL, mapper, DAO, repository)"
Tarea: "T021 holdings"
Tarea: "T022 market_trades"
Tarea: "T023 audit_entries"
```

### Historia 1

```text
Tarea: "T051 OrderMatcherTest (compra)"
Tarea: "T052 PlayerBookTest (compra)"
Tarea: "T053 InitialOrderServiceTest"
Tarea: "T054 InitialOrderStartupTest"
Tarea: "T055 MarketOrderServiceTest (compra)"
```

### Historia 2 (web)

```text
Tarea: "T069 usePortfolio"
Tarea: "T070 PositionTable y PortfolioTotals"
```

### Historia 4

```text
Tarea: "T082 casos de venta en OrderMatcherTest"
Tarea: "T083 casos de venta en PlayerBookTest"
Tarea: "T084 casos de venta en MarketOrderServiceTest"
```

### Historia 8

```text
Tarea: "T125 QuotePanel"
Tarea: "T126 RankingList"
Tarea: "T127 QuoteChart"
Tarea: "T128 QuoteTable"
Tarea: "T129 QuoteTag"
```

---

## Estrategia de implementación

### MVP primero (solo la Historia 1)

1. Fase 1: puertas (sin `cf0a0dd` en la rama, no se sigue con el backend).
2. Fase 2: fundacional (backend completo; la base del frontend puede quedar para después).
3. Fase 3: US1.
4. **PARAR y VALIDAR**: el arranque genera una orden inicial por jugador sin duplicar y `POST /orders/buy`
   reproduce US1.4 a US1.6 (quickstart, escenarios A, B.1 y F); `./gradlew build` verde.
5. PR chico contra `develop`.

### Entrega incremental

1. Setup + Fundacional → base lista.
2. US8 (opcional primero, como sugiere el plan) → score visible; es chico e independiente.
3. US1 → órdenes iniciales y compra al superusuario (MVP).
4. US2 → portfolio actual por API y en la web, perfil con los saldos.
5. US3 → comprar y vender desde la ficha.
6. US4 → reventa entre usuarios.
7. US5 → listado del mercado y pantalla "Mercado".
8. US6 → historial, órdenes propias y cancelación.
9. US7 → portfolio a una fecha: cierra el escenario 3 de la consigna.
10. US9 → consulta de la auditoría.
11. Pulido → seguridad de punta a punta, volumen, Swagger, 360 px, README y definición de terminado.

Cada paso suma valor sin romper los anteriores; PRs chicos y frecuentes contra `develop` (constitución,
"Trabajo en paralelo").

---

## Notas

- [P] = archivos distintos, sin dependencias pendientes.
- [US#] liga cada tarea con su historia; la trazabilidad de cada regla a su clase está en la sección 8 de
  `data-model.md`.
- Lecturas que el plan no nombra y estas tareas agregan (anotarlas en `data-model.md` al implementarlas):
  `OrderView` (T012), `HoldingView` (T060), `BookOutcome` con el jugador, la cotización y la cuenta del
  pedido (T044), las entradas automáticas `onStartup` y `afterSync` de `InitialOrderService` (T047), el
  método de `MarketOrder` para sumar a la reserva (T079) y la lectura por lote de `PlayerRepository` (T115).
- Distinto de una frase de D8: los IT del mercado crean las órdenes iniciales llamando a
  `InitialOrderService.openMissing()` y dejan la generación automática apagada; solo `InitialOrderIT` la
  prende, para no competir con el `@Async` del arranque.
- Los tests existentes de la 001 a la 007 no se tocan, salvo los cuatro cambios autorizados por G2 y G3,
  cada uno en la tarea que lo hace necesario.
- Hacer commit al cerrar cada tarea o grupo lógico; parar en cada checkpoint para validar la historia.
- Puntos abiertos que no frenan las tareas (G4, T004): reserva del remanente (D11), portfolio del
  superusuario sin costo (D7 y D19) y plan B del punto caliente del superusuario (D13).
