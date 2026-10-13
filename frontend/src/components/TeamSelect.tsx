// Desplegable de equipo en tiza: botón + listbox accesible con teclado, mouse y toque.
import { useEffect, useId, useRef, useState, type FocusEvent, type KeyboardEvent } from 'react';
import { ChalkNote } from './ChalkNote';
import styles from './TeamSelect.module.css';

interface TeamSelectProps {
  teams: string[];
  value: string | null;
  status: 'idle' | 'loading' | 'success' | 'error';
  // Se llama al abrir el desplegable (dispara la carga del catálogo)
  onOpen: () => void;
  onSelect: (team: string | null) => void;
  onRetry: () => void;
}

export function TeamSelect({ teams, value, status, onOpen, onSelect, onRetry }: TeamSelectProps) {
  const [open, setOpen] = useState(false);
  // 0 es "todos"; los equipos siguen desde 1
  const [highlighted, setHighlighted] = useState(0);
  const rootRef = useRef<HTMLDivElement>(null);
  const buttonRef = useRef<HTMLButtonElement>(null);
  const listRef = useRef<HTMLUListElement>(null);
  const listId = useId();

  const options: (string | null)[] = [null, ...teams];
  const listed = status === 'success' || teams.length > 0;

  // Al abrir la lista el foco pasa a ella para navegar con flechas
  useEffect(() => {
    if (open && listed) listRef.current?.focus();
  }, [open, listed]);

  function openList() {
    const index = value === null ? 0 : options.indexOf(value);
    setHighlighted(index >= 0 ? index : 0);
    setOpen(true);
    onOpen();
  }

  function close() {
    setOpen(false);
    buttonRef.current?.focus();
  }

  function choose(team: string | null) {
    onSelect(team);
    close();
  }

  function handleTriggerKeyDown(event: KeyboardEvent<HTMLButtonElement>) {
    if (event.key === 'ArrowDown' && !open) {
      event.preventDefault();
      openList();
    }
  }

  function handleListKeyDown(event: KeyboardEvent<HTMLUListElement>) {
    if (event.key === 'Escape') {
      event.preventDefault();
      close();
    } else if (event.key === 'ArrowDown') {
      event.preventDefault();
      setHighlighted((index) => Math.min(index + 1, options.length - 1));
    } else if (event.key === 'ArrowUp') {
      event.preventDefault();
      setHighlighted((index) => Math.max(index - 1, 0));
    } else if (event.key === 'Enter') {
      event.preventDefault();
      choose(options[highlighted] ?? null);
    }
  }

  // Cierra al salir con Tab o al tocar fuera
  function handleBlur(event: FocusEvent<HTMLDivElement>) {
    if (!rootRef.current?.contains(event.relatedTarget as Node | null)) setOpen(false);
  }

  return (
    <div className={styles.root} ref={rootRef} onBlur={handleBlur}>
      <button
        type="button"
        ref={buttonRef}
        className={styles.trigger}
        aria-haspopup="listbox"
        aria-expanded={open}
        aria-controls={open && listed ? listId : undefined}
        onClick={() => (open ? close() : openList())}
        onKeyDown={handleTriggerKeyDown}
      >
        equipo: {value ?? 'todos'} ▾
      </button>
      {open ? (
        <div className={styles.panel}>
          {status === 'loading' || status === 'idle' ? <ChalkNote variant="loading" /> : null}
          {status === 'error' ? (
            <ChalkNote variant="error" action={{ label: 'reintentar', onClick: onRetry }}>
              no se pudo conectar con la pizarra
            </ChalkNote>
          ) : null}
          {listed ? (
            <ul
              id={listId}
              ref={listRef}
              role="listbox"
              tabIndex={-1}
              aria-label="equipos"
              aria-activedescendant={`${listId}-${highlighted}`}
              className={styles.list}
              onKeyDown={handleListKeyDown}
            >
              {options.map((team, index) => (
                <li key={team ?? 'todos'} role="presentation">
                  <button
                    type="button"
                    id={`${listId}-${index}`}
                    role="option"
                    tabIndex={-1}
                    aria-selected={team === value}
                    className={`${styles.option} ${index === highlighted ? styles.highlighted : ''}`}
                    // Evita que el foco salga de la lista antes del clic (Safari)
                    onMouseDown={(event) => event.preventDefault()}
                    onPointerEnter={() => setHighlighted(index)}
                    onClick={() => choose(team)}
                  >
                    {team ?? 'todos'}
                  </button>
                </li>
              ))}
            </ul>
          ) : null}
        </div>
      ) : null}
    </div>
  );
}
