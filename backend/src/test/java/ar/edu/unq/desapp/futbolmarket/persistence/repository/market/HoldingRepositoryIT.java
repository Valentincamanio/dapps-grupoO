package ar.edu.unq.desapp.futbolmarket.persistence.repository.market;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import ar.edu.unq.desapp.futbolmarket.modelo.market.CostBasis;
import ar.edu.unq.desapp.futbolmarket.modelo.market.Holding;
import ar.edu.unq.desapp.futbolmarket.modelo.market.HoldingView;
import ar.edu.unq.desapp.futbolmarket.modelo.player.Player;
import ar.edu.unq.desapp.futbolmarket.modelo.position.Position;
import ar.edu.unq.desapp.futbolmarket.modelo.team.Team;
import ar.edu.unq.desapp.futbolmarket.modelo.user.AppUser;
import ar.edu.unq.desapp.futbolmarket.modelo.user.Role;
import ar.edu.unq.desapp.futbolmarket.persistence.repository.user.AppUserRepository;
import ar.edu.unq.desapp.futbolmarket.persistence.sql.interfaces.market.HoldingSQLDAO;
import ar.edu.unq.desapp.futbolmarket.persistence.sql.interfaces.user.AppUserSQLDAO;
import ar.edu.unq.desapp.futbolmarket.service.quote.QuoteDatabase;

