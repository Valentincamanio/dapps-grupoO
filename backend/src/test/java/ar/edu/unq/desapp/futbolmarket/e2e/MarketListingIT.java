package ar.edu.unq.desapp.futbolmarket.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.stream.StreamSupport;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import ar.edu.unq.desapp.futbolmarket.modelo.auth.FakePasswordHasher;
import ar.edu.unq.desapp.futbolmarket.modelo.league.League;
import ar.edu.unq.desapp.futbolmarket.modelo.market.CancelReason;
import ar.edu.unq.desapp.futbolmarket.modelo.market.MarketOrder;
import ar.edu.unq.desapp.futbolmarket.modelo.player.Player;
import ar.edu.unq.desapp.futbolmarket.modelo.position.Position;
import ar.edu.unq.desapp.futbolmarket.modelo.team.Team;
import ar.edu.unq.desapp.futbolmarket.modelo.user.AppUser;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.market.MarketOrderRepository;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.team.TeamRepository;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.user.AppUserRepository;
import ar.edu.unq.desapp.futbolmarket.service.market.MarketDatabase;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * El listado público del mercado (US5) de punta a punta por HTTP, contra una H2 propia. Las órdenes
 * se guardan directo con el repository, en los cuatro estados, de dos ligas y de los dos tipos de
 * usuario dueño. Las iniciales de Kane y Saka tienen el mismo instante, así que las desempata el id.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties =
        "spring.datasource.url=jdbc:h2:mem:market-listing-e2e;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000")
class MarketListingIT {

    private static final String ORDERS_PATH = "/orders";
    private static final String INVALID_PARAMETERS = "Los parámetros enviados no son válidos.";
    private static final Instant FIRST = Instant.parse("2026-10-28T12:00:00Z");
    private static final List<String> ORDER_FIELDS = List.of("id", "player", "side", "ownerType", "status",
            "requestedQuantity", "executedQuantity", "pendingQuantity", "createdAt");
    private static final List<String> PLAYER_FIELDS = List.of("id", "name", "team", "league");

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private ApplicationContext context;

    @Autowired
    private MarketOrderRepository orderRepository;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private TeamRepository teamRepository;

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    private MarketDatabase database;
    private AppUser superuser;
    private AppUser buyer;
    private AppUser seller;
    private Player kane;

    @BeforeEach
    void setUp() {
        database = new MarketDatabase(context);
        database.clean();
        superuser = userRepository.findSuperuserId().map(database::account).orElseGet(() -> userRepository.save(
                AppUser.createAdmin("administrador", "administrador@correo.com", "Admin_mercado_1",
                        new FakePasswordHasher())));
        buyer = database.user("1000.00");
        seller = database.user("1000.00");
        Team bayern = teamRepository.save(new Team(null, "5", "FC Bayern München", null, League.BUNDESLIGA, "37"));
        Team arsenal = database.quoteDatabase().team("57", "Arsenal FC");
        Team liverpool = database.quoteDatabase().team("64", "Liverpool FC");
        kane = database.quoteDatabase().player("8300", "Harry Kane", Position.FORWARD, bayern);
        Player musiala = database.quoteDatabase().player("8301", "Jamal Musiala", Position.MIDFIELDER, bayern);
        Player saka = database.quoteDatabase().player("8302", "Bukayo Saka", Position.FORWARD, arsenal);
        Player salah = database.quoteDatabase().player("8303", "Mohamed Salah", Position.FORWARD, liverpool);
        seedOrders(musiala, saka, salah);
    }

    @AfterEach
    void cleanDatabase() {
        database.clean();
    }

