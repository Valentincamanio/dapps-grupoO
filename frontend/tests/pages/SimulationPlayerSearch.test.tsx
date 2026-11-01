// El buscador de la simulación: después de simular, deja ver un solo jugador, con la misma simulada
// que en la lista completa, y "ver todos" vuelve a la lista.
import { screen, waitFor, within } from '@testing-library/react';
import { beforeEach, describe, expect, it } from 'vitest';
import { SIMULATION_SIZE } from '../../src/hooks/useSimulation';
import { formatCredits } from '../../src/utils/quotes';
import { adminProfileHandler, resetAdminMocks } from '../mocks/quoteHandlers';
import { simulationRows, STRATEGY_A } from '../mocks/quotes';
import { renderWithRouter } from '../mocks/renderWithRouter';
import { server } from '../mocks/server';

const SEED_A = STRATEGY_A.id * 10 + STRATEGY_A.version;

function rowNames(): string[] {
  return within(screen.getByRole('table'))
    .getAllByRole('rowheader')
    .map((header) => header.textContent ?? '');
}

async function simulate() {
  const utils = renderWithRouter({ initialEntries: ['/admin/simulacion'], session: 'valid' });
  await screen.findByRole('combobox', { name: 'estrategia' });
  await utils.user.click(screen.getByRole('button', { name: 'Simular' }));
  await screen.findByRole('table');
  return utils;
}

describe('buscar un jugador en la simulación', () => {
  beforeEach(() => {
    resetAdminMocks();
    server.use(adminProfileHandler);
  });

  it('elegir un jugador deja solo su fila, con la misma simulada', async () => {
    const target = simulationRows(SEED_A)[SIMULATION_SIZE + 2];
    const { user } = await simulate();

    await user.type(screen.getByRole('combobox', { name: 'buscar jugador' }), target.name);
    await user.click(await screen.findByRole('option', { name: new RegExp(target.name) }));

    await waitFor(() => expect(rowNames()).toEqual([target.name]));
    const row = screen.getByRole('rowheader', { name: target.name }).closest('tr') as HTMLElement;
    expect(within(row).getByText(formatCredits(target.simulatedValue))).toBeInTheDocument();
    expect(screen.getByText(/^solo/)).toHaveTextContent(`solo ${target.name}`);
  });

  it('ver todos vuelve a la lista completa', async () => {
    const target = simulationRows(SEED_A)[0];
    const { user } = await simulate();
    await user.type(screen.getByRole('combobox', { name: 'buscar jugador' }), target.name);
    await user.click(await screen.findByRole('option', { name: new RegExp(target.name) }));
    await waitFor(() => expect(rowNames()).toEqual([target.name]));

    await user.click(screen.getByRole('button', { name: 'ver todos' }));

    await waitFor(() => expect(rowNames()).toHaveLength(SIMULATION_SIZE));
    expect(screen.getByRole('combobox', { name: 'buscar jugador' })).toBeInTheDocument();
  });

  it('un jugador que no cumple los filtros deja la hoja vacía', async () => {
    const target = simulationRows(SEED_A).find((row) => row.league !== 'PREMIER')!;
    const { user } = await simulate();

    await user.click(screen.getByRole('button', { name: 'Premier League' }));
    await screen.findByRole('table');
    await user.type(screen.getByRole('combobox', { name: 'buscar jugador' }), target.name);
    await user.click(await screen.findByRole('option', { name: new RegExp(target.name) }));

    expect(await screen.findByText('no hay jugadores con esos filtros')).toBeInTheDocument();
  });
});
