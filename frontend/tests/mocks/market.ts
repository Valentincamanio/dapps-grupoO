// Mercado de prueba sobre los jugadores de players.ts, con las formas de
// specs/008-mercado-tokens-portfolio/contracts/market-api.yaml. Los números siguen los ejemplos del
// spec: 10 tokens a 1,0000 que valen 10,30 con la cotización de 103,00; una compra parcial de 10 con 4
// ejecutados y 6 pendientes reservados a 1,0300 (6,18); 10 a 1,0000 y 5 a 1,0300 con promedio 1,0100,
// y la venta de 5 a 1,0500 con +0,20 de ganancia realizada.
import type {
  MarketOrder,
  PlayerRef,
  Portfolio,
  PortfolioPosition,
  QuoteUsed,
  Transaction,
  UserOrder,
} from '../../src/types/market';
import { PLAYERS } from './players';
import { currentQuoteOf, STRATEGY_A } from './quotes';

// El usuario sembrado de handlers.ts, otro usuario y el superusuario
export const MARKET_USER_ID = 1;
export const OTHER_USER_ID = 7;
export const SUPERUSER_ID = 99;

// El día en que se registró el usuario sembrado, en hora de Argentina
export const REGISTRATION_DAY = '2026-08-17';
// Lo que el usuario sembrado tiene reservado: el remanente de la compra 302
export const SEED_RESERVED_BALANCE = 6.18;

// Orden inicial con 87 tokens sin vender: se compra a 1,0000 y la venta está bloqueada
export const INITIAL_ORDER_PLAYER_ID = 1;
// Orden inicial intacta, con los 100 tokens
export const FRESH_INITIAL_PLAYER_ID = 3;
// Orden inicial agotada: el usuario sembrado tiene 10 tokens, 5 comprometidos en una venta
export const SOLD_OUT_PLAYER_ID = 4;
// Compra propia parcial, con 6 tokens pendientes y su reserva
export const PENDING_BUY_PLAYER_ID = 5;

// Órdenes del usuario sembrado en los cuatro estados
export const EXECUTED_ORDER_ID = 301;
export const PARTIAL_ORDER_ID = 302;
export const PENDING_ORDER_ID = 303;
export const CANCELLED_ORDER_ID = 304;
// La orden inicial del jugador 1: no es del usuario
export const INITIAL_ORDER_ID = 101;
export const MISSING_ORDER_ID = 9999;

// Mensajes del backend (contracts/market-api.yaml y data-model §4)
export const MARKET_MESSAGES = {
  forbidden: 'No tiene permisos para acceder a este recurso.',
  accountForbidden: 'No tiene permiso para ver la cuenta de otro usuario.',
  notOwned: 'La orden no pertenece al usuario.',
  executed: 'La orden ya está ejecutada.',
  cancelled: 'La orden ya está cancelada.',
  withoutQuote: 'El jugador no se puede operar: todavía no tiene cotización.',
  withoutInitialOrder: 'El jugador no se puede operar: todavía no tiene su orden inicial.',
  inactive: 'El jugador no se puede operar: está inactivo.',
  invalidQuantity: 'La cantidad tiene que ser un entero entre 1 y 100.',
  invalidRequest: 'El request tiene datos inválidos.',
  invalidParams: 'Los parámetros enviados no son válidos.',
};

// Montos de los mensajes como los escribe el backend: coma decimal, sin separador de miles
export function creditsText(amount: number): string {
  return amount.toFixed(2).replace('.', ',');
}

function tokensText(quantity: number): string {
  return quantity === 1 ? '1 token' : `${quantity} tokens`;
}

export function insufficientCreditsMessage(needed: number, available: number): string {
  return `Saldo insuficiente: se necesitan ${creditsText(needed)} créditos y el saldo libre es ${creditsText(available)}.`;
}

export function insufficientTokensMessage(free: number): string {
  return `Tokens insuficientes: tiene ${tokensText(free)} ${free === 1 ? 'libre' : 'libres'} de este jugador.`;
}

export function saleBlockedMessage(remaining: number): string {
  const verb = remaining === 1 ? 'queda' : 'quedan';
  return `Todavía no se puede vender: al administrador le ${verb} ${tokensText(remaining)} de este jugador.`;
}

export function playerRef(playerId: number): PlayerRef {
  const player = PLAYERS.find((candidate) => candidate.id === playerId);
  if (!player) throw new Error(`No hay un jugador de prueba con id ${playerId}`);
  return { id: player.id, name: player.name, team: player.team, league: player.league };
}

// La cotización actual del jugador de quotes.ts, como la usa una operación
export function currentQuoteUsed(playerId: number): QuoteUsed | undefined {
  const quote = currentQuoteOf(playerId);
  if (!quote) return undefined;
  return {
    validFrom: quote.validFrom,
    value: quote.value,
    tokenPrice: quote.tokenPrice,
    score: quote.score ?? 0,
    strategy: quote.strategy,
  };
}

