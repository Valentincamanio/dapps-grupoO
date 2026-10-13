// Campo de texto con label visible y mensaje de error asociado.
import { useId, type InputHTMLAttributes } from 'react';
import styles from './ChalkInput.module.css';

interface ChalkInputProps extends Omit<InputHTMLAttributes<HTMLInputElement>, 'id'> {
  label: string;
  id?: string;
  error?: string;
}

export function ChalkInput({ label, id, error, className, ...rest }: ChalkInputProps) {
  const generatedId = useId();
  const inputId = id ?? generatedId;
  const errorId = `${inputId}-error`;

  return (
    <div className={styles.field}>
      <label className={styles.label} htmlFor={inputId}>
        {label}
      </label>
      <input
        {...rest}
        id={inputId}
        className={[styles.input, error ? styles.invalid : '', className]
          .filter(Boolean)
          .join(' ')}
        aria-invalid={error ? true : undefined}
        aria-describedby={error ? errorId : undefined}
      />
      {error ? (
        <p id={errorId} className={styles.error}>
          {error}
        </p>
      ) : null}
    </div>
  );
}
