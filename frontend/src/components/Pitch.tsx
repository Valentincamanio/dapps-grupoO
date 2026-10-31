// Filtro de posición: un chip por zona de la cancha, más "todos".
import type { PitchZone } from '../types/board';
import { positionPlural, zoneToPosition } from '../utils/positions';
import styles from './Pitch.module.css';

interface PitchProps {
  activeZone: PitchZone | null;
  onZoneSelect: (zone: PitchZone | null) => void;
}

// De la delantera al arco
const ZONES: PitchZone[] = ['ATTACK', 'MIDFIELD', 'DEFENSE', 'GOAL'];

export function Pitch({ activeZone, onZoneSelect }: PitchProps) {
  return (
    <div className={styles.pitch} role="group" aria-label="filtrar por posición">
      {ZONES.map((zone) => {
        const active = activeZone === zone;
        const plural = positionPlural(zoneToPosition(zone));
        return (
          <button
            key={zone}
            type="button"
            className={`${styles.zone} ${active ? styles.active : ''}`}
            aria-pressed={active}
            aria-label={`Filtrar por ${plural}`}
            onClick={() => onZoneSelect(active ? null : zone)}
          >
            <span aria-hidden="true">{plural}</span>
          </button>
        );
      })}
      <button
        type="button"
        className={`${styles.zone} ${activeZone === null ? styles.active : ''}`}
        aria-pressed={activeZone === null}
        aria-label="Ver todos"
        onClick={() => onZoneSelect(null)}
      >
        <span aria-hidden="true">todos</span>
      </button>
    </div>
  );
}
