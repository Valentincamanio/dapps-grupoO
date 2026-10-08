package ar.edu.unq.desapp.futbolmarket.modelo.match;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import ar.edu.unq.desapp.futbolmarket.modelo.league.League;
import ar.edu.unq.desapp.futbolmarket.modelo.player.exception.CatalogInvariantException;
import ar.edu.unq.desapp.futbolmarket.modelo.season.Season;
import ar.edu.unq.desapp.futbolmarket.modelo.team.Team;

class MatchTest {

    private static final String MATCH_ID = "560593";
    private static final String SEASON_ID = "2502";
    private static final Instant KICK_OFF = Instant.parse("2026-10-10T11:30:00Z");
    private static final int MATCHDAY = 6;
    private static final Season SEASON = new Season(
            1L, SEASON_ID, League.PREMIER, LocalDate.of(2026, 8, 21), LocalDate.of(2027, 5, 30), MATCHDAY);
    private static final Team ARSENAL = new Team(1L, "57", "Arsenal FC", null, League.PREMIER);
    private static final Team LEEDS = new Team(2L, "341", "Leeds United FC", null, League.PREMIER);

    private static final String MISSING_EXTERNAL_ID_MESSAGE = "El identificador externo del partido es obligatorio.";
    private static final String MISSING_DATE_MESSAGE = "La fecha y la hora del partido son obligatorias.";
    private static final String MISSING_HOME_TEAM_MESSAGE = "El equipo local del partido es obligatorio.";
    private static final String MISSING_AWAY_TEAM_MESSAGE = "El equipo visitante del partido es obligatorio.";
    private static final String SAME_TEAMS_MESSAGE = "El equipo local y el visitante de un partido tienen que ser distintos.";

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void rechazaUnPartidoSinIdentificadorExterno(String externalId) {
        assertThatThrownBy(() -> match(externalId, SEASON, KICK_OFF, MatchStatus.TIMED, ARSENAL, LEEDS))
                .isInstanceOf(CatalogInvariantException.class)
                .hasMessage(MISSING_EXTERNAL_ID_MESSAGE);
    }

    @Test
    void rechazaUnPartidoSinTemporada() {
        assertThatThrownBy(() -> match(MATCH_ID, null, KICK_OFF, MatchStatus.TIMED, ARSENAL, LEEDS))
                .isInstanceOf(CatalogInvariantException.class)
                .hasMessage("La temporada del partido es obligatoria.");
    }

    @Test
    void rechazaUnPartidoSinFecha() {
        assertThatThrownBy(() -> match(MATCH_ID, SEASON, null, MatchStatus.TIMED, ARSENAL, LEEDS))
                .isInstanceOf(CatalogInvariantException.class)
                .hasMessage(MISSING_DATE_MESSAGE);
    }

    @Test
    void rechazaUnPartidoSinEstado() {
        assertThatThrownBy(() -> match(MATCH_ID, SEASON, KICK_OFF, null, ARSENAL, LEEDS))
                .isInstanceOf(CatalogInvariantException.class)
                .hasMessage("El estado del partido es obligatorio.");
    }

    @Test
    void rechazaUnPartidoSinEquipoLocal() {
        assertThatThrownBy(() -> match(MATCH_ID, SEASON, KICK_OFF, MatchStatus.TIMED, null, LEEDS))
                .isInstanceOf(CatalogInvariantException.class)
                .hasMessage(MISSING_HOME_TEAM_MESSAGE);
    }

    @Test
    void rechazaUnPartidoSinEquipoVisitante() {
        assertThatThrownBy(() -> match(MATCH_ID, SEASON, KICK_OFF, MatchStatus.TIMED, ARSENAL, null))
                .isInstanceOf(CatalogInvariantException.class)
                .hasMessage(MISSING_AWAY_TEAM_MESSAGE);
    }

    @Test
    void rechazaUnPartidoConElMismoEquipoDeLocalYDeVisitante() {
        assertThatThrownBy(() -> match(MATCH_ID, SEASON, KICK_OFF, MatchStatus.TIMED, ARSENAL, ARSENAL))
                .isInstanceOf(CatalogInvariantException.class)
                .hasMessage(SAME_TEAMS_MESSAGE);
    }

    @ParameterizedTest
    @CsvSource({"-1, 0", "0, -1"})
    void rechazaUnResultadoConGolesNegativos(int home, int away) {
        assertThatThrownBy(() -> new Score(home, away))
                .isInstanceOf(CatalogInvariantException.class)
                .hasMessage("Los goles de un resultado no pueden ser negativos.");
    }

