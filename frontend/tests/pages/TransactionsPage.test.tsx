// Historia 6 de la 008: la pantalla Operaciones con el historial del usuario de la sesión. Los filtros y
// la hoja viven en la dirección; la contraparte se dice por su tipo, nunca quién es.
import { screen, waitFor, within } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { afterEach, describe, expect, it } from 'vitest';
import type { TransactionPage } from '../../src/types/market';
import { SOLD_OUT_PLAYER_ID, TRANSACTIONS } from '../mocks/market';
import { renderWithRouter } from '../mocks/renderWithRouter';
import { server } from '../mocks/server';

const TRANSACTIONS_PATH = '*/api/users/:id/transactions';
const LIST_NAME = 'Tus operaciones, de la más nueva a la más vieja';
const [NEWEST, SALE] = TRANSACTIONS;

function renderTransactions(path = '/operaciones') {
  return renderWithRouter({ initialEntries: [path], session: 'valid' });
}

// Las búsquedas del historial pedidas desde que se llama
function trackRequests(): string[] {
  const requested: string[] = [];
  server.events.on('request:start', ({ request }) => {
    const url = new URL(request.url);
    if (url.pathname.endsWith('/transactions')) requested.push(url.search);
  });
  return requested;
}

async function cards(): Promise<HTMLElement[]> {
  const list = await screen.findByRole('list', { name: LIST_NAME });
  return within(list).getAllByRole('listitem');
}

// El valor que acompaña a una etiqueta de una tarjeta
function fieldOf(card: HTMLElement, label: string): string {
  const value = within(card).getByText(label, { selector: 'dt' }).nextElementSibling;
  return value?.textContent ?? '';
}

afterEach(() => server.events.removeAllListeners());

