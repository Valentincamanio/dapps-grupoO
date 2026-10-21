// Cotización actual de los jugadores de la hoja visible, con una sola consulta por lote (FR-062)
import { useCallback } from 'react';
import { getCurrentQuotes } from '../services/quoteService';
import type { ApiError } from '../types/api';
import type { AsyncState } from '../types/board';
import type { Quote, QuoteBadge } from '../types/quotes';
import { useAsync } from './useAsync';

interface UseCurrentQuotesResult {
  status: AsyncState<Map<number, Quote>>['status'];
  // Estado de la cotización de un jugador de la hoja; 'none' si el lote no la trajo
  badgeFor: (playerId: number) => QuoteBadge;
  error: ApiError | undefined;
  retry: () => void;
}

export function useCurrentQuotes(ids: number[]): UseCurrentQuotesResult {
  // El arreglo cambia en cada render; la consulta depende solo de los ids
  const key = ids.join(',');

  const { status, data, error, retry } = useAsync(async (signal): Promise<Map<number, Quote>> => {
    // Sin jugadores no se consulta
    if (key === '') return new Map();
    const response = await getCurrentQuotes(key.split(',').map(Number), signal);
    return new Map(response.quotes.map(({ playerId, ...quote }) => [playerId, quote]));
  }, [key]);

  const badgeFor = useCallback(
    (playerId: number): QuoteBadge => {
      if (status === 'error') return { status: 'error' };
      if (status !== 'success' || !data) return { status: 'loading' };
      const quote = data.get(playerId);
      return quote ? { status: 'ready', quote } : { status: 'none' };
    },
    [status, data],
  );

  return { status, badgeFor, error, retry };
}
