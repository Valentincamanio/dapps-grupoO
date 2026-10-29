// Historia 6 de la 008: el historial de operaciones del usuario de la sesión, con filtros y hoja.
import { act, renderHook, waitFor } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import type { ReactNode } from 'react';
import { afterEach, describe, expect, it } from 'vitest';
import { SessionProvider } from '../../src/context/SessionContext';
import { useTransactions } from '../../src/hooks/useTransactions';
import { writeSession } from '../../src/services/sessionStorage';
import type { TransactionQuery } from '../../src/types/market';
import { SEED_TOKEN } from '../mocks/handlers';
import { MARKET_USER_ID, SOLD_OUT_PLAYER_ID, TRANSACTIONS } from '../mocks/market';
import { server } from '../mocks/server';

const TRANSACTIONS_PATH = '*/api/users/:id/transactions';

function wrapper({ children }: { children: ReactNode }) {
  return <SessionProvider>{children}</SessionProvider>;
}

function withSession() {
  writeSession({ token: SEED_TOKEN, expiresAt: new Date(Date.now() + 60 * 60 * 1000).toISOString() });
}

// Las direcciones del historial pedidas desde que se llama
function trackRequests(): string[] {
  const requested: string[] = [];
  server.events.on('request:start', ({ request }) => {
    const url = new URL(request.url);
    if (url.pathname.endsWith('/transactions')) requested.push(url.pathname + url.search);
  });
  return requested;
}

async function renderTransactions(query: TransactionQuery) {
  withSession();
  const rendered = renderHook(() => useTransactions(query), { wrapper });
  await waitFor(() => expect(rendered.result.current.status).toBe('success'));
  return rendered;
}

afterEach(() => server.events.removeAllListeners());

describe('useTransactions', () => {
  it('trae el historial de la cuenta de la sesión de la más nueva a la más vieja', async () => {
    const requested = trackRequests();

    const { result } = await renderTransactions({});

    expect(result.current.transactions).toEqual(TRANSACTIONS);
    expect(result.current.page?.totalElements).toBe(TRANSACTIONS.length);
    expect(requested).toEqual([`/api/users/${MARKET_USER_ID}/transactions`]);
  });

  it('manda los filtros que vienen y no los vacíos', async () => {
    const requested = trackRequests();

    const { result } = await renderTransactions({
      playerId: SOLD_OUT_PLAYER_ID,
      side: 'BUY',
      from: '2026-08-24',
      to: '2026-08-31',
    });

    expect(result.current.transactions.map((transaction) => transaction.id)).toEqual([402, 401]);
    expect(requested).toEqual([
      `/api/users/${MARKET_USER_ID}/transactions?playerId=${SOLD_OUT_PLAYER_ID}&side=BUY&from=2026-08-24&to=2026-08-31`,
    ]);
  });

  it('trae la hoja pedida con sus datos de paginación', async () => {
    const { result } = await renderTransactions({ page: 1, size: 3 });

    expect(result.current.transactions.map((transaction) => transaction.id)).toEqual([401]);
    expect(result.current.page).toMatchObject({ page: 1, size: 3, totalPages: 2, hasPrevious: true, hasNext: false });
  });

  it('al cambiar el filtro vuelve a pedir', async () => {
    withSession();
    const requested = trackRequests();
    const { result, rerender } = renderHook((query: TransactionQuery) => useTransactions(query), {
      wrapper,
      initialProps: {},
    });
    await waitFor(() => expect(result.current.status).toBe('success'));

    rerender({ side: 'SELL' });

    await waitFor(() => expect(result.current.transactions.map((transaction) => transaction.id)).toEqual([403]));
    expect(requested).toHaveLength(2);
  });

  it('sin sesión no pide nada', async () => {
    const requested = trackRequests();

    const { result } = renderHook(() => useTransactions(), { wrapper });

    await waitFor(() => expect(result.current.status).toBe('idle'));
    expect(result.current.transactions).toEqual([]);
    expect(requested).toEqual([]);
  });

  it('un rango invertido llega como error con el mensaje del servidor', async () => {
    withSession();

    const { result } = renderHook(() => useTransactions({ from: '2026-09-10', to: '2026-09-01' }), { wrapper });

    await waitFor(() => expect(result.current.status).toBe('error'));
    expect(result.current.error?.message).toBe('El inicio del rango (2026-09-10) es posterior al final (2026-09-01).');
  });

  it('si el servidor falla queda el error, y reintentar lo recupera', async () => {
    withSession();
    server.use(http.get(TRANSACTIONS_PATH, () => HttpResponse.error(), { once: true }));
    const { result } = renderHook(() => useTransactions(), { wrapper });
    await waitFor(() => expect(result.current.status).toBe('error'));
    expect(result.current.error?.message).toBe('no se pudo conectar con la pizarra');

    act(() => result.current.retry());

    await waitFor(() => expect(result.current.status).toBe('success'));
    expect(result.current.transactions).toHaveLength(TRANSACTIONS.length);
  });
});
