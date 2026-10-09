import { act, renderHook, waitFor } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { afterEach, describe, expect, it } from 'vitest';
import { useCatalog } from '../../src/hooks/useCatalog';
import { server } from '../mocks/server';

// Cuenta los pedidos a GET /api/players que salen durante el test
function countPlayerRequests(): string[] {
  const urls: string[] = [];
  server.events.on('request:start', ({ request }) => {
    const url = new URL(request.url);
    if (url.pathname === '/api/players') urls.push(url.search);
  });
  return urls;
}

afterEach(() => {
  server.events.removeAllListeners();
});

describe('useCatalog', () => {
  it('no pide nada al montarse', async () => {
    const requests = countPlayerRequests();

    const { result } = renderHook(() => useCatalog());
    await new Promise((resolve) => setTimeout(resolve, 20));

    expect(result.current.status).toBe('idle');
    expect(requests).toHaveLength(0);
  });

  it('ensureLoaded trae los 55 jugadores en dos pedidos', async () => {
    const requests = countPlayerRequests();
    const { result } = renderHook(() => useCatalog());

    await act(async () => {
      await result.current.ensureLoaded();
    });

    expect(result.current.status).toBe('success');
    expect(result.current.players).toHaveLength(55);
    expect(requests).toEqual(['?size=50&page=0', '?size=50&page=1']);
  });

  it('dos llamadas simultáneas hacen un solo recorrido', async () => {
    const requests = countPlayerRequests();
    const { result } = renderHook(() => useCatalog());

    await act(async () => {
      await Promise.all([result.current.ensureLoaded(), result.current.ensureLoaded()]);
    });

    expect(result.current.players).toHaveLength(55);
    expect(requests).toHaveLength(2);
  });

  it('un segundo ensureLoaded no vuelve a pedir', async () => {
    const requests = countPlayerRequests();
    const { result } = renderHook(() => useCatalog());
    await act(async () => {
      await result.current.ensureLoaded();
    });

    await act(async () => {
      await result.current.ensureLoaded();
    });

    expect(result.current.players).toHaveLength(55);
    expect(requests).toHaveLength(2);
  });

  it('si falla queda en error y retry vuelve a pedir y carga', async () => {
    const error = {
      timestamp: '2026-10-01T10:00:00Z',
      status: 500,
      error: 'Internal Server Error',
      message: 'Error interno.',
      path: '/players',
    };
    server.use(http.get('*/api/players', () => HttpResponse.json(error, { status: 500 }), { once: true }));
    const { result } = renderHook(() => useCatalog());
    await act(async () => {
      await result.current.ensureLoaded();
    });
    expect(result.current.status).toBe('error');
    expect(result.current.error?.status).toBe(500);

    await act(async () => {
      await result.current.retry();
    });

    await waitFor(() => expect(result.current.status).toBe('success'));
    expect(result.current.players).toHaveLength(55);
  });
});
