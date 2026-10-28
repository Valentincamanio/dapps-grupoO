// Handlers de MSW del mercado (specs/008-mercado-tokens-portfolio/contracts/market-api.yaml): el
// listado público, comprar, vender y cancelar, y el portfolio, el historial y las órdenes de un
// usuario. Responden en /api/... con los datos de market.ts.
//
// Las cuentas son las de handlers.ts, que las pasa al registrar estos handlers: comprar y cancelar
// cambian el mismo objeto, y GET /auth/me informa después los saldos nuevos. Simplificaciones: una
// compra se cruza solo con la orden inicial del jugador (el resto queda pendiente) y una venta queda
// pendiente sin cruzarse.
import { http, HttpResponse } from 'msw';
import type { ApiError, League, Role } from '../../src/types/api';
import { LEAGUES } from '../../src/types/api';
import type {
  Balances,
  MarketOrderPage,
  OrderSide,
  OwnerType,
  Portfolio,
  QuoteUsed,
  TradeSummary,
  TransactionPage,
  UserOrder,
  UserOrderPage,
} from '../../src/types/market';
import { ORDER_SIDES, OWNER_TYPES } from '../../src/types/market';
import {
  CURRENT_PORTFOLIO,
  currentQuoteUsed,
  insufficientCreditsMessage,
  insufficientTokensMessage,
  MARKET_MESSAGES,
  MARKET_USER_ID,
  type OwnedOrder,
  playerRef,
  PORTFOLIO_AT_DATE,
  REGISTRATION_DAY,
  saleBlockedMessage,
  seedHoldings,
  seedOrders,
  toMarketOrder,
  TRANSACTIONS,
} from './market';
import { PLAYERS } from './players';

// Lo que el mercado necesita de una cuenta de handlers.ts
export interface MarketAccount {
  id: number;
  role: Role;
  balance: number;
  reservedBalance: number;
}

export interface MarketAccounts {
  // La cuenta del token Bearer, o la respuesta 401 del backend
  authenticate: (request: Request, path: string) => MarketAccount | Response;
  exists: (userId: number) => boolean;
}

interface Holding {
  tokens: number;
  committed: number;
}

const DEFAULT_SIZE = 10;
const MAX_SIZE = 50;
const MAX_QUANTITY = 100;
const INITIAL_TOKEN_PRICE = 1;
const OPEN_STATUSES: UserOrder['status'][] = ['PENDING', 'PARTIAL'];
// Argentina, UTC−3 sin horario de verano
const ARGENTINA_OFFSET_MS = 3 * 60 * 60 * 1000;
const FIRST_ID = 1000;

let orders: OwnedOrder[] = seedOrders();
let holdings = new Map<string, Holding>();
let nextOrderId = FIRST_ID;
let nextTradeId = FIRST_ID;

function holdingKey(userId: number, playerId: number): string {
  return `${userId}:${playerId}`;
}

// Vuelve el mercado al estado inicial; lo llama resetMockDb de handlers.ts después de cada test
export function resetMarketMock(): void {
  orders = seedOrders();
  holdings = new Map(
    [...seedHoldings()].map(([playerId, holding]) => [holdingKey(MARKET_USER_ID, playerId), holding]),
  );
  nextOrderId = FIRST_ID;
  nextTradeId = FIRST_ID;
}

resetMarketMock();

function apiError(
  status: number,
  error: string,
  message: string,
  path: string,
  violations?: ApiError['violations'],
): Response {
  const body: ApiError = {
    timestamp: new Date().toISOString(),
    status,
    error,
    message,
    path,
    ...(violations ? { violations } : {}),
  };
  return HttpResponse.json(body, { status });
}

function badRequest(path: string, message = MARKET_MESSAGES.invalidParams, violations?: ApiError['violations']) {
  return apiError(400, 'Bad Request', message, path, violations);
}

function forbidden(path: string, message: string): Response {
  return apiError(403, 'Forbidden', message, path);
}

