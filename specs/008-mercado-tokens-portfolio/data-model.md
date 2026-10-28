# Modelo de datos: Mercado de tokens y portfolio de usuarios

**Feature**: `008-mercado-tokens-portfolio` | **Fecha**: 2026-10-28 | **Plan**: [plan.md](./plan.md) |
**Investigación**: [research.md](./research.md)

Este documento fija las clases del modelo, sus invariantes, las tablas, las consultas y los tipos del frontend.
No contiene cuerpos de métodos: el comportamiento se describe con reglas y ejemplos. Las decisiones están
justificadas en `research.md` (D1 a D31).

## 1. Vista general

```text
                 ┌──────────────────────────── PlayerBook (un jugador, un pedido) ────────────────────────────┐
                 │  Player · Quote vigente · MarketOrder inicial · MarketOrder pendientes del otro lado        │
                 │  AppUser involucrados (bloqueados) · Holding del jugador                                    │
                 │                                                                                             │
 pedido ───────► │  placeBuy / placeSell / cancel ──► OrderMatcher.match ──► MatchPlan (Fill…, remanente)      │
                 │                                   │                                                         │
                 │                                   └─► aplica: AppUser (reserve/pay/receive/release)         │
                 │                                              Holding (CostBasis, committed)                 │
                 │                                              MarketOrder (fill/cancel)                      │
                 │                                              Trade + AuditEntry                             │
                 └──────────────────────────────────── BookOutcome ───────────────────────────────────────────┘

 Lecturas:  Holding + Quote vigente ──► Portfolio.current        Trade… ──► TradeReplay ──► Portfolio.at(fecha)
```

- `modelo/market/` es puro: solo JDK y clases de modelo de otros contextos (`AppUser`, `Player`, `Quote`,
  `QuoteDay`, `QuotePage`, `PlayerFilter`, `League`).
- El superusuario no tiene `Holding`: su tenencia de un jugador es el pendiente de su orden inicial (D7).
- Para cada jugador con orden inicial: **pendiente de la inicial + Σ `Holding.quantity` = 100** (FR-026).

## 2. Modelo: contexto `market`

### 2.1 Enums

| Enum | Valores | Notas |
|---|---|---|
| `OrderSide` | `BUY`, `SELL` | tipo de orden; en el historial, el lado del usuario |
| `OrderStatus` | `PENDING`, `PARTIAL`, `EXECUTED`, `CANCELLED` | se deriva de las cantidades (D9); `isOpen()` para los dos primeros |
| `OwnerType` | `USER`, `ADMIN` | tipo de usuario dueño de una orden y contraparte de una operación |
| `CancelReason` | `USER_REQUEST`, `INSUFFICIENT_CREDITS` | motivo de una cancelación |
| `AuditEventType` | `ORDER_CREATED`, `TRADE_EXECUTED`, `ORDER_CANCELLED` | |
| `AuditActorType` | `USER`, `SUPERUSER`, `SYSTEM` | autor de un registro de auditoría |
| `NotTradableReason` | `INACTIVE`, `WITHOUT_QUOTE`, `WITHOUT_INITIAL_ORDER` | arma el mensaje de `PlayerNotTradableException` |

### 2.2 `MarketMath`

Clase final de utilidad, sin estado, con las escalas como constantes (D4):
`CREDIT_SCALE = 2`, `PRICE_SCALE = 4`, `PERCENT_SCALE = 2`, `TOKENS_PER_PLAYER = 100`,
`MAX_ORDER_QUANTITY = 100`, `INITIAL_TOKEN_PRICE = 1.0000`.

| Función | Regla | Ejemplo |
|---|---|---|
| `total(quantity, tokenPrice)` | `quantity × tokenPrice`, 2 decimales `HALF_UP`, un solo redondeo | `total(3, 1.0333) = 3.10`; `total(10, 1.0345) = 10.35` |
| `maxAffordable(available, tokenPrice, limit)` | mayor `n ≤ limit` con `total(n, tokenPrice) ≤ available` | `maxAffordable(10.30, 1.1000, 10) = 9` |
| `averagePrice(cost, quantity)` | `cost / quantity`, 4 decimales `HALF_UP` | `averagePrice(15.15, 15) = 1.0100` |
| `percent(part, whole)` | `part / whole × 100`, 2 decimales; vacío si `whole = 0` | `percent(0.30, 10.00) = 3.00` |

### 2.3 `MarketOrder`

Una orden de compra o de venta. Entidad con identidad; cambia solo por sus métodos.

| Campo | Tipo | Regla |
|---|---|---|
| `id` | `Long` | nulo hasta guardarse |
| `side` | `OrderSide` | |
| `ownerId` | `long` | el titular; el superusuario solo en las iniciales |
| `playerId` | `long` | |
| `requested` | `int` | 1 a 100 (`MAX_ORDER_QUANTITY`); 100 en la inicial |
| `executed` | `int` | 0 ≤ `executed` ≤ `requested` |
| `cancelled` | `int` | 0 ≤ `cancelled`; `executed + cancelled ≤ requested` |
| `reservedCredits` | `BigDecimal` (2) | solo compras; 0 en ventas y cuando no queda pendiente |
| `reservationPrice` | `BigDecimal` (4) o nulo | precio por token vigente al crear una compra; nulo en ventas |
| `createdAt` | `Instant` | del `Clock` |
| `initial` | `boolean` | orden inicial del superusuario |
| `cancelReason` | `CancelReason` o nulo | solo si `cancelled > 0` |

Derivados: `pending() = requested − executed − cancelled`; `status()` (tabla de D9); `ownerType()` (`ADMIN` si
`initial`, si no `USER`); `isOpen()`.

