# Investigación: Mercado de tokens y portfolio de usuarios

**Feature**: `008-mercado-tokens-portfolio` | **Fecha**: 2026-10-28 | **Plan**: [plan.md](./plan.md)

Cada decisión sigue el formato **Decisión / Por qué / Alternativas descartadas**. Las decisiones que
el pedido de planificación ya traía resueltas se adoptan tal cual y acá se fija el detalle; las que el
plan resuelve distinto del pedido están marcadas con **(distinto del pedido)** y resumidas en D31.

El estado del código se relevó el 2026-10-28 sobre la rama `008-mercado-tokens-portfolio`, que parte de
`8e99cd6` (la 005, la 006 y la 007 integradas). `develop` tiene un commit más (`cf0a0dd`: exclusiones de
Sonar y `ScheduleValidation`), que hay que traer antes de implementar (puerta G1 del plan).

## Índice

- [D1. Base de partida y dependencias](#d1-base-de-partida-y-dependencias)
- [D2. Contextos, paquetes y status codes](#d2-contextos-paquetes-y-status-codes)
- [D3. Nombres del modelo](#d3-nombres-del-modelo)
- [D4. Aritmética, escalas y redondeo](#d4-aritmética-escalas-y-redondeo)
- [D5. El saldo: total, reservado y libre](#d5-el-saldo-total-reservado-y-libre)
- [D6. La fecha de registro](#d6-la-fecha-de-registro)
- [D7. El superusuario y su tenencia](#d7-el-superusuario-y-su-tenencia)
- [D8. Órdenes iniciales](#d8-órdenes-iniciales)
- [D9. Órdenes: estados, cantidades y reserva](#d9-órdenes-estados-cantidades-y-reserva)
- [D10. El cruce: `OrderMatcher` y `PlayerBook`](#d10-el-cruce-ordermatcher-y-playerbook)
- [D11. Compra que se ejecuta a un precio mayor que su reserva](#d11-compra-que-se-ejecuta-a-un-precio-mayor-que-su-reserva)
- [D12. Tenencia, precio promedio y ganancia realizada](#d12-tenencia-precio-promedio-y-ganancia-realizada)
- [D13. Transacción y concurrencia: la decisión de bloqueo](#d13-transacción-y-concurrencia-la-decisión-de-bloqueo)
- [D14. Precio vigente y semanas a medias](#d14-precio-vigente-y-semanas-a-medias)
- [D15. Jugadores no operables y reactivación](#d15-jugadores-no-operables-y-reactivación)
- [D16. Cancelación](#d16-cancelación)
- [D17. Persistencia](#d17-persistencia)
- [D18. Auditoría](#d18-auditoría)
- [D19. Portfolio actual](#d19-portfolio-actual)
- [D20. Portfolio a una fecha](#d20-portfolio-a-una-fecha)
- [D21. Historial y órdenes propias](#d21-historial-y-órdenes-propias)
- [D22. Listado del mercado](#d22-listado-del-mercado)
- [D23. API: rutas, cuerpos y validación](#d23-api-rutas-cuerpos-y-validación)
- [D24. Seguridad y propiedad de los recursos](#d24-seguridad-y-propiedad-de-los-recursos)
- [D25. Score visible](#d25-score-visible)
- [D26. Caché](#d26-caché)
- [D27. Frontend](#d27-frontend)
- [D28. Tests existentes que cambian (Principio IV)](#d28-tests-existentes-que-cambian-principio-iv)
- [D29. Estrategia de tests](#d29-estrategia-de-tests)
- [D30. Riesgos conocidos](#d30-riesgos-conocidos)
- [D31. Lo que el plan resuelve distinto del pedido y puntos a confirmar](#d31-lo-que-el-plan-resuelve-distinto-del-pedido-y-puntos-a-confirmar)

---

## D1. Base de partida y dependencias

**Decisión**: se implementa sobre `develop` con la 005, la 006 y la 007 integradas (después de traer
`cf0a0dd`). **No se agrega ninguna dependencia**, ni en el backend ni en el frontend.

Lo que se usa y ya está en el classpath:

| Pieza | De dónde sale |
|---|---|
| `@Lock(LockModeType.PESSIMISTIC_WRITE)` sobre derived queries | `spring-data-jpa` (starter `data-jpa`) |
| `@Immutable`, `@ColumnDefault` | `hibernate-core` (starter `data-jpa`) |
| `ApplicationReadyEvent`, `@Async`, `@EventListener` | `spring-boot` y `spring-context`, ya habilitados en `ApplicationConfig` |
| `Clock` | bean de `ApplicationConfig` |
| `ExecutorService` y `CountDownLatch` para los tests de concurrencia | JDK |
| Diálogo de confirmación, tablas, filtros | React y CSS Modules, sin librerías |

**Por qué**: el stack es fijo (constitución, Stack Tecnológico) y nada de lo que pide la feature lo
excede.

**Alternativas descartadas**: `spring-retry` para reintentar con `@Version` (D13); una librería de
diálogos o de tablas en el frontend.

## D2. Contextos, paquetes y status codes

**Decisión**:

- **Contexto nuevo `market`** (sustantivo del dominio, en inglés y en singular), en las cuatro capas donde
  tiene clases: `modelo/market/` (con `exception/`), `service/market/`, `controller/market/` (con `dto/`) y
  `persistence/{repository,mapper,sql/entity,sql/interfaces}/market/`. No hay `adapter/`: el mercado no
  consulta fuentes externas (FR-030).
- **Contextos que se tocan**: `user` (saldo reservado, fecha de registro, permiso sobre cuentas ajenas),
  `quote` (exponer el score y dos consultas nuevas), `sync` (generar las órdenes iniciales de los jugadores
  nuevos) y `auth` (el reloj del registro). `player` **no cambia**: el mercado lee jugadores por
  `PlayerRepository`, que ya alcanza.
- **No se crea ningún paquete de primer nivel ni carpeta fuera del árbol** de la constitución.
- **Status codes**: 400, 401, 403, 404 y 409, todos de la base del Principio III. Para el 403 de dominio
  (cuenta ajena, orden ajena) se agrega la base `shared/ForbiddenException` y su handler en
  `GlobalExceptionHandler`. **No suma un status code nuevo**: hoy el 403 lo emite solo Spring Security
  (`JsonAccessDeniedHandler`), con el mismo formato `ApiError`. Las respuestas de éxito usan 200 y 201 (el
  201 ya existe en `POST /auth/register`).

**Por qué**: Principio I (contexto nuevo declarado en el plan, sin carpetas vacías) y Principio III (un
único advice, excepciones con nombre).

**Alternativas descartadas**: contextos separados `order`, `portfolio` y `audit` (partirían un único
agregado y obligarían a que los servicios se llamen en cadena); resolver la propiedad de las cuentas solo en
`SecurityConfig` con una regla por variable de ruta (sirve para `/users/{id}/**`, pero no para cancelar una
orden ajena, que necesita cargar la orden: el 403 de dominio hace falta igual, ver D24).

## D3. Nombres del modelo

**Decisión**: `MarketOrder`, `Holding`, `Trade` y `AuditEntry`, con sus contrapartes `MarketOrderSQL`,
`HoldingSQL`, `TradeSQL` y `AuditEntrySQL`; y `Portfolio` con `PortfolioPosition`. El pedido las nombra
`Order`, `Holding`, `Trade`, `AuditEntry`, `Portfolio` y `Position`.

**Por qué**: `Order` choca de lectura con `@Order` de Spring, con `Sort.Order` de Spring Data y con el
`order by` de las consultas JPQL que la nombran; `MarketOrder` no es ambiguo en ninguna capa. `Position` ya es
el enum de la posición del jugador (`modelo/position/Position`), que el portfolio usa en la misma clase. Los
demás nombres se conservan.

**Alternativas descartadas**: `Order` y `OrderSQL` (posible, pero con imports calificados en persistencia).

## D4. Aritmética, escalas y redondeo

**Decisión**: todo con `BigDecimal` y `RoundingMode.HALF_UP`, con las escalas como constantes con nombre en
una clase de utilidad del modelo, `MarketMath`:

| Magnitud | Escala | Regla |
|---|---|---|
| Precio por token | 4 | `Quote.tokenPrice()` de la 007 (valor / 100, exacto). Contra la orden inicial, `1.0000` fijo |
| Total de una operación | 2 | `cantidad × precio por token`, **un solo redondeo** (FR-003). El comprador paga y el vendedor recibe exactamente ese total |
| Reserva de una compra pendiente | 2 | `pendiente × precio de reserva`, con el mismo redondeo que un total (FR-019) |
| Precio promedio de compra | 4 | `costo adquirido / tokens adquiridos` desde que la tenencia quedó en 0 (FR-037, D12) |
| Invertido, valor, ganancia, ganancia realizada | 2 | `tokens × precio` redondeado; ganancia = valor − invertido |
| Rentabilidad | 2 | `ganancia / invertido × 100`; sin invertido (0) no se informa |
| Participación | entero | `tokens` (cada token es el 1 %) |
| Score | 6 | el de la 007, sin tocar |

Ejemplos que fijan los tests de dominio: `3 × 1,0333 = 3,0999 → 3,10`; `10 × 1,0345 = 10,345 → 10,35`;
`15,15 / 15 = 1,0100`; `5,25 − 5 × 1,0100 = +0,20`.

"La mayor cantidad que alcanza" (FR-027) es la mayor cantidad entera `n` cuyo total redondeado no supera lo
disponible. Se calcula con `n = floor(disponible / precio)` y, si el redondeo de `n × precio` se pasa (solo
puede pasar por medio centavo), `n − 1`. Es exacto y no recorre cantidades.

**Por qué**: Principio V (sin números mágicos) y SC-005 (los ejemplos se reproducen con 0 diferencias).

**Alternativas descartadas**: `double` (no reproduce los ejemplos); redondear el precio y después el total
(dos redondeos, viola FR-003).

## D5. El saldo: total, reservado y libre

**Decisión**: `AppUser` conserva `balance` como **saldo total** (lo que tiene el usuario: libre +
reservado) y suma **`reserved`** como columna (`app_user.reserved_balance`). El saldo libre se deriva:
`available = balance − reserved`. La invariante del modelo es `reserved ≥ 0` y `available ≥ 0` después de
cada operación (FR-006).

Métodos nuevos de `AppUser`, sin setters:

| Método | Qué hace | Invariante |
|---|---|---|
| `reserve(amount)` | pasa créditos de libre a reservado | lanza `InsufficientCreditsException` si `available < amount` |
| `release(amount)` | devuelve créditos reservados al libre | `amount ≤ reserved` |
| `payFromReserve(amount)` | resta del total y del reservado (paga una operación con su reserva) | `amount ≤ reserved` |
| `receive(amount)` | suma al total (cobra una venta) | `amount > 0` |
| `availableBalance()` | `balance − reserved` | — |
| `requireCanTrade()` | solo `USER` opera; el superusuario solo cobra (FR-010) | lanza `TradingNotAllowedException` (403) |

El total cambia **solo** con operaciones (cobrar o pagar); reservar y liberar no lo mueven. Por eso la
conservación de créditos (SC-003) es simplemente `Σ balance = Σ saldos iniciales`.

**Por qué columna y no derivado**:

- Cada pedido necesita el libre de varias cuentas a la vez (comprador y dueños de compras pendientes,
  FR-027). Con la columna, bloquear la fila del usuario (D13) protege todo lo que hace falta para decidir;
  derivado, habría que sumar las reservas de sus órdenes pendientes de **todos** los jugadores dentro de la
  transacción, y bloquearlas para que no cambien, lo que rompe la serialización por jugador.
- El modelo puede verificar la invariante solo (`AppUser` tiene los dos números). Derivado, `AppUser` no
  sabría su libre sin recibir las órdenes.
- El perfil (FR-057), la barra superior (FR-062) y el portfolio lo leen sin agregar.

El riesgo de una columna redundante (que no coincida con la suma de las reservas de las órdenes) se cubre con
un test de integración que verifica, después de cada escenario, `reserved = Σ reservedCredits` de las compras
pendientes del usuario.

**Compatibilidad**: la columna se declara `nullable = false` con `@ColumnDefault("0.00")`, así
`ddl-auto: update` la agrega en una base local existente con 0 para las cuentas viejas (H2 y PostgreSQL
aceptan `ADD COLUMN ... DEFAULT 0 NOT NULL`). Se conservan las fábricas actuales de `AppUser`
(`reconstitute` de 7 argumentos y `register` de 5), que usan tests de la 001 que no se modifican
(Principio IV); delegan en las nuevas con reservado 0 y sin fecha de registro.

**Alternativas descartadas**: reservado derivado de las órdenes pendientes (arriba); guardar libre y
reservado en dos columnas y dejar de guardar el total (cambia el significado de `balance` de la 001, que
el perfil ya informa y un test fija).

## D6. La fecha de registro

**Decisión**: `AppUser` suma **`registeredAt`** (`app_user.registered_at`, un `Instant`), que fijan las dos
fábricas de alta con el instante del `Clock`:

- `AuthService.register` pasa `clock.instant()` a una sobrecarga nueva de `AppUser.register`. `AuthService`
  suma un constructor con `Clock` (el que usa Spring, marcado con `@Autowired`) y **conserva el de cuatro
  argumentos**, que delega con `Clock.systemUTC()`: lo usan `AuthServiceTest` (líneas 63 y 145), que no se
  modifica.
- `AdminAccountInitializer` suma el `Clock` a sus campos (`@RequiredArgsConstructor`; ningún test lo
  construye a mano) y lo pasa a `AppUser.createAdmin`.

La columna es **nullable**: las cuentas creadas antes de esta feature quedan sin fecha y para ellas **no se
aplica el límite inferior** de FR-041 (no tienen operaciones anteriores a la 008, así que el portfolio de
cualquier fecha previa es su saldo, sin posiciones). La regla vive en el modelo:
`AppUser.requireRegisteredBy(LocalDate day)` lanza `DateBeforeRegistrationException` (400) si el día es
anterior al día de registro en hora de Argentina.

**Por qué**: FR-041 quedó confirmado en la clarificación ("anterior al registro se rechaza"), así que no se
propone ajustar la regla. El instante sale del mismo `Clock` que el resto del mercado, y los tests de
integración lo controlan con `MutableClock` (registrar usuarios "en agosto" para el ejemplo del portfolio a
una fecha).

**Alternativas descartadas**:

- `@CreationTimestamp` de Hibernate: pisa el valor al insertar, así que los tests no pueden registrar
  usuarios en el pasado, y usa la hora de la JVM, no el `Clock`.
- Auditoría de Spring Data (`@CreatedDate` con un `DateTimeProvider` sobre el `Clock`): funciona, pero
  agrega `@EnableJpaAuditing` y un listener para un solo campo, y deja un dato del dominio fijado por la
  persistencia.
- Cambiar el constructor de `AuthService` y pedir el "sí" para tocar `AuthServiceTest`: más limpio, pero
  suma una puerta que no hace falta.
- Ajustar la regla a "anterior a la primera operación": contradice FR-041 confirmado.

## D7. El superusuario y su tenencia

**Decisión**:

- El **superusuario es la primera cuenta `ADMIN`** (la de menor id): la del alta de arranque de la 001. Se
  busca con `AppUserRepository.findSuperuser()` (derived query `findFirstByRoleOrderByIdAsc`). No se usa el
  nombre de usuario configurado, porque si la configuración cambia la cuenta original sigue siendo la dueña.
- **Su tenencia de un jugador es el pendiente de su orden inicial**. No tiene filas en `holdings`. La
  invariante FR-026 queda: `pendiente de la orden inicial + Σ tenencias de usuarios = 100` para cada jugador
  con orden inicial.
- **Solo cobra**: vende por su orden inicial y recibe créditos (`AppUser.receive`); no reserva, no paga y no
  crea órdenes (`requireCanTrade`, D5). Sus órdenes no se cancelan (D16).
- **Sin superusuario** (FR-012, la 001 no lo crea si falta su contraseña): el generador de órdenes
  iniciales escribe la advertencia en el registro y termina; ningún jugador tiene orden inicial, así que
  ninguno es operable (D15). Cuando el superusuario exista (siempre al arrancar, por la 001), el próximo
  arranque genera todas.
- Como titular (el administrador pide su propio portfolio o historial), se informa como cualquier
  titular: posiciones con los tokens sin vender de cada jugador y su valor, y su saldo. Como no compró,
  sus posiciones no tienen precio promedio, invertido, ganancia ni rentabilidad, y sus ventas no registran
  ganancia realizada (D12 y D19).

**Por qué**: el pedido (punto 4) y FR-007 a FR-012. No duplicar la tenencia evita que dos filas tengan que
moverse juntas.

**Alternativas descartadas**: una fila en `holdings` para el superusuario (duplica el pendiente de la orden
inicial y obliga a mantener los dos iguales).

## D8. Órdenes iniciales

**Decisión**: `service/market/InitialOrderService`, idempotente, con dos entradas:

- `openMissing()`: crea la orden inicial de **cada jugador del catálogo que no la tiene** (activo o no:
  FR-007 dice "cada jugador del catálogo"; uno inactivo la tiene visible pero no se ejecuta, D15).
- `openFor(Collection<Long> playerIds)`: lo mismo, acotado a esos jugadores.

Cada orden inicial es una `MarketOrder` de venta del superusuario por 100 tokens, marcada como inicial, con
su entrada de auditoría `ORDER_CREATED` y el superusuario como autor (US9.1). El precio de 1,0000 no se
guarda en la orden: lo aplica el cruce por ser inicial (FR-002).

**Cuándo corre** (FR-008):

- **Al arrancar**: `service/market/InitialOrderStartup`, con `@Async` y `@EventListener(ApplicationReadyEvent)`,
  el patrón de `StartupSync` y `QuoteStartupRecalculation`. El alta del administrador
  (`AdminAccountInitializer`, un `ApplicationRunner`) corre antes de ese evento, así que el superusuario ya
  existe si la configuración está completa.
- **Al sincronizar el catálogo**: dentro de `SyncWriteService.applyLeague`, en la misma transacción de la
  liga, con los jugadores que acaba de guardar. Un jugador nuevo y su orden inicial se confirman juntos: nunca
  hay un jugador del catálogo sin orden fuera del caso sin superusuario.

**Por qué en `SyncWriteService` y no en `SyncService`**: `SyncServiceTest`, `SyncServiceLastFullSyncTest` y
`SyncCoordinatorTest` construyen `SyncService` y `SyncCoordinator` a mano, y uno verifica
`verifyNoInteractions(writeService)` con las cinco ligas fallidas. Sumarles un colaborador obliga a cambiar esos
tests. `SyncWriteService` lo inyecta Spring en sus IT y lo mockea Mockito en los unitarios, así que sumarle
`InitialOrderService` no toca ningún test. `service.sync → service.market` no forma ciclo: el mercado no
depende de la sincronización.

**Sin duplicados**:

- **Restricción de unicidad**: columna `market_orders.initial_player_id`, igual al jugador en la orden inicial
  y nula en las demás, con índice único `ux_market_orders_initial_player`. H2 y PostgreSQL admiten varios
  nulos en un índice único, así que no hace falta un índice parcial (que exigiría SQL nativo).
- **Serialización**: las dos entradas bloquean primero la fila del superusuario (`PESSIMISTIC_WRITE`, D13) y
  recién después buscan los jugadores sin orden. Así el generador del arranque y la escritura de una liga que
  corren a la vez (la puesta al día del arranque de la 005) no ven los dos al mismo jugador sin orden: el
  segundo espera, y al seguir ya ve la del primero.
- Reiniciar no crea nada: la consulta "jugadores sin orden inicial" devuelve vacío.

**Propiedad `futbolmarket.market.initial-orders.automatic`** (nueva, `config/MarketProperties`): `true` en
`application.yaml` y `false` en `application-test.yml`. Con `false`, las dos entradas automáticas no hacen nada
(el servicio sigue disponible para llamarlo directo). Es el mismo criterio que `sync.on-startup` y
`quotes.recalculation.on-startup` en el perfil test, y hace falta por una razón concreta: `SyncControllerIT` y
`StatsSyncControllerIT` configuran el administrador, sincronizan y después borran `players` con
`playerDAO.deleteAll()`; con órdenes iniciales apuntando a esos jugadores, el borrado fallaría por la clave
foránea. Los IT del mercado la activan con `@TestPropertySource`.

**Volumen**: el primer arranque con el catálogo completo crea unas 2.600 órdenes y 2.600 entradas de auditoría
en una transacción. Con `IDENTITY` Hibernate no agrupa los inserts; en H2 local son unos segundos y corre en
segundo plano. Se mide en el IT de volumen (D29). Mientras corre, los jugadores que todavía no tienen orden
no son operables (409), que es lo que pide el caso de borde.

**Alternativas descartadas**: llamar al generador desde `SyncService.execute` o `SyncCoordinator` (cambia tests
existentes); una tabla aparte `initial_orders` para la unicidad (duplica la orden); un índice único parcial
(`WHERE initial`), que no se puede declarar sin SQL nativo; correr el generador de forma síncrona al arrancar
(demora el arranque y no aporta: el bloqueo del superusuario ya serializa).

## D9. Órdenes: estados, cantidades y reserva

**Decisión**: `MarketOrder` guarda tres cantidades y deriva el resto:

- `requested` (1 a 100), `executed` y `cancelled`; **`pending = requested − executed − cancelled`**.
- **Estado** (FR-020), derivado de las cantidades y guardado también como columna para filtrar:

| Estado | Condición |
|---|---|
| `PENDING` | `executed = 0` y `pending > 0` |
| `PARTIAL` | `executed > 0` y `pending > 0` |
| `EXECUTED` | `pending = 0` y `cancelled = 0` |
| `CANCELLED` | `pending = 0` y `cancelled > 0` (con o sin parte ejecutada) |

- **Reserva** (solo compras): `reservedCredits` (lo reservado hoy para el remanente) y `reservationPrice`
  (el precio por token vigente al crearla, FR-019). Con remanente normal,
  `reservedCredits = pendiente × reservationPrice` redondeado; la excepción es D11.
- `createdAt` (del `Clock`), `side` (`BUY` o `SELL`), dueño, jugador y la marca de inicial
  (`initialPlayerId`, D8). El tipo de usuario dueño (`OwnerType`: `USER` o `ADMIN`) se deriva de la marca: solo
  el superusuario tiene órdenes, y son las iniciales.
- **No vencen** (FR-021): no hay fecha de vencimiento ni tarea que las cierre.

Transiciones que hace el modelo: `fill(n)` (suma a ejecutado), `cancelRemainder(reason)` (todo el pendiente
pasa a cancelado) y `cancelBeyond(n)` (cancela lo que queda después de ejecutar `n`, FR-027).

**Por qué**: con las tres cantidades el estado nunca contradice los números y "cancelada con parte ejecutada"
se distingue de "ejecutada" sin columnas extra.

**Alternativas descartadas**: guardar `pending` como columna (un dato derivable más que puede desincronizarse).

## D10. El cruce: `OrderMatcher` y `PlayerBook`

**Decisión**: la decisión de qué se cruza es un objeto de dominio puro, **`OrderMatcher`**, y la aplicación de
ese cruce sobre créditos, tokens y órdenes la hace el agregado **`PlayerBook`** (el libro de un jugador durante
un pedido). Los dos están en `modelo/market/`, sin Spring.

`OrderMatcher.match(incoming, initialOrder, opposite, price)` recibe la orden entrante, la orden inicial del
jugador (solo cuenta para compras), las órdenes pendientes del otro lado y el precio por token vigente, y
devuelve un `MatchPlan`: la lista de `Fill` (contraparte, cantidad, precio por token y total) y el remanente.
Reglas:

1. **Compra**: primero la orden inicial a `1.0000`, hasta agotarla (FR-022). Después las ventas pendientes de
   usuarios, **de la más vieja a la más nueva** (`createdAt` y después `id`), al precio vigente. Lo que falta
   queda pendiente.
2. **Venta**: si la orden inicial del jugador tiene pendiente, **se rechaza** con
   `SaleBlockedByInitialOrderException` (409, informa cuántos tokens le quedan al superusuario; FR-011) y no se
   crea nada. Si no, se cruza con las compras pendientes de la más vieja a la más nueva, al precio vigente
   (FR-023).
3. **Órdenes propias nunca se cruzan** (FR-017): el matcher salta las del mismo dueño. Con la regla de
   abajo nunca aparecen, pero el matcher no depende de eso.
4. **Determinista**: el orden es total (`createdAt`, `id`) y no depende del orden en que llegan las listas.

`PlayerBook` se arma con todo lo que el servicio cargó y bloqueó (D13): el jugador, la cotización vigente, la
orden inicial, las pendientes del otro lado, las cuentas involucradas y las tenencias del jugador. Expone
`placeBuy(owner, quantity, at)`, `placeSell(owner, quantity, at)` y `cancel(order, requester, at)`, y devuelve un
`BookOutcome`: la orden, las operaciones (`Trade`), las órdenes, cuentas y tenencias que cambiaron y las
entradas de auditoría, en el orden en que pasaron.

Validaciones, en este orden (cada una con su excepción con nombre, D23):

| Compra | Venta |
|---|---|
| el dueño puede operar (`requireCanTrade`) | ídem |
| el jugador es operable (D15) | ídem |
| no tiene ventas pendientes de ese jugador (FR-017) | la orden inicial no tiene pendiente (FR-011) |
| el libre alcanza para la parte inmediata + la reserva del remanente (FR-015) | no tiene compras pendientes de ese jugador (FR-017) |
| | la cantidad no supera sus tokens libres: tenencia − comprometidos (FR-016) |

Cómo se aplica una **compra** (todo o nada, FR-015 y supuesto del spec):

1. `OrderMatcher` arma el plan. Lo necesario es `Σ totales de los Fill + reserva(remanente × precio vigente)`.
2. Si el libre no alcanza, `InsufficientCreditsException` y **no cambia nada** (ni orden, ni auditoría).
3. Se crea la orden con `reservedCredits` = lo necesario y `reservationPrice` = el precio vigente: el comprador
   reserva todo de una vez (auditoría `ORDER_CREATED`).
4. Cada `Fill` paga su total **desde la reserva de la orden** (`payFromReserve`), el vendedor lo cobra, los
   tokens pasan de una tenencia a otra (o salen del pendiente de la inicial) y queda un `Trade` con su
   auditoría `TRADE_EXECUTED`.
5. Al terminar, la reserva que queda es exactamente `remanente × reservationPrice`, porque lo necesario se
   calculó así.

Cómo se aplica una **venta**: se crea la orden y se comprometen sus tokens (`ORDER_CREATED`); por cada compra
pendiente, en orden, se calcula cuánto puede pagar su dueño (D11) y se ejecuta eso; una compra que no puede
pagar ni un token se cancela entera por saldo insuficiente (`ORDER_CANCELLED`, autor el sistema) y la venta
sigue con la próxima (caso de borde del spec).

**El libro nunca tiene pendientes de los dos lados**: una compra queda pendiente solo si no había ventas; una
venta, solo si no había compras; y las ventas están bloqueadas mientras la inicial tiene pendiente. Un test de
dominio lo fija. Por eso al reactivarse un jugador no hay nada que cruzar (D15).

**Por qué**: Principio II. El servicio carga, bloquea, llama a `placeBuy` o `placeSell` y guarda lo que
devuelve; no tiene un `if` de negocio. Separar el plan (puro, fácil de probar con los ejemplos del spec) de su
aplicación (estado de cuentas y tenencias) mantiene los métodos cortos (Principio V).

**Alternativas descartadas**: un único método que planifica y aplica a la vez (más difícil de probar y con
complejidad cognitiva alta); que la compra reserve todo al precio vigente y pague después (rechazaría a quien
tiene justo para comprar al superusuario a 1,0000).

## D11. Compra que se ejecuta a un precio mayor que su reserva

**Decisión**: al cruzar una venta con una compra pendiente, lo **disponible para esa compra** es su reserva más
el libre de su dueño (nunca la reserva de otras órdenes, FR-027). Se ejecuta la mayor cantidad entera que
alcanza (D4), hasta lo que pide la venta. Si alcanza para menos que lo que la compra tenía pendiente **y** que
lo que la venta ofrece, el resto de la compra se cancela por saldo insuficiente y se libera su reserva
(ejemplo del spec: 10,30 reservados, libre 0, precio 1,1000 → 9 tokens por 9,90, 1 cancelado, 0,40 liberados).
Si el precio es menor, la diferencia vuelve al libre sola, porque la reserva se recalcula.

**Hueco del spec que el plan cierra** (punto a confirmar 2): FR-027 no dice qué pasa cuando la compra **puede
pagar** lo que se ejecuta, pero después no le alcanza para mantener la reserva completa del remanente. Ejemplo:
10,30 reservados para 10 a 1,0300, libre 0, precio 1,1000 y una venta de 4: paga 4,40 y le quedan 5,90, pero la
reserva del remanente de 6 debería ser 6,18. Reservar 6,18 dejaría el libre en −0,28, y FR-006 lo prohíbe.

El plan decide: **la reserva del remanente es `min(pendiente × reservationPrice, reserva + libre − pagado)`**.
En el ejemplo queda en 5,90 y el libre en 0. El remanente sigue pendiente; la próxima vez que se cruce, la regla
de arriba vuelve a calcular cuánto puede pagar. Así nunca hay saldo negativo y no se cancela nada que se pueda
pagar.

**Alternativas descartadas**: cancelar también la parte del remanente que no se puede reservar completa (5 a
1,0300 = 5,15, cancelar 1): cancela tokens que la compra todavía podría pagar más adelante, y FR-027 solo cancela
lo que no alcanza para ejecutar; dejar el libre negativo (viola FR-006).

## D12. Tenencia, precio promedio y ganancia realizada

**Decisión**: `Holding` (una por titular y jugador, solo usuarios) guarda `quantity`, `committed` (tokens en
ventas pendientes, FR-016) y un **`CostBasis`**: los tokens adquiridos y lo pagado **desde la última vez que la
tenencia quedó en 0**. El precio promedio se deriva: `costo adquirido / tokens adquiridos`, con 4 decimales.

- **Comprar** `n` por `T`: `quantity += n`, `acquiredQuantity += n`, `acquiredCost += T`.
- **Vender** `n` por `T`: `quantity −= n`, `committed −= n`; ganancia realizada =
  `T − n × precio promedio`, a 2 decimales (FR-029). El costo adquirido **no cambia** (el promedio no cambia al
  vender, FR-037).
- **Al llegar a 0**: se reinician los acumuladores; la próxima compra arranca un promedio nuevo (US2.4). La
  fila se conserva con 0 y el portfolio la omite.

`CostBasis` es el mismo objeto que usa la reconstrucción a una fecha (D20), así las dos vías no pueden calcular
distinto y no se duplica código.

**Por qué acumuladores y no una columna `average_price`** (distinto del pedido): FR-037 define el promedio como
"totales de las compras / tokens comprados". Con una sola columna redondeada a 4 decimales, cada compra nueva
tendría que recalcular desde el promedio redondeado y el error se acumula. Con los dos acumuladores el promedio
sale exacto en cada momento. El ejemplo del spec lo muestra: 10 a 1,0000 y 5 a 1,0300 dan 1,0100 (15,15 / 15);
al vender 5 a 1,0500 quedan 10 a 1,0100, invertido 10,10 y realizada +0,20.

El superusuario no tiene `Holding` (D7): sus ventas no registran ganancia realizada.

**Alternativas descartadas**: guardar el promedio y recalcularlo como promedio ponderado del remanente (no es la
definición de FR-037: daría 1,0233 y no 1,0200 en una tercera compra de 5 a 1,0500 después de vender).

## D13. Transacción y concurrencia: la decisión de bloqueo

**Decisión**: **bloqueo pesimista** (`@Lock(LockModeType.PESSIMISTIC_WRITE)` sobre derived queries, que en H2
y en PostgreSQL es `SELECT ... FOR UPDATE`), dentro de **un único método `@Transactional` por pedido**
(`MarketOrderService.buy`, `sell` y `cancel`), que valida, cruza, mueve créditos y tokens, actualiza órdenes y
tenencias y escribe operaciones y auditoría: todo o nada (FR-025).

**Qué se bloquea y en qué orden** (siempre el mismo, para que no haya interbloqueos):

1. **El libro del jugador**: la fila de su **orden inicial** (`MarketOrderRepository.lockBook(playerId)`, sobre `findByInitialPlayerId` con `@Lock`). Todo lo que
   cambia el libro de un jugador (comprar, vender, cancelar una orden suya) toma primero este candado, así que
   los pedidos de un mismo jugador se procesan uno detrás del otro. Si el jugador no tiene orden inicial, no es
   operable (D15) y no hay nada que serializar. Para cancelar, el jugador de la orden se lee antes con una
   consulta escalar (`select o.player.id ...`) que no carga la orden en el contexto de persistencia.
2. **Las cuentas involucradas, en orden ascendente de id, de a una**: el que pide, el superusuario si la orden
   inicial tiene pendiente (compras) y los dueños de **todas** las órdenes pendientes del otro lado del libro.
   Se bloquean todas porque cuáles se usan depende del cruce (una compra sin saldo se cancela y la venta sigue
   con la siguiente). En la práctica son pocas. Se piden de a una (`AppUserRepository.lockById`) para que el orden de
   adquisición sea el del código y no el del plan de ejecución de la base.
3. **El resto se lee después, sin bloqueo propio**: órdenes pendientes y tenencias del jugador. Solo cambian con
   el candado del libro tomado, así que ya están serializadas.

**Regla: se bloquea antes de leer.** Una entidad que ya está en el contexto de persistencia no se refresca al
bloquearla después; por eso el servicio no carga ninguna cuenta ni orden antes de su bloqueo. El usuario del
pedido llega como id desde el principal (el filtro de autenticación lo cargó en otra transacción, que ya
terminó: `open-in-view` es `false`).

**Por qué no hay interbloqueo**: cada transacción toma un solo candado de libro, y lo toma antes que cualquier
cuenta; las cuentas se toman siempre en orden ascendente. Una transacción que espera una cuenta nunca tiene
otra transacción esperándola por un libro que ella tenga y que esa otra necesite.

**Qué garantiza**:

- Dos pedidos por los últimos tokens del superusuario (US4.12): el segundo espera el candado del libro, lee el
  pendiente ya confirmado y queda pendiente. Sin estados intermedios.
- Dos pedidos del mismo usuario sobre jugadores distintos (caso de borde): los dos bloquean su cuenta; el
  segundo ve el saldo que dejó el primero y, si no le alcanza, se rechaza. Nunca hay saldo negativo.
- Saldo, tenencia, disponibilidad y precio se leen **de la base, dentro de la transacción**, nunca del caché
  (D14 y D26).

**Tiempo de espera**: H2 espera un candado 1 s por defecto (`LOCK_TIMEOUT=1000`). Con 20 o 50 pedidos sobre el
mismo jugador el último puede esperar más. Se sube a 10 s con `;LOCK_TIMEOUT=10000` en la URL de H2 del perfil
`local` y de los IT del mercado (en PostgreSQL el equivalente es `lock_timeout`). Si aun así vence, la
`PessimisticLockingFailureException` se traduce en el repository a `MarketBusyException` (409, "el mercado está
ocupado, vuelva a intentar"), con el patrón de `AppUserRepository.translate`. Nunca sale un 500.

**El superusuario es un punto caliente**: toda compra contra una orden inicial bloquea su cuenta, así que esas
compras se serializan entre todos los jugadores. Cada transacción dura milisegundos y el volumen del trabajo
práctico es bajo; se mide en el IT de concurrencia (D29). Si molestara, la salida es acreditarle con un
`update ... set balance = balance + :monto` atómico en JPQL, sin bloquear su fila.

**Alternativa descartada: `@Version` con reintento**:

- La cuenta del superusuario cambia en casi todas las compras iniciales: con optimista, la mayoría de los
  pedidos concurrentes fallaría y reintentaría.
- El reintento tiene que estar **fuera** de la transacción (otro bean o un bucle propio) porque una transacción
  que perdió la carrera queda marcada para rollback; `spring-retry` sería una dependencia nueva.
- Los tests de concurrencia serían no deterministas (cuántos reintentos hubo).
- Con pesimista el resultado es el mismo con menos código y sin reintentos.

**Otras descartadas**: bloquear la fila del jugador (`players`) como candado del libro (la sincronización
actualiza esas filas y bloquearía las operaciones durante la escritura de una liga); un candado en memoria por
jugador (no sirve con más de una instancia y no protege las cuentas, que son de todos los jugadores); aislamiento
`SERIALIZABLE` (H2 lo soporta mal y PostgreSQL resolvería con reintentos, como el optimista).

## D14. Precio vigente y semanas a medias

**Decisión**: el precio de una operación entre usuarios es `tokenPrice()` de **la última cotización del jugador
con vigencia anterior o igual al instante de la operación**, leída de la base dentro de la transacción con un
método nuevo, `QuoteRepository.findInForceAt(playerId, instant)` (JPQL, `validFrom <= :instant`). El instante
sale del `Clock` y es el mismo para todo el pedido. Contra la orden inicial el precio es 1,0000, y la operación
registra igual la cotización vigente como referencia (FR-028).

**Semana a medias** (FR-030, US4.13): la 007 escribe cada semana en **una** transacción
(`QuoteWeekWriter`) y H2 corre con `READ COMMITTED`, así que una lectura dentro de una operación ve la semana
entera o nada de ella. No hace falta coordinar con `RecalculationLock`: el mercado no espera al recálculo y usa
la última completa, que es lo que pide el spec.

**Por qué un método nuevo y no `findCurrent`**: `findCurrent` devuelve la de mayor vigencia sin mirar el
instante. En producción coincide, porque ninguna cotización rige desde el futuro, pero los tests corren con un
`MutableClock` en fechas pasadas (el ejemplo del 20/08) y tienen que ver el precio de ese momento.

**Alternativas descartadas**: `QuoteQueryService.getCurrent` (pasa por el caché, D26); `findAt` con
`instant.plusNanos(1)` (correcto pero oscuro).

## D15. Jugadores no operables y reactivación

**Decisión**: `PlayerBook` decide la operabilidad (FR-018 y FR-031). Un jugador **no es operable** si está
inactivo, si no tiene cotización a ese instante o si no tiene orden inicial; el pedido se rechaza con
`PlayerNotTradableException` (409), con el motivo en el mensaje. Un jugador **inexistente** es 404
(`PlayerNotFoundException` de la 002), y eso lo resuelve el servicio antes de armar el libro.

Las órdenes pendientes de un jugador no operable no se ejecutan, porque las órdenes solo se cruzan cuando llega
una nueva y las nuevas se rechazan. Se pueden cancelar (FR-032, D16).

**Reactivación** (FR-033): al reactivarse o recibir la primera cotización, las órdenes quedan como estaban. No
hace falta un proceso que las cruce: el libro nunca tiene pendientes de los dos lados (D10), así que no hay nada
que cruzar hasta que llegue un pedido nuevo, que las cruza.

**Alternativas descartadas**: un listener del recálculo o de la sincronización que intente cruzar al reactivar
(no encontraría nada que cruzar y acoplaría la 005 y la 007 al mercado).

## D16. Cancelación

**Decisión**: `POST /orders/{id}/cancel`, solo rol `USER`. El servicio lee el jugador de la orden (consulta
escalar), bloquea el libro y la cuenta del dueño, y delega en `PlayerBook.cancel(order, requester, at)`:

- orden inexistente → `OrderNotFoundException` (404);
- orden ajena **o inicial** (del superusuario) → `OrderNotOwnedException` (403, `ForbiddenException`);
- orden `EXECUTED` o `CANCELLED` → `OrderNotCancellableException` (409);
- si no, `cancelRemainder(USER_REQUEST)`: lo ejecutado se conserva; una compra libera su reserva al libre; una
  venta libera sus tokens comprometidos. Auditoría `ORDER_CANCELLED` con el usuario como autor.

Se puede cancelar aunque el jugador no sea operable (FR-032). El administrador recibe 403 antes del controller
por la regla de `POST /orders/**` (D24), así que sus órdenes iniciales nunca se cancelan (FR-010).

**Por qué `POST .../cancel` y no `DELETE /orders/{id}`**: la orden no se borra; queda cancelada con su historia
(y la auditoría no admite borrados). Un `DELETE` sugeriría lo contrario.

**Alternativas descartadas**: 404 para una orden ajena, para no revelar que existe (las órdenes ya son públicas
en el listado del mercado, con su id, así que no hay nada que ocultar; y FR-056 pide distinguir falta de
permiso).

## D17. Persistencia

**Decisión**: cuatro tablas nuevas y dos columnas en `app_user`, con nombres e índices explícitos (Principio
VI). Detalle de columnas en [data-model.md](./data-model.md), sección 6.

| Tabla | Clase | Escritura |
|---|---|---|
| `market_orders` | `MarketOrderSQL` | inserta y actualiza (cantidades, estado, reserva) |
| `holdings` | `HoldingSQL` | inserta y actualiza |
| `market_trades` | `TradeSQL` | **solo inserta** |
| `audit_entries` | `AuditEntrySQL` (con dos `@Embedded AuditPartySQL`) | **solo inserta** |

Índices (los del pedido, más uno):

| Índice | Columnas | Para qué |
|---|---|---|
| `ix_market_orders_book` | `player_id, side, status, created_at` | el cruce y el listado filtrado por jugador |
| `ix_market_orders_status_created` | `status, created_at` | el listado sin filtro de jugador (agregado: el anterior no sirve sin `player_id`) |
| `ix_market_orders_owner_created` | `owner_id, created_at DESC` | las órdenes propias |
| `ux_market_orders_initial_player` | `initial_player_id` (único) | una sola orden inicial por jugador (D8) |
| `ux_holdings_owner_player` | `owner_id, player_id` (único) | una tenencia por titular y jugador; su primera columna sirve al portfolio |
| `ix_market_trades_buyer_executed` | `buyer_id, executed_at DESC` | historial |
| `ix_market_trades_seller_executed` | `seller_id, executed_at DESC` | historial y ganancia realizada |
| `ix_audit_entries_occurred` | `occurred_at DESC` | auditoría sin filtros |
| `ix_audit_entries_primary_user` / `_secondary_user` | `primary_user_id, occurred_at` / `secondary_user_id, occurred_at` | filtro por usuario |
| `ix_audit_entries_player` | `player_id, occurred_at` | filtro por jugador |

Reglas:

- **Nombres que evitan palabras reservadas**: `market_orders` (no `order`), `market_trades` (no
  `transaction`), `quantity`, `side`, `status` (no reservadas en H2 2.x ni PostgreSQL).
- **Relaciones**: `@ManyToOne(fetch = LAZY)` a `AppUserSQL`, `PlayerSQL`, `MarketOrderSQL` y `QuoteSQL`, con
  claves foráneas. Las cotizaciones son inmutables (007), así que una operación puede apuntar a la suya.
- **Enums como texto**: columnas `VARCHAR` con largo fijo, traducidas por el mapper, como `AppUserSQL.role`.
  Con `@Enumerated`, Hibernate usa el `ENUM` nativo de H2, y `ddl-auto: update` no lo altera si mañana se suma un
  valor (un tipo de evento de auditoría, por ejemplo).
- **Inmutables**: `TradeSQL` y `AuditEntrySQL` llevan `@Immutable` de Hibernate, sin setters y con todas las
  columnas `updatable = false`. `TradeRepository` y `AuditRepository` no tienen ningún método de actualizar ni
  de borrar, como `QuoteRepository` (FR-051 y SC-011).
- **Sin SQL nativo**: derived queries, JPQL y `@Lock`. Las consultas con `FOR UPDATE` son de una sola tabla y
  sin `join fetch` (H2 no admite `FOR UPDATE` con ciertas construcciones y no hace falta).
- **`app_user`**: `reserved_balance NUMERIC(19,2) DEFAULT 0 NOT NULL` y `registered_at TIMESTAMP` (nullable),
  D5 y D6.
- **Paginación**: se reutiliza `QuotePage<T>` (genérica, de `modelo/quote/`, con la regla de `PlayerPage`)
  para no duplicar el cálculo de hojas.

**Alternativas descartadas**: `@Enumerated` (arriba); claves sin foráneas (perderían integridad; el problema de
los tests que borran `players` se resuelve con la propiedad de D8); `@ElementCollection` para las partes de la
auditoría (una tabla más, consultas con `distinct` y paginación en memoria para filtrar por usuario: con dos
partes como máximo, dos `@Embedded` alcanzan).

## D18. Auditoría

**Decisión**: `AuditEntry` (inmutable, `record`) registra cada **orden creada** (también las iniciales), cada
**operación ejecutada** y cada **cancelación**, en la **misma transacción** que el hecho (FR-050 a FR-052):

- `eventType`: `ORDER_CREATED`, `TRADE_EXECUTED` u `ORDER_CANCELLED`.
- Autor: `actorType` (`USER`, `SUPERUSER` o `SYSTEM`) y `actorId` (nulo para `SYSTEM`). El usuario que hizo el
  pedido; el superusuario en las órdenes iniciales; el sistema en las cancelaciones por saldo insuficiente.
- `occurredAt`, `playerId`, `orderId`, `tradeId` (solo en operaciones), `cancelReason` (`USER_REQUEST` o
  `INSUFFICIENT_CREDITS`) y los datos del hecho: lado, cantidad, precio por token, total y cotización usada.
- **Estado anterior y posterior de cada parte** (una o dos): saldo libre, saldo reservado y tenencia del
  jugador (para el superusuario, el pendiente de su orden inicial). En una operación, la primera parte es el
  comprador y la segunda el vendedor; en los demás eventos, solo el dueño.
- `detail`: una frase en español que arma el modelo ("Compra de 10 tokens de Harry Kane a 1,0000 por 10,00
  contra la orden inicial").

Un pedido rechazado lanza su excepción **antes de escribir**, y cualquier falla posterior revierte la transacción
entera: nunca queda una orden creada sin su auditoría ni una auditoría sin su hecho (US9.6).

Consulta: `GET /audit`, solo `ADMIN`, paginada de la más nueva a la más vieja, con filtros opcionales de tipo de
evento, usuario (autor o cualquiera de las partes), jugador y rango de fechas (FR-053).

**Por qué**: FR-050 pide autor, fecha, detalle y estados anterior y posterior. Datos estructurados más una frase
permiten filtrar y leer sin reconstruir nada.

**Alternativas descartadas**: un JSON libre en una columna de texto (no se puede filtrar ni indexar sin SQL
nativo); auditar con un listener de JPA (no sabe el motivo ni el autor, y no ve los rechazos).

## D19. Portfolio actual

**Decisión**: `PortfolioService.current(requester, userId)`, `readOnly`:

1. La cuenta pedida (404 si no existe) y el permiso (D24).
2. Las tenencias del usuario con cantidad > 0, con el jugador y su equipo en la misma consulta (`join fetch`).
3. La **cotización vigente de esos jugadores en una consulta**: `QuoteRepository.findCurrentOf(ids)`, que ya
   existe (la de mayor vigencia, también la de un jugador inactivo, FR-039). Sin N+1.
4. La ganancia realizada acumulada: una suma JPQL de `realizedProfit` de sus ventas.
5. `Portfolio.current(account, holdings, quotes, realized)` calcula cada `PortfolioPosition` (tokens, participación,
   comprometidos, precio promedio, invertido, precio por token y score, valor, ganancia y rentabilidad) y los
   totales (libre, reservado, invertido, valor, ganancia, realizada y patrimonio = libre + reservado + valor).

Para el superusuario las posiciones salen de sus órdenes iniciales con pendiente > 0 y sin datos de costo (D7).

**No se guarda** (Key Entities). **No consulta fuentes externas** (US2.10, SC-012).

**(distinto del pedido)** El pedido dice usar `QuoteQueryService.getCurrentOf` "que ya existe y usa el caché".
Ese método **no está cacheado** (su comentario lo dice) y **rechaza más de 50 jugadores**, y un portfolio puede
tener más. El plan llama directo a `QuoteRepository.findCurrentOf`, que es la misma consulta sin el tope: una sola
consulta por portfolio, sin caché nuevo.

**Alternativas descartadas**: `getCurrent` por jugador (N+1, aunque esté cacheado); partir los ids en lotes de 50
para pasar por `getCurrentOf` (más consultas sin ninguna ventaja).

## D20. Portfolio a una fecha

**Decisión**: `PortfolioService.at(requester, userId, date)`, `readOnly`:

1. La fecha se valida en este orden, todas con 400 (FR-041): formato (`QuoteDay.parse`,
   `InvalidQuoteDateException`), futura (`QuoteDay.requireNotFuture`, `FutureQuoteDateException`) y anterior al
   registro (`AppUser.requireRegisteredBy`, `DateBeforeRegistrationException`, D6). Se reutilizan las
   excepciones de la 007 porque sus mensajes no nombran la cotización.
2. El final del día es `QuoteDay.endOf(day)` (el 00:00 del día siguiente en hora de Argentina, excluido): una
   operación a las 23:30 de ese día entra (US7.3).
3. Las operaciones del usuario con `executedAt` anterior a ese instante, de la más vieja a la más nueva, en una
   consulta.
4. **`TradeReplay`** (dominio puro) las reproduce con `CostBasis` (D12): por jugador, cantidad, precio promedio e
   invertido; en total, la ganancia realizada. Para el superusuario, cada orden inicial creada antes del final
   del día cuenta como 100 tokens y cada venta los resta, sin datos de costo ni ganancia realizada (D7).
5. La valuación usa **la cotización que regía al final de ese día** para cada jugador, en una consulta por lote
   nueva: `QuoteRepository.findAtOf(ids, exclusiveEnd)` (por jugador, la de mayor vigencia anterior al instante:
   la misma regla que `findAt` de la 007, FR-043).
6. **Saldo a la fecha**: el saldo posterior (comprador o vendedor, según el lado del usuario) de su última
   operación anterior al final del día; si no tiene ninguna anterior, el saldo previo de la primera posterior; si
   no tiene ninguna operación, su saldo actual. Es correcto porque el total solo cambia con operaciones (D5).
7. La respuesta informa solo el **saldo total** (sin libre, reservado ni comprometidos) y el patrimonio = saldo +
   valor de las posiciones (FR-040).

El portfolio a la fecha de hoy se calcula igual (supuesto del spec): toma todas las operaciones y la cotización
vigente al final del día.

Ejemplo del spec que fija el test (US7.1 y US7.2): compras de 10 el 20/08 y de 5 el 26/08, cotizaciones de
100,00 desde el 17/08, 103,00 desde el 24/08 y 101,00 desde el 31/08 → al 25/08, 10 tokens a 1,0300 (10,30); al
01/09, 15 tokens a 1,0100 (15,15).

**Alternativas descartadas**: guardar fotos diarias del portfolio (más datos, un proceso diario y nada que no se
pueda reconstruir); valuar con `findAt` por jugador (N+1).

## D21. Historial y órdenes propias

**Decisión**:

- `GET /users/{id}/transactions` (FR-043 y FR-044): las operaciones donde el usuario es comprador o vendedor, de
  la más nueva a la más vieja (`executedAt` desc, `id` desc), con la paginación del catálogo y filtros
  opcionales y combinables: `playerId`, `side` (`BUY` o `SELL`, desde el punto de vista del usuario), `from` y
  `to` (fechas AAAA-MM-DD en hora de Argentina, los dos extremos incluidos; un rango invertido es 400 con
  `InvalidDateRangeException`). Cada operación informa fecha y hora, jugador, lado, cantidad, precio por token,
  total, **contraparte** (`ADMIN` o `USER`, nunca quién), la orden propia, la cotización usada (vigencia, score,
  estrategia y versión) y, en las ventas, la ganancia realizada. Una consulta JPQL con `join fetch` del jugador,
  su equipo, la cotización y la estrategia.
- `GET /users/{id}/orders` (FR-045): las órdenes del usuario, de la más nueva a la más vieja, paginadas, con
  jugador, lado, estado, cantidades pedida, ejecutada, cancelada y pendiente, reserva (compras) y fecha.

Los dos con el permiso de D24 y el 404 de usuario inexistente.

**Alternativas descartadas**: devolver el id de la contraparte (FR-044 y SC-013 lo prohíben); un `UNION` de
compras y ventas (HQL lo admite, pero la paginación sobre la unión complica el conteo; un `or` sobre los dos
índices alcanza a esta escala).

## D22. Listado del mercado

**Decisión**: `GET /orders`, **público** (FR-047 y FR-055), con las órdenes con remanente pendiente
(`PENDING` o `PARTIAL`), **de la más vieja a la más nueva** (`createdAt`, `id`), con la paginación del catálogo
(`page` desde 0; `size` de 1 a 50, 10 por defecto). Filtros opcionales y combinables (FR-048): `side`, `league`,
`team`, `ownerType` (`USER` o `ADMIN`) y `playerId`. Cada orden informa id, jugador, equipo, liga, lado, tipo de
usuario dueño, cantidades pedida, ejecutada y pendiente, estado y fecha. **Nunca** el nombre, el correo ni el id
del dueño (FR-049, SC-013).

Un valor no admitido (`side=X`, `league=X`, `ownerType=X`, `playerId=0`, `team=` vacío) responde 400 con el
mismo mensaje que el catálogo ("Los parámetros enviados no son válidos.").

La ficha del jugador usa este mismo endpoint con `playerId` (FR-064): ve la orden inicial con los tokens que le
quedan y las pendientes de usuarios.

**(distinto del pedido)** El pedido propone un filtro `status` con `PENDING` por defecto. La clarificación del
spec lo descartó: el listado muestra solo las pendientes y parciales y **no ofrece filtro de estado** (FR-047);
las ejecutadas y canceladas se ven en las órdenes propias y en la auditoría.

**Alternativas descartadas**: un endpoint aparte para las órdenes de un jugador (duplicaría el listado).

## D23. API: rutas, cuerpos y validación

**Decisión**: contrato completo en [contracts/market-api.yaml](./contracts/market-api.yaml). Rutas:

| Ruta | Acceso | Respuesta |
|---|---|---|
| `POST /orders/buy` | `USER` | 201 con la orden, sus operaciones, el total ejecutado y el saldo posterior |
| `POST /orders/sell` | `USER` | ídem |
| `POST /orders/{id}/cancel` | `USER` (dueño) | 200 con la orden cancelada y el saldo posterior |
| `GET /orders` | público | 200, hoja del listado |
| `GET /users/{id}/portfolio[?date=AAAA-MM-DD]` | dueño o `ADMIN` | 200, portfolio actual o a esa fecha |
| `GET /users/{id}/transactions` | dueño o `ADMIN` | 200, hoja del historial |
| `GET /users/{id}/orders` | dueño o `ADMIN` | 200, hoja de órdenes propias |
| `GET /audit` | `ADMIN` | 200, hoja de la auditoría |

Las cuatro de la consigna se nombran tal cual (FR-054).

**Cuerpo de compra y venta**: `{ "playerId": 12, "quantity": 10 }`. Validación de forma en el DTO
(Principio III): `playerId` obligatorio y ≥ 1; `quantity` obligatoria, **entera** y entre 1 y 100 (FR-014).
`quantity` se declara `BigDecimal` con `@Digits(integer = 3, fraction = 0)`, `@DecimalMin("1")` y
`@DecimalMax("100")`, porque con un `Integer` Jackson convierte `1.5` en `1` sin avisar
(`ACCEPT_FLOAT_AS_INT`), y US1.9 pide rechazar la cantidad decimal. El controller la pasa a `int` antes de
delegar. Cualquier otro campo del cuerpo (por ejemplo un `userId`) se ignora: **el usuario sale siempre del
principal** (FR-013, US1.10).

**Rechazos** (FR-056), todos con `ApiError` y mensajes en español:

| Status | Casos |
|---|---|
| 400 | cuerpo inválido (con `violations`), filtro o fecha no admitidos, rango invertido, fecha futura o anterior al registro |
| 401 | sin credencial o credencial inválida (Spring Security, ya existente) |
| 403 | administrador en `POST /orders/**`; usuario común en `/audit`; cuenta ajena en `/users/{id}/**`; orden ajena o inicial al cancelar |
| 404 | jugador, usuario u orden inexistentes |
| 409 | saldo insuficiente, tokens insuficientes, venta bloqueada por la orden inicial, órdenes del sentido contrario, jugador no operable, orden ya cerrada, mercado ocupado (D13) |

**Excepciones nuevas** (todas con nombre; ninguna genérica de la JDK): en `modelo/market/exception/`,
`InsufficientCreditsException`, `InsufficientTokensException`, `PlayerNotTradableException`,
`SaleBlockedByInitialOrderException`, `OppositeOrderPendingException`, `OrderNotCancellableException`,
`MarketBusyException` (409), `OrderNotFoundException` (404), `OrderNotOwnedException` y
`TradingNotAllowedException` (403), y `MarketInvariantException` (invariantes internas, como
`QuoteInvariantException`: si salta es un error de programación); en `modelo/user/exception/`,
`UserNotFoundException` (404), `AccountAccessDeniedException` (403) y `DateBeforeRegistrationException` (400).

**Alternativas descartadas**: `Integer quantity` (arriba); configurar `ACCEPT_FLOAT_AS_INT = false` para toda la
aplicación (cambia el comportamiento de los endpoints existentes).

## D24. Seguridad y propiedad de los recursos

**Decisión**, en `SecurityConfig` y en el modelo:

- `GET /orders` (exacto, no `/orders/**`) se suma a `publicEndpoints()`: el filtro de credenciales no lo mira y
  una credencial inválida no lo bloquea, como el ranking.
- `POST /orders/**` → `hasRole(USER)`: el administrador recibe **403 antes del controller** (FR-010, US1.7 y
  SC-007) y un visitante 401.
- `/audit/**` → `hasRole(ADMIN)`.
- `/users/**` queda en `anyRequest().authenticated()` (401 sin credencial). **La propiedad se decide en el
  modelo**: `AppUser.requireCanAccessAccountOf(Long userId)` permite al dueño y al `ADMIN` y si no lanza
  `AccountAccessDeniedException` (403). El servicio la llama **antes** de buscar la cuenta pedida, así un
  usuario común no distingue una cuenta ajena de una inexistente (los dos son 403) y el administrador sí recibe
  el 404 (US2.9).
- El usuario sale del principal (`@AuthenticationPrincipal Long`, como `AccountController`), nunca del cuerpo ni
  de un parámetro.

**Por qué el permiso en el modelo y no en `SecurityConfig`**: el pedido lo pide así, y es la misma regla que la
cancelación de una orden ajena, que necesita cargar la orden y no se puede resolver en la cadena de filtros. Con
las dos en el modelo hay un único lugar para "de quién es esto".

**Alternativas descartadas**: un `AuthorizationManager` con la variable `{id}` de la ruta (D2); `@PreAuthorize`
en los controllers (necesita `@EnableMethodSecurity` y reparte las reglas en dos lugares).

## D25. Score visible

**Decisión** (FR-058, cambia la 007): `QuoteResponse` y `PlayerQuoteResponse` suman **`score`** (`BigDecimal` con
6 decimales, después de `tokenPrice`). Como `RankingRowResponse` y `QuoteHistoryPageResponse` usan
`QuoteResponse`, y el lote usa `PlayerQuoteResponse`, el score aparece en la cotización actual, a una fecha, la
evolución, el ranking y el lote sin tocar nada más. El portfolio y el historial lo informan en su propia forma
(D19 y D21). Se actualiza el comentario de `QuoteResponse` ("a propósito no lleva el score") y
`contracts/quotes-api.yaml` de la 007 pasa a la versión 1.1.0 (`score` obligatorio en `Quote`, descripciones y
ejemplos).

Siguen **sin exponerse** las estadísticas crudas, las métricas normalizadas y el valor base. La simulación de
administración (`SimulationRow`) no es una cotización y no cambia.

**Por qué**: es un campo más; la clarificación del 2026-10-27 lo pide.

## D26. Caché

**Decisión**: **ningún caché nuevo**. El estado del mercado (órdenes, tenencias, saldos, operaciones) se lee
siempre de la base. Las operaciones leen el precio de la base dentro de la transacción (D14), no de
`quotes-current`. El portfolio hace una sola consulta de cotizaciones por pedido (D19). Los cachés de la 007
(`quotes-current` y `quotes-ranking`) no cambian y el mercado no los invalida: no escribe cotizaciones.

**Por qué**: el estado del mercado cambia en cada pedido y un caché obligaría a invalidar en cada uno; las
consultas son por índice y alcanzan para SC-009.

## D27. Frontend

**Decisión**: dentro del árbol vigente, sin carpetas nuevas ni dependencias (detalle en el plan y en
[data-model.md](./data-model.md), sección 10):

- **Tipos** en `types/market.ts`, escritos a mano desde `contracts/market-api.yaml`, con los enums tal cual el
  backend (`BUY`, `PENDING`, `ADMIN`...). Las etiquetas en español ("compra", "pendiente", "administrador",
  "otro usuario") en `utils/market.ts`.
- **Servicios**: `marketService.ts` (`getMarketOrders`, `buyTokens`, `sellTokens`, `cancelOrder`,
  `getUserOrders`) y `portfolioService.ts` (`getPortfolio`, `getTransactions`), sobre `request()` de
  `httpClient.ts`.
- **Hooks**: `useMarketOrders` (listado y pendientes de la ficha), `useMarketFilters` (filtros y hoja en la
  dirección, como `useBoardFilters`), `usePortfolio`, `useTransactions`, `useMyOrders` y `useOrderCommands`
  (comprar, vender y cancelar sobre `useMutation` de la 007; después de cada éxito refresca el perfil con
  `refreshProfile` para el saldo de la barra).
- **Páginas y rutas**: `/mercado` (`MarketPage`, pública), `/portfolio` (`PortfolioPage`), `/operaciones`
  (`TransactionsPage`) y `/ordenes` (`MyOrdersPage`). Las tres privadas van con `RequireRole role="USER"`:
  `RequireRole` suma una prop `role` con `ADMIN` por defecto (así sus usos actuales y `AdminAccess.test.tsx` no
  cambian); un visitante va a `/login` y el administrador ve el aviso de falta de permiso (FR-072 y supuesto del
  spec: el administrador no las ve en la web).
- **Componentes**: `TradePanel` (en la ficha: cantidad, comprar o vender, avisos, resultado), `ConfirmDialog`
  (con `role="dialog"`, foco inicial en confirmar y Escape para cancelar, el patrón de `RequoteChoice`),
  `OrderList` (tabla que pasa a tarjetas a 360 px), `MarketFilters`, `PositionTable`, `PortfolioTotals` y
  `ProfitBadge` (ganancia o pérdida con signo, texto "gana" o "pierde" y triángulo, como `VariationBadge`;
  FR-070).
- **Ficha**: con sesión de usuario común ve `TradePanel`; sin sesión o como administrador, no (FR-065). Siempre
  ve las órdenes pendientes del jugador (`OrderList` con `GET /orders?playerId=`). El aviso de venta bloqueada
  sale de la orden inicial del listado (cuántos tokens le quedan); el de no operable, de `player.active` y de la
  cotización. La confirmación muestra cantidad, precio por token y total estimado, y avisa que el precio final es
  el del momento de la ejecución (FR-063). Para comprar con tokens del superusuario el estimado es
  `min(cantidad, pendiente de la inicial) × 1,0000 + resto × precio vigente`.
- **Barra superior**: con sesión, `Saldo libre: X` desde `profile.availableBalance` (FR-062; el perfil suma ese
  campo con el "sí" de la puerta G3, cumplida el 2026-10-28).
- **Enlaces**: "Mercado" para todos; "Portfolio", "Operaciones" y "Mis órdenes" solo para `USER`.
- **Score** en `QuotePanel` (un par `dt`/`dd` más), `RankingList` (una columna), `QuoteChart` (al final del
  detalle de cada punto), `QuoteTable` (última columna), `QuoteTag` de la pizarra (FR-071) y el portfolio. Va
  **al final** de cada texto o fila para que las aserciones de subcadena de los tests de la 007 sigan pasando.
- **`score` opcional en el tipo `Quote`** (`score?: number`): la API lo envía siempre, pero
  `QuotePanel.test.tsx`, `QuoteTag.test.tsx`, `quotes.test.ts` y `chart.test.ts` arman objetos `Quote` sin él,
  y `npm run build` compila `tests/` (`tsconfig` incluye `tests`). Con el campo obligatorio habría que tocar
  esos cuatro tests; opcional, los componentes lo muestran cuando viene.
- **Dirección**: `/mercado` con `side`, `league`, `team`, `ownerType` y `page` (los mismos nombres que la pizarra
  y el ranking donde coinciden; los vacíos no aparecen); `/operaciones` con `player`, `side`, `from`, `to` y
  `page`; `/portfolio` con `date`; `/ordenes` con `page`.
- **Estética, 360 px y teclado** (FR-061): pizarra con CSS Modules y los tokens de `tokens.css`; todo lo
  clickeable es `button` o enlace con foco visible; tablas que pasan a tarjetas.
- **Mocks**: `tests/mocks/marketHandlers.ts` y `tests/mocks/market.ts` (nuevos), registrados en `handlers.ts`.

**Alternativas descartadas**: `<dialog>` nativo con `showModal` (jsdom no lo implementa completo); un hook propio
para cada comando (repite `useMutation`).

## D28. Tests existentes que cambian (Principio IV)

**El "sí" explícito del equipo (Valentin, 2026-10-27) está en Clarifications** y alcanza solo a quitar de las
expresiones prohibidas comprar, vender, orden, portfolio, presupuesto, tokens y puntaje (conservando "sumar al
plantel"), ajustar las aserciones que verifican que el score no se expone y actualizar las descripciones de esos
tests. No autoriza borrar tests ni cambiar otras aserciones. **Un segundo "sí" (Lucas y Valentin, 2026-10-28),
también en Clarifications, cubre solo la aserción de `AccountControllerIT` que fija las claves del perfil**
(puerta G3). **Ningún test se toca antes de la tarea que lo cambia.**

| Test | Cambio | Por qué | ¿Lo cubre el "sí"? |
|---|---|---|---|
| `frontend/tests/pages/NoPurchaseActions.test.tsx`, `FORBIDDEN` (línea 7) | `/comprar\|vender\|sumar\|plantel\|presupuesto\|puntaje\|portfolio\|orden/i` → `/sumar\|plantel/i` | Con sesión de usuario común la barra tiene "Portfolio" y la ficha "Comprar" y "Vender" | Sí |
| ídem, comentario de la línea 1 y `describe('pantallas sin acciones de compra')` | Redacción: "ninguna pantalla ofrece sumar al plantel" | La descripción afirma lo que esta feature levanta | Sí |
| ídem, `PRICE_TEXT` y estrellas | **No cambian** | Los montos siguen en créditos, sin `$`, `€`, `USD`, `EUR` ni estrellas | — |
| `frontend/tests/pages/NoMarketActions.test.tsx`, `MARKET_ACTION` (línea 13) | `/compra\|comprar\|vend\|sumar\|plantel\|orden/i` → `/sumar\|plantel/i` | Mismo motivo | Sí |
| ídem, `INVESTMENT_DATA` (línea 14) | `/presupuesto\|portfolio\|cartera\|\btokens\b\|mis acciones/i` → `/cartera\|mis acciones/i` | La ficha (también para el administrador) muestra las órdenes pendientes con sus tokens | Sí |
| ídem, comentario de las líneas 1 a 3 y `describe('pantallas de la 007 sin acciones de mercado')` | Redacción: "sin acciones de plantel" | Mismo motivo | Sí |
| `backend/.../e2e/QuoteControllerIT.java`, `ningunaRespuestaTraeElScoreNiEstadisticas` (línea 344) | `doesNotContain("score", "baseValue", "goals", "minutes", "rating")` → `doesNotContain("baseValue", "goals", "minutes", "rating")` y `contains("\"score\"")`; el método pasa a `ningunaRespuestaTraeEstadisticasYTodasTraenElScore` | El score se expone (FR-058); las estadísticas y el valor base siguen ocultos | Sí |
| `backend/.../e2e/AccountControllerIT.java`, `elPerfilTieneExactamenteLasClavesDelContratoYNingunSecreto` (línea 75) | `containsExactlyInAnyOrder("id", "username", "email", "role", "balance")` → suma `"availableBalance"` y `"reservedBalance"` | FR-057: el perfil informa el saldo libre y el reservado | Sí, por el "sí" del 2026-10-28 (Lucas y Valentin, puerta G3); el del 2026-10-27 no lo cubría |

**Tests que se revisaron y no cambian**:

| Test | Por qué no cambia |
|---|---|
| `frontend/tests/components/PostIt.test.tsx` ("no contiene botones ni textos de compra, venta ni plantel") | El post-it suma el score de su cotización (`QuoteTag`), pero ningún texto de compra ni venta, y sigue sin botones |
| `frontend/tests/pages/PlayerPage.test.tsx` ("la ficha no tiene botones de compra, venta ni similares") | Renderiza la ficha **sin sesión**, y un visitante no ve `TradePanel` (FR-065). El test sigue verificando eso |
| `QuotePanel.test.tsx`, `QuoteTag.test.tsx`, `RankingList.test.tsx`, `QuoteChart.test.tsx`, `BoardQuotes.test.tsx`, `quotes.test.ts`, `chart.test.ts` | El score va al final de cada texto y fila, y es opcional en el tipo (D27) |
| `LockerRoomPage.test.tsx` (`within(profile).getByText('1.000,00')`) | El vestuario no cambia: el saldo libre va en la barra, fuera de ese `dl`. Si el vestuario mostrara también el libre, `getByText` encontraría dos `1.000,00` |
| `AuthControllerIT` (claves exactas del registro) | La respuesta del registro no cambia |
| `AuthServiceTest`, `AppUserTest`, `AppUserRepositoryIT`, `JwtServiceTest`, `AccountServiceTest` | Se conservan las firmas que usan (D5 y D6) |
| `SyncServiceTest`, `SyncServiceLastFullSyncTest`, `SyncCoordinatorTest`, `SyncWriteServiceIT`, `SyncControllerIT`, `StatsSyncControllerIT` | `SyncService` y `SyncCoordinator` no cambian, y la generación automática está apagada en el perfil test (D8) |
| `AccessControlIT` | No recorre las rutas de Swagger: verifica rutas puntuales que no cambian |

**Archivos de apoyo que cambian (no son tests; se listan igual)**: `tests/mocks/handlers.ts` (importa y suma
`marketHandlers`; el perfil simulado suma `availableBalance` y `reservedBalance`) y `tests/mocks/quotes.ts` (las
cotizaciones simuladas suman `score`; el perfil del administrador suma los dos saldos). Son aditivos: `setup.ts`
usa `onUnhandledRequest: 'error'`, así que sin los handlers del mercado la ficha existente fallaría al pedir sus
órdenes pendientes.

También cambian dos archivos de apoyo más, sin tocar ninguna aserción:

- `frontend/tests/setup.ts` (en T088 a T096): suma `configure({ asyncUtilTimeout: 3000 })`, así `findBy` y
  `waitFor` esperan hasta 3 s en vez de 1 s. Con la suite completa en paralelo, una pantalla que carga varias
  cosas puede tardar más de 1 s en una máquina cargada; sigue por debajo de los 5 s que Vitest le da a cada test.
- `backend/src/test/resources/application-test.yml` (en la fundacional): suma
  `futbolmarket.market.initial-orders.automatic: false` (D8 y data-model, sección 11), para que la generación
  automática no compita con los IT que crean las órdenes iniciales a mano.

**Se conserva la prohibición de "sumar al plantel"** en los dos tests del frontend.

## D29. Estrategia de tests

**Decisión** (Principio IV: sin TDD, setup-execute-verify, AssertJ, nombres descriptivos, casos felices y de
borde, estructura de producción):

- **Dominio** (`modelo/market/`, sin Spring): `MarketMathTest`, `CostBasisTest`, `MarketOrderTest`,
  `HoldingTest`, `OrderMatcherTest`, `PlayerBookTest`, `TradeReplayTest`, `PortfolioTest`, `AuditEntryTest`, y
  `AppUserMarketTest` en `modelo/user/`. Con los ejemplos del spec: compra de 10 contra una venta de 5 que deja 5
  pendientes; el superusuario primero; venta rechazada mientras el superusuario tiene pendiente;
  `3 × 1,0333 = 3,10`; `10 × 1,0345 = 10,35`; promedio 1,0100 y 1,1000; ganancia realizada +0,20 y +1,00; 10
  tokens que valen 10,00, 12,00 y 9,00 con cotizaciones de 100,00, 120,00 y 90,00; reserva de 10,30 que baja a
  6,18; 9 ejecutados y 1 cancelado a 1,1000; el caso de D11; el libro nunca tiene pendientes de los dos lados;
  portfolio a una fecha (25/08 y 01/09).
- **Servicio** (Mockito): `MarketOrderServiceTest` (orden de bloqueo: libro primero, cuentas ascendentes; nada se
  guarda si el modelo rechaza), `InitialOrderServiceTest` (sin superusuario, idempotencia, propiedad apagada),
  `InitialOrderStartupTest`, `PortfolioServiceTest`, `MarketQueryServiceTest` y `AuditQueryServiceTest`.
- **Integración en H2** (`@ActiveProfiles("test")`, base propia por clase con `LOCK_TIMEOUT=10000`, reloj con
  `MutableClock`): `MarketOrderServiceIT` (escenarios de US1 y US4; invariantes suma = 100, conservación de
  créditos y `reserved = Σ reservas` después de cada uno), `MarketConcurrencyIT` (**20 compras simultáneas contra
  10 tokens disponibles**: 10 ejecutadas, 10 pendientes, sin estados intermedios; y 50 pedidos mezclados de SC-002
  con las invariantes), `InitialOrderIT` (**sin duplicar al reiniciar** y **al sincronizar** con
  `SyncWriteService.applyLeague`; los dos a la vez), `AuditAtomicityIT` (**auditoría en la misma transacción**:
  con una falla forzada al guardar la operación, no queda ni la orden ni su auditoría), `PortfolioIT` (actual y a
  una fecha con el historial de cotizaciones), repositories (`MarketOrderRepositoryIT`, `TradeRepositoryIT`,
  `AuditRepositoryIT`, `QuoteRepositoryMarketIT` para las dos consultas nuevas de la 007) y `MarketVolumeIT`
  (opt-in, como `QuoteVolumeBenchmarkIT`: 2.600 órdenes iniciales y tiempos de SC-009).
- **End to end** (`e2e/`, MockMvc): `OrderControllerIT`, `MarketListingIT`, `PortfolioControllerIT`,
  `TransactionsControllerIT`, `AuditControllerIT` y `MarketAccessIT`: 400 (cuerpo, cantidad 0, negativa, decimal
  y 101; filtros; fechas), 401, **403** (administrador en `/orders/buy`, `/orders/sell` y cancelar; usuario común
  en `/audit` y en el portfolio, el historial y las órdenes de otro), 404, 409 (cada conflicto), el listado
  **público** sin la identidad del dueño, la propiedad del portfolio, el `userId` del cuerpo ignorado y el
  **score en las respuestas de cotización** (`QuoteScoreIT`).
- **Frontend** (Vitest, RTL y MSW, en español): `utils/market.test.ts`, componentes (`TradePanel`,
  `ConfirmDialog`, `OrderList`, `MarketFilters`, `PositionTable`, `PortfolioTotals`, `ProfitBadge`), hooks
  (`useMarketOrders`, `useMarketFilters`, `usePortfolio`, `useTransactions`, `useMyOrders`, `useOrderCommands`) y
  páginas (`MarketPage`, `PortfolioPage`, `TransactionsPage`, `MyOrdersPage`, `PlayerTrade`, `MarketAccess`,
  `QuoteScore`). Estados de carga, error con reintentar y vacío; 360 px y teclado donde se pueda verificar con
  comportamiento (foco, roles), no con estilos.

Los tests de concurrencia usan un `ExecutorService` y un `CountDownLatch` para largar los pedidos juntos, y
verifican solo el estado final (no cuál ganó), así no dependen de la planificación de hilos.

## D30. Riesgos conocidos

| Riesgo | Mitigación |
|---|---|
| Espera de candados en H2 (1 s por defecto) | `LOCK_TIMEOUT=10000` y `MarketBusyException` (409), D13; se mide en `MarketConcurrencyIT` |
| El superusuario serializa todas las compras iniciales | Transacciones cortas; plan B de acreditación atómica (D13) |
| Primer arranque con el catálogo completo (2.600 órdenes y su auditoría) | En segundo plano; se mide en `MarketVolumeIT` |
| `reserved` redundante con las reservas de las órdenes | Invariante verificada en cada IT del mercado (D5) |
| `ddl-auto: update` sobre una base local existente | Columnas nuevas nullable o con `DEFAULT`; tablas nuevas; nada se renombra |
| El portfolio del superusuario tiene una posición por jugador (hasta 2.600) | Solo lo consulta el administrador por API; una consulta por pedido |
| El historial filtra por comprador **o** vendedor | Dos índices; se mide con el volumen del escenario 3 |

## D31. Lo que el plan resuelve distinto del pedido y puntos a confirmar

**Distinto del pedido**:

| # | Pedido | Qué hace el plan | Dónde |
|---|---|---|---|
| 1 | `GET /orders` con filtro `status` (por defecto `PENDING`) | Sin filtro de estado; solo pendientes y parciales (FR-047 clarificado) | D22 |
| 2 | Portfolio con `QuoteQueryService.getCurrentOf` "que usa el caché" | `QuoteRepository.findCurrentOf`: `getCurrentOf` no está cacheado y tope 50 | D19 |
| 3 | Órdenes iniciales "al terminar cada sincronización (`SyncService` o `SyncWriteService`)" | En `SyncWriteService.applyLeague`, en la transacción de cada liga; más la propiedad `automatic`, apagada en test | D8 |
| 4 | `Holding` con "precio promedio" | Dos acumuladores y el promedio derivado, exacto | D12 |
| 5 | `Order` y `Position` | `MarketOrder` y `PortfolioPosition` | D3 |
| 6 | "Score en `QuotePanel`, `RankingList`, `QuoteChart` y `QuoteTable`" | También en `QuoteTag` de la pizarra (FR-071: "pizarra si muestra la cotización") | D27 |
| 7 | Componentes listados | Se suma `ProfitBadge` (FR-070) y el hook `useMarketFilters` | D27 |
| 8 | `.specify/feature.json` apuntaba a la 006 | Se corrigió a `specs/008-mercado-tokens-portfolio` para generar este plan | n/a |

**Puntos a confirmar con el equipo** (no frenan el diseño):

1. ~~El "sí" para `AccountControllerIT`~~ (puerta G3): **dado por Lucas y Valentin el 2026-10-28** y registrado en
   Clarifications. Alcanza solo a sumar `availableBalance` y `reservedBalance` a las claves exactas del perfil.
2. **La reserva de un remanente que ya no se puede reservar completa** (D11): el plan la deja con lo que queda,
   sin cancelar.
3. **El portfolio del superusuario** sin datos de costo (D7 y D19).
4. **El plan B del punto caliente del superusuario** si la medición lo pide (D13).