/**
 * Las tenencias contra la base, con una H2 propia.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:holding-repository-it;DB_CLOSE_DELAY=-1")
class HoldingRepositoryIT {
    private static final String PASSWORD_HASH = "$2a$10$abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0";
    private static final String OWNER_PLAYER_INDEX = "ux_holdings_owner_player";

    @Autowired
    private ApplicationContext context;

    @Autowired
    private HoldingRepository repository;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private HoldingSQLDAO holdingDAO;

    @Autowired
    private AppUserSQLDAO appUserDAO;

    private QuoteDatabase database;
    private long ana;
    private long beto;
    private long carla;
    private Player kane;
    private Player salah;

    @BeforeEach
    void prepareDatabase() {
        database = new QuoteDatabase(context);
        cleanDatabase();
        Team team = database.team("57", "Arsenal FC");
        kane = database.player("8300", "Harry Kane", Position.FORWARD, team);
        salah = database.player("8301", "Mohamed Salah", Position.FORWARD, team);
        ana = user("ana");
        beto = user("beto");
        carla = user("carla");
    }

    @AfterEach
    void cleanDatabaseAfter() {
        cleanDatabase();
    }

    @Test
    void idaYVueltaConservaElCostoYLosComprometidos() {
        Holding holding = Holding.empty(ana, kane.id());
        holding.buy(10, new BigDecimal("10.00"));
        holding.buy(5, new BigDecimal("5.15"));
        holding.commit(6);

        List<Holding> saved = repository.saveAll(List.of(holding));
        List<Holding> read = repository.findOfPlayer(kane.id(), List.of(ana));

        assertThat(saved).singleElement().satisfies(holdingSaved -> assertThat(holdingSaved.getId()).isNotNull());
        assertThat(read).singleElement().satisfies(holdingRead -> {
            assertThat(holdingRead.getId()).isEqualTo(saved.getFirst().getId());
            assertThat(holdingRead.getOwnerId()).isEqualTo(ana);
            assertThat(holdingRead.getPlayerId()).isEqualTo(kane.id());
            assertThat(holdingRead.getBasis()).isEqualTo(new CostBasis(15, 15, new BigDecimal("15.15")));
            assertThat(holdingRead.getBasis().averagePrice()).isEqualTo(new BigDecimal("1.0100"));
            assertThat(holdingRead.getCommitted()).isEqualTo(6);
        });
    }

    @Test
    void guardarDeNuevoUnaTenenciaLaActualiza() {
        Holding holding = Holding.empty(ana, kane.id());
        holding.buy(10, new BigDecimal("10.00"));
        Holding saved = repository.saveAll(List.of(holding)).getFirst();
        saved.commit(4);

        repository.saveAll(List.of(saved));

        List<Holding> read = repository.findOfPlayer(kane.id(), List.of(ana));
        assertThat(read).singleElement().satisfies(holdingRead -> {
            assertThat(holdingRead.getId()).isEqualTo(saved.getId());
            assertThat(holdingRead.getCommitted()).isEqualTo(4);
        });
        assertThat(holdingDAO.count()).isEqualTo(1);
    }

    @Test
    void unaSegundaTenenciaDelMismoTitularYJugadorViolaElIndiceUnico() {
        repository.saveAll(List.of(Holding.empty(ana, kane.id())));

        assertThatThrownBy(() -> repository.saveAll(List.of(Holding.empty(ana, kane.id()))))
                .isInstanceOf(DataIntegrityViolationException.class)
                .satisfies(e -> assertThat(mostSpecificCauseMessage(e)).contains(OWNER_PLAYER_INDEX));
    }

    @Test
    void lasTenenciasDeUnJugadorSonSoloLasDeLosTitularesPedidos() {
        repository.saveAll(List.of(
                Holding.empty(ana, kane.id()),
                Holding.empty(beto, kane.id()),
                Holding.empty(carla, kane.id()),
                Holding.empty(ana, salah.id())));

        List<Holding> read = repository.findOfPlayer(kane.id(), List.of(ana, beto));

        assertThat(read).extracting(Holding::getOwnerId).containsExactlyInAnyOrder(ana, beto);
        assertThat(read).extracting(Holding::getPlayerId).containsOnly(kane.id());
    }

    @Test
    void sinTitularesNoHayTenencias() {
        repository.saveAll(List.of(Holding.empty(ana, kane.id())));

        List<Holding> read = repository.findOfPlayer(kane.id(), List.of());

        assertThat(read).isEmpty();
    }

    @Test
    void lasPosicionesDeUnTitularSonSusTenenciasConTokensConElJugadorYSuEquipo() {
        Holding kaneHolding = Holding.empty(ana, kane.id());
        kaneHolding.buy(10, new BigDecimal("10.00"));
        kaneHolding.commit(3);
        Holding salahHolding = Holding.empty(ana, salah.id());
        salahHolding.buy(5, new BigDecimal("5.15"));
        Holding betoHolding = Holding.empty(beto, kane.id());
        betoHolding.buy(1, new BigDecimal("1.00"));
        repository.saveAll(List.of(kaneHolding, salahHolding, betoHolding));

        List<HoldingView> positions = repository.findPositionsOf(ana);

        assertThat(positions).extracting(view -> view.player().name())
                .containsExactlyInAnyOrder("Harry Kane", "Mohamed Salah");
        assertThat(positions).filteredOn(view -> view.player().id().equals(kane.id())).singleElement()
                .satisfies(view -> {
                    assertThat(view.holding().getOwnerId()).isEqualTo(ana);
                    assertThat(view.holding().getQuantity()).isEqualTo(10);
                    assertThat(view.holding().getCommitted()).isEqualTo(3);
                    assertThat(view.player().team().name()).isEqualTo("Arsenal FC");
                });
    }

    @Test
    void unaTenenciaQueQuedoEnCeroNoEsUnaPosicion() {
        Holding sold = Holding.empty(ana, kane.id());
        sold.buy(10, new BigDecimal("10.00"));
        sold.commit(10);
        sold.sell(10, new BigDecimal("10.30"));
        repository.saveAll(List.of(sold));

        List<HoldingView> positions = repository.findPositionsOf(ana);

        assertThat(positions).isEmpty();
    }

    @Test
    void sinTenenciasNoHayPosiciones() {
        List<HoldingView> positions = repository.findPositionsOf(carla);

        assertThat(positions).isEmpty();
    }

    private long user(String username) {
        AppUser user = AppUser.reconstitute(null, username, username + "@correo.com", PASSWORD_HASH, null, Role.USER,
                new BigDecimal("1000.00"));
        return appUserRepository.save(user).getId();
    }

    private static String mostSpecificCauseMessage(Throwable e) {
        return NestedExceptionUtils.getMostSpecificCause(e).getMessage().toLowerCase(Locale.ROOT);
    }

    private void cleanDatabase() {
        holdingDAO.deleteAll();
        appUserDAO.deleteAll();
        database.clean();
    }
}
