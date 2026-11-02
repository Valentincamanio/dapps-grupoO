// Historia 3 de la 008: el panel para operar desde la ficha (FR-063 y FR-064). Nada se envía sin
// confirmar; la confirmación estima con los tokens del administrador a 1,0000 y el resto al precio
// vigente; después se ve el resultado real o el motivo del rechazo.
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { TradePanel } from '../../src/components/TradePanel';
import type { ApiError } from '../../src/types/api';
import type { OrderExecution } from '../../src/types/market';
import { currentQuoteUsed, insufficientCreditsMessage, playerRef } from '../mocks/market';

const PLAYER_NAME = 'Kylian Mbappé';

interface PanelOptions {
  active?: boolean;
  tokenPrice?: number | null;
  initialPending?: number;
  busy?: boolean;
  result?: OrderExecution;
  error?: ApiError;
}

function renderPanel({
  active = true,
  tokenPrice = 1.03,
  initialPending = 0,
  busy = false,
  result,
  error,
}: PanelOptions = {}) {
  const onBuy = vi.fn(() => Promise.resolve());
  const onSell = vi.fn(() => Promise.resolve());
  render(
    <TradePanel
      playerName={PLAYER_NAME}
      active={active}
      tokenPrice={tokenPrice}
      initialPending={initialPending}
      busy={busy}
      result={result}
      error={error}
      onBuy={onBuy}
      onSell={onSell}
    />,
  );
  return { onBuy, onSell, user: userEvent.setup() };
}

async function typeQuantity(user: ReturnType<typeof userEvent.setup>, quantity: string) {
  const input = screen.getByLabelText('Cantidad de tokens');
  await user.clear(input);
  await user.type(input, quantity);
}

// El detalle de la confirmación: el <dd> de cada etiqueta
function summaryValue(label: string): string {
  const term = within(screen.getByRole('dialog')).getByText(label);
  return term.nextElementSibling?.textContent ?? '';
}

// Una compra de 10 con 3 tokens del administrador: quedan 7 pendientes con su reserva
const PARTIAL_BUY: OrderExecution = {
  order: {
    id: 1000,
    player: playerRef(1),
    side: 'BUY',
    ownerType: 'USER',
    status: 'PARTIAL',
    requestedQuantity: 10,
    executedQuantity: 3,
    pendingQuantity: 7,
    createdAt: '2026-10-28T15:00:00Z',
    cancelledQuantity: 0,
    reservedCredits: 7.21,
  },
  trades: [
    {
      id: 40,
      executedAt: '2026-10-28T15:00:00Z',
      quantity: 3,
      tokenPrice: 1,
      total: 3,
      counterparty: 'ADMIN',
      quote: currentQuoteUsed(1)!,
    },
  ],
  executedQuantity: 3,
  executedTotal: 3,
  balance: 997,
  availableBalance: 989.79,
  reservedBalance: 7.21,
};

const INSUFFICIENT_CREDITS: ApiError = {
  timestamp: '2026-10-28T15:00:00Z',
  status: 409,
  error: 'Conflict',
  message: insufficientCreditsMessage(10, 5),
  path: '/orders/buy',
};

