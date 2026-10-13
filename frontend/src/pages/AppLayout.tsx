// Marco común de todas las pantallas: pizarrón, barra superior y la página activa.
import { Link, Outlet, useLocation, useNavigate } from 'react-router';
import { Board } from '../components/Board';
import { SearchBox } from '../components/SearchBox';
import { TopBar } from '../components/TopBar';
import { useCatalog } from '../hooks/useCatalog';
import { useSession } from '../hooks/useSession';
import { formatBalance } from '../utils/format';
import styles from './AppLayout.module.css';

export function AppLayout() {
  const { status, profile } = useSession();
  const location = useLocation();
  const navigate = useNavigate();
  const catalog = useCatalog();

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
        searchSlot={
          <SearchBox
            players={catalog.players}
            status={catalog.status}
            onFocus={() => void catalog.ensureLoaded()}
            onRetry={() => void catalog.retry()}
            // Sin state: la ficha vuelve a la pizarra completa
            onSelect={(player) => navigate(`/jugadores/${player.id}`)}
          />
        }
        balance={profile ? formatBalance(profile.balance) : undefined}
        accountLink={accountLink}
      />
      <main className={styles.content}>
        <Outlet />
      </main>
    </Board>
  );
}
