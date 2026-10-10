import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { Pitch } from '../../src/components/Pitch';

const ZONE_NAMES = [
  'Filtrar por arqueros',
  'Filtrar por defensores',
  'Filtrar por mediocampistas',
  'Filtrar por delanteros',
];

describe('Pitch', () => {
  it('cada zona es un botón con nombre accesible, más "Ver todos"', () => {
    render(<Pitch activeZone={null} onZoneSelect={() => {}} />);

    for (const name of ZONE_NAMES) {
      expect(screen.getByRole('button', { name })).toBeInTheDocument();
    }
    expect(screen.getByRole('button', { name: 'Ver todos' })).toBeInTheDocument();
  });

  it('sin zona activa solo "todos" está presionado', () => {
    render(<Pitch activeZone={null} onZoneSelect={() => {}} />);

    expect(screen.getByRole('button', { name: 'Ver todos' })).toHaveAttribute('aria-pressed', 'true');
    for (const name of ZONE_NAMES) {
      expect(screen.getByRole('button', { name })).toHaveAttribute('aria-pressed', 'false');
    }
  });

  it('la zona activa tiene aria-pressed="true" y "todos" no', () => {
    render(<Pitch activeZone="DEFENSE" onZoneSelect={() => {}} />);

    expect(screen.getByRole('button', { name: 'Filtrar por defensores' })).toHaveAttribute(
      'aria-pressed',
      'true',
    );
    expect(screen.getByRole('button', { name: 'Filtrar por delanteros' })).toHaveAttribute(
      'aria-pressed',
      'false',
    );
    expect(screen.getByRole('button', { name: 'Ver todos' })).toHaveAttribute('aria-pressed', 'false');
  });

  it('tocar una zona llama onZoneSelect con ella', async () => {
    const onZoneSelect = vi.fn();
    const user = userEvent.setup();
    render(<Pitch activeZone={null} onZoneSelect={onZoneSelect} />);

    await user.click(screen.getByRole('button', { name: 'Filtrar por delanteros' }));

    expect(onZoneSelect).toHaveBeenCalledWith('ATTACK');
  });

  it('tocar la zona activa llama onZoneSelect(null)', async () => {
    const onZoneSelect = vi.fn();
    const user = userEvent.setup();
    render(<Pitch activeZone="DEFENSE" onZoneSelect={onZoneSelect} />);

    await user.click(screen.getByRole('button', { name: 'Filtrar por defensores' }));

    expect(onZoneSelect).toHaveBeenCalledWith(null);
  });

  it('tocar "todos" llama onZoneSelect(null)', async () => {
    const onZoneSelect = vi.fn();
    const user = userEvent.setup();
    render(<Pitch activeZone="GOAL" onZoneSelect={onZoneSelect} />);

    await user.click(screen.getByRole('button', { name: 'Ver todos' }));

    expect(onZoneSelect).toHaveBeenCalledWith(null);
  });

  it('se activa con teclado: Tab + Enter y Espacio', async () => {
    const onZoneSelect = vi.fn();
    const user = userEvent.setup();
    render(<Pitch activeZone={null} onZoneSelect={onZoneSelect} />);

    await user.tab();
    expect(screen.getByRole('button', { name: 'Filtrar por arqueros' })).toHaveFocus();
    await user.keyboard('{Enter}');
    await user.tab();
    await user.keyboard(' ');

    expect(onZoneSelect).toHaveBeenNthCalledWith(1, 'GOAL');
    expect(onZoneSelect).toHaveBeenNthCalledWith(2, 'DEFENSE');
  });

  it('no dibuja ningún jugador: solo botones de zona y "todos"', () => {
    render(<Pitch activeZone={null} onZoneSelect={() => {}} />);

    expect(screen.getAllByRole('button')).toHaveLength(5);
    expect(screen.queryByRole('link')).not.toBeInTheDocument();
    expect(screen.queryByRole('listitem')).not.toBeInTheDocument();
  });
});
