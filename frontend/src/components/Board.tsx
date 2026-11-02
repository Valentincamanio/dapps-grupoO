// Fondo de la app: envuelve la barra superior, la pantalla activa y el panel de la ficha.
import type { ReactNode } from 'react';
import styles from './Board.module.css';

export function Board({ children }: { children: ReactNode }) {
  return <div className={styles.board}>{children}</div>;
}
