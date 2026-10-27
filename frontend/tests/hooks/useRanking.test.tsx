import { act, renderHook, waitFor } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import type { ReactNode } from 'react';
import { MemoryRouter, useLocation } from 'react-router';
import { afterEach, describe, expect, it } from 'vitest';
import { RANKING_SIZE, useRanking } from '../../src/hooks/useRanking';
import { ACTIVE_STRATEGY, RANKING_ROWS } from '../mocks/quotes';
import { server } from '../mocks/server';

const RANKING_PATH = '/api/players/ranking';

// Direcciones de los pedidos al ranking hechos durante el test
function recordRankingRequests(): URL[] {
  const urls: URL[] = [];
  server.events.on('request:start', ({ request }) => {
    const url = new URL(request.url);
    if (url.pathname === RANKING_PATH) urls.push(url);
  });
  return urls;
}

function setup(initialEntry: string) {
  const wrapper = ({ children }: { children: ReactNode }) => (
    <MemoryRouter initialEntries={[initialEntry]}>{children}</MemoryRouter>
  );
  return renderHook(
    () => {
      const ranking = useRanking();
      const location = useLocation();
      return { ...ranking, search: location.search };
    },
    { wrapper },
  );
}

afterEach(() => server.events.removeAllListeners());

describe('useRanking', () => {
  it('trae la primera hoja ordenada por cotización descendente con la estrategia activa', async () => {
    const { result } = setup('/ranking');

    await waitFor(() => expect(result.current.status).toBe('success'));

    const values = result.current.sheet!.rows.map((row) => row.quote.value);
    expect(values).toHaveLength(RANKING_SIZE);
    expect(values).toEqual([...values].sort((a, b) => b - a));
    expect(result.current.sheet?.rows[0].playerId).toBe(RANKING_ROWS[0].playerId);
    expect(result.current.sheet?.activeStrategy).toEqual(ACTIVE_STRATEGY);
    expect(result.current.sheet?.firstRank).toBe(1);
  });

  it('los filtros y la hoja salen de la dirección, sin los vacíos', async () => {
    const requests = recordRankingRequests();
    const { result } = setup('/ranking?league=LA_LIGA&position=FORWARD&page=1');

    await waitFor(() => expect(result.current.status).toBe('success'));

    const params = requests[0].searchParams;
    expect(params.get('league')).toBe('LA_LIGA');
    expect(params.get('position')).toBe('FORWARD');
    expect(params.get('page')).toBe('0');
    expect(params.get('size')).toBe(String(RANKING_SIZE));
    expect(params.has('team')).toBe(false);
    result.current.sheet!.rows.forEach((row) => {
      expect(row).toMatchObject({ league: 'LA_LIGA', position: 'FORWARD' });
    });
  });

  it('la hoja 2 de la dirección pide la página 1 y numera desde el puesto 11', async () => {
    const requests = recordRankingRequests();
    const { result } = setup('/ranking?page=2');

    await waitFor(() => expect(result.current.status).toBe('success'));

    expect(requests[0].searchParams.get('page')).toBe('1');
    expect(result.current.sheet?.sheet).toBe(2);
    expect(result.current.sheet?.firstRank).toBe(RANKING_SIZE + 1);
    expect(result.current.sheet?.hasPrevious).toBe(true);
  });

  it('filtrar escribe el filtro en la dirección y reinicia la hoja', async () => {
    const requests = recordRankingRequests();
    const { result } = setup('/ranking?page=3');
    await waitFor(() => expect(result.current.status).toBe('success'));

    act(() => result.current.setPosition('DEFENDER'));

    expect(result.current.search).toBe('?position=DEFENDER');
    await waitFor(() => expect(requests).toHaveLength(2));
    expect(requests[1].searchParams.get('position')).toBe('DEFENDER');
    expect(requests[1].searchParams.get('page')).toBe('0');
  });

  it('cambiar de hoja la escribe en la dirección y vuelve a pedir', async () => {
    const requests = recordRankingRequests();
    const { result } = setup('/ranking');
    await waitFor(() => expect(result.current.status).toBe('success'));

    act(() => result.current.setPage(2));

    expect(result.current.search).toBe('?page=2');
    await waitFor(() => expect(requests).toHaveLength(2));
    expect(requests[1].searchParams.get('page')).toBe('1');
  });

  it('un equipo sin jugadores cotizados deja el ranking vacío', async () => {
    const { result } = setup('/ranking?team=Equipo+Fantasma');

    await waitFor(() => expect(result.current.status).toBe('success'));

    expect(result.current.sheet?.isEmpty).toBe(true);
    expect(result.current.sheet?.isOutOfRange).toBe(false);
  });

  it('una hoja fuera de rango se marca como isOutOfRange', async () => {
    const { result } = setup('/ranking?page=99');

    await waitFor(() => expect(result.current.status).toBe('success'));

    expect(result.current.sheet?.isOutOfRange).toBe(true);
    expect(result.current.sheet?.isEmpty).toBe(false);
  });

  it('ante un error queda en error y retry carga la hoja', async () => {
    server.use(http.get(`*${RANKING_PATH}`, () => HttpResponse.error(), { once: true }));
    const { result } = setup('/ranking');
    await waitFor(() => expect(result.current.status).toBe('error'));

    act(() => result.current.retry());

    await waitFor(() => expect(result.current.status).toBe('success'));
    expect(result.current.sheet?.rows).toHaveLength(RANKING_SIZE);
  });
});
