// La ficha como panel: abierta desde una pantalla se ve encima de ella, sin perder sus filtros ni su
// hoja; abierta directamente sigue siendo una pantalla.
import { screen, waitFor, within } from '@testing-library/react';
import { afterEach, describe, expect, it } from 'vitest';
import { PLAYERS } from '../mocks/players';
import { renderWithRouter } from '../mocks/renderWithRouter';
import { server } from '../mocks/server';

const PANEL = { name: 'ficha del jugador' };

async function openFirstCard(path: string) {
  const rendered = renderWithRouter({ initialEntries: [path] });
  await screen.findByText(/^hoja \d+ de/);
  const [card] = within(screen.getByRole('list')).getAllByRole('link');
  await rendered.user.click(card);
  return { ...rendered, card };
}

afterEach(() => server.events.removeAllListeners());

describe('la ficha como panel', () => {
  it('abierta desde la pizarra se ve encima, con la pizarra debajo y la dirección de la ficha', async () => {
    const { router, card } = await openFirstCard('/pizarra?position=DEFENDER&page=2');

    const panel = await screen.findByRole('dialog', PANEL);

    expect(await within(panel).findByRole('heading', { level: 1 })).toBeInTheDocument();
    expect(router.state.location.pathname).toBe(card.getAttribute('href'));
    expect(screen.getByText('hoja 2 de 2')).toBeInTheDocument();
  });

  it('la pantalla de atrás no se vuelve a pedir ni pierde sus filtros al abrir y cerrar', async () => {
    const sheets: string[] = [];
    server.events.on('request:start', ({ request }) => {
      if (new URL(request.url).pathname === '/api/players') sheets.push(request.url);
    });
    const { user, router } = await openFirstCard('/pizarra?position=DEFENDER&page=2');
    await screen.findByRole('dialog', PANEL);

    await user.keyboard('{Escape}');

    await waitFor(() => expect(router.state.location.pathname).toBe('/pizarra'));
    expect(router.state.location.search).toBe('?position=DEFENDER&page=2');
    expect(screen.queryByRole('dialog', PANEL)).toBeNull();
    expect(screen.getByText('hoja 2 de 2')).toBeInTheDocument();
    expect(sheets).toHaveLength(1);
  });

  it('tocar afuera lo cierra', async () => {
    const { user, router } = await openFirstCard('/pizarra');
    await screen.findByRole('dialog', PANEL);

    await user.click(screen.getByRole('button', { name: 'cerrar el panel' }));

    await waitFor(() => expect(router.state.location.pathname).toBe('/pizarra'));
    expect(screen.queryByRole('dialog', PANEL)).toBeNull();
  });

  it('el enlace de salida dice a qué pantalla vuelve', async () => {
    const { user, router } = renderWithRouter({ initialEntries: ['/ranking'] });
    await screen.findByText(/^hoja \d+ de/);

    await user.click(within(screen.getByRole('table')).getAllByRole('link')[0]);
    const panel = await screen.findByRole('dialog', PANEL);
    await user.click(await within(panel).findByRole('link', { name: 'volver al ranking' }));

    await waitFor(() => expect(router.state.location.pathname).toBe('/ranking'));
  });

  it('abierta desde Mi equipo vuelve a Mi equipo', async () => {
    const { user, router } = renderWithRouter({ initialEntries: ['/portfolio'], session: 'valid' });
    const table = await screen.findByRole('table');

    await user.click(within(table).getAllByRole('link')[0]);
    const panel = await screen.findByRole('dialog', PANEL);
    await user.click(await within(panel).findByRole('link', { name: 'volver a mi equipo' }));

    await waitFor(() => expect(router.state.location.pathname).toBe('/portfolio'));
  });

  it('abierta directamente es una pantalla, sin panel', async () => {
    renderWithRouter({ initialEntries: [`/jugadores/${PLAYERS[0].id}`] });

    await screen.findByRole('heading', { name: PLAYERS[0].name });

    expect(screen.queryByRole('dialog', PANEL)).toBeNull();
  });

  it('sin sesión invita a entrar', async () => {
    renderWithRouter({ initialEntries: [`/jugadores/${PLAYERS[0].id}`] });

    const invite = await screen.findByRole('link', { name: 'Entrá para comprar y vender tokens' });

    expect(invite).toHaveAttribute('href', '/login');
  });
});
