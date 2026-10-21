// Ranking por cotización: una fila por jugador con su puesto, datos, cotización, variación y la
// estrategia y versión usadas. Por debajo de un ancho, cada fila pasa a ser una tarjeta (CSS).
import type { ElementType, ReactNode } from 'react';
import type { RankingRow } from '../types/quotes';
import { leagueLabel } from '../utils/positions';
import { formatCredits } from '../utils/quotes';
import { VariationBadge } from './VariationBadge';
import styles from './RankingList.module.css';

interface LinkProps {
  to: string;
  className?: string;
  children?: ReactNode;
}

interface RankingListProps {
  rows: RankingRow[];
  // Puesto de la primera fila (la hoja 2 de 10 filas empieza en el 11)
  firstRank: number;
  // Dirección de la ficha de un jugador
  playerHref: (playerId: number) => string;
  // Por defecto un <a>; la página pasa el Link del router
  LinkComponent?: ElementType;
}

function DefaultLink({ to, className, children }: LinkProps) {
  return (
    <a href={to} className={className}>
      {children}
    </a>
  );
}

export function RankingList({
  rows,
  firstRank,
  playerHref,
  LinkComponent = DefaultLink,
}: RankingListProps) {
  return (
    <table className={styles.table}>
      <caption className={styles.caption}>Jugadores por cotización, de mayor a menor</caption>
      <thead>
        <tr>
          <th scope="col">Puesto</th>
          <th scope="col">Jugador</th>
          <th scope="col">Equipo</th>
          <th scope="col">Liga</th>
          <th scope="col">Cotización</th>
          <th scope="col">Variación</th>
          <th scope="col">Estrategia</th>
        </tr>
      </thead>
      <tbody>
        {rows.map((row, index) => (
          <tr key={row.playerId}>
            <td data-label="Puesto" className={styles.rank}>
              {firstRank + index}
            </td>
            <th scope="row" data-label="Jugador" className={styles.player}>
              <LinkComponent to={playerHref(row.playerId)} className={styles.link}>
                {row.name}
              </LinkComponent>
            </th>
            <td data-label="Equipo">{row.team}</td>
            <td data-label="Liga">{leagueLabel(row.league)}</td>
            <td data-label="Cotización" className={styles.value}>
              {formatCredits(row.quote.value)}
            </td>
            <td data-label="Variación">
              {row.quote.variation ? (
                <VariationBadge variation={row.quote.variation} />
              ) : (
                <span className={styles.muted}>sin variación</span>
              )}
            </td>
            <td data-label="Estrategia">
              {row.quote.strategy.name}, versión {row.quote.strategy.version}
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}
