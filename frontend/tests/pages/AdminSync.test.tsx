// Las sincronizaciones manuales en la pestaña Recálculo, como ADMIN: el catálogo con Football-Data y
// las estadísticas con WhoScored, de las cinco ligas o de una, con su informe y sus rechazos.
import { screen, within } from '@testing-library/react';
import { delay, http, HttpResponse } from 'msw';
import { beforeEach, describe, expect, it } from 'vitest';
import { leagueLabel } from '../../src/utils/positions';
import { adminProfileHandler, resetAdminMocks } from '../mocks/quoteHandlers';
import { renderWithRouter } from '../mocks/renderWithRouter';
import { server } from '../mocks/server';
import { FAILING_LEAGUE, FAILURE_REASON } from '../mocks/syncHandlers';

async function openPage() {
  const utils = renderWithRouter({ initialEntries: ['/admin/recalculo'], session: 'valid' });
  await screen.findByRole('button', { name: 'Sincronizar catálogo' });
  return utils;
}

function panel(name: string): HTMLElement {
  return screen.getByRole('region', { name });
}

function apiError(status: number, message: string) {
  return HttpResponse.json(
    { timestamp: new Date().toISOString(), status, error: 'Error', message, path: '/players/sync' },
    { status },
  );
}

describe('sincronización manual', () => {
  beforeEach(() => {
    resetAdminMocks();
    server.use(adminProfileHandler);
  });

  it('sincroniza el catálogo de las cinco ligas y muestra cómo le fue a cada una', async () => {
    const { user } = await openPage();

    await user.click(screen.getByRole('button', { name: 'Sincronizar catálogo' }));

    const report = await screen.findByRole('region', { name: 'informe de la sincronización del catálogo' });
    const rows = within(report).getAllByRole('row').slice(1);
    expect(rows).toHaveLength(5);
    expect(within(report).getAllByText('3 nuevos · 500 actualizados')).toHaveLength(4);
    const failing = rows.find((row) => within(row).queryByText(leagueLabel(FAILING_LEAGUE)))!;
    expect(within(failing).getByText(`falló: ${FAILURE_REASON}`)).toBeInTheDocument();
    expect(within(report).getByRole('alert')).toHaveTextContent('una liga falló');
  });

  it('con una liga elegida sincroniza solo esa', async () => {
    const { user } = await openPage();
    const catalog = panel('Catálogo (Football-Data.org)');

    await user.selectOptions(within(catalog).getByRole('combobox', { name: 'liga' }), 'SERIE_A');
    await user.click(within(catalog).getByRole('button', { name: 'Sincronizar catálogo' }));

    const report = await screen.findByRole('region', { name: 'informe de la sincronización del catálogo' });
    const rows = within(report).getAllByRole('row').slice(1);
    expect(rows).toHaveLength(1);
    expect(within(rows[0]).getByRole('rowheader')).toHaveTextContent(leagueLabel('SERIE_A'));
  });

  it('sincroniza las estadísticas y muestra lo leído del snapshot y de WhoScored', async () => {
    const { user } = await openPage();

    await user.click(screen.getByRole('button', { name: 'Sincronizar estadísticas' }));

    const report = await screen.findByRole('region', { name: 'informe de la sincronización de estadísticas' });
    expect(within(report).getByText('snapshot cargado')).toBeInTheDocument();
    expect(within(report).getAllByText('procesada')).toHaveLength(5);
    expect(within(report).queryByRole('alert')).toBeNull();
  });

  it('mientras corre una, ninguna de las dos se puede volver a disparar', async () => {
    server.use(
      http.post('*/api/matches/stats/sync', async () => {
        await delay('infinite');
        return HttpResponse.json({});
      }),
    );
    const { user } = await openPage();

    await user.click(screen.getByRole('button', { name: 'Sincronizar estadísticas' }));

    expect(await screen.findByText('sincronizando las estadísticas, puede tardar varios minutos...')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Sincronizar estadísticas' })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Sincronizar catálogo' })).toBeDisabled();
  });

  it('un rechazo muestra el motivo del backend, por ejemplo sin el token de Football-Data', async () => {
    const message = 'La sincronización con Football-Data.org está deshabilitada: falta el token.';
    server.use(http.post('*/api/players/sync', () => apiError(503, message)));
    const { user } = await openPage();

    await user.click(screen.getByRole('button', { name: 'Sincronizar catálogo' }));

    expect(await within(panel('Catálogo (Football-Data.org)')).findByText(message)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Sincronizar catálogo' })).toBeEnabled();
  });

  it('con otra sincronización en curso muestra el 409 sin informe', async () => {
    const message = 'Ya hay una sincronización en curso.';
    server.use(http.post('*/api/matches/stats/sync', () => apiError(409, message)));
    const { user } = await openPage();

    await user.click(screen.getByRole('button', { name: 'Sincronizar estadísticas' }));

    expect(await within(panel('Estadísticas (WhoScored)')).findByText(message)).toBeInTheDocument();
    expect(screen.queryByRole('region', { name: 'informe de la sincronización de estadísticas' })).toBeNull();
  });
});