    @Test
    void aceptaUnResultadoSinGoles() {
        Score score = new Score(null, null);

        assertThat(score.home()).isNull();
        assertThat(score.away()).isNull();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void elSnapshotRechazaUnPartidoSinIdentificadorExterno(String externalId) {
        assertThatThrownBy(() -> snapshot(externalId, KICK_OFF, "57", "341"))
                .isInstanceOf(CatalogInvariantException.class)
                .hasMessage(MISSING_EXTERNAL_ID_MESSAGE);
    }

    @Test
    void elSnapshotRechazaUnPartidoSinFecha() {
        assertThatThrownBy(() -> snapshot(MATCH_ID, null, "57", "341"))
                .isInstanceOf(CatalogInvariantException.class)
                .hasMessage(MISSING_DATE_MESSAGE);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void elSnapshotRechazaUnPartidoSinEquipoLocal(String homeTeamExternalId) {
        assertThatThrownBy(() -> snapshot(MATCH_ID, KICK_OFF, homeTeamExternalId, "341"))
                .isInstanceOf(CatalogInvariantException.class)
                .hasMessage(MISSING_HOME_TEAM_MESSAGE);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void elSnapshotRechazaUnPartidoSinEquipoVisitante(String awayTeamExternalId) {
        assertThatThrownBy(() -> snapshot(MATCH_ID, KICK_OFF, "57", awayTeamExternalId))
                .isInstanceOf(CatalogInvariantException.class)
                .hasMessage(MISSING_AWAY_TEAM_MESSAGE);
    }

    @Test
    void elSnapshotRechazaUnPartidoConElMismoEquipoDeLocalYDeVisitante() {
        assertThatThrownBy(() -> snapshot(MATCH_ID, KICK_OFF, "57", " 57 "))
                .isInstanceOf(CatalogInvariantException.class)
                .hasMessage(SAME_TEAMS_MESSAGE);
    }

    @Test
    void elSnapshotAceptaUnEstadoDesconocidoYUnPartidoSinTemporada() {
        MatchSnapshot snapshot = new MatchSnapshot(MATCH_ID, null, KICK_OFF, MATCHDAY, null, "57", "341", null, null, null);

        assertThat(snapshot.status()).isNull();
        assertThat(snapshot.seasonExternalId()).isNull();
    }

    @Test
    void updateFromPasaUnPartidoProgramadoAFinalizadoConSuResultadoYSinPerderLaIdentidad() {
        Match timed = match(MATCH_ID, SEASON, KICK_OFF, MatchStatus.TIMED, ARSENAL, LEEDS);
        MatchSnapshot finished = new MatchSnapshot(MATCH_ID, SEASON_ID, KICK_OFF, MATCHDAY, MatchStatus.FINISHED,
                "57", "341", new Score(2, 1), new Score(1, 0), MatchWinner.HOME_TEAM);

        Match updated = timed.updateFrom(finished, ARSENAL, LEEDS);

        assertThat(updated).isEqualTo(new Match(1L, MATCH_ID, SEASON, KICK_OFF, MATCHDAY, MatchStatus.FINISHED,
                ARSENAL, LEEDS, new Score(2, 1), new Score(1, 0), MatchWinner.HOME_TEAM));
    }

    @Test
    void toNewMatchArmaUnPartidoSinIdConLaTemporadaYLosEquiposRecibidos() {
        MatchSnapshot snapshot = snapshot(MATCH_ID, KICK_OFF, "57", "341");

        Match match = snapshot.toNewMatch(SEASON, ARSENAL, LEEDS);

        assertThat(match).isEqualTo(new Match(null, MATCH_ID, SEASON, KICK_OFF, MATCHDAY, MatchStatus.TIMED,
                ARSENAL, LEEDS, null, null, null));
    }

    private static Match match(String externalId, Season season, Instant utcDate, MatchStatus status, Team home,
                               Team away) {
        return new Match(1L, externalId, season, utcDate, MATCHDAY, status, home, away, null, null, null);
    }

    private static MatchSnapshot snapshot(String externalId, Instant utcDate, String homeTeamExternalId,
                                          String awayTeamExternalId) {
        return new MatchSnapshot(externalId, SEASON_ID, utcDate, MATCHDAY, MatchStatus.TIMED, homeTeamExternalId,
                awayTeamExternalId, null, null, null);
    }
}
