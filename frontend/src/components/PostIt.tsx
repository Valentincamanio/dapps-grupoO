// Tarjeta de un jugador: un único link a su ficha, sin ninguna otra acción. Un potenciado se ve
// dorado, con la cinta del multiplicador y la barra de volatilidad que rige en el cierre.
import type { CSSProperties, ElementType, ReactNode } from 'react';
import type { Player } from '../types/api';
import type { QuoteBadge } from '../types/quotes';
import { boostMeterWidth, formatBoostPercent, formatMultiplier } from '../utils/boosts';
import { hueOf, initialsOf } from '../utils/format';
import { leagueLabel, positionLabel } from '../utils/positions';
import { BoltIcon } from './BoltIcon';
import { QuoteTag } from './QuoteTag';
import styles from './PostIt.module.css';

interface LinkProps {
  to: string;
  state?: unknown;
  className?: string;
  draggable?: boolean;
  children?: ReactNode;
}

interface PostItProps {
  player: Player;
  href: string;
  linkState?: unknown;
  // Por defecto un <a>; la página pasa el Link del router (research R-9)
  LinkComponent?: ElementType;
  // Cotización actual; sin ella el post-it queda igual que antes de las cotizaciones
  quote?: QuoteBadge;
  // El porcentaje de un potenciado de la semana (300 es ×3)
  boostPercent?: number;
}

function DefaultLink({ to, className, draggable, children }: LinkProps) {
  return (
    <a href={to} className={className} draggable={draggable}>
      {children}
    </a>
  );
}

export function PostIt({
  player,
  href,
  linkState,
  LinkComponent = DefaultLink,
  quote,
  boostPercent,
}: PostItProps) {
  return (
    <LinkComponent
      to={href}
      state={linkState}
      className={boostPercent ? `${styles.postIt} ${styles.boosted}` : styles.postIt}
      draggable={false}
    >
      {boostPercent ? (
        <span className={styles.ribbon}>
          <BoltIcon size={13} />
          {formatMultiplier(boostPercent)}
        </span>
      ) : null}
      {/* Las iniciales van por CSS: son decoración y no son parte del texto del enlace */}
      <span
        className={styles.avatar}
        style={{ '--hue': hueOf(player.team) } as CSSProperties}
        data-initials={initialsOf(player.name)}
        aria-hidden="true"
      />
      <span className={styles.name}>{player.name}</span>
      <span className={styles.position}>{positionLabel(player.position)}</span>
      <span className={styles.meta}>
        {player.team} · {leagueLabel(player.league)}
      </span>
      {quote ? <QuoteTag badge={quote} label={boostPercent ? 'cotización actual' : undefined} /> : null}
      {boostPercent ? (
        <span className={styles.boost}>
          volatilidad al cierre
          <span className={styles.meter} aria-hidden="true">
            <span style={{ width: `${boostMeterWidth(boostPercent)}%` }} />
          </span>
          <b>{formatBoostPercent(boostPercent)}</b>
        </span>
      ) : null}
    </LinkComponent>
  );
}