function notFound(path: string, message: string): Response {
  return apiError(404, 'Not Found', message, path);
}

function conflict(path: string, message: string): Response {
  return apiError(409, 'Conflict', message, path);
}

function round2(value: number): number {
  return Math.round(value * 100) / 100;
}

// Como el backend: cantidad × precio por token, redondeado una vez hacia arriba desde la mitad,
// en enteros para que 10 × 1,0345 dé 10,35
function totalOf(quantity: number, tokenPrice: number): number {
  const priceUnits = Math.round(tokenPrice * 10000);
  return Math.floor((quantity * priceUnits + 50) / 100) / 100;
}

// Entero o NaN; sin valor usa el predeterminado
function parseInteger(raw: string | null, fallback: number): number {
  if (raw === null || raw === '') return fallback;
  return /^-?\d+$/.test(raw) ? Number(raw) : Number.NaN;
}

function isValidDay(raw: string): boolean {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(raw)) return false;
  const date = new Date(`${raw}T00:00:00Z`);
  return !Number.isNaN(date.getTime()) && date.toISOString().slice(0, 10) === raw;
}

function invalidDay(path: string, raw: string): Response {
  return badRequest(path, `La fecha '${raw}' no es válida: use el formato AAAA-MM-DD.`);
}

// El día en hora de Argentina de un instante ISO 8601
function argentineDay(instant: string): string {
  return new Date(Date.parse(instant) - ARGENTINA_OFFSET_MS).toISOString().slice(0, 10);
}

function isOneOf<T extends string>(values: readonly T[], raw: string | null): boolean {
  return raw === null || values.includes(raw as T);
}

// El id del filtro, 0 si no vino o NaN si no es un id válido (entero desde 1)
function optionalId(raw: string | null): number {
  const id = parseInteger(raw, 0);
  return raw !== null && raw !== '' && !(id >= 1) ? Number.NaN : id;
}

interface Paging {
  page: number;
  size: number;
}

function paging(params: URLSearchParams): Paging | null {
  const page = parseInteger(params.get('page'), 0);
  const size = parseInteger(params.get('size'), DEFAULT_SIZE);
  return page >= 0 && size >= 1 && size <= MAX_SIZE ? { page, size } : null;
}

function sheetOf<T>(items: T[], { page, size }: Paging) {
  const totalPages = Math.ceil(items.length / size);
  return {
    content: items.slice(page * size, page * size + size),
    page,
    size,
    totalElements: items.length,
    totalPages,
    hasPrevious: page > 0 && totalPages > 0,
    hasNext: page + 1 < totalPages,
  };
}

function byCreation(a: UserOrder, b: UserOrder): number {
  return a.createdAt.localeCompare(b.createdAt) || a.id - b.id;
}

function isOpen(order: UserOrder): boolean {
  return OPEN_STATUSES.includes(order.status);
}

function balancesOf(account: MarketAccount): Balances {
  return {
    balance: account.balance,
    availableBalance: round2(account.balance - account.reservedBalance),
    reservedBalance: account.reservedBalance,
  };
}

function statusOf(order: UserOrder): UserOrder['status'] {
  if (order.pendingQuantity > 0) return order.executedQuantity > 0 ? 'PARTIAL' : 'PENDING';
  return order.cancelledQuantity > 0 ? 'CANCELLED' : 'EXECUTED';
}

function initialOrderOf(playerId: number): UserOrder | undefined {
  return orders.find(({ order }) => order.ownerType === 'ADMIN' && order.player.id === playerId)?.order;
}

function newOrder(ownerId: number, playerId: number, side: OrderSide, quantity: number): UserOrder {
  const order: UserOrder = {
    id: nextOrderId++,
    player: playerRef(playerId),
    side,
    ownerType: 'USER',
    status: 'PENDING',
    requestedQuantity: quantity,
    executedQuantity: 0,
    pendingQuantity: quantity,
    createdAt: new Date().toISOString(),
    cancelledQuantity: 0,
    reservedCredits: 0,
  };
  orders.push({ ownerId, order });
  return order;
}

