// Jugadores: hojas de tarjetas con los jugadores del catálogo, con el buscador y los filtros.
import { Link, useLocation, useNavigate } from 'react-router';
import { ChalkNote } from '../components/ChalkNote';
import { ChalkText } from '../components/ChalkText';
import { Pager } from '../components/Pager';
import { Pitch } from '../components/Pitch';
import { PostIt } from '../components/PostIt';
import { SearchBox } from '../components/SearchBox';
import { SectionNav } from '../components/SectionNav';
import { TapeTab } from '../components/TapeTab';
import { TeamSelect } from '../components/TeamSelect';
import { useBoardFilters } from '../hooks/useBoardFilters';
import { useCatalog } from '../hooks/useCatalog';
import { useCurrentQuotes } from '../hooks/useCurrentQuotes';
import { usePlayers } from '../hooks/usePlayers';
import { LEAGUES } from '../types/api';
import { describeEmptyCombination } from '../utils/filters';
import { leagueLabel, positionToZone, zoneToPosition } from '../utils/positions';
import { PLAYER_SECTIONS } from '../utils/sections';
import { deriveTeams } from '../utils/teams';
import styles from './BoardPage.module.css';

export function BoardPage() {
  const location = useLocation();
  const navigate = useNavigate();
  const { filters, setPage, setPosition, setLeague, setTeam, clear } = useBoardFilters();
  const catalog = useCatalog();
  const { status, sheet, retry } = usePlayers(filters);
  // Una sola consulta de cotizaciones por hoja visible (FR-062)
  const quotes = useCurrentQuotes(
    status === 'success' && sheet ? sheet.players.map((player) => player.id) : [],
  );

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
        {/* Si fallan las cotizaciones, la pizarra sigue y cada post-it lo indica */}
        {quotes.status === 'error' ? (
          <ChalkNote
            variant="error"
            action={{ label: 'reintentar cotizaciones', onClick: quotes.retry }}
          >
            no se pudieron cargar las cotizaciones
          </ChalkNote>
        ) : null}
        <ul className={styles.grid}>
          {sheet.players.map((player) => (
            <li key={player.id}>
              <PostIt
                player={player}
                href={`/jugadores/${player.id}`}
                LinkComponent={Link}
                linkState={{ boardSearch: location.search }}
                quote={quotes.badgeFor(player.id)}
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
    <section className={styles.page}>
      <ChalkText as="h1" variant="srOnly">
        Jugadores
      </ChalkText>
      <SectionNav
        title="Jugadores"
        label="secciones de jugadores"
        sections={PLAYER_SECTIONS}
        current="/pizarra"
        LinkComponent={Link}
      />
      <div className={styles.filters}>
        <SearchBox
          players={catalog.players}
          status={catalog.status}
          onFocus={() => void catalog.ensureLoaded()}
          onRetry={() => void catalog.retry()}
          // Sin state: la ficha vuelve a la pizarra completa
          onSelect={(player) => navigate(`/jugadores/${player.id}`)}
        />
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
