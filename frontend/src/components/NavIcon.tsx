// Ícono de una sección de la barra; es decoración: el nombre lo da el texto del enlace.
import type { TopSection } from '../utils/sections';

const PATHS: Record<TopSection, string> = {
  players:
    'M9 11.5a3.5 3.5 0 1 0 0-7 3.5 3.5 0 0 0 0 7ZM2.5 20c.8-3.6 3.4-5.5 6.5-5.5s5.7 1.9 6.5 5.5M17 11.5a2.5 2.5 0 1 0 0-5M17 14c2.4 0 4 1.5 4.6 4.5',
  team: 'M3 7h18v12a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1ZM8 7V5a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2M3 12h18',
  admin: 'M4 21v-7M4 10V3M12 21v-9M12 8V3M20 21v-5M20 12V3M1 14h6M9 8h6M17 16h6',
};

export function NavIcon({ section }: { section: TopSection }) {
  return (
    <svg
      viewBox="0 0 24 24"
      width="20"
      height="20"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      aria-hidden="true"
      focusable="false"
    >
      <path d={PATHS[section]} />
    </svg>
  );
}
