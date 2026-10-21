package ar.edu.unq.desapp.futbolmarket.persistence.repository.match;

import ar.edu.unq.desapp.futbolmarket.modelo.league.League;
import ar.edu.unq.desapp.futbolmarket.modelo.match.Match;
import ar.edu.unq.desapp.futbolmarket.modelo.match.MatchStatus;
import ar.edu.unq.desapp.futbolmarket.modelo.match.MatchWinner;
import ar.edu.unq.desapp.futbolmarket.modelo.match.Score;
import ar.edu.unq.desapp.futbolmarket.modelo.season.Season;
import ar.edu.unq.desapp.futbolmarket.modelo.team.Team;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.season.SeasonRepository;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.team.TeamRepository;
import ar.edu.unq.desapp.futbolmarket.persistence.sql.interfaces.match.MatchSQLDAO;
import ar.edu.unq.desapp.futbolmarket.persistence.sql.interfaces.player.PlayerSQLDAO;
import ar.edu.unq.desapp.futbolmarket.persistence.sql.interfaces.season.SeasonSQLDAO;
import ar.edu.unq.desapp.futbolmarket.persistence.sql.interfaces.team.TeamSQLDAO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:stats-it;DB_CLOSE_DELAY=-1")
class MatchStatsRepositoryIT {
    private static final String CURRENT_SEASON_ID = "2502";
    private static final String PREVIOUS_SEASON_ID = "2400";
    private static final Instant KICK_OFF = Instant.parse("2026-10-17T14:00:00Z");
    private static final String WHOSCORED_ID_INDEX = "ux_matches_whoscored_id";

    /**
     * Una fila como las de la 005, sin la columna {@code stats_loaded}. Es el único SQL nativo de
     * la feature, aprobado por el equipo el 2026-10-14 (research D17): un {@code INSERT} estándar,
     * sin funciones de H2.
     */
    private static final String INSERT_WITHOUT_STATS_LOADED = """
            INSERT INTO matches (external_id, season_id, utc_date, matchday, status, home_team_id, away_team_id)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """;

    @Autowired
    private MatchRepository matchRepository;

    @Autowired
    private SeasonRepository seasonRepository;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MatchSQLDAO matchDAO;

    @Autowired
    private SeasonSQLDAO seasonDAO;

    @Autowired
    private PlayerSQLDAO playerDAO;

    @Autowired
    private TeamSQLDAO teamDAO;

    private Season currentSeason;
    private Season previousSeason;
    private Team everton;
    private Team manUtd;
    private Team tottenham;

    @BeforeEach
    void prepareDatabase() {
        cleanDatabase();
        currentSeason = seasonRepository.save(new Season(
                null, CURRENT_SEASON_ID, League.PREMIER, LocalDate.of(2026, 8, 21), LocalDate.of(2027, 5, 30), 8));
        previousSeason = seasonRepository.save(new Season(
                null, PREVIOUS_SEASON_ID, League.PREMIER, LocalDate.of(2025, 8, 15), LocalDate.of(2026, 5, 24), 38));
        everton = teamRepository.save(new Team(null, "62", "Everton FC", null, League.PREMIER, "31"));
        manUtd = teamRepository.save(new Team(null, "66", "Manchester United FC", null, League.PREMIER, "32"));
        tottenham = teamRepository.save(new Team("73", "Tottenham Hotspur FC", null, League.PREMIER));
    }

    @AfterEach
    void cleanDatabaseAfter() {
        cleanDatabase();
    }

    @Test
    void findAllBySeasonTraeSoloLosPartidosDeEsaTemporadaConSusEquipos() {
        matchRepository.saveAll(List.of(
                match("560600", currentSeason, everton, manUtd, null, false),
                match("560601", currentSeason, tottenham, everton, null, false),
                match("540100", previousSeason, manUtd, tottenham, null, false)));

        List<Match> found = matchRepository.findAllBySeason(CURRENT_SEASON_ID);

        assertThat(found)
                .extracting(Match::externalId, match -> match.homeTeam().name(), match -> match.awayTeam().name())
                .containsExactlyInAnyOrder(
                        tuple("560600", "Everton FC", "Manchester United FC"),
                        tuple("560601", "Tottenham Hotspur FC", "Everton FC"));
        assertThat(found).extracting(Match::season).containsOnly(currentSeason);
    }

