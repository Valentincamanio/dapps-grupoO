package ar.edu.unq.desapp.futbolmarket.service.sync;

import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import ar.edu.unq.desapp.futbolmarket.adapter.whoscored.WhoScoredAdapter;
import ar.edu.unq.desapp.futbolmarket.config.WhoScoredProperties;
import ar.edu.unq.desapp.futbolmarket.modelo.league.League;
import ar.edu.unq.desapp.futbolmarket.modelo.match.Match;
import ar.edu.unq.desapp.futbolmarket.modelo.player.exception.CatalogInvariantException;
import ar.edu.unq.desapp.futbolmarket.modelo.season.CurrentSeasons;
import ar.edu.unq.desapp.futbolmarket.modelo.season.Season;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.BlockedPage;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.CatalogPlayers;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.LeagueStatsStatus;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.LeagueStatsSync;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.MatchStatsLoad;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.PageKind;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotLoad;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotRead;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsSnapshot;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsSyncReport;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsSyncRun;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.SyncOrigin;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.exception.SourceBlockedException;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.match.MatchRepository;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.player.PlayerRepository;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.season.SeasonRepository;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.sync.LastFullSyncRepository;
import lombok.RequiredArgsConstructor;

/**
 * Orquesta una corrida de WhoScored (research D18 y D20). Las reglas son del modelo
 * ({@link LeagueStatsSync}, {@link SnapshotLoad} y {@link StatsSyncRun}); acá solo se pide, se guarda
 * y se registra.
 *
 * <p>Dos fases: primero el snapshot de todas las ligas pedidas, antes de cualquier pedido a
 * WhoScored, así un bloqueo desde el primer pedido igual deja cargado lo que trae el snapshot (HU2,
 * escenario 3); después el descubrimiento y el scraping, liga por liga.</p>
 *
 * <p>No es {@code @Transactional}: cada partido se guarda en su propia transacción. Se capturan solo
 * el bloqueo de la fuente, que corta la corrida, las fallas de la base al guardar un partido y los
 * valores imposibles de la fuente al pedir y preparar un partido. Un error de programación o una
 * interrupción abortan la corrida, y el candado se libera igual. No revisa ninguna credencial
 * (FR-040).</p>
 */
@Service
@RequiredArgsConstructor
public class StatsSyncService {
    private static final Logger LOGGER = LoggerFactory.getLogger(StatsSyncService.class);

    private final WhoScoredAdapter adapter;
    private final StatsWriteService writeService;
    private final StatsSnapshotService snapshotService;
    private final SyncLock lock;
    private final SeasonRepository seasonRepository;
    private final MatchRepository matchRepository;
    private final PlayerRepository playerRepository;
    private final LastFullSyncRepository lastFullSyncRepository;
    private final StatsSyncReportLogger reportLogger;
    private final WhoScoredProperties properties;
    private final Clock clock;

    /**
     * Las cinco ligas, con el candado: si hay otra sincronización en curso, 409.
     */
    public StatsSyncReport synchronizeAll(SyncOrigin origin) {
        return lock.runExclusively(() -> runFull(origin));
    }

    /**
     * Una sola liga, siempre manual. Nunca cuenta como la última completa exitosa.
     */
    public StatsSyncReport synchronizeLeague(League league) {
        return lock.runExclusively(() -> execute(StatsSyncRun.singleLeague(league, clock.instant())));
    }

    /**
     * Las cinco ligas sin tomar el candado: la usa la cadena, que ya lo tiene. La corrida se crea
     * recién acá, así su inicio es el de la sincronización.
     */
    StatsSyncReport runFull(SyncOrigin origin) {
        return execute(StatsSyncRun.full(origin, clock.instant()));
    }

    private StatsSyncReport execute(StatsSyncRun run) {
        LOGGER.info("Sincronización de WhoScored {} ({}) iniciada.", run.type(), run.origin());
        SnapshotRead read = snapshotService.read();
        run.snapshotRead(read);
        warnIfNotLoaded(read);
        CatalogPlayers catalog = CatalogPlayers.of(playerRepository.findAllWithTeam());
        CurrentSeasons seasons = CurrentSeasons.of(seasonRepository.findAll());
        Map<League, LeagueStatsSync> leagues = new LinkedHashMap<>();
        run.leagues().forEach(league -> leagues.put(league, loadSnapshot(league, seasons, read.snapshot(), catalog)));
        leagues.forEach((league, sync) -> scrapeLeague(run, league, sync, catalog));
        StatsSyncReport report = run.finish(clock.instant());
        reportLogger.log(report);
        report.lastFullSync().ifPresent(lastFullSyncRepository::save);
        return report;
    }

    private void warnIfNotLoaded(SnapshotRead read) {
        String path = properties.snapshot().path();
        switch (read.status()) {
            case MISSING -> LOGGER.warn("No se encontró el snapshot {}: se scrapea todo lo pendiente.", path);
            case UNREADABLE -> LOGGER.warn(
                    "No se pudo leer el snapshot {} ({}): se ignora entero y se scrapea todo lo pendiente.",
                    path, read.problem());
            case LOADED -> LOGGER.info("Se leyó el snapshot {}.", path);
        }
    }

