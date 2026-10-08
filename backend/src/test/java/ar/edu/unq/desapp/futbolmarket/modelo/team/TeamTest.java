package ar.edu.unq.desapp.futbolmarket.modelo.team;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import ar.edu.unq.desapp.futbolmarket.modelo.league.League;
import ar.edu.unq.desapp.futbolmarket.modelo.player.PlayerSnapshot;
import ar.edu.unq.desapp.futbolmarket.modelo.player.exception.CatalogInvariantException;
import ar.edu.unq.desapp.futbolmarket.modelo.position.Position;

class TeamTest {

    private static final String LIVERPOOL_ID = "64";
    private static final String LIVERPOOL_NAME = "Liverpool FC";
    private static final String LIVERPOOL_CREST = "https://crests.football-data.org/64.png";
    private static final String MISSING_EXTERNAL_ID_MESSAGE = "El identificador externo del equipo es obligatorio.";

    private final PlayerSnapshot alisson = new PlayerSnapshot(
            "1795", "Alisson Becker", Position.GOALKEEPER, LocalDate.of(1992, 10, 2), "Brazil");
    private final PlayerSnapshot chiesa = new PlayerSnapshot(
            "1780", "Federico Chiesa", Position.FORWARD, LocalDate.of(1997, 10, 25), "Italy");

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void rechazaUnEquipoSinIdentificadorExterno(String externalId) {
        assertThatThrownBy(() -> new Team(externalId, LIVERPOOL_NAME, null, League.PREMIER))
                .isInstanceOf(CatalogInvariantException.class)
                .hasMessage(MISSING_EXTERNAL_ID_MESSAGE);
    }

    @Test
    void recortaElNombreYElIdentificadorExternoYDejaSinEscudoUnoEnBlanco() {
        Team team = new Team("  64 ", "  Liverpool FC  ", "   ", League.PREMIER);

        assertThat(team.externalId()).isEqualTo(LIVERPOOL_ID);
        assertThat(team.name()).isEqualTo(LIVERPOOL_NAME);
        assertThat(team.crest()).isNull();
    }

    @Test
    void updateFromConservaLaIdentidadYTomaElNombreOficialElEscudoYLaLiga() {
        Team team = new Team(10L, LIVERPOOL_ID, "Liverpool", null, League.LA_LIGA);
        TeamSnapshot snapshot = new TeamSnapshot(LIVERPOOL_ID, LIVERPOOL_NAME, LIVERPOOL_CREST, List.of(alisson));

        Team updated = team.updateFrom(snapshot, League.PREMIER);

        assertThat(updated).isEqualTo(new Team(10L, LIVERPOOL_ID, LIVERPOOL_NAME, LIVERPOOL_CREST, League.PREMIER));
    }

    @Test
    void elSnapshotCopiaElPlantel() {
        List<PlayerSnapshot> squad = new ArrayList<>(List.of(alisson));
        TeamSnapshot snapshot = new TeamSnapshot(LIVERPOOL_ID, LIVERPOOL_NAME, LIVERPOOL_CREST, squad);

        squad.add(chiesa);

        assertThat(snapshot.squad()).containsExactly(alisson);
    }

    @Test
    void elSnapshotSinPlantelQuedaConUnPlantelVacio() {
        TeamSnapshot snapshot = new TeamSnapshot(LIVERPOOL_ID, LIVERPOOL_NAME, LIVERPOOL_CREST, null);

        assertThat(snapshot.squad()).isEmpty();
    }

    @Test
    void elSnapshotRechazaUnEquipoSinIdentificadorExternoConElMensajeDeTeam() {
        assertThatThrownBy(() -> new TeamSnapshot(" ", LIVERPOOL_NAME, LIVERPOOL_CREST, List.of()))
                .isInstanceOf(CatalogInvariantException.class)
                .hasMessage(MISSING_EXTERNAL_ID_MESSAGE);
    }

    @Test
    void toNewTeamArmaUnEquipoSinIdEnLaLigaRecibida() {
        TeamSnapshot snapshot = new TeamSnapshot(LIVERPOOL_ID, LIVERPOOL_NAME, LIVERPOOL_CREST, List.of(alisson));

        Team team = snapshot.toNewTeam(League.PREMIER);

        assertThat(team).isEqualTo(new Team(null, LIVERPOOL_ID, LIVERPOOL_NAME, LIVERPOOL_CREST, League.PREMIER));
    }
}
