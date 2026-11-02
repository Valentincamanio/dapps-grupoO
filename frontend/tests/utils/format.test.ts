import { describe, expect, it } from 'vitest';
import { hueOf, initialsOf } from '../../src/utils/format';

describe('initialsOf', () => {
  it('toma la inicial de las dos primeras palabras, en mayúscula', () => {
    expect(initialsOf('Kylian Mbappé')).toBe('KM');
    expect(initialsOf('virgil van dijk')).toBe('VV');
  });

  it('con una sola palabra da una inicial', () => {
    expect(initialsOf('Pedri')).toBe('P');
  });

  it('ignora los espacios de más y no falla con un texto vacío', () => {
    expect(initialsOf('  Lionel   Messi ')).toBe('LM');
    expect(initialsOf('')).toBe('');
  });
});

describe('hueOf', () => {
  it('da siempre el mismo tono para el mismo texto', () => {
    expect(hueOf('Real Madrid')).toBe(hueOf('Real Madrid'));
  });

  it('da un tono entre 0 y 359', () => {
    ['', 'Arsenal', 'Bayern München', 'Paris Saint-Germain'].forEach((team) => {
      expect(hueOf(team)).toBeGreaterThanOrEqual(0);
      expect(hueOf(team)).toBeLessThan(360);
    });
  });
});
