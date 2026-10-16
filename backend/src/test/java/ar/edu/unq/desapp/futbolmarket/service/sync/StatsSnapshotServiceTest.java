package ar.edu.unq.desapp.futbolmarket.service.sync;

import static ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures.ARSENAL;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures.CASEMIRO;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures.EVERTON;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures.MAN_UTD;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures.PICKFORD;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures.TOTTENHAM;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures.crossed;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures.crossedSeasonMatches;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures.evertonManUtd;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures.matchBetween;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures.snapshot;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures.whoscoredIdOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import ar.edu.unq.desapp.futbolmarket.config.WhoScoredProperties;
import ar.edu.unq.desapp.futbolmarket.config.WhoScoredProperties.Snapshot;
import ar.edu.unq.desapp.futbolmarket.modelo.league.League;
import ar.edu.unq.desapp.futbolmarket.modelo.match.Match;
import ar.edu.unq.desapp.futbolmarket.modelo.performance.Metric;
import ar.edu.unq.desapp.futbolmarket.modelo.performance.MetricCounts;
import ar.edu.unq.desapp.futbolmarket.modelo.performance.PlayerMatchStats;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.CatalogReferences;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotTeamEntry;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsSnapshot;
import ar.edu.unq.desapp.futbolmarket.modelo.team.Team;
import ar.edu.unq.desapp.futbolmarket.persistence.mapper.sync.StatsSnapshotLineMapper;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.match.MatchRepository;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.performance.PlayerMatchStatsRepository;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.sync.StatsSnapshotRepository;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.team.TeamRepository;

/**
 * La base está mockeada con los datos de {@code StatsFixtures}; el snapshot se escribe de verdad en
 * una carpeta temporal.
 */
@ExtendWith(MockitoExtension.class)
class StatsSnapshotServiceTest {

    private static final String HEADER = "{\"format\":\"futbolmarket-whoscored-snapshot\",\"version\":1,"
            + "\"playerColumns\":[\"player\",\"whoscored\",\"starter\",\"minutesPlayed\",\"goals\",\"assists\","
            + "\"shots\",\"shotsOnTarget\",\"keyPasses\",\"passes\",\"accuratePasses\",\"successfulDribbles\","
            + "\"tackles\",\"interceptions\",\"clearances\",\"aerialsWon\",\"foulsCommitted\",\"yellowCards\","
            + "\"redCards\",\"rating\",\"manOfTheMatch\"]}";
    private static final MetricCounts PASSES = MetricCounts.of(Map.of(Metric.PASSES, 30, Metric.ACCURATE_PASSES, 25));

    @TempDir
    private Path directory;

    @Mock
    private TeamRepository teamRepository;

    @Mock
    private MatchRepository matchRepository;

    @Mock
    private PlayerMatchStatsRepository statsRepository;

    private Path file;
    private StatsSnapshotRepository snapshotRepository;
    private StatsSnapshotService service;

    @BeforeEach
    void setUp() {
        file = directory.resolve("snapshot").resolve("whoscored-stats.jsonl");
        snapshotRepository = new StatsSnapshotRepository(properties(file), new StatsSnapshotLineMapper());
        service = new StatsSnapshotService(snapshotRepository, teamRepository, matchRepository, statsRepository);
    }

    @Test
    void sinSnapshotVigenteEscribeLasEntradasDeLaBaseEnOrdenCanonicoSinNombres() throws IOException {
        databaseWithEvertonManUtd();

        boolean exported = service.export();

        assertThat(exported).isTrue();
        assertThat(Files.readAllLines(file)).containsExactly(
                HEADER,
                "{\"type\":\"team\",\"league\":\"PREMIER\",\"team\":\"62\",\"whoscored\":\"31\"}",
                "{\"type\":\"team\",\"league\":\"PREMIER\",\"team\":\"66\",\"whoscored\":\"32\"}",
                "{\"type\":\"match\",\"league\":\"PREMIER\",\"match\":\"560603\",\"whoscored\":\"1983603\"}",
                "{\"type\":\"stats\",\"league\":\"PREMIER\",\"match\":\"560603\",\"players\":["
                        + "[\"3204\",\"93145\",1,96,0,0,0,0,0,30,25,0,0,0,0,0,0,0,0,7.10,1],"
                        + "[\"3255\",null,1,62,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,null,0]]}");
        assertThat(Files.readString(file)).doesNotContain("Everton", "Manchester", "Pickford", "Casemiro");
    }

    @Test
    void dosExportacionesSinCambiosEnLaBaseDanElMismoArchivoByteAByte() throws IOException {
        databaseWithEvertonManUtd();
        service.export();
        byte[] first = Files.readAllBytes(file);

        service.export();

        assertThat(Files.readAllBytes(file)).isEqualTo(first);
    }

