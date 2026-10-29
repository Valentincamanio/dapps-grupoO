// Evolución semanal de la cotización (research D24): una cotización por semana, la última de esa
// semana. El SVG es decorativo; cada punto es un <button> encima, posicionado en porcentajes, que
// al enfocarlo, pasarle el mouse o activarlo muestra su detalle.
import { useState } from 'react';
import type { ChartPoint, Quote } from '../types/quotes';
import { axisTicks, polylinePoints, scalePoints, toChartPoints, valueDomain } from '../utils/chart';
import { formatBalance } from '../utils/format';
import { formatScore } from '../utils/market';
import { formatCredits, formatDay, quoteTypeLabel, weekLabel } from '../utils/quotes';
import styles from './QuoteChart.module.css';

interface QuoteChartProps {
  // Las cotizaciones del rango, en cualquier orden; el gráfico se queda con una por semana
  quotes: Quote[];
}

export const EMPTY_RANGE_TEXT = 'No hay cotizaciones en ese rango';

// "Cotización de la semana del 17/08/2026"
export function pointLabel(point: ChartPoint): string {
  return `Cotización de la ${weekLabel(point.week)}`;
}

// El score va al final, cuando la cotización lo trae (FR-058 de la 008)
function pointDetail(point: ChartPoint): string {
  const { quote } = point;
  const score = quote.score === undefined ? '' : `, score ${formatScore(quote.score)}`;
  return (
    `${pointLabel(point)}: ${formatCredits(point.value)}, ${quoteTypeLabel(quote.type)}, ` +
    `estrategia ${quote.strategy.name}, versión ${quote.strategy.version}${score}`
  );
}

export function QuoteChart({ quotes }: QuoteChartProps) {
  // Semana del punto elegido; se busca por semana para sobrevivir a un cambio de rango
  const [selectedWeek, setSelectedWeek] = useState<string | null>(null);

  const points = toChartPoints(quotes);
  if (points.length === 0) {
    return (
      <p className={styles.empty} role="status">
        {EMPTY_RANGE_TEXT}
      </p>
    );
  }

  const scaled = scalePoints(points);
  const ticks = axisTicks(valueDomain(points.map((point) => point.value)));
  const selected = points.find((point) => point.week === selectedWeek);

  return (
    <figure className={styles.chart}>
      <div className={styles.frame}>
        <div className={styles.axis} aria-hidden="true">
          {ticks.map((tick) => (
            <span key={tick.y} className={styles.tick} style={{ top: `${tick.y}%` }}>
              {formatBalance(tick.value)}
            </span>
          ))}
        </div>
        <div className={styles.plot}>
          <svg
            className={styles.lines}
            viewBox="0 0 100 100"
            preserveAspectRatio="none"
            aria-hidden="true"
            focusable="false"
          >
            {ticks.map((tick) => (
              <line
                key={tick.y}
                className={styles.grid}
                x1="0"
                x2="100"
                y1={tick.y}
                y2={tick.y}
                vectorEffect="non-scaling-stroke"
              />
            ))}
            <polyline
              className={styles.line}
              points={polylinePoints(scaled)}
              fill="none"
              vectorEffect="non-scaling-stroke"
            />
          </svg>
          {scaled.map(({ x, y, point }) => (
            <button
              key={point.week}
              type="button"
              className={`${styles.point} ${point.week === selectedWeek ? styles.selected : ''}`}
              style={{ left: `${x}%`, top: `${y}%` }}
              aria-label={pointLabel(point)}
              onFocus={() => setSelectedWeek(point.week)}
              onMouseEnter={() => setSelectedWeek(point.week)}
              onClick={() => setSelectedWeek(point.week)}
            >
              <span className={styles.dot} aria-hidden="true" />
            </button>
          ))}
        </div>
        <div className={styles.weeks} aria-hidden="true">
          <span>{formatDay(points[0].week)}</span>
          {points.length > 1 ? <span>{formatDay(points[points.length - 1].week)}</span> : null}
        </div>
      </div>
      <figcaption className={styles.detail} aria-live="polite">
        {selected ? pointDetail(selected) : 'Elegí un punto para ver su cotización.'}
      </figcaption>
    </figure>
  );
}
