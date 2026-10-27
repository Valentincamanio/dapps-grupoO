import { act, renderHook, waitFor } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { afterEach, describe, expect, it } from 'vitest';
import { useCurrentQuotes } from '../../src/hooks/useCurrentQuotes';
import { currentQuoteOf, PLAYER_WITHOUT_QUOTE_ID, REQUOTED_PLAYER_ID } from '../mocks/quotes';
import { server } from '../mocks/server';

const BATCH_PATH = '/api/players/quotes/current';

// Direcciones de los pedidos al lote hechos durante el test
function recordBatchRequests(): URL[] {
  const urls: URL[] = [];
  server.events.on('request:start', ({ request }) => {
    const url = new URL(request.url);
    if (url.pathname === BATCH_PATH) urls.push(url);
  });
  return urls;
}

afterEach(() => server.events.removeAllListeners());

describe('useCurrentQuotes', () => {
  it('pide las cotizaciones de toda la hoja en una sola consulta', async () => {
    const requests = recordBatchRequests();
    const ids = [REQUOTED_PLAYER_ID, PLAYER_WITHOUT_QUOTE_ID, 4, 5];

    const { result } = renderHook(() => useCurrentQuotes(ids));
    await waitFor(() => expect(result.current.status).toBe('success'));

    expect(requests).toHaveLength(1);
    expect(requests[0].searchParams.get('ids')).toBe('1,2,4,5');
  });

  it('devuelve la cotización de cada jugador y "none" para el que no vino', async () => {
    const ids = [REQUOTED_PLAYER_ID, PLAYER_WITHOUT_QUOTE_ID];

    const { result } = renderHook(() => useCurrentQuotes(ids));
    await waitFor(() => expect(result.current.status).toBe('success'));

    expect(result.current.badgeFor(REQUOTED_PLAYER_ID)).toEqual({
      status: 'ready',
      quote: currentQuoteOf(REQUOTED_PLAYER_ID),
    });
    expect(result.current.badgeFor(PLAYER_WITHOUT_QUOTE_ID)).toEqual({ status: 'none' });
  });

  it('mientras carga cada jugador está en "loading"', () => {
    const { result } = renderHook(() => useCurrentQuotes([REQUOTED_PLAYER_ID]));

    expect(result.current.badgeFor(REQUOTED_PLAYER_ID)).toEqual({ status: 'loading' });
  });

  it('con la hoja vacía no consulta', async () => {
    const requests = recordBatchRequests();

    const { result } = renderHook(() => useCurrentQuotes([]));
    await waitFor(() => expect(result.current.status).toBe('success'));

    expect(requests).toHaveLength(0);
  });

  it('volver a renderizar con los mismos ids no repite la consulta', async () => {
    const requests = recordBatchRequests();
    const { result, rerender } = renderHook(({ ids }) => useCurrentQuotes(ids), {
      initialProps: { ids: [4, 5] },
    });
    await waitFor(() => expect(result.current.status).toBe('success'));

    rerender({ ids: [4, 5] });

    expect(result.current.status).toBe('success');
    expect(requests).toHaveLength(1);
  });

  it('otra hoja hace otra consulta con sus ids', async () => {
    const requests = recordBatchRequests();
    const { result, rerender } = renderHook(({ ids }) => useCurrentQuotes(ids), {
      initialProps: { ids: [4, 5] },
    });
    await waitFor(() => expect(result.current.status).toBe('success'));

    rerender({ ids: [6, 7] });
    await waitFor(() => expect(requests).toHaveLength(2));
    await waitFor(() => expect(result.current.status).toBe('success'));

    expect(requests[1].searchParams.get('ids')).toBe('6,7');
    expect(result.current.badgeFor(6).status).toBe('ready');
  });

  it('ante un error del lote cada jugador queda en error y retry lo recupera', async () => {
    server.use(http.get(`*${BATCH_PATH}`, () => HttpResponse.error(), { once: true }));
    const { result } = renderHook(() => useCurrentQuotes([REQUOTED_PLAYER_ID]));
    await waitFor(() => expect(result.current.status).toBe('error'));
    expect(result.current.badgeFor(REQUOTED_PLAYER_ID)).toEqual({ status: 'error' });

    act(() => result.current.retry());

    await waitFor(() => expect(result.current.status).toBe('success'));
    expect(result.current.badgeFor(REQUOTED_PLAYER_ID).status).toBe('ready');
  });
});
