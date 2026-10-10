import { act, screen, waitFor, within } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { SEED_USER } from '../mocks/handlers';
import { renderWithRouter } from '../mocks/renderWithRouter';
import { server } from '../mocks/server';

function openLocker() {
  const utils = renderWithRouter({ initialEntries: ['/vestuario'], session: 'valid' });
  return utils;
}

async function changePassword(
  user: ReturnType<typeof renderWithRouter>['user'],
  current: string,
  next: string,
) {
  await user.type(await screen.findByLabelText('contraseña actual'), current);
  await user.type(screen.getByLabelText('contraseña nueva'), next);
  await user.click(screen.getByRole('button', { name: 'cambiar contraseña' }));
}

describe('LockerRoomPage', () => {
  beforeEach(() => {
    Object.defineProperty(navigator, 'clipboard', {
      configurable: true,
      value: { writeText: vi.fn().mockResolvedValue(undefined) },
    });
  });

  it('muestra usuario, correo, rol legible y saldo con dos decimales, sin acciones de saldo', async () => {
    openLocker();

    expect(await screen.findByRole('heading', { name: 'el vestuario' })).toBeInTheDocument();
    const profile = (await screen.findByText(SEED_USER.email)).closest('dl') as HTMLElement;
    expect(within(profile).getByText(SEED_USER.username)).toBeInTheDocument();
    expect(within(profile).getByText('Usuario')).toBeInTheDocument();
    expect(within(profile).getByText('1.000,00')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /saldo|recargar|depositar|retirar/i })).toBeNull();
  });

  it('cambiar la contraseña con la actual correcta confirma y vacía el formulario', async () => {
    const { user } = openLocker();

    await changePassword(user, SEED_USER.password, 'nueva-clave-123');

    expect(await screen.findByText('contraseña cambiada')).toBeInTheDocument();
    expect(screen.getByLabelText('contraseña actual')).toHaveValue('');
    expect(screen.getByLabelText('contraseña nueva')).toHaveValue('');
  });

  it('con la actual incorrecta muestra la nota junto al campo', async () => {
    const { user } = openLocker();

    await changePassword(user, 'equivocada', 'nueva-clave-123');

    const field = screen.getByLabelText('contraseña actual');
    await waitFor(() => expect(field).toHaveAccessibleDescription('La contraseña actual es incorrecta.'));
    expect(screen.queryByText('contraseña cambiada')).toBeNull();
  });

  it('con una nueva inválida muestra la nota junto al campo', async () => {
    const { user } = openLocker();

    await changePassword(user, SEED_USER.password, 'corta');

    const field = screen.getByLabelText('contraseña nueva');
    await waitFor(() =>
      expect(field).toHaveAccessibleDescription('La contraseña debe tener entre 8 y 72 caracteres.'),
    );
  });

  it('"regenerar clave" + "cancelar" no llama a la API', async () => {
    let calls = 0;
    server.use(
      http.post('*/api/auth/me/api-key', () => {
        calls += 1;
        return HttpResponse.json({ apiKey: 'x' });
      }),
    );
    const { user } = openLocker();

    await user.click(await screen.findByRole('button', { name: 'regenerar clave' }));
    expect(screen.getByText(/la clave actual deja de funcionar/)).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'cancelar' }));

    expect(screen.queryByText(/la clave actual deja de funcionar/)).toBeNull();
    expect(screen.getByRole('button', { name: 'regenerar clave' })).toBeInTheDocument();
    expect(calls).toBe(0);
  });

  it('confirmar muestra la clave nueva con "copiar" y la advertencia; tras "listo" ya no aparece', async () => {
    const { user } = openLocker();

    await user.click(await screen.findByRole('button', { name: 'regenerar clave' }));
    await user.click(screen.getByRole('button', { name: 'sí, regenerar' }));

    const key = await screen.findByLabelText('clave de API');
    expect(key).toHaveValue('clave-de-api-2'.padEnd(43, 'x'));
    expect(screen.getByRole('button', { name: 'copiar' })).toBeInTheDocument();
    expect(screen.getByText('esta clave no se vuelve a mostrar')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'listo' }));

    expect(screen.queryByLabelText('clave de API')).toBeNull();
    expect(await screen.findByRole('heading', { name: 'el vestuario' })).toBeInTheDocument();
  });

  it('"cerrar sesión" lleva a /pizarra sin sesión y /vestuario vuelve a pedir inicio de sesión', async () => {
    const { user, router } = openLocker();

    await user.click(await screen.findByRole('button', { name: 'cerrar sesión' }));

    await waitFor(() => expect(router.state.location.pathname).toBe('/pizarra'));
    expect(await screen.findByRole('link', { name: 'entrar' })).toBeInTheDocument();

    await act(() => router.navigate('/vestuario'));
    await waitFor(() => expect(router.state.location.pathname).toBe('/login'));
  });

  it('un 401 al cambiar la contraseña lleva a /login con el aviso y al entrar vuelve a /vestuario', async () => {
    server.use(
      http.put('*/api/auth/me/password', () =>
        HttpResponse.json(
          {
            timestamp: new Date().toISOString(),
            status: 401,
            error: 'Unauthorized',
            message: 'La credencial es inválida.',
            path: '/auth/me/password',
          },
          { status: 401 },
        ),
      ),
    );
    const { user, router } = openLocker();

    await changePassword(user, SEED_USER.password, 'nueva-clave-123');

    expect(await screen.findByText('tu sesión venció, volvé a entrar')).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/login');

    await user.type(screen.getByLabelText('usuario'), SEED_USER.username);
    await user.type(screen.getByLabelText('contraseña'), SEED_USER.password);
    await user.click(screen.getByRole('button', { name: 'entrar' }));

    await waitFor(() => expect(router.state.location.pathname).toBe('/vestuario'));
  });

  it('una sesión que vence en la pizarra no redirige', async () => {
    const { router } = renderWithRouter({ initialEntries: ['/pizarra'], session: 'expired' });

    expect(await screen.findByRole('link', { name: 'entrar' })).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/pizarra');
  });
});
