// Mapeo entre zonas de la cancha y posiciones, y etiquetas en español (data-model §4)
import type { League, Position, Role } from '../types/api';
import type { PitchZone } from '../types/board';

const ZONE_TO_POSITION: Record<PitchZone, Position> = {
  GOAL: 'GOALKEEPER',
  DEFENSE: 'DEFENDER',
  MIDFIELD: 'MIDFIELDER',
  ATTACK: 'FORWARD',
};

const POSITION_TO_ZONE: Record<Position, PitchZone> = {
  GOALKEEPER: 'GOAL',
  DEFENDER: 'DEFENSE',
  MIDFIELDER: 'MIDFIELD',
  FORWARD: 'ATTACK',
};

const ZONE_LABELS: Record<PitchZone, string> = {
  GOAL: 'arco',
  DEFENSE: 'defensa',
  MIDFIELD: 'mediocampo',
  ATTACK: 'delantera',
};

const POSITION_LABELS: Record<Position, string> = {
  GOALKEEPER: 'Arquero',
  DEFENDER: 'Defensor',
  MIDFIELDER: 'Mediocampista',
  FORWARD: 'Delantero',
};

const POSITION_PLURALS: Record<Position, string> = {
  GOALKEEPER: 'arqueros',
  DEFENDER: 'defensores',
  MIDFIELDER: 'mediocampistas',
  FORWARD: 'delanteros',
};

const LEAGUE_LABELS: Record<League, string> = {
  PREMIER: 'Premier League',
  BUNDESLIGA: 'Bundesliga',
  LA_LIGA: 'La Liga',
  SERIE_A: 'Serie A',
  LIGUE_1: 'Ligue 1',
};

const ROLE_LABELS: Record<Role, string> = {
  USER: 'Usuario',
  ADMIN: 'Administrador',
};

// La zona null ("todos") no filtra por posición
export function zoneToPosition(zone: PitchZone | null): Position | null {
  return zone === null ? null : ZONE_TO_POSITION[zone];
}

export function positionToZone(position: Position | null): PitchZone | null {
  return position === null ? null : POSITION_TO_ZONE[position];
}

export function zoneLabel(zone: PitchZone): string {
  return ZONE_LABELS[zone];
}

export function positionLabel(position: Position): string {
  return POSITION_LABELS[position];
}

// Sin posición se habla de "jugadores"
export function positionPlural(position: Position | null): string {
  return position === null ? 'jugadores' : POSITION_PLURALS[position];
}

export function leagueLabel(league: League): string {
  return LEAGUE_LABELS[league];
}

export function roleLabel(role: Role): string {
  return ROLE_LABELS[role];
}
