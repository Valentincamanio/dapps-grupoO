// Confirmación de una acción que no se deshace, como comprar o vender (FR-063): nada se ejecuta hasta
// confirmar. Toma el foco en "Confirmar" (Enter confirma), Escape cancela y Tab no sale del diálogo. Del patrón de
// RequoteChoice toma el par de botones; no usa <dialog> porque jsdom no implementa showModal
// (research D27). Al cerrarse, el foco vuelve a donde estaba.
import { useEffect, useId, useRef, type ReactNode } from 'react';
import { ChalkButton } from './ChalkButton';
import styles from './ConfirmDialog.module.css';

interface ConfirmDialogProps {
  title: string;
  // El detalle de lo que se confirma
  children?: ReactNode;
  confirmLabel?: string;
  cancelLabel?: string;
  // Mientras se espera la respuesta no se puede confirmar de nuevo ni cancelar
  busy?: boolean;
  onConfirm: () => void;
  onCancel: () => void;
}

export function ConfirmDialog({
  title,
  children,
  confirmLabel = 'Confirmar',
  cancelLabel = 'Cancelar',
  busy = false,
  onConfirm,
  onCancel,
}: ConfirmDialogProps) {
  const titleId = useId();
  const dialogRef = useRef<HTMLDivElement>(null);

  // ChalkButton no reenvía refs: el botón de confirmar se busca por su marca
  useEffect(() => {
    const previous = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    dialogRef.current?.querySelector<HTMLButtonElement>('[data-confirm]')?.focus();
    return () => previous?.focus();
  }, []);

  // El teclado del patrón de diálogo de WAI-ARIA. Va como listener nativo porque jsx-a11y no admite
  // un onKeyDown en un elemento que no es interactivo, aunque sea un diálogo
  useEffect(() => {
    const dialog = dialogRef.current;
    if (!dialog) return;
    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === 'Tab') {
        keepFocusInside(event);
        return;
      }
      if (busy) return;
      if (event.key === 'Escape') {
        event.preventDefault();
        onCancel();
      } else if (event.key === 'Enter' && !(event.target instanceof HTMLButtonElement)) {
        // Sobre un botón, Enter ya lo activa: solo se confirma desde el resto del diálogo
        event.preventDefault();
        onConfirm();
      }
    }
    // Es modal: Tab y Shift+Tab dan la vuelta entre sus botones y nunca llevan el foco a la pantalla
    // tapada, donde Escape ya no cancelaría. Mientras espera no hay nada habilitado y el foco se queda
    const keepFocusInside = (event: KeyboardEvent) => {
      const buttons = Array.from(dialog.querySelectorAll<HTMLButtonElement>('button:not(:disabled)'));
      const first = buttons[0];
      const last = buttons[buttons.length - 1];
      if (!first || !last) {
        event.preventDefault();
      } else if (event.shiftKey && document.activeElement === first) {
        event.preventDefault();
        last.focus();
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault();
        first.focus();
      }
    };
    dialog.addEventListener('keydown', handleKeyDown);
    return () => dialog.removeEventListener('keydown', handleKeyDown);
  }, [busy, onCancel, onConfirm]);

  return (
    <div className={styles.backdrop}>
      <div ref={dialogRef} role="dialog" aria-modal="true" aria-labelledby={titleId} className={styles.dialog}>
        <h2 id={titleId} className={styles.title}>
          {title}
        </h2>
        {children ? <div className={styles.body}>{children}</div> : null}
        <div className={styles.actions}>
          <ChalkButton data-confirm busy={busy} onClick={onConfirm}>
            {confirmLabel}
          </ChalkButton>
          <ChalkButton disabled={busy} onClick={onCancel}>
            {cancelLabel}
          </ChalkButton>
        </div>
      </div>
    </div>
  );
}
