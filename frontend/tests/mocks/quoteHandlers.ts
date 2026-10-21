// Handlers de MSW de las consultas de cotizaciones (contracts/quotes-api.yaml) y de la
// administración. Van antes que los de players.ts en handlers.ts, porque */api/players/:id
// también captura "ranking".
import { http, HttpResponse } from 'msw';
import type { ApiError, League, Position } from '../../src/types/api';
import { LEAGUES, POSITIONS } from '../../src/types/api';
import type {
  Metric,
  PlayerQuote,
  PositionWeights,
  QuoteHistoryPage,
  RankingPage,
  RecalculationReport,
  RequoteTiming,
  SimulationPage,
  StrategyChange,
  StrategyDto,
  StrategyRef,
  StrategyVersionDto,
  WeightMap,
} from '../../src/types/quotes';
import { METRICS, REQUOTE_TIMINGS } from '../../src/types/quotes';
import { PLAYERS } from './players';
import {
  ACTIVE_STRATEGY,
  ADMIN_PROFILE,
  currentQuoteOf,
  PENDING_WEEKS,
  quoteHistoryOf,
  RANKING_ROWS,
  RECALCULATION_REPORT,
  requotationReport,
  seedStrategies,
  simulationRows,
  WEEKS,
} from './quotes';

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

// Administración: estrategias, recálculo y simulación (contracts/strategies-api.yaml y
// contracts/quotes-admin-api.yaml). El rol no se controla acá: los tests piden como ADMIN
// sobreescribiendo GET */api/auth/me con server.use(adminProfileHandler). Sin credencial, 401.

const INVALID_BODY = 'El request tiene datos inválidos.';
const BOTH_OR_NEITHER = 'Indique una estrategia guardada o parámetros, pero no las dos cosas ni ninguna.';
const DEFAULT_SCALE_FACTOR = 10;
const DEFAULT_MINIMUM_VALUE = 10;
const MAX_DECIMALS = 2;
const FULL_SET_HUNDREDTHS = 100;
const MAX_SIMULATION_SIZE = 50;
const SEED_VERSION_ROWS = 2;

// Cómo nombra el backend cada juego de pesos en sus mensajes (WeightSet)
const DEFAULT_SET_LABEL = 'por defecto';
const POSITION_SET_LABELS: Record<Position, string> = {
  GOALKEEPER: 'de arqueros',
  DEFENDER: 'de defensores',
  MIDFIELDER: 'de mediocampistas',
  FORWARD: 'de delanteros',
};

type Body = Record<string, unknown>;

let strategies: StrategyDto[] = seedStrategies();
// Cada versión es una fila: el id de una estrategia es el de su versión 1
let nextVersionRow = SEED_VERSION_ROWS + 1;
// Con un motivo, el recálculo y los cambios con AHORA responden 409 sin cambiar nada
let conflictMessage: string | null = null;

// Vuelve las estrategias a la semilla y apaga el modo de conflicto. Los tests de administración
// lo llaman en su beforeEach (resetMockDb de handlers.ts no lo toca).
export function resetAdminMocks(): void {
  strategies = seedStrategies();
  nextVersionRow = SEED_VERSION_ROWS + 1;
  conflictMessage = null;
}

// Activa el modo de conflicto con un motivo (RECALCULATION_IN_PROGRESS o SYNC_IN_PROGRESS de
// quotes.ts), o lo apaga con null
export function setAdminConflict(message: string | null): void {
  conflictMessage = message;
}

// Las estrategias guardadas en el backend simulado, para verificar qué cambió
export function mockStrategies(): StrategyDto[] {
  return structuredClone(strategies);
}

// El usuario sembrado, con rol ADMIN
export const adminProfileHandler = http.get('*/api/auth/me', ({ request }) => {
  if (!request.headers.get('Authorization')) {
    return apiError(401, 'Unauthorized', 'Se requiere una credencial.', '/auth/me');
  }
  return HttpResponse.json(ADMIN_PROFILE);
});

function conflict(path: string, message: string): Response {
  return apiError(409, 'Conflict', message, path);
}

