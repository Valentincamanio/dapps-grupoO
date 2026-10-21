// Tipos de la API de cotizaciones copiados del contrato
// specs/007-cotizacion-estrategias/contracts/quotes-api.yaml (parte de lectura).
// No se agregan campos que el backend no devuelve: ninguna respuesta trae score ni estadísticas.
import type { League, PlayerPage, Position } from './api';

export const QUOTE_TYPES = ['INITIAL', 'CALCULATED', 'REQUOTED'] as const;
export type QuoteType = (typeof QUOTE_TYPES)[number];

// Datos de paginación del catálogo (la misma forma que PlayerPage, sin el contenido)
export type PageInfo = Omit<PlayerPage, 'content'>;

// La estrategia y la versión con que se calculó una cotización
export interface StrategyRef {
  id: number;
  name: string;
  version: number;
}

// Diferencia contra el valor de partida, en créditos y en porcentaje, con signo
export interface Variation {
  amount: number;
  percent: number;
}

export interface Quote {
  // Valor del 100 % del jugador, en créditos, con dos decimales
  value: number;
  // value / 100, con cuatro decimales
  tokenPrice: number;
  // No aparece en la cotización INITIAL
  variation?: Variation;
  // Lunes de la semana, AAAA-MM-DD en hora de Argentina
  weekStart: string;
  // Instante ISO 8601 en UTC
  validFrom: string;
  type: QuoteType;
  strategy: StrategyRef;
}

export interface PlayerQuote extends Quote {
  playerId: number;
}

export interface QuoteHistoryPage extends PageInfo {
  playerId: number;
  // Ordenado por validFrom ascendente
  content: Quote[];
}

export interface CurrentQuotesResponse {
  // Solo los jugadores que tienen cotización
  quotes: PlayerQuote[];
}

export interface RankingRow {
  playerId: number;
  name: string;
  position: Position;
  team: string;
  league: League;
  teamCrest?: string | null;
  quote: Quote;
}

export interface RankingPage extends PageInfo {
  activeStrategy: StrategyRef;
  content: RankingRow[];
}

// Parámetros de GET /players/ranking: page >= 0 y 1 <= size <= 50
export interface RankingQuery {
  league?: League;
  team?: string;
  position?: Position;
  page?: number;
  size?: number;
}

// Parámetros de GET /players/{id}/quotes: page >= 0 y 1 <= size <= 100
export interface QuoteHistoryQuery {
  from?: string;
  to?: string;
  page?: number;
  size?: number;
}

// Tipos propios de la interfaz (data-model §10)

// Rango de fechas AAAA-MM-DD, ambos extremos incluidos; sin extremos es todo el historial
export interface QuoteRange {
  from?: string;
  to?: string;
}

// Un punto del gráfico: la última cotización de una semana
export interface ChartPoint {
  // Lunes de la semana, AAAA-MM-DD
  week: string;
  value: number;
  quote: Quote;
}

// Cotización compacta de un post-it
export type QuoteBadge =
  | { status: 'loading' }
  | { status: 'none' }
  | { status: 'ready'; quote: Quote }
  | { status: 'error' };

// Administración (contracts/strategies-api.yaml y contracts/quotes-admin-api.yaml)

// Las 19 métricas semanales; las tres últimas son de "menos es mejor"
export const METRICS = [
  'GOALS',
  'ASSISTS',
  'SHOTS',
  'SHOTS_ON_TARGET',
  'KEY_PASSES',
  'SUCCESSFUL_DRIBBLES',
  'TACKLES',
  'INTERCEPTIONS',
  'CLEARANCES',
  'AERIALS_WON',
  'PASSES',
  'MINUTES_PLAYED',
  'RATING',
  'PASS_ACCURACY',
  'TEAM_RESULT',
  'CLEAN_SHEETS',
  'FOULS_COMMITTED',
  'YELLOW_CARDS',
  'RED_CARDS',
] as const;
export type Metric = (typeof METRICS)[number];

