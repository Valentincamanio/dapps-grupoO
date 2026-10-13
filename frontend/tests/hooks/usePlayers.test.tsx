import { act, renderHook, waitFor } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { usePlayers } from '../../src/hooks/usePlayers';
import type { BoardFilters } from '../../src/types/board';
import { TEAM_WITHOUT_GOALKEEPERS } from '../mocks/players';
import { server } from '../mocks/server';

function filtersFor(overrides: Partial<BoardFilters> = {}): BoardFilters {
  return { position: null, league: null, team: null, page: 1, ...overrides };
}

describe('usePlayers', () => {
  it('la primera hoja trae 12 jugadores y hay 5 hojas para 55', async () => {
    const { result } = renderHook(() => usePlayers(filtersFor()));

    await waitFor(() => expect(result.current.status).toBe('success'));

    expect(result.current.sheet?.players).toHaveLength(12);
    expect(result.current.sheet?.sheet).toBe(1);
    expect(result.current.sheet?.totalSheets).toBe(5);
    expect(result.current.sheet?.hasPrevious).toBe(false);
    expect(result.current.sheet?.hasNext).toBe(true);
  });

  it('la última hoja trae los 7 jugadores restantes', async () => {
    const { result } = renderHook(() => usePlayers(filtersFor({ page: 5 })));

    await waitFor(() => expect(result.current.status).toBe('success'));

    expect(result.current.sheet?.players).toHaveLength(7);
    expect(result.current.sheet?.hasNext).toBe(false);
    expect(result.current.sheet?.hasPrevious).toBe(true);
  });

  it('una hoja fuera de rango se marca como isOutOfRange', async () => {
    const { result } = renderHook(() => usePlayers(filtersFor({ page: 40 })));

    await waitFor(() => expect(result.current.status).toBe('success'));

    expect(result.current.sheet?.isOutOfRange).toBe(true);
    expect(result.current.sheet?.isEmptyCombination).toBe(false);
  });

  it('un equipo inexistente se marca como isEmptyCombination', async () => {
    const { result } = renderHook(() => usePlayers(filtersFor({ team: 'Equipo Fantasma' })));

    await waitFor(() => expect(result.current.status).toBe('success'));

    expect(result.current.sheet?.isEmptyCombination).toBe(true);
    expect(result.current.sheet?.isOutOfRange).toBe(false);
  });

  it('un equipo sin arqueros con esa posición es una combinación vacía', async () => {
    const filters = filtersFor({ position: 'GOALKEEPER', team: TEAM_WITHOUT_GOALKEEPERS });
    const { result } = renderHook(() => usePlayers(filters));

    await waitFor(() => expect(result.current.status).toBe('success'));

    expect(result.current.sheet?.isEmptyCombination).toBe(true);
  });

  it('ante un error de red queda en error y retry carga la hoja', async () => {
    server.use(http.get('*/api/players', () => HttpResponse.error(), { once: true }));
    const { result } = renderHook(() => usePlayers(filtersFor()));
    await waitFor(() => expect(result.current.status).toBe('error'));

    act(() => result.current.retry());

    await waitFor(() => expect(result.current.status).toBe('success'));
    expect(result.current.sheet?.players).toHaveLength(12);
  });
});
