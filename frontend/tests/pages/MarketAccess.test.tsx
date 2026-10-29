// Acceso a las pantallas del mercado por rol (research D24 y D27 de la 008, SC-007): el listado es
// para todos; el portfolio, las operaciones y las órdenes, solo para USER.
import { screen, waitFor } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { adminProfileHandler } from '../mocks/quoteHandlers';
import { renderWithRouter } from '../mocks/renderWithRouter';
import { server } from '../mocks/server';

const USER_PATHS = ['/portfolio', '/operaciones', '/ordenes'];
const USER_LINKS = ['Portfolio', 'Operaciones', 'Mis órdenes'];

function expectLinks(present: string[], absent: string[]) {
  present.forEach((name) => expect(screen.getByRole('link', { name })).toBeInTheDocument());
  absent.forEach((name) => expect(screen.queryByRole('link', { name })).toBeNull());
}

describe('acceso a las pantallas del mercado', () => {
  it.each(USER_PATHS)('sin sesión, %s lleva a iniciar sesión', async (path) => {
    const { router } = renderWithRouter({ initialEntries: [path] });

    await waitFor(() => expect(router.state.location.pathname).toBe('/login'));
    expect(await screen.findByRole('button', { name: /entrar/i })).toBeInTheDocument();
  });

  it.each(USER_PATHS)('el administrador que entra por %s ve el aviso de falta de permiso', async (path) => {
    server.use(adminProfileHandler);
    renderWithRouter({ initialEntries: [path], session: 'valid' });

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'no tenés permiso para entrar acá: el administrador no opera en el mercado.',
    );
    expect(screen.getByRole('link', { name: 'volver a la pizarra' })).toHaveAttribute('href', '/pizarra');
    expect(screen.queryByRole('heading', { name: /portfolio|operaciones|órdenes/i })).toBeNull();
  });

  it('un visitante tiene en la barra solo el enlace al Mercado', async () => {
    renderWithRouter({ initialEntries: ['/ranking'] });

    expect(await screen.findByRole('link', { name: 'entrar' })).toBeInTheDocument();
    expectLinks(['Mercado'], [...USER_LINKS, 'Administración']);
    expect(screen.getByRole('link', { name: 'Mercado' })).toHaveAttribute('href', '/mercado');
  });

  it('un usuario común tiene Mercado, Portfolio, Operaciones y Mis órdenes, sin Administración', async () => {
    renderWithRouter({ initialEntries: ['/ranking'], session: 'valid' });

    expect(await screen.findByRole('link', { name: 'Portfolio' })).toHaveAttribute('href', '/portfolio');
    expectLinks(['Mercado', ...USER_LINKS], ['Administración']);
    expect(screen.getByRole('link', { name: 'Operaciones' })).toHaveAttribute('href', '/operaciones');
    expect(screen.getByRole('link', { name: 'Mis órdenes' })).toHaveAttribute('href', '/ordenes');
  });

  it('el administrador tiene Mercado y Administración, sin los enlaces de usuario', async () => {
    server.use(adminProfileHandler);
    renderWithRouter({ initialEntries: ['/ranking'], session: 'valid' });

    expect(await screen.findByRole('link', { name: 'Administración' })).toBeInTheDocument();
    expectLinks(['Mercado', 'Administración'], USER_LINKS);
  });
});
