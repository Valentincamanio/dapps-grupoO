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
