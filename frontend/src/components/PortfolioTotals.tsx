// Totales de un portfolio (FR-038): saldo libre y reservado (solo en el actual), saldo total,
// invertido, valor de las posiciones, ganancia o pérdida, ganancia realizada y patrimonio. Recibe
// los datos: no pide nada a la API.
import type { ReactNode } from 'react';
import type { PortfolioTotals as Totals } from '../types/market';
import { formatCredits } from '../utils/quotes';
import { ProfitBadge } from './ProfitBadge';
import styles from './PortfolioTotals.module.css';

interface PortfolioTotalsProps {
  totals: Totals;
  title?: string;
}

export function PortfolioTotals({ totals, title = 'Totales' }: PortfolioTotalsProps) {
  return (
    <section className={styles.totals} aria-labelledby="portfolio-totals-title">
      <h2 id="portfolio-totals-title" className={styles.title}>
        {title}
      </h2>
      <dl className={styles.list}>
        {totals.availableBalance === undefined ? null : (
          <Item label="Saldo libre">{formatCredits(totals.availableBalance)}</Item>
        )}
        {totals.reservedBalance === undefined ? null : (
          <Item label="Saldo reservado" secondary>
            {formatCredits(totals.reservedBalance)}
          </Item>
        )}
        <Item label="Saldo total" secondary={totals.availableBalance !== undefined}>
          {formatCredits(totals.balance)}
        </Item>
        <Item label="Invertido" secondary>
          {formatCredits(totals.invested)}
        </Item>
        <Item label="Valor de las posiciones">{formatCredits(totals.value)}</Item>
        <Item label="Ganancia o pérdida">
          <ProfitBadge amount={totals.profit} />
        </Item>
        <Item label="Ganancia realizada">
          <ProfitBadge amount={totals.realizedProfit} />
        </Item>
        <Item label="Patrimonio" highlighted>
          {formatCredits(totals.equity)}
        </Item>
      </dl>
    </section>
  );
}

interface ItemProps {
  label: string;
  highlighted?: boolean;
  // Va en chico, debajo de lo principal
  secondary?: boolean;
  children: ReactNode;
}

function Item({ label, highlighted = false, secondary = false, children }: ItemProps) {
  const classes = [styles.item, highlighted ? styles.highlighted : '', secondary ? styles.secondary : ''];
  return (
    <div className={classes.filter(Boolean).join(' ')}>
      <dt className={styles.label}>{label}</dt>
      <dd className={styles.value}>{children}</dd>
    </div>
  );
}
