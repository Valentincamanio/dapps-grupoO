// Catálogo completo para el buscador (research R-2). Se pide recién al enfocar la búsqueda.
import { useCallback, useSyncExternalStore } from 'react';
import { ApiRequestError } from '../services/httpClient';
import { getPlayers } from '../services/playerService';
import type { ApiError, Player } from '../types/api';

type CatalogStatus = 'idle' | 'loading' | 'success' | 'error';

interface CatalogState {
  status: CatalogStatus;
  players: Player[];
  error?: ApiError;
}

const PAGE_SIZE = 50;

// Caché compartida por todos los componentes que usan el hook
let cache: CatalogState = { status: 'idle', players: [] };
let pending: Promise<void> | null = null;
const listeners = new Set<() => void>();

function publish(next: CatalogState): void {
  cache = next;
  listeners.forEach((listener) => listener());
}

function toApiError(error: unknown): ApiError {
  if (error instanceof ApiRequestError) return error.apiError;
  return {
    timestamp: new Date().toISOString(),
    status: 0,
    error: 'Network Error',
    message: 'no se pudo conectar con la pizarra',
    path: '/players',
  };
}

async function loadAll(): Promise<void> {
  publish({ status: 'loading', players: [] });
  try {
    const players: Player[] = [];
    let page = 0;
    let hasNext = true;
    while (hasNext) {
      const result = await getPlayers({ size: PAGE_SIZE, page });
      players.push(...result.content);
      hasNext = result.hasNext;
      page += 1;
    }
    publish({ status: 'success', players });
  } catch (error) {
    // Se descarta la caché para que retry vuelva a pedir
    publish({ status: 'error', players: [], error: toApiError(error) });
  }
}

// Resuelve con el catálogo completo, o con null si no se pudo cargar
function ensureLoaded(): Promise<Player[] | null> {
  if (!pending && cache.status !== 'success') {
    pending = loadAll().finally(() => {
      pending = null;
    });
  }
  return (pending ?? Promise.resolve()).then(() =>
    cache.status === 'success' ? cache.players : null,
  );
}

// Solo para los tests: vuelve la caché al estado inicial
export function resetCatalogCache(): void {
  cache = { status: 'idle', players: [] };
  pending = null;
  listeners.forEach((listener) => listener());
}

function subscribe(listener: () => void): () => void {
  const wrapped = () => listener();
  listeners.add(wrapped);
  return () => {
    listeners.delete(wrapped);
  };
}

function getSnapshot(): CatalogState {
  return cache;
}

export function useCatalog() {
  const state = useSyncExternalStore(subscribe, getSnapshot);

  const retry = useCallback(() => ensureLoaded(), []);

  return {
    status: state.status,
    players: state.players,
    error: state.error,
    ensureLoaded,
    retry,
  };
}
