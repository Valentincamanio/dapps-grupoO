// La pizarra: hojas de post-its con los jugadores del catálogo.
import { Link, useLocation } from 'react-router';
import { Board } from '../components/Board';
import { ChalkNote } from '../components/ChalkNote';
import { Pager } from '../components/Pager';
import { PostIt } from '../components/PostIt';
import { useBoardFilters } from '../hooks/useBoardFilters';
import { usePlayers } from '../hooks/usePlayers';
import styles from './BoardPage.module.css';

export function BoardPage() {
  const location = useLocation();
  const { filters, setPage } = useBoardFilters();
  const { status, sheet, retry } = usePlayers(filters);

  function renderContent() {
    if (status === 'loading' || status === 'idle') return <ChalkNote variant="loading" />;
    if (status === 'error' || !sheet) {
      return (
        <ChalkNote variant="error" action={{ label: 'reintentar', onClick: retry }}>
          no se pudo conectar con la pizarra
        </ChalkNote>
      );
    }
    if (sheet.isOutOfRange) {
      return (
        <ChalkNote
          variant="info"
          action={{ label: 'volver a la primera hoja', onClick: () => setPage(1) }}
        >
          esta hoja de la pizarra está vacía
        </ChalkNote>
      );
    }
    if (sheet.isEmptyCombination) {
      return <ChalkNote variant="info">no hay jugadores en la pizarra</ChalkNote>;
    }

    return (
      <>
        <ul className={styles.grid}>
          {sheet.players.map((player) => (
            <li key={player.id}>
              <PostIt
                player={player}
                href={`/jugadores/${player.id}`}
                LinkComponent={Link}
                linkState={{ boardSearch: location.search }}
              />
            </li>
          ))}
        </ul>
        <Pager
          sheet={sheet.sheet}
          totalSheets={sheet.totalSheets}
          hasPrevious={sheet.hasPrevious}
          hasNext={sheet.hasNext}
          onPrevious={() => setPage(sheet.sheet - 1)}
          onNext={() => setPage(sheet.sheet + 1)}
        />
      </>
    );
  }

  return (
    <Board>
      <div className={styles.content}>{renderContent()}</div>
    </Board>
  );
}
