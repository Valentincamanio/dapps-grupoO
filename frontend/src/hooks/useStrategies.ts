// Estrategias con todas sus versiones, pedidas a GET /strategies (solo ADMIN)
import { listStrategies } from '../services/strategyService';
import type { ApiError } from '../types/api';
import type { AsyncState } from '../types/board';
import type { StrategyDto } from '../types/quotes';
import { useAsync } from './useAsync';

interface UseStrategiesResult {
  status: AsyncState<StrategyDto[]>['status'];
  // En orden de creación
  strategies: StrategyDto[] | undefined;
  // La estrategia activa (exactamente una cuando hay estrategias)
  active: StrategyDto | undefined;
  error: ApiError | undefined;
  // Vuelve a pedir la lista, también después de crear, editar o activar
  retry: () => void;
}

export function useStrategies(): UseStrategiesResult {
  const { status, data, error, retry } = useAsync((signal) => listStrategies(signal), []);
  return {
    status,
    strategies: data,
    active: data?.find((strategy) => strategy.active),
    error,
    retry,
  };
}