    @Test
    void elListadoEsPublicoYTraeSoloLasAbiertasDeLaMasViejaALaMasNueva() throws Exception {
        MvcTestResult result = mvc.get().uri(ORDERS_PATH).exchange();

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.OK.value());
        assertThat(playerNames(json(result))).containsExactly("Harry Kane", "Bukayo Saka", "Mohamed Salah",
                "Jamal Musiala");
        assertThat(json(result).get("content")).allSatisfy(order ->
                assertThat(order.get("status").asString()).isIn("PENDING", "PARTIAL"));
    }

    @Test
    void unaCredencialInvalidaNoImpideVerElListado() throws Exception {
        MvcTestResult result = mvc.get().uri(ORDERS_PATH).header("Authorization", "Bearer token-invalido").exchange();

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.OK.value());
        assertThat(json(result).get("content")).hasSize(4);
    }

    @Test
    void cadaOrdenTraeLosCamposDelListadoYNingunDatoDelDueno() throws Exception {
        MvcTestResult result = mvc.get().uri(ORDERS_PATH).exchange();

        String raw = result.getResponse().getContentAsString();
        JsonNode first = json(result).get("content").get(0);
        assertThat(first.propertyNames()).containsExactlyInAnyOrderElementsOf(ORDER_FIELDS);
        assertThat(first.get("player").propertyNames()).containsExactlyInAnyOrderElementsOf(PLAYER_FIELDS);
        assertThat(first.get("player").get("id").asLong()).isEqualTo(kane.id());
        assertThat(first.get("player").get("name").asString()).isEqualTo("Harry Kane");
        assertThat(first.get("player").get("team").asString()).isEqualTo("FC Bayern München");
        assertThat(first.get("player").get("league").asString()).isEqualTo("BUNDESLIGA");
        assertThat(first.get("side").asString()).isEqualTo("SELL");
        assertThat(first.get("ownerType").asString()).isEqualTo("ADMIN");
        assertThat(first.get("status").asString()).isEqualTo("PARTIAL");
        assertThat(first.get("requestedQuantity").asInt()).isEqualTo(100);
        assertThat(first.get("executedQuantity").asInt()).isEqualTo(13);
        assertThat(first.get("pendingQuantity").asInt()).isEqualTo(87);
        assertThat(first.get("createdAt").asString()).isEqualTo("2026-10-28T12:00:00Z");
        assertThat(raw).doesNotContain("\"owner\"", "\"ownerId\"", "\"userId\"", "\"username\"", "\"email\"",
                buyer.getUsername(), seller.getUsername(), superuser.getUsername(), buyer.getEmail(),
                seller.getEmail());
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = ';', value = {
            "side=BUY;Jamal Musiala",
            "side=SELL;Harry Kane|Bukayo Saka|Mohamed Salah",
            "league=PREMIER;Bukayo Saka|Mohamed Salah",
            "team=Arsenal FC;Bukayo Saka",
            "ownerType=USER;Mohamed Salah|Jamal Musiala",
            "side=SELL&ownerType=USER;Mohamed Salah",
            "league=BUNDESLIGA&side=BUY;Jamal Musiala",
            "league=PREMIER&side=BUY&team=Liverpool FC;",
            "status=EXECUTED;Harry Kane|Bukayo Saka|Mohamed Salah|Jamal Musiala"})
    void losFiltrosSeAplicanYSeCombinanYElEstadoSeIgnora(String query, String expected) throws Exception {
        MvcTestResult result = mvc.get().uri(ORDERS_PATH + "?" + query).exchange();

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.OK.value());
        List<String> names = expected == null ? List.of() : List.of(expected.split("\\|"));
        assertThat(playerNames(json(result))).containsExactlyElementsOf(names);
    }

    @Test
    void conElTipoDeUsuarioDuenoAdministradorSeVenLasInicialesConLoQueLesQueda() throws Exception {
        MvcTestResult result = mvc.get().uri(ORDERS_PATH + "?ownerType=ADMIN").exchange();

        JsonNode content = json(result).get("content");
        assertThat(playerNames(json(result))).containsExactly("Harry Kane", "Bukayo Saka");
        assertThat(content).allSatisfy(order -> assertThat(order.get("ownerType").asString()).isEqualTo("ADMIN"));
        assertThat(content.get(0).get("pendingQuantity").asInt()).isEqualTo(87);
        assertThat(content.get(1).get("pendingQuantity").asInt()).isEqualTo(100);
    }

    @Test
    void conUnJugadorTraeSoloSusOrdenesPendientes() throws Exception {
        MvcTestResult result = mvc.get().uri(ORDERS_PATH + "?playerId=" + kane.id()).exchange();

        assertThat(playerNames(json(result))).containsExactly("Harry Kane");
    }

    @ParameterizedTest
    @ValueSource(strings = {"side=X", "ownerType=X", "league=X", "playerId=0", "playerId=abc", "team=", "team= ",
            "page=-1", "size=0", "size=51"})
    void unValorNoAdmitidoResponde400ComoElCatalogo(String query) throws Exception {
        MvcTestResult result = mvc.get().uri(ORDERS_PATH + "?" + query).exchange();

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(json(result).get("message").asString()).isEqualTo(INVALID_PARAMETERS);
    }

    @Test
    void elListadoSeRecorrePorHojasConLaPaginacionDelCatalogo() throws Exception {
        MvcTestResult result = mvc.get().uri(ORDERS_PATH + "?page=1&size=3").exchange();

        JsonNode page = json(result);
        assertThat(playerNames(page)).containsExactly("Jamal Musiala");
        assertThat(page.get("page").asInt()).isEqualTo(1);
        assertThat(page.get("size").asInt()).isEqualTo(3);
        assertThat(page.get("totalElements").asLong()).isEqualTo(4);
        assertThat(page.get("totalPages").asInt()).isEqualTo(2);
        assertThat(page.get("hasPrevious").asBoolean()).isTrue();
        assertThat(page.get("hasNext").asBoolean()).isFalse();
    }

    @Test
    void sinPaginacionLaHojaEsLaPrimeraDeDiez() throws Exception {
        MvcTestResult result = mvc.get().uri(ORDERS_PATH).exchange();

        JsonNode page = json(result);
        assertThat(page.get("page").asInt()).isZero();
        assertThat(page.get("size").asInt()).isEqualTo(10);
    }

    /**
     * Abiertas: la inicial de Kane con 87 tokens, la de Saka intacta, una venta de Salah y una compra
     * parcial de Musiala. Fuera del listado: la inicial agotada de Musiala, una venta ejecutada y una
     * cancelada.
     */
    private void seedOrders(Player musiala, Player saka, Player salah) {
        MarketOrder kaneInitial = MarketOrder.initialFor(superuser.getId(), kane.id(), FIRST);
        kaneInitial.fill(13);
        orderRepository.save(kaneInitial);
        MarketOrder musialaInitial = MarketOrder.initialFor(superuser.getId(), musiala.id(), FIRST);
        musialaInitial.fill(100);
        orderRepository.save(musialaInitial);
        orderRepository.save(MarketOrder.initialFor(superuser.getId(), saka.id(), FIRST));
        orderRepository.save(MarketOrder.sell(seller.getId(), salah.id(), 5, FIRST.plusSeconds(60)));
        MarketOrder purchase = MarketOrder.buy(buyer.getId(), musiala.id(), 3, new BigDecimal("3.09"),
                new BigDecimal("1.0300"), FIRST.plusSeconds(120));
        purchase.fill(1);
        orderRepository.save(purchase);
        MarketOrder executed = MarketOrder.sell(seller.getId(), salah.id(), 2, FIRST.plusSeconds(30));
        executed.fill(2);
        orderRepository.save(executed);
        MarketOrder cancelled = MarketOrder.sell(seller.getId(), saka.id(), 2, FIRST.plusSeconds(30));
        cancelled.cancelRemainder(CancelReason.USER_REQUEST);
        orderRepository.save(cancelled);
    }

    private JsonNode json(MvcTestResult result) throws Exception {
        return jsonMapper.readTree(result.getResponse().getContentAsString());
    }

    private static List<String> playerNames(JsonNode page) {
        return StreamSupport.stream(page.get("content").spliterator(), false)
                .map(order -> order.get("player").get("name").asString())
                .toList();
    }
}
