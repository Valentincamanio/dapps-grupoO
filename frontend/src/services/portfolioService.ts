// Portfolio e historial de operaciones de un usuario
// (specs/008-mercado-tokens-portfolio/contracts/market-api.yaml). Solo los ve su dueño o el
// administrador.
import type { Portfolio, TransactionPage, TransactionQuery } from '../types/market';
import { request } from './httpClient';

// Sin fecha, el portfolio actual; con fecha (AAAA-MM-DD en hora de Argentina), el de ese día
export function getPortfolio(userId: number, date?: string, signal?: AbortSignal): Promise<Portfolio> {
  return request<Portfolio>('GET', `/users/${userId}/portfolio`, { query: { date }, signal });
}

// De la más nueva a la más vieja; los filtros vacíos no viajan
export function getTransactions(
  userId: number,
  query: TransactionQuery = {},
  signal?: AbortSignal,
): Promise<TransactionPage> {
  return request<TransactionPage>('GET', `/users/${userId}/transactions`, { query, signal });
}
