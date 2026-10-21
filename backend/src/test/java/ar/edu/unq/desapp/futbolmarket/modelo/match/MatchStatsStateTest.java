package ar.edu.unq.desapp.futbolmarket.modelo.match;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import ar.edu.unq.desapp.futbolmarket.modelo.league.League;
import ar.edu.unq.desapp.futbolmarket.modelo.player.exception.CatalogInvariantException;
import ar.edu.unq.desapp.futbolmarket.modelo.season.Season;
import ar.edu.unq.desapp.futbolmarket.modelo.team.Team;

class MatchStatsStateTest {

    private static final String MATCH_ID = "560600";
    private static final String SEASON_ID = "2502";
    private static final Instant KICK_OFF = Instant.parse("2026-10-17T14:00:00Z");
    private static final int MATCHDAY = 8;
    private static final Season SEASON = new Season(
            1L, SEASON_ID, League.PREMIER, LocalDate.of(2026, 8, 21), LocalDate.of(2027, 5, 30), MATCHDAY);
    private static final Team EVERTON = new Team(1L, "62", "Everton FC", null, League.PREMIER, "31");
    private static final Team MAN_UTD = new Team(2L, "66", "Manchester United FC", null, League.PREMIER, "32");
    private static final String MATCH_WHOSCORED_ID = "1983600";
    private static final String OTHER_WHOSCORED_ID = "1983601";

    @Test
    void elConstructorDeOnceArgumentosDejaElPartidoSinCruzarYSinEstadisticas() {
        Match match = new Match(1L, MATCH_ID, SEASON, KICK_OFF, MATCHDAY, MatchStatus.TIMED, EVERTON, MAN_UTD,
                null, null, null);

        assertThat(match.whoscoredId()).isNull();
        assertThat(match.statsLoaded()).isFalse();
    }

    @Test
    void unPartidoTerminadoSinEstadisticasLasNecesita() {
        Match match = match(MatchStatus.FINISHED, null, false);

        boolean needsStats = match.needsStats();

        assertThat(needsStats).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = MatchStatus.class, names = "FINISHED", mode = EnumSource.Mode.EXCLUDE)
    void unPartidoQueNoTerminoNoNecesitaEstadisticas(MatchStatus status) {
        Match match = match(status, null, false);

        boolean needsStats = match.needsStats();

        assertThat(needsStats).isFalse();
    }

    @Test
    void despuesDeMarcarLasEstadisticasYaNoLasNecesita() {
        Match match = match(MatchStatus.FINISHED, MATCH_WHOSCORED_ID, false);

        Match loaded = match.markStatsLoaded();

        assertThat(loaded.statsLoaded()).isTrue();
        assertThat(loaded.needsStats()).isFalse();
        assertThat(loaded.whoscoredId()).isEqualTo(MATCH_WHOSCORED_ID);
    }

    @Test
    void updateFromConservaElCruceYLaMarcaDeEstadisticas() {
        Match match = match(MatchStatus.FINISHED, MATCH_WHOSCORED_ID, true);
        MatchSnapshot corrected = snapshot(MatchStatus.FINISHED, new Score(1, 1), MatchWinner.DRAW);

        Match updated = match.updateFrom(corrected, EVERTON, MAN_UTD);

        assertThat(updated.fullTime()).isEqualTo(new Score(1, 1));
        assertThat(updated.whoscoredId()).isEqualTo(MATCH_WHOSCORED_ID);
        assertThat(updated.statsLoaded()).isTrue();
    }

    @Test
    void updateFromConservaElCruceCuandoElPartidoPasaATerminado() {
        Match match = match(MatchStatus.TIMED, MATCH_WHOSCORED_ID, false);
        MatchSnapshot finished = snapshot(MatchStatus.FINISHED, new Score(2, 0), MatchWinner.HOME_TEAM);

        Match updated = match.updateFrom(finished, EVERTON, MAN_UTD);

        assertThat(updated.status()).isEqualTo(MatchStatus.FINISHED);
        assertThat(updated.whoscoredId()).isEqualTo(MATCH_WHOSCORED_ID);
        assertThat(updated.statsLoaded()).isFalse();
        assertThat(updated.needsStats()).isTrue();
    }

    @Test
    void crossWithLeAsignaElIdentificadorDeWhoScoredSinCambiarLoDemas() {
        Match match = match(MatchStatus.TIMED, null, false);

        Match crossed = match.crossWith(" 1983600 ");

        assertThat(crossed).isEqualTo(match(MatchStatus.TIMED, MATCH_WHOSCORED_ID, false));
        assertThat(crossed.isCrossed()).isTrue();
    }

    @Test
    void crossWithConElMismoIdentificadorDevuelveUnPartidoIgual() {
        Match match = match(MatchStatus.TIMED, MATCH_WHOSCORED_ID, false);

        Match crossedAgain = match.crossWith(MATCH_WHOSCORED_ID);

        assertThat(crossedAgain).isEqualTo(match);
    }

    @Test
    void crossWithConOtroIdentificadorRechazaElCambioDeCruce() {
        Match match = match(MatchStatus.TIMED, MATCH_WHOSCORED_ID, false);

        assertThatThrownBy(() -> match.crossWith(OTHER_WHOSCORED_ID))
                .isInstanceOf(CatalogInvariantException.class)
                .hasMessage("El partido ya está cruzado con otro identificador de WhoScored.");
    }

    @Test
    void unPartidoALasVeintitresYTreintaUtcEsDeEseDiaUtc() {
        Match match = new Match(1L, MATCH_ID, SEASON, Instant.parse("2026-10-17T23:30:00Z"), MATCHDAY,
                MatchStatus.TIMED, EVERTON, MAN_UTD, null, null, null);

        LocalDate day = match.utcDay();

        assertThat(day).isEqualTo(LocalDate.of(2026, 10, 17));
    }

    private static Match match(MatchStatus status, String whoscoredId, boolean statsLoaded) {
        return new Match(1L, MATCH_ID, SEASON, KICK_OFF, MATCHDAY, status, EVERTON, MAN_UTD, null, null, null,
                whoscoredId, statsLoaded);
    }

    private static MatchSnapshot snapshot(MatchStatus status, Score fullTime, MatchWinner winner) {
        return new MatchSnapshot(MATCH_ID, SEASON_ID, KICK_OFF, MATCHDAY, status, "62", "66", fullTime, null, winner);
    }
}
