// Hoja de scout clavada al pizarrón: datos de un jugador, sin ninguna acción.
import type { ReactNode } from 'react';
import type { Player } from '../types/api';
import { leagueLabel, positionLabel } from '../utils/positions';
import styles from './ScoutSheet.module.css';

interface ScoutSheetProps {
  player: Player;
  backLink: ReactNode;
  // Espacio para la cotización y su evolución; sin él, la hoja queda como antes
  quoteSlot?: ReactNode;
}

export function ScoutSheet({ player, backLink, quoteSlot }: ScoutSheetProps) {
  return (
    <article className={quoteSlot ? `${styles.sheet} ${styles.wide}` : styles.sheet}>
      <span className={styles.pin} aria-hidden="true" />
      <h1 className={styles.name}>{player.name}</h1>
      <dl className={styles.data}>
        <div>
          <dt>Posición</dt>
          <dd>{positionLabel(player.position)}</dd>
        </div>
        <div>
          <dt>Equipo</dt>
          <dd>{player.team}</dd>
        </div>
        <div>
          <dt>Liga</dt>
          <dd>{leagueLabel(player.league)}</dd>
        </div>
      </dl>
      {quoteSlot ? <div className={styles.quote}>{quoteSlot}</div> : null}
      <div className={styles.back}>{backLink}</div>
    </article>
  );
}
