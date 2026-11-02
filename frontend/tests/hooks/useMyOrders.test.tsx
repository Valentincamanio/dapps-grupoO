// Historia 6 de la 008: las órdenes propias del usuario de la sesión, con hoja y recarga.
import { act, renderHook, waitFor } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import type { ReactNode } from 'react';
import { afterEach, describe, expect, it } from 'vitest';
import { SessionProvider } from '../../src/context/SessionContext';
import { useMyOrders } from '../../src/hooks/useMyOrders';
import { cancelOrder } from '../../src/services/marketService';
import { writeSession } from '../../src/services/sessionStorage';
import type { PageQuery } from '../../src/types/market';
import { SEED_TOKEN } from '../mocks/handlers';
import {
  CANCELLED_ORDER_ID,
  EXECUTED_ORDER_ID,
  MARKET_USER_ID,
  PARTIAL_ORDER_ID,
  PENDING_ORDER_ID,
} from '../mocks/market';
import { server } from '../mocks/server';

const ORDERS_PATH = '*/api/users/:id/orders';
// Las órdenes viejas del usuario sembrado, de la más nueva a la más vieja
const OLDER_ORDER_IDS = [305, 306, 307];

function wrapper({ children }: { children: ReactNode }) {
  return <SessionProvider>{children}</SessionProvider>;
}

function withSession() {
  writeSession({ token: SEED_TOKEN, expiresAt: new Date(Date.now() + 60 * 60 * 1000).toISOString() });
}

// Las direcciones de las órdenes propias pedidas desde que se llama
function trackRequests(): string[] {
  const requested: string[] = [];
  server.events.on('request:start', ({ request }) => {
    const url = new URL(request.url);
    if (url.pathname.endsWith(`/users/${MARKET_USER_ID}/orders`)) requested.push(url.pathname + url.search);
  });
  return requested;
}

async function renderMyOrders(query: PageQuery = {}) {
  withSession();
  const rendered = renderHook(() => useMyOrders(query), { wrapper });
  await waitFor(() => expect(rendered.result.current.status).toBe('success'));
  return rendered;
}

afterEach(() => server.events.removeAllListeners());

describe('useMyOrders', () => {
  it('trae las órdenes de la cuenta de la sesión en cualquier estado, de la más nueva a la más vieja', async () => {
    const requested = trackRequests();

    const { result } = await renderMyOrders();

    expect(result.current.orders.map((order) => order.id)).toEqual([
      CANCELLED_ORDER_ID,
      PENDING_ORDER_ID,
      PARTIAL_ORDER_ID,
      EXECUTED_ORDER_ID,
      ...OLDER_ORDER_IDS,
    ]);
    expect(result.current.orders.map((order) => order.status)).toEqual([
      'CANCELLED',
      'PENDING',
      'PARTIAL',
      'EXECUTED',
      'EXECUTED',
      'EXECUTED',
      'EXECUTED',
    ]);
    expect(requested).toEqual([`/api/users/${MARKET_USER_ID}/orders`]);
  });

  it('trae la hoja pedida con sus datos de paginación', async () => {
    const requested = trackRequests();

    const { result } = await renderMyOrders({ page: 1, size: 5 });

    expect(result.current.orders.map((order) => order.id)).toEqual(OLDER_ORDER_IDS.slice(1));
    expect(result.current.page).toMatchObject({ page: 1, size: 5, totalElements: 7, hasPrevious: true });
    expect(requested).toEqual([`/api/users/${MARKET_USER_ID}/orders?page=1&size=5`]);
  });

  it('recargar después de cancelar trae la orden cancelada', async () => {
    const { result } = await renderMyOrders();
    await cancelOrder(PENDING_ORDER_ID);

    act(() => result.current.reload());

    await waitFor(() =>
      expect(result.current.orders.find((order) => order.id === PENDING_ORDER_ID)?.status).toBe('CANCELLED'),
    );
  });

  it('sin sesión no pide nada', async () => {
    const requested = trackRequests();

    const { result } = renderHook(() => useMyOrders(), { wrapper });

    await waitFor(() => expect(result.current.status).toBe('idle'));
    expect(result.current.orders).toEqual([]);
    expect(requested).toEqual([]);
  });

  it('si el servidor falla queda el error, y recargar lo recupera', async () => {
    withSession();
    server.use(http.get(ORDERS_PATH, () => HttpResponse.error(), { once: true }));
    const { result } = renderHook(() => useMyOrders(), { wrapper });
    await waitFor(() => expect(result.current.status).toBe('error'));
    expect(result.current.error?.message).toBe('no se pudo conectar con la pizarra');

    act(() => result.current.reload());

    await waitFor(() => expect(result.current.status).toBe('success'));
    expect(result.current.orders).toHaveLength(7);
  });
});
