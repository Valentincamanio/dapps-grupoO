package ar.edu.unq.desapp.futbolmarket.service.sync;

import static ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures.BARRY;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures.BARRY_APPEARANCE;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures.EVERTON;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures.MAN_UTD;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures.MARTINEZ;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures.NDIAYE;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures.SEASON;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures.SEASON_ID;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures.ageOf;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures.crossed;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures.crossedSeasonMatches;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures.crossedTeams;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures.fixturesOf;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures.players;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures.seasonMatches;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures.snapshot;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures.sourceTeams;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures.teams;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures.whoscoredIdOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataIntegrityViolationException;

import ar.edu.unq.desapp.futbolmarket.adapter.whoscored.WhoScoredAdapter;
import ar.edu.unq.desapp.futbolmarket.adapter.whoscored.WhoScoredPages;
import ar.edu.unq.desapp.futbolmarket.config.WhoScoredProperties;
import ar.edu.unq.desapp.futbolmarket.config.WhoScoredProperties.Snapshot;
import ar.edu.unq.desapp.futbolmarket.modelo.league.League;
import ar.edu.unq.desapp.futbolmarket.modelo.match.Match;
import ar.edu.unq.desapp.futbolmarket.modelo.match.MatchStatus;
import ar.edu.unq.desapp.futbolmarket.modelo.performance.MetricCounts;
import ar.edu.unq.desapp.futbolmarket.modelo.performance.ReportedAppearance;
import ar.edu.unq.desapp.futbolmarket.modelo.performance.ReportedMatchStats;
import ar.edu.unq.desapp.futbolmarket.modelo.performance.Side;
import ar.edu.unq.desapp.futbolmarket.modelo.player.Player;
import ar.edu.unq.desapp.futbolmarket.modelo.position.Position;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.CatalogReferences;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.CrossFailure;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.LastFullSync;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.LeagueStatsResult;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.LeagueStatsStatus;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.MatchStatsLoad;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.PageKind;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.ReportedPlayerProfile;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.ReportedTeam;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.RunCut;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.SkippedStatsMatch;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotRead;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotStatus;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsSkipReason;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsSyncReport;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.SyncOrigin;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.SyncSource;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.SyncType;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.UncrossedPlayer;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.exception.SourceBlockedException;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.exception.SyncInProgressException;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.exception.SyncInterruptedException;
import ar.edu.unq.desapp.futbolmarket.modelo.team.Team;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.match.MatchRepository;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.player.PlayerRepository;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.season.SeasonRepository;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.sync.LastFullSyncRepository;

/**
 * Solo la Premier tiene temporada en el catálogo: las otras ligas no tienen pendientes y no piden
 * nada. Los partidos de {@code StatsFixtures} van del 560600 al 560629 (los terminados, hasta el
 * 560620) y su id de WhoScored es el 1983600 más el mismo número.
 */
