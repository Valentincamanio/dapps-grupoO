// Un registro de la auditoría: qué pasó, cuándo, quién lo hizo y sobre qué jugador, el detalle de la
// orden u operación y cómo quedó cada parte (saldo libre, reservado y tokens, antes y después).
import type { ElementType, ReactNode } from 'react';
import type { AuditEntry } from '../types/audit';
import {
  actorLabel,
  balanceChange,
  cancelReasonLabel,
  eventTypeLabel,
  partyRoleLabel,
  tokensChange,
} from '../utils/audit';
import { formatTokens, sideLabel } from '../utils/market';
import { formatCredits, formatInstant, formatTokenPrice } from '../utils/quotes';
import styles from './AuditEntryCard.module.css';

interface LinkProps {
  to: string;
  className?: string;
  children?: ReactNode;
}

interface AuditEntryCardProps {
  entry: AuditEntry;
  // Del catálogo; sin él, el jugador se muestra por su id
  playerName?: string;
  // Por defecto un <a>; la página pasa el Link del router
  LinkComponent?: ElementType;
}

function DefaultLink({ to, className, children }: LinkProps) {
  return (
    <a href={to} className={className}>
      {children}
    </a>
  );
}

// "compra de 10 tokens a 1,0000 · total 10,00 cr."
function operationText(entry: AuditEntry): string {
  const parts = [
    entry.side ? `${sideLabel(entry.side)} de ${formatTokens(entry.quantity)}` : formatTokens(entry.quantity),
    entry.tokenPrice === null ? null : `a ${formatTokenPrice(entry.tokenPrice)}`,
  ].filter(Boolean);
  const total = entry.total === null ? '' : ` · total ${formatCredits(entry.total)}`;
  return `${parts.join(' ')}${total}`;
}

export function AuditEntryCard({ entry, playerName, LinkComponent = DefaultLink }: AuditEntryCardProps) {
  return (
    <article className={styles.entry} aria-label={`${eventTypeLabel(entry.eventType)} ${entry.id}`}>
      <header className={styles.header}>
        <span className={`${styles.type} ${styles[entry.eventType]}`}>{eventTypeLabel(entry.eventType)}</span>
        <span className={styles.when}>{formatInstant(entry.occurredAt)}</span>
        <span className={styles.actor}>por {actorLabel(entry.actorType, entry.actorId)}</span>
        <span className={styles.id}>#{entry.id}</span>
      </header>
      <p className={styles.what}>
        <LinkComponent to={`/jugadores/${entry.playerId}`} className={styles.player}>
          {playerName ?? `jugador #${entry.playerId}`}
        </LinkComponent>
        <span>{operationText(entry)}</span>
        {entry.cancelReason ? <span className={styles.muted}>{cancelReasonLabel(entry.cancelReason)}</span> : null}
      </p>
      {entry.detail ? <p className={styles.detail}>{entry.detail}</p> : null}
      <table className={styles.parties}>
        <caption className={styles.caption}>Partes antes y después</caption>
        <thead>
          <tr>
            <th scope="col">Parte</th>
            <th scope="col">Saldo libre</th>
            <th scope="col">Reservado</th>
            <th scope="col">Tokens</th>
          </tr>
        </thead>
        <tbody>
          {entry.parties.map((party) => (
            <tr key={`${party.role}-${party.userId}`}>
              <th scope="row" data-label="Parte">
                {partyRoleLabel(party.role)} · usuario #{party.userId}
              </th>
              <td data-label="Saldo libre">
                {balanceChange(party.before.availableBalance, party.after.availableBalance)}
              </td>
              <td data-label="Reservado">
                {balanceChange(party.before.reservedBalance, party.after.reservedBalance)}
              </td>
              <td data-label="Tokens">{tokensChange(party.before.tokens, party.after.tokens)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </article>
  );
}
