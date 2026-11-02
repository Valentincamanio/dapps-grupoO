// Historia 5 de la 008: los filtros y la hoja del mercado viven en la dirección, con los mismos
// nombres que la pizarra y el ranking.
import { act, renderHook, waitFor } from '@testing-library/react';
import type { ReactNode } from 'react';
import { MemoryRouter, useLocation } from 'react-router';
import { describe, expect, it } from 'vitest';
import { MARKET_SHEET_SIZE, toMarketQuery, useMarketFilters } from '../../src/hooks/useMarketFilters';

function setup(initialEntry: string) {
  const wrapper = ({ children }: { children: ReactNode }) => (
    <MemoryRouter initialEntries={[initialEntry]}>{children}</MemoryRouter>
  );
  return renderHook(
    () => {
      const market = useMarketFilters();
      const location = useLocation();
      return { ...market, search: location.search };
    },
    { wrapper },
  );
}

describe('useMarketFilters', () => {
  it('setSide escribe side con el valor del backend y quita page', () => {
    const { result } = setup('/mercado?page=3');

    act(() => result.current.setSide('SELL'));

    expect(result.current.search).toBe('?side=SELL');
    expect(result.current.filters).toMatchObject({ side: 'SELL', page: 1 });
  });

  it('setOwnerType escribe ownerType y quita page', () => {
    const { result } = setup('/mercado?side=SELL&page=3');

    act(() => result.current.setOwnerType('ADMIN'));

    expect(result.current.search).toBe('?side=SELL&ownerType=ADMIN');
  });

  it('setTeam escribe team y quita page', () => {
    const { result } = setup('/mercado?page=3');

    act(() => result.current.setTeam('Real Madrid'));

    expect(result.current.search).toBe('?team=Real+Madrid');
  });

  it('setLeague escribe league y quita page', async () => {
    const { result } = setup('/mercado?page=3');

    await act(async () => {
      await result.current.setLeague('LA_LIGA');
    });

    expect(result.current.search).toBe('?league=LA_LIGA');
  });

  it('cambiar a una liga a la que no pertenece el equipo elegido quita team', async () => {
    const { result } = setup('/mercado?league=LA_LIGA&team=Real+Madrid');

    await act(async () => {
      await result.current.setLeague('PREMIER');
    });

    await waitFor(() => expect(result.current.search).toBe('?league=PREMIER'));
    expect(result.current.filters.team).toBeNull();
  });

  it('setPage escribe la hoja sin tocar los filtros, y la primera hoja no aparece', () => {
    const { result } = setup('/mercado?side=BUY');

    act(() => result.current.setPage(2));
    const second = result.current.search;
    act(() => result.current.setPage(1));

    expect(second).toBe('?side=BUY&page=2');
    expect(result.current.search).toBe('?side=BUY');
  });

  it('los filtros vacíos no aparecen en la dirección', () => {
    const { result } = setup('/mercado?side=SELL');

    act(() => result.current.setSide(null));

    expect(result.current.search).toBe('');
  });

  it('abrir una dirección con filtros los reproduce', () => {
    const { result } = setup('/mercado?side=BUY&league=LA_LIGA&team=Real+Madrid&ownerType=USER&page=2');

    const { filters } = result.current;

    expect(filters).toEqual({ side: 'BUY', league: 'LA_LIGA', team: 'Real Madrid', ownerType: 'USER', page: 2 });
  });

  it('un valor que no es del backend no filtra', () => {
    const { result } = setup('/mercado?side=X&league=Y&ownerType=Z&team=%20&page=-3');

    const { filters } = result.current;

    expect(filters).toEqual({ side: null, league: null, team: null, ownerType: null, page: 1 });
  });

  it('clear deja la dirección limpia', () => {
    const { result } = setup('/mercado?side=SELL&league=LA_LIGA&ownerType=USER&page=2');

    act(() => result.current.clear());

    expect(result.current.search).toBe('');
  });

  it('hacia el backend la hoja cuenta desde 0 y los filtros vacíos no viajan', () => {
    const query = toMarketQuery({ side: 'SELL', league: null, team: null, ownerType: 'USER', page: 2 });

    expect(query).toEqual({
      side: 'SELL',
      league: undefined,
      team: undefined,
      ownerType: 'USER',
      page: 1,
      size: MARKET_SHEET_SIZE,
    });
  });
});
