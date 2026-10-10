// Tipos propios de la interfaz (data-model §2)
import type { ApiError, League, Player, Position } from './api';

// Sesión guardada en sessionStorage; el token nunca se loguea ni va en la URL
export interface Session {
  token: string;
  // ISO 8601
  expiresAt: string;
}

// Motivo por el que terminó la sesión; elige el aviso del login
export type SessionEndReason = 'logout' | 'expired' | null;

// Filtros de la pizarra; null = sin filtro
export interface BoardFilters {
  position: Position | null;
  league: League | null;
  team: string | null;
  // Hoja visible, desde 1
  page: number;
}

// Zonas de la cancha; "todos" no es una zona, es null
export type PitchZone = 'GOAL' | 'DEFENSE' | 'MIDFIELD' | 'ATTACK';

// Hoja de la pizarra que expone usePlayers
export interface BoardSheet {
  players: Player[];
  // Hoja actual, desde 1
  sheet: number;
  totalSheets: number;
  hasPrevious: boolean;
  hasNext: boolean;
  // La combinación de filtros no tiene jugadores
  isEmptyCombination: boolean;
  // Hay jugadores, pero la hoja pedida está fuera de rango
  isOutOfRange: boolean;
}

export interface AsyncState<T> {
  status: 'idle' | 'loading' | 'success' | 'error';
  data?: T;
  error?: ApiError;
}
