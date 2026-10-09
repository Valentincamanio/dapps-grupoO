// Cancha en tiza que funciona solo como filtro de posición: no dibuja jugadores.
import type { PitchZone } from '../types/board';
import { positionPlural, zoneLabel, zoneToPosition } from '../utils/positions';
import styles from './Pitch.module.css';

interface PitchProps {
  activeZone: PitchZone | null;
  onZoneSelect: (zone: PitchZone | null) => void;
}

const ZONES: PitchZone[] = ['GOAL', 'DEFENSE', 'MIDFIELD', 'ATTACK'];

// Líneas de la cancha, decorativas
function PitchLines() {
  return (
    <svg className={styles.lines} viewBox="0 0 400 200" aria-hidden="true" focusable="false">
      <rect x="4" y="4" width="392" height="192" fill="none" />
      <line x1="200" y1="4" x2="200" y2="196" />
      <circle cx="200" cy="100" r="28" fill="none" />
      <rect x="4" y="60" width="44" height="80" fill="none" />
      <rect x="352" y="60" width="44" height="80" fill="none" />
    </svg>
  );
}

export function Pitch({ activeZone, onZoneSelect }: PitchProps) {
  return (
    <div className={styles.pitch} role="group" aria-label="filtrar por posición">
      <div className={styles.field}>
        <PitchLines />
        <div className={styles.zones}>
          {ZONES.map((zone) => {
            const active = activeZone === zone;
            return (
              <button
                key={zone}
                type="button"
                className={`${styles.zone} ${active ? styles.active : ''}`}
                aria-pressed={active}
                aria-label={`Filtrar por ${positionPlural(zoneToPosition(zone))}`}
                onClick={() => onZoneSelect(active ? null : zone)}
              >
                <span aria-hidden="true">{zoneLabel(zone)}</span>
              </button>
            );
          })}
        </div>
      </div>
      <button
        type="button"
        className={`${styles.all} ${activeZone === null ? styles.active : ''}`}
        aria-pressed={activeZone === null}
        aria-label="Ver todos"
        onClick={() => onZoneSelect(null)}
      >
        <span aria-hidden="true">todos</span>
      </button>
    </div>
  );
}
