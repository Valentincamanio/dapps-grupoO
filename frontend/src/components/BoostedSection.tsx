// Los potenciados de la semana arriba de la pizarra: el título, la semana, qué es un potenciado,
// cuánto falta para el cierre y las tarjetas, que arma la página.
import { useId, type ReactNode } from 'react';
import type { BoostedWeek } from '../types/quotes';
import { boostWeekRange } from '../utils/boosts';
import { BoltIcon } from './BoltIcon';
import styles from './BoostedSection.module.css';

interface BoostedSectionProps {
  week: BoostedWeek;
  // Lo que falta para el cierre, ya formateado
  countdown: string;
  // Un <li> por potenciado
  children: ReactNode;
}

export function BoostedSection({ week, countdown, children }: BoostedSectionProps) {
  const titleId = useId();
  const tipId = useId();
  return (
    <section className={styles.section} aria-labelledby={titleId}>
      <div className={styles.head}>
        <h2 id={titleId} className={styles.title}>
          <BoltIcon size={20} />
          Potenciados de la semana
        </h2>
        <span className={styles.week}>{boostWeekRange(week.weekStart, week.weekEnd)}</span>
        <button type="button" className={styles.info} aria-label="qué es un potenciado" aria-describedby={tipId}>
          ?
          <span id={tipId} role="tooltip" className={styles.tip}>
            El administrador elige 4 jugadores por semana. En el cierre del domingo, su factor de escala y
            su volatilidad se multiplican por el porcentaje que tienen (de ×2 a ×4), así su cotización sube o
            baja mucho más que la del resto. La cotización actual todavía no lo lleva.
          </span>
        </button>
        <span className={styles.ends}>
          <svg
            viewBox="0 0 24 24"
            width="14"
            height="14"
            fill="none"
            stroke="currentColor"
            strokeWidth="2.4"
            aria-hidden="true"
            focusable="false"
          >
            <circle cx="12" cy="13" r="8" />
            <path d="M12 9v4l2.5 2M9 2h6" />
          </svg>
          cierra en {countdown}
        </span>
      </div>
      <ul className={styles.grid}>{children}</ul>
    </section>
  );
}
