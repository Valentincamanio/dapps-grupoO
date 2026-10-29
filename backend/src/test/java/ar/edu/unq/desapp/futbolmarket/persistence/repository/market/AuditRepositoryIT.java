package ar.edu.unq.desapp.futbolmarket.persistence.repository.market;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import org.hibernate.annotations.Immutable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import ar.edu.unq.desapp.futbolmarket.modelo.league.League;
import ar.edu.unq.desapp.futbolmarket.modelo.market.AuditActor;
import ar.edu.unq.desapp.futbolmarket.modelo.market.AuditActorType;
import ar.edu.unq.desapp.futbolmarket.modelo.market.AuditEntry;
import ar.edu.unq.desapp.futbolmarket.modelo.market.AuditEventType;
import ar.edu.unq.desapp.futbolmarket.modelo.market.AuditFilter;
import ar.edu.unq.desapp.futbolmarket.modelo.market.CancelReason;
import ar.edu.unq.desapp.futbolmarket.modelo.market.MarketOrder;
import ar.edu.unq.desapp.futbolmarket.modelo.market.OrderSide;
import ar.edu.unq.desapp.futbolmarket.modelo.market.OwnerType;
import ar.edu.unq.desapp.futbolmarket.modelo.market.PartyChange;
import ar.edu.unq.desapp.futbolmarket.modelo.market.PartyState;
import ar.edu.unq.desapp.futbolmarket.modelo.market.Trade;
import ar.edu.unq.desapp.futbolmarket.modelo.player.Player;
import ar.edu.unq.desapp.futbolmarket.modelo.position.Position;
import ar.edu.unq.desapp.futbolmarket.modelo.quote.QuotePage;
import ar.edu.unq.desapp.futbolmarket.modelo.team.Team;
import ar.edu.unq.desapp.futbolmarket.persistence.sql.entity.market.AuditEntrySQL;
import ar.edu.unq.desapp.futbolmarket.persistence.sql.entity.market.AuditPartySQL;
import ar.edu.unq.desapp.futbolmarket.persistence.sql.interfaces.market.AuditEntrySQLDAO;

