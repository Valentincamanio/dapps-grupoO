// Historia 8 de la 007: la tabla de la simulación, con la diferencia con signo y texto (FR-059).
import { render, screen, within } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { SimulationTable } from '../../src/components/SimulationTable';
import type { SimulationRow } from '../../src/types/quotes';
import { leagueLabel, positionLabel } from '../../src/utils/positions';
import { formatCredits, formatSignedCredits } from '../../src/utils/quotes';

const BASE: Omit<SimulationRow, 'playerId' | 'name' | 'currentValue' | 'simulatedValue' | 'difference'> = {
  team: 'Arsenal',
  league: 'PREMIER',
  position: 'FORWARD',
};

const ROWS: SimulationRow[] = [
  { ...BASE, playerId: 1, name: 'Sube', currentValue: 100, simulatedValue: 103, difference: 3 },
  { ...BASE, playerId: 2, name: 'Baja', currentValue: 105.5, simulatedValue: 103.25, difference: -2.25 },
  { ...BASE, playerId: 3, name: 'Igual', currentValue: 90, simulatedValue: 90, difference: 0 },
  { ...BASE, playerId: 4, name: 'Nuevo', currentValue: null, simulatedValue: 98, difference: -2 },
];

function rowOf(name: string): HTMLElement {
  return screen.getByRole('rowheader', { name }).closest('tr') as HTMLElement;
}

function cells(name: string): string[] {
  return within(rowOf(name))
    .getAllByRole('cell')
    .map((cell) => cell.textContent ?? '');
}

describe('SimulationTable', () => {
  it('muestra por jugador equipo, liga, posición, la cotización actual, la simulada y la diferencia', () => {
    render(<SimulationTable rows={ROWS} />);

    expect(within(screen.getByRole('table')).getAllByRole('row')).toHaveLength(ROWS.length + 1);
    expect(cells('Sube')).toEqual([
      'Arsenal',
      leagueLabel('PREMIER'),
      positionLabel('FORWARD'),
      formatCredits(100),
      formatCredits(103),
      `▲${formatSignedCredits(3)}sube`,
    ]);
  });

  it('la diferencia lleva signo y texto, no solo color', () => {
    render(<SimulationTable rows={ROWS} />);

    expect(formatSignedCredits(3)).toBe('+3,00 cr.');
    expect(within(rowOf('Baja')).getByText(formatSignedCredits(-2.25))).toBeInTheDocument();
    expect(within(rowOf('Baja')).getByText('baja')).toBeInTheDocument();
    expect(within(rowOf('Igual')).getByText('0,00 cr.')).toBeInTheDocument();
    expect(within(rowOf('Igual')).getByText('sin cambios')).toBeInTheDocument();
  });

  it('un jugador sin cotización actual lo dice y se compara contra 100,00', () => {
    render(<SimulationTable rows={ROWS} />);

    expect(within(rowOf('Nuevo')).getByText('sin cotización')).toBeInTheDocument();
    expect(within(rowOf('Nuevo')).getByText('(contra 100,00 cr.)')).toBeInTheDocument();
  });

  it('no tiene botones ni enlaces', () => {
    render(<SimulationTable rows={ROWS} />);

    expect(screen.queryByRole('button')).toBeNull();
    expect(screen.queryByRole('link')).toBeNull();
  });
});
