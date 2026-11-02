// Marco común de todas las pantallas: la barra superior y la pantalla activa. La ficha de un jugador
// abierta desde otra pantalla es un panel encima de esa pantalla, que sigue montada y con su dirección
// (filtros y hoja); abierta directamente, por ejemplo con un enlace, es una pantalla más.
import { useCallback, useState, type ReactNode } from 'react';
import {
  Link,
  matchPath,
  UNSAFE_LocationContext,
  useLocation,
  useNavigate,
  useNavigationType,
  useOutlet,
  type Location,
} from 'react-router';
import { Board } from '../components/Board';
import { NavIcon } from '../components/NavIcon';
import { SidePanel } from '../components/SidePanel';
import { ThemeToggle } from '../components/ThemeToggle';
import { TopBar } from '../components/TopBar';
import { useSession } from '../hooks/useSession';
import { useTheme } from '../hooks/useTheme';
import { formatBalance, initialsOf } from '../utils/format';
import { backLabelOf, topSectionOf, type TopSection } from '../utils/sections';
import styles from './AppLayout.module.css';

// Una pantalla ya resuelta: lo que se dibuja y la dirección que le corresponde
interface PageView {
  location: Location;
  outlet: ReactNode;
}

// Lo que la ficha necesita saber del panel en el que se abrió
export interface PlayerPanelContext {
  back: { to: string; label: string };
}

// Encima de estas pantallas no se abre la ficha: después de entrar no hay nada que mostrar atrás
const NO_PANEL_ORIGINS = ['/login', '/register'];

function currentAttribute(active: TopSection | null, section: TopSection): 'page' | undefined {
  return active === section ? 'page' : undefined;
}

export function AppLayout() {
  const { status, profile } = useSession();
  const { theme, toggle: toggleTheme } = useTheme();
  const location = useLocation();
  const navigate = useNavigate();
  const navigationType = useNavigationType();
  const onPlayer = matchPath('/jugadores/:id', location.pathname) !== null;

  // La última pantalla que no fue una ficha, para dejarla debajo del panel
  const [origin, setOrigin] = useState<PageView | null>(null);
  const background = onPlayer && origin && !NO_PANEL_ORIGINS.includes(origin.location.pathname) ? origin : null;
  const backTo = background ? `${background.location.pathname}${background.location.search}` : '';
  const panel: PlayerPanelContext | undefined = background
    ? { back: { to: backTo, label: backLabelOf(background.location.pathname) } }
    : undefined;

  const outlet = useOutlet(panel);
  if (!onPlayer && origin?.location !== location) setOrigin({ location, outlet });

  const page = background ?? { location, outlet };
  const closePanel = useCallback(() => navigate(backTo), [navigate, backTo]);

  const active = topSectionOf(page.location.pathname);
  // Los jugadores no tienen entrada propia: el logo lleva a la pizarra. Sin secciones del rol (un
  // visitante) no hay barra de secciones
  let navSlot: ReactNode;
  if (profile?.role === 'USER') {
    // El portfolio, las operaciones y las órdenes son de quien opera: solo USER
    navSlot = (
      <Link to="/portfolio" aria-current={currentAttribute(active, 'team')}>
        <NavIcon section="team" />
        Mi equipo
      </Link>
    );
  } else if (profile?.role === 'ADMIN') {
    // La entrada a la administración existe solo para ADMIN (FR-064)
    navSlot = (
      <Link to="/admin" aria-current={currentAttribute(active, 'admin')}>
        <NavIcon section="admin" />
        Administración
      </Link>
    );
  }
  const accountLink =
    status === 'anonymous' ? (
      <Link to="/login" state={{ from: location }} className={styles.enter}>
        entrar
      </Link>
    ) : (
      <Link to="/vestuario" className={styles.avatar} aria-label="vestuario">
        {profile ? initialsOf(profile.username) : ''}
      </Link>
    );

  return (
    <Board>
      {/* Con el panel abierto, lo de atrás no recibe el foco ni el teclado */}
      <div {...(background ? { inert: '' } : {})}>
        <TopBar
          navSlot={navSlot}
          themeToggle={<ThemeToggle theme={theme} onToggle={toggleTheme} />}
          balance={profile ? formatBalance(profile.availableBalance) : undefined}
          accountLink={accountLink}
        />
        <main className={styles.content}>
          {/* La pantalla de atrás sigue leyendo su propia dirección, no la de la ficha */}
          <UNSAFE_LocationContext.Provider value={{ location: page.location, navigationType }}>
            {page.outlet}
          </UNSAFE_LocationContext.Provider>
        </main>
      </div>
      {background ? (
        <SidePanel label="ficha del jugador" onClose={closePanel}>
          {outlet}
        </SidePanel>
      ) : null}
    </Board>
  );
}
