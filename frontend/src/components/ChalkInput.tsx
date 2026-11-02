// Campo de texto con label visible y mensaje de error asociado. Uno de contraseña lleva a la derecha
// un ojo que la muestra u oculta, para revisar lo escrito.
import { useId, useState, type InputHTMLAttributes } from 'react';
import styles from './ChalkInput.module.css';

interface ChalkInputProps extends Omit<InputHTMLAttributes<HTMLInputElement>, 'id'> {
  label: string;
  id?: string;
  error?: string;
}

const EYE = 'M2 12s3.6-7 10-7 10 7 10 7-3.6 7-10 7S2 12 2 12ZM12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6Z';
const EYE_OFF =
  'M3 3l18 18M10.6 5.1A10 10 0 0 1 12 5c6.4 0 10 7 10 7a17 17 0 0 1-3.2 4.1M6.6 6.6A17 17 0 0 0 2 12s3.6 7 10 7a9.7 9.7 0 0 0 5.4-1.6M9.9 9.9a3 3 0 0 0 4.2 4.2';

export function ChalkInput({ label, id, error, className, type, ...rest }: ChalkInputProps) {
  const generatedId = useId();
  const inputId = id ?? generatedId;
  const errorId = `${inputId}-error`;
  const isPassword = type === 'password';
  const [revealed, setRevealed] = useState(false);

  const input = (
    <input
      {...rest}
      id={inputId}
      type={isPassword && revealed ? 'text' : type}
      className={[styles.input, isPassword ? styles.withToggle : '', error ? styles.invalid : '', className]
        .filter(Boolean)
        .join(' ')}
      aria-invalid={error ? true : undefined}
      aria-describedby={error ? errorId : undefined}
    />
  );

  return (
    <div className={styles.field}>
      <label className={styles.label} htmlFor={inputId}>
        {label}
      </label>
      {isPassword ? (
        <div className={styles.control}>
          {input}
          <button
            type="button"
            className={styles.toggle}
            aria-label={revealed ? 'ocultar contraseña' : 'mostrar contraseña'}
            aria-pressed={revealed}
            aria-controls={inputId}
            onClick={() => setRevealed((current) => !current)}
          >
            <svg
              viewBox="0 0 24 24"
              width="18"
              height="18"
              fill="none"
              stroke="currentColor"
              strokeWidth="2"
              strokeLinecap="round"
              strokeLinejoin="round"
              aria-hidden="true"
              focusable="false"
            >
              <path d={revealed ? EYE_OFF : EYE} />
            </svg>
          </button>
        </div>
      ) : (
        input
      )}
      {error ? (
        <p id={errorId} className={styles.error}>
          {error}
        </p>
      ) : null}
    </div>
  );
}
