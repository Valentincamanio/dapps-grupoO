// Historia 8 de la 007: la pantalla de simulación (FR-067), como ADMIN.
import { screen, waitFor, within } from '@testing-library/react';
import { delay, http, HttpResponse } from 'msw';
import { beforeEach, describe, expect, it } from 'vitest';
import { SIMULATION_SIZE } from '../../src/hooks/useSimulation';
import { formatCredits } from '../../src/utils/quotes';
import { adminProfileHandler, mockStrategies, resetAdminMocks } from '../mocks/quoteHandlers';
import { simulationRows, STRATEGY_A, STRATEGY_B } from '../mocks/quotes';
import { renderWithRouter } from '../mocks/renderWithRouter';
import { server } from '../mocks/server';

// La semilla del backend simulado: id * 10 + versión, o factor en centésimos + métricas enviadas
const SEED_A = STRATEGY_A.id * 10 + STRATEGY_A.version;
const SEED_B = STRATEGY_B.id * 10 + STRATEGY_B.version;
const SEED_PARAMETERS = 10 * 100 + 1;

async function openPage() {
  const utils = renderWithRouter({ initialEntries: ['/admin/simulacion'], session: 'valid' });
  await screen.findByRole('combobox', { name: 'estrategia' });
  return utils;
}

function rowNames(): string[] {
  return within(screen.getByRole('table'))
    .getAllByRole('rowheader')
    .map((header) => header.textContent ?? '');
}

function totalSheets(seed: number): number {
  return Math.ceil(simulationRows(seed).length / SIMULATION_SIZE);
}

