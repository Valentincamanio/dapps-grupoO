// Botón de la barra que cambia entre el tema claro y el oscuro. Muestra solo un ícono, la luna en el
// claro y el sol en el oscuro, y al pasar el mouse o enfocarlo, el cartel "Cambiar tema".
import type { Theme } from '../types/board';
import styles from './ThemeToggle.module.css';

interface ThemeToggleProps {
  theme: Theme;
  onToggle: () => void;
}

const MOON = 'M20 14.5A8.5 8.5 0 0 1 9.5 4a8.5 8.5 0 1 0 10.5 10.5Z';
const SUN =
  'M12 16a4 4 0 1 0 0-8 4 4 0 0 0 0 8ZM12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4';

export function ThemeToggle({ theme, onToggle }: ThemeToggleProps) {
  const label = theme === 'dark' ? 'Cambiar tema: pasar al claro' : 'Cambiar tema: pasar al oscuro';
  return (
    <button type="button" className={styles.toggle} aria-label={label} onClick={onToggle}>
      <svg
        viewBox="0 0 24 24"
        width="20"
        height="20"
        fill="none"
        stroke="currentColor"
        strokeWidth="2"
        strokeLinecap="round"
        strokeLinejoin="round"
        aria-hidden="true"
        focusable="false"
      >
        <path d={theme === 'dark' ? SUN : MOON} />
      </svg>
      <span className={styles.tooltip} aria-hidden="true">
        Cambiar tema
      </span>
    </button>
  );
}
