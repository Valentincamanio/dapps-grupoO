// Marco común de todas las pantallas: pizarrón, barra superior y la página activa.
import { Link, Outlet, useLocation } from 'react-router';
import { Board } from '../components/Board';
import { TopBar } from '../components/TopBar';
import { useSession } from '../hooks/useSession';
import { formatBalance } from '../utils/format';
import styles from './AppLayout.module.css';

export function AppLayout() {
  const { status, profile } = useSession();
  const location = useLocation();

  const accountLink =
    status === 'anonymous' ? (
      <Link to="/login" state={{ from: location }}>
        entrar
      </Link>
    ) : (
      <Link to="/vestuario">vestuario</Link>
    );

  return (
    <Board>
      <TopBar
        balance={profile ? formatBalance(profile.balance) : undefined}
        accountLink={accountLink}
      />
      <main className={styles.content}>
        <Outlet />
      </main>
    </Board>
  );
}
