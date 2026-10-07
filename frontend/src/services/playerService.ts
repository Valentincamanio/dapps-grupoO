// Catálogo de jugadores (specs/002-catalogo-jugadores)
import type { Player, PlayerPage, PlayerQuery } from '../types/api';
import { request } from './httpClient';

export function getPlayers(query: PlayerQuery, signal?: AbortSignal): Promise<PlayerPage> {
  return request<PlayerPage>('GET', '/players', { query, signal });
}

export function getPlayer(id: number, signal?: AbortSignal): Promise<Player> {
  return request<Player>('GET', `/players/${id}`, { signal });
}
