// La lista para elegir a los potenciados de una semana: un buscador por jugador o equipo, la liga y
// las filas con la cotización actual y "Agregar". Los ya elegidos se marcan y, con los cuatro lugares
// llenos, no se puede agregar a nadie más. No conoce la API: la página filtra y pasa las filas.
import { useId, type CSSProperties } from 'react';
import { LEAGUES, type League, type Player } from '../types/api';
import type { QuoteBadge } from '../types/quotes';
import { BOOSTS_PER_WEEK } from '../utils/boosts';
import { hueOf, initialsOf } from '../utils/format';
import { leagueLabel, positionLabel } from '../utils/positions';
import { formatCredits } from '../utils/quotes';
import { BoltIcon } from './BoltIcon';
import { ChalkNote } from './ChalkNote';
import { TapeTab } from './TapeTab';
import styles from './BoostPicker.module.css';

interface BoostPickerProps {
  query: string;
  onQueryChange: (query: string) => void;
  league: League | null;
  onLeagueChange: (league: League | null) => void;
  // Las filas visibles y cuántos jugadores cumplen el filtro en total
  rows: Player[];
  total: number;
  chosenIds: number[];
  onAdd: (player: Player) => void;
  quoteFor: (playerId: number) => QuoteBadge;
  status: 'idle' | 'loading' | 'success' | 'error';
  onRetry: () => void;
}

function priceText(badge: QuoteBadge): string {
  if (badge.status === 'loading') return '...';
  return badge.status === 'ready' ? formatCredits(badge.quote.value) : '—';
}

export function BoostPicker({
  query,
  onQueryChange,
  league,
  onLeagueChange,
  rows,
  total,
  chosenIds,
  onAdd,
  quoteFor,
  status,
  onRetry,
}: BoostPickerProps) {
  const titleId = useId();
  const full = chosenIds.length >= BOOSTS_PER_WEEK;

  function renderRows() {
    if (status === 'loading' || status === 'idle') return <ChalkNote variant="loading">cargando jugadores...</ChalkNote>;
    if (status === 'error') {
      return (
        <ChalkNote variant="error" action={{ label: 'reintentar', onClick: onRetry }}>
          no se pudieron cargar los jugadores
        </ChalkNote>
      );
    }
    if (rows.length === 0) return <p className={styles.emptyRow}>no hay jugadores con ese filtro</p>;
    return (
      <ul className={styles.rows} aria-label="jugadores para potenciar">
        {rows.map((player) => (
          <li key={player.id}>
            <span
              className={styles.avatar}
              style={{ '--hue': hueOf(player.team) } as CSSProperties}
              data-initials={initialsOf(player.name)}
              aria-hidden="true"
            />
            <span className={styles.info}>
              <span className={styles.name}>{player.name}</span>
              <span className={styles.meta}>
                {positionLabel(player.position)} · {player.team} · {leagueLabel(player.league)}
              </span>
            </span>
            <span className={styles.price}>
              {priceText(quoteFor(player.id))}
              <small>cotización</small>
            </span>
            {chosenIds.includes(player.id) ? (
              <span className={styles.picked}>
                <BoltIcon size={12} />
                elegido
              </span>
            ) : (
              <button
                type="button"
                className={styles.add}
                aria-label={`agregar a ${player.name}`}
                disabled={full}
                onClick={() => onAdd(player)}
              >
                Agregar
              </button>
            )}
          </li>
        ))}
      </ul>
    );
  }

  return (
    <section className={styles.picker} aria-labelledby={titleId}>
      <div className={styles.head}>
        <h3 id={titleId} className={styles.title}>
          Elegir jugadores
        </h3>
        <span className={styles.count}>
          {full ? 'ya elegiste los 4: quitá uno para cambiarlo' : `${chosenIds.length} de ${BOOSTS_PER_WEEK} elegidos`}
        </span>
      </div>
      <input
        type="search"
        className={styles.search}
        aria-label="buscar jugador o equipo"
        placeholder="buscar jugador o equipo..."
        value={query}
        onChange={(event) => onQueryChange(event.target.value)}
      />
      <div className={styles.chips} role="group" aria-label="filtrar por liga">
        <TapeTab label="todas" active={league === null} onSelect={() => onLeagueChange(null)} />
        {LEAGUES.map((candidate) => (
          <TapeTab
            key={candidate}
            label={leagueLabel(candidate)}
            active={league === candidate}
            onSelect={() => onLeagueChange(candidate)}
          />
        ))}
      </div>
      {renderRows()}
      {status === 'success' && total > rows.length ? (
        <p className={styles.more}>
          se muestran {rows.length} de {total}: buscá por nombre o equipo para encontrar al resto
        </p>
      ) : null}
    </section>
  );
}
