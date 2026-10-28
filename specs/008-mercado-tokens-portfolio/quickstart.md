# Guía de validación: Mercado de tokens y portfolio de usuarios

Pasos para comprobar de punta a punta que la feature funciona, en el backend (`./gradlew bootRun`) y en el
frontend (`npm run dev`). El contrato está en [contracts/market-api.yaml](./contracts/market-api.yaml) y el modelo
en [data-model.md](./data-model.md); esta guía no los repite.

Los escenarios cubren: el **arranque del mercado** con las órdenes del superusuario, el **escenario 3 de la
consigna** (4 usuarios que compran tokens de 5 jugadores y ven su portfolio actual y a fechas pasadas), la
**reventa entre usuarios**, el **listado del mercado**, el **historial y la cancelación**, la **seguridad**, la
**auditoría**, el **score visible** y la **aplicación web**.

## 0. Puertas antes de empezar

| Puerta | Cómo se verifica |
|---|---|
| G1. La rama tiene **todo `develop`** (la 005, la 006, la 007 y `cf0a0dd`) | `git log --oneline -1 develop` aparece en `git log --oneline` de la rama |
| G2. El **"sí" del equipo** para los tests de la 004 y la 007 figura en Clarifications de `spec.md` | Está (Valentin, 2026-10-27). Cubre solo los cambios de la tabla de D28 de `research.md` |
| G3. El **"sí" para `AccountControllerIT`** (claves del perfil, FR-057) | Está (Lucas y Valentin, 2026-10-28). Cubre solo sumar `availableBalance` y `reservedBalance` a esa aserción; los pasos marcados con (G3) dependen de ese cambio |

## 1. Preparación

Requisitos: Java 21, Node 20 o más, `curl`. Sin Docker.

**1.1. Compilar y correr los tests del backend**

```bash
cd backend && ./gradlew build
```

Esperado: `BUILD SUCCESSFUL`. No incluye la medición de volumen (escenario J).

**1.2. Levantar el backend con el superusuario**

El superusuario es la cuenta administradora de la 001. Sin su contraseña no se crea, y el mercado no inicia.

```bash
cd backend && FUTBOLMARKET_AUTH_ADMIN_USERNAME=admin FUTBOLMARKET_AUTH_ADMIN_EMAIL=admin@correo.com FUTBOLMARKET_AUTH_ADMIN_PASSWORD=admin12345 FOOTBALL_DATA_TOKEN=<tu token> ./gradlew bootRun
```

Esperado en el registro: se creó el administrador (o ya existía), y después, en segundo plano, se generaron las
órdenes iniciales de los jugadores del catálogo. Si el catálogo está vacío, la sincronización de arranque de la
005 lo carga y genera las órdenes de cada liga al escribirla. Con el catálogo completo son unas 2.600 órdenes:
esperar a que el registro lo informe.

**1.3. Credenciales**

```bash
API=http://localhost:8080
ADMIN=$(curl -s -X POST $API/auth/login -H "Content-Type: application/json" -d '{"username":"admin","password":"admin12345"}' | sed 's/.*"token":"\([^"]*\)".*/\1/')
```

Registrar cuatro usuarios y guardar su clave de API (la respuesta del registro la trae una sola vez):

```bash
for U in ana beto caro dani; do curl -s -X POST $API/auth/register -H "Content-Type: application/json" -d "{\"username\":\"$U-mkt\",\"email\":\"$U@correo.com\",\"password\":\"clave12345\"}" | sed 's/.*"id":\([0-9]*\).*"apiKey":"\([^"]*\)".*/\1 \2/' > $U.key; done
```

Cada archivo `<usuario>.key` queda con `id clave`. Para los pasos siguientes:

```bash
read ANA_ID ANA < ana.key; read BETO_ID BETO < beto.key; read CARO_ID CARO < caro.key; read DANI_ID DANI < dani.key
```

## 2. Escenario A: arranque del mercado (US1.1 a US1.3, US9.1)

**2.1. Una orden del superusuario por jugador**

```bash
curl -s "$API/orders?ownerType=ADMIN&size=5"
```

