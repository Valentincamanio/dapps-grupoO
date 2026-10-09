// Filtros de la pizarra guardados en la dirección (research R-6)
import { useCallback, useMemo } from 'react';
import { useSearchParams } from 'react-router';
import type { League, Position } from '../types/api';
import type { BoardFilters } from '../types/board';
import { parseBoardFilters, toSearchParams } from '../utils/filters';
import { teamBelongsToLeague } from '../utils/teams';
import { useCatalog } from './useCatalog';

export function useBoardFilters() {
  const [searchParams, setSearchParams] = useSearchParams();
  const filters = useMemo(() => parseBoardFilters(searchParams), [searchParams]);
  const { ensureLoaded } = useCatalog();

  const write = useCallback(
    (next: BoardFilters) => setSearchParams(toSearchParams(next), { replace: false }),
    [setSearchParams],
  );

  const setPage = useCallback(
    (page: number) => write({ ...parseBoardFilters(searchParams), page }),
    [searchParams, write],
  );

  // Cambiar cualquier filtro vuelve a la primera hoja (RF-019)
  const setPosition = useCallback(
    (position: Position | null) => write({ ...parseBoardFilters(searchParams), position, page: 1 }),
    [searchParams, write],
  );

  const setTeam = useCallback(
    (team: string | null) => write({ ...parseBoardFilters(searchParams), team, page: 1 }),
    [searchParams, write],
  );

  const setLeague = useCallback(
    async (league: League | null) => {
      const current = parseBoardFilters(searchParams);
      let team = current.team;
      // El equipo elegido se conserva solo si pertenece a la nueva liga
      if (team && league) {
        const players = await ensureLoaded();
        // Si el catálogo no se pudo cargar, el equipo se conserva
        if (players && !teamBelongsToLeague(players, team, league)) team = null;
      }
      write({ ...current, league, team, page: 1 });
    },
    [ensureLoaded, searchParams, write],
  );

  const clear = useCallback(
    () => setSearchParams(new URLSearchParams(), { replace: false }),
    [setSearchParams],
  );

  return { filters, setPage, setPosition, setLeague, setTeam, clear };
}
