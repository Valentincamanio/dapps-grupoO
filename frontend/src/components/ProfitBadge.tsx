// Ganancia o pérdida de una posición o de una venta: triángulo, monto y porcentaje con signo y el
// texto "gana", "pierde" o "sin cambios", como VariationBadge. El color solo refuerza; se entiende
// sin él (FR-070). Sin valor, por ejemplo en el portfolio del superusuario, muestra un guion.
import { formatSigned, formatSignedPercent, profitText } from '../utils/market';
import { amountDirection } from '../utils/quotes';
import styles from './ProfitBadge.module.css';

interface ProfitBadgeProps {
  amount?: number | null;
  // Rentabilidad sobre lo invertido; sin invertido no se informa
  percent?: number | null;
  // 'chalk' sobre el pizarrón, 'ink' sobre el papel
  tone?: 'chalk' | 'ink';
}

const TRIANGLES = { up: '▲', down: '▼', flat: '' } as const;

export function ProfitBadge({ amount, percent, tone = 'chalk' }: ProfitBadgeProps) {
  if (amount === undefined || amount === null) {
    return (
      <span className={`${styles.badge} ${styles[tone]} ${styles.flat}`}>
        <span aria-hidden="true">—</span>
        <span className={styles.hidden}>sin datos</span>
      </span>
    );
  }
  const direction = amountDirection(amount);

  // Los espacios entre las partes son para el texto que se lee; en pantalla las separa el gap
  return (
    <span className={`${styles.badge} ${styles[tone]} ${styles[direction]}`}>
      {TRIANGLES[direction] ? (
        <span className={styles.triangle} aria-hidden="true">
          {TRIANGLES[direction]}
        </span>
      ) : null}{' '}
      <span>{formatSigned(amount)}</span>{' '}
      {percent === undefined || percent === null ? null : <span>({formatSignedPercent(percent)}) </span>}
      <span className={styles.text}>{profitText(amount)}</span>
    </span>
  );
}
