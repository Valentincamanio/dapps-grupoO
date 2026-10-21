// Ranking de jugadores por cotización, con los mismos filtros y la misma hoja en la dirección que
// la pizarra (league, team, position, page; FR-063).
import { Link } from 'react-router';
import { Board } from '../components/Board';
import { ChalkNote } from '../components/ChalkNote';
import { Pager } from '../components/Pager';
import { Pitch } from '../components/Pitch';
import { RankingList } from '../components/RankingList';
import { TapeTab } from '../components/TapeTab';
import { TeamSelect } from '../components/TeamSelect';
import { useCatalog } from '../hooks/useCatalog';
import { useRanking } from '../hooks/useRanking';
import { LEAGUES } from '../types/api';
import { leagueLabel, positionToZone, zoneToPosition } from '../utils/positions';
import { deriveTeams } from '../utils/teams';
import styles from './RankingPage.module.css';

export function RankingPage() {
  const { filters, setPage, setPosition, setLeague, setTeam, clear, status, sheet, retry } =
    useRanking();
  const catalog = useCatalog();

  function renderContent() {
    if (status === 'loading' || status === 'idle') return <ChalkNote variant="loading" />;
    if (status === 'error' || !sheet) {
      return (
        <ChalkNote variant="error" action={{ label: 'reintentar', onClick: retry }}>
          no se pudo cargar el ranking
        </ChalkNote>
      );
    }
    if (sheet.isOutOfRange) {
      return (
        <ChalkNote
          variant="info"
          action={{ label: 'volver a la primera hoja', onClick: () => setPage(1) }}
        >
          esta hoja del ranking está vacía
        </ChalkNote>
      );
    }
    if (sheet.isEmpty) {
      return (
        <ChalkNote variant="info" action={{ label: 'limpiar filtros', onClick: clear }}>
          no hay jugadores cotizados con esos filtros
        </ChalkNote>
      );
    }

    return (
      <>
        <p className={styles.strategy}>
          estrategia activa: {sheet.activeStrategy.name}, versión {sheet.activeStrategy.version}
        </p>
        <RankingList
          rows={sheet.rows}
          firstRank={sheet.firstRank}
          playerHref={(playerId) => `/jugadores/${playerId}`}
          LinkComponent={Link}
        />
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
          <h1 className={styles.title}>Ranking por cotización</h1>
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
