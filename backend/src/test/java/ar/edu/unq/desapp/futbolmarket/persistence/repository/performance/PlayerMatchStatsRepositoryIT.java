package ar.edu.unq.desapp.futbolmarket.persistence.repository.performance;

import ar.edu.unq.desapp.futbolmarket.modelo.league.League;
import ar.edu.unq.desapp.futbolmarket.modelo.match.Match;
import ar.edu.unq.desapp.futbolmarket.modelo.match.MatchStatus;
import ar.edu.unq.desapp.futbolmarket.modelo.performance.Metric;
import ar.edu.unq.desapp.futbolmarket.modelo.performance.MetricCounts;
import ar.edu.unq.desapp.futbolmarket.modelo.performance.PlayerMatchStats;
import ar.edu.unq.desapp.futbolmarket.modelo.player.Player;
import ar.edu.unq.desapp.futbolmarket.modelo.position.Position;
import ar.edu.unq.desapp.futbolmarket.modelo.season.Season;
import ar.edu.unq.desapp.futbolmarket.modelo.team.Team;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.match.MatchRepository;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.player.PlayerRepository;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.season.SeasonRepository;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.team.TeamRepository;
import ar.edu.unq.desapp.futbolmarket.persistence.sql.interfaces.match.MatchSQLDAO;
import ar.edu.unq.desapp.futbolmarket.persistence.sql.interfaces.performance.PlayerMatchStatsSQLDAO;
import ar.edu.unq.desapp.futbolmarket.persistence.sql.interfaces.player.PlayerSQLDAO;
import ar.edu.unq.desapp.futbolmarket.persistence.sql.interfaces.season.SeasonSQLDAO;
import ar.edu.unq.desapp.futbolmarket.persistence.sql.interfaces.team.TeamSQLDAO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:stats-it;DB_CLOSE_DELAY=-1")
class PlayerMatchStatsRepositoryIT {
    private static final String PLAYER_MATCH_INDEX = "ux_player_match_stats_player_match";
    private static final BigDecimal RATING = new BigDecimal("7.65");

    @Autowired
    private PlayerMatchStatsRepository statsRepository;

    @Autowired
    private MatchRepository matchRepository;

    @Autowired
    private PlayerRepository playerRepository;

    @Autowired
    private SeasonRepository seasonRepository;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private PlayerMatchStatsSQLDAO statsDAO;

    @Autowired
    private MatchSQLDAO matchDAO;

    @Autowired
    private SeasonSQLDAO seasonDAO;

    @Autowired
    private PlayerSQLDAO playerDAO;

    @Autowired
    private TeamSQLDAO teamDAO;

    private Match match;
    private Player ndiaye;
    private Player casemiro;

    @BeforeEach
    void prepareDatabase() {
        cleanDatabase();
        Season season = seasonRepository.save(new Season(
                null, "2502", League.PREMIER, LocalDate.of(2026, 8, 21), LocalDate.of(2027, 5, 30), 8));
        Team everton = teamRepository.save(new Team(null, "62", "Everton FC", null, League.PREMIER, "31"));
        Team manUtd = teamRepository.save(new Team(null, "66", "Manchester United FC", null, League.PREMIER, "32"));
        match = matchRepository.saveAll(List.of(new Match(null, "560600", season, Instant.parse("2026-10-17T14:00:00Z"),
                8, MatchStatus.FINISHED, everton, manUtd, null, null, null, "1983600", false))).getFirst();
        ndiaye = playerRepository.save(new Player("8135", "Iliman Ndiaye", Position.FORWARD, everton));
        casemiro = playerRepository.save(new Player("3255", "Casemiro", Position.MIDFIELDER, manUtd));
    }

    @AfterEach
    void cleanDatabaseAfter() {
        cleanDatabase();
    }

    @Test
    void unaFilaGuardadaSeLeeConLasQuinceMetricasElRatingYLaFigura() {
        statsRepository.saveAll(List.of(
                new PlayerMatchStats(null, ndiaye, match, true, 96, allMetrics(), RATING, true),
                new PlayerMatchStats(null, casemiro, match, true, 62, MetricCounts.empty(), null, false)));

        List<PlayerMatchStats> found = statsRepository.findAll();

        assertThat(found)
                .extracting(stats -> stats.player().externalId(), PlayerMatchStats::starter,
                        PlayerMatchStats::minutesPlayed, PlayerMatchStats::counts, PlayerMatchStats::rating,
                        PlayerMatchStats::manOfTheMatch)
                .containsExactlyInAnyOrder(
                        tuple("8135", true, 96, allMetrics(), RATING, true),
                        tuple("3255", true, 62, MetricCounts.empty(), null, false));
    }

    @Test
    void dosFilasDelMismoJugadorEnElMismoPartidoViolanElIndiceUnico() {
        statsRepository.saveAll(List.of(new PlayerMatchStats(null, ndiaye, match, true, 96, allMetrics(), RATING, true)));
        List<PlayerMatchStats> repeated =
                List.of(new PlayerMatchStats(null, ndiaye, match, false, 10, MetricCounts.empty(), null, false));

        assertThatThrownBy(() -> statsRepository.saveAll(repeated))
                .isInstanceOf(DataIntegrityViolationException.class)
                .rootCause()
                .message()
                .containsIgnoringCase(PLAYER_MATCH_INDEX);
    }

    @Test
    void findAllTraeElJugadorConSuEquipoYElPartidoConSuTemporadaYSusEquipos() {
        statsRepository.saveAll(List.of(new PlayerMatchStats(null, ndiaye, match, true, 96, allMetrics(), RATING, true)));

        List<PlayerMatchStats> found = statsRepository.findAll();

        assertThat(found)
                .extracting(stats -> stats.player().team().name(), stats -> stats.match().season().externalId(),
                        stats -> stats.match().homeTeam().name(), stats -> stats.match().awayTeam().name())
                .containsExactly(tuple("Everton FC", "2502", "Everton FC", "Manchester United FC"));
        assertThat(found).singleElement().satisfies(stats -> {
            assertThat(stats.player()).isEqualTo(ndiaye);
            assertThat(stats.match()).isEqualTo(match);
        });
    }

    /**
     * Un valor distinto por métrica, así una columna cruzada con otra no pasa desapercibida.
     */
    private static MetricCounts allMetrics() {
        Map<Metric, Integer> counts = new EnumMap<>(Metric.class);
        for (Metric metric : Metric.values()) {
            counts.put(metric, metric.ordinal() + 1);
        }
        return MetricCounts.of(counts);
    }

    private void cleanDatabase() {
        statsDAO.deleteAll();
        matchDAO.deleteAll();
        seasonDAO.deleteAll();
        playerDAO.deleteAll();
        teamDAO.deleteAll();
    }
}
