// Historia 5 de la 008: los filtros del listado del mercado (FR-047), accesibles con botones.
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { MarketFilters } from '../../src/components/MarketFilters';
import type { MarketFilters as Filters } from '../../src/types/market';

const NO_FILTERS: Filters = { side: null, league: null, team: null, ownerType: null, page: 1 };

function renderFilters(filters: Filters = NO_FILTERS) {
  const callbacks = {
    onTeamsOpen: vi.fn(),
    onTeamsRetry: vi.fn(),
    onSideChange: vi.fn(),
    onLeagueChange: vi.fn(),
    onTeamChange: vi.fn(),
    onOwnerTypeChange: vi.fn(),
  };
  render(<MarketFilters filters={filters} teams={['Real Madrid', 'FC Barcelona']} teamsStatus="success" {...callbacks} />);
  return { ...callbacks, user: userEvent.setup() };
}

function group(name: string): HTMLElement {
  return screen.getByRole('group', { name });
}

describe('MarketFilters', () => {
  it('sin filtros cada grupo marca "todas" o "todos"', () => {
    renderFilters();

    const sides = group('filtrar por tipo de orden');
    const owners = group('filtrar por tipo de usuario dueño');
    const leagues = group('filtrar por liga');

    expect(within(sides).getByRole('button', { name: 'todas' })).toHaveAttribute('aria-pressed', 'true');
    expect(within(owners).getByRole('button', { name: 'todos' })).toHaveAttribute('aria-pressed', 'true');
    expect(within(leagues).getByRole('button', { name: 'todas' })).toHaveAttribute('aria-pressed', 'true');
    expect(screen.getByRole('button', { name: /equipo: todos/ })).toBeInTheDocument();
  });

  it('marca los filtros elegidos', () => {
    renderFilters({ side: 'SELL', league: 'LA_LIGA', team: 'Real Madrid', ownerType: 'ADMIN', page: 2 });

    const sides = group('filtrar por tipo de orden');
    const owners = group('filtrar por tipo de usuario dueño');
    const leagues = group('filtrar por liga');

    expect(within(sides).getByRole('button', { name: 'ventas' })).toHaveAttribute('aria-pressed', 'true');
    expect(within(sides).getByRole('button', { name: 'todas' })).toHaveAttribute('aria-pressed', 'false');
    expect(within(owners).getByRole('button', { name: 'del administrador' })).toHaveAttribute('aria-pressed', 'true');
    expect(within(leagues).getByRole('button', { name: 'La Liga' })).toHaveAttribute('aria-pressed', 'true');
    expect(screen.getByRole('button', { name: /equipo: Real Madrid/ })).toBeInTheDocument();
  });

  it('elegir el tipo de orden avisa con el valor del backend, y "todas" lo quita', async () => {
    const { onSideChange, user } = renderFilters({ ...NO_FILTERS, side: 'SELL' });

    await user.click(within(group('filtrar por tipo de orden')).getByRole('button', { name: 'compras' }));
    await user.click(within(group('filtrar por tipo de orden')).getByRole('button', { name: 'todas' }));

    expect(onSideChange.mock.calls).toEqual([['BUY'], [null]]);
  });

  it('elegir el tipo de usuario dueño avisa con el valor del backend', async () => {
    const { onOwnerTypeChange, user } = renderFilters();

    await user.click(within(group('filtrar por tipo de usuario dueño')).getByRole('button', { name: 'de usuarios' }));

    expect(onOwnerTypeChange).toHaveBeenCalledExactlyOnceWith('USER');
  });

  it('elegir una liga avisa con el valor del backend', async () => {
    const { onLeagueChange, user } = renderFilters();

    await user.click(within(group('filtrar por liga')).getByRole('button', { name: 'Premier League' }));

    expect(onLeagueChange).toHaveBeenCalledExactlyOnceWith('PREMIER');
  });

  it('el equipo se elige con el desplegable de la pizarra', async () => {
    const { onTeamsOpen, onTeamChange, user } = renderFilters();

    await user.click(screen.getByRole('button', { name: /equipo: todos/ }));
    await user.click(screen.getByRole('option', { name: 'FC Barcelona' }));

    expect(onTeamsOpen).toHaveBeenCalled();
    expect(onTeamChange).toHaveBeenCalledExactlyOnceWith('FC Barcelona');
  });
});
