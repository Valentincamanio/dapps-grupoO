// Historia 8 de la 007: la pantalla del recálculo (FR-067, FR-068), como ADMIN.
import { screen, within } from '@testing-library/react';
import { delay, http, HttpResponse } from 'msw';
import { beforeEach, describe, expect, it } from 'vitest';
import { pendingReasonLabel } from '../../src/utils/metrics';
import { weekLabel } from '../../src/utils/quotes';
import { adminProfileHandler, resetAdminMocks, setAdminConflict } from '../mocks/quoteHandlers';
import { EMPTY_RECALCULATION_REPORT, QUOTED_PLAYERS, SYNC_IN_PROGRESS, WEEKS } from '../mocks/quotes';
import { renderWithRouter } from '../mocks/renderWithRouter';
import { server } from '../mocks/server';

async function openPage() {
  const utils = renderWithRouter({ initialEntries: ['/admin/recalculo'], session: 'valid' });
  const button = await screen.findByRole('button', { name: 'Recalcular cotizaciones' });
  return { ...utils, button };
}

describe('AdminRecalculationPage', () => {
  beforeEach(() => {
    resetAdminMocks();
    server.use(adminProfileHandler);
  });

  it('dispara el recálculo y muestra el informe completo', async () => {
    const { user, button } = await openPage();

    await user.click(button);

    const report = await screen.findByRole('region', { name: 'informe del recálculo' });
    expect(within(report).getByText('origen: disparo manual')).toBeInTheDocument();
    const rows = within(report).getAllByRole('row').slice(1);
    expect(rows.map((row) => within(row).getByRole('rowheader').textContent)).toEqual([
      weekLabel(WEEKS[6]),
      weekLabel(WEEKS[7]),
    ]);
    expect(within(rows[1]).getAllByRole('cell')[1]).toHaveTextContent(String(QUOTED_PLAYERS));
    expect(within(report).getByText(pendingReasonLabel('MISSING_STATS'), { exact: false })).toBeInTheDocument();
    expect(within(report).getByText(pendingReasonLabel('PREVIOUS_WEEK_PENDING'), { exact: false })).toBeInTheDocument();
  });

  it('muestra la carga mientras recalcula', async () => {
    server.use(
      http.post('*/api/quotes/recalculate', async () => {
        await delay(100);
        return HttpResponse.json(EMPTY_RECALCULATION_REPORT);
      }),
    );
    const { user, button } = await openPage();

    await user.click(button);

    expect(screen.getByText('recalculando, puede tardar un rato...')).toBeInTheDocument();
    expect(button).toBeDisabled();
    expect(await screen.findByText('No había semanas para cotizar')).toBeInTheDocument();
    expect(button).toBeEnabled();
  });

  it('un informe vacío dice que no había semanas para cotizar', async () => {
    server.use(http.post('*/api/quotes/recalculate', () => HttpResponse.json(EMPTY_RECALCULATION_REPORT)));
    const { user, button } = await openPage();

    await user.click(button);

    expect(await screen.findByText('No había semanas para cotizar')).toBeInTheDocument();
    expect(screen.queryByRole('table')).toBeNull();
  });

  it('un 409 muestra el motivo y conserva la pantalla con el último informe', async () => {
    const { user, button } = await openPage();
    await user.click(button);
    await screen.findByRole('region', { name: 'informe del recálculo' });

    setAdminConflict(SYNC_IN_PROGRESS);
    await user.click(button);

    expect(await screen.findByRole('alert')).toHaveTextContent(SYNC_IN_PROGRESS);
    expect(screen.queryByRole('button', { name: 'reintentar' })).toBeNull();
    expect(screen.getByRole('region', { name: 'informe del recálculo' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Recalcular cotizaciones' })).toBeEnabled();
  });

  it('un error del servidor se puede reintentar', async () => {
    let fail = true;
    server.use(
      http.post('*/api/quotes/recalculate', () =>
        fail
          ? HttpResponse.json({ status: 500, message: 'Error interno del servidor.' }, { status: 500 })
          : HttpResponse.json(EMPTY_RECALCULATION_REPORT),
      ),
    );
    const { user, button } = await openPage();
    await user.click(button);
    expect(await screen.findByRole('alert')).toBeInTheDocument();

    fail = false;
    await user.click(screen.getByRole('button', { name: 'reintentar' }));

    expect(await screen.findByText('No había semanas para cotizar')).toBeInTheDocument();
    expect(screen.queryByRole('alert')).toBeNull();
  });
});
