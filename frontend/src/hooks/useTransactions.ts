// Historial de operaciones de la cuenta de la sesión pedido a GET /users/{id}/transactions
// (FR-043, FR-044), con filtros y hoja. Sin sesión no hay cuenta: no pide nada y queda en 'idle'.
import { getTransactions } from '../services/portfolioService';
import type { ApiError } from '../types/api';
import type { AsyncState } from '../types/board';
import type { Transaction, TransactionPage, TransactionQuery } from '../types/market';
import { useAsync } from './useAsync';
import { useSession } from './useSession';

interface UseTransactionsResult {
  status: AsyncState<TransactionPage>['status'];
  // La hoja entera, con sus datos de paginación
  page: TransactionPage | undefined;
  transactions: Transaction[];
  error: ApiError | undefined;
  // Reintenta después de un error
  retry: () => void;
}

// Una tarea que no termina nunca: sin cuenta no hay pedido, y useAsync la cancela al desmontar
const WITHOUT_ACCOUNT = () => new Promise<TransactionPage>(() => {});

export function useTransactions(query: TransactionQuery = {}): UseTransactionsResult {
  const { profile } = useSession();
  const userId = profile?.id;
  const { playerId, side, from, to, page, size } = query;
  const { status, data, error, retry } = useAsync(
    (signal) =>
      userId === undefined
        ? WITHOUT_ACCOUNT()
        : getTransactions(userId, { playerId, side, from, to, page, size }, signal),
    [userId, playerId, side, from, to, page, size],
  );

  if (userId === undefined) {
    return { status: 'idle', page: undefined, transactions: [], error: undefined, retry };
  }
  return { status, page: data, transactions: data?.content ?? [], error, retry };
}