Fábricas y métodos:

| Método | Efecto | Lanza |
|---|---|---|
| `MarketOrder.initialFor(superuserId, playerId, at)` | venta inicial por 100 | — |
| `MarketOrder.buy(ownerId, playerId, quantity, reservation, reservationPrice, at)` | compra con su reserva total | `MarketInvariantException` si la cantidad está fuera de 1..100 |
| `MarketOrder.sell(ownerId, playerId, quantity, at)` | venta | ídem |
| `fill(n)` | `executed += n` | `MarketInvariantException` si `n > pending` |
| `consumeReservation(amount)` | en una compra, paga una operación con su reserva: `reservedCredits −= amount` | `MarketInvariantException` si `amount > reservedCredits` |
| `adjustReservation(available)` | `reservedCredits = min(total(pending, reservationPrice), available)` (D11); devuelve cuánto cambió, para mover lo mismo en la cuenta | — |
| `cancelRemainder(reason)` | `cancelled += pending`, `reservedCredits = 0` | `OrderNotCancellableException` si no está abierta |
| `requireOwnedBy(requesterId)` | — | `OrderNotOwnedException` si es ajena **o inicial** |

### 2.4 `CostBasis`

Valor inmutable que comparten `Holding` y `TradeReplay` (D12).

| Campo | Regla |
|---|---|
| `quantity` | tokens que tiene, ≥ 0 |
| `acquiredQuantity` | tokens comprados desde la última vez que `quantity` fue 0 |
| `acquiredCost` | lo pagado por esos tokens (2 decimales) |

| Método | Regla |
|---|---|
| `acquire(n, paid)` | suma a los tres |
| `dispose(n, received)` | devuelve `Disposal(basis, realized)` con `realized = received − n × averagePrice()`, redondeado a 2 decimales (FR-029); el costo adquirido no cambia; si `quantity` llega a 0, los acumuladores se reinician |
| `averagePrice()` | `MarketMath.averagePrice(acquiredCost, acquiredQuantity)`; 0 sin compras |
| `invested()` | `quantity × averagePrice()`, 2 decimales |

Ejemplo: `acquire(10, 10.00)`, `acquire(5, 5.15)` → promedio 1,0100, invertido 15,15; `dispose(5, 5.25)` →
realizada +0,20, quedan 10, promedio 1,0100, invertido 10,10.

### 2.5 `Holding`

La tenencia de un **usuario** de un jugador (el superusuario no tiene).

| Campo | Regla |
|---|---|
| `id`, `ownerId`, `playerId` | uno por titular y jugador |
| `basis` | `CostBasis` |
| `committed` | tokens comprometidos en sus ventas pendientes; 0 ≤ `committed` ≤ `quantity` |

Métodos: `freeTokens()` (`quantity − committed`); `commit(n)` (lanza `InsufficientTokensException` si
`n > freeTokens()`); `releaseCommitted(n)`; `buy(n, paid)`; `sell(n, received)` → realizada (baja `quantity` y
`committed`). `Holding.empty(ownerId, playerId)` para el primer cruce.

### 2.6 `Trade`

Una operación ejecutada. **Inmutable** (`record`): no tiene métodos de cambio.

| Campo | Regla |
|---|---|
| `id` | nulo hasta guardarse |
| `executedAt` | el instante del pedido |
| `playerId` | |
| `buyOrderId`, `sellOrderId` | las órdenes de cada parte (FR-028) |
| `buyerId`, `sellerId` | |
| `sellerType` | `OwnerType` del vendedor (la contraparte de una compra al superusuario es `ADMIN`) |
| `quantity` | ≥ 1 |
| `tokenPrice` | 1,0000 contra la inicial; si no, el de la cotización vigente |
| `total` | `MarketMath.total(quantity, tokenPrice)` |
| `quoteId` | la cotización vigente en ese momento, también contra la inicial (FR-028) |
| `buyerBalanceBefore/After`, `sellerBalanceBefore/After` | saldo **total** de cada parte |
| `buyerTokensBefore/After`, `sellerTokensBefore/After` | tenencia de ese jugador (del superusuario: el pendiente de su inicial) |
| `realizedProfit` | ganancia o pérdida del vendedor (FR-029); nula si vende el superusuario |

Derivados para el historial: `sideFor(userId)` y `counterpartyFor(userId)` (`ADMIN` o `USER`).

Una lectura del historial (`TradeView`) suma lo que se muestra: el jugador (nombre, equipo, liga) y la cotización
(vigencia, score, estrategia y versión). Es un modelo de lectura, no se guarda.

### 2.7 `PartyState` y `PartyChange`

`PartyState(userId, freeBalance, reservedBalance, tokens)` es una foto de una parte; `PartyChange(before, after)`
son las dos fotos de un hecho. Las arma `PlayerBook` con `AppUser.availableBalance()`, `AppUser.getReserved()` y
la tenencia (o el pendiente de la inicial).

### 2.8 `AuditEntry`

Un registro de auditoría. **Inmutable** (`record`). Las fábricas arman el `detail` en español.

| Campo | Regla |
|---|---|
| `id`, `occurredAt` | |
| `eventType` | `AuditEventType` |
| `actorType`, `actorId` | `actorId` nulo solo con `SYSTEM` |
| `playerId`, `orderId` | siempre |
| `tradeId` | solo en `TRADE_EXECUTED` |
| `side`, `quantity` | de la orden o de la operación |
| `tokenPrice`, `total`, `quoteId` | solo en `TRADE_EXECUTED` |
| `cancelReason` | solo en `ORDER_CANCELLED` |
| `primary` | `PartyChange` del dueño (o del comprador en una operación) |
| `secondary` | `PartyChange` del vendedor en una operación; nulo en el resto |
| `detail` | frase en español, hasta 500 caracteres |

