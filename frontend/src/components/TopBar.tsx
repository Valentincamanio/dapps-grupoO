// Encabezado del pizarrón: logo, buscador opcional, saldo opcional y enlace de cuenta.
import type { ReactNode } from 'react';
import { Link } from 'react-router';
import styles from './TopBar.module.css';

interface TopBarProps {
  searchSlot?: ReactNode;
  balance?: string;
  accountLink: ReactNode;
}

export function TopBar({ searchSlot, balance, accountLink }: TopBarProps) {
  return (
    <header className={styles.header}>
      <Link to="/pizarra" className={styles.logo} aria-label="La Pizarra, ir al inicio">
        La Pizarra
        <small>mercado de jugadores · temporada 26/27</small>
      </Link>
      {searchSlot ? <div className={styles.search}>{searchSlot}</div> : null}
      <div className={styles.right}>
        {balance !== undefined ? (
          <div className={styles.balance}>
            Saldo: <b>{balance}</b>
          </div>
        ) : null}
        <div className={styles.account}>{accountLink}</div>
      </div>
    </header>
  );
}
