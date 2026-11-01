// Qué tema se ve y a cuál lleva el botón de la barra.
import type { Theme } from '../types/board';

// El elegido; sin elegir, el del sistema
export function effectiveTheme(chosen: Theme | null, systemPrefersDark: boolean): Theme {
  if (chosen) return chosen;
  return systemPrefersDark ? 'dark' : 'light';
}

export function oppositeTheme(theme: Theme): Theme {
  return theme === 'dark' ? 'light' : 'dark';
}
