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
import ar.edu.unq.desapp.futbolmarket.modelo.market.AuditEntry;
import ar.edu.unq.desapp.futbolmarket.modelo.market.CancelReason;
import ar.edu.unq.desapp.futbolmarket.modelo.market.MarketOrder;
import ar.edu.unq.desapp.futbolmarket.modelo.market.OrderSide;
import ar.edu.unq.desapp.futbolmarket.modelo.market.OwnerType;
import ar.edu.unq.desapp.futbolmarket.modelo.market.PartyChange;
import ar.edu.unq.desapp.futbolmarket.modelo.market.PartyState;
import ar.edu.unq.desapp.futbolmarket.modelo.market.Trade;
import ar.edu.unq.desapp.futbolmarket.modelo.player.Player;
import ar.edu.unq.desapp.futbolmarket.modelo.position.Position;
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
