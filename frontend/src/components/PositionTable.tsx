// Posiciones de un portfolio: por jugador, tokens, participación, comprometidos, precio promedio,
// invertido, precio por token y score de la cotización usada, valor, ganancia y rentabilidad
// (FR-036). Los campos que no vienen (los de costo del superusuario, los comprometidos de un
// portfolio a una fecha) muestran un guion. Recibe los datos: no pide nada a la API.
import { useState, type ElementType, type ReactNode } from 'react';
import type { PortfolioPosition } from '../types/market';
import { formatScore, formatSignedPercent } from '../utils/market';
import { positionLabel } from '../utils/positions';
import { formatCredits, formatTokenPrice } from '../utils/quotes';
import { ChalkButton } from './ChalkButton';
import { ProfitBadge } from './ProfitBadge';
import styles from './PositionTable.module.css';

interface PositionTableProps {
  positions: PortfolioPosition[];
  caption?: string;
  emptyMessage?: string;
  // Con la dirección de la ficha, el nombre del jugador es un enlace
  playerHref?: (playerId: number) => string;
  // Por defecto un <a>; la página pasa el Link del router
  LinkComponent?: ElementType;
}

interface LinkProps {
  to: string;
  className?: string;
  children?: ReactNode;
}

function DefaultLink({ to, className, children }: LinkProps) {
  return (
    <a href={to} className={className}>
      {children}
    </a>
  );
}

const DEFAULT_CAPTION = 'Tus posiciones, por jugador';
const DEFAULT_EMPTY_MESSAGE = 'todavía no tenés tokens de ningún jugador.';

export function PositionTable({
  positions,
  caption = DEFAULT_CAPTION,
  emptyMessage = DEFAULT_EMPTY_MESSAGE,
  playerHref,
  LinkComponent = DefaultLink,
}: PositionTableProps) {
  // Las columnas de detalle arrancan ocultas: lo primero es qué tenés y cuánto vale
  const [detailed, setDetailed] = useState(false);

  if (positions.length === 0) {
    return <p className={styles.empty}>{emptyMessage}</p>;
  }

  return (
    <div className={styles.wrapper}>
      <ChalkButton className={styles.toggle} aria-pressed={detailed} onClick={() => setDetailed(!detailed)}>
        {detailed ? 'ocultar detalle' : 'ver detalle'}
      </ChalkButton>
      <table className={`${styles.table} ${detailed ? styles.detailed : ''}`}>
        <caption className={styles.caption}>{caption}</caption>
      <thead>
        <tr>
          <th scope="col">Jugador</th>
          <th scope="col">Tokens</th>
          <th scope="col" className={styles.extra}>Participación</th>
          <th scope="col" className={styles.extra}>Comprometidos</th>
          <th scope="col" className={styles.extra}>Precio promedio</th>
          <th scope="col" className={styles.extra}>Invertido</th>
          <th scope="col">Precio por token</th>
          <th scope="col" className={styles.extra}>Score</th>
          <th scope="col">Valor</th>
          <th scope="col">Ganancia</th>
          <th scope="col">Rentabilidad</th>
        </tr>
      </thead>
      <tbody>
        {positions.map((position) => (
          <tr key={position.player.id}>
            <th scope="row" data-label="Jugador" className={styles.player}>
              {playerHref ? (
                <LinkComponent to={playerHref(position.player.id)} className={styles.name}>
                  {position.player.name}
                </LinkComponent>
              ) : (
                <span className={styles.name}>{position.player.name}</span>
              )}
              <span className={styles.detail}>
                {positionLabel(position.player.position)}, {position.player.team}
                {position.player.active ? null : ' (inactivo)'}
              </span>
            </th>
            <td data-label="Tokens" className={styles.number}>
              {position.tokens}
            </td>
            <td data-label="Participación" className={`${styles.number} ${styles.extra}`}>
              {position.sharePercent} %
            </td>
            <td data-label="Comprometidos" className={`${styles.number} ${styles.extra}`}>
              <OrDash value={position.committed} />
            </td>
            <td data-label="Precio promedio" className={`${styles.number} ${styles.extra}`}>
              <OrDash value={position.averagePrice} format={formatTokenPrice} />
            </td>
            <td data-label="Invertido" className={`${styles.number} ${styles.extra}`}>
              <OrDash value={position.invested} format={formatCredits} />
            </td>
            <td data-label="Precio por token" className={styles.number}>
              {formatTokenPrice(position.quote.tokenPrice)}
            </td>
            <td data-label="Score" className={`${styles.number} ${styles.extra}`}>
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
    </div>
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