Esperado (200, sin credencial): órdenes `SELL` con `ownerType: ADMIN`, `requestedQuantity: 100`,
`pendingQuantity: 100` y `status: PENDING`, de la más vieja a la más nueva; `totalElements` igual a la cantidad de
jugadores del catálogo. Ninguna orden trae nombre, correo ni id del dueño.

Guardar cinco jugadores para el escenario 3 (los ids de `player.id` de la respuesta):

```bash
P1=<id>; P2=<id>; P3=<id>; P4=<id>; P5=<id>
```

**2.2. Reiniciar no duplica**: detener y volver a levantar el backend (paso 1.2) tres veces. `totalElements` del
paso 2.1 no cambia (SC-001).

**2.3. Sincronizar no duplica**: con el token de administrador, `curl -s -X POST $API/players/sync -H
"Authorization: Bearer $ADMIN"` y esperar a que termine. `totalElements` sube solo si entraron jugadores nuevos, y
cada nuevo tiene su orden inicial; ninguno tiene dos.

## 3. Escenario B: el escenario 3 de la consigna (US1 y US2, SC-010)

**3.1. Cada usuario compra tokens de los cinco jugadores**

```bash
for K in $ANA $BETO $CARO $DANI; do for P in $P1 $P2 $P3 $P4 $P5; do curl -s -X POST $API/orders/buy -H "X-API-Key: $K" -H "Content-Type: application/json" -d "{\"playerId\":$P,\"quantity\":5}"; echo; done; done
```

Esperado (201 cada una): `order.status: EXECUTED`, un `trade` de 5 tokens a `tokenPrice: 1.0000` por `total: 5.00`
con `counterparty: ADMIN`, y la cotización vigente como referencia (con su `score`). Al final cada usuario tiene
`balance: 975.00`.

**3.2. El superusuario cobró y le quedan tokens**: `curl -s "$API/orders?ownerType=ADMIN&playerId=$P1"` muestra
`executedQuantity: 20`, `pendingQuantity: 80` y `status: PARTIAL`.

**3.3. Portfolio actual de cada usuario**

```bash
curl -s "$API/users/$ANA_ID/portfolio" -H "X-API-Key: $ANA"
```

Esperado (200): cinco posiciones con `tokens: 5`, `sharePercent: 5`, `committed: 0`, `averagePrice: 1.0000`,
`invested: 5.00`, la cotización vigente con `tokenPrice` y `score`, `value = 5 × tokenPrice` (2 decimales),
`profit = value − 5.00` y `profitPercent`. Totales: `availableBalance: 975.00`, `reservedBalance: 0.00`,
`invested: 25.00` y `equity = 975.00 + value`. Repetir con los otros tres usuarios.

**3.4. Portfolio a fechas pasadas**

- El día del registro, sin operaciones antes del momento de las compras: `?date=<hoy>` devuelve las cinco
  posiciones valuadas con la cotización que rige al final del día, `balance: 975.00` y sin `availableBalance` ni
  `reservedBalance`.
- Un día anterior al registro: `?date=<ayer>` responde 400 "La fecha ... es anterior al registro del usuario".
- Una fecha futura o `?date=2026-13-01`: 400.
- **Valoración histórica real**: después del próximo recálculo semanal (o un día después), pedir `?date=<día de
  las compras>` y sin fecha. La primera conserva la cotización de ese día; la segunda usa la nueva. El ejemplo del
  spec con fechas fijas (compras el 20/08 y el 26/08, portfolio al 25/08 a 1,0300 y al 01/09 a 1,0100) lo
  reproduce `PortfolioIT` con el reloj quieto:

```bash
cd backend && ./gradlew test --tests "*PortfolioIT"
```

## 4. Escenario C: reventa entre usuarios (US4)

**4.1. Agotar la orden inicial de un jugador**: los cuatro usuarios ya tienen 5 de `P1`; Ana compra los 80 que
quedan.

```bash
curl -s -X POST $API/orders/buy -H "X-API-Key: $ANA" -H "Content-Type: application/json" -d "{\"playerId\":$P1,\"quantity\":80}"
```

