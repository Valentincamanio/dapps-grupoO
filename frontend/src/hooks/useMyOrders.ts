// Órdenes de la cuenta de la sesión pedidas a GET /users/{id}/orders, de la más nueva a la más
// vieja, con hoja, recarga y, opcionalmente, los estados a mostrar (sin estados, en cualquiera). Sin
// sesión no hay cuenta: no pide nada y queda en 'idle'.
import { getUserOrders } from '../services/marketService';
import type { ApiError } from '../types/api';
import type { AsyncState } from '../types/board';
import type { OrderStatus, UserOrder, UserOrderPage, UserOrderQuery } from '../types/market';
import { useAsync } from './useAsync';
import { useSession } from './useSession';

interface UseMyOrdersResult {
  status: AsyncState<UserOrderPage>['status'];
  // La hoja entera, con sus datos de paginación
  page: UserOrderPage | undefined;
  orders: UserOrder[];
  error: ApiError | undefined;
  // Reintenta después de un error, o vuelve a pedir después de cancelar
  reload: () => void;
}

// Una tarea que no termina nunca: sin cuenta no hay pedido, y useAsync la cancela al desmontar
const WITHOUT_ACCOUNT = () => new Promise<UserOrderPage>(() => {});

export function useMyOrders(query: UserOrderQuery = {}): UseMyOrdersResult {
  const { profile } = useSession();
  const userId = profile?.id;
  const { page, size } = query;
  // El arreglo cambia en cada render; el pedido depende solo de qué estados son
  const statusKey = (query.status ?? []).join(',');
  const { status, data, error, retry } = useAsync(
    (signal) =>
      userId === undefined
        ? WITHOUT_ACCOUNT()
        : getUserOrders(
            userId,
            { page, size, status: statusKey === '' ? undefined : (statusKey.split(',') as OrderStatus[]) },
            signal,
          ),
    [userId, page, size, statusKey],
  );

  if (userId === undefined) {
    return { status: 'idle', page: undefined, orders: [], error: undefined, reload: retry };
  }
  return { status, page: data, orders: data?.content ?? [], error, reload: retry };
}
