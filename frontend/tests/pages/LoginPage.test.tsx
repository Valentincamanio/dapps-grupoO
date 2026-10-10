import { screen, waitFor } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { SEED_USER } from '../mocks/handlers';
import { renderWithRouter } from '../mocks/renderWithRouter';
import { server } from '../mocks/server';

async function fillAndSubmit(
  user: ReturnType<typeof renderWithRouter>['user'],
  password: string = SEED_USER.password,
) {
  await user.type(screen.getByLabelText('usuario'), SEED_USER.username);
  await user.type(screen.getByLabelText('contraseña'), password);
  await user.click(screen.getByRole('button', { name: 'entrar' }));
}

describe('LoginPage', () => {
  it('con credenciales correctas lleva a /pizarra', async () => {
    const { user, router } = renderWithRouter({ initialEntries: ['/login'] });

    await fillAndSubmit(user);

    await waitFor(() => expect(router.state.location.pathname).toBe('/pizarra'));
  });

  it('con credenciales incorrectas muestra la nota y sigue en /login', async () => {
    const { user, router } = renderWithRouter({ initialEntries: ['/login'] });

    await fillAndSubmit(user, 'incorrecta');

    expect(await screen.findByRole('alert')).toHaveTextContent('Credenciales inválidas.');
    expect(router.state.location.pathname).toBe('/login');
  });

  it('/vestuario sin sesión lleva a /login con el aviso y al entrar vuelve a /vestuario', async () => {
    const { user, router } = renderWithRouter({ initialEntries: ['/vestuario'] });

    expect(
      await screen.findByText('para entrar al vestuario tenés que iniciar sesión'),
    ).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/login');

    await fillAndSubmit(user);

    await waitFor(() => expect(router.state.location.pathname).toBe('/vestuario'));
    expect(await screen.findByRole('heading', { name: 'el vestuario' })).toBeInTheDocument();
  });

  it('entrar desde "entrar" con filtros vuelve a la misma dirección', async () => {
    const { user, router } = renderWithRouter({
      initialEntries: [
        {
          pathname: '/login',
          state: { from: { pathname: '/pizarra', search: '?position=DEFENDER&page=2' } },
        },
      ],
    });

    await fillAndSubmit(user);

    await waitFor(() => expect(router.state.location.pathname).toBe('/pizarra'));
    expect(router.state.location.search).toBe('?position=DEFENDER&page=2');
  });

  it('una sesión vencida al abrir /vestuario muestra "tu sesión venció, volvé a entrar"', async () => {
    const { router } = renderWithRouter({ initialEntries: ['/vestuario'], session: 'expired' });

    expect(await screen.findByText('tu sesión venció, volvé a entrar')).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/login');
  });

  it('el botón no se puede accionar dos veces mientras espera', async () => {
    let loginRequests = 0;
    server.use(
      http.post('*/api/auth/login', async () => {
        loginRequests += 1;
        await new Promise((resolve) => setTimeout(resolve, 100));
        return HttpResponse.json({
          token: 'token-semilla',
          tokenType: 'Bearer',
          expiresAt: new Date(Date.now() + 60 * 60 * 1000).toISOString(),
        });
      }),
    );
    const { user } = renderWithRouter({ initialEntries: ['/login'] });

    await user.type(screen.getByLabelText('usuario'), SEED_USER.username);
    await user.type(screen.getByLabelText('contraseña'), SEED_USER.password);
    const button = screen.getByRole('button', { name: 'entrar' });
    await user.click(button);

    expect(button).toBeDisabled();
    await user.click(button);

    await waitFor(() => expect(loginRequests).toBe(1));
  });
});
