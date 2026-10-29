// Historia 8 de la 008: el score se ve en toda pantalla con cotizaciones (FR-058, FR-071). Las
// estadísticas crudas, el valor base y las métricas normalizadas siguen ocultos (SC-014).
import { screen, waitFor, within } from '@testing-library/react';
import { afterEach, describe, expect, it } from 'vitest';
import { formatScore } from '../../src/utils/market';
import { CURRENT_PORTFOLIO } from '../mocks/market';
import { PLAYERS } from '../mocks/players';
import { currentQuoteOf, RANKING_ROWS, REQUOTED_PLAYER_ID } from '../mocks/quotes';
import { renderWithRouter } from '../mocks/renderWithRouter';
import { server } from '../mocks/server';

// Un score con su formato: 0,30, -0,123456
const SCORE_TEXT = /^-?\d+,\d{2,6}$/;
// Lo que ninguna pantalla muestra: estadísticas crudas, valor base ni métricas normalizadas
const HIDDEN_DATA = /\b(goles|asistencias|minutos jugados|rating|tarjetas|valor base|normalizad[ao]s?)\b/i;

function scoreOf(playerId: number): string {
  return formatScore(currentQuoteOf(playerId)!.score!);
}

// La última celda de una fila: la del score en las tablas que lo suman al final
function lastCellOf(row: HTMLElement): string {
  const cells = within(row).getAllByRole('cell');
  return cells[cells.length - 1].textContent ?? '';
}

function expectNoHiddenData() {
  expect(document.body.textContent ?? '').not.toMatch(HIDDEN_DATA);
}

afterEach(() => server.events.removeAllListeners());

describe('el score en las pantallas', () => {
  it('la ficha lo muestra al final del panel de la cotización', async () => {
    renderWithRouter({ initialEntries: [`/jugadores/${REQUOTED_PLAYER_ID}`] });

    const label = await screen.findByText('Score', { selector: 'dt' });

    expect(label.nextElementSibling).toHaveTextContent(scoreOf(REQUOTED_PLAYER_ID));
    const panelLabels = Array.from(label.closest('dl')!.querySelectorAll('dt')).map((dt) => dt.textContent);
    expect(panelLabels[panelLabels.length - 1]).toBe('Score');
    expectNoHiddenData();
  });

  it('la ficha lo muestra en el detalle de cada punto del gráfico y en su tabla', async () => {
    const { user } = renderWithRouter({ initialEntries: [`/jugadores/${REQUOTED_PLAYER_ID}`] });
    const points = await screen.findAllByRole('button', { name: /^Cotización de la semana del / });

    await user.click(points[points.length - 1]);

    expect(screen.getByText(/^Cotización de la semana del /, { selector: 'figcaption' })).toHaveTextContent(
      new RegExp(`, score ${scoreOf(REQUOTED_PLAYER_ID)}$`),
    );

    await user.click(screen.getByText('ver la tabla de cotizaciones por semana'));

    const table = screen.getByRole('table', { name: 'Cotización de cada semana del rango' });
    const headers = within(table).getAllByRole('columnheader').map((header) => header.textContent);
    expect(headers[headers.length - 1]).toBe('Score');
    const rows = within(table).getAllByRole('row').slice(1);
    rows.forEach((row) => expect(lastCellOf(row)).toMatch(SCORE_TEXT));
    expect(lastCellOf(rows[rows.length - 1])).toBe(scoreOf(REQUOTED_PLAYER_ID));
    expectNoHiddenData();
  });

  it('el ranking lo muestra en la última columna', async () => {
    renderWithRouter({ initialEntries: ['/ranking'] });

    const table = await screen.findByRole('table', { name: 'Jugadores por cotización, de mayor a menor' });

    const headers = within(table).getAllByRole('columnheader').map((header) => header.textContent);
    expect(headers[headers.length - 1]).toBe('Score');
    const [first] = within(table).getAllByRole('row').slice(1);
    expect(lastCellOf(first)).toBe(formatScore(RANKING_ROWS[0].quote.score!));
    expectNoHiddenData();
  });

  it('la pizarra lo muestra al final de cada post-it, sin listas ni botones nuevos', async () => {
    renderWithRouter({ initialEntries: ['/pizarra'] });
    await screen.findByText('hoja 1 de 5');

    const player = PLAYERS[0];
    const postIt = within(screen.getByRole('list'))
      .getAllByRole('link')
      .find((link) => link.getAttribute('href') === `/jugadores/${player.id}`)!;

    await waitFor(() => expect(postIt).toHaveTextContent(new RegExp(`score ${scoreOf(player.id)}$`)));
    expect(screen.getAllByRole('list')).toHaveLength(1);
    expect(within(postIt).queryAllByRole('button')).toHaveLength(0);
    expectNoHiddenData();
  });

  it('el portfolio lo muestra con la cotización de cada posición', async () => {
    renderWithRouter({ initialEntries: ['/portfolio'], session: 'valid' });

    const table = await screen.findByRole('table');

    const rows = within(table).getAllByRole('row').slice(1);
    rows.forEach((row, index) => {
      const score = row.querySelector('[data-label="Score"]');
      expect(score).toHaveTextContent(formatScore(CURRENT_PORTFOLIO.positions[index].quote.score));
    });
    expectNoHiddenData();
  });
});
