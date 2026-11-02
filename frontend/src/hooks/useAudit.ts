// Una hoja de la auditoría del mercado (GET /audit) con sus filtros. Cambiar un filtro o la hoja la
// vuelve a pedir.
import { getAudit } from '../services/auditService';
import type { ApiError } from '../types/api';
import type { AuditPage, AuditQuery } from '../types/audit';
import type { AsyncState } from '../types/board';
import { useAsync } from './useAsync';

interface UseAuditResult {
  status: AsyncState<AuditPage>['status'];
  page: AuditPage | undefined;
  error: ApiError | undefined;
  retry: () => void;
}

export function useAudit(query: AuditQuery): UseAuditResult {
  const { eventType, userId, playerId, from, to, page, size } = query;
  const { status, data, error, retry } = useAsync(
    (signal) => getAudit({ eventType, userId, playerId, from, to, page, size }, signal),
    [eventType, userId, playerId, from, to, page, size],
  );
  return { status, page: data, error, retry };
}