function fill(order: UserOrder, quantity: number): void {
  order.executedQuantity += quantity;
  order.pendingQuantity -= quantity;
  order.status = statusOf(order);
}

interface PlacedOrder {
  account: MarketAccount;
  playerId: number;
  quantity: number;
  quote: QuoteUsed;
  initial: UserOrder;
  path: string;
}

// Primero la orden inicial a 1,0000; lo que falta queda pendiente y reserva al precio vigente
function buy({ account, playerId, quantity, quote, initial, path }: PlacedOrder): Response {
  const fromInitial = Math.min(quantity, initial.pendingQuantity);
  const remainder = quantity - fromInitial;
  const executedTotal = totalOf(fromInitial, INITIAL_TOKEN_PRICE);
  const reservation = totalOf(remainder, quote.tokenPrice);
  const available = round2(account.balance - account.reservedBalance);
  const needed = round2(executedTotal + reservation);
  if (needed > available) return conflict(path, insufficientCreditsMessage(needed, available));

  account.balance = round2(account.balance - executedTotal);
  account.reservedBalance = round2(account.reservedBalance + reservation);
  fill(initial, fromInitial);
  const order = newOrder(account.id, playerId, 'BUY', quantity);
  fill(order, fromInitial);
  order.reservedCredits = reservation;
  const key = holdingKey(account.id, playerId);
  const holding = holdings.get(key) ?? { tokens: 0, committed: 0 };
  holdings.set(key, { ...holding, tokens: holding.tokens + fromInitial });

  const trades: TradeSummary[] = fromInitial > 0
    ? [{
        id: nextTradeId++,
        executedAt: order.createdAt,
        quantity: fromInitial,
        tokenPrice: INITIAL_TOKEN_PRICE,
        total: executedTotal,
        counterparty: 'ADMIN',
        quote,
      }]
    : [];
  return HttpResponse.json(
    { order, trades, executedQuantity: fromInitial, executedTotal, ...balancesOf(account) },
    { status: 201 },
  );
}

// Bloqueada mientras el superusuario tenga tokens; si no, compromete los tokens y queda pendiente
function sell({ account, playerId, quantity, initial, path }: PlacedOrder): Response {
  if (initial.pendingQuantity > 0) return conflict(path, saleBlockedMessage(initial.pendingQuantity));
  const key = holdingKey(account.id, playerId);
  const holding = holdings.get(key) ?? { tokens: 0, committed: 0 };
  const free = holding.tokens - holding.committed;
  if (quantity > free) return conflict(path, insufficientTokensMessage(free));

  holdings.set(key, { ...holding, committed: holding.committed + quantity });
  const order = newOrder(account.id, playerId, 'SELL', quantity);
  return HttpResponse.json(
    { order, trades: [], executedQuantity: 0, executedTotal: 0, ...balancesOf(account) },
    { status: 201 },
  );
}

function quantityViolations(body: { playerId?: unknown; quantity?: unknown }): NonNullable<ApiError['violations']> {
  const violations: NonNullable<ApiError['violations']> = [];
  if (!Number.isInteger(body.playerId) || (body.playerId as number) < 1) {
    violations.push({ field: 'playerId', message: 'El jugador es obligatorio.' });
  }
  const quantity = body.quantity;
  if (!Number.isInteger(quantity) || (quantity as number) < 1 || (quantity as number) > MAX_QUANTITY) {
    violations.push({ field: 'quantity', message: MARKET_MESSAGES.invalidQuantity });
  }
  return violations;
}

