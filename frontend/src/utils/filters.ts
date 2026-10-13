// Filtros de la pizarra <-> dirección (data-model §3)
import { LEAGUES, POSITIONS } from '../types/api';
import type { League, PlayerQuery, Position } from '../types/api';
import type { BoardFilters } from '../types/board';
import { leagueLabel, positionPlural } from './positions';

export const SHEET_SIZE = 12;

export function parseBoardFilters(params: URLSearchParams): BoardFilters {
  const position = params.get('position');
  const league = params.get('league');
  const team = params.get('team')?.trim();
  const page = Number(params.get('page'));

  return {
    position: POSITIONS.includes(position as Position) ? (position as Position) : null,
    league: LEAGUES.includes(league as League) ? (league as League) : null,
    team: team ? team : null,
    page: Number.isInteger(page) && page >= 1 ? page : 1,
  };
}

// Los filtros vacíos y la primera hoja no aparecen en la dirección
export function toSearchParams(filters: BoardFilters): URLSearchParams {
  const params = new URLSearchParams();
  if (filters.position) params.set('position', filters.position);
  if (filters.league) params.set('league', filters.league);
  if (filters.team) params.set('team', filters.team);
  if (filters.page > 1) params.set('page', String(filters.page));
  return params;
}

// Hacia el backend la hoja cuenta desde 0
export function toPlayerQuery(filters: BoardFilters): PlayerQuery {
  return {
    position: filters.position ?? undefined,
    league: filters.league ?? undefined,
    team: filters.team ?? undefined,
    page: filters.page - 1,
    size: SHEET_SIZE,
  };
}

// Texto de la nota cuando la combinación de filtros no tiene jugadores
export function describeEmptyCombination(filters: BoardFilters): string {
  const parts = ['no hay', positionPlural(filters.position)];
  if (filters.league) parts.push(`de ${leagueLabel(filters.league)}`);
  if (filters.team) parts.push('de ese equipo');
  parts.push('en la pizarra');
  return parts.join(' ');
}
