// Informes de las sincronizaciones manuales, copiados de los contratos de la 005 (Football-Data,
// SyncReportResponse) y la 006 (WhoScored, StatsSyncReportResponse). Solo los campos que muestra la
// pantalla; el backend manda además el detalle de lo salteado.
import type { League } from './api';

export type SyncType = 'FULL' | 'SINGLE_LEAGUE';
export type SyncOrigin = 'WEEKLY' | 'CATCH_UP' | 'MANUAL';

export interface EntityCounts {
  created: number;
  updated: number;
  skipped: number;
}

export type LeagueSyncStatus = 'SUCCEEDED' | 'FAILED';

export interface LeagueSyncReport {
  league: League;
  status: LeagueSyncStatus;
  // Solo en una liga fallida
  failureReason: string | null;
  teams: EntityCounts;
  players: EntityCounts;
  matches: EntityCounts;
}

export interface PlayerSummary {
  id: number;
  externalId: string;
  name: string;
  team: string;
}

export interface SyncReport {
  type: SyncType;
  origin: SyncOrigin;
  startedAt: string;
  finishedAt: string;
  // Solo una sincronización completa con las cinco ligas en éxito inactiva jugadores
  inactivationApplied: boolean;
  leagues: LeagueSyncReport[];
  inactivatedPlayers: PlayerSummary[];
}

export type SnapshotStatus = 'LOADED' | 'MISSING' | 'UNREADABLE';
export type LeagueStatsStatus = 'PROCESSED' | 'CUT' | 'NOT_REACHED';

export interface LeagueStatsReport {
  league: League;
  status: LeagueStatsStatus;
  matchesFromSnapshot: number;
  matchesScraped: number;
  skippedMatches: { externalId: string }[];
}

export interface StatsSyncReport {
  type: SyncType;
  origin: SyncOrigin;
  startedAt: string;
  finishedAt: string;
  snapshot: SnapshotStatus;
  // null si la corrida no se cortó por un bloqueo de WhoScored
  cut: { league: League | null } | null;
  leagues: LeagueStatsReport[];
}
