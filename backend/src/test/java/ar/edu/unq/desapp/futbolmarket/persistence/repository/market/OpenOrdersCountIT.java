package ar.edu.unq.desapp.futbolmarket.persistence.repository.market;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import ar.edu.unq.desapp.futbolmarket.modelo.market.CancelReason;
import ar.edu.unq.desapp.futbolmarket.modelo.market.MarketOrder;
import ar.edu.unq.desapp.futbolmarket.modelo.player.Player;
import ar.edu.unq.desapp.futbolmarket.modelo.position.Position;
import ar.edu.unq.desapp.futbolmarket.modelo.team.Team;
import ar.edu.unq.desapp.futbolmarket.modelo.user.AppUser;
import ar.edu.unq.desapp.futbolmarket.modelo.user.Role;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.user.AppUserRepository;
import ar.edu.unq.desapp.futbolmarket.persistence.sql.interfaces.market.MarketOrderSQLDAO;
import ar.edu.unq.desapp.futbolmarket.persistence.sql.interfaces.user.AppUserSQLDAO;
import ar.edu.unq.desapp.futbolmarket.service.quote.QuoteDatabase;

/**
 * El conteo de órdenes abiertas que lee el gauge {@code futbolmarket.market.orders.open} (research D7
 * de la 012), con una H2 propia.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:open-orders-count-it;DB_CLOSE_DELAY=-1")
class OpenOrdersCountIT {
    private static final Instant AT = Instant.parse("2026-11-16T15:00:00Z");
    private static final String PASSWORD_HASH = "$2a$10$abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0";
    private static final int QUANTITY = 5;
    private static final int PARTIAL_FILL = 2;

    @Autowired
    private ApplicationContext context;

    @Autowired
    private MarketOrderRepository repository;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private MarketOrderSQLDAO orderDAO;

    @Autowired
    private AppUserSQLDAO appUserDAO;

    private QuoteDatabase database;
    private long seller;
    private Player kane;

    @BeforeEach
    void prepareDatabase() {
        database = new QuoteDatabase(context);
        cleanDatabase();
        Team team = database.team("57", "Arsenal FC");
        kane = database.player("8300", "Harry Kane", Position.FORWARD, team);
        AppUser user = AppUser.reconstitute(null, "vendedor", "vendedor@correo.com", PASSWORD_HASH, null, Role.USER,
                new BigDecimal("1000.00"));
        seller = appUserRepository.save(user).getId();
    }

    @AfterEach
    void cleanDatabaseAfter() {
        cleanDatabase();
    }

    @Test
    void cuentaSoloLasPendientesYLasParciales() {
        repository.save(MarketOrder.sell(seller, kane.id(), QUANTITY, AT));
        MarketOrder partial = MarketOrder.sell(seller, kane.id(), QUANTITY, AT);
        partial.fill(PARTIAL_FILL);
        repository.save(partial);
        MarketOrder executed = MarketOrder.sell(seller, kane.id(), QUANTITY, AT);
        executed.fill(QUANTITY);
        repository.save(executed);
        MarketOrder cancelled = MarketOrder.sell(seller, kane.id(), QUANTITY, AT);
        cancelled.cancelRemainder(CancelReason.USER_REQUEST);
        repository.save(cancelled);

        long open = repository.countOpen();

        assertThat(open).isEqualTo(2);
    }

    @Test
    void sinOrdenesCuentaCero() {
        long open = repository.countOpen();

        assertThat(open).isZero();
    }

    private void cleanDatabase() {
        orderDAO.deleteAll();
        appUserDAO.deleteAll();
        database.clean();
    }
}
