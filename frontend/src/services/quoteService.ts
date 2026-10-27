// Consultas públicas de cotizaciones (specs/007-cotizacion-estrategias/contracts/quotes-api.yaml)
import type {
  CurrentQuotesResponse,
  PlayerQuote,
  QuoteHistoryPage,
  QuoteHistoryQuery,
  RankingPage,
  RankingQuery,
} from '../types/quotes';
import { request } from './httpClient';

export function getPlayerQuotes(
  id: number,
  query: QuoteHistoryQuery = {},
  signal?: AbortSignal,
): Promise<QuoteHistoryPage> {
  return request<QuoteHistoryPage>('GET', `/players/${id}/quotes`, { query, signal });
}

export function getCurrentQuote(id: number, signal?: AbortSignal): Promise<PlayerQuote> {
  return request<PlayerQuote>('GET', `/players/${id}/quotes/current`, { signal });
}

// date en AAAA-MM-DD, hora de Argentina
export function getQuoteAt(id: number, date: string, signal?: AbortSignal): Promise<PlayerQuote> {
  return request<PlayerQuote>('GET', `/players/${id}/quotes/at`, { query: { date }, signal });
}

// Una sola consulta para varios jugadores (hasta 50); los que no tienen cotización no vienen
export function getCurrentQuotes(ids: number[], signal?: AbortSignal): Promise<CurrentQuotesResponse> {
  return request<CurrentQuotesResponse>('GET', '/players/quotes/current', {
    query: { ids: ids.join(',') },
    signal,
  });
}

// Los filtros vacíos no viajan (un equipo en blanco tampoco)
export function getRanking(query: RankingQuery, signal?: AbortSignal): Promise<RankingPage> {
  const team = query.team?.trim();
  return request<RankingPage>('GET', '/players/ranking', {
    query: { ...query, team: team ? team : undefined },
    signal,
  });
}
