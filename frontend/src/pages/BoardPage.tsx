// La pizarra: hojas de post-its con los jugadores del catálogo.
import { Link, useLocation } from 'react-router';
import { Board } from '../components/Board';
import { ChalkNote } from '../components/ChalkNote';
import { Pager } from '../components/Pager';
import { Pitch } from '../components/Pitch';
import { PostIt } from '../components/PostIt';
import { TapeTab } from '../components/TapeTab';
import { TeamSelect } from '../components/TeamSelect';
import { useBoardFilters } from '../hooks/useBoardFilters';
import { useCatalog } from '../hooks/useCatalog';
import { usePlayers } from '../hooks/usePlayers';
import { LEAGUES } from '../types/api';
import { describeEmptyCombination } from '../utils/filters';
import { leagueLabel, positionToZone, zoneToPosition } from '../utils/positions';
import { deriveTeams } from '../utils/teams';
import styles from './BoardPage.module.css';

export function BoardPage() {
  const location = useLocation();
  const { filters, setPage, setPosition, setLeague, setTeam, clear } = useBoardFilters();
  const catalog = useCatalog();
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
          action={{
            label: 'volver a la primera hoja',
            onClick: () => setPage(1),
          }}
        >
          esta hoja de la pizarra está vacía
        </ChalkNote>
      );
    }
    if (sheet.isEmptyCombination) {
      return (
        <ChalkNote variant="info" action={{ label: 'limpiar filtros', onClick: clear }}>
          {describeEmptyCombination(filters)}
        </ChalkNote>
      );
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
      <div className={styles.layout}>
        <aside className={styles.filters}>
          <Pitch
            activeZone={positionToZone(filters.position)}
            onZoneSelect={(zone) => setPosition(zoneToPosition(zone))}
          />
        </aside>
        <div className={styles.content}>
          <div className={styles.tabs} role="group" aria-label="filtrar por liga">
            <TapeTab
              label="todas"
              active={filters.league === null}
              onSelect={() => void setLeague(null)}
            />
            {LEAGUES.map((league) => (
              <TapeTab
                key={league}
                label={leagueLabel(league)}
                active={filters.league === league}
                onSelect={() => void setLeague(league)}
              />
            ))}
          </div>
          <div className={styles.team}>
            <TeamSelect
              teams={deriveTeams(catalog.players, filters.league)}
              value={filters.team}
              status={catalog.status}
              onOpen={() => void catalog.ensureLoaded()}
              onSelect={setTeam}
              onRetry={() => void catalog.retry()}
            />
          </div>
          {renderContent()}
        </div>
      </div>
    </Board>
  );
}
