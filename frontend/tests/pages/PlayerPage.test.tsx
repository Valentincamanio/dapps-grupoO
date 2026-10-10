import { screen, waitFor, within } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { PLAYERS } from '../mocks/players';
import { renderWithRouter } from '../mocks/renderWithRouter';
import { server } from '../mocks/server';
import { leagueLabel, positionLabel } from '../../src/utils/positions';

describe('PlayerPage', () => {
  it('sin sesión muestra nombre, posición, equipo y liga legibles', async () => {
    const player = PLAYERS[0];
    renderWithRouter({ initialEntries: [`/jugadores/${player.id}`] });

    expect(await screen.findByRole('heading', { name: player.name })).toBeInTheDocument();
    expect(screen.getByText(positionLabel(player.position))).toBeInTheDocument();
    expect(screen.getByText(player.team)).toBeInTheDocument();
    expect(screen.getByText(leagueLabel(player.league))).toBeInTheDocument();
  });

  it('abierta desde un post-it, "volver a la pizarra" conserva filtros y hoja', async () => {
    const { user, router } = renderWithRouter({
      initialEntries: ['/pizarra?position=DEFENDER&page=2'],
    });
    await screen.findByText(/hoja 2 de/);
    const [postIt] = within(screen.getByRole('list')).getAllByRole('link');
    await user.click(postIt);

    await user.click(await screen.findByRole('link', { name: 'volver a la pizarra' }));

    await waitFor(() => expect(router.state.location.pathname).toBe('/pizarra'));
    expect(router.state.location.search).toBe('?position=DEFENDER&page=2');
  });

  it('abierta directamente, "volver a la pizarra" lleva a /pizarra sin parámetros', async () => {
    const { user, router } = renderWithRouter({ initialEntries: [`/jugadores/${PLAYERS[0].id}`] });

    await user.click(await screen.findByRole('link', { name: 'volver a la pizarra' }));

    await waitFor(() => expect(router.state.location.pathname).toBe('/pizarra'));
    expect(router.state.location.search).toBe('');
  });

  it.each(['999999', 'abc'])('/jugadores/%s muestra "jugador no encontrado" con salida', async (id) => {
    renderWithRouter({ initialEntries: [`/jugadores/${id}`] });

    expect(await screen.findByText('jugador no encontrado')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'volver a la pizarra' })).toBeInTheDocument();
  });

  it('la ficha no tiene botones de compra, venta ni similares', async () => {
    renderWithRouter({ initialEntries: [`/jugadores/${PLAYERS[0].id}`] });
    await screen.findByRole('heading', { name: PLAYERS[0].name });

    expect(screen.queryByRole('button', { name: /comprar|vender|sumar|plantel|precio/i })).toBeNull();
  });

  it('un error de red muestra "reintentar" y al reintentar carga la ficha', async () => {
    server.use(http.get('*/api/players/:id', () => HttpResponse.error(), { once: true }));
    const { user } = renderWithRouter({ initialEntries: [`/jugadores/${PLAYERS[0].id}`] });

    await user.click(await screen.findByRole('button', { name: 'reintentar' }));

    expect(await screen.findByRole('heading', { name: PLAYERS[0].name })).toBeInTheDocument();
  });
});
