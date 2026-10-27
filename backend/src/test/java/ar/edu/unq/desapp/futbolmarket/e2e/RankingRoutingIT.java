package ar.edu.unq.desapp.futbolmarket.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import ar.edu.unq.desapp.futbolmarket.modelo.player.Player;
import ar.edu.unq.desapp.futbolmarket.modelo.position.Position;
import ar.edu.unq.desapp.futbolmarket.service.quote.QuoteCacheInvalidator;
import ar.edu.unq.desapp.futbolmarket.service.quote.QuoteDatabase;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Fija el routing de las rutas literales nuevas bajo {@code /players} (research D20 y D22):
 * {@code /players/ranking} y {@code /players/quotes/current} llegan a sus métodos y no al detalle
 * {@code /players/{id}}, que sigue respondiendo como antes. Todo sin credencial.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:ranking-routing-e2e;DB_CLOSE_DELAY=-1")
class RankingRoutingIT {
    private static final long MISSING_ID = 999_999L;

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private ApplicationContext context;

    @Autowired
    private QuoteCacheInvalidator cacheInvalidator;

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    private QuoteDatabase database;
    private Player ndiaye;

    @BeforeEach
    void setUp() {
        database = new QuoteDatabase(context);
        database.clean();
        cacheInvalidator.invalidateAll();
        ndiaye = database.player("8135", "Iliman Ndiaye", Position.FORWARD, database.team("62", "Everton FC"));
    }

    @AfterEach
    void cleanDatabase() {
        database.clean();
    }

    /**
     * Si llegara a {@code /players/{id}} con {@code id = "ranking"}, respondería 400 por el id no
     * numérico. El ranking vacío trae la estrategia activa, que el detalle de un jugador no tiene.
     */
    @Test
    void elRankingLlegaAlRankingYNoAlDetalleDeUnJugadorSinCredencial() {
        MvcTestResult result = mvc.get().uri("/players/ranking").exchange();

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.OK.value());
        JsonNode ranking = body(result);
        assertThat(ranking.has("activeStrategy")).isTrue();
        assertThat(ranking.get("content").isArray()).isTrue();
        assertThat(ranking.has("name")).isFalse();
    }

    @Test
    void elLoteDeCotizacionesActualesLlegaASuMetodoSinCredencial() {
        MvcTestResult result = mvc.get().uri("/players/quotes/current?ids=" + ndiaye.id()).exchange();

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.OK.value());
        assertThat(body(result).get("quotes").isArray()).isTrue();
    }

    @Test
    void elDetalleDeUnJugadorSigueRespondiendoIgual() {
        MvcTestResult existing = mvc.get().uri("/players/" + ndiaye.id()).exchange();
        MvcTestResult missing = mvc.get().uri("/players/" + MISSING_ID).exchange();
        MvcTestResult notNumeric = mvc.get().uri("/players/abc").exchange();

        assertThat(existing.getResponse().getStatus()).isEqualTo(HttpStatus.OK.value());
        assertThat(body(existing).get("id").asLong()).isEqualTo(ndiaye.id());
        assertThat(body(existing).get("name").asString()).isEqualTo("Iliman Ndiaye");
        assertThat(missing.getResponse().getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(body(missing).get("message").asString())
                .isEqualTo("No se encontró el jugador con id " + MISSING_ID + ".");
        assertThat(notNumeric.getResponse().getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(body(notNumeric).get("message").asString()).isEqualTo("Los parámetros enviados no son válidos.");
    }

    private JsonNode body(MvcTestResult result) {
        try {
            return jsonMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }
}
