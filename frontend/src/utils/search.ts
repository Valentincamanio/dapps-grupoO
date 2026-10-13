// Búsqueda de jugadores por nombre sobre el catálogo completo (research R-1, data-model §5)
import type { Player } from '../types/api';

export const MAX_SUGGESTIONS = 8;
export const MIN_QUERY_LENGTH = 2;

// Recorta, pasa a minúsculas y quita las marcas diacríticas
export function normalize(text: string): string {
  return text
    .trim()
    .toLowerCase()
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '');
}

// Con menos de 2 letras no hay sugerencias; respeta el orden del catálogo
export function searchPlayers(players: Player[], query: string): Player[] {
  const needle = normalize(query);
  if (needle.length < MIN_QUERY_LENGTH) return [];
  const found: Player[] = [];
  for (const player of players) {
    if (normalize(player.name).includes(needle)) {
      found.push(player);
      if (found.length === MAX_SUGGESTIONS) break;
    }
  }
  return found;
}