@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class StatsSyncServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-12T07:00:00Z");
    private static final String SNAPSHOT_PATH = "snapshot/whoscored-stats.jsonl";
    private static final String LISANDRO_WHOSCORED_ID = "400001";
    private static final WhoScoredProperties PROPERTIES = new WhoScoredProperties(
            URI.create("http://whoscored.invalid"), "agente", "text/html", "en-US", Duration.ofSeconds(10),
            Duration.ofSeconds(30), Duration.ofSeconds(4),
            Duration.ofSeconds(30), new Snapshot(SNAPSHOT_PATH));

    @Mock
    private WhoScoredAdapter adapter;

    @Mock
    private StatsWriteService writeService;

    @Mock
    private StatsSnapshotService snapshotService;

    @Mock
    private SeasonRepository seasonRepository;

    @Mock
    private MatchRepository matchRepository;

    @Mock
    private PlayerRepository playerRepository;

    @Mock
    private LastFullSyncRepository lastFullSyncRepository;

    @Mock
    private StatsSyncReportLogger reportLogger;

    private final SyncLock lock = new SyncLock();
    private StatsSyncService service;

    @BeforeEach
    void setUp() {
        service = new StatsSyncService(adapter, writeService, snapshotService, lock, seasonRepository,
                matchRepository, playerRepository, lastFullSyncRepository, reportLogger, PROPERTIES,
                Clock.fixed(NOW, ZoneOffset.UTC));
        lenient().when(snapshotService.read()).thenReturn(SnapshotRead.missing());
        lenient().when(snapshotService.references(any()))
                .thenAnswer(invocation -> new CatalogReferences(teams(), seasonMatches()));
        lenient().when(seasonRepository.findAll()).thenReturn(List.of(SEASON));
        lenient().when(playerRepository.findAllWithTeam()).thenReturn(players());
        lenient().when(writeService.saveTeamCrosses(any())).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(writeService.saveMatchCrosses(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void cargaElSnapshotDeTodasLasLigasAntesDelPrimerPedidoAWhoScored() {
        seasonMatchesAre(pendingOnly(identifiedMatches(), "560603", "560604"));
        when(snapshotService.read()).thenReturn(SnapshotRead.loaded(snapshot()));

        StatsSyncReport report = service.synchronizeAll(SyncOrigin.WEEKLY);

        verify(snapshotService, times(1)).read();
        InOrder snapshotFirst = inOrder(snapshotService, adapter);
        snapshotFirst.verify(snapshotService, times(League.values().length)).references(any());
        snapshotFirst.verify(adapter).fetchMatchStats("1983604");
        InOrder savedFirst = inOrder(writeService, adapter);
        savedFirst.verify(writeService).saveMatchStats(argThat(load -> load.match().externalId().equals("560603")));
        savedFirst.verify(adapter).fetchMatchStats("1983604");
        verify(adapter, never()).fetchMatchStats("1983603");
        assertThat(report.snapshot()).isEqualTo(SnapshotStatus.LOADED);
        assertThat(premier(report).matchesFromSnapshot()).isEqualTo(1);
    }

    @Test
    void sinSnapshotDejaUnWarnConLaRutaYScrapeaTodoLoPendiente(CapturedOutput output) {
        seasonMatchesAre(pendingOnly(identifiedMatches(), "560603"));

        StatsSyncReport report = service.synchronizeAll(SyncOrigin.WEEKLY);

        assertThat(output).contains("No se encontró el snapshot " + SNAPSHOT_PATH + ": se scrapea todo lo pendiente.");
        assertThat(report.snapshot()).isEqualTo(SnapshotStatus.MISSING);
        verify(adapter).fetchMatchStats("1983603");
    }

    @Test
    void conUnSnapshotIlegibleDejaUnWarnConElProblemaYScrapeaTodoLoPendiente(CapturedOutput output) {
        seasonMatchesAre(pendingOnly(identifiedMatches(), "560603"));
        when(snapshotService.read()).thenReturn(SnapshotRead.unreadable("la línea 3 no se puede leer"));

        StatsSyncReport report = service.synchronizeAll(SyncOrigin.WEEKLY);

        assertThat(output).contains("No se pudo leer el snapshot " + SNAPSHOT_PATH
                + " (la línea 3 no se puede leer): se ignora entero y se scrapea todo lo pendiente.");
        assertThat(report.snapshot()).isEqualTo(SnapshotStatus.UNREADABLE);
        verify(adapter).fetchMatchStats("1983603");
    }

    @Test
    void sinPartidosPendientesNoHaceNingunPedido() {
        seasonMatchesAre(pendingOnly(identifiedMatches()));

        service.synchronizeAll(SyncOrigin.WEEKLY);

        verifyNoInteractions(adapter);
    }

    @Test
    void conTodoCruzadoPideSoloLasPaginasDeLosPartidosEnOrdenDeFecha() {
        seasonMatchesAre(pendingOnly(identifiedMatches(), "560610", "560601", "560605"));
        ArgumentCaptor<String> requested = ArgumentCaptor.forClass(String.class);

        service.synchronizeAll(SyncOrigin.WEEKLY);

        verify(adapter, times(3)).fetchMatchStats(requested.capture());
        assertThat(requested.getAllValues()).containsExactly("1983601", "1983605", "1983610");
        verify(adapter, never()).fetchLeagueTeams(any());
        verify(adapter, never()).fetchTeamFixtures(any(), anyString());
    }

    @Test
    void conUnEquipoSinCruzarPideLaLigaYLosFixturesYGuardaLosCrucesAntesDelScraping() {
        List<Team> versions = crossedTeams().stream()
                .map(team -> team.externalId().equals(MAN_UTD.externalId()) ? MAN_UTD : team)
                .toList();
        seasonMatchesAre(seasonMatches(versions));
        when(adapter.fetchLeagueTeams(League.PREMIER)).thenReturn(sourceTeams());
        when(adapter.fetchTeamFixtures(eq(League.PREMIER), anyString()))
                .thenAnswer(invocation -> fixturesOf(sourceTeam(invocation.getArgument(1))));

        service.synchronizeAll(SyncOrigin.WEEKLY);

        InOrder discoveryFirst = inOrder(adapter, writeService);
        discoveryFirst.verify(adapter).fetchLeagueTeams(League.PREMIER);
        discoveryFirst.verify(adapter).fetchTeamFixtures(League.PREMIER, "32");
        discoveryFirst.verify(writeService).saveTeamCrosses(List.of(crossed(MAN_UTD)));
        discoveryFirst.verify(writeService).saveMatchCrosses(argThat(matches -> matches.size() == 10));
        discoveryFirst.verify(adapter, atLeastOnce()).fetchMatchStats(anyString());
    }

    @Test
    void unJugadorCruzadoEnUnPartidoSeReconocePorSuIdEnElSiguiente() {
        List<Match> matches = pendingOnly(identifiedMatches(), "560601", "560603");
        seasonMatchesAre(matches);
        when(adapter.fetchMatchStats("1983601"))
                .thenReturn(Optional.of(reportedWith(match(matches, "560601"), ndiaye("Iliman Ndiaye"))));
        when(adapter.fetchMatchStats("1983603"))
                .thenReturn(Optional.of(reportedWith(match(matches, "560603"), ndiaye("I. Ndiaye"))));
        ArgumentCaptor<MatchStatsLoad> saved = ArgumentCaptor.forClass(MatchStatsLoad.class);

        service.synchronizeAll(SyncOrigin.WEEKLY);

        verify(writeService, times(2)).saveMatchStats(saved.capture());
        MatchStatsLoad first = saved.getAllValues().getFirst();
        MatchStatsLoad second = saved.getAllValues().getLast();
        assertThat(first.crossedPlayers()).containsExactly(NDIAYE.crossWith("393485"));
        assertThat(second.crossedPlayers()).isEmpty();
        assertThat(second.stats()).extracting(stats -> stats.player().externalId()).containsExactly("8135");
    }

    @Test
    void soloSePidenLasPaginasDeLosJugadoresQueQuedanSinCruzar() {
        List<Match> matches = pendingOnly(identifiedMatches(), "560601");
        seasonMatchesAre(matches);
        when(adapter.fetchMatchStats("1983601"))
                .thenReturn(Optional.of(reportedWith(match(matches, "560601"), ndiaye("Iliman Ndiaye"), titi())));
        when(adapter.fetchPlayerProfile(BARRY_APPEARANCE.whoscoredId()))
                .thenReturn(Optional.of(new ReportedPlayerProfile(BARRY.dateOfBirth(), "Thierno Barry Diallo")));
        ArgumentCaptor<MatchStatsLoad> saved = ArgumentCaptor.forClass(MatchStatsLoad.class);

        service.synchronizeAll(SyncOrigin.WEEKLY);

        verify(adapter).fetchPlayerProfile(BARRY_APPEARANCE.whoscoredId());
        verify(adapter, times(1)).fetchPlayerProfile(anyString());
        verify(writeService).saveMatchStats(saved.capture());
        assertThat(saved.getValue().crossedPlayers()).containsExactlyInAnyOrder(NDIAYE.crossWith("393485"),
                BARRY.crossWith(BARRY_APPEARANCE.whoscoredId()));
    }

    /**
     * La página de Titi no trae la fecha: el mapa de la corrida guarda también la respuesta vacía.
     */
    @Test
    void unMismoJugadorEnDosPartidosDeLaCorridaSePideUnaSolaVez() {
        List<Match> matches = pendingOnly(identifiedMatches(), "560601", "560603");
        seasonMatchesAre(matches);
        when(adapter.fetchMatchStats("1983601"))
                .thenReturn(Optional.of(reportedWith(match(matches, "560601"), titi())));
        when(adapter.fetchMatchStats("1983603"))
                .thenReturn(Optional.of(reportedWith(match(matches, "560603"), titi())));
        when(adapter.fetchPlayerProfile(BARRY_APPEARANCE.whoscoredId())).thenReturn(Optional.empty());

        service.synchronizeAll(SyncOrigin.WEEKLY);

        verify(adapter, times(1)).fetchPlayerProfile(BARRY_APPEARANCE.whoscoredId());
        verify(writeService, times(2)).saveMatchStats(any());
    }

    @Test
    void unaPaginaDeJugadorVaciaNoCortaLaCorrida() {
        List<Match> matches = pendingOnly(identifiedMatches(), "560601", "560603");
        seasonMatchesAre(matches);
        when(adapter.fetchMatchStats("1983601"))
                .thenReturn(Optional.of(reportedWith(match(matches, "560601"), titi())));
        when(adapter.fetchMatchStats("1983603"))
                .thenReturn(Optional.of(reportedWith(match(matches, "560603"), ndiaye("Iliman Ndiaye"))));
        when(adapter.fetchPlayerProfile(BARRY_APPEARANCE.whoscoredId())).thenReturn(Optional.empty());

        StatsSyncReport report = service.synchronizeAll(SyncOrigin.WEEKLY);

        assertThat(report.cut()).isNull();
        assertThat(premier(report).status()).isEqualTo(LeagueStatsStatus.PROCESSED);
        assertThat(premier(report).matchesScraped()).isEqualTo(2);
        assertThat(premier(report).uncrossedPlayers()).extracting(UncrossedPlayer::name).containsExactly("Titi");
        verify(adapter).fetchMatchStats("1983603");
    }

    /**
     * WhoScored informa a Lisandro Martínez en el Everton, y el catálogo lo tiene en el Manchester
     * United con su misma edad. Pedro Martínez, del Everton y de su edad, lo cruzaría por el apellido
     * si se probaran los niveles (FR-019a).
     */
    @Test
    void unCandidatoDeOtroEquipoPideSuPaginaAunqueLosNivelesLoCruzarian() {
        List<Match> matches = pendingOnly(identifiedMatches(), "560601");
        seasonMatchesAre(matches);
        when(playerRepository.findAllWithTeam()).thenReturn(withPedroMartinez());
        when(adapter.fetchMatchStats("1983601"))
                .thenReturn(Optional.of(reportedWith(match(matches, "560601"), lisandroMartinez())));
        when(adapter.fetchPlayerProfile(LISANDRO_WHOSCORED_ID)).thenReturn(Optional.empty());
        ArgumentCaptor<MatchStatsLoad> saved = ArgumentCaptor.forClass(MatchStatsLoad.class);

        StatsSyncReport report = service.synchronizeAll(SyncOrigin.WEEKLY);

        verify(adapter).fetchPlayerProfile(LISANDRO_WHOSCORED_ID);
        verify(writeService).saveMatchStats(saved.capture());
        assertThat(saved.getValue().crossedPlayers()).isEmpty();
        assertThat(premier(report).uncrossedPlayers()).extracting(UncrossedPlayer::reason)
                .containsExactly(CrossFailure.NOT_CONFIRMED);
    }

    @Test
    void unTransferidoCruzadoPorLaFechaDejaLaLineaEnOtroEquipo(CapturedOutput output) {
        List<Match> matches = pendingOnly(identifiedMatches(), "560601");
        seasonMatchesAre(matches);
        when(playerRepository.findAllWithTeam()).thenReturn(withPedroMartinez());
        when(adapter.fetchMatchStats("1983601"))
                .thenReturn(Optional.of(reportedWith(match(matches, "560601"), lisandroMartinez())));
        when(adapter.fetchPlayerProfile(LISANDRO_WHOSCORED_ID))
                .thenReturn(Optional.of(new ReportedPlayerProfile(MARTINEZ.dateOfBirth(), null)));
        ArgumentCaptor<MatchStatsLoad> saved = ArgumentCaptor.forClass(MatchStatsLoad.class);

        service.synchronizeAll(SyncOrigin.WEEKLY);

        verify(writeService).saveMatchStats(saved.capture());
        assertThat(saved.getValue().crossedPlayers()).containsExactly(MARTINEZ.crossWith(LISANDRO_WHOSCORED_ID));
        assertThat(output).contains("PREMIER: jugador cruzado por fecha de nacimiento en otro equipo (18/01/1998): "
                + "'Lisandro Martínez' → 'Lisandro Martínez' (Manchester United FC), partido 560601.");
    }

    /**
     * La página de Titi trae otra fecha del mismo año que la de Thierno Barry y un nombre completo
     * que contiene el suyo (FR-019d, paso 3).
     */
    @Test
    void unCrucePorElNombreCompletoDejaSuLineaConElAnio(CapturedOutput output) {
        List<Match> matches = pendingOnly(identifiedMatches(), "560601");
        seasonMatchesAre(matches);
        when(adapter.fetchMatchStats("1983601"))
                .thenReturn(Optional.of(reportedWith(match(matches, "560601"), titi())));
        when(adapter.fetchPlayerProfile(BARRY_APPEARANCE.whoscoredId())).thenReturn(Optional.of(
                new ReportedPlayerProfile(BARRY.dateOfBirth().withDayOfYear(1), "Thierno Barry Diallo")));

        service.synchronizeAll(SyncOrigin.WEEKLY);

        assertThat(output).contains("PREMIER: jugador cruzado por nombre completo y año de nacimiento (2002): "
                + "'Titi' ('Thierno Barry Diallo') → 'Thierno Barry' (Everton FC), partido 560601.");
    }

    @Test
    void unBloqueoEnLaPaginaDeUnPartidoCortaLaCorridaYDevuelveElInforme() {
        List<Match> matches = pendingOnly(identifiedMatches(), "560600", "560601", "560602");
        seasonMatchesAre(matches);
        when(adapter.fetchMatchStats("1983600")).thenReturn(Optional.of(reportedWith(match(matches, "560600"))));
        when(adapter.fetchMatchStats("1983601"))
                .thenThrow(new SourceBlockedException(WhoScoredPages.match("1983601")));

        StatsSyncReport report = service.synchronizeAll(SyncOrigin.WEEKLY);

        assertThat(report.cut()).isEqualTo(new RunCut(WhoScoredPages.match("1983601"), League.PREMIER, "560601"));
        assertThat(report.leagues()).extracting(LeagueStatsResult::league, LeagueStatsResult::status)
                .containsExactly(
                        tuple(League.PREMIER, LeagueStatsStatus.CUT),
                        tuple(League.BUNDESLIGA, LeagueStatsStatus.NOT_REACHED),
                        tuple(League.LA_LIGA, LeagueStatsStatus.NOT_REACHED),
                        tuple(League.SERIE_A, LeagueStatsStatus.NOT_REACHED),
                        tuple(League.LIGUE_1, LeagueStatsStatus.NOT_REACHED));
        assertThat(premier(report).matchesScraped()).isEqualTo(1);
        verify(adapter).fetchMatchStats("1983600");
        verify(adapter).fetchMatchStats("1983601");
        verifyNoMoreInteractions(adapter);
        verify(writeService).saveMatchStats(argThat(load -> load.match().externalId().equals("560600")));
        verify(lastFullSyncRepository, never()).save(any());
    }

    @Test
    void unBloqueoEnLaPaginaDeLaLigaCortaSinPartido() {
        List<Team> versions = crossedTeams().stream()
                .map(team -> team.externalId().equals(MAN_UTD.externalId()) ? MAN_UTD : team)
                .toList();
        seasonMatchesAre(seasonMatches(versions));
        when(adapter.fetchLeagueTeams(League.PREMIER))
                .thenThrow(new SourceBlockedException(WhoScoredPages.league(League.PREMIER)));

        StatsSyncReport report = service.synchronizeAll(SyncOrigin.WEEKLY);

        assertThat(report.cut().page().kind()).isEqualTo(PageKind.LEAGUE);
        assertThat(report.cut().matchExternalId()).isNull();
        verify(adapter).fetchLeagueTeams(League.PREMIER);
        verifyNoMoreInteractions(adapter);
    }

    @Test
    void unaFallaDeLaBaseAlGuardarUnPartidoScrapeadoLoOmiteYSigueConElSiguiente() {
        List<Match> matches = pendingOnly(identifiedMatches(), "560600", "560601");
        seasonMatchesAre(matches);
        when(adapter.fetchMatchStats("1983600")).thenReturn(Optional.of(reportedWith(match(matches, "560600"))));
        when(adapter.fetchMatchStats("1983601")).thenReturn(Optional.of(reportedWith(match(matches, "560601"))));
        doThrow(new DataIntegrityViolationException("fila repetida")).when(writeService)
                .saveMatchStats(argThat(load -> load.match().externalId().equals("560600")));

        StatsSyncReport report = service.synchronizeAll(SyncOrigin.WEEKLY);

        assertThat(premier(report).skippedMatches()).extracting(SkippedStatsMatch::matchExternalId,
                SkippedStatsMatch::reason).containsExactly(tuple("560600", StatsSkipReason.WRITE_FAILED));
        assertThat(premier(report).matchesScraped()).isEqualTo(1);
    }

    @Test
    void unaFallaDeLaBaseAlGuardarUnaCargaDelSnapshotDejaElPartidoPendienteYSeScrapea() {
        List<Match> matches = pendingOnly(identifiedMatches(), "560603");
        seasonMatchesAre(matches);
        when(snapshotService.read()).thenReturn(SnapshotRead.loaded(snapshot()));
        when(adapter.fetchMatchStats("1983603")).thenReturn(Optional.of(reportedWith(match(matches, "560603"))));
        doThrow(new DataIntegrityViolationException("fila repetida")).doNothing().when(writeService)
                .saveMatchStats(any());

        StatsSyncReport report = service.synchronizeAll(SyncOrigin.WEEKLY);

        verify(writeService, times(2)).saveMatchStats(any());
        assertThat(premier(report).matchesFromSnapshot()).isZero();
        assertThat(premier(report).matchesScraped()).isEqualTo(1);
        assertThat(premier(report).skippedMatches()).isEmpty();
    }

    @Test
    void unPartidoSinDatosEnWhoScoredSeOmite() {
        seasonMatchesAre(pendingOnly(identifiedMatches(), "560600"));

        StatsSyncReport report = service.synchronizeAll(SyncOrigin.WEEKLY);

        assertThat(premier(report).skippedMatches()).extracting(SkippedStatsMatch::reason)
                .containsExactly(StatsSkipReason.NOT_FINISHED_IN_SOURCE);
        verify(writeService, never()).saveMatchStats(any());
    }

    @Test
    void unPartidoConUnRatingImposibleSeOmiteYLaCorridaSigue() {
        List<Match> matches = pendingOnly(identifiedMatches(), "560603", "560604");
        seasonMatchesAre(matches);
        ReportedAppearance pickfordWithRatingEleven = new ReportedAppearance("93145", "Jordan Pickford", 32,
                Side.HOME, true, null, null, MetricCounts.empty(), new BigDecimal("11"), false);
        when(adapter.fetchMatchStats("1983603"))
                .thenReturn(Optional.of(reportedWith(match(matches, "560603"), pickfordWithRatingEleven)));
        when(adapter.fetchMatchStats("1983604")).thenReturn(Optional.of(reportedWith(match(matches, "560604"))));

        StatsSyncReport report = service.synchronizeAll(SyncOrigin.WEEKLY);

        assertThat(premier(report).skippedMatches()).extracting(SkippedStatsMatch::matchExternalId,
                SkippedStatsMatch::reason).containsExactly(tuple("560603", StatsSkipReason.INVALID_IN_SOURCE));
        assertThat(premier(report).matchesScraped()).isEqualTo(1);
    }

    @Test
    void unaInterrupcionSePropagaSinGuardarLaUltimaCompletaYLiberaElCandado() {
        seasonMatchesAre(pendingOnly(identifiedMatches(), "560600"));
        when(adapter.fetchMatchStats("1983600"))
                .thenThrow(new SyncInterruptedException(new InterruptedException()))
                .thenReturn(Optional.empty());

        Throwable interrupted = catchThrowable(() -> service.synchronizeAll(SyncOrigin.WEEKLY));
        StatsSyncReport next = service.synchronizeAll(SyncOrigin.MANUAL);

        assertThat(interrupted).isInstanceOf(SyncInterruptedException.class);
        assertThat(next.origin()).isEqualTo(SyncOrigin.MANUAL);
        verify(lastFullSyncRepository, times(1)).save(any());
    }

    @Test
    void unaDeUnaLigaLeeYPideSoloEsaLigaYNoEsLaUltimaCompleta() {
        seasonMatchesAre(pendingOnly(identifiedMatches(), "560600"));

        StatsSyncReport report = service.synchronizeLeague(League.PREMIER);

        assertThat(report.type()).isEqualTo(SyncType.SINGLE_LEAGUE);
        assertThat(report.origin()).isEqualTo(SyncOrigin.MANUAL);
        assertThat(report.leagues()).extracting(LeagueStatsResult::league).containsExactly(League.PREMIER);
        verify(snapshotService, times(1)).references(any());
        verify(adapter).fetchMatchStats("1983600");
        verify(lastFullSyncRepository, never()).save(any());
    }

    @Test
    void unaCompletaSinCorteGuardaLaUltimaCompletaDeWhoScoredConElFin() {
        seasonMatchesAre(pendingOnly(identifiedMatches()));

        service.synchronizeAll(SyncOrigin.WEEKLY);

        verify(lastFullSyncRepository).save(new LastFullSync(SyncSource.WHOSCORED, NOW));
    }

    @Test
    void unSegundoPedidoDuranteLaCorridaSeRechazaYAlTerminarSePuedeSincronizarOtraVez() {
        seasonMatchesAre(pendingOnly(identifiedMatches(), "560600"));
        Throwable[] inner = new Throwable[1];
        when(adapter.fetchMatchStats("1983600")).thenAnswer(invocation -> {
            inner[0] = catchThrowable(() -> service.synchronizeLeague(League.PREMIER));
            return Optional.empty();
        }).thenReturn(Optional.empty());

        service.synchronizeAll(SyncOrigin.WEEKLY);
        StatsSyncReport next = service.synchronizeAll(SyncOrigin.MANUAL);

        assertThat(inner[0]).isInstanceOf(SyncInProgressException.class)
                .hasMessage("Ya hay una sincronización en curso.");
        assertThat(next.origin()).isEqualTo(SyncOrigin.MANUAL);
    }

    @Test
    void despuesDeUnErrorInesperadoQueSePropagaSePuedeSincronizarOtraVez() {
        seasonMatchesAre(pendingOnly(identifiedMatches(), "560600"));
        when(adapter.fetchMatchStats("1983600"))
                .thenThrow(new IllegalStateException("error de programación"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.synchronizeAll(SyncOrigin.WEEKLY)).isInstanceOf(IllegalStateException.class);
        StatsSyncReport next = service.synchronizeAll(SyncOrigin.MANUAL);

        assertThat(next.origin()).isEqualTo(SyncOrigin.MANUAL);
    }

    @Test
    void runFullCorreConElCandadoYaTomadoPorQuienLaLlama() {
        seasonMatchesAre(pendingOnly(identifiedMatches()));

        StatsSyncReport report = lock.runExclusively(() -> service.runFull(SyncOrigin.WEEKLY));

        assertThat(report.type()).isEqualTo(SyncType.FULL);
        assertThat(report.origin()).isEqualTo(SyncOrigin.WEEKLY);
    }

    @Test
    void elInformeVaUnaVezAlRegistroYSuInicioYSuFinSalenDelReloj() {
        seasonMatchesAre(pendingOnly(identifiedMatches()));

        StatsSyncReport report = service.synchronizeAll(SyncOrigin.MANUAL);

        verify(reportLogger, times(1)).log(report);
        assertThat(report.startedAt()).isEqualTo(NOW);
        assertThat(report.finishedAt()).isEqualTo(NOW);
    }

    private void seasonMatchesAre(List<Match> matches) {
        when(matchRepository.findAllBySeason(SEASON_ID)).thenReturn(matches);
    }

    /**
     * Los 30 partidos con los equipos cruzados y su id de WhoScored.
     */
    private static List<Match> identifiedMatches() {
        return crossedSeasonMatches().stream()
                .map(match -> match.crossWith(whoscoredIdOf(match.homeTeam(), match.awayTeam())))
                .toList();
    }

    /**
     * Los terminados que no están en la lista ya tienen estadísticas.
     */
    private static List<Match> pendingOnly(List<Match> matches, String... pendingIds) {
        Set<String> pending = Set.copyOf(Arrays.asList(pendingIds));
        return matches.stream()
                .map(match -> match.status() == MatchStatus.FINISHED && !pending.contains(match.externalId())
                        ? match.markStatsLoaded()
                        : match)
                .toList();
    }

    private static Match match(List<Match> matches, String externalId) {
        return matches.stream().filter(match -> match.externalId().equals(externalId)).findFirst().orElseThrow();
    }

    /**
     * Lo que WhoScored informa del partido, con sus equipos cruzados y esas apariciones.
     */
    private static ReportedMatchStats reportedWith(Match match, ReportedAppearance... appearances) {
        return new ReportedMatchStats(match.whoscoredId(), true, match.homeTeam().whoscoredId(),
                match.awayTeam().whoscoredId(), 96, List.of(appearances), List.of(), List.of());
    }

    private static ReportedAppearance ndiaye(String name) {
        return new ReportedAppearance("393485", name, 26, Side.HOME, true, null, null, MetricCounts.empty(), null,
                false);
    }

    /**
     * Thierno Barry con un apodo que no comparte nada con su nombre: solo lo cruza su fecha de
     * nacimiento.
     */
    private static ReportedAppearance titi() {
        return new ReportedAppearance(BARRY_APPEARANCE.whoscoredId(), "Titi", ageOf(BARRY), Side.HOME, true, null,
                null, MetricCounts.empty(), null, false);
    }

    private static ReportedAppearance lisandroMartinez() {
        return new ReportedAppearance(LISANDRO_WHOSCORED_ID, MARTINEZ.name(), ageOf(MARTINEZ), Side.HOME, true, null,
                null, MetricCounts.empty(), null, false);
    }

    /**
     * El catálogo de siempre y un Pedro Martínez en el Everton, de la edad de Lisandro Martínez.
     */
    private static List<Player> withPedroMartinez() {
        Player pedroMartinez = new Player(109L, "8140", "Pedro Martínez", Position.DEFENDER, EVERTON,
                LocalDate.of(1998, 5, 5), null, true);
        return Stream.concat(players().stream(), Stream.of(pedroMartinez)).toList();
    }

    private static ReportedTeam sourceTeam(String whoscoredId) {
        return sourceTeams().stream().filter(team -> team.whoscoredId().equals(whoscoredId)).findFirst()
                .orElseThrow();
    }

    private static LeagueStatsResult premier(StatsSyncReport report) {
        return report.leagues().stream().filter(result -> result.league() == League.PREMIER).findFirst()
                .orElseThrow();
    }
}
