// Historia 8 de la 007: acceso a la administración (FR-064, FR-069): solo ADMIN la ve.
import { screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it } from 'vitest';
import { adminProfileHandler, resetAdminMocks } from '../mocks/quoteHandlers';
import { renderWithRouter } from '../mocks/renderWithRouter';
import { server } from '../mocks/server';

const ADMIN_PATHS = ['/admin/estrategias', '/admin/recalculo', '/admin/simulacion'];

function expectNoAdminControls() {
  expect(screen.queryByRole('navigation', { name: 'administración' })).toBeNull();
  expect(screen.queryByRole('button', { name: 'Nueva estrategia' })).toBeNull();
  expect(screen.queryByRole('button', { name: 'Recalcular cotizaciones' })).toBeNull();
  expect(screen.queryByRole('button', { name: 'Simular' })).toBeNull();
}

describe('acceso a la administración', () => {
  beforeEach(() => resetAdminMocks());

  it.each(ADMIN_PATHS)('sin sesión, %s lleva a iniciar sesión sin controles (US8.2)', async (path) => {
    const { router } = renderWithRouter({ initialEntries: [path] });

    await waitFor(() => expect(router.state.location.pathname).toBe('/login'));
    expectNoAdminControls();
    expect(screen.queryByRole('link', { name: 'Administración' })).toBeNull();
  });

  it('un usuario común no tiene el enlace "Administración" en el encabezado (US8.1)', async () => {
    renderWithRouter({ initialEntries: ['/ranking'], session: 'valid' });

    expect(await screen.findByRole('link', { name: 'vestuario' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Ranking' })).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'Administración' })).toBeNull();
  });

  it.each(ADMIN_PATHS)('un usuario común que entra por %s ve el aviso de falta de permiso (US8.1)', async (path) => {
    renderWithRouter({ initialEntries: [path], session: 'valid' });

    expect(await screen.findByRole('alert')).toHaveTextContent('no tenés permiso para entrar a la administración');
    expect(screen.getByRole('link', { name: 'volver a la pizarra' })).toHaveAttribute('href', '/pizarra');
    expectNoAdminControls();
  });

  it('un ADMIN ve el enlace y entra a la sección (US8.3)', async () => {
    server.use(adminProfileHandler);
    const { user, router } = renderWithRouter({ initialEntries: ['/ranking'], session: 'valid' });

    await user.click(await screen.findByRole('link', { name: 'Administración' }));

    await waitFor(() => expect(router.state.location.pathname).toBe('/admin/estrategias'));
    expect(await screen.findByRole('heading', { name: 'Rendimiento de partido' })).toBeInTheDocument();
    const nav = screen.getByRole('navigation', { name: 'administración' });
    expect(nav).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Estrategias' })).toHaveAttribute('aria-current', 'page');
    expect(screen.queryByText(/no tenés permiso/)).toBeNull();
  });

  it('un ADMIN recorre las tres pantallas con las pestañas', async () => {
    server.use(adminProfileHandler);
    const { user, router } = renderWithRouter({ initialEntries: ['/admin/estrategias'], session: 'valid' });

    await user.click(await screen.findByRole('link', { name: 'Recálculo' }));
    await waitFor(() => expect(router.state.location.pathname).toBe('/admin/recalculo'));
    expect(screen.getByRole('button', { name: 'Recalcular cotizaciones' })).toBeInTheDocument();

    await user.click(screen.getByRole('link', { name: 'Simulación' }));
    await waitFor(() => expect(router.state.location.pathname).toBe('/admin/simulacion'));
    expect(screen.getByRole('button', { name: 'Simular' })).toBeInTheDocument();
  });
});
