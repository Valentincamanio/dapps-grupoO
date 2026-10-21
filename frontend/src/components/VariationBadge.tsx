// Variación de una cotización: triángulo, monto y porcentaje con signo y texto ("sube"/"baja").
// El color solo refuerza; se entiende sin él (FR-059).
import type { Variation } from '../types/quotes';
import { formatVariationAmount, variationDirection, variationText } from '../utils/quotes';
import styles from './VariationBadge.module.css';

interface VariationBadgeProps {
  // La cotización inicial no trae variación: entonces no se muestra nada
  variation?: Variation;
  // 'chalk' sobre el pizarrón, 'ink' sobre el papel de un post-it o de la ficha
  tone?: 'chalk' | 'ink';
}

const TRIANGLES = { up: '▲', down: '▼', flat: '' } as const;

export function VariationBadge({ variation, tone = 'chalk' }: VariationBadgeProps) {
  if (!variation) return null;
  const direction = variationDirection(variation);

  return (
    <span className={`${styles.badge} ${styles[tone]} ${styles[direction]}`}>
      {TRIANGLES[direction] ? (
        <span className={styles.triangle} aria-hidden="true">
          {TRIANGLES[direction]}
        </span>
      ) : null}
      <span>{formatVariationAmount(variation)}</span>
      <span className={styles.text}>{variationText(variation)}</span>
    </span>
  );
}