function quoteUsed(value: number, score: number, validFrom: string): QuoteUsed {
  return { validFrom, value, tokenPrice: value / 100, score, strategy: STRATEGY_A };
}

const INITIAL_QUOTE = quoteUsed(100, 0, '2026-08-17T03:00:00Z');
const QUOTE_103 = quoteUsed(103, 0.3, '2026-08-24T03:00:00Z');
const QUOTE_105 = quoteUsed(105, 0.5, '2026-08-31T03:00:00Z');

interface OrderSeed {
  id: number;
  playerId: number;
  side: UserOrder['side'];
  requested: number;
  executed: number;
  cancelled?: number;
  reservedCredits?: number;
  createdAt: string;
}

function order(seed: OrderSeed, ownerType: UserOrder['ownerType']): UserOrder {
  const cancelled = seed.cancelled ?? 0;
  const pending = seed.requested - seed.executed - cancelled;
  let status: UserOrder['status'] = 'EXECUTED';
  if (pending > 0) status = seed.executed > 0 ? 'PARTIAL' : 'PENDING';
  else if (cancelled > 0) status = 'CANCELLED';
  return {
    id: seed.id,
    player: playerRef(seed.playerId),
    side: seed.side,
    ownerType,
    status,
    requestedQuantity: seed.requested,
    executedQuantity: seed.executed,
    pendingQuantity: pending,
    createdAt: seed.createdAt,
    cancelledQuantity: cancelled,
    reservedCredits: seed.reservedCredits ?? 0,
  };
}

// Una orden con su dueño, que el listado público no muestra
export interface OwnedOrder {
  ownerId: number;
  order: UserOrder;
}

function initialOrder(id: number, playerId: number, executed: number): OwnedOrder {
  const seed = { id, playerId, side: 'SELL' as const, requested: 100, executed, createdAt: '2026-10-28T12:00:00Z' };
  return { ownerId: SUPERUSER_ID, order: order(seed, 'ADMIN') };
}

function userOrder(ownerId: number, seed: OrderSeed): OwnedOrder {
  return { ownerId, order: order(seed, 'USER') };
}

// Las órdenes del mercado al empezar cada test, como copias nuevas
export function seedOrders(): OwnedOrder[] {
  return [
    initialOrder(INITIAL_ORDER_ID, INITIAL_ORDER_PLAYER_ID, 13),
    initialOrder(103, FRESH_INITIAL_PLAYER_ID, 0),
    initialOrder(104, SOLD_OUT_PLAYER_ID, 100),
    initialOrder(105, PENDING_BUY_PLAYER_ID, 100),
    initialOrder(106, 6, 100),
    userOrder(OTHER_USER_ID, {
      id: 201, playerId: SOLD_OUT_PLAYER_ID, side: 'SELL', requested: 5, executed: 0,
      createdAt: '2026-10-29T15:00:00Z',
    }),
    userOrder(MARKET_USER_ID, {
      id: 307, playerId: SOLD_OUT_PLAYER_ID, side: 'BUY', requested: 10, executed: 10,
      createdAt: '2026-08-24T15:00:00Z',
    }),
    userOrder(MARKET_USER_ID, {
      id: 306, playerId: SOLD_OUT_PLAYER_ID, side: 'BUY', requested: 5, executed: 5,
      createdAt: '2026-08-31T15:00:00Z',
    }),
    userOrder(MARKET_USER_ID, {
      id: 305, playerId: SOLD_OUT_PLAYER_ID, side: 'SELL', requested: 5, executed: 5,
      createdAt: '2026-09-01T15:00:00Z',
    }),
    userOrder(MARKET_USER_ID, {
      id: EXECUTED_ORDER_ID, playerId: INITIAL_ORDER_PLAYER_ID, side: 'BUY', requested: 10, executed: 10,
      createdAt: '2026-10-28T13:00:00Z',
    }),
    userOrder(MARKET_USER_ID, {
      id: PARTIAL_ORDER_ID, playerId: PENDING_BUY_PLAYER_ID, side: 'BUY', requested: 10, executed: 4,
      reservedCredits: SEED_RESERVED_BALANCE, createdAt: '2026-10-29T16:00:00Z',
    }),
    userOrder(MARKET_USER_ID, {
      id: PENDING_ORDER_ID, playerId: SOLD_OUT_PLAYER_ID, side: 'SELL', requested: 5, executed: 0,
      createdAt: '2026-10-30T10:00:00Z',
    }),
    userOrder(MARKET_USER_ID, {
      id: CANCELLED_ORDER_ID, playerId: 6, side: 'BUY', requested: 10, executed: 0, cancelled: 10,
      createdAt: '2026-10-30T11:00:00Z',
    }),
  ];
}

