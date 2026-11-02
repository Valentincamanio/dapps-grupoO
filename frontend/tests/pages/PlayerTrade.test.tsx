// Historia 3 de la 008: comprar y vender desde la ficha (FR-062 a FR-065). Todos ven las órdenes
// pendientes del jugador y su cotización; solo un usuario común ve el panel para operar, que pide
// confirmación, muestra el resultado real y actualiza el saldo libre de la barra.
import { screen, waitFor, within } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { formatBalance } from '../../src/utils/format';
import {
  INITIAL_ORDER_PLAYER_ID,
  insufficientTokensMessage,
  SEED_RESERVED_BALANCE,
  SOLD_OUT_PLAYER_ID,
} from '../mocks/market';
import { PLAYERS } from '../mocks/players';
import { adminProfileHandler } from '../mocks/quoteHandlers';
import { PLAYER_WITHOUT_QUOTE_ID } from '../mocks/quotes';
import { renderWithRouter } from '../mocks/renderWithRouter';
import { server } from '../mocks/server';

const MBAPPE = PLAYERS.find((player) => player.id === INITIAL_ORDER_PLAYER_ID)!;
const SOLD_OUT = PLAYERS.find((player) => player.id === SOLD_OUT_PLAYER_ID)!;
const SEED_BALANCE = 1000;

function openPlayer(playerId: number, session?: 'valid') {
  return renderWithRouter({ initialEntries: [`/jugadores/${playerId}`], session });
}

// Las órdenes que llegan al backend simulado: el handler anota y deja seguir al de market.ts
function recordOrders(): string[] {
  const posted: string[] = [];
  server.use(
    http.post('*/api/orders/:side', ({ request }) => {
      posted.push(new URL(request.url).pathname);
    }),
  );
  return posted;
}

async function pendingOrders(): Promise<HTMLElement> {
  return screen.findByRole('list', { name: /^Órdenes con tokens pendientes de / });
}

// El valor de una etiqueta de una tarjeta o de la confirmación: el <dd> que sigue a su <dt>
function fieldOf(container: HTMLElement, label: string): string {
  return within(container).getByText(label).nextElementSibling?.textContent ?? '';
}

function balanceInBar(): HTMLElement {
  return screen.getByText(/^Saldo libre:/);
}

async function setQuantity(user: ReturnType<typeof openPlayer>['user'], quantity: string) {
  const input = await screen.findByLabelText('Cantidad de tokens');
  await user.clear(input);
  await user.type(input, quantity);
}

describe('PlayerPage - operar', () => {
  it('sin sesión muestra las órdenes pendientes y la cotización, sin controles de operar', async () => {
    openPlayer(MBAPPE.id);

    const orders = await pendingOrders();

    const initial = within(orders).getByRole('listitem');
    expect(fieldOf(initial, 'Dueño')).toBe('administrador');
    expect(fieldOf(initial, 'Pendiente')).toBe('87');
    expect(await screen.findByText('Precio por token')).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: 'Operar' })).toBeNull();
    expect(screen.queryByRole('button', { name: /comprar|vender/i })).toBeNull();
  });

  it('el administrador ve las órdenes pendientes y la cotización, sin controles de operar', async () => {
    server.use(adminProfileHandler);
    openPlayer(MBAPPE.id, 'valid');

    await screen.findByRole('link', { name: 'Administración' });
    await pendingOrders();

    expect(await screen.findByText('Precio por token')).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: 'Operar' })).toBeNull();
    expect(screen.queryByRole('button', { name: /comprar|vender/i })).toBeNull();
  });

  it('la barra muestra el saldo libre del usuario', async () => {
    openPlayer(MBAPPE.id, 'valid');

    await waitFor(() => expect(balanceInBar()).toHaveTextContent(formatBalance(SEED_BALANCE - SEED_RESERVED_BALANCE)));
  });

  it('comprar pide confirmación, no envía nada antes y muestra el resultado real y el saldo nuevo', async () => {
    const posted = recordOrders();
    const { user } = openPlayer(MBAPPE.id, 'valid');
    await setQuantity(user, '10');

    await user.click(screen.getByRole('button', { name: 'Comprar' }));
    const dialog = screen.getByRole('dialog', { name: '¿Comprar 10 tokens?' });

    expect(fieldOf(dialog, 'Total estimado')).toBe('10,00 cr.');
    expect(posted).toEqual([]);

    await user.click(within(dialog).getByRole('button', { name: 'Confirmar compra' }));

    const result = await screen.findByRole('status', { name: 'resultado de la operación' });
    expect(result).toHaveTextContent('Se compraron 10 tokens por 10,00 cr.');
    expect(result).toHaveTextContent('contraparte: administrador');
    expect(posted).toEqual(['/api/orders/buy']);
    await waitFor(() =>
      expect(balanceInBar()).toHaveTextContent(formatBalance(SEED_BALANCE - SEED_RESERVED_BALANCE - 10)),
    );
    await waitFor(() => {
      const orders = screen.getByRole('list', { name: /^Órdenes con tokens pendientes de / });
      expect(fieldOf(within(orders).getByRole('listitem'), 'Pendiente')).toBe('77');
    });
  });

  it('mientras el administrador tenga tokens avisa cuántos le quedan y no deja vender', async () => {
    openPlayer(MBAPPE.id, 'valid');

    expect(await screen.findByText(/al administrador le quedan 87 tokens de este jugador/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Vender' })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Comprar' })).toBeEnabled();
  });

  it('un jugador inactivo explica por qué no se opera', async () => {
    server.use(http.get('*/api/players/:id', () => HttpResponse.json({ ...SOLD_OUT, active: false })));
    openPlayer(SOLD_OUT.id, 'valid');

    expect(await screen.findByText('No se puede operar: el jugador está inactivo.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Comprar' })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Vender' })).toBeDisabled();
  });

  it('un jugador sin cotización explica por qué no se opera', async () => {
    openPlayer(PLAYER_WITHOUT_QUOTE_ID, 'valid');

    expect(await screen.findByText('No se puede operar: el jugador todavía no tiene cotización.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Comprar' })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Vender' })).toBeDisabled();
  });

  it('un 409 del servidor muestra el motivo en español y no muestra resultado', async () => {
    const { user } = openPlayer(SOLD_OUT.id, 'valid');
    await setQuantity(user, '6');

    await user.click(screen.getByRole('button', { name: 'Vender' }));
    await user.click(screen.getByRole('button', { name: 'Confirmar venta' }));

    expect(await screen.findByRole('alert')).toHaveTextContent(insufficientTokensMessage(5));
    expect(screen.queryByRole('status', { name: 'resultado de la operación' })).toBeNull();
    expect(balanceInBar()).toHaveTextContent(formatBalance(SEED_BALANCE - SEED_RESERVED_BALANCE));
  });

  it('el diálogo toma el foco: Escape cancela sin enviar y Enter confirma', async () => {
    const posted = recordOrders();
    const { user } = openPlayer(MBAPPE.id, 'valid');

    await user.click(await screen.findByRole('button', { name: 'Comprar' }));
    expect(screen.getByRole('button', { name: 'Confirmar compra' })).toHaveFocus();
    await user.keyboard('{Escape}');

    expect(screen.queryByRole('dialog')).toBeNull();
    expect(posted).toEqual([]);

    await user.click(screen.getByRole('button', { name: 'Comprar' }));
    await user.keyboard('{Enter}');

    expect(await screen.findByRole('status', { name: 'resultado de la operación' })).toHaveTextContent(
      'Se compraron 1 token por 1,00 cr.',
    );
    expect(posted).toEqual(['/api/orders/buy']);
  });
});
