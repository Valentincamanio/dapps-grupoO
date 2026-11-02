// Ranking de jugadores por cotización, con los mismos filtros y la misma hoja en la dirección que
// la pizarra (league, team, position, page; FR-063).
import { Link } from 'react-router';
import { ChalkNote } from '../components/ChalkNote';
import { ChalkText } from '../components/ChalkText';
import { Pager } from '../components/Pager';
import { Pitch } from '../components/Pitch';
import { RankingList } from '../components/RankingList';
import { SectionNav } from '../components/SectionNav';
import { TapeTab } from '../components/TapeTab';
import { TeamSelect } from '../components/TeamSelect';
import { useCatalog } from '../hooks/useCatalog';
import { useRanking } from '../hooks/useRanking';
import { LEAGUES } from '../types/api';
import { leagueLabel, positionToZone, zoneToPosition } from '../utils/positions';
import { PLAYER_SECTIONS } from '../utils/sections';
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
    <section className={styles.page}>
      <ChalkText as="h1" variant="srOnly">
        Ranking por cotización
      </ChalkText>
      <SectionNav
        title="Jugadores"
        label="secciones de jugadores"
        sections={PLAYER_SECTIONS}
        current="/ranking"
        LinkComponent={Link}
      />
      <div className={styles.filters}>
        <TeamSelect
          teams={deriveTeams(catalog.players, filters.league)}
          value={filters.team}
          status={catalog.status}
          onOpen={() => void catalog.ensureLoaded()}
          onSelect={setTeam}
          onRetry={() => void catalog.retry()}
        />
        <div className={styles.chips}>
          <Pitch
            activeZone={positionToZone(filters.position)}
            onZoneSelect={(zone) => setPosition(zoneToPosition(zone))}
          />
        </div>
        <div className={styles.chips} role="group" aria-label="filtrar por liga">
          <TapeTab label="todas" active={filters.league === null} onSelect={() => void setLeague(null)} />
          {LEAGUES.map((league) => (
            <TapeTab
              key={league}
              label={leagueLabel(league)}
              active={filters.league === league}
              onSelect={() => void setLeague(league)}
            />
          ))}
        </div>
      </div>
      {renderContent()}
    </section>
  );
}