Fábricas: `orderCreated(order, actor, change, at)`, `tradeExecuted(trade, actor, buyer, seller)`,
`orderCancelled(order, actor, reason, change, at)`.

### 2.9 `OrderMatcher`, `MatchPlan` y `Fill`

`OrderMatcher` es puro y sin estado (D10). `match(incoming, initialOrder, opposite, price)`:

- `incoming`: lado, dueño y cantidad del pedido.
- `initialOrder`: la inicial del jugador (para compras; puede estar agotada).
- `opposite`: las pendientes del otro lado, en cualquier orden; el matcher las ordena por `createdAt` y `id`.
- `price`: el precio por token vigente.
- Para las ventas, cada compra pendiente llega con su **disponible** (reserva + libre de su dueño, D11).

Devuelve `MatchPlan(fills, remainder, shortfalls)`:

- `Fill(counterpart, quantity, tokenPrice, total)`: un cruce.
- `remainder`: lo que queda pendiente de la orden entrante.
- `shortfalls`: compras pendientes a cancelar (entera o en parte) por saldo insuficiente, con la cantidad.

Casos que fija `OrderMatcherTest` (los del spec): compra de 10 contra la inicial con 100 → un `Fill` de 10 a 1,0000;
inicial con 3 y compra de 10 → `Fill` de 3 a 1,0000 y remanente 7; compra de 10 contra una venta de 5 → `Fill` de 5 y
remanente 5; dos ventas, la de B antes que la de D → primero B; venta de 8 contra una compra de 5 → `Fill` de 5 y
remanente 3; dos compras, la de A antes que la de C → completa A y sigue con C; compra con disponible 10,30 a 1,1000
contra una venta de 10 → `Fill` de 9 y `shortfall` de 1; compra con disponible menor que un token → `shortfall`
entero y la venta sigue con la próxima; las del mismo dueño se saltean; el resultado no depende del orden de entrada.

### 2.10 `PlayerBook` y `BookOutcome`

Agregado que el servicio arma por pedido con lo que cargó **después de bloquear** (D13):

| Parte | Qué es |
|---|---|
| `player` | `Player` (activo o no) |
| `quote` | `Optional<Quote>` vigente al instante del pedido |
| `initialOrder` | `Optional<MarketOrder>` |
| `opposite` | pendientes del otro lado (las del lado del pedido también, para FR-017) |
| `accounts` | `Map<Long, AppUser>` de las cuentas bloqueadas |
| `holdings` | `Map<Long, Holding>` del jugador, por titular |
| `superuserId` | para reconocer la contraparte `ADMIN` |

Métodos:

| Método | Validaciones (en orden) | Resultado |
|---|---|---|
| `placeBuy(ownerId, quantity, at)` | `requireCanTrade`; operable; sin ventas propias pendientes (FR-017); libre ≥ lo necesario (FR-015) | la orden, sus `Trade`, la auditoría y lo que cambió |
| `placeSell(ownerId, quantity, at)` | `requireCanTrade`; operable; inicial sin pendiente (FR-011); sin compras propias pendientes; tokens libres ≥ cantidad (FR-016) | ídem, más las compras canceladas por saldo |
| `cancel(order, requesterId, at)` | `requireOwnedBy`; abierta | la orden cancelada, la cuenta o tenencia liberada y la auditoría |

`BookOutcome(order, trades, changedOrders, changedAccounts, changedHoldings, auditEntries)`. El orden de
`auditEntries` es el de los hechos: `ORDER_CREATED`, cada `TRADE_EXECUTED` y cada `ORDER_CANCELLED`.

`isTradable()` es `player.active() && quote.isPresent() && initialOrder.isPresent()` (D15).

### 2.11 `Portfolio`, `PortfolioPosition` y `PortfolioTotals`

Se calculan, no se guardan. La posición se llama `PortfolioPosition` porque `Position` ya es el enum de la posición
del jugador (`modelo/position/Position`), que estas mismas clases usan (D3).

`PortfolioPosition`:

| Campo | Regla |
|---|---|
| `player` | `Player` (nombre, posición, equipo, liga, activo) |
| `tokens` | > 0 |
| `sharePercent` | = `tokens` (cada token es el 1 %) |
| `committed` | tokens en ventas pendientes; solo en el actual |
| `averagePrice`, `invested` | `CostBasis`; nulos en el del superusuario |
| `quote` | la cotización usada (precio por token, score, vigencia) |
| `value` | `total(tokens, quote.tokenPrice())` |
| `profit` | `value − invested`; nulo sin `invested` |
| `profitPercent` | `percent(profit, invested)`; nulo si `invested` es 0 o nulo |

`PortfolioTotals`:

| Campo | Actual | A una fecha |
|---|---|---|
| `availableBalance`, `reservedBalance` | sí | no (FR-040) |
| `balance` (total) | sí | sí |
| `invested`, `value`, `profit` | Σ de las posiciones | ídem |
| `realizedProfit` | Σ de las ventas | Σ de las ventas hasta la fecha |
| `equity` | `balance + value` (= libre + reservado + valor) | `balance + value` |

Fábricas: `Portfolio.current(account, holdings, quotesByPlayer, realized)`, `Portfolio.currentOfSuperuser(account,
initialOrders, quotesByPlayer)` y `Portfolio.at(day, balance, replay, quotesByPlayer)`.

