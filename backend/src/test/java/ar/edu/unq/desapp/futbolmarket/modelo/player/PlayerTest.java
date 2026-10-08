package ar.edu.unq.desapp.futbolmarket.modelo.player;

import org.junit.jupiter.api.Test;

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
}
