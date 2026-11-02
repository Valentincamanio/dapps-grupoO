import { describe, expect, it } from 'vitest';
import { backLabelOf, topSectionOf } from '../../src/utils/sections';

describe('topSectionOf', () => {
  it.each([
    ['/pizarra', 'players'],
    ['/ranking', 'players'],
    ['/mercado', 'players'],
    ['/portfolio', 'team'],
    ['/ordenes', 'team'],
    ['/operaciones', 'team'],
    ['/admin/recalculo', 'admin'],
  ])('%s pertenece a %s', (pathname, section) => {
    expect(topSectionOf(pathname)).toBe(section);
  });

  it.each(['/vestuario', '/login', '/jugadores/1'])('%s no pertenece a ningún grupo', (pathname) => {
    expect(topSectionOf(pathname)).toBeNull();
  });
});

describe('backLabelOf', () => {
  it('nombra la pantalla a la que se vuelve', () => {
    expect(backLabelOf('/pizarra')).toBe('volver a la pizarra');
    expect(backLabelOf('/portfolio')).toBe('volver a mi equipo');
  });

  it('en una pantalla sin nombre dice solo "volver"', () => {
    expect(backLabelOf('/admin/estrategias')).toBe('volver');
  });
});
