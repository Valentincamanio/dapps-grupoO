// Aviso escrito en tiza: información, error, carga o éxito, con acción opcional.
import type { ReactNode } from 'react';
import styles from './ChalkNote.module.css';

export type ChalkNoteVariant = 'info' | 'error' | 'loading' | 'success';

interface ChalkNoteProps {
  variant?: ChalkNoteVariant;
  children?: ReactNode;
  action?: { label: string; onClick: () => void };
}

const DEFAULT_LOADING_TEXT = 'el DT está pensando...';

export function ChalkNote({ variant = 'info', children, action }: ChalkNoteProps) {
  const role = variant === 'error' ? 'alert' : 'status';
  const content = children ?? (variant === 'loading' ? DEFAULT_LOADING_TEXT : null);

  return (
    <div className={`${styles.note} ${styles[variant]}`} role={role}>
      <span>{content}</span>
      {action ? (
        <button type="button" className={styles.action} onClick={action.onClick}>
          {action.label}
        </button>
      ) : null}
    </div>
  );
}
