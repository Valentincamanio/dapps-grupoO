// Historias 5 y 6 de la 008: la lista de órdenes del mercado y de las órdenes propias. Nunca dice
// quién es el dueño (FR-048); las propias se cancelan solo si están abiertas.
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { OrderList } from '../../src/components/OrderList';
import type { MarketOrder, UserOrder } from '../../src/types/market';
import {
  CANCELLED_ORDER_ID,
  EXECUTED_ORDER_ID,
  INITIAL_ORDER_ID,
  MARKET_USER_ID,
  PARTIAL_ORDER_ID,
  PENDING_ORDER_ID,
  seedOrders,
  toMarketOrder,
} from '../mocks/market';

function orderById(id: number): UserOrder {
  const found = seedOrders().find(({ order }) => order.id === id);
  if (!found) throw new Error(`No hay una orden de prueba con id ${id}`);
  return found.order;
}

function marketOrders(): MarketOrder[] {
  return seedOrders()
    .map(({ order }) => order)
    .filter((order) => order.status === 'PENDING' || order.status === 'PARTIAL')
    .map(toMarketOrder);
}

function ownOrders(): UserOrder[] {
  return seedOrders()
    .filter(({ ownerId }) => ownerId === MARKET_USER_ID)
    .map(({ order }) => order)
    .filter((order) => [EXECUTED_ORDER_ID, PARTIAL_ORDER_ID, PENDING_ORDER_ID, CANCELLED_ORDER_ID].includes(order.id));
}

function rowOf(playerName: string, status: string): HTMLElement {
  const row = screen
    .getAllByRole('row')
    .find((candidate) => candidate.textContent?.includes(playerName) && candidate.textContent.includes(status));
  if (!row) throw new Error(`No hay una fila de ${playerName} ${status}`);
  return row;
}

describe('OrderList', () => {
  it('muestra jugador, tipo, tipo de dueño, cantidades, estado y fecha de cada orden del mercado', () => {
    const initial = toMarketOrder(orderById(INITIAL_ORDER_ID));

    render(<OrderList variant="market" orders={[initial]} />);

    const row = screen.getByRole('row', { name: /Kylian Mbappé/ });
    const cells = within(row).getAllByRole('cell').map((cell) => cell.textContent);
    expect(cells).toEqual(['venta', 'administrador', '100', '13', '87', 'parcial', '28/10/2026, 09:00']);
  });

  it('nunca dice quién es el dueño ni ofrece cancelar en el listado del mercado', () => {
    render(<OrderList variant="market" orders={marketOrders()} />);

    expect(screen.getAllByRole('row')).toHaveLength(marketOrders().length + 1);
    expect(screen.queryByText(/lionel10/)).toBeNull();
    expect(screen.queryByRole('columnheader', { name: 'Reserva' })).toBeNull();
    expect(screen.queryByRole('button')).toBeNull();
  });

  it('las órdenes propias suman lo cancelado y la reserva', () => {
    render(<OrderList variant="own" orders={ownOrders()} />);

    const partial = rowOf(orderById(PARTIAL_ORDER_ID).player.name, 'parcial');
    expect(within(partial).getByText('6,18 cr.')).toBeInTheDocument();
    const cancelled = rowOf(orderById(CANCELLED_ORDER_ID).player.name, 'cancelada');
    expect(within(cancelled).getAllByRole('cell')[5]).toHaveTextContent('10');
  });

  it('ofrece cancelar solo las órdenes abiertas', () => {
    render(<OrderList variant="own" orders={ownOrders()} onCancel={vi.fn()} />);

    const buttons = screen.getAllByRole('button', { name: /^Cancelar la orden/ });

    expect(buttons.map((button) => button.getAttribute('aria-label'))).toEqual([
      `Cancelar la orden de compra de ${orderById(PARTIAL_ORDER_ID).player.name}`,
      `Cancelar la orden de venta de ${orderById(PENDING_ORDER_ID).player.name}`,
    ]);
  });

  it('cancelar avisa qué orden y no hace nada más', async () => {
    const user = userEvent.setup();
    const onCancel = vi.fn();
    const partial = orderById(PARTIAL_ORDER_ID);
    render(<OrderList variant="own" orders={[partial]} onCancel={onCancel} />);

    await user.click(screen.getByRole('button', { name: /^Cancelar la orden de compra/ }));

    expect(onCancel).toHaveBeenCalledExactlyOnceWith(partial);
  });

  it('mientras se cancela una orden su botón no se puede volver a usar', () => {
    const partial = orderById(PARTIAL_ORDER_ID);

    render(<OrderList variant="own" orders={[partial]} onCancel={vi.fn()} cancellingId={partial.id} />);

    expect(screen.getByRole('button', { name: /^Cancelar la orden/ })).toBeDisabled();
  });

  it('sin órdenes muestra un mensaje en lugar de la tabla', () => {
    render(<OrderList variant="market" orders={[]} />);

    expect(screen.getByText('no hay órdenes pendientes.')).toBeInTheDocument();
    expect(screen.queryByRole('table')).toBeNull();
  });

  it('en tarjetas muestra los mismos datos sin tabla, para ponerla junto a otra', () => {
    const initial = toMarketOrder(orderById(INITIAL_ORDER_ID));

    render(<OrderList variant="market" layout="list" orders={[initial]} caption="Órdenes de Kylian Mbappé" />);

    expect(screen.queryByRole('table')).toBeNull();
    const card = within(screen.getByRole('list', { name: 'Órdenes de Kylian Mbappé' })).getByRole('listitem');
    const values = within(card).getAllByRole('definition').map((value) => value.textContent);
    expect(card).toHaveTextContent('Kylian Mbappé');
    expect(values).toEqual(['venta', 'administrador', '100', '13', '87', 'parcial', '28/10/2026, 09:00']);
  });
});
