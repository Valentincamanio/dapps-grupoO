// Encabezado de la app: logo (lleva al inicio), secciones (quien lo usa decide cuáles ve cada rol),
// el botón de tema, saldo libre opcional y enlace de cuenta. Los links los arma quien lo usa: el
// componente no conoce el router. En el celular las secciones pasan a una barra fija abajo (CSS).
import type { ReactNode } from 'react';
import styles from './TopBar.module.css';

interface TopBarProps {
  // El logo, con el link al inicio
  homeLink: ReactNode;
  navSlot?: ReactNode;
  // Va a la izquierda del saldo
  themeToggle?: ReactNode;
  // El saldo libre ya formateado: lo que se puede usar, sin lo reservado para compras (FR-062)
  balance?: string;
  accountLink: ReactNode;
}

export function TopBar({ homeLink, navSlot, themeToggle, balance, accountLink }: TopBarProps) {
  return (
    <header className={styles.header}>
      <div className={styles.inner}>
        {homeLink}
        {navSlot ? (
          <nav className={styles.nav} aria-label="secciones">
            {navSlot}
          </nav>
        ) : null}
        <div className={styles.right}>
          {themeToggle}
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
