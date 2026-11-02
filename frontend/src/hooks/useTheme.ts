// El tema de la app: arranca siguiendo al sistema y, cuando la persona lo cambia con el botón de la
// barra, queda fijo en ese navegador. El tema se aplica con data-theme en <html> (tokens.css).
import { useCallback, useState, useSyncExternalStore } from 'react';
import { readTheme, writeTheme } from '../services/themeStorage';
import type { Theme } from '../types/board';
import { effectiveTheme, oppositeTheme } from '../utils/theme';

const DARK_QUERY = '(prefers-color-scheme: dark)';

// jsdom y algunos navegadores viejos no tienen matchMedia: ahí el sistema cuenta como claro
function darkQuery(): MediaQueryList | null {
  return typeof window.matchMedia === 'function' ? window.matchMedia(DARK_QUERY) : null;
}

function subscribeToSystem(listener: () => void): () => void {
  const query = darkQuery();
  query?.addEventListener('change', listener);
  return () => query?.removeEventListener('change', listener);
}

function systemPrefersDark(): boolean {
  return darkQuery()?.matches ?? false;
}

function applyTheme(theme: Theme | null): void {
  if (theme) {
    document.documentElement.dataset.theme = theme;
  } else {
    delete document.documentElement.dataset.theme;
  }
}

// Antes de dibujar la app, para que no parpadee el tema del sistema
export function applyStoredTheme(): void {
  applyTheme(readTheme());
}

export function useTheme(): { theme: Theme; toggle: () => void } {
  const [chosen, setChosen] = useState<Theme | null>(readTheme);
  const systemDark = useSyncExternalStore(subscribeToSystem, systemPrefersDark, () => false);
  const theme = effectiveTheme(chosen, systemDark);

  const toggle = useCallback(() => {
    const next = oppositeTheme(theme);
    writeTheme(next);
    applyTheme(next);
    setChosen(next);
  }, [theme]);

  return { theme, toggle };
}
