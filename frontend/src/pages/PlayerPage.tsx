// Ficha de un jugador: hoja de scout dentro del pizarrón.
import { Link, useLocation, useParams } from 'react-router';
import { Board } from '../components/Board';
import { ChalkNote } from '../components/ChalkNote';
import { ScoutSheet } from '../components/ScoutSheet';
import { usePlayer } from '../hooks/usePlayer';
import styles from './PlayerPage.module.css';

interface PlayerLocationState {
  boardSearch?: string;
}

export function PlayerPage() {
  const { id } = useParams();
  const location = useLocation();
  const { status, player, notFound, retry } = usePlayer(id);

  // Vuelve a la pizarra con los mismos filtros y hoja desde donde se abrió la ficha
  const boardSearch = (location.state as PlayerLocationState | null)?.boardSearch;
  const backLink = <Link to={`/pizarra${boardSearch ?? ''}`}>volver a la pizarra</Link>;

  function renderContent() {
    if (notFound) {
      return (
        <>
          <ChalkNote variant="info">jugador no encontrado</ChalkNote>
          <p className={styles.back}>{backLink}</p>
        </>
      );
    }
    if (status === 'loading' || status === 'idle') return <ChalkNote variant="loading" />;
    if (status === 'error' || !player) {
      return (
        <>
          <ChalkNote variant="error" action={{ label: 'reintentar', onClick: retry }}>
            no se pudo conectar con la pizarra
          </ChalkNote>
          <p className={styles.back}>{backLink}</p>
        </>
      );
    }
    return <ScoutSheet player={player} backLink={backLink} />;
  }

  return (
    <Board>
      <div className={styles.content}>{renderContent()}</div>
    </Board>
  );
}
