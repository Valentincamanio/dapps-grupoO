// Los potenciados de una semana cualquiera, para la administración (GET /quotes/boosts?week=).
import { getBoostWeek } from '../services/boostService';
import type { ApiError } from '../types/api';
import type { AsyncState } from '../types/board';
import type { BoostedWeek } from '../types/quotes';
import { useAsync } from './useAsync';

interface UseBoostWeekResult {
  status: AsyncState<BoostedWeek>['status'];
  week: BoostedWeek | undefined;
  error: ApiError | undefined;
  // Vuelve a pedirla, también después de guardar
  retry: () => void;
}

// Cualquier día (AAAA-MM-DD) indica la semana que lo contiene
export function useBoostWeek(day: string): UseBoostWeekResult {
  const { status, data, error, retry } = useAsync((signal) => getBoostWeek(day, signal), [day]);
  return { status, week: data, error, retry };
}
