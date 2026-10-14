package ar.edu.unq.desapp.futbolmarket.modelo.sync;

import static ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures.DANILO_ARSENAL;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures.DANILO_LIVERPOOL;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures.EVERTON;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures.MAN_UTD;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures.NDIAYE;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures.OBRIEN;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures.OBRIEN_WITHOUT_BIRTH_DATE;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures.PICKFORD;
import static ar.edu.unq.desapp.futbolmarket.modelo.sync.StatsFixtures.players;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import ar.edu.unq.desapp.futbolmarket.modelo.player.Player;

class CatalogPlayersTest {

    private static final String NDIAYE_WHOSCORED_ID = "393485";

    @Test
    void buscaPorIdDeWhoScored() {
        CatalogPlayers catalog = CatalogPlayers.of(players());

        Optional<Player> found = catalog.byWhoscoredId("93145");

        assertThat(found).contains(PICKFORD);
    }

    @Test
    void buscaPorIdDeFootballData() {
        CatalogPlayers catalog = CatalogPlayers.of(players());

        Optional<Player> found = catalog.byExternalId(NDIAYE.externalId());

        assertThat(found).contains(NDIAYE);
    }

    @Test
    void buscaPorEquipoYNombreNormalizado() {
        CatalogPlayers catalog = CatalogPlayers.of(players());

        List<Player> inEverton = catalog.inTeam(EVERTON, NormalizedName.ofPlayer("JAKE O BRIEN"));
        List<Player> inManUtd = catalog.inTeam(MAN_UTD, NormalizedName.ofPlayer("Jake O'Brien"));

        assertThat(inEverton).containsExactlyInAnyOrder(OBRIEN, OBRIEN_WITHOUT_BIRTH_DATE);
        assertThat(inManUtd).isEmpty();
    }

    @Test
    void buscaPorNombreNormalizadoEnTodoElCatalogo() {
        CatalogPlayers catalog = CatalogPlayers.of(players());

        List<Player> named = catalog.named(NormalizedName.ofPlayer("Danilo"));

        assertThat(named).containsExactlyInAnyOrder(DANILO_ARSENAL, DANILO_LIVERPOOL);
    }

    @Test
    void incluyeALosJugadoresInactivos() {
        Player inactive = NDIAYE.deactivate();
        CatalogPlayers catalog = CatalogPlayers.of(Stream.concat(Stream.of(inactive),
                players().stream().filter(player -> !player.equals(NDIAYE))).toList());

        Optional<Player> byId = catalog.byExternalId(NDIAYE.externalId());
        List<Player> named = catalog.named(NormalizedName.ofPlayer(NDIAYE.name()));

        assertThat(byId).contains(inactive);
        assertThat(named).containsExactly(inactive);
    }

    @Test
    void confirmActualizaLasBusquedasConLosCrucesDeUnPartidoGuardado() {
        CatalogPlayers catalog = CatalogPlayers.of(players());
        Player crossed = NDIAYE.crossWith(NDIAYE_WHOSCORED_ID);

        catalog.confirm(List.of(crossed));

        assertThat(catalog.byWhoscoredId(NDIAYE_WHOSCORED_ID)).contains(crossed);
        assertThat(catalog.byExternalId(NDIAYE.externalId())).contains(crossed);
        assertThat(catalog.inTeam(EVERTON, NormalizedName.ofPlayer(NDIAYE.name()))).containsExactly(crossed);
    }

    @Test
    void unIdDeWhoScoredDesconocidoNoEncuentraANadie() {
        CatalogPlayers catalog = CatalogPlayers.of(players());

        Optional<Player> found = catalog.byWhoscoredId(NDIAYE_WHOSCORED_ID);

        assertThat(found).isEmpty();
    }
}
