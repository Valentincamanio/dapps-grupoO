// Tarjeta de un jugador: un único link a su ficha, sin ninguna otra acción.
import type { CSSProperties, ElementType, ReactNode } from 'react';
import type { Player } from '../types/api';
import type { QuoteBadge } from '../types/quotes';
import { hueOf, initialsOf } from '../utils/format';
import { leagueLabel, positionLabel } from '../utils/positions';
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
}: PostItProps) {
  return (
    <LinkComponent
      to={href}
      state={linkState}
      className={styles.postIt}
      draggable={false}
    >
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
      {quote ? <QuoteTag badge={quote} /> : null}
    </LinkComponent>
  );
}
