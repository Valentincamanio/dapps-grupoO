// Jugadores: arriba, los potenciados de la semana (si el administrador eligió alguno); después, hojas
// de tarjetas con los jugadores del catálogo, con el buscador y los filtros.
import { Link, useLocation, useNavigate } from 'react-router';
import { BoostedSection } from '../components/BoostedSection';
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
import { useCountdown } from '../hooks/useCountdown';
import { useCurrentBoosts } from '../hooks/useCurrentBoosts';
import { useCurrentQuotes } from '../hooks/useCurrentQuotes';
import { usePlayers } from '../hooks/usePlayers';
import { LEAGUES } from '../types/api';
import type { BoostedWeek } from '../types/quotes';
import { describeEmptyCombination } from '../utils/filters';
import { leagueLabel, positionToZone, zoneToPosition } from '../utils/positions';
import { PLAYER_SECTIONS } from '../utils/sections';
import { deriveTeams } from '../utils/teams';
import styles from './BoardPage.module.css';

// Las tarjetas doradas de la semana, con su propia consulta de cotizaciones y la cuenta regresiva
function BoostedBoard({ week, boardSearch }: { week: BoostedWeek; boardSearch: string }) {
  const quotes = useCurrentQuotes(week.players.map((player) => player.playerId));
  const countdown = useCountdown(week.endsAt);
  return (
    <BoostedSection week={week} countdown={countdown}>
      {week.players.map((boosted) => (
        <li key={boosted.playerId}>
          <PostIt
            player={{
              id: boosted.playerId,
              name: boosted.name,
              position: boosted.position,
              team: boosted.team,
              league: boosted.league,
            }}
            href={`/jugadores/${boosted.playerId}`}
            LinkComponent={Link}
            linkState={{ boardSearch }}
            quote={quotes.badgeFor(boosted.playerId)}
            boostPercent={boosted.percent}
          />
        </li>
      ))}
    </BoostedSection>
  );
}

export function BoardPage() {
  const location = useLocation();
  const navigate = useNavigate();
  // Sin potenciados, o si no se pudieron leer, la pizarra se ve como siempre
  const boosts = useCurrentBoosts();
  const boostedWeek = boosts.week && boosts.week.players.length > 0 ? boosts.week : null;
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
      {boostedWeek ? (
        <>
          <BoostedBoard week={boostedWeek} boardSearch={location.search} />
          <p className={styles.divider}>Todos los jugadores</p>
        </>
      ) : null}
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
