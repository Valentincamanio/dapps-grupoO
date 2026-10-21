package ar.edu.unq.desapp.futbolmarket.persistence.repository.performance;

import ar.edu.unq.desapp.futbolmarket.modelo.league.League;
import ar.edu.unq.desapp.futbolmarket.modelo.match.Match;
import ar.edu.unq.desapp.futbolmarket.modelo.match.MatchStatus;
import ar.edu.unq.desapp.futbolmarket.modelo.match.MatchWinner;
import ar.edu.unq.desapp.futbolmarket.modelo.match.Score;
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
import ar.edu.unq.desapp.futbolmarket.persistence.sql.entity.performance.PlayerMatchStatsSQL;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * La lectura por rango de fechas que usa la cotización para armar una semana: de lunes 00:00 a
 * lunes 00:00 de Argentina, con el inicio adentro y el fin afuera.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:stats-it;DB_CLOSE_DELAY=-1")
class PlayerMatchStatsRepositoryMatchBetweenIT {
    private static final ZoneId ARGENTINA = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final Instant WEEK_START = LocalDate.of(2026, 10, 12).atStartOfDay(ARGENTINA).toInstant();
    private static final Instant WEEK_END = LocalDate.of(2026, 10, 19).atStartOfDay(ARGENTINA).toInstant();
    private static final Instant NEXT_WEEK_END = LocalDate.of(2026, 10, 26).atStartOfDay(ARGENTINA).toInstant();
    private static final Instant SUNDAY_BEFORE_WEEK = WEEK_START.minusSeconds(1);
    private static final Instant SATURDAY_IN_WEEK = Instant.parse("2026-10-17T14:00:00Z");
    private static final Instant TUESDAY_AFTER_WEEK = Instant.parse("2026-10-20T19:00:00Z");

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

    private Season season;
    private Team everton;
    private Team manUtd;
    private Player ndiaye;

    @BeforeEach
    void prepareDatabase() {
        cleanDatabase();
        season = seasonRepository.save(new Season(
                null, "2502", League.PREMIER, LocalDate.of(2026, 8, 21), LocalDate.of(2027, 5, 30), 8));
        everton = teamRepository.save(new Team(null, "62", "Everton FC", null, League.PREMIER, "31"));
        manUtd = teamRepository.save(new Team(null, "66", "Manchester United FC", null, League.PREMIER, "32"));
        ndiaye = playerRepository.save(new Player("8135", "Iliman Ndiaye", Position.FORWARD, everton));
    }

    @AfterEach
    void cleanDatabaseAfter() {
        cleanDatabase();
    }

    @Test
    void traeSoloLasEstadisticasDeLosPartidosDentroDelRango() {
        Match before = saveMatch("560600", SUNDAY_BEFORE_WEEK);
        Match inside = saveMatch("560601", SATURDAY_IN_WEEK);
        Match after = saveMatch("560602", TUESDAY_AFTER_WEEK);
        saveStatsOf(before, inside, after);

        List<PlayerMatchStats> found = statsRepository.findAllByMatchBetween(WEEK_START, WEEK_END);

        assertThat(found)
                .extracting(stats -> stats.match().externalId())
                .containsExactly("560601");
    }

    @Test
    void unPartidoJustoEnElInicioQuedaAdentroYUnoJustoEnElFinQuedaAfuera() {
        Match atStart = saveMatch("560600", WEEK_START);
        Match atEnd = saveMatch("560601", WEEK_END);
        saveStatsOf(atStart, atEnd);

        List<PlayerMatchStats> thisWeek = statsRepository.findAllByMatchBetween(WEEK_START, WEEK_END);
        List<PlayerMatchStats> nextWeek = statsRepository.findAllByMatchBetween(WEEK_END, NEXT_WEEK_END);

        assertThat(thisWeek)
                .extracting(stats -> stats.match().externalId())
                .containsExactly("560600");
        assertThat(nextWeek)
                .extracting(stats -> stats.match().externalId())
                .containsExactly("560601");
    }

    @Test
    void elRepositoryDevuelveElJugadorConSuEquipoYElPartidoConSuTemporadaYSusEquipos() {
        saveStatsOf(saveMatch("560600", SATURDAY_IN_WEEK));

        List<PlayerMatchStats> found = statsRepository.findAllByMatchBetween(WEEK_START, WEEK_END);

        assertThat(found)
                .extracting(stats -> stats.player().team().name(), stats -> stats.match().season().externalId(),
                        stats -> stats.match().homeTeam().name(), stats -> stats.match().awayTeam().name())
                .containsExactly(tuple("Everton FC", "2502", "Everton FC", "Manchester United FC"));
    }

    /**
     * El test no es transaccional y {@code open-in-view} está apagado: si el {@code @EntityGraph} no
     * trajera alguna asociación, leerla acá lanzaría {@code LazyInitializationException}.
     */
    @Test
    void lasAsociacionesSeLeenFueraDeLaTransaccionSinLazyInitializationException() {
        saveStatsOf(saveMatch("560600", SATURDAY_IN_WEEK));

        List<PlayerMatchStatsSQL> found = statsDAO.findAllByMatchUtcDateBetween(WEEK_START, WEEK_END);

        assertThat(found)
                .extracting(stats -> stats.getPlayer().getName(), stats -> stats.getPlayer().getTeam().getName(),
                        stats -> stats.getMatch().getSeason().getExternalId(),
                        stats -> stats.getMatch().getHomeTeam().getName(),
                        stats -> stats.getMatch().getAwayTeam().getName())
                .containsExactly(tuple("Iliman Ndiaye", "Everton FC", "2502", "Everton FC", "Manchester United FC"));
    }

    @Test
    void unRangoSinPartidosDevuelveUnaListaVacia() {
        saveStatsOf(saveMatch("560600", SATURDAY_IN_WEEK));

        List<PlayerMatchStats> found = statsRepository.findAllByMatchBetween(WEEK_END, NEXT_WEEK_END);

        assertThat(found).isEmpty();
    }

    private Match saveMatch(String externalId, Instant kickOff) {
        return matchRepository.saveAll(List.of(new Match(null, externalId, season, kickOff, 8, MatchStatus.FINISHED,
                everton, manUtd, new Score(1, 0), new Score(0, 0), MatchWinner.HOME_TEAM))).getFirst();
    }

    private void saveStatsOf(Match... matches) {
        statsRepository.saveAll(List.of(matches).stream()
                .map(match -> new PlayerMatchStats(null, ndiaye, match, true, 90, MetricCounts.empty(), null, false))
                .toList());
    }

    private void cleanDatabase() {
        statsDAO.deleteAll();
        matchDAO.deleteAll();
        seasonDAO.deleteAll();
        playerDAO.deleteAll();
        teamDAO.deleteAll();
    }
}
