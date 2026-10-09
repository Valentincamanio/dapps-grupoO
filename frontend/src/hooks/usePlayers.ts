// Hoja de la pizarra pedida a GET /players (research R-7)
import { getPlayers } from '../services/playerService';
import type { ApiError } from '../types/api';
import type { AsyncState, BoardFilters, BoardSheet } from '../types/board';
import { toPlayerQuery } from '../utils/filters';
import { useAsync } from './useAsync';

interface UsePlayersResult {
  status: AsyncState<BoardSheet>['status'];
  sheet: BoardSheet | undefined;
  error: ApiError | undefined;
  retry: () => void;
}

export function usePlayers(filters: BoardFilters): UsePlayersResult {
  const { position, league, team, page } = filters;

  const { status, data, error, retry } = useAsync(
    async (signal): Promise<BoardSheet> => {
      const result = await getPlayers(toPlayerQuery({ position, league, team, page }), signal);
      return {
        players: result.content,
        sheet: result.page + 1,
        totalSheets: result.totalPages,
        hasPrevious: result.hasPrevious,
        hasNext: result.hasNext,
        isEmptyCombination: result.totalElements === 0,
        isOutOfRange: result.totalElements > 0 && result.content.length === 0,
      };
    },
    [position, league, team, page],
  );

  return { status, sheet: data, error, retry };
}