Ejemplos que fija `PortfolioTest`: 10 tokens a 1,0000 con cotización 103,00 → 10 %, invertido 10,00, precio 1,0300,
valor 10,30, ganancia +0,30, rentabilidad +3,00 %; libre 990,00, reservado 6,18 y posiciones por 20,60 →
patrimonio 1016,78; sin tenencias → posiciones vacías y patrimonio = saldo; 10 tokens con cotizaciones 100,00,
120,00 y 90,00 → valor 10,00, 12,00 y 9,00.

### 2.12 `TradeReplay`

Dominio puro que reconstruye las posiciones a una fecha (D20).

- `TradeReplay.ofUser(userId, trades)`: las operaciones ya filtradas hasta el final del día, en orden
  (`executedAt`, `id`); compra → `CostBasis.acquire`, venta → `CostBasis.dispose`; acumula la realizada.
- `TradeReplay.ofSuperuser(initialOrders, trades)`: 100 tokens por cada inicial creada antes del final del día,
  menos lo vendido; sin costo ni realizada.
- Devuelve, por jugador, `CostBasis` (o solo la cantidad) y la realizada total. Los jugadores que quedaron en 0 no
  figuran.

### 2.13 Filtros

| Clase | Campos | Reglas |
|---|---|---|
| `MarketOrderFilter` | `side`, `league`, `team`, `ownerType`, `playerId` | `team` se normaliza como en `PlayerFilter` (vacío → `CatalogInvariantException`, 400) |
| `TradeFilter` | `playerId`, `side`, `from`, `to` | fechas con `QuoteDay.parse`; `QuoteDay.requireOrdered` si vienen las dos; los extremos son `startOf(from)` y `endOf(to)` |
| `AuditFilter` | `eventType`, `userId`, `playerId`, `from`, `to` | ídem |

Las hojas se devuelven como `QuotePage<T>` (genérica, D17).

## 3. Modelo: cambios en otros contextos

### 3.1 `user`: `AppUser`

Campos nuevos: `reserved` (`BigDecimal`, 2 decimales, ≥ 0) y `registeredAt` (`Instant`, nulo en las cuentas
anteriores a la 008).

| Método nuevo | Regla |
|---|---|
| `availableBalance()` | `balance − reserved` |
| `reserve(amount)` | `InsufficientCreditsException` si `amount > availableBalance()` |
| `release(amount)` | `MarketInvariantException` si `amount > reserved` |
| `payFromReserve(amount)` | resta de `balance` y de `reserved`; `MarketInvariantException` si `amount > reserved` |
| `receive(amount)` | suma a `balance` |
| `requireCanTrade()` | `TradingNotAllowedException` si el rol no es `USER` |
| `requireCanAccessAccountOf(userId)` | `AccountAccessDeniedException` si no es el dueño ni `ADMIN` |
| `requireRegisteredBy(day)` | `DateBeforeRegistrationException` si `day` es anterior al día de `registeredAt` en hora de Argentina; sin `registeredAt` no se aplica |

Fábricas: se suman `register(username, email, rawPassword, initialBalance, hasher, registeredAt)`,
`createAdmin(username, email, rawPassword, hasher, registeredAt)` y
`reconstitute(id, username, email, passwordHash, apiKeyHash, role, balance, reserved, registeredAt)`. Las actuales
se conservan y delegan con `reserved = 0` y `registeredAt = null` (D5 y D6).

Ejemplo (US4.3 y US4.4): 1000,00 de total; reserva de 10,30 → libre 989,70. Una venta de 4 a 1,0500 se paga con la
reserva (4,20; quedan 6,10 reservados) y la reserva del remanente se ajusta a `6 × 1,0300 = 6,18`, tomando 0,08 del
libre → total 995,80, reservado 6,18, libre 989,62.

### 3.2 `quote`

`Quote` no cambia. `QuoteRepository` suma `findInForceAt(playerId, instant)` y `findAtOf(playerIds, exclusiveEnd)`
(sección 6.6). `QuoteResponse` y `PlayerQuoteResponse` suman `score` (D25).

### 3.3 `sync`

`SyncWriteService.applyLeague` llama a `InitialOrderService.openFor(ids de los jugadores guardados)` antes de
devolver, en la misma transacción (D8). No hay cambios en `modelo/sync/`.

## 4. Excepciones de dominio

Todas con nombre; extienden las bases de `shared/` y el advice único las traduce (Principio III).

| Excepción | Paquete | Base | Status | Mensaje (ejemplo) |
|---|---|---|---|---|
| `InsufficientCreditsException` | `modelo/market/exception` | `ConflictException` | 409 | "Saldo insuficiente: se necesitan 10,30 créditos y el saldo libre es 5,00." |
| `InsufficientTokensException` | ídem | `ConflictException` | 409 | "Tokens insuficientes: tiene 4 tokens libres de este jugador." |
| `PlayerNotTradableException` | ídem | `ConflictException` | 409 | "El jugador no se puede operar: está inactivo." / "...todavía no tiene cotización." / "...todavía no tiene su orden inicial." |
| `SaleBlockedByInitialOrderException` | ídem | `ConflictException` | 409 | "Todavía no se puede vender: al administrador le quedan 3 tokens de este jugador." |
| `OppositeOrderPendingException` | ídem | `ConflictException` | 409 | "Ya tiene una orden de compra pendiente de este jugador." |
| `OrderNotCancellableException` | ídem | `ConflictException` | 409 | "La orden ya está ejecutada." / "...ya está cancelada." |
| `MarketBusyException` | ídem | `ConflictException` | 409 | "El mercado está ocupado. Vuelva a intentar en unos segundos." |
| `OrderNotFoundException` | ídem | `NotFoundException` | 404 | "No se encontró la orden con id 77." |
| `OrderNotOwnedException` | ídem | `ForbiddenException` | 403 | "La orden no pertenece al usuario." |
| `TradingNotAllowedException` | ídem | `ForbiddenException` | 403 | "El administrador no opera en el mercado." |
| `MarketInvariantException` | ídem | `RuntimeException` | 500 | invariante interna; si salta es un error de programación (como `QuoteInvariantException`) |
| `UserNotFoundException` | `modelo/user/exception` | `NotFoundException` | 404 | "No se encontró el usuario con id 999." |
| `AccountAccessDeniedException` | ídem | `ForbiddenException` | 403 | "No tiene permiso para ver la cuenta de otro usuario." |
| `DateBeforeRegistrationException` | ídem | `BadRequestException` | 400 | "La fecha 2026-08-01 es anterior al registro del usuario (2026-08-17)." |

