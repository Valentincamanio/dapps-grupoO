// Persistencia del tema elegido en window.localStorage: es una preferencia de la persona en ese
// navegador, no de la sesión, y no guarda ningún dato de la cuenta.
import type { Theme } from '../types/board';

const THEME_KEY = 'futbolmarket.theme';

function isTheme(value: unknown): value is Theme {
  return value === 'light' || value === 'dark';
}

// El tema elegido, o null si no se eligió ninguno o el navegador no deja leerlo
export function readTheme(): Theme | null {
  try {
    const value = window.localStorage.getItem(THEME_KEY);
    return isTheme(value) ? value : null;
  } catch {
    return null;
  }
}

// Si el navegador no deja guardarlo, el tema dura hasta recargar la página
export function writeTheme(theme: Theme): void {
  try {
    window.localStorage.setItem(THEME_KEY, theme);
  } catch {
    // Sin almacenamiento no hay nada más que hacer: el tema ya se aplicó en la página
  }
}
