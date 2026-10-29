// Etiquetas, estimados y formatos del mercado (research D27). Sin símbolos de moneda: los importes
// son créditos, con formatCredits y formatTokenPrice de utils/quotes.ts.
import type { OrderSide, OrderStatus, OwnerType, TradeBlock, TradeEstimate } from '../types/market';
import { amountDirection, type VariationDirection } from './quotes';

const SIDE_LABELS: Record<OrderSide, string> = {
  BUY: 'compra',
  SELL: 'venta',
};

const STATUS_LABELS: Record<OrderStatus, string> = {
  PENDING: 'pendiente',
  PARTIAL: 'parcial',
  EXECUTED: 'ejecutada',
  CANCELLED: 'cancelada',
};

const OWNER_TYPE_LABELS: Record<OwnerType, string> = {
  USER: 'usuario',
  ADMIN: 'administrador',
};

// Contra quién operó el usuario, sin decir quién es
const COUNTERPARTY_LABELS: Record<OwnerType, string> = {
  USER: 'otro usuario',
  ADMIN: 'administrador',
};

// El texto de una ganancia o pérdida: se entiende sin el color (FR-070)
const PROFIT_TEXT: Record<VariationDirection, string> = {
  up: 'gana',
  down: 'pierde',
  flat: 'sin cambios',
};

// El precio por token de la orden inicial del superusuario
export const INITIAL_TOKEN_PRICE = 1;

// Los precios por token tienen cuatro decimales y los totales dos
const PRICE_UNITS = 10_000;
const CENTS = 100;
const HALF_CENT_IN_PRICE_UNITS = 50;

// Con signo salvo el cero: +0,30, -0,30, 0,00 (el mismo criterio que formatSignedCredits)
const signedFormatter = new Intl.NumberFormat('es-AR', {
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
  signDisplay: 'exceptZero',
});

// El score tiene seis decimales: se muestran los que tenga, con al menos dos (0,30, 0,123456)
const scoreFormatter = new Intl.NumberFormat('es-AR', {
  minimumFractionDigits: 2,
  maximumFractionDigits: 6,
});

export function sideLabel(side: OrderSide): string {
  return SIDE_LABELS[side];
}

export function statusLabel(status: OrderStatus): string {
  return STATUS_LABELS[status];
}

export function ownerTypeLabel(ownerType: OwnerType): string {
  return OWNER_TYPE_LABELS[ownerType];
}

export function counterpartyLabel(counterparty: OwnerType): string {
  return COUNTERPARTY_LABELS[counterparty];
}

// El total en centavos, en enteros: cantidad × precio por token redondeado una sola vez, hacia
// arriba desde la mitad, como el backend (FR-003). Con decimales de coma flotante 10 × 1,0345 daría
// 10,34 en vez de 10,35.
function totalInCents(quantity: number, tokenPrice: number): number {
  const priceUnits = Math.round(tokenPrice * PRICE_UNITS);
  return Math.floor((quantity * priceUnits + HALF_CENT_IN_PRICE_UNITS) / CENTS);
}

// Lo que cuestan esos tokens a ese precio por token, con dos decimales
export function tradeTotal(quantity: number, tokenPrice: number): number {
  return totalInCents(quantity, tokenPrice) / CENTS;
}

// Una compra toma primero los tokens que le quedan al superusuario, a 1,0000, y el resto al precio
// vigente (FR-022). Es un estimado: el precio final es el del momento de la ejecución (FR-063).
export function estimateBuy(quantity: number, initialPending: number, tokenPrice: number): TradeEstimate {
  const atInitialPrice = Math.max(0, Math.min(quantity, initialPending));
  const atQuotePrice = Math.max(0, quantity - atInitialPrice);
  const cents = totalInCents(atInitialPrice, INITIAL_TOKEN_PRICE) + totalInCents(atQuotePrice, tokenPrice);
  return { atInitialPrice, atQuotePrice, total: cents / CENTS };
}

// Una venta se ejecuta siempre al precio vigente: mientras el superusuario tenga tokens, no se vende
export function estimateSell(quantity: number, tokenPrice: number): TradeEstimate {
  const atQuotePrice = Math.max(0, quantity);
  return { atInitialPrice: 0, atQuotePrice, total: tradeTotal(atQuotePrice, tokenPrice) };
}

// Por qué no se puede operar desde la ficha (FR-064): un jugador inactivo o sin cotización no se
// opera, y mientras el superusuario tenga tokens solo se compra. null si se puede comprar y vender.
export function tradeBlockOf(active: boolean, hasQuote: boolean, initialPending: number): TradeBlock {
  if (!active) return { kind: 'not-tradable', reason: 'el jugador está inactivo' };
  if (!hasQuote) return { kind: 'not-tradable', reason: 'el jugador todavía no tiene cotización' };
  if (initialPending > 0) return { kind: 'sale-blocked', remainingTokens: initialPending };
  return null;
}

// Una cantidad de tokens en palabras: 1 token, 10 tokens
export function formatTokens(quantity: number): string {
  return quantity === 1 ? '1 token' : `${quantity} tokens`;
}

// Los tokens que quedan pendientes, con el adjetivo concordado: 1 token pendiente, 6 tokens pendientes
export function formatPendingTokens(quantity: number): string {
  return `${formatTokens(quantity)} ${quantity === 1 ? 'pendiente' : 'pendientes'}`;
}

// Ganancia, pérdida o diferencia con signo salvo el cero, p. ej. +0,30
export function formatSigned(amount: number): string {
  return signedFormatter.format(amount);
}

// Porcentaje con signo salvo el cero, p. ej. +3,00 %
export function formatSignedPercent(percent: number): string {
  return `${signedFormatter.format(percent)} %`;
}

export function profitText(amount: number): string {
  return PROFIT_TEXT[amountDirection(amount)];
}

// Score de una cotización, de -1 a +1, p. ej. 0,30
export function formatScore(score: number): string {
  return scoreFormatter.format(score);
}
