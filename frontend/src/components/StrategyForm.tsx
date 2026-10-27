// Alta o edición (versión nueva) de una estrategia: nombre (solo al crear), factor, mínimo, juego
// por defecto y juegos por posición. El envío queda deshabilitado mientras algún juego definido no
// sume exactamente 1,00 (FR-066). Lo escrito vive acá como texto, así un rechazo del servidor no
// lo pierde; el motivo llega por error. Al editar la activa se elige además cuándo recotizar.
import { useState, type FormEvent } from 'react';
import type { RequoteTiming, StrategyInput, StrategyVersionDto } from '../types/quotes';
import { checkParameters, parametersFormState, parametersFromFormState } from '../utils/weights';
import { ChalkButton } from './ChalkButton';
import { ChalkInput } from './ChalkInput';
import { ChalkNote } from './ChalkNote';
import { ParametersFields } from './ParametersFields';
import { DEFAULT_REQUOTE, RequoteChoice } from './RequoteChoice';
import styles from './StrategyForm.module.css';

export interface StrategyFormValues {
  // Solo al crear
  name?: string;
  input: StrategyInput;
  // Solo al editar la activa
  requote?: RequoteTiming;
}

interface StrategyFormProps {
  // Sin versión es un alta; con versión, la edición parte de ella
  version?: StrategyVersionDto;
  // Se edita la estrategia activa: pide cuándo recotizar
  isActive?: boolean;
  submitting?: boolean;
  // El motivo del rechazo del servidor, en español
  error?: string;
  onSubmit: (values: StrategyFormValues) => void;
  onCancel?: () => void;
}

export function StrategyForm({
  version,
  isActive = false,
  submitting = false,
  error,
  onSubmit,
  onCancel,
}: StrategyFormProps) {
  const creating = version === undefined;
  const [name, setName] = useState('');
  const [parameters, setParameters] = useState(() => parametersFormState(version));
  const [requote, setRequote] = useState<RequoteTiming>(DEFAULT_REQUOTE);

  const nameMissing = creating && name.trim() === '';
  const canSubmit = !nameMissing && checkParameters(parameters).valid;

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!canSubmit || submitting) return;
    onSubmit({
      name: creating ? name.trim() : undefined,
      input: parametersFromFormState(parameters),
      requote: !creating && isActive ? requote : undefined,
    });
  }

  return (
    <form className={styles.form} onSubmit={handleSubmit} noValidate>
      {creating ? (
        <ChalkInput
          label="nombre"
          name="name"
          autoComplete="off"
          maxLength={80}
          value={name}
          disabled={submitting}
          onChange={(event) => setName(event.target.value)}
        />
      ) : null}

      <ParametersFields value={parameters} onChange={setParameters} disabled={submitting} />

      {!creating && isActive ? (
        <RequoteChoice
          legend="es la estrategia activa: ¿cuándo recotizar con la versión nueva?"
          value={requote}
          onChange={setRequote}
          disabled={submitting}
        />
      ) : null}

      {error ? <ChalkNote variant="error">{error}</ChalkNote> : null}
      {canSubmit ? null : (
        <p className={styles.blocked}>
          para guardar, {nameMissing ? 'poné un nombre y ' : ''}cada juego tiene que dar exactamente
          1,00
        </p>
      )}

      <div className={styles.actions}>
        <ChalkButton type="submit" busy={submitting} disabled={!canSubmit}>
          {creating ? 'Crear estrategia' : 'Guardar versión nueva'}
        </ChalkButton>
        {onCancel ? (
          <ChalkButton disabled={submitting} onClick={onCancel}>
            Cancelar
          </ChalkButton>
        ) : null}
      </div>
    </form>
  );
}
