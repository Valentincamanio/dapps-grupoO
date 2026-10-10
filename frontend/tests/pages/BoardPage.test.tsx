import { screen, waitFor, within } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import type { RouteObject } from 'react-router';
import { describe, expect, it } from 'vitest';
import { appRoutes } from '../../src/router/routes';
import { renderWithRouter } from '../mocks/renderWithRouter';
import { PLAYERS, TEAM_WITHOUT_GOALKEEPERS } from '../mocks/players';
import { SHEET_SIZE } from '../../src/utils/filters';
import { deriveTeams } from '../../src/utils/teams';
import type { Player } from '../../src/types/api';
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

function hrefsOf(players: Player[]): string[] {
  return players.slice(0, SHEET_SIZE).map((player) => `/jugadores/${player.id}`);
}

describe('BoardPage - filtros (historia 3)', () => {
  it('tocar "defensa" deja solo defensores, marca la zona y vuelve a la hoja 1', async () => {
    const { user, router } = renderWithRouter({ initialEntries: ['/pizarra?page=2'] });
    await screen.findByText('hoja 2 de 5');

    await user.click(screen.getByRole('button', { name: 'Filtrar por defensores' }));

    await waitFor(() =>
      expect(sheetHrefs()).toEqual(hrefsOf(PLAYERS.filter((p) => p.position === 'DEFENDER'))),
    );
    expect(screen.getByRole('button', { name: 'Filtrar por defensores' })).toHaveAttribute(
      'aria-pressed',
      'true',
    );
    expect(screen.getByText('hoja 1 de 2')).toBeInTheDocument();
    expect(router.state.location.search).toBe('?position=DEFENDER');
  });

  it('tocar otra vez "defensa" o "todos" quita el filtro', async () => {
    const { user, router } = renderWithRouter({ initialEntries: ['/pizarra?position=DEFENDER'] });
    await screen.findByText('hoja 1 de 2');

    await user.click(screen.getByRole('button', { name: 'Filtrar por defensores' }));
    expect(await screen.findByText('hoja 1 de 5')).toBeInTheDocument();
    expect(router.state.location.search).toBe('');

    await user.click(screen.getByRole('button', { name: 'Filtrar por delanteros' }));
    await waitFor(() => expect(router.state.location.search).toBe('?position=FORWARD'));
    await user.click(screen.getByRole('button', { name: 'Ver todos' }));

    expect(await screen.findByText('hoja 1 de 5')).toBeInTheDocument();
    expect(router.state.location.search).toBe('');
  });

  it('elegir "La Liga" deja solo jugadores de La Liga', async () => {
    const { user } = renderWithRouter({ initialEntries: ['/pizarra'] });
    await screen.findByText('hoja 1 de 5');

    await user.click(screen.getByRole('button', { name: 'La Liga' }));

    const expected = hrefsOf(PLAYERS.filter((p) => p.league === 'LA_LIGA'));
    await waitFor(() => expect(sheetHrefs()).toEqual(expected));
    expect(screen.getByRole('button', { name: 'La Liga' })).toHaveAttribute('aria-pressed', 'true');
  });

  it('el desplegable de equipo solo ofrece equipos de la liga elegida, ordenados, y filtra', async () => {
    const { user, router } = renderWithRouter({ initialEntries: ['/pizarra?league=LA_LIGA'] });
    await waitFor(() => expect(sheetHrefs().length).toBeGreaterThan(0));

    await user.click(screen.getByRole('button', { name: /^equipo:/ }));

    const options = await screen.findAllByRole('option');
    expect(options.map((option) => option.textContent)).toEqual([
      'todos',
      ...deriveTeams(PLAYERS, 'LA_LIGA'),
    ]);
    await user.click(screen.getByRole('option', { name: 'Real Madrid' }));

    const expected = PLAYERS.filter((p) => p.league === 'LA_LIGA' && p.team === 'Real Madrid');
    await waitFor(() => expect(sheetHrefs()).toEqual(hrefsOf(expected)));
    expect(router.state.location.search).toBe('?league=LA_LIGA&team=Real+Madrid');
  });

  it('con posición, liga y equipo combinados cada post-it cumple los tres', async () => {
    renderWithRouter({
      initialEntries: ['/pizarra?position=DEFENDER&league=LA_LIGA&team=Real+Madrid'],
    });
    const expected = hrefsOf(
      PLAYERS.filter(
        (p) => p.position === 'DEFENDER' && p.league === 'LA_LIGA' && p.team === 'Real Madrid',
      ),
    );
    expect(expected.length).toBeGreaterThan(0);

    await waitFor(() => expect(sheetHrefs()).toEqual(expected));
  });

  it('una dirección con posición y hoja reproduce filtros y hoja sin sesión', async () => {
    renderWithRouter({ initialEntries: ['/pizarra?position=DEFENDER&page=2'] });

    expect(await screen.findByText('hoja 2 de 2')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Filtrar por defensores' })).toHaveAttribute(
      'aria-pressed',
      'true',
    );
    const defenders = PLAYERS.filter((p) => p.position === 'DEFENDER');
    expect(sheetHrefs()).toEqual(
      defenders.slice(SHEET_SIZE).map((player) => `/jugadores/${player.id}`),
    );
  });

  it('una dirección con liga marca su pestaña', async () => {
    renderWithRouter({ initialEntries: ['/pizarra?position=DEFENDER&league=LA_LIGA'] });

    await waitFor(() =>
      expect(screen.getByRole('button', { name: 'La Liga' })).toHaveAttribute('aria-pressed', 'true'),
    );
  });

  it('la combinación vacía muestra la descripción y "limpiar filtros" vuelve a la hoja 1', async () => {
    const { user, router } = renderWithRouter({
      initialEntries: [`/pizarra?position=GOALKEEPER&team=${TEAM_WITHOUT_GOALKEEPERS}`],
    });

    expect(await screen.findByText('no hay arqueros de ese equipo en la pizarra')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'limpiar filtros' }));

    expect(await screen.findByText('hoja 1 de 5')).toBeInTheDocument();
    expect(router.state.location.search).toBe('');
  });

  it('?position=XYZ&page=abc muestra la pizarra completa sin error', async () => {
    renderWithRouter({ initialEntries: ['/pizarra?position=XYZ&page=abc'] });

    expect(await screen.findByText('hoja 1 de 5')).toBeInTheDocument();
    expect(sheetHrefs()).toHaveLength(12);
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('el catálogo completo no se pide hasta abrir el desplegable de equipo', async () => {
    const sizes: string[] = [];
    server.events.on('request:start', ({ request }) => {
      const url = new URL(request.url);
      if (url.pathname === '/api/players') sizes.push(url.searchParams.get('size') ?? '');
    });
    const { user } = renderWithRouter({ initialEntries: ['/pizarra'] });
    await screen.findByText('hoja 1 de 5');
    expect(sizes).not.toContain('50');

    await user.click(screen.getByRole('button', { name: /^equipo:/ }));

    await waitFor(() => expect(sizes).toContain('50'));
    server.events.removeAllListeners();
  });
});

describe('BoardPage - buscador (historia 4)', () => {
  it('busca en todo el catálogo con filtros activos, navega a la ficha y no toca los filtros', async () => {
    const { user, router } = renderWithRouter({
      routes: routesWithPlayerStub,
      initialEntries: ['/pizarra?position=DEFENDER'],
    });
    await screen.findByText('hoja 1 de 2');

    await user.click(screen.getByRole('combobox'));
    await user.keyboard('mbappe');
    const option = await screen.findByRole('option', { name: /Kylian Mbappé/ });

    expect(option).toBeInTheDocument();
    expect(router.state.location.search).toBe('?position=DEFENDER');

    await user.keyboard('{Enter}');

    await waitFor(() => expect(router.state.location.pathname).toBe(`/jugadores/${PLAYERS[0].id}`));
    expect(router.state.location.search).toBe('');
    expect(router.state.location.state).toBeNull();
  });
});
