// Las estrategias guardadas, cada una con todas sus versiones y cuál es la activa, y los botones
// "Editar" (versión nueva) y "Activar" (solo en las que no son la activa).
import { POSITIONS } from '../types/api';
import { METRICS, type StrategyDto, type StrategyVersionDto, type WeightMap } from '../types/quotes';
import { formatBalance } from '../utils/format';
import { metricLabel } from '../utils/metrics';
import { positionPlural } from '../utils/positions';
import { formatCredits, formatInstant } from '../utils/quotes';
import { formatHundredths } from '../utils/weights';
import { ChalkButton } from './ChalkButton';
import styles from './StrategyList.module.css';

interface StrategyListProps {
  strategies: StrategyDto[];
  onEdit: (strategy: StrategyDto) => void;
  onActivate: (strategy: StrategyDto) => void;
  // Mientras se guarda un cambio no se ofrece otro
  disabled?: boolean;
}

// Solo las métricas que pesan, en el orden de METRICS, p. ej. goles 0,25 · asistencias 0,15
function weightsSummary(weights: WeightMap): string {
  return METRICS.filter((metric) => (weights[metric] ?? 0) > 0)
    .map((metric) => `${metricLabel(metric)} ${formatHundredths(Math.round(weights[metric]! * 100))}`)
    .join(' · ');
}

function VersionItem({ version }: { version: StrategyVersionDto }) {
  return (
    <li className={styles.version}>
      <p className={styles.versionLine}>
        <span className={styles.versionNumber}>versión {version.version}</span>
        {version.active ? <span className={styles.activeMark}>(activa)</span> : null}
        <span>factor {formatBalance(version.scaleFactor)}</span>
        <span>mínimo {formatCredits(version.minimumValue)}</span>
        <span className={styles.muted}>creada el {formatInstant(version.createdAt)}</span>
      </p>
      <details className={styles.weights}>
        <summary>pesos de la versión {version.version}</summary>
        <dl>
          <div>
            <dt>por defecto</dt>
            <dd>{weightsSummary(version.defaultWeights)}</dd>
          </div>
          {POSITIONS.map((position) => {
            const weights = version.positionWeights[position];
            if (!weights) return null;
            return (
              <div key={position}>
                <dt>{positionPlural(position)}</dt>
                <dd>{weightsSummary(weights)}</dd>
              </div>
            );
          })}
        </dl>
      </details>
    </li>
  );
}

export function StrategyList({ strategies, onEdit, onActivate, disabled = false }: StrategyListProps) {
  return (
    <ul className={styles.list} aria-label="estrategias">
      {strategies.map((strategy) => {
        const headingId = `strategy-${strategy.id}`;
        return (
          <li key={strategy.id} className={styles.strategy}>
            <article aria-labelledby={headingId}>
              <header className={styles.header}>
                <h3 id={headingId} className={styles.name}>
                  {strategy.name}
                </h3>
                {strategy.active ? <span className={styles.badge}>activa</span> : null}
                <div className={styles.actions}>
                  <ChalkButton
                    aria-label={`Editar ${strategy.name}`}
                    disabled={disabled}
                    onClick={() => onEdit(strategy)}
                  >
                    Editar
                  </ChalkButton>
                  {strategy.active ? null : (
                    <ChalkButton
                      aria-label={`Activar ${strategy.name}`}
                      disabled={disabled}
                      onClick={() => onActivate(strategy)}
                    >
                      Activar
                    </ChalkButton>
                  )}
                </div>
              </header>
              <ol className={styles.versions} aria-label={`versiones de ${strategy.name}`}>
                {strategy.versions.map((version) => (
                  <VersionItem key={version.version} version={version} />
                ))}
              </ol>
            </article>
          </li>
        );
      })}
    </ul>
  );
}
