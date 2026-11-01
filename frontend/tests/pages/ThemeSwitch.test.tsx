// El botón de tema de la barra: está para todos, a la izquierda del saldo, solo con un ícono y el
// cartel "Cambiar tema", y lo elegido queda guardado en el navegador.
import { screen, within } from '@testing-library/react';
import { afterEach, describe, expect, it } from 'vitest';
import { renderWithRouter } from '../mocks/renderWithRouter';

afterEach(() => {
  localStorage.clear();
  delete document.documentElement.dataset.theme;
});

describe('cambio de tema', () => {
  it('un visitante tiene el botón, con el cartel "Cambiar tema" y sin texto propio', async () => {
    renderWithRouter({ initialEntries: ['/pizarra'] });

    const toggle = await screen.findByRole('button', { name: /^Cambiar tema/ });

    expect(within(toggle).getByText('Cambiar tema')).toBeInTheDocument();
    expect(toggle).toHaveAccessibleName('Cambiar tema: pasar al oscuro');
  });

  it('va a la izquierda del saldo libre del usuario', async () => {
    renderWithRouter({ initialEntries: ['/pizarra'], session: 'valid' });

    const balance = await screen.findByText(/Saldo libre/);
    const toggle = screen.getByRole('button', { name: /^Cambiar tema/ });

    expect(toggle.compareDocumentPosition(balance) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
  });

  it('cambiar al oscuro lo aplica a la página y lo guarda; volver a tocarlo pasa al claro', async () => {
    const { user } = renderWithRouter({ initialEntries: ['/pizarra'] });
    const toggle = await screen.findByRole('button', { name: /^Cambiar tema/ });

    await user.click(toggle);
    const afterFirst = document.documentElement.dataset.theme;
    await user.click(screen.getByRole('button', { name: /^Cambiar tema/ }));

    expect(afterFirst).toBe('dark');
    expect(document.documentElement.dataset.theme).toBe('light');
    expect(localStorage.getItem('futbolmarket.theme')).toBe('light');
    expect(screen.getByRole('button', { name: /^Cambiar tema/ })).toHaveAccessibleName('Cambiar tema: pasar al oscuro');
  });

  it('arranca con el tema que quedó guardado', async () => {
    localStorage.setItem('futbolmarket.theme', 'dark');

    renderWithRouter({ initialEntries: ['/pizarra'] });

    expect(await screen.findByRole('button', { name: /^Cambiar tema/ })).toHaveAccessibleName(
      'Cambiar tema: pasar al claro',
    );
  });

  it('un valor guardado que no es un tema se ignora y sigue al sistema', async () => {
    localStorage.setItem('futbolmarket.theme', 'violeta');

    renderWithRouter({ initialEntries: ['/pizarra'] });

    expect(await screen.findByRole('button', { name: /^Cambiar tema/ })).toHaveAccessibleName(
      'Cambiar tema: pasar al oscuro',
    );
  });
});
