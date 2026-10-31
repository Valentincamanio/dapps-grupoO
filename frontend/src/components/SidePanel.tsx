// Panel que se abre encima de la pantalla: a la derecha en una pantalla ancha, desde abajo en el
// celular. Toma el foco y lo devuelve al cerrarse. Escape o tocar afuera lo cierran. No conoce el router.
import { useEffect, useRef, type ReactNode } from 'react';
import styles from './SidePanel.module.css';

interface SidePanelProps {
  // Nombre accesible del panel
  label: string;
  onClose: () => void;
  children: ReactNode;
}

export function SidePanel({ label, onClose, children }: SidePanelProps) {
  const panelRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const previous = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    panelRef.current?.focus();
    return () => previous?.focus();
  }, []);

  // La pantalla de atrás no se desplaza mientras el panel está abierto
  useEffect(() => {
    const { overflow } = document.body.style;
    document.body.style.overflow = 'hidden';
    return () => {
      document.body.style.overflow = overflow;
    };
  }, []);

  // Un diálogo abierto dentro del panel usa Escape primero y lo marca con preventDefault
  useEffect(() => {
    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === 'Escape' && !event.defaultPrevented) onClose();
    }
    document.addEventListener('keydown', handleKeyDown);
    return () => document.removeEventListener('keydown', handleKeyDown);
  }, [onClose]);

  return (
    <div className={styles.root}>
      {/* Es un atajo para el mouse: con teclado se cierra con Escape o con el enlace del panel */}
      <button type="button" className={styles.scrim} aria-label="cerrar el panel" tabIndex={-1} onClick={onClose} />
      <div ref={panelRef} role="dialog" aria-modal="true" aria-label={label} tabIndex={-1} className={styles.panel}>
        {children}
      </div>
    </div>
  );
}
