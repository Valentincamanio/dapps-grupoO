// Post-it de un jugador: un único link a su ficha, sin ninguna otra acción.
import type { ElementType, ReactNode } from 'react';
import type { Player, Position } from '../types/api';
import { leagueLabel, positionLabel } from '../utils/positions';
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
}

const COLOR_CLASS: Record<Position, string> = {
  GOALKEEPER: styles.yellow,
  DEFENDER: styles.blue,
  MIDFIELDER: styles.green,
  FORWARD: styles.pink,
};

function DefaultLink({ to, className, draggable, children }: LinkProps) {
  return (
    <a href={to} className={className} draggable={draggable}>
      {children}
    </a>
  );
}

export function PostIt({ player, href, linkState, LinkComponent = DefaultLink }: PostItProps) {
  return (
    <LinkComponent
      to={href}
      state={linkState}
      className={`${styles.postIt} ${COLOR_CLASS[player.position]}`}
      draggable={false}
    >
      <span className={styles.pin} aria-hidden="true" />
      <span className={styles.position}>{positionLabel(player.position)}</span>
      <span className={styles.name}>{player.name}</span>
      <span className={styles.meta}>
        {player.team} · {leagueLabel(player.league)}
      </span>
    </LinkComponent>
  );
}