    /**
     * Fase 1: los cruces y las estadísticas que trae el snapshot de la liga, sin pedir nada a
     * WhoScored (FR-025). Un partido que no se pudo guardar sigue pendiente y se scrapea en la fase 2.
     */
    private LeagueStatsSync loadSnapshot(League league, CurrentSeasons seasons, StatsSnapshot snapshot,
                                         CatalogPlayers catalog) {
        Optional<Season> season = seasons.current(league);
        List<Match> matches = season.map(current -> matchRepository.findAllBySeason(current.externalId()))
                .orElse(List.of());
        LeagueStatsSync sync = new LeagueStatsSync(league, season.orElse(null), matches);
        StatsSnapshot forLeague = snapshot.forLeague(league);
        SnapshotLoad load = new SnapshotLoad(forLeague, matches, sync.seasonTeams(), catalog,
                snapshotService.references(forLeague));
        sync.confirmTeams(writeService.saveTeamCrosses(load.teamCrosses()));
        sync.confirmMatches(writeService.saveMatchCrosses(load.matchCrosses()));
        List<MatchStatsLoad> saved = new ArrayList<>();
        for (MatchStatsLoad loaded : load.loads()) {
            try {
                writeService.saveMatchStats(loaded);
                catalog.confirm(loaded.crossedPlayers());
                saved.add(loaded);
            } catch (DataAccessException e) {
                LOGGER.warn("{}: no se pudo guardar el partido {} del snapshot; queda pendiente y se scrapea.",
                        league, loaded.match().externalId(), e);
            }
        }
        sync.recordSnapshot(load, saved);
        return sync;
    }

    /**
     * Fase 2. Si la corrida ya se cortó, la liga no se alcanza y no se pide nada. Un bloqueo corta la
     * liga en curso y la corrida entera (FR-044 y FR-045).
     */
    private void scrapeLeague(StatsSyncRun run, League league, LeagueStatsSync sync, CatalogPlayers catalog) {
        if (run.isCut()) {
            run.record(sync.result(LeagueStatsStatus.NOT_REACHED));
            return;
        }
        try {
            discover(league, sync);
            sync.closeDiscovery();
            for (Match match : sync.matchesToScrape()) {
                scrapeMatch(league, sync, match, catalog);
            }
            run.record(sync.result(LeagueStatsStatus.PROCESSED));
        } catch (SourceBlockedException e) {
            run.cut(e.page(), league, matchExternalIdOf(e.page(), sync));
            run.record(sync.result(LeagueStatsStatus.CUT));
        }
    }

    /**
     * La página de la liga, si hace falta, y las de fixtures que elija el modelo. Los cruces se
     * guardan apenas se deciden, antes del scraping.
     */
    private void discover(League league, LeagueStatsSync sync) {
        if (sync.needsLeaguePage()) {
            sync.registerSourceTeams(adapter.fetchLeagueTeams(league));
        }
        Optional<String> nextPage = sync.nextFixturesPage();
        while (nextPage.isPresent()) {
            String teamWhoscoredId = nextPage.get();
            sync.registerFixtures(teamWhoscoredId, adapter.fetchTeamFixtures(league, teamWhoscoredId));
            sync.confirmTeams(writeService.saveTeamCrosses(sync.teamCrosses()));
            sync.confirmMatches(writeService.saveMatchCrosses(sync.matchCrosses()));
            nextPage = sync.nextFixturesPage();
        }
    }

    /**
     * Un valor imposible de la fuente omite el partido; se captura solo acá (research D18).
     */
    private void scrapeMatch(League league, LeagueStatsSync sync, Match match, CatalogPlayers catalog) {
        Optional<MatchStatsLoad> load;
        try {
            load = sync.prepare(match, adapter.fetchMatchStats(match.whoscoredId()), catalog);
        } catch (CatalogInvariantException e) {
            LOGGER.warn("{}: WhoScored informó un valor imposible para el partido {}: {}", league,
                    match.externalId(), e.getMessage());
            sync.recordInvalidSource(match);
            return;
        }
        load.ifPresent(prepared -> save(league, sync, prepared, catalog));
    }

    private void save(League league, LeagueStatsSync sync, MatchStatsLoad load, CatalogPlayers catalog) {
        try {
            writeService.saveMatchStats(load);
            catalog.confirm(load.crossedPlayers());
            sync.recordScraped(load);
        } catch (DataAccessException e) {
            LOGGER.warn("{}: no se pudo guardar el partido {}.", league, load.match().externalId(), e);
            sync.recordWriteFailure(load.match());
        }
    }

    /**
     * El id de Football-Data del partido, si la página bloqueada era la de un partido.
     */
    private static String matchExternalIdOf(BlockedPage page, LeagueStatsSync sync) {
        if (page.kind() != PageKind.MATCH) {
            return null;
        }
        return sync.matchesToScrape().stream()
                .filter(match -> page.whoscoredId().equals(match.whoscoredId()))
                .map(Match::externalId)
                .findFirst()
                .orElse(null);
    }
}
