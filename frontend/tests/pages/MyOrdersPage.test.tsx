// Historia 6 de la 008: la pantalla Mis órdenes. Las órdenes del usuario de la sesión en cualquier
// estado; cancelar una abierta pide confirmación, y después cambia el saldo libre de la barra.
import { screen, waitFor, within } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { afterEach, describe, expect, it } from 'vitest';
import { formatBalance } from '../../src/utils/format';
import { MARKET_MESSAGES, PENDING_BUY_PLAYER_ID, SEED_RESERVED_BALANCE, playerRef } from '../mocks/market';
import { renderWithRouter } from '../mocks/renderWithRouter';
import { server } from '../mocks/server';

const SEED_BALANCE = 1000;
const ORDERS_PATH = '*/api/users/:id/orders';
const PARTIAL_PLAYER = playerRef(PENDING_BUY_PLAYER_ID).name;
const CANCEL_PARTIAL = `Cancelar la orden de compra de ${PARTIAL_PLAYER}`;

function renderMyOrders(path = '/ordenes') {
  return renderWithRouter({ initialEntries: [path], session: 'valid' });
}

// Los pedidos de cancelar enviados desde que se llama
function trackCancellations(): string[] {
  const posted: string[] = [];
  server.events.on('request:start', ({ request }) => {
    const url = new URL(request.url);
    if (request.method === 'POST' && url.pathname.endsWith('/cancel')) posted.push(url.pathname);
  });
  return posted;
}

// Las filas de órdenes, en el orden de la tabla, sin el encabezado
async function rows(): Promise<HTMLElement[]> {
  const table = await screen.findByRole('table');
  return within(table).getAllByRole('row').slice(1);
}

function cellOf(row: HTMLElement, label: string): string {
  return row.querySelector(`[data-label="${label}"]`)?.textContent ?? '';
}

function balanceInBar(): HTMLElement {
  return screen.getByText(/^Saldo libre:/);
}

afterEach(() => server.events.removeAllListeners());

describe('MyOrdersPage', () => {
  it('muestra las órdenes de la más nueva a la más vieja con su estado y sus cantidades', async () => {
    renderMyOrders();

    expect(await screen.findByRole('heading', { name: 'Mis órdenes' })).toBeInTheDocument();
    const [cancelled, pending, partial, executed, ...older] = await rows();
    expect(older).toHaveLength(3);
    expect(cellOf(cancelled, 'Estado')).toBe('cancelada');
    expect(cellOf(cancelled, 'Cancelada')).toBe('10');
    expect(cellOf(pending, 'Estado')).toBe('pendiente');
    expect(cellOf(pending, 'Tipo')).toBe('venta');
    expect(cellOf(pending, 'Pendiente')).toBe('5');
    expect(cellOf(partial, 'Estado')).toBe('parcial');
    expect(cellOf(partial, 'Pedida')).toBe('10');
    expect(cellOf(partial, 'Ejecutada')).toBe('4');
    expect(cellOf(partial, 'Pendiente')).toBe('6');
    expect(cellOf(partial, 'Reserva')).toBe('6,18 cr.');
    expect(cellOf(executed, 'Estado')).toBe('ejecutada');
    // Solo las abiertas se pueden cancelar
    expect(screen.getAllByRole('button', { name: /^Cancelar la orden/ })).toHaveLength(2);
    expect(within(cancelled).queryByRole('button')).toBeNull();
    expect(within(executed).queryByRole('button')).toBeNull();
  });

  it('cancelar pide confirmación y nada se envía antes de confirmar', async () => {
    const posted = trackCancellations();
    const { user } = renderMyOrders();
    await rows();

    await user.click(screen.getByRole('button', { name: CANCEL_PARTIAL }));

    const dialog = screen.getByRole('dialog', { name: `¿Cancelar la compra de ${PARTIAL_PLAYER}?` });
    expect(dialog).toHaveTextContent('Se cancelan 6 tokens pendientes');
    expect(dialog).toHaveTextContent('Se liberan 6,18 cr. reservados.');
    expect(posted).toEqual([]);

    await user.click(within(dialog).getByRole('button', { name: 'Volver' }));

    expect(screen.queryByRole('dialog')).toBeNull();
    expect(posted).toEqual([]);
    expect(cellOf((await rows())[2], 'Estado')).toBe('parcial');
  });

  it('después de cancelar la orden queda cancelada y cambia el saldo libre de la barra', async () => {
    const posted = trackCancellations();
    const { user } = renderMyOrders();
    await rows();
    await waitFor(() => expect(balanceInBar()).toHaveTextContent(formatBalance(SEED_BALANCE - SEED_RESERVED_BALANCE)));

    await user.click(screen.getByRole('button', { name: CANCEL_PARTIAL }));
    await user.click(screen.getByRole('button', { name: 'Confirmar cancelación' }));

    expect(await screen.findByText(/^Cancelaste la compra de/)).toHaveTextContent('se cancelaron 6 tokens');
    expect(posted).toHaveLength(1);
    await waitFor(async () => expect(cellOf((await rows())[2], 'Estado')).toBe('cancelada'));
    await waitFor(() => expect(balanceInBar()).toHaveTextContent(formatBalance(SEED_BALANCE)));
    expect(screen.getAllByRole('button', { name: /^Cancelar la orden/ })).toHaveLength(1);
  });

  it('un rechazo del servidor se muestra en español y la orden sigue como estaba', async () => {
    server.use(
      http.post(
        '*/api/orders/:id/cancel',
        ({ params }) =>
          HttpResponse.json(
            {
              timestamp: '2026-10-30T12:00:00Z',
              status: 409,
              error: 'Conflict',
              message: MARKET_MESSAGES.executed,
              path: `/orders/${String(params.id)}/cancel`,
            },
            { status: 409 },
          ),
        { once: true },
      ),
    );
    const { user } = renderMyOrders();
    await rows();

    await user.click(screen.getByRole('button', { name: CANCEL_PARTIAL }));
    await user.click(screen.getByRole('button', { name: 'Confirmar cancelación' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('La orden ya está ejecutada.');
    expect(screen.queryByRole('dialog')).toBeNull();
    expect(cellOf((await rows())[2], 'Estado')).toBe('parcial');
    expect(balanceInBar()).toHaveTextContent(formatBalance(SEED_BALANCE - SEED_RESERVED_BALANCE));
  });

  it('la hoja va en la dirección', async () => {
    const { router } = renderMyOrders('/ordenes?page=2');

    expect(await screen.findByText('esta hoja de órdenes está vacía')).toBeInTheDocument();
    expect(router.state.location.search).toBe('?page=2');
  });

  it('si el servidor falla avisa, y reintentar trae las órdenes', async () => {
    server.use(http.get(ORDERS_PATH, () => HttpResponse.error(), { once: true }));
    const { user } = renderMyOrders();

    expect(await screen.findByText('no se pudieron cargar tus órdenes')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'reintentar' }));

    expect(await rows()).toHaveLength(7);
  });
});
