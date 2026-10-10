// Equipos derivados del catálogo completo (data-model §5)
import type { League, Player } from '../types/api';

// Sin repetidos, acotados por liga si la hay y ordenados alfabéticamente en español
export function deriveTeams(players: Player[], league: League | null): string[] {
  const teams = new Set<string>();
  for (const player of players) {
    if (league === null || player.league === league) teams.add(player.team);
  }
  return [...teams].sort((a, b) => a.localeCompare(b, 'es'));
}

export function teamBelongsToLeague(players: Player[], team: string, league: League): boolean {
  return players.some((player) => player.team === team && player.league === league);
}
