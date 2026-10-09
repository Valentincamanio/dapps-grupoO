// Filtros de la pizarra guardados en la dirección (research R-6)
import { useCallback, useMemo } from 'react';
import { useSearchParams } from 'react-router';
import type { BoardFilters } from '../types/board';
import { parseBoardFilters, toSearchParams } from '../utils/filters';

export function useBoardFilters() {
  const [searchParams, setSearchParams] = useSearchParams();
  const filters = useMemo(() => parseBoardFilters(searchParams), [searchParams]);

  const setPage = useCallback(
    (page: number) => {
      const next: BoardFilters = { ...parseBoardFilters(searchParams), page };
      setSearchParams(toSearchParams(next), { replace: false });
    },
    [searchParams, setSearchParams],
  );

  return { filters, setPage };
}
