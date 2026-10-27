// Cotización actual de la ficha, pedida a GET /players/{id}/quotes/current
import { ApiRequestError } from '../services/httpClient';
import { getCurrentQuote } from '../services/quoteService';
import type { ApiError } from '../types/api';
import type { AsyncState } from '../types/board';
import type { PlayerQuote } from '../types/quotes';
import { useAsync } from './useAsync';

interface UsePlayerQuoteResult {
  // 'idle' mientras no hay jugador cargado
  status: AsyncState<PlayerQuote | null>['status'];
  quote: PlayerQuote | undefined;
  // El jugador existe pero todavía no tiene cotización (no es un error)
  noQuote: boolean;
  error: ApiError | undefined;
  retry: () => void;
}

// Recibe el id del jugador ya cargado: un 404 entonces es "sin cotización"
export function usePlayerQuote(playerId: number | undefined): UsePlayerQuoteResult {
  const { status, data, error, retry } = useAsync(
    async (signal): Promise<PlayerQuote | null> => {
      if (playerId === undefined) return null;
      try {
        return await getCurrentQuote(playerId, signal);
      } catch (failure) {
        if (failure instanceof ApiRequestError && failure.apiError.status === 404) return null;
        throw failure;
      }
    },
    [playerId],
  );

  if (playerId === undefined) {
    return { status: 'idle', quote: undefined, noQuote: false, error: undefined, retry };
  }

  return {
    status,
    quote: data ?? undefined,
    noQuote: status === 'success' && data === null,
    error,
    retry,
  };
}
