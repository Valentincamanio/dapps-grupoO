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
import ar.edu.unq.desapp.futbolmarket.persistence.sql.entity.match.MatchSQL;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * La lectura de los partidos terminados, con la que la cotización decide qué semanas cerradas
 * tienen partidos y si a alguno le faltan estadísticas.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:stats-it;DB_CLOSE_DELAY=-1")
class MatchRepositoryFinishedIT {
    private static final Instant KICK_OFF = Instant.parse("2026-10-17T14:00:00Z");

    @Autowired
    private MatchRepository matchRepository;

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
    private Team arsenal;
    private Team coventry;

    @BeforeEach
    void prepareDatabase() {
        cleanDatabase();
        season = seasonRepository.save(new Season(
                null, "2502", League.PREMIER, LocalDate.of(2026, 8, 21), LocalDate.of(2027, 5, 30), 8));
        arsenal = teamRepository.save(new Team("57", "Arsenal FC", null, League.PREMIER));
        coventry = teamRepository.save(new Team("1076", "Coventry City FC", null, League.PREMIER));
    }

    @AfterEach
    void cleanDatabaseAfter() {
        cleanDatabase();
    }

    @Test
    void findAllFinishedDevuelveSoloLosPartidosTerminados() {
        matchRepository.saveAll(List.of(
                finishedMatch("560600"),
                finishedMatch("560601"),
                unplayedMatch("560602", MatchStatus.SCHEDULED),
                unplayedMatch("560603", MatchStatus.TIMED),
                unplayedMatch("560604", MatchStatus.IN_PLAY),
                unplayedMatch("560605", MatchStatus.POSTPONED)));

        List<Match> found = matchRepository.findAllFinished();

        assertThat(found)
                .extracting(Match::externalId, Match::status)
                .containsExactlyInAnyOrder(
                        tuple("560600", MatchStatus.FINISHED),
                        tuple("560601", MatchStatus.FINISHED));
    }

    @Test
    void findAllFinishedTraeElLocalElVisitanteYLaTemporada() {
        matchRepository.saveAll(List.of(finishedMatch("560600")));

        List<Match> found = matchRepository.findAllFinished();

        assertThat(found)
                .extracting(match -> match.homeTeam().name(), match -> match.awayTeam().name(),
                        match -> match.season().externalId(), Match::fullTime, Match::winner)
                .containsExactly(tuple("Arsenal FC", "Coventry City FC", "2502", new Score(3, 0), MatchWinner.HOME_TEAM));
    }

    /**
     * El test no es transaccional y {@code open-in-view} está apagado: si el {@code @EntityGraph} no
     * trajera alguna asociación, leerla acá lanzaría {@code LazyInitializationException}.
     */
    @Test
    void lasAsociacionesSeLeenFueraDeLaTransaccionSinLazyInitializationException() {
        matchRepository.saveAll(List.of(finishedMatch("560600")));

        List<MatchSQL> found = matchDAO.findAllByStatus(MatchStatus.FINISHED.name());

        assertThat(found)
                .extracting(match -> match.getHomeTeam().getName(), match -> match.getAwayTeam().getName(),
                        match -> match.getSeason().getExternalId())
                .containsExactly(tuple("Arsenal FC", "Coventry City FC", "2502"));
    }

    @Test
    void sinPartidosTerminadosDevuelveUnaListaVacia() {
        matchRepository.saveAll(List.of(
                unplayedMatch("560602", MatchStatus.SCHEDULED),
                unplayedMatch("560603", MatchStatus.TIMED)));

        List<Match> found = matchRepository.findAllFinished();

        assertThat(found).isEmpty();
    }

    private Match finishedMatch(String externalId) {
        return new Match(null, externalId, season, KICK_OFF, 8, MatchStatus.FINISHED,
                arsenal, coventry, new Score(3, 0), new Score(2, 0), MatchWinner.HOME_TEAM);
    }

    private Match unplayedMatch(String externalId, MatchStatus status) {
        return new Match(null, externalId, season, KICK_OFF, 8, status, arsenal, coventry, null, null, null);
    }

    private void cleanDatabase() {
        statsDAO.deleteAll();
        matchDAO.deleteAll();
        seasonDAO.deleteAll();
        playerDAO.deleteAll();
        teamDAO.deleteAll();
    }
}
