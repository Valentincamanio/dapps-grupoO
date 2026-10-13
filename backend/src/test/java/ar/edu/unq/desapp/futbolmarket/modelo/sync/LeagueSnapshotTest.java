package ar.edu.unq.desapp.futbolmarket.modelo.sync;

import static ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotFixtures.ALISSON_ID;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotFixtures.CHELSEA_ID;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotFixtures.CHELSEA_NAME;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotFixtures.CHIESA_ID;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotFixtures.LIVERPOOL_ID;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotFixtures.LIVERPOOL_NAME;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotFixtures.PREMIER_SEASON_ID;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotFixtures.alisson;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotFixtures.chiesa;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotFixtures.finishedMatchId;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotFixtures.liverpool;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotFixtures.match;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotFixtures.season;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotFixtures.snapshot;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotFixtures.team;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.SnapshotFixtures.timedMatchId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;

import ar.edu.unq.desapp.futbolmarket.modelo.league.League;
import ar.edu.unq.desapp.futbolmarket.modelo.match.MatchSnapshot;
import ar.edu.unq.desapp.futbolmarket.modelo.match.MatchStatus;
import ar.edu.unq.desapp.futbolmarket.modelo.sync.exception.ExternalSourceException;
import ar.edu.unq.desapp.futbolmarket.modelo.team.TeamSnapshot;

class LeagueSnapshotTest {

    @ParameterizedTest
    @NullAndEmptySource
    void unaLigaSinEquiposFallaConElMotivoDeLaFuenteSinEquipos(List<TeamSnapshot> teams) {
        assertThatThrownBy(() -> new LeagueSnapshot(League.PREMIER, season(League.PREMIER, PREMIER_SEASON_ID), teams,
                List.of()))
                .isInstanceOf(ExternalSourceException.class)
                .hasMessage("La fuente no informó ningún equipo para la liga.");
    }

    @Test
    void unaTemporadaDeOtraLigaFallaConElMotivoDeFormato() {
        var bundesligaSeason = season(League.BUNDESLIGA, PREMIER_SEASON_ID);
        var teams = List.of(liverpool());

        assertThatThrownBy(() -> new LeagueSnapshot(League.PREMIER, bundesligaSeason, teams, List.of()))
                .isInstanceOf(ExternalSourceException.class)
                .hasMessage("La respuesta de la fuente no tiene el formato esperado.");
    }

    @Test
    void unaLigaSinTemporadaFallaConElMotivoDeFormato() {
        var teams = List.of(liverpool());

        assertThatThrownBy(() -> new LeagueSnapshot(League.PREMIER, null, teams, List.of()))
                .isInstanceOf(ExternalSourceException.class)
                .hasMessage("La respuesta de la fuente no tiene el formato esperado.");
    }

    @Test
    void unPartidoDeOtraTemporadaFallaConSuMotivo() {
        var teams = List.of(liverpool());
        var matches = List.of(match("249001", "2401", MatchStatus.FINISHED, LIVERPOOL_ID, CHELSEA_ID));

        assertThatThrownBy(() -> snapshot(League.PREMIER, teams, matches))
                .isInstanceOf(ExternalSourceException.class)
                .hasMessage("La fuente informó partidos de otra temporada.");
    }

    @Test
    void unPartidoSinTemporadaInformadaSeAcepta() {
        MatchSnapshot withoutSeason = match("250299", null, MatchStatus.TIMED, LIVERPOOL_ID, CHELSEA_ID);

        LeagueSnapshot snapshot = snapshot(League.PREMIER, List.of(liverpool()), List.of(withoutSeason));

        assertThat(snapshot.matches()).containsExactly(withoutSeason);
    }

    @Test
    void teamExternalIdsDevuelveLosEquiposEnElOrdenDeLaFuente() {
        LeagueSnapshot premier = snapshot(League.PREMIER);

        List<String> teamExternalIds = premier.teamExternalIds();

        assertThat(teamExternalIds).containsExactly(LIVERPOOL_ID, CHELSEA_ID);
    }

    @Test
    void playerExternalIdsDevuelveCadaJugadorUnaSolaVez() {
        LeagueSnapshot premier = snapshot(League.PREMIER,
                team(LIVERPOOL_ID, LIVERPOOL_NAME, alisson(), alisson(), chiesa()),
                team(CHELSEA_ID, CHELSEA_NAME, chiesa()));

        List<String> playerExternalIds = premier.playerExternalIds();

        assertThat(playerExternalIds).containsExactly(ALISSON_ID, CHIESA_ID);
    }

    @Test
    void matchExternalIdsDevuelveLosPartidosDeLaLiga() {
        LeagueSnapshot premier = snapshot(League.PREMIER);

        List<String> matchExternalIds = premier.matchExternalIds();

        assertThat(matchExternalIds).containsExactly(finishedMatchId(League.PREMIER), timedMatchId(League.PREMIER));
    }

    @Test
    void matchTeamExternalIdsDevuelveLocalYVisitanteAunqueNoSeanEquiposDeLaLiga() {
        LeagueSnapshot premier = snapshot(League.PREMIER, List.of(liverpool()), List.of(
                match("250297", PREMIER_SEASON_ID, MatchStatus.TIMED, LIVERPOOL_ID, CHELSEA_ID),
                match("250298", PREMIER_SEASON_ID, MatchStatus.TIMED, "1076", LIVERPOOL_ID)));

        List<String> matchTeamExternalIds = premier.matchTeamExternalIds();

        assertThat(matchTeamExternalIds).containsExactly(LIVERPOOL_ID, CHELSEA_ID, "1076");
    }
}
