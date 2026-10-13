// Botón de tiza; con `busy` queda deshabilitado mientras se espera una respuesta.
import type { ButtonHTMLAttributes } from 'react';
import styles from './ChalkButton.module.css';

interface ChalkButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  busy?: boolean;
}

export function ChalkButton({
  busy = false,
  disabled,
  className,
  type = 'button',
  children,
  ...rest
}: ChalkButtonProps) {
  return (
    <button
      {...rest}
      type={type}
      className={[styles.button, className].filter(Boolean).join(' ')}
      disabled={disabled || busy}
      aria-busy={busy || undefined}
    >
      {children}
    </button>
  );
}
