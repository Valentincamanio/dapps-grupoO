// Ficha de un jugador pedida a GET /players/{id}
import { getPlayer } from '../services/playerService';
import type { ApiError, Player } from '../types/api';
import type { AsyncState } from '../types/board';
import { useAsync } from './useAsync';

interface UsePlayerResult {
  status: AsyncState<Player | null>['status'];
  player: Player | undefined;
  // El id no es válido o el backend no tiene ese jugador (404 y 400)
  notFound: boolean;
  error: ApiError | undefined;
  retry: () => void;
}

function parseId(raw: string | undefined): number | null {
  if (raw === undefined || !/^\d+$/.test(raw)) return null;
  const id = Number(raw);
  return Number.isSafeInteger(id) && id > 0 ? id : null;
}

export function usePlayer(rawId: string | undefined): UsePlayerResult {
  const id = parseId(rawId);

  const { status, data, error, retry } = useAsync(
    // Con un id inválido no se pide nada
    async (signal): Promise<Player | null> => (id === null ? null : getPlayer(id, signal)),
    [id],
  );

  const missingOnBackend = error !== undefined && (error.status === 404 || error.status === 400);
  const notFound = id === null || missingOnBackend;

  return {
    status: id === null ? 'success' : status,
    player: data ?? undefined,
    notFound,
    error: missingOnBackend ? undefined : error,
    retry,
  };
}
