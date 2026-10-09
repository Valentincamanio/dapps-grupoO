import { render } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { createMemoryRouter, RouterProvider, type RouteObject } from 'react-router';
import { SessionProvider } from '../../src/context/SessionContext';
import { appRoutes } from '../../src/router/routes';
import { writeSession } from '../../src/services/sessionStorage';
import { SEED_TOKEN } from './handlers';

interface RenderWithRouterOptions {
  // Por defecto, las rutas reales de la app
  routes?: RouteObject[];
  initialEntries?: string[];
  // 'valid': token que el backend simulado acepta.
  // 'expired': vigente según su fecha pero rechazado con 401 por el backend (token vencido).
  session?: 'valid' | 'expired';
}

export function renderWithRouter({
  routes = appRoutes,
  initialEntries = ['/'],
  session,
}: RenderWithRouterOptions = {}) {
  if (session) {
    writeSession({
      token: session === 'valid' ? SEED_TOKEN : 'token-vencido',
      expiresAt: new Date(Date.now() + 60 * 60 * 1000).toISOString(),
    });
  }

  const router = createMemoryRouter(routes, { initialEntries });
  const user = userEvent.setup();
  const utils = render(
    <SessionProvider>
      <RouterProvider router={router} />
    </SessionProvider>,
  );
  return { user, router, ...utils };
}
