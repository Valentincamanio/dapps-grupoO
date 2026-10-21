// Handlers de MSW de las consultas de cotizaciones (contracts/quotes-api.yaml). Van antes que
// los de players.ts en handlers.ts, porque */api/players/:id también captura "ranking".
import { http, HttpResponse } from 'msw';
import type { ApiError, League, Position } from '../../src/types/api';
import { LEAGUES, POSITIONS } from '../../src/types/api';
import type { PlayerQuote, QuoteHistoryPage, RankingPage } from '../../src/types/quotes';
import { PLAYERS } from './players';
import { ACTIVE_STRATEGY, currentQuoteOf, quoteHistoryOf, RANKING_ROWS } from './quotes';

const INVALID_PARAMS = 'Los parámetros enviados no son válidos.';
const MAX_BATCH_IDS = 50;
const MAX_HISTORY_SIZE = 100;
const MAX_RANKING_SIZE = 50;
// Argentina, UTC−3 sin horario de verano
const ARGENTINA_OFFSET_MS = 3 * 60 * 60 * 1000;
const DAY_MS = 24 * 60 * 60 * 1000;

function apiError(status: number, error: string, message: string, path: string): Response {
  const body: ApiError = { timestamp: new Date().toISOString(), status, error, message, path };
  return HttpResponse.json(body, { status });
}

function badRequest(path: string, message = INVALID_PARAMS): Response {
  return apiError(400, 'Bad Request', message, path);
}

function notFound(path: string, message: string): Response {
  return apiError(404, 'Not Found', message, path);
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

// Comienzo del día en Argentina, como instante en milisegundos
function dayStart(day: string): number {
  return Date.parse(`${day}T00:00:00Z`) + ARGENTINA_OFFSET_MS;
}

function todayInArgentina(): string {
  return new Date(Date.now() - ARGENTINA_OFFSET_MS).toISOString().slice(0, 10);
}

// El id del path es numérico y el jugador existe; si no, la respuesta de error del backend
function findPlayer(raw: string, path: string): number | Response {
  if (!/^\d+$/.test(raw) || Number(raw) < 1) return badRequest(path);
  const id = Number(raw);
  if (!PLAYERS.some((player) => player.id === id)) {
    return notFound(path, `No existe un jugador con id ${raw}.`);
  }
  return id;
}

function withPlayerId(playerId: number): PlayerQuote | undefined {
  const quote = currentQuoteOf(playerId);
  return quote ? { playerId, ...quote } : undefined;
}

function pageOf<T>(items: T[], page: number, size: number) {
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

export const quoteHandlers = [
  http.get('*/api/players/quotes/current', ({ request }) => {
    const path = '/players/quotes/current';
    const raw = new URL(request.url).searchParams.get('ids');
    if (raw === null || raw === '') return badRequest(path);
    const parts = raw.split(',');
    const valid = parts.every((part) => /^\d+$/.test(part.trim()) && Number(part) >= 1);
    if (!valid || parts.length > MAX_BATCH_IDS) return badRequest(path);

    const ids = [...new Set(parts.map((part) => Number(part)))];
    const quotes = ids.flatMap((id) => withPlayerId(id) ?? []);
    return HttpResponse.json({ quotes });
  }),

  http.get('*/api/players/ranking', ({ request }) => {
    const path = '/players/ranking';
    const params = new URL(request.url).searchParams;
    const size = parseInteger(params.get('size'), 10);
    const page = parseInteger(params.get('page'), 0);
    const position = params.get('position');
    const league = params.get('league');
    const team = params.get('team');

    const invalid =
      !(size >= 1 && size <= MAX_RANKING_SIZE) ||
      !(page >= 0) ||
      (position !== null && !POSITIONS.includes(position as Position)) ||
      (league !== null && !LEAGUES.includes(league as League)) ||
      (team !== null && team.trim() === '');
    if (invalid) return badRequest(path);

    const filtered = RANKING_ROWS.filter(
      (row) =>
        (position === null || row.position === position) &&
        (league === null || row.league === league) &&
        (team === null || row.team.toLowerCase() === team.trim().toLowerCase()),
    );
    const body: RankingPage = { activeStrategy: ACTIVE_STRATEGY, ...pageOf(filtered, page, size) };
    return HttpResponse.json(body);
  }),

  http.get('*/api/players/:id/quotes/current', ({ params }) => {
    const raw = String(params.id);
    const path = `/players/${raw}/quotes/current`;
    const id = findPlayer(raw, path);
    if (id instanceof Response) return id;

    const quote = withPlayerId(id);
    if (!quote) return notFound(path, `El jugador ${id} no tiene cotización.`);
    return HttpResponse.json(quote);
  }),

  http.get('*/api/players/:id/quotes/at', ({ params, request }) => {
    const raw = String(params.id);
    const path = `/players/${raw}/quotes/at`;
    const id = findPlayer(raw, path);
    if (id instanceof Response) return id;

    const date = new URL(request.url).searchParams.get('date');
    if (date === null || date === '') return badRequest(path);
    if (!isValidDay(date)) return invalidDay(path, date);
    if (date > todayInArgentina()) return badRequest(path, `La fecha ${date} es futura.`);

    // La última que ya regía al final de ese día
    const endOfDay = dayStart(date) + DAY_MS;
    const quote = quoteHistoryOf(id)
      .filter((candidate) => Date.parse(candidate.validFrom) < endOfDay)
      .at(-1);
    if (!quote) return notFound(path, `El jugador ${id} no tiene cotización al ${date}.`);
    return HttpResponse.json({ playerId: id, ...quote });
  }),

  http.get('*/api/players/:id/quotes', ({ params, request }) => {
    const raw = String(params.id);
    const path = `/players/${raw}/quotes`;
    const id = findPlayer(raw, path);
    if (id instanceof Response) return id;

    const search = new URL(request.url).searchParams;
    const from = search.get('from');
    const to = search.get('to');
    const size = parseInteger(search.get('size'), 20);
    const page = parseInteger(search.get('page'), 0);
    if (from !== null && !isValidDay(from)) return invalidDay(path, from);
    if (to !== null && !isValidDay(to)) return invalidDay(path, to);
    if (from !== null && to !== null && from > to) {
      return badRequest(path, `El inicio del rango (${from}) es posterior al final (${to}).`);
    }
    if (!(size >= 1 && size <= MAX_HISTORY_SIZE) || !(page >= 0)) return badRequest(path);

    // Los dos extremos incluidos: de from 00:00 al final del día to, hora de Argentina
    const start = from === null ? -Infinity : dayStart(from);
    const end = to === null ? Infinity : dayStart(to) + DAY_MS;
    const inRange = quoteHistoryOf(id).filter((quote) => {
      const validFrom = Date.parse(quote.validFrom);
      return validFrom >= start && validFrom < end;
    });
    const body: QuoteHistoryPage = { playerId: id, ...pageOf(inRange, page, size) };
    return HttpResponse.json(body);
  }),
];
