// Ranking por cotización pedido a GET /players/ranking. Los filtros y la hoja son los de la
// pizarra (league, team, position y page en la dirección, FR-063).
import { getRanking } from '../services/quoteService';
import type { ApiError } from '../types/api';
import type { AsyncState } from '../types/board';
import type { RankingRow, StrategyRef } from '../types/quotes';
import { useAsync } from './useAsync';
import { useBoardFilters } from './useBoardFilters';

export const RANKING_SIZE = 10;

export interface RankingSheet {
  rows: RankingRow[];
  activeStrategy: StrategyRef;
  // Hoja actual, desde 1
  sheet: number;
  totalSheets: number;
  hasPrevious: boolean;
  hasNext: boolean;
  // Puesto en el ranking de la primera fila de la hoja
  firstRank: number;
  // La combinación de filtros no tiene jugadores cotizados
  isEmpty: boolean;
  // Hay jugadores, pero la hoja pedida está fuera de rango
  isOutOfRange: boolean;
}

// Filtros y setters de useBoardFilters (cambiar un filtro vuelve a la primera hoja) más la hoja
interface UseRankingResult extends ReturnType<typeof useBoardFilters> {
  status: AsyncState<RankingSheet>['status'];
  sheet: RankingSheet | undefined;
  error: ApiError | undefined;
  retry: () => void;
}

export function useRanking(): UseRankingResult {
  const board = useBoardFilters();
  const { position, league, team, page } = board.filters;

  const { status, data, error, retry } = useAsync(
    async (signal): Promise<RankingSheet> => {
      const result = await getRanking(
        {
          league: league ?? undefined,
          team: team ?? undefined,
          position: position ?? undefined,
          page: page - 1,
          size: RANKING_SIZE,
        },
        signal,
      );
      return {
        rows: result.content,
        activeStrategy: result.activeStrategy,
        sheet: result.page + 1,
        totalSheets: result.totalPages,
        hasPrevious: result.hasPrevious,
        hasNext: result.hasNext,
        firstRank: result.page * result.size + 1,
        isEmpty: result.totalElements === 0,
        isOutOfRange: result.totalElements > 0 && result.content.length === 0,
      };
    },
    [position, league, team, page],
  );

  return { ...board, status, sheet: data, error, retry };
}