describe('TransactionsPage', () => {
  it('muestra el historial de la más nueva a la más vieja con cada dato de la operación', async () => {
    renderTransactions();

    expect(await screen.findByRole('heading', { name: 'Operaciones' })).toBeInTheDocument();
    const [newest, sale, ...rest] = await cards();
    expect(rest).toHaveLength(TRANSACTIONS.length - 2);
    expect(newest).toHaveTextContent(NEWEST.player.name);
    expect(newest).toHaveTextContent('compra');
    expect(fieldOf(newest, 'Fecha')).toBe('28/10/2026, 10:00');
    expect(fieldOf(newest, 'Cantidad')).toBe('10');
    expect(fieldOf(newest, 'Precio')).toBe('1,0000 cr.');
    expect(fieldOf(newest, 'Total')).toBe('10,00 cr.');
    expect(fieldOf(newest, 'Orden')).toBe(`#${NEWEST.orderId}`);
    expect(fieldOf(newest, 'Cotización')).toContain('103,00 cr.');
    expect(fieldOf(newest, 'Cotización')).toContain('score 0,30');
    expect(fieldOf(newest, 'Cotización')).toContain(`${NEWEST.quote.strategy.name} v${NEWEST.quote.strategy.version}`);
    // La ganancia realizada va solo en las ventas
    expect(within(newest).queryByText('Ganancia realizada')).toBeNull();
    expect(sale).toHaveTextContent('venta');
    expect(fieldOf(sale, 'Ganancia realizada')).toContain('+0,20');
    expect(fieldOf(sale, 'Ganancia realizada')).toContain('gana');
    expect(fieldOf(sale, 'Orden')).toBe(`#${SALE.orderId}`);
  });

  it('la contraparte es "administrador" u "otro usuario", sin decir quién', async () => {
    renderTransactions();

    const counterparts = (await cards()).map((card) => fieldOf(card, 'Contraparte'));

    expect(counterparts).toEqual(['administrador', 'otro usuario', 'otro usuario', 'administrador']);
  });

  it('los filtros de la dirección llegan al pedido y cambiar uno lo escribe en la dirección', async () => {
    const requested = trackRequests();
    const { user, router } = renderTransactions(
      `/operaciones?player=${SOLD_OUT_PLAYER_ID}&side=BUY&from=2026-08-24&to=2026-09-01`,
    );

    expect((await cards()).map((card) => fieldOf(card, 'Orden'))).toEqual(['#306', '#307']);
    expect(requested[0]).toBe(
      `?playerId=${SOLD_OUT_PLAYER_ID}&side=BUY&from=2026-08-24&to=2026-09-01&page=0&size=10`,
    );

    await user.click(screen.getByRole('button', { name: 'ventas' }));

    await waitFor(() => expect(router.state.location.search).toContain('side=SELL'));
    expect(router.state.location.search).toContain(`player=${SOLD_OUT_PLAYER_ID}`);
    await waitFor(async () => expect(await cards()).toHaveLength(1));
    expect(fieldOf((await cards())[0], 'Orden')).toBe(`#${SALE.orderId}`);
  });

  it('la hoja va en la dirección, desde 1, y hacia el servidor desde 0', async () => {
    server.use(
      http.get(TRANSACTIONS_PATH, ({ request }) => {
        const page = Number(new URL(request.url).searchParams.get('page'));
        const body: TransactionPage = {
          content: [TRANSACTIONS[page]],
          page,
          size: 1,
          totalElements: 2,
          totalPages: 2,
          hasPrevious: page > 0,
          hasNext: page === 0,
        };
        return HttpResponse.json(body);
      }),
    );
    const requested = trackRequests();
    const { user, router } = renderTransactions();
    await cards();
    expect(screen.getByText('hoja 1 de 2')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'hoja siguiente' }));

    await waitFor(() => expect(router.state.location.search).toBe('?page=2'));
    expect(await screen.findByText('hoja 2 de 2')).toBeInTheDocument();
    expect(fieldOf((await cards())[0], 'Orden')).toBe(`#${SALE.orderId}`);
    expect(requested.at(-1)).toBe('?page=1&size=10');
  });

  it('un rango invertido se rechaza con un mensaje y no se pide', async () => {
    const { user, router } = renderTransactions();
    await cards();

    await user.type(screen.getByLabelText('Desde'), '2026-09-10');
    await user.type(screen.getByLabelText('Hasta'), '2026-09-01');

    expect(await screen.findByText('La fecha "desde" no puede ser posterior a la fecha "hasta".')).toBeInTheDocument();
    expect(router.state.location.search).toBe('?from=2026-09-10');
  });

  it('un rango invertido escrito en la dirección muestra el mensaje del servidor', async () => {
    const { user, router } = renderTransactions('/operaciones?from=2026-09-10&to=2026-09-01');

    expect(
      await screen.findByText('El inicio del rango (2026-09-10) es posterior al final (2026-09-01).'),
    ).toBeInTheDocument();

    await user.click(screen.getAllByRole('button', { name: 'limpiar filtros' })[0]);

    await waitFor(() => expect(router.state.location.search).toBe(''));
    expect(await cards()).toHaveLength(TRANSACTIONS.length);
  });

  it('mientras el servidor no responde está cargando', async () => {
    let respond: () => void = () => {};
    const answered = new Promise<void>((resolve) => {
      respond = resolve;
    });
    server.use(
      http.get(TRANSACTIONS_PATH, async () => {
        await answered;
        return HttpResponse.json({
          content: [],
          page: 0,
          size: 10,
          totalElements: 0,
          totalPages: 0,
          hasPrevious: false,
          hasNext: false,
        });
      }),
    );
    const requested = trackRequests();
    renderTransactions();
    await waitFor(() => expect(requested).toHaveLength(1));

    expect(await screen.findByRole('heading', { name: 'Operaciones' })).toBeInTheDocument();
    expect(screen.getByText('el DT está pensando...')).toBeInTheDocument();
    expect(screen.queryByRole('list', { name: LIST_NAME })).toBeNull();

    respond();
    expect(await screen.findByText('todavía no tenés operaciones')).toBeInTheDocument();
  });

  it('si el servidor falla avisa, y reintentar trae el historial', async () => {
    server.use(http.get(TRANSACTIONS_PATH, () => HttpResponse.error(), { once: true }));
    const { user } = renderTransactions();

    expect(await screen.findByText('no se pudo cargar el historial')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'reintentar' }));

    expect(await cards()).toHaveLength(TRANSACTIONS.length);
  });

  it('con filtros que no dejan nada lo dice y ofrece limpiarlos', async () => {
    const { user, router } = renderTransactions('/operaciones?from=2026-10-29');

    expect(await screen.findByText('no hay operaciones con esos filtros')).toBeInTheDocument();

    await user.click(screen.getAllByRole('button', { name: 'limpiar filtros' })[0]);

    await waitFor(() => expect(router.state.location.search).toBe(''));
    expect(await cards()).toHaveLength(TRANSACTIONS.length);
  });
});
