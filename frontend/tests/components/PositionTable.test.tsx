// Historia 2 de la 008: las posiciones del portfolio, con cada campo de FR-036, guiones para lo que
// no se informa y la ganancia o pérdida con signo y texto.
import { render, screen, within } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { PositionTable } from '../../src/components/PositionTable';
import type { PortfolioPosition } from '../../src/types/market';
import { CURRENT_PORTFOLIO } from '../mocks/market';

const [FIRST, SECOND] = CURRENT_PORTFOLIO.positions;

function rowOf(playerName: string): HTMLElement {
  const header = screen.getByRole('rowheader', { name: new RegExp(playerName) });
  const row = header.closest('tr');
  if (!row) throw new Error(`No hay una fila para ${playerName}`);
  return row;
}

function cell(row: HTMLElement, label: string): HTMLElement {
  const found = row.querySelector(`[data-label="${label}"]`);
  if (!(found instanceof HTMLElement)) throw new Error(`No hay una celda ${label}`);
  return found;
}

// La posición del superusuario: sin datos de costo ni comprometidos
function superuserPosition(): PortfolioPosition {
  return {
    ...FIRST,
    tokens: 90,
    sharePercent: 90,
    committed: undefined,
    averagePrice: null,
    invested: null,
    value: 92.7,
    profit: null,
    profitPercent: null,
  };
}

describe('PositionTable', () => {
  it('muestra cada campo de una posición', () => {
    render(<PositionTable positions={[FIRST]} />);

    const row = rowOf(FIRST.player.name);
    expect(cell(row, 'Tokens')).toHaveTextContent('10');
    expect(cell(row, 'Participación')).toHaveTextContent('10 %');
    expect(cell(row, 'Comprometidos')).toHaveTextContent('0');
    expect(cell(row, 'Precio promedio')).toHaveTextContent('1,0000 cr.');
    expect(cell(row, 'Invertido')).toHaveTextContent('10,00 cr.');
    expect(cell(row, 'Precio por token')).toHaveTextContent('1,0300 cr.');
    expect(cell(row, 'Score')).toHaveTextContent('0,30');
    expect(cell(row, 'Valor')).toHaveTextContent('10,30 cr.');
    expect(cell(row, 'Ganancia')).toHaveTextContent('+0,30 gana');
    expect(cell(row, 'Rentabilidad')).toHaveTextContent('+3,00 %');
  });

  it('muestra una posición por jugador con sus comprometidos', () => {
    render(<PositionTable positions={CURRENT_PORTFOLIO.positions} />);

    expect(screen.getAllByRole('rowheader')).toHaveLength(2);
    const row = rowOf(SECOND.player.name);
    expect(cell(row, 'Comprometidos')).toHaveTextContent('5');
    expect(cell(row, 'Precio promedio')).toHaveTextContent('1,0100 cr.');
    expect(cell(row, 'Invertido')).toHaveTextContent('10,10 cr.');
  });

  it('una pérdida se lee con el signo y el texto, sin depender del color', () => {
    const losing: PortfolioPosition = { ...FIRST, value: 9, profit: -1, profitPercent: -10 };

    render(<PositionTable positions={[losing]} />);

    const row = rowOf(FIRST.player.name);
    expect(cell(row, 'Ganancia')).toHaveTextContent('-1,00 pierde');
    expect(cell(row, 'Rentabilidad')).toHaveTextContent('-10,00 %');
  });

  it('lo que no se informa muestra un guion que se lee "sin datos"', () => {
    render(<PositionTable positions={[superuserPosition()]} />);

    const row = rowOf(FIRST.player.name);
    for (const label of ['Comprometidos', 'Precio promedio', 'Invertido', 'Ganancia', 'Rentabilidad']) {
      expect(within(cell(row, label)).getByText('—')).toHaveAttribute('aria-hidden', 'true');
      expect(within(cell(row, label)).getByText('sin datos')).toBeInTheDocument();
    }
    expect(cell(row, 'Valor')).toHaveTextContent('92,70 cr.');
  });

  it('marca al jugador que ya no está activo', () => {
    const inactive: PortfolioPosition = { ...FIRST, player: { ...FIRST.player, active: false } };

    render(<PositionTable positions={[inactive]} />);

    expect(rowOf(FIRST.player.name)).toHaveTextContent('(inactivo)');
  });

  it('sin posiciones muestra el mensaje de vacío y ninguna tabla', () => {
    render(<PositionTable positions={[]} />);

    expect(screen.getByText('todavía no tenés tokens de ningún jugador.')).toBeInTheDocument();
    expect(screen.queryByRole('table')).toBeNull();
  });

  it('no muestra símbolos de moneda', () => {
    const { container } = render(<PositionTable positions={CURRENT_PORTFOLIO.positions} />);

    expect(container.textContent).not.toMatch(/[$€]|\bUSD\b|\bEUR\b/);
  });
});
