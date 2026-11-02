// Historia 5 de la 008: la pantalla "Mercado" (FR-047 a FR-049). Pública, con los filtros y la hoja
// en la dirección, y nunca muestra quién es el dueño de una orden.
import { screen, waitFor, within } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import type { MarketOrderPage } from '../../src/types/market';
import {
  FRESH_INITIAL_PLAYER_ID,
  INITIAL_ORDER_PLAYER_ID,
  PENDING_BUY_PLAYER_ID,
  playerRef,
  seedOrders,
  SOLD_OUT_PLAYER_ID,
  toMarketOrder,
} from '../mocks/market';
import { renderWithRouter } from '../mocks/renderWithRouter';
import { server } from '../mocks/server';

const ORDERS_PATH = '*/api/orders';

function nameOf(playerId: number): string {
  return playerRef(playerId).name;
}

// Los jugadores de las filas de la tabla, en orden
function listedPlayers(): string[] {
  return within(screen.getByRole('table'))
    .getAllByRole('rowheader')
    .map((cell) => cell.textContent ?? '');
}

function group(name: string): HTMLElement {
  return screen.getByRole('group', { name });
}

const ALL_OPEN = [
  nameOf(INITIAL_ORDER_PLAYER_ID),
  nameOf(FRESH_INITIAL_PLAYER_ID),
  nameOf(SOLD_OUT_PLAYER_ID),
  nameOf(PENDING_BUY_PLAYER_ID),
  nameOf(SOLD_OUT_PLAYER_ID),
];

describe('MarketPage', () => {
  it('sin sesión muestra las órdenes pendientes de la más vieja a la más nueva', async () => {
    renderWithRouter({ initialEntries: ['/mercado'] });

    expect(await screen.findByText('hoja 1 de 1')).toBeInTheDocument();

    expect(screen.getByRole('heading', { name: 'Mercado' })).toBeInTheDocument();
    expect(listedPlayers()).toEqual(ALL_OPEN);
    expect(within(screen.getAllByRole('row')[1]).getAllByRole('cell').map((cell) => cell.textContent)).toEqual([
      'venta',
      'administrador',
      '100',
      '13',
      '87',
      'parcial',
      '28/10/2026, 09:00',
    ]);
  });

  it('nunca muestra quién es el dueño de una orden, aunque haya sesión', async () => {
    const { container } = renderWithRouter({ initialEntries: ['/mercado'], session: 'valid' });

    await screen.findByText('hoja 1 de 1');

    expect(within(screen.getByRole('table')).queryByText(/lionel10|lionel@mail\.com/)).toBeNull();
    expect(container).not.toHaveTextContent('lionel@mail.com');
  });

  it('elegir un filtro lo deja en la dirección, vuelve a la primera hoja y acota la lista', async () => {
    const { user, router } = renderWithRouter({ initialEntries: ['/mercado?page=2'] });
    await screen.findByText('esta hoja del mercado está vacía');

    await user.click(within(group('filtrar por tipo de orden')).getByRole('button', { name: 'ventas' }));

    await waitFor(() => expect(router.state.location.search).toBe('?side=SELL'));
    await waitFor(() =>
      expect(listedPlayers()).toEqual([
        nameOf(INITIAL_ORDER_PLAYER_ID),
        nameOf(FRESH_INITIAL_PLAYER_ID),
        nameOf(SOLD_OUT_PLAYER_ID),
        nameOf(SOLD_OUT_PLAYER_ID),
      ]),
    );
  });

  it('abrir una dirección con filtros reproduce la vista', async () => {
    renderWithRouter({ initialEntries: ['/mercado?side=BUY&ownerType=USER'] });

    await waitFor(() => expect(listedPlayers()).toEqual([nameOf(PENDING_BUY_PLAYER_ID)]));

    expect(within(group('filtrar por tipo de orden')).getByRole('button', { name: 'compras' })).toHaveAttribute(
      'aria-pressed',
      'true',
    );
    expect(
      within(group('filtrar por tipo de usuario dueño')).getByRole('button', { name: 'de usuarios' }),
    ).toHaveAttribute('aria-pressed', 'true');
  });

  it('la hoja siguiente queda en la dirección y trae sus órdenes', async () => {
    const [first, second] = seedOrders().map(({ order }) => toMarketOrder(order));
    server.use(
      http.get(ORDERS_PATH, ({ request }) => {
        const page = Number(new URL(request.url).searchParams.get('page'));
        const body: MarketOrderPage = {
          content: [page === 0 ? first : second],
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
    const { user, router } = renderWithRouter({ initialEntries: ['/mercado'] });
    await screen.findByText('hoja 1 de 2');

    await user.click(screen.getByRole('button', { name: 'hoja siguiente' }));

    await waitFor(() => expect(router.state.location.search).toBe('?page=2'));
    expect(await screen.findByText('hoja 2 de 2')).toBeInTheDocument();
    expect(listedPlayers()).toEqual([second.player.name]);
  });

  it('muestra la carga y, si el servidor falla, el error con reintentar', async () => {
    server.use(http.get(ORDERS_PATH, () => HttpResponse.error(), { once: true }));
    const { user } = renderWithRouter({ initialEntries: ['/mercado'] });
    expect(screen.getByText('el DT está pensando...')).toBeInTheDocument();
    expect(await screen.findByRole('alert')).toHaveTextContent('no se pudo cargar el mercado');

    await user.click(screen.getByRole('button', { name: 'reintentar' }));

    await waitFor(() => expect(listedPlayers()).toEqual(ALL_OPEN));
  });

  it('sin órdenes con esos filtros lo dice y ofrece limpiar los filtros', async () => {
    const { user, router } = renderWithRouter({ initialEntries: ['/mercado?side=BUY&league=PREMIER'] });
    await screen.findByText('no hay órdenes pendientes con esos filtros');
    expect(screen.queryByRole('table')).toBeNull();

    await user.click(screen.getByRole('button', { name: 'limpiar filtros' }));

    await waitFor(() => expect(router.state.location.search).toBe(''));
    await waitFor(() => expect(listedPlayers()).toEqual(ALL_OPEN));
  });

  it('el enlace "Mercado" de la barra está también sin sesión', async () => {
    const { user, router } = renderWithRouter({ initialEntries: ['/pizarra'] });

    await user.click(await screen.findByRole('link', { name: 'Mercado' }));

    await waitFor(() => expect(router.state.location.pathname).toBe('/mercado'));
    expect(await screen.findByRole('heading', { name: 'Mercado' })).toBeInTheDocument();
  });
});