describe('TradePanel', () => {
  it('estima una compra con los tokens del administrador a 1,0000 y el resto al precio vigente', async () => {
    const { user, onBuy } = renderPanel({ initialPending: 3 });

    await typeQuantity(user, '10');
    await user.click(screen.getByRole('button', { name: 'Comprar' }));

    expect(screen.getByRole('dialog', { name: '¿Comprar 10 tokens?' })).toBeInTheDocument();
    expect(summaryValue('Cantidad')).toBe('10 tokens');
    expect(summaryValue('Precio por token')).toBe('3 a 1,0000 cr. (del administrador) y 7 a 1,0300 cr.');
    expect(summaryValue('Total estimado')).toBe('10,21 cr.');
    expect(screen.getByText(/el precio final es el del momento de la ejecución/)).toBeInTheDocument();
    expect(onBuy).not.toHaveBeenCalled();
  });

  it('estima una venta al precio vigente, redondeando como el backend', async () => {
    const { user, onSell } = renderPanel({ tokenPrice: 1.0345 });

    await typeQuantity(user, '10');
    await user.click(screen.getByRole('button', { name: 'Vender' }));

    expect(screen.getByRole('dialog', { name: '¿Vender 10 tokens?' })).toBeInTheDocument();
    expect(summaryValue('Precio por token')).toBe('1,0345 cr.');
    expect(summaryValue('Total estimado')).toBe('10,35 cr.');
    expect(onSell).not.toHaveBeenCalled();
  });

  it('nada se envía sin confirmar: cancelar cierra el diálogo sin operar', async () => {
    const { user, onBuy } = renderPanel();

    await user.click(screen.getByRole('button', { name: 'Comprar' }));
    await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Cancelar' }));

    expect(screen.queryByRole('dialog')).toBeNull();
    expect(onBuy).not.toHaveBeenCalled();
  });

  it('confirmar envía la cantidad una sola vez y cierra el diálogo', async () => {
    const { user, onBuy, onSell } = renderPanel();

    await typeQuantity(user, '4');
    await user.click(screen.getByRole('button', { name: 'Vender' }));
    await user.click(screen.getByRole('button', { name: 'Confirmar venta' }));

    expect(onSell).toHaveBeenCalledExactlyOnceWith(4);
    expect(onBuy).not.toHaveBeenCalled();
    expect(screen.queryByRole('dialog')).toBeNull();
  });

  it('Enter confirma y Escape cancela, con el foco en el diálogo', async () => {
    const { user, onBuy } = renderPanel();

    await user.click(screen.getByRole('button', { name: 'Comprar' }));
    expect(screen.getByRole('button', { name: 'Confirmar compra' })).toHaveFocus();
    await user.keyboard('{Escape}');

    expect(screen.queryByRole('dialog')).toBeNull();
    expect(onBuy).not.toHaveBeenCalled();

    await user.click(screen.getByRole('button', { name: 'Comprar' }));
    await user.keyboard('{Enter}');

    expect(onBuy).toHaveBeenCalledExactlyOnceWith(1);
  });

  it('mientras el administrador tenga tokens avisa cuántos le quedan y no deja vender', () => {
    renderPanel({ initialPending: 87 });

    expect(screen.getByText(/Todavía no se puede vender: al administrador le quedan 87 tokens/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Vender' })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Comprar' })).toBeEnabled();
  });

  it.each([
    [{ active: false }, 'No se puede operar: el jugador está inactivo.'],
    [{ tokenPrice: null }, 'No se puede operar: el jugador todavía no tiene cotización.'],
  ] as const)('un jugador no operable explica por qué y no deja comprar ni vender', (options, message) => {
    renderPanel(options);

    expect(screen.getByText(message)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Comprar' })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Vender' })).toBeDisabled();
  });

  it.each(['0', '101', '1.5'])('con la cantidad %s avisa el rango y no deja operar', async (quantity) => {
    const { user } = renderPanel();

    await typeQuantity(user, quantity);

    expect(screen.getByText('La cantidad tiene que ser un entero entre 1 y 100.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Comprar' })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Vender' })).toBeDisabled();
  });

  it('muestra el resultado real: lo ejecutado, su precio, el total y el remanente pendiente', () => {
    renderPanel({ result: PARTIAL_BUY });

    const result = screen.getByRole('status', { name: 'resultado de la operación' });

    expect(result).toHaveTextContent('Se compraron 3 tokens por 3,00 cr.');
    expect(result).toHaveTextContent('3 tokens a 1,0000 cr. por 3,00 cr., contraparte: administrador');
    expect(result).toHaveTextContent('Quedaron 7 tokens pendientes en tu orden de compra.');
  });

  it('una orden que no se cruzó con nada dice que quedó toda pendiente', () => {
    const pendingSale: OrderExecution = {
      ...PARTIAL_BUY,
      order: { ...PARTIAL_BUY.order, side: 'SELL', status: 'PENDING', executedQuantity: 0, pendingQuantity: 10 },
      trades: [],
      executedQuantity: 0,
      executedTotal: 0,
    };

    renderPanel({ result: pendingSale });

    const result = screen.getByRole('status', { name: 'resultado de la operación' });
    expect(result).toHaveTextContent('No se ejecutó nada en el acto.');
    expect(result).toHaveTextContent('Quedaron 10 tokens pendientes en tu orden de venta.');
  });

  it('un rechazo muestra el motivo en español y no da la operación por hecha', () => {
    renderPanel({ error: INSUFFICIENT_CREDITS });

    expect(screen.getByRole('alert')).toHaveTextContent(
      'No se hizo la operación. Saldo insuficiente: se necesitan 10,00 créditos y el saldo libre es 5,00.',
    );
    expect(screen.queryByRole('status', { name: 'resultado de la operación' })).toBeNull();
  });
});
