package ar.edu.unq.desapp.futbolmarket.catalog.controller;

import ar.edu.unq.desapp.futbolmarket.catalog.modelo.League;
import ar.edu.unq.desapp.futbolmarket.catalog.modelo.Player;
import ar.edu.unq.desapp.futbolmarket.catalog.modelo.PlayerPage;
import ar.edu.unq.desapp.futbolmarket.catalog.modelo.Position;
import ar.edu.unq.desapp.futbolmarket.catalog.modelo.Team;
import ar.edu.unq.desapp.futbolmarket.catalog.service.PlayerCatalogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class PlayerControllerIT {
    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @MockitoBean
    private PlayerCatalogService playerCatalogService;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    @Test
    void devuelveLaRespuestaPaginadaYLosMetadatosDeNavegacion() throws Exception {
        var player = new Player(7L, "premier-07", "Bukayo Saka", Position.FORWARD, new Team(2L, "Arsenal", League.PREMIER));
        given(playerCatalogService.getPlayers(1, 1)).willReturn(new PlayerPage(List.of(player), 1, 1, 2));

        mockMvc.perform(get("/players?page=1&size=1").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(7))
                .andExpect(jsonPath("$.content[0].name").value("Bukayo Saka"))
                .andExpect(jsonPath("$.content[0].position").value("FORWARD"))
                .andExpect(jsonPath("$.content[0].team").value("Arsenal"))
                .andExpect(jsonPath("$.content[0].league").value("PREMIER"))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.hasPrevious").value(true))
                .andExpect(jsonPath("$.hasNext").value(false));
        verify(playerCatalogService).getPlayers(1, 1);
    }

    @Test
    void usaLosValoresDePaginacionPredeterminados() throws Exception {
        given(playerCatalogService.getPlayers(0, 10)).willReturn(new PlayerPage(List.of(), 0, 10, 0));

        mockMvc.perform(get("/players").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(10));
        verify(playerCatalogService).getPlayers(0, 10);
    }

    @Test
    void rechazaPaginacionInvalidaConApiError() throws Exception {
        mockMvc.perform(get("/players?page=-1&size=0").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Solicitud inválida"))
                .andExpect(jsonPath("$.message").value("Los parámetros enviados no son válidos."))
                .andExpect(jsonPath("$.path").value("/players"));
    }
}
