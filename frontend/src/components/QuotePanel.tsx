// Cotización actual de la ficha: valor del jugador, precio por token, variación, semana y
// estrategia. No repite equipo, posición ni liga (ya están en la hoja de scout).
import type { Quote } from '../types/quotes';
import { formatCredits, formatTokenPrice, quoteTypeLabel, weekLabel } from '../utils/quotes';
import { VariationBadge } from './VariationBadge';
import styles from './QuotePanel.module.css';

interface QuotePanelProps {
  quote: Quote;
}

export function QuotePanel({ quote }: QuotePanelProps) {
  return (
    <dl className={styles.panel}>
      <div className={styles.main}>
        <dt>Valor del jugador</dt>
        <dd className={styles.value}>{formatCredits(quote.value)}</dd>
      </div>
      <div className={styles.main}>
        <dt>Precio por token</dt>
        <dd className={styles.value}>{formatTokenPrice(quote.tokenPrice)}</dd>
      </div>
      <div>
        <dt>Variación respecto de la semana anterior</dt>
        <dd>
          {quote.variation ? (
            <VariationBadge variation={quote.variation} tone="ink" />
          ) : (
            'sin variación: es la cotización inicial'
          )}
        </dd>
      </div>
      <div>
        <dt>Cotización</dt>
        <dd>
          {quoteTypeLabel(quote.type)}, {weekLabel(quote.weekStart)}
        </dd>
      </div>
      <div>
        <dt>Estrategia</dt>
        <dd>
          {quote.strategy.name}, versión {quote.strategy.version}
        </dd>
      </div>
    </dl>
  );
}
