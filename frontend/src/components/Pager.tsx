// Paginación de la pizarra: "hoja X de N" con botones de hoja anterior y siguiente.
import { ChalkButton } from './ChalkButton';
import styles from './Pager.module.css';

interface PagerProps {
  sheet: number;
  totalSheets: number;
  hasPrevious: boolean;
  hasNext: boolean;
  onPrevious: () => void;
  onNext: () => void;
}

export function Pager({
  sheet,
  totalSheets,
  hasPrevious,
  hasNext,
  onPrevious,
  onNext,
}: PagerProps) {
  return (
    <nav className={styles.pager} aria-label="paginación de la pizarra">
      <ChalkButton disabled={!hasPrevious} onClick={onPrevious}>
        hoja anterior
      </ChalkButton>
      <span className={styles.label} aria-live="polite">
        hoja {sheet} de {totalSheets}
      </span>
      <ChalkButton disabled={!hasNext} onClick={onNext}>
        hoja siguiente
      </ChalkButton>
    </nav>
  );
}
