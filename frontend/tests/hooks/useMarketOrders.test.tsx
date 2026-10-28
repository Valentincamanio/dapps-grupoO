// Historia 5 de la 008: el listado público del mercado y las pendientes de un jugador para la ficha.
import { act, renderHook, waitFor } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { useMarketOrders } from '../../src/hooks/useMarketOrders';
import type { MarketOrderQuery } from '../../src/types/market';
import {
  FRESH_INITIAL_PLAYER_ID,
  INITIAL_ORDER_ID,
  PARTIAL_ORDER_ID,
  PENDING_ORDER_ID,
  SOLD_OUT_PLAYER_ID,
} from '../mocks/market';
import { server } from '../mocks/server';

const ORDERS_PATH = '*/api/orders';
// La orden inicial intacta del jugador 3 y la venta de otro usuario del jugador 4
const FRESH_INITIAL_ORDER_ID = 103;
const OTHER_USER_SALE_ID = 201;

async function renderOrders(query: MarketOrderQuery) {
  const rendered = renderHook(() => useMarketOrders(query));
  await waitFor(() => expect(rendered.result.current.status).toBe('success'));
  return rendered;
}

describe('useMarketOrders', () => {
  it('trae sin sesión las órdenes abiertas de la más vieja a la más nueva', async () => {
    const { result } = await renderOrders({});

    expect(result.current.orders.map((order) => order.id)).toEqual([
      INITIAL_ORDER_ID,
      FRESH_INITIAL_ORDER_ID,
      OTHER_USER_SALE_ID,
      PARTIAL_ORDER_ID,
      PENDING_ORDER_ID,
    ]);
  });

  it('con un jugador trae solo sus órdenes pendientes, para la ficha', async () => {
    const { result } = await renderOrders({ playerId: SOLD_OUT_PLAYER_ID });

    expect(result.current.orders.map((order) => order.id)).toEqual([OTHER_USER_SALE_ID, PENDING_ORDER_ID]);
    expect(result.current.orders.every((order) => order.player.id === SOLD_OUT_PLAYER_ID)).toBe(true);
  });

  it('con el tipo de usuario dueño ADMIN trae las órdenes iniciales', async () => {
    const { result } = await renderOrders({ ownerType: 'ADMIN' });

    expect(result.current.orders.map((order) => order.player.id)).toEqual([1, FRESH_INITIAL_PLAYER_ID]);
  });

  it('con el tipo de orden trae solo ese lado', async () => {
    const { result } = await renderOrders({ side: 'BUY' });

    expect(result.current.orders.map((order) => order.id)).toEqual([PARTIAL_ORDER_ID]);
  });

  it('trae la hoja pedida con sus datos de paginación', async () => {
    const { result } = await renderOrders({ page: 1, size: 2 });

    expect(result.current.orders.map((order) => order.id)).toEqual([OTHER_USER_SALE_ID, PARTIAL_ORDER_ID]);
    expect(result.current.page).toMatchObject({ page: 1, size: 2, totalPages: 3, hasPrevious: true, hasNext: true });
  });

  it('cambiar los filtros vuelve a pedir', async () => {
    const { result, rerender } = renderHook((query: MarketOrderQuery) => useMarketOrders(query), {
      initialProps: {},
    });
    await waitFor(() => expect(result.current.orders).toHaveLength(5));

    rerender({ side: 'SELL', ownerType: 'USER' });

    await waitFor(() =>
      expect(result.current.orders.map((order) => order.id)).toEqual([OTHER_USER_SALE_ID, PENDING_ORDER_ID]),
    );
  });

  it('si el servidor falla queda el error, y volver a pedir lo recupera', async () => {
    server.use(http.get(ORDERS_PATH, () => HttpResponse.error(), { once: true }));
    const { result } = renderHook(() => useMarketOrders({}));
    await waitFor(() => expect(result.current.status).toBe('error'));
    expect(result.current.error?.message).toBe('no se pudo conectar con la pizarra');
    expect(result.current.orders).toEqual([]);

    act(() => result.current.reload());

    await waitFor(() => expect(result.current.status).toBe('success'));
    expect(result.current.orders).toHaveLength(5);
  });
});
