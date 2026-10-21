import { act, renderHook, waitFor } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { afterEach, describe, expect, it } from 'vitest';
import { usePlayerQuote } from '../../src/hooks/usePlayerQuote';
import {
  currentQuoteOf,
  PLAYER_WITH_ONLY_INITIAL_ID,
  PLAYER_WITHOUT_QUOTE_ID,
  REQUOTED_PLAYER_ID,
} from '../mocks/quotes';
import { server } from '../mocks/server';

const CURRENT_PATH = '*/api/players/:id/quotes/current';

afterEach(() => server.events.removeAllListeners());

describe('usePlayerQuote', () => {
  it('trae la cotización actual del jugador', async () => {
    const { result } = renderHook(() => usePlayerQuote(REQUOTED_PLAYER_ID));

    await waitFor(() => expect(result.current.status).toBe('success'));

    expect(result.current.quote).toEqual({
      playerId: REQUOTED_PLAYER_ID,
      ...currentQuoteOf(REQUOTED_PLAYER_ID),
    });
    expect(result.current.noQuote).toBe(false);
  });

  it('la cotización inicial llega sin variación', async () => {
    const { result } = renderHook(() => usePlayerQuote(PLAYER_WITH_ONLY_INITIAL_ID));

    await waitFor(() => expect(result.current.status).toBe('success'));

    expect(result.current.quote?.type).toBe('INITIAL');
    expect(result.current.quote?.variation).toBeUndefined();
  });

  it('un 404 de la cotización es "sin cotización" y no un error', async () => {
    const { result } = renderHook(() => usePlayerQuote(PLAYER_WITHOUT_QUOTE_ID));

    await waitFor(() => expect(result.current.status).toBe('success'));

    expect(result.current.noQuote).toBe(true);
    expect(result.current.quote).toBeUndefined();
    expect(result.current.error).toBeUndefined();
  });

  it('un error del servidor es un error, no "sin cotización", y retry lo recupera', async () => {
    server.use(http.get(CURRENT_PATH, () => HttpResponse.error(), { once: true }));
    const { result } = renderHook(() => usePlayerQuote(REQUOTED_PLAYER_ID));
    await waitFor(() => expect(result.current.status).toBe('error'));
    expect(result.current.noQuote).toBe(false);
    expect(result.current.error?.message).toBe('no se pudo conectar con la pizarra');

    act(() => result.current.retry());

    await waitFor(() => expect(result.current.status).toBe('success'));
    expect(result.current.quote?.playerId).toBe(REQUOTED_PLAYER_ID);
  });

  it('sin jugador cargado no consulta', async () => {
    const requested: string[] = [];
    server.events.on('request:start', ({ request }) => {
      requested.push(request.url);
    });

    const { result } = renderHook(() => usePlayerQuote(undefined));
    await waitFor(() => expect(result.current.status).toBe('idle'));

    expect(result.current.noQuote).toBe(false);
    expect(requested).toHaveLength(0);
  });
});
