// Pizarrón verde con marco de madera que envuelve el contenido de la página.
import type { ReactNode } from 'react';
import styles from './Board.module.css';

export function Board({ children }: { children: ReactNode }) {
  return <div className={styles.board}>{children}</div>;
}
