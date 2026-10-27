// Simulación de una estrategia (POST /quotes/simulate): el pedido (una estrategia guardada o
// parámetros) más los filtros y la hoja del catálogo. Cambiar un filtro vuelve a la primera hoja
// y vuelve a simular con el mismo pedido; cada hoja recalcula todo en el backend.
import { useCallback, useState } from 'react';
import { simulate as simulateRequest } from '../services/recalculationService';
import type { ApiError, League, Position } from '../types/api';
import type { BoardFilters } from '../types/board';
import type { PendingWeek, SimulationPage, SimulationRequest, SimulationRow, StrategyRef } from '../types/quotes';
import { useMutation, type MutationStatus } from './useMutation';

export const SIMULATION_SIZE = 10;

const NO_FILTERS: BoardFilters = { position: null, league: null, team: null, page: 1 };

export interface SimulationSheet {
  rows: SimulationRow[];
  // null si se simularon parámetros
  strategy: StrategyRef | null;
  weeksSimulated: number;
  pendingWeeks: PendingWeek[];
  // Hoja actual, desde 1
  sheet: number;
  totalSheets: number;
  hasPrevious: boolean;
  hasNext: boolean;
  // La combinación de filtros no tiene jugadores
  isEmpty: boolean;
  // Hay jugadores, pero la hoja pedida está fuera de rango
  isOutOfRange: boolean;
}

interface SimulationArgs {
  request: SimulationRequest;
  filters: BoardFilters;
}

interface UseSimulationResult {
  // El último pedido simulado; null antes de simular
  request: SimulationRequest | null;
  filters: BoardFilters;
  status: MutationStatus;
  sheet: SimulationSheet | undefined;
  error: ApiError | undefined;
  // Simula un pedido nuevo desde la primera hoja, con los filtros elegidos
  simulate: (request: SimulationRequest) => Promise<void>;
  setPosition: (position: Position | null) => void;
  setLeague: (league: League | null) => void;
  setTeam: (team: string | null) => void;
  setPage: (page: number) => void;
  // Repite la última simulación
  retry: () => void;
}

async function runSimulation({ request, filters }: SimulationArgs): Promise<SimulationSheet> {
  const result: SimulationPage = await simulateRequest(request, {
    league: filters.league ?? undefined,
    team: filters.team ?? undefined,
    position: filters.position ?? undefined,
    page: filters.page - 1,
    size: SIMULATION_SIZE,
  });
  return {
    rows: result.content,
    strategy: result.strategy,
    weeksSimulated: result.weeksSimulated,
    pendingWeeks: result.pendingWeeks,
    sheet: result.page + 1,
    totalSheets: result.totalPages,
    hasPrevious: result.hasPrevious,
    hasNext: result.hasNext,
    isEmpty: result.totalElements === 0,
    isOutOfRange: result.totalElements > 0 && result.content.length === 0,
  };
}

export function useSimulation(): UseSimulationResult {
  const [request, setRequest] = useState<SimulationRequest | null>(null);
  const [filters, setFilters] = useState<BoardFilters>(NO_FILTERS);
  const { status, data, error, run } = useMutation<SimulationArgs, SimulationSheet>(runSimulation);

  // Guarda los filtros y, si ya hay un pedido, vuelve a simular con ellos
  const apply = useCallback(
    (next: BoardFilters) => {
      setFilters(next);
      if (request) void run({ request, filters: next });
    },
    [request, run],
  );

  const simulate = useCallback(
    async (next: SimulationRequest) => {
      const firstSheet = { ...filters, page: 1 };
      setRequest(next);
      setFilters(firstSheet);
      await run({ request: next, filters: firstSheet });
    },
    [filters, run],
  );

  const setPosition = useCallback((position: Position | null) => apply({ ...filters, position, page: 1 }), [apply, filters]);
  const setLeague = useCallback((league: League | null) => apply({ ...filters, league, page: 1 }), [apply, filters]);
  const setTeam = useCallback((team: string | null) => apply({ ...filters, team, page: 1 }), [apply, filters]);
  const setPage = useCallback((page: number) => apply({ ...filters, page }), [apply, filters]);
  const retry = useCallback(() => apply(filters), [apply, filters]);

  return {
    request,
    filters,
    status,
    sheet: data,
    error,
    simulate,
    setPosition,
    setLeague,
    setTeam,
    setPage,
    retry,
  };
}
