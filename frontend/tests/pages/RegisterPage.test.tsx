import { screen, waitFor } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { SEED_USER } from '../mocks/handlers';
import { renderWithRouter } from '../mocks/renderWithRouter';

type Rendered = ReturnType<typeof renderWithRouter>;

async function fillForm(
  user: Rendered['user'],
  values: { username: string; email: string; password: string },
) {
  await user.type(screen.getByLabelText('usuario'), values.username);
  await user.type(screen.getByLabelText('correo'), values.email);
  await user.type(screen.getByLabelText('contraseña'), values.password);
  await user.click(screen.getByRole('button', { name: 'registrarse' }));
}

const NEW_USER = { username: 'nuevo_dt', email: 'nuevo@mail.com', password: 'clave12345' };

// userEvent.setup() instala su propio portapapeles al renderizar: se reemplaza después
function mockClipboard(writeText: () => Promise<void>) {
  Object.defineProperty(navigator, 'clipboard', { configurable: true, value: { writeText } });
}

describe('RegisterPage', () => {
  it('un registro válido muestra la clave una vez con "copiar" y la advertencia', async () => {
    const { user } = renderWithRouter({ initialEntries: ['/register'] });

    await fillForm(user, NEW_USER);

    const key = (await screen.findByLabelText('clave de API')) as HTMLInputElement;
    expect(key.value).toContain('clave-de-api-');
    expect(screen.getByText('esta clave no se vuelve a mostrar')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'copiar' })).toBeInTheDocument();
  });

  it('"copiar" confirma con una nota', async () => {
    const { user } = renderWithRouter({ initialEntries: ['/register'] });
    const writeText = vi.fn().mockResolvedValue(undefined);
    mockClipboard(writeText);
    await fillForm(user, NEW_USER);
    const key = (await screen.findByLabelText('clave de API')) as HTMLInputElement;

    await user.click(screen.getByRole('button', { name: 'copiar' }));

    expect(await screen.findByText('clave copiada')).toBeInTheDocument();
    expect(writeText).toHaveBeenCalledWith(key.value);
  });

  it('si la copia falla avisa y deja la clave visible', async () => {
    const { user } = renderWithRouter({ initialEntries: ['/register'] });
    mockClipboard(vi.fn().mockRejectedValue(new Error('no')));
    await fillForm(user, NEW_USER);
    await screen.findByLabelText('clave de API');

    await user.click(screen.getByRole('button', { name: 'copiar' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('la copia automática no funcionó');
    expect(screen.getByLabelText('clave de API')).toBeVisible();
  });

  it('"continuar" deja la sesión iniciada y lleva a /pizarra; la clave ya no aparece', async () => {
    const { user, router } = renderWithRouter({ initialEntries: ['/register'] });
    await fillForm(user, NEW_USER);
    const key = (await screen.findByLabelText('clave de API')) as HTMLInputElement;
    const apiKey = key.value;

    await user.click(screen.getByRole('button', { name: 'continuar' }));

    await waitFor(() => expect(router.state.location.pathname).toBe('/pizarra'));
    expect(screen.queryByDisplayValue(apiKey)).not.toBeInTheDocument();
    expect(document.body).not.toHaveTextContent(apiKey);
    expect(sessionStorage.getItem('futbolmarket.session')).not.toBeNull();
    for (let index = 0; index < sessionStorage.length; index += 1) {
      const storageKey = sessionStorage.key(index) as string;
      expect(sessionStorage.getItem(storageKey)).not.toContain(apiKey);
    }
  });

  it('un usuario duplicado (409) muestra la nota junto al usuario y conserva usuario y correo', async () => {
    const { user } = renderWithRouter({ initialEntries: ['/register'] });

    await fillForm(user, {
      username: SEED_USER.username,
      email: 'otro@mail.com',
      password: NEW_USER.password,
    });

    const usernameInput = screen.getByLabelText('usuario');
    await waitFor(() =>
      expect(usernameInput).toHaveAccessibleDescription('El nombre de usuario ya está registrado.'),
    );
    expect(usernameInput).toHaveValue(SEED_USER.username);
    expect(screen.getByLabelText('correo')).toHaveValue('otro@mail.com');
  });

  it('una contraseña inválida (400) muestra la nota junto a la contraseña', async () => {
    const { user } = renderWithRouter({ initialEntries: ['/register'] });

    await fillForm(user, { ...NEW_USER, password: 'corta' });

    const passwordInput = screen.getByLabelText('contraseña');
    await waitFor(() =>
      expect(passwordInput).toHaveAccessibleDescription(
        'La contraseña debe tener entre 8 y 72 caracteres.',
      ),
    );
    expect(screen.getByLabelText('usuario')).toHaveValue(NEW_USER.username);
    expect(screen.getByLabelText('correo')).toHaveValue(NEW_USER.email);
  });
});
