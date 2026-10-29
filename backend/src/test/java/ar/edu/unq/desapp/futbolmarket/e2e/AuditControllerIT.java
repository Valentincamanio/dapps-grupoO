package ar.edu.unq.desapp.futbolmarket.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import ar.edu.unq.desapp.futbolmarket.e2e.AuthTestHelper.TestUser;
import ar.edu.unq.desapp.futbolmarket.modelo.market.MarketOrder;
import ar.edu.unq.desapp.futbolmarket.modelo.player.Player;
import ar.edu.unq.desapp.futbolmarket.modelo.position.Position;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.Quote;
import ar.edu.unq.desapp.futbolmarket.modelo.team.Team;
import ar.edu.unq.desapp.futbolmarket.modelo.user.AppUser;
import ar.edu.unq.desapp.futbolmarket.service.market.InitialOrderService;
import ar.edu.unq.desapp.futbolmarket.service.market.MarketDatabase;
import ar.edu.unq.desapp.futbolmarket.service.market.MarketOrderService;
import ar.edu.unq.desapp.futbolmarket.service.quote.MutableClock;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * La consulta de la auditoría de punta a punta por HTTP (US9), contra una H2 propia, con el
 * administrador configurado como superusuario. Kane y Musiala tienen su orden inicial; Kane cotiza
 * 103,00 desde el lunes 26/10.
 *
 * <p>Los hechos se arman con el servicio, con el reloj avanzando un día entre pasos, así cada
 * cuenta tiene el saldo exacto que el escenario necesita: el 28/10 Ana compra los 100 tokens de Kane
 * a la orden inicial; el 29/10 Bruno deja una compra de 10 y la cancela; el 30/10 Carla, con 10,30
 * créditos, deja una compra de 10, Kane pasa a cotizar 110,00 y Ana le vende 10: se ejecutan 9 y el
 * sistema cancela el que no alcanza.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuditControllerIT {

    private static final String SUFFIX = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    private static final String ADMIN_USERNAME = "admin_" + SUFFIX;
    private static final String ADMIN_EMAIL = "admin_" + SUFFIX + "@correo.com";
    private static final String ADMIN_PASSWORD = "Admin_" + UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-10-28T15:00:00Z");
    private static final Instant QUOTE_FROM = Instant.parse("2026-10-26T03:00:00Z");

    private static final String AUDIT_PATH = "/audit";
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String INVALID_PARAMETERS = "Los parámetros enviados no son válidos.";
    private static final String FORBIDDEN_MESSAGE = "No tiene permisos para acceder a este recurso.";
    private static final String MISSING_CREDENTIAL_MESSAGE =
            "Se requiere una credencial: un token de sesión (Authorization: Bearer) o una clave de API (X-API-Key).";
    private static final int ALL_ENTRIES = 10;

    @DynamicPropertySource
    static void configuration(DynamicPropertyRegistry registry) {
        registry.add("futbolmarket.auth.admin.username", () -> ADMIN_USERNAME);
        registry.add("futbolmarket.auth.admin.email", () -> ADMIN_EMAIL);
        registry.add("futbolmarket.auth.admin.password", () -> ADMIN_PASSWORD);
        registry.add("spring.datasource.url",
                () -> "jdbc:h2:mem:audit-controller-e2e-" + SUFFIX + ";DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000");
    }

    @TestConfiguration
    static class FixedClockConfiguration {

        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock(NOW);
        }
    }

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private ApplicationContext context;

    @Autowired
    private InitialOrderService initialOrderService;

    @Autowired
    private MarketOrderService marketOrderService;

    @Autowired
    private MutableClock clock;

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    private AuthTestHelper helper;
    private MarketDatabase database;
    private Player kane;
    private Player musiala;
    private Quote kaneQuote;

    @BeforeEach
    void setUp() {
        clock.set(NOW.minus(Duration.ofHours(5)));
        helper = new AuthTestHelper(mvc);
        database = new MarketDatabase(context);
        database.clean();
        Team bayern = database.quoteDatabase().team("5", "FC Bayern München");
        kane = database.quoteDatabase().player("8300", "Harry Kane", Position.FORWARD, bayern);
        musiala = database.quoteDatabase().player("8301", "Jamal Musiala", Position.MIDFIELDER, bayern);
        kaneQuote = database.quote(kane, "103.00", "0.300000", QUOTE_FROM);
        database.quote(musiala, "100.00", "0.000000", QUOTE_FROM);
        initialOrderService.openMissing();
        clock.set(NOW);
    }

    @AfterEach
    void verifyAndClean() {
        clock.set(NOW);
        database.assertInvariants();
        database.clean();
    }

    @Test
    void elAdministradorRecibeLaAuditoriaDeLaMasNuevaALaMasVieja() throws Exception {
        scenario();

        MvcTestResult result = audit(adminBearer(), "?size=50");

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.OK.value());
        JsonNode page = json(result);
        List<JsonNode> content = page.get("content").values().stream().toList();
        assertThat(page.get("totalElements").asLong()).isEqualTo(ALL_ENTRIES);
        assertThat(content).hasSize(ALL_ENTRIES);
        for (int i = 1; i < content.size(); i++) {
            Instant newer = Instant.parse(content.get(i - 1).get("occurredAt").asString());
            Instant older = Instant.parse(content.get(i).get("occurredAt").asString());
            assertThat(newer).isAfterOrEqualTo(older);
            if (newer.equals(older)) {
                assertThat(content.get(i - 1).get("id").asLong()).isGreaterThan(content.get(i).get("id").asLong());
            }
        }
        assertThat(content.getFirst().get("occurredAt").asString()).isEqualTo("2026-10-30T16:01:00Z");
        assertThat(content.getLast().get("occurredAt").asString()).isEqualTo("2026-10-28T10:00:00Z");
    }

    @Test
    void unUsuarioComunRecibe403YSinCredencial401() throws Exception {
        TestUser user = helper.registerUser();

        MvcTestResult byUser = audit(bearer(user), "");
        MvcTestResult withApiKey = mvc.get().uri(AUDIT_PATH).header("X-API-Key", user.apiKey()).exchange();
        MvcTestResult anonymous = mvc.get().uri(AUDIT_PATH).exchange();
        MvcTestResult invalid = audit(BEARER_PREFIX + "no-es-un-token", "");

        assertThat(byUser.getResponse().getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
        assertThat(json(byUser).get("message").asString()).isEqualTo(FORBIDDEN_MESSAGE);
        assertThat(withApiKey.getResponse().getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
        assertThat(anonymous.getResponse().getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(json(anonymous).get("message").asString()).isEqualTo(MISSING_CREDENTIAL_MESSAGE);
        assertThat(invalid.getResponse().getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
    }

    @Test
    void lasOrdenesInicialesTienenAutorSuperuserUS91() throws Exception {
        long superuserId = database.superuser().getId();

        JsonNode page = json(audit(adminBearer(), "?eventType=ORDER_CREATED&userId=" + superuserId));

        List<JsonNode> content = page.get("content").values().stream().toList();
        assertThat(content).hasSize(2);
        assertThat(content).extracting(entry -> entry.get("playerId").asLong())
                .containsExactlyInAnyOrder(kane.id(), musiala.id());
        JsonNode kaneInitial = content.stream()
                .filter(entry -> entry.get("playerId").asLong() == kane.id())
                .findFirst().orElseThrow();
        assertThat(kaneInitial.get("actorType").asString()).isEqualTo("SUPERUSER");
        assertThat(kaneInitial.get("actorId").asLong()).isEqualTo(superuserId);
        assertThat(kaneInitial.get("orderId").asLong()).isEqualTo(database.initialOrderOf(kane).getId());
        assertThat(kaneInitial.get("side").asString()).isEqualTo("SELL");
        assertThat(kaneInitial.get("quantity").asInt()).isEqualTo(100);
        assertThat(kaneInitial.get("tradeId").isNull()).isTrue();
        assertThat(kaneInitial.get("cancelReason").isNull()).isTrue();
        assertThat(kaneInitial.get("detail").asString())
                .isEqualTo("Orden inicial de venta de 100 tokens de Harry Kane a 1,0000");
        assertThat(kaneInitial.get("parties").values()).singleElement().satisfies(owner -> {
            assertThat(owner.get("role").asString()).isEqualTo("OWNER");
            assertThat(owner.get("userId").asLong()).isEqualTo(superuserId);
            assertThat(owner.get("before").get("tokens").asInt()).isZero();
            assertThat(owner.get("after").get("tokens").asInt()).isEqualTo(100);
        });
    }

    @Test
    void unaOperacionInformaSusDosPartesAntesYDespuesUS92() throws Exception {
        Scenario scenario = scenario();
        long superuserId = database.superuser().getId();

        JsonNode page = json(audit(adminBearer(), "?eventType=TRADE_EXECUTED&userId=" + superuserId));

        JsonNode trade = page.get("content").values().stream().toList().getFirst();
        assertThat(page.get("totalElements").asLong()).isEqualTo(1);
        assertThat(trade.get("actorType").asString()).isEqualTo("USER");
        assertThat(trade.get("actorId").asLong()).isEqualTo(scenario.ana().getId());
        assertThat(trade.get("playerId").asLong()).isEqualTo(kane.id());
        assertThat(trade.get("orderId").asLong()).isEqualTo(scenario.anaPurchase());
        assertThat(trade.get("tradeId").asLong()).isPositive();
        assertThat(trade.get("side").asString()).isEqualTo("BUY");
        assertThat(trade.get("quantity").asInt()).isEqualTo(100);
        assertThat(trade.get("tokenPrice").decimalValue()).isEqualByComparingTo("1.0000");
        assertThat(trade.get("total").decimalValue()).isEqualByComparingTo("100.00");
        assertThat(trade.get("quoteId").asLong()).isEqualTo(kaneQuote.id());
        assertThat(trade.get("detail").asString())
                .isEqualTo("Compra de 100 tokens de Harry Kane a 1,0000 por 100,00 contra la orden inicial");
        List<JsonNode> parties = trade.get("parties").values().stream().toList();
        assertThat(parties).extracting(party -> party.get("role").asString()).containsExactly("BUYER", "SELLER");
        JsonNode buyer = parties.get(0);
        assertThat(buyer.get("userId").asLong()).isEqualTo(scenario.ana().getId());
        assertState(buyer.get("before"), "900.00", "100.00", 0);
        assertState(buyer.get("after"), "900.00", "0.00", 100);
        JsonNode seller = parties.get(1);
        assertThat(seller.get("userId").asLong()).isEqualTo(superuserId);
        assertThat(seller.get("after").get("availableBalance").decimalValue()
                .subtract(seller.get("before").get("availableBalance").decimalValue()))
                .isEqualByComparingTo("100.00");
        assertThat(seller.get("before").get("reservedBalance").decimalValue()).isEqualByComparingTo("0.00");
        assertThat(seller.get("before").get("tokens").asInt()).isEqualTo(100);
        assertThat(seller.get("after").get("tokens").asInt()).isZero();
    }

    @Test
    void lasCancelacionesDelUsuarioYDelSistemaTienenSuMotivoYSuAutorUS93() throws Exception {
        Scenario scenario = scenario();

        List<JsonNode> cancellations = json(audit(adminBearer(), "?eventType=ORDER_CANCELLED"))
                .get("content").values().stream().toList();

        assertThat(cancellations).hasSize(2);
        JsonNode bySystem = cancellations.get(0);
        assertThat(bySystem.get("actorType").asString()).isEqualTo("SYSTEM");
        assertThat(bySystem.get("actorId").isNull()).isTrue();
        assertThat(bySystem.get("cancelReason").asString()).isEqualTo("INSUFFICIENT_CREDITS");
        assertThat(bySystem.get("orderId").asLong()).isEqualTo(scenario.carlaPurchase());
        assertThat(bySystem.get("quantity").asInt()).isEqualTo(1);
        assertThat(bySystem.get("detail").asString())
                .isEqualTo("Cancelación de 1 token de la orden de compra de Harry Kane por saldo insuficiente");
        assertThat(bySystem.get("parties").values()).singleElement().satisfies(owner -> {
            assertThat(owner.get("userId").asLong()).isEqualTo(scenario.carla().getId());
            assertState(owner.get("before"), "0.00", "0.40", 9);
            assertState(owner.get("after"), "0.40", "0.00", 9);
        });
        JsonNode byUser = cancellations.get(1);
        assertThat(byUser.get("actorType").asString()).isEqualTo("USER");
        assertThat(byUser.get("actorId").asLong()).isEqualTo(scenario.bruno().getId());
        assertThat(byUser.get("cancelReason").asString()).isEqualTo("USER_REQUEST");
        assertThat(byUser.get("orderId").asLong()).isEqualTo(scenario.brunoPurchase());
        assertThat(byUser.get("quantity").asInt()).isEqualTo(10);
        assertThat(byUser.get("parties").values()).singleElement().satisfies(owner -> {
            assertState(owner.get("before"), "989.70", "10.30", 0);
            assertState(owner.get("after"), "1000.00", "0.00", 0);
        });
    }

    @Test
    void losFiltrosSeCombinanYElRangoIncluyeLosDosDias() throws Exception {
        Scenario scenario = scenario();
        long anaId = scenario.ana().getId();

        assertThat(count("?userId=" + anaId)).isEqualTo(4);
        assertThat(count("?userId=" + scenario.carla().getId())).isEqualTo(3);
        assertThat(count("?playerId=" + musiala.id())).isEqualTo(1);
        assertThat(count("?eventType=TRADE_EXECUTED")).isEqualTo(2);
        assertThat(count("?from=2026-10-29&to=2026-10-29")).isEqualTo(2);
        assertThat(count("?from=2026-10-30")).isEqualTo(4);
        assertThat(count("?to=2026-10-28")).isEqualTo(4);
        assertThat(count("?eventType=ORDER_CREATED&userId=" + anaId + "&playerId=" + kane.id() + "&from=2026-10-30"))
                .isEqualTo(1);
        assertThat(count("?playerId=999999")).isZero();
        JsonNode second = json(audit(adminBearer(), "?page=1&size=4"));
        assertThat(second.get("content").size()).isEqualTo(4);
        assertThat(second.get("totalPages").asInt()).isEqualTo(3);
        assertThat(second.get("hasPrevious").asBoolean()).isTrue();
        assertThat(second.get("hasNext").asBoolean()).isTrue();
    }

    @Test
    void cadaFiltroOValorNoAdmitidoEs400() throws Exception {
        String admin = adminBearer();

        assertBadRequest(audit(admin, "?eventType=ORDER_DELETED"), INVALID_PARAMETERS);
        assertBadRequest(audit(admin, "?userId=0"), INVALID_PARAMETERS);
        assertBadRequest(audit(admin, "?userId=abc"), INVALID_PARAMETERS);
        assertBadRequest(audit(admin, "?playerId=-1"), INVALID_PARAMETERS);
        assertBadRequest(audit(admin, "?page=-1"), INVALID_PARAMETERS);
        assertBadRequest(audit(admin, "?size=0"), INVALID_PARAMETERS);
        assertBadRequest(audit(admin, "?size=51"), INVALID_PARAMETERS);
        assertBadRequest(audit(admin, "?from=2026-02-30"),
                "La fecha '2026-02-30' no es válida: use el formato AAAA-MM-DD.");
        assertBadRequest(audit(admin, "?to=30/10/2026"),
                "La fecha '30/10/2026' no es válida: use el formato AAAA-MM-DD.");
        assertBadRequest(audit(admin, "?from=2026-09-10&to=2026-09-01"),
                "El inicio del rango (2026-09-10) es posterior al final (2026-09-01).");
    }

    @Test
    void ningunaEscrituraSobreLaAuditoriaModificaNadaUS95() throws Exception {
        scenario();
        String admin = adminBearer();
        String before = raw(audit(admin, "?size=50"));
        long entryId = json(audit(admin, "?size=1")).get("content").get(0).get("id").asLong();
        String entryPath = AUDIT_PATH + "/" + entryId;
        String body = "{\"detail\": \"modificado\", \"quantity\": 1}";

        List<MvcTestResult> writes = List.of(
                mvc.put().uri(AUDIT_PATH).header(AUTHORIZATION_HEADER, admin)
                        .contentType(MediaType.APPLICATION_JSON).content(body).exchange(),
                mvc.patch().uri(AUDIT_PATH).header(AUTHORIZATION_HEADER, admin)
                        .contentType(MediaType.APPLICATION_JSON).content(body).exchange(),
                mvc.delete().uri(AUDIT_PATH).header(AUTHORIZATION_HEADER, admin).exchange(),
                mvc.put().uri(entryPath).header(AUTHORIZATION_HEADER, admin)
                        .contentType(MediaType.APPLICATION_JSON).content(body).exchange(),
                mvc.patch().uri(entryPath).header(AUTHORIZATION_HEADER, admin)
                        .contentType(MediaType.APPLICATION_JSON).content(body).exchange(),
                mvc.delete().uri(entryPath).header(AUTHORIZATION_HEADER, admin).exchange());

        assertThat(writes.subList(0, 3)).allSatisfy(result ->
                assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED.value()));
        assertThat(writes.subList(3, 6)).allSatisfy(result ->
                assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value()));
        assertThat(raw(audit(admin, "?size=50"))).isEqualTo(before);
        assertThat(database.auditEntries()).hasSize(ALL_ENTRIES);
    }

    /**
     * El escenario de la clase, con el reloj en el día de cada paso. Deja diez registros: dos
     * órdenes iniciales, la compra de Ana (orden y operación), la compra cancelada de Bruno (orden y
     * cancelación), la compra de Carla y la venta de Ana (orden, operación y cancelación del sistema).
     */
    private Scenario scenario() {
        AppUser ana = database.user("1000.00");
        AppUser bruno = database.user("1000.00");
        AppUser carla = database.user("10.30");
        long anaPurchase = marketOrderService.buy(ana.getId(), kane.id(), 100).order().getId();
        onDay(29);
        long brunoPurchase = marketOrderService.buy(bruno.getId(), kane.id(), 10).order().getId();
        marketOrderService.cancel(bruno.getId(), brunoPurchase);
        onDay(30);
        long carlaPurchase = marketOrderService.buy(carla.getId(), kane.id(), 10).order().getId();
        Instant requoted = Instant.parse("2026-10-30T16:00:00Z");
        database.quote(kane, "110.00", "0.300000", requoted);
        clock.set(requoted.plus(Duration.ofMinutes(1)));
        marketOrderService.sell(ana.getId(), kane.id(), 10);
        MarketOrder cancelled = database.ordersOf(carla).getFirst();
        assertThat(cancelled.getCancelled()).isEqualTo(1);
        return new Scenario(ana, bruno, carla, anaPurchase, brunoPurchase, carlaPurchase);
    }

    private void onDay(int day) {
        clock.set(Instant.parse("2026-10-%02dT15:00:00Z".formatted(day)));
    }

    private long count(String query) throws Exception {
        MvcTestResult result = audit(adminBearer(), query);
        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.OK.value());
        return json(result).get("totalElements").asLong();
    }

    private MvcTestResult audit(String authorization, String query) {
        return mvc.get().uri(AUDIT_PATH + query).header(AUTHORIZATION_HEADER, authorization).exchange();
    }

    private static void assertState(JsonNode state, String available, String reserved, int tokens) {
        assertThat(state.get("availableBalance").decimalValue()).isEqualByComparingTo(available);
        assertThat(state.get("reservedBalance").decimalValue()).isEqualByComparingTo(reserved);
        assertThat(state.get("tokens").asInt()).isEqualTo(tokens);
    }

    private void assertBadRequest(MvcTestResult result, String message) throws Exception {
        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(json(result).get("message").asString()).isEqualTo(message);
    }

    /**
     * Cada pedido inicia sesión con el reloj del momento, así la credencial nunca está vencida.
     */
    private String bearer(TestUser user) throws Exception {
        return BEARER_PREFIX + helper.login(user);
    }

    private String adminBearer() throws Exception {
        return bearer(new TestUser(null, ADMIN_USERNAME, ADMIN_EMAIL, ADMIN_PASSWORD, null));
    }

    private static String raw(MvcTestResult result) throws Exception {
        return result.getResponse().getContentAsString();
    }

    private JsonNode json(MvcTestResult result) throws Exception {
        return jsonMapper.readTree(raw(result));
    }

    private record Scenario(AppUser ana, AppUser bruno, AppUser carla, long anaPurchase, long brunoPurchase,
                            long carlaPurchase) {
    }
}
