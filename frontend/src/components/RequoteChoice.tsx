// Cuándo recotizar al cambiar la estrategia activa (FR-065): dos opciones con su explicación y "la
// semana siguiente" preseleccionada por quien lo usa. Con onConfirm suma los botones "Confirmar" y
// "Cancelar" y nada se envía hasta confirmar; dentro de un formulario, el envío del formulario es
// la confirmación.
import { useId } from 'react';
import { REQUOTE_TIMINGS, type RequoteTiming } from '../types/quotes';
import { ChalkButton } from './ChalkButton';
import styles from './RequoteChoice.module.css';

interface RequoteChoiceProps {
  value: RequoteTiming;
  onChange: (value: RequoteTiming) => void;
  // Por defecto "¿cuándo recotizar?"
  legend?: string;
  disabled?: boolean;
  onConfirm?: () => void;
  onCancel?: () => void;
  // Mientras se espera la respuesta de la confirmación
  busy?: boolean;
}

// La opción que se preselecciona: no cambia las cotizaciones de ahora
export const DEFAULT_REQUOTE: RequoteTiming = 'PROXIMA_SEMANA';

const OPTIONS: Record<RequoteTiming, { label: string; explanation: string }> = {
  AHORA: {
    label: 'Recotizar ahora',
    explanation:
      'recotiza en el acto la última semana cerrada con la estrategia nueva: las cotizaciones actuales cambian ya',
  },
  PROXIMA_SEMANA: {
    label: 'Recotizar la semana siguiente',
    explanation:
      'las cotizaciones actuales no cambian: la estrategia nueva se usa desde el próximo cierre semanal de los lunes',
  },
};

export function RequoteChoice({
  value,
  onChange,
  legend = '¿cuándo recotizar?',
  disabled = false,
  onConfirm,
  onCancel,
  busy = false,
}: RequoteChoiceProps) {
  const groupId = useId();

  return (
    <fieldset className={styles.choice} disabled={disabled || busy}>
      <legend className={styles.legend}>{legend}</legend>
      {REQUOTE_TIMINGS.map((timing) => {
        const inputId = `${groupId}-${timing}`;
        const explanationId = `${inputId}-explanation`;
        return (
          <div key={timing} className={styles.option}>
            <input
              id={inputId}
              type="radio"
              name={groupId}
              value={timing}
              className={styles.radio}
              checked={value === timing}
              aria-describedby={explanationId}
              onChange={() => onChange(timing)}
            />
            <label htmlFor={inputId} className={styles.label}>
              {OPTIONS[timing].label}
            </label>
            <p id={explanationId} className={styles.explanation}>
              {OPTIONS[timing].explanation}
            </p>
          </div>
        );
      })}
      {onConfirm || onCancel ? (
        <div className={styles.actions}>
          {onConfirm ? (
            <ChalkButton busy={busy} onClick={onConfirm}>
              Confirmar
            </ChalkButton>
          ) : null}
          {onCancel ? (
            <ChalkButton disabled={busy} onClick={onCancel}>
              Cancelar
            </ChalkButton>
          ) : null}
        </div>
      ) : null}
    </fieldset>
  );
}
