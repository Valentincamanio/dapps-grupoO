// Textos de los informes de sincronización.
import type { EntityCounts, LeagueStatsStatus, LeagueSyncStatus, SnapshotStatus } from '../types/sync';

const CATALOG_STATUS: Record<LeagueSyncStatus, string> = {
  SUCCEEDED: 'sincronizada',
  FAILED: 'falló',
};

const STATS_STATUS: Record<LeagueStatsStatus, string> = {
  PROCESSED: 'procesada',
  CUT: 'cortada por WhoScored',
  NOT_REACHED: 'no se llegó',
};

const SNAPSHOT: Record<SnapshotStatus, string> = {
  LOADED: 'snapshot cargado',
  MISSING: 'sin snapshot',
  UNREADABLE: 'snapshot ilegible',
};

export function catalogStatusLabel(status: LeagueSyncStatus): string {
  return CATALOG_STATUS[status];
}

export function statsStatusLabel(status: LeagueStatsStatus): string {
  return STATS_STATUS[status];
}

export function snapshotLabel(status: SnapshotStatus): string {
  return SNAPSHOT[status];
}

// "3 nuevos · 12 actualizados", sin las partes en cero; "sin cambios" si no hubo nada
export function countsText({ created, updated }: EntityCounts): string {
  const parts = [
    created > 0 ? `${created} ${created === 1 ? 'nuevo' : 'nuevos'}` : null,
    updated > 0 ? `${updated} ${updated === 1 ? 'actualizado' : 'actualizados'}` : null,
  ].filter(Boolean);
  return parts.length > 0 ? parts.join(' · ') : 'sin cambios';
}
