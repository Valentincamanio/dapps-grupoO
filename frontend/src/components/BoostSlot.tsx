// Uno de los cuatro lugares de potenciados de una semana: vacío, o con el jugador, su cotización
// actual y su porcentaje. Si la semana se puede cambiar, el porcentaje se elige con un deslizador y
// el jugador se puede quitar; si no, solo se lee.
import { useId, type CSSProperties } from 'react';
import type { Player } from '../types/api';
import type { QuoteBadge } from '../types/quotes';
import {
  BOOST_PERCENT_STEP,
  formatBoostPercent,
  formatMultiplier,
  MAX_BOOST_PERCENT,
  MIN_BOOST_PERCENT,
} from '../utils/boosts';
import { hueOf, initialsOf } from '../utils/format';
import { positionLabel } from '../utils/positions';
import { formatCredits } from '../utils/quotes';
import { BoltIcon } from './BoltIcon';
import styles from './BoostSlot.module.css';

export interface BoostEntry {
  player: Player;
  percent: number;
}

interface BoostSlotProps {
  // Desde 0
  index: number;
  entry: BoostEntry | null;
  quote?: QuoteBadge;
  editable: boolean;
  // El primer lugar vacío, el que se llena con el próximo jugador elegido
  isNext?: boolean;
  onPercentChange?: (percent: number) => void;
  onRemove?: () => void;
}

function quoteText(quote: QuoteBadge | undefined): string {
  if (!quote || quote.status === 'loading') return '...';
  return quote.status === 'ready' ? formatCredits(quote.quote.value) : 'sin cotización';
}

export function BoostSlot({
  index,
  entry,
  quote,
  editable,
  isNext = false,
  onPercentChange,
  onRemove,
}: BoostSlotProps) {
  const sliderId = useId();
  if (!entry) {
    return (
      <li className={`${styles.slot} ${styles.empty} ${isNext ? styles.next : ''}`}>
        <b className={styles.number}>{index + 1}</b>
        {editable ? (
          <span>
            lugar libre
            <br />
            elegí un jugador de la lista
          </span>
        ) : (
          <span>sin jugador</span>
        )}
      </li>
    );
  }

  const { player, percent } = entry;
  return (
    <li className={`${styles.slot} ${styles.gold}`} aria-label={`potenciado ${index + 1}: ${player.name}`}>
      <span className={styles.mult}>
        <BoltIcon size={13} />
        {formatMultiplier(percent)}
      </span>
      <div className={styles.who}>
        <span
          className={styles.avatar}
          style={{ '--hue': hueOf(player.team) } as CSSProperties}
          data-initials={initialsOf(player.name)}
          aria-hidden="true"
        />
        <span className={styles.name}>{player.name}</span>
        <span className={styles.meta}>
          {positionLabel(player.position)} · {player.team}
        </span>
      </div>
      <p className={styles.now}>
        <span>cotización actual</span>
        <b>{quoteText(quote)}</b>
      </p>
      {editable ? (
        <>
          <div className={styles.range}>
            <label htmlFor={sliderId}>
              multiplicador
              <b>
                {formatBoostPercent(percent)} · {formatMultiplier(percent)}
              </b>
            </label>
            <input
              id={sliderId}
              type="range"
              min={MIN_BOOST_PERCENT}
              max={MAX_BOOST_PERCENT}
              step={BOOST_PERCENT_STEP}
              value={percent}
              // Cuánto del riel se pinta: de 0 en el mínimo a 1 en el máximo
              style={{ '--progress': (percent - MIN_BOOST_PERCENT) / (MAX_BOOST_PERCENT - MIN_BOOST_PERCENT) } as CSSProperties}
              aria-valuetext={formatBoostPercent(percent)}
              onChange={(event) => onPercentChange?.(Number(event.target.value))}
            />
            <div className={styles.scale} aria-hidden="true">
              <span>{formatBoostPercent(MIN_BOOST_PERCENT)}</span>
              <span>{formatBoostPercent(MAX_BOOST_PERCENT)}</span>
            </div>
          </div>
          <button type="button" className={styles.remove} aria-label={`quitar a ${player.name}`} onClick={onRemove}>
            quitar
          </button>
        </>
      ) : (
        <p className={styles.readOnly}>
          factor de escala y volatilidad <b>{formatBoostPercent(percent)}</b>
        </p>
      )}
    </li>
  );
}
