// Resultado de una simulación: por jugador la cotización actual, la simulada y la diferencia con
// signo y texto ("sube"/"baja"), sin depender del color (FR-059). Un jugador sin cotización actual
// se compara contra los 100,00 de partida. Por debajo de un ancho cada fila es una tarjeta (CSS).
import type { SimulationRow } from '../types/quotes';
import { leagueLabel, positionLabel } from '../utils/positions';
import { amountDirection, directionText, formatCredits, formatSignedCredits } from '../utils/quotes';
import styles from './SimulationTable.module.css';

interface SimulationTableProps {
  rows: SimulationRow[];
}

const TRIANGLES = { up: '▲', down: '▼', flat: '' } as const;

function Difference({ row }: { row: SimulationRow }) {
  const direction = amountDirection(row.difference);
  return (
    <span className={`${styles.difference} ${styles[direction]}`}>
      {TRIANGLES[direction] ? (
        <span className={styles.triangle} aria-hidden="true">
          {TRIANGLES[direction]}
        </span>
      ) : null}
      <span>{formatSignedCredits(row.difference)}</span>
      <span className={styles.text}>{directionText(direction)}</span>
      {row.currentValue === null ? <span className={styles.muted}>(contra 100,00 cr.)</span> : null}
    </span>
  );
}

export function SimulationTable({ rows }: SimulationTableProps) {
  return (
    <table className={styles.table}>
      <caption className={styles.caption}>
        Cotización actual y simulada por jugador, de mayor a menor simulada
      </caption>
      <thead>
        <tr>
          <th scope="col">Jugador</th>
          <th scope="col">Equipo</th>
          <th scope="col">Liga</th>
          <th scope="col">Posición</th>
          <th scope="col">Actual</th>
          <th scope="col">Simulada</th>
          <th scope="col">Diferencia</th>
        </tr>
      </thead>
      <tbody>
        {rows.map((row) => (
          <tr key={row.playerId}>
            <th scope="row" data-label="Jugador" className={styles.player}>
              {row.name}
            </th>
            <td data-label="Equipo">{row.team}</td>
            <td data-label="Liga">{leagueLabel(row.league)}</td>
            <td data-label="Posición">{positionLabel(row.position)}</td>
            <td data-label="Actual" className={styles.value}>
              {row.currentValue === null ? (
                <span className={styles.muted}>sin cotización</span>
              ) : (
                formatCredits(row.currentValue)
              )}
            </td>
            <td data-label="Simulada" className={styles.value}>
              {formatCredits(row.simulatedValue)}
            </td>
            <td data-label="Diferencia">
              <Difference row={row} />
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}
