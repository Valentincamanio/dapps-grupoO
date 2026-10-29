// Cotización compacta dentro de un post-it. Va dentro de su único link: solo texto, sin listas,
// botones ni enlaces. El score va al final, cuando la cotización lo trae (FR-071 de la 008).
import type { QuoteBadge } from '../types/quotes';
import { formatScore } from '../utils/market';
import { formatCredits } from '../utils/quotes';
import { VariationBadge } from './VariationBadge';
import styles from './QuoteTag.module.css';

interface QuoteTagProps {
  badge: QuoteBadge;
}

export function QuoteTag({ badge }: QuoteTagProps) {
  if (badge.status === 'loading') {
    return <span className={`${styles.tag} ${styles.muted}`}>cargando cotización...</span>;
  }
  if (badge.status === 'none') {
    return <span className={`${styles.tag} ${styles.muted}`}>sin cotización</span>;
  }
  if (badge.status === 'error') {
    return <span className={`${styles.tag} ${styles.muted}`}>cotización no disponible</span>;
  }

  const { quote } = badge;
  return (
    <span className={styles.tag}>
      <span className={styles.label}>cotización</span>
      <span className={styles.value}>{formatCredits(quote.value)}</span>
      <VariationBadge variation={quote.variation} tone="ink" />
      {quote.score === undefined ? null : <span className={styles.label}>score {formatScore(quote.score)}</span>}
    </span>
  );
}
