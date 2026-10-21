// Evolución de la cotización de un jugador en un rango de fechas
import { getPlayerQuotes } from '../services/quoteService';
import type { ApiError } from '../types/api';
import type { AsyncState } from '../types/board';
import type { Quote, QuoteRange } from '../types/quotes';
import { useAsync } from './useAsync';

// Alcanza para los puntos semanales de una temporada entera en una sola consulta
export const HISTORY_SIZE = 100;

interface UseQuoteHistoryResult {
  // 'idle' mientras no hay jugador cargado
  status: AsyncState<Quote[]>['status'];
  // Todas las cotizaciones del rango, por validFrom ascendente
  quotes: Quote[] | undefined;
  // El rango no tiene cotizaciones
  isEmpty: boolean;
  error: ApiError | undefined;
  retry: () => void;
}

export function useQuoteHistory(playerId: number | undefined, range: QuoteRange): UseQuoteHistoryResult {
  const { from, to } = range;

  const { status, data, error, retry } = useAsync(
    async (signal): Promise<Quote[]> => {
      if (playerId === undefined) return [];
      const page = await getPlayerQuotes(playerId, { from, to, page: 0, size: HISTORY_SIZE }, signal);
      return page.content;
    },
    // Cambiar el rango vuelve a pedir
    [playerId, from, to],
  );

  if (playerId === undefined) {
    return { status: 'idle', quotes: undefined, isEmpty: false, error: undefined, retry };
  }

  return {
    status,
    quotes: data,
    isEmpty: status === 'success' && data !== undefined && data.length === 0,
    error,
    retry,
  };
}
