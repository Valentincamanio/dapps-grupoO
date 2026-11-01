// Los potenciados de la semana en curso (GET /players/boosts/current), para el inicio. Es público:
// lo ven visitantes, usuarios y el administrador.
import { getCurrentBoosts } from '../services/boostService';
import type { AsyncState } from '../types/board';
import type { BoostedWeek } from '../types/quotes';
import { useAsync } from './useAsync';

interface UseCurrentBoostsResult {
  status: AsyncState<BoostedWeek>['status'];
  week: BoostedWeek | undefined;
  retry: () => void;
}

export function useCurrentBoosts(): UseCurrentBoostsResult {
  const { status, data, retry } = useAsync((signal) => getCurrentBoosts(signal), []);
  return { status, week: data, retry };
}