Se reutilizan `PlayerNotFoundException` (404), `InvalidQuoteDateException`, `FutureQuoteDateException`,
`InvalidDateRangeException` (400) y `CatalogInvariantException` (400, filtro de equipo vacío).

`shared/ForbiddenException` es nueva: base sin tipos de Spring, como las otras cuatro; `GlobalExceptionHandler` la
traduce a 403 con `ApiError`.

## 5. Transiciones de estado

### 5.1 Una orden

```text
              fill(n < pending)            fill(n = pending)
  PENDING ───────────────────► PARTIAL ───────────────────► EXECUTED
     │  └──────────── fill(n = pending) ─────────────────────▲
     │                           │
     │ cancelRemainder           │ cancelRemainder / cancelBeyond
     ▼                           ▼
 CANCELLED (executed = 0)    CANCELLED (executed > 0)
```

- Solo `PENDING` y `PARTIAL` se cancelan; `EXECUTED` y `CANCELLED` son finales (FR-035).
- La inicial nunca se cancela (FR-010).
- No vencen (FR-021).

### 5.2 Una tenencia

`quantity` sube al comprar y baja al vender; `committed` sube al crear una venta y baja al ejecutarla o cancelarla.
Al llegar `quantity` a 0, `CostBasis` se reinicia (US2.4). La fila se conserva.

### 5.3 Una cuenta

| Hecho | `balance` (total) | `reserved` | libre |
|---|---|---|---|
| crear una compra (lo necesario `N`) | = | + `N` | − `N` |
| ejecutar `n` de esa compra por `T` | − `T` | − `T`, y se ajusta al remanente (D10, D11) | según el ajuste |
| cobrar una venta por `T` | + `T` | = | + `T` |
| cancelar una compra con reserva `R` | = | − `R` | + `R` |

## 6. Persistencia

Nombres de tabla con `@Table(name = ...)`, índices con nombre, sin SQL nativo (Principio VI). Las cantidades son
`INTEGER`; los créditos `NUMERIC(19,2)` (como `app_user.balance`); los precios por token `NUMERIC(10,4)`; los
enums `VARCHAR` traducidos por el mapper (D17).

### 6.1 `market_orders` (`MarketOrderSQL`)

| Columna | Tipo | Notas |
|---|---|---|
| `id` | `BIGINT` identidad | |
| `side` | `VARCHAR(4)` | `BUY`, `SELL` |
| `owner_id` | `BIGINT` FK `app_user` | `@ManyToOne(LAZY)`, no actualizable |
| `player_id` | `BIGINT` FK `players` | ídem |
| `requested_quantity` | `INTEGER` | no actualizable |
| `executed_quantity` | `INTEGER` | |
| `cancelled_quantity` | `INTEGER` | |
| `status` | `VARCHAR(10)` | copia del derivado, para filtrar |
| `reserved_credits` | `NUMERIC(19,2)` | 0 en ventas |
| `reservation_price` | `NUMERIC(10,4)` nullable | |
| `created_at` | `TIMESTAMP` | no actualizable |
| `initial_player_id` | `BIGINT` nullable | igual a `player_id` en la inicial; nulo en el resto |
| `cancel_reason` | `VARCHAR(20)` nullable | |

Índices: `ix_market_orders_book (player_id, side, status, created_at)`, `ix_market_orders_status_created (status,
created_at)`, `ix_market_orders_owner_created (owner_id, created_at DESC)`, `ux_market_orders_initial_player
(initial_player_id)` único.

### 6.2 `holdings` (`HoldingSQL`)

| Columna | Tipo | Notas |
|---|---|---|
| `id` | `BIGINT` identidad | |
| `owner_id` | `BIGINT` FK `app_user` | |
| `player_id` | `BIGINT` FK `players` | |
| `quantity` | `INTEGER` | |
| `committed_quantity` | `INTEGER` | |
| `acquired_quantity` | `INTEGER` | `CostBasis` |
| `acquired_cost` | `NUMERIC(19,2)` | `CostBasis` |

Índice: `ux_holdings_owner_player (owner_id, player_id)` único.

### 6.3 `market_trades` (`TradeSQL`, `@Immutable`, sin setters, columnas `updatable = false`)

| Columna | Tipo |
|---|---|
| `id` | `BIGINT` identidad |
| `executed_at` | `TIMESTAMP` |
| `player_id` | `BIGINT` FK `players` |
| `buy_order_id`, `sell_order_id` | `BIGINT` FK `market_orders` |
| `buyer_id`, `seller_id` | `BIGINT` FK `app_user` |
| `seller_type` | `VARCHAR(5)` |
| `quantity` | `INTEGER` |
| `token_price` | `NUMERIC(10,4)` |
| `total` | `NUMERIC(19,2)` |
| `quote_id` | `BIGINT` FK `quotes` |
| `buyer_balance_before`, `buyer_balance_after`, `seller_balance_before`, `seller_balance_after` | `NUMERIC(19,2)` |
| `buyer_tokens_before`, `buyer_tokens_after`, `seller_tokens_before`, `seller_tokens_after` | `INTEGER` |
| `realized_profit` | `NUMERIC(19,2)` nullable |

