// La navegación simplificada: la barra tiene los grupos de pantallas y cada grupo, sus pestañas.
import { screen, waitFor, within } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { adminProfileHandler } from '../mocks/quoteHandlers';
import { renderWithRouter } from '../mocks/renderWithRouter';
import { server } from '../mocks/server';

function linksOf(nav: HTMLElement): string[] {
  return within(nav)
    .getAllByRole('link')
    .map((link) => link.textContent ?? '');
}

describe('navegación', () => {
  it('un visitante tiene en la barra solo Jugadores', async () => {
    renderWithRouter({ initialEntries: ['/pizarra'] });

    const nav = await screen.findByRole('navigation', { name: 'secciones' });

    expect(linksOf(nav)).toEqual(['Jugadores']);
  });

  it('un usuario común tiene en la barra Jugadores y Mi equipo, sin las pantallas sueltas', async () => {
    renderWithRouter({ initialEntries: ['/pizarra'], session: 'valid' });

    const myTeam = await screen.findByRole('link', { name: 'Mi equipo' });

    expect(myTeam).toHaveAttribute('href', '/portfolio');
    expect(linksOf(screen.getByRole('navigation', { name: 'secciones' }))).toEqual(['Jugadores', 'Mi equipo']);
  });

  it('el administrador tiene en la barra Jugadores y Administración', async () => {
    server.use(adminProfileHandler);
    renderWithRouter({ initialEntries: ['/pizarra'], session: 'valid' });

    await screen.findByRole('link', { name: 'Administración' });

    expect(linksOf(screen.getByRole('navigation', { name: 'secciones' }))).toEqual(['Jugadores', 'Administración']);
  });

  it.each(['/pizarra', '/ranking', '/mercado'])('%s marca Jugadores y ofrece sus tres pestañas', async (path) => {
    renderWithRouter({ initialEntries: [path] });

    const tabs = await screen.findByRole('navigation', { name: 'secciones de jugadores' });

    expect(linksOf(tabs)).toEqual(['Todos', 'Ranking', 'Mercado']);
    expect(within(tabs).getByRole('link', { current: 'page' })).toHaveAttribute('href', path);
    expect(screen.getByRole('link', { name: 'Jugadores' })).toHaveAttribute('aria-current', 'page');
  });

  it.each(['/portfolio', '/ordenes', '/operaciones'])('%s marca Mi equipo y ofrece sus tres pestañas', async (path) => {
    renderWithRouter({ initialEntries: [path], session: 'valid' });

    const tabs = await screen.findByRole('navigation', { name: 'secciones de mi equipo' });

    expect(linksOf(tabs)).toEqual(['Mis jugadores', 'Órdenes', 'Movimientos']);
    expect(within(tabs).getByRole('link', { current: 'page' })).toHaveAttribute('href', path);
    expect(await screen.findByRole('link', { name: 'Mi equipo' })).toHaveAttribute('aria-current', 'page');
  });

  it('las pestañas de Mi equipo llevan de una pantalla a otra', async () => {
    const { user, router } = renderWithRouter({ initialEntries: ['/portfolio'], session: 'valid' });

    await user.click(await screen.findByRole('link', { name: 'Órdenes' }));

    await waitFor(() => expect(router.state.location.pathname).toBe('/ordenes'));
    expect(await screen.findByRole('heading', { name: 'Mis órdenes' })).toBeInTheDocument();
  });

  it('en la cuenta ningún grupo queda marcado', async () => {
    renderWithRouter({ initialEntries: ['/vestuario'], session: 'valid' });

    await screen.findByRole('heading', { name: 'el vestuario' });
    const nav = await screen.findByRole('navigation', { name: 'secciones' });

    expect(within(nav).queryByRole('link', { current: 'page' })).toBeNull();
  });
});