// AHORA recotiza en el acto; PROXIMA_SEMANA (el valor por defecto) solo cambia la activa
export const REQUOTE_TIMINGS = ['AHORA', 'PROXIMA_SEMANA'] as const;
export type RequoteTiming = (typeof REQUOTE_TIMINGS)[number];

export const PENDING_REASONS = ['MISSING_STATS', 'PREVIOUS_WEEK_PENDING'] as const;
export type PendingReason = (typeof PENDING_REASONS)[number];

export const RECALCULATION_ORIGINS = ['WEEKLY', 'STARTUP', 'MANUAL', 'STRATEGY_CHANGE'] as const;
export type RecalculationOrigin = (typeof RECALCULATION_ORIGINS)[number];

// Pesos por métrica: cada uno >= 0 con a lo sumo dos decimales; el juego suma 1,00.
// Una métrica que no figura pesa 0.
export type WeightMap = Partial<Record<Metric, number>>;

// Juegos propios por posición; una posición sin juego usa el por defecto
export type PositionWeights = Partial<Record<Position, WeightMap>>;

export interface StrategyVersionDto {
  version: number;
  // Exactamente una versión de todo el sistema la tiene en true
  active: boolean;
  scaleFactor: number;
  minimumValue: number;
  defaultWeights: WeightMap;
  positionWeights: PositionWeights;
  // Instante ISO 8601 en UTC
  createdAt: string;
}

export interface StrategyDto {
  // El de su versión 1
  id: number;
  name: string;
  // Si su versión más reciente es la activa
  active: boolean;
  // De la 1 en adelante
  versions: StrategyVersionDto[];
}

// Parámetros de valuación (StrategyParameters). Al crear, factor y mínimo omitidos valen 10 y
// 10,00; en una versión nueva conservan los de la anterior.
export interface StrategyInput {
  scaleFactor?: number;
  minimumValue?: number;
  defaultWeights: WeightMap;
  positionWeights?: PositionWeights;
}

export interface WeekResult {
  // Lunes de la semana, AAAA-MM-DD en hora de Argentina
  weekStart: string;
  initial: number;
  calculated: number;
  requoted: number;
}

export interface PendingWeek {
  weekStart: string;
  reason: PendingReason;
}

// Resumen de una corrida del recálculo; no se guarda
export interface RecalculationReport {
  origin: RecalculationOrigin;
  startedAt: string;
  finishedAt: string;
  strategy: StrategyRef;
  quotedWeeks: WeekResult[];
  pendingWeeks: PendingWeek[];
  // No había semanas para cotizar; no es un error
  nothingToQuote: boolean;
  // La semana que falló al guardarse, si la corrida se cortó
  interruptedAt?: string | null;
}

export interface StrategyChange {
  strategy: StrategyDto;
  // Solo con AHORA cuando el cambio lo admite
  requotation: RecalculationReport | null;
}

// Una estrategia guardada o parámetros: uno de los dos, no ambos ni ninguno
export interface SimulationRequest {
  strategyId?: number;
  parameters?: StrategyInput;
}

// Filtros y paginación de POST /quotes/simulate, los del catálogo: page >= 0 y 1 <= size <= 50
export interface SimulationQuery {
  league?: League;
  team?: string;
  position?: Position;
  page?: number;
  size?: number;
}

export interface SimulationRow {
  playerId: number;
  name: string;
  team: string;
  league: League;
  position: Position;
  // null si el jugador no tiene cotización actual
  currentValue: number | null;
  simulatedValue: number;
  // simulatedValue − currentValue (o − 100,00 sin cotización actual), con signo
  difference: number;
}

export interface SimulationPage extends PageInfo {
  // null si se simularon parámetros enviados
  strategy: StrategyRef | null;
  weeksSimulated: number;
  pendingWeeks: PendingWeek[];
  content: SimulationRow[];
}

// Estado del formulario de pesos: el texto tal cual se escribe ('' pesa 0), así un rechazo
// del servidor no pierde lo escrito
export type WeightsText = Record<Metric, string>;

export interface WeightsFormState {
  defaultWeights: WeightsText;
  // Solo las posiciones con juego propio
  positionWeights: Partial<Record<Position, WeightsText>>;
}
