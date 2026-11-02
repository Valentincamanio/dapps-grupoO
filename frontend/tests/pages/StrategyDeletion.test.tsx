// Eliminar una estrategia desde la administración: pide confirmación con una advertencia, la activa
// no se puede eliminar y un rechazo se muestra sin cerrar la advertencia.
import { screen, waitFor, within } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { beforeEach, describe, expect, it } from 'vitest';
import { adminProfileHandler, mockStrategies, resetAdminMocks } from '../mocks/quoteHandlers';
import { STRATEGY_A, STRATEGY_B } from '../mocks/quotes';
import { renderWithRouter } from '../mocks/renderWithRouter';
import { server } from '../mocks/server';

async function openPage() {
  const utils = renderWithRouter({ initialEntries: ['/admin/estrategias'], session: 'valid' });
  await screen.findByRole('heading', { name: STRATEGY_B.name });
  return utils;
}

function cardOf(name: string): HTMLElement {
  return screen.getByRole('heading', { name }).closest('article') as HTMLElement;
}

describe('eliminar una estrategia', () => {
  beforeEach(() => {
    resetAdminMocks();
    server.use(adminProfileHandler);
  });

  it('la activa no ofrece eliminar y las demás sí', async () => {
    await openPage();

    const active = within(cardOf(STRATEGY_A.name)).queryByRole('button', { name: `Eliminar ${STRATEGY_A.name}` });
    const inactive = within(cardOf(STRATEGY_B.name)).queryByRole('button', { name: `Eliminar ${STRATEGY_B.name}` });

    expect(active).toBeNull();
    expect(inactive).toBeInTheDocument();
  });

  it('pide confirmar con una advertencia antes de eliminar', async () => {
    const { user } = await openPage();

    await user.click(screen.getByRole('button', { name: `Eliminar ${STRATEGY_B.name}` }));

    const dialog = screen.getByRole('dialog', { name: `¿Eliminar ${STRATEGY_B.name}?` });
    expect(within(dialog).getByText(/No se puede deshacer/)).toBeInTheDocument();
    expect(mockStrategies().map((strategy) => strategy.name)).toContain(STRATEGY_B.name);
  });

  it('al confirmar, la elimina, la saca de la lista y lo informa', async () => {
    const { user } = await openPage();

    await user.click(screen.getByRole('button', { name: `Eliminar ${STRATEGY_B.name}` }));
    await user.click(screen.getByRole('button', { name: 'Eliminar' }));

    expect(await screen.findByText(`se eliminó la estrategia ${STRATEGY_B.name}`)).toBeInTheDocument();
    expect(screen.queryByRole('dialog')).toBeNull();
    await waitFor(() => expect(screen.queryByRole('heading', { name: STRATEGY_B.name })).toBeNull());
    expect(mockStrategies().map((strategy) => strategy.name)).toEqual([STRATEGY_A.name]);
  });

  it('volver cierra la advertencia sin eliminar nada', async () => {
    const before = mockStrategies();
    const { user } = await openPage();

    await user.click(screen.getByRole('button', { name: `Eliminar ${STRATEGY_B.name}` }));
    await user.click(screen.getByRole('button', { name: 'Volver' }));

    expect(screen.queryByRole('dialog')).toBeNull();
    expect(screen.getByRole('heading', { name: STRATEGY_B.name })).toBeInTheDocument();
    expect(mockStrategies()).toEqual(before);
  });

  it('un rechazo del backend se muestra en la advertencia, que sigue abierta', async () => {
    server.use(
      http.delete('*/api/strategies/:id', () =>
        HttpResponse.json(
          {
            timestamp: new Date().toISOString(),
            status: 409,
            error: 'Conflict',
            message: 'No se puede eliminar porque es la estrategia activa: primero hay que activar otra.',
            path: `/strategies/${STRATEGY_B.id}`,
          },
          { status: 409 },
        ),
      ),
    );
    const { user } = await openPage();

    await user.click(screen.getByRole('button', { name: `Eliminar ${STRATEGY_B.name}` }));
    await user.click(screen.getByRole('button', { name: 'Eliminar' }));

    const dialog = screen.getByRole('dialog');
    expect(await within(dialog).findByText(/primero hay que activar otra/)).toBeInTheDocument();
    expect(mockStrategies().map((strategy) => strategy.name)).toContain(STRATEGY_B.name);
  });
});
