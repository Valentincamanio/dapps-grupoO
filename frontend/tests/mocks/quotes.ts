// Cotizaciones de prueba para los jugadores de players.ts, con las formas de
// specs/007-cotizacion-estrategias/contracts/quotes-api.yaml.
import type { Quote, RankingRow, StrategyRef } from '../../src/types/quotes';
import { PLAYERS } from './players';

export const STRATEGY_A: StrategyRef = { id: 1, name: 'Rendimiento de partido', version: 1 };
export const STRATEGY_B: StrategyRef = { id: 2, name: 'Equilibrada por posición', version: 1 };
export const ACTIVE_STRATEGY = STRATEGY_A;

// Jugador con historial de ejemplo del contrato: inicial, calculadas y una semana recotizada
export const REQUOTED_PLAYER_ID = 1;
// Jugadores del catálogo que todavía no tienen ninguna cotización
export const PLAYERS_WITHOUT_QUOTE = [2, 55];
export const PLAYER_WITHOUT_QUOTE_ID = PLAYERS_WITHOUT_QUOTE[0];
// Jugador que solo tiene la cotización inicial (sin variación)
export const PLAYER_WITH_ONLY_INITIAL_ID = 3;

// Lunes de las semanas cotizadas, hora de Argentina
export const FIRST_WEEK = '2026-08-17';
export const WEEKS = [
  '2026-08-17',
  '2026-08-24',
  '2026-08-31',
  '2026-09-07',
  '2026-09-14',
  '2026-09-21',
  '2026-09-28',
  '2026-10-05',
];

const INITIAL_VALUE = 100;
const MINIMUM_VALUE = 10;

function round(value: number, decimals: number): number {
  const factor = 10 ** decimals;
  return Math.round(value * factor) / factor;
}

// Lunes 00:00 de Argentina en UTC (UTC−3)
function mondayStart(day: string): string {
  return `${day}T03:00:00Z`;
}

function addDays(day: string, days: number): string {
  const date = new Date(`${day}T00:00:00Z`);
  date.setUTCDate(date.getUTCDate() + days);
  return date.toISOString().slice(0, 10);
}

function initialQuote(): Quote {
  return {
    value: INITIAL_VALUE,
    tokenPrice: 1,
    weekStart: FIRST_WEEK,
    validFrom: mondayStart(FIRST_WEEK),
    type: 'INITIAL',
    strategy: STRATEGY_A,
  };
}

// Cotización que parte de base; rige desde validFrom
function quoteFrom(
  base: number,
  value: number,
  weekStart: string,
  validFrom: string,
  type: Quote['type'],
  strategy: StrategyRef,
): Quote {
  const amount = round(value - base, 2);
  return {
    value,
    tokenPrice: round(value / 100, 4),
    variation: { amount, percent: round((amount / base) * 100, 2) },
    weekStart,
    validFrom,
    type,
    strategy,
  };
}

// Calculada del cierre de la semana weekStart: rige desde el lunes siguiente
function calculated(base: number, value: number, weekStart: string): Quote {
  return quoteFrom(base, value, weekStart, mondayStart(addDays(weekStart, 7)), 'CALCULATED', STRATEGY_A);
}

// Ejemplo del contrato: 100,00 -> 103,00 y en la misma semana la recotizada de 104,00 con B,
// medida contra el mismo valor de partida (100,00). La semana siguiente parte de la recotizada.
function requotedHistory(): Quote[] {
  const history: Quote[] = [
    initialQuote(),
    calculated(100, 103, WEEKS[0]),
    quoteFrom(100, 104, WEEKS[0], '2026-08-26T15:30:00Z', 'REQUOTED', STRATEGY_B),
  ];
  const values = [101, 105.5, 108.25, 107, 110.4, 112.4, 115.1];
  let base = 104;
  values.forEach((value, index) => {
    history.push(calculated(base, value, WEEKS[index + 1]));
    base = value;
  });
  return history;
}

// Historial determinístico: una calculada por semana con un cambio de -4 a +4 créditos
function generatedHistory(playerId: number): Quote[] {
  const history: Quote[] = [initialQuote()];
  let base = INITIAL_VALUE;
  WEEKS.forEach((weekStart, index) => {
    const delta = ((playerId * 7 + index * 3) % 9) - 4 + (playerId % 4) * 0.25;
    const value = Math.max(round(base + delta, 2), MINIMUM_VALUE);
    history.push(calculated(base, value, weekStart));
    base = value;
  });
  return history;
}

function historyFor(playerId: number): Quote[] {
  if (PLAYERS_WITHOUT_QUOTE.includes(playerId)) return [];
  if (playerId === REQUOTED_PLAYER_ID) return requotedHistory();
  if (playerId === PLAYER_WITH_ONLY_INITIAL_ID) return [initialQuote()];
  return generatedHistory(playerId);
}

// id de jugador -> historial ordenado por validFrom ascendente
export const QUOTE_HISTORY = new Map<number, Quote[]>(
  PLAYERS.map((player) => [player.id, historyFor(player.id)]),
);

export function quoteHistoryOf(playerId: number): Quote[] {
  return QUOTE_HISTORY.get(playerId) ?? [];
}

// La última por validFrom, o undefined si no tiene cotización
export function currentQuoteOf(playerId: number): Quote | undefined {
  const history = quoteHistoryOf(playerId);
  return history[history.length - 1];
}

// Ranking completo: activos con cotización, por valor descendente, luego nombre e id
export const RANKING_ROWS: RankingRow[] = PLAYERS.flatMap((player) => {
  const quote = currentQuoteOf(player.id);
  if (!quote) return [];
  return [
    {
      playerId: player.id,
      name: player.name,
      position: player.position,
      team: player.team,
      league: player.league,
      teamCrest: null,
      quote,
    },
  ];
}).sort(
  (a, b) =>
    b.quote.value - a.quote.value || a.name.localeCompare(b.name) || a.playerId - b.playerId,
);
