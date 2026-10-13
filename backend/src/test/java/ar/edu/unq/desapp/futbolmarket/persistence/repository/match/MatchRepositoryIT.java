package ar.edu.unq.desapp.futbolmarket.persistence.repository.match;

import ar.edu.unq.desapp.futbolmarket.modelo.league.League;
import ar.edu.unq.desapp.futbolmarket.modelo.match.Match;
import ar.edu.unq.desapp.futbolmarket.modelo.match.MatchSnapshot;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:sync-it;DB_CLOSE_DELAY=-1")
class MatchRepositoryIT {
    private static final String FINISHED_MATCH_ID = "560542";
    private static final String TIMED_MATCH_ID = "560593";
    private static final Instant FINISHED_KICK_OFF = Instant.parse("2026-08-21T19:00:00Z");
    private static final Instant TIMED_KICK_OFF = Instant.parse("2026-10-10T11:30:00Z");
    private static final String EXTERNAL_ID_INDEX = "ux_matches_external_id";

    @Autowired
    private MatchRepository matchRepository;

    @Autowired
    private SeasonRepository seasonRepository;

    @Autowired
    private TeamRepository teamRepository;

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
    private Team leeds;

    @BeforeEach
    void prepareDatabase() {
        matchDAO.deleteAll();
        seasonDAO.deleteAll();
        playerDAO.deleteAll();
        teamDAO.deleteAll();
        season = seasonRepository.save(new Season(
                null, "2502", League.PREMIER, LocalDate.of(2026, 8, 21), LocalDate.of(2027, 5, 30), 6));
        arsenal = teamRepository.save(new Team("57", "Arsenal FC", null, League.PREMIER));
        coventry = teamRepository.save(new Team("1076", "Coventry City FC", null, League.PREMIER));
        leeds = teamRepository.save(new Team("341", "Leeds United FC", null, League.PREMIER));
    }

    @Test
    void saveAllGuardaLosPartidosConSuTemporadaYSusEquipos() {
        List<Match> saved = matchRepository.saveAll(List.of(finishedMatch(), timedMatch()));

        assertThat(saved).allSatisfy(match -> assertThat(match.id()).isNotNull());
        assertThat(saved).extracting(Match::season).containsOnly(season);
        assertThat(saved)
                .extracting(Match::homeTeam, Match::awayTeam)
                .containsExactly(tuple(arsenal, coventry), tuple(arsenal, leeds));
        assertThat(matchDAO.count()).isEqualTo(2);
    }

    @Test
    void findAllByExternalIdsDevuelveLosPartidosConSuTemporadaYSusEquipos() {
        matchRepository.saveAll(List.of(finishedMatch(), timedMatch()));

        List<Match> found = matchRepository.findAllByExternalIds(List.of(FINISHED_MATCH_ID, TIMED_MATCH_ID));

        assertThat(found).extracting(Match::season).containsOnly(season);
        assertThat(found)
                .extracting(Match::externalId, match -> match.homeTeam().name(), match -> match.awayTeam().name())
                .containsExactlyInAnyOrder(
                        tuple(FINISHED_MATCH_ID, "Arsenal FC", "Coventry City FC"),
                        tuple(TIMED_MATCH_ID, "Arsenal FC", "Leeds United FC"));
    }

    @Test
    void findAllByExternalIdsConUnaColeccionVaciaDevuelveUnaListaVacia() {
        matchRepository.saveAll(List.of(timedMatch()));

        List<Match> found = matchRepository.findAllByExternalIds(List.of());

        assertThat(found).isEmpty();
    }

    @Test
    void unPartidoSinJugarVuelveSinResultadoNiGanador() {
        matchRepository.saveAll(List.of(timedMatch()));

        List<Match> found = matchRepository.findAllByExternalIds(List.of(TIMED_MATCH_ID));

        assertThat(found).singleElement().satisfies(match -> {
            assertThat(match.status()).isEqualTo(MatchStatus.TIMED);
            assertThat(match.fullTime()).isNull();
            assertThat(match.halfTime()).isNull();
            assertThat(match.winner()).isNull();
        });
    }

    @Test
    void unPartidoJugadoVuelveConSuResultadoYSuGanador() {
        matchRepository.saveAll(List.of(finishedMatch()));

        List<Match> found = matchRepository.findAllByExternalIds(List.of(FINISHED_MATCH_ID));

        assertThat(found).singleElement().satisfies(match -> {
            assertThat(match.status()).isEqualTo(MatchStatus.FINISHED);
            assertThat(match.utcDate()).isEqualTo(FINISHED_KICK_OFF);
            assertThat(match.matchday()).isEqualTo(1);
            assertThat(match.fullTime()).isEqualTo(new Score(3, 0));
            assertThat(match.halfTime()).isEqualTo(new Score(2, 0));
            assertThat(match.winner()).isEqualTo(MatchWinner.HOME_TEAM);
        });
    }

    @Test
    void saveAllActualizaUnPartidoExistenteSinCrearOtro() {
        Match saved = matchRepository.saveAll(List.of(timedMatch())).getFirst();
        var finished = new MatchSnapshot(TIMED_MATCH_ID, "2502", TIMED_KICK_OFF, 6, MatchStatus.FINISHED,
                "57", "341", new Score(2, 1), new Score(1, 0), MatchWinner.HOME_TEAM);

        List<Match> updated = matchRepository.saveAll(List.of(saved.updateFrom(finished, arsenal, leeds)));

        assertThat(updated).singleElement().satisfies(match -> {
            assertThat(match.id()).isEqualTo(saved.id());
            assertThat(match.status()).isEqualTo(MatchStatus.FINISHED);
            assertThat(match.fullTime()).isEqualTo(new Score(2, 1));
        });
        assertThat(matchDAO.count()).isEqualTo(1);
    }

    @Test
    void unIdentificadorExternoRepetidoViolaElIndiceUnico() {
        matchRepository.saveAll(List.of(timedMatch()));
        var repeated = List.of(timedMatch());

        assertThatThrownBy(() -> matchRepository.saveAll(repeated))
                .isInstanceOf(DataIntegrityViolationException.class)
                .rootCause()
                .message()
                .containsIgnoringCase(EXTERNAL_ID_INDEX);
    }

    private Match finishedMatch() {
        return new Match(null, FINISHED_MATCH_ID, season, FINISHED_KICK_OFF, 1, MatchStatus.FINISHED,
                arsenal, coventry, new Score(3, 0), new Score(2, 0), MatchWinner.HOME_TEAM);
    }

    private Match timedMatch() {
        return new Match(null, TIMED_MATCH_ID, season, TIMED_KICK_OFF, 6, MatchStatus.TIMED,
                arsenal, leeds, null, null, null);
    }
}
