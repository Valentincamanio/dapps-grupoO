// Historia 3 de la 007: la pantalla de ranking por cotización (FR-063).
import { screen, waitFor, within } from '@testing-library/react';
import { delay, http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { RANKING_SIZE } from '../../src/hooks/useRanking';
import { formatCredits } from '../../src/utils/quotes';
import { ACTIVE_STRATEGY, RANKING_ROWS } from '../mocks/quotes';
import { renderWithRouter } from '../mocks/renderWithRouter';
import { server } from '../mocks/server';

function bodyRows(): HTMLElement[] {
  return within(screen.getByRole('table')).getAllByRole('row').slice(1);
}

function playerNames(): string[] {
  return bodyRows().map((row) => within(row).getByRole('link').textContent ?? '');
}

const TOTAL_SHEETS = Math.ceil(RANKING_ROWS.length / RANKING_SIZE);

describe('RankingPage', () => {
  it('sin sesión lista los jugadores por cotización descendente, con la estrategia activa', async () => {
    renderWithRouter({ initialEntries: ['/ranking'] });

    expect(await screen.findByText(`hoja 1 de ${TOTAL_SHEETS}`)).toBeInTheDocument();

    expect(screen.getByRole('heading', { name: 'Ranking por cotización' })).toBeInTheDocument();
    expect(playerNames()).toEqual(RANKING_ROWS.slice(0, RANKING_SIZE).map((row) => row.name));
    expect(bodyRows()[0]).toHaveTextContent(formatCredits(RANKING_ROWS[0].quote.value));
    expect(
      screen.getByText(
        `estrategia activa: ${ACTIVE_STRATEGY.name}, versión ${ACTIVE_STRATEGY.version}`,
      ),
    ).toBeInTheDocument();
  });

  it('elegir una liga acota la lista, reinicia la hoja y la deja en la dirección', async () => {
    const { user, router } = renderWithRouter({ initialEntries: ['/ranking?page=2'] });
    await screen.findByText(`hoja 2 de ${TOTAL_SHEETS}`);

    await user.click(screen.getByRole('button', { name: 'La Liga' }));

    await waitFor(() => expect(router.state.location.search).toBe('?league=LA_LIGA'));
    const expected = RANKING_ROWS.filter((row) => row.league === 'LA_LIGA');
    await waitFor(() =>
      expect(playerNames()).toEqual(expected.slice(0, RANKING_SIZE).map((row) => row.name)),
    );
    expect(screen.getByText(/^hoja 1 de/)).toBeInTheDocument();
  });

  it('elegir una posición reinicia la hoja y la deja en la dirección', async () => {
    const { user, router } = renderWithRouter({ initialEntries: ['/ranking?page=2'] });
    await screen.findByText(`hoja 2 de ${TOTAL_SHEETS}`);

    await user.click(screen.getByRole('button', { name: 'Filtrar por defensores' }));

    await waitFor(() => expect(router.state.location.search).toBe('?position=DEFENDER'));
    const expected = RANKING_ROWS.filter((row) => row.position === 'DEFENDER');
    await waitFor(() =>
      expect(playerNames()).toEqual(expected.slice(0, RANKING_SIZE).map((row) => row.name)),
    );
  });

  it('cargar la dirección con filtros y hoja reproduce la misma vista', async () => {
    const expected = RANKING_ROWS.filter((row) => row.league === 'LA_LIGA');
    renderWithRouter({ initialEntries: ['/ranking?league=LA_LIGA&page=2'] });

    expect(await screen.findByText(/^hoja 2 de/)).toBeInTheDocument();

    expect(screen.getByRole('button', { name: 'La Liga' })).toHaveAttribute('aria-pressed', 'true');
    expect(playerNames()).toEqual(
      expected.slice(RANKING_SIZE, RANKING_SIZE * 2).map((row) => row.name),
    );
    expect(within(bodyRows()[0]).getAllByRole('cell')[0]).toHaveTextContent(String(RANKING_SIZE + 1));
  });

  it('pasar de hoja la escribe en la dirección', async () => {
    const { user, router } = renderWithRouter({ initialEntries: ['/ranking'] });
    await screen.findByText(`hoja 1 de ${TOTAL_SHEETS}`);

    await user.click(screen.getByRole('button', { name: 'hoja siguiente' }));

    expect(await screen.findByText(`hoja 2 de ${TOTAL_SHEETS}`)).toBeInTheDocument();
    expect(router.state.location.search).toBe('?page=2');
    expect(playerNames()[0]).toBe(RANKING_ROWS[RANKING_SIZE].name);
  });

  it('sin jugadores cotizados para los filtros muestra el vacío y permite limpiarlos', async () => {
    const { user, router } = renderWithRouter({ initialEntries: ['/ranking?team=Equipo+Fantasma'] });

    await user.click(await screen.findByRole('button', { name: 'limpiar filtros' }));

    expect(screen.queryByText('no hay jugadores cotizados con esos filtros')).toBeNull();
    await waitFor(() => expect(router.state.location.search).toBe(''));
    expect(await screen.findByText(`hoja 1 de ${TOTAL_SHEETS}`)).toBeInTheDocument();
  });

  it('una hoja fuera de rango ofrece volver a la primera', async () => {
    const { user } = renderWithRouter({ initialEntries: ['/ranking?page=99'] });

    await user.click(await screen.findByRole('button', { name: 'volver a la primera hoja' }));

    expect(await screen.findByText(`hoja 1 de ${TOTAL_SHEETS}`)).toBeInTheDocument();
  });

  it('mientras carga muestra el estado de carga', async () => {
    server.use(http.get('*/api/players/ranking', async () => delay('infinite')));
    renderWithRouter({ initialEntries: ['/ranking'] });

    expect(await screen.findByText('el DT está pensando...')).toBeInTheDocument();
    expect(screen.queryByRole('table')).toBeNull();
  });

  it('un error muestra "reintentar" y al reintentar carga el ranking', async () => {
    server.use(http.get('*/api/players/ranking', () => HttpResponse.error(), { once: true }));
    const { user } = renderWithRouter({ initialEntries: ['/ranking'] });

    expect(await screen.findByRole('alert')).toHaveTextContent('no se pudo cargar el ranking');
    await user.click(screen.getByRole('button', { name: 'reintentar' }));

    expect(await screen.findByText(`hoja 1 de ${TOTAL_SHEETS}`)).toBeInTheDocument();
  });

  it('sin sesión se llega desde el encabezado y del ranking a la ficha de un jugador', async () => {
    const { user, router } = renderWithRouter({ initialEntries: ['/pizarra'] });

    await user.click(await screen.findByRole('link', { name: 'Ranking' }));
    await screen.findByText(`hoja 1 de ${TOTAL_SHEETS}`);
    expect(router.state.location.pathname).toBe('/ranking');

    await user.click(screen.getByRole('link', { name: RANKING_ROWS[0].name }));

    expect(await screen.findByRole('heading', { name: RANKING_ROWS[0].name })).toBeInTheDocument();
    expect(router.state.location.pathname).toBe(`/jugadores/${RANKING_ROWS[0].playerId}`);
    expect(await screen.findByText('Valor del jugador')).toBeInTheDocument();
  });

  it('el encabezado no agrega listas', async () => {
    renderWithRouter({ initialEntries: ['/ranking'] });
    await screen.findByText(`hoja 1 de ${TOTAL_SHEETS}`);

    expect(screen.queryAllByRole('list')).toHaveLength(0);
  });
});
