// Reglas y textos de los potenciados: el porcentaje, el multiplicador, las semanas y la cuenta
// regresiva hasta el cierre.
import type { League, Player } from '../types/api';
import type { BoostWeekStatus } from '../types/quotes';
import { normalize } from './search';

export const BOOSTS_PER_WEEK = 4;
export const MIN_BOOST_PERCENT = 200;
export const MAX_BOOST_PERCENT = 400;
export const BOOST_PERCENT_STEP = 25;

const MONTHS = ['ene', 'feb', 'mar', 'abr', 'may', 'jun', 'jul', 'ago', 'sep', 'oct', 'nov', 'dic'];
const DAYS_PER_WEEK = 7;
const MS_PER_MINUTE = 60_000;
const MINUTES_PER_HOUR = 60;
const MINUTES_PER_DAY = 24 * MINUTES_PER_HOUR;

const multiplierFormatter = new Intl.NumberFormat('es-AR', { maximumFractionDigits: 2 });

// 300 es ×3 y 250 es ×2,5
export function formatMultiplier(percent: number): string {
  return `×${multiplierFormatter.format(percent / 100)}`;
}

export function formatBoostPercent(percent: number): string {
  return `${percent} %`;
}

// Qué tanto de la barra de volatilidad se llena: el máximo la llena entera
export function boostMeterWidth(percent: number): number {
  return Math.min(100, (percent / MAX_BOOST_PERCENT) * 100);
}

function parseDay(day: string): Date {
  const [year, month, date] = day.split('-').map(Number);
  return new Date(Date.UTC(year, month - 1, date));
}

function shortDay(day: string): string {
  const date = parseDay(day);
  return `${date.getUTCDate()} ${MONTHS[date.getUTCMonth()]}`;
}

// "27 oct – 2 nov"
export function boostWeekRange(weekStart: string, weekEnd: string): string {
  return `${shortDay(weekStart)} – ${shortDay(weekEnd)}`;
}

// El mismo día corrido unas semanas, AAAA-MM-DD
export function addWeeks(day: string, weeks: number): string {
  const date = parseDay(day);
  date.setUTCDate(date.getUTCDate() + weeks * DAYS_PER_WEEK);
  return date.toISOString().slice(0, 10);
}

// Cómo se llama una semana según cuántas semanas la separan de la en curso
export function relativeWeekLabel(offset: number): string {
  if (offset === 0) return 'esta semana';
  if (offset === 1) return 'próxima semana';
  if (offset === -1) return 'semana pasada';
  return offset > 1 ? `en ${offset} semanas` : `hace ${-offset} semanas`;
}

export function boostWeekStatusLabel(status: BoostWeekStatus, hasPlayers: boolean): string {
  if (status === 'CLOSED') return 'cerrada';
  if (!hasPlayers) return 'sin elegir';
  return status === 'CURRENT' ? 'en curso' : 'programada';
}

// Lo que falta hasta el cierre, en días, horas y minutos: "1d 12h 49m", "3h 5m", "12m"
export function timeLeft(endsAt: string, now: number): string {
  const minutes = Math.max(0, Math.floor((Date.parse(endsAt) - now) / MS_PER_MINUTE));
  const days = Math.floor(minutes / MINUTES_PER_DAY);
  const hours = Math.floor((minutes % MINUTES_PER_DAY) / MINUTES_PER_HOUR);
  const rest = minutes % MINUTES_PER_HOUR;
  if (days > 0) return `${days}d ${hours}h ${rest}m`;
  if (hours > 0) return `${hours}h ${rest}m`;
  return `${rest}m`;
}

// Los jugadores que se ofrecen para potenciar: por nombre o equipo, sin distinguir mayúsculas ni
// tildes, y por liga. Respeta el orden del catálogo
export function boostCandidates(players: Player[], query: string, league: League | null): Player[] {
  const needle = normalize(query);
  return players.filter(
    (player) =>
      (league === null || player.league === league) &&
      (needle === '' || normalize(`${player.name} ${player.team}`).includes(needle)),
  );
}
