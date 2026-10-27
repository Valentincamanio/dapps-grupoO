// Pestañas de la administración: estrategias, recálculo y simulación. Son enlaces sueltos, sin
// listas (como el encabezado); la pantalla actual queda marcada con aria-current.
import type { ElementType, ReactNode } from 'react';
import styles from './AdminNav.module.css';

export type AdminSection = 'strategies' | 'recalculation' | 'simulation';

interface LinkProps {
  to: string;
  className?: string;
  'aria-current'?: 'page';
  children?: ReactNode;
}

interface AdminNavProps {
  current: AdminSection;
  // Por defecto un <a>; la página pasa el Link del router
  LinkComponent?: ElementType;
}

const SECTIONS: { section: AdminSection; to: string; label: string }[] = [
  { section: 'strategies', to: '/admin/estrategias', label: 'Estrategias' },
  { section: 'recalculation', to: '/admin/recalculo', label: 'Recálculo' },
  { section: 'simulation', to: '/admin/simulacion', label: 'Simulación' },
];

function DefaultLink({ to, children, ...rest }: LinkProps) {
  return (
    <a href={to} {...rest}>
      {children}
    </a>
  );
}

export function AdminNav({ current, LinkComponent = DefaultLink }: AdminNavProps) {
  return (
    <nav className={styles.nav} aria-label="administración">
      {SECTIONS.map(({ section, to, label }) => (
        <LinkComponent
          key={section}
          to={to}
          className={`${styles.link} ${section === current ? styles.current : ''}`}
          aria-current={section === current ? 'page' : undefined}
        >
          {label}
        </LinkComponent>
      ))}
    </nav>
  );
}
