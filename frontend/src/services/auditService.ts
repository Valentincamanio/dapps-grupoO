// Auditoría del mercado (contrato de la 008). Solo ADMIN: sin sesión responde 401 y con un usuario
// común, 403. Los filtros vacíos no viajan.
import type { AuditPage, AuditQuery } from '../types/audit';
import { request } from './httpClient';

export function getAudit(query: AuditQuery = {}, signal?: AbortSignal): Promise<AuditPage> {
  return request<AuditPage>('GET', '/audit', { query, signal });
}
