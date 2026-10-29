// Posiciones de un portfolio: por jugador, tokens, participación, comprometidos, precio promedio,
// invertido, precio por token y score de la cotización usada, valor, ganancia y rentabilidad
// (FR-036). Los campos que no vienen (los de costo del superusuario, los comprometidos de un
// portfolio a una fecha) muestran un guion. Recibe los datos: no pide nada a la API.
import type { PortfolioPosition } from '../types/market';
import { formatScore, formatSignedPercent } from '../utils/market';
import { positionLabel } from '../utils/positions';
import { formatCredits, formatTokenPrice } from '../utils/quotes';
import { ProfitBadge } from './ProfitBadge';
import styles from './PositionTable.module.css';

interface PositionTableProps {
  positions: PortfolioPosition[];
  caption?: string;
  emptyMessage?: string;
}

const DEFAULT_CAPTION = 'Tus posiciones, por jugador';
const DEFAULT_EMPTY_MESSAGE = 'todavía no tenés tokens de ningún jugador.';

export function PositionTable({ positions, caption = DEFAULT_CAPTION, emptyMessage = DEFAULT_EMPTY_MESSAGE }: PositionTableProps) {
  if (positions.length === 0) {
    return <p className={styles.empty}>{emptyMessage}</p>;
  }

  return (
    <table className={styles.table}>
      <caption className={styles.caption}>{caption}</caption>
      <thead>
        <tr>
          <th scope="col">Jugador</th>
          <th scope="col">Tokens</th>
          <th scope="col">Participación</th>
          <th scope="col">Comprometidos</th>
          <th scope="col">Precio promedio</th>
          <th scope="col">Invertido</th>
          <th scope="col">Precio por token</th>
          <th scope="col">Score</th>
          <th scope="col">Valor</th>
          <th scope="col">Ganancia</th>
          <th scope="col">Rentabilidad</th>
        </tr>
      </thead>
      <tbody>
        {positions.map((position) => (
          <tr key={position.player.id}>
            <th scope="row" data-label="Jugador" className={styles.player}>
              <span className={styles.name}>{position.player.name}</span>
              <span className={styles.detail}>
                {positionLabel(position.player.position)}, {position.player.team}
                {position.player.active ? null : ' (inactivo)'}
              </span>
            </th>
            <td data-label="Tokens" className={styles.number}>
              {position.tokens}
            </td>
            <td data-label="Participación" className={styles.number}>
              {position.sharePercent} %
            </td>
            <td data-label="Comprometidos" className={styles.number}>
              <OrDash value={position.committed} />
            </td>
            <td data-label="Precio promedio" className={styles.number}>
              <OrDash value={position.averagePrice} format={formatTokenPrice} />
            </td>
            <td data-label="Invertido" className={styles.number}>
              <OrDash value={position.invested} format={formatCredits} />
            </td>
            <td data-label="Precio por token" className={styles.number}>
              {formatTokenPrice(position.quote.tokenPrice)}
            </td>
            <td data-label="Score" className={styles.number}>
              {formatScore(position.quote.score)}
            </td>
            <td data-label="Valor" className={styles.number}>
              {formatCredits(position.value)}
            </td>
            <td data-label="Ganancia">
              <ProfitBadge amount={position.profit} />
            </td>
            <td data-label="Rentabilidad" className={styles.number}>
              <OrDash value={position.profitPercent} format={formatSignedPercent} />
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}

interface OrDashProps {
  value: number | null | undefined;
  format?: (value: number) => string;
}

// Un guion para lo que no se informa; el lector de pantalla dice "sin datos"
function OrDash({ value, format = String }: OrDashProps) {
  if (value === undefined || value === null) {
    return (
      <>
        <span aria-hidden="true">—</span>
        <span className={styles.hidden}>sin datos</span>
      </>
    );
  }
  return <>{format(value)}</>;
}
