// Pestaña de cinta adhesiva para elegir una liga; se pega al pizarrón como filtro.
import styles from './TapeTab.module.css';

interface TapeTabProps {
  label: string;
  active: boolean;
  onSelect: () => void;
}

export function TapeTab({ label, active, onSelect }: TapeTabProps) {
  return (
    <button
      type="button"
      className={`${styles.tab} ${active ? styles.active : ''}`}
      aria-pressed={active}
      onClick={onSelect}
    >
      {label}
    </button>
  );
}
