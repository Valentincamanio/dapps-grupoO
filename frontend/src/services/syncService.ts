// Disparo manual de las sincronizaciones (contratos de la 005 y la 006). Solo ADMIN: sin sesión
// responde 401 y con un usuario común, 403. Hay a lo sumo una a la vez, de cualquiera de las dos
// fuentes: otra en curso responde 409. Los dos pedidos esperan a que la sincronización termine.
import type { League } from '../types/api';
import type { StatsSyncReport, SyncReport } from '../types/sync';
import { request } from './httpClient';

// El catálogo con Football-Data.org: equipos, jugadores y partidos. Sin liga, las cinco. Sin el
// token de la fuente responde 503
export function syncCatalog(league?: League, signal?: AbortSignal): Promise<SyncReport> {
  return request<SyncReport>('POST', '/players/sync', { query: { league }, signal });
}

// Las estadísticas de los partidos terminados con WhoScored. Sin liga, las cinco
export function syncStats(league?: League, signal?: AbortSignal): Promise<StatsSyncReport> {
  return request<StatsSyncReport>('POST', '/matches/stats/sync', { query: { league }, signal });
}
