// Cotizaciones de prueba para los jugadores de players.ts, con las formas de
// specs/007-cotizacion-estrategias/contracts/quotes-api.yaml.
import type { Profile } from '../../src/types/api';
import type {
  PendingWeek,
  Quote,
  RankingRow,
  RecalculationReport,
  SimulationRow,
  StrategyDto,
  StrategyRef,
  WeightMap,
} from '../../src/types/quotes';
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

// Con el factor de escala de la estrategia A, un score de 0,30 lleva 100,00 a 103,00 (ejemplo de la
// historia 8 de la 008): el score es la variación relativa dividida por ese factor
const SCORE_SCALE_FACTOR = 0.1;

function scoreOf(base: number, value: number): number {
  return round(Math.max(-1, Math.min(1, (value - base) / base / SCORE_SCALE_FACTOR)), 6);
}

function initialQuote(): Quote {
  return {
    value: INITIAL_VALUE,
    tokenPrice: 1,
    weekStart: FIRST_WEEK,
    validFrom: mondayStart(FIRST_WEEK),
    type: 'INITIAL',
    strategy: STRATEGY_A,
    score: 0,
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
    score: scoreOf(base, value),
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

// Administración (contracts/strategies-api.yaml y contracts/quotes-admin-api.yaml)

// El usuario sembrado de handlers.ts con rol ADMIN. Los tests de administración lo obtienen
// sobreescribiendo GET */api/auth/me con server.use(adminProfileHandler) (quoteHandlers.ts).
export const ADMIN_PROFILE: Profile = {
  id: 1,
  username: 'lionel10',
  email: 'lionel@mail.com',
  role: 'ADMIN',
  balance: 1000,
  availableBalance: 1000,
  reservedBalance: 0,
};

export const RECALCULATION_IN_PROGRESS = 'Ya hay un recálculo en curso.';
export const SYNC_IN_PROGRESS = 'No se puede recalcular: hay una sincronización en curso.';

const SEED_CREATED_AT = '2026-08-10T12:00:00Z';

// Los pesos semilla de A (y de B como juego por defecto, US4.1)
const MATCH_PERFORMANCE_WEIGHTS: WeightMap = {
  GOALS: 0.25,
  ASSISTS: 0.15,
  SHOTS: 0.1,
  KEY_PASSES: 0.1,
  SUCCESSFUL_DRIBBLES: 0.1,
  TACKLES: 0.1,
  RATING: 0.2,
};

const FORWARD_WEIGHTS: WeightMap = {
  GOALS: 0.28,
  ASSISTS: 0.14,
  SHOTS_ON_TARGET: 0.12,
  KEY_PASSES: 0.06,
  SUCCESSFUL_DRIBBLES: 0.08,
  RATING: 0.2,
  TEAM_RESULT: 0.05,
  MINUTES_PLAYED: 0.03,
  FOULS_COMMITTED: 0.01,
  YELLOW_CARDS: 0.02,
  RED_CARDS: 0.01,
};

// A activa en su versión 1 y B inactiva con un juego propio para delanteros. Devuelve copias
// nuevas en cada llamada, así un test puede modificarlas sin afectar a los demás.
export function seedStrategies(): StrategyDto[] {
  return [
    {
      id: STRATEGY_A.id,
      name: STRATEGY_A.name,
      active: true,
      versions: [
        {
          version: 1,
          active: true,
          scaleFactor: 10,
          minimumValue: 10,
          defaultWeights: { ...MATCH_PERFORMANCE_WEIGHTS },
          positionWeights: {},
          createdAt: SEED_CREATED_AT,
        },
      ],
    },
    {
      id: STRATEGY_B.id,
      name: STRATEGY_B.name,
      active: false,
      versions: [
        {
          version: 1,
          active: false,
          scaleFactor: 10,
          minimumValue: 10,
          defaultWeights: { ...MATCH_PERFORMANCE_WEIGHTS },
          positionWeights: { FORWARD: { ...FORWARD_WEIGHTS } },
          createdAt: SEED_CREATED_AT,
        },
      ],
    },
  ];
}

// Jugadores con alguna cotización: los que cuenta cada semana de un informe
export const QUOTED_PLAYERS = PLAYERS.length - PLAYERS_WITHOUT_QUOTE.length;

// Semanas cerradas que hoy quedarían pendientes, con los dos motivos
export const PENDING_WEEKS: PendingWeek[] = [
  { weekStart: '2026-10-12', reason: 'MISSING_STATS' },
  { weekStart: '2026-10-19', reason: 'PREVIOUS_WEEK_PENDING' },
];

// Informe del recálculo manual: dos semanas cotizadas y dos pendientes
export const RECALCULATION_REPORT: RecalculationReport = {
  origin: 'MANUAL',
  startedAt: '2026-10-26T09:00:00Z',
  finishedAt: '2026-10-26T09:01:20Z',
  strategy: ACTIVE_STRATEGY,
  quotedWeeks: [
    { weekStart: WEEKS[6], initial: 0, calculated: QUOTED_PLAYERS, requoted: 0 },
    { weekStart: WEEKS[7], initial: 0, calculated: QUOTED_PLAYERS, requoted: 0 },
  ],
  pendingWeeks: PENDING_WEEKS,
  nothingToQuote: false,
  interruptedAt: null,
};

// Todas las semanas cerradas ya estaban cotizadas
export const EMPTY_RECALCULATION_REPORT: RecalculationReport = {
  ...RECALCULATION_REPORT,
  finishedAt: RECALCULATION_REPORT.startedAt,
  quotedWeeks: [],
  pendingWeeks: [],
  nothingToQuote: true,
};

// Informe de un cambio de estrategia con AHORA: la última semana cerrada recotizada
export function requotationReport(strategy: StrategyRef): RecalculationReport {
  return {
    ...RECALCULATION_REPORT,
    origin: 'STRATEGY_CHANGE',
    finishedAt: '2026-10-26T09:00:04Z',
    strategy,
    quotedWeeks: [{ weekStart: WEEKS[7], initial: 0, calculated: 0, requoted: QUOTED_PLAYERS }],
    pendingWeeks: [],
  };
}

// Simulación determinística: la misma semilla da siempre las mismas filas, ordenadas por
// cotización simulada descendente, luego nombre e id (el criterio del ranking)
export function simulationRows(seed: number): SimulationRow[] {
  return PLAYERS.map((player) => {
    const currentValue = currentQuoteOf(player.id)?.value ?? null;
    const step = ((player.id * 17 + seed * 7) % 31) - 15;
    const simulatedValue = Math.max(round(INITIAL_VALUE + step * 0.85, 2), MINIMUM_VALUE);
    return {
      playerId: player.id,
      name: player.name,
      team: player.team,
      league: player.league,
      position: player.position,
      currentValue,
      simulatedValue,
      difference: round(simulatedValue - (currentValue ?? INITIAL_VALUE), 2),
    };
  }).sort(
    (a, b) =>
      b.simulatedValue - a.simulatedValue || a.name.localeCompare(b.name) || a.playerId - b.playerId,
  );
}