    @Test
    void findAllCrossedTraeSoloLosPartidosCruzadosDeCualquierTemporada() {
        matchRepository.saveAll(List.of(
                match("560600", currentSeason, everton, manUtd, "1983600", false),
                match("560601", currentSeason, tottenham, everton, null, false),
                match("540100", previousSeason, manUtd, everton, "1903200", true)));

        List<Match> crossed = matchRepository.findAllCrossed();

        assertThat(crossed)
                .extracting(Match::externalId, Match::whoscoredId)
                .containsExactlyInAnyOrder(tuple("560600", "1983600"), tuple("540100", "1903200"));
    }

    @Test
    void findAllWithStatsTraeSoloLosPartidosConEstadisticas() {
        matchRepository.saveAll(List.of(
                match("560600", currentSeason, everton, manUtd, "1983600", true),
                match("560601", currentSeason, manUtd, everton, "1983601", false),
                match("540100", previousSeason, manUtd, everton, "1903200", true)));

        List<Match> withStats = matchRepository.findAllWithStats();

        assertThat(withStats).extracting(Match::externalId).containsExactlyInAnyOrder("560600", "540100");
        assertThat(withStats).allSatisfy(match -> assertThat(match.statsLoaded()).isTrue());
    }

    @Test
    void variosPartidosSinIdentificadorDeWhoScoredConviven() {
        List<Match> uncrossed = List.of(
                match("560600", currentSeason, everton, manUtd, null, false),
                match("560601", currentSeason, tottenham, everton, null, false));

        List<Match> saved = matchRepository.saveAll(uncrossed);

        assertThat(saved).extracting(Match::whoscoredId).containsOnlyNulls();
        assertThat(matchDAO.count()).isEqualTo(2);
    }

    @Test
    void unIdentificadorDeWhoScoredRepetidoViolaElIndiceUnico() {
        matchRepository.saveAll(List.of(match("560600", currentSeason, everton, manUtd, "1983600", false)));
        List<Match> repeated = List.of(match("560601", currentSeason, tottenham, everton, "1983600", false));

        assertThatThrownBy(() -> matchRepository.saveAll(repeated))
                .isInstanceOf(DataIntegrityViolationException.class)
                .rootCause()
                .message()
                .containsIgnoringCase(WHOSCORED_ID_INDEX);
    }

    @Test
    void unaFilaGuardadaSinLaMarcaDeEstadisticasSeLeeSinEstadisticas() {
        jdbcTemplate.update(INSERT_WITHOUT_STATS_LOADED, "560600", currentSeason.id(),
                OffsetDateTime.ofInstant(KICK_OFF, ZoneOffset.UTC), 8, MatchStatus.FINISHED.name(), everton.id(),
                manUtd.id());

        List<Match> found = matchRepository.findAllByExternalIds(List.of("560600"));

        assertThat(found).singleElement().satisfies(match -> {
            assertThat(match.statsLoaded()).isFalse();
            assertThat(match.whoscoredId()).isNull();
            assertThat(match.needsStats()).isTrue();
        });
    }

    @Test
    void seasonRepositoryFindAllTraeTodasLasTemporadas() {
        List<Season> seasons = seasonRepository.findAll();

        assertThat(seasons).containsExactlyInAnyOrder(currentSeason, previousSeason);
    }

    private static Match match(String externalId, Season season, Team home, Team away, String whoscoredId,
                               boolean statsLoaded) {
        return new Match(null, externalId, season, KICK_OFF, 8, MatchStatus.FINISHED, home, away, new Score(1, 0),
                new Score(0, 0), MatchWinner.HOME_TEAM, whoscoredId, statsLoaded);
    }

    private void cleanDatabase() {
        matchDAO.deleteAll();
        seasonDAO.deleteAll();
        playerDAO.deleteAll();
        teamDAO.deleteAll();
    }
}
