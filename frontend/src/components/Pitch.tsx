// Cancha en tiza que funciona solo como filtro de posición: no dibuja jugadores.
import type { PitchZone } from '../types/board';
import { positionPlural, zoneLabel, zoneToPosition } from '../utils/positions';
import styles from './Pitch.module.css';

interface PitchProps {
  activeZone: PitchZone | null;
  onZoneSelect: (zone: PitchZone | null) => void;
}

// De arriba (ataque) a abajo (arco), como en el mockup
const ZONES: PitchZone[] = ['ATTACK', 'MIDFIELD', 'DEFENSE', 'GOAL'];

// Líneas de la cancha, decorativas
function PitchLines() {
  return (
    <svg
      className={styles.lines}
      viewBox="0 0 68 100"
      preserveAspectRatio="none"
      aria-hidden="true"
      focusable="false"
    >
      <rect x="2" y="2" width="64" height="96" fill="none" />
      <line x1="2" y1="50" x2="66" y2="50" />
      <circle cx="34" cy="50" r="9" fill="none" />
      <rect x="14" y="2" width="40" height="16" fill="none" />
      <rect x="24" y="2" width="20" height="6" fill="none" />
      <rect x="14" y="82" width="40" height="16" fill="none" />
      <rect x="24" y="92" width="20" height="6" fill="none" />
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
