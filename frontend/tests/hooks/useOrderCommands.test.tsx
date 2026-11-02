// Historias 1, 3 y 6 de la 008: comprar, vender y cancelar. Después de cada operación que sale bien
// el perfil se vuelve a pedir, así la barra muestra el saldo libre nuevo; un rechazo deja el mensaje
// del backend y no se da por hecha.
import { act, renderHook, waitFor } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import type { ReactNode } from 'react';
import { afterEach, describe, expect, it } from 'vitest';
import { SessionProvider } from '../../src/context/SessionContext';
import { useOrderCommands } from '../../src/hooks/useOrderCommands';
import { useSession } from '../../src/hooks/useSession';
import { writeSession } from '../../src/services/sessionStorage';
import { tradeTotal } from '../../src/utils/market';
import { SEED_TOKEN } from '../mocks/handlers';
import {
  currentQuoteUsed,
  INITIAL_ORDER_ID,
  INITIAL_ORDER_PLAYER_ID,
  MARKET_MESSAGES,
  PARTIAL_ORDER_ID,
  saleBlockedMessage,
  SEED_RESERVED_BALANCE,
  SOLD_OUT_PLAYER_ID,
} from '../mocks/market';
import { server } from '../mocks/server';

const PROFILE_PATH = '*/api/auth/me';
// El usuario sembrado: 1000,00 de total con 6,18 reservados
const SEED_AVAILABLE = 993.82;

function wrapper({ children }: { children: ReactNode }) {
  return <SessionProvider>{children}</SessionProvider>;
}

async function renderCommands() {
  writeSession({ token: SEED_TOKEN, expiresAt: new Date(Date.now() + 60 * 60 * 1000).toISOString() });
  const rendered = renderHook(() => ({ commands: useOrderCommands(), session: useSession() }), { wrapper });
  await waitFor(() => expect(rendered.result.current.session.profile).not.toBeNull());
  return rendered;
}

// Cuántas veces se pide el perfil desde que se llama
function countProfileRequests(): () => number {
  let count = 0;
  server.events.on('request:start', ({ request }) => {
    if (new URL(request.url).pathname === '/api/auth/me') count++;
  });
  return () => count;
}

afterEach(() => server.events.removeAllListeners());

describe('useOrderCommands', () => {
  it('comprar a la orden inicial ejecuta en el acto y actualiza el saldo libre del perfil', async () => {
    const { result } = await renderCommands();
    expect(result.current.session.profile?.availableBalance).toBe(SEED_AVAILABLE);

    const execution = await act(() => result.current.commands.buy({ playerId: INITIAL_ORDER_PLAYER_ID, quantity: 10 }));

    expect(execution?.order.status).toBe('EXECUTED');
    expect(execution?.executedQuantity).toBe(10);
    expect(execution?.executedTotal).toBe(10);
    expect(execution?.trades).toHaveLength(1);
    expect(execution?.trades[0]).toMatchObject({ quantity: 10, tokenPrice: 1, total: 10, counterparty: 'ADMIN' });
    expect(result.current.commands.trade.status).toBe('success');
    expect(result.current.commands.trade.data).toEqual(execution);
    expect(result.current.session.profile?.availableBalance).toBe(983.82);
  });

  it('lo que el superusuario no cubre queda pendiente y reservado al precio vigente', async () => {
    const { result } = await renderCommands();
    const tokenPrice = currentQuoteUsed(INITIAL_ORDER_PLAYER_ID)?.tokenPrice ?? 0;

    const execution = await act(() => result.current.commands.buy({ playerId: INITIAL_ORDER_PLAYER_ID, quantity: 90 }));

    expect(execution?.order).toMatchObject({ status: 'PARTIAL', executedQuantity: 87, pendingQuantity: 3 });
    expect(execution?.order.reservedCredits).toBe(tradeTotal(3, tokenPrice));
    expect(result.current.session.profile?.reservedBalance).toBe(
      Math.round((SEED_RESERVED_BALANCE + tradeTotal(3, tokenPrice)) * 100) / 100,
    );
  });

  it('vender deja la venta pendiente y sus tokens comprometidos', async () => {
    const { result } = await renderCommands();

    const execution = await act(() => result.current.commands.sell({ playerId: SOLD_OUT_PLAYER_ID, quantity: 5 }));

    expect(execution?.order).toMatchObject({ side: 'SELL', status: 'PENDING', pendingQuantity: 5 });
    expect(execution?.trades).toEqual([]);
  });

  it('un rechazo deja el mensaje del backend, no da la operación por hecha ni pide el perfil', async () => {
    const { result } = await renderCommands();
    const profileRequests = countProfileRequests();

    const execution = await act(() => result.current.commands.sell({ playerId: INITIAL_ORDER_PLAYER_ID, quantity: 5 }));

    expect(execution).toBeUndefined();
    expect(result.current.commands.trade.status).toBe('error');
    expect(result.current.commands.trade.data).toBeUndefined();
    expect(result.current.commands.trade.error?.message).toBe(saleBlockedMessage(87));
    expect(profileRequests()).toBe(0);
    expect(result.current.session.profile?.availableBalance).toBe(SEED_AVAILABLE);
  });

  it('cancelar una compra pendiente libera su reserva y actualiza el saldo libre', async () => {
    const { result } = await renderCommands();

    const cancellation = await act(() => result.current.commands.cancel(PARTIAL_ORDER_ID));

    expect(cancellation?.order).toMatchObject({ status: 'CANCELLED', pendingQuantity: 0, cancelledQuantity: 6 });
    expect(cancellation?.reservedBalance).toBe(0);
    expect(result.current.commands.cancellation.status).toBe('success');
    expect(result.current.session.profile).toMatchObject({ availableBalance: 1000, reservedBalance: 0 });
  });

  it('cancelar una orden ajena deja el mensaje del backend', async () => {
    const { result } = await renderCommands();

    const cancellation = await act(() => result.current.commands.cancel(INITIAL_ORDER_ID));

    expect(cancellation).toBeUndefined();
    expect(result.current.commands.cancellation.error?.message).toBe(MARKET_MESSAGES.notOwned);
  });

  it('si el perfil no se puede pedir después de comprar, la compra igual se informa', async () => {
    const { result } = await renderCommands();
    server.use(http.get(PROFILE_PATH, () => HttpResponse.error()));

    const execution = await act(() => result.current.commands.buy({ playerId: INITIAL_ORDER_PLAYER_ID, quantity: 1 }));

    expect(execution?.order.status).toBe('EXECUTED');
    expect(result.current.commands.trade.status).toBe('success');
    expect(result.current.session.profile?.availableBalance).toBe(SEED_AVAILABLE);
  });
});
