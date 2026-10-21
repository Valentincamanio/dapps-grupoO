// Formato de cotizaciones para mostrar en pantalla (research D24). Sin símbolos de moneda:
// los importes son créditos ("103,00 cr.").
import type { Quote, QuoteType, Variation } from '../types/quotes';
import { formatBalance } from './format';

export type VariationDirection = 'up' | 'down' | 'flat';

const tokenPriceFormatter = new Intl.NumberFormat('es-AR', {
  minimumFractionDigits: 4,
  maximumFractionDigits: 4,
});

// Con signo salvo el cero: +3,00, -2,00, 0,00
const signedFormatter = new Intl.NumberFormat('es-AR', {
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
  signDisplay: 'exceptZero',
});

const QUOTE_TYPE_LABELS: Record<QuoteType, string> = {
  INITIAL: 'inicial',
  CALCULATED: 'calculada',
  REQUOTED: 'recotizada',
};

const DIRECTION_TEXT: Record<VariationDirection, string> = {
  up: 'sube',
  down: 'baja',
  flat: 'sin cambios',
};

// Valor del jugador en créditos, p. ej. 103,00 cr.
export function formatCredits(value: number): string {
  return `${formatBalance(value)} cr.`;
}

// Precio por token con cuatro decimales, p. ej. 1,0300 cr.
export function formatTokenPrice(tokenPrice: number): string {
  return `${tokenPriceFormatter.format(tokenPrice)} cr.`;
}

// Instante en hora de Argentina, p. ej. 26/10/2026, 06:00
const instantFormatter = new Intl.DateTimeFormat('es-AR', {
  timeZone: 'America/Argentina/Buenos_Aires',
  day: '2-digit',
  month: '2-digit',
  year: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
  hourCycle: 'h23',
});

// Sube, baja o sin cambios según el signo de un monto
export function amountDirection(amount: number): VariationDirection {
  if (amount > 0) return 'up';
  if (amount < 0) return 'down';
  return 'flat';
}

// Sube, baja o sin cambios según el monto (el porcentaje tiene el mismo signo)
export function variationDirection(variation: Variation): VariationDirection {
  return amountDirection(variation.amount);
}

export function variationText(variation: Variation): string {
  return DIRECTION_TEXT[variationDirection(variation)];
}

export function directionText(direction: VariationDirection): string {
  return DIRECTION_TEXT[direction];
}

// Diferencia en créditos con signo salvo el cero, p. ej. +3,00 cr.
export function formatSignedCredits(amount: number): string {
  return `${signedFormatter.format(amount)} cr.`;
}

// Instante ISO 8601 -> DD/MM/AAAA, HH:MM en hora de Argentina
export function formatInstant(instant: string): string {
  return instantFormatter.format(new Date(instant));
}

// Monto y porcentaje con signo, p. ej. +3,00 (+3,00 %)
export function formatVariationAmount(variation: Variation): string {
  return `${signedFormatter.format(variation.amount)} (${signedFormatter.format(variation.percent)} %)`;
}

// Variación completa con texto, p. ej. +3,00 (+3,00 %) sube
export function formatVariation(variation: Variation): string {
  return `${formatVariationAmount(variation)} ${variationText(variation)}`;
}

export function quoteTypeLabel(type: QuoteType): string {
  return QUOTE_TYPE_LABELS[type];
}

// AAAA-MM-DD -> DD/MM/AAAA, sin pasar por Date para no correr el día por la zona horaria
export function formatDay(day: string): string {
  const [year, month, date] = day.split('-');
  return `${date}/${month}/${year}`;
}

// Etiqueta de la semana a partir de su lunes, p. ej. semana del 17/08/2026
export function weekLabel(weekStart: string): string {
  return `semana del ${formatDay(weekStart)}`;
}

// Una cotización por semana: la última por validFrom, así una semana recotizada muestra la
// recotizada. Ordenadas por semana ascendente.
export function lastPerWeek(quotes: Quote[]): Quote[] {
  const byWeek = new Map<string, Quote>();
  for (const quote of quotes) {
    const current = byWeek.get(quote.weekStart);
    if (!current || Date.parse(quote.validFrom) > Date.parse(current.validFrom)) {
      byWeek.set(quote.weekStart, quote);
    }
  }
  return [...byWeek.values()].sort((a, b) => a.weekStart.localeCompare(b.weekStart));
}
