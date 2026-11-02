// Alternativa en texto del gráfico de evolución: los mismos puntos (una cotización por semana),
// en una tabla dentro de un <details>, con el score en la última columna (FR-058 de la 008).
import type { Quote } from '../types/quotes';
import { toChartPoints } from '../utils/chart';
import { formatScore } from '../utils/market';
import { formatCredits, formatDay, formatVariation, quoteTypeLabel } from '../utils/quotes';
import styles from './QuoteTable.module.css';

interface QuoteTableProps {
  quotes: Quote[];
}

export function QuoteTable({ quotes }: QuoteTableProps) {
  const points = toChartPoints(quotes);
  // Sin puntos el gráfico ya muestra el mensaje del rango vacío
  if (points.length === 0) return null;

  return (
    <details className={styles.details}>
      <summary className={styles.summary}>ver la tabla de cotizaciones por semana</summary>
      <div className={styles.scroll}>
        <table className={styles.table}>
          <caption className={styles.caption}>Cotización de cada semana del rango</caption>
          <thead>
            <tr>
              <th scope="col">Semana del</th>
              <th scope="col">Cotización</th>
              <th scope="col">Variación</th>
              <th scope="col">Tipo</th>
              <th scope="col">Estrategia</th>
              <th scope="col">Score</th>
            </tr>
          </thead>
          <tbody>
            {points.map(({ week, quote }) => (
              <tr key={week}>
                <th scope="row">{formatDay(week)}</th>
                <td>{formatCredits(quote.value)}</td>
                <td>{quote.variation ? formatVariation(quote.variation) : 'sin variación'}</td>
                <td>{quoteTypeLabel(quote.type)}</td>
                <td>
                  {quote.strategy.name}, versión {quote.strategy.version}
                </td>
                <td>{quote.score === undefined ? '—' : formatScore(quote.score)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </details>
  );
}
