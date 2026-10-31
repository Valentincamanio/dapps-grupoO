// Encabezado de la ficha: datos de un jugador y salida, sin ninguna acción.
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
      <div className={styles.back}>{backLink}</div>
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
    </article>
  );
}