Índices: `ix_market_trades_buyer_executed (buyer_id, executed_at DESC)`, `ix_market_trades_seller_executed
(seller_id, executed_at DESC)`.

### 6.4 `audit_entries` (`AuditEntrySQL`, `@Immutable`, sin setters, columnas `updatable = false`)

| Columna | Tipo |
|---|---|
| `id` | `BIGINT` identidad |
| `occurred_at` | `TIMESTAMP` |
| `event_type` | `VARCHAR(20)` |
| `actor_type` | `VARCHAR(10)` |
| `actor_id` | `BIGINT` nullable (sin FK: el autor puede ser el sistema) |
| `player_id` | `BIGINT` |
| `order_id` | `BIGINT` |
| `trade_id` | `BIGINT` nullable |
| `side` | `VARCHAR(4)` |
| `quantity` | `INTEGER` |
| `token_price` | `NUMERIC(10,4)` nullable |
| `total` | `NUMERIC(19,2)` nullable |
| `quote_id` | `BIGINT` nullable |
| `cancel_reason` | `VARCHAR(20)` nullable |
| `detail` | `VARCHAR(500)` |
| `primary_user_id`, `primary_free_before`, `primary_free_after`, `primary_reserved_before`, `primary_reserved_after`, `primary_tokens_before`, `primary_tokens_after` | `@Embedded AuditPartySQL` con `@AttributeOverrides` |
| `secondary_user_id`, `secondary_...` (las mismas siete) | ídem, nullable |

Índices: `ix_audit_entries_occurred (occurred_at DESC)`, `ix_audit_entries_primary_user (primary_user_id,
occurred_at)`, `ix_audit_entries_secondary_user (secondary_user_id, occurred_at)`, `ix_audit_entries_player
(player_id, occurred_at)`.

Los ids de la auditoría (`player_id`, `order_id`, `trade_id`, `quote_id`, usuarios) son columnas simples, sin FK: un
registro de auditoría describe un hecho y no tiene que impedir nada en las demás tablas.

### 6.5 `app_user` (`AppUserSQL`, cambia)

| Columna nueva | Tipo | Notas |
|---|---|---|
| `reserved_balance` | `NUMERIC(19,2) DEFAULT 0 NOT NULL` | `@ColumnDefault("0.00")`, para `ddl-auto: update` sobre una base existente |
| `registered_at` | `TIMESTAMP` nullable | no actualizable |

### 6.6 Consultas (derived o JPQL, sin `nativeQuery`)

| DAO | Método | Qué hace |
|---|---|---|
| `MarketOrderSQLDAO` | `findByInitialPlayerId(playerId)` con `@Lock(PESSIMISTIC_WRITE)` | el candado del libro (D13) |
| | `findPlayerIdById(orderId)` (JPQL escalar) | el jugador de una orden sin cargarla |
| | `findById` (sin bloqueo propio: el candado del libro ya serializa sus cambios) | la orden a cancelar, leída después de bloquear el libro |
| | `findOpenOfPlayer(playerId)` (JPQL: `initialPlayerId is null`, `status in (PENDING, PARTIAL)`, `order by createdAt, id`) | el libro de usuarios |
| | `findPlayerIdsWithoutInitialOrder()` y `...In(ids)` (JPQL con `not exists`) | órdenes iniciales a crear (D8) |
| | `findMarket(filtros, Pageable)` (JPQL con `join fetch` de jugador y equipo, filtros nulos que no filtran, `order by createdAt, id`) + conteo | el listado (D22) |
| | `findByOwnerId(ownerId, Pageable)` (`order by createdAt desc, id desc`, con jugador) | órdenes propias |
| | `findInitialOrdersOf(ownerId)` (JPQL, `initialPlayerId is not null`, con jugador y equipo) | las iniciales del superusuario para su portfolio |
| `HoldingSQLDAO` | `findByPlayerIdAndOwnerIdIn(playerId, ownerIds)` | las tenencias del libro |
| | `findPositions(ownerId)` (JPQL con `join fetch` de jugador y equipo, `quantity > 0`) | el portfolio actual |
| `TradeSQLDAO` | `findHistory(userId, playerId, side, from, to, Pageable)` (JPQL, `buyer.id = :u or seller.id = :u`, `join fetch` de jugador, equipo, cotización y estrategia, `order by executedAt desc, id desc`) + conteo | el historial (D21) |
| | `findOfUserBefore(userId, end)` (`order by executedAt, id`) | la reconstrucción a una fecha |
| | `sumRealizedProfitOfSeller(userId)` | la realizada del portfolio actual |
| | `findLastOfUserBefore(userId, end)` y `findFirstOfUserAtOrAfter(userId, end)` (JPQL con una hoja de tamaño 1) | el saldo a una fecha (D20) |
| `AuditEntrySQLDAO` | `findAudit(filtros, Pageable)` (JPQL, `order by occurredAt desc, id desc`) + conteo | la consulta de auditoría |
| `AppUserSQLDAO` (cambia) | `findWithLockById(id)` con `@Lock(PESSIMISTIC_WRITE)` | las cuentas, de a una y en orden ascendente |
| | `findFirstByRoleOrderByIdAsc(role)` | el superusuario |
| `QuoteSQLDAO` (cambia) | `findInForceAt(playerId, instant)` (JPQL, `max(validFrom) where validFrom <= :instant`) | el precio de una operación (D14) |
| | `findLastBeforeOf(playerIds, end)` (JPQL, por jugador la de mayor vigencia anterior a `end`) | la valuación a una fecha (D20) |

