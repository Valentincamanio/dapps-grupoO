import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { PostIt } from '../../src/components/PostIt';
import type { Player } from '../../src/types/api';

const PLAYER: Player = {
  id: 7,
  name: 'Rúben Dias',
  position: 'DEFENDER',
  team: 'Real Madrid',
  league: 'LA_LIGA',
};

describe('PostIt', () => {
  it('muestra nombre, posición, equipo y liga', () => {
    render(<PostIt player={PLAYER} href="/jugadores/7" />);

    expect(screen.getByText('Rúben Dias')).toBeInTheDocument();
    expect(screen.getByText('Defensor')).toBeInTheDocument();
    expect(screen.getByText(/Real Madrid/)).toBeInTheDocument();
    expect(screen.getByText(/La Liga/)).toBeInTheDocument();
  });

  it('es un único link que apunta al href', () => {
    render(<PostIt player={PLAYER} href="/jugadores/7" />);

    const links = screen.getAllByRole('link');

    expect(links).toHaveLength(1);
    expect(links[0]).toHaveAttribute('href', '/jugadores/7');
  });

  it('no contiene botones ni textos de compra, venta, plantel, precio o valor', () => {
    const { container } = render(<PostIt player={PLAYER} href="/jugadores/7" />);

    const text = container.textContent?.toLowerCase() ?? '';

    expect(screen.queryAllByRole('button')).toHaveLength(0);
    expect(text).not.toMatch(/compr|vend|plantel|precio|valor/);
  });

  it('no es arrastrable', () => {
    render(<PostIt player={PLAYER} href="/jugadores/7" />);

    const link = screen.getByRole('link');

    expect(link).toHaveAttribute('draggable', 'false');
  });
});
