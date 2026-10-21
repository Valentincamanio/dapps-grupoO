// Disparo manual del recálculo (POST /quotes/recalculate). El último informe se conserva aunque
// un disparo posterior falle, por ejemplo con 409 por otro recálculo o una sincronización.
import { recalculate } from '../services/recalculationService';
import type { ApiError } from '../types/api';
import type { RecalculationReport } from '../types/quotes';
import { useMutation, type MutationStatus } from './useMutation';

interface UseRecalculationResult {
  status: MutationStatus;
  report: RecalculationReport | undefined;
  error: ApiError | undefined;
  isConflict: boolean;
  recalculate: () => Promise<RecalculationReport | undefined>;
}

export function useRecalculation(): UseRecalculationResult {
  const { status, data, error, isConflict, run } = useMutation<void, RecalculationReport>(() => recalculate());
  return { status, report: data, error, isConflict, recalculate: run };
}