Esperado: `executedQuantity: 80` a 1,0000; la orden inicial de `P1` queda `EXECUTED` y ya no figura en
`GET /orders?ownerType=ADMIN&playerId=$P1`.

**4.2. Vender mientras el superusuario tenía tokens**: antes del paso 4.1, `POST /orders/sell` de `P1` respondía
409 "Todavía no se puede vender: al administrador le quedan ... tokens" y no creaba nada (SC-006). Ahora, con `P2`
(que todavía tiene tokens del superusuario), sigue respondiendo 409.

**4.3. Compra pendiente con reserva**: Beto pide 10 de `P1` y no hay ventas.

```bash
curl -s -X POST $API/orders/buy -H "X-API-Key: $BETO" -H "Content-Type: application/json" -d "{\"playerId\":$P1,\"quantity\":10}"
```

Esperado: `status: PENDING`, `trades: []`, `reservedCredits = 10 × tokenPrice vigente` (2 decimales),
`reservedBalance` igual a eso y `availableBalance = 975.00 − reservedBalance`.

**4.4. Venta que cruza**: Caro vende 4 de `P1`.

```bash
curl -s -X POST $API/orders/sell -H "X-API-Key: $CARO" -H "Content-Type: application/json" -d "{\"playerId\":$P1,\"quantity\":4}"
```

Esperado: un `trade` de 4 al precio vigente con `counterparty: USER`; Caro cobra el total. La compra de Beto queda
`PARTIAL` con 6 pendientes y su reserva baja a `6 × precio de reserva`.

**4.5. Tokens libres y órdenes de sentido contrario**: Caro pide vender 2 de `P1` (le queda 1 libre) → 409 "Tokens
insuficientes"; Beto pide vender `P1` con su compra pendiente → 409 "Ya tiene una orden de compra pendiente".

**4.6. Invariantes** en la consola de H2 (`http://localhost:8080/h2-console`, solo perfil `local`):

```sql
select o.player_id from market_orders o left join holdings h on h.player_id = o.player_id where o.initial_player_id is not null group by o.player_id, o.requested_quantity, o.executed_quantity, o.cancelled_quantity having o.requested_quantity - o.executed_quantity - o.cancelled_quantity + coalesce(sum(h.quantity), 0) <> 100
```

Esperado: 0 filas (FR-026). Y `select sum(balance) from app_user` es igual a la suma de los saldos iniciales
otorgados (SC-003).

## 5. Escenario D: listado del mercado (US5)

| Pedido | Esperado |
|---|---|
| `GET /orders` | 200 sin credencial; solo `PENDING` y `PARTIAL`, de la más vieja a la más nueva, con paginación |
| `GET /orders?side=BUY&playerId=$P1` | la compra de Beto con 6 pendientes, `ownerType: USER` |
| `GET /orders?league=PREMIER&ownerType=ADMIN` | órdenes iniciales de jugadores de la Premier |
| `GET /orders?team=<nombre exacto>` | solo ese equipo |
| `GET /orders?side=X`, `?ownerType=X`, `?league=X`, `?playerId=0`, `?team=` | 400 "Los parámetros enviados no son válidos." |
| `GET /orders?status=EXECUTED` | el parámetro no existe: se ignora y el listado sigue mostrando solo las abiertas |

Ninguna respuesta trae `owner`, `username`, `email` ni el id del dueño (SC-013).

## 6. Escenario E: historial, órdenes propias y cancelación (US6)

**6.1. Historial**: `curl -s "$API/users/$CARO_ID/transactions" -H "X-API-Key: $CARO"` → la venta de 4 primero
(con `realizedProfit`), después las cinco compras al superusuario con `counterparty: ADMIN`. Con
`?side=SELL`, `?playerId=$P1`, `?from=<hoy>&to=<hoy>` filtra; `?from=<hoy>&to=<ayer>` → 400.

**6.2. Órdenes propias**: `curl -s "$API/users/$BETO_ID/orders" -H "X-API-Key: $BETO"` → la compra `PARTIAL` primero.

**6.3. Cancelar**: con el `id` de esa orden,