    @Test
    void despuesDeUnPartidoNuevoSoloSeAgreganSusLineasYLosCrucesNuevos() throws IOException {
        databaseWithEvertonManUtd();
        service.export();
        List<String> before = Files.readAllLines(file);
        Match tottenhamArsenal = matchBetween(crossedSeasonMatches(), TOTTENHAM, ARSENAL)
                .crossWith(whoscoredIdOf(TOTTENHAM, ARSENAL))
                .markStatsLoaded();
        Match evertonManUtd = evertonManUtd().markStatsLoaded();
        when(teamRepository.findAllCrossed())
                .thenReturn(List.of(crossed(EVERTON), crossed(MAN_UTD), crossed(TOTTENHAM)));
        when(matchRepository.findAllCrossed()).thenReturn(List.of(evertonManUtd, tottenhamArsenal));
        when(matchRepository.findAllWithStats()).thenReturn(List.of(evertonManUtd, tottenhamArsenal));

        service.export();

        List<String> after = Files.readAllLines(file);
        assertThat(after).containsAll(before).hasSize(before.size() + 3);
        assertThat(after).contains(
                "{\"type\":\"team\",\"league\":\"PREMIER\",\"team\":\"73\",\"whoscored\":\"30\"}",
                "{\"type\":\"match\",\"league\":\"PREMIER\",\"match\":\"560605\",\"whoscored\":\"1983605\"}",
                "{\"type\":\"stats\",\"league\":\"PREMIER\",\"match\":\"560605\",\"players\":[]}");
    }

    @Test
    void conservaLasEntradasVigentesQueLaBaseNoTieneOTieneDistintas() throws IOException {
        snapshotRepository.write(new StatsSnapshot(List.of(
                new SnapshotTeamEntry(League.PREMIER, EVERTON.externalId(), "77"),
                new SnapshotTeamEntry(League.PREMIER, "999", "5")), List.of(), List.of()));
        databaseWithEvertonManUtd();

        service.export();

        assertThat(Files.readAllLines(file)).contains(
                "{\"type\":\"team\",\"league\":\"PREMIER\",\"team\":\"62\",\"whoscored\":\"77\"}",
                "{\"type\":\"team\",\"league\":\"PREMIER\",\"team\":\"66\",\"whoscored\":\"32\"}",
                "{\"type\":\"team\",\"league\":\"PREMIER\",\"team\":\"999\",\"whoscored\":\"5\"}")
                .doesNotContain("{\"type\":\"team\",\"league\":\"PREMIER\",\"team\":\"62\",\"whoscored\":\"31\"}");
    }

    @Test
    void conUnSnapshotVigenteIlegibleNoLoPisaYDevuelveFalse() throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, HEADER + "\n{\"type\":\"team\",\n", StandardCharsets.UTF_8);
        byte[] before = Files.readAllBytes(file);

        boolean exported = service.export();

        assertThat(exported).isFalse();
        assertThat(Files.readAllBytes(file)).isEqualTo(before);
        verifyNoInteractions(teamRepository, matchRepository, statsRepository);
    }

    @Test
    void referencesTraeLosEquiposYLosPartidosDelCatalogoQueNombraElSnapshotDeCualquierTemporada() {
        StatsSnapshot forLeague = snapshot().forLeague(League.PREMIER);
        List<Team> teams = List.of(EVERTON, MAN_UTD);
        List<Match> matches = List.of(evertonManUtd(), matchBetween(crossedSeasonMatches(), MAN_UTD, EVERTON));
        when(teamRepository.findAllByExternalIds(forLeague.teamExternalIds())).thenReturn(teams);
        when(matchRepository.findAllByExternalIds(forLeague.matchExternalIds())).thenReturn(matches);

        CatalogReferences references = service.references(forLeague);

        assertThat(references).isEqualTo(new CatalogReferences(teams, matches));
    }

    /**
     * Everton y Manchester United cruzados, y el Everton - Manchester United de la fecha 1 con las
     * estadísticas de Pickford, cruzado, y de Casemiro, sin cruzar.
     */
    private void databaseWithEvertonManUtd() {
        Match evertonManUtd = evertonManUtd().markStatsLoaded();
        when(teamRepository.findAllCrossed()).thenReturn(List.of(crossed(MAN_UTD), crossed(EVERTON)));
        when(matchRepository.findAllCrossed()).thenReturn(List.of(evertonManUtd));
        when(matchRepository.findAllWithStats()).thenReturn(List.of(evertonManUtd));
        when(statsRepository.findAll()).thenReturn(List.of(
                new PlayerMatchStats(1L, PICKFORD, evertonManUtd, true, 96, PASSES, new BigDecimal("7.1"), true),
                new PlayerMatchStats(2L, CASEMIRO, evertonManUtd, true, 62, MetricCounts.empty(), null, false)));
    }

    private static WhoScoredProperties properties(Path file) {
        return new WhoScoredProperties(URI.create("http://whoscored.invalid"), "agente", "text/html", "en-US",
                Duration.ofSeconds(10), Duration.ofSeconds(30), Duration.ofSeconds(4), Duration.ofSeconds(30),
                new Snapshot(file.toString()));
    }
}
