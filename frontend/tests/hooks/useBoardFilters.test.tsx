import { act, renderHook, waitFor } from '@testing-library/react';
import type { ReactNode } from 'react';
import { MemoryRouter, useLocation } from 'react-router';
import { describe, expect, it } from 'vitest';
import { useBoardFilters } from '../../src/hooks/useBoardFilters';

function setup(initialEntry: string) {
  const wrapper = ({ children }: { children: ReactNode }) => (
    <MemoryRouter initialEntries={[initialEntry]}>{children}</MemoryRouter>
  );
  return renderHook(
    () => {
      const board = useBoardFilters();
      const location = useLocation();
      return { ...board, search: location.search };
    },
    { wrapper },
  );
}

describe('useBoardFilters', () => {
  it('setPosition escribe position con el valor del backend y quita page', () => {
    const { result } = setup('/pizarra?page=3');

    act(() => result.current.setPosition('DEFENDER'));

    expect(result.current.search).toBe('?position=DEFENDER');
    expect(result.current.filters.position).toBe('DEFENDER');
  });

  it('setTeam escribe team y quita page', () => {
    const { result } = setup('/pizarra?page=3');

    act(() => result.current.setTeam('Real Madrid'));

    expect(result.current.search).toBe('?team=Real+Madrid');
    expect(result.current.filters.page).toBe(1);
  });

  it('setLeague escribe league y quita page', async () => {
    const { result } = setup('/pizarra?page=3');

    await act(async () => {
      await result.current.setLeague('LA_LIGA');
    });

    expect(result.current.search).toBe('?league=LA_LIGA');
  });

  it('los filtros vacíos no aparecen en la dirección', () => {
    const { result } = setup('/pizarra?position=DEFENDER');

    act(() => result.current.setPosition(null));

    expect(result.current.search).toBe('');
  });

  it('clear deja la dirección limpia', () => {
    const { result } = setup('/pizarra?position=DEFENDER&league=LA_LIGA&team=Real+Madrid&page=2');

    act(() => result.current.clear());

    expect(result.current.search).toBe('');
    expect(result.current.filters).toEqual({ position: null, league: null, team: null, page: 1 });
  });

  it('cambiar a una liga a la que no pertenece el equipo elegido quita team', async () => {
    const { result } = setup('/pizarra?league=LA_LIGA&team=Real+Madrid');

    await act(async () => {
      await result.current.setLeague('PREMIER');
    });

    await waitFor(() => expect(result.current.search).toBe('?league=PREMIER'));
    expect(result.current.filters.team).toBeNull();
  });

  it('cambiar a una liga a la que sí pertenece el equipo lo conserva', async () => {
    const { result } = setup('/pizarra?team=Real+Madrid');

    await act(async () => {
      await result.current.setLeague('LA_LIGA');
    });

    expect(result.current.filters).toMatchObject({ league: 'LA_LIGA', team: 'Real Madrid' });
    expect(result.current.search).toBe('?league=LA_LIGA&team=Real+Madrid');
  });
});
