// Buscador de jugadores en tiza: combobox accesible con sugerencias. No conoce el router.
import { useEffect, useId, useRef, useState, type KeyboardEvent } from 'react';
import type { Player } from '../types/api';
import { positionLabel } from '../utils/positions';
import { MIN_QUERY_LENGTH, normalize, searchPlayers } from '../utils/search';
import { ChalkNote } from './ChalkNote';
import styles from './SearchBox.module.css';

interface SearchBoxProps {
  players: Player[];
  status: 'idle' | 'loading' | 'success' | 'error';
  // Se llama al enfocar el campo (dispara la carga del catálogo)
  onFocus: () => void;
  onRetry: () => void;
  onSelect: (player: Player) => void;
}

const DEBOUNCE_MS = 200;

export function SearchBox({ players, status, onFocus, onRetry, onSelect }: SearchBoxProps) {
  const [text, setText] = useState('');
  // Consulta ya estabilizada por el debounce
  const [query, setQuery] = useState('');
  const [open, setOpen] = useState(false);
  const [highlighted, setHighlighted] = useState(0);
  const inputRef = useRef<HTMLInputElement>(null);
  const listId = useId();

  useEffect(() => {
    const timer = setTimeout(() => setQuery(text), DEBOUNCE_MS);
    return () => clearTimeout(timer);
  }, [text]);

  const searching = normalize(query).length >= MIN_QUERY_LENGTH;
  const suggestions = status === 'success' && searching ? searchPlayers(players, query) : [];
  const showPanel = open && searching;
  const showList = showPanel && suggestions.length > 0;
  const activeId = showList ? `${listId}-${highlighted}` : undefined;

  function choose(player: Player) {
    onSelect(player);
    setText('');
    setQuery('');
    setOpen(false);
  }

  function handleKeyDown(event: KeyboardEvent<HTMLInputElement>) {
    if (event.key === 'Escape') {
      event.preventDefault();
      setOpen(false);
      inputRef.current?.focus();
    } else if (event.key === 'ArrowDown' && showList) {
      event.preventDefault();
      setHighlighted((index) => Math.min(index + 1, suggestions.length - 1));
    } else if (event.key === 'ArrowUp' && showList) {
      event.preventDefault();
      setHighlighted((index) => Math.max(index - 1, 0));
    } else if (event.key === 'Enter' && showList) {
      event.preventDefault();
      const player = suggestions[highlighted];
      if (player) choose(player);
    }
  }

  return (
    <div className={styles.root}>
      <input
        ref={inputRef}
        type="text"
        role="combobox"
        className={styles.input}
        aria-label="buscar jugador"
        aria-autocomplete="list"
        aria-expanded={showList}
        aria-controls={showList ? listId : undefined}
        aria-activedescendant={activeId}
        placeholder="buscar jugador..."
        autoComplete="off"
        value={text}
        onFocus={() => {
          setOpen(true);
          onFocus();
        }}
        onChange={(event) => {
          setText(event.target.value);
          setHighlighted(0);
          setOpen(true);
        }}
        onKeyDown={handleKeyDown}
      />
      {showPanel ? (
        <div className={styles.panel}>
          {status === 'loading' || status === 'idle' ? <ChalkNote variant="loading" /> : null}
          {status === 'error' ? (
            <ChalkNote variant="error" action={{ label: 'reintentar', onClick: onRetry }}>
              no se pudo conectar con la pizarra
            </ChalkNote>
          ) : null}
          {status === 'success' && suggestions.length === 0 ? (
            <ChalkNote variant="info">no hay nadie con ese nombre en la pizarra</ChalkNote>
          ) : null}
          {showList ? (
            <ul id={listId} role="listbox" aria-label="jugadores" className={styles.list}>
              {suggestions.map((player, index) => (
                // El teclado se maneja en el input (patrón combobox), no en cada opción
                // eslint-disable-next-line jsx-a11y/click-events-have-key-events
                <li
                  key={player.id}
                  id={`${listId}-${index}`}
                  role="option"
                  aria-selected={index === highlighted}
                  className={`${styles.option} ${index === highlighted ? styles.highlighted : ''}`}
                  // Evita que el input pierda el foco antes del clic
                  onMouseDown={(event) => event.preventDefault()}
                  onPointerEnter={() => setHighlighted(index)}
                  onClick={() => choose(player)}
                >
                  <span className={styles.name}>{player.name}</span>
                  <span className={styles.meta}>
                    {positionLabel(player.position)} · {player.team}
                  </span>
                </li>
              ))}
            </ul>
          ) : null}
        </div>
      ) : null}
    </div>
  );
}
