// Research R-4, CE-009: ninguna pantalla ofrece compra, venta, plantel, precio ni puntaje.
import { screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { PLAYERS } from '../mocks/players';
import { renderWithRouter } from '../mocks/renderWithRouter';

const FORBIDDEN = /comprar|vender|sumar|plantel|precio|valor|presupuesto|puntaje|portfolio|orden/i;
const PRICE_TEXT = /[$€]\s*\d|\d\s*[$€]|\bUSD\b|\bEUR\b/;

function expectNoPurchaseActions() {
  const actionable = [...screen.queryAllByRole('button'), ...screen.queryAllByRole('link')];
  const offending = actionable
    .map((element) => element.getAttribute('aria-label') ?? element.textContent ?? '')
    .filter((name) => FORBIDDEN.test(name));
  expect(offending).toEqual([]);

  const text = document.body.textContent ?? '';
  expect(text).not.toMatch(PRICE_TEXT);
  expect(text).not.toMatch(/[★☆⭐]/);
  expect(screen.queryByRole('img', { name: /estrella/i })).toBeNull();
}

describe('pantallas sin acciones de compra', () => {
  it('/pizarra, con un filtro y el desplegable de equipos abierto', async () => {
    const { user } = renderWithRouter({ initialEntries: ['/pizarra'] });

    await screen.findAllByRole('link', { name: /./ });
    await user.click(screen.getByRole('button', { name: 'La Liga' }));
    await user.click(await screen.findByRole('button', { name: /^equipo:/ }));
    await screen.findAllByRole('option');

    expectNoPurchaseActions();
  });

  it('/jugadores/<id>', async () => {
    renderWithRouter({ initialEntries: [`/jugadores/${PLAYERS[0].id}`] });

    await screen.findByRole('heading', { name: PLAYERS[0].name });

    expectNoPurchaseActions();
  });

  it('/login', async () => {
    renderWithRouter({ initialEntries: ['/login'] });

    await screen.findByRole('button', { name: /entrar/i });

    expectNoPurchaseActions();
  });

  it('/register', async () => {
    renderWithRouter({ initialEntries: ['/register'] });

    await screen.findByLabelText(/correo/i);

    expectNoPurchaseActions();
  });

  it('/vestuario, con sesión', async () => {
    renderWithRouter({ initialEntries: ['/vestuario'], session: 'valid' });

    await screen.findByRole('heading', { name: 'el vestuario' });
    await screen.findByText('lionel@mail.com');

    expectNoPurchaseActions();
  });
});
