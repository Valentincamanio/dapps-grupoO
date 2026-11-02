// El filtro de estado de Mis órdenes: al entrar se ven las abiertas (pendientes y parciales), y las
// ejecutadas, las canceladas o todas se eligen con el filtro, que queda en la dirección.
import { screen, waitFor, within } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { renderWithRouter } from '../mocks/renderWithRouter';
import { server } from '../mocks/server';

function renderMyOrders(path = '/ordenes') {
  return renderWithRouter({ initialEntries: [path], session: 'valid' });
}

async function states(): Promise<string[]> {
  const table = await screen.findByRole('table');
  return within(table)
    .getAllByRole('row')
    .slice(1)
    .map((row) => row.querySelector('[data-label="Estado"]')?.textContent ?? '');
}

function filters(): HTMLElement {
  return screen.getByRole('group', { name: 'filtrar por estado' });
}

describe('filtro de Mis órdenes', () => {
  it('al entrar muestra solo las abiertas, con ese filtro marcado', async () => {
    renderMyOrders();

    expect(await states()).toEqual(['pendiente', 'parcial']);
    expect(within(filters()).getByRole('button', { name: 'abiertas' })).toHaveAttribute('aria-pressed', 'true');
  });

  it('pide al servidor solo las pendientes y las parciales', async () => {
    const requested: (string | null)[] = [];
    server.events.on('request:start', ({ request }) => {
      const url = new URL(request.url);
      if (/\/users\/\d+\/orders$/.test(url.pathname)) requested.push(url.searchParams.get('status'));
    });
    renderMyOrders();

    await states();

    expect(requested.at(-1)).toBe('PENDING,PARTIAL');
    server.events.removeAllListeners();
  });

  it('elegir ejecutadas muestra solo esas y lo escribe en la dirección', async () => {
    const { user, router } = renderMyOrders();
    await states();

    await user.click(within(filters()).getByRole('button', { name: 'ejecutadas' }));

    await waitFor(async () => expect(new Set(await states())).toEqual(new Set(['ejecutada'])));
    expect(router.state.location.search).toBe('?estado=ejecutadas');
  });

  it('todas muestra cualquier estado', async () => {
    renderMyOrders('/ordenes?estado=todas');

    expect(new Set(await states())).toEqual(new Set(['cancelada', 'pendiente', 'parcial', 'ejecutada']));
  });

  it('volver a abiertas saca el filtro de la dirección y vuelve a la primera hoja', async () => {
    const { user, router } = renderMyOrders('/ordenes?estado=canceladas&page=2');
    await screen.findByText('esta hoja de órdenes está vacía');

    await user.click(within(filters()).getByRole('button', { name: 'abiertas' }));

    await waitFor(() => expect(router.state.location.search).toBe(''));
    expect(await states()).toEqual(['pendiente', 'parcial']);
  });

  it('un filtro desconocido en la dirección vale abiertas', async () => {
    renderMyOrders('/ordenes?estado=archivadas');

    expect(await states()).toEqual(['pendiente', 'parcial']);
  });

  it('sin órdenes en ese estado lo dice y ofrece ver todas', async () => {
    const { user } = renderMyOrders();
    await states();
    await user.click(screen.getByRole('button', { name: /^Cancelar la orden de venta/ }));
    await user.click(screen.getByRole('button', { name: 'Confirmar cancelación' }));
    await user.click(await screen.findByRole('button', { name: /^Cancelar la orden de compra/ }));
    await user.click(screen.getByRole('button', { name: 'Confirmar cancelación' }));

    expect(await screen.findByText('no tenés órdenes abiertas')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'ver todas' }));
    expect(new Set(await states())).toContain('cancelada');
  });
});
