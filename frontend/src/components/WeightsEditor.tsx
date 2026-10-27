// Un juego de pesos: las 19 métricas con su etiqueta, la suma en vivo y el aviso si no suma 1,00
// (FR-066). Los valores son texto, tal cual se escriben, con coma o punto.
import type { ReactNode } from 'react';
import { METRICS, type Metric, type WeightsText } from '../types/quotes';
import { metricLabel } from '../utils/metrics';
import { checkWeightSet, formatHundredths, FULL_SET_HUNDREDTHS, weightErrorMessage } from '../utils/weights';
import { ChalkInput } from './ChalkInput';
import styles from './WeightsEditor.module.css';

interface WeightsEditorProps {
  // Qué juego es, p. ej. "juego por defecto"
  legend: string;
  value: WeightsText;
  onChange: (value: WeightsText) => void;
  disabled?: boolean;
  // Acción junto al título, p. ej. quitar un juego por posición
  action?: ReactNode;
}

function sumNotice(sum: number): string {
  if (sum < FULL_SET_HUNDREDTHS) return `faltan ${formatHundredths(FULL_SET_HUNDREDTHS - sum)} para llegar a 1,00`;
  return `sobran ${formatHundredths(sum - FULL_SET_HUNDREDTHS)}: tiene que dar 1,00`;
}

export function WeightsEditor({ legend, value, onChange, disabled = false, action }: WeightsEditorProps) {
  const check = checkWeightSet(value);
  const complete = check.sum === FULL_SET_HUNDREDTHS;

  function change(metric: Metric, text: string) {
    onChange({ ...value, [metric]: text });
  }

  return (
    <fieldset className={styles.set} disabled={disabled}>
      <legend className={styles.legend}>{legend}</legend>
      {action ? <div className={styles.action}>{action}</div> : null}
      <div className={styles.grid}>
        {METRICS.map((metric) => {
          const error = check.errors[metric];
          return (
            <ChalkInput
              key={metric}
              label={metricLabel(metric)}
              name={metric}
              inputMode="decimal"
              autoComplete="off"
              placeholder="0"
              value={value[metric]}
              error={error ? weightErrorMessage(error) : undefined}
              onChange={(event) => change(metric, event.target.value)}
            />
          );
        })}
      </div>
      <p className={styles.sum} aria-live="polite">
        <span className={complete ? styles.complete : styles.incomplete}>
          suma: {formatHundredths(check.sum)}
        </span>
        {complete ? null : <span className={styles.notice}>{sumNotice(check.sum)}</span>}
      </p>
    </fieldset>
  );
}
