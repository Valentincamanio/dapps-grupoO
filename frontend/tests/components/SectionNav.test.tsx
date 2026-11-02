import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { SectionNav } from '../../src/components/SectionNav';
import { PLAYER_SECTIONS } from '../../src/utils/sections';

describe('SectionNav', () => {
  it('muestra el título y un enlace por sección', () => {
    render(<SectionNav title="Jugadores" label="pestañas" sections={PLAYER_SECTIONS} current="/pizarra" />);

    expect(screen.getByText('Jugadores')).toBeInTheDocument();
    expect(screen.getByRole('navigation', { name: 'pestañas' })).toBeInTheDocument();
    expect(screen.getAllByRole('link')).toHaveLength(PLAYER_SECTIONS.length);
    expect(screen.getByRole('link', { name: 'Ranking' })).toHaveAttribute('href', '/ranking');
  });

  it('marca solo la sección actual', () => {
    render(<SectionNav label="pestañas" sections={PLAYER_SECTIONS} current="/mercado" />);

    expect(screen.getByRole('link', { name: 'Mercado' })).toHaveAttribute('aria-current', 'page');
    expect(screen.getByRole('link', { name: 'Todos' })).not.toHaveAttribute('aria-current');
  });

  it('sin una sección actual válida no marca ninguna', () => {
    render(<SectionNav label="pestañas" sections={PLAYER_SECTIONS} current="/otra" />);

    expect(screen.queryByRole('link', { current: 'page' })).toBeNull();
  });
});