```bash
curl -s -X POST $API/orders/<id>/cancel -H "X-API-Key: $BETO"
```

Esperado (200): `status: CANCELLED`, `executedQuantity: 4`, `cancelledQuantity: 6`, `reservedCredits: 0.00`, y
`reservedBalance: 0.00`. Repetir → 409 "La orden ya está cancelada". Cancelarla con la clave de Caro → 403.
Cancelar una orden inicial (su `id` sale del paso 2.1) con cualquier usuario → 403.

## 7. Escenario F: seguridad y errores (FR-055 y FR-056, SC-007)

| Pedido | Esperado |
|---|---|
| `POST /orders/buy` sin credencial | 401 |
| `POST /orders/buy` con `Authorization: Bearer $ADMIN` | 403, antes de llegar al controller; nada cambia |
| `POST /orders/buy` con `{"playerId":$P2,"quantity":0}`, `-1`, `1.5`, `101` o sin cuerpo | 400 con `violations` (o "El cuerpo del request no es un JSON válido.") |
| `POST /orders/buy` con un `playerId` inexistente | 404 |
| Un usuario nuevo (`eva`) compra 100 tokens de diez órdenes iniciales completas (1.000,00 a 1,0000) y pide 1 más | 409 "Saldo insuficiente"; no se crea ninguna orden ni cambia ningún saldo |
| `POST /orders/buy` con `"userId": <otro>` en el cuerpo | la orden queda a nombre del dueño de la credencial |
| `GET /users/$ANA_ID/portfolio` con la clave de Beto | 403 |
| `GET /users/$ANA_ID/portfolio` con `Bearer $ADMIN` | 200 |
| `GET /users/999999/portfolio` con `Bearer $ADMIN` | 404; con la clave de Beto, 403 |
| `GET /audit` con la clave de Ana | 403 |
| Un jugador inactivo del catálogo (su `GET /players/{id}` dice `active: false`), si lo hay | `POST /orders/buy` → 409 "El jugador no se puede operar: está inactivo." (también lo cubre `OrderControllerIT`) |

Con WhoScored y Football-Data sin red (por ejemplo, sin `FOOTBALL_DATA_TOKEN`), todo lo anterior responde igual
(SC-012).

## 8. Escenario G: auditoría (US9)

```bash
curl -s "$API/audit?size=20" -H "Authorization: Bearer $ADMIN"
```

Esperado: de la más nueva a la más vieja, la cancelación de Beto (`ORDER_CANCELLED`, `actorType: USER`,
`cancelReason: USER_REQUEST`), la venta de Caro (`TRADE_EXECUTED` con comprador y vendedor, sus saldos libre y
reservado y su tenencia antes y después), las órdenes creadas y, al final, las `ORDER_CREATED` del superusuario
(`actorType: SUPERUSER`). Filtros: `?eventType=TRADE_EXECUTED`, `?userId=$BETO_ID`, `?playerId=$P1`,
`?from=<hoy>&to=<hoy>`.

El pedido rechazado del escenario F (saldo insuficiente) **no** dejó ninguna `ORDER_CREATED` (US9.6). La API no
ofrece ninguna operación para modificar o borrar un registro (US9.5).

## 9. Escenario H: score visible (US8, SC-014)

```bash
curl -s "$API/players/$P1/quotes/current"; curl -s "$API/players/$P1/quotes/at?date=<hoy>"; curl -s "$API/players/$P1/quotes"; curl -s "$API/players/quotes/current?ids=$P1,$P2"; curl -s "$API/players/ranking"
```

Esperado: cada cotización trae `score` con seis decimales (0 en la `INITIAL`) y ninguna trae `baseValue`, goles,
minutos, rating ni métricas normalizadas. El perfil (G3): `curl -s $API/auth/me -H "X-API-Key: $BETO"` trae
`balance`, `availableBalance` y `reservedBalance`.

## 10. Escenario I: la aplicación web (US3, US5 a US8)

```bash
cd frontend && npm run dev
```

Con el backend del paso 1.2 corriendo. Abrir `http://localhost:5173`.

