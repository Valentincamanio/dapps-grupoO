import { screen, waitFor, within } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import type { RouteObject } from 'react-router';
import { describe, expect, it } from 'vitest';
import { appRoutes } from '../../src/router/routes';
import { renderWithRouter } from '../mocks/renderWithRouter';
import { PLAYERS } from '../mocks/players';
import { server } from '../mocks/server';

// La ficha llega con otra historia: acá alcanza con una ruta que permita verificar la dirección
const routesWithPlayerStub: RouteObject[] = [
  {
    path: '/',
    element: appRoutes[0].element,
    children: [{ path: 'jugadores/:id', element: <p>ficha</p> }, ...(appRoutes[0].children ?? [])],
  },
];

function sheetHrefs(): string[] {
  const grid = screen.getByRole('list');
  return within(grid)
    .getAllByRole('link')
    .map((link) => link.getAttribute('href') ?? '');
}

describe('BoardPage', () => {
  it('sin sesión muestra la primera hoja con 12 post-its y "hoja 1 de 5"', async () => {
    renderWithRouter({ initialEntries: ['/pizarra'] });

    expect(await screen.findByText('hoja 1 de 5')).toBeInTheDocument();

    expect(sheetHrefs()).toHaveLength(12);
  });

  it('"hoja anterior" está deshabilitada en la primera hoja y "hoja siguiente" en la última', async () => {
    const { user } = renderWithRouter({ initialEntries: ['/pizarra'] });
    await screen.findByText('hoja 1 de 5');
    expect(screen.getByRole('button', { name: 'hoja anterior' })).toBeDisabled();

    for (let sheet = 2; sheet <= 5; sheet += 1) {
      await user.click(screen.getByRole('button', { name: 'hoja siguiente' }));
      await screen.findByText(`hoja ${sheet} de 5`);
    }

    expect(screen.getByRole('button', { name: 'hoja siguiente' })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'hoja anterior' })).toBeEnabled();
  });

  it('recorrer todas las hojas muestra cada jugador exactamente una vez', async () => {
    const { user } = renderWithRouter({ initialEntries: ['/pizarra'] });
    await screen.findByText('hoja 1 de 5');
    const seen = [...sheetHrefs()];

    for (let sheet = 2; sheet <= 5; sheet += 1) {
      await user.click(screen.getByRole('button', { name: 'hoja siguiente' }));
      await screen.findByText(`hoja ${sheet} de 5`);
      seen.push(...sheetHrefs());
    }

    const expected = PLAYERS.map((player) => `/jugadores/${player.id}`);
    expect(seen).toHaveLength(PLAYERS.length);
    expect([...seen].sort()).toEqual([...expected].sort());
  });

  it('la hoja va en la dirección y se respeta al cargar /pizarra?page=2', async () => {
    const { user, router } = renderWithRouter({ initialEntries: ['/pizarra'] });
    await screen.findByText('hoja 1 de 5');

    await user.click(screen.getByRole('button', { name: 'hoja siguiente' }));

    await screen.findByText('hoja 2 de 5');
    expect(router.state.location.search).toBe('?page=2');
    expect(sheetHrefs()[0]).toBe(`/jugadores/${PLAYERS[12].id}`);
  });

  it('cargar /pizarra?page=2 directamente muestra la segunda hoja', async () => {
    renderWithRouter({ initialEntries: ['/pizarra?page=2'] });

    expect(await screen.findByText('hoja 2 de 5')).toBeInTheDocument();

    expect(sheetHrefs()[0]).toBe(`/jugadores/${PLAYERS[12].id}`);
  });

  it('/pizarra?page=40 ofrece "volver a la primera hoja" y al elegirlo vuelve a la hoja 1', async () => {
    const { user, router } = renderWithRouter({ initialEntries: ['/pizarra?page=40'] });
    const back = await screen.findByRole('button', { name: 'volver a la primera hoja' });

    await user.click(back);

    expect(await screen.findByText('hoja 1 de 5')).toBeInTheDocument();
    expect(router.state.location.search).toBe('');
  });

  it('activar un post-it con teclado navega a /jugadores/<id>', async () => {
    const { user, router } = renderWithRouter({
      routes: routesWithPlayerStub,
      initialEntries: ['/pizarra'],
    });
    await screen.findByText('hoja 1 de 5');

    await user.tab();
    let guard = 0;
    while (!document.activeElement?.getAttribute('href')?.startsWith('/jugadores/') && guard < 20) {
      await user.tab();
      guard += 1;
    }
    const focusedHref = document.activeElement?.getAttribute('href');
    await user.keyboard('{Enter}');

    await waitFor(() => expect(router.state.location.pathname).toBe(focusedHref));
    expect(focusedHref).toBe(`/jugadores/${PLAYERS[0].id}`);
  });

  it('un error de red muestra "reintentar" y al reintentar carga la hoja', async () => {
    server.use(http.get('*/api/players', () => HttpResponse.error(), { once: true }));
    const { user } = renderWithRouter({ initialEntries: ['/pizarra'] });
    const retry = await screen.findByRole('button', { name: 'reintentar' });

    await user.click(retry);

    expect(await screen.findByText('hoja 1 de 5')).toBeInTheDocument();
    expect(sheetHrefs()).toHaveLength(12);
  });
});