/**
 * La auditoría contra la base, con una H2 propia. Los ids de un registro no tienen claves foráneas,
 * así que los hechos se arman sin guardar jugadores, cuentas ni órdenes.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:audit-repository-it;DB_CLOSE_DELAY=-1")
class AuditRepositoryIT {
    private static final Instant AT = Instant.parse("2026-08-24T15:00:00Z");
    private static final long SUPERUSER = 1L;
    private static final long BUYER = 7L;
    private static final long SELLER = 8L;
    private static final long PLAYER = 42L;
    private static final long BUY_ORDER = 11L;
    private static final long INITIAL_ORDER = 12L;
    private static final long TRADE = 900L;
    private static final long QUOTE = 300L;
    private static final long OTHER_PLAYER = 43L;
    private static final long STRANGER = 99L;
    private static final Player KANE = new Player(PLAYER, "ext-42", "Harry Kane", Position.FORWARD,
            new Team(5L, "5", "FC Bayern München", null, League.BUNDESLIGA));
    private static final List<String> WRITE_PREFIXES = List.of("update", "delete", "remove", "set");

    @Autowired
    private AuditRepository repository;

    @Autowired
    private AuditEntrySQLDAO auditDAO;

    @BeforeEach
    void prepareDatabase() {
        auditDAO.deleteAll();
    }

    @AfterEach
    void cleanDatabase() {
        auditDAO.deleteAll();
    }

    @Test
    void idaYVueltaDeUnaOrdenCreadaConUnaSolaParte() {
        AuditEntry created = AuditEntry.orderCreated(buyOrder(), KANE, AuditActor.user(BUYER),
                change(BUYER, "1000.00", "0.00", 0, "989.70", "10.30", 0), AT);

        AuditEntry saved = repository.saveAll(List.of(created)).getFirst();
        AuditEntry read = repository.findById(saved.id()).orElseThrow();

        assertThat(saved.id()).isNotNull();
        assertThat(read).isEqualTo(withId(created, saved.id()));
        assertThat(read.secondary()).isNull();
    }

    @Test
    void idaYVueltaDeUnaOperacionConLasDosPartes() {
        AuditEntry executed = AuditEntry.tradeExecuted(trade(), KANE, AuditActor.user(BUYER),
                change(BUYER, "989.70", "10.30", 0, "989.70", "0.30", 10),
                change(SUPERUSER, "0.00", "0.00", 100, "10.00", "0.00", 90));

        AuditEntry saved = repository.saveAll(List.of(executed)).getFirst();
        AuditEntry read = repository.findById(saved.id()).orElseThrow();

        assertThat(read).isEqualTo(withId(executed, saved.id()));
        assertThat(read.secondary().userId()).isEqualTo(SUPERUSER);
    }

    @Test
    void idaYVueltaDeUnaCancelacionDelSistemaSinAutor() {
        MarketOrder buy = buyOrder();
        buy.cancelRemainder(CancelReason.INSUFFICIENT_CREDITS);
        AuditEntry cancelled = AuditEntry.orderCancelled(buy, KANE, AuditActor.system(),
                change(BUYER, "0.00", "10.30", 0, "10.30", "0.00", 0), AT);

        AuditEntry saved = repository.saveAll(List.of(cancelled)).getFirst();
        AuditEntry read = repository.findById(saved.id()).orElseThrow();

        assertThat(read).isEqualTo(withId(cancelled, saved.id()));
        assertThat(read.actorId()).isNull();
        assertThat(read.cancelReason()).isEqualTo(CancelReason.INSUFFICIENT_CREDITS);
    }

    @Test
    void guardaLosRegistrosEnElOrdenDeLosHechos() {
        AuditEntry created = AuditEntry.orderCreated(buyOrder(), KANE, AuditActor.user(BUYER),
                change(BUYER, "1000.00", "0.00", 0, "989.70", "10.30", 0), AT);
        AuditEntry executed = AuditEntry.tradeExecuted(trade(), KANE, AuditActor.user(BUYER),
                change(BUYER, "989.70", "10.30", 0, "989.70", "0.30", 10),
                change(SUPERUSER, "0.00", "0.00", 100, "10.00", "0.00", 90));

        List<AuditEntry> saved = repository.saveAll(List.of(created, executed));

        assertThat(saved).extracting(AuditEntry::eventType).containsExactly(created.eventType(), executed.eventType());
        assertThat(saved.get(0).id()).isLessThan(saved.get(1).id());
    }

    @Test
    void sinFiltrosLaAuditoriaVaDeLaMasNuevaALaMasViejaYEnElMismoInstantePorIdDescendente() {
        List<AuditEntry> saved = repository.saveAll(List.of(
                created(at("2026-08-24T15:00:00Z"), BUYER, PLAYER),
                created(at("2026-08-26T15:00:00Z"), BUYER, PLAYER),
                created(at("2026-08-26T15:00:00Z"), SELLER, PLAYER),
                created(at("2026-08-25T15:00:00Z"), SELLER, PLAYER)));

        QuotePage<AuditEntry> page = repository.findAudit(AuditFilter.none(), 0, 10);

        assertThat(page.content()).extracting(AuditEntry::id).containsExactly(saved.get(2).id(), saved.get(1).id(),
                saved.get(3).id(), saved.get(0).id());
        assertThat(page.totalElements()).isEqualTo(4);
    }

    @Test
    void elFiltroDeUsuarioEncuentraAlAutorYACualquieraDeLasDosPartes() {
        List<AuditEntry> saved = repository.saveAll(List.of(
                created(at("2026-08-24T15:00:00Z"), BUYER, PLAYER),
                executed(at("2026-08-24T16:00:00Z"), BUYER, BUYER, SELLER),
                executed(at("2026-08-24T17:00:00Z"), SELLER, BUYER, SUPERUSER),
                systemCancellation(at("2026-08-24T18:00:00Z"), SELLER)));

        List<Long> ofSeller = ids(new AuditFilter(null, SELLER, null, null, null));
        List<Long> ofSuperuser = ids(new AuditFilter(null, SUPERUSER, null, null, null));
        List<Long> ofStranger = ids(new AuditFilter(null, STRANGER, null, null, null));

        assertThat(ofSeller).containsExactly(saved.get(3).id(), saved.get(2).id(), saved.get(1).id());
        assertThat(ofSuperuser).containsExactly(saved.get(2).id());
        assertThat(ofStranger).isEmpty();
    }

    @Test
    void losFiltrosDeTipoJugadorYFechasSeCombinanYElRangoIncluyeLosDosDiasEnHoraDeArgentina() {
        List<AuditEntry> saved = repository.saveAll(List.of(
                created(at("2026-08-24T02:59:59Z"), BUYER, PLAYER),
                created(at("2026-08-24T03:00:00Z"), BUYER, PLAYER),
                executed(at("2026-08-25T12:00:00Z"), BUYER, BUYER, SELLER),
                created(at("2026-08-26T02:59:59Z"), BUYER, OTHER_PLAYER),
                created(at("2026-08-26T03:00:00Z"), BUYER, PLAYER)));

        assertThat(ids(new AuditFilter(AuditEventType.TRADE_EXECUTED, null, null, null, null)))
                .containsExactly(saved.get(2).id());
        assertThat(ids(new AuditFilter(null, null, OTHER_PLAYER, null, null))).containsExactly(saved.get(3).id());
        assertThat(ids(AuditFilter.of(null, null, null, "2026-08-24", "2026-08-25")))
                .containsExactly(saved.get(3).id(), saved.get(2).id(), saved.get(1).id());
        assertThat(ids(AuditFilter.of(null, null, null, "2026-08-26", null))).containsExactly(saved.get(4).id());
        assertThat(ids(AuditFilter.of(null, null, null, null, "2026-08-23"))).containsExactly(saved.get(0).id());
        assertThat(ids(AuditFilter.of(AuditEventType.ORDER_CREATED, BUYER, PLAYER, "2026-08-24", "2026-08-25")))
                .containsExactly(saved.get(1).id());
    }

    @Test
    void laAuditoriaSeRecorrePorHojasConSuConteo() {
        List<AuditEntry> saved = repository.saveAll(List.of(
                created(at("2026-08-24T15:00:00Z"), BUYER, PLAYER),
                created(at("2026-08-24T16:00:00Z"), BUYER, PLAYER),
                created(at("2026-08-24T17:00:00Z"), BUYER, PLAYER)));

        QuotePage<AuditEntry> first = repository.findAudit(AuditFilter.none(), 0, 2);
        QuotePage<AuditEntry> second = repository.findAudit(AuditFilter.none(), 1, 2);
        QuotePage<AuditEntry> empty = repository.findAudit(new AuditFilter(null, STRANGER, null, null, null), 0, 2);

        assertThat(first.content()).extracting(AuditEntry::id).containsExactly(saved.get(2).id(), saved.get(1).id());
        assertThat(first.totalElements()).isEqualTo(3);
        assertThat(first.totalPages()).isEqualTo(2);
        assertThat(first.hasNext()).isTrue();
        assertThat(second.content()).extracting(AuditEntry::id).containsExactly(saved.get(0).id());
        assertThat(second.hasPrevious()).isTrue();
        assertThat(second.hasNext()).isFalse();
        assertThat(empty.content()).isEmpty();
        assertThat(empty.totalElements()).isZero();
    }

    @Test
    void laConsultaDevuelveCadaRegistroCompletoConSusDosPartes() {
        AuditEntry executed = AuditEntry.tradeExecuted(trade(), KANE, AuditActor.user(BUYER),
                change(BUYER, "989.70", "10.30", 0, "989.70", "0.30", 10),
                change(SUPERUSER, "0.00", "0.00", 100, "10.00", "0.00", 90));
        AuditEntry saved = repository.saveAll(List.of(executed)).getFirst();

        QuotePage<AuditEntry> page = repository.findAudit(AuditFilter.none(), 0, 10);

        assertThat(page.content()).containsExactly(withId(executed, saved.id()));
    }

    @Test
    void elRepositoryNoTieneMetodosParaModificarNiBorrarRegistros() {
        List<String> methods = publicMethodNames(AuditRepository.class);

        assertThat(methods).noneMatch(AuditRepositoryIT::writes);
        assertThat(methods).contains("saveAll");
    }

    @Test
    void laFilaDeUnRegistroEsInmutableYNoTieneSetters() {
        List<String> entryMethods = publicMethodNames(AuditEntrySQL.class);
        List<String> partyMethods = publicMethodNames(AuditPartySQL.class);

        assertThat(entryMethods).noneMatch(name -> name.startsWith("set"));
        assertThat(partyMethods).noneMatch(name -> name.startsWith("set"));
        assertThat(AuditEntrySQL.class.isAnnotationPresent(Immutable.class)).isTrue();
    }

    private List<Long> ids(AuditFilter filter) {
        return repository.findAudit(filter, 0, 10).content().stream().map(AuditEntry::id).toList();
    }

    private static Instant at(String instant) {
        return Instant.parse(instant);
    }

    /**
     * Un registro de una orden creada por su dueño, armado sin fábrica: para filtrar solo importan
     * el instante, el tipo, el autor, el jugador y las partes.
     */
    private static AuditEntry created(Instant occurredAt, long owner, long playerId) {
        return new AuditEntry(null, occurredAt, AuditEventType.ORDER_CREATED, AuditActorType.USER, owner, playerId,
                BUY_ORDER, null, OrderSide.BUY, 10, null, null, null, null,
                change(owner, "1000.00", "0.00", 0, "989.70", "10.30", 0), null, "Orden de compra");
    }

    private static AuditEntry executed(Instant occurredAt, long actor, long buyer, long seller) {
        return new AuditEntry(null, occurredAt, AuditEventType.TRADE_EXECUTED, AuditActorType.USER, actor, PLAYER,
                BUY_ORDER, TRADE, OrderSide.BUY, 10, new BigDecimal("1.0000"), new BigDecimal("10.00"), QUOTE, null,
                change(buyer, "989.70", "10.30", 0, "989.70", "0.30", 10),
                change(seller, "0.00", "0.00", 100, "10.00", "0.00", 90), "Compra");
    }

    private static AuditEntry systemCancellation(Instant occurredAt, long owner) {
        return new AuditEntry(null, occurredAt, AuditEventType.ORDER_CANCELLED, AuditActorType.SYSTEM, null, PLAYER,
                BUY_ORDER, null, OrderSide.BUY, 1, null, null, null, CancelReason.INSUFFICIENT_CREDITS,
                change(owner, "0.00", "10.30", 0, "10.30", "0.00", 0), null, "Cancelación");
    }

    private static MarketOrder buyOrder() {
        return MarketOrder.reconstitute(new MarketOrder.State(BUY_ORDER, OrderSide.BUY, BUYER, PLAYER, 10, 0, 0,
                new BigDecimal("10.30"), new BigDecimal("1.0300"), AT, false, null));
    }

    private static Trade trade() {
        return new Trade(TRADE, AT, PLAYER, BUY_ORDER, INITIAL_ORDER, BUYER, SUPERUSER, OwnerType.ADMIN, 10,
                new BigDecimal("1.0000"), new BigDecimal("10.00"), QUOTE, new BigDecimal("1000.00"),
                new BigDecimal("990.00"), new BigDecimal("0.00"), new BigDecimal("10.00"), 0, 10, 100, 90, null);
    }

    private static PartyChange change(long userId, String freeBefore, String reservedBefore, int tokensBefore,
                                      String freeAfter, String reservedAfter, int tokensAfter) {
        return new PartyChange(
                new PartyState(userId, new BigDecimal(freeBefore), new BigDecimal(reservedBefore), tokensBefore),
                new PartyState(userId, new BigDecimal(freeAfter), new BigDecimal(reservedAfter), tokensAfter));
    }

    private static AuditEntry withId(AuditEntry entry, Long id) {
        return new AuditEntry(id, entry.occurredAt(), entry.eventType(), entry.actorType(), entry.actorId(),
                entry.playerId(), entry.orderId(), entry.tradeId(), entry.side(), entry.quantity(), entry.tokenPrice(),
                entry.total(), entry.quoteId(), entry.cancelReason(), entry.primary(), entry.secondary(),
                entry.detail());
    }

    private static List<String> publicMethodNames(Class<?> type) {
        return Arrays.stream(type.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .map(Method::getName)
                .toList();
    }

    private static boolean writes(String methodName) {
        return WRITE_PREFIXES.stream().anyMatch(methodName::startsWith);
    }
}