| Paso | Esperado |
|---|---|
| Sin sesión, abrir `/mercado` | el listado con filtros de tipo, liga, equipo y tipo de usuario; cambiar un filtro vuelve a la hoja 1; los filtros y la hoja quedan en la dirección y recargar reproduce la vista |
| Sin sesión, abrir la ficha de `P2` | cotización con su score, gráfico con el score en el detalle y en la tabla, órdenes pendientes (la del administrador con los tokens que le quedan); **sin** controles de compra ni venta |
| Entrar como Ana | la barra muestra "Saldo libre" (G3) y los enlaces Mercado, Portfolio, Operaciones y Mis órdenes |
| Ficha de `P2`, comprar 3 | la confirmación muestra cantidad, precio por token y total estimado, y avisa que el precio final es el de la ejecución; nada se envía hasta confirmar; después, el resultado real y el saldo actualizado (SC-008: menos de 30 s) |
| Ficha de `P2`, vender | el aviso dice que todavía no se puede vender porque el administrador tiene tokens, y cuántos |
| Ficha de un jugador inactivo | los controles no están y la ficha explica por qué |
| Un rechazo (comprar más de lo que alcanza el saldo) | el motivo en español; la operación no se da por hecha |
| `/portfolio` | posiciones y totales con ganancia o pérdida con signo y texto ("gana", "pierde"); elegir una fecha muestra el portfolio a esa fecha y la deja en la dirección; volver al actual |
| `/operaciones` | el historial con filtros y hoja en la dirección |
| `/ordenes` | las órdenes propias; cancelar una pendiente pide confirmación |
| Entrar como `admin` | la ficha sin controles de operar; `/portfolio`, `/operaciones` y `/ordenes` muestran el aviso de falta de permiso |
| Cerrar sesión y abrir `/portfolio` | lleva a `/login` |
| Las pantallas a 360 px de ancho | sin desplazamiento horizontal de la página; las tablas pasan a tarjetas |
| Recorrer cada pantalla solo con el teclado | todo lo clickeable se alcanza con Tab, con foco visible; el diálogo se confirma con Enter y se cancela con Escape |
| Error de red (detener el backend) | cada pantalla nueva muestra el error con "reintentar"; un listado vacío muestra su mensaje |

## 11. Escenario J: concurrencia, invariantes y volumen

```bash
cd backend && ./gradlew test --tests "*MarketConcurrencyIT" --tests "*InitialOrderIT" --tests "*AuditAtomicityIT"
```

Esperado: 20 compras simultáneas contra 10 tokens disponibles dejan 10 ejecutadas y 10 pendientes, sin estados
intermedios; después de 50 pedidos mezclados, las tenencias de cada jugador suman 100, ningún saldo ni tenencia es
negativo y la suma de los saldos no cambió (SC-002 y SC-003); las órdenes iniciales no se duplican al reiniciar ni al
sincronizar; con una falla forzada no queda ni la orden ni su auditoría.

Volumen (opt-in, como el de la 007): 2.600 jugadores con su orden inicial; una orden, el portfolio actual y a una
fecha, una hoja del historial y una del listado, en menos de 2 s cada uno (SC-009).

```bash
cd backend && MARKET_BENCHMARK=true ./gradlew test --tests "*MarketVolumeIT"
```

Se habilita con la variable de entorno, como `QuoteVolumeBenchmarkIT` (`@EnabledIfEnvironmentVariable`), y usa su
propia base en archivo bajo `build/benchmark/`.

## 12. Definición de terminado

**Backend**: `./gradlew build` con `BUILD SUCCESSFUL`; `./gradlew bootRun` levanta con el perfil `local`; los
endpoints nuevos están en `/swagger-ui.html` (tags Órdenes, Mercado, Portfolio y Auditoría) y se pueden ejecutar
desde ahí con un token o una clave de API; SonarCloud con menos de 10 issues.

**Frontend**:

```bash
cd frontend && npm test
```

```bash
cd frontend && npm run lint
```

```bash
cd frontend && npm run build
```

Los tres sin errores, y `npm run dev` funciona contra el backend del paso 1.2 (escenario I).