export function createMarketHandlers(accounts: MarketAccounts) {
  async function placeOrder(request: Request, side: OrderSide): Promise<Response> {
    const path = side === 'BUY' ? '/orders/buy' : '/orders/sell';
    const account = accounts.authenticate(request, path);
    if (account instanceof Response) return account;
    if (account.role !== 'USER') return forbidden(path, MARKET_MESSAGES.forbidden);

    const body = (await request.json().catch(() => null)) as { playerId?: unknown; quantity?: unknown } | null;
    if (body === null) return badRequest(path, 'El cuerpo del request no es un JSON válido.');
    const violations = quantityViolations(body);
    if (violations.length > 0) return badRequest(path, MARKET_MESSAGES.invalidRequest, violations);

    const playerId = body.playerId as number;
    if (!PLAYERS.some((player) => player.id === playerId)) {
      return notFound(path, `No se encontró el jugador con id ${playerId}.`);
    }
    const quote = currentQuoteUsed(playerId);
    if (!quote) return conflict(path, MARKET_MESSAGES.withoutQuote);
    const initial = initialOrderOf(playerId);
    if (!initial) return conflict(path, MARKET_MESSAGES.withoutInitialOrder);

    const placed = { account, playerId, quantity: body.quantity as number, quote, initial, path };
    return side === 'BUY' ? buy(placed) : sell(placed);
  }

  // La cuenta pedida en /users/{id}/...: existe, y quien pide es su dueño o el administrador
  function accountAccess(request: Request, rawId: string, path: string): number | Response {
    if (!/^\d+$/.test(rawId)) return badRequest(path);
    const account = accounts.authenticate(request, path);
    if (account instanceof Response) return account;
    const userId = Number(rawId);
    if (!accounts.exists(userId)) return notFound(path, `No se encontró el usuario con id ${userId}.`);
    if (account.role !== 'ADMIN' && account.id !== userId) return forbidden(path, MARKET_MESSAGES.accountForbidden);
    return userId;
  }

  return [
    http.get('*/api/orders', ({ request }) => {
      const path = '/orders';
      const params = new URL(request.url).searchParams;
      const side = params.get('side');
      const ownerType = params.get('ownerType');
      const league = params.get('league');
      const team = params.get('team');
      const playerId = optionalId(params.get('playerId'));
      const sheet = paging(params);
      const valid = sheet !== null && isOneOf(ORDER_SIDES, side) && isOneOf(OWNER_TYPES, ownerType)
        && isOneOf(LEAGUES, league) && !Number.isNaN(playerId);
      if (!valid) return badRequest(path);
      if (team !== null && team.trim() === '') return badRequest(path, 'El equipo del filtro no puede estar vacío.');

      const listed = orders
        .map(({ order }) => order)
        .filter((order) => isOpen(order)
          && (side === null || order.side === (side as OrderSide))
          && (ownerType === null || order.ownerType === (ownerType as OwnerType))
          && (league === null || order.player.league === (league as League))
          && (team === null || order.player.team === team.trim())
          && (playerId === 0 || order.player.id === playerId))
        .sort(byCreation)
        .map(toMarketOrder);
      const body: MarketOrderPage = sheetOf(listed, sheet);
      return HttpResponse.json(body);
    }),

    http.post('*/api/orders/buy', ({ request }) => placeOrder(request, 'BUY')),

    http.post('*/api/orders/sell', ({ request }) => placeOrder(request, 'SELL')),

    http.post('*/api/orders/:id/cancel', ({ request, params }) => {
      const raw = String(params.id);
      const path = `/orders/${raw}/cancel`;
      const account = accounts.authenticate(request, path);
      if (account instanceof Response) return account;
      if (account.role !== 'USER') return forbidden(path, MARKET_MESSAGES.forbidden);
      if (!/^\d+$/.test(raw)) return badRequest(path);

      const owned = orders.find(({ order }) => order.id === Number(raw));
      if (!owned) return notFound(path, `No se encontró la orden con id ${raw}.`);
      if (owned.ownerId !== account.id) return forbidden(path, MARKET_MESSAGES.notOwned);
      const { order } = owned;
      if (order.status === 'EXECUTED') return conflict(path, MARKET_MESSAGES.executed);
      if (order.status === 'CANCELLED') return conflict(path, MARKET_MESSAGES.cancelled);

      if (order.side === 'BUY') {
        account.reservedBalance = round2(account.reservedBalance - order.reservedCredits);
      } else {
        const key = holdingKey(account.id, order.player.id);
        const holding = holdings.get(key) ?? { tokens: 0, committed: 0 };
        holdings.set(key, { ...holding, committed: holding.committed - order.pendingQuantity });
      }
      order.cancelledQuantity += order.pendingQuantity;
      order.pendingQuantity = 0;
      order.reservedCredits = 0;
      order.status = statusOf(order);
      return HttpResponse.json({ order, ...balancesOf(account) });
    }),

    http.get('*/api/users/:id/portfolio', ({ request, params }) => {
      const raw = String(params.id);
      const path = `/users/${raw}/portfolio`;
      const userId = accountAccess(request, raw, path);
      if (userId instanceof Response) return userId;

      const date = new URL(request.url).searchParams.get('date');
      if (date !== null && !isValidDay(date)) return invalidDay(path, date);
      if (date !== null && date < REGISTRATION_DAY) {
        return badRequest(path, `La fecha ${date} es anterior al registro del usuario (${REGISTRATION_DAY}).`);
      }
      if (userId !== MARKET_USER_ID) return HttpResponse.json(emptyPortfolio(userId, date));
      const body: Portfolio = date === null ? CURRENT_PORTFOLIO : { ...PORTFOLIO_AT_DATE, date };
      return HttpResponse.json(body);
    }),

    http.get('*/api/users/:id/transactions', ({ request, params }) => {
      const raw = String(params.id);
      const path = `/users/${raw}/transactions`;
      const userId = accountAccess(request, raw, path);
      if (userId instanceof Response) return userId;

      const query = new URL(request.url).searchParams;
      const playerId = optionalId(query.get('playerId'));
      const side = query.get('side');
      const from = query.get('from');
      const to = query.get('to');
      const sheet = paging(query);
      if (sheet === null || Number.isNaN(playerId) || !isOneOf(ORDER_SIDES, side)) return badRequest(path);
      if (from !== null && !isValidDay(from)) return invalidDay(path, from);
      if (to !== null && !isValidDay(to)) return invalidDay(path, to);
      if (from !== null && to !== null && from > to) {
        return badRequest(path, `El inicio del rango (${from}) es posterior al final (${to}).`);
      }

      const own = userId === MARKET_USER_ID ? TRANSACTIONS : [];
      const filtered = own.filter((transaction) => {
        const day = argentineDay(transaction.executedAt);
        return (playerId === 0 || transaction.player.id === playerId)
          && (side === null || transaction.side === side)
          && (from === null || day >= from)
          && (to === null || day <= to);
      });
      const body: TransactionPage = sheetOf(filtered, sheet);
      return HttpResponse.json(body);
    }),

    http.get('*/api/users/:id/orders', ({ request, params }) => {
      const raw = String(params.id);
      const path = `/users/${raw}/orders`;
      const userId = accountAccess(request, raw, path);
      if (userId instanceof Response) return userId;

      const sheet = paging(new URL(request.url).searchParams);
      if (sheet === null) return badRequest(path);
      const own = orders
        .filter(({ ownerId }) => ownerId === userId)
        .map(({ order }) => order)
        .sort((a, b) => byCreation(b, a));
      const body: UserOrderPage = sheetOf(own, sheet);
      return HttpResponse.json(body);
    }),
  ];
}

function emptyPortfolio(userId: number, date: string | null): Portfolio {
  return {
    userId,
    date,
    positions: [],
    totals: { balance: 0, invested: 0, value: 0, profit: 0, realizedProfit: 0, equity: 0 },
  };
}