describe('AdminSimulationPage', () => {
  beforeEach(() => {
    resetAdminMocks();
    server.use(adminProfileHandler);
  });

  it('simula la activa por defecto y muestra actual, simulada y diferencia', async () => {
    const { user } = await openPage();

    expect(screen.getByRole('combobox', { name: 'estrategia' })).toHaveDisplayValue(
      `${STRATEGY_A.name}, versión 1 (activa)`,
    );
    expect(screen.getByText('elegí qué simular y tocá "Simular"')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Simular' }));

    expect(await screen.findByText(`simulada con ${STRATEGY_A.name}, versión 1 · 8 semanas simuladas`)).toBeInTheDocument();
    const expected = simulationRows(SEED_A);
    expect(rowNames()).toEqual(expected.slice(0, SIMULATION_SIZE).map((row) => row.name));
    const first = screen.getByRole('rowheader', { name: expected[0].name }).closest('tr') as HTMLElement;
    expect(within(first).getByText(formatCredits(expected[0].simulatedValue))).toBeInTheDocument();
    expect(screen.getByText(`hoja 1 de ${totalSheets(SEED_A)}`)).toBeInTheDocument();
    expect(screen.getByText(/sin simular: semana del 12\/10\/2026/)).toBeInTheDocument();
  });

  it('simula otra estrategia guardada sin cambiar nada', async () => {
    const before = mockStrategies();
    const { user } = await openPage();

    await user.selectOptions(screen.getByRole('combobox', { name: 'estrategia' }), String(STRATEGY_B.id));
    await user.click(screen.getByRole('button', { name: 'Simular' }));

    expect(await screen.findByText(new RegExp(`^simulada con ${STRATEGY_B.name}, versión 1`))).toBeInTheDocument();
    expect(rowNames()).toEqual(simulationRows(SEED_B).slice(0, SIMULATION_SIZE).map((row) => row.name));
    expect(mockStrategies()).toEqual(before);
  });

  it('con parámetros no deja simular hasta que los pesos suman 1,00', async () => {
    const { user } = await openPage();

    await user.click(screen.getByRole('radio', { name: 'parámetros nuevos' }));
    const simulate = screen.getByRole('button', { name: 'Simular' });
    expect(simulate).toBeDisabled();
    await user.type(screen.getByLabelText('goles'), '0,95');
    expect(simulate).toBeDisabled();
    expect(screen.getByText('faltan 0,05 para llegar a 1,00')).toBeInTheDocument();

    await user.clear(screen.getByLabelText('goles'));
    await user.type(screen.getByLabelText('goles'), '1');
    await user.click(simulate);

    expect(await screen.findByText(/^simulada con los parámetros cargados/)).toBeInTheDocument();
    expect(rowNames()).toEqual(simulationRows(SEED_PARAMETERS).slice(0, SIMULATION_SIZE).map((row) => row.name));
  });

  it('los filtros y la hoja vuelven a simular el mismo pedido', async () => {
    const { user } = await openPage();
    await user.click(screen.getByRole('button', { name: 'Simular' }));
    await screen.findByText(`hoja 1 de ${totalSheets(SEED_A)}`);

    await user.click(screen.getByRole('button', { name: 'hoja siguiente' }));
    await screen.findByText(`hoja 2 de ${totalSheets(SEED_A)}`);
    expect(rowNames()).toEqual(
      simulationRows(SEED_A)
        .slice(SIMULATION_SIZE, SIMULATION_SIZE * 2)
        .map((row) => row.name),
    );

    await user.click(screen.getByRole('button', { name: 'La Liga' }));
    const laLiga = simulationRows(SEED_A).filter((row) => row.league === 'LA_LIGA');
    await waitFor(() => expect(rowNames()).toEqual(laLiga.slice(0, SIMULATION_SIZE).map((row) => row.name)));
    expect(screen.getByText(/^hoja 1 de/)).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'defensores' }));
    const defenders = laLiga.filter((row) => row.position === 'DEFENDER');
    await waitFor(() => expect(rowNames()).toEqual(defenders.slice(0, SIMULATION_SIZE).map((row) => row.name)));
  });

  it('un 400 del servidor muestra el motivo sin reintentar', async () => {
    server.use(
      http.post('*/api/quotes/simulate', () =>
        HttpResponse.json(
          { status: 400, message: 'Los pesos por defecto deben sumar 1,00 y suman 0,95.' },
          { status: 400 },
        ),
      ),
    );
    const { user } = await openPage();

    await user.click(screen.getByRole('button', { name: 'Simular' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Los pesos por defecto deben sumar 1,00 y suman 0,95.');
    expect(screen.queryByRole('button', { name: 'reintentar' })).toBeNull();
  });

  it('muestra la carga y, si el servidor falla, el error con reintentar', async () => {
    let fail = true;
    server.use(
      http.post('*/api/quotes/simulate', async () => {
        await delay(50);
        if (fail) return HttpResponse.json({ status: 500, message: 'Error interno.' }, { status: 500 });
        return undefined;
      }),
    );
    const { user } = await openPage();

    await user.click(screen.getByRole('button', { name: 'Simular' }));
    expect(screen.getByText('simulando...')).toBeInTheDocument();
    expect(await screen.findByRole('alert')).toBeInTheDocument();

    fail = false;
    await user.click(screen.getByRole('button', { name: 'reintentar' }));

    expect(await screen.findByText(`hoja 1 de ${totalSheets(SEED_A)}`)).toBeInTheDocument();
  });

  it('una combinación sin jugadores lo dice', async () => {
    server.use(
      http.post('*/api/quotes/simulate', () =>
        HttpResponse.json({
          strategy: STRATEGY_A,
          weeksSimulated: 8,
          pendingWeeks: [],
          content: [],
          page: 0,
          size: SIMULATION_SIZE,
          totalElements: 0,
          totalPages: 0,
          hasPrevious: false,
          hasNext: false,
        }),
      ),
    );
    const { user } = await openPage();

    await user.click(screen.getByRole('button', { name: 'Simular' }));

    expect(await screen.findByText('no hay jugadores con esos filtros')).toBeInTheDocument();
    expect(screen.queryByRole('table')).toBeNull();
  });
});
