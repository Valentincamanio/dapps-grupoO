// Portfolio de la cuenta de la sesión pedido a GET /users/{id}/portfolio (FR-036 a FR-039). Sin
// fecha, el actual. Sin sesión no hay cuenta: no pide nada y queda en 'idle'.
import { getPortfolio } from '../services/portfolioService';
import type { ApiError } from '../types/api';
import type { AsyncState } from '../types/board';
import type { Portfolio } from '../types/market';
import { useAsync } from './useAsync';
import { useSession } from './useSession';

interface UsePortfolioResult {
  status: AsyncState<Portfolio>['status'];
  portfolio: Portfolio | undefined;
  error: ApiError | undefined;
  // Reintenta después de un error
  retry: () => void;
}

// Una tarea que no termina nunca: sin cuenta no hay pedido, y useAsync la cancela al desmontar
const WITHOUT_ACCOUNT = () => new Promise<Portfolio>(() => {});

export function usePortfolio(date?: string): UsePortfolioResult {
  const { profile } = useSession();
  const userId = profile?.id;
  const { status, data, error, retry } = useAsync(
    (signal) => (userId === undefined ? WITHOUT_ACCOUNT() : getPortfolio(userId, date, signal)),
    [userId, date],
  );

  if (userId === undefined) return { status: 'idle', portfolio: undefined, error: undefined, retry };
  return { status, portfolio: data, error, retry };
}
