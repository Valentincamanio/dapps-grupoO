// Encabezado de un grupo de pantallas: el título y sus pestañas. Son enlaces sueltos, sin listas (como
// la barra superior); la pantalla actual queda marcada con aria-current.
import type { ElementType, ReactNode } from 'react';
import type { Section } from '../utils/sections';
import styles from './SectionNav.module.css';

interface LinkProps {
  to: string;
  className?: string;
  'aria-current'?: 'page';
  children?: ReactNode;
}

interface SectionNavProps {
  title?: string;
  // Nombre accesible de las pestañas
  label: string;
  sections: Section[];
  // La dirección de la pantalla actual
  current: string;
  // Por defecto un <a>; la página pasa el Link del router
  LinkComponent?: ElementType;
}

function DefaultLink({ to, children, ...rest }: LinkProps) {
  return (
    <a href={to} {...rest}>
      {children}
    </a>
  );
}

export function SectionNav({ title, label, sections, current, LinkComponent = DefaultLink }: SectionNavProps) {
  return (
    <div className={styles.head}>
      {title ? <p className={styles.title}>{title}</p> : null}
      <nav className={styles.nav} aria-label={label}>
        {sections.map(({ to, label: sectionLabel }) => (
          <LinkComponent
            key={to}
            to={to}
            className={`${styles.link} ${to === current ? styles.current : ''}`}
            aria-current={to === current ? 'page' : undefined}
          >
            {sectionLabel}
          </LinkComponent>
        ))}
      </nav>
    </div>
  );
}