// Lo que se ve de una orden en el listado público: sin dueño, sin reserva ni cancelados
export function toMarketOrder(userOrder: UserOrder): MarketOrder {
  return {
    id: userOrder.id,
    player: userOrder.player,
    side: userOrder.side,
    ownerType: userOrder.ownerType,
    status: userOrder.status,
    requestedQuantity: userOrder.requestedQuantity,
    executedQuantity: userOrder.executedQuantity,
    pendingQuantity: userOrder.pendingQuantity,
    createdAt: userOrder.createdAt,
  };
}

// Tokens del usuario sembrado por jugador, con los comprometidos en sus ventas pendientes
export function seedHoldings(): Map<number, { tokens: number; committed: number }> {
  return new Map([
    [INITIAL_ORDER_PLAYER_ID, { tokens: 10, committed: 0 }],
    [SOLD_OUT_PLAYER_ID, { tokens: 10, committed: 5 }],
  ]);
}

function position(
  playerId: number,
  tokens: number,
  averagePrice: number,
  quote: QuoteUsed,
  committed?: number,
): PortfolioPosition {
  const player = PLAYERS.find((candidate) => candidate.id === playerId);
  const invested = Math.round(tokens * averagePrice * 100) / 100;
  const value = Math.round(tokens * quote.tokenPrice * 100) / 100;
  const profit = Math.round((value - invested) * 100) / 100;
  return {
    player: { ...playerRef(playerId), position: player?.position ?? 'FORWARD', active: true },
    tokens,
    sharePercent: tokens,
    ...(committed === undefined ? {} : { committed }),
    averagePrice,
    invested,
    quote,
    value,
    profit,
    profitPercent: Math.round((profit / invested) * 10000) / 100,
  };
}

// Portfolio actual del usuario sembrado: dos posiciones que valen 10,30 cada una
export const CURRENT_PORTFOLIO: Portfolio = {
  userId: MARKET_USER_ID,
  date: null,
  positions: [
    position(INITIAL_ORDER_PLAYER_ID, 10, 1, QUOTE_103, 0),
    position(SOLD_OUT_PLAYER_ID, 10, 1.01, QUOTE_103, 5),
  ],
  totals: {
    balance: 1000,
    availableBalance: 993.82,
    reservedBalance: SEED_RESERVED_BALANCE,
    invested: 20.1,
    value: 20.6,
    profit: 0.5,
    realizedProfit: 0.2,
    equity: 1020.6,
  },
};

// Portfolio del usuario sembrado al 25/08/2026: sin libre ni reservado, como pide FR-040
export const PORTFOLIO_DATE = '2026-08-25';
export const PORTFOLIO_AT_DATE: Portfolio = {
  userId: MARKET_USER_ID,
  date: PORTFOLIO_DATE,
  positions: [position(SOLD_OUT_PLAYER_ID, 10, 1, QUOTE_103)],
  totals: {
    balance: 990,
    invested: 10,
    value: 10.3,
    profit: 0.3,
    realizedProfit: 0,
    equity: 1000.3,
  },
};

// Historial del usuario sembrado, de la más nueva a la más vieja: contrapartes administrador y usuario
export const TRANSACTIONS: Transaction[] = [
  {
    id: 404,
    executedAt: '2026-10-28T13:00:00Z',
    player: playerRef(INITIAL_ORDER_PLAYER_ID),
    side: 'BUY',
    quantity: 10,
    tokenPrice: 1,
    total: 10,
    counterparty: 'ADMIN',
    orderId: EXECUTED_ORDER_ID,
    quote: QUOTE_103,
  },
  {
    id: 403,
    executedAt: '2026-09-01T15:00:00Z',
    player: playerRef(SOLD_OUT_PLAYER_ID),
    side: 'SELL',
    quantity: 5,
    tokenPrice: 1.05,
    total: 5.25,
    counterparty: 'USER',
    orderId: 305,
    quote: QUOTE_105,
    realizedProfit: 0.2,
  },
  {
    id: 402,
    executedAt: '2026-08-31T15:00:00Z',
    player: playerRef(SOLD_OUT_PLAYER_ID),
    side: 'BUY',
    quantity: 5,
    tokenPrice: 1.03,
    total: 5.15,
    counterparty: 'USER',
    orderId: 306,
    quote: QUOTE_103,
  },
  {
    id: 401,
    executedAt: '2026-08-24T15:00:00Z',
    player: playerRef(SOLD_OUT_PLAYER_ID),
    side: 'BUY',
    quantity: 10,
    tokenPrice: 1,
    total: 10,
    counterparty: 'ADMIN',
    orderId: 307,
    quote: INITIAL_QUOTE,
  },
];
