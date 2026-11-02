// Potenciados simulados (GET /players/boosts/current, GET y PUT /quotes/boosts). La semana en curso
// es siempre la del lunes BOOST_CURRENT_MONDAY, sin importar la fecha real; su cierre queda a un día
// y medio del momento en que se reinicia el mock. Arranca sin potenciados.
import { http, HttpResponse } from 'msw';
import type { ApiError } from '../../src/types/api';
import type { BoostInput, BoostWeekStatus, BoostedWeek } from '../../src/types/quotes';
import { PLAYERS } from './players';

export const BOOST_CURRENT_MONDAY = '2026-10-26';

const DAYS_PER_WEEK = 7;
const MS_PER_DAY = 24 * 60 * 60 * 1000;
const CURRENT_ENDS_IN_MS = 1.5 * MS_PER_DAY;
const MIN_PERCENT = 200;
const MAX_PERCENT = 400;
const PERCENT_STEP = 25;
const PLAYERS_PER_WEEK = 4;

// lunes -> potenciados elegidos, en orden
let weeks = new Map<string, BoostInput[]>();
let currentEndsAt = '';

export function resetBoostMock(): void {
  weeks = new Map();
  currentEndsAt = new Date(Date.now() + CURRENT_ENDS_IN_MS).toISOString();
}

resetBoostMock();

// Deja guardados los potenciados de una semana, como si el administrador ya los hubiera elegido
export function setMockBoosts(monday: string, players: BoostInput[]): void {
  weeks.set(monday, structuredClone(players));
}

// Los potenciados guardados de una semana, para verificar qué cambió
export function mockBoosts(monday: string): BoostInput[] {
  return structuredClone(weeks.get(monday) ?? []);
}

function parseDay(day: string): Date | null {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(day)) return null;
  const date = new Date(`${day}T00:00:00Z`);
  return Number.isNaN(date.getTime()) || date.toISOString().slice(0, 10) !== day ? null : date;
}

function addDays(day: string, days: number): string {
  const date = new Date(`${day}T00:00:00Z`);
  date.setUTCDate(date.getUTCDate() + days);
  return date.toISOString().slice(0, 10);
}

// El lunes de la semana que contiene el día
function mondayOf(date: Date): string {
  const sinceMonday = (date.getUTCDay() + DAYS_PER_WEEK - 1) % DAYS_PER_WEEK;
  return addDays(date.toISOString().slice(0, 10), -sinceMonday);
}

function statusOf(monday: string): BoostWeekStatus {
  if (monday < BOOST_CURRENT_MONDAY) return 'CLOSED';
  return monday === BOOST_CURRENT_MONDAY ? 'CURRENT' : 'UPCOMING';
}

function endsAtOf(monday: string): string {
  const weeksAway = (Date.parse(monday) - Date.parse(BOOST_CURRENT_MONDAY)) / (DAYS_PER_WEEK * MS_PER_DAY);
  return new Date(Date.parse(currentEndsAt) + weeksAway * DAYS_PER_WEEK * MS_PER_DAY).toISOString();
}

function weekOf(monday: string): BoostedWeek {
  const players = (weeks.get(monday) ?? []).flatMap(({ playerId, percent }) => {
    const player = PLAYERS.find((candidate) => candidate.id === playerId);
    return player
      ? [{ playerId, name: player.name, position: player.position, team: player.team, league: player.league, percent }]
      : [];
  });
  return {
    weekStart: monday,
    weekEnd: addDays(monday, DAYS_PER_WEEK - 1),
    endsAt: endsAtOf(monday),
    status: statusOf(monday),
    players,
  };
}

function apiError(status: number, error: string, message: string, path: string): Response {
  const body: ApiError = { timestamp: new Date().toISOString(), status, error, message, path };
  return HttpResponse.json(body, { status });
}

function requireCredential(request: Request, path: string): Response | null {
  if (request.headers.get('Authorization')) return null;
  return apiError(401, 'Unauthorized', 'Se requiere una credencial.', path);
}

function isValidPercent(percent: unknown): boolean {
  return (
    typeof percent === 'number' &&
    percent >= MIN_PERCENT &&
    percent <= MAX_PERCENT &&
    percent % PERCENT_STEP === 0
  );
}

export const boostHandlers = [
  http.get('*/api/players/boosts/current', () => HttpResponse.json(weekOf(BOOST_CURRENT_MONDAY))),

  http.get('*/api/quotes/boosts', ({ request }) => {
    const path = '/quotes/boosts';
    const denied = requireCredential(request, path);
    if (denied) return denied;
    const raw = new URL(request.url).searchParams.get('week');
    if (raw === null) return HttpResponse.json(weekOf(BOOST_CURRENT_MONDAY));
    const day = parseDay(raw);
    if (!day) return apiError(400, 'Bad Request', 'Los parámetros enviados no son válidos.', path);
    return HttpResponse.json(weekOf(mondayOf(day)));
  }),

  http.put('*/api/quotes/boosts/:week', async ({ params, request }) => {
    const path = `/quotes/boosts/${String(params.week)}`;
    const denied = requireCredential(request, path);
    if (denied) return denied;
    const day = parseDay(String(params.week));
    if (!day) return apiError(400, 'Bad Request', 'Los parámetros enviados no son válidos.', path);
    const monday = mondayOf(day);
    if (statusOf(monday) === 'CLOSED') {
      return apiError(409, 'Conflict', `La semana del ${monday} ya cerró: sus potenciados no se pueden cambiar.`, path);
    }
    const body = (await request.json()) as { players?: BoostInput[] };
    const players = body.players ?? [];
    if (players.length !== 0 && players.length !== PLAYERS_PER_WEEK) {
      return apiError(
        400,
        'Bad Request',
        `Una semana lleva exactamente 4 potenciados, o ninguno, y se eligieron ${players.length}.`,
        path,
      );
    }
    if (!players.every(({ percent }) => isValidPercent(percent))) {
      return apiError(400, 'Bad Request', 'El potenciado va de 200 % a 400 %, de a 25 %.', path);
    }
    const missing = players.find(({ playerId }) => !PLAYERS.some((player) => player.id === playerId));
    if (missing) return apiError(404, 'Not Found', `No se encontró el jugador con id ${missing.playerId}.`, path);
    weeks.set(monday, structuredClone(players));
    return HttpResponse.json(weekOf(monday));
  }),
];