Las consultas con `@Lock` son de una sola tabla y sin `join fetch`. Las de lectura con `join fetch` son
`readOnly`.

### 6.7 Repositories

En `persistence/repository/market/`: `MarketOrderRepository`, `HoldingRepository`, `TradeRepository` (solo
`saveAll` y lecturas) y `AuditRepository` (solo `saveAll` y lecturas). Envuelven al DAO, invocan al mapper,
reciben y devuelven modelo, y traducen `PessimisticLockingFailureException` a `MarketBusyException` (D13).
`AppUserRepository` suma `lockById(id)`, `lockAllInOrder(ids)` (ordena y bloquea de a una) y `findSuperuser()`.
`QuoteRepository` suma `findInForceAt` y `findAtOf`.

## 7. Invariantes y dónde se verifican

| Invariante | Dónde se decide | Dónde se prueba |
|---|---|---|
| 1 ≤ cantidad ≤ 100, entera | DTO (forma) y `MarketOrder` (invariante) | `OrderControllerIT`, `MarketOrderTest` |
| pendiente de la inicial + Σ tenencias = 100 | `PlayerBook` (mueve los tokens de a pares) | `MarketOrderServiceIT`, `MarketConcurrencyIT` |
| libre ≥ 0, reservado ≥ 0 | `AppUser` | `AppUserMarketTest`, IT |
| Σ saldos = Σ saldos iniciales | `PlayerBook` (cada `Trade` paga y cobra el mismo total) | IT |
| `reserved` = Σ `reservedCredits` de sus compras abiertas | `PlayerBook` | IT |
| `committed` = Σ pendientes de sus ventas abiertas | `Holding` y `PlayerBook` | IT |
| una sola inicial por jugador | índice único y bloqueo del superusuario | `InitialOrderIT` |
| el libro no tiene pendientes de los dos lados | `PlayerBook` | `PlayerBookTest` |
| una operación y la auditoría no se modifican | `@Immutable`, sin setters, repositories sin update ni delete | `TradeRepositoryIT`, `AuditRepositoryIT` |

## 8. Trazabilidad: dónde se decide cada regla

| Requisito | Clase |
|---|---|
| FR-001, FR-002 | `MarketMath.TOKENS_PER_PLAYER`, `Quote.tokenPrice()`, `OrderMatcher` (1,0000 contra la inicial) |
| FR-003, FR-004 | `MarketMath.total`; `Trade` (un total para las dos partes); sin comisión |
| FR-005, FR-006 | `AppUser` (saldo inicial de la 001, `reserve`, `payFromReserve`, `receive`, invariantes) |
| FR-007 a FR-009 | `MarketOrder.initialFor`, `InitialOrderService`, `InitialOrderStartup`, `SyncWriteService` |
| FR-010 | `SecurityConfig` (`POST /orders/**` solo `USER`), `AppUser.requireCanTrade`, `MarketOrder.requireOwnedBy` |
| FR-011 | `PlayerBook.placeSell` → `SaleBlockedByInitialOrderException` |
| FR-012 | `InitialOrderService` (sin superusuario, advierte y no crea) y `PlayerBook.isTradable` |
| FR-013 | controllers (`@AuthenticationPrincipal`) |
| FR-014 | `OrderRequest` y `MarketOrder` |
| FR-015, FR-016 | `PlayerBook` con `AppUser.reserve` y `Holding.commit` |
| FR-017 | `PlayerBook` → `OppositeOrderPendingException`; `OrderMatcher` saltea las propias |
| FR-018, FR-031 a FR-033 | `MarketOrderService` (404) y `PlayerBook.isTradable` (409) |
| FR-019 a FR-021 | `MarketOrder` |
| FR-022 a FR-024 | `OrderMatcher` |
| FR-025 | `MarketOrderService` (`@Transactional`) y el bloqueo de D13 |
| FR-026 | `PlayerBook` |
| FR-027 | `MarketMath.maxAffordable`, `OrderMatcher` (`shortfalls`), `PlayerBook` (D11) |
| FR-028, FR-029 | `Trade`, `Holding.sell`, `CostBasis.dispose` |
| FR-030 | `QuoteRepository.findInForceAt`; el mercado no usa adapters |
| FR-034, FR-035 | `PlayerBook.cancel`, `MarketOrder.cancelRemainder`, `MarketOrder.requireOwnedBy` |
| FR-036 a FR-039 | `Portfolio`, `PortfolioPosition`, `CostBasis` |
| FR-040, FR-041 | `TradeReplay`, `Portfolio.at`, `QuoteDay`, `AppUser.requireRegisteredBy` |
| FR-042, FR-046 | `AppUser.requireCanAccessAccountOf` |
| FR-043 a FR-045 | `TradeFilter`, `TradeView`, `MarketQueryService` |
| FR-047 a FR-049 | `MarketOrderFilter`, `MarketQueryService`, `MarketOrderResponse` (sin el dueño) |
| FR-050 a FR-053 | `AuditEntry`, `AuditRepository`, `AuditQueryService`, `SecurityConfig` |
| FR-054 a FR-056 | controllers, DTO, `SecurityConfig`, `GlobalExceptionHandler` |
| FR-057 | `ProfileResponse` (puerta G3, cumplida) |
| FR-058 | `QuoteResponse`, `PlayerQuoteResponse` |
| FR-059, FR-060 | tests de D28 |
| FR-061 a FR-072 | frontend (sección 10) |

## 9. Respuestas HTTP (resumen; el contrato completo está en `contracts/`)

En `controller/market/dto/`, con los nombres de los esquemas del contrato:

