// Encabezado de la app: logo, secciones (quien lo usa decide cuáles ve cada rol), saldo libre opcional
// y enlace de cuenta. En el celular las secciones pasan a una barra fija abajo (CSS).
import type { ReactNode } from 'react';
import { Link } from 'react-router';
import styles from './TopBar.module.css';

interface TopBarProps {
  navSlot?: ReactNode;
  // El saldo libre ya formateado: lo que se puede usar, sin lo reservado para compras (FR-062)
  balance?: string;
  accountLink: ReactNode;
}

export function TopBar({ navSlot, balance, accountLink }: TopBarProps) {
  return (
    <header className={styles.header}>
      <div className={styles.inner}>
        <Link to="/pizarra" className={styles.logo} aria-label="La Pizarra, ir al inicio">
          <svg viewBox="0 0 32 32" aria-hidden="true" focusable="false">
            <circle cx="16" cy="16" r="15" className={styles.ball} />
            <path d="M16 9l5 3.6-1.9 5.9h-6.2L11 12.6z" className={styles.patch} />
            <path
              d="M16 9V3.5M21 12.6l5-1.7M19.1 18.5l3.2 4.4M12.9 18.5l-3.2 4.4M11 12.6l-5-1.7"
              className={styles.seams}
            />
          </svg>
          La Pizarra
        </Link>
        {navSlot ? (
          <nav className={styles.nav} aria-label="secciones">
            {navSlot}
          </nav>
        ) : null}
        <div className={styles.right}>
          {balance !== undefined ? (
            <div className={styles.balance}>
              Saldo libre: <b>{balance}</b>
            </div>
          ) : null}
          <div className={styles.account}>{accountLink}</div>
        </div>
      </div>
    </header>
  );
}
