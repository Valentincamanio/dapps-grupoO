package ar.edu.unq.desapp.futbolmarket.modelo.player;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.unq.desapp.futbolmarket.modelo.league.League;
import ar.edu.unq.desapp.futbolmarket.modelo.player.exception.CatalogInvariantException;
import ar.edu.unq.desapp.futbolmarket.modelo.position.Position;
import ar.edu.unq.desapp.futbolmarket.modelo.team.Team;

class PlayerTest {
    private final Team river = new Team(1L, "river", "River", null, League.LA_LIGA);

    @Test
    void rechazaEquipoSinNombreOLiga() {
        assertThatThrownBy(() -> new Team("57", " ", null, League.PREMIER))
                .isInstanceOf(CatalogInvariantException.class)
                .hasMessage("El nombre del equipo es obligatorio.");
        assertThatThrownBy(() -> new Team("57", "Arsenal", null, null))
                .isInstanceOf(CatalogInvariantException.class)
                .hasMessage("La liga del equipo es obligatoria.");
    }

    @Test
    void rechazaJugadorSinDatosObligatorios() {
        assertThatThrownBy(() -> new Player("id-1", " ", Position.FORWARD, river))
                .isInstanceOf(CatalogInvariantException.class)
                .hasMessage("El nombre del jugador es obligatorio.");
        assertThatThrownBy(() -> new Player("id-1", "Nombre", null, river))
                .isInstanceOf(CatalogInvariantException.class)
                .hasMessage("La posición del jugador es obligatoria.");
        assertThatThrownBy(() -> new Player("id-1", "Nombre", Position.FORWARD, null))
                .isInstanceOf(CatalogInvariantException.class)
                .hasMessage("El equipo del jugador es obligatorio.");
    }

    @Test
    void derivaLaLigaDesdeElEquipo() {
        var player = new Player(5L, "mbappe", "Kylian Mbappé", Position.FORWARD, river);

        assertThat(player.league()).isEqualTo(League.LA_LIGA);
    }

    @Test
    void calculaMetadatosDePaginaBaseCero() {
        var firstPage = new PlayerPage(List.of(), 0, 10, 21);
        var lastPage = new PlayerPage(List.of(), 2, 10, 21);

        assertThat(firstPage.totalPages()).isEqualTo(3);
        assertThat(firstPage.hasPrevious()).isFalse();
        assertThat(firstPage.hasNext()).isTrue();
        assertThat(lastPage.hasPrevious()).isTrue();
        assertThat(lastPage.hasNext()).isFalse();
    }

    @Test
    void losConstructoresDeConvenienciaDejanAlJugadorActivoSinFechaDeNacimientoNiNacionalidad() {
        var withoutId = new Player("1780", "Federico Chiesa", Position.FORWARD, river);
        var withId = new Player(5L, "1780", "Federico Chiesa", Position.FORWARD, river);

        assertThat(List.of(withoutId, withId)).allSatisfy(player -> {
            assertThat(player.active()).isTrue();
            assertThat(player.dateOfBirth()).isNull();
            assertThat(player.nationality()).isNull();
        });
    }

    @Test
    void updateFromConservaElNombreSiLaFuenteNoLoInforma() {
        var player = chiesaAt(liverpool());
        var snapshot = new PlayerSnapshot("1780", null, Position.MIDFIELDER, LocalDate.of(1997, 10, 25), "Italy");

        var updated = player.updateFrom(snapshot, liverpool());

        assertThat(updated.name()).isEqualTo("Federico Chiesa");
        assertThat(updated.position()).isEqualTo(Position.MIDFIELDER);
    }

    @Test
    void updateFromConservaLaPosicionSiLaFuenteNoLaInforma() {
        var player = new Player(5L, "1780", "F. Chiesa", Position.FORWARD, liverpool(), null, null, true);
        var snapshot = new PlayerSnapshot("1780", "Federico Chiesa", null, LocalDate.of(1997, 10, 25), "Italy");

        var updated = player.updateFrom(snapshot, liverpool());

        assertThat(updated.position()).isEqualTo(Position.FORWARD);
        assertThat(updated.name()).isEqualTo("Federico Chiesa");
    }

    @Test
    void updateFromCambiaDeEquipoYConElDeLigaSinPerderLaIdentidad() {
        var player = chiesaAt(juventus());

        var updated = player.updateFrom(chiesaSnapshot(), liverpool());

        assertThat(updated.team()).isEqualTo(liverpool());
        assertThat(updated.league()).isEqualTo(League.PREMIER);
        assertThat(updated.id()).isEqualTo(player.id());
        assertThat(updated.externalId()).isEqualTo(player.externalId());
    }

    @Test
    void updateFromTomaLaFechaDeNacimientoYLaNacionalidadQueInformaLaFuente() {
        var player = new Player(5L, "1780", "Federico Chiesa", Position.FORWARD, liverpool());

        var updated = player.updateFrom(chiesaSnapshot(), liverpool());

        assertThat(updated.dateOfBirth()).isEqualTo(LocalDate.of(1997, 10, 25));
        assertThat(updated.nationality()).isEqualTo("Italy");
    }

    @Test
    void updateFromTomaLaFechaDeNacimientoYLaNacionalidadAunqueVenganVacias() {
        var player = chiesaAt(liverpool());
        var snapshot = new PlayerSnapshot("1780", "Federico Chiesa", Position.FORWARD, null, "  ");

        var updated = player.updateFrom(snapshot, liverpool());

        assertThat(updated.dateOfBirth()).isNull();
        assertThat(updated.nationality()).isNull();
    }

    @Test
    void updateFromReactivaAUnJugadorInactivo() {
        var inactive = new Player(5L, "1780", "Federico Chiesa", Position.FORWARD, juventus(),
                LocalDate.of(1997, 10, 25), "Italy", false);

        var updated = inactive.updateFrom(chiesaSnapshot(), liverpool());

        assertThat(updated.active()).isTrue();
    }

    @Test
    void deactivateDejaAlJugadorInactivoConSuUltimoEquipo() {
        var player = chiesaAt(liverpool());

        var inactive = player.deactivate();

        assertThat(inactive.active()).isFalse();
        assertThat(inactive.team()).isEqualTo(liverpool());
        assertThat(inactive).usingRecursiveComparison().ignoringFields("active").isEqualTo(player);
    }

    private static Team juventus() {
        return new Team(2L, "109", "Juventus FC", null, League.SERIE_A);
    }

    private static Team liverpool() {
        return new Team(3L, "64", "Liverpool FC", "https://crests.football-data.org/64.png", League.PREMIER);
    }

    private static Player chiesaAt(Team team) {
        return new Player(5L, "1780", "Federico Chiesa", Position.FORWARD, team, LocalDate.of(1997, 10, 25), "Italy", true);
    }

    private static PlayerSnapshot chiesaSnapshot() {
        return new PlayerSnapshot("1780", "Federico Chiesa", Position.FORWARD, LocalDate.of(1997, 10, 25), "Italy");
    }
}
