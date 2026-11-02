// Historia 2 de la 008: el portfolio actual del usuario de la sesión, con carga, error y reintento.
import { act, renderHook, waitFor } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import type { ReactNode } from 'react';
import { afterEach, describe, expect, it } from 'vitest';
import { SessionProvider } from '../../src/context/SessionContext';
import { usePortfolio } from '../../src/hooks/usePortfolio';
import { writeSession } from '../../src/services/sessionStorage';
import { SEED_TOKEN } from '../mocks/handlers';
import { CURRENT_PORTFOLIO, MARKET_USER_ID, PORTFOLIO_AT_DATE, PORTFOLIO_DATE } from '../mocks/market';
import { server } from '../mocks/server';

const PORTFOLIO_PATH = '*/api/users/:id/portfolio';

function wrapper({ children }: { children: ReactNode }) {
  return <SessionProvider>{children}</SessionProvider>;
}

function withSession() {
  writeSession({ token: SEED_TOKEN, expiresAt: new Date(Date.now() + 60 * 60 * 1000).toISOString() });
}

// Las direcciones del portfolio pedidas desde que se llama
function trackPortfolioRequests(): string[] {
  const requested: string[] = [];
  server.events.on('request:start', ({ request }) => {
    const url = new URL(request.url);
    if (url.pathname.endsWith('/portfolio')) requested.push(url.pathname + url.search);
  });
  return requested;
}

afterEach(() => server.events.removeAllListeners());

describe('usePortfolio', () => {
  it('trae el portfolio actual de la cuenta de la sesión', async () => {
    withSession();
    const requested = trackPortfolioRequests();

    const { result } = renderHook(() => usePortfolio(), { wrapper });

    await waitFor(() => expect(result.current.status).toBe('success'));
    expect(result.current.portfolio).toEqual(CURRENT_PORTFOLIO);
    expect(requested).toEqual([`/api/users/${MARKET_USER_ID}/portfolio`]);
  });

  it('mientras el servidor no responde está cargando', async () => {
    withSession();
    let respond: () => void = () => {};
    const answered = new Promise<void>((resolve) => {
      respond = resolve;
    });
    server.use(
      http.get(PORTFOLIO_PATH, async () => {
        await answered;
        return HttpResponse.json(CURRENT_PORTFOLIO);
      }),
    );
    const requested = trackPortfolioRequests();
    const { result } = renderHook(() => usePortfolio(), { wrapper });
    await waitFor(() => expect(requested).toHaveLength(1));

    expect(result.current.status).toBe('loading');
    expect(result.current.portfolio).toBeUndefined();

    respond();
    await waitFor(() => expect(result.current.status).toBe('success'));
  });

  it('sin sesión no pide nada', async () => {
    const requested = trackPortfolioRequests();

    const { result } = renderHook(() => usePortfolio(), { wrapper });

    await waitFor(() => expect(result.current.status).toBe('idle'));
    expect(result.current.portfolio).toBeUndefined();
    expect(requested).toEqual([]);
  });

  it('con una fecha trae el portfolio de ese día', async () => {
    withSession();
    const requested = trackPortfolioRequests();

    const { result } = renderHook(() => usePortfolio(PORTFOLIO_DATE), { wrapper });

    await waitFor(() => expect(result.current.status).toBe('success'));
    expect(result.current.portfolio).toEqual(PORTFOLIO_AT_DATE);
    expect(result.current.portfolio?.totals.availableBalance).toBeUndefined();
    expect(requested).toEqual([`/api/users/${MARKET_USER_ID}/portfolio?date=${PORTFOLIO_DATE}`]);
  });

  it('al cambiar la fecha vuelve a pedir, y sin ella trae el actual', async () => {
    withSession();
    const requested = trackPortfolioRequests();
    const { result, rerender } = renderHook((date?: string) => usePortfolio(date), {
      wrapper,
      initialProps: PORTFOLIO_DATE,
    });
    await waitFor(() => expect(result.current.portfolio?.date).toBe(PORTFOLIO_DATE));

    rerender(undefined);

    await waitFor(() => expect(result.current.portfolio).toEqual(CURRENT_PORTFOLIO));
    expect(requested).toEqual([
      `/api/users/${MARKET_USER_ID}/portfolio?date=${PORTFOLIO_DATE}`,
      `/api/users/${MARKET_USER_ID}/portfolio`,
    ]);
  });

  it('una fecha rechazada queda como error con el mensaje del servidor', async () => {
    withSession();

    const { result } = renderHook(() => usePortfolio('2026-08-10'), { wrapper });

    await waitFor(() => expect(result.current.status).toBe('error'));
    expect(result.current.error?.status).toBe(400);
    expect(result.current.error?.message).toBe(
      'La fecha 2026-08-10 es anterior al registro del usuario (2026-08-17).',
    );
    expect(result.current.portfolio).toBeUndefined();
  });

  it('si el servidor falla queda el error, y reintentar lo recupera', async () => {
    withSession();
    server.use(http.get(PORTFOLIO_PATH, () => HttpResponse.error(), { once: true }));
    const { result } = renderHook(() => usePortfolio(), { wrapper });
    await waitFor(() => expect(result.current.status).toBe('error'));
    expect(result.current.error?.message).toBe('no se pudo conectar con la pizarra');
    expect(result.current.portfolio).toBeUndefined();

    act(() => result.current.retry());

    await waitFor(() => expect(result.current.status).toBe('success'));
    expect(result.current.portfolio?.positions).toHaveLength(2);
  });
});
