// Órdenes del mercado pedidas a GET /orders: el listado con filtros y hojas, o, con playerId, las
// pendientes de un jugador para la ficha. Es público: no hace falta sesión.
import { getMarketOrders } from '../services/marketService';
import type { ApiError } from '../types/api';
import type { AsyncState } from '../types/board';
import type { MarketOrder, MarketOrderPage, MarketOrderQuery } from '../types/market';
import { useAsync } from './useAsync';

interface UseMarketOrdersResult {
  status: AsyncState<MarketOrderPage>['status'];
  // La hoja entera, con sus datos de paginación
  page: MarketOrderPage | undefined;
  orders: MarketOrder[];
  error: ApiError | undefined;
  // Reintenta después de un error, o vuelve a pedir después de operar
  reload: () => void;
}

export function useMarketOrders(query: MarketOrderQuery = {}): UseMarketOrdersResult {
  const { side, league, team, ownerType, playerId, page, size } = query;
  const { status, data, error, retry } = useAsync(
    (signal) => getMarketOrders({ side, league, team, ownerType, playerId, page, size }, signal),
    [side, league, team, ownerType, playerId, page, size],
  );

  return { status, page: data, orders: data?.content ?? [], error, reload: retry };
}
