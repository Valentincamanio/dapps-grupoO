// Jugadores potenciados de cada semana. Los de la semana en curso son públicos; consultar cualquier
// semana y elegirlos es solo de ADMIN (sin sesión responde 401 y con un usuario común, 403).
import type { BoostInput, BoostedWeek } from '../types/quotes';
import { request } from './httpClient';

export function getCurrentBoosts(signal?: AbortSignal): Promise<BoostedWeek> {
  return request<BoostedWeek>('GET', '/players/boosts/current', { signal });
}

// Cualquier día (AAAA-MM-DD) indica la semana que lo contiene
export function getBoostWeek(week: string, signal?: AbortSignal): Promise<BoostedWeek> {
  return request<BoostedWeek>('GET', '/quotes/boosts', { query: { week }, signal });
}

// Reemplaza los potenciados de la semana: cuatro, o ninguno para vaciarla. Una semana cerrada da 409
export function saveBoostWeek(week: string, players: BoostInput[], signal?: AbortSignal): Promise<BoostedWeek> {
  return request<BoostedWeek>('PUT', `/quotes/boosts/${week}`, { body: { players }, signal });
}
