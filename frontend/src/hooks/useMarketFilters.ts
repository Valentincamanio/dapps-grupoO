// Filtros del mercado guardados en la dirección (research D27): side, league, team, ownerType y
// page, con los mismos nombres que la pizarra y el ranking donde coinciden. Los filtros vacíos y la
// primera hoja no aparecen, y cambiar un filtro vuelve a la primera hoja, como en useBoardFilters.
import { useCallback, useMemo } from 'react';
import { useSearchParams } from 'react-router';
import { LEAGUES, type League } from '../types/api';
import {
  ORDER_SIDES,
  OWNER_TYPES,
  type MarketFilters,
  type MarketOrderQuery,
  type OrderSide,
  type OwnerType,
} from '../types/market';
import { teamBelongsToLeague } from '../utils/teams';
import { useCatalog } from './useCatalog';

// Órdenes por hoja del listado
export const MARKET_SHEET_SIZE = 10;

// Un valor que no es del backend no filtra
export function parseMarketFilters(params: URLSearchParams): MarketFilters {
  const side = params.get('side');
  const league = params.get('league');
  const team = params.get('team')?.trim();
  const ownerType = params.get('ownerType');
  const page = Number(params.get('page'));

  return {
    side: ORDER_SIDES.includes(side as OrderSide) ? (side as OrderSide) : null,
    league: LEAGUES.includes(league as League) ? (league as League) : null,
    team: team ? team : null,
    ownerType: OWNER_TYPES.includes(ownerType as OwnerType) ? (ownerType as OwnerType) : null,
    page: Number.isInteger(page) && page >= 1 ? page : 1,
  };
}

export function toMarketSearchParams(filters: MarketFilters): URLSearchParams {
  const params = new URLSearchParams();
  if (filters.side) params.set('side', filters.side);
  if (filters.league) params.set('league', filters.league);
  if (filters.team) params.set('team', filters.team);
  if (filters.ownerType) params.set('ownerType', filters.ownerType);
  if (filters.page > 1) params.set('page', String(filters.page));
  return params;
}

// Hacia el backend la hoja cuenta desde 0
export function toMarketQuery(filters: MarketFilters): MarketOrderQuery {
  return {
    side: filters.side ?? undefined,
    league: filters.league ?? undefined,
    team: filters.team ?? undefined,
    ownerType: filters.ownerType ?? undefined,
    page: filters.page - 1,
    size: MARKET_SHEET_SIZE,
  };
}

export function useMarketFilters() {
  const [searchParams, setSearchParams] = useSearchParams();
  const filters = useMemo(() => parseMarketFilters(searchParams), [searchParams]);
  const { ensureLoaded } = useCatalog();

  const write = useCallback(
    (next: MarketFilters) => setSearchParams(toMarketSearchParams(next), { replace: false }),
    [setSearchParams],
  );

  const setPage = useCallback(
    (page: number) => write({ ...parseMarketFilters(searchParams), page }),
    [searchParams, write],
  );

  // Cambiar cualquier filtro vuelve a la primera hoja
  const change = useCallback(
    (changed: Partial<MarketFilters>) => write({ ...parseMarketFilters(searchParams), ...changed, page: 1 }),
    [searchParams, write],
  );

  const setSide = useCallback((side: OrderSide | null) => change({ side }), [change]);

  const setTeam = useCallback((team: string | null) => change({ team }), [change]);

  const setOwnerType = useCallback((ownerType: OwnerType | null) => change({ ownerType }), [change]);

  const setLeague = useCallback(
    async (league: League | null) => {
      let team = parseMarketFilters(searchParams).team;
      // El equipo elegido se conserva solo si pertenece a la nueva liga
      if (team && league) {
        const players = await ensureLoaded();
        // Si el catálogo no se pudo cargar, el equipo se conserva
        if (players && !teamBelongsToLeague(players, team, league)) team = null;
      }
      change({ league, team });
    },
    [change, ensureLoaded, searchParams],
  );

  const clear = useCallback(
    () => setSearchParams(new URLSearchParams(), { replace: false }),
    [setSearchParams],
  );

  return { filters, setPage, setSide, setLeague, setTeam, setOwnerType, clear };
}
