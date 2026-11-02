import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { ChalkInput } from '../../src/components/ChalkInput';

describe('ChalkInput', () => {
  it('una contraseña arranca oculta y el ojo la muestra y la vuelve a ocultar', async () => {
    const user = userEvent.setup();
    render(<ChalkInput label="contraseña" type="password" defaultValue="campeon2022" />);
    const field = screen.getByLabelText('contraseña');

    await user.click(screen.getByRole('button', { name: 'mostrar contraseña' }));
    const typeWhenShown = field.getAttribute('type');
    await user.click(screen.getByRole('button', { name: 'ocultar contraseña' }));

    expect(typeWhenShown).toBe('text');
    expect(field).toHaveAttribute('type', 'password');
    expect(screen.getByRole('button', { name: 'mostrar contraseña' })).toHaveAttribute('aria-pressed', 'false');
  });

  it('mostrarla no cambia lo escrito', async () => {
    const user = userEvent.setup();
    render(<ChalkInput label="contraseña" type="password" />);
    const field = screen.getByLabelText('contraseña');
    await user.type(field, 'secreta1');

    await user.click(screen.getByRole('button', { name: 'mostrar contraseña' }));

    expect(field).toHaveValue('secreta1');
    expect(field).toHaveAttribute('type', 'text');
  });

  it('un campo que no es de contraseña no tiene el ojo', () => {
    render(<ChalkInput label="usuario" type="text" />);

    const toggle = screen.queryByRole('button');

    expect(toggle).toBeNull();
  });
});
