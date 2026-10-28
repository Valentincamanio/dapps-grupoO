// Órdenes del mercado (specs/008-mercado-tokens-portfolio/contracts/market-api.yaml): el listado
// público, comprar, vender y cancelar, y las órdenes de un usuario. El usuario sale siempre de la
// credencial: ningún pedido lo manda en el cuerpo.
import type {
  MarketOrderPage,
  MarketOrderQuery,
  OrderCancellation,
  OrderExecution,
  OrderRequest,
  PageQuery,
  UserOrderPage,
} from '../types/market';
import { request } from './httpClient';

// Público. Los filtros vacíos no viajan (un equipo en blanco tampoco)
export function getMarketOrders(query: MarketOrderQuery = {}, signal?: AbortSignal): Promise<MarketOrderPage> {
  const team = query.team?.trim();
  return request<MarketOrderPage>('GET', '/orders', {
    query: { ...query, team: team ? team : undefined },
    signal,
  });
}

export function buyTokens(order: OrderRequest): Promise<OrderExecution> {
  return request<OrderExecution>('POST', '/orders/buy', { body: order });
}

export function sellTokens(order: OrderRequest): Promise<OrderExecution> {
  return request<OrderExecution>('POST', '/orders/sell', { body: order });
}

// Cancela el remanente pendiente de una orden propia; lo ejecutado se conserva
export function cancelOrder(orderId: number): Promise<OrderCancellation> {
  return request<OrderCancellation>('POST', `/orders/${orderId}/cancel`);
}

// De la más nueva a la más vieja; solo las ve su dueño o el administrador
export function getUserOrders(userId: number, query: PageQuery = {}, signal?: AbortSignal): Promise<UserOrderPage> {
  return request<UserOrderPage>('GET', `/users/${userId}/orders`, { query, signal });
}
