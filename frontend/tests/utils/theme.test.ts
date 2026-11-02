import { describe, expect, it } from 'vitest';
import { effectiveTheme, oppositeTheme } from '../../src/utils/theme';

describe('effectiveTheme', () => {
  it('sin elegir, sigue al sistema', () => {
    const dark = effectiveTheme(null, true);
    const light = effectiveTheme(null, false);

    expect(dark).toBe('dark');
    expect(light).toBe('light');
  });

  it('lo elegido gana sobre el sistema', () => {
    const chosenLight = effectiveTheme('light', true);
    const chosenDark = effectiveTheme('dark', false);

    expect(chosenLight).toBe('light');
    expect(chosenDark).toBe('dark');
  });
});

describe('oppositeTheme', () => {
  it('el botón lleva al otro tema', () => {
    const fromLight = oppositeTheme('light');
    const fromDark = oppositeTheme('dark');

    expect(fromLight).toBe('dark');
    expect(fromDark).toBe('light');
  });
});
