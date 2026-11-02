// Una sincronización manual: qué trae, la liga (o las cinco), el botón y, debajo, el estado y el
// informe que arma la página. No conoce la API.
import { useId, type ReactNode } from 'react';
import { LEAGUES, type League } from '../types/api';
import { leagueLabel } from '../utils/positions';
import { ChalkButton } from './ChalkButton';
import styles from './SyncPanel.module.css';

interface SyncPanelProps {
  title: string;
  description: string;
  buttonLabel: string;
  league: League | null;
  onLeagueChange: (league: League | null) => void;
  // Esta sincronización está corriendo
  busy: boolean;
  // Hay otra corriendo: el backend la rechazaría con 409
  disabled: boolean;
  onSync: () => void;
  children?: ReactNode;
}

const ALL = '';

export function SyncPanel({
  title,
  description,
  buttonLabel,
  league,
  onLeagueChange,
  busy,
  disabled,
  onSync,
  children,
}: SyncPanelProps) {
  const titleId = useId();
  const selectId = useId();
  return (
    <section className={styles.panel} aria-labelledby={titleId}>
      <h3 id={titleId} className={styles.title}>
        {title}
      </h3>
      <p className={styles.description}>{description}</p>
      <div className={styles.controls}>
        <label htmlFor={selectId} className={styles.label}>
          liga
        </label>
        <select
          id={selectId}
          className={styles.select}
          value={league ?? ALL}
          disabled={busy || disabled}
          onChange={(event) => onLeagueChange(event.target.value === ALL ? null : (event.target.value as League))}
        >
          <option value={ALL}>las cinco ligas</option>
          {LEAGUES.map((candidate) => (
            <option key={candidate} value={candidate}>
              {leagueLabel(candidate)}
            </option>
          ))}
        </select>
        <ChalkButton className={styles.button} busy={busy} disabled={disabled} onClick={onSync}>
          {buttonLabel}
        </ChalkButton>
      </div>
      {children}
    </section>
  );
}
