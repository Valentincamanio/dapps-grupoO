package ar.edu.unq.desapp.futbolmarket.catalog.service;

import ar.edu.unq.desapp.futbolmarket.catalog.modelo.League;
import ar.edu.unq.desapp.futbolmarket.catalog.modelo.Player;
import ar.edu.unq.desapp.futbolmarket.catalog.modelo.PlayerFilter;
import ar.edu.unq.desapp.futbolmarket.catalog.modelo.PlayerPage;
import ar.edu.unq.desapp.futbolmarket.catalog.modelo.Position;
import ar.edu.unq.desapp.futbolmarket.catalog.modelo.Team;
import ar.edu.unq.desapp.futbolmarket.catalog.persistence.repository.PlayerRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class PlayerCatalogServiceTest {
    @Mock
    private PlayerRepository playerRepository;

    @InjectMocks
    private PlayerCatalogService playerCatalogService;

    @Test
    void delegaLaConsultaPaginadaEnElRepositorio() {
        var expectedPage = new PlayerPage(
                List.of(new Player(1L, "premier-01", "Bukayo Saka", Position.FORWARD, new Team(1L, "Arsenal", League.PREMIER))),
                0,
                10,
                1
        );
        given(playerRepository.findPage(0, 10)).willReturn(expectedPage);

        var result = playerCatalogService.getPlayers(0, 10);

        assertThat(result).isSameAs(expectedPage);
        then(playerRepository).should().findPage(0, 10);
    }

    @Test
    void devuelvePaginaVaciaCuandoElRepositorioResuelveUnaPaginaFueraDeRango() {
        var emptyPage = new PlayerPage(List.of(), 8, 10, 3);
        given(playerRepository.findPage(8, 10)).willReturn(emptyPage);

        var result = playerCatalogService.getPlayers(8, 10);

        assertThat(result.content()).isEmpty();
        assertThat(result.page()).isEqualTo(8);
        assertThat(result.totalPages()).isEqualTo(1);
        then(playerRepository).should().findPage(8, 10);
    }

    @Test
    void delegaElCriterioDeFiltrosEnElRepositorio() {
        var filter = new PlayerFilter(League.PREMIER, "Arsenal", Position.FORWARD);
        var expectedPage = new PlayerPage(List.of(), 0, 10, 0);
        given(playerRepository.findPage(0, 10, filter)).willReturn(expectedPage);

        var result = playerCatalogService.getPlayers(0, 10, filter);

        assertThat(result).isSameAs(expectedPage);
        then(playerRepository).should().findPage(0, 10, filter);
    }
}
