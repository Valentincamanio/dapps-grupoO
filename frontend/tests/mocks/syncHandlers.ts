// Sincronizaciones manuales simuladas (POST /players/sync y POST /matches/stats/sync). Responden
// enseguida con un informe de las cinco ligas, o de la pedida; sin credencial, 401. Los tests
// prueban el 409 y el 503 con server.use.
import { http, HttpResponse } from 'msw';
import type { ApiError, League } from '../../src/types/api';
import { LEAGUES } from '../../src/types/api';
import type { LeagueStatsReport, LeagueSyncReport, StatsSyncReport, SyncReport } from '../../src/types/sync';

export const SYNC_FINISHED_AT = '2026-11-01T19:30:00Z';
// La liga que falla en el informe del catálogo de las cinco ligas
export const FAILING_LEAGUE: League = 'LIGUE_1';
export const FAILURE_REASON = 'la fuente respondió 429';

function apiError(status: number, error: string, message: string, path: string): Response {
  const body: ApiError = { timestamp: new Date().toISOString(), status, error, message, path };
  return HttpResponse.json(body, { status });
}

function leaguesOf(request: Request): League[] | null {
  const raw = new URL(request.url).searchParams.get('league');
  if (raw === null) return [...LEAGUES];
  return LEAGUES.includes(raw as League) ? [raw as League] : null;
}

function catalogLeague(league: League, all: boolean): LeagueSyncReport {
  const failed = all && league === FAILING_LEAGUE;
  return {
    league,
    status: failed ? 'FAILED' : 'SUCCEEDED',
    failureReason: failed ? FAILURE_REASON : null,
    teams: { created: 0, updated: failed ? 0 : 20, skipped: 0 },
    players: { created: failed ? 0 : 3, updated: failed ? 0 : 500, skipped: 0 },
    matches: { created: 0, updated: failed ? 0 : 10, skipped: 0 },
  };
}

function statsLeague(league: League): LeagueStatsReport {
  return { league, status: 'PROCESSED', matchesFromSnapshot: 8, matchesScraped: 2, skippedMatches: [] };
}

export const syncHandlers = [
  http.post('*/api/players/sync', ({ request }) => {
    const path = '/players/sync';
    if (!request.headers.get('Authorization')) return apiError(401, 'Unauthorized', 'Se requiere una credencial.', path);
    const leagues = leaguesOf(request);
    if (!leagues) return apiError(400, 'Bad Request', 'La liga pedida no es una de las cinco.', path);
    const all = leagues.length === LEAGUES.length;
    const report: SyncReport = {
      type: all ? 'FULL' : 'SINGLE_LEAGUE',
      origin: 'MANUAL',
      startedAt: SYNC_FINISHED_AT,
      finishedAt: SYNC_FINISHED_AT,
      inactivationApplied: false,
      leagues: leagues.map((league) => catalogLeague(league, all)),
      inactivatedPlayers: [],
    };
    return HttpResponse.json(report);
  }),

  http.post('*/api/matches/stats/sync', ({ request }) => {
    const path = '/matches/stats/sync';
    if (!request.headers.get('Authorization')) return apiError(401, 'Unauthorized', 'Se requiere una credencial.', path);
    const leagues = leaguesOf(request);
    if (!leagues) return apiError(400, 'Bad Request', 'La liga pedida no es una de las cinco.', path);
    const report: StatsSyncReport = {
      type: leagues.length === LEAGUES.length ? 'FULL' : 'SINGLE_LEAGUE',
      origin: 'MANUAL',
      startedAt: SYNC_FINISHED_AT,
      finishedAt: SYNC_FINISHED_AT,
      snapshot: 'LOADED',
      cut: null,
      leagues: leagues.map(statsLeague),
    };
    return HttpResponse.json(report);
  }),
];
