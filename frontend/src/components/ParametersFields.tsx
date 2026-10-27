// Parámetros de valuación de una estrategia: factor de escala, cotización mínima, el juego de pesos
// por defecto y los juegos por posición opcionales. Lo usan el formulario de estrategias y la
// simulación con parámetros; el estado es del que lo usa.
import { POSITIONS, type Position } from '../types/api';
import type { WeightsText } from '../types/quotes';
import { positionPlural } from '../utils/positions';
import { checkParameters, type ParametersFormState } from '../utils/weights';
import { ChalkButton } from './ChalkButton';
import { ChalkInput } from './ChalkInput';
import { WeightsEditor } from './WeightsEditor';
import styles from './ParametersFields.module.css';

interface ParametersFieldsProps {
  value: ParametersFormState;
  onChange: (value: ParametersFormState) => void;
  disabled?: boolean;
}

export function ParametersFields({ value, onChange, disabled = false }: ParametersFieldsProps) {
  const check = checkParameters(value);
  const { weights } = value;

  function setPositionWeights(position: Position, text: WeightsText | undefined) {
    const positionWeights = { ...weights.positionWeights };
    if (text) {
      positionWeights[position] = text;
    } else {
      delete positionWeights[position];
    }
    onChange({ ...value, weights: { ...weights, positionWeights } });
  }

  return (
    <div className={styles.fields}>
      <div className={styles.numbers}>
        <ChalkInput
          label="factor de escala"
          name="scaleFactor"
          inputMode="decimal"
          autoComplete="off"
          value={value.scaleFactor}
          disabled={disabled}
          error={check.scaleFactorError}
          onChange={(event) => onChange({ ...value, scaleFactor: event.target.value })}
        />
        <ChalkInput
          label="cotización mínima (cr.)"
          name="minimumValue"
          inputMode="decimal"
          autoComplete="off"
          value={value.minimumValue}
          disabled={disabled}
          error={check.minimumValueError}
          onChange={(event) => onChange({ ...value, minimumValue: event.target.value })}
        />
      </div>

      <WeightsEditor
        legend="juego por defecto"
        value={weights.defaultWeights}
        disabled={disabled}
        onChange={(defaultWeights) => onChange({ ...value, weights: { ...weights, defaultWeights } })}
      />

      <p className={styles.hint}>
        una posición sin juego propio usa el juego por defecto
      </p>
      {POSITIONS.map((position) => {
        const text = weights.positionWeights[position];
        if (!text) {
          return (
            <ChalkButton
              key={position}
              className={styles.add}
              disabled={disabled}
              // Arranca con una copia del juego por defecto
              onClick={() => setPositionWeights(position, { ...weights.defaultWeights })}
            >
              agregar juego de {positionPlural(position)}
            </ChalkButton>
          );
        }
        return (
          <WeightsEditor
            key={position}
            legend={`juego de ${positionPlural(position)}`}
            value={text}
            disabled={disabled}
            onChange={(next) => setPositionWeights(position, next)}
            action={
              <ChalkButton disabled={disabled} onClick={() => setPositionWeights(position, undefined)}>
                quitar juego de {positionPlural(position)}
              </ChalkButton>
            }
          />
        );
      })}
    </div>
  );
}