| DTO | Esquema | Forma |
|---|---|---|
| `OrderRequest` | `OrderRequest` | `playerId`, `quantity` (D23) |
| `PlayerRefResponse` | `PlayerRef` | `id`, `name`, `team`, `league` |
| `MarketOrderResponse` / `MarketOrderPageResponse` | `MarketOrder` / `MarketOrderPage` | listado: `id`, `player`, `side`, `ownerType`, cantidades pedida, ejecutada y pendiente, `status`, `createdAt`; sin dueño |
| `UserOrderResponse` / `UserOrderPageResponse` | `UserOrder` / `UserOrderPage` | `MarketOrder` más `cancelledQuantity` y `reservedCredits`; la usan las órdenes propias, la ejecución y la cancelación |
| `QuoteUsedResponse` | `QuoteUsed` | `validFrom`, `value`, `tokenPrice`, `score`, `strategy` (`StrategyRefResponse` de la 007) |
| `TradeSummaryResponse` | `TradeSummary` | `id`, `executedAt`, `quantity`, `tokenPrice`, `total`, `counterparty`, `quote` |
| `OrderExecutionResponse` | `OrderExecution` | `order`, `trades`, `executedQuantity`, `executedTotal`, `balance`, `availableBalance`, `reservedBalance` |
| `OrderCancellationResponse` | `OrderCancellation` | `order`, `balance`, `availableBalance`, `reservedBalance` |
| `PortfolioResponse`, `PortfolioPositionResponse`, `PortfolioTotalsResponse` | `Portfolio`, `PortfolioPosition`, `PortfolioTotals` | D19 y D20 |
| `TransactionResponse` / `TransactionPageResponse` | `Transaction` / `TransactionPage` | historial (D21) |
| `AuditEntryResponse`, `AuditPartyResponse` / `AuditPageResponse` | `AuditEntry`, `AuditParty` / `AuditPage` | auditoría (D18) |

Cambian: `ProfileResponse` (+ `availableBalance`, `reservedBalance`; puerta G3, cumplida), `QuoteResponse` y
`PlayerQuoteResponse` (+ `score`).

## 10. Tipos del frontend (`frontend/src/types/market.ts`)

Escritos a mano desde `contracts/market-api.yaml`; los enums tal cual el backend.

```ts
export const ORDER_SIDES = ['BUY', 'SELL'] as const;
export const ORDER_STATUSES = ['PENDING', 'PARTIAL', 'EXECUTED', 'CANCELLED'] as const;
export const OWNER_TYPES = ['USER', 'ADMIN'] as const;

interface PlayerRef { id: number; name: string; team: string; league: League }

interface MarketOrder {            // listado público
  id: number; player: PlayerRef; side: OrderSide; ownerType: OwnerType; status: OrderStatus;
  requestedQuantity: number; executedQuantity: number; pendingQuantity: number; createdAt: string;
}
interface UserOrder extends MarketOrder { cancelledQuantity: number; reservedCredits: number }
interface QuoteUsed { validFrom: string; value: number; tokenPrice: number; score: number; strategy: StrategyRef }
interface TradeSummary { id: number; executedAt: string; quantity: number; tokenPrice: number; total: number;
                         counterparty: OwnerType; quote: QuoteUsed }
interface OrderExecution { order: UserOrder; trades: TradeSummary[]; executedQuantity: number;
                           executedTotal: number; availableBalance: number; reservedBalance: number; balance: number }
// PortfolioPosition y no Position, que ya es la posición del jugador en types/api.ts
interface PortfolioPosition { player: PlayerRef & { position: Position; active: boolean }; tokens: number;
                     sharePercent: number; committed?: number; averagePrice: number | null; invested: number | null;
                     quote: QuoteUsed; value: number; profit: number | null; profitPercent: number | null }
interface PortfolioTotals { balance: number; availableBalance?: number; reservedBalance?: number; invested: number;
                            value: number; profit: number; realizedProfit: number | null; equity: number }
interface Portfolio { userId: number; date: string | null; positions: PortfolioPosition[]; totals: PortfolioTotals }
interface Transaction { id: number; executedAt: string; player: PlayerRef; side: OrderSide; quantity: number;
                        tokenPrice: number; total: number; counterparty: OwnerType; orderId: number;
                        quote: QuoteUsed; realizedProfit?: number | null }
// Páginas con PageInfo de types/quotes.ts; queries con los mismos nombres que la dirección (D27)
```

Cambian: `Profile` (`types/api.ts`) suma `availableBalance` y `reservedBalance`; `Quote` (`types/quotes.ts`) suma
`score?: number` (opcional a propósito, D27) y el comentario del encabezado.

Tipos propios de la interfaz: `MarketFilters` (`side`, `league`, `team`, `ownerType`, `page`), `TransactionFilters`
(`player`, `side`, `from`, `to`, `page`), `TradeEstimate` (cantidad a 1,0000, cantidad al precio vigente, total
estimado) y `TradeBlock` (`'not-tradable' | 'sale-blocked' | null`, con el motivo).

## 11. Configuración

| Clave | `application.yaml` | `application-test.yml` | Para qué |
|---|---|---|---|
| `futbolmarket.market.initial-orders.automatic` | `true` | `false` | generar las órdenes iniciales al arrancar y al sincronizar (D8) |

`config/MarketProperties` (`record` con `@ConfigurationProperties("futbolmarket.market")`) se registra en
`ApplicationConfig`. La URL de H2 de `application-local.yml` suma `;LOCK_TIMEOUT=10000` (D13); los IT del mercado lo
ponen en su `@TestPropertySource`. No se agrega ninguna otra clave.
