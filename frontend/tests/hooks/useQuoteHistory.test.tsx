import { renderHook, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it } from 'vitest';
import { HISTORY_SIZE, useQuoteHistory } from '../../src/hooks/useQuoteHistory';
import type { QuoteRange } from '../../src/types/quotes';
import { quoteHistoryOf, REQUOTED_PLAYER_ID } from '../mocks/quotes';
import { server } from '../mocks/server';

const HISTORY_PATH = `/api/players/${REQUOTED_PLAYER_ID}/quotes`;

// Direcciones de los pedidos al historial hechos durante el test
function recordHistoryRequests(): URL[] {
  const urls: URL[] = [];
  server.events.on('request:start', ({ request }) => {
    const url = new URL(request.url);
    if (url.pathname === HISTORY_PATH) urls.push(url);
  });
  return urls;
}

afterEach(() => server.events.removeAllListeners());

describe('useQuoteHistory', () => {
  it('sin rango trae todo el historial en una consulta de 100', async () => {
    const requests = recordHistoryRequests();

    const { result } = renderHook(() => useQuoteHistory(REQUOTED_PLAYER_ID, {}));
    await waitFor(() => expect(result.current.status).toBe('success'));

    expect(result.current.quotes).toEqual(quoteHistoryOf(REQUOTED_PLAYER_ID));
    expect(requests).toHaveLength(1);
    expect(requests[0].searchParams.get('size')).toBe(String(HISTORY_SIZE));
    expect(requests[0].searchParams.has('from')).toBe(false);
    expect(requests[0].searchParams.has('to')).toBe(false);
  });

  it('con rango pide from y to y trae las cotizaciones de ese rango, extremos incluidos', async () => {
    const requests = recordHistoryRequests();

    const { result } = renderHook(() =>
      useQuoteHistory(REQUOTED_PLAYER_ID, { from: '2026-08-24', to: '2026-08-31' }),
    );
    await waitFor(() => expect(result.current.status).toBe('success'));

    expect(requests[0].searchParams.get('from')).toBe('2026-08-24');
    expect(requests[0].searchParams.get('to')).toBe('2026-08-31');
    expect(result.current.quotes?.map((quote) => quote.type)).toEqual(['CALCULATED', 'REQUOTED', 'CALCULATED']);
  });

  it('cambiar el rango cambia la consulta', async () => {
    const requests = recordHistoryRequests();
    const { result, rerender } = renderHook(({ range }) => useQuoteHistory(REQUOTED_PLAYER_ID, range), {
      initialProps: { range: {} as QuoteRange },
    });
    await waitFor(() => expect(result.current.status).toBe('success'));

    rerender({ range: { from: '2026-09-28', to: '2026-10-12' } });
    await waitFor(() => expect(requests).toHaveLength(2));
    await waitFor(() => expect(result.current.status).toBe('success'));

    expect(requests[1].searchParams.get('from')).toBe('2026-09-28');
    expect(result.current.quotes).toHaveLength(3);
  });

  it('el mismo rango en otro objeto no repite la consulta', async () => {
    const requests = recordHistoryRequests();
    const { result, rerender } = renderHook(({ range }) => useQuoteHistory(REQUOTED_PLAYER_ID, range), {
      initialProps: { range: { from: '2026-08-24' } as QuoteRange },
    });
    await waitFor(() => expect(result.current.status).toBe('success'));

    rerender({ range: { from: '2026-08-24' } });

    expect(requests).toHaveLength(1);
  });

  it('un rango sin cotizaciones queda vacío', async () => {
    const { result } = renderHook(() =>
      useQuoteHistory(REQUOTED_PLAYER_ID, { from: '2026-01-01', to: '2026-01-31' }),
    );

    await waitFor(() => expect(result.current.status).toBe('success'));

    expect(result.current.isEmpty).toBe(true);
    expect(result.current.quotes).toEqual([]);
  });

  it('un rango invertido es un error con el mensaje del backend', async () => {
    const { result } = renderHook(() =>
      useQuoteHistory(REQUOTED_PLAYER_ID, { from: '2026-09-01', to: '2026-08-01' }),
    );

    await waitFor(() => expect(result.current.status).toBe('error'));

    expect(result.current.error?.message).toBe(
      'El inicio del rango (2026-09-01) es posterior al final (2026-08-01).',
    );
    expect(result.current.isEmpty).toBe(false);
  });

  it('sin jugador cargado no consulta', async () => {
    const requests = recordHistoryRequests();

    const { result } = renderHook(() => useQuoteHistory(undefined, {}));
    await waitFor(() => expect(result.current.status).toBe('idle'));

    expect(requests).toHaveLength(0);
  });
});