function requireCredential(request: Request, path: string): Response | null {
  if (request.headers.get('Authorization')) return null;
  return apiError(
    401,
    'Unauthorized',
    'Se requiere una credencial: un token de sesión (Authorization: Bearer) o una clave de API (X-API-Key).',
    path,
  );
}

// El cuerpo como objeto; sin cuerpo, un objeto vacío; ilegible, null
async function readBody(request: Request): Promise<Body | null> {
  const text = await request.text();
  if (text.trim() === '') return {};
  try {
    const parsed: unknown = JSON.parse(text);
    return typeof parsed === 'object' && parsed !== null && !Array.isArray(parsed) ? (parsed as Body) : null;
  } catch {
    return null;
  }
}

function isPresent(value: unknown): boolean {
  return value !== undefined && value !== null;
}

function isObject(value: unknown): value is Body {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

// Decimales de un número JSON (0.1 tiene uno; los ceros del final no aparecen)
function decimals(value: number): number {
  const text = String(value);
  if (text.includes('e')) return Number.POSITIVE_INFINITY;
  return text.split('.')[1]?.length ?? 0;
}

function formatSum(hundredths: number): string {
  return (hundredths / FULL_SET_HUNDREDTHS).toFixed(MAX_DECIMALS).replace('.', ',');
}

function validateWeights(label: string, raw: unknown): string | null {
  if (!isPresent(raw)) return `Los pesos ${label} son obligatorios.`;
  if (!isObject(raw)) return INVALID_BODY;
  let sum = 0;
  for (const [name, weight] of Object.entries(raw)) {
    if (!METRICS.includes(name as Metric)) return `La métrica '${name}' no existe.`;
    if (typeof weight !== 'number') return INVALID_BODY;
    if (weight < 0) return `El peso de ${name} no puede ser negativo.`;
    if (decimals(weight) > MAX_DECIMALS) return `El peso de ${name} admite a lo sumo dos decimales.`;
    sum += Math.round(weight * FULL_SET_HUNDREDTHS);
  }
  if (sum !== FULL_SET_HUNDREDTHS) return `Los pesos ${label} deben sumar 1,00 y suman ${formatSum(sum)}.`;
  return null;
}

// Las reglas de StrategyVersion y WeightSet; null si los parámetros son válidos
function validateParameters(body: Body): string | null {
  const { scaleFactor, minimumValue, positionWeights } = body;
  if (isPresent(scaleFactor)) {
    if (typeof scaleFactor !== 'number') return INVALID_BODY;
    if (scaleFactor <= 0) return 'El factor de escala debe ser mayor que 0.';
    if (decimals(scaleFactor) > MAX_DECIMALS) return 'El factor de escala admite a lo sumo dos decimales.';
  }
  if (isPresent(minimumValue)) {
    if (typeof minimumValue !== 'number') return INVALID_BODY;
    if (minimumValue < 0.01) return 'La cotización mínima debe ser al menos 0,01.';
    if (decimals(minimumValue) > MAX_DECIMALS) return 'La cotización mínima admite a lo sumo dos decimales.';
  }
  const defaultError = validateWeights(DEFAULT_SET_LABEL, body.defaultWeights);
  if (defaultError) return defaultError;
  if (!isPresent(positionWeights)) return null;
  if (!isObject(positionWeights)) return INVALID_BODY;
  for (const [position, weights] of Object.entries(positionWeights)) {
    if (!POSITIONS.includes(position as Position)) return INVALID_BODY;
    const error = validateWeights(POSITION_SET_LABELS[position as Position], weights);
    if (error) return error;
  }
  return null;
}

// El valor de requote, o la respuesta 400; sin valor es PROXIMA_SEMANA
function parseRequote(raw: unknown, path: string): RequoteTiming | Response {
  if (!isPresent(raw)) return 'PROXIMA_SEMANA';
  if (REQUOTE_TIMINGS.includes(raw as RequoteTiming)) return raw as RequoteTiming;
  return badRequest(path, `La opción '${String(raw)}' no es válida: use AHORA o PROXIMA_SEMANA.`);
}

function findStrategy(raw: string, path: string): StrategyDto | Response {
  if (!/^\d+$/.test(raw) || Number(raw) < 1) return badRequest(path);
  const strategy = strategies.find((candidate) => candidate.id === Number(raw));
  return strategy ?? notFound(path, `No existe una estrategia con id ${raw}.`);
}

function latestVersion(strategy: StrategyDto): StrategyVersionDto {
  return strategy.versions[strategy.versions.length - 1];
}

function refOf(strategy: StrategyDto): StrategyRef {
  return { id: strategy.id, name: strategy.name, version: latestVersion(strategy).version };
}

function changeResponse(strategy: StrategyDto, requotation: RecalculationReport | null, status: number): Response {
  const body: StrategyChange = { strategy: structuredClone(strategy), requotation };
  return HttpResponse.json(body, { status });
}

// Factor y mínimo omitidos: los de la versión anterior, o los valores por defecto al crear
function newVersion(body: Body, version: number, previous?: StrategyVersionDto): StrategyVersionDto {
  return {
    version,
    active: false,
    scaleFactor: (body.scaleFactor as number | undefined) ?? previous?.scaleFactor ?? DEFAULT_SCALE_FACTOR,
    minimumValue: (body.minimumValue as number | undefined) ?? previous?.minimumValue ?? DEFAULT_MINIMUM_VALUE,
    defaultWeights: body.defaultWeights as WeightMap,
    positionWeights: (body.positionWeights as PositionWeights | undefined) ?? {},
    createdAt: new Date().toISOString(),
  };
}

// Semilla de la simulación: la versión de una guardada o los parámetros enviados
function simulationSeed(strategy: StrategyRef | null, parameters: unknown): number {
  if (strategy) return strategy.id * 10 + strategy.version;
  const body = parameters as Body;
  const scaleFactor = (body.scaleFactor as number | undefined) ?? DEFAULT_SCALE_FACTOR;
  return Math.round(scaleFactor * FULL_SET_HUNDREDTHS) + Object.keys(body.defaultWeights as Body).length;
}

const adminHandlers = [
  http.get('*/api/strategies', ({ request }) => {
    const denied = requireCredential(request, '/strategies');
    if (denied) return denied;
    return HttpResponse.json(structuredClone(strategies));
  }),

  http.post('*/api/strategies', async ({ request }) => {
    const path = '/strategies';
    const denied = requireCredential(request, path);
    if (denied) return denied;
    const body = await readBody(request);
    if (body === null) return badRequest(path, INVALID_BODY);

    const name = typeof body.name === 'string' ? body.name.trim() : '';
    if (name === '') return badRequest(path, 'El nombre de la estrategia es obligatorio.');
    const invalid = validateParameters(body);
    if (invalid) return badRequest(path, invalid);
    if (strategies.some((strategy) => strategy.name.toLowerCase() === name.toLowerCase())) {
      return conflict(path, `Ya existe una estrategia con el nombre '${name}'.`);
    }

    const strategy: StrategyDto = {
      id: nextVersionRow++,
      name,
      active: false,
      versions: [newVersion(body, 1)],
    };
    strategies.push(strategy);
    return HttpResponse.json(structuredClone(strategy), { status: 201 });
  }),

  http.get('*/api/strategies/:id', ({ params, request }) => {
    const raw = String(params.id);
    const path = `/strategies/${raw}`;
    const denied = requireCredential(request, path);
    if (denied) return denied;
    const strategy = findStrategy(raw, path);
    if (strategy instanceof Response) return strategy;
    return HttpResponse.json(structuredClone(strategy));
  }),

  http.post('*/api/strategies/:id/versions', async ({ params, request }) => {
    const raw = String(params.id);
    const path = `/strategies/${raw}/versions`;
    const denied = requireCredential(request, path);
    if (denied) return denied;
    const strategy = findStrategy(raw, path);
    if (strategy instanceof Response) return strategy;
    const body = await readBody(request);
    if (body === null) return badRequest(path, INVALID_BODY);
    const requote = parseRequote(body.requote, path);
    if (requote instanceof Response) return requote;
    const invalid = validateParameters(body);
    if (invalid) return badRequest(path, invalid);

    // La opción solo rige si la estrategia es la activa; si no, se ignora
    const requoteNow = strategy.active && requote === 'AHORA';
    if (requoteNow && conflictMessage) return conflict(path, conflictMessage);

    const previous = latestVersion(strategy);
    const version = newVersion(body, previous.version + 1, previous);
    nextVersionRow++;
    if (strategy.active) {
      previous.active = false;
      version.active = true;
    }
    strategy.versions.push(version);
    return changeResponse(strategy, requoteNow ? requotationReport(refOf(strategy)) : null, 201);
  }),

  http.post('*/api/strategies/:id/activate', async ({ params, request }) => {
    const raw = String(params.id);
    const path = `/strategies/${raw}/activate`;
    const denied = requireCredential(request, path);
    if (denied) return denied;
    const strategy = findStrategy(raw, path);
    if (strategy instanceof Response) return strategy;
    const body = await readBody(request);
    if (body === null) return badRequest(path, INVALID_BODY);
    const requote = parseRequote(body.requote, path);
    if (requote instanceof Response) return requote;

    // Activar la ya activa no cambia nada
    if (strategy.active) return changeResponse(strategy, null, 200);
    if (requote === 'AHORA' && conflictMessage) return conflict(path, conflictMessage);

    for (const candidate of strategies) {
      candidate.active = false;
      candidate.versions.forEach((version) => {
        version.active = false;
      });
    }
    strategy.active = true;
    latestVersion(strategy).active = true;
    return changeResponse(strategy, requote === 'AHORA' ? requotationReport(refOf(strategy)) : null, 200);
  }),

  http.post('*/api/quotes/recalculate', ({ request }) => {
    const path = '/quotes/recalculate';
    const denied = requireCredential(request, path);
    if (denied) return denied;
    if (conflictMessage) return conflict(path, conflictMessage);
    return HttpResponse.json(RECALCULATION_REPORT);
  }),

  http.post('*/api/quotes/simulate', async ({ request }) => {
    const path = '/quotes/simulate';
    const denied = requireCredential(request, path);
    if (denied) return denied;

    const search = new URL(request.url).searchParams;
    const size = parseInteger(search.get('size'), 10);
    const page = parseInteger(search.get('page'), 0);
    const position = search.get('position');
    const league = search.get('league');
    const team = search.get('team');
    const invalidQuery =
      !(size >= 1 && size <= MAX_SIMULATION_SIZE) ||
      !(page >= 0) ||
      (position !== null && !POSITIONS.includes(position as Position)) ||
      (league !== null && !LEAGUES.includes(league as League)) ||
      (team !== null && team.trim() === '');
    if (invalidQuery) return badRequest(path);

    const body = await readBody(request);
    if (body === null) return badRequest(path, INVALID_BODY);
    if (isPresent(body.strategyId) === isPresent(body.parameters)) return badRequest(path, BOTH_OR_NEITHER);

    let strategy: StrategyRef | null = null;
    if (isPresent(body.strategyId)) {
      const found = findStrategy(String(body.strategyId), path);
      if (found instanceof Response) return found;
      strategy = refOf(found);
    } else {
      if (!isObject(body.parameters)) return badRequest(path, INVALID_BODY);
      const invalid = validateParameters(body.parameters);
      if (invalid) return badRequest(path, invalid);
    }

    const rows = simulationRows(simulationSeed(strategy, body.parameters)).filter(
      (row) =>
        (position === null || row.position === position) &&
        (league === null || row.league === league) &&
        (team === null || row.team.toLowerCase() === team.trim().toLowerCase()),
    );
    const result: SimulationPage = {
      strategy,
      weeksSimulated: WEEKS.length,
      pendingWeeks: PENDING_WEEKS,
      ...pageOf(rows, page, size),
    };
    return HttpResponse.json(result);
  }),
];

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
  ...adminHandlers,
];
